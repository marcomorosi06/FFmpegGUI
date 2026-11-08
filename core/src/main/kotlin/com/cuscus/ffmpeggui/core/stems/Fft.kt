package com.cuscus.ffmpeggui.core.stems

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A complex fast Fourier transform of a fixed [size] (a power of two), in
 * place on separate real and imaginary arrays: iterative radix 2, its turn
 * factors and bit-reversed order worked out once. [forward] computes
 * X[k] = sum x[n] e^(-2 pi i k n / N); [inverse] the same with the opposite
 * sign, divided by N, so that one undoes the other.
 */
class Fft(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "The size must be a power of two" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
    private val reversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun forward(re: FloatArray, im: FloatArray) = transform(re, im, -1.0)

    fun inverse(re: FloatArray, im: FloatArray) {
        transform(re, im, 1.0)
        val scale = 1f / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: FloatArray, im: FloatArray, sign: Double) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var t = 0
                for (k in 0 until half) {
                    val wr = cosTable[t]
                    val wi = sign * sinTable[t]
                    val a = start + k
                    val b = a + half
                    val br = re[b].toDouble()
                    val bi = im[b].toDouble()
                    val xr = br * wr - bi * wi
                    val xi = br * wi + bi * wr
                    val ar = re[a].toDouble()
                    val ai = im[a].toDouble()
                    re[b] = (ar - xr).toFloat()
                    im[b] = (ai - xi).toFloat()
                    re[a] = (ar + xr).toFloat()
                    im[a] = (ai + xi).toFloat()
                    t += step
                }
                start += half * 2
            }
            half *= 2
        }
    }
}
