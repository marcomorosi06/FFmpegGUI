package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.SpeedCurve
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.model.transitionLengths

/**
 * Maps between the clocks the live preview has to keep in sync:
 *
 *  - media time: where the player is inside the clips laid end to end, before
 *    any speed change or reversal. The editor's timeline and playhead use it;
 *  - timeline time: the same instant in the joined video, i.e. after each
 *    clip's own speed and direction, and after the reversal of the whole video
 *    but before its speed change. This is the clock subtitles follow;
 *  - output time: what the exported file will show, i.e. timeline time divided
 *    by the speed of the whole video. Fades, the seek bar and the extra
 *    soundtrack follow it.
 *
 * @param segments   the clips in playback order, with their own speed and direction.
 * @param speed      speed of the whole video.
 * @param reversed   true when the whole video plays backwards.
 * @param keepPitch  false when the pitch of the whole video follows its speed.
 */
class PreviewTimeline(
    segments: List<SegmentTiming>,
    speed: Float = 1f,
    val reversed: Boolean = false,
    val keepPitch: Boolean = true,
) {
    /**
     * One clip: its length on the media clock, its own speed (or speed curve,
     * which then takes its place) and direction, and whether its sound keeps
     * its pitch.
     */
    data class SegmentTiming(
        val lengthMs: Long,
        val speed: Float = 1f,
        val reversed: Boolean = false,
        val curve: SpeedCurve? = null,
        val keepPitch: Boolean = true,
        /** How the clip gives way to the next one; it overlaps it for its length. */
        val transition: Transition? = null,
    ) {
        private val ramp: SpeedCurve? = curve?.takeUnless { it.isFlat }
        private val constant: Double =
            (curve?.takeIf { it.isFlat }?.nodes?.first()?.speed ?: speed).let { if (it > 0f) it.toDouble() else 1.0 }

        /** The speed, the average one along a curve. */
        val effectiveSpeed: Float get() = (ramp?.averageSpeed ?: constant).toFloat()

        /** How long the clip plays. */
        fun playedLengthMs(): Double = ramp?.playedLengthMs(lengthMs.toDouble()) ?: (lengthMs / constant)

        /** How long the first [consumedMs] of footage (in playing order) take to play. */
        fun playedOffsetMs(consumedMs: Double): Double = ramp?.playedMs(lengthMs.toDouble(), consumedMs) ?: (consumedMs / constant)

        /** Inverse of [playedOffsetMs]. */
        fun consumedOffsetMs(playedMs: Double): Double = ramp?.consumedMs(lengthMs.toDouble(), playedMs) ?: (playedMs * constant)

        /** The speed after [consumedMs] of footage (in playing order). */
        fun speedAt(consumedMs: Double): Float =
            ramp?.speedAt(if (lengthMs > 0) consumedMs / lengthMs else 0.0) ?: constant.toFloat()
    }

    /** A position inside one clip of the sequence. */
    data class MediaPoint(val segment: Int, val offsetMs: Long)

    /**
     * Where a clip is being played: [playedOffsetMs] of [playedLengthMs], both on
     * the clip's own clock after its speed change (what the clip's fades follow).
     */
    data class ClipPosition(val segment: Int, val playedOffsetMs: Long, val playedLengthMs: Long)

    private val timings: List<SegmentTiming> = segments.map { it.copy(lengthMs = it.lengthMs.coerceAtLeast(0L)) }
    private val lengths: List<Long> = timings.map { it.lengthMs }
    private val starts: List<Long> = lengths.runningFold(0L) { acc, length -> acc + length }.dropLast(1)
    private val playedLengths: List<Double> = timings.map { it.playedLengthMs() }

    /** How long each transition really lasts, one per gap between two clips (0 where there is none). */
    val overlaps: List<Long> = transitionLengths(timings.map { it.transition }, playedLengths.map { Math.round(it) })

    /** Where each clip starts in the joined video: after the one before, less the overlap between them. */
    private val joinedStarts: List<Double> = playedLengths.indices.runningFold(0.0) { acc, i ->
        acc + playedLengths[i] - (overlaps.getOrNull(i) ?: 0L)
    }.dropLast(1)

    val speed: Float = if (speed > 0f) speed else 1f
    val segmentCount: Int get() = timings.size
    val mediaDurationMs: Long = lengths.sum()

    /** Length of the joined video: every clip at its own speed. */
    val joinedDurationMs: Long = Math.round(playedLengths.sum() - overlaps.sum())
    val outputDurationMs: Long = (joinedDurationMs / this.speed).toLong()

    /** Media time of [point], counted from the start of the first clip. */
    fun mediaMs(point: MediaPoint): Long {
        if (timings.isEmpty()) return 0L
        val index = point.segment.coerceIn(0, timings.lastIndex)
        return starts[index] + point.offsetMs.coerceIn(0L, lengths[index])
    }

    /** Clip and offset for a media time. Positions past the end land on the last clip. */
    fun locate(mediaMs: Long): MediaPoint {
        if (timings.isEmpty()) return MediaPoint(0, 0L)
        val clamped = mediaMs.coerceIn(0L, mediaDurationMs)
        val index = starts.indexOfLast { it <= clamped }.coerceAtLeast(0)
        // Skip empty clips so the player is never parked on one.
        val usable = (index..timings.lastIndex).firstOrNull { lengths[it] > 0 && clamped < starts[it] + lengths[it] }
            ?: index
        return MediaPoint(usable, (clamped - starts[usable]).coerceIn(0L, lengths[usable]))
    }

    /** True when the clip [segment] is seen backwards in the output. */
    fun playsBackwards(segment: Int): Boolean =
        (timings.getOrNull(segment)?.reversed ?: false) != reversed

    /** Rate at which the media of clip [segment] goes by while the output plays at normal speed (on average). */
    fun rate(segment: Int): Float = (timings.getOrNull(segment)?.effectiveSpeed ?: 1f) * speed

    /** True when clip [segment] follows a speed curve (its rate changes as it plays). */
    fun curved(segment: Int): Boolean = timings.getOrNull(segment)?.curve?.isFlat == false

    /** Rate at which the media goes by at [mediaMs] while the output plays at normal speed. */
    fun rateAt(mediaMs: Long): Float {
        if (timings.isEmpty()) return speed
        val point = locate(mediaMs)
        val timing = timings[point.segment]
        return timing.speedAt(consumed(timing, point.offsetMs).toDouble()) * speed
    }

    /**
     * Pitch factor the sound at [mediaMs] is heard at: 1 where the pitch is kept,
     * else the speed of the clip and / or of the whole video.
     */
    fun pitchAt(mediaMs: Long): Float {
        if (timings.isEmpty()) return if (keepPitch) 1f else speed
        val point = locate(mediaMs)
        val timing = timings[point.segment]
        val clip = if (timing.keepPitch) 1f else timing.speedAt(consumed(timing, point.offsetMs).toDouble())
        return clip * (if (keepPitch) 1f else speed)
    }

    /** Footage of [timing] played at [offsetMs] (its media offset), in playing order. */
    private fun consumed(timing: SegmentTiming, offsetMs: Long): Long =
        if (timing.reversed) timing.lengthMs - offsetMs else offsetMs

    /**
     * How far through its footage the clip at [mediaMs] is, 0..1 in playing
     * order (what a still's motion follows: the export moves it along the footage).
     */
    fun footageProgress(mediaMs: Long): Float {
        if (timings.isEmpty()) return 0f
        val point = locate(mediaMs)
        val timing = timings[point.segment]
        return if (timing.lengthMs > 0) (consumed(timing, point.offsetMs).toFloat() / timing.lengthMs).coerceIn(0f, 1f) else 0f
    }

    /** Position inside its clip, on the clip's own played clock. */
    fun clipPosition(mediaMs: Long): ClipPosition {
        if (timings.isEmpty()) return ClipPosition(0, 0L, 0L)
        val point = locate(mediaMs)
        val timing = timings[point.segment]
        val offset = if (timing.reversed) timing.lengthMs - point.offsetMs else point.offsetMs
        return ClipPosition(
            segment = point.segment,
            playedOffsetMs = Math.round(timing.playedOffsetMs(offset.toDouble())),
            playedLengthMs = Math.round(playedLengths[point.segment]),
        )
    }

    /** Timeline (subtitle) clock for a media time. */
    fun timelineMs(mediaMs: Long): Long {
        val joined = joinedMs(mediaMs)
        return if (reversed) (joinedDurationMs - joined).coerceAtLeast(0L) else joined
    }

    fun outputMs(mediaMs: Long): Long = (timelineMs(mediaMs) / speed).toLong()

    /** Inverse of [outputMs]. */
    fun mediaMsForOutput(outputMs: Long): Long {
        if (timings.isEmpty()) return 0L
        val timeline = (outputMs.coerceIn(0L, outputDurationMs) * speed.toDouble()).coerceIn(0.0, joinedDurationMs.toDouble())
        val joined = if (reversed) joinedDurationMs - timeline else timeline

        // The clip on screen at that instant; a boundary belongs to the next non-empty clip.
        var index = timings.lastIndex
        for (i in timings.indices) {
            if (lengths[i] > 0 && joined < joinedStarts[i] + playedLengths[i]) { index = i; break }
        }
        val timing = timings[index]
        val local = timing.consumedOffsetMs(joined - joinedStarts[index]).coerceIn(0.0, timing.lengthMs.toDouble())
        val offset = if (timing.reversed) timing.lengthMs - local else local
        return starts[index] + Math.round(offset).coerceIn(0L, timing.lengthMs)
    }

    /** Position in the joined video, before the whole video is reversed. */
    private fun joinedMs(mediaMs: Long): Long {
        if (timings.isEmpty()) return 0L
        val point = locate(mediaMs)
        val timing = timings[point.segment]
        val offset = if (timing.reversed) timing.lengthMs - point.offsetMs else point.offsetMs
        return Math.round(joinedStarts[point.segment] + timing.playedOffsetMs(offset.toDouble()))
            .coerceIn(0L, joinedDurationMs)
    }

    /**
     * Two clips on screen at once: [outgoing] giving way to [incoming] with its
     * transition, [progress] (0..1) of the way. The incoming clip is at
     * [incomingOffsetMs] of its media (its own clock, as [MediaPoint.offsetMs])
     * and has played [incomingPlayedMs] so far.
     */
    data class TransitionPoint(
        val outgoing: Int,
        val incoming: Int,
        val progress: Float,
        val incomingOffsetMs: Long,
        val incomingPlayedMs: Long,
    )

    /** The transition on screen at [outputMs], or null when one clip alone is. */
    fun transitionAt(outputMs: Long): TransitionPoint? {
        if (timings.size < 2 || overlaps.none { it > 0L }) return null
        val timeline = (outputMs.coerceIn(0L, outputDurationMs) * speed.toDouble()).coerceIn(0.0, joinedDurationMs.toDouble())
        val joined = if (reversed) joinedDurationMs - timeline else timeline
        for (gap in overlaps.indices) {
            val overlap = overlaps[gap]
            if (overlap <= 0L) continue
            val from = joinedStarts[gap + 1]
            if (joined >= from && joined < from + overlap) {
                val incoming = timings[gap + 1]
                val played = joined - from
                val consumed = incoming.consumedOffsetMs(played).coerceIn(0.0, incoming.lengthMs.toDouble())
                val offset = if (incoming.reversed) incoming.lengthMs - consumed else consumed
                return TransitionPoint(
                    outgoing = gap,
                    incoming = gap + 1,
                    progress = (played / overlap).toFloat().coerceIn(0f, 1f),
                    incomingOffsetMs = Math.round(offset).coerceIn(0L, incoming.lengthMs),
                    incomingPlayedMs = Math.round(played),
                )
            }
        }
        return null
    }

    /** The stretch of the output (ms, in order) the transition after clip [gap] shows over; empty when it has none. */
    fun transitionOutputRange(gap: Int): LongRange {
        val overlap = overlaps.getOrNull(gap) ?: return LongRange.EMPTY
        if (overlap <= 0L) return LongRange.EMPTY
        val from = joinedStarts[gap + 1]
        val to = from + overlap
        val a = if (reversed) joinedDurationMs - to else from
        val b = if (reversed) joinedDurationMs - from else to
        return (a / speed).toLong()..(b / speed).toLong()
    }

    /** Where clip [segment] starts playing in the joined video, ms. */
    fun joinedStartMs(segment: Int): Long = joinedStarts.getOrNull(segment)?.let { Math.round(it) } ?: 0L

    /**
     * How much of the footage of clip [segment] (played forwards) goes by while it
     * comes in through the transition before it, ms: the part the clip shows only
     * inside that transition. 0 for a clip played backwards or without one.
     */
    fun incomingFootageMs(segment: Int): Long {
        val timing = timings.getOrNull(segment) ?: return 0L
        val overlap = overlaps.getOrNull(segment - 1) ?: return 0L
        if (overlap <= 0L || timing.reversed) return 0L
        return Math.round(timing.consumedOffsetMs(overlap.toDouble())).coerceIn(0L, timing.lengthMs)
    }

    companion object {
        /** Clips at normal speed and direction, inside a whole video at [speed]. */
        fun ofLengths(lengthsMs: List<Long>, speed: Float = 1f, reversed: Boolean = false): PreviewTimeline =
            PreviewTimeline(lengthsMs.map { SegmentTiming(it) }, speed, reversed)
    }
}
