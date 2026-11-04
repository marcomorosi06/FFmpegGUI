package com.cuscus.ffmpeggui.core.command.studio

import com.cuscus.ffmpeggui.core.command.AliasKind
import com.cuscus.ffmpeggui.core.command.CommandAlias
import com.cuscus.ffmpeggui.core.command.CommandFile
import com.cuscus.ffmpeggui.core.command.tokenizeCommandLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandStudioTest {

    private val full = """
Hyper fast Audio and Video encoder
usage: ffmpeg [options] [[infile options] -i infile]... {[outfile options] outfile}...

Main options:
-y                  overwrite output files
-c[:<stream_spec>] <codec>  select encoder/decoder ('copy' to copy stream without reencoding)
-vf filter_graph    set video filters
-af filter_graph    set audio filters
-filter_complex graph_description  create a complex filtergraph
-map [-]input_file_id[:stream_specifier]  set input stream mapping
-i url              input file
-an                 disable audio
-pix_fmt[:<stream_spec>] <format>  set pixel format
-f fmt              force container format

AVCodecContext AVOptions:
  -b                 <int64>      E..VA...... set bitrate (in bits/s) (from 0 to INT64_MAX) (default 200000)

libx264 AVOptions:
  -preset            <string>     E..V....... Set the encoding preset (cf. x264 --fullhelp) (default "medium")
  -crf               <float>      E..V....... Select the quality for constant quality mode (from -1 to 51) (default -1)
  -aq-mode           <int>        E..V....... AQ method (from -1 to INT_MAX) (default -1)
     none            0            E..V.......
     variance        1            E..V....... Variance AQ (complexity mask)

mov/mp4/tgp/psp/tg2/ipod/ismv/f4v muxer AVOptions:
  -movflags          <flags>      E.......... MOV muxer flags (default 0)
     rtphint                      E.......... Add RTP hint tracks
     faststart                    E.......... Run a second pass to put the index (moov atom) at the beginning of the file
"""

    private val filters = """
Filters:
  T.. = Timeline support
  .S. = Slice threading
  A = Audio input/output
  V = Video input/output
  N = Dynamic number and/or type of input/output
  | = Source or sink filter
 ... aformat           A->A       Convert the input audio to one of the specified formats.
 T.. atempo            A->A       Adjust audio tempo.
 ..C scale             V->V       Scale the input video size and/or convert the image format.
 T.C crop              V->V       Crop the input video.
 TSC overlay           VV->V      Overlay a video source on top of the input.
 ... split             V->N       Pass on the input to N video outputs.
 ... amix              N->A       Audio mixing.
"""

    private val encoders = """
Encoders:
 V..... = Video
 A..... = Audio
 S..... = Subtitle
 ------
 V....D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)
 V....D png                  PNG (Portable Network Graphics) image
 A....D aac                  AAC (Advanced Audio Coding)
 A....D libmp3lame           libmp3lame MP3 (MPEG audio layer 3) (codec mp3)
 S..... mov_text             3GPP Timed Text subtitle
"""

    private val muxers = """
File formats:
 D. = Demuxing supported
 .E = Muxing supported
 --
  E mp4             MP4 (MPEG-4 Part 14)
  E matroska        Matroska
 DE gif             CompuServe Graphics Interchange Format (GIF)
"""

    private val pixFmts = """
Pixel formats:
I.... = Supported Input  format for conversion
.O... = Supported Output format for conversion
-----
IO... yuv420p                3            12      8-8-8
IO... rgb24                  3            24      8-8-8
I.... bayer_bggr8            3             8      2-4-2
"""

    private val scaleHelp = """
Filter scale
  Scale the input video size and/or convert the image format.
    Inputs:
       #0: default (video)
    Outputs:
       #0: default (video)
scale AVOptions:
  w                 <string>     ..FV.....T. Output video width
  h                 <string>     ..FV.....T. Output video height
  flags             <string>     ..FV....... Flags to pass to libswscale (default "")
  eval              <int>        ..FV....... specify when to evaluate expressions (from 0 to 1) (default init)
     init            0            ..FV....... eval expressions once during initialization
     frame           1            ..FV....... eval expressions during initialization and per-frame
"""

    private val catalog = FfmpegHelpParser.catalog(full, filters, encoders, muxers, pixFmts)
        .let { it.withFilterHelp("scale", FfmpegHelpParser.parseTopic(scaleHelp)) }

    private val files = listOf(
        CommandAlias("input.mp4", AliasKind.INPUT),
        CommandAlias("overlay_0.png", AliasKind.OVERLAY),
        CommandAlias("logo.png", AliasKind.EXTRA),
        CommandAlias("output.mp4", AliasKind.OUTPUT),
    )
    private val assist = CommandAssist(catalog, files)
    private val lint = CommandLint(catalog, files)

    /** The suggestions where "|" is in [marked]. */
    private fun at(marked: String): Pair<String, Assist> {
        val cursor = marked.indexOf('|')
        val text = marked.removeRange(cursor, cursor + 1)
        return text to assist.at(text, cursor)
    }

    @Test
    fun `FFmpeg's help is read into options, values, filters, encoders, formats`() {
        val c = catalog.option("-c:v")!!
        assertTrue(c.perStream)
        assertTrue(c.takesValue)
        assertFalse(catalog.option("-y")!!.takesValue)
        assertFalse(catalog.option("an")!!.takesValue)
        val crf = catalog.allNamed("crf").first { it.section == "libx264 AVOptions" }
        assertEquals("-1 .. 51", crf.range)
        assertEquals("-1", crf.default)
        assertEquals(listOf("none", "variance"), catalog.valuesOf("aq-mode").map { it.name })
        assertEquals("<flags>", catalog.option("movflags")!!.argument)
        assertEquals(listOf("aformat", "atempo", "scale", "crop", "overlay", "split", "amix"), catalog.filters.map { it.name })
        assertTrue(catalog.filter("overlay")!!.video)
        assertTrue(catalog.filter("amix")!!.audio)
        assertEquals('A', catalog.encoder("aac")!!.type)
        assertEquals(5, catalog.encoders.size)
        assertEquals(listOf("mp4", "matroska", "gif"), catalog.muxers.map { it.name })
        assertEquals(listOf("yuv420p", "rgb24"), catalog.pixelFormats)
        val scale = catalog.filterHelp["scale"]!!
        assertEquals("Filter scale", scale.summary.first())
        assertEquals(listOf("w", "h", "flags", "eval"), scale.options.map { it.name })
        assertEquals(listOf("init", "frame"), scale.options.last().values.map { it.name })
        assertTrue(catalog.isComplete)
        assertFalse(FfmpegCatalog.BUILT_IN.isComplete)
    }

    @Test
    fun `options are offered as they are typed, the everyday ones first`() {
        val (_, a) = at("ffmpeg -i input.mp4 -c|")
        val labels = a.suggestions.map { it.label }
        assertEquals("-c:v", labels[0])
        assertEquals("-c:a", labels[1])
        assertTrue("-crf" in labels)
        val (_, b) = at("ffmpeg -pre|")
        assertEquals("-preset", b.suggestions.first().label)
        assertTrue(b.suggestions.first().detail.startsWith("string"))
        val (_, spec) = at("ffmpeg -b:|")
        assertEquals(listOf("-b:v", "-b:a", "-b:s"), spec.suggestions.take(3).map { it.label })
    }

    @Test
    fun `a suggestion is only taken when asked, and replaces exactly its word`() {
        val (text, a) = at("ffmpeg -i input.mp4 -c:v lib|x output.mp4")
        val x264 = a.suggestions.first { it.label == "libx264" }
        assertEquals(DocTopic.Option("-c:v"), a.topic)
        val (out, cursor) = applySuggestion(text, x264)
        assertEquals("ffmpeg -i input.mp4 -c:v libx264 output.mp4", out)
        assertEquals("ffmpeg -i input.mp4 -c:v libx264 ".length, cursor)
        // Video encoders for -c:v, audio ones for -c:a, and "copy" for both.
        assertFalse(a.suggestions.any { it.label == "aac" })
        val (_, audio) = at("ffmpeg -c:a |")
        assertEquals(listOf("copy", "aac", "libmp3lame"), audio.suggestions.map { it.label })
    }

    @Test
    fun `values come from the option's own list, flags with a plus`() {
        val (_, a) = at("ffmpeg -i input.mp4 -movflags |")
        assertEquals(listOf("+rtphint", "+faststart"), a.suggestions.map { it.label })
        val (_, b) = at("ffmpeg -aq-mode v|")
        assertEquals(listOf("variance"), b.suggestions.map { it.label })
        val (_, f) = at("ffmpeg -f m|")
        assertEquals(listOf("mp4", "matroska"), f.suggestions.map { it.label })
        val (_, p) = at("ffmpeg -pix_fmt |")
        assertEquals(listOf("yuv420p", "rgb24"), p.suggestions.map { it.label })
        val (_, i) = at("ffmpeg -i |")
        assertEquals(listOf("input.mp4", "overlay_0.png", "logo.png"), i.suggestions.map { it.label })
    }

    @Test
    fun `inside a filtergraph, filters, their options and their values`() {
        val (text, a) = at("ffmpeg -i input.mp4 -vf \"crop=100:100,sc|\" output.mp4")
        assertEquals(listOf("scale"), a.suggestions.map { it.label })
        val (taken, cursor) = applySuggestion(text, a.suggestions.single())
        assertEquals("ffmpeg -i input.mp4 -vf \"crop=100:100,scale\" output.mp4", taken)
        assertEquals(text.indexOf("sc") + 5, cursor)
        // Audio filters for -af only.
        val (_, af) = at("ffmpeg -af a|")
        assertEquals(listOf("atempo", "amix", "aformat"), af.suggestions.map { it.label })
        // The filter's options, once its help is known; the ones given are not offered again.
        val (_, opts) = at("ffmpeg -vf scale=w=1:|")
        assertEquals(listOf("h=", "flags=", "eval="), opts.suggestions.map { it.label })
        assertEquals(DocTopic.Filter("scale"), opts.topic)
        val (_, values) = at("ffmpeg -vf scale=eval=|")
        assertEquals(listOf("init", "frame"), values.suggestions.map { it.label })
        // Unknown yet: the app is told to read it.
        val (_, crop) = at("ffmpeg -vf crop=|")
        assertEquals("crop", crop.needsFilterHelp)
        // Labels: the inputs and the ones the graph names.
        val (_, labels) = at("ffmpeg -i a.mp4 -i b.png -filter_complex \"[0:v][1:v]overlay[out];[|\" -map \"[out]\" output.mp4")
        assertEquals(listOf("0:v", "0:a", "1:v", "1:a", "out"), labels.suggestions.map { it.label })
        assertEquals("1:v]", labels.suggestions[2].insert)
    }

    @Test
    fun `streams to map, and what may come next when nothing is typed`() {
        val (_, m) = at("ffmpeg -i input.mp4 -i logo.png -filter_complex \"[0:v][1:v]overlay[v]\" -map |")
        val labels = m.suggestions.map { it.label }
        assertEquals("[v]", labels.first())
        assertTrue("1:v" in labels)
        assertFalse(labels.any { it.startsWith("2") })
        val (_, empty) = at("ffmpeg |")
        assertEquals("-i", empty.suggestions.first().label)
        val (_, after) = at("ffmpeg -i input.mp4 -y |")
        assertEquals("output.mp4", after.suggestions.first().label)
        assertFalse(after.suggestions.any { it.label == "-y" })
    }

    @Test
    fun `the checker marks what is wrong and where, and offers a fix`() {
        assertTrue(lint.check("ffmpeg -i input.mp4 -c:v libx264 -vf scale=-2:720 output.mp4").isEmpty())
        val text = "ffmpeg -i input.mp4 -c:v libx999 -vf \"[2:v]scal=1:1[x\" -map 3 -bogus 1 -i nothing.mp4 -c"
        val issues = lint.check(text)
        val codes = issues.map { it.code }
        assertTrue(LintCode.UNKNOWN_ENCODER in codes)
        assertTrue(LintCode.UNKNOWN_FILTER in codes)
        assertTrue(LintCode.UNBALANCED in codes)
        assertTrue(LintCode.BAD_INPUT_INDEX in codes)
        assertTrue(LintCode.UNKNOWN_OPTION in codes)
        assertTrue(LintCode.UNKNOWN_FILE in codes)
        assertTrue(LintCode.MISSING_VALUE in codes)
        val filter = issues.first { it.code == LintCode.UNKNOWN_FILTER }
        assertEquals("scal", text.substring(filter.start, filter.end))
        val noOutput = issues.first { it.code == LintCode.NO_OUTPUT }
        assertEquals(LintSeverity.ERROR, noOutput.severity)
        val (fixed, _) = applySuggestion(text, noOutput.fix!!)
        assertTrue(fixed.endsWith("-c output.mp4"))
        assertEquals(listOf(LintCode.OPEN_QUOTE), lint.check("ffmpeg -i input.mp4 -vf 'scale output.mp4").map { it.code }.distinct().filter { it == LintCode.OPEN_QUOTE })
        assertEquals(listOf(LintCode.NO_INPUT), lint.check("ffmpeg -y output.mp4").map { it.code })
        // Before FFmpeg has been read nothing is called unknown.
        assertTrue(CommandLint(FfmpegCatalog.BUILT_IN, files).check("ffmpeg -i input.mp4 -whatever 3 output.mp4").isEmpty())
    }

    @Test
    fun `dropping or setting the AI tags is said to change nothing`() {
        val text = "ffmpeg -i input.mp4 -map_metadata -1 -metadata AI_GENERATED= -metadata:s:a:0 digital_source_type=x -metadata title=Mine output.mp4"
        val issues = lint.check(text).filter { it.code == LintCode.AI_MARKS_KEPT }
        assertEquals(listOf("-1", "AI_GENERATED=", "digital_source_type=x"), issues.map { text.substring(it.start, it.end) })
        assertTrue(issues.all { it.severity == LintSeverity.WARNING })
    }

    @Test
    fun `laid out on lines and back, it runs the same`() {
        val line = "ffmpeg -y -i input.mp4 -c:v libx264 -vf \"scale=-2:720, fps=30\" -an output.mp4"
        val tidy = tidyCommand(line, catalog)
        assertEquals(
            "ffmpeg -y \\\n  -i input.mp4 \\\n  -c:v libx264 \\\n  -vf \"scale=-2:720, fps=30\" \\\n  -an \\\n  output.mp4",
            tidy,
        )
        assertEquals(tokenizeCommandLine(line), tokenizeCommandLine(tidy))
        assertEquals(line, oneLineCommand(tidy))
    }

    @Test
    fun `recipes are filled with the project's files`() {
        val gif = CommandRecipe.ALL.first { it.id == "gif" }.fill(files)!!
        assertTrue(gif.startsWith("ffmpeg -i input.mp4 "))
        assertTrue(lint.check(gif).none { it.severity == LintSeverity.ERROR })
        val pip = CommandRecipe.ALL.first { it.id == "pip" }
        assertEquals("ffmpeg -i input.mp4 -i overlay_0.png", pip.fill(files)!!.substringBefore(" -filter"))
        assertNull(pip.fill(files.take(1)))
        assertNull(CommandRecipe.ALL.first { it.id == "subs" }.fill(files))
        assertTrue(CommandRecipe.ALL.first { it.id == "mute" }.fill(files)!!.endsWith("output.mp4"))
        // The test FFmpeg has libx264 and aac, not libx265 nor libvpx-vp9.
        assertEquals(emptyList<String>(), CommandRecipe.ALL.first { it.id == "h264" }.missingEncoders(catalog))
        assertEquals(listOf("libx265"), CommandRecipe.ALL.first { it.id == "h265" }.missingEncoders(catalog))
        assertEquals(listOf("libvpx-vp9", "libopus"), CommandRecipe.ALL.first { it.id == "vp9" }.missingEncoders(catalog))
        assertEquals(emptyList<String>(), CommandRecipe.ALL.first { it.id == "vp9" }.missingEncoders(FfmpegCatalog.BUILT_IN))
        CommandRecipe.ALL.forEach { r ->
            val filled = r.fill(files + CommandAlias("subtitles.srt", AliasKind.SUBTITLES))
            assertNotNull(r.id, filled)
            assertTrue(r.id, CommandLint(FfmpegCatalog.BUILT_IN, emptyList()).check(filled!!).none { it.code == LintCode.NO_OUTPUT })
        }
    }

    @Test
    fun `added files get plain names the command can use`() {
        assertEquals("My_Logo.png", CommandFile.aliasFor("My Logo.png", emptySet()))
        assertEquals("My_Logo_2.png", CommandFile.aliasFor("My Logo.png", setOf("My_Logo.png")))
        assertEquals("file_2024.jpg", CommandFile.aliasFor("2024.JPG", emptySet()))
        assertTrue(CommandFile.isValidAlias("logo.png"))
        assertFalse(CommandFile.isValidAlias("output.png"))
        assertFalse(CommandFile.isValidAlias("no extension"))
        assertEquals("output_2.png", CommandFile.aliasFor("output.png", emptySet()))
    }
}
