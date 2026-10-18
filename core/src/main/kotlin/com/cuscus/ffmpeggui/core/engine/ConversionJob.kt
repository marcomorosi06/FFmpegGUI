package com.cuscus.ffmpeggui.core.engine

import com.cuscus.ffmpeggui.core.model.ConversionRequest

/** A source together with the name the user knows it by. */
data class NamedSource(val uri: String, val name: String)

/** The actual media behind a [ConversionRequest], as opaque URIs. */
data class MediaSources(
    /** One entry per request source, in the same order. */
    val inputs: List<NamedSource>,
    /** One file per request sound, in the same order. */
    val sounds: List<NamedSource> = emptyList(),
    val imageOverlays: List<NamedSource> = emptyList(),
    val subtitles: NamedSource? = null,
    /** The cover picture of an audio file (see ConversionRequest.cover). */
    val cover: NamedSource? = null,
)

sealed interface CommandMode {
    /** The command is derived from the request. */
    data object Generated : CommandMode

    /** The user wrote (or edited) the command line; see ManualCommandResolver. */
    data class Manual(val commandLine: String) : CommandMode
}

/** One unit of work for the [ConversionEngine]. */
data class ConversionJob(
    val request: ConversionRequest,
    val sources: MediaSources,
    val mode: CommandMode = CommandMode.Generated,
) {
    init {
        require(request.sources.size == sources.inputs.size) {
            "Request sources and input files differ in number"
        }
        require(request.imageOverlays.size == sources.imageOverlays.size) {
            "Overlay placements and overlay sources differ in number"
        }
        require(request.sounds.size == sources.sounds.size) {
            "Sound placements and sound files differ in number"
        }
        require((request.subtitleSource != null) == (sources.subtitles != null)) {
            "Subtitle burn-in and subtitle source must be provided together"
        }
        require(request.cover == (sources.cover != null)) {
            "A cover and its picture must be provided together"
        }
    }
}

enum class ConversionStage {
    PREPARING,
    ANALYZING,
    ENCODING,

    /** The parts or frames are being packed into a zip. */
    PACKING,
}

sealed interface ConversionEvent {
    data class StageChanged(val stage: ConversionStage) : ConversionEvent
    data class LogLine(val text: String) : ConversionEvent

    /** Fraction in 0..1. Only emitted when the output length is known. */
    data class Progress(val fraction: Float) : ConversionEvent

    /**
     * Which encoder makes the picture, said before it starts: the phone's own
     * ([hardware]) or x264 on the processor (also after the phone's one failed).
     */
    data class Encoder(val hardware: Boolean) : ConversionEvent
}

enum class FailureReason {
    /** A source could not be read; the detail holds its name. */
    SOURCE_UNREADABLE,

    /** The stabilization analysis pass failed. */
    ANALYSIS_FAILED,

    /** FFmpeg returned an error; the log has the details. */
    ENCODING_FAILED,

    /** The hand-written command is empty. */
    MANUAL_COMMAND_EMPTY,

    /** The hand-written command has no "output.<ext>" argument. */
    MANUAL_COMMAND_WITHOUT_OUTPUT,

    /** FFmpeg wrote the parts, but they could not be packed into a zip (storage full?). */
    PACKING_FAILED,
}

/** How the picture of a finished video was encoded. */
enum class VideoEncoderUse {
    /** No choice was made: no picture, a remux, or the processor asked for. */
    NONE,

    /** By the phone's video encoder. */
    HARDWARE,

    /** By the processor, after the phone's video encoder failed. */
    FALLBACK,
}

sealed interface ConversionOutcome {
    /** [outputPath] is a single file: numbered outputs come packed in a zip. */
    data class Success(val outputPath: String, val encoder: VideoEncoderUse = VideoEncoderUse.NONE) : ConversionOutcome
    data class Failure(val reason: FailureReason, val detail: String = "") : ConversionOutcome
    data object Cancelled : ConversionOutcome
}
