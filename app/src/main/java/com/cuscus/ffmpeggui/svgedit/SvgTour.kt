@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.svgedit

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import com.cuscus.ffmpeggui.ui.expressive.MorphShape
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape

/*
 * The SVG editor's tour, shown the first time it opens (and from the header's
 * help button): one card over a scrim. On top, a coloured stage: the page's
 * badge morphing out of the last page's shape into its own, big shapes of the
 * same kind drifting behind it, the colour turning with the page. Under it
 * the step, its title and its words; then a segmented progress, back, skip
 * and next. A swipe turns the pages too.
 */

/** One card of the tour. */
private class TourPage(val title: Int, val body: Int, val icon: ImageVector, val shape: RoundedPolygon)

private val PAGES = listOf(
    TourPage(R.string.svg_tour_1_title, R.string.svg_tour_1_body, Icons.Rounded.TouchApp, MaterialShapes.Cookie9Sided),
    TourPage(R.string.svg_tour_2_title, R.string.svg_tour_2_body, Icons.Rounded.OpenWith, MaterialShapes.Clover4Leaf),
    TourPage(R.string.svg_tour_3_title, R.string.svg_tour_3_body, Icons.Rounded.ZoomIn, MaterialShapes.SoftBurst),
    TourPage(R.string.svg_tour_4_title, R.string.svg_tour_4_body, Icons.Rounded.Layers, MaterialShapes.Flower),
    TourPage(R.string.svg_tour_5_title, R.string.svg_tour_5_body, Icons.Rounded.AutoFixHigh, MaterialShapes.Sunny),
    TourPage(R.string.svg_tour_6_title, R.string.svg_tour_6_body, Icons.Rounded.Flare, MaterialShapes.Clover8Leaf),
)

/** The stage's colours of page [page]: the three accent families, in turn, whatever the number of pages. */
@Composable
private fun stageColors(page: Int): Triple<Color, Color, Color> {
    val s = MaterialTheme.colorScheme
    return when (page % 3) {
        0 -> Triple(s.primaryContainer, s.primary, s.onPrimary)
        1 -> Triple(s.tertiaryContainer, s.tertiary, s.onTertiary)
        else -> Triple(s.secondaryContainer, s.secondary, s.onSecondary)
    }
}

