@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.command

import android.net.Uri
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.command.CommandAlias
import com.cuscus.ffmpeggui.core.command.CommandFile
import com.cuscus.ffmpeggui.core.command.studio.CatalogTopic
import com.cuscus.ffmpeggui.core.command.studio.CommandAssist
import com.cuscus.ffmpeggui.core.command.studio.CommandJokes
import com.cuscus.ffmpeggui.core.command.studio.CommandLint
import com.cuscus.ffmpeggui.core.command.studio.DocTopic
import com.cuscus.ffmpeggui.core.command.studio.FfmpegCatalog
import com.cuscus.ffmpeggui.core.command.studio.LintIssue
import com.cuscus.ffmpeggui.core.command.studio.LintSeverity
import com.cuscus.ffmpeggui.core.command.studio.Suggestion
import com.cuscus.ffmpeggui.core.command.studio.SuggestionKind
import com.cuscus.ffmpeggui.core.command.studio.applySuggestion
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.launch

/**
 * The command studio, for the editors: what FFmpeg knows, the files a command
 * may name, the commands used and starred, and what can be done with them.
 */
class CommandStudioUi(
    val catalog: FfmpegCatalog,
    /** FFmpeg is being asked what it knows (the first time only). */
    val reading: Boolean,
    /** FFmpeg's help about encoders and formats asked about so far ("encoder=libx264"). */
    val topics: Map<String, CatalogTopic>,
    val aliases: List<CommandAlias>,
    val files: List<CommandFile>,
    val history: List<String>,
    val favourites: List<String>,
    val onNeedCatalog: () -> Unit,
    val onRequestHelp: (String) -> Unit,
    val onAddFiles: (List<Uri>) -> Unit,
    val onRenameFile: (String, String) -> Unit,
    val onRemoveFile: (String) -> Unit,
    val onRemember: (String) -> Unit,
    val onForget: (String) -> Unit,
    val onFavourite: (String, Boolean) -> Unit,
)

val LocalCommandStudio = compositionLocalOf<CommandStudioUi?> { null }

/** The studio's panels, under the editor. */
internal enum class StudioPanel { DOCS, FILES, RECIPES, HISTORY, TOOLS }

/** Undo and redo of the studio's own changes and typing (typing is grouped by pauses). */
internal class StudioHistory {
    val past = mutableStateListOf<TextFieldValue>()
    val future = mutableStateListOf<TextFieldValue>()
    private var lastTyping = 0L

    fun record(before: TextFieldValue, typing: Boolean) {
        val now = System.currentTimeMillis()
        if (!typing || now - lastTyping > TYPING_PAUSE_MS || past.isEmpty()) {
            past += before
            if (past.size > MAX_STEPS) past.removeAt(0)
        }
        lastTyping = if (typing) now else 0L
        future.clear()
    }

    companion object {
        private const val TYPING_PAUSE_MS = 900L
        private const val MAX_STEPS = 80
    }
}

