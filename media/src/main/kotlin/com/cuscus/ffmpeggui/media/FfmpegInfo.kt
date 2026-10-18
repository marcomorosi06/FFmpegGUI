package com.cuscus.ffmpeggui.media

import com.arthenica.ffmpegkit.FFmpegKitConfig

/** What the bundled FFmpeg is, for the About page. */
object FfmpegInfo {
    /** FFmpeg's version ("n6.1.6"), or null when the library cannot tell. */
    fun ffmpegVersion(): String? = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The FFmpegKit wrapper's version, or null. */
    fun kitVersion(): String? = runCatching { FFmpegKitConfig.getVersion() }.getOrNull()?.takeIf { it.isNotBlank() }
}
