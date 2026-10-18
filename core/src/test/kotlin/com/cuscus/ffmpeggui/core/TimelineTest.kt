package com.cuscus.ffmpeggui.core

import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.Timeline
import com.cuscus.ffmpeggui.core.timeline.Timeline.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    private val a = Clip("a", "main", 0, 10_000)
    private val b = Clip("b", "extra", 2_000, 5_000)
    private val timeline = Timeline(listOf(a, b))

    @Test
    fun `duration and positions`() {
        assertEquals(13_000L, timeline.durationMs)
        assertEquals(10_000L, timeline.startOf(1))
        assertEquals(Position(0, 9_999), timeline.locate(9_999))
        assertEquals(Position(1, 0), timeline.locate(10_000))
        assertEquals(Position(1, 3_000), timeline.locate(99_000))
        assertNull(Timeline().locate(0))
    }

    @Test
    fun `split cuts one clip in two on the source clock`() {
        val split = timeline.splitAt(11_000, "c")
        assertEquals(
            listOf(a, Clip("b", "extra", 2_000, 3_000), Clip("c", "extra", 3_000, 5_000)),
            split.clips,
        )
        assertEquals(timeline.durationMs, split.durationMs)
    }

    @Test
    fun `split is refused on or next to a cut`() {
        assertFalse(timeline.canSplitAt(10_000))
        assertFalse(timeline.canSplitAt(50))
        assertFalse(timeline.canSplitAt(12_950))
        assertTrue(timeline.canSplitAt(5_000))
        assertSame(timeline, timeline.splitAt(10_000, "c"))
    }

    @Test
    fun `remove keeps at least one clip`() {
        assertEquals(listOf(b), timeline.remove("a").clips)
        val single = Timeline(listOf(a))
        assertSame(single, single.remove("a"))
        assertSame(timeline, timeline.remove("missing"))
    }

    @Test
    fun `duplicate inserts the copy after the original`() {
        assertEquals(listOf(a, a.copy(id = "a2"), b), timeline.duplicate("a", "a2").clips)
    }

    @Test
    fun `move reorders within bounds`() {
        assertEquals(listOf(b, a), timeline.move("a", 1).clips)
        assertEquals(listOf(b, a), timeline.move("b", -5).clips)
        assertSame(timeline, timeline.move("a", -1))
    }

    @Test
    fun `trim stays inside the source and above the minimum length`() {
        assertEquals(Clip("b", "extra", 1_000, 6_000), timeline.trim("b", 1_000, 6_000, 8_000).clips[1])
        assertEquals(Clip("b", "extra", 0, 8_000), timeline.trim("b", -500, 99_000, 8_000).clips[1])
        assertEquals(Clip("b", "extra", 3_000, 3_100), timeline.trim("b", 3_000, 3_000, 8_000).clips[1])
        // Unknown source length: only the lower bound applies.
        assertEquals(Clip("b", "extra", 3_000, 99_000), timeline.trim("b", 3_000, 99_000, 0).clips[1])
    }

    @Test
    fun `open ended clips are closed once the source length is known`() {
        val open = Timeline(listOf(Clip("a", "main")))
        assertEquals(0L, open.durationMs)
        assertFalse(open.canSplitAt(0))
        assertEquals(listOf(Clip("a", "main", 0, 7_000)), open.closeOpenClips("main", 7_000).clips)
        assertSame(open, open.closeOpenClips("other", 7_000))
        assertSame(open, open.closeOpenClips("main", 0))
    }

    @Test
    fun `window trims the whole sequence without touching the clips`() {
        assertEquals(timeline.clips, timeline.window(0, null))
        assertEquals(
            listOf(Clip("a", "main", 4_000, 10_000), Clip("b", "extra", 2_000, 3_500)),
            timeline.window(4_000, 11_500),
        )
        // A window inside the second clip drops the first one entirely.
        assertEquals(listOf(Clip("b", "extra", 3_000, 4_000)), timeline.window(11_000, 12_000))
        assertTrue(timeline.window(20_000, null).isEmpty())
    }

    @Test
    fun `window on an open ended clip moves only the known edges`() {
        val open = Timeline(listOf(Clip("a", "main")))
        assertEquals(listOf(Clip("a", "main", 2_000, null)), open.window(2_000, null))
        assertEquals(listOf(Clip("a", "main", 2_000, 9_000)), open.window(2_000, 9_000))
    }

    @Test
    fun `split keeps the clip effects and hands each fade to its own half`() {
        val faded = Timeline(listOf(a.copy(effects = Effects(speed = 2f, fadeInSeconds = 1f, fadeOutSeconds = 2f))))
        val (first, second) = faded.splitAt(4_000, "new").clips
        assertEquals(Clip("a", "main", 0, 4_000, Effects(speed = 2f, fadeInSeconds = 1f)), first)
        assertEquals(Clip("new", "main", 4_000, 10_000, Effects(speed = 2f, fadeOutSeconds = 2f)), second)
    }

    @Test
    fun `splitting a reversed clip keeps what is seen unchanged`() {
        val backwards = Timeline(listOf(a.copy(effects = Effects(reversed = true))))
        // Backwards, 4..10 s of the file plays before 0..4 s.
        assertEquals(
            listOf(Clip("new", "main", 4_000, 10_000, Effects(reversed = true)), Clip("a", "main", 0, 4_000, Effects(reversed = true))),
            backwards.splitAt(4_000, "new").clips,
        )
    }

    @Test
    fun `effects of one clip can be changed alone`() {
        val edited = timeline.updateEffects("b") { it.copy(muted = true) }
        assertEquals(Effects(muted = true), edited.clips[1].effects)
        assertEquals(Effects.NONE, edited.clips[0].effects)
        assertSame(timeline, timeline.updateEffects("missing") { it.copy(muted = true) })
        // A duplicate takes the effects along.
        assertEquals(Effects(muted = true), edited.duplicate("b", "c").clips[2].effects)
    }

    @Test
    fun `clips after one of unknown length still play without an end trim`() {
        val sequence = Timeline(listOf(a, Clip("open", "unreadable"), b))
        assertEquals(listOf(a.copy(startMs = 1_000), Clip("open", "unreadable"), b), sequence.window(1_000, null))
        // With an end trim nothing after the unknown length can be placed.
        assertEquals(listOf(a, Clip("open", "unreadable", 0, 2_000)), sequence.window(0, 12_000))
    }

    @Test
    fun `insertAt cuts the clip under the position and puts the new clip in between`() {
        val still = Clip("s", "still", 0, 3_000)
        val result = timeline.insertAt(4_000, still, "a2")
        assertEquals(listOf("a", "s", "a2", "b"), result.clips.map { it.id })
        assertEquals(4_000L, result.clips[0].endMs)
        assertEquals(4_000L, result.clips[2].startMs)
        assertEquals(16_000L, result.durationMs)
    }

    @Test
    fun `insertAt next to a cut goes on the nearer side without cutting`() {
        val still = Clip("s", "still", 0, 3_000)
        assertEquals(listOf("a", "s", "b"), timeline.insertAt(10_000, still, "x").clips.map { it.id })
        assertEquals(listOf("s", "a", "b"), timeline.insertAt(50, still, "x").clips.map { it.id })
        assertEquals(listOf("a", "b", "s"), timeline.insertAt(12_990, still, "x").clips.map { it.id })
    }

    @Test
    fun `a still clip is trimmed without a source length`() {
        val still = Timeline(listOf(Clip("s", "still", 0, 3_000)))
        assertEquals(7_500L, still.trim("s", 0, 7_500, 0).durationMs)
    }
}
