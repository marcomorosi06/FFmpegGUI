@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.svgedit

import com.cuscus.ffmpeggui.ui.expressive.peeksSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.CompactSlider
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.svg.GradientSpec
import com.cuscus.ffmpeggui.core.vector.svg.GradientStop
import com.cuscus.ffmpeggui.core.vector.svg.PaintKind
import com.cuscus.ffmpeggui.core.vector.svg.SvgPaint
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * A fill or an outline in the look sheet: nothing, a colour, or a gradient.
 * A gradient is edited on a bar of its stops (a tap adds one, a stop is
 * dragged along it), its kind (along a line or out from the middle), its
 * angle, and the chosen stop's colour, opacity and place; a tile shows it as
 * it paints. Every change shows on the stage at once.
 */

/** What paints a fill or an outline. */
private enum class PaintMode(val label: Int) {
    NONE(R.string.svg_paint_none),
    COLOR(R.string.svg_paint_color),
    GRADIENT(R.string.svg_paint_gradient),
}

/**
 * A fill or an outline: [paint] as it is resolved, and [gradient] when it
 * names one of the drawing's; [allowGradient] false where a gradient cannot
 * show (a line has no box to lay it across). [onGradient] gets the name of
 * what changed (a run of the same is one step of undo) and the gradient.
 */
@Composable
internal fun PaintCard(
    title: String,
    paint: SvgPaint,
    gradient: GradientSpec?,
    swatches: List<Long>,
    onPaint: (SvgPaint) -> Unit,
    onGradient: (String, GradientSpec) -> Unit,
    allowGradient: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    val mode = when {
        paint.kind == PaintKind.NONE -> PaintMode.NONE
        paint.kind == PaintKind.URL && gradient != null && allowGradient -> PaintMode.GRADIENT
        // A pattern, or a gradient that is not there: neither of the editor's own.
        paint.kind == PaintKind.URL -> null
        else -> PaintMode.COLOR
    }
    // The colour a change of kind starts from.
    val base = when {
        paint.kind == PaintKind.COLOR -> paint.argb
        gradient != null -> gradient.stops.first().argb or 0xFF000000L
        else -> swatches.first()
    }
    PanelCard {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
        ConnectedChoiceGroup<PaintMode?>(
            options = if (allowGradient) PaintMode.entries else listOf(PaintMode.NONE, PaintMode.COLOR),
            selected = mode,
            label = { stringResource(it!!.label) },
            onSelect = { chosen ->
                when (chosen) {
                    PaintMode.NONE -> onPaint(SvgPaint.NONE)
                    PaintMode.COLOR -> onPaint(SvgPaint.color(base))
                    PaintMode.GRADIENT -> if (mode != PaintMode.GRADIENT) onGradient("kind", GradientSpec.between(base, partnerOf(base)))
                    null -> Unit
                }
            },
        )
        if (mode == null) PanelHint(stringResource(R.string.svg_paint_pattern))
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "paintMode",
        ) { shown ->
            when (shown) {
                PaintMode.COLOR -> ColorSwatchRow(
                    colors = swatches,
                    selected = if (paint.kind == PaintKind.COLOR) paint.argb else 0L,
                    onSelect = { onPaint(SvgPaint.color(it)) },
                    noneSelected = paint.kind != PaintKind.COLOR,
                )
                PaintMode.GRADIENT -> if (gradient != null) GradientEditor(gradient, swatches, onGradient) else Spacer(Modifier)
                else -> Spacer(Modifier)
            }
        }
    }
}

/** A second colour for a new gradient: [argb] lighter, or darker when it is already light. */
private fun partnerOf(argb: Long): Long {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val light = (r * 299 + g * 587 + b * 114) / 1000 > 170
    fun mix(c: Long): Long = if (light) c * 45 / 100 else c + (255 - c) * 60 / 100
    return 0xFF000000L or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
}

/** At most this many stops. */
private const val MAX_STOPS = 8

