package com.cuscus.ffmpeggui.conversion

/** Where the magic eraser is (see ConversionViewModel.magicErase). */
sealed interface EraseStatus {
    data object Idle : EraseStatus

    /** Filling in what was painted over the picture [uri]. */
    data class Working(val uri: String) : EraseStatus

    data class Failed(val problem: EraseProblem) : EraseStatus
}

enum class EraseProblem {
    NO_MODEL,
    NO_MEMORY,
    NOTHING,
    UNREADABLE,
    FAILED,

    /** The AI marks are not installed: nothing is erased without them (EU AI Act, Article 50(2)). */
    NO_MARKS,

    /** The AI mark did not hold on the picture (a very small one): it is not kept. */
    MARK_NOT_HELD,
}
