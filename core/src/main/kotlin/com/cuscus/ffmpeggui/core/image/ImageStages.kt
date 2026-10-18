package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.command.keyFilters
import com.cuscus.ffmpeggui.core.command.keyOverBackground
import com.cuscus.ffmpeggui.core.command.keyOverTranslucentBackground
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.preview.ColorKeying
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A picture as the editor's preview works on it: [pixels] are 0xAARRGGBB,
 * row by row (alpha is ignored and written as opaque, except by [ImageStage.Key],
 * the last step, which leaves what it cuts transparent).
 */
class Raster(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "Raster of $width x $height needs ${width * height} pixels" }
    }

    fun copy(): Raster = Raster(width, height, pixels.copyOf())
}

/**
 * One step of an image edit, described once and turned into both the FFmpeg
 * filter of the export ([filter]) and its port for the preview ([apply]).
 * Steps that work on single colours (the tone and colour tables) are exact
 * ports; the others work in pixels of the raster they get, so they look the
 * same at the preview's smaller size.
 */
sealed class ImageStage {
    abstract val filter: String

    abstract fun apply(input: Raster): Raster

    /** A step that turns, mirrors, bends or cuts the picture, rather than changing its colours. */
    val isGeometry: Boolean
        get() = this is QuarterTurn || this is Mirror || this is Perspective || this is Straighten || this is Crop

    // -- Geometry ----------------------------------------------------------------------

    /** A quarter turn clockwise ([clockwise]) or anticlockwise: transpose. */
    data class QuarterTurn(val clockwise: Boolean) : ImageStage() {
        override val filter: String get() = if (clockwise) "transpose=1" else "transpose=2"

