package com.cuscus.ffmpeggui.core.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class StemSeparationTest {

    /**
     * A sound at [rate] made of [tones] (Hz to amplitude), the right side a
     * quarter turn on. It fades in and out, so that its edges add no top end.
     */
    private class Tones(override val length: Long, val rate: Double, val tones: List<Pair<Double, Double>>) : StemSeparation.Source {
        private fun envelope(n: Long): Double {
            val edge = minOf(n, length - 1 - n).coerceAtLeast(0L)
            return if (edge >= RAMP) 1.0 else 0.5 - 0.5 * cos(PI * edge / RAMP)
        }

        fun left(n: Long): Float = (envelope(n) * tones.sumOf { (f, a) -> a * sin(2 * PI * f * n / rate) }).toFloat()
        fun right(n: Long): Float = (envelope(n) * tones.sumOf { (f, a) -> a * cos(2 * PI * f * n / rate) }).toFloat()

        override fun read(start: Long, into: FloatArray, count: Int) {
            for (i in 0 until count) {
                val n = start + i
                val inside = n in 0 until length
                into[i * 2] = if (inside) left(n) else 0f
                into[i * 2 + 1] = if (inside) right(n) else 0f
            }
        }

        private companion object {
            const val RAMP = 8192L
        }
    }

    private class Collected(count: Int) {
        val stems = Array(count) { ArrayList<Float>() }
        val sink = StemSeparation.Sink { out, frames ->
            for (s in stems.indices) for (i in 0 until frames * 2) stems[s] += out[s][i]
        }
    }

    /** Masks of ones for stem [s] where [band] says so, zeros elsewhere. */
    private fun bands(stems: Int, bins: Int, band: (stem: Int, bin: Int) -> Boolean) = StemSeparation.Model {
        StemSeparation.Masks { stem, _, _, re, im ->
            for (k in 0 until bins) {
                re[k] = if (band(stem, k)) 1f else 0f
                im[k] = 0f
            }
        }
    }

    private val small = StemSeparation.Layout(sampleRate = 48_000, n = 1024, hop = 256, frames = 64, context = 8, stems = 3, residualTo = 2)

    @Test
    fun `the transform undoes itself and finds a tone in its bin`() {
        val fft = Fft(64)
        val re = FloatArray(64) { cos(2 * PI * 5 * it / 64).toFloat() }
        val im = FloatArray(64)
        val copy = re.copyOf()
        fft.forward(re, im)
        assertEquals(32f, re[5], 1e-3f)
        assertEquals(32f, re[59], 1e-3f)
        assertEquals(0f, re[6], 1e-3f)
        fft.inverse(re, im)
        for (i in 0 until 64) {
            assertEquals(copy[i], re[i], 1e-5f)
            assertEquals(0f, im[i], 1e-5f)
        }
    }

    @Test
    fun `a stem of all ones gives the sound back whole, over several windows and without seams`() {
        // Two and a half windows, so that the joins and a short last window are crossed.
        val length = (small.core * small.hop * 2.5).toLong()
        val source = Tones(length, 48_000.0, listOf(220.0 to 0.4, 1_000.0 to 0.2))
        val out = Collected(3)
        var progress = 0f
        StemSeparation(small, bands(3, small.bins) { s, _ -> s == 1 }).separate(source, out.sink, onProgress = { progress = it })
        assertEquals(1f, progress, 0f)
        assertEquals(length * 2, out.stems[1].size.toLong())
        var worst = 0f
        for (n in 0 until length) {
            worst = maxOf(worst, abs(out.stems[1][(n * 2).toInt()] - source.left(n)), abs(out.stems[1][(n * 2 + 1).toInt()] - source.right(n)))
        }
        assertTrue("stem off by $worst", worst < 1e-4f)
        assertTrue(out.stems[0].all { abs(it) < 1e-6f })
        assertTrue(out.stems[2].all { abs(it) < 1e-4f })
    }

    @Test
    fun `masks of zeros leave the sound whole in the stem of what is left`() {
        val length = 20_000L
        val source = Tones(length, 48_000.0, listOf(440.0 to 0.5))
        val out = Collected(3)
        StemSeparation(small, bands(3, small.bins) { _, _ -> false }).separate(source, out.sink)
        val buffer = FloatArray(2)
        for (n in 0 until length) {
            source.read(n, buffer, 1)
            assertEquals(buffer[0], out.stems[2][(n * 2).toInt()], 1e-6f)
        }
    }

    @Test
    fun `each stem keeps its own tones, and the stems add up to the sound`() {
        val length = 60_000L
        // At 48 kHz and 1024 points a bin is 46.9 Hz: 300 Hz is in bin 6, 3 kHz in bin 64, 12 kHz in bin 256.
        val source = Tones(length, 48_000.0, listOf(300.0 to 0.3, 3_000.0 to 0.2, 12_000.0 to 0.1))
        val out = Collected(3)
        StemSeparation(small, bands(3, small.bins) { s, k -> (s == 0 && k < 30) || (s == 1 && k in 30 until 150) }).separate(source, out.sink)
        val low = Tones(length, 48_000.0, listOf(300.0 to 0.3))
        val middle = Tones(length, 48_000.0, listOf(3_000.0 to 0.2))
        val high = Tones(length, 48_000.0, listOf(12_000.0 to 0.1))
        val error = DoubleArray(3)
        var sumError = 0.0
        val buffer = FloatArray(2)
        for (n in 0 until length) {
            source.read(n, buffer, 1)
            val i = (n * 2).toInt()
            error[0] += (out.stems[0][i] - low.left(n)).let { it * it }
            error[1] += (out.stems[1][i] - middle.left(n)).let { it * it }
            error[2] += (out.stems[2][i] - high.left(n)).let { it * it }
            sumError = maxOf(sumError, abs(out.stems[0][i] + out.stems[1][i] + out.stems[2][i] - buffer[0]).toDouble())
        }
        for (s in 0..2) {
            val rms = sqrt(error[s] / length)
            assertTrue("stem $s error $rms", rms < 3e-3)
        }
        assertTrue("sum off by $sumError", sumError < 1e-5)
    }

    @Test
    fun `a complex mask turns the phase, channel by channel`() {
        // A mask of i on the left turns each of its tones a quarter turn: sin becomes cos, which is
        // what the right channel is. Nothing of the right comes through.
        val length = 30_000L
        val source = Tones(length, 48_000.0, listOf(750.0 to 0.4))
        val out = Collected(3)
        val model = StemSeparation.Model {
            StemSeparation.Masks { stem, channel, _, re, im ->
                java.util.Arrays.fill(re, 0f)
                java.util.Arrays.fill(im, if (stem == 0 && channel == 0) 1f else 0f)
            }
        }
        StemSeparation(small, model).separate(source, out.sink)
        var worst = 0f
        for (n in 10_000L until 20_000L) {
            val i = (n * 2).toInt()
            worst = maxOf(worst, abs(out.stems[0][i] - source.right(n)))
            assertEquals(0f, out.stems[0][i + 1], 1e-4f)
        }
        assertTrue("phase off by $worst", worst < 1e-3f)
    }

    @Test
    fun `it stops when asked`() {
        val source = Tones(200_000L, 48_000.0, listOf(440.0 to 0.5))
        var windows = 0
        try {
            StemSeparation(small, bands(3, small.bins) { _, _ -> false }).separate(
                source, Collected(3).sink,
                checkCancelled = { if (++windows > 3) throw IllegalStateException("stop") },
            )
            fail("not stopped")
        } catch (e: IllegalStateException) {
            assertEquals("stop", e.message)
        }
    }

    @Test
    fun `Bandit reads the middle of the channels, scaled as torchaudio, and its masks where it lays them`() {
        val layout = BanditMasks.LAYOUT
        assertEquals(1025, layout.bins)
        assertEquals(2, layout.residualTo)
        // A tone of 0.5 in bin 40 (937.5 Hz at 48 kHz), the same on both sides: the middle is the tone itself.
        val bin = 40
        val tone = object : StemSeparation.Source {
            override val length = 3L * layout.core * layout.hop
            override fun read(start: Long, into: FloatArray, count: Int) {
                for (i in 0 until count) {
                    val n = start + i
                    val v = if (n in 0 until length) (0.5 * cos(2 * PI * bin * n / BanditMasks.N)).toFloat() else 0f
                    into[i * 2] = v
                    into[i * 2 + 1] = v
                }
            }
        }
        var peak = 0f
        var peakBin = -1
        var calls = 0
        val network = BanditMasks.Network { spectrogram, frames ->
            calls++
            assertEquals(BanditMasks.FRAMES, frames)
            assertEquals(1025 * frames * 2, spectrogram.size)
            // A frame well inside the sound.
            if (calls == 2) {
                val f = frames / 2
                for (k in 0 until 1025) {
                    val m = sqrt(spectrogram[(k * frames + f) * 2].let { it * it } + spectrogram[(k * frames + f) * 2 + 1].let { it * it })
                    if (m > peak) {
                        peak = m
                        peakBin = k
                    }
                }
            }
            // Dialogue: everything up to bin 100; music: nothing.
            FloatArray(3 * 1025 * frames * 2).also { out ->
                for (k in 0 until 100) for (t in 0 until frames) out[((0 * 1025 + k) * frames + t) * 2] = 1f
            }
        }
        val out = Collected(3)
        StemSeparation(layout, BanditMasks(network)).separate(tone, out.sink)
        assertEquals(bin, peakBin)
        // |X[k]| of a cosine of amplitude A on a bin is A N / 4 under a Hann window; then divided by sqrt(3 N / 8).
        val expected = (0.5 * 2048 / 4 / sqrt(3.0 * 2048 / 8)).toFloat()
        assertEquals(expected, peak, expected * 1e-3f)
        // All of it is dialogue, none of it is left over (away from the sudden start and end, whose
        // clicks reach above bin 100).
        var rest = 0f
        for (i in 20_000 until out.stems[2].size - 20_000) rest = maxOf(rest, abs(out.stems[2][i]))
        assertTrue("effects $rest", rest < 1e-3f)
    }

    @Test
    fun `Spleeter keeps each instrument's share of the power, and the top end goes with the others`() {
        val layout = SpleeterMasks.LAYOUT
        assertEquals(4, layout.residualTo)
        val rate = 44_100.0
        // 200 Hz (bin 18.6), 2 kHz (bin 186) and 16 kHz (bin 1486: above what the network sees).
        val source = Tones(150_000L, rate, listOf(200.0 to 0.3, 2_000.0 to 0.2, 16_000.0 to 0.1))
        val network = SpleeterMasks.Network { magnitudes ->
            assertEquals(2 * 512 * 1024, magnitudes.size)
            // Drums (2) hear everything below bin 100, bass (3) everything from 100 up: by power, all of each.
            FloatArray(5 * 2 * 512 * 1024).also { out ->
                for (c in 0..1) for (f in 0 until 512) for (k in 0 until 1024) {
                    val m = magnitudes[(c * 512 + f) * 1024 + k]
                    val s = if (k < 100) 2 else 3
                    out[((s * 2 + c) * 512 + f) * 1024 + k] = m
                }
            }
        }
        val out = Collected(5)
        StemSeparation(layout, SpleeterMasks(network)).separate(source, out.sink)
        val low = Tones(150_000L, rate, listOf(200.0 to 0.3))
        val middle = Tones(150_000L, rate, listOf(2_000.0 to 0.2))
        val high = Tones(150_000L, rate, listOf(16_000.0 to 0.1))
        val error = DoubleArray(3)
        for (n in 0 until 150_000L) {
            val i = (n * 2).toInt()
            error[0] += (out.stems[2][i] - low.left(n)).let { it * it }
            error[1] += (out.stems[3][i] - middle.left(n)).let { it * it }
            error[2] += (out.stems[4][i] - high.left(n)).let { it * it }
        }
        for (e in error) assertTrue("error ${sqrt(e / 150_000)}", sqrt(e / 150_000) < 3e-3)
        assertTrue(out.stems[0].all { abs(it) < 1e-3f })
        assertEquals(0.5f, SpleeterMasks.mask(1f, 2f), 1e-6f)
        assertEquals(0.2f, SpleeterMasks.mask(0f, 0f), 1e-6f)
    }

    @Test
    fun `stems are named for good`() {
        assertEquals(listOf("dialogue", "music", "effects"), StemKind.SCENE.map { it.id })
        assertEquals(listOf("singing", "piano", "drums", "bass", "other"), StemKind.INSTRUMENTS.map { it.id })
        assertEquals(StemKind.DRUMS, StemKind.of("drums"))
        assertTrue(StemKind.BASS.isInstrument)
        assertTrue(!StemKind.MUSIC.isInstrument)
    }
}
