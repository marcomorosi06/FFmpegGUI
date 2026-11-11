package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.command.VideoFilters
import com.cuscus.ffmpeggui.core.model.ColorSettings
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

/*
 * The finishing touches of the video editor's Details tool (sharpness, blur,
 * vignette and grain; see ColorSettings) as the preview draws them, from the
 * very numbers the export's filters are written with (VideoFilters):
 *
 *  - sharpness: unsharp's 5x5 luma kernel, amount sharpness x 1.5 (below 0 it
 *    softens), the kernel as wide on screen as five pixels of the footage;
 *  - blur: gblur's sigma, in pixels of the footage, scaled to the screen;
 *  - vignette: vignette's own falloff, cos(angle x d / dmax)^4, d the distance
 *    from the middle and dmax half the diagonal;
 *  - grain: noise's uniform strength, as much either way on the 0..255 scale,
 *    a new draw every frame.
 *
 * In the export's order: sharpness, blur, vignette, grain.
 */
object FinishingTouches {

    /** unsharp's luma amount for [color] (-1.5..1.5; 0: none). */
    fun unsharpAmount(color: ColorSettings): Float = color.sharpness.coerceIn(-1f, 1f) * VideoFilters.UNSHARP_MAX_AMOUNT

    /** gblur's sigma for [color], in pixels of the screen at [screenPerPixel] screen pixels a footage pixel. */
    fun blurOnScreen(color: ColorSettings, screenPerPixel: Float): Float =
        color.gaussianBlur.coerceIn(0f, ColorSettings.BLUR_MAX) * screenPerPixel.coerceAtLeast(0f)

    /** vignette's angle for [color] (radians; 0: none). */
    fun vignetteAngle(color: ColorSettings): Float = (color.vignette.coerceIn(0f, 1f) * ColorSettings.VIGNETTE_MAX_ANGLE).toFloat()

    /**
     * How much of the picture vignette leaves at ([x], [y]) of a [width] x
     * [height] frame (1 in the middle, less towards the corners), for [angle].
     */
    fun vignetteFactor(x: Float, y: Float, width: Float, height: Float, angle: Float): Float {
        if (angle <= 0f || width <= 0f || height <= 0f) return 1f
        val reach = hypot(width / 2.0, height / 2.0)
        val d = (hypot(x - width / 2.0, y - height / 2.0) / reach).coerceIn(0.0, 1.0)
        return cos(angle * d).pow(4).toFloat()
    }

    /** How far noise moves a value either way, as a share of full scale (0..1), for [color]. */
    fun grainAmplitude(color: ColorSettings): Float = color.filmGrain.coerceIn(0f, ColorSettings.GRAIN_MAX).toInt().coerceIn(0, 100) / 255f

    /**
     * The vignette as a radial gradient for a preview without shaders: the
     * dark it lays (1 - factor) at [STOPS] distances from the middle, as
     * fractions of half the diagonal.
     */
    fun vignetteStops(angle: Float): List<Pair<Float, Float>> = (0..STOPS).map { i ->
        val d = i / STOPS.toFloat()
        d to (1f - cos(angle * d.toDouble()).pow(4).toFloat()).coerceIn(0f, 1f)
    }

    /** How many steps the gradient of [vignetteStops] takes. */
    const val STOPS = 12
}
