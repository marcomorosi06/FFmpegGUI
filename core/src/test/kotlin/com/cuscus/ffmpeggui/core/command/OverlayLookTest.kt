package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.image.OverlayDecoration
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.OverlayLook
import com.cuscus.ffmpeggui.core.model.OverlayShape
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.project.JsonText
import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A picture's look: the crop, shape, blur, outline and shadow, in the export and in the preview. */
class OverlayLookTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true)),
    )

    private fun graph(placement: ImagePlacement): String {
        val request = video.copy(imageOverlays = listOf(placement))
        val args = builder.build(request, MediaPaths.aliasesFor(request)).arguments
        return args[args.indexOf("-filter_complex") + 1]
    }

    @Test
    fun `a plain look changes nothing`() {
        val plain = graph(ImagePlacement())
        assertFalse(plain, plain.contains("crop="))
        assertFalse(plain, plain.contains("geq"))
        assertTrue(OverlayLook.NONE.isPlain)
    }

    @Test
    fun `the crop comes before sizing, the look after it`() {
        val look = OverlayLook(
            crop = CropRect(0.1f, 0.2f, 0.5f, 0.6f),
            shape = OverlayShape.ELLIPSE,
            borderWidth = 0.03f,
            shadowOpacity = 0.5f,
        )
        val g = graph(ImagePlacement(look = look))
        val crop = g.indexOf("crop=w='max(2,trunc(iw*0.5000/2)*2)':h='max(2,trunc(ih*0.6000/2)*2)':x='trunc(iw*0.1000)':y='trunc(ih*0.2000)'")
        val sizing = g.indexOf("scale=w=128:")
        val shape = g.indexOf("geq=")
        assertTrue(g, crop in 0 until sizing)
        assertTrue(g, shape > sizing)
        assertTrue(g, g.contains("pad=w='iw+2*trunc(iw*${"%.4f".format(java.util.Locale.US, look.margin)})'"))
        assertTrue(g, g.contains("lut=y='clip(val*16,0,255)'"))
        assertTrue(g, g.contains("lutrgb=a=0"))
    }

    @Test
    fun `a turning picture is placed by the box of its look`() {
        val look = OverlayLook(crop = CropRect(0f, 0f, 0.5f, 1f), borderWidth = 0.05f)
        assertEquals(1f, look.shownAspect(2f), 1e-6f)
        // 1.1 wide, 1.1 high: still square.
        assertEquals(1f, look.laidAspect(2f), 1e-6f)
        val wide = OverlayLook(borderWidth = 0.1f)
        // A 2:1 picture, 0.1 of its width around it: (1 + 0.2) / (0.5 + 0.2).
        assertEquals(1.2f / 0.7f, wide.laidAspect(2f), 1e-5f)
    }

    @Test
    fun `the preview's decoration has the export's sizes`() {
        val picture = Raster(100, 50, IntArray(100 * 50) { -0x1 })
        val look = OverlayLook(borderWidth = 0.05f, shadowOpacity = 0.5f, shadowBlur = 0.02f, shadowX = 0.03f, shadowY = 0.01f)
        val out = OverlayDecoration.decorate(picture, look)
        val margin = look.pixels(100, 50).margin
        assertEquals(100 + 2 * margin, out.width)
        assertEquals(50 + 2 * margin, out.height)
        // The outline reaches beyond the picture; the shadow falls to the right.
        val row = out.height / 2
        assertTrue(out.pixels[row * out.width + margin - 2] ushr 24 > 200)
        val rightGlow = out.pixels[row * out.width + out.width - margin + 6] ushr 24
        val leftGlow = out.pixels[row * out.width + margin - 9] ushr 24
        assertTrue("$rightGlow > $leftGlow", rightGlow > leftGlow)
    }

    @Test
    fun `an ellipse leaves the corners transparent and the middle whole`() {
        val picture = Raster(80, 40, IntArray(80 * 40) { -0x1 })
        val out = OverlayDecoration.decorate(picture, OverlayLook(shape = OverlayShape.ELLIPSE))
        assertEquals(0, out.pixels[0] ushr 24)
        assertEquals(255, out.pixels[20 * 80 + 40] ushr 24)
        val rounded = OverlayDecoration.decorate(picture, OverlayLook(shape = OverlayShape.ROUNDED, cornerRadius = 1f))
        assertEquals(0, rounded.pixels[0] ushr 24)
        assertEquals(255, rounded.pixels[20 * 80 + 2] ushr 24)
    }

    @Test
    fun `a look is saved and read back`() {
        val look = OverlayLook(
            crop = CropRect(0.1f, 0.1f, 0.7f, 0.8f), shape = OverlayShape.ROUNDED, cornerRadius = 0.6f, blur = 0.01f,
            borderWidth = 0.02f, borderColor = 0x123456, shadowOpacity = 0.4f, shadowBlur = 0.05f, shadowX = -0.02f, shadowY = 0.07f, shadowColor = 0x654321,
        )
        val text = JsonText.write(ModelJson.write(look))
        assertEquals(look, ModelJson.overlayLook(JsonText.parse(text) as Json.Obj))
    }
}
