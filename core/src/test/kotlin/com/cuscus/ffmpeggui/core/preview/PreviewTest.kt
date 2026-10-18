package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.MediaPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTest {

    // -- Timeline --------------------------------------------------------------

    @Test
    fun `single clip at double speed`() {
        val timeline = PreviewTimeline.ofLengths(listOf(10_000), speed = 2f)
        assertEquals(5_000L, timeline.outputDurationMs)
        assertEquals(2_000L, timeline.outputMs(4_000))
        assertEquals(4_000L, timeline.mediaMsForOutput(2_000))
        assertEquals(4_000L, timeline.timelineMs(4_000))
    }

    @Test
    fun `reversed clip runs the output clock backwards through the media`() {
        val timeline = PreviewTimeline.ofLengths(listOf(10_000), speed = 2f, reversed = true)
        assertEquals(0L, timeline.outputMs(10_000))
        assertEquals(5_000L, timeline.outputMs(0))
        assertEquals(3_000L, timeline.timelineMs(7_000))
        assertEquals(7_000L, timeline.mediaMsForOutput(1_500))
    }

    @Test
    fun `positions map across several clips`() {
        val timeline = PreviewTimeline.ofLengths(listOf(4_000, 0, 6_000))
        assertEquals(10_000L, timeline.mediaDurationMs)
        assertEquals(MediaPoint(0, 3_999), timeline.locate(3_999))
        // A boundary belongs to the next non-empty clip.
        assertEquals(MediaPoint(2, 0), timeline.locate(4_000))
        assertEquals(MediaPoint(2, 6_000), timeline.locate(99_000))
        assertEquals(7_000L, timeline.mediaMs(MediaPoint(2, 3_000)))
        assertEquals(4_000L, timeline.mediaMs(MediaPoint(1, 500)))
    }

    @Test
    fun `degenerate timelines do not crash`() {
        val empty = PreviewTimeline.ofLengths(emptyList(), speed = 0f)
        assertEquals(0L, empty.outputDurationMs)
        assertEquals(MediaPoint(0, 0), empty.locate(500))
        assertEquals(0L, empty.mediaMsForOutput(500))
        assertEquals(1f, empty.speed, 0f)
    }

    // -- Fades -------------------------------------------------------------------

    @Test
    fun `fade in and out ramp linearly`() {
        assertEquals(0f, fadeLevel(0, 10_000, 2f, 2f), 0.001f)
        assertEquals(0.5f, fadeLevel(1_000, 10_000, 2f, 2f), 0.001f)
        assertEquals(1f, fadeLevel(5_000, 10_000, 2f, 2f), 0.001f)
        assertEquals(0.5f, fadeLevel(9_000, 10_000, 2f, 2f), 0.001f)
        assertEquals(0f, fadeLevel(10_000, 10_000, 2f, 2f), 0.001f)
    }

    @Test
    fun `fade out is skipped when it does not fit, like the export`() {
        assertEquals(1f, fadeLevel(900, 1_000, 0f, 2f), 0.001f)
        assertEquals(1f, fadeLevel(900, 0, 0f, 2f), 0.001f)
        assertEquals(1f, fadeLevel(0, 10_000, 0f, 0f), 0.001f)
    }

    // -- Colour ------------------------------------------------------------------

    @Test
    fun `neutral eq is the identity matrix`() {
        assertTrue(isNeutralEq(0f, 1f, 1f))
        assertFalse(isNeutralEq(0.1f, 1f, 1f))
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
            eqColorMatrix(0f, 1f, 1f),
            0.0001f,
        )
    }

    @Test
    fun `zero saturation maps every colour to its luma`() {
        val m = eqColorMatrix(0f, 1f, 0f)
        // All three rows are the luma weights, so R = G = B for any input.
        assertArrayEquals(m.copyOfRange(0, 3), m.copyOfRange(5, 8), 0.0001f)
        assertArrayEquals(m.copyOfRange(0, 3), m.copyOfRange(10, 13), 0.0001f)
        assertEquals(1f, m[0] + m[1] + m[2], 0.0001f)
    }

    @Test
    fun `brightness shifts every channel by the same amount`() {
        val m = eqColorMatrix(0.1f, 1f, 1f)
        // 0.1 of the video range, stretched to full range: 0.1 * 255/219 * 255.
        assertEquals(29.69f, m[4], 0.01f)
        assertEquals(m[4], m[9], 0f)
        assertEquals(m[4], m[14], 0f)
    }

    // -- Audio -------------------------------------------------------------------

    @Test
    fun `volume and decibels convert both ways`() {
        assertEquals(6.02f, volumeToDecibels(2f), 0.01f)
        assertEquals(0f, volumeToDecibels(1f), 0.001f)
        assertEquals(0.5f, decibelsToVolume(-6.0206f), 0.001f)
        assertEquals(0f, decibelsToVolume(volumeToDecibels(0f)), 0f)
    }

    @Test
    fun `normalisation gain targets the loudnorm level and is bounded`() {
        assertEquals(-2.2f, normalizationGainDb(-21.8), 0.001f)
        assertEquals(6f, normalizationGainDb(-30.0), 0.001f)
        assertEquals(30f, normalizationGainDb(-70.0), 0.001f)
    }

    @Test
    fun `integrated loudness is read from the ebur128 summary`() {
        val log = """
            [Parsed_ebur128_0 @ 0x5641] Summary:

              Integrated loudness:
                I:         -21.8 LUFS
                Threshold: -31.8 LUFS

              Loudness range:
                LRA:         0.0 LU
        """.trimIndent()
        assertEquals(-21.8, parseIntegratedLoudness(log)!!, 0.0001)
        assertNull(parseIntegratedLoudness("Output file does not contain any stream"))
        assertEquals(listOf("-nostats", "-i", "in.mp4"), loudnessAnalysisCommand("in.mp4").arguments.take(3))
    }

    // -- Subtitles ---------------------------------------------------------------

    private val parser = SubtitleParser()

    @Test
    fun `srt cues are parsed with markup removed`() {
        val cues = parser.parse(
            "\uFEFF1\r\n00:00:00,500 --> 00:00:03,000\r\n<i>Hello</i> there\r\nsecond line\r\n\r\n" +
                "2\n00:01:03.5 --> 00:01:05.250 X1:10\n{\\an8}Top text\n",
        )
        assertEquals(
            listOf(
                SubtitleCue(500, 3_000, "Hello there\nsecond line"),
                SubtitleCue(63_500, 65_250, "Top text"),
            ),
            cues,
        )
    }

    @Test
    fun `ass dialogue lines are parsed using the format header`() {
        val cues = parser.parse(
            """
            [Script Info]
            Title: demo

            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,20

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Comment: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,ignored
            Dialogue: 0,0:00:01.50,0:00:04.00,Default,,0,0,0,,{\b1}Bold{\b0}, with a comma\Nand a break
            Dialogue: 0,1:00:00.00,1:00:02.05,Default,,0,0,0,,Late
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                SubtitleCue(1_500, 4_000, "Bold, with a comma\nand a break"),
                SubtitleCue(3_600_000, 3_602_050, "Late"),
            ),
            cues,
        )
    }

    @Test
    fun `broken input yields no cues`() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("not a subtitle file\n--> nope").isEmpty())
        assertTrue(parser.parse("1\n00:00:05,000 --> 00:00:01,000\nends before it starts\n").isEmpty())
    }

    @Test
    fun `active text is looked up by time`() {
        val cues = listOf(SubtitleCue(0, 2_000, "a"), SubtitleCue(1_000, 3_000, "b"))
        assertEquals("a", cues.textAt(500))
        assertEquals("a\nb", cues.textAt(1_500))
        assertEquals("b", cues.textAt(2_000))
        assertNull(cues.textAt(3_000))
    }

    // -- Subtitles following the edit ----------------------------------------------

    private val cuts = listOf(Segment(0, 4_000, 6_000), Segment(1, 0, 3_000), Segment(0, 0, 5_000))

    @Test
    fun `a stretch of footage maps to every segment that shows it`() {
        // Source 0, 4.5-5.5 s: inside the first cut, and again at the end of the third.
        assertEquals(listOf(500L to 1_500L, 9_500L to 10_000L), mapToTimeline(4_500, 5_500, cuts, sourceIndex = 0))
        assertTrue(mapToTimeline(4_500, 5_500, cuts, sourceIndex = 2).isEmpty())
        assertTrue(mapToTimeline(7_000, 8_000, cuts, sourceIndex = 0).isEmpty())
    }

    @Test
    fun `reversal mirrors the mapped ranges`() {
        // The sequence is 10 s long, so 0.5-1.5 s becomes 8.5-9.5 s.
        assertEquals(listOf(0L to 500L, 8_500L to 9_500L), mapToTimeline(4_500, 5_500, cuts, sourceIndex = 0, reversed = true))
    }

    @Test
    fun `cues follow the footage`() {
        val cues = listOf(SubtitleCue(4_500, 5_500, "hi"), SubtitleCue(8_000, 9_000, "cut away"))
        assertEquals(
            listOf(SubtitleCue(500, 1_500, "hi"), SubtitleCue(9_500, 10_000, "hi")),
            cues.onTimeline(cuts, sourceIndex = 0),
        )
    }

    @Test
    fun `srt files are rewritten on the edited clock`() {
        val retimed = SubtitleRetimer().retime(
            "1\n00:00:04,500 --> 00:00:05,500\nhi\n\n2\n00:00:08,000 --> 00:00:09,000\ngone\n",
            cuts,
            sourceIndex = 0,
        )!!
        assertEquals("srt", retimed.extension)
        assertEquals(
            "1\n00:00:00,500 --> 00:00:01,500\nhi\n\n2\n00:00:09,500 --> 00:00:10,000\nhi\n\n",
            retimed.content,
        )
    }

    @Test
    fun `ass files keep their styles and only move the dialogue lines`() {
        val source = listOf(
            "[Script Info]",
            "[V4+ Styles]",
            "Style: Default,Arial,20",
            "[Events]",
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
            "Dialogue: 0,0:00:04.50,0:00:05.50,Default,,0,0,0,,{\\b1}hi, there",
            "Dialogue: 0,0:00:08.00,0:00:09.00,Default,,0,0,0,,gone",
        ).joinToString("\n")
        val retimed = SubtitleRetimer().retime(source, cuts, sourceIndex = 0)!!
        assertEquals("ass", retimed.extension)
        assertEquals(
            listOf(
                "[Script Info]",
                "[V4+ Styles]",
                "Style: Default,Arial,20",
                "[Events]",
                "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
                "Dialogue: 0,0:00:00.50,0:00:01.50,Default,,0,0,0,,{\\b1}hi, there",
                "Dialogue: 0,0:00:09.50,0:00:10.00,Default,,0,0,0,,{\\b1}hi, there",
                "",
            ).joinToString("\n"),
            retimed.content,
        )
    }

    @Test
    fun `nothing is returned when no line survives the edit`() {
        assertNull(SubtitleRetimer().retime("1\n00:00:08,000 --> 00:00:09,000\ngone\n", cuts, sourceIndex = 0))
        assertNull(SubtitleRetimer().retime("", cuts, sourceIndex = 0))
    }
}
