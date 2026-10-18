@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui

import com.cuscus.ffmpeggui.core.model.VideoPlacement
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.StillMotion
import com.cuscus.ffmpeggui.core.model.MotionKind
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.West
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Piano
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import com.cuscus.ffmpeggui.core.timeline.TimeSpan
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.OutputFormat
import androidx.compose.material.icons.rounded.HdrOn
import com.cuscus.ffmpeggui.core.model.VideoHdr
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import com.cuscus.ffmpeggui.preview.Waveform
import com.cuscus.ffmpeggui.ui.expressive.AspectPreview
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.FilePickCard
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTile
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * The panels of the editor's tools, shown in place of the timeline (see
 * ToolPanel in EditorToolbar.kt). Every panel is built from the expressive
 * blocks in ui/expressive/Panels.kt: choices are shape tiles or connected
 * button groups, values are sliders with their value in a pill, switches are
 * whole cards. Each change is applied at once and shown live in the preview.
 */

/** The two kinds of sound: the footage's own, and the sounds placed on the video. */
private enum class AudioSource(@StringRes val labelRes: Int) {
    ORIGINAL(R.string.audio_original_track),
    SOUNDS(R.string.audio_sounds),
}

/** The two halves of the transform panel. */
private enum class TransformTab(@StringRes val labelRes: Int) {
    ORIENTATION(R.string.transform_orientation),
    CROP(R.string.transform_aspect_ratio),
}

/** The picture settings of the colour panel. */
private enum class ColorSetting(@StringRes val labelRes: Int, val icon: ImageVector) {
    BRIGHTNESS(R.string.color_brightness, Icons.Rounded.LightMode),
    CONTRAST(R.string.color_contrast, Icons.Rounded.Contrast),
    SATURATION(R.string.color_saturation, Icons.Rounded.WaterDrop),
}

/** Longest fade, in seconds. */
private const val MAX_FADE_SECONDS = 5f

/** Colours offered for captions, ARGB. */
private val CAPTION_COLORS = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFFF5252L, 0xFFFFAB40L, 0xFFFFEB3BL,
    0xFF69F0AEL, 0xFF40C4FFL, 0xFF536DFEL, 0xFFE040FBL, 0xFFFF80ABL,
)

// -- Audio ---------------------------------------------------------------------------

/**
 * The sound of the whole video (or of one clip): the footage's own sound, and
 * the list of sounds placed on the video, from where one is added or picked
 * for editing on the timeline.
 */
@Composable
internal fun AudioSectionContent(
    /** True when one clip is edited: only its own sound can be changed then. */
    clipScope: Boolean,
    removeAudio: Boolean,
    volumeLevel: Float,
    normalizeAudio: Boolean,
    /** Sounds placed on the video. */
    sounds: List<AudioClip>,
    onRemoveAudio: (Boolean) -> Unit,
    onVolumeLevel: (Float) -> Unit,
    onNormalizeAudio: (Boolean) -> Unit,
    /** Asks for a file; the sound is placed at the playhead. */
    onAddSound: () -> Unit,
    /** Selects a sound on the timeline, to move, trim or fade it there. */
    onSelectSound: (String) -> Unit,
    /** False for an audio file, whose own sound is always kept (it is what the file is made of). */
    canRemoveOriginal: Boolean = true,
) {
    var chosenSource by remember { mutableStateOf(AudioSource.ORIGINAL) }
    // Placed sounds belong to the whole video.
    val source = if (clipScope) AudioSource.ORIGINAL else chosenSource
    val motion = MaterialTheme.motionScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (clipScope) {
            PanelHint(stringResource(R.string.audio_clip_scope_note))
        } else {
            ConnectedChoiceGroup(
                options = AudioSource.entries,
                selected = source,
                label = { stringResource(it.labelRes) },
                onSelect = { chosenSource = it },
            )
        }

        AnimatedContent(
            targetState = source,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "audioSource",
        ) { shown ->
            when (shown) {
                AudioSource.ORIGINAL -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canRemoveOriginal) {
                        SwitchCard(
                            icon = Icons.Rounded.Mic,
                            title = stringResource(R.string.audio_use_original),
                            subtitle = stringResource(if (removeAudio) R.string.audio_original_excluded else R.string.audio_original_kept),
                            checked = !removeAudio,
                            onCheckedChange = { onRemoveAudio(!it) },
                        )
                    }
                    AnimatedVisibility(
                        visible = !removeAudio || !canRemoveOriginal,
                        enter = expandVertically(motion.defaultEffectsSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PanelCard {
                                ValueSlider(
                                    label = stringResource(R.string.audio_volume),
                                    valueText = stringResource(R.string.value_percent, (volumeLevel * 100).toInt()),
                                    value = volumeLevel,
                                    onValueChange = onVolumeLevel,
                                    valueRange = 0f..2f,
                                    icon = Icons.Rounded.VolumeUp,
                                    defaultValue = 1f,
                                    startLabel = stringResource(R.string.value_percent, 0),
                                    endLabel = stringResource(R.string.value_percent, 200),
                                )
                            }
                            // Loudness levelling works on the final mix.
                            if (!clipScope) {
                                SwitchCard(
                                    icon = Icons.Rounded.GraphicEq,
                                    title = stringResource(R.string.audio_normalize),
                                    subtitle = stringResource(R.string.audio_normalize_hint),
                                    checked = normalizeAudio,
                                    onCheckedChange = onNormalizeAudio,
                                )
                            }
                        }
                    }
                }

                AudioSource.SOUNDS -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onAddSound,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.sound_add), style = MaterialTheme.typography.titleSmallEmphasized)
                    }
                    PanelHint(stringResource(if (sounds.isEmpty()) R.string.sound_none else R.string.sound_list_hint))
                    sounds.forEach { sound ->
                        key(sound.id) {
                            SoundRow(sound = sound, onClick = { onSelectSound(sound.id) })
                        }
                    }
                }
            }
        }
    }
}

/** One placed sound in the list: what it is, when it plays and for how long. */
@Composable
private fun SoundRow(sound: AudioClip, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie6Sided,
            to = MaterialShapes.Cookie6Sided,
            morphed = false,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sound.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.sound_when,
                    clockTime(sound.span.startMs),
                    clockTime(sound.span.endMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Which part of its file the selected sound plays, and where it starts on the
 * video. The ends can also be dragged on the timeline.
 *
 * @param revision bump it when the sound may have changed from outside (undo /
 *                 redo, a drag on the timeline): the trimmer's handles are placed again.
 */
@Composable
internal fun SoundTrimContent(
    sound: AudioClip,
    waveform: Waveform?,
    outputDurationMs: Long,
    revision: Int,
    onSpan: (stepKey: String, span: SoundSpan) -> Unit,
) {
    val span = sound.span
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            // Rebuilt when the range changes from outside, since the handles are placed in pixels once.
            key(sound.id, span.trimStartMs, span.trimEndMs, revision) {
                AudioTimelineTrimmer(
                    durationMs = span.fileDurationMs,
                    startTimeMs = span.trimStartMs,
                    endTimeMs = span.trimEndMs,
                    waveform = waveform,
                    onRangeChange = { start, end -> onSpan("trim", span.withFileRange(start, end)) },
                )
            }
        }
        PanelCard {
            val latest = max(outputDurationMs, span.startMs).coerceAtLeast(1L)
            ValueSlider(
                label = stringResource(R.string.sound_start),
                valueText = clockTime(span.startMs),
                value = span.startMs.toFloat(),
                onValueChange = { ms -> onSpan("start", span.movedTo((ms / 100f).roundToLong() * 100L)) },
                valueRange = 0f..latest.toFloat(),
                icon = Icons.Rounded.Schedule,
                defaultValue = 0f,
                startLabel = clockTime(0L),
                endLabel = clockTime(latest),
            )
        }
        PanelHint(stringResource(R.string.sound_trim_hint))
    }
}

