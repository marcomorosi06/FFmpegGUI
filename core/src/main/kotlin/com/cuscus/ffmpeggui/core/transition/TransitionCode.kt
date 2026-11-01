package com.cuscus.ffmpeggui.core.transition

/*
 * A TransitionRecipe written out as AGSL for the preview: one shader for each
 * clip's layer and one for the light laid over them. The same layers, worked
 * out pixel by pixel as xfade would (planeExpression), let the tests check a
 * recipe against itself; the export draws them with FFmpeg's own filters (see
 * TransitionGraph.kt).
 */

/** Luma weights of BT.709, as Rgb.yuv. */
private const val KR = 0.2126
private const val KG = 0.7152
private const val KB = 0.0722

/**
 * The value of [plane] (0 luma, 1 blue chroma, 2 red chroma) at each pixel,
 * worked out as xfade's custom transition would for pictures of [depth] bits
 * per sample (YUV 4:4:4, full range: black 0, white the largest value, grey
 * chroma half of it): what the tests check a recipe with.
 */
fun planeExpression(recipe: TransitionRecipe, plane: Int, depth: Int = 8): Ex {
    val max = ((1 shl depth) - 1).toDouble()
    val mid = (((1 shl depth) - 1) / 2).toDouble()
    var color: Ex = num(colorPlane(recipe.backdrop, plane, max, mid))
    val order = if (recipe.outgoingOnTop) listOf(1 to recipe.incoming, 0 to recipe.outgoing) else listOf(0 to recipe.outgoing, 1 to recipe.incoming)
    for ((input, part) in order) {
        color = lerp(color, partSample(part, input, plane, max, mid), partAlpha(part, input, max))
    }
    for (overlay in recipe.overlays) {
        val value = if (plane == 0) {
            overlay.level * (overlay.color.yuv()[0] * max)
        } else if (overlay.color.isGrey) {
            num(mid)
        } else {
            mid + overlay.level * ((overlay.color.yuv()[plane] - 0.5) * max)
        }
        color = lerp(color, value, clip(overlay.alpha, 0.0, 1.0))
    }
    return clip(color, 0.0, max)
}

private fun colorPlane(color: Rgb, plane: Int, max: Double, mid: Double): Double = when {
    plane == 0 -> color.yuv()[0] * max
    color.isGrey -> mid
    else -> color.yuv()[plane] * max
}

/** Where a part reads its picture, for a point moved by ([du], [dv]), in terms of the leaves U and V. */
private fun wrapped(part: Part, du: Ex = Ex.ZERO, dv: Ex = Ex.ZERO): Pair<Ex, Ex> {
    val su = U + du
    val sv = V + dv
    return if (part.wrap == Wrap.MIRROR) mirror(su) to mirror(sv) else su to sv
}

/** A pixel of [input]'s [plane] at (u, v) given in terms of U and V, U and V then replaced by the part's own. */
private fun pixelAt(part: Part, input: Int, plane: Int, at: Pair<Ex, Ex>): Ex =
    Ex.Pixel(input, plane, at.first * W + 0.5, at.second * H + 0.5).substitute(part.u, part.v, Ex.ZERO)

private fun partSample(part: Part, input: Int, plane: Int, max: Double, mid: Double): Ex {
    val luma = pixelAt(part, input, 0, wrapped(part)) / max
    fun own(e: Ex) = e.substitute(part.u, part.v, luma)
    var sample: Ex = when (val sampling = part.sampling) {
        Sampling.Single -> if (part.unmoved) Ex.Here(input) else pixelAt(part, input, plane, wrapped(part))
        is Sampling.Blur -> {
            var sum: Ex = Ex.ZERO
            for ((du, dv) in sampling.taps) sum += pixelAt(part, input, plane, wrapped(part, du, dv))
            sum / sampling.taps.size.toDouble()
        }
        is Sampling.Split -> when (plane) {
            0 -> pixelAt(part, input, 0, wrapped(part))
            1 -> pixelAt(part, input, 1, wrapped(part, sampling.du1, sampling.dv1))
            else -> pixelAt(part, input, 2, wrapped(part, sampling.du2, sampling.dv2))
        }
    }
    if (plane > 0) sample = lerp(num(mid), sample, own(part.saturation))
    return lerp(sample, num(colorPlane(part.tint, plane, max, mid)), clip(own(part.tintAmount), 0.0, 1.0))
}

private fun partAlpha(part: Part, input: Int, max: Double): Ex {
    val luma = pixelAt(part, input, 0, wrapped(part)) / max
    return part.shownAlpha.substitute(part.u, part.v, luma)
}

// ---------------------------------------------------------------------------
// AGSL
// ---------------------------------------------------------------------------

/** Names the preview's shaders are written with, for whoever sets their uniforms. */
object TransitionShaderNames {
    const val CONTENT = "content"
    const val SIZE = "uSize"
    const val PROGRESS = "uT"
}

