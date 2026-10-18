package com.cuscus.ffmpeggui.core.audio

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One step of the sound chain, described once and turned into both the FFmpeg
 * filter of the export ([filter]) and its sample-exact port for the preview
 * ([processor]). The numbers a stage holds are the ones written into the
 * filter, so both always work with the same values.
 */
sealed class AudioStage {
    abstract val filter: String

    abstract fun processor(sampleRate: Int, channels: Int): StageProcessor

    /** A two-pole filter of af_biquads. */
    data class Biquad(
        val type: BiquadType,
        val frequency: Double,
        val width: Double,
        val widthType: WidthType,
        val gainDb: Double = 0.0,
    ) : AudioStage() {
        override val filter: String
            get() {
                val name = when (type) {
                    BiquadType.HIGHPASS -> "highpass"
                    BiquadType.LOWPASS -> "lowpass"
                    BiquadType.EQUALIZER -> "equalizer"
                    BiquadType.BASS -> "bass"
                    BiquadType.TREBLE -> "treble"
                }
                val t = if (widthType == WidthType.Q) "q" else "o"
                val gain = if (type == BiquadType.HIGHPASS || type == BiquadType.LOWPASS) "" else ":g=${num(gainDb)}"
                return "$name=f=${num(frequency)}:t=$t:w=${num(width)}$gain"
            }

        /** The coefficients at [sampleRate], or null when FFmpeg leaves the sound alone. */
        fun coefficients(sampleRate: Int): DoubleArray? =
            biquadCoefficients(type, sampleRate, frequency, width, widthType, gainDb)

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            coefficients(sampleRate)?.let { BiquadProcessor(it, channels) } ?: PassThrough
    }

    /** agate: lowers what stays under [threshold] (linear) by up to [range]. */
    data class Gate(
        val threshold: Double,
        val range: Double,
        val ratio: Double,
        val attackMs: Double,
        val releaseMs: Double,
        val knee: Double,
    ) : AudioStage() {
        override val filter: String
            get() = "agate=threshold=${num(threshold)}:range=${num(range)}:ratio=${num(ratio)}:attack=${num(attackMs)}" +
                ":release=${num(releaseMs)}:knee=${num(knee)}:detection=rms:link=average"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            GateProcessor(sampleRate, channels, threshold, range, ratio, attackMs, releaseMs, knee)
    }

    /** acompressor (also the limiter, with a hard knee and peak detection). */
    data class Compressor(
        val threshold: Double,
        val ratio: Double,
        val attackMs: Double,
        val releaseMs: Double,
        val makeup: Double,
        val knee: Double,
        val rms: Boolean = true,
        val linkMaximum: Boolean = false,
    ) : AudioStage() {
        override val filter: String
            get() = "acompressor=threshold=${num(threshold)}:ratio=${num(ratio)}:attack=${num(attackMs)}" +
                ":release=${num(releaseMs)}:makeup=${num(makeup)}:knee=${num(knee)}" +
                ":detection=${if (rms) "rms" else "peak"}:link=${if (linkMaximum) "maximum" else "average"}"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            CompressorProcessor(sampleRate, channels, threshold, ratio, attackMs, releaseMs, makeup, knee, rms, linkMaximum)
    }

    /** acrusher reduced to its plain bit reduction. */
    data class Crusher(val bits: Int) : AudioStage() {
        override val filter: String get() = "acrusher=bits=$bits:mode=lin:aa=0:mix=0:samples=1"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor = CrusherProcessor(channels, bits.toDouble())
    }

    data class Chorus(val inGain: Float, val outGain: Float, val voices: List<ChorusVoice>) : AudioStage() {
        override val filter: String
            get() = "chorus=${num(inGain)}:${num(outGain)}:" +
                voices.joinToString("|") { num(it.delayMs) } + ":" +
                voices.joinToString("|") { num(it.decay) } + ":" +
                voices.joinToString("|") { num(it.speedHz) } + ":" +
                voices.joinToString("|") { num(it.depthMs) }

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            ChorusProcessor(sampleRate, channels, inGain, outGain, voices)
    }

