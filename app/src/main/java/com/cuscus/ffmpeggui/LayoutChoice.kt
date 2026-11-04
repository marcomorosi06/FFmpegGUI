@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt
import com.cuscus.ffmpeggui.settings.LandscapeLayout

/*
 * Where the player goes on a screen wider than it is tall: the editors' own
 * switch in their top bar (the player hops to the other side with a spring,
 * on a badge that morphs as it does), and the little pictures of each choice
 * the settings show.
 */

/**
 * The player and the tools side by side, half the width each, the player on
 * the right when [playerRight]; without [toolsShown] the player takes it all.
 * Both are always the same two children, so that the player is never built
 * again when it changes side, only laid elsewhere (with the theme's spatial
 * spring, from where it was).
 */
@Composable
internal fun SideBySide(
    playerRight: Boolean,
    toolsShown: Boolean,
    player: @Composable () -> Unit,
    tools: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0: player on the left, 1: on the right; the halves slide across.
    val side by animateFloatAsState(if (playerRight) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "sideBySide")
    Layout(
        modifier = modifier,
        content = {
            Box(modifier = Modifier.clipToBounds()) { player() }
            Box(modifier = Modifier.clipToBounds()) { tools() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val half = if (toolsShown) width / 2 else width
        val fixed = Constraints.fixed(half, height)
        val playerPlaceable = measurables[0].measure(fixed)
        val toolsPlaceable = measurables[1].measure(Constraints.fixed(width - half, height))
        layout(width, height) {
            if (!toolsShown) {
                playerPlaceable.place(0, 0)
            } else {
                val travel = width - half
                playerPlaceable.place((side * travel).roundToInt(), 0)
                toolsPlaceable.place(((1f - side) * half).roundToInt(), 0)
            }
        }
    }
}

/** Whether the editor lays the player beside the tools in a window [landscape] (wider than tall), and on which side. */
internal fun LandscapeLayout.forcesSides(landscape: Boolean): Boolean = landscape && this != LandscapeLayout.AUTO

/**
 * A picture of [layout] for the settings' tiles, in [color]: a landscape
 * window with its player (filled) and its tools (faint).
 */
@Composable
internal fun LayoutSample(layout: LandscapeLayout, color: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (layout) {
                LandscapeLayout.PLAYER_LEFT -> drawLayout(0f, color, color.copy(alpha = 0.35f))
                LandscapeLayout.PLAYER_RIGHT -> drawLayout(1f, color, color.copy(alpha = 0.35f))
                LandscapeLayout.AUTO -> drawStacked(color, color.copy(alpha = 0.35f))
            }
        }
    }
}

/** A window with the player at [side] (0 left, 1 right) and the tools beside it. */
/**
 * The same switch as the leading half of a connected pair (Export the other,
 * see EditorTopBar): a tonal button, its outer end round, squaring up under
 * the finger, the layout drawn in it sliding to the new side.
 */
@Composable
internal fun ConnectedSwapSidesButton(playerRight: Boolean, onSwap: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val side by animateFloatAsState(if (playerRight) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "swapSide")
    val label = stringResource(if (playerRight) R.string.editor_player_to_left else R.string.editor_player_to_right)
    FilledTonalIconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSwap()
        },
        shapes = IconButtonDefaults.shapes(
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            pressedShape = RoundedCornerShape(8.dp),
        ),
        modifier = modifier
            .size(width = 56.dp, height = 48.dp)
            .semantics { contentDescription = label },
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            drawLayout(side, player = scheme.onSecondaryContainer, tools = scheme.onSecondaryContainer.copy(alpha = 0.35f))
        }
    }
}

private fun DrawScope.drawLayout(side: Float, player: Color, tools: Color) {
    val w = size.width
    val h = size.height * 0.72f
    val top = (size.height - h) / 2f
    val gap = w * 0.08f
    val half = (w - gap) / 2f
    val corner = CornerRadius(h * 0.18f)
    val playerLeft = side * (half + gap)
    val toolsLeft = (1f - side) * (half + gap)
    drawRoundRect(tools, Offset(toolsLeft, top), Size(half, h), corner)
    drawRoundRect(player, Offset(playerLeft, top), Size(half, h), corner)
}

/** A window with the player above and the timeline across the bottom (as the window's size suggests). */
private fun DrawScope.drawStacked(player: Color, tools: Color) {
    val w = size.width
    val h = size.height * 0.72f
    val top = (size.height - h) / 2f
    val gap = h * 0.1f
    val playerH = (h - gap) * 0.6f
    val corner = CornerRadius(h * 0.14f)
    drawRoundRect(player, Offset(w * 0.18f, top), Size(w * 0.64f, playerH), corner)
    drawRoundRect(tools, Offset(0f, top + playerH + gap), Size(w, h - playerH - gap), corner)
}
