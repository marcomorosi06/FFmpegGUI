package com.cuscus.ffmpeggui.core.hdr

import java.util.Locale
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The HDR look of a video, as the photo editor gives one to a picture (see
 * GainMap, HdrIntensity): its light parts made brighter than SDR white, the
 * dark ones left as they are. The export makes a real HDR video of it: HLG
 * (ARIB STD-B67), BT.2020 colours, 10 bits, HEVC.
 *
 * Every pixel goes through the same curve, so the whole look is one 3D LUT
 * (FFmpeg's lut3d) from the SDR picture (BT.709) to the HLG one; the editor
 * works the same stops out (stopsAt) to show where the look goes.
 *
 * Light is relative to SDR white (1.0). SDR white lands where BT.2408 puts it
 * in HLG, at 75 % of the signal (203 cd/m2 on a 1000 cd/m2 screen).
 */
object HdrVideoLook {
    /** Points along each side of the LUT. */
    const val LUT_SIZE = 33

    /** How much brighter (in stops) a pixel of encoded BT.709 values [r], [g], [b] (0..1) gets at [intensity] 0..1. */
    fun stopsAt(r: Float, g: Float, b: Float, intensity: Float): Float {
        val peak = HdrIntensity.peakStops(intensity)
        if (peak <= 0f) return 0f
        val spread = HdrIntensity.spread(intensity)
        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val light = GainMapBuilder.smoothstep(lightFrom(intensity), LIGHT_TO, luma)
        return peak * HdrIntensity.raise(light, spread)
    }

    /** The luma (0..1) the light parts start at, at [intensity]: lower as it passes its middle. */
    fun lightFrom(intensity: Float): Float = LIGHT_FROM - (LIGHT_FROM - LIGHT_FROM_SPREAD) * HdrIntensity.spread(intensity)

    /**
     * How much brighter (in stops) a pixel of encoded BT.709 values [r], [g],
     * [b] shows on an HDR screen than on an SDR one: [stopsAt] as the HLG
     * signal holds it (its top rolled off) and as a screen shows HLG (light
     * to the power of 1.2, at 1000 cd/m2).
     */
    fun shownStops(r: Float, g: Float, b: Float, intensity: Float): Float {
        val y = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        if (y <= 1e-6) return 0f
        val gain = 2.0.pow(stopsAt(r, g, b, intensity).toDouble())
        val ratio = rolled(y * gain * WHITE_SCENE) / rolled(y * WHITE_SCENE)
        return (log2(ratio) * SYSTEM_GAMMA).toFloat().coerceAtLeast(0f)
    }

    /**
     * The gain map of a frame ([pixels], 0xAARRGGBB, [width] x [height]) that
     * shows on an HDR screen how the frame will look in the HDR video: one
     * value per pixel, from [shownStops]. Null when it changes nothing.
     */
    fun gainMap(pixels: IntArray, width: Int, height: Int, intensity: Float): GainMap? {
        require(width > 0 && height > 0 && pixels.size >= width * height) { "One pixel each" }
        val stops = FloatArray(width * height) { i ->
            val p = pixels[i]
            shownStops(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f, intensity)
        }
        var high = 0f
        for (s in stops) if (s > high) high = s
        if (high < GainMapBuilder.MIN_STOPS) return null
        val metadata = GainMapMetadata(minStops = 0f, maxStops = high, capacityMax = max(high, GainMapMetadata.MIN_CAPACITY))
        val values = ByteArray(stops.size) { (stops[it] / high * 255f + 0.5f).toInt().coerceIn(0, 255).toByte() }
        return GainMap(width, height, values, metadata)
    }

    /** The HLG signal (0..1 each, BT.2020) a pixel of encoded BT.709 values [r], [g], [b] (0..1) becomes. */
    fun hlg(r: Float, g: Float, b: Float, intensity: Float): FloatArray {
        val gain = 2.0.pow(stopsAt(r, g, b, intensity).toDouble())
        val lr = linear(r) * gain
        val lg = linear(g) * gain
        val lb = linear(b) * gain
        // BT.709 primaries in BT.2020 (ITU-R BT.2087), in linear light.
        val r2 = 0.6274 * lr + 0.3293 * lg + 0.0433 * lb
        val g2 = 0.0691 * lr + 0.9195 * lg + 0.0114 * lb
        val b2 = 0.0164 * lr + 0.0880 * lg + 0.8956 * lb
        return floatArrayOf(oetf(r2), oetf(g2), oetf(b2))
    }

    /** The LUT of [intensity] as a .cube file (red changing fastest, as the format has it). */
    fun cube(intensity: Float): String {
        val out = StringBuilder()
        out.append("TITLE \"HDR look\"\n")
        out.append("LUT_3D_SIZE ").append(LUT_SIZE).append('\n')
        val step = 1f / (LUT_SIZE - 1)
        for (bi in 0 until LUT_SIZE) {
            for (gi in 0 until LUT_SIZE) {
                for (ri in 0 until LUT_SIZE) {
                    val v = hlg(ri * step, gi * step, bi * step, intensity)
                    out.append(String.format(Locale.ROOT, "%.6f %.6f %.6f\n", v[0], v[1], v[2]))
                }
            }
        }
        return out.toString()
    }

    /** BT.1886 (gamma 2.4) from an encoded value to linear light, SDR white = 1. */
    private fun linear(v: Float): Double = v.coerceIn(0f, 1f).toDouble().pow(2.4)

    /**
     * The HLG signal of linear light [l] (SDR white = 1): scaled so that white
     * lands at 75 %, then rolled off softly above [KNEE] so that the brightest
     * parts reach the top without a hard edge.
     */
    private fun oetf(l: Double): Float {
        val e = rolled(max(0.0, l) * WHITE_SCENE)
        val signal = if (e <= 1.0 / 12.0) sqrt(3.0 * e) else HLG_A * ln(12.0 * e - HLG_B) + HLG_C
        return min(1.0, max(0.0, signal)).toFloat()
    }

    /** Scene light [e] with its top rolled off above [KNEE]: the rest of the range filled smoothly, never passed. */
    private fun rolled(e: Double): Double {
        if (e <= KNEE) return e
        val over = e - KNEE
        val room = 1.0 - KNEE
        return KNEE + room * (over / (over + room))
    }

    /** How a screen shows HLG at 1000 cd/m2: scene light to this power (BT.2100). */
    private const val SYSTEM_GAMMA = 1.2

    /** Scene light of SDR white in HLG (its signal is then 0.75). */
    private const val WHITE_SCENE = 0.2647

    /** Above this scene light the roll-off starts. */
    private const val KNEE = 0.75

    private const val HLG_A = 0.17883277
    private const val HLG_B = 0.28466892
    private const val HLG_C = 0.55991073

    /** Where the light parts start and where they are at their brightest (luma 0..1), as for pictures. */
    private const val LIGHT_FROM = 0.5f
    private const val LIGHT_FROM_SPREAD = 0.25f
    const val LIGHT_TO = 0.95f
}
