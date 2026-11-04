@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.model.stackOrder
import com.cuscus.ffmpeggui.preview.smallPicture
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.springPress
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/*
 * The list of layers of the picture editor: every text and sticker laid on
 * the picture, one row each, the one in front at the top, as in a photo app.
 * A row is dragged by its handle to another place in the stack (the others
 * make way with the theme's spatial springs); a tap selects it on the picture
 * too, and the selected row opens its own actions (to the front, up, down, to
 * the back, duplicate, delete). The rows are a Material 3 Expressive grouped
 * list: they touch, very round at the group's ends; the selected and the
 * dragged one round off whole and stand out.
 *
 * Each row has a lock and an eye (issue #30), as the SVG editor's layers: a
 * hidden layer is kept but neither shown nor exported, a locked one cannot be
 * picked nor moved on the picture (it is chosen here). Each toggle is a round
 * button that morphs into a cookie while it is on.
 */

/** One text or sticker of the picture, as the list shows it. */
internal class LayerEntry(
    val id: String,
    val name: String,
    /** The caption, for a text. */
    val text: TextOverlay?,
    /** The picture laid over, for a sticker. */
    val sticker: ImageOverlay?,
) {
    val hidden: Boolean get() = text?.hidden ?: sticker?.hidden ?: false
    val locked: Boolean get() = text?.locked ?: sticker?.locked ?: false

    /** The picture itself on its canvas cannot be hidden: without it there is nothing to edit. */
    val hideable: Boolean get() = sticker?.isPhoto != true
}

/** The texts and stickers of the picture, the one in front first (the reverse of stackOrder). */
internal fun layerEntries(
    texts: List<TextOverlay>,
    stickers: List<ImageOverlay>,
    untitledText: String,
    untitledSticker: String,
    /** The name of each kind of shape, for shape layers. */
    shapeNames: Map<ShapeKind, String> = emptyMap(),
    /** The name of each of the app's effects, by its library name. */
    effectNames: Map<String, String> = emptyMap(),
): List<LayerEntry> =
    stackOrder(texts.map { it.layer }, stickers.map { it.layer }).asReversed().map { slot ->
        if (slot.isText) {
            val text = texts[slot.index]
            LayerEntry(text.id, text.text.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { untitledText }, text, null)
        } else {
            val sticker = stickers[slot.index]
            val name = sticker.shape?.let { shapeNames[it.kind] }
                ?: sticker.effect?.let { effectNames[it] ?: it }
                ?: sticker.fileName.ifBlank { untitledSticker }
            LayerEntry(sticker.id, name, null, sticker)
        }
    }

/**
 * The layers panel, of the picture editor and of the video editor (the texts,
 * pictures, videos and shapes of the whole video). [onPlace] puts a layer at a
 * place counted from the bottom (0) up, as the export stacks them; [onArrange]
 * moves one a step or to an end. Without [enabled] (a hand-written command)
 * the list only shows. [addPictureLabel] names what [onAddSticker] adds.
 */
