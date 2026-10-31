package com.cuscus.ffmpeggui.svgedit

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.asComposePath
import com.cuscus.ffmpeggui.core.vector.svg.Affine
import com.cuscus.ffmpeggui.core.vector.svg.PaintKind
import com.cuscus.ffmpeggui.core.vector.svg.ResolvedStyle
import com.cuscus.ffmpeggui.core.vector.svg.Seg
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgGroup
import com.cuscus.ffmpeggui.core.vector.svg.SvgPaint
import com.cuscus.ffmpeggui.core.vector.svg.SvgPath
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.SvgStyle
import com.cuscus.ffmpeggui.core.vector.svg.SvgText
import com.cuscus.ffmpeggui.core.vector.svg.find
import com.cuscus.ffmpeggui.core.vector.svg.outermost
import com.cuscus.ffmpeggui.core.vector.svg.replaceWithOutlines
import com.cuscus.ffmpeggui.core.vector.svg.resolvedStyle
import com.cuscus.ffmpeggui.core.vector.svg.update
import com.cuscus.ffmpeggui.core.vector.svg.worldTransform

/*
 * Shapes combined as Blender, Illustrator and Figma combine them: joined into
 * one, the ones on top cut out of the bottom one, only where they all meet,
 * only where an odd number of them lie, or cut into every piece their outlines
 * make. The work is Skia's (Path.op, what Android draws with): exact, curves
 * kept as curves. What takes part is each one's area on the drawing: its fill,
 * or for an outline with nothing inside (a line) the band its stroke paints; a
 * text takes part as its letters, a group as everything in it.
 *
 * Also here, for the same reasons: a stroke made a shape of its own, and a
 * text made shapes (its letters as paths, which any program draws alike).
 */

/** How shapes are combined. */
internal enum class SvgBoolean {
    /** All joined into one. */
    UNION,
    /** The ones on top cut out of the bottom one. */
    SUBTRACT,
    /** Only where they all meet. */
    INTERSECT,
    /** Only where an odd number of them lie: where two meet, a hole. */
    EXCLUDE,
    /** Cut along every outline into separate pieces, each in the colours of the top one there. */
    DIVIDE,
}

internal object SvgBooleans {

    /** Below this the area of a piece is nothing (pixels squared of the drawing). */
    private const val MIN_AREA = 0.5

    /**
     * [keys] combined as [op] says, in place of them: what is made lies where
     * the top one was, in the style of the bottom one (for [SvgBoolean.DIVIDE]
     * each piece in that of the top one over it). With the keys of what was
     * made; null when fewer than two can take part or nothing is left.
     */
    fun combine(doc: SvgDocument, keys: Collection<Int>, op: SvgBoolean, renderer: SvgRenderer): Pair<SvgDocument, List<Int>>? {
        val parts = doc.outermost(keys).mapNotNull { k -> area(doc, k, renderer)?.let { (path, style) -> Part(k, path, style) } }
        if (parts.size < 2) return null
        val outlines: List<Pair<Path, SvgStyle>> = when (op) {
            SvgBoolean.UNION -> listOf(fold(parts, Path.Op.UNION) to parts.first().style)
            SvgBoolean.SUBTRACT -> {
                val cut = Path(parts.first().path)
                parts.drop(1).forEach { cut.op(it.path, Path.Op.DIFFERENCE) }
                listOf(cut to parts.first().style)
            }
            SvgBoolean.INTERSECT -> listOf(fold(parts, Path.Op.INTERSECT) to parts.first().style)
            SvgBoolean.EXCLUDE -> listOf(fold(parts, Path.Op.XOR) to parts.first().style)
            SvgBoolean.DIVIDE -> divide(parts)
        }
        val made = outlines.mapNotNull { (path, style) ->
            val segs = segmentsOf(path)
            if (segs.isEmpty() || area(segs) < MIN_AREA) return@mapNotNull null
            segs to style.copy(fillRule = if (path.fillType == Path.FillType.EVEN_ODD || path.fillType == Path.FillType.INVERSE_EVEN_ODD) "evenodd" else null)
        }
        if (made.isEmpty()) return null
        return doc.replaceWithOutlines(parts.map { it.key }, parts.last().key, made)
    }

