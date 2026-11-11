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
import androidx.compose.material.icons.rounded.CallMerge
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
import com.cuscus.ffmpeggui.core.stems.StemSplit
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption

/*
 * The stems of the clips as tracks of their own (see ConversionViewModel.placeStemTracks):
 * how far to split (core's StemSplit: dialogue, music and effects; every
 * instrument of the music too; or a song, split straight into its
 * instruments, much quicker), which stems to lay on the video, and the one
 * button that does it. Each stem is a pill with its own shape and colour that
 * blooms when it is picked; a stem already kept for every file in question
 * carries a tick. A sound alone starts as a song, a video as its scene.
 */

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
    // A song for sounds alone, the scene when a video is among them; the user changes it at will.
    var depth by rememberSaveable { mutableStateOf(StemSplit.firstFor(soundOnly = sourceIds.isNotEmpty() && sourceIds.all { it in voice.soundOnly })) }
    // What is picked, by stem id, so it survives a turn of the phone; every stem at first.
    var picked by rememberSaveable { mutableStateOf(StemKind.entries.map { it.id }.toSet()) }
    val offered = depth.kinds
    val chosen = offered.filter { it.id in picked }.toSet()
    val working = voice.status is VoiceStatus.Working
    val placed = voice.status as? VoiceStatus.Placed
    val problem = (voice.status as? VoiceStatus.Failed)?.problem
        ?.takeIf { it == VoiceProblem.NO_INSTRUMENTS_MODEL || it == VoiceProblem.NO_TRACKS }
    val needsInstruments = depth.usesSpleeter && !voice.instrumentsReady
    // Bandit is needed unless the sound is split as a song.
    val needsBandit = depth.usesBandit && !voice.modelReady
    // A stem is kept already when every file in question has it: placing it is then quick. A song's
    // instruments are not the music's: asked for with the dialogue apart, they are split again.
    fun kept(kind: StemKind) = sourceIds.isNotEmpty() && sourceIds.all { id ->
        kind in voice.tracks[id].orEmpty() && !(depth == StemSplit.INSTRUMENTS && kind.isInstrument && id in voice.songSplits)
    }
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
                morphed = depth != StemSplit.SCENE,
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
                TileOption(StemSplit.SCENE, stringResource(R.string.stems_depth_scene), Icons.Rounded.Theaters),
                TileOption(StemSplit.INSTRUMENTS, stringResource(R.string.stems_depth_instruments), Icons.Rounded.Piano),
                TileOption(StemSplit.SONG, stringResource(R.string.stems_depth_song), Icons.Rounded.MusicNote),
            ),
            selected = depth,
            enabled = !working,
            onSelect = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                depth = it
            },
        )

        // What each way costs: a song skips Bandit, by far the slower model.
        PanelHint(
            stringResource(
                when (depth) {
                    StemSplit.SCENE -> R.string.stems_depth_scene_hint
                    StemSplit.INSTRUMENTS -> R.string.stems_depth_instruments_hint
                    StemSplit.SONG -> R.string.stems_depth_song_hint
                },
            ),
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
                    voice.onPlaceTracks(clipId, chosen, names, depth == StemSplit.SONG)
                },
                enabled = !needsBandit && !working && count > 0 && sourceIds.isNotEmpty(),
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

        if (needsBandit) PanelHint(stringResource(R.string.voice_needs_model))

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

/**
 * The stem tracks laid for the clip (or for every clip), to put back
 * together (see StemRejoin): for one clip its tracks as pills, all picked at
 * first; all of them bring the clip back, two or more become one track. For
 * the whole video, one button puts every clip back. Nothing shows while no
 * clip in question has stem tracks.
 */
@Composable
internal fun StemRejoinCard(voice: VoiceEditing, clipId: String?) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val laid = if (clipId != null) voice.laid[clipId].orEmpty() else voice.laid.values.flatten()
    if (laid.isEmpty()) return
    val working = voice.rejoining || voice.status is VoiceStatus.Working
    // What is picked, by track id: every track at first, and every one laid since.
    var unpicked by rememberSaveable(clipId) { mutableStateOf(emptySet<String>()) }
    val picked = laid.map { it.id }.filter { it !in unpicked }.toSet()
    val names = StemKind.entries.associateWith { stemLabel(it) }
    val all = picked.size == laid.size
    val count = picked.size

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
                from = MaterialShapes.Clover8Leaf,
                to = MaterialShapes.Cookie12Sided,
                morphed = clipId != null && all,
                color = scheme.tertiaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Rounded.CallMerge, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.stems_rejoin_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(stringResource(R.string.stems_rejoin_text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }

        if (clipId != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                laid.forEach { track ->
                    val kinds = track.kinds.sortedBy { it.ordinal }
                    StemPill(
                        kind = kinds.firstOrNull() ?: StemKind.OTHER,
                        label = kinds.joinToString(" + ") { names.getValue(it) }.ifEmpty { track.name },
                        selected = track.id in picked,
                        kept = false,
                        enabled = !working,
                        onToggle = {
                            haptics.performHapticFeedback(if (track.id in picked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            unpicked = if (track.id in unpicked) unpicked - track.id else unpicked + track.id
                        },
                    )
                }
            }
            PanelHint(stringResource(if (all) R.string.stems_rejoin_all_hint else R.string.stems_rejoin_some_hint))
        }

        val clips = if (clipId != null) 1 else voice.laid.size
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                voice.onRejoin(clipId, if (clipId == null || all) null else picked, names)
            },
            enabled = !working && (clipId == null || all || count >= 2),
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.CallMerge, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                when {
                    clipId == null -> pluralStringResource(R.plurals.stems_rejoin_every, clips, clips)
                    all -> stringResource(R.string.stems_rejoin_all)
                    else -> pluralStringResource(R.plurals.stems_rejoin_some, count.coerceAtLeast(2), count)
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}
