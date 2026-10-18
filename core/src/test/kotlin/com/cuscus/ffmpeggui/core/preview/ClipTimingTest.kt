package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.ClipPosition
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preview clocks and subtitle timing when clips have their own speed and direction. */
class ClipTimingTest {

    // 4 s at normal speed, then 6 s at 2x played backwards.
    private val timeline = PreviewTimeline(
        listOf(SegmentTiming(4_000), SegmentTiming(6_000, speed = 2f, reversed = true)),
    )

    @Test
    fun `lengths add up clip by clip`() {
        assertEquals(10_000L, timeline.mediaDurationMs)
        assertEquals(7_000L, timeline.joinedDurationMs)
        assertEquals(7_000L, timeline.outputDurationMs)
    }

    @Test
    fun `a reversed clip starts from its last frame`() {
        // Output 4 s is the start of the second clip, i.e. the end of its media.
        assertEquals(10_000L, timeline.mediaMsForOutput(4_000))
        assertEquals(4_000L, timeline.outputMs(10_000))
        // Half way through it (1.5 s of output) is the middle of its media.
        assertEquals(7_000L, timeline.mediaMsForOutput(5_500))
        assertEquals(5_500L, timeline.outputMs(7_000))
        assertTrue(timeline.playsBackwards(1))
        assertFalse(timeline.playsBackwards(0))
        assertEquals(2f, timeline.rate(1))
    }

    @Test
    fun `output and media clocks are inverse of each other`() {
        val whole = PreviewTimeline(
            listOf(SegmentTiming(4_000, speed = 0.5f), SegmentTiming(6_000, speed = 2f, reversed = true), SegmentTiming(2_000)),
            speed = 1.5f,
            reversed = true,
        )
        for (outputMs in 0L..whole.outputDurationMs step 250) {
            val media = whole.mediaMsForOutput(outputMs)
            assertEquals("at $outputMs", outputMs.toDouble(), whole.outputMs(media).toDouble(), 2.0)
        }
        // Reversing the whole video turns the reversed clip forwards again.
        assertFalse(whole.playsBackwards(1))
        assertTrue(whole.playsBackwards(0))
        assertEquals(0.75f, whole.rate(0))
    }

    @Test
    fun `clip position follows the clip's own clock`() {
        assertEquals(ClipPosition(0, 1_000, 4_000), timeline.clipPosition(1_000))
        // 2 s into the reversed clip's media is 4 s before its end: at 2x, 2 s into its 3 s of playback.
        assertEquals(ClipPosition(1, 2_000, 3_000), timeline.clipPosition(6_000))
    }

    @Test
    fun `subtitles follow a clip's speed and direction`() {
        val segments = listOf(
            Segment(0, 0, 4_000),
            Segment(0, 10_000, 16_000, Effects(speed = 2f, reversed = true)),
        )
        // A line at 11..12 s of the file is 4..5 s before the end of the second clip:
        // at 2x backwards it is shown 2..2.5 s into it, i.e. at 6..6.5 s.
        assertEquals(listOf(6_000L to 6_500L), mapToTimeline(11_000, 12_000, segments, sourceIndex = 0))
        // The first clip is untouched.
        assertEquals(listOf(1_000L to 2_000L), mapToTimeline(1_000, 2_000, segments, sourceIndex = 0))
        // Whole video reversed: mirrored on the 7 s joined length.
        assertEquals(listOf(500L to 1_000L), mapToTimeline(11_000, 12_000, segments, sourceIndex = 0, reversed = true))
    }

    @Test
    fun `fade levels of a clip use its played clock`() {
        val position = timeline.clipPosition(9_000) // 0.5 s into the reversed clip's playback
        assertEquals(0.5f, fadeLevel(position.playedOffsetMs, position.playedLengthMs, 1f, 0f), 0.001f)
    }
}
