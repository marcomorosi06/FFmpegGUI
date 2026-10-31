package com.cuscus.ffmpeggui.core.vector.svg

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/*
 * What the SVG editor does to a drawing, without a screen: find a node and
 * what it sits in, the transform from its own units to the drawing's pixels,
 * its box, which node is under a finger, and every change (move, size, turn,
 * restyle, hide, duplicate, delete, reorder). A drawing never changes in
 * place: each change gives a new one, so that undoing is keeping the old one.
 *
 * "World" here is the drawing in pixels: its width x height, the view box
 * already applied.
 */

/** How wide a text is drawn: the screen measures it with the real font; [ESTIMATE] guesses. */
fun interface TextMeasure {
    /** The width of [line] drawn in [style], in the text's own units. */
    fun width(line: String, style: ResolvedStyle): Double

    companion object {
        /** Half a font size and a bit per character: close enough for a box before a font is at hand. */
        val ESTIMATE = TextMeasure { line, style -> line.length * style.fontSize * (if (style.bold) 0.6 else 0.55) }
    }
}

/** Where a node goes among its siblings. */
enum class SvgOrder { UP, DOWN, TOP, BOTTOM }

/** The distance between two lines of a text, in font sizes (as written by [SvgWriter]). */
const val SVG_LINE_HEIGHT = 1.2

/** The node with [key], at any depth; null when there is none. */
fun SvgDocument.find(key: Int): SvgNode? = pathTo(key)?.last()

/** The nodes from the top of the drawing down to the one with [key] (it last); null when there is none. */
fun SvgDocument.pathTo(key: Int): List<SvgNode>? {
    fun walk(nodes: List<SvgNode>, trail: List<SvgNode>): List<SvgNode>? {
        for (n in nodes) {
            if (n.key == key) return trail + n
            if (n is SvgGroup) walk(n.children, trail + n)?.let { return it }
        }
        return null
    }
    return walk(nodes, emptyList())
}

/** The group the node with [key] is in; null at the top of the drawing (or when there is no such node). */
fun SvgDocument.parentOf(key: Int): SvgGroup? = pathTo(key)?.dropLast(1)?.lastOrNull() as? SvgGroup

/** From the units the node with [key] is placed in (its own transform not yet applied) to the drawing's pixels. */
fun SvgDocument.parentTransform(key: Int): Affine {
    var t = viewTransform
    pathTo(key)?.dropLast(1)?.forEach { t *= it.transform }
    return t
}

/** From the node's own units (its transform applied) to the drawing's pixels. */
fun SvgDocument.worldTransform(key: Int): Affine {
    val path = pathTo(key) ?: return viewTransform
    var t = viewTransform
    path.forEach { t *= it.transform }
    return t
}

/** The style the node with [key] draws with: its own over what its groups give it. */
fun SvgDocument.resolvedStyle(key: Int): ResolvedStyle {
    var style = ResolvedStyle()
    pathTo(key)?.forEach { style = style.inherit(it.style) }
    return style
}

/** Every node, groups before what they hold, in drawing order (bottom first). */
fun SvgDocument.allNodes(): List<SvgNode> {
    val out = ArrayList<SvgNode>()
    fun walk(nodes: List<SvgNode>) {
        for (n in nodes) {
            out += n
            if (n is SvgGroup) walk(n.children)
        }
    }
    walk(nodes)
    return out
}

/** How many nodes the drawing has (groups count, with what they hold). */
val SvgDocument.nodeCount: Int get() = allNodes().size

/** The drawing with the node [key] replaced by what [change] makes of it. */
fun SvgDocument.update(key: Int, change: (SvgNode) -> SvgNode): SvgDocument {
    fun walk(nodes: List<SvgNode>): List<SvgNode> {
        var changed = false
        val out = nodes.map { n ->
            when {
                n.key == key -> change(n).also { changed = true }
                n is SvgGroup -> {
                    val inner = walk(n.children)
                    if (inner !== n.children) {
                        changed = true
                        n.copy(children = inner)
                    } else {
                        n
                    }
                }
                else -> n
            }
        }
        return if (changed) out else nodes
    }
    val next = walk(nodes)
    return if (next === nodes) this else copy(nodes = next)
}

