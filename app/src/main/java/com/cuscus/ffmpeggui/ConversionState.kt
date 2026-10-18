package com.cuscus.ffmpeggui

import com.cuscus.ffmpeggui.core.engine.ConversionStage
import com.cuscus.ffmpeggui.core.engine.VideoEncoderUse

/** What the processing screen shows. */
sealed class ConversionState {
    data object Idle : ConversionState()

    /**
     * An export running: [progress] (0..1) of its encoding, at [stage]. Times are
     * SystemClock.elapsedRealtime: [startedAtMs] when it started, [encodingSinceMs]
     * when the encoding did (0 before), for the time left. [hardware] says who
     * encodes the picture: the phone's video encoder (true), the processor after
     * it failed (false), or nobody asked (null).
     */
    data class Processing(
        val progress: Float,
        val logs: List<String>,
        val stage: ConversionStage = ConversionStage.PREPARING,
        val startedAtMs: Long = 0L,
        val encodingSinceMs: Long = 0L,
        val hardware: Boolean? = null,
    ) : ConversionState()

    /**
     * The file at [outputPath] was made, in [tookMs]; a copy went to the
     * phone's shared folder [copiedTo] ("Movies/FFmpegGUI"), when it did; its
     * picture made by [encoder].
     */
    data class Success(
        val outputPath: String,
        val tookMs: Long = 0L,
        val copiedTo: String? = null,
        val encoder: VideoEncoderUse = VideoEncoderUse.NONE,
    ) : ConversionState()
    data class Error(val message: String, val logs: List<String>) : ConversionState()
}
