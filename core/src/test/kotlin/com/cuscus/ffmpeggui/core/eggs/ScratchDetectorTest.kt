package com.cuscus.ffmpeggui.core.eggs

import org.junit.Assert.assertEquals
import org.junit.Test

class ScratchDetectorTest {

    private val ms = 1_000_000L

    /** Swings of [px] each way, [stepMs] apart, in [steps] steps per swing; the steps of the drag. */
    private fun swings(detector: ScratchDetector, count: Int, px: Float, stepMs: Long, steps: Int = 4): List<ScratchDetector.Step> {
        var t = 0L
        val out = mutableListOf<ScratchDetector.Step>()
        repeat(count) { k ->
            val sign = if (k % 2 == 0) 1f else -1f
            repeat(steps) {
                t += stepMs * ms
                out += detector.feed(sign * px / steps, t)
            }
        }
        return out
    }

    @Test
    fun `back and forth quickly and widely is a scratch`() {
        val steps = swings(ScratchDetector(minSwingPx = 60f), count = 6, px = 120f, stepMs = 20)
        assertEquals(ScratchDetector.Step.SCRATCH, steps.last { it != ScratchDetector.Step.NONE })
        assertEquals(1 + 1 + 1, steps.count { it == ScratchDetector.Step.TURN })
    }

    @Test
    fun `small trembling swings never count`() {
        val steps = swings(ScratchDetector(minSwingPx = 60f), count = 20, px = 20f, stepMs = 20)
        assertEquals(0, steps.count { it != ScratchDetector.Step.NONE })
    }

    @Test
    fun `slow looking around is not a scratch`() {
        // Each swing takes 800 ms: the turns never gather within the window.
        val steps = swings(ScratchDetector(minSwingPx = 60f), count = 8, px = 300f, stepMs = 200)
        assertEquals(0, steps.count { it == ScratchDetector.Step.SCRATCH })
    }

    @Test
    fun `dragging one way only is never a scratch`() {
        val detector = ScratchDetector(minSwingPx = 10f)
        val steps = (1..50).map { detector.feed(40f, it * 10 * ms) }
        assertEquals(0, steps.count { it != ScratchDetector.Step.NONE })
    }

    @Test
    fun `a pause starts it over`() {
        val detector = ScratchDetector(minSwingPx = 60f)
        swings(detector, count = 4, px = 120f, stepMs = 20)
        // Long after: the turns before do not count any more.
        assertEquals(ScratchDetector.Step.NONE, detector.feed(100f, 10_000 * ms))
        assertEquals(ScratchDetector.Step.TURN, detector.feed(-100f, 10_020 * ms))
    }
}
