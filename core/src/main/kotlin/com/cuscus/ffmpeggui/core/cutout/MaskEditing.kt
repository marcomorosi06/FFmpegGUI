package com.cuscus.ffmpeggui.core.cutout

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A soft part of a picture: how much of each pixel of a rectangle belongs to
 * it, 0..255 ([alpha], row by row, [width] x [height] from [left], [top]).
 * What a brush stroke found, kept to be put on a mask again (an undo replays
 * the strokes left) or on the same mask at another size.
 */
class MaskRegion(val left: Int, val top: Int, val width: Int, val height: Int, val alpha: ByteArray) {
    init {
        require(width >= 0 && height >= 0 && alpha.size == width * height) { "One value per pixel" }
    }

    /** The value at [x], [y] of the picture (0 outside the rectangle). */
    fun at(x: Int, y: Int): Int {
        val cx = x - left
        val cy = y - top
        if (cx < 0 || cy < 0 || cx >= width || cy >= height) return 0
        return alpha[cy * width + cx].toInt() and 0xFF
    }
}

/**
 * The plain operations of correcting a mask (0..255 per pixel, row by row) by
 * hand: a round brush along a stroke, a found part put on or taken off, the
 * parts touched by a stroke, the points deepest inside.
 */
object MaskEditing {

    /**
     * Puts [region] (found on the same picture at [width] x [height]) on
     * [mask]: kept, each pixel takes the larger of the two; removed, it keeps
     * at most what the region leaves.
     */
    fun apply(mask: IntArray, width: Int, height: Int, region: MaskRegion, keep: Boolean) {
        require(mask.size == width * height) { "One value per pixel" }
        val x0 = max(region.left, 0)
        val y0 = max(region.top, 0)
        val x1 = min(region.left + region.width, width)
        val y1 = min(region.top + region.height, height)
        for (y in y0 until y1) {
            val row = (y - region.top) * region.width - region.left
            for (x in x0 until x1) {
                val a = region.alpha[row + x].toInt() and 0xFF
                if (a == 0) continue
                val i = y * width + x
                mask[i] = if (keep) max(mask[i], a) else min(mask[i], 255 - a)
            }
        }
    }

    /**
     * As [apply], [region] having been found on the same picture at
     * [sourceWidth] x [sourceHeight]: read between its four nearest values.
     */
    fun applyScaled(mask: IntArray, width: Int, height: Int, region: MaskRegion, sourceWidth: Int, sourceHeight: Int, keep: Boolean) {
        require(mask.size == width * height) { "One value per pixel" }
        if (region.width == 0 || region.height == 0) return
        val sx = sourceWidth.toFloat() / width
        val sy = sourceHeight.toFloat() / height
        val x0 = floor(region.left / sx).toInt().coerceIn(0, width)
        val y0 = floor(region.top / sy).toInt().coerceIn(0, height)
        val x1 = ceil((region.left + region.width) / sx).toInt().coerceIn(0, width)
        val y1 = ceil((region.top + region.height) / sy).toInt().coerceIn(0, height)
        for (y in y0 until y1) {
            val fy = (y + 0.5f) * sy - 0.5f
            val ry0 = floor(fy).toInt()
            val ty = fy - ry0
            for (x in x0 until x1) {
                val fx = (x + 0.5f) * sx - 0.5f
                val rx0 = floor(fx).toInt()
                val tx = fx - rx0
                val top = region.at(rx0, ry0) * (1 - tx) + region.at(rx0 + 1, ry0) * tx
                val bottom = region.at(rx0, ry0 + 1) * (1 - tx) + region.at(rx0 + 1, ry0 + 1) * tx
                val a = (top * (1 - ty) + bottom * ty).roundToInt()
                if (a == 0) continue
                val i = y * width + x
                mask[i] = if (keep) max(mask[i], a) else min(mask[i], 255 - a)
            }
        }
    }

