package com.cuscus.ffmpeggui.core.vector

import com.cuscus.ffmpeggui.core.image.Raster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VectorTracerTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val red = 0xFFFF0000.toInt()

    /** A [w] x [h] picture, [paint] giving each pixel's colour. */
    private fun picture(w: Int, h: Int, paint: (Int, Int) -> Int) = Raster(w, h, IntArray(w * h) { paint(it % w, it / w) })

    @Test
    fun `a square on a background is two colours, the background with a hole`() {
        val p = picture(10, 10) { x, y -> if (x in 3..6 && y in 3..6) black else white }
        val image = VectorTracer.trace(p, TraceSettings(colors = 2, smooth = false, speckle = 0))
        assertEquals(2, image.layers.size)
        // The larger colour lies first.
        assertEquals(white, image.layers[0].argb)
        assertEquals(black, image.layers[1].argb)
        assertEquals(2, image.layers[0].ops.count { it is PathOp.MoveTo })
        // The square is its four corners.
        val square = image.layers[1].ops
        assertEquals(PathOp.MoveTo(3f, 3f), square.first())
        assertEquals(4, square.count { it is PathOp.MoveTo || it is PathOp.LineTo })
    }

    @Test
    fun `outlines follow pixel edges, holes the other way round`() {
        // A ring: 5 x 5 with the middle missing.
        val labels = IntArray(25) { i -> if (i == 12) -1 else 0 }
        val outlines = VectorTracer.outlines(labels, 5, 5, 0)
        assertEquals(2, outlines.size)
        val areas = outlines.map { VectorTracer.area(it) }.sortedBy { abs(it) }
        assertEquals(1.0, abs(areas[0]), 0.0)
        assertEquals(25.0, abs(areas[1]), 0.0)
        assertTrue(areas[0] * areas[1] < 0)
    }

    @Test
    fun `pixels touching by a corner stay apart`() {
        val labels = intArrayOf(0, -1, -1, 0)
        assertEquals(2, VectorTracer.outlines(labels, 2, 2, 0).size)
    }

    @Test
    fun `specks are dropped and transparency stays empty`() {
        val p = picture(20, 20) { x, y ->
            when {
                x == 10 && y == 10 -> black
                x < 5 -> 0
                else -> white
            }
        }
        val image = VectorTracer.trace(p, TraceSettings(colors = 2, speckle = 4))
        // Only the white area: the black pixel is a speck, the left strip transparent.
        assertEquals(1, image.layers.size)
        assertEquals(white, image.layers.single().argb)
        val bounds = image.layers.single().ops.filterIsInstance<PathOp.MoveTo>().map { it.x }
        assertTrue(bounds.all { it >= 5f })
    }

    @Test
    fun `black and white keeps the dark shapes only`() {
        val p = picture(30, 30) { x, y -> if ((x - 15) * (x - 15) + (y - 15) * (y - 15) < 64) 0xFF202020.toInt() else 0xFFEEEEEE.toInt() }
        val image = VectorTracer.trace(p, TraceSettings(mono = true))
        assertEquals(1, image.layers.size)
        assertEquals(black, image.layers.single().argb)
        // A disc: smooth curves, no hairline stroke needed.
        assertTrue(image.layers.single().ops.any { it is PathOp.QuadTo })
        assertFalse(image.toSvg().contains("stroke="))
    }

    @Test
    fun `the svg scales its traced picture to the size asked`() {
        val p = picture(4, 2) { x, _ -> if (x < 2) red else white }
        val svg = VectorTracer.trace(p, TraceSettings(colors = 2, speckle = 0)).toSvg(400, 200)
        assertTrue(svg, svg.contains("width=\"400\" height=\"200\" viewBox=\"0 0 4 2\""))
        assertTrue(svg, svg.contains("fill=\"#ff0000\""))
        assertTrue(svg, svg.contains("fill-rule=\"evenodd\""))
        assertTrue(svg, svg.trimEnd().endsWith("</svg>"))
    }

    @Test
    fun `a large picture is traced smaller`() {
        val p = picture(3000, 1000) { x, _ -> if (x < 1500) red else white }
        val image = VectorTracer.trace(p, TraceSettings(maxSide = 600, colors = 2))
        assertEquals(600, image.width)
        assertEquals(200, image.height)
    }

    @Test
    fun `otsu splits two peaks between them`() {
        val histogram = IntArray(256)
        histogram[40] = 100
        histogram[200] = 100
        assertTrue(VectorTracer.otsu(histogram) in 40 until 200)
    }

    @Test
    fun `the same picture always traces the same`() {
        val p = picture(40, 40) { x, y -> (0xFF shl 24) or ((x * 6) shl 16) or ((y * 6) shl 8) or 128 }
        assertEquals(VectorTracer.trace(p).toSvg(), VectorTracer.trace(p).toSvg())
    }
}
