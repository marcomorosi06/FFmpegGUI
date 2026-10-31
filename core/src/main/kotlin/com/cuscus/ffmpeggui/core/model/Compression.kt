package com.cuscus.ffmpeggui.core.model

import kotlin.math.roundToInt

/**
 * How hard the compressor squeezes a file, when no size is asked for:
 *
 *  - a video is encoded by x265 (HEVC, which needs about half the bits of the
 *    H.264 phones record for the same look) at [x265Crf]: 24 keeps what the eye
 *    sees of a phone's recording, 28 is x265's own default, 32 is clearly softer;
 *  - a JPG at [jpegQuality];
 *  - a sound at [audioScale] times the usual bitrate of its format (see [audioKbps]).
 */
enum class CompressionLevel(val x265Crf: Int, val jpegQuality: Float, val audioScale: Float) {
    SAME_QUALITY(24, 0.86f, 1f),
    BALANCED(28, 0.72f, 0.75f),
    SMALLEST(32, 0.52f, 0.5f),
}

/**
 * What the compressor makes of a file (see the compressor screen): as [level]
 * makes it, or at most [targetBytes] (a video in two x265 passes at the
 * bitrate that fills it, a JPG at the best quality that fits, a sound at the
 * bitrate that fits). A PNG is written with the encoder's strongest
 * compression, and with [pngPalette] in 256 colours (dithered), which makes it
 * far smaller for a nearly identical look.
 */
data class Compression(
    val level: CompressionLevel = CompressionLevel.SAME_QUALITY,
    /** At most this many bytes; 0: as [level] makes it. */
    val targetBytes: Long = 0L,
    val pngPalette: Boolean = false,
) {
    val hasTarget: Boolean get() = targetBytes > 0L

    companion object {
        /** The sound of a compressed video, AAC. */
        const val VIDEO_AUDIO_KBPS = 128

        /** Below this, a smaller sound for a video that must fit (64 kbit/s AAC still carries voices well). */
        const val VIDEO_AUDIO_MIN_KBPS = 64

        /** What the container and the stream headers take, kept free of the target. */
        const val OVERHEAD = 0.97

        /**
         * The video bitrate (kbit/s) that makes [durationMs] of footage with a
         * sound of [audioKbps] fit in [targetBytes]; null when it cannot be told.
         */
        fun videoKbps(targetBytes: Long, durationMs: Long?, audioKbps: Int): Int? {
            if (targetBytes <= 0L || durationMs == null || durationMs <= 0L) return null
            val totalKbps = targetBytes * 8.0 * OVERHEAD / (durationMs / 1000.0) / 1000.0
            return (totalKbps - audioKbps).roundToInt().coerceAtLeast(MIN_VIDEO_KBPS)
        }

        /** The sound's bitrate for a video that must fit [targetBytes]: smaller when the video would get too little. */
        fun videoAudioKbps(targetBytes: Long, durationMs: Long?): Int {
            if (targetBytes <= 0L || durationMs == null || durationMs <= 0L) return VIDEO_AUDIO_KBPS
            val totalKbps = targetBytes * 8.0 * OVERHEAD / (durationMs / 1000.0) / 1000.0
            // The sound takes at most a fifth of what there is.
            return (totalKbps / 5).roundToInt().coerceIn(VIDEO_AUDIO_MIN_KBPS, VIDEO_AUDIO_KBPS)
        }

        /**
         * The bitrate of a sound file in [format] squeezed as [compression] says,
         * for [durationMs] of sound.
         */
        fun audioKbps(format: OutputFormat, compression: Compression, durationMs: Long?): Int {
            val usual = when (format) {
                OutputFormat.MP3 -> 192
                OutputFormat.M4A -> 160
                OutputFormat.OGG -> 160
                OutputFormat.OPUS -> 128
                else -> 160
            }
            val (low, high) = when (format) {
                OutputFormat.MP3 -> 32 to 320
                OutputFormat.OGG -> 64 to 320
                OutputFormat.OPUS -> 16 to 256
                else -> 32 to 320
            }
            val wanted = if (compression.hasTarget && durationMs != null && durationMs > 0L) {
                (compression.targetBytes * 8.0 * OVERHEAD / (durationMs / 1000.0) / 1000.0).toInt()
            } else {
                (usual * compression.level.audioScale).roundToInt()
            }
            return wanted.coerceIn(low, high)
        }

        private const val MIN_VIDEO_KBPS = 50
    }
}
