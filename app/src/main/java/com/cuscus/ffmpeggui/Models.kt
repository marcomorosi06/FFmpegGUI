package com.cuscus.ffmpeggui

import android.net.Uri

data class MediaInfo(
    val durationMs: Long,
    val durationFormatted: String,
    val isVideo: Boolean,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val hasAudio: Boolean = true,
)

enum class OutputFormat(
    val label: String,
    val extension: String,
    val category: FormatCategory,
) {
    MP4("MP4", "mp4", FormatCategory.VIDEO),
    MKV("MKV", "mkv", FormatCategory.VIDEO),
    AVI("AVI", "avi", FormatCategory.VIDEO),
    GIF("GIF", "gif", FormatCategory.ANIMATION),
    WEBP("WebP", "webp", FormatCategory.ANIMATION),
    MP3("MP3", "mp3", FormatCategory.AUDIO),
    FLAC("FLAC", "flac", FormatCategory.AUDIO),
    OGG("OGG", "ogg", FormatCategory.AUDIO),
    CUSTOM("Custom", "", FormatCategory.CUSTOM),
}

enum class FormatCategory(val label: String) {
    VIDEO("Video"),
    ANIMATION("Animazione"),
    AUDIO("Audio"),
    CUSTOM("Custom"),
}

enum class Resolution(val label: String, val width: Int, val height: Int) {
    P240("240p", 426, 240),
    P480("480p", 854, 480),
    P720("720p", 1280, 720),
    P1080("1080p", 1920, 1080),
    ORIGINAL("Originale", -1, -1),
}

enum class Framerate(val label: String, val value: Int) {
    FPS10("10", 10),
    FPS15("15", 15),
    FPS24("24", 24),
    FPS30("30", 30),
    ORIGINAL("orig.", -1),
}

enum class Rotation(val label: String, val degrees: Int) {
    NONE("Nessuna", 0),
    DEG_90("90°", 90),
    DEG_180("180°", 180),
    DEG_270("270°", 270),
}

/**
 * Controls frame/thumbnail extraction mode.
 * SINGLE  → one frame at a specific timecode (-frames:v 1)
 * SEQUENCE → multiple frames at a given rate (-vf fps=N)
 */
enum class FrameExtractionMode(val label: String) {
    DISABLED("Disabilitato"),
    SINGLE("Singolo fotogramma"),
    SEQUENCE("Sequenza"),
}

/**
 * Split/segment mode for exporting the video as multiple chunks.
 * BY_DURATION → each segment is [splitValue] seconds long.
 * BY_PARTS    → the video is split into [splitValue] equal parts.
 */
enum class SplitMode(val label: String) {
    DISABLED("Disabilitato"),
    BY_DURATION("Per durata (s)"),
    BY_PARTS("In N parti"),
}

/**
 * One clip in a multi-clip concatenation sequence.
 * Each clip can carry its own trim window (trimStart / trimEnd).
 */
data class ClipItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val trimStart: String = "00:00:00",
    val trimEnd: String = "",
    val durationMs: Long = 0L,
)

/**
 * An image (PNG/JPG with optional alpha) overlaid on the video, e.g. watermark or logo.
 *
 * All coordinates and dimensions are expressed as fractions of the output video
 * so that they remain resolution-independent.
 *
 * @param x      Left edge of the overlay, 0 = left border, 1 = right border.
 * @param y      Top edge of the overlay, 0 = top border, 1 = bottom border.
 * @param scaleW Width of the overlay expressed as a fraction of the video width (0–1).
 * @param opacity Alpha multiplier 0 (invisible) to 1 (fully opaque).
 */
data class ImageOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val fileName: String = "",
    val x: Float = 0.05f,
    val y: Float = 0.05f,
    val scaleW: Float = 0.20f,
    val opacity: Float = 1.0f,
)

/**
 * Full FFmpeg conversion parameters derived from [ConversionSettings].
 * Built once per conversion start by the ViewModel, then passed to [CommandBuilder].
 */
