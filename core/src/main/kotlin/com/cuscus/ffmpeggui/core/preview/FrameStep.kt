package com.cuscus.ffmpeggui.core.preview

import kotlin.math.ceil
import kotlin.math.floor

/**
 * One clip of the playing sequence, as a frame step sees it: where its part of
 * its file starts ([fileStartMs]), how long it is ([lengthMs]) and its file's
 * frame rate ([frameRate], 0 when not known).
 */
data class StepClip(val fileStartMs: Long, val lengthMs: Long, val frameRate: Float)

/**
 * Steps the playhead one frame of the footage at a time: the editor's arrows
 * beside play / pause, for cutting exactly where a finger on the timeline would
 * glide past. Frames are counted in each clip's own file, at its own rate, so
 * a step always lands on the next (or previous) frame that file holds; past a
 * clip's end it goes on to the first frame of the next clip, before its start
 * to the last frame of the one before.
 */
object FrameStep {

    /** Frame rate assumed for a file whose rate is not known (a still, an unprobed file). */
    const val FALLBACK_RATE = 30f

    /**
     * Where one step [forward] (or back) from [positionMs] of the playing
     * sequence (the clips end to end) lands: the middle of the part of that frame
     * the clip shows, so that an exact seek shows it whatever the rounding (a
     * clip may start or end inside a frame). Null at either end.
     */
    fun target(clips: List<StepClip>, positionMs: Long, forward: Boolean): Long? {
        if (clips.isEmpty()) return null
        val starts = LongArray(clips.size)
        var at = 0L
        clips.forEachIndexed { i, clip ->
            starts[i] = at
            at += clip.lengthMs.coerceAtLeast(0L)
        }
        val total = at
        if (total <= 0L) return null
        val position = positionMs.coerceIn(0L, total - 1)
        val index = clips.indices.lastOrNull { starts[it] <= position && clips[it].lengthMs > 0L } ?: return null
        val clip = clips[index]
        val fileMs = clip.fileStartMs + (position - starts[index])
        // The frame shown now: a hair of slack, so that a frame's own start counts as it.
        val shown = floor(fileMs / frameMs(clip) + EPSILON).toLong()
        visibleMiddle(clip, shown + if (forward) 1 else -1)?.let { return starts[index] + it }
        val step = if (forward) 1 else -1
        var i = index + step
        while (i in clips.indices) {
            val next = clips[i]
            if (next.lengthMs > 0L) {
                val frame = frameMs(next)
                val edge = if (forward) {
                    floor(next.fileStartMs / frame + EPSILON).toLong()
                } else {
                    ceil((next.fileStartMs + next.lengthMs) / frame - EPSILON).toLong() - 1
                }
                visibleMiddle(next, edge)?.let { return starts[i] + it }
            }
            i += step
        }
        return null
    }

    /**
     * The middle of the part of frame [frame] (counted in [clip]'s file) the clip
     * shows, from the clip's start; null when the clip shows none of it.
     */
    private fun visibleMiddle(clip: StepClip, frame: Long): Long? {
        val length = frameMs(clip)
        val from = maxOf(frame * length, clip.fileStartMs.toDouble())
        val to = minOf((frame + 1) * length, (clip.fileStartMs + clip.lengthMs).toDouble())
        if (to - from < MIN_VISIBLE_MS) return null
        return ((from + to) / 2.0 - clip.fileStartMs).toLong().coerceIn(0L, clip.lengthMs - 1)
    }

    /** How long one frame of [clip]'s file lasts, in milliseconds. */
    fun frameMs(clip: StepClip): Double = 1000.0 / (clip.frameRate.takeIf { it > 0f } ?: FALLBACK_RATE)

    private const val EPSILON = 1e-6

    /** Less of a frame than this is not worth a step. */
    private const val MIN_VISIBLE_MS = 1.0
}
