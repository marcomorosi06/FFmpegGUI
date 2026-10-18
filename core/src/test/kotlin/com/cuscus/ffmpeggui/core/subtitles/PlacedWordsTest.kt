package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/** The words of a sound placed on the video, where they are said. */
class PlacedWordsTest {

    private val words = listOf(SpokenWord(1_000, 1_500, "one"), SpokenWord(3_000, 3_400, "two"), SpokenWord(9_000, 9_500, "three"))

    @Test
    fun `a sound placed later says its words later`() {
        val span = SoundSpan(startMs = 10_000, trimStartMs = 0, trimEndMs = 10_000, fileDurationMs = 10_000)
        assertEquals(listOf(11_000L, 13_000L, 19_000L), placedWords(words, span, 1f).map { it.startMs })
    }

    @Test
    fun `only the part played is said, as many times as it plays, at its speed`() {
        val span = SoundSpan(startMs = 0, trimStartMs = 2_000, trimEndMs = 4_000, fileDurationMs = 10_000, speed = 2f, repeats = 2)
        val placed = placedWords(words, span, 1f)
        // "two" at 1 s into the part: 0.5 s at twice the speed, then again one part (1 s) later.
        assertEquals(listOf("two", "two"), placed.map { it.text })
        assertEquals(listOf(500L, 1_500L), placed.map { it.startMs })
    }

    @Test
    fun `a faster whole video puts the words further along the sequence`() {
        val span = SoundSpan(startMs = 1_000, trimStartMs = 0, trimEndMs = 10_000, fileDurationMs = 10_000)
        // Two seconds of output are four of the sequence.
        assertEquals(4_000L, placedWords(words, span, 2f).first().startMs)
    }
}
