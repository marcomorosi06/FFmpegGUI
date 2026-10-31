package com.cuscus.ffmpeggui.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankTest {

    @Test
    fun `a blank keeps its shape with its longer side as asked, sides even`() {
        val story = BlankSpec.of(9f / 16f, 1920, BlankFill())
        assertEquals(1080, story.width)
        assertEquals(1920, story.height)
        val odd = BlankSpec(width = 1001, height = 99_999).sane()
        assertEquals(1000, odd.width)
        assertEquals(BlankSpec.MAX_SIDE, odd.height)
    }

    @Test
    fun `fills paint each row`() {
        val row = IntArray(4)
        BlankFill(BlankFillType.TRANSPARENT).row(0, 4, 1, row)
        assertTrue(row.all { it == 0 })
        BlankFill(BlankFillType.SOLID, from = 0x80FF0000L).row(0, 4, 1, row)
        assertTrue(row.all { it == 0x80FF0000.toInt() })
        // Left to right, black to white: darker on the left, lighter on the right.
        BlankFill(BlankFillType.LINEAR, from = 0xFF000000L, to = 0xFFFFFFFFL, angleDegrees = 0f).row(0, 4, 1, row)
        val levels = row.map { it and 0xFF }
        assertEquals(levels.sorted(), levels)
        assertEquals(32, levels.first())
        assertEquals(223, levels.last())
        // Radial: the middle is the first colour, the corners the second.
        val radial = BlankFill(BlankFillType.RADIAL, from = 0xFF000000L, to = 0xFFFFFFFFL)
        val wide = IntArray(101)
        radial.row(50, 101, 101, wide)
        assertTrue((wide[50] and 0xFF) < 5)
        assertTrue((wide[0] and 0xFF) > (wide[25] and 0xFF))
    }

    @Test
    fun `see-through fills say so`() {
        assertTrue(BlankFill(BlankFillType.TRANSPARENT).seeThrough)
        assertFalse(BlankFill(BlankFillType.SOLID, from = 0xFF112233L).seeThrough)
        assertTrue(BlankFill(BlankFillType.LINEAR, from = 0xFF112233L, to = 0x00112233L).seeThrough)
    }

    @Test
    fun `presets are kept and read back`() {
        val presets = listOf(
            BlankSpec("Story", 1080, 1920, BlankFill(BlankFillType.LINEAR, 0xFF000000L, 0xFFFF00FFL, 45f)),
            BlankSpec("Logo", 1024, 1024, BlankFill(BlankFillType.TRANSPARENT)),
        )
        assertEquals(presets, BlankPresetsJson.read(BlankPresetsJson.write(presets)))
        assertEquals(emptyList<BlankSpec>(), BlankPresetsJson.read("not json"))
    }
}
