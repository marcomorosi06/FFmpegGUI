package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.command.audioDenoiseFilter
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import java.util.Locale

/*
 * The sound's denoise heard before and after (the editor's Noise tool): a few
 * seconds of the clip's sound around the playhead, written twice by FFmpeg,
 * as it is and through the export's own filter (audioDenoiseFilter, anlmdn),
 * so that the panel can play one or the other at the same instant.
 *
 * The preview's live sound cannot run anlmdn: it compares every short patch
 * of the sound with those around it, too much work for every buffer of the
 * player. So the panel makes the comparison where it is asked for, as the
 * video's denoise does with a square of the frame (see DenoiseSample).
 *
 * anlmdn looks a few milliseconds around each sample: a short lead-in before
 * the part heard ([AudioDenoiseSample.LEAD_IN_MS], or from the clip's start if
 * nearer) is decoded and denoised too, then cut away, so that the start of
 * what is heard is denoised as in the export. Both halves come from one decode
 * and one split: they line up sample for sample.
 */

/**
 * What a comparison plays.
 *
 * @param sourceUri   the clip's file.
 * @param positionMs  where the part heard starts, as a position in the file.
 * @param clipStartMs where the clip starts in the file: nothing before it is heard.
 * @param clipEndMs   where the clip ends in the file, or null for its end.
 * @param strengths   the denoise strengths run one after the other (the clip's,
 *                    then the whole video's), as in the export; zeros are skipped.
 */
data class AudioDenoiseSampleRequest(
    val sourceUri: String,
    val positionMs: Long,
    val clipStartMs: Long,
    val clipEndMs: Long?,
    val strengths: List<Float>,
) {
    /** True when some denoise is on: otherwise both halves would sound the same. */
    val denoises: Boolean get() = strengths.any { it > 0f }
}

object AudioDenoiseSample {
    /** How long the part heard is. */
    const val LENGTH_MS = 8_000L

    /** What is decoded before it, and cut away once denoised. */
    const val LEAD_IN_MS = 250L

    /** The rate both halves are written at: what the player plays. */
    const val SAMPLE_RATE = 48_000

    /**
     * Where the part heard starts in the file: at the playhead, moved back so
     * that a whole [LENGTH_MS] fits before the clip's end where it can, never
     * before the clip's start.
     */
    fun startOf(request: AudioDenoiseSampleRequest): Long {
        val end = request.clipEndMs ?: Long.MAX_VALUE
        val latest = (end - LENGTH_MS).coerceAtLeast(request.clipStartMs)
        return request.positionMs.coerceIn(request.clipStartMs, maxOf(request.clipStartMs, latest))
    }

    /** How long the part heard is, the clip's end allowing. */
    fun lengthOf(request: AudioDenoiseSampleRequest): Long {
        val start = startOf(request)
        val end = request.clipEndMs ?: Long.MAX_VALUE
        return minOf(LENGTH_MS, (end - start).coerceAtLeast(0L))
    }

    /**
     * The command that writes the part of [request] read from [inputPath]: as
     * it is into [beforePath], denoised into [afterPath] (both 16-bit WAV,
     * stereo, at [SAMPLE_RATE]).
     */
    fun command(request: AudioDenoiseSampleRequest, inputPath: String, beforePath: String, afterPath: String): FfmpegCommand {
        val start = startOf(request)
        val length = lengthOf(request)
        val leadIn = minOf(LEAD_IN_MS, (start - request.clipStartMs).coerceAtLeast(0L))
        val args = mutableListOf("-nostats", "-y")
        if (start - leadIn > 0L) args += listOf("-ss", seconds(start - leadIn))
        args += listOf("-t", seconds(leadIn + length), "-i", inputPath)
        args += listOf("-filter_complex", graph(request, leadIn))
        args += listOf("-map", "[before]", "-c:a", "pcm_s16le", beforePath)
        args += listOf("-map", "[after]", "-c:a", "pcm_s16le", afterPath)
        return FfmpegCommand(args)
    }

    /** The filtergraph of [command]; what it reads starts [leadInMs] before what is heard. */
    internal fun graph(request: AudioDenoiseSampleRequest, leadInMs: Long): String {
        val head = "aformat=sample_fmts=fltp:sample_rates=$SAMPLE_RATE:channel_layouts=stereo,asplit=2"
        val denoise = request.strengths.mapNotNull { audioDenoiseFilter(it) }.ifEmpty { listOf("anull") }
        val cut = if (leadInMs > 0L) listOf("atrim=start=${seconds(leadInMs)}", "asetpts=PTS-STARTPTS") else emptyList()
        return "[0:a:0]$head[raw][dirty];" +
            "[raw]${(cut.ifEmpty { listOf("anull") }).joinToString(",")}[before];" +
            "[dirty]${(denoise + cut).joinToString(",")}[after]"
    }

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)
}
