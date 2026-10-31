package com.cuscus.ffmpeggui.core.vector.svg

import kotlin.math.abs
import kotlin.math.hypot

/*
 * The editor's finer tools on a drawing:
 *
 *  - sizing a node in its own units, a side at a time (a square becomes a
 *    rectangle), written into its geometry where it has one, so that its
 *    outline keeps its width and its corners their roundness;
 *  - magnetic snapping, as presentation programs do it: a box being moved
 *    lands on the edges and centres of the others and of the page, and the
 *    guides it lands on are drawn;
 *  - connectors: a line whose ends hold on to two shapes (data-connect-from
 *    and data-connect-to, the shapes' ids) and follows them when they move;
 *  - a finger's stroke made into a smooth path of cubic curves.
 */

/** The attribute naming the shape a connector starts at (its id). */
const val CONNECT_FROM = "data-connect-from"

/** The attribute naming the shape a connector ends at (its id). */
const val CONNECT_TO = "data-connect-to"

/**
 * The drawing with the node [key] sized by [sx], [sy] about [px], [py] in its
 * own units (its transform not applied). A shape's geometry takes it (a circle
 * sized one way becomes an ellipse); a picture's box too; a text, a group or
 * an element kept as it was takes it in its transform.
 */
fun SvgDocument.resizeOwn(key: Int, sx: Double, sy: Double, px: Double, py: Double): SvgDocument =
    update(key) { resized(it, sx, sy, px, py) }

/** [node] sized as [resizeOwn] says. */
fun resized(node: SvgNode, sx: Double, sy: Double, px: Double, py: Double): SvgNode {
    fun mx(x: Double) = px + (x - px) * sx
    fun my(y: Double) = py + (y - py) * sy
    fun inTransform() = node.withTransform(node.transform * Affine.translate(px, py) * Affine.scale(sx, sy) * Affine.translate(-px, -py))
    // A flip, or nothing left: in the transform, where it is exact.
    if (sx <= 0 || sy <= 0) return inTransform()
    return when (node) {
        is SvgShape -> {
            val g = LinkedHashMap(node.geometry)
            fun get(k: String) = g[k]?.let { cssLength(it) } ?: 0.0
            when (node.tag) {
                "rect" -> {
                    val x = get("x"); val y = get("y")
                    g["x"] = n(mx(x)); g["y"] = n(my(y))
                    g["width"] = n(get("width") * sx); g["height"] = n(get("height") * sy)
                    node.copy(geometry = g)
                }
                "circle" -> {
                    val cx = get("cx"); val cy = get("cy"); val r = get("r")
                    if (abs(sx - sy) < 1e-9) {
                        g["cx"] = n(mx(cx)); g["cy"] = n(my(cy)); g["r"] = n(r * sx)
                        node.copy(geometry = g)
                    } else {
                        node.copy(tag = "ellipse", geometry = linkedMapOf("cx" to n(mx(cx)), "cy" to n(my(cy)), "rx" to n(r * sx), "ry" to n(r * sy)))
                    }
                }
                "ellipse" -> {
                    g["cx"] = n(mx(get("cx"))); g["cy"] = n(my(get("cy")))
                    g["rx"] = n(get("rx") * sx); g["ry"] = n(get("ry") * sy)
                    node.copy(geometry = g)
                }
                "line" -> {
                    g["x1"] = n(mx(get("x1"))); g["y1"] = n(my(get("y1")))
                    g["x2"] = n(mx(get("x2"))); g["y2"] = n(my(get("y2")))
                    node.copy(geometry = g)
                }
                "polyline", "polygon" -> {
                    val v = numbers(g["points"].orEmpty())
                    g["points"] = v.chunked(2).filter { it.size == 2 }.joinToString(" ") { (x, y) -> n(mx(x)) + "," + n(my(y)) }
                    node.copy(geometry = g)
                }
                "path" -> {
                    g["d"] = SvgPath.write(SvgPath.transform(SvgPath.parse(g["d"]), Affine.translate(px, py) * Affine.scale(sx, sy) * Affine.translate(-px, -py)))
                    node.copy(geometry = g)
                }
                else -> inTransform()
            }
        }
        is SvgImage -> node.copy(x = mx(node.x), y = my(node.y), width = node.width * sx, height = node.height * sy)
        else -> inTransform()
    }
}

