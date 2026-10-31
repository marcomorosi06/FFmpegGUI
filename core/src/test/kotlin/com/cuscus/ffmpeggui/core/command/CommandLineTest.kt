package com.cuscus.ffmpeggui.core.command

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandLineTest {

    @Test
    fun `splits on whitespace`() {
        assertEquals(listOf("-i", "in.mp4", "out.mkv"), tokenizeCommandLine("  -i in.mp4\t out.mkv \n"))
    }

    @Test
    fun `double quotes keep spaces and single quotes`() {
        assertEquals(
            listOf("-vf", "drawtext=text='hello world'"),
            tokenizeCommandLine("""-vf "drawtext=text='hello world'""""),
        )
    }

    @Test
    fun `single quotes are literal`() {
        assertEquals(listOf("a b\\c", "d"), tokenizeCommandLine("""'a b\c' d"""))
    }

    @Test
    fun `backslash only escapes special characters inside double quotes`() {
        assertEquals(listOf("""a\:b"c\d"""), tokenizeCommandLine(""""a\:b\"c\\d""""))
    }

    @Test
    fun `empty quoted argument is preserved`() {
        assertEquals(listOf("-metadata", ""), tokenizeCommandLine("-metadata \"\""))
    }

    @Test
    fun `plain arguments are not quoted`() {
        assertEquals("-crf 23 out_%03d.mp4", renderCommandLine(listOf("-crf", "23", "out_%03d.mp4")))
    }

    @Test
    fun `rendering and tokenizing round trip`() {
        val tricky = listOf(
            "-vf",
            """drawtext=fontfile='/system/fonts/Roboto-Bold.ttf':text='it'\''s 100\\\% \: "ok"'""",
            "[0:v]null[vmain];[1:v]scale=384:-2[ov0]",
            "",
            "title=Price is \$5 `now`",
            "trailing\\",
            "a b",
        )
        assertEquals(tricky, tokenizeCommandLine(renderCommandLine(tricky)))
    }

    @Test
    fun `a backslash before a line break joins the lines`() {
        assertEquals(listOf("-i", "in.mp4", "-c", "copy", "out.mkv"), tokenizeCommandLine("-i in.mp4 \\\n  -c copy \\\n  out.mkv"))
        assertEquals(listOf("ab"), tokenizeCommandLine("a\\\nb"))
    }

    @Test
    fun `spans say where each argument is`() {
        val line = "-vf \"scale=1:2\" 'open"
        val spans = spanCommandLine(line)
        assertEquals(listOf(0 to 3, 4 to 15, 16 to 21), spans.map { it.start to it.end })
        assertEquals("scale=1:2", spans[1].value)
        assertEquals(null, spans[1].openQuote)
        assertEquals('\'', spans[2].openQuote)
    }
}