    /**
     * The stroke of the shape [key] as a shape of its own, filled with the
     * stroke's paint, just above it. A shape that is also filled stays, its
     * stroke taken away; one that is only an outline is replaced. With the
     * new shape's key; null when it has no stroke to make a shape of.
     */
    fun outlineStroke(doc: SvgDocument, key: Int): Pair<SvgDocument, Int>? {
        val shape = doc.find(key) as? SvgShape ?: return null
        val style = doc.resolvedStyle(key)
        if (style.stroke.kind == PaintKind.NONE || style.strokeWidth <= 0) return null
        val band = strokeBand(pathOf(shape.segments()), style)
        val segs = SvgPath.transform(segmentsOf(band), doc.worldTransform(key))
        if (segs.isEmpty()) return null
        val filled = style.fill.kind != PaintKind.NONE && shape.segments().any { it == Seg.Close }
        val bandStyle = SvgStyle(
            fill = style.stroke, fillOpacity = style.strokeOpacity, stroke = SvgPaint.NONE,
            opacity = shape.style.opacity,
        )
        return if (filled) {
            val stripped = doc.update(key) { it.withStyle(it.style.copy(stroke = SvgPaint.NONE, strokeWidth = null)) }
            val (d, made) = stripped.replaceWithOutlines(emptyList(), key, listOf(segs to bandStyle))
            made.firstOrNull()?.let { d to it }
        } else {
            val (d, made) = doc.replaceWithOutlines(listOf(key), key, listOf(segs to bandStyle))
            made.firstOrNull()?.let { d to it }
        }
    }

    /** The text [key] as its letters, a path in its place drawn as it was. Null for anything but a text. */
    fun textToPath(doc: SvgDocument, key: Int, renderer: SvgRenderer): Pair<SvgDocument, Int>? {
        val text = doc.find(key) as? SvgText ?: return null
        val style = doc.resolvedStyle(key)
        val segs = SvgPath.transform(segmentsOf(renderer.textOutline(text, style)), doc.worldTransform(key))
        if (segs.isEmpty()) return null
        val (d, made) = doc.replaceWithOutlines(listOf(key), key, listOf(segs to explicit(style, text.style).copy(fillRule = null)))
        return made.firstOrNull()?.let { d to it }
    }

    private class Part(val key: Int, val path: Path, val style: SvgStyle)

    private fun fold(parts: List<Part>, op: Path.Op): Path {
        val out = Path(parts.first().path)
        parts.drop(1).forEach { out.op(it.path, op) }
        return out
    }

    /**
     * Every piece the outlines of [parts] (bottom first) cut each other into:
     * each one added in turn splits what is there into what it covers (now
     * its piece, in its style) and what it leaves, and adds what it covers of
     * nothing yet.
     */
    private fun divide(parts: List<Part>): List<Pair<Path, SvgStyle>> {
        var pieces = listOf(Path(parts.first().path) to parts.first().style)
        val covered = Path(parts.first().path)
        for (part in parts.drop(1)) {
            val next = ArrayList<Pair<Path, SvgStyle>>()
            for ((piece, style) in pieces) {
                next.add(Path(piece).apply { op(part.path, Path.Op.DIFFERENCE) } to style)
                next.add(Path(piece).apply { op(part.path, Path.Op.INTERSECT) } to part.style)
            }
            next.add(Path(part.path).apply { op(covered, Path.Op.DIFFERENCE) } to part.style)
            covered.op(part.path, Path.Op.UNION)
            pieces = next.filter { !it.first.isEmpty() }
        }
        // A piece may be in several bits apart: each bit its own shape.
        return pieces.flatMap { (path, style) -> split(path).map { it to style } }
    }

