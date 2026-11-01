package com.cuscus.ffmpeggui.core.look

import com.cuscus.ffmpeggui.core.command.fmt
import com.cuscus.ffmpeggui.core.model.LookSpan
import com.cuscus.ffmpeggui.core.model.PictureLook
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The picture looks (PictureLook): each told twice from one set of numbers,
 * as FFmpeg filters for the export (lookSpanGraph) and as an AGSL shader for
 * the preview (lookShader). Whatever moves is a function of the time since
 * the look started, taken modulo its period, so that it loops seamlessly for
 * as long as it lasts; the noise is new on every frame, the same at every
 * moment of the loop. The pseudo-random choices (a glitch's bands, the
 * scratches of an old film) come from one small hash, h(x) = fract(sin(0.37 x
 * + 0.3) * 43.758), written in both: its numbers stay small, so that a phone's
 * single-precision floats work it out as FFmpeg's doubles do.
 *
 * Every filter of the export is on the whole video, switched on only while
 * the look lasts (timeline `enable`): nothing is cut out and put back, so no
 * frame waits in a queue for the part of the video the look starts at.
 */

/** The names the look shaders are written with. */
object LookShaderNames {
    const val CONTENT = "content"
    const val SIZE = "uSize"
    const val TIME = "uTime"
    const val STRENGTH = "uStrength"
    const val HUE = "uHue"
}

/** True when the look's filters need frames at a steady rate (its time is counted in frames). */
internal val PictureLook.countsFrames: Boolean get() = this == PictureLook.SHAKE || this == PictureLook.PULSE

/** FFmpeg's form of the looks' hash h(x) (see the file's comment). */
internal fun hashExpr(x: String): String = "(sin(($x)*0.37+0.3)*43.758-floor(sin(($x)*0.37+0.3)*43.758))"

/**
 * [span]'s look over the video [input] ([width] x [height], [fps] frames a
 * second) into [output]: the look's filters, then, unless it is at full
 * strength and neither fades nor turns, mixed back over the picture as it is
 * (its strength as opacity, its fades). Labels start with [prefix].
 */
fun lookSpanGraph(span: LookSpan, input: String, output: String, prefix: String, width: Int, height: Int, fps: Int): List<String> {
    val start = span.startMs.coerceAtLeast(0L) / 1000.0
    val end = span.endMs / 1000.0
    if (end <= start) return listOf("[$input]null[$output]")
    val window = fmt("between(t,%.4f,%.4f)", start, end)
    val strength = span.strength.coerceIn(0f, 1f)
    val turn = ((span.hueDegrees % 360f) + 360f) % 360f
    val turns = turn > 0.5f && turn < 359.5f
    val fades = span.fadeInSeconds > 0f || span.fadeOutSeconds > 0f
    val whole = strength >= 0.999f && !fades && !turns
    if (whole) return lookGraph(span.look, input, output, prefix, width, height, fps, start, window)

    val keep = "${prefix}keep"
    val source = "${prefix}src"
    val looked = "${prefix}raw"
    val mixed = "${prefix}mix"
    val segments = mutableListOf("[$input]split=2[$keep][$source]")
    segments += lookGraph(span.look, source, looked, prefix, width, height, fps, start, window)
    val finish = mutableListOf<String>()
    if (turns) finish += fmt("hue=h=%.1f:enable='%s'", turn, window)
    finish += "format=yuva420p"
    if (strength < 0.999f) finish += fmt("lut=a='val*%.4f':enable='%s'", strength, window)
    if (span.fadeInSeconds > 0f) finish += fmt("fade=t=in:st=%.4f:d=%.4f:alpha=1", start, span.fadeInSeconds.toDouble())
    if (span.fadeOutSeconds > 0f) {
        val out = (end - span.fadeOutSeconds).coerceAtLeast(start)
        finish += fmt("fade=t=out:st=%.4f:d=%.4f:alpha=1", out, span.fadeOutSeconds.toDouble())
    }
    segments += "[$looked]${finish.joinToString(",")}[$mixed]"
    segments += "[$keep][$mixed]overlay=format=auto:eof_action=pass:enable='$window'[$output]"
    return segments
}

