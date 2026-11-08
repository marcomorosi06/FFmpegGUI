@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.dub

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.conversion.AutoDubProblem
import com.cuscus.ffmpeggui.conversion.AutoDubStatus
import com.cuscus.ffmpeggui.conversion.AutoDubStep
import com.cuscus.ffmpeggui.core.dub.DubLanguage
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import java.util.Locale

/** The dub in one go: what it needs and does, for the editor's "Dub" tool. */
class AutoDubUi(
    /** Every voice to dub with (see DubSpeaker.all). */
    val speakers: List<DubSpeaker> = emptyList(),
    val status: AutoDubStatus = AutoDubStatus.Idle,
    /** The video already has subtitles (a file, or every file listened to). */
    val hasSubtitles: Boolean = false,
    /** The language of those subtitles (two letters), worked out away from the main thread. */
    val guess: suspend () -> String? = { null },
    /** Changes when the subtitles do, so that the guess is made again. */
    val linesKey: Any? = null,
    /** The speech model is on the phone. */
    val speechReady: Boolean = false,
    /** The voice model is on the phone. */
    val voiceModelReady: Boolean = false,
    /** Some of the video's files have no voice set apart yet. */
    val separationNeeded: Boolean = true,
    /** Whether the translation models bring a text in the first language (null: unknown) to the second. */
    val canTranslate: (String?, String) -> Boolean = { _, _ -> false },
    val onStart: (String, DubSpeaker, Float?) -> Unit = { _, _, _ -> },
    /** A few words in a voice, to hear it: a sound file, or null. */
    val onSample: suspend (DubSpeaker, Float) -> java.io.File? = { _, _ -> null },
    val onStop: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onOpenModels: () -> Unit = {},
)

/** What becomes of the video's own voice. */
private enum class Original(val level: Float, val labelRes: Int) {
    REMOVE(0f, R.string.dub_original_remove),
    LOWER(0.15f, R.string.dub_original_lower),
}

/** How a step of the plan is, before or during the run. */
private enum class StepState { TODO, WORKING, DONE, SKIPPED, MISSING }

/**
 * The editor's "Dub" tool. Before it starts: one group of blocks (the
 * language to dub into, a strip of tokens; the voice, a carousel of cards
 * each heard with its own button; what becomes of the video's own voice),
 * then the plan as four nodes on a rail (what is not needed is struck off,
 * what is missing says so under it) and the button. While it runs, and after,
 * the four steps as rows that tick off.
 */