@Composable
internal fun ImageLayersPanel(
    texts: List<TextOverlay>,
    stickers: List<ImageOverlay>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
    onPlace: (String, Int) -> Unit,
    onArrange: (String, LayerMove) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    /** The eye of a row: the layer hidden or shown again. */
    onToggleHidden: (String) -> Unit,
    /** The lock of a row: the layer locked on the picture or freed. */
    onToggleLocked: (String) -> Unit,
    onAddText: () -> Unit,
    onAddSticker: () -> Unit,
    onAddShape: () -> Unit,
    addPictureLabel: String = stringResource(R.string.layers_add_sticker),
    untitledSticker: String = stringResource(R.string.image_sticker_name),
) {
    val untitledText = stringResource(R.string.tool_text)
    val shapeNames = ShapeKind.entries.associateWith { stringResource(it.labelRes) }
    val effectNames = effectLabels()
    val entries = layerEntries(texts, stickers, untitledText, untitledSticker, shapeNames, effectNames)
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AnimatedContent(
            targetState = entries.isEmpty(),
            transitionSpec = {
                (scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f) + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "layersEmpty",
        ) { empty ->
            if (empty) LayersEmpty() else LayersHeader(count = entries.size)
        }
        if (entries.isNotEmpty()) {
            LayerList(
                entries = entries,
                selectedId = selectedId,
                enabled = enabled,
                onSelect = onSelect,
                onPlace = onPlace,
                onArrange = onArrange,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onToggleHidden = onToggleHidden,
                onToggleLocked = onToggleLocked,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AddLayerButton(Icons.Rounded.Title, stringResource(R.string.layers_add_text), enabled, onAddText, Modifier.weight(1f))
            AddLayerButton(Icons.Rounded.AddPhotoAlternate, addPictureLabel, enabled, onAddSticker, Modifier.weight(1f))
            AddLayerButton(Icons.Rounded.Category, stringResource(R.string.layers_add_shape), enabled, onAddShape, Modifier.weight(1f))
        }
    }
}

/** How many layers there are, in a big badge that bursts in, and how the list reads. */
@Composable
private fun LayersHeader(count: Int) {
    // The badge pops each time the count changes.
    var pulse by remember { mutableIntStateOf(0) }
    val pop = remember { Animatable(1f) }
    val popSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(count) {
        pulse++
        pop.snapTo(0.82f)
        pop.animateTo(1f, popSpec)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Clover8Leaf,
            to = MaterialShapes.SoftBurst,
            morphed = pulse % 2 == 1,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMediumEmphasized.copy(fontFamily = DisplayFamily, fontWeight = FontWeight(800)),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(R.plurals.layers_count, count, count),
                style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFamily = DisplayFamily),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.layers_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/** Nothing on the picture yet: a slowly turning burst, and what will show here. */
@Composable
private fun LayersEmpty() {
    val turning = rememberInfiniteTransition(label = "layersEmptyTurn")
    val turn by turning.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(EMPTY_TURN_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "layersEmptyAngle",
    )
    var bloomed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bloomed = true }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 22.dp, horizontal = 20.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = MaterialShapes.SoftBurst,
                morphed = bloomed,
                color = MaterialTheme.colorScheme.primaryContainer,
                rotationDegrees = turn,
                modifier = Modifier.fillMaxSize(),
            ) {}
            Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.layers_empty_title),
            style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFamily = DisplayFamily),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        PanelHint(stringResource(R.string.layers_empty_hint))
    }
}

