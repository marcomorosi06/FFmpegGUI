package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.transitionLengths
import java.util.Locale

/** One timed line of text. The clock it refers to depends on where it comes from. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/** Text to show at [timeMs]; overlapping cues are stacked. Null when nothing is on screen. */
fun List<SubtitleCue>.textAt(timeMs: Long): String? =
    filter { timeMs >= it.startMs && timeMs < it.endMs }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("\n") { it.text }

/**
 * Minimal SubRip (.srt) and SubStation Alpha (.ass / .ssa) reader for the live
 * preview. It extracts timing and plain text only: styling, positioning and
 * effects are left to libass at export time.
 */
class SubtitleParser {

    fun parse(content: String): List<SubtitleCue> {
        val text = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val cues = if (looksLikeAss(text)) parseAss(text) else parseSrt(text)
        return cues.filter { it.endMs > it.startMs && it.text.isNotBlank() }.sortedBy { it.startMs }
    }

    private fun looksLikeAss(text: String): Boolean =
        text.lineSequence().any { it.startsWith("Dialogue:") || it.trim().equals("[Script Info]", ignoreCase = true) }

    // -- SubRip --------------------------------------------------------------

    private fun parseSrt(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = text.split('\n')
        var i = 0
        while (i < lines.size) {
            val timing = SRT_TIMING.find(lines[i])
            if (timing == null) {
                i++
                continue
            }
            val start = srtTime(timing.groupValues[1])
            val end = srtTime(timing.groupValues[2])
            i++
            val body = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                body += lines[i]
                i++
            }
            cues += SubtitleCue(start, end, stripMarkup(body.joinToString("\n")))
        }
        return cues
    }

    /** "HH:MM:SS,mmm", also accepting "." and a missing hour field. */
    private fun srtTime(value: String): Long {
        val (clock, fraction) = value.replace(',', '.').split('.').let { it[0] to it.getOrElse(1) { "0" } }
        val fields = clock.split(':').map { it.trim().toLongOrNull() ?: 0L }
        val seconds = fields.fold(0L) { acc, field -> acc * 60 + field }
        return seconds * 1000 + fraction.padEnd(3, '0').take(3).toLong()
    }

    // -- SubStation Alpha ----------------------------------------------------

    private fun parseAss(text: String): List<SubtitleCue> {
        var startIndex = 1
        var endIndex = 2
        var textIndex = 9
        val cues = mutableListOf<SubtitleCue>()
        var inEvents = false

        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[") -> inEvents = trimmed.equals("[Events]", ignoreCase = true)
                inEvents && trimmed.startsWith("Format:") -> {
                    val fields = trimmed.removePrefix("Format:").split(',').map { it.trim().lowercase() }
                    startIndex = fields.indexOf("start").takeIf { it >= 0 } ?: startIndex
                    endIndex = fields.indexOf("end").takeIf { it >= 0 } ?: endIndex
                    textIndex = fields.indexOf("text").takeIf { it >= 0 } ?: textIndex
                }
                trimmed.startsWith("Dialogue:") -> {
                    // The text is the last field and may itself contain commas.
                    val fields = trimmed.removePrefix("Dialogue:").split(',', limit = textIndex + 1)
                    if (fields.size > maxOf(startIndex, endIndex, textIndex)) {
                        cues += SubtitleCue(
                            startMs = assTime(fields[startIndex]),
                            endMs = assTime(fields[endIndex]),
                            text = stripMarkup(fields[textIndex]),
                        )
                    }
                }
            }
        }
        return cues
    }

    /** "H:MM:SS.cc" (centiseconds). */
    private fun assTime(value: String): Long {
        val (clock, fraction) = value.trim().split('.').let { it[0] to it.getOrElse(1) { "0" } }
        val fields = clock.split(':').map { it.trim().toLongOrNull() ?: 0L }
        val seconds = fields.fold(0L) { acc, field -> acc * 60 + field }
        return seconds * 1000 + fraction.padEnd(3, '0').take(3).toLong()
    }

    /** Drops {\override} blocks and <html-like> tags, and resolves ASS line breaks. */
    private fun stripMarkup(raw: String): String =
        raw.replace(ASS_OVERRIDE, "")
            .replace(HTML_TAG, "")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()

    private companion object {
        val SRT_TIMING = Regex("""(\d{1,2}(?::\d{1,2}){1,2}[,.]\d{1,3})\s*-->\s*(\d{1,2}(?::\d{1,2}){1,2}[,.]\d{1,3})""")
        val ASS_OVERRIDE = Regex("""\{[^}]*\}""")
        val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")
    }
}

