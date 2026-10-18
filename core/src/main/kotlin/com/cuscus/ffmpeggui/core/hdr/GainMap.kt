package com.cuscus.ffmpeggui.core.hdr

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Gain maps, as Ultra HDR (and Android's Gainmap) define them: a small grey
 * picture beside the SDR one, each of its values how much brighter that part
 * may get on an HDR screen. A value v (0..1) stands for
 *
 *   stops = minStops + (maxStops - minStops) * v^(1 / gamma)
 *
 * and the HDR picture is (sdr + offsetSdr) * 2^(stops * weight) - offsetHdr,
 * the weight going from 0 to 1 as the screen's headroom goes from
 * capacityMin to capacityMax stops. Everything here is in stops (log2).
 */

/** How a gain map's values are read (see the note at the top of this file). */
data class GainMapMetadata(
    val minStops: Float,
    val maxStops: Float,
    val gamma: Float = 1f,
    val offsetSdr: Float = DEFAULT_OFFSET,
    val offsetHdr: Float = DEFAULT_OFFSET,
    val capacityMin: Float = 0f,
    val capacityMax: Float = max(maxStops, MIN_CAPACITY),
) {
    /** The stops a stored value (0..255) stands for. */
    fun stopsOf(value: Int): Float = stopsOf(value.coerceIn(0, 255) / 255f)

    /** The stops a value 0..1 stands for. */
    fun stopsOf(fraction: Float): Float {
        val v = fraction.coerceIn(0f, 1f)
        val curved = if (gamma == 1f || v == 0f) v else v.toDouble().pow(1.0 / gamma).toFloat()
        return minStops + (maxStops - minStops) * curved
    }

    /** As a line of text, to keep beside a picture (see [parse]). */
    fun encode(): String = listOf(minStops, maxStops, gamma, offsetSdr, offsetHdr, capacityMin, capacityMax)
        .joinToString(" ") { String.format(Locale.ROOT, "%.6f", it) }

    companion object {
        /** The offsets Ultra HDR writers use, so that black stays black. */
        const val DEFAULT_OFFSET = 1f / 64f

        /** A screen this many stops above SDR shows any of it. */
        const val MIN_CAPACITY = 0.05f

        /** What [encode] wrote; null when it is not that. */
        fun parse(text: String): GainMapMetadata? {
            val values = text.trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
            if (values.size != 7 || values.any { !it.isFinite() }) return null
            if (values[1] < values[0] || values[2] <= 0f || values[6] <= values[5]) return null
            return GainMapMetadata(values[0], values[1], values[2], values[3], values[4], values[5], values[6])
        }
    }
}

/** A single-channel gain map: [values] (0..255, row by row) read by [metadata]. */
class GainMap(val width: Int, val height: Int, val values: ByteArray, val metadata: GainMapMetadata) {
    init {
        require(width > 0 && height > 0 && values.size == width * height) { "One value per pixel" }
    }

    /** The stored value at [x], [y], 0..255. */
    fun valueAt(x: Int, y: Int): Int = values[y * width + x].toInt() and 0xFF
}

/**
 * Makes the gain map of a picture [width] x [height], [SCALE] times smaller
 * each way. The picture is given a few rows at a time ([add]), so that a large
 * one never has to be held whole; then [generated] works the gain map out of
 * its light, or [original] takes a photo's own.
 *
 * Where the picture has things laid over it (texts, stickers, the drawing, the
 * border: what differs from the same picture without them, see [add]) nothing
 * gets brighter: they stay as they are drawn, as the editor shows them over
 * the HDR picture.
 */
class GainMapBuilder(val width: Int, val height: Int) {

    init {
        require(width > 0 && height > 0) { "Empty picture" }
    }

    val mapWidth: Int = (width + SCALE - 1) / SCALE
    val mapHeight: Int = (height + SCALE - 1) / SCALE

    private val luma = FloatArray(mapWidth * mapHeight)
    private val covered = FloatArray(mapWidth * mapHeight)
    private val counts = FloatArray(mapWidth * mapHeight)

    /**
     * Rows [top] .. [top] + [rows] of the picture: [picture] their pixels
     * (0xAARRGGBB, [width] a row); [plain] the same rows without what is laid
     * over the picture, null when nothing is.
     */
    fun add(top: Int, rows: Int, picture: IntArray, plain: IntArray? = null) {
        require(top >= 0 && rows >= 0 && top + rows <= height) { "Rows out of the picture" }
        require(picture.size >= rows * width && (plain == null || plain.size >= rows * width)) { "Missing pixels" }
        for (r in 0 until rows) {
            val my = (top + r) / SCALE
            val row = r * width
            for (x in 0 until width) {
                val i = my * mapWidth + x / SCALE
                val p = picture[row + x]
                luma[i] += lumaOf(p)
                if (plain != null && (plain[row + x] and 0xFFFFFF) != (p and 0xFFFFFF)) covered[i] += 1f
                counts[i] += 1f
            }
        }
    }

    /**
     * The gain map worked out of the picture's light, for [intensity] 0..1
     * (see [HdrIntensity]): the brightest parts, and those lighter than what is
     * around them, the most; dark tones stay. [area] (0..1, [mapWidth] x
     * [mapHeight]) says where, null for everywhere. Null when nothing gets
     * brighter.
     */
    fun generated(intensity: Float, area: FloatArray? = null): GainMap? {
        val peak = HdrIntensity.peakStops(intensity)
        if (peak <= 0f) return null
        val spread = HdrIntensity.spread(intensity)
        val n = luma.size
        require(area == null || area.size == n) { "One area value per gain map pixel" }
        val l = FloatArray(n) { if (counts[it] > 0f) luma[it] / counts[it] else 0f }
        val around = boxBlur(l, mapWidth, mapHeight, max(1, (max(mapWidth, mapHeight) / LOCAL_SHARE).roundToInt()))
        // More of the picture is lit as the intensity passes its middle: lighter tones start lower.
        val from = LIGHT_FROM - (LIGHT_FROM - LIGHT_FROM_SPREAD) * spread
        val lift = FloatArray(n) { i ->
            val light = smoothstep(from, LIGHT_TO, l[i])
            val standsOut = smoothstep(0.02f, 0.25f, l[i] - around[i]) * smoothstep(0.3f, 0.7f, l[i])
            (LIGHT_WEIGHT * light + STAND_OUT_WEIGHT * standsOut).coerceIn(0f, 1f)
        }
        // A little softer, so that the gain map has no grain of its own.
        val soft = boxBlur(lift, mapWidth, mapHeight, 1)
        val gains = FloatArray(n) { i -> peak * HdrIntensity.raise(soft[i], spread) * (area?.get(i) ?: 1f) * free(i) }
        return encode(gains)
    }

    /**
     * A photo's own gain map, already brought onto this picture ([stops], one
     * per gain map pixel, NaN where the photo is not), at [strength]: none at
     * 0, as shot at 1; above 1 its brightest parts stay as bright (no screen
     * would show more, see [HdrIntensity]) and the rest comes nearer to them.
     * [area] as for [generated]. Null when nothing changes.
     */
    fun original(stops: FloatArray, strength: Float, area: FloatArray? = null): GainMap? {
        val n = luma.size
        require(stops.size == n && (area == null || area.size == n)) { "One value per gain map pixel" }
        if (strength <= 0f) return null
        var peak = 0f
        for (s in stops) if (!s.isNaN() && s > peak) peak = s
        val raise = (strength - 1f).coerceIn(0f, 1f)
        val gains = FloatArray(n) { i ->
            val s = stops[i]
            val shaped = when {
                s.isNaN() -> 0f
                strength <= 1f -> s * strength
                s > 0f && peak > 0f -> peak * HdrIntensity.raise(s / peak, raise)
                else -> s
            }
            shaped * (area?.get(i) ?: 1f) * free(i)
        }
        return encode(gains)
    }

    /** How much of gain map pixel [i] has nothing laid over it. */
    private fun free(i: Int): Float = if (counts[i] > 0f) 1f - covered[i] / counts[i] else 1f

    private fun encode(gains: FloatArray): GainMap? {
        var low = 0f
        var high = 0f
        for (g in gains) {
            if (g < low) low = g
            if (g > high) high = g
        }
        if (high < MIN_STOPS && low > -MIN_STOPS) return null
        val metadata = GainMapMetadata(minStops = low, maxStops = high, capacityMax = max(high, GainMapMetadata.MIN_CAPACITY))
        val range = (high - low).takeIf { it > 0f } ?: 1f
        val values = ByteArray(gains.size) { ((gains[it] - low) / range * 255f).roundToInt().coerceIn(0, 255).toByte() }
        return GainMap(mapWidth, mapHeight, values, metadata)
    }

    companion object {
        /** The gain map is this many times smaller than the picture, each way. */
        const val SCALE = 2

        /** Below this many stops a gain map changes nothing that shows. */
        const val MIN_STOPS = 0.05f

        /** Where the light parts start and where they are at their brightest (luma 0..1). */
        private const val LIGHT_FROM = 0.5f
        private const val LIGHT_FROM_SPREAD = 0.25f
        private const val LIGHT_TO = 0.95f
        private const val LIGHT_WEIGHT = 0.85f
        private const val STAND_OUT_WEIGHT = 0.5f

        /** "Around" a part is this fraction of the gain map's longer side. */
        private const val LOCAL_SHARE = 48f

        /** Luma (BT.709 weights) of a pixel's encoded values, 0..1. */
        fun lumaOf(argb: Int): Float =
            (0.2126f * ((argb shr 16) and 0xFF) + 0.7152f * ((argb shr 8) and 0xFF) + 0.0722f * (argb and 0xFF)) / 255f

        internal fun smoothstep(from: Float, to: Float, x: Float): Float {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Means over (2 r + 1)^2 windows, cut at the borders. */
        internal fun boxBlur(src: FloatArray, width: Int, height: Int, r: Int): FloatArray {
            val tmp = FloatArray(src.size)
            val out = FloatArray(src.size)
            for (y in 0 until height) {
                val row = y * width
                var sum = 0.0
                for (x in 0..min(r, width - 1)) sum += src[row + x]
                for (x in 0 until width) {
                    val count = min(x + r, width - 1) - max(x - r, 0) + 1
                    tmp[row + x] = (sum / count).toFloat()
                    if (x + r + 1 < width) sum += src[row + x + r + 1]
                    if (x - r >= 0) sum -= src[row + x - r]
                }
            }
            for (x in 0 until width) {
                var sum = 0.0
                for (y in 0..min(r, height - 1)) sum += tmp[y * width + x]
                for (y in 0 until height) {
                    val count = min(y + r, height - 1) - max(y - r, 0) + 1
                    out[y * width + x] = (sum / count).toFloat()
                    if (y + r + 1 < height) sum += tmp[(y + r + 1) * width + x]
                    if (y - r >= 0) sum -= tmp[(y - r) * width + x]
                }
            }
            return out
        }
    }
}

/**
 * What the intensity of an HDR look does. An HDR screen never shows more than
 * its headroom: a gain map asking for more is scaled down as a whole (the
 * weight of Ultra HDR), so past a point a stronger look would only look the
 * same. So the intensity first raises the peak, up to [PEAK_STOPS] (about what
 * phone screens show, 5 to 6 times SDR white), at its middle; past it, the
 * peak stays and more of the picture comes near it ([spread]).
 */
object HdrIntensity {
    /** The most a generated gain map brightens, in stops. */
    const val PEAK_STOPS = 2.5f

    /** The intensity at which the peak is reached. */
    const val MIDDLE = 0.5f

    /** The peak, in stops, at [intensity] 0..1. */
    fun peakStops(intensity: Float): Float = PEAK_STOPS * (intensity.coerceIn(0f, 1f) / MIDDLE).coerceAtMost(1f)

    /** How far past the middle [intensity] is, 0..1. */
    fun spread(intensity: Float): Float = ((intensity.coerceIn(0f, 1f) - MIDDLE) / (1f - MIDDLE)).coerceIn(0f, 1f)

    /** [share] 0..1 brought nearer to 1 by [spread] 0..1 (none at 0, much at 1); 0 and 1 stay. */
    fun raise(share: Float, spread: Float): Float {
        val x = share.coerceIn(0f, 1f)
        if (spread <= 0f || x == 0f) return x
        return x.toDouble().pow(1.0 / (1.0 + RAISE * spread)).toFloat()
    }

    private const val RAISE = 2.0
}
