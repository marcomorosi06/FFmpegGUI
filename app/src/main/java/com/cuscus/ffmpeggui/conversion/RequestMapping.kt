package com.cuscus.ffmpeggui.conversion

import com.cuscus.ffmpeggui.preview.ofSounds
import com.cuscus.ffmpeggui.AudioClip
import com.cuscus.ffmpeggui.ConversionSettings
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.core.engine.CommandMode
import com.cuscus.ffmpeggui.core.engine.MediaSources
import com.cuscus.ffmpeggui.core.engine.NamedSource
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.EncodingSettings
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.OverlayVideo
import com.cuscus.ffmpeggui.core.model.PlacedSound
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.VideoHdr
import com.cuscus.ffmpeggui.core.model.Split
import com.cuscus.ffmpeggui.core.model.SplitMode
import com.cuscus.ffmpeggui.core.model.Stabilization
import com.cuscus.ffmpeggui.core.time.parseTimecodeMs
import com.cuscus.ffmpeggui.core.subtitles.SourceWords

/*
 * Translation from the editor state (URIs, timecode strings) to the
 * platform-free types the :core module works with. This is the single place
 * where the two worlds meet.
 */

/**
 * The footage of the edit, resolved once so that the request (what to do) and
 * the sources (where the files are) are guaranteed to line up.
 *
 * @param files     the distinct files the playing clips use, in order of first use.
 * @param segments  the playing clips, pointing into [files], with their own effects.
 * @param pictures  the picture overlays that will be drawn, in request order:
 *                  those of the whole video and those of clips that still play.
 */
internal class EditFootage(
    val files: List<EditorSource>,
    val segments: List<Segment>,
    val pictures: List<ImageOverlay>,
) {
    /** Position of the project's main file in [files], or null when none of its clips is left. */
    val mainFileIndex: Int? = files.indexOfFirst { it.id == EditorSource.MAIN_ID }.takeIf { it >= 0 }

    /** Ids of the clips that play. */
    val clipIds: Set<String> = segments.mapNotNull { it.clipId }.toSet()
}

internal fun ConversionSettings.footage(): EditFootage {
    val clips = playingClips.filter { source(it.sourceId) != null }
    val files = clips.map { it.sourceId }.distinct().mapNotNull(::source)
    val segments = clips.map { clip ->
        Segment(
            source = files.indexOfFirst { it.id == clip.sourceId },
            startMs = clip.startMs,
            endMs = clip.endMs,
            effects = clip.effects,
            clipId = clip.id,
        )
    }
    val clipIds = clips.map { it.id }.toSet()
    // A picture of a clip that the whole-video trim has cut away is not drawn.
    val pictures = imageOverlays.filter { it.uri != null && (it.clipId == null || it.clipId in clipIds) }
    return EditFootage(files, segments, pictures)
}

/** Describes what to do, without saying where the files are. */
internal fun ConversionSettings.toRequest(
    format: OutputFormat,
    inputFileName: String,
    footage: EditFootage = footage(),
): ConversionRequest = toRequestOf(format, inputFileName, footage).let { request ->
    // A GIF is pictures only, always encoded: no sound, no remux, no frame extraction
    // (whatever was set while another format was chosen).
    if (format == OutputFormat.GIF) {
        return@let request.copy(
            sounds = emptyList(),
            encoding = request.encoding.copy(streamCopy = false),
            frameExtraction = FrameExtraction.Disabled,
        )
    }
    if (!format.isImage) return@let request
    // A picture has no sound and no subtitles; its drawing is laid over it last, the
    // whole picture wide (see [drawingSource]).
    request.copy(
        sounds = emptyList(),
        subtitleSource = null,
        subtitleStyle = null,
        spokenWords = emptyList(),
        imageOverlays = request.imageOverlays +
            listOfNotNull(if (imageEdit.strokes.isNotEmpty()) ImagePlacement(x = 0f, y = 0f, widthFraction = 1f) else null),
    )
}

