package com.cuscus.ffmpeggui.conversion

import com.cuscus.ffmpeggui.speech.SpeechTranscriber

/** Where making subtitles from what is said is (see ConversionViewModel.generateSubtitles). */
sealed interface SpeechStatus {
    data object Idle : SpeechStatus

    /** Working on file [fileIndex] of [fileCount] ([fileName]): [step]. */
    data class Working(
        val step: SpeechTranscriber.Step,
        val fileIndex: Int,
        val fileCount: Int,
        val fileName: String,
    ) : SpeechStatus {
        /** How far the whole run is, 0..1; null while it cannot be told. */
        val fraction: Float?
            get() {
                // Setting the singing apart comes before listening: its own bar, file by file.
                if (step is SpeechTranscriber.Step.Isolating) return ((fileIndex + (step.fraction ?: return null)) / fileCount).coerceIn(0f, 1f)
                val inFile = when (step) {
                    is SpeechTranscriber.Step.Isolating -> 0f
                    // Taking the sound out is quick next to listening to it.
                    is SpeechTranscriber.Step.Extracting -> (step.fraction ?: return null) * EXTRACT_SHARE
                    SpeechTranscriber.Step.Loading -> EXTRACT_SHARE
                    is SpeechTranscriber.Step.Listening -> EXTRACT_SHARE + step.fraction * (1f - EXTRACT_SHARE)
                }
                return ((fileIndex + inFile) / fileCount).coerceIn(0f, 1f)
            }

        private companion object {
            const val EXTRACT_SHARE = 0.1f
        }
    }

    data class Failed(val problem: SpeechProblem) : SpeechStatus
}

enum class SpeechProblem {
    /** The speech model is not on the phone (or did not pass its check). */
    NO_MODEL,

    /** Too little free memory to load it. */
    NO_MEMORY,

    /** Something else went wrong. */
    FAILED,

    /** The singing was to be set apart first, and Spleeter's five stems are not on the phone. */
    NO_SINGING_MODEL,
}
