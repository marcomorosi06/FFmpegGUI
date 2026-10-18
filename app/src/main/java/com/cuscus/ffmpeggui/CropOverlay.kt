package com.cuscus.ffmpeggui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The crop frame drawn over a picture: what is cut away dimmed, a frame with
 * handles at its four corners and in the middle of its four sides.
 *
 *  - A handle is caught from a finger's width around it (the corners first),
 *    so it is easy to take even on a small preview; a touch inside the frame
 *    and away from the handles moves the whole frame; two fingers make it
 *    larger or smaller around them.
 *  - The handle follows the finger exactly (it does not drift when the frame
 *    meets an edge), snaps to the picture's edges and to its middle with a
 *    tick, and grows in the theme's colour while it is held; the rule of
 *    thirds shows meanwhile.
 *  - With a fixed shape ([freeform] false) every handle keeps it: a corner
 *    grows the frame from the opposite corner, a side from the middle of
 *    the opposite side.
 *  - The handles are kept out of the system's back gesture, so that one at
 *    the side of the screen can be dragged.
 */

/** A part of the frame a finger can hold. */
private enum class CropHandle(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    TOP_LEFT(true, true, false, false),
    TOP_RIGHT(false, true, true, false),
    BOTTOM_LEFT(true, false, false, true),
    BOTTOM_RIGHT(false, false, true, true),
    TOP(false, true, false, false),
    BOTTOM(false, false, false, true),
    LEFT(true, false, false, false),
    RIGHT(false, false, true, false),
    MOVE(false, false, false, false),
    ;

    val isCorner: Boolean get() = ordinal < 4
}

/**
 * The crop of a picture as fractions (0..1) of the box it is drawn in:
 * [cropRect] now, [onCropRectChange] with the new one as it is dragged.
 */
