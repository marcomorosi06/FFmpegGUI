package com.cuscus.ffmpeggui.core.model

import kotlin.math.abs

/**
 * The edits that can be applied either to one clip or to the whole video.
 *
 * The same settings exist at two levels. Clip effects are applied to that clip
 * alone, before the clips are joined; the effects of the whole video are
 * applied afterwards to the joined result. Both can be used together: a clip
 * at 2x inside a video at 0.5x plays at normal speed.
 *
 * Geometry: [crop] is applied after [rotation] and the flips, so its fractions
 * refer to the frame as it looks once rotated.
 *
 * Sound: [muted] silences a clip; on the whole video it drops the source audio
 * altogether (an extra soundtrack is still mixed in).
 */
data class Effects(
    val rotation: Rotation = Rotation.NONE,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropRect? = null,
    val color: ColorSettings = ColorSettings(),
    val speed: Float = 1f,
    val reversed: Boolean = false,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** 0..1, 0 = off. */
    val audioDenoise: Float = 0f,
    /** Slow zoom or pan over a still (a picture clip); ignored on footage and on the whole video. */
    val motion: StillMotion? = null,
    /**
     * A colour cut out of the picture (green screen), after its colours are set;
     * what is cut shows the key's background (black when it has none: a video
     * file holds no transparency).
     */
    val key: ColorKey? = null,
    /**
     * The picture enlarged [zoom] times (1 = the whole frame, never less),
     * showing the part [panX] / [panY] of the way across the free space (0 =
     * left / top edge, 0.5 = the middle). On a clip only.
     */
    val zoom: Float = 1f,
    val panX: Float = 0.5f,
    val panY: Float = 0.5f,
    /**
     * How the framing, the colours and the volume change over the clip (see
     * [Keyframes]; times are positions in the clip's file). On a clip only.
     */
    val keyframes: Keyframes = Keyframes.NONE,
    /**
     * The speed changing along the clip (see [SpeedCurve]); when set it takes
     * the place of [speed]. On a clip only.
     */
    val speedCurve: SpeedCurve? = null,
    /**
     * Faster or slower, the sound keeps its pitch; when false the pitch rises and
     * falls with the speed, like a tape.
     */
    val keepPitch: Boolean = true,
    /** How the clip gives way to the one after it (see [Transition]); none when null. On a clip only. */
    val transition: Transition? = null,
    /**
     * Where the picture lies on the canvas (see [VideoPlacement]): moved,
     * enlarged or turned like a picture laid over it. On the whole video only;
     * null keeps it in the middle, at its own size.
     */
    val placement: VideoPlacement? = null,
    /**
     * An HDR look (see HdrVideoLook) of this intensity, 0..1: the video is made
     * HDR, its light parts brighter than SDR white. On the whole video only, in
     * an MP4 or MKV; null leaves it SDR.
     */
    val hdrLook: Float? = null,
) {
    /** True when the picture is enlarged at some moment (by [zoom] or by keyframes). */
    val reframes: Boolean
        get() = keyframes.range(KeyProperty.ZOOM, zoom).endInclusive > 1.0001f

    /** Playback rate, with nonsense values treated as normal speed (the average one of a curve). */
    val effectiveSpeed: Float
        get() = (curve?.averageSpeed ?: constantSpeed).toFloat()

    /** The speed curve, when the speed does change along the clip. */
    val curve: SpeedCurve?
        get() = speedCurve?.takeUnless { it.isFlat }

    /** The constant speed: that of [speed], or of a curve that does not change. */
    private val constantSpeed: Double
        get() = (speedCurve?.takeIf { it.isFlat }?.nodes?.first()?.speed ?: speed).let { if (it > 0f) it.toDouble() else 1.0 }

    /** How long [lengthMs] of footage play. */
    fun playedMs(lengthMs: Double): Double = curve?.playedLengthMs(lengthMs) ?: (lengthMs / constantSpeed)

    fun playedMs(lengthMs: Long): Long = Math.round(playedMs(lengthMs.toDouble()))

    /**
     * How long the first [consumedMs] of a clip of [lengthMs] footage take to play,
     * counted in playing order (from the end of the footage when it plays backwards).
     */
    fun playedOffsetMs(lengthMs: Double, consumedMs: Double): Double =
        curve?.playedMs(lengthMs, consumedMs) ?: (consumedMs / constantSpeed)

    /** Inverse of [playedOffsetMs]. */
    fun consumedOffsetMs(lengthMs: Double, playedMs: Double): Double =
        curve?.consumedMs(lengthMs, playedMs) ?: (playedMs * constantSpeed)

    /** The speed after [consumedMs] of a clip of [lengthMs] footage (in playing order). */
    fun speedAt(lengthMs: Double, consumedMs: Double): Float =
        curve?.speedAt(if (lengthMs > 0) consumedMs / lengthMs else 0.0) ?: constantSpeed.toFloat()

    /**
     * These effects for the part [fileFrom]..[fileTo] (fractions of the clip's
     * footage in file order) cut out of the clip: its speed curve narrowed to that
     * part, so that it plays as it did inside the whole clip.
     */
    fun windowed(fileFrom: Double, fileTo: Double): Effects {
        val c = speedCurve ?: return this
        val window = if (reversed) c.window(1.0 - fileTo, 1.0 - fileFrom) else c.window(fileFrom, fileTo)
        return copy(speedCurve = window)
    }

    /** True when the speed is not normal somewhere along the clip. */
    val changesSpeed: Boolean
        get() = curve != null || abs(constantSpeed - 1.0) > 1e-4

    /** The crop when it actually removes something, else null. */
    val effectiveCrop: CropRect?
        get() = crop?.takeUnless { it.isFull }

    /** True when the frame size changes (a quarter turn or a crop). */
    val changesFrameSize: Boolean
        get() = rotation == Rotation.DEG_90 || rotation == Rotation.DEG_270 || effectiveCrop != null

    /** True when nothing is changed, so no filter is needed. */
    val isNeutral: Boolean
        get() = copy(crop = effectiveCrop) == NONE

    companion object {
        val NONE = Effects()
    }
}

