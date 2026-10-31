package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTextTest {

    /** Words one after the other, [gapMs] apart unless a word says otherwise. */
    private fun sung(vararg parts: Pair<String, Long>): List<SpokenWord> {
        var at = 0L
        return parts.map { (text, before) ->
            at += before
            SpokenWord(at, at + 300, text).also { at += 300 }
        }
    }

    private val song = sung(
        "Hello" to 1_000L, "darkness," to 50L, "my" to 50L, "old" to 50L, "friend." to 50L,
        "I've" to 200L, "come" to 50L, "to" to 50L, "talk" to 50L,
        "again" to 800L,
        "Because" to 4_000L, "a" to 50L, "vision" to 50L,
    )

    @Test
    fun `plain lyrics are lines and verses`() {
        assertEquals("Hello darkness, my old friend.\nI've come to talk\nagain\n\nBecause a vision", LyricsText.plain(song))
    }

    @Test
    fun `synced lyrics carry the time of every line and an empty line between verses`() {
        val lrc = LyricsText.synced(song)
        val lines = lrc.lines()
        assertEquals("[00:01.00]Hello darkness, my old friend.", lines[0])
        assertTrue(lines[1], lines[1].endsWith("]I've come to talk"))
        assertTrue(lines[2], lines[2].endsWith("]again"))
        // The verse stops when "again" ends.
        assertTrue(lines[3], lines[3].matches(Regex("""\[\d{2}:\d{2}\.\d{2}]""")))
        assertTrue(lines[4], lines[4].endsWith("]Because a vision"))
        assertEquals(5, lines.size)
        assertTrue(LyricsText.isSynced(lrc))
        assertFalse(LyricsText.isSynced(LyricsText.plain(song)))
    }

    @Test
    fun `synced times follow the export's speed`() {
        val lrc = LyricsText.synced(listOf(SpokenWord(4_000, 4_500, "la")), speed = 2f)
        assertEquals("[00:02.00]la", lrc)
    }

    @Test
    fun `stamps are minutes, seconds and hundredths`() {
        assertEquals("[00:00.00]", LyricsText.stamp(0))
        assertEquals("[01:05.43]", LyricsText.stamp(65_437))
        assertEquals("[125:00.00]", LyricsText.stamp(125 * 60_000L))
    }

    @Test
    fun `a full line breaks before the next word`() {
        val many = (1..20).map { SpokenWord(it * 400L, it * 400L + 300, "word$it") }
        LyricsText.plain(many).lines().forEach { assertTrue(it, it.length <= 42) }
    }

    @Test
    fun `album artist and lyrics are written and saved`() {
        val tags = MetadataTags(albumArtist = "Various", lyrics = "[00:01.00]la")
        assertEquals(listOf("album_artist" to "Various", "lyrics" to "[00:01.00]la"), tags.asMap().toList())
        assertEquals(tags, ModelJson.metadataTags(ModelJson.write(tags)))
    }
}
