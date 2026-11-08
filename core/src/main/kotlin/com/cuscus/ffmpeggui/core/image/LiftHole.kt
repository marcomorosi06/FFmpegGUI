package com.cuscus.ffmpeggui.core.image

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What the photo editor's long press leaves where a subject was lifted, when
 * the settings ask for more than a copy: the hole cut (transparent, or a
 * colour), or filled in by the magic eraser. Pure, tested.
 *
 * The subject is found on the picture as the editor shows it (turned, cut,
 * straightened...), but the hole goes in the photo itself, before those
 * steps: [sourcePoint] takes the press back through them to the photo, where
 * the subject is found again.
 */
object LiftHole {

    /**
     * Where the point ([x], [y]) of the picture after the geometry steps of
     * [stages] (the photo [width] x [height] going in) comes from in the photo:
     * each step undone, the last first. The colour steps after them do not move
     * anything.
     */
    fun sourcePoint(stages: List<ImageStage>, x: Double, y: Double, width: Int, height: Int): Pair<Double, Double> {
        val geometry = stages.takeWhile { it.isGeometry }
        // The size going into each step.
        val sizes = ArrayList<Pair<Int, Int>>(geometry.size + 1)
        var w = width.coerceAtLeast(1)
        var h = height.coerceAtLeast(1)
        sizes += w to h
        for (stage in geometry) {
            when (stage) {
                is ImageStage.QuarterTurn -> { val t = w; w = h; h = t }
                is ImageStage.Straighten -> stage.outputSize(w, h).let { (ow, oh) -> w = ow; h = oh }
                is ImageStage.Crop -> crop(stage, w, h).let { w = it[2]; h = it[3] }
                else -> Unit
            }
            sizes += w to h
        }
        var px = x
        var py = y
        for (i in geometry.indices.reversed()) {
            val (inw, inh) = sizes[i]
            when (val stage = geometry[i]) {
                is ImageStage.Crop -> crop(stage, inw, inh).let { px += it[0]; py += it[1] }
                is ImageStage.Mirror -> if (stage.horizontal) px = inw - 1 - px else py = inh - 1 - py
                is ImageStage.QuarterTurn -> {
                    // Out (ox, oy) came from in (x, y): clockwise ox = h - 1 - y, oy = x.
                    val ox = px
                    val oy = py
                    if (stage.clockwise) {
                        px = oy
                        py = inh - 1 - ox
                    } else {
                        px = inw - 1 - oy
                        py = ox
                    }
                }
                is ImageStage.Perspective -> stage.sourceOf(px, py, inw, inh).let { px = it.first; py = it.second }
                is ImageStage.Straighten -> stage.sourceOf(px, py, inw, inh).let { px = it.first; py = it.second }
                else -> Unit
            }
        }
        return px to py
    }

    /** A crop's left, top, width and height on an input of [w] x [h], as [ImageStage.Crop] cuts it. */
    private fun crop(stage: ImageStage.Crop, w: Int, h: Int): IntArray {
        val cw = (w * stage.w).toInt().coerceIn(1, w)
        val ch = (h * stage.h).toInt().coerceIn(1, h)
        val cx = (w * stage.x).toInt().coerceIn(0, w - cw)
        val cy = (h * stage.y).toInt().coerceIn(0, h - ch)
        return intArrayOf(cx, cy, cw, ch)
    }

