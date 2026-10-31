package com.cuscus.ffmpeggui.core.vector.svg

import kotlin.math.abs
import kotlin.math.max

/*
 * The SVG editor's professional tools, pure and tested:
 *
 *  - live symmetry: a group whose first child (the source) is what is drawn,
 *    and whose other children are copies of it, mirrored or turned about a
 *    centre, made again after every change, so that an edit on one side shows
 *    on all of them (two halves, four quarters, a turn of 3 to 12, a
 *    kaleidoscope). The copies are real SVG content (the file looks right
 *    anywhere) but cannot be picked: only the source is edited;
 *  - locking: a locked node cannot be picked on the stage;
 *  - repeating: in a row, in a grid, round a centre;
 *  - paths: simplified, smoothed, turned the other way, broken apart into
 *    their pieces, or several joined into one;
 *  - a grid to snap to.
 */

/** How the drawing is mirrored: left and right, top and bottom, four quarters, turned, or turned and mirrored. */
enum class SymmetryKind { MIRROR_X, MIRROR_Y, QUAD, RADIAL, KALEIDO }

/**
 * A symmetry about [cx], [cy] (the drawing's own units); [count] is how many
 * parts a turn has (RADIAL, KALEIDO: 2 to 24).
 */
data class Symmetry(val kind: SymmetryKind, val count: Int = 6, val cx: Double, val cy: Double) {

    /** How many parts the drawing shows, the source counted. */
    val parts: Int
        get() = when (kind) {
            SymmetryKind.MIRROR_X, SymmetryKind.MIRROR_Y -> 2
            SymmetryKind.QUAD -> 4
            SymmetryKind.RADIAL -> count.coerceIn(2, 24)
            SymmetryKind.KALEIDO -> 2 * count.coerceIn(2, 24)
        }

    /** The transforms that make the copies from the source (the source's own, the identity, left out). */
    fun transforms(): List<Affine> {
        val mirrorX = Affine.translate(cx, cy) * Affine.scale(-1.0, 1.0) * Affine.translate(-cx, -cy)
        val mirrorY = Affine.translate(cx, cy) * Affine.scale(1.0, -1.0) * Affine.translate(-cx, -cy)
        val n = count.coerceIn(2, 24)
        return when (kind) {
            SymmetryKind.MIRROR_X -> listOf(mirrorX)
            SymmetryKind.MIRROR_Y -> listOf(mirrorY)
            SymmetryKind.QUAD -> listOf(mirrorX, mirrorY, mirrorX * mirrorY)
            SymmetryKind.RADIAL -> (1 until n).map { Affine.rotate(360.0 * it / n, cx, cy) }
            SymmetryKind.KALEIDO -> (1 until n).map { Affine.rotate(360.0 * it / n, cx, cy) } +
                (0 until n).map { Affine.rotate(360.0 * it / n, cx, cy) * mirrorX }
        }
    }

    /** The lines the parts meet on, through the centre: their angles in degrees (0: straight up). */
    fun axes(): List<Double> = when (kind) {
        SymmetryKind.MIRROR_X -> listOf(0.0)
        SymmetryKind.MIRROR_Y -> listOf(90.0)
        SymmetryKind.QUAD -> listOf(0.0, 90.0)
        SymmetryKind.RADIAL, SymmetryKind.KALEIDO -> {
            val n = count.coerceIn(2, 24)
            (0 until n).map { 360.0 * it / n }
        }
    }

    /** As written on the group: "kind;count;cx;cy". */
    fun write(): String = "${kind.name};$count;${svgNumber(cx)};${svgNumber(cy)}"

    companion object {
        fun parse(text: String?): Symmetry? {
            val p = text?.split(';') ?: return null
            if (p.size != 4) return null
            val kind = SymmetryKind.entries.firstOrNull { it.name == p[0] } ?: return null
            return Symmetry(kind, p[1].toIntOrNull() ?: 6, p[2].toDoubleOrNull() ?: return null, p[3].toDoubleOrNull() ?: return null)
        }

        /** On the group that holds a symmetry: what it is. */
        const val GROUP = "data-ffg-symmetry"

        /** On a copy a symmetry made: which one (1, 2...). */
        const val COPY = "data-ffg-copy"

        /** On a locked node. */
        const val LOCKED = "data-ffg-locked"
    }
}

