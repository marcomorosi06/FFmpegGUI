package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.ColorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

class FinishingTouchesTest {

    @Test
    fun `the numbers are those the export writes`() {
        assertEquals(1.5f, FinishingTouches.unsharpAmount(ColorSettings(sharpness = 1f)), 1e-6f)
        assertEquals(-0.75f, FinishingTouches.unsharpAmount(ColorSettings(sharpness = -0.5f)), 1e-6f)
        assertEquals((PI / 5).toFloat(), FinishingTouches.vignetteAngle(ColorSettings(vignette = 0.5f)), 1e-5f)
        assertEquals(12f / 255f, FinishingTouches.grainAmplitude(ColorSettings(filmGrain = 12.7f)), 1e-6f)
        // Five pixels of sigma on footage shown at half its size are two and a half on screen.
        assertEquals(2.5f, FinishingTouches.blurOnScreen(ColorSettings(gaussianBlur = 5f), 0.5f), 1e-6f)
    }

    @Test
    fun `the vignette leaves the middle and darkens the corners as FFmpeg's does`() {
        val angle = (PI / 5).toFloat()
        assertEquals(1f, FinishingTouches.vignetteFactor(50f, 25f, 100f, 50f, angle), 1e-6f)
        val corner = FinishingTouches.vignetteFactor(0f, 0f, 100f, 50f, angle)
        assertEquals(cos(PI / 5).pow(4).toFloat(), corner, 1e-5f)
        assertTrue(FinishingTouches.vignetteFactor(25f, 12f, 100f, 50f, angle) in corner..1f)
        assertEquals(1f, FinishingTouches.vignetteFactor(0f, 0f, 100f, 50f, 0f), 0f)
    }

    @Test
    fun `the gradient of the vignette goes from nothing to the corners' dark`() {
        val angle = (PI / 2.5).toFloat()
        val stops = FinishingTouches.vignetteStops(angle)
        assertEquals(FinishingTouches.STOPS + 1, stops.size)
        assertEquals(0f to 0f, stops.first())
        assertEquals(1f - cos(PI / 2.5).pow(4).toFloat(), stops.last().second, 1e-5f)
        assertTrue(stops.zipWithNext().all { (a, b) -> b.second >= a.second })
    }
}
