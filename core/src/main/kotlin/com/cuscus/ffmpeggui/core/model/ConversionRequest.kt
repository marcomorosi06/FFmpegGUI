package com.cuscus.ffmpeggui.core.model

import com.cuscus.ffmpeggui.core.preview.frameRatio
import com.cuscus.ffmpeggui.core.subtitles.SourceWords
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.onTimeline
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle

/**
 * Everything the user asked for, with no file locations attached.
 *
 * A request is pure data: the same instance is used to render the command
 * preview (paired with alias paths) and to run the conversion (paired with the
 * real, staged paths). See [com.cuscus.ffmpeggui.core.command.MediaPaths].
 *
 * The footage is described as a sequence of [segments], each one a range of
 * one of the [sources] with its own [Effects]. The segments are joined and
 * [effects] is applied to the result. A plain conversion is a single segment
 * covering the whole first source.
 */
data class ConversionRequest(
    val format: OutputFormat,
    /** Display name of the project's main file; drives the output base name. */
    val inputFileName: String,
    /** The distinct media files used by [segments]. */
    val sources: List<SourceInfo> = listOf(SourceInfo()),
    /** What plays, in order. Never empty. */
    val segments: List<Segment> = listOf(Segment(source = 0)),
    val encoding: EncodingSettings = EncodingSettings(),
    /** Effects of the whole video, applied once the segments are joined. */
    val effects: Effects = Effects(),
    /** Shape of the output frame; the picture is padded with black to reach it. */
    val canvas: CanvasRatio = CanvasRatio.FIT,
    /** Evens out the loudness of the final mix (loudnorm). */
    val normalizeAudio: Boolean = false,
    /** What is done to the final sound once everything is mixed (see [AudioEffects]). */
    val audioEffects: AudioEffects = AudioEffects.NONE,
    /** For a picture file: what is done to the picture (see [ImageEdit]). */
    val image: ImageEdit = ImageEdit(),
    /**
     * Sounds placed freely on the output clock (music, voice-overs, effects),
     * mixed with the source audio or in its place. They may overlap.
     */
    val sounds: List<PlacedSound> = emptyList(),
    /** Captions; each one belongs to the whole video or to one segment (see [TextOverlay.clipId]). */
    val textOverlays: List<TextOverlay> = emptyList(),
    /** One entry per picture overlay, in stacking order; bound like [textOverlays]. */
    val imageOverlays: List<ImagePlacement> = emptyList(),
    /**
     * Index of the source the subtitle file is timed against, or null when no
     * subtitles are burnt in. The lines follow that footage through every cut.
     */
    val subtitleSource: Int? = null,
    /**
     * How burnt-in subtitles look. Spoken subtitles ([spokenWords]) always have
     * a style; a SubRip file takes it too, a SubStation Alpha file keeps its own
     * look. Null draws a subtitle file as it is.
     */
    val subtitleStyle: SubtitleStyle? = null,
    /**
     * The words heard in the sources, burnt in as subtitles with [subtitleStyle]
     * instead of a file. Each word follows its footage through every cut.
     */
    val spokenWords: List<SourceWords> = emptyList(),
    /**
     * The words heard in the sounds placed on the video (music, voice-overs),
     * already where they are said on the edited sequence (the clock the words
     * of [spokenWords] are moved onto), burnt in with them.
     */
    val placedWords: List<SpokenWord> = emptyList(),
    val frameExtraction: FrameExtraction = FrameExtraction.Disabled,
    val stabilization: Stabilization? = null,
    val split: Split = Split.Disabled,
    val metadata: MetadataTags = MetadataTags(),
    /**
     * A picture is set as the cover of the audio file (album art); where it is
     * comes with the paths. Formats that cannot hold one leave it out (see
     * `coverWriting` in the command package).
     */
    val cover: Boolean = false,
) {
    init {
        require(segments.isNotEmpty()) { "A request needs at least one segment" }
        require(segments.all { it.source in sources.indices }) { "A segment refers to a missing source" }
    }

    /** Extension of the main file, used for display aliases. */
    val inputExtension: String
        get() = inputFileName.substringAfterLast('.', "mp4").ifBlank { "mp4" }

    /** True when subtitles are burnt in: a file, or the words heard. */
    val burnsSubtitles: Boolean
        get() = subtitleSource != null || hasSpokenSubtitles

    /** True when the subtitles are the words heard rather than a file. */
    val hasSpokenSubtitles: Boolean
        get() = subtitleStyle != null && (spokenWords.any { it.words.isNotEmpty() } || placedWords.isNotEmpty())

    /**
     * Every word heard, on the edited sequence: those of the sources moved onto
     * their footage, and those of the placed sounds. Sorted by start.
     */
    fun timedWords(): List<SpokenWord> =
        (spokenWords.onTimeline(segments, effects.reversed) + placedWords).sortedBy { it.startMs }

    /**
     * Shape (width / height) of the frame the subtitles are drawn on: the first
     * clip's file (every clip is fitted into it), turned and cropped as the
     * whole video is, then the canvas. The editor's preview uses the same.
     */
    val subtitleFrameRatio: Float
        get() {
            val first = sources.getOrNull(segments.first().source)
            val file = if (first != null && first.width > 0 && first.height > 0) first.width.toFloat() / first.height else DEFAULT_FRAME_RATIO
            return canvas.ratio ?: effects.frameRatio(file)
        }

    /** True when the footage is cut from more than one range. */
    val isMultiSegment: Boolean
        get() = segments.size > 1

    /** True when at least one segment comes from a source with a sound stream. */
    val hasSourceAudio: Boolean
        get() = segments.any { sources[it.source].hasAudio }

    private companion object {
        /** Shape assumed for a file whose size is not known. */
        const val DEFAULT_FRAME_RATIO = 16f / 9f
    }

    /** Captions drawn on the joined video. */
    val wholeTextOverlays: List<TextOverlay>
        get() = textOverlays.filter { it.clipId == null }

    /** Captions that belong to [segment]. */
    fun textOverlaysOf(segment: Segment): List<TextOverlay> =
        if (segment.clipId == null) emptyList() else textOverlays.filter { it.clipId == segment.clipId }

    /** True when some segment has effects or overlays of its own. */
    val hasClipEdits: Boolean
        get() = segments.any { segment ->
            !segment.effects.isNeutral ||
                (segment.clipId != null && (textOverlays.any { it.clipId == segment.clipId } ||
                    imageOverlays.any { it.clipId == segment.clipId }))
        }
}

