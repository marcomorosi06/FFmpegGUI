package com.cuscus.ffmpeggui.core.hdr

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Ultra HDR JPG files (Android's "Ultra HDR image format v1"): an ordinary JPG,
 * which every viewer shows, followed by a second JPG, the gain map. The first
 * one says so twice: in its XMP (a container directory naming the gain map and
 * its length, "hdrgm:Version") and in a Multi-Picture Format index (where the
 * second picture starts). The gain map's own XMP holds how its values are read
 * ([GainMapMetadata], "hdrgm" names).
 *
 * [assemble] writes one, [read] finds the gain map of one (a photo from an
 * Ultra HDR camera).
 */
object UltraHdr {

    /** A gain map found in a file: its JPG and how its values are read. */
    class Embedded(val gainMapJpeg: ByteArray, val metadata: GainMapMetadata)

    private const val XMP_NAMESPACE = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val HDRGM = "http://ns.adobe.com/hdr-gain-map/1.0/"

    // JPG markers.
    private const val SOI = 0xD8
    private const val SOS = 0xDA
    private const val EOI = 0xD9
    private const val APP0 = 0xE0
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2

    /**
     * The Ultra HDR file of [primary] (a JPG, the SDR picture, tags included)
     * and [gainMap] (a JPG of the gain map's values) read by [metadata].
     */
    fun assemble(primary: ByteArray, gainMap: ByteArray, metadata: GainMapMetadata): ByteArray {
        val base = segments(primary) ?: throw IllegalArgumentException("The picture is not a JPG")
        val map = segments(gainMap) ?: throw IllegalArgumentException("The gain map is not a JPG")

        // The gain map: its XMP right after its start, everything else as it was (its own XMP and index left out).
        val second = ByteArrayOutputStream()
        second.write(byteArrayOf(0xFF.toByte(), SOI.toByte()))
        second.write(segment(APP1, (XMP_NAMESPACE + gainMapXmp(metadata)).toByteArray(Charsets.UTF_8)))
        writeKept(second, gainMap, map)
        val secondBytes = second.toByteArray()

        // The picture: SOI, its JFIF and EXIF, then its XMP and the index, then the rest.
        val xmp = segment(APP1, (XMP_NAMESPACE + primaryXmp(secondBytes.size)).toByteArray(Charsets.UTF_8))
        val leading = base.headers.takeWhile { it.marker == APP0 || (it.marker == APP1 && !isXmp(primary, it)) }
        val leadingBytes = leading.sumOf { it.length }
        val restLength = base.headers.filter { it !in leading && !dropped(primary, it) }.sumOf { it.length } + (primary.size - base.bodyStart)
        val primaryLength = 2 + leadingBytes + xmp.size + MPF_SEGMENT_LENGTH + restLength
        // Offsets of the index count from its byte order mark.
        val indexStart = 2 + leadingBytes + xmp.size + 4 + 4
        val mpf = mpfSegment(primaryLength, secondBytes.size, primaryLength - indexStart)

        val out = ByteArrayOutputStream(primaryLength + secondBytes.size)
        out.write(byteArrayOf(0xFF.toByte(), SOI.toByte()))
        for (s in leading) out.write(primary, s.start, s.length)
        out.write(xmp)
        out.write(mpf)
        for (s in base.headers) if (s !in leading && !dropped(primary, s)) out.write(primary, s.start, s.length)
        out.write(primary, base.bodyStart, primary.size - base.bodyStart)
        check(out.size() == primaryLength) { "Ultra HDR layout: ${out.size()} != $primaryLength" }
        out.write(secondBytes)
        return out.toByteArray()
    }

    /** The gain map of the Ultra HDR file [file]; null when it has none (or one this cannot read). */
    fun read(file: ByteArray): Embedded? {
        val base = segments(file) ?: return null
        val gainMap = fromIndex(file, base) ?: fromDirectory(file, base) ?: return null
        val map = segments(gainMap) ?: return null
        val xmp = map.headers.firstOrNull { it.marker == APP1 && isXmp(gainMap, it) } ?: return null
        val text = String(gainMap, xmp.start + 4 + XMP_NAMESPACE.length, xmp.length - 4 - XMP_NAMESPACE.length, Charsets.UTF_8)
        val metadata = parseGainMapXmp(text) ?: return null
        return Embedded(gainMap, metadata)
    }

    /** The "hdrgm" values of a gain map's XMP; null without them or for an HDR base picture. */
    internal fun parseGainMapXmp(xmp: String): GainMapMetadata? {
        if (!xmp.contains(HDRGM) || value(xmp, "Version") == null) return null
        if (value(xmp, "BaseRenditionIsHDR")?.trim()?.equals("True", ignoreCase = true) == true) return null
        fun number(name: String, default: Float?): Float? {
            val raw = value(xmp, name) ?: return default
            val numbers = Regex("-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?").findAll(raw).mapNotNull { it.value.toFloatOrNull() }.toList()
            return if (numbers.isEmpty()) default else numbers.average().toFloat()
        }
        val max = number("GainMapMax", null) ?: return null
        val min = number("GainMapMin", 0f) ?: 0f
        val gamma = number("Gamma", 1f) ?: 1f
        val capacityMax = number("HDRCapacityMax", null) ?: return null
        val capacityMin = number("HDRCapacityMin", 0f) ?: 0f
        if (max < min || gamma <= 0f || capacityMax <= capacityMin) return null
        return GainMapMetadata(
            minStops = min,
            maxStops = max,
            gamma = gamma,
            offsetSdr = number("OffsetSDR", GainMapMetadata.DEFAULT_OFFSET) ?: GainMapMetadata.DEFAULT_OFFSET,
            offsetHdr = number("OffsetHDR", GainMapMetadata.DEFAULT_OFFSET) ?: GainMapMetadata.DEFAULT_OFFSET,
            capacityMin = capacityMin,
            capacityMax = capacityMax,
        )
    }

    /** The text of "hdrgm:[name]", as an attribute or an element (its content, a list included). */
    private fun value(xmp: String, name: String): String? {
        Regex("hdrgm:$name\\s*=\\s*\"([^\"]*)\"").find(xmp)?.let { return it.groupValues[1] }
        Regex("<hdrgm:$name>(.*?)</hdrgm:$name>", RegexOption.DOT_MATCHES_ALL).find(xmp)?.let { found ->
            return Regex("<[^>]+>").replace(found.groupValues[1], " ")
        }
        return null
    }

    internal fun primaryXmp(gainMapLength: Int): String =
        """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="FFmpegGUI">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:hdrgm="$HDRGM"
        hdrgm:Version="1.0">
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Semantic="GainMap" Item:Mime="image/jpeg" Item:Length="$gainMapLength"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>"""

    internal fun gainMapXmp(m: GainMapMetadata): String {
        fun f(v: Float) = String.format(Locale.ROOT, "%.6f", v)
        return """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="FFmpegGUI">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:hdrgm="$HDRGM"
        hdrgm:Version="1.0"
        hdrgm:GainMapMin="${f(m.minStops)}"
        hdrgm:GainMapMax="${f(m.maxStops)}"
        hdrgm:Gamma="${f(m.gamma)}"
        hdrgm:OffsetSDR="${f(m.offsetSdr)}"
        hdrgm:OffsetHDR="${f(m.offsetHdr)}"
        hdrgm:HDRCapacityMin="${f(m.capacityMin)}"
        hdrgm:HDRCapacityMax="${f(m.capacityMax)}"
        hdrgm:BaseRenditionIsHDR="False"/>
  </rdf:RDF>
</x:xmpmeta>"""
    }

    // -- Reading the JPG's structure ----------------------------------------------------

    /** A marker segment of a JPG: [start] at its 0xFF, [length] bytes in all (marker included). */
    private class Segment(val marker: Int, val start: Int, val length: Int)

    /** The segments before the picture's data, and where that data starts (its SOS). */
    private class Layout(val headers: List<Segment>, val bodyStart: Int)

    private fun segments(jpeg: ByteArray): Layout? {
        if (jpeg.size < 4 || u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != SOI) return null
        val headers = mutableListOf<Segment>()
        var at = 2
        while (at + 4 <= jpeg.size) {
            if (u8(jpeg, at) != 0xFF) return null
            var marker = u8(jpeg, at + 1)
            // Fill bytes before a marker.
            while (marker == 0xFF && at + 2 < jpeg.size) {
                at++
                marker = u8(jpeg, at + 1)
            }
            if (marker == SOS) return Layout(headers, at)
            if (marker == EOI) return null
            val length = u16(jpeg, at + 2)
            if (length < 2 || at + 2 + length > jpeg.size) return null
            headers += Segment(marker, at, 2 + length)
            at += 2 + length
        }
        return null
    }

    private fun isXmp(jpeg: ByteArray, s: Segment): Boolean = startsWith(jpeg, s.start + 4, XMP_NAMESPACE)

    private fun isMpf(jpeg: ByteArray, s: Segment): Boolean = s.marker == APP2 && startsWith(jpeg, s.start + 4, "MPF\u0000")

    /** Segments of the picture the Ultra HDR one writes anew: its XMP and its index. */
    private fun dropped(jpeg: ByteArray, s: Segment): Boolean = (s.marker == APP1 && isXmp(jpeg, s)) || isMpf(jpeg, s)

    private fun writeKept(out: ByteArrayOutputStream, jpeg: ByteArray, layout: Layout) {
        for (s in layout.headers) if (!dropped(jpeg, s)) out.write(jpeg, s.start, s.length)
        out.write(jpeg, layout.bodyStart, jpeg.size - layout.bodyStart)
    }

    /** The second picture the Multi-Picture Format index points to. */
    private fun fromIndex(file: ByteArray, layout: Layout): ByteArray? {
        val mpf = layout.headers.firstOrNull { isMpf(file, it) } ?: return null
        val tiff = mpf.start + 8
        val end = mpf.start + mpf.length
        if (tiff + 8 > end) return null
        val big = when {
            u8(file, tiff) == 0x4D && u8(file, tiff + 1) == 0x4D -> true
            u8(file, tiff) == 0x49 && u8(file, tiff + 1) == 0x49 -> false
            else -> return null
        }
        fun u16(at: Int) = if (big) u16(file, at) else (u8(file, at) or (u8(file, at + 1) shl 8))
        fun u32(at: Int): Long = if (big) {
            (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        } else {
            u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        }
        val ifd = tiff + u32(tiff + 4).toInt()
        if (ifd + 2 > end) return null
        val count = u16(ifd)
        var entries = -1
        var entryCount = 0
        for (k in 0 until count) {
            val e = ifd + 2 + 12 * k
            if (e + 12 > end) return null
            if (u16(e) == 0xB002) {
                entryCount = (u32(e + 4) / 16).toInt()
                entries = tiff + u32(e + 8).toInt()
            }
        }
        if (entries < 0 || entryCount < 2 || entries + 32 > end) return null
        val second = entries + 16
        val size = u32(second + 4)
        val offset = u32(second + 8)
        val start = tiff + offset
        if (size <= 0 || offset <= 0 || start + size > file.size) return null
        return file.copyOfRange(start.toInt(), (start + size).toInt())
    }

    /** The gain map named last in the XMP container directory: the last bytes of the file. */
    private fun fromDirectory(file: ByteArray, layout: Layout): ByteArray? {
        val xmp = layout.headers.firstOrNull { it.marker == APP1 && isXmp(file, it) } ?: return null
        val text = String(file, xmp.start + 4 + XMP_NAMESPACE.length, xmp.length - 4 - XMP_NAMESPACE.length, Charsets.UTF_8)
        val items = Regex("<Container:Item\\b[^>]*>").findAll(text).map { it.value }.toList()
        val last = items.lastOrNull() ?: return null
        if (!last.contains("Semantic=\"GainMap\"")) return null
        val length = Regex("Item:Length=\"(\\d+)\"").find(last)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (length <= 0 || length >= file.size) return null
        return file.copyOfRange(file.size - length, file.size)
    }

    // -- Writing ---------------------------------------------------------------------------

    /** A marker segment holding [payload]. */
    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        require(payload.size + 2 <= 0xFFFF) { "Segment too long" }
        val out = ByteArray(payload.size + 4)
        out[0] = 0xFF.toByte()
        out[1] = marker.toByte()
        out[2] = ((payload.size + 2) shr 8).toByte()
        out[3] = (payload.size + 2).toByte()
        payload.copyInto(out, 4)
        return out
    }

    /** Bytes of the index segment (marker included). */
    private const val MPF_SEGMENT_LENGTH = 2 + 2 + 4 + 8 + 2 + 3 * 12 + 4 + 2 * 16

    /** The Multi-Picture Format index: the picture ([primaryLength] bytes) and the gain map, [secondOffset] from the index's start. */
    private fun mpfSegment(primaryLength: Int, secondLength: Int, secondOffset: Int): ByteArray {
        val p = ByteArrayOutputStream()
        fun u16(v: Int) { p.write((v shr 8) and 0xFF); p.write(v and 0xFF) }
        fun u32(v: Long) { u16(((v shr 16) and 0xFFFF).toInt()); u16((v and 0xFFFF).toInt()) }
        p.write("MPF".toByteArray(Charsets.US_ASCII)); p.write(0)
        // Big-endian header, the index right after it.
        p.write(0x4D); p.write(0x4D); u16(0x002A); u32(8)
        u16(3)
        // Version "0100".
        u16(0xB000); u16(7); u32(4); p.write("0100".toByteArray(Charsets.US_ASCII))
        // Two pictures.
        u16(0xB001); u16(4); u32(1); u32(2)
        // The entries, after this index and its next-index offset.
        u16(0xB002); u16(7); u32(32); u32((8 + 2 + 3 * 12 + 4).toLong())
        u32(0)
        // The picture: a baseline primary picture, at the start.
        u32(0x030000); u32(primaryLength.toLong()); u32(0); u16(0); u16(0)
        // The gain map.
        u32(0); u32(secondLength.toLong()); u32(secondOffset.toLong()); u16(0); u16(0)
        val bytes = segment(APP2, p.toByteArray())
        check(bytes.size == MPF_SEGMENT_LENGTH) { "Index of ${bytes.size} bytes" }
        return bytes
    }

    private fun u8(b: ByteArray, at: Int): Int = b[at].toInt() and 0xFF

    private fun u16(b: ByteArray, at: Int): Int = (u8(b, at) shl 8) or u8(b, at + 1)

    private fun startsWith(b: ByteArray, at: Int, text: String): Boolean {
        if (at + text.length > b.size) return false
        for (k in text.indices) if (b[at + k] != text[k].code.toByte()) return false
        return true
    }
}