/** The drawing without the node [key] (and what it holds). */
fun SvgDocument.remove(key: Int): SvgDocument {
    fun walk(nodes: List<SvgNode>): List<SvgNode> {
        if (nodes.none { it.key == key || it is SvgGroup }) return nodes
        return nodes.filter { it.key != key }.map { if (it is SvgGroup) it.copy(children = walk(it.children)) else it }
    }
    return copy(nodes = walk(nodes))
}

/** The drawing with [node] just above the node [key], in the same group; at the top of the drawing when there is no [key]. */
fun SvgDocument.insertAbove(key: Int?, node: SvgNode): SvgDocument {
    if (key == null || find(key) == null) return copy(nodes = nodes + node)
    fun walk(nodes: List<SvgNode>): List<SvgNode> {
        val at = nodes.indexOfFirst { it.key == key }
        if (at >= 0) return nodes.toMutableList().apply { add(at + 1, node) }
        return nodes.map { if (it is SvgGroup) it.copy(children = walk(it.children)) else it }
    }
    return copy(nodes = walk(nodes))
}

/**
 * A copy of the node [key] just above it, moved by [dx], [dy] pixels of the
 * drawing so that it shows; with the copy's key. Null when there is no such node.
 */
fun SvgDocument.duplicate(key: Int, dx: Double = 0.0, dy: Double = 0.0): Pair<SvgDocument, Int>? {
    val node = find(key) ?: return null
    val copy = node.duplicated()
    val placed = insertAbove(key, copy)
    val moved = if (dx == 0.0 && dy == 0.0) placed else placed.transformInWorld(copy.key, Affine.translate(dx, dy))
    return moved to copy.key
}

/** The drawing with [node] just below the node [key], in the same group; at the bottom of the drawing when there is no [key]. */
fun SvgDocument.insertBelow(key: Int?, node: SvgNode): SvgDocument {
    if (key == null || find(key) == null) return copy(nodes = listOf(node) + nodes)
    fun walk(nodes: List<SvgNode>): List<SvgNode> {
        val at = nodes.indexOfFirst { it.key == key }
        if (at >= 0) return nodes.toMutableList().apply { add(at, node) }
        return nodes.map { if (it is SvgGroup) it.copy(children = walk(it.children)) else it }
    }
    return copy(nodes = walk(nodes))
}

/** Whether the node [inner] is the node [key] or inside it. */
fun SvgDocument.isWithin(inner: Int, key: Int): Boolean = pathTo(inner)?.any { it.key == key } == true

/**
 * The drawing with the node [key] moved just above the node [target] (into
 * [target]'s group when it is in another): what the layers list does when a
 * row is dropped over another. Nothing changes when [target] is [key] or
 * inside it.
 */
fun SvgDocument.moveAbove(key: Int, target: Int): SvgDocument {
    if (isWithin(target, key)) return this
    val node = find(key) ?: return this
    return remove(key).insertAbove(target, node)
}

/** The drawing with the node [key] moved just below the node [target] (see [moveAbove]). */
fun SvgDocument.moveBelow(key: Int, target: Int): SvgDocument {
    if (isWithin(target, key)) return this
    val node = find(key) ?: return this
    return remove(key).insertBelow(target, node)
}

/** The drawing with the node [key] moved among its siblings as [order] says. */
fun SvgDocument.reorder(key: Int, order: SvgOrder): SvgDocument {
    fun move(list: List<SvgNode>): List<SvgNode>? {
        val at = list.indexOfFirst { it.key == key }
        if (at < 0) return null
        val target = when (order) {
            SvgOrder.UP -> (at + 1).coerceAtMost(list.lastIndex)
            SvgOrder.DOWN -> (at - 1).coerceAtLeast(0)
            SvgOrder.TOP -> list.lastIndex
            SvgOrder.BOTTOM -> 0
        }
        if (target == at) return list
        return list.toMutableList().apply { add(target, removeAt(at)) }
    }
    fun walk(nodes: List<SvgNode>): List<SvgNode> {
        move(nodes)?.let { return it }
        return nodes.map { if (it is SvgGroup) it.copy(children = walk(it.children)) else it }
    }
    return copy(nodes = walk(nodes))
}

