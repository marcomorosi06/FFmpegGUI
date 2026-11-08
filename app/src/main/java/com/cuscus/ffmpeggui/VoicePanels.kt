@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.conversion.VoiceProblem
import com.cuscus.ffmpeggui.conversion.VoiceStatus
import com.cuscus.ffmpeggui.core.stems.StemKind
import com.cuscus.ffmpeggui.core.voice.VoiceStem
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.voice.VoiceSeparator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The sound split into its parts (see VoiceSeparator): the card that does
 * it (with the model's state, the run as it goes, what went wrong and the
 * parts it gave), the quick mix that sets the dialogue's level and the
 * rest's, with presets for the usual wishes (the dialogue alone, none, up or
 * down), and the tracks (StemTracksPanels). The editor's Stems tool is made
 * of these.
 */

/** The voice separation's state and actions, for the editor. */
class VoiceEditing(
    /** The voice model is on the phone and checked. */
    val modelReady: Boolean = false,
    val status: VoiceStatus = VoiceStatus.Idle,
    /** The separated voice of the files that have one, by source id. */
    val stems: Map<String, Uri> = emptyMap(),
    /** Spleeter's five stems are on the phone: the music can be split into its instruments. */
    val instrumentsReady: Boolean = false,
    /** The stems kept as tracks of their own, by source id. */
    val tracks: Map<String, Set<StemKind>> = emptyMap(),
    /** Lays these stems of clip (its id; null: of every clip) on the video as tracks, named so. */
    val onPlaceTracks: (String?, Set<StemKind>, Map<StemKind, String>) -> Unit = { _, _, _ -> },
    /** Sets the voice of these files (source ids) apart; null: of every clip that plays. */
    val onSeparate: (Set<String>?) -> Unit = {},
    val onStop: () -> Unit = {},
    val onDismissProblem: () -> Unit = {},
    /** The levels of the voice and of the rest of clip (its id; null: of every clip), and an undo key. */
    val onLevels: (String?, Float, Float, String?) -> Unit = { _, _, _, _ -> },
    val onOpenSmartFeatures: () -> Unit = {},
)

/** A mix the user often wants: the voice's level and the rest's. */
internal enum class VoiceMixPreset(val voice: Float, val rest: Float) {
    ORIGINAL(1f, 1f),
    VOICE_ONLY(1f, 0f),
    NO_VOICE(0f, 1f),
    VOICE_UP(1.2f, 0.35f),
    VOICE_DOWN(0.3f, 1f),
    ;

    companion object {
        /** The preset of these levels, or null for a mix of the user's own. */
        fun of(voice: Float, rest: Float): VoiceMixPreset? =
            entries.firstOrNull { abs(it.voice - voice) < 0.01f && abs(it.rest - rest) < 0.01f }
    }
}

/** The two halves of the Stems tool: the quick mix of the dialogue and the rest, or every part as a track. */
private enum class StemsMode { MIX, TRACKS }

/**
 * The editor's Stems tool, for clip [clipId] (null: every clip), whose files
 * are [sourceIds]: on top the card of the split (what is split, the run, the
 * one thing to do next) with the parts it gives lit up as they are there;
 * below, two ways to use them. "Quick mix" sets the dialogue's level and the
 * music's and effects' together, live, now at [voiceLevel] and
 * [backgroundLevel]; "Tracks" lays the parts picked on the video as sounds of
 * their own, to move, cut and level one by one. After tracks were laid (or
 * could not be) it shows Tracks, where that is said.
 */