/**
 * The full-screen studio for a hand-written command. Nothing is ever typed,
 * fixed or completed for the user: suggestions sit in a strip above the
 * keyboard and only a tap (or Tab on a keyboard) takes one; every change the
 * studio makes on request can be undone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun CommandStudioDialog(
    initial: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Runs the command written here; null hides the button (the studio opened from an editor). */
    onRun: (() -> Unit)? = null,
) {
    val ui = LocalCommandStudio.current ?: return
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }
    val history = remember { StudioHistory() }
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var panel by remember { mutableStateOf<StudioPanel?>(null) }
    val replacedMessage = stringResource(R.string.studio_replaced)
    val undoLabel = stringResource(R.string.editor_undo)

    LaunchedEffect(Unit) { ui.onNeedCatalog() }

    fun commit(next: TextFieldValue, typing: Boolean) {
        if (next.text != value.text) {
            history.record(value, typing)
            onTextChange(next.text)
        }
        value = next
    }

    fun take(s: Suggestion) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val (text, cursor) = applySuggestion(value.text, s)
        commit(TextFieldValue(text, TextRange(cursor)), typing = false)
    }

    /** A whole new command, asked for: said so, with a way back. */
    fun replaceAll(text: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        commit(TextFieldValue(text, TextRange(text.length)), typing = false)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(replacedMessage, actionLabel = undoLabel) == SnackbarResult.ActionPerformed) {
                history.past.removeLastOrNull()?.let {
                    history.future += value
                    value = it
                    onTextChange(it.text)
                }
            }
        }
    }

    fun undo() {
        val before = history.past.removeLastOrNull() ?: return
        history.future += value
        value = before
        onTextChange(before.text)
    }

    fun redo() {
        val after = history.future.removeLastOrNull() ?: return
        history.past += value
        value = after
        onTextChange(after.text)
    }

    val cursor = value.selection.end
    val collapsed = value.selection.collapsed
    val assist = remember(value.text, cursor, collapsed, ui.catalog, ui.aliases) {
        if (collapsed) CommandAssist(ui.catalog, ui.aliases).at(value.text, cursor) else null
    }
    val issues = remember(value.text, ui.catalog, ui.aliases) { CommandLint(ui.catalog, ui.aliases).check(value.text) }
    // A terminal classic typed as a whole: a reply instead of errors, and it never runs.
    val joke = remember(value.text) { CommandJokes.replyTo(value.text) }
    LaunchedEffect(assist?.needsFilterHelp) { assist?.needsFilterHelp?.let { ui.onRequestHelp("filter=$it") } }

    Dialog(
        onDismissRequest = {
            if (value.text != initial) ui.onRemember(value.text)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            containerColor = scheme.surfaceContainerLow,
            snackbarHost = { SnackbarHost(snackbar) },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            // With the keyboard up the room is short: only the editor and its suggestions stay.
            val imeOpen = WindowInsets.isImeVisible
            // Typing again (a tap on the editor while a panel is open): the panel folds away, the editor needs the room.
            LaunchedEffect(imeOpen) {
                if (imeOpen) panel = null
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // The bars already paid for by the scaffold must not be counted again under the keyboard.
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                StudioHeader(
                    ui = ui,
                    issues = issues,
                    canUndo = history.past.isNotEmpty(),
                    canRedo = history.future.isNotEmpty(),
                    starred = value.text.trim() in ui.favourites,
                    onClose = {
                        if (value.text != initial) ui.onRemember(value.text)
                        onDismiss()
                    },
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onStar = { ui.onFavourite(value.text, it) },
                    onIssues = {
                        panel = StudioPanel.TOOLS
                        keyboard?.hide()
                    },
                    compact = imeOpen,
                    onRun = onRun?.let { run ->
                        {
                            if (value.text != initial) ui.onRemember(value.text)
                            keyboard?.hide()
                            run()
                        }
                    },
                    canRun = value.text.isNotBlank() && joke == null,
                )
                StudioReply(joke)
                StudioEditor(
                    value = value,
                    catalog = ui.catalog,
                    issues = issues,
                    focus = focus,
                    onValueChange = { commit(it, typing = true) },
                    onTab = { assist?.suggestions?.firstOrNull()?.let(::take) != null },
                    modifier = Modifier.weight(1f),
                )
                SuggestionStrip(
                    suggestions = assist?.suggestions.orEmpty(),
                    onTake = ::take,
                    onKey = { key ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val start = value.selection.min
                        val end = value.selection.max
                        val text = value.text.replaceRange(start, end, key)
                        commit(TextFieldValue(text, TextRange(start + key.length)), typing = false)
                    },
                )
                AnimatedVisibility(
                    visible = panel != null,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Surface(
                        color = scheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    ) {
                        val fx = MaterialTheme.motionScheme
                        AnimatedContent(
                            targetState = panel,
                            transitionSpec = {
                                fadeIn(fx.defaultEffectsSpec()) togetherWith fadeOut(fx.fastEffectsSpec())
                            },
                            label = "studioPanel",
                        ) { shown ->
                            Box(Modifier.padding(16.dp)) {
                                when (shown) {
                                    StudioPanel.DOCS -> DocsPanel(ui, assist?.topic)
                                    StudioPanel.FILES -> FilesPanel(
                                        ui = ui,
                                        onInsert = { alias ->
                                            val start = value.selection.min
                                            val text = value.text.replaceRange(start, value.selection.max, alias)
                                            commit(TextFieldValue(text, TextRange(start + alias.length)), typing = false)
                                        },
                                        onRenamed = { old, new -> renameInCommand(value.text, old, new)?.let(::replaceAll) },
                                    )
                                    StudioPanel.RECIPES -> RecipesPanel(ui, onUse = ::replaceAll)
                                    StudioPanel.HISTORY -> HistoryPanel(ui, current = value.text, onUse = ::replaceAll)
                                    StudioPanel.TOOLS -> ToolsPanel(
                                        text = value.text,
                                        catalog = ui.catalog,
                                        issues = issues,
                                        onReplace = ::replaceAll,
                                        onGoTo = { issue ->
                                            value = value.copy(selection = TextRange(issue.start, issue.end))
                                            focus.requestFocus()
                                        },
                                        onFix = ::take,
                                    )
                                    null -> Unit
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !imeOpen || panel != null,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    StudioDock(
                        panel = panel,
                        onPanel = { chosen ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            panel = if (panel == chosen) null else chosen
                            if (panel != null) keyboard?.hide()
                        },
                    )
                }
            }
        }
    }
}

/** Swaps the alias [old] for [new] in [text], where it stands alone; null when it is not there. */
internal fun renameInCommand(text: String, old: String, new: String): String? {
    val pattern = Regex("""(?<![A-Za-z0-9_./\\-])${Regex.escape(old)}(?![A-Za-z0-9_])""")
    if (!pattern.containsMatchIn(text)) return null
    return pattern.replace(text) { new }
}

// -- Header --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StudioHeader(
    ui: CommandStudioUi,
    issues: List<LintIssue>,
    canUndo: Boolean,
    canRedo: Boolean,
    starred: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStar: (Boolean) -> Unit,
    onIssues: () -> Unit,
    /** The keyboard is up: the checker's badge folds away to leave the room to the editor. */
    compact: Boolean = false,
    /** Runs the command; null when the studio only edits it. */
    onRun: (() -> Unit)? = null,
    canRun: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val errors = issues.count { it.severity == LintSeverity.ERROR }
    val warnings = issues.size - errors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.studio_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val fx = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = ui.reading to ui.catalog.isComplete,
                transitionSpec = { fadeIn(fx.defaultEffectsSpec()) togetherWith fadeOut(fx.fastEffectsSpec()) },
                label = "studioStatus",
            ) { (reading, complete) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reading) {
                        LoadingIndicator(color = scheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = when {
                            reading -> stringResource(R.string.studio_reading)
                            complete -> stringResource(
                                R.string.studio_knows,
                                ui.catalog.options.distinctBy { it.name }.size,
                                ui.catalog.filters.size,
                                ui.catalog.encoders.size,
                            )
                            else -> stringResource(R.string.studio_basic)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo))
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo))
        }
        FilledIconToggleButton(checked = starred, onCheckedChange = onStar) {
            Icon(
                imageVector = if (starred) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                contentDescription = stringResource(R.string.studio_favourite),
            )
        }
        if (onRun != null) {
            // The key action: the widest, filled button, at the thumb's corner; its shape answers the press.
            Spacer(Modifier.width(4.dp))
            FilledIconButton(
                onClick = onRun,
                enabled = canRun,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_convert))
            }
        }
    }
    // The checker's verdict: a calm round badge, or a spiky one that says how much is wrong.
    val interaction = remember { MutableInteractionSource() }
    val bad = errors > 0
    val container by animateColorAsState(
        when {
            bad -> scheme.errorContainer
            warnings > 0 -> scheme.tertiaryContainer
            else -> scheme.primaryContainer
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "lintColor",
    )
    val content = when {
        bad -> scheme.onErrorContainer
        warnings > 0 -> scheme.onTertiaryContainer
        else -> scheme.onPrimaryContainer
    }
    AnimatedVisibility(
        visible = !compact,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Surface(
            onClick = onIssues,
            color = container,
            shape = RoundedCornerShape(50),
            interactionSource = interaction,
            modifier = Modifier.springPress(interaction),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            ) {
                MorphingContainer(
                    from = MaterialShapes.Circle,
                    to = MaterialShapes.Cookie9Sided,
                    morphed = bad || warnings > 0,
                    color = content,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = when {
                            bad -> Icons.Rounded.ErrorOutline
                            warnings > 0 -> Icons.Rounded.WarningAmber
                            else -> Icons.Rounded.Check
                        },
                        contentDescription = null,
                        tint = container,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        bad -> pluralStringResource(R.plurals.studio_errors, errors, errors)
                        warnings > 0 -> pluralStringResource(R.plurals.studio_warnings, warnings, warnings)
                        else -> stringResource(R.string.studio_all_good)
                    },
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = content,
                )
            }
        }
    }
}

