package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.OverlayLook
import com.cuscus.ffmpeggui.core.model.OverlayShape
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What an [OverlayLook] does to a picture laid over the video, on the CPU, for
 * the preview: the same steps as the export's filters (command.overlayLookFilters)
 * on the same pixel sizes, so that the picture looks in the editor as it will
 * in the file. Pixels are ARGB, not premultiplied (as Android's Bitmap.getPixels
 * gives them).
 */
object OverlayDecoration {

    /** The part [crop] of [picture] (fractions of it), at least 1 x 1 pixel. */
    fun crop(picture: Raster, crop: CropRect): Raster {
        val left = (picture.width * crop.left.coerceIn(0f, 1f)).toInt().coerceIn(0, picture.width - 1)
        val top = (picture.height * crop.top.coerceIn(0f, 1f)).toInt().coerceIn(0, picture.height - 1)
        val width = (picture.width * crop.width.coerceIn(0.01f, 1f)).roundToInt().coerceIn(1, picture.width - left)
        val height = (picture.height * crop.height.coerceIn(0.01f, 1f)).roundToInt().coerceIn(1, picture.height - top)
        val out = IntArray(width * height)
        for (y in 0 until height) System.arraycopy(picture.pixels, (top + y) * picture.width + left, out, y * width, width)
        return Raster(width, height, out)
    }

