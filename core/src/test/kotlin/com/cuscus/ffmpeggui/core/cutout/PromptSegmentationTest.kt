package com.cuscus.ffmpeggui.core.cutout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSegmentationTest {

    private val side = PromptSegmentation.SIDE
    private val maskSide = PromptSegmentation.MASK_SIDE

    @Test
    fun `the input is the stretched square, normalised, channels first`() {
        val pixels = IntArray(side * side) { 0xFF_FF_80_00.toInt() }
        pixels[side * side - 1] = 0xFF_00_00_FF.toInt()
        val input = PromptSegmentation.input(pixels)
        val plane = side * side
        assertEquals(3 * plane, input.size)
        assertEquals((1f - 0.485f) / 0.229f, input[0], 1e-5f)
        assertEquals((128 / 255f - 0.456f) / 0.224f, input[plane + 1], 1e-5f)
        assertEquals((0f - 0.406f) / 0.225f, input[2 * plane], 1e-5f)
        // Not divided by the largest value (as SAM 2 does it, unlike rembg): the last pixel is pure blue.
        assertEquals((0f - 0.485f) / 0.229f, input[plane - 1], 1e-5f)
        assertEquals((1f - 0.406f) / 0.225f, input[3 * plane - 1], 1e-5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a picture not yet stretched to the square is refused`() {
        PromptSegmentation.input(IntArray(4000 * 3))
    }

    @Test
    fun `points are stretched with the picture, each side on its own`() {
        val points = PromptSegmentation.points(floatArrayOf(2000f, 0f, 4000f), floatArrayOf(1500f, 3000f, 0f), 4000, 3000)
        assertArrayEquals(floatArrayOf(512f, 512f, 0f, 1024f, 1024f, 0f), points, 1e-3f)
        // A tall picture: x is stretched more than y.
        val tall = PromptSegmentation.points(floatArrayOf(540f), floatArrayOf(960f), 1080, 1920)
        assertArrayEquals(floatArrayOf(512f, 512f), tall, 1e-3f)
    }

    @Test
    fun `a mask covers the whole picture and is read between its values`() {
        // The left half of the mask is in: on a picture of 400 x 300, its left half.
        val logits = FloatArray(3 * maskSide * maskSide) { -10f }
        for (y in 0 until maskSide) for (x in 0 until maskSide / 2) logits[maskSide * maskSide + y * maskSide + x] = 10f
        val region = PromptSegmentation.region(logits, 1, 400, 300)
        assertEquals(400 * 300, region.size)
        assertTrue(region[150 * 400 + 50])
        assertTrue(region[299 * 400 + 50])
        assertFalse(region[150 * 400 + 350])
        // The edge falls half way across, between the last column in and the first out.
        assertTrue(region[150 * 400 + 198])
        assertFalse(region[150 * 400 + 202])
        assertTrue(PromptSegmentation.logitAt(logits, 1, 50, 150, 400, 300) > 9f)
        // Mask 0 holds nothing.
        assertFalse(PromptSegmentation.region(logits, 0, 400, 300)[150 * 400 + 50])
    }

    @Test
    fun `the mask taken agrees with the points, then is the best scored`() {
        // A picture of 1024 x 1024: mask 0 all in, mask 1 the left half, mask 2 nothing.
        val plane = maskSide * maskSide
        val logits = FloatArray(3 * plane) { -5f }
        for (i in 0 until plane) logits[i] = 5f
        for (y in 0 until maskSide) for (x in 0 until maskSide / 2) logits[plane + y * maskSide + x] = 5f
        val inLeft = floatArrayOf(100f, 500f)
        val outRight = floatArrayOf(900f, 500f)
        // In on the left, out on the right: only the left half agrees, though "all" scores higher.
        val choice = PromptSegmentation.choose(logits, floatArrayOf(0.9f, 0.6f, 0.95f), 3, inLeft, outRight, 1024, 1024)
        assertEquals(1, choice.index)
        assertEquals(1f, choice.agreement, 0f)
        // Out on the left and in on the right: none agrees fully; the one that agrees most.
        val wrong = PromptSegmentation.choose(logits, floatArrayOf(0.9f, 0.6f, 0.95f), 3, outRight, inLeft, 1024, 1024)
        assertEquals(0.5f, wrong.agreement, 0f)
        assertEquals(2, wrong.index)
    }

    @Test
    fun `a tap takes the best scored thing under the finger, not the background`() {
        // A picture of 1024 x 1024: mask 0 all in (the background with it), mask 1 the
        // left half, mask 2 nothing.
        val plane = maskSide * maskSide
        val logits = FloatArray(3 * plane) { -5f }
        for (i in 0 until plane) logits[i] = 5f
        for (y in 0 until maskSide) for (x in 0 until maskSide / 2) logits[plane + y * maskSide + x] = 5f
        val scores = floatArrayOf(0.99f, 0.7f, 0.95f)
        // On the left: mask 0 scores higher, but it is the whole picture.
        assertEquals(1, PromptSegmentation.chooseForTap(logits, scores, 3, 100f, 500f, 1024, 1024))
        // On the right only mask 0 holds the point: better than nothing.
        assertEquals(0, PromptSegmentation.chooseForTap(logits, scores, 3, 900f, 500f, 1024, 1024))
        // Out of the picture's bounds, the nearest pixel is read.
        assertEquals(1, PromptSegmentation.chooseForTap(logits, scores, 3, -20f, 2000f, 1024, 1024))
        // No mask under the finger.
        val empty = FloatArray(3 * plane) { -5f }
        assertNull(PromptSegmentation.chooseForTap(empty, scores, 3, 100f, 500f, 1024, 1024))
    }

    @Test
    fun `a stroke gives points along it, never more than the limit`() {
        val stroke = FloatArray(202) { i -> if (i % 2 == 0) (i / 2).toFloat() else 0f }
        val points = StrokePoints.of(listOf(stroke), spacing = 20f, limit = 50)
        // 0, 20, 40, ..., 100: six points.
        assertEquals(12, points.size)
        assertEquals(0f, points[0], 0f)
        assertEquals(100f, points[10], 0f)
        val few = StrokePoints.of(listOf(stroke, stroke), spacing = 5f, limit = 8)
        assertEquals(16, few.size)
        assertTrue(StrokePoints.of(listOf(floatArrayOf(3f, 4f)), 10f, 4).contentEquals(floatArrayOf(3f, 4f)))
    }
}
