@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.dub

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.dub.PiperListed
import com.cuscus.ffmpeggui.core.dub.VoiceLicence
import com.cuscus.ffmpeggui.settings.ConfirmDialog
import com.cuscus.ffmpeggui.settings.SettingsGroupTitle
import com.cuscus.ffmpeggui.settings.SettingsRowTitle
import com.cuscus.ffmpeggui.settings.groupShape
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.springPress
import java.util.Locale

/*
 * The Piper voices in the smart features' settings: one card with the voices
 * on the phone as a group of rows (each opens a sheet with all it says of
 * itself, its licence and its removal), an import as it goes, and the way to
 * add more (the list, see PiperAddSheet).
 */

/** The Piper voices' state and actions, for the settings. */
class PiperFeatureUi(
    val voices: List<PiperVoiceStore.Voice>,
    val importing: PiperVoiceStore.Importing,
    /** False while the video is being dubbed. */
    val idle: Boolean,
    val onImport: (List<Uri>) -> Unit,
    val onStopImport: () -> Unit,
    val onDismissImport: () -> Unit,
    val onDelete: (String) -> Unit,
    /** eSpeak NG's data is on the phone: Piper's own files can be imported alone. */
    val hasEspeak: Boolean = false,
)

@Composable
fun PiperSettingsSection(ui: PiperFeatureUi) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val any = ui.voices.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)) {
            Text(stringResource(R.string.piper_section), style = SettingsGroupTitle, color = scheme.onSurface)
            Text(stringResource(R.string.piper_intro), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
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
                    to = MaterialShapes.Cookie12Sided,
                    morphed = any,
                    color = if (any) scheme.tertiary else scheme.tertiaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = if (any) scheme.onTertiary else scheme.onTertiaryContainer, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.piper_card_title), style = SettingsRowTitle, color = scheme.onSurface)
                    Text(
                        if (any) {
                            pluralStringResource(R.plurals.piper_count, ui.voices.size, ui.voices.size, Formatter.formatShortFileSize(context, ui.voices.sumOf { it.bytes }))
                        } else {
                            stringResource(R.string.piper_none)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (any) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ui.voices.forEachIndexed { index, voice ->
                        PiperVoiceRow(voice, groupShape(index, ui.voices.size)) { opened = voice.id }
                    }
                }
            }
            AnimatedVisibility(
                visible = ui.importing !is PiperVoiceStore.Importing.Idle,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Box(modifier = Modifier.padding(top = 14.dp)) { PiperImportStatus(ui) }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    adding = true
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.piper_add), style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
        PanelHint(stringResource(R.string.piper_licence_hint), modifier = Modifier.padding(horizontal = 4.dp))
    }
    if (adding) PiperAddSheet(ui, onDismiss = { adding = false })
    ui.voices.firstOrNull { it.id == opened }?.let { voice ->
        PiperVoiceSheet(voice, ui, onDismiss = { opened = null })
    }
}

/** The shapes a voice's badge takes, one per voice (by its name), so that each is told apart at a glance. */
private val VOICE_SHAPES = listOf(
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Pentagon,
    MaterialShapes.Flower,
    MaterialShapes.Gem,
    MaterialShapes.Arch,
    MaterialShapes.SoftBurst,
)

/** A voice's badge: its first letter in its own shape, rounding to a circle while pressed. */
@Composable
private fun VoiceBadge(voice: PiperVoiceStore.Voice, pressed: Boolean, size: Int) {
    val scheme = MaterialTheme.colorScheme
    MorphingContainer(
        from = VOICE_SHAPES[Math.floorMod(voice.id.hashCode(), VOICE_SHAPES.size)],
        to = MaterialShapes.Circle,
        morphed = pressed,
        color = scheme.tertiaryContainer,
        modifier = Modifier.size(size.dp),
    ) {
        Text(
            voice.name.take(1).uppercase(Locale.getDefault()),
            style = if (size > 60) MaterialTheme.typography.headlineMediumEmphasized else MaterialTheme.typography.titleMediumEmphasized,
            color = scheme.onTertiaryContainer,
        )
    }
}

/** A small tag: a language, a quality, a licence. */
@Composable
private fun Tag(text: String, container: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** The tags of [voice]: its language, how good it sounds, its licence when it has conditions. */
@Composable
private fun VoiceTags(voice: PiperVoiceStore.Voice) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Tag(voiceLanguageName(voice.config.languageTag), scheme.secondaryContainer, scheme.onSecondaryContainer)
        voice.quality?.let { q ->
            Tag(stringResource(qualityName(PiperListed.QUALITIES.indexOf(q))), scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        }
        licenceTag(voice.licence)?.let { tag ->
            if (voice.licence == VoiceLicence.RESEARCH_ONLY) {
                Tag(tag, scheme.errorContainer, scheme.onErrorContainer)
            } else {
                Tag(tag, scheme.tertiaryContainer, scheme.onTertiaryContainer)
            }
        }
    }
}

