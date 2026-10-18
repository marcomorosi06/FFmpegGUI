package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.audio.STEREO_UPMIX
import com.cuscus.ffmpeggui.core.image.borderPixels
import com.cuscus.ffmpeggui.core.image.editedSize
import com.cuscus.ffmpeggui.core.image.pictureFrameMs
import com.cuscus.ffmpeggui.core.image.imageStages
import com.cuscus.ffmpeggui.core.model.TransitionKind
import com.cuscus.ffmpeggui.core.model.stackOrder
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.sequenceFormat
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.MotionKind
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.VideoHdr
import com.cuscus.ffmpeggui.core.time.Segmenting
import com.cuscus.ffmpeggui.core.time.expectedOutputDurationMs
import com.cuscus.ffmpeggui.core.time.footageLengthMs
import com.cuscus.ffmpeggui.core.time.transitionsMs
import com.cuscus.ffmpeggui.core.time.formatTimecode
import com.cuscus.ffmpeggui.core.time.playedLengthMs
import com.cuscus.ffmpeggui.core.time.segmenting
import kotlin.math.abs

/**
 * Translates a [ConversionRequest] plus its [MediaPaths] into an FFmpeg command.
 *
 * The builder is pure: the same request yields the command preview when paired
 * with [MediaPaths.aliasesFor] and the real command when paired with staged
 * paths, so the two can never drift apart.
 *
 * Footage made of several segments is assembled frame-accurately: every segment
 * is its own seeked input, goes through its own effects, and the concat filter
 * joins them, after which the rest of the chain treats the result as one video.
 *
 * Hand-written commands are not handled here; see [ManualCommandResolver].
 */
class CommandBuilder(fonts: FontProvider) {

    private val videoFilters = VideoFilters(fonts)
    private val audioFilters = AudioFilters()

    fun build(request: ConversionRequest, paths: MediaPaths): FfmpegCommand {
        return buildFor(request.withStillsForward(), paths)
    }

    private fun buildFor(request: ConversionRequest, paths: MediaPaths): FfmpegCommand {
        require(request.format != OutputFormat.CUSTOM || request.frameExtraction != FrameExtraction.Disabled) {
            "The custom format requires a hand-written command"
        }
        require(paths.inputs.size == request.sources.size) {
            "Expected ${request.sources.size} input paths, got ${paths.inputs.size}"
        }
        if (request.format.isImage && request.frameExtraction == FrameExtraction.Disabled) {
            return FfmpegCommand(imageCommand(request, paths))
        }
        val args = when (val extraction = request.frameExtraction) {
            is FrameExtraction.SingleFrame -> singleFrame(extraction, request, paths)
            is FrameExtraction.Sequence -> frameSequence(extraction, request, paths)
            FrameExtraction.Disabled ->
                // A still has no stream to copy: with one in the sequence everything is encoded.
                if (request.encoding.streamCopy && request.sources.none { it.isStill }) {
                    streamCopy(request, paths)
                } else {
                    transcode(request, paths)
                }
        }
        return FfmpegCommand(args)
    }

    /**
     * This request with every still played forwards. A still looks the same either
     * way, and reversing it would only make FFmpeg hold all its frames in memory.
     */
    private fun ConversionRequest.withStillsForward(): ConversionRequest {
        if (segments.none { it.effects.reversed && sources.getOrNull(it.source)?.isStill == true }) return this
        return copy(
            segments = segments.map { segment ->
                if (sources.getOrNull(segment.source)?.isStill == true) {
                    segment.copy(effects = segment.effects.copy(reversed = false))
                } else {
                    segment
                }
            },
        )
    }

    /**
     * True when [build] has the phone's video encoder make the picture: one given
     * ([com.cuscus.ffmpeggui.core.model.EncodingSettings.hardwareEncoder]), an MP4 or
     * MKV that is encoded (not remuxed, not frames), and not cut into parts (the
     * parts need key frames exactly at the cuts, which only x264 places).
     */
    fun usesHardwareEncoder(request: ConversionRequest): Boolean =
        request.encoding.hardwareEncoder != null &&
            (request.format == OutputFormat.MP4 || request.format == OutputFormat.MKV) &&
            !(request.encoding.streamCopy && request.sources.none { it.isStill }) &&
            request.frameExtraction == FrameExtraction.Disabled &&
            request.segmenting() == null &&
            // An HDR video is encoded by x265, in 10 bits.
            !hdrOutput(request)

    /** The same request encoded by x264: what the engine falls back on when the phone's encoder fails. */
    fun withoutHardwareEncoder(request: ConversionRequest): ConversionRequest =
        request.copy(encoding = request.encoding.copy(hardwareEncoder = null))

    /**
     * The bitrate for the phone's encoder: the first clip's frame, at the chosen
     * height when there is one, at the chosen frame rate (30 when kept).
     */
    private fun hardwareKbps(request: ConversionRequest): Int {
        val frame = request.segments.firstOrNull()?.let { request.sources.getOrNull(it.source) } ?: request.sources.firstOrNull()
        var width = frame?.width ?: 0
        var height = frame?.height ?: 0
        val target = request.encoding.resolution.height
        if (target > 0 && width > 0 && height > 0) {
            width = (width.toLong() * target / height).toInt()
            height = target
        }
        val fps = request.encoding.framerate.value.takeIf { it > 0 } ?: DEFAULT_HARDWARE_FPS
        return hardwareVideoKbps(width, height, fps, request.encoding.quality)
    }

    /**
     * The picture's sides brought to the nearest multiples [encoder] reads frames
     * at without padding of its own (see [HardwareEncoder]): at other sizes it would
     * tear the picture into coloured streaks. Scaling keeps the shape (the pixel
     * aspect ratio makes up for the few pixels), and an aligned size is left as it is.
     */
    private fun hardwareAlignment(encoder: HardwareEncoder): String {
        val w = encoder.widthAlign.coerceAtLeast(2)
        val h = encoder.heightAlign.coerceAtLeast(2)
        return "scale=trunc(iw/$w+0.5)*$w:trunc(ih/$h+0.5)*$h"
    }

    /** True when [buildStabilizationAnalysis] must run before [build]. */
    fun needsStabilizationAnalysis(request: ConversionRequest): Boolean =
        request.stabilization != null &&
            request.format.hasVideo &&
            !request.encoding.streamCopy &&
            request.frameExtraction == FrameExtraction.Disabled

    /**
     * First pass of vid.stab: analyses the same frames the second pass will see
     * and writes the transforms file. Produces no media output.
     */
    fun buildStabilizationAnalysis(request: ConversionRequest, paths: MediaPaths): FfmpegCommand {
        val stabilization = requireNotNull(request.stabilization) { "Stabilization is not requested" }
        val transforms = requireNotNull(paths.stabilizationTransforms) { "Missing transforms path" }
        val detect = "vidstabdetect=shakiness=${stabilization.shakiness.coerceIn(1, 10)}" +
            ":accuracy=15:result=${filterOptionValue(transforms)}"

        // The analysis must see the frames the second pass will see: the joined clips,
        // each with its own effects (and pictures), before anything of the whole video.
        val footage = footage(
            request, paths,
            withVideo = true, withAudio = false, clipEffects = true,
            withOverlays = request.imageOverlays.any { it.clipId != null },
        )
        val args = mutableListOf("-y")
        args += footage.inputArguments
        if (footage.graph.isEmpty()) {
            args += listOf("-vf", detect)
        } else {
            val graph = footage.graph + "[${footage.video}]$detect[$VIDEO_OUT]"
            args += listOf("-filter_complex", graph.joinToString(";"), "-map", "[$VIDEO_OUT]")
        }
        args += listOf("-f", "null", "-")
        return FfmpegCommand(args)
    }

    // ---------------------------------------------------------------------
    // Operating modes
    // ---------------------------------------------------------------------

