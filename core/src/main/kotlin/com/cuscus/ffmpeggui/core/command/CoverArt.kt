package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.time.segmenting
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

/*
 * The cover picture of an audio file (album art). How it is stored depends on
 * the container, and the bundled FFmpeg can write it in two ways:
 *
 *  - MP3 (ID3 APIC), M4A (covr) and FLAC (PICTURE block) take it as a picture
 *    stream marked "attached_pic", copied from the cover file;
 *  - Ogg Vorbis and Opus cannot carry a picture stream: their tags hold it as
 *    METADATA_BLOCK_PICTURE, a FLAC picture block in base64, which FFmpeg reads
 *    from a small tags file (ffmetadata) made next to the sources;
 *  - WAV holds no cover at all.
 */

/** How an audio file holds its cover. */
enum class CoverWriting {
    /** A picture stream marked as the cover. */
    ATTACHED_PICTURE,

    /** A METADATA_BLOCK_PICTURE tag, read from a tags file. */
    PICTURE_TAG,
}

/** How a file of this format holds a cover, or null when it cannot hold one. */
val OutputFormat.coverWriting: CoverWriting?
    get() = when (this) {
        OutputFormat.MP3, OutputFormat.M4A, OutputFormat.FLAC -> CoverWriting.ATTACHED_PICTURE
        OutputFormat.OGG, OutputFormat.OPUS -> CoverWriting.PICTURE_TAG
        else -> null
    }

/**
 * How the cover of this request is written, or null when it is not: none was
 * set, the format cannot hold one, or frames are extracted. A file cut into
 * parts gets no picture stream (the segment muxer would give it to the first
 * part only); a cover kept in the tags goes into every part.
 */
fun ConversionRequest.coverWriting(): CoverWriting? {
    if (!cover || frameExtraction != FrameExtraction.Disabled) return null
    val writing = format.coverWriting ?: return null
    if (writing == CoverWriting.ATTACHED_PICTURE && segmenting() != null) return null
    return writing
}

/** The tags file that gives an Ogg or Opus file its cover (see [CoverWriting.PICTURE_TAG]). */
object CoverTags {

    /** An ffmetadata file holding [picture] (JPEG or PNG bytes) as METADATA_BLOCK_PICTURE. */
    fun ffmetadata(picture: ByteArray): String {
        val encoded = Base64.getEncoder().encodeToString(pictureBlock(picture))
        // '=', ';', '#', '\' and line breaks are escaped in ffmetadata values; base64 only has '='.
        return ";FFMETADATA1\nMETADATA_BLOCK_PICTURE=" + encoded.replace("=", "\\=") + "\n"
    }

    /**
     * A FLAC picture block: picture type 3 (front cover), MIME type, an empty
     * description, size, colour depth, colour count (0, not indexed) and the data.
     */
    fun pictureBlock(picture: ByteArray): ByteArray {
        val mime = mimeType(picture).toByteArray(Charsets.US_ASCII)
        val (width, height) = pictureSize(picture) ?: (0 to 0)
        val bytes = ByteArrayOutputStream(picture.size + 64)
        DataOutputStream(bytes).use { out ->
            out.writeInt(FRONT_COVER)
            out.writeInt(mime.size)
            out.write(mime)
            out.writeInt(0)
            out.writeInt(width)
            out.writeInt(height)
            out.writeInt(24)
            out.writeInt(0)
            out.writeInt(picture.size)
            out.write(picture)
        }
        return bytes.toByteArray()
    }

    /** The MIME type of [picture]: PNG by its signature, JPEG otherwise. */
    fun mimeType(picture: ByteArray): String = if (isPng(picture)) "image/png" else "image/jpeg"

    /** Width and height of a JPEG or PNG picture, or null when they cannot be read. */
    fun pictureSize(picture: ByteArray): Pair<Int, Int>? {
        if (isPng(picture)) {
            if (picture.size < 24) return null
            return int32(picture, 16) to int32(picture, 20)
        }
        if (picture.size < 4 || u8(picture, 0) != 0xFF || u8(picture, 1) != 0xD8) return null
        var i = 2
        while (i + 3 < picture.size) {
            if (u8(picture, i) != 0xFF) return null
            val marker = u8(picture, i + 1)
            when {
                // Fill bytes before a marker.
                marker == 0xFF -> i++
                // Markers without a length.
                marker == 0x01 || marker in 0xD0..0xD9 -> i += 2
                // Start of frame: every SOFn but DHT (C4), JPG (C8) and DAC (CC).
                marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC -> {
                    if (i + 8 >= picture.size) return null
                    return u16(picture, i + 7) to u16(picture, i + 5)
                }
                else -> i += 2 + u16(picture, i + 2)
            }
        }
        return null
    }

    private fun isPng(picture: ByteArray): Boolean =
        picture.size >= 8 && u8(picture, 0) == 0x89 && u8(picture, 1) == 0x50 && u8(picture, 2) == 0x4E && u8(picture, 3) == 0x47

    private fun u8(bytes: ByteArray, at: Int): Int = bytes[at].toInt() and 0xFF

    private fun u16(bytes: ByteArray, at: Int): Int = (u8(bytes, at) shl 8) or u8(bytes, at + 1)

    private fun int32(bytes: ByteArray, at: Int): Int = (u16(bytes, at) shl 16) or u16(bytes, at + 2)

    private const val FRONT_COVER = 3
}
