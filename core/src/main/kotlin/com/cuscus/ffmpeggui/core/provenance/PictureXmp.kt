package com.cuscus.ffmpeggui.core.provenance

import java.io.ByteArrayOutputStream
import java.io.File

/*
 * More writers of an XMP packet into a finished picture (see XmpWriters.kt):
 * WebP in an "XMP " chunk, GIF in an application extension, TIFF in its tag
 * 700, SVG in its <metadata>. The same rules: an XMP already there is
 * replaced, a file not recognised is left alone (false). Pure, tested.
 */

/**
 * XMP in a WebP: an "XMP " chunk last, flagged in the VP8X chunk. A simple
 * WebP (one VP8 or VP8L picture, no other chunk) is made an extended one for
 * it, its size read from the picture.
 */
object WebpXmp {
    private const val XMP_FLAG = 0x04
    private const val ALPHA_FLAG = 0x10

    fun write(file: File, xmp: String): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < 20 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WEBP") return false
        val declared = littleInt(bytes, 4).toLong() and 0xFFFFFFFFL
        if (declared + 8 != bytes.size.toLong()) return false
        val chunks = mutableListOf<Pair<String, ByteArray>>()
        var position = 12
        while (position < bytes.size) {
            if (position + 8 > bytes.size) return false
            val type = String(bytes, position, 4, Charsets.US_ASCII)
            val size = littleInt(bytes, position + 4)
            if (size < 0 || position + 8 + size > bytes.size) return false
            chunks += type to bytes.copyOfRange(position + 8, position + 8 + size)
            position += 8 + size + (size and 1)
        }
        if (chunks.isEmpty()) return false
        val first = chunks.first()
        val head: ByteArray = when (first.first) {
            "VP8X" -> first.second.copyOf().also { if (it.size < 10) return false }
            "VP8 ", "VP8L" -> extendedHeader(first.first, first.second) ?: return false
            else -> return false
        }
        head[0] = (head[0].toInt() or XMP_FLAG).toByte()
        val kept = chunks.drop(if (first.first == "VP8X") 1 else 0).filter { it.first != "XMP " }
        val out = ByteArrayOutputStream(bytes.size + xmp.length + 32)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(ByteArray(4))
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        fun chunk(type: String, data: ByteArray) {
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(littleBytes(data.size))
            out.write(data)
            if (data.size and 1 == 1) out.write(0)
        }
        chunk("VP8X", head)
        for ((type, data) in kept) chunk(type, data)
        chunk("XMP ", xmp.toByteArray(Charsets.UTF_8))
        val result = out.toByteArray()
        littleBytes(result.size - 8).copyInto(result, 4)
        file.writeBytes(result)
        return true
    }

    /** The VP8X chunk of a simple WebP: its flags, then its width and height less one, 24 bits each. */
    private fun extendedHeader(type: String, data: ByteArray): ByteArray? {
        val width: Int
        val height: Int
        var flags = 0
        if (type == "VP8L") {
            if (data.size < 5 || data[0] != 0x2F.toByte()) return null
            val bits = littleInt(data, 1)
            width = (bits and 0x3FFF) + 1
            height = ((bits ushr 14) and 0x3FFF) + 1
            if ((bits ushr 28) and 1 == 1) flags = ALPHA_FLAG
        } else {
            if (data.size < 10 || data[3] != 0x9D.toByte() || data[4] != 0x01.toByte() || data[5] != 0x2A.toByte()) return null
            width = ((data[6].toInt() and 0xFF) or ((data[7].toInt() and 0xFF) shl 8)) and 0x3FFF
            height = ((data[8].toInt() and 0xFF) or ((data[9].toInt() and 0xFF) shl 8)) and 0x3FFF
        }
        if (width < 1 || height < 1) return null
        val w = width - 1
        val h = height - 1
        return byteArrayOf(
            flags.toByte(), 0, 0, 0,
            w.toByte(), (w shr 8).toByte(), (w shr 16).toByte(),
            h.toByte(), (h shr 8).toByte(), (h shr 16).toByte(),
        )
    }
}

/**
 * XMP in a GIF: the application extension "XMP DataXMP" before the trailer,
 * the packet as it is followed by the "magic trailer" that lets readers
 * that do not know it skip it as ordinary data blocks (XMP Specification,
 * part 3). A GIF87a becomes a GIF89a, the first version with extensions.
 */
object GifXmp {
    private val IDENTIFIER = "XMP DataXMP".toByteArray(Charsets.US_ASCII)

