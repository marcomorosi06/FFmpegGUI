package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpokenSubtitlesTest {

    // -- Words from the recognizer ------------------------------------------------

    /** Real output of Parakeet TDT 0.6b v3 on the English sample of its archive. */
    private val englishTokens = listOf(
        " A", "sk", " not", " what", " your", " co", "un", "tr", "y", " can", " do", " for", " you", ",",
        " a", "sk", " what", " you", " can", " do", " for", " your", " co", "un", "tr", "y", ".",
    )
    private val englishStarts = floatArrayOf(
        0f, .24f, .4f, .64f, .8f, .96f, 1.04f, 1.12f, 1.2f, 1.28f, 1.44f, 1.6f, 1.68f, 1.92f,
        2.08f, 2.24f, 2.4f, 2.56f, 2.64f, 2.8f, 2.96f, 3.12f, 3.28f, 3.36f, 3.44f, 3.52f, 3.68f,
    )
    private val englishDurations = floatArrayOf(
        .24f, .16f, .24f, .16f, .16f, .08f, .08f, .08f, .08f, .16f, .16f, .08f, .24f, .16f,
        .16f, .16f, .16f, .08f, .16f, .16f, .16f, .16f, .08f, .08f, .08f, .16f, .08f,
    )

    @Test
    fun `pieces join into words and punctuation sticks to the word before`() {
        val words = RecognizedWords.fromTokens(englishTokens, englishStarts, englishDurations, offsetMs = 0, chunkEndMs = 3_845)
        assertEquals(
            listOf("Ask", "not", "what", "your", "country", "can", "do", "for", "you,", "ask", "what", "you", "can", "do", "for", "your", "country."),
            words.map { it.text },
        )
        // "country": from its first piece to the end of its last one.
        assertEquals(960L, words[4].startMs)
        assertEquals(1_280L, words[4].endMs)
        // The comma does not keep "you" on longer.
        assertEquals(1_920L, words[8].endMs)
    }

    @Test
    fun `chunk offset moves every word and nothing passes the chunk end`() {
        val words = RecognizedWords.fromTokens(listOf(" Hi", " there"), floatArrayOf(0.5f, 1.0f), null, offsetMs = 60_000, chunkEndMs = 61_200)
        assertEquals(60_500L, words[0].startMs)
        assertEquals(61_000L, words[0].endMs)
        assertEquals(61_000L, words[1].startMs)
        assertEquals(61_200L, words[1].endMs)
    }

    @Test
    fun `sentence piece spaces start words too`() {
        val words = RecognizedWords.fromTokens(listOf("\u2581Al", "les", "\u2581gut"), floatArrayOf(0f, .2f, .4f), null, 0, 1_000)
        assertEquals(listOf("Alles", "gut"), words.map { it.text })
    }

    @Test
    fun `quietest cut finds the silence`() {
        val samples = FloatArray(16_000) { if (it in 9_600 until 9_920) 0f else 0.5f }
        assertEquals(9_600, RecognizedWords.quietestCut(samples, 8_000, 16_000, 320))
        // Nothing to look at: the cut stays where it was asked.
        assertEquals(16_000, RecognizedWords.quietestCut(samples, 15_900, 16_000, 320))
    }

    // -- Layout -----------------------------------------------------------------------

    private fun words(vararg pairs: Pair<String, Long>): List<SpokenWord> =
        pairs.map { (text, start) -> SpokenWord(start, start + 250, text) }

    @Test
    fun `a sentence end and a pause start a new block`() {
        val spoken = words("Hello" to 0, "there." to 300, "How" to 700, "are" to 950, "you" to 3_000)
        val blocks = SubtitleLayout.ofWords(spoken, SubtitleStyle(), 16f / 9f)
        assertEquals(listOf("Hello there.", "How are", "you"), blocks.map { it.text })
        // A block ends a little after its last word, never after the next one starts.
        assertEquals(700L, blocks[0].endMs)
        assertEquals(950L + 250 + 300, blocks[1].endMs)
    }

    @Test
    fun `lines never leave the frame and two lines are balanced`() {
        val style = SubtitleStyle(size = 0.08f, maxLines = 2)
        val measure = SubtitleLayout.Measure(style, 9f / 16f, null)
        val text = "the quick brown fox jumps over the lazy dog and runs away"
        val spoken = text.split(' ').mapIndexed { i, w -> SpokenWord(i * 200L, i * 200L + 180, w) }
        val blocks = SubtitleLayout.ofWords(spoken, style, 9f / 16f)
        for (block in blocks) {
            assertTrue(block.lines.size <= 2)
            for (line in block.lines) assertTrue(measure.width(line) <= measure.maxWidth)
        }
        assertEquals(text, blocks.joinToString(" ") { it.text.replace('\n', ' ') })
        val two = blocks.first { it.lines.size == 2 }
        val widths = two.lines.map { measure.width(it) }
        assertTrue(kotlin.math.abs(widths[0] - widths[1]) < measure.maxWidth * 0.35f)
    }

    @Test
    fun `a wider font breaks sooner`() {
        val file = File("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
        if (!file.isFile) return
        val font = FontFacts.read(file)!!
        val style = SubtitleStyle(size = 0.075f, uppercase = true)
        val measure = SubtitleLayout.Measure(style, 9f / 16f, font)
        val spoken = "ask not what your country can do for you".split(' ').mapIndexed { i, w -> SpokenWord(i * 150L, i * 150L + 140, w) }
        val blocks = SubtitleLayout.ofWords(spoken, style, 9f / 16f, font)
        for (block in blocks) for (line in block.lines) assertTrue(measure.width(line) <= measure.maxWidth)
        // DejaVu Sans Bold: "ASK WHAT YOU" is 8.75 em.
        assertEquals(8.7476f, font.widthEm("ASK WHAT YOU"), 0.001f)
    }

    @Test
    fun `a word wider than the frame shrinks its block`() {
        val style = SubtitleStyle(size = 0.15f)
        val blocks = SubtitleLayout.ofWords(listOf(SpokenWord(0, 500, "Donaudampfschiff")), style, 9f / 16f)
        val block = blocks.single()
        assertTrue(block.scale < 1f)
        val measure = SubtitleLayout.Measure(style, 9f / 16f, null)
        assertEquals(measure.maxWidth, measure.width(block.lines.single()) * block.scale, 0.001f)
        val script = AssSubtitles.write(blocks, style, 9f / 16f, "Roboto")
        assertTrue(script, script.lines().single { it.startsWith("Dialogue:") }.contains("\\fs"))
    }

    @Test
    fun `one line at a time`() {
        val spoken = "one two three four five six seven eight nine ten".split(' ').mapIndexed { i, w -> SpokenWord(i * 200L, i * 200L + 150, w) }
        val blocks = SubtitleLayout.ofWords(spoken, SubtitleStyle(size = 0.1f, maxLines = 1), 9f / 16f)
        assertTrue(blocks.size > 1)
        assertTrue(blocks.all { it.lines.size == 1 })
    }

    @Test
    fun `uppercase is applied to what is shown`() {
        val blocks = SubtitleLayout.ofWords(words("ciao" to 0), SubtitleStyle(uppercase = true), 1f)
        assertEquals("CIAO", blocks.single().text)
    }

    @Test
    fun `highlight follows the word being said`() {
        val block = SubtitleLayout.ofWords(words("a" to 0, "b" to 300, "c" to 600), SubtitleStyle(), 1f).single()
        assertEquals(IntRange.EMPTY, SubtitleLayout.highlighted(block, -1, WordHighlight.WORD))
        assertEquals(1..1, SubtitleLayout.highlighted(block, 400, WordHighlight.WORD))
        assertEquals(0..1, SubtitleLayout.highlighted(block, 400, WordHighlight.SPOKEN))
        assertEquals(IntRange.EMPTY, SubtitleLayout.highlighted(block, 400, WordHighlight.NONE))
        assertEquals(listOf(0L, 300L, 600L, 1_150L), SubtitleLayout.highlightSteps(block, WordHighlight.WORD))
    }

    @Test
    fun `file lines keep their breaks and timing, long ones are broken again`() {
        val cues = listOf(SubtitleCue(1_000, 2_000, "short\nthis line is far too long to fit on a narrow portrait frame"))
        val blocks = SubtitleLayout.ofCues(cues, SubtitleStyle(size = 0.07f), 9f / 16f)
        val block = blocks.single()
        assertFalse(block.timed)
        assertEquals("short", block.lines.first().single().text)
        assertTrue(block.lines.size > 2)
        assertEquals(1_000L, block.startMs)
    }

    @Test
    fun `block lookup`() {
        val blocks = SubtitleLayout.ofWords(words("Hi." to 0, "Bye." to 2_000), SubtitleStyle(), 1f)
        assertEquals("Hi.", SubtitleLayout.blockAt(blocks, 100)?.text)
        assertNull(SubtitleLayout.blockAt(blocks, 1_500))
        assertEquals("Bye.", SubtitleLayout.blockAt(blocks, 2_100)?.text)
    }

    // -- Following the edit -------------------------------------------------------------

    @Test
    fun `words follow their footage and vanish with what is cut`() {
        val spoken = listOf(SourceWords(0, listOf(SpokenWord(500, 800, "kept"), SpokenWord(2_500, 2_800, "cut"), SpokenWord(5_200, 5_400, "later"))))
        val segments = listOf(Segment(source = 0, startMs = 0, endMs = 2_000), Segment(source = 0, startMs = 5_000, endMs = 6_000))
        val moved = spoken.onTimeline(segments)
        assertEquals(listOf("kept", "later"), moved.map { it.text })
        assertEquals(2_200L, moved[1].startMs)
    }

    // -- Script ---------------------------------------------------------------------------

    @Test
    fun `script places the text and colours the word being said`() {
        val blocks = SubtitleLayout.ofWords(words("Hello" to 0, "world" to 300), SubtitleStyle(), 9f / 16f)
        val script = AssSubtitles.write(blocks, SubtitleStyle(x = 0.5f, y = 0.8f), 9f / 16f, "Roboto")
        assertTrue(script, script.contains("PlayResX: 608\nPlayResY: 1080"))
        assertTrue(script, script.contains("Style: Default,Roboto,64.8,&H00FFFFFF,&H000AD6FF,&H00000000,&H33000000,-1,0,0,0,100,100,0,0,1,4.54,0,2,0,0,0,1"))
        val events = script.lines().filter { it.startsWith("Dialogue:") }
        assertEquals(2, events.size)
        assertTrue(events[0], events[0].startsWith("Dialogue: 0,0:00:00.00,0:00:00.30,Default,,0,0,0,,{\\an2\\pos(304,864)}"))
        assertTrue(events[0], events[0].endsWith("{\\c&H0AD6FF&}Hello{\\c&HFFFFFF&} world"))
        assertTrue(events[1], events[1].endsWith("Hello {\\c&H0AD6FF&}world{\\c&HFFFFFF&}"))
    }

    @Test
    fun `box look and escaped text`() {
        val blocks = SubtitleLayout.ofCues(listOf(SubtitleCue(0, 1_000, "a {b} c\\N")), SubtitleStyle(), 1f)
        val script = AssSubtitles.write(blocks, SubtitleStyle(look = SubtitleLook.BOX), 1f, "Roboto")
        assertTrue(script.contains(",&H4C000000,&H33000000,-1,0,0,0,100,100,0,0,3,"))
        val event = script.lines().single { it.startsWith("Dialogue:") }
        assertTrue(event, event.endsWith("a (b) c\\\u200BN"))
    }

    // -- Request ----------------------------------------------------------------------------

    @Test
    fun `frame of the subtitles follows the first file, its turn, crop and the canvas`() {
        val portrait = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "a.mp4",
            sources = listOf(SourceInfo(width = 1080, height = 1920)),
        )
        assertEquals(1080f / 1920f, portrait.subtitleFrameRatio, 0.001f)
        val turned = portrait.copy(effects = Effects(rotation = Rotation.DEG_90))
        assertEquals(1920f / 1080f, turned.subtitleFrameRatio, 0.001f)
        val cropped = portrait.copy(effects = Effects(crop = CropRect(0f, 0f, 1f, 0.5f)))
        assertEquals(1080f / 960f, cropped.subtitleFrameRatio, 0.001f)
        assertEquals(1f, portrait.copy(canvas = CanvasRatio.SQUARE).subtitleFrameRatio, 0.001f)
    }

    @Test
    fun `spoken subtitles need a style and a word`() {
        val base = ConversionRequest(format = OutputFormat.MP4, inputFileName = "a.mp4")
        assertFalse(base.copy(spokenWords = listOf(SourceWords(0, listOf(SpokenWord(0, 1, "x"))))).burnsSubtitles)
        assertFalse(base.copy(subtitleStyle = SubtitleStyle(), spokenWords = listOf(SourceWords(0, emptyList()))).burnsSubtitles)
        assertTrue(base.copy(subtitleStyle = SubtitleStyle(), spokenWords = listOf(SourceWords(0, listOf(SpokenWord(0, 1, "x"))))).burnsSubtitles)
    }

    // -- Transcript -------------------------------------------------------------------------

    @Test
    fun `transcript paragraphs start at pauses, with their time`() {
        val text = TranscriptText.write(
            listOf(SpokenWord(0, 300, "Ciao."), SpokenWord(400, 700, "Come"), SpokenWord(800, 900, "va?"), SpokenWord(65_000, 65_400, "Bene.")),
        )
        assertEquals("[0:00] Ciao. Come va?\n\n[1:05] Bene.\n", text)
        assertEquals("[0:32] Bene.\n", TranscriptText.write(listOf(SpokenWord(65_000, 65_400, "Bene.")), speed = 2f))
    }

    // -- Projects ---------------------------------------------------------------------------

    @Test
    fun `words and style survive a project file`() {
        val words = listOf(SpokenWord(0, 300, "Ciao,"), SpokenWord(350, 900, "mondo!"))
        val json = com.cuscus.ffmpeggui.core.project.JsonText.write(com.cuscus.ffmpeggui.core.project.ModelJson.writeWords(words))
        val read = com.cuscus.ffmpeggui.core.project.JsonText.parse(json) as com.cuscus.ffmpeggui.core.project.Json.Obj
        assertEquals(words, com.cuscus.ffmpeggui.core.project.ModelJson.spokenWords(read))
        val style = SubtitleStyle(size = 0.08f, x = 0.3f, look = SubtitleLook.BOX, highlight = WordHighlight.SPOKEN, maxLines = 1, uppercase = true)
        assertEquals(style, com.cuscus.ffmpeggui.core.project.ModelJson.subtitleStyle(com.cuscus.ffmpeggui.core.project.ModelJson.write(style)))
    }

    // -- Fonts ------------------------------------------------------------------------------

    @Test
    fun `font facts of a system font`() {
        val file = File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        if (!file.isFile) return // only where the font is installed
        val facts = FontFacts.read(file)!!
        assertEquals("DejaVu Sans", facts.family)
        assertEquals(2048, facts.unitsPerEm)
        assertEquals(1901, facts.winAscent)
        assertEquals(483, facts.winDescent)
    }
}
