@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.translate

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.translate.Languages
import com.cuscus.ffmpeggui.settings.SettingsGroupTitle
import com.cuscus.ffmpeggui.settings.openLink
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import kotlinx.coroutines.launch

/*
 * The translation models in the smart features' settings: the language pairs
 * on the phone (each checked again or removed with a tap), and a sheet to add
 * more: a list of ready pairs to search, or any Opus-MT model by its name on
 * Hugging Face, each with its four files to download and one button to import
 * them all at once.
 */

/** The translation models' state and actions, for the settings. */
class TranslationFeatureUi(
    val models: List<InstalledModel>,
    val importing: TranslationModelStore.Importing,
    /** A model is in memory now. */
    val loaded: Boolean,
    val onImport: (List<Uri>, String?) -> Unit,
    val onStopImport: () -> Unit,
    val onDismissImport: () -> Unit,
    val onDelete: (String) -> Unit,
    /** Hashes a model's files again; true when they are as imported. */
    val onVerify: suspend (String) -> Boolean,
    val onUnload: () -> Unit,
)

@Composable
fun TranslationSettingsSection(ui: TranslationFeatureUi, showHeading: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showHeading) Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)) {
            Text(stringResource(R.string.translate_section), style = SettingsGroupTitle, color = scheme.onSurface)
            Text(stringResource(R.string.translate_intro), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        ImportStatus(ui)
        if (ui.models.isEmpty()) {
            PanelHint(stringResource(R.string.translate_none_installed), modifier = Modifier.padding(horizontal = 4.dp))
        }
        ui.models.forEach { model -> InstalledCard(model, ui) }
        Button(
            onClick = { adding = true },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.translate_add_pair), style = MaterialTheme.typography.titleMediumEmphasized)
        }
        if (ui.loaded) {
            TextButton(onClick = ui.onUnload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.translate_unload)) }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { openLink(context, OpusMt.PROJECT_URL) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                stringResource(R.string.translate_about, OpusMt.NAME, OpusMt.MAKER, OpusMt.LICENCE),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
    if (adding) AddModelSheet(ui, onDismiss = { adding = false })
}

/** A model on the phone: from and to, its size, check and remove. */
@Composable
private fun InstalledCard(model: InstalledModel, ui: TranslationFeatureUi) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var confirm by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf<Boolean?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie7Sided,
            to = MaterialShapes.Cookie7Sided,
            morphed = false,
            color = if (checked == false) scheme.errorContainer else scheme.primaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(if (checked == false) Icons.Rounded.ErrorOutline else Icons.Rounded.Translate, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        val many = stringResource(R.string.translate_many_languages)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (model.sources.isEmpty()) stringResource(R.string.translate_many_languages) else model.sources.joinToString(", ") { languageName(it, many) },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp))
                Text(
                    if (model.targets.size > 3) pluralStringResource(R.plurals.translate_languages, model.targets.size, model.targets.size) else model.targets.joinToString(", ") { languageName(it, many) },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(
                model.repo + "  -  " + Formatter.formatShortFileSize(context, model.bytes) +
                    when (checked) {
                        true -> "  -  " + stringResource(R.string.translate_checked_ok)
                        false -> "  -  " + stringResource(R.string.translate_checked_bad)
                        null -> ""
                    },
                style = MaterialTheme.typography.labelSmall,
                color = if (checked == false) scheme.error else scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = {
                scope.launch {
                    checked = ui.onVerify(model.id)
                    haptic.performHapticFeedback(if (checked == true) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                }
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.Verified, contentDescription = stringResource(R.string.translate_check))
        }
        FilledTonalIconButton(onClick = { confirm = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.translate_delete))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.translate_delete_title)) },
            text = { Text(stringResource(R.string.translate_delete_text, model.repo, Formatter.formatShortFileSize(context, model.bytes))) },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    ui.onDelete(model.id)
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.translate_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** An import as it goes, or what came of it. */
@Composable
private fun ImportStatus(ui: TranslationFeatureUi) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val importing = ui.importing
    AnimatedVisibility(
        visible = importing !is TranslationModelStore.Importing.Idle,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        val failed = importing as? TranslationModelStore.Importing.Failed
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (failed != null) scheme.errorContainer else scheme.secondaryContainer)
                .padding(16.dp),
        ) {
            val color = if (failed != null) scheme.onErrorContainer else scheme.onSecondaryContainer
            when (importing) {
                is TranslationModelStore.Importing.Working -> {
                    Text(
                        stringResource(if (importing.checking) R.string.translate_importing_check else R.string.translate_importing, importing.file),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = color,
                    )
                    LinearWavyProgressIndicator(progress = { importing.fraction }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = ui.onStopImport) { Text(stringResource(R.string.translate_stop)) }
                }
                is TranslationModelStore.Importing.Failed -> {
                    Text(
                        when (importing.problem) {
                            TranslationModelStore.Problem.MISSING_FILES -> stringResource(R.string.translate_import_missing, importing.missing.joinToString(", ") { it.fileName })
                            TranslationModelStore.Problem.NOT_MARIAN -> stringResource(R.string.translate_import_not_marian)
                            TranslationModelStore.Problem.NO_LANGUAGES -> stringResource(R.string.translate_import_no_languages)
                            TranslationModelStore.Problem.BROKEN -> stringResource(R.string.translate_import_broken)
                            TranslationModelStore.Problem.NO_SPACE -> stringResource(R.string.translate_import_no_space)
                            TranslationModelStore.Problem.UNREADABLE, TranslationModelStore.Problem.HASH_MISMATCH -> stringResource(R.string.translate_import_unreadable)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                    TextButton(onClick = ui.onDismissImport) { Text(stringResource(R.string.translate_ok)) }
                }
                is TranslationModelStore.Importing.Done -> {
                    Text(stringResource(R.string.translate_import_done, importing.model.repo), style = MaterialTheme.typography.titleSmallEmphasized, color = color)
                    TextButton(onClick = ui.onDismissImport) { Text(stringResource(R.string.translate_ok)) }
                }
                TranslationModelStore.Importing.Idle -> Unit
            }
        }
    }
}

