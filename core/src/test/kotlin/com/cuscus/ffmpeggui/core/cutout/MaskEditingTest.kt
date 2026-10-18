package com.cuscus.ffmpeggui.core.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskEditingTest {

    @Test
    fun `a brush covers its width along the stroke, with a soft edge, and nothing past it`() {
        val mask = IntArray(40 * 20)
        MaskEditing.paint(mask, 40, 20, floatArrayOf(5f, 10.5f, 35f, 10.5f), 3f, keep = true)
        assertEquals(255, mask[10 * 40 + 20])
        assertEquals(255, mask[8 * 40 + 20])
        // Pixel centres 3 away get half; 5 away nothing.
        assertEquals(128, mask[13 * 40 + 20])
        assertEquals(0, mask[15 * 40 + 20])
        assertEquals(0, mask[10 * 40 + 39])
        // Removing takes it away again.
        MaskEditing.paint(mask, 40, 20, floatArrayOf(20.5f, 10.5f), 3f, keep = false)
        assertEquals(0, mask[10 * 40 + 20])
        assertEquals(255, mask[10 * 40 + 10])
    }

    @Test
    fun `only the parts a stroke touches are taken`() {
        // Two blocks apart; the stroke touches the left one.
        val inside = BooleanArray(20 * 10) { i -> val x = i % 20; x in 1..5 || x in 12..18 }
        val seeds = MaskEditing.pathPixels(floatArrayOf(3f, 2f, 3f, 7f), 20, 10)
        val touched = MaskEditing.connected(inside, 20, 10, seeds)
        assertTrue(touched[5 * 20 + 1])
        assertTrue(touched[0 * 20 + 5])
        assertFalse(touched[5 * 20 + 14])
        assertEquals(5 * 10, touched.count { it })
    }

    @Test
    fun `a region is put on or taken off a mask, at its own size or another`() {
        val region = MaskRegion(2, 1, 3, 2, ByteArray(6) { 255.toByte() })
        val mask = IntArray(8 * 4) { 100 }
        MaskEditing.apply(mask, 8, 4, region, keep = true)
        assertEquals(255, mask[1 * 8 + 2])
        assertEquals(100, mask[1 * 8 + 1])
        MaskEditing.apply(mask, 8, 4, region, keep = false)
        assertEquals(0, mask[2 * 8 + 4])
        // The same region on the picture twice as large.
        val big = IntArray(16 * 8)
        MaskEditing.applyScaled(big, 16, 8, region, 8, 4, keep = true)
        assertEquals(255, big[3 * 16 + 6])
        assertEquals(0, big[7 * 16 + 1])
        assertEquals(0, big[0 * 16 + 15])
    }

    @Test
    fun `deep points are far inside, apart, and away from what is avoided`() {
        // A 60 x 20 bar inside a 80 x 40 picture.
        val mask = IntArray(80 * 40) { i -> val x = i % 80; val y = i / 80; if (x in 10..69 && y in 10..29) 255 else 0 }
        val points = MaskEditing.deepPoints(mask, 80, 40, 3, floatArrayOf(), 4f)
        assertTrue(points.size >= 4)
        for (k in 0 until points.size / 2) {
            assertTrue(points[2 * k] in 15f..64f)
            assertTrue(points[2 * k + 1] in 15f..24f)
        }
        assertTrue(kotlin.math.abs(points[0] - points[2]) >= 8f)
        // Nothing near what is avoided, on the left.
        val away = MaskEditing.deepPoints(mask, 80, 40, 1, floatArrayOf(25f, 20f), 20f)
        assertEquals(2, away.size)
        assertTrue(away[0] >= 45f)
        assertEquals(0, MaskEditing.deepPoints(IntArray(100), 10, 10, 2, floatArrayOf(), 1f).size)
    }

    @Test
    fun `a rough part is brought to the picture's edge`() {
        // White on the left of x = 50, black from it; the part stops 3 pixels short of the edge.
        val w = 100
        val h = 40
        val argb = IntArray(w * h) { i -> if (i % w < 50) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val inside = BooleanArray(w * h) { i -> i % w < 47 }
        val region = GuidedFilter.snap(argb, w, h, inside, 6, 1e-3f)!!
        val y = 20
        assertTrue(region.at(40, y) > 230)
        // Past the part's edge but still white: brought in.
        assertTrue("at 48: ${region.at(48, y)}", region.at(48, y) > 128)
        // On the black side: out.
        assertTrue("at 52: ${region.at(52, y)}", region.at(52, y) < 30)
        assertNull(GuidedFilter.snap(argb, w, h, BooleanArray(w * h), 6, 1e-3f))
    }
}
