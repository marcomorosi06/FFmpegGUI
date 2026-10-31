@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui.svgedit

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.svg.Affine
import com.cuscus.ffmpeggui.core.vector.svg.Seg
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgKeys
import com.cuscus.ffmpeggui.core.vector.svg.SvgPaint
import com.cuscus.ffmpeggui.core.vector.svg.SvgPath
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.SvgStyle
import com.cuscus.ffmpeggui.core.vector.svg.pathData
import com.cuscus.ffmpeggui.core.vector.svg.svgNumber
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * What can be added to a drawing: basic shapes (rectangles, an ellipse,
 * polygons, a star, a heart, an arrow, a speech bubble), lines (a plain one
 * and a connector, whose ends hold on to shapes), and every Material 3
 * Expressive shape (cookies, flowers, clovers, bursts...) as an exact path.
 * The gallery shows each as it will come, in the theme's colour.
 */

/** One shape of the gallery: [make] builds it filling the box left, top, width, height. */
internal class ShapeTemplate(
    @StringRes val label: Int,
    val line: Boolean = false,
    val make: (Double, Double, Double, Double, SvgStyle) -> SvgShape,
)

private fun shape(tag: String, style: SvgStyle, geometry: Map<String, String>) =
    SvgShape(SvgKeys.next(), null, Affine.IDENTITY, style, emptyMap(), tag, LinkedHashMap(geometry))

/** A path of unit coordinates (0..1 each way) laid on the box. */
private fun unitPath(d: String): (Double, Double, Double, Double, SvgStyle) -> SvgShape = { l, t, w, h, style ->
    shape("path", style, mapOf("d" to pathData(SvgPath.transform(SvgPath.parse(d), Affine.translate(l, t) * Affine.scale(w, h)))))
}

/** A polygon of unit points (x, y, x, y...) laid on the box. */
private fun unitPolygon(vararg p: Double): (Double, Double, Double, Double, SvgStyle) -> SvgShape = { l, t, w, h, style ->
    shape("polygon", style, mapOf("points" to p.toList().chunked(2).joinToString(" ") { (x, y) -> svgNumber(l + x * w) + "," + svgNumber(t + y * h) }))
}

/** A regular polygon of [sides] (or a star of [sides] points when [inner] is given), pointing up. */
private fun regular(sides: Int, inner: Double? = null): (Double, Double, Double, Double, SvgStyle) -> SvgShape {
    val count = if (inner != null) sides * 2 else sides
    val pts = DoubleArray(count * 2)
    for (i in 0 until count) {
        val r = if (inner != null && i % 2 == 1) inner * 0.5 else 0.5
        val a = -PI / 2 + i * 2 * PI / count
        pts[i * 2] = 0.5 + r * cos(a)
        pts[i * 2 + 1] = 0.5 + r * sin(a)
    }
    return unitPolygon(*pts)
}

/** A Material shape as an exact path of cubic curves laid on the box (its proportions kept, centred). */
private fun material(polygon: RoundedPolygon): (Double, Double, Double, Double, SvgStyle) -> SvgShape = { l, t, w, h, style ->
    val b = polygon.calculateBounds()
    val bw = (b[2] - b[0]).toDouble().coerceAtLeast(1e-6)
    val bh = (b[3] - b[1]).toDouble().coerceAtLeast(1e-6)
    val s = min(w / bw, h / bh)
    val ox = l + (w - bw * s) / 2 - b[0] * s
    val oy = t + (h - bh * s) / 2 - b[1] * s
    val segs = ArrayList<Seg>()
    polygon.cubics.forEachIndexed { i, c ->
        if (i == 0) segs += Seg.Move(ox + c.anchor0X * s, oy + c.anchor0Y * s)
        segs += Seg.Cubic(
            ox + c.control0X * s, oy + c.control0Y * s,
            ox + c.control1X * s, oy + c.control1Y * s,
            ox + c.anchor1X * s, oy + c.anchor1Y * s,
        )
    }
    segs += Seg.Close
    shape("path", style, mapOf("d" to pathData(segs)))
}

