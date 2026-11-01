package com.cuscus.ffmpeggui.core.model

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.transition.ExEnv
import com.cuscus.ffmpeggui.core.transition.eval
import com.cuscus.ffmpeggui.core.transition.recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TV break ("technical difficulties"): as long as asked, within its own bounds, its sound over the clips'. */
class TvBreakTest {

    private val tv = TransitionKind.TECHNICAL_DIFFICULTIES

    @Test
    fun `it lasts as long as asked, longer than other transitions may`() {
        assertEquals(listOf(3_000L), transitionLengths(listOf(Transition(tv, 3_000)), listOf(10_000L, 10_000L)))
        assertEquals(listOf(12_000L), transitionLengths(listOf(Transition(tv, 12_000)), listOf(30_000L, 30_000L)))
        assertEquals(listOf(TV_BREAK_MAX_MS), transitionLengths(listOf(Transition(tv, 40_000)), listOf(60_000L, 60_000L)))
        // Others keep their own bounds.
        assertEquals(listOf(Transition.MAX_MS), transitionLengths(listOf(Transition(TransitionKind.FADE, 12_000)), listOf(30_000L, 30_000L)))
    }

    @Test
    fun `its sounds, each on or off and as loud as set, are kept with the project`() {
        val e = Effects(transition = Transition(tv, 4_000, sound = false, soundVolume = 0.4f, music = true, musicVolume = 1.5f))
        assertEquals(e.transition, com.cuscus.ffmpeggui.core.project.ModelJson.effects(com.cuscus.ffmpeggui.core.project.ModelJson.write(e)).transition)
    }

    @Test
    fun `too short for its card, the clips simply cut`() {
        assertEquals(listOf(0L), transitionLengths(listOf(Transition(tv, 3_000)), listOf(2_400L, 10_000L)))
        assertEquals(listOf(TV_BREAK_MIN_MS), transitionLengths(listOf(Transition(tv, 3_000)), listOf(3_200L, 10_000L)))
    }

    @Test
    fun `the clips fall silent under it, and cross-fade elsewhere`() {
        assertEquals(1f to 0f, clipSoundShares(tv, 0.0, 4_000))
        assertEquals(0f to 0f, clipSoundShares(tv, 2_000.0, 4_000))
        assertEquals(0f to 1f, clipSoundShares(tv, 4_000.0, 4_000))
        assertEquals(0.5f to 0.5f, clipSoundShares(TransitionKind.FADE, 250.0, 500))
        assertTrue(tv.mutesClips && !TransitionKind.FADE.mutesClips)
    }

    @Test
    fun `its picture cuts halfway, under the card`() {
        val recipe = tv.recipe!!
        assertEquals(0.0, recipe.incoming.alpha.eval(ExEnv(10.0, 10.0, 0.49, 64.0, 36.0)), 1e-9)
        assertEquals(1.0, recipe.incoming.alpha.eval(ExEnv(10.0, 10.0, 0.51, 64.0, 36.0)), 1e-9)
        // The card covers the cut however short the break.
        assertTrue(TV_BREAK_CARD_IN_MS < TV_BREAK_MIN_MS / 2)
        assertTrue(TV_BREAK_CARD_IN_MS < TV_BREAK_PART_MS)
    }

    @Test
    fun `the export silences the clips around it`() {
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "clip.mp4",
            sources = listOf(SourceInfo(durationMs = 20_000, width = 640, height = 360, hasAudio = true, frameRate = 25f)),
            segments = listOf(
                Segment(0, 0, 8_000, Effects(transition = Transition(tv, 4_000))),
                Segment(0, 10_000, 18_000),
            ),
        )
        val args = CommandBuilder { "/fonts/font.ttf" }.build(request, MediaPaths.aliasesFor(request)).arguments
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertTrue(graph, graph.contains("afade=t=out:st=4.000:d=0.250"))
        assertTrue(graph, graph.contains("afade=t=in:st=3.750:d=0.250"))
        assertTrue(graph, graph.contains("acrossfade=d=4.000:c1=nofade:c2=nofade"))
        assertFalse(graph, graph.contains("c1=tri"))
    }
}
