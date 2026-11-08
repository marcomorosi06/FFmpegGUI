@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The handle that turns what is selected on the picture (a caption, a picture,
 * a video): a knob on a short stalk beyond its top, turning with it. Dragged,
 * the thing turns to face the finger about its pivot, caught by whole steps
 * (multiples of 45 degrees, SnapScene.turn) with a tick; the angle shows in a
 * pill beside the knob meanwhile. A double tap stands it upright again.
 */

/**
 * The turning handle over the canvas this Box fills. [pivot] is the point it
 * turns about and [reach] how far from it the knob's middle is (both in
 * canvas pixels, read while drawing: a moving thing does not recompose it),
 * [degrees] its turn now (clockwise). [onTurn] gets each new turn, [onEnd]
 * the end of a drag.
 */
@Composable
internal fun BoxScope.RotateHandle(
    pivot: () -> Offset,
    reach: () -> Float,
    degrees: () -> Float,
    scene: SnapScene?,
    onTurn: (Float) -> Unit,
    onEnd: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val knob = 40.dp
    val knobPx = with(density) { knob.toPx() }
    val currentOnTurn by rememberUpdatedState(onTurn)
    val currentOnEnd by rememberUpdatedState(onEnd)
    val currentScene by rememberUpdatedState(scene)
    var dragging by remember { mutableStateOf(false) }
    // The canvas's size: a knob that would fall outside it goes below the thing instead.
    val area = remember { floatArrayOf(0f, 0f) }
    // Below while a drag lasts, if it started there (the knob does not jump under the finger).
    var heldBelow by remember { mutableStateOf<Boolean?>(null) }
    val grow by animateFloatAsState(if (dragging) 1.15f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "rotateKnob")

    /** The knob's middle at [r] from the pivot, along the thing's up. */
    fun centreAt(r: Float): Offset {
        val p = pivot()
        val radians = Math.toRadians(degrees().toDouble())
        return Offset(p.x + (r * sin(radians)).toFloat(), p.y - (r * cos(radians)).toFloat())
    }

    /** Whether the knob is below the thing: above, it would leave the canvas. */
    fun below(): Boolean = heldBelow ?: run {
        val above = centreAt(reach())
        val margin = knobPx / 2f
        area[0] > 0f && (above.x < margin || above.y < margin || above.x > area[0] - margin || above.y > area[1] - margin)
    }

    /** Where the knob's middle is now, on the canvas. */
    fun knobCentre(): Offset = centreAt(if (below()) -reach() else reach())

    // The stalk: from the thing's edge to the knob, turning with it.
    val line = scheme.primary
    Canvas(modifier = Modifier.matchParentSize()) {
        area[0] = size.width
        area[1] = size.height
        val p = pivot()
        val radians = Math.toRadians(degrees().toDouble())
        val r = if (below()) -reach() else reach()
        val sign = if (r < 0f) -1f else 1f
        val from = sign * (kotlin.math.abs(r) - knobPx / 2f - 18.dp.toPx())
        val to = sign * (kotlin.math.abs(r) - knobPx / 2f)
        if (kotlin.math.abs(r) - knobPx / 2f - 18.dp.toPx() > 0f) {
            val sx = sin(radians).toFloat()
            val cy = cos(radians).toFloat()
            drawLine(
                color = line,
                start = Offset(p.x + from * sx, p.y - from * cy),
                end = Offset(p.x + to * sx, p.y - to * cy),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    val description = stringResource(R.string.rotate_handle)
    Box(
        modifier = Modifier
            .offset {
                val c = knobCentre()
                IntOffset((c.x - knobPx / 2f).roundToInt(), (c.y - knobPx / 2f).roundToInt())
            }
            .size(knob)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        currentOnTurn(0f)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        heldBelow = below()
                        dragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        // The finger on the canvas: the knob's place now, and where in it the finger is.
                        val c = knobCentre()
                        val finger = Offset(c.x - knobPx / 2f + change.position.x, c.y - knobPx / 2f + change.position.y)
                        val p = pivot()
                        val towards = Math.toDegrees(atan2((finger.x - p.x).toDouble(), (p.y - finger.y).toDouble())).toFloat()
                        // A knob below points the thing's down at the finger.
                        val raw = ((((if (heldBelow == true) towards + 180f else towards) % 360f) + 540f) % 360f) - 180f
                        currentOnTurn(currentScene?.turn(raw, haptic) ?: raw)
                    },
                    onDragEnd = {
                        dragging = false
                        heldBelow = null
                        currentScene?.clear()
                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        currentOnEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        heldBelow = null
                        currentScene?.clear()
                        currentOnEnd()
                    },
                )
            },
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie6Sided,
            to = MaterialShapes.Circle,
            morphed = dragging,
            color = scheme.primary,
            rotationDegrees = degrees(),
            modifier = Modifier.matchParentSize(),
        ) {
            Icon(
                imageVector = Icons.Rounded.RotateRight,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    // While it turns: the angle, in a pill beside the knob.
    if (dragging) {
        Text(
            text = stringResource(R.string.value_degrees, Math.round(((degrees() % 360f) + 540f) % 360f - 180f)),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimaryContainer,
            modifier = Modifier
                .offset {
                    val c = knobCentre()
                    IntOffset((c.x + knobPx * 0.75f).roundToInt(), (c.y - knobPx / 2f).roundToInt())
                }
                .clip(RoundedCornerShape(50))
                .background(scheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
