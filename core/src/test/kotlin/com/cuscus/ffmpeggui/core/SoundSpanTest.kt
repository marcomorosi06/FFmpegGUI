package com.cuscus.ffmpeggui.core

import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundSpanTest {

    // 2..8 s of a 10 s file, played from 1 s of the video.
    private val span = SoundSpan(startMs = 1_000, trimStartMs = 2_000, trimEndMs = 8_000, fileDurationMs = 10_000)

    @Test
    fun `moving keeps the length and never goes before the video`() {
        assertEquals(span.copy(startMs = 4_000), span.movedBy(3_000))
        assertEquals(span.copy(startMs = 0), span.movedBy(-5_000))
        assertEquals(7_000L, span.movedTo(1_000).endMs)
    }

    @Test
    fun `the start edge trims the beginning and leaves the rest in place`() {
        val later = span.withStartEdgeMovedBy(1_500)
        assertEquals(SoundSpan(2_500, 3_500, 8_000, 10_000), later)
        assertEquals(span.endMs, later.endMs)
        // Earlier: limited by the start of the video (1 s), before the start of the file (2 s).
        assertEquals(SoundSpan(0, 1_000, 8_000, 10_000), span.withStartEdgeMovedBy(-5_000))
        // Never shorter than the minimum.
        assertEquals(SoundSpan.MIN_SOUND_MS, span.withStartEdgeMovedBy(99_000).lengthMs)
    }

    @Test
    fun `the end edge is kept inside the file`() {
        assertEquals(10_000L, span.withEndEdgeMovedBy(5_000).trimEndMs)
        assertEquals(2_000L + SoundSpan.MIN_SOUND_MS, span.withEndEdgeMovedBy(-9_000).trimEndMs)
        assertEquals(6_500L, span.withEndEdgeMovedBy(-1_500).trimEndMs)
    }

    @Test
    fun `the file range of the panel keeps the start on the video`() {
        val trimmed = span.withFileRange(0, 12_000)
        assertEquals(SoundSpan(1_000, 0, 10_000, 10_000), trimmed)
    }

    @Test
    fun `a cut makes two halves that play on from each other`() {
        val (first, second) = requireNotNull(span.splitAt(3_000))
        assertEquals(SoundSpan(1_000, 2_000, 4_000, 10_000), first)
        assertEquals(SoundSpan(3_000, 4_000, 8_000, 10_000), second)
        assertEquals(span.endMs, second.endMs)
        assertNull(span.splitAt(1_050))
        assertNull(span.splitAt(6_950))
        assertNull(span.splitAt(9_000))
    }

    @Test
    fun `overlapping sounds get rows of their own`() {
        val spans = listOf(
            SoundSpan(0, 0, 5_000, 5_000),      // row 0
            SoundSpan(2_000, 0, 5_000, 5_000),  // overlaps the first: row 1
            SoundSpan(5_000, 0, 1_000, 5_000),  // starts where the first ends: row 0
            SoundSpan(4_000, 0, 4_000, 5_000),  // overlaps both rows: row 2
        )
        assertArrayEquals(intArrayOf(0, 1, 0, 2), SoundSpan.rows(spans))
    }

    @Test
    fun `snapping picks the nearest target in reach`() {
        assertEquals(1_050.0, SoundSpan.snap(1_030.0, listOf(0.0, 1_000.0, 1_050.0, 5_000.0), 40.0)!!, 0.0)
        assertEquals(1_000.0, SoundSpan.snap(1_010.0, listOf(0.0, 1_000.0, 1_050.0), 40.0)!!, 0.0)
        assertNull(SoundSpan.snap(2_000.0, listOf(0.0, 1_000.0), 40.0))
    }

    @Test
    fun `speed and repeats set the length on the video`() {
        val fast = span.withSpeed(2f)
        assertEquals(3_000L, fast.lengthMs)
        assertEquals(1_000L, fast.startMs)
        val thrice = span.withRepeats(3)
        assertEquals(18_000L, thrice.lengthMs)
        assertEquals(9_000L, span.withSpeed(2f).withRepeats(3).lengthMs)
    }

    @Test
    fun `edges follow the finger whatever the speed`() {
        val fast = span.withSpeed(2f) // 1..4 s on the video, 2..8 s of the file
        val later = fast.withStartEdgeMovedBy(1_000)
        assertEquals(2_000L, later.startMs)
        assertEquals(4_000L, later.trimStartMs)
        assertEquals(fast.endMs, later.endMs)
        assertEquals(10_000L, fast.withEndEdgeMovedBy(5_000).trimEndMs)
        assertEquals(7_000L, fast.withEndEdgeMovedBy(-500).trimEndMs)
    }

    @Test
    fun `a fast sound is cut where it is heard and a repeated one is not cut`() {
        val (first, second) = requireNotNull(span.withSpeed(2f).splitAt(2_000))
        assertEquals(4_000L, first.trimEndMs)
        assertEquals(2_000L, first.endMs)
        assertEquals(SoundSpan(2_000, 4_000, 8_000, 10_000, speed = 2f), second)
        assertNull(span.withRepeats(2).splitAt(3_000))
    }

    @Test
    fun `the file position under the playhead wraps with each repetition`() {
        val twice = span.withRepeats(2) // 6 s part, played from 1 s to 13 s
        assertEquals(2_000L, twice.fileMsAt(1_000))
        assertEquals(7_000L, twice.fileMsAt(6_000))
        assertEquals(3_000L, twice.fileMsAt(8_000))
        assertEquals(8_000L, twice.fileMsAt(20_000))
    }
}
