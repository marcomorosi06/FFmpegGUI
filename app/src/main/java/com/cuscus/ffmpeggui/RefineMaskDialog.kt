@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import android.graphics.Bitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.animateColorAsState
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cuscus.ffmpeggui.cutout.MaskRefiner
import com.cuscus.ffmpeggui.cutout.RefineEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** The keep brush's colour (what is painted with it stays). */
private val KEEP_COLOR = Color(0xFF2E7D32)

/** The remove brush's colour (what is painted with it goes). */
private val REMOVE_COLOR = Color(0xFFC62828)

/** The light brush's colour (where it paints, the HDR look goes), and the one taking it away. */
private val LIGHT_COLOR = Color(0xFFFFA000)
private val DARK_COLOR = Color(0xFF5C6BC0)

/**
 * What the painted mask is for: a subject cut out of its picture (what is kept
 * shows, the rest goes), or where a look (the HDR one) is laid on the picture.
 */
internal enum class MaskKind { CUTOUT, LIGHT }

/** How the picture is shown while it is corrected. */
private enum class MaskView {
    /** What is kept over a checkerboard, what goes faint. */
    CUT,

    /** The whole picture, what goes (or, for a light mask, what is lit) tinted. */
    OVERLAY,
}

/**
 * Corrects a subject's cut-out, a stroke at a time (see MaskRefiner): with the
 * magic tool a tap or a rough stroke on something takes or leaves the whole of
 * it, edges included; with the precise brush exactly what it covers changes.
 * Green keeps, red removes (for a light mask, amber lights, blue takes it
 * away). While a finger paints, a ring shows the brush under it and a loupe
 * in a corner shows, larger, what it covers; the size slider shows the brush
 * on the picture. The picture can be seen cut out or as a whole with a tint,
 * and as it was while a button is held. Two fingers zoom and move the picture,
 * for the fine parts; Undo and Redo go back and forth over the corrections. Done
 * writes the corrected mask as a new file and hands its path to [onDone]; the
 * old mask is left as it was.
 */