/** The look's own filters, from [input] to [output], on only within [window] (an expression of t). */
internal fun lookGraph(
    look: PictureLook,
    input: String,
    output: String,
    prefix: String,
    width: Int,
    height: Int,
    fps: Int,
    startSeconds: Double,
    window: String,
): List<String> {
    val w = width.coerceAtLeast(2)
    val h = (if (height > 0) height else w * 9 / 16).coerceAtLeast(2)
    val scale = w / 1280.0
    val s = fmt("%.4f", startSeconds)
    val p = look.periodSeconds
    val period = fmt("%.4f", p)
    // A frame that falls right on a step of the loop (its time a hair off, in binary) is
    // counted in the step it starts, in every turn of the loop alike.
    /** Where in its loop the look is, 0..1. */
    val phase = "(mod(t-$s+$EPSILON,$period)/$period)"
    /** The step of the loop it is at, of [k] a loop. */
    fun step(k: Int) = "floor(mod(t-$s+$EPSILON,$period)/$period*$k)"
    fun on(condition: String) = "enable='$window*$condition'"
    val onWindow = "enable='$window'"
    val rate = fps.coerceIn(1, 120)

    fun chain(vararg filters: String) = listOf("[$input]${filters.joinToString(",")}[$output]")

    return when (look) {
        PictureLook.BLACK_WHITE -> chain("hue=s=0:$onWindow", "eq=contrast=1.12:$onWindow")

        PictureLook.OLD_FILM -> {
            val n = step(12)
            val scratch = max(2, (2 * scale / 2).roundToInt() * 2)
            val segments = mutableListOf(
                "[$input]colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131:$onWindow," +
                    "eq=brightness='0.025*sin(2*PI*12*$phase)+0.015*sin(2*PI*7*$phase+1)':eval=frame:$onWindow," +
                    "noise=c0s=16:c0f=t+u:$onWindow,vignette=angle=0.75:$onWindow[${prefix}o0]",
            )
            var previous = "${prefix}o0"
            for (k in 0 until 3) {
                val line = "${prefix}os$k"
                segments += "color=c=0xF2EAD3:s=${scratch}x$h:r=$rate,format=yuva420p,lut=a=115[$line]"
                val next = if (k == 2) output else "${prefix}o${k + 1}"
                // shortest: the line's source never ends; the video does.
                segments += "[$previous][$line]overlay=x='W*${hashExpr("$n*3+${k + 1}")}':y=0:format=auto:shortest=1:" +
                    on("lt(${hashExpr("$n*3+${k + 50}")},0.3)") + "[$next]"
                previous = next
            }
            segments
        }

        PictureLook.VHS -> {
            val shift = max(1, (3 * scale).roundToInt())
            val lines = max(2, (h / 360.0).roundToInt())
            val band = evenAtLeast2(0.07 * h)
            listOf(
                "[$input]rgbashift=rh=-$shift:bh=$shift:$onWindow," +
                    fmt("gblur=sigma=%.3f:sigmaV=0.01:%s,", 1.2 * scale, onWindow) +
                    "eq=saturation=1.35:contrast=1.06:brightness=0.02:$onWindow," +
                    "noise=c0s=10:c0f=t+u:$onWindow," +
                    "drawgrid=w=iw:h=$lines:t=1:c=black@0.22:$onWindow[${prefix}v0]",
                "color=c=white@0.10:s=${w}x$band:r=$rate,format=yuva420p[${prefix}v1]",
                "[${prefix}v0][${prefix}v1]overlay=x=0:y='$phase*(H+h)-h':format=auto:shortest=1:$onWindow[$output]",
            )
        }

        PictureLook.GLITCH -> {
            val n = step(20)
            val burst = "lt(${hashExpr("$n+1")},0.35)"
            val shift = max(1, (12 * scale).roundToInt())
            val segments = mutableListOf(
                "[$input]rgbashift=rh=-$shift:bh=$shift:${on(burst)}[${prefix}g0]",
                "[${prefix}g0]split=4[${prefix}g1][${prefix}c0][${prefix}c1][${prefix}c2]",
            )
            var previous = "${prefix}g1"
            GLITCH_BANDS.forEachIndexed { k, size ->
                val y = hashExpr("$n*3+${k + 10}")
                segments += fmt("[${prefix}c$k]crop=w=iw:h=floor(ih*%.2f/2)*2:x=0:y='(ih-oh)*$y'[${prefix}b$k]", size)
                val next = "${prefix}g${k + 2}"
                segments += "[$previous][${prefix}b$k]overlay=x='(${hashExpr("$n*5+${k + 30}")}-0.5)*0.24*W':y='(H-h)*$y':format=auto:" +
                    on("$burst*lt(${hashExpr("$n*7+${k + 20}")},0.7)") + "[$next]"
                previous = next
            }
            segments += "[$previous]negate=${on("lt(${hashExpr("$n+40")},0.06)")}[$output]"
            segments
        }

        PictureLook.TV_STATIC -> {
            val n = step(16)
            val lines = max(2, (h / 240.0).roundToInt())
            val band = evenAtLeast2(0.12 * h)
            listOf(
                "[$input]eq=saturation=0.5:contrast=0.95:$onWindow," +
                    "eq=contrast=0.35:brightness=0.08:${on("lt(${hashExpr("$n+3")},0.15)")}," +
                    "noise=c0s=55:c0f=t+u:$onWindow," +
                    "drawgrid=w=iw:h=$lines:t=1:c=black@0.18:$onWindow[${prefix}s0]",
                "color=c=white@0.08:s=${w}x$band:r=$rate,format=yuva420p[${prefix}s1]",
                "[${prefix}s0][${prefix}s1]overlay=x=0:y='$phase*(H+h)-h':format=auto:shortest=1:$onWindow[$output]",
            )
        }

        PictureLook.PRISM -> {
            val shift = max(1, (6 * scale).roundToInt())
            chain("rgbashift=rh=-$shift:bh=$shift:$onWindow", "eq=saturation=1.15:$onWindow")
        }

        PictureLook.SHAKE, PictureLook.PULSE -> {
            // Time in frames of a steady rate (perspective knows no other clock): the frame
            // counted from 1, as perspective counts it.
            val tau = "((in-1)/$rate-$s+$EPSILON)"
            val loop = "(mod($tau,$period)/$period)"
            val corners = if (look == PictureLook.SHAKE) {
                shakeCorners(loop)
            } else {
                zoomCorners("(1+0.08*exp(-7*$loop))")
            }
            val moved = "${prefix}m"
            val keep = "${prefix}k"
            val steady = "${prefix}r"
            listOf(
                "[$input]split=2[$keep][$steady]",
                "[$steady]fps=$rate,perspective=$corners:sense=source:eval=frame:$onWindow[$moved]",
                "[$keep][$moved]overlay=format=auto:eof_action=pass:$onWindow[$output]",
            )
        }

        PictureLook.RAINBOW -> chain("hue=H='2*PI*$phase':s=1.25:$onWindow")

        PictureLook.STROBE -> chain("eq=brightness='0.45*pow(max(0,cos(2*PI*$phase)),8)':contrast=1.08:eval=frame:$onWindow")

        PictureLook.PIXELATE -> {
            val block = max(4, (w / 90.0).roundToInt())
            chain("pixelize=w=$block:h=$block:$onWindow")
        }

        PictureLook.INVERT -> chain("negate=$onWindow")

        PictureLook.MIRROR -> listOf(
            "[$input]split=3[${prefix}k][${prefix}l][${prefix}r]",
            "[${prefix}l]crop=iw/2:ih:0:0[${prefix}lh]",
            "[${prefix}r]crop=iw/2:ih:0:0,hflip[${prefix}rh]",
            "[${prefix}lh][${prefix}rh]hstack[${prefix}m]",
            "[${prefix}k][${prefix}m]overlay=format=auto:eof_action=pass:$onWindow[$output]",
        )

        PictureLook.GLOW -> listOf(
            "[$input]split=2[${prefix}a][${prefix}b]",
            fmt("[${prefix}b]gblur=sigma=%.3f:%s[${prefix}g]", 0.012 * w, onWindow),
            "[${prefix}a][${prefix}g]blend=all_mode=screen:c0_opacity=0.55:c1_opacity=0:c2_opacity=0:$onWindow[$output]",
        )
    }
}

