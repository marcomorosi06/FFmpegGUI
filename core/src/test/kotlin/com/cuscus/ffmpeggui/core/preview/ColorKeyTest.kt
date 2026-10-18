package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.image.ImageStage
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.image.imageStages
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.SpillScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorKeyTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun List<String>.valueOf(flag: String): String = this[indexOf(flag) + 1]

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mov",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360)),
    )

    private fun opaque(rgb: Int) = (0xFF shl 24) or rgb

    @Test
    fun `the key colour's chroma follows chromakey's fixed point`() {
        // RGB_TO_U / RGB_TO_V of vf_chromakey.c: green 0x00B140 gives U 101 and V 49.
        assertEquals(101 to 49, ColorKeying.keyChroma(0x00B140))
        assertEquals(128 to 128, ColorKeying.keyChroma(0x808080))
    }

    @Test
    fun `a chroma key cuts the screen, keeps the subject and ramps over the blend`() {
        val key = ColorKey(KeyMode.CHROMA, 0x00B140, similarity = 0.1f, blend = 0.1f)
        val screen = IntArray(9) { opaque(0x00B140) }
        assertTrue(ColorKeying.alphas(screen, 3, 3, key).all { it == 0 })
        val subject = IntArray(9) { opaque(0xC04020) }
        // The middle one (the edges average in neighbours that start out as the key, as in FFmpeg).
        assertEquals(255, ColorKeying.alphas(subject, 3, 3, key)[4])
        // With a wide blend the subject (about 0.38 away) is partly see-through.
        val a = ColorKeying.alphas(subject, 3, 3, key.copy(blend = 0.5f))[4]
        assertTrue("alpha $a", a in 1..254)
    }

    @Test
    fun `an exact colour key compares the colour itself, with a hard edge without blend`() {
        val key = ColorKey(KeyMode.COLOR, 0xFFFFFF, similarity = 0.05f, blend = 0f)
        val pixels = intArrayOf(opaque(0xFFFFFF), opaque(0xF8F8F8), opaque(0x000000))
        assertEquals(listOf(0, 0, 255), ColorKeying.alphas(pixels, 3, 1, key).toList())
    }

    @Test
    fun `what is cut shows the background, or stays transparent`() {
        val pixels = intArrayOf(opaque(0x00B140), opaque(0xC04020))
        val transparent = ColorKeying.apply(pixels, 2, 1, ColorKey(KeyMode.COLOR, 0x00B140, 0.1f, 0f))
        assertEquals(0, transparent[0] ushr 24)
        assertEquals(opaque(0xC04020), transparent[1])
        val flat = ColorKeying.apply(pixels, 2, 1, ColorKey(KeyMode.COLOR, 0x00B140, 0.1f, 0f, background = 0x2040FF))
        assertEquals(opaque(0x2040FF), flat[0])
    }

    @Test
    fun `a picture's own transparency stays through the key, or is dropped as the export drops it`() {
        val key = ColorKey(KeyMode.COLOR, 0x00FF00, 0.1f, 0f)
        val half = (128 shl 24) or 0xC04020
        assertEquals(128, ColorKeying.apply(intArrayOf(half), 1, 1, key)[0] ushr 24)
        assertEquals(255, ColorKeying.apply(intArrayOf(half), 1, 1, key, keepAlpha = false)[0] ushr 24)
    }

    @Test
    fun `spill removal lowers the screen's channel only, on green and blue screens`() {
        assertEquals(SpillScreen.GREEN, ColorKey(spill = 0.5f).spillScreen)
        assertEquals(SpillScreen.BLUE, ColorKey(color = ColorKey.BLUE_SCREEN, spill = 0.5f).spillScreen)
        assertNull(ColorKey(color = 0xFF0000, spill = 0.5f).spillScreen)
        assertNull(ColorKey(mode = KeyMode.COLOR, spill = 0.5f).spillScreen)
        // A green-tinted edge: the green comes down, red and blue stay.
        val edge = intArrayOf(opaque(0x80C080))
        val out = ColorKeying.apply(edge, 1, 1, ColorKey(KeyMode.CHROMA, ColorKey.GREEN_SCREEN, 0.00001f, 0f, spill = 1f, background = 0))
        assertTrue(kotlin.math.abs(((out[0] shr 16) and 0xFF) - 0x80) <= 1)
        assertTrue(kotlin.math.abs((out[0] and 0xFF) - 0x80) <= 1)
        assertTrue(kotlin.math.abs(((out[0] shr 8) and 0xFF) - 0x80) <= 1)
    }

    @Test
    fun `a clip's key is laid over its background in the clip's chain`() {
        val request = video.copy(segments = listOf(Segment(0, 0, 4_000, Effects(key = ColorKey(spill = 0.5f, background = 0xFF0000)))))
        val graph = args(request).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("format=yuva444p,chromakey=color=0x00B140:similarity=0.1200:blend=0.0600,despill=type=green:mix=0.5:expand=0:red=0:green=-0.5000:blue=0,split[kc0k][kc0b];"))
        assertTrue(graph, graph.contains("[kc0b]format=yuv420p,drawbox=x=0:y=0:w=iw:h=ih:color=0xFF0000@1:t=fill[kc0c];[kc0c][kc0k]overlay=format=auto"))
    }

    @Test
    fun `the whole video's key comes after its colours, black where it has no background`() {
        val request = video.copy(effects = Effects(color = com.cuscus.ffmpeggui.core.model.ColorSettings(contrast = 1.2f), key = ColorKey(KeyMode.COLOR, 0x112233, 0.2f, 0f)))
        val filter = args(request).valueOf("-vf")
        val eq = filter.indexOf("eq=")
        val key = filter.indexOf("colorkey=color=0x112233:similarity=0.2000:blend=0.0000")
        assertTrue(filter, eq in 0 until key)
        assertTrue(filter, filter.contains("color=0x000000@1:t=fill[kwc]"))
    }

    @Test
    fun `an overlay's key cuts it after its own colours and lets the video show`() {
        val request = video.copy(imageOverlays = listOf(ImagePlacement(brightness = 0.1f, key = ColorKey(background = 0x00FF00))))
        val graph = args(request).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("[1:v]eq=brightness=0.10:contrast=1.00:saturation=1.00,format=rgba,split[wky0o][wky0s];[wky0s]format=yuva444p,chromakey="))
        // Its own transparency stays: the two alpha planes multiplied, then merged back.
        assertTrue(graph, graph.contains("[wky0oa][wky0ka]blend=all_mode=multiply[wky0am];[wky0k][wky0am]alphamerge[wovp0]"))
        // An overlay is never laid over a background of its own.
        assertFalse(graph, graph.contains("drawbox"))
    }

    @Test
    fun `a cut-out picture keeps its transparency where the format can`() {
        val edit = ImageEdit(key = ColorKey())
        val png = args(video.copy(format = OutputFormat.PNG, image = edit.keyFor(OutputFormat.PNG)))
        assertEquals(listOf("-c:v", "png", "-pix_fmt", "rgba", "output.png"), png.takeLast(5))
        assertTrue(png.valueOf("-filter_complex").contains("chromakey=") && png.valueOf("-filter_complex").contains(",format=rgba"))

        // JPG: white instead, in full colour.
        val jpgEdit = edit.keyFor(OutputFormat.JPG)
        assertEquals(ImageEdit.OPAQUE_KEY_BACKGROUND, jpgEdit.key?.background)
        val jpg = args(video.copy(format = OutputFormat.JPG, image = jpgEdit))
        assertTrue(jpg.valueOf("-filter_complex"), jpg.valueOf("-filter_complex").contains("format=yuv444p,drawbox=x=0:y=0:w=iw:h=ih:color=0xFFFFFF@1:t=fill[ikc]"))
        assertFalse(jpg.contains("rgba"))
    }

    @Test
    fun `a see-through fill is laid under the cut-out at its opacity, as overlay does`() {
        val key = ColorKey(KeyMode.COLOR, 0x00FF00, 0.1f, 0f, background = 0x2040FF, backgroundOpacity = 0.5f)
        assertTrue(key.hasTranslucentBackground)
        assertEquals(128, key.backgroundAlpha)
        val out = ColorKeying.apply(intArrayOf(opaque(0x00FF00), opaque(0xC04020)), 2, 1, key, keepAlpha = false)
        // Cut: the fill itself, half opaque. Kept: the picture, opaque.
        assertEquals((128 shl 24) or 0x2040FF, out[0])
        assertEquals(opaque(0xC04020), out[1])
        // A half-cut pixel over it: overlay's un-premultiplied alpha and its alpha compositing.
        val mixed = ColorKeying.overlayStraight(200, 100, 0, 128, 0, 0, 255, 128)
        assertEquals(192, mixed ushr 24)
        assertEquals(133, (mixed shr 16) and 0xFF)
        assertEquals(85, mixed and 0xFF)
    }

    @Test
    fun `a see-through fill makes the picture rgba, and turns opaque where the format cannot hold it`() {
        val key = ColorKey(background = 0xF2C94C, backgroundOpacity = 0.3f)
        val filter = ImageStage.Key(key).filter
        assertTrue(filter, filter.endsWith(",format=rgba,split[ikk][ikb];[ikb]lutrgb=r=242:g=201:b=76:a=77[ikc];[ikc][ikk]overlay=format=rgb,format=rgba"))
        val edit = ImageEdit(key = key)
        assertTrue(edit.isTransparent)
        val png = args(video.copy(format = OutputFormat.PNG, image = edit.keyFor(OutputFormat.PNG)))
        assertEquals(listOf("-c:v", "png", "-pix_fmt", "rgba", "output.png"), png.takeLast(5))
        val jpg = edit.keyFor(OutputFormat.JPG)
        assertEquals(1f, jpg.key?.backgroundOpacity)
        assertEquals(0xF2C94C, jpg.key?.background)
        assertFalse(jpg.isTransparent)
        // A fully opaque fill keeps the full-colour path.
        assertTrue(ImageStage.Key(key.copy(backgroundOpacity = 1f)).filter.contains("drawbox"))
    }

    @Test
    fun `the suggested key takes the border's commonest colour, by hue for a screen and exact for a flat background`() {
        val w = 40
        val h = 30
        val rnd = java.util.Random(3)
        fun picture(background: (Int) -> Int) = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in 12 until 28 && y in 8 until 22) opaque(0xC04020) else opaque(background(rnd.nextInt(9) - 4))
        }
        val green = ColorKeying.suggestedKey(picture { n -> (0x10 + n shl 16) or (0xB0 + n shl 8) or (0x40 + n) }, w, h)!!
        assertEquals(KeyMode.CHROMA, green.mode)
        assertTrue(Integer.toHexString(green.color), kotlin.math.abs(((green.color shr 8) and 0xFF) - 0xB0) <= 4)
        // Its own settings stay, the tolerance is the mode's.
        val white = ColorKeying.suggestedKey(picture { n -> (0xF8 + n / 2) * 0x010101 }, w, h, base = green.copy(blend = 0.2f, background = 0x112233))!!
        assertEquals(KeyMode.COLOR, white.mode)
        assertEquals(ColorKey.DEFAULT_COLOR_SIMILARITY, white.similarity)
        assertEquals(0.2f, white.blend)
        assertEquals(0x112233, white.background)
        assertTrue(Integer.toHexString(white.color), (white.color and 0xFF) >= 0xF0)
        assertNull(ColorKeying.suggestedKey(IntArray(0), 0, 0))
    }

    @Test
    fun `the picture editor's key is its last step, as the export runs it`() {
        val key = ColorKey(KeyMode.COLOR, 0x00FF00, 0.1f, 0f)
        val stages = imageStages(ImageEdit(sharpen = 0.3f, key = key), Effects(), 4, 4)
        assertEquals(ImageStage.Key(key), stages.last())
        val raster = Raster(2, 1, intArrayOf(opaque(0x00FF00), opaque(0x0000FF)))
        val out = stages.last().apply(raster)
        assertEquals(0, out.pixels[0] ushr 24)
        assertEquals(255, out.pixels[1] ushr 24)
    }
}
