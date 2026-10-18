package com.cuscus.ffmpeggui.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The outline a picture laid over the video is cut to. */
enum class OverlayShape {
    /** As it is (its own transparency kept). */
    RECTANGLE,

    /** Its corners rounded by [OverlayLook.cornerRadius]. */
    ROUNDED,

    /** The ellipse that fits it (a circle when it is square). */
    ELLIPSE,
}

/**
 * How a picture (or video) laid over the video looks, beyond its place, size,
 * turn and colours: the part of it that shows, the outline it is cut to, a
 * blur of what it shows, an outline drawn around it and a shadow under it.
 *
 * [crop] is in the picture as it shows (after mirroring), fractions of it;
 * the place and size of the picture are those of the cropped part. Every
 * length is a fraction of the width the picture shows at (so that it looks the
 * same at any size): [blur] is the blur's radius; [borderWidth] how far the
 * outline goes beyond what shows of the picture (following its transparency:
 * around a cut-out subject, the subject's outline); [shadowBlur] how soft the
 * shadow is and [shadowX], [shadowY] how far it falls (right, down).
 *
 * The outline and the shadow need room around the picture: the box laid on
 * the video is the picture with [margin] of its width on every side, placed
 * as any picture is (the preview places it the same way).
 *
 * The export works these out with FFmpeg after sizing the picture (see
 * command.overlayLookFilters); the preview with core.image.OverlayDecoration,
 * on the same pixel sizes.
 */
data class OverlayLook(
    val crop: CropRect? = null,
    val shape: OverlayShape = OverlayShape.RECTANGLE,
    /** [OverlayShape.ROUNDED]: the corners' radius, a fraction of half the shorter side (1 = a pill). */
    val cornerRadius: Float = DEFAULT_CORNER_RADIUS,
    val blur: Float = 0f,
    val borderWidth: Float = 0f,
    /** 0xRRGGBB. */
    val borderColor: Int = DEFAULT_BORDER_COLOR,
    /** How dark the shadow is, 0..1 (0 = none). */
    val shadowOpacity: Float = 0f,
    val shadowBlur: Float = DEFAULT_SHADOW_BLUR,
    val shadowX: Float = DEFAULT_SHADOW_X,
    val shadowY: Float = DEFAULT_SHADOW_Y,
    /** 0xRRGGBB. */
    val shadowColor: Int = 0x000000,
) {
    /** True when only part of the picture shows. */
    val crops: Boolean get() = crop != null && !crop.isFull

    /** True when the picture is cut to another outline than its own. */
    val shaped: Boolean
        get() = when (shape) {
            OverlayShape.RECTANGLE -> false
            OverlayShape.ROUNDED -> cornerRadius > EPSILON
            OverlayShape.ELLIPSE -> true
        }

    val blurs: Boolean get() = blur > EPSILON
    val borders: Boolean get() = borderWidth > EPSILON
    val shadows: Boolean get() = shadowOpacity > EPSILON

    /** True when anything is done to the picture once it is sized (all but the crop). */
    val decorates: Boolean get() = blurs || shaped || borders || shadows

    /** True when this look changes nothing. */
    val isPlain: Boolean get() = !crops && !decorates

    /** Room around the picture on every side, a fraction of its width: for the outline, then the shadow. */
    val margin: Float
        get() {
            var room = 0f
            if (borders) room += borderWidth
            // The shadow's blur (two box passes) reaches twice its radius.
            if (shadows) room += 2f * shadowBlur + max(abs(shadowX), abs(shadowY))
            return room
        }

    /** The part shown, [crop] or all of the picture. */
    val shownPart: CropRect get() = crop?.takeUnless { it.isFull } ?: FULL

    /** Width / height of the picture as it shows, for a picture of [aspect] (0 when unknown). */
    fun shownAspect(aspect: Float): Float {
        if (aspect <= 0f) return 0f
        val part = shownPart
        return aspect * part.width.coerceAtLeast(MIN_PART) / part.height.coerceAtLeast(MIN_PART)
    }

    /** Width / height of the box laid on the video (the shown picture and [margin] around it). */
    fun laidAspect(aspect: Float): Float {
        val shown = shownAspect(aspect)
        if (shown <= 0f) return 0f
        val room = 2f * margin
        return (1f + room) / (1f / shown + room)
    }

    /** Pixel sizes of this look for a picture shown [width] pixels wide (the export rounds the same way). */
    fun pixels(width: Int, height: Int): Pixels {
        fun px(fraction: Float) = (width * fraction).toInt()
        val shortest = min(width, height)
        return Pixels(
            margin = if (decorates) px(margin) else 0,
            blur = if (blurs) px(blur).coerceAtMost(shortest / 2 - 1).coerceAtLeast(0) else 0,
            border = if (borders) px(borderWidth) else 0,
            shadowBlur = if (shadows) px(shadowBlur) else 0,
            shadowX = if (shadows) px(shadowX) else 0,
            shadowY = if (shadows) px(shadowY) else 0,
        )
    }

    /** The look's lengths in pixels, for a picture of a given width. */
    data class Pixels(val margin: Int, val blur: Int, val border: Int, val shadowBlur: Int, val shadowX: Int, val shadowY: Int)

    companion object {
        val NONE = OverlayLook()

        private val FULL = CropRect()
        private const val EPSILON = 1e-4f
        private const val MIN_PART = 0.01f

        const val DEFAULT_CORNER_RADIUS = 0.35f
        const val DEFAULT_BORDER_COLOR = 0xFFFFFF
        const val DEFAULT_SHADOW_BLUR = 0.03f
        const val DEFAULT_SHADOW_X = 0.02f
        const val DEFAULT_SHADOW_Y = 0.03f

        /** Largest blur, outline and shadow blur, fractions of the picture's width. */
        const val MAX_BLUR = 0.08f
        const val MAX_BORDER = 0.08f
        const val MAX_SHADOW_BLUR = 0.08f

        /** Furthest a shadow falls, either way, a fraction of the picture's width. */
        const val MAX_SHADOW_OFFSET = 0.1f

        /**
         * How much of the outline's soft edge counts as outline: the picture's
         * alpha is spread by a box blur of the outline's radius and every value
         * at least 1/16 of full is made full (with a soft edge below that).
         */
        const val BORDER_GAIN = 16
    }
}