@Composable
fun CropOverlay(
    cropRect: Rect,
    freeform: Boolean,
    onCropRectChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var held by remember { mutableStateOf<CropHandle?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val motion = MaterialTheme.motionScheme
    val currentRect by rememberUpdatedState(cropRect)
    val currentOnChange by rememberUpdatedState(onCropRectChange)
    val currentFreeform by rememberUpdatedState(freeform)
    // While a handle is held: the rule of thirds fades in, the handle grows.
    val grip by animateFloatAsState(if (held != null) 1f else 0f, motion.fastSpatialSpec(), label = "cropGrip")

    // The handles' places on the screen, for the system's back gesture to leave them alone.
    fun handleArea(handle: CropHandle): Rect {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        val r = Rect(currentRect.left * w, currentRect.top * h, currentRect.right * w, currentRect.bottom * h)
        val reach = with(density) { TOUCH_REACH.toPx() }
        return Rect(point(handle, r), reach)
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .then(
                CropHandle.entries.filter { it != CropHandle.MOVE }.fold(Modifier as Modifier) { chain, handle ->
                    chain.systemGestureExclusion { handleArea(handle) }
                },
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w <= 0f || h <= 0f) return@awaitEachGesture
                    val start = currentRect.let { Rect(it.left * w, it.top * h, it.right * w, it.bottom * h) }
                    val reach = TOUCH_REACH.toPx()
                    // The nearest handle within reach (a corner before a side), else the inside.
                    val handle = CropHandle.entries
                        .filter { it != CropHandle.MOVE }
                        .map { it to (point(it, start) - down.position).getDistance() }
                        .filter { it.second <= reach }
                        .minByOrNull { it.second - if (it.first.isCorner) reach / 3f else 0f }
                        ?.first
                        ?: if (start.contains(down.position)) CropHandle.MOVE else return@awaitEachGesture
                    down.consume()
                    held = handle
                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    val ratio = if (start.height > 0f) start.width / start.height else 1f
                    val minSide = max(MIN_SIDE.toPx(), min(w, h) * MIN_SHARE)
                    val snap = SNAP_DISTANCE.toPx()
                    var snapped = false
                    var pinchFrom: Rect? = null
                    var pinchZoom = 1f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val next: Rect
                        if (pressed.size >= 2) {
                            // Two fingers: larger or smaller around their middle, the shape kept.
                            val from = pinchFrom ?: currentRect.let { Rect(it.left * w, it.top * h, it.right * w, it.bottom * h) }.also { pinchFrom = it }
                            pinchZoom *= event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            next = scaled(from, pinchZoom, centroid, w, h, minSide)
                        } else {
                            pinchFrom = null
                            pinchZoom = 1f
                            val change = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                            val delta = change.position - down.position
                            val raw = if (currentFreeform || handle == CropHandle.MOVE) {
                                free(start, handle, delta, w, h, minSide)
                            } else {
                                locked(start, handle, delta, ratio, w, h, minSide)
                            }
                            val (fitted, didSnap) = if (currentFreeform || handle == CropHandle.MOVE) snapped(raw, handle, w, h, snap) else raw to false
                            if (didSnap && !snapped) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            snapped = didSnap
                            next = fitted
                        }
                        event.changes.forEach { it.consume() }
                        currentOnChange(Rect(next.left / w, next.top / h, next.right / w, next.bottom / h))
                    }
                    held = null
                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
            },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val frame = Rect(cropRect.left * size.width, cropRect.top * size.height, cropRect.right * size.width, cropRect.bottom * size.height)

        // What is cut away, dimmed.
        val outside = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRect(frame)
            fillType = PathFillType.EvenOdd
        }
        drawPath(outside, Color.Black.copy(alpha = 0.58f))

        // The rule of thirds, while the frame is being changed.
        if (grip > 0.01f) {
            val line = Color.White.copy(alpha = 0.55f * grip)
            for (k in 1..2) {
                val x = frame.left + frame.width * k / 3f
                val y = frame.top + frame.height * k / 3f
                drawLine(line, Offset(x, frame.top), Offset(x, frame.bottom), strokeWidth = 1.dp.toPx())
                drawLine(line, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 1.dp.toPx())
            }
        }

        // The frame itself.
        drawRect(Color.White.copy(alpha = 0.9f), topLeft = frame.topLeft, size = frame.size, style = Stroke(width = 1.5.dp.toPx()))

        // The handles: brackets at the corners, bars in the middle of the sides, drawn inside the frame.
        val active = held
        CropHandle.entries.filter { it != CropHandle.MOVE }.forEach { handle ->
            val isHeld = active == handle || active == CropHandle.MOVE
            val color = if (isHeld) lerp(Color.White, accent, grip) else Color.White
            val thick = (if (isHeld) 4.dp.toPx() * (1f + 0.4f * grip) else 4.dp.toPx())
            if (handle.isCorner) {
                drawCorner(frame, handle, color, thick, min(CORNER_LENGTH.toPx(), min(frame.width, frame.height) / 3f))
            } else {
                drawSide(frame, handle, color, thick, min(SIDE_LENGTH.toPx() * (if (isHeld) 1f + 0.3f * grip else 1f), (if (handle == CropHandle.TOP || handle == CropHandle.BOTTOM) frame.width else frame.height) / 3f))
            }
        }
    }
}

/** Where [handle] is on [frame] (the middle for a move). */
private fun point(handle: CropHandle, frame: Rect): Offset = when (handle) {
    CropHandle.TOP_LEFT -> frame.topLeft
    CropHandle.TOP_RIGHT -> frame.topRight
    CropHandle.BOTTOM_LEFT -> frame.bottomLeft
    CropHandle.BOTTOM_RIGHT -> frame.bottomRight
    CropHandle.TOP -> Offset(frame.center.x, frame.top)
    CropHandle.BOTTOM -> Offset(frame.center.x, frame.bottom)
    CropHandle.LEFT -> Offset(frame.left, frame.center.y)
    CropHandle.RIGHT -> Offset(frame.right, frame.center.y)
    CropHandle.MOVE -> frame.center
}

