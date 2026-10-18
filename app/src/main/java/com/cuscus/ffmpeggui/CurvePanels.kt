package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.EaseCurve
import com.cuscus.ffmpeggui.core.model.KeyEasing
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import com.cuscus.ffmpeggui.core.model.SpeedPreset
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/*
 * Curves drawn with a finger: the speed along a clip and how a keyframe moves
 * on to the next. Both are a graph of nodes (drag one to move it, tap the empty
 * graph to add one there, tap a node twice to take it away) and a row of
 * ready-made curves.
 */

// -- The graph -----------------------------------------------------------------------------

/**
 * Nodes joined by a curve, edited by touch. [nodes] and everything else are in
 * the graph's own units: x 0..1 across, y as the model has it; [toLinear] turns
 * a y into the vertical scale it is drawn on (a logarithm for a speed) and
 * [fromLinear] back, the graph showing [linearRange] of that scale.
 *
 * The first and last node stay at the edges: [endsMove] says whether they can
 * still go up and down. [guides] are levels drawn across with their labels
 * ([strong] ones bolder); while a node is dragged it snaps to [snaps] and shows
 * [valueLabel] of its level. [marker] (read while drawing) is a position to
 * mark across, such as the playhead.
 */
@Composable
internal fun NodeGraph(
    nodes: List<Offset>,
    curveAt: (Float) -> Float,
    toLinear: (Float) -> Float,
    fromLinear: (Float) -> Float,
    linearRange: ClosedFloatingPointRange<Float>,
    endsMove: Boolean,
    guides: List<Pair<Float, String>>,
    strong: Set<Float>,
    snaps: List<Float>,
    valueLabel: (Float) -> String,
    onMove: (index: Int, x: Float, y: Float) -> Unit,
    onAdd: (x: Float, y: Float) -> Unit,
    onRemove: (index: Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    marker: () -> Float? = { null },
) {
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val currentNodes by rememberUpdatedState(nodes)
    val move by rememberUpdatedState(onMove)
    val add by rememberUpdatedState(onAdd)
    val remove by rememberUpdatedState(onRemove)
    val snapLevels by rememberUpdatedState(snaps)
    var dragged by remember { mutableIntStateOf(-1) }
    var lastTap by remember { mutableStateOf(-1 to 0L) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(GRAPH_HEIGHT)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                val inset = GRAPH_INSET.dp.toPx()
                val reach = TOUCH_RADIUS.dp.toPx()
                val slop = DRAG_SLOP.dp.toPx()
                fun width() = (size.width - 2 * inset).coerceAtLeast(1f)
                fun height() = (size.height - 2 * inset).coerceAtLeast(1f)
                fun screen(node: Offset) = Offset(
                    inset + node.x * width(),
                    inset + (1f - (toLinear(node.y) - linearRange.start) / (linearRange.endInclusive - linearRange.start)) * height(),
                )
                fun model(point: Offset): Offset {
                    val x = ((point.x - inset) / width()).coerceIn(0f, 1f)
                    val fraction = (1f - (point.y - inset) / height()).coerceIn(0f, 1f)
                    val linear = linearRange.start + fraction * (linearRange.endInclusive - linearRange.start)
                    return Offset(x, fromLinear(linear))
                }
                fun snapped(y: Float): Float {
                    val linear = toLinear(y)
                    val close = snapLevels.firstOrNull { abs(toLinear(it) - linear) < (linearRange.endInclusive - linearRange.start) * SNAP_SHARE }
                    return close ?: y
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val points = currentNodes
                    val hit = points.indices
                        .map { it to (screen(points[it]) - down.position).getDistance() }
                        .filter { it.second <= reach }
                        .minByOrNull { it.second }
                        ?.first
                    if (hit != null) down.consume()
                    var moved = false
                    var lastSnap: Float? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!moved && (change.position - down.position).getDistance() > slop) moved = true
                        if (moved && hit != null) {
                            change.consume()
                            dragged = hit
                            val at = model(change.position)
                            val y = snapped(at.y)
                            val snap = snapLevels.firstOrNull { it == y }
                            if (snap != null && snap != lastSnap) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lastSnap = snap
                            val fixedX = hit == 0 || hit == points.lastIndex
                            if (!(fixedX && !endsMove)) move(hit, if (fixedX) points[hit].x else at.x, y)
                        }
                    }
                    if (moved) {
                        if (hit != null) haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    } else if (hit != null) {
                        // A second tap on the same node takes it away (the ends stay).
                        val now = System.nanoTime()
                        val (index, time) = lastTap
                        if (index == hit && now - time < DOUBLE_TAP_NANOS && hit != 0 && hit != points.lastIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            remove(hit)
                            lastTap = -1 to 0L
                        } else {
                            lastTap = hit to now
                        }
                    } else {
                        val at = model(down.position)
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        add(at.x, snapped(at.y))
                        lastTap = -1 to 0L
                    }
                    dragged = -1
                }
            },
    ) {
        val inset = GRAPH_INSET.dp.toPx()
        val w = (size.width - 2 * inset).coerceAtLeast(1f)
        val h = (size.height - 2 * inset).coerceAtLeast(1f)
        val span = linearRange.endInclusive - linearRange.start
        fun yOf(value: Float) = inset + (1f - (toLinear(value) - linearRange.start) / span) * h
        fun xOf(x: Float) = inset + x * w

        drawRoundRect(
            color = scheme.surfaceContainerHighest,
            topLeft = Offset(inset / 2f, inset / 2f),
            size = Size(size.width - inset, size.height - inset),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
        // The levels, labelled on the left.
        for ((level, label) in guides) {
            val y = yOf(level)
            val bold = level in strong
            drawLine(
                color = scheme.outlineVariant.copy(alpha = if (bold) 1f else 0.6f),
                start = Offset(inset, y),
                end = Offset(inset + w, y),
                strokeWidth = if (bold) 1.5.dp.toPx() else 1.dp.toPx(),
                pathEffect = if (bold) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            val text = measurer.measure(label, style = labelStyle.copy(color = scheme.onSurfaceVariant))
            drawText(text, topLeft = Offset(inset + 4.dp.toPx(), (y - text.size.height - 1.dp.toPx()).coerceAtLeast(0f)))
        }
        // The curve, and the area between it and the strongest level.
        val base = strong.firstOrNull()?.let(::yOf)
        val steps = (w / 3f).toInt().coerceIn(24, 240)
        val line = Path()
        for (k in 0..steps) {
            val x = k.toFloat() / steps
            val point = Offset(xOf(x), yOf(curveAt(x)).coerceIn(0f, size.height))
            if (k == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
        }
        if (base != null) {
            val area = Path()
            area.addPath(line)
            area.lineTo(xOf(1f), base)
            area.lineTo(xOf(0f), base)
            area.close()
            drawPath(area, scheme.primary.copy(alpha = 0.14f))
        }
        drawPath(line, scheme.primary, style = Stroke(width = 3.dp.toPx()))
        // Where the playhead is.
        marker()?.let { m ->
            val x = xOf(m.coerceIn(0f, 1f))
            drawLine(scheme.tertiary, Offset(x, inset), Offset(x, inset + h), strokeWidth = 2.dp.toPx())
        }
        // The nodes; the one under the finger bigger, with its value.
        nodes.forEachIndexed { index, node ->
            val center = Offset(xOf(node.x), yOf(node.y))
            val active = index == dragged
            val radius = (if (active) 9 else 6).dp.toPx()
            drawCircle(scheme.surface, radius = radius + 2.dp.toPx(), center = center)
            drawCircle(if (active) scheme.tertiary else scheme.primary, radius = radius, center = center)
            if (active) {
                val text = measurer.measure(valueLabel(node.y), style = labelStyle.copy(color = scheme.onTertiaryContainer))
                val pad = 4.dp.toPx()
                val left = (center.x - text.size.width / 2f - pad).coerceIn(0f, size.width - text.size.width - 2 * pad)
                val top = (center.y - radius - text.size.height - 3 * pad).let { if (it < 0f) center.y + radius + pad else it }
                drawRoundRect(
                    scheme.tertiaryContainer,
                    topLeft = Offset(left, top),
                    size = Size(text.size.width + 2 * pad, text.size.height + pad),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawText(text, topLeft = Offset(left + pad, top + pad / 2f))
            }
        }
    }
}

// -- Speed curves ---------------------------------------------------------------------------

/** What each ready-made speed curve is called. */
@get:StringRes
internal val SpeedPreset.labelRes: Int
    get() = when (this) {
        SpeedPreset.MONTAGE -> R.string.speed_preset_montage
        SpeedPreset.HERO -> R.string.speed_preset_hero
        SpeedPreset.BULLET -> R.string.speed_preset_bullet
        SpeedPreset.JUMP_CUT -> R.string.speed_preset_jump_cut
        SpeedPreset.FLASH_IN -> R.string.speed_preset_flash_in
        SpeedPreset.FLASH_OUT -> R.string.speed_preset_flash_out
        SpeedPreset.RAMP_UP -> R.string.speed_preset_ramp_up
        SpeedPreset.RAMP_DOWN -> R.string.speed_preset_ramp_down
    }

private val LOG_MIN = ln(SpeedCurve.MIN_SPEED)
private val LOG_MAX = ln(SpeedCurve.MAX_SPEED)

/** "0.25x", "1x", "2.5x". */
internal fun speedText(speed: Float): String {
    val text = if (speed < 1f) String.format(Locale.US, "%.2f", speed) else String.format(Locale.US, "%.1f", speed)
    return text.trimEnd('0').trimEnd('.') + "x"
}

/**
 * The speed along a clip, drawn: 0.1x at the bottom, 10x at the top (a log
 * scale, so 0.5x and 2x are as far from 1x), with the ready-made curves and a
 * button that puts it back to normal speed.
 */
@Composable
internal fun SpeedCurveEditor(
    curve: SpeedCurve,
    onCurve: (SpeedCurve) -> Unit,
    marker: () -> Float? = { null },
) {
    val haptic = LocalHapticFeedback.current
    val chosen = SpeedPreset.entries.firstOrNull { it.curve.nodes == curve.nodes }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelHint(stringResource(R.string.speed_curve_hint), modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCurve(SpeedCurve.flat(1f))
                }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.action_reset))
                }
            }
            NodeGraph(
                nodes = curve.nodes.map { Offset(it.at, it.speed) },
                curveAt = { curve.speedAt(it.toDouble()) },
                toLinear = { ln(it.coerceAtLeast(SpeedCurve.MIN_SPEED)) },
                fromLinear = { exp(it).coerceIn(SpeedCurve.MIN_SPEED, SpeedCurve.MAX_SPEED) },
                linearRange = LOG_MIN..LOG_MAX,
                endsMove = true,
                guides = SPEED_GUIDES.map { it to speedText(it) },
                strong = setOf(1f),
                snaps = SPEED_GUIDES,
                valueLabel = ::speedText,
                onMove = { index, at, speed -> onCurve(curve.movedNode(index, at, speed)) },
                onAdd = { at, speed -> onCurve(curve.withNode(at, speed)) },
                onRemove = { index -> onCurve(curve.withoutNode(index)) },
                description = stringResource(R.string.speed_curve_description, speedText(curve.averageSpeed.toFloat())),
                marker = marker,
            )
            PanelHint(stringResource(R.string.speed_curve_average, speedText(curve.averageSpeed.toFloat())))
        }
        OptionTileRow(
            options = SpeedPreset.entries.map { preset ->
                TileOption(preset, stringResource(preset.labelRes), preview = { color -> SpeedCurveIcon(preset.curve, color) })
            },
            selected = chosen,
            onSelect = { preset -> onCurve(preset.curve) },
        )
    }
}

