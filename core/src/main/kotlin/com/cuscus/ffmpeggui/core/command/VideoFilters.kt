package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.VideoPlacement
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.TextOverlay
import java.util.Locale

/**
 * Builds the linear chains of picture filters: one per clip, applied before the
 * clips are joined, and one for the whole video, applied after.
 *
 * Both follow the same order, which matters:
 *  - denoise first, on untouched frames;
 *  - geometry (reverse, flip, rotate, crop);
 *  - colour and effects;
 *  - the colour key (a green screen cut out, over its background colour), on
 *    the colours as they now are, which is what the editor shows and picks from;
 *  - speed change;
 *  - captions and pictures, stacked in their order (see stackOrder), on the
 *    time played;
 *  - fades, which cover everything below them.
 *
 * The whole-video chain adds what only exists once: stabilization (very first),
 * the canvas (the picture placed on it, see VideoPlacement) and output size
 * (after the crop), subtitles (before the speed change) and the frame rate.
 * Its captions come after the speed change, so that their times are those of
 * the exported video.
 */
internal class VideoFilters(private val fonts: FontProvider) {

    /**
     * Filters of one clip, with its captions: its picture ([clipPicture]), its
     * captions on the time it has played, then its fades, which cover them.
     *
     * @param playedSeconds  length of the clip once its speed is applied; null
     *                       when unknown, which skips the fade-out.
     * @param label          makes the links of the key's and the framing's little graphs unique.
     * @param fileTime       the position in the clip's file, in seconds, as an expression of
     *                       the frames' time before the speed change (what the clip's
     *                       keyframes follow).
     * @param footageSeconds length of the clip's footage, or null; a speed curve needs it
     *                       (without it the clip plays at the curve's average speed).
     */
    fun clipChain(
        effects: Effects,
        texts: List<TextOverlay>,
        playedSeconds: Double?,
        label: String = "kc",
        fileTime: String = "t",
        footageSeconds: Double? = null,
    ): List<String> =
        clipPicture(effects, label, fileTime, footageSeconds) + clipCaptions(texts).map { it.second } + fades(effects, playedSeconds)

    /**
     * The clip's own picture: everything of [clipChain] but its captions and
     * fades, which go on top of it with its pictures (see [clipCaptions]).
     */
    fun clipPicture(
        effects: Effects,
        label: String = "kc",
        fileTime: String = "t",
        footageSeconds: Double? = null,
    ): List<String> {
        val filters = mutableListOf<String>()
        filters += denoise(effects.color)
        filters += geometry(effects)
        framing(effects, fileTime, "${label}z")?.let { filters += it }
        filters += color(effects.color, effects.keyframes, fileTime)
        effects.key?.let { filters += keyOverBackground(it, label) }
        filters += speed(effects, footageSeconds)
        return filters
    }

    /**
     * A clip's captions (those with text), each with its layer: drawn after its
     * speed change, on the time it has played (what their keyframes follow).
     */
    fun clipCaptions(texts: List<TextOverlay>): List<Pair<Int, String>> =
        texts.filter { it.text.isNotBlank() }.map { it.layer to drawtext(it, timed = false, time = "t") }

    /** The fades of a clip ([playedSeconds] long) or of the whole video, which cover what lies on it. */
    fun fadesOf(effects: Effects, playedSeconds: Double?): List<String> = fades(effects, playedSeconds)

