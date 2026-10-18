package com.cuscus.ffmpeggui.core.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One node of a [SpeedCurve]: at [at] (0..1 of the clip's footage, in playing order) the clip plays at [speed]. */
data class SpeedPoint(val at: Float, val speed: Float)

/**
 * A clip's speed changing along it ("speed ramp"): nodes of speed against the
 * position in the clip's footage (0 = where it starts playing, 1 = where it
 * ends; for a clip played backwards, from its end), joined by a smooth curve
 * that never overshoots its nodes (a monotone cubic over the speed's
 * logarithm, so 0.5x and 2x are as far from 1x).
 *
 * The curve is cut into short pieces over which the speed changes
 * exponentially. Over such a piece the time played has a closed form, and so
 * has its inverse: the export (setpts expressions, sound tempo steps) and the
 * live preview use the same pieces, so they agree.
 */
data class SpeedCurve(val points: List<SpeedPoint>) {

    /** The nodes in order, within range, the first at 0 and the last at 1. */
    val nodes: List<SpeedPoint> = sanitized(points)

    /** True when the speed never changes along the clip. */
    val isFlat: Boolean get() = nodes.all { abs(it.speed - nodes[0].speed) < 1e-3f }

    /** Slowest and fastest speed. */
    val slowest: Float get() = nodes.minOf { it.speed }
    val fastest: Float get() = nodes.maxOf { it.speed }

    /** A piece: from [from] to [to] (fractions of the footage) the speed goes from [s0] to [s1] exponentially. */
    internal class Piece(val from: Double, val to: Double, val s0: Double, val s1: Double) {
        val width: Double get() = to - from
        private val q: Double = ln(s1 / s0)
        private val constant: Boolean = abs(q) < 1e-6

        /** Time played over this piece per unit of footage length. */
        val played: Double = if (constant) width / s0 else width / s0 * (1 - exp(-q)) / q

        /** Speed at fraction [x] (0..1) of the piece. */
        fun speedAt(x: Double): Double = if (constant) s0 else s0 * exp(q * x)

        /** Time played from the start of the piece to fraction [x] of it, per unit of footage length. */
        fun playedTo(x: Double): Double = if (constant) width * x / s0 else width / s0 * (1 - exp(-q * x)) / q

        /** Fraction of the piece reached once [p] (per unit of footage length) has played. */
        fun reachedAfter(p: Double): Double {
            if (width <= 0.0) return 1.0
            if (constant) return (p * s0 / width).coerceIn(0.0, 1.0)
            val inner = 1 - p * s0 * q / width
            return if (inner <= 0.0) 1.0 else (-ln(inner) / q).coerceIn(0.0, 1.0)
        }

        /** The two forms of the time played: start + a * (1 - exp(-c * (u - from))) or start + (u - from) / s0. */
        val a: Double get() = width / (s0 * q)
        val c: Double get() = q / width
        val isConstant: Boolean get() = constant
    }

    internal val pieces: List<Piece> = cut(nodes)

    /** Time played per unit of footage length, up to the start of each piece. */
    private val playedStarts: DoubleArray = DoubleArray(pieces.size + 1).also { starts ->
        for (i in pieces.indices) starts[i + 1] = starts[i] + pieces[i].played
    }

    /** Time played per unit of footage length over the whole clip. */
    private val playedUnit: Double = playedStarts.last()

    /** The speed the clip would need to last as long at a constant speed. */
    val averageSpeed: Double get() = if (playedUnit > 0) 1.0 / playedUnit else 1.0

    /** Speed at [fraction] (0..1) of the footage. */
    fun speedAt(fraction: Double): Float {
        val f = fraction.coerceIn(0.0, 1.0)
        val i = pieceAt(f)
        val piece = pieces[i]
        val x = if (piece.width > 0) (f - piece.from) / piece.width else 0.0
        return piece.speedAt(x).toFloat()
    }

    /** How long a clip of [lengthMs] footage plays. */
    fun playedLengthMs(lengthMs: Double): Double = lengthMs * playedUnit

    /** How long [consumedMs] of a clip of [lengthMs] footage take to play (from where it starts playing). */
    fun playedMs(lengthMs: Double, consumedMs: Double): Double {
        if (lengthMs <= 0.0) return 0.0
        val f = (consumedMs / lengthMs).coerceIn(0.0, 1.0)
        val i = pieceAt(f)
        val piece = pieces[i]
        val x = if (piece.width > 0) (f - piece.from) / piece.width else 0.0
        return lengthMs * (playedStarts[i] + piece.playedTo(x))
    }

    /** Inverse of [playedMs]: how much footage has played after [playedMs]. */
    fun consumedMs(lengthMs: Double, playedMs: Double): Double {
        if (lengthMs <= 0.0) return 0.0
        val p = (playedMs / lengthMs).coerceIn(0.0, playedUnit)
        var i = playedStarts.indexOfLast { it <= p }.coerceIn(0, pieces.lastIndex)
        while (i < pieces.lastIndex && pieces[i].played <= 0.0) i++
        val piece = pieces[i]
        return lengthMs * (piece.from + piece.width * piece.reachedAfter(p - playedStarts[i]))
    }

    /**
     * The time played, in seconds, as an FFmpeg expression of [time] (the footage
     * played so far, in seconds) for a clip of [lengthSeconds] footage.
     * [slot] is the expression variable it keeps [time] in.
     */
    fun playedExpression(lengthSeconds: Double, time: String, slot: Int = SLOT): String {
        val l = lengthSeconds.coerceAtLeast(MIN_SECONDS)
        val u = "ld($slot)"
        var expression = ""
        for (i in pieces.lastIndex downTo 0) {
            val piece = pieces[i]
            val start = playedStarts[i] * l
            val from = piece.from * l
            val body = if (piece.isConstant) {
                "${num(start)}+($u-${num(from)})/${num(piece.s0)}"
            } else {
                "${num(start)}+${num(piece.a * l)}*(1-exp(${num(-piece.c / l)}*($u-${num(from)})))"
            }
            expression = if (expression.isEmpty()) body else "if(lt($u,${num(piece.to * l)}),$body,$expression)"
        }
        return "if(gte(st($slot,clip($time,0,${num(l)})),-1),$expression,0)"
    }

    /**
     * The footage played, in seconds, as an FFmpeg expression of [time] (the
     * time played so far, in seconds): the inverse of [playedExpression].
     */
    fun consumedExpression(lengthSeconds: Double, time: String, slot: Int = SLOT): String {
        val l = lengthSeconds.coerceAtLeast(MIN_SECONDS)
        val p = "ld($slot)"
        var expression = ""
        for (i in pieces.lastIndex downTo 0) {
            val piece = pieces[i]
            val start = playedStarts[i] * l
            val from = piece.from * l
            val body = if (piece.isConstant) {
                "${num(from)}+($p-${num(start)})*${num(piece.s0)}"
            } else {
                // from + w * (-ln(1 - (p - start) * s0 * q / w) / q), with q / w = c and w in seconds.
                "${num(from)}-log(max(1e-9,1-($p-${num(start)})*${num(piece.s0 * piece.c / l)}))/${num(piece.c / l)}"
            }
            val end = playedStarts[i + 1] * l
            expression = if (expression.isEmpty()) body else "if(lt($p,${num(end)}),$body,$expression)"
        }
        return "if(gte(st($slot,clip($time,0,${num(playedUnit * l)})),-1),$expression,0)"
    }

    /**
     * The clip cut into steps of about [stepSeconds] of footage (more when it
     * would make more than [maxSteps]): where each starts in the footage, in
     * seconds, and the constant speed that plays it in exactly its time.
     */
    fun steps(lengthSeconds: Double, stepSeconds: Double = STEP_SECONDS, maxSteps: Int = MAX_STEPS): List<Step> {
        val l = lengthSeconds.coerceAtLeast(MIN_SECONDS)
        val count = max(1, min(maxSteps, (l / stepSeconds).roundToInt()))
        return (0 until count).map { k ->
            val a = l * k / count
            val b = l * (k + 1) / count
            val played = playedMs(l, b) - playedMs(l, a)
            Step(a, b, if (played > 0) (b - a) / played else 1.0)
        }
    }

    /** A stretch of footage, [from] to [to] seconds, played at [speed]. */
    data class Step(val from: Double, val to: Double, val speed: Double)

    /** The speed of every node multiplied by [factor] (within range). */
    fun scaled(factor: Float): SpeedCurve = SpeedCurve(nodes.map { it.copy(speed = it.speed * factor) })

    /**
     * The part of the curve from [from] to [to] (fractions in playing order),
     * stretched over a whole clip: what a part of a clip keeps when it is cut
     * out of it (its nodes there, and the speed at both ends).
     */
    fun window(from: Double, to: Double): SpeedCurve {
        val a = from.coerceIn(0.0, 1.0)
        val b = to.coerceIn(0.0, 1.0)
        if (b - a < 1e-6) return flat(speedAt(a))
        if (a <= 1e-9 && b >= 1.0 - 1e-9) return this
        val inside = nodes.filter { it.at > a + 1e-6 && it.at < b - 1e-6 }
            .map { SpeedPoint(((it.at - a) / (b - a)).toFloat(), it.speed) }
        return SpeedCurve(listOf(SpeedPoint(0f, speedAt(a))) + inside + SpeedPoint(1f, speedAt(b)))
    }

    /** The curve read from its end: what a clip played the other way needs to look the same. */
    fun mirrored(): SpeedCurve = SpeedCurve(nodes.map { it.copy(at = 1f - it.at) }.reversed())

    /** With a node at [at] (the speed the curve has there when [speed] is null). */
    fun withNode(at: Float, speed: Float? = null): SpeedCurve {
        val a = at.coerceIn(0f, 1f)
        if (nodes.any { abs(it.at - a) < MIN_GAP }) return this
        return SpeedCurve(nodes + SpeedPoint(a, speed ?: speedAt(a.toDouble())))
    }

    /** Node [index] moved to [at], [speed]; the ends stay at 0 and 1 and no node passes another. */
    fun movedNode(index: Int, at: Float, speed: Float): SpeedCurve {
        if (index !in nodes.indices) return this
        val position = when (index) {
            0 -> 0f
            nodes.lastIndex -> 1f
            else -> at.coerceIn(nodes[index - 1].at + MIN_GAP, nodes[index + 1].at - MIN_GAP)
        }
        return SpeedCurve(nodes.mapIndexed { i, node -> if (i == index) SpeedPoint(position, speed) else node })
    }

    /** Without node [index]; the ends cannot be taken away. */
    fun withoutNode(index: Int): SpeedCurve =
        if (index <= 0 || index >= nodes.lastIndex) this else SpeedCurve(nodes.filterIndexed { i, _ -> i != index })

    private fun pieceAt(fraction: Double): Int {
        for (i in pieces.indices) if (fraction < pieces[i].to) return i
        return pieces.lastIndex
    }

    companion object {
        const val MIN_SPEED = 0.1f
        const val MAX_SPEED = 10f

        /** Closest two nodes can be, as a fraction of the clip. */
        const val MIN_GAP = 0.02f

        /** Pieces each stretch between two nodes is cut into. */
        private const val SUBDIVISIONS = 8

        /** Expression variable the curve's expressions keep their time in (0 is the keyframe easings'). */
        const val SLOT = 1

        /** Footage per step of the sound's tempo changes. */
        const val STEP_SECONDS = 0.1
        const val MAX_STEPS = 240

        private const val MIN_SECONDS = 0.001

        /** A curve at a constant [speed]. */
        fun flat(speed: Float): SpeedCurve = SpeedCurve(listOf(SpeedPoint(0f, speed), SpeedPoint(1f, speed)))

        private fun num(v: Double): String = String.format(Locale.US, "%.6f", v)

        private fun sanitized(points: List<SpeedPoint>): List<SpeedPoint> {
            val clean = points
                .filter { it.at.isFinite() && it.speed.isFinite() }
                .map { SpeedPoint(it.at.coerceIn(0f, 1f), it.speed.coerceIn(MIN_SPEED, MAX_SPEED)) }
                .sortedBy { it.at }
            if (clean.isEmpty()) return listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 1f))
            val kept = mutableListOf<SpeedPoint>()
            for (point in clean) {
                if (kept.isNotEmpty() && point.at - kept.last().at < MIN_GAP / 2) kept[kept.lastIndex] = point else kept += point
            }
            if (kept.first().at > 0f) kept.add(0, SpeedPoint(0f, kept.first().speed))
            if (kept.last().at < 1f) kept += SpeedPoint(1f, kept.last().speed)
            if (kept.size == 1) kept += SpeedPoint(1f, kept[0].speed)
            return kept
        }

        /**
         * The smooth curve through [nodes] (Fritsch-Carlson monotone cubic over
         * log2 of the speed), cut into exponential pieces.
         */
        private fun cut(nodes: List<SpeedPoint>): List<Piece> {
            val xs = nodes.map { it.at.toDouble() }
            val ys = nodes.map { ln(it.speed.toDouble()) }
            val slopes = MonotoneCubic.tangents(xs, ys)
            val pieces = mutableListOf<Piece>()
            for (i in 0 until xs.lastIndex) {
                val x0 = xs[i]
                val x1 = xs[i + 1]
                if (x1 - x0 <= 0.0) continue
                if (abs(ys[i + 1] - ys[i]) < 1e-9 && abs(slopes[i]) < 1e-9 && abs(slopes[i + 1]) < 1e-9) {
                    pieces += Piece(x0, x1, exp(ys[i]), exp(ys[i]))
                    continue
                }
                var previous = exp(ys[i])
                for (k in 1..SUBDIVISIONS) {
                    val x = x0 + (x1 - x0) * k / SUBDIVISIONS
                    val y = exp(MonotoneCubic.value(x0, x1, ys[i], ys[i + 1], slopes[i], slopes[i + 1], x))
                    pieces += Piece(x0 + (x1 - x0) * (k - 1) / SUBDIVISIONS, x, previous, y)
                    previous = y
                }
            }
            if (pieces.isEmpty()) pieces += Piece(0.0, 1.0, exp(ys[0]), exp(ys[0]))
            return pieces
        }
    }
}

