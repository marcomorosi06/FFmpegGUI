package com.cuscus.ffmpeggui.core.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Splits a stereo sound into the voice and everything else with Spleeter's
 * two-stem model (Deezer, MIT), as sherpa-onnx runs it: a short-time Fourier
 * transform at 44.1 kHz (4096-point periodic Hann window, hop 1024, no
 * centring), the magnitudes of the first 1024 bins (up to about 11 kHz) of
 * both channels shown to the voice network and to the accompaniment network
 * 512 frames at a time, and from what they estimate a soft mask:
 *
 *     voice = v^2 / (v^2 + a^2)    (both plus a tiny epsilon, as sherpa-onnx)
 *
 * which keeps that share of every bin of the voice. Above the networks' 1024
 * bins nothing is voice: the cymbals' and the air's top end stays with the
 * rest (sherpa-onnx drops it from both, which takes it out of the rest too).
 * The rest is then exactly the sound minus the voice, so that the two always
 * add up to the original.
 *
 * However long the sound, it is read [CORE_FRAMES] frames at a time and the
 * two are written as they are found: memory stays the same. Each window the
 * networks see reaches [CONTEXT_FRAMES] frames past both ends of what is kept
 * of it, so that nothing is cut at its edges, and the masked frames are added
 * back over each other (overlap-add) across windows without a seam.
 *
 * The front of the sound is padded with N - hop zeros so that every sample is
 * covered by four frames, whose squared windows always add up to 1.5.
 */
class VoiceSeparation(private val model: StemModel) {

    /**
     * The two networks: given the magnitudes of [FRAMES] frames of both
     * channels, laid out [channel][frame][bin] (2 x 512 x 1024), what they
     * estimate of the voice and of the accompaniment, laid out the same way.
     */
    fun interface StemModel {
        fun estimate(magnitudes: FloatArray): Pair<FloatArray, FloatArray>
    }

    /** The sound: [length] stereo frames, read interleaved (left, right); zeros outside. */
    interface Source {
        val length: Long

        /** Fills [into] with [count] stereo frames from [start] (interleaved); zeros before 0 and past the end. */
        fun read(start: Long, into: FloatArray, count: Int)
    }

    /** Where the two go, in order, [count] interleaved stereo frames at a time. */
    fun interface Sink {
        fun write(voice: FloatArray, rest: FloatArray, count: Int)
    }