    /**
     * The clip enlarged and moved ([Effects.zoom], [Effects.panX], [Effects.panY],
     * or their keyframes over [time]): an enlarged copy of each frame, scaled
     * frame by frame, laid over the frame itself (which it always covers, the
     * enlargement never being less than 1) where the pan puts it. The frame keeps
     * its size. Null when the picture is never enlarged. [label] makes the links
     * of its little graph unique.
     */
    private fun framing(effects: Effects, time: String, label: String): String? {
        if (!effects.reframes) return null
        val keys = effects.keyframes
        val zoom = "max(1,${keys.expression(KeyProperty.ZOOM, effects.zoom, time)})"
        val panX = "clip(${keys.expression(KeyProperty.PAN_X, effects.panX, time)},0,1)"
        val panY = "clip(${keys.expression(KeyProperty.PAN_Y, effects.panY, time)},0,1)"
        return "split[${label}a][${label}b];" +
            "[${label}b]scale=w='max(2,trunc(iw*$zoom/2)*2)':h='max(2,trunc(ih*$zoom/2)*2)':eval=frame[${label}c];" +
            "[${label}a][${label}c]overlay=x='(W-w)*$panX':y='(H-h)*$panY'"
    }

    /**
     * Filters of the joined video: [wholeBefore], its captions in their order
     * (see [wholeCaptions]), then [wholeAfter]. Its pictures, when it has any,
     * go between the two with the captions.
     */
    fun wholeChain(
        request: ConversionRequest,
        paths: MediaPaths,
        outputDurationSeconds: Double?,
    ): List<String> =
        wholeBefore(request, paths) + wholeCaptions(request).sortedBy { it.first }.map { it.second } + wholeAfter(request, outputDurationSeconds)

    /** The captions of the whole video (with text), each with its layer, timed on the exported video. */
    fun wholeCaptions(request: ConversionRequest): List<Pair<Int, String>> =
        request.wholeTextOverlays.filter { it.text.isNotBlank() }
            .map { it.layer to drawtext(it, timed = true, time = fmt("(t-%.4f)", it.startMs.coerceAtLeast(0L) / 1000.0)) }

    /** What comes after the captions and pictures of the whole video: its frame rate and its fades, which cover them. */
    fun wholeAfter(request: ConversionRequest, outputDurationSeconds: Double?): List<String> {
        val filters = mutableListOf<String>()
        when {
            request.format == OutputFormat.GIF -> filters += "fps=${request.encoding.gif.fps.coerceIn(1, 50)}"
            request.encoding.framerate != Framerate.ORIGINAL ->
                filters += "fps=${request.encoding.framerate.value}"
        }
        filters += fades(request.effects, outputDurationSeconds)
        return filters
    }

    /** The joined video up to its captions: from its stabilization to its speed change. */
    fun wholeBefore(
        request: ConversionRequest,
        paths: MediaPaths,
    ): List<String> {
        val filters = mutableListOf<String>()
        val effects = request.effects

        val stabilization = request.stabilization
        if (stabilization != null) {
            val transforms = requireNotNull(paths.stabilizationTransforms) {
                "Stabilization requested but no transforms path was provided"
            }
            filters += "vidstabtransform=input=${filterOptionValue(transforms)}" +
                ":smoothing=${stabilization.smoothing.coerceAtLeast(0)}"
        }
        filters += denoise(effects.color)
        filters += geometry(effects)
        val placement = effects.placement?.takeUnless { it.isNeutral }
        if (placement == null) {
            padFilter(request.canvas)?.let { filters += it }
        } else {
            filters += placed(request.canvas, placement.normalized())
        }

        val isGif = request.format == OutputFormat.GIF
        val gif = request.encoding.gif
        when {
            // A GIF's own width: never larger than the picture, height in proportion.
            isGif -> if (gif.width > 0) filters += "scale=w='min(iw,${gif.width})':h=-1:flags=lanczos"
            request.encoding.resolution != Resolution.ORIGINAL ->
                filters += "scale=-2:${request.encoding.resolution.height}"
        }

        filters += color(effects.color)
        effects.key?.let { filters += keyOverBackground(it, "kw") }

        // The file handed over here is already re-timed to the edited sequence (see
        // SubtitleRetimer), so it lines up with this point of the chain as it is.
        // Spoken subtitles are written for this point of the chain too (see AssSubtitles).
        val subtitles = paths.subtitles
        if (request.burnsSubtitles && subtitles != null) {
            var filter = "subtitles=${filterOptionValue(subtitles)}"
            paths.subtitleFonts?.let { filter += ":fontsdir=${filterOptionValue(it)}" }
            filters += filter
        }

        filters += speed(effects, null)
        // From here on the frames carry the exported video's timestamps.
        return filters
    }

