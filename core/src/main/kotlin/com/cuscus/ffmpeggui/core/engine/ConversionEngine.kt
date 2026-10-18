package com.cuscus.ffmpeggui.core.engine

import com.cuscus.ffmpeggui.core.hdr.HdrVideoLook
import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.CoverTags
import com.cuscus.ffmpeggui.core.command.CoverWriting
import com.cuscus.ffmpeggui.core.command.coverWriting
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.ManualCommandResolver
import com.cuscus.ffmpeggui.core.command.HdrPictures
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.hdr.UltraHdr
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.output.OutputPlan
import com.cuscus.ffmpeggui.core.output.OutputPlanner
import com.cuscus.ffmpeggui.core.output.PartsArchive
import com.cuscus.ffmpeggui.core.command.FontProvider
import com.cuscus.ffmpeggui.core.preview.SubtitleParser
import com.cuscus.ffmpeggui.core.preview.SubtitleRetimer
import com.cuscus.ffmpeggui.core.preview.onTimeline
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.subtitles.AssSubtitles
import com.cuscus.ffmpeggui.core.subtitles.FontFacts
import com.cuscus.ffmpeggui.core.subtitles.SubtitleBlock
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLayout
import com.cuscus.ffmpeggui.core.time.progressDurationMs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/**
 * Runs a [ConversionJob] from start to finish:
 *
 *   stage sources (and the cover) -> re-time subtitles (or write the spoken ones) -> (stabilization analysis)
 *   -> build command -> encode
 *   (with the phone's video encoder when asked, again with x264 when that fails)
 *   -> (pack numbered files into a zip) -> clean up
 *
 * The engine owns the workflow only. Everything platform-specific is reached
 * through the ports in this package, which also makes the whole flow testable
 * on a plain JVM.
 */