    private fun singleFrame(
        extraction: FrameExtraction.SingleFrame,
        request: ConversionRequest,
        paths: MediaPaths,
    ): List<String> {
        // Find the segment that is on screen at the requested instant.
        var remaining = extraction.positionMs.coerceAtLeast(0L)
        var segment = request.segments.first()
        for ((index, candidate) in request.segments.withIndex()) {
            segment = candidate
            val length = candidate.durationMs
            if (length == null || remaining < length || index == request.segments.lastIndex) break
            remaining -= length
        }
        segment.durationMs?.let { remaining = remaining.coerceAtMost(it) }
        val sourcePosition = segment.startMs + remaining

        val args = mutableListOf("-y")
        // A still is the same picture all along: there is nothing to seek.
        if (sourcePosition > 0 && !request.sources[segment.source].isStill) {
            args += listOf("-ss", formatTimecode(sourcePosition))
        }
        args += listOf("-i", paths.inputs[segment.source])
        // HDR footage is tone mapped (see VideoHdr), as for every other output.
        val toneMap = toneMapFilters(request.sources[segment.source].hdr, "yuvj444p")
        if (toneMap.isNotEmpty()) args += listOf("-vf", toneMap.joinToString(","))
        args += listOf("-frames:v", "1", "-q:v", "2", paths.output)
        return args
    }

    private fun frameSequence(
        extraction: FrameExtraction.Sequence,
        request: ConversionRequest,
        paths: MediaPaths,
    ): List<String> {
        // Only the part asked for, on the clock of the edited sequence.
        val start = extraction.startMs.coerceAtLeast(0L)
        val end = extraction.endMs?.takeIf { it > start }
        val range = if (start > 0L || end != null) {
            "trim=" + listOfNotNull(
                fmt("start=%.3f", start / 1000.0).takeIf { start > 0L },
                end?.let { fmt("end=%.3f", it / 1000.0) },
            ).joinToString(":") + ",setpts=PTS-STARTPTS,"
        } else {
            ""
        }
        val sample = range + fmt("fps=%.4f", extraction.framesPerSecond)
        // Frame extraction saves the footage as it is, without effects.
        val footage = footage(request, paths, withVideo = true, withAudio = false, clipEffects = false, withOverlays = false)
        val args = mutableListOf("-y")
        args += footage.inputArguments
        if (footage.graph.isEmpty()) {
            args += listOf("-vf", sample)
        } else {
            val graph = footage.graph + "[${footage.video}]$sample[$VIDEO_OUT]"
            args += listOf("-filter_complex", graph.joinToString(";"), "-map", "[$VIDEO_OUT]")
        }
        // In the picture format asked for (a picture's quality when one was chosen).
        val format = request.sequenceFormat()
        args += if (format == OutputFormat.PNG && !request.format.isImage) {
            listOf("-q:v", "2")
        } else {
            imageEncoderArguments(format, request.image.output.quality)
        }
        args += paths.output
        return args
    }

    private fun streamCopy(request: ConversionRequest, paths: MediaPaths): List<String> {
        val args = mutableListOf("-y")
        if (request.isMultiSegment) {
            // Filters cannot run without re-encoding, so the segments are joined by the
            // concat demuxer; its cuts fall on key frames. Per-segment trims live in the list.
            val list = requireNotNull(paths.concatList) { "Several segments to remux but no concat list was provided" }
            args += listOf("-f", "concat", "-safe", "0", "-i", list)
        } else {
            args += seekedInput(request.segments.single(), request, paths, limitWithDuration = false)
        }
        // The cover comes after the footage, the only other input.
        val cover = request.coverWriting()
        args += coverInput(cover, paths)
        args += listOf("-c", "copy")
        when {
            cover == CoverWriting.ATTACHED_PICTURE -> args += listOf("-map", "0:a", "-map", "1:v")
            // An audio file keeps only the sound: no video, nor the cover the source may have.
            request.format.isAudioOnly -> args += "-vn"
        }
        args += coverArguments(cover, coverInput = 1, request.format, copyPicture = false)
        args += metadata(request)
        args += segmenting(request, forceKeyFrames = false)
        args += paths.output
        return args
    }

    private fun transcode(request: ConversionRequest, paths: MediaPaths): List<String> {
        val format = request.format
        val outputSeconds = request.expectedOutputDurationMs()?.let { it / 1000.0 }

        // Pictures only make sense on a picture; placed sounds only where sound is kept.
        val withOverlays = format.hasVideo && request.imageOverlays.isNotEmpty()
        val sounds = if (format.hasAudio) request.sounds else emptyList()
        val wantsSourceAudio = format.hasAudio && request.hasSourceAudio &&
            !(request.effects.muted && format.category != FormatCategory.AUDIO)

        val footage = footage(
            request, paths,
            withVideo = format.hasVideo, withAudio = wantsSourceAudio, clipEffects = true, withOverlays = withOverlays,
        )
        val sourceAudio = footage.audio
        val keepSourceAudio = sourceAudio != null

        val args = mutableListOf("-y")
        args += footage.inputArguments
        val firstSoundInput = footage.inputCount
        require(sounds.isEmpty() || paths.sounds.size == request.sounds.size) { "Missing sound paths" }
        sounds.indices.forEach { index -> args += listOf("-i", paths.sounds[index]) }
        // The cover of an audio file, after the sounds.
        val cover = request.coverWriting()
        val coverInput = firstSoundInput + sounds.size
        args += coverInput(cover, paths)

        // Pictures of the whole video; those of single clips are already in the footage.
        val wholeOverlays = if (withOverlays) {
            overlayInputs(request, paths, footage).filter { it.placement.clipId == null }
        } else {
            emptyList()
        }

        val hdrLook = if (format.hasVideo) hdrLookFilters(request, paths) else emptyList()
        val videoChain = if (format.hasVideo) videoFilters.wholeChain(request, paths, outputSeconds) + hdrLook else emptyList()
        // With pictures the captions are stacked among them, between these two parts.
        val videoBefore = if (format.hasVideo) videoFilters.wholeBefore(request, paths) else emptyList()
        val videoCaptions = if (format.hasVideo) videoFilters.wholeCaptions(request) else emptyList()
        val videoAfter = if (format.hasVideo) videoFilters.wholeAfter(request, outputSeconds) + hdrLook else emptyList()
        val videoTail = when {
            format == OutputFormat.GIF -> VideoFilters.gifPaletteTail(request.encoding.gif)
            usesHardwareEncoder(request) -> request.encoding.hardwareEncoder?.let(::hardwareAlignment)
            else -> null
        }
        // The final sound's own effects run on the mix, or on the footage's sound alone.
        val master = if (format.hasAudio) audioFilters.masterChain(request.audioEffects, outputSeconds) else emptyList()
        val sourceAudioChain = if (keepSourceAudio) {
            audioFilters.wholeChain(request, outputSeconds) + (if (sounds.isEmpty()) master else emptyList())
        } else {
            emptyList()
        }

        var withAudio = keepSourceAudio || sounds.isNotEmpty()

        if (footage.graph.isNotEmpty() || wholeOverlays.isNotEmpty() || sounds.isNotEmpty()) {
            val graph = footage.graph.toMutableList()
            if (format.hasVideo) {
                graph += if (wholeOverlays.isEmpty()) {
                    videoGraph(footage.video, videoChain, videoTail, wholeOverlays, wholeFrameWidth(request))
                } else {
                    videoGraph(footage.video, videoBefore, videoTail, wholeOverlays, wholeFrameWidth(request), videoCaptions, videoAfter)
                }
            }
            if (sounds.isNotEmpty()) {
                // The source sound (if kept) and every placed sound, mixed at their own levels.
                val mixed = mutableListOf<String>()
                // A single sound with nothing to mix it with is the output itself.
                val alone = sourceAudio == null && sounds.size == 1
                // Everything mixed on two channels, a mono sound at its full level on both
                // sides (as the preview plays it), so that a stereo sound stays stereo.
                val toMix = if (alone) emptyList() else listOf(STEREO_UPMIX)
                if (sourceAudio != null) {
                    graph += "[$sourceAudio]${(sourceAudioChain + toMix).joinOrPassthrough("anull")}[a0]"
                    mixed += "a0"
                }
                sounds.forEachIndexed { index, sound ->
                    val chain = audioFilters.soundChain(sound, request, outputSeconds) + toMix + (if (alone) master else emptyList())
                    val label = if (alone) AUDIO_OUT else "s$index"
                    graph += "[${firstSoundInput + index}:a]${chain.joinToString(",")}[$label]"
                    mixed += label
                }
                if (!alone) {
                    graph += mixed.joinToString("") { "[$it]" } +
                        (listOf(audioFilters.mixer(mixed.size, outputSeconds, sourceFirst = sourceAudio != null)) + master).joinToString(",") +
                        "[$AUDIO_OUT]"
                }
            } else if (sourceAudio != null && sourceAudioChain.isNotEmpty()) {
                graph += "[$sourceAudio]${sourceAudioChain.joinToString(",")}[$AUDIO_OUT]"
            }
            args += listOf("-filter_complex", graph.joinToString(";"))
            if (format.hasVideo) args += listOf("-map", "[$VIDEO_OUT]")
            when {
                sounds.isNotEmpty() || sourceAudioChain.isNotEmpty() -> args += listOf("-map", "[$AUDIO_OUT]")
                // Untouched source audio: straight from the file, or from the joined footage.
                sourceAudio != null && footage.isSingleFile -> args += listOf("-map", "$sourceAudio?")
                sourceAudio != null -> args += listOf("-map", "[$sourceAudio]")
                else -> args += "-an"
            }
            if (cover == CoverWriting.ATTACHED_PICTURE) args += listOf("-map", "$coverInput:v")
        } else {
            val videoFilter = (videoChain + listOfNotNull(videoTail)).joinToString(",")
            if (videoFilter.isNotEmpty()) args += listOf("-vf", videoFilter)
            when {
                // Audio-only containers are left alone so FFmpeg reports a missing stream itself.
                !withAudio && format.category != FormatCategory.AUDIO -> args += "-an"
                sourceAudioChain.isNotEmpty() -> args += listOf("-af", sourceAudioChain.joinToString(","))
            }
            // With a cover there are two inputs with pictures: the sound and the cover are named.
            if (cover == CoverWriting.ATTACHED_PICTURE) args += listOf("-map", sourceAudio ?: "0:a", "-map", "$coverInput:v")
        }

        // Audio-only containers already imply "no picture"; they always encode sound.
        if (format.category == FormatCategory.AUDIO) withAudio = true

        val segmented = request.segmenting() != null
        args += encoderArguments(
            format, request.encoding.quality, withAudio, segmented, request.encoding.audio, request.encoding.gif,
            withCover = cover == CoverWriting.ATTACHED_PICTURE,
            hardware = request.encoding.hardwareEncoder?.takeIf { usesHardwareEncoder(request) },
            hardwareKbps = if (usesHardwareEncoder(request)) hardwareKbps(request) else null,
            x264Preset = request.encoding.cpuSpeed.preset,
            hdr = hdrOutput(request),
        )
        // HDR kept or made: the video says so, whatever the filters left on its frames.
        (keptHdr(request) ?: VideoHdr.HLG.takeIf { hdrLookOut(request) != null })?.let { hdr ->
            args += listOf("-color_primaries", "bt2020", "-color_trc", hdr.transfer.orEmpty(), "-colorspace", "bt2020nc")
        }
        args += coverArguments(cover, coverInput, format, copyPicture = true)
        if (format.hasAudio) args += metadata(request)
        args += segmenting(request, forceKeyFrames = format.hasVideo)
        args += paths.output
        return args
    }