/** Whether the node [key] can go [order]: false when it is already there. */
fun SvgDocument.canReorder(key: Int, order: SvgOrder): Boolean {
    val siblings = parentOf(key)?.children ?: nodes
    val at = siblings.indexOfFirst { it.key == key }
    if (at < 0) return false
    return when (order) {
        SvgOrder.UP, SvgOrder.TOP -> at < siblings.lastIndex
        SvgOrder.DOWN, SvgOrder.BOTTOM -> at > 0
    }
}

/**
 * The drawing with the node [key] changed by [m], a transform of the drawing's
 * pixels (a move, a size about a point, a turn about a point): its own
 * transform takes it, in the units it is placed in, whatever its groups do.
 */
fun SvgDocument.transformInWorld(key: Int, m: Affine): SvgDocument {
    val p = parentTransform(key)
    val local = p.inverse() * m * p
    return update(key) { it.withTransform(local * it.transform) }
}

/** The drawing with the node [key] shown or hidden. */
fun SvgDocument.setHidden(key: Int, hidden: Boolean): SvgDocument = update(key) { it.withStyle(it.style.withHidden(hidden)) }

/**
 * The box of [node] in its own units (its transform not applied): its outline,
 * its text, its picture, or what a group holds; null when it draws nothing
 * the editor can measure. [style] is what it draws with (for a text's size).
 */
fun SvgDocument.ownBounds(node: SvgNode, measure: TextMeasure = TextMeasure.ESTIMATE, style: ResolvedStyle = resolvedStyle(node.key)): Box? = when (node) {
    is SvgShape -> SvgPath.bounds(node.segments())
    is SvgText -> textBox(node, style, measure)
    is SvgImage -> if (node.width > 0 && node.height > 0) Box(node.x, node.y, node.x + node.width, node.y + node.height) else null
    is SvgGroup -> {
        var box: Box? = null
        for (child in node.children) {
            val own = ownBounds(child, measure, style.inherit(child.style)) ?: continue
            val mapped = mapBox(own, child.transform)
            box = box?.union(mapped) ?: mapped
        }
        box
    }
    is SvgOther -> {
        val a = node.element.attributes
        val w = a["width"]?.let { cssLength(it) } ?: 0.0
        val h = a["height"]?.let { cssLength(it) } ?: 0.0
        if (w > 0 && h > 0) {
            val x = a["x"]?.let { cssLength(it) } ?: 0.0
            val y = a["y"]?.let { cssLength(it) } ?: 0.0
            Box(x, y, x + w, y + h)
        } else {
            null
        }
    }
}

/** The box of a text: its lines one under the other, where its anchor puts them. */
fun textBox(text: SvgText, style: ResolvedStyle, measure: TextMeasure = TextMeasure.ESTIMATE): Box? {
    val lines = text.lines
    if (lines.all { it.isEmpty() }) return null
    val size = style.fontSize
    val width = lines.maxOf { measure.width(it, style) }
    val left = when (style.textAnchor) {
        "middle" -> text.x - width / 2
        "end" -> text.x - width
        else -> text.x
    }
    // The ascent is about four fifths of the size, the descent a fifth.
    val top = text.y - size * 0.8
    val bottom = text.y + size * 0.2 + (lines.size - 1) * size * SVG_LINE_HEIGHT
    return Box(left, top, left + width, bottom)
}

/** The box around [box] once through [t] (its four corners). */
fun mapBox(box: Box, t: Affine): Box {
    val xs = doubleArrayOf(t.mapX(box.left, box.top), t.mapX(box.right, box.top), t.mapX(box.right, box.bottom), t.mapX(box.left, box.bottom))
    val ys = doubleArrayOf(t.mapY(box.left, box.top), t.mapY(box.right, box.top), t.mapY(box.right, box.bottom), t.mapY(box.left, box.bottom))
    return Box(xs.min(), ys.min(), xs.max(), ys.max())
}

/**
 * The four corners of the node [key]'s own box on the drawing, in pixels
 * (top left, top right, bottom right, bottom left): turned and slanted as it
 * is. Null when it has no box.
 */
