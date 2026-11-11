package com.cuscus.ffmpeggui.core.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcLinesTest {

    @Test
    fun `timed lines, lines not timed yet and the header are read`() {
        val lyrics = LrcLines.parse("[ar:Someone]\n[00:33.61]Jeg ved ikke\nNot timed\n[01:02.5]Two\n[1:04.760]Three\n")
        assertEquals(listOf("[ar:Someone]"), lyrics.header)
        assertEquals(
            listOf(
                LrcLines.Line(33_610L, "Jeg ved ikke"),
                LrcLines.Line(null, "Not timed"),
                LrcLines.Line(62_500L, "Two"),
                LrcLines.Line(64_760L, "Three"),
            ),
            lyrics.lines,
        )
    }

    @Test
    fun `a line timed twice is one line, at its first time`() {
        val lyrics = LrcLines.parse("[00:10.00][00:50.00]Chorus")
        assertEquals(listOf(LrcLines.Line(10_000L, "Chorus")), lyrics.lines)
    }

    @Test
    fun `written back the LRC way, untimed lines as words`() {
        val lyrics = LrcLines.Lyrics(listOf("[ti:Song]"), listOf(LrcLines.Line(1_230L, "One"), LrcLines.Line(null, "Two"), LrcLines.Line(61_000L, "")))
        assertEquals("[ti:Song]\n[00:01.23]One\nTwo\n[01:01.00]", LrcLines.format(lyrics))
        assertEquals(lyrics, LrcLines.parse(LrcLines.format(lyrics)))
    }

    @Test
    fun `plain lines become lines to time, and back`() {
        val lines = LrcLines.fromPlain("A\nB\n\nC\n")
        assertEquals(listOf("A", "B", "", "C"), lines.map { it.text })
        assertEquals("A\nB\n\nC", LrcLines.plain(lines))
        lines.forEach { assertNull(it.timeMs) }
    }

    @Test
    fun `edited as text, lines keep their times`() {
        val before = listOf(LrcLines.Line(1_000L, "A"), LrcLines.Line(2_000L, "B"), LrcLines.Line(3_000L, "C"))
        // B removed, D added, C kept by its words.
        val after = LrcLines.retimed(before, "A\nC\nD")
        assertEquals(listOf(LrcLines.Line(1_000L, "A"), LrcLines.Line(3_000L, "C"), LrcLines.Line(null, "D")), after)
    }

    @Test
    fun `the line sung now is the last timed before the playhead`() {
        val lines = listOf(LrcLines.Line(1_000L, "A"), LrcLines.Line(null, "x"), LrcLines.Line(5_000L, "B"))
        assertEquals(-1, LrcLines.lineAt(lines, 500L))
        assertEquals(0, LrcLines.lineAt(lines, 4_999L))
        assertEquals(2, LrcLines.lineAt(lines, 5_000L))
    }
}