    /**
     * Paints a round brush of [radius] pixels along [points] (x, y pairs, in
     * order) on [mask]: what it covers becomes part of it ([keep]) or is taken
     * out of it; its edge is smoothed over a pixel.
     */
    fun paint(mask: IntArray, width: Int, height: Int, points: FloatArray, radius: Float, keep: Boolean) {
        require(mask.size == width * height) { "One value per pixel" }
        if (points.size < 2 || radius <= 0f) return
        val count = points.size / 2
        val reach = radius + 1f
        for (k in 0 until count) {
            val ax = points[2 * k]
            val ay = points[2 * k + 1]
            val bx = if (k + 1 < count) points[2 * k + 2] else ax
            val by = if (k + 1 < count) points[2 * k + 3] else ay
            if (k + 1 < count || count == 1) {
                stampSegment(mask, width, height, ax, ay, bx, by, radius, reach, keep)
            }
        }
    }

    private fun stampSegment(mask: IntArray, width: Int, height: Int, ax: Float, ay: Float, bx: Float, by: Float, radius: Float, reach: Float, keep: Boolean) {
        val x0 = floor(min(ax, bx) - reach).toInt().coerceAtLeast(0)
        val y0 = floor(min(ay, by) - reach).toInt().coerceAtLeast(0)
        val x1 = ceil(max(ax, bx) + reach).toInt().coerceAtMost(width - 1)
        val y1 = ceil(max(ay, by) + reach).toInt().coerceAtMost(height - 1)
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        for (y in y0..y1) {
            val py = y + 0.5f
            for (x in x0..x1) {
                val px = x + 0.5f
                val t = if (length2 > 0f) (((px - ax) * dx + (py - ay) * dy) / length2).coerceIn(0f, 1f) else 0f
                val d = hypot(px - (ax + t * dx), py - (ay + t * dy))
                val cover = (radius + 0.5f - d).coerceIn(0f, 1f)
                if (cover <= 0f) continue
                val a = (cover * 255f).roundToInt()
                val i = y * width + x
                mask[i] = if (keep) max(mask[i], a) else min(mask[i], 255 - a)
            }
        }
    }

    /** The pixels (indices) a stroke along [points] (x, y pairs) passes over, one per pixel step. */
    fun pathPixels(points: FloatArray, width: Int, height: Int): IntArray {
        val out = LinkedHashSet<Int>()
        fun add(x: Float, y: Float) {
            val px = x.toInt().coerceIn(0, width - 1)
            val py = y.toInt().coerceIn(0, height - 1)
            out += py * width + px
        }
        val count = points.size / 2
        for (k in 0 until count) {
            val ax = points[2 * k]
            val ay = points[2 * k + 1]
            add(ax, ay)
            if (k + 1 < count) {
                val bx = points[2 * k + 2]
                val by = points[2 * k + 3]
                val steps = ceil(hypot(bx - ax, by - ay)).toInt()
                for (s in 1..steps) add(ax + (bx - ax) * s / steps, ay + (by - ay) * s / steps)
            }
        }
        return out.toIntArray()
    }

    /** The pixels of [inside] joined (side by side) to one of [seeds] that is inside. */
    fun connected(inside: BooleanArray, width: Int, height: Int, seeds: IntArray): BooleanArray {
        require(inside.size == width * height) { "One value per pixel" }
        val out = BooleanArray(inside.size)
        val queue = IntArray(inside.size)
        var head = 0
        var tail = 0
        for (s in seeds) {
            if (s in inside.indices && inside[s] && !out[s]) {
                out[s] = true
                queue[tail++] = s
            }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            if (x > 0 && inside[i - 1] && !out[i - 1]) { out[i - 1] = true; queue[tail++] = i - 1 }
            if (x < width - 1 && inside[i + 1] && !out[i + 1]) { out[i + 1] = true; queue[tail++] = i + 1 }
            if (i >= width && inside[i - width] && !out[i - width]) { out[i - width] = true; queue[tail++] = i - width }
            if (i + width < inside.size && inside[i + width] && !out[i + width]) { out[i + width] = true; queue[tail++] = i + width }
        }
        return out
    }