private fun ConversionSettings.toRequestOf(
    format: OutputFormat,
    inputFileName: String,
    footage: EditFootage,
): ConversionRequest = ConversionRequest(
    format = format,
    inputFileName = inputFileName,
    sources = footage.files.map { file ->
        SourceInfo(
            durationMs = file.durationMs,
            width = file.info?.width ?: 0,
            height = file.info?.height ?: 0,
            hasAudio = file.info?.hasAudio ?: true,
            isStill = file.isStill,
            hdr = file.info?.hdr ?: VideoHdr.NONE,
            frameRate = file.info?.frameRate ?: 0f,
        )
    }.ifEmpty { listOf(SourceInfo()) },
    segments = footage.segments.ifEmpty { listOf(Segment(source = 0)) },
    encoding = EncodingSettings(
        resolution = resolution,
        framerate = framerate,
        quality = qualityLevel,
        streamCopy = useStreamCopy,
        keepHdr = keepHdr,
        audio = audioEncoding,
        gif = gif,
    ),
    // A speed curve is a clip's alone.
    effects = wholeEffects.copy(speedCurve = null),
    audioEffects = audioEffects,
    canvas = projectRatio,
    normalizeAudio = normalizeAudio,
    sounds = audibleSounds().map { clip ->
        PlacedSound(
            startMs = clip.span.startMs,
            trimStartMs = clip.span.trimStartMs,
            trimEndMs = clip.span.trimEndMs,
            volume = clip.volume,
            fadeInSeconds = clip.fadeInSeconds,
            fadeOutSeconds = clip.fadeOutSeconds,
            speed = clip.span.rate,
            keepPitch = clip.keepPitch,
            pitchSemitones = clip.pitchSemitones,
            repeats = clip.span.loops,
            bassDb = clip.bassDb,
            midDb = clip.midDb,
            trebleDb = clip.trebleDb,
            normalize = clip.normalize,
            effects = clip.effects,
            keyframes = clip.keyframes,
        )
    } + overlaySounds(footage).map { overlay ->
        // The sound of a video laid over the picture: it plays with the video and fades with it.
        val media = requireNotNull(overlay.video)
        PlacedSound(
            startMs = media.startMs,
            trimStartMs = media.trimStartMs,
            trimEndMs = media.trimEndMs,
            volume = overlay.volume,
            fadeInSeconds = overlay.fadeInSeconds,
            fadeOutSeconds = overlay.fadeOutSeconds,
        )
    },
    textOverlays = textOverlays.filter { it.clipId == null || it.clipId in footage.clipIds },
    imageOverlays = footage.pictures.map {
        ImagePlacement(
            x = it.x,
            y = it.y,
            widthFraction = it.scaleW,
            opacity = it.opacity,
            clipId = it.clipId,
            startMs = it.startMs,
            endMs = it.endMs,
            fadeInSeconds = it.fadeInSeconds,
            fadeOutSeconds = it.fadeOutSeconds,
            rotationDegrees = it.rotationDegrees,
            flipHorizontal = it.flipHorizontal,
            flipVertical = it.flipVertical,
            brightness = it.brightness,
            contrast = it.contrast,
            saturation = it.saturation,
            video = it.video?.let { media -> OverlayVideo(media.trimStartMs, media.trimEndMs) },
            key = it.key,
            keyframes = it.keyframes,
            aspect = it.aspect,
            look = it.look,
            layer = it.layer,
        )
    },
    // The subtitle file is timed against the main file; without it there is nothing to burn.
    subtitleSource = if (subtitleUri != null) footage.mainFileIndex else null,
    // Spoken subtitles: the words of every file that plays, each following its own clips.
    spokenWords = if (hasSpokenSubtitles) {
        footage.files.mapIndexedNotNull { index, file -> spokenWords[file.id]?.takeIf { it.isNotEmpty() }?.let { SourceWords(index, it) } }
    } else {
        emptyList()
    },
    // And those of the sounds placed on the video, where each one plays.
    placedWords = if (hasSpokenSubtitles) spokenWords.ofSounds(audioClips, wholeEffects.effectiveSpeed) else emptyList(),
    subtitleStyle = if (subtitleUri != null || hasSpokenSubtitles) subtitleStyle else null,
    frameExtraction = when (frameExtractionMode) {
        FrameExtractionMode.DISABLED -> FrameExtraction.Disabled
        FrameExtractionMode.SINGLE -> FrameExtraction.SingleFrame(parseTimecodeMs(frameExtractionTimecode))
        FrameExtractionMode.SEQUENCE -> FrameExtraction.Sequence(frameExtractionRate, frameRangeStartMs, frameRangeEndMs)
    },
    stabilization = if (stabilize) Stabilization(stabilizeShakiness, stabilizeSmoothing) else null,
    split = when (splitMode) {
        SplitMode.DISABLED -> Split.Disabled
        SplitMode.BY_DURATION -> Split.ByDuration(splitValue)
        SplitMode.BY_PARTS -> Split.ByParts(splitValue)
    },
    image = imageEdit.keyFor(format),
    metadata = tags,
    cover = coverFor(format) != null,
)

