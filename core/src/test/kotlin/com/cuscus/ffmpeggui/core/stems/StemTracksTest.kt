package com.cuscus.ffmpeggui.core.stems

import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.timeline.Clip
import org.junit.Assert.assertEquals
import org.junit.Test

class StemTracksTest {

    @Test
    fun `each clip's stems start where it plays, from the part of its file it plays`() {
        val clips = listOf(
            Clip("a", "s1", startMs = 1_000, endMs = 5_000),
            Clip("b", "s2", startMs = 2_000, endMs = 6_000, effects = Effects(speed = 2f, volume = 0.5f, voiceLevel = 0.3f)),
            Clip("c", "s1", startMs = 0, endMs = 3_000),
        )
        val placement = StemTracks.place(clips, Effects(), 0L, wanted = { true }, fileDurationMs = { 10_000L })
        assertEquals(0, placement.skipped)
        val (a, b, c) = placement.placed
        assertEquals(0L, a.span.startMs)
        assertEquals(1_000L, a.span.trimStartMs)
        assertEquals(5_000L, a.span.trimEndMs)
        assertEquals(4_000L, a.span.lengthMs)
        // b plays twice as fast: 4 s of its file in 2 s, from 4 s on.
        assertEquals(4_000L, b.span.startMs)
        assertEquals(2f, b.span.rate, 0f)
        assertEquals(2_000L, b.span.lengthMs)
        assertEquals(0.5f, b.volume, 0f)
        assertEquals(0.3f, b.voiceLevel, 0f)
        assertEquals(6_000L, c.span.startMs)
        assertEquals(10_000L, c.span.fileDurationMs)
    }

    @Test
    fun `the whole video's speed and trim move them, and what cannot follow is left`() {
        val clips = listOf(
            Clip("a", "s1", startMs = 0, endMs = 4_000),
            Clip("b", "s1", startMs = 0, endMs = 4_000, effects = Effects(reversed = true)),
            Clip("c", "s1", startMs = 0, endMs = null),
            Clip("d", "s1", startMs = 0, endMs = 2_000),
        )
        val placement = StemTracks.place(clips, Effects(speed = 2f), 1_000L, wanted = { it.id != "d" }, fileDurationMs = { 0L })
        assertEquals(2, placement.skipped)
        val a = placement.placed.single()
        assertEquals(0L, a.span.startMs)
        assertEquals(2f, a.span.rate, 0f)
        assertEquals(4_000L, a.span.fileDurationMs)
        val back = StemTracks.place(clips.take(1), Effects(reversed = true), 0L, wanted = { true }, fileDurationMs = { 0L })
        assertEquals(1, back.skipped)
    }
}