/** Volume of the selected sound: its level, a mute switch and loudness levelling. */
@Composable
internal fun SoundVolumeContent(
    sound: AudioClip,
    onVolume: (Float) -> Unit,
    onMuted: (Boolean) -> Unit,
    onNormalize: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.audio_volume),
                valueText = stringResource(R.string.value_percent, (sound.volume * 100).toInt()),
                value = sound.volume,
                onValueChange = onVolume,
                valueRange = 0f..2f,
                icon = Icons.Rounded.VolumeUp,
                defaultValue = 1f,
                startLabel = stringResource(R.string.value_percent, 0),
                endLabel = stringResource(R.string.value_percent, 200),
                enabled = !sound.muted,
            )
        }
        SwitchCard(
            icon = Icons.Rounded.VolumeOff,
            title = stringResource(R.string.sound_mute),
            subtitle = stringResource(R.string.sound_mute_hint),
            checked = sound.muted,
            onCheckedChange = onMuted,
        )
        SwitchCard(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.sound_level),
            subtitle = stringResource(
                when {
                    !sound.normalize -> R.string.sound_level_hint
                    sound.loudnessLufs == null -> R.string.sound_level_measuring
                    else -> R.string.sound_level_on
                },
            ),
            checked = sound.normalize,
            onCheckedChange = onNormalize,
            enabled = !sound.muted,
        )
        PanelHint(stringResource(R.string.sound_volume_hint))
    }
}

/** Speed of the selected sound, with or without keeping its pitch. */
@Composable
internal fun SoundSpeedContent(sound: AudioClip, onSpeed: (Float) -> Unit, onKeepPitch: (Boolean) -> Unit) {
    val speed = sound.span.rate
    val presets = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.speed_title),
                valueText = stringResource(R.string.value_multiplier, decimal(speed, 2)),
                value = speed,
                onValueChange = { onSpeed(Math.round(it * 20f) / 20f) },
                valueRange = SoundSpan.MIN_SPEED..SoundSpan.MAX_SPEED,
                icon = Icons.Rounded.Speed,
                defaultValue = 1f,
                startLabel = stringResource(R.string.value_multiplier, "0.25"),
                endLabel = stringResource(R.string.value_multiplier, "4"),
            )
            ConnectedChoiceGroup(
                options = presets,
                selected = speed,
                label = { preset -> stringResource(R.string.value_multiplier, decimal(preset, 2).trimEnd('0').trimEnd('.')) },
                onSelect = onSpeed,
            )
        }
        SwitchCard(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.sound_keep_pitch),
            subtitle = stringResource(if (sound.keepPitch) R.string.sound_keep_pitch_on else R.string.sound_keep_pitch_off),
            checked = sound.keepPitch,
            onCheckedChange = onKeepPitch,
        )
        PanelHint(stringResource(R.string.sound_lasts, clockTime(sound.span.lengthMs)))
    }
}

/** Ready-made voices: a pitch shift in semitones. */
internal enum class VoicePreset(@StringRes val labelRes: Int, val icon: ImageVector, val semitones: Float) {
    NORMAL(R.string.voice_normal, Icons.Rounded.RecordVoiceOver, 0f),
    CHIPMUNK(R.string.voice_chipmunk, Icons.Rounded.Pets, 8f),
    HIGH(R.string.voice_high, Icons.Rounded.ArrowUpward, 4f),
    LOW(R.string.voice_low, Icons.Rounded.ArrowDownward, -4f),
    DEEP(R.string.voice_deep, Icons.Rounded.Waves, -8f),
    MONSTER(R.string.voice_monster, Icons.Rounded.SmartToy, -12f),
}

/** Pitch of the selected sound: a ready-made voice or a number of semitones; the speed stays. */
@Composable
internal fun SoundVoiceContent(sound: AudioClip, onPitch: (Float) -> Unit) {
    val pitch = sound.pitchSemitones
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = VoicePreset.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = VoicePreset.entries.firstOrNull { it.semitones == pitch },
            onSelect = { onPitch(it.semitones) },
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.voice_pitch),
                valueText = stringResource(R.string.value_semitones, signedWhole(pitch)),
                value = pitch,
                onValueChange = { onPitch(Math.round(it).toFloat()) },
                valueRange = -MAX_SEMITONES..MAX_SEMITONES,
                icon = Icons.Rounded.Tune,
                defaultValue = 0f,
                startLabel = stringResource(R.string.value_semitones, signedWhole(-MAX_SEMITONES)),
                endLabel = stringResource(R.string.value_semitones, signedWhole(MAX_SEMITONES)),
            )
        }
        PanelHint(stringResource(R.string.voice_hint))
    }
}

/** Ready-made equaliser settings: bass, middle and treble gains in dB. */
private enum class EqPreset(@StringRes val labelRes: Int, val icon: ImageVector, val bass: Float, val mid: Float, val treble: Float) {
    FLAT(R.string.eq_flat, Icons.Rounded.Equalizer, 0f, 0f, 0f),
    BASS(R.string.eq_bass_boost, Icons.Rounded.Speaker, 8f, 0f, 0f),
    VOCAL(R.string.eq_vocal, Icons.Rounded.Mic, -3f, 5f, 2f),
    TELEPHONE(R.string.eq_telephone, Icons.Rounded.Phone, -12f, 6f, -12f),
    BRIGHT(R.string.eq_bright, Icons.Rounded.LightMode, 0f, 0f, 6f),
    WARM(R.string.eq_warm, Icons.Rounded.Whatshot, 4f, 1f, -4f),
}

/** The three-band equaliser of the selected sound. */
@Composable
internal fun SoundEqualizerContent(sound: AudioClip, onEq: (bass: Float, mid: Float, treble: Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = EqPreset.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = EqPreset.entries.firstOrNull { it.bass == sound.bassDb && it.mid == sound.midDb && it.treble == sound.trebleDb },
            onSelect = { onEq(it.bass, it.mid, it.treble) },
        )
        PanelCard {
            listOf(
                Triple(R.string.eq_band_bass, sound.bassDb) { db: Float -> onEq(db, sound.midDb, sound.trebleDb) },
                Triple(R.string.eq_band_mid, sound.midDb) { db: Float -> onEq(sound.bassDb, db, sound.trebleDb) },
                Triple(R.string.eq_band_treble, sound.trebleDb) { db: Float -> onEq(sound.bassDb, sound.midDb, db) },
            ).forEach { (labelRes, value, onChange) ->
                ValueSlider(
                    label = stringResource(labelRes),
                    valueText = stringResource(R.string.value_decibels, signedWhole(value)),
                    value = value,
                    onValueChange = { onChange(Math.round(it).toFloat()) },
                    valueRange = -MAX_EQ_DB..MAX_EQ_DB,
                    icon = Icons.Rounded.Equalizer,
                    defaultValue = 0f,
                )
            }
        }
        PanelHint(stringResource(R.string.eq_hint))
    }
}

/** How many times the selected sound plays in a row. */
@Composable
internal fun SoundRepeatContent(sound: AudioClip, onRepeats: (Int) -> Unit) {
    val repeats = sound.span.loops
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.repeat_times),
                valueText = stringResource(R.string.value_times, repeats),
                value = repeats.toFloat(),
                onValueChange = { onRepeats(Math.round(it)) },
                valueRange = 1f..MAX_REPEATS_SHOWN.toFloat(),
                icon = Icons.Rounded.Repeat,
                defaultValue = 1f,
                startLabel = stringResource(R.string.value_times, 1),
                endLabel = stringResource(R.string.value_times, MAX_REPEATS_SHOWN),
            )
            ConnectedChoiceGroup(
                options = listOf(1, 2, 3, 4, 6, 8),
                selected = repeats,
                label = { stringResource(R.string.value_times, it) },
                onSelect = onRepeats,
            )
        }
        PanelHint(stringResource(R.string.repeat_hint, clockTime(sound.span.lengthMs)))
    }
}

/** Widest pitch shift offered, in semitones (an octave). */
private const val MAX_SEMITONES = 12f

/** Widest equaliser gain offered, in dB. */
private const val MAX_EQ_DB = 12f

/** Most repeats the slider offers. */
private const val MAX_REPEATS_SHOWN = 20

