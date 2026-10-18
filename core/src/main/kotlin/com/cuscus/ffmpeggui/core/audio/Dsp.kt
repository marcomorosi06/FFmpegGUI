package com.cuscus.ffmpeggui.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/*
 * Sample-by-sample ports of the FFmpeg 6.1 audio filters the editor uses, so
 * that the preview sounds like the export. Each one follows the C code of the
 * filter named in its comment: the same coefficients, tables, rounding and
 * order of operations. All work on interleaved float frames, in place, and
 * keep their state between calls like the filter keeps it between frames.
 */

/** Processes interleaved frames in place. */
interface StageProcessor {
    fun process(buffer: FloatArray, frames: Int)
}

/** Lets the sound through unchanged (format changes have no sound of their own). */
internal object PassThrough : StageProcessor {
    override fun process(buffer: FloatArray, frames: Int) = Unit
}

// -- Wave tables (generate_wave_table.c) ---------------------------------------------

internal enum class Wave { SIN, TRI }

private fun waveValue(wave: Wave, point: Long, size: Int): Double = when (wave) {
    Wave.SIN -> (sin(point.toDouble() / size * 2 * PI) + 1) / 2
    Wave.TRI -> {
        val d = point.toDouble() * 2 / size
        when ((4 * point / size).toInt()) {
            0 -> d + 0.5
            1, 2 -> 1.5 - d
            else -> d - 1.5
        }
    }
}

private fun phaseOffset(phase: Double, size: Int): Long = (phase / PI / 2 * size + 0.5).toLong()

internal fun waveTableDouble(wave: Wave, size: Int, min: Double, max: Double, phase: Double): DoubleArray {
    val offset = phaseOffset(phase, size)
    return DoubleArray(size) { i -> waveValue(wave, (i + offset) % size, size) * (max - min) + min }
}

internal fun waveTableFloat(wave: Wave, size: Int, min: Double, max: Double, phase: Double): FloatArray {
    val offset = phaseOffset(phase, size)
    return FloatArray(size) { i -> (waveValue(wave, (i + offset) % size, size) * (max - min) + min).toFloat() }
}

internal fun waveTableInt(wave: Wave, size: Int, min: Double, max: Double, phase: Double): IntArray {
    val offset = phaseOffset(phase, size)
    return IntArray(size) { i ->
        var d = waveValue(wave, (i + offset) % size, size) * (max - min) + min
        d += if (d < 0) -0.5 else 0.5
        d.toInt()
    }
}

/** C lrint (round half to even). */
internal fun lrint(value: Double): Int = Math.rint(value).toInt()

// -- Biquads (af_biquads.c, direct form I) -------------------------------------------

/** The shape of a two-pole filter, as af_biquads names it. */
enum class BiquadType { HIGHPASS, LOWPASS, EQUALIZER, BASS, TREBLE }

/** How the width of a biquad is given (af_biquads width_type). */
enum class WidthType { Q, OCTAVE }

/**
 * Normalised coefficients of an af_biquads filter (b0 b1 b2 a1 a2), or null
 * when FFmpeg bypasses it (frequency at or above half the sample rate).
 */