    // ---------------------------------------------------------------------
    // Pieces shared by both chains
    // ---------------------------------------------------------------------

    private fun denoise(color: ColorSettings): List<String> =
        if (color.denoise > 0f) listOf(fmt("hqdn3d=%.1f", color.denoise)) else emptyList()

    private fun geometry(effects: Effects): List<String> {
        val filters = mutableListOf<String>()
        if (effects.reversed) filters += "reverse"
        if (effects.flipHorizontal) filters += "hflip"
        if (effects.flipVertical) filters += "vflip"
        when (effects.rotation) {
            Rotation.DEG_90 -> filters += "transpose=1"
            Rotation.DEG_180 -> { filters += "transpose=1"; filters += "transpose=1" }
            Rotation.DEG_270 -> filters += "transpose=2"
            Rotation.NONE -> Unit
        }
        effects.effectiveCrop?.let { filters += cropFilter(it) }
        return filters
    }

    private fun color(color: ColorSettings, keyframes: Keyframes = Keyframes.NONE, time: String = "t"): List<String> {
        val filters = mutableListOf<String>()
        if (keyframes.animatesAny(Keyframes.COLOURS)) {
            // Changing over time: eq works its values out again on every frame.
            filters += "eq=eval=frame" +
                ":brightness='${keyframes.expression(KeyProperty.BRIGHTNESS, color.brightness, time)}'" +
                ":contrast='${keyframes.expression(KeyProperty.CONTRAST, color.contrast, time)}'" +
                ":saturation='${keyframes.expression(KeyProperty.SATURATION, color.saturation, time)}'"
        } else if (color.brightness != 0f || color.contrast != 1f || color.saturation != 1f) {
            filters += fmt(
                "eq=brightness=%.2f:contrast=%.2f:saturation=%.2f",
                color.brightness, color.contrast, color.saturation,
            )
        }
        if (color.sharpness != 0f) {
            filters += fmt("unsharp=5:5:%.2f:5:5:0.00", color.sharpness.coerceIn(-1f, 1f) * UNSHARP_MAX_AMOUNT)
        }
        if (color.gaussianBlur > 0f) filters += fmt("gblur=sigma=%.1f", color.gaussianBlur)
        if (color.vignette) filters += "vignette"
        if (color.filmGrain > 0f) filters += "noise=alls=${color.filmGrain.toInt().coerceIn(1, 100)}:allf=t+u"
        return filters
    }

    /** The frames' times played at the clip's speed, or along its curve (the time played as an expression of T). */
    private fun speed(effects: Effects, footageSeconds: Double?): List<String> {
        val curve = effects.curve
        if (curve != null && footageSeconds != null && footageSeconds > 0.0) {
            return listOf("setpts='(${curve.playedExpression(footageSeconds, "T")})/TB'")
        }
        return if (effects.effectiveSpeed != 1f) listOf(fmt("setpts=%.4f*PTS", 1f / effects.effectiveSpeed)) else emptyList()
    }

    private fun fades(effects: Effects, durationSeconds: Double?): List<String> {
        val filters = mutableListOf<String>()
        if (effects.fadeInSeconds > 0f) {
            filters += fmt("fade=t=in:st=0:d=%.1f", effects.fadeInSeconds)
        }
        fadeOutStart(effects.fadeOutSeconds, durationSeconds)?.let { start ->
            filters += fmt("fade=t=out:st=%.3f:d=%.1f", start, effects.fadeOutSeconds)
        }
        return filters
    }

