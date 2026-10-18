package com.cuscus.ffmpeggui.cutout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cuscus.ffmpeggui.core.cutout.GuidedFilter
import com.cuscus.ffmpeggui.core.cutout.MaskEditing
import com.cuscus.ffmpeggui.core.cutout.MaskRegion
import com.cuscus.ffmpeggui.core.cutout.PromptSegmentation
import com.cuscus.ffmpeggui.core.cutout.StrokePoints
import com.cuscus.ffmpeggui.preview.SubjectMasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.max

/**
 * Corrects the mask of a subject cut out of a picture, one stroke at a time:
 * each stroke changes the mask once, and later strokes never undo what an
 * earlier one did (only Undo does), so every correction stays where it was put.
 *
 *  - [Tool.MAGIC]: a tap or a rough stroke on something; SlimSAM ([RefineEngine])
 *    finds the whole of it. Only what touches the stroke is taken (nothing
 *    elsewhere in the picture), its edge is brought to the picture's own edges
 *    (GuidedFilter, the model's mask being four times coarser) and it is put on
 *    the mask whole, never half see-through. The strokes of the other brush
 *    made before tell the model what is not meant; removing, so do the points
 *    deepest in the subject.
 *  - [Tool.PRECISE]: a round brush; exactly what it covers changes.
 *
 * Without the model ([engine] null, not set up) only the precise brush works.
 *
 * One refiner per picture being corrected: [open] reads the picture and its
 * mask (at a size that suits the screen), [study] has the model read the
 * picture, [apply] makes one correction, [replay] gives the mask after some
 * corrections (for Undo), [save] writes it as a new mask file (the old one
 * stays: projects may still use it).
 */
class MaskRefiner(context: Context, private val engine: RefineEngine?) {

    enum class Brush { KEEP, REMOVE }

    enum class Tool { MAGIC, PRECISE }

    /** A stroke: x, y pairs in the pixels of [Opened.picture], in the order drawn; [radius] the precise brush's, in those pixels. */
    class Stroke(val brush: Brush, val tool: Tool, val points: FloatArray, val radius: Float)

    /** A correction made: its stroke and, with the magic tool, what it found (on [Opened.picture]). */
    class Edit internal constructor(val stroke: Stroke, internal val region: MaskRegion?)

    /** What a stroke gave: a correction and the mask with it, or nothing (the model found nothing there). */
    sealed interface Applied {
        class Done(val edit: Edit, val mask: IntArray) : Applied
        data object NothingFound : Applied
    }

    /** The picture being corrected and its mask as it is, both at the working size. */
    class Opened(val picture: Bitmap, val base: IntArray, internal val pixels: IntArray, internal val maskFile: File, internal val uri: Uri)

    class Unreadable : IOException("The picture or its mask cannot be read")

    private val appContext = context.applicationContext
    private var studied: RefineEngine.Studied? = null

    /** Reads the picture at [uri] and the mask at [maskPath], their longer side at most [WORK_SIDE]. */
    suspend fun open(uri: Uri, maskPath: String): Opened = withContext(Dispatchers.IO) {
        val maskFile = File(maskPath)
        if (!maskFile.isFile) throw Unreadable()
        val picture = decode(uri, WORK_SIDE) ?: throw Unreadable()
        val base = SubjectMasks.alphas(maskPath, picture.width, picture.height) ?: throw Unreadable()
        val pixels = IntArray(picture.width * picture.height)
        picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
        Opened(picture, base, pixels, maskFile, uri)
    }

    /** Has the model read the picture: the slow part, once (nothing without the model). */
    suspend fun study(opened: Opened) {
        studied = engine?.study(opened.picture)
    }

    /** [stroke] put on [mask] (the mask after the corrections [before]): the new correction and the mask with it. */
    suspend fun apply(opened: Opened, mask: IntArray, before: List<Edit>, stroke: Stroke): Applied {
        val width = opened.picture.width
        val height = opened.picture.height
        val keep = stroke.brush == Brush.KEEP
        if (stroke.tool == Tool.PRECISE) {
            return withContext(Dispatchers.Default) {
                val out = mask.copyOf()
                MaskEditing.paint(out, width, height, stroke.points, stroke.radius, keep)
                Applied.Done(Edit(stroke, null), out)
            }
        }
        val study = studied ?: throw IllegalStateException("Study the picture first")
        val model = engine ?: throw IllegalStateException("No model for the magic tool")
        val side = max(width, height).toFloat()
        val spacing = side / POINTS_ACROSS
        val wanted = StrokePoints.of(listOf(stroke.points), spacing, STROKE_POINTS)
        val unwanted = withContext(Dispatchers.Default) {
            val out = ArrayList<Float>()
            // The other brush's strokes, the latest first: what is not meant.
            for (edit in before.asReversed()) {
                if (edit.stroke.brush == stroke.brush) continue
                val points = StrokePoints.of(listOf(edit.stroke.points), spacing, 2)
                for (k in 0 until points.size / 2) {
                    if (out.size / 2 >= OPPOSITE_POINTS) break
                    if (!near(points[2 * k], points[2 * k + 1], stroke.points, side * APART)) {
                        out += points[2 * k]
                        out += points[2 * k + 1]
                    }
                }
                if (out.size / 2 >= OPPOSITE_POINTS) break
            }
            // Removing: the heart of the subject is not meant either.
            if (!keep) out += MaskEditing.deepPoints(mask, width, height, DEEP_POINTS, wanted, side * DEEP_APART).toList()
            out.toFloatArray()
        }
        val points = wanted + unwanted
        val inside = BooleanArray(points.size / 2) { it < wanted.size / 2 }
        val prediction = model.find(study, points, inside)
        return withContext(Dispatchers.Default) {
            val choice = PromptSegmentation.choose(prediction.logits, prediction.scores, prediction.count, wanted, unwanted, width, height)
            if (choice.agreement < MIN_AGREEMENT) return@withContext Applied.NothingFound
            val found = PromptSegmentation.region(prediction.logits, choice.index, width, height)
            val touched = MaskEditing.connected(found, width, height, MaskEditing.pathPixels(stroke.points, width, height))
            val region = GuidedFilter.snap(opened.pixels, width, height, touched, max(2, (side / EDGE_WINDOW).toInt()), EDGE_EPS)
                ?: return@withContext Applied.NothingFound
            val out = mask.copyOf()
            MaskEditing.apply(out, width, height, region, keep)
            Applied.Done(Edit(stroke, region), out)
        }
    }