/** [start] with [handle] dragged by [delta], any shape, inside the picture ([w] x [h]) and never smaller than [minSide]. */
private fun free(start: Rect, handle: CropHandle, delta: Offset, w: Float, h: Float, minSide: Float): Rect {
    if (handle == CropHandle.MOVE) {
        val dx = delta.x.coerceIn(-start.left, w - start.right)
        val dy = delta.y.coerceIn(-start.top, h - start.bottom)
        return start.translate(Offset(dx, dy))
    }
    var left = start.left
    var top = start.top
    var right = start.right
    var bottom = start.bottom
    if (handle.left) left = (start.left + delta.x).coerceIn(0f, right - minSide)
    if (handle.right) right = (start.right + delta.x).coerceIn(left + minSide, w)
    if (handle.top) top = (start.top + delta.y).coerceIn(0f, bottom - minSide)
    if (handle.bottom) bottom = (start.bottom + delta.y).coerceIn(top + minSide, h)
    return Rect(left, top, right, bottom)
}

/**
 * [start] with [handle] dragged by [delta], keeping its shape ([ratio], width
 * over height): a corner grows it from the opposite corner, a side from the
 * middle of the opposite side; inside the picture, never smaller than [minSide].
 */
private fun locked(start: Rect, handle: CropHandle, delta: Offset, ratio: Float, w: Float, h: Float, minSide: Float): Rect {
    val minScale = max(minSide / start.width, minSide / start.height)
    if (handle.isCorner) {
        val anchor = Offset(if (handle.left) start.right else start.left, if (handle.top) start.bottom else start.top)
        val corner = point(handle, start) + delta
        // The larger of the two moves decides, so that the corner keeps under the finger.
        val scale = max(abs(corner.x - anchor.x) / start.width, abs(corner.y - anchor.y) / start.height)
        val roomX = if (handle.left) anchor.x else w - anchor.x
        val roomY = if (handle.top) anchor.y else h - anchor.y
        val s = scale.coerceAtMost(min(roomX / start.width, roomY / start.height)).coerceAtLeast(minScale)
        val width = start.width * s
        val height = start.height * s
        val left = if (handle.left) anchor.x - width else anchor.x
        val top = if (handle.top) anchor.y - height else anchor.y
        return Rect(left, top, left + width, top + height)
    }
    val horizontal = handle == CropHandle.LEFT || handle == CropHandle.RIGHT
    val centre = start.center
    val scale = if (horizontal) {
        (start.width + if (handle.right) delta.x else -delta.x) / start.width
    } else {
        (start.height + if (handle.bottom) delta.y else -delta.y) / start.height
    }
    // Room on the side it grows towards, and on both sides of the middle it grows around.
    val maxScale = if (horizontal) {
        val along = if (handle.right) (w - start.left) / start.width else start.right / start.width
        val across = 2f * min(centre.y, h - centre.y) / start.height
        min(along, across)
    } else {
        val along = if (handle.bottom) (h - start.top) / start.height else start.bottom / start.height
        val across = 2f * min(centre.x, w - centre.x) / start.width
        min(along, across)
    }
    val s = scale.coerceIn(minScale, max(minScale, maxScale))
    val width = start.width * s
    val height = width / ratio
    return if (horizontal) {
        val left = if (handle.right) start.left else start.right - width
        Rect(left, centre.y - height / 2f, left + width, centre.y + height / 2f)
    } else {
        val top = if (handle.bottom) start.top else start.bottom - height
        Rect(centre.x - width / 2f, top, centre.x + width / 2f, top + height)
    }
}

/** [from] made [zoom] times larger around [around], its shape kept, inside the picture. */
private fun scaled(from: Rect, zoom: Float, around: Offset, w: Float, h: Float, minSide: Float): Rect {
    val minScale = max(minSide / from.width, minSide / from.height)
    val maxScale = min(w / from.width, h / from.height)
    val s = zoom.coerceIn(minScale, max(minScale, maxScale))
    val width = from.width * s
    val height = from.height * s
    val cx = around.x + (from.center.x - around.x) * s
    val cy = around.y + (from.center.y - around.y) * s
    val left = (cx - width / 2f).coerceIn(0f, w - width)
    val top = (cy - height / 2f).coerceIn(0f, h - height)
    return Rect(left, top, left + width, top + height)
}