/** "+3", "-5", "0". */
internal fun signedWhole(value: Float): String {
    val whole = Math.round(value)
    return if (whole > 0) "+$whole" else whole.toString()
}

/** Fade-in and fade-out of the selected sound, never longer than the sound itself. */
@Composable
internal fun SoundFadeContent(sound: AudioClip, onFadeIn: (Float) -> Unit, onFadeOut: (Float) -> Unit) {
    val longest = min(MAX_FADE_SECONDS, sound.span.lengthMs / 1000f).coerceAtLeast(0.1f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.fade_in),
                valueText = stringResource(R.string.value_seconds_decimal, decimal(sound.fadeInSeconds)),
                value = sound.fadeInSeconds.coerceAtMost(longest),
                onValueChange = { onFadeIn(roundToTenth(it)) },
                valueRange = 0f..longest,
                icon = Icons.Rounded.Gradient,
                defaultValue = 0f,
            )
            ValueSlider(
                label = stringResource(R.string.fade_out),
                valueText = stringResource(R.string.value_seconds_decimal, decimal(sound.fadeOutSeconds)),
                value = sound.fadeOutSeconds.coerceAtMost(longest),
                onValueChange = { onFadeOut(roundToTenth(it)) },
                valueRange = 0f..longest,
                icon = Icons.Rounded.Gradient,
                defaultValue = 0f,
            )
        }
        PanelHint(stringResource(R.string.sound_fade_hint))
    }
}

/** "1:05.2" on the video's clock. */
private fun clockTime(ms: Long): String {
    val tenths = ms.coerceAtLeast(0L) / 100
    return String.format(java.util.Locale.US, "%d:%02d.%d", tenths / 600, (tenths % 600) / 10, tenths % 10)
}

// -- Speed and fades -----------------------------------------------------------------

/** How the speed is set: one speed for the whole clip, or a curve along it. */
private enum class SpeedMode(@StringRes val labelRes: Int, val icon: ImageVector) {
    CONSTANT(R.string.speed_mode_constant, Icons.Rounded.HorizontalRule),
    CURVE(R.string.speed_mode_curve, Icons.Rounded.Gesture),
}

/**
 * Speed and direction of a clip or of the whole video: one speed, or (on a
 * clip, [curveAllowed]) a curve drawn along it; whether the pitch follows the
 * speed; playing backwards. [footageMs] is the clip's footage, for how long it
 * plays; [marker] where the playhead is along the clip (0..1, playing order).
 */
@Composable
internal fun SpeedSectionContent(
    videoSpeed: Float,
    isReversed: Boolean,
    onVideoSpeed: (Float) -> Unit,
    onIsReversed: (Boolean) -> Unit,
    keepPitch: Boolean = true,
    onKeepPitch: (Boolean) -> Unit = {},
    curve: SpeedCurve? = null,
    curveAllowed: Boolean = false,
    onCurve: (SpeedCurve?) -> Unit = {},
    footageMs: Long? = null,
    marker: () -> Float? = { null },
) {
    val presets = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f)
    val mode = if (curve != null && curveAllowed) SpeedMode.CURVE else SpeedMode.CONSTANT
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (curveAllowed) {
            OptionTileRow(
                options = SpeedMode.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
                selected = mode,
                onSelect = { chosen ->
                    when (chosen) {
                        // A curve starts flat at the speed set until then.
                        SpeedMode.CURVE -> if (curve == null) onCurve(SpeedCurve.flat(videoSpeed.coerceIn(SpeedCurve.MIN_SPEED, SpeedCurve.MAX_SPEED)))
                        // Back to one speed: the curve's average, so the clip lasts as long.
                        SpeedMode.CONSTANT -> if (curve != null) {
                            onVideoSpeed(Math.round(curve.averageSpeed.toFloat().coerceIn(0.25f, 4f) * 100f) / 100f)
                            onCurve(null)
                        }
                    }
                },
                role = Role.Tab,
            )
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "speedMode",
        ) { shown ->
            when (shown) {
                SpeedMode.CONSTANT -> PanelCard {
                    ValueSlider(
                        label = stringResource(R.string.speed_title),
                        valueText = stringResource(R.string.value_multiplier, decimal(videoSpeed, 2)),
                        value = videoSpeed,
                        onValueChange = onVideoSpeed,
                        valueRange = 0.25f..4.0f,
                        icon = Icons.Rounded.Speed,
                        defaultValue = 1f,
                        startLabel = stringResource(R.string.value_multiplier, "0.25"),
                        endLabel = stringResource(R.string.value_multiplier, "4"),
                    )
                    ConnectedChoiceGroup(
                        options = presets,
                        selected = videoSpeed,
                        label = { preset -> stringResource(R.string.value_multiplier, preset.toString()) },
                        onSelect = onVideoSpeed,
                    )
                }
                SpeedMode.CURVE -> SpeedCurveEditor(
                    curve = curve ?: SpeedCurve.flat(1f),
                    onCurve = { onCurve(it) },
                    marker = marker,
                )
            }
        }
        if (!curveAllowed) PanelHint(stringResource(R.string.speed_curve_on_clip))
        if (footageMs != null && footageMs > 0L) {
            val played = curve?.playedLengthMs(footageMs.toDouble())?.let { Math.round(it) }
                ?: Math.round(footageMs / videoSpeed.coerceAtLeast(0.01f).toDouble())
            PanelHint(stringResource(R.string.sound_lasts, clockTime(played)))
        }
        SwitchCard(
            icon = Icons.Rounded.Piano,
            title = stringResource(R.string.speed_pitch_follows),
            subtitle = stringResource(if (keepPitch) R.string.speed_pitch_follows_off else R.string.speed_pitch_follows_on),
            checked = !keepPitch,
            onCheckedChange = { follows -> onKeepPitch(!follows) },
        )
        SwitchCard(
            icon = Icons.Rounded.FastRewind,
            title = stringResource(R.string.speed_reverse),
            subtitle = stringResource(R.string.speed_reverse_hint),
            checked = isReversed,
            onCheckedChange = onIsReversed,
        )
    }
}

/** Fade from black at the start and to black at the end (of the clip or of the whole video). */
@Composable
internal fun FadeSectionContent(
    fadeInSeconds: Float,
    fadeOutSeconds: Float,
    onFadeIn: (Float) -> Unit,
    onFadeOut: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelHint(stringResource(R.string.speed_fades))
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.fade_in),
                valueText = stringResource(R.string.value_seconds_decimal, decimal(fadeInSeconds)),
                value = fadeInSeconds,
                onValueChange = { onFadeIn(roundToTenth(it)) },
                valueRange = 0f..MAX_FADE_SECONDS,
                icon = Icons.Rounded.Gradient,
                defaultValue = 0f,
            )
            ValueSlider(
                label = stringResource(R.string.fade_out),
                valueText = stringResource(R.string.value_seconds_decimal, decimal(fadeOutSeconds)),
                value = fadeOutSeconds,
                onValueChange = { onFadeOut(roundToTenth(it)) },
                valueRange = 0f..MAX_FADE_SECONDS,
                icon = Icons.Rounded.Gradient,
                defaultValue = 0f,
            )
        }
    }
}

// -- Colour --------------------------------------------------------------------------

