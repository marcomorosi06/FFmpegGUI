package com.cuscus.ffmpeggui.core.cutout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSegmentationTest {

    private val side = PromptSegmentation.SIDE
    private val maskSide = PromptSegmentation.MASK_SIDE

    @Test
    fun `the longer side becomes 1024, the shorter in proportion as the processor rounds it`() {
        assertEquals(1024 to 768, PromptSegmentation.resizedSize(4000, 3000))
        assertEquals(576 to 1024, PromptSegmentation.resizedSize(1080, 1920))
        assertEquals(1024 to 1024, PromptSegmentation.resizedSize(10, 10))
        // 1000 x 333: 333 * 1.024 = 340.99 -> 341.
        assertEquals(1024 to 341, PromptSegmentation.resizedSize(1000, 333))
    }

    @Test
    fun `the input is normalised, channels first, and padded with zeros`() {
        val pixels = IntArray(2 * 1) { 0xFF_FF_80_00.toInt() }
        val input = PromptSegmentation.input(pixels, 2, 1)
        val plane = side * side
        assertEquals(3 * plane, input.size)
        assertEquals((1f - 0.485f) / 0.229f, input[0], 1e-5f)
        assertEquals((128 / 255f - 0.456f) / 0.224f, input[plane + 1], 1e-5f)
        assertEquals((0f - 0.406f) / 0.225f, input[2 * plane], 1e-5f)
        // Padding, past the picture.
        assertEquals(0f, input[2], 0f)
        assertEquals(0f, input[side], 0f)
    }

    @Test
    fun `points are moved into the resized picture`() {
        val points = PromptSegmentation.points(floatArrayOf(2000f, 0f), floatArrayOf(1500f, 3000f), 4000, 3000)
        assertArrayEquals(floatArrayOf(512f, 384f, 0f, 768f), points, 1e-3f)
    }

    @Test
    fun `a mask is read from the top left of the padded square, between its values`() {
        // The left half of the resized picture (512 of 1024 wide, 768 high) is in: in the
        // mask, columns 0..127 of rows 0..191.
        val logits = FloatArray(3 * maskSide * maskSide) { -10f }
        for (y in 0 until 192) for (x in 0 until 128) logits[maskSide * maskSide + y * maskSide + x] = 10f
        val region = PromptSegmentation.region(logits, 1, 400, 300)
        assertEquals(400 * 300, region.size)
        assertTrue(region[150 * 400 + 50])
        assertFalse(region[150 * 400 + 350])
        // The edge falls half way, between the last column in and the first out.
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