/** Whether this node is a copy a symmetry makes (it cannot be picked: its source is edited). */
val SvgNode.isSymmetryCopy: Boolean get() = attributes.containsKey(Symmetry.COPY)

/** Whether this node is locked (it cannot be picked on the stage). */
val SvgNode.isLocked: Boolean get() = attributes[Symmetry.LOCKED] == "1"

/** The group that holds the drawing's symmetry, when it has one (the first, at the top level). */
fun SvgDocument.symmetryGroup(): SvgGroup? = nodes.firstOrNull { it is SvgGroup && it.attributes.containsKey(Symmetry.GROUP) } as? SvgGroup

/** The drawing's symmetry, when it has one. */
fun SvgDocument.symmetry(): Symmetry? = symmetryGroup()?.let { Symmetry.parse(it.attributes[Symmetry.GROUP]) }

/** What the symmetry copies: the first child of its group. */
fun SvgDocument.symmetrySource(): SvgGroup? = symmetryGroup()?.children?.firstOrNull() as? SvgGroup

/**
 * The drawing with [symmetry]: on the group there is (its source kept), or a
 * new one at the top holding an empty source; the copies made. With the
 * source's key, where what is drawn next goes.
 */
fun SvgDocument.withSymmetry(symmetry: Symmetry): Pair<SvgDocument, Int> {
    val existing = symmetryGroup()
    val d = if (existing != null) {
        update(existing.key) { (it as SvgGroup).copy(attributes = it.attributes + (Symmetry.GROUP to symmetry.write())) }
    } else {
        val source = SvgGroup(SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(), mapOf("inkscape:label" to "Symmetry source"), emptyList())
        val group = SvgGroup(
            SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(),
            mapOf(Symmetry.GROUP to symmetry.write(), "inkscape:label" to "Symmetry"),
            listOf(source),
        )
        copy(nodes = nodes + group)
    }
    val made = d.mirrored()
    return made to (made.symmetrySource()?.key ?: -1)
}

/**
 * The drawing without its symmetry. With [keepCopies] the copies stay as
 * ordinary groups (they no longer follow the source); else they go. Either
 * way what was drawn stays where it was, in an ordinary group.
 */
fun SvgDocument.withoutSymmetry(keepCopies: Boolean): SvgDocument {
    val group = symmetryGroup() ?: return this
    return update(group.key) { n ->
        val g = n as SvgGroup
        val children = if (keepCopies) {
            g.children.map { c -> if (c is SvgGroup) c.copy(attributes = c.attributes - Symmetry.COPY) else c }
        } else {
            g.children.filter { !it.isSymmetryCopy }
        }
        g.copy(attributes = g.attributes - Symmetry.GROUP + ("inkscape:label" to "Mirrored"), children = children)
    }
}

/**
 * The drawing with its symmetry's copies made again from the source (nothing
 * to do without a symmetry). A copy keeps the keys it had where its shape is
 * the same, so that what was worked out for drawing it is kept.
 */
fun SvgDocument.mirrored(): SvgDocument {
    val group = symmetryGroup() ?: return this
    val symmetry = Symmetry.parse(group.attributes[Symmetry.GROUP]) ?: return this
    val source = group.children.firstOrNull() as? SvgGroup ?: return this
    val old = group.children.drop(1).filter { it.isSymmetryCopy }
    val copies = symmetry.transforms().mapIndexed { i, t ->
        val fresh = SvgGroup(
            SvgKeys.next(), null, t * source.transform, source.style,
            mapOf(Symmetry.COPY to (i + 1).toString()),
            source.children.map { it.duplicated() },
        )
        old.getOrNull(i)?.let { rekeyedLike(fresh, it) } ?: fresh
    }
    // Whatever else was put in the group (not a copy) stays, after the copies.
    val others = group.children.drop(1).filter { !it.isSymmetryCopy }
    return update(group.key) { (it as SvgGroup).copy(children = listOf(source) + copies + others) }
}

