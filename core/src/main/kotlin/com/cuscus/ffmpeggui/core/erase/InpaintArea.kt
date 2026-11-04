package com.cuscus.ffmpeggui.core.erase

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a picture is filled in by an inpainting model (MI-GAN, LaMa), and how
 * the result goes back into it. The models see a square of [SIDE] pixels:
 * the part of the picture around what is erased is cut out (with enough
 * around it for the model to see what should be there), squeezed to it, and
 * the model's picture is laid back only where the erased part was, its edge
 * faded so that no seam shows. Pure, tested.
 */
object InpaintArea {

    /** The side of the square the models see. */
    const val SIDE = 512

    /** A rectangle of the picture: left, top, right and bottom (exclusive). */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** [mask] (true: erased) grown by [radius] pixels every way, so that an outline or a shadow left at its edge goes too. */
    fun grow(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask.copyOf()
        // A square grown in two passes: along the rows, then down the columns.
        val rows = BooleanArray(mask.size)
        for (y in 0 until height) {
            var last = -1_000_000
            for (x in 0 until width) {
                if (mask[y * width + x]) last = x
                if (x - last <= radius) rows[y * width + x] = true
            }
            last = 1_000_000
            for (x in width - 1 downTo 0) {
                if (mask[y * width + x]) last = x
                if (last - x <= radius) rows[y * width + x] = true
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until width) {
            var last = -1_000_000
            for (y in 0 until height) {
                if (rows[y * width + x]) last = y
                if (y - last <= radius) out[y * width + x] = true
            }
            last = 1_000_000
            for (y in height - 1 downTo 0) {
                if (rows[y * width + x]) last = y
                if (last - y <= radius) out[y * width + x] = true
            }
        }
        return out
    }

    /** The smallest box holding everything erased in [mask], or null when nothing is. */
    fun bounds(mask: BooleanArray, width: Int, height: Int): Box? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (!mask[y * width + x]) continue
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
        }
        return if (right < 0) null else Box(left, top, right + 1, bottom + 1)
    }

    /**
     * The part of a [width] x [height] picture the model is shown for [erased]:
     * a square around it about [CONTEXT] times its size (so the model sees what
     * surrounds it), at least [SIDE] pixels where the picture is that large,
     * moved and cut to stay inside the picture.
     */
    fun crop(erased: Box, width: Int, height: Int): Box {
        val side = max(max(erased.width, erased.height) * CONTEXT, SIDE.toFloat()).roundToInt()
        val w = min(side, width)
        val h = min(side, height)
        val cx = (erased.left + erased.right) / 2
        val cy = (erased.top + erased.bottom) / 2
        val left = (cx - w / 2).coerceIn(0, width - w)
        val top = (cy - h / 2).coerceIn(0, height - h)
        return Box(left, top, left + w, top + h)
    }

    /**
     * How much of the model's picture each pixel of [mask] takes (0..1), with
     * its edge faded over [feather] pixels outward: 1 inside, falling to 0 that far out.
     */
    fun weights(mask: BooleanArray, width: Int, height: Int, feather: Int): FloatArray {
        val out = FloatArray(mask.size) { if (mask[it]) 1f else 0f }
        if (feather <= 0) return out
        // A box blur of the grown mask, kept at 1 inside: the fade lies outside the erased part.
        val grown = grow(mask, width, height, feather)
        val soft = blur(FloatArray(mask.size) { if (grown[it]) 1f else 0f }, width, height, feather)
        for (i in out.indices) if (!mask[i]) out[i] = (soft[i] * 2f - 1f).coerceIn(0f, 1f)
        return out
    }

    /** [values] blurred with a box of [radius], rows then columns. */
    private fun blur(values: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val tmp = FloatArray(values.size)
        for (y in 0 until height) {
            var sum = 0f
            val row = y * width
            for (x in -radius..radius) sum += values[row + x.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                tmp[row + x] = sum / (2 * radius + 1)
                sum += values[row + (x + radius + 1).coerceIn(0, width - 1)] - values[row + (x - radius).coerceIn(0, width - 1)]
            }
        }
        val out = FloatArray(values.size)
        for (x in 0 until width) {
            var sum = 0f
            for (y in -radius..radius) sum += tmp[y.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                out[y * width + x] = sum / (2 * radius + 1)
                sum += tmp[(y + radius + 1).coerceIn(0, height - 1) * width + x] - tmp[(y - radius).coerceIn(0, height - 1) * width + x]
            }
        }
        return out
    }

    /**
     * [made] laid on [picture] (both ARGB) by [weights], read from [from] on (a
     * row of a larger set of weights): the colour mixed, the picture's own alpha kept.
     */
    fun blend(picture: IntArray, made: IntArray, weights: FloatArray, from: Int = 0) {
        for (i in picture.indices) {
            val w = weights[from + i]
            if (w <= 0f) continue
            val p = picture[i]
            val m = made[i]
            fun mix(shift: Int): Int {
                val a = (p shr shift) and 0xFF
                val b = (m shr shift) and 0xFF
                return (a + (b - a) * w).roundToInt().coerceIn(0, 255)
            }
            picture[i] = (p and -0x1000000) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }

    /** How many times the erased part's size the crop is, at least. */
    const val CONTEXT = 2.2f
}