    /**
     * The HDR kind the output keeps (see EncodingSettings.keepHdr): an MP4 or
     * MKV whose clips all are HDR of one kind; null when HDR clips are made SDR.
     */
    internal fun keptHdr(request: ConversionRequest): VideoHdr? {
        if (!request.encoding.keepHdr) return null
        if (request.format != OutputFormat.MP4 && request.format != OutputFormat.MKV) return null
        if (request.frameExtraction != FrameExtraction.Disabled) return null
        val kinds = request.segments.mapNotNull { request.sources.getOrNull(it.source)?.hdr }.distinct()
        return kinds.singleOrNull()?.takeIf { it != VideoHdr.NONE }
    }

    /**
     * The intensity of the HDR look the output is given (see HdrVideoLook): a
     * video of SDR footage (HDR footage kept HDR is already) in an MP4 or MKV,
     * encoded rather than copied; null for none.
     */
    internal fun hdrLookOut(request: ConversionRequest): Float? =
        request.effects.hdrLook?.takeIf {
            (request.format == OutputFormat.MP4 || request.format == OutputFormat.MKV) &&
                request.frameExtraction == FrameExtraction.Disabled &&
                !(request.encoding.streamCopy && request.sources.none { s -> s.isStill }) &&
                keptHdr(request) == null
        }

    /** The output is an HDR video (kept or made so): 10 bits, HEVC. */
    internal fun hdrOutput(request: ConversionRequest): Boolean = keptHdr(request) != null || hdrLookOut(request) != null

    /**
     * The HDR look's filters, at the very end of the picture: the SDR picture
     * through its LUT into HLG (BT.2020), in 10 bits.
     */
    private fun hdrLookFilters(request: ConversionRequest, paths: MediaPaths): List<String> {
        if (hdrLookOut(request) == null) return emptyList()
        val lut = paths.hdrLut ?: return emptyList()
        return listOf(
            "format=gbrp16le",
            "lut3d=file=${filterOptionValue(lut)}:interp=tetrahedral",
            "scale=out_color_matrix=bt2020:out_range=tv",
            "format=yuv420p10le",
        )
    }

    /** What [segment]'s footage is tone mapped from: its HDR kind, or NONE when it stays as it is. */
    private fun sdrFrom(request: ConversionRequest, segment: Segment): VideoHdr =
        if (keptHdr(request) != null) VideoHdr.NONE else request.sources[segment.source].hdr

    /**
     * One picture: the first clip's file (a frame of it at [ImageEdit.frameMs] for
     * footage), edited by [ConversionRequest.image] and the whole video's turn,
     * mirrors and crop, with the captions and the pictures (the drawing is one of
     * them) on top, then the border and the output size.
     */
    private fun imageCommand(request: ConversionRequest, paths: MediaPaths): List<String> {
        val edit = request.image
        val segment = request.segments.first()
        val source = request.sources[segment.source]
        val args = mutableListOf("-y")
        if (!source.isStill) {
            val at = segment.startMs + pictureFrameMs(edit, segment.endMs?.let { it - segment.startMs })
            if (at > 0) args += listOf("-ss", formatTimecode(at))
        }
        args += listOf("-i", paths.inputs[segment.source])
        val pictures = request.imageOverlays.filter { it.clipId == null }
        require(paths.imageOverlays.size == request.imageOverlays.size) { "Missing overlay paths" }
        request.imageOverlays.forEachIndexed { index, placement ->
            if (placement.clipId == null) args += listOf("-i", paths.imageOverlays[index])
        }

        // The picture itself, sized as the source is (stills are made upright); a frame of
        // HDR footage tone mapped first (see VideoHdr).
        val width = source.width.takeIf { it > 0 } ?: DEFAULT_IMAGE_SIDE
        val height = source.height.takeIf { it > 0 } ?: DEFAULT_IMAGE_SIDE
        val picture = toneMapFilters(source.hdr, "gbrp") + "format=rgb24" + imageStages(edit, request.effects, width, height).map { it.filter }
        // Captions and pictures stacked in their order, as in a video.
        val captions = request.wholeTextOverlays.filter { it.text.isNotBlank() }.map { it.layer to videoFilters.drawtext(it, timed = false) }
        // An Ultra HDR picture is also written losslessly as it comes out, and without what
        // lies on it (see HdrPictures): the picture is split before the captions.
        val hdr = paths.hdrPictures?.takeIf { request.format == OutputFormat.JPG }

        val (editedW, editedH) = editedSize(edit, request.effects, width, height)
        val tail = mutableListOf<String>()
        // The picture without what lies on it: the border black, the same size.
        val plainTail = mutableListOf<String>()
        val border = borderPixels(edit, editedW, editedH)
        edit.border?.takeIf { border > 0 }?.let {
            val color = String.format(java.util.Locale.US, "0x%06X", it.color and 0xFFFFFF)
            tail += "pad=w=iw+${2 * border}:h=ih+${2 * border}:x=$border:y=$border:color=$color"
            plainTail += "pad=w=iw+${2 * border}:h=ih+${2 * border}:x=$border:y=$border:color=black"
        }
        val maxSide = edit.output.maxSide
        if (maxSide > 0) {
            val scale = "scale=w='if(gte(iw,ih),min(iw,$maxSide),-1)':h='if(gte(iw,ih),-1,min(ih,$maxSide))'"
            tail += scale
            plainTail += scale
        }

        // One picture has no clock: every picture lies on it, whole, without fades.
        val overlays = pictures.mapIndexed { index, placement ->
            OverlayInput(1 + index, placement.copy(startMs = 0L, endMs = null, fadeInSeconds = 0f, fadeOutSeconds = 0f, video = null))
        }
        val graph = mutableListOf<String>()
        if (hdr == null) {
            graph += overlayGraph("0:v", picture, overlays, prefix = "w", output = if (tail.isEmpty()) VIDEO_OUT else "pic", frameWidth = editedW, keepAlpha = edit.isTransparent, captions = captions)
            if (tail.isNotEmpty()) graph += "[pic]${tail.joinToString(",")}[$VIDEO_OUT]"
        } else {
            graph += "[0:v]${picture.joinToString(",")},split[$HDR_ALL][$HDR_PLAIN_IN]"
            graph += overlayGraph(HDR_ALL, emptyList(), overlays, prefix = "w", output = "pic", frameWidth = editedW, captions = captions)
            graph += "[pic]${(tail + "split").joinToString(",")}[$VIDEO_OUT][$HDR_FINAL]"
            graph += "[$HDR_PLAIN_IN]${plainTail.joinOrPassthrough("null")}[$HDR_PLAIN]"
        }
        args += listOf("-filter_complex", graph.joinToString(";"), "-map", "[$VIDEO_OUT]", "-frames:v", "1", "-update", "1")
        args += imageEncoderArguments(request.format, edit.output.quality, alpha = edit.isTransparent && request.format.holdsTransparency)
        args += paths.output
        if (hdr != null) {
            for ((label, path) in listOf(HDR_FINAL to hdr.finalPng, HDR_PLAIN to hdr.plainPng)) {
                args += listOf("-map", "[$label]", "-frames:v", "1", "-update", "1")
                args += imageEncoderArguments(OutputFormat.PNG, 1f)
                args += path
            }
        }
        return args
    }

