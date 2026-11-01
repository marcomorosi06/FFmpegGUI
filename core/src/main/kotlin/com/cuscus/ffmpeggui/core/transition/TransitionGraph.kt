package com.cuscus.ffmpeggui.core.transition

import com.cuscus.ffmpeggui.core.model.TransitionKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * A transition told as layers (TransitionRecipe) exported with FFmpeg's own
 * filters, frame by frame, without working anything out pixel by pixel at full
 * size (an expression per pixel, as xfade's custom transition has, takes
 * minutes for a second of video):
 *
 *  - the joined video so far is cut where the transition starts and the clip
 *    coming in where it ends; the two pieces that overlap make the region the
 *    transition is drawn in, and the three parts are joined again by concat;
 *  - in the region each clip is moved as its recipe says: by perspective when
 *    the recipe gives where its corners go (moved, turned, tilted in 3D), else
 *    by remap, through maps of where each pixel comes from, worked out by geq
 *    at a small size and enlarged (they change smoothly);
 *  - its opacity and the light laid over the clips are masks worked out by geq
 *    at a small size too, enlarged;
 *  - blurs, colour fringes and tones are FFmpeg's own filters (gblur, dblur,
 *    chromashift, eq), their strength sent to them for each frame (sendcmd);
 *  - the layers are laid one over the other by overlay.
 *
 * Everything is worked out from the same recipe the preview draws.
 */

/** Width of the masks worked out by geq before they are enlarged to the frame. */
private const val MASK_WIDTH = 256

/** Width of the maps of where pixels come from (larger expressions: smaller). */
private const val MAP_WIDTH = 192

/**
 * The filtergraph lines joining [outgoing] (the video so far, a label such as
 * "[v0]") to [incoming] (the next clip) through a [kind] transition drawn from
 * its recipe: the transition starts [startFrame] frames into [outgoing] and
 * lasts [frames] frames, both [width] x [height] at [fps]. Labels made up start
 * with [prefix]; the result is [output] (with its brackets), on [clock] (a
 * settb filter: concat gives a clock of its own, which xfade after it would
 * refuse). Null when the kind has no recipe or the region is too short to
 * draw: xfade then does it.
 */
fun nativeTransitionJoin(
    kind: TransitionKind,
    outgoing: String,
    incoming: String,
    startFrame: Int,
    frames: Int,
    width: Int,
    height: Int,
    fps: Int,
    prefix: String,
    output: String,
    clock: String = "settb=1/$fps",
): List<String>? {
    val recipe = kind.recipe ?: return null
    if (startFrame < 1 || frames < 2 || width < 2 || height < 2 || fps <= 0) return null
    return RegionGraph(recipe, width, height, fps, frames, prefix).join(outgoing, incoming, startFrame, output, clock)
}