    /**
     * Up to [count] points (x, y pairs) deep inside the part of [mask] that is
     * at least half in: the deepest first, each far from those before it, none
     * closer than [minDistance] to any of [avoid] (x, y pairs).
     */
    fun deepPoints(mask: IntArray, width: Int, height: Int, count: Int, avoid: FloatArray, minDistance: Float): FloatArray {
        require(mask.size == width * height) { "One value per pixel" }
        // Distance to the nearest pixel outside, by two passes (1 straight, 1.414 across).
        val far = 1e9f
        val d = FloatArray(mask.size) { if (mask[it] >= HALF) far else 0f }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (d[i] == 0f) continue
                var v = d[i]
                v = min(v, if (x > 0) d[i - 1] + 1f else 1f)
                v = min(v, if (y > 0) d[i - width] + 1f else 1f)
                v = min(v, if (x > 0 && y > 0) d[i - width - 1] + DIAGONAL else 1f)
                v = min(v, if (x < width - 1 && y > 0) d[i - width + 1] + DIAGONAL else 1f)
                d[i] = v
            }
        }
        for (y in height - 1 downTo 0) {
            for (x in width - 1 downTo 0) {
                val i = y * width + x
                if (d[i] == 0f) continue
                var v = d[i]
                v = min(v, if (x < width - 1) d[i + 1] + 1f else 1f)
                v = min(v, if (y < height - 1) d[i + width] + 1f else 1f)
                v = min(v, if (x < width - 1 && y < height - 1) d[i + width + 1] + DIAGONAL else 1f)
                v = min(v, if (x > 0 && y < height - 1) d[i + width - 1] + DIAGONAL else 1f)
                d[i] = v
            }
        }
        val chosen = ArrayList<Float>()
        val apart = ArrayList<Float>()
        repeat(count) {
            var best = -1
            var bestDepth = 0f
            for (i in d.indices) {
                val depth = d[i]
                if (depth <= bestDepth) continue
                val x = (i % width).toFloat()
                val y = (i / width).toFloat()
                if (near(x, y, avoid, minDistance)) continue
                var clash = false
                for (k in 0 until chosen.size / 2) {
                    if (hypot(x - chosen[2 * k], y - chosen[2 * k + 1]) < apart[k]) { clash = true; break }
                }
                if (clash) continue
                best = i
                bestDepth = depth
            }
            if (best < 0 || bestDepth < MIN_DEPTH) return chosen.toFloatArray()
            chosen += (best % width).toFloat()
            chosen += (best / width).toFloat()
            apart += max(bestDepth * 2f, minDistance)
        }
        return chosen.toFloatArray()
    }

    private fun near(x: Float, y: Float, points: FloatArray, distance: Float): Boolean {
        for (k in 0 until points.size / 2) {
            if (hypot(x - points[2 * k], y - points[2 * k + 1]) < distance) return true
        }
        return false
    }

    private const val HALF = 128
    private const val DIAGONAL = 1.4142135f

    /** Points less deep than this (pixels) are too near an edge to say anything. */
    private const val MIN_DEPTH = 3f
}

/**
 * Brings a part found roughly (a Segment Anything mask, four times coarser than
 * the picture) to the picture's own edges, with a guided filter (He, Sun and
 * Tang): in each small window the part is taken as a linear function of the
 * picture's colours, so its edge moves to where the colours change. It runs on
 * the picture halved (the "fast" guided filter) and only around the part.
 */
object GuidedFilter {

