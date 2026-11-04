package com.cuscus.ffmpeggui.core.provenance

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32

/*
 * Writers of an XMP packet into a finished file, each where its format's
 * readers look for it: MP4 and QuickTime in a top-level "uuid" box, WAV and
 * AVI in a "_PMX" chunk of the RIFF, PNG in an iTXt chunk, JPEG in an APP1
 * segment (see PictureXmp.kt for WebP, GIF, TIFF and SVG). They run after
 * FFmpeg, on whatever it wrote (a command typed by hand included), so what
 * they write cannot be left out by the command. An XMP already in the file is
 * put out of use (readers take the first one they find). Each refuses a file
 * it does not recognise rather than damage it (false). Pure, tested.
 */

/** XMP in an MP4, M4A, MOV...: a top-level uuid box with Adobe's XMP id, after the others. */
object Mp4Xmp {
    internal val XMP_UUID = byteArrayOf(
        0xBE.toByte(), 0x7A, 0xCF.toByte(), 0xCB.toByte(), 0x97.toByte(), 0xA9.toByte(), 0x42, 0xE8.toByte(),
        0x9C.toByte(), 0x71, 0x99.toByte(), 0x94.toByte(), 0x91.toByte(), 0xE3.toByte(), 0xAF.toByte(), 0xAC.toByte(),
    )

    fun write(file: File, xmp: String): Boolean = RandomAccessFile(file, "rw").use { f ->
        val length = f.length()
        var position = 0L
        var first = true
        var hasMovie = false
        val oldXmp = mutableListOf<Long>()
        while (position < length) {
            if (length - position < 8) return false
            f.seek(position)
            val size32 = f.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { f.readFully(it) }.toString(Charsets.US_ASCII)
            if (first && type != "ftyp") return false
            first = false
            val header = if (size32 == 1L) 16 else 8
            val size = when (size32) {
                // A box that runs to the end of the file: nothing may come after it.
                0L -> return false
                1L -> if (length - position < 16) return false else f.readLong()
                else -> size32
            }
            if (size < header || position + size > length) return false
            if (type == "moov") hasMovie = true
            if (type == "uuid" && size >= header + XMP_UUID.size) {
                val id = ByteArray(XMP_UUID.size).also { f.readFully(it) }
                if (id.contentEquals(XMP_UUID)) oldXmp += position
            }
            position += size
        }
        if (!hasMovie) return false
        // An XMP already there becomes free space, its size unchanged.
        for (at in oldXmp) {
            f.seek(at + 4)
            f.write("free".toByteArray(Charsets.US_ASCII))
        }
        val payload = xmp.toByteArray(Charsets.UTF_8)
        f.seek(length)
        f.writeInt(8 + XMP_UUID.size + payload.size)
        f.write("uuid".toByteArray(Charsets.US_ASCII))
        f.write(XMP_UUID)
        f.write(payload)
        true
    }
}

/** XMP in a WAV or an AVI: a "_PMX" chunk at the end of the RIFF, its size made right. */
object RiffXmp {
    fun write(file: File, xmp: String): Boolean = RandomAccessFile(file, "rw").use { f ->
        val length = f.length()
        if (length < 12) return false
        val head = ByteArray(12).also { f.readFully(it) }
        if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF") return false
        val form = String(head, 8, 4, Charsets.US_ASCII)
        if (form != "WAVE" && form != "AVI ") return false
        val declared = littleInt(head, 4).toLong() and 0xFFFFFFFFL
        // A RIFF whose size is not what it says (cut short, or past 4 GB) is left alone.
        if (declared + 8 != length && declared + 8 != length - 1) return false
        // The chunks, for an XMP already there: it becomes JUNK (skipped by every reader).
        val oldXmp = mutableListOf<Long>()
        var position = 12L
        val chunk = ByteArray(8)
        while (position + 8 <= length) {
            f.seek(position)
            f.readFully(chunk)
            val size = littleInt(chunk, 4).toLong() and 0xFFFFFFFFL
            if (String(chunk, 0, 4, Charsets.US_ASCII) == "_PMX") oldXmp += position
            position += 8 + size + (size and 1)
        }
        for (at in oldXmp) {
            f.seek(at)
            f.write("JUNK".toByteArray(Charsets.US_ASCII))
        }
        val payload = xmp.toByteArray(Charsets.UTF_8)
        // After everything, a last pad byte the size left out included.
        val end = length
        val padded = payload.size + (payload.size and 1)
        val total = end + 8 + padded
        if (total - 8 > 0xFFFFFFFFL) return false
        f.seek(end)
        f.write("_PMX".toByteArray(Charsets.US_ASCII))
        f.write(littleBytes(payload.size))
        f.write(payload)
        if (payload.size and 1 == 1) f.write(0)
        f.setLength(total)
        f.seek(4)
        f.write(littleBytes((total - 8).toInt()))
        true
    }
}

