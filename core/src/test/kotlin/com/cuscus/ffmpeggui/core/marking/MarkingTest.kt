package com.cuscus.ffmpeggui.core.marking

import com.cuscus.ffmpeggui.core.voice.WavWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class MarkingTest {

    @get:Rule
    val temp = TemporaryFolder()

    // --- The payloads: nothing but "made with AI" -----------------------------------------

    @Test
    fun `both marks carry only constants, the same for everyone`() {
        assertTrue(Marks.SPEECH_MESSAGE.all { it == 0L })
        assertEquals(16, Marks.SPEECH_MESSAGE.size)
        assertEquals(100, Marks.PICTURE_CODEWORD.length)
        assertEquals("01".repeat(20), Marks.PICTURE_CODEWORD.take(40))
        assertEquals("0000", Marks.PICTURE_CODEWORD.takeLast(4))
        assertEquals(Marks.PICTURE_CODEWORD, Marks.PICTURE_PAYLOAD.joinToString("") { if (it == 1f) "1" else "0" })
        // A fresh copy each time: nothing can be written into a shared one.
        Marks.PICTURE_PAYLOAD[0] = 1f
        assertEquals(0f, Marks.PICTURE_PAYLOAD[0], 0f)
    }

    @Test
    fun `the picture mark is found through up to eight wrong bits, its version exactly`() {
        val logits = FloatArray(100) { if (Marks.PICTURE_CODEWORD[it] == '1') 1f else -1f }
        assertEquals(0, Marks.pictureErrors(logits))
        assertTrue(Marks.pictureMarked(logits))
        for (i in listOf(0, 10, 39, 40, 60, 80, 90, 95)) logits[i] = -logits[i]
        assertEquals(8, Marks.pictureErrors(logits))
        assertTrue(Marks.pictureMarked(logits))
        logits[50] = -logits[50]
        assertFalse(Marks.pictureMarked(logits))
        // All zeros (an unmarked reading, or the old payload): far from the mark.
        assertFalse(Marks.pictureMarked(FloatArray(100) { -1f }))
        // Another schema (BCH_5 ends in 01): not this mark, however its bits.
        val other = FloatArray(100) { if (Marks.PICTURE_CODEWORD[it] == '1') 1f else -1f }.also { it[99] = 1f }
        assertNull(Marks.pictureErrors(other))
        assertFalse(Marks.pictureMarked(FloatArray(10)))
    }

    @Test
    fun `the speech message is ours when every bit reads 0`() {
        assertTrue(Marks.speechMessageIsOurs(FloatArray(16) { 0.1f }))
        assertFalse(Marks.speechMessageIsOurs(FloatArray(16) { 0.1f }.also { it[3] = 0.7f }))
        assertFalse(Marks.speechMessageIsOurs(FloatArray(8)))
    }

    // --- Speech ------------------------------------------------------------------------------

    @Test
    fun `marking windows overlap by half a second and cover everything`() {
        assertEquals(emptyList<SpeechMarking.Window>(), SpeechMarking.markWindows(0))
        assertEquals(listOf(SpeechMarking.Window(0, 1000)), SpeechMarking.markWindows(1000))
        assertEquals(listOf(SpeechMarking.Window(0, 80_000)), SpeechMarking.markWindows(80_000))
        val w = SpeechMarking.markWindows(200_000)
        assertEquals(listOf(SpeechMarking.Window(0, 80_000), SpeechMarking.Window(72_000, 80_000), SpeechMarking.Window(144_000, 56_000)), w)
        // The last always reaches the end, and is longer than the overlap.
        for (n in listOf(80_001, 152_000, 152_001, 160_000, 1_000_003)) {
            val ws = SpeechMarking.markWindows(n)
            assertEquals(n, ws.last().start + ws.last().length)
            assertTrue(ws.last().length > SpeechMarking.OVERLAP)
        }
        assertEquals(listOf(0, 80_000, 160_000), SpeechMarking.checkWindows(200_000).map { it.start })
    }

    @Test
    fun `joined windows of a steady mark give it back unchanged, crossfades included`() {
        val total = 230_000
        val windows = SpeechMarking.markWindows(total)
        val marks = windows.map { FloatArray(SpeechMarking.WINDOW) { 0.25f } }
        val joined = SpeechMarking.join(total, windows, marks)
        for (v in joined) assertEquals(0.25f, v, 1e-5f)
        val padded = SpeechMarking.padded(FloatArray(total) { 1f }, windows.last())
        assertEquals(SpeechMarking.WINDOW, padded.size)
        assertEquals(1f, padded[windows.last().length - 1], 0f)
        assertEquals(0f, padded[windows.last().length], 0f)
    }

    @Test
    fun `only speech counts, and too little cannot be told`() {
        val audio = FloatArray(32_000) { i -> if (i < 16_000) (0.2 * sin(2 * PI * 200 * i / 16_000.0)).toFloat() else 0f }
        val voiced = SpeechMarking.voiced(audio)
        assertTrue(voiced[100] && !voiced[20_000])
        val probabilities = FloatArray(32_000) { i -> if (i < 12_000) 0.9f else 0.1f }
        assertEquals(0.75f, SpeechMarking.share(probabilities, voiced)!!, 0.02f)
        assertNull(SpeechMarking.share(probabilities, BooleanArray(32_000) { it < 1000 }))
        val message = SpeechMarking.message(listOf(FloatArray(16) { 0.2f }, FloatArray(16) { 0.8f }), listOf(3, 1))
        assertEquals(0.35f, message[0], 1e-5f)
    }

    @Test
    fun `a dub's lines are found between its silences`() {
        val rate = 1000
        val samples = FloatArray(5000)
        for (i in 1000 until 1500) samples[i] = 0.3f
        // A short pause (0.1 s) inside the line does not split it.
        for (i in 1600 until 2000) samples[i] = -0.2f
        for (i in 3500 until 3600) samples[i] = 0.1f
        assertEquals(listOf(990..2009, 3490..3609), SpeechMarking.runs(samples, rate))
        assertEquals(emptyList<IntRange>(), SpeechMarking.runs(FloatArray(100), rate))
        assertEquals(listOf(0..9), SpeechMarking.runs(FloatArray(10) { 0.5f }, rate))
    }

    @Test
    fun `the resampler keeps a tone and its loudness, both ways`() {
        val from = 24_000
        val tone = FloatArray(from) { i -> (0.5 * sin(2 * PI * 440 * i / from)).toFloat() }
        val down = Resampler.resample(tone, from, 16_000)
        assertEquals(16_000, down.size)
        val expected = FloatArray(16_000) { i -> (0.5 * sin(2 * PI * 440 * i / 16_000.0)).toFloat() }
        // Away from the edges, the same tone.
        for (i in 200 until 15_800) assertEquals(expected[i], down[i], 0.01f)
        val back = Resampler.resample(down, 16_000, from, tone.size)
        assertEquals(tone.size, back.size)
        for (i in 300 until 23_700) assertEquals(tone[i], back[i], 0.02f)
        // Above the lower Nyquist nothing comes through.
        val high = FloatArray(from) { i -> (0.5 * sin(2 * PI * 10_000 * i / from)).toFloat() }
        val gone = Resampler.resample(high, from, 16_000)
        assertTrue(rms(gone, 200, 15_800) < 0.01)
        assertEquals(22_050, Resampler.lengthAt(16_000, 16_000, 22_050))
    }

    private fun rms(a: FloatArray, from: Int, to: Int): Double = sqrt((from until to).sumOf { (a[it] * a[it]).toDouble() } / (to - from))

    @Test
    fun `a dub's WAV reads back as it was written`() {
        val file = temp.newFile("dub.wav")
        val samples = FloatArray(1000) { i -> (i - 500) / 600f }
        WavWriter(file, 1, 24_000, comment = "AI-generated voice").use { it.write(samples, samples.size) }
        val sound = PcmWav.read(file)
        assertEquals(24_000, sound.rate)
        assertEquals(1000, sound.samples.size)
        for (i in samples.indices) assertEquals(samples[i], sound.samples[i], 1f / 16_000)
    }

    // --- Pictures ---------------------------------------------------------------------------

    @Test
    fun `the region is the whole picture, or its centre square when it is very long`() {
        assertEquals(PictureMarking.Region(0, 0, 400, 300), PictureMarking.region(400, 300))
        assertEquals(PictureMarking.Region(0, 0, 200, 400), PictureMarking.region(200, 400))
        assertEquals(PictureMarking.Region(150, 0, 100, 100), PictureMarking.region(400, 100))
        assertEquals(PictureMarking.Region(0, 175, 50, 50), PictureMarking.region(50, 400))
        assertEquals(1, PictureMarking.feather(PictureMarking.Region(0, 0, 90, 90)))
        assertEquals(30, PictureMarking.feather(PictureMarking.Region(0, 0, 4000, 3000)))
        assertEquals(50, PictureMarking.feather(PictureMarking.Region(0, 0, 9000, 9000)))
    }

    @Test
    fun `the mark fades in at the edges, columns over rows`() {
        val r = PictureMarking.Region(0, 0, 100, 100)
        assertEquals(1f, PictureMarking.weight(50, 50, r, 4), 0f)
        assertEquals(0.25f, PictureMarking.weight(0, 50, r, 4), 1e-6f)
        assertEquals(0.25f, PictureMarking.weight(99, 50, r, 4), 1e-6f)
        assertEquals(0.5f, PictureMarking.weight(50, 1, r, 4), 1e-6f)
        assertEquals(0.75f, PictureMarking.weight(2, 0, r, 4), 1e-6f)
    }

    @Test
    fun `the residual has no colour shift, and stretches back smoothly`() {
        val n = PictureMarking.SIZE * PictureMarking.SIZE
        val cover = FloatArray(3 * n) { 0.1f }
        val stego = FloatArray(3 * n) { i -> 0.1f + (if (i % 2 == 0) 0.02f else 0f) + if (i < n) 0.5f else 0f }
        stego[5] = 3f
        val residual = PictureMarking.residual(stego, cover)
        for (c in 0 until 3) assertEquals(0.0, (0 until n).sumOf { residual[c * n + it].toDouble() } / n, 1e-5)
        // Stretched to the same size: as it is.
        val row = FloatArray(3 * PictureMarking.SIZE)
        PictureMarking.residualRow(residual, 7, PictureMarking.SIZE, PictureMarking.SIZE, row)
        for (x in 0 until PictureMarking.SIZE) assertEquals(residual[7 * PictureMarking.SIZE + x], row[x * 3], 1e-6f)
        // Twice as large: between its neighbours.
        val wide = FloatArray(3 * 512)
        PictureMarking.residualRow(residual, 100, 512, 512, wide)
        val a = residual[50 * 256 + 10]
        val b = residual[50 * 256 + 11]
        assertTrue(wide[21 * 3] in minOf(a, b) - 1e-6f..maxOf(a, b) + 1e-6f)
    }

    @Test
    fun `adding the mark stays in range and fades with its weight`() {
        assertEquals(255, PictureMarking.apply(250, 0.5f, 1f))
        assertEquals(0, PictureMarking.apply(3, -0.5f, 1f))
        assertEquals(128, PictureMarking.apply(128, 0f, 1f))
        assertEquals(100, PictureMarking.apply(100, 0.3f, 0f))
        val half = PictureMarking.apply(100, 0.2f, 0.5f)
        assertTrue(half in 112..113)
    }

    @Test
    fun `the squeezer averages what each square covers, and fills a small picture`() {
        val s = PictureMarking.Squeezer(512, 512)
        val white = IntArray(512) { 0xFFFFFFFF.toInt() }
        val black = IntArray(512) { 0xFF000000.toInt() }
        for (y in 0 until 512) s.add(y, if (y % 2 == 0) white else black)
        val out = s.result()
        for (v in out) assertEquals(0f, v, 1e-3f)
        val small = PictureMarking.Squeezer(10, 10)
        val red = IntArray(10) { 0xFFFF0000.toInt() }
        for (y in 0 until 10) small.add(y, red)
        val r = small.result()
        val n = 256 * 256
        assertTrue((0 until n).all { abs(r[it] - 1f) < 1e-4f })
        assertTrue((n until 2 * n).all { abs(r[it] + 1f) < 1e-4f })
    }

    @Test
    fun aMarkIsMeasuredAndAddedWithinRange() {
        val plane = PictureMarking.SIZE * PictureMarking.SIZE
        val residual = FloatArray(3 * plane) { if (it % 2 == 0) 0.01f else -0.01f }
        assertEquals(0.01f, PictureMarking.meanMagnitude(residual), 1e-6f)
        assertTrue(PictureMarking.meanMagnitude(residual) <= PictureMarking.MOST_MEAN_MAGNITUDE)
        // Burnt: far stronger than TrustMark's own.
        assertTrue(PictureMarking.meanMagnitude(FloatArray(3 * plane) { 0.5f }) > PictureMarking.MOST_MEAN_MAGNITUDE)
        val cover = FloatArray(3 * plane) { 0.995f }
        val marked = PictureMarking.marked(cover, FloatArray(3 * plane) { 0.02f })
        assertEquals(1f, marked[0], 0f)
        assertEquals(0.5f, PictureMarking.marked(FloatArray(3 * plane) { 0.49f }, FloatArray(3 * plane) { 0.01f })[5], 1e-6f)
    }
}