/** Ready-made speed curves, as video editors offer them. */
enum class SpeedPreset(val curve: SpeedCurve) {
    /** Slow in the middle, fast at both ends. */
    MONTAGE(curve(0f to 2.5f, 0.3f to 1.4f, 0.5f to 0.4f, 0.7f to 1.4f, 1f to 2.5f)),

    /** Fast, a slow moment in the middle, fast again. */
    HERO(curve(0f to 1.8f, 0.35f to 1.8f, 0.45f to 0.25f, 0.6f to 0.25f, 0.7f to 1.8f, 1f to 1.8f)),

    /** Normal, then a long slow stretch, then normal. */
    BULLET(curve(0f to 1f, 0.25f to 1f, 0.35f to 0.2f, 0.75f to 0.2f, 0.85f to 1f, 1f to 1f)),

    /** Bursts of speed between normal stretches. */
    JUMP_CUT(curve(0f to 1f, 0.15f to 1f, 0.25f to 4f, 0.4f to 1f, 0.6f to 1f, 0.7f to 4f, 0.85f to 1f, 1f to 1f)),

    /** Starts fast and settles to normal. */
    FLASH_IN(curve(0f to 4f, 0.3f to 2f, 0.6f to 1f, 1f to 1f)),

    /** Normal, then faster and faster. */
    FLASH_OUT(curve(0f to 1f, 0.4f to 1f, 0.7f to 2f, 1f to 4f)),