/** A small drawing of [curve] for a tile. */
@Composable
internal fun SpeedCurveIcon(curve: SpeedCurve, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(30.dp)) {
        drawMiniCurve(color) { x -> ((ln(curve.speedAt(x.toDouble())) - LOG_MIN) / (LOG_MAX - LOG_MIN)) }
    }
}

/** Levels drawn across the speed graph. */
private val SPEED_GUIDES = listOf(0.25f, 0.5f, 1f, 2f, 4f)

// -- Keyframe easings -----------------------------------------------------------------------

/** What each easing is called. */
@get:StringRes
internal val KeyEasing.labelRes: Int
    get() = when (this) {
        KeyEasing.LINEAR -> R.string.easing_linear
        KeyEasing.SMOOTH -> R.string.easing_smooth
        KeyEasing.EASE_IN -> R.string.easing_ease_in
        KeyEasing.EASE_OUT -> R.string.easing_ease_out
        KeyEasing.EASE_IN_OUT -> R.string.easing_ease_in_out
        KeyEasing.BACK -> R.string.easing_back
        KeyEasing.BOUNCE -> R.string.easing_bounce
        KeyEasing.ELASTIC -> R.string.easing_elastic
        KeyEasing.HOLD -> R.string.easing_hold
        KeyEasing.CUSTOM -> R.string.easing_custom
    }

