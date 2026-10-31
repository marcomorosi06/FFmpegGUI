package com.cuscus.ffmpeggui.core.engine

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.ManualCommandResolver
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.model.Compression
import com.cuscus.ffmpeggui.core.model.CompressionLevel
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.EncodingSettings
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageOutput
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.output.OutputPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The compressor: x265 at a CRF or fitting a size, squeezed pictures and sounds. */
class CompressionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun video(compression: Compression, hardware: Boolean = false) = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 1920, height = 1080)),
        compression = compression,
        encoding = EncodingSettings(hardwareEncoder = if (hardware) HardwareEncoder("c2.enc", "nv12") else null),
    )

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun List<String>.after(flag: String): String = this[indexOf(flag) + 1]

    @Test
    fun `same quality is x265 at a calibrated CRF, never the phone's encoder`() {
        val a = args(video(Compression(CompressionLevel.SAME_QUALITY), hardware = true))
        assertEquals("libx265", a.after("-vcodec"))
        assertEquals("24", a.after("-crf"))
        assertEquals("hvc1", a.after("-tag:v"))
        assertEquals("128k", a.after("-b:a"))
        assertFalse(builder.usesHardwareEncoder(video(Compression(), hardware = true)))
        assertEquals("32", args(video(Compression(CompressionLevel.SMALLEST))).after("-crf"))
    }

    @Test
    fun `a size is filled in two passes at the bitrate that fits it`() {
        val request = video(Compression(targetBytes = 10_000_000L))
        assertTrue(builder.needsFirstPass(request))
        val paths = MediaPaths.aliasesFor(request)
        assertEquals("x265_2pass.log", paths.passLog)
        val first = builder.buildFirstPass(request, paths).arguments
        assertEquals("pass=1:stats=x265_2pass.log", first.after("-x265-params"))
        assertEquals(listOf("-f", "null", "-"), first.takeLast(3))
        val second = builder.build(request, paths).arguments
        assertEquals("pass=2:stats=x265_2pass.log", second.after("-x265-params"))
        // 10 MB in 10 s: 7760 kbit/s in all (3 % kept for the container), a fifth at most for the sound.
        assertEquals("128k", second.after("-b:a"))
        assertEquals("${7760 - 128}k", second.after("-b:v"))
        assertFalse(second.contains("-crf"))
    }

    @Test
    fun `a tight size gives the sound less`() {
        assertEquals(64, Compression.videoAudioKbps(1_000_000L, 60_000L))
        assertEquals(50, Compression.videoKbps(100_000L, 60_000L, 64))
        assertEquals(null, Compression.videoKbps(1_000_000L, null, 64))
    }

    @Test
    fun `a sound is squeezed within what its codec takes`() {
        assertEquals(192, Compression.audioKbps(OutputFormat.MP3, Compression(), null))
        assertEquals(96, Compression.audioKbps(OutputFormat.MP3, Compression(CompressionLevel.SMALLEST), null))
        // 3 MB for three minutes.
        assertEquals(129, Compression.audioKbps(OutputFormat.M4A, Compression(targetBytes = 3_000_000L), 180_000L))
        assertEquals(16, Compression.audioKbps(OutputFormat.OPUS, Compression(targetBytes = 10_000L), 600_000L))
    }

    @Test
    fun `a png is deflated hardest, and can be made 256 colours`() {
        fun png(palette: Boolean) = ConversionRequest(
            format = OutputFormat.PNG,
            inputFileName = "shot.png",
            sources = listOf(SourceInfo(width = 400, height = 300, hasAudio = false, isStill = true)),
            compression = Compression(pngPalette = palette),
        )
        val plain = args(png(false))
        assertEquals("9", plain.after("-compression_level"))
        assertEquals("mixed", plain.after("-pred"))
        assertEquals("rgb24", plain.after("-pix_fmt"))
        val palette = args(png(true))
        assertEquals("pal8", palette.after("-pix_fmt"))
        assertEquals("none", palette.after("-pred"))
        assertTrue(palette.after("-filter_complex").contains("palettegen=max_colors=256:reserve_transparent=1"))
    }

    @Test
    fun `a jpg is fitted into its size at the best quality that fits`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val staging = temp.newFolder("staging")
        val qualities = mutableListOf<Int>()
        // A JPG as large as its quality scale says: 2 (best) is 30 kB, 31 (worst) 1 kB.
        val executor = object : FfmpegExecutor {
            override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult {
                val q = command.arguments.after("-q:v").toInt()
                qualities += q
                File(command.arguments.last()).writeBytes(ByteArray((32 - q) * 1000))
                return ExecutionResult.Success
            }
        }
        val stager = object : SourceStager {
            override fun open(): StagingSession = object : StagingSession {
                override fun stage(sourceUri: String, preferredName: String): String = File(staging, preferredName).apply { writeText("x") }.path
                override fun scratchFile(name: String): String = File(staging, name).path
                override fun close() = Unit
            }
        }
        val engine = ConversionEngine(
            commandBuilder = builder,
            manualResolver = ManualCommandResolver(),
            outputPlanner = OutputPlanner(),
            progressParser = ProgressParser(),
            executor = executor,
            stager = stager,
            outputDirectory = { outputDir.path },
            clock = { 1L },
        )
        val request = ConversionRequest(
            format = OutputFormat.JPG,
            inputFileName = "photo.jpg",
            sources = listOf(SourceInfo(width = 400, height = 300, hasAudio = false, isStill = true)),
            image = ImageEdit(output = ImageOutput(quality = 0.95f)),
            compression = Compression(targetBytes = 12_000L),
        )
        val outcome = engine.run(ConversionJob(request, MediaSources(listOf(NamedSource("content://p", "photo.jpg")))))
        assertTrue(outcome.toString(), outcome is ConversionOutcome.Success)
        val written = outputDir.walkTopDown().first { it.isFile && it.name.endsWith(".jpg") }
        // 12 kB fits at the scale of 20 (12 kB), not better.
        assertTrue("${written.length()}", written.length() <= 12_000L)
        assertTrue("${written.length()}", written.length() >= 10_000L)
        assertEquals(7, qualities.size)
    }
}
