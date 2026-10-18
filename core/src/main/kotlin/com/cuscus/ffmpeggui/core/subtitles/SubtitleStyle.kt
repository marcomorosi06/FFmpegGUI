package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.model.TextFont

/** What is drawn around the letters. */
enum class SubtitleLook {
    /** A dark outline around every letter. */
    OUTLINE,

    /** A dark, translucent box behind every line. */
    BOX,

    /** The letters alone, with a small shadow. */
    SHADOW,
}

/** How the word being said stands out. */
enum class WordHighlight {
    /** It does not. */
    NONE,

    /** The word being said takes the highlight colour, the others keep theirs. */
    WORD,

    /** Every word said so far takes the highlight colour (karaoke). */
    SPOKEN,
}

/**
 * How burnt-in subtitles look, the same in the preview and in the export.
 *
 * Sizes are fractions of the frame height and positions fractions of the
 * frame, so that a style fits any video. [x] and [y] place the middle of the
 * bottom edge of the text (the last line's box, without the outline or the
 * box around it). Colours are ARGB.
 */
data class SubtitleStyle(
    /** Height of a line (the font size of SubStation Alpha), as a fraction of the frame height. */
    val size: Float = DEFAULT_SIZE,
    val x: Float = 0.5f,
    val y: Float = 0.9f,
    val textColor: Long = WHITE,
    val highlightColor: Long = YELLOW,
    val look: SubtitleLook = SubtitleLook.OUTLINE,
    val bold: Boolean = true,
    val uppercase: Boolean = false,
    val highlight: WordHighlight = WordHighlight.WORD,
    /** Lines shown at once, 1 or 2. */
    val maxLines: Int = 2,
    val font: TextFont = TextFont.SANS,
) {
    /** The style with every value inside the range it can take. */
    fun sane(): SubtitleStyle = copy(
        size = size.coerceIn(MIN_SIZE, MAX_SIZE),
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        maxLines = maxLines.coerceIn(1, 2),
    )

    /** Width of the outline (OUTLINE) or of the box around the text (BOX), as a fraction of the frame height. */
    val border: Float
        get() = when (look) {
            SubtitleLook.OUTLINE -> size * OUTLINE_PER_SIZE
            SubtitleLook.BOX -> size * BOX_PADDING_PER_SIZE
            SubtitleLook.SHADOW -> 0f
        }

    /** How far the shadow is moved right and down, as a fraction of the frame height. */
    val shadow: Float
        get() = if (look == SubtitleLook.SHADOW) size * SHADOW_PER_SIZE else 0f

    companion object {
        const val DEFAULT_SIZE = 0.06f
        const val MIN_SIZE = 0.025f
        const val MAX_SIZE = 0.16f

        const val WHITE = 0xFFFFFFFFL
        const val YELLOW = 0xFFFFD60AL

        /** The outline, the box and the shadow are drawn in these. */
        const val OUTLINE_COLOR = 0xFF000000L
        const val BOX_COLOR = 0xB3000000L
        const val SHADOW_COLOR = 0xCC000000L

        private const val OUTLINE_PER_SIZE = 0.07f
        private const val BOX_PADDING_PER_SIZE = 0.16f
        private const val SHADOW_PER_SIZE = 0.05f

        /** Ready-made looks, each a whole style kept where it is on the frame. */
        val PRESETS: List<SubtitlePreset> = listOf(
            SubtitlePreset.CLASSIC,
            SubtitlePreset.POP,
            SubtitlePreset.BOXED,
            SubtitlePreset.KARAOKE,
            SubtitlePreset.MINIMAL,
        )
    }
}

/** A ready-made look; [apply] keeps the position, the number of lines and the font. */
enum class SubtitlePreset(private val look: SubtitleStyle) {
    CLASSIC(SubtitleStyle(size = 0.055f, look = SubtitleLook.OUTLINE, bold = false, highlight = WordHighlight.NONE)),
    POP(SubtitleStyle(size = 0.075f, look = SubtitleLook.OUTLINE, bold = true, uppercase = true, highlight = WordHighlight.WORD, highlightColor = 0xFFFFD60AL)),
    BOXED(SubtitleStyle(size = 0.055f, look = SubtitleLook.BOX, bold = false, highlight = WordHighlight.WORD, highlightColor = 0xFF64D2FFL)),
    KARAOKE(SubtitleStyle(size = 0.065f, look = SubtitleLook.OUTLINE, bold = true, highlight = WordHighlight.SPOKEN, highlightColor = 0xFF30D158L)),
    MINIMAL(SubtitleStyle(size = 0.045f, look = SubtitleLook.SHADOW, bold = false, highlight = WordHighlight.NONE)),
    ;

    fun apply(to: SubtitleStyle): SubtitleStyle = look.copy(x = to.x, y = to.y, maxLines = to.maxLines, font = to.font)

    /** The preset [style] matches, if any (position and lines aside). */
    companion object {
        fun of(style: SubtitleStyle): SubtitlePreset? = entries.firstOrNull { it.apply(style) == style }
    }
}