        override fun apply(input: Raster): Raster {
            val w = input.width
            val h = input.height
            val out = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val p = input.pixels[y * w + x]
                // Output is h wide and w tall.
                val ox = if (clockwise) h - 1 - y else y
                val oy = if (clockwise) x else w - 1 - x
                out[oy * h + ox] = p
            }
            return Raster(h, w, out)
        }
    }

    data class Mirror(val horizontal: Boolean) : ImageStage() {
        override val filter: String get() = if (horizontal) "hflip" else "vflip"

        override fun apply(input: Raster): Raster {
            val w = input.width
            val h = input.height
            val out = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val sx = if (horizontal) w - 1 - x else x
                val sy = if (horizontal) y else h - 1 - y
                out[y * w + x] = input.pixels[sy * w + sx]
            }
            return Raster(w, h, out)
        }
    }

    /**
     * Keystone correction: the four source corners ([corners], as fractions of the
     * frame: top left, top right, bottom left, bottom right, x then y) are pulled
     * to the corners of the frame (perspective, sense=source).
     */
    data class Perspective(val corners: List<Double>) : ImageStage() {
        override val filter: String
            get() {
                val c = corners
                fun x(i: Int) = "'W*${num(c[2 * i])}'"
                fun y(i: Int) = "'H*${num(c[2 * i + 1])}'"
                return "perspective=x0=${x(0)}:y0=${y(0)}:x1=${x(1)}:y1=${y(1)}:x2=${x(2)}:y2=${y(2)}" +
                    ":x3=${x(3)}:y3=${y(3)}:interpolation=linear:sense=source"
            }

        override fun apply(input: Raster): Raster {
            val w = input.width
            val h = input.height
            // The filter's projective mapping (sense=source), from the frame to the source.
            val ref = Array(4) { i -> doubleArrayOf(corners[2 * i] * w, corners[2 * i + 1] * h) }
            val x6 = ((ref[0][0] - ref[1][0] - ref[2][0] + ref[3][0]) * (ref[2][1] - ref[3][1]) -
                (ref[0][1] - ref[1][1] - ref[2][1] + ref[3][1]) * (ref[2][0] - ref[3][0])) * h
            val x7 = ((ref[0][1] - ref[1][1] - ref[2][1] + ref[3][1]) * (ref[1][0] - ref[3][0]) -
                (ref[0][0] - ref[1][0] - ref[2][0] + ref[3][0]) * (ref[1][1] - ref[3][1])) * w
            val q = (ref[1][0] - ref[3][0]) * (ref[2][1] - ref[3][1]) - (ref[2][0] - ref[3][0]) * (ref[1][1] - ref[3][1])
            val x0 = q * (ref[1][0] - ref[0][0]) * h + x6 * ref[1][0]
            val x1 = q * (ref[2][0] - ref[0][0]) * w + x7 * ref[2][0]
            val x2 = q * ref[0][0] * w * h
            val x3 = q * (ref[1][1] - ref[0][1]) * h + x6 * ref[1][1]
            val x4 = q * (ref[2][1] - ref[0][1]) * w + x7 * ref[2][1]
            val x5 = q * ref[0][1] * w * h
            val x8 = q * w * h
            val out = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val d = x6 * x + x7 * y + x8
                out[y * w + x] = sample(input, (x0 * x + x1 * y + x2) / d, (x3 * x + x4 * y + x5) / d)
            }
            return Raster(w, h, out)
        }
    }

    /**
     * Turns the picture by [degrees] and keeps the largest frame of the same
     * shape that the turned picture fills: 1 / [zoom] of each side, so nothing
     * is scaled up. A port of the rotate filter (its fixed-point sine and
     * bilinear interpolation).
     */
    data class Straighten(val degrees: Double, val zoom: Double) : ImageStage() {
        private val radians: Double get() = written(degrees * PI / 180)

        override val filter: String
            get() = "rotate=a=${num(degrees * PI / 180)}:ow='iw/${num(zoom)}':oh='ih/${num(zoom)}':c=black:bilinear=1"

        /** Size of the output for an input of [width] x [height], as the filter rounds it. */
        fun outputSize(width: Int, height: Int): Pair<Int, Int> =
            max(1, (width / zoom + 0.5).toInt()) to max(1, (height / zoom + 0.5).toInt())

        override fun apply(input: Raster): Raster {
            val inw = input.width
            val inh = input.height
            val (outw, outh) = outputSize(inw, inh)
            val angle = (radians * FIXP * 16).toLong()
            val s = intSin(angle).toInt()
            val c = intSin(angle + INT_PI / 2).toInt()
            val xi = -(outw - 1) * c / 2
            val yi = (outw - 1) * s / 2
            var xprime = -(outh - 1) * s / 2
            var yprime = -(outh - 1) * c / 2
            val out = IntArray(outw * outh) { 0xFF shl 24 }
            val src = input.pixels
            for (j in 0 until outh) {
                var x = xprime + xi + FIXP * (inw - 1) / 2
                var y = yprime + yi + FIXP * (inh - 1) / 2
                for (i in 0 until outw) {
                    val x1 = x shr 16
                    val y1 = y shr 16
                    if (x1 >= -1 && x1 <= inw && y1 >= -1 && y1 <= inh) {
                        val ix = x1.coerceIn(0, inw - 1)
                        val iy = y1.coerceIn(0, inh - 1)
                        val fx = x and 0xFFFF
                        val fy = y and 0xFFFF
                        val ix1 = min(ix + 1, inw - 1)
                        val iy1 = min(iy + 1, inh - 1)
                        val p00 = src[iy * inw + ix]
                        val p01 = src[iy * inw + ix1]
                        val p10 = src[iy1 * inw + ix]
                        val p11 = src[iy1 * inw + ix1]
                        out[j * outw + i] = (0xFF shl 24) or
                            (rotateChannel(p00, p01, p10, p11, fx, fy, 16) shl 16) or
                            (rotateChannel(p00, p01, p10, p11, fx, fy, 8) shl 8) or
                            rotateChannel(p00, p01, p10, p11, fx, fy, 0)
                    }
                    x += c
                    y -= s
                }
                xprime += s
                yprime += c
            }
            return Raster(outw, outh, out)
        }
    }

    /** The part of the frame from ([x], [y]), [w] x [h], as fractions. */
    data class Crop(val x: Double, val y: Double, val w: Double, val h: Double) : ImageStage() {
        override val filter: String
            get() = "crop=w='iw*${num(w)}':h='ih*${num(h)}':x='iw*${num(x)}':y='ih*${num(y)}'"

        override fun apply(input: Raster): Raster {
            val cw = (input.width * w).toInt().coerceIn(1, input.width)
            val ch = (input.height * h).toInt().coerceIn(1, input.height)
            val cx = (input.width * x).toInt().coerceIn(0, input.width - cw)
            val cy = (input.height * y).toInt().coerceIn(0, input.height - ch)
            val out = IntArray(cw * ch)
            for (row in 0 until ch) System.arraycopy(input.pixels, (cy + row) * input.width + cx, out, row * cw, cw)
            return Raster(cw, ch, out)
        }
    }

    // -- Colour (exact) ----------------------------------------------------------------

    /** lutrgb: each channel through its own expression of `val` (0..255). */
    data class Lut(val red: Expr, val green: Expr, val blue: Expr) : ImageStage() {
        override val filter: String get() = "lutrgb=r='${red.ff()}':g='${green.ff()}':b='${blue.ff()}'"

        /** The three tables, as FFmpeg builds them: the value cut to an integer and clipped. */
        fun tables(): Array<IntArray> = arrayOf(red, green, blue).map { e ->
            IntArray(256) { v -> e.eval(v.toDouble()).toInt().coerceIn(0, 255) }
        }.toTypedArray()

        override fun apply(input: Raster): Raster {
            val (r, g, b) = tables()
            val out = IntArray(input.pixels.size)
            for (i in out.indices) {
                val p = input.pixels[i]
                out[i] = rgb(r[(p shr 16) and 0xFF], g[(p shr 8) and 0xFF], b[p and 0xFF])
            }
            return Raster(input.width, input.height, out)
        }
    }

    /**
     * colorchannelmixer: each output channel a sum of the inputs ([m] row by row, 3 x 3).
     *
     * FFmpeg takes coefficients from -2 to 2 only: a stronger matrix (a high
     * saturation, say) is written as the matrix halved as often as needed, then
     * that many mixers doubling each channel. Values clipped in between come out
     * the same (doubling keeps 0 at 0 and what was over the top over it); only
     * the rounding to whole levels in between differs, by a level or so.
     */
    data class Mixer(val m: List<Double>) : ImageStage() {
        /** The mixers the filter is made of, each within FFmpeg's range. */
        val steps: List<List<Double>>
            get() {
                val largest = m.maxOf { abs(it) }
                var doublings = 0
                while (largest / (1 shl doublings) > MAX_COEFFICIENT) doublings++
                if (doublings == 0) return listOf(m)
                val halved = m.map { written(it / (1 shl doublings)) }
                return listOf(halved) + List(doublings) { DOUBLE }
            }

        override val filter: String
            get() = steps.joinToString(",") { k ->
                "colorchannelmixer=rr=${num(k[0])}:rg=${num(k[1])}:rb=${num(k[2])}" +
                    ":gr=${num(k[3])}:gg=${num(k[4])}:gb=${num(k[5])}:br=${num(k[6])}:bg=${num(k[7])}:bb=${num(k[8])}"
            }

        override fun apply(input: Raster): Raster = steps.fold(input) { raster, k -> mix(raster, k) }

        private fun mix(input: Raster, k: List<Double>): Raster {
            // The filter's tables: every product rounded to the nearest integer (lrint).
            val lut = Array(9) { j -> IntArray(256) { i -> Math.rint(i * k[j]).toInt() } }
            val out = IntArray(input.pixels.size)
            for (i in out.indices) {
                val p = input.pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[i] = rgb(
                    (lut[0][r] + lut[1][g] + lut[2][b]).coerceIn(0, 255),
                    (lut[3][r] + lut[4][g] + lut[5][b]).coerceIn(0, 255),
                    (lut[6][r] + lut[7][g] + lut[8][b]).coerceIn(0, 255),
                )
            }
            return Raster(input.width, input.height, out)
        }

        private companion object {
            /** The largest coefficient colorchannelmixer takes (either sign). */
            const val MAX_COEFFICIENT = 2.0
            val DOUBLE = listOf(2.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 2.0)
        }
    }

    // -- Effects (in pixels of the raster) ---------------------------------------------

    /** boxblur, [radius] pixels, applied [power] times. */
    data class BoxBlur(val radius: Int, val power: Int = 2) : ImageStage() {
        override val filter: String get() = "boxblur=lr=$radius:lp=$power"

        override fun apply(input: Raster): Raster {
            if (radius <= 0) return input
            var channels = splitChannels(input)
            repeat(power) {
                channels = channels.map { boxPass(boxPass(it, input.width, input.height, radius, true), input.width, input.height, radius, false) }
            }
            return joinChannels(channels, input.width, input.height)
        }
    }

    /** pixelize in average mode: blocks of [block] x [block] pixels. */
    data class Pixelize(val block: Int) : ImageStage() {
        override val filter: String get() = "pixelize=w=$block:h=$block:m=avg"

        override fun apply(input: Raster): Raster {
            val w = input.width
            val h = input.height
            val out = IntArray(w * h)
            var by = 0
            while (by < h) {
                var bx = 0
                val bh = min(block, h - by)
                while (bx < w) {
                    val bw = min(block, w - bx)
                    var r = 0L; var g = 0L; var b = 0L
                    for (y in by until by + bh) for (x in bx until bx + bw) {
                        val p = input.pixels[y * w + x]
                        r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                    }
                    val n = bw * bh
                    val color = rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
                    for (y in by until by + bh) for (x in bx until bx + bw) out[y * w + x] = color
                    bx += block
                }
                by += block
            }
            return Raster(w, h, out)
        }
    }

    /** rgbashift: red [shift] pixels to the right, blue as far to the left, edges smeared. */
    data class RgbShift(val shift: Int) : ImageStage() {
        override val filter: String get() = "rgbashift=rh=$shift:bh=${-shift}:edge=smear"

        override fun apply(input: Raster): Raster {
            val w = input.width
            val out = IntArray(input.pixels.size)
            for (y in 0 until input.height) for (x in 0 until w) {
                val row = y * w
                val red = (input.pixels[row + (x - shift).coerceIn(0, w - 1)] shr 16) and 0xFF
                val green = (input.pixels[row + x] shr 8) and 0xFF
                val blue = input.pixels[row + (x + shift).coerceIn(0, w - 1)] and 0xFF
                out[row + x] = rgb(red, green, blue)
            }
            return Raster(w, input.height, out)
        }
    }

    /** convolution with the same 3 x 3 [kernel] on every channel, then * [rdiv] + [bias]. */
    data class Convolution(val kernel: List<Int>, val rdiv: Double, val bias: Double = 0.0) : ImageStage() {
        override val filter: String
            get() {
                val m = kernel.joinToString(" ")
                val r = num(rdiv)
                val b = num(bias)
                return "convolution=0m='$m':1m='$m':2m='$m':0rdiv=$r:1rdiv=$r:2rdiv=$r:0bias=$b:1bias=$b:2bias=$b"
            }

        override fun apply(input: Raster): Raster {
            val w = input.width
            val h = input.height
            val out = IntArray(w * h)
            val rd = rdiv.toFloat()
            val bs = bias.toFloat()
            for (y in 0 until h) for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0
                var k = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val p = input.pixels[mirror(y + dy, h) * w + mirror(x + dx, w)]
                    val c = kernel[k++]
                    r += c * ((p shr 16) and 0xFF); g += c * ((p shr 8) and 0xFF); b += c * (p and 0xFF)
                }
                out[y * w + x] = rgb(
                    (r * rd + bs + 0.5f).toInt().coerceIn(0, 255),
                    (g * rd + bs + 0.5f).toInt().coerceIn(0, 255),
                    (b * rd + bs + 0.5f).toInt().coerceIn(0, 255),
                )
            }
            return Raster(w, h, out)
        }
    }

    /** vignette: darker towards the corners, cos^4 of [angle] times the distance. */
    data class Vignette(val angle: Double) : ImageStage() {
        override val filter: String get() = "vignette=angle=${num(angle)}"

        override fun apply(input: Raster): Raster {
            val w = input.width
            val h = input.height
            val x0 = w / 2
            val y0 = h / 2
            val dmax = hypot(w / 2.0, h / 2.0)
            val out = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val d = hypot((x - x0).toDouble(), (y - y0).toDouble()) / dmax
                val f = if (d > 1) 0.0 else cos(angle * d).let { it * it * it * it }
                val p = input.pixels[y * w + x]
                out[y * w + x] = rgb(
                    (((p shr 16) and 0xFF) * f + 0.5).toInt(),
                    (((p shr 8) and 0xFF) * f + 0.5).toInt(),
                    ((p and 0xFF) * f + 0.5).toInt(),
                )
            }
            return Raster(w, h, out)
        }
    }

    /** noise: uniform grain of [strength] (0..100). The preview draws its own grain. */
    data class Noise(val strength: Int) : ImageStage() {
        override val filter: String get() = "noise=alls=$strength:allf=u"

        override fun apply(input: Raster): Raster {
            val random = java.util.Random(GRAIN_SEED)
            val out = IntArray(input.pixels.size)
            for (i in out.indices) {
                val p = input.pixels[i]
                fun grain(c: Int) = (c + ((random.nextInt(65536) * strength) shr 16) - strength / 2).coerceIn(0, 255)
                out[i] = rgb(grain((p shr 16) and 0xFF), grain((p shr 8) and 0xFF), grain(p and 0xFF))
            }
            return Raster(input.width, input.height, out)
        }
    }

    // -- Colour key ---------------------------------------------------------------------

    /**
     * A colour cut out (see [ColorKey]): transparent where the key has no
     * background (the picture then stays rgba), over that colour at its opacity
     * where it is see-through (rgba too), else over that colour (in full
     * colour, yuv444p, then back to rgb24).
     *
     * A subject key cuts with its mask instead, which first goes through
     * [maskGeometry] (the steps that turned, mirrored and cut the picture), so
     * that it lies on the picture as it now is. The export reads the mask file;
     * the preview gets it as [maskRaster], as large as the picture it starts
     * from, its value in every colour channel.
     */
    data class Key(
        val key: ColorKey,
        val maskGeometry: List<ImageStage> = emptyList(),
        val maskRaster: Raster? = null,
    ) : ImageStage() {
        override val filter: String
            get() {
                val geometry = maskGeometry.map { it.filter }
                return when {
                    key.background == null -> keyFilters(key, "ikx", geometry) + ",format=rgba"
                    key.hasTranslucentBackground -> keyOverTranslucentBackground(key, "ik", geometry)
                    else -> keyOverBackground(key, "ik", fullColor = true, maskGeometry = geometry) + ",format=rgb24"
                }
            }

        // The export works on rgb24 here: the picture's own transparency is gone already.
        override fun apply(input: Raster): Raster {
            val cut = if (key.mode == KeyMode.SUBJECT) subjectAlphas(input.width, input.height) else null
            return Raster(input.width, input.height, ColorKeying.apply(input.pixels, input.width, input.height, key, keepAlpha = false, cut = cut))
        }

        /** How much of each pixel is the subject, 0..255: the mask shaped as the picture was, at its size. */
        private fun subjectAlphas(width: Int, height: Int): IntArray {
            val mask = maskRaster ?: return IntArray(width * height) { 255 }
            val shaped = shapedMask(mask, maskGeometry)
            val out = IntArray(width * height)
            if (shaped.width == width && shaped.height == height) {
                for (i in out.indices) out[i] = shaped.pixels[i] and 0xFF
            } else {
                // Off by a pixel of rounding (or another size): the nearest pixel of the mask.
                for (y in 0 until height) {
                    val my = (y * shaped.height / height).coerceIn(0, shaped.height - 1)
                    for (x in 0 until width) {
                        val mx = (x * shaped.width / width).coerceIn(0, shaped.width - 1)
                        out[y * width + x] = shaped.pixels[my * shaped.width + mx] and 0xFF
                    }
                }
            }
            return out
        }
    }

    private companion object {
        const val GRAIN_SEED = 31_337L

        // The mask shaped last time: the editor asks again on every move of a slider.
        private var shapedFrom: Pair<Raster, List<ImageStage>>? = null
        private var shaped: Raster? = null

        @Synchronized
        fun shapedMask(mask: Raster, geometry: List<ImageStage>): Raster {
            val from = shapedFrom
            val last = shaped
            if (last != null && from != null && from.first === mask && from.second == geometry) return last
            var raster = mask
            for (stage in geometry) raster = stage.apply(raster)
            shapedFrom = mask to geometry
            shaped = raster
            return raster
        }
    }
}

