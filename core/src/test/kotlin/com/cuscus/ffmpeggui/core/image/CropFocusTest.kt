package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.model.CropRect
import org.junit.Assert.assertEquals
import org.junit.Test

class CropFocusTest {

    private fun frame(crop: CropRect, margin: Float = 0f, maxScale: Float = 8f) =
        CropFocus.frame(crop, 0f, 0f, 1000f, 500f, 1000f, 500f, 1000f, 500f, margin, maxScale)

    @Test
    fun `a whole or nearly whole frame leaves the picture as it fits`() {
        assertEquals(CropFocus.FIT, frame(CropRect()))
        assertEquals(CropFocus.FIT, frame(CropRect(0.05f, 0.05f, 0.9f, 0.9f)))
    }

    @Test
    fun `a small frame in the middle fills the stage, centred`() {
        val view = frame(CropRect(0.375f, 0.375f, 0.25f, 0.25f))
        assertEquals(4f, view.scale, 1e-4f)
        assertEquals(0f, view.x, 1e-3f)
        assertEquals(0f, view.y, 1e-3f)
    }

    @Test
    fun `the frame's middle comes to the stage's middle`() {
        // A quarter of the picture, top left: its middle at (125, 62.5), 375 and 187.5 from the content's.
        val view = frame(CropRect(0f, 0f, 0.25f, 0.25f), margin = 0f)
        assertEquals(4f, view.scale, 1e-4f)
        // The content at 4x is 4000 x 2000: it may move 1500 x 750, which is exactly where the corner's middle comes.
        assertEquals(1500f, view.x, 1e-2f)
        assertEquals(750f, view.y, 1e-2f)
    }

    @Test
    fun `the margin keeps room for the handles, and the zoom has a ceiling`() {
        val view = frame(CropRect(0.45f, 0.45f, 0.1f, 0.1f), margin = 50f)
        // 900 / 100 and 400 / 50: the height decides, 8.
        assertEquals(8f, view.scale, 1e-4f)
        assertEquals(3f, frame(CropRect(0.45f, 0.45f, 0.1f, 0.1f), maxScale = 3f).scale, 1e-4f)
    }

    @Test
    fun `a frame at an edge is moved no farther than the margin past it`() {
        val view = frame(CropRect(0.75f, 0.375f, 0.25f, 0.25f), margin = 20f)
        val limit = (1000f * view.scale - 1000f) / 2f + 20f
        assertEquals(-limit, view.x, 1e-2f)
    }

    @Test
    fun `nothing to frame without sizes`() {
        assertEquals(CropFocus.FIT, CropFocus.frame(CropRect(0.4f, 0.4f, 0.2f, 0.2f), 0f, 0f, 0f, 0f, 0f, 0f, 100f, 100f, 0f, 8f))
    }
}
