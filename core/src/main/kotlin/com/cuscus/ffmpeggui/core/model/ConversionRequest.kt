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
    /** Shape of the output frame; the picture meets it as [canvasFit] says. */
    val canvas: CanvasRatio = CanvasRatio.FIT,
    /** How the picture meets a [canvas] of another shape: black around it, cut, or stretched. */
    val canvasFit: CanvasFit = CanvasFit.FIT,
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
     * True when a picture seen in the output was changed by an AI model (the
     * magic eraser, or one another program marked so): the output says so
     * (AI Act, Article 50(2); see aiDisclosure), never by which model.
     */
    val aiEditedPictures: Boolean = false,
    /**
     * The picture looks laid over the whole video (black and white, a glitch...,
     * see PictureLook), on the exported video's clock: each changes everything
     * under it, after the captions and pictures, in this order.
     */
    val looks: List<LookSpan> = emptyList(),
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
    /**
     * Where the picture lies on its canvas ([ImageEdit.canvas]), as a picture
     * laid on it: place, size (a fraction of the canvas width), turn, opacity,
     * look, layer among the texts and stickers, blend. Null: centred, as large
     * as the canvas holds it whole.
     */
    val photoPlacement: ImagePlacement? = null,
    /**
     * Made by the compressor (see [Compression]): a video by x265 at a CRF or,
     * to fit a size, in two passes; a picture or a sound squeezed as it says.
     */
    val compression: Compression? = null,
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

    /**
     * True when a picture file is made from a picture with transparent parts of
     * its own, in a format that keeps them: they stay transparent (see
     * [com.cuscus.ffmpeggui.core.image.withSourceAlpha]). Elsewhere (JPG, BMP, a
     * video) the picture is read opaque, as before.
     */
    val keepsPictureAlpha: Boolean
        get() {
            if (!format.isImage || !format.holdsTransparency || frameExtraction != FrameExtraction.Disabled) return false
            val source = sources.getOrNull(segments.first().source) ?: return false
            return source.isStill && source.hasAlpha
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
    /** A picture with transparent parts of its own (a PNG with an alpha channel, say). */
    val hasAlpha: Boolean = false,
    /**
     * Its sound separated into the voice and the rest (see VoiceStem): the
     * four-channel file, on the file's own clock; null when not separated.
     */
    val voiceStem: String? = null,
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
    /** The machine that made the voice in it (a dub), when one did: the output says so in its tags. */
    val aiVoice: String? = null,
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
    /** How it mixes with what lies under it (see [LayerBlend]). */
    val blend: LayerBlend = LayerBlend.NORMAL,
    /**
     * Fills the whole frame, its shape kept and the overflow cut (as a cover
     * fill): [x], [y] and [widthFraction] are then not used (whole video only).
     */
    val cover: Boolean = false,
    /** Its colours turned round the colour wheel by this many degrees (0 = untouched). */
    val hueDegrees: Float = 0f,
    /**
     * With [cover], how large the fill is beside the frame's (1 the frame's
     * size; smaller fills a smaller frame in the middle, and a video with
     * [stackedAlpha] then fades out towards that frame's edges, over
     * [COVER_FEATHER] of its shorter side).
     */
    val coverSize: Float = 1f,
    /**
     * A video packed with its opacity: each frame its colours on top and its
     * opacity (grey) below, twice as tall as the picture (the app's effects
     * that hide what is behind them, a 3D fist).
     */
    val stackedAlpha: Boolean = false,
    /**
     * How many times wider than its own shape it is laid ([widthFraction] wide,
     * this much less high): 1 keeps its shape. Pulled to the frame's shape, a
     * picture fills it with nothing cut (see ImageEditing's stretchedPhoto). Its
     * look (corners, outline, shadow) is drawn on it once stretched.
     */
    val stretch: Float = 1f,
) {
    /** [stretch] within reasonable bounds (1 when it hardly changes the shape). */
    val saneStretch: Float
        get() = saneStretch(stretch)

    /** Width / height of the picture as it is laid ([aspect] times [stretch]), 0 when unknown. */
    val laidPictureAspect: Float
        get() = if (aspect > 0f) aspect * saneStretch else 0f

    /** True when the picture fades, which needs it as a stream of frames rather than one. */
    val fades: Boolean get() = clipId == null && (fadeInSeconds > 0f || (fadeOutSeconds > 0f && endMs != null))

    /** True when its place, size, turn or opacity change over time. */
    val animated: Boolean get() = keyframes.animatesAny(Keyframes.OVERLAY)

    /** True when it is read as a stream of frames (it fades or moves) rather than one. */
    val streamed: Boolean get() = fades || animated

    /** True when its colours are changed. */
    val adjustsColor: Boolean get() = brightness != 0f || contrast != 1f || saturation != 1f

    /** True when its colours are turned round the colour wheel. */
    val turnsHue: Boolean get() = (((hueDegrees % 360f) + 360f) % 360f).let { it > 0.5f && it < 359.5f }

    companion object {
        /** Widest a picture is laid, a fraction of the frame: beyond 1 it fills the frame and is cut by it. */
        const val MAX_WIDTH = 4f

        /** Furthest a picture is pulled out of its shape ([stretch]): a quarter, or four times, as wide. */
        const val MIN_STRETCH = 0.25f
        const val MAX_STRETCH = 4f

        /** [stretch] within [MIN_STRETCH]..[MAX_STRETCH], 1 when it hardly changes the shape (or is not a number). */
        fun saneStretch(stretch: Float): Float =
            stretch.takeIf { it.isFinite() && kotlin.math.abs(it - 1f) > 1e-3f }?.coerceIn(MIN_STRETCH, MAX_STRETCH) ?: 1f

        /**
         * How far a caption or picture can be placed ([x], [y], as fractions of the
         * free space): past 0 and 1 it goes over the edges, where the frame cuts it.
         */
        const val POSITION_MIN = -1f
        const val POSITION_MAX = 2f

        /** A [coverSize] below this is smaller than the frame. */
        const val FULL_COVER = 0.999f

        /**
         * How far in from its edges a smaller [cover] with [stackedAlpha] fades
         * out, a fraction of its shorter side (the export and the preview alike).
         */
        const val COVER_FEATHER = 0.18
    }
}

