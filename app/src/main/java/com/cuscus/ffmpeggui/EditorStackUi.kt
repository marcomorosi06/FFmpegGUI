@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.conversion.NestedApply
import com.cuscus.ffmpeggui.onboarding.popIn
import com.cuscus.ffmpeggui.core.stack.TreeLayout
import com.cuscus.ffmpeggui.core.stack.GraphNode
import com.cuscus.ffmpeggui.core.stack.GraphNodeKind
import com.cuscus.ffmpeggui.core.stack.NestedEditor
import com.cuscus.ffmpeggui.core.stack.NestedKind
import com.cuscus.ffmpeggui.core.stack.NestedPart
import com.cuscus.ffmpeggui.core.stack.StackGraph
import com.cuscus.ffmpeggui.core.stack.StackHop
import com.cuscus.ffmpeggui.core.stack.StackStep
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.expressive.springPress

/*
 * Editors in a stack (see core.stack and ARCHITECTURE.md), as the
 * editors show them, in Material 3 Expressive:
 *
 *  - OpenInEditorButton: the first tool of a selected part (a clip, a
 *    picture or video laid over, a sound; in the photo editor the picture
 *    itself too), unlike the others (a shape of its own in the tertiary
 *    tone, which blooms under the finger); it opens the "Open in" panel like
 *    any tool (OpenInPanel): a large card per editor the part can open in;
 *  - StackPath: while editors are stacked, the way to the one open now as a
 *    row of small badges in the top bar, under the file's name, out of the
 *    way of everything; tapped, it opens the map;
 *  - StackGraphSheet: the map of the editors as a calm tree, top down
 *    (core.stack.TreeLayout): the project, then every editor opened from a
 *    part under the editor it came from, the part told on the link; the
 *    stack open now lit; the map pinched and moved, nothing moving by itself;
 *    an editor tapped lights its neighbours and says what it is, with the
 *    way to it;
 *  - NestedApplyOverlay: what "Apply" is doing, and why it failed;
 *  - LeaveStackDialog: leaving editors without applying them, asked first
 *    when that loses changes.
 *
 * Shapes and colours tell the editors apart everywhere: the project a
 * four-sided cookie, the video editor a nine-sided one, the audio editor a
 * soft burst, the photo editor a flower, the GIF editor a sun. Motion is the theme's springs: spatial ones
 * for shapes and sizes, effects ones for colours and fades.
 */

/**
 * What an editor needs to open parts of its project (a clip, a picture or
 * video laid over, a sound) on top of itself, to apply, and to show the stack.
 */
class EditorStackEditing(
    /** This editor is open on top of another: it applies instead of exporting. */
    val nested: Boolean,
    /** Whether [NestedKind] can open for a part now. */
    val canOpen: (partId: String, kind: NestedKind) -> Boolean,
    val onOpen: (partId: String, kind: NestedKind) -> Unit,
    val onApply: () -> Unit,
    /** The editors of the stack, the project's first and this one last; empty when it is alone. */
    val steps: List<StackStep> = emptyList(),
    /** Opens the map of the editors. */
    val onShowMap: () -> Unit = {},
)

/** The shape of the editor of [kind] (null: the project's): one per editor, the same everywhere. */
internal fun stackShapeOf(kind: NestedKind?): RoundedPolygon = when (kind?.editor) {
    null -> MaterialShapes.Cookie4Sided
    NestedEditor.VIDEO -> MaterialShapes.Cookie9Sided
    NestedEditor.AUDIO -> MaterialShapes.SoftBurst
    NestedEditor.PHOTO -> MaterialShapes.Flower
    NestedEditor.GIF -> MaterialShapes.Sunny
}

internal fun stackIconOf(kind: NestedKind?): ImageVector = when (kind?.editor) {
    null -> Icons.Rounded.Folder
    NestedEditor.VIDEO -> Icons.Rounded.Movie
    NestedEditor.AUDIO -> Icons.Rounded.GraphicEq
    NestedEditor.PHOTO -> Icons.Rounded.Photo
    NestedEditor.GIF -> Icons.Rounded.Gif
}

/** The kind of part a link of the map comes from: a clip, something laid over, a sound. */
private fun partIconOf(kind: NestedKind?): ImageVector = when (kind?.part) {
    null, NestedPart.CLIP -> Icons.Rounded.ViewWeek
    NestedPart.OVERLAY -> Icons.Rounded.Layers
    NestedPart.SOUND -> Icons.Rounded.MusicNote
}