/** Where an easing curve's graph reaches: a little below the first value and past the next. */
private const val EASE_LOW = -0.35f
private const val EASE_HIGH = 1.35f

/**
 * How the keyframe at the playhead moves on to the next: the ready-made
 * easings and, for "Custom", its curve to draw. [easing] null: the playhead is
 * not on a keyframe. [last]: there is no keyframe after it, so its easing does
 * nothing yet.
 */
@Composable
internal fun KeyEasingContent(
    easing: KeyEasing?,
    curve: EaseCurve?,
    last: Boolean,
    onEasing: (KeyEasing, EaseCurve?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (easing == null) {
            PanelHint(stringResource(R.string.easing_no_keyframe))
            return@Column
        }
        PanelHint(stringResource(if (last) R.string.easing_last_hint else R.string.easing_hint))
        OptionTileRow(
            options = KeyEasing.entries.map { option ->
                TileOption(option, stringResource(option.labelRes), preview = { color -> EaseIcon(option, curve, color) })
            },
            selected = easing,
            onSelect = { option ->
                // Custom starts from the shape chosen until then.
                val drawn = if (option == KeyEasing.CUSTOM) curve ?: EaseCurve.of(easing) else curve
                onEasing(option, drawn)
            },
        )
        if (easing == KeyEasing.CUSTOM) {
            val drawn = curve ?: EaseCurve.of(KeyEasing.SMOOTH)
            PanelCard {
                PanelHint(stringResource(R.string.easing_curve_hint))
                NodeGraph(
                    nodes = drawn.nodes.map { Offset(it.x, it.y) },
                    curveAt = { drawn.at(it.toDouble()).toFloat() },
                    toLinear = { it },
                    fromLinear = { it.coerceIn(EaseCurve.MIN_Y, EaseCurve.MAX_Y) },
                    linearRange = EASE_LOW..EASE_HIGH,
                    endsMove = false,
                    guides = listOf(0f to stringResource(R.string.easing_from), 1f to stringResource(R.string.easing_to)),
                    strong = setOf(0f, 1f),
                    snaps = listOf(0f, 0.5f, 1f),
                    valueLabel = { y -> String.format(Locale.US, "%d%%", Math.round(y * 100)) },
                    onMove = { index, x, y -> onEasing(KeyEasing.CUSTOM, drawn.movedNode(index, x, y)) },
                    onAdd = { x, y -> onEasing(KeyEasing.CUSTOM, drawn.withNode(x, y)) },
                    onRemove = { index -> onEasing(KeyEasing.CUSTOM, drawn.withoutNode(index)) },
                    description = stringResource(R.string.easing_curve_description),
                )
            }
        }
    }
}

