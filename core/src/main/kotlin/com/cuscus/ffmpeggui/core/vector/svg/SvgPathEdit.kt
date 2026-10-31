package com.cuscus.ffmpeggui.core.vector.svg

import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * A line or an outline as its points and what joins them, for editing point
 * by point: each piece between two points is straight, a curve (its two
 * control points), a zig-zag (how many teeth, how tall) or a wave (how many,
 * how tall). Zig-zags and waves are written into the path as the lines and
 * curves they make; how they were made is kept beside it (data-ffg-path), so
 * that the drawing opens again as it was edited. A path changed by another
 * program (its d no longer what the editor wrote) is read from its d.
 *
 * Coordinates are the shape's own units.
 */

/** The attribute keeping how an edited path was made. */
const val EDIT_PATH = "data-ffg-path"

/** What joins two points. */
sealed interface Bend {
    data object Straight : Bend
    /** A cubic curve through the control points [c1x], [c1y] and [c2x], [c2y]. */
    data class Curve(val c1x: Double, val c1y: Double, val c2x: Double, val c2y: Double) : Bend
    /** [teeth] teeth [height] tall (each side of the line, the first to its left). */
    data class ZigZag(val teeth: Int, val height: Double) : Bend
    /** [waves] waves [height] tall. */
    data class Wave(val waves: Int, val height: Double) : Bend
}

/** A point of a line. */
data class Pt(val x: Double, val y: Double)

/**
 * One stroke of a path: its [points], and the [bends] between them (bend i
 * joins point i to point i + 1; when [closed], the last joins the last point
 * back to the first).
 */
data class EditSub(val points: List<Pt>, val bends: List<Bend>, val closed: Boolean) {
    /** How many pieces join the points. */
    val pieces: Int get() = if (closed) points.size else points.size - 1

    fun end(piece: Int): Pt = points[(piece + 1) % points.size]
}

/** Where a point is near a path: on a point, or along a piece at [t] (0..1). */
sealed interface PathHit {
    val sub: Int
    val distance: Double
    data class OnPoint(override val sub: Int, val point: Int, override val distance: Double) : PathHit
    data class OnPiece(override val sub: Int, val piece: Int, val t: Double, val x: Double, val y: Double, override val distance: Double) : PathHit
}

/** A path as its strokes, for editing. */
data class EditPath(val subs: List<EditSub>) {

    /** The path as segments, zig-zags and waves made into lines and curves. */
    fun toSegments(): List<Seg> {
        val out = ArrayList<Seg>()
        for (sub in subs) {
            if (sub.points.isEmpty()) continue
            out += Seg.Move(sub.points[0].x, sub.points[0].y)
            for (i in 0 until sub.pieces) out += piece(sub.points[i], sub.end(i), sub.bends[i])
            if (sub.closed) out += Seg.Close
        }
        return out
    }

    /** The segments of piece [i] of stroke [s] alone, from its start. */
    fun pieceSegments(s: Int, i: Int): List<Seg> {
        val sub = subs[s]
        return listOf(Seg.Move(sub.points[i].x, sub.points[i].y)) + piece(sub.points[i], sub.end(i), sub.bends[i])
    }

    /** The path with point [p] of stroke [s] at [x], [y]; the curves' control points beside it move with it. */
    fun movePoint(s: Int, p: Int, x: Double, y: Double): EditPath = changeSub(s) { sub ->
        val old = sub.points[p]
        val dx = x - old.x
        val dy = y - old.y
        val bends = sub.bends.toMutableList()
        // The piece leaving it: its first control.
        if (p < sub.pieces) (bends[p] as? Bend.Curve)?.let { bends[p] = it.copy(c1x = it.c1x + dx, c1y = it.c1y + dy) }
        // The piece arriving at it: its second control.
        val before = if (p > 0) p - 1 else if (sub.closed) sub.pieces - 1 else -1
        if (before >= 0) (bends[before] as? Bend.Curve)?.let { bends[before] = it.copy(c2x = it.c2x + dx, c2y = it.c2y + dy) }
        sub.copy(points = sub.points.toMutableList().apply { set(p, Pt(x, y)) }, bends = bends)
    }

    /** The path with piece [i] of stroke [s] joined by [bend]. */
    fun setBend(s: Int, i: Int, bend: Bend): EditPath = changeSub(s) { sub ->
        sub.copy(bends = sub.bends.toMutableList().apply { set(i, bend) })
    }