@StringRes
private fun labelOf(kind: NestedKind?): Int = when (kind) {
    null -> R.string.stack_level_project
    NestedKind.CLIP_VIDEO -> R.string.stack_level_clip_video
    NestedKind.CLIP_SOUND -> R.string.stack_level_clip_sound
    NestedKind.CLIP_PHOTO -> R.string.stack_level_clip_photo
    NestedKind.OVERLAY_PICTURE -> R.string.stack_level_overlay_picture
    NestedKind.OVERLAY_VIDEO -> R.string.stack_level_overlay_video
    NestedKind.OVERLAY_GIF -> R.string.stack_level_overlay_gif
    NestedKind.OVERLAY_SOUND -> R.string.stack_level_overlay_sound
    NestedKind.SOUND -> R.string.stack_level_sound
}

/** What the "Open in" card of [kind] says it opens, and what happens then. */
private fun openTextsOf(kind: NestedKind): Pair<Int, Int> = when (kind) {
    NestedKind.CLIP_VIDEO -> R.string.stack_open_clip to R.string.stack_open_clip_hint
    NestedKind.CLIP_SOUND -> R.string.stack_open_sound to R.string.stack_open_sound_hint
    NestedKind.CLIP_PHOTO -> R.string.stack_open_picture to R.string.stack_open_still_hint
    NestedKind.OVERLAY_PICTURE -> R.string.stack_open_picture to R.string.stack_open_layer_picture_hint
    NestedKind.OVERLAY_VIDEO -> R.string.stack_open_video to R.string.stack_open_layer_video_hint
    NestedKind.OVERLAY_GIF -> R.string.stack_open_gif to R.string.stack_open_layer_gif_hint
    NestedKind.OVERLAY_SOUND -> R.string.stack_open_sound to R.string.stack_open_layer_sound_hint
    NestedKind.SOUND -> R.string.stack_open_a_sound to R.string.stack_open_a_sound_hint
}

// -- Open in ---------------------------------------------------------------------------------------

/**
 * The first tool of a selected part (see OpenInPanel): opens the "Open in" panel ([onClick]).
 * Not like the tools after it: its icon sits in a nine-sided cookie of the
 * tertiary container, which blooms into a circle under the finger, and its
 * label is in the tertiary tone.
 */
@Composable
fun OpenInEditorButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight()
            .springPress(interaction, pressedScale = 0.9f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie9Sided,
            to = MaterialShapes.Circle,
            morphed = pressed,
            color = scheme.tertiaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            stringResource(R.string.stack_open),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = scheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The "Open in" panel of a selected part (a clip, a picture or video laid
 * over, a sound): a large card for each editor it, or a part of it, opens in
 * ([kinds]), its badge, what happens, and whether it was edited there before
 * ([edited]: it then opens with its edits). One that cannot open now is
 * greyed, and says why.
 */
@Composable
fun OpenInPanel(partId: String, kinds: List<NestedKind>, editing: EditorStackEditing, edited: NestedKind?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        kinds.forEach { kind ->
            OpenInCard(
                kind = kind,
                enabled = editing.canOpen(partId, kind),
                again = edited == kind,
                onOpen = { editing.onOpen(partId, kind) },
            )
        }
        PanelHint(stringResource(R.string.stack_open_hint))
    }
}

@Composable
private fun OpenInCard(kind: NestedKind, enabled: Boolean, again: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = nodeColor(kind, scheme)
    val content = nodeContentColor(kind, scheme)
    val accent = accentOf(kind, scheme)
    val (title, hint) = openTextsOf(kind)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .springPress(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(if (pressed) 36.dp else 28.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onOpen()
            }
            .padding(14.dp),
    ) {
        // Where it goes: this editor's badge, an arrow, the other editor's badge blooming.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            MorphingContainer(
                from = stackShapeOf(kind),
                to = MaterialShapes.Circle,
                morphed = pressed,
                color = container,
                modifier = Modifier.size(60.dp),
                rotationDegrees = if (pressed) 20f else 0f,
            ) {
                Icon(stackIconOf(kind), contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = scheme.onSurface,
            )
            Text(
                stringResource(if (enabled) hint else R.string.stack_open_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (again) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(CircleShape).background(container).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.stack_open_again), style = MaterialTheme.typography.labelMedium, color = content)
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = accent)
    }
}

