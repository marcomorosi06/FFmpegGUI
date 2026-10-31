package com.cuscus.ffmpeggui.core.vector.svg

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Magnetic points, as in a slide editor: a point being dragged, or placed with
 * a tap, lands on what is near it.
 *
 *  - on another point of the drawing first (both ways at once);
 *  - else on the lines it is near, each way on its own: the page's edges and
 *    middle, and with a grid its lines and the middles of its squares (a point
 *    near both a line across and one down sits on their crossing);
 *  - else, with a symmetry, on its axes and on the lines half way between
 *    them (a turn's parts and their middles), or where one of them crosses a
 *    line it already rests on.
 *
 * Everything is in the drawing's pixels.
 */

/**
 * A line to snap to through ([ox], [oy]) along ([dx], [dy]) (unit length):
 * both ways when [whole], else only from its origin on (a turn's spoke).
 */
data class SnapRay(val ox: Double, val oy: Double, val dx: Double, val dy: Double, val whole: Boolean) {

    /** How far along the ray the foot of ([x], [y]) is (negative: behind its origin). */
    fun along(x: Double, y: Double): Double = (x - ox) * dx + (y - oy) * dy

    /** Whether [t] along it is on the ray. */
    fun holds(t: Double): Boolean = whole || t >= -1e-9
}

/** What a point can snap to: other points (x, y pairs), vertical lines at [xs], horizontal ones at [ys], and [rays]. */
class PointTargets(
    val points: DoubleArray,
    val xs: DoubleArray,
    val ys: DoubleArray,
    val rays: List<SnapRay> = emptyList(),
)

/**
 * Where a point lands: ([x], [y]), the guides it rests on (x of the vertical
 * ones, y of the horizontal ones, and [rays]), and whether it sits on another
 * point ([onPoint]).
 */
data class PointSnap(
    val x: Double,
    val y: Double,
    val xs: List<Double>,
    val ys: List<Double>,
    val rays: List<SnapRay>,
    val onPoint: Boolean,
) {
    /** Whether it landed on anything at all. */
    val snapped: Boolean get() = onPoint || xs.isNotEmpty() || ys.isNotEmpty() || rays.isNotEmpty()

    companion object {
        /** Where it was, on nothing. */
        fun free(x: Double, y: Double) = PointSnap(x, y, emptyList(), emptyList(), emptyList(), false)
    }
}

/** Where ([x], [y]) lands among [targets] within [threshold] (see the top of the file for the order). */
fun snapPoint(x: Double, y: Double, targets: PointTargets, threshold: Double): PointSnap {
    if (threshold <= 0.0) return PointSnap.free(x, y)
    // Another point first: it holds both ways at once.
    val pts = targets.points
    var best = -1
    var bestD = threshold
    var i = 0
    while (i + 1 < pts.size) {
        val d = hypot(pts[i] - x, pts[i + 1] - y)
        if (d <= bestD) {
            bestD = d
            best = i
        }
        i += 2
    }
    if (best >= 0) {
        val px = pts[best]
        val py = pts[best + 1]
        return PointSnap(px, py, listOf(px), listOf(py), emptyList(), onPoint = true)
    }

    fun nearest(value: Double, lines: DoubleArray): Double? {
        var found: Double? = null
        var foundD = threshold
        for (l in lines) {
            val d = abs(l - value)
            if (d <= foundD) {
                foundD = d
                found = l
            }
        }
        return found
    }
    val sx = nearest(x, targets.xs)
    val sy = nearest(y, targets.ys)
    if (sx != null && sy != null) return PointSnap(sx, sy, listOf(sx), listOf(sy), emptyList(), onPoint = false)

    // The nearest ray within reach.
    var ray: SnapRay? = null
    var rayD = threshold
    for (r in targets.rays) {
        val t = r.along(x, y)
        if (!r.holds(t)) continue
        val d = hypot(r.ox + r.dx * t - x, r.oy + r.dy * t - y)
        if (d <= rayD) {
            rayD = d
            ray = r
        }
    }
    if (ray == null) {
        return PointSnap(sx ?: x, sy ?: y, listOfNotNull(sx), listOfNotNull(sy), emptyList(), onPoint = false)
    }
    // Resting on a line already: where the ray crosses it, when that is still near.
    if (sx != null || sy != null) {
        val t = when {
            sx != null && abs(ray.dx) > 1e-9 -> (sx - ray.ox) / ray.dx
            sy != null && abs(ray.dy) > 1e-9 -> (sy - ray.oy) / ray.dy
            else -> Double.NaN
        }
        if (!t.isNaN() && ray.holds(t)) {
            val cx = ray.ox + ray.dx * t
            val cy = ray.oy + ray.dy * t
            if (hypot(cx - x, cy - y) <= threshold * CROSSING_REACH) {
                return PointSnap(cx, cy, listOfNotNull(sx), listOfNotNull(sy), listOf(ray), onPoint = false)
            }
        }
        // The crossing is too far: the line it rests on wins.
        return PointSnap(sx ?: x, sy ?: y, listOfNotNull(sx), listOfNotNull(sy), emptyList(), onPoint = false)
    }
    val t = ray.along(x, y)
    return PointSnap(ray.ox + ray.dx * t, ray.oy + ray.dy * t, emptyList(), emptyList(), listOf(ray), onPoint = false)
}

/** How much farther than the threshold a crossing may pull a point that already rests on a line. */
private const val CROSSING_REACH = 1.5

/**
 * The points of the drawing's visible shapes that can be edited point by point,
 * in the drawing's pixels (x, y pairs), at most [limit] of them. Point
 * [exceptPoint] of part [exceptSub] of the shape [exceptKey] is left out: the
 * one being moved.
 */
fun SvgDocument.pathPoints(exceptKey: Int? = null, exceptSub: Int = -1, exceptPoint: Int = -1, limit: Int = 4000): DoubleArray {
    val out = ArrayList<Double>()
    for (n in allNodes()) {
        if (out.size / 2 >= limit) break
        if (n !is SvgShape || n.style.hidden || !n.editable) continue
        val path = runCatching { n.editPath() }.getOrNull() ?: continue
        val w = worldTransform(n.key)
        path.subs.forEachIndexed { s, sub ->
            sub.points.forEachIndexed { p, pt ->
                if (n.key == exceptKey && s == exceptSub && p == exceptPoint) return@forEachIndexed
                if (out.size / 2 >= limit) return@forEachIndexed
                out += w.mapX(pt.x, pt.y)
                out += w.mapY(pt.x, pt.y)
            }
        }
    }
    return out.toDoubleArray()
}

/**
 * The lines of a grid [step] pixels apart across a [width] x [height] drawing,
 * and the middles of its squares, each way: x of the vertical ones, y of the
 * horizontal ones.
 */
fun gridSnapLines(step: Double, width: Double, height: Double): Pair<DoubleArray, DoubleArray> {
    if (step <= 0.0) return DoubleArray(0) to DoubleArray(0)
    fun along(size: Double): DoubleArray {
        val out = ArrayList<Double>()
        var v = 0.0
        while (v <= size + 1e-9) {
            out += v
            val middle = v + step / 2
            if (middle <= size + 1e-9) out += middle
            v += step
        }
        return out.toDoubleArray()
    }
    return along(width) to along(height)
}

/**
 * The symmetry's lines to snap to, in the drawing's pixels ([view]: from the
 * drawing's own units to its pixels): its axes, and for a turn the lines half
 * way between them too. A mirror's axes run both ways; a turn's spokes only
 * from the centre.
 */
fun Symmetry.snapRays(view: Affine): List<SnapRay> {
    val ox = view.mapX(cx, cy)
    val oy = view.mapY(cx, cy)
    val turn = kind == SymmetryKind.RADIAL || kind == SymmetryKind.KALEIDO
    val angles = if (turn) {
        val n = count.coerceIn(2, 24)
        (0 until n * 2).map { 180.0 * it / n }
    } else {
        axes()
    }
    return angles.mapNotNull { degrees ->
        val r = Math.toRadians(degrees)
        // An axis's direction as the editor draws it (0 degrees: straight up), through the view.
        val ux = cx + sin(r)
        val uy = cy - cos(r)
        val dx = view.mapX(ux, uy) - ox
        val dy = view.mapY(ux, uy) - oy
        val len = hypot(dx, dy)
        if (len < 1e-12) null else SnapRay(ox, oy, dx / len, dy / len, whole = !turn)
    }
}

/** The symmetry's centre in the drawing's pixels, as a point to snap to (x, y). */
fun Symmetry.snapCentre(view: Affine): DoubleArray = doubleArrayOf(view.mapX(cx, cy), view.mapY(cx, cy))
