@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.onboarding

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/*
 * How the welcome moves. Every page is laid out in layers: as the pages are
 * swiped, each layer slides at its own speed (the title first, what is below it
 * a little later), so a page arrives in a short cascade rather than as one
 * block; the first time a page comes into view its pieces pop in one after the
 * other, on the theme's slow spatial spring. Colours never jump: a change of
 * theme flows into the new colours. When the system's animations are off
 * (Developer options, or the accessibility setting), the loops stand still and
 * the pieces are simply there.
 */

/** What a page needs to move: the pager, which page it is, whether it is the one in view. */
internal class PageMotion(val pager: PagerState, val page: Int, val active: Boolean)

/** True when the system's animations are off. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
}

/** An angle going round, 0..360, once every [periodMs]; still when animations are off. */
@Composable
internal fun rememberTurn(periodMs: Int, label: String): State<Float> {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = label,
    )
}

/** A value going 0..1 and starting over, once every [periodMs]; held at [still] when animations are off. */
@Composable
internal fun rememberLoop(periodMs: Int, label: String, still: Float = 0.5f): State<Float> {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = if (reduced) still else 0f,
        targetValue = if (reduced) still else 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = label,
    )
}

/**
 * Slides with the swipe at its own [depth] (0 = with the page, more = later),
 * and fades as it goes. Read in the draw phase only: swiping recomposes nothing.
 */
internal fun Modifier.parallax(pager: PagerState, page: Int, depth: Float): Modifier = graphicsLayer {
    val offset = pager.getOffsetDistanceInPages(page)
    translationX = offset * size.width * depth
    alpha = 1f - (abs(offset) * (0.5f + depth)).coerceIn(0f, 1f)
}

/**
 * Pops in (fades, grows from a little smaller and rises) the first time [active]
 * is true, [order] steps after the first piece of the page.
 */
@Composable
internal fun Modifier.popIn(active: Boolean, order: Int): Modifier {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(active) {
        if (!active || progress.targetValue >= 1f) return@LaunchedEffect
        if (reduced) {
            progress.snapTo(1f)
        } else {
            delay(order * STAGGER_MS)
            progress.animateTo(1f, spec)
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val scale = 0.84f + 0.16f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * 40.dp.toPx()
    }
}

/** The usual entrance of piece [order] of a page: its own layer of the swipe, then the pop. */
@Composable
internal fun Modifier.enter(motion: PageMotion, order: Int): Modifier =
    this
        .parallax(motion.pager, motion.page, (order * 0.07f).coerceAtMost(0.45f))
        .popIn(motion.active, order)

/**
 * The theme around [content] with its colours flowing: when the theme mode,
 * the wallpaper colours or black backgrounds change on the welcome, every role
 * eases into its new colour instead of jumping.
 */
@Composable
internal fun FlowingColors(content: @Composable () -> Unit) {
    val target = MaterialTheme.colorScheme
    MaterialExpressiveTheme(
        colorScheme = flowing(target),
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
private fun flowing(target: ColorScheme): ColorScheme {
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()

    @Composable
    fun ease(color: Color): Color = animateColorAsState(color, spec, label = "flow").value

    return target.copy(
        primary = ease(target.primary),
        onPrimary = ease(target.onPrimary),
        primaryContainer = ease(target.primaryContainer),
        onPrimaryContainer = ease(target.onPrimaryContainer),
        inversePrimary = ease(target.inversePrimary),
        secondary = ease(target.secondary),
        onSecondary = ease(target.onSecondary),
        secondaryContainer = ease(target.secondaryContainer),
        onSecondaryContainer = ease(target.onSecondaryContainer),
        tertiary = ease(target.tertiary),
        onTertiary = ease(target.onTertiary),
        tertiaryContainer = ease(target.tertiaryContainer),
        onTertiaryContainer = ease(target.onTertiaryContainer),
        background = ease(target.background),
        onBackground = ease(target.onBackground),
        surface = ease(target.surface),
        onSurface = ease(target.onSurface),
        surfaceVariant = ease(target.surfaceVariant),
        onSurfaceVariant = ease(target.onSurfaceVariant),
        inverseSurface = ease(target.inverseSurface),
        inverseOnSurface = ease(target.inverseOnSurface),
        error = ease(target.error),
        onError = ease(target.onError),
        errorContainer = ease(target.errorContainer),
        onErrorContainer = ease(target.onErrorContainer),
        outline = ease(target.outline),
        outlineVariant = ease(target.outlineVariant),
        surfaceContainerLowest = ease(target.surfaceContainerLowest),
        surfaceContainerLow = ease(target.surfaceContainerLow),
        surfaceContainer = ease(target.surfaceContainer),
        surfaceContainerHigh = ease(target.surfaceContainerHigh),
        surfaceContainerHighest = ease(target.surfaceContainerHighest),
    )
}

/** Between one piece of a page popping in and the next. */
private const val STAGGER_MS = 70L