class ConversionEngine(
    private val commandBuilder: CommandBuilder,
    private val manualResolver: ManualCommandResolver,
    private val outputPlanner: OutputPlanner,
    private val progressParser: ProgressParser,
    private val executor: FfmpegExecutor,
    private val stager: SourceStager,
    private val outputDirectory: OutputDirectoryProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    private val subtitleRetimer: SubtitleRetimer = SubtitleRetimer(),
    private val imageRecoder: ImageRecoder? = null,
    private val pictureTagger: PictureTagger? = null,
    /** The font styled subtitles are drawn with; without it libass picks one itself. */
    private val fonts: FontProvider? = null,
    private val subtitleParser: SubtitleParser = SubtitleParser(),
    /** Makes the gain map of an Ultra HDR picture; without it the HDR look is not written. */
    private val hdrMaker: HdrPictureMaker? = null,
) {

    /**
     * Runs [job], reporting through [onEvent] (which may be invoked from other
     * threads). Cancelling the calling coroutine stops FFmpeg, removes temporary
     * files and any partial output, then rethrows the cancellation.
     */
    suspend fun run(job: ConversionJob, onEvent: (ConversionEvent) -> Unit = {}): ConversionOutcome {
        var partialOutput: File? = null
        var packedPlan: OutputPlan? = null
        var succeeded = false
        val session = stager.open()
        try {
            onEvent(ConversionEvent.StageChanged(ConversionStage.PREPARING))

            val staged = when (val result = stageSources(job, session)) {
                is Staging.Ready -> result
                is Staging.Unreadable ->
                    return ConversionOutcome.Failure(FailureReason.SOURCE_UNREADABLE, result.name)
            }

            val request = job.request
            val timestamp = clock()
            val directory = outputDirectory.outputDirectory()
            val plan = outputPlanner.plan(request, directory, timestamp)
            val paths = staged.paths.copy(output = plan.commandPath)

            if (commandBuilder.needsStabilizationAnalysis(request)) {
                onEvent(ConversionEvent.StageChanged(ConversionStage.ANALYZING))
                val analysis = commandBuilder.buildStabilizationAnalysis(request, paths)
                when (executor.execute(analysis) { onEvent(ConversionEvent.LogLine(it)) }) {
                    ExecutionResult.Success -> Unit
                    ExecutionResult.Cancelled -> return ConversionOutcome.Cancelled
                    is ExecutionResult.Failed -> return ConversionOutcome.Failure(FailureReason.ANALYSIS_FAILED)
                }
            }

            // A format FFmpeg cannot write: it makes a PNG next to the sources, encoded afterwards.
            // (One picture: a sequence of frames is written in a format FFmpeg has, see sequenceFormat.)
            val onePicture = request.frameExtraction == FrameExtraction.Disabled
            val recoded = job.mode == CommandMode.Generated && request.format.needsPlatformEncoder && onePicture
            val intermediate = if (recoded) session.scratchFile("picture.png") else null
            // An Ultra HDR picture: FFmpeg writes the JPG and its two lossless copies next to the
            // sources; the gain map is worked out of them and joined to the JPG afterwards.
            val hdr = request.image.hdr?.takeIf {
                job.mode == CommandMode.Generated && request.format == OutputFormat.JPG && it.hasArea && hdrMaker != null && onePicture
            }
            val hdrBase = if (hdr != null) session.scratchFile("hdr_base.jpg") else null
            val hdrPictures = if (hdr != null) HdrPictures(session.scratchFile("hdr_final.png"), session.scratchFile("hdr_plain.png")) else null
            val hardware = job.mode == CommandMode.Generated && commandBuilder.usesHardwareEncoder(request)
            val prepared = when (val mode = job.mode) {
                CommandMode.Generated -> Prepared(
                    commandBuilder.build(request, paths.copy(output = intermediate ?: hdrBase ?: plan.commandPath, hdrPictures = hdrPictures)),
                    plan,
                )
                is CommandMode.Manual -> {
                    val aliases = MediaPaths.aliasesFor(request).inputAliasesTo(paths)
                    val stem = outputPlanner.manualStem(request, directory, timestamp)
                    when (val resolved = manualResolver.resolve(mode.commandLine, aliases, stem)) {
                        ManualCommandResolver.Result.Empty ->
                            return ConversionOutcome.Failure(FailureReason.MANUAL_COMMAND_EMPTY)
                        ManualCommandResolver.Result.MissingOutput ->
                            return ConversionOutcome.Failure(FailureReason.MANUAL_COMMAND_WITHOUT_OUTPUT)
                        is ManualCommandResolver.Result.Resolved -> Prepared(
                            command = resolved.command,
                            plan = OutputPlan(
                                commandPath = resolved.outputPath,
                                resultPath = if (resolved.isPattern) "$stem.zip" else resolved.outputPath,
                                packed = resolved.isPattern,
                            ),
                        )
                    }
                }
            }

            File(prepared.plan.commandPath).parentFile?.mkdirs()
            if (prepared.plan.packed) packedPlan = prepared.plan else partialOutput = File(prepared.plan.commandPath)

            onEvent(ConversionEvent.StageChanged(ConversionStage.ENCODING))
            val expectedMs = if (job.mode == CommandMode.Generated) request.progressDurationMs() else null
            val encode: suspend (FfmpegCommand) -> ExecutionResult = { command ->
                // The command first: the log then tells what was asked, not only what came of it.
                onEvent(ConversionEvent.LogLine(command.toDisplayString()))
                executor.execute(command) { line ->
                    onEvent(ConversionEvent.LogLine(line))
                    progressParser.fraction(line, expectedMs)?.let { onEvent(ConversionEvent.Progress(it)) }
                }
            }
            if (hardware) onEvent(ConversionEvent.Encoder(hardware = true))
            var result = encode(prepared.command)
            var encoder = if (hardware) VideoEncoderUse.HARDWARE else VideoEncoderUse.NONE
            // The phone's encoder refused (a size, a format it does not take, a busy chip):
            // the same file again, from the start, with x264.
            if (hardware && result is ExecutionResult.Failed) {
                currentCoroutineContext().ensureActive()
                File(prepared.plan.commandPath).delete()
                onEvent(ConversionEvent.LogLine("The phone's video encoder failed (exit code ${result.returnCode}): encoding on the processor"))
                onEvent(ConversionEvent.Encoder(hardware = false))
                onEvent(ConversionEvent.Progress(0f))
                val software = commandBuilder.build(
                    commandBuilder.withoutHardwareEncoder(request),
                    paths.copy(output = intermediate ?: plan.commandPath),
                )
                result = encode(software)
                encoder = VideoEncoderUse.FALLBACK
            }

            return when (result) {
                ExecutionResult.Success -> {
                    if (intermediate != null) {
                        val written = imageRecoder?.recode(intermediate, prepared.plan.commandPath, request.format, request.image.output.quality) ?: false
                        if (!written) return ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "no ${request.format.extension} encoder")
                    }
                    // FFmpeg writes no tags into a picture: they are added to the finished file
                    // (to the JPG of an Ultra HDR picture, before the gain map is joined to it).
                    val tagPath = hdrBase ?: prepared.plan.commandPath
                    if (request.format.isImage && onePicture && job.mode == CommandMode.Generated && !request.metadata.isEmpty) {
                        val tagged = pictureTagger?.write(tagPath, request.format, request.metadata) ?: false
                        if (!tagged) onEvent(ConversionEvent.LogLine("Tags not written: ${request.format.extension} cannot hold them here"))
                    }
                    if (hdr != null && hdrBase != null && hdrPictures != null) {
                        currentCoroutineContext().ensureActive()
                        if (!writeUltraHdr(job, hdr, hdrBase, hdrPictures, session, prepared.plan.commandPath, onEvent)) {
                            return ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "picture not written")
                        }
                    }
                    if (prepared.plan.packed) pack(prepared.plan, directory, onEvent)?.let { return it }
                    succeeded = true
                    ConversionOutcome.Success(prepared.plan.resultPath, encoder)
                }
                ExecutionResult.Cancelled -> ConversionOutcome.Cancelled
                is ExecutionResult.Failed ->
                    ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "exit code ${result.returnCode}")
            }
        } finally {
            // Runs on success, failure and cancellation alike.
            session.close()
            if (!succeeded) {
                partialOutput?.delete()
                packedPlan?.let { discardParts(it, outputDirectory.outputDirectory()) }
            }
        }
    }

    /**
     * Joins the gain map of [hdr] to the JPG at [base], into [target]; when the
     * gain map would change nothing (or cannot be made) the JPG is the result.
     * False when not even the JPG can be written.
     */
    private fun writeUltraHdr(
        job: ConversionJob,
        hdr: ImageHdr,
        base: String,
        pictures: HdrPictures,
        session: StagingSession,
        target: String,
        onEvent: (ConversionEvent) -> Unit,
    ): Boolean {
        val request = job.request
        val segment = request.segments.first()
        val source = request.sources[segment.source]
        val gainMapJpg = session.scratchFile("hdr_gainmap.jpg")
        val metadata = try {
            hdrMaker?.make(
                HdrJob(
                    hdr = hdr,
                    edit = request.image,
                    effects = request.effects,
                    sourceUri = job.sources.inputs[segment.source].uri,
                    sourceWidth = source.width,
                    sourceHeight = source.height,
                    finalPng = pictures.finalPng,
                    plainPng = pictures.plainPng,
                    gainMapJpg = gainMapJpg,
                ),
            )
        } catch (e: Exception) {
            onEvent(ConversionEvent.LogLine("HDR gain map not made: ${e.message}"))
            null
        }
        return try {
            val picture = File(base).readBytes()
            val bytes = if (metadata != null) {
                try {
                    UltraHdr.assemble(picture, File(gainMapJpg).readBytes(), metadata).also {
                        onEvent(ConversionEvent.LogLine(String.format(java.util.Locale.ROOT, "Ultra HDR: up to %.2f stops brighter", metadata.maxStops)))
                    }
                } catch (e: RuntimeException) {
                    onEvent(ConversionEvent.LogLine("Ultra HDR not written (${e.message}): a plain JPG instead"))
                    picture
                }
            } else {
                onEvent(ConversionEvent.LogLine("Ultra HDR: nothing to make brighter, written as a plain JPG"))
                picture
            }
            File(target).writeBytes(bytes)
            true
        } catch (e: IOException) {
            onEvent(ConversionEvent.LogLine("The picture could not be written: ${e.message}"))
            false
        }
    }

    /**
     * Packs the numbered files of [plan] into its zip, so that the result is one
     * file to share or save; the folder they were written to goes. Null when it
     * worked, else the failure.
     */
    private suspend fun pack(plan: OutputPlan, directory: String, onEvent: (ConversionEvent) -> Unit): ConversionOutcome.Failure? {
        val parts = PartsArchive.parts(plan.commandPath)
        if (parts.isEmpty()) return ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "no files written")
        onEvent(ConversionEvent.StageChanged(ConversionStage.PACKING))
        val context = currentCoroutineContext()
        val archive = File(plan.resultPath)
        try {
            PartsArchive.pack(parts, archive) { context.ensureActive() }
        } catch (e: IOException) {
            return ConversionOutcome.Failure(FailureReason.PACKING_FAILED, e.message.orEmpty())
        }
        removeFolderIfEmpty(File(plan.commandPath).parentFile, directory)
        onEvent(ConversionEvent.LogLine("Packed ${parts.size} files into ${archive.name}"))
        return null
    }

    /** What a numbered output that did not make it leaves: its files, its zip, its folder. */
    private fun discardParts(plan: OutputPlan, directory: String) {
        PartsArchive.parts(plan.commandPath).forEach { it.delete() }
        File(plan.resultPath).delete()
        removeFolderIfEmpty(File(plan.commandPath).parentFile, directory)
    }

    /** Removes [folder] when nothing is left in it, unless it is the output directory itself. */
    private fun removeFolderIfEmpty(folder: File?, directory: String) {
        if (folder == null || folder.absoluteFile == File(directory).absoluteFile) return
        if (folder.listFiles()?.isEmpty() == true) folder.delete()
    }

    private class Prepared(val command: FfmpegCommand, val plan: OutputPlan)

    private sealed interface Staging {
        /** [paths] has every slot filled except the output. */
        class Ready(val paths: MediaPaths) : Staging
        class Unreadable(val name: String) : Staging
    }

    private fun stageSources(job: ConversionJob, session: StagingSession): Staging {
        val request = job.request
        val sources = job.sources

        val inputs = sources.inputs.map { input ->
            session.stage(input.uri, input.name) ?: return Staging.Unreadable(input.name)
        }

        // Remuxing several segments goes through the concat demuxer, which reads a list.
        var concatList: String? = null
        if (request.encoding.streamCopy && request.isMultiSegment) {
            val entries = request.segments.map { segment ->
                ConcatEntry(inputs[segment.source], inpointMs = segment.startMs, outpointMs = segment.endMs)
            }
            concatList = session.scratchFile("concat_list.txt")
            File(concatList).writeText(renderConcatList(entries))
        }

        val overlays = sources.imageOverlays.map { overlay ->
            session.stage(overlay.uri, overlay.name) ?: return Staging.Unreadable(overlay.name)
        }
        val sounds = sources.sounds.map { sound -> session.stage(sound.uri, sound.name) ?: return Staging.Unreadable(sound.name) }
        val font = if (request.burnsSubtitles && request.subtitleStyle != null) subtitleFont(request, session) else null
        val subtitles = when {
            request.hasSpokenSubtitles -> spokenSubtitles(request, session, font)
            else -> sources.subtitles?.let { subtitle ->
                val staged = session.stage(subtitle.uri, subtitle.name) ?: return Staging.Unreadable(subtitle.name)
                retimedSubtitles(staged, job, session, font)
            }
        }
        val transforms = if (request.stabilization != null) session.scratchFile("transforms.trf") else null
        // The cover: the picture itself, or a tags file holding it (see CoverWriting).
        val writing = request.coverWriting()
        val cover = sources.cover?.takeIf { writing != null }?.let { picture ->
            session.stage(picture.uri, picture.name) ?: return Staging.Unreadable(picture.name)
        }
        val coverTags = if (writing == CoverWriting.PICTURE_TAG && cover != null) {
            session.scratchFile("cover_tags.txt").also { File(it).writeText(CoverTags.ffmetadata(File(cover).readBytes())) }
        } else {
            null
        }

        // The HDR look's LUT, worked out for its intensity (see HdrVideoLook).
        val hdrLut = request.effects.hdrLook?.let { intensity ->
            session.scratchFile("hdr_look.cube").also { File(it).writeText(HdrVideoLook.cube(intensity)) }
        }

        return Staging.Ready(
            MediaPaths(
                inputs = inputs,
                output = "",
                concatList = concatList,
                sounds = sounds,
                imageOverlays = overlays,
                subtitles = subtitles,
                subtitleFonts = font?.folder?.takeIf { subtitles != null },
                stabilizationTransforms = transforms,
                cover = cover.takeIf { writing == CoverWriting.ATTACHED_PICTURE },
                coverTags = coverTags,
                hdrLut = hdrLut,
            ),
        )
    }

    /**
     * Rewrites the subtitle file so that its lines follow the footage through
     * the edit. A SubRip file with a style becomes a styled script, drawn as the
     * preview draws it. Returns null when no line is left to show.
     */
    private fun retimedSubtitles(stagedPath: String, job: ConversionJob, session: StagingSession, font: StagedFont?): String? {
        val request = job.request
        val sourceIndex = request.subtitleSource ?: return null
        val file = File(stagedPath)
        if (file.length() > MAX_SUBTITLE_BYTES) return null
        val content = file.readText()
        val style = request.subtitleStyle
        if (style != null && !SubtitleRetimer.isAss(content)) {
            val cues = subtitleParser.parse(content).onTimeline(request.segments, sourceIndex, request.effects.reversed)
            val ratio = request.subtitleFrameRatio
            return styledScript(SubtitleLayout.ofCues(cues, style, ratio, font?.facts), request, session, font)
        }
        val retimed = subtitleRetimer.retime(
            content = content,
            segments = request.segments,
            sourceIndex = sourceIndex,
            reversed = request.effects.reversed,
        ) ?: return null
        val target = session.scratchFile("subtitles_retimed.${retimed.extension}")
        File(target).writeText(retimed.content)
        return target
    }

    /** The words heard, moved onto the edit and written as a styled script; null when none is left. */
    private fun spokenSubtitles(request: ConversionRequest, session: StagingSession, font: StagedFont?): String? {
        val style = request.subtitleStyle ?: return null
        val words = request.timedWords()
        return styledScript(SubtitleLayout.ofWords(words, style, request.subtitleFrameRatio, font?.facts), request, session, font)
    }

    private fun styledScript(blocks: List<SubtitleBlock>, request: ConversionRequest, session: StagingSession, font: StagedFont?): String? {
        val style = request.subtitleStyle ?: return null
        if (blocks.isEmpty()) return null
        val script = AssSubtitles.write(blocks, style, request.subtitleFrameRatio, font?.facts?.family ?: FALLBACK_FAMILY)
        val target = session.scratchFile("subtitles_styled.ass")
        File(target).writeText(script)
        return target
    }

    /** The font the styled subtitles name ([facts]), copied alone into a [folder] for libass. */
    private class StagedFont(val facts: FontFacts, val folder: String?)

    private fun subtitleFont(request: ConversionRequest, session: StagingSession): StagedFont? {
        val provider = fonts ?: return null
        val style = request.subtitleStyle ?: return null
        val bold = style.bold
        val main = File(provider.fontPath(style.font, bold))
        val facts = FontFacts.read(main) ?: return null
        val folder = File(session.scratchFile("fonts"))
        return try {
            folder.mkdirs()
            // Both weights when they are separate files, so that libass finds the one the style asks for.
            listOf(main, File(provider.fontPath(style.font, !bold))).distinctBy { it.absolutePath }.forEach { source ->
                if (source.isFile && FontFacts.read(source)?.family == facts.family) source.copyTo(File(folder, source.name), overwrite = true)
            }
            StagedFont(facts, folder.absolutePath)
        } catch (e: IOException) {
            StagedFont(facts, null)
        }
    }

    private companion object {
        const val MAX_SUBTITLE_BYTES = 5L * 1024 * 1024

        /** Asked for when the font is not known; libass then takes what fontconfig offers. */
        const val FALLBACK_FAMILY = "sans-serif"
    }
}
