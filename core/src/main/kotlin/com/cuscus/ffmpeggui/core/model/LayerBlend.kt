package com.cuscus.ffmpeggui.core.model

/**
 * How a picture laid over the picture or the video mixes with what lies under
 * it, as in a photo app's layers. NORMAL simply covers it; the others are the
 * separable blend modes of the W3C compositing spec, which Android draws too
 * (the editor's preview), done by FFmpeg's `blend` filter in planar RGB with the
 * picture as its top input and what lies under it as the bottom one.
 *
 * [ffmpegMode] is the `blend` mode that gives the mode with that order: FFmpeg's
 * `hardlight` tests the bottom input, which is the spec's overlay, and its
 * `overlay` tests the top input, which is the spec's hard light. Soft light is
 * left out: FFmpeg's formula is not the spec's.
 */
enum class LayerBlend(val ffmpegMode: String?) {
    NORMAL(null),
    MULTIPLY("multiply"),
    SCREEN("screen"),
    OVERLAY("hardlight"),
    DARKEN("darken"),
    LIGHTEN("lighten"),
    COLOR_DODGE("dodge"),
    COLOR_BURN("burn"),
    HARD_LIGHT("overlay"),
    DIFFERENCE("difference"),
    EXCLUSION("exclusion"),
    ADD("addition"),
    ;

    companion object {
        /** The mode called [name] ([LayerBlend.name]), NORMAL when there is none (a file of another version). */
        fun named(name: String?): LayerBlend = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