    /**
     * The picture placed freely on its canvas ([VideoPlacement]): the canvas as
     * [padFilter] makes it, black, and the picture laid on it enlarged, turned
     * (its corners see-through) and with its middle where [placement] puts it.
     * Even sides, so that every encoder takes it.
     */
    private fun placed(canvas: CanvasRatio, placement: VideoPlacement): String {
        val base = listOfNotNull(padFilter(canvas), "drawbox=x=0:y=0:w=iw:h=ih:color=black:t=fill").joinToString(",")
        val scale = fmt("%.4f", placement.scale)
        val picture = mutableListOf("scale=w='max(2,trunc(iw*$scale/2)*2)':h='max(2,trunc(ih*$scale/2)*2)'")
        val turn = placement.turn
        if (kotlin.math.abs(turn) >= 0.01f) {
            val radians = fmt("%.6f", Math.toRadians(turn.toDouble()))
            picture += "format=rgba,rotate=$radians:ow=rotw($radians):oh=roth($radians):c=none"
        }
        return "split[vpa][vpb];[vpa]$base[vpc];[vpb]${picture.joinToString(",")}[vpd];" +
            "[vpc][vpd]overlay=x='W*${fmt("%.4f", placement.centerX)}-w/2':y='H*${fmt("%.4f", placement.centerY)}-h/2',setsar=1"
    }

    private fun padFilter(canvas: CanvasRatio): String? {
        if (canvas == CanvasRatio.FIT) return null
        val ratio = if (canvas.width == canvas.height) null else "(${canvas.width}/${canvas.height})"
        val width = if (ratio == null) "max(iw,ih)" else "max(iw,ih*$ratio)"
        val height = if (ratio == null) "max(ih,iw)" else "max(ih,iw/$ratio)"
        return "pad=width='ceil($width/2)*2':height='ceil($height/2)*2'" +
            ":x='(ow-iw)/2':y='(oh-ih)/2':color=black"
    }

    /**
     * One caption. A [timed] one (of the whole video) shows only during its window
     * and fades in and out there; any caption can be translucent. With keyframes
     * its place, size and opacity are expressions of [time] (seconds on the
     * keyframes' clock), worked out on every frame.
     */
    internal fun drawtext(overlay: TextOverlay, timed: Boolean, time: String = "t"): String {
        val color = String.format(Locale.US, "%06X", overlay.color and 0xFFFFFF)
        var filter = "drawtext=fontfile=${filterOptionValue(fonts.fontPath(overlay.font, overlay.isBold))}" +
            ":text=${drawtextValue(overlay.text)}" +
            ":fontcolor=0x$color"
        val keys = overlay.keyframes
        filter += if (keys.animatesAny(Keyframes.TEXT)) {
            ":fontsize='h*${size(keys.expression(KeyProperty.SIZE, overlay.size, time), keys.animates(KeyProperty.SIZE))}'" +
                ":x='(w-tw)*${keys.expression(KeyProperty.X, overlay.x, time)}'" +
                ":y='(h-th)*${keys.expression(KeyProperty.Y, overlay.y, time)}'"
        } else {
            val size = keys.valueAt(KeyProperty.SIZE, 0L, overlay.size)
            val x = keys.valueAt(KeyProperty.X, 0L, overlay.x)
            val y = keys.valueAt(KeyProperty.Y, 0L, overlay.y)
            fmt(":fontsize=h*%.4f:x=(w-tw)*%.4f:y=(h-th)*%.4f", size, x, y)
        }
        val opacity = if (keys.animates(KeyProperty.OPACITY)) {
            "clip(${keys.expression(KeyProperty.OPACITY, overlay.opacity, time)},0,1)"
        } else {
            keys.valueAt(KeyProperty.OPACITY, 0L, overlay.opacity).coerceIn(0f, 1f).let { if (it >= 0.999f) null else fmt("%.3f", it) }
        }
        val fadeIn = if (timed) overlay.fadeInSeconds else 0f
        val fadeOut = if (timed && overlay.endMs != null) overlay.fadeOutSeconds else 0f
        alphaExpression(opacity, overlay.startMs / 1000.0, overlay.endMs?.let { it / 1000.0 }, fadeIn, fadeOut)
            ?.let { filter += ":alpha='$it'" }
        if (timed) enableExpression(overlay.startMs, overlay.endMs)?.let { filter += ":enable='$it'" }
        return filter
    }