/**
 * Adding a pair: search the ready ones, or name any Opus-MT model; each one
 * opens into its three steps (its five files, then one import of them all).
 */
@Composable
private fun AddModelSheet(ui: TranslationFeatureUi, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val installed = ui.models.map { it.repo.substringAfterLast('/') }.toSet()
    val names = OpusMt.SUGGESTED.associateWith { repo ->
        val (from, to) = Languages.pairOf(repo) ?: ("" to "")
        languageLabel(from) + " " + languageLabel(to)
    }
    val shown = OpusMt.SUGGESTED.filter { it.substringAfterLast('/') !in installed }
        .filter { query.isBlank() || names.getValue(it).contains(query.trim(), ignoreCase = true) || it.contains(query.trim(), ignoreCase = true) }
    val custom = query.trim().takeIf { OpusMt.isRepo(it) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp).padding(horizontal = 16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    MorphingContainer(from = MaterialShapes.Sunny, to = MaterialShapes.Sunny, morphed = false, color = scheme.primary, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Translate, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.translate_add_pair), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.translate_add_hint), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            item { ImportStatus(ui) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.translate_search)) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (custom != null) {
                item(key = "custom") {
                    PairRow(repo = custom, expanded = open == custom, onToggle = { open = if (open == custom) null else custom }, ui = ui, custom = true)
                }
            }
            items(shown, key = { it }) { repo ->
                PairRow(repo = repo, expanded = open == repo, onToggle = { open = if (open == repo) null else repo }, ui = ui)
            }
            item {
                PanelHint(stringResource(R.string.translate_other_hint), modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
            }
        }
    }
}

/** One pair: its two languages; opened, its files to download and the import. */
@Composable
private fun PairRow(repo: String, expanded: Boolean, onToggle: () -> Unit, ui: TranslationFeatureUi, custom: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val context = LocalContext.current
    val (from, to) = Languages.pairOf(repo) ?: ("" to "")
    val container by animateColorAsState(if (expanded) scheme.secondaryContainer else scheme.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "pairRow")
    val corner by animateDpAsState(if (expanded) 32.dp else 20.dp, motion.defaultSpatialSpec(), label = "pairCorner")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ui.onImport(uris, repo)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner.coerceAtLeast(0.dp)))
            .background(container),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(languageLabel(from), style = MaterialTheme.typography.titleSmallEmphasized)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp).size(16.dp))
            Text(languageLabel(to), style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f))
            if (custom) Text(repo, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(stringResource(R.string.translate_step_download), style = MaterialTheme.typography.labelLargeEmphasized)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OpusMt.downloads(repo).forEach { d ->
                        FilledTonalButton(onClick = { openLink(context, d.url) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(d.kind.fileName, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                PanelHint(stringResource(R.string.translate_json_hint))
                TextButton(onClick = { openLink(context, OpusMt.page(repo)) }) {
                    Text(stringResource(R.string.translate_open_page))
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(stringResource(R.string.translate_step_import), style = MaterialTheme.typography.labelLargeEmphasized)
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = ui.importing !is TranslationModelStore.Importing.Working,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.translate_import_files))
                }
            }
        }
    }
}
