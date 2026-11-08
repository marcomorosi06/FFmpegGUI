package com.cuscus.ffmpeggui.core.stems

import kotlin.math.PI
import kotlin.math.cos

/**
 * Splits a stereo sound into stems with a model that masks its spectrogram:
 * the short-time Fourier transform of both channels (a periodic Hann window
 * of [Layout.n] points every [Layout.hop], a quarter of it), shown to the
 * model [Layout.frames] frames at a time; for each stem the model gives a
 * complex mask per channel, frame and bin, and the stem is the sound's
 * spectrum times that mask, back to sound by the inverse transform
 * (overlap-add of the windowed frames, divided by the squared windows' sum).
 * [BanditMasks] and [SpleeterMasks] are the two models the app runs.
 *
 * However long the sound, it is read [Layout.core] frames at a time and the
 * stems are written as they are found: memory stays the same. Each window
 * the model sees reaches [Layout.context] frames past both ends of what is
 * kept of it, so that nothing is cut at its edges (a network that reads the
 * whole window, as Bandit's recurrent layers do, hears what comes before and
 * after), and the masked frames are added back over each other across
 * windows without a seam.
 *
 * The stems always add up to the sound: the stem [Layout.residualTo], the one
 * of "everything else", is the sound less every other stem, so it takes
 * whatever the other masks leave out (a model's masks need not add up to
 * one; Spleeter's top band, above what it sees, is there too). Its own mask
 * is never asked for. With masks of zeros, it gives the sound back whole.
 *
 * The front of the sound is padded with n - hop zeros so that every sample is
 * covered by four frames, whose squared windows always add up to 1.5.
 */
class StemSeparation(private val layout: Layout, private val model: Model) {

    /**
     * How the model reads the sound: [sampleRate] it works at, transform
     * size [n] and [hop] (a quarter of n), [frames] it sees at once, of which
     * [context] at each end are only for hearing around, and [stems] it gives,
     * of which [residualTo] takes what the masks leave out.
     */
    data class Layout(
        val sampleRate: Int,
        val n: Int,
        val hop: Int,
        val frames: Int,
        val context: Int,
        val stems: Int,
        val residualTo: Int,
    ) {
        init {
            require(hop * 4 == n) { "The hop must be a quarter of the window" }
            require(context >= 0 && frames > 2 * context) { "No frame would be kept" }
            require(residualTo in 0 until stems) { "No such stem" }
        }

        /** Bins of a one-sided spectrum. */
        val bins: Int get() = n / 2 + 1

        /** Frames kept of each window. */
        val core: Int get() = frames - 2 * context

        /** Zeros in front of the sound: every sample then lies under four frames. */
        val pad: Long get() = (n - hop).toLong()

        /** Samples of the sound a window reads. */
        val samplesPerWindow: Int get() = (frames - 1) * hop + n
    }

    /**
     * The spectra of one window, both channels: frame f, bin k of channel c
     * at [re] and [im] of c, index f * bins + k. Bins 0..n/2 of the window's
     * frames, not scaled (the sum of the windowed samples' e^(-2 pi i k t / n)).
     */
    class Window(val frames: Int, val bins: Int) {
        val re: Array<FloatArray> = Array(2) { FloatArray(frames * bins) }
        val im: Array<FloatArray> = Array(2) { FloatArray(frames * bins) }
    }

    /** The masks the model found for a window. */
    fun interface Masks {
        /** Fills [re] and [im] (bins 0..n/2) with the mask of [stem] for [channel] at [frame] of the window. */
        fun fill(stem: Int, channel: Int, frame: Int, re: FloatArray, im: FloatArray)
    }

    /** The model: from a window's spectra, the masks of every stem. */
    fun interface Model {
        fun masks(window: Window): Masks
    }

    /** The sound: [length] stereo frames, read interleaved (left, right); zeros outside. */
    interface Source {
        val length: Long

        /** Fills [into] with [count] stereo frames from [start] (interleaved); zeros before 0 and past the end. */
        fun read(start: Long, into: FloatArray, count: Int)
    }

    /** Where the stems go, in order: [count] interleaved stereo frames of each, at a time. */
    fun interface Sink {
        fun write(stems: Array<FloatArray>, count: Int)
    }

