package com.cuscus.ffmpeggui.core.provenance

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Reads the AI marks of a picture opened in the app: the magic eraser's own
 * results (see Xmp.packet), wherever they were copied, and pictures other
 * programs marked as made with a trained model (whether, nothing more). What it finds goes into the
 * marks of what is exported from them, so that they are never lost on the way
 * (EU AI Act, Article 50(2)). Pure, tested.
 */
object AiMarksReader {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val PNG_KEYWORD = "XML:com.adobe.xmp"
    private const val JPEG_NAMESPACE = "http://ns.adobe.com/xap/1.0/\u0000"

    /** Larger than any XMP of a picture: a chunk past it is not read. */
    private const val MOST = 4 shl 20

    /** The XMP of the PNG or JPEG in [input], or null (another format, or none). */
    fun xmp(input: InputStream): String? = try {
        val data = DataInputStream(input)
        val head = ByteArray(2)
        data.readFully(head)
        when {
            head[0] == PNG_SIGNATURE[0] && head[1] == PNG_SIGNATURE[1] -> png(data)
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xFF) == 0xD8 -> jpeg(data)
            else -> null
        }
    } catch (e: EOFException) {
        null
    }

    /** True when [xmp] says the picture was made or changed by a trained model (IPTC's Digital Source Type). */
    fun changedWithAi(xmp: String): Boolean = isAi(sourceType(xmp))

    /** IPTC's Digital Source Type [xmp] gives, or null. */
    fun sourceType(xmp: String): String? =
        (
            Regex("Iptc4xmpExt:DigitalSourceType\\s*=\\s*\"([^\"]*)\"").find(xmp)?.groupValues?.get(1)
                ?: Regex("<Iptc4xmpExt:DigitalSourceType(?:\\s[^>]*)?>([^<]*)<").find(xmp)?.groupValues?.get(1)
            )?.trim()

    /** True when [sourceType] is one of a trained model's: made with one, or changed with one. */
    fun isAi(sourceType: String?): Boolean = sourceType == AiDisclosure.COMPOSITE || sourceType == AiDisclosure.TRAINED

    /**
     * What a file's metadata says: [hasXmp] when it has any XMP, [sourceType]
     * the first Digital Source Type of a trained model found (else the first
     * found at all, else null), [inTags] when that type was found in the
     * container's own tags rather than in XMP (Matroska, MP3, FLAC, Ogg), and
     * [voice] when the file says a voice in it was made by AI.
     */
    class Found(val hasXmp: Boolean, val sourceType: String?, val inTags: Boolean = false, val voice: Boolean = false) {
        val ai: Boolean get() = isAi(sourceType)
    }

    /**
     * The AI marks anywhere in [bytes] (a whole file of any format, or its
     * head and tail): XMP packets (JPEG, PNG, WebP, GIF, TIFF, SVG, MP4, WAV...),
     * kept as plain text by every format the app marks, so they are found by
     * their wrapper rather than by walking each format's structure; failing
     * that, a trained model's Digital Source Type alone, as the container's
     * own tags hold it (DIGITAL_SOURCE_TYPE, in plain text too).
     */
    fun scan(bytes: ByteArray): Found {
        val text = String(bytes, Charsets.ISO_8859_1)
        val voice = text.contains(AiDisclosure.SPEECH_TEXT)
        var from = 0
        var any = false
        var first: String? = null
        while (true) {
            val start = text.indexOf(XMP_OPEN, from)
            if (start < 0) break
            val close = text.indexOf(XMP_CLOSE, start)
            if (close < 0) break
            val end = close + XMP_CLOSE.length
            any = true
            val type = sourceType(String(bytes, start, end - start, Charsets.UTF_8))
            if (isAi(type)) return Found(true, type, voice = voice)
            if (first == null) first = type
            from = end
        }
        val tagged = listOf(AiDisclosure.TRAINED, AiDisclosure.COMPOSITE).firstOrNull { text.contains(it) }
        if (tagged != null) return Found(any, tagged, inTags = true, voice = voice)
        return Found(any, first)
    }

    private const val XMP_OPEN = "<x:xmpmeta"
    private const val XMP_CLOSE = "</x:xmpmeta>"

    private fun png(data: DataInputStream): String? {
        val rest = ByteArray(6)
        data.readFully(rest)
        if (!rest.contentEquals(PNG_SIGNATURE.copyOfRange(2, 8))) return null
        val key = (PNG_KEYWORD + "\u0000").toByteArray(Charsets.ISO_8859_1)
        while (true) {
            val length = data.readInt()
            val type = ByteArray(4).also { data.readFully(it) }.toString(Charsets.US_ASCII)
            if (length < 0) return null
            if (type == "IEND") return null
            if (type == "iTXt" && length >= key.size + 3 && length <= MOST) {
                val chunk = ByteArray(length).also { data.readFully(it) }
                data.skipFully(4)
                if (!chunk.copyOfRange(0, key.size).contentEquals(key)) continue
                // Not compressed, then a language and a translated keyword, each ended by a zero.
                if (chunk[key.size] != 0.toByte()) return null
                var at = key.size + 2
                repeat(2) {
                    while (at < chunk.size && chunk[at] != 0.toByte()) at++
                    at++
                }
                return if (at <= chunk.size) String(chunk, at, chunk.size - at, Charsets.UTF_8) else null
            }
            data.skipFully(length.toLong() + 4)
        }
    }

    private fun jpeg(data: DataInputStream): String? {
        val namespace = JPEG_NAMESPACE.toByteArray(Charsets.ISO_8859_1)
        while (true) {
            if (data.readUnsignedByte() != 0xFF) return null
            val marker = data.readUnsignedByte()
            // The picture's data begins: no XMP after it.
            if (marker == 0xDA || marker == 0xD9) return null
            val length = data.readUnsignedShort()
            if (length < 2) return null
            if (marker == 0xE1 && length - 2 > namespace.size) {
                val payload = ByteArray(length - 2).also { data.readFully(it) }
                if (payload.copyOfRange(0, namespace.size).contentEquals(namespace)) {
                    return String(payload, namespace.size, payload.size - namespace.size, Charsets.UTF_8)
                }
            } else {
                data.skipFully(length.toLong() - 2)
            }
        }
    }

    private fun DataInputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) throw EOFException()
                left--
            } else {
                left -= skipped
            }
        }
    }
}
