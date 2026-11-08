package com.cuscus.ffmpeggui.core.textart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Colour for ANSI art: the perceptual spaces colours are compared in (OKLab,
 * CIELAB with CIEDE2000), the terminal palettes (the sixteen colours of a few
 * themes, xterm's 256) and the quantiser that matches a colour to them.
 */

/** A colour in OKLab: lightness 0..1, and two opponent axes (about -0.4..0.4). */
data class Lab(val l: Float, val a: Float, val b: Float) {
    val chroma: Float get() = hypot(a, b)

    /** The hue in degrees, 0..360. */
    val hue: Float get() = ((atan2(b, a) * 180.0 / PI).toFloat() + 360f) % 360f

    fun distance(other: Lab): Float {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return sqrt(dl * dl + da * da + db * db)
    }
}

object ArtColors {
    private val toLinear = FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun toGamma(c: Float): Int {
        val v = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f
        return (v * 255f).roundToInt().coerceIn(0, 255)
    }

    /** [rgb] (0xRRGGBB) in OKLab. */
    fun oklab(rgb: Int): Lab = oklab(toLinear[(rgb shr 16) and 0xFF], toLinear[(rgb shr 8) and 0xFF], toLinear[rgb and 0xFF])

    /** A linear-light colour in OKLab (Bjorn Ottosson's matrices). */
    fun oklab(r: Float, g: Float, b: Float): Lab {
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        return Lab(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        )
    }

    /** [lab] back in sRGB (0xRRGGBB), clipped to it. */
    fun rgb(lab: Lab): Int {
        val l = lab.l + 0.3963377774f * lab.a + 0.2158037573f * lab.b
        val m = lab.l - 0.1055613458f * lab.a - 0.0638541728f * lab.b
        val s = lab.l - 0.0894841775f * lab.a - 1.2914855480f * lab.b
        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s
        val r = 4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
        val g = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
        val b = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
        return (toGamma(r.coerceIn(0f, 1f)) shl 16) or (toGamma(g.coerceIn(0f, 1f)) shl 8) or toGamma(b.coerceIn(0f, 1f))
    }

    /** [rgb] in CIELAB (D65), L 0..100. */
    fun cielab(rgb: Int): FloatArray {
        val r = toLinear[(rgb shr 16) and 0xFF]
        val g = toLinear[(rgb shr 8) and 0xFF]
        val b = toLinear[rgb and 0xFF]
        val x = (0.4124564f * r + 0.3575761f * g + 0.1804375f * b) / 0.95047f
        val y = 0.2126729f * r + 0.7151522f * g + 0.0721750f * b
        val z = (0.0193339f * r + 0.1191920f * g + 0.9503041f * b) / 1.08883f
        fun f(t: Float) = if (t > 216f / 24389f) cbrt(t) else (24389f / 27f * t + 16f) / 116f
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    /** The CIEDE2000 difference of two CIELAB colours (Sharma, Wu and Dalal's formulation). */
    fun ciede2000(p: FloatArray, q: FloatArray): Float {
        val l1 = p[0].toDouble(); val a1 = p[1].toDouble(); val b1 = p[2].toDouble()
        val l2 = q[0].toDouble(); val a2 = q[1].toDouble(); val b2 = q[2].toDouble()
        val c1 = hypot(a1, b1)
        val c2 = hypot(a2, b2)
        val cMean = (c1 + c2) / 2
        val c7 = cMean.pow(7)
        val g = 0.5 * (1 - sqrt(c7 / (c7 + 6103515625.0)))
        val a1p = (1 + g) * a1
        val a2p = (1 + g) * a2
        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)
        fun hueOf(b: Double, a: Double): Double = if (b == 0.0 && a == 0.0) 0.0 else (Math.toDegrees(atan2(b, a)) + 360) % 360
        val h1p = hueOf(b1, a1p)
        val h2p = hueOf(b2, a2p)
        val dLp = l2 - l1
        val dCp = c2p - c1p
        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p - h1p > 180 -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val dHp = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp / 2))
        val lMean = (l1 + l2) / 2
        val cMeanP = (c1p + c2p) / 2
        val hMeanP = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(Math.toRadians(hMeanP - 30)) + 0.24 * cos(Math.toRadians(2 * hMeanP)) +
            0.32 * cos(Math.toRadians(3 * hMeanP + 6)) - 0.20 * cos(Math.toRadians(4 * hMeanP - 63))
        val dTheta = 30 * exp(-((hMeanP - 275) / 25).pow(2))
        val cMeanP7 = cMeanP.pow(7)
        val rc = 2 * sqrt(cMeanP7 / (cMeanP7 + 6103515625.0))
        val sl = 1 + 0.015 * (lMean - 50).pow(2) / sqrt(20 + (lMean - 50).pow(2))
        val sc = 1 + 0.045 * cMeanP
        val sh = 1 + 0.015 * cMeanP * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc
        val dl = dLp / sl
        val dc = dCp / sc
        val dh = dHp / sh
        return sqrt(dl * dl + dc * dc + dh * dh + rt * dc * dh).toFloat()
    }

    /** The sixteen colours of [theme] (0xRRGGBB): black, red, green, yellow, blue, magenta, cyan, white, then their bright ones. */
    fun theme(theme: TerminalTheme): IntArray = when (theme) {
        TerminalTheme.VGA -> intArrayOf(
            0x000000, 0xAA0000, 0x00AA00, 0xAA5500, 0x0000AA, 0xAA00AA, 0x00AAAA, 0xAAAAAA,
            0x555555, 0xFF5555, 0x55FF55, 0xFFFF55, 0x5555FF, 0xFF55FF, 0x55FFFF, 0xFFFFFF,
        )
        TerminalTheme.XTERM -> intArrayOf(
            0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
            0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF,
        )
        // Windows Terminal's and the console's "Campbell".
        TerminalTheme.WINDOWS -> intArrayOf(
            0x0C0C0C, 0xC50F1F, 0x13A10E, 0xC19C00, 0x0037DA, 0x881798, 0x3A96DD, 0xCCCCCC,
            0x767676, 0xE74856, 0x16C60C, 0xF9F1A5, 0x3B78FF, 0xB4009E, 0x61D6D6, 0xF2F2F2,
        )
        TerminalTheme.TANGO -> intArrayOf(
            0x2E3436, 0xCC0000, 0x4E9A06, 0xC4A000, 0x3465A4, 0x75507B, 0x06989A, 0xD3D7CF,
            0x555753, 0xEF2929, 0x8AE234, 0xFCE94F, 0x729FCF, 0xAD7FA8, 0x34E2E2, 0xEEEEEC,
        )
    }

    /** xterm's 256 colours: [theme]'s sixteen, the 6x6x6 cube, then 24 greys. */
    fun xterm256(theme: TerminalTheme): IntArray {
        val out = IntArray(256)
        theme(theme).copyInto(out)
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        for (i in 0 until 216) {
            out[16 + i] = (steps[i / 36] shl 16) or (steps[(i / 6) % 6] shl 8) or steps[i % 6]
        }
        for (i in 0 until 24) {
            val v = 8 + 10 * i
            out[232 + i] = (v shl 16) or (v shl 8) or v
        }
        return out
    }

    /** The paper of [paper] for the preview when the terminal's own background is meant. */
    fun paperColor(paper: Paper, theme: TerminalTheme): Int = when (paper) {
        Paper.DARK -> theme(theme)[0]
        Paper.LIGHT -> 0xFFFFFF
    }
}

