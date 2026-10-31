@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTile
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The panel of a shape layer: the shape large, drawn live by the very path its
 * picture is made from (ShapePaths), then what it is, how it is filled, its
 * outline and its proportions. The picture on the video follows a moment
 * later, once it has been drawn again.
 */

/** A change of a shape, as one undo step per key (null: a step of its own). */
internal typealias ShapeChange = (String?, (ShapeLayer) -> ShapeLayer) -> Unit

/** Colours offered for fills and outlines. */
private val SHAPE_COLORS = listOf(
    ShapeLayer.DEFAULT_FILL, 0xFFFFFFFFL, 0xFF000000L, 0xFF263238L, 0xFFFF5252L, 0xFFFFAB40L,
    0xFFFFEB3BL, 0xFF69F0AEL, 0xFF40C4FFL, 0xFF536DFEL, 0xFFE040FBL, 0xFFFF80ABL,
)

@Composable
internal fun ShapeLayerPanel(shape: ShapeLayer, onShape: ShapeChange, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ShapeHero(shape)

        // What it is: every kind on a tile, drawn as it is.
        Text(stringResource(R.string.shape_kind_title), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
        ShapeKind.entries.chunked(KINDS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                row.forEach { kind ->
                    OptionTile(
                        label = stringResource(kind.labelRes),
                        selected = kind == shape.kind,
                        onClick = {
                            if (kind != shape.kind) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onShape(null) { it.copy(kind = kind) }
                        },
                        chosenShape = MaterialShapes.Circle,
                        enabled = enabled,
                        role = Role.RadioButton,
                        preview = { color -> KindSample(kind, color.toArgb().toLong() and 0xFFFFFFFFL) },
                    )
                }
                repeat(KINDS_PER_ROW - row.size) { Spacer(modifier = Modifier.width(TILE_WIDTH)) }
            }
        }

        // How it is filled.
        PanelCard {
            Text(stringResource(R.string.shape_fill_title), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
            ConnectedChoiceGroup(
                options = ShapeFill.entries,
                selected = shape.fill,
                label = { stringResource(it.labelRes) },
                onSelect = { fill -> onShape(null) { it.copy(fill = fill) } },
                enabled = enabled,
            )
            AnimatedVisibility(
                visible = shape.fill != ShapeFill.NONE,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (shape.fill == ShapeFill.GRADIENT) PanelHint(stringResource(R.string.shape_gradient_from))
                    ColorSwatchRow(colors = SHAPE_COLORS, selected = shape.fillColor, onSelect = { c -> onShape(null) { it.copy(fillColor = c) } })
                }
            }
            AnimatedVisibility(
                visible = shape.fill == ShapeFill.GRADIENT,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelHint(stringResource(R.string.shape_gradient_to))
                    ColorSwatchRow(colors = SHAPE_COLORS, selected = shape.gradientColor, onSelect = { c -> onShape(null) { it.copy(gradientColor = c) } })
                    ValueSlider(
                        label = stringResource(R.string.shape_gradient_angle),
                        valueText = stringResource(R.string.value_degrees, shape.gradientAngle.roundToInt()),
                        value = shape.gradientAngle,
                        onValueChange = { v -> onShape("angle") { it.copy(gradientAngle = v) } },
                        valueRange = 0f..360f,
                        icon = Icons.Rounded.Explore,
                        defaultValue = 90f,
                        steps = 23,
                        enabled = enabled,
                    )
                }
            }
        }

        // Its outline.
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.shape_outline),
                valueText = if (shape.outlined) {
                    stringResource(R.string.value_percent, (shape.outlineWidth / ShapeLayer.MAX_OUTLINE * 100).roundToInt())
                } else {
                    stringResource(R.string.overlay_off)
                },
                value = shape.outlineWidth,
                onValueChange = { v -> onShape("outline") { it.copy(outlineWidth = v) } },
                valueRange = 0f..ShapeLayer.MAX_OUTLINE,
                icon = Icons.Rounded.BorderOuter,
                defaultValue = 0f,
                enabled = enabled,
            )
            AnimatedVisibility(
                visible = shape.outlined,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                ColorSwatchRow(colors = SHAPE_COLORS, selected = shape.outlineColor, onSelect = { c -> onShape(null) { it.copy(outlineColor = c) } })
            }
        }

        // Its proportions and corners.
        PanelCard {
            // Wider and narrower alike: the slider runs on the logarithm of the shape.
            val position = (ln(shape.shownAspect) / ln(ShapeLayer.MAX_ASPECT)).coerceIn(-1f, 1f)
            ValueSlider(
                label = stringResource(R.string.shape_proportions),
                valueText = aspectText(shape.shownAspect),
                value = position,
                onValueChange = { v -> onShape("aspect") { it.copy(aspect = ShapeLayer.MAX_ASPECT.pow(v)) } },
                valueRange = -1f..1f,
                icon = Icons.Rounded.AspectRatio,
                defaultValue = 0f,
                enabled = enabled,
            )
            AnimatedVisibility(
                visible = shape.kind.rounds,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                ValueSlider(
                    label = stringResource(R.string.shape_corners),
                    valueText = stringResource(R.string.value_percent, (shape.roundness * 100).roundToInt()),
                    value = shape.roundness,
                    onValueChange = { v -> onShape("round") { it.copy(roundness = v) } },
                    valueRange = 0f..1f,
                    icon = Icons.Rounded.RoundedCorner,
                    defaultValue = 0.25f,
                    enabled = enabled,
                )
            }
        }
        PanelHint(stringResource(R.string.shape_hint))
    }
}

/** "1.50 : 1" (wider) or "1 : 1.50" (taller). */
private fun aspectText(aspect: Float): String = when {
    aspect >= 1f -> String.format(java.util.Locale.US, "%.2f : 1", aspect)
    else -> String.format(java.util.Locale.US, "1 : %.2f", 1f / aspect)
}

/**
 * The shape large, on a tonal stage: it pops each time it changes, inside a
 * Material shape that morphs along.
 */
@Composable
private fun ShapeHero(shape: ShapeLayer) {
    var bump by remember { mutableStateOf(false) }
    val pop = remember { Animatable(1f) }
    val popSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(shape.kind, shape.fill) {
        bump = !bump
        pop.snapTo(0.85f)
        pop.animateTo(1f, popSpec)
    }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie12Sided,
            to = MaterialShapes.SoftBurst,
            morphed = bump,
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f),
            modifier = Modifier.size(HERO_HEIGHT - 16.dp),
        ) {}
        // As wide as it is, fitted into the stage.
        val aspect = shape.shownAspect
        val width = minOf(maxWidth * 0.8f, maxHeight * 0.72f * aspect)
        Box(
            modifier = Modifier
                .size(width, width / aspect)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas -> ShapePaths.draw(canvas.nativeCanvas, shape, size.width, size.height) }
            }
        }
        Text(
            text = stringResource(shape.kind.labelRes),
            style = MaterialTheme.typography.titleMediumEmphasized.copy(fontFamily = DisplayFamily),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp),
        )
    }
}

/** [kind] in [color], filling a tile's badge. */
@Composable
private fun KindSample(kind: ShapeKind, color: Long) {
    val sample = remember(kind, color) { ShapeLayer(kind = kind, fillColor = color, roundness = 0.25f) }
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        drawIntoCanvas { canvas -> ShapePaths.draw(canvas.nativeCanvas, sample, size.width, size.height) }
    }
}

private const val KINDS_PER_ROW = 4
private val TILE_WIDTH = 76.dp
private val HERO_HEIGHT = 168.dp
