package com.cuscus.ffmpeggui.core.vector.svg

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * The geometry of an SVG: 2D transforms (as SVG writes them), path data (the
 * whole grammar, relative commands, shorthand curves and arcs, the last made
 * into cubic curves) and the outlines of the basic shapes, all as absolute
 * segments the editor draws, measures and writes back.
 */

/**
 * A 2D affine transform, SVG's matrix(a b c d e f):
 * x' = a x + c y + e, y' = b x + d y + f.
 */
data class Affine(val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0, val d: Double = 1.0, val e: Double = 0.0, val f: Double = 0.0) {

    val isIdentity: Boolean get() = this == IDENTITY

    /** This one after [other] (other applied first). */
    operator fun times(other: Affine): Affine = Affine(
        a * other.a + c * other.b,
        b * other.a + d * other.b,
        a * other.c + c * other.d,
        b * other.c + d * other.d,
        a * other.e + c * other.f + e,
        b * other.e + d * other.f + f,
    )

    fun mapX(x: Double, y: Double): Double = a * x + c * y + e
    fun mapY(x: Double, y: Double): Double = b * x + d * y + f

    /** The inverse; the identity when it has none. */
    fun inverse(): Affine {
        val det = a * d - b * c
        if (abs(det) < 1e-12) return IDENTITY
        return Affine(d / det, -b / det, -c / det, a / det, (c * f - d * e) / det, (b * e - a * f) / det)
    }

    /** As an SVG transform attribute (a translation alone written as one). */
    fun toSvg(): String = when {
        isIdentity -> ""
        a == 1.0 && b == 0.0 && c == 0.0 && d == 1.0 -> "translate(${n4(e)} ${n4(f)})"
        else -> "matrix(${n4(a)} ${n4(b)} ${n4(c)} ${n4(d)} ${n4(e)} ${n4(f)})"
    }

    companion object {
        val IDENTITY = Affine()

        fun translate(x: Double, y: Double) = Affine(e = x, f = y)
        fun scale(x: Double, y: Double = x) = Affine(a = x, d = y)

        /** A turn by [degrees] (clockwise on screen) about [cx], [cy]. */
        fun rotate(degrees: Double, cx: Double = 0.0, cy: Double = 0.0): Affine {
            val r = degrees * PI / 180
            val turn = Affine(cos(r), sin(r), -sin(r), cos(r))
            return if (cx == 0.0 && cy == 0.0) turn else translate(cx, cy) * turn * translate(-cx, -cy)
        }

        /** A transform attribute ("translate(10) rotate(45 5 5) scale(2)"); the identity where it cannot be read. */
        fun parse(text: String?): Affine {
            if (text.isNullOrBlank()) return IDENTITY
            var result = IDENTITY
            val call = Regex("""([a-zA-Z]+)\s*\(([^)]*)\)""")
            for (m in call.findAll(text)) {
                val v = numbers(m.groupValues[2])
                val t = when (m.groupValues[1]) {
                    "matrix" -> if (v.size >= 6) Affine(v[0], v[1], v[2], v[3], v[4], v[5]) else IDENTITY
                    "translate" -> translate(v.getOrElse(0) { 0.0 }, v.getOrElse(1) { 0.0 })
                    "scale" -> scale(v.getOrElse(0) { 1.0 }, v.getOrElse(1) { v.getOrElse(0) { 1.0 } })
                    "rotate" -> rotate(v.getOrElse(0) { 0.0 }, v.getOrElse(1) { 0.0 }, v.getOrElse(2) { 0.0 })
                    "skewX" -> Affine(c = tan(v.getOrElse(0) { 0.0 } * PI / 180))
                    "skewY" -> Affine(b = tan(v.getOrElse(0) { 0.0 } * PI / 180))
                    else -> IDENTITY
                }
                result *= t
            }
            return result
        }
    }
}

/** One absolute step of an outline. */
sealed interface Seg {
    data class Move(val x: Double, val y: Double) : Seg
    data class Line(val x: Double, val y: Double) : Seg
    data class Quad(val cx: Double, val cy: Double, val x: Double, val y: Double) : Seg
    data class Cubic(val c1x: Double, val c1y: Double, val c2x: Double, val c2y: Double, val x: Double, val y: Double) : Seg
    data object Close : Seg
}

