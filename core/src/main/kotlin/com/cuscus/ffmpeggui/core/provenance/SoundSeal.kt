package com.cuscus.ffmpeggui.core.provenance

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import java.io.File

/**
 * The invisible mark on a finished file's whole sound (EU AI Act, Article
 * 50(2)): a dub carries AudioSeal's mark from the start, but mixed with music
 * and effects the mark sinks under them (a test: 4 s of 14 found, with the
 * wrong message; under 1.5 s with louder music), so the sound of an export
 * that holds a dub is marked again, all of it, once mixed (then found at
 * 100 %, after AAC too). The engine takes the sound out ([decode]), the
 * [SoundSealer] marks it, and [remux] puts it back in place of the first
 * sound, everything else copied. Pure, tested.
 */
object SoundSeal {

    /**
     * Marks the sound of a WAV (32-bit float, any rate and channels) into
     * another, the same but for the mark, saying how far it is (0..1) as it goes.
     */
    interface SoundSealer {
        suspend fun seal(input: File, output: File, onProgress: (Float) -> Unit)
    }

    /** The first sound of [path] as a 32-bit float WAV at [wav], at its own rate and channels. */
    fun decode(path: String, wav: String): FfmpegCommand = FfmpegCommand(
        listOf("-y", "-i", path, "-map", "0:a:0", "-vn", "-sn", "-dn", "-c:a", "pcm_f32le", "-f", "wav", wav),
    )

    /**
     * [path] again at [output], its first sound taken from [wav] and encoded
     * by [codec] (see [codecFor]); every other stream, the tags and the
     * chapters copied as they are.
     */
    fun remux(path: String, wav: String, output: String, codec: List<String>): FfmpegCommand {
        val extension = output.substringAfterLast('.', "").lowercase()
        val args = mutableListOf(
            "-y", "-i", path, "-i", wav,
            "-map", "0", "-map", "-0:a:0", "-map", "1:a:0",
            "-map_metadata", "0", "-map_chapters", "0",
            "-c", "copy",
        )
        args += codec
        if (extension in FAST_START) args += listOf("-movflags", "+faststart")
        args += output
        return FfmpegCommand(args)
    }

    /**
     * How the marked sound is written back into a file with [extension]: the
     * codec its sound has (what the app writes there), at [audio]'s bitrate,
     * a little more for a video's AAC (it is encoded a second time); null when
     * such a file holds no sound the mark can go in.
     */
    fun codecFor(extension: String, audio: AudioEncoding): List<String>? {
        val kbps = audio.bitrateKbps
        return when (extension.lowercase()) {
            "mp4", "mov", "mkv", "webm" -> if (extension.lowercase() == "webm") listOf("-c:a", "libopus", "-b:a", "160k") else listOf("-c:a", "aac", "-b:a", "${VIDEO_AAC_KBPS}k")
            "m4a" -> listOf("-c:a", "aac", "-b:a", "${kbps.coerceIn(32, 320)}k")
            "avi" -> listOf("-c:a", "libmp3lame", "-b:a", "${VIDEO_AAC_KBPS}k")
            "mp3" -> listOf("-c:a", "libmp3lame", "-b:a", "${kbps.coerceIn(32, 320)}k")
            "ogg", "oga" -> listOf("-c:a", "libvorbis", "-b:a", "${kbps.coerceIn(64, 320)}k")
            "opus" -> listOf("-c:a", "libopus", "-b:a", "${kbps.coerceIn(16, 256)}k")
            "flac" -> listOf("-c:a", "flac") + if (audio.bitDepth >= 24) listOf("-sample_fmt", "s32", "-bits_per_raw_sample", "24") else listOf("-sample_fmt", "s16")
            "wav" -> listOf("-c:a", if (audio.bitDepth >= 24) "pcm_s24le" else "pcm_s16le")
            else -> null
        }
    }

    /** A video's AAC written again: a little above the 128 kbit/s of the first time. */
    private const val VIDEO_AAC_KBPS = 192

    private val FAST_START = setOf("mp4", "mov", "m4a")
}
