package com.cuscus.ffmpeggui.subtitles

import com.cuscus.ffmpeggui.core.command.FontProvider
import com.cuscus.ffmpeggui.core.model.TextFont
import com.cuscus.ffmpeggui.core.subtitles.FontFacts
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A font file and what libass reads in it. */
class SubtitleFont(val file: File, val facts: FontFacts)

/**
 * The fonts styled subtitles are drawn with: the same files the export hands
 * to libass (see the conversion engine), so that the preview measures and
 * draws the very same letters. Read once, on first use.
 */
class SubtitleFonts(private val provider: FontProvider) {

    private val cache = ConcurrentHashMap<Pair<TextFont, Boolean>, SubtitleFont>()
    private val missing = ConcurrentHashMap.newKeySet<Pair<TextFont, Boolean>>()

    /** The font of the regular or the bold text of [font]; null when no font could be read. Reads a file: not on the main thread. */
    fun of(font: TextFont, bold: Boolean): SubtitleFont? {
        val key = font to bold
        cache[key]?.let { return it }
        if (key in missing) return null
        val file = file(font, bold)
        val facts = FontFacts.read(file)
        if (facts == null) {
            missing += key
            return null
        }
        return SubtitleFont(file, facts).also { cache[key] = it }
    }

    fun of(bold: Boolean): SubtitleFont? = of(TextFont.SANS, bold)

    private val added = ConcurrentHashMap<String, SubtitleFont>()

    /**
     * The font file the user added, [fontFile], while it can be read (as the export
     * takes it, see the conversion engine); else that of [font]. Reads a file.
     */
    fun of(font: TextFont, bold: Boolean, fontFile: String?): SubtitleFont? {
        if (fontFile == null) return of(font, bold)
        added[fontFile]?.let { if (it.file.isFile) return it }
        val file = provider.customFontPath(fontFile)?.let(::File)?.takeIf { it.isFile } ?: return of(font, bold)
        val facts = FontFacts.read(file) ?: return of(font, bold)
        return SubtitleFont(file, facts).also { added[fontFile] = it }
    }

    /** The file FFmpeg draws [font] with (captions use it as it is). */
    fun file(font: TextFont, bold: Boolean): File = File(provider.fontPath(font, bold))
}
