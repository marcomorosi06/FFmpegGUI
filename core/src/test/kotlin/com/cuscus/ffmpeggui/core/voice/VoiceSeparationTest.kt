package com.cuscus.ffmpeggui.core.voice

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceSeparationTest {

    private val rate = VoiceSeparation.SAMPLE_RATE.toDouble()

    /**
     * A sound made of [tones] (Hz to amplitude), the right side a quarter turn
     * on. It fades in and out: a sudden start would have a top end above what
     * the networks see, which is never voice.
     */
    private class Tones(override val length: Long, val tones: List<Pair<Double, Double>>) : VoiceSeparation.Source {
        private fun envelope(n: Long): Double {
            val edge = minOf(n, length - 1 - n).coerceAtLeast(0L)
            return if (edge >= RAMP) 1.0 else 0.5 - 0.5 * cos(PI * edge / RAMP)
        }

        fun left(n: Long): Float = (envelope(n) * tones.sumOf { (f, a) -> a * sin(2 * PI * f * n / 44_100.0) }).toFloat()
        fun right(n: Long): Float = (envelope(n) * tones.sumOf { (f, a) -> a * cos(2 * PI * f * n / 44_100.0) }).toFloat()

        private companion object {
            const val RAMP = 8192L
        }

        override fun read(start: Long, into: FloatArray, count: Int) {
            for (i in 0 until count) {
                val n = start + i
                val inside = n in 0 until length
                into[i * 2] = if (inside) left(n) else 0f
                into[i * 2 + 1] = if (inside) right(n) else 0f
            }
        }
    }

    private class Collected {
        val voice = ArrayList<Float>()
        val rest = ArrayList<Float>()
        val sink = VoiceSeparation.Sink { v, r, count ->
            for (i in 0 until count * 2) {
                voice += v[i]
                rest += r[i]
            }
        }
    }

    /** A model that hears a voice in the bins below [below] (all of it when it is past the top). */
    private fun lowIsVoice(below: Int) = VoiceSeparation.StemModel { magnitudes ->
        val vocals = FloatArray(magnitudes.size)
        val accompaniment = FloatArray(magnitudes.size)
        for (i in magnitudes.indices) {
            if (i % VoiceSeparation.BINS < below) vocals[i] = magnitudes[i] else accompaniment[i] = magnitudes[i]
        }
        vocals to accompaniment
    }

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
    fun `all voice gives the sound back whole, over several windows and without seams`() {
        // Two and a half windows, so that the joins and a short last window are crossed.
        val length = (VoiceSeparation.CORE_FRAMES * VoiceSeparation.HOP * 2.5).toLong()
        val source = Tones(length, listOf(220.0 to 0.4, 1_000.0 to 0.2))
        val out = Collected()
        var progress = 0f
        VoiceSeparation(lowIsVoice(VoiceSeparation.BINS)).separate(source, out.sink, onProgress = { progress = it })
        assertEquals(1f, progress, 0f)
        assertEquals(length * 2, out.voice.size.toLong())
        var worst = 0f
        for (n in 0 until length) {
            worst = maxOf(worst, abs(out.voice[(n * 2).toInt()] - source.left(n)), abs(out.voice[(n * 2 + 1).toInt()] - source.right(n)))
        }
        assertTrue("voice off by $worst", worst < 1e-3f)
        assertTrue(out.rest.all { abs(it) < 1e-3f })
    }

    @Test
    fun `each part keeps its own tones, and the two add up to the sound`() {
        val length = 100_000L
        // 300 Hz is in bin 28, 4 kHz in bin 371 and 15 kHz above what the networks see.
        val source = Tones(length, listOf(300.0 to 0.3, 4_000.0 to 0.2, 15_000.0 to 0.1))
        val out = Collected()
        VoiceSeparation(lowIsVoice(100)).separate(source, out.sink)
        val low = Tones(length, listOf(300.0 to 0.3))
        var voiceError = 0.0
        var sumError = 0.0
        val buffer = FloatArray(2)
        for (n in 0 until length) {
            source.read(n, buffer, 1)
            val i = (n * 2).toInt()
            voiceError += (out.voice[i] - low.left(n)).let { it * it }
            sumError = maxOf(sumError, abs(out.voice[i] + out.rest[i] - buffer[0]).toDouble())
        }
        val rms = sqrt(voiceError / length)
        assertTrue("voice error $rms", rms < 2e-3)
        assertTrue("sum off by $sumError", sumError < 1e-5)
    }

    @Test
    fun `the mask is the voice's share of the power`() {
        assertEquals(0.5f, VoiceSeparation.mask(1f, 1f), 1e-6f)
        assertEquals(0.8f, VoiceSeparation.mask(2f, 1f), 1e-6f)
        assertEquals(0.5f, VoiceSeparation.mask(0f, 0f), 1e-6f)
    }

    @Test
    fun `stems are read and written as the files say`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "stems-" + System.nanoTime()).also { it.mkdirs() }
        try {
            val raw = File(dir, "in.pcm")
            val shorts = shortArrayOf(1000, -1000, 16384, -16384, 32767, -32768)
            val bytes = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            shorts.forEach { bytes.putShort(it) }
            raw.writeBytes(bytes.array())
            PcmStereoFile(raw).use { pcm ->
                assertEquals(3L, pcm.length)
                val into = FloatArray(8)
                pcm.read(-1, into, 4)
                assertEquals(0f, into[0], 0f)
                assertEquals(1000 / 32768f, into[2], 1e-7f)
                assertEquals(0.5f, into[4], 1e-7f)
                assertEquals(-1f, into[7], 1e-7f)
            }
            val wav = File(dir, "out.wav")
            WavWriter(wav, VoiceStem.CHANNELS, 44_100).use { it.write(floatArrayOf(0.5f, -0.5f, 2f, -2f), 1) }
            val written = ByteBuffer.wrap(wav.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(WavWriter.HEADER_BYTES + 8, wav.length().toInt())
            assertEquals(60 + 8, written.getInt(4))
            assertEquals(40, written.getInt(16))
            assertEquals(0xFFFE, written.getShort(20).toInt() and 0xFFFF)
            assertEquals(4, written.getShort(22).toInt())
            assertEquals(44_100, written.getInt(24))
            // Four channels named quad: what FFmpeg's FLAC encoder takes.
            assertEquals(0x33, written.getInt(40))
            assertEquals(8, written.getInt(64))
            assertEquals(16384, written.getShort(68).toInt())
            assertEquals(32767, written.getShort(72).toInt())
            assertEquals(-32768, written.getShort(74).toInt())
            // A comment goes before the samples, in a LIST INFO chunk.
            val tagged = File(dir, "tagged.wav")
            WavWriter(tagged, 1, 24_000, comment = "AI-generated speech").use { it.write(floatArrayOf(0.25f), 1) }
            val taggedBytes = tagged.readBytes()
            val info = WavWriter.infoChunk("AI-generated speech")
            assertEquals(WavWriter.HEADER_BYTES + info.size + 2, taggedBytes.size)
            assertEquals("LIST", String(taggedBytes, 60, 4, Charsets.US_ASCII))
            assertEquals("ICMT", String(taggedBytes, 72, 4, Charsets.US_ASCII))
            assertEquals("AI-generated speech", String(taggedBytes, 80, 19, Charsets.US_ASCII))
            assertEquals("data", String(taggedBytes, 60 + info.size, 4, Charsets.US_ASCII))
            assertEquals(0, info.size % 2)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a clip with its voice set apart takes its sound from the stems, mixed to its levels`() {
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "clip.mp4",
            sources = listOf(SourceInfo(durationMs = 20_000, width = 1280, height = 720, hasAudio = true, voiceStem = "/stems/clip.flac")),
            segments = listOf(Segment(0, 2_000, 8_000, effects = Effects(voiceLevel = 0.2f, backgroundLevel = 1.5f))),
        )
        val args = CommandBuilder { "/fonts/Regular.ttf" }.build(request, MediaPaths.aliasesFor(request)).arguments
        val stem = args.indexOf("/stems/clip.flac")
        assertTrue(args.toString(), stem > 0)
        // From the same part of the file as the clip.
        assertEquals(listOf("-ss", "00:00:02.000", "-t", "00:00:06.000", "-i"), args.subList(stem - 5, stem))
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertTrue(graph, graph.contains("[1:a]pan=stereo|c0=0.2000*c0+1.5000*c2|c1=0.2000*c1+1.5000*c3"))
        assertFalse(graph, graph.contains("[0:a]"))

        // Without stems, or at the levels as recorded, the file's own sound plays.
        val plain = request.copy(sources = listOf(request.sources[0].copy(voiceStem = null)))
        val plainArgs = CommandBuilder { "/fonts/Regular.ttf" }.build(plain, MediaPaths.aliasesFor(plain)).arguments
        assertFalse(plainArgs.contains("/stems/clip.flac"))
        assertFalse(Effects(voiceLevel = 1f, backgroundLevel = 1f).splitsVoice)
        assertTrue(Effects(backgroundLevel = 0f).splitsVoice)
    }

    @Test
    fun `the levels are kept in a project`() {
        val effects = Effects(voiceLevel = 0.25f, backgroundLevel = 1.75f)
        val back = ModelJson.effects(ModelJson.write(effects))
        assertEquals(0.25f, back.voiceLevel, 1e-6f)
        assertEquals(1.75f, back.backgroundLevel, 1e-6f)
        assertEquals(1f, ModelJson.effects(ModelJson.write(Effects())).voiceLevel, 0f)
    }
}
