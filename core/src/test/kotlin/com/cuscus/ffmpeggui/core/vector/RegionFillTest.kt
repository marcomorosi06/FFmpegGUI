package com.cuscus.ffmpeggui.core.vector

import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.vector.svg.SvgPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionFillTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    /** A 60 x 60 white picture with a black square ring from 10 to 49, 2 px thick, a [hole] px gap in its top. */
    private fun ring(hole: Int = 0): Raster = Raster(60, 60, IntArray(3600) { i ->
        val x = i % 60
        val y = i / 60
        val onRing = (x in 10..49 && y in 10..49) && !(x in 12..47 && y in 12..47)
        val inHole = y in 10..11 && x in 30 until 30 + hole
        if (onRing && !inHole) black else white
    })

    @Test
    fun `the inside of a ring is filled up to the ring`() {
        val mask = RegionFill.region(ring(), 30, 30)!!
        assertEquals(36 * 36, mask.count { it })
        // The outside is another space.
        val outside = RegionFill.region(ring(), 2, 2)!!
        assertEquals(3600 - 40 * 40, outside.count { it })
    }

    @Test
    fun `a gap lets the fill out, unless it is closed`() {
        val leaking = RegionFill.region(ring(hole = 3), 30, 30)!!
        assertTrue(leaking.count { it } > 2000)
        val closed = RegionFill.region(ring(hole = 3), 30, 30, gap = 2)!!
        // Inside only, grown back to the ring.
        assertTrue(closed.count { it } in 1100..1400)
        assertTrue(!closed[2 * 60 + 2])
    }

    @Test
    fun `a filled space becomes an outline around it`() {
        val mask = RegionFill.region(ring(), 30, 30)!!
        val segs = RegionFill.outline(mask, 60, 60)
        val box = SvgPath.bounds(segs)!!
        assertTrue(box.left in 11.0..13.5 && box.right in 46.5..49.0)
    }

    @Test
    fun `outside the picture there is nothing`() {
        assertNull(RegionFill.region(ring(), 70, 3))
        // On the ring itself: the ring's own pixels.
        assertTrue(RegionFill.region(ring(), 10, 10)!!.count { it } > 100)
    }
}
