@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui.svgedit

import com.cuscus.ffmpeggui.core.vector.svg.isLocked
import com.cuscus.ffmpeggui.core.vector.svg.isSymmetryCopy
import com.cuscus.ffmpeggui.core.vector.svg.Symmetry
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.svg.PaintKind
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgGroup
import com.cuscus.ffmpeggui.core.vector.svg.SvgImage
import com.cuscus.ffmpeggui.core.vector.svg.SvgNode
import com.cuscus.ffmpeggui.core.vector.svg.SvgOther
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.SvgText
import com.cuscus.ffmpeggui.core.vector.svg.allNodes
import com.cuscus.ffmpeggui.core.vector.svg.nodeCount
import com.cuscus.ffmpeggui.core.vector.svg.pathTo
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress

/*
 * The drawing's layers: every node as a row, the top of the drawing first
 * (as layer lists go), groups opening onto what they hold. A row's badge is
 * a rounded square that blooms into a cookie when it is the selected one; its
 * eye hides and shows it. A drawing traced from a photo may hold thousands of
 * nodes: the list is lazy, and its large groups start closed.
 */

/** What a node is, for a person: its name and its icon. */
internal class SvgKind(@StringRes val label: Int, val icon: ImageVector, val shape: RoundedPolygon)

internal fun kindOf(node: SvgNode): SvgKind = when (node) {
    is SvgGroup -> SvgKind(R.string.svg_kind_group, Icons.Rounded.Folder, MaterialShapes.Bun)
    is SvgText -> SvgKind(R.string.svg_kind_text, Icons.Rounded.TextFields, MaterialShapes.Slanted)
    is SvgImage -> SvgKind(R.string.svg_kind_image, Icons.Rounded.Image, MaterialShapes.Arch)
    is SvgOther -> SvgKind(R.string.svg_kind_other, Icons.Rounded.Code, MaterialShapes.Ghostish)
    is SvgShape -> when (node.tag) {
        "rect" -> SvgKind(R.string.svg_kind_rect, Icons.Rounded.CropSquare, MaterialShapes.Square)
        "circle" -> SvgKind(R.string.svg_kind_circle, Icons.Rounded.Circle, MaterialShapes.Circle)
        "ellipse" -> SvgKind(R.string.svg_kind_ellipse, Icons.Rounded.Circle, MaterialShapes.Oval)
        "line" -> SvgKind(R.string.svg_kind_line, Icons.Rounded.HorizontalRule, MaterialShapes.Pill)
        "polyline" -> SvgKind(R.string.svg_kind_polyline, Icons.Rounded.Timeline, MaterialShapes.Diamond)
        "polygon" -> SvgKind(R.string.svg_kind_polygon, Icons.Rounded.ChangeHistory, MaterialShapes.Triangle)
        else -> SvgKind(R.string.svg_kind_path, Icons.Rounded.Gesture, MaterialShapes.Flower)
    }
}

/** One row of the list: a node, how deep it is, whether it is a group that is open. */
private class LayerRow(val node: SvgNode, val depth: Int, val open: Boolean)

/** Groups larger than this start closed. */
private const val OPEN_LIMIT = 40

