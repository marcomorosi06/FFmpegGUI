package com.cuscus.ffmpeggui.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnappingTest {

    @Test
    fun `a box near the middle of the frame sticks to it`() {
        // 100 wide, its middle at 497 of a 1000 wide frame.
        val snap = snapMove(SnapBox(447f, 10f, 547f, 60f), 1000f, 500f, emptyList(), threshold = 8f)
        assertEquals(3f, snap.dx, 1e-3f)
        assertEquals(0f, snap.dy, 1e-3f)
        assertTrue(snap.guides.any { it.vertical && it.at == 500f && it.target == null })
    }

    @Test
    fun `edges stick to the frame's edges and to other things, the closest first`() {
        val other = SnapBox(300f, 300f, 400f, 400f)
        // Its left edge 5 px right of the other's right edge; its top 2 px under the frame's top.
        val snap = snapMove(SnapBox(405f, 2f, 455f, 52f), 1000f, 500f, listOf(other), threshold = 8f)
        assertEquals(-5f, snap.dx, 1e-3f)
        assertEquals(-2f, snap.dy, 1e-3f)
        assertTrue(snap.guides.any { it.vertical && it.at == 400f && it.target == other })
        assertTrue(snap.guides.any { !it.vertical && it.at == 0f && it.target == null })
        // Too far: nothing.
        assertEquals(Snap.NONE.guides, snapMove(SnapBox(600f, 100f, 650f, 150f), 1000f, 500f, listOf(other), 8f).guides)
    }

    /** A box 2 x [half] wide and [half] high about (500, 250): resized about its middle. */
    private fun centred(half: Float) = SnapBox(500f - half, 250f - half / 2f, 500f + half, 250f + half / 2f)

    @Test
    fun `a box resized about its middle sticks by its edges to the frame's`() {
        // Its left edge at 3, its right at 997: pulled out to the frame's edges.
        val snap = snapResize(::centred, 497f, 1000f, 500f, emptyList(), threshold = 8f)
        assertEquals(500f, snap.value, 1e-2f)
        assertTrue(snap.guides.any { it.vertical && it.at == 0f && it.target == null })
        assertTrue(snap.guides.any { it.vertical && it.at == 1000f && it.target == null })
        // Its middle stays where it is: the frame's middle line is not one it stuck to.
        assertTrue(snap.guides.none { it.vertical && it.at == 500f })
    }

    @Test
    fun `a resize sticks to another thing's edge, the closest edge first`() {
        val other = SnapBox(100f, 0f, 296f, 50f)
        // Its left edge at 300 (4 px from the other's right edge), its top at 400 - its height.
        val snap = snapResize(::centred, 200f, 1000f, 500f, listOf(other), threshold = 8f)
        assertEquals(204f, snap.value, 1e-2f)
        assertTrue(snap.guides.any { it.vertical && it.at == 296f && it.target == other })
        // Far from every line: as it is, nothing shows.
        val free = snapResize(::centred, 150f, 1000f, 500f, listOf(other), threshold = 8f)
        assertEquals(150f, free.value, 0f)
        assertTrue(free.guides.isEmpty())
    }

    @Test
    fun `a stretch moves one edge only, and sticks by it`() {
        // A handle on the right: pulled by d, the right edge at 600 + 2 d (grown on both sides).
        val box = { d: Float -> SnapBox(400f - d, 100f, 600f + d, 200f) }
        val snap = snapResize(box, 96f, 1000f, 500f, emptyList(), threshold = 8f)
        // 696 and 304: neither near a line; 99 would make them 699 and 301. Nothing: as it is.
        assertEquals(96f, snap.value, 0f)
        // Near the frame's edges: 400 - 397 = 3, 600 + 397 = 997.
        assertEquals(400f, snapResize(box, 397f, 1000f, 500f, emptyList(), 8f).value, 1e-2f)
    }

    @Test
    fun `turns stick to whole steps`() {
        assertEquals(90f, snapAngle(87.5f)!!, 0f)
        assertEquals(-45f, snapAngle(-47f)!!, 0f)
        assertNull(snapAngle(70f))
        assertEquals(0f, snapAngle(1f, step = 90f)!!, 0f)
    }
}