internal val BASIC_SHAPES = listOf(
    ShapeTemplate(R.string.svg_shape_rectangle) { l, t, w, h, s -> shape("rect", s, mapOf("x" to svgNumber(l), "y" to svgNumber(t), "width" to svgNumber(w), "height" to svgNumber(h * 0.7))) },
    ShapeTemplate(R.string.svg_shape_rounded) { l, t, w, h, s -> shape("rect", s, mapOf("x" to svgNumber(l), "y" to svgNumber(t), "width" to svgNumber(w), "height" to svgNumber(h), "rx" to svgNumber(min(w, h) * 0.22))) },
    ShapeTemplate(R.string.svg_shape_ellipse) { l, t, w, h, s -> shape("ellipse", s, mapOf("cx" to svgNumber(l + w / 2), "cy" to svgNumber(t + h / 2), "rx" to svgNumber(w / 2), "ry" to svgNumber(h / 2))) },
    ShapeTemplate(R.string.svg_shape_triangle, make = unitPolygon(0.5, 0.0, 1.0, 1.0, 0.0, 1.0)),
    ShapeTemplate(R.string.svg_shape_diamond, make = unitPolygon(0.5, 0.0, 1.0, 0.5, 0.5, 1.0, 0.0, 0.5)),
    ShapeTemplate(R.string.svg_shape_pentagon, make = regular(5)),
    ShapeTemplate(R.string.svg_shape_hexagon, make = regular(6)),
    ShapeTemplate(R.string.svg_shape_star, make = regular(5, inner = 0.45)),
    ShapeTemplate(R.string.svg_shape_burst, make = regular(12, inner = 0.7)),
    ShapeTemplate(
        R.string.svg_shape_heart,
        make = unitPath("M0.5 0.92C0.2 0.72 0 0.52 0 0.3C0 0.12 0.14 0.02 0.28 0.02C0.38 0.02 0.46 0.08 0.5 0.17C0.54 0.08 0.62 0.02 0.72 0.02C0.86 0.02 1 0.12 1 0.3C1 0.52 0.8 0.72 0.5 0.92Z"),
    ),
    ShapeTemplate(R.string.svg_shape_arrow, make = unitPolygon(0.0, 0.35, 0.58, 0.35, 0.58, 0.12, 1.0, 0.5, 0.58, 0.88, 0.58, 0.65, 0.0, 0.65)),
    ShapeTemplate(
        R.string.svg_shape_bubble,
        make = unitPath("M0.12 0.05H0.88Q1 0.05 1 0.17V0.62Q1 0.74 0.88 0.74H0.42L0.2 0.96L0.25 0.74H0.12Q0 0.74 0 0.62V0.17Q0 0.05 0.12 0.05Z"),
    ),
)

internal val LINE_SHAPES = listOf(
    ShapeTemplate(R.string.svg_shape_line, line = true) { l, t, w, h, s ->
        shape("line", s, mapOf("x1" to svgNumber(l), "y1" to svgNumber(t + h), "x2" to svgNumber(l + w), "y2" to svgNumber(t)))
    },
)

/** Every Material 3 Expressive shape, by name. */
internal val MATERIAL_SHAPES: List<Pair<String, RoundedPolygon>> = listOf(
    "Circle" to MaterialShapes.Circle, "Square" to MaterialShapes.Square, "Slanted" to MaterialShapes.Slanted,
    "Arch" to MaterialShapes.Arch, "Fan" to MaterialShapes.Fan, "Arrow" to MaterialShapes.Arrow,
    "Semicircle" to MaterialShapes.SemiCircle, "Oval" to MaterialShapes.Oval, "Pill" to MaterialShapes.Pill,
    "Triangle" to MaterialShapes.Triangle, "Diamond" to MaterialShapes.Diamond, "Clam shell" to MaterialShapes.ClamShell,
    "Pentagon" to MaterialShapes.Pentagon, "Gem" to MaterialShapes.Gem, "Sunny" to MaterialShapes.Sunny,
    "Very sunny" to MaterialShapes.VerySunny, "Cookie 4" to MaterialShapes.Cookie4Sided, "Cookie 6" to MaterialShapes.Cookie6Sided,
    "Cookie 7" to MaterialShapes.Cookie7Sided, "Cookie 9" to MaterialShapes.Cookie9Sided, "Cookie 12" to MaterialShapes.Cookie12Sided,
    "Ghost" to MaterialShapes.Ghostish, "Clover 4" to MaterialShapes.Clover4Leaf, "Clover 8" to MaterialShapes.Clover8Leaf,
    "Burst" to MaterialShapes.Burst, "Soft burst" to MaterialShapes.SoftBurst, "Boom" to MaterialShapes.Boom,
    "Flower" to MaterialShapes.Flower, "Pixel circle" to MaterialShapes.PixelCircle, "Pixel triangle" to MaterialShapes.PixelTriangle,
    "Bun" to MaterialShapes.Bun, "Heart" to MaterialShapes.Heart,
)

