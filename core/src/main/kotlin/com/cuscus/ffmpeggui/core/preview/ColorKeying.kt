package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.SpillScreen
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A [ColorKey] on a picture, as the export's filters do it (see `keyFilters`
 * in the command package), for the editors' previews:
 *
 *  - CHROMA, like `chromakey` on yuva444p: every pixel's chroma (U, V of BT.601
 *    in TV range, what FFmpeg converts the picture to) is compared with the
 *    key colour's (whose U, V FFmpeg works out with the full-range formula); the
 *    distances of the 3 x 3 pixels around it are averaged, out-of-picture
 *    neighbours keeping the last value seen in their place, as FFmpeg's loop does;
 *  - COLOR, like `colorkey` on rgba: the RGB distance of the pixel itself;
 *  - then the distance becomes the opacity: 0 below the similarity, rising
 *    over the blend (or a hard edge without one), truncated to 0..255;
 *  - `despill` (mix 0.5, the screen's channel scaled by -spill) takes the
 *    screen's tint out of the colour;
 *  - with [apply]'s keepAlpha the picture's own transparency stays: the two
 *    opacities are multiplied, as the export's `blend=all_mode=multiply` of
 *    the two alpha planes does (see `keyFilters` with keepAlpha);
 *  - what is cut is transparent, or shows the key's background: opaque, or
 *    see-through as FFmpeg's `overlay` lays the keyed picture over it (packed
 *    RGB, straight alpha, the main picture's alpha un-premultiplied in).
 *
 * The export goes through YUV and back, so colours may differ by a unit or two.
 */
object ColorKeying {

    /**
     * [pixels] (0xAARRGGBB, [width] x [height]) keyed by [key] into [out]
     * (which may be [pixels] itself): with transparency where the key has no
     * background, else laid over it (opaque, or at its opacity). With [keepAlpha] the pixels' own
     * transparency is kept (multiplied in); without, they count as opaque, as
     * when the export has dropped it before the key. Returns [out].
     */
    fun apply(
        pixels: IntArray,
        width: Int,
        height: Int,
        key: ColorKey,
        out: IntArray = IntArray(pixels.size),
        keepAlpha: Boolean = true,
        /** How opaque the key leaves each pixel, when known already (a subject's mask); else worked out. */
        cut: IntArray? = null,
    ): IntArray {
        val count = width * height
        require(pixels.size >= count && out.size >= count) { "Expected $count pixels" }
        val alphas = cut?.takeIf { it.size >= count } ?: alphas(pixels, width, height, key)
        val screen = key.spillScreen
        val spill = filterValue(key.spill.coerceIn(0f, 1f))
        val background = key.background
        val bgR = background?.let { (it shr 16) and 0xFF } ?: 0
        val bgG = background?.let { (it shr 8) and 0xFF } ?: 0
        val bgB = background?.let { it and 0xFF } ?: 0
        val bgA = key.backgroundAlpha
        val translucent = key.hasTranslucentBackground
        for (i in 0 until count) {
            val p = pixels[i]
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF
            if (screen != null) {
                val rf = r / 255f
                val gf = g / 255f
                val bf = b / 255f
                // Every channel goes through despill's float round trip, the screen's lowered.
                if (screen == SpillScreen.GREEN) {
                    val map = maxOf(gf - (rf * SPILL_MIX + bf * SPILL_FACTOR), 0f)
                    g = clipByte(maxOf(gf - map * spill, 0f) * 255f)
                    b = clipByte(bf * 255f)
                } else {
                    val map = maxOf(bf - (rf * SPILL_MIX + gf * SPILL_FACTOR), 0f)
                    b = clipByte(maxOf(bf - map * spill, 0f) * 255f)
                    g = clipByte(gf * 255f)
                }
                r = clipByte(rf * 255f)
            }
            // The picture's own transparency, multiplied in as FFmpeg's blend does: (a * b) / 255.
            val a = if (keepAlpha) alphas[i] * ((p ushr 24) and 0xFF) / 255 else alphas[i]
            out[i] = if (background == null) {
                (a shl 24) or (r shl 16) or (g shl 8) or b
            } else if (translucent) {
                overlayStraight(r, g, b, a, bgR, bgG, bgB, bgA)
            } else {
                val keep = a / 255f
                val nr = (r * keep + bgR * (1f - keep)).roundToInt()
                val ng = (g * keep + bgG * (1f - keep)).roundToInt()
                val nb = (b * keep + bgB * (1f - keep)).roundToInt()
                (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return out
    }

    /**
     * The pixel (r, g, b, a) laid over the main pixel (mr, mg, mb, ma) as
     * FFmpeg's `overlay` blends packed RGB with straight alpha when the main
     * picture has an alpha of its own: the overlay's alpha un-premultiplied
     * against the main's, the colours mixed with it, the alphas composited.
     */
    internal fun overlayStraight(r: Int, g: Int, b: Int, a: Int, mr: Int, mg: Int, mb: Int, ma: Int): Int {
        var alpha = a
        if (alpha != 0 && alpha != 255) alpha = unpremultiplyAlpha(alpha, ma)
        var dr = mr
        var dg = mg
        var db = mb
        var da = ma
        when (alpha) {
            0 -> Unit
            255 -> {
                dr = r
                dg = g
                db = b
            }
            else -> {
                dr = fastDiv255(dr * (255 - alpha) + r * alpha)
                dg = fastDiv255(dg * (255 - alpha) + g * alpha)
                db = fastDiv255(db * (255 - alpha) + b * alpha)
            }
        }
        when (alpha) {
            0 -> Unit
            255 -> da = a
            else -> da += fastDiv255((255 - da) * a)
        }
        return (da.coerceIn(0, 255) shl 24) or (dr.coerceIn(0, 255) shl 16) or (dg.coerceIn(0, 255) shl 8) or db.coerceIn(0, 255)
    }

    /** UNPREMULTIPLY_ALPHA of vf_overlay.c. */
    private fun unpremultiplyAlpha(x: Int, y: Int): Int =
        ((x shl 16) - (x shl 9) + x) / (((x + y) shl 8) - (x + y) - y * x)

    /** FAST_DIV255 of vf_overlay.c. */
    private fun fastDiv255(x: Int): Int = ((x + 128) * 257) shr 16

    /**
     * The key [pixels] most likely want, from [base]: the commonest colour of
     * the picture's border band (its outer eighth on every side), where a screen
     * or a flat background shows the most. A clearly green or blue colour is cut
     * by hue (a green or blue screen), anything else (white, black, a flat
     * colour) exactly; the tolerance goes back to the mode's default when the
     * mode changes. Null for an empty picture.
     */
    fun suggestedKey(pixels: IntArray, width: Int, height: Int, base: ColorKey = ColorKey()): ColorKey? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val band = maxOf(1, minOf(width, height) / 8)
        val counts = IntArray(BINS)
        val sums = LongArray(BINS * 3)
        fun add(p: Int) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            counts[bin]++
            sums[bin * 3] += r.toLong()
            sums[bin * 3 + 1] += g.toLong()
            sums[bin * 3 + 2] += b.toLong()
        }
        for (y in 0 until height) {
            val edgeRow = y < band || y >= height - band
            var x = 0
            while (x < width) {
                add(pixels[y * width + x])
                // Inside the band rows every pixel counts; elsewhere only the two side bands.
                x = if (!edgeRow && x == band - 1) maxOf(width - band, band) else x + 1
            }
        }
        var best = 0
        for (bin in 1 until BINS) if (counts[bin] > counts[best]) best = bin
        val n = counts[best].coerceAtLeast(1)
        val r = (sums[best * 3] / n).toInt()
        val g = (sums[best * 3 + 1] / n).toInt()
        val b = (sums[best * 3 + 2] / n).toInt()
        val color = (r shl 16) or (g shl 8) or b
        val spread = maxOf(r, g, b) - minOf(r, g, b)
        val screen = spread >= SCREEN_SPREAD && (g > r && g >= b || b > r && b > g)
        val mode = if (screen) KeyMode.CHROMA else KeyMode.COLOR
        return base.copy(
            mode = mode,
            color = color,
            similarity = if (mode == base.mode) base.similarity else ColorKey.defaultSimilarity(mode),
        )
    }

    /** How opaque [key] leaves each pixel of [pixels], 0..255 (the key's alpha plane). */
    fun alphas(pixels: IntArray, width: Int, height: Int, key: ColorKey): IntArray {
        val count = width * height
        val similarity = filterValue(key.effectiveSimilarity).toDouble()
        val blend = filterValue(key.effectiveBlend)
        val out = IntArray(count)
        // A subject is cut by its mask, not by colour: without the mask nothing goes.
        if (key.mode == KeyMode.SUBJECT) {
            out.fill(255)
            return out
        }
        if (key.mode == KeyMode.COLOR) {
            val kr = (key.color shr 16) and 0xFF
            val kg = (key.color shr 8) and 0xFF
            val kb = key.color and 0xFF
            // colorkey keeps 1 / blend as a float and treats 10000 and above as a hard edge.
            val inverse = 1f / blend
            for (i in 0 until count) {
                val p = pixels[i]
                val dr = (((p shr 16) and 0xFF) - kr).toDouble()
                val dg = (((p shr 8) and 0xFF) - kg).toDouble()
                val db = ((p and 0xFF) - kb).toDouble()
                val diff = sqrt((dr * dr + dg * dg + db * db) / (255.0 * 255.0 * 3.0))
                out[i] = if (inverse < 10000f) {
                    (((diff - similarity) * inverse).coerceIn(0.0, 1.0) * 255.0).toInt()
                } else {
                    if (diff > similarity) 255 else 0
                }
            }
            return out
        }
        // Each pixel's own distance to the key colour's chroma.
        val (keyU, keyV) = keyChroma(key.color)
        val distance = DoubleArray(count)
        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val du = tvChromaU(r, g, b) - keyU
            val dv = tvChromaV(r, g, b) - keyV
            distance[i] = sqrt((du * du + dv * dv) / (255.0 * 255.0 * 2.0))
        }
        // The 3 x 3 average; a neighbour outside the picture keeps the value its slot last had.
        val slots = DoubleArray(9)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0.0
                for (yo in 0 until 3) {
                    val ny = y + yo - 1
                    for (xo in 0 until 3) {
                        val nx = x + xo - 1
                        val slot = yo * 3 + xo
                        if (nx in 0 until width && ny in 0 until height) slots[slot] = distance[ny * width + nx]
                        sum += slots[slot]
                    }
                }
                val diff = sum / 9.0
                out[y * width + x] = if (blend > 0.0001f) {
                    ((diff - similarity) / blend).coerceIn(0.0, 1.0).times(255.0).toInt()
                } else {
                    if (diff > similarity) 255 else 0
                }
            }
        }
        return out
    }

    /** The key colour's U and V, as chromakey works them out (full-range coefficients, 10-bit fixed point). */
    fun keyChroma(color: Int): Pair<Int, Int> {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val u = ((-fix(0.16874) * r - fix(0.33126) * g + fix(0.50000) * b + (1 shl 9) - 1) shr 10) + 128
        val v = ((fix(0.50000) * r - fix(0.41869) * g - fix(0.08131) * b + (1 shl 9) - 1) shr 10) + 128
        return u to v
    }

    /** The value a filter option gets: the 4 decimals the command writes. */
    fun filterValue(value: Float): Float = (Math.round(value * 10000.0) / 10000.0).toFloat()

    /** U of BT.601 in TV range, as FFmpeg converts RGB pictures before chromakey. */
    private fun tvChromaU(r: Int, g: Int, b: Int): Int =
        (128.0 + (-0.148223 * r - 0.290993 * g + 0.439216 * b)).roundToInt().coerceIn(16, 240)

    private fun tvChromaV(r: Int, g: Int, b: Int): Int =
        (128.0 + (0.439216 * r - 0.367788 * g - 0.071427 * b)).roundToInt().coerceIn(16, 240)

    private fun fix(x: Double): Int = Math.rint(x * (1 shl 10)).toInt()

    private fun clipByte(v: Float): Int = v.toInt().coerceIn(0, 255)

    /** Colour bins of [suggestedKey]: 4 bits a channel, so that noise falls in one bin. */
    private const val BINS = 4096

    /** How far apart a colour's channels must be for it to count as a screen's green or blue. */
    private const val SCREEN_SPREAD = 60

    /** despill's mix, and with no expansion the factor of the other channel: (1 - mix). */
    private const val SPILL_MIX = 0.5f
    private const val SPILL_FACTOR = 0.5f
}