@Composable
internal fun ColorSectionContent(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    /** A line under the sliders, if any. */
    hint: String? = null,
) {
    var setting by remember { mutableStateOf(ColorSetting.BRIGHTNESS) }
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val changed = brightness != 0f || contrast != 1f || saturation != 1f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OptionTileRow(
                options = ColorSetting.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
                selected = setting,
                onSelect = { setting = it },
                role = Role.Tab,
                modifier = Modifier.weight(1f),
            )
            // Puts all three back at once.
            AnimatedVisibility(
                visible = changed,
                enter = fadeIn(motion.fastEffectsSpec()) + expandHorizontally(motion.fastEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.fastEffectsSpec()),
            ) {
                FilledTonalIconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBrightness(0f)
                    onContrast(1f)
                    onSaturation(1f)
                }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.action_reset))
                }
            }
        }
        AnimatedContent(
            targetState = setting,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "colorSetting",
        ) { shown ->
            PanelCard {
                when (shown) {
                    ColorSetting.BRIGHTNESS -> ValueSlider(
                        label = stringResource(R.string.color_brightness),
                        valueText = signed(brightness),
                        value = brightness,
                        onValueChange = onBrightness,
                        valueRange = -1f..1f,
                        icon = shown.icon,
                        defaultValue = 0f,
                    )
                    ColorSetting.CONTRAST -> ValueSlider(
                        label = stringResource(R.string.color_contrast),
                        valueText = decimal(contrast, 2),
                        value = contrast,
                        onValueChange = onContrast,
                        valueRange = -2f..2f,
                        icon = shown.icon,
                        defaultValue = 1f,
                    )
                    ColorSetting.SATURATION -> ValueSlider(
                        label = stringResource(R.string.color_saturation),
                        valueText = decimal(saturation, 2),
                        value = saturation,
                        onValueChange = onSaturation,
                        valueRange = 0f..3f,
                        icon = shown.icon,
                        defaultValue = 1f,
                    )
                }
            }
        }
        if (hint != null) PanelHint(hint)
    }
}

// -- Texts and pictures ---------------------------------------------------------------

/** What a selected caption says. */
@Composable
internal fun TextEditContent(text: TextOverlay, onText: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OutlinedTextField(
            value = text.text,
            onValueChange = onText,
            label = { Text(stringResource(R.string.text_content)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        PanelHint(stringResource(R.string.text_edit_hint))
    }
    // A new caption is typed at once.
    LaunchedEffect(text.id) { runCatching { focus.requestFocus() } }
}

/** Colour, weight and opacity of a selected caption. */
@Composable
internal fun TextStyleContent(
    text: TextOverlay,
    onColor: (Long) -> Unit,
    onBold: (Boolean) -> Unit,
    onOpacity: (Float) -> Unit,
    onFont: (com.cuscus.ffmpeggui.core.model.TextFont) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // The typeface, each shown in itself.
        FontPicker(selected = text.font, bold = text.isBold, onSelect = onFont)
        PanelCard {
            ColorSwatchRow(colors = CAPTION_COLORS, selected = text.color, onSelect = onColor)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var hexText by remember(text.id, text.color) {
                    mutableStateOf(String.format(java.util.Locale.US, "%06X", (text.color and 0xFFFFFF)))
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = {
                        hexText = it.take(6).uppercase().filter { c -> c in "0123456789ABCDEF" }
                        if (hexText.length == 6) onColor(("FF$hexText").toLong(16))
                    },
                    prefix = { Text(stringResource(R.string.text_color_prefix)) },
                    // The colour in use, also when it is none of the swatches.
                    trailingIcon = {
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(text.color)))
                    },
                    label = { Text(stringResource(R.string.text_color)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
                ToggleButton(
                    checked = text.isBold,
                    onCheckedChange = { bold ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBold(bold)
                    },
                ) {
                    Icon(Icons.Rounded.FormatBold, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.text_bold_short))
                }
            }
        }
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.image_opacity),
                valueText = stringResource(R.string.value_percent, (text.opacity * 100).toInt()),
                value = text.opacity,
                onValueChange = onOpacity,
                valueRange = 0f..1f,
                icon = Icons.Rounded.Opacity,
                defaultValue = 1f,
            )
        }
    }
}

/** Nine places on the frame, as the x / y fractions of [TextOverlay] and [ImageOverlay]. */
private enum class PlacePreset(@StringRes val labelRes: Int, val icon: ImageVector, val x: Float, val y: Float) {
    TOP_LEFT(R.string.place_top_left, Icons.Rounded.NorthWest, 0.05f, 0.05f),
    TOP(R.string.place_top, Icons.Rounded.North, 0.5f, 0.05f),
    TOP_RIGHT(R.string.place_top_right, Icons.Rounded.NorthEast, 0.95f, 0.05f),
    LEFT(R.string.place_left, Icons.Rounded.West, 0.05f, 0.5f),
    CENTER(R.string.place_center, Icons.Rounded.CenterFocusStrong, 0.5f, 0.5f),
    RIGHT(R.string.place_right, Icons.Rounded.East, 0.95f, 0.5f),
    BOTTOM_LEFT(R.string.place_bottom_left, Icons.Rounded.SouthWest, 0.05f, 0.95f),
    BOTTOM(R.string.place_bottom, Icons.Rounded.South, 0.5f, 0.95f),
    BOTTOM_RIGHT(R.string.place_bottom_right, Icons.Rounded.SouthEast, 0.95f, 0.95f),
}

/**
 * Size and place of a selected text or picture: ready-made places and sliders.
 * It can also be dragged (and resized by its corner) on the preview.
 */
@Composable
internal fun OverlayLayoutContent(
    x: Float,
    y: Float,
    size: Float,
    sizeRange: ClosedFloatingPointRange<Float>,
    sizeDefault: Float,
    onSize: (Float) -> Unit,
    onPosition: (x: Float, y: Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = PlacePreset.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = PlacePreset.entries.firstOrNull { kotlin.math.abs(it.x - x) < 0.001f && kotlin.math.abs(it.y - y) < 0.001f },
            onSelect = { onPosition(it.x, it.y) },
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.image_size),
                valueText = stringResource(R.string.value_percent, (size * 100).toInt()),
                value = size.coerceIn(sizeRange),
                onValueChange = onSize,
                valueRange = sizeRange,
                icon = Icons.Rounded.AspectRatio,
                defaultValue = sizeDefault,
            )
            ValueSlider(
                label = stringResource(R.string.image_position_x),
                valueText = stringResource(R.string.value_percent, (x * 100).toInt()),
                // Past the edges (dragged there) the slider waits at its end.
                value = x.coerceIn(0f, 1f),
                onValueChange = { onPosition(it, y) },
                valueRange = 0f..1f,
                startLabel = stringResource(R.string.image_left),
                endLabel = stringResource(R.string.image_right),
            )
            ValueSlider(
                label = stringResource(R.string.image_position_y),
                valueText = stringResource(R.string.value_percent, (y * 100).toInt()),
                // Past the edges (dragged there) the slider waits at its end.
                value = y.coerceIn(0f, 1f),
                onValueChange = { onPosition(x, it) },
                valueRange = 0f..1f,
                startLabel = stringResource(R.string.image_top),
                endLabel = stringResource(R.string.image_bottom),
            )
        }
        PanelHint(stringResource(R.string.overlay_layout_hint))
    }
}

/** Opacity of a selected picture. */
@Composable
internal fun ImageOpacityContent(opacity: Float, onOpacity: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.image_opacity),
                valueText = stringResource(R.string.value_percent, (opacity * 100).toInt()),
                value = opacity,
                onValueChange = onOpacity,
                valueRange = 0f..1f,
                icon = Icons.Rounded.Opacity,
                defaultValue = 1f,
                startLabel = stringResource(R.string.value_percent, 0),
                endLabel = stringResource(R.string.value_percent, 100),
            )
        }
    }
}

/**
 * Turn and mirror of a selected overlay: any angle, quarter turns either way and
 * the two flips. The overlay keeps its size; the box that holds it grows to fit.
 */
