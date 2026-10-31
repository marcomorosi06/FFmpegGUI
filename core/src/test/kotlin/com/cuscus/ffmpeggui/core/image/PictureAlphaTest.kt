package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A picture with transparent parts of its own, opened in the picture editor. */
class PictureAlphaTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun photo(format: OutputFormat, hasAlpha: Boolean, edit: ImageEdit = ImageEdit(), effects: Effects = Effects()) = ConversionRequest(
        format = format,
        inputFileName = "logo.png",
        sources = listOf(SourceInfo(width = 400, height = 200, hasAudio = false, isStill = true, hasAlpha = hasAlpha)),
        image = edit,
        effects = effects,
    )

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun graph(request: ConversionRequest): String = args(request).let { it[it.indexOf("-filter_complex") + 1] }

    @Test
    fun `a transparent png stays transparent`() {
        val request = photo(OutputFormat.PNG, hasAlpha = true, effects = Effects(flipHorizontal = true))
        assertTrue(request.keepsPictureAlpha)
        val g = graph(request)
        // Its alpha goes through the same geometry as the picture.
        assertTrue(g, g.startsWith("[0:v]format=rgba,split[opic][oain];[oain]alphaextract,hflip[oalp];[opic]format=rgb24,hflip"))
        // And is multiplied back in at the end.
        assertTrue(g, g.contains("[oma][oalp]blend=all_mode=multiply[omb];[om0][omb]alphamerge"))
        val a = args(request)
        assertEquals("rgba", a[a.indexOf("-pix_fmt") + 1])
    }

    @Test
    fun `an opaque picture, or a format without transparency, is read opaque as before`() {
        listOf(photo(OutputFormat.PNG, hasAlpha = false), photo(OutputFormat.JPG, hasAlpha = true), photo(OutputFormat.BMP, hasAlpha = true)).forEach { request ->
            assertFalse(request.keepsPictureAlpha)
            val g = graph(request)
            assertFalse(g, g.contains("alphaextract"))
            assertTrue(g, g.startsWith("[0:v]format=rgb24"))
        }
        val a = args(photo(OutputFormat.PNG, hasAlpha = false))
        assertEquals("rgb24", a[a.indexOf("-pix_fmt") + 1])
    }

    @Test
    fun `a key on a transparent picture keeps both transparencies`() {
        val edit = ImageEdit(key = ColorKey(mode = KeyMode.COLOR, color = 0x00FF00))
        val g = graph(photo(OutputFormat.WEBP, hasAlpha = true, edit = edit))
        val key = g.indexOf("colorkey")
        val merge = g.indexOf("[oma][oalp]blend")
        assertTrue(g, key in 0 until merge)
    }

    @Test
    fun `the preview puts the alpha back the same way`() {
        // 2 x 1: the left pixel half see-through, the right one opaque.
        val source = Raster(2, 1, intArrayOf((0x80 shl 24) or 0x112233, (0xFF shl 24) or 0x445566))
        val alpha = alphaOf(source)
        assertEquals((0xFF shl 24) or 0x808080, alpha.pixels[0])
        val stages = imageStages(ImageEdit(), Effects(flipHorizontal = true), 2, 1)
        // The picture as the steps make it, opaque: mirrored.
        val picture = render(Raster(2, 1, source.pixels.map { it or (0xFF shl 24) }.toIntArray()), stages)
        val out = withSourceAlpha(picture, alpha, stages)
        // The mirrored picture: the opaque pixel now on the left, the see-through one on the right.
        assertEquals((0xFF shl 24) or 0x445566, out.pixels[0])
        assertEquals((0x80 shl 24) or 0x112233, out.pixels[1])
    }

    @Test
    fun `the alpha is multiplied into a key's, as blend multiplies`() {
        val picture = Raster(1, 1, intArrayOf((200 shl 24) or 0x010203))
        val alpha = Raster(1, 1, intArrayOf((0xFF shl 24) or 0x7F7F7F))
        assertEquals((200 * 127 / 255 shl 24) or 0x010203, withSourceAlpha(picture, alpha, emptyList()).pixels[0])
    }

    @Test
    fun `a turned picture's alpha turns with it`() {
        val source = Raster(2, 1, intArrayOf(0x00000000, -1))
        val stages = imageStages(ImageEdit(), Effects(rotation = Rotation.DEG_90), 2, 1)
        val picture = render(Raster(2, 1, intArrayOf(-0x1000000, -1)), stages)
        val out = withSourceAlpha(picture, alphaOf(source), stages)
        assertEquals(1, out.width)
        assertEquals(2, out.height)
        // Clockwise: the transparent left pixel goes to the top.
        assertEquals(0, out.pixels[0] ushr 24)
        assertEquals(255, out.pixels[1] ushr 24)
    }

    @Test
    fun `whether a picture has an alpha channel is kept with the project`() {
        val info = MediaInfo.of(0L, true, 10, 10, 100L, hasAudio = false, isStill = true, hasAlpha = true)
        assertEquals(info, ModelJson.mediaInfo(ModelJson.write(info)))
    }
}
