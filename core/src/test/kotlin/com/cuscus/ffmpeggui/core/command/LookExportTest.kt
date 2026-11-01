package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.LookSpan
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.PictureLook
import com.cuscus.ffmpeggui.core.model.SourceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picture looks in a whole export: after the captions and pictures, before the whole video's fades. */
class LookExportTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true, frameRate = 25f)),
    )

    private fun arguments(request: ConversionRequest) = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun graph(request: ConversionRequest): String {
        val args = arguments(request)
        return args[args.indexOf("-filter_complex") + 1]
    }

    @Test
    fun `a look alone makes a graph`() {
        val g = graph(video.copy(looks = listOf(LookSpan(PictureLook.BLACK_WHITE, 1_000, 3_000))))
        assertTrue(g, g.contains("hue=s=0:enable='between(t,1.0000,3.0000)'"))
        assertTrue(g, g.endsWith("[vout]"))
        assertTrue(arguments(video.copy(looks = listOf(LookSpan(PictureLook.BLACK_WHITE, 1_000, 3_000)))).contains("[vout]"))
    }

    @Test
    fun `looks follow one another, then the fades cover them`() {
        val request = video.copy(
            effects = Effects(fadeOutSeconds = 1f),
            looks = listOf(LookSpan(PictureLook.GLITCH, 0, 2_000), LookSpan(PictureLook.INVERT, 1_000, 4_000)),
        )
        val g = graph(request)
        val glitch = g.indexOf("rgbashift")
        val invert = g.indexOf("negate=enable='between(t,1.0000,4.0000)'")
        val fade = g.indexOf("fade=t=out")
        assertTrue(g, glitch in 0 until invert)
        assertTrue(g, invert < fade)
    }

    @Test
    fun `no look, no change`() {
        val args = arguments(video)
        assertFalse(args.joinToString(" "), args.joinToString(" ").contains("vlook"))
    }
}