/**
 * Matches colours to the entries of [colors] that may be used ([allowed]), by
 * [match], then paints an entry as another where [remap] says so. Results are
 * kept by colour (6 bits a channel), so a picture of many cells asks each
 * colour once.
 */
class ColorQuantizer(
    private val colors: IntArray,
    allowed: List<Int>,
    private val match: ColorMatch,
    private val remap: Map<Int, Int> = emptyMap(),
) {
    private val choices: IntArray = allowed.filter { it in colors.indices }.distinct().toIntArray().let { if (it.isEmpty()) intArrayOf(0) else it }
    private val labs = choices.map { ArtColors.oklab(colors[it]) }
    private val cielabs = if (match == ColorMatch.CIEDE2000) choices.map { ArtColors.cielab(colors[it]) } else emptyList()
    private val cache = IntArray(1 shl 18) { -1 }

    /** The entry nearest [rgb] (0xRRGGBB), after the remap. */
    fun nearest(rgb: Int): Int {
        val key = ((rgb shr 18) and 0x3F shl 12) or ((rgb shr 10) and 0x3F shl 6) or ((rgb shr 2) and 0x3F)
        val known = cache[key]
        if (known >= 0) return known
        val chosen = remapped(choices[bestOf(rgb)])
        cache[key] = chosen
        return chosen
    }

    /** The entry nearest [lab], after the remap (not cached: dithered colours vary). */
    fun nearest(lab: Lab): Int = if (match == ColorMatch.OKLAB) {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in labs.indices) {
            val d = labs[i].distance(lab)
            if (d < bestD) { bestD = d; best = i }
        }
        remapped(choices[best])
    } else {
        nearest(ArtColors.rgb(lab))
    }

    fun rgbOf(entry: Int): Int = colors[entry]

    private fun remapped(entry: Int): Int = remap[entry]?.takeIf { it in colors.indices } ?: entry

    private fun bestOf(rgb: Int): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        when (match) {
            ColorMatch.OKLAB -> {
                val lab = ArtColors.oklab(rgb)
                for (i in labs.indices) {
                    val d = labs[i].distance(lab)
                    if (d < bestD) { bestD = d; best = i }
                }
            }
            ColorMatch.CIEDE2000 -> {
                val lab = ArtColors.cielab(rgb)
                for (i in cielabs.indices) {
                    val d = ArtColors.ciede2000(lab, cielabs[i])
                    if (d < bestD) { bestD = d; best = i }
                }
            }
            ColorMatch.RGB -> {
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                for (i in choices.indices) {
                    val c = colors[choices[i]]
                    val dr = r - ((c shr 16) and 0xFF)
                    val dg = g - ((c shr 8) and 0xFF)
                    val db = b - (c and 0xFF)
                    val d = (dr * dr + dg * dg + db * db).toFloat()
                    if (d < bestD) { bestD = d; best = i }
                }
            }
        }
        return best
    }
}

