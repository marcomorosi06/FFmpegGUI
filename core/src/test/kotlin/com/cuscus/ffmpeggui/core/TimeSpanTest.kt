package com.cuscus.ffmpeggui.core

import com.cuscus.ffmpeggui.core.timeline.TimeSpan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSpanTest {

    private val span = TimeSpan(2_000, 5_000)

    @Test
    fun `moving keeps the length and never goes before the video`() {
        assertEquals(TimeSpan(3_000, 6_000), span.movedBy(1_000))
        assertEquals(TimeSpan(0, 3_000), span.movedBy(-9_000))
    }

    @Test
    fun `edges move alone and keep the minimum length`() {
        assertEquals(TimeSpan(1_000, 5_000), span.withStartEdgeMovedBy(-1_000))
        assertEquals(TimeSpan(0, 5_000), span.withStartEdgeMovedBy(-9_000))
        assertEquals(TimeSpan(4_900, 5_000), span.withStartEdgeMovedBy(9_000))
        assertEquals(TimeSpan(2_000, 2_100), span.withEndEdgeMovedBy(-9_000))
        assertEquals(TimeSpan(2_000, 8_000), span.withEndEdgeMovedBy(3_000))
    }

    @Test
    fun `a cut makes two halves`() {
        assertEquals(TimeSpan(2_000, 3_000) to TimeSpan(3_000, 5_000), span.splitAt(3_000))
        assertNull(span.splitAt(2_050))
        assertNull(span.splitAt(6_000))
    }

    @Test
    fun `rows work for any kind of span`() {
        assertArrayEquals(intArrayOf(0, 1, 0), TimeSpan.rows(listOf(TimeSpan(0, 3_000), TimeSpan(1_000, 2_000), TimeSpan(3_000, 4_000))))
    }
}