// -- Editor ------------------------------------------------------------------------------------

@Composable
private fun StudioEditor(
    value: TextFieldValue,
    catalog: FfmpegCatalog,
    issues: List<LintIssue>,
    focus: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    /** Tab on a keyboard: takes the first suggestion; false when there is none. */
    onTab: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val highlight = remember(scheme, catalog, issues) { StudioHighlight(scheme, catalog, issues) }
    Surface(
        color = scheme.surfaceContainerHighest,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (value.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.studio_empty_hint),
                    style = editorStyle().copy(color = scheme.onSurfaceVariant),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = editorStyle().copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                visualTransformation = highlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        event.key == Key.Tab && event.type == KeyEventType.KeyDown && onTab()
                    },
            )
        }
    }
}

@Composable
private fun editorStyle() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 23.sp)

// -- Suggestions -------------------------------------------------------------------------------

/** Keys a phone keyboard hides away, one tap each. */
private val QUICK_KEYS = listOf("-", ":", "=", ",", ";", "[", "]", "\"", "'", "\\\n  ")

/**
 * The suggestions for the word under the cursor, then a row of keys. The
 * first suggestion is the one Tab (or the big key) takes; any can be tapped.
 */
@Composable
private fun SuggestionStrip(
    suggestions: List<Suggestion>,
    onTake: (Suggestion) -> Unit,
    onKey: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val fx = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = suggestions.isEmpty(),
            transitionSpec = { fadeIn(fx.defaultEffectsSpec()) togetherWith fadeOut(fx.fastEffectsSpec()) },
            label = "suggestionsShown",
        ) { empty ->
            if (empty) {
                Text(
                    text = stringResource(R.string.studio_no_suggestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.height(56.dp).padding(start = 8.dp, top = 18.dp),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    itemsIndexed(suggestions, key = { i, s -> "$i:${s.label}" }) { i, s ->
                        SuggestionChip(s, first = i == 0, onClick = { onTake(s) })
                    }
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                KeyPill(
                    label = stringResource(R.string.studio_accept),
                    accent = true,
                    enabled = suggestions.isNotEmpty(),
                    icon = true,
                    onClick = { suggestions.firstOrNull()?.let(onTake) },
                )
            }
            itemsIndexed(QUICK_KEYS) { _, k ->
                KeyPill(label = if (k.startsWith("\\")) "\\\u21b5" else k, onClick = { onKey(k) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(s: Suggestion, first: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (first) 20.dp else 12.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chipCorner")
    val shape = kindShape(s.kind)
    Surface(
        onClick = onClick,
        color = if (first) scheme.primaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (first) scheme.onPrimaryContainer else scheme.onSurface,
        shape = RoundedCornerShape(corner),
        interactionSource = interaction,
        modifier = Modifier.springPress(interaction).height(56.dp).widthIn(max = 280.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(26.dp).clip(remember(shape) { PolygonShape(shape) }).background(kindColor(s.kind, scheme)),
            ) {
                Text(kindGlyph(s.kind), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black), color = scheme.surface)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (s.detail.isNotEmpty()) {
                    Text(
                        text = s.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (first) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyPill(label: String, onClick: () -> Unit, accent: Boolean = false, enabled: Boolean = true, icon: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (accent) scheme.tertiary else scheme.secondaryContainer,
        contentColor = if (accent) scheme.onTertiary else scheme.onSecondaryContainer,
        shape = RoundedCornerShape(if (accent) 16.dp else 12.dp),
        interactionSource = interaction,
        modifier = Modifier.springPress(interaction).height(40.dp).widthIn(min = 44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (icon) {
                Icon(Icons.Rounded.KeyboardDoubleArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontFamily = if (accent) FontFamily.Default else FontFamily.Monospace, fontWeight = FontWeight.Bold))
        }
    }
}

private fun kindGlyph(kind: SuggestionKind): String = when (kind) {
    SuggestionKind.OPTION -> "-"
    SuggestionKind.VALUE -> "v"
    SuggestionKind.FILTER -> "f"
    SuggestionKind.FILTER_OPTION -> "="
    SuggestionKind.ENCODER -> "c"
    SuggestionKind.FORMAT -> "m"
    SuggestionKind.FILE -> "@"
    SuggestionKind.STREAM -> "#"
    SuggestionKind.FIX -> "!"
}

internal fun kindShape(kind: SuggestionKind): RoundedPolygon = when (kind) {
    SuggestionKind.OPTION -> MaterialShapes.Cookie4Sided
    SuggestionKind.VALUE -> MaterialShapes.Sunny
    SuggestionKind.FILTER -> MaterialShapes.Flower
    SuggestionKind.FILTER_OPTION -> MaterialShapes.Clover4Leaf
    SuggestionKind.ENCODER -> MaterialShapes.Gem
    SuggestionKind.FORMAT -> MaterialShapes.Pentagon
    SuggestionKind.FILE -> MaterialShapes.Arch
    SuggestionKind.STREAM -> MaterialShapes.Diamond
    SuggestionKind.FIX -> MaterialShapes.SoftBurst
}

internal fun kindColor(kind: SuggestionKind, s: ColorScheme): Color = when (kind) {
    SuggestionKind.OPTION, SuggestionKind.FORMAT, SuggestionKind.STREAM -> s.secondary
    SuggestionKind.VALUE, SuggestionKind.ENCODER, SuggestionKind.FILE -> s.primary
    SuggestionKind.FILTER, SuggestionKind.FILTER_OPTION -> s.tertiary
    SuggestionKind.FIX -> s.error
}

// -- Dock --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StudioDock(panel: StudioPanel?, onPanel: (StudioPanel) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            StudioPanel.entries.forEach { p ->
                FilledIconToggleButton(checked = panel == p, onCheckedChange = { onPanel(p) }) {
                    Icon(
                        imageVector = when (p) {
                            StudioPanel.DOCS -> Icons.AutoMirrored.Rounded.HelpOutline
                            StudioPanel.FILES -> Icons.Rounded.Folder
                            StudioPanel.RECIPES -> Icons.Rounded.AutoAwesome
                            StudioPanel.HISTORY -> Icons.Rounded.History
                            StudioPanel.TOOLS -> Icons.Rounded.Code
                        },
                        contentDescription = stringResource(
                            when (p) {
                                StudioPanel.DOCS -> R.string.studio_docs
                                StudioPanel.FILES -> R.string.studio_files
                                StudioPanel.RECIPES -> R.string.studio_recipes
                                StudioPanel.HISTORY -> R.string.studio_history
                                StudioPanel.TOOLS -> R.string.studio_tools
                            },
                        ),
                    )
                }
            }
        }
    }
}
