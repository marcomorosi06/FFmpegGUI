package com.cuscus.ffmpeggui.core.subtitles

import java.io.File
import java.io.RandomAccessFile

/**
 * What libass needs to know of a font file, read from its tables: the family
 * it is asked for by, and the metrics it sizes the text with.
 *
 * libass gives a line the height of the font size, split between ascent and
 * descent as the OS/2 table's Windows metrics say; the em (what the font size
 * means everywhere else) is [emPerSize] of it. The preview uses these to draw
 * the text exactly where libass will.
 */
class FontFacts(
    val family: String,
    val unitsPerEm: Int,
    val winAscent: Int,
    val winDescent: Int,
    /** Weight class of the OS/2 table (400 regular, 700 bold). */
    val weight: Int = 400,
    /** Advance width of each glyph, in font units. */
    private val advances: IntArray = IntArray(0),
    /** Glyph of each character of the Basic Multilingual Plane; 0 = none. */
    private val glyphs: CharArray = CharArray(0),
    /** Width of a character the font has no glyph for, in em. */
    private val fallbackEm: Float = DEFAULT_CHAR_EM,
) {
    /** Width of [text] in em, as the font's advances add up (no kerning). */
    fun widthEm(text: String): Float {
        if (unitsPerEm <= 0) return text.length * fallbackEm
        var units = 0L
        var missing = 0
        for (c in text) {
            val glyph = if (c.code < glyphs.size) glyphs[c.code].code else 0
            if (glyph == 0 || advances.isEmpty()) {
                missing++
            } else {
                units += advances[minOf(glyph, advances.size - 1)]
            }
        }
        return units.toFloat() / unitsPerEm + missing * fallbackEm
    }

    override fun toString(): String = "FontFacts($family, $unitsPerEm, $winAscent, $winDescent, $weight)"

    /** The em size of a font size of 1 (a line height of 1). */
    val emPerSize: Float
        get() = if (winAscent + winDescent > 0) unitsPerEm.toFloat() / (winAscent + winDescent) else SubtitleLayout.EM_PER_SIZE

    /** Where the baseline sits in a line of height 1, from its top. */
    val baselinePerSize: Float
        get() = if (winAscent + winDescent > 0) winAscent.toFloat() / (winAscent + winDescent) else BASELINE_FALLBACK

    companion object {
        private const val BASELINE_FALLBACK = 0.79f

        /** Average width of a character in em, when nothing better is known. */
        const val DEFAULT_CHAR_EM = 0.52f

        /** The facts of a TrueType / OpenType file (the first face of a collection), or null when unreadable. */
        fun read(file: File): FontFacts? = try {
            RandomAccessFile(file, "r").use { read(it) }
        } catch (e: Exception) {
            null
        }

        private fun read(data: RandomAccessFile): FontFacts? {
            var base = 0L
            data.seek(0)
            if (data.readInt() == TTC_TAG) {
                data.seek(12)
                base = data.readInt().toLong() and 0xFFFFFFFFL
            }
            data.seek(base + 4)
            val tableCount = data.readUnsignedShort()
            val tables = HashMap<Int, Long>()
            for (i in 0 until tableCount) {
                data.seek(base + 12 + i * 16L)
                val tag = data.readInt()
                data.readInt() // checksum
                val offset = data.readInt().toLong() and 0xFFFFFFFFL
                tables[tag] = offset
            }
            val head = tables[tag("head")] ?: return null
            data.seek(head + 18)
            val unitsPerEm = data.readUnsignedShort()
            var ascent = 0
            var descent = 0
            tables[tag("OS/2")]?.let { os2 ->
                data.seek(os2 + 74)
                ascent = data.readShort().toInt()
                descent = data.readShort().toInt()
            }
            if (ascent + descent <= 0) {
                // No usable Windows metrics: libass then keeps the hhea ones.
                val hhea = tables[tag("hhea")] ?: return null
                data.seek(hhea + 4)
                ascent = data.readShort().toInt()
                descent = -data.readShort().toInt()
            }
            val family = tables[tag("name")]?.let { family(data, it) } ?: return null
            var weight = 400
            var averageEm = DEFAULT_CHAR_EM
            tables[tag("OS/2")]?.let { os2 ->
                data.seek(os2 + 2)
                val average = data.readShort().toInt()
                weight = data.readUnsignedShort()
                if (average > 0 && unitsPerEm > 0) averageEm = average.toFloat() / unitsPerEm
            }
            val advances = tables[tag("hhea")]?.let { hhea ->
                data.seek(hhea + 34)
                val metrics = data.readUnsignedShort()
                tables[tag("hmtx")]?.let { hmtx ->
                    val bytes = ByteArray(metrics * 4)
                    data.seek(hmtx)
                    data.readFully(bytes)
                    IntArray(metrics) { i -> ((bytes[i * 4].toInt() and 0xFF) shl 8) or (bytes[i * 4 + 1].toInt() and 0xFF) }
                }
            } ?: IntArray(0)
            val glyphs = tables[tag("cmap")]?.let { glyphMap(data, it) } ?: CharArray(0)
            return FontFacts(family, unitsPerEm, ascent, descent, weight, advances, glyphs, averageEm)
        }

        /** Glyph of every BMP character, from the Unicode subtable (format 4) of the cmap. */
        private fun glyphMap(data: RandomAccessFile, table: Long): CharArray? {
            data.seek(table + 2)
            val count = data.readUnsignedShort()
            var subtable = -1L
            for (i in 0 until count) {
                data.seek(table + 4 + i * 8L)
                val platform = data.readUnsignedShort()
                val encoding = data.readUnsignedShort()
                val offset = data.readInt().toLong() and 0xFFFFFFFFL
                data.seek(table + offset)
                val format = data.readUnsignedShort()
                val unicode = (platform == 3 && encoding == 1) || platform == 0
                if (format == 4 && unicode) {
                    subtable = table + offset
                    if (platform == 3) break
                }
            }
            if (subtable < 0) return null
            data.seek(subtable + 2)
            val length = data.readUnsignedShort()
            val bytes = ByteArray(length)
            data.seek(subtable)
            data.readFully(bytes)
            fun u16(at: Int): Int = if (at + 1 < bytes.size) ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF) else 0
            val segments = u16(6) / 2
            val endsAt = 14
            val startsAt = endsAt + segments * 2 + 2
            val deltasAt = startsAt + segments * 2
            val rangesAt = deltasAt + segments * 2
            val map = CharArray(0x10000)
            for (i in 0 until segments) {
                val start = u16(startsAt + i * 2)
                val end = u16(endsAt + i * 2)
                val delta = u16(deltasAt + i * 2).toShort().toInt()
                val rangeOffset = u16(rangesAt + i * 2)
                if (start > end || start == 0xFFFF) continue
                for (code in start..end) {
                    val glyph = if (rangeOffset == 0) {
                        (code + delta) and 0xFFFF
                    } else {
                        val raw = u16(rangesAt + i * 2 + rangeOffset + (code - start) * 2)
                        if (raw == 0) 0 else (raw + delta) and 0xFFFF
                    }
                    map[code] = glyph.toChar()
                }
            }
            return map
        }

        /** Name 1 (the family) in Windows English, else any Windows one, else the Macintosh one. */
        private fun family(data: RandomAccessFile, table: Long): String? {
            data.seek(table + 2)
            val count = data.readUnsignedShort()
            val strings = table + data.readUnsignedShort()
            var windowsAny: String? = null
            var mac: String? = null
            for (i in 0 until count) {
                data.seek(table + 6 + i * 12L)
                val platform = data.readUnsignedShort()
                data.readUnsignedShort() // encoding
                val language = data.readUnsignedShort()
                val nameId = data.readUnsignedShort()
                val length = data.readUnsignedShort()
                val offset = data.readUnsignedShort()
                if (nameId != 1 || length == 0) continue
                val bytes = ByteArray(length)
                val here = data.filePointer
                data.seek(strings + offset)
                data.readFully(bytes)
                data.seek(here)
                when (platform) {
                    3 -> {
                        val name = String(bytes, Charsets.UTF_16BE).trim()
                        if (language == ENGLISH_US) return name
                        if (windowsAny == null) windowsAny = name
                    }
                    1 -> if (mac == null) mac = String(bytes, Charsets.ISO_8859_1).trim()
                }
            }
            return (windowsAny ?: mac)?.takeIf { it.isNotEmpty() }
        }

        private fun tag(name: String): Int = name.fold(0) { acc, c -> (acc shl 8) or c.code }

        private val TTC_TAG = tag("ttcf")
        private const val ENGLISH_US = 0x409
    }
}
