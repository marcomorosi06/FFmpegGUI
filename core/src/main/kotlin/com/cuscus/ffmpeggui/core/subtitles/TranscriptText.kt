package com.cuscus.ffmpeggui.core.subtitles

import java.util.Locale

/**
 * What is said, as plain text (the .txt export): paragraphs, each starting
 * with the time it is said at, "[m:ss]". A paragraph ends at a long pause, or
 * at the end of a sentence once it is long.
 */
object TranscriptText {

    /** [words] in order, on the clock of the exported file once divided by [speed]. */
    fun write(words: List<SpokenWord>, speed: Float = 1f): String {
        if (words.isEmpty()) return ""
        val factor = if (speed > 0f) speed else 1f
        val out = StringBuilder()
        var paragraph = mutableListOf<SpokenWord>()
        fun flush() {
            if (paragraph.isEmpty()) return
            if (out.isNotEmpty()) out.append("\n\n")
            out.append('[').append(time((paragraph.first().startMs / factor).toLong())).append("] ")
            out.append(paragraph.joinToString(" ") { it.text })
            paragraph = mutableListOf()
        }
        var previous: SpokenWord? = null
        for (word in words) {
            val last = previous
            if (last != null && paragraph.isNotEmpty() && (
                    word.startMs - last.endMs >= PAUSE_MS ||
                        (paragraph.size >= LONG_PARAGRAPH && last.text.trimEnd().lastOrNull() in SENTENCE_ENDS)
                    )
            ) {
                flush()
            }
            paragraph += word
            previous = word
        }
        flush()
        return out.append('\n').toString()
    }

    private fun time(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000
        return if (seconds >= 3600) {
            String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
        } else {
            String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        }
    }

    /** A silence this long starts a new paragraph. */
    private const val PAUSE_MS = 1_500L

    /** Words after which a paragraph ends at the next full stop. */
    private const val LONG_PARAGRAPH = 60

    private val SENTENCE_ENDS = setOf('.', '?', '!')
}