/** A box: [left], [top], [right], [bottom]. */
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2
    val centerY: Double get() = (top + bottom) / 2
    val isEmpty: Boolean get() = !(right > left || bottom > top)

    fun union(other: Box?): Box = if (other == null) this else Box(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))
}

object SvgPath {

    /** The segments of path data [d]; reading stops at the first mistake, as SVG asks. */
    fun parse(d: String?): List<Seg> {
        if (d.isNullOrBlank()) return emptyList()
        val out = ArrayList<Seg>()
        val tokens = Tokens(d)
        var cmd = ' '
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        // The last control point, for the shorthand curves.
        var lastCtrlX = 0.0
        var lastCtrlY = 0.0
        var lastWasCubic = false
        var lastWasQuad = false
        try {
            while (true) {
                tokens.skip()
                if (tokens.done) break
                val c = tokens.peek()
                if (c.isLetter()) {
                    cmd = c
                    tokens.next()
                } else if (cmd == ' ') {
                    break
                }
                val rel = cmd.isLowerCase()
                val bx = if (rel) x else 0.0
                val by = if (rel) y else 0.0
                var cubic = false
                var quad = false
                when (cmd.uppercaseChar()) {
                    'M' -> {
                        x = bx + tokens.number(); y = by + tokens.number()
                        out += Seg.Move(x, y)
                        startX = x; startY = y
                        // Pairs after a move are lines.
                        cmd = if (rel) 'l' else 'L'
                    }
                    'L' -> { x = bx + tokens.number(); y = by + tokens.number(); out += Seg.Line(x, y) }
                    'H' -> { x = bx + tokens.number(); out += Seg.Line(x, y) }
                    'V' -> { y = by + tokens.number(); out += Seg.Line(x, y) }
                    'C' -> {
                        val c1x = bx + tokens.number(); val c1y = by + tokens.number()
                        val c2x = bx + tokens.number(); val c2y = by + tokens.number()
                        x = bx + tokens.number(); y = by + tokens.number()
                        out += Seg.Cubic(c1x, c1y, c2x, c2y, x, y)
                        lastCtrlX = c2x; lastCtrlY = c2y; cubic = true
                    }
                    'S' -> {
                        val c1x = if (lastWasCubic) 2 * x - lastCtrlX else x
                        val c1y = if (lastWasCubic) 2 * y - lastCtrlY else y
                        val c2x = bx + tokens.number(); val c2y = by + tokens.number()
                        x = bx + tokens.number(); y = by + tokens.number()
                        out += Seg.Cubic(c1x, c1y, c2x, c2y, x, y)
                        lastCtrlX = c2x; lastCtrlY = c2y; cubic = true
                    }
                    'Q' -> {
                        val cx = bx + tokens.number(); val cy = by + tokens.number()
                        x = bx + tokens.number(); y = by + tokens.number()
                        out += Seg.Quad(cx, cy, x, y)
                        lastCtrlX = cx; lastCtrlY = cy; quad = true
                    }
                    'T' -> {
                        val cx = if (lastWasQuad) 2 * x - lastCtrlX else x
                        val cy = if (lastWasQuad) 2 * y - lastCtrlY else y
                        x = bx + tokens.number(); y = by + tokens.number()
                        out += Seg.Quad(cx, cy, x, y)
                        lastCtrlX = cx; lastCtrlY = cy; quad = true
                    }
                    'A' -> {
                        val rx = tokens.number(); val ry = tokens.number(); val rot = tokens.number()
                        val large = tokens.flag(); val sweep = tokens.flag()
                        val ex = bx + tokens.number(); val ey = by + tokens.number()
                        out += arc(x, y, rx, ry, rot, large, sweep, ex, ey)
                        x = ex; y = ey
                    }
                    'Z' -> {
                        out += Seg.Close
                        x = startX; y = startY
                    }
                    else -> break
                }
                lastWasCubic = cubic
                lastWasQuad = quad
            }
        } catch (e: NumberFormatException) {
            // What was read so far stands.
        }
        return out
    }

