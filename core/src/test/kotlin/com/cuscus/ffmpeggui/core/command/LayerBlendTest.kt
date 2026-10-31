package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframe
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.LayerBlend
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A picture laid over with a blend mode, in the export. */
class LayerBlendTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true)),
    )

    private val photo = ConversionRequest(
        format = OutputFormat.PNG,
        inputFileName = "photo.png",
        sources = listOf(SourceInfo(width = 800, height = 600, hasAudio = false, isStill = true)),
    )

    private fun graph(request: ConversionRequest): String {
        val args = builder.build(request, MediaPaths.aliasesFor(request)).arguments
        return args[args.indexOf("-filter_complex") + 1]
    }

    @Test
    fun `a normal picture is simply laid over`() {
        val g = graph(video.copy(imageOverlays = listOf(ImagePlacement())))
        assertFalse(g, g.contains("blend="))
        assertFalse(g, g.contains("alphamerge"))
        assertTrue(g, g.contains("overlay=x=(W-w)*0.0500:y=(H-h)*0.0500"))
    }

    @Test
    fun `a blended picture is mixed where it lies, then laid back as much as it is opaque`() {
        val g = graph(video.copy(imageOverlays = listOf(ImagePlacement(blend = LayerBlend.MULTIPLY))))
        // A see-through copy of the frame, with the picture laid on it where it goes.
        assertTrue(g, g.contains("split=3[wbd0b][wbd0c][wbd0k]"))
        assertTrue(g, g.contains("[wbd0k]format=rgba,colorchannelmixer=aa=0[wbd0z]"))
        assertTrue(g, g.contains("[wbd0z]["))
        assertTrue(g, g.contains("overlay=x=(W-w)*0.0500:y=(H-h)*0.0500"))
        assertTrue(g, g.contains(":format=rgb,format=gbrap,split[wbd0t][wbd0m]"))
        // The picture on top, the frame under it, in planar RGB.
        assertTrue(g, g.contains("[wbd0b]format=gbrap[wbd0g]"))
        assertTrue(g, g.contains("[wbd0t][wbd0g]blend=all_mode=multiply[wbd0x]"))
        // Kept only where the picture is.
        assertTrue(g, g.contains("[wbd0m]alphaextract[wbd0a]"))
        assertTrue(g, g.contains("[wbd0x][wbd0a]alphamerge[wbd0y]"))
        assertTrue(g, g.contains("[wbd0c][wbd0y]overlay=x=0:y=0"))
    }

    @Test
    fun `the spec's overlay and hard light are FFmpeg's other way round`() {
        assertEquals("hardlight", LayerBlend.OVERLAY.ffmpegMode)
        assertEquals("overlay", LayerBlend.HARD_LIGHT.ffmpegMode)
        assertEquals(null, LayerBlend.NORMAL.ffmpegMode)
        val g = graph(video.copy(imageOverlays = listOf(ImagePlacement(blend = LayerBlend.OVERLAY))))
        assertTrue(g, g.contains("blend=all_mode=hardlight"))
    }

    @Test
    fun `a sticker of a photo blends with it`() {
        val g = graph(photo.copy(imageOverlays = listOf(ImagePlacement(blend = LayerBlend.SCREEN))))
        assertTrue(g, g.contains("blend=all_mode=screen"))
        assertTrue(g, g.contains("alphamerge"))
    }

    @Test
    fun `a moving picture blends too`() {
        val keys = Keyframes(listOf(Keyframe(0L, mapOf(KeyProperty.X to 0f)), Keyframe(1_000L, mapOf(KeyProperty.X to 1f))))
        val g = graph(video.copy(imageOverlays = listOf(ImagePlacement(blend = LayerBlend.DIFFERENCE, keyframes = keys))))
        assertTrue(g, g.contains("blend=all_mode=difference"))
        assertTrue(g, g.contains("[wk0bdt][wk0bdg]blend"))
    }

    @Test
    fun `a mode is read back by its name, any other is normal`() {
        LayerBlend.entries.forEach { assertEquals(it, LayerBlend.named(it.name)) }
        assertEquals(LayerBlend.NORMAL, LayerBlend.named("SOFT_LIGHT"))
        assertEquals(LayerBlend.NORMAL, LayerBlend.named(null))
    }
}
