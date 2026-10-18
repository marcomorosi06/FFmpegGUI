package com.cuscus.ffmpeggui.core.model

import java.util.Locale

/** Basic facts about a media source, as reported by a [com.cuscus.ffmpeggui.core.engine.MediaProbe]. */
data class MediaInfo(
    val durationMs: Long,
    val durationFormatted: String,
    val isVideo: Boolean,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val hasAudio: Boolean = true,
    /**
     * A single picture rather than footage: it has no length of its own and a
     * clip of it shows the picture for as long as the clip lasts.
     */
    val isStill: Boolean = false,
    /** The picture's HDR kind (footage); [VideoHdr.NONE] for SDR and when not known. */
    val hdr: VideoHdr = VideoHdr.NONE,
    /** Frames per second of the picture (footage); 0 when not known. */
    val frameRate: Float = 0f,
) {
    companion object {
        /** Builds a [MediaInfo] from raw values, deriving the display strings. */
        fun of(
            durationMs: Long,
            isVideo: Boolean,
            width: Int,
            height: Int,
            fileSizeBytes: Long,
            hasAudio: Boolean,
            isStill: Boolean = false,
            hdr: VideoHdr = VideoHdr.NONE,
            frameRate: Float = 0f,
        ): MediaInfo = MediaInfo(
            durationMs = durationMs,
            durationFormatted = formatDuration(durationMs),
            isVideo = isVideo,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            fileSizeFormatted = formatFileSize(fileSizeBytes),
            hasAudio = hasAudio,
            isStill = isStill,
            hdr = hdr,
            frameRate = frameRate,
        )

        fun formatDuration(durationMs: Long): String {
            val safe = durationMs.coerceAtLeast(0L)
            val hours = safe / 3_600_000
            val minutes = (safe / 60_000) % 60
            val seconds = (safe / 1_000) % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }

        fun formatFileSize(bytes: Long): String = when {
            bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            else -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
        }
    }
}
