package com.cuscus.ffmpeggui.core.cutout

import kotlin.math.roundToInt

/**
 * The kinds of cut-out model, by what they take in, as rembg runs them: a
 * square picture of [side] pixels, its values divided by the largest of them,
 * then each channel moved by [mean] and divided by [deviation] (red, green,
 * blue), channels first.
 */
enum class SubjectNet(val side: Int, val mean: DoubleArray, val deviation: DoubleArray) {
    /** IS-Net (general use): large, slow, precise. */
    ISNET(1024, doubleArrayOf(0.5, 0.5, 0.5), doubleArrayOf(1.0, 1.0, 1.0)),

    /** U2-Net and its small sibling: the ImageNet statistics, a small picture; quick. */
    U2NET(320, doubleArrayOf(0.485, 0.456, 0.406), doubleArrayOf(0.229, 0.224, 0.225)),
}

/**
 * What goes into a cut-out model and what comes out of it (see [SubjectNet]):
 * out comes one map of the same size, stretched between its lowest and highest
 * value to 0..255, how much of each pixel is the subject.
 */
object SubjectModelIo {

    /** Side of the square picture IS-Net works on. */
    const val SIDE = 1024

    /** [argb] (0xAARRGGBB, side x side of [net], row by row) as the model's input: red, green and blue planes. */
    fun input(argb: IntArray, net: SubjectNet = SubjectNet.ISNET): FloatArray {
        val count = net.side * net.side
        require(argb.size == count) { "Expected $count pixels" }
        var largest = 0
        for (p in argb) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r > largest) largest = r
            if (g > largest) largest = g
            if (b > largest) largest = b
        }
        val scale = 1.0 / maxOf(largest.toDouble(), 1e-6)
        val (mr, mg, mb) = net.mean.toList()
        val (dr, dg, db) = net.deviation.toList()
        val out = FloatArray(3 * count)
        for (i in 0 until count) {
            val p = argb[i]
            out[i] = ((((p shr 16) and 0xFF) * scale - mr) / dr).toFloat()
            out[count + i] = ((((p shr 8) and 0xFF) * scale - mg) / dg).toFloat()
            out[2 * count + i] = (((p and 0xFF) * scale - mb) / db).toFloat()
        }
        return out
    }

    /** The model's output map as 0..255 per pixel, stretched between its lowest and highest value. */
    fun mask(output: FloatArray): IntArray {
        var low = Float.POSITIVE_INFINITY
        var high = Float.NEGATIVE_INFINITY
        for (v in output) {
            if (v < low) low = v
            if (v > high) high = v
        }
        val range = high - low
        if (!(range > 0f)) return IntArray(output.size)
        return IntArray(output.size) { i -> (((output[i] - low) / range) * 255f).roundToInt().coerceIn(0, 255) }
    }
}