/**
 * Says where the files are. Built from the same [EditFootage] as the request.
 * Only an audio file ([format]) gets the cover. For a picture ([format] an
 * image) there are no sounds or subtitles, and [drawing] (the drawing made
 * into a picture) comes after the stickers.
 */
internal fun ConversionSettings.toSources(
    footage: EditFootage = footage(),
    format: OutputFormat? = null,
    drawing: String? = null,
): MediaSources = toSourcesOf(footage, format).let { sources ->
    // A GIF has no sound: nothing to copy for it.
    if (format == OutputFormat.GIF) return@let sources.copy(sounds = emptyList())
    if (format?.isImage != true) return@let sources
    require((drawing != null) == imageEdit.strokes.isNotEmpty()) { "The drawing must be made before the export" }
    sources.copy(
        sounds = emptyList(),
        subtitles = null,
        imageOverlays = sources.imageOverlays + listOfNotNull(drawing?.let { NamedSource(it, "drawing.png") }),
    )
}

private fun ConversionSettings.toSourcesOf(footage: EditFootage, format: OutputFormat?): MediaSources = MediaSources(
    inputs = footage.files.map { NamedSource(it.uri.toString(), it.stagedName()) },
    sounds = audibleSounds().map { NamedSource(it.uri.toString(), it.name.ifBlank { "sound" }) } +
        overlaySounds(footage).mapNotNull { overlay -> overlay.uri?.let { NamedSource(it.toString(), overlay.fileName.ifBlank { "overlay_video" }) } },
    imageOverlays = footage.pictures.mapNotNull { overlay ->
        overlay.uri?.let { NamedSource(it.toString(), overlay.fileName.ifBlank { if (overlay.isVideo) "overlay.mp4" else "overlay.png" }) }
    },
    subtitles = subtitleUri
        ?.takeIf { footage.mainFileIndex != null }
        ?.let { NamedSource(it.toString(), subtitleName.ifBlank { "subtitles.srt" }) },
    cover = coverFor(format)?.let { NamedSource(it.toString(), "cover.jpg") },
)

/**
 * The videos laid over the picture whose sound is heard (the export mixes it in
 * after the placed sounds). Request and sources both use this.
 */
private fun ConversionSettings.overlaySounds(footage: EditFootage): List<ImageOverlay> =
    footage.pictures.filter { it.clipId == null && it.video != null && it.hasAudio && !it.muted && it.uri != null }

/** The cover the export writes: only an audio file has one. Request and sources both use this. */
private fun ConversionSettings.coverFor(format: OutputFormat?) = coverUri?.takeIf { format?.isAudioOnly == true }

/** The sounds the export mixes in: a muted one is left out altogether. Request and sources both use this. */
private fun ConversionSettings.audibleSounds(): List<AudioClip> = audioClips.filter { !it.muted }

/** A hand-edited command wins over everything; the custom format is always hand-written. */
internal fun ConversionSettings.commandMode(format: OutputFormat): CommandMode = when {
    isManualMode -> CommandMode.Manual(manualCommandOverride)
    format == OutputFormat.CUSTOM -> CommandMode.Manual(customCommand)
    else -> CommandMode.Generated
}

/**
 * The name a file is copied under for FFmpeg. A still is always a PNG made by the
 * app, whatever the picture it came from was called, and FFmpeg reads a looped
 * picture by its extension.
 */
private fun EditorSource.stagedName(): String {
    val name = fileName.ifBlank { "input" }
    return if (isStill) name.substringBeforeLast('.') + ".png" else name
}
