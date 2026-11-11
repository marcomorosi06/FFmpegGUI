package com.cuscus.ffmpeggui.core.subtitles

/**
 * Lyrics as lines to time by hand (issue #16, the "synced" page): each line
 * with the time it is sung at, or none yet, read from and written back to the
 * lyrics tag the LRC way ("[mm:ss.xx]words"). What else an LRC file may hold
 * at its top ("[ar:Artist]", "[offset:+100]"...) is kept as it was.
 */
object LrcLines {

    /** A line of the lyrics: when it is sung ([timeMs], null: not timed yet) and its words. */
    data class Line(val timeMs: Long?, val text: String)

    /** Lyrics read: the [header] lines kept as they were, then the [lines]. */
    data class Lyrics(val header: List<String>, val lines: List<Line>)

    /**
     * [text] read: a line starting with one time or more ("[01:02.50]") is
     * timed by its first one (the words after the last); an "[xx:...]" tag
     * that is not a time, before any words, is header; any other line is a
     * line not timed yet. A line timed several times stays one line.
     */
    fun parse(text: String): Lyrics {
        val header = ArrayList<String>()
        val lines = ArrayList<Line>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trimEnd()
            var rest = line.trimStart()
            var time: Long? = null
            while (true) {
                val match = TIME.find(rest) ?: break
                if (match.range.first != 0) break
                if (time == null) time = timeOf(match)
                rest = rest.substring(match.range.last + 1)
            }
            when {
                time != null -> lines += Line(time, rest.trim())
                lines.isEmpty() && TAG.matches(line.trim()) -> header += line.trim()
                else -> lines += Line(null, line.trim())
            }
        }
        // The text's last newline is not a line of its own.
        while (lines.isNotEmpty() && lines.last().timeMs == null && lines.last().text.isEmpty()) lines.removeAt(lines.lastIndex)
        return Lyrics(header, lines)
    }

    /** Plain [text] (one line each) as lines not timed yet. */
    fun fromPlain(text: String): List<Line> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { Line(null, it.trim()) }.toMutableList()
        while (lines.isNotEmpty() && lines.last().text.isEmpty()) lines.removeAt(lines.lastIndex)
        return lines
    }

    /** [lines] as plain text, their times left out. */
    fun plain(lines: List<Line>): String = lines.joinToString("\n") { it.text }

    /**
     * [lyrics] written back: its header, then each line, timed ones with their
     * time first ([LyricsText.stamp]); a line not timed is written as words only.
     */
    fun format(lyrics: Lyrics): String =
        (lyrics.header + lyrics.lines.map { line -> line.timeMs?.let { LyricsText.stamp(it) + line.text } ?: line.text })
            .joinToString("\n")

    /**
     * The words keep the times they had where they still are: [text], edited
     * as plain lines, laid back on [before], each line keeping the time of the
     * line at its place if the words there are the same, else of the line with
     * the same words not used yet, else none.
     */
    fun retimed(before: List<Line>, text: String): List<Line> {
        val used = BooleanArray(before.size)
        return fromPlain(text).mapIndexed { index, line ->
            val same = before.getOrNull(index)?.takeIf { it.text == line.text && !used[index] }?.let { index }
                ?: before.indices.firstOrNull { !used[it] && before[it].text == line.text }
            if (same != null) {
                used[same] = true
                line.copy(timeMs = before[same].timeMs)
            } else {
                line
            }
        }
    }

    /**
     * The line sung at [positionMs]: the last one timed at or before it, among
     * lines whose times go up; -1 before the first.
     */
    fun lineAt(lines: List<Line>, positionMs: Long): Int {
        var found = -1
        var latest = Long.MIN_VALUE
        lines.forEachIndexed { index, line ->
            val time = line.timeMs ?: return@forEachIndexed
            if (time <= positionMs && time >= latest) {
                found = index
                latest = time
            }
        }
        return found
    }

    private fun timeOf(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val ms = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return (minutes * 60 + seconds) * 1000 + ms
    }

    private val TIME = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val TAG = Regex("""\[[A-Za-z#]+:.*]""")
}