    data class Flanger(
        val delayMs: Double,
        val depthMs: Double,
        val regenPercent: Double,
        val widthPercent: Double,
        val speedHz: Double,
        val phasePercent: Double,
    ) : AudioStage() {
        override val filter: String
            get() = "flanger=delay=${num(delayMs)}:depth=${num(depthMs)}:regen=${num(regenPercent)}:width=${num(widthPercent)}" +
                ":speed=${num(speedHz)}:shape=sinusoidal:phase=${num(phasePercent)}:interp=linear"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            FlangerProcessor(sampleRate, channels, delayMs, depthMs, regenPercent, widthPercent, speedHz, Wave.SIN, phasePercent)
    }

    data class Phaser(
        val inGain: Double,
        val outGain: Double,
        val delayMs: Double,
        val decay: Double,
        val speedHz: Double,
    ) : AudioStage() {
        override val filter: String
            get() = "aphaser=in_gain=${num(inGain)}:out_gain=${num(outGain)}:delay=${num(delayMs)}:decay=${num(decay)}" +
                ":speed=${num(speedHz)}:type=t"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            PhaserProcessor(sampleRate, channels, inGain, outGain, delayMs, decay, speedHz, Wave.TRI)
    }

    data class Tremolo(val frequency: Double, val depth: Double) : AudioStage() {
        override val filter: String get() = "tremolo=f=${num(frequency)}:d=${num(depth)}"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            TremoloProcessor(sampleRate, channels, frequency, depth)
    }

    data class Vibrato(val frequency: Double, val depth: Double) : AudioStage() {
        override val filter: String get() = "vibrato=f=${num(frequency)}:d=${num(depth)}"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            VibratoProcessor(sampleRate, channels, frequency, depth)
    }

    /** aecho: the sound plus delayed copies of it (also the reverb, with many short ones). */
    data class Echo(val inGain: Float, val outGain: Float, val delaysMs: List<Float>, val decays: List<Float>) : AudioStage() {
        override val filter: String
            get() = "aecho=${num(inGain)}:${num(outGain)}:" + delaysMs.joinToString("|") { num(it) } + ":" +
                decays.joinToString("|") { num(it) }

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            EchoProcessor(sampleRate, channels, inGain, outGain, delaysMs, decays)
    }

    /**
     * Two channels from here on (the stereo steps need them): a mono sound goes to
     * both sides at its full level, as a player plays it; nothing else changes.
     */
    data object StereoLayout : AudioStage() {
        override val filter: String get() = STEREO_UPMIX

        override fun processor(sampleRate: Int, channels: Int): StageProcessor = PassThrough
    }

    /** extrastereo without clipping. */
    data class Width(val multiplier: Float) : AudioStage() {
        override val filter: String get() = "extrastereo=m=${num(multiplier)}:c=0"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor = WidthProcessor(channels, multiplier)
    }

    /** pan: a gain per side, the sides exchanged first when [swap]. */
    data class Balance(val leftGain: Float, val rightGain: Float, val swap: Boolean) : AudioStage() {
        override val filter: String
            get() = "pan=stereo|c0=${num(leftGain)}*c${if (swap) 1 else 0}|c1=${num(rightGain)}*c${if (swap) 0 else 1}"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor =
            BalanceProcessor(channels, leftGain, rightGain, swap)
    }

    /** pan to one channel: the middle of the two. */
    data object Mono : AudioStage() {
        override val filter: String get() = "pan=mono|c0=0.5*c0+0.5*c1"

        override fun processor(sampleRate: Int, channels: Int): StageProcessor = MonoProcessor(channels)
    }
}

/**
 * Makes two channels of any sound without changing its level: left and right
 * stay, a mono sound (its single channel is the centre) goes to both sides at
 * full level. FFmpeg's own conversion would lower a mono sound by 3 dB.
 */
const val STEREO_UPMIX = "pan=stereo|FL=FL+FC|FR=FR+FC"

/** A number as written into a filter: at most six decimals, no trailing zeros. */
internal fun num(value: Double): String =
    BigDecimal(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

internal fun num(value: Float): String = num(value.toDouble())

/** [value] kept to the precision it is written with, so the preview uses exactly it. */
internal fun written(value: Double): Double = num(value).toDouble()

internal fun written(value: Float): Float = num(value).toFloat()
