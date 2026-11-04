package com.cuscus.ffmpeggui.core.eggs

import kotlin.math.abs
import kotlin.math.sign

/**
 * Tells a DJ's scratch on the timeline from ordinary scrubbing: the finger
 * dragging back and forth, quickly and widely, [turns] times within
 * [windowNanos]. Each swing must cover [minSwingPx] before it counts, so a
 * slow look around or a trembling finger never sets it off. Feed it every step
 * of the drag ([feed]); a pause longer than the window starts it over. Pure,
 * tested.
 */
class ScratchDetector(
    private val minSwingPx: Float,
    private val turns: Int = DEFAULT_TURNS,
    private val windowNanos: Long = DEFAULT_WINDOW_NANOS,
) {
    /** What a step of the drag did. */
    enum class Step {
        /** Nothing to tell. */
        NONE,

        /** The direction turned with a full swing, not (yet) a scratch. */
        TURN,

        /** The direction turned and, with the turns just before, it is a scratch. */
        SCRATCH,
    }

    private var direction = 0f
    private var swing = 0f
    private var lastNanos = Long.MIN_VALUE
    private val turnTimes = ArrayDeque<Long>()

    /** One step of the drag: [deltaPx] (either way) at [nanos]. */
    fun feed(deltaPx: Float, nanos: Long): Step {
        if (deltaPx == 0f || deltaPx.isNaN()) return Step.NONE
        if (lastNanos != Long.MIN_VALUE && nanos - lastNanos > windowNanos) reset()
        lastNanos = nanos
        val s = sign(deltaPx)
        if (direction == 0f || s == direction) {
            direction = s
            swing += abs(deltaPx)
            return Step.NONE
        }
        // The direction turned: it counts only after a wide enough swing.
        val counted = swing >= minSwingPx
        direction = s
        swing = abs(deltaPx)
        if (!counted) return Step.NONE
        turnTimes.addLast(nanos)
        while (turnTimes.isNotEmpty() && nanos - turnTimes.first() > windowNanos) turnTimes.removeFirst()
        return if (turnTimes.size >= turns) Step.SCRATCH else Step.TURN
    }

    /** Starts over (the finger lifted). */
    fun reset() {
        direction = 0f
        swing = 0f
        lastNanos = Long.MIN_VALUE
        turnTimes.clear()
    }

    companion object {
        /** Four turns: back, forth, back, forth. */
        const val DEFAULT_TURNS = 4

        /** Within 1.2 seconds. */
        const val DEFAULT_WINDOW_NANOS = 1_200_000_000L
    }
}
