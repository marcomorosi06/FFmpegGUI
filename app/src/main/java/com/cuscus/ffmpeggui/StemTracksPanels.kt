@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.conversion.VoiceProblem
import com.cuscus.ffmpeggui.conversion.VoiceStatus
import com.cuscus.ffmpeggui.core.stems.StemKind
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption

/*
 * The stems of the clips as tracks of their own (see ConversionViewModel.placeStemTracks):
 * how far to split (dialogue, music and effects, or every instrument of the
 * music too), which stems to lay on the video, and the one button that does
 * it. Each stem is a pill with its own shape and colour that blooms when it is
 * picked; a stem already kept for every file in question carries a tick.
 */

/** How far the sound is split: the scene (Bandit) or the scene with its music's instruments (Spleeter too). */
private enum class SplitDepth { SCENE, INSTRUMENTS }

/** The stems a depth offers, in the order they are shown and placed. */
private fun SplitDepth.kinds(): List<StemKind> = when (this) {
    SplitDepth.SCENE -> StemKind.SCENE
    SplitDepth.INSTRUMENTS -> listOf(StemKind.DIALOGUE, StemKind.EFFECTS) + StemKind.INSTRUMENTS
}

/** A stem's name, in words. */
@Composable
internal fun stemLabel(kind: StemKind): String = stringResource(
    when (kind) {
        StemKind.DIALOGUE -> R.string.stem_dialogue
        StemKind.MUSIC -> R.string.stem_music
        StemKind.EFFECTS -> R.string.stem_effects
        StemKind.SINGING -> R.string.stem_singing
        StemKind.PIANO -> R.string.stem_piano
        StemKind.DRUMS -> R.string.stem_drums
        StemKind.BASS -> R.string.stem_bass
        StemKind.OTHER -> R.string.stem_other
    },
)

internal fun stemIcon(kind: StemKind): ImageVector = when (kind) {
    StemKind.DIALOGUE -> Icons.Rounded.RecordVoiceOver
    StemKind.MUSIC -> Icons.Rounded.MusicNote
    StemKind.EFFECTS -> Icons.Rounded.Bolt
    StemKind.SINGING -> Icons.Rounded.Mic
    StemKind.PIANO -> Icons.Rounded.Piano
    StemKind.DRUMS -> Icons.Rounded.GraphicEq
    StemKind.BASS -> Icons.Rounded.Waves
    StemKind.OTHER -> Icons.Rounded.LibraryMusic
}

private fun stemShape(kind: StemKind) = when (kind) {
    StemKind.DIALOGUE -> MaterialShapes.Cookie9Sided
    StemKind.MUSIC -> MaterialShapes.Flower
    StemKind.EFFECTS -> MaterialShapes.SoftBurst
    StemKind.SINGING -> MaterialShapes.Clover4Leaf
    StemKind.PIANO -> MaterialShapes.Cookie6Sided
    StemKind.DRUMS -> MaterialShapes.Sunny
    StemKind.BASS -> MaterialShapes.Cookie12Sided
    StemKind.OTHER -> MaterialShapes.Clover8Leaf
}

/** Each stem's pair of colours: the voice in primary, the music's family in tertiary, the effects in secondary. */
@Composable
private fun stemTone(kind: StemKind): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        StemKind.DIALOGUE -> ToneColors(scheme.primary, scheme.onPrimary)
        StemKind.EFFECTS -> ToneColors(scheme.secondary, scheme.onSecondary)
        StemKind.MUSIC, StemKind.OTHER -> ToneColors(scheme.tertiary, scheme.onTertiary)
        StemKind.SINGING, StemKind.BASS -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        StemKind.PIANO, StemKind.DRUMS -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
}

/**
 * The card: the depth as two tiles, the stems as pills, then the button
 * that lays them on the video, or the way to the instruments' model, or what
 * the last run did.
 */