    /**
     * [inside] (width x height) brought to the edges of [argb] (0xAARRGGBB,
     * the same size): a soft region, null when [inside] is empty. [radius] is
     * the window's half side in pixels, [eps] how far the colours must change
     * to make an edge (the colours from 0 to 1).
     */
    fun snap(argb: IntArray, width: Int, height: Int, inside: BooleanArray, radius: Int, eps: Float): MaskRegion? {
        require(argb.size == width * height && inside.size == width * height) { "One value per pixel" }
        require(radius >= 1 && eps > 0f) { "A positive radius and eps" }
        // Around the part, with room for the window to see past its edges.
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (inside[row + x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        val margin = 3 * radius + 2
        val left = (minX - margin).coerceAtLeast(0)
        val top = (minY - margin).coerceAtLeast(0)
        val right = (maxX + margin).coerceAtMost(width - 1)
        val bottom = (maxY + margin).coerceAtMost(height - 1)
        val w = right - left + 1
        val h = bottom - top + 1

        // The picture and the part, halved (2 x 2 averages).
        val lw = (w + 1) / 2
        val lh = (h + 1) / 2
        val n = lw * lh
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        val p = FloatArray(n)
        for (ly in 0 until lh) {
            for (lx in 0 until lw) {
                var sr = 0f
                var sg = 0f
                var sb = 0f
                var sp = 0f
                var c = 0
                for (dy in 0..1) {
                    val y = top + 2 * ly + dy
                    if (y > bottom) continue
                    for (dx in 0..1) {
                        val x = left + 2 * lx + dx
                        if (x > right) continue
                        val px = argb[y * width + x]
                        sr += ((px shr 16) and 0xFF) / 255f
                        sg += ((px shr 8) and 0xFF) / 255f
                        sb += (px and 0xFF) / 255f
                        if (inside[y * width + x]) sp += 1f
                        c++
                    }
                }
                val i = ly * lw + lx
                r[i] = sr / c
                g[i] = sg / c
                b[i] = sb / c
                p[i] = sp / c
            }
        }
        val lr = max(1, radius / 2)
        val box = BoxMean(lw, lh, lr)
        val mr = box.of(r)
        val mg = box.of(g)
        val mb = box.of(b)
        val mp = box.of(p)
        val crp = box.of(FloatArray(n) { r[it] * p[it] })
        val cgp = box.of(FloatArray(n) { g[it] * p[it] })
        val cbp = box.of(FloatArray(n) { b[it] * p[it] })
        val vrr = box.of(FloatArray(n) { r[it] * r[it] })
        val vrg = box.of(FloatArray(n) { r[it] * g[it] })
        val vrb = box.of(FloatArray(n) { r[it] * b[it] })
        val vgg = box.of(FloatArray(n) { g[it] * g[it] })
        val vgb = box.of(FloatArray(n) { g[it] * b[it] })
        val vbb = box.of(FloatArray(n) { b[it] * b[it] })
        // a = (covariance of the colours + eps)^-1 * covariance of colours and part; b = mean part - a . mean colour.
        val ar = r
        val ag = g
        val ab = b
        val bb = p
        for (i in 0 until n) {
            val covR = crp[i] - mr[i] * mp[i]
            val covG = cgp[i] - mg[i] * mp[i]
            val covB = cbp[i] - mb[i] * mp[i]
            val sRR = vrr[i] - mr[i] * mr[i] + eps
            val sRG = vrg[i] - mr[i] * mg[i]
            val sRB = vrb[i] - mr[i] * mb[i]
            val sGG = vgg[i] - mg[i] * mg[i] + eps
            val sGB = vgb[i] - mg[i] * mb[i]
            val sBB = vbb[i] - mb[i] * mb[i] + eps
            val c00 = sGG * sBB - sGB * sGB
            val c01 = sGB * sRB - sRG * sBB
            val c02 = sRG * sGB - sGG * sRB
            val c11 = sRR * sBB - sRB * sRB
            val c12 = sRB * sRG - sRR * sGB
            val c22 = sRR * sGG - sRG * sRG
            val det = sRR * c00 + sRG * c01 + sRB * c02
            if (det <= 1e-12f) {
                ar[i] = 0f
                ag[i] = 0f
                ab[i] = 0f
                bb[i] = mp[i]
                continue
            }
            val xr = (c00 * covR + c01 * covG + c02 * covB) / det
            val xg = (c01 * covR + c11 * covG + c12 * covB) / det
            val xb = (c02 * covR + c12 * covG + c22 * covB) / det
            ar[i] = xr
            ag[i] = xg
            ab[i] = xb
            bb[i] = mp[i] - xr * mr[i] - xg * mg[i] - xb * mb[i]
        }
        val mar = box.of(ar)
        val mag = box.of(ag)
        val mab = box.of(ab)
        val mbb = box.of(bb)

        // Back at the full size: the averaged a and b read between their four nearest, on the picture's colours.
        val alpha = ByteArray(w * h)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / 2f - 0.5f).coerceIn(0f, (lh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, lh - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / 2f - 0.5f).coerceIn(0f, (lw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, lw - 1)
                val tx = fx - x0
                val i00 = y0 * lw + x0
                val i01 = y0 * lw + x1
                val i10 = y1 * lw + x0
                val i11 = y1 * lw + x1
                val w00 = (1 - tx) * (1 - ty)
                val w01 = tx * (1 - ty)
                val w10 = (1 - tx) * ty
                val w11 = tx * ty
                val px = argb[(top + y) * width + left + x]
                val cr = ((px shr 16) and 0xFF) / 255f
                val cg = ((px shr 8) and 0xFF) / 255f
                val cb = (px and 0xFF) / 255f
                val q = (mar[i00] * w00 + mar[i01] * w01 + mar[i10] * w10 + mar[i11] * w11) * cr +
                    (mag[i00] * w00 + mag[i01] * w01 + mag[i10] * w10 + mag[i11] * w11) * cg +
                    (mab[i00] * w00 + mab[i01] * w01 + mab[i10] * w10 + mab[i11] * w11) * cb +
                    (mbb[i00] * w00 + mbb[i01] * w01 + mbb[i10] * w10 + mbb[i11] * w11)
                // Firmer: what is mostly in is in, what is mostly out is out, a soft step between.
                val a = ((q - LOW) / (HIGH - LOW)).coerceIn(0f, 1f)
                alpha[y * w + x] = (a * 255f).roundToInt().toByte()
            }
        }
        return MaskRegion(left, top, w, h, alpha)
    }

    private const val LOW = 0.2f
    private const val HIGH = 0.8f

    /** Means over (2 r + 1)^2 windows, cut at the borders, of [width] x [height] maps. */
    private class BoxMean(private val width: Int, private val height: Int, private val r: Int) {
        private val tmp = FloatArray(width * height)
        private val counts = FloatArray(width * height).also { c ->
            for (y in 0 until height) {
                val ch = (min(y + r, height - 1) - max(y - r, 0) + 1)
                for (x in 0 until width) {
                    val cw = (min(x + r, width - 1) - max(x - r, 0) + 1)
                    c[y * width + x] = (cw * ch).toFloat()
                }
            }
        }

        fun of(src: FloatArray): FloatArray {
            val out = FloatArray(width * height)
            // Rows, then columns: running sums.
            for (y in 0 until height) {
                val row = y * width
                var sum = 0.0
                for (x in 0..min(r, width - 1)) sum += src[row + x]
                for (x in 0 until width) {
                    tmp[row + x] = sum.toFloat()
                    val add = x + r + 1
                    val drop = x - r
                    if (add < width) sum += src[row + add]
                    if (drop >= 0) sum -= src[row + drop]
                }
            }
            for (x in 0 until width) {
                var sum = 0.0
                for (y in 0..min(r, height - 1)) sum += tmp[y * width + x]
                for (y in 0 until height) {
                    val i = y * width + x
                    out[i] = sum.toFloat() / counts[i]
                    val add = y + r + 1
                    val drop = y - r
                    if (add < height) sum += tmp[add * width + x]
                    if (drop >= 0) sum -= tmp[drop * width + x]
                }
            }
            return out
        }
    }
}
