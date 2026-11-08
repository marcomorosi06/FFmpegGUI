@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cuscus.ffmpeggui.conversion.ReversingProgress
import com.cuscus.ffmpeggui.core.preview.ReversedSoundRequest
import com.cuscus.ffmpeggui.onboarding.rememberReducedMotion
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlinx.coroutines.delay

/*
 * Clips played backwards, as the editor has it: the parts reversed by FFmpeg
 * so far (picture and sound), what is being made, and the window that shows
 * it. The preview plays each part in its clip's place (nothing is added to
 * the project); the timeline draws the clip's sound faint while it is still
 * silent backwards. See ReversedSound.
 */

/** The reversed sounds, for the editor (see the file's notes). */
@Immutable
data class ReversedSounds(
    /** The files made so far, by the part of a file they hold. */
    val files: Map<ReversedSoundRequest, String> = emptyMap(),
    /** The reversing under way, or null. */
    val progress: ReversingProgress? = null,
    /** Asked with the parts the clips played backwards need. */
    val onNeeded: (List<ReversedSoundRequest>) -> Unit = {},
    /** The window's stop (or closing it): the rest is not made. */
    val onStop: () -> Unit = {},
)

/**
 * The window while FFmpeg reverses a clip's sound: the rewinding shape
 * breathing, what is going on, the wavy line, and a stop. Closing it stops
 * too. It shows only after a moment: a file made before is found at once.
 */
@Composable
internal fun ReverseSoundWindow(progress: ReversingProgress?, onStop: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(progress != null) {
        if (progress != null) {
            delay(SHOW_AFTER_MS)
            shown = true
        } else {
            shown = false
        }
    }
    // The count stays while the window goes.
    var last by remember { mutableStateOf(progress) }
    if (progress != null) last = progress
    if (!shown) return
    val current = last ?: return
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    var breathe by remember { mutableStateOf(false) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(BREATH_MS)
                breathe = !breathe
            }
        }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    Dialog(onDismissRequest = onStop) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                MorphingContainer(
                    from = MaterialShapes.Cookie9Sided,
                    to = MaterialShapes.SoftBurst,
                    morphed = breathe,
                    color = scheme.tertiaryContainer,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(34.dp))
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.reverse_sound_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reverse_sound_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (current.total > 1) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.reverse_sound_count, current.done + 1, current.total),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(scheme.secondaryContainer)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                // The work done, smoothly between the steps FFmpeg reports.
                val shownFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = current.fraction.coerceIn(0f, 1f),
                    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                    label = "reverseProgress",
                )
                LinearWavyProgressIndicator(progress = { shownFraction }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            onStop()
                        },
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.reverse_sound_stop))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reverse_sound_stop_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A file made before is found at once: the window waits this long before it shows. */
private const val SHOW_AFTER_MS = 350L

/** How long the shape rests before changing, or back. */
private const val BREATH_MS = 1_400L
