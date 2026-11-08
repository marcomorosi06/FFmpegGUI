@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.dub

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.conversion.DubProblem
import com.cuscus.ffmpeggui.conversion.DubStatus
import com.cuscus.ffmpeggui.core.dub.DubLanguage
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
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
private enum class OriginalVoice(val level: Float?, val labelRes: Int) {
    KEEP(null, R.string.dub_original_keep),
    LOWER(0.15f, R.string.dub_original_lower),
    REMOVE(0f, R.string.dub_original_remove),
}

/**
 * The dub: one group of three blocks, then the button. The language (a strip
 * of tokens, the subtitles' own found by itself and starred), the voice (a
 * carousel of cards, each heard with its own button) and how it sounds (how
 * fast it speaks, what becomes of the video's own voice). While it dubs the
 * group gives way to what is being said and how far it has gone.
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
    val sampler = rememberDubSampler(ui.onSample)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        DubHeader(status, working, ui.modelReady)

        if (!ui.modelReady) {
            FilledTonalButton(onClick = ui.onOpenModels, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dub_get_model))
            }
            return@Column
        }

        AnimatedVisibility(
            visible = !working,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            DubGroup {
                DubBlock(
                    index = 0,
                    count = 3,
                    title = stringResource(R.string.dub_language),
                    icon = Icons.Rounded.Translate,
                    color = scheme.surfaceContainerLow,
                ) {
                    LanguageStrip(
                        languages = languages,
                        selected = language,
                        found = guess?.takeIf { it in languages },
                        enabled = true,
                        onSelect = { chosenLanguage = it },
                    )
                }
                DubBlock(
                    index = 1,
                    count = 3,
                    title = stringResource(R.string.dub_voice),
                    icon = Icons.Rounded.RecordVoiceOver,
                    color = scheme.surfaceContainerLow,
                ) {
                    VoiceCarousel(voices, voice, sampler, speed, enabled = true, onSelect = { chosenVoice = it.key })
                    PanelHint(stringResource(if (voice is DubSpeaker.Piper) R.string.dub_voice_piper_hint else R.string.dub_voice_kokoro_hint))
                    if (voice != null && voice.licence.needsCare) DubLicenceNote(voice)
                }
                DubBlock(
                    index = 2,
                    count = 3,
                    title = stringResource(R.string.dub_how),
                    icon = Icons.Rounded.Tune,
                    color = scheme.surfaceContainerLow,
                ) {
                    ValueSlider(
                        label = stringResource(R.string.dub_speed),
                        valueText = stringResource(R.string.value_percent, (speed * 100).roundToInt()),
                        value = speed,
                        onValueChange = { speed = (it * 20).roundToInt() / 20f },
                        valueRange = 0.7f..1.3f,
                        icon = Icons.Rounded.Speed,
                        defaultValue = 1f,
                        input = ValueInput(step = 0.05f, unit = "%", decimals = 0, scale = 100f),
                    )
                    Text(stringResource(R.string.dub_original), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurfaceVariant)
                    ConnectedChoiceGroup(
                        options = OriginalVoice.entries,
                        selected = original,
                        label = { stringResource(it.labelRes) },
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
                }
            }
        }

        AnimatedVisibility(
            visible = working,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            val fraction = when (status) {
                is DubStatus.Speaking -> status.fraction
                is DubStatus.Separating -> status.fraction
                else -> null
            }
            DubWorking(
                voice = voice,
                details = listOfNotNull(languageName(language), stringResource(R.string.value_percent, (speed * 100).roundToInt())),
                fraction = fraction,
                note = stringResource(R.string.dub_working_note),
                onStop = ui.onStop,
            )
        }

        if (!working) {
            val canLower = original == OriginalVoice.KEEP || ui.voiceModelReady
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    sampler.stop()
                    if (voice != null && voice.licence.needsCare) {
                        askingLicence = true
                    } else {
                        ui.onDismiss()
                        voice?.let { ui.onDub(it, speed, original.level) }
                    }
                },
                enabled = canLower && voice != null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(24.dp))
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

/** The card's badge (a shape that turns while it dubs, and blooms when done), its title and what is going on. */
@Composable
private fun DubHeader(status: DubStatus, working: Boolean, modelReady: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val failed = status is DubStatus.Failed
    val done = status is DubStatus.Done
    val turn = rememberInfiniteTransition(label = "dubTurn")
    val spin by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(DUB_TURN_MS, easing = LinearEasing)), label = "dubSpin")
    Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(stringResource(R.string.dub_title), style = MaterialTheme.typography.titleLargeEmphasized, color = scheme.onSurface)
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
                        DubStatus.Idle -> stringResource(if (modelReady) R.string.dub_hint else R.string.dub_needs_model)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shown is DubStatus.Failed) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * While a dub runs: the voice saying it (its badge pulsing), its language and
 * speed, how far it has gone, and the way to stop.
 */
@Composable
internal fun DubWorking(voice: DubSpeaker?, details: List<String>, fraction: Float?, note: String, onStop: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "dubPulse")
    val beat by pulse.animateFloat(0.9f, 1.06f, infiniteRepeatable(tween(DUB_BEAT_MS), RepeatMode.Reverse), label = "dubBeat")
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.tertiaryContainer)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Sunny,
                to = MaterialShapes.SoftBurst,
                morphed = true,
                color = scheme.tertiary,
                modifier = Modifier.size(48.dp).scale(beat),
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(voice?.label.orEmpty(), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onTertiaryContainer)
                Text(details.joinToString("  \u00B7  "), style = MaterialTheme.typography.labelMedium, color = scheme.onTertiaryContainer.copy(alpha = 0.8f))
            }
            if (fraction != null) {
                Text(
                    stringResource(R.string.value_percent, (fraction * 100).roundToInt()),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = scheme.onTertiaryContainer,
                )
            }
        }
        if (fraction != null) {
            LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer.copy(alpha = 0.8f))
        OutlinedButton(onClick = onStop, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.dub_stop))
        }
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

private const val DUB_TURN_MS = 2_400
private const val DUB_BEAT_MS = 700