/** XMP in a PNG: an iTXt chunk "XML:com.adobe.xmp" before IEND (one already there is replaced). */
object PngXmp {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val KEYWORD = "XML:com.adobe.xmp"

    fun write(file: File, xmp: String): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < SIGNATURE.size || !bytes.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)) return false
        val out = ByteArrayOutputStream(bytes.size + xmp.length + 64)
        out.write(SIGNATURE)
        var position = SIGNATURE.size
        while (position + 12 <= bytes.size) {
            val length = bigInt(bytes, position)
            if (length < 0 || position + 12 + length > bytes.size) return false
            val type = String(bytes, position + 4, 4, Charsets.US_ASCII)
            val chunkEnd = position + 12 + length
            when {
                type == "IEND" -> {
                    out.write(chunk("iTXt", iTxt(xmp)))
                    out.write(bytes, position, chunkEnd - position)
                    file.writeBytes(out.toByteArray())
                    return true
                }
                // An XMP already there goes: two would disagree.
                type == "iTXt" && startsWithKeyword(bytes, position + 8, length) -> Unit
                else -> out.write(bytes, position, chunkEnd - position)
            }
            position = chunkEnd
        }
        return false
    }

    private fun startsWithKeyword(bytes: ByteArray, at: Int, length: Int): Boolean {
        val key = (KEYWORD + "\u0000").toByteArray(Charsets.ISO_8859_1)
        return length >= key.size && bytes.copyOfRange(at, at + key.size).contentEquals(key)
    }

    /** iTXt: keyword, NUL, not compressed (0, 0), no language, no translated keyword, the text. */
    private fun iTxt(xmp: String): ByteArray = ByteArrayOutputStream().apply {
        write(KEYWORD.toByteArray(Charsets.ISO_8859_1))
        write(byteArrayOf(0, 0, 0, 0, 0))
        write(xmp.toByteArray(Charsets.UTF_8))
    }.toByteArray()

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()
        return ByteArrayOutputStream(12 + data.size).apply {
            write(bigBytes(data.size))
            write(typeBytes)
            write(data)
            write(bigBytes(crc))
        }.toByteArray()
    }
}

/**
 * XMP in a JPEG: an APP1 segment with Adobe's namespace, after its JFIF and
 * EXIF segments. An XMP already there is replaced, unless [merge] keeps it
 * with the marks added (an Ultra HDR picture's names its gain map: without it
 * the picture would lose its HDR). The index of a picture with others in it
 * (MPF: Ultra HDR, some cameras' pictures) is set right for the new sizes.
 */
object JpegXmp {
    private const val NAMESPACE = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val GAIN_MAP_NAMESPACE = "http://ns.adobe.com/hdr-gain-map/1.0/"
    private const val APP0 = 0xE0
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2
    private const val SOS = 0xDA

    /** [merge]: [xmp] for an XMP already there, the marks added to it (null: it cannot be). */
    fun write(file: File, xmp: String, merge: ((String) -> String?)? = null): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < 4 || (bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) return false
        val namespace = NAMESPACE.toByteArray(Charsets.ISO_8859_1)

        class Segment(val start: Int, val marker: Int, val length: Int) {
            val end get() = start + 2 + length
            fun payloadStarts(prefix: ByteArray) = length - 2 >= prefix.size &&
                bytes.copyOfRange(start + 4, start + 4 + prefix.size).contentEquals(prefix)
        }
        val segments = mutableListOf<Segment>()
        var position = 2
        while (true) {
            if (position + 4 > bytes.size || (bytes[position].toInt() and 0xFF) != 0xFF) return false
            val marker = bytes[position + 1].toInt() and 0xFF
            if (marker == SOS) break
            val length = ((bytes[position + 2].toInt() and 0xFF) shl 8) or (bytes[position + 3].toInt() and 0xFF)
            if (length < 2 || position + 2 + length > bytes.size) return false
            segments += Segment(position, marker, length)
            position += 2 + length
        }
        val body = position
        val oldXmp = segments.firstOrNull { it.marker == APP1 && it.payloadStarts(namespace) }
        val mpf = segments.firstOrNull { it.marker == APP2 && it.payloadStarts(MPF) }

