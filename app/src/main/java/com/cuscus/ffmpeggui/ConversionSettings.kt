package com.cuscus.ffmpeggui

import android.net.Uri
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.SplitMode
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.Timeline

/**
 * All user-configurable parameters for a single conversion, as the editor holds
 * them (content URIs, timecode strings).
 *
 * Effects exist at two levels: [wholeEffects] for the whole video, and the
 * effects of each clip, stored in the clip itself (see [Timeline]). Overlays are
 * attached to the whole video or to one clip through their `clipId`.
 *
 * Keeping them in one data class allows an atomic reset (reassign the default
 * instance). The mapping to the platform-free request lives in
 * conversion/RequestMapping.kt.
 */
data class ConversionSettings(
    // Footage: the files in use and how they are cut and ordered
    val sources: List<EditorSource> = emptyList(),
    val timeline: Timeline = Timeline(),

    // Trim of the whole sequence, on the timeline clock (null end = to the end)
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,

    // Video quality
    val resolution: Resolution = Resolution.ORIGINAL,
    val framerate: Framerate = Framerate.ORIGINAL,
    val qualityLevel: Float = 0.7f,
    /** HDR footage stays HDR in an MP4 or MKV (see EncodingSettings.keepHdr). */
    val keepHdr: Boolean = true,

    // Effects of the whole video: geometry, colour, speed, fades, sound
    val wholeEffects: Effects = Effects(),

    // Output frame and loudness: these only exist for the whole video
    val projectRatio: CanvasRatio = CanvasRatio.FIT,
    val normalizeAudio: Boolean = false,

    // The final sound (after the mix) and how an audio file is encoded
    val audioEffects: AudioEffects = AudioEffects.NONE,
    val audioEncoding: AudioEncoding = AudioEncoding(),

    // How a GIF is made (size, frame rate, colours, dithering, loops)
    val gif: GifSettings = GifSettings(),

    // What is done to the picture when the output is an image file (the turn, mirrors and
    // crop are those of wholeEffects; texts and stickers are the overlays below)
    val imageEdit: ImageEdit = ImageEdit(),

    // Sounds placed on the video (music, voice-overs, effects), in the order added
    val audioClips: List<AudioClip> = emptyList(),

    // Text overlays
    val textOverlays: List<TextOverlay> = emptyList(),

    // Custom / manual command
    val customCommand: String = "",
    val isManualMode: Boolean = false,
    val manualCommandOverride: String = "",

    // New feature: image overlays (watermark / logo)
    val imageOverlays: List<ImageOverlay> = emptyList(),

    // Subtitles burnt in: a file (timed against the main file), or the words heard in the files
    val subtitleUri: Uri? = null,
    val subtitleName: String = "",
    /**
     * Spoken subtitles: the words heard in each file, by source id, on that file's
     * own clock (a file listened to that says nothing has an empty list). They are
     * burnt in instead of a file.
     */
    val spokenWords: Map<String, List<SpokenWord>> = emptyMap(),
    /** How spoken subtitles (and SubRip files) look. */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),

    // New feature: stream copy (fast remux without re-encoding)
    val useStreamCopy: Boolean = false,

    // New feature: frame / thumbnail extraction
    val frameExtractionMode: FrameExtractionMode = FrameExtractionMode.DISABLED,
    /** Frames per second for SEQUENCE mode (e.g. 0.2 = one frame every 5 s). */
    val frameExtractionRate: Float = 1f,
    /** Timecode for SINGLE mode ("HH:MM:SS" or "HH:MM:SS.ms"). */
    val frameExtractionTimecode: String = "00:00:00",
    /**
     * The part of the sequence SEQUENCE mode takes frames from (its clock,
     * without the clips' speeds; null end: to the end). Only for one export
     * (see ConversionViewModel.startConversion): never kept in a project.
     */
    val frameRangeStartMs: Long = 0L,
    val frameRangeEndMs: Long? = null,

    // New feature: video stabilization (2-pass vidstab)
    val stabilize: Boolean = false,
    val stabilizeShakiness: Int = 5,
    val stabilizeSmoothing: Int = 10,

    // New feature: video split / segmenting
    val splitMode: SplitMode = SplitMode.DISABLED,
    val splitValue: Int = 10,

    // Tags written into the file: those read from the opened file, as the user changes them
    val tags: MetadataTags = MetadataTags(),

    // Cover picture of an audio file (an app-made square JPEG): the opened file's own, or one chosen
    val coverUri: Uri? = null,
) {
    /**
     * The clips that actually play: the timeline with the whole-sequence trim
     * applied. Falls back to the full timeline if the trim leaves nothing.
     */
    val playingClips: List<Clip>
        get() = timeline.window(trimStartMs, trimEndMs).ifEmpty { timeline.clips }

    fun source(id: String): EditorSource? = sources.firstOrNull { it.id == id }

    /** True when spoken subtitles are burnt in (some word was heard, and no file replaces them). */
    val hasSpokenSubtitles: Boolean
        get() = subtitleUri == null && spokenWords.values.any { it.isNotEmpty() }

    /** The effects edited when [clipId] is selected: that clip's, or the whole video's for null. */
    fun effectsOf(clipId: String?): Effects =
        if (clipId == null) wholeEffects else timeline.clips.firstOrNull { it.id == clipId }?.effects ?: Effects.NONE
}
