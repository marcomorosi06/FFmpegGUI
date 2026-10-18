package com.cuscus.ffmpeggui.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineSupportTest {

    private val parser = ProgressParser()

    @Test
    fun `progress is read from the status line`() {
        val line = "frame=  120 fps= 30 q=28.0 size=     512kB time=00:00:05.00 bitrate= 838.9kbits/s speed=1.2x"
        assertEquals(5_000L, parser.positionMs(line))
        assertEquals(0.25f, parser.fraction(line, 20_000)!!, 0.0001f)
        assertEquals(1f, parser.fraction(line, 1_000)!!, 0.0001f)
    }

    @Test
    fun `progress is unknown without a time or a duration`() {
        assertNull(parser.fraction("Stream mapping:", 20_000))
        assertNull(parser.fraction("time=00:00:05.00", null))
        assertNull(parser.fraction("time=N/A", 20_000))
    }

    @Test
    fun `concat list carries trims and escapes quotes`() {
        assertEquals(
            "ffconcat version 1.0\n" +
                "file '/c/a.mp4'\n" +
                "inpoint 1.500\n" +
                "outpoint 4.000\n" +
                "file '/c/it'\\''s.mp4'\n",
            renderConcatList(listOf(ConcatEntry("/c/a.mp4", 1_500, 4_000), ConcatEntry("/c/it's.mp4", 0, null))),
        )
    }
}
