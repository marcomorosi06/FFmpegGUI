@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.svgedit

import androidx.compose.foundation.layout.height
import kotlin.math.roundToInt
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Cyclone
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.ScatterPlot
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.AlignVerticalBottom
import androidx.compose.material.icons.rounded.AlignVerticalCenter
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.HorizontalDistribute
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VerticalDistribute
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.svg.subpaths
import com.cuscus.ffmpeggui.core.vector.svg.Box
import com.cuscus.ffmpeggui.core.vector.svg.PaintKind
import com.cuscus.ffmpeggui.core.vector.svg.SvgAlign
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgGroup
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.SvgText
import com.cuscus.ffmpeggui.core.vector.svg.boundsOf
import com.cuscus.ffmpeggui.core.vector.svg.find
import com.cuscus.ffmpeggui.core.vector.svg.parentOf
import com.cuscus.ffmpeggui.core.vector.svg.resolvedStyle
import com.cuscus.ffmpeggui.core.vector.svg.svgNumber
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.springPress

/*
 * The SVG editor's sheet of what can be done to the selection, in sections:
 * combine (the booleans, each tile drawing what it makes of a square and a
 * circle), line up and space out, mirror and turn, what it is made of (a
 * group, a path, a stroke as a shape, a text as letters), copy and paste a
 * look, and its exact place and size.
 */

/** One thing the sheet asks for. */
internal sealed interface SvgAct {
    data class Combine(val op: SvgBoolean) : SvgAct
    /** Lined up on the box around the selection, or with [toPage] on the page. */
    data class Align(val how: SvgAlign, val toPage: Boolean = false) : SvgAct
    data class Distribute(val horizontal: Boolean) : SvgAct
    data class Flip(val horizontal: Boolean) : SvgAct
    data class Rotate(val degrees: Double) : SvgAct
    data object Group : SvgAct
    data object Ungroup : SvgAct
    data object SelectGroup : SvgAct
    data object ToPath : SvgAct
    data object OutlineStroke : SvgAct
    data object TextToPath : SvgAct
    data object CopyStyle : SvgAct
    data object PasteStyle : SvgAct
    /** The selection's box on the drawing made [box] (pixels): moved and sized. */
    data class Place(val box: Box) : SvgAct
    /** A mirrored copy beside it, flipped across ([horizontal]) or down. */
    data class MirrorCopy(val horizontal: Boolean) : SvgAct
    /** [count] in a row, [gap] pixels between their boxes, across ([across]) or down. */
    data class RepeatRow(val count: Int, val gap: Double, val across: Boolean) : SvgAct
    /** [columns] x [rows], [gap] pixels between their boxes. */
    data class RepeatGrid(val columns: Int, val rows: Int, val gap: Double) : SvgAct
    /** [count] turned evenly about the page's centre. */
    data class RepeatAround(val count: Int) : SvgAct
    data object Simplify : SvgAct
    data object Smooth : SvgAct
    data object Reverse : SvgAct
    data object BreakApart : SvgAct
    data object JoinPaths : SvgAct
    data object Lock : SvgAct
}

/** Each boolean: its name, what it does, and the colours of its tile. */
private class BooleanInfo(val op: SvgBoolean, val label: Int, val hint: Int)

private val BOOLEANS = listOf(
    BooleanInfo(SvgBoolean.UNION, R.string.svg_bool_union, R.string.svg_bool_union_hint),
    BooleanInfo(SvgBoolean.SUBTRACT, R.string.svg_bool_subtract, R.string.svg_bool_subtract_hint),
    BooleanInfo(SvgBoolean.INTERSECT, R.string.svg_bool_intersect, R.string.svg_bool_intersect_hint),
    BooleanInfo(SvgBoolean.EXCLUDE, R.string.svg_bool_exclude, R.string.svg_bool_exclude_hint),
    BooleanInfo(SvgBoolean.DIVIDE, R.string.svg_bool_divide, R.string.svg_bool_divide_hint),
)