/** Where a moved box lands: how far it moves to snap, and the guides it snapped to (x of the vertical ones, y of the horizontal ones). */
data class Snap(val dx: Double, val dy: Double, val xs: List<Double>, val ys: List<Double>) {
    companion object {
        val NONE = Snap(0.0, 0.0, emptyList(), emptyList())
    }
}

/** What a box can snap to: lines at these x and these y (pixels of the drawing). */
class SnapTargets(val xs: DoubleArray, val ys: DoubleArray)

/**
 * The guides of the drawing for moving the node [key]: the page's edges and
 * middle, and the edges and middles of the other nodes (at most [limit], the
 * top of the drawing and the first level of groups first; not the node, what
 * it holds or the groups it is in). Hidden nodes give none.
 */
fun SvgDocument.snapTargets(key: Int, measure: TextMeasure = TextMeasure.ESTIMATE, limit: Int = 400): SnapTargets =
    snapTargets(listOf(key), measure, limit)

/** The guides for moving [keys] together: as for one node, none of them giving any. */
fun SvgDocument.snapTargets(keys: Collection<Int>, measure: TextMeasure = TextMeasure.ESTIMATE, limit: Int = 400): SnapTargets {
    val xs = ArrayList<Double>()
    val ys = ArrayList<Double>()
    xs += listOf(0.0, width / 2, width)
    ys += listOf(0.0, height / 2, height)
    val skip = HashSet<Int>()
    for (key in keys) {
        pathTo(key)?.forEach { skip += it.key }
        (find(key) as? SvgGroup)?.let { g -> SvgDocument(width, height, viewBox, emptyList(), g.children).allNodes().forEach { skip += it.key } }
    }
    var count = 0
    fun add(n: SvgNode) {
        if (count >= limit || n.key in skip || n.style.hidden) return
        val b = worldBounds(n.key, measure) ?: return
        count++
        xs += listOf(b.left, b.centerX, b.right)
        ys += listOf(b.top, b.centerY, b.bottom)
    }
    for (n in nodes) {
        if (n is SvgGroup && n.key in skip) {
            // The node's own group: its other members are its neighbours.
            n.children.forEach(::add)
        } else {
            add(n)
        }
    }
    for (n in nodes) if (n is SvgGroup && n.key !in skip) n.children.forEach(::add)
    return SnapTargets(xs.toDoubleArray(), ys.toDoubleArray())
}

/**
 * Where [box] snaps among [targets] within [threshold]: its left, middle or
 * right on the nearest vertical guide, its top, middle or bottom on the
 * nearest horizontal one, each way on its own.
 */
fun snapBox(box: Box, targets: SnapTargets, threshold: Double): Snap {
    fun best(own: DoubleArray, lines: DoubleArray): Pair<Double, List<Double>> {
        var bestD = Double.MAX_VALUE
        for (o in own) for (l in lines) {
            val d = l - o
            if (abs(d) <= threshold && abs(d) < abs(bestD)) bestD = d
        }
        if (bestD == Double.MAX_VALUE) return 0.0 to emptyList()
        // Every guide the box now lies on (an edge and a middle may meet two).
        val on = lines.filter { l -> own.any { abs(it + bestD - l) < 1e-6 } }.distinct()
        return bestD to on
    }
    val (dx, gx) = best(doubleArrayOf(box.left, box.centerX, box.right), targets.xs)
    val (dy, gy) = best(doubleArrayOf(box.top, box.centerY, box.bottom), targets.ys)
    return Snap(dx, dy, gx, gy)
}

/** [degrees] on the nearest multiple of [step] when within [within] of it. */
fun snapAngle(degrees: Double, step: Double = 15.0, within: Double = 4.0): Double {
    val nearest = Math.round(degrees / step) * step
    return if (abs(degrees - nearest) <= within) nearest else degrees
}

/** The node with the id [id]; null when there is none. */
fun SvgDocument.findById(id: String): SvgNode? = allNodes().firstOrNull { it.id == id }

/** The drawing where the node [key] has an id (made up when it had none), and that id. */
fun SvgDocument.ensureId(key: Int, prefix: String = "shape"): Pair<SvgDocument, String>? {
    val node = find(key) ?: return null
    node.id?.let { return this to it }
    val taken = allNodes().mapNotNull { it.id }.toSet()
    var i = 1
    while ("$prefix-$i" in taken) i++
    val id = "$prefix-$i"
    return update(key) { n ->
        when (n) {
            is SvgGroup -> n.copy(id = id)
            is SvgShape -> n.copy(id = id)
            is SvgText -> n.copy(id = id)
            is SvgImage -> n.copy(id = id)
            is SvgOther -> n.copy(id = id)
        }
    } to id
}

