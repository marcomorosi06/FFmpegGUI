@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.dub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.dub.VoiceLicence
import com.cuscus.ffmpeggui.settings.SettingsGroupTitle
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer

/** The badge, the colours and the words for a voice's [VoiceLicence]. */
private class LicenceLook(val icon: androidx.compose.ui.graphics.vector.ImageVector, val container: Color, val content: Color, val title: Int, val text: Int)

@Composable
private fun lookOf(licence: VoiceLicence): LicenceLook {
    val scheme = MaterialTheme.colorScheme
    return when (licence) {
        VoiceLicence.RESEARCH_ONLY -> LicenceLook(Icons.Rounded.Science, scheme.errorContainer, scheme.onErrorContainer, R.string.dub_licence_research_title, R.string.dub_licence_research_text)
        VoiceLicence.NON_COMMERCIAL -> LicenceLook(Icons.Rounded.Gavel, scheme.tertiaryContainer, scheme.onTertiaryContainer, R.string.dub_licence_nc_title, R.string.dub_licence_nc_text)
        else -> LicenceLook(Icons.AutoMirrored.Rounded.HelpOutline, scheme.secondaryContainer, scheme.onSecondaryContainer, R.string.dub_licence_unknown_title, R.string.dub_licence_unknown_text)
    }
}

/** A short word for [licence], for the voice's chip and its row in the settings; null when it is free. */
@Composable
internal fun licenceTag(licence: VoiceLicence): String? = when (licence) {
    VoiceLicence.COMMERCIAL -> null
    VoiceLicence.NON_COMMERCIAL -> stringResource(R.string.dub_licence_nc_tag)
    VoiceLicence.RESEARCH_ONLY -> stringResource(R.string.dub_licence_research_tag)
    VoiceLicence.UNKNOWN -> stringResource(R.string.dub_licence_unknown_tag)
}

/**
 * Under the voice chosen, when its licence has conditions: a tinted strip
 * with the licence's badge and what it means, always in sight.
 */
@Composable
internal fun DubLicenceNote(speaker: DubSpeaker, modifier: Modifier = Modifier) {
    val look = lookOf(speaker.licence)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(look.container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(look.icon, contentDescription = null, tint = look.content, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            stringResource(R.string.dub_licence_note, stringResource(look.title)),
            style = MaterialTheme.typography.bodySmall,
            color = look.content,
        )
    }
}

/**
 * Asked before every dub with a voice whose licence has conditions (not for
 * commercial use, research only, or not known): what that means, that keeping
 * to it is up to whoever publishes the video, and three ways on: read the
 * licence, take another voice, or dub knowing it.
 */
@Composable
internal fun DubLicenceDialog(
    speaker: DubSpeaker,
    canChange: Boolean,
    onDub: () -> Unit,
    onChangeVoice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val look = lookOf(speaker.licence)
    var shown by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val scale by animateFloatAsState(if (shown) 1f else 0.82f, motion.defaultSpatialSpec(), label = "licenceScale")
    val fade by animateFloatAsState(if (shown) 1f else 0f, motion.defaultEffectsSpec(), label = "licenceFade")
    val turn by animateFloatAsState(if (shown) 0f else -45f, motion.slowSpatialSpec(), label = "licenceTurn")
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(scheme.surfaceContainerHigh)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
        ) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Cookie4Sided,
                morphed = shown,
                color = look.container,
                modifier = Modifier
                    .graphicsLayer { rotationZ = turn }
                    .size(80.dp),
            ) {
                Icon(
                    look.icon,
                    contentDescription = null,
                    tint = look.content,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = -turn }
                        .size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(look.title),
                style = SettingsGroupTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(look.text, speaker.label),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // What counts as earning from it: the cases people do not think of.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceContainerHighest)
                    .padding(14.dp),
            ) {
                Icon(Icons.Rounded.Paid, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.dub_licence_commercial_examples), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                reading = !reading
            }) {
                Text(stringResource(if (reading) R.string.dub_licence_hide else R.string.dub_licence_read))
            }
            AnimatedVisibility(
                visible = reading,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainerLowest)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    Text(
                        speaker.card ?: stringResource(R.string.piper_no_card),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDub()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = if (speaker.licence == VoiceLicence.RESEARCH_ONLY) {
                        ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.dub_licence_accept), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (canChange) onChangeVoice() else onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        stringResource(if (canChange) R.string.dub_licence_change else R.string.dub_licence_back),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
