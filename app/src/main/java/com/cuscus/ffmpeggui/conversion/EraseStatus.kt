package com.cuscus.ffmpeggui.conversion

/** Where the magic eraser is (see ConversionViewModel.magicErase). */
sealed interface EraseStatus {
    data object Idle : EraseStatus

    /** Filling in what was painted over the picture [uri]. */
    data class Working(val uri: String) : EraseStatus

    data class Failed(val problem: EraseProblem) : EraseStatus
}

enum class EraseProblem { NO_MODEL, NO_MEMORY, NOTHING, UNREADABLE, FAILED }
