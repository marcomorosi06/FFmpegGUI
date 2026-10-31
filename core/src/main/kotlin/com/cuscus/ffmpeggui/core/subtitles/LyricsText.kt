package com.cuscus.ffmpeggui.core.subtitles

import java.util.Locale

/**
 * The words of a song out of the words heard in it, for its lyrics tag
 * ([com.cuscus.ffmpeggui.core.model.MetadataTags.lyrics]): short lines, as
 * lyrics are written, and a blank line between verses.
 *
 * A line ends at a pause, at the end of a sentence, after a comma once it is
 * half full, or when it is full; a long silence ends the verse. Synced, each
 * line starts with the time it is sung at the LRC way ("[mm:ss.xx]", in
 * hundredths), and a verse ends with an empty timed line where the singing
 * stops, so that a player clears the line while nothing is sung.
 */
object LyricsText {

    /** [words] in order (on the exported file's clock once divided by [speed]) as plain lines. */
    fun plain(words: List<SpokenWord>): String =
        verses(words).joinToString("\n\n") { verse -> verse.joinToString("\n") { line -> line.joinToString(" ") { it.text.trim() } } }

    /** [words] in order, timed for the exported file (its speed [speed]), as LRC lines. */
    fun synced(words: List<SpokenWord>, speed: Float = 1f): String {
        val factor = if (speed > 0f) speed else 1f
        val out = StringBuilder()
        val all = verses(words)
        all.forEachIndexed { index, verse ->
            for (line in verse) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(stamp((line.first().startMs / factor).toLong())).append(line.joinToString(" ") { it.text.trim() })
            }
            // Where the verse's singing stops, an empty line (not after the last one).
            if (index < all.lastIndex) out.append('\n').append(stamp((verse.last().last().endMs / factor).toLong()))
        }
        return out.toString()
    }

    /** True when [text] is synced lyrics: its lines (most of them) start with an LRC time. */
    fun isSynced(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return false
        return lines.count { TIME.containsMatchIn(it) } * 2 > lines.size
    }

    /** The verses of [words]: lists of lines, each a list of words. */
    private fun verses(words: List<SpokenWord>): List<List<List<SpokenWord>>> {
        val verses = mutableListOf<MutableList<List<SpokenWord>>>()
        var verse = mutableListOf<List<SpokenWord>>()
        var line = mutableListOf<SpokenWord>()
        var length = 0
        var previous: SpokenWord? = null
        for (word in words.filter { it.text.isNotBlank() }) {
            val last = previous
            if (last != null) {
                val gap = word.startMs - last.endMs
                val end = last.text.trimEnd().lastOrNull()
                val newVerse = gap >= VERSE_PAUSE_MS
                val newLine = newVerse || gap >= LINE_PAUSE_MS || end in SENTENCE_ENDS ||
                    (end in CLAUSE_ENDS && length >= LINE_CHARS / 2) || length + 1 + word.text.trim().length > LINE_CHARS
                if (newLine && line.isNotEmpty()) {
                    verse += line
                    line = mutableListOf()
                    length = 0
                }
                if (newVerse && verse.isNotEmpty()) {
                    verses += verse
                    verse = mutableListOf()
                }
            }
            length += (if (line.isEmpty()) 0 else 1) + word.text.trim().length
            line += word
            previous = word
        }
        if (line.isNotEmpty()) verse += line
        if (verse.isNotEmpty()) verses += verse
        return verses
    }

    /** "[mm:ss.xx]" for [ms] (minutes past 99 are written in full). */
    fun stamp(ms: Long): String {
        val hundredths = ms.coerceAtLeast(0L) / 10
        return String.format(Locale.ROOT, "[%02d:%02d.%02d]", hundredths / 6000, (hundredths / 100) % 60, hundredths % 100)
    }

    /** A pause this long ends a line. */
    private const val LINE_PAUSE_MS = 700L

    /** A silence this long ends a verse. */
    private const val VERSE_PAUSE_MS = 3_000L

    /** Characters a line holds at most (one word longer than that keeps a line of its own). */
    private const val LINE_CHARS = 42

    private val SENTENCE_ENDS = setOf('.', '?', '!')
    private val CLAUSE_ENDS = setOf(',', ';', ':')
    private val TIME = Regex("""^\[\d{1,3}:\d{2}([.:]\d{1,3})?]""")
}