    // ---------------------------------------------------------------------
    // Footage: the edited sequence as FFmpeg inputs
    // ---------------------------------------------------------------------

    /**
     * @param video             what to read the picture from: a stream specifier
     *                          ("0:v") for a single untouched file, a graph label
     *                          ("vcat") after the join.
     * @param audio             same for sound; null when the footage carries none.
     * @param firstOverlayInput input index of the first picture overlay, when the
     *                          overlay files were added as inputs.
     */
    private class Footage(
        val inputArguments: List<String>,
        val inputCount: Int,
        val graph: List<String>,
        val video: String,
        val audio: String?,
        val isSingleFile: Boolean,
        val firstOverlayInput: Int,
    )

    /**
     * Turns the segments into inputs and, when needed, a graph that joins them.
     *
     * Every segment is its own seeked input. Its own effects ([clipEffects]) and
     * pictures run on it alone; then all segments are fitted into one frame size
     * (concat needs one) and joined. A single segment without effects of its own
     * needs no graph at all.
     *
     * @param withOverlays  also add every picture overlay file as an input, after
     *                      the segments (see [Footage.firstOverlayInput]).
     */
    private fun footage(
        request: ConversionRequest,
        paths: MediaPaths,
        withVideo: Boolean,
        withAudio: Boolean,
        clipEffects: Boolean,
        withOverlays: Boolean,
    ): Footage {
        val segments = request.segments
        val overlayArguments = if (withOverlays) {
            require(paths.imageOverlays.size == request.imageOverlays.size) {
                "Expected ${request.imageOverlays.size} overlay paths, got ${paths.imageOverlays.size}"
            }
            // A picture is a single frame; a video its own part of the file.
            paths.imageOverlays.flatMapIndexed { index, path -> overlayInputArguments(request.imageOverlays[index], path) }
        } else {
            emptyList()
        }
        val overlayCount = if (withOverlays) paths.imageOverlays.size else 0

        if (segments.size == 1 && !(clipEffects && request.hasClipEdits)) {
            // HDR footage made SDR is tone mapped first (see VideoHdr), even when nothing else is done.
            val toneMap = if (withVideo) toneMapFilters(sdrFrom(request, segments.single())) else emptyList()
            return Footage(
                inputArguments = seekedInput(segments.single(), request, paths, limitWithDuration = false) + overlayArguments,
                inputCount = 1 + overlayCount,
                graph = if (toneMap.isEmpty()) emptyList() else listOf("[0:v]${toneMap.joinToString(",")}[$VIDEO_SDR]"),
                video = if (toneMap.isEmpty()) "0:v" else VIDEO_SDR,
                audio = if (withAudio) "0:a" else null,
                isSingleFile = true,
                firstOverlayInput = 1,
            )
        }

        val inputArguments = segments.flatMap { seekedInput(it, request, paths, limitWithDuration = true) } + overlayArguments
        val firstOverlayInput = segments.size
        val graph = mutableListOf<String>()

        // Concat needs one frame size. The project frame is the one of the first clip's
        // file: every clip whose frame may differ (another file, a crop, a quarter turn)
        // is fitted into it. Cuts of a single untouched file need nothing.
        val frame = request.sources[segments.first().source]
        // Clips joined by transitions overlap: xfade needs every clip in one size, frame
        // rate and pixel format, and the sound is cross-faded (see transitionJoin).
        val overlaps = if (clipEffects) request.transitionsMs() else emptyList()
        // Every clip's length is needed to place the cross-fades, and pictures of one known size.
        val transitions = overlaps.any { it > 0L } && segments.all { request.playedLengthMs(it) != null } &&
            (!withVideo || (frame.width > 0 && frame.height > 0))
        val normalize = withVideo && frame.width > 0 && frame.height > 0 &&
            (transitions || segments.map { it.source }.distinct().size > 1 ||
                (clipEffects && segments.any { it.effects.changesFrameSize }))
        val width = frame.width + frame.width % 2
        val height = frame.height + frame.height % 2
        val fit = "scale=$width:$height:force_original_aspect_ratio=decrease," +
            "pad=$width:$height:(ow-iw)/2:(oh-ih)/2,setsar=1" +
            (if (transitions) ",fps=${transitionFrameRate(request)},format=${if (keptHdr(request) != null) "yuv420p10le" else "yuv420p"}" else "")

        // A clip without sound gets silence, which needs its length.
        val audio = withAudio && segments.all { request.sources[it.source].hasAudio || request.playedLengthMs(it) != null }

        val concatInputs = StringBuilder()
        // Each segment's streams, in order, for a join by transitions.
        val videoLabels = mutableListOf<String>()
        val audioLabels = mutableListOf<String>()
        segments.forEachIndexed { index, segment ->
            val playedSeconds = request.playedLengthMs(segment)?.let { it / 1000.0 }
            val footageSeconds = request.footageLengthMs(segment)?.let { it / 1000.0 }
            val times = SegmentTimes.of(segment, footageSeconds)
            if (withVideo) {
                // HDR footage made SDR is tone mapped before anything else (see VideoHdr).
                val toneMap = toneMapFilters(sdrFrom(request, segment))
                val chain = toneMap + if (clipEffects) {
                    stillMotion(segment, request) + videoFilters.clipPicture(
                        segment.effects, label = "kc$index", fileTime = times.picture, footageSeconds = footageSeconds,
                    )
                } else {
                    emptyList()
                }
                // Its captions and pictures, stacked in their order, then its fades over them.
                val captions = if (clipEffects) videoFilters.clipCaptions(request.textOverlaysOf(segment)) else emptyList()
                val fades = if (clipEffects) videoFilters.fadesOf(segment.effects, playedSeconds) else emptyList()
                val pictures = if (clipEffects && withOverlays && segment.clipId != null) {
                    overlayInputs(request, paths, firstOverlayInput).filter { it.placement.clipId == segment.clipId }
                } else {
                    emptyList()
                }
                val tail = if (normalize) listOf(fit) else emptyList()
                val label = "sv$index"
                when {
                    pictures.isNotEmpty() -> {
                        graph += overlayGraph(
                            input = "$index:v",
                            chain = chain,
                            overlays = pictures,
                            prefix = "c$index",
                            output = if (tail.isEmpty()) label else "co$index",
                            frameWidth = clipFrame(request, segment)?.first ?: DEFAULT_IMAGE_SIDE,
                            captions = captions,
                            post = fades,
                        )
                        if (tail.isNotEmpty()) graph += "[co$index]${tail.joinToString(",")}[$label]"
                        concatInputs.append("[$label]")
                        videoLabels += "[$label]"
                    }
                    chain.isNotEmpty() || captions.isNotEmpty() || fades.isNotEmpty() || tail.isNotEmpty() -> {
                        val all = chain + captions.sortedBy { it.first }.map { it.second } + fades + tail
                        graph += "[$index:v]${all.joinToString(",")}[$label]"
                        concatInputs.append("[$label]")
                        videoLabels += "[$label]"
                    }
                    else -> {
                        concatInputs.append("[$index:v]")
                        videoLabels += "[$index:v]"
                    }
                }
            }
            if (audio) {
                val chain = if (clipEffects) {
                    audioFilters.clipChain(segment.effects, playedSeconds, times.sound, footageSeconds, label = "ac$index")
                } else {
                    emptyList()
                }
                when {
                    !request.sources[segment.source].hasAudio -> {
                        graph += fmt("anullsrc=r=48000:cl=stereo,atrim=duration=%.3f", playedSeconds ?: 0.0) +
                            (if (transitions) ",aformat=sample_fmts=fltp:channel_layouts=stereo" else "") + "[sa$index]"
                        concatInputs.append("[sa$index]")
                    }
                    // A cross-fade needs both sounds in one rate and layout.
                    transitions -> {
                        graph += "[$index:a]${(chain + TRANSITION_SOUND).joinToString(",")}[sa$index]"
                        concatInputs.append("[sa$index]")
                    }
                    chain.isNotEmpty() -> {
                        graph += "[$index:a]${chain.joinToString(",")}[sa$index]"
                        concatInputs.append("[sa$index]")
                    }
                    else -> concatInputs.append("[$index:a]")
                }
                audioLabels += if (!request.sources[segment.source].hasAudio || transitions || chain.isNotEmpty()) "[sa$index]" else "[$index:a]"
            }
        }
        if (transitions) {
            val played = segments.map { (request.playedLengthMs(it) ?: 0L) / 1000.0 }
            val kinds = segments.map { it.effects.transition?.kind }
            if (withVideo) graph += transitionJoin(videoLabels, played, overlaps, kinds, video = true, output = VIDEO_JOINED)
            if (audio) graph += transitionJoin(audioLabels, played, overlaps, kinds, video = false, output = AUDIO_JOINED)
        } else {
            graph += "${concatInputs}concat=n=${segments.size}:v=${if (withVideo) 1 else 0}:a=${if (audio) 1 else 0}" +
                (if (withVideo) "[$VIDEO_JOINED]" else "") +
                (if (audio) "[$AUDIO_JOINED]" else "")
        }

        return Footage(
            inputArguments = inputArguments,
            inputCount = segments.size + overlayCount,
            graph = graph,
            video = VIDEO_JOINED,
            audio = if (audio) AUDIO_JOINED else null,
            isSingleFile = false,
            firstOverlayInput = firstOverlayInput,
        )
    }

