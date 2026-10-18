package com.cuscus.ffmpeggui.core.hdr

import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.image.imageStages
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.HdrArea
import com.cuscus.ffmpeggui.core.model.ImageEdit
import kotlin.math.floor

/**
 * Brings something drawn on the source picture (a mask, a photo's gain map:
 * grey values in a [Raster], in the source picture's pixels) onto the edited
 * picture: through the edit's turn, mirrors, keystone, straighten and crop,
 * as the picture itself goes, then into the place the picture takes in a
 * frame (the border around it, the output size).
 */
object HdrAreas {

    /**
     * [source] (grey, its blue channel read) through the geometry of [edit] and
     * [effects], then laid on [rect] (left, top, width, height, in pixels) of
     * a [width] x [height] frame: one value 0..1 per pixel of the frame,
     * [outside] where the picture is not.
     */
    fun aligned(
        source: Raster,
        edit: ImageEdit,
        effects: Effects,
        width: Int,
        height: Int,
        rect: FloatArray = floatArrayOf(0f, 0f, width.toFloat(), height.toFloat()),
        outside: Float = 0f,
    ): FloatArray {
        require(width > 0 && height > 0 && rect.size == 4) { "A frame and the picture's place in it" }
        val geometry = imageStages(edit.copy(key = null, hdr = null), effects, source.width, source.height).filter { it.isGeometry }
        val shaped = geometry.fold(source) { raster, stage -> stage.apply(raster) }
        val out = FloatArray(width * height) { outside }
        val (left, top, w, h) = rect.toList()
        if (w <= 0f || h <= 0f) return out
        val sx = shaped.width / w
        val sy = shaped.height / h
        val x0 = floor(left).toInt().coerceIn(0, width)
        val y0 = floor(top).toInt().coerceIn(0, height)
        val x1 = kotlin.math.ceil(left + w).toInt().coerceIn(0, width)
        val y1 = kotlin.math.ceil(top + h).toInt().coerceIn(0, height)
        for (y in y0 until y1) {
            val cy = y + 0.5f
            if (cy < top || cy > top + h) continue
            val fy = ((cy - top) * sy - 0.5f).coerceIn(0f, (shaped.height - 1).toFloat())
            val ry = fy.toInt()
            val ry1 = minOf(ry + 1, shaped.height - 1)
            val ty = fy - ry
            for (x in x0 until x1) {
                val cx = x + 0.5f
                if (cx < left || cx > left + w) continue
                val fx = ((cx - left) * sx - 0.5f).coerceIn(0f, (shaped.width - 1).toFloat())
                val rx = fx.toInt()
                val rx1 = minOf(rx + 1, shaped.width - 1)
                val tx = fx - rx
                fun v(px: Int, py: Int) = (shaped.pixels[py * shaped.width + px] and 0xFF) / 255f
                val a = v(rx, ry) * (1 - tx) + v(rx1, ry) * tx
                val b = v(rx, ry1) * (1 - tx) + v(rx1, ry1) * tx
                out[y * width + x] = a * (1 - ty) + b * ty
            }
        }
        return out
    }

    /** A mask's alphas (0..255, [width] x [height]) as the grey raster [aligned] reads. */
    fun raster(alphas: IntArray, width: Int, height: Int): Raster =
        Raster(width, height, IntArray(alphas.size) { i -> val a = alphas[i].coerceIn(0, 255); (0xFF shl 24) or (a shl 16) or (a shl 8) or a })

    /** The share of each pixel the HDR look takes, from the aligned subject or brush mask ([HdrArea.EVERYWHERE] takes no mask). */
    fun share(area: HdrArea, aligned: FloatArray?): FloatArray? = when (area) {
        HdrArea.EVERYWHERE -> null
        HdrArea.SUBJECT, HdrArea.BRUSH -> aligned
        HdrArea.BACKGROUND -> aligned?.let { a -> FloatArray(a.size) { 1f - a[it] } }
    }
}
