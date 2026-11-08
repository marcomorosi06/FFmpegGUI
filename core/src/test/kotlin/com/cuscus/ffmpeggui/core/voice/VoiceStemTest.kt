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

/** The files a separation leaves, and how a clip with its voice set apart is mixed (see VoiceStem). */
class VoiceStemTest {

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
