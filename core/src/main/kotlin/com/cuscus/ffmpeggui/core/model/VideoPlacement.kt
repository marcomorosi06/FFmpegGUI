package com.cuscus.ffmpeggui.core.model

/**
 * The video placed freely on its canvas (the project's frame, see
 * [CanvasRatio]), like a picture laid over it: its middle at ([centerX],
 * [centerY]) of the canvas (0..1 each way, 0.5 = the middle), [scale] times
 * the size it has unmoved (1 = as large as fits), turned [rotationDegrees]
 * clockwise. What it leaves uncovered is black; what goes past the canvas
 * is cut.
 */
data class VideoPlacement(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
) {
    /** True when the video lies where and as it would without it. */
    val isNeutral: Boolean
        get() = kotlin.math.abs(centerX - 0.5f) < 1e-4f && kotlin.math.abs(centerY - 0.5f) < 1e-4f &&
            kotlin.math.abs(scale - 1f) < 1e-4f && kotlin.math.abs(turn) < 0.01f

    /** The turn, in -180..180 degrees. */
    val turn: Float
        get() = ((rotationDegrees % 360f) + 540f) % 360f - 180f

    /** The same placement within reasonable bounds. */
    fun normalized(): VideoPlacement = copy(
        centerX = centerX.coerceIn(-0.5f, 1.5f),
        centerY = centerY.coerceIn(-0.5f, 1.5f),
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
    )

    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 5f
    }
}
