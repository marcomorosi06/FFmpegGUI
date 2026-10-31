@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.command

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.command.AliasKind
import com.cuscus.ffmpeggui.core.command.CommandFile
import com.cuscus.ffmpeggui.core.command.studio.CatalogOption
import com.cuscus.ffmpeggui.core.command.studio.CatalogTopic
import com.cuscus.ffmpeggui.core.command.studio.CommandRecipe
import com.cuscus.ffmpeggui.core.command.studio.DocTopic
import com.cuscus.ffmpeggui.core.command.studio.FfmpegCatalog
import com.cuscus.ffmpeggui.core.command.studio.LintCode
import com.cuscus.ffmpeggui.core.command.studio.LintIssue
import com.cuscus.ffmpeggui.core.command.studio.LintSeverity
import com.cuscus.ffmpeggui.core.command.studio.RecipeGroup
import com.cuscus.ffmpeggui.core.command.studio.Suggestion
import com.cuscus.ffmpeggui.core.command.studio.oneLineCommand
import com.cuscus.ffmpeggui.core.command.studio.tidyCommand
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.expressive.springPress

private val Mono = FontFamily.Monospace

// -- Shared pieces -------------------------------------------------------------------------------

/** A shape badge with a glyph: the studio's way of telling kinds apart at a glance. */
@Composable
private fun Badge(shape: RoundedPolygon, color: androidx.compose.ui.graphics.Color, size: Int = 36, content: @Composable () -> Unit = {}) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).clip(remember(shape) { PolygonShape(shape) }).background(color),
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** What a panel shows when it has nothing yet: a big shape and one line. */
@Composable
private fun EmptyPanel(shape: RoundedPolygon, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Badge(shape, scheme.tertiaryContainer, size = 64)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

/** A tappable row on its own rounded surface, which squishes on press. */
@Composable
private fun PanelRow(onClick: (() -> Unit)?, enabled: Boolean = true, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    if (onClick == null) {
        Surface(color = scheme.surfaceContainerHighest, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) { content() }
        }
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            color = scheme.surfaceContainerHighest,
            shape = RoundedCornerShape(20.dp),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().springPress(interaction, pressedScale = 0.97f),
        ) {
            Box(Modifier.padding(12.dp)) { content() }
        }
    }
}

// -- Docs ----------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DocsPanel(ui: CommandStudioUi, topic: DocTopic?) {
    val scheme = MaterialTheme.colorScheme
    val key = when (topic) {
        is DocTopic.Filter -> "filter=${topic.name}"
        is DocTopic.Encoder -> "encoder=${topic.name}"
        is DocTopic.Muxer -> "muxer=${topic.name}"
        else -> null
    }
    LaunchedEffect(key) { key?.let(ui.onRequestHelp) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        when (topic) {
            null -> EmptyPanel(MaterialShapes.Cookie12Sided, stringResource(R.string.studio_docs_empty))
            is DocTopic.Option -> {
                val all = ui.catalog.allNamed(topic.written)
                val first = all.firstOrNull()
                DocHeader("-" + FfmpegCatalog.baseName(topic.written), first?.section ?: "", MaterialShapes.Cookie4Sided, scheme.secondary)
                if (first == null) {
                    Text(stringResource(R.string.studio_docs_unknown, topic.written), style = MaterialTheme.typography.bodyMedium)
                } else {
                    OptionBody(first)
                    val places = all.map { it.section }.filter { it.isNotEmpty() && it != first.section }.distinct()
                    if (places.isNotEmpty()) {
                        Text(
                            stringResource(R.string.studio_docs_also, places.joinToString(", ") { it.removeSuffix(" AVOptions") }),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    val values = ui.catalog.valuesOf(topic.written)
                    if (values.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.studio_docs_values))
                        values.forEach { v -> ValueLine(v.name, v.help) }
                    }
                }
            }
            is DocTopic.Filter -> {
                val f = ui.catalog.filter(topic.name)
                DocHeader(topic.name, f?.let { "${it.inputs.ifEmpty { "|" }} \u2192 ${it.outputs.ifEmpty { "|" }}" } ?: "", MaterialShapes.Flower, scheme.tertiary)
                f?.help?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TopicOptions(ui.catalog.filterHelp[topic.name], showSummary = false)
            }
            is DocTopic.Encoder -> {
                val e = ui.catalog.encoder(topic.name)
                DocHeader(topic.name, e?.type?.let { typeWord(it) } ?: "", MaterialShapes.Gem, scheme.primary)
                e?.help?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TopicOptions(ui.topics["encoder=${topic.name}"], showSummary = true)
            }
            is DocTopic.Muxer -> {
                val m = ui.catalog.muxer(topic.name)
                DocHeader(topic.name, "", MaterialShapes.Pentagon, scheme.secondary)
                m?.help?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TopicOptions(ui.topics["muxer=${topic.name}"], showSummary = true)
            }
        }
    }
}