// -- Expressions of lutrgb ----------------------------------------------------------------

/**
 * A tiny expression of FFmpeg's language over `val`: evaluated here exactly as
 * FFmpeg evaluates it (in doubles, in the same order), and written fully
 * bracketed so that FFmpeg parses it the same way.
 */
sealed class Expr {
    abstract fun eval(v: Double): Double
    abstract fun ff(): String

    data object Val : Expr() {
        override fun eval(v: Double) = v
        override fun ff() = "val"
    }

    data class Num(val value: Double) : Expr() {
        override fun eval(v: Double) = value
        override fun ff() = exact(value)
    }

    data class Add(val a: Expr, val b: Expr) : Expr() {
        override fun eval(v: Double) = a.eval(v) + b.eval(v)
        override fun ff() = "(${a.ff()}+${b.ff()})"
    }

    data class Mul(val a: Expr, val b: Expr) : Expr() {
        override fun eval(v: Double) = a.eval(v) * b.eval(v)
        override fun ff() = "(${a.ff()}*${b.ff()})"
    }

    data class Clip(val a: Expr, val lo: Double, val hi: Double) : Expr() {
        override fun eval(v: Double): Double {
            val x = a.eval(v)
            return if (x < lo) lo else if (x > hi) hi else x
        }
        override fun ff() = "clip(${a.ff()},${exact(lo)},${exact(hi)})"
    }

