package com.cuscus.ffmpeggui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.cuscus.ffmpeggui.core.model.TextFont
import com.cuscus.ffmpeggui.subtitles.SubtitleFonts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.core.subtitles.CueWord
import com.cuscus.ffmpeggui.core.subtitles.SubtitleBlock
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLayout
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLook
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle
import com.cuscus.ffmpeggui.subtitles.SubtitleFont
import kotlin.math.abs
import kotlin.math.roundToInt

/** The fonts the export draws captions and subtitles with (the same files). */
val LocalExportFonts = staticCompositionLocalOf<SubtitleFonts?> { null }

/**
 * The family of [font] as the export draws it: its very file (the bold one for
 * [bold] when there is one, else the regular one, made bold as the export does).
 * The system default while the fonts are not known.
 */
@Composable
internal fun rememberExportFont(font: TextFont, bold: Boolean): FontFamily {
    val fonts = LocalExportFonts.current ?: return FontFamily.Default
    return remember(fonts, font, bold) {
        val file = fonts.file(font, bold)
        if (file.isFile) {
            FontFamily(Font(file, weight = if (bold && file != fonts.file(font, false)) FontWeight.Bold else FontWeight.Normal))
        } else {
            FontFamily.Default
        }
    }
}

/*
 * Styled subtitles on the preview, drawn as libass will burn them (see
 * AssSubtitles in :core): the same blocks and lines, a line as high as the font
 * size, its baseline where the font's OS/2 metrics put it, the same font file,
 * the outline as a stroke of twice its width under the letters, the box around
 * each line's advance width, the shadow moved right and down.
 */

/**
 * The subtitle block on screen at [timeMs], on a frame that fills this layer.
 * With [editable] (the subtitles panel is open) the text can be dragged to a
 * new place; while nothing is said a faint sample shows where it goes.
 */