/**
 * The shader of [part] (the clip going out when [input] is 0), run on its
 * layer's picture: it gives the picture as the part shows it at each point,
 * premultiplied by its opacity, for the layer to be drawn over what is below.
 */
fun partShader(part: Part): String {
    val n = TransitionShaderNames
    val (wu, wv) = wrapped(part)
    val body = StringBuilder()
    body.append("    float2 p = fragCoord / ${n.SIZE};\n")
    body.append("    float u = ${part.u.toAgsl()};\n")
    body.append("    float v = ${part.v.toAgsl()};\n")
    body.append("    half4 s0 = ${eval(wu, wv)};\n")
    body.append("    float lum = float(dot(s0.rgb, half3($KR, $KG, $KB)));\n")
    // Every uniform is read, whatever the part does with it: Android refuses to set a uniform a shader lost.
    body.append("    float a = ${part.shownAlpha.toAgsl()} * step(-1.0, ${n.PROGRESS});\n")
    body.append("    float3 c = float3(s0.rgb);\n")
    when (val sampling = part.sampling) {
        Sampling.Single -> Unit
        is Sampling.Blur -> {
            val taps = sampling.taps.joinToString(" + ") { (du, dv) ->
                val (tu, tv) = wrapped(part, du, dv)
                "float3(${eval(tu, tv)}.rgb)"
            }
            body.append("    c = ($taps) / ${decimal(sampling.taps.size.toDouble(), true)};\n")
        }
        is Sampling.Split -> {
            val (bu, bv) = wrapped(part, sampling.du1, sampling.dv1)
            val (ru, rv) = wrapped(part, sampling.du2, sampling.dv2)
            body.append("    float3 c1 = float3(${eval(bu, bv)}.rgb);\n")
            body.append("    float3 c2 = float3(${eval(ru, rv)}.rgb);\n")
            body.append("    float y0 = dot(c, float3($KR, $KG, $KB));\n")
            body.append("    float cb = (c1.b - dot(c1, float3($KR, $KG, $KB))) / 1.8556;\n")
            body.append("    float cr = (c2.r - dot(c2, float3($KR, $KG, $KB))) / 1.5748;\n")
            body.append("    float rr = y0 + 1.5748 * cr;\n")
            body.append("    float bb = y0 + 1.8556 * cb;\n")
            body.append("    c = float3(rr, (y0 - $KR * rr - $KB * bb) / $KG, bb);\n")
        }
    }
    if (!part.saturation.isNum(1.0)) {
        body.append("    float g = dot(c, float3($KR, $KG, $KB));\n")
        body.append("    c = mix(float3(g), c, ${part.saturation.toAgsl()});\n")
    }
    if (!part.tintAmount.isNum(0.0)) {
        val tint = part.tint
        body.append(
            "    c = mix(c, float3(${decimal(tint.r, true)}, ${decimal(tint.g, true)}, ${decimal(tint.b, true)}), " +
                "clamp(${part.tintAmount.toAgsl()}, 0.0, 1.0));\n",
        )
    }
    body.append("    c = clamp(c, 0.0, 1.0);\n")
    body.append("    return half4(float4(c * a, a));\n")
    return """
uniform shader ${n.CONTENT};
uniform float2 ${n.SIZE};
uniform float ${n.PROGRESS};

half4 main(float2 fragCoord) {
$body}
""".trimStart()
}

/** A pixel of the layer's picture at (u, v), clamped into it as FFmpeg's pixel reader does. */
private fun eval(u: Ex, v: Ex): String {
    val n = TransitionShaderNames
    return "${n.CONTENT}.eval(clamp(float2(${u.toAgsl()}, ${v.toAgsl()}) * ${n.SIZE}, float2(0.5), ${n.SIZE} - 0.5))"
}

/** The shader of the light laid over the clips ([TransitionRecipe.overlays], at least one), premultiplied. */
fun overlayShader(overlays: List<Overlay>): String {
    require(overlays.isNotEmpty())
    val n = TransitionShaderNames
    val body = StringBuilder()
    body.append("    float2 p = fragCoord / ${n.SIZE};\n")
    body.append("    float4 acc = float4(0.0);\n")
    overlays.forEachIndexed { i, overlay ->
        val c = overlay.color
        body.append("    float a$i = clamp(${overlay.alpha.toAgsl()}, 0.0, 1.0);\n")
        body.append(
            "    acc = acc * (1.0 - a$i) + float4(float3(${decimal(c.r, true)}, ${decimal(c.g, true)}, ${decimal(c.b, true)}) * " +
                "(${overlay.level.toAgsl()}) * a$i, a$i);\n",
        )
    }
    body.append("    return half4(clamp(acc, 0.0, 1.0) * step(-1.0, ${n.PROGRESS}));\n")
    return """
uniform float2 ${n.SIZE};
uniform float ${n.PROGRESS};

half4 main(float2 fragCoord) {
$body}
""".trimStart()
}