/** Whether the tour was already seen on this phone. */
internal fun tourSeen(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

internal fun markTourSeen(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply()
}

private const val PREFS = "svg_editor"
private const val KEY_SEEN = "tour_seen"

@Composable
internal fun SvgTour(onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = page == PAGES.lastIndex
    fun go(to: Int) {
        val target = to.coerceIn(0, PAGES.lastIndex)
        if (target != page) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            page = target
        }
    }
    BackHandler {
        if (page > 0) go(page - 1) else onDone()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.scrim.copy(alpha = 0.6f))
            // The scrim takes the touches: nothing under it moves.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // A phone on its side is short: the card scrolls, its stage lower.
        val short = LocalWindowLayout.current.isShortLandscape
        var drag by remember { mutableFloatStateOf(0f) }
        Column(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(44.dp))
                .background(scheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            val threshold = 64.dp.toPx()
                            if (drag < -threshold) go(page + 1) else if (drag > threshold) go(page - 1)
                            drag = 0f
                        },
                        onHorizontalDrag = { _, amount -> drag += amount },
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            TourStage(page, if (short) 150.dp else 248.dp)
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(motion.fastSpatialSpec()) { if (forward) -it / 4 else it / 4 } + fadeOut(motion.fastEffectsSpec()))
                    },
                    label = "tourText",
                ) { p ->
                    Column(modifier = Modifier.fillMaxWidth().heightIn(min = if (short) 96.dp else 150.dp)) {
                        val (_, accent, _) = stageColors(p)
                        Text(
                            stringResource(R.string.svg_tour_step, p + 1, PAGES.size),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = accent,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(PAGES[p].title),
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = scheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            stringResource(PAGES[p].body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Segments(page, onPick = ::go)
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = page > 0,
                        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        FilledTonalIconButton(
                            onClick = { go(page - 1) },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                    AnimatedVisibility(visible = !last, enter = fadeIn(motion.fastEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
                        TextButton(onClick = onDone) { Text(stringResource(R.string.svg_tour_skip)) }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val (_, accent, onAccent) = stageColors(page)
                    val container by animateColorAsState(accent, motion.defaultEffectsSpec(), label = "nextColor")
                    val content by animateColorAsState(onAccent, motion.defaultEffectsSpec(), label = "nextContent")
                    Button(
                        onClick = {
                            if (last) {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onDone()
                            } else {
                                go(page + 1)
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                        contentPadding = ButtonDefaults.MediumContentPadding,
                        modifier = Modifier.height(56.dp).animateContentSize(motion.defaultSpatialSpec()),
                    ) {
                        AnimatedContent(targetState = last, label = "tourButton") { end ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(if (end) R.string.svg_tour_start else R.string.svg_tour_next),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    if (end) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The page's stage: its colour, two big shapes of its kind drifting behind,
 * and in the middle its badge, the last page's shape morphing into its own,
 * turning slowly, its icon bobbing.
 */
@Composable
private fun TourStage(page: Int, height: Dp) {
    val motion = MaterialTheme.motionScheme
    val (back, accent, onAccent) = stageColors(page)
    val stage by animateColorAsState(back, motion.slowEffectsSpec(), label = "stage")
    val badge by animateColorAsState(accent, motion.defaultEffectsSpec(), label = "badge")
    val iconTint by animateColorAsState(onAccent, motion.defaultEffectsSpec(), label = "badgeIcon")
    // The shape it comes from: the page before (or itself, at the start).
    val from = remember { mutableIntStateOf(page) }
    val morph = remember(page) { Morph(PAGES[from.intValue].shape, PAGES[page].shape) }
    val progress = remember(page) { Animatable(0f) }
    LaunchedEffect(page) {
        progress.animateTo(1f, motion.slowSpatialSpec())
        from.intValue = page
    }
    val loop = rememberInfiniteTransition(label = "tourLoop")
    val turn by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "turn")
    val bob by loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "bob")
    val drift = remember(page) { PolygonShape(PAGES[page].shape) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(36.dp))
            .background(stage),
    ) {
        // The page's shape twice more, large and faint, drifting the other way at the corners.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(height * 0.9f)
                .graphicsLayer {
                    translationX = -size.width * 0.35f
                    translationY = -size.height * 0.4f
                    rotationZ = -turn
                }
                .clip(drift)
                .background(accent.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(height * 0.7f)
                .graphicsLayer {
                    translationX = size.width * 0.3f
                    translationY = size.height * 0.35f
                    rotationZ = turn * 1.5f
                }
                .clip(drift)
                .background(accent.copy(alpha = 0.18f)),
        )
        val side = height * 0.62f
        Box(
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    rotationZ = turn
                    shape = MorphShape(morph, progress.value)
                    clip = true
                }
                .background(badge),
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = { (scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f) + fadeIn(motion.fastEffectsSpec())) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "tourIcon",
        ) { p ->
            Icon(
                PAGES[p].icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(side * 0.42f)
                    .graphicsLayer {
                        translationY = bob * 5.dp.toPx()
                        val s = 1f + 0.05f * bob
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}

/** Where the tour is: one segment per page, those seen filled, the current one widest. Tapping one goes there. */
@Composable
private fun Segments(page: Int, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        PAGES.indices.forEach { i ->
            val weight by animateFloatAsState(if (i == page) 3f else 1f, motion.fastSpatialSpec(), label = "segment")
            val (_, accent, _) = stageColors(page)
            val color by animateColorAsState(if (i <= page) accent else scheme.outlineVariant, motion.fastEffectsSpec(), label = "segmentColor")
            Box(
                modifier = Modifier
                    .weight(weight)
                    .height(if (i == page) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(role = Role.Tab) { onPick(i) },
            )
        }
    }
}
