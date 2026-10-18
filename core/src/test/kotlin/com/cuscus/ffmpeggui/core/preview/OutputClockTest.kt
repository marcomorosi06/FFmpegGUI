package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputClockTest {

    @Test
    fun `without speed changes the clocks only differ by the trimmed start`() {
        val clock = OutputClock(listOf(SegmentTiming(4_000), SegmentTiming(6_000)), windowStartMs = 1_000)
        assertEquals(10_000L, clock.outputDurationMs)
        assertEquals(1_000.0, clock.timelineMs(0), 0.0)
        assertEquals(6_000.0, clock.timelineMs(5_000), 0.0)
        assertEquals(5_000L, clock.outputMs(6_000.0))
    }

    @Test
    fun `a fast clip takes less output time than timeline space`() {
        // 4 s at 2x (2 s of output), then 6 s at normal speed.
        val clock = OutputClock(listOf(SegmentTiming(4_000, speed = 2f), SegmentTiming(6_000)))
        assertEquals(8_000L, clock.outputDurationMs)
        assertEquals(2_000.0, clock.timelineMs(1_000), 0.0)
        assertEquals(4_000.0, clock.timelineMs(2_000), 0.0)
        assertEquals(7_000.0, clock.timelineMs(5_000), 0.0)
        assertEquals(5_000L, clock.outputMs(7_000.0))
        assertEquals(1_000L, clock.outputMs(2_000.0))
    }

    @Test
    fun `past either end the footage goes on at the whole-video speed`() {
        val clock = OutputClock(listOf(SegmentTiming(10_000)), speed = 2f, windowStartMs = 500)
        assertEquals(5_000L, clock.outputDurationMs)
        // 1 s of output past the end is 2 s of footage at 2x.
        assertEquals(12_500.0, clock.timelineMs(6_000), 0.0)
        assertEquals(6_000L, clock.outputMs(12_500.0))
        assertEquals(-1_500.0, clock.timelineMs(-1_000), 0.0)
        assertEquals(-1_000L, clock.outputMs(-1_500.0))
    }

    @Test
    fun `a reversed video maps the start of the output to the end of the footage`() {
        val clock = OutputClock(listOf(SegmentTiming(10_000)), reversed = true)
        assertEquals(10_000.0, clock.timelineMs(0), 0.0)
        assertEquals(7_000.0, clock.timelineMs(3_000), 0.0)
        assertEquals(3_000L, clock.outputMs(7_000.0))
        assertEquals(-1_000.0, clock.timelineMs(11_000), 0.0)
        assertEquals(11_000L, clock.outputMs(-1_000.0))
    }
}
