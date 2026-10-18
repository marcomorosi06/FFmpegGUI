package com.cuscus.ffmpeggui.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StillMotionTest {

    @Test
    fun `zooms run between 1x and the amount`() {
        val zoomIn = StillMotion(MotionKind.ZOOM_IN, 1.5f)
        assertEquals(1f, zoomIn.zoomAt(0f), 0f)
        assertEquals(1.25f, zoomIn.zoomAt(0.5f), 0.0001f)
        assertEquals(1.5f, zoomIn.zoomAt(2f), 0f)
        assertEquals(1.5f, StillMotion(MotionKind.ZOOM_OUT, 1.5f).zoomAt(0f), 0f)
        assertEquals(0.5f, zoomIn.xAt(0.3f), 0f)
    }

    @Test
    fun `pans cross the enlarged picture at a steady zoom`() {
        val right = StillMotion(MotionKind.PAN_RIGHT, 1.6f)
        assertEquals(1.6f, right.zoomAt(0.4f), 0f)
        assertEquals(0f, right.xAt(0f), 0f)
        assertEquals(1f, right.xAt(1f), 0f)
        assertEquals(1f, StillMotion(MotionKind.PAN_UP).yAt(0f), 0f)
        assertEquals(StillMotion.MAX_AMOUNT, StillMotion(MotionKind.ZOOM_IN, 9f).strength, 0f)
    }
}
