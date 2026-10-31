package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.Compression
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.time.expectedOutputDurationMs

/*
 * The compressor's encoding (see Compression): a video by x265, HEVC in an
 * MP4 tagged hvc1 (as phones and Apple's players want it), at a CRF of the
 * chosen level, or at the bitrate that fills the size asked for in two
 * passes (the first one only measures, into a stats file; the second one
 * spends the bits where the picture needs them). Its sound AAC, smaller when
 * the size is tight. A PNG with the strongest deflate and the best row filter
 * of each row, and optionally reduced to 256 dithered colours.
 */

/** How x265 encodes a compressed video: at [crf], or at [kbps] in pass [pass] (1 or 2) with [stats]. */
internal class X265Encoding(
    val crf: Int?,
    val kbps: Int?,
    val pass: Int,
    val stats: String?,
    val audioKbps: Int,
)

/** True when the compressor's video is made in two x265 passes to fit its size. */
internal fun ConversionRequest.twoPassVideo(): Boolean {
    val c = compression ?: return false
    return c.hasTarget && compressesVideo() &&
        Compression.videoKbps(c.targetBytes, expectedOutputDurationMs(), Compression.VIDEO_AUDIO_KBPS) != null
}

/** True when the compressor makes this request's video (an MP4 or MKV that is encoded). */
internal fun ConversionRequest.compressesVideo(): Boolean =
    compression != null && (format == OutputFormat.MP4 || format == OutputFormat.MKV) &&
        !encoding.streamCopy && frameExtraction == FrameExtraction.Disabled

/** The compressor's x265 for [request] in [pass] (0: one pass), or null when it does not make the video. */
internal fun x265For(request: ConversionRequest, paths: MediaPaths, pass: Int): X265Encoding? {
    val c = request.compression ?: return null
    if (!request.compressesVideo()) return null
    val durationMs = request.expectedOutputDurationMs()
    if (request.twoPassVideo()) {
        val audio = Compression.videoAudioKbps(c.targetBytes, durationMs)
        return X265Encoding(
            crf = null,
            kbps = Compression.videoKbps(c.targetBytes, durationMs, audio),
            pass = if (pass == 1) 1 else 2,
            stats = paths.passLog,
            audioKbps = audio,
        )
    }
    return X265Encoding(crf = c.level.x265Crf, kbps = null, pass = 0, stats = null, audioKbps = Compression.VIDEO_AUDIO_KBPS)
}

/** x265's arguments for [x265] in [format], [preset] fast or slow; an [hdr] video in 10 bits. */
internal fun x265Arguments(x265: X265Encoding, format: OutputFormat, preset: String, hdr: Boolean): List<String> {
    val args = mutableListOf("-vcodec", "libx265")
    args += if (x265.kbps != null) listOf("-b:v", "${x265.kbps}k") else listOf("-crf", (x265.crf ?: Compression().level.x265Crf).toString())
    args += listOf("-preset", preset)
    args += if (hdr) listOf("-pix_fmt", "yuv420p10le", "-profile:v", "main10") else listOf("-pix_fmt", "yuv420p")
    if (x265.pass > 0 && x265.stats != null) {
        args += listOf("-x265-params", "pass=${x265.pass}:stats=${x265.stats}")
    }
    if (format == OutputFormat.MP4) args += listOf("-tag:v", "hvc1", "-movflags", "+faststart")
    return args
}

/** The filters that bring a PNG down to 256 colours, dithered, its transparency kept. */
internal const val PNG_PALETTE_FILTER =
    "split[pq0][pq1];[pq0]palettegen=max_colors=256:reserve_transparent=1:stats_mode=full[pqp];" +
        "[pq1][pqp]paletteuse=dither=sierra2_4a:alpha_threshold=128"

/** The PNG encoder at its strongest: deflate level 9, each row filtered as suits it best. */
internal val PNG_STRONGEST = listOf("-compression_level", "9", "-pred", "mixed")

/** The same for a picture in 256 colours, whose rows are best left unfiltered. */
internal val PNG_PALETTE_STRONGEST = listOf("-compression_level", "9", "-pred", "none")
