package com.cuscus.ffmpeggui.core.model

/*
 * These enums carry no display text: user-facing names live in the app's string
 * resources (see app/.../Labels.kt).
 */

enum class FormatCategory { VIDEO, ANIMATION, AUDIO, IMAGE, TEXT, CUSTOM }

enum class OutputFormat(
    val extension: String,
    val category: FormatCategory,
) {
    MP4("mp4", FormatCategory.VIDEO),
    MKV("mkv", FormatCategory.VIDEO),
    AVI("avi", FormatCategory.VIDEO),
    GIF("gif", FormatCategory.ANIMATION),
    MP3("mp3", FormatCategory.AUDIO),
    M4A("m4a", FormatCategory.AUDIO),
    OGG("ogg", FormatCategory.AUDIO),
    OPUS("opus", FormatCategory.AUDIO),
    FLAC("flac", FormatCategory.AUDIO),
    WAV("wav", FormatCategory.AUDIO),
    JPG("jpg", FormatCategory.IMAGE),
    PNG("png", FormatCategory.IMAGE),
    WEBP("webp", FormatCategory.IMAGE),
    BMP("bmp", FormatCategory.IMAGE),
    TIFF("tiff", FormatCategory.IMAGE),

    /** What is said in the file, written down by the speech model (no FFmpeg involved). */
    TXT("txt", FormatCategory.TEXT),

    /** The same words as subtitles (SubRip), timed like the exported file. */
    SRT("srt", FormatCategory.TEXT),
    CUSTOM("", FormatCategory.CUSTOM);

    /** True when the container carries a picture stream. */
    val hasVideo: Boolean
        get() = category == FormatCategory.VIDEO || category == FormatCategory.ANIMATION

    /** True when the container carries a sound stream. */
    val hasAudio: Boolean
        get() = category == FormatCategory.VIDEO || category == FormatCategory.AUDIO

    /** True for an audio file: the editor works on its sound alone. */
    val isAudioOnly: Boolean
        get() = category == FormatCategory.AUDIO

    /** True for a transcript: what is said, as text. */
    val isTranscript: Boolean
        get() = category == FormatCategory.TEXT

    /** True for a picture file: the editor works on one picture. */
    val isImage: Boolean
        get() = category == FormatCategory.IMAGE

    /**
     * True when the bundled FFmpeg has no encoder for it: FFmpeg writes a PNG
     * and the platform's encoder turns it into this format (WEBP).
     */
    val needsPlatformEncoder: Boolean
        get() = this == WEBP

    /** True when a picture file of this format can keep transparent parts. */
    val holdsTransparency: Boolean
        get() = this == PNG || this == WEBP || this == TIFF

    /** True when the picture is compressed with loss, so it has a quality. */
    val hasImageQuality: Boolean
        get() = this == JPG || this == WEBP

    /** True when the sound is compressed with loss, so it has a bitrate. */
    val isLossyAudio: Boolean
        get() = this == MP3 || this == M4A || this == OGG || this == OPUS

    /** True when the sound is kept exactly, at a bit depth. */
    val isLosslessAudio: Boolean
        get() = this == FLAC || this == WAV
}

/** Output height; [ORIGINAL] keeps the source size. */
enum class Resolution(val height: Int) {
    P240(240),
    P480(480),
    P720(720),
    P1080(1080),
    ORIGINAL(-1),
}

/** Output frame rate; [ORIGINAL] keeps the source rate. */
enum class Framerate(val value: Int) {
    FPS10(10),
    FPS15(15),
    FPS24(24),
    FPS30(30),
    ORIGINAL(-1),
}

/**
 * How hard x264 works when the processor encodes a video ([preset] is its
 * `-preset`): faster makes a bigger file at the same quality (CRF), slower a
 * smaller one. Each step is roughly twice as fast as the next.
 */
enum class CpuEncoderSpeed(val preset: String) {
    FASTEST("ultrafast"),
    FAST("veryfast"),
    BALANCED("medium"),
    SMALLEST("slow"),
}

enum class Rotation(val degrees: Int) {
    NONE(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270),
}

/** Aspect ratio of the output canvas; anything but [FIT] letterboxes the picture. */
enum class CanvasRatio(val width: Int, val height: Int) {
    FIT(0, 0),
    SQUARE(1, 1),
    LANDSCAPE_16_9(16, 9),
    LANDSCAPE_4_3(4, 3),
    PORTRAIT_9_16(9, 16);

    /** Width divided by height, or null for [FIT]. */
    val ratio: Float?
        get() = if (this == FIT) null else width.toFloat() / height
}

enum class FrameExtractionMode { DISABLED, SINGLE, SEQUENCE }

enum class SplitMode { DISABLED, BY_DURATION, BY_PARTS }