@Composable
internal fun StyledSubtitleLayer(
    blocks: List<SubtitleBlock>,
    style: SubtitleStyle,
    font: SubtitleFont?,
    timeMs: () -> Long,
    /** How much of the picture the fade to black lets through now (1 = all): it covers the subtitles, as in the export. */
    fadeLevel: () -> Float,
    editable: Boolean,
    sampleText: String,
    onStyle: (String?, (SubtitleStyle) -> SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    // Where the text was last drawn: the handle that drags it is laid there, so that
    // touches anywhere else go on to what is under the subtitles.
    var handle by remember { mutableStateOf(Rect.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val currentStyle by rememberUpdatedState(style)
    val currentOnStyle by rememberUpdatedState(onStyle)
    val family = remember(font?.file) {
        font?.let { FontFamily(Font(it.file, weight = if (it.facts.weight >= BOLD_WEIGHT) FontWeight.Bold else FontWeight.Normal)) } ?: FontFamily.SansSerif
    }
    val emPerSize = font?.facts?.emPerSize ?: SubtitleLayout.EM_PER_SIZE
    val baselinePerSize = font?.facts?.baselinePerSize ?: DEFAULT_BASELINE
    val sample = remember(sampleText, style.uppercase) {
        val text = if (style.uppercase) sampleText.uppercase() else sampleText
        SubtitleBlock(0, 1, listOf(text.split(' ').map { CueWord(it, 0, 1) }), timed = false)
    }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { frame = it }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = timeMs()
            val onScreen = SubtitleLayout.blockAt(blocks, now)
            val block = onScreen ?: (if (editable) blocks.firstOrNull() ?: sample else null)
            if (block == null) {
                if (!handle.isEmpty) handle = Rect.Zero
                return@Canvas
            }
            val ghost = onScreen == null
            // The fade level is how much of the picture shows (1 = all of it, 0 = black).
            val visibility = fadeLevel().coerceIn(0f, 1f) * (if (ghost) GHOST_ALPHA else 1f)
            val frameHeight = size.height
            val frameWidth = size.width
            val lineHeight = style.size * frameHeight * block.scale
            val emPx = lineHeight * emPerSize
            val border = style.border * frameHeight * block.scale
            val shadow = style.shadow * frameHeight * block.scale
            val lit = if (ghost) IntRange.EMPTY else SubtitleLayout.highlighted(block, now, style.highlight)
            val litColor = Color(style.highlightColor)
            val textStyle = TextStyle(
                fontFamily = family,
                fontSize = with(density) { emPx.toSp() },
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                color = Color(style.textColor),
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )

            val centerX = style.x * frameWidth
            val bottom = style.y * frameHeight
            val count = block.lines.size
            var first = 0
            // The outline is drawn from the same words without their colours: a word lit up
            // (its own colour in the text) would otherwise be stroked in that colour too and
            // swell into an unreadable blob, where libass strokes every letter in black. It is
            // a layout of its own (its style black): a layout shared with the letters would
            // carry the stroke, the colour and the alpha of one drawing into the other.
            val outlineStyle = textStyle.copy(color = Color(SubtitleStyle.OUTLINE_COLOR))
            val outlines = if (style.look == SubtitleLook.OUTLINE && border > 0f) {
                block.lines.map { line ->
                    measurer.measure(AnnotatedString(line.joinToString(" ") { it.text }), outlineStyle, overflow = TextOverflow.Visible, softWrap = false, maxLines = 1)
                }
            } else {
                emptyList()
            }
            val lines = block.lines.mapIndexed { index, line ->
                val text = lineText(line, first, lit, litColor)
                first += line.size
                val layout = measurer.measure(text, textStyle, overflow = TextOverflow.Visible, softWrap = false, maxLines = 1)
                val top = bottom - (count - index) * lineHeight
                val left = centerX - layout.size.width / 2f
                val topLeft = Offset(left, top + baselinePerSize * lineHeight - layout.firstBaseline)
                Triple(layout, topLeft, Rect(left, top, left + layout.size.width, top + lineHeight))
            }
            val area = lines.fold(Rect.Zero) { acc, (_, _, rect) ->
                if (acc.isEmpty) rect else Rect(minOf(acc.left, rect.left), minOf(acc.top, rect.top), maxOf(acc.right, rect.right), maxOf(acc.bottom, rect.bottom))
            }
            val reach = area.inflate(border)
            // Only a change of place is written: it lays the handle out again.
            if (reach != handle) handle = reach

            when (style.look) {
                SubtitleLook.BOX -> {
                    // One shape for all the boxes, so that where two lines' boxes overlap it is not darker.
                    val boxes = Path()
                    lines.forEach { (_, _, rect) -> boxes.addRect(rect.inflate(border)) }
                    val box = Color(SubtitleStyle.BOX_COLOR)
                    drawPath(boxes, color = box.copy(alpha = box.alpha * visibility))
                }
                SubtitleLook.OUTLINE -> if (border > 0f) {
                    val outline = Stroke(width = border * 2f, join = StrokeJoin.Round)
                    lines.forEachIndexed { index, (_, topLeft, _) ->
                        val plain = outlines.getOrNull(index) ?: return@forEachIndexed
                        drawText(plain, color = Color(SubtitleStyle.OUTLINE_COLOR), topLeft = topLeft, alpha = visibility, drawStyle = outline)
                    }
                }
                SubtitleLook.SHADOW -> lines.forEach { (layout, topLeft, _) ->
                    drawText(layout, color = Color(SubtitleStyle.SHADOW_COLOR), topLeft = topLeft + Offset(shadow, shadow), alpha = visibility, drawStyle = Fill)
                }
            }
            // Filled and in their colour, said outright rather than left to what the layout's
            // paint last held.
            lines.forEach { (layout, topLeft, _) ->
                drawText(layout, color = Color(style.textColor), topLeft = topLeft, alpha = visibility, drawStyle = Fill)
            }
        }

        if (editable && !handle.isEmpty && frame.width > 0 && frame.height > 0) {
            val slack = with(density) { HANDLE_SLACK.toPx() }
            val area = handle.inflate(slack)
            Box(
                modifier = Modifier
                    .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                    .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() })
                    .semantics { contentDescription = sampleText }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // The corner is the resize handle's.
                            val corner = 28.dp.toPx()
                            if (down.position.x > size.width - corner && down.position.y > size.height - corner) return@awaitEachGesture
                            down.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            var x = currentStyle.x
                            var y = currentStyle.y
                            var snapped = abs(x - 0.5f) < SNAP
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val moved = change.positionChange()
                                if (moved == Offset.Zero || change.isConsumed) continue
                                change.consume()
                                x = (x + moved.x / frame.width).coerceIn(0f, 1f)
                                y = (y + moved.y / frame.height).coerceIn(0f, 1f)
                                // The middle holds the text a little, with a tick as it gets there.
                                val nearMiddle = abs(x - 0.5f) < SNAP
                                if (nearMiddle && !snapped) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                snapped = nearMiddle
                                val shownX = if (nearMiddle) 0.5f else x
                                currentOnStyle("move") { it.copy(x = shownX, y = y) }
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                    },
            ) {
                // The corner resizes them, as a caption's does.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .pointerInput(Unit) {
                            var start = 0f
                            var total = 0f
                            detectDragGestures(
                                onDragStart = {
                                    start = currentStyle.size
                                    total = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragEnd = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                            ) { change, amount ->
                                change.consume()
                                total += amount.x + amount.y
                                val size = (start + total / (frame.height * 1.5f)).coerceIn(SubtitleStyle.MIN_SIZE, SubtitleStyle.MAX_SIZE)
                                currentOnStyle("size") { it.copy(size = size) }
                            }
                        },
                )
            }
        }
    }
}

/** One line, the words of [lit] (indexes in the whole block; [first] is this line's first) in [litColor]. */
private fun lineText(line: List<CueWord>, first: Int, lit: IntRange, litColor: Color): AnnotatedString = buildAnnotatedString {
    line.forEachIndexed { index, word ->
        if (index > 0) append(' ')
        if (first + index in lit) {
            pushStyle(SpanStyle(color = litColor))
            append(word.text)
            pop()
        } else {
            append(word.text)
        }
    }
}

private const val BOLD_WEIGHT = 600

/** Where the baseline sits in a line when the font is not known (Roboto's). */
private const val DEFAULT_BASELINE = 0.795f

/** The sample shown while editing and nothing is said is this see-through. */
private const val GHOST_ALPHA = 0.55f

/** The text sticks to the middle within this share of the frame width. */
private const val SNAP = 0.015f

/** The handle reaches this far around the text, for a finger. */
private val HANDLE_SLACK = 16.dp
