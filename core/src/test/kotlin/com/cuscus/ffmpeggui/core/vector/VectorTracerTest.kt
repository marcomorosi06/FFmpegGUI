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

    /** Red to blue from left to right, smoothly. */
    private fun ramp(w: Int, h: Int) = picture(w, h) { x, _ ->
        val f = x / (w - 1.0)
        (0xFF shl 24) or ((255 * (1 - f)).toInt() shl 16) or (255 * f).toInt()
    }

    @Test
    fun `without gradients a smooth ramp is flat colours`() {
        val image = VectorTracer.trace(ramp(120, 60), TraceSettings(colors = 3, speckle = 0))
        assertEquals(0, image.gradients)
        assertFalse(image.toSvg().contains("Gradient"))
    }

    @Test
    fun `a smooth ramp is shaded with a linear gradient along it`() {
        val image = VectorTracer.trace(ramp(120, 60), TraceSettings(colors = 2, speckle = 0, gradients = true))
        val shaded = image.layers.mapNotNull { it.gradient }
        assertTrue(shaded.isNotEmpty())
        for (g in shaded) {
            assertFalse(g.radial)
            // It runs across, left to right, and keeps level.
            assertTrue(abs(g.bx - g.ax) > 10 * abs(g.by - g.ay))
            val reddish = if (g.bx > g.ax) g.colors.first() else g.colors.last()
            val bluish = if (g.bx > g.ax) g.colors.last() else g.colors.first()
            assertTrue((reddish shr 16 and 0xFF) > (bluish shr 16 and 0xFF))
            assertTrue((bluish and 0xFF) > (reddish and 0xFF))
            // Its stops rise.
            assertTrue(g.offsets.toList().zipWithNext().all { (a, b) -> a <= b })
        }
        // The palette still counts the picture's colours, not its shaded shapes.
        assertEquals(2, image.palette.size)
        val svg = image.toSvg()
        assertTrue(svg.contains("<linearGradient id=\"ffg-trace-"))
        assertTrue(svg.contains("fill=\"url(#ffg-trace-"))
    }

    @Test
    fun `light fading out from a centre is shaded with a radial gradient`() {
        val p = picture(100, 100) { x, y ->
            val d = kotlin.math.hypot(x - 49.5, y - 49.5) / 70.0
            val v = (255 * (1 - d)).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val image = VectorTracer.trace(p, TraceSettings(colors = 2, speckle = 0, gradients = true))
        val disc = image.layers.mapNotNull { it.gradient }.firstOrNull { it.radial }
        assertTrue(disc != null)
        // Centred where the light is, the light at its middle.
        assertEquals(50f, disc!!.ax, 6f)
        assertEquals(50f, disc.ay, 6f)
        assertTrue((disc.colors.first() and 0xFF) > (disc.colors.last() and 0xFF))
        assertTrue(image.toSvg().contains("<radialGradient"))
    }

    @Test
    fun `flat colours stay flat even with gradients on`() {
        val p = picture(60, 60) { x, y -> if (x in 15..44 && y in 15..44) red else white }
        val flat = VectorTracer.trace(p, TraceSettings(colors = 2, speckle = 0))
        val shaded = VectorTracer.trace(p, TraceSettings(colors = 2, speckle = 0, gradients = true))
        assertEquals(0, shaded.gradients)
        assertEquals(flat.toSvg(), shaded.toSvg())
    }

    @Test
    fun `black and white ignores gradients`() {
        val image = VectorTracer.trace(ramp(80, 40), TraceSettings(mono = true, gradients = true))
        assertEquals(0, image.gradients)
    }

    @Test
    fun `shapes touching only by a corner are apart`() {
        // Two pixels of one colour, corner to corner.
        val labels = intArrayOf(0, -1, -1, 0)
        val (component, labelOf) = VectorTracer.components(labels, 2, 2)
        assertEquals(2, labelOf.size)
        assertTrue(component[0] != component[3])
        assertEquals(-1, component[1])
    }
}