@Composable
internal fun StemTracksCard(voice: VoiceEditing, clipId: String?, sourceIds: Set<String>) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    var depth by rememberSaveable { mutableStateOf(SplitDepth.SCENE) }
    // What is picked, by stem id, so it survives a turn of the phone; every stem at first.
    var picked by rememberSaveable { mutableStateOf(StemKind.entries.map { it.id }.toSet()) }
    val offered = depth.kinds()
    val chosen = offered.filter { it.id in picked }.toSet()
    val working = voice.status is VoiceStatus.Working
    val placed = voice.status as? VoiceStatus.Placed
    val problem = (voice.status as? VoiceStatus.Failed)?.problem
        ?.takeIf { it == VoiceProblem.NO_INSTRUMENTS_MODEL || it == VoiceProblem.NO_TRACKS }
    val needsInstruments = depth == SplitDepth.INSTRUMENTS && !voice.instrumentsReady
    // A stem is kept already when every file in question has it: placing it is then quick.
    fun kept(kind: StemKind) = sourceIds.isNotEmpty() && sourceIds.all { kind in voice.tracks[it].orEmpty() }
    val names = offered.associateWith { stemLabel(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie12Sided,
                to = MaterialShapes.Clover8Leaf,
                morphed = depth == SplitDepth.INSTRUMENTS,
                color = scheme.secondaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Rounded.Layers, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.stems_tracks_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(
                    stringResource(R.string.stems_tracks_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        OptionTileRow(
            options = listOf(
                TileOption(SplitDepth.SCENE, stringResource(R.string.stems_depth_scene), Icons.Rounded.Theaters),
                TileOption(SplitDepth.INSTRUMENTS, stringResource(R.string.stems_depth_instruments), Icons.Rounded.Piano),
            ),
            selected = depth,
            enabled = !working,
            onSelect = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                depth = it
            },
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            offered.forEach { kind ->
                StemPill(
                    kind = kind,
                    label = names.getValue(kind),
                    selected = kind in chosen,
                    kept = kept(kind),
                    enabled = !working,
                    onToggle = {
                        haptics.performHapticFeedback(if (kind in chosen) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        picked = if (kind.id in picked) picked - kind.id else picked + kind.id
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = needsInstruments,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelHint(stringResource(R.string.stems_needs_instruments))
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        voice.onOpenSmartFeatures()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.speech_open_settings), style = MaterialTheme.typography.titleSmallEmphasized)
                }
            }
        }

        AnimatedVisibility(
            visible = !needsInstruments,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            val count = chosen.size
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    voice.onDismissProblem()
                    voice.onPlaceTracks(clipId, chosen, names)
                },
                enabled = voice.modelReady && !working && count > 0 && sourceIds.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    pluralStringResource(R.plurals.stems_place, count.coerceAtLeast(1), count),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
        }

        if (!voice.modelReady) PanelHint(stringResource(R.string.voice_needs_model))

        AnimatedVisibility(
            visible = placed != null || problem != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            val good = placed != null
            val tone = if (good) ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer) else ToneColors(scheme.errorContainer, scheme.onErrorContainer)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(tone.container)
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(if (good) Icons.Rounded.CheckCircle else Icons.Rounded.Layers, contentDescription = null, tint = tone.content, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            placed != null -> pluralStringResource(R.plurals.stems_placed, placed.tracks, placed.tracks, placed.clips)
                            problem == VoiceProblem.NO_INSTRUMENTS_MODEL -> stringResource(R.string.stems_problem_instruments_model)
                            else -> stringResource(R.string.stems_problem_no_tracks)
                        },
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = tone.content,
                    )
                    if (placed != null && placed.skipped > 0) {
                        Text(
                            pluralStringResource(R.plurals.stems_skipped, placed.skipped, placed.skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = tone.content,
                        )
                    }
                }
                TextButton(onClick = voice.onDismissProblem) {
                    Text(stringResource(R.string.retired_ok), color = tone.content)
                }
            }
        }
    }
}

/**
 * One stem as a pill: its shape as a badge that blooms into a circle when
 * picked, the pill's colour filling in, a little spring as it is pressed,
 * and a tick when it is kept already.
 */
@Composable
private fun StemPill(kind: StemKind, label: String, selected: Boolean, kept: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val tone = stemTone(kind)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, motion.fastSpatialSpec(), label = "stemPress")
    val container by animateColorAsState(if (selected) tone.container else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "stemContainer")
    val content by animateColorAsState(if (selected) tone.content else scheme.onSurfaceVariant, motion.defaultEffectsSpec(), label = "stemContent")
    val badge by animateColorAsState(if (selected) tone.content.copy(alpha = 0.18f) else scheme.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "stemBadge")
    val selectedText = stringResource(if (selected) R.string.stems_picked else R.string.stems_not_picked)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
                alpha = if (enabled) 1f else 0.55f
            }
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = selectedText }
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        MorphingContainer(
            from = stemShape(kind),
            to = MaterialShapes.Circle,
            morphed = selected,
            color = badge,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(stemIcon(kind), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLargeEmphasized, color = content)
        AnimatedVisibility(visible = kept) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) tone.content else scheme.primary),
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.stems_kept),
                    tint = if (selected) tone.container else scheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