    /**
     * The path with a new point on piece [i] of stroke [s] at [t] along it (a
     * curve split where it is, so that it keeps its shape; a zig-zag or a wave
     * shares its teeth between the two halves); the new point's index.
     */
    fun insertPoint(s: Int, i: Int, t: Double): Pair<EditPath, Int> {
        val sub = subs[s]
        val a = sub.points[i]
        val b = sub.end(i)
        val u = t.coerceIn(0.02, 0.98)
        val (mid, left, right) = when (val bend = sub.bends[i]) {
            Bend.Straight -> Triple(Pt(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u), Bend.Straight, Bend.Straight)
            is Bend.Curve -> {
                // De Casteljau at u.
                fun lerp(p: Double, q: Double) = p + (q - p) * u
                val x01 = lerp(a.x, bend.c1x); val y01 = lerp(a.y, bend.c1y)
                val x12 = lerp(bend.c1x, bend.c2x); val y12 = lerp(bend.c1y, bend.c2y)
                val x23 = lerp(bend.c2x, b.x); val y23 = lerp(bend.c2y, b.y)
                val xa = lerp(x01, x12); val ya = lerp(y01, y12)
                val xb = lerp(x12, x23); val yb = lerp(y12, y23)
                val xm = lerp(xa, xb); val ym = lerp(ya, yb)
                Triple(Pt(xm, ym), Bend.Curve(x01, y01, xa, ya), Bend.Curve(xb, yb, x23, y23))
            }
            is Bend.ZigZag -> {
                val l = (bend.teeth * u).roundToInt().coerceAtLeast(1)
                Triple(Pt(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u), bend.copy(teeth = l), bend.copy(teeth = (bend.teeth - l).coerceAtLeast(1)))
            }
            is Bend.Wave -> {
                val l = (bend.waves * u).roundToInt().coerceAtLeast(1)
                Triple(Pt(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u), bend.copy(waves = l), bend.copy(waves = (bend.waves - l).coerceAtLeast(1)))
            }
        }
        val points = sub.points.toMutableList().apply { add(i + 1, mid) }
        val bends = sub.bends.toMutableList().apply {
            set(i, left)
            add(i + 1, right)
        }
        return changeSub(s) { sub.copy(points = points, bends = bends) } to (i + 1)
    }

    /**
     * The path without point [p] of stroke [s] (the pieces on either side made
     * one, as the first of them was); null when the stroke would be left with
     * a single point.
     */
    fun removePoint(s: Int, p: Int): EditPath? {
        val sub = subs[s]
        if (sub.points.size <= 2) {
            return if (subs.size > 1) copy(subs = subs.filterIndexed { i, _ -> i != s }) else null
        }
        val points = sub.points.toMutableList().apply { removeAt(p) }
        val bends = sub.bends.toMutableList()
        when {
            // An end of an open stroke: the piece beside it goes.
            !sub.closed && p == 0 -> bends.removeAt(0)
            !sub.closed && p == sub.points.lastIndex -> bends.removeAt(bends.lastIndex)
            else -> {
                // The piece before it reaches the point after it; a curve keeps its outer controls.
                val before = if (p > 0) p - 1 else sub.pieces - 1
                val joined = when (val first = bends[before]) {
                    is Bend.Curve -> (bends[p] as? Bend.Curve)?.let { second -> first.copy(c2x = second.c2x, c2y = second.c2y) } ?: first
                    else -> first
                }
                bends[before] = joined
                bends.removeAt(p)
            }
        }
        return changeSub(s) { sub.copy(points = points, bends = bends) }
    }

    /** The path with stroke [s] closed (joined back by a straight piece) or opened (its last piece gone). */
    fun setClosed(s: Int, closed: Boolean): EditPath = changeSub(s) { sub ->
        when {
            sub.closed == closed || sub.points.size < 2 -> sub
            closed -> sub.copy(bends = sub.bends + Bend.Straight, closed = true)
            else -> sub.copy(bends = sub.bends.dropLast(1), closed = false)
        }
    }

    /** The path with a point at [x], [y] added after the last of the last stroke (a new stroke when there is none). */
    fun append(x: Double, y: Double): EditPath {
        if (subs.isEmpty()) return EditPath(listOf(EditSub(listOf(Pt(x, y)), emptyList(), false)))
        return changeSub(subs.lastIndex) { sub ->
            if (sub.closed) sub else sub.copy(points = sub.points + Pt(x, y), bends = sub.bends + Bend.Straight)
        }
    }

