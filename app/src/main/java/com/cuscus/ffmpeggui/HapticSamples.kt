package com.cuscus.ffmpeggui

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The three taps offered to try (settings and welcome): light, medium, strong.
 * Many phones play Compose's tick, click and confirm the same way, so these go
 * to the view's own feedback constants, which phones tell apart.
 */
internal enum class HapticSample { TICK, TAP, CONFIRM }

internal fun View.playHapticSample(sample: HapticSample) {
    performHapticFeedback(
        when (sample) {
            HapticSample.TICK -> HapticFeedbackConstants.CLOCK_TICK
            HapticSample.TAP -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticSample.CONFIRM -> HapticFeedbackConstants.LONG_PRESS
        },
    )
}
