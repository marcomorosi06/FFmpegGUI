@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.dub

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.conversion.DubProblem
import com.cuscus.ffmpeggui.conversion.DubStatus
import com.cuscus.ffmpeggui.core.dub.DubLanguage
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Dubbing the video in the subtitles' language: its state and actions, for the subtitles panel. */
class DubEditing(
    /** Every voice there is to dub with: Kokoro's (when its model is here) and the Piper voices imported. */
    val speakers: List<DubSpeaker> = emptyList(),
    /** The voice model too: the video's own voice can be lowered under the dub. */
    val voiceModelReady: Boolean = false,
    val status: DubStatus = DubStatus.Idle,
    /** The language the subtitles seem to be in (two letters), worked out away from the main thread. */
    val guess: suspend () -> String? = { null },
    /** Changes when the subtitles do, so that the guess is made again. */
    val linesKey: Any? = null,
    /** Dubs with a voice at a speed; the last value is the original voice's level, or null to leave it. */
    val onDub: (DubSpeaker, Float, Float?) -> Unit = { _, _, _ -> },
    val onStop: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    /** A few words in a voice, to hear it: a sound file, or null. */
    val onSample: suspend (DubSpeaker, Float) -> File? = { _, _ -> null },
    val onOpenModels: () -> Unit = {},
) {
    /** There is a voice to dub with. */
    val modelReady: Boolean get() = speakers.isNotEmpty()
}

/** What becomes of the video's own voice under the dub. */
private enum class OriginalVoice(val level: Float?) { KEEP(null), LOWER(0.15f), REMOVE(0f) }

/**
 * The dub: the language (the subtitles' own, found by itself), a voice of it
 * (each can be heard first), how fast it speaks and what becomes of the
 * video's own voice; then the run, line by line, and what came of it.
 */
