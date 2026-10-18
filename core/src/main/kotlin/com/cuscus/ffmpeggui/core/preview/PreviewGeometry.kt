package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Rotation

/*
 * Frame shapes and colours as the export produces them, for the live preview.
 * Ratios are width / height.
 */

/** Ratio of a frame of [ratio] once turned by [rotation]. */
fun turnedRatio(ratio: Float, rotation: Rotation): Float =
    if ((rotation == Rotation.DEG_90 || rotation == Rotation.DEG_270) && ratio > 0f) 1f / ratio else ratio

/** Ratio of the part [crop] keeps of a frame of [ratio]. */
fun croppedRatio(ratio: Float, crop: CropRect?): Float =
    if (crop == null || crop.isFull || crop.height <= 0f) ratio else crop.width * ratio / crop.height

/** Ratio of a frame of [ratio] after the geometry of [effects] (turn, then crop). */
fun Effects.frameRatio(ratio: Float): Float = croppedRatio(turnedRatio(ratio, rotation), effectiveCrop)

/**
 * Colour matrix of the `eq` filter of [effects], or null when it changes nothing.
 * Same 4x5 layout as [eqColorMatrix].
 */
fun Effects.eqMatrix(highDefinition: Boolean = true): FloatArray? {
    val color = color
    if (isNeutralEq(color.brightness, color.contrast, color.saturation)) return null
    return eqColorMatrix(color.brightness, color.contrast, color.saturation, highDefinition)
}

/**
 * The matrix that applies [inner] first and then [outer]: a clip's colour,
 * then the whole video's. Null stands for "no change".
 */
fun combineColorMatrices(outer: FloatArray?, inner: FloatArray?): FloatArray? {
    if (outer == null) return inner
    if (inner == null) return outer
    val result = FloatArray(20)
    for (row in 0 until 4) {
        for (column in 0 until 5) {
            var value = if (column == 4) outer[row * 5 + 4] else 0f
            for (k in 0 until 4) value += outer[row * 5 + k] * inner[k * 5 + column]
            result[row * 5 + column] = value
        }
    }
    return result
}

/** [argb] (alpha kept) passed through [matrix], each channel clamped to 0..255. */
fun applyColorMatrix(argb: Long, matrix: FloatArray?): Long {
    if (matrix == null) return argb
    val channels = floatArrayOf(
        ((argb shr 16) and 0xFF).toFloat(),
        ((argb shr 8) and 0xFF).toFloat(),
        (argb and 0xFF).toFloat(),
        ((argb shr 24) and 0xFF).toFloat(),
    )
    var result = argb and 0xFF000000L
    for (row in 0 until 3) {
        var value = matrix[row * 5 + 4]
        for (k in 0 until 4) value += matrix[row * 5 + k] * channels[k]
        result = result or (value.toInt().coerceIn(0, 255).toLong() shl (16 - row * 8))
    }
    return result
}
