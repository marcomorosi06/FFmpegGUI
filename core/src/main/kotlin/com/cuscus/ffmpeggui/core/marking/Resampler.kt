package com.cuscus.ffmpeggui.core.marking

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Sound from one sample rate to another, with a windowed sinc (Hann, 16 zero
 * crossings each side, cut a little below the lower Nyquist so nothing folds
 * back). For the AI marks: AudioSeal works at 16 kHz, a dub is said at 22.05
 * or 24 kHz, so the speech goes down to 16 kHz and its mark comes back up.
 * Pure, tested.
 */
object Resampler {
    private const val HALF_WIDTH = 16
    private const val CUTOFF = 0.94

    /** The length [samples] samples at [from] take at [to]. */
    fun lengthAt(samples: Int, from: Int, to: Int): Int = (samples.toLong() * to / from).toInt()

    /** [input] (mono, at [from]) at [to]; [length] samples (the rate's own length by default). */
    fun resample(input: FloatArray, from: Int, to: Int, length: Int = lengthAt(input.size, from, to)): FloatArray {
        require(from > 0 && to > 0)
        if (from == to) return input.copyOf(length)
        val out = FloatArray(length)
        val step = from.toDouble() / to
        // Below the lower of the two Nyquists: the filter widens when going down.
        val cutoff = CUTOFF * min(1.0, to.toDouble() / from)
        val reach = HALF_WIDTH / cutoff
        for (n in 0 until length) {
            val centre = n * step
            val first = floor(centre - reach).toLong().coerceAtLeast(0L)
            val last = (centre + reach).toLong().coerceAtMost(input.size - 1L)
            var sum = 0.0
            var k = first
            while (k <= last) {
                val u = k - centre
                sum += input[k.toInt()] * kernel(u, cutoff, reach)
                k++
            }
            out[n] = sum.toFloat()
        }
        return out
    }

    private fun kernel(u: Double, cutoff: Double, reach: Double): Double {
        if (u <= -reach || u >= reach) return 0.0
        val x = PI * cutoff * u
        val sinc = if (u == 0.0) 1.0 else sin(x) / x
        val window = 0.5 + 0.5 * cos(PI * u / reach)
        return cutoff * sinc * window
    }

    /** Where [sample] at [from] falls at [to] (rounded). */
    fun position(sample: Long, from: Int, to: Int): Long = (sample.toDouble() * to / from).roundToLong()
}
