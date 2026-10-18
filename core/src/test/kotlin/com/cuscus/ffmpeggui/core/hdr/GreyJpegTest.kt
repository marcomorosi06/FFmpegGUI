package com.cuscus.ffmpeggui.core.hdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs

class GreyJpegTest {

    @Test
    fun `a grey JPG reads back as the values it was made of`() {
        // Odd sizes: blocks cut at the edges.
        val w = 37
        val h = 21
        val values = ByteArray(w * h) { i -> ((i % w) * 255 / (w - 1) / 2 + (i / w) * 4).coerceIn(0, 255).toByte() }
        val jpeg = GreyJpeg.encode(values, w, h, quality = 95)
        val picture = ImageIO.read(ByteArrayInputStream(jpeg))
        assertEquals(w, picture.width)
        assertEquals(h, picture.height)
        assertEquals(1, picture.raster.numBands)
        var worst = 0
        for (y in 0 until h) for (x in 0 until w) {
            val read = picture.raster.getSample(x, y, 0)
            worst = maxOf(worst, abs(read - (values[y * w + x].toInt() and 0xFF)))
        }
        assertTrue("worst error $worst", worst <= 6)
    }

    @Test
    fun `flat and extreme pictures stay so`() {
        for (v in listOf(0, 255, 128)) {
            val jpeg = GreyJpeg.encode(ByteArray(64 * 16) { v.toByte() }, 64, 16, quality = 90)
            val picture = ImageIO.read(ByteArrayInputStream(jpeg))
            assertTrue(abs(picture.raster.getSample(10, 5, 0) - v) <= 1)
        }
    }
}