    data class Floor(val a: Expr) : Expr() {
        override fun eval(v: Double) = floor(a.eval(v))
        override fun ff() = "floor(${a.ff()})"
    }

    /**
     * Steps run in order, each result kept in variable 0 (st / ld), the last one
     * being the value: so that a step can use the previous result several times
     * without writing it out again.
     */
    data class Steps(val steps: List<(Expr) -> Expr>, val first: Expr) : Expr() {
        override fun eval(v: Double): Double {
            var x = first.eval(v)
            for (step in steps) {
                val held = x
                x = step(Num(held)).eval(v)
            }
            return x
        }

        override fun ff(): String {
            val parts = mutableListOf("st(0,${first.ff()})")
            steps.forEach { step -> parts += "st(0,${step(Load).ff()})" }
            parts += "ld(0)"
            return "(" + parts.joinToString(";") + ")"
        }
    }

    /** The value held by [Steps] (ld(0)). */
    data object Load : Expr() {
        override fun eval(v: Double): Double = error("Load is only meaningful inside Steps")
        override fun ff() = "ld(0)"
    }
}

operator fun Expr.plus(o: Expr): Expr = Expr.Add(this, o)
operator fun Expr.plus(o: Double): Expr = if (o == 0.0) this else Expr.Add(this, Expr.Num(o))
operator fun Expr.times(o: Expr): Expr = Expr.Mul(this, o)
operator fun Expr.times(o: Double): Expr = if (o == 1.0) this else Expr.Mul(this, Expr.Num(o))
operator fun Expr.minus(o: Expr): Expr = Expr.Add(this, Expr.Mul(o, Expr.Num(-1.0)))
fun Expr.clip(lo: Double = 0.0, hi: Double = 1.0): Expr = Expr.Clip(this, lo, hi)

