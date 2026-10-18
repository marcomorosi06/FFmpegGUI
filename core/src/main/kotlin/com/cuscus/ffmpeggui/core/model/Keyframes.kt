package com.cuscus.ffmpeggui.core.model

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** What a keyframe can set: of a text or picture laid over the video, of a clip, of a sound. */
enum class KeyProperty {
    /** Place across and down, fractions of the free space (texts and pictures). */
    X, Y,

    /** A text's height or a picture's width, fractions of the frame. */
    SIZE,

    /** A picture's turn, in degrees clockwise. */
    ROTATION,

    /** 0..1 (texts and pictures). */
    OPACITY,

    /** A clip's enlargement (1 = the whole frame) and which part of it shows (0..1 of the free space). */
    ZOOM, PAN_X, PAN_Y,

    /** A clip's colours, as eq takes them. */
    BRIGHTNESS, CONTRAST, SATURATION,

    /** A clip's or a sound's volume, linear (1 = unchanged). */
    VOLUME,
}

/** How a value goes from one keyframe to the next. */
enum class KeyEasing {
    /** At an even pace. */
    LINEAR,

    /** Slowly out of the keyframe and slowly into the next (smoothstep). */
    SMOOTH,

    /** Slowly out of the keyframe, then faster and faster. */
    EASE_IN,

    /** Fast out of the keyframe, slowing down into the next. */
    EASE_OUT,

    /** Slowly out, fast in the middle, slowly in (stronger than [SMOOTH]). */
    EASE_IN_OUT,

    /** Goes a little past the next keyframe's value and comes back. */
    BACK,

    /** Bounces on the next keyframe's value. */
    BOUNCE,

    /** Springs around the next keyframe's value before settling. */
    ELASTIC,

    /** Stays at this keyframe's value, then jumps to the next one's. */
    HOLD,

    /** Follows the keyframe's own curve ([Keyframe.curve]). */
    CUSTOM,
}

/** A node of an [EaseCurve]: at [x] of the time between two keyframes, [y] of the way from one value to the other. */
data class EasePoint(val x: Float, val y: Float)

/**
 * How a value goes from one keyframe to the next, drawn: [points] between the
 * start (0, 0) and the end (1, 1), which are always there, joined by a smooth
 * curve that never overshoots its nodes (monotone cubic). [y] may go below 0
 * or above 1, past the values of the keyframes.
 */
data class EaseCurve(val points: List<EasePoint> = emptyList()) {

    /** Every node in order, the ends included. */
    val nodes: List<EasePoint> = buildList {
        add(EasePoint(0f, 0f))
        points
            .filter { it.x.isFinite() && it.y.isFinite() && it.x > MIN_GAP / 2 && it.x < 1f - MIN_GAP / 2 }
            .map { EasePoint(it.x, it.y.coerceIn(MIN_Y, MAX_Y)) }
            .sortedBy { it.x }
            .forEach { if (it.x - last().x >= MIN_GAP / 2) add(it) }
        add(EasePoint(1f, 1f))
    }

    private val xs: List<Double> = nodes.map { it.x.toDouble() }
    private val ys: List<Double> = nodes.map { it.y.toDouble() }
    private val tangents: DoubleArray = MonotoneCubic.tangents(xs, ys)

    /** The curve at [p] (0..1). */
    fun at(p: Double): Double = MonotoneCubic.at(xs, ys, p.coerceIn(0.0, 1.0), tangents)

    /** The curve as an FFmpeg expression of [x] (0..1, used several times: keep it short). */
    fun expression(x: String): String {
        fun num(v: Double): String = String.format(Locale.US, "%.5f", v)
        var expression = ""
        for (i in xs.lastIndex - 1 downTo 0) {
            val c = MonotoneCubic.coefficients(xs[i], xs[i + 1], ys[i], ys[i + 1], tangents[i], tangents[i + 1])
            val d = "($x-${num(xs[i])})"
            val body = "${num(c[0])}+$d*(${num(c[1])}+$d*(${num(c[2])}+$d*${num(c[3])}))"
            expression = if (expression.isEmpty()) body else "if(lt($x,${num(xs[i + 1])}),$body,$expression)"
        }
        return expression
    }

    /** With a node at [x], on the curve when [y] is null. */
    fun withNode(x: Float, y: Float? = null): EaseCurve {
        if (x <= MIN_GAP || x >= 1f - MIN_GAP || nodes.any { abs(it.x - x) < MIN_GAP }) return this
        return EaseCurve(points + EasePoint(x, y ?: at(x.toDouble()).toFloat()))
    }

