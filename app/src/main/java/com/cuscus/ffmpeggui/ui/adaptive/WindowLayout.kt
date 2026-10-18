package com.cuscus.ffmpeggui.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * How big the app's window is, so that every screen can lay itself out for it:
 * a phone held upright (one column), a phone on its side (short: what matters
 * goes side by side), a tablet or a monitor (room for two panes, columns kept
 * to a readable width).
 *
 * The window is measured once, at the root (see [ProvideWindowLayout]): its
 * size without the system bars, without the keyboard (so that opening it never
 * switches a screen's layout and takes the focus away from a field).
 */

/** The window's usable size, and what kind of screen that makes it. */
data class WindowLayout(val width: Dp, val height: Dp) {

    /** Narrower than a small tablet: one column. */
    val isCompact: Boolean get() = width < MEDIUM_WIDTH

    /** Wider than it is tall and short: a phone on its side. */
    val isShortLandscape: Boolean get() = width > height && height < SHORT_HEIGHT

    /** Wider than it is tall. */
    val isLandscape: Boolean get() = width > height

    /** A large tablet or a monitor, or a tablet on its side. */
    val isExpanded: Boolean get() = width >= EXPANDED_WIDTH

    /**
     * Two panes fit side by side: a tablet or a monitor, or a phone on its side
     * (there the height is what is short, and two panes use the width).
     */
    val twoPanes: Boolean get() = isExpanded || (isLandscape && width >= MEDIUM_WIDTH)

    /** Room on each side that keeps a column at most [max] wide in the middle, and never less than [min]. */
    fun sidePadding(max: Dp, min: Dp = 16.dp): Dp = maxOf(min, (width - max) / 2)

    companion object {
        val MEDIUM_WIDTH = 600.dp
        val EXPANDED_WIDTH = 840.dp
        val SHORT_HEIGHT = 480.dp

        /** The widest a column of text and settings reads well at. */
        val READABLE_WIDTH = 720.dp
    }
}

/** The window this screen is laid out for (a phone held upright until measured). */
val LocalWindowLayout = staticCompositionLocalOf { WindowLayout(400.dp, 800.dp) }

/** Measures the space it is given and tells everything inside it (see [LocalWindowLayout]). */
@Composable
fun ProvideWindowLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val layout = remember(maxWidth, maxHeight) { WindowLayout(maxWidth, maxHeight) }
        CompositionLocalProvider(LocalWindowLayout provides layout) { content() }
    }
}

/**
 * A column that scrolls when its content is taller than the room it has and
 * otherwise sits in the middle of it (or at the top, unless [centered]): one
 * pane of a screen laid out side by side.
 */
@Composable
fun ScrollPane(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    centered: Boolean = true,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val room = maxHeight
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = if (centered) Arrangement.spacedBy(spacing, Alignment.CenterVertically) else Arrangement.spacedBy(spacing),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = room)
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Shrinks the content, keeping its proportions, when it is taller than the room
 * it has (a drawing made for a phone held upright, on a phone on its side); at
 * its own size otherwise. It takes the room it is drawn in.
 */
fun Modifier.scaledToFit(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val room = constraints.maxHeight
    val scale = if (constraints.hasBoundedHeight && placeable.height > room && placeable.height > 0) {
        room.toFloat() / placeable.height
    } else {
        1f
    }
    val height = (placeable.height * scale).roundToInt().coerceAtLeast(constraints.minHeight)
    layout(placeable.width, height) {
        placeable.placeWithLayer(0, (height - placeable.height) / 2) {
            scaleX = scale
            scaleY = scale
        }
    }
}
