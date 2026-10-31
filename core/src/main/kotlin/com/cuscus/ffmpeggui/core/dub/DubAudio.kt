package com.cuscus.ffmpeggui.core.dub

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * What is done to each line the voice says before it is laid on the dub: its
 * silence before and after cut off (it would make the line start late and
 * last longer than it does), and its two ends faded over a few
 * milliseconds, so that no line starts or stops with a click.
 */
object DubAudio {

    /** Below this a sample counts as silence. */
    const val SILENCE = 0.004f

    /** Kept of the silence on each side, so that a soft start or end is not cut. */
    const val MARGIN_MS = 20

    const val FADE_MS = 8

    /** [samples] at [rate] without their silence at both ends (but [MARGIN_MS]); empty when all silent. */
    fun trimmed(samples: FloatArray, rate: Int): FloatArray {
        var first = 0
        while (first < samples.size && abs(samples[first]) < SILENCE) first++
        if (first == samples.size) return FloatArray(0)
        var last = samples.size - 1
        while (last > first && abs(samples[last]) < SILENCE) last--
        val margin = MARGIN_MS * rate / 1000
        val from = (first - margin).coerceAtLeast(0)
        val to = (last + 1 + margin).coerceAtMost(samples.size)
        return samples.copyOfRange(from, to)
    }

    /** Fades both ends of [samples] in place over [FADE_MS] (less for a very short line). */
    fun fade(samples: FloatArray, rate: Int) {
        val n = minOf(FADE_MS * rate / 1000, samples.size / 2)
        for (i in 0 until n) {
            val g = i.toFloat() / n
            samples[i] *= g
            samples[samples.size - 1 - i] *= g
        }
    }
}

/**
 * How fast the voice speaks, learnt as it goes: characters a second at speed
 * 1, line after line, so that a line too long for its slot is said fast
 * enough the first time (each line said twice would take twice as long).
 */
class DubPace(private var charsPerSecond: Float = START_CPS) {

    /** What [text] should take at [speed], in ms. */
    fun estimateMs(text: String, speed: Float): Long =
        Math.round(text.length / (charsPerSecond * speed.coerceAtLeast(0.1f)) * 1000.0)

    /** The speed for [text] to fit in [slotMs]: [baseSpeed] when it does, faster when not (to the limit). */
    fun speedFor(text: String, slotMs: Long, baseSpeed: Float): Float =
        DubTiming.speedFor(estimateMs(text, baseSpeed), slotMs, baseSpeed)

    /** [text] took [ms] at [speed]: the pace follows (lines of a few letters say little). */
    fun learn(text: String, ms: Long, speed: Float) {
        if (text.length < MIN_CHARS || ms <= 0L) return
        val seen = text.length / (ms / 1000f) / speed.coerceAtLeast(0.1f)
        charsPerSecond = charsPerSecond * (1 - WEIGHT) + seen * WEIGHT
    }

    val pace: Float get() = charsPerSecond

    private companion object {
        const val START_CPS = 15f
        const val MIN_CHARS = 12
        const val WEIGHT = 0.3f
    }
}

/**
 * Faster speech without a higher voice: WSOLA (waveform similarity overlap
 * add). Frames of [FRAME_MS] are taken from the input further apart than they
 * are laid in the output, each one moved a little (up to [SEEK_MS]) to where
 * its waveform best continues the one before, and overlapped with a Hann
 * window: the pitch stays, only the time shrinks.
 */
object TimeStretch {

    const val FRAME_MS = 30
    const val SEEK_MS = 10

