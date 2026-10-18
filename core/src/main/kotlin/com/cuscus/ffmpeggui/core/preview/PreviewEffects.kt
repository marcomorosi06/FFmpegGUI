package com.cuscus.ffmpeggui.core.preview

import kotlin.math.log10

/*
 * Math shared by the live preview so that it shows what FFmpeg will produce.
 * Each function mirrors one filter of the export chain; keep them in step with
 * core/command/VideoFilters.kt and AudioFilters.kt.
 */

/**
 * Brightness / loudness multiplier (0..1) produced by the fade filters at
 * [outputMs]. Mirrors `fade` / `afade`: the fade-out is skipped when it does
 * not fit in the output or the length is unknown.
 */
fun fadeLevel(
    outputMs: Long,
    outputDurationMs: Long,
    fadeInSeconds: Float,
    fadeOutSeconds: Float,
): Float {
    val seconds = outputMs.coerceAtLeast(0L) / 1000f
    var level = 1f
    if (fadeInSeconds > 0f) level = (seconds / fadeInSeconds).coerceIn(0f, 1f)

    val durationSeconds = outputDurationMs / 1000f
    val fadeOutStart = durationSeconds - fadeOutSeconds
    if (fadeOutSeconds > 0f && fadeOutStart > 0f) {
        level = minOf(level, ((durationSeconds - seconds) / fadeOutSeconds).coerceIn(0f, 1f))
    }
    return level
}

/**
 * 4x5 row-major colour matrix (Android `ColorMatrix` layout, offsets in 0..255)
 * equivalent to FFmpeg's `eq=brightness:contrast:saturation`.
 *
 * `eq` works on video-range YUV: contrast and brightness act on luma around
 * mid-grey, saturation scales chroma. Since RGB = luma + chroma, that is the
 * affine RGB transform  rgb' = s*rgb + (c - s)*luma + k.
 *
 * @param highDefinition  true for BT.709 sources (720p and up), false for BT.601.
 */
fun eqColorMatrix(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    highDefinition: Boolean = true,
): FloatArray {
    val (wr, wg, wb) = if (highDefinition) BT709_LUMA else BT601_LUMA
    val lumaGain = contrast - saturation
    // Luma offset, converted from video range (16..235) to full range and to 0..255.
    val offset = ((1f - contrast) * (0.5f - 16f / 255f) + brightness) * (255f / 219f) * 255f
    return floatArrayOf(
        saturation + lumaGain * wr, lumaGain * wg, lumaGain * wb, 0f, offset,
        lumaGain * wr, saturation + lumaGain * wg, lumaGain * wb, 0f, offset,
        lumaGain * wr, lumaGain * wg, saturation + lumaGain * wb, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** True when `eq` would be a no-op, so the preview can skip the colour filter. */
fun isNeutralEq(brightness: Float, contrast: Float, saturation: Float): Boolean =
    brightness == 0f && contrast == 1f && saturation == 1f

private val BT709_LUMA = Triple(0.2126f, 0.7152f, 0.0722f)
private val BT601_LUMA = Triple(0.299f, 0.587f, 0.114f)

/** Linear volume factor (1 = unchanged) expressed in decibels. */
fun volumeToDecibels(volume: Float): Float =
    if (volume <= 0f) SILENCE_DB else 20f * log10(volume)

/** Decibels back to a linear volume factor. */
fun decibelsToVolume(decibels: Float): Float =
    if (decibels <= SILENCE_DB) 0f else Math.pow(10.0, decibels / 20.0).toFloat()

/**
 * Gain that brings audio measured at [integratedLufs] to the level `loudnorm`
 * targets. The real filter also evens out the dynamics over time; a constant
 * gain reproduces how loud the result will be, not that reshaping.
 */
fun normalizationGainDb(integratedLufs: Double): Float =
    (LOUDNORM_TARGET_LUFS - integratedLufs).toFloat().coerceIn(-MAX_NORMALIZATION_GAIN_DB, MAX_NORMALIZATION_GAIN_DB)

/** Default integrated loudness target of FFmpeg's loudnorm filter. */
const val LOUDNORM_TARGET_LUFS = -24.0

private const val MAX_NORMALIZATION_GAIN_DB = 30f
private const val SILENCE_DB = -120f