/** [made] with the keys of [like] wherever the two have the same shape of tree. */
private fun rekeyedLike(made: SvgNode, like: SvgNode): SvgNode {
    if (made::class != like::class) return made
    return when (made) {
        is SvgGroup -> {
            val old = (like as SvgGroup).children
            made.copy(key = like.key, children = made.children.mapIndexed { i, c -> old.getOrNull(i)?.let { rekeyedLike(c, it) } ?: c })
        }
        is SvgShape -> made.copy(key = like.key)
        is SvgText -> made.copy(key = like.key)
        is SvgImage -> made.copy(key = like.key)
        is SvgOther -> made.copy(key = like.key)
    }
}

/** The drawing with [node] last (on top) in the group [groupKey]; on top of the drawing when there is no such group. */
fun SvgDocument.insertInto(groupKey: Int?, node: SvgNode): SvgDocument {
    if (groupKey == null || find(groupKey) !is SvgGroup) return copy(nodes = nodes + node)
    return update(groupKey) { (it as SvgGroup).copy(children = it.children + node) }
}

/** The drawing with the node [key] locked or not. */
fun SvgDocument.setLocked(key: Int, locked: Boolean): SvgDocument = update(key) { n ->
    val attributes = if (locked) n.attributes + (Symmetry.LOCKED to "1") else n.attributes - Symmetry.LOCKED
    when (n) {
        is SvgGroup -> n.copy(attributes = attributes)
        is SvgShape -> n.copy(attributes = attributes)
        is SvgText -> n.copy(attributes = attributes)
        is SvgImage -> n.copy(attributes = attributes)
        is SvgOther -> n.copy(attributes = attributes)
    }
}

// -- Repeating ------------------------------------------------------------------------

/**
 * [count] copies in all of what [keys] are (the originals the first), each
 * [dx], [dy] pixels of the drawing on from the one before. With every key.
 */
fun SvgDocument.repeatInRow(keys: Collection<Int>, count: Int, dx: Double, dy: Double): Pair<SvgDocument, List<Int>> =
    repeated(keys, (1 until count.coerceAtLeast(1)).map { Affine.translate(dx * it, dy * it) })

/** [columns] x [rows] of what [keys] are, [stepX], [stepY] pixels apart (the originals top left). */
fun SvgDocument.repeatInGrid(keys: Collection<Int>, columns: Int, rows: Int, stepX: Double, stepY: Double): Pair<SvgDocument, List<Int>> {
    val moves = ArrayList<Affine>()
    for (r in 0 until rows.coerceAtLeast(1)) for (c in 0 until columns.coerceAtLeast(1)) {
        if (r == 0 && c == 0) continue
        moves += Affine.translate(stepX * c, stepY * r)
    }
    return repeated(keys, moves)
}

/** [count] of what [keys] are, turned evenly about [cx], [cy] (pixels of the drawing). */
fun SvgDocument.repeatAround(keys: Collection<Int>, count: Int, cx: Double, cy: Double): Pair<SvgDocument, List<Int>> {
    val n = count.coerceAtLeast(1)
    return repeated(keys, (1 until n).map { Affine.rotate(360.0 * it / n, cx, cy) })
}

/** A copy of what [keys] are for each of [moves] (pixels of the drawing), just above them, in order. */
private fun SvgDocument.repeated(keys: Collection<Int>, moves: List<Affine>): Pair<SvgDocument, List<Int>> {
    val top = outermost(keys)
    if (top.isEmpty()) return this to emptyList()
    var d = this
    val all = ArrayList<Int>(top)
    // Each copy goes above the last one made of the same node: the stack stays in order.
    val last = top.associateWith { it }.toMutableMap()
    for (m in moves) {
        for (k in top) {
            val (next, copy) = d.duplicate(last.getValue(k)) ?: continue
            // The copy is where the last one was: brought back to the original's place, then moved.
            val back = d.worldTransform(k) * d.worldTransform(last.getValue(k)).inverse()
            d = next.transformInWorld(copy, m * back)
            last[k] = copy
            all += copy
        }
    }
    return d to all
}

/** A mirrored copy of what [keys] are, just above them, flipped across ([horizontal]) or down about their box. */
fun SvgDocument.mirrorCopy(keys: Collection<Int>, horizontal: Boolean, measure: TextMeasure = TextMeasure.ESTIMATE): Pair<SvgDocument, List<Int>> {
    val top = outermost(keys)
    var d = this
    val made = ArrayList<Int>()
    for (k in top) {
        val (next, copy) = d.duplicate(k) ?: continue
        d = next
        made += copy
    }
    if (made.isEmpty()) return this to emptyList()
    return d.flip(made, horizontal, measure) to made
}

