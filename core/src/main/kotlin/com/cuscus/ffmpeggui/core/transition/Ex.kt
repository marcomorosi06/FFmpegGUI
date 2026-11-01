package com.cuscus.ffmpeggui.core.transition

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * A small language of numbers worked out at each pixel of a transition, so
 * that one description (see TransitionRecipe) gives both the export and the
 * preview: written out as an FFmpeg expression for xfade's custom transition,
 * as AGSL for the preview's shaders, and worked out here for the tests.
 *
 * FFmpeg's expressions keep their variables (st/ld) in one place that xfade's
 * threads share, so they are never used: every expression is a plain tree.
 */

/** What a leaf of an [Ex] stands for. */
enum class Leaf {
    /** Across the frame, 0..1 (left to right). */
    X,

    /** Down the frame, 0..1 (top to bottom). */
    Y,

    /** How far the transition has gone, 0..1. */
    T,

    /** Width and height of the frame in pixels. */
    W,
    H,

    /** Where in its own picture a part shows at this point (see Part.u, Part.v), 0..1. */
    U,
    V,

    /** Luma of a part's own picture where it shows at this point, 0..1. */
    LUMA,
}

/** The operations of an [Ex], with how many operands each takes. */
enum class Op(val arity: Int) {
    ADD(2), SUB(2), MUL(2), DIV(2),
    SIN(1), COS(1), ABS(1), FLOOR(1), SQRT(1), EXP(1),
    POW(2), ATAN2(2), MIN(2), MAX(2), HYPOT(2),
    CLIP(3), LERP(3),

    /** 1 when a < b, else 0. */
    LT(2),

    /** 1 when a >= b, else 0. */
    GTE(2),

    /** 1 when lo <= x <= hi, else 0. */
    BETWEEN(3),

    /** b when c is not 0, else e (c is always 0 or 1 here). */
    IF(3),
    SMOOTHSTEP(3),
}

/** A number for each pixel and moment of a transition. */
sealed class Ex {
    data class Num(val value: Double) : Ex()
    data class Var(val leaf: Leaf) : Ex()
    data class Call(val op: Op, val args: List<Ex>) : Ex() {
        init {
            require(args.size == op.arity) { "$op takes ${op.arity} operands" }
        }
    }

    /**
     * A pixel of one of the two clips' pictures (0 the clip going out, 1 the one
     * coming in), [plane] of its YUV, at [px], [py] in pixels. Only the export
     * reads pixels this way (the preview's shaders sample their own picture).
     */
    data class Pixel(val input: Int, val plane: Int, val px: Ex, val py: Ex) : Ex()

    /** The pixel of one of the two pictures right here, on the plane being worked out. */
    data class Here(val input: Int) : Ex()

    operator fun plus(o: Ex): Ex = add(this, o)
    operator fun plus(o: Double): Ex = add(this, Num(o))
    operator fun minus(o: Ex): Ex = sub(this, o)
    operator fun minus(o: Double): Ex = sub(this, Num(o))
    operator fun times(o: Ex): Ex = mul(this, o)
    operator fun times(o: Double): Ex = mul(this, Num(o))
    operator fun div(o: Ex): Ex = div(this, o)
    operator fun div(o: Double): Ex = div(this, Num(o))
    operator fun unaryMinus(): Ex = sub(ZERO, this)

    /** True when this is the number [v]. */
    fun isNum(v: Double): Boolean = this is Num && value == v

    /** This expression with its [Leaf.U], [Leaf.V] and [Leaf.LUMA] leaves replaced. */
    fun substitute(u: Ex, v: Ex, luma: Ex): Ex = when (this) {
        is Num, is Here -> this
        is Var -> when (leaf) {
            Leaf.U -> u
            Leaf.V -> v
            Leaf.LUMA -> luma
            else -> this
        }
        is Call -> rebuild(op, args.map { it.substitute(u, v, luma) })
        is Pixel -> Pixel(input, plane, px.substitute(u, v, luma), py.substitute(u, v, luma))
    }

    /** True when some leaf of this is one of [leaves]. */
    fun uses(vararg leaves: Leaf): Boolean = when (this) {
        is Num, is Here -> false
        is Var -> leaf in leaves
        is Call -> args.any { it.uses(*leaves) }
        is Pixel -> px.uses(*leaves) || py.uses(*leaves)
    }

    companion object {
        val ZERO: Ex = Num(0.0)
        val ONE: Ex = Num(1.0)
    }
}

