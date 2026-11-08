package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.image.fittedPhoto
import com.cuscus.ffmpeggui.core.image.stretchedPhoto
import com.cuscus.ffmpeggui.core.model.CanvasFit
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframe
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.PictureCanvas
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.VideoPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The video on a frame of another shape: whole, covering it or stretched to it (issue #34). */
class CanvasFitTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun vf(request: ConversionRequest): String = args(request).let { it[it.indexOf("-vf") + 1] }

    private fun graph(request: ConversionRequest): String = args(request).let { it[it.indexOf("-filter_complex") + 1] }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 1920, height = 1080, hasAudio = true)),
    )

    @Test
    fun `whole keeps the black bars`() {
        val chain = vf(video.copy(canvas = CanvasRatio.PORTRAIT_9_16))
        assertTrue(chain, chain.contains("pad=width='ceil(max(iw,ih*(9/16))/2)*2'"))
        assertFalse(chain, chain.contains("setsar"))
    }

    @Test
    fun `stretched, the video is scaled to the canvas both ways`() {
        val chain = vf(video.copy(canvas = CanvasRatio.PORTRAIT_9_16, canvasFit = CanvasFit.STRETCH))
        assertTrue(chain, chain.contains("scale=w='ceil(max(iw,ih*(9/16))/2)*2':h='ceil(max(ih,iw/(9/16))/2)*2',setsar=1"))
        assertFalse(chain, chain.contains("pad="))
        assertFalse(chain, chain.contains("crop="))
        // A square canvas reads its ratio as 1.
        assertTrue(vf(video.copy(canvas = CanvasRatio.SQUARE, canvasFit = CanvasFit.STRETCH)).contains("scale=w='ceil(max(iw,ih*1)/2)*2'"))
    }

    @Test
    fun `covering, the video is enlarged to the canvas and its edges cut`() {
        val chain = vf(video.copy(canvas = CanvasRatio.PORTRAIT_9_16, canvasFit = CanvasFit.FILL))
        assertTrue(chain, chain.contains(":force_original_aspect_ratio=increase,crop=w='trunc(min(iw,ih*(9/16))/2)*2':h='trunc(min(ih,iw/(9/16))/2)*2',setsar=1"))
        assertFalse(chain, chain.contains("pad="))
    }

    @Test
    fun `without a canvas of its own the fit does nothing`() {
        CanvasFit.entries.forEach { fit ->
            assertEquals(vf(video), vf(video.copy(canvasFit = fit)))
        }
    }

    @Test
    fun `placed by hand, the video lies on the canvas as the fit lays it`() {
        val moved = video.copy(canvas = CanvasRatio.SQUARE, effects = Effects(placement = VideoPlacement(centerX = 0.4f, scale = 1.5f)))
        // The canvas is black and padded either way; the picture on it is sized by the fit.
        listOf(CanvasFit.FIT, CanvasFit.FILL, CanvasFit.STRETCH).forEach { fit ->
            assertTrue(vf(moved.copy(canvasFit = fit)).contains("[vpa]pad=width='ceil(max(iw,ih)/2)*2'"))
        }
        assertTrue(vf(moved).contains("[vpb]scale=w='max(2,trunc(iw*1.5000/2)*2)'"))
        assertTrue(vf(moved.copy(canvasFit = CanvasFit.STRETCH)).contains("[vpb]scale=w='max(2,trunc(max(iw,ih*1)*1.5000/2)*2)':h='max(2,trunc(max(ih,iw/1)*1.5000/2)*2)'"))
        assertTrue(vf(moved.copy(canvasFit = CanvasFit.FILL)).contains("[vpb]scale=w='max(2,trunc(iw*max(ih*1/iw,iw/(ih*1))*1.5000/2)*2)'"))
    }

    @Test
    fun `the frame the pictures land on is the canvas's either way`() {
        CanvasFit.entries.forEach { fit ->
            // 1920 x 1080 on 9:16: the frame grows to 1920 x 3414, as the bars make it.
            assertEquals(1920 to 3414, builder.wholeFrameSize(video.copy(canvas = CanvasRatio.PORTRAIT_9_16, canvasFit = fit)))
        }
    }

    @Test
    fun `a stretched picture is laid as wide and less high`() {
        val g = graph(video.copy(imageOverlays = listOf(ImagePlacement(widthFraction = 0.25f, aspect = 1f, stretch = 2f))))
        assertTrue(g, g.contains("scale=w=480:h='max(2,trunc(ow/dar/2.0000/2)*2)',setsar=1"))
        // Its own shape: as before.
        val plain = graph(video.copy(imageOverlays = listOf(ImagePlacement(widthFraction = 0.25f, aspect = 1f))))
        assertTrue(plain, plain.contains("scale=w=480:h='max(2,trunc(ow/dar/2)*2)'["))
        // Moving, it is sized once, stretched.
        val keys = Keyframes(listOf(Keyframe(0L, mapOf(KeyProperty.X to 0f)), Keyframe(1_000L, mapOf(KeyProperty.X to 1f))))
        val moving = graph(video.copy(imageOverlays = listOf(ImagePlacement(widthFraction = 0.25f, aspect = 1f, stretch = 2f, keyframes = keys))))
        assertTrue(moving, moving.contains("trunc(ow/dar/2.0000/2)*2)',setsar=1"))
    }

    @Test
    fun `stretch stays within bounds`() {
        assertEquals(1f, ImagePlacement(stretch = 1.0001f).saneStretch, 0f)
        assertEquals(1f, ImagePlacement(stretch = Float.NaN).saneStretch, 0f)
        assertEquals(ImagePlacement.MAX_STRETCH, ImagePlacement(stretch = 40f).saneStretch, 0f)
        assertEquals(ImagePlacement.MIN_STRETCH, ImagePlacement(stretch = 0.01f).saneStretch, 0f)
        assertEquals(3f, ImagePlacement(aspect = 1.5f, stretch = 2f).laidPictureAspect, 1e-6f)
        assertEquals(0f, ImagePlacement(stretch = 2f).laidPictureAspect, 0f)
    }

    @Test
    fun `a picture stretched to its canvas fills it`() {
        val canvas = PictureCanvas(1080, 1920, null)
        val placed = stretchedPhoto(canvas, 4000, 3000)
        assertEquals(1f, placed.widthFraction, 0f)
        // Laid as wide as the canvas, it is as high as it too.
        assertEquals(1080f / 1920f, placed.laidPictureAspect, 1e-4f)
        // Whole, it keeps its shape.
        assertEquals(1f, fittedPhoto(canvas, 4000, 3000).stretch, 0f)
    }
}
