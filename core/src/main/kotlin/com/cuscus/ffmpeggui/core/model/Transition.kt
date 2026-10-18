package com.cuscus.ffmpeggui.core.model

import kotlin.math.min

/**
 * How one clip gives way to the next: FFmpeg's xfade transition [xfade] (the
 * export) and its family, which tells the live preview how to draw it.
 *
 * The two clips overlap for the transition's length: the joined video is that
 * much shorter, as in every video editor.
 */
enum class TransitionKind(val xfade: String) {
    FADE("fade"),
    FADE_BLACK("fadeblack"),
    FADE_WHITE("fadewhite"),
    WIPE_LEFT("wipeleft"),
    WIPE_RIGHT("wiperight"),
    WIPE_UP("wipeup"),
    WIPE_DOWN("wipedown"),
    SLIDE_LEFT("slideleft"),
    SLIDE_RIGHT("slideright"),
    SLIDE_UP("slideup"),
    SLIDE_DOWN("slidedown"),
    COVER_LEFT("coverleft"),
    COVER_RIGHT("coverright"),
    REVEAL_LEFT("revealleft"),
    REVEAL_RIGHT("revealright"),
    SMOOTH_LEFT("smoothleft"),
    SMOOTH_RIGHT("smoothright"),
    CIRCLE_OPEN("circleopen"),
    CIRCLE_CLOSE("circleclose"),
    CIRCLE_CROP("circlecrop"),
    RECT_CROP("rectcrop"),
    VERT_OPEN("vertopen"),
    HORZ_OPEN("horzopen"),
    RADIAL("radial"),
    ZOOM_IN("zoomin"),
}

/**
 * The transition from a clip into the one after it: its [kind], [durationMs]
 * long (made shorter when a clip is too short for it, see [effectiveMs]).
 */
data class Transition(val kind: TransitionKind = TransitionKind.FADE, val durationMs: Long = DEFAULT_MS) {

    companion object {
        const val DEFAULT_MS = 500L
        const val MIN_MS = 100L
        const val MAX_MS = 3_000L

        /**
         * How long a transition of [requestedMs] really lasts between clips that
         * play [outgoingMs] and [incomingMs]: never more than half of either, so
         * that the two transitions of a clip never meet, and 0 when that is too
         * short to be seen.
         */
        fun effectiveMs(requestedMs: Long, outgoingMs: Long, incomingMs: Long): Long {
            val ms = min(requestedMs.coerceIn(0L, MAX_MS), min(outgoingMs, incomingMs) / 2)
            return if (ms < MIN_MS / 2) 0L else ms
        }
    }
}

/**
 * How long each transition between [playedMs] (the clips' lengths once their
 * speed is applied, in order) really lasts: one value per gap, 0 where there
 * is none. A null length (open-ended clip) has none around it.
 */
fun transitionLengths(transitions: List<Transition?>, playedMs: List<Long?>): List<Long> =
    (0 until (playedMs.size - 1).coerceAtLeast(0)).map { i ->
        val transition = transitions.getOrNull(i) ?: return@map 0L
        val a = playedMs[i] ?: return@map 0L
        val b = playedMs[i + 1] ?: return@map 0L
        Transition.effectiveMs(transition.durationMs, a, b)
    }
