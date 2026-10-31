package com.cuscus.ffmpeggui.core.vector

import com.cuscus.ffmpeggui.core.image.Raster
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * A picture made into shapes (an SVG): tracing. The picture is brought down
 * to a few colours (k-means on the colours it has, or black and white at the
 * threshold that best splits its light, Otsu's), then every colour's area is
 * followed along the pixel edges into closed outlines (holes too: they are
 * filled even-odd), small specks are dropped, each outline is simplified
 * (Ramer-Douglas-Peucker, as tight as the detail asks) and its gentle bends
 * become curves through the middles of its sides while its sharp corners stay
 * corners. Colours are laid largest first, each one stroked with itself by
 * half a pixel so that no hairline shows between neighbours.
 *
 * With [TraceSettings.gradients], each large enough shape is also measured on
 * the picture under it: when its colours run along a direction (or out from a
 * centre) far better than one flat colour explains them, the shape is filled
 * with that linear (or radial) gradient instead, its stops the picture's own
 * colours along the way.
 *
 * It suits logos, icons, drawings, text and flat graphics; a photograph turns
 * into a poster of many shapes, softer with gradients.
 */

/** How a picture is traced. */
data class TraceSettings(
    /** Colours the picture is brought down to, 2..64 ([mono] ignores it). */
    val colors: Int = 16,
    /** 0..1: how closely the outlines follow the pixels (1 keeps every step). */
    val detail: Float = 0.6f,
    /** Gentle bends as curves; off, every outline is straight lines. */
    val smooth: Boolean = true,
    /** Specks smaller than this many pixels (of the traced size) are dropped. */
    val speckle: Int = 8,
    /** Black shapes only, where the picture is dark (a logo, text, a drawing). */
    val mono: Boolean = false,
    /** The picture is traced at most this large on its longer side. */
    val maxSide: Int = 1200,
    /** Shapes whose colours shade across them are filled with a gradient ([mono] ignores it). */
    val gradients: Boolean = false,
) {
    fun sane(): TraceSettings = copy(
        colors = colors.coerceIn(2, 64),
        detail = detail.coerceIn(0f, 1f),
        speckle = speckle.coerceIn(0, 10_000),
        maxSide = maxSide.coerceIn(64, 4096),
    )

    /** How far (pixels) a simplified outline may stray from the pixel edges. */
    val tolerance: Double get() = 2.2 - 1.4 * detail.coerceIn(0f, 1f)
}

/** One drawing step of an outline, in pixels of the traced picture. */
sealed interface PathOp {
    data class MoveTo(val x: Float, val y: Float) : PathOp
    data class LineTo(val x: Float, val y: Float) : PathOp
    data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathOp
    data object Close : PathOp
}

/**
 * A gradient a traced shape is filled with, in pixels of the traced picture:
 * a linear one runs from [ax], [ay] to [bx], [by]; a [radial] one is centred
 * on [ax], [ay] and reaches [bx], [by]. Its stops are [offsets] (0..1, rising)
 * and [colors] (ARGB).
 */
class TracedGradient(
    val radial: Boolean,
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val offsets: FloatArray,
    val colors: IntArray,
) {
    /** How far a radial gradient reaches from its centre. */
    val radius: Float get() = hypot(bx - ax, by - ay)
}

/**
 * The shapes of one colour ([argb], the palette's): their outlines, filled
 * even-odd, with that colour or, when [gradient] is set, with the gradient.
 */
class VectorLayer(val argb: Int, val ops: List<PathOp>, val pixels: Int, val gradient: TracedGradient? = null)

/**
 * A traced picture: [width] x [height] pixels (the traced size), its layers
 * bottom first.
 */
class VectorImage(val width: Int, val height: Int, val layers: List<VectorLayer>, val stroked: Boolean) {

    /** The number of outlines (a measure of how heavy the SVG is). */
    val outlines: Int get() = layers.sumOf { layer -> layer.ops.count { it is PathOp.MoveTo } }

    /** Its colours (ARGB), the one covering most of the picture first; shaded shapes count with their colour. */
    val palette: List<Int>
        get() = layers.groupBy { it.argb }.entries.sortedByDescending { e -> e.value.sumOf { it.pixels } }.map { it.key }

    /** How many shapes are filled with a gradient. */
    val gradients: Int get() = layers.count { it.gradient != null }

    /** The SVG, shown [outWidth] x [outHeight] by default (it scales to anything). */
    fun toSvg(outWidth: Int = width, outHeight: Int = height): String {
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(outWidth).append("\" height=\"").append(outHeight)
            .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n")
        // The gradients, in the traced picture's pixels, each named after its layer.
        val shaded = layers.withIndex().filter { it.value.gradient != null }
        if (shaded.isNotEmpty()) {
            out.append("<defs>\n")
            for ((i, layer) in shaded) gradient(out, gradientId(i), layer.gradient!!)
            out.append("</defs>\n")
        }
        for ((i, layer) in layers.withIndex()) {
            val rgb = if (layer.gradient != null) "url(#${gradientId(i)})" else String.format(Locale.ROOT, "#%06x", layer.argb and 0xFFFFFF)
            val alpha = if (layer.gradient != null) 255 else (layer.argb ushr 24) and 0xFF
            out.append("<path fill=\"").append(rgb).append("\" fill-rule=\"evenodd\"")
            if (alpha < 255) out.append(" fill-opacity=\"").append(number(alpha / 255f)).append('"')
            if (stroked) {
                out.append(" stroke=\"").append(rgb).append("\" stroke-width=\"0.5\" stroke-linejoin=\"round\"")
                if (alpha < 255) out.append(" stroke-opacity=\"").append(number(alpha / 255f)).append('"')
            }
            out.append(" d=\"")
            for (op in layer.ops) {
                when (op) {
                    is PathOp.MoveTo -> out.append('M').append(number(op.x)).append(' ').append(number(op.y))
                    is PathOp.LineTo -> out.append('L').append(number(op.x)).append(' ').append(number(op.y))
                    is PathOp.QuadTo -> out.append('Q').append(number(op.cx)).append(' ').append(number(op.cy)).append(' ')
                        .append(number(op.x)).append(' ').append(number(op.y))
                    PathOp.Close -> out.append('Z')
                }
            }
            out.append("\"/>\n")
        }
        out.append("</svg>\n")
        return out.toString()
    }

    /** The id of the gradient of layer [index] (the app's own prefix: unused ones are dropped when edited). */
    private fun gradientId(index: Int): String = "ffg-trace-$index"

    private fun gradient(out: StringBuilder, id: String, g: TracedGradient) {
        if (g.radial) {
            out.append("<radialGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\" cx=\"").append(number(g.ax))
                .append("\" cy=\"").append(number(g.ay)).append("\" r=\"").append(number(g.radius)).append("\">")
        } else {
            out.append("<linearGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\" x1=\"").append(number(g.ax))
                .append("\" y1=\"").append(number(g.ay)).append("\" x2=\"").append(number(g.bx)).append("\" y2=\"").append(number(g.by)).append("\">")
        }
        for (i in g.offsets.indices) {
            out.append("<stop offset=\"").append(String.format(Locale.ROOT, "%.3f", g.offsets[i]).trimEnd('0').trimEnd('.').ifEmpty { "0" })
                .append("\" stop-color=\"").append(String.format(Locale.ROOT, "#%06x", g.colors[i] and 0xFFFFFF)).append("\"/>")
        }
        out.append(if (g.radial) "</radialGradient>\n" else "</linearGradient>\n")
    }

    private fun number(v: Float): String {
        val r = (v * 100).roundToInt()
        val whole = r / 100
        val frac = abs(r % 100)
        val sign = if (r < 0 && whole == 0) "-" else ""
        return when {
            frac == 0 -> "$sign$whole"
            frac % 10 == 0 -> "$sign$whole.${frac / 10}"
            else -> "$sign$whole.${if (frac < 10) "0" else ""}$frac"
        }
    }
}

object VectorTracer {

    /** [raster] traced as [settings] say (brought to the traced size first). */
    fun trace(raster: Raster, settings: TraceSettings = TraceSettings()): VectorImage {
        val s = settings.sane()
        val picture = shrunk(raster, s.maxSide)
        val w = picture.width
        val h = picture.height
        val (labels, palette) = if (s.mono) mono(picture) else quantized(picture, s.colors)
        // Pixels of each colour; a colour that shows nowhere is left out.
        val counts = IntArray(palette.size)
        for (l in labels) if (l >= 0) counts[l]++
        val order = palette.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }
        val layers = if (s.gradients && !s.mono) {
            shadedLayers(picture, labels, palette, order, s)
        } else {
            order.mapNotNull { label -> layer(labels, w, h, label, null, palette[label], counts[label], s, null) }
        }
        return VectorImage(w, h, layers, stroked = !s.mono)
    }

    /**
     * The layer of the pixels of [region] marked [id] ([bounds]: their box,
     * minX minY maxX maxY, when known): its outlines without the specks,
     * simplified; null when nothing is left.
     */
    private fun layer(region: IntArray, w: Int, h: Int, id: Int, bounds: IntArray?, argb: Int, pixels: Int, s: TraceSettings, gradient: TracedGradient?): VectorLayer? {
        val found = if (bounds == null) outlines(region, w, h, id) else outlines(region, w, h, id, bounds[0], bounds[1], bounds[2], bounds[3])
        val outlines = found
            .filter { abs(area(it)) >= max(1, s.speckle) || s.speckle == 0 }
            .map { simplified(it, s.tolerance) }
            .filter { it.size >= 6 }
        return if (outlines.isEmpty()) null else VectorLayer(argb, outlines.flatMap { ops(it, s.smooth) }, pixels, gradient)
    }

    // -- Gradients ----------------------------------------------------------------------

    /**
     * The layers when shapes may be shaded: every colour's area split into its
     * connected shapes; each large one measured on [picture] and, when its
     * colours run as a gradient, made a layer of its own with it, just above
     * the flat rest of its colour.
     */
    private fun shadedLayers(picture: Raster, labels: IntArray, palette: IntArray, order: List<Int>, s: TraceSettings): List<VectorLayer> {
        val w = picture.width
        val h = picture.height
        val n = w * h
        val (component, labelOf) = components(labels, w, h)
        val count = labelOf.size
        // The pixels of each shape, one run after the other.
        val start = IntArray(count + 1)
        for (c in component) if (c >= 0) start[c + 1]++
        for (c in 0 until count) start[c + 1] += start[c]
        val members = IntArray(start[count])
        val fill = start.copyOf()
        for (i in 0 until n) component[i].let { c -> if (c >= 0) members[fill[c]++] = i }
        val minPixels = max(GRADIENT_MIN_PIXELS, n / GRADIENT_MIN_SHARE)
        val candidates = (0 until count).filter { start[it + 1] - start[it] >= minPixels }
            .sortedByDescending { start[it + 1] - start[it] }
            .take(MAX_GRADIENTS)
        val gradientOf = arrayOfNulls<TracedGradient>(count)
        for (c in candidates) gradientOf[c] = fitted(picture, members, start[c], start[c + 1])
        // The flat rest of each colour keeps its label; each shaded shape gets an id after the palette.
        val shadedIds = IntArray(count) { -1 }
        var next = palette.size
        for (c in 0 until count) if (gradientOf[c] != null) shadedIds[c] = next++
        val region = IntArray(n) { i -> component[i].let { c -> if (c < 0) -1 else if (shadedIds[c] >= 0) shadedIds[c] else labels[i] } }
        val bounds = Array(next) { intArrayOf(w, h, -1, -1) }
        val pixels = IntArray(next)
        for (i in 0 until n) {
            val r = region[i]
            if (r < 0) continue
            val x = i % w
            val y = i / w
            val b = bounds[r]
            if (x < b[0]) b[0] = x
            if (y < b[1]) b[1] = y
            if (x > b[2]) b[2] = x
            if (y > b[3]) b[3] = y
            pixels[r]++
        }
        val shadedOf = (0 until count).filter { shadedIds[it] >= 0 }.groupBy { labelOf[it] }
        val out = ArrayList<VectorLayer>()
        for (label in order) {
            if (pixels[label] > 0) layer(region, w, h, label, bounds[label], palette[label], pixels[label], s, null)?.let { out += it }
            for (c in shadedOf[label].orEmpty().sortedByDescending { pixels[shadedIds[it]] }) {
                val id = shadedIds[c]
                layer(region, w, h, id, bounds[id], palette[label], pixels[id], s, gradientOf[c])?.let { out += it }
            }
        }
        return out
    }

    /**
     * The connected shapes of [labels] (pixels side by side with the same label;
     * touching by a corner is apart, as the outlines keep them): each pixel's
     * shape (-1 where transparent), and each shape's label.
     */
    internal fun components(labels: IntArray, w: Int, h: Int): Pair<IntArray, IntArray> {
        val component = IntArray(labels.size) { -1 }
        val labelOf = ArrayList<Int>()
        val queue = IntArray(labels.size)
        for (seed in labels.indices) {
            if (labels[seed] < 0 || component[seed] >= 0) continue
            val id = labelOf.size
            val label = labels[seed]
            labelOf += label
            var head = 0
            var tail = 0
            component[seed] = id
            queue[tail++] = seed
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (x > 0 && component[i - 1] < 0 && labels[i - 1] == label) { component[i - 1] = id; queue[tail++] = i - 1 }
                if (x < w - 1 && component[i + 1] < 0 && labels[i + 1] == label) { component[i + 1] = id; queue[tail++] = i + 1 }
                if (y > 0 && component[i - w] < 0 && labels[i - w] == label) { component[i - w] = id; queue[tail++] = i - w }
                if (y < h - 1 && component[i + w] < 0 && labels[i + w] == label) { component[i + w] = id; queue[tail++] = i + w }
            }
        }
        return component to labelOf.toIntArray()
    }

    /**
     * The gradient that best tells the colours of the pixels members[from until to]
     * of [picture]: along the direction they change most (a least-squares plane
     * per channel), or out from a centre (a least-squares bowl), its stops their
     * mean colours along the way. Null when one flat colour tells them about as
     * well, or when they barely change.
     */
    private fun fitted(picture: Raster, members: IntArray, from: Int, to: Int): TracedGradient? {
        val w = picture.width
        val step = max(1, (to - from) / FIT_SAMPLE)
        val size = (to - from + step - 1) / step
        val xs = DoubleArray(size)
        val ys = DoubleArray(size)
        val colours = Array(3) { DoubleArray(size) }
        var mx = 0.0; var my = 0.0
        val mean = DoubleArray(3)
        for (k in 0 until size) {
            val i = members[from + k * step]
            val p = picture.pixels[i]
            xs[k] = i % w + 0.5
            ys[k] = i / w + 0.5
            colours[0][k] = ((p shr 16) and 0xFF).toDouble()
            colours[1][k] = ((p shr 8) and 0xFF).toDouble()
            colours[2][k] = (p and 0xFF).toDouble()
            mx += xs[k]; my += ys[k]
            for (c in 0..2) mean[c] += colours[c][k]
        }
        mx /= size; my /= size
        for (c in 0..2) mean[c] /= size
        for (k in 0 until size) { xs[k] -= mx; ys[k] -= my }
        var flatError = 0.0
        for (k in 0 until size) for (c in 0..2) flatError += WEIGHT[c] * sq(colours[c][k] - mean[c])
        if (flatError <= 0.0) return null

        // -- Along a direction: a plane per channel, the direction they change most together.
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0
        val sxc = DoubleArray(3); val syc = DoubleArray(3)
        for (k in 0 until size) {
            sxx += xs[k] * xs[k]; sxy += xs[k] * ys[k]; syy += ys[k] * ys[k]
            for (c in 0..2) { sxc[c] += xs[k] * (colours[c][k] - mean[c]); syc[c] += ys[k] * (colours[c][k] - mean[c]) }
        }
        // A line of pixels has no second direction to measure.
        val det = sxx * syy - sxy * sxy
        if (det <= 1e-9 * max(1.0, sxx * syy)) return null
        var m11 = 0.0; var m12 = 0.0; var m22 = 0.0
        for (c in 0..2) {
            val gx = (syy * sxc[c] - sxy * syc[c]) / det
            val gy = (sxx * syc[c] - sxy * sxc[c]) / det
            m11 += WEIGHT[c] * gx * gx; m12 += WEIGHT[c] * gx * gy; m22 += WEIGHT[c] * gy * gy
        }
        val angle = 0.5 * kotlin.math.atan2(2 * m12, m11 - m22)
        val dx = kotlin.math.cos(angle)
        val dy = kotlin.math.sin(angle)
        // Colours that change the same way all round (a disc of light) have no direction.
        val linear = if (m11 + m22 > 1e-12) ramp(DoubleArray(size) { xs[it] * dx + ys[it] * dy }, colours) else null

        // -- Out from a centre: a bowl per channel (its plane and its curve), one centre for them all.
        var radial: Ramp? = null
        var centreX = 0.0
        var centreY = 0.0
        val bowl = bowlCentre(xs, ys, colours, mean)
        if (bowl != null) {
            centreX = bowl.first
            centreY = bowl.second
            val reach = kotlin.math.sqrt((sxx + syy) / size)
            if (hypot(centreX, centreY) <= RADIAL_REACH * reach) {
                radial = ramp(DoubleArray(size) { hypot(xs[it] - centreX, ys[it] - centreY) }, colours)
            }
        }
        val useRadial = radial != null && (linear == null || radial.error < RADIAL_BETTER * linear.error)
        val best = (if (useRadial) radial else linear) ?: return null
        if (best.error > GOOD_FIT * flatError) return null
        if (distance(best.colours.first(), best.colours.last()) < MIN_SPAN) return null
        val colors = IntArray(best.colours.size) { j ->
            val c = best.colours[j]
            (0xFF shl 24) or (c[0].roundToInt().coerceIn(0, 255) shl 16) or (c[1].roundToInt().coerceIn(0, 255) shl 8) or c[2].roundToInt().coerceIn(0, 255)
        }
        return if (useRadial) {
            val ax = (mx + centreX).toFloat()
            val ay = (my + centreY).toFloat()
            val offsets = FloatArray(best.ts.size) { (best.ts[it] / best.end).toFloat().coerceIn(0f, 1f) }
            TracedGradient(true, ax, ay, ax + best.end.toFloat(), ay, offsets, colors)
        } else {
            val offsets = FloatArray(best.ts.size) { ((best.ts[it] - best.start) / (best.end - best.start)).toFloat().coerceIn(0f, 1f) }
            TracedGradient(
                false,
                (mx + dx * best.start).toFloat(), (my + dy * best.start).toFloat(),
                (mx + dx * best.end).toFloat(), (my + dy * best.end).toFloat(),
                offsets, colors,
            )
        }
    }

    /** Colours along a run of values: where the run starts and ends, its stops (value, colour) and how far the pixels stray from them. */
    private class Ramp(val start: Double, val end: Double, val ts: DoubleArray, val colours: List<DoubleArray>, val error: Double)

    /**
     * The colours as a ramp over [t] (one value per pixel): the values from the
     * 2nd to the 98th percentile cut into equal bins, each bin's mean colour a
     * stop at its middle, every pixel told by the stops either side of it.
     */
    private fun ramp(t: DoubleArray, colours: Array<DoubleArray>): Ramp? {
        val sorted = t.copyOf().also { it.sort() }
        val start = sorted[(sorted.size * 0.02).toInt().coerceIn(0, sorted.lastIndex)]
        val end = sorted[(sorted.size * 0.98).toInt().coerceIn(0, sorted.lastIndex)]
        if (end - start < MIN_RAMP_PX) return null
        val sums = Array(STOPS) { DoubleArray(4) }
        for (k in t.indices) {
            val bin = (((t[k] - start) / (end - start)) * STOPS).toInt().coerceIn(0, STOPS - 1)
            for (c in 0..2) sums[bin][c] += colours[c][k]
            sums[bin][3] += 1.0
        }
        val ts = ArrayList<Double>()
        val stops = ArrayList<DoubleArray>()
        for (j in 0 until STOPS) {
            if (sums[j][3] == 0.0) continue
            ts += start + (end - start) * (j + 0.5) / STOPS
            stops += doubleArrayOf(sums[j][0] / sums[j][3], sums[j][1] / sums[j][3], sums[j][2] / sums[j][3])
        }
        if (stops.size < 2) return null
        var error = 0.0
        val told = DoubleArray(3)
        for (k in t.indices) {
            val v = t[k]
            var j = 0
            while (j < ts.size - 1 && ts[j + 1] < v) j++
            if (v <= ts[0]) {
                for (c in 0..2) told[c] = stops[0][c]
            } else if (j >= ts.size - 1) {
                for (c in 0..2) told[c] = stops.last()[c]
            } else {
                val f = ((v - ts[j]) / (ts[j + 1] - ts[j])).coerceIn(0.0, 1.0)
                for (c in 0..2) told[c] = stops[j][c] + (stops[j + 1][c] - stops[j][c]) * f
            }
            for (c in 0..2) error += WEIGHT[c] * sq(colours[c][k] - told[c])
        }
        return Ramp(start, end, ts.toDoubleArray(), stops, error)
    }

    /**
     * The centre (from the pixels' mean) of the bowls c = a + bx x + by y + q (x^2 + y^2)
     * that best fit each channel, each channel's centre weighed by how much it
     * curves; null when none curves.
     */
    private fun bowlCentre(xs: DoubleArray, ys: DoubleArray, colours: Array<DoubleArray>, mean: DoubleArray): Pair<Double, Double>? {
        // Normal equations of [1, x, y, r2] against each channel.
        val m = Array(4) { DoubleArray(4) }
        val v = Array(3) { DoubleArray(4) }
        for (k in xs.indices) {
            val row = doubleArrayOf(1.0, xs[k], ys[k], xs[k] * xs[k] + ys[k] * ys[k])
            for (a in 0..3) {
                for (b in 0..3) m[a][b] += row[a] * row[b]
                for (c in 0..2) v[c][a] += row[a] * (colours[c][k] - mean[c])
            }
        }
        var cx = 0.0
        var cy = 0.0
        var weight = 0.0
        for (c in 0..2) {
            val solution = solve(m, v[c]) ?: return null
            val q = solution[3]
            if (abs(q) < 1e-9) continue
            val wq = WEIGHT[c] * q * q
            cx += wq * (-solution[1] / (2 * q))
            cy += wq * (-solution[2] / (2 * q))
            weight += wq
        }
        return if (weight <= 0.0) null else (cx / weight) to (cy / weight)
    }

    /** [a] x = [b] by Gaussian elimination with partial pivoting; null when singular. */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val size = b.size
        val m = Array(size) { r -> DoubleArray(size + 1) { c -> if (c < size) a[r][c] else b[r] } }
        for (col in 0 until size) {
            var pivot = col
            for (r in col + 1 until size) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < 1e-12) return null
            val swap = m[pivot]; m[pivot] = m[col]; m[col] = swap
            for (r in 0 until size) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                if (f != 0.0) for (c in col..size) m[r][c] -= f * m[col][c]
            }
        }
        return DoubleArray(size) { m[it][size] / m[it][it] }
    }

    private fun sq(v: Double) = v * v


    // -- Size -------------------------------------------------------------------------

    /** [raster] no larger than [maxSide] on its longer side (box filtered). */
    private fun shrunk(raster: Raster, maxSide: Int): Raster {
        val longer = max(raster.width, raster.height)
        if (longer <= maxSide) return raster
        val scale = maxSide.toDouble() / longer
        val w = max(1, (raster.width * scale).roundToInt())
        val h = max(1, (raster.height * scale).roundToInt())
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = y * raster.height / h
            val y1 = max(y0 + 1, (y + 1) * raster.height / h)
            for (x in 0 until w) {
                val x0 = x * raster.width / w
                val x1 = max(x0 + 1, (x + 1) * raster.width / w)
                var a = 0L; var r = 0L; var g = 0L; var b = 0L; var n = 0
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = raster.pixels[yy * raster.width + xx]
                    val pa = (p ushr 24) and 0xFF
                    a += pa
                    // Colours weighed by their opacity, so that transparent pixels do not darken edges.
                    r += ((p shr 16) and 0xFF) * pa; g += ((p shr 8) and 0xFF) * pa; b += (p and 0xFF) * pa
                    n++
                }
                val ca = (a / n).toInt()
                out[y * w + x] = if (a == 0L) 0 else (ca shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
            }
        }
        return Raster(w, h, out)
    }

    // -- Colours ----------------------------------------------------------------------

    /** Below this opacity a pixel is left out (transparent). */
    private const val OPAQUE_ENOUGH = 128

    /**
     * Each pixel's colour among at most [k] (k-means on a sample of the
     * opaque pixels, started by k-means++ with a fixed seed so that the same
     * picture always traces the same); -1 for a transparent pixel.
     */
    private fun quantized(picture: Raster, k: Int): Pair<IntArray, IntArray> {
        val px = picture.pixels
        val opaque = px.indices.filter { ((px[it] ushr 24) and 0xFF) >= OPAQUE_ENOUGH }
        if (opaque.isEmpty()) return IntArray(px.size) { -1 } to IntArray(0)
        val step = max(1, opaque.size / SAMPLE)
        val sample = opaque.filterIndexed { i, _ -> i % step == 0 }.map { px[it] }
        val distinct = sample.map { it and 0xFFFFFF }.distinct()
        val count = min(k, distinct.size)
        val random = Random(SEED)
        // k-means++: each new centre far from those already chosen.
        val centres = ArrayList<DoubleArray>()
        centres += rgb(distinct[random.nextInt(distinct.size)])
        val nearest = DoubleArray(sample.size) { Double.MAX_VALUE }
        while (centres.size < count) {
            val last = centres.last()
            var total = 0.0
            for (i in sample.indices) {
                nearest[i] = min(nearest[i], distance(rgb(sample[i]), last))
                total += nearest[i]
            }
            if (total <= 0.0) break
            var pick = random.nextDouble() * total
            var chosen = sample.lastIndex
            for (i in sample.indices) {
                pick -= nearest[i]
                if (pick <= 0) { chosen = i; break }
            }
            centres += rgb(sample[chosen])
        }
        repeat(ITERATIONS) {
            val sums = Array(centres.size) { DoubleArray(4) }
            for (p in sample) {
                val c = rgb(p)
                val j = closest(centres, c)
                sums[j][0] += c[0]; sums[j][1] += c[1]; sums[j][2] += c[2]; sums[j][3] += 1.0
            }
            for (j in centres.indices) if (sums[j][3] > 0) centres[j] = doubleArrayOf(sums[j][0] / sums[j][3], sums[j][1] / sums[j][3], sums[j][2] / sums[j][3])
        }
        val cache = HashMap<Int, Int>()
        val labels = IntArray(px.size) { i ->
            val p = px[i]
            if (((p ushr 24) and 0xFF) < OPAQUE_ENOUGH) -1 else cache.getOrPut(p and 0xFFFFFF) { closest(centres, rgb(p)) }
        }
        val palette = IntArray(centres.size) { j ->
            val c = centres[j]
            (0xFF shl 24) or (c[0].roundToInt().coerceIn(0, 255) shl 16) or (c[1].roundToInt().coerceIn(0, 255) shl 8) or c[2].roundToInt().coerceIn(0, 255)
        }
        return labels to palette
    }

    /** Black where the picture is darker than Otsu's threshold, nothing elsewhere. */
    private fun mono(picture: Raster): Pair<IntArray, IntArray> {
        val px = picture.pixels
        val histogram = IntArray(256)
        val luma = IntArray(px.size) { i ->
            val p = px[i]
            if (((p ushr 24) and 0xFF) < OPAQUE_ENOUGH) {
                -1
            } else {
                val l = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                histogram[l]++
                l
            }
        }
        val threshold = otsu(histogram)
        return IntArray(px.size) { i -> if (luma[i] in 0..threshold) 0 else -1 } to intArrayOf(0xFF000000.toInt())
    }

    /** The level that best splits [histogram] in two (Otsu). */
    internal fun otsu(histogram: IntArray): Int {
        val total = histogram.sum().toDouble()
        if (total == 0.0) return 127
        var sum = 0.0
        for (i in 0..255) sum += i * histogram[i].toDouble()
        var sumB = 0.0
        var weightB = 0.0
        var best = 0.0
        var threshold = 127
        for (t in 0..255) {
            weightB += histogram[t]
            if (weightB == 0.0) continue
            val weightF = total - weightB
            if (weightF == 0.0) break
            sumB += t * histogram[t].toDouble()
            val meanB = sumB / weightB
            val meanF = (sum - sumB) / weightF
            val between = weightB * weightF * (meanB - meanF) * (meanB - meanF)
            if (between > best) {
                best = between
                threshold = t
            }
        }
        return threshold
    }

    private fun rgb(p: Int) = doubleArrayOf(((p shr 16) and 0xFF).toDouble(), ((p shr 8) and 0xFF).toDouble(), (p and 0xFF).toDouble())

    /** Squared distance, the green weighed more as the eye does. */
    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dr = a[0] - b[0]
        val dg = a[1] - b[1]
        val db = a[2] - b[2]
        return 2 * dr * dr + 4 * dg * dg + 3 * db * db
    }

    private fun closest(centres: List<DoubleArray>, c: DoubleArray): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (j in centres.indices) {
            val d = distance(centres[j], c)
            if (d < bestD) { bestD = d; best = j }
        }
        return best
    }

    // -- Outlines ---------------------------------------------------------------------

    /**
     * The closed outlines of the pixels labelled [label], along the pixel
     * edges, as x, y pairs of the grid's corners: each pixel's sides that face
     * another colour, turned so that the colour is on their right, joined end
     * to end (turning right where two corners touch, so that diagonal
     * neighbours stay apart). Outer outlines go one way round, holes the other.
     */
    internal fun outlines(labels: IntArray, w: Int, h: Int, label: Int): List<IntArray> {
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (labels[y * w + x] == label) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return outlines(labels, w, h, label, minX, minY, maxX, maxY)
    }

    /** [outlines] of the pixels labelled [label], all within minX..maxX, minY..maxY. */
    internal fun outlines(labels: IntArray, w: Int, h: Int, label: Int, minX: Int, minY: Int, maxX: Int, maxY: Int): List<IntArray> {
        if (maxX < 0) return emptyList()
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val stride = bw + 1
        fun inside(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= bw || y >= bh) return false
            return labels[(y + minY) * w + (x + minX)] == label
        }
        // Up to two edges leave each corner of the grid.
        val first = IntArray(stride * (bh + 1)) { -1 }
        val second = IntArray(stride * (bh + 1)) { -1 }
        val from = ArrayList<Int>()
        val to = ArrayList<Int>()
        fun edge(ax: Int, ay: Int, bx: Int, by: Int) {
            val a = ay * stride + ax
            val index = from.size
            from += a
            to += by * stride + bx
            if (first[a] < 0) first[a] = index else second[a] = index
        }
        for (y in 0 until bh) for (x in 0 until bw) {
            if (!inside(x, y)) continue
            if (!inside(x, y - 1)) edge(x, y, x + 1, y)
            if (!inside(x + 1, y)) edge(x + 1, y, x + 1, y + 1)
            if (!inside(x, y + 1)) edge(x + 1, y + 1, x, y + 1)
            if (!inside(x - 1, y)) edge(x, y + 1, x, y)
        }
        val used = BooleanArray(from.size)
        val result = ArrayList<IntArray>()
        for (start in from.indices) {
            if (used[start]) continue
            val points = ArrayList<Int>()
            var e = start
            while (!used[e]) {
                used[e] = true
                val a = from[e]
                points += a % stride + minX
                points += a / stride + minY
                val b = to[e]
                val dx = b % stride - a % stride
                val dy = b / stride - a / stride
                val f = first[b]
                val s = second[b]
                e = when {
                    s < 0 -> f
                    used[f] -> s
                    used[s] -> f
                    else -> {
                        // Two ways on: the right turn, (dx, dy) -> (-dy, dx) with y down.
                        val fdx = to[f] % stride - b % stride
                        val fdy = to[f] / stride - b / stride
                        if (fdx == -dy && fdy == dx) f else s
                    }
                }
                if (e < 0) break
            }
            result += corners(points.toIntArray())
        }
        return result
    }

    /** [points] (x, y pairs of a closed outline) without the ones on a straight run. */
    private fun corners(points: IntArray): IntArray {
        val n = points.size / 2
        if (n < 3) return points
        val kept = ArrayList<Int>()
        for (i in 0 until n) {
            val p = (i + n - 1) % n
            val q = (i + 1) % n
            val ax = points[2 * i] - points[2 * p]
            val ay = points[2 * i + 1] - points[2 * p + 1]
            val bx = points[2 * q] - points[2 * i]
            val by = points[2 * q + 1] - points[2 * i + 1]
            if (ax * by - ay * bx != 0 || ax * bx + ay * by < 0) {
                kept += points[2 * i]
                kept += points[2 * i + 1]
            }
        }
        return kept.toIntArray()
    }

    /** The area an outline encloses (signed: outer outlines and holes go opposite ways). */
    internal fun area(points: IntArray): Double {
        val n = points.size / 2
        var sum = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += points[2 * i].toDouble() * points[2 * j + 1] - points[2 * j].toDouble() * points[2 * i + 1]
        }
        return sum / 2
    }

    // -- Simplifying and curves ---------------------------------------------------------

    /** A closed outline with only the points that keep it within [tolerance] of itself. */
    private fun simplified(points: IntArray, tolerance: Double): FloatArray {
        val n = points.size / 2
        if (n <= 4) return FloatArray(points.size) { points[it].toFloat() }
        // Split at the point farthest from the first, then each half on its own.
        var far = 0
        var farD = -1.0
        for (i in 1 until n) {
            val d = hypot((points[2 * i] - points[0]).toDouble(), (points[2 * i + 1] - points[1]).toDouble())
            if (d > farD) { farD = d; far = i }
        }
        val keep = BooleanArray(n + 1)
        keep[0] = true
        keep[far] = true
        keep[n] = true
        fun x(i: Int) = points[2 * (i % n)].toDouble()
        fun y(i: Int) = points[2 * (i % n) + 1].toDouble()
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack += 0 to far
        stack += far to n
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            val ax = x(a); val ay = y(a); val bx = x(b); val by = y(b)
            val len = hypot(bx - ax, by - ay)
            var worst = -1
            var worstD = 0.0
            for (i in a + 1 until b) {
                val d = if (len == 0.0) hypot(x(i) - ax, y(i) - ay) else abs((bx - ax) * (ay - y(i)) - (ax - x(i)) * (by - ay)) / len
                if (d > worstD) { worstD = d; worst = i }
            }
            if (worst >= 0 && worstD > tolerance) {
                keep[worst] = true
                stack += a to worst
                stack += worst to b
            }
        }
        val out = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) { out += x(i).toFloat(); out += y(i).toFloat() }
        return out.toFloatArray()
    }

    /**
     * The drawing steps of a closed outline: straight, or ([smooth]) curves
     * through the middles of its sides with its points as their handles,
     * except where it turns sharply, which stays a corner.
     */
    private fun ops(points: FloatArray, smooth: Boolean): List<PathOp> {
        val n = points.size / 2
        val ops = ArrayList<PathOp>(n + 2)
        if (!smooth) {
            ops += PathOp.MoveTo(points[0], points[1])
            for (i in 1 until n) ops += PathOp.LineTo(points[2 * i], points[2 * i + 1])
            ops += PathOp.Close
            return ops
        }
        fun px(i: Int) = points[2 * ((i % n + n) % n)]
        fun py(i: Int) = points[2 * ((i % n + n) % n) + 1]
        fun midX(i: Int) = (px(i) + px(i + 1)) / 2
        fun midY(i: Int) = (py(i) + py(i + 1)) / 2
        fun sharp(i: Int): Boolean {
            val ax = px(i) - px(i - 1); val ay = py(i) - py(i - 1)
            val bx = px(i + 1) - px(i); val by = py(i + 1) - py(i)
            val la = sqrt(ax * ax + ay * ay); val lb = sqrt(bx * bx + by * by)
            if (la == 0f || lb == 0f) return true
            return (ax * bx + ay * by) / (la * lb) < SHARP_COS
        }
        ops += PathOp.MoveTo(midX(n - 1), midY(n - 1))
        for (i in 0 until n) {
            if (sharp(i)) {
                ops += PathOp.LineTo(px(i), py(i))
                ops += PathOp.LineTo(midX(i), midY(i))
            } else {
                ops += PathOp.QuadTo(px(i), py(i), midX(i), midY(i))
            }
        }
        ops += PathOp.Close
        return ops
    }

    /** A turn sharper than about 70 degrees stays a corner. */
    private const val SHARP_COS = 0.34f

    /** How much each channel counts in a colour's distance (as [distance]: the green most). */
    private val WEIGHT = doubleArrayOf(2.0, 4.0, 3.0)

    /** A shape is measured for a gradient from this many pixels, and from this share of the picture. */
    private const val GRADIENT_MIN_PIXELS = 120
    private const val GRADIENT_MIN_SHARE = 4000

    /** At most this many shapes are shaded (the largest). */
    private const val MAX_GRADIENTS = 400

    /** At most this many of a shape's pixels are measured. */
    private const val FIT_SAMPLE = 30_000

    /** Stops of a traced gradient (fewer where no pixel falls). */
    private const val STOPS = 5

    /** A gradient is kept when it leaves at most this share of a flat colour's error... */
    private const val GOOD_FIT = 0.55

    /** ...and its ends are at least this far apart (about 12 levels a channel). */
    private const val MIN_SPAN = 1300.0

    /** A run shorter than this many pixels is no gradient. */
    private const val MIN_RAMP_PX = 3.0

    /** A radial gradient wins when it leaves this share of the linear one's error, or less... */
    private const val RADIAL_BETTER = 0.8

    /** ...and its centre is within this many times the shape's spread of its middle. */
    private const val RADIAL_REACH = 3.0

    private const val SAMPLE = 20_000
    private const val ITERATIONS = 10
    private const val SEED = 7
}
