@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.svgedit

import com.cuscus.ffmpeggui.TracingOverlay
import androidx.compose.ui.zIndex
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ZoomPill
import com.cuscus.ffmpeggui.core.vector.svg.Affine
import com.cuscus.ffmpeggui.core.vector.svg.isLocked
import com.cuscus.ffmpeggui.core.vector.svg.setLocked
import com.cuscus.ffmpeggui.core.vector.svg.joinPaths
import com.cuscus.ffmpeggui.core.vector.svg.breakApart
import com.cuscus.ffmpeggui.core.vector.svg.reversePath
import com.cuscus.ffmpeggui.core.vector.svg.smoothPath
import com.cuscus.ffmpeggui.core.vector.svg.simplifyPath
import com.cuscus.ffmpeggui.core.vector.svg.reshape
import com.cuscus.ffmpeggui.core.vector.svg.repeatAround
import com.cuscus.ffmpeggui.core.vector.svg.repeatInGrid
import com.cuscus.ffmpeggui.core.vector.svg.repeatInRow
import com.cuscus.ffmpeggui.core.vector.svg.mirrorCopy
import com.cuscus.ffmpeggui.core.vector.svg.gridStep
import com.cuscus.ffmpeggui.core.vector.svg.symmetry
import com.cuscus.ffmpeggui.core.vector.svg.EditPath
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgKeys
import com.cuscus.ffmpeggui.core.vector.svg.SvgPaint
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.SvgStyle
import com.cuscus.ffmpeggui.core.vector.svg.editPath
import com.cuscus.ffmpeggui.core.vector.svg.editable
import com.cuscus.ffmpeggui.core.vector.svg.isConnector
import com.cuscus.ffmpeggui.core.vector.svg.svgNumber
import com.cuscus.ffmpeggui.core.vector.svg.update
import com.cuscus.ffmpeggui.core.vector.svg.withEditPath
import com.cuscus.ffmpeggui.core.vector.svg.worldTransform
import com.cuscus.ffmpeggui.core.vector.svg.SvgOrder
import com.cuscus.ffmpeggui.core.vector.svg.canReorder
import com.cuscus.ffmpeggui.core.vector.svg.duplicate
import com.cuscus.ffmpeggui.core.vector.svg.find
import com.cuscus.ffmpeggui.core.vector.svg.moveAbove
import com.cuscus.ffmpeggui.core.vector.svg.moveBelow
import com.cuscus.ffmpeggui.core.vector.svg.nodeCount
import com.cuscus.ffmpeggui.core.vector.svg.parentOf
import com.cuscus.ffmpeggui.core.vector.svg.remove
import com.cuscus.ffmpeggui.core.vector.svg.reorder
import com.cuscus.ffmpeggui.core.vector.svg.setHidden
import com.cuscus.ffmpeggui.core.vector.svg.worldBounds
import com.cuscus.ffmpeggui.core.vector.svg.align
import com.cuscus.ffmpeggui.core.vector.svg.boundsOf
import com.cuscus.ffmpeggui.core.vector.svg.distribute
import com.cuscus.ffmpeggui.core.vector.svg.flip
import com.cuscus.ffmpeggui.core.vector.svg.group
import com.cuscus.ffmpeggui.core.vector.svg.outermost
import com.cuscus.ffmpeggui.core.vector.svg.toPath
import com.cuscus.ffmpeggui.core.vector.svg.transformAll
import com.cuscus.ffmpeggui.core.vector.svg.ungroup
import com.cuscus.ffmpeggui.rememberStageZoom
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * The SVG editor: a drawing opened from a file (or started empty) on a stage
 * of its own, what is in it selected with a tap and moved, sized and turned
 * with the fingers. Material 3 Expressive all the way:
 *
 *  - the header's title in emphasized type, undo and redo as a connected
 *    pair, and a big morphing Save;
 *  - over the stage, a pill naming what is selected, its badge morphing into
 *    that kind's own shape, sliding and popping as the selection changes;
 *  - at the bottom a vibrant floating toolbar whose actions unfold from its
 *    FAB (the layers) only while something is selected;
 *  - opening shows the contained loading indicator; a drawing that cannot be
 *    opened says so with a broken cookie.
 */

