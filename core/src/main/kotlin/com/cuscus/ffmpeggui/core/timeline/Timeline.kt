package com.cuscus.ffmpeggui.core.timeline

import com.cuscus.ffmpeggui.core.model.Effects

/**
 * One piece of footage on the timeline: a range of a source file, on that
 * file's own clock, with the effects that apply to this clip only.
 *
 * [endMs] is null only while the length of the source is still unknown (the
 * clip then runs "to the end"); such a clip cannot be split or trimmed yet.
 */
data class Clip(
    val id: String,
    val sourceId: String,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val effects: Effects = Effects.NONE,
) {
    val isOpenEnded: Boolean get() = endMs == null

    /** Playable length; 0 for an open-ended clip. */
    val lengthMs: Long get() = ((endMs ?: startMs) - startMs).coerceAtLeast(0L)
}

/**
 * The edited sequence: clips played one after another.
 *
 * Immutable. Every editing operation returns a new timeline and never fails:
 * a request that makes no sense (splitting on a cut, moving past the ends,
 * removing the last clip) returns the timeline unchanged.
 */
data class Timeline(val clips: List<Clip> = emptyList()) {

    /** A position inside one clip. */
    data class Position(val index: Int, val offsetMs: Long)

    val durationMs: Long get() = clips.sumOf { it.lengthMs }

    fun indexOf(clipId: String): Int = clips.indexOfFirst { it.id == clipId }

    /** Where clip [index] starts on the timeline. */
    fun startOf(index: Int): Long = clips.take(index.coerceIn(0, clips.size)).sumOf { it.lengthMs }

    /** Clip under [timelineMs]; a cut belongs to the clip that starts there. */
    fun locate(timelineMs: Long): Position? {
        if (clips.isEmpty()) return null
        var start = 0L
        clips.forEachIndexed { index, clip ->
            val end = start + clip.lengthMs
            if (timelineMs < end) return Position(index, (timelineMs - start).coerceAtLeast(0L))
            start = end
        }
        return Position(clips.lastIndex, clips.last().lengthMs)
    }

    // ---------------------------------------------------------------------
    // Editing
    // ---------------------------------------------------------------------

    fun append(clip: Clip): Timeline = copy(clips = clips + clip)

    /** Puts [clip] at [index] of the sequence (clamped to the ends). */
    fun insert(index: Int, clip: Clip): Timeline {
        val at = index.coerceIn(0, clips.size)
        return copy(clips = clips.take(at) + clip + clips.drop(at))
    }

    /**
     * Puts [clip] at [timelineMs]: the clip found there is cut in two (its second
     * half gets [newClipId]) and [clip] goes in between. Too close to the edge of
     * a clip to cut, [clip] goes on the nearer side of it.
     */
    fun insertAt(timelineMs: Long, clip: Clip, newClipId: String): Timeline {
        val position = locate(timelineMs) ?: return append(clip)
        if (canSplitAt(timelineMs)) return splitAt(timelineMs, newClipId).insert(position.index + 1, clip)
        val target = clips[position.index]
        val before = position.offsetMs < target.lengthMs / 2
        return insert(if (before) position.index else position.index + 1, clip)
    }

    /** True when [timelineMs] is far enough from both edges of its clip to cut there. */
    fun canSplitAt(timelineMs: Long): Boolean {
        val position = locate(timelineMs) ?: return false
        val clip = clips[position.index]
        return !clip.isOpenEnded &&
            position.offsetMs >= MIN_CLIP_MS &&
            clip.lengthMs - position.offsetMs >= MIN_CLIP_MS
    }

    /**
     * Cuts the clip under [timelineMs] in two; the part of the file after the
     * cut gets [newClipId]. Both halves keep the clip's effects, except that the
     * fade-in stays on the half that plays first and the fade-out on the other.
     * A reversed clip plays the later part of its file first, so its halves are
     * placed in that order and the result looks exactly as before the cut.
     */
    fun splitAt(timelineMs: Long, newClipId: String): Timeline {
        if (!canSplitAt(timelineMs)) return this
        val position = locate(timelineMs) ?: return this
        val clip = clips[position.index]
        val cut = clip.startMs + position.offsetMs
        // A speed curve is cut with the clip: each half keeps its own part of it.
        val f = position.offsetMs.toDouble() / clip.lengthMs
        val before = clip.copy(endMs = cut, effects = clip.effects.windowed(0.0, f))
        val after = clip.copy(id = newClipId, startMs = cut, effects = clip.effects.windowed(f, 1.0))
        val (first, second) = if (clip.effects.reversed) after to before else before to after
        // The transition into the next clip stays with the half that plays last.
        val halves = listOf(
            first.copy(effects = first.effects.copy(fadeOutSeconds = 0f, transition = null)),
            second.copy(effects = second.effects.copy(fadeInSeconds = 0f)),
        )
        return copy(clips = clips.take(position.index) + halves + clips.drop(position.index + 1))
    }