/** Seconds added to the time a look reads (see lookGraph). */
private const val EPSILON = "0.0001"

/** The heights of a glitch's three bands, fractions of the frame (later ones on top). */
internal val GLITCH_BANDS = listOf(0.06, 0.10, 0.04)

private fun evenAtLeast2(x: Double): Int = max(2, (x / 2).roundToInt() * 2)

/**
 * perspective's corners (sense=source: the part of the picture that fills the
 * frame) for a shake at [loop] (0..1): a little larger than the frame, moved and
 * turned, never past the picture's edges.
 */
private fun shakeCorners(loop: String): String {
    val dx = "(0.028*W*(0.6*sin(2*PI*3*$loop)+0.4*sin(2*PI*7*$loop+1.3)))"
    val dy = "(0.016*H*(0.6*sin(2*PI*4*$loop+0.7)+0.4*sin(2*PI*9*$loop+2.1)))"
    val a = "(0.01*sin(2*PI*5*$loop+0.4))"
    return corners("(W/2+$dx)", "(H/2+$dy)", "(W/2.14)", "(H/2.14)", a)
}

/** perspective's corners of the frame seen [zoom] times closer, in the middle. */
private fun zoomCorners(zoom: String): String = corners("(W/2)", "(H/2)", "(W/(2*$zoom))", "(H/(2*$zoom))", null)