/** Where on its shape a connector's start holds: "u v", fractions of the shape's own box (absent: the side facing the other end). */
const val CONNECT_FROM_AT = "data-connect-from-at"

/** Where on its shape a connector's end holds (see [CONNECT_FROM_AT]). */
const val CONNECT_TO_AT = "data-connect-to-at"

/** How a connector runs: "elbow" (square turns), "curve", or absent (straight). */
const val CONNECT_STYLE = "data-connect-style"

/** The ends a connector has arrows at: "end", "start" or "both" (absent: none). */
const val CONNECT_ARROWS = "data-connect-arrows"

/** A path connector's ends ("x1 y1 x2 y2", its own units): where a free end stays. */
const val CONNECT_ENDS = "data-connect-ends"

/** How a connector runs. */
enum class ConnectorStyle { STRAIGHT, ELBOW, CURVE }

/** The nine places a connector snaps to on a shape (fractions of its box): corners, sides' middles, middle. */
val ANCHORS: List<Pair<Double, Double>> = listOf(
    0.0 to 0.0, 0.5 to 0.0, 1.0 to 0.0,
    0.0 to 0.5, 0.5 to 0.5, 1.0 to 0.5,
    0.0 to 1.0, 0.5 to 1.0, 1.0 to 1.0,
)

/**
 * The drawing with the line [line]'s end ([start] or the other) held on to
 * the node [target] (null: let go), at [at] on it (fractions of its own box;
 * null: the side facing the other end, moving round as the shapes move).
 */
fun SvgDocument.connect(line: Int, start: Boolean, target: Int?, at: Pair<Double, Double>? = null): SvgDocument {
    val attr = if (start) CONNECT_FROM else CONNECT_TO
    val atAttr = if (start) CONNECT_FROM_AT else CONNECT_TO_AT
    if (target == null) {
        return update(line) { n -> if (n is SvgShape) n.copy(attributes = n.attributes - attr - atAttr) else n }
    }
    val (withId, id) = ensureId(target) ?: return this
    return withId.update(line) { n ->
        if (n !is SvgShape) return@update n
        val attrs = n.attributes + (attr to id)
        n.copy(attributes = if (at == null) attrs - atAttr else attrs + (atAttr to "${n4(at.first)} ${n4(at.second)}"))
    }.reroute()
}

/** Whether a node is a connector: a line or a path that holds a shape, or runs, or has arrows, as connectors do. */
val SvgNode.isConnector: Boolean
    get() = this is SvgShape && (tag == "line" || tag == "path") &&
        (CONNECT_FROM in attributes || CONNECT_TO in attributes || CONNECT_STYLE in attributes || CONNECT_ARROWS in attributes)

/** Whether a node has two ends to drag: a line, or a connector. */
val SvgNode.isLineLike: Boolean get() = this is SvgShape && (tag == "line" || isConnector)

/** A connector's or a line's two ends in its own units (x1, y1, x2, y2); null for anything else. */
fun SvgShape.ends(): DoubleArray? {
    fun g(k: String) = geometry[k]?.let { cssLength(it) } ?: 0.0
    if (tag == "line") return doubleArrayOf(g("x1"), g("y1"), g("x2"), g("y2"))
    if (tag != "path") return null
    attributes[CONNECT_ENDS]?.let { numbers(it) }?.takeIf { it.size == 4 }?.let { return it.toDoubleArray() }
    // Without them: the first stroke's first and last points.
    val segs = SvgPath.parse(geometry["d"])
    val first = segs.firstOrNull() as? Seg.Move ?: return null
    var lx = first.x
    var ly = first.y
    for (seg in segs.drop(1)) {
        when (seg) {
            is Seg.Move, Seg.Close -> break
            is Seg.Line -> { lx = seg.x; ly = seg.y }
            is Seg.Quad -> { lx = seg.x; ly = seg.y }
            is Seg.Cubic -> { lx = seg.x; ly = seg.y }
        }
    }
    return doubleArrayOf(first.x, first.y, lx, ly)
}