    /**
     * One segment as a seeked input. Seeking before `-i` is fast and, because
     * the footage is re-encoded, exact to the frame.
     *
     * @param limitWithDuration  use `-t` (length) rather than `-to` (end); needed
     *                           when several inputs follow each other.
     */
    private fun seekedInput(segment: Segment, request: ConversionRequest, paths: MediaPaths, limitWithDuration: Boolean): List<String> {
        if (request.sources[segment.source].isStill) {
            // A picture shown for the length of the segment: looped at the output rate
            // (or a common one) for exactly that long. The range only gives the length.
            val rate = request.encoding.framerate.value.takeIf { it > 0 } ?: STILL_FRAME_RATE
            val length = segment.durationMs ?: DEFAULT_STILL_MS
            return listOf(
                "-loop", "1",
                "-framerate", rate.toString(),
                "-t", formatTimecode(length),
                "-i", paths.inputs[segment.source],
            )
        }
        val args = mutableListOf<String>()
        if (segment.startMs > 0) args += listOf("-ss", formatTimecode(segment.startMs))
        val end = segment.endMs
        if (end != null) {
            args += if (limitWithDuration) {
                listOf("-t", formatTimecode((end - segment.startMs).coerceAtLeast(0L)))
            } else {
                listOf("-to", formatTimecode(end))
            }
        }
        args += listOf("-i", paths.inputs[segment.source])
        return args
    }

    // ---------------------------------------------------------------------
    // Shared pieces
    // ---------------------------------------------------------------------

    /**
     * The slow zoom or pan over a still segment, as zoompan: one output frame per
     * input frame (the still is looped at a known rate), the view following
     * [StillMotion] over the frames of the clip. Empty for anything else.
     */
    private fun stillMotion(segment: Segment, request: ConversionRequest): List<String> {
        val motion = segment.effects.motion ?: return emptyList()
        val source = request.sources[segment.source]
        if (!source.isStill || source.width <= 0 || source.height <= 0) return emptyList()
        val rate = request.encoding.framerate.value.takeIf { it > 0 } ?: STILL_FRAME_RATE
        val frames = Math.round((segment.durationMs ?: DEFAULT_STILL_MS) / 1000.0 * rate).coerceAtLeast(2L)
        val progress = "min(on/${frames - 1},1)"
        val a = fmt("%.4f", motion.strength)
        val zoom = when (motion.kind) {
            MotionKind.ZOOM_IN -> "1+($a-1)*$progress"
            MotionKind.ZOOM_OUT -> "$a-($a-1)*$progress"
            else -> a
        }
        val x = when (motion.kind) {
            MotionKind.PAN_LEFT -> "(iw-iw/zoom)*(1-$progress)"
            MotionKind.PAN_RIGHT -> "(iw-iw/zoom)*$progress"
            else -> "(iw-iw/zoom)/2"
        }
        val y = when (motion.kind) {
            MotionKind.PAN_UP -> "(ih-ih/zoom)*(1-$progress)"
            MotionKind.PAN_DOWN -> "(ih-ih/zoom)*$progress"
            else -> "(ih-ih/zoom)/2"
        }
        val width = source.width - source.width % 2
        val height = source.height - source.height % 2
        return listOf("zoompan=z='$zoom':x='$x':y='$y':d=1:s=${width}x$height:fps=$rate")
    }

    /**
     * How an overlay file is read: a video from its own part of the file; a
     * picture as a single frame (one that fades or moves is repeated after it is
     * made, see [STILL_LOOP]).
     */
    private fun overlayInputArguments(placement: ImagePlacement, path: String): List<String> {
        val video = placement.video
        if (video != null && placement.clipId == null) {
            val args = mutableListOf<String>()
            if (video.trimStartMs > 0) args += listOf("-ss", formatTimecode(video.trimStartMs))
            val end = video.trimEndMs
            if (end != null) args += listOf("-t", formatTimecode((end - video.trimStartMs).coerceAtLeast(0L)))
            return args + listOf("-i", path)
        }
        // A picture is read once (a single frame, at the image reader's 25 frames a second):
        // one that fades or moves is repeated by the loop filter after it is made (STILL_LOOP).
        return listOf("-i", path)
    }

    /**
     * The scale of a picture laid [fraction] of the width of a frame [frameWidth]
     * pixels wide, its shape kept (even sides, as scale2ref sized it before).
     *
     * Not scale2ref: in the bundled FFmpeg 6.1.6 a filter without an activate
     * callback closes all its inputs as soon as one of its outputs ends, so
     * scale2ref closed the video it measured against once the single frame of a
     * picture was through, and the export waited for frames forever.
     */
    private fun overlayScale(frameWidth: Int, fraction: Float): String {
        val width = (Math.floor(frameWidth.coerceAtLeast(2) * fraction / 2.0) * 2).toInt().coerceAtLeast(2)
        return "scale=w=$width:h='max(2,trunc(ow/dar/2)*2)'"
    }

    /**
     * Width of the frame the pictures of a clip of [segment] land on: its file's,
     * after the clip's own turn and crop. Null when the file's size is not known.
     */
    private fun clipFrame(request: ConversionRequest, segment: Segment): Pair<Int, Int>? {
        val source = request.sources.getOrNull(segment.source) ?: return null
        if (source.width <= 0 || source.height <= 0) return null
        var width = source.width
        var height = source.height
        // A still that moves goes through zoompan, at even sides.
        if (source.isStill && segment.effects.motion != null) {
            width -= width % 2
            height -= height % 2
        }
        return turnedAndCropped(segment.effects, width, height)
    }

    /** A frame of [width] x [height] after the quarter turns and the crop of [effects]. */
    private fun turnedAndCropped(effects: Effects, width: Int, height: Int): Pair<Int, Int> {
        val sideways = effects.rotation == Rotation.DEG_90 || effects.rotation == Rotation.DEG_270
        var w = if (sideways) height else width
        var h = if (sideways) width else height
        effects.effectiveCrop?.let { crop ->
            // As cropFilter: trunc(iw * fraction / 2) * 2.
            w = (Math.floor(w * crop.width.coerceIn(0.01f, 1f) / 2.0) * 2).toInt().coerceAtLeast(2)
            h = (Math.floor(h * crop.height.coerceIn(0.01f, 1f) / 2.0) * 2).toInt().coerceAtLeast(2)
        }
        return w to h
    }

