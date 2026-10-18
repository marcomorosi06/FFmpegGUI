package com.cuscus.ffmpeggui.core.command

/** Supplies font files for text overlays. Implemented per platform. */
fun interface FontProvider {
    /** Absolute path of a readable font file, bold when [bold] is true and one exists. */
    fun fontPath(bold: Boolean): String

    /** A font file of [family] (the default one when the phone has none of it), bold when [bold] and one exists. */
    fun fontPath(family: com.cuscus.ffmpeggui.core.model.TextFont, bold: Boolean): String = fontPath(bold)
}
