package com.cuscus.ffmpeggui.cutout

import android.content.Context
import android.graphics.Bitmap
import com.cuscus.ffmpeggui.core.cutout.GuidedFilter
import com.cuscus.ffmpeggui.core.cutout.MaskEditing
import com.cuscus.ffmpeggui.core.cutout.PromptSegmentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Lifts a subject off a picture where the finger presses (the photo editor's
 * long press): EdgeTAM ([RefineEngine]) finds the thing under the point, its
 * mask is brought to the picture's own edges (GuidedFilter) and only the part
 * that touches the point is kept; what comes out is that part alone, on a
 * transparent background, cut to its box: a sticker to lay back exactly where
 * it was.
 *
 * The picture is read once ([RefineEngine.study]) and kept while the same
 * bitmap comes back, so a second press on the same picture is quick.
 */
class SubjectLifter(context: Context, private val engine: RefineEngine) {

    /**
     * The subject lifted: [file], a PNG of [width] x [height] pixels, which lay
     * at [left], [top] of the picture of [pictureWidth] x [pictureHeight].
     */
    class Lifted(
        val file: File,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val pictureWidth: Int,
        val pictureHeight: Int,
    ) {
        /**
         * Where it lay, as an overlay's x and y say it: a share of the free space
         * around it (0 at the left or top edge, 1 at the right or bottom one).
         */
        val placeX: Float get() = free(left, pictureWidth - width)
        val placeY: Float get() = free(top, pictureHeight - height)

        private fun free(offset: Int, room: Int): Float = if (room <= 0) 0.5f else (offset.toFloat() / room).coerceIn(0f, 1f)

        /** Its width as a share of the picture's (an overlay's scaleW). */
        val share: Float get() = width.toFloat() / pictureWidth

        /** Width over height. */
        val aspect: Float get() = width.toFloat() / height.coerceAtLeast(1)
    }

    private val folder = File(context.applicationContext.filesDir, FOLDER)
    private var studiedOf: Bitmap? = null
    private var studied: RefineEngine.Studied? = null

    /**
     * The subject under [x], [y] (pixels of [picture]), or null when nothing
     * clear is there (no mask holds the point, or what it holds is too small
     * or is the whole picture). Throws what the engine throws (the model
     * missing, not enough memory).
     */
    suspend fun lift(picture: Bitmap, x: Float, y: Float): Lifted? {
        val width = picture.width
        val height = picture.height
        val study = studied?.takeIf { studiedOf === picture } ?: engine.study(picture).also {
            studiedOf = picture
            studied = it
        }
        val prediction = engine.find(study, floatArrayOf(x, y), booleanArrayOf(true))
        return withContext(Dispatchers.Default) {
            val index = PromptSegmentation.chooseForTap(prediction.logits, prediction.scores, prediction.count, x, y, width, height)
                ?: return@withContext null
            val found = PromptSegmentation.region(prediction.logits, index, width, height)
            val seed = y.toInt().coerceIn(0, height - 1) * width + x.toInt().coerceIn(0, width - 1)
            // Only the piece under the finger: a mask may hold two things apart.
            val touched = MaskEditing.connected(found, width, height, intArrayOf(seed))
            val pixels = IntArray(width * height).also { picture.getPixels(it, 0, width, 0, 0, width, height) }
            val side = max(width, height)
            val region = GuidedFilter.snap(pixels, width, height, touched, max(2, side / EDGE_WINDOW), EDGE_EPS)
                ?: return@withContext null
            // Its box: where it is at least faintly there.
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = -1
            var maxY = -1
            var covered = 0L
            for (ry in 0 until region.height) {
                for (rx in 0 until region.width) {
                    val a = region.alpha[ry * region.width + rx].toInt() and 0xFF
                    if (a < MIN_ALPHA) continue
                    val px = region.left + rx
                    val py = region.top + ry
                    if (px < minX) minX = px
                    if (px > maxX) maxX = px
                    if (py < minY) minY = py
                    if (py > maxY) maxY = py
                    if (a >= 128) covered++
                }
            }
            if (maxX < 0) return@withContext null
            val share = covered.toFloat() / (width.toLong() * height)
            if (share < MIN_SHARE || share > PromptSegmentation.MAX_TAP_SHARE) return@withContext null
            val boxW = maxX - minX + 1
            val boxH = maxY - minY + 1
            val out = IntArray(boxW * boxH)
            for (by in 0 until boxH) {
                for (bx in 0 until boxW) {
                    val px = minX + bx
                    val py = minY + by
                    val p = pixels[py * width + px]
                    // The picture's own transparency (a cut-out already) is kept too.
                    val a = min(region.at(px, py), (p ushr 24) and 0xFF)
                    out[by * boxW + bx] = (a shl 24) or (p and 0xFFFFFF)
                }
            }
            val bitmap = Bitmap.createBitmap(out, boxW, boxH, Bitmap.Config.ARGB_8888)
            prune()
            folder.mkdirs()
            val target = File(folder, "lifted-${UUID.randomUUID().toString().take(8)}.png")
            try {
                FileOutputStream(target).use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not write the subject") }
            } finally {
                bitmap.recycle()
            }
            Lifted(target, minX, minY, boxW, boxH, width, height)
        }
    }

    /**
     * Old subjects go: a saved project keeps its own copy of every file it
     * uses (ProjectStore copies the app's own files into the project), so a
     * lifted subject is only needed here while it is being edited.
     */
    private fun prune() {
        val before = System.currentTimeMillis() - KEEP_MS
        folder.listFiles()?.forEach { if (it.isFile && it.lastModified() < before) it.delete() }
    }

    /** Lets go of the picture read (a new picture is read again). */
    fun forget() {
        studiedOf = null
        studied = null
    }

    companion object {
        /** Inside the app's files: the subjects lifted, used by projects as stickers. */
        const val FOLDER = "lifted"

        /** The edge window is the longer side divided by this (as MaskRefiner's). */
        private const val EDGE_WINDOW = 200

        /** How far colours must change to make an edge (see GuidedFilter). */
        private const val EDGE_EPS = 1e-3f

        /** Below this (of 255) a pixel is not part of the box. */
        private const val MIN_ALPHA = 8

        /** Lifted subjects older than this are deleted (see [prune]). */
        private const val KEEP_MS = 14L * 24 * 60 * 60 * 1000

        /** A subject smaller than this share of the picture is a speck, not a subject. */
        private const val MIN_SHARE = 0.0015f
    }
}
