package com.cuscus.ffmpeggui.core.provenance

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import java.io.File
import java.io.IOException

/**
 * How the AI marks get into a finished file, by its extension (the file a
 * command typed by hand wrote may not be of the project's format). See
 * ConversionEngine: the marks are written after FFmpeg, so neither the
 * metadata settings nor a command can leave them out. Pure, tested.
 */
object AiSeal {

    enum class Way {
        /** An XMP uuid box (MP4, M4A, MOV...). */
        MP4,

        /** An XMP chunk of the RIFF (WAV, AVI). */
        RIFF,

        /** An XMP iTXt chunk. */
        PNG,

        /** An XMP APP1 segment. */
        JPEG,

        /** An XMP chunk, flagged in VP8X. */
        WEBP,

        /** An XMP application extension. */
        GIF,

        /** The XMP tag of the first directory. */
        TIFF,

        /** XMP in <metadata>. */
        SVG,

        /** The container's own tags, written again by a stream-copy pass (Matroska, MP3, FLAC, Ogg). */
        TAGS,

        /**
         * No place for them that programs read (BMP, MPEG-TS, MPEG-PS, a
         * format this does not know): such a file is not written.
         */
        NONE,
    }

    fun wayFor(path: String): Way = when (extensionOf(path)) {
        "mp4", "m4a", "m4v", "m4b", "mov", "3gp", "3g2" -> Way.MP4
        "wav", "avi" -> Way.RIFF
        "png" -> Way.PNG
        "jpg", "jpeg" -> Way.JPEG
        "webp" -> Way.WEBP
        "gif" -> Way.GIF
        "tif", "tiff" -> Way.TIFF
        "svg" -> Way.SVG
        "mkv", "mka", "webm", "mp3", "flac", "ogg", "oga", "opus" -> Way.TAGS
        else -> Way.NONE
    }

    /**
     * What of [all] a file at [path] can hold, by its kind: sound alone
     * (speech), pictures alone, or text (nothing: what is said written down
     * is not what the Act asks to mark). Anything else may hold both.
     */
    fun disclosureFor(path: String, all: AiDisclosure): AiDisclosure = when (extensionOf(path)) {
        in SOUND -> all.copy(pictures = false)
        in PICTURES -> all.copy(speech = false)
        in TEXT -> AiDisclosure()
        else -> all
    }

    /** The tags that say what a machine made, for a command writing [extension]. */
    fun tagArguments(disclosure: AiDisclosure, extension: String): List<String> = buildList {
        if (wayFor("x.$extension") == Way.MP4) addAll(listOf("-movflags", "+use_metadata_tags"))
        addAll(listOf("-metadata", "$AI_GENERATED_TAG=${disclosure.text}"))
        addAll(listOf("-metadata", "$DIGITAL_SOURCE_TAG=${AiDisclosure.COMPOSITE}"))
    }

    /**
     * A stream copy of [input] into [output] (of the same kind) with its tags and
     * the AI ones over them: what a command typed by hand may have left out.
     */
    fun remux(input: String, output: String, disclosure: AiDisclosure): FfmpegCommand = FfmpegCommand(
        listOf("-hide_banner", "-nostdin", "-y", "-i", input, "-map", "0", "-c", "copy", "-map_metadata", "0") +
            tagArguments(disclosure, extensionOf(output)) +
            output,
    )

    /** What [mark] did. */
    enum class Marked {
        /** The XMP is in the file. */
        WRITTEN,

        /** A format whose marks are its tags ([Way.TAGS]): they are FFmpeg's to write. */
        TAGS_ONLY,

        /** The format has no place for them ([Way.NONE]). */
        NO_PLACE,

        /** The file is not what its name says, or could not be written. */
        FAILED,
    }

    /** Writes the XMP that says [disclosure] into [file], where its format keeps it. */
    fun mark(file: File, disclosure: AiDisclosure, sourceType: String = AiDisclosure.COMPOSITE): Marked {
        val xmp = Xmp.packet(disclosure, sourceType)
        val written = try {
            when (wayFor(file.name)) {
                Way.MP4 -> Mp4Xmp.write(file, xmp)
                Way.RIFF -> RiffXmp.write(file, xmp)
                Way.PNG -> PngXmp.write(file, xmp)
                Way.JPEG -> JpegXmp.write(file, xmp) { old -> Xmp.mergeInto(old, disclosure, sourceType) }
                Way.WEBP -> WebpXmp.write(file, xmp)
                Way.GIF -> GifXmp.write(file, xmp)
                Way.TIFF -> TiffXmp.write(file, xmp)
                Way.SVG -> SvgXmp.write(file, xmp)
                Way.TAGS -> return Marked.TAGS_ONLY
                Way.NONE -> return Marked.NO_PLACE
            }
        } catch (e: IOException) {
            false
        }
        return if (written) Marked.WRITTEN else Marked.FAILED
    }

    private fun extensionOf(path: String): String = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    private val SOUND = setOf("mp3", "m4a", "m4b", "wav", "flac", "ogg", "oga", "opus", "mka", "aac", "ac3", "wma", "aiff", "aif", "amr")
    private val PICTURES = setOf("jpg", "jpeg", "png", "webp", "gif", "tif", "tiff", "svg", "bmp", "avif", "heic", "jxl")
    private val TEXT = setOf("txt", "srt", "vtt", "ass", "ssa", "lrc", "json", "csv", "xml", "sub")

    /** The tags that say a file holds what a machine made (read by programs, not a standard name). */
    const val AI_GENERATED_TAG = "AI_GENERATED"
    const val DIGITAL_SOURCE_TAG = "DIGITAL_SOURCE_TYPE"
}