// ---------------------------------------------------------------------------
// Building, with the constant parts worked out at once
// ---------------------------------------------------------------------------

val X: Ex = Ex.Var(Leaf.X)
val Y: Ex = Ex.Var(Leaf.Y)
val T: Ex = Ex.Var(Leaf.T)
val W: Ex = Ex.Var(Leaf.W)
val H: Ex = Ex.Var(Leaf.H)
val U: Ex = Ex.Var(Leaf.U)
val V: Ex = Ex.Var(Leaf.V)
val LUMA: Ex = Ex.Var(Leaf.LUMA)

fun num(v: Double): Ex = Ex.Num(v)
fun num(v: Int): Ex = Ex.Num(v.toDouble())

operator fun Double.plus(o: Ex): Ex = add(Ex.Num(this), o)
operator fun Double.minus(o: Ex): Ex = sub(Ex.Num(this), o)
operator fun Double.times(o: Ex): Ex = mul(Ex.Num(this), o)
operator fun Double.div(o: Ex): Ex = div(Ex.Num(this), o)

private fun add(a: Ex, b: Ex): Ex = when {
    a is Ex.Num && b is Ex.Num -> Ex.Num(a.value + b.value)
    a.isNum(0.0) -> b
    b.isNum(0.0) -> a
    else -> Ex.Call(Op.ADD, listOf(a, b))
}

private fun sub(a: Ex, b: Ex): Ex = when {
    a is Ex.Num && b is Ex.Num -> Ex.Num(a.value - b.value)
    b.isNum(0.0) -> a
    else -> Ex.Call(Op.SUB, listOf(a, b))
}

private fun mul(a: Ex, b: Ex): Ex = when {
    a is Ex.Num && b is Ex.Num -> Ex.Num(a.value * b.value)
    a.isNum(0.0) || b.isNum(0.0) -> Ex.ZERO
    a.isNum(1.0) -> b
    b.isNum(1.0) -> a
    else -> Ex.Call(Op.MUL, listOf(a, b))
}

private fun div(a: Ex, b: Ex): Ex = when {
    a is Ex.Num && b is Ex.Num && b.value != 0.0 -> Ex.Num(a.value / b.value)
    a.isNum(0.0) -> Ex.ZERO
    b.isNum(1.0) -> a
    else -> Ex.Call(Op.DIV, listOf(a, b))
}

/** [op] of [args], worked out when they are all numbers. */
private fun rebuild(op: Op, args: List<Ex>): Ex = when (op) {
    Op.ADD -> add(args[0], args[1])
    Op.SUB -> sub(args[0], args[1])
    Op.MUL -> mul(args[0], args[1])
    Op.DIV -> div(args[0], args[1])
    Op.CLIP -> clip(args[0], args[1], args[2])
    Op.LERP -> lerp(args[0], args[1], args[2])
    Op.IF -> iff(args[0], args[1], args[2])
    else -> fold(op, args)
}

private fun fold(op: Op, args: List<Ex>): Ex =
    if (args.all { it is Ex.Num }) Ex.Num(apply(op, args.map { (it as Ex.Num).value })) else Ex.Call(op, args)

fun sin(a: Ex): Ex = fold(Op.SIN, listOf(a))
fun cos(a: Ex): Ex = fold(Op.COS, listOf(a))
fun abs(a: Ex): Ex = fold(Op.ABS, listOf(a))
fun floor(a: Ex): Ex = fold(Op.FLOOR, listOf(a))
fun sqrt(a: Ex): Ex = fold(Op.SQRT, listOf(a))
fun exp(a: Ex): Ex = fold(Op.EXP, listOf(a))

/** [a] to the power [b]; [a] must never be below 0 (AGSL leaves that undefined). */
fun pow(a: Ex, b: Ex): Ex = fold(Op.POW, listOf(a, b))
fun pow(a: Ex, b: Double): Ex = pow(a, num(b))
fun atan2(y: Ex, x: Ex): Ex = fold(Op.ATAN2, listOf(y, x))
fun min(a: Ex, b: Ex): Ex = fold(Op.MIN, listOf(a, b))
fun max(a: Ex, b: Ex): Ex = fold(Op.MAX, listOf(a, b))
fun hypot(a: Ex, b: Ex): Ex = fold(Op.HYPOT, listOf(a, b))
fun lt(a: Ex, b: Ex): Ex = fold(Op.LT, listOf(a, b))
fun gte(a: Ex, b: Ex): Ex = fold(Op.GTE, listOf(a, b))
fun between(x: Ex, lo: Ex, hi: Ex): Ex = fold(Op.BETWEEN, listOf(x, lo, hi))
fun smoothstep(e0: Ex, e1: Ex, x: Ex): Ex = fold(Op.SMOOTHSTEP, listOf(e0, e1, x))
fun smoothstep(e0: Double, e1: Double, x: Ex): Ex = smoothstep(num(e0), num(e1), x)

