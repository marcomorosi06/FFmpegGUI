package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.TransitionKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/*
 * How the live preview draws a transition: the same math as FFmpeg's xfade
 * filter (libavfilter/vf_xfade.c), told as what to do with each of the two
 * clips' pictures - fade it, move it, show only part of it - so that layers
 * of the screen can reproduce it without touching a pixel.
 *
 * Throughout, `t` is how far the transition has gone, 0..1; xfade counts the
 * other way (its `progress` is 1 - t).
 */

/** The part of a picture that shows, in fractions of the frame (left, top, right, bottom). */
data class Window(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Along what a soft mask changes (see [maskAlpha]). */
enum class MaskShape {
    NONE,

    /** Across the frame: u = x / width. */
    ACROSS,

    /** Down the frame: u = y / height. */
    DOWN,

    /** Out from the middle: u = distance / half the diagonal. */
    RADIUS,

    /** Around the middle: u = xfade's angle, atan2(x - w/2, y - h/2), in -PI..PI. */
    ANGLE,
}

/**
 * How one of the two clips of a transition is drawn at one moment: faded to
 * [alpha], moved by ([dx], [dy]) frames, enlarged [scale] times about the
 * middle, only its [window] or its [circle] (radius in halves of the frame's
 * diagonal) showing, and faded by a soft [mask] (see [maskAlpha]).
 */
data class PartLook(
    val alpha: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val window: Window? = null,
    val circle: Float = -1f,
    val mask: MaskShape = MaskShape.NONE,
) {
    companion object {
        val PLAIN = PartLook()
        val HIDDEN = PartLook(alpha = 0f)
    }
}

/** The clip going out is drawn over the one coming in (it moves or zooms away from it). */
val TransitionKind.outgoingOnTop: Boolean
    get() = this == TransitionKind.REVEAL_LEFT || this == TransitionKind.REVEAL_RIGHT || this == TransitionKind.ZOOM_IN

/** What shows behind both clips: white for [TransitionKind.FADE_WHITE], black otherwise. */
val TransitionKind.whiteBehind: Boolean get() = this == TransitionKind.FADE_WHITE

/** How the clip going out ([incoming] false) or coming in looks [t] of the way through a [kind] transition. */
fun transitionLook(kind: TransitionKind, t: Float, incoming: Boolean): PartLook {
    val t = t.coerceIn(0f, 1f)
    val p = 1f - t
    fun moved(dx: Float = 0f, dy: Float = 0f) = PartLook(dx = dx, dy = dy)
    return when (kind) {
        TransitionKind.FADE -> if (incoming) PartLook(alpha = t) else PartLook.PLAIN
        TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE -> {
            // out = [A s1 + bg (1 - s1)] p + [bg s2 + B (1 - s2)] (1 - p), drawn as bg, A, B.
            val s1 = smoothstep(0.8f, 1f, p)
            val s2 = smoothstep(0.2f, 1f, p)
            val b = (1f - s2) * (1f - p)
            val a = s1 * p
            if (incoming) PartLook(alpha = b) else PartLook(alpha = if (b < 1f - 1e-4f) (a / (1f - b)).coerceIn(0f, 1f) else 0f)
        }
        TransitionKind.WIPE_LEFT -> if (incoming) PartLook(window = Window(p, 0f, 1f, 1f)) else PartLook.PLAIN
        TransitionKind.WIPE_RIGHT -> if (incoming) PartLook(window = Window(0f, 0f, t, 1f)) else PartLook.PLAIN
        TransitionKind.WIPE_UP -> if (incoming) PartLook(window = Window(0f, p, 1f, 1f)) else PartLook.PLAIN
        TransitionKind.WIPE_DOWN -> if (incoming) PartLook(window = Window(0f, 0f, 1f, t)) else PartLook.PLAIN
        TransitionKind.SLIDE_LEFT -> if (incoming) moved(dx = p) else moved(dx = -t)
        TransitionKind.SLIDE_RIGHT -> if (incoming) moved(dx = -p) else moved(dx = t)
        TransitionKind.SLIDE_UP -> if (incoming) moved(dy = p) else moved(dy = -t)
        TransitionKind.SLIDE_DOWN -> if (incoming) moved(dy = -p) else moved(dy = t)
        TransitionKind.COVER_LEFT -> if (incoming) moved(dx = p) else PartLook.PLAIN
        TransitionKind.COVER_RIGHT -> if (incoming) moved(dx = -p) else PartLook.PLAIN
        TransitionKind.REVEAL_LEFT -> if (incoming) PartLook.PLAIN else moved(dx = -t)
        TransitionKind.REVEAL_RIGHT -> if (incoming) PartLook.PLAIN else moved(dx = t)
        TransitionKind.SMOOTH_LEFT, TransitionKind.SMOOTH_RIGHT, TransitionKind.VERT_OPEN ->
            if (incoming) PartLook(mask = MaskShape.ACROSS) else PartLook.PLAIN
        TransitionKind.HORZ_OPEN -> if (incoming) PartLook(mask = MaskShape.DOWN) else PartLook.PLAIN
        TransitionKind.CIRCLE_OPEN, TransitionKind.CIRCLE_CLOSE -> if (incoming) PartLook(mask = MaskShape.RADIUS) else PartLook.PLAIN
        TransitionKind.RADIAL -> if (incoming) PartLook(mask = MaskShape.ANGLE) else PartLook.PLAIN
        TransitionKind.CIRCLE_CROP -> {
            // Black outside a circle that shrinks to nothing on the first clip, then grows on the second.
            val radius = abs(2f * p - 1f).pow(3)
            val shows = if (incoming) p < 0.5f else p >= 0.5f
            if (shows) PartLook(circle = radius) else PartLook.HIDDEN
        }
        TransitionKind.RECT_CROP -> {
            val half = abs(p - 0.5f)
            val shows = if (incoming) p < 0.5f else p >= 0.5f
            if (shows) PartLook(window = Window(0.5f - half, 0.5f - half, 0.5f + half, 0.5f + half)) else PartLook.HIDDEN
        }
        TransitionKind.ZOOM_IN -> {
            // The first clip is enlarged about the middle while it fades into the second.
            val zoom = smoothstep(0.5f, 1f, p)
            if (incoming) PartLook.PLAIN else PartLook(alpha = smoothstep(0f, 0.5f, p), scale = 1f / max(zoom, MIN_ZOOM))
        }
    }
}

/**
 * Opacity of the clip coming in at [u] of its mask (see [MaskShape]), [t] of
 * the way through a [kind] transition; 1 for a transition without a mask.
 */
fun maskAlpha(kind: TransitionKind, t: Float, u: Float): Float {
    val p = 1f - t.coerceIn(0f, 1f)
    return when (kind) {
        TransitionKind.SMOOTH_LEFT -> smoothstep(0f, 1f, 1f + u - 2f * p)
        TransitionKind.SMOOTH_RIGHT -> smoothstep(0f, 1f, 1f + (1f - u) - 2f * p)
        TransitionKind.VERT_OPEN, TransitionKind.HORZ_OPEN -> smoothstep(0f, 1f, 2f - abs(2f * u - 1f) - 2f * p)
        TransitionKind.CIRCLE_OPEN -> 1f - smoothstep(0f, 1f, u + (p - 0.5f) * 3f)
        TransitionKind.CIRCLE_CLOSE -> smoothstep(0f, 1f, u + (0.5f - p) * 3f)
        TransitionKind.RADIAL -> smoothstep(0f, 1f, u - (p - 0.5f) * (PI.toFloat() * 2.5f))
        else -> 1f
    }
}

/** GLSL's smoothstep, as xfade has it. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val k = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/** Least zoom factor of [TransitionKind.ZOOM_IN] drawn (xfade reaches 0: one pixel stretched over the frame). */
private const val MIN_ZOOM = 0.02f
