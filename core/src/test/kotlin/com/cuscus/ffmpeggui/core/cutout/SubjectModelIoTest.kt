package com.cuscus.ffmpeggui.core.cutout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectModelIoTest {

    private val count = SubjectModelIo.SIDE * SubjectModelIo.SIDE

    @Test
    fun `the input is divided by its largest value and centred, channels first`() {
        val pixels = IntArray(count) { 0xFF000000.toInt() }
        pixels[0] = 0xFF_C8_64_00.toInt()
        pixels[1] = 0xFF_00_00_64.toInt()
        val input = SubjectModelIo.input(pixels)
        assertEquals(3 * count, input.size)
        // The largest value is 200: red 200 -> 1 - 0.5, green 100 -> 0.5 - 0.5, blue 0 -> -0.5.
        assertEquals(0.5f, input[0], 1e-6f)
        assertEquals(0f, input[count], 1e-6f)
        assertEquals(-0.5f, input[2 * count], 1e-6f)
        assertEquals(0f, input[2 * count + 1], 1e-6f)
        // A black picture stays at -0.5 without dividing by zero.
        assertEquals(-0.5f, SubjectModelIo.input(IntArray(count))[5], 1e-6f)
    }

    @Test
    fun `U2-Net takes the ImageNet statistics on a small picture`() {
        val side = SubjectNet.U2NET.side
        val pixels = IntArray(side * side) { 0xFF000000.toInt() }
        pixels[0] = 0xFF_FF_FF_FF.toInt()
        val input = SubjectModelIo.input(pixels, SubjectNet.U2NET)
        val n = side * side
        assertEquals(3 * n, input.size)
        assertEquals(((1.0 - 0.485) / 0.229).toFloat(), input[0], 1e-5f)
        assertEquals(((0.0 - 0.456) / 0.224).toFloat(), input[n + 1], 1e-5f)
        assertEquals(((1.0 - 0.406) / 0.225).toFloat(), input[2 * n], 1e-5f)
    }

    @Test
    fun `the output is stretched to 0 to 255`() {
        assertArrayEquals(intArrayOf(0, 128, 255), SubjectModelIo.mask(floatArrayOf(-2f, 0.5f, 3f)))
        assertArrayEquals(intArrayOf(0, 0), SubjectModelIo.mask(floatArrayOf(1f, 1f)))
    }
}