@Composable
fun DubCard(ui: DubEditing, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val status = ui.status
    val working = status is DubStatus.Speaking || status is DubStatus.Separating

    var guess by remember(ui.linesKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.linesKey) { guess = ui.guess() }
    // The languages there are voices for (two letters), Kokoro's order first.
    val languages = remember(ui.speakers) {
        val order = DubLanguage.entries.map { it.code }
        ui.speakers.map { it.languageCode }.distinct().sortedBy { code -> order.indexOf(code).let { if (it < 0) order.size else it } }
    }
    var chosenLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    val language = chosenLanguage?.takeIf { it in languages }
        ?: guess?.takeIf { it in languages }
        ?: "en".takeIf { it in languages }
        ?: languages.firstOrNull()
        ?: "en"
    // Its voices: the Piper ones first (much quicker to dub with).
    val voices = remember(ui.speakers, language) {
        ui.speakers.filter { it.languageCode == language }.sortedBy { if (it is DubSpeaker.Piper) 0 else 1 }
    }
    var chosenVoice by rememberSaveable { mutableStateOf<String?>(null) }
    val voice = voices.firstOrNull { it.key == chosenVoice } ?: voices.firstOrNull()
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    // Asked before every dub with a voice whose licence has conditions.
    var askingLicence by remember { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf(OriginalVoice.LOWER) }

    // The voice's sample, as it plays.
    val scope = rememberCoroutineScope()
    var sampling by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val playSample: () -> Unit = {
        player?.release()
        player = null
        sampling = true
        scope.launch {
            val file = voice?.let { ui.onSample(it, speed) }
            if (file != null) {
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        sampling = false
                        it.release()
                        if (player === it) player = null
                    }
                    prepare()
                    start()
                }
            } else {
                sampling = false
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val failed = status is DubStatus.Failed
            val done = status is DubStatus.Done
            val turn = rememberInfiniteTransition(label = "dubTurn")
            val spin by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(DUB_TURN_MS, easing = LinearEasing)), label = "dubSpin")
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = if (done) MaterialShapes.Sunny else MaterialShapes.SoftBurst,
                morphed = working || done,
                color = when {
                    failed -> scheme.errorContainer
                    done -> scheme.primary
                    else -> scheme.tertiaryContainer
                },
                rotationDegrees = if (working) spin else 0f,
                modifier = Modifier.size(56.dp),
            ) {
                when {
                    working -> LoadingIndicator(color = scheme.onTertiaryContainer, modifier = Modifier.size(38.dp))
                    else -> Icon(
                        when {
                            failed -> Icons.Rounded.ErrorOutline
                            done -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.RecordVoiceOver
                        },
                        contentDescription = null,
                        tint = when {
                            failed -> scheme.onErrorContainer
                            done -> scheme.onPrimary
                            else -> scheme.onTertiaryContainer
                        },
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dub_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                AnimatedContent(
                    targetState = status,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "dubStatus",
                ) { shown ->
                    Text(
                        when (shown) {
                            is DubStatus.Separating -> stringResource(R.string.dub_separating)
                            is DubStatus.Speaking -> stringResource(R.string.dub_speaking, shown.done, shown.total)
                            is DubStatus.Done -> if (shown.tookMs > 0) {
                                pluralStringResource(R.plurals.dub_done, shown.lines, shown.lines) + " " +
                                    stringResource(R.string.dub_done_took, clockText(shown.lengthMs), clockText(shown.tookMs))
                            } else {
                                pluralStringResource(R.plurals.dub_done, shown.lines, shown.lines)
                            }
                            is DubStatus.Failed -> stringResource(
                                when (shown.problem) {
                                    DubProblem.NO_MODEL -> R.string.dub_problem_model
                                    DubProblem.NO_MEMORY -> R.string.dub_problem_memory
                                    DubProblem.NOTHING -> R.string.dub_problem_nothing
                                    DubProblem.NO_SEPARATION -> R.string.dub_problem_separation
                                    DubProblem.FAILED -> R.string.dub_problem_failed
                                    DubProblem.NO_MARKS -> R.string.marks_problem_missing
                                    DubProblem.MARK_NOT_HELD -> R.string.marks_problem_voice
                                },
                            )
                            DubStatus.Idle -> stringResource(if (ui.modelReady) R.string.dub_hint else R.string.dub_needs_model)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }

        if (!ui.modelReady) {
            FilledTonalButton(onClick = ui.onOpenModels, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dub_get_model))
            }
            return@Column
        }

        // The language: the subtitles' own, found by itself, or another.
        Text(stringResource(R.string.dub_language), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { lang ->
                DubPill(
                    badge = lang.uppercase(Locale.ROOT),
                    label = languageName(lang),
                    selected = lang == language,
                    found = chosenLanguage == null && lang == language && guess == lang,
                    enabled = !working,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    chosenLanguage = lang
                }
            }
        }

        // The voice: each one can be heard first.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dub_voice),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (player != null) {
                        player?.release()
                        player = null
                        sampling = false
                    } else if (!sampling) {
                        playSample()
                    }
                },
                enabled = !working,
            ) {
                when {
                    player != null -> Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.dub_sample_stop))
                    sampling -> LoadingIndicator(modifier = Modifier.size(24.dp))
                    else -> Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.dub_sample))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            voices.forEach { v ->
                DubPill(
                    badge = when (v.female) {
                        true -> "\u2640"
                        false -> "\u2642"
                        // A Piper voice: the quick ones.
                        null -> "\u26A1"
                    },
                    label = v.label,
                    selected = v == voice,
                    found = false,
                    enabled = !working,
                    caution = v.licence.needsCare,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    chosenVoice = v.key
                }
            }
        }
        PanelHint(stringResource(if (voice is DubSpeaker.Piper) R.string.dub_voice_piper_hint else R.string.dub_voice_kokoro_hint))
        if (voice != null && voice.licence.needsCare) DubLicenceNote(voice)

        ValueSlider(
            label = stringResource(R.string.dub_speed),
            valueText = stringResource(R.string.value_percent, (speed * 100).roundToInt()),
            value = speed,
            onValueChange = { speed = (it * 20).roundToInt() / 20f },
            valueRange = 0.7f..1.3f,
            icon = Icons.Rounded.Speed,
            defaultValue = 1f,
            enabled = !working,
            input = ValueInput(step = 0.05f, unit = "%", decimals = 0, scale = 100f),
        )

        // The video's own voice, under the dub.
        Text(stringResource(R.string.dub_original), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurfaceVariant)
        OptionTileRow(
            options = listOf(
                TileOption(OriginalVoice.KEEP, stringResource(R.string.dub_original_keep), Icons.AutoMirrored.Rounded.VolumeUp),
                TileOption(OriginalVoice.LOWER, stringResource(R.string.dub_original_lower), Icons.AutoMirrored.Rounded.VolumeDown),
                TileOption(OriginalVoice.REMOVE, stringResource(R.string.dub_original_remove), Icons.AutoMirrored.Rounded.VolumeOff),
            ),
            selected = original,
            enabled = !working,
            onSelect = { original = it },
        )
        PanelHint(
            stringResource(
                when {
                    original == OriginalVoice.KEEP -> R.string.dub_original_keep_hint
                    ui.voiceModelReady -> R.string.dub_original_apart_hint
                    else -> R.string.dub_original_needs_model
                },
            ),
        )

        AnimatedVisibility(
            visible = working,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val fraction = when (status) {
                    is DubStatus.Speaking -> status.fraction
                    is DubStatus.Separating -> status.fraction
                    else -> null
                }
                if (fraction != null) {
                    LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                PanelHint(stringResource(R.string.dub_working_note))
                OutlinedButton(onClick = ui.onStop, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dub_stop))
                }
            }
        }
        if (!working) {
            val canLower = original == OriginalVoice.KEEP || ui.voiceModelReady
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    player?.release()
                    player = null
                    if (voice != null && voice.licence.needsCare) {
                        askingLicence = true
                    } else {
                        ui.onDismiss()
                        voice?.let { ui.onDub(it, speed, original.level) }
                    }
                },
                enabled = canLower && voice != null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(if (status is DubStatus.Done) R.string.dub_again else R.string.dub_start, voice?.label.orEmpty()),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
        }
    }

    val asked = voice
    if (askingLicence && asked != null) {
        val free = voices.firstOrNull { !it.licence.needsCare }
        DubLicenceDialog(
            speaker = asked,
            canChange = free != null,
            onDub = {
                askingLicence = false
                ui.onDismiss()
                ui.onDub(asked, speed, original.level)
            },
            onChangeVoice = {
                askingLicence = false
                free?.let { chosenVoice = it.key }
            },
            onDismiss = { askingLicence = false },
        )
    }
}

