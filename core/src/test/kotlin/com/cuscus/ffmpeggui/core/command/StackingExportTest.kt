package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.LayerMove
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.StackSlot
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.model.VideoPlacement
import com.cuscus.ffmpeggui.core.model.movedLayers
import com.cuscus.ffmpeggui.core.model.stackOrder
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Captions and pictures stacked in their order, and the video placed on its canvas. */
class StackingExportTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun graph(request: ConversionRequest): String =
        builder.build(request, MediaPaths.aliasesFor(request)).arguments.let { it[it.indexOf("-filter_complex") + 1] }

    private fun vf(request: ConversionRequest): String =
        builder.build(request, MediaPaths.aliasesFor(request)).arguments.let { it[it.indexOf("-vf") + 1] }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true)),
    )

    @Test
    fun `the stack goes by layer, captions under pictures on the same one`() {
        assertEquals(listOf(StackSlot(true, 0), StackSlot(true, 1), StackSlot(false, 0)), stackOrder(listOf(0, 0), listOf(0)))
        assertEquals(listOf(StackSlot(false, 0), StackSlot(true, 1), StackSlot(true, 0)), stackOrder(listOf(5, 2), listOf(1)))
        val order = stackOrder(listOf(0, 0), listOf(0))
        assertEquals(listOf(StackSlot(true, 0), StackSlot(false, 0), StackSlot(true, 1)), movedLayers(order, 1, LayerMove.TO_FRONT))
        assertEquals(listOf(StackSlot(false, 0), StackSlot(true, 0), StackSlot(true, 1)), movedLayers(order, 2, LayerMove.TO_BACK))
        assertEquals(order, movedLayers(order, 2, LayerMove.FORWARD))
    }

    @Test
    fun `a caption on a higher layer is drawn over the pictures of the whole video`() {
        val request = video.copy(
            textOverlays = listOf(TextOverlay(text = "under"), TextOverlay(text = "over", layer = 3)),
            imageOverlays = listOf(ImagePlacement(layer = 1)),
            effects = Effects(fadeOutSeconds = 1f),
        )
        val g = graph(request)
        val under = g.indexOf("text='under'")
        val picture = g.indexOf("[wmain][wov0]overlay")
        val over = g.indexOf("text='over'")
        assertTrue(g, under in 0 until picture && picture < over)
        // The caption on top, then the fade over everything.
        assertTrue(g, g.contains("[wtmp0]drawtext="))
        assertTrue(g, g.contains("[wend]fade=t=out"))
    }

    @Test
    fun `a clip's caption may lie over its pictures, under its fades`() {
        val request = video.copy(
            segments = listOf(Segment(0, 0, 3_000, Effects(fadeInSeconds = 0.5f), clipId = "c")),
            textOverlays = listOf(TextOverlay(text = "top", clipId = "c", layer = 2)),
            imageOverlays = listOf(ImagePlacement(clipId = "c")),
        )
        val g = graph(request)
        assertTrue(g, g.contains("[c0tmp0]drawtext="))
        assertTrue(g, g.indexOf("text='top'") > g.indexOf("[c0main][c0ov0]overlay"))
        assertTrue(g, g.contains("[c0end]fade=t=in:st=0:d=0.5"))
    }

    @Test
    fun `without pictures the captions stay in one chain, by layer`() {
        val request = video.copy(textOverlays = listOf(TextOverlay(text = "b", layer = 1), TextOverlay(text = "a")))
        val chain = vf(request)
        assertTrue(chain, chain.indexOf("text='a'") < chain.indexOf("text='b'"))
    }

    @Test
    fun `a picture may go past the edges of the frame`() {
        val g = graph(video.copy(imageOverlays = listOf(ImagePlacement(x = -0.5f, y = 1.5f), ImagePlacement(x = 7f))))
        assertTrue(g, g.contains("overlay=x=(W-w)*-0.5000:y=(H-h)*1.5000"))
        assertTrue(g, g.contains("overlay=x=(W-w)*2.0000:"))
    }

    @Test
    fun `the video placed on its canvas`() {
        val request = video.copy(
            canvas = CanvasRatio.SQUARE,
            effects = Effects(placement = VideoPlacement(centerX = 0.3f, centerY = 0.6f, scale = 1.5f, rotationDegrees = 370f)),
        )
        val chain = vf(request)
        assertTrue(chain, chain.contains("split[vpa][vpb];[vpa]pad=width='ceil(max(iw,ih)/2)*2'"))
        assertTrue(chain, chain.contains(",drawbox=x=0:y=0:w=iw:h=ih:color=black:t=fill[vpc]"))
        assertTrue(chain, chain.contains("[vpb]scale=w='max(2,trunc(iw*1.5000/2)*2)'"))
        assertTrue(chain, chain.contains("rotate=0.174533:"))
        assertTrue(chain, chain.contains("[vpc][vpd]overlay=x='W*0.3000-w/2':y='H*0.6000-h/2',setsar=1"))
        // Unmoved: the plain canvas.
        assertFalse(vf(video.copy(canvas = CanvasRatio.SQUARE, effects = Effects(placement = VideoPlacement()))).contains("vpa"))
        val effects = Effects(placement = VideoPlacement(0.2f, 0.7f, 2f, 45f))
        assertEquals(effects, ModelJson.effects(ModelJson.write(effects)))
        val text = TextOverlay(text = "t", layer = 4)
        assertEquals(text, ModelJson.textOverlay(ModelJson.write(text)))
    }
}