    /** Node [index] (of [nodes]) moved; the ends stay where they are and no node passes another. */
    fun movedNode(index: Int, x: Float, y: Float): EaseCurve {
        if (index <= 0 || index >= nodes.lastIndex) return this
        val placed = EasePoint(x.coerceIn(nodes[index - 1].x + MIN_GAP, nodes[index + 1].x - MIN_GAP), y.coerceIn(MIN_Y, MAX_Y))
        return EaseCurve(nodes.subList(1, nodes.lastIndex).mapIndexed { i, node -> if (i + 1 == index) placed else node })
    }

    /** Without node [index] (of [nodes]); the ends cannot be taken away. */
    fun withoutNode(index: Int): EaseCurve =
        if (index <= 0 || index >= nodes.lastIndex) this else EaseCurve(nodes.subList(1, nodes.lastIndex).filterIndexed { i, _ -> i + 1 != index })

    companion object {
        const val MIN_Y = -0.5f
        const val MAX_Y = 1.5f
        const val MIN_GAP = 0.03f

        /** A curve to start drawing from: the shape of [easing], through a few of its points. */
        fun of(easing: KeyEasing): EaseCurve = when (easing) {
            KeyEasing.LINEAR, KeyEasing.CUSTOM -> EaseCurve(listOf(EasePoint(0.5f, 0.5f)))
            else -> EaseCurve(listOf(0.25f, 0.5f, 0.75f).map { EasePoint(it, Keyframes.ease(easing, it.toDouble()).toFloat()) })
        }
    }
}

/**
 * The values of some properties at one moment: [atMs] on the clock of what it
 * belongs to (see [Keyframes]). [easing] is how the values go on to the next
 * keyframe (with [curve] when it is drawn).
 */
data class Keyframe(
    val atMs: Long,
    val values: Map<KeyProperty, Float>,
    val easing: KeyEasing = KeyEasing.SMOOTH,
    /** The curve [KeyEasing.CUSTOM] follows. */
    val curve: EaseCurve? = null,
) {
    /** [p] (0..1 of the time to the next keyframe) through this keyframe's easing. */
    fun ease(p: Double): Double = Keyframes.ease(easing, p, curve)
}

/**
 * Keyframes of one text, picture, clip or sound: between two of them each
 * property goes from the first value to the second; before the first and
 * after the last it keeps their values. A property no keyframe holds keeps the
 * value of the thing itself.
 *
 * Times are on the clock of what they belong to: for a text, picture or sound
 * the milliseconds from its own start on the exported video (they move with
 * it); for a clip the position in its file (they stay with the footage when the
 * clip is trimmed or split).
 *
 * The preview ([valueAt]) and the export ([expression], an FFmpeg expression of
 * the time) follow the same curve.
 */
data class Keyframes(val frames: List<Keyframe> = emptyList()) {

    val isEmpty: Boolean get() = frames.isEmpty()

    /** The keyframes in time order. */
    val sorted: List<Keyframe> get() = frames.sortedBy { it.atMs }

    /** The keyframe within [toleranceMs] of [atMs], the nearest one; null when there is none. */
    fun at(atMs: Long, toleranceMs: Long = TOLERANCE_MS): Keyframe? =
        frames.filter { abs(it.atMs - atMs) <= toleranceMs }.minByOrNull { abs(it.atMs - atMs) }

    /** True when [property] changes over time (two keyframes hold different values of it). */
    fun animates(property: KeyProperty): Boolean {
        val values = frames.mapNotNull { it.values[property] }
        return values.size >= 2 && values.any { abs(it - values[0]) > EPSILON }
    }

    /** True when any of [properties] changes over time. */
    fun animatesAny(properties: Collection<KeyProperty>): Boolean = properties.any(::animates)