/**
 * What is known about one media file. Zero / default values mean "unknown".
 * [width] and [height] are the displayed size, i.e. after the rotation flag
 * stored in the file has been applied.
 */
data class SourceInfo(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val hasAudio: Boolean = true,
    /**
     * A single picture: a segment of it shows the picture for its length (the
     * range only gives the length; a still has no clock to seek on).
     */
    val isStill: Boolean = false,
    /** HDR footage: its picture is tone mapped to SDR before anything else (see VideoHdr). */
    val hdr: VideoHdr = VideoHdr.NONE,
    /** Frames per second of its picture, 0 when unknown (a still, or not probed). */
    val frameRate: Float = 0f,
)

/**
 * A range of one source, on that source's own clock, with the effects of that
 * clip. A null [endMs] means "until the end" and is only used while the length
 * of the source is not known.
 *
 * [clipId] identifies the clip in the editor; overlays bound to a clip refer
 * to it. Null for footage that is not a clip (a plain conversion).
 */
data class Segment(
    val source: Int,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val effects: Effects = Effects.NONE,
    val clipId: String? = null,
) {
    /** Length of the range, or null when it is open-ended. */
    val durationMs: Long?
        get() = endMs?.let { (it - startMs).coerceAtLeast(0L) }

    /** How long the segment plays once its own speed is applied, or null when open-ended. */
    val playedDurationMs: Long?
        get() = durationMs?.let { effects.playedMs(it) }
}

data class EncodingSettings(
    val resolution: Resolution = Resolution.ORIGINAL,
    val framerate: Framerate = Framerate.ORIGINAL,
    /** 0 = smallest file, 1 = best quality. */
    val quality: Float = 0.7f,
    /** Remux without re-encoding; every filter is ignored. */
    val streamCopy: Boolean = false,
    /** How an audio file's sound is encoded. */
    val audio: AudioEncoding = AudioEncoding(),
    /** How a GIF is made (its size, frame rate and colours replace the settings above). */
    val gif: GifSettings = GifSettings(),
    /**
     * The phone's own video encoder, checked on this phone (see [HardwareEncoder]):
     * when given, the picture of an MP4 or MKV is encoded by it (MediaCodec, through
     * FFmpeg's h264_mediacodec) instead of x264, many times faster. [quality] then
     * sets a bitrate instead of a CRF. See
     * [com.cuscus.ffmpeggui.core.command.CommandBuilder.usesHardwareEncoder] for
     * when it applies; the engine falls back on x264 when it fails.
     */
    val hardwareEncoder: HardwareEncoder? = null,
    /** How hard x264 works when the processor encodes the picture (also after the phone's encoder failed). */
    val cpuSpeed: CpuEncoderSpeed = CpuEncoderSpeed.BALANCED,
    /**
     * HDR footage stays HDR in an MP4 or MKV (its transfer curve and BT.2020
     * colours written on the video, see VideoHdr) rather than being tone mapped
     * to SDR. Only when every clip is HDR of the same kind; otherwise, and for
     * every other format, HDR clips are tone mapped.
     */
    val keepHdr: Boolean = true,
)

