package com.cuscus.ffmpeggui.core.marking

import kotlin.math.sqrt

/**
 * How a voice a model said gets its invisible mark (AudioSeal), and how the
 * mark is found again. AudioSeal's ONNX files take a fixed [WINDOW] of
 * samples at 16 kHz (see scripts/export-ai-markers.py, which checks this very
 * scheme against PyTorch and against compression):
 *
 *  - marking: the speech at 16 kHz, cut in windows [OVERLAP] samples apart
 *    from one another's ends ([markWindows], the last one padded with
 *    silence); each gets its watermark, the windows are joined with linear
 *    crossfades ([join]); the watermark goes back to the speech's own rate
 *    and is added to it (the speech itself is never resampled);
 *  - checking: the speech at 16 kHz in whole windows ([checkWindows]); the
 *    detector tells, sample by sample, how likely the mark is there; the
 *    share of the speech's samples (not its silences: [voiced]) where it is
 *    ([share]) says whether the mark is.
 *
 * Pure, tested.
 */
object SpeechMarking {
    const val RATE = 16_000

    /** Samples AudioSeal's ONNX files take at a time: 5 s (a multiple of its hop, 320). */
    const val WINDOW = 80_000

    /** Samples two marking windows share, crossfaded: 0.5 s. */
    const val OVERLAP = 8_000

    /** Above this, a sample's detector output says "marked" (as AudioSeal's own detect_watermark). */
    const val DETECTED = 0.5f

    /** At least this share of the speech marked: the mark is there. */
    const val MARKED_SHARE = 0.5f

    /** Right after marking, at least this much: a margin for what the file goes through later. */
    const val FRESH_SHARE = 0.8f

    /** A 20 ms frame whose RMS is below this (about -50 dBFS) is silence, not speech. */
    const val VOICED_RMS = 0.003f

    private const val FRAME = 320

    /** Less speech than this (0.25 s) cannot be told marked or not. */
    const val LEAST_VOICED = RATE / 4

    /** A part of the speech: [start] and the [length] of real samples in it (the rest is padding). */
    data class Window(val start: Int, val length: Int)

    /** The marking windows of [samples] samples (at 16 kHz). */
    fun markWindows(samples: Int): List<Window> = windows(samples, WINDOW - OVERLAP)

    /** The checking windows of [samples] samples (at 16 kHz). */
    fun checkWindows(samples: Int): List<Window> = windows(samples, WINDOW)

    private fun windows(samples: Int, hop: Int): List<Window> {
        if (samples <= 0) return emptyList()
        val out = ArrayList<Window>()
        var start = 0
        while (true) {
            out += Window(start, minOf(WINDOW, samples - start))
            if (start + WINDOW >= samples) break
            start += hop
        }
        return out
    }

    /** The [WINDOW] samples [window] of [audio] feeds the model, silence past its end. */
    fun padded(audio: FloatArray, window: Window): FloatArray {
        val out = FloatArray(WINDOW)
        System.arraycopy(audio, window.start, out, 0, window.length)
        return out
    }

    /**
     * The watermark of [total] samples from each window's own ([marks], in the
     * order of [windows], each at least as long as its window): in the samples
     * two windows share, the first fades out as the second fades in.
     */
    fun join(total: Int, windows: List<Window>, marks: List<FloatArray>): FloatArray {
        require(windows.size == marks.size)
        val out = FloatArray(total)
        for ((i, w) in windows.withIndex()) {
            val mark = marks[i]
            val fadesIn = i > 0
            val fadesOut = i < windows.lastIndex
            for (j in 0 until w.length) {
                var weight = 1f
                if (fadesIn && j < OVERLAP) weight = (j + 0.5f) / OVERLAP
                if (fadesOut && j >= w.length - OVERLAP) weight = 1f - (j - (w.length - OVERLAP) + 0.5f) / OVERLAP
                out[w.start + j] += mark[j] * weight
            }
        }
        return out
    }

    /** Which samples of [audio] (16 kHz) are speech: those of the 20 ms frames above [VOICED_RMS]. */
    fun voiced(audio: FloatArray): BooleanArray {
        val out = BooleanArray(audio.size)
        var start = 0
        while (start < audio.size) {
            val end = minOf(audio.size, start + FRAME)
            var sum = 0.0
            for (i in start until end) sum += audio[i] * audio[i]
            val loud = sqrt(sum / (end - start)) > VOICED_RMS
            if (loud) for (i in start until end) out[i] = true
            start = end
        }
        return out
    }

    /**
     * The share of [voiced] samples whose detector output ([probabilities],
     * one per sample, the windows one after the other) says marked; null when
     * there is too little speech to tell.
     */
    fun share(probabilities: FloatArray, voiced: BooleanArray): Float? {
        var speech = 0
        var marked = 0
        for (i in 0 until minOf(probabilities.size, voiced.size)) {
            if (!voiced[i]) continue
            speech++
            if (probabilities[i] > DETECTED) marked++
        }
        return if (speech < LEAST_VOICED) null else marked.toFloat() / speech
    }

    /**
     * The parts of [samples] (at [rate]) that are not digital silence: a dub's
     * lines, between the silence written around them (a gap shorter than a
     * quarter of a second does not split a line), each with 10 ms around it.
     * A dub made before the marks gets them line by line, its silences left
     * silent.
     */
    fun runs(samples: FloatArray, rate: Int): List<IntRange> {
        val quiet = 2f / 32768f
        val gap = rate / 4
        val margin = rate / 100
        val out = ArrayList<IntRange>()
        var start = -1
        var lastLoud = -1
        for (i in samples.indices) {
            if (kotlin.math.abs(samples[i]) <= quiet) continue
            if (start < 0) start = i
            else if (i - lastLoud > gap) {
                out += maxOf(0, start - margin)..minOf(samples.size - 1, lastLoud + margin)
                start = i
            }
            lastLoud = i
        }
        if (start >= 0) out += maxOf(0, start - margin)..minOf(samples.size - 1, lastLoud + margin)
        return out
    }

    /** The message read over every window, each weighed by its speech ([voicedPerWindow]). */
    fun message(perWindow: List<FloatArray>, voicedPerWindow: List<Int>): FloatArray {
        val out = FloatArray(Marks.SPEECH_BITS)
        val total = voicedPerWindow.sum().coerceAtLeast(1)
        for ((i, bits) in perWindow.withIndex()) {
            val weight = voicedPerWindow[i].toFloat() / total
            for (b in 0 until Marks.SPEECH_BITS) out[b] += bits[b] * weight
        }
        return out
    }
}
