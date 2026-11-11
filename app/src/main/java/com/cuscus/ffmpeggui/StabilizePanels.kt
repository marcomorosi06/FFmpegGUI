@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * The video editor's Stabilize tool: the whole video steadied by vid.stab, in
 * two passes of the export (the first studies how the camera moved, the
 * second evens the move out and zooms in a little so that no border shows;
 * see CommandBuilder.buildStabilizationAnalysis). In Material 3 Expressive:
 *
 *  - a hero card whose badge shakes while the video is left as it is and
 *    settles on a spring, its cookie swelling, once it is steadied; a switch;
 *  - how firm, as a connected button group (Gentle, Steady, Firm, Tripod),
 *    and the two numbers behind it on sliders: how shaky the footage is
 *    (vidstabdetect's shakiness) and over how many frames the camera's move
 *    is smoothed (vidstabtransform's smoothing; 0 holds it still, as on a
 *    tripod);
 *  - what to expect: the preview cannot show it (it needs the first pass),
 *    the export takes longer, the frame is cropped a little.
 *
 * The preview's sign that it is on: a badge among its status badges
 * (StabilizeBadge), which opens this tool.
 */

/** How firmly the video is steadied, in one tap: the two numbers behind it. */
private enum class SteadyLevel(@StringRes val labelRes: Int, val shakiness: Int, val smoothing: Int) {
    GENTLE(R.string.stabilize_gentle, 3, 8),
    STEADY(R.string.stabilize_steady, 5, 15),
    FIRM(R.string.stabilize_firm, 8, 30),
    TRIPOD(R.string.stabilize_tripod, 10, 0),
}

/**
 * The Stabilize tool.
 *
 * @param on          the video is steadied.
 * @param shakiness   how shaky the footage is, 1..10 (vidstabdetect).
 * @param smoothing   frames the camera's move is smoothed over, 0..100 (0: held still).
 * @param available   the export can steady it (not while copying the streams or extracting frames).
 */
@Composable
internal fun StabilizePanel(
    on: Boolean,
    shakiness: Int,
    smoothing: Int,
    onOn: (Boolean) -> Unit,
    onShakiness: (Int) -> Unit,
    onSmoothing: (Int) -> Unit,
    available: Boolean,
    enabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val level = SteadyLevel.entries.firstOrNull { it.shakiness == shakiness && it.smoothing == smoothing }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SteadyHero(on = on && available, onOn = onOn, enabled = enabled && available)
        AnimatedVisibility(
            visible = on && available,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectedChoiceGroup(
                    options = SteadyLevel.entries,
                    // Numbers set by hand show none of the levels chosen.
                    selected = level,
                    label = { option -> option?.let { stringResource(it.labelRes) }.orEmpty() },
                    onSelect = { option ->
                        option?.let {
                            onShakiness(it.shakiness)
                            onSmoothing(it.smoothing)
                        }
                    },
                    enabled = enabled,
                )
                PanelCard {
                    ValueSlider(
                        label = stringResource(R.string.stabilize_shakiness),
                        valueText = stringResource(R.string.stabilize_shakiness_value, shakiness),
                        value = shakiness.toFloat(),
                        onValueChange = { onShakiness(it.roundToInt().coerceIn(1, 10)) },
                        valueRange = 1f..10f,
                        steps = 8,
                        icon = Icons.Rounded.Vibration,
                        defaultValue = DEFAULT_SHAKINESS.toFloat(),
                        startLabel = stringResource(R.string.stabilize_shakiness_low),
                        endLabel = stringResource(R.string.stabilize_shakiness_high),
                        enabled = enabled,
                        input = ValueInput(step = 1f, range = 1f..10f),
                    )
                    ValueSlider(
                        label = stringResource(R.string.stabilize_smoothing),
                        valueText = if (smoothing == 0) {
                            stringResource(R.string.stabilize_smoothing_still)
                        } else {
                            pluralStringResource(R.plurals.stabilize_smoothing_frames, smoothing, smoothing)
                        },
                        value = smoothing.toFloat(),
                        onValueChange = { onSmoothing(it.roundToInt().coerceIn(0, MAX_SMOOTHING)) },
                        valueRange = 0f..MAX_SMOOTHING.toFloat(),
                        icon = Icons.Rounded.Timelapse,
                        defaultValue = DEFAULT_SMOOTHING.toFloat(),
                        startLabel = stringResource(R.string.stabilize_smoothing_low),
                        endLabel = stringResource(R.string.stabilize_smoothing_high),
                        enabled = enabled,
                        input = ValueInput(step = 1f, range = 0f..MAX_SMOOTHING.toFloat()),
                    )
                    AnimatedVisibility(
                        visible = smoothing == 0,
                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        PanelHint(stringResource(R.string.stabilize_tripod_hint))
                    }
                }
                ExpectNote(R.string.stabilize_expect_preview)
                ExpectNote(R.string.stabilize_expect_time)
                ExpectNote(R.string.stabilize_expect_crop)
            }
        }
        if (!available) PanelHint(stringResource(R.string.stabilize_unavailable))
        PanelHint(stringResource(R.string.stabilize_hint))
    }
}

