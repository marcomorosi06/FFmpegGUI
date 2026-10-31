package com.cuscus.ffmpeggui.conversion

import com.cuscus.ffmpeggui.voice.VoiceSeparator

/** Where setting the voice of the video's files apart is (see ConversionViewModel.separateVoice). */
sealed interface VoiceStatus {
    data object Idle : VoiceStatus

    /** Working on file [fileIndex] of [fileCount] ([fileName]): [step]. */
    data class Working(
        val step: VoiceSeparator.Step,
        val fileIndex: Int,
        val fileCount: Int,
        val fileName: String,
    ) : VoiceStatus {
        /** How far the whole run is, 0..1; null while it cannot be told. */
        val fraction: Float?
            get() {
                val inFile = when (step) {
                    is VoiceSeparator.Step.Extracting -> (step.fraction ?: return null) * EXTRACT_SHARE
                    VoiceSeparator.Step.Loading -> EXTRACT_SHARE
                    is VoiceSeparator.Step.Separating -> EXTRACT_SHARE + step.fraction * SEPARATE_SHARE
                    is VoiceSeparator.Step.Saving -> EXTRACT_SHARE + SEPARATE_SHARE + (step.fraction ?: 0f) * (1f - EXTRACT_SHARE - SEPARATE_SHARE)
                }
                return ((fileIndex + inFile) / fileCount).coerceIn(0f, 1f)
            }

        private companion object {
            const val EXTRACT_SHARE = 0.08f
            const val SEPARATE_SHARE = 0.84f
        }
    }

    data class Failed(val problem: VoiceProblem) : VoiceStatus
}

enum class VoiceProblem {
    /** The voice model is not on the phone (or did not pass its check). */
    NO_MODEL,

    /** Too little free memory to load it. */
    NO_MEMORY,

    /** Too little free space for the sound while it is worked on. */
    NO_SPACE,

    /** The file has no sound to set apart. */
    NO_SOUND,

    /** Something else went wrong. */
    FAILED,
}