    /** [picture] mirrored across (left to right) and / or upside down. */
    fun mirror(picture: Raster, horizontal: Boolean, vertical: Boolean): Raster {
        if (!horizontal && !vertical) return picture
        val w = picture.width
        val h = picture.height
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val sy = if (vertical) h - 1 - y else y
            for (x in 0 until w) out[y * w + x] = picture.pixels[sy * w + (if (horizontal) w - 1 - x else x)]
        }
        return Raster(w, h, out)
    }

    /**
     * [picture], already cropped and as large as it shows, with [look] done to
     * it: blurred, cut to its shape, given room, its outline and its shadow.
     * The result is the box laid on the video (the picture plus the margin).
     */
    fun decorate(picture: Raster, look: OverlayLook): Raster {
        if (!look.decorates) return picture
        val px = look.pixels(picture.width, picture.height)
        var current = picture
        if (px.blur > 0) current = blurColour(current, px.blur, passes = 3)
        if (look.shaped) current = cutToShape(current, look)
        if (!look.borders && !look.shadows) return current
        current = pad(current, px.margin)
        if (look.borders && px.border > 0) {
            val spread = boxBlurPlane(alphaOf(current), current.width, current.height, px.border, passes = 1)
            for (i in spread.indices) spread[i] = (spread[i] * OverlayLook.BORDER_GAIN).coerceIn(0, 255)
            val outline = layer(look.borderColor, spread, current.width, current.height)
            current = over(current, outline, 0, 0)
        }
        if (look.shadows) {
            val blurred = boxBlurPlane(alphaOf(current), current.width, current.height, px.shadowBlur, passes = 2)
            val opacity = look.shadowOpacity.coerceIn(0f, 1f)
            for (i in blurred.indices) blurred[i] = (blurred[i] * opacity).toInt().coerceIn(0, 255)
            val shadow = layer(look.shadowColor, blurred, current.width, current.height)
            val canvas = Raster(current.width, current.height, IntArray(current.width * current.height))
            val fallen = over(shadow, canvas, px.shadowX, px.shadowY)
            current = over(current, fallen, 0, 0)
        }
        return current
    }

    /**
     * How much of pixel ([x], [y]) of a [width] x [height] picture lies inside
     * [look]'s shape, 0..1 (the export's geq expression, in Kotlin).
     */
    fun shapeCoverage(look: OverlayLook, x: Int, y: Int, width: Int, height: Int): Float {
        val w = width.toDouble()
        val h = height.toDouble()
        val cx = x + 0.5 - w / 2
        val cy = y + 0.5 - h / 2
        val outside = when (look.shape) {
            OverlayShape.RECTANGLE -> return 1f
            OverlayShape.ROUNDED -> {
                val radius = min(w, h) * look.cornerRadius.coerceIn(0f, 1f) / 2
                val px = abs(cx) - (w / 2 - radius)
                val py = abs(cy) - (h / 2 - radius)
                hypot(max(px, 0.0), max(py, 0.0)) + min(max(px, py), 0.0) - radius
            }
            OverlayShape.ELLIPSE -> (hypot(cx / (w / 2), cy / (h / 2)) - 1) * min(w, h) / 2
        }
        return (0.5 - outside).coerceIn(0.0, 1.0).toFloat()
    }

    // -- Steps -----------------------------------------------------------------------------

    /** The colour blurred (box blur of [radius], [passes] times), the alpha left as it is. */
    private fun blurColour(picture: Raster, radius: Int, passes: Int): Raster {
        val w = picture.width
        val h = picture.height
        val planes = listOf(16, 8, 0).map { shift -> IntArray(w * h) { (picture.pixels[it] shr shift) and 0xFF } }
            .map { boxBlurPlane(it, w, h, radius, passes) }
        val out = IntArray(w * h) { i ->
            (picture.pixels[i] and ALPHA) or (planes[0][i] shl 16) or (planes[1][i] shl 8) or planes[2][i]
        }
        return Raster(w, h, out)
    }

    private fun cutToShape(picture: Raster, look: OverlayLook): Raster {
        val w = picture.width
        val h = picture.height
        val out = picture.pixels.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val coverage = shapeCoverage(look, x, y, w, h)
            if (coverage >= 1f) continue
            val alpha = ((out[i] ushr 24) * coverage).roundToInt()
            out[i] = (alpha shl 24) or (out[i] and RGB)
        }
        return Raster(w, h, out)
    }

    /** [picture] with [margin] transparent pixels on every side. */
    private fun pad(picture: Raster, margin: Int): Raster {
        if (margin <= 0) return picture
        val w = picture.width + 2 * margin
        val h = picture.height + 2 * margin
        val out = IntArray(w * h)
        for (y in 0 until picture.height) {
            System.arraycopy(picture.pixels, y * picture.width, out, (y + margin) * w + margin, picture.width)
        }
        return Raster(w, h, out)
    }

    private fun alphaOf(picture: Raster): IntArray = IntArray(picture.pixels.size) { picture.pixels[it] ushr 24 }

    private fun boxBlurPlane(plane: IntArray, width: Int, height: Int, radius: Int, passes: Int): IntArray {
        if (radius <= 0) return plane
        var out = plane
        repeat(passes) { out = boxPass(boxPass(out, width, height, radius, true), width, height, radius, false) }
        return out
    }

    /** A layer of [color] with [alpha] as its alpha. */
    private fun layer(color: Int, alpha: IntArray, width: Int, height: Int): Raster =
        Raster(width, height, IntArray(alpha.size) { (alpha[it] shl 24) or (color and RGB) })

    /** [top] laid over [bottom] moved by ([dx], [dy]) (straight alpha), as large as [bottom]... */
    private fun over(top: Raster, bottom: Raster, dx: Int, dy: Int): Raster {
        val w = bottom.width
        val h = bottom.height
        val out = bottom.pixels.copyOf()
        for (y in 0 until h) {
            val sy = y - dy
            if (sy < 0 || sy >= top.height) continue
            for (x in 0 until w) {
                val sx = x - dx
                if (sx < 0 || sx >= top.width) continue
                out[y * w + x] = blend(top.pixels[sy * top.width + sx], out[y * w + x])
            }
        }
        return Raster(w, h, out)
    }

    /** [source] over [destination], straight alpha. */
    private fun blend(source: Int, destination: Int): Int {
        val sa = source ushr 24
        if (sa == 255) return source
        if (sa == 0) return destination
        val da = destination ushr 24
        if (da == 0) return source
        val s = sa / 255f
        val d = da / 255f * (1f - s)
        val a = s + d
        fun channel(shift: Int): Int {
            val sc = (source shr shift) and 0xFF
            val dc = (destination shr shift) and 0xFF
            return ((sc * s + dc * d) / a).roundToInt().coerceIn(0, 255)
        }
        return ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val ALPHA = -0x1000000
    private const val RGB = 0xFFFFFF
}
