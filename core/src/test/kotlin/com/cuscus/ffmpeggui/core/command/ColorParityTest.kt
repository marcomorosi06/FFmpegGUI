package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.preview.eqColorMatrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Low contrast is a flat grey, never a negative: in the preview and in the export alike (issue #21). */
class ColorParityTest {

    private val builder = CommandBuilder { "/fonts/Regular.ttf" }

    private fun graph(color: ColorSettings): String {
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "clip.mov",
            sources = listOf(SourceInfo(durationMs = 20_000, width = 1920, height = 1080, hasAudio = true)),
            segments = listOf(Segment(0, 0, 5_000, Effects(color = color))),
        )
        val args = builder.build(request, MediaPaths.aliasesFor(request)).arguments
        return args[args.indexOf("-filter_complex") + 1]
    }

    @Test
    fun `a contrast below zero is exported as zero, a flat grey`() {
        val g = graph(ColorSettings(contrast = -1.5f))
        assertTrue(g, g.contains("eq=brightness=0.00:contrast=0.00:saturation=1.00"))
    }

    @Test
    fun `a saturation below zero is exported as grey`() {
        val g = graph(ColorSettings(saturation = -0.5f))
        assertTrue(g, g.contains("contrast=1.00:saturation=0.00"))
    }

    @Test
    fun `the preview of a contrast below zero is the grey of zero, as the export`() {
        assertArrayEquals(eqColorMatrix(0f, 0f, 1f), eqColorMatrix(0f, -1.5f, 1f), 1e-6f)
    }

    @Test
    fun `the preview at contrast zero is flat: every channel ignores the picture`() {
        val m = eqColorMatrix(0f, 0f, 1f)
        // With saturation 1 the luma gain is -1 and the channel's own weight 1: what is left is chroma, no luma.
        val grey = floatArrayOf(128f, 128f, 128f)
        val r = m[0] * grey[0] + m[1] * grey[1] + m[2] * grey[2] + m[4]
        val g = m[5] * grey[0] + m[6] * grey[1] + m[7] * grey[2] + m[9]
        assertTrue("grey stays grey: $r $g", kotlin.math.abs(r - g) < 1e-3f)
    }
}
