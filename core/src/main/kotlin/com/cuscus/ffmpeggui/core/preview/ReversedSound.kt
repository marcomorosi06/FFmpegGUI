package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.toneMapFilters
import com.cuscus.ffmpeggui.core.model.VideoHdr
import java.util.Locale

/*
 * A clip played backwards, for the preview. A player can only go forwards:
 * stepped back frame by frame the picture showed about ten frames a second,
 * and the sound cannot be stepped at all. Instead FFmpeg writes the clip's
 * part of its file reversed, picture and sound, into a file of its own,
 * which the preview plays forwards in the clip's place while it goes
 * backwards. The file the user opened is never touched; the export still
 * reverses the clip itself.
 *
 * FFmpeg's reverse and areverse keep everything they reverse in memory, so:
 *  - the picture is made small ([PROXY_SIDE] on its short side, [PROXY_FPS]
 *    frames a second: it is only shown in the editor) and reversed in pieces
 *    of [CHUNK_MS], the last piece of the part first, then the pieces are
 *    joined without being encoded again;
 *  - the sound (light: a minute is a few tens of MB) is reversed in one go,
 *    so that no seam is heard, and laid under the joined picture.
 * Every piece is cut before the input (-ss and -t): only it is decoded.
 * Pure, tested.
 */

/**
 * The part of a file wanted reversed: [startMs] to [endMs] of [sourceUri]'s
 * own clock, its picture ([video], tone mapped first when [hdr]) and/or its
 * sound ([audio]).
 */
data class ReversedSoundRequest(
    val sourceUri: String,
    val startMs: Long,
    val endMs: Long,
    val video: Boolean = false,
    val audio: Boolean = true,
    val hdr: VideoHdr = VideoHdr.NONE,
) {
    val lengthMs: Long get() = endMs - startMs
}

object ReversedSound {

    /**
     * What a clip from [startMs] to [endMs] (null: to the end of the file,
     * [sourceDurationMs] long, 0 when not known yet) needs reversed; null when
     * its end is not known yet, nothing of it is left, or it has neither
     * picture nor sound.
     */
    fun requestFor(
        sourceUri: String,
        startMs: Long,
        endMs: Long?,
        sourceDurationMs: Long,
        video: Boolean = false,
        audio: Boolean = true,
        hdr: VideoHdr = VideoHdr.NONE,
    ): ReversedSoundRequest? {
        if (!video && !audio) return null
        val start = startMs.coerceAtLeast(0L)
        val end = endMs ?: sourceDurationMs.takeIf { it > 0L } ?: return null
        if (end - start < MIN_LENGTH_MS) return null
        return ReversedSoundRequest(sourceUri, start, end, video, audio, if (video) hdr else VideoHdr.NONE)
    }

    /** Where the reversed file is while the clip's footage shows [fileMs] (the file's own clock). */
    fun reversedMs(request: ReversedSoundRequest, fileMs: Long): Long =
        (request.endMs - fileMs).coerceIn(0L, request.lengthMs)

    /** FFmpeg writing [request]'s sound reversed, from [input] (a path), into [output] (AAC in .m4a). */
    fun command(request: ReversedSoundRequest, input: String, output: String): FfmpegCommand = FfmpegCommand(
        listOf("-y", "-nostdin", "-hide_banner") +
            cut(request.startMs, request.lengthMs) +
            listOf("-i", input, "-vn", "-sn", "-dn", "-af", "areverse", "-c:a", "aac", "-b:a", AUDIO_BITRATE, output),
    )

    /**
     * The pieces the picture is reversed in, as (start, length) on the file's
     * clock, in the order they are joined: the last piece of the part first.
     */
    fun pieces(request: ReversedSoundRequest): List<Pair<Long, Long>> {
        val pieces = ArrayList<Pair<Long, Long>>()
        var end = request.endMs
        while (end > request.startMs) {
            val start = maxOf(request.startMs, end - CHUNK_MS)
            // A sliver left at the start goes with the piece before it.
            val from = if (start - request.startMs in 1 until MIN_LENGTH_MS) request.startMs else start
            pieces += from to (end - from)
            end = from
        }
        return pieces
    }

