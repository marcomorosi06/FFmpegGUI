package com.cuscus.ffmpeggui.core.transition

/*
 * A transition told as layers, each worked out at every pixel (see Ex): what
 * shows behind, the two clips' pictures one over the other, each moved, bent,
 * blurred and faded as its own expressions say, and coloured light laid over
 * them. The export writes it out as one xfade expression, the preview as one
 * shader per layer (see TransitionCode.kt): both draw the same picture.
 */

/** A colour, each channel 0..1. */
data class Rgb(val r: Double, val g: Double, val b: Double) {
    /** BT.709 luma and chroma, full range, 0..1 (chroma 0.5 for grey), as xfade treats YUV. */
    fun yuv(): DoubleArray {
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return doubleArrayOf(y, (b - y) / 1.8556 + 0.5, (r - y) / 1.5748 + 0.5)
    }

    val isGrey: Boolean get() = r == g && g == b

    companion object {
        val BLACK = Rgb(0.0, 0.0, 0.0)
        val WHITE = Rgb(1.0, 1.0, 1.0)
    }
}

/** What a part shows where its picture has been moved away from. */
enum class Wrap {
    /** Nothing: the part is transparent there. */
    NONE,

    /** The picture again, mirrored at its edges (a picture spun or shaken never leaves black corners). */
    MIRROR,
}

/** How a part reads its picture at each point. */
sealed class Sampling {
    /** One pixel. */
    data object Single : Sampling()

    /**
     * The average of pixels at these offsets (fractions of the frame) from the
     * point; the export blurs the picture [native]ly instead (see [NativeBlur]).
     */
    data class Blur(val taps: List<Pair<Ex, Ex>>, val native: NativeBlur) : Sampling()

    /**
     * Luma at the point, the blue chroma ([du1], [dv1]) and the red chroma
     * ([du2], [dv2]) that far away: colour fringes, as a lens or a broken
     * signal gives.
     */
    data class Split(val du1: Ex, val dv1: Ex, val du2: Ex, val dv2: Ex) : Sampling()
}

/** How the export blurs a part whose [Sampling.Blur] the preview reads tap by tap. */
sealed class NativeBlur {
    /** A Gaussian blur, [sigma] in heights of the frame (T only). */
    data class Gauss(val sigma: Ex) : NativeBlur()

    /** A blur along [angle] degrees (0 across, 90 down), [radius] in widths of the frame (T only). */
    data class Streak(val angle: Double, val radius: Ex) : NativeBlur()

    /**
     * The average of the picture placed at each of [copies] (each its corners,
     * see [Part.corners]), as the taps sample it.
     */
    data class Echo(val copies: List<List<Pair<Ex, Ex>>>) : NativeBlur()
}

/**
 * One of the two clips' pictures as a layer: at each point of the screen it
 * shows its picture at ([u], [v]) (0..1 across and down; X, Y for where it
 * is), [alpha] opaque (it may use U, V and LUMA), with its colours
 * [saturation] times as strong and mixed [tintAmount] of the way into [tint].
 *
 * When the picture is only moved, enlarged, turned or tilted in 3D, [corners]
 * also say where its four corners go (top left, top right, bottom left, bottom
 * right; fractions of the frame across and down, T only), for the export to
 * draw it with FFmpeg's perspective; its opacity then is [cornersAlpha] (the
 * picture's own outline making the rest). The export reads [saturation] and
 * [tintAmount] once a frame (T only), [tint] black or white.
 */
data class Part(
    val u: Ex = X,
    val v: Ex = Y,
    val alpha: Ex = Ex.ONE,
    val wrap: Wrap = Wrap.NONE,
    val sampling: Sampling = Sampling.Single,
    val saturation: Ex = Ex.ONE,
    val tint: Rgb = Rgb.BLACK,
    val tintAmount: Ex = Ex.ZERO,
    val corners: List<Pair<Ex, Ex>>? = null,
    val cornersAlpha: Ex = alpha,
) {
    init {
        require(corners == null || corners.size == 4) { "four corners" }
    }

    /** True when the part shows its picture where it is, unmoved. */
    val unmoved: Boolean get() = u == X && v == Y && sampling == Sampling.Single

    /** True when the part shows its picture as it is: nothing to draw but the picture itself. */
    val plain: Boolean get() = unmoved && alpha.isNum(1.0) && saturation.isNum(1.0) && tintAmount.isNum(0.0)

    /** Where the part reads its picture once [wrap] is applied, for a point moved by ([du], [dv]). */
    fun at(du: Ex = Ex.ZERO, dv: Ex = Ex.ZERO): Pair<Ex, Ex> {
        val su = u + du
        val sv = v + dv
        return if (wrap == Wrap.MIRROR) mirror(su) to mirror(sv) else su to sv
    }

    /**
     * [alpha], kept to 0..1, and 0 outside the picture when it has been moved
     * and shows nothing there ([Wrap.NONE]).
     */
    val shownAlpha: Ex
        get() {
            // Up to its last pixel, not to the edge beyond it (a picture moved a whole frame away is gone).
            val inside = if (wrap == Wrap.NONE && (u != X || v != Y)) between(U, num(-EDGE), num(1.0 - EDGE)) * between(V, num(-EDGE), num(1.0 - EDGE)) else Ex.ONE
            return clip(alpha, 0.0, 1.0) * inside
        }
}

/** How far rounding may take a point past an edge of a moved picture and still find it there. */
private const val EDGE = 1e-6

/** Light of [color], [level] times as bright (0..1), laid over the clips [alpha] opaque (X, Y and T only). */
data class Overlay(val color: Rgb, val alpha: Ex, val level: Ex = Ex.ONE) {
    init {
        require(!alpha.uses(Leaf.U, Leaf.V, Leaf.LUMA) && !level.uses(Leaf.U, Leaf.V, Leaf.LUMA)) { "an overlay has no picture of its own" }
    }
}

/**
 * A transition: [backdrop] behind, then the clip going out and the one coming
 * in (the one going out on top when [outgoingOnTop]), then the [overlays].
 * At T = 0 it shows the clip going out, at T = 1 the one coming in.
 */
data class TransitionRecipe(
    val outgoing: Part,
    val incoming: Part,
    val outgoingOnTop: Boolean = false,
    val backdrop: Rgb = Rgb.BLACK,
    val overlays: List<Overlay> = emptyList(),
)