/** A big tonal button that adds a text, a picture or a shape: its icon over its name; it morphs under the finger. */
@Composable
private fun AddLayerButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        modifier = modifier.height(68.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The rows, the one in front first. While a row is dragged it follows the
 * finger, lifted over the others, and the rows it passes slide out of its
 * way; let go, it lands where the gap is, as one undo step ([onPlace]).
 */
@Composable
private fun LayerList(
    entries: List<LayerEntry>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
    onPlace: (String, Int) -> Unit,
    onArrange: (String, LayerMove) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onToggleLocked: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val gapPx = with(LocalDensity.current) { ROW_GAP.toPx() }

    // The order just dropped, shown until the project brings it back.
    val ids = entries.map { it.id }
    var dropped by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(ids) { dropped = null }
    val byId = entries.associateBy { it.id }
    val shown = dropped?.takeIf { it.toSet() == ids.toSet() }?.mapNotNull { byId[it] } ?: entries

    // The row under the finger, how far it went, and every row's height.
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    // A dropped row settles from where the finger left it into its slot.
    val settle = remember { Animatable(0f) }
    var settlingId by remember { mutableStateOf<String?>(null) }
    // Counted up at each drop: the rows' make-way springs start again from rest.
    var drops by remember { mutableIntStateOf(0) }

    val from = draggedId?.let { id -> shown.indexOfFirst { it.id == id } } ?: -1
    val target = if (from >= 0) dropTarget(shown, heights, from, dragOffset, gapPx) else -1
    val draggedSpan = if (from >= 0) (heights[shown[from].id] ?: 0) + gapPx else 0f

    // A tick each time the gap moves to another place.
    var lastTarget by remember { mutableIntStateOf(-1) }
    LaunchedEffect(target) {
        if (target >= 0 && lastTarget >= 0 && target != lastTarget) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastTarget = target
    }

    val currentShown by rememberUpdatedState(shown)
    val currentOnPlace by rememberUpdatedState(onPlace)
    fun drop() {
        val id = draggedId ?: return
        val list = currentShown
        val start = list.indexOfFirst { it.id == id }
        if (start < 0) {
            draggedId = null
            return
        }
        val end = dropTarget(list, heights, start, dragOffset, gapPx)
        // How far the row travelled to its slot; what is left over springs away.
        val travelled = when {
            end > start -> (start + 1..end).sumOf { (heights[list[it].id] ?: 0) + gapPx.toDouble() }.toFloat()
            end < start -> -(end until start).sumOf { (heights[list[it].id] ?: 0) + gapPx.toDouble() }.toFloat()
            else -> 0f
        }
        val residual = dragOffset - travelled
        drops++
        draggedId = null
        dragOffset = 0f
        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
        settlingId = id
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(residual)
            settle.animateTo(0f, motion.defaultSpatialSpec())
        }
        if (end != start) {
            val order = list.map { it.id }.toMutableList()
            order.add(end, order.removeAt(start))
            dropped = order
            // The list is the front first; the stack counts from the bottom.
            currentOnPlace(id, list.lastIndex - end)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP), modifier = Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, entry ->
            key(entry.id) {
                val dragging = entry.id == draggedId
                // Out of the dragged row's way: up or down by its height, with a spring.
                val makeWay = when {
                    from < 0 || dragging -> 0f
                    target > from && index in from + 1..target -> -draggedSpan
                    target < from && index in target until from -> draggedSpan
                    else -> 0f
                }
                val shift = key(drops) {
                    animateFloatAsState(makeWay, motion.defaultSpatialSpec(), label = "layerMakeWay")
                }
                LayerRow(
                    entry = entry,
                    index = index,
                    count = shown.size,
                    selected = entry.id == selectedId,
                    dragging = dragging,
                    enabled = enabled,
                    offset = {
                        when {
                            dragging -> dragOffset
                            entry.id == settlingId -> settle.value
                            else -> shift.value
                        }
                    },
                    onHeight = { heights[entry.id] = it },
                    onClick = { onSelect(if (entry.id == selectedId) null else entry.id) },
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        settlingId = null
                        draggedId = entry.id
                        dragOffset = 0f
                    },
                    onDrag = { delta -> if (draggedId == entry.id) dragOffset += delta },
                    onDragEnd = { drop() },
                    onArrange = { move -> onArrange(entry.id, move) },
                    onDuplicate = { onDuplicate(entry.id) },
                    onDelete = { onDelete(entry.id) },
                    onToggleHidden = { onToggleHidden(entry.id) },
                    onToggleLocked = { onToggleLocked(entry.id) },
                )
            }
        }
    }
}

/**
 * Where the row at [from] would land if let go [offset] pixels from its
 * place: it passes a neighbour once it is more than halfway over it.
 */
private fun dropTarget(rows: List<LayerEntry>, heights: Map<String, Int>, from: Int, offset: Float, gapPx: Float): Int {
    var target = from
    var left = offset
    if (left > 0f) {
        while (target < rows.lastIndex) {
            val span = (heights[rows[target + 1].id] ?: 0) + gapPx
            if (left > span / 2f) {
                left -= span
                target++
            } else {
                break
            }
        }
    } else {
        while (target > 0) {
            val span = (heights[rows[target - 1].id] ?: 0) + gapPx
            if (-left > span / 2f) {
                left += span
                target--
            } else {
                break
            }
        }
    }
    return target
}

/**
 * One layer: its picture (or its text, in its own colour and font) in a
 * MaterialShapes badge that rounds into a circle when chosen, its name and
 * place, and the handle to drag it by. Selected, it opens its actions under it.
 */