fun SvgDocument.corners(key: Int, measure: TextMeasure = TextMeasure.ESTIMATE): DoubleArray? {
    val node = find(key) ?: return null
    val box = ownBounds(node, measure) ?: return null
    val t = worldTransform(key)
    return doubleArrayOf(
        t.mapX(box.left, box.top), t.mapY(box.left, box.top),
        t.mapX(box.right, box.top), t.mapY(box.right, box.top),
        t.mapX(box.right, box.bottom), t.mapY(box.right, box.bottom),
        t.mapX(box.left, box.bottom), t.mapY(box.left, box.bottom),
    )
}

/** The box of the node [key] on the drawing, in pixels; null when it has none. */
fun SvgDocument.worldBounds(key: Int, measure: TextMeasure = TextMeasure.ESTIMATE): Box? {
    val node = find(key) ?: return null
    return ownBounds(node, measure)?.let { mapBox(it, worldTransform(key)) }
}

/**
 * The node drawn on top at [x], [y] (pixels of the drawing): a shape where it
 * is filled or within [tolerance] pixels of its outline, a text or a picture
 * inside its box. Hidden nodes are passed over; groups are looked into (the
 * node found is never a group). Null when only the background is there.
 */
fun SvgDocument.hit(x: Double, y: Double, tolerance: Double = 0.0, measure: TextMeasure = TextMeasure.ESTIMATE): Int? =
    hits(x, y, tolerance, measure, limit = 1).firstOrNull()

/** Every node at [x], [y] as [hit] finds them, the top one first, at most [limit]. */
fun SvgDocument.hits(x: Double, y: Double, tolerance: Double = 0.0, measure: TextMeasure = TextMeasure.ESTIMATE, limit: Int = Int.MAX_VALUE): List<Int> {
    val found = ArrayList<Int>()
    fun walk(nodes: List<SvgNode>, parent: Affine, inherited: ResolvedStyle) {
        for (n in nodes.asReversed()) {
            if (found.size >= limit) return
            if (n.style.hidden) continue
            // A symmetry's copies follow their source (it is edited, not they); a locked node stays put.
            if (n.isSymmetryCopy || n.isLocked) continue
            val t = parent * n.transform
            val style = inherited.inherit(n.style)
            if (n is SvgGroup) {
                walk(n.children, t, style)
                continue
            }
            val inv = t.inverse()
            val lx = inv.mapX(x, y)
            val ly = inv.mapY(x, y)
            // The tolerance in the node's own units (its scale undone, on average).
            val scale = sqrt(abs(t.a * t.d - t.b * t.c)).takeIf { it > 1e-9 } ?: 1.0
            val tol = tolerance / scale
            val hit = when (n) {
                is SvgShape -> hitsShape(n, style, lx, ly, tol)
                is SvgText, is SvgImage, is SvgOther -> ownBounds(n, measure, style)?.let { b ->
                    lx >= b.left - tol && lx <= b.right + tol && ly >= b.top - tol && ly <= b.bottom + tol
                } ?: false
                is SvgGroup -> false
            }
            if (hit) found += n.key
        }
    }
    walk(nodes, viewTransform, ResolvedStyle())
    return found
}

/** Whether [x], [y] (the shape's own units) is on [shape]: filled there, or on its outline. */
private fun hitsShape(shape: SvgShape, style: ResolvedStyle, x: Double, y: Double, tolerance: Double): Boolean {
    val segments = shape.segments()
    val box = SvgPath.bounds(segments) ?: return false
    val reach = tolerance + (if (style.stroke.kind != PaintKind.NONE) style.strokeWidth / 2 else 0.0)
    if (x < box.left - reach || x > box.right + reach || y < box.top - reach || y > box.bottom + reach) return false
    val lines = flatten(segments)
    // A line has nothing inside; an open path or polyline fills as if closed.
    val filled = style.fill.kind != PaintKind.NONE && shape.tag != "line"
    if (filled && inside(lines, x, y, style.evenOdd)) return true
    // Near the outline: always for something thin, else where it is drawn.
    if (reach > 0) {
        for (line in lines) {
            val p = line.points
            var i = 0
            while (i + 3 < p.size) {
                if (distance(x, y, p[i], p[i + 1], p[i + 2], p[i + 3]) <= reach) return true
                i += 2
            }
            if (line.closed && p.size >= 4 && distance(x, y, p[p.size - 2], p[p.size - 1], p[0], p[1]) <= reach) return true
        }
    }
    return false
}