@Composable
internal fun OverlayRotateContent(
    degrees: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    onDegrees: (Float) -> Unit,
    onFlipHorizontal: (Boolean) -> Unit,
    onFlipVertical: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            OptionTile(
                label = stringResource(R.string.overlay_turn_left),
                selected = false,
                onClick = { onDegrees(normalizedDegrees(degrees - 90f)) },
                role = Role.Button,
                icon = Icons.Rounded.RotateLeft,
                chosenShape = MaterialShapes.Cookie9Sided,
            )
            OptionTile(
                label = stringResource(R.string.overlay_turn_right),
                selected = false,
                onClick = { onDegrees(normalizedDegrees(degrees + 90f)) },
                role = Role.Button,
                icon = Icons.Rounded.RotateRight,
                chosenShape = MaterialShapes.Clover4Leaf,
            )
            OptionTile(
                label = stringResource(R.string.flip_horizontal),
                selected = flipHorizontal,
                onClick = { onFlipHorizontal(!flipHorizontal) },
                role = Role.Checkbox,
                icon = Icons.Rounded.Flip,
                chosenShape = MaterialShapes.Flower,
            )
            OptionTile(
                label = stringResource(R.string.flip_vertical),
                selected = flipVertical,
                onClick = { onFlipVertical(!flipVertical) },
                role = Role.Checkbox,
                chosenShape = MaterialShapes.Sunny,
                preview = { color ->
                    Icon(
                        imageVector = Icons.Rounded.Flip,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = 90f },
                    )
                },
            )
        }
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.overlay_angle),
                valueText = stringResource(R.string.value_degrees, Math.round(degrees)),
                value = degrees.coerceIn(-180f, 180f),
                // Whole degrees, with a light pull towards the straight angles.
                onValueChange = { onDegrees(snappedDegrees(it)) },
                valueRange = -180f..180f,
                icon = Icons.Rounded.RotateRight,
                defaultValue = 0f,
                startLabel = stringResource(R.string.value_degrees, -180),
                endLabel = stringResource(R.string.value_degrees, 180),
            )
        }
        PanelHint(stringResource(R.string.overlay_rotate_hint))
    }
}

/** [degrees] brought into -180..180. */
private fun normalizedDegrees(degrees: Float): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

/** Whole degrees; within 3 of a multiple of 45 the angle sticks to it. */
private fun snappedDegrees(degrees: Float): Float {
    val nearest = Math.round(degrees / 45f) * 45f
    return if (kotlin.math.abs(degrees - nearest) <= 3f) nearest else Math.round(degrees).toFloat()
}

/** Sound of a selected video overlay: its level and a mute switch. */
@Composable
internal fun OverlayVolumeContent(volume: Float, muted: Boolean, onVolume: (Float) -> Unit, onMuted: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.audio_volume),
                valueText = stringResource(R.string.value_percent, (volume * 100).toInt()),
                value = volume,
                onValueChange = onVolume,
                valueRange = 0f..2f,
                icon = Icons.Rounded.VolumeUp,
                defaultValue = 1f,
                startLabel = stringResource(R.string.value_percent, 0),
                endLabel = stringResource(R.string.value_percent, 200),
                enabled = !muted,
            )
        }
        SwitchCard(
            icon = Icons.Rounded.VolumeOff,
            title = stringResource(R.string.sound_mute),
            subtitle = stringResource(R.string.overlay_mute_hint),
            checked = muted,
            onCheckedChange = onMuted,
        )
    }
}

/** The ways the camera can move over a still, as tiles; null is no move. */
private enum class MotionChoice(@StringRes val labelRes: Int, val icon: ImageVector, val kind: MotionKind?) {
    NONE(R.string.motion_none, Icons.Rounded.Block, null),
    ZOOM_IN(R.string.motion_zoom_in, Icons.Rounded.ZoomIn, MotionKind.ZOOM_IN),
    ZOOM_OUT(R.string.motion_zoom_out, Icons.Rounded.ZoomOut, MotionKind.ZOOM_OUT),
    PAN_LEFT(R.string.motion_pan_left, Icons.Rounded.West, MotionKind.PAN_LEFT),
    PAN_RIGHT(R.string.motion_pan_right, Icons.Rounded.East, MotionKind.PAN_RIGHT),
    PAN_UP(R.string.motion_pan_up, Icons.Rounded.North, MotionKind.PAN_UP),
    PAN_DOWN(R.string.motion_pan_down, Icons.Rounded.South, MotionKind.PAN_DOWN),
}

/**
 * A slow zoom or pan over a still clip ("Ken Burns"): how the camera moves and
 * how far it zooms in.
 */
@Composable
internal fun StillMotionContent(motion: StillMotion?, onMotion: (StillMotion?) -> Unit) {
    val amount = motion?.strength ?: StillMotion.DEFAULT_AMOUNT
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = MotionChoice.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = MotionChoice.entries.firstOrNull { it.kind == motion?.kind },
            onSelect = { choice -> onMotion(choice.kind?.let { StillMotion(it, amount) }) },
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.motion_amount),
                valueText = stringResource(R.string.value_multiplier, decimal(amount, 2)),
                value = amount,
                onValueChange = { value -> motion?.let { onMotion(it.copy(amount = Math.round(value * 20f) / 20f)) } },
                valueRange = StillMotion.MIN_AMOUNT..StillMotion.MAX_AMOUNT,
                icon = Icons.Rounded.ZoomIn,
                defaultValue = StillMotion.DEFAULT_AMOUNT,
                enabled = motion != null,
            )
        }
        PanelHint(stringResource(R.string.motion_hint))
    }
}

/** Fade-in and fade-out of a selected text or picture, never longer than the time it shows. */
@Composable
internal fun OverlayFadeContent(
    fadeInSeconds: Float,
    fadeOutSeconds: Float,
    lengthMs: Long,
    onFadeIn: (Float) -> Unit,
    onFadeOut: (Float) -> Unit,
) {
    val longest = min(MAX_FADE_SECONDS, lengthMs / 1000f).coerceAtLeast(0.1f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.fade_in),
                valueText = stringResource(R.string.value_seconds_decimal, decimal(fadeInSeconds)),
                value = fadeInSeconds.coerceAtMost(longest),
                onValueChange = { onFadeIn(roundToTenth(it)) },
                valueRange = 0f..longest,
                icon = Icons.Rounded.Gradient,
                defaultValue = 0f,
            )
            ValueSlider(
                label = stringResource(R.string.fade_out),
                valueText = stringResource(R.string.value_seconds_decimal, decimal(fadeOutSeconds)),
                value = fadeOutSeconds.coerceAtMost(longest),
                onValueChange = { onFadeOut(roundToTenth(it)) },
                valueRange = 0f..longest,
                icon = Icons.Rounded.Gradient,
                defaultValue = 0f,
            )
        }
        PanelHint(stringResource(R.string.overlay_fade_hint))
    }
}

/** When a selected text or picture shows: its start and how long it lasts, by tenths of a second. */
@Composable
internal fun OverlayTimingContent(span: TimeSpan, outputDurationMs: Long, onSpan: (stepKey: String, span: TimeSpan) -> Unit) {
    val latest = max(outputDurationMs, span.startMs).coerceAtLeast(1L)
    val longest = max(outputDurationMs, span.lengthMs).coerceAtLeast(TimeSpan.MIN_SPAN_MS * 2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.sound_start),
                valueText = clockTime(span.startMs),
                value = span.startMs.toFloat(),
                onValueChange = { ms -> onSpan("start", span.movedTo((ms / 100f).roundToLong() * 100L)) },
                valueRange = 0f..latest.toFloat(),
                icon = Icons.Rounded.Schedule,
                defaultValue = 0f,
                startLabel = clockTime(0L),
                endLabel = clockTime(latest),
            )
            ValueSlider(
                label = stringResource(R.string.timing_length),
                valueText = clockTime(span.lengthMs),
                value = span.lengthMs.toFloat(),
                onValueChange = { ms -> onSpan("length", span.withLength((ms / 100f).roundToLong() * 100L)) },
                valueRange = TimeSpan.MIN_SPAN_MS.toFloat()..longest.toFloat(),
                icon = Icons.Rounded.Timer,
                defaultValue = TimeSpan.DEFAULT_LENGTH_MS.toFloat(),
                startLabel = clockTime(TimeSpan.MIN_SPAN_MS),
                endLabel = clockTime(longest),
            )
        }
        PanelHint(stringResource(R.string.timing_hint))
    }
}

// -- Canvas and transform ------------------------------------------------------------

