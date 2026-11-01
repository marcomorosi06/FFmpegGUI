package com.cuscus.ffmpeggui.core.transition

import com.cuscus.ffmpeggui.core.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transitions told as layers, exported with FFmpeg's own filters (see
 * nativeTransitionJoin): a graph whose every label is made once and used once,
 * that cuts and joins the clips where the transition lies.
 */
class TransitionGraphTest {

    private fun join(kind: TransitionKind, start: Int = 42, frames: Int = 18): List<String>? =
        nativeTransitionJoin(kind, "[a]", "[b]", start, frames, 1280, 720, 30, "q", "[out]")

    private val custom = TransitionKind.entries.filter { it.isCustom }

    @Test
    fun `every custom kind is drawn`() {
        assertTrue(custom.size > 40)
        for (kind in custom) assertTrue("$kind", join(kind) != null)
    }

    @Test
    fun `each label is made once and used once`() {
        val label = Regex("""\[[A-Za-z0-9_]+]""")
        for (kind in custom) {
            val lines = join(kind)!!
            val made = HashMap<String, Int>()
            val used = HashMap<String, Int>()
            for (line in lines) {
                // Labels before the first filter are inputs, after the last one outputs.
                val inputs = Regex("""^(\[[A-Za-z0-9_]+])+""").find(line)?.value.orEmpty()
                val outputs = Regex("""(\[[A-Za-z0-9_]+])+$""").find(line)?.value.orEmpty()
                label.findAll(inputs).forEach { used.merge(it.value, 1, Int::plus) }
                label.findAll(outputs).forEach { made.merge(it.value, 1, Int::plus) }
            }
            assertEquals("$kind: the two clips are read once each", 1, used["[a]"])
            assertEquals("$kind", 1, used["[b]"])
            assertEquals("$kind: one result", 1, made["[out]"])
            for ((name, count) in made) {
                assertEquals("$kind: $name made", 1, count)
                if (name != "[out]") assertEquals("$kind: $name used", 1, used[name])
            }
            for (name in used.keys) assertTrue("$kind: $name used but never made", name in made || name == "[a]" || name == "[b]")
        }
    }

    @Test
    fun `the region is cut out of both clips and joined back on one clock`() {
        val lines = join(TransitionKind.CUBE_LEFT)!!
        val graph = lines.joinToString(";")
        assertTrue(graph, graph.contains("trim=end_frame=42,setpts=PTS-STARTPTS"))
        assertTrue(graph, graph.contains("trim=start_frame=42:end_frame=60,setpts=PTS-STARTPTS"))
        assertTrue(graph, graph.contains("trim=end_frame=18,setpts=PTS-STARTPTS"))
        assertTrue(graph, graph.contains("trim=start_frame=18,setpts=PTS-STARTPTS"))
        assertTrue(graph, lines.last().endsWith("concat=n=3:v=1:a=0,settb=1/30[out]"))
        // A cube is drawn by perspective, its faces transparent around.
        assertTrue(graph, graph.contains("perspective="))
        assertTrue(graph, graph.contains("sense=destination:eval=frame"))
    }

    @Test
    fun `quotes enclose whole expressions only`() {
        for (kind in custom) {
            for (line in join(kind)!!) {
                assertEquals("$kind: $line", 0, line.count { it == '\'' } % 2)
                assertTrue("$kind: ASCII", line.all { it.code < 128 })
                // Nothing pixel by pixel at full size: geq only works on small masks and maps.
                Regex("""s=(\d+)x(\d+)[^;]*geq""").findAll(line).forEach { assertTrue("$kind: ${it.value}", it.groupValues[1].toInt() <= 256) }
            }
        }
    }

    @Test
    fun `strengths follow the frames through sendcmd`() {
        val graph = join(TransitionKind.DEFOCUS)!!.joinToString(";")
        assertTrue(graph, graph.contains("sendcmd=c='"))
        // One list for each clip's blur, each in order of time and within the region.
        val lists = graph.split("sendcmd=c='").drop(1).map { it.substringBefore("'") }
        assertEquals(2, lists.size)
        for (list in lists) {
            val times = Regex("""(\d+\.\d{6}) gblur@""").findAll(list).map { it.groupValues[1].toDouble() }.toList()
            assertTrue(times.isNotEmpty())
            assertEquals(times.sorted(), times)
            assertTrue(times.last() < 18.0 / 30.0)
        }
    }

    @Test
    fun `nothing is drawn where there is no room`() {
        assertNull(join(TransitionKind.CUBE_LEFT, start = 0))
        assertNull(join(TransitionKind.CUBE_LEFT, frames = 1))
        assertNull(join(TransitionKind.FADE))
    }
}