/** [value] in full, without an exponent: FFmpeg reads back exactly the same double. */
internal fun exact(value: Double): String {
    val text = value.toString()
    return if (text.contains('E') || text.contains('e')) BigDecimal(value).toPlainString() else text.removeSuffix(".0")
}

/** A number written into a filter: at most six decimals. */
internal fun num(value: Double): String =
    BigDecimal(value).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

/** [value] kept to the precision it is written with. */
internal fun written(value: Double): Double = num(value).toDouble()

// -- Helpers -------------------------------------------------------------------------------

private const val FIXP = 1 shl 16
private const val FIXP2 = 1L shl 20
private const val INT_PI = 3294199L

/** The rotate filter's sine: [angle] scaled by 2^20, the result by 2^16. */
private fun intSin(angle: Long): Long {
    var a = angle
    if (a < 0) a = INT_PI - a
    a %= 2 * INT_PI
    if (a >= INT_PI * 3 / 2) a -= 2 * INT_PI
    if (a >= INT_PI / 2) a = INT_PI - a
    val a2 = a * a / FIXP2
    var res = 0L
    var i = 2
    while (i < 11) {
        res += a
        a = -a * a2 / (FIXP2 * i * (i + 1))
        i += 2
    }
    return (res + 8) shr 4
}

/** One channel (at [shift]) of the rotate filter's bilinear interpolation, in 16.16 fixed point. */
private fun rotateChannel(p00: Int, p01: Int, p10: Int, p11: Int, fx: Int, fy: Int, shift: Int): Int {
    val s0 = (FIXP - fx) * ((p00 shr shift) and 0xFF) + fx * ((p01 shr shift) and 0xFF)
    val s1 = (FIXP - fx) * ((p10 shr shift) and 0xFF) + fx * ((p11 shr shift) and 0xFF)
    return ((((FIXP - fy).toLong() * s0 + fy.toLong() * s1) shr 32).toInt()) and 0xFF
}

