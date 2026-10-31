package com.cuscus.ffmpeggui.core.erase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InpaintAreaTest {

    @Test
    fun `the erased part grows, and its box is found`() {
        val w = 20
        val h = 10
        val mask = BooleanArray(w * h)
        mask[5 * w + 8] = true
        val grown = InpaintArea.grow(mask, w, h, 2)
        assertEquals(25, grown.count { it })
        assertEquals(InpaintArea.Box(6, 3, 11, 8), InpaintArea.bounds(grown, w, h))
        assertNull(InpaintArea.bounds(BooleanArray(w * h), w, h))
    }

    @Test
    fun `the crop surrounds what is erased and stays inside the picture`() {
        val crop = InpaintArea.crop(InpaintArea.Box(1_900, 100, 2_000, 200), 2_000, 1_000)
        assertEquals(InpaintArea.SIDE, crop.width)
        assertEquals(InpaintArea.SIDE, crop.height)
        assertEquals(2_000, crop.right)
        assertEquals(0, crop.top)
        // A large part: a crop larger than the model's square, the picture's size at most.
        val big = InpaintArea.crop(InpaintArea.Box(0, 0, 400, 300), 600, 500)
        assertEquals(600, big.width)
        assertEquals(500, big.height)
    }

    @Test
    fun `the model's picture goes only where the erased part was, its edge faded, the alpha kept`() {
        val w = 30
        val h = 1
        val mask = BooleanArray(w) { it in 10..19 }
        val weights = InpaintArea.weights(mask, w, h, 3)
        assertEquals(1f, weights[15], 0f)
        assertEquals(0f, weights[0], 0f)
        assertTrue(weights[21] in 0.01f..0.99f)
        assertEquals(0f, weights[25], 0f)
        val picture = IntArray(w) { 0x80000000.toInt() }
        val made = IntArray(w) { 0xFFFFFFFF.toInt() }
        InpaintArea.blend(picture, made, weights)
        assertEquals(0x80FFFFFF.toInt(), picture[15])
        assertEquals(0x80000000.toInt(), picture[0])
    }
}