@Composable
internal fun SvgActionsSheet(
    doc: SvgDocument,
    selection: List<Int>,
    renderer: SvgRenderer,
    canPaste: Boolean,
    onAct: (SvgAct) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scheme = MaterialTheme.colorScheme
    val nodes = selection.mapNotNull { doc.find(it) }
    val primary = nodes.firstOrNull() ?: return
    val many = nodes.size > 1
    PeekThroughSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingContainer(
                    from = MaterialShapes.Cookie9Sided,
                    to = MaterialShapes.Cookie9Sided,
                    morphed = false,
                    color = scheme.primary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.svg_actions), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(
                        pluralStringResource(R.plurals.svg_selected_count, nodes.size, nodes.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            // Combine: the hero of the sheet, when several are selected.
            Section(stringResource(R.string.svg_section_combine)) {
                if (many) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 5,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BOOLEANS.forEach { info -> BooleanTile(info) { onAct(SvgAct.Combine(info.op)) } }
                    }
                    PanelHint(stringResource(R.string.svg_combine_hint))
                } else {
                    PanelHint(stringResource(R.string.svg_combine_need_more))
                }
            }

            Section(stringResource(R.string.svg_section_align)) {
                // With several: on the box around them, or on the page.
                var toPage by rememberSaveable { mutableStateOf(false) }
                if (many) {
                    androidx.compose.material3.FilterChip(
                        selected = toPage,
                        onClick = { toPage = !toPage },
                        label = { Text(stringResource(R.string.svg_align_to_page)) },
                        leadingIcon = { Icon(Icons.Rounded.CropFree, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                ActionRow {
                    ActionButton(Icons.Rounded.AlignHorizontalLeft, R.string.svg_align_left) { onAct(SvgAct.Align(SvgAlign.LEFT, toPage)) }
                    ActionButton(Icons.Rounded.AlignHorizontalCenter, R.string.svg_align_center) { onAct(SvgAct.Align(SvgAlign.CENTER, toPage)) }
                    ActionButton(Icons.Rounded.AlignHorizontalRight, R.string.svg_align_right) { onAct(SvgAct.Align(SvgAlign.RIGHT, toPage)) }
                    ActionButton(Icons.Rounded.AlignVerticalTop, R.string.svg_align_top) { onAct(SvgAct.Align(SvgAlign.TOP, toPage)) }
                    ActionButton(Icons.Rounded.AlignVerticalCenter, R.string.svg_align_middle) { onAct(SvgAct.Align(SvgAlign.MIDDLE, toPage)) }
                    ActionButton(Icons.Rounded.AlignVerticalBottom, R.string.svg_align_bottom) { onAct(SvgAct.Align(SvgAlign.BOTTOM, toPage)) }
                    ActionButton(Icons.Rounded.HorizontalDistribute, R.string.svg_distribute_across, enabled = nodes.size >= 3) { onAct(SvgAct.Distribute(true)) }
                    ActionButton(Icons.Rounded.VerticalDistribute, R.string.svg_distribute_down, enabled = nodes.size >= 3) { onAct(SvgAct.Distribute(false)) }
                }
                PanelHint(stringResource(if (many) R.string.svg_align_hint_many else R.string.svg_align_hint_one))
            }

            Section(stringResource(R.string.svg_section_turn)) {
                ActionRow {
                    ActionButton(Icons.Rounded.Flip, R.string.svg_flip_across) { onAct(SvgAct.Flip(true)) }
                    ActionButton(Icons.Rounded.Flip, R.string.svg_flip_down, turned = true) { onAct(SvgAct.Flip(false)) }
                    ActionButton(Icons.Rounded.Rotate90DegreesCcw, R.string.svg_rotate_left) { onAct(SvgAct.Rotate(-90.0)) }
                    ActionButton(Icons.Rounded.Rotate90DegreesCw, R.string.svg_rotate_right) { onAct(SvgAct.Rotate(90.0)) }
                    ActionButton(Icons.Rounded.Flip, R.string.svg_mirror_copy_across) { onAct(SvgAct.MirrorCopy(true)) }
                    ActionButton(Icons.Rounded.Flip, R.string.svg_mirror_copy_down, turned = true) { onAct(SvgAct.MirrorCopy(false)) }
                }
            }

            Section(stringResource(R.string.svg_section_repeat)) {
                RepeatPanel(onAct)
            }

            Section(stringResource(R.string.svg_section_make)) {
                val shapes = nodes.filterIsInstance<SvgShape>()
                val stroked = shapes.any { doc.resolvedStyle(it.key).let { s -> s.stroke.kind != PaintKind.NONE && s.strokeWidth > 0 } }
                ActionRow {
                    ActionButton(Icons.Rounded.Workspaces, R.string.svg_group, enabled = many) { onAct(SvgAct.Group) }
                    ActionButton(Icons.Rounded.LayersClear, R.string.svg_ungroup, enabled = nodes.any { it is SvgGroup }) { onAct(SvgAct.Ungroup) }
                    ActionButton(Icons.Rounded.SelectAll, R.string.svg_select_group, enabled = !many && doc.parentOf(primary.key) != null) { onAct(SvgAct.SelectGroup) }
                    ActionButton(Icons.Rounded.Polyline, R.string.svg_to_path, enabled = shapes.any { it.tag != "path" }) { onAct(SvgAct.ToPath) }
                    ActionButton(Icons.Rounded.LineWeight, R.string.svg_outline_stroke, enabled = stroked) { onAct(SvgAct.OutlineStroke) }
                    ActionButton(Icons.Rounded.FontDownload, R.string.svg_text_to_path, enabled = nodes.any { it is SvgText }) { onAct(SvgAct.TextToPath) }
                }
            }

            Section(stringResource(R.string.svg_section_path)) {
                val shapes = nodes.filterIsInstance<SvgShape>()
                val pieces = shapes.any { s -> subpaths(s.segments()).size > 1 }
                ActionRow {
                    ActionButton(Icons.Rounded.Compress, R.string.svg_simplify, enabled = shapes.isNotEmpty()) { onAct(SvgAct.Simplify) }
                    ActionButton(Icons.Rounded.Gesture, R.string.svg_smooth, enabled = shapes.isNotEmpty()) { onAct(SvgAct.Smooth) }
                    ActionButton(Icons.Rounded.SwapHoriz, R.string.svg_reverse, enabled = shapes.isNotEmpty()) { onAct(SvgAct.Reverse) }
                    ActionButton(Icons.Rounded.ScatterPlot, R.string.svg_break_apart, enabled = pieces) { onAct(SvgAct.BreakApart) }
                    ActionButton(Icons.Rounded.Merge, R.string.svg_join_paths, enabled = shapes.size >= 2) { onAct(SvgAct.JoinPaths) }
                }
                PanelHint(stringResource(R.string.svg_path_hint))
            }

            Section(stringResource(R.string.svg_section_lock)) {
                ActionRow {
                    ActionButton(Icons.Rounded.Lock, R.string.svg_lock) { onAct(SvgAct.Lock) }
                }
                PanelHint(stringResource(R.string.svg_lock_hint))
            }

            Section(stringResource(R.string.svg_section_style)) {
                ActionRow {
                    ActionButton(Icons.Rounded.FormatPaint, R.string.svg_copy_style, enabled = !many) { onAct(SvgAct.CopyStyle) }
                    ActionButton(Icons.Rounded.ContentPaste, R.string.svg_paste_style, enabled = canPaste) { onAct(SvgAct.PasteStyle) }
                }
            }

            doc.boundsOf(selection, renderer.measure)?.let { box ->
                Section(stringResource(R.string.svg_section_place)) {
                    PlaceFields(box, selection) { onAct(SvgAct.Place(it)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PanelCard {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

/** A round tonal button with its name under it; [turned] shows the icon a quarter turned (a flip down). */
@Composable
private fun ActionButton(icon: ImageVector, label: Int, enabled: Boolean = true, turned: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            },
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = if (turned) 90f else 0f })
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One boolean as a big tile: a square and a circle over each other, what the
 * boolean keeps of them filled; pressed, the tile squishes and its frame
 * rounds into a cookie.
 */
@Composable
private fun BooleanTile(info: BooleanInfo, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(if (pressed) scheme.primaryContainer else scheme.surfaceContainerHighest, MaterialTheme.motionScheme.fastEffectsSpec(), label = "boolTile")
    val lift by animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "boolLift")
    val fill = scheme.primary.toArgb()
    val second = scheme.tertiary.toArgb()
    val third = scheme.secondary.toArgb()
    val line = scheme.outline.toArgb()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .springPress(interaction, pressedScale = 0.88f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(vertical = 4.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Square,
            to = MaterialShapes.Cookie12Sided,
            morphed = pressed,
            color = container,
            modifier = Modifier.size(84.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp).graphicsLayer { rotationZ = lift * -8f }) {
                val w = size.width
                val h = size.height
                val square = Path().apply { addRoundRect(RectF(0f, 0f, w * 0.62f, h * 0.62f), w * 0.1f, w * 0.1f, Path.Direction.CW) }
                val circle = Path().apply { addCircle(w * 0.64f, h * 0.64f, w * 0.34f, Path.Direction.CW) }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    fun fillWith(p: Path, argb: Int) {
                        paint.style = Paint.Style.FILL
                        paint.color = argb
                        n.drawPath(p, paint)
                    }
                    fun combined(a: Path, b: Path, o: Path.Op) = Path(a).apply { op(b, o) }
                    when (info.op) {
                        SvgBoolean.UNION -> fillWith(combined(square, circle, Path.Op.UNION), fill)
                        SvgBoolean.SUBTRACT -> fillWith(combined(square, circle, Path.Op.DIFFERENCE), fill)
                        SvgBoolean.INTERSECT -> fillWith(combined(square, circle, Path.Op.INTERSECT), fill)
                        SvgBoolean.EXCLUDE -> fillWith(combined(square, circle, Path.Op.XOR), fill)
                        SvgBoolean.DIVIDE -> {
                            fillWith(combined(square, circle, Path.Op.DIFFERENCE), fill)
                            fillWith(combined(circle, square, Path.Op.DIFFERENCE), second)
                            fillWith(combined(square, circle, Path.Op.INTERSECT), third)
                        }
                    }
                    // The two shapes' outlines, thin, over what is kept.
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5.dp.toPx()
                    paint.color = line
                    n.drawPath(square, paint)
                    n.drawPath(circle, paint)
                }
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            stringResource(info.label),
            style = MaterialTheme.typography.labelLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(info.hint),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The selection's place and size on the drawing, typed: X, Y, width and
 * height in pixels; with the proportions linked, a width brings its height.
 */
@Composable
private fun PlaceFields(box: Box, selection: List<Int>, onPlace: (Box) -> Unit) {
    var linked by rememberSaveable { mutableStateOf(true) }
    // What is typed, kept while it is not a number yet; new when the selection changes.
    var x by remember(selection) { mutableStateOf(svgNumber(box.left)) }
    var y by remember(selection) { mutableStateOf(svgNumber(box.top)) }
    var w by remember(selection) { mutableStateOf(svgNumber(box.width)) }
    var h by remember(selection) { mutableStateOf(svgNumber(box.height)) }
    fun place() {
        val nx = x.replace(',', '.').toDoubleOrNull() ?: return
        val ny = y.replace(',', '.').toDoubleOrNull() ?: return
        val nw = w.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: return
        val nh = h.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: return
        onPlace(Box(nx, ny, nx + nw, ny + nh))
    }
    val ratio = if (box.height > 1e-9) box.width / box.height else 1.0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("X", x, Modifier.weight(1f)) { x = it; place() }
            NumberField("Y", y, Modifier.weight(1f)) { y = it; place() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(stringResource(R.string.svg_width_short), w, Modifier.weight(1f)) {
                w = it
                if (linked) it.replace(',', '.').toDoubleOrNull()?.let { v -> h = svgNumber(v / ratio) }
                place()
            }
            FilledTonalIconToggleButton(
                checked = linked,
                onCheckedChange = { linked = it },
            ) {
                Icon(Icons.Rounded.Link, contentDescription = stringResource(R.string.svg_keep_ratio))
            }
            NumberField(stringResource(R.string.svg_height_short), h, Modifier.weight(1f)) {
                h = it
                if (linked) it.replace(',', '.').toDoubleOrNull()?.let { v -> w = svgNumber(v * ratio) }
                place()
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        modifier = modifier,
    )
}

/** How the selection is repeated. */
private enum class RepeatKind(val label: Int, val icon: ImageVector) {
    ROW(R.string.svg_repeat_row, Icons.Rounded.ViewWeek),
    GRID(R.string.svg_repeat_grid, Icons.Rounded.GridView),
    AROUND(R.string.svg_repeat_around, Icons.Rounded.Cyclone),
}

/**
 * Repeating the selection: in a row (across or down), in a grid, or turned
 * round the page's centre; how many, and the gap between them.
 */
@Composable
private fun RepeatPanel(onAct: (SvgAct) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var kind by rememberSaveable { mutableStateOf(RepeatKind.ROW) }
    var count by rememberSaveable { mutableStateOf(4) }
    var rows by rememberSaveable { mutableStateOf(3) }
    var gap by rememberSaveable { mutableStateOf(16f) }
    var across by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.cuscus.ffmpeggui.ui.expressive.OptionTileRow(
            options = RepeatKind.entries.map { com.cuscus.ffmpeggui.ui.expressive.TileOption(it, stringResource(it.label), it.icon) },
            selected = kind,
            onSelect = { kind = it },
        )
        Stepper(stringResource(if (kind == RepeatKind.GRID) R.string.svg_repeat_columns else R.string.svg_repeat_count), count, 2..36) { count = it }
        if (kind == RepeatKind.GRID) Stepper(stringResource(R.string.svg_repeat_rows), rows, 1..36) { rows = it }
        if (kind == RepeatKind.ROW) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = across, onClick = { across = true }, label = { Text(stringResource(R.string.svg_repeat_across)) })
                androidx.compose.material3.FilterChip(selected = !across, onClick = { across = false }, label = { Text(stringResource(R.string.svg_repeat_down)) })
            }
        }
        if (kind != RepeatKind.AROUND) {
            com.cuscus.ffmpeggui.ui.expressive.ValueSlider(
                label = stringResource(R.string.svg_repeat_gap),
                valueText = svgNumber(gap.toDouble()),
                value = gap,
                onValueChange = { gap = it.roundToInt().toFloat() },
                valueRange = -100f..300f,
                defaultValue = 16f,
            )
        }
        androidx.compose.material3.Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onAct(
                    when (kind) {
                        RepeatKind.ROW -> SvgAct.RepeatRow(count, gap.toDouble(), across)
                        RepeatKind.GRID -> SvgAct.RepeatGrid(count, rows, gap.toDouble())
                        RepeatKind.AROUND -> SvgAct.RepeatAround(count)
                    },
                )
            },
            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(kind.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.svg_repeat_do, if (kind == RepeatKind.GRID) count * rows else count), style = MaterialTheme.typography.titleSmallEmphasized)
        }
    }
}

/** A whole number picked with a minus, its value set large, and a plus. */
@Composable
internal fun Stepper(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onValue((value - 1).coerceIn(range))
            },
            enabled = value > range.first,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Rounded.Remove, contentDescription = null) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmallEmphasized.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onValue((value + 1).coerceIn(range))
            },
            enabled = value < range.last,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Rounded.Add, contentDescription = null) }
    }
}
