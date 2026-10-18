package com.cuscus.ffmpeggui.core.timeline

/**
 * Something placed on the exported video's clock that the timeline lets the user
 * move and trim by its edges: a sound ([SoundSpan]) or a text or picture
 * ([TimeSpan]). Every edit keeps it valid and returns a new one.
 */
interface LaneSpan<T : LaneSpan<T>> {
    val startMs: Long
    val endMs: Long

    /** Placed so that it starts at [startMs], never before the start of the video. */
    fun movedTo(startMs: Long): T

    /** Its start edge dragged by [deltaMs]; the end stays where it is. */
    fun withStartEdgeMovedBy(deltaMs: Long): T

    /** Its end edge dragged by [deltaMs]; the start stays where it is. */
    fun withEndEdgeMovedBy(deltaMs: Long): T
}

/**
 * When a text or picture shows on the exported video: from [startMs] to
 * [endMs], at least [MIN_SPAN_MS] long and never before the start.
 */
data class TimeSpan(override val startMs: Long, override val endMs: Long) : LaneSpan<TimeSpan> {

    val lengthMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    override fun movedTo(startMs: Long): TimeSpan {
        val start = startMs.coerceAtLeast(0L)
        return TimeSpan(start, start + lengthMs)
    }

    fun movedBy(deltaMs: Long): TimeSpan = movedTo(startMs + deltaMs)

    override fun withStartEdgeMovedBy(deltaMs: Long): TimeSpan =
        copy(startMs = (startMs + deltaMs).coerceIn(0L, (endMs - MIN_SPAN_MS).coerceAtLeast(0L)))

    override fun withEndEdgeMovedBy(deltaMs: Long): TimeSpan =
        copy(endMs = (endMs + deltaMs).coerceAtLeast(startMs + MIN_SPAN_MS))

    /** Lasting [lengthMs] from the same start. */
    fun withLength(lengthMs: Long): TimeSpan = copy(endMs = startMs + lengthMs.coerceAtLeast(MIN_SPAN_MS))

    /** True when [atMs] is far enough inside to cut there. */
    fun canSplitAt(atMs: Long): Boolean = atMs - startMs >= MIN_SPAN_MS && endMs - atMs >= MIN_SPAN_MS

    /** The two halves of a cut at [atMs], or null when it is too close to an edge. */
    fun splitAt(atMs: Long): Pair<TimeSpan, TimeSpan>? =
        if (canSplitAt(atMs)) TimeSpan(startMs, atMs) to TimeSpan(atMs, endMs) else null

    companion object {
        /** Shortest a text or picture can be made to show. */
        const val MIN_SPAN_MS = 100L

        /** How long a new text or picture shows. */
        const val DEFAULT_LENGTH_MS = 3_000L

        /**
         * A row for each span so that spans sharing a row never overlap, filled in
         * order of start (ties in the order given); the first row that is free is
         * taken. Returns the row of each span, in the order given.
         */
        fun rows(spans: List<LaneSpan<*>>): IntArray {
            val result = IntArray(spans.size)
            val rowEnds = mutableListOf<Long>()
            spans.indices.sortedWith(compareBy({ spans[it].startMs }, { it })).forEach { index ->
                val span = spans[index]
                val row = rowEnds.indexOfFirst { it <= span.startMs }.takeIf { it >= 0 } ?: rowEnds.size
                if (row == rowEnds.size) rowEnds += span.endMs else rowEnds[row] = span.endMs
                result[index] = row
            }
            return result
        }
    }
}
