package com.cuscus.ffmpeggui.svgedit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * A row of toolbar buttons that scrolls sideways and says so: its edges fade
 * where more buttons are hidden (the last one is never covered), and the
 * first time it shows ([nudge]) it slides a little and springs back, so
 * a finger learns it can be swiped.
 */
@Composable
internal fun HintedScrollRow(
    nudge: Boolean,
    onNudged: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val motion = MaterialTheme.motionScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    // Effects springs: an alpha never overshoots.
    val startFade by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, motion.fastEffectsSpec(), label = "fadeStart")
    val endFade by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, motion.fastEffectsSpec(), label = "fadeEnd")

    if (nudge) {
        LaunchedEffect(Unit) {
            // Int.MAX_VALUE until the row has been measured.
            val max = snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE }
            if (max > 0 && scroll.value == 0) {
                delay(NUDGE_DELAY_MS)
                val peek = minOf(max, with(density) { NUDGE_DISTANCE.roundToPx() })
                // Spatial springs: the slide may overshoot a little, which is what makes it read as a nudge.
                scroll.animateScrollTo(peek, motion.defaultSpatialSpec())
                scroll.animateScrollTo(0, motion.defaultSpatialSpec())
            }
            onNudged()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content,
            modifier = Modifier
                .weight(1f, fill = false)
                // Offscreen, so the fade cuts into the buttons and not into the toolbar under them.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = EDGE_FADE.toPx().coerceAtMost(size.width / 2f)
                    // In a right-to-left layout the hidden buttons are on the left.
                    val left = if (rtl) endFade else startFade
                    val right = if (rtl) startFade else endFade
                    if (left > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 1f - left),
                                1f to Color.Black,
                                startX = 0f,
                                endX = fade,
                            ),
                            size = Size(fade, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    if (right > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - right),
                                startX = size.width - fade,
                                endX = size.width,
                            ),
                            topLeft = Offset(size.width - fade, 0f),
                            size = Size(fade, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .horizontalScroll(scroll),
        )
    }
}

/** How wide the fade over a hidden edge is. */
private val EDGE_FADE = 28.dp

/** How far the first-time nudge slides. */
private val NUDGE_DISTANCE = 56.dp

/** Wait before the nudge: the toolbar has just sprung in. */
private const val NUDGE_DELAY_MS = 600L