@Composable
internal fun SvgEditorScreen(
    vm: SvgEditorViewModel,
    onPickAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val load by vm.load.collectAsState()
    val docState = vm.doc.collectAsState()
    val selectedState = vm.selected.collectAsState()
    val doc = docState.value
    val selected = selectedState.value
    val alsoState = vm.also.collectAsState()
    val multiState = vm.multi.collectAsState()
    val multi = multiState.value
    val canPaste by vm.hasCopiedStyle.collectAsState()
    // Everything selected, the one picked first first.
    val selection = listOfNotNull(selected) + alsoState.value.filter { it != selected && doc.find(it) != null }
    val name by vm.name.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    val dirty by vm.dirty.collectAsState()
    val renderer = remember { SvgRenderer() }
    val zoom = rememberStageZoom()
    LaunchedEffect(name) { renderer.forget() }
    val tracing by vm.tracing.collectAsState()

    // The tour, the first time the editor opens on this phone (and from the help button).
    var touring by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(load) {
        if (load == SvgEditorViewModel.Load.Ready && !tourSeen(context)) touring = true
    }
    var askingText by remember { mutableStateOf(false) }
    // New shapes take the theme's colours in turn.
    val palette = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.error, scheme.inversePrimary).map { it.toArgb().toLong() and 0xFFFFFFFFL }
    var nextColour by rememberSaveable { mutableStateOf(0) }
    fun colour(): Long = palette[nextColour % palette.size].also { nextColour++ }
    // What a finger does on the stage: select, draw, edit points, place points, fill.
    val mode = rememberSaveable { mutableStateOf(StageMode.SELECT) }
    val pointSel = remember { mutableStateOf<PointSel?>(null) }
    var building by remember { mutableStateOf<Int?>(null) }
    var bucketColour by rememberSaveable { mutableStateOf(palette.first()) }
    var bucketGap by rememberSaveable { mutableStateOf(2f) }
    val filling by vm.filling.collectAsState()
    val penColour = palette[nextColour % palette.size]
    fun setMode(next: StageMode) {
        mode.value = next
        pointSel.value = null
        building = null
    }
    // Points edited of the selected shape: [change] given its points as they are now.
    fun editPoints(step: String, change: (EditPath) -> EditPath?) {
        val key = selected ?: return
        vm.commitStep(step) { d ->
            d.update(key) { n -> (n as? SvgShape)?.let { s -> change(s.editPath())?.let { s.withEditPath(it) } } ?: n }
        }
    }
    // A selection gone (deleted, undone) leaves the modes that work on it.
    LaunchedEffect(selected) {
        pointSel.value = null
        if (selected == null && mode.value == StageMode.POINTS) setMode(StageMode.SELECT)
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    var styling by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        SvgShapesSheet(
            onPick = { line, make ->
                picking = false
                val argb = colour()
                vm.add { d -> placed(d, line, argb, make) }
            },
            onDismiss = { picking = false },
        )
    }
    val styled = selected
    if (styling && styled != null && load == SvgEditorViewModel.Load.Ready) {
        SvgStyleSheet(doc = doc, key = styled, onChange = vm::commitStep, onDismiss = { styling = false }, others = selection.drop(1))
    }
    LaunchedEffect(selected) { if (selected == null) styling = false }
    // The sheet of what can be done to the selection: combine, line up, mirror, remake, place.
    var acting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selected) { if (selected == null) acting = false }
    val combineFailed = stringResource(R.string.svg_combine_failed)
    val styleCopied = stringResource(R.string.svg_style_copied)
    val lockedNote = stringResource(R.string.svg_locked_note)
    // The symmetry and the grid.
    var symmetrySheet by rememberSaveable { mutableStateOf(false) }
    val gridDivisions by vm.grid.collectAsState()
    val snapGridState = vm.snapToGrid.collectAsState()
    val symmetryState = remember { androidx.compose.runtime.derivedStateOf { docState.value.symmetry() } }
    val gridStepState = remember { androidx.compose.runtime.derivedStateOf { gridStep(gridDivisions, docState.value.width, docState.value.height) } }
    if (symmetrySheet && load == SvgEditorViewModel.Load.Ready) {
        SvgSymmetrySheet(
            current = symmetryState.value,
            centreX = doc.viewBox.centerX,
            centreY = doc.viewBox.centerY,
            grid = gridDivisions,
            snapToGrid = snapGridState.value,
            onSymmetry = { sym, keep -> vm.setSymmetry(sym, keep) },
            onGrid = vm::setGrid,
            onSnapToGrid = vm::setSnapToGrid,
            onDismiss = { symmetrySheet = false },
        )
    }
    fun act(a: SvgAct) {
        val keys = vm.selection
        if (keys.isEmpty()) return
        val m = renderer.measure
        when (a) {
            is SvgAct.Combine -> {
                val ok = vm.remake { d, k -> SvgBooleans.combine(d, k, a.op, renderer) }
                haptic.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                if (ok) acting = false else Toast.makeText(context, combineFailed, Toast.LENGTH_SHORT).show()
            }
            is SvgAct.Align -> vm.commit { it.align(keys, a.how, m, a.toPage) }
            is SvgAct.MirrorCopy -> vm.remake { d, k ->
                val (next, made) = d.mirrorCopy(k, a.horizontal, m)
                val b = d.boundsOf(k, m)
                if (made.isEmpty() || b == null) {
                    null
                } else {
                    // Beside the original, as if folded out of it.
                    val shift = if (a.horizontal) Affine.translate(b.width, 0.0) else Affine.translate(0.0, b.height)
                    next.transformAll(made, shift) to made
                }
            }
            is SvgAct.RepeatRow -> vm.remake { d, k ->
                val b = d.boundsOf(k, m) ?: return@remake null
                val dx = if (a.across) b.width + a.gap else 0.0
                val dy = if (a.across) 0.0 else b.height + a.gap
                d.repeatInRow(k, a.count, dx, dy)
            }
            is SvgAct.RepeatGrid -> vm.remake { d, k ->
                val b = d.boundsOf(k, m) ?: return@remake null
                d.repeatInGrid(k, a.columns, a.rows, b.width + a.gap, b.height + a.gap)
            }
            is SvgAct.RepeatAround -> vm.remake { d, k -> d.repeatAround(k, a.count, d.width / 2, d.height / 2) }
            SvgAct.Simplify -> {
                val tolerance = maxOf(doc.viewBox.width, doc.viewBox.height) * 0.004
                vm.commit { d -> keys.fold(d) { acc, k -> acc.reshape(k) { simplifyPath(it, tolerance) } } }
            }
            SvgAct.Smooth -> vm.commit { d -> keys.fold(d) { acc, k -> acc.reshape(k) { smoothPath(it) } } }
            SvgAct.Reverse -> vm.commit { d -> keys.fold(d) { acc, k -> acc.reshape(k) { reversePath(it) } } }
            SvgAct.BreakApart -> vm.remake { d, k ->
                var next = d
                val out = ArrayList<Int>()
                for (key in k) {
                    val apart = next.breakApart(key)
                    if (apart != null) {
                        next = apart.first
                        out += apart.second
                    } else {
                        out += key
                    }
                }
                if (next === d) null else next to out
            }
            SvgAct.JoinPaths -> {
                val ok = vm.remake { d, k -> d.joinPaths(k)?.let { (next, key) -> next to listOf(key) } }
                haptic.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            }
            SvgAct.Lock -> {
                vm.commit { d -> keys.fold(d) { acc, k -> acc.setLocked(k, true) } }
                vm.select(null)
                acting = false
                Toast.makeText(context, lockedNote, Toast.LENGTH_SHORT).show()
            }
            is SvgAct.Distribute -> vm.commit { it.distribute(keys, a.horizontal, m) }
            is SvgAct.Flip -> vm.commit { it.flip(keys, a.horizontal, m) }
            is SvgAct.Rotate -> vm.commit { d ->
                d.boundsOf(keys, m)?.let { b -> d.transformAll(keys, Affine.rotate(a.degrees, b.centerX, b.centerY)) } ?: d
            }
            SvgAct.Group -> vm.remake { d, k -> d.group(k)?.let { (next, g) -> next to listOf(g) } }
            SvgAct.Ungroup -> vm.remake { d, k ->
                var next = d
                val out = ArrayList<Int>()
                for (key in k) {
                    val apart = next.ungroup(key)
                    if (apart != null) {
                        next = apart.first
                        out += apart.second
                    } else {
                        out += key
                    }
                }
                if (next === d) null else next to out
            }
            SvgAct.SelectGroup -> vm.select(doc.parentOf(keys.first())?.key)
            SvgAct.ToPath -> vm.commit { d -> keys.fold(d) { acc, k -> acc.toPath(k) } }
            SvgAct.OutlineStroke -> vm.remake { d, k ->
                var next = d
                val out = ArrayList<Int>()
                for (key in k) {
                    val made = SvgBooleans.outlineStroke(next, key)
                    if (made != null) {
                        next = made.first
                        out += made.second
                    } else if (next.find(key) != null) {
                        out += key
                    }
                }
                if (next === d) null else next to out
            }
            SvgAct.TextToPath -> vm.remake { d, k ->
                var next = d
                val out = ArrayList<Int>()
                for (key in k) {
                    val made = SvgBooleans.textToPath(next, key, renderer)
                    if (made != null) {
                        next = made.first
                        out += made.second
                    } else {
                        out += key
                    }
                }
                if (next === d) null else next to out
            }
            SvgAct.CopyStyle -> {
                vm.copyStyle()
                Toast.makeText(context, styleCopied, Toast.LENGTH_SHORT).show()
            }
            SvgAct.PasteStyle -> vm.pasteStyle()
            is SvgAct.Place -> vm.commitStep("place") { d ->
                val b = d.boundsOf(keys, m) ?: return@commitStep d
                if (b.width <= 1e-9 || b.height <= 1e-9) return@commitStep d
                val t = Affine.translate(a.box.left, a.box.top) * Affine.scale(a.box.width / b.width, a.box.height / b.height) * Affine.translate(-b.left, -b.top)
                d.transformAll(keys, t)
            }
        }
    }
    if (acting && selection.isNotEmpty() && load == SvgEditorViewModel.Load.Ready) {
        SvgActionsSheet(
            doc = doc,
            selection = selection,
            renderer = renderer,
            canPaste = canPaste,
            onAct = ::act,
            onDismiss = { acting = false },
        )
    }
    if (askingText) {
        TextDialog(
            onDismiss = { askingText = false },
            onAdd = { text ->
                askingText = false
                vm.addText(text, colour())
            },
        )
    }

    var layers by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }
    // The tip shows until something is first selected.
    var tipped by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selected) { if (selected != null) tipped = true }
    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            delay(1800)
            savedFlash = false
        }
    }
    val saveFailed = stringResource(R.string.svg_save_failed)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
        if (uri != null) {
            vm.save(uri) { ok ->
                if (ok) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    savedFlash = true
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val save = { saver.launch("${name.ifBlank { "drawing" }}.svg") }

    BackHandler {
        if (dirty && load == SvgEditorViewModel.Load.Ready && !vm.keepDrawings) {
            leaving = true
        } else {
            // Kept under "Continue editing" on the home screen: nothing is lost.
            vm.keepNow()
            onBack()
        }
    }
    if (leaving) {
        LeaveDialog(
            onSave = {
                leaving = false
                save()
            },
            onLeave = {
                leaving = false
                onBack()
            },
            onDismiss = { leaving = false },
        )
    }
    if (touring) {
        // Over the whole screen, the header too.
        Dialog(
            onDismissRequest = {
                touring = false
                markTourSeen(context)
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            SvgTour(onDone = {
                touring = false
                markTourSeen(context)
            })
        }
    }
    if (layers && load == SvgEditorViewModel.Load.Ready) {
        SvgLayersSheet(
            doc = doc,
            selected = selected,
            onSelect = vm::select,
            onToggleHidden = { node -> vm.commit { it.setHidden(node.key, !node.style.hidden) } },
            onToggleLocked = { node -> vm.setLocked(node.key, !node.isLocked) },
            onMove = { key, below, above ->
                vm.commit { d ->
                    when {
                        below != null -> d.moveAbove(key, below)
                        above != null -> d.moveBelow(key, above)
                        else -> d
                    }
                }
            },
            onDismiss = { layers = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(scheme.surfaceContainer)) {
        Header(
            name = name.ifBlank { stringResource(R.string.svg_editor_title) },
            doc = doc,
            ready = load == SvgEditorViewModel.Load.Ready,
            canUndo = canUndo,
            canRedo = canRedo,
            dirty = dirty,
            onBack = { if (dirty && load == SvgEditorViewModel.Load.Ready && !vm.keepDrawings) {
            leaving = true
        } else {
            // Kept under "Continue editing" on the home screen: nothing is lost.
            vm.keepNow()
            onBack()
        } },
            onUndo = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                vm.undo()
            },
            onRedo = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                vm.redo()
            },
            onSave = save,
        )
        // What takes the toolbar's place: the trace's settings, or the card of what a finger
        // does now. Beside the stage on a wide window, under it otherwise: never over the drawing.
        val ready = load == SvgEditorViewModel.Load.Ready
        val panel: Panel? = when {
            !ready -> null
            tracing != null -> Panel.TRACE
            mode.value == StageMode.POINTS && (selected?.let { doc.find(it) } as? SvgShape) != null -> Panel.POINTS
            mode.value == StageMode.BUILD -> Panel.BUILD
            mode.value == StageMode.FILL -> Panel.FILL
            else -> null
        }
        // The last panel stays drawn while it slides away.
        val lastPanel = remember { arrayOfNulls<Panel>(1) }
        if (panel != null) lastPanel[0] = panel
        val window = LocalWindowLayout.current
        val beside = window.twoPanes
        val panelContent: @Composable (Modifier) -> Unit = { cardModifier ->
            when (panel ?: lastPanel[0]) {
                Panel.TRACE -> {
                    // The last trace's settings stay while the card slides away.
                    val shown = remember { arrayOfNulls<SvgEditorViewModel.Tracing>(1) }
                    tracing?.let { shown[0] = it }
                    shown[0]?.let { t ->
                        SvgTraceCard(
                            tracing = t,
                            onChange = vm::retrace,
                            onDone = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.finishTracing()
                            },
                            modifier = cardModifier,
                        )
                    }
                }
                Panel.POINTS -> {
                    val shape = selected?.let { doc.find(it) } as? SvgShape
                    if (shape != null) {
                        SvgPointsCard(
                            path = shape.editPath(),
                            sel = pointSel.value,
                            onBend = { step, sub, piece, bend -> editPoints("bend-$step-$sub-$piece") { it.setBend(sub, piece, bend) } },
                            onRemove = { sub, point ->
                                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                editPoints("remove") { it.removePoint(sub, point) }
                                pointSel.value = null
                            },
                            onClose = { sub, closed -> editPoints("close") { it.setClosed(sub, closed) } },
                            onDone = { setMode(StageMode.SELECT) },
                            modifier = cardModifier,
                        )
                    }
                }
                Panel.BUILD -> {
                    SvgBuildCard(
                        onDone = {
                            val made = building
                            setMode(StageMode.SELECT)
                            made?.let(vm::select)
                        },
                        modifier = cardModifier,
                    )
                }
                Panel.FILL -> {
                    SvgFillCard(
                        colours = palette + listOf(0xFF000000L, 0xFFFFFFFFL, 0xFFE53935L, 0xFFFDD835L, 0xFF43A047L, 0xFF1E88E5L, 0xFF8E24AAL),
                        colour = bucketColour,
                        onColour = { bucketColour = it },
                        gap = bucketGap,
                        onGap = { bucketGap = it },
                        busy = filling,
                        onDone = { setMode(StageMode.SELECT) },
                        modifier = cardModifier,
                    )
                }
                null -> Unit
            }
        }
        val stageArea: @Composable (Modifier) -> Unit = { areaModifier ->
            Box(
                modifier = areaModifier
                    .clip(RoundedCornerShape(36.dp))
                    .background(scheme.surfaceContainerLow),
            ) {
                AnimatedContent(
                    targetState = load,
                    contentKey = { it::class },
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith
                            fadeOut(motion.fastEffectsSpec())
                    },
                    label = "load",
                ) { state ->
                    when (state) {
                        SvgEditorViewModel.Load.Loading -> Loading()
                        is SvgEditorViewModel.Load.Failed -> Failed(state.reason, onPickAnother)
                        else -> Box(modifier = Modifier.fillMaxSize()) {
                            // While a newer trace is made: the drawing dims and a loading badge says so.
                            TracingOverlay(visible = tracing?.working == true, modifier = Modifier.matchParentSize().zIndex(1f))
                            SvgStage(
                                doc = docState,
                                selected = selectedState,
                                also = alsoState,
                                multi = multiState,
                                onToggle = { if (tracing == null) vm.toggle(it) },
                                renderer = renderer,
                                zoom = zoom,
                                // While tracing, the shapes are not yet to be picked.
                                onSelect = { if (tracing == null) vm.select(it) },
                                onBegin = vm::checkpoint,
                                onChange = vm::live,
                                onEnd = {},
                                symmetry = symmetryState,
                                gridStep = gridStepState,
                                snapToGrid = snapGridState,
                                mode = mode,
                                points = pointSel,
                                onPointSelect = { pointSel.value = it },
                                onTap = { p, reach ->
                                    when (mode.value) {
                                        StageMode.FILL -> vm.fillAt(p.x.toDouble(), p.y.toDouble(), bucketColour, bucketGap * minOf(doc.width, doc.height) / 400.0) { ok ->
                                            haptic.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                                        }
                                        StageMode.BUILD -> {
                                            val inv = doc.viewTransform.inverse()
                                            val x = inv.mapX(p.x.toDouble(), p.y.toDouble())
                                            val y = inv.mapY(p.x.toDouble(), p.y.toDouble())
                                            val key = building?.takeIf { doc.find(it) != null }
                                            if (key == null) {
                                                // The first point: a line of no length, its end following the next tap.
                                                val argb = colour()
                                                val width = (minOf(doc.viewBox.width, doc.viewBox.height) * 0.01).coerceAtLeast(1.0)
                                                vm.add {
                                                    SvgShape(
                                                        SvgKeys.next(), null, Affine.IDENTITY,
                                                        SvgStyle(fill = SvgPaint.NONE, stroke = SvgPaint.color(argb), strokeWidth = width, other = mapOf("stroke-linecap" to "round", "stroke-linejoin" to "round")),
                                                        emptyMap(), "path",
                                                        linkedMapOf("d" to "M${svgNumber(x)} ${svgNumber(y)}L${svgNumber(x)} ${svgNumber(y)}"),
                                                    )
                                                }
                                                building = vm.selected.value
                                            } else {
                                                val shape = doc.find(key) as SvgShape
                                                val path = shape.editPath()
                                                val sub = path.subs.lastOrNull()
                                                val first = sub?.points?.firstOrNull()
                                                val w = doc.worldTransform(key)
                                                val fx = first?.let { w.mapX(it.x, it.y) }
                                                val fy = first?.let { w.mapY(it.x, it.y) }
                                                val near = fx != null && fy != null && kotlin.math.hypot(fx - p.x, fy - p.y) <= reach
                                                when {
                                                    sub != null && near && sub.points.size >= 3 -> {
                                                        // Back on the first point: the shape closes, and is done.
                                                        vm.commit { d -> d.update(key) { n -> (n as SvgShape).withEditPath(path.setClosed(path.subs.lastIndex, true)) } }
                                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                                        setMode(StageMode.SELECT)
                                                        vm.select(key)
                                                    }
                                                    sub != null && sub.points.size == 2 && sub.points[0] == sub.points[1] ->
                                                        vm.commit { d -> d.update(key) { n -> (n as SvgShape).withEditPath(path.movePoint(path.subs.lastIndex, 1, x, y)) } }
                                                    else -> vm.commit { d -> d.update(key) { n -> (n as SvgShape).withEditPath(path.append(x, y)) } }
                                                }
                                            }
                                        }
                                        else -> Unit
                                    }
                                },
                                penColor = penColour,
                                penWidth = (minOf(doc.width, doc.height) * 0.012).toFloat().coerceAtLeast(1f),
                                onStroke = { pts ->
                                    vm.addStroke(pts, colour(), (minOf(doc.width, doc.height) * 0.012).coerceAtLeast(1.0))
                                    vm.select(null)
                                },
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp, start = 16.dp, end = 16.dp),
                            ) {
                                SelectionPill(doc = doc, selected = selected, renderer = renderer, selection = selection)
                                AnimatedVisibility(
                                    visible = !tipped && selected == null && doc.nodes.isNotEmpty() && tracing == null && !touring,
                                    enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { -it / 2 },
                                    exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 2 },
                                ) {
                                    TipPill()
                                }
                            }
                            ZoomPill(zoom, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
                            SavedFlash(visible = savedFlash, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
        // The toolbar under the stage (never over the drawing), while no panel takes its place.
        val toolbar: @Composable () -> Unit = {
            BottomSlot(visible = ready && panel == null && (mode.value == StageMode.SELECT || mode.value == StageMode.PEN)) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Toolbar(
                        doc = doc,
                        selected = selected.takeIf { mode.value != StageMode.PEN },
                        count = selection.size,
                        multi = multi,
                        pen = mode.value == StageMode.PEN,
                        onLayers = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            layers = true
                        },
                        onAction = { action ->
                            haptic.performHapticFeedback(if (action == Action.DELETE) HapticFeedbackType.Reject else HapticFeedbackType.SegmentTick)
                            when (action) {
                                Action.ADD_SHAPE -> {
                                    setMode(StageMode.SELECT)
                                    picking = true
                                }
                                Action.ADD_TEXT -> {
                                    setMode(StageMode.SELECT)
                                    askingText = true
                                }
                                Action.PEN -> {
                                    setMode(if (mode.value == StageMode.PEN) StageMode.SELECT else StageMode.PEN)
                                    if (mode.value == StageMode.PEN) vm.select(null)
                                }
                                Action.BUILD -> {
                                    setMode(StageMode.BUILD)
                                    vm.select(null)
                                }
                                Action.FILL -> {
                                    setMode(StageMode.FILL)
                                    vm.select(null)
                                }
                                Action.POINTS -> setMode(StageMode.POINTS)
                                Action.HELP -> touring = true
                                Action.SYMMETRY -> symmetrySheet = true
                                Action.STYLE -> styling = true
                                Action.MORE -> acting = true
                                Action.MULTI -> {
                                    setMode(StageMode.SELECT)
                                    vm.setMulti(!multi)
                                }
                                Action.SELECT_ALL -> {
                                    setMode(StageMode.SELECT)
                                    vm.selectAll(doc.nodes.filter { !it.style.hidden && !it.isLocked }.map { it.key }.reversed())
                                }
                                else -> {
                                    val key = selected ?: return@Toolbar
                                    when (action) {
                                        Action.DUPLICATE -> {
                                            val nudge = (minOf(doc.width, doc.height) * 0.03).coerceAtLeast(4.0)
                                            val copies = ArrayList<Int>()
                                            vm.commit { d ->
                                                d.outermost(selection).fold(d) { acc, k -> acc.duplicate(k, nudge, nudge)?.also { copies += it.second }?.first ?: acc }
                                            }
                                            // The copy of the one picked first stays the one picked first.
                                            vm.selectAll(copies.reversed())
                                        }
                                        Action.DELETE -> {
                                            val gone = selection
                                            vm.commit { d -> d.outermost(gone).fold(d) { acc, k -> acc.remove(k) } }
                                            vm.select(null)
                                        }
                                        Action.FORWARD -> vm.commit { it.reorder(key, SvgOrder.UP) }
                                        Action.BACKWARD -> vm.commit { it.reorder(key, SvgOrder.DOWN) }
                                        Action.GROUP -> vm.select(doc.parentOf(key)?.key)
                                        else -> vm.select(null)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
        if (beside) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).fillMaxHeight()) {
                    stageArea(Modifier.weight(1f).fillMaxWidth())
                    toolbar()
                }
                PanelSlot(visible = panel != null, beside = true) {
                    // Never so wide that the drawing is left with a strip (a phone on its side).
                    Box(modifier = Modifier.width(minOf(PANEL_WIDTH, window.width * 0.42f)).fillMaxHeight()) {
                        panelContent(Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                stageArea(Modifier.weight(1f).fillMaxWidth())
                toolbar()
                PanelSlot(visible = panel != null, beside = false) {
                    // At most a little under half the window: the drawing keeps the rest.
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = window.height * 0.46f), contentAlignment = Alignment.TopCenter) {
                        panelContent(Modifier.widthIn(max = PANEL_MAX_WIDTH))
                    }
                }
            }
        }
    }
}

/** What can take the toolbar's place beside or under the stage. */
private enum class Panel { TRACE, POINTS, BUILD, FILL }

/** How wide the panel is beside the stage. */
private val PANEL_WIDTH = 400.dp

/** How wide the panel under the stage grows at most. */
private val PANEL_MAX_WIDTH = 640.dp

/**
 * A panel sliding in beside the stage ([beside]) or under it, and out again.
 * A function of its own, so that the plain AnimatedVisibility is the one used.
 */
@Composable
private fun PanelSlot(visible: Boolean, beside: Boolean, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = if (beside) {
            expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
        } else {
            expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
        },
        exit = if (beside) {
            shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        } else {
            shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        },
    ) {
        content()
    }
}

/**
 * What slides up from the bottom of the stage and back down (the trace card,
 * the toolbar). A function of its own: inside the screen's column the
 * column's own AnimatedVisibility would be picked.
 */
@Composable
private fun BottomSlot(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(motion.defaultSpatialSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
private fun Header(
    name: String,
    doc: SvgDocument,
    ready: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    dirty: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
    ) {
        FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ready) {
                val count = doc.nodeCount
                Text(
                    stringResource(R.string.svg_size, doc.width.roundToInt(), doc.height.roundToInt()) + "  -  " +
                        pluralStringResource(R.plurals.svg_elements, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (ready) {
            // Undo and redo as one connected pair: round outer ends, a narrow gap.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FilledTonalIconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                    modifier = Modifier.size(width = 48.dp, height = 44.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo))
                }
                FilledTonalIconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp),
                    modifier = Modifier.size(width = 48.dp, height = 44.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                shapes = ButtonDefaults.shapes(),
                colors = if (dirty) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.height(44.dp),
            ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.svg_save), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/**
 * What is selected, as a pill over the stage: its badge in the shape of its
 * kind, its name and its size on the drawing. A new selection slides in from
 * above while the old one pops away.
 */
@Composable
private fun SelectionPill(doc: SvgDocument, selected: Int?, renderer: SvgRenderer, selection: List<Int> = listOfNotNull(selected)) {
    val count = selection.size
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val node = selected?.let { doc.find(it) }
    AnimatedContent(
        targetState = node?.key,
        transitionSpec = {
            (slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.7f)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.7f))
        },
        label = "selection",
    ) { key ->
        val shown = key?.let { doc.find(it) } ?: return@AnimatedContent
        val kind = kindOf(shown)
        val box = doc.worldBounds(shown.key, renderer.measure)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(scheme.inverseSurface.copy(alpha = 0.92f))
                .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        ) {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = kind.shape,
                morphed = true,
                color = scheme.inversePrimary,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(kind.icon, contentDescription = null, tint = scheme.inverseSurface, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    if (count > 1) pluralStringResource(R.plurals.svg_selected_count, count, count) else shown.label ?: stringResource(kind.label),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = scheme.inverseOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                val shownBox = if (count > 1) doc.boundsOf(selection, renderer.measure) ?: box else box
                if (shownBox != null) {
                    Text(
                        stringResource(R.string.svg_size, shownBox.width.roundToInt(), shownBox.height.roundToInt()),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = scheme.inverseOnSurface.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TipPill() {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.secondaryContainer)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        MorphingContainer(from = MaterialShapes.SoftBurst, to = MaterialShapes.SoftBurst, morphed = false, color = scheme.secondary, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = scheme.onSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(stringResource(R.string.svg_tap_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSecondaryContainer)
    }
}

/** "Saved", popping out of a sunny badge in the middle of the stage. */
@Composable
private fun SavedFlash(visible: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motion.fastSpatialSpec(), initialScale = 0.3f) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.defaultSpatialSpec(), targetScale = 0.6f) + fadeOut(motion.defaultEffectsSpec()),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var bloom by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { bloom = true }
            MorphingContainer(from = MaterialShapes.Circle, to = MaterialShapes.VerySunny, morphed = bloom, color = scheme.primary, modifier = Modifier.size(96.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(44.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(R.string.svg_saved),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = scheme.onPrimaryContainer,
                modifier = Modifier.clip(CircleShape).background(scheme.primaryContainer).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/** What the toolbar does: adds something, or acts on the selection. */
private enum class Action { ADD_SHAPE, ADD_TEXT, PEN, BUILD, FILL, SYMMETRY, HELP, STYLE, POINTS, MORE, MULTI, SELECT_ALL, DUPLICATE, DELETE, FORWARD, BACKWARD, GROUP, DESELECT }

private class ToolbarItem(val action: Action, val icon: ImageVector, val label: Int, val enabled: Boolean = true)

/**
 * The floating toolbar, always there: the layers in its FAB; beside it what
 * can be added (a rectangle, a circle, a star, a text, and the tour), or,
 * while something is selected, what can be done to it. The two sets swap
 * with a springy slide.
 */
@Composable
private fun Toolbar(
    doc: SvgDocument,
    selected: Int?,
    /** How many are selected. */
    count: Int,
    /** Whether a tap adds to the selection. */
    multi: Boolean,
    pen: Boolean,
    onLayers: () -> Unit,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    // The toolbar slides once to show it scrolls; after that it stays still.
    var nudged by rememberSaveable { mutableStateOf(false) }
    val adding = listOf(
        ToolbarItem(Action.ADD_SHAPE, Icons.Rounded.Category, R.string.svg_add_shape),
        ToolbarItem(Action.ADD_TEXT, Icons.Rounded.TextFields, R.string.svg_add_text),
        ToolbarItem(Action.PEN, Icons.Rounded.Draw, R.string.svg_pen),
        ToolbarItem(Action.BUILD, Icons.Rounded.Timeline, R.string.svg_build),
        ToolbarItem(Action.FILL, Icons.Rounded.FormatColorFill, R.string.svg_bucket),
        ToolbarItem(Action.SYMMETRY, Icons.Rounded.Flare, R.string.svg_symmetry),
        ToolbarItem(Action.MULTI, Icons.Rounded.LibraryAddCheck, R.string.svg_multi),
        ToolbarItem(Action.SELECT_ALL, Icons.Rounded.DoneAll, R.string.svg_select_all, doc.nodes.isNotEmpty()),
        ToolbarItem(Action.HELP, Icons.AutoMirrored.Rounded.HelpOutline, R.string.svg_help),
    )
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onLayers) {
                Icon(Icons.Rounded.Layers, contentDescription = stringResource(R.string.svg_layers))
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = selected,
            contentKey = { it != null },
            transitionSpec = {
                (slideInVertically(motion.fastSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f)) togetherWith
                    (slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()))
            },
            label = "toolbarSet",
        ) { key ->
            val items = if (key == null || doc.find(key) == null) {
                adding
            } else if (count > 1) {
                // Several: what is done to them all (combine and line up in the sheet).
                listOf(
                    ToolbarItem(Action.MORE, Icons.Rounded.AutoFixHigh, R.string.svg_actions),
                    ToolbarItem(Action.STYLE, Icons.Rounded.Palette, R.string.svg_style),
                    ToolbarItem(Action.MULTI, Icons.Rounded.LibraryAddCheck, R.string.svg_multi),
                    ToolbarItem(Action.DUPLICATE, Icons.Rounded.ContentCopy, R.string.svg_duplicate),
                    ToolbarItem(Action.DESELECT, Icons.Rounded.Close, R.string.svg_deselect),
                    ToolbarItem(Action.DELETE, Icons.Rounded.DeleteOutline, R.string.svg_delete),
                )
            } else {
                listOfNotNull(
                    ToolbarItem(Action.STYLE, Icons.Rounded.Palette, R.string.svg_style),
                    ToolbarItem(Action.MORE, Icons.Rounded.AutoFixHigh, R.string.svg_actions),
                    ToolbarItem(Action.MULTI, Icons.Rounded.LibraryAddCheck, R.string.svg_multi),
                    // Its points, for a shape that has an outline (not a connector: it runs by itself).
                    ToolbarItem(Action.POINTS, Icons.Rounded.ShowChart, R.string.svg_points)
                        .takeIf { (doc.find(key) as? SvgShape)?.let { it.editable && !it.isConnector } == true },
                    ToolbarItem(Action.DUPLICATE, Icons.Rounded.ContentCopy, R.string.svg_duplicate),
                    ToolbarItem(Action.FORWARD, Icons.Rounded.FlipToFront, R.string.svg_forward, doc.canReorder(key, SvgOrder.UP)),
                    ToolbarItem(Action.BACKWARD, Icons.Rounded.FlipToBack, R.string.svg_backward, doc.canReorder(key, SvgOrder.DOWN)),
                    ToolbarItem(Action.GROUP, Icons.Rounded.SelectAll, R.string.svg_select_group).takeIf { doc.parentOf(key) != null },
                    ToolbarItem(Action.DELETE, Icons.Rounded.DeleteOutline, R.string.svg_delete),
                )
            }
            // Scrolls sideways where the phone is too narrow for them all, and shows that it does.
            HintedScrollRow(
                nudge = !nudged,
                onNudged = { nudged = true },
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                items.forEach { item ->
                    // The pen and picking several, while on, are filled: what a finger does on the stage has changed.
                    val on = (item.action == Action.PEN && pen) || (item.action == Action.MULTI && multi)
                    IconButton(
                        onClick = { onAction(item.action) },
                        enabled = item.enabled,
                        shapes = IconButtonDefaults.shapes(),
                        colors = if (on) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors(),
                    ) {
                        Icon(item.icon, contentDescription = stringResource(item.label))
                    }
                }
            }
        }
    }
}

/** Asks for the words of a new text. */
@Composable
private fun TextDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.TextFields, contentDescription = null) },
        title = { Text(stringResource(R.string.svg_add_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.svg_text_words)) },
                shape = RoundedCornerShape(20.dp),
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(text.trim()) }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.svg_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Loading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(96.dp))
        Spacer(modifier = Modifier.height(18.dp))
        Text(stringResource(R.string.svg_loading), style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Failed(reason: String, onPickAnother: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var wobble by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1400)
            wobble = !wobble
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        MorphingContainer(from = MaterialShapes.Cookie7Sided, to = MaterialShapes.Boom, morphed = wobble, color = scheme.errorContainer, modifier = Modifier.size(120.dp)) {
            Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(52.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.svg_failed_title), style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.svg_failed_body, reason),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onPickAnother, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) {
            Text(stringResource(R.string.svg_failed_pick), style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

@Composable
private fun LeaveDialog(onSave: () -> Unit, onLeave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
        title = { Text(stringResource(R.string.svg_leave_title)) },
        text = { Text(stringResource(R.string.svg_leave_body)) },
        confirmButton = {
            Button(onClick = onSave, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.svg_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.svg_keep_editing)) }
                TextButton(onClick = onLeave) { Text(stringResource(R.string.svg_leave), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
