package com.cuscus.ffmpeggui.core.model

import com.cuscus.ffmpeggui.core.preview.PreviewTimeline
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming
import com.cuscus.ffmpeggui.core.preview.mapToTimeline
import com.cuscus.ffmpeggui.core.project.ModelJson
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionTest {

    private val fade = Transition(TransitionKind.FADE, 1_000)

    @Test
    fun `the playhead glides across a transition and does not jump when it ends`() {
        val timeline = PreviewTimeline(listOf(SegmentTiming(4_000, transition = fade), SegmentTiming(4_000)), speed = 1f)
        // Outside it, the media on screen.
        assertEquals(timeline.mediaMsForOutput(2_000), timeline.playheadMs(2_000))
        // The transition shows from 3 s to 4 s of the output: from 3 s of the first clip to 1 s into the second.
        assertEquals(3_000L, timeline.playheadMs(3_000))
        assertEquals(4_000L, timeline.playheadMs(3_500))
        val end = timeline.playheadMs(3_999)
        assertTrue("$end", kotlin.math.abs(end - timeline.mediaMsForOutput(4_000)) <= 3)
        assertEquals(timeline.mediaMsForOutput(4_000), timeline.playheadMs(4_000))
    }

    @Test
    fun `a transition is never longer than half of either clip`() {
        assertEquals(1_000L, Transition.effectiveMs(1_000, 4_000, 4_000))
        assertEquals(750L, Transition.effectiveMs(1_000, 1_500, 4_000))
        assertEquals(Transition.MAX_MS, Transition.effectiveMs(10_000, 60_000, 60_000))
        // Too short to be seen: none.
        assertEquals(0L, Transition.effectiveMs(1_000, 80, 4_000))
        assertEquals(listOf(1_000L, 0L), transitionLengths(listOf(fade, null, fade), listOf(3_000L, 3_000L, 3_000L)))
        assertEquals(listOf(0L), transitionLengths(listOf(fade), listOf(3_000L, null)))
        assertTrue(transitionLengths(listOf(fade), listOf(3_000L)).isEmpty())
    }

    @Test
    fun `clips under an effect meet with a cut, found where the next one starts`() {
        val fairy = Transition(effect = "fairy")
        assertEquals(listOf(0L, 1_000L), transitionLengths(listOf(fairy, fade, null), listOf(3_000L, 3_000L, 3_000L)))
        val timeline = PreviewTimeline(
            listOf(SegmentTiming(4_000, transition = fairy), SegmentTiming(4_000, speed = 2f, transition = fade), SegmentTiming(3_000)),
            speed = 2f,
        )
        assertEquals(listOf(0L, 1_000L), timeline.overlaps)
        assertNull(timeline.transitionAt(2_000))
        // 4 s of the first clip at the video's double speed.
        assertEquals(2_000L, timeline.cutOutputMs(0))
        assertEquals(2_500L, timeline.cutOutputMs(1))
        assertNull(timeline.cutOutputMs(2))
        // Backwards, the first cut comes last.
        val back = PreviewTimeline(listOf(SegmentTiming(4_000, transition = fairy), SegmentTiming(1_000)), speed = 1f, reversed = true)
        assertEquals(1_000L, back.cutOutputMs(0))
        val saved = ModelJson.effects(ModelJson.write(Effects(transition = fairy)))
        assertEquals(fairy, saved.transition)
    }

    @Test
    fun `clips joined by a transition overlap on the preview timeline`() {
        val timeline = PreviewTimeline(
            listOf(SegmentTiming(4_000, transition = fade), SegmentTiming(4_000, speed = 2f, transition = fade), SegmentTiming(3_000)),
            speed = 1f,
        )
        // 4 + 2 + 3 seconds, less two overlaps of a second (the second clip plays 2 s).
        assertEquals(listOf(1_000L, 1_000L), timeline.overlaps)
        assertEquals(7_000L, timeline.outputDurationMs)
        assertEquals(3_000L, timeline.joinedStartMs(1))
        assertEquals(4_000L, timeline.joinedStartMs(2))
        assertNull(timeline.transitionAt(2_999))
        val mid = timeline.transitionAt(3_500)!!
        assertEquals(0, mid.outgoing)
        assertEquals(1, mid.incoming)
        assertEquals(0.5f, mid.progress, 1e-3f)
        // Half a second played at double speed: a second into its footage.
        assertEquals(1_000L, mid.incomingOffsetMs)
        assertEquals(500L, mid.incomingPlayedMs)
        assertEquals(2, timeline.transitionAt(4_200)!!.incoming)
        assertEquals(3_000L..4_000L, timeline.transitionOutputRange(0))
        assertEquals(4_000L..5_000L, timeline.transitionOutputRange(1))
        assertTrue(timeline.transitionOutputRange(2).isEmpty())
        // The main player stays on the outgoing clip during the overlap.
        assertTrue(timeline.mediaMsForOutput(3_900) < 4_000)
        assertNull(timeline.transitionAt(5_500))
        // The second clip shows its first 2 s of footage (1 s at double speed) only inside the transition.
        assertEquals(0L, timeline.incomingFootageMs(0))
        assertEquals(2_000L, timeline.incomingFootageMs(1))
        assertEquals(1_000L, timeline.incomingFootageMs(2))
    }

    @Test
    fun `subtitles of a later clip move back by the overlap`() {
        val segments = listOf(
            Segment(0, 0, 4_000, Effects(transition = fade)),
            Segment(1, 0, 4_000),
        )
        assertEquals(listOf(3_500L to 4_000L), mapToTimeline(500, 1_000, segments, sourceIndex = 1))
    }

    @Test
    fun `a transition is saved with its clip and stays on the second half of a cut`() {
        val effects = Effects(transition = Transition(TransitionKind.CIRCLE_OPEN, 1_250))
        assertEquals(effects, ModelJson.effects(ModelJson.write(effects)))
        assertEquals(Effects(), ModelJson.effects(ModelJson.write(Effects())))
        val clip = Clip(id = "a", sourceId = "s", startMs = 0, endMs = 4_000, effects = effects)
        val (first, second) = Timeline(listOf(clip)).splitAt(1_000, "b").clips
        assertNull(first.effects.transition)
        assertEquals(effects.transition, second.effects.transition)
    }
}