/** One piece of an outline as points (x, y, x, y...), closed or not. */
class Polyline(val points: DoubleArray, val closed: Boolean)

/** [segments] as straight pieces, curves cut into [steps] each. */
fun flatten(segments: List<Seg>, steps: Int = 12): List<Polyline> {
    val out = ArrayList<Polyline>()
    var current = ArrayList<Double>()
    var startX = 0.0
    var startY = 0.0
    var px = 0.0
    var py = 0.0
    fun finish(closed: Boolean) {
        if (current.size >= 4) out += Polyline(current.toDoubleArray(), closed)
        current = ArrayList()
    }
    for (s in segments) {
        when (s) {
            is Seg.Move -> {
                finish(false)
                current.add(s.x); current.add(s.y)
                startX = s.x; startY = s.y; px = s.x; py = s.y
            }
            is Seg.Line -> {
                if (current.isEmpty()) { current.add(px); current.add(py) }
                current.add(s.x); current.add(s.y)
                px = s.x; py = s.y
            }
            is Seg.Quad -> {
                if (current.isEmpty()) { current.add(px); current.add(py) }
                for (k in 1..steps) {
                    val u = k.toDouble() / steps; val v = 1 - u
                    current.add(v * v * px + 2 * v * u * s.cx + u * u * s.x)
                    current.add(v * v * py + 2 * v * u * s.cy + u * u * s.y)
                }
                px = s.x; py = s.y
            }
            is Seg.Cubic -> {
                if (current.isEmpty()) { current.add(px); current.add(py) }
                for (k in 1..steps) {
                    val u = k.toDouble() / steps; val v = 1 - u
                    current.add(v * v * v * px + 3 * v * v * u * s.c1x + 3 * v * u * u * s.c2x + u * u * u * s.x)
                    current.add(v * v * v * py + 3 * v * v * u * s.c1y + 3 * v * u * u * s.c2y + u * u * u * s.y)
                }
                px = s.x; py = s.y
            }
            Seg.Close -> {
                finish(true)
                px = startX; py = startY
            }
        }
    }
    finish(false)
    return out
}

/** Whether [x], [y] is inside [lines] (every piece closed for filling), by the non-zero or the even-odd rule. */
fun inside(lines: List<Polyline>, x: Double, y: Double, evenOdd: Boolean): Boolean {
    var winding = 0
    var crossings = 0
    for (line in lines) {
        val p = line.points
        val n = p.size / 2
        for (i in 0 until n) {
            val x1 = p[i * 2]; val y1 = p[i * 2 + 1]
            val j = (i + 1) % n
            val x2 = p[j * 2]; val y2 = p[j * 2 + 1]
            if ((y1 <= y) != (y2 <= y)) {
                val cx = x1 + (y - y1) / (y2 - y1) * (x2 - x1)
                if (cx > x) {
                    crossings++
                    winding += if (y2 > y1) 1 else -1
                }
            }
        }
    }
    return if (evenOdd) crossings % 2 == 1 else winding != 0
}

/** The distance from [x], [y] to the segment [x1], [y1] - [x2], [y2]. */
private fun distance(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    val dx = x2 - x1
    val dy = y2 - y1
    val len2 = dx * dx + dy * dy
    if (len2 < 1e-18) return hypot(x - x1, y - y1)
    val u = (((x - x1) * dx + (y - y1) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(x - (x1 + u * dx), y - (y1 + u * dy))
}

/** A size about [px], [py] by [sx], [sy], in pixels of the drawing. */
fun scaleAbout(sx: Double, sy: Double, px: Double, py: Double): Affine =
    Affine.translate(px, py) * Affine.scale(sx, sy) * Affine.translate(-px, -py)

/** The larger of a transform's two scales (how much bigger it draws, at most). */
fun Affine.maxScale(): Double = max(hypot(a, b), hypot(c, d))