    /** The value of [property] at [atMs]; [fallback] when no keyframe holds it. */
    fun valueAt(property: KeyProperty, atMs: Double, fallback: Float): Float {
        val points = points(property)
        if (points.isEmpty()) return fallback
        if (atMs <= points.first().atMs) return points.first().value
        if (atMs >= points.last().atMs) return points.last().value
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            if (atMs < b.atMs) {
                val span = (b.atMs - a.atMs).toDouble()
                val p = if (span <= 0.0) 1.0 else ((atMs - a.atMs) / span).coerceIn(0.0, 1.0)
                return (a.value + (b.value - a.value) * a.frame.ease(p)).toFloat()
            }
        }
        return points.last().value
    }

    fun valueAt(property: KeyProperty, atMs: Long, fallback: Float): Float = valueAt(property, atMs.toDouble(), fallback)

    /** The largest and smallest value [property] takes ([fallback] included when no keyframe holds it). */
    fun range(property: KeyProperty, fallback: Float): ClosedFloatingPointRange<Float> {
        val values = points(property).map { it.value }.ifEmpty { listOf(fallback) }
        return values.min()..values.max()
    }

    /**
     * [property] over time as an FFmpeg expression, [time] being an expression
     * of the time in seconds on the keyframes' clock (for instance "(t-2.5)").
     * Values are written with [decimals] decimals; [fallback] when no keyframe
     * holds the property. The curve is that of [valueAt].
     */
    fun expression(property: KeyProperty, fallback: Float, time: String, decimals: Int = 4): String {
        val points = points(property)
        fun num(v: Double): String = String.format(Locale.US, "%.${decimals}f", v)
        fun sec(ms: Long): String = String.format(Locale.US, "%.4f", ms / 1000.0)
        if (points.isEmpty()) return num(fallback.toDouble())
        if (points.size == 1 || !animates(property)) return num(points.first().value.toDouble())
        // A long clock (a speed curve) is worked out once and kept in a variable.
        val kept = time.length > LONG_TIME
        val t = if (kept) "ld($TIME_SLOT)" else time
        // From the last piece backwards: if before the next keyframe, this piece, else what follows.
        var expression = num(points.last().value.toDouble())
        for (i in points.lastIndex - 1 downTo 0) {
            val a = points[i]
            val b = points[i + 1]
            val span = (b.atMs - a.atMs).coerceAtLeast(1L)
            val p = "clip(($t-${sec(a.atMs)})/${sec(span)},0,1)"
            val delta = b.value - a.value
            val piece = if (abs(delta) < EPSILON) {
                num(a.value.toDouble())
            } else {
                "${num(a.value.toDouble())}+(${num(delta.toDouble())})*(${easedExpression(a.frame.easing, a.frame.curve, p)})"
            }
            expression = "if(lt($t,${sec(b.atMs)}),$piece,$expression)"
        }
        return if (kept) "if(gte(st($TIME_SLOT,$time),-1e12),$expression,0)" else expression
    }

    /** With [values] set at [atMs] (merged into the keyframe there, or a new one). */
    fun set(atMs: Long, values: Map<KeyProperty, Float>, toleranceMs: Long = TOLERANCE_MS): Keyframes {
        val existing = at(atMs, toleranceMs)
        val updated = if (existing == null) {
            frames + Keyframe(atMs, values)
        } else {
            frames.map { if (it === existing) it.copy(values = it.values + values) else it }
        }
        return Keyframes(updated.sortedBy { it.atMs })
    }

    /** Without the keyframe at [atMs] (within [toleranceMs]). */
    fun remove(atMs: Long, toleranceMs: Long = TOLERANCE_MS): Keyframes {
        val existing = at(atMs, toleranceMs) ?: return this
        return Keyframes(frames.filter { it !== existing })
    }

    /** With the easing of the keyframe at [atMs] changed (and its drawn curve, when [curve] is given). */
    fun withEasing(atMs: Long, easing: KeyEasing, curve: EaseCurve? = null, toleranceMs: Long = TOLERANCE_MS): Keyframes {
        val existing = at(atMs, toleranceMs) ?: return this
        return Keyframes(frames.map { if (it === existing) it.copy(easing = easing, curve = curve ?: it.curve) else it })
    }

    /** Every time moved by [byMs] (a text, picture or sound cut at its start). */
    fun shifted(byMs: Long): Keyframes = if (byMs == 0L) this else Keyframes(frames.map { it.copy(atMs = it.atMs + byMs) })

    /** The keyframe after [atMs] and the one before, for jumping between them. */
    fun next(atMs: Long): Keyframe? = sorted.firstOrNull { it.atMs > atMs + TOLERANCE_MS }

    fun previous(atMs: Long): Keyframe? = sorted.lastOrNull { it.atMs < atMs - TOLERANCE_MS }

    private class Point(val atMs: Long, val value: Float, val frame: Keyframe)

    private fun points(property: KeyProperty): List<Point> =
        frames.mapNotNull { frame -> frame.values[property]?.let { Point(frame.atMs, it, frame) } }.sortedBy { it.atMs }

    companion object {
        val NONE = Keyframes()

        /** How close to a keyframe the playhead counts as on it (about a frame). */
        const val TOLERANCE_MS = 40L

        private const val EPSILON = 1e-4f

        /** Clocks longer than this are kept in a variable rather than repeated. */
        private const val LONG_TIME = 24

        /** Expression variables: the eased fraction's and the clock's (a speed curve uses 1). */
        private const val EASE_SLOT = 0
        private const val TIME_SLOT = 2

        /** [p] (0..1) through [easing] ([curve] for [KeyEasing.CUSTOM]). */
        fun ease(easing: KeyEasing, p: Double, curve: EaseCurve? = null): Double {
            val x = p.coerceIn(0.0, 1.0)
            return when (easing) {
                KeyEasing.LINEAR -> x
                KeyEasing.SMOOTH -> x * x * (3 - 2 * x)
                KeyEasing.EASE_IN -> x * x * x
                KeyEasing.EASE_OUT -> 1 - (1 - x).pow(3)
                KeyEasing.EASE_IN_OUT -> if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
                KeyEasing.BACK -> 1 + BACK_C3 * (x - 1).pow(3) + BACK_C1 * (x - 1).pow(2)
                KeyEasing.BOUNCE -> bounce(x)
                KeyEasing.ELASTIC -> when {
                    x <= 0.0 -> 0.0
                    x >= 1.0 -> 1.0
                    else -> 2.0.pow(-10 * x) * sin((x * 10 - 0.75) * ELASTIC_C4) + 1
                }
                KeyEasing.HOLD -> if (x >= 1.0) 1.0 else 0.0
                KeyEasing.CUSTOM -> (curve ?: EaseCurve()).at(x)
            }
        }

        private fun bounce(x: Double): Double = when {
            x < 1 / BOUNCE_D -> BOUNCE_N * x * x
            x < 2 / BOUNCE_D -> BOUNCE_N * (x - 1.5 / BOUNCE_D).pow(2) + 0.75
            x < 2.5 / BOUNCE_D -> BOUNCE_N * (x - 2.25 / BOUNCE_D).pow(2) + 0.9375
            else -> BOUNCE_N * (x - 2.625 / BOUNCE_D).pow(2) + 0.984375
        }

        /**
         * [p] (an expression of 0..1) through [easing], as an FFmpeg expression
         * (the same curve as [ease]). [p] is worked out once and kept in a variable
         * when the curve needs it more than once.
         */
        fun easedExpression(easing: KeyEasing, curve: EaseCurve?, p: String): String {
            val x = "ld($EASE_SLOT)"
            val body = when (easing) {
                KeyEasing.LINEAR -> return p
                KeyEasing.HOLD -> return "0"
                KeyEasing.SMOOTH -> "$x*$x*(3-2*$x)"
                KeyEasing.EASE_IN -> "$x*$x*$x"
                KeyEasing.EASE_OUT -> "1-pow(1-$x,3)"
                KeyEasing.EASE_IN_OUT -> "if(lt($x,0.5),4*$x*$x*$x,1-pow(2-2*$x,3)/2)"
                KeyEasing.BACK -> "1+${c(BACK_C3)}*pow($x-1,3)+${c(BACK_C1)}*pow($x-1,2)"
                KeyEasing.BOUNCE -> {
                    val n = c(BOUNCE_N)
                    "if(lt($x,${c(1 / BOUNCE_D)}),$n*$x*$x," +
                        "if(lt($x,${c(2 / BOUNCE_D)}),$n*pow($x-${c(1.5 / BOUNCE_D)},2)+0.75," +
                        "if(lt($x,${c(2.5 / BOUNCE_D)}),$n*pow($x-${c(2.25 / BOUNCE_D)},2)+0.9375," +
                        "$n*pow($x-${c(2.625 / BOUNCE_D)},2)+0.984375)))"
                }
                KeyEasing.ELASTIC -> "if(gte($x,1),1,pow(2,-10*$x)*sin(($x*10-0.75)*${c(ELASTIC_C4)})+1)"
                KeyEasing.CUSTOM -> (curve ?: EaseCurve()).expression(x)
            }
            return "if(gte(st($EASE_SLOT,$p),-1),$body,0)"
        }

        private fun c(v: Double): String = String.format(Locale.US, "%.6f", v)

        private const val BACK_C1 = 1.70158
        private const val BACK_C3 = BACK_C1 + 1
        private const val BOUNCE_N = 7.5625
        private const val BOUNCE_D = 2.75
        private const val ELASTIC_C4 = 2 * PI / 3

        /** What a text or picture laid over the video can animate. */
        val OVERLAY = listOf(KeyProperty.X, KeyProperty.Y, KeyProperty.SIZE, KeyProperty.ROTATION, KeyProperty.OPACITY)

        /** What a text can animate (it does not turn). */
        val TEXT = listOf(KeyProperty.X, KeyProperty.Y, KeyProperty.SIZE, KeyProperty.OPACITY)

        /** What a clip can animate. */
        val CLIP = listOf(
            KeyProperty.ZOOM, KeyProperty.PAN_X, KeyProperty.PAN_Y,
            KeyProperty.BRIGHTNESS, KeyProperty.CONTRAST, KeyProperty.SATURATION, KeyProperty.VOLUME,
        )

        /** The picture of a clip, apart from its sound. */
        val FRAMING = listOf(KeyProperty.ZOOM, KeyProperty.PAN_X, KeyProperty.PAN_Y)
        val COLOURS = listOf(KeyProperty.BRIGHTNESS, KeyProperty.CONTRAST, KeyProperty.SATURATION)
    }
}
