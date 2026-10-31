@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Pinch to zoom the picture editor's canvas: two fingers on the picture zoom
 * in on it (around where they are) and move it, so that a drawing or a brush
 * can go exactly where it should. Everything on the canvas zooms with it (the
 * picture, texts, stickers, the drawing, the crop frame), and touches land
 * where they are seen. Two fingers on a sticker still size and turn the
 * sticker; a stroke being drawn when a second finger comes is dropped (it was
 * the start of a pinch, not a line). A pill over the canvas says how far it
 * is zoomed and springs it back when tapped.
 */

/** How far the canvas is zoomed ([scale], 1 = it fits) and moved ([x], [y], in pixels of the stage). */
@Stable
internal class StageZoom {
    var scale by mutableFloatStateOf(1f)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)

    val zoomed: Boolean get() = scale > 1.01f

    /**
     * Zooms by [factor] around [centroid] and moves by [pan] (pixels of a stage
     * [width] x [height], whose canvas is [contentWidth] x [contentHeight] at
     * scale 1, centred): the point under the fingers stays under them, and the
     * canvas never leaves the stage entirely.
     */
    fun transform(factor: Float, centroid: Offset, pan: Offset, width: Float, height: Float, contentWidth: Float, contentHeight: Float) {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = next / scale
        val cx = centroid.x - width / 2f
        val cy = centroid.y - height / 2f
        var nx = cx - (cx - x) * k + pan.x
        var ny = cy - (cy - y) * k + pan.y
        // At least a fifth of the canvas stays in sight.
        val limitX = maxOf(0f, (contentWidth * next - width) / 2f) + width * 0.4f
        val limitY = maxOf(0f, (contentHeight * next - height) / 2f) + height * 0.4f
        if (next <= 1f) {
            nx = 0f
            ny = 0f
        }
        scale = next
        x = nx.coerceIn(-limitX, limitX)
        y = ny.coerceIn(-limitY, limitY)
    }

    /** Back to the canvas that fits, with the theme's default spatial spring. */
    suspend fun reset(motion: MotionScheme) {
        val fromScale = scale
        val fromX = x
        val fromY = y
        animate(0f, 1f, animationSpec = motion.defaultSpatialSpec()) { t, _ ->
            scale = fromScale + (1f - fromScale) * t
            x = fromX * (1f - t)
            y = fromY * (1f - t)
        }
        scale = 1f
        x = 0f
        y = 0f
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 8f
    }
}

/** The stage's zoom, kept while the editor is open. */
@Composable
internal fun rememberStageZoom(): StageZoom = remember { StageZoom() }

/**
 * The canvas inside, zoomed and moved as [zoom] says (around its centre: the
 * stage lays it out centred).
 */
internal fun Modifier.zoomedBy(zoom: StageZoom): Modifier = graphicsLayer {
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.x
    translationY = zoom.y
}

/**
 * Two fingers on the stage zoom and move [zoom]'s canvas, [contentWidth] x
 * [contentHeight] at scale 1, only when nothing on the canvas takes them:
 *
 *  - a layer that moved (a sticker or a text dragged, pinched or turned, the
 *    crop frame) consumes what its fingers do; seen once during the gesture
 *    (its first touch apart, which tap detectors take), the gesture is the
 *    layer's to the end, even if a second finger lands beside it;
 *  - the zoom starts only past a slop larger than the layers' own, so that a
 *    layer under the fingers always claims them first (layers see every
 *    move before the stage does).
 */
internal fun Modifier.stageZoomGestures(zoom: StageZoom, contentWidth: () -> Float, contentHeight: () -> Float): Modifier =
    pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop * ZOOM_SLOP_FACTOR
            var zooming = false
            var owned = false
            var pending = 1f
            var travel = Offset.Zero
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                // A layer took a move: the gesture is its own.
                if (!zooming && event.changes.any { it.isConsumed && !it.changedToDownIgnoreConsumed() }) owned = true
                if (owned) continue
                if (pressed.size < 2) {
                    if (zooming) break
                    continue
                }
                val factor = event.calculateZoom()
                val pan = event.calculatePan()
                if (!zooming) {
                    // Not yet: past the slop, measured as the layers measure theirs.
                    pending *= factor
                    travel += pan
                    val spread = event.calculateCentroidSize(useCurrent = false)
                    if (abs(1f - pending) * spread <= slop && travel.getDistance() <= slop) continue
                    zooming = true
                }
                val centroid = event.calculateCentroid(useCurrent = true)
                if (centroid != Offset.Unspecified) {
                    zoom.transform(factor, centroid, pan, size.width.toFloat(), size.height.toFloat(), contentWidth(), contentHeight())
                }
                event.changes.forEach { it.consume() }
            }
        }
    }

/** How much farther than a layer's the fingers go before the stage zooms. */
private const val ZOOM_SLOP_FACTOR = 1.6f

/**
 * How far the canvas is zoomed, as a pill: tapped, it springs the canvas back
 * to fit. Shown only while zoomed.
 */
@Composable
internal fun ZoomPill(zoom: StageZoom, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = zoom.zoomed,
        enter = scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f) + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .springPress(interaction, pressedScale = 0.88f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f))
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch { zoom.reset(motion) }
                }
                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.value_percent, (zoom.scale * 100).roundToInt()),
                style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.zoom_fit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
            )
        }
    }
}