@Composable
fun AutoDubSectionContent(ui: AutoDubUi) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val status = ui.status
    val working = status is AutoDubStatus.Working

    var guess by remember(ui.linesKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.linesKey, ui.hasSubtitles) { guess = if (ui.hasSubtitles) ui.guess() else null }
    val languages = remember(ui.speakers) {
        val order = DubLanguage.entries.map { it.code }
        ui.speakers.map { it.languageCode }.distinct().sortedBy { code -> order.indexOf(code).let { if (it < 0) order.size else it } }
    }
    val phone = Locale.getDefault().language
    var chosenLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    val target = chosenLanguage?.takeIf { it in languages }
        ?: (if (guess != phone) phone else "en").takeIf { it in languages }
        ?: "en".takeIf { it in languages }
        ?: languages.firstOrNull()
    val voices = remember(ui.speakers, target) {
        ui.speakers.filter { it.languageCode == target }.sortedBy { if (it is DubSpeaker.Piper) 0 else 1 }
    }
    var chosenVoice by rememberSaveable { mutableStateOf<String?>(null) }
    val voice = voices.firstOrNull { it.key == chosenVoice } ?: voices.firstOrNull()
    var original by rememberSaveable { mutableStateOf(Original.REMOVE) }
    var askingLicence by remember { mutableStateOf(false) }
    val sampler = rememberDubSampler(ui.onSample)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Hero(status, target, voice)

        if (ui.speakers.isEmpty()) {
            PanelHint(stringResource(R.string.autodub_no_voice))
            FilledTonalButton(onClick = ui.onOpenModels, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.autodub_open_models))
            }
            return@Column
        }

        // The four steps: what each will do and what is missing, then how far each has gone.
        val running = status as? AutoDubStatus.Working
        val skipped = when (status) {
            is AutoDubStatus.Working -> status.skipped
            is AutoDubStatus.Done -> status.skipped
            else -> emptySet()
        }
        val source = running?.from ?: (status as? AutoDubStatus.Done)?.from ?: guess
        fun stateOf(step: AutoDubStep, missing: Boolean, notNeeded: Boolean): StepState = when (status) {
            is AutoDubStatus.Working -> when {
                step in skipped -> StepState.SKIPPED
                step == status.step -> StepState.WORKING
                step.ordinal < status.step.ordinal -> StepState.DONE
                else -> StepState.TODO
            }
            is AutoDubStatus.Done -> if (step in skipped) StepState.SKIPPED else StepState.DONE
            is AutoDubStatus.Failed -> when {
                step == status.step -> StepState.MISSING
                step.ordinal < status.step.ordinal -> StepState.DONE
                else -> StepState.TODO
            }
            AutoDubStatus.Idle -> when {
                missing -> StepState.MISSING
                notNeeded -> StepState.SKIPPED
                else -> StepState.TODO
            }
        }
        val translateNeeded = target != null && source != target
        val translationMissing = target != null && translateNeeded && !ui.canTranslate(source, target)
        val steps = listOf(
            PlanStep(
                AutoDubStep.TRANSCRIBE,
                Icons.Rounded.ClosedCaption,
                stringResource(R.string.autodub_step_transcribe),
                when {
                    ui.hasSubtitles -> stringResource(R.string.autodub_transcribe_skip)
                    !ui.speechReady -> stringResource(R.string.autodub_transcribe_missing)
                    else -> stringResource(R.string.autodub_transcribe_text)
                },
                stateOf(AutoDubStep.TRANSCRIBE, missing = !ui.hasSubtitles && !ui.speechReady, notNeeded = ui.hasSubtitles),
            ),
            PlanStep(
                AutoDubStep.TRANSLATE,
                Icons.Rounded.Translate,
                stringResource(R.string.autodub_step_translate),
                when {
                    target == null -> ""
                    source == null && !ui.hasSubtitles -> stringResource(R.string.autodub_translate_later, languageName(target))
                    !translateNeeded -> stringResource(R.string.autodub_translate_skip, languageName(target))
                    translationMissing -> stringResource(R.string.autodub_translate_missing, source?.let { languageName(it) } ?: "?", languageName(target))
                    else -> stringResource(R.string.autodub_translate_text, source?.let { languageName(it) } ?: "?", languageName(target))
                },
                stateOf(AutoDubStep.TRANSLATE, missing = translationMissing && (source != null || ui.hasSubtitles), notNeeded = !translateNeeded && source != null),
            ),
            PlanStep(
                AutoDubStep.SEPARATE,
                Icons.Rounded.Mic,
                stringResource(R.string.autodub_step_separate),
                when {
                    !ui.separationNeeded -> stringResource(R.string.autodub_separate_skip)
                    !ui.voiceModelReady -> stringResource(R.string.autodub_separate_missing)
                    else -> stringResource(R.string.autodub_separate_text)
                },
                stateOf(AutoDubStep.SEPARATE, missing = ui.separationNeeded && !ui.voiceModelReady, notNeeded = !ui.separationNeeded),
            ),
            PlanStep(
                AutoDubStep.DUB,
                Icons.Rounded.RecordVoiceOver,
                stringResource(R.string.autodub_step_dub),
                voice?.let { stringResource(R.string.autodub_dub_text, it.label) } ?: "",
                stateOf(AutoDubStep.DUB, missing = voice == null, notNeeded = false),
            ),
        )
        val idle = status is AutoDubStatus.Idle
        val missing = idle && steps.any { it.state == StepState.MISSING }

        AnimatedVisibility(
            visible = idle,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            DubGroup {
                DubBlock(
                    index = 0,
                    count = 4,
                    title = stringResource(R.string.autodub_into),
                    icon = Icons.Rounded.Translate,
                    color = scheme.surfaceContainerHigh,
                    // The language the video was heard in, when it is known.
                    trailing = if (source != null) {
                        {
                            Text(
                                stringResource(R.string.autodub_from, languageName(source)),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        null
                    },
                ) {
                    LanguageStrip(languages, target, found = null, enabled = true, onSelect = { chosenLanguage = it })
                }
                DubBlock(
                    index = 1,
                    count = 4,
                    title = stringResource(R.string.dub_voice),
                    icon = Icons.Rounded.RecordVoiceOver,
                    color = scheme.surfaceContainerHigh,
                ) {
                    VoiceCarousel(voices, voice, sampler, speed = 1f, enabled = true, onSelect = { chosenVoice = it.key })
                    if (voice != null && voice.licence.needsCare) DubLicenceNote(voice)
                }
                DubBlock(
                    index = 2,
                    count = 4,
                    title = stringResource(R.string.dub_original),
                    icon = Icons.AutoMirrored.Rounded.VolumeDown,
                    color = scheme.surfaceContainerHigh,
                ) {
                    ConnectedChoiceGroup(
                        options = Original.entries,
                        selected = original,
                        label = { stringResource(it.labelRes) },
                        onSelect = { original = it },
                    )
                }
                DubBlock(
                    index = 3,
                    count = 4,
                    title = stringResource(R.string.autodub_plan),
                    icon = Icons.Rounded.Checklist,
                    color = scheme.surfaceContainerHigh,
                ) {
                    PlanRail(steps)
                    val gaps = steps.filter { it.state == StepState.MISSING }
                    if (gaps.isEmpty()) {
                        PanelHint(stringResource(R.string.autodub_plan_ready))
                    } else {
                        gaps.forEach { gap -> PlanGap(gap) }
                    }
                }
            }
        }

        if (!idle) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                steps.forEachIndexed { index, step ->
                    StepRow(step, index, steps.size, if (step.step == running?.step) running.fraction else null)
                }
            }
        }

        when (status) {
            is AutoDubStatus.Working -> OutlinedButton(onClick = ui.onStop, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dub_stop))
            }
            is AutoDubStatus.Failed, is AutoDubStatus.Done -> FilledTonalButton(onClick = ui.onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.translate_ok))
            }
            AutoDubStatus.Idle -> Unit
        }

        if (idle) {
            if (missing) {
                FilledTonalButton(onClick = ui.onOpenModels, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.autodub_open_models))
                }
            }
            Button(
                onClick = {
                    val v = voice ?: return@Button
                    val to = target ?: return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    sampler.stop()
                    if (v.licence.needsCare) {
                        askingLicence = true
                    } else {
                        ui.onDismiss()
                        ui.onStart(to, v, original.level)
                    }
                },
                enabled = voice != null && target != null && !missing,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.autodub_start, target?.let { languageName(it) } ?: ""),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            PanelHint(stringResource(R.string.autodub_hint))
        }
    }

    val asked = voice
    val to = target
    if (askingLicence && asked != null && to != null) {
        val free = voices.firstOrNull { !it.licence.needsCare }
        DubLicenceDialog(
            speaker = asked,
            canChange = free != null,
            onDub = {
                askingLicence = false
                ui.onDismiss()
                ui.onStart(to, asked, original.level)
            },
            onChangeVoice = {
                askingLicence = false
                free?.let { chosenVoice = it.key }
            },
            onDismiss = { askingLicence = false },
        )
    }
}