    /** [input] at [rate] made [factor] times faster (1.5: two thirds as long); as it is for 1 or less. */
    fun faster(input: FloatArray, rate: Int, factor: Float): FloatArray {
        val n = (FRAME_MS * rate / 1000).let { it + (it and 1) }
        if (factor <= 1.001f || input.size < 2 * n) return input
        val hop = n / 2
        val seek = SEEK_MS * rate / 1000
        val window = FloatArray(n) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / n)).toFloat() }
        val outLength = (input.size / factor).toInt()
        val out = FloatArray(outLength + n)
        val weight = FloatArray(outLength + n)
        val lastStart = input.size - n
        var previous = 0
        var k = 0
        while (true) {
            val at = k * hop
            if (at >= outLength) break
            val nominal = Math.round(at * factor.toDouble()).toInt().coerceIn(0, lastStart)
            val pos = if (k == 0) 0 else bestMatch(input, previous + hop, nominal, seek, hop, lastStart)
            for (i in 0 until n) {
                val w = window[i]
                out[at + i] += input[pos + i] * w
                weight[at + i] += w
            }
            previous = pos
            k++
        }
        return FloatArray(outLength) { i -> if (weight[i] > 0.05f) out[i] / weight[i] else out[i] }
    }

    /**
     * Where near [nominal] (within [seek]) the input best continues the
     * stretch [natural] (where the frame before would have gone on): the
     * start whose first [length] samples look most like it.
     */
    private fun bestMatch(input: FloatArray, natural: Int, nominal: Int, seek: Int, length: Int, lastStart: Int): Int {
        if (natural + length > input.size) return nominal
        val from = (nominal - seek).coerceAtLeast(0)
        val to = (nominal + seek).coerceAtMost(lastStart)
        var best = nominal
        var bestScore = Double.NEGATIVE_INFINITY
        var p = from
        while (p <= to) {
            var dot = 0.0
            var energy = 1e-9
            var i = 0
            while (i < length) {
                val x = input[p + i]
                dot += x * input[natural + i]
                energy += x * x
                i += 2
            }
            val score = dot / sqrt(energy)
            if (score > bestScore) {
                bestScore = score
                best = p
            }
            p++
        }
        return best
    }
}

/**
 * A line made to fit its slot, so that every line starts with its subtitle
 * and none runs into the next: its long pauses shortened first, then the
 * whole of it said faster (TimeStretch, up to [MAX_STRETCH]), and only past
 * that its end let go, faded.
 */
object DubFit {

    /** Past this the stretch is heard. */
    const val MAX_STRETCH = 1.6f

    /** A pause inside a line that does not fit is shortened to this. */
    const val PAUSE_MS = 140

    /** How long the end of a line cut short fades. */
    const val CUT_FADE_MS = 60

    /** Silence is judged block by block, this long. */
    private const val BLOCK_MS = 5

    /** [samples] at [rate] in at most [slotMs]: as they are when they fit. */
    fun fit(samples: FloatArray, rate: Int, slotMs: Long): FloatArray {
        val slot = (slotMs * rate / 1000).toInt().coerceAtLeast(1)
        if (samples.size <= slot) return samples
        var s = squeezePauses(samples, rate, PAUSE_MS)
        if (s.size > slot) {
            s = TimeStretch.faster(s, rate, (s.size.toFloat() / slot).coerceAtMost(MAX_STRETCH))
            DubAudio.fade(s, rate)
        }
        if (s.size > slot) {
            s = s.copyOf(slot)
            val n = minOf(CUT_FADE_MS * rate / 1000, slot)
            for (i in 0 until n) s[slot - 1 - i] *= i.toFloat() / n
        }
        return s
    }

    /** [samples] with every pause (a run of quiet blocks) longer than [keepMs] made [keepMs] long. */
    fun squeezePauses(samples: FloatArray, rate: Int, keepMs: Int): FloatArray {
        val block = (BLOCK_MS * rate / 1000).coerceAtLeast(1)
        val keep = keepMs * rate / 1000
        val blocks = (samples.size + block - 1) / block
        val quiet = BooleanArray(blocks) { b ->
            var peak = 0f
            for (i in b * block until minOf((b + 1) * block, samples.size)) peak = maxOf(peak, abs(samples[i]))
            peak < DubAudio.SILENCE * 2
        }
        val out = FloatArray(samples.size)
        var written = 0
        var b = 0
        while (b < blocks) {
            if (!quiet[b]) {
                val from = b * block
                val to = minOf(from + block, samples.size)
                samples.copyInto(out, written, from, to)
                written += to - from
                b++
                continue
            }
            var e = b
            while (e < blocks && quiet[e]) e++
            val from = b * block
            val to = minOf(e * block, samples.size)
            // Its middle goes: what is left of it is its two ends, joined in the quiet.
            if (to - from > keep) {
                val half = keep / 2
                samples.copyInto(out, written, from, from + half)
                written += half
                samples.copyInto(out, written, to - (keep - half), to)
                written += keep - half
            } else {
                samples.copyInto(out, written, from, to)
                written += to - from
            }
            b = e
        }
        return out.copyOf(written)
    }
}
