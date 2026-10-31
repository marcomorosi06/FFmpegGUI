@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.core.model.LayerBlend
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTile
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily

/*
 * How a sticker or a shape mixes with the picture under it. Every mode shows
 * itself on its tile: an orange-to-blue backdrop with a magenta-to-teal disc
 * mixed onto it by that very mode, as Android composites the layer in the
 * preview; the chosen one fills the hero card above, in a badge that morphs
 * into the mode's own shape.
 */

/** The mode as Android draws it: the same separable formula the export's `blend` runs. */
internal fun LayerBlend.composeMode(): BlendMode = when (this) {
    LayerBlend.NORMAL -> BlendMode.SrcOver
    LayerBlend.MULTIPLY -> BlendMode.Multiply
    LayerBlend.SCREEN -> BlendMode.Screen
    LayerBlend.OVERLAY -> BlendMode.Overlay
    LayerBlend.DARKEN -> BlendMode.Darken
    LayerBlend.LIGHTEN -> BlendMode.Lighten
    LayerBlend.COLOR_DODGE -> BlendMode.ColorDodge
    LayerBlend.COLOR_BURN -> BlendMode.ColorBurn
    LayerBlend.HARD_LIGHT -> BlendMode.Hardlight
    LayerBlend.DIFFERENCE -> BlendMode.Difference
    LayerBlend.EXCLUSION -> BlendMode.Exclusion
    LayerBlend.ADD -> BlendMode.Plus
}

/** The name of a mode. */
@StringRes
internal fun LayerBlend.labelRes(): Int = when (this) {
    LayerBlend.NORMAL -> R.string.blend_normal
    LayerBlend.MULTIPLY -> R.string.blend_multiply
    LayerBlend.SCREEN -> R.string.blend_screen
    LayerBlend.OVERLAY -> R.string.blend_overlay
    LayerBlend.DARKEN -> R.string.blend_darken
    LayerBlend.LIGHTEN -> R.string.blend_lighten
    LayerBlend.COLOR_DODGE -> R.string.blend_color_dodge
    LayerBlend.COLOR_BURN -> R.string.blend_color_burn
    LayerBlend.HARD_LIGHT -> R.string.blend_hard_light
    LayerBlend.DIFFERENCE -> R.string.blend_difference
    LayerBlend.EXCLUSION -> R.string.blend_exclusion
    LayerBlend.ADD -> R.string.blend_add
}

/** What a mode does, in a line. */
@StringRes
private fun LayerBlend.hintRes(): Int = when (this) {
    LayerBlend.NORMAL -> R.string.blend_normal_hint
    LayerBlend.MULTIPLY -> R.string.blend_multiply_hint
    LayerBlend.SCREEN -> R.string.blend_screen_hint
    LayerBlend.OVERLAY -> R.string.blend_overlay_hint
    LayerBlend.DARKEN -> R.string.blend_darken_hint
    LayerBlend.LIGHTEN -> R.string.blend_lighten_hint
    LayerBlend.COLOR_DODGE -> R.string.blend_color_dodge_hint
    LayerBlend.COLOR_BURN -> R.string.blend_color_burn_hint
    LayerBlend.HARD_LIGHT -> R.string.blend_hard_light_hint
    LayerBlend.DIFFERENCE -> R.string.blend_difference_hint
    LayerBlend.EXCLUSION -> R.string.blend_exclusion_hint
    LayerBlend.ADD -> R.string.blend_add_hint
}

/** Each mode's own shape: the chosen tile and the hero badge morph into it. */
private fun LayerBlend.shape(): RoundedPolygon {
    val shapes = listOf(
        MaterialShapes.Circle,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.SoftBurst,
        MaterialShapes.Flower,
        MaterialShapes.Gem,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Cookie7Sided,
    )
    return shapes[ordinal % shapes.size]
}

/**
 * The blend panel: the chosen mode large, what it does, then every mode on a
 * tile that shows it. [onBlend] sets one (one undo step each).
 */
@Composable
internal fun ImageBlendPanel(blend: LayerBlend, onBlend: (LayerBlend) -> Unit, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // The hero: the chosen mode, large.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(14.dp),
        ) {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = blend.shape(),
                morphed = blend != LayerBlend.NORMAL,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.size(88.dp),
            ) {
                BlendSample(blend)
            }
            Spacer(modifier = Modifier.width(16.dp))
            AnimatedContent(
                targetState = blend,
                transitionSpec = {
                    (scaleIn(motion.fastSpatialSpec(), initialScale = 0.9f) + fadeIn(motion.fastEffectsSpec()))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                label = "blendHero",
                modifier = Modifier.weight(1f),
            ) { mode ->
                Column {
                    Text(
                        text = stringResource(mode.labelRes()),
                        style = MaterialTheme.typography.headlineSmallEmphasized.copy(fontFamily = DisplayFamily),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(mode.hintRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
        // Every mode, four to a row.
        LayerBlend.entries.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                row.forEach { mode ->
                    OptionTile(
                        label = stringResource(mode.labelRes()),
                        selected = mode == blend,
                        onClick = {
                            if (mode != blend) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onBlend(mode)
                        },
                        chosenShape = mode.shape(),
                        enabled = enabled,
                        role = Role.RadioButton,
                        preview = { BlendSample(mode) },
                    )
                }
                // A short last row keeps the tiles where the others are.
                repeat(TILES_PER_ROW - row.size) { Spacer(modifier = Modifier.width(TILE_WIDTH)) }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Title, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(modifier = Modifier.width(8.dp))
            PanelHint(stringResource(R.string.blend_panel_hint))
        }
    }
}

/**
 * [mode] shown: a warm-to-cold backdrop and a disc mixed onto it by the mode,
 * drawn on a layer of its own so that only the two of them mix.
 */
@Composable
private fun BlendSample(mode: LayerBlend) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(SAMPLE_BACK_START, SAMPLE_BACK_MIDDLE, SAMPLE_BACK_END)),
            )
            val radius = size.minDimension * 0.36f
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(SAMPLE_TOP_START, SAMPLE_TOP_END),
                    startY = size.height / 2f - radius,
                    endY = size.height / 2f + radius,
                ),
                radius = radius,
                center = Offset(size.width * 0.58f, size.height * 0.5f),
                blendMode = mode.composeMode(),
            )
        }
    }
}

private const val TILES_PER_ROW = 4

/** Width an OptionTile takes (its badge and the room around it). */
private val TILE_WIDTH = 76.dp

private val SAMPLE_BACK_START = Color(0xFFFF7043)
private val SAMPLE_BACK_MIDDLE = Color(0xFFFFEB3B)
private val SAMPLE_BACK_END = Color(0xFF1E88E5)
private val SAMPLE_TOP_START = Color(0xFFE040FB)
private val SAMPLE_TOP_END = Color(0xFF26A69A)
