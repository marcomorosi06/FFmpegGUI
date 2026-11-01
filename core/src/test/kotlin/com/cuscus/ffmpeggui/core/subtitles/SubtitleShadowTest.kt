package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The styled subtitles' shadow (any look, any colour) and the user's own font, kept with the project. */
class SubtitleShadowTest {

    private fun styleLine(style: SubtitleStyle): String =
        AssSubtitles.write(emptyList(), style, 16f / 9f, "Sans").lines().first { it.startsWith("Style: ") }

    @Test
    fun `a shadow under the outline, in its own colour`() {
        val plain = styleLine(SubtitleStyle(look = SubtitleLook.OUTLINE)).split(',')
        assertEquals("0", plain[17])
        val shaded = styleLine(SubtitleStyle(look = SubtitleLook.OUTLINE, dropShadow = true, shadowColor = 0xCC0A84FFL)).split(',')
        assertTrue(shaded[17], shaded[17].toFloat() > 0f)
        // BackColour, as &HAABBGGRR: alpha 0xCC turned (0x33), then blue, green, red.
        assertEquals("&H33FF840A", shaded[6])
        assertTrue(SubtitleStyle(look = SubtitleLook.SHADOW).hasShadow)
    }

    @Test
    fun `the new fields are kept`() {
        val style = SubtitleStyle(fontFile = "/fonts/mine.ttf", dropShadow = true, shadowColor = 0xFF123456L)
        assertEquals(style, ModelJson.subtitleStyle(ModelJson.write(style)))
        assertEquals("/fonts/mine.ttf", SubtitlePreset.POP.apply(style).fontFile)
    }
}
