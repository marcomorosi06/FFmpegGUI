package com.cuscus.ffmpeggui.core.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A caption drawn on top of the picture.
 *
 * [x] and [y] are fractions (0..1) of the free space left around the text, so
 * 0.5 centres it. [size] is the font height as a fraction of the frame height.
 * [color] is ARGB; only the RGB part is used.
 *
 * [opacity] goes from 0 to 1.
 *
 * A caption of the whole video shows from [startMs] to [endMs] (null = to the
 * end) of the exported video, fading in and out over its own fade lengths.
 *
 * With [keyframes], [x], [y], [size] and [opacity] change over time.
 *
 * [clipId] binds the caption to one clip instead (it is then drawn on that
 * clip only, sized against its frame, for the whole clip; its times and fades
 * are not used); null puts it on the whole video.
 *
 * [look] gives it an outline, a shadow and a box behind it; [fontFile] draws
 * it with a font the user added instead of [font].
 *
 * It is turned by [rotationDegrees] (clockwise) about its anchor: the point
 * of its box at ([x], [y]) of its size, which lies at ([x], [y]) of the frame
 * (the middle of a centred caption). [blend] mixes it with what lies under it
 * (see LayerBlend), as a picture's. [stretch] pulls it out of its shape:
 * that many times as wide at the same height (its look, outline, shadow and
 * box, with it), about its anchor, before it is turned.
 *
 * A [hidden] caption is kept in the project but neither shown nor exported; a
 * [locked] one cannot be picked or moved on the picture (it is chosen from the
 * layers list, and its panels still change it), as in the SVG editor.
 */
data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.05f,
    val color: Long = 0xFFFFFFFF,
    val isBold: Boolean = true,
    val clipId: String? = null,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val opacity: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    /** The typeface; the bold one when [isBold] and the phone has it. */
    val font: TextFont = TextFont.SANS,
    /**
     * How its place, size and opacity change while it shows (see [Keyframes];
     * times from its own start, or from its clip's start for a caption of a clip).
     */
    val keyframes: Keyframes = Keyframes.NONE,
    /**
     * Where it lies among the captions and pictures of its clip (or of the
     * whole video): higher is on top (see [stackOrder]).
     */
    val layer: Int = 0,
    /** Its outline, shadow and box (none by default). */
    val look: TextLook = TextLook.NONE,
    /**
     * A font file the user added (.ttf or .otf), its path in the app's own
     * storage; it takes the place of [font] while it can be read (else [font]).
     * Such a font has no bold of its own: [isBold] does not change it.
     */
    val fontFile: String? = null,
    /** Left out of the picture and of the export, kept in the project (the layers list's eye). */
    val hidden: Boolean = false,
    /** Not picked nor moved by hand on the picture (the layers list's lock). */
    val locked: Boolean = false,
    /** How far it is turned, clockwise, about its anchor (degrees). */
    val rotationDegrees: Float = 0f,
    /** How it mixes with what lies under it. */
    val blend: LayerBlend = LayerBlend.NORMAL,
    /**
     * How many times wider than its own shape it is drawn, at the same height
     * (see [laidStretch]); 1 keeps it.
     */
    val stretch: Float = 1f,
) {
    /** [stretch] as it is drawn: within ImagePlacement.MIN_STRETCH..MAX_STRETCH, 1 when it hardly changes it. */
    val laidStretch: Float get() = ImagePlacement.saneStretch(stretch)

    /** Drawn as a layer of its own (turned, blended or stretched), rather than straight on the frame. */
    val layered: Boolean get() = blend != LayerBlend.NORMAL || abs(rotationDegrees % 360f) > 0.01f || laidStretch != 1f
}

/**
 * A caption's outline, shadow and background box, as FFmpeg's drawtext draws
 * them (issue #22): sizes are fractions of the font size, colours ARGB with
 * their alpha used. The shadow is the letters again in [shadowColor], moved
 * right and down by [shadow], under the outline (sharp, drawtext's own; soft
 * with [shadowBlur], drawn apart and blurred, then under the box too); the box
 * is a rectangle around the text, [boxPadding] wider on every side.
 */
data class TextLook(
    /** How thick the outline is, around the letters; 0 = none. */
    val outline: Float = 0f,
    val outlineColor: Long = BLACK,
    /** How far the shadow is moved right and down; 0 = under the letters (a glow, when soft). */
    val shadow: Float = 0f,
    val shadowColor: Long = SHADOW_COLOR,
    /** How soft the shadow is (the blur's reach); 0 = sharp. A soft shadow shows even unmoved. */
    val shadowBlur: Float = 0f,
    /** A box behind the text. */
    val box: Boolean = false,
    val boxColor: Long = BOX_COLOR,
    /** How much the box goes past the text on every side. */
    val boxPadding: Float = DEFAULT_BOX_PADDING,
) {
    /** Whether there is a shadow: moved, or soft (a glow round the letters). */
    val hasShadow: Boolean get() = shadow > 0f || shadowBlur > 0f

    /** Nothing drawn besides the letters. */
    val isPlain: Boolean get() = outline <= 0f && !hasShadow && !box

    /** Every value inside the range it can take. */
    fun sane(): TextLook = copy(
        outline = outline.coerceIn(0f, MAX_OUTLINE),
        shadow = shadow.coerceIn(0f, MAX_SHADOW),
        shadowBlur = shadowBlur.coerceIn(0f, MAX_SHADOW_BLUR),
        boxPadding = boxPadding.coerceIn(0f, MAX_BOX_PADDING),
    )

    companion object {
        val NONE = TextLook()

        const val BLACK = 0xFF000000L
        const val SHADOW_COLOR = 0xB3000000L
        const val BOX_COLOR = 0x99000000L

        /** What an outline, a shadow and a box start with when turned on. */
        const val DEFAULT_OUTLINE = 0.08f
        const val DEFAULT_SHADOW = 0.08f
        const val DEFAULT_BOX_PADDING = 0.3f

        const val MAX_OUTLINE = 0.3f
        const val MAX_SHADOW = 0.4f
        const val MAX_SHADOW_BLUR = 0.4f
        const val MAX_BOX_PADDING = 1f

        /** [fraction] of a font [fontPixels] high, in whole pixels: at least 1 when it is not 0. */
        fun pixels(fraction: Float, fontPixels: Double): Int =
            if (fraction <= 0f || fontPixels <= 0.0) 0 else max(1, (fraction * fontPixels).roundToInt())
    }
}
