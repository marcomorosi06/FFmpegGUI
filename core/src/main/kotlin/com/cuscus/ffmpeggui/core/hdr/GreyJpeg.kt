package com.cuscus.ffmpeggui.core.hdr

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A baseline JPG of one grey channel: what a gain map is stored as. Android's
 * own encoder only writes colour JPGs, and a colour gain map would be read as
 * three channels by some decoders, so this writes the single channel itself:
 * the standard quantisation table scaled to the quality asked (as libjpeg
 * scales it) and the standard Huffman tables (ITU T.81, annex K).
 */
object GreyJpeg {

    /** [values] (0..255, [width] x [height], row by row) as a JPG at [quality] 1..100. */
    fun encode(values: ByteArray, width: Int, height: Int, quality: Int = 90): ByteArray {
        require(width in 1..0xFFFF && height in 1..0xFFFF && values.size == width * height) { "One value per pixel" }
        val table = quantTable(quality.coerceIn(1, 100))
        val out = ByteArrayOutputStream(values.size / 4 + 1024)
        writeHeaders(out, width, height, table)
        val bits = BitWriter(out)
        val block = FloatArray(64)
        val coefficients = FloatArray(64)
        var previousDc = 0
        for (by in 0 until (height + 7) / 8) {
            for (bx in 0 until (width + 7) / 8) {
                // The block, its edges repeated past the picture's, centred on 0.
                for (y in 0 until 8) {
                    val sy = minOf(by * 8 + y, height - 1)
                    for (x in 0 until 8) {
                        val sx = minOf(bx * 8 + x, width - 1)
                        block[y * 8 + x] = (values[sy * width + sx].toInt() and 0xFF) - 128f
                    }
                }
                forwardDct(block, coefficients)
                val quantised = IntArray(64) { k ->
                    val natural = ZIGZAG[k]
                    (coefficients[natural] / table[natural]).roundToInt()
                }
                // The DC as the change from the block before.
                val diff = quantised[0] - previousDc
                previousDc = quantised[0]
                val dcSize = bitLength(diff)
                bits.write(DC_CODES[dcSize], DC_LENGTHS[dcSize])
                if (dcSize > 0) bits.write(magnitude(diff, dcSize), dcSize)
                // The AC as runs of zeros and values.
                var run = 0
                for (k in 1 until 64) {
                    val v = quantised[k]
                    if (v == 0) {
                        run++
                        continue
                    }
                    while (run > 15) {
                        bits.write(AC_CODES[0xF0], AC_LENGTHS[0xF0])
                        run -= 16
                    }
                    val size = bitLength(v)
                    val symbol = (run shl 4) or size
                    bits.write(AC_CODES[symbol], AC_LENGTHS[symbol])
                    bits.write(magnitude(v, size), size)
                    run = 0
                }
                if (run > 0) bits.write(AC_CODES[0x00], AC_LENGTHS[0x00])
            }
        }
        bits.flush()
        out.write(0xFF)
        out.write(0xD9)
        return out.toByteArray()
    }

    private fun writeHeaders(out: ByteArrayOutputStream, width: Int, height: Int, table: IntArray) {
        fun u16(v: Int) { out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }
        out.write(0xFF); out.write(0xD8)
        // JFIF.
        out.write(0xFF); out.write(0xE0); u16(16)
        out.write("JFIF".toByteArray(Charsets.US_ASCII)); out.write(0)
        out.write(1); out.write(1); out.write(0); u16(1); u16(1); out.write(0); out.write(0)
        // The quantisation table, in zigzag order.
        out.write(0xFF); out.write(0xDB); u16(67); out.write(0)
        for (k in 0 until 64) out.write(table[ZIGZAG[k]])
        // One 8-bit grey component.
        out.write(0xFF); out.write(0xC0); u16(11); out.write(8); u16(height); u16(width)
        out.write(1); out.write(1); out.write(0x11); out.write(0)
        // The Huffman tables.
        out.write(0xFF); out.write(0xC4); u16(2 + 1 + 16 + DC_VALUES.size + 1 + 16 + AC_VALUES.size)
        out.write(0x00); DC_BITS.forEach { out.write(it) }; DC_VALUES.forEach { out.write(it) }
        out.write(0x10); AC_BITS.forEach { out.write(it) }; AC_VALUES.forEach { out.write(it) }
        // The scan.
        out.write(0xFF); out.write(0xDA); u16(8); out.write(1); out.write(1); out.write(0x00)
        out.write(0); out.write(63); out.write(0)
    }