/** The ends of the line-like node [key] on the drawing (x1, y1, x2, y2, pixels). */
fun SvgDocument.lineEnds(key: Int): DoubleArray? {
    val n = find(key) as? SvgShape ?: return null
    val e = n.ends() ?: return null
    val t = worldTransform(key)
    return doubleArrayOf(t.mapX(e[0], e[1]), t.mapY(e[0], e[1]), t.mapX(e[2], e[3]), t.mapY(e[2], e[3]))
}

/** The drawing with an end ([first] or the other) of the line-like node [key] at [x], [y] (pixels of the drawing). */
fun SvgDocument.moveLineEnd(key: Int, first: Boolean, x: Double, y: Double): SvgDocument {
    val inv = worldTransform(key).inverse()
    val lx = inv.mapX(x, y)
    val ly = inv.mapY(x, y)
    return update(key) { n ->
        if (n !is SvgShape) return@update n
        val e = n.ends() ?: return@update n
        if (first) { e[0] = lx; e[1] = ly } else { e[2] = lx; e[3] = ly }
        withEnds(n, e)
    }.reroute()
}

/** [line] with its ends at [e] (own units): a line's geometry, or a path connector's ends drawn again. */
private fun withEnds(line: SvgShape, e: DoubleArray): SvgShape {
    if (line.tag == "line") {
        return line.copy(geometry = LinkedHashMap(line.geometry).apply {
            put("x1", n(e[0])); put("y1", n(e[1])); put("x2", n(e[2])); put("y2", n(e[3]))
        })
    }
    return line.copy(attributes = line.attributes + (CONNECT_ENDS to e.joinToString(" ") { n(it) }))
}

/** How the connector [n] runs. */
val SvgShape.connectorStyle: ConnectorStyle
    get() = when (attributes[CONNECT_STYLE]) {
        "elbow" -> ConnectorStyle.ELBOW
        "curve" -> ConnectorStyle.CURVE
        else -> ConnectorStyle.STRAIGHT
    }

/** [line] running as [style] with arrows at [arrows] ("end", "start", "both" or null), drawn as such at once. */
fun SvgDocument.restyleConnector(key: Int, style: ConnectorStyle, arrows: String?): SvgDocument = update(key) { node ->
    val line = node as? SvgShape ?: return@update node
    if (!line.isLineLike) return@update node
    val e = line.ends() ?: return@update node
    var attrs = line.attributes - CONNECT_STYLE - CONNECT_ARROWS
    if (style != ConnectorStyle.STRAIGHT) attrs = attrs + (CONNECT_STYLE to style.name.lowercase())
    if (arrows != null) attrs = attrs + (CONNECT_ARROWS to arrows)
    val plain = style == ConnectorStyle.STRAIGHT && arrows == null
    if (plain) {
        // A plain straight connector is a line again.
        SvgShape(line.key, line.id, line.transform, line.style, attrs - CONNECT_ENDS, "line", linkedMapOf("x1" to n(e[0]), "y1" to n(e[1]), "x2" to n(e[2]), "y2" to n(e[3])))
    } else {
        val path = line.copy(tag = "path", attributes = attrs + (CONNECT_ENDS to e.joinToString(" ") { n(it) }), geometry = linkedMapOf("d" to ""))
        // Filled connectors would fill their turns: outlines only.
        path.copy(style = path.style.copy(fill = SvgPaint.NONE))
    }
}.reroute(force = key)

/**
 * The drawing with every connector's ends on the shapes they hold, each where
 * it was put on its shape (or, put nowhere in particular, where the line
 * between the two shapes leaves it), and its run (straight, elbow, curve) and
 * arrows drawn again between them. [force] draws the node with that key again
 * even if nothing moved.
 */