// ---------------------------------------------------------------------------
// Following the footage through the edit
// ---------------------------------------------------------------------------

/**
 * Where the stretch [startMs]..[endMs] of source [sourceIndex] ends up in the
 * joined video: one range per segment that shows part of it. Each segment's own
 * speed and direction are taken into account. With [reversed] (the whole video
 * plays backwards) the ranges are mirrored.
 */
fun mapToTimeline(
    startMs: Long,
    endMs: Long,
    segments: List<Segment>,
    sourceIndex: Int,
    reversed: Boolean = false,
): List<Pair<Long, Long>> {
    val ranges = mutableListOf<Pair<Long, Long>>()
    var segmentStart = 0.0
    var lengthKnown = true
    // Two clips joined by a transition overlap for its length.
    val overlaps = transitionLengths(
        segments.map { it.effects.transition },
        segments.map { s -> s.durationMs?.let { s.effects.playedMs(it) } },
    )
    for ((index, segment) in segments.withIndex()) {
        val length = segment.durationMs
        val end = segment.endMs
        val effects = segment.effects
        val footage = (length ?: 0L).toDouble()
        // Played time of the first [consumed] ms of footage (in playing order); open-ended: the average speed.
        fun played(consumed: Long): Double =
            if (length != null) effects.playedOffsetMs(footage, consumed.toDouble()) else consumed / effects.effectiveSpeed.toDouble()
        val backwards = segment.effects.reversed
        if (segment.source == sourceIndex && (end != null || !backwards)) {
            val visibleFrom = maxOf(startMs, segment.startMs)
            val visibleTo = if (end == null) endMs else minOf(endMs, end)
            if (visibleTo > visibleFrom) {
                // Offsets inside the segment, on the joined clock.
                val (from, to) = if (backwards && end != null) {
                    played(end - visibleTo) to played(end - visibleFrom)
                } else {
                    played(visibleFrom - segment.startMs) to played(visibleTo - segment.startMs)
                }
                ranges += Math.round(segmentStart + from) to Math.round(segmentStart + to)
            }
        }
        if (length == null) {
            lengthKnown = false
            break // nothing after an open-ended segment can be placed
        }
        segmentStart += effects.playedMs(length.toDouble()) - (overlaps.getOrNull(index) ?: 0L)
    }
    if (!reversed || !lengthKnown) return ranges.sortedBy { it.first }
    val total = Math.round(segmentStart)
    return ranges.map { (from, to) -> (total - to) to (total - from) }.sortedBy { it.first }
}

/** Cues timed against source [sourceIndex], moved onto the edited sequence. */
fun List<SubtitleCue>.onTimeline(
    segments: List<Segment>,
    sourceIndex: Int,
    reversed: Boolean = false,
): List<SubtitleCue> =
    flatMap { cue ->
        mapToTimeline(cue.startMs, cue.endMs, segments, sourceIndex, reversed)
            .map { (from, to) -> SubtitleCue(from, to, cue.text) }
    }.sortedBy { it.startMs }

/** A subtitle file rewritten for the edited sequence. */
data class RetimedSubtitles(val content: String, val extension: String)

