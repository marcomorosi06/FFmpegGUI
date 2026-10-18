package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareEncoderTest {

    private val builder = CommandBuilder { "/fonts/Regular.ttf" }

    private val chip = HardwareEncoder("c2.test.avc.encoder")

    private fun request(
        format: OutputFormat = OutputFormat.MP4,
        hardware: Boolean = true,
        encoder: HardwareEncoder = chip,
    ): ConversionRequest {
        val plain = ConversionRequest(
            format = format,
            inputFileName = "clip.mov",
            sources = listOf(SourceInfo(durationMs = 20_000, width = 1920, height = 1080, hasAudio = true)),
        )
        return plain.copy(encoding = plain.encoding.copy(hardwareEncoder = encoder.takeIf { hardware }))
    }

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun List<String>.valueOf(flag: String): String = this[indexOf(flag) + 1]

    @Test
    fun `an mp4 is encoded by the phone at a bitrate`() {
        val args = args(request())
        assertEquals("h264_mediacodec", args.valueOf("-vcodec"))
        // The very encoder checked on the phone, fed what it was checked with.
        assertEquals("c2.test.avc.encoder", args.valueOf("-codec_name"))
        assertEquals("nv12", args.valueOf("-pix_fmt"))
        assertEquals("vbr", args.valueOf("-bitrate_mode"))
        assertEquals("60", args.valueOf("-g"))
        assertEquals("${hardwareVideoKbps(1920, 1080, 30, 0.7f)}k", args.valueOf("-b:v"))
        assertFalse("-crf" in args)
        assertFalse("libx264" in args)
        // The sound is still encoded as before.
        assertEquals("aac", args.valueOf("-acodec"))
    }

    @Test
    fun `mkv too, and the processor when it is not asked for`() {
        assertEquals("h264_mediacodec", args(request(OutputFormat.MKV)).valueOf("-vcodec"))
        assertEquals("libx264", args(request(hardware = false)).valueOf("-vcodec"))
    }

    @Test
    fun `other formats, parts, remuxes and frames stay as they are`() {
        assertEquals("libxvid", args(request(OutputFormat.AVI)).valueOf("-vcodec"))
        assertFalse(builder.usesHardwareEncoder(request(OutputFormat.GIF)))
        assertFalse(builder.usesHardwareEncoder(request(OutputFormat.MP3)))
        val parts = request().copy(split = Split.ByDuration(10))
        assertFalse(builder.usesHardwareEncoder(parts))
        assertEquals("libx264", args(parts).valueOf("-vcodec"))
        val remux = request().let { it.copy(encoding = it.encoding.copy(streamCopy = true)) }
        assertFalse(builder.usesHardwareEncoder(remux))
        assertFalse(builder.usesHardwareEncoder(request().copy(frameExtraction = FrameExtraction.SingleFrame(1_000))))
    }

    @Test
    fun `the bitrate follows the size, the frame rate and the quality`() {
        assertEquals(8_255, hardwareVideoKbps(1920, 1080, 30, 0.7f))
        assertTrue(hardwareVideoKbps(1280, 720, 30, 0.7f) < hardwareVideoKbps(1920, 1080, 30, 0.7f))
        assertTrue(hardwareVideoKbps(1920, 1080, 60, 0.7f) > hardwareVideoKbps(1920, 1080, 30, 0.7f))
        assertTrue(hardwareVideoKbps(1920, 1080, 30, 1f) > hardwareVideoKbps(1920, 1080, 30, 0.5f))
        // An unknown size counts as 1080p; the ends are kept within reach of every encoder.
        assertEquals(hardwareVideoKbps(1920, 1080, 30, 0.7f), hardwareVideoKbps(0, 0, 30, 0.7f))
        assertEquals(300, hardwareVideoKbps(160, 90, 10, 0f))
        assertEquals(60_000, hardwareVideoKbps(7680, 4320, 60, 1f))
    }

    @Test
    fun `a chosen height and frame rate set the bitrate`() {
        val small = request().let { it.copy(encoding = it.encoding.copy(resolution = Resolution.P720, framerate = Framerate.FPS24)) }
        assertEquals("${hardwareVideoKbps(1280, 720, 24, 0.7f)}k", args(small).valueOf("-b:v"))
    }

    @Test
    fun `without the phone's encoder the command is the processor's`() {
        val fallback = builder.withoutHardwareEncoder(request())
        assertEquals(args(request(hardware = false)), args(fallback))
    }

    @Test
    fun `the processor works as hard as chosen, also after the phone's encoder failed`() {
        CpuEncoderSpeed.entries.forEach { speed ->
            val plain = request(hardware = false).let { it.copy(encoding = it.encoding.copy(cpuSpeed = speed)) }
            assertEquals(speed.preset, args(plain).valueOf("-preset"))
            val fallback = builder.withoutHardwareEncoder(plain.copy(encoding = plain.encoding.copy(hardwareEncoder = chip)))
            assertEquals(speed.preset, args(fallback).valueOf("-preset"))
        }
        // The default stays the balanced one.
        assertEquals("medium", args(request(hardware = false)).valueOf("-preset"))
    }

    @Test
    fun `the phone's encoder gets sides that are multiples of 16, the processor the picture as it is`() {
        val hardware = args(request())
        assertTrue(hardware.valueOf("-vf").endsWith("scale=trunc(iw/16+0.5)*16:trunc(ih/16+0.5)*16"))
        val software = args(request(hardware = false))
        assertFalse(software.any { "iw/16" in it })
        // In a filter graph too (two clips joined), at the end of the picture's chain.
        val joined = request().copy(segments = listOf(Segment(0, 0, 5_000), Segment(0, 8_000, 12_000)))
        val graph = args(joined).valueOf("-filter_complex")
        assertTrue(graph, "trunc(iw/16+0.5)*16" in graph)
    }

    @Test
    fun `an encoder that needs other sizes or planar input gets them`() {
        val planar = HardwareEncoder("c2.other.avc.encoder", pixelFormat = "yuv420p", widthAlign = 128, heightAlign = 32)
        val args = args(request(encoder = planar))
        assertEquals("c2.other.avc.encoder", args.valueOf("-codec_name"))
        assertEquals("yuv420p", args.valueOf("-pix_fmt"))
        assertTrue(args.valueOf("-vf").endsWith("scale=trunc(iw/128+0.5)*128:trunc(ih/32+0.5)*32"))
    }
}
