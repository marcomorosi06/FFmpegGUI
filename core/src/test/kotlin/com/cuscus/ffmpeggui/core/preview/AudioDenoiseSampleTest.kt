package com.cuscus.ffmpeggui.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDenoiseSampleTest {

    private fun request(position: Long, start: Long = 0L, end: Long? = null, strengths: List<Float> = listOf(0.5f)) =
        AudioDenoiseSampleRequest("content://a", position, start, end, strengths)

    @Test
    fun `the part heard starts at the playhead and fits in the clip`() {
        assertEquals(10_000L, AudioDenoiseSample.startOf(request(10_000L, end = 60_000L)))
        // Near the clip's end it moves back, so that the whole length is heard.
        assertEquals(52_000L, AudioDenoiseSample.startOf(request(58_000L, end = 60_000L)))
        assertEquals(8_000L, AudioDenoiseSample.lengthOf(request(58_000L, end = 60_000L)))
        // A clip shorter than the length is heard whole.
        assertEquals(1_000L, AudioDenoiseSample.startOf(request(3_000L, start = 1_000L, end = 4_000L)))
        assertEquals(3_000L, AudioDenoiseSample.lengthOf(request(3_000L, start = 1_000L, end = 4_000L)))
        // Never before the clip's start.
        assertEquals(5_000L, AudioDenoiseSample.startOf(request(2_000L, start = 5_000L)))
    }

    @Test
    fun `both halves come from one decode, the denoised one through the export's filter`() {
        val command = AudioDenoiseSample.command(request(10_000L, end = 60_000L, strengths = listOf(0.5f, 0f, 1f)), "in.mp4", "b.wav", "a.wav")
        val args = command.arguments
        assertEquals("9.750", args[args.indexOf("-ss") + 1])
        assertEquals("8.250", args[args.indexOf("-t") + 1])
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertTrue(graph.startsWith("[0:a:0]aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo,asplit=2[raw][dirty];"))
        assertTrue(graph.contains("[dirty]anlmdn=s=5.00,anlmdn=s=10.00,atrim=start=0.250,asetpts=PTS-STARTPTS[after]"))
        assertTrue(graph.contains("[raw]atrim=start=0.250,asetpts=PTS-STARTPTS[before]"))
        assertTrue(args.containsAll(listOf("b.wav", "a.wav")))
    }

    @Test
    fun `at the clip's start there is no lead-in`() {
        val graph = AudioDenoiseSample.command(request(0L, end = 20_000L), "in", "b", "a").arguments.let { it[it.indexOf("-filter_complex") + 1] }
        assertTrue(graph.contains("[raw]anull[before]"))
        assertFalse(graph.contains("atrim"))
    }

    @Test
    fun `nothing to compare without a denoise`() {
        assertFalse(request(0L, strengths = listOf(0f)).denoises)
        assertTrue(request(0L).denoises)
    }
}