    private val fft = Fft(N)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / N)).toFloat() }

    /**
     * Separates [source] into [sink]. [onProgress] gets the share done (0..1);
     * [checkCancelled] is called between windows and may throw to stop.
     */
    fun separate(source: Source, sink: Sink, onProgress: (Float) -> Unit = {}, checkCancelled: () -> Unit = {}) {
        val length = source.length
        if (length <= 0L) return
        // Frames over the padded sound: the last one starts at or before its last sample.
        val frames = (PAD + length - 1) / HOP + 1
        val windows = (frames + CORE_FRAMES - 1) / CORE_FRAMES

        val samplesPerWindow = (FRAMES - 1) * HOP + N
        val input = FloatArray(samplesPerWindow * 2)
        val magnitudes = FloatArray(2 * FRAMES * BINS)
        // The spectra of the frames kept, both channels: bins 0..N/2.
        val spectra = Array(4) { Array(CORE_FRAMES) { FloatArray(N / 2 + 1) } }
        val (leftRe, leftIm, rightRe, rightIm) = spectra
        val re = FloatArray(N)
        val im = FloatArray(N)
        // The voice, overlap-added: the kept frames and the tail they leave to the next window.
        val accLength = CORE_FRAMES * HOP + N
        val acc = FloatArray(accLength * 2)
        val emitVoice = FloatArray(CORE_FRAMES * HOP * 2)
        val emitRest = FloatArray(CORE_FRAMES * HOP * 2)
        val original = FloatArray(CORE_FRAMES * HOP * 2)

        for (w in 0 until windows) {
            checkCancelled()
            val firstKept = w * CORE_FRAMES
            val keptCount = minOf(CORE_FRAMES.toLong(), frames - firstKept).toInt()
            val firstSeen = firstKept - CONTEXT_FRAMES
            // The padded sound from the first frame seen: sample m of it is the sound's m - PAD.
            source.read(firstSeen * HOP - PAD, input, samplesPerWindow)

            for (f in 0 until FRAMES) {
                val offset = f * HOP
                // Both channels at once: left as the real part, right as the imaginary part.
                for (n in 0 until N) {
                    val at = (offset + n) * 2
                    re[n] = input[at] * window[n]
                    im[n] = input[at + 1] * window[n]
                }
                fft.forward(re, im)
                val kept = f - CONTEXT_FRAMES
                val keep = kept in 0 until keptCount
                for (k in 0..N / 2) {
                    val j = if (k == 0) 0 else N - k
                    // L[k] = (Z[k] + conj Z[N-k]) / 2, R[k] = (Z[k] - conj Z[N-k]) / 2i.
                    val lr = (re[k] + re[j]) * 0.5f
                    val li = (im[k] - im[j]) * 0.5f
                    val rr = (im[k] + im[j]) * 0.5f
                    val ri = (re[j] - re[k]) * 0.5f
                    if (k < BINS) {
                        magnitudes[f * BINS + k] = sqrt(lr * lr + li * li)
                        magnitudes[(FRAMES + f) * BINS + k] = sqrt(rr * rr + ri * ri)
                    }
                    if (keep) {
                        leftRe[kept][k] = lr; leftIm[kept][k] = li
                        rightRe[kept][k] = rr; rightIm[kept][k] = ri
                    }
                }
            }

            val (vocals, accompaniment) = model.estimate(magnitudes)
            checkCancelled()

            for (kept in 0 until keptCount) {
                val f = kept + CONTEXT_FRAMES
                java.util.Arrays.fill(re, 0f)
                java.util.Arrays.fill(im, 0f)
                // The voice of both channels, as one spectrum: Y = VL + i VR (VL, VR Hermitian).
                for (k in 0 until BINS) {
                    val ml = mask(vocals[f * BINS + k], accompaniment[f * BINS + k])
                    val mr = mask(vocals[(FRAMES + f) * BINS + k], accompaniment[(FRAMES + f) * BINS + k])
                    val lr = leftRe[kept][k] * ml
                    val li = leftIm[kept][k] * ml
                    val rr = rightRe[kept][k] * mr
                    val ri = rightIm[kept][k] * mr
                    re[k] = lr - ri
                    im[k] = li + rr
                    if (k > 0) {
                        // conj(VL) + i conj(VR) at N - k.
                        re[N - k] = lr + ri
                        im[N - k] = rr - li
                    }
                }
                fft.inverse(re, im)
                val base = kept * HOP
                for (n in 0 until N) {
                    val at = (base + n) * 2
                    acc[at] += re[n] * window[n]
                    acc[at + 1] += im[n] * window[n]
                }
            }

            // Final now: everything before the first frame of the next window.
            val done = keptCount * HOP
            val firstSample = firstKept * HOP - PAD
            // Of those, the samples of the sound itself (the padding in front is left out).
            val from = maxOf(0L, -firstSample).toInt().coerceAtMost(done)
            val to = minOf(done.toLong(), length - firstSample).toInt()
            if (to > from) {
                val count = to - from
                source.read(firstSample + from, original, count)
                for (i in 0 until count * 2) {
                    val voice = acc[from * 2 + i] / OVERLAP_GAIN
                    emitVoice[i] = voice
                    emitRest[i] = original[i] - voice
                }
                sink.write(emitVoice, emitRest, count)
            }
            // The tail moves to the front; the rest starts again from silence.
            System.arraycopy(acc, done * 2, acc, 0, (accLength - done) * 2)
            java.util.Arrays.fill(acc, (accLength - done) * 2, acc.size, 0f)
            onProgress((w + 1).toFloat() / windows)
        }
    }

    companion object {
        /** The rate the networks were trained at. */
        const val SAMPLE_RATE = 44_100

        /** Window and hop of the transform. */
        const val N = 4096
        const val HOP = 1024

        /** Bins the networks see (of N / 2 + 1). */
        const val BINS = 1024

        /** Frames the networks see at once. */
        const val FRAMES = 512

        /** Frames seen past each end of what is kept of a window. */
        const val CONTEXT_FRAMES = 32

        /** Frames kept of each window: about 10.4 s. */
        const val CORE_FRAMES = FRAMES - 2 * CONTEXT_FRAMES

        /** Zeros in front of the sound: every sample then lies under four frames. */
        const val PAD = (N - HOP).toLong()

        /** Sum of the squared windows over every sample (periodic Hann at a quarter hop). */
        const val OVERLAP_GAIN = 1.5f

        private const val EPSILON = 1e-10f

        /** The voice's share of a bin, from the two estimates. */
        fun mask(vocals: Float, accompaniment: Float): Float {
            val v = vocals * vocals
            return (v + EPSILON / 2) / (v + accompaniment * accompaniment + EPSILON)
        }
    }
}
