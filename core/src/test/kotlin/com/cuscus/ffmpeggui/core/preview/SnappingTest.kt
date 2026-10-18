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

    @Test
    fun `turns stick to whole steps`() {
        assertEquals(90f, snapAngle(87.5f)!!, 0f)
        assertEquals(-45f, snapAngle(-47f)!!, 0f)
        assertNull(snapAngle(70f))
        assertEquals(0f, snapAngle(1f, step = 90f)!!, 0f)
    }
}
