package com.cuscus.ffmpeggui.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameStepTest {

    // 25 fps: a frame every 40 ms.
    private val one = listOf(StepClip(fileStartMs = 0L, lengthMs = 1_000L, frameRate = 25f))

    @Test
    fun `a step lands in the middle of the next or previous frame`() {
        // At 100 ms the frame shown is the third (80..120): the next is 120..160, the previous 40..80.
        assertEquals(140L, FrameStep.target(one, 100L, forward = true))
        assertEquals(60L, FrameStep.target(one, 100L, forward = false))
        // From the middle of a frame, steps go frame by frame.
        assertEquals(180L, FrameStep.target(one, 140L, forward = true))
        assertEquals(100L, FrameStep.target(one, 140L, forward = false))
    }

    @Test
    fun `a frame's own start counts as that frame`() {
        // 120 ms is the start of the fourth frame (120..160): the next is 160..200, the previous 80..120.
        assertEquals(180L, FrameStep.target(one, 120L, forward = true))
        assertEquals(100L, FrameStep.target(one, 120L, forward = false))
    }

    @Test
    fun `nothing past either end of the sequence`() {
        assertNull(FrameStep.target(one, 10L, forward = false))
        assertNull(FrameStep.target(one, 990L, forward = true))
        assertNull(FrameStep.target(emptyList(), 0L, forward = true))
    }

    @Test
    fun `frames are those of each clip's own file`() {
        // The clip starts 1010 ms into its file at 50 fps (a frame every 20 ms): the frame
        // shown at its start is 1000..1020, the next one 1020..1040, 20 ms into the clip.
        val clips = listOf(StepClip(fileStartMs = 1_010L, lengthMs = 500L, frameRate = 50f))
        assertEquals(20L, FrameStep.target(clips, 0L, forward = true))
    }

    @Test
    fun `steps cross from one clip into the next and back`() {
        val clips = listOf(
            StepClip(fileStartMs = 0L, lengthMs = 100L, frameRate = 25f),
            StepClip(fileStartMs = 5_000L, lengthMs = 400L, frameRate = 50f),
        )
        // The last frame of the first clip (80..120 of its file, cut at 100), then the first of
        // the second (5000..5020 of its file), 10 ms into it.
        assertEquals(110L, FrameStep.target(clips, 90L, forward = true))
        // Back from the first frame of the second clip: the last frame of the first.
        assertEquals(90L, FrameStep.target(clips, 105L, forward = false))
    }

    @Test
    fun `an unknown rate counts thirty frames a second`() {
        val clips = listOf(StepClip(fileStartMs = 0L, lengthMs = 3_000L, frameRate = 0f))
        assertEquals(1000.0 / 30.0, FrameStep.frameMs(clips.first()), 1e-9)
        assertEquals(50L, FrameStep.target(clips, 0L, forward = true))
    }
}