    /** What is nearest to [x], [y] within [reach]: a point first, else a piece. */
    fun hit(x: Double, y: Double, reach: Double): PathHit? {
        var best: PathHit? = null
        subs.forEachIndexed { s, sub ->
            sub.points.forEachIndexed { i, p ->
                val d = hypot(p.x - x, p.y - y)
                if (d <= reach && (best !is PathHit.OnPoint || d < best!!.distance)) best = PathHit.OnPoint(s, i, d)
            }
        }
        if (best != null) return best
        subs.forEachIndexed { s, sub ->
            for (i in 0 until sub.pieces) {
                val a = sub.points[i]
                val b = sub.end(i)
                val pts = flatten(pieceSegments(s, i), steps = 24).firstOrNull()?.points ?: continue
                var k = 0
                val total = (pts.size / 2 - 1).coerceAtLeast(1)
                while (k + 3 < pts.size) {
                    val x1 = pts[k]; val y1 = pts[k + 1]; val x2 = pts[k + 2]; val y2 = pts[k + 3]
                    val dx = x2 - x1; val dy = y2 - y1
                    val len2 = dx * dx + dy * dy
                    val u = if (len2 < 1e-18) 0.0 else (((x - x1) * dx + (y - y1) * dy) / len2).coerceIn(0.0, 1.0)
                    val px = x1 + u * dx; val py = y1 + u * dy
                    val d = hypot(x - px, y - py)
                    if (d <= reach && (best == null || d < best!!.distance)) {
                        // Along a curve: by its samples; along a zig-zag or a wave: by its chord.
                        val t = when (sub.bends[i]) {
                            is Bend.Curve -> ((k / 2) + u) / total
                            else -> {
                                val cx = b.x - a.x; val cy = b.y - a.y
                                val c2 = cx * cx + cy * cy
                                if (c2 < 1e-18) 0.5 else (((px - a.x) * cx + (py - a.y) * cy) / c2).coerceIn(0.0, 1.0)
                            }
                        }
                        best = PathHit.OnPiece(s, i, t, px, py, d)
                    }
                    k += 2
                }
            }
        }
        return best
    }

    /** The middle of piece [i] of stroke [s], halfway along it (where its marker goes). */
    fun pieceMiddle(s: Int, i: Int): Pt {
        val pts = flatten(pieceSegments(s, i), steps = 24).firstOrNull()?.points ?: return subs[s].points[i]
        var total = 0.0
        var k = 0
        while (k + 3 < pts.size) {
            total += hypot(pts[k + 2] - pts[k], pts[k + 3] - pts[k + 1])
            k += 2
        }
        var walked = 0.0
        k = 0
        while (k + 3 < pts.size) {
            val step = hypot(pts[k + 2] - pts[k], pts[k + 3] - pts[k + 1])
            if (walked + step >= total / 2 && step > 0) {
                val u = (total / 2 - walked) / step
                return Pt(pts[k] + (pts[k + 2] - pts[k]) * u, pts[k + 1] + (pts[k + 3] - pts[k + 1]) * u)
            }
            walked += step
            k += 2
        }
        return Pt(pts[0], pts[1])
    }

    /** As kept beside the path ([EDIT_PATH]). */
    fun encode(): String = buildString {
        for (sub in subs) {
            if (sub.points.isEmpty()) continue
            if (isNotEmpty()) append(' ')
            append("P ").append(n4(sub.points[0].x)).append(' ').append(n4(sub.points[0].y))
            for (i in 0 until sub.pieces) {
                append(' ')
                if (sub.closed && i == sub.pieces - 1) append("Q ")
                when (val b = sub.bends[i]) {
                    Bend.Straight -> append('L')
                    is Bend.Curve -> append("C ").append(n4(b.c1x)).append(' ').append(n4(b.c1y)).append(' ').append(n4(b.c2x)).append(' ').append(n4(b.c2y))
                    is Bend.ZigZag -> append("Z ").append(b.teeth).append(' ').append(n4(b.height))
                    is Bend.Wave -> append("W ").append(b.waves).append(' ').append(n4(b.height))
                }
                if (!(sub.closed && i == sub.pieces - 1)) {
                    val e = sub.end(i)
                    append(' ').append(n4(e.x)).append(' ').append(n4(e.y))
                }
            }
            if (sub.closed && sub.pieces == 0) append(" Q L")
        }
    }