data class ConversionConfig(
    val inputUri: Uri,
    val inputFileName: String,
    val outputFormat: OutputFormat,
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
    // Manual / custom
    val customCommand: String = "",
    val isManualMode: Boolean = false,
    val manualCommandOverride: String = "",
    // Input capabilities
    val inputHasAudio: Boolean = true,
    // Audio
    val removeAudio: Boolean = false,
    val volumeLevel: Float = 1.0f,
    val normalizeAudio: Boolean = false,
    val audioTrackUri: Uri? = null,
    val audioTrackName: String = "",
    val audioTrackTrimStart: String = "00:00:00",
    val audioTrackTrimEnd: String = "",
    val audioTrackDelay: String = "00:00:00",
    val audioTrackVolume: Float = 1.0f,
    // Speed / timing
    val videoSpeed: Float = 1.0f,
    val activeVideoDurationMs: Long = 0L,
    // Colour
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    // Fades
    val fadeInDuration: Float = 0f,
    val fadeOutDuration: Float = 0f,
    // Reverse
    val isReversed: Boolean = false,
    // Text overlays
    val textOverlays: List<TextOverlay> = emptyList(),

    // ── New feature: concat ───────────────────────────────────────────────────
    /** Non-empty path → use concat demuxer; the .txt file is prepared by the ViewModel. */
    val concatListPath: String = "",
    val extraClips: List<ClipItem> = emptyList(),

    // ── New feature: image overlays ───────────────────────────────────────────
    val imageOverlays: List<ImageOverlay> = emptyList(),
    /** Maps each ImageOverlay.id to its resolved absolute path on the filesystem. */
    val imageOverlayCachePaths: Map<String, String> = emptyMap(),

    // ── New feature: subtitle burn-in ─────────────────────────────────────────
    /** Absolute path to the subtitle file (.srt / .ass) copied to the app cache. */
    val subtitleCachePath: String = "",

    // ── New feature: stream copy (fast remux) ─────────────────────────────────
    /** When true, all streams are copied without re-encoding (-c copy). */
    val useStreamCopy: Boolean = false,

    // ── New feature: frame extraction ─────────────────────────────────────────
    val frameExtractionMode: FrameExtractionMode = FrameExtractionMode.DISABLED,
    /** For SEQUENCE mode: how many frames per second to extract (e.g. 0.2 = one every 5 s). */
    val frameExtractionRate: Float = 1f,
    /** For SINGLE mode: timecode of the frame to extract ("HH:MM:SS.ms"). */
    val frameExtractionTimecode: String = "00:00:00",

    // ── Video dimensions (populated from MediaInfo; used for pixel-accurate overlay placement)
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,

    // ── New feature: video stabilization (vidstab, 2-pass) ────────────────────
    /** When true, run a 2-pass vidstabdetect + vidstabtransform workflow. */
    val stabilize: Boolean = false,
    /** vidstabdetect shakiness (1-10). */
    val stabilizeShakiness: Int = 5,
    /** vidstabtransform smoothing window in frames. */
    val stabilizeSmoothing: Int = 10,
    /** Absolute path to the transforms.trf file produced by pass 1. */
    val stabilizeTransformsPath: String = "",

    // ── New feature: noise reduction ──────────────────────────────────────────
    /** 0 = disabled. Typical range 0-10 (mapped to hqdn3d strengths). */
    val denoiseVideo: Float = 0f,
    /** 0 = disabled. Typical range 0-1 (mapped to anlmdn strength). */
    val denoiseAudio: Float = 0f,

    // ── New feature: additional visual filters (COLOR tab) ────────────────────
    /** -1 … +1 (unsharp luma_amount). 0 = neutral. */
    val sharpness: Float = 0f,
    /** 0 = off, >0 radius for gblur. */
    val gaussianBlur: Float = 0f,
    /** Toggle the vignette filter. */
    val vignette: Boolean = false,
    /** 0 = off, typical 0-50 for the noise filter's alls= strength. */
    val filmGrain: Float = 0f,

    // ── New feature: video split / segmenting ─────────────────────────────────
    val splitMode: SplitMode = SplitMode.DISABLED,
    /** Seconds per segment (BY_DURATION) or number of parts (BY_PARTS). */
    val splitValue: Int = 10,

    // ── New feature: metadata tags ────────────────────────────────────────────
    val metaTitle: String = "",
    val metaArtist: String = "",
    val metaAlbum: String = "",
    val metaYear: String = "",
    val metaComment: String = "",
    val metaGenre: String = "",
)

sealed class ConversionState {
    data object Idle : ConversionState()
    data class Processing(val progress: Float, val logs: List<String>) : ConversionState()
    data class Success(val outputPath: String) : ConversionState()
    data class Error(val message: String, val logs: List<String>) : ConversionState()
}

data class TextOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.05f,
    val color: Long = 0xFFFFFFFF,
    val isBold: Boolean = true,
)