/** [make] in the middle of [doc], a third of its shorter side, in [argb] (an outline for a line). */
internal fun placed(doc: SvgDocument, line: Boolean, argb: Long, make: (Double, Double, Double, Double, SvgStyle) -> SvgShape): SvgShape {
    val vb = doc.viewBox
    val side = min(vb.width, vb.height) * 0.34
    val style = if (line) {
        SvgStyle(fill = SvgPaint.NONE, stroke = SvgPaint.color(argb), strokeWidth = (side * 0.035).coerceAtLeast(1.0), other = mapOf("stroke-linecap" to "round"))
    } else {
        SvgStyle(fill = SvgPaint.color(argb))
    }
    return make(vb.centerX - side / 2, vb.centerY - side / 2, side, side, style)
}

/** The gallery of shapes, in a sheet; [onPick] gets what to build and whether it is a line. */
@Composable
internal fun SvgShapesSheet(
    onPick: (Boolean, (Double, Double, Double, Double, SvgStyle) -> SvgShape) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    PeekThroughSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.svg_add_shape), style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(bottom = 4.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Section(stringResource(R.string.svg_shapes_basic)) }
            items(BASIC_SHAPES) { t -> Tile(stringResource(t.label), false, t.make) { onPick(false, t.make) } }
            item(span = { GridItemSpan(maxLineSpan) }) { Section(stringResource(R.string.svg_shapes_lines)) }
            items(LINE_SHAPES) { t -> Tile(stringResource(t.label), true, t.make) { onPick(true, t.make) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.svg_line_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Section(stringResource(R.string.svg_shapes_material)) }
            items(MATERIAL_SHAPES) { (name, polygon) ->
                val make = remember(polygon) { material(polygon) }
                Tile(name, false, make) { onPick(false, make) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Box(modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

/** One shape of the gallery: drawn as it will come, squishing under the finger. */
@Composable
private fun Tile(label: String, line: Boolean, make: (Double, Double, Double, Double, SvgStyle) -> SvgShape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val segments = remember(make) { make(0.0, 0.0, 1.0, 1.0, SvgStyle()).segments() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.88f)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHighest)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { contentDescription = label }
            .padding(12.dp),
    ) {
        val fill = scheme.primary
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val path = Path().apply { fillType = PathFillType.NonZero }
            val sw = size.width
            for (seg in segments) {
                when (seg) {
                    is Seg.Move -> path.moveTo(seg.x.toFloat() * sw, seg.y.toFloat() * sw)
                    is Seg.Line -> path.lineTo(seg.x.toFloat() * sw, seg.y.toFloat() * sw)
                    is Seg.Quad -> path.quadraticTo(seg.cx.toFloat() * sw, seg.cy.toFloat() * sw, seg.x.toFloat() * sw, seg.y.toFloat() * sw)
                    is Seg.Cubic -> path.cubicTo(
                        seg.c1x.toFloat() * sw, seg.c1y.toFloat() * sw,
                        seg.c2x.toFloat() * sw, seg.c2y.toFloat() * sw,
                        seg.x.toFloat() * sw, seg.y.toFloat() * sw,
                    )
                    Seg.Close -> path.close()
                }
            }
            if (line) {
                drawPath(path, fill, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Its ends, as the editor shows them: they hold on to shapes.
                (segments.firstOrNull() as? Seg.Move)?.let { drawCircle(scheme.tertiary, radius = 5.dp.toPx(), center = Offset(it.x.toFloat() * sw, it.y.toFloat() * sw)) }
                (segments.lastOrNull() as? Seg.Line)?.let { drawCircle(scheme.tertiary, radius = 5.dp.toPx(), center = Offset(it.x.toFloat() * sw, it.y.toFloat() * sw)) }
            } else {
                drawPath(path, fill)
            }
        }
    }
}