internal fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

/** Index [i] reflected back into 0 until [size], as the convolution filter does it. */
private fun mirror(i: Int, size: Int): Int {
    val a = if (i < 0) -i else i
    return (if (a >= size) 2 * size - 1 - a else a).coerceIn(0, size - 1)
}

/** Bilinear sample at ([x], [y]); outside the picture is black. */
private fun sample(input: Raster, x: Double, y: Double): Int {
    if (x < -0.5 || y < -0.5 || x > input.width - 0.5 || y > input.height - 0.5) return rgb(0, 0, 0)
    val cx = x.coerceIn(0.0, input.width - 1.0)
    val cy = y.coerceIn(0.0, input.height - 1.0)
    val x0 = floor(cx).toInt()
    val y0 = floor(cy).toInt()
    val x1 = min(x0 + 1, input.width - 1)
    val y1 = min(y0 + 1, input.height - 1)
    val fx = cx - x0
    val fy = cy - y0
    val p00 = input.pixels[y0 * input.width + x0]
    val p10 = input.pixels[y0 * input.width + x1]
    val p01 = input.pixels[y1 * input.width + x0]
    val p11 = input.pixels[y1 * input.width + x1]
    fun ch(shift: Int): Int {
        val a = (p00 shr shift) and 0xFF
        val b = (p10 shr shift) and 0xFF
        val c = (p01 shr shift) and 0xFF
        val d = (p11 shr shift) and 0xFF
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        return (top + (bottom - top) * fy).roundToInt().coerceIn(0, 255)
    }
    return rgb(ch(16), ch(8), ch(0))
}

private fun splitChannels(input: Raster): List<IntArray> = listOf(16, 8, 0).map { shift ->
    IntArray(input.pixels.size) { (input.pixels[it] shr shift) and 0xFF }
}

private fun joinChannels(channels: List<IntArray>, width: Int, height: Int): Raster =
    Raster(width, height, IntArray(width * height) { rgb(channels[0][it], channels[1][it], channels[2][it]) })

/** One pass of a box average of [radius] along rows ([horizontal]) or columns, edges repeated. */
internal fun boxPass(plane: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
    val out = IntArray(plane.size)
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val r = radius.coerceAtMost(max(0, (length - 1) / 2)).coerceAtLeast(0)
    val size = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) plane[line * width + c] else plane[c * width + line]
        }
        var sum = 0
        for (i in -r..r) sum += at(i)
        for (i in 0 until length) {
            val value = (sum + size / 2) / size
            if (horizontal) out[line * width + i] = value else out[i * width + line] = value
            sum += at(i + r + 1) - at(i - r)
        }
    }
    return out
}