/** The name of the language [code] in the phone's language: "Inglese". */
@Composable
internal fun languageName(code: String): String {
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
    return if (name.isBlank()) code else name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** [ms] as minutes and seconds: "3:07". */
internal fun clockText(ms: Long): String {
    val seconds = (ms + 500) / 1000
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

/**
 * A choice of the card: a pill that rounds off and fills when chosen, a badge
 * in front ([found]: a little star, the language was found by itself).
 */
@Composable
internal fun DubPill(badge: String, label: String, selected: Boolean, found: Boolean, enabled: Boolean, caution: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(if (selected) scheme.tertiary else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "dubPill")
    val content by animateColorAsState(if (selected) scheme.onTertiary else scheme.onSurface, motion.defaultEffectsSpec(), label = "dubPillText")
    val corner by animateDpAsState(if (selected) 26.dp else 12.dp, motion.defaultSpatialSpec(), label = "dubPillCorner")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.9f)
            .clip(RoundedCornerShape(corner.coerceAtLeast(0.dp)))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(30.dp).clip(CircleShape).background(if (selected) scheme.onTertiary.copy(alpha = 0.18f) else scheme.tertiaryContainer),
        ) {
            if (found) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = if (selected) scheme.onTertiary else scheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
            } else {
                Text(badge, style = MaterialTheme.typography.labelMedium, color = if (selected) scheme.onTertiary else scheme.onTertiaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge, color = content)
        if (caution) {
            // Its licence has conditions (see DubLicenceNote).
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Rounded.Gavel, contentDescription = stringResource(R.string.dub_licence_caution), tint = content, modifier = Modifier.size(16.dp))
        }
    }
}

private const val DUB_TURN_MS = 2_400