/**
 * The plan before it runs: its four steps as nodes on a rail, each with its
 * icon (a dash where it is not needed, a warning where something is missing)
 * and its name under it.
 */
@Composable
private fun PlanRail(steps: List<PlanStep>) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth()) {
        NodeRail(scheme.outlineVariant, steps.size, y = PLAN_NODE / 2, modifier = Modifier.matchParentSize())
        Row(modifier = Modifier.fillMaxWidth()) {
            steps.forEach { step ->
                val (badge, content) = when (step.state) {
                    StepState.MISSING -> scheme.errorContainer to scheme.onErrorContainer
                    StepState.SKIPPED -> scheme.surfaceContainerHighest to scheme.outline
                    else -> scheme.secondaryContainer to scheme.onSecondaryContainer
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    MorphingContainer(
                        from = MaterialShapes.Cookie6Sided,
                        to = MaterialShapes.Clover4Leaf,
                        morphed = step.state == StepState.MISSING,
                        color = badge,
                        modifier = Modifier.size(PLAN_NODE),
                    ) {
                        Icon(
                            when (step.state) {
                                StepState.MISSING -> Icons.Rounded.WarningAmber
                                StepState.SKIPPED -> Icons.Rounded.Remove
                                else -> step.icon
                            },
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        step.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (step.state == StepState.SKIPPED) scheme.outline else scheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** What a step of the plan is missing, said under the rail. */
@Composable
private fun PlanGap(step: PlanStep) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(step.icon, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            step.title + ": " + step.text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onErrorContainer,
        )
    }
}

/** One step of the plan: what it is, what it will do, how it is. */
private class PlanStep(val step: AutoDubStep, val icon: ImageVector, val title: String, val text: String, val state: StepState)

/** The badge, title and words of the run, as it goes. */
@Composable
private fun Hero(status: AutoDubStatus, target: String?, voice: DubSpeaker?) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val working = status is AutoDubStatus.Working
    val done = status is AutoDubStatus.Done
    val failed = status is AutoDubStatus.Failed
    val turn = rememberInfiniteTransition(label = "autoDubTurn")
    val spin by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "autoDubSpin")
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
            modifier = Modifier.size(60.dp),
        ) {
            when {
                working -> LoadingIndicator(color = scheme.onTertiaryContainer, modifier = Modifier.size(40.dp))
                else -> Icon(
                    when {
                        failed -> Icons.Rounded.ErrorOutline
                        done -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.AutoAwesome
                    },
                    contentDescription = null,
                    tint = when {
                        failed -> scheme.onErrorContainer
                        done -> scheme.onPrimary
                        else -> scheme.onTertiaryContainer
                    },
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.autodub_title), style = MaterialTheme.typography.titleLargeEmphasized, color = scheme.onSurface)
            AnimatedContent(
                targetState = status,
                contentKey = { it::class to (it as? AutoDubStatus.Working)?.step },
                transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "autoDubStatus",
            ) { shown ->
                Text(
                    when (shown) {
                        is AutoDubStatus.Working -> stringResource(
                            when (shown.step) {
                                AutoDubStep.TRANSCRIBE -> R.string.autodub_doing_transcribe
                                AutoDubStep.TRANSLATE -> R.string.autodub_doing_translate
                                AutoDubStep.SEPARATE -> R.string.autodub_doing_separate
                                AutoDubStep.DUB -> R.string.autodub_doing_dub
                            },
                        )
                        is AutoDubStatus.Done -> stringResource(R.string.autodub_done, shown.lines, clockText(shown.lengthMs), clockText(shown.tookMs))
                        is AutoDubStatus.Failed -> stringResource(
                            when (shown.problem) {
                                AutoDubProblem.NO_SPEECH_MODEL -> R.string.autodub_problem_speech
                                AutoDubProblem.NOTHING_SAID -> R.string.autodub_problem_nothing
                                AutoDubProblem.NO_TRANSLATION_MODEL -> R.string.autodub_problem_translation
                                AutoDubProblem.NO_VOICE_MODEL -> R.string.autodub_problem_voice
                                AutoDubProblem.NO_DUB_MODEL -> R.string.autodub_problem_dub
                                AutoDubProblem.NO_MARKS -> R.string.marks_problem_missing
                                AutoDubProblem.NO_MEMORY -> R.string.dub_problem_memory
                                AutoDubProblem.FAILED -> R.string.autodub_problem_failed
                            },
                        )
                        AutoDubStatus.Idle -> stringResource(R.string.autodub_intro)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** A step as a row of the group: its badge (what it is, or how it went), its words and, while it runs, its progress. */
@Composable
private fun StepRow(step: PlanStep, index: Int, count: Int, fraction: Float?) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val (badge, content) = when (step.state) {
        StepState.WORKING -> scheme.tertiary to scheme.onTertiary
        StepState.DONE -> scheme.primary to scheme.onPrimary
        StepState.MISSING -> scheme.errorContainer to scheme.onErrorContainer
        StepState.SKIPPED -> scheme.surfaceContainerHighest to scheme.outline
        StepState.TODO -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    val container by animateColorAsState(
        if (step.state == StepState.WORKING) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
        motion.defaultEffectsSpec(),
        label = "stepRow",
    )
    val shape = com.cuscus.ffmpeggui.settings.groupShape(index, count)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie6Sided,
                to = MaterialShapes.Circle,
                morphed = step.state == StepState.DONE,
                color = badge,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (step.state) {
                        StepState.WORKING -> LoadingIndicator(color = content, modifier = Modifier.size(30.dp))
                        StepState.DONE -> Icon(Icons.Rounded.Check, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                        StepState.SKIPPED -> Icon(Icons.Rounded.Remove, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                        StepState.MISSING -> Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                        StepState.TODO -> Icon(step.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    step.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = if (step.state == StepState.SKIPPED) scheme.onSurfaceVariant else scheme.onSurface,
                )
                if (step.text.isNotEmpty()) {
                    Text(
                        step.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (step.state == StepState.MISSING) scheme.error else scheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (step.state == StepState.WORKING) {
            Spacer(modifier = Modifier.height(10.dp))
            if (fraction != null) {
                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val PLAN_NODE = 44.dp