    /** [segments] as path data, absolute, two decimals at most. */
    fun write(segments: List<Seg>): String = buildString {
        for (s in segments) {
            when (s) {
                is Seg.Move -> append('M').append(n(s.x)).append(' ').append(n(s.y))
                is Seg.Line -> append('L').append(n(s.x)).append(' ').append(n(s.y))
                is Seg.Quad -> append('Q').append(n(s.cx)).append(' ').append(n(s.cy)).append(' ').append(n(s.x)).append(' ').append(n(s.y))
                is Seg.Cubic -> append('C').append(n(s.c1x)).append(' ').append(n(s.c1y)).append(' ').append(n(s.c2x)).append(' ')
                    .append(n(s.c2y)).append(' ').append(n(s.x)).append(' ').append(n(s.y))
                Seg.Close -> append('Z')
            }
        }
    }

    /** [segments] through [t]. */
    fun transform(segments: List<Seg>, t: Affine): List<Seg> = segments.map { s ->
        when (s) {
            is Seg.Move -> Seg.Move(t.mapX(s.x, s.y), t.mapY(s.x, s.y))
            is Seg.Line -> Seg.Line(t.mapX(s.x, s.y), t.mapY(s.x, s.y))
            is Seg.Quad -> Seg.Quad(t.mapX(s.cx, s.cy), t.mapY(s.cx, s.cy), t.mapX(s.x, s.y), t.mapY(s.x, s.y))
            is Seg.Cubic -> Seg.Cubic(
                t.mapX(s.c1x, s.c1y), t.mapY(s.c1x, s.c1y),
                t.mapX(s.c2x, s.c2y), t.mapY(s.c2x, s.c2y),
                t.mapX(s.x, s.y), t.mapY(s.x, s.y),
            )
            Seg.Close -> Seg.Close
        }
    }

    /** The box of [segments] (curves by their points along them); null when they have none. */
    fun bounds(segments: List<Seg>): Box? {
        var l = Double.MAX_VALUE; var t = Double.MAX_VALUE; var r = -Double.MAX_VALUE; var b = -Double.MAX_VALUE
        var any = false
        var px = 0.0; var py = 0.0
        fun add(x: Double, y: Double) {
            any = true
            l = min(l, x); t = min(t, y); r = max(r, x); b = max(b, y)
        }
        for (s in segments) {
            when (s) {
                is Seg.Move -> { add(s.x, s.y); px = s.x; py = s.y }
                is Seg.Line -> { add(s.x, s.y); px = s.x; py = s.y }
                is Seg.Quad -> {
                    for (k in 1..CURVE_SAMPLES) {
                        val u = k.toDouble() / CURVE_SAMPLES; val v = 1 - u
                        add(v * v * px + 2 * v * u * s.cx + u * u * s.x, v * v * py + 2 * v * u * s.cy + u * u * s.y)
                    }
                    px = s.x; py = s.y
                }
                is Seg.Cubic -> {
                    for (k in 1..CURVE_SAMPLES) {
                        val u = k.toDouble() / CURVE_SAMPLES; val v = 1 - u
                        add(
                            v * v * v * px + 3 * v * v * u * s.c1x + 3 * v * u * u * s.c2x + u * u * u * s.x,
                            v * v * v * py + 3 * v * v * u * s.c1y + 3 * v * u * u * s.c2y + u * u * u * s.y,
                        )
                    }
                    px = s.x; py = s.y
                }
                Seg.Close -> Unit
            }
        }
        return if (any) Box(l, t, r, b) else null
    }