/**
 * A phone video encoder that was found to work with FFmpeg on this phone:
 * [codecName] (MediaCodec's name, "c2.qti.avc.encoder" say), fed [pixelFormat]
 * ("nv12" or "yuv420p"), with the picture's sides brought to multiples of
 * [widthAlign] and [heightAlign]. FFmpeg 6.1 hands the encoder frames packed
 * row after row; an encoder that reads them with padding of its own makes a
 * torn picture, which is why the app checks the encoder before using it.
 */
data class HardwareEncoder(
    val codecName: String,
    val pixelFormat: String = "nv12",
    val widthAlign: Int = 16,
    val heightAlign: Int = 16,
)

/**
 * A sound file placed on the exported video: the part of the file from
 * [trimStartMs] to [trimEndMs] (null = to its end) plays [repeats] times in a
 * row from [startMs] of the output, at its own [speed] whatever the speed of
 * the footage. Its fades are its own; the fades of the whole video apply on
 * top. Whatever runs past the end of the video is cut.
 *
 * Sound shaping, in the order it is applied: speed (with the pitch kept when
 * [keepPitch], else rising and falling with it like a tape) and a pitch shift
 * of [pitchSemitones]; a three-band equaliser ([bassDb], [midDb], [trebleDb]);
 * its own [effects] (as those of the final sound, without the pitch); loudness
 * levelling ([normalize]); then [volume].
 */
data class PlacedSound(
    val startMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val volume: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val speed: Float = 1f,
    val keepPitch: Boolean = true,
    val pitchSemitones: Float = 0f,
    val repeats: Int = 1,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val normalize: Boolean = false,
    val effects: AudioEffects = AudioEffects.NONE,
    /** How [volume] changes while it plays (see [Keyframes]; times from [startMs] on the video). */
    val keyframes: Keyframes = Keyframes.NONE,
) {
    /** Playback rate, never zero or negative. */
    val rate: Float get() = if (speed > 0f) speed else 1f

    /** How many times the part plays, at least once. */
    val loops: Int get() = repeats.coerceAtLeast(1)

    /** How long it plays on the video, or null when the end of its part is not known. */
    val lengthMs: Long?
        get() = trimEndMs?.let { Math.round((it - trimStartMs).coerceAtLeast(0L).toDouble() * loops / rate) }
}

/**
 * Where a picture overlay goes.
 *
 * [x] and [y] are fractions (0..1) of the free space around the overlay, the
 * same convention as [TextOverlay]. [widthFraction] is the overlay width
 * relative to the width of the frame it is drawn on: the final frame, or the
 * clip's own frame when [clipId] binds it to a clip.
 */
