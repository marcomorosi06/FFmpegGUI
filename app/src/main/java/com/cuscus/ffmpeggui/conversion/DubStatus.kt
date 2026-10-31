package com.cuscus.ffmpeggui.conversion

/** Where dubbing the video in the subtitles' language is (see ConversionViewModel.dubSubtitles). */
sealed interface DubStatus {
    data object Idle : DubStatus

    /** First setting the voice of the video's files apart (to lower it under the dub): [fraction] done. */
    data class Separating(val fraction: Float?) : DubStatus

    /** Saying the lines: [done] of [total]. */
    data class Speaking(val done: Int, val total: Int) : DubStatus {
        val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
    }

    /**
     * Done: [lines] lines said, laid on the video as a sound of their own
     * [lengthMs] long, made in [tookMs].
     */
    data class Done(val lines: Int, val lengthMs: Long = 0, val tookMs: Long = 0) : DubStatus

    data class Failed(val problem: DubProblem) : DubStatus
}

enum class DubProblem {
    /** The dubbing model is not on the phone (or did not pass its check). */
    NO_MODEL,

    /** Too little free memory to load it. */
    NO_MEMORY,

    /** No line of the subtitles has words to say. */
    NOTHING,

    /** The original voice could not be set apart to be lowered. */
    NO_SEPARATION,

    /** Something else went wrong. */
    FAILED,
}