    /** The standard luminance table scaled to [quality], natural order. */
    private fun quantTable(quality: Int): IntArray {
        val scale = if (quality < 50) 5000 / quality else 200 - 2 * quality
        return IntArray(64) { ((BASE_TABLE[it] * scale + 50) / 100).coerceIn(1, 255) }
    }

    private val COSINES = Array(8) { u -> FloatArray(8) { x -> cos((2 * x + 1) * u * PI / 16).toFloat() } }
    private val C0 = (1 / sqrt(2.0)).toFloat()

    /** The 8 x 8 DCT of [block] into [out], both row by row. */
    private fun forwardDct(block: FloatArray, out: FloatArray) {
        val rows = FloatArray(64)
        for (y in 0 until 8) {
            for (u in 0 until 8) {
                var sum = 0f
                for (x in 0 until 8) sum += block[y * 8 + x] * COSINES[u][x]
                rows[y * 8 + u] = sum * (if (u == 0) C0 else 1f) / 2f
            }
        }
        for (u in 0 until 8) {
            for (v in 0 until 8) {
                var sum = 0f
                for (y in 0 until 8) sum += rows[y * 8 + u] * COSINES[v][y]
                out[v * 8 + u] = sum * (if (v == 0) C0 else 1f) / 2f
            }
        }
    }

    private fun bitLength(value: Int): Int {
        var v = abs(value)
        var n = 0
        while (v > 0) {
            n++
            v = v shr 1
        }
        return n
    }

    /** The bits written for [value] of [size] bits: itself, or for a negative one its ones' complement. */
    private fun magnitude(value: Int, size: Int): Int = if (value >= 0) value else value + (1 shl size) - 1

    /** Bits into bytes, a 0 after every 0xFF written. */
    private class BitWriter(private val out: ByteArrayOutputStream) {
        private var buffer = 0
        private var count = 0

        fun write(code: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                buffer = (buffer shl 1) or ((code shr i) and 1)
                count++
                if (count == 8) emit()
            }
        }

        fun flush() {
            while (count != 0) {
                buffer = (buffer shl 1) or 1
                count++
                if (count == 8) emit()
            }
        }

        private fun emit() {
            out.write(buffer)
            if (buffer == 0xFF) out.write(0)
            buffer = 0
            count = 0
        }
    }

    private val ZIGZAG = intArrayOf(
        0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
        12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
        58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63,
    )

    private val BASE_TABLE = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99,
    )

    private val DC_BITS = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
    private val DC_VALUES = IntArray(12) { it }
    private val AC_BITS = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7D)
    private val AC_VALUES = intArrayOf(
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
        0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xA1, 0x08, 0x23, 0x42, 0xB1, 0xC1, 0x15, 0x52, 0xD1, 0xF0,
        0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
        0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
        0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7,
        0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3, 0xC4, 0xC5,
        0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xE1, 0xE2,
        0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8,
        0xF9, 0xFA,
    )

    /** Codes and lengths of each symbol, from a table's counts per length and its symbols (canonical Huffman). */
    private fun codes(bits: IntArray, symbols: IntArray): Pair<IntArray, IntArray> {
        val codes = IntArray(256)
        val lengths = IntArray(256)
        var code = 0
        var k = 0
        for (length in 1..16) {
            repeat(bits[length - 1]) {
                codes[symbols[k]] = code
                lengths[symbols[k]] = length
                code++
                k++
            }
            code = code shl 1
        }
        return codes to lengths
    }

    private val DC = codes(DC_BITS, DC_VALUES)
    private val AC = codes(AC_BITS, AC_VALUES)
    private val DC_CODES = DC.first
    private val DC_LENGTHS = DC.second
    private val AC_CODES = AC.first
    private val AC_LENGTHS = AC.second
}
