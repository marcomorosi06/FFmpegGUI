@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.textart

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a touch on the stage does, besides zooming. */
internal enum class StageTouch { NONE, PICK, BRUSH_HIDE, BRUSH_SHOW }

/** Where the art's own cells sit in the laid-out art (frame and margins around them). */
internal class ArtBox(val left: Int, val top: Int, val columns: Int, val rows: Int)

/**
 * The stage: the art in a terminal window. [view] shows the art alone, over
 * the picture ([overOpacity]) or beside it, a divider at [split] between the
 * two. A new kind of art ([revealKey]) types itself in, row after row (not
 * when animations are off). With [touch] a tap picks a colour ([onPick], at
 * the picture's fractions) or a drag paints with the brush ([onStroke]).
 */
@Composable
internal fun TextArtStage(
    picture: Bitmap?,
    art: ArtPicture?,
    box: ArtBox?,
    view: ArtView,
    overOpacity: Float,
    split: Float,
    onSplit: (Float) -> Unit,
    working: Boolean,
    revealKey: Any,
    reducedMotion: Boolean,
    title: String,
    sizeLabel: String,
    description: String,
    touch: StageTouch,
    brushRadius: Float,
    onPick: (Float, Float) -> Unit,
    onStroke: (List<Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val pictureImage = remember(picture) { picture?.asImageBitmap() }
    val artImage = remember(art) { art?.bitmap?.asImageBitmap() }
    val paper = art?.let { Color(it.paper) } ?: scheme.surfaceContainerLowest
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(revealKey) {
        if (reducedMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow))
        }
    }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // Back to the whole art when the view changes.
    LaunchedEffect(view) {
        zoom = 1f
        pan = Offset.Zero
    }
    val brushPoints = remember { mutableStateListOf<Offset>() }
    val currentTouch by rememberUpdatedState(touch)
    val currentView by rememberUpdatedState(view)
    val currentSplit by rememberUpdatedState(split)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHighest),
    ) {
        WindowBar(title = title, sizeLabel = sizeLabel, working = working, reducedMotion = reducedMotion)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(paper)
                .semantics { contentDescription = description },
        ) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val artSize = artImage?.let { Size(it.width.toFloat(), it.height.toFloat()) }
            val pictureSize = pictureImage?.let { Size(it.width.toFloat(), it.height.toFloat()) }
            // The art fills its own aspect; over or beside the picture, the picture's.
            val contentSize = when (view) {
                ArtView.ART -> artSize ?: pictureSize
                else -> pictureSize ?: artSize
            } ?: Size(1f, 1f)
            val content = fit(contentSize, boxW, boxH, inset = with(density) { 10.dp.toPx() })
            // Where the picture is on the stage (in the art view: the art's own cells, inside frame and margins).
            val pictureRect = if (view == ArtView.ART && art != null && box != null) {
                val sx = content.width / art.bitmap.width
                val sy = content.height / art.bitmap.height
                Rect(
                    content.left + box.left * art.cellW * sx,
                    content.top + box.top * art.cellH * sy,
                    content.left + (box.left + box.columns) * art.cellW * sx,
                    content.top + (box.top + box.rows) * art.cellH * sy,
                )
            } else {
                content
            }
            val currentPictureRect by rememberUpdatedState(pictureRect)
            fun toPicture(p: Offset): Offset {
                // Undo the zoom (about the middle) and the pan, then into the picture's fractions.
                val c = Offset(boxW / 2f, boxH / 2f)
                val q = c + (p - pan - c) / zoom
                val r = currentPictureRect
                return Offset(((q.x - r.left) / r.width).coerceIn(0f, 1f), ((q.y - r.top) / r.height).coerceIn(0f, 1f))
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(touch, view, boxW, boxH) {
                        when {
                            currentTouch == StageTouch.PICK -> detectTapGestures { p ->
                                val f = toPicture(p)
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onPick(f.x, f.y)
                            }
                            currentTouch == StageTouch.BRUSH_HIDE || currentTouch == StageTouch.BRUSH_SHOW -> detectDragGestures(
                                onDragStart = { p ->
                                    brushPoints.clear()
                                    brushPoints += toPicture(p)
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragEnd = {
                                    if (brushPoints.isNotEmpty()) onStroke(brushPoints.flatMap { listOf(it.x, it.y) })
                                    brushPoints.clear()
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                                onDragCancel = { brushPoints.clear() },
                            ) { change, _ ->
                                val f = toPicture(change.position)
                                val last = brushPoints.lastOrNull()
                                if (last == null || (f - last).getDistance() > 0.004f) brushPoints += f
                            }
                            currentView == ArtView.COMPARE -> detectHorizontalDragGestures(
                                onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                onDragEnd = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                            ) { change, _ ->
                                val r = currentPictureRect
                                val s = ((change.position.x - r.left) / r.width).coerceIn(0f, 1f)
                                if ((s * 50).roundToInt() != (currentSplit * 50).roundToInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                onSplit(s)
                            }
                            else -> detectTransformGestures { _, panChange, zoomChange, _ ->
                                val next = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                zoom = next
                                val limitX = boxW * (next - 1f) / 2f
                                val limitY = boxH * (next - 1f) / 2f
                                pan = Offset((pan.x + panChange.x).coerceIn(-limitX, limitX), (pan.y + panChange.y).coerceIn(-limitY, limitY))
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (currentTouch == StageTouch.NONE) {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                zoom = if (zoom > 1.01f) 1f else DOUBLE_TAP_ZOOM
                                pan = Offset.Zero
                            }
                        })
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
            ) {
                val shownArt = artImage
                when (view) {
                    ArtView.ART -> if (shownArt != null && art != null) {
                        drawArt(shownArt, art, content, reveal.value, null)
                    }
                    ArtView.OVER -> {
                        if (pictureImage != null) {
                            checkerboard(content)
                            drawImage(pictureImage, dstOffset = content.topLeftInt(), dstSize = content.sizeInt(), filterQuality = FilterQuality.Medium)
                        }
                        if (shownArt != null && art != null && box != null) {
                            drawArt(shownArt, art, content, reveal.value, box, alpha = overOpacity)
                        }
                    }
                    ArtView.COMPARE -> {
                        val divider = content.left + content.width * split
                        if (pictureImage != null) {
                            clipRect(left = content.left, top = content.top, right = divider, bottom = content.bottom) {
                                checkerboard(content)
                                drawImage(pictureImage, dstOffset = content.topLeftInt(), dstSize = content.sizeInt(), filterQuality = FilterQuality.Medium)
                            }
                        }
                        if (shownArt != null && art != null && box != null) {
                            clipRect(left = divider, top = content.top, right = content.right, bottom = content.bottom) {
                                drawRect(Color(art.paper), topLeft = Offset(divider, content.top), size = Size(content.right - divider, content.height))
                                drawArt(shownArt, art, content, reveal.value, box)
                            }
                        }
                        // The divider, with its handle.
                        val lineW = 3.dp.toPx()
                        drawLine(Color.White, Offset(divider, content.top), Offset(divider, content.bottom), strokeWidth = lineW)
                        val handle = Offset(divider, content.center.y)
                        drawCircle(Color.White, radius = 18.dp.toPx(), center = handle)
                        drawCircle(scheme.primary, radius = 18.dp.toPx(), center = handle, style = Stroke(width = 3.dp.toPx()))
                        val a = 6.dp.toPx()
                        val arrow = Path().apply {
                            moveTo(handle.x - a * 0.4f, handle.y - a); lineTo(handle.x - a * 1.4f, handle.y); lineTo(handle.x - a * 0.4f, handle.y + a)
                            moveTo(handle.x + a * 0.4f, handle.y - a); lineTo(handle.x + a * 1.4f, handle.y); lineTo(handle.x + a * 0.4f, handle.y + a)
                        }
                        drawPath(arrow, scheme.primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                // The stroke being painted.
                if (brushPoints.isNotEmpty()) {
                    val r = pictureRect
                    val path = Path()
                    brushPoints.forEachIndexed { i, f ->
                        val p = Offset(r.left + f.x * r.width, r.top + f.y * r.height)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    val color = if (touch == StageTouch.BRUSH_SHOW) scheme.tertiary else scheme.error
                    drawPath(path, color.copy(alpha = 0.45f), style = Stroke(width = brushRadius * r.width * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // Nothing yet: the expressive loading shape in the middle. Called by its full name:
            // inside the window's Column, the plain name would pick ColumnScope's overload,
            // which a box inside that column may not call.
            androidx.compose.animation.AnimatedVisibility(
                visible = artImage == null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
                modifier = Modifier.align(Alignment.Center),
            ) {
                LoadingIndicator(modifier = Modifier.size(96.dp))
            }
        }
    }
}

/** The bar of the terminal window: three lights, the file's name and size, a cursor that blinks. */
@Composable
private fun WindowBar(title: String, sizeLabel: String, working: Boolean, reducedMotion: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val blink = rememberInfiniteTransition(label = "cursor")
    // 0..2 each second: on for the first half, off for the second, as a terminal's cursor.
    val cursor by blink.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1060, easing = LinearEasing), RepeatMode.Restart),
        label = "cursorBlink",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
    ) {
        // The window's lights, as little cookies, the first a burst while the art is being made.
        listOf(scheme.error, scheme.tertiary, scheme.primary).forEachIndexed { i, color ->
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = MaterialShapes.SoftBurst,
                morphed = working && i == 0,
                color = color,
                modifier = Modifier.size(14.dp),
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(scheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(sizeLabel, style = MaterialTheme.typography.labelMediumEmphasized, fontFamily = FontFamily.Monospace, color = scheme.onSecondaryContainer, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(width = 9.dp, height = 18.dp)
                .graphicsLayer { alpha = if (reducedMotion || cursor < 1f) 1f else 0f }
                .background(scheme.primary),
        )
    }
}

/**
 * [image] (the laid-out art) drawn in [content], typed in as far as [reveal]
 * (0..1, row after row). With [box], only the art's own cells, stretched over
 * [content] (the picture), at [alpha].
 */
private fun DrawScope.drawArt(image: ImageBitmap, art: ArtPicture, content: Rect, reveal: Float, box: ArtBox?, alpha: Float = 1f) {
    val srcLeft = if (box == null) 0 else (box.left * art.cellW).roundToInt()
    val srcTop = if (box == null) 0 else (box.top * art.cellH).roundToInt()
    val srcRight = if (box == null) image.width else min(image.width, ((box.left + box.columns) * art.cellW).roundToInt())
    val srcBottom = if (box == null) image.height else min(image.height, ((box.top + box.rows) * art.cellH).roundToInt())
    val srcW = max(1, srcRight - srcLeft)
    val srcH = max(1, srcBottom - srcTop)
    if (reveal >= 0.999f) {
        drawImage(image, srcOffset = IntOffset(srcLeft, srcTop), srcSize = IntSize(srcW, srcH), dstOffset = content.topLeftInt(), dstSize = content.sizeInt(), alpha = alpha, filterQuality = FilterQuality.Medium)
        return
    }
    // Row after row, each sliding up into place as it fades in.
    val rows = if (box == null) art.rows else box.rows
    val scaleY = content.height / srcH
    val progress = reveal * (1f + REVEAL_STAGGER)
    for (r in 0 until rows) {
        val local = (progress - r.toFloat() / rows * REVEAL_STAGGER).coerceIn(0f, 1f)
        if (local <= 0f) break
        val top = srcTop + (r * art.cellH).roundToInt()
        val bottom = min(srcBottom, srcTop + ((r + 1) * art.cellH).roundToInt())
        if (bottom <= top) continue
        val dy = (1f - local) * art.cellH * scaleY * 0.9f
        drawImage(
            image,
            srcOffset = IntOffset(srcLeft, top),
            srcSize = IntSize(srcW, bottom - top),
            dstOffset = IntOffset(content.left.roundToInt(), (content.top + (top - srcTop) * scaleY + dy).roundToInt()),
            dstSize = IntSize(content.width.roundToInt(), max(1, ((bottom - top) * scaleY).roundToInt())),
            alpha = alpha * local,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** A checkerboard under the picture, where its cut-out shows through. */
private fun DrawScope.checkerboard(r: Rect) {
    val cell = 12.dp.toPx()
    drawRect(Color(0xFFF2F2F2), topLeft = r.topLeft, size = r.size)
    clipRect(r.left, r.top, r.right, r.bottom) {
        var y = r.top
        var row = 0
        while (y < r.bottom) {
            var x = r.left + if (row % 2 == 0) 0f else cell
            while (x < r.right) {
                drawRect(Color(0xFFD9D9D9), topLeft = Offset(x, y), size = Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

/** [size] fitted in a [w] x [h] box, centred, [inset] in from its edges. */
private fun fit(size: Size, w: Float, h: Float, inset: Float): Rect {
    val bw = (w - inset * 2).coerceAtLeast(1f)
    val bh = (h - inset * 2).coerceAtLeast(1f)
    val s = min(bw / size.width, bh / size.height)
    val cw = size.width * s
    val ch = size.height * s
    return Rect(Offset((w - cw) / 2f, (h - ch) / 2f), Size(cw, ch))
}

private fun Rect.topLeftInt() = IntOffset(left.roundToInt(), top.roundToInt())
private fun Rect.sizeInt() = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))

private const val MAX_ZOOM = 8f
private const val DOUBLE_TAP_ZOOM = 3f
private const val REVEAL_STAGGER = 1.4f