@Composable
internal fun VoiceSectionContent(
    voice: VoiceEditing,
    clipId: String?,
    sourceIds: Set<String>,
    voiceLevel: Float,
    backgroundLevel: Float,
) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val missing = sourceIds.count { it !in voice.stems }
    val separated = sourceIds.isNotEmpty() && missing == 0
    // The parts every file in question has as tracks.
    val parts = if (sourceIds.isEmpty()) emptySet() else sourceIds.map { voice.tracks[it].orEmpty() }.reduce { a, b -> a intersect b }
    var mode by rememberSaveable { mutableStateOf(StemsMode.MIX) }
    val tracksNote = when (val status = voice.status) {
        is VoiceStatus.Placed -> true
        is VoiceStatus.Failed -> status.problem == VoiceProblem.NO_INSTRUMENTS_MODEL || status.problem == VoiceProblem.NO_TRACKS
        else -> false
    }
    LaunchedEffect(tracksNote) { if (tracksNote) mode = StemsMode.TRACKS }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // What the tracks card says (tracks laid, or why not) stays there.
        VoiceSeparationCard(
            status = if (tracksNote) VoiceStatus.Idle else voice.status,
            modelReady = voice.modelReady,
            separated = separated,
            missingFiles = missing,
            parts = parts,
            onSeparate = { voice.onSeparate(sourceIds) },
            onStop = voice.onStop,
            onDismissProblem = voice.onDismissProblem,
            onOpenSmartFeatures = voice.onOpenSmartFeatures,
        )
        OptionTileRow(
            options = listOf(
                TileOption(StemsMode.MIX, stringResource(R.string.stems_mode_mix), Icons.Rounded.Tune),
                TileOption(StemsMode.TRACKS, stringResource(R.string.stems_mode_tracks), Icons.Rounded.Layers),
            ),
            selected = mode,
            onSelect = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                mode = it
            },
        )
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 3 else -it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(motion.fastSpatialSpec()) { if (forward) -it / 3 else it / 3 } + fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false))
            },
            label = "stemsMode",
        ) { shown ->
            when (shown) {
                StemsMode.MIX -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    VoiceMixer(
                        voiceLevel = voiceLevel,
                        backgroundLevel = backgroundLevel,
                        enabled = separated,
                        onLevels = { step, v, b -> voice.onLevels(clipId, v, b, step) },
                    )
                    PanelHint(stringResource(if (clipId == null) R.string.voice_scope_all_hint else R.string.voice_scope_clip_hint))
                }
                StemsMode.TRACKS -> StemTracksCard(voice = voice, clipId = clipId, sourceIds = sourceIds)
            }
        }
    }
}

/**
 * Where setting the voice apart is: a badge that morphs as it goes, what it is
 * doing, the run's wavy progress, and the one thing to do next (separate,
 * stop, get the model, try again).
 */