internal fun biquadCoefficients(
    type: BiquadType,
    sampleRate: Int,
    frequency: Double,
    width: Double,
    widthType: WidthType,
    gainDb: Double,
): DoubleArray? {
    val a = 10.0.pow(gainDb / 40)
    val w0 = 2 * PI * frequency / sampleRate
    if (w0 > PI || w0 <= 0.0 || width <= 0.0) return null
    val alpha = when (widthType) {
        WidthType.Q -> sin(w0) / (2 * width)
        WidthType.OCTAVE -> sin(w0) * sinh(ln(2.0) / 2 * width * w0 / sin(w0))
    }
    val c = cos(w0)
    val coefficients: DoubleArray = when (type) {
        BiquadType.EQUALIZER -> doubleArrayOf(1 + alpha / a, -2 * c, 1 - alpha / a, 1 + alpha * a, -2 * c, 1 - alpha * a)
        BiquadType.BASS -> {
            val beta = sqrt((a * a + 1) - (a - 1) * (a - 1))
            doubleArrayOf(
                (a + 1) + (a - 1) * c + beta * alpha,
                -2 * ((a - 1) + (a + 1) * c),
                (a + 1) + (a - 1) * c - beta * alpha,
                a * ((a + 1) - (a - 1) * c + beta * alpha),
                2 * a * ((a - 1) - (a + 1) * c),
                a * ((a + 1) - (a - 1) * c - beta * alpha),
            )
        }
        BiquadType.TREBLE -> {
            val beta = sqrt((a * a + 1) - (a - 1) * (a - 1))
            doubleArrayOf(
                (a + 1) - (a - 1) * c + beta * alpha,
                2 * ((a - 1) - (a + 1) * c),
                (a + 1) - (a - 1) * c - beta * alpha,
                a * ((a + 1) + (a - 1) * c + beta * alpha),
                -2 * a * ((a - 1) + (a + 1) * c),
                a * ((a + 1) + (a - 1) * c - beta * alpha),
            )
        }
        BiquadType.LOWPASS -> doubleArrayOf(1 + alpha, -2 * c, 1 - alpha, (1 - c) / 2, 1 - c, (1 - c) / 2)
        BiquadType.HIGHPASS -> doubleArrayOf(1 + alpha, -2 * c, 1 - alpha, (1 + c) / 2, -(1 + c), (1 + c) / 2)
    }
    // a0 a1 a2 b0 b1 b2 -> b0 b1 b2 a1 a2, divided by a0; FFmpeg filters float audio with
    // float coefficients.
    val a0 = coefficients[0]
    return doubleArrayOf(
        (coefficients[3] / a0).toFloat().toDouble(),
        (coefficients[4] / a0).toFloat().toDouble(),
        (coefficients[5] / a0).toFloat().toDouble(),
        (coefficients[1] / a0).toFloat().toDouble(),
        (coefficients[2] / a0).toFloat().toDouble(),
    )
}

