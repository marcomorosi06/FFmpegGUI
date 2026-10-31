@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.dub

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.dub.PiperCatalog
import com.cuscus.ffmpeggui.core.dub.PiperListed
import com.cuscus.ffmpeggui.settings.openLink
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import java.util.Locale

/**
 * Adding a Piper voice from a list: a language (the phone's first), its
 * voices the best first, each opening into its two steps (download it in the
 * browser, then import it); a search across them all; and, at the end, any
 * other voice (one trained at home).
 */
@Composable
internal fun PiperAddSheet(ui: PiperFeatureUi, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val languages = remember {
        val phone = Locale.getDefault().language
        PiperCatalog.LANGUAGES.sortedWith(compareBy({ it != phone }, { languageLabel(it) }))
    }
    var language by rememberSaveable { mutableStateOf(languages.first()) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val installed = ui.voices.map { it.id }.toSet()
    val trimmed = query.trim()
    val shown = if (trimmed.isEmpty()) {
        PiperCatalog.of(language)
    } else {
        PiperCatalog.ALL.filter {
            it.key.contains(trimmed, ignoreCase = true) || it.name.contains(trimmed, ignoreCase = true) ||
                languageLabel(it.languageCode).contains(trimmed, ignoreCase = true)
        }
    }
    val other = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ui.onImport(uris)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                    MorphingContainer(from = MaterialShapes.Sunny, to = MaterialShapes.Sunny, morphed = false, color = scheme.tertiary, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.piper_add), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.piper_add_hint), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            item { PanelHint(stringResource(R.string.piper_licence_list_hint), modifier = Modifier.padding(horizontal = 20.dp)) }
            item { Box(modifier = Modifier.padding(horizontal = 16.dp)) { PiperImportStatus(ui) } }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.piper_search)) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (trimmed.isEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    ) {
                        items(languages, key = { it }) { code ->
                            FilterChip(
                                selected = code == language,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    language = code
                                    open = null
                                },
                                label = { Text(languageLabel(code)) },
                            )
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item { PanelHint(stringResource(R.string.piper_search_none), modifier = Modifier.padding(horizontal = 20.dp)) }
            }
            items(shown, key = { it.key }) { listed ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ListedRow(
                        listed = listed,
                        installed = listed.key in installed,
                        expanded = open == listed.key,
                        withLanguage = trimmed.isNotEmpty(),
                        ui = ui,
                        onToggle = { open = if (open == listed.key) null else listed.key },
                    )
                }
            }
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                ) {
                    Text(stringResource(R.string.piper_other), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurfaceVariant)
                    PanelHint(stringResource(R.string.piper_how))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { other.launch(arrayOf("*/*")) },
                            enabled = ui.importing !is PiperVoiceStore.Importing.Working,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.piper_import_other))
                        }
                        TextButton(onClick = { openLink(context, PiperVoiceStore.ARCHIVES_PAGE_URL) }) {
                            Text(stringResource(R.string.piper_get_archives))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    PanelHint(stringResource(R.string.piper_licence_hint))
                }
            }
        }
    }
}

/** One listed voice: its name and quality; opened, its download and its import. */
@Composable
private fun ListedRow(
    listed: PiperListed,
    installed: Boolean,
    expanded: Boolean,
    withLanguage: Boolean,
    ui: PiperFeatureUi,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val context = LocalContext.current
    val container by animateColorAsState(if (expanded) scheme.secondaryContainer else scheme.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "piperRow")
    val corner by animateDpAsState(if (expanded) 32.dp else 20.dp, motion.defaultSpatialSpec(), label = "piperCorner")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ui.onImport(uris)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner.coerceAtLeast(0.dp)))
            .background(container),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            QualityBadge(listed.rank)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (withLanguage) "${listed.name}  -  ${languageLabel(listed.languageCode)}" else listed.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        regionLabel(listed.languageTag),
                        stringResource(qualityName(listed.rank)),
                        if (listed.speakers > 1) stringResource(R.string.piper_speakers, listed.speakers) else null,
                        listed.bytes?.let { Formatter.formatShortFileSize(context, it) },
                    ).joinToString("  -  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (installed) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.piper_installed), tint = scheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(stringResource(R.string.piper_step_download), style = MaterialTheme.typography.labelLargeEmphasized)
                FilledTonalButton(onClick = { openLink(context, listed.archiveUrl) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(listed.archiveName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PanelHint(stringResource(R.string.piper_archive_hint))
                if (listed.files.isNotEmpty() && ui.hasEspeak) {
                    Text(stringResource(R.string.piper_or_files), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listed.files.forEach { (name, url) ->
                            OutlinedButton(onClick = { openLink(context, url) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (name == "MODEL_CARD") name else name.substringAfter(listed.key), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                TextButton(onClick = { openLink(context, listed.pageUrl) }) {
                    Text(stringResource(R.string.piper_open_card))
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(stringResource(R.string.piper_step_import), style = MaterialTheme.typography.labelLargeEmphasized)
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = ui.importing !is PiperVoiceStore.Importing.Working,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(if (installed) R.string.piper_import_again else R.string.piper_import_downloaded))
                }
            }
        }
    }
}

/** How good a voice sounds, as one to four filled dots in a pill. */
@Composable
private fun QualityBadge(rank: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (rank >= 3) scheme.tertiaryContainer else scheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        for (i in 0..3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i <= rank) (if (rank >= 3) scheme.onTertiaryContainer else scheme.primary) else scheme.outlineVariant),
            )
        }
    }
}

internal fun qualityName(rank: Int): Int = when (rank) {
    3 -> R.string.piper_quality_high
    2 -> R.string.piper_quality_medium
    1 -> R.string.piper_quality_low
    else -> R.string.piper_quality_x_low
}

/** The name of the language [code] in the phone's language: "Italiano". */
private fun languageLabel(code: String): String {
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
    return if (name.isBlank()) code else name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** The country of [tag] ("en_GB": "Regno Unito"), or null. */
private fun regionLabel(tag: String): String? =
    Locale.forLanguageTag(tag.replace('_', '-')).getDisplayCountry(Locale.getDefault()).takeIf { it.isNotBlank() }