    private val n = layout.n
    private val hop = layout.hop
    private val fft = Fft(n)
    private val hann = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / n)).toFloat() }

    /**
     * Separates [source] into [sink]. [onProgress] gets the share done (0..1);
     * [checkCancelled] is called between windows and may throw to stop.
     */
    fun separate(source: Source, sink: Sink, onProgress: (Float) -> Unit = {}, checkCancelled: () -> Unit = {}) {
        val length = source.length
        if (length <= 0L) return
        val bins = layout.bins
        val stems = layout.stems
        val core = layout.core
        val context = layout.context
        val pad = layout.pad
        // Frames over the padded sound: the last one starts at or before its last sample.
        val frames = (pad + length - 1) / hop + 1
        val windows = (frames + core - 1) / core

        val samples = layout.samplesPerWindow
        val input = FloatArray(samples * 2)
        val window = Window(layout.frames, bins)
        val re = FloatArray(n)
        val im = FloatArray(n)
        val maskLeftRe = FloatArray(bins)
        val maskLeftIm = FloatArray(bins)
        val maskRightRe = FloatArray(bins)
        val maskRightIm = FloatArray(bins)
        // Each stem, overlap-added: the kept frames and the tail they leave to the next window.
        val accLength = core * hop + n
        val acc = Array(stems) { FloatArray(accLength * 2) }
        val emit = Array(stems) { FloatArray(core * hop * 2) }
        val original = FloatArray(core * hop * 2)

        for (w in 0 until windows) {
            checkCancelled()
            val firstKept = w * core
            val keptCount = minOf(core.toLong(), frames - firstKept).toInt()
            val firstSeen = firstKept - context
            // The padded sound from the first frame seen: sample m of it is the sound's m - pad.
            source.read(firstSeen * hop - pad, input, samples)

            for (f in 0 until layout.frames) {
                val offset = f * hop
                // Both channels at once: left as the real part, right as the imaginary part.
                for (t in 0 until n) {
                    val at = (offset + t) * 2
                    re[t] = input[at] * hann[t]
                    im[t] = input[at + 1] * hann[t]
                }
                fft.forward(re, im)
                val row = f * bins
                for (k in 0 until bins) {
                    val j = if (k == 0) 0 else n - k
                    // L[k] = (Z[k] + conj Z[n-k]) / 2, R[k] = (Z[k] - conj Z[n-k]) / 2i.
                    window.re[0][row + k] = (re[k] + re[j]) * 0.5f
                    window.im[0][row + k] = (im[k] - im[j]) * 0.5f
                    window.re[1][row + k] = (im[k] + im[j]) * 0.5f
                    window.im[1][row + k] = (re[j] - re[k]) * 0.5f
                }
            }

            val masks = model.masks(window)
            checkCancelled()

            for (kept in 0 until keptCount) {
                val f = kept + context
                val row = f * bins
                val base = kept * hop
                for (s in 0 until stems) {
                    // The stem of what is left is found by taking the others away (below).
                    if (s == layout.residualTo) continue
                    masks.fill(s, 0, f, maskLeftRe, maskLeftIm)
                    masks.fill(s, 1, f, maskRightRe, maskRightIm)
                    java.util.Arrays.fill(re, 0f)
                    java.util.Arrays.fill(im, 0f)
                    // The stem of both channels, as one spectrum: Y = SL + i SR (SL, SR Hermitian).
                    for (k in 0 until bins) {
                        val xr = window.re[0][row + k]
                        val xi = window.im[0][row + k]
                        val yr = window.re[1][row + k]
                        val yi = window.im[1][row + k]
                        val lr = xr * maskLeftRe[k] - xi * maskLeftIm[k]
                        val li = xr * maskLeftIm[k] + xi * maskLeftRe[k]
                        val rr = yr * maskRightRe[k] - yi * maskRightIm[k]
                        val ri = yr * maskRightIm[k] + yi * maskRightRe[k]
                        if (k == 0 || k == n / 2) {
                            // The two real bins of a real signal: their imaginary parts are none.
                            re[k] = lr
                            im[k] = rr
                        } else {
                            re[k] = lr - ri
                            im[k] = li + rr
                            // conj(SL) + i conj(SR) at n - k.
                            re[n - k] = lr + ri
                            im[n - k] = rr - li
                        }
                    }
                    fft.inverse(re, im)
                    val out = acc[s]
                    for (t in 0 until n) {
                        val at = (base + t) * 2
                        out[at] += re[t] * hann[t]
                        out[at + 1] += im[t] * hann[t]
                    }
                }
            }

            // Final now: everything before the first frame of the next window.
            val done = keptCount * hop
            val firstSample = firstKept * hop - pad
            // Of those, the samples of the sound itself (the padding in front is left out).
            val from = maxOf(0L, -firstSample).toInt().coerceAtMost(done)
            val to = minOf(done.toLong(), length - firstSample).toInt()
            if (to > from) {
                val count = to - from
                source.read(firstSample + from, original, count)
                val residual = emit[layout.residualTo]
                for (i in 0 until count * 2) {
                    var left = original[i]
                    for (s in 0 until stems) {
                        if (s == layout.residualTo) continue
                        val v = acc[s][from * 2 + i] / OVERLAP_GAIN
                        emit[s][i] = v
                        left -= v
                    }
                    residual[i] = left
                }
                sink.write(emit, count)
            }
            // The tail moves to the front; the rest starts again from silence.
            for (s in 0 until stems) {
                System.arraycopy(acc[s], done * 2, acc[s], 0, (accLength - done) * 2)
                java.util.Arrays.fill(acc[s], (accLength - done) * 2, acc[s].size, 0f)
            }
            onProgress((w + 1).toFloat() / windows)
        }
    }

    companion object {
        /** Sum of the squared windows over every sample (periodic Hann at a quarter hop). */
        const val OVERLAP_GAIN = 1.5f
    }
}