private fun corners(cx: String, cy: String, hw: String, hh: String, angle: String?): String {
    val signs = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
    return signs.mapIndexed { i, (sx, sy) ->
        val x: String
        val y: String
        if (angle == null) {
            x = "$cx${sign(sx)}$hw"
            y = "$cy${sign(sy)}$hh"
        } else {
            x = "$cx${sign(sx)}cos($angle)*$hw${sign(-sy)}sin($angle)*$hh"
            y = "$cy${sign(sx)}sin($angle)*$hw${sign(sy)}cos($angle)*$hh"
        }
        "x$i='$x':y$i='$y'"
    }.joinToString(":")
}

private fun sign(s: Int) = if (s < 0) "-" else "+"

// ---------------------------------------------------------------------------
// Shaders
// ---------------------------------------------------------------------------

private val SHADER_HEAD = """
uniform shader content;
uniform float2 uSize;
uniform float uTime;
uniform float uStrength;
uniform float uHue;

const float3 Y601 = float3(0.299, 0.587, 0.114);

float h1(float x) { return fract(sin(x * 0.37 + 0.3) * 43.758); }

float grain(float2 p, float f) {
    float3 q = fract(float3(floor(p.x), floor(p.y), floor(p.x)) * 0.1031 + f * 0.1373);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float3 px(float2 p) { return float3(content.eval(clamp(p, float2(0.5), uSize - 0.5)).rgb); }

float3 turnHue(float3 c, float a, float sat) {
    float y = dot(c, Y601);
    float u = dot(c, float3(-0.168736, -0.331264, 0.5));
    float v = dot(c, float3(0.5, -0.418688, -0.081312));
    float cs = cos(a) * sat;
    float sn = sin(a) * sat;
    float u2 = cs * u - sn * v;
    float v2 = sn * u + cs * v;
    return float3(y + 1.402 * v2, y - 0.344136 * u2 - 0.714136 * v2, y + 1.772 * u2);
}

float3 shifted(float2 p, float r) {
    return float3(px(p + float2(r, 0.0)).r, px(p).g, px(p - float2(r, 0.0)).b);
}

float3 colourful(float3 c, float s) {
    float y = dot(c, Y601);
    return y + (c - y) * s;
}

float3 lighten(float3 c, float a) { return c + (1.0 - c) * a; }
""".trimIndent()

private val SHADER_MAIN = """
half4 main(float2 p) {
    half4 base = content.eval(p);
    float3 c = clamp(look(p), 0.0, 1.0);
    c = clamp(turnHue(c, uHue, 1.0), 0.0, 1.0);
    float k = clamp(uStrength, 0.0, 1.0) * step(-1.0, uTime) * step(-1.0, uSize.x);
    return half4(half3(mix(float3(base.rgb), c, k)), base.a);
}
""".trimIndent()

