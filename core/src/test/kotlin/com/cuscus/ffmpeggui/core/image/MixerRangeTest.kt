package com.cuscus.ffmpeggui.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** colorchannelmixer takes -2..2 only: a stronger colour matrix is written in steps. */
class MixerRangeTest {

    private val strong = listOf(-1.447, 2.273, 0.174, 0.553, 0.273, 0.174, 0.553, 2.273, -1.826)

    @Test
    fun `every coefficient written is within FFmpeg's range`() {
        val filter = ImageStage.Mixer(strong).filter
        val values = Regex("=(-?[0-9.]+)").findAll(filter).map { it.groupValues[1].toDouble() }.toList()
        assertTrue(filter, values.all { it in -2.0..2.0 })
        assertEquals(2, filter.split("colorchannelmixer").size - 1)
    }

    @Test
    fun `a matrix within range stays one mixer`() {
        val gentle = listOf(1.2, -0.1, -0.1, -0.1, 1.2, -0.1, -0.1, -0.1, 1.2)
        assertEquals(1, ImageStage.Mixer(gentle).steps.size)
    }

    @Test
    fun `the steps give what the whole matrix gives, to a level`() {
        val pixels = IntArray(256) { i -> (0xFF shl 24) or (i shl 16) or ((255 - i) shl 8) or (i / 2) }
        val stepped = ImageStage.Mixer(strong).apply(Raster(256, 1, pixels.copyOf())).pixels
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val exact = doubleArrayOf(
                strong[0] * r + strong[1] * g + strong[2] * b,
                strong[3] * r + strong[4] * g + strong[5] * b,
                strong[6] * r + strong[7] * g + strong[8] * b,
            ).map { it.coerceIn(0.0, 255.0) }
            val got = listOf((stepped[i] shr 16) and 0xFF, (stepped[i] shr 8) and 0xFF, stepped[i] and 0xFF)
            for (c in 0..2) assertEquals("pixel $i channel $c", exact[c], got[c].toDouble(), 2.5)
        }
    }
}