/** Gain in dB of the biquad [coefficients] at [hz] (what the equaliser panel draws). */
fun biquadResponseDb(coefficients: DoubleArray, sampleRate: Int, hz: Double): Double {
    val w = 2 * PI * hz / sampleRate
    val (b0, b1, b2, a1, a2) = coefficients
    val cw = cos(w)
    val c2w = cos(2 * w)
    val sw = sin(w)
    val s2w = sin(2 * w)
    val numRe = b0 + b1 * cw + b2 * c2w
    val numIm = -(b1 * sw + b2 * s2w)
    val denRe = 1 + a1 * cw + a2 * c2w
    val denIm = -(a1 * sw + a2 * s2w)
    val magnitude = sqrt((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
    return 20 * kotlin.math.log10(magnitude.coerceAtLeast(1e-9))
}

internal class BiquadProcessor(private val c: DoubleArray, private val channels: Int) : StageProcessor {
    private val i1 = DoubleArray(channels)
    private val i2 = DoubleArray(channels)
    private val o1 = DoubleArray(channels)
    private val o2 = DoubleArray(channels)

    override fun process(buffer: FloatArray, frames: Int) {
        val b0 = c[0]; val b1 = c[1]; val b2 = c[2]; val a1 = c[3]; val a2 = c[4]
        for (ch in 0 until channels) {
            var x1 = i1[ch]; var x2 = i2[ch]; var y1 = o1[ch]; var y2 = o2[ch]
            var index = ch
            for (n in 0 until frames) {
                val x = buffer[index].toDouble()
                // Rounded to float at every step, like the float filter.
                val y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2).toFloat().toDouble()
                x2 = x1; x1 = x
                y2 = y1; y1 = y
                buffer[index] = y.toFloat()
                index += channels
            }
            i1[ch] = x1; i2[ch] = x2; o1[ch] = y1; o2[ch] = y2
        }
    }
}

// -- aecho -----------------------------------------------------------------------------

internal class EchoProcessor(
    sampleRate: Int,
    private val channels: Int,
    inGain: Float,
    outGain: Float,
    delaysMs: List<Float>,
    decays: List<Float>,
) : StageProcessor {
    private val inGain = inGain.toDouble()
    private val outGain = outGain.toDouble()
    private val decays = DoubleArray(decays.size) { decays[it].toDouble() }
    private val samples = IntArray(delaysMs.size) { (delaysMs[it] * sampleRate / 1000.0).toInt() }
    private val maxSamples = (samples.maxOrNull() ?: 0).coerceAtLeast(1)
    private val delay = Array(channels) { FloatArray(maxSamples) }
    private var delayIndex = 0

    override fun process(buffer: FloatArray, frames: Int) {
        var index = delayIndex
        for (ch in 0 until channels) {
            val dbuf = delay[ch]
            index = delayIndex
            var at = ch
            for (n in 0 until frames) {
                val input = buffer[at].toDouble()
                var out = input * inGain
                for (j in decays.indices) {
                    var ix = index + maxSamples - samples[j]
                    if (ix >= maxSamples) ix -= maxSamples
                    out += dbuf[ix] * decays[j]
                }
                out *= outGain
                buffer[at] = out.coerceIn(-1.0, 1.0).toFloat()
                dbuf[index] = input.toFloat()
                index += 1
                if (index >= maxSamples) index -= maxSamples
                at += channels
            }
        }
        delayIndex = index
    }
}

// -- chorus ----------------------------------------------------------------------------

/** One voice of a chorus: delay and depth in ms, its level and its speed in Hz. */
data class ChorusVoice(val delayMs: Float, val decay: Float, val speedHz: Float, val depthMs: Float)

internal class ChorusProcessor(
    sampleRate: Int,
    private val channels: Int,
    private val inGain: Float,
    private val outGain: Float,
    voices: List<ChorusVoice>,
) : StageProcessor {
    private val decays = FloatArray(voices.size) { voices[it].decay }
    private val lengths = IntArray(voices.size) { (sampleRate.toFloat() / voices[it].speedHz).toInt().coerceAtLeast(1) }
    private val tables = Array(voices.size) { n ->
        val depthSamples = (voices[n].depthMs.toDouble() * sampleRate / 1000.0).toInt()
        waveTableInt(Wave.SIN, lengths[n], 0.0, depthSamples.toDouble(), 0.0)
    }
    private val maxSamples = voices.maxOfOrNull { ((it.delayMs + it.depthMs).toDouble() * sampleRate / 1000.0).toInt() }
        ?.coerceAtLeast(1) ?: 1
    private val buffers = Array(channels) { FloatArray(maxSamples) }
    private val counters = IntArray(channels)
    private val phases = Array(channels) { IntArray(voices.size) }

    override fun process(buffer: FloatArray, frames: Int) {
        for (ch in 0 until channels) {
            val chorusbuf = buffers[ch]
            val phase = phases[ch]
            var counter = counters[ch]
            var at = ch
            for (i in 0 until frames) {
                val input = buffer[at]
                var out = input * inGain
                for (n in decays.indices) {
                    var ix = maxSamples + counter - tables[n][phase[n]]
                    if (ix >= maxSamples) ix -= maxSamples
                    out += chorusbuf[ix] * decays[n]
                    phase[n] = if (phase[n] + 1 >= lengths[n]) phase[n] + 1 - lengths[n] else phase[n] + 1
                }
                out *= outGain
                buffer[at] = out
                chorusbuf[counter] = input
                counter = if (counter + 1 >= maxSamples) counter + 1 - maxSamples else counter + 1
                at += channels
            }
            counters[ch] = counter
        }
    }
}

// -- flanger ---------------------------------------------------------------------------

internal class FlangerProcessor(
    sampleRate: Int,
    private val channels: Int,
    delayMs: Double,
    depthMs: Double,
    regenPercent: Double,
    widthPercent: Double,
    speedHz: Double,
    wave: Wave,
    phasePercent: Double,
) : StageProcessor {
    private val feedbackGain = regenPercent / 100
    private val channelPhase = phasePercent / 100
    private val inGain: Double
    private val delayGain: Double
    private val maxSamples: Int
    private val lfoLength: Int
    private val lfo: FloatArray
    private val delayBuffer: Array<DoubleArray>
    private val delayLast = DoubleArray(channels)
    private var delayBufPos = 0
    private var lfoPos = 0

    init {
        var gain = widthPercent / 100
        inGain = 1 / (1 + gain)
        gain /= 1 + gain
        gain *= 1 - abs(feedbackGain)
        delayGain = gain
        val delayMin = delayMs / 1000
        val delayDepth = depthMs / 1000
        maxSamples = ((delayMin + delayDepth) * sampleRate + 2.5).toInt()
        lfoLength = (sampleRate / speedHz).toInt().coerceAtLeast(1)
        lfo = waveTableFloat(wave, lfoLength, Math.rint(delayMin * sampleRate), maxSamples - 2.0, 3 * PI / 2)
        delayBuffer = Array(channels) { DoubleArray(maxSamples) }
    }

    override fun process(buffer: FloatArray, frames: Int) {
        var at = 0
        for (i in 0 until frames) {
            delayBufPos = (delayBufPos + maxSamples - 1) % maxSamples
            for (ch in 0 until channels) {
                val phaseShift = (ch * lfoLength * channelPhase + .5).toInt()
                val delay = lfo[(lfoPos + phaseShift) % lfoLength].toDouble()
                var intDelay = delay.toInt()
                val frac = delay - floor(delay)
                val line = delayBuffer[ch]
                val input = buffer[at + ch].toDouble()
                line[delayBufPos] = input + delayLast[ch] * feedbackGain
                val d0 = line[(delayBufPos + intDelay++) % maxSamples]
                val d1 = line[(delayBufPos + intDelay) % maxSamples]
                val delayed = d0 + (d1 - d0) * frac
                delayLast[ch] = delayed
                buffer[at + ch] = (input * inGain + delayed * delayGain).toFloat()
            }
            lfoPos = (lfoPos + 1) % lfoLength
            at += channels
        }
    }
}

// -- aphaser ---------------------------------------------------------------------------

internal class PhaserProcessor(
    sampleRate: Int,
    private val channels: Int,
    private val inGain: Double,
    private val outGain: Double,
    delayMs: Double,
    private val decay: Double,
    speedHz: Double,
    wave: Wave,
) : StageProcessor {
    private val delayLength = (delayMs * 0.001 * sampleRate + 0.5).toInt().coerceAtLeast(1)
    private val modulationLength = (sampleRate / speedHz + 0.5).toInt().coerceAtLeast(1)
    private val modulation = waveTableInt(wave, modulationLength, 1.0, delayLength.toDouble(), PI / 2.0)
    private val buffers = Array(channels) { DoubleArray(delayLength) }
    private var delayPos = 0
    private var modulationPos = 0

    override fun process(buffer: FloatArray, frames: Int) {
        var endDelay = delayPos
        var endModulation = modulationPos
        for (ch in 0 until channels) {
            val line = buffers[ch]
            var dp = delayPos
            var mp = modulationPos
            var at = ch
            for (i in 0 until frames) {
                var ix = dp + modulation[mp]
                if (ix >= delayLength) ix -= delayLength
                val v = buffer[at] * inGain + line[ix] * decay
                mp = if (mp + 1 >= modulationLength) mp + 1 - modulationLength else mp + 1
                dp = if (dp + 1 >= delayLength) dp + 1 - delayLength else dp + 1
                line[dp] = v
                buffer[at] = (v * outGain).toFloat()
                at += channels
            }
            endDelay = dp
            endModulation = mp
        }
        delayPos = endDelay
        modulationPos = endModulation
    }
}

// -- tremolo ---------------------------------------------------------------------------

internal class TremoloProcessor(sampleRate: Int, private val channels: Int, frequency: Double, depth: Double) : StageProcessor {
    private val table: DoubleArray
    private var index = 0

    init {
        val offset = 1.0 - depth / 2.0
        val size = lrint(sampleRate / frequency + 0.5).coerceAtLeast(1)
        table = DoubleArray(size) { i ->
            val env = sin(2 * PI * ((frequency * i / sampleRate + 0.25) % 1.0))
            env * (1 - abs(offset)) + offset
        }
    }

    override fun process(buffer: FloatArray, frames: Int) {
        var at = 0
        for (n in 0 until frames) {
            val gain = table[index]
            for (c in 0 until channels) buffer[at + c] = (buffer[at + c] * gain).toFloat()
            at += channels
            index++
            if (index >= table.size) index = 0
        }
    }
}

// -- vibrato ---------------------------------------------------------------------------

internal class VibratoProcessor(sampleRate: Int, private val channels: Int, frequency: Double, private val depth: Double) : StageProcessor {
    private val bufSize = lrint(sampleRate * 0.005 + 0.5).coerceAtLeast(2)
    private val buffers = Array(channels) { DoubleArray(bufSize) }
    private val table = waveTableDouble(
        Wave.SIN, lrint(sampleRate / frequency + 0.5).coerceAtLeast(1), 0.0, bufSize - 1.0, 3.0 * PI / 2,
    )
    private var bufIndex = 0
    private var tableIndex = 0

    override fun process(buffer: FloatArray, frames: Int) {
        var at = 0
        for (n in 0 until frames) {
            val value = depth * table[tableIndex]
            val integer = floor(value)
            val decimal = value - integer
            tableIndex++
            if (tableIndex >= table.size) tableIndex -= table.size
            var s1 = bufIndex + integer.toInt()
            if (s1 >= bufSize) s1 -= bufSize
            var s2 = s1 + 1
            if (s2 >= bufSize) s2 -= bufSize
            for (c in 0 until channels) {
                val buf = buffers[c]
                val sample = buffer[at + c].toDouble()
                buffer[at + c] = (buf[s1] + decimal * (buf[s2] - buf[s1])).toFloat()
                buf[bufIndex] = sample
            }
            bufIndex++
            if (bufIndex >= bufSize) bufIndex -= bufSize
            at += channels
        }
    }
}

// -- acompressor (af_sidechaincompress.c) ----------------------------------------------

private fun hermite(x: Double, x0: Double, x1: Double, p0: Double, p1: Double, m0In: Double, m1In: Double): Double {
    val width = x1 - x0
    val t = (x - x0) / width
    val m0 = m0In * width
    val m1 = m1In * width
    val t2 = t * t
    val t3 = t2 * t
    val ct2 = -3 * p0 - 2 * m0 + 3 * p1 - m1
    val ct3 = 2 * p0 + m0 - 2 * p1 + m1
    return ct3 * t3 + ct2 * t2 + m0 * t + p0
}

/**
 * acompressor in downward mode with the given [threshold] and [makeup] (linear),
 * RMS detection when [rms], channels averaged unless [linkMaximum].
 */
internal class CompressorProcessor(
    sampleRate: Int,
    private val channels: Int,
    threshold: Double,
    private val ratio: Double,
    attackMs: Double,
    releaseMs: Double,
    private val makeup: Double,
    private val knee: Double,
    private val rms: Boolean,
    private val linkMaximum: Boolean,
) : StageProcessor {
    private val thres = ln(threshold)
    private val linKneeStart = threshold / sqrt(knee)
    private val linKneeStop = threshold * sqrt(knee)
    private val adjKneeStart = linKneeStart * linKneeStart
    private val kneeStart = ln(linKneeStart)
    private val kneeStop = ln(linKneeStop)
    private val compressedKneeStop = (kneeStop - thres) / ratio + thres
    private val attackCoeff = minOf(1.0, 1.0 / (attackMs * sampleRate / 4000.0))
    private val releaseCoeff = minOf(1.0, 1.0 / (releaseMs * sampleRate / 4000.0))
    private var linSlope = 0.0

    private fun outputGain(linSlope: Double): Double {
        var slope = ln(linSlope)
        if (rms) slope *= 0.5
        var gain = (slope - thres) / ratio + thres
        val delta = 1.0 / ratio
        if (knee > 1.0 && slope < kneeStop) {
            gain = hermite(slope, kneeStart, kneeStop, kneeStart, compressedKneeStop, 1.0, delta)
        }
        return exp(gain - slope)
    }

    override fun process(buffer: FloatArray, frames: Int) {
        var at = 0
        for (i in 0 until frames) {
            var absSample = abs(buffer[at].toDouble())
            if (linkMaximum) {
                for (c in 1 until channels) absSample = maxOf(abs(buffer[at + c].toDouble()), absSample)
            } else {
                for (c in 1 until channels) absSample += abs(buffer[at + c].toDouble())
                absSample /= channels
            }
            if (rms) absSample *= absSample
            linSlope += (absSample - linSlope) * (if (absSample > linSlope) attackCoeff else releaseCoeff)
            val detector = if (rms) adjKneeStart else linKneeStart
            var gain = 1.0
            if (linSlope > 0.0 && linSlope > detector) gain = outputGain(linSlope)
            val factor = gain * makeup
            for (c in 0 until channels) buffer[at + c] = (buffer[at + c] * factor).toFloat()
            at += channels
        }
    }
}

// -- agate -----------------------------------------------------------------------------

/** agate in downward mode, RMS detection, channels averaged. */
internal class GateProcessor(
    sampleRate: Int,
    private val channels: Int,
    threshold: Double,
    private val range: Double,
    private val ratio: Double,
    attackMs: Double,
    releaseMs: Double,
    private val knee: Double,
) : StageProcessor {
    private val attackCoeff = minOf(1.0, 1.0 / (attackMs * sampleRate / 4000.0))
    private val releaseCoeff = minOf(1.0, 1.0 / (releaseMs * sampleRate / 4000.0))
    private val linThreshold = threshold * threshold
    private val linKneeStop = linThreshold * sqrt(knee)
    private val linKneeStart = linThreshold / sqrt(knee)
    private val thres = ln(linThreshold)
    private val kneeStart = ln(linKneeStart)
    private val kneeStop = ln(linKneeStop)
    private var linSlope = 0.0

    private fun outputGain(linSlope: Double): Double {
        val slope = ln(linSlope)
        var gain = (slope - thres) * ratio + thres
        if (knee > 1.0 && slope > kneeStart) {
            gain = hermite(slope, kneeStart, kneeStop, (kneeStart - thres) * ratio + thres, kneeStop, ratio, 1.0)
        }
        return maxOf(range, exp(gain - slope))
    }

    override fun process(buffer: FloatArray, frames: Int) {
        var at = 0
        for (n in 0 until frames) {
            var absSample = abs(buffer[at].toDouble())
            for (c in 1 until channels) absSample += abs(buffer[at + c].toDouble())
            absSample /= channels
            absSample *= absSample
            linSlope += (absSample - linSlope) * (if (absSample > linSlope) attackCoeff else releaseCoeff)
            var gain = 1.0
            if (linSlope > 0.0 && linSlope < linKneeStop) gain = outputGain(linSlope)
            for (c in 0 until channels) buffer[at + c] = (buffer[at + c] * gain).toFloat()
            at += channels
        }
    }
}

// -- acrusher (linear, no anti-aliasing, no sample reduction) --------------------------

internal class CrusherProcessor(private val channels: Int, bits: Double) : StageProcessor {
    private val coeff = 2.0.pow(bits) - 1

    override fun process(buffer: FloatArray, frames: Int) {
        for (i in 0 until frames * channels) {
            val y = buffer[i] * coeff
            // C roundf: on the value as a float, halves away from zero.
            val f = y.toFloat()
            val k = if (f >= 0f) floor(f + 0.5f) else -floor(-f + 0.5f)
            buffer[i] = (k / coeff).toFloat()
        }
    }
}

// -- extrastereo, pan ------------------------------------------------------------------

/** extrastereo without clipping: each side moved away from (or towards) the middle. */
internal class WidthProcessor(private val channels: Int, private val multiplier: Float) : StageProcessor {
    override fun process(buffer: FloatArray, frames: Int) {
        if (channels < 2) return
        var at = 0
        for (n in 0 until frames) {
            val left = buffer[at]
            val right = buffer[at + 1]
            val average = ((left + right) / 2.0).toFloat()
            buffer[at] = average + multiplier * (left - average)
            buffer[at + 1] = average + multiplier * (right - average)
            at += channels
        }
    }
}

/** pan=stereo with a gain per side, the sides exchanged first when [swap]. */
internal class BalanceProcessor(private val channels: Int, private val leftGain: Float, private val rightGain: Float, private val swap: Boolean) : StageProcessor {
    override fun process(buffer: FloatArray, frames: Int) {
        if (channels < 2) return
        var at = 0
        for (n in 0 until frames) {
            val left = if (swap) buffer[at + 1] else buffer[at]
            val right = if (swap) buffer[at] else buffer[at + 1]
            buffer[at] = leftGain * left
            buffer[at + 1] = rightGain * right
            at += channels
        }
    }
}

/** pan=mono, heard on both sides: the export writes one channel. */
internal class MonoProcessor(private val channels: Int) : StageProcessor {
    override fun process(buffer: FloatArray, frames: Int) {
        if (channels < 2) return
        var at = 0
        for (n in 0 until frames) {
            val mid = 0.5f * buffer[at] + 0.5f * buffer[at + 1]
            buffer[at] = mid
            buffer[at + 1] = mid
            at += channels
        }
    }
}