    /**
     * [cover] (one byte a pixel, 0..255: how much of the subject is there) of
     * [width] x [height], stretched to [toWidth] x [toHeight], bilinear.
     */
    fun scaled(cover: ByteArray, width: Int, height: Int, toWidth: Int, toHeight: Int): ByteArray {
        if (width == toWidth && height == toHeight) return cover
        val out = ByteArray(toWidth * toHeight)
        val sx = width.toDouble() / toWidth
        val sy = height.toDouble() / toHeight
        for (y in 0 until toHeight) {
            val fy = ((y + 0.5) * sy - 0.5).coerceIn(0.0, (height - 1).toDouble())
            val y0 = floor(fy).toInt()
            val y1 = minOf(y0 + 1, height - 1)
            val ty = fy - y0
            for (x in 0 until toWidth) {
                val fx = ((x + 0.5) * sx - 0.5).coerceIn(0.0, (width - 1).toDouble())
                val x0 = floor(fx).toInt()
                val x1 = minOf(x0 + 1, width - 1)
                val tx = fx - x0
                fun at(ix: Int, iy: Int) = (cover[iy * width + ix].toInt() and 0xFF).toDouble()
                val top = at(x0, y0) * (1 - tx) + at(x1, y0) * tx
                val bottom = at(x0, y1) * (1 - tx) + at(x1, y1) * tx
                out[y * toWidth + x] = (top * (1 - ty) + bottom * ty).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    /**
     * [pixels] (ARGB, not premultiplied) with the hole [cover] marks: where it
     * is whole, [transparent] makes it see-through, with [opaqueColor] under
     * it for the files that hold no transparency (they drop the alpha and
     * keep the colour), else it is [color]; at its soft edge, as much of
     * either as the cover says. A new array.
     */
    fun punch(pixels: IntArray, cover: ByteArray, transparent: Boolean, color: Long, opaqueColor: Long): IntArray {
        require(pixels.size == cover.size)
        val fill = (if (transparent) opaqueColor else color).toInt() and 0xFFFFFF
        val fr = (fill shr 16) and 0xFF
        val fg = (fill shr 8) and 0xFF
        val fb = fill and 0xFF
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val c = cover[i].toInt() and 0xFF
            if (c == 0) return@IntArray p
            val keep = 255 - c
            val r = (((p shr 16) and 0xFF) * keep + fr * c + 127) / 255
            val g = (((p shr 8) and 0xFF) * keep + fg * c + 127) / 255
            val b = ((p and 0xFF) * keep + fb * c + 127) / 255
            val a = if (transparent) (((p ushr 24) and 0xFF) * keep + 127) / 255 else (p ushr 24) and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

/**
 * A PNG writer for pictures with transparency, colours kept as they are under
 * it (Android's encoder writes from premultiplied pixels, where a see-through
 * pixel has lost its colour). 8 bits RGBA, no interlace. Pure, tested.
 */
object RgbaPng {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** [argb] ([width] x [height], not premultiplied) as a PNG, to [out]. */
    fun write(out: OutputStream, width: Int, height: Int, argb: IntArray) {
        require(argb.size == width * height)
        out.write(SIGNATURE)
        chunk(out, "IHDR", ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8)
                writeByte(6)
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray())
        val data = ByteArrayOutputStream()
        DeflaterOutputStream(data, Deflater(Deflater.DEFAULT_COMPRESSION)).use { z ->
            val row = ByteArray(1 + width * 4)
            for (y in 0 until height) {
                // Filter 0 (none) on every row.
                row[0] = 0
                for (x in 0 until width) {
                    val p = argb[y * width + x]
                    val o = 1 + x * 4
                    row[o] = (p shr 16).toByte()
                    row[o + 1] = (p shr 8).toByte()
                    row[o + 2] = p.toByte()
                    row[o + 3] = (p ushr 24).toByte()
                }
                z.write(row)
            }
        }
        chunk(out, "IDAT", data.toByteArray())
        chunk(out, "IEND", ByteArray(0))
    }

    private fun chunk(out: OutputStream, type: String, body: ByteArray) {
        val head = DataOutputStream(out)
        head.writeInt(body.size)
        val name = type.toByteArray(Charsets.US_ASCII)
        out.write(name)
        out.write(body)
        val crc = CRC32().apply {
            update(name)
            update(body)
        }
        head.writeInt(crc.value.toInt())
    }
}

/**
 * [raster] (ARGB, not premultiplied) at [width] x [height], each channel
 * bilinear on its own: the colour under a see-through pixel is kept, as a file
 * that holds no transparency shows it. Pure, tested.
 */
fun scaledRaster(raster: Raster, width: Int, height: Int): Raster {
    if (raster.width == width && raster.height == height) return raster
    val w = raster.width
    val h = raster.height
    val src = raster.pixels
    val sx = w.toDouble() / width
    val sy = h.toDouble() / height
    val out = IntArray(width * height)
    for (y in 0 until height) {
        val fy = ((y + 0.5) * sy - 0.5).coerceIn(0.0, (h - 1).toDouble())
        val y0 = fy.toInt()
        val y1 = minOf(y0 + 1, h - 1)
        val ty = fy - y0
        for (x in 0 until width) {
            val fx = ((x + 0.5) * sx - 0.5).coerceIn(0.0, (w - 1).toDouble())
            val x0 = fx.toInt()
            val x1 = minOf(x0 + 1, w - 1)
            val tx = fx - x0
            val p00 = src[y0 * w + x0]
            val p01 = src[y0 * w + x1]
            val p10 = src[y1 * w + x0]
            val p11 = src[y1 * w + x1]
            var pixel = 0
            for (shift in intArrayOf(24, 16, 8, 0)) {
                fun c(p: Int) = ((p ushr shift) and 0xFF).toDouble()
                val v = (c(p00) * (1 - tx) + c(p01) * tx) * (1 - ty) + (c(p10) * (1 - tx) + c(p11) * tx) * ty
                pixel = pixel or ((v + 0.5).toInt().coerceIn(0, 255) shl shift)
            }
            out[y * width + x] = pixel
        }
    }
    return Raster(width, height, out)
}