/** A small drawing of [easing] ([curve] for a drawn one) for a tile or a button. */
@Composable
internal fun EaseIcon(easing: KeyEasing, curve: EaseCurve?, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(30.dp)) {
        drawMiniCurve(color) { x ->
            val y = Keyframes.ease(easing, x.toDouble(), curve ?: EaseCurve.of(KeyEasing.SMOOTH))
            ((y - EASE_LOW) / (EASE_HIGH - EASE_LOW)).toFloat()
        }
    }
}

/** [at] (0..1 across to 0..1 up) drawn as a line filling the canvas, with a faint base. */
private fun DrawScope.drawMiniCurve(color: Color, at: (Float) -> Number) {
    val stroke = 2.5.dp.toPx()
    val w = size.width - stroke
    val h = size.height - stroke
    val path = Path()
    val steps = 40
    for (k in 0..steps) {
        val x = k.toFloat() / steps
        val y = at(x).toFloat().coerceIn(0f, 1f)
        val px = stroke / 2f + x * w
        val py = stroke / 2f + (1f - y) * h
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawLine(color.copy(alpha = 0.3f), Offset(stroke / 2f, size.height - stroke / 2f), Offset(size.width - stroke / 2f, size.height - stroke / 2f), strokeWidth = 1.dp.toPx())
    drawPath(path, color, style = Stroke(width = stroke))
}

/** The button next to the keyframe diamond that opens [KeyEasingContent]: a small S curve. */
@Composable
internal fun EasingGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        drawMiniCurve(color) { x -> Keyframes.ease(KeyEasing.EASE_IN_OUT, x.toDouble()) }
    }
}

private val GRAPH_HEIGHT = 180.dp
private const val GRAPH_INSET = 14
private const val TOUCH_RADIUS = 28
private const val DRAG_SLOP = 6
private const val SNAP_SHARE = 0.025f
private const val DOUBLE_TAP_NANOS = 400_000_000L