    /** [path]'s separate outer outlines, each with the holes inside it. */
    private fun split(path: Path): List<Path> {
        val subs = subpaths(segmentsOf(path))
        if (subs.size <= 1) return listOf(path)
        // Every outline as its own area; one inside another is a hole of it (or an island in that hole).
        val areas = subs.map { pathOf(it) }
        val out = ArrayList<Path>()
        val used = BooleanArray(subs.size)
        val order = subs.indices.sortedByDescending { area(subs[it]) }
        for (i in order) {
            if (used[i]) continue
            used[i] = true
            val piece = Path(areas[i])
            for (j in order) {
                if (used[j]) continue
                val inside = Path(areas[j]).apply { op(areas[i], Path.Op.DIFFERENCE) }.isEmpty()
                if (inside) {
                    used[j] = true
                    piece.addPath(areas[j])
                }
            }
            piece.fillType = Path.FillType.EVEN_ODD
            // What is left of it once its holes are out.
            out += Path(path).apply { op(piece, Path.Op.INTERSECT) }
        }
        return out
    }

    /** The node [key]'s area on the drawing, in pixels, and the style what is made of it keeps; null when it has none. */
    private fun area(doc: SvgDocument, key: Int, renderer: SvgRenderer): Pair<Path, SvgStyle>? {
        val node = doc.find(key) ?: return null
        if (node.style.hidden) return null
        val style = doc.resolvedStyle(key)
        val world = doc.worldTransform(key)
        return when (node) {
            is SvgShape -> {
                val local = pathOf(node.segments())
                if (local.isEmpty()) return null
                val hasFill = style.fill.kind != PaintKind.NONE && !node.isOpenLine()
                if (hasFill) {
                    local.fillType = if (style.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    transformed(local, world) to explicit(style, node.style)
                } else if (style.stroke.kind != PaintKind.NONE && style.strokeWidth > 0) {
                    // Only an outline: the band its stroke paints, which then takes the stroke's paint.
                    transformed(strokeBand(local, style), world) to SvgStyle(fill = style.stroke, fillOpacity = style.strokeOpacity, stroke = SvgPaint.NONE, opacity = node.style.opacity)
                } else {
                    null
                }
            }
            is SvgText -> {
                val local = renderer.textOutline(node, style)
                if (local.isEmpty()) null else transformed(local, world) to explicit(style, node.style).copy(stroke = SvgPaint.NONE, strokeWidth = null)
            }
            is SvgGroup -> {
                // Everything in it as one area, in the style of the first thing in it that has one.
                var out: Path? = null
                var first: SvgStyle? = null
                fun walk(g: SvgGroup) {
                    for (c in g.children) {
                        if (c is SvgGroup) {
                            walk(c)
                            continue
                        }
                        val (p, s) = area(doc, c.key, renderer) ?: continue
                        if (out == null) {
                            out = Path(p)
                            first = s
                        } else {
                            out!!.op(p, Path.Op.UNION)
                        }
                    }
                }
                walk(node)
                out?.let { it to first!! }
            }
            else -> null
        }
    }

    /** A path or a line that does not close: its "inside" is no area to combine. */
    private fun SvgShape.isOpenLine(): Boolean = tag == "line" || tag == "polyline" || (tag == "path" && segments().none { it == Seg.Close })

    /** The band [style]'s stroke paints along [path] (dashes left out), as an area. */
    private fun strokeBand(path: Path, style: ResolvedStyle): Path {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = style.strokeWidth.toFloat()
            strokeCap = when (style.other["stroke-linecap"]) {
                "round" -> Paint.Cap.ROUND
                "square" -> Paint.Cap.SQUARE
                else -> Paint.Cap.BUTT
            }
            strokeJoin = when (style.other["stroke-linejoin"]) {
                "round" -> Paint.Join.ROUND
                "bevel" -> Paint.Join.BEVEL
                else -> Paint.Join.MITER
            }
            style.other["stroke-miterlimit"]?.toFloatOrNull()?.let { strokeMiter = it }
        }
        val band = Path()
        paint.getFillPath(path, band)
        // Overlaps of the band (a curve over itself) are one area.
        band.fillType = Path.FillType.WINDING
        return Path().apply { op(band, Path.Op.UNION) }
    }

