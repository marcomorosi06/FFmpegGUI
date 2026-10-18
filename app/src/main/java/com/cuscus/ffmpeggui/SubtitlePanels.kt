@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.conversion.SpeechProblem
import com.cuscus.ffmpeggui.conversion.SpeechStatus
import com.cuscus.ffmpeggui.core.model.TextFont
import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLayout
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLook
import com.cuscus.ffmpeggui.core.subtitles.SubtitlePreset
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle
import com.cuscus.ffmpeggui.core.subtitles.WordHighlight
import com.cuscus.ffmpeggui.speech.SpeechTranscriber
import com.cuscus.ffmpeggui.subtitles.SubtitleFonts
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.FilePickCard
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * The subtitles panel: where they come from (what is said, made on this phone,
 * or a file), how they look (a preset, then size, place, colours, outline or
 * box, the word being said), and the lines themselves, to correct and to save
 * as a .srt file. Every change shows on the preview at once.
 */

/** Everything the subtitles panel and the preview need about subtitles. */
class SubtitleEditing(
    val fileUri: Uri? = null,
    val fileName: String = "",
    val fileCues: List<SubtitleCue> = emptyList(),
    /** False for a SubStation Alpha file, which keeps its own look. */
    val fileStyled: Boolean = true,
    /** The words heard and kept, by source id, on each file's clock. */
    val words: Map<String, List<SpokenWord>> = emptyMap(),
    /** The words being found now, not kept yet. */
    val liveWords: Map<String, List<SpokenWord>> = emptyMap(),
    val style: SubtitleStyle = SubtitleStyle(),
    val speech: SpeechStatus = SpeechStatus.Idle,
    /** The speech model is on the phone and checked. */
    val modelReady: Boolean = false,
    /** Files of the video not listened to yet. */
    val unheardFiles: Int = 0,
    val fonts: SubtitleFonts? = null,
    /** The names of the files, by source id. */
    val sourceNames: Map<String, String> = emptyMap(),
    val onSetFile: (Uri?, String) -> Unit = { _, _ -> },
    /** Listens: to the files not done yet, or to all again (true). */
    val onGenerate: (Boolean) -> Unit = {},
    val onStop: () -> Unit = {},
    val onDismissProblem: () -> Unit = {},
    val onStyle: (String?, (SubtitleStyle) -> SubtitleStyle) -> Unit = { _, _ -> },
    val onClearSpoken: () -> Unit = {},
    /** Makes the spoken subtitles into ordinary texts, one per line. */
    val onToTexts: () -> Unit = {},
    /** Rewrites a line: source id, its start and end on the file's clock, the new text. */
    val onEditLine: (String, Long, Long, String) -> Unit = { _, _, _, _ -> },
    /** The spoken subtitles as a .srt file, timed like the export. */
    val srt: () -> String? = { null },
    val onOpenSmartFeatures: () -> Unit = {},
) {
    /** The words the preview shows: those kept and those being found. */
    val shownWords: Map<String, List<SpokenWord>>
        get() = if (liveWords.isEmpty()) words else (words.keys + liveWords.keys).associateWith { words[it].orEmpty() + liveWords[it].orEmpty() }

    val hasSpoken: Boolean get() = fileUri == null && shownWords.values.any { it.isNotEmpty() }

    /** Something is drawn with the style: spoken subtitles, or a SubRip file. */
    val styled: Boolean get() = hasSpoken || (fileUri != null && fileStyled)
}

private enum class SubtitlePage(val label: Int) { SOURCE(R.string.subtitle_page_source), STYLE(R.string.subtitle_page_style), LINES(R.string.subtitle_page_lines) }

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFFL, 0xFFFFD60AL, 0xFFFFEB3BL, 0xFF30D158L, 0xFF64D2FFL,
    0xFF0A84FFL, 0xFFBF5AF2L, 0xFFFF375FL, 0xFFFF9F0AL, 0xFF000000L,
)