/** k-means in OKLab, seeded so that the same colours always give the same result. */
object ColorClusters {
    /** At most [k] colours (0xRRGGBB) that stand for [colors], by how many they stand for (most first). */
    fun of(colors: IntArray, k: Int, weights: FloatArray? = null): IntArray {
        if (colors.isEmpty() || k <= 0) return IntArray(0)
        val step = (colors.size / MAX_SAMPLES).coerceAtLeast(1)
        val picked = (colors.indices step step).toList()
        val labs = picked.map { ArtColors.oklab(colors[it]) }
        val w = FloatArray(picked.size) { weights?.get(picked[it]) ?: 1f }
        val count = minOf(k, labs.size)
        // k-means++ with a fixed walk: the farthest point from the centres so far, in turn.
        val centres = ArrayList<Lab>(count)
        centres += labs[labs.indices.maxByOrNull { w[it] } ?: 0]
        val nearest = FloatArray(labs.size) { labs[it].distance(centres[0]) }
        while (centres.size < count) {
            var far = 0
            var farD = -1f
            for (i in labs.indices) {
                val d = nearest[i] * w[i]
                if (d > farD) { farD = d; far = i }
            }
            if (farD <= 1e-6f) break
            centres += labs[far]
            for (i in labs.indices) nearest[i] = minOf(nearest[i], labs[i].distance(labs[far]))
        }
        val assign = IntArray(labs.size)
        repeat(ITERATIONS) {
            for (i in labs.indices) {
                var best = 0
                var bestD = Float.MAX_VALUE
                for (c in centres.indices) {
                    val d = labs[i].distance(centres[c])
                    if (d < bestD) { bestD = d; best = c }
                }
                assign[i] = best
            }
            for (c in centres.indices) {
                var sl = 0f; var sa = 0f; var sb = 0f; var sw = 0f
                for (i in labs.indices) if (assign[i] == c) {
                    sl += labs[i].l * w[i]; sa += labs[i].a * w[i]; sb += labs[i].b * w[i]; sw += w[i]
                }
                if (sw > 0f) centres[c] = Lab(sl / sw, sa / sw, sb / sw)
            }
        }
        val share = FloatArray(centres.size)
        for (i in labs.indices) share[assign[i]] += w[i]
        return centres.indices.sortedByDescending { share[it] }.filter { share[it] > 0f }.map { ArtColors.rgb(centres[it]) }.toIntArray()
    }

    private const val MAX_SAMPLES = 6000
    private const val ITERATIONS = 10
}
