package com.cuscus.ffmpeggui.core.cutout

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * What goes into a Segment Anything model and what comes out of it, as the
 * Hugging Face processor prepares it (SlimSAM, converted for transformers.js):
 *
 *  - the picture, its longer side brought to [SIDE] pixels (the shorter in
 *    proportion), its values divided by 255, moved by [MEAN] and divided by
 *    [DEVIATION] (red, green, blue), channels first, then padded with zeros on
 *    the right and at the bottom to a [SIDE] x [SIDE] square;
 *  - points on the picture, in the pixels of that resized picture, each with a
 *    label: 1 for "this is part of it", 0 for "this is not";
 *  - out come three masks of [MASK_SIDE] x [MASK_SIDE] values (logits, the
 *    padded square seen four times smaller; above 0 is in), each with how good
 *    the model thinks it is.
 */
object PromptSegmentation {

    /** Side of the square the model sees. */
    const val SIDE = 1024

    /** Side of the masks it gives. */
    const val MASK_SIDE = 256

    val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val DEVIATION = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** Label of a point on what is wanted. */
    const val INSIDE = 1L

    /** Label of a point on what is not. */
    const val OUTSIDE = 0L

    /** The size a picture of [width] x [height] is brought to: its longer side [SIDE], as the processor rounds it. */
    fun resizedSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Empty picture" }
        val scale = SIDE.toDouble() / max(width, height)
        val w = floor(width * scale + 0.5).toInt().coerceIn(1, SIDE)
        val h = floor(height * scale + 0.5).toInt().coerceIn(1, SIDE)
        return w to h
    }

    /**
     * [argb] (0xAARRGGBB, row by row) of the picture already brought to
     * [width] x [height] (see [resizedSize]) as the model's input: three planes
     * of [SIDE] x [SIDE], normalised, padded with zeros.
     */
    fun input(argb: IntArray, width: Int, height: Int): FloatArray {
        require(width in 1..SIDE && height in 1..SIDE) { "Bring the picture to $SIDE pixels first" }
        require(argb.size == width * height) { "Expected ${width * height} pixels" }
        val plane = SIDE * SIDE
        val out = FloatArray(3 * plane)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = argb[y * width + x]
                val i = y * SIDE + x
                out[i] = ((((p shr 16) and 0xFF) / 255f) - MEAN[0]) / DEVIATION[0]
                out[plane + i] = ((((p shr 8) and 0xFF) / 255f) - MEAN[1]) / DEVIATION[1]
                out[2 * plane + i] = (((p and 0xFF) / 255f) - MEAN[2]) / DEVIATION[2]
            }
        }
        return out
    }

    /**
     * The points [xs], [ys] (in pixels of a picture [width] x [height]) as the
     * model takes them: x, y pairs in the pixels of the resized picture.
     */
    fun points(xs: FloatArray, ys: FloatArray, width: Int, height: Int): FloatArray {
        require(xs.size == ys.size) { "As many x as y" }
        val (rw, rh) = resizedSize(width, height)
        val sx = rw.toFloat() / width
        val sy = rh.toFloat() / height
        val out = FloatArray(xs.size * 2)
        for (i in xs.indices) {
            out[2 * i] = xs[i] * sx
            out[2 * i + 1] = ys[i] * sy
        }
        return out
    }

    /**
     * The logit of mask [index] of [logits] (masks of [MASK_SIDE] x [MASK_SIDE],
     * one after the other) at pixel [x], [y] of a picture of [width] x
     * [height]: read where the pixel's centre falls once the picture is resized
     * and padded (the square's top-left part), between the four nearest values.
     * Above 0, the model counts the pixel in.
     */
    fun logitAt(logits: FloatArray, index: Int, x: Int, y: Int, width: Int, height: Int): Float {
        val count = MASK_SIDE * MASK_SIDE
        require(logits.size >= (index + 1) * count) { "Missing mask $index" }
        val (rw, rh) = resizedSize(width, height)
        return sample(logits, index * count, x, y, rw.toFloat() / width * MASK_SIDE / SIDE, rh.toFloat() / height * MASK_SIDE / SIDE)
    }

    /**
     * Which pixels of a picture of [width] x [height] mask [index] holds (its
     * logit, read as [logitAt] reads it, above 0): the masks' coarse grid
     * becomes smooth edges, as the processor's own resizing makes them.
     */
    fun region(logits: FloatArray, index: Int, width: Int, height: Int): BooleanArray {
        val count = MASK_SIDE * MASK_SIDE
        require(logits.size >= (index + 1) * count) { "Missing mask $index" }
        val (rw, rh) = resizedSize(width, height)
        val toMaskX = rw.toFloat() / width * MASK_SIDE / SIDE
        val toMaskY = rh.toFloat() / height * MASK_SIDE / SIDE
        val offset = index * count
        val out = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[y * width + x] = sample(logits, offset, x, y, toMaskX, toMaskY) > 0f
            }
        }
        return out
    }

    private fun sample(logits: FloatArray, offset: Int, x: Int, y: Int, toMaskX: Float, toMaskY: Float): Float {
        val my = ((y + 0.5f) * toMaskY - 0.5f).coerceIn(0f, (MASK_SIDE - 1).toFloat())
        val y0 = my.toInt()
        val y1 = min(y0 + 1, MASK_SIDE - 1)
        val fy = my - y0
        val mx = ((x + 0.5f) * toMaskX - 0.5f).coerceIn(0f, (MASK_SIDE - 1).toFloat())
        val x0 = mx.toInt()
        val x1 = min(x0 + 1, MASK_SIDE - 1)
        val fx = mx - x0
        val top = logits[offset + y0 * MASK_SIDE + x0] * (1 - fx) + logits[offset + y0 * MASK_SIDE + x1] * fx
        val bottom = logits[offset + y1 * MASK_SIDE + x0] * (1 - fx) + logits[offset + y1 * MASK_SIDE + x1] * fx
        return top * (1 - fy) + bottom * fy
    }

    /** The mask taken, and the share of the points it agrees with. */
    data class Choice(val index: Int, val agreement: Float)

    /**
     * Which of the [count] masks of [logits] to take, for a picture of [width]
     * x [height]: of those that agree with most of the points ([inside] meant
     * to be in, [outside] meant to be out, x, y pairs in pixels), the one the
     * model scores best ([scores], one per mask); when none does, the one that
     * agrees with the most.
     */
    fun choose(logits: FloatArray, scores: FloatArray, count: Int, inside: FloatArray, outside: FloatArray, width: Int, height: Int): Choice {
        require(count >= 1 && scores.size >= count) { "A score for each mask" }
        val total = max((inside.size + outside.size) / 2, 1)
        val agreements = FloatArray(count) { index ->
            var agree = 0
            for (k in 0 until inside.size / 2) {
                if (logitAt(logits, index, pixel(inside[2 * k], width), pixel(inside[2 * k + 1], height), width, height) > 0f) agree++
            }
            for (k in 0 until outside.size / 2) {
                if (logitAt(logits, index, pixel(outside[2 * k], width), pixel(outside[2 * k + 1], height), width, height) <= 0f) agree++
            }
            agree.toFloat() / total
        }
        val agreeing = (0 until count).filter { agreements[it] >= AGREEMENT }
        val index = if (agreeing.isNotEmpty()) {
            agreeing.maxBy { scores[it] }
        } else {
            (0 until count).maxWith(compareBy<Int> { agreements[it] }.thenBy { scores[it] })
        }
        return Choice(index, agreements[index])
    }

    private fun pixel(v: Float, size: Int): Int = v.toInt().coerceIn(0, size - 1)

    /** The share of the points a mask must agree with to be taken (see [choose]). */
    private const val AGREEMENT = 0.8f
}