// -- Paths ----------------------------------------------------------------------------

/** The pieces of a path: each a list of segments starting with a move. */
fun subpaths(segments: List<Seg>): List<List<Seg>> {
    val out = ArrayList<List<Seg>>()
    var cur = ArrayList<Seg>()
    for (s in segments) {
        if (s is Seg.Move && cur.isNotEmpty()) {
            out += cur
            cur = ArrayList()
        }
        cur += s
    }
    if (cur.isNotEmpty()) out += cur
    return out.filter { p -> p.any { it !is Seg.Move } }
}

/** The points a piece goes through (its ends of segments, curves' controls left out), and whether it is closed. */
private fun corners(piece: List<Seg>): Pair<DoubleArray, Boolean> {
    val pts = ArrayList<Double>()
    var closed = false
    for (s in piece) {
        when (s) {
            is Seg.Move -> { pts += s.x; pts += s.y }
            is Seg.Line -> { pts += s.x; pts += s.y }
            is Seg.Quad -> { pts += s.x; pts += s.y }
            is Seg.Cubic -> { pts += s.x; pts += s.y }
            Seg.Close -> closed = true
        }
    }
    // A closed piece that comes back to its start by a line: the start once.
    if (closed && pts.size >= 4 && abs(pts[0] - pts[pts.size - 2]) < 1e-9 && abs(pts[1] - pts[pts.size - 1]) < 1e-9) {
        pts.removeAt(pts.size - 1)
        pts.removeAt(pts.size - 1)
    }
    return pts.toDoubleArray() to closed
}

/**
 * [segments] with fewer points: each piece drawn as a polyline, its points
 * thinned within [tolerance] and smooth curves laid through what is left.
 */
fun simplifyPath(segments: List<Seg>, tolerance: Double): List<Seg> = subpaths(segments).flatMap { piece ->
    val closed = piece.any { it == Seg.Close }
    val line = flatten(piece, steps = 8).firstOrNull() ?: return@flatMap piece
    val out = smoothStroke(line.points, tolerance, closed = closed)
    if (out.isEmpty()) piece else out
}

/** [segments] with smooth curves through the same points (corners rounded off). */
fun smoothPath(segments: List<Seg>): List<Seg> = subpaths(segments).flatMap { piece ->
    val (pts, closed) = corners(piece)
    val n = pts.size / 2
    if (n < 3) return@flatMap piece
    fun x(i: Int) = pts[2 * Math.floorMod(i, n)]
    fun y(i: Int) = pts[2 * Math.floorMod(i, n) + 1]
    fun cx(i: Int) = if (closed) x(i) else pts[2 * i.coerceIn(0, n - 1)]
    fun cy(i: Int) = if (closed) y(i) else pts[2 * i.coerceIn(0, n - 1) + 1]
    val out = ArrayList<Seg>()
    out += Seg.Move(x(0), y(0))
    val last = if (closed) n else n - 1
    for (i in 0 until last) {
        // Catmull-Rom through i-1, i, i+1, i+2 as one cubic from i to i+1.
        val c1x = cx(i) + (cx(i + 1) - cx(i - 1)) / 6
        val c1y = cy(i) + (cy(i + 1) - cy(i - 1)) / 6
        val c2x = cx(i + 1) - (cx(i + 2) - cx(i)) / 6
        val c2y = cy(i + 1) - (cy(i + 2) - cy(i)) / 6
        out += Seg.Cubic(c1x, c1y, c2x, c2y, cx(i + 1), cy(i + 1))
    }
    if (closed) out += Seg.Close
    out
}

