package com.cuscus.ffmpeggui.core.engine

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.ManualCommandResolver
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.EncodingSettings
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.Stabilization
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.Split
import com.cuscus.ffmpeggui.core.output.OutputPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class ConversionEngineTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Stages by creating empty files; URIs containing "missing" cannot be read. */
    private inner class FakeStager : SourceStager {
        var closed = false
        lateinit var directory: File

        override fun open(): StagingSession {
            directory = temp.newFolder("staging")
            return object : StagingSession {
                private var counter = 0
                override fun stage(sourceUri: String, preferredName: String): String? {
                    if ("missing" in sourceUri) return null
                    return File(directory, "${counter++}_$preferredName").apply { writeText(sourceUri) }.path
                }
                override fun scratchFile(name: String): String = File(directory, name).path
                override fun close() {
                    directory.deleteRecursively()
                    closed = true
                }
            }
        }
    }

    /** Records commands and answers with scripted results; creates the output file like FFmpeg would. */
    private class FakeExecutor(private vararg val results: ExecutionResult) : FfmpegExecutor {
        val commands = mutableListOf<FfmpegCommand>()
        var concatListSeen: String? = null
        var coverTagsSeen: String? = null
        var logLines: List<String> = emptyList()

        /** How many numbered files a pattern output gets, as the segment muxer writes them (from 0). */
        var parts = 0
        var throwOnExecute: Throwable? = null

        override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult {
            commands += command
            val args = command.arguments
            args.indexOf("concat").takeIf { it >= 0 }?.let { concatListSeen = File(args[args.indexOf("-i")  + 1]).readText() }
            args.indexOf("ffmetadata").takeIf { it >= 0 }?.let { coverTagsSeen = File(args[it + 2]).readText() }
            val output = File(args.last())
            if (args.last() != "-" && '%' !in output.name) output.writeText("partial")
            if ('%' in output.name) repeat(parts) { File(String.format(args.last(), it)).writeText("part $it") }
            logLines.forEach(onLog)
            throwOnExecute?.let { throw it }
            return results.getOrElse(commands.size - 1) { ExecutionResult.Success }
        }
    }

    private val main = NamedSource("content://in", "input.mov")

    private val request = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mov",
        sources = listOf(SourceInfo(durationMs = 10_000)),
    )

    private fun engine(
        executor: FfmpegExecutor,
        stager: SourceStager,
        outputDir: File,
        recoder: ImageRecoder? = null,
        tagger: PictureTagger? = null,
        fonts: com.cuscus.ffmpeggui.core.command.FontProvider? = null,
        hdrMaker: HdrPictureMaker? = null,
    ) = ConversionEngine(
        commandBuilder = CommandBuilder { "/fonts/font.ttf" },
        manualResolver = ManualCommandResolver(),
        outputPlanner = OutputPlanner(),
        progressParser = ProgressParser(),
        executor = executor,
        stager = stager,
        outputDirectory = { outputDir.path },
        clock = { 99L },
        imageRecoder = recoder,
        pictureTagger = tagger,
        fonts = fonts,
        hdrMaker = hdrMaker,
    )

    /** A JPG-shaped file: the segments Ultra HDR looks at, then [body]. */
    private fun jpeg(body: String): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0, 2) + body.toByteArray() + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `an Ultra HDR picture is tagged, then joined to its gain map`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val order = mutableListOf<String>()
        // FFmpeg writes the JPG and the two lossless copies.
        val executor = object : FfmpegExecutor {
            lateinit var arguments: List<String>
            override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult {
                arguments = command.arguments
                arguments.filter { it.endsWith(".png") || it.endsWith(".jpg") }.forEach { path ->
                    File(path).writeBytes(if (path.endsWith(".jpg")) jpeg("picture") else byteArrayOf(1))
                }
                return ExecutionResult.Success
            }
        }
        var asked: HdrJob? = null
        val maker = HdrPictureMaker { job ->
            asked = job
            order += "gain map"
            File(job.gainMapJpg).writeBytes(jpeg("gain"))
            com.cuscus.ffmpeggui.core.hdr.GainMapMetadata(minStops = 0f, maxStops = 1.5f)
        }
        val hdrRequest = request.copy(
            format = OutputFormat.JPG,
            sources = listOf(SourceInfo(width = 40, height = 30, isStill = true)),
            image = com.cuscus.ffmpeggui.core.model.ImageEdit(hdr = com.cuscus.ffmpeggui.core.model.ImageHdr()),
            metadata = MetadataTags(title = "Sea"),
        )
        val job = ConversionJob(hdrRequest, MediaSources(listOf(main)))

        val outcome = engine(executor, FakeStager(), outputDir, tagger = { path, _, _ -> order += "tags on " + File(path).name; true }, hdrMaker = maker).run(job)

        val expected = File(outputDir, "clip_99.jpg")
        assertEquals(ConversionOutcome.Success(expected.path), outcome)
        assertEquals(listOf("tags on hdr_base.jpg", "gain map"), order)
        assertEquals("content://in", asked!!.sourceUri)
        assertTrue(executor.arguments.any { it.endsWith("hdr_final.png") })
        val written = com.cuscus.ffmpeggui.core.hdr.UltraHdr.read(expected.readBytes())!!
        assertEquals(1.5f, written.metadata.maxStops, 1e-5f)

        // Nothing to make brighter: the plain JPG.
        val plain = engine(executor, FakeStager(), temp.newFolder("out2"), hdrMaker = { null }).run(job)
        assertEquals("picture", String((plain as ConversionOutcome.Success).let { File(it.outputPath) }.readBytes()).substring(6, 13))
    }

    @Test
    fun `a picture gets its tags once it is written, and a failure only shows in the log`() = runBlocking {
        val outputDir = temp.newFolder("out")
        var tagged: Pair<String, MetadataTags>? = null
        val tags = MetadataTags(title = "Sea", artist = "Me")
        val job = ConversionJob(request.copy(format = OutputFormat.JPG, metadata = tags), MediaSources(listOf(main)))

        val outcome = engine(FakeExecutor(), FakeStager(), outputDir, tagger = { path, _, t -> tagged = path to t; true }).run(job)

        val expected = File(outputDir, "clip_99.jpg")
        assertEquals(ConversionOutcome.Success(expected.path), outcome)
        assertEquals(expected.path to tags, tagged)

        val events = mutableListOf<ConversionEvent>()
        val refused = engine(FakeExecutor(), FakeStager(), temp.newFolder("out2"), tagger = { _, _, _ -> false }).run(job) { events += it }
        assertTrue(refused is ConversionOutcome.Success)
        assertTrue(events.any { it is ConversionEvent.LogLine && it.text.startsWith("Tags not written") })
    }

    @Test
    fun `a webp picture is made by ffmpeg as png, then written by the recoder`() = runBlocking {
        val executor = FakeExecutor()
        val outputDir = temp.newFolder("out")
        var recoded: List<Any>? = null
        val recoder = ImageRecoder { png, target, format, quality ->
            recoded = listOf(File(png).readText(), format, quality)
            File(target).writeText("webp")
            true
        }
        val job = ConversionJob(request.copy(format = OutputFormat.WEBP), MediaSources(listOf(main)))

        val outcome = engine(executor, FakeStager(), outputDir, recoder).run(job)

        val expected = File(outputDir, "clip_99.webp")
        assertEquals(ConversionOutcome.Success(expected.path), outcome)
        assertEquals("webp", expected.readText())
        assertTrue(executor.commands.single().arguments.last().endsWith("picture.png"))
        assertEquals(listOf("partial", OutputFormat.WEBP, 0.9f), recoded)
    }

    @Test
    fun `a webp picture without a recoder fails and leaves nothing behind`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val job = ConversionJob(request.copy(format = OutputFormat.WEBP), MediaSources(listOf(main)))

        val outcome = engine(FakeExecutor(), FakeStager(), outputDir).run(job)

        assertEquals(ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "no webp encoder"), outcome)
        assertTrue(outputDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `successful conversion stages, encodes and cleans up`() = runBlocking {
        val stager = FakeStager()
        val executor = FakeExecutor().apply { logLines = listOf("time=00:00:05.00") }
        val outputDir = File(temp.root, "out")
        val events = mutableListOf<ConversionEvent>()

        val outcome = engine(executor, stager, outputDir).run(ConversionJob(request, MediaSources(listOf(main)))) { events += it }

        val expectedOutput = File(outputDir, "clip_99.mp4")
        assertEquals(ConversionOutcome.Success(expectedOutput.path), outcome)
        assertTrue(expectedOutput.exists())
        assertTrue(stager.closed)
        assertFalse(stager.directory.exists())
        assertEquals(File(stager.directory, "0_input.mov").path, executor.commands.single().arguments[2])
        assertEquals(
            listOf(
                ConversionEvent.StageChanged(ConversionStage.PREPARING),
                ConversionEvent.StageChanged(ConversionStage.ENCODING),
                ConversionEvent.LogLine(executor.commands.single().toDisplayString()),
                ConversionEvent.LogLine("time=00:00:05.00"),
                ConversionEvent.Progress(0.5f),
            ),
            events,
        )
    }

    @Test
    fun `unreadable source fails before running ffmpeg`() = runBlocking {
        val stager = FakeStager()
        val executor = FakeExecutor()
        val job = ConversionJob(
            request.copy(imageOverlays = listOf(ImagePlacement())),
            MediaSources(listOf(main), imageOverlays = listOf(NamedSource("content://missing", "logo.png"))),
        )

        val outcome = engine(executor, stager, temp.newFolder("out")).run(job)

        assertEquals(ConversionOutcome.Failure(FailureReason.SOURCE_UNREADABLE, "logo.png"), outcome)
        assertTrue(executor.commands.isEmpty())
        assertTrue(stager.closed)
    }

    @Test
    fun `failed encode removes the partial output`() = runBlocking {
        val executor = FakeExecutor(ExecutionResult.Failed(1))
        val outputDir = temp.newFolder("out")

        val outcome = engine(executor, FakeStager(), outputDir).run(ConversionJob(request, MediaSources(listOf(main))))

        assertEquals(ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "exit code 1"), outcome)
        assertFalse(File(outputDir, "clip_99.mp4").exists())
    }

    @Test
    fun `cancellation cleans up and propagates`() = runBlocking {
        val stager = FakeStager()
        val executor = FakeExecutor().apply { throwOnExecute = CancellationException("stop") }
        val outputDir = temp.newFolder("out")

        try {
            engine(executor, stager, outputDir).run(ConversionJob(request, MediaSources(listOf(main))))
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertTrue(stager.closed)
            assertFalse(File(outputDir, "clip_99.mp4").exists())
        }
    }

    private fun zipEntries(file: File): List<Pair<String, String>> = ZipFile(file).use { zip ->
        zip.entries().toList().map { it.name to zip.getInputStream(it).readBytes().decodeToString() }
    }

    @Test
    fun `a video split into parts comes out as one zip of them, without the folder`() = runBlocking {
        val executor = FakeExecutor().apply { parts = 3 }
        val outputDir = temp.newFolder("out")
        val events = mutableListOf<ConversionEvent>()

        val outcome = engine(executor, FakeStager(), outputDir).run(ConversionJob(request.copy(split = Split.ByDuration(4)), MediaSources(listOf(main)))) { events += it }

        val zip = File(outputDir, "clip_segments_99.zip")
        assertEquals(ConversionOutcome.Success(zip.path), outcome)
        assertEquals(
            listOf("part_000.mp4" to "part 0", "part_001.mp4" to "part 1", "part_002.mp4" to "part 2"),
            zipEntries(zip),
        )
        assertEquals(listOf(zip.name), outputDir.list()?.toList())
        assertTrue(ConversionEvent.StageChanged(ConversionStage.PACKING) in events)
    }

    @Test
    fun `a split that fails leaves neither parts nor zip, and one that writes nothing fails`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val split = ConversionJob(request.copy(split = Split.ByDuration(4)), MediaSources(listOf(main)))

        val failed = engine(FakeExecutor(ExecutionResult.Failed(1)).apply { parts = 2 }, FakeStager(), outputDir).run(split)
        assertEquals(ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "exit code 1"), failed)
        assertTrue(outputDir.listFiles().isNullOrEmpty())

        val empty = engine(FakeExecutor(), FakeStager(), outputDir).run(split)
        assertEquals(ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "no files written"), empty)
        assertTrue(outputDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a hand-written numbered output is packed with only its own files`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val other = File(outputDir, "earlier_export.mp4").apply { writeText("keep") }
        val executor = FakeExecutor().apply { parts = 2 }
        val job = ConversionJob(
            request.copy(format = OutputFormat.CUSTOM),
            MediaSources(listOf(main)),
            CommandMode.Manual("ffmpeg -i input.mov -vf fps=1 output_%03d.png"),
        )

        val outcome = engine(executor, FakeStager(), outputDir).run(job)

        val zip = File(outputDir, "clip_99.zip")
        assertEquals(ConversionOutcome.Success(zip.path), outcome)
        assertEquals(listOf("clip_99_000.png", "clip_99_001.png"), zipEntries(zip).map { it.first })
        assertEquals(setOf(zip.name, other.name), outputDir.list()?.toSet())
    }

    @Test
    fun `each source is staged once and segments become seeked inputs`() = runBlocking {
        val stager = FakeStager()
        val executor = FakeExecutor()
        val job = ConversionJob(
            request.copy(
                sources = listOf(SourceInfo(durationMs = 10_000), SourceInfo(durationMs = 3_000)),
                segments = listOf(Segment(0, 1_000, 4_000), Segment(1, 500, 3_000), Segment(0, 6_000, 7_000)),
            ),
            MediaSources(listOf(main, NamedSource("content://b", "b.mp4"))),
        )

        engine(executor, stager, temp.newFolder("out")).run(job)

        val dir = stager.directory.path
        val inputs = executor.commands.single().arguments.let { args ->
            args.indices.filter { args[it] == "-i" }.map { args[it + 1] }
        }
        assertEquals(listOf("$dir/0_input.mov", "$dir/1_b.mp4", "$dir/0_input.mov"), inputs)
    }

    @Test
    fun `remuxing several segments writes a concat list with their trims`() = runBlocking {
        val stager = FakeStager()
        val executor = FakeExecutor()
        val job = ConversionJob(
            request.copy(
                format = OutputFormat.MKV,
                encoding = EncodingSettings(streamCopy = true),
                segments = listOf(Segment(0, 1_000, 4_000), Segment(0, 500, null)),
            ),
            MediaSources(listOf(main)),
        )

        engine(executor, stager, temp.newFolder("out")).run(job)

        val dir = stager.directory.path
        assertEquals(
            "ffconcat version 1.0\n" +
                "file '$dir/0_input.mov'\ninpoint 1.000\noutpoint 4.000\n" +
                "file '$dir/0_input.mov'\ninpoint 0.500\n",
            executor.concatListSeen,
        )
    }

    @Test
    fun `subtitles are re-timed to the edit before they are burnt in`() = runBlocking {
        val stager = object : SourceStager {
            lateinit var directory: File
            override fun open(): StagingSession {
                directory = temp.newFolder("staging-subs")
                return object : StagingSession {
                    override fun stage(sourceUri: String, preferredName: String): String =
                        File(directory, preferredName).apply {
                            writeText(if (preferredName.endsWith(".srt")) "1\n00:00:05,000 --> 00:00:07,000\nHello\n" else "x")
                        }.path
                    override fun scratchFile(name: String): String = File(directory, name).path
                    override fun close() {}
                }
            }
        }
        val executor = FakeExecutor()
        val job = ConversionJob(
            request.copy(segments = listOf(Segment(0, 4_000, 6_000)), subtitleSource = 0),
            MediaSources(listOf(main), subtitles = NamedSource("content://subs", "subs.srt")),
        )

        engine(executor, stager, temp.newFolder("out")).run(job)

        val retimed = File(stager.directory, "subtitles_retimed.srt")
        // The cue starts 1 s into the kept range and is cut where the range ends.
        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHello\n\n", retimed.readText())
        assertTrue(executor.commands.single().arguments.any { it.contains("subtitles='${retimed.path}'") })
    }

    @Test
    fun `spoken words are written as a styled script and burnt in with their font`() = runBlocking {
        val stager = FakeStager()
        val executor = FakeExecutor()
        val font = File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        val words = listOf(
            com.cuscus.ffmpeggui.core.subtitles.SpokenWord(5_000, 5_400, "Hello"),
            com.cuscus.ffmpeggui.core.subtitles.SpokenWord(5_500, 5_900, "there."),
            com.cuscus.ffmpeggui.core.subtitles.SpokenWord(9_000, 9_400, "Gone"),
        )
        var script = ""
        val reading = object : FfmpegExecutor {
            override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult {
                command.arguments.firstOrNull { it.contains("subtitles_styled.ass") }?.let {
                    script = File(stager.directory, "subtitles_styled.ass").readText()
                }
                return executor.execute(command, onLog)
            }
        }
        val job = ConversionJob(
            request.copy(
                sources = listOf(SourceInfo(durationMs = 10_000, width = 1080, height = 1920)),
                segments = listOf(Segment(0, 4_000, 6_000)),
                subtitleStyle = com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle(),
                spokenWords = listOf(com.cuscus.ffmpeggui.core.subtitles.SourceWords(0, words)),
            ),
            MediaSources(listOf(main)),
        )

        engine(reading, stager, temp.newFolder("out"), fonts = if (font.isFile) ({ font.path }) else null).run(job)

        val filter = executor.commands.single().arguments.first { it.contains("subtitles=") }
        assertTrue(filter, filter.contains("subtitles_styled.ass"))
        // The words of the kept range only, moved to its start; portrait canvas.
        assertTrue(script, script.contains("PlayResX: 608"))
        assertTrue(script, script.contains("Dialogue: 0,0:00:01.00,"))
        assertTrue(script, script.contains("there."))
        assertFalse(script, script.contains("Gone"))
        if (font.isFile) {
            assertTrue(filter, filter.contains(":fontsdir="))
            assertTrue(script, script.contains("Style: Default,DejaVu Sans,"))
        }
    }

    @Test
    fun `a subrip file with a style is drawn as a styled script`() = runBlocking {
        val stager = object : SourceStager {
            lateinit var directory: File
            override fun open(): StagingSession {
                directory = temp.newFolder("staging-styled")
                return object : StagingSession {
                    override fun stage(sourceUri: String, preferredName: String): String =
                        File(directory, preferredName).apply {
                            writeText(if (preferredName.endsWith(".srt")) "1\n00:00:05,000 --> 00:00:07,000\nHello\n" else "x")
                        }.path
                    override fun scratchFile(name: String): String = File(directory, name).path
                    override fun close() {}
                }
            }
        }
        val executor = FakeExecutor()
        val job = ConversionJob(
            request.copy(subtitleSource = 0, subtitleStyle = com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle(look = com.cuscus.ffmpeggui.core.subtitles.SubtitleLook.BOX)),
            MediaSources(listOf(main), subtitles = NamedSource("content://subs", "subs.srt")),
        )

        engine(executor, stager, temp.newFolder("out")).run(job)

        val script = File(stager.directory, "subtitles_styled.ass").readText()
        assertTrue(script, script.contains("Dialogue: 0,0:00:05.00,0:00:07.00,Default,,0,0,0,,{\\an2"))
        assertTrue(script, script.contains(",3,")) // the box
        assertTrue(executor.commands.single().arguments.any { it.contains("subtitles_styled.ass") })
    }

    @Test
    fun `stabilization runs the analysis pass first`() = runBlocking {
        val executor = FakeExecutor()
        val events = mutableListOf<ConversionEvent>()
        val job = ConversionJob(request.copy(stabilization = Stabilization()), MediaSources(listOf(main)))

        engine(executor, FakeStager(), temp.newFolder("out")).run(job) { events += it }

        assertEquals(2, executor.commands.size)
        assertTrue(executor.commands[0].arguments.any { it.startsWith("vidstabdetect=") })
        assertTrue(executor.commands[1].arguments.any { it.startsWith("vidstabtransform=") })
        assertTrue(ConversionEvent.StageChanged(ConversionStage.ANALYZING) in events)
    }

    @Test
    fun `failed analysis stops the job`() = runBlocking {
        val executor = FakeExecutor(ExecutionResult.Failed(1))
        val job = ConversionJob(request.copy(stabilization = Stabilization()), MediaSources(listOf(main)))

        val outcome = engine(executor, FakeStager(), temp.newFolder("out")).run(job)

        assertEquals(ConversionOutcome.Failure(FailureReason.ANALYSIS_FAILED), outcome)
        assertEquals(1, executor.commands.size)
    }

    @Test
    fun `manual command is resolved against staged files`() = runBlocking {
        val stager = FakeStager()
        val executor = FakeExecutor()
        val outputDir = temp.newFolder("out")
        val job = ConversionJob(
            request.copy(format = OutputFormat.CUSTOM),
            MediaSources(listOf(main)),
            CommandMode.Manual("ffmpeg -i input.mov -c copy output.mkv"),
        )

        val outcome = engine(executor, stager, outputDir).run(job)

        val expectedOutput = File(outputDir, "clip_99.mkv").path
        assertEquals(ConversionOutcome.Success(expectedOutput), outcome)
        assertEquals(
            listOf("-i", File(stager.directory, "0_input.mov").path, "-c", "copy", expectedOutput),
            executor.commands.single().arguments,
        )
    }

    @Test
    fun `manual command without an output is rejected`() = runBlocking {
        val executor = FakeExecutor()
        val job = ConversionJob(request, MediaSources(listOf(main)), CommandMode.Manual("ffmpeg -i input.mov"))

        val outcome = engine(executor, FakeStager(), temp.newFolder("out")).run(job)

        assertEquals(ConversionOutcome.Failure(FailureReason.MANUAL_COMMAND_WITHOUT_OUTPUT), outcome)
        assertTrue(executor.commands.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `job rejects mismatched sources`() {
        ConversionJob(request.copy(subtitleSource = 0), MediaSources(listOf(main)))
    }

    @Test
    fun `a cover is staged, and made into a tags file for ogg`() = runBlocking {
        val cover = NamedSource("content://cover", "cover.jpg")
        val mp3 = FakeExecutor()
        val stager = FakeStager()
        engine(mp3, stager, temp.newFolder("out")).run(ConversionJob(request.copy(format = OutputFormat.MP3, cover = true), MediaSources(listOf(main), cover = cover)))
        val args = mp3.commands.single().arguments
        assertEquals(File(stager.directory, "1_cover.jpg").path, args[args.lastIndexOf("-i") + 1])

        val ogg = FakeExecutor()
        engine(ogg, FakeStager(), temp.newFolder("out2")).run(ConversionJob(request.copy(format = OutputFormat.OGG, cover = true), MediaSources(listOf(main), cover = cover)))
        val tags = requireNotNull(ogg.coverTagsSeen)
        assertTrue(tags, tags.startsWith(";FFMETADATA1\nMETADATA_BLOCK_PICTURE="))
        assertFalse(ogg.commands.single().arguments.any { it.endsWith("cover.jpg") })

        // A WAV holds no cover: the picture is not even copied.
        val wav = FakeExecutor()
        val wavStager = FakeStager()
        engine(wav, wavStager, temp.newFolder("out3")).run(ConversionJob(request.copy(format = OutputFormat.WAV, cover = true), MediaSources(listOf(main), cover = cover)))
        assertFalse(wav.commands.single().arguments.any { "cover" in it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `job rejects a cover without its picture`() {
        ConversionJob(request.copy(format = OutputFormat.MP3, cover = true), MediaSources(listOf(main)))
    }

    @Test
    fun `the phone's encoder is used, and the processor takes over when it fails`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val hardware = request.copy(encoding = request.encoding.copy(hardwareEncoder = HardwareEncoder("c2.test.avc.encoder")))
        val job = ConversionJob(hardware, MediaSources(listOf(main)))
        val expected = File(outputDir, "clip_99.mp4")

        // It works: one run, by the phone.
        val working = FakeExecutor()
        val events = mutableListOf<ConversionEvent>()
        val first = engine(working, FakeStager(), outputDir).run(job) { events += it }
        assertEquals(ConversionOutcome.Success(expected.path, VideoEncoderUse.HARDWARE), first)
        assertEquals(1, working.commands.size)
        assertTrue("h264_mediacodec" in working.commands[0].arguments)
        assertTrue(ConversionEvent.Encoder(hardware = true) in events)

        // It fails: the same file again with x264, said in the events.
        val failing = FakeExecutor(ExecutionResult.Failed(1), ExecutionResult.Success)
        val fallbackEvents = mutableListOf<ConversionEvent>()
        val second = engine(failing, FakeStager(), outputDir).run(job) { fallbackEvents += it }
        assertEquals(ConversionOutcome.Success(expected.path, VideoEncoderUse.FALLBACK), second)
        assertEquals(2, failing.commands.size)
        assertTrue("h264_mediacodec" in failing.commands[0].arguments)
        assertTrue("libx264" in failing.commands[1].arguments)
        assertFalse("h264_mediacodec" in failing.commands[1].arguments)
        assertTrue(ConversionEvent.Encoder(hardware = false) in fallbackEvents)

        // Both fail: the failure is reported, nothing is left behind.
        File(outputDir, "clip_99.mp4").delete()
        val broken = FakeExecutor(ExecutionResult.Failed(1), ExecutionResult.Failed(2))
        val third = engine(broken, FakeStager(), outputDir).run(job)
        assertTrue(third is ConversionOutcome.Failure)
        assertFalse(expected.exists())
    }

    @Test
    fun `without the phone's encoder a failure is not run again`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val executor = FakeExecutor(ExecutionResult.Failed(1))
        val outcome = engine(executor, FakeStager(), outputDir).run(ConversionJob(request, MediaSources(listOf(main))))
        assertTrue(outcome is ConversionOutcome.Failure)
        assertEquals(1, executor.commands.size)
    }
}
