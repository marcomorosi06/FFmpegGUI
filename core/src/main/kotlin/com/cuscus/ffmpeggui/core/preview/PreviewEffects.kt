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
    // The same limits the export keeps: no negative picture, no negative colour.
    val contrast = eqContrast(contrast)
    val saturation = eqSaturation(saturation)
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

/**
 * 4x5 colour matrix (as [eqColorMatrix]) equivalent to FFmpeg's `hue=h=`
 * [degrees]: the chroma of BT.601 YUV turned by that angle, luma kept (red
 * goes towards green as the angle grows). Null when nothing turns.
 */
fun hueColorMatrix(degrees: Float): FloatArray? {
    val turn = ((degrees % 360f) + 360f) % 360f
    if (turn < 0.5f || turn > 359.5f) return null
    val a = Math.toRadians(turn.toDouble())
    val cos = Math.cos(a)
    val sin = Math.sin(a)
    // RGB to YUV (Y, U, V rows), the chroma plane turned, back to RGB.
    val toYuv = arrayOf(
        doubleArrayOf(0.299, 0.587, 0.114),
        doubleArrayOf(-0.168736, -0.331264, 0.5),
        doubleArrayOf(0.5, -0.418688, -0.081312),
    )
    val toRgb = arrayOf(
        doubleArrayOf(1.0, 0.0, 1.402),
        doubleArrayOf(1.0, -0.344136, -0.714136),
        doubleArrayOf(1.0, 1.772, 0.0),
    )
    val turned = Array(3) { row ->
        when (row) {
            0 -> toYuv[0]
            1 -> DoubleArray(3) { k -> cos * toYuv[1][k] - sin * toYuv[2][k] }
            else -> DoubleArray(3) { k -> sin * toYuv[1][k] + cos * toYuv[2][k] }
        }
    }
    val result = FloatArray(20)
    for (row in 0 until 3) {
        for (column in 0 until 3) {
            var value = 0.0
            for (k in 0 until 3) value += toRgb[row][k] * turned[k][column]
            result[row * 5 + column] = value.toFloat()
        }
    }
    result[18] = 1f
    return result
}

/**
 * The contrast `eq` is given: at least 0. Below it `eq` turns the picture into
 * its negative, which is not "less contrast"; 0 is a flat grey, as low as it goes.
 * The preview and the export both use it, so they agree.
 */
fun eqContrast(contrast: Float): Float = contrast.coerceIn(EQ_CONTRAST_MIN, EQ_CONTRAST_MAX)

/** The saturation `eq` is given: 0 (grey) to 3, its own range. */
fun eqSaturation(saturation: Float): Float = saturation.coerceIn(0f, EQ_SATURATION_MAX)

/** The lowest and highest contrast the editors offer and the export uses. */
const val EQ_CONTRAST_MIN = 0f
const val EQ_CONTRAST_MAX = 2f
private const val EQ_SATURATION_MAX = 3f

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
