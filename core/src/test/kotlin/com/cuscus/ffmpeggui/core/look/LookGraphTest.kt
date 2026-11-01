package com.cuscus.ffmpeggui.core.look

import com.cuscus.ffmpeggui.core.model.LookSpan
import com.cuscus.ffmpeggui.core.model.PictureLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * The picture looks in the export (see lookSpanGraph): a graph whose labels
 * are made once and used once, every filter switched on only while the look
 * lasts, nothing cut out of the video; and their shaders for the preview.
 */
class LookGraphTest {

    private fun graph(look: PictureLook, strength: Float = 1f, fadeIn: Float = 0f, hue: Float = 0f): List<String> =
        lookSpanGraph(LookSpan(look, 2_000, 5_500, strength, fadeIn, 0f, hue), "in", "out", "q", 1280, 720, 30)

    private val variants: List<Pair<String, List<String>>>
        get() = PictureLook.entries.flatMap { look ->
            listOf("$look" to graph(look), "$look mixed" to graph(look, strength = 0.6f, fadeIn = 0.5f, hue = 90f))
        }

    @Test
    fun `each label is made once and used once`() {
        val label = Regex("""\[[A-Za-z0-9_]+]""")
        for ((name, lines) in variants) {
            val made = HashMap<String, Int>()
            val used = HashMap<String, Int>()
            for (line in lines) {
                val inputs = Regex("""^(\[[A-Za-z0-9_]+])+""").find(line)?.value.orEmpty()
                val outputs = Regex("""(\[[A-Za-z0-9_]+])+$""").find(line)?.value.orEmpty()
                label.findAll(inputs).forEach { used.merge(it.value, 1, Int::plus) }
                label.findAll(outputs).forEach { made.merge(it.value, 1, Int::plus) }
            }
            assertEquals("$name: the video is read once", 1, used["[in]"])
            assertEquals("$name: one result", 1, made["[out]"])
            for ((made1, count) in made) {
                assertEquals("$name: $made1 made", 1, count)
                if (made1 != "[out]") assertEquals("$name: $made1 used", 1, used[made1])
            }
            for (u in used.keys) assertTrue("$name: $u used but never made", u in made || u == "[in]")
        }
    }

    @Test
    fun `everything is on only while the look lasts, and nothing is cut out`() {
        for ((name, lines) in variants) {
            val text = lines.joinToString(";")
            assertTrue(name, text.contains("between(t,2.0000,5.5000)"))
            assertFalse("$name: no frame waits for the look to start", text.contains("trim="))
            for (line in lines) {
                assertEquals("$name: $line", 0, line.count { it == '\'' } % 2)
                assertTrue("$name: ASCII", line.all { it.code < 128 })
            }
        }
    }

    @Test
    fun `a full look is laid as it is, a weaker one mixed back`() {
        assertEquals(listOf("[in]negate=enable='between(t,2.0000,5.5000)'[out]"), graph(PictureLook.INVERT))
        val mixed = graph(PictureLook.INVERT, strength = 0.6f, fadeIn = 0.5f, hue = 90f).joinToString(";")
        assertTrue(mixed, mixed.contains("[in]split=2[qkeep][qsrc]"))
        assertTrue(mixed, mixed.contains("hue=h=90.0"))
        assertTrue(mixed, mixed.contains("lut=a='val*0.6000'"))
        assertTrue(mixed, mixed.contains("fade=t=in:st=2.0000:d=0.5000:alpha=1"))
        assertTrue(mixed, mixed.endsWith("[qkeep][qmix]overlay=format=auto:eof_action=pass:enable='between(t,2.0000,5.5000)'[out]"))
    }

    @Test
    fun `what moves loops on the look's own period`() {
        val glitch = graph(PictureLook.GLITCH).joinToString(";")
        assertTrue(glitch, glitch.contains("floor(mod((t-2.0000)*1.0000+0.0001,2.0000)/2.0000*20)"))
        val vhs = graph(PictureLook.VHS).joinToString(";")
        assertTrue(vhs, vhs.contains("y='(mod((t-2.0000)*1.0000+0.0001,4.0000)/4.0000)*(H+h)-h'"))
        val shake = graph(PictureLook.SHAKE).joinToString(";")
        assertTrue(shake, shake.contains("fps=30,perspective="))
        assertTrue(shake, shake.contains("mod((((in-1)/30-2.0000)*1.0000+0.0001),1.0000)"))
        assertTrue(shake, shake.contains("sense=source:eval=frame"))
    }

    @Test
    fun `speed runs the loop faster, amount does more`() {
        val fast = lookSpanGraph(LookSpan(PictureLook.GLITCH, 2_000, 5_500, speed = 2f, amount = 1.5f), "in", "out", "q", 1280, 720, 30).joinToString(";")
        assertTrue(fast, fast.contains("floor(mod((t-2.0000)*2.0000+0.0001,2.0000)/2.0000*20)"))
        assertTrue(fast, fast.contains("rgbashift=rh=-18:bh=18"))
        val pixels = lookSpanGraph(LookSpan(PictureLook.PIXELATE, 0, 1_000, amount = 2f), "in", "out", "q", 1280, 720, 30).joinToString(";")
        assertTrue(pixels, pixels.contains("pixelize=w=28:h=28"))
        // Out of bounds, kept within them.
        val wild = lookSpanGraph(LookSpan(PictureLook.STROBE, 0, 1_000, speed = 50f, amount = 9f), "in", "out", "q", 1280, 720, 30).joinToString(";")
        assertTrue(wild, wild.contains("*4.0000+0.0001") && wild.contains("brightness='0.9000*"))
    }

    @Test
    fun `the hash keeps its small numbers`() {
        // h(x) of the shaders and of FFmpeg: the same values in single precision.
        for (n in 0..200) {
            val double = (sin(n * 0.37 + 0.3) * 43.758).let { it - floor(it) }
            val single = (sin((n * 0.37f + 0.3f).toDouble()).toFloat() * 43.758f).let { it - floor(it) }
            assertTrue("h($n)", abs(double - single) < 1e-3 || abs(double - single) > 0.999)
        }
    }

    @Test
    fun `every shader reads its uniforms and has a look`() {
        for (look in PictureLook.entries) {
            val code = lookShader(look)
            for (name in listOf(LookShaderNames.CONTENT, LookShaderNames.SIZE, LookShaderNames.TIME, LookShaderNames.STRENGTH, LookShaderNames.HUE, LookShaderNames.AMOUNT)) {
                assertTrue("$look: $name", code.contains("uniform") && code.contains(name))
            }
            assertTrue("$look", code.contains("float3 look(float2 p)"))
            assertTrue("$look", code.contains("half4 main(float2 p)"))
            assertTrue("$look: ASCII", code.all { it.code < 128 })
            assertEquals("$look: braces", code.count { it == '{' }, code.count { it == '}' })
        }
    }

    @Test
    fun `looks are found by their names`() {
        for (look in PictureLook.entries) assertEquals(look, PictureLook.of(look.id))
        assertEquals(null, PictureLook.of("flames"))
        assertEquals(PictureLook.entries.size, PictureLook.entries.map { it.id }.toSet().size)
    }
}
