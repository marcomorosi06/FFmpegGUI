package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.annotation.StringRes
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.OverlayLook
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.timeline.LaneSpan
import com.cuscus.ffmpeggui.core.timeline.TimeSpan
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import java.util.UUID

/*
 * Editor-side models: what the user picked, still expressed as content URIs.
 * They are mapped to the platform-free types of the :core module right before
 * a command is built (see conversion/RequestMapping.kt).
 */

/**
 * A media file the project uses. Clips on the timeline refer to it by [id].
 * [info] is filled in once the file has been inspected.
 */
data class EditorSource(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val info: MediaInfo? = null,
) {
    /** Full length of the file, 0 while unknown. */
    val durationMs: Long get() = info?.durationMs ?: 0L

    /** True for a picture (an imported photo or a freeze frame): shown for as long as its clip lasts. */
    val isStill: Boolean get() = info?.isStill == true

    /** Width / height of the picture as displayed, or null while unknown. */
    val frameRatio: Float?
        get() = info?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height.toFloat() }

    companion object {
        /** Id of the file the project was opened with. Subtitles are timed against it. */
        const val MAIN_ID = "main"
    }
}

/**
 * A picture (sticker, logo, watermark) or a video (picture in picture) drawn
 * over the video.
 *
 * [x] and [y] are fractions (0..1) of the free space around it, so 0 is the
 * left / top edge and 1 the right / bottom edge. [scaleW] is its width (upright)
 * as a fraction of the video width. [opacity] goes from 0 to 1. It shows from
 * [startMs] to [endMs] (null = to the end) of the exported video, with its own
 * fades, turned by [rotationDegrees] (clockwise), mirrored by the flips and
 * with its colours adjusted ([brightness], [contrast], [saturation], as eq).
 *
 * For a video, [video] says which part of the file plays and when (its start
 * and end are also [startMs] / [endMs]); [aspect] is its frame shape (width /
 * height, 0 = unknown); its sound, if [hasAudio], is mixed in at [volume]
 * unless [muted].
 *
 * [clipId] attaches a picture to one clip instead (for the whole clip); the
 * editor places everything new freely.
 */
data class ImageOverlay(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val fileName: String = "",
    val x: Float = 0.05f,
    val y: Float = 0.05f,
    val scaleW: Float = 0.20f,
    val opacity: Float = 1.0f,
    val clipId: String? = null,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val video: SoundSpan? = null,
    val aspect: Float = 0f,
    val hasAudio: Boolean = false,
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** A colour cut out of it (a green screen): what is cut lets the video show through. */
    val key: ColorKey? = null,
    /** How its place, size, turn and opacity change while it shows (times from [startMs]). */
    val keyframes: Keyframes = Keyframes.NONE,
    /** The part of it that shows, its shape, blur, outline and shadow. */
    val look: OverlayLook = OverlayLook.NONE,
    /** Where it lies among the captions and pictures of its clip or of the whole video (see stackOrder). */
    val layer: Int = 0,
) {
    val isVideo: Boolean get() = video != null

    /** When it shows. */
    val span: TimeSpan get() = TimeSpan(startMs, endMs ?: (startMs + TimeSpan.DEFAULT_LENGTH_MS))

    /** What the timeline moves and trims: a video's part of its file, or simply when a picture shows. */
    val laneSpan: LaneSpan<*> get() = video ?: span

    /**
     * Shown during [span]. A video keeps playing the same moment of its file at
     * its start, and is never made longer than its file allows.
     */
    fun withSpan(span: TimeSpan): ImageOverlay {
        val media = video ?: return copy(startMs = span.startMs, endMs = span.endMs)
        val moved = media.movedTo(span.startMs)
        return withVideo(moved.withEndEdgeMovedBy(span.lengthMs - moved.lengthMs))
    }

    /** Playing [media] (a video's part of its file and its place). */
    fun withVideo(media: SoundSpan): ImageOverlay = copy(video = media, startMs = media.startMs, endMs = media.endMs)

    /** True when its colours are changed. */
    val adjustsColor: Boolean get() = brightness != 0f || contrast != 1f || saturation != 1f
}

/** When a caption shows, as the timeline edits it. */
val TextOverlay.span: TimeSpan get() = TimeSpan(startMs, endMs ?: (startMs + TimeSpan.DEFAULT_LENGTH_MS))

fun TextOverlay.withSpan(span: TimeSpan): TextOverlay = copy(startMs = span.startMs, endMs = span.endMs)

/**
 * A sound placed freely on the video, CapCut style: music, a voice-over, an
 * effect. [span] says which part of the file plays, when (on the clock of the
 * exported video), how fast and how many times; sounds may overlap.
 *
 * [volume] is linear (1 = unchanged) and [muted] silences the sound without
 * losing it; the fades are its own, in seconds. [keepPitch] keeps the pitch
 * when the speed changes (else it follows, like a tape); [pitchSemitones]
 * shifts it on top. The equaliser gains are in dB. [normalize] levels the
 * loudness; [loudnessLufs] is the measured loudness of the file, which the
 * preview needs to imitate it (null until measured).
 */
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val span: SoundSpan,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val keepPitch: Boolean = true,
    val pitchSemitones: Float = 0f,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val normalize: Boolean = false,
    val loudnessLufs: Double? = null,
    /** Its own effects (equaliser, clean up, dynamics, space, effects, stereo), heard as it plays. */
    val effects: AudioEffects = AudioEffects.NONE,
    /** How its volume changes while it plays (times from its start on the video). */
    val keyframes: Keyframes = Keyframes.NONE,
)

/**
 * Aspect ratio the crop rectangle is locked to while editing.
 * [ratio] is width divided by height; null leaves the rectangle free-form.
 */
enum class CropAspect(val ratio: Float?, @StringRes val labelRes: Int) {
    FREE(null, R.string.crop_free),
    SQUARE(1f, R.string.ratio_1_1),
    LANDSCAPE_16_9(16f / 9f, R.string.ratio_16_9),
    LANDSCAPE_4_3(4f / 3f, R.string.ratio_4_3),
    PORTRAIT_9_16(9f / 16f, R.string.ratio_9_16),
}

/** Where a caption or picture goes among those laid over the video. */
typealias LayerMove = com.cuscus.ffmpeggui.core.model.LayerMove