@Composable
internal fun FormatSectionContent(
    projectRatio: CanvasRatio,
    onProjectRatioChange: (CanvasRatio) -> Unit,
    /** Where the video lies on its canvas (null: in the middle, at its size). */
    placement: VideoPlacement? = null,
    onPlacement: (VideoPlacement?) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = CanvasRatio.entries.map { ratio ->
                TileOption(
                    value = ratio,
                    label = stringResource(ratio.labelRes),
                    icon = if (ratio == CanvasRatio.FIT) Icons.Rounded.AspectRatio else null,
                    preview = ratio.ratio?.let { r -> { color -> AspectPreview(ratio = r, color = color) } },
                )
            },
            selected = projectRatio,
            onSelect = onProjectRatioChange,
        )
        PanelHint(stringResource(if (projectRatio == CanvasRatio.FIT) R.string.canvas_fit_hint else R.string.canvas_fixed_hint))
        VideoPlacementCard(placement = placement ?: VideoPlacement(), onPlacement = onPlacement)
    }
}

/**
 * Where the video lies on its canvas: what to do (tap it on the preview) and,
 * once it was moved, a way back to the middle at its own size.
 */
@Composable
private fun VideoPlacementCard(placement: VideoPlacement, onPlacement: (VideoPlacement?) -> Unit) {
    val haptic = LocalHapticFeedback.current
    PanelHint(stringResource(R.string.placement_hint))
    AnimatedVisibility(visible = !placement.isNeutral) {
        FilledTonalButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onPlacement(null)
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.placement_reset))
        }
    }
}

@Composable
internal fun TransformSectionContent(
    rotation: Rotation,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    aspectRatio: CropAspect,
    onRotation: (Rotation) -> Unit,
    onFlipHorizontal: (Boolean) -> Unit,
    onFlipVertical: (Boolean) -> Unit,
    onAspectRatio: (CropAspect) -> Unit,
) {
    var tab by remember { mutableStateOf(TransformTab.ORIENTATION) }
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ConnectedChoiceGroup(
            options = TransformTab.entries,
            selected = tab,
            label = { stringResource(it.labelRes) },
            onSelect = { tab = it },
        )
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "transformTab",
        ) { shown ->
            when (shown) {
                TransformTab.ORIENTATION -> Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    // The arrow points where the top of the picture goes.
                    Rotation.entries.forEachIndexed { index, turn ->
                        OptionTile(
                            label = stringResource(turn.labelRes),
                            selected = rotation == turn,
                            onClick = { onRotation(turn) },
                            chosenShape = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst, MaterialShapes.Cookie6Sided)[index % 4],
                            preview = { color ->
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = turn.degrees.toFloat() },
                                )
                            },
                        )
                    }
                    OptionTile(
                        label = stringResource(R.string.flip_horizontal),
                        selected = flipHorizontal,
                        onClick = { onFlipHorizontal(!flipHorizontal) },
                        role = Role.Checkbox,
                        icon = Icons.Rounded.Flip,
                        chosenShape = MaterialShapes.Flower,
                    )
                    OptionTile(
                        label = stringResource(R.string.flip_vertical),
                        selected = flipVertical,
                        onClick = { onFlipVertical(!flipVertical) },
                        role = Role.Checkbox,
                        chosenShape = MaterialShapes.Sunny,
                        preview = { color ->
                            Icon(
                                imageVector = Icons.Rounded.Flip,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = 90f },
                            )
                        },
                    )
                }
                TransformTab.CROP -> OptionTileRow(
                    options = CropAspect.entries.map { aspect ->
                        TileOption(
                            value = aspect,
                            label = stringResource(aspect.labelRes),
                            preview = { color -> AspectPreview(ratio = aspect.ratio, color = color) },
                        )
                    },
                    selected = aspectRatio,
                    onSelect = onAspectRatio,
                )
            }
        }
    }
}

// -- Remux, frames (the subtitles have their own file, SubtitlePanels.kt) -------------