fun SvgDocument.reroute(measure: TextMeasure = TextMeasure.ESTIMATE, force: Int? = null): SvgDocument {
    val connectors = allNodes().filter { it.isConnector }
    if (connectors.isEmpty()) return this
    var doc = this
    for (c in connectors) {
        val line = c as SvgShape
        val t = doc.worldTransform(line.key)
        val inv = t.inverse()
        val e = line.ends() ?: continue
        var ax = t.mapX(e[0], e[1]); var ay = t.mapY(e[0], e[1])
        var bx = t.mapX(e[2], e[3]); var by = t.mapY(e[2], e[3])
        val from = line.attributes[CONNECT_FROM]?.let { doc.findById(it) }?.takeIf { it.key != line.key }
        val to = line.attributes[CONNECT_TO]?.let { doc.findById(it) }?.takeIf { it.key != line.key }
        val fromBox = from?.let { doc.worldBounds(it.key, measure) }
        val toBox = to?.let { doc.worldBounds(it.key, measure) }
        val fromAt = line.attributes[CONNECT_FROM_AT]?.let { numbers(it) }?.takeIf { it.size == 2 }
        val toAt = line.attributes[CONNECT_TO_AT]?.let { numbers(it) }?.takeIf { it.size == 2 }
        // The far ends first, where each end aims.
        val aimA = fromAt?.let { doc.anchorPoint(from!!.key, it[0], it[1], measure) }
        val aimB = toAt?.let { doc.anchorPoint(to!!.key, it[0], it[1], measure) }
        val fx = aimA?.first ?: fromBox?.centerX ?: ax
        val fy = aimA?.second ?: fromBox?.centerY ?: ay
        val tx = aimB?.first ?: toBox?.centerX ?: bx
        val ty = aimB?.second ?: toBox?.centerY ?: by
        var dirA: Pair<Double, Double>? = null
        var dirB: Pair<Double, Double>? = null
        if (from != null && fromBox != null) {
            if (aimA != null) {
                ax = aimA.first; ay = aimA.second
                dirA = doc.anchorNormal(from.key, fromAt!![0], fromAt[1])
            } else {
                val (x, y) = exitPoint(fromBox, fx, fy, tx, ty)
                ax = x; ay = y
                dirA = sideNormal(fromBox, x, y)
            }
        }
        if (to != null && toBox != null) {
            if (aimB != null) {
                bx = aimB.first; by = aimB.second
                dirB = doc.anchorNormal(to.key, toAt!![0], toAt[1])
            } else {
                val (x, y) = exitPoint(toBox, tx, ty, fx, fy)
                bx = x; by = y
                dirB = sideNormal(toBox, x, y)
            }
        }
        val local = doubleArrayOf(inv.mapX(ax, ay), inv.mapY(ax, ay), inv.mapX(bx, by), inv.mapY(bx, by))
        val next = if (line.tag == "line") {
            withEnds(line, local)
        } else {
            val world = connectorRun(ax, ay, bx, by, dirA, dirB, line.connectorStyle, line.attributes[CONNECT_ARROWS], line.style.strokeWidth ?: 1.0, t)
            withEnds(line, local).copy(geometry = linkedMapOf("d" to SvgPath.write(SvgPath.transform(world, inv))))
        }
        if (next != line || line.key == force) doc = doc.update(line.key) { next }
    }
    return doc
}

/** The point at [u], [v] of the node [key]'s own box, on the drawing. */
fun SvgDocument.anchorPoint(key: Int, u: Double, v: Double, measure: TextMeasure = TextMeasure.ESTIMATE): Pair<Double, Double>? {
    val node = find(key) ?: return null
    val b = ownBounds(node, measure) ?: return null
    val t = worldTransform(key)
    val x = b.left + b.width * u
    val y = b.top + b.height * v
    return t.mapX(x, y) to t.mapY(x, y)
}

/** Where [x], [y] (pixels of the drawing) is on the node [key]'s own box, as fractions (outside it: beyond 0..1). */
fun SvgDocument.anchorOf(key: Int, x: Double, y: Double, measure: TextMeasure = TextMeasure.ESTIMATE): Pair<Double, Double>? {
    val node = find(key) ?: return null
    val b = ownBounds(node, measure) ?: return null
    if (b.width <= 0 || b.height <= 0) return null
    val inv = worldTransform(key).inverse()
    return ((inv.mapX(x, y) - b.left) / b.width) to ((inv.mapY(x, y) - b.top) / b.height)
}

/** The way out of the node [key]'s box at [u], [v] (its nearest side's, turned as the node is; none from its middle). */
private fun SvgDocument.anchorNormal(key: Int, u: Double, v: Double): Pair<Double, Double>? {
    val d = listOf(u to (-1.0 to 0.0), (1 - u) to (1.0 to 0.0), v to (0.0 to -1.0), (1 - v) to (0.0 to 1.0)).minByOrNull { it.first } ?: return null
    if (d.first > 0.45) return null
    val t = worldTransform(key)
    val (lx, ly) = d.second
    // Through the transform's turn and size, without its move.
    val wx = t.a * lx + t.c * ly
    val wy = t.b * lx + t.d * ly
    val len = hypot(wx, wy)
    return if (len < 1e-9) null else (wx / len) to (wy / len)
}

