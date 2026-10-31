@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.translate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.translate.LanguageGuess
import com.cuscus.ffmpeggui.core.translate.Languages
import com.cuscus.ffmpeggui.core.translate.TranslationRoute
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * The subtitles panel's card that translates them: the language they are in
 * (found by itself, changed with a tap), the languages the models on the
 * phone can bring them to, one big button; the lines counting up while it
 * works. Times never change: each line keeps its own.
 */

/** What the translate card shows and does. */
class SubtitleTranslationUi(
    val models: List<InstalledModel> = emptyList(),
    val status: TranslationStatus = TranslationStatus.Idle,
    /** The subtitles' lines (to find their language). */
    val lines: () -> List<String> = { emptyList() },
    /** Changes when the subtitles change (a new file, new words). */
    val linesKey: Any? = null,
    val onTranslate: (from: String?, to: String) -> Unit = { _, _ -> },
    val onStop: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** A language's name in the phone's language ("Italiano"); "many languages" for a group. */
@Composable
fun languageLabel(code: String): String = languageName(code, stringResource(R.string.translate_many_languages))

/** As [languageLabel], outside of composition: [many] is what a group is called. */
fun languageName(code: String, many: String): String {
    if (Languages.isGroup(code)) return many
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code.uppercase(Locale.ROOT) else name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

@Composable
fun TranslateCard(ui: SubtitleTranslationUi, onOpenModels: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val status = ui.status
    val working = status as? TranslationStatus.Working
    val abilities = remember(ui.models) { ui.models.map { it.ability } }
    // The language the lines are in, found away from the main thread.
    var guess by remember(ui.linesKey) { mutableStateOf<LanguageGuess.Guess?>(null) }
    LaunchedEffect(ui.linesKey) {
        guess = withContext(Dispatchers.Default) { LanguageGuess.guess(ui.lines()) }
    }
    var chosenFrom by rememberSaveable(ui.linesKey) { mutableStateOf<String?>(null) }
    val from = chosenFrom ?: guess?.language
    val targets = remember(abilities, from) {
        TranslationRoute.reachable(abilities, from).sortedBy { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
    }
    var chosenTo by rememberSaveable { mutableStateOf<String?>(null) }
    val own = Locale.getDefault().language
    val to = chosenTo?.takeIf { it in targets } ?: targets.firstOrNull { it == own } ?: targets.firstOrNull()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val failed = status is TranslationStatus.Failed
            val done = status is TranslationStatus.Done
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Sunny,
                morphed = working != null || done,
                color = when {
                    failed -> scheme.errorContainer
                    done -> scheme.primary
                    else -> scheme.secondaryContainer
                },
                modifier = Modifier.size(52.dp),
            ) {
                when {
                    working != null -> LoadingIndicator(color = scheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
                    else -> Icon(
                        when {
                            failed -> Icons.Rounded.ErrorOutline
                            done -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Translate
                        },
                        contentDescription = null,
                        tint = when {
                            failed -> scheme.onErrorContainer
                            done -> scheme.onPrimary
                            else -> scheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.translate_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                AnimatedContent(
                    targetState = status,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "translateStatus",
                ) { shown ->
                    Text(
                        when (shown) {
                            is TranslationStatus.Working -> stringResource(R.string.translate_working, shown.done, shown.total)
                            is TranslationStatus.Done -> stringResource(R.string.translate_done, languageLabel(shown.language))
                            is TranslationStatus.Failed -> stringResource(
                                when (shown.problem) {
                                    TranslationProblem.NO_MODEL -> R.string.translate_problem_model
                                    TranslationProblem.NO_MEMORY -> R.string.translate_problem_memory
                                    TranslationProblem.NOTHING -> R.string.translate_problem_nothing
                                    TranslationProblem.FAILED -> R.string.translate_problem_failed
                                },
                            )
                            TranslationStatus.Idle -> if (ui.models.isEmpty()) stringResource(R.string.translate_needs_models) else stringResource(R.string.translate_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (ui.models.isEmpty()) {
            FilledTonalButton(onClick = onOpenModels, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.translate_get_models))
            }
            return@Column
        }

        // From: the language found, or the one chosen.
        FromPill(
            from = from,
            found = chosenFrom == null && guess != null,
            options = (abilities.flatMap { it.sources } + listOfNotNull(guess?.language)).distinct().sorted(),
            anyModel = abilities.any { it.sources.isEmpty() },
            enabled = working == null,
            onChoose = { chosenFrom = it },
        )

        if (targets.isEmpty()) {
            PanelHint(stringResource(R.string.translate_no_way, from?.let { languageLabel(it) } ?: stringResource(R.string.translate_unknown_language)))
            OutlinedButton(onClick = onOpenModels, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.translate_add_models))
            }
            return@Column
        }

        Text(stringResource(R.string.translate_into), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { lang ->
                LanguagePill(code = lang, selected = lang == to, enabled = working == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    chosenTo = lang
                }
            }
        }

        AnimatedVisibility(
            visible = working != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearWavyProgressIndicator(progress = { working?.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = ui.onStop, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.translate_stop))
                }
            }
        }
        if (working == null && to != null) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    ui.onDismiss()
                    ui.onTranslate(from, to)
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.translate_into_button, languageLabel(to)), style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
        PanelHint(stringResource(R.string.translate_times_kept))
    }
}

/** "From English, found": a tap lists the languages the models read, and "find it". */
@Composable
private fun FromPill(from: String?, found: Boolean, options: List<String>, anyModel: Boolean, enabled: Boolean, onChoose: (String?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(scheme.tertiaryContainer)
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            val name = from?.let { languageLabel(it) } ?: stringResource(if (anyModel) R.string.translate_any_language else R.string.translate_unknown_language)
            Text(
                stringResource(if (found) R.string.translate_from_found else R.string.translate_from, name),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = scheme.onTertiaryContainer,
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = scheme.onTertiaryContainer)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.translate_find_language)) }, onClick = {
                open = false
                onChoose(null)
            })
            options.forEach { code ->
                DropdownMenuItem(text = { Text(languageLabel(code)) }, onClick = {
                    open = false
                    onChoose(code)
                })
            }
        }
    }
}

/** A language to translate into: a pill that rounds off and fills when chosen, its code in a badge. */
@Composable
private fun LanguagePill(code: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "langPill")
    val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, motion.defaultEffectsSpec(), label = "langText")
    val corner by animateDpAsState(if (selected) 26.dp else 12.dp, motion.defaultSpatialSpec(), label = "langCorner")
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
            modifier = Modifier.size(30.dp).clip(CircleShape).background(if (selected) scheme.onPrimary.copy(alpha = 0.18f) else scheme.secondaryContainer),
        ) {
            Text(code.uppercase(Locale.ROOT).take(3), style = MaterialTheme.typography.labelMedium, color = if (selected) scheme.onPrimary else scheme.onSecondaryContainer)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(languageLabel(code), style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge, color = content)
    }
}
