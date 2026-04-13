package com.cuscus.ffmpeggui

import android.net.Uri

/**
 * All user-configurable parameters for a single conversion.
 *
 * Grouping these into one data class allows atomic reset (just reassign to the
 * default instance) and makes [ConversionViewModel] significantly leaner.
 */
data class ConversionSettings(
    // Trim
    val startTime: String = "00:00:00",
    val endTime: String = "",

    // Video quality
    val resolution: Resolution = Resolution.ORIGINAL,
    val framerate: Framerate = Framerate.ORIGINAL,
    val qualityLevel: Float = 0.7f,

    // Transform
    val rotation: Rotation = Rotation.NONE,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val projectRatio: String = "Adatta",
    val cropW: String = "",
    val cropH: String = "",
    val cropX: String = "",
    val cropY: String = "",

    // Speed / reverse
    val videoSpeed: Float = 1.0f,
    val isReversed: Boolean = false,

    // Colour grading
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,

    // Fades
    val fadeInDuration: Float = 0f,
    val fadeOutDuration: Float = 0f,

    // Audio (primary stream)
    val removeAudio: Boolean = false,
    val volumeLevel: Float = 1.0f,
    val normalizeAudio: Boolean = false,

    // External audio track
    val audioTrackUri: Uri? = null,
    val audioTrackName: String = "",
    val audioTrackDurationMs: Long = 0L,
    val audioTrackTrimStart: String = "00:00:00",
    val audioTrackTrimEnd: String = "",
    val audioTrackDelay: String = "00:00:00",
    val audioTrackVolume: Float = 1.0f,

    // Text overlays
    val textOverlays: List<TextOverlay> = emptyList(),

    // Custom / manual command
    val customCommand: String = "",
    val isManualMode: Boolean = false,
    val manualCommandOverride: String = "",
)