@Composable
private fun LayerRow(
    entry: LayerEntry,
    index: Int,
    count: Int,
    selected: Boolean,
    dragging: Boolean,
    enabled: Boolean,
    offset: () -> Float,
    onHeight: (Int) -> Unit,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onArrange: (LayerMove) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleLocked: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    // A hidden layer's row is dimmed (its toggles stay clear, to show it again).
    val dim by animateFloatAsState(if (entry.hidden) HIDDEN_ALPHA else 1f, motion.defaultEffectsSpec(), label = "layerHidden")

    // The grouped list's corners: very round at its ends, barely between; the chosen
    // and the lifted row round off whole, springing there.
    val whole = selected || dragging
    val top by animateDpAsState(if (whole || index == 0) OUTER_RADIUS else INNER_RADIUS, motion.fastSpatialSpec(), label = "layerTop")
    val bottom by animateDpAsState(if (whole || index == count - 1) OUTER_RADIUS else INNER_RADIUS, motion.fastSpatialSpec(), label = "layerBottom")
    val container by animateColorAsState(
        targetValue = when {
            dragging -> colors.tertiaryContainer
            selected -> colors.primaryContainer
            else -> colors.surfaceContainerHigh
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "layerContainer",
    )
    val onContainer by animateColorAsState(
        targetValue = when {
            dragging -> colors.onTertiaryContainer
            selected -> colors.onPrimaryContainer
            else -> colors.onSurface
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "layerOnContainer",
    )
    val lift by animateFloatAsState(if (dragging) 1f else 0f, motion.fastSpatialSpec(), label = "layerLift")

    val position = index + 1
    val kind = stringResource(
        when {
            entry.text != null -> R.string.layers_kind_text
            entry.sticker?.isPhoto == true -> R.string.layers_kind_picture
            entry.sticker?.shape != null -> R.string.layers_kind_shape
            entry.sticker?.isEffect == true -> R.string.layers_kind_effect
            entry.sticker?.isVideo == true -> R.string.layers_kind_video
            else -> R.string.layers_kind_sticker
        },
    )
    val placeOnly = stringResource(R.string.layers_place, kind, position, count)
    // What it is, after where it is: hidden, locked, or both.
    val hiddenWord = stringResource(R.string.layers_hidden)
    val lockedWord = stringResource(R.string.layers_locked)
    val place = listOfNotNull(placeOnly, hiddenWord.takeIf { entry.hidden }, lockedWord.takeIf { entry.locked }).joinToString(" \u00B7 ")
    val selectedState = stringResource(R.string.layers_selected)
    val dragLabel = stringResource(R.string.layers_drag, entry.name)
    val upLabel = stringResource(R.string.overlay_layer_forward)
    val downLabel = stringResource(R.string.overlay_layer_backward)
    val frontLabel = stringResource(R.string.overlay_layer_front)
    val backLabel = stringResource(R.string.overlay_layer_back)
    val atFront = index == 0
    val atBack = index == count - 1
    // The drag handler outlives recompositions: it calls the latest callbacks.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .zIndex(if (dragging) 2f else if (selected) 1f else 0f)
            .graphicsLayer {
                translationY = offset()
                val grow = 1f + LIFT_SCALE * lift
                scaleX = grow
                scaleY = grow
                shadowElevation = with(density) { LIFT_ELEVATION.toPx() } * lift
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                clip = true
            }
            .background(container),
    ) {
        val interaction = remember { MutableInteractionSource() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
                .semantics(mergeDescendants = true) {
                    if (selected) stateDescription = selectedState
                    customActions = buildList {
                        if (enabled && !atFront) {
                            add(CustomAccessibilityAction(frontLabel) { onArrange(LayerMove.TO_FRONT); true })
                            add(CustomAccessibilityAction(upLabel) { onArrange(LayerMove.FORWARD); true })
                        }
                        if (enabled && !atBack) {
                            add(CustomAccessibilityAction(downLabel) { onArrange(LayerMove.BACKWARD); true })
                            add(CustomAccessibilityAction(backLabel) { onArrange(LayerMove.TO_BACK); true })
                        }
                    }
                }
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Box(modifier = Modifier.graphicsLayer { alpha = dim }) {
                LayerBadge(entry = entry, chosen = whole, interaction = interaction)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).graphicsLayer { alpha = dim }) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // The ends of the stack say so.
                    if (count > 1 && (atFront || atBack)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        EndPill(front = atFront)
                    }
                }
            }
            // Its lock and its eye, as in the SVG editor's layers.
            LayerToggle(
                on = entry.locked,
                onIcon = Icons.Rounded.Lock,
                offIcon = Icons.Rounded.LockOpen,
                label = stringResource(if (entry.locked) R.string.layers_unlock else R.string.layers_lock),
                enabled = enabled,
                content = onContainer,
                onToggle = onToggleLocked,
            )
            if (entry.hideable) {
                LayerToggle(
                    on = entry.hidden,
                    onIcon = Icons.Rounded.VisibilityOff,
                    offIcon = Icons.Rounded.Visibility,
                    label = stringResource(if (entry.hidden) R.string.layers_show else R.string.layers_hide),
                    enabled = enabled,
                    content = onContainer,
                    onToggle = onToggleHidden,
                )
            }
            // The handle: a drag here moves the layer through the stack.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = dragLabel }
                    .then(
                        if (!enabled || count < 2) {
                            Modifier
                        } else {
                            Modifier.pointerInput(entry.id) {
                                detectVerticalDragGestures(
                                    onDragStart = { currentOnDragStart() },
                                    onDragEnd = { currentOnDragEnd() },
                                    onDragCancel = { currentOnDragEnd() },
                                    onVerticalDrag = { change, delta ->
                                        change.consume()
                                        currentOnDrag(delta)
                                    },
                                )
                            }
                        },
                    ),
            ) {
                MorphingContainer(
                    from = MaterialShapes.Circle,
                    to = MaterialShapes.Cookie4Sided,
                    morphed = dragging,
                    color = if (dragging) colors.tertiary else Color.Transparent,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = null,
                        tint = if (dragging) colors.onTertiary else onContainer.copy(alpha = if (enabled && count > 1) 0.9f else 0.3f),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = selected && enabled,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            LayerActions(
                atFront = atFront,
                atBack = atBack,
                onArrange = onArrange,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                removable = entry.sticker?.isPhoto != true,
            )
        }
    }
}