    private fun changeSub(s: Int, change: (EditSub) -> EditSub): EditPath =
        copy(subs = subs.mapIndexed { i, sub -> if (i == s) change(sub) else sub })

    companion object {

        /** [segments] as points and pieces (a quadratic curve as the cubic it is). */
        fun fromSegments(segments: List<Seg>): EditPath {
            val subs = ArrayList<EditSub>()
            var points = ArrayList<Pt>()
            var bends = ArrayList<Bend>()
            var px = 0.0
            var py = 0.0
            fun finish(closed: Boolean) {
                if (points.isEmpty()) return
                var pts: List<Pt> = points
                var bs: List<Bend> = bends
                if (closed) {
                    // The last point back on the first: the piece that reaches it closes the stroke.
                    if (pts.size >= 2 && hypot(pts.last().x - pts.first().x, pts.last().y - pts.first().y) < 1e-6) {
                        pts = pts.dropLast(1)
                    } else {
                        bs = bs + Bend.Straight
                    }
                    if (pts.size >= 2) subs += EditSub(pts, bs, true)
                } else if (pts.size >= 2) {
                    subs += EditSub(pts, bs, false)
                }
                points = ArrayList()
                bends = ArrayList()
            }
            for (s in segments) {
                when (s) {
                    is Seg.Move -> {
                        finish(false)
                        points += Pt(s.x, s.y)
                    }
                    is Seg.Line -> {
                        if (points.isEmpty()) points += Pt(px, py)
                        points += Pt(s.x, s.y)
                        bends += Bend.Straight
                    }
                    is Seg.Quad -> {
                        if (points.isEmpty()) points += Pt(px, py)
                        bends += Bend.Curve(
                            px + 2.0 / 3 * (s.cx - px), py + 2.0 / 3 * (s.cy - py),
                            s.x + 2.0 / 3 * (s.cx - s.x), s.y + 2.0 / 3 * (s.cy - s.y),
                        )
                        points += Pt(s.x, s.y)
                    }
                    is Seg.Cubic -> {
                        if (points.isEmpty()) points += Pt(px, py)
                        bends += Bend.Curve(s.c1x, s.c1y, s.c2x, s.c2y)
                        points += Pt(s.x, s.y)
                    }
                    Seg.Close -> {
                        val start = points.firstOrNull()
                        finish(true)
                        if (start != null) {
                            px = start.x
                            py = start.y
                        }
                        continue
                    }
                }
                (s as? Seg.Move)?.let { px = it.x; py = it.y }
                (s as? Seg.Line)?.let { px = it.x; py = it.y }
                (s as? Seg.Quad)?.let { px = it.x; py = it.y }
                (s as? Seg.Cubic)?.let { px = it.x; py = it.y }
            }
            finish(false)
            return EditPath(subs)
        }

        /** What [encode] wrote; null when it cannot be read. */
        fun decode(text: String): EditPath? = runCatching {
            val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            var i = 0
            fun num(): Double = tokens[i++].toDouble()
            val subs = ArrayList<EditSub>()
            while (i < tokens.size) {
                require(tokens[i++] == "P")
                val points = arrayListOf(Pt(num(), num()))
                val bends = ArrayList<Bend>()
                var closed = false
                while (i < tokens.size && tokens[i] != "P") {
                    val closing = tokens[i] == "Q"
                    if (closing) i++
                    val bend = when (tokens[i++]) {
                        "L" -> Bend.Straight
                        "C" -> Bend.Curve(num(), num(), num(), num())
                        "Z" -> Bend.ZigZag(tokens[i++].toInt().coerceIn(1, 500), num())
                        "W" -> Bend.Wave(tokens[i++].toInt().coerceIn(1, 500), num())
                        else -> error("unknown piece")
                    }
                    bends += bend
                    if (closing) {
                        closed = true
                    } else {
                        points += Pt(num(), num())
                    }
                }
                subs += EditSub(points, bends, closed)
            }
            require(subs.all { it.bends.size == it.pieces })
            EditPath(subs)
        }.getOrNull()

        /** The segments of one piece from [a] to [b] joined by [bend] (without the move to [a]). */
        fun piece(a: Pt, b: Pt, bend: Bend): List<Seg> = when (bend) {
            Bend.Straight -> listOf(Seg.Line(b.x, b.y))
            is Bend.Curve -> listOf(Seg.Cubic(bend.c1x, bend.c1y, bend.c2x, bend.c2y, b.x, b.y))
            is Bend.ZigZag -> {
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy).coerceAtLeast(1e-9)
                // To the left of the way it goes.
                val nx = dy / len
                val ny = -dx / len
                val out = ArrayList<Seg>()
                val n = bend.teeth.coerceAtLeast(1)
                for (k in 0 until n) {
                    val u = (k + 0.5) / n
                    val side = if (k % 2 == 0) 1 else -1
                    out += Seg.Line(a.x + dx * u + nx * bend.height * side, a.y + dy * u + ny * bend.height * side)
                }
                out += Seg.Line(b.x, b.y)
                out
            }
            is Bend.Wave -> {
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy).coerceAtLeast(1e-9)
                val nx = dy / len
                val ny = -dx / len
                val out = ArrayList<Seg>()
                val n = bend.waves.coerceAtLeast(1)
                // Each half wave one cubic: its controls 0.3642 of the way in and 4/3 as high
                // (a symmetric cubic peaks at three quarters of its controls), close to a sine.
                val halves = n * 2
                val lift = 4.0 / 3.0 * bend.height
                for (h in 0 until halves) {
                    val u0 = h.toDouble() / halves
                    val u1 = (h + 1.0) / halves
                    val side = if (h % 2 == 0) 1 else -1
                    val c1u = u0 + (u1 - u0) * SINE_IN
                    val c2u = u1 - (u1 - u0) * SINE_IN
                    out += Seg.Cubic(
                        a.x + dx * c1u + nx * lift * side, a.y + dy * c1u + ny * lift * side,
                        a.x + dx * c2u + nx * lift * side, a.y + dy * c2u + ny * lift * side,
                        a.x + dx * u1, a.y + dy * u1,
                    )
                }
                out
            }
        }

        /** The control points of a symmetric curve from [a] to [b] bent by [amount] (-1..1 of half its length, to its left). */
        fun bentCurve(a: Pt, b: Pt, amount: Double): Bend.Curve {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx, dy)
            val nx = if (len < 1e-9) 0.0 else dy / len
            val ny = if (len < 1e-9) 0.0 else -dx / len
            val off = amount * len * 0.5
            return Bend.Curve(a.x + dx / 3 + nx * off, a.y + dy / 3 + ny * off, a.x + dx * 2 / 3 + nx * off, a.y + dy * 2 / 3 + ny * off)
        }

        /** How far a curve from [a] to [b] bends, as [bentCurve] measures it (its controls' mean offset to the left). */
        fun bendOf(a: Pt, b: Pt, curve: Bend.Curve): Double {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx, dy)
            if (len < 1e-9) return 0.0
            val nx = dy / len
            val ny = -dx / len
            val o1 = (curve.c1x - a.x) * nx + (curve.c1y - a.y) * ny
            val o2 = (curve.c2x - a.x) * nx + (curve.c2y - a.y) * ny
            return ((o1 + o2) / 2 / (len * 0.5)).coerceIn(-1.0, 1.0)
        }

        /** How far into a half wave its controls lie (of its length), for a sine's shape. */
        private const val SINE_IN = 0.3642
    }
}

/** The tags whose outline the editor can edit point by point. */
val SvgShape.editable: Boolean get() = tag in SvgShape.TAGS

/**
 * This shape as points and pieces: as the editor made it when its path is
 * still what it wrote, otherwise from its outline (a rectangle's four sides,
 * an ellipse's four curves...).
 */
fun SvgShape.editPath(): EditPath {
    val kept = attributes[EDIT_PATH]?.let { EditPath.decode(it) }
    if (kept != null && tag == "path" && SvgPath.write(kept.toSegments()) == geometry["d"]) return kept
    return EditPath.fromSegments(segments())
}

/** This shape made a path drawing [path], how it was made kept beside it. */
fun SvgShape.withEditPath(path: EditPath): SvgShape {
    val plain = path.subs.all { sub -> sub.bends.all { it is Bend.Straight || it is Bend.Curve } }
    val attrs = if (plain) attributes - EDIT_PATH else attributes + (EDIT_PATH to path.encode())
    return copy(tag = "path", geometry = linkedMapOf("d" to SvgPath.write(path.toSegments())), attributes = attrs)
}