@Composable
internal fun RefineMaskDialog(
    uri: Uri,
    maskPath: String,
    /** SlimSAM, for the magic tool; null when it is not set up (the precise brush only). */
    engine: RefineEngine?,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    /** What the dialog is for, at its top. */
    title: String = stringResource(R.string.refine_title),
    /** The tool it starts with. */
    startTool: MaskRefiner.Tool = MaskRefiner.Tool.MAGIC,
    /** What the mask is for: how it is shown, what the brushes are called. */
    kind: MaskKind = MaskKind.CUTOUT,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val refiner = remember(engine) { MaskRefiner(context, engine) }

    var opened by remember { mutableStateOf<MaskRefiner.Opened?>(null) }
    var studying by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var mask by remember { mutableStateOf<IntArray?>(null) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    // The mask as a tint over the whole picture (what goes, or what is lit).
    var tint by remember { mutableStateOf<ImageBitmap?>(null) }
    var view by remember { mutableStateOf(if (kind == MaskKind.LIGHT) MaskView.OVERLAY else MaskView.CUT) }
    // Held down: the picture as it was, untouched.
    var comparing by remember { mutableStateOf(false) }
    // Where the finger paints (on the screen), for the cursor and the loupe.
    var touch by remember { mutableStateOf<Offset?>(null) }
    // The size being set: the brush is shown in the middle of the picture for a moment.
    var sizing by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(if (engine == null) MaskRefiner.Tool.PRECISE else startTool) }
    var brush by remember { mutableStateOf(MaskRefiner.Brush.KEEP) }
    // The precise brush's size, as seen on the screen (zooming in makes it finer on the picture).
    var brushDp by remember { mutableFloatStateOf(DEFAULT_BRUSH_DP) }
    val edits = remember { mutableStateListOf<MaskRefiner.Edit>() }
    val undone = remember { mutableStateListOf<MaskRefiner.Edit>() }
    // The view: zoom and shift of the picture fitted in its box.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // The stroke under the finger, then while it is worked on: in the picture's pixels, with its look.
    var drawing by remember { mutableStateOf<DrawnStroke?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    /** Shows [newMask] cut out of the picture. */
    suspend fun show(picture: Bitmap, newMask: IntArray) {
        val (cut, tinted) = withContext(Dispatchers.Default) {
            cutOut(picture, newMask) to maskTint(newMask, picture.width, picture.height, kind)
        }
        shown = cut
        tint = tinted
        mask = newMask
    }

    /** Runs [block] as the one piece of work under way, saying what went wrong. */
    fun work(block: suspend () -> Unit) {
        if (working) return
        working = true
        scope.launch {
            try {
                block()
                problem = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefineEngine.NotEnoughMemory) {
                problem = R.string.cutout_problem_memory
            } catch (e: Exception) {
                android.util.Log.w("RefineMaskDialog", "Correction failed", e)
                problem = R.string.refine_failed
            } finally {
                working = false
                drawing = null
            }
        }
    }

    /** Puts [stroke] on the mask. */
    fun commit(stroke: MaskRefiner.Stroke) {
        val now = opened ?: return
        val current = mask ?: return
        val before = edits.toList()
        work {
            when (val result = refiner.apply(now, current, before, stroke)) {
                is MaskRefiner.Applied.Done -> {
                    edits.add(result.edit)
                    undone.clear()
                    show(now.picture, result.mask)
                    notice = null
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                MaskRefiner.Applied.NothingFound -> {
                    notice = R.string.refine_nothing_found
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                }
            }
        }
    }

    /** Shows the mask after the corrections kept now. */
    fun replay() {
        val now = opened ?: return
        val taken = edits.toList()
        work { show(now.picture, refiner.replay(now, taken)) }
    }

    LaunchedEffect(uri, maskPath) {
        try {
            val picture = refiner.open(uri, maskPath)
            opened = picture
            show(picture.picture, picture.base)
            refiner.study(picture)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaskRefiner.Unreadable) {
            problem = R.string.cutout_problem_unreadable
        } catch (e: RefineEngine.NotEnoughMemory) {
            problem = R.string.cutout_problem_memory
        } catch (e: RefineEngine.ModelMissing) {
            problem = R.string.refine_needs_model
        } catch (e: Exception) {
            android.util.Log.w("RefineMaskDialog", "Could not get ready", e)
            problem = R.string.refine_failed
        } finally {
            studying = false
        }
    }
    DisposableEffect(refiner) {
        onDispose { refiner.close(opened) }
    }

    val ready = opened != null && mask != null && !studying && !saving
    Dialog(onDismissRequest = { if (!saving) currentOnDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(scheme.surface)) {
            // The bar: close, what this is, undo, redo and done.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                IconButton(onClick = { currentOnDismiss() }, enabled = !saving) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        undone.add(edits.removeAt(edits.lastIndex))
                        notice = null
                        replay()
                    },
                    enabled = ready && !working && edits.isNotEmpty(),
                ) { Icon(Icons.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo)) }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        edits.add(undone.removeAt(undone.lastIndex))
                        notice = null
                        replay()
                    },
                    enabled = ready && !working && undone.isNotEmpty(),
                ) { Icon(Icons.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo)) }
                FilledIconButton(
                    onClick = {
                        val now = opened ?: return@FilledIconButton
                        if (edits.isEmpty()) {
                            currentOnDismiss()
                            return@FilledIconButton
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        val taken = edits.toList()
                        scope.launch {
                            saving = true
                            try {
                                onDone(refiner.save(now, taken).path)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("RefineMaskDialog", "Could not save the corrected mask", e)
                                problem = R.string.refine_failed
                                saving = false
                            }
                        }
                    },
                    enabled = ready && !working,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.refine_apply)) }
            }
            if (saving) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            val keepColor = if (kind == MaskKind.LIGHT) LIGHT_COLOR else KEEP_COLOR
            val removeColor = if (kind == MaskKind.LIGHT) DARK_COLOR else REMOVE_COLOR
            val brushColor by animateColorAsState(
                if (brush == MaskRefiner.Brush.KEEP) keepColor else removeColor,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "brushColor",
            )
            // The brush's size, shown in the middle of the picture while it is set.
            LaunchedEffect(brushDp) {
                if (sizing) {
                    delay(SIZE_PREVIEW_MS)
                    sizing = false
                }
            }
            val pictureArea: @Composable (Modifier) -> Unit = { modifier ->
                // The picture, framed in the brush's colour: the stroke being made, the cursor
                // and, under the finger, a loupe showing what it covers.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(scheme.surfaceContainerHighest)
                        .border(3.dp, brushColor.copy(alpha = if (ready) 0.9f else 0.25f), RoundedCornerShape(28.dp)),
                ) {
                    val picture = opened?.picture
                    if (picture != null) {
                        val whole = remember(picture) { picture.asImageBitmap() }
                        val light = scheme.surfaceContainerHigh
                        val dark = scheme.surfaceContainerLow
                        val checker = remember(picture, light, dark) { checkerboard(picture.width, picture.height, light, dark) }
                        val loupeRing = scheme.surface
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(picture, ready) {
                                    if (!ready) return@pointerInput
                                    awaitEachGesture {
                                        val first = awaitFirstDown(requireUnconsumed = false)
                                        // While a stroke is worked on, the picture can still be zoomed and moved.
                                        val canDraw = !working
                                        val (origin, fit) = fitted(size.width.toFloat(), size.height.toFloat(), picture.width, picture.height)
                                        val viewZoom = zoom
                                        val strokeTool = tool
                                        val strokeBrush = brush
                                        // The precise brush's radius in the picture's pixels, at this zoom.
                                        val radius = brushDp.dp.toPx() / 2f / (fit * viewZoom)
                                        fun onPicture(at: Offset): Offset {
                                            val box = (at - pan) / zoom
                                            return Offset((box.x - origin.x) / fit, (box.y - origin.y) / fit)
                                        }
                                        val points = ArrayList<Offset>()
                                        points += onPicture(first.position)
                                        var transforming = !canDraw
                                        if (canDraw) {
                                            drawing = DrawnStroke(points.toList(), strokeTool, strokeBrush, radius)
                                            touch = first.position
                                        }
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.count { it.pressed }
                                            if (pressed == 0) break
                                            if (pressed >= 2 || transforming) {
                                                // Two fingers: the stroke is given up, the picture zoomed and moved.
                                                if (!transforming) {
                                                    transforming = true
                                                    drawing = null
                                                    touch = null
                                                }
                                                if (!canDraw) {
                                                    if (pressed < 2) continue
                                                }
                                                if (pressed >= 2) {
                                                    val centroid = event.calculateCentroid(useCurrent = true)
                                                    val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                                                    val moved = centroid - (centroid - pan) * (newZoom / zoom) + event.calculatePan()
                                                    zoom = newZoom
                                                    pan = clampedPan(moved, newZoom, size.width.toFloat(), size.height.toFloat())
                                                }
                                                event.changes.forEach { it.consume() }
                                            } else {
                                                event.changes.firstOrNull { it.pressed }?.let { change ->
                                                    points += onPicture(change.position)
                                                    touch = change.position
                                                    change.consume()
                                                }
                                                drawing = DrawnStroke(points.toList(), strokeTool, strokeBrush, radius)
                                            }
                                        }
                                        touch = null
                                        if (transforming) {
                                            if (canDraw) drawing = null
                                        } else {
                                            val xy = FloatArray(points.size * 2)
                                            points.forEachIndexed { i, p ->
                                                xy[2 * i] = p.x.coerceIn(0f, picture.width - 1f)
                                                xy[2 * i + 1] = p.y.coerceIn(0f, picture.height - 1f)
                                            }
                                            commit(MaskRefiner.Stroke(strokeBrush, strokeTool, xy, radius))
                                        }
                                    }
                                },
                        ) {
                            val (origin, fit) = fitted(size.width, size.height, picture.width, picture.height)
                            val drawn = Size(picture.width * fit, picture.height * fit)
                            val dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt())
                            val dstSize = IntSize(drawn.width.toInt(), drawn.height.toInt())
                            // The picture as it is shown, on the box's own scale (the loupe draws it again, larger).
                            fun DrawScope.scene() {
                                withTransform({
                                    translate(pan.x, pan.y)
                                    scale(zoom, zoom, pivot = Offset.Zero)
                                }) {
                                    when {
                                        comparing -> drawImage(whole, dstOffset = dstOffset, dstSize = dstSize)
                                        view == MaskView.OVERLAY -> {
                                            drawImage(whole, dstOffset = dstOffset, dstSize = dstSize)
                                            tint?.let { drawImage(it, dstOffset = dstOffset, dstSize = dstSize) }
                                        }
                                        else -> {
                                            // The checkerboard of transparency: one small picture, its squares kept sharp.
                                            clipRect(dstOffset.x.toFloat(), dstOffset.y.toFloat(), (dstOffset.x + dstSize.width).toFloat(), (dstOffset.y + dstSize.height).toFloat()) {
                                                val cell = max(drawn.width, drawn.height) / CHECKER_CELLS
                                                drawImage(
                                                    checker,
                                                    dstOffset = dstOffset,
                                                    dstSize = IntSize(ceil(checker.width * cell).toInt(), ceil(checker.height * cell).toInt()),
                                                    filterQuality = FilterQuality.None,
                                                )
                                            }
                                            // What is cut away stays visible, faintly, so that it can be brought back.
                                            drawImage(whole, dstOffset = dstOffset, dstSize = dstSize, alpha = CUT_AWAY_ALPHA)
                                            shown?.let { drawImage(it, dstOffset = dstOffset, dstSize = dstSize) }
                                        }
                                    }
                                    // The stroke being made, then worked on.
                                    drawing?.let { stroke ->
                                        val color = if (stroke.brush == MaskRefiner.Brush.KEEP) keepColor else removeColor
                                        val width = if (stroke.tool == MaskRefiner.Tool.PRECISE) {
                                            stroke.radius * 2f * fit
                                        } else {
                                            MAGIC_STROKE_DP.dp.toPx() / zoom
                                        }
                                        if (stroke.points.size == 1) {
                                            val p = stroke.points[0]
                                            drawCircle(color.copy(alpha = 0.6f), radius = width / 2f, center = Offset(origin.x + p.x * fit, origin.y + p.y * fit))
                                        } else {
                                            val path = Path()
                                            stroke.points.forEachIndexed { i, p ->
                                                val px = origin.x + p.x * fit
                                                val py = origin.y + p.y * fit
                                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                            }
                                            drawPath(path, color.copy(alpha = 0.55f), style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                        }
                                    }
                                }
                            }
                            scene()

                            // The brush's own size, on the screen: a ring (light and dark, seen on any picture).
                            val brushRadius = if (tool == MaskRefiner.Tool.PRECISE) brushDp.dp.toPx() / 2f else MAGIC_STROKE_DP.dp.toPx() / 2f
                            fun DrawScope.ring(center: Offset, radius: Float) {
                                drawCircle(Color.Black.copy(alpha = 0.55f), radius = radius, center = center, style = Stroke(width = 3.dp.toPx()))
                                drawCircle(Color.White, radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
                            }
                            val finger = touch
                            if (finger != null) {
                                ring(finger, brushRadius)
                                // The loupe: what is under the finger, larger, in a corner away from it.
                                val loupe = LOUPE_RADIUS_DP.dp.toPx()
                                val margin = 12.dp.toPx() + loupe
                                val left = finger.x > size.width / 2f || finger.y > size.height / 2f
                                val centre = Offset(if (left) margin else size.width - margin, margin)
                                val circle = Path().apply { addOval(Rect(centre, loupe)) }
                                drawCircle(Color.Black.copy(alpha = 0.25f), radius = loupe + 4.dp.toPx(), center = centre + Offset(0f, 2.dp.toPx()))
                                clipPath(circle) {
                                    drawRect(dark, topLeft = centre - Offset(loupe, loupe), size = Size(loupe * 2, loupe * 2))
                                    withTransform({
                                        translate(centre.x - finger.x * LOUPE_ZOOM, centre.y - finger.y * LOUPE_ZOOM)
                                        scale(LOUPE_ZOOM, LOUPE_ZOOM, pivot = Offset.Zero)
                                    }) {
                                        scene()
                                    }
                                    ring(centre, brushRadius * LOUPE_ZOOM)
                                }
                                drawCircle(loupeRing, radius = loupe, center = centre, style = Stroke(width = 4.dp.toPx()))
                                drawCircle(brushColor, radius = loupe - 2.dp.toPx(), center = centre, style = Stroke(width = 2.dp.toPx()))
                            } else if (sizing && tool == MaskRefiner.Tool.PRECISE) {
                                val centre = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(brushColor.copy(alpha = 0.35f), radius = brushRadius, center = centre)
                                ring(centre, brushRadius)
                            }
                        }
                        // The view's own tools, floating over the bottom of the picture.
                        HorizontalFloatingToolbar(
                            expanded = true,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        ) {
                            if (kind == MaskKind.CUTOUT) {
                                IconToggleButton(
                                    checked = view == MaskView.OVERLAY,
                                    onCheckedChange = { on ->
                                        haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                        view = if (on) MaskView.OVERLAY else MaskView.CUT
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.Layers,
                                        contentDescription = stringResource(if (view == MaskView.OVERLAY) R.string.refine_view_cut else R.string.refine_view_overlay),
                                    )
                                }
                            }
                            // Held: the picture as it was.
                            val compare = remember { MutableInteractionSource() }
                            val holding by compare.collectIsPressedAsState()
                            LaunchedEffect(holding) {
                                if (holding != comparing) {
                                    comparing = holding
                                    if (holding) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                            }
                            IconButton(onClick = {}, interactionSource = compare) {
                                Icon(Icons.Rounded.Compare, contentDescription = stringResource(R.string.refine_compare))
                            }
                            // Back to the whole picture, once zoomed in.
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    zoom = 1f
                                    pan = Offset.Zero
                                },
                                enabled = zoom > 1.01f,
                            ) { Icon(Icons.Rounded.FitScreen, contentDescription = stringResource(R.string.refine_fit)) }
                        }
                        if (studying || working) {
                            ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center).size(64.dp))
                        }
                    }
                }
            }
            val controlsArea: @Composable ColumnScope.() -> Unit = {
                // What happens: a hint in a card, its badge in the brush's colour.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (problem != null) scheme.errorContainer else scheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    MorphingContainer(
                        from = MaterialShapes.Cookie9Sided,
                        to = MaterialShapes.Circle,
                        morphed = working || studying,
                        color = if (problem != null) scheme.error else brushColor,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (tool == MaskRefiner.Tool.MAGIC) Icons.Rounded.AutoFixHigh else Icons.Rounded.Brush,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            problem ?: notice ?: when {
                                studying -> R.string.refine_studying
                                saving -> R.string.refine_saving
                                working && tool == MaskRefiner.Tool.MAGIC -> R.string.refine_working
                                kind == MaskKind.LIGHT -> when {
                                    tool == MaskRefiner.Tool.MAGIC && brush == MaskRefiner.Brush.KEEP -> R.string.light_hint_magic_add
                                    tool == MaskRefiner.Tool.MAGIC -> R.string.light_hint_magic_remove
                                    brush == MaskRefiner.Brush.KEEP -> R.string.light_hint_precise_add
                                    else -> R.string.light_hint_precise_remove
                                }
                                tool == MaskRefiner.Tool.MAGIC && brush == MaskRefiner.Brush.KEEP -> R.string.refine_hint_magic_keep
                                tool == MaskRefiner.Tool.MAGIC -> R.string.refine_hint_magic_remove
                                brush == MaskRefiner.Brush.KEEP -> R.string.refine_hint_precise_keep
                                else -> R.string.refine_hint_precise_remove
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (problem != null) scheme.onErrorContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // The tool: magic or precise (without the model, the precise brush is the only one).
                if (engine != null) {
                    ConnectedPair(
                        first = Choice(stringResource(R.string.refine_tool_magic), Icons.Rounded.AutoFixHigh),
                        second = Choice(stringResource(R.string.refine_tool_precise), Icons.Rounded.Brush),
                        firstChosen = tool == MaskRefiner.Tool.MAGIC,
                        onChoose = { magic ->
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            tool = if (magic) MaskRefiner.Tool.MAGIC else MaskRefiner.Tool.PRECISE
                            notice = null
                        },
                        height = 48.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // The brush: what it does, in its colour.
                ConnectedPair(
                    first = Choice(stringResource(if (kind == MaskKind.LIGHT) R.string.light_brush_add else R.string.refine_brush_keep), Icons.Rounded.Add, keepColor),
                    second = Choice(stringResource(if (kind == MaskKind.LIGHT) R.string.light_brush_remove else R.string.refine_brush_remove), Icons.Rounded.Remove, removeColor),
                    firstChosen = brush == MaskRefiner.Brush.KEEP,
                    onChoose = { keep ->
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        brush = if (keep) MaskRefiner.Brush.KEEP else MaskRefiner.Brush.REMOVE
                        notice = null
                    },
                    height = 56.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                // The precise brush's size, shown on the picture while it is set.
                AnimatedVisibility(
                    visible = tool == MaskRefiner.Tool.PRECISE,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                ) {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                        ValueSlider(
                            label = stringResource(R.string.refine_brush_size),
                            valueText = stringResource(R.string.value_dp, brushDp.roundToInt()),
                            value = brushDp,
                            onValueChange = {
                                sizing = true
                                brushDp = it
                            },
                            valueRange = MIN_BRUSH_DP..MAX_BRUSH_DP,
                            icon = Icons.Rounded.LineWeight,
                            defaultValue = DEFAULT_BRUSH_DP,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        undone.clear()
                        undone.addAll(edits.asReversed())
                        edits.clear()
                        notice = null
                        replay()
                    },
                    enabled = ready && !working && edits.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.refine_reset))
                }
            }
            if (LocalWindowLayout.current.let { it.isLandscape && it.width >= SIDE_CONTROLS_MIN_WIDTH }) {
                // On its side (or a large screen): the picture takes the height, the tools are beside it.
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    pictureArea(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.width(SIDE_CONTROLS_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    ) {
                        controlsArea()
                    }
                }
            } else {
                pictureArea(Modifier.weight(1f).fillMaxWidth())
                controlsArea()
            }
        }
    }
}

