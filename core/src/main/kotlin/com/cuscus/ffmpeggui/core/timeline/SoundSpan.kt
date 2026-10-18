package com.cuscus.ffmpeggui.core.timeline

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Where a placed sound plays and which part of its file it plays: from
 * [trimStartMs] to [trimEndMs] of the file, starting at [startMs] of the
 * exported video. [fileDurationMs] is the length of the whole file.
 *
 * The part plays [repeats] times in a row, at [speed] (2 = twice as fast, so
 * half as long). Its length on the video follows from both; dragging an edge
 * on the timeline moves the matching edge of the part in the file by as much
 * as it takes for the edge to follow the finger.
 *
 * Immutable; every edit returns a new span and keeps it valid: inside the file,
 * not before the start of the video and at least [MIN_SOUND_MS] long.
 */
data class SoundSpan(
    override val startMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val fileDurationMs: Long,
    val speed: Float = 1f,
    val repeats: Int = 1,
) : LaneSpan<SoundSpan> {
    /** Length of the part of the file that plays, once. */
    val rangeMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    /** Playback rate, kept to what the player and the export support. */
    val rate: Float get() = if (speed > 0f) speed.coerceIn(MIN_SPEED, MAX_SPEED) else 1f

    /** How many times the part plays, at least once. */
    val loops: Int get() = repeats.coerceIn(1, MAX_REPEATS)

    /** How long it plays on the video. */
    val lengthMs: Long get() = (rangeMs.toDouble() * loops / rate).roundToLong()

    /** Where it stops, on the output clock. */
    override val endMs: Long get() = startMs + lengthMs

    /** File milliseconds (of one repetition) that pass in [outputMs] of the video. */
    private fun fileMsFor(outputMs: Long): Long = (outputMs.toDouble() * rate / loops).roundToLong()

    /** Video milliseconds that [fileMs] of the file (in each repetition) take. */
    private fun outputMsFor(fileMs: Long): Long = (fileMs.toDouble() * loops / rate).roundToLong()

    /** Shortest part of the file that still plays for [MIN_SOUND_MS]. */
    private val minRangeMs: Long get() = ceil(MIN_SOUND_MS.toDouble() * rate / loops).toLong().coerceAtLeast(1L)

    /** Placed [deltaMs] later (earlier when negative), never before the start of the video. */
    fun movedBy(deltaMs: Long): SoundSpan = copy(startMs = (startMs + deltaMs).coerceAtLeast(0L))

    /** Placed so that it starts at [startMs]. */
    override fun movedTo(startMs: Long): SoundSpan = movedBy(startMs - this.startMs)

    /**
     * Its start edge dragged by [deltaMs] of the video: more or less of the
     * beginning of the file is played, and the rest of the sound stays where it is.
     */
    override fun withStartEdgeMovedBy(deltaMs: Long): SoundSpan {
        val fileDelta = fileMsFor(deltaMs)
            .coerceAtLeast(-trimStartMs)
            .coerceAtLeast(-fileMsFor(startMs))
            .coerceAtMost(rangeMs - minRangeMs)
        val trimStart = trimStartMs + fileDelta
        // The end stays where it was; the start goes where the shorter (or longer) part begins.
        val start = (endMs - copy(trimStartMs = trimStart).lengthMs).coerceAtLeast(0L)
        return copy(startMs = start, trimStartMs = trimStart)
    }

    /** Its end edge dragged by [deltaMs] of the video: more or less of the end of the file is played. */
    override fun withEndEdgeMovedBy(deltaMs: Long): SoundSpan {
        val limit = if (fileDurationMs > 0L) fileDurationMs else Long.MAX_VALUE
        val end = (trimEndMs + fileMsFor(deltaMs)).coerceAtMost(limit).coerceAtLeast(trimStartMs + minRangeMs)
        return copy(trimEndMs = end)
    }

    /**
     * Plays the part of the file from [fromMs] to [toMs], still starting at the
     * same moment of the video (what the panel's trimmer edits).
     */
    fun withFileRange(fromMs: Long, toMs: Long): SoundSpan {
        val limit = if (fileDurationMs > 0L) fileDurationMs else Long.MAX_VALUE
        val from = fromMs.coerceIn(0L, (limit - minRangeMs).coerceAtLeast(0L))
        val to = toMs.coerceAtMost(limit).coerceAtLeast(from + minRangeMs)
        return copy(trimStartMs = from, trimEndMs = to)
    }

    /** At another speed, from the same moment; the length follows. */
    fun withSpeed(speed: Float): SoundSpan =
        copy(speed = speed.coerceIn(MIN_SPEED, MAX_SPEED)).let { it.withFileRange(it.trimStartMs, it.trimEndMs) }

    /** Played [repeats] times in a row, from the same moment. */
    fun withRepeats(repeats: Int): SoundSpan =
        copy(repeats = repeats.coerceIn(1, MAX_REPEATS)).let { it.withFileRange(it.trimStartMs, it.trimEndMs) }

    /**
     * Where in the file the sound is at [outputMs] of the video (clamped to the
     * sound); what the timeline draws its waveform from.
     */
    fun fileMsAt(outputMs: Long): Long {
        val range = rangeMs
        if (range <= 0L) return trimStartMs
        val played = ((outputMs - startMs).toDouble() * rate).roundToLong().coerceIn(0L, range * loops)
        val inPart = if (played == range * loops) range else played % range
        return trimStartMs + inPart
    }

    /** True when [outputMs] is far enough inside to cut there. A repeated sound is not cut. */
    fun canSplitAt(outputMs: Long): Boolean =
        loops == 1 && outputMs - startMs >= MIN_SOUND_MS && endMs - outputMs >= MIN_SOUND_MS

    /** The two halves of a cut at [outputMs], or null when it cannot be cut there. */
    fun splitAt(outputMs: Long): Pair<SoundSpan, SoundSpan>? {
        if (!canSplitAt(outputMs)) return null
        val cut = trimStartMs + ((outputMs - startMs).toDouble() * rate).roundToLong()
        val first = copy(trimEndMs = cut)
        return first to copy(startMs = first.endMs, trimStartMs = cut)
    }

    companion object {
        /** Shortest sound that can be left by a trim or a cut. */
        const val MIN_SOUND_MS = 100L

        /** Speeds a sound can play at. */
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f

        /** Most times a sound can be repeated. */
        const val MAX_REPEATS = 20

        /** The whole file at [startMs]. */
        fun wholeFile(startMs: Long, fileDurationMs: Long): SoundSpan =
            SoundSpan(startMs.coerceAtLeast(0L), 0L, fileDurationMs.coerceAtLeast(MIN_SOUND_MS), fileDurationMs)

        /**
         * A row for each span so that spans sharing a row never overlap, filled in
         * order of start (ties in the order given); the first row that is free is
         * taken. Returns the row of each span, in the order given.
         */
        fun rows(spans: List<SoundSpan>): IntArray = TimeSpan.rows(spans)

        /**
         * The target nearest to [value] within [tolerance], or null: what an edge
         * being dragged snaps to (the playhead, cuts, other sounds).
         */
        fun snap(value: Double, targets: List<Double>, tolerance: Double): Double? =
            targets.filter { abs(it - value) <= tolerance }.minByOrNull { abs(it - value) }
    }
}