        // The packet: the old one with the marks when it names a gain map, else ours.
        val text = oldXmp
            ?.let { String(bytes, it.start + 4 + namespace.size, it.length - 2 - namespace.size, Charsets.UTF_8) }
            ?.takeIf { it.contains(GAIN_MAP_NAMESPACE) }
            ?.let { old -> (merge ?: return false)(old) ?: return false }
            ?: xmp
        val payload = (NAMESPACE + text).toByteArray(Charsets.UTF_8)
        if (payload.size + 2 > 0xFFFF) return false

        val out = ByteArrayOutputStream(bytes.size + payload.size + 4)
        out.write(bytes, 0, 2)
        var written = false
        var mpfAt = -1
        fun writeOurs() {
            if (written) return
            out.write(0xFF)
            out.write(APP1)
            out.write(bigShort(payload.size + 2))
            out.write(payload)
            written = true
        }
        for (s in segments) {
            val isXmp = s === oldXmp || (s.marker == APP1 && s.payloadStarts(namespace))
            // Ours goes where the old one was, or right after the leading JFIF and EXIF segments.
            if (s === oldXmp || (oldXmp == null && s.marker != APP0 && !(s.marker == APP1 && !isXmp))) writeOurs()
            if (isXmp) continue
            if (s === mpf) mpfAt = out.size()
            out.write(bytes, s.start, 2 + s.length)
        }
        writeOurs()
        out.write(bytes, body, bytes.size - body)
        val result = out.toByteArray()
        if (mpf != null && !fixIndex(result, mpfAt, mpfShift = mpfAt - mpf.start, delta = result.size - bytes.size)) return false
        file.writeBytes(result)
        return true
    }

    private val MPF = byteArrayOf(0x4D, 0x50, 0x46, 0x00)

    /**
     * The MPF index at [at] (in [b], the new file) set right: the first
     * picture grew by [delta], the others moved by [delta] in the file and by
     * [delta] - [mpfShift] from the index (their offsets count from it).
     */
    private fun fixIndex(b: ByteArray, at: Int, mpfShift: Int, delta: Int): Boolean {
        val tiff = at + 4 + MPF.size
        if (tiff + 8 > b.size) return false
        val little = when {
            b[tiff] == 0x49.toByte() && b[tiff + 1] == 0x49.toByte() -> true
            b[tiff] == 0x4D.toByte() && b[tiff + 1] == 0x4D.toByte() -> false
            else -> return false
        }
        fun u16(i: Int) = if (little) (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) else ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = if (little) (u16(i).toLong() or (u16(i + 2).toLong() shl 16)) else ((u16(i).toLong() shl 16) or u16(i + 2).toLong())
        fun put32(i: Int, v: Long) {
            for (k in 0 until 4) b[i + k] = (v shr (if (little) 8 * k else 8 * (3 - k))).toByte()
        }
        val ifd = tiff + u32(tiff + 4).toInt()
        if (ifd + 2 > b.size) return false
        val count = u16(ifd)
        for (e in 0 until count) {
            val entry = ifd + 2 + 12 * e
            if (entry + 12 > b.size) return false
            if (u16(entry) != 0xB002) continue
            val pictures = (u32(entry + 4) / 16).toInt()
            val list = tiff + u32(entry + 8).toInt()
            if (pictures < 1 || list + 16 * pictures > b.size) return false
            put32(list + 4, u32(list + 4) + delta)
            for (p in 1 until pictures) {
                val offset = u32(list + 16 * p + 8)
                if (offset != 0L) put32(list + 16 * p + 8, offset + delta - mpfShift)
            }
            return true
        }
        return false
    }
}

internal fun bigInt(b: ByteArray, at: Int): Int =
    ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

internal fun bigBytes(v: Int): ByteArray = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

private fun bigShort(v: Int): ByteArray = byteArrayOf((v shr 8).toByte(), v.toByte())

internal fun littleInt(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

internal fun littleBytes(v: Int): ByteArray = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