    /** From slow to fast. */
    RAMP_UP(curve(0f to 0.5f, 1f to 3f)),

    /** From fast to slow. */
    RAMP_DOWN(curve(0f to 3f, 1f to 0.5f)),
}

private fun curve(vararg points: Pair<Float, Float>): SpeedCurve = SpeedCurve(points.map { SpeedPoint(it.first, it.second) })

/**
 * Monotone cubic Hermite interpolation (Fritsch-Carlson): smooth through its
 * nodes, never above or below them between two of them.
 */
object MonotoneCubic {

    /** Tangents at the nodes [xs] (increasing), [ys]. */
    fun tangents(xs: List<Double>, ys: List<Double>): DoubleArray {
        val n = xs.size
        val m = DoubleArray(n)
        if (n < 2) return m
        val d = DoubleArray(n - 1) { i -> val h = xs[i + 1] - xs[i]; if (h > 0) (ys[i + 1] - ys[i]) / h else 0.0 }
        m[0] = d[0]
        m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0.0) 0.0 else (d[i - 1] + d[i]) / 2
        for (i in 0 until n - 1) {
            if (abs(d[i]) < 1e-12) {
                m[i] = 0.0
                m[i + 1] = 0.0
                continue
            }
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9.0) {
                val t = 3.0 / kotlin.math.sqrt(s)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
        return m
    }

