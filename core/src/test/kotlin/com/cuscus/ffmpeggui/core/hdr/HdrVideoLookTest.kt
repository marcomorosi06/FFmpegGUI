package com.cuscus.ffmpeggui.core.hdr

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.EncodingSettings
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.VideoHdr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrVideoLookTest {

    @Test
    fun `SDR white lands at 75 percent, darks stay, lights rise with the intensity`() {
        assertEquals(0.75f, HdrVideoLook.hlg(1f, 1f, 1f, 0f)[1], 0.01f)
        assertEquals(0f, HdrVideoLook.hlg(0f, 0f, 0f, 1f)[0], 1e-6f)
        // A dark grey is not touched by the look.
        assertEquals(HdrVideoLook.hlg(0.2f, 0.2f, 0.2f, 0f)[1], HdrVideoLook.hlg(0.2f, 0.2f, 0.2f, 1f)[1], 1e-6f)
        val white = HdrVideoLook.hlg(1f, 1f, 1f, 0.6f)[1]
        assertTrue("$white", white > 0.85f && white <= 1f)
        assertTrue(HdrVideoLook.stopsAt(1f, 1f, 1f, 0.5f) > HdrVideoLook.stopsAt(1f, 1f, 1f, 0.2f))
        val cube = HdrVideoLook.cube(0.5f).lines().filter { it.isNotBlank() }
        assertEquals(2 + HdrVideoLook.LUT_SIZE * HdrVideoLook.LUT_SIZE * HdrVideoLook.LUT_SIZE, cube.size)
        assertEquals("LUT_3D_SIZE 33", cube[1])
    }

    @Test
    fun `the gain map shows the lights brighter, the darks as they are`() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFF333333.toInt(), 0xFFFFFFFF.toInt(), 0xFFF0E0D0.toInt())
        val map = HdrVideoLook.gainMap(pixels, 2, 2, 0.6f)!!
        assertEquals(0, map.valueAt(0, 0))
        assertEquals(0, map.valueAt(1, 0))
        assertTrue(map.valueAt(0, 1) > 150 && map.valueAt(1, 1) > 150)
        assertTrue(map.metadata.maxStops > 0.5f && map.metadata.maxStops < 3f)
        assertTrue(HdrVideoLook.shownStops(0.9f, 0.9f, 0.9f, 0.6f) > HdrVideoLook.shownStops(0.6f, 0.6f, 0.6f, 0.6f))
        assertEquals(null, HdrVideoLook.gainMap(pixels, 2, 2, 0f))
        assertEquals(0.25f, HdrVideoLook.lightFrom(1f), 1e-6f)
    }

    private val builder = CommandBuilder { "/fonts/font.ttf" }
    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true)),
        encoding = EncodingSettings(hardwareEncoder = HardwareEncoder("c2.enc", "nv12", 16, 16)),
    )

    @Test
    fun `an HDR look makes an HLG video in HEVC Main 10`() {
        val request = video.copy(effects = Effects(hdrLook = 0.6f))
        val args = builder.build(request, MediaPaths.aliasesFor(request)).arguments
        val line = args.joinToString(" ")
        assertTrue(line, line.contains("format=gbrp16le,lut3d=file='hdr_look.cube':interp=tetrahedral,scale=out_color_matrix=bt2020:out_range=tv,format=yuv420p10le"))
        assertTrue(line, line.contains("-vcodec libx265"))
        assertTrue(line, line.contains("-pix_fmt yuv420p10le -profile:v main10 -tag:v hvc1"))
        assertTrue(line, line.contains("-color_trc arib-std-b67"))
        assertFalse(line, line.contains("mediacodec"))
        // Not in a GIF.
        val gif = video.copy(format = OutputFormat.GIF, effects = Effects(hdrLook = 0.6f))
        assertFalse(builder.build(gif, MediaPaths.aliasesFor(gif)).arguments.joinToString(" ").contains("lut3d"))
        // HDR footage kept HDR: no look on top, but HEVC too.
        val kept = video.copy(sources = listOf(SourceInfo(10_000, 640, 360, true, hdr = VideoHdr.PQ)), effects = Effects(hdrLook = 0.6f))
        val keptLine = builder.build(kept, MediaPaths.aliasesFor(kept)).arguments.joinToString(" ")
        assertFalse(keptLine, keptLine.contains("lut3d"))
        assertTrue(keptLine, keptLine.contains("-vcodec libx265") && keptLine.contains("-color_trc smpte2084"))
    }
}
