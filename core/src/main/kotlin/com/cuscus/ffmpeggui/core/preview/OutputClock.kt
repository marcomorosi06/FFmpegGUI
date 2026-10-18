package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming

/**
 * Converts between the editor's timeline and the output clock, for what is
 * placed on the exported video rather than on the footage (the placed sounds).
 *
 * The editor's timeline lays the clips end to end at their media length,
 * before any speed change and before the whole-video trim; the output clock is
 * the exported file's. [segments] are the clips that play (the timeline with
 * the trim applied) and [windowStartMs] is where they start on the timeline.
 *
 * Before the start and past the end of the video the footage is imagined to go
 * on at the speed of the whole video, so that a sound reaching past either end
 * is still drawn at its full length and can still be grabbed.
 */
class OutputClock(
    segments: List<SegmentTiming>,
    speed: Float = 1f,
    private val reversed: Boolean = false,
    private val windowStartMs: Long = 0L,
) {
    private val timeline = PreviewTimeline(segments, speed, reversed)
    private val speed: Double = timeline.speed.toDouble()
    private val mediaDurationMs: Long = timeline.mediaDurationMs

    /** Length of the exported video. */
    val outputDurationMs: Long = timeline.outputDurationMs

    /** Where output instant [outputMs] is drawn on the editor's timeline. */
    fun timelineMs(outputMs: Long): Double {
        val media = when {
            outputMs < 0L -> {
                val before = -outputMs * speed
                if (reversed) mediaDurationMs + before else -before
            }
            outputMs > outputDurationMs -> {
                val after = (outputMs - outputDurationMs) * speed
                if (reversed) -after else mediaDurationMs + after
            }
            else -> timeline.mediaMsForOutput(outputMs).toDouble()
        }
        return windowStartMs + media
    }

    /** The output instant drawn at [timelineMs] of the editor's timeline. */
    fun outputMs(timelineMs: Double): Long {
        val media = timelineMs - windowStartMs
        val output = when {
            media < 0.0 -> if (reversed) outputDurationMs + -media / speed else media / speed
            media > mediaDurationMs -> {
                val after = (media - mediaDurationMs) / speed
                if (reversed) -after else outputDurationMs + after
            }
            else -> timeline.outputMs(Math.round(media)).toDouble()
        }
        return Math.round(output)
    }
}
