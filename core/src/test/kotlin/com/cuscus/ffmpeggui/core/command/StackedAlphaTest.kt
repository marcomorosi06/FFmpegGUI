package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.OverlayVideo
import com.cuscus.ffmpeggui.core.model.SourceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A video packed with its opacity (a punch's 3D fist), laid over the video in the export. */
class StackedAlphaTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true)),
    )

    private fun graph(request: ConversionRequest): String {
        val args = builder.build(request, MediaPaths.aliasesFor(request)).arguments
        return args[args.indexOf("-filter_complex") + 1]
    }

    private val fist = ImagePlacement(
        startMs = 2_000,
        endMs = 3_000,
        video = OverlayVideo(0, 1_000),
        cover = true,
        stackedAlpha = true,
    )

    @Test
    fun `its two halves become one picture with transparency`() {
        val g = graph(video.copy(imageOverlays = listOf(fist)))
        assertTrue(g, g.contains("crop=iw:ih/2:0:0["))
        assertTrue(g, g.contains("crop=iw:ih/2:0:ih/2,format=yuv420p,extractplanes=y,lut=c0='if(lt(val,19),0,if(gt(val,232),255,(val-16)*255/219))'["))
        // Nothing turns the shared frame grey: the colours keep their colour.
        assertFalse(g, g.contains("format=gray"))
        assertTrue(g, g.contains("alphamerge"))
        // Filling the frame, in the middle.
        assertTrue(g, g.contains("scale=w=640:h=360:force_original_aspect_ratio=increase,crop=w=640:h=360"))
        assertTrue(g, g.contains("overlay=x=(W-w)*0.5000:y=(H-h)*0.5000"))
    }

    @Test
    fun `a smaller one fills a smaller frame in the middle`() {
        val g = graph(video.copy(imageOverlays = listOf(fist.copy(coverSize = 0.5f))))
        assertTrue(g, g.contains("scale=w=320:h=180:force_original_aspect_ratio=increase,crop=w=320:h=180,format=yuva420p,geq="))
        // Faded out towards its edges, so that nothing is cut there.
        assertTrue(g, g.contains("a='alpha(X,Y)*clip(min(X,W-1-X)/(0.18*min(W,H)),0,1)*clip(min(Y,H-1-Y)/(0.18*min(W,H)),0,1)'"))
        assertTrue(g, g.contains("overlay=x=(W-w)*0.5000:y=(H-h)*0.5000"))
    }

    @Test
    fun `a fist filling the frame is not faded at its edges`() {
        val g = graph(video.copy(imageOverlays = listOf(fist)))
        assertFalse(g, g.contains("geq=lum='lum(X,Y)'"))
    }

    @Test
    fun `an ordinary video is not split`() {
        val g = graph(video.copy(imageOverlays = listOf(fist.copy(stackedAlpha = false))))
        assertFalse(g, g.contains("crop=iw:ih/2"))
    }
}