/**
 * The points a finger's strokes give the model: along each stroke, at least
 * [spacing] pixels apart, no more than [limit] for all the strokes together
 * (Segment Anything works best with a few points; taken evenly from every
 * stroke when there are more).
 */
object StrokePoints {

    /**
     * [strokes]: each stroke as x, y pairs in the picture's pixels, in the order
     * drawn. Returns the points as x, y pairs.
     */
    fun of(strokes: List<FloatArray>, spacing: Float, limit: Int): FloatArray {
        require(spacing > 0f && limit > 0) { "Spacing and limit must be positive" }
        val all = ArrayList<Float>()
        for (stroke in strokes) {
            if (stroke.size < 2) continue
            var lastX = stroke[0]
            var lastY = stroke[1]
            all += lastX
            all += lastY
            var walked = 0f
            var i = 2
            while (i + 1 < stroke.size) {
                val x = stroke[i]
                val y = stroke[i + 1]
                walked += hypot(x - lastX, y - lastY)
                if (walked >= spacing) {
                    all += x
                    all += y
                    walked = 0f
                }
                lastX = x
                lastY = y
                i += 2
            }
            // The end of a long stroke counts too.
            if (walked >= spacing / 2 && stroke.size >= 4) {
                all += stroke[stroke.size - 2]
                all += stroke[stroke.size - 1]
            }
        }
        val count = all.size / 2
        if (count <= limit) return all.toFloatArray()
        // Evenly spread over all of them.
        val out = FloatArray(limit * 2)
        for (k in 0 until limit) {
            val src = ((k + 0.5f) * count / limit).toInt().coerceIn(0, count - 1)
            out[2 * k] = all[2 * src]
            out[2 * k + 1] = all[2 * src + 1]
        }
        return out
    }
}
