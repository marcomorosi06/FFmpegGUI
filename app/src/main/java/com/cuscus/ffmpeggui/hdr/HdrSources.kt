package com.cuscus.ffmpeggui.hdr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import com.cuscus.ffmpeggui.core.hdr.HdrAreas
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.media.StillGainMaps
import com.cuscus.ffmpeggui.preview.SubjectMasks
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the HDR look of a picture is made from, brought onto the picture as it
 * comes out (the export's JPG, or the editor's preview): where it goes (the
 * subject's or the painted mask, see ImageHdr.area) and, for a photo's own HDR
 * look, its gain map. Shared by the export and the preview, so that both make
 * the same gain map.
 */
object HdrSources {

    /** Longest side a mask or a photo's gain map is read at. */
    private const val READ_SIDE = 2048

    /**
     * The share of the HDR look on a [width] x [height] frame where the
     * picture lies at [rect] (see HdrAreas.aligned): null for everywhere, and
     * null as well when the area's mask cannot be read (see ImageHdr.areaMask).
     */
    fun areaOn(hdr: ImageHdr, edit: ImageEdit, effects: Effects, sourceWidth: Int, sourceHeight: Int, width: Int, height: Int, rect: FloatArray): FloatArray? {
        val path = hdr.areaMask ?: return null
        val (mw, mh) = fitted(sourceWidth, sourceHeight)
        val alphas = SubjectMasks.alphas(path, mw, mh) ?: return null
        val aligned = HdrAreas.aligned(HdrAreas.raster(alphas, mw, mh), edit, effects, width, height, rect)
        return HdrAreas.share(hdr.area, aligned)
    }

    /**
     * The photo's own gain map (kept beside its still, see StillGainMaps) on
     * the frame, in stops; NaN where the photo is not; null without one.
     */
    fun originalOn(gainMaps: StillGainMaps, stillUri: String, edit: ImageEdit, effects: Effects, width: Int, height: Int, rect: FloatArray): FloatArray? {
        val stored = gainMaps.read(stillUri, READ_SIDE) ?: return null
        val values = stored.values
        val pixels = IntArray(values.width * values.height)
        values.getPixels(pixels, 0, values.width, 0, 0, values.width, values.height)
        values.recycle()
        val aligned = HdrAreas.aligned(Raster(values.width, values.height, pixels), edit, effects, width, height, rect, outside = Float.NaN)
        val metadata = stored.metadata
        return FloatArray(aligned.size) { i -> val v = aligned[i]; if (v.isNaN()) Float.NaN else metadata.stopsOf(v) }
    }

    /** How much of a photo's own HDR look is kept: none at 0, as shot at the middle, twice as much at 1. */
    fun originalStrength(hdr: ImageHdr): Float = hdr.intensity.coerceIn(0f, 1f) * 2f

    /**
     * A new mask for painting where the HDR look goes, as large as the picture
     * at [pictureUri] (at most [READ_SIDE]): nowhere yet. Kept with the cut-out
     * masks; returns its path.
     */
    fun blankMask(context: Context, pictureUri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(pictureUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("The picture cannot be read")
        val (w, h) = fitted(bounds.outWidth, bounds.outHeight)
        val folder = File(context.filesDir, MASK_FOLDER).apply { mkdirs() }
        val file = File(folder, "hdr-" + java.lang.Long.toHexString(System.currentTimeMillis()) + ".png")
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            // White and fully see-through: the subject masks' way, with nothing in it.
            bitmap.eraseColor(0x00FFFFFF)
            file.outputStream().use { out -> if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Could not write the mask") }
        } finally {
            bitmap.recycle()
        }
        return file.path
    }

    /** [width] x [height] brought to at most [READ_SIDE] on the longer side. */
    private fun fitted(width: Int, height: Int): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val scale = minOf(1f, READ_SIDE.toFloat() / max(w, h))
        return (w * scale).roundToInt().coerceAtLeast(1) to (h * scale).roundToInt().coerceAtLeast(1)
    }

    /** Beside the subject masks (see SubjectCutter). */
    private const val MASK_FOLDER = "cutouts"
}
