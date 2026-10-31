package com.cuscus.ffmpeggui.core.engine

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.ManualCommandResolver
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.output.OutputPlanner
import com.cuscus.ffmpeggui.core.project.ModelJson
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A picture saved as an SVG: FFmpeg writes a PNG, the tracer draws it. */
class SvgExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `an svg is the png ffmpeg made, traced`() = runBlocking {
        val outputDir = temp.newFolder("out")
        val staging = temp.newFolder("staging")
        var pngWritten: String? = null
        val executor = object : FfmpegExecutor {
            override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult {
                val args = command.arguments
                assertEquals("png", args[args.indexOf("-c:v") + 1])
                pngWritten = args.last()
                File(args.last()).writeText("png")
                return ExecutionResult.Success
            }
        }
        val red = 0xFFFF0000.toInt()
        val recoder = object : ImageRecoder {
            override fun recode(pngPath: String, targetPath: String, format: OutputFormat, quality: Float) = false
            override fun read(pngPath: String): Raster = Raster(8, 4, IntArray(32) { if (it % 8 < 4) red else -1 })
        }
        val stager = object : SourceStager {
            override fun open(): StagingSession = object : StagingSession {
                override fun stage(sourceUri: String, preferredName: String): String = File(staging, preferredName).apply { writeText("x") }.path
                override fun scratchFile(name: String): String = File(staging, name).path
                override fun close() = Unit
            }
        }
        val engine = ConversionEngine(
            commandBuilder = CommandBuilder { "/f.ttf" },
            manualResolver = ManualCommandResolver(),
            outputPlanner = OutputPlanner(),
            progressParser = ProgressParser(),
            executor = executor,
            stager = stager,
            outputDirectory = { outputDir.path },
            clock = { 1L },
            imageRecoder = recoder,
        )
        val request = ConversionRequest(
            format = OutputFormat.SVG,
            inputFileName = "logo.png",
            sources = listOf(SourceInfo(width = 8, height = 4, hasAudio = false, isStill = true)),
        )
        val outcome = engine.run(ConversionJob(request, MediaSources(listOf(NamedSource("content://l", "logo.png")))))
        assertTrue(outcome.toString(), outcome is ConversionOutcome.Success)
        assertTrue(pngWritten!!.endsWith(".png"))
        val svg = outputDir.walkTopDown().first { it.isFile && it.name.endsWith(".svg") }.readText()
        assertTrue(svg, svg.contains("<svg") && svg.contains("#ff0000"))
    }

    @Test
    fun `how a picture is traced is saved with the project`() {
        val edit = ImageEdit(trace = TraceSettings(colors = 5, detail = 0.25f, smooth = false, speckle = 3, mono = true, maxSide = 800))
        assertEquals(edit, ModelJson.imageEdit(ModelJson.write(edit)))
    }
}