/** A voice on the phone, as a row of the group: its badge, its name and tags. Tapped, its sheet. */
@Composable
private fun PiperVoiceRow(voice: PiperVoiceStore.Voice, shape: Shape, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpen()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        VoiceBadge(voice, pressed, 44)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(voice.name, style = SettingsRowTitle, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VoiceTags(voice)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

/** All a voice says of itself: what it is, its licence, its card and checksum; and its removal. */
@Composable
private fun PiperVoiceSheet(voice: PiperVoiceStore.Voice, ui: PiperFeatureUi, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirm by remember { mutableStateOf(false) }
    val config = voice.config
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceBadge(voice, pressed = false, size = 72)
                Spacer(modifier = Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(voice.name, style = MaterialTheme.typography.headlineSmallEmphasized, color = scheme.onSurface)
                    VoiceTags(voice)
                }
            }
            // The facts, as a group of short rows.
            val facts = listOfNotNull(
                stringResource(R.string.piper_fact_size) to Formatter.formatShortFileSize(context, voice.bytes),
                stringResource(R.string.piper_fact_rate) to stringResource(R.string.piper_fact_rate_value, config.sampleRate / 1000f),
                if (config.speakers.size > 1) stringResource(R.string.piper_fact_speakers) to config.speakers.size.toString() else null,
                config.dataset?.let { stringResource(R.string.piper_fact_dataset) to it },
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                facts.forEachIndexed { index, (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(groupShape(index, facts.size))
                            .background(scheme.surfaceContainerHigh)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Its licence: free, or what it asks.
            val free = !voice.licence.needsCare
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        when (voice.licence) {
                            VoiceLicence.COMMERCIAL -> scheme.primaryContainer
                            VoiceLicence.RESEARCH_ONLY -> scheme.errorContainer
                            else -> scheme.tertiaryContainer
                        },
                    )
                    .padding(14.dp),
            ) {
                val content = when (voice.licence) {
                    VoiceLicence.COMMERCIAL -> scheme.onPrimaryContainer
                    VoiceLicence.RESEARCH_ONLY -> scheme.onErrorContainer
                    else -> scheme.onTertiaryContainer
                }
                Icon(if (free) Icons.Rounded.Verified else Icons.Rounded.ErrorOutline, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(licenceTag(voice.licence) ?: stringResource(R.string.dub_licence_free_tag), style = MaterialTheme.typography.titleSmallEmphasized, color = content)
                    Text(
                        stringResource(if (free) R.string.piper_licence_free_text else R.string.piper_licence_care_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = content,
                    )
                }
            }
            Text(stringResource(R.string.piper_card), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurfaceVariant)
            Text(
                voice.card ?: stringResource(R.string.piper_no_card),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainerHigh)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("SHA-256", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(voice.sha256, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = scheme.onSurface)
            }
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    confirm = true
                },
                enabled = ui.idle,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.piper_delete))
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = stringResource(R.string.piper_delete_title, voice.name),
            text = stringResource(R.string.piper_delete_text, Formatter.formatShortFileSize(context, voice.bytes)),
            confirm = stringResource(R.string.piper_delete),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirm = false
                onDismiss()
                ui.onDelete(voice.id)
            },
            onDismiss = { confirm = false },
        )
    }
}

/** "it_IT" as "Italiano (Italia)", in the phone's language. */
private fun voiceLanguageName(tag: String): String =
    Locale.forLanguageTag(tag.replace('_', '-')).getDisplayName(Locale.getDefault())
        .ifBlank { tag }
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** An import as it goes, or what came of it: a tinted strip with a shape that turns while it works. */
@Composable
internal fun PiperImportStatus(ui: PiperFeatureUi) {
    val scheme = MaterialTheme.colorScheme
    val importing = ui.importing
    if (importing is PiperVoiceStore.Importing.Idle) return
    val failed = importing is PiperVoiceStore.Importing.Failed
    val done = importing is PiperVoiceStore.Importing.Done
    val container = when {
        failed -> scheme.errorContainer
        done -> scheme.primaryContainer
        else -> scheme.secondaryContainer
    }
    val color = when {
        failed -> scheme.onErrorContainer
        done -> scheme.onPrimaryContainer
        else -> scheme.onSecondaryContainer
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                when (importing) {
                    is PiperVoiceStore.Importing.Working -> LoadingIndicator(color = color, modifier = Modifier.size(36.dp))
                    is PiperVoiceStore.Importing.Failed -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = color)
                    else -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = color)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                when (importing) {
                    is PiperVoiceStore.Importing.Working ->
                        importing.file?.let { stringResource(R.string.piper_importing_file, it) } ?: stringResource(R.string.piper_importing)
                    is PiperVoiceStore.Importing.Failed -> stringResource(
                        when (importing.problem) {
                            PiperVoiceStore.Problem.NOT_A_VOICE -> R.string.piper_problem_not_voice
                            PiperVoiceStore.Problem.UNSUPPORTED -> R.string.piper_problem_unsupported
                            PiperVoiceStore.Problem.NO_ESPEAK -> R.string.piper_problem_no_espeak
                            PiperVoiceStore.Problem.UNREADABLE -> R.string.piper_problem_unreadable
                            PiperVoiceStore.Problem.NO_SPACE -> R.string.piper_problem_no_space
                        },
                    )
                    is PiperVoiceStore.Importing.Done -> stringResource(R.string.piper_import_done, importing.name)
                    PiperVoiceStore.Importing.Idle -> ""
                },
                style = if (failed) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmallEmphasized,
                color = color,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (importing is PiperVoiceStore.Importing.Working) {
            val fraction = importing.fraction
            if (fraction != null) {
                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        TextButton(
            onClick = if (importing is PiperVoiceStore.Importing.Working) ui.onStopImport else ui.onDismissImport,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(if (importing is PiperVoiceStore.Importing.Working) R.string.translate_stop else R.string.translate_ok), color = color)
        }
    }
}
