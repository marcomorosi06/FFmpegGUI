package com.cuscus.ffmpeggui

import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.TextOverlay

/*
 * Ready-made movements in and out of a text or picture, made of keyframes: a
 * movement written in is nothing but keyframes (the timeline shows them as
 * diamonds, the panels change them, the export and the preview play them as
 * any other), placed over its first or last moments. It goes from a pose where
 * it is hidden (moved aside, small, turned, transparent) to its pose in the
 * middle of the time it shows, or back. Keyframes already there outside those
 * moments stay.
 */

/** A ready-made movement in or out. */
enum class OverlayAnimation {
    /** Slides in from, or out to, a side, fading. */
    SIDE_LEFT, SIDE_RIGHT, SIDE_TOP, SIDE_BOTTOM,

    /** Grows from small, fading in (shrinks, fading out). */
    ZOOM,

    /** Pops: grows past its size and settles. */
    POP,

    /** Spins in a full turn while growing (pictures only). */
    SPIN,

    /** Drops from above and bounces on its place. */
    BOUNCE,
}

/** How far a slide comes from, a fraction of the free space. */
private const val SLIDE = 0.35f

/** How long a movement is at the least. */
private const val SHORTEST_MS = 120L

/**
 * The keyframes of [animation] over its first [durationMs] ([entering]) or its
 * last ([lengthMs] being how long it shows), ending (or starting) at [rest], its
 * pose in the middle. Times from its start, as its keyframes count.
 */
fun Keyframes.withAnimation(
    animation: OverlayAnimation,
    entering: Boolean,
    rest: Map<KeyProperty, Float>,
    durationMs: Long,
    lengthMs: Long,
): Keyframes {
    val length = lengthMs.coerceAtLeast(2 * SHORTEST_MS)
    val d = durationMs.coerceIn(SHORTEST_MS, length / 2)
    val (from, to) = if (entering) 0L to d else (length - d) to length
    var keys = Keyframes(frames.filter { it.atMs < from - Keyframes.TOLERANCE_MS || it.atMs > to + Keyframes.TOLERANCE_MS })
    for ((t, values) in poses(animation, rest, d)) {
        keys = keys.set(if (entering) t else length - t, values)
    }
    return keys
}

/**
 * The poses of [animation] going in over [d] ms: from hidden at 0 to [rest]
 * at [d] (going out is the same, the other way round).
 */
private fun poses(animation: OverlayAnimation, rest: Map<KeyProperty, Float>, d: Long): List<Pair<Long, Map<KeyProperty, Float>>> {
    fun v(property: KeyProperty) = rest[property] ?: 0f
    val opacity = v(KeyProperty.OPACITY)
    fun at(fraction: Float) = (d * fraction).toLong()
    fun restOf(vararg properties: KeyProperty) = properties.filter { it in rest }.associateWith { v(it) }
    return when (animation) {
        OverlayAnimation.SIDE_LEFT, OverlayAnimation.SIDE_RIGHT -> {
            val x = v(KeyProperty.X)
            val start = if (animation == OverlayAnimation.SIDE_LEFT) (x - SLIDE).coerceAtLeast(0f) else (x + SLIDE).coerceAtMost(1f)
            listOf(
                0L to mapOf(KeyProperty.X to start, KeyProperty.OPACITY to 0f),
                d to restOf(KeyProperty.X, KeyProperty.OPACITY),
            )
        }
        OverlayAnimation.SIDE_TOP, OverlayAnimation.SIDE_BOTTOM -> {
            val y = v(KeyProperty.Y)
            val start = if (animation == OverlayAnimation.SIDE_TOP) (y - SLIDE).coerceAtLeast(0f) else (y + SLIDE).coerceAtMost(1f)
            listOf(
                0L to mapOf(KeyProperty.Y to start, KeyProperty.OPACITY to 0f),
                d to restOf(KeyProperty.Y, KeyProperty.OPACITY),
            )
        }
        OverlayAnimation.ZOOM -> listOf(
            0L to mapOf(KeyProperty.SIZE to v(KeyProperty.SIZE) * 0.25f, KeyProperty.OPACITY to 0f),
            d to restOf(KeyProperty.SIZE, KeyProperty.OPACITY),
        )
        OverlayAnimation.POP -> listOf(
            0L to mapOf(KeyProperty.SIZE to v(KeyProperty.SIZE) * 0.2f, KeyProperty.OPACITY to 0f),
            at(0.4f) to mapOf(KeyProperty.OPACITY to opacity),
            at(0.65f) to mapOf(KeyProperty.SIZE to v(KeyProperty.SIZE) * 1.15f),
            d to restOf(KeyProperty.SIZE, KeyProperty.OPACITY),
        )
        OverlayAnimation.SPIN -> listOf(
            0L to mapOf(KeyProperty.ROTATION to v(KeyProperty.ROTATION) - 360f, KeyProperty.SIZE to v(KeyProperty.SIZE) * 0.3f, KeyProperty.OPACITY to 0f),
            d to restOf(KeyProperty.ROTATION, KeyProperty.SIZE, KeyProperty.OPACITY),
        )
        OverlayAnimation.BOUNCE -> {
            val y = v(KeyProperty.Y)
            listOf(
                0L to mapOf(KeyProperty.Y to (y - 0.4f).coerceAtLeast(0f), KeyProperty.OPACITY to 0f),
                at(0.3f) to mapOf(KeyProperty.OPACITY to opacity),
                at(0.55f) to mapOf(KeyProperty.Y to (y + 0.05f).coerceAtMost(1f)),
                at(0.8f) to mapOf(KeyProperty.Y to (y - 0.02f).coerceAtLeast(0f)),
                d to restOf(KeyProperty.Y, KeyProperty.OPACITY),
            )
        }
    }
}

/** This picture with [animation] put in over its first or last [durationMs] of [lengthMs]. */
fun ImageOverlay.animated(animation: OverlayAnimation, entering: Boolean, durationMs: Long, lengthMs: Long): ImageOverlay {
    val rest = at(lengthMs / 2).keyValues()
    return copy(keyframes = keyframes.withAnimation(animation, entering, rest, durationMs, lengthMs))
}

/** This text with [animation] put in (a text does not turn: no spin). */
fun TextOverlay.animated(animation: OverlayAnimation, entering: Boolean, durationMs: Long, lengthMs: Long): TextOverlay {
    if (animation == OverlayAnimation.SPIN) return this
    val rest = at(lengthMs / 2).keyValues()
    return copy(keyframes = keyframes.withAnimation(animation, entering, rest, durationMs, lengthMs))
}

/** Without keyframes, as it shows in the middle of its time. */
fun ImageOverlay.withoutAnimation(lengthMs: Long): ImageOverlay = at(lengthMs / 2).copy(keyframes = Keyframes.NONE)

fun TextOverlay.withoutAnimation(lengthMs: Long): TextOverlay = at(lengthMs / 2).copy(keyframes = Keyframes.NONE)