/**
 * The part of a video file that plays as an overlay, from [trimStartMs] to
 * [trimEndMs] of the file; with [loop], the whole file played over and over
 * for as long as the overlay shows (the trim is then not used).
 */
data class OverlayVideo(val trimStartMs: Long = 0L, val trimEndMs: Long? = null, val loop: Boolean = false)

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
    /** The artist of the whole album, when it is not the track's (a compilation, a featuring). */
    val albumArtist: String = "",
    /**
     * The words of the song: plain lines, or synced lines in the LRC way
     * ("[mm:ss.xx]line", see `core.subtitles.LyricsText`), which players that
     * know it follow as the song plays. MP3 keeps them as USLT, M4A as its
     * lyrics atom, FLAC, Ogg and Opus as LYRICS.
     */
    val lyrics: String = "",
    val fromSource: Boolean = false,
    /**
     * Every other tag, in order: the source's own (read with it, see
     * [sourceKeys]) and those added by hand. FFmpeg keys for sound and video
     * ("location", "com.android.model", "creation_time"...), EXIF names for
     * pictures ("Model", "GPSLatitude", "DateTimeOriginal"...). See [TagKind].
     */
    val custom: List<CustomTag> = emptyList(),
    /** Keys of [custom] the source had: one no longer there is removed from the file. */
    val sourceKeys: Set<String> = emptySet(),
    /** Remove every tag of the source, written or not here (`-map_metadata -1`): only these are written. */
    val stripAll: Boolean = false,
    /**
     * Remove where the file was made and with what ([TagKind.sensitive]) even
     * where it was not read: the usual keys of phones and cameras are removed.
     */
    val stripSensitive: Boolean = false,
) {
    /**
     * FFmpeg metadata keys mapped to their values, in a stable order: the non-blank
     * ones, and with [fromSource] the blank ones too (as empty values, which make
     * FFmpeg drop the tag); then the other tags ([custom]), those of the source
     * taken away (empty), and with [stripSensitive] the usual sensitive keys (empty).
     */
    fun asMap(): Map<String, String> {
        val map = linkedMapOf(
            "title" to title,
            "artist" to artist,
            "album" to album,
            "date" to year,
            "genre" to genre,
            "comment" to comment,
            "copyright" to copyright,
            "album_artist" to albumArtist,
            "lyrics" to lyrics,
        ).mapValues { it.value.trim() }.filterValues { fromSource || it.isNotEmpty() }.toMutableMap()
        val known = KNOWN_KEYS
        for (tag in custom) {
            val key = tag.key.trim()
            if (key.isEmpty() || key in known) continue
            val value = tag.value.trim()
            if (value.isNotEmpty() || key in sourceKeys) map[key] = value
        }
        for (key in sourceKeys) if (key !in map && key !in known) map[key] = ""
        if (stripSensitive) for (key in SENSITIVE_FFMPEG_KEYS) if (map[key].isNullOrEmpty()) map[key] = ""
        return map
    }

    /** True when there is nothing to write and nothing to remove. */
    val isEmpty: Boolean get() = !stripAll && asMap().isEmpty()

    /** True when a tag of [custom] is not one every container knows (an MP4 then keeps it only when told to). */
    val hasOwnTags: Boolean get() = custom.any { it.key.isNotBlank() && it.value.isNotBlank() && it.key.trim() !in KNOWN_KEYS }

    /** These tags without where the file was made and with what ([TagKind.sensitive]), which are removed. */
    fun withoutSensitive(): MetadataTags = copy(custom = custom.filter { !tagKind(it.key).sensitive }, stripSensitive = true)

    /** No tag at all: every field empty and every tag of the source removed. */
    fun cleared(): MetadataTags = MetadataTags(fromSource = fromSource, stripAll = true, stripSensitive = true)

    companion object {
        /** The keys of the fields above. */
        val KNOWN_KEYS = setOf("title", "artist", "album", "date", "genre", "comment", "copyright", "album_artist", "lyrics")

        /** Where and with what phones and cameras say a video or a sound was made. */
        val SENSITIVE_FFMPEG_KEYS = listOf(
            "location", "location-eng", "com.apple.quicktime.location.ISO6709",
            "com.android.manufacturer", "com.android.model",
            "com.apple.quicktime.make", "com.apple.quicktime.model", "com.apple.quicktime.software",
            "make", "model",
        )
    }
}

/** A tag of the file besides the usual ones: [key] as the file names it, its [value]. */
data class CustomTag(val key: String, val value: String)
