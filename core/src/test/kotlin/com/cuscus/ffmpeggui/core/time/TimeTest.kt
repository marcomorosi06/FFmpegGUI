package com.cuscus.ffmpeggui.core.time

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.EncodingSettings
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.Split
import com.cuscus.ffmpeggui.core.model.Effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeTest {

    private val request = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "a.mp4",
        sources = listOf(SourceInfo(durationMs = 60_000)),
    )

    @Test
    fun `timecodes parse and format`() {
        assertEquals(3_723_450L, parseTimecodeMs("01:02:03.45"))
        assertEquals(5_000L, parseTimecodeMs("00:00:05"))
        assertEquals(0L, parseTimecodeMs("garbage"))
        assertEquals(0L, parseTimecodeMs(""))
        assertEquals("01:02:03.450", formatTimecode(3_723_450L))
        assertEquals("00:00:00.000", formatTimecode(-5))
    }

    @Test
    fun `output length follows the segments and the speed`() {
        assertEquals(60_000L, request.expectedOutputDurationMs())
        assertEquals(
            // (50 - 10) + (8 - 2) + 4 = 50 s, played at 2x.
            25_000L,
            request.copy(
                sources = listOf(SourceInfo(durationMs = 60_000), SourceInfo(durationMs = 30_000)),
                segments = listOf(Segment(0, 10_000, 50_000), Segment(1, 2_000, 8_000), Segment(1, 26_000)),
                effects = Effects(speed = 2f),
            ).expectedOutputDurationMs(),
        )
    }

    @Test
    fun `stream copy ignores the speed`() {
        val copy = request.copy(encoding = EncodingSettings(streamCopy = true), effects = Effects(speed = 2f))
        assertEquals(60_000L, copy.expectedOutputDurationMs())
    }

    @Test
    fun `output length is unknown without durations`() {
        assertNull(request.copy(sources = listOf(SourceInfo())).expectedOutputDurationMs())
        assertNull(request.copy(frameExtraction = FrameExtraction.Sequence(1f)).expectedOutputDurationMs())
        // The progress of frames goes over the part they are taken from.
        assertEquals(60_000L, request.copy(frameExtraction = FrameExtraction.Sequence(1f)).progressDurationMs())
        assertEquals(2_500L, request.copy(frameExtraction = FrameExtraction.Sequence(1f, 1_000, 3_500)).progressDurationMs())
        assertEquals(5_000L, request.copy(sources = listOf(SourceInfo()), segments = listOf(Segment(0, 1_000, 6_000))).expectedOutputDurationMs())
    }

    @Test
    fun `segmenting plan`() {
        assertNull(request.segmenting())
        assertEquals(Segmenting.Every(10), request.copy(split = Split.ByDuration(10)).segmenting())
        assertEquals(Segmenting.Every(1), request.copy(split = Split.ByDuration(0)).segmenting())
        assertEquals(Segmenting.At(listOf(15.0, 30.0, 45.0)), request.copy(split = Split.ByParts(4)).segmenting())
        assertNull(request.copy(split = Split.ByParts(1)).segmenting())
        assertNull(request.copy(sources = listOf(SourceInfo()), split = Split.ByParts(4)).segmenting())
        assertNull(request.copy(format = OutputFormat.GIF, split = Split.ByDuration(10)).segmenting())
    }
}
