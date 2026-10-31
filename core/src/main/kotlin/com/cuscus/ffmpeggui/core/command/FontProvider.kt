package com.cuscus.ffmpeggui.core.command

/** Supplies font files for text overlays. Implemented per platform. */
fun interface FontProvider {
    /** Absolute path of a readable font file, bold when [bold] is true and one exists. */
    fun fontPath(bold: Boolean): String

    /** A font file of [family] (the default one when the phone has none of it), bold when [bold] and one exists. */
    fun fontPath(family: com.cuscus.ffmpeggui.core.model.TextFont, bold: Boolean): String = fontPath(bold)

    /** The font file [file] the user added, when it can still be read; null: the caption's family is used instead. */
    fun customFontPath(file: String): String? = file
}
