package com.cuscus.ffmpeggui.translate

/** Where translating the subtitles is. */
sealed interface TranslationStatus {
    data object Idle : TranslationStatus

    /** [done] lines of [total] (all the models' runs counted). */
    data class Working(val done: Int, val total: Int) : TranslationStatus {
        val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    data class Failed(val problem: TranslationProblem) : TranslationStatus

    /** Translated into [language] (a two-letter code). */
    data class Done(val language: String) : TranslationStatus
}

enum class TranslationProblem {
    /** No model goes from the subtitles' language to the one asked. */
    NO_MODEL,
    NO_MEMORY,
    /** No words to translate. */
    NOTHING,
    FAILED,
}
