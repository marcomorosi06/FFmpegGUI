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
import com.cuscus.ffmpeggui.core.provenance.AiDisclosure
import com.cuscus.ffmpeggui.core.provenance.AiSeal
import com.cuscus.ffmpeggui.core.provenance.SoundSeal
import com.cuscus.ffmpeggui.core.provenance.withAiNote
import com.cuscus.ffmpeggui.core.provenance.Xmp
import com.cuscus.ffmpeggui.core.provenance.aiDisclosure
import com.cuscus.ffmpeggui.core.provenance.aiSources
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
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import com.cuscus.ffmpeggui.core.vector.VectorTracer

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
    /** The font text art is chosen with; without it ASCII and ANSI art cannot be written. */
    private val glyphs: GlyphAtlasSource? = null,
    /** Marks a finished file's whole sound when a dub is heard in it (see SoundSeal); without it only the dub carries the mark. */
    private val soundSealer: SoundSeal.SoundSealer? = null,
) {

    /**
     * Runs [command] with its log joined into whole lines (FFmpeg hands it over in
     * pieces), each sent on as a [ConversionEvent.LogLine] and read for the
     * progress against [expectedMs]; notes in [full] when the storage ran out.
     */
    private suspend fun logged(command: FfmpegCommand, expectedMs: Long?, full: StorageFull, onEvent: (ConversionEvent) -> Unit): ExecutionResult {
        val lines = LogLineJoiner { line ->
            if (StorageFull.saysSo(line)) full.seen = true
            onEvent(ConversionEvent.LogLine(line))
            progressParser.fraction(line, expectedMs)?.let { onEvent(ConversionEvent.Progress(it)) }
        }
        return try {
            executor.execute(command) { lines.feed(it) }
        } finally {
            lines.flush()
        }
    }

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
        // Set when FFmpeg says the phone's storage is full, whichever pass it was.
        val full = StorageFull()
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
                when (logged(analysis, null, full, onEvent)) {
                    ExecutionResult.Success -> Unit
                    ExecutionResult.Cancelled -> return ConversionOutcome.Cancelled
                    is ExecutionResult.Failed -> return full.or(FailureReason.ANALYSIS_FAILED)
                }
            }

            // The compressor fitting a size: x265's first pass measures the video.
            if (job.mode == CommandMode.Generated && commandBuilder.needsFirstPass(request)) {
                onEvent(ConversionEvent.StageChanged(ConversionStage.ANALYZING))
                val firstPass = commandBuilder.buildFirstPass(request, paths)
                onEvent(ConversionEvent.LogLine(firstPass.toDisplayString()))
                val expected = request.progressDurationMs()
                when (logged(firstPass, expected, full, onEvent)) {
                    ExecutionResult.Success -> onEvent(ConversionEvent.Progress(0f))
                    ExecutionResult.Cancelled -> return ConversionOutcome.Cancelled
                    is ExecutionResult.Failed -> return full.or(FailureReason.ANALYSIS_FAILED)
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
                    // The extra files first: a name of the project itself wins over one of theirs.
                    val extra = mode.files.associate { file ->
                        file.alias to (session.stage(file.uri, file.name) ?: return ConversionOutcome.Failure(FailureReason.SOURCE_UNREADABLE, file.name))
                    }
                    val aliases = extra + MediaPaths.aliasesFor(request).inputAliasesTo(paths)
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
                logged(command, expectedMs, full, onEvent)
            }
            if (hardware) onEvent(ConversionEvent.Encoder(hardware = true))
            var result = encode(prepared.command)
            var encoder = if (hardware) VideoEncoderUse.HARDWARE else VideoEncoderUse.NONE
            // The phone's encoder refused (a size, a format it does not take, a busy chip):
            // the same file again, from the start, with x264.
            // (Not when the storage is full: x264 would not find more room.)
            if (hardware && result is ExecutionResult.Failed && !full.seen) {
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
            // The compressor fitting a JPG into a size: the best quality that fits, found by halves.
            if (result == ExecutionResult.Success) {
                fitted(job, paths.copy(output = plan.commandPath), session, encode)?.let { result = it }
            }

            return when (result) {
                ExecutionResult.Success -> {
                    if (intermediate != null) {
                        val written = when {
                            request.format == OutputFormat.SVG -> {
                                onEvent(ConversionEvent.LogLine("Tracing the picture into shapes"))
                                traced(intermediate, prepared.plan.commandPath, request.image.trace)
                            }
                            request.format.isTextArt -> {
                                onEvent(ConversionEvent.LogLine("Writing the picture as text"))
                                textArt(intermediate, prepared.plan.commandPath, request)
                            }
                            else -> imageRecoder?.recode(intermediate, prepared.plan.commandPath, request.format, request.image.output.quality) ?: false
                        }
                        if (!written) return ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "no ${request.format.extension} encoder")
                    }
                    // FFmpeg writes no tags into a picture: they are added to the finished file
                    // (to the JPG of an Ultra HDR picture, before the gain map is joined to it).
                    val tagPath = hdrBase ?: prepared.plan.commandPath
                    // A picture a model changed says so in its EXIF too, whatever the user's own tags.
                    val pictureTags = request.metadata.withAiNote(request.aiDisclosure())
                    if (request.format.isImage && !request.format.isTextArt && onePicture && job.mode == CommandMode.Generated && !pictureTags.isEmpty) {
                        val tagged = pictureTagger?.write(tagPath, request.format, pictureTags) ?: false
                        if (!tagged) onEvent(ConversionEvent.LogLine("Tags not written: ${request.format.extension} cannot hold them here"))
                    }
                    // Last of all on each file: nothing after may drop the marks (the Ultra HDR
                    // picture keeps its JPG's, see UltraHdr.assemble).
                    val sealed = if (prepared.plan.packed) PartsArchive.parts(prepared.plan.commandPath).map(File::getPath) else listOf(tagPath)
                    seal(job, sealed, session, full, onEvent)?.let { return it }
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
                    full.or(FailureReason.ENCODING_FAILED, "exit code ${result.returnCode}")
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
                    UltraHdr.assemble(picture, File(gainMapJpg).readBytes(), metadata, request.aiDisclosure()).also {
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
     * Writes the AI marks into each of [files] (see core.provenance and
     * Regulation (EU) 2024/1689, Article 50(2)): after FFmpeg, so neither the
     * metadata settings nor a command typed by hand can leave them out. For
     * such a command the container's tags are written again too (a stream
     * copy), since the command may have dropped them. Null when every file
     * says what machines made in it (or nothing was made by one), else why
     * not: such a file is never handed out without them.
     */
    private suspend fun seal(
        job: ConversionJob,
        files: List<String>,
        session: StagingSession,
        full: StorageFull,
        onEvent: (ConversionEvent) -> Unit,
    ): ConversionOutcome? {
        val manual = job.mode is CommandMode.Manual
        val all = if (manual) job.request.aiSources() else job.request.aiDisclosure()
        if (all.isEmpty) return null
        if (files.isEmpty()) return ConversionOutcome.Failure(FailureReason.ENCODING_FAILED, "no files written")
        for ((index, path) in files.withIndex()) {
            currentCoroutineContext().ensureActive()
            val disclosure = AiSeal.disclosureFor(path, all)
            if (disclosure.isEmpty) continue
            val way = AiSeal.wayFor(path)
            if (way == AiSeal.Way.NONE) return ConversionOutcome.Failure(FailureReason.AI_MARKS_NO_PLACE, path.substringAfterLast('.', "").lowercase())
            // A dub mixed with the rest: the whole sound gets AudioSeal's mark, before the tags.
            if (disclosure.speech) sealSound(job, path, index, session, full, onEvent)?.let { return it }
            if (manual && (way == AiSeal.Way.TAGS || way == AiSeal.Way.MP4)) {
                val copy = session.scratchFile("marked_$index.${path.substringAfterLast('.')}")
                val command = AiSeal.remux(path, copy, disclosure)
                onEvent(ConversionEvent.LogLine(command.toDisplayString()))
                when (val result = logged(command, null, full, onEvent)) {
                    ExecutionResult.Success -> try {
                        File(copy).copyTo(File(path), overwrite = true)
                    } catch (e: IOException) {
                        return ConversionOutcome.Failure(FailureReason.AI_MARKS_FAILED, e.message.orEmpty())
                    } finally {
                        File(copy).delete()
                    }
                    ExecutionResult.Cancelled -> return ConversionOutcome.Cancelled
                    is ExecutionResult.Failed -> {
                        File(copy).delete()
                        if (full.seen) return full.or(FailureReason.AI_MARKS_FAILED, "")
                        // Without the tags only a format whose marks are its tags is left unmarked.
                        if (way == AiSeal.Way.TAGS) return ConversionOutcome.Failure(FailureReason.AI_MARKS_FAILED, "exit code ${result.returnCode}")
                        onEvent(ConversionEvent.LogLine("AI tags not written again (exit code ${result.returnCode}): the XMP says it"))
                    }
                }
            }
            val sourceType = AiDisclosure.COMPOSITE
            when (AiSeal.mark(File(path), disclosure, sourceType)) {
                AiSeal.Marked.WRITTEN, AiSeal.Marked.TAGS_ONLY -> Unit
                AiSeal.Marked.NO_PLACE -> return ConversionOutcome.Failure(FailureReason.AI_MARKS_NO_PLACE, path.substringAfterLast('.', "").lowercase())
                AiSeal.Marked.FAILED -> return ConversionOutcome.Failure(FailureReason.AI_MARKS_FAILED, File(path).name)
            }
        }
        onEvent(ConversionEvent.LogLine("Marked as made with AI (EU AI Act, Article 50(2)): ${all.text}"))
        return null
    }

    /**
     * The whole sound of the file at [path] marked by [soundSealer] (see
     * SoundSeal): taken out, marked, put back in place of the first sound,
     * everything else copied. Best effort: the dub in it carries its own mark
     * already, so a file this cannot do (no sound, a format it does not know, a
     * failure) is kept as it is, said in the log. Null but when stopped, or the
     * storage is full.
     */
    private suspend fun sealSound(
        job: ConversionJob,
        path: String,
        index: Int,
        session: StagingSession,
        full: StorageFull,
        onEvent: (ConversionEvent) -> Unit,
    ): ConversionOutcome? {
        val sealer = soundSealer ?: return null
        val extension = path.substringAfterLast('.', "").lowercase()
        val codec = SoundSeal.codecFor(extension, job.request.encoding.audio)
        if (codec == null) {
            onEvent(ConversionEvent.LogLine("Sound not marked again: .$extension"))
            return null
        }
        val wav = session.scratchFile("sound_$index.wav")
        val marked = session.scratchFile("sound_marked_$index.wav")
        val again = session.scratchFile("sound_sealed_$index.$extension")
        try {
            when (logged(SoundSeal.decode(path, wav), null, full, onEvent)) {
                ExecutionResult.Success -> Unit
                ExecutionResult.Cancelled -> return ConversionOutcome.Cancelled
                is ExecutionResult.Failed -> {
                    if (full.seen) return full.or(FailureReason.AI_MARKS_FAILED, "")
                    onEvent(ConversionEvent.LogLine("No sound to mark in ${File(path).name}"))
                    return null
                }
            }
            // A stage of its own, with its progress: it takes a while (about a second per 10 s of sound).
            onEvent(ConversionEvent.StageChanged(ConversionStage.MARKING))
            onEvent(ConversionEvent.Progress(0f))
            try {
                sealer.seal(File(wav), File(marked)) { fraction -> onEvent(ConversionEvent.Progress(fraction.coerceIn(0f, 1f))) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onEvent(ConversionEvent.LogLine("Sound not marked again (${e.message}): the dub keeps its own mark"))
                return null
            }
            when (val result = logged(SoundSeal.remux(path, marked, again, codec), null, full, onEvent)) {
                ExecutionResult.Success -> try {
                    File(again).copyTo(File(path), overwrite = true)
                    onEvent(ConversionEvent.LogLine("Whole sound marked with AudioSeal"))
                } catch (e: IOException) {
                    return ConversionOutcome.Failure(FailureReason.AI_MARKS_FAILED, e.message.orEmpty())
                }
                ExecutionResult.Cancelled -> return ConversionOutcome.Cancelled
                is ExecutionResult.Failed -> {
                    if (full.seen) return full.or(FailureReason.AI_MARKS_FAILED, "")
                    onEvent(ConversionEvent.LogLine("Sound not marked again (exit code ${result.returnCode}): the dub keeps its own mark"))
                }
            }
            return null
        } finally {
            File(wav).delete()
            File(marked).delete()
            File(again).delete()
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

    /**
     * A JPG the compressor must fit into its size ([com.cuscus.ffmpeggui.core.model.Compression.targetBytes]),
     * already written at [paths]' output: when it is larger, it is made again at
     * the quality halfway between the last that fitted and the last that did
     * not, [FIT_STEPS] times, and the best one that fits is kept (the smallest
     * one when none does). Returns the last result when it ran, null when it
     * had nothing to do.
     */
    private suspend fun fitted(
        job: ConversionJob,
        paths: MediaPaths,
        session: StagingSession,
        encode: suspend (FfmpegCommand) -> ExecutionResult,
    ): ExecutionResult? {
        val request = job.request
        val asked = request.compression?.targetBytes?.takeIf { it > 0L } ?: return null
        if (job.mode != CommandMode.Generated || request.format != OutputFormat.JPG || request.image.hdr != null) return null
        // Room for the AI marks written afterwards (see seal): the file still fits once they are in.
        val disclosure = request.aiDisclosure()
        val target = if (disclosure.isEmpty) asked else (asked - Xmp.packet(disclosure).toByteArray(Charsets.UTF_8).size - MARK_SEGMENT_HEADER).coerceAtLeast(1L)
        val output = File(paths.output)
        if (!output.exists() || output.length() <= target) return null
        val best = File(session.scratchFile("fitted.jpg"))
        val smallest = File(session.scratchFile("smallest.jpg"))
        output.copyTo(smallest, overwrite = true)
        var fits = false
        var low = 0f
        var high = request.image.output.quality
        var last: ExecutionResult = ExecutionResult.Success
        repeat(FIT_STEPS) {
            currentCoroutineContext().ensureActive()
            val quality = (low + high) / 2f
            val again = request.copy(image = request.image.copy(output = request.image.output.copy(quality = quality)))
            last = encode(commandBuilder.build(again, paths))
            if (last != ExecutionResult.Success) return last
            if (output.length() <= target) {
                output.copyTo(best, overwrite = true)
                fits = true
                low = quality
            } else {
                if (output.length() < smallest.length()) output.copyTo(smallest, overwrite = true)
                high = quality
            }
        }
        (if (fits) best else smallest).copyTo(output, overwrite = true)
        return last
    }

    /**
     * The PNG FFmpeg made at [pngPath] traced into shapes as [settings] say
     * (see [VectorTracer]) and written as an SVG to [targetPath], as large as
     * the picture; false when it cannot be read or written.
     */
    private fun traced(pngPath: String, targetPath: String, settings: TraceSettings): Boolean {
        val raster = imageRecoder?.read(pngPath) ?: return false
        return try {
            File(targetPath).writeText(VectorTracer.trace(raster, settings).toSvg(raster.width, raster.height))
            true
        } catch (e: IOException) {
            false
        }
    }

    /** The PNG at [pngPath] written as the ASCII or ANSI art [request] asks for, at [targetPath]. */
    private fun textArt(pngPath: String, targetPath: String, request: ConversionRequest): Boolean {
        val raster = imageRecoder?.read(pngPath) ?: return false
        val atlas = glyphs?.atlas() ?: return false
        val settings = request.image.textArt
        val ansi = request.format == OutputFormat.ANSI
        val cp437 = ansi && settings.layout.encoding == com.cuscus.ffmpeggui.core.textart.ArtEncoding.CP437
        val art = com.cuscus.ffmpeggui.core.textart.TextArtLayout.compose(
            com.cuscus.ffmpeggui.core.textart.TextArtRenderer.render(raster, settings, atlas, ansi),
            settings.layout,
            cp437,
        )
        return try {
            File(targetPath).writeBytes(com.cuscus.ffmpeggui.core.textart.TextArtWriter.bytes(art, settings, ansi))
            true
        } catch (e: IOException) {
            false
        }
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
        // The compressor's two x265 passes share their stats.
        val passLog = if (commandBuilder.needsFirstPass(request)) session.scratchFile("x265_2pass.log") else null
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
                passLog = passLog,
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
        // The user's own file while it can be read (as a caption's), else the family's.
        val own = style.fontFile?.let(provider::customFontPath)?.let(::File)?.takeIf { it.isFile && FontFacts.read(it) != null }
        val main = own ?: File(provider.fontPath(style.font, bold))
        val facts = FontFacts.read(main) ?: return null
        val folder = File(session.scratchFile("fonts"))
        return try {
            folder.mkdirs()
            // Both weights when they are separate files, so that libass finds the one the style asks for.
            (if (own != null) listOf(own) else listOf(main, File(provider.fontPath(style.font, !bold)))).distinctBy { it.absolutePath }.forEach { source ->
                if (source.isFile && FontFacts.read(source)?.family == facts.family) source.copyTo(File(folder, source.name), overwrite = true)
            }
            StagedFont(facts, folder.absolutePath)
        } catch (e: IOException) {
            StagedFont(facts, null)
        }
    }

    private companion object {
        /** Halvings of the quality range when a JPG must fit a size (the JPG scale has 30 steps). */
        const val FIT_STEPS = 6

        /** An APP1 segment's marker, length and XMP namespace, around the AI marks' packet. */
        const val MARK_SEGMENT_HEADER = 4 + 29

        const val MAX_SUBTITLE_BYTES = 5L * 1024 * 1024

        /** Asked for when the font is not known; libass then takes what fontconfig offers. */
        const val FALLBACK_FAMILY = "sans-serif"
    }
}
