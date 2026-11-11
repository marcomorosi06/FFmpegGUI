package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.TextLook
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A caption's outline, shadow, box and own font file, as drawtext draws them (issue #22). */
class TextLookExportTest {

    private fun graph(text: TextOverlay, fonts: FontProvider = FontProvider { "/fonts/Regular.ttf" }): String {
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "clip.mov",
            sources = listOf(SourceInfo(durationMs = 20_000, width = 1920, height = 1080, hasAudio = true)),
            textOverlays = listOf(text),
        )
        // The captions go in -vf or in -filter_complex, as the rest of the graph asks: read them in either.
        return CommandBuilder(fonts).build(request, MediaPaths.aliasesFor(request)).arguments.joinToString(" ")
    }

    private val caption = TextOverlay(id = "t", text = "Hi", size = 0.05f)

    @Test
    fun `a turned caption is drawn on a canvas about its anchor and laid back there`() {
        val g = graph(caption.copy(x = 0.25f, y = 0.75f, rotationDegrees = 30f))
        // A canvas twice the frame's size, see-through in the letters' colour, the anchor in its middle.
        assertTrue(g, g.contains("format=rgba,lutrgb=r=255:g=255:b=255:a=0,pad=w=iw*2:h=ih*2:x=iw/2:y=ih/2:color=0xFFFFFF@0,drawtext="))
        assertTrue(g, g.contains(":fontsize=h/2*0.0500:x='w/2-tw*0.2500':y='h/2-th*0.7500'"))
        assertTrue(g, g.contains(",rotate=a=0.523599:c=none["))
        assertTrue(g, g.contains("overlay=x='main_w*0.2500-overlay_w/2':y='main_h*0.7500-overlay_h/2':format=auto"))
        // Not turned nor blended: straight on the frame, as before.
        assertFalse(graph(caption.copy(rotationDegrees = 360f)), graph(caption.copy(rotationDegrees = 360f)).contains("pad=w=iw*2"))
    }

    @Test
    fun `a stretched caption is drawn on a narrower canvas and widened about its anchor`() {
        val g = graph(caption.copy(x = 0.25f, y = 0.75f, stretch = 2f))
        // Twice as wide: a canvas as wide as the frame (2 / 2), as high as two, widened twice.
        assertTrue(g, g.contains("format=rgba,lutrgb=r=255:g=255:b=255:a=0,scale=w=trunc(iw*2/2.0000/2)*2:h=ih*2,setsar=1,drawtext="))
        assertTrue(g, g.contains(":fontsize=h/2*0.0500:x='w/2-tw*0.2500':y='h/2-th*0.7500'"))
        assertTrue(g, g.contains(",scale=w=trunc(iw*2.0000/2)*2:h=ih,setsar=1["))
        assertTrue(g, g.contains("overlay=x='main_w*0.2500-overlay_w/2':y='main_h*0.7500-overlay_h/2':format=auto"))
        // Narrower: the canvas of every layered caption, then narrowed; turned after it.
        val narrow = graph(caption.copy(stretch = 0.5f, rotationDegrees = 30f))
        assertTrue(narrow, narrow.contains("scale=w=iw*2:h=ih*2,setsar=1,drawtext="))
        assertTrue(narrow, narrow.contains(",scale=w=trunc(iw*0.5000/2)*2:h=ih,setsar=1,rotate=a=0.523599:c=none["))
        // Hardly stretched: drawn straight on the frame, as before.
        assertFalse(graph(caption.copy(stretch = 1.0005f)).contains("lutrgb"))
    }

    @Test
    fun `a stretch is kept with the project, within its range`() {
        val stretched = caption.copy(stretch = 1.75f)
        assertEquals(stretched, ModelJson.textOverlay(ModelJson.write(stretched)))
        assertEquals(4f, caption.copy(stretch = 9f).laidStretch, 0f)
        assertEquals(1f, caption.copy(stretch = Float.NaN).laidStretch, 0f)
    }

    @Test
    fun `a blended caption is mixed as a blended picture is`() {
        val g = graph(caption.copy(blend = com.cuscus.ffmpeggui.core.model.LayerBlend.MULTIPLY))
        assertTrue(g, g.contains("split=4["))
        assertTrue(g, g.contains("blend=all_mode=multiply"))
        assertTrue(g, g.contains("alphaextract"))
        // Upright: no turn.
        assertFalse(g, g.contains("rotate="))
    }

    @Test
    fun `turn and blend are kept with the project`() {
        val turned = caption.copy(rotationDegrees = -12.5f, blend = com.cuscus.ffmpeggui.core.model.LayerBlend.SCREEN)
        assertEquals(turned, ModelJson.textOverlay(ModelJson.write(turned)))
        assertEquals(caption, ModelJson.textOverlay(ModelJson.write(caption)))
    }

    @Test
    fun `a plain caption has no outline, shadow or box`() {
        val g = graph(caption)
        assertFalse(g, g.contains("borderw") || g.contains("shadowx") || g.contains("box="))
    }

    @Test
    fun `outline, shadow and box are in pixels of the font on the frame`() {
        // A 1080 high frame, a font 5 % of it: 54 pixels.
        val styled = caption.copy(look = TextLook(outline = 0.1f, shadow = 0.08f, box = true, boxPadding = 0.3f))
        val g = graph(styled)
        assertTrue(g, g.contains(":borderw=5:bordercolor=0x000000@1.000"))
        assertTrue(g, g.contains(":shadowx=4:shadowy=4:shadowcolor=0x000000@0.702"))
        assertTrue(g, g.contains(":box=1:boxcolor=0x000000@0.600:boxborderw=16"))
    }

    @Test
    fun `a soft shadow is the letters apart, blurred and laid under the text`() {
        val soft = caption.copy(look = TextLook(shadow = 0.08f, shadowBlur = 0.2f, shadowColor = 0xB3102030L))
        val g = graph(soft)
        // Drawn on a see-through copy in the shadow's own colour, moved 4 px, blurred (11 px: sigma 5.5).
        assertTrue(g, g.contains("format=rgba,lutrgb=r=16:g=32:b=48:a=0,drawtext="))
        assertTrue(g, g.contains(":fontcolor=0x102030@0.702:fontsize=h*0.0500:x=(w-tw)*0.5000+4:y=(h-th)*0.5000+4"))
        assertTrue(g, g.contains("gblur=sigma=5.50"))
        assertTrue(g, g.contains("overlay=format=auto,drawtext="))
        // The text itself has no sharp shadow of its own then.
        assertFalse(g, g.contains("shadowx"))
    }

    @Test
    fun `a soft shadow that is not moved is a glow`() {
        val glow = caption.copy(look = TextLook(shadowBlur = 0.2f))
        val g = graph(glow)
        assertTrue(g, g.contains("x=(w-tw)*0.5000:y=(h-th)*0.5000,gblur="))
    }

    @Test
    fun `colours keep their alpha`() {
        val styled = caption.copy(look = TextLook(outline = 0.1f, outlineColor = 0x80FF0000L))
        assertTrue(graph(styled).contains(":bordercolor=0xFF0000@0.502"))
    }

    @Test
    fun `a font the user added is drawn with its own file`() {
        val g = graph(caption.copy(fontFile = "/data/fonts/Mine.otf"))
        assertTrue(g, g.contains("drawtext=fontfile='/data/fonts/Mine.otf':text='Hi'"))
    }

    @Test
    fun `a font file that is gone falls back to the caption's family`() {
        val gone = object : FontProvider {
            override fun fontPath(bold: Boolean): String = "/fonts/Regular.ttf"
            override fun customFontPath(file: String): String? = null
        }
        val g = graph(caption.copy(fontFile = "/data/fonts/Gone.ttf"), gone)
        assertTrue(g, g.contains("drawtext=fontfile='/fonts/Regular.ttf'"))
    }

    @Test
    fun `the look and the font file are kept in the project`() {
        val styled = caption.copy(
            look = TextLook(outline = 0.12f, outlineColor = 0xFF112233L, shadow = 0.05f, shadowBlur = 0.1f, box = true, boxColor = 0x80FFFFFFL, boxPadding = 0.4f),
            fontFile = "/data/fonts/Mine.ttf",
        )
        assertEquals(styled, ModelJson.textOverlay(ModelJson.write(styled)))
        assertEquals(caption, ModelJson.textOverlay(ModelJson.write(caption)))
    }

    @Test
    fun `pixels are at least one when asked for, none when not`() {
        assertEquals(0, TextLook.pixels(0f, 54.0))
        assertEquals(1, TextLook.pixels(0.001f, 54.0))
        assertEquals(5, TextLook.pixels(0.1f, 54.0))
    }
}