    /** What [style] draws with, written out: what a node made elsewhere keeps of how it looked. */
    private fun explicit(style: ResolvedStyle, own: SvgStyle): SvgStyle = SvgStyle(
        fill = style.fill,
        stroke = style.stroke,
        strokeWidth = style.strokeWidth.takeIf { style.stroke.kind != PaintKind.NONE },
        fillOpacity = style.fillOpacity.takeIf { it < 1.0 },
        strokeOpacity = style.strokeOpacity.takeIf { it < 1.0 },
        opacity = own.opacity,
        other = style.other.filterKeys { it.startsWith("stroke-") } + own.other.filterKeys { it != "display" && it != "visibility" },
    )

    private fun transformed(path: Path, t: Affine): Path {
        if (t.isIdentity) return path
        val m = android.graphics.Matrix().apply {
            setValues(floatArrayOf(t.a.toFloat(), t.c.toFloat(), t.e.toFloat(), t.b.toFloat(), t.d.toFloat(), t.f.toFloat(), 0f, 0f, 1f))
        }
        return Path(path).apply { transform(m) }
    }

    /** [path] read back as the editor's steps (a conic, Skia's arc, as quadratic curves). */
    fun segmentsOf(path: Path): List<Seg> {
        val out = ArrayList<Seg>()
        val iterator = PathIterator(path.asComposePath(), PathIterator.ConicEvaluation.AsQuadratics, 0.1f)
        // Each step's points, its start first (as Skia gives them).
        val p = FloatArray(8)
        while (iterator.hasNext()) {
            when (iterator.next(p)) {
                PathSegment.Type.Move -> out += Seg.Move(p[0].toDouble(), p[1].toDouble())
                PathSegment.Type.Line -> out += Seg.Line(p[2].toDouble(), p[3].toDouble())
                PathSegment.Type.Quadratic -> out += Seg.Quad(p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble())
                PathSegment.Type.Cubic -> out += Seg.Cubic(p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble(), p[6].toDouble(), p[7].toDouble())
                PathSegment.Type.Close -> out += Seg.Close
                PathSegment.Type.Done -> break
                else -> Unit
            }
        }
        // A move with nothing after it draws nothing.
        return out.filterIndexed { i, s -> !(s is Seg.Move && (i == out.lastIndex || out[i + 1] is Seg.Move)) }
    }

    /** [segs] cut where each outline starts. */
    private fun subpaths(segs: List<Seg>): List<List<Seg>> {
        val out = ArrayList<List<Seg>>()
        var current = ArrayList<Seg>()
        for (s in segs) {
            if (s is Seg.Move && current.isNotEmpty()) {
                out += current
                current = ArrayList()
            }
            current += s
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /** About how much [segs] cover (their outlines as polygons through their points, holes counted once). */
    private fun area(segs: List<Seg>): Double {
        var total = 0.0
        var sx = 0.0; var sy = 0.0; var x = 0.0; var y = 0.0
        var sub = 0.0
        fun edge(nx: Double, ny: Double) {
            sub += x * ny - nx * y
            x = nx; y = ny
        }
        for (s in segs) {
            when (s) {
                is Seg.Move -> {
                    total += kotlin.math.abs(sub) / 2
                    sub = 0.0
                    sx = s.x; sy = s.y; x = s.x; y = s.y
                }
                is Seg.Line -> edge(s.x, s.y)
                is Seg.Quad -> { edge(s.cx, s.cy); edge(s.x, s.y) }
                is Seg.Cubic -> { edge(s.c1x, s.c1y); edge(s.c2x, s.c2y); edge(s.x, s.y) }
                Seg.Close -> edge(sx, sy)
            }
        }
        return total + kotlin.math.abs(sub) / 2
    }
}