    /**
     * Width of the frame the pictures of the whole video land on: the joined
     * footage (the first clip's file, or what fits it), turned, cropped and put
     * on its canvas as the whole video is, at the output's height. Worked out as
     * the filters do it; [DEFAULT_IMAGE_SIDE] when the files' sizes are not known.
     */
    internal fun wholeFrameWidth(request: ConversionRequest): Int {
        val segments = request.segments
        val first = segments.firstOrNull() ?: return DEFAULT_IMAGE_SIDE
        val source = request.sources.getOrNull(first.source) ?: return DEFAULT_IMAGE_SIDE
        if (source.width <= 0 || source.height <= 0) return DEFAULT_IMAGE_SIDE
        // As footage(): one untouched clip as it is; clips of other files or of other
        // shapes fitted into the first file's frame (even sides); else the first clip's.
        val joined = when {
            segments.size == 1 && !request.hasClipEdits -> source.width to source.height
            segments.map { it.source }.distinct().size > 1 || segments.any { it.effects.changesFrameSize } ->
                (source.width + source.width % 2) to (source.height + source.height % 2)
            else -> clipFrame(request, first) ?: (source.width to source.height)
        }
        var (w, h) = turnedAndCropped(request.effects, joined.first, joined.second)
        // The canvas (see VideoFilters.padFilter): the frame grows to the ratio, even sides.
        request.canvas.ratio?.let { ratio ->
            val paddedW = Math.ceil(maxOf(w.toDouble(), h * ratio.toDouble()) / 2.0) * 2
            val paddedH = Math.ceil(maxOf(h.toDouble(), w / ratio.toDouble()) / 2.0) * 2
            w = paddedW.toInt()
            h = paddedH.toInt()
        }
        val gif = request.encoding.gif
        when {
            request.format == OutputFormat.GIF -> if (gif.width > 0) w = minOf(w, gif.width)
            request.encoding.resolution != Resolution.ORIGINAL && h > 0 -> {
                // scale=-2:height, as FFmpeg rounds it.
                val height = request.encoding.resolution.height
                w = (Math.round(height.toDouble() * w / (h * 2.0)) * 2).toInt()
            }
        }
        return w.coerceAtLeast(2)
    }

    /** A picture overlay and the input it is read from. */
    private data class OverlayInput(val input: Int, val placement: ImagePlacement)

    private fun overlayInputs(request: ConversionRequest, paths: MediaPaths, footage: Footage): List<OverlayInput> =
        overlayInputs(request, paths, footage.firstOverlayInput)

    private fun overlayInputs(request: ConversionRequest, paths: MediaPaths, firstInput: Int): List<OverlayInput> {
        require(paths.imageOverlays.size == request.imageOverlays.size) {
            "Expected ${request.imageOverlays.size} overlay paths, got ${paths.imageOverlays.size}"
        }
        return request.imageOverlays.mapIndexed { index, placement -> OverlayInput(firstInput + index, placement) }
    }

    /**
     * Picture part of the filter graph for the whole video:
     *
     *   [input] main chain -> overlay 0 -> overlay 1 -> ... -> optional tail -> [vout]
     */
    private fun videoGraph(
        input: String,
        chain: List<String>,
        tail: String?,
        overlays: List<OverlayInput>,
        frameWidth: Int,
        captions: List<Pair<Int, String>> = emptyList(),
        post: List<String> = emptyList(),
    ): List<String> {
        val lastLabel = if (tail == null) VIDEO_OUT else "vpre"
        val segments = overlayGraph(input, chain, overlays, prefix = "w", output = lastLabel, frameWidth = frameWidth, captions = captions, post = post).toMutableList()
        if (tail != null) segments += "[$lastLabel]$tail[$VIDEO_OUT]"
        return segments
    }

    /**
     * [input] through [chain], then [captions] (drawtext filters, each with its
     * layer) and [overlays] stacked on top in their order (see [stackOrder]),
     * then [post] over everything, into [output]. Captions under every picture
     * simply go on with the chain. Labels are made unique with [prefix], so
     * several of these can share a graph.
     */
    private fun overlayGraph(
        input: String,
        chain: List<String>,
        overlays: List<OverlayInput>,
        prefix: String,
        output: String,
        /** Width in pixels of the frame the pictures land on (see [wholeFrameWidth]). */
        frameWidth: Int,
        /** The picture below has transparent parts to keep (a cut-out picture file). */
        keepAlpha: Boolean = false,
        captions: List<Pair<Int, String>> = emptyList(),
        post: List<String> = emptyList(),
    ): List<String> {
        val segments = mutableListOf<String>()
        val order = stackOrder(captions.map { it.first }, overlays.map { it.placement.layer })
        val leading = order.takeWhile { it.isText }
        val stacked = order.drop(leading.size)
        val head = chain + leading.map { captions[it.index].second }
        if (stacked.isEmpty()) {
            segments += "[$input]${(head + post).joinOrPassthrough("null")}[$output]"
            return segments
        }
        val mainLabel = "${prefix}main"
        segments += "[$input]${head.joinOrPassthrough("null")}[$mainLabel]"
        val endLabel = if (post.isEmpty()) output else "${prefix}end"

        var previous = mainLabel
        stacked.forEachIndexed { position, slot ->
            val last = position == stacked.lastIndex
            if (slot.isText) {
                val next = if (last) endLabel else "${prefix}cap${slot.index}"
                segments += "[$previous]${captions[slot.index].second}[$next]"
                previous = next
                return@forEachIndexed
            }
            val index = slot.index
            val overlay = overlays[index]
            val placement = overlay.placement
            val picture = "${prefix}ov$index"
            val next = if (last) endLabel else "${prefix}tmp$index"
            val timed = placement.clipId == null
            val video = placement.video?.takeIf { timed }

            // Before sizing: a video is moved to where it starts on the exported video;
            // colours and mirroring are the picture's own.
            val prepared = mutableListOf<String>()
            if (video != null) prepared += fmt("setpts=PTS-STARTPTS+%.3f/TB", placement.startMs.coerceAtLeast(0L) / 1000.0)
            if (placement.adjustsColor) {
                prepared += fmt("eq=brightness=%.2f:contrast=%.2f:saturation=%.2f", placement.brightness, placement.contrast, placement.saturation)
            }
            if (placement.flipHorizontal) prepared += "hflip"
            if (placement.flipVertical) prepared += "vflip"
            // A colour cut out of it lets what it lies on show through; its own transparent
            // parts stay so (the two opacities multiplied). A subject's mask is mirrored as
            // the picture was.
            placement.key?.let { key ->
                val mirrors = listOfNotNull("hflip".takeIf { placement.flipHorizontal }, "vflip".takeIf { placement.flipVertical })
                prepared += keyKeepingAlpha(key, "${prefix}ky$index", mirrors)
            }
            // The part of it that shows, as it shows (mirrored, keyed): what is sized and placed.
            placement.look.crop?.takeIf { placement.look.crops }?.let { prepared += overlayCropFilter(it) }
            var source = "${overlay.input}:v"
            if (prepared.isNotEmpty()) {
                source = "${prefix}ovp$index"
                segments += "[${overlay.input}:v]${prepared.joinToString(",")}[$source]"
            }

            if (placement.animated) {
                segments += animatedOverlay(placement, source, previous, next, "${prefix}k$index", keepAlpha, frameWidth)
                previous = next
                return@forEachIndexed
            }
            // Keyframes that do not change anything over time still set its values.
            val keys = placement.keyframes
            val fraction = keys.valueAt(KeyProperty.SIZE, 0L, placement.widthFraction).coerceIn(0.01f, ImagePlacement.MAX_WIDTH)
            val opacity = keys.valueAt(KeyProperty.OPACITY, 0L, placement.opacity).coerceIn(0f, 1f)
            val translucent = opacity < 0.999f
            val decorated = placement.look.decorates
            val scaled = if (translucent || decorated) "${prefix}ovs$index" else picture

            // Sized as a fraction of the frame it lands on, whose width is worked out here
            // (scale2ref, which would read it, never ends in the bundled FFmpeg 6.1.6 once
            // the picture's single frame is over: see overlayScale).
            segments += "[$source]${overlayScale(frameWidth, fraction)}[$scaled]"
            // Its look, at the size it shows: blur, shape, outline and shadow.
            val looked = if (decorated) "${prefix}ovl$index" else scaled
            if (decorated) {
                segments += "[$scaled]${overlayLookFilters(placement.look, "${prefix}lk$index")}[${if (translucent) looked else picture}]"
            }
            if (translucent) {
                segments += "[$looked]" +
                    fmt("format=rgba,colorchannelmixer=aa=%.3f", opacity) +
                    "[$picture]"
            }
            var shown = picture
            if (placement.fades) {
                // Made once, then repeated as a stream of frames whose opacity can change.
                shown = "${prefix}ovf$index"
                segments += "[$picture]${(listOf(STILL_LOOP) + overlayFades(placement)).joinToString(",")}[$shown]"
            }
            // A turn, after sizing (the width fraction is that of the upright picture); the
            // frame grows to hold the turned picture and the corners stay transparent.
            val turn = ((keys.valueAt(KeyProperty.ROTATION, 0L, placement.rotationDegrees) % 360f) + 360f) % 360f
            if (turn > 0.01f && turn < 359.99f) {
                val radians = fmt("%.6f", Math.toRadians(turn.toDouble()))
                val turned = "${prefix}ovr$index"
                segments += "[$shown]format=rgba,rotate=$radians:ow=rotw($radians):oh=roth($radians):c=none[$turned]"
                shown = turned
            }
            val x = keys.valueAt(KeyProperty.X, 0L, placement.x).coerceIn(ImagePlacement.POSITION_MIN, ImagePlacement.POSITION_MAX)
            val y = keys.valueAt(KeyProperty.Y, 0L, placement.y).coerceIn(ImagePlacement.POSITION_MIN, ImagePlacement.POSITION_MAX)
            val window = if (placement.clipId == null) enableExpression(placement.startMs, placement.endMs) else null
            segments += "[$previous][$shown]overlay=" +
                fmt("x=(W-w)*%.4f:y=(H-h)*%.4f", x, y) +
                (if (window != null) ":enable='$window'" else "") +
                (if (placement.fades && video == null) ":shortest=1" else "") +
                (if (keepAlpha) ":format=auto" else "") +
                (if (video != null) ":eof_action=pass" else "") +
                "[$next]"
            previous = next
        }
        if (post.isNotEmpty()) segments += "[$endLabel]${post.joinToString(",")}[$output]"
        return segments
    }

