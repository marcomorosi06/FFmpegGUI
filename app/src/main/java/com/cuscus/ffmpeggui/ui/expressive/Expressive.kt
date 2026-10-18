@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.ui.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.min

/*
 * The Material 3 Expressive pieces the app's screens share: shapes from
 * MaterialShapes (and morphs between them) usable anywhere a Shape is, a
 * spring "squish" on press, the morphing play button and a connected button
 * group for picking one option. Motion always comes from the theme's
 * MotionScheme, so every screen moves the same way.
 */

/**
 * A [RoundedPolygon] (one of [MaterialShapes], say) as a Compose [Shape]: centred
 * and scaled to fit the bounds, its proportions kept, optionally turned by
 * [rotationDegrees].
 */
class PolygonShape(
    private val polygon: RoundedPolygon,
    private val rotationDegrees: Float = 0f,
) : Shape {
    // The polygon as a path and its bounds, worked out once: only the fit changes with the size.
    private val unitPath by lazy(LazyThreadSafetyMode.NONE) { polygon.toPath() }
    private val bounds by lazy(LazyThreadSafetyMode.NONE) { polygon.calculateBounds() }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(fitPath(android.graphics.Path(unitPath), bounds, size, rotationDegrees))

    // Equal shapes are the same shape: a shape made again on recomposition does not make
    // the outline again.
    override fun equals(other: Any?): Boolean =
        other is PolygonShape && other.polygon == polygon && other.rotationDegrees == rotationDegrees

    override fun hashCode(): Int = polygon.hashCode() * 31 + rotationDegrees.hashCode()
}

/**
 * The shape [progress] of the way through [morph] (0 = its start, 1 = its end).
 * A spring may carry [progress] a little past either end, which gives the
 * expressive overshoot.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(fitPath(morph.toPath(progress), morph.calculateBounds(), size, rotationDegrees))

    override fun equals(other: Any?): Boolean =
        other is MorphShape && other.morph === morph && other.progress == progress && other.rotationDegrees == rotationDegrees

    override fun hashCode(): Int = (System.identityHashCode(morph) * 31 + progress.hashCode()) * 31 + rotationDegrees.hashCode()
}

/**
 * [path], whose bounds are [bounds] (left, top, right, bottom), turned by
 * [rotationDegrees] about its centre, then scaled to fit [size] and centred in it.
 * The fit is measured after the turn, so a turned shape never spills out.
 */
private fun fitPath(path: android.graphics.Path, bounds: FloatArray, size: Size, rotationDegrees: Float): androidx.compose.ui.graphics.Path {
    val matrix = android.graphics.Matrix()
    matrix.setTranslate(-(bounds[0] + bounds[2]) / 2f, -(bounds[1] + bounds[3]) / 2f)
    if (rotationDegrees != 0f) matrix.postRotate(rotationDegrees)
    path.transform(matrix)
    val turned = android.graphics.RectF()
    path.computeBounds(turned, true)
    val scale = min(
        size.width / turned.width().coerceAtLeast(MIN_EXTENT),
        size.height / turned.height().coerceAtLeast(MIN_EXTENT),
    )
    matrix.setTranslate(-turned.centerX(), -turned.centerY())
    matrix.postScale(scale, scale)
    matrix.postTranslate(size.width / 2f, size.height / 2f)
    path.transform(matrix)
    return path.asComposePath()
}

private const val MIN_EXTENT = 0.0001f

/**
 * Shrinks a little while [interactionSource] is pressed and springs back, with
 * the theme's fast spatial spring.
 */
@Composable
fun Modifier.springPress(interactionSource: MutableInteractionSource, pressedScale: Float = 0.92f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "springPress",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A box clipped to [from], morphing into [to] while [morphed] is true, filled
 * with [color]. The morph follows the theme's spatial spring.
 */
@Composable
fun MorphingContainer(
    from: RoundedPolygon,
    to: RoundedPolygon,
    morphed: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val morph = remember(from, to) { sharedMorph(from, to) }
    val progress = animateFloatAsState(
        targetValue = if (morphed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "morphProgress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // The morph is read while drawing: it moves without recomposing what is inside.
            .graphicsLayer {
                shape = MorphShape(morph, progress.value, rotationDegrees)
                clip = true
            }
            .background(color),
        content = content,
    )
}

/**
 * The morph from [from] to [to], made once for the app: matching two shapes'
 * features takes a while, and a screen full of badges asks for the same few
 * morphs many times. The shapes are told apart by identity (MaterialShapes
 * hands out the same instances). Main thread only.
 */
private fun sharedMorph(from: RoundedPolygon, to: RoundedPolygon): Morph {
    val key = MorphKey(from, to)
    MORPHS[key]?.let { return it }
    if (MORPHS.size >= MAX_SHARED_MORPHS) MORPHS.clear()
    return Morph(from, to).also { MORPHS[key] = it }
}

private class MorphKey(val from: RoundedPolygon, val to: RoundedPolygon) {
    override fun equals(other: Any?): Boolean = other is MorphKey && other.from === from && other.to === to
    override fun hashCode(): Int = System.identityHashCode(from) * 31 + System.identityHashCode(to)
}

private val MORPHS = HashMap<MorphKey, Morph>()
private const val MAX_SHARED_MORPHS = 64

/**
 * Play / pause. Paused it is a nine-sided cookie that invites a tap; playing it
 * relaxes into a circle. It squishes under the finger.
 */
@Composable
fun MorphingPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    playDescription: String,
    pauseDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val description = if (isPlaying) pauseDescription else playDescription
    MorphingContainer(
        from = MaterialShapes.Cookie9Sided,
        to = MaterialShapes.Circle,
        morphed = isPlaying,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .springPress(interaction, pressedScale = 0.85f)
            .size(size)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** Up to this many options share the full width; more scroll sideways. */
private const val MAX_FILLED_OPTIONS = 5

/**
 * Picks one of [options]: a connected button group, the selected button
 * filled and rounder. [label] gives the text of each option.
 */
@Composable
fun <T> ConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val fill = options.size <= MAX_FILLED_OPTIONS
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = if (fill) {
            modifier.fillMaxWidth()
        } else {
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        },
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    // Tapping the chosen option again keeps it chosen.
                    onSelect(option)
                },
                shapes = connectedShapes(index, options.size),
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 10.dp),
                // Announced as one choice among several, as in the official sample.
                modifier = (if (fill) Modifier.weight(1f) else Modifier).semantics { role = Role.RadioButton },
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Shapes of the button at [index] of [count] in a connected group: round outer ends. */
@Composable
private fun connectedShapes(index: Int, count: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}