/**
 * Part of the frame to keep, as fractions (0..1) of its width and height.
 * Values outside the frame are clamped when the filter is built.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
) {
    /** True when the rectangle covers the whole frame (to within a rounding error). */
    val isFull: Boolean
        get() = left <= EPSILON && top <= EPSILON && width >= 1f - EPSILON && height >= 1f - EPSILON

    private companion object {
        const val EPSILON = 0.001f
    }
}

/** Picture filters that do not change the geometry. */
data class ColorSettings(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    /** -1..1, 0 = untouched. */
    val sharpness: Float = 0f,
    /** Blur sigma, 0 = off. */
    val gaussianBlur: Float = 0f,
    val vignette: Boolean = false,
    /** Noise strength 0..60, 0 = off. */
    val filmGrain: Float = 0f,
    /** Spatial denoise strength 0..10, 0 = off. */
    val denoise: Float = 0f,
)

/** The ways a camera can move over a still picture. */
enum class MotionKind { ZOOM_IN, ZOOM_OUT, PAN_LEFT, PAN_RIGHT, PAN_UP, PAN_DOWN }

/**
 * A slow move over a still picture during its clip ("Ken Burns"): a zoom
 * between 1x and [amount], or a pan across the picture enlarged [amount] times.
 *
 * At [progress] (0 at the start of the clip, 1 at its end) the view is the
 * picture enlarged [zoomAt] times, showing the part that starts [xAt] / [yAt]
 * of the way across the free space (0 = left / top edge, 1 = right / bottom).
 * The export (zoompan) and the preview both follow these.
 */
data class StillMotion(val kind: MotionKind, val amount: Float = DEFAULT_AMOUNT) {

    /** Enlargement at its strongest, kept to a sensible range. */
    val strength: Float get() = amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)

    fun zoomAt(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (kind) {
            MotionKind.ZOOM_IN -> 1f + (strength - 1f) * p
            MotionKind.ZOOM_OUT -> strength - (strength - 1f) * p
            else -> strength
        }
    }

    fun xAt(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (kind) {
            MotionKind.PAN_LEFT -> 1f - p
            MotionKind.PAN_RIGHT -> p
            else -> 0.5f
        }
    }

    fun yAt(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (kind) {
            MotionKind.PAN_UP -> 1f - p
            MotionKind.PAN_DOWN -> p
            else -> 0.5f
        }
    }

    companion object {
        const val MIN_AMOUNT = 1.05f
        const val MAX_AMOUNT = 2f
        const val DEFAULT_AMOUNT = 1.3f
    }
}
