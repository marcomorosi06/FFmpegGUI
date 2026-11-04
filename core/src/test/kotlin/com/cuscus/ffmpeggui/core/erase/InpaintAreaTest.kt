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

    @Test
    fun `blending row by row from an offset gives what blending all at once does`() {
        val w = 7
        val h = 5
        val mask = BooleanArray(w * h) { i -> i % w in 2..4 && i / w in 1..3 }
        val weights = InpaintArea.weights(mask, w, h, 1)
        val picture = IntArray(w * h) { 0xFF000000.toInt() or (it * 7) }
        val made = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        val whole = picture.copyOf().also { InpaintArea.blend(it, made, weights) }
        val rows = picture.copyOf()
        for (y in 0 until h) {
            val row = rows.copyOfRange(y * w, y * w + w)
            InpaintArea.blend(row, made.copyOfRange(y * w, y * w + w), weights, y * w)
            row.copyInto(rows, y * w)
        }
        assertTrue(whole.contentEquals(rows))
    }

    @Test
    fun `weighing only three feathers past the erased part gives the weights of the whole crop`() {
        val w = 80
        val h = 60
        val feather = 4
        val mask = BooleanArray(w * h) { i -> i % w in 30..41 && i / w in 22..33 }
        val all = InpaintArea.weights(mask, w, h, feather)
        val inside = InpaintArea.bounds(mask, w, h)!!
        val reach = 3 * feather
        val bx = maxOf(0, inside.left - reach)
        val by = maxOf(0, inside.top - reach)
        val bw = minOf(w, inside.right + reach) - bx
        val bh = minOf(h, inside.bottom + reach) - by
        val part = BooleanArray(bw * bh) { i -> mask[(by + i / bw) * w + bx + i % bw] }
        val some = InpaintArea.weights(part, bw, bh, feather)
        for (y in 0 until h) for (x in 0 until w) {
            val expected = all[y * w + x]
            val inBox = x in bx until bx + bw && y in by until by + bh
            if (inBox) {
                assertEquals("at $x, $y", expected, some[(y - by) * bw + x - bx], 1e-6f)
            } else {
                assertEquals("at $x, $y", 0f, expected, 0f)
            }
        }
    }
}