private fun typeWord(type: Char): String = when (type) {
    'V' -> "video"
    'A' -> "audio"
    else -> "subtitles"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocHeader(name: String, tag: String, shape: RoundedPolygon, color: androidx.compose.ui.graphics.Color) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(shape, color, size = 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFamily = Mono), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tag.isNotEmpty()) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OptionBody(o: CatalogOption) {
    val scheme = MaterialTheme.colorScheme
    if (o.help.isNotEmpty()) Text(o.help, style = MaterialTheme.typography.bodyMedium)
    Text(
        text = o.argument?.let { stringResource(R.string.studio_docs_takes, it.removeSurrounding("<", ">")) } ?: stringResource(R.string.studio_docs_switch),
        style = MaterialTheme.typography.labelLarge,
        color = scheme.secondary,
    )
    o.default?.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.studio_docs_default, it), style = MaterialTheme.typography.labelMedium) }
    o.range?.let { Text(stringResource(R.string.studio_docs_range, it), style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun ValueLine(name: String, help: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Bold), modifier = Modifier.width(140.dp))
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopicOptions(topic: CatalogTopic?, showSummary: Boolean) {
    if (topic == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.studio_docs_loading), style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    if (showSummary) {
        topic.summary.drop(1).take(SUMMARY_LINES).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (topic.options.isNotEmpty()) {
        SectionTitle(stringResource(R.string.studio_docs_options))
        topic.options.forEach { o ->
            val extra = listOfNotNull(o.argument?.removeSurrounding("<", ">"), o.default?.takeIf { it.isNotEmpty() }?.let { "= $it" }).joinToString(" ")
            ValueLine(o.name, listOf(o.help, extra).filter { it.isNotEmpty() }.joinToString(" \u00b7 "))
        }
    }
}

private const val SUMMARY_LINES = 6

// -- Files ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilesPanel(ui: CommandStudioUi, onInsert: (String) -> Unit, onRenamed: (String, String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ui.onAddFiles(uris)
    }
    var renaming by remember { mutableStateOf<CommandFile?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.studio_files_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        SectionTitle(stringResource(R.string.studio_files_project))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.aliases.filter { it.kind != AliasKind.EXTRA }.forEach { a ->
                AliasChip(a.alias, kindLabel(a.kind), a.kind) { onInsert(a.alias) }
            }
        }
        SectionTitle(stringResource(R.string.studio_files_added))
        ui.files.forEach { f ->
            PanelRow(onClick = { onInsert(f.alias) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(MaterialShapes.Arch, scheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.alias, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.name, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { renaming = f }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.studio_files_rename))
                    }
                    IconButton(onClick = { ui.onRemoveFile(f.alias) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.studio_files_remove), tint = scheme.error)
                    }
                }
            }
        }
        Button(
            onClick = { pick.launch(arrayOf("*/*")) },
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.studio_files_add), style = MaterialTheme.typography.titleMedium)
        }
    }
    renaming?.let { file ->
        RenameDialog(
            file = file,
            taken = ui.aliases.map { it.alias }.toSet() - file.alias,
            onDismiss = { renaming = null },
            onRename = { newAlias, alsoInCommand ->
                ui.onRenameFile(file.alias, newAlias)
                if (alsoInCommand) onRenamed(file.alias, newAlias)
                renaming = null
            },
        )
    }
}