// -- The way in the top bar --------------------------------------------------------------------------

/**
 * The editors of the stack as a row of small badges joined by short lines,
 * for the top bar: the one open now last, larger and lit. Long stacks show
 * the project, how many in between, and the last two.
 */
@Composable
fun StackPath(steps: List<StackStep>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val shown: List<Pair<Int, StackStep>?> = if (steps.size <= MAX_PATH_BADGES) {
        steps.mapIndexed { i, s -> i to s }
    } else {
        listOf(0 to steps.first(), null) + steps.takeLast(2).mapIndexed { i, s -> (steps.size - 2 + i) to s }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        shown.forEachIndexed { index, entry ->
            if (index > 0) Box(modifier = Modifier.padding(horizontal = 2.dp).size(width = 6.dp, height = 2.dp).clip(CircleShape).background(scheme.outline))
            if (entry == null) {
                Text("+${steps.size - 3}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            } else {
                val (level, step) = entry
                val current = level == steps.lastIndex
                val side by animateFloatAsState(if (current) 18f else 14f, motion.fastSpatialSpec(), label = "pathBadge")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(side.dp)
                        .clip(PolygonShape(stackShapeOf(step.kind)))
                        .background(if (current) scheme.primary else scheme.secondaryContainer),
                ) {
                    Icon(
                        stackIconOf(step.kind),
                        contentDescription = null,
                        tint = if (current) scheme.onPrimary else scheme.onSecondaryContainer,
                        modifier = Modifier.size((side * 0.6f).dp),
                    )
                }
            }
        }
    }
}

/** Most badges the way shows before it sums up the middle of the stack. */
private const val MAX_PATH_BADGES = 4

// -- The map ------------------------------------------------------------------------------------------

/**
 * The map of the editors ([graph]) over the editor, as a calm tree, top
 * down (core.stack.TreeLayout): the project at the top, each editor opened
 * from a part under the editor it was opened from, the part told on the
 * link ("#3" with the icon of a clip, a layer or a sound). The stack open now is the strong line,
 * the editor open now breathes; a part open but never applied says so.
 * Nothing moves by itself: the map is dragged and pinched, a node tapped
 * lights it and the editors next to it and says what it is, with the way
 * to it ([onGo]: the hops from the project to the editor to open).
 */
@Composable
fun StackGraphSheet(graph: StackGraph, onDismiss: () -> Unit, onGo: (List<StackHop>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = scheme.surfaceContainerLow,
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp),
        ) {
            var selected by remember(graph) { mutableIntStateOf(-1) }
            Box(modifier = Modifier.fillMaxSize()) {
                TreeMap(
                    graph = graph,
                    selected = selected,
                    onSelect = { index ->
                        haptic.performHapticFeedback(if (index >= 0) HapticFeedbackType.SegmentTick else HapticFeedbackType.ContextClick)
                        selected = index
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // The title over the map, and the way out.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainerLow.copy(alpha = 0.92f))
                        .padding(14.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(PolygonShape(MaterialShapes.Clover8Leaf)).background(scheme.primaryContainer)) {
                        Icon(Icons.Rounded.AccountTree, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.stack_map_title), style = MaterialTheme.typography.titleLargeEmphasized, color = scheme.onSurface)
                        val editors = graph.nodes.size
                        Text(
                            pluralStringResource(R.plurals.stack_map_count, editors, editors),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.stack_map_close))
                    }
                }
                // The legend and the hint, at the bottom while nothing is picked.
                AnimatedVisibility(
                    visible = selected < 0,
                    enter = fadeIn(motion.defaultEffectsSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Legend()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.stack_map_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                // What the node picked is, and the way to it.
                val picked = graph.nodes.getOrNull(selected)
                AnimatedVisibility(
                    visible = picked != null,
                    enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                    exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    // Kept while it leaves, so that it does not change on its way out.
                    val shown = remember { arrayOfNulls<GraphNode>(1) }
                    if (picked != null) shown[0] = picked
                    shown[0]?.let { node ->
                        NodeCard(node = node, onGo = { hops ->
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onGo(hops)
                        })
                    }
                }
            }
        }
    }
}