@Composable
internal fun RemuxSectionContent(
    useStreamCopy: Boolean,
    onUseStreamCopy: (Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SwitchCard(
            icon = Icons.Rounded.Sync,
            title = stringResource(R.string.remux_title),
            subtitle = stringResource(R.string.remux_hint),
            checked = useStreamCopy,
            onCheckedChange = onUseStreamCopy,
        )
        AnimatedVisibility(
            visible = useStreamCopy,
            enter = expandVertically(motion.defaultEffectsSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            NoteCard(
                icon = Icons.Outlined.Construction,
                title = stringResource(R.string.remux_how),
                text = stringResource(R.string.remux_explanation),
            )
        }
    }
}

@Composable
internal fun FrameExtractionSectionContent(
    frameExtractionMode: FrameExtractionMode,
    frameExtractionRate: Float,
    frameExtractionTimecode: String,
    onFrameExtractionMode: (FrameExtractionMode) -> Unit,
    onFrameExtractionRate: (Float) -> Unit,
    onFrameExtractionTimecode: (String) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = FrameExtractionMode.entries.map { mode ->
                TileOption(
                    value = mode,
                    label = stringResource(mode.labelRes),
                    icon = when (mode) {
                        FrameExtractionMode.DISABLED -> Icons.Rounded.Block
                        FrameExtractionMode.SINGLE -> Icons.Rounded.PhotoCamera
                        FrameExtractionMode.SEQUENCE -> Icons.Rounded.BurstMode
                    },
                )
            },
            selected = frameExtractionMode,
            onSelect = onFrameExtractionMode,
        )
        AnimatedContent(
            targetState = frameExtractionMode,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "extractMode",
        ) { mode ->
            when (mode) {
                FrameExtractionMode.DISABLED -> PanelHint(stringResource(R.string.extract_disabled_hint))
                FrameExtractionMode.SINGLE -> PanelCard {
                    PanelHint(stringResource(R.string.extract_single_hint))
                    OutlinedTextField(
                        value = frameExtractionTimecode,
                        onValueChange = onFrameExtractionTimecode,
                        label = { Text(stringResource(R.string.extract_timecode_label)) },
                        placeholder = { Text(stringResource(R.string.extract_timecode_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        leadingIcon = { Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FrameExtractionMode.SEQUENCE -> PanelCard {
                    PanelHint(stringResource(R.string.extract_sequence_hint))
                    ValueSlider(
                        label = stringResource(R.string.extract_rate),
                        valueText = if (frameExtractionRate >= 1f) {
                            stringResource(R.string.extract_rate_fps, frameExtractionRate.toInt())
                        } else {
                            stringResource(R.string.extract_rate_interval, decimal(1f / frameExtractionRate))
                        },
                        value = frameExtractionRate,
                        onValueChange = onFrameExtractionRate,
                        valueRange = 0.1f..30f,
                        icon = Icons.Rounded.BurstMode,
                        startLabel = stringResource(R.string.extract_rate_min),
                        endLabel = stringResource(R.string.extract_rate_max),
                    )
                    val ratePresets = listOf(
                        stringResource(R.string.extract_preset_every_5s) to 0.2f,
                        stringResource(R.string.extract_preset_every_second) to 1f,
                        stringResource(R.string.extract_preset_5fps) to 5f,
                        stringResource(R.string.extract_preset_15fps) to 15f,
                    )
                    ConnectedChoiceGroup(
                        options = ratePresets,
                        selected = ratePresets.firstOrNull { kotlin.math.abs(frameExtractionRate - it.second) < 0.01f },
                        label = { it?.first.orEmpty() },
                        onSelect = { preset -> preset?.let { onFrameExtractionRate(it.second) } },
                    )
                }
            }
        }
    }
}

/** A tonal note: an icon, an optional [title] and a [text]. */
@Composable
internal fun NoteCard(icon: ImageVector, text: String, title: String? = null) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// -- Command and export --------------------------------------------------------------

/**
 * The FFmpeg command: the one the editor builds, or the one typed by hand
 * (which freezes every other control). [collapsible] folds it behind its title.
 */
@Composable
internal fun CommandSectionContent(
    commandPreview: String,
    isManualMode: Boolean,
    manualCommand: String,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
    collapsible: Boolean = false,
) {
    var expanded by remember { mutableStateOf(!collapsible) }
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "commandChevron",
    )

    PanelCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (collapsible) {
                        Modifier.clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expanded = !expanded
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            MorphingContainer(
                from = MaterialShapes.Square,
                to = MaterialShapes.Cookie4Sided,
                morphed = isManualMode,
                color = if (isManualMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = if (isManualMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(if (isManualMode) R.string.command_manual else R.string.command_preview),
                style = MaterialTheme.typography.titleSmallEmphasized,
                modifier = Modifier.weight(1f),
            )
            if (isManualMode) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onResetManualMode()
                }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.action_reset), tint = MaterialTheme.colorScheme.error)
                }
            }
            if (collapsible) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    modifier = Modifier.graphicsLayer { rotationZ = chevron },
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.defaultEffectsSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalBox(
                    text = if (isManualMode) manualCommand else commandPreview,
                    editable = true,
                    onTextChange = onManualCommandChange,
                )
                if (isManualMode) {
                    Text(
                        text = stringResource(R.string.command_gui_disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * The export settings, in a bottom sheet: output size, frame rate and quality,
 * the command, and the big Convert button.
 */
@Composable
internal fun ExportDialog(
    format: OutputFormat,
    /** Length of what is exported (0 when not known), for the size an audio file will have. */
    durationMs: Long = 0L,
    /** A GIF's own settings, shown instead of resolution, frame rate and quality. */
    gif: GifSettings = GifSettings(),
    gifVideoWidth: Int = 0,
    onGif: (String?, (GifSettings) -> GifSettings) -> Unit = { _, _ -> },
    audioEncoding: AudioEncoding,
    onAudioEncoding: ((AudioEncoding) -> AudioEncoding) -> Unit,
    resolution: Resolution,
    framerate: Framerate,
    qualityLevel: Float,
    commandPreview: String,
    isManualMode: Boolean,
    manualCommand: String,
    onResolution: (Resolution) -> Unit,
    onFramerate: (Framerate) -> Unit,
    onQualityLevel: (Float) -> Unit,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    /** The HDR kind of the footage: NONE for SDR, null when HDR and SDR clips are mixed. */
    footageHdr: VideoHdr? = VideoHdr.NONE,
    keepHdr: Boolean = true,
    onKeepHdr: (Boolean) -> Unit = {},
) {
    val audioOnly = format.isAudioOnly
    val gifMode = format == OutputFormat.GIF
    val showResolution = !audioOnly && !gifMode
    val showFps = !audioOnly && !gifMode && format != OutputFormat.MKV && format != OutputFormat.AVI

    ExportSheet(
        format = format,
        isManualMode = isManualMode,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        command = {
            CommandSectionContent(
                commandPreview = commandPreview,
                isManualMode = isManualMode,
                manualCommand = manualCommand,
                onManualCommandChange = onManualCommandChange,
                onResetManualMode = onResetManualMode,
                collapsible = true,
            )
        },
    ) {
        if (showResolution) {
            PanelCard {
                SectionLabel(stringResource(R.string.export_resolution))
                ConnectedChoiceGroup(
                    options = Resolution.entries,
                    selected = resolution,
                    label = { stringResource(it.labelRes) },
                    onSelect = onResolution,
                    enabled = !isManualMode,
                )
            }
        }
        if (showFps) {
            PanelCard {
                SectionLabel(stringResource(R.string.export_framerate))
                ConnectedChoiceGroup(
                    options = Framerate.entries,
                    selected = framerate,
                    label = { stringResource(it.labelRes) },
                    onSelect = onFramerate,
                    enabled = !isManualMode,
                )
            }
        }
        if (!audioOnly && footageHdr != VideoHdr.NONE) {
            // HDR footage: kept as filmed in an MP4 or MKV whose clips all are HDR, else made SDR.
            val keepable = footageHdr != null && (format == OutputFormat.MP4 || format == OutputFormat.MKV)
            if (keepable) {
                SwitchCard(
                    icon = Icons.Rounded.HdrOn,
                    title = stringResource(R.string.export_keep_hdr),
                    subtitle = stringResource(if (keepHdr) R.string.export_keep_hdr_on else R.string.export_keep_hdr_off),
                    checked = keepHdr,
                    onCheckedChange = onKeepHdr,
                    enabled = !isManualMode,
                )
            } else {
                PanelCard {
                    PanelHint(stringResource(if (footageHdr == null) R.string.export_hdr_mixed else R.string.export_hdr_to_sdr))
                }
            }
        }
        if (gifMode) {
            GifSettingsContent(gif = gif, videoWidth = gifVideoWidth, onGif = onGif, enabled = !isManualMode)
        } else if (audioOnly) {
            AudioEncodingSettings(format, audioEncoding, onAudioEncoding, enabled = !isManualMode, durationMs = durationMs)
        } else {
            PanelCard {
                // Higher is a better picture (a lower CRF / a higher JPEG quality), and a bigger file.
                ValueSlider(
                    label = stringResource(R.string.export_quality),
                    valueText = stringResource(R.string.value_percent, (qualityLevel * 100).toInt()),
                    value = qualityLevel,
                    onValueChange = onQualityLevel,
                    valueRange = 0f..1f,
                    startLabel = stringResource(R.string.export_quality_small),
                    endLabel = stringResource(R.string.export_quality_best),
                    enabled = !isManualMode,
                )
            }
        }
    }
}

/**
 * How an audio file is encoded: the bitrate of a lossy format, the bit depth of
 * a lossless one, and the sample rate (Opus always works at 48 kHz). Each choice
 * says what it is good for, a meter of five bars shows where the bitrate sits,
 * and the size of the file is estimated when it can be (a lossy file, or WAV at
 * a chosen sample rate).
 */
@Composable
private fun AudioEncodingSettings(
    format: OutputFormat,
    encoding: AudioEncoding,
    onEncoding: ((AudioEncoding) -> AudioEncoding) -> Unit,
    enabled: Boolean,
    durationMs: Long = 0L,
) {
    val context = LocalContext.current
    if (format.isLossyAudio) {
        val rates = if (format == OutputFormat.OPUS) OPUS_BITRATES else LOSSY_BITRATES
        val chosen = rates.minBy { kotlin.math.abs(it - encoding.bitrateKbps) }
        val level = rates.indexOf(chosen)
        val bytes = if (durationMs > 0L) chosen * 1000L / 8L * durationMs / 1000L else 0L
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { SectionLabel(stringResource(R.string.export_bitrate)) }
                QualityMeter(level = level, levels = rates.size)
            }
            ConnectedChoiceGroup(
                options = rates,
                selected = chosen,
                label = { it.toString() },
                onSelect = { kbps -> onEncoding { it.copy(bitrateKbps = kbps) } },
                enabled = enabled,
            )
            EncodingNote(
                title = stringResource(R.string.value_kbps, chosen),
                text = stringResource(bitrateUse(level, rates.size)),
                size = if (bytes > 0L) Formatter.formatShortFileSize(context, bytes) else null,
            )
        }
    }
    if (format.isLosslessAudio) {
        val bits = if (encoding.bitDepth >= 24) 24 else 16
        val rate = encoding.sampleRate
        val bytes = if (durationMs > 0L && rate > 0 && format == OutputFormat.WAV) rate.toLong() * (bits / 8) * 2L * durationMs / 1000L else 0L
        PanelCard {
            SectionLabel(stringResource(R.string.export_bit_depth))
            ConnectedChoiceGroup(
                options = listOf(16, 24),
                selected = bits,
                label = { stringResource(R.string.value_bits, it) },
                onSelect = { b -> onEncoding { it.copy(bitDepth = b) } },
                enabled = enabled,
            )
            EncodingNote(
                title = stringResource(R.string.value_bits, bits),
                text = stringResource(if (bits == 24) R.string.export_depth_24 else R.string.export_depth_16),
                size = if (bytes > 0L) Formatter.formatShortFileSize(context, bytes) else null,
            )
        }
    }
    if (format != OutputFormat.OPUS) {
        val rate = encoding.sampleRate.takeIf { it in SAMPLE_RATES } ?: 0
        PanelCard {
            SectionLabel(stringResource(R.string.export_sample_rate))
            ConnectedChoiceGroup(
                options = SAMPLE_RATES,
                selected = rate,
                label = { r -> if (r == 0) stringResource(R.string.resolution_original) else stringResource(R.string.value_kilohertz, decimal(r / 1000f, if (r % 1000 == 0) 0 else 1)) },
                onSelect = { r -> onEncoding { it.copy(sampleRate = r) } },
                enabled = enabled,
            )
            PanelHint(stringResource(sampleRateUse(rate)))
        }
    }
}

/** What a bitrate is good for, by where it sits among [count] choices. */
@StringRes
private fun bitrateUse(level: Int, count: Int): Int = when ((level * 4) / (count - 1).coerceAtLeast(1)) {
    0 -> R.string.export_bitrate_voice
    1 -> R.string.export_bitrate_fair
    2 -> R.string.export_bitrate_good
    3 -> R.string.export_bitrate_great
    else -> R.string.export_bitrate_best
}

@StringRes
private fun sampleRateUse(rate: Int): Int = when (rate) {
    0 -> R.string.export_rate_original
    22050 -> R.string.export_rate_22
    44100 -> R.string.export_rate_44
    48000 -> R.string.export_rate_48
    else -> R.string.export_rate_96
}

/**
 * Five bars, rising, lit up to [level] (of [levels]): where a bitrate sits.
 * Only a picture: the note beside it says the same in words.
 */
@Composable
private fun QualityMeter(level: Int, levels: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(22.dp).clearAndSetSemantics { },
    ) {
        repeat(levels) { index ->
            val lit by animateColorAsState(
                targetValue = if (index <= level) scheme.primary else scheme.surfaceContainerHighest,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "meterBar",
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(8.dp + 14.dp * index / (levels - 1).coerceAtLeast(1))
                    .clip(RoundedCornerShape(3.dp))
                    .background(lit),
            )
        }
    }
}

/** The chosen setting in words: its value, what it is good for, and the size the file will have. */
@Composable
private fun EncodingNote(title: String, text: String, size: String?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.onSurface)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (size != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.export_size_estimate, size),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
        }
    }
}