/** [segments] drawn the other way: every piece from its end to its start (a line's arrows swap). */
fun reversePath(segments: List<Seg>): List<Seg> = subpaths(segments).flatMap { piece ->
    val closed = piece.any { it == Seg.Close }
    // The pieces as (start, segment) pairs, then walked back.
    var px = 0.0
    var py = 0.0
    val parts = ArrayList<Triple<Double, Double, Seg>>()
    for (s in piece) {
        when (s) {
            is Seg.Move -> { px = s.x; py = s.y }
            is Seg.Line -> { parts += Triple(px, py, s); px = s.x; py = s.y }
            is Seg.Quad -> { parts += Triple(px, py, s); px = s.x; py = s.y }
            is Seg.Cubic -> { parts += Triple(px, py, s); px = s.x; py = s.y }
            Seg.Close -> Unit
        }
    }
    if (parts.isEmpty()) return@flatMap piece
    val out = ArrayList<Seg>()
    out += Seg.Move(px, py)
    for ((sx, sy, s) in parts.asReversed()) {
        out += when (s) {
            is Seg.Line -> Seg.Line(sx, sy)
            is Seg.Quad -> Seg.Quad(s.cx, s.cy, sx, sy)
            is Seg.Cubic -> Seg.Cubic(s.c2x, s.c2y, s.c1x, s.c1y, sx, sy)
            else -> s
        }
    }
    if (closed) out += Seg.Close
    out
}

/** A shape's path changed by [change] (a basic shape first made a path); anything else as it is. */
fun SvgDocument.reshape(key: Int, change: (List<Seg>) -> List<Seg>): SvgDocument = update(key) { n ->
    if (n !is SvgShape) return@update n
    val segs = n.segments()
    if (segs.isEmpty()) return@update n
    val made = change(segs)
    if (made.isEmpty()) n else n.copy(tag = "path", geometry = linkedMapOf("d" to SvgPath.write(made)))
}

/**
 * Each piece of the path [key] a shape of its own (its style and place kept),
 * in its place. With their keys; null when it has one piece or is no shape.
 */
fun SvgDocument.breakApart(key: Int): Pair<SvgDocument, List<Int>>? {
    val shape = find(key) as? SvgShape ?: return null
    val pieces = subpaths(shape.segments())
    if (pieces.size < 2) return null
    var d = this
    var below = key
    val made = ArrayList<Int>()
    for (p in pieces) {
        val piece = shape.copy(key = SvgKeys.next(), id = null, tag = "path", geometry = linkedMapOf("d" to SvgPath.write(p)))
        d = d.insertAbove(below, piece)
        below = piece.key
        made += piece.key
    }
    return d.remove(key) to made
}

/**
 * The shapes [keys] joined into one path (where the first one is, with its
 * style; holes where they overlap, drawn even-odd). With its key; null for
 * fewer than two shapes.
 */
fun SvgDocument.joinPaths(keys: Collection<Int>): Pair<SvgDocument, Int>? {
    val shapes = outermost(keys).filter { find(it) is SvgShape }
    if (shapes.size < 2) return null
    val first = find(shapes.first()) as SvgShape
    val toLocal = worldTransform(first.key).inverse()
    val all = shapes.flatMap { k -> worldOutline(k)?.let { SvgPath.transform(it, toLocal) }.orEmpty() }
    if (all.isEmpty()) return null
    val joined = first.copy(
        key = SvgKeys.next(), id = null, tag = "path",
        style = first.style.copy(fillRule = "evenodd"),
        geometry = linkedMapOf("d" to SvgPath.write(all)),
    )
    var d = insertAbove(first.key, joined)
    for (k in shapes) d = d.remove(k)
    return d to joined.key
}

// -- The grid -------------------------------------------------------------------------

/** [this] with the lines of a grid [step] pixels apart across a [width] x [height] drawing added, to snap to. */
fun SnapTargets.withGrid(step: Double, width: Double, height: Double): SnapTargets {
    if (step <= 0.0) return this
    val xs = ArrayList<Double>()
    var x = 0.0
    while (x <= width + 1e-9) { xs += x; x += step }
    val ys = ArrayList<Double>()
    var y = 0.0
    while (y <= height + 1e-9) { ys += y; y += step }
    return SnapTargets(this.xs + xs.toDoubleArray(), this.ys + ys.toDoubleArray())
}

/** [value] on the nearest line of a grid [step] apart (as it is without a grid). */
fun onGrid(value: Double, step: Double): Double = if (step <= 0.0) value else Math.round(value / step) * step

/** The step of a grid of [divisions] across the shorter side of a [width] x [height] drawing. */
fun gridStep(divisions: Int, width: Double, height: Double): Double = if (divisions <= 0) 0.0 else max(1.0, minOf(width, height) / divisions)
