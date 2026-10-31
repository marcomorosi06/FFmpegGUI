package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.ImageBorder
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rounded corners of a picture file, from square to a circle. */
class RoundCornersTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun photo(format: OutputFormat, edit: ImageEdit) = ConversionRequest(
        format = format,
        inputFileName = "photo.jpg",
        sources = listOf(SourceInfo(width = 400, height = 400, hasAudio = false, isStill = true)),
        image = edit,
    )

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun graph(request: ConversionRequest): String = args(request).let { it[it.indexOf("-filter_complex") + 1] }

    private fun opaque(w: Int, h: Int, color: Int = 0x336699) = Raster(w, h, IntArray(w * h) { (0xFF shl 24) or color })

    @Test
    fun `square corners do nothing`() {
        assertTrue(cornerFilters(0f, fill = null).isEmpty())
        val g = graph(photo(OutputFormat.PNG, ImageEdit()))
        assertFalse(g, g.contains("geq"))
        val raster = opaque(4, 4)
        assertEquals(raster, roundCorners(raster, 0f, fill = null))
    }

    @Test
    fun `a png rounds last and keeps the cut-off corners transparent`() {
        val request = photo(OutputFormat.PNG, ImageEdit(corners = 0.5f, border = ImageBorder(widthFraction = 0.05f), output = com.cuscus.ffmpeggui.core.model.ImageOutput(maxSide = 200)))
        val g = graph(request)
        val pad = g.indexOf("pad=")
        val scale = g.lastIndexOf("scale=")
        val geq = g.indexOf("geq=")
        // After the border and the output size: the whole picture, at its size.
        assertTrue(g, pad in 0 until scale && scale < geq)
        assertTrue(g, g.contains("format=rgba,geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='alpha(X,Y)*clip(max(0.5*min(W,H)/2,0.5)-hypot("))
        assertTrue(g, g.contains(":i=nearest"))
        val a = args(request)
        assertEquals("rgba", a[a.indexOf("-pix_fmt") + 1])
    }

    @Test
    fun `a jpg fills the corners black by default, or with the colour chosen`() {
        val request = photo(OutputFormat.JPG, ImageEdit(corners = 1f))
        val g = graph(request)
        assertTrue(g, g.contains("format=gbrp,geq=r='r(X,Y)*clip("))
        assertTrue(g, g.contains("+0*(1-clip("))
        val a = args(request)
        assertEquals("yuvj444p", a[a.indexOf("-pix_fmt") + 1])
        // Transparent asked of a JPG: black too.
        assertEquals(0x000000, ImageEdit(cornerColor = ImageEdit.TRANSPARENT_CORNERS).cornerFillFor(OutputFormat.JPG))
        val red = graph(photo(OutputFormat.JPG, ImageEdit(corners = 1f, cornerColor = 0xFFFF0000L)))
        assertTrue(red, red.contains("r='r(X,Y)*clip(") && red.contains("+255*(1-clip(") && red.contains("g='g(X,Y)*clip("))
    }

    @Test
    fun `a png can fill its corners with a colour, keeping its own transparency`() {
        val edit = ImageEdit(corners = 0.4f, cornerColor = 0xFF00FF00L)
        assertEquals(0x00FF00, edit.cornerFillFor(OutputFormat.PNG))
        assertEquals(null, ImageEdit(corners = 0.4f).cornerFillFor(OutputFormat.PNG))
        val g = graph(photo(OutputFormat.PNG, edit))
        assertTrue(g, g.contains("format=rgba,geq=r='r(X,Y)*clip("))
        assertTrue(g, g.contains(":a='alpha(X,Y)*clip("))
        val out = roundCorners(opaque(20, 20), 1f, fill = 0x00FF00, keepAlpha = true)
        assertEquals(0xFF00FF00.toInt(), out.pixels[0])
    }

    @Test
    fun `fully round, a square picture becomes a circle`() {
        val out = roundCorners(opaque(40, 40), 1f, fill = null)
        fun alpha(x: Int, y: Int) = out.pixels[y * 40 + x] ushr 24
        assertEquals(0, alpha(0, 0))
        assertEquals(0, alpha(39, 39))
        assertEquals(0, alpha(4, 4))
        assertEquals(255, alpha(20, 20))
        // The middle of every side touches the edge (nearly whole: the circle's curve).
        assertTrue(alpha(20, 0) >= 250)
        assertTrue(alpha(0, 20) >= 250)
        assertTrue(alpha(39, 20) >= 250)
        // Its colours stay.
        assertEquals(0x336699, out.pixels[20 * 40 + 20] and 0xFFFFFF)
    }

    @Test
    fun `a long picture becomes a pill and the edge is smoothed`() {
        val out = roundCorners(opaque(80, 20), 1f, fill = null)
        fun alpha(x: Int, y: Int) = out.pixels[y * 80 + x] ushr 24
        assertEquals(0, alpha(0, 0))
        // The straight sides stay whole.
        assertEquals(255, alpha(40, 0))
        assertEquals(255, alpha(40, 19))
        // Somewhere on the curve a pixel is partly covered.
        assertTrue((0 until 10).any { alpha(it, 3) in 1..254 })
    }

    @Test
    fun `without transparency the corners take their colour and stay opaque`() {
        val out = roundCorners(opaque(20, 20, color = 0x000000), 1f, fill = 0xFFFFFF, keepAlpha = false)
        assertEquals(0xFFFFFFFF.toInt(), out.pixels[0])
        assertEquals(0xFF000000.toInt(), out.pixels[10 * 20 + 10])
        assertTrue(out.pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `the port follows the filter's expression`() {
        // The cover where the formula says it: the corner pixel of a radius of 10 at its corner.
        val c = cornerCover(0, 0, 20, 20, 1f)
        val r = 10.0
        val d = Math.hypot(r - 0.5, r - 0.5)
        assertEquals((r - d + 0.5).coerceIn(0.0, 1.0), c, 0.0)
        // Inside, fully covered.
        assertEquals(1.0, cornerCover(10, 10, 20, 20, 1f), 0.0)
    }

    @Test
    fun `corners are saved with the project`() {
        val edit = ImageEdit(corners = 0.35f)
        assertEquals(edit, ModelJson.imageEdit(ModelJson.write(edit)))
        val coloured = ImageEdit(corners = 0.35f, cornerColor = 0xFF123456L)
        assertEquals(coloured, ModelJson.imageEdit(ModelJson.write(coloured)))
        val clear = ImageEdit(corners = 0.35f, cornerColor = ImageEdit.TRANSPARENT_CORNERS)
        assertEquals(clear, ModelJson.imageEdit(ModelJson.write(clear)))
    }
}