/**
 * The sides [handle] moves pulled onto the picture's edges or its middle when
 * they come within [snap]; whether one was.
 */
private fun snapped(rect: Rect, handle: CropHandle, w: Float, h: Float, snap: Float): Pair<Rect, Boolean> {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom
    var any = false
    fun near(value: Float, target: Float): Boolean = abs(value - target) < snap
    if (handle == CropHandle.MOVE) {
        // The whole frame: onto an edge, or its middle onto the picture's.
        val dx = when {
            near(left, 0f) -> -left
            near(right, w) -> w - right
            near(rect.center.x, w / 2f) -> w / 2f - rect.center.x
            else -> 0f
        }
        val dy = when {
            near(top, 0f) -> -top
            near(bottom, h) -> h - bottom
            near(rect.center.y, h / 2f) -> h / 2f - rect.center.y
            else -> 0f
        }
        return rect.translate(Offset(dx, dy)) to (dx != 0f || dy != 0f)
    }
    if (handle.left && near(left, 0f)) { left = 0f; any = true }
    if (handle.right && near(right, w)) { right = w; any = true }
    if (handle.top && near(top, 0f)) { top = 0f; any = true }
    if (handle.bottom && near(bottom, h)) { bottom = h; any = true }
    return Rect(left, top, right, bottom) to any
}

/** A corner bracket of [frame], inside it, [length] along each side. */
private fun DrawScope.drawCorner(frame: Rect, handle: CropHandle, color: Color, thick: Float, length: Float) {
    val c = point(handle, frame)
    val inX = if (handle.left) 1f else -1f
    val inY = if (handle.top) 1f else -1f
    val edge = Offset(c.x + inX * thick / 2f, c.y + inY * thick / 2f)
    val shadow = Color.Black.copy(alpha = 0.35f)
    for ((tint, width) in listOf(shadow to thick + 2.dp.toPx(), color to thick)) {
        drawLine(tint, edge, Offset(edge.x + inX * length, edge.y), strokeWidth = width, cap = StrokeCap.Round)
        drawLine(tint, edge, Offset(edge.x, edge.y + inY * length), strokeWidth = width, cap = StrokeCap.Round)
    }
}

/** The bar in the middle of a side of [frame], inside it, [length] long. */
private fun DrawScope.drawSide(frame: Rect, handle: CropHandle, color: Color, thick: Float, length: Float) {
    val c = point(handle, frame)
    val inward = when (handle) {
        CropHandle.TOP -> Offset(0f, thick / 2f)
        CropHandle.BOTTOM -> Offset(0f, -thick / 2f)
        CropHandle.LEFT -> Offset(thick / 2f, 0f)
        else -> Offset(-thick / 2f, 0f)
    }
    val along = if (handle == CropHandle.TOP || handle == CropHandle.BOTTOM) Offset(length / 2f, 0f) else Offset(0f, length / 2f)
    val middle = c + inward
    drawLine(Color.Black.copy(alpha = 0.35f), middle - along, middle + along, strokeWidth = thick + 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, middle - along, middle + along, strokeWidth = thick, cap = StrokeCap.Round)
}

/**
 * Room left around a picture while it is cropped: its handles then stay clear
 * of the screen's edges, where the system's gestures are.
 */
internal val CROP_FRAME_INSET = 24.dp

/** How far from a handle a finger still catches it. */
private val TOUCH_REACH = 32.dp

/** The handles' lengths. */
private val CORNER_LENGTH = 22.dp
private val SIDE_LENGTH = 28.dp

/** The smallest the frame gets: this much, or this share of the picture's shorter side. */
private val MIN_SIDE = 40.dp
private const val MIN_SHARE = 0.08f

/** How close to an edge (or the middle) a side is pulled onto it. */
private val SNAP_DISTANCE = 10.dp