    /**
     * Opacity over time: [opacity] (a number or an expression; null for fully
     * opaque), ramping up over [fadeIn] seconds from [start] and down over
     * [fadeOut] seconds to [end]. Null when fully opaque throughout.
     */
    private fun alphaExpression(opacity: String?, start: Double, end: Double?, fadeIn: Float, fadeOut: Float): String? {
        val ramps = mutableListOf<String>()
        if (fadeIn > 0f) ramps += fmt("(t-%.3f)/%.3f", start, fadeIn)
        if (fadeOut > 0f && end != null) ramps += fmt("(%.3f-t)/%.3f", end, fadeOut)
        val ramp = when (ramps.size) {
            0 -> null
            1 -> "clip(${ramps[0]},0,1)"
            else -> "clip(min(${ramps[0]},${ramps[1]}),0,1)"
        }
        return when {
            ramp == null -> opacity
            opacity == null -> ramp
            else -> "$opacity*$ramp"
        }
    }

    companion object {

        /** unsharp accepts luma amounts in -1.5..1.5; the request uses -1..1. */
        const val UNSHARP_MAX_AMOUNT = 1.5f

        /**
         * Tail that turns a picture stream into a GIF stream with its own palette of
         * [GifSettings.colors]; with [GifSettings.smaller] the palette is weighted to
         * what moves and each frame keeps only the rectangle that changed.
         */
        fun gifPaletteTail(gif: GifSettings): String {
            val colors = gif.colors.coerceIn(2, 256)
            val generate = "palettegen=max_colors=$colors" + (if (gif.smaller) ":stats_mode=diff" else "")
            val use = "paletteuse=dither=${gif.dither.filter}" + (if (gif.smaller) ":diff_mode=rectangle" else "")
            return "split[s0][s1];[s0]$generate[p];[s1][p]$use"
        }
    }
}

/**
 * `crop` keeping the fractions of [rect], clamped inside the frame. Sizes are
 * rounded down to even numbers, which every encoder accepts.
 */
internal fun cropFilter(rect: CropRect): String {
    val width = rect.width.coerceIn(MIN_CROP_FRACTION, 1f)
    val height = rect.height.coerceIn(MIN_CROP_FRACTION, 1f)
    val left = rect.left.coerceIn(0f, 1f - width)
    val top = rect.top.coerceIn(0f, 1f - height)
    return fmt(
        "crop=w=trunc(iw*%.4f/2)*2:h=trunc(ih*%.4f/2)*2:x=trunc(iw*%.4f):y=trunc(ih*%.4f)",
        width, height, left, top,
    )
}

private const val MIN_CROP_FRACTION = 0.01f

/** Start time of a fade-out, or null when it does not fit or the length is unknown. */
internal fun fadeOutStart(fadeSeconds: Float, durationSeconds: Double?): Double? {
    if (fadeSeconds <= 0f || durationSeconds == null) return null
    val start = durationSeconds - fadeSeconds
    return start.takeIf { it > 0.0 }
}

/**
 * When something timed on the exported video shows: from [startMs] to [endMs]
 * (null = to the end). Null when it shows all along.
 */
internal fun enableExpression(startMs: Long, endMs: Long?): String? = when {
    endMs != null -> fmt("between(t,%.3f,%.3f)", startMs.coerceAtLeast(0L) / 1000.0, endMs / 1000.0)
    startMs > 0L -> fmt("gte(t,%.3f)", startMs / 1000.0)
    else -> null
}

/** A size that may change over time, kept above zero (an easing may overshoot). */
private fun size(expression: String, changes: Boolean): String = if (changes) "max(0.002,$expression)" else expression

internal fun fmt(pattern: String, vararg args: Any): String =
    String.format(Locale.US, pattern, *args)
