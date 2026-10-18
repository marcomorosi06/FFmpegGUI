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
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The subject cut out by the model's mask (KeyMode.SUBJECT), in the export and the preview. */
class SubjectKeyTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun List<String>.valueOf(flag: String): String = this[indexOf(flag) + 1]

    private val picture = ConversionRequest(
        format = OutputFormat.PNG,
        inputFileName = "photo.jpg",
        sources = listOf(SourceInfo(durationMs = 0, width = 400, height = 300)),
    )

    private val subject = ColorKey(mode = KeyMode.SUBJECT, mask = "/data/cutouts/a b.png")

    private fun opaque(rgb: Int) = (0xFF shl 24) or rgb

    @Test
    fun `the mask is read, turned as the picture was and merged in as its alpha`() {
        val stages = imageStages(ImageEdit(key = subject), Effects(rotation = Rotation.DEG_90, flipHorizontal = true), 400, 300)
        val key = stages.last() as ImageStage.Key
        assertEquals(listOf("hflip", "transpose=1"), key.maskGeometry.map { it.filter })
        val filter = key.filter
        assertTrue(filter, filter.startsWith("format=rgba,loop=loop=-1:size=1[ikxp];movie='/data/cutouts/a b.png',alphaextract,hflip,transpose=1,loop=loop=-1:size=1[ikxm];"))
        assertTrue(filter, filter.contains("[ikxm][ikxp]scale2ref[ikxms][ikxps];[ikxps][ikxms]alphamerge,trim=end_frame=1"))
        assertTrue(filter, filter.endsWith(",format=rgba"))
        // The colour plays no part.
        assertFalse(filter, filter.contains("chromakey") || filter.contains("colorkey"))
    }

    @Test
    fun `a subject over a fill, opaque or see-through`() {
        val over = ImageStage.Key(subject.copy(background = 0x00B140)).filter
        assertTrue(over, over.startsWith("format=rgba,loop=loop=-1:size=1[ikxp];movie="))
        assertTrue(over, over.contains("alphamerge,trim=end_frame=1,split[ikk][ikb];[ikb]format=yuv444p,drawbox"))
        val see = ImageStage.Key(subject.copy(background = 0xFFFFFF, backgroundOpacity = 0.5f)).filter
        assertTrue(see, see.contains("alphamerge,trim=end_frame=1,format=rgba,split[ikk][ikb];[ikb]lutrgb"))
        // A JPG gets the white the format needs, around the subject.
        val jpg = args(picture.copy(format = OutputFormat.JPG, image = ImageEdit(key = subject).keyFor(OutputFormat.JPG)))
        assertTrue(jpg.valueOf("-filter_complex"), jpg.valueOf("-filter_complex").contains("color=0xFFFFFF@1:t=fill"))
    }

    @Test
    fun `without its mask a subject key cuts nothing`() {
        val key = ColorKey(mode = KeyMode.SUBJECT)
        assertEquals("format=rgba,format=rgba", ImageStage.Key(key).filter)
        val out = ColorKeying.apply(intArrayOf(opaque(0x00B140), opaque(0x123456)), 2, 1, key, keepAlpha = false)
        assertEquals(255, out[0] ushr 24)
        assertEquals(255, out[1] ushr 24)
    }

    @Test
    fun `the preview cuts by the mask, shaped as the picture`() {
        // A 2 x 1 picture whose right pixel is the subject; the picture is mirrored.
        val mask = Raster(2, 1, intArrayOf(opaque(0x000000), opaque(0xFFFFFF)))
        val stages = imageStages(ImageEdit(key = subject), Effects(flipHorizontal = true), 2, 1, subjectMask = mask)
        val source = Raster(2, 1, intArrayOf(opaque(0xFF0000), opaque(0x0000FF)))
        var raster = source
        for (stage in stages) raster = stage.apply(raster)
        // Mirrored: the blue pixel is now on the left and is the subject, the red one is cut.
        assertEquals(opaque(0x0000FF), raster.pixels[0])
        assertEquals(0, raster.pixels[1] ushr 24)
        // Over a fill, what is cut shows it.
        val filled = imageStages(ImageEdit(key = subject.copy(background = 0x00FF00)), Effects(), 2, 1, subjectMask = mask)
            .last().apply(source)
        assertEquals(opaque(0x00FF00), filled.pixels[0])
        assertEquals(opaque(0x0000FF), filled.pixels[1])
    }

    @Test
    fun `a picture laid over the video keeps its own transparency, its mask mirrored with it`() {
        val video = picture.copy(format = OutputFormat.MP4, inputFileName = "clip.mp4", sources = listOf(SourceInfo(durationMs = 5_000, width = 640, height = 360)))
        val graph = args(video.copy(imageOverlays = listOf(ImagePlacement(flipVertical = true, key = subject)))).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("vflip,format=rgba,split[wky0o][wky0s];[wky0s]format=rgba,loop=loop=-1:size=1[wky0xp];movie='/data/cutouts/a b.png',alphaextract,vflip,loop=loop=-1:size=1[wky0xm];"))
        assertTrue(graph, graph.contains("[wky0oa][wky0ka]blend=all_mode=multiply"))
    }

    @Test
    fun `the mask is kept in the project`() {
        val back = ModelJson.colorKey(ModelJson.write(subject.copy(background = 0x112233)))
        assertEquals(subject.copy(background = 0x112233), back)
        assertTrue(back.cutsSubject)
        assertFalse(ColorKey().cutsSubject)
    }
}
