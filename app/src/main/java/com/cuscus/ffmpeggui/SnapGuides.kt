@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.preview.Guide
import com.cuscus.ffmpeggui.core.preview.Snap
import com.cuscus.ffmpeggui.core.preview.SnapBox
import com.cuscus.ffmpeggui.core.preview.snapAngle
import com.cuscus.ffmpeggui.core.preview.snapMove
import kotlin.math.PI
import kotlin.math.abs

/*
 * Magnetic guides on the preview, as in a slide editor: what is moved by hand
 * (a caption, a picture, the video) sticks to the middle and the edges of its
 * frame and of the other things on it, and its turn to whole steps; the lines
 * it sticks to show while it does, a tick of feedback each time it catches.
 * The math is core's (Snapping.kt).
 */

/**
 * The things lying on one frame (a clip's, or the whole video's canvas), that
 * what is moved there can line up with, and the guides shown meanwhile.
 */
@Stable
internal class SnapScene {
    private val boxes = mutableMapOf<String, () -> SnapBox?>()

    /** The lines what is being moved lies on now. */
    var guides by mutableStateOf<List<Guide>>(emptyList())
        private set

    /** The turn it stuck to, in degrees, while it does. */
    var angle by mutableStateOf<Int?>(null)
        private set

    private var lastKey = ""
    private var lastAngle: Int? = null

    /** [id] lies on this frame where [box] says (read when something else moves). */
    fun register(id: String, box: () -> SnapBox?) {
        boxes[id] = box
    }

    fun unregister(id: String) {
        boxes.remove(id)
    }

    /** Where the others lie, but [id] itself. */
    fun others(id: String): List<SnapBox> = boxes.filterKeys { it != id }.values.mapNotNull { it() }

    /**
     * Where [moving] (that of [id]) sticks on a frame of [width] x [height]
     * pixels; its guides show, and a new catch ticks.
     */
    fun snap(id: String, moving: SnapBox, width: Float, height: Float, threshold: Float, haptic: HapticFeedback): Snap {
        val snap = snapMove(moving, width, height, others(id), threshold)
        guides = snap.guides
        val key = snap.key
        if (key.isNotEmpty() && key != lastKey) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastKey = key
        return snap
    }

    /** [degrees] stuck to a whole step (multiples of 45) when close; the badge shows it, a new catch ticks. */
    fun turn(degrees: Float, haptic: HapticFeedback): Float {
        val stuck = snapAngle(degrees)
        val shown = stuck?.let { Math.round(((it % 360f) + 360f) % 360f) }
        if (shown != null && shown != lastAngle) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastAngle = shown
        angle = shown
        return stuck ?: degrees
    }

    /** The hand let go: nothing shows. */
    fun clear() {
        guides = emptyList()
        angle = null
        lastKey = ""
        lastAngle = null
    }
}

@Composable
internal fun rememberSnapScene(): SnapScene = remember { SnapScene() }

/**
 * The guides of [scene] over its frame: the frame's own lines in the tertiary
 * tone, those of another thing dashed in the primary tone with that thing
 * outlined; and the turn caught, in a small badge at the top.
 */
@Composable
internal fun BoxScope.SnapGuidesLayer(scene: SnapScene) {
    val guides = scene.guides
    val frameLine = MaterialTheme.colorScheme.tertiary
    val itemLine = MaterialTheme.colorScheme.primary
    if (guides.isNotEmpty()) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = 1.5.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            val outlined = mutableSetOf<SnapBox>()
            for (guide in guides) {
                val target = guide.target
                val color = if (target == null) frameLine else itemLine
                val effect = if (target == null) null else dash
                if (guide.vertical) {
                    drawLine(color, Offset(guide.at, 0f), Offset(guide.at, size.height), strokeWidth = width, pathEffect = effect)
                } else {
                    drawLine(color, Offset(0f, guide.at), Offset(size.width, guide.at), strokeWidth = width, pathEffect = effect)
                }
                if (target != null && outlined.add(target)) {
                    drawRect(
                        itemLine.copy(alpha = 0.8f),
                        topLeft = Offset(target.left, target.top),
                        size = Size(target.right - target.left, target.bottom - target.top),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                    )
                }
            }
        }
    }
    val angle = scene.angle
    val shownAngle = lastShownAngle(angle)
    AnimatedVisibility(
        visible = angle != null,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "$shownAngle\u00B0",
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

/** While the badge leaves, it keeps showing the last turn. */
@Composable
private fun lastShownAngle(angle: Int?): Int {
    val last = remember { intArrayOf(0) }
    if (angle != null) last[0] = angle
    return last[0]
}

/**
 * One gesture of one or two fingers on something: [onStart] once it moves
 * past the touch slop, then [onMove] with the pan (pixels), the zoom and the
 * turn (degrees) since it started, then [onEnd]. A gesture something above
 * took (a consumed change) is left to it.
 */
internal suspend fun PointerInputScope.detectTotalTransform(
    onStart: () -> Unit,
    onMove: (pan: Offset, zoom: Float, rotation: Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var moving = false
        val touchSlop = viewConfiguration.touchSlop
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                zoom *= event.calculateZoom()
                rotation += event.calculateRotation()
                pan += event.calculatePan()
                if (!moving) {
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    if (zoomMotion > touchSlop || rotationMotion > touchSlop || pan.getDistance() > touchSlop) {
                        moving = true
                        onStart()
                    }
                }
                if (moving) {
                    onMove(pan, zoom, rotation)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        if (moving) onEnd()
    }
}

/** How close (in pixels, for [density]) something must come to a line to stick to it. */
internal fun snapThreshold(density: androidx.compose.ui.unit.Density): Float = with(density) { SNAP_DISTANCE.toPx() }

private val SNAP_DISTANCE = 8.dp