fun clip(x: Ex, lo: Ex, hi: Ex): Ex = if (x is Ex.Num && lo is Ex.Num && hi is Ex.Num) {
    Ex.Num(x.value.coerceIn(lo.value, hi.value))
} else {
    Ex.Call(Op.CLIP, listOf(x, lo, hi))
}

fun clip(x: Ex, lo: Double, hi: Double): Ex = clip(x, num(lo), num(hi))

/** [a] where [f] is 0, [b] where it is 1. */
fun lerp(a: Ex, b: Ex, f: Ex): Ex = when {
    f.isNum(0.0) -> a
    f.isNum(1.0) -> b
    a == b -> a
    a is Ex.Num && b is Ex.Num && f is Ex.Num -> Ex.Num(a.value + (b.value - a.value) * f.value)
    else -> Ex.Call(Op.LERP, listOf(a, b, f))
}

/** [then] where [condition] (0 or 1) is 1, else [otherwise]. */
fun iff(condition: Ex, then: Ex, otherwise: Ex): Ex = when {
    condition is Ex.Num -> if (condition.value != 0.0) then else otherwise
    then == otherwise -> then
    else -> Ex.Call(Op.IF, listOf(condition, then, otherwise))
}

/** [x] folded back into 0..1 as a mirror would show it (1.2 is 0.8, -0.3 is 0.3). */
fun mirror(x: Ex): Ex = 1.0 - abs(1.0 - (x - 2.0 * floor(x * 0.5)))

/** Fractional part of [x]. */
fun fract(x: Ex): Ex = x - floor(x)

/** xfade's own noise, sin-based, from two numbers (its frand). */
fun noise(a: Ex, b: Ex): Ex = fract(sin(a * 12.9898 + b * 78.233) * 43758.545)

// ---------------------------------------------------------------------------
// Working out
// ---------------------------------------------------------------------------

/** What an [Ex] is worked out against. */
class ExEnv(
    val x: Double,
    val y: Double,
    val t: Double,
    val w: Double,
    val h: Double,
    val u: Double = x,
    val v: Double = y,
    val luma: Double = 0.0,
    /** The current plane, for [Ex.Here]. */
    val plane: Int = 0,
    /** A pixel of a picture: input, plane, x, y (whole pixels, inside the frame). */
    val pixel: (Int, Int, Int, Int) -> Double = { _, _, _, _ -> 0.0 },
)

fun Ex.eval(env: ExEnv): Double = when (this) {
    is Ex.Num -> value
    is Ex.Var -> when (leaf) {
        Leaf.X -> env.x
        Leaf.Y -> env.y
        Leaf.T -> env.t
        Leaf.W -> env.w
        Leaf.H -> env.h
        Leaf.U -> env.u
        Leaf.V -> env.v
        Leaf.LUMA -> env.luma
    }
    is Ex.Call -> when (op) {
        // As FFmpeg works out its own: only the branch chosen.
        Op.IF -> if (args[0].eval(env) != 0.0) args[1].eval(env) else args[2].eval(env)
        else -> apply(op, args.map { it.eval(env) })
    }
    is Ex.Pixel -> {
        // FFmpeg's getpix: clipped into the frame, then truncated.
        val px = px.eval(env).coerceIn(0.0, env.w - 1).toInt()
        val py = py.eval(env).coerceIn(0.0, env.h - 1).toInt()
        env.pixel(input, plane, px, py)
    }
    is Ex.Here -> env.pixel(input, env.plane, Math.round(env.x * env.w).toInt(), Math.round(env.y * env.h).toInt())
}

