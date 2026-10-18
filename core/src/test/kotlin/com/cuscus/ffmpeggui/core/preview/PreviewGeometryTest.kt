package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Rotation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PreviewGeometryTest {

    @Test
    fun `turning and cropping change the frame shape`() {
        assertEquals(9f / 16f, turnedRatio(16f / 9f, Rotation.DEG_90), 1e-6f)
        assertEquals(16f / 9f, turnedRatio(16f / 9f, Rotation.DEG_180), 1e-6f)
        // Half the width of a 16:9 frame is 8:9.
        assertEquals(8f / 9f, croppedRatio(16f / 9f, CropRect(0f, 0f, 0.5f, 1f)), 1e-6f)
        assertEquals(16f / 9f, croppedRatio(16f / 9f, CropRect()), 1e-6f)
        // Turned to 9:16, then the top half kept: 9:8.
        val effects = Effects(rotation = Rotation.DEG_270, crop = CropRect(0f, 0f, 1f, 0.5f))
        assertEquals(9f / 8f, effects.frameRatio(16f / 9f), 1e-6f)
    }

    @Test
    fun `colour matrices chain like the filters`() {
        val clip = Effects(color = ColorSettings(saturation = 0f)).eqMatrix()!!
        val whole = Effects(color = ColorSettings(brightness = 0.2f)).eqMatrix()!!
        val combined = combineColorMatrices(whole, clip)!!
        val red = 0xFFFF0000
        assertEquals(
            applyColorMatrix(applyColorMatrix(red, clip), whole),
            applyColorMatrix(red, combined),
        )
        assertSame(clip, combineColorMatrices(null, clip))
        assertSame(whole, combineColorMatrices(whole, null))
        assertNull(Effects().eqMatrix())
    }

    @Test
    fun `a colour is clamped and keeps its alpha`() {
        val brighter = Effects(color = ColorSettings(brightness = 1f)).eqMatrix()!!
        assertEquals(0x80FFFFFF, applyColorMatrix(0x80FFFFFF, brighter))
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        assertEquals(0xFF123456, applyColorMatrix(0xFF123456, identity))
        assertArrayEquals(identity, combineColorMatrices(identity, identity), 1e-6f)
    }
}
