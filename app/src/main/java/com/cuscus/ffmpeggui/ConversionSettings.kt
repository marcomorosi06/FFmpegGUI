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

    // ── New feature: multi-clip concatenation ─────────────────────────────────
    /** Clips to append after the main input, in order. */
    val extraClips: List<ClipItem> = emptyList(),

    // ── New feature: image overlays (watermark / logo) ────────────────────────
    val imageOverlays: List<ImageOverlay> = emptyList(),

    // ── New feature: subtitle burn-in ─────────────────────────────────────────
    val subtitleUri: Uri? = null,
    val subtitleName: String = "",

    // ── New feature: stream copy (fast remux without re-encoding) ─────────────
    val useStreamCopy: Boolean = false,

    // ── New feature: frame / thumbnail extraction ─────────────────────────────
    val frameExtractionMode: FrameExtractionMode = FrameExtractionMode.DISABLED,
    /** Frames per second for SEQUENCE mode (e.g. 0.2 = one frame every 5 s). */
    val frameExtractionRate: Float = 1f,
    /** Timecode for SINGLE mode ("HH:MM:SS" or "HH:MM:SS.ms"). */
    val frameExtractionTimecode: String = "00:00:00",

    // ── New feature: video stabilization (2-pass vidstab) ─────────────────────
    val stabilize: Boolean = false,
    val stabilizeShakiness: Int = 5,
    val stabilizeSmoothing: Int = 10,

    // ── New feature: noise reduction ──────────────────────────────────────────
    val denoiseVideo: Float = 0f,
    val denoiseAudio: Float = 0f,

    // ── New feature: additional visual filters (COLOR tab) ────────────────────
    val sharpness: Float = 0f,
    val gaussianBlur: Float = 0f,
    val vignette: Boolean = false,
    val filmGrain: Float = 0f,

    // ── New feature: video split / segmenting ────────────────────────────────
    val splitMode: SplitMode = SplitMode.DISABLED,
    val splitValue: Int = 10,

    // ── New feature: metadata tags ────────────────────────────────────────────
    val metaTitle: String = "",
    val metaArtist: String = "",
    val metaAlbum: String = "",
    val metaYear: String = "",
    val metaComment: String = "",
    val metaGenre: String = "",
)