private fun apply(op: Op, a: List<Double>): Double = when (op) {
    Op.ADD -> a[0] + a[1]
    Op.SUB -> a[0] - a[1]
    Op.MUL -> a[0] * a[1]
    Op.DIV -> a[0] / a[1]
    Op.SIN -> sin(a[0])
    Op.COS -> cos(a[0])
    Op.ABS -> abs(a[0])
    Op.FLOOR -> floor(a[0])
    Op.SQRT -> sqrt(a[0])
    Op.EXP -> exp(a[0])
    Op.POW -> a[0].pow(a[1])
    Op.ATAN2 -> atan2(a[0], a[1])
    Op.MIN -> min(a[0], a[1])
    Op.MAX -> max(a[0], a[1])
    Op.HYPOT -> hypot(a[0], a[1])
    Op.CLIP -> a[0].coerceIn(a[1], a[2])
    Op.LERP -> a[0] + (a[1] - a[0]) * a[2]
    Op.LT -> if (a[0] < a[1]) 1.0 else 0.0
    Op.GTE -> if (a[0] >= a[1]) 1.0 else 0.0
    Op.BETWEEN -> if (a[0] >= a[1] && a[0] <= a[2]) 1.0 else 0.0
    Op.IF -> if (a[0] != 0.0) a[1] else a[2]
    Op.SMOOTHSTEP -> {
        val k = ((a[2] - a[0]) / (a[1] - a[0])).coerceIn(0.0, 1.0)
        k * k * (3.0 - 2.0 * k)
    }
}

// ---------------------------------------------------------------------------
// Writing out
// ---------------------------------------------------------------------------