/**
 * The preview's shader of [look]: run on the frame's picture, it gives the
 * picture as the look shows it (the same numbers as [lookGraph]), mixed with
 * the picture as it is by uStrength and turned by uHue (radians); uTime is the
 * time since the look started, seconds.
 */
fun lookShader(look: PictureLook): String = SHADER_HEAD + "\n\n" + lookBody(look) + "\n\n" + SHADER_MAIN

private fun lookBody(look: PictureLook): String = when (look) {
    PictureLook.BLACK_WHITE -> """
        float3 look(float2 p) {
            float y = dot(px(p), Y601);
            return float3((y - 0.5) * 1.12 + 0.5);
        }
    """

    PictureLook.OLD_FILM -> """
        float3 scratch(float3 c, float2 p, float n, float k, float w) {
            float x = floor(uSize.x * h1(n * 3.0 + k + 1.0));
            if (h1(n * 3.0 + k + 50.0) < 0.3 && p.x >= x && p.x < x + w) return mix(c, float3(0.949, 0.918, 0.827), 0.45);
            return c;
        }

        float3 look(float2 p) {
            float3 c = px(p);
            float3 s = float3(dot(c, float3(0.393, 0.769, 0.189)), dot(c, float3(0.349, 0.686, 0.168)), dot(c, float3(0.272, 0.534, 0.131)));
            float m = fract(uTime / 2.0);
            s += 0.025 * sin(6.2831853 * 12.0 * m) + 0.015 * sin(6.2831853 * 7.0 * m + 1.0);
            s += (grain(p, floor(uTime * 30.0)) - 0.5) * 16.0 / 255.0;
            float d = min(length(p - uSize * 0.5) / length(uSize * 0.5), 1.0);
            float v = cos(0.75 * d);
            s *= v * v * v * v;
            float n = floor(m * 12.0);
            float w = max(2.0, floor(uSize.x / 1280.0 + 0.5) * 2.0);
            s = scratch(s, p, n, 0.0, w);
            s = scratch(s, p, n, 1.0, w);
            s = scratch(s, p, n, 2.0, w);
            return s;
        }
    """

    PictureLook.VHS -> """
        float3 blurred(float2 p, float g) {
            float3 c = px(p) * 0.4026;
            c += (px(p + float2(g, 0.0)) + px(p - float2(g, 0.0))) * 0.2442;
            c += (px(p + float2(2.0 * g, 0.0)) + px(p - float2(2.0 * g, 0.0))) * 0.0545;
            return c;
        }

        float3 look(float2 p) {
            float sc = uSize.x / 1280.0;
            float r = max(1.0, 3.0 * sc);
            float g = max(0.5, 1.2 * sc);
            float3 c = float3(blurred(p + float2(r, 0.0), g).r, blurred(p, g).g, blurred(p - float2(r, 0.0), g).b);
            c = colourful(c, 1.35);
            c = (c - 0.5) * 1.06 + 0.5 + 0.02;
            c += (grain(p, floor(uTime * 30.0)) - 0.5) * 10.0 / 255.0;
            float lines = max(2.0, floor(uSize.y / 360.0 + 0.5));
            if (mod(floor(p.y), lines) < 1.0) c *= 0.78;
            float bh = 0.07 * uSize.y;
            float by = fract(uTime / 4.0) * (uSize.y + bh) - bh;
            if (p.y >= by && p.y < by + bh) c = lighten(c, 0.10);
            return c;
        }
    """

    PictureLook.GLITCH -> """
        float3 band(float3 c, float2 p, float n, float k, float size, float burst, float r) {
            float on = burst * step(h1(n * 7.0 + k + 20.0), 0.7);
            float bh = floor(uSize.y * size / 2.0) * 2.0;
            float y0 = (uSize.y - bh) * h1(n * 3.0 + k + 10.0);
            float dx = (h1(n * 5.0 + k + 30.0) - 0.5) * 0.24 * uSize.x;
            float2 q = float2(p.x - dx, p.y);
            if (on > 0.5 && p.y >= y0 && p.y < y0 + bh && q.x >= 0.0 && q.x < uSize.x) return shifted(q, r * burst);
            return c;
        }

        float3 look(float2 p) {
            float n = floor(fract(uTime / 2.0) * 20.0);
            float burst = step(h1(n + 1.0), 0.35);
            float r = max(1.0, floor(12.0 * uSize.x / 1280.0 + 0.5));
            float3 c = shifted(p, r * burst);
            c = band(c, p, n, 0.0, 0.06, burst, r);
            c = band(c, p, n, 1.0, 0.10, burst, r);
            c = band(c, p, n, 2.0, 0.04, burst, r);
            if (h1(n + 40.0) < 0.06) c = 1.0 - c;
            return c;
        }
    """

    PictureLook.TV_STATIC -> """
        float3 look(float2 p) {
            float3 c = colourful(px(p), 0.5);
            c = (c - 0.5) * 0.95 + 0.5;
            float m = fract(uTime / 2.0);
            float n = floor(m * 16.0);
            if (h1(n + 3.0) < 0.15) c = (c - 0.5) * 0.35 + 0.5 + 0.08;
            c += (grain(p, floor(uTime * 30.0)) - 0.5) * 55.0 / 255.0;
            float lines = max(2.0, floor(uSize.y / 240.0 + 0.5));
            if (mod(floor(p.y), lines) < 1.0) c *= 0.82;
            float bh = 0.12 * uSize.y;
            float by = m * (uSize.y + bh) - bh;
            if (p.y >= by && p.y < by + bh) c = lighten(c, 0.08);
            return c;
        }
    """

    PictureLook.PRISM -> """
        float3 look(float2 p) {
            float r = max(1.0, floor(6.0 * uSize.x / 1280.0 + 0.5));
            return colourful(shifted(p, r), 1.15);
        }
    """

    PictureLook.SHAKE -> """
        float3 look(float2 p) {
            float t = fract(uTime);
            float dx = 0.028 * uSize.x * (0.6 * sin(6.2831853 * 3.0 * t) + 0.4 * sin(6.2831853 * 7.0 * t + 1.3));
            float dy = 0.016 * uSize.y * (0.6 * sin(6.2831853 * 4.0 * t + 0.7) + 0.4 * sin(6.2831853 * 9.0 * t + 2.1));
            float a = 0.01 * sin(6.2831853 * 5.0 * t + 0.4);
            float2 l = (p / uSize - 0.5) * uSize / 1.07;
            float2 q = uSize * 0.5 + float2(dx, dy) + float2(cos(a) * l.x - sin(a) * l.y, sin(a) * l.x + cos(a) * l.y);
            return px(q);
        }
    """

    PictureLook.PULSE -> """
        float3 look(float2 p) {
            float z = 1.0 + 0.08 * exp(-7.0 * fract(uTime / 0.5));
            return px(uSize * 0.5 + (p - uSize * 0.5) / z);
        }
    """

    PictureLook.RAINBOW -> """
        float3 look(float2 p) {
            return turnHue(px(p), 6.2831853 * fract(uTime / 3.0), 1.25);
        }
    """

    PictureLook.STROBE -> """
        float3 look(float2 p) {
            float b = 0.45 * pow(max(0.0, cos(6.2831853 * fract(uTime / 0.5))), 8.0);
            return (px(p) - 0.5) * 1.08 + 0.5 + b;
        }
    """

    PictureLook.PIXELATE -> """
        float3 look(float2 p) {
            float b = max(2.0, floor(uSize.x / 90.0 + 0.5));
            return px((floor(p / b) + 0.5) * b);
        }
    """

    PictureLook.INVERT -> """
        float3 look(float2 p) {
            return 1.0 - px(p);
        }
    """

    PictureLook.MIRROR -> """
        float3 look(float2 p) {
            return px(float2(p.x < uSize.x * 0.5 ? p.x : uSize.x - p.x, p.y));
        }
    """

    PictureLook.GLOW -> """
        float3 look(float2 p) {
            float3 c = px(p);
            float g = 0.012 * uSize.x * 0.9;
            float total = 0.0;
            float blur = 0.0;
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    float w = exp(-0.405 * float(i * i + j * j));
                    blur += dot(px(p + float2(float(i), float(j)) * g), Y601) * w;
                    total += w;
                }
            }
            blur /= total;
            float y = dot(c, Y601);
            return c + 0.55 * (blur - y * blur);
        }
    """
}.trimIndent()
