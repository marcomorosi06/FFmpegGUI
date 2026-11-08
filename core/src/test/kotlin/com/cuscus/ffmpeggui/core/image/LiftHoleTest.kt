package com.cuscus.ffmpeggui.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs

class LiftHoleTest {

    /** A picture whose every pixel says where it is: x in red, y in green. */
    private fun where(w: Int, h: Int) = Raster(w, h, IntArray(w * h) { i -> (0xFF shl 24) or ((i % w) shl 16) or ((i / w) shl 8) })

    private fun through(stages: List<ImageStage>, raster: Raster): Raster = stages.fold(raster) { r, s -> s.apply(r) }

    @Test
    fun `a point is taken back through mirrors, quarter turns and a crop exactly`() {
        val stages = listOf(
            ImageStage.Mirror(horizontal = true),
            ImageStage.QuarterTurn(clockwise = true),
            ImageStage.Mirror(horizontal = false),
            ImageStage.QuarterTurn(clockwise = false),
            ImageStage.QuarterTurn(clockwise = false),
            ImageStage.Crop(0.1, 0.2, 0.6, 0.5),
        )
        val out = through(stages, where(40, 30))
        for (oy in 0 until out.height) for (ox in 0 until out.width) {
            val p = out.pixels[oy * out.width + ox]
            val (sx, sy) = LiftHole.sourcePoint(stages, ox.toDouble(), oy.toDouble(), 40, 30)
            assertEquals("$ox,$oy", ((p shr 16) and 0xFF).toDouble(), sx, 1e-9)
            assertEquals("$ox,$oy", ((p shr 8) and 0xFF).toDouble(), sy, 1e-9)
        }
    }

    @Test
    fun `a point is taken back through a straighten and a keystone, near enough`() {
        for (stages in listOf(
            listOf<ImageStage>(ImageStage.Straighten(7.0, 1.2)),
            listOf<ImageStage>(ImageStage.Perspective(listOf(0.05, 0.02, 0.97, 0.0, 0.0, 1.0, 1.0, 0.95))),
            listOf(ImageStage.QuarterTurn(true), ImageStage.Straighten(-4.0, 1.1), ImageStage.Crop(0.1, 0.1, 0.8, 0.8)),
        )) {
            val out = through(stages, where(60, 50))
            // Away from the edges, where the samples are all inside.
            for (oy in 4 until out.height - 4) for (ox in 4 until out.width - 4) {
                val p = out.pixels[oy * out.width + ox]
                val (sx, sy) = LiftHole.sourcePoint(stages, ox.toDouble(), oy.toDouble(), 60, 50)
                assertTrue("$stages $ox,$oy: $sx", abs(((p shr 16) and 0xFF) - sx) < 1.5)
                assertTrue("$stages $ox,$oy: $sy", abs(((p shr 8) and 0xFF) - sy) < 1.5)
            }
        }
        // No geometry: the point itself (colour steps move nothing).
        assertEquals(3.0 to 4.0, LiftHole.sourcePoint(emptyList(), 3.0, 4.0, 10, 10))
    }

    @Test
    fun `the hole is see-through with a colour under it, or a colour, its edge soft`() {
        val pixels = intArrayOf(0xFF102030.toInt(), 0xFF102030.toInt(), 0xFF102030.toInt())
        val cover = byteArrayOf(0, 255.toByte(), 128.toByte())
        val clear = LiftHole.punch(pixels, cover, transparent = true, color = 0xFF00FF00, opaqueColor = 0xFFFFFFFF)
        assertEquals(0xFF102030.toInt(), clear[0])
        assertEquals(0x00FFFFFF, clear[1])
        assertEquals(127, clear[2] ushr 24)
        val painted = LiftHole.punch(pixels, cover, transparent = false, color = 0xFF00FF00, opaqueColor = 0xFFFFFFFF)
        assertEquals(0xFF00FF00.toInt(), painted[1])
        assertEquals(255, painted[2] ushr 24)
        // Stretched: the middle of a 2 x 1 cover of 0 and 255 is half.
        val wide = LiftHole.scaled(byteArrayOf(0, 255.toByte()), 2, 1, 4, 1)
        assertEquals(0, wide[0].toInt() and 0xFF)
        assertEquals(255, wide[3].toInt() and 0xFF)
    }

    @Test
    fun `the PNG keeps the colour under a see-through pixel`() {
        val argb = intArrayOf(0x00FF8000, 0x80123456.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        val bytes = ByteArrayOutputStream().also { RgbaPng.write(it, 2, 2, argb) }.toByteArray()
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(2, image.width)
        val raster = image.raster
        val px = IntArray(4)
        raster.getPixel(0, 0, px)
        assertEquals(listOf(0xFF, 0x80, 0x00, 0x00), px.toList())
        raster.getPixel(1, 0, px)
        assertEquals(listOf(0x12, 0x34, 0x56, 0x80), px.toList())
    }

    @Test
    fun `a picture is scaled with the colour under its see-through pixels kept`() {
        val white = 0x00FFFFFF
        val red = 0xFFFF0000.toInt()
        val small = scaledRaster(Raster(4, 2, intArrayOf(white, white, red, red, white, white, red, red)), 2, 1)
        assertEquals(white, small.pixels[0])
        assertEquals(red, small.pixels[1])
        // The same size: itself.
        val same = Raster(1, 1, intArrayOf(red))
        assertTrue(scaledRaster(same, 1, 1) === same)
    }
}
