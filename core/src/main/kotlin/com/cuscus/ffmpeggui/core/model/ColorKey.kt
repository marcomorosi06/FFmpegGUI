package com.cuscus.ffmpeggui.core.model

import kotlin.math.roundToInt

/** How a [ColorKey] tells the colour to cut out. */
enum class KeyMode {
    /**
     * By its hue only (FFmpeg `chromakey`): a green or blue screen goes whatever
     * its light and shadows, as long as the subject has none of that hue.
     */
    CHROMA,

    /** By the exact colour (FFmpeg `colorkey`): a flat background of one colour. */
    COLOR,

    /**
     * Around the subject, whatever the background: the cut-out model found
     * the subject of a still picture and its outline is kept in a mask (see
     * [ColorKey.mask]); the colour, similarity, blend and spill play no part.
     */
    SUBJECT,
}

/**
 * Cuts one colour out of a picture: a green screen, a blue screen or a flat
 * background. What is cut becomes transparent (a picture or video laid over the
 * video, a picture file that can hold transparency) or shows [background].
 *
 * [color] is 0xRRGGBB. [similarity] (0..1) is how far from the colour a pixel
 * may be and still go; [blend] (0..1) softens the edge over that much more
 * distance; [spill] (0..1) takes the screen's tint out of what stays (hair,
 * edges), a green or blue screen only (FFmpeg `despill`). [backgroundOpacity]
 * (0..1) makes the background see-through, where the result can hold
 * transparency (a picture file such as PNG): elsewhere it is always opaque.
 */
data class ColorKey(
    val mode: KeyMode = KeyMode.CHROMA,
    val color: Int = GREEN_SCREEN,
    val similarity: Float = DEFAULT_CHROMA_SIMILARITY,
    val blend: Float = DEFAULT_BLEND,
    val spill: Float = 0f,
    /** 0xRRGGBB shown where the colour was cut, or null for transparency where it can be kept. */
    val background: Int? = null,
    /** How opaque [background] is, 0..1; only a picture that holds transparency shows less than 1. */
    val backgroundOpacity: Float = 1f,
    /**
     * With [KeyMode.SUBJECT]: the mask of the subject, a PNG file as large as
     * the picture whose alpha is how much of each pixel is the subject (made
     * by the cut-out model). Null otherwise; without it nothing is cut.
     */
    val mask: String? = null,
) {
    /** True when this key cuts around a subject found by the model, and has its mask. */
    val cutsSubject: Boolean get() = mode == KeyMode.SUBJECT && mask != null

    /** [backgroundOpacity] as an alpha byte, 0..255, as the export writes it. */
    val backgroundAlpha: Int get() = (backgroundOpacity.coerceIn(0f, 1f) * 255f).roundToInt()

    /** True when what is cut shows [background] see-through rather than opaque. */
    val hasTranslucentBackground: Boolean get() = background != null && backgroundAlpha < 255

    /** [similarity] within what FFmpeg accepts. */
    val effectiveSimilarity: Float get() = similarity.coerceIn(MIN_SIMILARITY, 1f)

    /** [blend] within 0..1. */
    val effectiveBlend: Float get() = blend.coerceIn(0f, 1f)

    /** The screen whose tint [spill] takes out, or null when it cannot be told (not a green or blue screen). */
    val spillScreen: SpillScreen?
        get() {
            if (mode != KeyMode.CHROMA || spill <= 0f) return null
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return when {
                g > r && g >= b -> SpillScreen.GREEN
                b > r && b > g -> SpillScreen.BLUE
                else -> null
            }
        }

    /** This key showing [color] (or nothing, for null) where it cuts, opaque. */
    fun over(color: Int?): ColorKey = copy(background = color, backgroundOpacity = 1f)

    companion object {
        /** The green of most green screens (chroma key green). */
        const val GREEN_SCREEN = 0x00B140

        /** The blue of most blue screens (chroma key blue). */
        const val BLUE_SCREEN = 0x0047BB

        const val DEFAULT_CHROMA_SIMILARITY = 0.12f
        const val DEFAULT_COLOR_SIMILARITY = 0.25f
        const val DEFAULT_BLEND = 0.06f
        const val MIN_SIMILARITY = 0.00001f

        /** The tolerance a key of [mode] starts with. */
        fun defaultSimilarity(mode: KeyMode): Float =
            if (mode == KeyMode.COLOR) DEFAULT_COLOR_SIMILARITY else DEFAULT_CHROMA_SIMILARITY
    }
}

/** The kind of screen a key's spill removal works on. */
enum class SpillScreen { GREEN, BLUE }
