package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.model.TransitionKind
import com.cuscus.ffmpeggui.core.time.expectedOutputDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transitions between clips, in the command. */
class TransitionExportTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun graph(request: ConversionRequest): String =
        builder.build(request, MediaPaths.aliasesFor(request)).arguments.let { it[it.indexOf("-filter_complex") + 1] }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(
            SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true, frameRate = 25f),
            SourceInfo(durationMs = 10_000, width = 1280, height = 720, hasAudio = false, frameRate = 59.94f),
        ),
    )

    @Test
    fun `clips joined by transitions cross-fade, the others are concatenated`() {
        val request = video.copy(
            segments = listOf(
                Segment(0, 0, 3_000, Effects(transition = Transition(TransitionKind.WIPE_LEFT, 800))),
                Segment(1, 0, 2_000, Effects(speed = 2f)),
                Segment(0, 5_000, 7_000, Effects(transition = Transition(TransitionKind.RADIAL, 600))),
                Segment(0, 8_000, 9_000),
            ),
        )
        val g = graph(request)
        // One size, one frame rate (the fastest source), one pixel format.
        assertTrue(g, g.contains("setsar=1,fps=60,format=yuv420p[sv0]"))
        assertTrue(g, g.contains("setsar=1,fps=60,format=yuv420p[sv3]"))
        // Clip 2 lasts 1 s: its transition into it is half of that at most.
        assertTrue(g, g.contains("[sv0][sv1]xfade=transition=wipeleft:duration=0.500:offset=2.500[tv1]"))
        assertTrue(g, g.contains("[tv1][sv2]concat=n=2:v=1:a=0[tv2]"))
        // 2.5 + 1 + 2 seconds joined so far, less the overlap.
        assertTrue(g, g.contains("[tv2][sv3]xfade=transition=radial:duration=0.500:offset=5.000[vcat]"))
        assertTrue(g, g.contains("[ta2][sa3]acrossfade=d=0.500:c1=tri:c2=tri[acat]"))
        assertTrue(g, g.contains("[sa0][sa1]acrossfade=d=0.500:c1=tri:c2=tri[ta1]"))
        assertTrue(g, g.contains("anullsrc=r=48000:cl=stereo,atrim=duration=1.000,aformat=sample_fmts=fltp:channel_layouts=stereo[sa1]"))
        assertFalse(g, g.contains("concat=n=4"))
        assertEquals(3_000L + 1_000L + 2_000L + 1_000L - 500L - 500L, request.expectedOutputDurationMs())
    }

    @Test
    fun `without transitions the join is unchanged`() {
        val g = graph(video.copy(segments = listOf(Segment(0, 0, 3_000), Segment(0, 4_000, 6_000))))
        assertTrue(g, g.contains("concat=n=2:v=1:a=1"))
        assertFalse(g, g.contains("xfade"))
    }
}
