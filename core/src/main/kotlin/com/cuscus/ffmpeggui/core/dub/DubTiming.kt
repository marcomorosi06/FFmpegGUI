package com.cuscus.ffmpeggui.core.dub

/**
 * Where each line of a dub is said: always when its subtitle starts, never
 * two at once, never past the end of the video. A line may take the time
 * until the next one starts (its "slot"); a voice that needs more is first
 * said faster by the voice itself, up to [MAX_SPEED_UP], then made to fit
 * (DubFit: shorter pauses, then faster without a higher pitch): nothing
 * pushes the next lines later, so the dub never drifts from the subtitles.
 */
object DubTiming {

    /** One line to say, on the output clock. */
    data class Line(val startMs: Long, val endMs: Long, val text: String)

    /** Speed-ups past this sound hurried. */
    const val MAX_SPEED_UP = 1.5f

    /** A line this much longer than its slot is said again; less is left as it is. */
    const val TOLERANCE = 1.04f

    /** Room left between two lines said one after the other. */
    const val GAP_MS = 120L

    /** A line followed by nothing may run on this long past its end. */
    const val TAIL_MS = 1_500L

    /**
     * The time [lines] (sorted by start) may each take: up to the next line's
     * start (less a breath), or a little past its own end for the last one,
     * and never past [endMs] (the end of the video) when it is known.
     */
    fun slots(lines: List<Line>, endMs: Long? = null): List<Long> = lines.mapIndexed { index, line ->
        val own = (line.endMs - line.startMs).coerceAtLeast(0L)
        val next = lines.getOrNull(index + 1)?.startMs
        var room = if (next != null) next - line.startMs - GAP_MS else own + TAIL_MS
        if (endMs != null) room = minOf(room, endMs - line.startMs)
        maxOf(room, MIN_SLOT_MS)
    }

    /**
     * How much faster than [baseSpeed] a line that takes [naturalMs] at that
     * speed must be said to fit in [slotMs]: [baseSpeed] when it fits, at most
     * [MAX_SPEED_UP] times it.
     */
    fun speedFor(naturalMs: Long, slotMs: Long, baseSpeed: Float): Float {
        if (naturalMs <= slotMs || slotMs <= 0L) return baseSpeed
        val factor = (naturalMs.toFloat() / slotMs).coerceAtMost(MAX_SPEED_UP)
        return baseSpeed * factor
    }

    /**
     * The speed to say a line again at, when said at [usedSpeed] it took
     * [measuredMs] for a slot of [slotMs]: a little more than the ratio (the
     * pauses do not shrink with the speed), at most [MAX_SPEED_UP] times
     * [baseSpeed]; null when it fits, or cannot be made faster.
     */
    fun retrySpeed(measuredMs: Long, slotMs: Long, usedSpeed: Float, baseSpeed: Float): Float? {
        if (slotMs <= 0L || measuredMs <= slotMs * TOLERANCE) return null
        val most = baseSpeed * MAX_SPEED_UP
        if (usedSpeed >= most - 1e-3f) return null
        return (usedSpeed * measuredMs.toFloat() / slotMs * 1.06f).coerceAtMost(most)
    }

    /**
     * The starts of lines that last [durationsMs], each at its subtitle's
     * [Line.startMs] unless the one before is still being said: then right
     * after it (and [GAP_MS]).
     */
    fun place(lines: List<Line>, durationsMs: List<Long>): List<Long> {
        require(lines.size == durationsMs.size) { "One length per line" }
        var free = Long.MIN_VALUE
        return lines.mapIndexed { index, line ->
            val start = if (free == Long.MIN_VALUE) line.startMs else maxOf(line.startMs, free + GAP_MS)
            free = start + durationsMs[index]
            start
        }
    }

    /**
     * What of a subtitle line is said: its tags (SubStation Alpha's {\...},
     * HTML's <i>), line breaks and what is in square brackets (sound
     * descriptions, "[music]") taken out, and spaces made single. Empty when
     * nothing that can be said is left (a line of notes, "...").
     */
    fun spoken(text: String): String {
        val plain = text
            .replace(Regex("\\{[^}]*\\}"), " ")
            .replace(Regex("<[^>]*>"), " ")
            .replace("\\N", " ").replace("\\n", " ")
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (plain.any { it.isLetterOrDigit() }) plain else ""
    }

    /**
     * [text] as one breath: the voices cut a line into its sentences and say
     * each one on its own, its tune starting again and its end drawn out, so
     * that a line of two sentences sounds like two lines glued. Inside the
     * line the ends of sentences (. ! ? ; and their Chinese forms) become
     * commas, which the voices say as a short pause within one sentence; the
     * end of the line keeps its own (a question stays a question). A dot
     * between digits (3.5) or not followed by a space ("e.g.") is left.
     */
    fun oneBreath(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        // The end of the line: its last run of punctuation, kept as it is.
        var end = trimmed.length
        while (end > 0 && trimmed[end - 1] in ENDS) end--
        val body = trimmed.substring(0, end)
        val tail = trimmed.substring(end)
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c in ENDS) {
                // A run of them ("?!", "..."), then a space or the Chinese kind (no space needed).
                var j = i
                while (j < body.length && body[j] in ENDS) j++
                val chinese = body.substring(i, j).any { it in CHINESE_ENDS }
                val spaced = j < body.length && body[j].isWhitespace()
                if (chinese) {
                    out.append('\uFF0C')
                    i = j
                    continue
                }
                if (spaced) {
                    out.append(',')
                    i = j
                    continue
                }
                out.append(body, i, j)
                i = j
                continue
            }
            out.append(c)
            i++
        }
        return out.toString() + tail
    }

    private const val ENDS = ".!?;\u2026\u3002\uFF01\uFF1F\uFF1B"
    private const val CHINESE_ENDS = "\u3002\uFF01\uFF1F\uFF1B"

    private const val MIN_SLOT_MS = 300L
}