@Composable
internal fun SubtitleSectionContent(subtitles: SubtitleEditing) {
    var page by rememberSaveable { mutableStateOf(SubtitlePage.SOURCE) }
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ConnectedChoiceGroup(
            options = SubtitlePage.entries,
            selected = page,
            label = { stringResource(it.label) },
            onSelect = { page = it },
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
            label = "subtitlePage",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                when (shown) {
                    SubtitlePage.SOURCE -> SourcePage(subtitles)
                    SubtitlePage.STYLE -> StylePage(subtitles)
                    SubtitlePage.LINES -> LinesPage(subtitles)
                }
            }
        }
    }
}

// -- Where they come from --------------------------------------------------------------

@Composable
private fun SourcePage(subtitles: SubtitleEditing) {
    val fallbackName = stringResource(R.string.subtitle_fallback_name)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) subtitles.onSetFile(uri, uri.lastPathSegment?.substringAfterLast("/") ?: fallbackName)
    }
    SpeechCard(subtitles)
    Text(
        text = stringResource(R.string.subtitle_or_file),
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
    FilePickCard(
        icon = Icons.Rounded.ClosedCaption,
        title = if (subtitles.fileUri != null) subtitles.fileName else stringResource(R.string.subtitle_none),
        subtitle = stringResource(
            when {
                subtitles.fileUri == null -> R.string.subtitle_formats
                subtitles.fileStyled -> R.string.subtitle_loaded_styled
                else -> R.string.subtitle_loaded_own_look
            },
        ),
        picked = subtitles.fileUri != null,
        pickLabel = stringResource(R.string.action_choose_file),
        removeDescription = stringResource(R.string.subtitle_remove),
        onPick = { picker.launch(arrayOf("text/plain", "application/x-subrip", "*/*")) },
        onRemove = { subtitles.onSetFile(null, "") },
    )
    if (subtitles.fileUri == null && subtitles.hasSpoken) PanelHint(stringResource(R.string.subtitle_file_replaces))
}

/**
 * Subtitles from what is said: how to get the model, the button that listens,
 * how far it is, what it found, or what went wrong.
 */
@Composable
private fun SpeechCard(subtitles: SubtitleEditing) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val speech = subtitles.speech
    val working = speech as? SpeechStatus.Working
    val failed = (speech as? SpeechStatus.Failed)?.problem
    val wordCount = subtitles.shownWords.values.sumOf { it.size }
    val done = subtitles.fileUri == null && subtitles.words.values.any { it.isNotEmpty() } && working == null
    val tone = when {
        failed != null -> ToneColors(scheme.errorContainer, scheme.onErrorContainer)
        done -> ToneColors(scheme.primary, scheme.onPrimary)
        else -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
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
                to = MaterialShapes.Sunny,
                morphed = working != null || done,
                color = tone.container,
                modifier = Modifier.size(52.dp),
            ) {
                if (working != null) {
                    LoadingIndicator(color = tone.content, modifier = Modifier.size(36.dp))
                } else {
                    Icon(
                        imageVector = when {
                            failed != null -> Icons.Rounded.ErrorOutline
                            done -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.AutoAwesome
                        },
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.speech_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(
                    text = when {
                        working != null -> stepText(working)
                        failed != null -> stringResource(
                            when (failed) {
                                SpeechProblem.NO_MODEL -> R.string.speech_problem_model
                                SpeechProblem.NO_MEMORY -> R.string.speech_problem_memory
                                SpeechProblem.FAILED -> R.string.speech_problem_failed
                            },
                        )
                        !subtitles.modelReady -> stringResource(R.string.speech_needs_model)
                        done -> pluralStringResource(R.plurals.speech_found_words, wordCount, wordCount)
                        else -> stringResource(R.string.speech_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

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
        }

        Spacer(modifier = Modifier.height(14.dp))
        when {
            working != null -> Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    subtitles.onStop()
                },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.speech_stop), style = MaterialTheme.typography.titleSmallEmphasized)
            }
            !subtitles.modelReady -> Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    subtitles.onOpenSmartFeatures()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.speech_open_settings), style = MaterialTheme.typography.titleSmallEmphasized)
            }
            done -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subtitles.unheardFiles > 0) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            subtitles.onGenerate(false)
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            pluralStringResource(R.plurals.speech_listen_new, subtitles.unheardFiles, subtitles.unheardFiles),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                    }
                }
                // Ordinary texts: each line moved, resized and edited on its own.
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        subtitles.onToTexts()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Rounded.Title, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.speech_to_texts), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    stringResource(R.string.speech_to_texts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            subtitles.onGenerate(true)
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.speech_again), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            subtitles.onClearSpoken()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.speech_remove), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            else -> Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    subtitles.onDismissProblem()
                    subtitles.onGenerate(false)
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(if (failed != null) R.string.speech_try_again else R.string.speech_generate),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
        }
        if (working == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, start = 4.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.speech_private), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun stepText(working: SpeechStatus.Working): String = when (val step = working.step) {
    is SpeechTranscriber.Step.Extracting -> stringResource(R.string.speech_step_sound)
    SpeechTranscriber.Step.Loading -> stringResource(R.string.speech_step_loading)
    is SpeechTranscriber.Step.Listening ->
        if (step.parts > 1) {
            stringResource(R.string.speech_step_listening_part, step.part, step.parts)
        } else {
            stringResource(R.string.speech_step_listening)
        }
}