@Composable
private fun kindLabel(kind: AliasKind): String = stringResource(
    when (kind) {
        AliasKind.INPUT -> R.string.studio_kind_input
        AliasKind.OVERLAY -> R.string.studio_kind_overlay
        AliasKind.SOUND -> R.string.studio_kind_sound
        AliasKind.SUBTITLES -> R.string.studio_kind_subtitles
        AliasKind.OTHER -> R.string.studio_kind_other
        AliasKind.EXTRA -> R.string.studio_kind_extra
        AliasKind.OUTPUT -> R.string.studio_kind_output
    },
)

@Composable
private fun AliasChip(alias: String, kind: String, aliasKind: AliasKind, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val output = aliasKind == AliasKind.OUTPUT
    Surface(
        onClick = onClick,
        color = if (output) scheme.primaryContainer else scheme.secondaryContainer,
        contentColor = if (output) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        interactionSource = interaction,
        modifier = Modifier.springPress(interaction),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(alias, style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold))
            Text(kind, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RenameDialog(file: CommandFile, taken: Set<String>, onDismiss: () -> Unit, onRename: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(file.alias) }
    var also by remember { mutableStateOf(true) }
    val valid = CommandFile.isValidAlias(name)
    val free = name !in taken
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_files_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.trim() },
                    singleLine = true,
                    isError = !valid || !free,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                    supportingText = {
                        when {
                            !valid -> Text(stringResource(R.string.studio_files_rename_invalid))
                            !free -> Text(stringResource(R.string.studio_files_rename_taken))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = also, onCheckedChange = { also = it })
                    Text(stringResource(R.string.studio_files_rename_also), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name, also) }, enabled = valid && free && name != file.alias) {
                Text(stringResource(R.string.studio_files_rename))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// -- Recipes -------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecipesPanel(ui: CommandStudioUi, onUse: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.studio_recipes_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        RecipeGroup.entries.forEach { group ->
            SectionTitle(stringResource(groupTitle(group)))
            CommandRecipe.ALL.filter { it.group == group }.forEach { recipe ->
                val filled = remember(recipe, ui.aliases) { recipe.fill(ui.aliases) }
                val missing = remember(recipe, ui.catalog) { recipe.missingEncoders(ui.catalog) }
                val usable = filled != null && missing.isEmpty()
                PanelRow(onClick = filled?.takeIf { usable }?.let { { onUse(it) } }, enabled = usable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(groupShape(group), if (usable) scheme.tertiary else scheme.outlineVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(recipe.title, style = MaterialTheme.typography.titleSmallEmphasized)
                            Text(
                                text = when {
                                    missing.isNotEmpty() -> stringResource(R.string.studio_recipe_missing, missing.joinToString(", "))
                                    filled != null -> recipe.summary
                                    recipe.needsSubtitles -> stringResource(R.string.studio_recipe_needs_subtitles)
                                    else -> stringResource(R.string.studio_recipe_needs_second)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                text = filled ?: recipe.template,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                                color = scheme.secondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun groupTitle(group: RecipeGroup): Int = when (group) {
    RecipeGroup.CUT -> R.string.studio_recipe_group_cut
    RecipeGroup.SHRINK -> R.string.studio_recipe_group_shrink
    RecipeGroup.LOOK -> R.string.studio_recipe_group_look
    RecipeGroup.SOUND -> R.string.studio_recipe_group_sound
    RecipeGroup.MAKE -> R.string.studio_recipe_group_make
}

private fun groupShape(group: RecipeGroup): RoundedPolygon = when (group) {
    RecipeGroup.CUT -> MaterialShapes.Slanted
    RecipeGroup.SHRINK -> MaterialShapes.Gem
    RecipeGroup.LOOK -> MaterialShapes.Flower
    RecipeGroup.SOUND -> MaterialShapes.Sunny
    RecipeGroup.MAKE -> MaterialShapes.Burst
}

// -- History -------------------------------------------------------------------------------------

@Composable
internal fun HistoryPanel(ui: CommandStudioUi, current: String, onUse: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (ui.favourites.isEmpty() && ui.history.isEmpty()) {
            EmptyPanel(MaterialShapes.Clover8Leaf, stringResource(R.string.studio_history_empty))
            return@Column
        }
        if (ui.favourites.isNotEmpty()) {
            SectionTitle(stringResource(R.string.studio_history_favourites))
            ui.favourites.forEach { c -> HistoryRow(c, starred = true, current = current, onUse = onUse, onStar = { ui.onFavourite(c, it) }, onForget = null) }
        }
        val recent = ui.history.filter { it !in ui.favourites }
        if (recent.isNotEmpty()) {
            SectionTitle(stringResource(R.string.studio_history_recent))
            recent.forEach { c -> HistoryRow(c, starred = false, current = current, onUse = onUse, onStar = { ui.onFavourite(c, it) }, onForget = { ui.onForget(c) }) }
        }
    }
}

@Composable
private fun HistoryRow(command: String, starred: Boolean, current: String, onUse: (String) -> Unit, onStar: (Boolean) -> Unit, onForget: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val same = command.trim() == current.trim()
    PanelRow(onClick = { onUse(command) }, enabled = !same) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                color = if (same) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onStar(!starred) }) {
                Icon(
                    imageVector = if (starred) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    contentDescription = stringResource(R.string.studio_favourite),
                    tint = if (starred) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            onForget?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.studio_history_forget))
                }
            }
        }
    }
}