/** A stroke as drawn: its points in the picture's pixels, its tool and brush, the precise brush's radius in those pixels. */
private class DrawnStroke(val points: List<Offset>, val tool: MaskRefiner.Tool, val brush: MaskRefiner.Brush, val radius: Float)

/** One of a [ConnectedPair]: its name, its icon, and the colour it fills with when chosen (the theme's when null). */
private class Choice(val label: String, val icon: ImageVector, val color: Color? = null)

/**
 * Two choices as a connected button group (Material 3 Expressive): the chosen
 * one filled (in its own colour, when it has one) and rounder, both squishing
 * under the finger.
 */
@Composable
private fun ConnectedPair(
    first: Choice,
    second: Choice,
    firstChosen: Boolean,
    onChoose: (first: Boolean) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        listOf(first to true, second to false).forEach { (choice, isFirst) ->
            val chosen = firstChosen == isFirst
            val colors = if (choice.color != null) {
                ToggleButtonDefaults.colors(checkedContainerColor = choice.color, checkedContentColor = Color.White)
            } else {
                ToggleButtonDefaults.colors()
            }
            ToggleButton(
                checked = chosen,
                onCheckedChange = { onChoose(isFirst) },
                shapes = if (isFirst) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors = colors,
                modifier = Modifier.weight(1f).height(height).semantics { role = Role.RadioButton },
            ) {
                Icon(choice.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(choice.label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
            }
        }
    }
}

/** A tint the size of the picture: for a cut-out what goes in red, for a light mask what is lit in amber; [mask] 0..255. */
private fun maskTint(mask: IntArray, width: Int, height: Int, kind: MaskKind): ImageBitmap {
    val color = (if (kind == MaskKind.LIGHT) LIGHT_COLOR else REMOVE_COLOR).toArgb() and 0xFFFFFF
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val m = mask[i].coerceIn(0, 255)
        val share = if (kind == MaskKind.LIGHT) m else 255 - m
        pixels[i] = color or ((share * TINT_ALPHA).toInt() shl 24)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Where a picture of [width] x [height] lies fitted in a box of [boxWidth] x [boxHeight]: its top left and its scale. */
private fun fitted(boxWidth: Float, boxHeight: Float, width: Int, height: Int): Pair<Offset, Float> {
    val scale = min(boxWidth / width, boxHeight / height)
    return Offset((boxWidth - width * scale) / 2f, (boxHeight - height * scale) / 2f) to scale
}

/** [pan] kept so that, at [zoom], the box of [width] x [height] stays covered by the zoomed picture's box. */
private fun clampedPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset =
    Offset(pan.x.coerceIn(width * (1f - zoom), 0f), pan.y.coerceIn(height * (1f - zoom), 0f))

/** A checkerboard of [CHECKER_CELLS] squares along the longer side of a [width] x [height] picture, one pixel a square. */
private fun checkerboard(width: Int, height: Int, light: Color, dark: Color): ImageBitmap {
    val long = max(width, height).toFloat()
    val columns = ceil(CHECKER_CELLS * width / long).toInt().coerceAtLeast(1)
    val rows = ceil(CHECKER_CELLS * height / long).toInt().coerceAtLeast(1)
    val a = light.toArgb()
    val b = dark.toArgb()
    val pixels = IntArray(columns * rows) { i -> if ((i % columns + i / columns) % 2 == 0) a else b }
    return Bitmap.createBitmap(pixels, columns, rows, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** [picture] with [mask] (0..255 per pixel) as its alpha. */
private fun cutOut(picture: Bitmap, mask: IntArray): ImageBitmap {
    val pixels = IntArray(picture.width * picture.height)
    picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
    for (i in pixels.indices) pixels[i] = (pixels[i] and 0xFFFFFF) or (mask[i].coerceIn(0, 255) shl 24)
    return Bitmap.createBitmap(pixels, picture.width, picture.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** How much of the picture the cut away parts show with, so that they can be painted back. */
private const val CUT_AWAY_ALPHA = 0.28f

/** Squares of the checkerboard along the picture's longer side. */
private const val CHECKER_CELLS = 40f

/** How far the picture can be zoomed in. */
private const val MAX_ZOOM = 8f

/** Width on the screen of a magic stroke as drawn. */
private const val MAGIC_STROKE_DP = 18f

/** The precise brush's sizes on the screen. */
private const val MIN_BRUSH_DP = 6f
private const val MAX_BRUSH_DP = 64f
private const val DEFAULT_BRUSH_DP = 24f

/** How strong the tint over the picture is, at its strongest. */
private const val TINT_ALPHA = 0.55f

/** The loupe: its radius on the screen, and how much larger it shows. */
private const val LOUPE_RADIUS_DP = 64f
private const val LOUPE_ZOOM = 2.5f

/** How long the brush shows in the middle of the picture after its size is set. */
private const val SIZE_PREVIEW_MS = 900L

/** From this width a dialog wider than it is tall puts its tools beside the picture, this wide. */
private val SIDE_CONTROLS_MIN_WIDTH = 600.dp
private val SIDE_CONTROLS_WIDTH = 380.dp