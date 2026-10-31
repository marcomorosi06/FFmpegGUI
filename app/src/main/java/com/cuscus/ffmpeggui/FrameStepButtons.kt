@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * The arrows beside play / pause that move the playhead one frame at a time,
 * for cutting exactly where a finger on the timeline would glide past. They
 * hug the play button like the two ends of a connected button group: round on
 * the outside, nearly square on the side facing it; pressed, the inner corners
 * round off too and the arrow nudges the way it goes, with the theme's fast
 * spatial spring. Held, they step again and again, faster the longer they are.
 */

/**
 * One arrow: [forward] or back. [onStep] moves one frame and says whether it
 * could (false at either end of the video, which stops a hold); it is told
 * whether the step is one of a hold (the preview then follows the steps as a
 * dragged timeline does) or a single tap.
 */
@Composable
internal fun FrameStepButton(forward: Boolean, enabled: Boolean, onStep: (held: Boolean) -> Boolean, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentStep by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }

    /** One step, with its tick; a refused one (an end) a firmer tap. */
    fun step(held: Boolean): Boolean {
        val moved = currentStep(held)
        haptic.performHapticFeedback(if (moved) HapticFeedbackType.SegmentFrequentTick else HapticFeedbackType.Reject)
        return moved
    }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeated = false
        delay(REPEAT_START_MS)
        var gap = REPEAT_FIRST_GAP_MS
        while (true) {
            repeated = true
            if (!step(held = true)) break
            delay(gap)
            gap = (gap * REPEAT_SPEED_UP).toLong().coerceAtLeast(REPEAT_FASTEST_MS)
        }
    }

    // Outer side round, inner side square-ish; pressed, the inner side rounds off.
    val inner by animateDpAsState(if (pressed) 24.dp else 6.dp, motion.fastSpatialSpec(), label = "frameStepInner")
    val nudge by animateFloatAsState(if (pressed) 1f else 0f, motion.fastSpatialSpec(), label = "frameStepNudge")
    val squish by animateFloatAsState(if (pressed) 0.92f else 1f, motion.fastSpatialSpec(), label = "frameStepSquish")
    val shape = if (forward) {
        RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = inner, bottomEnd = inner)
    }
    val label = stringResource(if (forward) R.string.editor_frame_forward else R.string.editor_frame_back)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 44.dp, height = 40.dp)
            .graphicsLayer {
                scaleX = squish
                scaleY = squish
            }
            .clip(shape)
            .background(if (pressed) scheme.secondary else scheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.38f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                // A hold already stepped: lifting the finger adds nothing.
                if (!repeated) step(held = false)
                repeated = false
            }
            .semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = null,
            tint = when {
                pressed -> scheme.onSecondary
                enabled -> scheme.onSecondaryContainer
                else -> scheme.onSecondaryContainer.copy(alpha = 0.38f)
            },
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { translationX = (if (forward) 3.dp else (-3).dp).toPx() * nudge },
        )
    }
}

private const val REPEAT_START_MS = 380L
private const val REPEAT_FIRST_GAP_MS = 120L
private const val REPEAT_FASTEST_MS = 33L
private const val REPEAT_SPEED_UP = 0.86f