/** The gradient's bar of stops, its kind and angle, and its chosen stop. */
@Composable
private fun GradientEditor(given: GradientSpec, swatches: List<Long>, onGradient: (String, GradientSpec) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    // A gradient of one stop from a file is edited as two of that colour.
    val spec = if (given.stops.size >= 2) given else given.copy(stops = listOf(GradientStop(0.0, given.stops.first().argb), GradientStop(1.0, given.stops.first().argb)))
    var chosen by remember { mutableIntStateOf(0) }
    val index = chosen.coerceIn(0, spec.stops.lastIndex)
    val stop = spec.stops[index]
    fun withStop(at: Int, changed: GradientStop): GradientSpec =
        spec.copy(stops = spec.stops.toMutableList().also { it[at] = changed })
    /** Stop [at] moved to [offset], kept between its neighbours so that the stops keep their order. */
    fun moved(at: Int, offset: Double): GradientSpec {
        val low = spec.stops.getOrNull(at - 1)?.offset ?: 0.0
        val high = spec.stops.getOrNull(at + 1)?.offset ?: 1.0
        return withStop(at, spec.stops[at].copy(offset = offset.coerceIn(low, high)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientTile(spec, Modifier.size(56.dp))
            ConnectedChoiceGroup(
                options = listOf(false, true),
                selected = spec.radial,
                label = { stringResource(if (it) R.string.svg_gradient_radial else R.string.svg_gradient_linear) },
                onSelect = { radial -> if (radial != spec.radial) onGradient("kind", spec.copy(radial = radial)) },
                modifier = Modifier.weight(1f),
            )
        }
        StopBar(
            spec = spec,
            chosen = index,
            onChoose = { chosen = it },
            onMove = { at, offset -> onGradient("stopMove", moved(at, offset.toDouble())) },
            onAdd = { offset ->
                if (spec.stops.size < MAX_STOPS) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val added = GradientStop(offset.toDouble(), colourAt(spec, offset.toDouble()))
                    val list = (spec.stops + added).sortedBy { it.offset }
                    chosen = list.indexOfFirst { it === added }
                    onGradient("stopAdd", spec.copy(stops = list))
                }
            },
        )
        AnimatedVisibility(
            visible = !spec.radial,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            CompactSlider(
                label = stringResource(R.string.svg_gradient_angle),
                valueText = stringResource(R.string.value_degrees, spec.angle.roundToInt()),
                value = spec.angle.toFloat().coerceIn(0f, 360f),
                // Rests on every 15 degrees, as turning does on the stage.
                onValueChange = { v -> onGradient("angle", spec.copy(angle = ((v / 15f).roundToInt() * 15f).toDouble() % 360.0)) },
                valueRange = 0f..360f,
                defaultValue = 0f,
            )
        }

        // -- The chosen stop ---------------------------------------------------------------
        Text(
            stringResource(R.string.svg_gradient_stop, index + 1, spec.stops.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorSwatchRow(
            colors = swatches,
            selected = stop.argb or 0xFF000000L,
            onSelect = { c -> onGradient("stopColour", withStop(index, stop.copy(argb = (c and 0xFFFFFFL) or (stop.argb and 0xFF000000L)))) },
        )
        val opacity = ((stop.argb ushr 24) and 0xFF) / 255f
        CompactSlider(
            label = stringResource(R.string.svg_gradient_stop_opacity),
            valueText = stringResource(R.string.value_percent, (opacity * 100).roundToInt()),
            value = opacity,
            onValueChange = { v ->
                val alpha = (v * 255).roundToInt().coerceIn(0, 255).toLong()
                onGradient("stopOpacity", withStop(index, stop.copy(argb = (stop.argb and 0xFFFFFFL) or (alpha shl 24))))
            },
            valueRange = 0f..1f,
            defaultValue = 1f,
        )
        CompactSlider(
            label = stringResource(R.string.svg_gradient_stop_position),
            valueText = stringResource(R.string.value_percent, (stop.offset * 100).roundToInt()),
            value = stop.offset.toFloat(),
            onValueChange = { v -> onGradient("stopMove", moved(index, v.toDouble())) },
            valueRange = 0f..1f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onGradient("reverse", spec.reversed())
                    chosen = spec.stops.lastIndex - index
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.svg_gradient_reverse), maxLines = 1)
            }
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onGradient("stopRemove", spec.copy(stops = spec.stops.filterIndexed { i, _ -> i != index }))
                    chosen = (index - 1).coerceAtLeast(0)
                },
                enabled = spec.stops.size > 2,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.svg_gradient_remove_stop), maxLines = 1)
            }
        }
        PanelHint(stringResource(R.string.svg_gradient_hint))
    }
}

/** The colour of [spec] at [offset], its opacity included (between the stops either side). */
private fun colourAt(spec: GradientSpec, offset: Double): Long {
    val stops = spec.stops
    if (offset <= stops.first().offset) return stops.first().argb
    if (offset >= stops.last().offset) return stops.last().argb
    val after = stops.indexOfFirst { it.offset >= offset }
    val a = stops[after - 1]
    val b = stops[after]
    val f = if (b.offset == a.offset) 0.0 else (offset - a.offset) / (b.offset - a.offset)
    fun channel(shift: Int): Long {
        val ca = (a.argb ushr shift) and 0xFF
        val cb = (b.argb ushr shift) and 0xFF
        return (ca + (cb - ca) * f).roundToInt().toLong().coerceIn(0, 255) shl shift
    }
    return channel(24) or channel(16) or channel(8) or channel(0)
}