/** The way out of [box] at [x], [y] on its border. */
private fun sideNormal(box: Box, x: Double, y: Double): Pair<Double, Double> {
    val d = listOf(
        abs(x - box.left) to (-1.0 to 0.0), abs(x - box.right) to (1.0 to 0.0),
        abs(y - box.top) to (0.0 to -1.0), abs(y - box.bottom) to (0.0 to 1.0),
    )
    return d.minByOrNull { it.first }!!.second
}

/**
 * A connector's run on the drawing from [ax], [ay] to [bx], [by], leaving and
 * arriving along [dirA] and [dirB] when it holds shapes there: straight, in
 * square turns, or a curve; with chevrons at the ends [arrows] asks, [width]
 * the outline's width in the connector's own units (scaled by [t]).
 */
private fun connectorRun(
    ax: Double, ay: Double, bx: Double, by: Double,
    dirA: Pair<Double, Double>?, dirB: Pair<Double, Double>?,
    style: ConnectorStyle, arrows: String?, width: Double, t: Affine,
): List<Seg> {
    val out = ArrayList<Seg>()
    val dx = bx - ax
    val dy = by - ay
    val dist = hypot(dx, dy)
    val dA = dirA ?: if (dist < 1e-9) (1.0 to 0.0) else (dx / dist to dy / dist)
    val dB = dirB ?: if (dist < 1e-9) (-1.0 to 0.0) else (-dx / dist to -dy / dist)
    out += Seg.Move(ax, ay)
    // The way it arrives, for its arrow.
    var inX: Double
    var inY: Double
    when (style) {
        ConnectorStyle.STRAIGHT -> {
            out += Seg.Line(bx, by)
            inX = dx; inY = dy
        }
        ConnectorStyle.CURVE -> {
            val pull = (dist * 0.45).coerceAtLeast(1e-6)
            val c2x = bx + dB.first * pull
            val c2y = by + dB.second * pull
            out += Seg.Cubic(ax + dA.first * pull, ay + dA.second * pull, c2x, c2y, bx, by)
            inX = bx - c2x; inY = by - c2y
        }
        ConnectorStyle.ELBOW -> {
            val hA = abs(dA.first) >= abs(dA.second)
            val hB = abs(dB.first) >= abs(dB.second)
            when {
                hA && hB -> {
                    val mx = (ax + bx) / 2
                    out += Seg.Line(mx, ay); out += Seg.Line(mx, by); out += Seg.Line(bx, by)
                    inX = bx - mx; inY = 0.0
                }
                !hA && !hB -> {
                    val my = (ay + by) / 2
                    out += Seg.Line(ax, my); out += Seg.Line(bx, my); out += Seg.Line(bx, by)
                    inX = 0.0; inY = by - my
                }
                hA -> {
                    out += Seg.Line(bx, ay); out += Seg.Line(bx, by)
                    inX = 0.0; inY = by - ay
                }
                else -> {
                    out += Seg.Line(ax, by); out += Seg.Line(bx, by)
                    inX = bx - ax; inY = 0.0
                }
            }
            if (abs(inX) < 1e-9 && abs(inY) < 1e-9) { inX = dx; inY = dy }
        }
    }
    // The way it leaves, for an arrow at its start.
    val outX = when (style) {
        ConnectorStyle.STRAIGHT -> dx
        else -> ((out.getOrNull(1) as? Seg.Line)?.x ?: (out.getOrNull(1) as? Seg.Cubic)?.c1x ?: bx) - ax
    }
    val outY = when (style) {
        ConnectorStyle.STRAIGHT -> dy
        else -> ((out.getOrNull(1) as? Seg.Line)?.y ?: (out.getOrNull(1) as? Seg.Cubic)?.c1y ?: by) - ay
    }
    // The chevrons' size on the drawing: by the outline's width as drawn.
    val size = (width * t.maxScale() * 4.5).coerceAtLeast(6.0)
    fun chevron(tipX: Double, tipY: Double, wx: Double, wy: Double) {
        val len = hypot(wx, wy)
        if (len < 1e-9) return
        val ux = wx / len
        val uy = wy / len
        val px = -uy
        val py = ux
        out += Seg.Move(tipX - ux * size + px * size * 0.6, tipY - uy * size + py * size * 0.6)
        out += Seg.Line(tipX, tipY)
        out += Seg.Line(tipX - ux * size - px * size * 0.6, tipY - uy * size - py * size * 0.6)
    }
    if (arrows == "end" || arrows == "both") chevron(bx, by, inX, inY)
    if (arrows == "start" || arrows == "both") chevron(ax, ay, -outX, -outY)
    return out
}