    /** The mask after [edits], from the one the picture came with. */
    suspend fun replay(opened: Opened, edits: List<Edit>): IntArray = withContext(Dispatchers.Default) {
        val width = opened.picture.width
        val height = opened.picture.height
        val out = opened.base.copyOf()
        for (edit in edits) {
            val keep = edit.stroke.brush == Brush.KEEP
            val region = edit.region
            if (region != null) {
                MaskEditing.apply(out, width, height, region, keep)
            } else {
                MaskEditing.paint(out, width, height, edit.stroke.points, edit.stroke.radius, keep)
            }
        }
        out
    }

    /**
     * Writes the mask after [edits] next to the others (white, the subject in
     * its alpha, as SubjectCutter writes them) at the size of the old one, at
     * most [SAVE_SIDE]; returns the new file.
     */
    suspend fun save(opened: Opened, edits: List<Edit>): File = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(opened.maskFile.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw Unreadable()
        val scale = minOf(1f, SAVE_SIDE.toFloat() / max(bounds.outWidth, bounds.outHeight))
        val width = (bounds.outWidth * scale).toInt().coerceAtLeast(1)
        val height = (bounds.outHeight * scale).toInt().coerceAtLeast(1)
        val mask = SubjectMasks.alphas(opened.maskFile.path, width, height) ?: throw Unreadable()
        // The corrections were made on the working picture: the same picture, smaller.
        val workWidth = opened.picture.width
        val workHeight = opened.picture.height
        val sx = width.toFloat() / workWidth
        val sy = height.toFloat() / workHeight
        for (edit in edits) {
            val keep = edit.stroke.brush == Brush.KEEP
            val region = edit.region
            if (region != null) {
                MaskEditing.applyScaled(mask, width, height, region, workWidth, workHeight, keep)
            } else {
                val points = edit.stroke.points
                val scaled = FloatArray(points.size) { i -> points[i] * if (i % 2 == 0) sx else sy }
                MaskEditing.paint(mask, width, height, scaled, edit.stroke.radius * (sx + sy) / 2f, keep)
            }
        }
        val pixels = IntArray(mask.size) { (mask[it] shl 24) or 0xFFFFFF }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val name = opened.maskFile.name.removeSuffix(".png").substringBefore(EDIT_MARK) +
            EDIT_MARK + java.lang.Long.toHexString(System.currentTimeMillis()) + ".png"
        val target = File(opened.maskFile.parentFile, name)
        val temp = File(target.path + ".part")
        try {
            temp.outputStream().use { out -> if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Could not write the mask") }
            if (!temp.renameTo(target)) throw IOException("Could not keep the mask")
        } finally {
            temp.delete()
            bitmap.recycle()
        }
        target
    }

    /** Lets the model go of this picture. */
    fun close(opened: Opened?) {
        studied = null
        opened?.picture?.recycle()
    }

    private fun near(x: Float, y: Float, points: FloatArray, distance: Float): Boolean {
        for (k in 0 until points.size / 2) {
            if (hypot(x - points[2 * k], y - points[2 * k + 1]) < distance) return true
        }
        return false
    }

    /** The picture at [uri], halved until its longer side is at most twice [side], then brought to [side]. */
    private fun decode(uri: Uri, side: Int): Bitmap? = try {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
            if (decoded == null) {
                null
            } else {
                val scale = minOf(1f, side.toFloat() / max(decoded.width, decoded.height))
                if (scale >= 1f) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
                        .also { if (it !== decoded) decoded.recycle() }
                }
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private companion object {
        /** Longest side of the picture the strokes are drawn on (and the corrected mask shown at). */
        const val WORK_SIDE = 1024

        /** Longest side of a corrected mask once saved. */
        const val SAVE_SIDE = 2048

        /** Points along a stroke: about one every this fraction of the picture's longer side. */
        const val POINTS_ACROSS = 40f

        /** At most this many points from a stroke (Segment Anything works best with a few). */
        const val STROKE_POINTS = 5

        /** At most this many points from the other brush's strokes. */
        const val OPPOSITE_POINTS = 3

        /** Points deep in the subject a removing stroke is told are not meant. */
        const val DEEP_POINTS = 2

        /** The other brush's points closer than this share of the longer side to the stroke are left out. */
        const val APART = 0.05f

        /** The deep points are at least this share of the longer side from the stroke. */
        const val DEEP_APART = 0.15f

        /** A mask that agrees with less than this share of the points is not taken. */
        const val MIN_AGREEMENT = 0.6f

        /** The edge window is the longer side divided by this (5 pixels at the working size). */
        const val EDGE_WINDOW = 200f

        /** How far colours must change to make an edge (see GuidedFilter). */
        const val EDGE_EPS = 1e-3f

        /** Between the old mask's name and the time of the correction. */
        const val EDIT_MARK = "-edit-"
    }
}
