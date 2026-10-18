package com.cuscus.ffmpeggui.core.model

/**
 * The typeface of a caption or of the subtitles: one of the fonts every
 * Android phone has (AOSP ships them all). Where a phone lacks one, the
 * default sans-serif font takes its place, in the preview and in the export
 * alike (both draw from the same file, see FontProvider).
 */
enum class TextFont {
    /** Roboto, or the phone's own sans-serif. */
    SANS,
    SERIF,
    MONO,
    TYPEWRITER,
    HANDWRITING,
    CASUAL,
    SMALL_CAPS,
}