/** The small key of the map: what each shape is. */
@Composable
private fun Legend() {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        listOf<Pair<NestedKind?, Int>>(
            null to R.string.stack_level_project,
            NestedKind.CLIP_VIDEO to R.string.stack_legend_video,
            NestedKind.CLIP_SOUND to R.string.stack_legend_sound,
            NestedKind.OVERLAY_PICTURE to R.string.stack_legend_photo,
            NestedKind.OVERLAY_GIF to R.string.stack_legend_gif,
        )
            .forEach { (kind, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(CircleShape).background(scheme.surfaceContainerHighest).padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Box(modifier = Modifier.size(14.dp).clip(PolygonShape(stackShapeOf(kind))).background(nodeColor(kind, scheme)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
    }
}

/** The colour of the editor of [kind] (null: the project's), on its badges, cards and nodes. */
private fun nodeColor(kind: NestedKind?, scheme: androidx.compose.material3.ColorScheme): Color = when (kind?.editor) {
    null -> scheme.secondaryContainer
    NestedEditor.VIDEO -> scheme.primaryContainer
    NestedEditor.AUDIO -> scheme.tertiaryContainer
    NestedEditor.PHOTO -> scheme.tertiary
    NestedEditor.GIF -> scheme.secondary
}

private fun nodeContentColor(kind: NestedKind?, scheme: androidx.compose.material3.ColorScheme): Color = when (kind?.editor) {
    null -> scheme.onSecondaryContainer
    NestedEditor.VIDEO -> scheme.onPrimaryContainer
    NestedEditor.AUDIO -> scheme.onTertiaryContainer
    NestedEditor.PHOTO -> scheme.onTertiary
    NestedEditor.GIF -> scheme.onSecondary
}

/** The strong tone of the editor of [kind], for an arrow towards it. */
private fun accentOf(kind: NestedKind, scheme: androidx.compose.material3.ColorScheme): Color = when (kind.editor) {
    NestedEditor.VIDEO -> scheme.primary
    NestedEditor.AUDIO, NestedEditor.PHOTO -> scheme.tertiary
    NestedEditor.GIF -> scheme.secondary
}

/** The node picked: its badge, what it is, where it stands, and the way to it. */
@Composable
private fun NodeCard(node: GraphNode, onGo: (List<StackHop>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHighest,
        modifier = Modifier.padding(12.dp).widthIn(max = 480.dp).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clip(PolygonShape(stackShapeOf(node.nested))).background(nodeColor(node.nested, scheme)),
                ) {
                    Icon(stackIconOf(node.nested), contentDescription = null, tint = nodeContentColor(node.nested, scheme), modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(node.title, style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    val what = stringResource(labelOf(node.nested))
                    Text(
                        if (node.number > 0) {
                            val from = when (node.nested?.part) {
                                NestedPart.OVERLAY -> R.string.stack_node_from_layer
                                NestedPart.SOUND -> R.string.stack_node_from_sound
                                else -> R.string.stack_node_from_clip
                            }
                            stringResource(from, what, node.number)
                        } else {
                            what
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Where it stands: open now, on the stack, applied or not yet.
            val chips = buildList {
                if (node.current) add(R.string.stack_here) else if (node.level != null) add(R.string.stack_on_stack)
                if (node.kind == GraphNodeKind.NESTED) add(if (node.applied) R.string.stack_applied else R.string.stack_not_applied)
            }
            if (chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { label ->
                        val here = label == R.string.stack_here
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (here) scheme.onPrimary else scheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (here) scheme.primary else scheme.secondaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (node.current) {
                Text(stringResource(R.string.stack_here_text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            } else {
                Button(onClick = { onGo(node.path) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (node.level != null) R.string.stack_go_back else R.string.stack_go_open))
                }
            }
        }
    }
}

/**
 * The tree itself: real badges (the shapes of their editors, which bloom
 * when picked) on a sheet as large as the tree, the links drawn under them,
 * the sheet zoomed and moved as a whole. It starts fitted to the screen,
 * the editor open now in view.
 */
@Composable
private fun TreeMap(graph: StackGraph, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val nodes = graph.nodes
    val links = remember(graph) { graph.links }
    val placed = remember(graph) { TreeLayout.place(IntArray(nodes.size) { nodes[it].parent }) }
    val columns = (placed.maxX - placed.minX + 1f).coerceAtLeast(1f)
    val sheetWidth = COLUMN * columns
    val sheetHeight = TOP_ROOM + ROW * placed.rows.coerceAtLeast(1)
    fun centreOf(i: Int): Offset = with(density) {
        Offset(
            (COLUMN * (placed.x[i] - placed.minX) + COLUMN / 2).toPx(),
            (TOP_ROOM + ROW * placed.depth[i] + BADGE / 2).toPx(),
        )
    }
    var zoom by remember(graph) { mutableFloatStateOf(1f) }
    var pan by remember(graph) { mutableStateOf(Offset.Zero) }
    var fitted by remember(graph) { mutableStateOf(false) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val pulse by rememberInfiniteTransition(label = "mapPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "mapPulseValue",
    )
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { box ->
                if (fitted || box.width == 0) return@onSizeChanged
                // Fitted once: the whole tree when it fits, else the editor open now in the middle.
                val width = with(density) { sheetWidth.toPx() }
                val height = with(density) { sheetHeight.toPx() }
                val room = box.height - with(density) { (HEADER + FOOTER).toPx() }
                val fit = minOf(1f, box.width / width, room / height).coerceIn(MIN_ZOOM, 1f)
                val current = graph.currentIndex.coerceAtLeast(0)
                val focusX = if (width * fit <= box.width) width / 2f else centreOf(current).x
                zoom = fit
                pan = Offset(box.width / 2f - focusX * fit, with(density) { HEADER.toPx() })
                fitted = true
            }
            .pointerInput(graph) {
                detectTransformGestures { centroid, move, gain, _ ->
                    val next = (zoom * gain).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val under = (centroid - pan) / zoom
                    pan = centroid - under * next + move
                    zoom = next
                }
            }
            .pointerInput(graph) { detectTapGestures { currentOnSelect(-1) } },
    ) {
        Box(
            modifier = Modifier
                .size(sheetWidth, sheetHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
        ) {
            // A node picked lights it and its neighbours; the rest steps back.
            fun lit(i: Int): Boolean = selected < 0 || i == selected || graph.linked(i, selected)
            Canvas(modifier = Modifier.matchParentSize()) {
                val half = BADGE.toPx() / 2f
                links.forEach { (from, to) ->
                    val onStack = nodes[from].level != null && nodes[to].level != null
                    val start = centreOf(from) + Offset(0f, half)
                    val end = centreOf(to) - Offset(0f, half + 4.dp.toPx())
                    val bend = (end.y - start.y) / 2f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(start.x, start.y + bend, end.x, end.y - bend, end.x, end.y)
                    }
                    val strength = if (lit(from) && lit(to)) 1f else 0.3f
                    drawPath(
                        path,
                        color = (if (onStack) scheme.primary else scheme.outlineVariant).copy(alpha = strength),
                        style = Stroke(
                            width = (if (onStack) 3.dp else 2.dp).toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (nodes[to].applied) null else PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                        ),
                    )
                }
            }
            // On each link, the clip of the editor above it came from.
            links.forEach { (from, to) ->
                val node = nodes[to]
                if (node.number <= 0) return@forEach
                val mid = (centreOf(from) + centreOf(to)) / 2f
                val dim = !(lit(from) && lit(to))
                LinkLabel(
                    number = node.number,
                    kind = node.nested,
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((mid.x - with(density) { LABEL_WIDTH.toPx() } / 2f).toInt(), (mid.y - with(density) { 12.dp.toPx() }).toInt()) }
                        .alpha(if (dim) 0.3f else 1f),
                )
            }
            nodes.forEachIndexed { i, node ->
                val centre = centreOf(i)
                MapNode(
                    node = node,
                    picked = i == selected,
                    dim = !lit(i),
                    pulse = pulse,
                    order = placed.depth[i],
                    onClick = { currentOnSelect(i) },
                    modifier = Modifier.offset {
                        androidx.compose.ui.unit.IntOffset(
                            (centre.x - with(density) { NODE_WIDTH.toPx() } / 2f).toInt(),
                            (centre.y - with(density) { BADGE.toPx() } / 2f).toInt(),
                        )
                    },
                )
            }
        }
    }
}

/** The part an editor was opened on, on its link: what kind of part it is (a clip, a layer, a sound) and "#3", on a small pill. */
@Composable
private fun LinkLabel(number: Int, kind: NestedKind?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center, modifier = modifier.width(LABEL_WIDTH)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(CircleShape).background(scheme.surfaceContainerHighest).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Icon(partIconOf(kind), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("#$number", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * An editor on the map: its badge (the shape of its editor, blooming into a
 * circle when picked; the one open now larger, lit, breathing a ring), its
 * name under it and what it is.
 */
@Composable
private fun MapNode(
    node: GraphNode,
    picked: Boolean,
    dim: Boolean,
    pulse: Float,
    order: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fade by animateFloatAsState(if (dim) 0.32f else 1f, motion.defaultEffectsSpec(), label = "mapNodeFade")
    val description = stringResource(labelOf(node.nested)) + ", " + node.title
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(NODE_WIDTH)
            .popIn(true, order)
            .alpha(fade)
            .springPress(interaction, pressedScale = 0.92f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        val side = if (node.current) BADGE else BADGE - 10.dp
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(BADGE)) {
            if (node.current) {
                // The editor open now breathes out a ring.
                Box(
                    modifier = Modifier
                        .size(BADGE)
                        .graphicsLayer {
                            val grow = 0.9f + 0.45f * pulse
                            scaleX = grow
                            scaleY = grow
                            alpha = 0.45f * (1f - pulse)
                        }
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
            MorphingContainer(
                from = stackShapeOf(node.nested),
                to = MaterialShapes.Circle,
                morphed = picked || pressed,
                color = if (node.current) scheme.primary else nodeColor(node.nested, scheme),
                modifier = Modifier.size(side),
            ) {
                Icon(
                    stackIconOf(node.nested),
                    contentDescription = null,
                    tint = if (node.current) scheme.onPrimary else nodeContentColor(node.nested, scheme),
                    modifier = Modifier.size(side * 0.45f),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            node.title,
            style = if (node.current) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = if (node.current) scheme.primary else scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!node.applied) {
            Text(
                stringResource(R.string.stack_not_applied),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.tertiary,
                maxLines = 1,
            )
        }
    }
}

/** A column and a row of the tree, and the room above it and around it. */
private val COLUMN = 132.dp
private val ROW = 150.dp
private val TOP_ROOM = 16.dp
private val BADGE = 64.dp
private val NODE_WIDTH = 120.dp
private val LABEL_WIDTH = 64.dp
private val HEADER = 84.dp
private val FOOTER = 96.dp
private const val MIN_ZOOM = 0.4f
private const val MAX_ZOOM = 2.5f

// -- Apply and leave ----------------------------------------------------------------------------------

/**
 * What "Apply" does while it does it: the editor dimmed, a card with the
 * expressive loading shape, how far the render is and a way to stop it; or,
 * when it failed, why, with the way to try again.
 */
@Composable
fun NestedApplyOverlay(state: NestedApply, onStop: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(visible = state is NestedApply.Rendering, enter = fadeIn(motion.defaultEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
        // Takes every touch: nothing changes while the part is made.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.42f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Surface(shape = RoundedCornerShape(32.dp), color = scheme.surfaceContainerHigh, tonalElevation = 3.dp, modifier = Modifier.padding(32.dp).widthIn(max = 360.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp).clip(CircleShape).background(scheme.primaryContainer)) {
                        LoadingIndicator(color = scheme.onPrimaryContainer, modifier = Modifier.size(56.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.stack_applying), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.stack_applying_text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    val progress = (state as? NestedApply.Rendering)?.progress ?: 0f
                    val shown by animateFloatAsState(progress, motion.defaultEffectsSpec(), label = "applyProgress")
                    LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(onClick = onStop, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.stack_apply_stop))
                    }
                }
            }
        }
    }
    if (state is NestedApply.Failed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.stack_apply_failed_title)) },
            text = { Text(state.message ?: stringResource(R.string.stack_apply_failed_text)) },
            confirmButton = {
                Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.stack_apply_retry)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.stack_keep_editing)) }
            },
        )
    }
}

/**
 * Asks before editors of the stack are left without applying them, when
 * that loses changes: [editors] of them ([toTitle]: where it goes back to).
 */
@Composable
fun LeaveStackDialog(editors: Int, toTitle: String, onLeave: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.stack_leave_title)) },
        text = { Text(pluralStringResource(R.plurals.stack_leave_text, editors, editors, toTitle)) },
        confirmButton = {
            Button(onClick = onLeave, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.stack_leave_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onStay) { Text(stringResource(R.string.stack_keep_editing)) }
        },
    )
}
