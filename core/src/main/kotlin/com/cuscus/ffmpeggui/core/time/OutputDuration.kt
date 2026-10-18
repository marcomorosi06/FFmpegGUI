package com.cuscus.ffmpeggui.core.time

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.Split
import com.cuscus.ffmpeggui.core.model.transitionLengths

/*
 * Lengths of the edited sequence. There are three of them:
 *
 *  - footage: the clips laid end to end, before any speed change;
 *  - joined: what comes out of the join, each clip at its own speed, two clips
 *    overlapping for the length of the transition between them;
 *  - output: the joined video at the speed of the whole video.
 *
 * Stream copy ignores every speed, so all three are the same there.
 */

/** Length of [segment] before its speed change, or null when it cannot be known. */
fun ConversionRequest.footageLengthMs(segment: Segment): Long? =
    segment.durationMs
        ?: sources[segment.source].durationMs.takeIf { it > 0 }?.let { (it - segment.startMs).coerceAtLeast(0L) }

/** How long [segment] plays in the joined video, or null when it cannot be known. */
fun ConversionRequest.playedLengthMs(segment: Segment): Long? {
    val length = footageLengthMs(segment) ?: return null
    return if (encoding.streamCopy) length else segment.effects.playedMs(length)
}

/**
 * How long each transition between two segments lasts in the joined video (0
 * where there is none): one value per gap. Stream copy joins without any.
 */
fun ConversionRequest.transitionsMs(): List<Long> {
    if (segments.size < 2) return emptyList()
    if (encoding.streamCopy) return List(segments.size - 1) { 0L }
    return transitionLengths(segments.map { it.effects.transition }, segments.map { playedLengthMs(it) })
}

/** True when some clip gives way to the next with a transition. */
fun ConversionRequest.hasTransitions(): Boolean = transitionsMs().any { it > 0L }

/**
 * Length of the joined video (each clip at its own speed, the clips overlapping
 * where a transition joins them), or null when a length is unknown.
 */
fun ConversionRequest.joinedDurationMs(): Long? {
    var total = 0L
    for (segment in segments) total += playedLengthMs(segment) ?: return null
    return total - transitionsMs().sum()
}

/**
 * Length of the file the request will produce, or null when it cannot be known
 * (open-ended segment, frame extraction).
 *
 * Used for progress reporting, fade-out placement and "split in N parts".
 */
fun ConversionRequest.expectedOutputDurationMs(): Long? {
    if (frameExtraction != FrameExtraction.Disabled || format.isImage) return null
    val joined = joinedDurationMs() ?: return null
    val speed = if (encoding.streamCopy) 1f else effects.effectiveSpeed
    return (joined / speed).toLong().takeIf { it > 0 }
}

/**
 * How much of the edited sequence FFmpeg goes through, for its progress: the
 * output's length, or for a sequence of frames the part they are taken from
 * (the footage as it is, without the clips' speeds). Null when not known.
 */
fun ConversionRequest.progressDurationMs(): Long? {
    val frames = frameExtraction as? FrameExtraction.Sequence ?: return expectedOutputDurationMs()
    var total = 0L
    for (segment in segments) {
        total += segment.durationMs
            ?: sources.getOrNull(segment.source)?.durationMs?.takeIf { it > 0 }?.let { (it - segment.startMs).coerceAtLeast(0L) }
            ?: return null
    }
    val end = (frames.endMs ?: total).coerceAtMost(total)
    return (end - frames.startMs.coerceAtLeast(0L)).takeIf { it > 0 }
}

/** How the output is cut into numbered files. */
sealed interface Segmenting {
    /** A new file every [seconds]. */
    data class Every(val seconds: Int) : Segmenting

    /** A new file at each of these positions (seconds from the start). */
    data class At(val cutPointsSeconds: List<Double>) : Segmenting
}

/** Segmenting plan for this request, or null when the output is a single file. */
fun ConversionRequest.segmenting(): Segmenting? {
    if (frameExtraction != FrameExtraction.Disabled) return null
    if (!format.hasAudio) return null // GIF / custom cannot be segmented
    return when (val mode = split) {
        Split.Disabled -> null
        is Split.ByDuration -> Segmenting.Every(mode.seconds.coerceAtLeast(1))
        is Split.ByParts -> {
            val parts = mode.count
            if (parts < 2) return null
            val totalSeconds = (expectedOutputDurationMs() ?: return null) / 1000.0
            Segmenting.At((1 until parts).map { it * totalSeconds / parts })
        }
    }
}