    /**
     * FFmpeg writing the picture of the piece [startMs] + [lengthMs] of
     * [input] reversed, small (see the notes), without sound, into [output] (.mp4).
     */
    fun pieceCommand(request: ReversedSoundRequest, startMs: Long, lengthMs: Long, input: String, output: String): FfmpegCommand {
        val filters = toneMapFilters(request.hdr) + listOf(PROXY_SCALE, "fps=$PROXY_FPS", "reverse", "format=yuv420p")
        return FfmpegCommand(
            listOf("-y", "-nostdin", "-hide_banner") +
                cut(startMs, lengthMs) +
                listOf(
                    "-i", input,
                    "-an", "-sn", "-dn",
                    "-vf", filters.joinToString(","),
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", PROXY_CRF,
                    output,
                ),
        )
    }

    /** The list file of FFmpeg's concat demuxer joining [paths] in that order. */
    fun joinList(paths: List<String>): String =
        paths.joinToString(separator = "\n", postfix = "\n") { "file '${it.replace("'", "'\\''")}'" }

    /** FFmpeg joining the pieces listed in [list] (see [joinList]) into [output], as they are. */
    fun joinCommand(list: String, output: String): FfmpegCommand = FfmpegCommand(
        listOf("-y", "-nostdin", "-hide_banner", "-f", "concat", "-safe", "0", "-i", list, "-c", "copy", output),
    )

    /** FFmpeg laying the reversed [sound] under the reversed [picture], as they are, into [output]. */
    fun layCommand(picture: String, sound: String, output: String): FfmpegCommand = FfmpegCommand(
        listOf(
            "-y", "-nostdin", "-hide_banner",
            "-i", picture, "-i", sound,
            "-map", "0:v:0", "-map", "1:a:0",
            "-c", "copy",
            output,
        ),
    )

    /**
     * The name of [request]'s reversed file: the same part of the same file gets
     * the same name, so a file made once is played again without FFmpeg.
     */
    fun fileName(request: ReversedSoundRequest): String {
        val key = "${request.sourceUri}|${request.startMs}|${request.endMs}|${request.video}|${request.audio}|${request.hdr}"
        val extension = if (request.video) "mp4" else "m4a"
        return "reversed_%016x.%s".format(Locale.ROOT, stableHash(key), extension)
    }

    /** -ss and -t before the input: only that piece is decoded. */
    private fun cut(startMs: Long, lengthMs: Long): List<String> = listOf("-ss", seconds(startMs), "-t", seconds(lengthMs))

    /** 64-bit FNV-1a: the same everywhere and from one run to the next. */
    private fun stableHash(text: String): Long {
        var hash = FNV_OFFSET
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xff)
            hash *= FNV_PRIME
        }
        return hash
    }

    private fun seconds(ms: Long): String = "%.3f".format(Locale.ROOT, ms / 1000.0)

    /** Shorter than this, a clip is not worth a run of FFmpeg. */
    const val MIN_LENGTH_MS = 50L

    /** The pieces the picture is reversed in: at the proxy's size, a few tens of MB of memory each. */
    const val CHUNK_MS = 3_000L

    /** The short side of the reversed picture, and its frames a second. */
    const val PROXY_SIDE = 480
    const val PROXY_FPS = 30

    /** The short side down to [PROXY_SIDE] (never up), the long one following, both even. */
    private const val PROXY_SCALE =
        "scale='if(gt(iw,ih),-2,min($PROXY_SIDE,iw))':'if(gt(iw,ih),min($PROXY_SIDE,ih),-2)'"
    private const val PROXY_CRF = "26"
    private const val AUDIO_BITRATE = "192k"
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
}