/** The stops of [spec] as Compose's colour stops. */
private fun colourStops(spec: GradientSpec): Array<Pair<Float, Color>> =
    spec.stops.map { it.offset.toFloat() to Color(it.argb.toInt()) }.toTypedArray()

/** Squares behind what may be see-through. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.checkerboard(light: Color, dark: Color, cell: Float) {
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(dark, topLeft = Offset(x, y), size = Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

/** [spec] as it paints a square: along its angle across the box, or out from its middle. */
@Composable
private fun GradientTile(spec: GradientSpec, modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val dark = MaterialTheme.colorScheme.outlineVariant
    val cell = with(LocalDensity.current) { 6.dp.toPx() }
    Canvas(modifier = modifier.clip(RoundedCornerShape(16.dp))) {
        checkerboard(light, dark, cell)
        val brush = if (spec.radial) {
            Brush.radialGradient(*colourStops(spec), center = center, radius = size.minDimension / 2)
        } else {
            val radians = Math.toRadians(spec.angle)
            val dx = cos(radians).toFloat()
            val dy = sin(radians).toFloat()
            val half = (abs(dx) * size.width + abs(dy) * size.height) / 2
            Brush.linearGradient(*colourStops(spec), start = center - Offset(dx, dy) * half, end = center + Offset(dx, dy) * half)
        }
        drawRect(brush)
    }
}

/**
 * The stops along a bar, left to right: a tap on the bar adds a stop there
 * (in the colour the gradient has there), a stop is chosen with a tap and
 * dragged along to move it, between the stops either side.
 */
@Composable
private fun StopBar(
    spec: GradientSpec,
    chosen: Int,
    onChoose: (Int) -> Unit,
    onMove: (Int, Float) -> Unit,
    onAdd: (Float) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.svg_gradient_bar_description)
    val handle = 28.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(handle + 8.dp).semantics { contentDescription = description }) {
        val density = LocalDensity.current
        val handlePx = with(density) { handle.toPx() }
        val trackPx = (constraints.maxWidth - handlePx).coerceAtLeast(1f)
        val cell = with(density) { 6.dp.toPx() }
        val currentAdd by rememberUpdatedState(onAdd)
        val currentMove by rememberUpdatedState(onMove)
        val currentChoose by rememberUpdatedState(onChoose)
        Canvas(
            modifier = Modifier
                .padding(horizontal = handle / 2)
                .fillMaxWidth()
                .height(handle - 4.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .pointerInput(Unit) {
                    detectTapGestures { at -> currentAdd((at.x / size.width).coerceIn(0f, 1f)) }
                },
        ) {
            checkerboard(scheme.surfaceContainerHighest, scheme.outlineVariant, cell)
            drawRect(Brush.horizontalGradient(*colourStops(spec)))
        }
        spec.stops.forEachIndexed { i, stop ->
            val selected = i == chosen
            val ring by animateDpAsState(if (selected) 4.dp else 2.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "stopRing")
            val currentStop by rememberUpdatedState(stop)
            val currentIndex by rememberUpdatedState(i)
            val stopDescription = stringResource(R.string.svg_gradient_stop_description, (stop.offset * 100).roundToInt())
            Box(
                modifier = Modifier
                    .offset { IntOffset((stop.offset.toFloat() * trackPx).roundToInt(), with(density) { 4.dp.roundToPx() }) }
                    .size(handle)
                    .clip(CircleShape)
                    .background(scheme.surface)
                    .border(ring, if (selected) scheme.primary else scheme.outline, CircleShape)
                    .padding(ring + 1.dp)
                    .clip(CircleShape)
                    .background(Color(stop.argb.toInt() or (0xFF shl 24)))
                    .semantics { contentDescription = stopDescription }
                    // Dragged along the bar, it changes the drawing live: the sheet fades meanwhile.
                    .peeksSheet()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentChoose(currentIndex)
                        }
                    }
                    .pointerInput(Unit) {
                        var offset = 0f
                        detectDragGestures(
                            onDragStart = {
                                offset = currentStop.offset.toFloat()
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                currentChoose(currentIndex)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                offset = (offset + drag.x / trackPx).coerceIn(0f, 1f)
                                currentMove(currentIndex, offset)
                            },
                        )
                    },
            )
        }
    }
}