private val LOSSY_BITRATES = listOf(96, 128, 192, 256, 320)
private val OPUS_BITRATES = listOf(48, 64, 96, 128, 192)
private val SAMPLE_RATES = listOf(0, 22050, 44100, 48000, 96000)

// -- Sound trimmer -------------------------------------------------------------------

/**
 * The part of a sound file that plays: two pill handles over its waveform, with
 * the kept length in a pill. The range is reported when a handle is let go.
 */
@Composable
internal fun AudioTimelineTrimmer(
    durationMs: Long,
    startTimeMs: Long,
    endTimeMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    /** The sound's outline; flat bars while it is being decoded. */
    waveform: Waveform? = null,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var startPx by remember { mutableFloatStateOf(0f) }
    var endPx by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableIntStateOf(NO_HANDLE) }

    val handleWidth by animateFloatAsState(
        targetValue = if (activeHandle != NO_HANDLE) 20f else 14f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "handleWidth",
    )
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val wave = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    // The loudest moment of each slice of the file; quiet sounds are lifted so that
    // their shape still shows.
    val amplitudes = remember(durationMs, waveform) {
        List(WAVE_BARS) { bar ->
            if (waveform == null || durationMs <= 0L) {
                WAITING_BAR
            } else {
                val from = durationMs * bar / WAVE_BARS
                val to = durationMs * (bar + 1) / WAVE_BARS
                kotlin.math.sqrt(waveform.peakBetween(from, to)).coerceIn(WAITING_BAR / 2f, 1f)
            }
        }
    }
    val minGapPx = with(density) { 32.dp.toPx() }
    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentDuration by rememberUpdatedState(durationMs)
    val grabPx = with(density) { 40.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .onSizeChanged { size ->
                    widthPx = size.width.toFloat()
                    if (!placed && durationMs > 0 && widthPx > 0f) {
                        startPx = startTimeMs.toFloat() / durationMs * widthPx
                        endPx = (endTimeMs.takeIf { it > 0 } ?: durationMs).toFloat() / durationMs * widthPx
                        placed = true
                    }
                },
        ) {
            val width = constraints.maxWidth.toFloat()
            if (width > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bar = size.width / amplitudes.size
                    amplitudes.forEachIndexed { index, amp ->
                        val h = size.height * amp * 0.6f
                        drawRoundRect(
                            color = wave,
                            topLeft = Offset(index * bar + bar * 0.2f, (size.height - h) / 2f),
                            size = Size(bar * 0.6f, h),
                            cornerRadius = CornerRadius(bar * 0.3f),
                        )
                    }
                    val shade = Color.Black.copy(alpha = 0.55f)
                    drawRect(shade, size = Size(startPx, size.height))
                    drawRect(shade, topLeft = Offset(endPx, 0f), size = Size((size.width - endPx).coerceAtLeast(0f), size.height))
                    val hw = handleWidth.dp.toPx()
                    val corner = CornerRadius(hw / 2f)
                    drawRoundRect(primary, topLeft = Offset(startPx, 0f), size = Size(endPx - startPx, size.height), cornerRadius = CornerRadius(20.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    listOf(startPx, endPx - hw).forEach { left ->
                        drawRoundRect(primary, topLeft = Offset(left, 0f), size = Size(hw, size.height), cornerRadius = corner)
                        drawLine(
                            onPrimary,
                            Offset(left + hw / 2f, size.height * 0.35f),
                            Offset(left + hw / 2f, size.height * 0.65f),
                            strokeWidth = 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                }

                val keptMs = ((endPx - startPx) / width * durationMs).toLong()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = with(density) { startPx.toDp() })
                        .width(with(density) { (endPx - startPx).coerceAtLeast(0f).toDp() })
                        .fillMaxHeight(),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.value_seconds_decimal, decimal(keptMs / 1000f)),
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(width) {
                            // Horizontal only, so that a vertical swipe still scrolls the panel.
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val toStart = kotlin.math.abs(offset.x - startPx)
                                    val toEnd = kotlin.math.abs(offset.x - endPx)
                                    activeHandle = when {
                                        toStart < grabPx && toStart <= toEnd -> START_HANDLE
                                        toEnd < grabPx -> END_HANDLE
                                        else -> NO_HANDLE
                                    }
                                    if (activeHandle != NO_HANDLE) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onHorizontalDrag = { change, drag ->
                                    if (activeHandle != NO_HANDLE) change.consume()
                                    when (activeHandle) {
                                        START_HANDLE -> startPx = (startPx + drag).coerceIn(0f, (endPx - minGapPx).coerceAtLeast(0f))
                                        END_HANDLE -> endPx = (endPx + drag).coerceIn((startPx + minGapPx).coerceAtMost(width), width)
                                    }
                                },
                                onDragEnd = {
                                    if (activeHandle != NO_HANDLE) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentOnRangeChange(
                                            (startPx / width * currentDuration).toLong(),
                                            (endPx / width * currentDuration).toLong(),
                                        )
                                    }
                                    activeHandle = NO_HANDLE
                                },
                                onDragCancel = { activeHandle = NO_HANDLE },
                            )
                        },
                )
            }
        }

        if (widthPx > 0f) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 6.dp),
            ) {
                Text(
                    text = formatTimeMs((startPx / widthPx * durationMs).toLong()).substringBefore("."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTimeMs((endPx / widthPx * durationMs).toLong()).substringBefore("."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val NO_HANDLE = -1
private const val START_HANDLE = 0
private const val END_HANDLE = 1
private const val WAVE_BARS = 72
/** Height of a bar while the waveform is not known yet. */
private const val WAITING_BAR = 0.12f

// -- Helpers -------------------------------------------------------------------------

private fun roundToTenth(value: Float): Float = Math.round(value * 10f) / 10f

/** Fixed-point number for value labels, independent of the device locale. */
internal fun decimal(value: Float, digits: Int = 1): String =
    String.format(java.util.Locale.US, "%.${digits}f", value)

/** "+0.25", "-0.10", "0.00". */
private fun signed(value: Float): String =
    if (value > 0.004f) "+" + decimal(value, 2) else decimal(value, 2)
