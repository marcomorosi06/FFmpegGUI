package com.cuscus.ffmpeggui

import android.net.Uri

data class MediaInfo(
    val durationMs: Long,
    val durationFormatted: String,
    val isVideo: Boolean,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String
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
    DEG_270("270°", 270)
}

enum class WatermarkPosition(val label: String, val x: String, val y: String) {
    TOP_LEFT("Alto-Sinistra", "10", "10"),
    TOP_RIGHT("Alto-Destra", "w-tw-10", "10"),
    BOTTOM_LEFT("Basso-Sinistra", "10", "h-th-10"),
    BOTTOM_RIGHT("Basso-Destra", "w-tw-10", "h-th-10")
}

data class ConversionConfig(
    val inputUri: Uri,
    val inputFileName: String,
    val outputFormat: OutputFormat,
    val startTime: String = "00:00:00",
    val endTime: String = "",
    val resolution: Resolution = Resolution.ORIGINAL,
    val framerate: Framerate = Framerate.ORIGINAL,
    val qualityLevel: Float = 0.7f,
    val rotation: Rotation = Rotation.NONE,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val projectRatio: String = "Adatta",
    val cropW: String = "",
    val cropH: String = "",
    val cropX: String = "",
    val cropY: String = "",
    val customCommand: String = "",
    val isManualMode: Boolean = false,
    val manualCommandOverride: String = "",
    val removeAudio: Boolean = false,
    val volumeLevel: Float = 1.0f,
    val normalizeAudio: Boolean = false,
    val audioTrackUri: Uri? = null,
    val audioTrackName: String = "",
    val audioTrackTrimStart: String = "00:00:00",
    val audioTrackTrimEnd: String = "",
    val audioTrackDelay: String = "00:00:00",
    val audioTrackVolume: Float = 1.0f,
    val videoSpeed: Float = 1.0f,
    val activeVideoDurationMs: Long = 0L,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val fadeInDuration: Float = 0f,
    val fadeOutDuration: Float = 0f,
    val isReversed: Boolean = false,
    val textOverlays: List<TextOverlay> = emptyList()
)

sealed class ConversionState {
    data object Idle : ConversionState()
    data class Processing(val progress: Float, val logs: List<String>) : ConversionState()
    data class Success(val outputPath: String) : ConversionState()
    data class Error(val message: String, val logs: List<String>) : ConversionState()
}

enum class OverlayColor(val ffmpegValue: String, val hexArgb: Long) {
    WHITE("white", 0xFFFFFFFF),
    BLACK("black", 0xFF000000),
    RED("red", 0xFFFF0000),
    YELLOW("yellow", 0xFFFFFF00),
    GREEN("green", 0xFF00FF00),
    BLUE("blue", 0xFF0000FF)
}

enum class OverlayFont(val path: String, val label: String, val isBold: Boolean) {
    REGULAR("/system/fonts/Roboto-Regular.ttf", "Normale", false),
    BOLD("/system/fonts/Roboto-Bold.ttf", "Grassetto", true)
}

data class TextOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.05f,
    val color: Long = 0xFFFFFFFF,
    val isBold: Boolean = true
)