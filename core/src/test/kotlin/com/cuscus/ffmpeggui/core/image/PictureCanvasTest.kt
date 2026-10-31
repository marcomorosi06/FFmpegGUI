package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.PictureCanvas
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A picture laid as a layer on a canvas of its own. */
class PictureCanvasTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun photo(format: OutputFormat, edit: ImageEdit, placement: ImagePlacement? = null, stickers: List<ImagePlacement> = emptyList(), texts: List<TextOverlay> = emptyList()) =
        ConversionRequest(
            format = format,
            inputFileName = "photo.jpg",
            sources = listOf(SourceInfo(width = 400, height = 200, hasAudio = false, isStill = true)),
            image = edit,
            photoPlacement = placement,
            imageOverlays = stickers,
            textOverlays = texts,
        )

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun graph(request: ConversionRequest): String = args(request).let { it[it.indexOf("-filter_complex") + 1] }

    @Test
    fun `the canvas is the frame texts and the drawing lie on`() {
        val edit = ImageEdit(canvas = PictureCanvas(1080, 1350))
        assertEquals(1080 to 1350, editedSize(edit, Effects(), 400, 200))
        assertEquals(400 to 200, pictureSize(edit, Effects(), 400, 200))
    }

    @Test
    fun `an unplaced picture fits the canvas, centred`() {
        val wide = fittedPhoto(PictureCanvas(1000, 1000), 400, 200)
        assertEquals(1f, wide.widthFraction, 0f)
        assertEquals(0.5f, wide.x, 0f)
        val tall = fittedPhoto(PictureCanvas(1000, 500), 200, 400)
        assertEquals(0.25f, tall.widthFraction, 1e-6f)
    }

    @Test
    fun `the picture is a layer laid on the canvas`() {
        val g = graph(photo(OutputFormat.PNG, ImageEdit(canvas = PictureCanvas(1080, 1080, background = 0xFF112233L)), ImagePlacement(x = 0.2f, y = 0.8f, widthFraction = 0.5f)))
        assertTrue(g, g.startsWith("color=c=0x112233FF:s=1080x1080:r=1:d=1,format=rgba[cnv];[0:v]format=rgb24[photo];"))
        // Sized against the canvas and placed where it was put, in RGB.
        assertTrue(g, g.contains("[photo]scale=w=540:"))
        assertTrue(g, g.contains("x=(W-w)*0.2000:y=(H-h)*0.8000:format=auto"))
    }

    @Test
    fun `a transparent canvas stays transparent where the format holds it`() {
        val png = photo(OutputFormat.PNG, ImageEdit(canvas = PictureCanvas(500, 500, background = null)))
        assertTrue(graph(png).startsWith("color=c=black@0:"))
        val a = args(png)
        assertEquals("rgba", a[a.indexOf("-pix_fmt") + 1])
        val jpg = photo(OutputFormat.JPG, ImageEdit(canvas = PictureCanvas(500, 500, background = null)))
        assertTrue(graph(jpg).startsWith("color=c=white:"))
    }

    @Test
    fun `the picture is stacked among the stickers by its layer`() {
        val g = graph(
            photo(
                OutputFormat.PNG,
                ImageEdit(canvas = PictureCanvas(800, 800)),
                ImagePlacement(widthFraction = 0.8f, layer = 2),
                stickers = listOf(ImagePlacement(widthFraction = 0.3f, layer = 1)),
            ),
        )
        // The sticker (layer 1) first, then the picture over it.
        assertTrue(g, g.indexOf("[1:v]") in 0 until g.indexOf("[photo]scale"))
        assertFalse(g, g.contains("[0:v]overlay"))
    }

    @Test
    fun `the canvas is saved with the project`() {
        val edit = ImageEdit(canvas = PictureCanvas(1200, 628, background = null))
        assertEquals(edit, ModelJson.imageEdit(ModelJson.write(edit)))
        val opaque = ImageEdit(canvas = PictureCanvas(64, 64, background = 0xFF00FF00L))
        assertEquals(opaque, ModelJson.imageEdit(ModelJson.write(opaque)))
    }
}