/**
 * A layer's lock or eye: a round button that morphs into a cookie, filled with
 * the tertiary tone, while it is [on] (locked, hidden), and squishes under the
 * finger. Read out as a switch, named by what a tap does ([label]).
 */
@Composable
private fun LayerToggle(
    on: Boolean,
    onIcon: ImageVector,
    offIcon: ImageVector,
    label: String,
    enabled: Boolean,
    content: Color,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .springPress(interaction, pressedScale = 0.85f)
            .toggleable(
                value = on,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) {
                haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onToggle()
            }
            .semantics { contentDescription = label },
    ) {
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie4Sided,
            morphed = on,
            color = if (on) colors.tertiaryContainer else Color.Transparent,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (on) onIcon else offIcon,
                contentDescription = null,
                tint = when {
                    on -> colors.onTertiaryContainer
                    enabled -> content.copy(alpha = 0.6f)
                    else -> content.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A row of a hidden layer, its badge and its words, dimmed this much. */
private const val HIDDEN_ALPHA = 0.45f

/** A small pill saying the row is the front or the back of the stack. */
@Composable
private fun EndPill(front: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (front) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (front) Icons.Rounded.FlipToFront else Icons.Rounded.FlipToBack,
            contentDescription = null,
            tint = if (front) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(if (front) R.string.layers_front else R.string.layers_back),
            style = MaterialTheme.typography.labelSmall,
            color = if (front) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What the layer looks like: a sticker's picture, or "Aa" in a text's own
 * colour and font, in a MaterialShapes badge of its own that rounds into a
 * circle when chosen and squishes under the finger.
 */
@Composable
private fun LayerBadge(entry: LayerEntry, chosen: Boolean, interaction: MutableInteractionSource) {
    val text = entry.text
    val sticker = entry.sticker
    val textColor = text?.let { Color(it.color) }
    // A light text on a dark badge, a dark one on a light badge.
    val badgeColor = when {
        textColor != null -> if (textColor.luminance() > 0.5f) Color(0xFF1C1B1F) else Color(0xFFF4EFF4)
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    MorphingContainer(
        from = badgeShapeFor(entry.id),
        to = MaterialShapes.Circle,
        morphed = chosen,
        color = badgeColor,
        modifier = Modifier.springPress(interaction, pressedScale = 0.86f).size(BADGE_SIZE),
    ) {
        when {
            text != null -> Text(
                text = stringResource(R.string.layers_text_sample),
                fontFamily = rememberExportFont(text.font, text.isBold),
                fontWeight = if (text.isBold) FontWeight.Bold else FontWeight.Normal,
                fontSize = 20.sp,
                color = textColor ?: MaterialTheme.colorScheme.onSurface,
            )
            // One of the app's effects plays its own frames, on black as it is drawn.
            sticker != null && sticker.isEffect -> EffectFrames(sticker.effect.orEmpty(), sticker.hue, modifier = Modifier.fillMaxSize())
            sticker != null && !sticker.isVideo -> {
                val picture = rememberLayerPicture(sticker)
                Checkerboard(modifier = Modifier.fillMaxSize(), cell = 6.dp)
                if (picture != null) {
                    Image(
                        bitmap = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = sticker.opacity.coerceIn(0.25f, 1f)
                                scaleX = if (sticker.flipHorizontal) -1f else 1f
                                scaleY = if (sticker.flipVertical) -1f else 1f
                            },
                    )
                }
            }
            else -> Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The sticker's picture, small, decoded off the main thread. */
@Composable
private fun rememberLayerPicture(sticker: ImageOverlay): ImageBitmap? {
    val context = LocalContext.current
    val uri = sticker.uri ?: return null
    val picture by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { smallPicture(context, uri, THUMB_PX)?.asImageBitmap() }
    }
    return picture
}

/** The selected layer's own actions: through the stack, duplicate, delete. */
@Composable
private fun LayerActions(
    atFront: Boolean,
    atBack: Boolean,
    onArrange: (LayerMove) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    /** It can be copied and deleted (not the picture on its canvas, which goes with the canvas). */
    removable: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
    ) {
        listOf(
            Triple(LayerMove.TO_FRONT, Icons.Rounded.FlipToFront, R.string.overlay_layer_front),
            Triple(LayerMove.FORWARD, Icons.Rounded.KeyboardArrowUp, R.string.overlay_layer_forward),
            Triple(LayerMove.BACKWARD, Icons.Rounded.KeyboardArrowDown, R.string.overlay_layer_backward),
            Triple(LayerMove.TO_BACK, Icons.Rounded.FlipToBack, R.string.overlay_layer_back),
        ).forEach { (move, icon, label) ->
            val forward = move == LayerMove.TO_FRONT || move == LayerMove.FORWARD
            ActionButton(
                icon = icon,
                label = stringResource(label),
                enabled = if (forward) !atFront else !atBack,
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.weight(1f),
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onArrange(move)
            }
        }
        if (removable) ActionButton(
            icon = Icons.Rounded.ContentCopy,
            label = stringResource(R.string.tool_duplicate),
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.weight(1f),
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onDuplicate()
        }
        if (removable) ActionButton(
            icon = Icons.Rounded.DeleteOutline,
            label = stringResource(R.string.tool_delete),
            enabled = true,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.weight(1f),
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
            onDelete()
        }
    }
}

/** An icon over its label in a button that morphs under the finger. */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    colors: androidx.compose.material3.ButtonColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = colors,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
        modifier = modifier.height(60.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Each layer keeps its own badge shape, picked from its id, so the list reads as a set of tokens. */
private fun badgeShapeFor(id: String): RoundedPolygon {
    val shapes = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Flower,
        MaterialShapes.Sunny,
        MaterialShapes.Gem,
        MaterialShapes.Cookie12Sided,
    )
    return shapes[abs(id.hashCode() % shapes.size)]
}

private val ROW_GAP = 3.dp
private val ROW_HEIGHT = 72.dp
private val OUTER_RADIUS = 28.dp
private val INNER_RADIUS = 6.dp
private val BADGE_SIZE: Dp = 52.dp
private val LIFT_ELEVATION = 14.dp
private const val LIFT_SCALE = 0.035f
private const val THUMB_PX = 160
private const val EMPTY_TURN_MS = 16_000