data class ImagePlacement(
    val x: Float = 0.05f,
    val y: Float = 0.05f,
    val widthFraction: Float = 0.20f,
    val opacity: Float = 1f,
    /** Clip it belongs to (see [Segment.clipId]); null = the whole video. */
    val clipId: String? = null,
    /** When a picture of the whole video shows, on the exported video's clock (null end = to the end). */
    val startMs: Long = 0L,
    val endMs: Long? = null,
    /** Its own fades, over the time it shows (whole video only). */
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    /** Turn in degrees (clockwise), then mirror images; the turn does not change [widthFraction]. */
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    /** Picture adjustments, as the eq filter takes them (0, 1 and 1 = untouched). */
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    /**
     * A video laid over the picture rather than a still one: the part of its
     * file that plays from [startMs] (whole video only; its sound, if any, is
     * mixed in as a placed sound by whoever builds the request).
     */
    val video: OverlayVideo? = null,
    /**
     * A colour cut out of it (a green screen), after its own colours: what is
     * cut lets the video show through. Its own transparent parts stay so.
     */
    val key: ColorKey? = null,
    /**
     * How its place, size, turn and opacity change while it shows (see
     * [Keyframes]; times from [startMs], or from its clip's start for a picture
     * of a clip).
     */
    val keyframes: Keyframes = Keyframes.NONE,
    /** Width / height of the picture upright (as it is laid over), 0 when unknown: needed to place it while it turns. */
    val aspect: Float = 0f,
    /** The part of it that shows, its shape, blur, outline and shadow (see [OverlayLook]). */
    val look: OverlayLook = OverlayLook.NONE,
    /**
     * Where it lies among the captions and pictures of its clip (or of the
     * whole video): higher is on top (see [stackOrder]).
     */
    val layer: Int = 0,
) {
    /** True when the picture fades, which needs it as a stream of frames rather than one. */
    val fades: Boolean get() = clipId == null && (fadeInSeconds > 0f || (fadeOutSeconds > 0f && endMs != null))

    /** True when its place, size, turn or opacity change over time. */
    val animated: Boolean get() = keyframes.animatesAny(Keyframes.OVERLAY)

    /** True when it is read as a stream of frames (it fades or moves) rather than one. */
    val streamed: Boolean get() = fades || animated

    /** True when its colours are changed. */
    val adjustsColor: Boolean get() = brightness != 0f || contrast != 1f || saturation != 1f

    companion object {
        /** Widest a picture is laid, a fraction of the frame: beyond 1 it fills the frame and is cut by it. */
        const val MAX_WIDTH = 4f

        /**
         * How far a caption or picture can be placed ([x], [y], as fractions of the
         * free space): past 0 and 1 it goes over the edges, where the frame cuts it.
         */
        const val POSITION_MIN = -1f
        const val POSITION_MAX = 2f
    }
}

/** The part of a video file that plays as an overlay, from [trimStartMs] to [trimEndMs] of the file. */
data class OverlayVideo(val trimStartMs: Long = 0L, val trimEndMs: Long? = null)

sealed interface FrameExtraction {
    data object Disabled : FrameExtraction

    /** One PNG taken at [positionMs] of the edited sequence. */
    data class SingleFrame(val positionMs: Long) : FrameExtraction

    /**
     * Numbered pictures sampled at [framesPerSecond], from [startMs] to
     * [endMs] (null: the end) of the edited sequence: in the picture format
     * asked for (see [sequenceFormat]), PNG for a video or a sound format.
     */
    data class Sequence(val framesPerSecond: Float, val startMs: Long = 0L, val endMs: Long? = null) : FrameExtraction
}

/**
 * The picture format the frames of a sequence are saved in: the one asked for
 * when it is a picture format FFmpeg writes, PNG otherwise (a WebP would need
 * the phone's encoder for each frame).
 */
fun ConversionRequest.sequenceFormat(): OutputFormat =
    format.takeIf { it.isImage && !it.needsPlatformEncoder } ?: OutputFormat.PNG

/** Two-pass vid.stab stabilization. */
data class Stabilization(
    val shakiness: Int = 5,
    val smoothing: Int = 10,
)

sealed interface Split {
    data object Disabled : Split
    data class ByDuration(val seconds: Int) : Split
    data class ByParts(val count: Int) : Split
}

/**
 * The tags written into the output. FFmpeg carries the input's own tags over by
 * itself; these are set on top of them. When [fromSource] is true the fields
 * were filled from the input's tags (see `MetadataReader`), so they are the
 * whole truth: a field left blank removes that tag instead of keeping the
 * input's.
 */
data class MetadataTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val comment: String = "",
    val genre: String = "",
    val copyright: String = "",
    val fromSource: Boolean = false,
) {
    /**
     * FFmpeg metadata keys mapped to their values, in a stable order: the non-blank
     * ones, and with [fromSource] the blank ones too (as empty values, which make
     * FFmpeg drop the tag).
     */
    fun asMap(): Map<String, String> = linkedMapOf(
        "title" to title,
        "artist" to artist,
        "album" to album,
        "date" to year,
        "genre" to genre,
        "comment" to comment,
        "copyright" to copyright,
    ).mapValues { it.value.trim() }.filterValues { fromSource || it.isNotEmpty() }

    /** True when there is nothing to write and nothing to remove. */
    val isEmpty: Boolean get() = asMap().isEmpty()
}