// -- How they look ---------------------------------------------------------------------

/** Where the text goes, as a quick choice; null when it was dragged elsewhere. */
private enum class Placement(val label: Int, val y: Float) {
    TOP(R.string.subtitle_place_top, 0.2f),
    MIDDLE(R.string.subtitle_place_middle, 0.6f),
    BOTTOM(R.string.subtitle_place_bottom, 0.9f),
}

@Composable
private fun StylePage(subtitles: SubtitleEditing) {
    val style = subtitles.style
    val onStyle = subtitles.onStyle
    if (!subtitles.styled) {
        PanelHint(
            stringResource(if (subtitles.fileUri != null) R.string.subtitle_style_own_look else R.string.subtitle_style_nothing),
        )
        if (subtitles.fileUri != null) return
    }
    // A ready-made look to start from.
    OptionTileRow(
        options = SubtitlePreset.entries.map { preset ->
            TileOption(
                value = preset,
                label = stringResource(presetLabel(preset)),
                preview = { _ -> PresetSample(preset.apply(style)) },
            )
        },
        selected = SubtitlePreset.of(style),
        onSelect = { preset -> onStyle(null) { preset.apply(it) } },
    )
    FontPicker(selected = style.font, bold = style.bold, onSelect = { font -> onStyle(null) { it.copy(font = font) } })
    PanelCard {
        ValueSlider(
            label = stringResource(R.string.subtitle_size),
            valueText = String.format(Locale.ROOT, "%.1f %%", style.size * 100f),
            value = style.size,
            onValueChange = { value -> onStyle("size") { it.copy(size = value) } },
            valueRange = SubtitleStyle.MIN_SIZE..SubtitleStyle.MAX_SIZE,
            icon = Icons.Rounded.TextFields,
            defaultValue = SubtitleStyle.DEFAULT_SIZE,
            input = ValueInput(step = 0.005f, unit = "%", scale = 100f, decimals = 1),
        )
        Text(stringResource(R.string.subtitle_place), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
        val placement = Placement.entries.firstOrNull { kotlin.math.abs(style.x - 0.5f) < 0.01f && kotlin.math.abs(style.y - it.y) < 0.01f }
        ConnectedChoiceGroup(
            options = Placement.entries,
            selected = placement,
            label = { stringResource(it?.label ?: R.string.subtitle_place_bottom) },
            onSelect = { chosen -> if (chosen != null) onStyle(null) { it.copy(x = 0.5f, y = chosen.y) } },
        )
        PanelHint(stringResource(R.string.subtitle_place_hint))
        Text(stringResource(R.string.subtitle_lines), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
        ConnectedChoiceGroup(
            options = listOf(1, 2),
            selected = style.maxLines,
            label = { pluralStringResource(R.plurals.subtitle_lines_count, it, it) },
            onSelect = { lines -> onStyle(null) { it.copy(maxLines = lines) } },
        )
    }
    PanelCard {
        Text(stringResource(R.string.subtitle_look), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
        ConnectedChoiceGroup(
            options = SubtitleLook.entries,
            selected = style.look,
            label = {
                stringResource(
                    when (it) {
                        SubtitleLook.OUTLINE -> R.string.subtitle_look_outline
                        SubtitleLook.BOX -> R.string.subtitle_look_box
                        SubtitleLook.SHADOW -> R.string.subtitle_look_shadow
                    },
                )
            },
            onSelect = { look -> onStyle(null) { it.copy(look = look) } },
        )
        Text(stringResource(R.string.subtitle_text_color), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
        ColorSwatchRow(colors = SUBTITLE_COLORS, selected = style.textColor, onSelect = { color -> onStyle(null) { it.copy(textColor = color) } })
    }
    SwitchCard(
        icon = Icons.Rounded.FormatBold,
        title = stringResource(R.string.subtitle_bold),
        checked = style.bold,
        onCheckedChange = { bold -> onStyle(null) { it.copy(bold = bold) } },
    )
    SwitchCard(
        icon = Icons.Rounded.TextFields,
        title = stringResource(R.string.subtitle_uppercase),
        subtitle = stringResource(R.string.subtitle_uppercase_hint),
        checked = style.uppercase,
        onCheckedChange = { upper -> onStyle(null) { it.copy(uppercase = upper) } },
    )
    // The word being said: only speech knows when each word is said.
    PanelCard {
        Text(stringResource(R.string.subtitle_highlight), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
        ConnectedChoiceGroup(
            options = WordHighlight.entries,
            selected = style.highlight,
            enabled = subtitles.fileUri == null,
            label = {
                stringResource(
                    when (it) {
                        WordHighlight.NONE -> R.string.subtitle_highlight_none
                        WordHighlight.WORD -> R.string.subtitle_highlight_word
                        WordHighlight.SPOKEN -> R.string.subtitle_highlight_spoken
                    },
                )
            },
            onSelect = { highlight -> onStyle(null) { it.copy(highlight = highlight) } },
        )
        AnimatedVisibility(
            visible = style.highlight != WordHighlight.NONE && subtitles.fileUri == null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.subtitle_highlight_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorSwatchRow(colors = SUBTITLE_COLORS, selected = style.highlightColor, onSelect = { color -> onStyle(null) { it.copy(highlightColor = color) } })
            }
        }
        PanelHint(stringResource(if (subtitles.fileUri == null) R.string.subtitle_highlight_hint else R.string.subtitle_highlight_file))
    }
}

/** The typefaces as tiles, each "Aa" drawn with the file the export uses. */
@Composable
internal fun FontPicker(selected: TextFont, bold: Boolean, onSelect: (TextFont) -> Unit) {
    OptionTileRow(
        options = TextFont.entries.map { font ->
            TileOption(
                value = font,
                label = stringResource(fontLabel(font)),
                preview = { color ->
                    Text(
                        text = "Aa",
                        color = color,
                        fontSize = 22.sp,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = rememberExportFont(font, bold),
                    )
                },
            )
        },
        selected = selected,
        onSelect = onSelect,
    )
}

private fun fontLabel(font: TextFont): Int = when (font) {
    TextFont.SANS -> R.string.font_sans
    TextFont.SERIF -> R.string.font_serif
    TextFont.MONO -> R.string.font_mono
    TextFont.TYPEWRITER -> R.string.font_typewriter
    TextFont.HANDWRITING -> R.string.font_handwriting
    TextFont.CASUAL -> R.string.font_casual
    TextFont.SMALL_CAPS -> R.string.font_small_caps
}

private fun presetLabel(preset: SubtitlePreset): Int = when (preset) {
    SubtitlePreset.CLASSIC -> R.string.subtitle_preset_classic
    SubtitlePreset.POP -> R.string.subtitle_preset_pop
    SubtitlePreset.BOXED -> R.string.subtitle_preset_boxed
    SubtitlePreset.KARAOKE -> R.string.subtitle_preset_karaoke
    SubtitlePreset.MINIMAL -> R.string.subtitle_preset_minimal
}

/** "Aa" in a preset's look, inside its tile: outline, box or shadow, the highlight on the second letter. */
@Composable
private fun PresetSample(style: SubtitleStyle) {
    val text = if (style.uppercase) "AA" else "Aa"
    val base = TextStyle(
        fontSize = 20.sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF5B6B7A))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        if (style.look == SubtitleLook.BOX) {
            Box(modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(Color(SubtitleStyle.BOX_COLOR)).padding(horizontal = 3.dp)) {
                SampleLetters(text, base, style)
            }
        } else {
            // The outline under the letters, laid out as one text with them so that it fits
            // them exactly, always in black (as libass strokes it), the letters on top.
            if (style.look == SubtitleLook.OUTLINE) {
                Text(text, style = base.copy(color = Color(SubtitleStyle.OUTLINE_COLOR), drawStyle = Stroke(width = 6f, join = StrokeJoin.Round)))
            }
            SampleLetters(
                text,
                if (style.look == SubtitleLook.SHADOW) base.copy(shadow = Shadow(Color(SubtitleStyle.SHADOW_COLOR), androidx.compose.ui.geometry.Offset(2f, 2f))) else base,
                style,
            )
        }
    }
}

/** The sample letters, the second lit up when the style lights words (both when it lights what was said). */
@Composable
private fun SampleLetters(text: String, base: TextStyle, style: SubtitleStyle) {
    val lit = style.highlight != WordHighlight.NONE
    val letters = buildAnnotatedString {
        pushStyle(SpanStyle(color = Color(if (lit && style.highlight == WordHighlight.SPOKEN) style.highlightColor else style.textColor)))
        append(text.substring(0, 1))
        pop()
        pushStyle(SpanStyle(color = Color(if (lit) style.highlightColor else style.textColor)))
        append(text.substring(1))
        pop()
    }
    Text(letters, style = base)
}

// -- The lines -------------------------------------------------------------------------

/** A line of the transcript: the words of one block, on the clock of the file they were heard in. */
private class TranscriptLine(val sourceId: String, val startMs: Long, val endMs: Long, val text: String)

@Composable
private fun LinesPage(subtitles: SubtitleEditing) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    if (subtitles.fileUri != null) {
        PanelHint(pluralStringResource(R.plurals.subtitle_file_lines, subtitles.fileCues.size, subtitles.fileCues.size))
        return
    }
    // Made in the background (hours of speech make thousands); the ones made before stay meanwhile.
    val made by produceState<List<TranscriptLine>?>(null, subtitles.words, subtitles.style) {
        value = withContext(Dispatchers.Default) {
            subtitles.words.flatMap { (sourceId, words) ->
                // One line per block, as the subtitles show them (the frame's shape does not matter here).
                SubtitleLayout.ofWords(words, subtitles.style.copy(maxLines = 2), 16f / 9f).map { block ->
                    TranscriptLine(sourceId, block.startMs, block.endMs, block.words.joinToString(" ") { it.text })
                }
            }
        }
    }
    val lines = made
    if (lines == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) { LoadingIndicator() }
        return
    }
    if (lines.isEmpty()) {
        PanelHint(stringResource(R.string.subtitle_lines_empty))
        return
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = subtitles.srt() ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanelHint(stringResource(R.string.subtitle_lines_hint), modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                saver.launch("subtitles.srt")
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.subtitle_save_srt))
        }
    }
    var shown by remember { mutableIntStateOf(LINES_PAGE) }
    var editing by remember { mutableStateOf<TranscriptLine?>(null) }
    val showFile = subtitles.words.keys.size > 1
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
    ) {
        lines.take(shown).forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        editing = line
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(lineTime(line.startMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (showFile) {
                        Text(subtitles.sourceNames[line.sourceId] ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.subtitle_edit_line), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (lines.size > shown) {
        TextButton(onClick = { shown += LINES_PAGE }, modifier = Modifier.fillMaxWidth()) {
            Text(pluralStringResource(R.plurals.subtitle_more_lines, lines.size - shown, lines.size - shown))
        }
    }
    editing?.let { line ->
        EditLineDialog(
            line = line,
            onSave = { text ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                subtitles.onEditLine(line.sourceId, line.startMs, line.endMs, text)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EditLineDialog(line: TranscriptLine, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(line) { mutableStateOf(line.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.subtitle_edit_title, lineTime(line.startMs))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace('\n', ' ') },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.subtitle_edit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.subtitle_edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.subtitle_edit_cancel)) }
        },
    )
}

/** "m:ss" (or "h:mm:ss"). */
private fun lineTime(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}

private const val LINES_PAGE = 40