/**
 * The tool's hero: a badge that shakes while the video is left shaky and
 * settles once it is steadied (its shape swelling from a soft burst to a
 * cookie), what it does, and the switch.
 */
@Composable
private fun SteadyHero(on: Boolean, onOn: (Boolean) -> Unit, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.stabilize_title)
    val state = stringResource(if (on) R.string.stabilize_state_on else R.string.stabilize_state_off)
    // The badge's jitter: random nudges while shaky, a spring back to rest when steadied.
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) {
            dx.animateTo(0f, motion.slowSpatialSpec())
            dy.animateTo(0f, motion.slowSpatialSpec())
            turn.animateTo(0f, motion.slowSpatialSpec())
        } else {
            val random = Random(System.nanoTime())
            while (true) {
                dx.snapTo((random.nextFloat() - 0.5f) * JITTER_PX)
                dy.snapTo((random.nextFloat() - 0.5f) * JITTER_PX)
                turn.snapTo((random.nextFloat() - 0.5f) * JITTER_DEGREES)
                delay(JITTER_STEP_MS)
            }
        }
    }
    val container by androidx.compose.animation.animateColorAsState(
        if (on) scheme.primaryContainer else scheme.surfaceContainerHigh,
        motion.defaultEffectsSpec(),
        label = "steadyHero",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(enabled = enabled, role = Role.Switch, onClickLabel = label) {
                haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onOn(!on)
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = state
            }
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    translationX = dx.value
                    translationY = dy.value
                    rotationZ = turn.value
                },
        ) {
            MorphingContainer(
                from = MaterialShapes.SoftBurst,
                to = MaterialShapes.Cookie9Sided,
                morphed = on,
                color = if (on) scheme.primary else scheme.surfaceContainerHighest,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    if (on) Icons.Rounded.CenterFocusStrong else Icons.Rounded.Videocam,
                    contentDescription = null,
                    tint = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMediumEmphasized, color = if (on) scheme.onPrimaryContainer else scheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                state,
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = on,
            onCheckedChange = {
                haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onOn(it)
            },
            enabled = enabled,
        )
    }
}

/** What to expect of the steadying, as a line with an icon. */
@Composable
private fun ExpectNote(@StringRes text: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = scheme.primary, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}

/**
 * The preview's sign that the export steadies the video, although the preview
 * cannot show it: a cookie of dark glass among its status badges, which says
 * what it is for a moment and then folds to the cookie; it fades while the
 * video plays, and a tap opens the Stabilize tool ([onOpen]).
 */
@Composable
internal fun StabilizeBadge(playing: Boolean, onOpen: (() -> Unit)?) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.stabilize_badge)
    val action = stringResource(R.string.stabilize_badge_open)
    var arrived by remember { mutableStateOf(false) }
    var spelled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        arrived = true
        spelled = true
        delay(BADGE_SPELLED_MS)
        spelled = false
    }
    val alpha by animateFloatAsState(if (playing) BADGE_PLAYING_ALPHA else 1f, motion.slowEffectsSpec(), label = "steadyBadge")
    val glass = Color.Black.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .then(
                if (onOpen == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = action) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onOpen()
                    }
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie9Sided,
            morphed = arrived,
            color = glass,
            modifier = Modifier.size(STATUS_BADGE_SIZE),
        ) {
            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        AnimatedVisibility(
            visible = spelled,
            enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkHorizontally(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .height(STATUS_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(glass)
                    .padding(horizontal = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
            }
        }
    }
}

/** vidstab's own defaults, which the sliders spring back to. */
private const val DEFAULT_SHAKINESS = 5
private const val DEFAULT_SMOOTHING = 10

/** The most frames the camera's move is smoothed over. */
internal const val MAX_SMOOTHING = 100

/** How far, how much and how often the hero's badge shakes while the video is shaky. */
private const val JITTER_PX = 9f
private const val JITTER_DEGREES = 7f
private const val JITTER_STEP_MS = 70L