    /** The curve between nodes (x0, y0) and (x1, y1), tangents m0 and m1, at [x]. */
    fun value(x0: Double, x1: Double, y0: Double, y1: Double, m0: Double, m1: Double, x: Double): Double {
        val h = x1 - x0
        if (h <= 0.0) return y1
        val t = ((x - x0) / h).coerceIn(0.0, 1.0)
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * h * m0 + (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * h * m1
    }

    /**
     * The polynomial a + b d + c d^2 + e d^3 (d = x - x0) of the same curve,
     * for expressions.
     */
    fun coefficients(x0: Double, x1: Double, y0: Double, y1: Double, m0: Double, m1: Double): DoubleArray {
        val h = (x1 - x0).coerceAtLeast(1e-9)
        val delta = (y1 - y0) / h
        val c = (3 * delta - 2 * m0 - m1) / h
        val e = (m0 + m1 - 2 * delta) / (h * h)
        return doubleArrayOf(y0, m0, c, e)
    }

    /** Value at [x] of the curve through every node. */
    fun at(xs: List<Double>, ys: List<Double>, x: Double, tangents: DoubleArray = tangents(xs, ys)): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        for (i in 0 until xs.lastIndex) {
            if (x < xs[i + 1]) return value(xs[i], xs[i + 1], ys[i], ys[i + 1], tangents[i], tangents[i + 1], x)
        }
        return ys.last()
    }
}
