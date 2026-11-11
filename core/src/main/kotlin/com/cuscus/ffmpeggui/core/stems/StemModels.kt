package com.cuscus.ffmpeggui.core.stems

import kotlin.math.sqrt

/**
 * The parts a sound can be split into: dialogue, music and effects (Bandit
 * v2, [BanditMasks]), and the music in turn into singing, piano, drums, bass
 * and the other instruments (Spleeter's five stems, [SpleeterMasks]). [id]
 * names a stem's file and never changes.
 */
enum class StemKind(val id: String) {
    DIALOGUE("dialogue"),
    MUSIC("music"),
    EFFECTS("effects"),
    SINGING("singing"),
    PIANO("piano"),
    DRUMS("drums"),
    BASS("bass"),
    OTHER("other");

    /** True for the instruments the music is split into. */
    val isInstrument: Boolean get() = this in INSTRUMENTS

    companion object {
        /** What Bandit gives, in its order. */
        val SCENE: List<StemKind> = listOf(DIALOGUE, MUSIC, EFFECTS)

        /** What Spleeter's five stems give, in its order. */
        val INSTRUMENTS: List<StemKind> = listOf(SINGING, PIANO, DRUMS, BASS, OTHER)

        fun of(id: String): StemKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How far a sound is split into tracks, and with which models:
 *
 *  - [SCENE]: Bandit v2 alone, dialogue, music and effects (a video's sound);
 *  - [INSTRUMENTS]: Bandit, then Spleeter's five stems on the music Bandit
 *    found, so that the dialogue and the effects stay apart from the
 *    instruments (a video with music under it);
 *  - [SONG]: Spleeter alone, on the whole sound: a song has no dialogue or
 *    effects to set apart, and Spleeter is made for music. Bandit, by far
 *    the slower of the two, is not run at all.
 *
 * [kinds] are the stems each offers, in the order they are shown and placed.
 */
enum class StemSplit(val kinds: List<StemKind>, val usesBandit: Boolean, val usesSpleeter: Boolean) {
    SCENE(StemKind.SCENE, usesBandit = true, usesSpleeter = false),
    INSTRUMENTS(listOf(StemKind.DIALOGUE, StemKind.EFFECTS) + StemKind.INSTRUMENTS, usesBandit = true, usesSpleeter = true),
    SONG(StemKind.INSTRUMENTS, usesBandit = false, usesSpleeter = true),
    ;

    companion object {
        /** Where a split starts: a song for a sound alone (no picture), the scene for a video. */
        fun firstFor(soundOnly: Boolean): StemSplit = if (soundOnly) SONG else SCENE
    }
}

/**
 * Bandit v2 (Watcharasupat, Wu, Orife; weights CC BY-SA 4.0) as a
 * [StemSeparation.Model]: the sound split into dialogue, music and effects at
 * 48 kHz. The network is mono: it reads the middle of the two channels and
 * its complex masks are laid on each channel's own spectrum, so the stems
 * keep the stereo picture.
 *
 * It takes the spectrogram as torchaudio makes it for the model (2048 points,
 * hop 512, periodic Hann, divided by the square root of the window's energy),
 * laid out [1][bin][frame][real, imaginary], and gives the masks laid out
 * [1][stem][bin][frame][real, imaginary] (scripts/export-bandit.py). Effects
 * are what the dialogue and the music leave of the sound (see StemSeparation).
 */
class BanditMasks(private val network: Network) : StemSeparation.Model {

    /** The ONNX file: from the spectrogram of [frames] frames, the masks. */
    fun interface Network {
        fun run(spectrogram: FloatArray, frames: Int): FloatArray
    }

    private var input = FloatArray(0)

    override fun masks(window: StemSeparation.Window): StemSeparation.Masks {
        val frames = window.frames
        val bins = window.bins
        if (input.size != bins * frames * 2) input = FloatArray(bins * frames * 2)
        val (leftRe, rightRe) = window.re
        val (leftIm, rightIm) = window.im
        val scale = 0.5f * SCALE
        for (f in 0 until frames) {
            val row = f * bins
            for (k in 0 until bins) {
                val at = (k * frames + f) * 2
                input[at] = (leftRe[row + k] + rightRe[row + k]) * scale
                input[at + 1] = (leftIm[row + k] + rightIm[row + k]) * scale
            }
        }
        val out = network.run(input, frames)
        require(out.size == STEMS * bins * frames * 2) { "Bandit gave ${out.size} values, not ${STEMS * bins * frames * 2}" }
        return StemSeparation.Masks { stem, _, frame, re, im ->
            for (k in 0 until bins) {
                val at = ((stem * bins + k) * frames + frame) * 2
                re[k] = out[at]
                im[k] = out[at + 1]
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val N = 2048
        const val HOP = 512

        /** Frames seen at once: about 10.9 s, of which half a second at each end only heard around. */
        const val FRAMES = 1024
        const val CONTEXT = 48

        const val STEMS = 3

        /** torchaudio's "normalized": one over the square root of the window's energy (3/8 of N). */
        val SCALE: Float = (1.0 / sqrt(3.0 * N / 8.0)).toFloat()

        val LAYOUT = StemSeparation.Layout(
            sampleRate = SAMPLE_RATE, n = N, hop = HOP, frames = FRAMES, context = CONTEXT,
            stems = STEMS, residualTo = StemKind.SCENE.indexOf(StemKind.EFFECTS),
        )
    }
}

/**
 * Spleeter's five-stem model (Deezer, MIT) as a [StemSeparation.Model]: music
 * split into singing, piano, drums, bass and the other instruments at
 * 44.1 kHz. It reads the magnitudes of the first 1024 bins (up to about
 * 11 kHz) of both channels, 512 frames at a time (4096 points, hop 1024),
 * laid out [channel][1][frame][bin], and estimates each instrument's
 * magnitudes, laid out [instrument][channel][1][frame][bin]; as Spleeter
 * does, each instrument keeps the share of every bin
 *
 *     e^2 / sum(e^2)    (with a tiny epsilon, as Spleeter)
 *
 * of its power. Above the 1024 bins it sees, nothing is told apart: that top
 * end goes with "other" (Spleeter drops it from every stem), which is the
 * sound less the four others (see StemSeparation), so the five add up.
 */
class SpleeterMasks(private val network: Network) : StemSeparation.Model {

    /** The ONNX file: from the magnitudes, the estimates. */
    fun interface Network {
        fun run(magnitudes: FloatArray): FloatArray
    }

    private val input = FloatArray(2 * FRAMES * BINS)

    override fun masks(window: StemSeparation.Window): StemSeparation.Masks {
        require(window.frames == FRAMES) { "Spleeter reads $FRAMES frames at a time" }
        val bins = window.bins
        for (c in 0..1) {
            val re = window.re[c]
            val im = window.im[c]
            for (f in 0 until FRAMES) {
                for (k in 0 until BINS) {
                    val a = re[f * bins + k]
                    val b = im[f * bins + k]
                    input[(c * FRAMES + f) * BINS + k] = sqrt(a * a + b * b)
                }
            }
        }
        val out = network.run(input)
        require(out.size == STEMS * 2 * FRAMES * BINS) { "Spleeter gave ${out.size} values, not ${STEMS * 2 * FRAMES * BINS}" }
        return StemSeparation.Masks { stem, channel, frame, re, im ->
            java.util.Arrays.fill(re, 0f)
            java.util.Arrays.fill(im, 0f)
            for (k in 0 until BINS) {
                var sum = 0f
                for (s in 0 until STEMS) {
                    val e = out[((s * 2 + channel) * FRAMES + frame) * BINS + k]
                    sum += e * e
                }
                val e = out[((stem * 2 + channel) * FRAMES + frame) * BINS + k]
                re[k] = mask(e * e, sum)
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val N = 4096
        const val HOP = 1024

        /** Bins it sees (of N / 2 + 1) and frames at once. */
        const val BINS = 1024
        const val FRAMES = 512

        /** Frames seen past each end of what is kept of a window: kept, about 10.4 s at a time. */
        const val CONTEXT = 32

        const val STEMS = 5

        private const val EPSILON = 1e-10f

        /** An instrument's share of a bin: its power [power] of the instruments' [sum]. */
        fun mask(power: Float, sum: Float): Float = (power + EPSILON / STEMS) / (sum + EPSILON)

        val LAYOUT = StemSeparation.Layout(
            sampleRate = SAMPLE_RATE, n = N, hop = HOP, frames = FRAMES, context = CONTEXT,
            stems = STEMS, residualTo = StemKind.INSTRUMENTS.indexOf(StemKind.OTHER),
        )
    }
}
