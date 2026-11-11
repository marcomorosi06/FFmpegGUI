package com.cuscus.ffmpeggui.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The audio editor's spectrum: what is playing, split into bands from the
 * lows (left) to the highs (right), as bars that jump up with the sound and
 * fall back slowly, each with a cap that is thrown up with it and falls
 * back under its own weight.
 *
 * Ported from WiFi Audio Streaming's desktop spectrum (SpectrumAnalyzer.kt,
 * by the same author, under the same licence, EUPL-1.2): the same Hann
 * window and FFT, the same bands spaced evenly in pitch from 35 Hz to
 * 18 kHz (or the Nyquist frequency), each the loudest bin in it, the same tilt
 * (the highs lifted, as the ear hears them), the same decibel range and the
 * same rise, fall and caps. Two changes: several sounds at once (each player
 * of the preview, at its own rate and level) are added as their powers band
 * by band, and the rise and fall are timed in seconds, not in frames, so
 * that they look the same at any refresh rate. Pure, tested.
 */

/**
 * Bands of [bandCount] over windows of [windowSize] samples (a power of two):
 * the power each band of a window holds, added up over the sounds heard.
 */
class SpectrumBands(val bandCount: Int = DEFAULT_BANDS, val windowSize: Int = DEFAULT_WINDOW) {

    init {
        require(windowSize >= 16 && windowSize and (windowSize - 1) == 0) { "The window must be a power of two" }
        require(bandCount > 0) { "No band" }
    }

