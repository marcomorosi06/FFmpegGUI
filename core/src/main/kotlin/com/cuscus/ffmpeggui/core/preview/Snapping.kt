package com.cuscus.ffmpeggui.core.preview

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Magnetic guides for what is moved by hand on the preview (a caption, a
 * picture, the video itself), as in a slide editor: its edges and its middle
 * stick to the frame's edges and middle, and to those of the other things on
 * it, when they come close; turns stick to whole steps. All in pixels of the
 * frame the thing lies on.
 */

/** A box on the frame: left, top, right and bottom edges, in pixels. */
data class SnapBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun moved(dx: Float, dy: Float): SnapBox = SnapBox(left + dx, top + dy, right + dx, bottom + dy)
}

/** A line something sticks to: across the frame ([vertical]: x = [at]), or down it (y = [at]). */
data class Guide(
    val vertical: Boolean,
    val at: Float,
    /** The other thing it lines up with; null for the frame itself. */
    val target: SnapBox? = null,
)

/** How far to shift what is moved, and the lines it now lies on. */
data class Snap(val dx: Float, val dy: Float, val guides: List<Guide>) {
    /** Tells two snaps apart by where they stick (for a tick of feedback when that changes). */
    val key: String get() = guides.joinToString(";") { "${if (it.vertical) "v" else "h"}${it.at.roundToInt()}" }

    companion object {
        val NONE = Snap(0f, 0f, emptyList())
    }
}

/**
 * Where [moving] sticks on a frame of [width] x [height] pixels: each way, the
 * closest of its edges or middle to an edge or the middle of the frame, or of
 * one of [others], within [threshold] pixels (the frame first when two are as
 * close). The result also lists every line it then lies on.
 */
fun snapMove(moving: SnapBox, width: Float, height: Float, others: List<SnapBox>, threshold: Float): Snap {
    fun targets(vertical: Boolean): List<Pair<Float, SnapBox?>> {
        val frame = if (vertical) listOf(0f, width / 2f, width) else listOf(0f, height / 2f, height)
        val items = others.flatMap { box ->
            val lines = if (vertical) listOf(box.left, box.centerX, box.right) else listOf(box.top, box.centerY, box.bottom)
            lines.map { it to box }
        }
        return frame.map { it to null } + items
    }

    fun anchors(box: SnapBox, vertical: Boolean): List<Float> =
        if (vertical) listOf(box.left, box.centerX, box.right) else listOf(box.top, box.centerY, box.bottom)

    fun shift(vertical: Boolean): Float {
        var best = Float.MAX_VALUE
        var shift = 0f
        for ((line, _) in targets(vertical)) {
            for (anchor in anchors(moving, vertical)) {
                val distance = abs(line - anchor)
                // Strictly closer: on a tie the frame (listed first) wins.
                if (distance <= threshold && distance < best - 0.01f) {
                    best = distance
                    shift = line - anchor
                }
            }
        }
        return if (best == Float.MAX_VALUE) 0f else shift
    }

    val dx = shift(vertical = true)
    val dy = shift(vertical = false)
    val placed = moving.moved(dx, dy)
    val guides = mutableListOf<Guide>()
    for (vertical in listOf(true, false)) {
        // Every line it now lies on, stuck or lined up exactly.
        for ((line, box) in targets(vertical)) {
            if (anchors(placed, vertical).any { abs(it - line) < 0.5f } && guides.none { it.vertical == vertical && abs(it.at - line) < 0.5f && it.target == box }) {
                guides += Guide(vertical, line, box)
            }
        }
    }
    return Snap(dx, dy, guides)
}

/**
 * [degrees] stuck to the closest multiple of [step] when within [threshold]
 * degrees of it; null when it is not.
 */
fun snapAngle(degrees: Float, step: Float = 45f, threshold: Float = 4f): Float? {
    val nearest = Math.round(degrees / step) * step
    return if (abs(degrees - nearest) <= threshold) nearest else null
}
