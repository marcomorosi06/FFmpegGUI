package com.cuscus.ffmpeggui.conversion

/** The steps of the dub in one go (see ConversionViewModel.autoDub), in order. */
enum class AutoDubStep {
    /** What is said in the video, written down (skipped when it has subtitles). */
    TRANSCRIBE,

    /** Those words put into the dub's language (skipped when they are in it already). */
    TRANSLATE,

    /** The video's own voice set apart, to be lowered or taken out (skipped for files done before). */
    SEPARATE,

    /** The new voice said and laid on the video. */
    DUB,
}

/** Where the dub in one go is. */
sealed interface AutoDubStatus {
    data object Idle : AutoDubStatus

    /** At [step], [fraction] of it done (null: cannot be told); [skipped] were not needed. */
    data class Working(val step: AutoDubStep, val fraction: Float?, val skipped: Set<AutoDubStep>, val from: String?) : AutoDubStatus

    /** Done: [lines] lines said, [lengthMs] of speech made in [tookMs]. */
    data class Done(val lines: Int, val lengthMs: Long, val tookMs: Long, val skipped: Set<AutoDubStep>, val from: String?) : AutoDubStatus

    /** Stopped at [step] by [problem]; what was done before it stays (one undo each). */
    data class Failed(val step: AutoDubStep, val problem: AutoDubProblem, val from: String? = null) : AutoDubStatus
}

enum class AutoDubProblem {
    /** The speech model is not on the phone, and the video has no subtitles. */
    NO_SPEECH_MODEL,

    /** Nothing is said in the video (or nothing that was understood). */
    NOTHING_SAID,

    /** No translation model brings the video's language to the dub's. */
    NO_TRANSLATION_MODEL,

    /** The voice model is needed to lower or take out the video's voice. */
    NO_VOICE_MODEL,

    /** The dubbing voice is not on the phone. */
    NO_DUB_MODEL,

    NO_MEMORY,
    FAILED,
}