/**
 * Rewrites a subtitle file so that every line follows its footage through
 * trims, cuts, reordering and reversal. FFmpeg then burns the result as is.
 *
 * SubStation Alpha files keep their styles (only the Dialogue timings change);
 * anything else is written back as SubRip.
 */
class SubtitleRetimer(private val parser: SubtitleParser = SubtitleParser()) {

    /** Null when no line is left on screen. */
    fun retime(
        content: String,
        segments: List<Segment>,
        sourceIndex: Int,
        reversed: Boolean = false,
    ): RetimedSubtitles? {
        val text = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        return if (isAss(text)) retimeAss(text, segments, sourceIndex, reversed) else retimeSrt(text, segments, sourceIndex, reversed)
    }

    companion object {
        /** True for a SubStation Alpha script (it has its own look), false for SubRip and the like. */
        fun isAss(content: String): Boolean = content.lineSequence().any { it.trimStart().startsWith("Dialogue:") }
    }

    private fun retimeSrt(text: String, segments: List<Segment>, sourceIndex: Int, reversed: Boolean): RetimedSubtitles? {
        val cues = parser.parse(text).onTimeline(segments, sourceIndex, reversed)
        if (cues.isEmpty()) return null
        val srt = buildString {
            cues.forEachIndexed { index, cue ->
                append(index + 1).append('\n')
                append(srtTime(cue.startMs)).append(" --> ").append(srtTime(cue.endMs)).append('\n')
                append(cue.text).append("\n\n")
            }
        }
        return RetimedSubtitles(srt, "srt")
    }

    private fun retimeAss(text: String, segments: List<Segment>, sourceIndex: Int, reversed: Boolean): RetimedSubtitles? {
        var startIndex = 1
        var endIndex = 2
        var fieldCount = 10
        var inEvents = false
        var kept = 0
        val output = StringBuilder()

        for (line in text.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) inEvents = trimmed.equals("[Events]", ignoreCase = true)
            if (inEvents && trimmed.startsWith("Format:")) {
                val fields = trimmed.removePrefix("Format:").split(',').map { it.trim().lowercase() }
                startIndex = fields.indexOf("start").takeIf { it >= 0 } ?: startIndex
                endIndex = fields.indexOf("end").takeIf { it >= 0 } ?: endIndex
                fieldCount = fields.size
            }
            if (!trimmed.startsWith("Dialogue:")) {
                output.append(line).append('\n')
                continue
            }
            // The last field is the text and may contain commas itself.
            val fields = trimmed.removePrefix("Dialogue:").split(',', limit = fieldCount).toMutableList()
            if (fields.size <= maxOf(startIndex, endIndex)) continue
            val ranges = mapToTimeline(assTime(fields[startIndex]), assTime(fields[endIndex]), segments, sourceIndex, reversed)
            for ((from, to) in ranges) {
                fields[startIndex] = formatAssTime(from)
                fields[endIndex] = formatAssTime(to)
                output.append("Dialogue:").append(fields.joinToString(",")).append('\n')
                kept++
            }
        }
        return if (kept == 0) null else RetimedSubtitles(output.toString(), "ass")
    }

    private fun assTime(value: String): Long {
        val (clock, fraction) = value.trim().split('.').let { it[0] to it.getOrElse(1) { "0" } }
        val seconds = clock.split(':').fold(0L) { acc, field -> acc * 60 + (field.trim().toLongOrNull() ?: 0L) }
        return seconds * 1000 + fraction.padEnd(3, '0').take(3).toLong()
    }

    private fun formatAssTime(ms: Long): String {
        val centis = ms.coerceAtLeast(0L) / 10
        return String.format(
            Locale.US, "%d:%02d:%02d.%02d",
            centis / 360_000, (centis / 6_000) % 60, (centis / 100) % 60, centis % 100,
        )
    }

    private fun srtTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        return String.format(
            Locale.US, "%02d:%02d:%02d,%03d",
            safe / 3_600_000, (safe / 60_000) % 60, (safe / 1_000) % 60, safe % 1_000,
        )
    }
}