    /** Removes a clip. The last remaining clip cannot be removed. */
    fun remove(clipId: String): Timeline =
        if (clips.size <= 1 || indexOf(clipId) < 0) this else copy(clips = clips.filter { it.id != clipId })

    /** Inserts a copy of the clip right after it. */
    fun duplicate(clipId: String, newClipId: String): Timeline {
        val index = indexOf(clipId)
        if (index < 0) return this
        return copy(clips = clips.take(index + 1) + clips[index].copy(id = newClipId) + clips.drop(index + 1))
    }

    /** Moves a clip [offset] places (negative = earlier). */
    fun move(clipId: String, offset: Int): Timeline {
        val from = indexOf(clipId)
        if (from < 0) return this
        val to = (from + offset).coerceIn(0, clips.lastIndex)
        if (to == from) return this
        val reordered = clips.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return copy(clips = reordered)
    }

    /**
     * Changes the range a clip shows. The range is kept inside the source
     * ([sourceDurationMs], 0 = unknown) and at least [MIN_CLIP_MS] long.
     */
    fun trim(clipId: String, startMs: Long, endMs: Long, sourceDurationMs: Long): Timeline {
        val index = indexOf(clipId)
        if (index < 0) return this
        val limit = if (sourceDurationMs > 0) sourceDurationMs else Long.MAX_VALUE
        val start = startMs.coerceIn(0L, (limit - MIN_CLIP_MS).coerceAtLeast(0L))
        val end = endMs.coerceIn(start + MIN_CLIP_MS, maxOf(limit, start + MIN_CLIP_MS))
        return replace(index, clips[index].copy(startMs = start, endMs = end))
    }

    /** Gives every open-ended clip of [sourceId] its real end once the length is known. */
    fun closeOpenClips(sourceId: String, sourceDurationMs: Long): Timeline {
        if (sourceDurationMs <= 0 || clips.none { it.sourceId == sourceId && it.isOpenEnded }) return this
        return copy(
            clips = clips.map { clip ->
                if (clip.sourceId == sourceId && clip.isOpenEnded) {
                    clip.copy(endMs = maxOf(sourceDurationMs, clip.startMs + MIN_CLIP_MS))
                } else {
                    clip
                }
            },
        )
    }

    // ---------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------

    /**
     * The clips that play between [startMs] and [endMs] of the whole sequence
     * (null = to the end), with the first and last one shortened as needed.
     * This is how "trim the whole video" is applied without touching the clips.
     */
    fun window(startMs: Long, endMs: Long?): List<Clip> {
        val from = startMs.coerceAtLeast(0L)
        val result = mutableListOf<Clip>()
        var clipStart = 0L
        for ((index, clip) in clips.withIndex()) {
            if (clip.isOpenEnded) {
                // Length unknown: it can only be shortened from the known side(s).
                val newStart = clip.startMs + (from - clipStart).coerceAtLeast(0L)
                val newEnd = endMs?.let { clip.startMs + (it - clipStart) }
                if (newEnd == null || newEnd > newStart) result += clip.copy(startMs = newStart, endMs = newEnd)
                // Nothing after it can be placed, but without an end trim it all plays,
                // so a file whose length could not be read does not hide the clips after it.
                if (endMs == null) result += clips.drop(index + 1)
                break
            }
            val clipEnd = clipStart + clip.lengthMs
            val visibleFrom = maxOf(from, clipStart)
            val visibleTo = if (endMs == null) clipEnd else minOf(endMs, clipEnd)
            if (visibleTo > visibleFrom) {
                val length = clip.lengthMs.coerceAtLeast(1L).toDouble()
                result += clip.copy(
                    startMs = clip.startMs + (visibleFrom - clipStart),
                    endMs = clip.startMs + (visibleTo - clipStart),
                    // A speed curve keeps the part of it that still plays.
                    effects = clip.effects.windowed((visibleFrom - clipStart) / length, (visibleTo - clipStart) / length),
                )
            }
            clipStart = clipEnd
        }
        return result
    }

    /** Changes the effects of one clip. */
    fun updateEffects(clipId: String, update: (Effects) -> Effects): Timeline {
        val index = indexOf(clipId)
        if (index < 0) return this
        return replace(index, clips[index].copy(effects = update(clips[index].effects)))
    }

    private fun replace(index: Int, clip: Clip): Timeline =
        copy(clips = clips.toMutableList().also { it[index] = clip })

    companion object {
        /** Shortest clip the editor will create. */
        const val MIN_CLIP_MS = 100L
    }
}