/** Where the ray from [cx], [cy] (inside [box]) towards [tx], [ty] leaves the box; its middle when they are the same point. */
private fun exitPoint(box: Box, cx: Double, cy: Double, tx: Double, ty: Double): Pair<Double, Double> {
    val dx = tx - cx
    val dy = ty - cy
    if (abs(dx) < 1e-9 && abs(dy) < 1e-9) return cx to cy
    var t = Double.MAX_VALUE
    if (dx > 0) t = minOf(t, (box.right - cx) / dx)
    if (dx < 0) t = minOf(t, (box.left - cx) / dx)
    if (dy > 0) t = minOf(t, (box.bottom - cy) / dy)
    if (dy < 0) t = minOf(t, (box.top - cy) / dy)
    // The other shape inside this one: the line just meets its middle.
    t = t.coerceIn(0.0, 1.0)
    return (cx + dx * t) to (cy + dy * t)
}

/**
 * A finger's stroke through [points] (x, y, x, y...) as a smooth path:
 * simplified within [tolerance] (Ramer-Douglas-Peucker), then curves through
 * what is left (Catmull-Rom made into cubic curves), closed when [closed].
 */
fun smoothStroke(points: DoubleArray, tolerance: Double, closed: Boolean = false): List<Seg> {
    val n = points.size / 2
    if (n == 0) return emptyList()
    if (n == 1) return listOf(Seg.Move(points[0], points[1]), Seg.Line(points[0] + 0.01, points[1]))
    val keep = BooleanArray(n)
    keep[0] = true
    keep[n - 1] = true
    fun rdp(a: Int, b: Int) {
        if (b <= a + 1) return
        var far = -1.0
        var at = -1
        val ax = points[a * 2]; val ay = points[a * 2 + 1]
        val bx = points[b * 2]; val by = points[b * 2 + 1]
        val len = hypot(bx - ax, by - ay)
        for (i in a + 1 until b) {
            val px = points[i * 2]; val py = points[i * 2 + 1]
            val d = if (len < 1e-9) hypot(px - ax, py - ay) else abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / len
            if (d > far) {
                far = d
                at = i
            }
        }
        if (far > tolerance) {
            keep[at] = true
            rdp(a, at)
            rdp(at, b)
        }
    }
    rdp(0, n - 1)
    val xs = ArrayList<Double>()
    val ys = ArrayList<Double>()
    for (i in 0 until n) if (keep[i]) {
        xs += points[i * 2]
        ys += points[i * 2 + 1]
    }
    if (closed && xs.size > 2 && hypot(xs.first() - xs.last(), ys.first() - ys.last()) < tolerance * 2) {
        xs.removeAt(xs.lastIndex)
        ys.removeAt(ys.lastIndex)
    }
    val m = xs.size
    val out = ArrayList<Seg>()
    out += Seg.Move(xs[0], ys[0])
    if (m == 2) {
        out += Seg.Line(xs[1], ys[1])
        return out
    }
    fun px(i: Int) = if (closed) xs[(i % m + m) % m] else xs[i.coerceIn(0, m - 1)]
    fun py(i: Int) = if (closed) ys[(i % m + m) % m] else ys[i.coerceIn(0, m - 1)]
    val last = if (closed) m else m - 1
    for (i in 0 until last) {
        // Catmull-Rom through i-1, i, i+1, i+2 as one cubic from i to i+1.
        val c1x = px(i) + (px(i + 1) - px(i - 1)) / 6
        val c1y = py(i) + (py(i + 1) - py(i - 1)) / 6
        val c2x = px(i + 1) - (px(i + 2) - px(i)) / 6
        val c2y = py(i + 1) - (py(i + 2) - py(i)) / 6
        out += Seg.Cubic(c1x, c1y, c2x, c2y, px(i + 1), py(i + 1))
    }
    if (closed) out += Seg.Close
    return out
}

/** [segments] as path data (absolute, two decimals). */
fun pathData(segments: List<Seg>): String = SvgPath.write(segments)

/** [v] as the editor writes numbers (two decimals at most). */
fun svgNumber(v: Double): String = n(v)