    /**
     * The segments' streams ([labels], in order) joined two at a time: with xfade
     * (pictures) or acrossfade (sounds) where a transition joins two clips, each
     * overlapping the next for its length ([overlaps], one per gap), with concat
     * where none does. [played] are the segments' lengths in seconds; a picture's
     * cross-fade starts where the joined video so far ends, less the overlap.
     */
    private fun transitionJoin(
        labels: List<String>,
        played: List<Double>,
        overlaps: List<Long>,
        kinds: List<TransitionKind?>,
        video: Boolean,
        output: String,
    ): List<String> {
        val prefix = if (video) "tv" else "ta"
        val parts = mutableListOf<String>()
        var current = labels.first()
        var length = played.first()
        for (k in 1 until labels.size) {
            val overlap = (overlaps.getOrNull(k - 1) ?: 0L) / 1000.0
            val next = if (k == labels.lastIndex) "[$output]" else "[$prefix$k]"
            val kind = kinds.getOrNull(k - 1)
            parts += when {
                overlap <= 0.0 || kind == null ->
                    "$current${labels[k]}concat=n=2:v=${if (video) 1 else 0}:a=${if (video) 0 else 1}$next"
                video -> "$current${labels[k]}" + fmt("xfade=transition=%s:duration=%.3f:offset=%.3f", kind.xfade, overlap, (length - overlap).coerceAtLeast(0.0)) + next
                else -> "$current${labels[k]}" + fmt("acrossfade=d=%.3f:c1=tri:c2=tri", overlap) + next
            }
            length += played[k] - (if (kind != null) overlap else 0.0)
            current = next
        }
        return parts
    }

    /** Frame rate the clips are brought to when transitions join them: the output's, else a common one. */
    private fun transitionFrameRate(request: ConversionRequest): Int =
        request.encoding.framerate.value.takeIf { it > 0 }
            ?: request.sources.maxOfOrNull { it.frameRate }?.takeIf { it > 0f }?.let { Math.round(it).coerceIn(10, 60) }
            ?: STILL_FRAME_RATE

    /**
     * The clocks of a segment's keyframes, as FFmpeg expressions in seconds: the
     * position in its file for the picture (whose filters see the frames before
     * the speed change, from 0, after `reverse` in playing order) and for the
     * sound (whose volume comes after the speed change), and the time since it
     * started playing (for its captions).
     */
    private class SegmentTimes(val picture: String, val sound: String, val played: String) {
        companion object {
            /** [footageSeconds]: length of the segment's footage, which a speed curve needs. */
            fun of(segment: Segment, footageSeconds: Double?): SegmentTimes {
                val start = segment.startMs / 1000.0
                val end = segment.endMs?.let { it / 1000.0 }
                val effects = segment.effects
                val speed = effects.effectiveSpeed.toDouble()
                val backwards = effects.reversed && end != null
                val curve = effects.curve?.takeIf { footageSeconds != null && footageSeconds > 0.0 }
                // Footage played so far, from the time played (t after the speed change).
                val consumed = curve?.consumedExpression(footageSeconds!!, "t") ?: fmt("t*%.6f", speed)
                return SegmentTimes(
                    picture = if (backwards) fmt("(%.4f-t)", end!!) else fmt("(t+%.4f)", start),
                    sound = if (backwards) fmt("(%.4f-", end!!) + "($consumed))" else fmt("(%.4f+", start) + "($consumed))",
                    played = curve?.let { "(${it.playedExpression(footageSeconds!!, "t")})" }
                        ?: if (speed == 1.0) "t" else fmt("(t/%.6f)", speed),
                )
            }
        }
    }

    /** A picture's own fades, on the exported video's clock (the looped picture starts with it). */
    private fun overlayFades(placement: ImagePlacement): List<String> {
        val fades = mutableListOf("format=rgba")
        val start = placement.startMs.coerceAtLeast(0L) / 1000.0
        if (placement.fadeInSeconds > 0f) {
            fades += fmt("fade=t=in:st=%.3f:d=%.3f:alpha=1", start, placement.fadeInSeconds)
        }
        val end = placement.endMs
        if (placement.fadeOutSeconds > 0f && end != null) {
            fades += fmt("fade=t=out:st=%.3f:d=%.3f:alpha=1", (end / 1000.0 - placement.fadeOutSeconds).coerceAtLeast(0.0), placement.fadeOutSeconds)
        }
        return fades
    }