    fun write(file: File, xmp: String): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < 14) return false
        val version = String(bytes, 0, 6, Charsets.US_ASCII)
        if (version != "GIF87a" && version != "GIF89a") return false
        val payload = xmp.toByteArray(Charsets.UTF_8)
        // A zero would end the data early for those readers: XML has none.
        if (payload.any { it == 0.toByte() }) return false
        val out = ByteArrayOutputStream(bytes.size + payload.size + 300)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        out.write(bytes, 6, 7)
        var position = 13
        val screenFlags = bytes[10].toInt() and 0xFF
        if (screenFlags and 0x80 != 0) {
            val table = 3 shl ((screenFlags and 0x07) + 1)
            if (position + table > bytes.size) return false
            out.write(bytes, position, table)
            position += table
        }
        fun subBlocksEnd(from: Int): Int? {
            var at = from
            while (true) {
                if (at >= bytes.size) return null
                val size = bytes[at].toInt() and 0xFF
                at += 1
                if (size == 0) return at
                at += size
            }
        }
        while (true) {
            if (position >= bytes.size) return false
            when (bytes[position].toInt() and 0xFF) {
                0x3B -> {
                    out.write(0x21)
                    out.write(0xFF)
                    out.write(IDENTIFIER.size)
                    out.write(IDENTIFIER)
                    out.write(payload)
                    out.write(0x01)
                    for (v in 0xFF downTo 0x00) out.write(v)
                    out.write(0x00)
                    out.write(0x3B)
                    file.writeBytes(out.toByteArray())
                    return true
                }
                0x21 -> {
                    if (position + 2 > bytes.size) return false
                    val end = subBlocksEnd(position + 2) ?: return false
                    val isXmp = (bytes[position + 1].toInt() and 0xFF) == 0xFF && position + 3 + IDENTIFIER.size <= bytes.size &&
                        (bytes[position + 2].toInt() and 0xFF) == IDENTIFIER.size &&
                        bytes.copyOfRange(position + 3, position + 3 + IDENTIFIER.size).contentEquals(IDENTIFIER)
                    if (!isXmp) out.write(bytes, position, end - position)
                    position = end
                }
                0x2C -> {
                    if (position + 10 > bytes.size) return false
                    val flags = bytes[position + 9].toInt() and 0xFF
                    var at = position + 10
                    if (flags and 0x80 != 0) at += 3 shl ((flags and 0x07) + 1)
                    // The LZW code size, then the picture's data blocks.
                    val end = subBlocksEnd(at + 1) ?: return false
                    out.write(bytes, position, end - position)
                    position = end
                }
                else -> return false
            }
        }
    }
}

/**
 * XMP in a TIFF: tag 700 of its first directory. The directory is written
 * again after the file with the tag (its other values stay where they are,
 * their offsets still right), and the header points to it.
 */
object TiffXmp {
    private const val XMP_TAG = 700
    private const val BYTE = 1

    fun write(file: File, xmp: String): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < 8) return false
        val little = when {
            bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() -> true
            bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() -> false
            else -> return false
        }
        fun u16(i: Int) = if (little) (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8) else ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = if (little) (u16(i).toLong() or (u16(i + 2).toLong() shl 16)) else ((u16(i).toLong() shl 16) or u16(i + 2).toLong())
        // 42: classic TIFF (BigTIFF, 43, is not written by FFmpeg).
        if (u16(2) != 42) return false
        val ifd = u32(4)
        if (ifd < 8 || ifd + 2 > bytes.size) return false
        val count = u16(ifd.toInt())
        val entriesEnd = ifd + 2 + 12L * count
        if (entriesEnd + 4 > bytes.size) return false
        fun tagOf(entry: ByteArray) = if (little) (entry[0].toInt() and 0xFF) or ((entry[1].toInt() and 0xFF) shl 8) else ((entry[0].toInt() and 0xFF) shl 8) or (entry[1].toInt() and 0xFF)
        val entries = (0 until count).map { e -> bytes.copyOfRange(ifd.toInt() + 2 + 12 * e, ifd.toInt() + 2 + 12 * e + 12) }
            .filter { tagOf(it) != XMP_TAG }
        val next = bytes.copyOfRange(entriesEnd.toInt(), entriesEnd.toInt() + 4)
        val payload = xmp.toByteArray(Charsets.UTF_8)

        val out = ByteArrayOutputStream(bytes.size + payload.size + 12 * (count + 1) + 16)
        out.write(bytes)
        if (out.size() and 1 == 1) out.write(0)
        val xmpAt = out.size()
        out.write(payload)
        if (out.size() and 1 == 1) out.write(0)
        val newIfd = out.size()
        if (newIfd.toLong() + 6 + 12L * (entries.size + 1) > 0xFFFFFFFFL) return false
        fun w16(v: Int) = if (little) { out.write(v); out.write(v shr 8) } else { out.write(v shr 8); out.write(v) }
        fun w32(v: Long) = if (little) { w16((v and 0xFFFF).toInt()); w16((v shr 16).toInt() and 0xFFFF) } else { w16((v shr 16).toInt() and 0xFFFF); w16((v and 0xFFFF).toInt()) }
        // The entries stay sorted by tag, as TIFF asks.
        w16(entries.size + 1)
        var placed = false
        fun writeOurs() {
            w16(XMP_TAG)
            w16(BYTE)
            w32(payload.size.toLong())
            w32(xmpAt.toLong())
            placed = true
        }
        for (entry in entries) {
            if (!placed && tagOf(entry) > XMP_TAG) writeOurs()
            out.write(entry)
        }
        if (!placed) writeOurs()
        out.write(next)
        val result = out.toByteArray()
        val pointer = newIfd.toLong()
        for (k in 0 until 4) result[4 + k] = (pointer shr (if (little) 8 * k else 8 * (3 - k))).toByte()
        file.writeBytes(result)
        return true
    }
}

/** XMP in an SVG: a <metadata> element first inside the <svg> one (one of ours already there is replaced). */
object SvgXmp {
    private const val ID = "ffmpeggui-ai"

    fun write(file: File, xmp: String): Boolean {
        val text = file.readText(Charsets.UTF_8)
        val cleaned = text.replace(Regex("<metadata id=\"$ID\">.*?</metadata>\\s*", RegexOption.DOT_MATCHES_ALL), "")
        val start = Regex("<svg[\\s>]").find(cleaned)?.range?.first ?: return false
        var quote: Char? = null
        var end = -1
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                end = i
                break
            }
        }
        // An empty <svg/> has no picture to speak of.
        if (end < 0 || cleaned[end - 1] == '/') return false
        // The packet's processing instructions are fine inside XML; its text is already escaped.
        val element = "\n<metadata id=\"$ID\">\n$xmp\n</metadata>\n"
        file.writeText(cleaned.substring(0, end + 1) + element + cleaned.substring(end + 1), Charsets.UTF_8)
        return true
    }
}
