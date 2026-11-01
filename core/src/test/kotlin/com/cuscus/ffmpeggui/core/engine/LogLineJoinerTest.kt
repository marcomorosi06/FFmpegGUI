package com.cuscus.ffmpeggui.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FFmpeg's log, handed over in pieces, read as whole lines. */
class LogLineJoinerTest {

    private fun joined(vararg pieces: String): List<String> {
        val lines = mutableListOf<String>()
        val joiner = LogLineJoiner { lines += it }
        pieces.forEach(joiner::feed)
        joiner.flush()
        return lines
    }

    @Test
    fun `pieces make one line until it ends`() {
        assertEquals(
            listOf("    major_brand     : iso5", "  Duration: 00:00:14.98, start: 0.000000, bitrate: 169471 kb/s"),
            joined("    major_brand     :", " iso5", "\n", "\n", "  Duration:", " 00:00:14.98", ", start:", " 0.000000", ", bitrate:", " 169471 kb/s", "\n"),
        )
    }

    @Test
    fun `the progress line ends at its carriage return, and what is left comes at the end`() {
        assertEquals(
            listOf("frame=  182 fps=9.5 time=00:00:06.00", "frame=  183 fps=8.9 time=00:00:06.70", "Conversion failed!"),
            joined("frame=  182 fps=9.5 time=00:00:06.00\r", "frame=  183 fps=8.9 time=00:00:06.70\r\n", "Conversion failed!"),
        )
        assertEquals(emptyList<String>(), joined("\n", "   ", "\r\n"))
    }

    @Test
    fun `a full storage is recognised`() {
        assertTrue(StorageFull.saysSo("[out#0/mp4 @ 0x7b2e] Error writing trailer: No space left on device"))
        assertTrue(StorageFull.saysSo("write failed: ENOSPC"))
        assertFalse(StorageFull.saysSo("frame=  182 fps=9.5 q=11.0 size=  283648kB"))
    }
}
