package com.cuscus.ffmpeggui.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureStretchTest {

    private val canvas = 1000f

    /** The picture's height on the canvas, in pixels, for a shown shape at stretch 1 of [own]. */
    private fun height(scaleW: Float, stretch: Float, own: Float) = scaleW * canvas / (own * stretch)

    @Test
    fun `the side handle keeps the height and makes it wider`() {
        val own = 1.5f
        val pulled = PictureStretch.sideways(scaleW = 0.4f, stretch = 1f, room = 0f, canvasWidth = canvas, drag = 100f)
        // 400 px wide, 2 x 100 more: 600 px, half as wide again.
        assertEquals(0.6f, pulled.scaleW, 1e-4f)
        assertEquals(1.5f, pulled.stretch, 1e-4f)
        assertEquals(height(0.4f, 1f, own), height(pulled.scaleW, pulled.stretch, own), 1e-2f)
        assertFalse(pulled.ownShape)
    }

    @Test
    fun `the side handle counts the room of the look in the box`() {
        // A box of 400 x 1.5 = 600 px; 2 x 150 more is half as wide again.
        val pulled = PictureStretch.sideways(scaleW = 0.4f, stretch = 1f, room = 0.5f, canvasWidth = canvas, drag = 150f)
        assertEquals(0.6f, pulled.scaleW, 1e-4f)
        assertEquals(1.5f, pulled.stretch, 1e-4f)
    }

    @Test
    fun `the bottom handle keeps the width and makes it taller`() {
        val own = 2f
        // 500 px wide, 250 high; 2 x 125 more: 500 high, so half as wide for its height.
        val pulled = PictureStretch.upDown(scaleW = 0.5f, stretch = 1f, shown = own, room = 0f, canvasWidth = canvas, drag = 125f)
        assertEquals(0.5f, pulled.scaleW, 0f)
        assertEquals(0.5f, pulled.stretch, 1e-4f)
        assertEquals(500f, height(pulled.scaleW, pulled.stretch, own), 1e-2f)
    }

    @Test
    fun `the bottom handle counts the room of the look in the box`() {
        // 500 px wide, 250 high, with 0.2 x 500 = 100 px of room: a box 350 high.
        // 2 x 125 more: the picture 500 high.
        val pulled = PictureStretch.upDown(scaleW = 0.5f, stretch = 1f, shown = 2f, room = 0.2f, canvasWidth = canvas, drag = 125f)
        assertEquals(0.5f, pulled.stretch, 1e-4f)
    }

    @Test
    fun `near its own shape it snaps back to it`() {
        // Stretched to 1.5, then pulled in to 1.02: its own shape exactly, at its own width for that height.
        val sideways = PictureStretch.sideways(scaleW = 0.6f, stretch = 1.5f, room = 0f, canvasWidth = canvas, drag = -98f)
        assertEquals(1f, sideways.stretch, 0f)
        assertEquals(0.4f, sideways.scaleW, 1e-4f)
        assertTrue(sideways.ownShape)
        val upDown = PictureStretch.upDown(scaleW = 0.5f, stretch = 0.5f, shown = 1f, room = 0f, canvasWidth = canvas, drag = -122f)
        assertEquals(1f, upDown.stretch, 0f)
        assertTrue(upDown.ownShape)
    }

    @Test
    fun `a pull stays within the stretch and the width the export lays`() {
        val wide = PictureStretch.sideways(scaleW = 0.3f, stretch = 1f, room = 0f, canvasWidth = canvas, drag = 1e6f)
        assertTrue(wide.stretch <= ImagePlacement.MAX_STRETCH)
        assertTrue(wide.scaleW <= ImagePlacement.MAX_WIDTH)
        val narrow = PictureStretch.sideways(scaleW = 0.3f, stretch = 1f, room = 0f, canvasWidth = canvas, drag = -1e6f)
        assertTrue(narrow.stretch >= ImagePlacement.MIN_STRETCH)
        assertTrue(narrow.scaleW >= PictureStretch.MIN_WIDTH)
        val tall = PictureStretch.upDown(scaleW = 0.3f, stretch = 1f, shown = 1f, room = 0f, canvasWidth = canvas, drag = 1e6f)
        assertEquals(ImagePlacement.MIN_STRETCH, tall.stretch, 0f)
        val flat = PictureStretch.upDown(scaleW = 0.3f, stretch = 1f, shown = 1f, room = 0f, canvasWidth = canvas, drag = -1e6f)
        assertEquals(ImagePlacement.MAX_STRETCH, flat.stretch, 0f)
    }

    @Test
    fun `nothing to pull on keeps it as it is`() {
        val none = PictureStretch.sideways(scaleW = 0.4f, stretch = 2f, room = 0f, canvasWidth = 0f, drag = 50f)
        assertEquals(0.4f, none.scaleW, 0f)
        assertEquals(2f, none.stretch, 0f)
    }

    @Test
    fun `a caption's side handle makes it wider at the same size`() {
        // 300 px wide, 2 x 75 more: half as wide again.
        val pulled = PictureStretch.textSideways(size = 0.05f, stretch = 1f, width = 300f, drag = 75f)
        assertEquals(0.05f, pulled.size, 0f)
        assertEquals(1.5f, pulled.stretch, 1e-4f)
        // Back near its own shape: exactly it.
        assertTrue(PictureStretch.textSideways(0.05f, 1.5f, 450f, -70f).ownShape)
    }

    @Test
    fun `a caption's bottom handle makes it taller and keeps its width`() {
        // 40 px high, 2 x 20 more: twice as high, so half as stretched for the same width.
        val pulled = PictureStretch.textUpDown(size = 0.05f, stretch = 1f, height = 40f, drag = 20f, minSize = 0.02f, maxSize = 0.5f)
        assertEquals(0.1f, pulled.size, 1e-5f)
        assertEquals(0.5f, pulled.stretch, 1e-4f)
        // Its width: size x stretch, kept.
        assertEquals(0.05f, pulled.size * pulled.stretch, 1e-5f)
        // Stretched twice, made twice as high: its own shape again, at twice the size.
        val back = PictureStretch.textUpDown(size = 0.05f, stretch = 2f, height = 40f, drag = 19.5f, minSize = 0.02f, maxSize = 0.5f)
        assertTrue(back.ownShape)
        assertEquals(0.1f, back.size, 1e-5f)
        assertEquals(1f, back.stretch, 0f)
        // Within the sizes a caption has.
        assertEquals(0.5f, PictureStretch.textUpDown(0.3f, 4f, 40f, 1e6f, 0.02f, 0.5f).size, 0f)
    }

    @Test
    fun `a drag is measured along the turned box`() {
        assertEquals(10f, PictureStretch.along(10f, 0f, 0f, sideways = true), 1e-4f)
        assertEquals(0f, PictureStretch.along(10f, 0f, 0f, sideways = false), 1e-4f)
        // Turned a quarter clockwise, its width runs down the screen.
        assertEquals(10f, PictureStretch.along(0f, 10f, 90f, sideways = true), 1e-4f)
        assertEquals(-10f, PictureStretch.along(10f, 0f, 90f, sideways = false), 1e-4f)
    }
}
