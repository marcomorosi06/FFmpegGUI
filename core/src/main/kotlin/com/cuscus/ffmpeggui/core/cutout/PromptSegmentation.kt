package com.cuscus.ffmpeggui.core.cutout

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * What goes into a Segment Anything 2 model and what comes out of it, as
 * SAM 2's image predictor prepares it (EdgeTAM, exported to ONNX by
 * scripts/export-edgetam.py, which writes this same interface):
 *
 *  - the picture stretched to a [SIDE] x [SIDE] square (SAM 2 does not keep
 *    its proportions, it resizes to the square), its values divided by 255,
 *    moved by [MEAN] and divided by [DEVIATION] (red, green, blue), channels
 *    first;
 *  - points on the picture, in the pixels of that square, each with a label:
 *    1 for "this is part of it", 0 for "this is not";
 *  - out come three masks of [MASK_SIDE] x [MASK_SIDE] values (logits, the
 *    square seen four times smaller; above 0 is in), each with how good the
 *    model thinks it is.
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

    /**
     * [argb] (0xAARRGGBB, row by row) of the picture already stretched to
     * [SIDE] x [SIDE] as the model's input: three normalised planes.
     */
    fun input(argb: IntArray): FloatArray {
        val plane = SIDE * SIDE
        require(argb.size == plane) { "Stretch the picture to $SIDE x $SIDE first" }
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = argb[i]
            out[i] = ((((p shr 16) and 0xFF) / 255f) - MEAN[0]) / DEVIATION[0]
            out[plane + i] = ((((p shr 8) and 0xFF) / 255f) - MEAN[1]) / DEVIATION[1]
            out[2 * plane + i] = (((p and 0xFF) / 255f) - MEAN[2]) / DEVIATION[2]
        }
        return out
    }

    /**
     * The points [xs], [ys] (in pixels of a picture [width] x [height]) as the
     * model takes them: x, y pairs in the pixels of the square, each side
     * stretched on its own as the picture is.
     */
    fun points(xs: FloatArray, ys: FloatArray, width: Int, height: Int): FloatArray {
        require(xs.size == ys.size) { "As many x as y" }
        require(width > 0 && height > 0) { "Empty picture" }
        val sx = SIDE.toFloat() / width
        val sy = SIDE.toFloat() / height
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
     * [height]: read where the pixel's centre falls on the mask, which covers
     * the whole picture, between the four nearest values. Above 0, the model
     * counts the pixel in.
     */
    fun logitAt(logits: FloatArray, index: Int, x: Int, y: Int, width: Int, height: Int): Float {
        val count = MASK_SIDE * MASK_SIDE
        require(logits.size >= (index + 1) * count) { "Missing mask $index" }
        return sample(logits, index * count, x, y, MASK_SIDE.toFloat() / width, MASK_SIDE.toFloat() / height)
    }

    /**
     * Which pixels of a picture of [width] x [height] mask [index] holds (its
     * logit, read as [logitAt] reads it, above 0): the masks' coarse grid
     * becomes smooth edges, as SAM 2's own upscaling makes them.
     */
    fun region(logits: FloatArray, index: Int, width: Int, height: Int): BooleanArray {
        val count = MASK_SIDE * MASK_SIDE
        require(logits.size >= (index + 1) * count) { "Missing mask $index" }
        val toMaskX = MASK_SIDE.toFloat() / width
        val toMaskY = MASK_SIDE.toFloat() / height
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

    /**
     * For a single tap (a subject lifted off the picture): of the masks that
     * hold the tapped point, the one the model scores best, unless it covers
     * nearly the whole picture (the background rather than a thing on it), when
     * the best of the others is taken. Null when no mask holds the point.
     */
    fun chooseForTap(logits: FloatArray, scores: FloatArray, count: Int, x: Float, y: Float, width: Int, height: Int): Int? {
        require(count >= 1 && scores.size >= count) { "A score for each mask" }
        val px = pixel(x, width)
        val py = pixel(y, height)
        val holding = (0 until count).filter { logitAt(logits, it, px, py, width, height) > 0f }
        if (holding.isEmpty()) return null
        val plane = MASK_SIDE * MASK_SIDE
        fun share(index: Int): Float {
            var inside = 0
            val offset = index * plane
            for (i in 0 until plane) if (logits[offset + i] > 0f) inside++
            return inside.toFloat() / plane
        }
        val things = holding.filter { share(it) < MAX_TAP_SHARE }
        return (things.ifEmpty { holding }).maxBy { scores[it] }
    }

    private fun pixel(v: Float, size: Int): Int = v.toInt().coerceIn(0, size - 1)

    /** The share of the points a mask must agree with to be taken (see [choose]). */
    private const val AGREEMENT = 0.8f

    /** A mask covering more of the picture than this is its background, not a thing on it (see [chooseForTap]). */
    const val MAX_TAP_SHARE = 0.92f
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
