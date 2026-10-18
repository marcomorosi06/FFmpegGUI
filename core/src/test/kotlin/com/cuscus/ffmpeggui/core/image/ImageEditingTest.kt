package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageBorder
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageEffect
import com.cuscus.ffmpeggui.core.model.ImageFilter
import com.cuscus.ffmpeggui.core.model.ImageTone
import com.cuscus.ffmpeggui.core.model.Rotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditingTest {

    private fun solid(w: Int, h: Int, r: Int, g: Int, b: Int) = Raster(w, h, IntArray(w * h) { rgb(r, g, b) })

    private fun Raster.at(x: Int, y: Int): Triple<Int, Int, Int> {
        val p = pixels[y * width + x]
        return Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
    }

    @Test
    fun `an untouched picture has no steps`() {
        assertTrue(imageStages(ImageEdit(), Effects(), 100, 80).isEmpty())
    }

    @Test
    fun `steps come in the documented order`() {
        val stages = imageStages(
            ImageEdit(
                straightenDegrees = 5f, perspectiveV = 0.5f, tone = ImageTone(exposure = 1f, saturation = 0.5f),
                effect = ImageEffect.PIXELATE, sharpen = 0.5f, vignette = 0.5f, grain = 0.5f,
            ),
            Effects(rotation = Rotation.DEG_90, flipHorizontal = true, crop = CropRect(0.1f, 0.1f, 0.8f, 0.8f)),
            400, 300,
        )
        assertEquals(
            listOf("hflip", "transpose", "perspective", "rotate", "crop", "lutrgb", "colorchannelmixer", "pixelize", "convolution", "vignette", "noise"),
            stages.map { it.filter.substringBefore('=') },
        )
    }

    @Test
    fun `the tone table is what the expression gives`() {
        val lut = imageStages(ImageEdit(tone = ImageTone(exposure = 1f)), Effects(), 10, 10).single() as ImageStage.Lut
        val (red) = lut.tables()
        // Twice as bright, clipped at white.
        assertEquals(0, red[0])
        assertEquals(200, red[100])
        assertEquals(255, red[200])
        assertTrue(lut.filter, lut.filter.startsWith("lutrgb=r='(st(0,(val*"))
        assertEquals(lut.red.eval(100.0), lut.green.eval(100.0), 1e-9)
    }

    @Test
    fun `mono makes grey, warmth warms`() {
        val grey = render(solid(4, 4, 200, 50, 20), imageStages(ImageEdit(filter = ImageFilter.MONO), Effects(), 4, 4)).at(0, 0)
        assertEquals(grey.first, grey.second)
        assertEquals(grey.second, grey.third)
        val warm = render(solid(4, 4, 128, 128, 128), imageStages(ImageEdit(tone = ImageTone(warmth = 1f)), Effects(), 4, 4)).at(0, 0)
        assertTrue(warm.first > 128 && warm.third < 128)
    }

    @Test
    fun `quarter turns, mirrors and crops move the pixels`() {
        val r = Raster(3, 2, intArrayOf(rgb(1, 0, 0), rgb(2, 0, 0), rgb(3, 0, 0), rgb(4, 0, 0), rgb(5, 0, 0), rgb(6, 0, 0)))
        val turned = ImageStage.QuarterTurn(clockwise = true).apply(r)
        assertEquals(2, turned.width)
        assertEquals(3, turned.height)
        // Clockwise: the bottom-left pixel goes to the top left.
        assertEquals(4, turned.at(0, 0).first)
        assertEquals(1, turned.at(1, 0).first)
        val mirrored = ImageStage.Mirror(horizontal = true).apply(r)
        assertEquals(3, mirrored.at(0, 0).first)
        val cropped = ImageStage.Crop(1.0 / 3, 0.5, 2.0 / 3, 0.5).apply(r)
        assertEquals(listOf(5, 6), cropped.pixels.map { (it shr 16) and 0xFF })
    }

    @Test
    fun `straightening zooms just enough to fill the frame`() {
        assertEquals(1.0, straightenZoom(0.0, 400, 300), 1e-9)
        val zoom = straightenZoom(10.0, 400, 300)
        val a = Math.toRadians(10.0)
        assertTrue(zoom * 400 >= 400 * Math.cos(a) + 300 * Math.sin(a) - 1e-9)
        assertTrue(zoom * 300 >= 400 * Math.sin(a) + 300 * Math.cos(a) - 1e-9)
        // The turned picture keeps the part it fills, never scaled up.
        val (w, h) = editedSize(ImageEdit(straightenDegrees = 10f), Effects(), 400, 300)
        assertEquals((400 / zoom).toInt().toDouble(), w.toDouble(), 1.0)
        assertEquals((300 / zoom).toInt().toDouble(), h.toDouble(), 1.0)
        val out = render(solid(400, 300, 90, 90, 90), imageStages(ImageEdit(straightenDegrees = 10f), Effects(), 400, 300))
        assertEquals(w to h, out.width to out.height)
        // No black corner shows.
        assertTrue(out.pixels.all { (it and 0xFFFFFF) == 0x5A5A5A })
    }

    @Test
    fun `sizes follow the turn and the crop, the border follows the shorter side`() {
        assertEquals(300 to 400, editedSize(ImageEdit(), Effects(rotation = Rotation.DEG_270), 400, 300))
        assertEquals(200 to 150, editedSize(ImageEdit(), Effects(crop = CropRect(0f, 0f, 0.5f, 0.5f)), 400, 300))
        assertEquals(15, borderPixels(ImageEdit(border = ImageBorder(0.05f)), 400, 300))
        assertEquals(0, borderPixels(ImageEdit(), 400, 300))
    }

    @Test
    fun `the frame of a picture is kept inside its clip`() {
        assertEquals(1_500L, pictureFrameMs(ImageEdit(frameMs = 1_500), 2_000))
        assertEquals(1_900L, pictureFrameMs(ImageEdit(frameMs = 5_000), 2_000))
        assertEquals(0L, pictureFrameMs(ImageEdit(frameMs = -5), null))
        assertEquals(9_999L, pictureFrameMs(ImageEdit(frameMs = 9_999), null))
    }

    @Test
    fun `expressions are written so that FFmpeg reads the same numbers`() {
        assertEquals("0.1", exact(0.1))
        assertEquals("255", exact(255.0))
        assertTrue(exact(1e-7).let { !it.contains('E') && it.toDouble() == 1e-7 })
    }

    @Test
    fun `effects change the picture`() {
        val source = Raster(8, 8, IntArray(64) { i -> rgb((i * 4) % 256, (i * 9) % 256, (i * 13) % 256) })
        for (effect in ImageEffect.entries.filter { it != ImageEffect.NONE }) {
            val out = render(source, imageStages(ImageEdit(effect = effect, effectAmount = 0.8f), Effects(), 8, 8))
            assertTrue("$effect", !out.pixels.contentEquals(source.pixels))
        }
    }
}