    private val window = DoubleArray(windowSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (windowSize - 1)) }
    private val re = DoubleArray(windowSize)
    private val im = DoubleArray(windowSize)
    private val cosT = DoubleArray(windowSize / 2) { cos(-2.0 * PI * it / windowSize) }
    private val sinT = DoubleArray(windowSize / 2) { sin(-2.0 * PI * it / windowSize) }
    private val bitRev = IntArray(windowSize).also { rev ->
        var j = 0
        for (i in 0 until windowSize) {
            rev[i] = j
            var m = windowSize shr 1
            while (m in 1..j) {
                j -= m
                m = m shr 1
            }
            j += m
        }
    }

    /** The bins of each band, by sample rate: a sound at another rate has other bins. */
    private val ranges = HashMap<Int, Pair<IntArray, IntArray>>()

    /** The first and last FFT bin of each band at [sampleRate]. */
    fun bins(sampleRate: Int): Pair<IntArray, IntArray> = ranges.getOrPut(sampleRate) {
        val lo = IntArray(bandCount)
        val hi = IntArray(bandCount)
        val top = (sampleRate / 2.0).coerceAtMost(F_MAX)
        val lnLo = ln(F_MIN)
        val lnHi = ln(top.coerceAtLeast(F_MIN * 2))
        val binHz = sampleRate.toDouble() / windowSize
        val last = windowSize / 2 - 1
        for (b in 0 until bandCount) {
            val f0 = exp(lnLo + (lnHi - lnLo) * b / bandCount)
            val f1 = exp(lnLo + (lnHi - lnLo) * (b + 1) / bandCount)
            lo[b] = (f0 / binHz).roundToInt().coerceIn(1, last)
            hi[b] = (f1 / binHz).roundToInt().coerceIn(1, last).coerceAtLeast(lo[b])
        }
        lo to hi
    }

    /**
     * Adds the power of each band of [samples] (the last [windowSize] of a
     * sound, mono, -1..1, at [sampleRate]), heard at [gain], into [powers]
     * ([bandCount] long): the loudest bin of each band, as an amplitude
     * where a full-scale sine is 1, squared.
     */
    fun add(samples: FloatArray, sampleRate: Int, gain: Float, powers: DoubleArray) {
        require(samples.size >= windowSize) { "Too few samples" }
        if (gain <= 0f || sampleRate <= 0) return
        val from = samples.size - windowSize
        for (i in 0 until windowSize) {
            re[i] = samples[from + i] * window[i]
            im[i] = 0.0
        }
        fft()
        val (lo, hi) = bins(sampleRate)
        // A full-scale sine through the Hann window (its coherent gain is one half) reads 1.
        val scale = 2.0 / (windowSize * 0.5) * gain
        for (b in 0 until bandCount) {
            var loudest = 0.0
            for (k in lo[b]..hi[b]) {
                val p = re[k] * re[k] + im[k] * im[k]
                if (p > loudest) loudest = p
            }
            powers[b] += loudest * scale * scale
        }
    }

    private fun fft() {
        val n = windowSize
        for (i in 0 until n) {
            val j = bitRev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val step = n / len
            val half = len / 2
            var i = 0
            while (i < n) {
                var k = 0
                for (j in 0 until half) {
                    val a = i + j
                    val b = a + half
                    val tr = re[b] * cosT[k] - im[b] * sinT[k]
                    val ti = re[b] * sinT[k] + im[b] * cosT[k]
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        const val DEFAULT_BANDS = 48
        const val DEFAULT_WINDOW = 2048
        const val F_MIN = 35.0
        const val F_MAX = 18_000.0
    }
}

/**
 * The bars drawn from the bands' powers: [bars] (0..1) rise quickly and fall
 * slowly, [peaks] (0..1) are pushed up by them and fall back under gravity.
 */
class SpectrumBars(val count: Int = SpectrumBands.DEFAULT_BANDS) {

    val bars = FloatArray(count)
    val peaks = FloatArray(count)
    private val peakSpeed = FloatArray(count)

    /** The level of each band of [powers], 0 (at [FLOOR_DB] or below) to 1 (at [CEIL_DB]), the highs tilted up. */
    fun levels(powers: DoubleArray, out: FloatArray) {
        for (b in 0 until count) {
            val tilt = TILT_DB * b / (count - 1).coerceAtLeast(1)
            val db = 10.0 * log10(powers[b] + 1e-24) + tilt
            out[b] = ((db - FLOOR_DB) / (CEIL_DB - FLOOR_DB)).toFloat().coerceIn(0f, 1f)
        }
    }

    private val target = FloatArray(count)

    /**
     * One step of [seconds]: the bars head for the levels of [powers] (null:
     * silence, they fall), the caps follow.
     */
    fun step(powers: DoubleArray?, seconds: Float) {
        if (powers != null) levels(powers, target) else target.fill(0f)
        val frames = (seconds / REFERENCE_FRAME_SECONDS).coerceIn(0f, MAX_FRAMES)
        val attack = 1f - (1f - ATTACK).pow(frames)
        val release = 1f - (1f - RELEASE).pow(frames)
        for (b in 0 until count) {
            val v = target[b]
            bars[b] += (v - bars[b]) * (if (v > bars[b]) attack else release)
            if (bars[b] >= peaks[b]) {
                peaks[b] = bars[b]
                peakSpeed[b] = 0f
            } else {
                peakSpeed[b] += PEAK_GRAVITY * frames
                peaks[b] -= peakSpeed[b] * frames
                if (peaks[b] < bars[b]) {
                    peaks[b] = bars[b]
                    peakSpeed[b] = 0f
                }
            }
        }
    }

    /** True when every bar and cap is down: nothing left to draw moving. */
    val settled: Boolean get() = bars.all { it < SETTLED } && peaks.all { it < SETTLED }

    companion object {
        /** Quietest and loudest a band shows, in dB from a full-scale sine. */
        const val FLOOR_DB = -66.0
        const val CEIL_DB = -9.0

        /** The highs lifted by up to this much, as the ear hears them. */
        const val TILT_DB = 6.0

        /** Share of the way up, and down, a bar goes in one [REFERENCE_FRAME_SECONDS]. */
        const val ATTACK = 0.55f
        const val RELEASE = 0.16f

        /** How fast a cap's fall speeds up, per [REFERENCE_FRAME_SECONDS]. */
        const val PEAK_GRAVITY = 0.006f

        /** The frame the rise and fall were tuned at (30 a second). */
        const val REFERENCE_FRAME_SECONDS = 1f / 30f

        /** A step longer than this (the app was away) counts as this much. */
        private const val MAX_FRAMES = 10f

        private const val SETTLED = 0.002f
    }
}

/** A sine of [frequency] Hz, [amplitude] at full scale, [length] samples at [sampleRate] (for tests and the debug bench). */
fun sineSamples(frequency: Double, sampleRate: Int, length: Int, amplitude: Double = 1.0): FloatArray =
    FloatArray(length) { (amplitude * sin(2.0 * PI * frequency * it / sampleRate)).toFloat() }

/** The band [frequency] falls in, for [bands] at [sampleRate]; -1 when below the first. */
fun SpectrumBands.bandOf(frequency: Double, sampleRate: Int): Int {
    val top = (sampleRate / 2.0).coerceAtMost(SpectrumBands.F_MAX)
    if (frequency < SpectrumBands.F_MIN) return -1
    val position = ln(frequency / SpectrumBands.F_MIN) / ln(top / SpectrumBands.F_MIN) * bandCount
    return position.toInt().coerceIn(0, bandCount - 1)
}

/** The amplitude of a band's power (see SpectrumBands.add). */
fun bandAmplitude(power: Double): Double = sqrt(power)