    /**
     * An elliptical arc from [x0], [y0] to [x], [y] as cubic curves (the
     * endpoint parameterisation of SVG's implementation notes, F.6.5), each
     * at most a quarter turn.
     */
    fun arc(x0: Double, y0: Double, rxIn: Double, ryIn: Double, degrees: Double, large: Boolean, sweep: Boolean, x: Double, y: Double): List<Seg> {
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if (rx == 0.0 || ry == 0.0 || (x0 == x && y0 == y)) return listOf(Seg.Line(x, y))
        val phi = degrees * PI / 180
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (x0 - x) / 2
        val dy = (y0 - y) / 2
        val x1 = cosPhi * dx + sinPhi * dy
        val y1 = -sinPhi * dx + cosPhi * dy
        // Radii too small for the ends: scaled up just enough.
        val lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if (lambda > 1) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        val num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        val den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var k = sqrt(max(0.0, num / den))
        if (large == sweep) k = -k
        val cx1 = k * rx * y1 / ry
        val cy1 = -k * ry * x1 / rx
        val cx = cosPhi * cx1 - sinPhi * cy1 + (x0 + x) / 2
        val cy = sinPhi * cx1 + cosPhi * cy1 + (y0 + y) / 2
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = atan2(uy, ux)
            val b = atan2(vy, vx)
            return b - a
        }
        val theta1 = atan2((y1 - cy1) / ry, (x1 - cx1) / rx)
        var delta = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
        while (delta > PI) delta -= 2 * PI
        while (delta < -PI) delta += 2 * PI
        if (!sweep && delta > 0) delta -= 2 * PI
        if (sweep && delta < 0) delta += 2 * PI
        val pieces = max(1, ceil(abs(delta) / (PI / 2) - 1e-9).toInt())
        val step = delta / pieces
        val out = ArrayList<Seg>(pieces)
        var t = theta1
        val alpha = 4.0 / 3.0 * tan(step / 4)
        for (p in 0 until pieces) {
            val c1 = cos(t); val s1 = sin(t)
            val c2 = cos(t + step); val s2 = sin(t + step)
            fun px(ex: Double, ey: Double) = cosPhi * rx * ex - sinPhi * ry * ey + cx
            fun py(ex: Double, ey: Double) = sinPhi * rx * ex + cosPhi * ry * ey + cy
            val e1x = c1 - alpha * s1; val e1y = s1 + alpha * c1
            val e2x = c2 + alpha * s2; val e2y = s2 - alpha * c2
            val endX = if (p == pieces - 1) x else px(c2, s2)
            val endY = if (p == pieces - 1) y else py(c2, s2)
            out += Seg.Cubic(px(e1x, e1y), py(e1x, e1y), px(e2x, e2y), py(e2x, e2y), endX, endY)
            t += step
        }
        return out
    }

    /** Points sampled along each curve to measure it. */
    private const val CURVE_SAMPLES = 12

    /** Numbers as path data writes them: "1.5-2e3.5" is 1.5, -2000, 0.5. */
    private class Tokens(private val s: String) {
        var i = 0
        val done: Boolean get() = i >= s.length

        fun skip() {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
        }

        fun peek(): Char = s[i]

        fun next(): Char = s[i++]

        fun number(): Double {
            skip()
            val start = i
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            var digits = false
            while (i < s.length && s[i].isDigit()) { i++; digits = true }
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) { i++; digits = true }
            }
            if (!digits) throw NumberFormatException("No number at $start")
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                val mark = i
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i < s.length && s[i].isDigit()) {
                    while (i < s.length && s[i].isDigit()) i++
                } else {
                    i = mark
                }
            }
            return s.substring(start, i).toDouble()
        }

        /** An arc's flag: a single 0 or 1, which may touch what follows. */
        fun flag(): Boolean {
            skip()
            val c = s.getOrNull(i) ?: throw NumberFormatException("No flag")
            if (c != '0' && c != '1') throw NumberFormatException("Not a flag at $i")
            i++
            return c == '1'
        }
    }
}

/** The numbers in [text] ("1,2 3e1 -4"). */
internal fun numbers(text: String): List<Double> =
    Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""").findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()

/** [v] with at most two decimals ("3", "3.5", "-0.25"). */
internal fun n(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "0"
    val r = Math.round(v * 100.0)
    val whole = r / 100
    val frac = abs(r % 100)
    val sign = if (r < 0 && whole == 0L) "-" else ""
    return when {
        frac == 0L -> "$sign$whole"
        frac % 10 == 0L -> "$sign$whole.${frac / 10}"
        else -> "$sign$whole.${if (frac < 10) "0" else ""}$frac"
    }
}

/** [v] with up to four decimals (transforms need more than coordinates). */
internal fun n4(v: Double): String = String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

/** The length of a vector. */
internal fun length(x: Double, y: Double): Double = hypot(x, y)