@Composable
internal fun SvgLayersSheet(
    doc: SvgDocument,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onToggleHidden: (SvgNode) -> Unit,
    /** Locks the node, or unlocks it. */
    onToggleLocked: (SvgNode) -> Unit = {},
    /** The node [key] dropped: just above [below]'s node, or (at the bottom of the list) just below [above]'s. */
    onMove: (Int, Int?, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scheme = MaterialTheme.colorScheme
    // Groups opened or closed by hand; the rest follow their size.
    var toggled by remember { mutableStateOf(emptyMap<Int, Boolean>()) }
    // The selected node's groups open, so that it shows.
    val selectedPath = remember(doc, selected) { selected?.let { doc.pathTo(it) }?.dropLast(1)?.map { it.key }.orEmpty() }
    val rows = remember(doc, toggled, selectedPath) {
        val out = ArrayList<LayerRow>()
        fun walk(nodes: List<SvgNode>, depth: Int) {
            for (n in nodes.asReversed()) {
                // A symmetry's copies are not layers of their own: they follow their source.
                if (n.isSymmetryCopy) continue
                val open = n is SvgGroup && (toggled[n.key] ?: (n.key in selectedPath || n.children.size <= OPEN_LIMIT))
                out += LayerRow(n, depth, open)
                if (n is SvgGroup && open) walk(n.children, depth + 1)
            }
        }
        walk(doc.nodes, 0)
        out
    }
    val list = rememberLazyListState()
    // A row being dragged by its handle: which, how far from its place, and the order as it is shown meanwhile.
    var dragKey by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf<List<LayerRow>?>(null) }
    val shown = working ?: rows
    LaunchedEffect(Unit) {
        val at = rows.indexOfFirst { it.node.key == selected }
        if (at > 2) list.scrollToItem(at - 2)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                MorphingContainer(
                    from = MaterialShapes.Cookie9Sided,
                    to = MaterialShapes.Cookie9Sided,
                    morphed = false,
                    color = scheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, tint = scheme.onTertiaryContainer)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.svg_layers), style = MaterialTheme.typography.headlineSmallEmphasized)
                    val count = doc.nodeCount
                    Text(
                        pluralStringResource(R.plurals.svg_elements, count, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.isNotEmpty()) {
                Text(
                    stringResource(R.string.svg_layers_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.svg_layers_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally),
                )
            }
            LazyColumn(
                state = list,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            ) {
                items(shown, key = { it.node.key }) { row ->
                    val key = row.node.key
                    val dragging = key == dragKey
                    LayerItem(
                        row = row,
                        dragging = dragging,
                        handle = Modifier.pointerInput(key, rows) {
                            detectDragGestures(
                                onDragStart = {
                                    // A group is moved with all it holds: shown closed while it moves.
                                    val closed = if (row.node is SvgGroup && row.open) {
                                        val inside = (row.node as SvgGroup).let { g -> SvgDocument(0.0, 0.0, doc.viewBox, emptyList(), g.children).allNodes().map { it.key }.toSet() }
                                        rows.filter { it.node.key !in inside }.map { if (it.node.key == key) LayerRow(it.node, it.depth, false) else it }
                                    } else {
                                        rows
                                    }
                                    working = closed
                                    dragKey = key
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val order = working ?: return@detectDragGestures
                                    val at = order.indexOfFirst { it.node.key == key }
                                    val items = list.layoutInfo.visibleItemsInfo
                                    val me = items.firstOrNull { it.key == key } ?: return@detectDragGestures
                                    val centre = me.offset + dragOffset + me.size / 2f
                                    val next = items.firstOrNull { it.index == me.index + 1 }
                                    val prev = items.firstOrNull { it.index == me.index - 1 }
                                    if (next != null && at < order.lastIndex && centre > next.offset + next.size / 2f) {
                                        working = order.toMutableList().apply { add(at + 1, removeAt(at)) }
                                        dragOffset -= (next.offset + next.size - (me.offset + me.size)).toFloat()
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    } else if (prev != null && at > 0 && centre < prev.offset + prev.size / 2f) {
                                        working = order.toMutableList().apply { add(at - 1, removeAt(at)) }
                                        dragOffset -= (prev.offset - me.offset).toFloat()
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                },
                                onDragEnd = {
                                    val order = working
                                    val at = order?.indexOfFirst { it.node.key == key } ?: -1
                                    val before = rows.indexOfFirst { it.node.key == key }
                                    if (order != null && at >= 0) {
                                        val below = order.getOrNull(at + 1)?.node?.key
                                        val above = order.getOrNull(at - 1)?.node?.key
                                        // Not moved at all: nothing to do.
                                        val same = rows.getOrNull(before + 1)?.node?.key == below && rows.getOrNull(before - 1)?.node?.key == above
                                        if (!same) {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            onMove(key, below, above)
                                        }
                                    }
                                    working = null
                                    dragKey = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    working = null
                                    dragKey = null
                                    dragOffset = 0f
                                },
                            )
                        },
                        selected = row.node.key == selected,
                        onClick = { onSelect(row.node.key) },
                        onToggleOpen = {
                            val n = row.node
                            toggled = toggled + (n.key to !row.open)
                        },
                        onToggleHidden = { onToggleHidden(row.node) },
                        onToggleLocked = { onToggleLocked(row.node) },
                        modifier = if (dragging) {
                            // Lifted over the others, following the finger.
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY = dragOffset
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 12.dp.toPx()
                                    shape = RoundedCornerShape(28.dp)
                                    clip = false
                                }
                        } else {
                            Modifier.animateItem()
                        },
                    )
                }
                item { Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

@Composable
private fun LayerItem(
    row: LayerRow,
    dragging: Boolean,
    handle: Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleOpen: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val node = row.node
    val kind = kindOf(node)
    val hidden = node.style.hidden
    val locked = node.isLocked
    // A shape's badge wears its own colour: the layers of a trace tell apart at a glance.
    val swatch = (node.style.fill?.takeIf { it.kind == PaintKind.COLOR })?.let { Color((it.argb or 0xFF000000L).toInt()) }
    val container by animateColorAsState(
        when {
            dragging -> scheme.tertiaryContainer
            selected -> scheme.primaryContainer
            else -> scheme.surfaceContainerHigh
        },
        motion.defaultEffectsSpec(),
        label = "row",
    )
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    val chevron by animateFloatAsState(if (row.open) 0f else -90f, motion.fastSpatialSpec(), label = "chevron")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 18).coerceAtMost(144).dp)
            .springPress(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(if (selected) 28.dp else 18.dp))
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Square,
            to = kind.shape.takeIf { it !== MaterialShapes.Square } ?: MaterialShapes.Cookie9Sided,
            morphed = selected || pressed,
            color = when {
                selected -> scheme.primary
                swatch != null -> swatch
                else -> scheme.secondaryContainer
            },
            modifier = Modifier.size(40.dp),
        ) {
            val tint = when {
                selected -> scheme.onPrimary
                swatch != null -> if (swatch.luminance() > 0.5f) Color.Black else Color.White
                else -> scheme.onSecondaryContainer
            }
            Icon(kind.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).alpha(if (hidden) 0.5f else 1f)) {
            Text(
                text = node.label ?: stringResource(kind.label),
                style = if (selected) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.titleSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                hidden -> stringResource(R.string.svg_hidden)
                locked -> stringResource(R.string.svg_locked)
                node is SvgGroup && node.attributes.containsKey(Symmetry.GROUP) -> stringResource(R.string.svg_symmetry_layer)
                node is SvgText -> node.text.replace('\n', ' ')
                node is SvgGroup -> pluralStringResource(R.plurals.svg_elements, node.children.size, node.children.size)
                node.label != null -> stringResource(kind.label)
                else -> null
            }
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (node is SvgGroup && node.children.isNotEmpty()) {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onToggleOpen()
            }) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (row.open) R.string.action_collapse else R.string.action_expand),
                    tint = content,
                    modifier = Modifier.rotate(chevron),
                )
            }
        }
        IconButton(onClick = {
            haptic.performHapticFeedback(if (locked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onToggleLocked()
        }) {
            Icon(
                if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = stringResource(if (locked) R.string.svg_unlock else R.string.svg_lock),
                tint = if (locked) scheme.tertiary else content.copy(alpha = 0.45f),
            )
        }
        IconButton(onClick = {
            haptic.performHapticFeedback(if (hidden) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggleHidden()
        }) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (hidden) R.string.svg_show else R.string.svg_hide),
                    tint = content.copy(alpha = if (hidden) 0.6f else 1f),
                )
            }
        }
        // The handle: dragged, the row moves above or below the others.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 40.dp, height = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (dragging) scheme.tertiary else scheme.surfaceContainerHighest)
                .then(handle),
        ) {
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = stringResource(R.string.svg_layer_move),
                tint = if (dragging) scheme.onTertiary else content,
            )
        }
    }
}