/** A number in plain decimal notation, at most 6 decimals, never in exponent form. */
internal fun decimal(v: Double, forceFraction: Boolean): String {
    require(v.isFinite()) { "not a number: $v" }
    var text = BigDecimal(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    if (text == "-0") text = "0"
    if (forceFraction && !text.contains('.')) text += ".0"
    return if (text.startsWith("-")) "($text)" else text
}

/**
 * How the leaves of an [Ex] are written in one FFmpeg filter's expressions
 * (each filter has its own variables): null where a leaf has no meaning there.
 */
class FfmpegLeaves(private val names: Map<Leaf, String>) {
    fun name(leaf: Leaf): String = names[leaf] ?: error("$leaf has no name in this FFmpeg expression")

    companion object {
        /**
         * xfade's custom transition: X and Y its pixel's, W and H its frame's, P
         * its progress (1 to 0); A and B the two pictures' pixels here, a0..b2
         * its pixel readers (see [Ex.Here], [Ex.Pixel]).
         */
        val XFADE = FfmpegLeaves(mapOf(Leaf.X to "(X/W)", Leaf.Y to "(Y/H)", Leaf.T to "(1-P)", Leaf.W to "W", Leaf.H to "H"))

        /**
         * geq on a picture [frames] frames long, worked out smaller than the
         * frame of [width] x [height] it stands for: the point is the middle of
         * its pixel, the progress its frame number over [frames]; LUMA, when
         * [luma], the luma of its own (grey) picture.
         */
        fun geq(frames: Int, width: Int, height: Int, luma: Boolean = false): FfmpegLeaves = FfmpegLeaves(
            mapOf(
                Leaf.X to "((X+0.5)/W)",
                Leaf.Y to "((Y+0.5)/H)",
                Leaf.T to "(N/$frames)",
                Leaf.W to "$width",
                Leaf.H to "$height",
            ) + (if (luma) mapOf(Leaf.LUMA to "(lum(X,Y)/255)") else emptyMap()),
        )

        /** A filter that works its expressions out once a frame, its frame number called [frameVar]. */
        fun perFrame(frames: Int, frameVar: String, width: String = "W", height: String = "H"): FfmpegLeaves =
            FfmpegLeaves(mapOf(Leaf.T to "($frameVar/$frames)", Leaf.W to width, Leaf.H to height))
    }
}

/**
 * This expression as FFmpeg writes it, its leaves named by [leaves] (xfade's
 * by default). [U], [V] and [LUMA] must have been replaced (see
 * [Ex.substitute]) unless [leaves] names them.
 */
fun Ex.toFfmpeg(leaves: FfmpegLeaves = FfmpegLeaves.XFADE): String = when (this) {
    is Ex.Num -> decimal(value, forceFraction = false)
    is Ex.Var -> leaves.name(leaf)
    is Ex.Here -> if (input == 0) "A" else "B"
    is Ex.Pixel -> "${if (input == 0) "a" else "b"}$plane(${px.toFfmpeg(leaves)},${py.toFfmpeg(leaves)})"
    is Ex.Call -> {
        val s = args.map { it.toFfmpeg(leaves) }
        when (op) {
            Op.ADD -> "(${s[0]}+${s[1]})"
            Op.SUB -> "(${s[0]}-${s[1]})"
            Op.MUL -> "(${s[0]}*${s[1]})"
            Op.DIV -> "(${s[0]}/${s[1]})"
            Op.SIN -> "sin(${s[0]})"
            Op.COS -> "cos(${s[0]})"
            Op.ABS -> "abs(${s[0]})"
            Op.FLOOR -> "floor(${s[0]})"
            Op.SQRT -> "sqrt(${s[0]})"
            Op.EXP -> "exp(${s[0]})"
            Op.POW -> "pow(${s[0]},${s[1]})"
            Op.ATAN2 -> "atan2(${s[0]},${s[1]})"
            Op.MIN -> "min(${s[0]},${s[1]})"
            Op.MAX -> "max(${s[0]},${s[1]})"
            Op.HYPOT -> "hypot(${s[0]},${s[1]})"
            Op.CLIP -> "clip(${s[0]},${s[1]},${s[2]})"
            Op.LERP -> "lerp(${s[0]},${s[1]},${s[2]})"
            Op.LT -> "lt(${s[0]},${s[1]})"
            Op.GTE -> "gte(${s[0]},${s[1]})"
            Op.BETWEEN -> "between(${s[0]},${s[1]},${s[2]})"
            Op.IF -> "if(${s[0]},${s[1]},${s[2]})"
            Op.SMOOTHSTEP -> {
                val k = "clip((${s[2]}-${s[0]})/(${s[1]}-${s[0]}),0,1)"
                "($k*$k*(3-2*$k))"
            }
        }
    }
}

/**
 * This expression as AGSL: `p` is the point (0..1 across and down), `uT` the
 * progress, `uSize` the frame's size, `u`, `v` and `lum` locals the shader
 * declares first. Written with step and mix only, no conditionals, as the
 * preview's other shaders (see KeyEffect.kt in the app).
 */
fun Ex.toAgsl(): String = when (this) {
    is Ex.Num -> decimal(value, forceFraction = true)
    is Ex.Var -> when (leaf) {
        Leaf.X -> "p.x"
        Leaf.Y -> "p.y"
        Leaf.T -> "uT"
        Leaf.W -> "uSize.x"
        Leaf.H -> "uSize.y"
        Leaf.U -> "u"
        Leaf.V -> "v"
        Leaf.LUMA -> "lum"
    }
    is Ex.Pixel, is Ex.Here -> error("pixels are read by the shader itself")
    is Ex.Call -> {
        val s = args.map { it.toAgsl() }
        when (op) {
            Op.ADD -> "(${s[0]} + ${s[1]})"
            Op.SUB -> "(${s[0]} - ${s[1]})"
            Op.MUL -> "(${s[0]} * ${s[1]})"
            Op.DIV -> "(${s[0]} / ${s[1]})"
            Op.SIN -> "sin(${s[0]})"
            Op.COS -> "cos(${s[0]})"
            Op.ABS -> "abs(${s[0]})"
            Op.FLOOR -> "floor(${s[0]})"
            Op.SQRT -> "sqrt(${s[0]})"
            Op.EXP -> "exp(${s[0]})"
            Op.POW -> "pow(${s[0]}, ${s[1]})"
            Op.ATAN2 -> "atan(${s[0]}, ${s[1]})"
            Op.MIN -> "min(${s[0]}, ${s[1]})"
            Op.MAX -> "max(${s[0]}, ${s[1]})"
            Op.HYPOT -> "length(float2(${s[0]}, ${s[1]}))"
            Op.CLIP -> "clamp(${s[0]}, ${s[1]}, ${s[2]})"
            Op.LERP -> "mix(${s[0]}, ${s[1]}, ${s[2]})"
            Op.LT -> "(1.0 - step(${s[1]}, ${s[0]}))"
            Op.GTE -> "step(${s[1]}, ${s[0]})"
            Op.BETWEEN -> "(step(${s[1]}, ${s[0]}) * step(${s[0]}, ${s[2]}))"
            Op.IF -> "mix(${s[2]}, ${s[1]}, ${s[0]})"
            Op.SMOOTHSTEP -> "smoothstep(${s[0]}, ${s[1]}, ${s[2]})"
        }
    }
}