    /**
     * A picture whose place, size, turn or opacity change over time (its
     * keyframes), read as a stream of frames, from [source] onto [main] into
     * [output]; [label] makes the links of its graph unique.
     *
     *  - sized once, against the frame it lands on, at the largest width it takes;
     *  - its opacity, when it changes: a 2 x 2 grey picture worked out on every frame
     *    (geq, with the time T), stretched over it and multiplied into its alpha;
     *  - its fades;
     *  - turned on every frame inside a square that holds it at any angle (its
     *    corners transparent);
     *  - shrunk on every frame to the width it has then;
     *  - laid where it goes then: the place is the fraction of the free space around
     *    the box that holds the turned picture tightly, as for a still one, so the
     *    square is moved by half the difference.
     *
     * Its time is that of the exported video for a picture of the whole video
     * (from its start), that of its clip for a picture of a clip.
     */
    private fun animatedOverlay(placement: ImagePlacement, source: String, main: String, output: String, label: String, keepAlpha: Boolean, frameWidth: Int): List<String> {
        val keys = placement.keyframes
        val whole = placement.clipId == null
        val time = if (whole) fmt("(t-%.4f)", placement.startMs.coerceAtLeast(0L) / 1000.0) else "t"
        val geqTime = time.replace("t", "T")
        val segments = mutableListOf<String>()
        val largest = keys.range(KeyProperty.SIZE, placement.widthFraction).endInclusive.coerceIn(0.01f, ImagePlacement.MAX_WIDTH)
        val sized = "${label}s"
        segments += "[$source]${overlayScale(frameWidth, largest)}[$sized]"

        val chain = mutableListOf("format=rgba")
        var current = sized
        // Its look, at the largest size it shows (it shrinks with it).
        if (placement.look.decorates) {
            segments += "[$sized]${overlayLookFilters(placement.look, "${label}l")}[${label}lk]"
            current = "${label}lk"
        }
        // Made once, then repeated as a stream of frames that move, turn and fade.
        segments += "[$current]$STILL_LOOP[${label}st]"
        current = "${label}st"
        if (keys.animates(KeyProperty.OPACITY)) {
            val opacity = keys.expression(KeyProperty.OPACITY, placement.opacity, geqTime)
            segments += "[$current]format=rgba,split[${label}pa][${label}pb]"
            segments += "color=c=black:s=2x2:r=$OVERLAY_STREAM_RATE,format=gray,geq=lum='255*clip($opacity,0,1)'[${label}m0]"
            segments += "[${label}m0][${label}pb]scale2ref[${label}m1][${label}pc]"
            segments += "[${label}pc]alphaextract[${label}pd]"
            segments += "[${label}pd][${label}m1]blend=all_mode=multiply[${label}pe]"
            segments += "[${label}pa][${label}pe]alphamerge[${label}o]"
            current = "${label}o"
        } else {
            val opacity = keys.valueAt(KeyProperty.OPACITY, 0L, placement.opacity).coerceIn(0f, 1f)
            if (opacity < 0.999f) chain += fmt("colorchannelmixer=aa=%.3f", opacity)
        }
        if (placement.fades) chain += overlayFades(placement).drop(1)
        val turns = keys.animates(KeyProperty.ROTATION) || abs(keys.valueAt(KeyProperty.ROTATION, 0L, placement.rotationDegrees) % 360f) > 0.01f
        val angle = "(${keys.expression(KeyProperty.ROTATION, placement.rotationDegrees, time)})*PI/180"
        if (turns) chain += "rotate=a='$angle':ow='hypot(iw,ih)':oh=ow:c=black@0"
        val scale = "${keys.expression(KeyProperty.SIZE, placement.widthFraction, time)}/${fmt("%.4f", largest)}"
        chain += "scale=w='max(2,trunc(iw*$scale/2)*2)':h='max(2,trunc(ih*$scale/2)*2)':eval=frame"
        val ready = "${label}r"
        segments += "[$current]${chain.joinToString(",")}[$ready]"

        val x = "clip(${keys.expression(KeyProperty.X, placement.x, time)},$POSITION_RANGE)"
        val y = "clip(${keys.expression(KeyProperty.Y, placement.y, time)},$POSITION_RANGE)"
        val place = if (turns) {
            // The square's side is the picture's diagonal; the tight box of the turned picture
            // is worked out from the picture's shape (square when it is not known).
            val aspect = placement.look.laidAspect(placement.aspect).takeIf { it > 0f } ?: 1f
            val diagonal = Math.hypot(aspect.toDouble(), 1.0)
            val c = fmt("%.6f", aspect / diagonal)
            val sn = fmt("%.6f", 1.0 / diagonal)
            val tightW = "(w*($c*abs(cos($angle))+$sn*abs(sin($angle))))"
            val tightH = "(w*($sn*abs(cos($angle))+$c*abs(sin($angle))))"
            "x='(W-$tightW)*$x+($tightW-w)/2':y='(H-$tightH)*$y+($tightH-h)/2'"
        } else {
            "x='(W-w)*$x':y='(H-h)*$y'"
        }
        val window = if (whole) enableExpression(placement.startMs, placement.endMs) else null
        // A looped picture ends with what it lies on; a video simply stops showing.
        segments += "[$main][$ready]overlay=$place" +
            (if (window != null) ":enable='$window'" else "") +
            (if (placement.video != null && whole) ":eof_action=pass" else ":shortest=1") +
            (if (keepAlpha) ":format=auto" else "") +
            "[$output]"
        return segments
    }

    /** The input of the cover: its picture, or the tags file that holds it (see [CoverWriting]). */
    private fun coverInput(cover: CoverWriting?, paths: MediaPaths): List<String> = when (cover) {
        CoverWriting.ATTACHED_PICTURE -> listOf("-i", requireNotNull(paths.cover) { "Missing cover path" })
        CoverWriting.PICTURE_TAG -> listOf("-f", "ffmetadata", "-i", requireNotNull(paths.coverTags) { "Missing cover tags path" })
        null -> emptyList()
    }

    /**
     * How the cover read from input [coverInput] is written: a picture stream marked
     * as the cover ([copyPicture] when the codecs are not copied already), named as
     * the front cover where the container keeps a picture type; or the tags of the
     * tags file added to those of the main file.
     */
    private fun coverArguments(cover: CoverWriting?, coverInput: Int, format: OutputFormat, copyPicture: Boolean): List<String> =
        when (cover) {
            CoverWriting.ATTACHED_PICTURE -> {
                val args = mutableListOf<String>()
                if (copyPicture) args += listOf("-c:v", "copy")
                args += listOf("-disposition:v:0", "attached_pic")
                // ID3 and FLAC take the picture type from the picture's comment.
                if (format == OutputFormat.MP3 || format == OutputFormat.FLAC) args += listOf("-metadata:s:v:0", "comment=Cover (front)")
                args
            }
            CoverWriting.PICTURE_TAG -> listOf("-map_metadata", "0", "-map_metadata", coverInput.toString())
            null -> emptyList()
        }

    private fun metadata(request: ConversionRequest): List<String> =
        request.metadata.asMap().flatMap { (key, value) -> listOf("-metadata", "$key=$value") }

    private fun segmenting(request: ConversionRequest, forceKeyFrames: Boolean): List<String> {
        val plan = request.segmenting() ?: return emptyList()
        val args = mutableListOf("-f", "segment")
        val keyFrames = when (plan) {
            is Segmenting.Every -> {
                args += listOf("-segment_time", plan.seconds.toString())
                "expr:gte(t,n_forced*${plan.seconds})"
            }
            is Segmenting.At -> {
                val cutPoints = plan.cutPointsSeconds.joinToString(",") { fmt("%.3f", it) }
                args += listOf("-segment_times", cutPoints)
                cutPoints
            }
        }
        args += listOf("-reset_timestamps", "1")
        if (forceKeyFrames) {
            // A segment can only start on a key frame: force one at every boundary and
            // allow a small tolerance, otherwise the muxer skips to the next boundary.
            args += listOf("-segment_time_delta", SEGMENT_TOLERANCE_SECONDS, "-force_key_frames", keyFrames)
        }
        return args
    }

    private fun List<String>.joinOrPassthrough(passthrough: String): String =
        if (isEmpty()) passthrough else joinToString(",")

    private companion object {
        const val VIDEO_OUT = "vout"
        const val AUDIO_OUT = "aout"
        const val VIDEO_JOINED = "vcat"
        const val AUDIO_JOINED = "acat"

        /** HDR footage once tone mapped, when nothing else is done to it. */
        const val VIDEO_SDR = "vsdr"

        /** Links of an Ultra HDR picture's graph (see HdrPictures). */
        const val HDR_ALL = "hdrall"
        const val HDR_PLAIN_IN = "hdrp"
        const val HDR_PLAIN = "hdrplain"
        const val HDR_FINAL = "hdrfinal"
        const val SEGMENT_TOLERANCE_SECONDS = "0.05"

        /** Frame rate a still is looped at when the output keeps the source rate. */
        const val STILL_FRAME_RATE = 30

        /** [ImagePlacement.POSITION_MIN] and [ImagePlacement.POSITION_MAX] as clip() bounds. */
        private const val POSITION_RANGE = "-1,2"

        /** The sound of every clip before a cross-fade: one rate and layout. */
        private const val TRANSITION_SOUND = "aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo"

        /** Frame rate a picture laid over the video is repeated at when it is a stream (the image reader's). */
        const val OVERLAY_STREAM_RATE = 25

        /** Repeats a picture made once (a single frame) as an endless stream of frames. */
        const val STILL_LOOP = "loop=loop=-1:size=1"

        /** Frame rate the bitrate of the phone's encoder is worked out for when the source rate is kept. */
        const val DEFAULT_HARDWARE_FPS = 30

        /** Side assumed for a picture whose size is not known (it always should be). */
        const val DEFAULT_IMAGE_SIDE = 1080

        /** Length of a still segment whose end is not set (it never should be). */
        const val DEFAULT_STILL_MS = 3_000L
    }
}