@Composable
internal fun VoiceSeparationCard(
    status: VoiceStatus,
    modelReady: Boolean,
    /** Every file in question is separated already. */
    separated: Boolean,
    /** How many are not. */
    missingFiles: Int,
    /** The parts every file in question has as tracks: lit in the strip. */
    parts: Set<StemKind> = emptySet(),
    onSeparate: () -> Unit,
    onStop: () -> Unit,
    onDismissProblem: () -> Unit,
    onOpenSmartFeatures: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val working = status as? VoiceStatus.Working
    val failed = (status as? VoiceStatus.Failed)?.problem
    val tone = when {
        failed != null -> ToneColors(scheme.errorContainer, scheme.onErrorContainer)
        separated && working == null -> ToneColors(scheme.primary, scheme.onPrimary)
        else -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    // While it works, the badge turns slowly, as a record does.
    val turn = rememberInfiniteTransition(label = "voiceTurn")
    val spin by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(VOICE_TURN_MS, easing = LinearEasing)), label = "voiceSpin")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = if (separated) MaterialShapes.Sunny else MaterialShapes.Flower,
                morphed = working != null || separated,
                color = tone.container,
                rotationDegrees = if (working != null) spin else 0f,
                modifier = Modifier.size(56.dp),
            ) {
                if (working != null) {
                    LoadingIndicator(color = tone.content, modifier = Modifier.size(38.dp))
                } else {
                    Icon(
                        imageVector = when {
                            failed != null -> Icons.Rounded.ErrorOutline
                            separated -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Layers
                        },
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(
                    text = when {
                        working != null -> voiceStepText(working)
                        failed != null -> stringResource(
                            when (failed) {
                                VoiceProblem.NO_MODEL -> R.string.voice_problem_model
                                VoiceProblem.NO_INSTRUMENTS_MODEL -> R.string.stems_problem_instruments_model
                                VoiceProblem.NO_TRACKS -> R.string.stems_problem_no_tracks
                                VoiceProblem.NO_MEMORY -> R.string.voice_problem_memory
                                VoiceProblem.NO_SPACE -> R.string.voice_problem_space
                                VoiceProblem.NO_SOUND -> R.string.voice_problem_sound
                                VoiceProblem.FAILED -> R.string.voice_problem_failed
                            },
                        )
                        !modelReady && !separated -> stringResource(R.string.voice_needs_model)
                        separated -> stringResource(R.string.voice_done)
                        else -> stringResource(R.string.voice_split_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        PartsStrip(parts = parts, working = working != null)

        if (working != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val fraction = working.fraction
            if (fraction != null) {
                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    text = if (working.fileCount > 1) {
                        stringResource(R.string.speech_file_of, working.fileIndex + 1, working.fileCount, working.fileName)
                    } else {
                        working.fileName
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (fraction != null) {
                    Text(
                        text = String.format(Locale.ROOT, "%d %%", Math.round(fraction * 100)),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = scheme.onSurface,
                    )
                }
            }
            Text(
                stringResource(R.string.voice_working_note),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        val action: (@Composable () -> Unit)? = when {
            working != null -> {
                {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            onStop()
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(stringResource(R.string.voice_stop), style = MaterialTheme.typography.titleSmallEmphasized)
                    }
                }
            }
            separated -> null
            !modelReady -> {
                {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onOpenSmartFeatures()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.speech_open_settings), style = MaterialTheme.typography.titleSmallEmphasized)
                    }
                }
            }
            else -> {
                {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDismissProblem()
                            onSeparate()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (failed != null) {
                                stringResource(R.string.voice_retry)
                            } else {
                                pluralStringResource(R.plurals.voice_separate_files, missingFiles.coerceAtLeast(1), missingFiles.coerceAtLeast(1))
                            },
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    }
                }
            }
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(14.dp))
            action()
        }
    }
}

/**
 * The parts the split gives, in a row: the dialogue, the music, the effects,
 * then the instruments of the music. Each lights up in its own colour, its
 * badge rounding off, once every file in question has it; while the split
 * runs, the ones still to come breathe.
 */
@Composable
private fun PartsStrip(parts: Set<StemKind>, working: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val breath = rememberInfiniteTransition(label = "partsBreath")
    val pulse by breath.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "partsPulse")
    val instruments = StemKind.INSTRUMENTS.all { it in parts }
    val items = listOf(
        Triple(R.string.stem_dialogue, StemKind.DIALOGUE in parts, ToneColors(scheme.primary, scheme.onPrimary)),
        Triple(R.string.stem_music, StemKind.MUSIC in parts, ToneColors(scheme.tertiary, scheme.onTertiary)),
        Triple(R.string.stem_effects, StemKind.EFFECTS in parts, ToneColors(scheme.secondary, scheme.onSecondary)),
        Triple(R.string.stems_instruments_short, instruments, ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)),
    )
    val icons = listOf(Icons.Rounded.RecordVoiceOver, Icons.Rounded.MusicNote, Icons.Rounded.Bolt, Icons.Rounded.Piano)
    val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Clover8Leaf)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, (label, lit, tone) ->
            val container by animateColorAsState(if (lit) tone.container else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "partBg$i")
            val content by animateColorAsState(if (lit) tone.content else scheme.onSurfaceVariant, motion.defaultEffectsSpec(), label = "partFg$i")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = if (working && !lit) pulse else 1f }
                    .clip(RoundedCornerShape(18.dp))
                    .background(scheme.surfaceContainer)
                    .padding(vertical = 8.dp),
            ) {
                MorphingContainer(
                    from = shapes[i],
                    to = MaterialShapes.Circle,
                    morphed = lit,
                    color = container,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(icons[i], contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = if (lit) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** What the run is doing now, in words. */
@Composable
private fun voiceStepText(working: VoiceStatus.Working): String = when (val step = working.step) {
    is VoiceSeparator.Step.Extracting -> stringResource(R.string.voice_step_extracting)
    is VoiceSeparator.Step.Loading -> stringResource(if (step.instruments) R.string.stems_step_loading_instruments else R.string.voice_step_loading)
    is VoiceSeparator.Step.Separating -> stringResource(
        if (step.instruments) R.string.stems_step_instruments else R.string.voice_step_separating,
        Math.round(step.fraction * 100),
    )
    is VoiceSeparator.Step.Saving -> stringResource(R.string.voice_step_saving)
}

/**
 * The two levels: the presets as tiles, two big badges that swell and shrink
 * with their level (the voice's and the rest's), and a slider for each. Until
 * the voice is set apart ([enabled] false) it only shows what it will do.
 */
@Composable
internal fun VoiceMixer(
    voiceLevel: Float,
    backgroundLevel: Float,
    enabled: Boolean,
    onLevels: (String?, Float, Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val preset = VoiceMixPreset.of(voiceLevel, backgroundLevel)
    PanelCard(modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f }) {
        Text(stringResource(R.string.voice_mix), style = MaterialTheme.typography.titleMediumEmphasized)
        OptionTileRow(
            options = listOf(
                TileOption(VoiceMixPreset.ORIGINAL, stringResource(R.string.voice_preset_original), Icons.Rounded.Groups),
                TileOption(VoiceMixPreset.VOICE_ONLY, stringResource(R.string.voice_preset_voice_only), Icons.Rounded.Mic),
                TileOption(VoiceMixPreset.NO_VOICE, stringResource(R.string.voice_preset_no_voice), Icons.Rounded.MusicNote),
                TileOption(VoiceMixPreset.VOICE_UP, stringResource(R.string.voice_preset_voice_up), Icons.Rounded.RecordVoiceOver),
                TileOption(VoiceMixPreset.VOICE_DOWN, stringResource(R.string.voice_preset_voice_down), Icons.Rounded.VolumeDown),
            ),
            selected = preset,
            enabled = enabled,
            onSelect = { chosen ->
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onLevels(null, chosen.voice, chosen.rest)
            },
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(LEVEL_BADGE_ROOM),
        ) {
            VoiceLevelBadge(
                level = voiceLevel,
                icon = Icons.Rounded.RecordVoiceOver,
                label = stringResource(R.string.voice_level_voice),
                from = MaterialShapes.Cookie12Sided,
                to = MaterialShapes.Pill,
                color = MaterialTheme.colorScheme.primary,
                onColor = MaterialTheme.colorScheme.onPrimary,
            )
            VoiceLevelBadge(
                level = backgroundLevel,
                icon = if (backgroundLevel < 0.01f) Icons.Rounded.MusicOff else Icons.Rounded.MusicNote,
                // The music and the effects, together.
                label = stringResource(R.string.voice_level_rest),
                from = MaterialShapes.SoftBurst,
                to = MaterialShapes.Pill,
                color = MaterialTheme.colorScheme.tertiary,
                onColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        ValueSlider(
            label = stringResource(R.string.voice_level_voice),
            valueText = stringResource(R.string.value_percent, (voiceLevel * 100).roundToInt()),
            value = voiceLevel,
            onValueChange = { onLevels("voice", (it * 20).roundToInt() / 20f, backgroundLevel) },
            valueRange = 0f..VoiceStem.MAX_LEVEL,
            icon = Icons.Rounded.Mic,
            defaultValue = 1f,
            enabled = enabled,
            input = ValueInput(step = 0.05f, unit = "%", decimals = 0, scale = 100f),
            startLabel = stringResource(R.string.voice_level_off),
            endLabel = stringResource(R.string.value_percent, 200),
        )
        ValueSlider(
            label = stringResource(R.string.voice_level_rest),
            valueText = stringResource(R.string.value_percent, (backgroundLevel * 100).roundToInt()),
            value = backgroundLevel,
            onValueChange = { onLevels("rest", voiceLevel, (it * 20).roundToInt() / 20f) },
            valueRange = 0f..VoiceStem.MAX_LEVEL,
            icon = Icons.Rounded.Tune,
            defaultValue = 1f,
            enabled = enabled,
            input = ValueInput(step = 0.05f, unit = "%", decimals = 0, scale = 100f),
            startLabel = stringResource(R.string.voice_level_off),
            endLabel = stringResource(R.string.value_percent, 200),
        )
        AnimatedVisibility(
            visible = !enabled,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            PanelHint(stringResource(R.string.voice_mix_locked))
        }
    }
}

/**
 * One of the two parts as a big badge: it swells with its level (a spring that
 * overshoots), grows round the louder it is and fades out as it falls silent.
 */
@Composable
private fun VoiceLevelBadge(
    level: Float,
    icon: ImageVector,
    label: String,
    from: androidx.graphics.shapes.RoundedPolygon,
    to: androidx.graphics.shapes.RoundedPolygon,
    color: Color,
    onColor: Color,
) {
    val motion = MaterialTheme.motionScheme
    // Half size when silent, three quarters as recorded, full at twice as loud.
    val scale by animateFloatAsState(0.5f + 0.5f * (level / VoiceStem.MAX_LEVEL), motion.defaultSpatialSpec(), label = "levelScale")
    val alpha by animateFloatAsState(if (level < 0.01f) 0.35f else 1f, motion.defaultEffectsSpec(), label = "levelAlpha")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(LEVEL_BADGE)) {
            MorphingContainer(
                from = from,
                to = to,
                morphed = level > 1.01f,
                color = color,
                modifier = Modifier
                    .size(LEVEL_BADGE)
                    .graphicsLayer {
                        scaleX = scale.coerceAtLeast(0.3f)
                        scaleY = scale.coerceAtLeast(0.3f)
                        this.alpha = alpha
                    },
            ) {
                Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.size(34.dp))
            }
        }
        Text(
            text = "$label \u00B7 ${(level * 100).roundToInt()} %",
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val VOICE_TURN_MS = 2_400
private val LEVEL_BADGE = 84.dp
private val LEVEL_BADGE_ROOM = 128.dp
