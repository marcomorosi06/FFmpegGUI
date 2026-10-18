package com.cuscus.ffmpeggui.conversion

/** Where cutting the subject of a picture out is (see ConversionViewModel.cutOutSubject). */
sealed interface CutoutStatus {
    data object Idle : CutoutStatus

    /** The model is looking at the picture at [uri]. */
    data class Working(val uri: String) : CutoutStatus

    /** Cutting the picture at [uri] out failed for [problem]. */
    data class Failed(val uri: String, val problem: CutoutProblem) : CutoutStatus
}

enum class CutoutProblem {
    /** The cut-out model is not on the phone (or did not pass its check). */
    NO_MODEL,

    /** Too little free memory to load it. */
    NO_MEMORY,

    /** The picture could not be read. */
    UNREADABLE,

    /** Something else went wrong. */
    FAILED,
}