private class RegionGraph(
    val recipe: TransitionRecipe,
    val width: Int,
    val height: Int,
    val fps: Int,
    val frames: Int,
    val prefix: String,
) {
    /**
     * The region's frames run from T = 0 to T = 1 in this many steps: its first
     * frame shows the clip going out alone and its last the one coming in alone.
     */
    private val steps = frames - 1

    private val lines = mutableListOf<String>()
    private var counter = 0

    /** Sources (a chain with no input) and the labels each feeds, made once and split. */
    private val sources = LinkedHashMap<String, MutableList<String>>()

    private fun label(): String = "[$prefix${counter++}]"
    private fun id(): String = "$prefix${counter++}"

    /** A label carrying what [source] makes (a chain starting with a source filter). */
    private fun from(source: String): String {
        val out = label()
        sources.getOrPut(source) { mutableListOf() } += out
        return out
    }

    /** [input] through [chain] (filters joined by commas). */
    private fun chain(input: String, chain: String): String {
        val out = label()
        lines += "$input$chain$out"
        return out
    }

    /** [input] split in [n]. */
    private fun split(input: String, n: Int): List<String> {
        val outs = List(n) { label() }
        lines += "${input}split=$n${outs.joinToString("")}"
        return outs
    }

    private val maskSize = small(MASK_WIDTH)
    private val mapSize = small(MAP_WIDTH)

    private fun small(w: Int): Pair<Int, Int> {
        val mw = even(min(width, w))
        val mh = even(max(2, (mw.toDouble() * height / width).roundToInt()))
        return mw to mh
    }

    private fun even(v: Int) = max(2, v - v % 2)

    fun join(outgoing: String, incoming: String, startFrame: Int, output: String, clock: String): List<String> {
        val (body, tailIn) = split(outgoing, 2)
        val bodyCut = chain(body, "trim=end_frame=$startFrame,setpts=PTS-STARTPTS")
        val tail = chain(tailIn, "trim=start_frame=$startFrame:end_frame=${startFrame + frames},setpts=PTS-STARTPTS")
        val (headIn, restIn) = split(incoming, 2)
        val head = chain(headIn, "trim=end_frame=$frames,setpts=PTS-STARTPTS")
        val rest = chain(restIn, "trim=start_frame=$frames,setpts=PTS-STARTPTS")

        val a = layer(recipe.outgoing, tail)
        val b = layer(recipe.incoming, head)
        val (lower, upper) = if (recipe.outgoingOnTop) b to a else a to b
        var picture = if (lower.transparent) {
            overlay(from("color=c=${hex(recipe.backdrop)}:s=${width}x$height:r=$fps,trim=end_frame=$frames,format=yuv420p"), lower.label)
        } else {
            lower.label
        }
        picture = if (upper.transparent) overlay(picture, upper.label) else upper.label
        for (light in recipe.overlays) picture = overlay(picture, light(light))
        val region = chain(picture, "format=yuv420p,setsar=1")
        lines += "$bodyCut$region${rest}concat=n=3:v=1:a=0${if (clock.isEmpty()) "" else ",$clock"}$output"

        val made = mutableListOf<String>()
        for ((source, outs) in sources) {
            made += if (outs.size == 1) "$source${outs[0]}" else "$source,split=${outs.size}${outs.joinToString("")}"
        }
        return made + lines
    }

    private fun overlay(under: String, over: String): String {
        val out = label()
        lines += "$under${over}overlay=format=auto:eof_action=pass$out"
        return out
    }

    private class Layer(val label: String, val transparent: Boolean)

    /** One clip's picture ([input], the region's frames) drawn as [part]. */
    private fun layer(part: Part, input: String): Layer {
        var cur = input
        tone(part)?.let { cur = chain(cur, it) }
        var clear = false
        when (val sampling = part.sampling) {
            Sampling.Single -> Unit
            is Sampling.Split -> cur = chain(cur, chroma(sampling))
            is Sampling.Blur -> when (val native = sampling.native) {
                is NativeBlur.Gauss -> cur = chain(cur, commanded("gblur", "sigma", native.sigma * H, "steps=2"))
                is NativeBlur.Streak -> cur = chain(cur, commanded("dblur", "radius", native.radius * W, "angle=${native.angle.roundToInt()}"))
                is NativeBlur.Echo -> {
                    clear = native.copies.any { !covers(it) }
                    val copies = split(cur, native.copies.size).zip(native.copies).map { (copy, corners) -> chain(copy, perspective(corners, clear)) }
                    cur = label()
                    lines += "${copies.joinToString("")}mix=inputs=${copies.size}:weights='${List(copies.size) { "1" }.joinToString(" ")}'$cur"
                }
            }
        }
        val echoed = part.sampling is Sampling.Blur && (part.sampling as Sampling.Blur).native is NativeBlur.Echo
        val corners = part.corners
        val alpha: Ex = when {
            echoed -> part.alpha
            part.unmovedPicture -> part.alpha
            corners != null -> {
                clear = !covers(corners)
                cur = chain(cur, perspective(corners, clear))
                part.cornersAlpha
            }
            else -> {
                cur = warp(cur, part)
                part.shownAlpha.substitute(part.u, part.v, LUMA)
            }
        }
        val opacity = clip(alpha, 0.0, 1.0)
        if (opacity.isNum(1.0)) return Layer(cur, clear)
        require(!opacity.uses(Leaf.U, Leaf.V)) { "opacity left in terms of U, V" }
        val mask = if (opacity.uses(Leaf.LUMA)) {
            val (keep, look) = split(cur, 2)
            cur = keep
            val (mw, mh) = maskSize
            chain(look, "scale=$mw:$mh,format=gray,geq=lum='${(opacity * 255.0).toFfmpeg(FfmpegLeaves.geq(steps, width, height, luma = true))}',scale=$width:$height:flags=bilinear")
        } else {
            mask(opacity)
        }
        val merged = if (clear) {
            val (keep, look) = split(cur, 2)
            val own = chain(look, "alphaextract")
            val both = label()
            lines += "$own${mask}blend=all_mode=multiply$both"
            label().also { lines += "$keep${both}alphamerge$it" }
        } else {
            label().also { lines += "$cur${mask}alphamerge$it" }
        }
        return Layer(merged, true)
    }

    /** The part's picture is where it is (no move nor blur that moves it). */
    private val Part.unmovedPicture: Boolean get() = u == X && v == Y

    /** A grey mask of [opacity] (0..1, X, Y and T), the frame's size. */
    private fun mask(opacity: Ex): String {
        val (mw, mh) = if (opacity.uses(Leaf.X, Leaf.Y)) maskSize else 2 to 2
        return from(
            "color=c=black:s=${mw}x$mh:r=$fps,trim=end_frame=$frames,format=gray," +
                "geq=lum='${(opacity * 255.0).toFfmpeg(FfmpegLeaves.geq(steps, width, height))}',scale=$width:$height:flags=bilinear",
        )
    }

    /** Light of [light]'s colour laid where its mask says, the frame's size. */
    private fun light(light: Overlay): String {
        val color = if (light.level.isNum(1.0)) {
            from("color=c=${hex(light.color)}:s=${width}x$height:r=$fps,trim=end_frame=$frames")
        } else {
            // A light of changing brightness is snow (see TV_NOISE): grey, its noise FFmpeg's own.
            from("color=c=0x808080:s=${width}x$height:r=$fps,trim=end_frame=$frames,noise=alls=100:allf=t+u")
        }
        return label().also { lines += "$color${mask(clip(light.alpha, 0.0, 1.0))}alphamerge$it" }
    }

    /** eq for the part's saturation and tint (black or white), once a frame; null when it has none. */
    private fun tone(part: Part): String? {
        if (part.saturation.isNum(1.0) && part.tintAmount.isNum(0.0)) return null
        val tint = part.tint
        require(tint == Rgb.BLACK || tint == Rgb.WHITE) { "the export tints to black or white only" }
        require(!part.saturation.uses(Leaf.X, Leaf.Y, Leaf.U, Leaf.V, Leaf.LUMA) && !part.tintAmount.uses(Leaf.X, Leaf.Y, Leaf.U, Leaf.V, Leaf.LUMA)) {
            "the export changes a picture's tone once a frame"
        }
        val leaves = FfmpegLeaves.perFrame(steps, "n", width.toString(), height.toString())
        val amount = clip(part.tintAmount, 0.0, 1.0)
        // Mixing toward black or white by a: luma times (1 - a) about its middle, plus or minus a / 2.
        val contrast = 1.0 - amount
        val brightness = if (tint == Rgb.WHITE) amount * 0.5 else amount * -0.5
        val saturation = part.saturation * (1.0 - amount)
        return "eq=eval=frame:contrast='${contrast.toFfmpeg(leaves)}':brightness='${brightness.toFfmpeg(leaves)}'" +
            ":saturation='${saturation.toFfmpeg(leaves)}'"
    }

    /** chromashift moving the chroma planes as [split] reads them, sent for each frame. */
    private fun chroma(split: Sampling.Split): String {
        // The chroma planes are half the frame's size; a plane read further on shows its picture moved back.
        val shifts = listOf(
            "cbh" to -split.du1 * (width / 2.0),
            "cbv" to -split.dv1 * (height / 2.0),
            "crh" to -split.du2 * (width / 2.0),
            "crv" to -split.dv2 * (height / 2.0),
        ).map { (name, e) -> name to { k: Int -> value(e, k).roundToInt().coerceIn(-255, 255).toString() } }
        return commandedMany("chromashift", shifts, "edge=smear")
    }

    /** [filter] whose [option] follows [value] (T only) frame by frame, through sendcmd. */
    private fun commanded(filter: String, option: String, value: Ex, extra: String): String =
        commandedMany(filter, listOf(option to { k: Int -> fixed(max(0.0, value(value, k)), 3) }), extra)

    private fun commandedMany(filter: String, options: List<Pair<String, (Int) -> String>>, extra: String): String {
        val name = "$filter@${id()}"
        val commands = StringBuilder()
        val last = options.map { (_, at) -> at(0) }.toMutableList()
        for (k in 1 until frames) {
            val changed = options.mapIndexedNotNull { i, (option, at) ->
                val now = at(k)
                if (now != last[i]) {
                    last[i] = now
                    "$name $option $now"
                } else {
                    null
                }
            }
            // Half a frame early, so that it is there for frame k whatever the rounding.
            if (changed.isNotEmpty()) commands.append(fixed((k - 0.5) / fps, 6)).append(' ').append(changed.joinToString(", ")).append(';')
        }
        val initial = options.joinToString(":") { (option, at) -> "$option=${at(0)}" }
        val send = if (commands.isEmpty()) "" else "sendcmd=c='$commands',"
        return "$send$name=$initial:$extra"
    }

    /** [e] (T, W and H only) at frame [k] of the region. */
    private fun value(e: Ex, k: Int): Double =
        e.eval(ExEnv(0.0, 0.0, k.toDouble() / steps, width.toDouble(), height.toDouble()))

    /** perspective sending the picture's corners to [corners], transparent around when [clear]. */
    private fun perspective(corners: List<Pair<Ex, Ex>>, clear: Boolean): String {
        // perspective counts its frames from 1.
        val leaves = FfmpegLeaves.perFrame(steps, "(in-1)")
        val points = corners.mapIndexed { i, (x, y) -> "x$i='${(x * W).toFfmpeg(leaves)}':y$i='${(y * H).toFfmpeg(leaves)}'" }
        // A transparent edge, which the filter carries out to everything around the picture.
        val edge = if (clear) "format=yuva420p,drawbox=x=0:y=0:w=iw:h=ih:t=1:c=black@0:replace=1," else ""
        return "${edge}perspective=${points.joinToString(":")}:sense=destination:eval=frame"
    }

    /** True when the picture with these [corners] covers the whole frame at every frame of the region. */
    private fun covers(corners: List<Pair<Ex, Ex>>): Boolean = (0 until frames).all { k ->
        val q = corners.map { (x, y) -> value(x, k) to value(y, k) }
        // Top left, top right, bottom right, bottom left: the outline in order.
        val outline = listOf(q[0], q[1], q[3], q[2])
        listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0).all { inside(outline, it, 1.0 / max(width, height)) }
    }

    /** True when [p] is inside the convex [outline] by at least [margin]. */
    private fun inside(outline: List<Pair<Double, Double>>, p: Pair<Double, Double>, margin: Double): Boolean {
        var sign = 0
        for (i in outline.indices) {
            val (ax, ay) = outline[i]
            val (bx, by) = outline[(i + 1) % outline.size]
            val len = kotlin.math.hypot(bx - ax, by - ay).takeIf { it > 1e-9 } ?: return false
            val cross = ((bx - ax) * (p.second - ay) - (by - ay) * (p.first - ax)) / len
            if (abs(cross) < margin) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** remap of [input] through maps of where [part] reads its picture, worked out small and enlarged. */
    private fun warp(input: String, part: Part): String {
        val (pu, pv) = if (part.wrap == Wrap.MIRROR) mirror(part.u) to mirror(part.v) else clip(part.u, 0.0, 1.0) to clip(part.v, 0.0, 1.0)
        val xmap = map(clip(pu * width.toDouble() - 0.5, 0.0, width - 1.0))
        val ymap = map(clip(pv * height.toDouble() - 0.5, 0.0, height - 1.0))
        return label().also { lines += "$input$xmap${ymap}remap=format=color:fill=black$it" }
    }

    /** A 16-bit grey map of [value] (pixels of the frame, X, Y and T), the frame's size. */
    private fun map(value: Ex): String {
        val (mw, mh) = if (value.uses(Leaf.X, Leaf.Y)) mapSize else 2 to 2
        return from(
            "color=c=black:s=${mw}x$mh:r=$fps,trim=end_frame=$frames,format=gray16le," +
                "geq=lum='${value.toFfmpeg(FfmpegLeaves.geq(steps, width, height))}',scale=$width:$height:flags=bilinear",
        )
    }

    private fun hex(c: Rgb): String {
        fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        return String.format(java.util.Locale.US, "0x%02X%02X%02X", ch(c.r), ch(c.g), ch(c.b))
    }

    private fun fixed(v: Double, decimals: Int): String = String.format(java.util.Locale.US, "%.${decimals}f", v)
}