// -- Tools and the checker -----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolsPanel(
    text: String,
    catalog: FfmpegCatalog,
    issues: List<LintIssue>,
    onReplace: (String) -> Unit,
    onGoTo: (LintIssue) -> Unit,
    onFix: (Suggestion) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.studio_tool_tidy)) {
                tidyCommand(text, catalog).takeIf { it != text }?.let(onReplace)
            }
            ToolButton(Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.studio_tool_one_line)) {
                oneLineCommand(text).takeIf { it != text }?.let(onReplace)
            }
            ToolButton(Icons.Rounded.ContentCopy, stringResource(R.string.studio_tool_copy)) {
                clipboard.setText(AnnotatedString(text))
            }
            ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.studio_tool_clear)) {
                if (text != "ffmpeg ") onReplace("ffmpeg ")
            }
        }
        SectionTitle(stringResource(R.string.studio_issues))
        if (issues.isEmpty()) {
            EmptyPanel(MaterialShapes.Sunny, stringResource(R.string.studio_issues_none))
        }
        issues.forEach { issue ->
            val error = issue.severity == LintSeverity.ERROR
            PanelRow(onClick = { onGoTo(issue) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(if (error) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie6Sided, if (error) scheme.error else scheme.tertiary) {
                        Icon(
                            imageVector = if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (error) scheme.onError else scheme.onTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(lintMessage(issue), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    issue.fix?.let { fix ->
                        FilledTonalButton(onClick = { onFix(fix) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.studio_fix_add, fix.label), fontFamily = Mono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
internal fun lintMessage(issue: LintIssue): String = when (issue.code) {
    LintCode.OPEN_QUOTE -> stringResource(R.string.lint_open_quote, issue.subject)
    LintCode.MISSING_VALUE -> stringResource(R.string.lint_missing_value, issue.subject)
    LintCode.UNKNOWN_OPTION -> stringResource(R.string.lint_unknown_option, issue.subject)
    LintCode.UNKNOWN_FILTER -> stringResource(R.string.lint_unknown_filter, issue.subject)
    LintCode.UNKNOWN_ENCODER -> stringResource(R.string.lint_unknown_encoder, issue.subject)
    LintCode.UNBALANCED -> stringResource(R.string.lint_unbalanced, issue.subject)
    LintCode.NO_INPUT -> stringResource(R.string.lint_no_input)
    LintCode.NO_OUTPUT -> stringResource(R.string.lint_no_output, issue.subject)
    LintCode.BAD_INPUT_INDEX -> stringResource(R.string.lint_bad_input_index, issue.subject)
    LintCode.UNKNOWN_FILE -> stringResource(R.string.lint_unknown_file, issue.subject)
}
