package com.cuscus.ffmpeggui.core.marking

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How a picture a model changed gets its invisible mark (TrustMark Q), as
 * Adobe's own code does it (trustmark.py: encode, get_the_image_for_processing,
 * put_the_image_after_processing), and how the mark is found again:
 *
 *  - the [region] marked: the whole picture, or its centre square when one
 *    side is more than twice the other;
 *  - the region squeezed to [SIZE] x [SIZE] ([Squeezer], an average of the
 *    pixels each square covers), in -1..1; the encoder gives it back with the
 *    mark; the difference, each colour's mean taken away ([residual]), is
 *    the mark;
 *  - the mark stretched back over the region ([residualRow], bilinear as
 *    PyTorch's interpolate) and added at [STRENGTH]; for the last [feather]
 *    pixels of each edge it fades into the picture as it was ([weight]);
 *  - checking: before the picture is touched, the mark's strength
 *    ([meanMagnitude], at most [MOST_MEAN_MAGNITUDE]) and its bits on the
 *    marked square ([marked]); after, the region squeezed to the decoder's
 *    size, its bits read (Marks.pictureErrors).
 *
 * The transparency of a picture is left as it was. Pure, tested.
 */
object PictureMarking {
    const val SIZE = 256

    /** TrustMark's own default for its Q model. */
    const val STRENGTH = 1.0f

    /**
     * The strongest a mark may be on average (-1..1 units, about 10 of 255
     * levels). TrustMark's own measured 0.001 to 0.04 (a black or flat page,
     * photos about 0.01, pure noise the most); more is a wrong computation,
     * which would show as burnt colours.
     */
    const val MOST_MEAN_MAGNITUDE = 0.08f

    private const val ASPECT_LIMIT = 2.0f
    private const val FEATHER_SHARE = 0.01f
    private const val MOST_FEATHER = 50

    /** The part of a picture that carries the mark. */
    data class Region(val left: Int, val top: Int, val width: Int, val height: Int)

    fun region(width: Int, height: Int): Region {
        val aspect = maxOf(width, height).toFloat() / minOf(width, height)
        if (aspect <= ASPECT_LIMIT) return Region(0, 0, width, height)
        val side = minOf(width, height)
        return Region((width - side) / 2, (height - side) / 2, side, side)
    }

    /** How many pixels at each edge of [region] the mark fades in over. */
    fun feather(region: Region): Int = (minOf(region.width, region.height) * FEATHER_SHARE).toInt().coerceIn(1, MOST_FEATHER)

    /**
     * How much of the mark goes into the pixel ([x], [y]) of the region (its
     * own coordinates): 1 inside, less near an edge. As TrustMark's
     * feather_paste: its column fade wins where both apply.
     */
    fun weight(x: Int, y: Int, region: Region, feather: Int): Float {
        val f = minOf(feather, region.width, region.height)
        fun ramp(i: Int): Float = (i + 1).toFloat() / feather
        return when {
            x < f -> ramp(x)
            x >= region.width - f -> ramp(region.width - 1 - x)
            y < f -> ramp(y)
            y >= region.height - f -> ramp(region.height - 1 - y)
            else -> 1f
        }
    }

    /**
     * The mark: [stego] (the encoder's picture) less [cover] (what it was
     * given), both [3, SIZE, SIZE] in -1..1, the stego clamped to that range,
     * each colour's mean taken away (no colour shift).
     */
    fun residual(stego: FloatArray, cover: FloatArray): FloatArray {
        val plane = SIZE * SIZE
        require(stego.size == 3 * plane && cover.size == 3 * plane)
        val out = FloatArray(3 * plane)
        for (c in 0 until 3) {
            var sum = 0.0
            for (i in 0 until plane) {
                val k = c * plane + i
                val v = stego[k].coerceIn(-1f, 1f) - cover[k]
                out[k] = v
                sum += v
            }
            val mean = (sum / plane).toFloat()
            for (i in 0 until plane) out[c * plane + i] -= mean
        }
        return out
    }

    /** The mark's mean absolute value over its three colours. */
    fun meanMagnitude(residual: FloatArray): Float {
        var sum = 0.0
        for (v in residual) sum += kotlin.math.abs(v)
        return if (residual.isEmpty()) 0f else (sum / residual.size).toFloat()
    }

    /** The [SIZE] square [cover] with the mark [residual] added at [STRENGTH], in -1..1: what the decoder should read. */
    fun marked(cover: FloatArray, residual: FloatArray): FloatArray {
        require(cover.size == residual.size)
        return FloatArray(cover.size) { i -> (cover[i] + residual[i] * STRENGTH).coerceIn(-1f, 1f) }
    }

    /**
     * Row [y] of the mark stretched over a region [width] x [height] (as
     * PyTorch's bilinear interpolate, align_corners false), as R, G, B
     * triplets into [out] (3 x [width]).
     */
    fun residualRow(residual: FloatArray, y: Int, width: Int, height: Int, out: FloatArray) {
        val plane = SIZE * SIZE
        val (y0, y1, wy) = source(y, height)
        for (x in 0 until width) {
            val (x0, x1, wx) = source(x, width)
            for (c in 0 until 3) {
                val base = c * plane
                val top = residual[base + y0 * SIZE + x0] * (1 - wx) + residual[base + y0 * SIZE + x1] * wx
                val bottom = residual[base + y1 * SIZE + x0] * (1 - wx) + residual[base + y1 * SIZE + x1] * wx
                out[x * 3 + c] = top * (1 - wy) + bottom * wy
            }
        }
    }

    private data class Source(val first: Int, val second: Int, val weight: Float)

    private fun source(i: Int, length: Int): Source {
        val at = ((i + 0.5f) * SIZE / length - 0.5f).coerceAtLeast(0f)
        val first = floor(at).toInt().coerceAtMost(SIZE - 1)
        val second = minOf(first + 1, SIZE - 1)
        return Source(first, second, at - first)
    }

    /** A colour value (0..255) with [mark] (-1..1 units) added at [weight], back in 0..255. */
    fun apply(value: Int, mark: Float, weight: Float): Int {
        val marked = ((value / 127.5f - 1f + mark * STRENGTH).coerceIn(-1f, 1f) * 127.5f + 127.5f)
        return (marked * weight + value * (1f - weight)).roundToInt().coerceIn(0, 255)
    }

    /**
     * A region of [width] x [height] squeezed to [size] x [size], fed one row
     * at a time ([add], ARGB pixels): each square of the result is the average
     * of the pixels whose centres fall in it (the nearest pixel when the
     * region is smaller than the square). [result] gives [3, size, size] in -1..1.
     */
    class Squeezer(private val width: Int, private val height: Int, private val size: Int = SIZE) {
        private val sums = DoubleArray(3 * size * size)
        private val counts = IntArray(size * size)

        fun add(y: Int, argb: IntArray, offset: Int = 0) {
            val dy = (y.toLong() * size / height).toInt()
            for (x in 0 until width) {
                val dx = (x.toLong() * size / width).toInt()
                val p = argb[offset + x]
                val cell = dy * size + dx
                sums[cell] += ((p shr 16) and 0xFF).toDouble()
                sums[size * size + cell] += ((p shr 8) and 0xFF).toDouble()
                sums[2 * size * size + cell] += (p and 0xFF).toDouble()
                counts[cell]++
            }
        }

        fun result(): FloatArray {
            val plane = size * size
            val out = FloatArray(3 * plane)
            for (cell in 0 until plane) {
                // A square no pixel's centre fell in (a small picture): the nearest one that has some.
                val from = if (counts[cell] > 0) cell else nearestFilled(cell)
                val n = counts[from].coerceAtLeast(1)
                for (c in 0 until 3) out[c * plane + cell] = (sums[c * plane + from] / n / 127.5 - 1.0).toFloat()
            }
            return out
        }

        private fun nearestFilled(cell: Int): Int {
            val y = cell / size
            val x = cell % size
            val sy = (y.toLong() * height / size).toInt().coerceIn(0, height - 1)
            val sx = (x.toLong() * width / size).toInt().coerceIn(0, width - 1)
            return ((sy.toLong() * size / height).toInt()) * size + (sx.toLong() * size / width).toInt()
        }
    }
}
