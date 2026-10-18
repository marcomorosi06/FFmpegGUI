package com.cuscus.ffmpeggui.media

import android.util.Log
import com.cuscus.ffmpeggui.core.command.FontProvider
import com.cuscus.ffmpeggui.core.model.TextFont
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a system font usable by FFmpeg's drawtext filter.
 *
 * Vendors ship different fonts under different names, so a list of well-known
 * files is tried first and the font directory is scanned as a last resort.
 * Lookups are cached per instance.
 */
class SystemFontProvider(
    private val fontDirectory: File = File("/system/fonts"),
) : FontProvider {

    private val cache = ConcurrentHashMap<Boolean, String>()
    private val familyCache = ConcurrentHashMap<Pair<TextFont, Boolean>, String>()

    override fun fontPath(bold: Boolean): String = cache.getOrPut(bold) { locate(bold) }

    override fun fontPath(family: TextFont, bold: Boolean): String {
        if (family == TextFont.SANS) return fontPath(bold)
        return familyCache.getOrPut(family to bold) {
            val (regular, boldFiles) = FAMILIES.getValue(family)
            (if (bold) boldFiles + regular else regular)
                .map { File(fontDirectory, it) }
                .firstOrNull { it.isFile && it.canRead() }
                ?.absolutePath
                // Not on this phone: the default font, as the preview shows it too.
                ?: fontPath(bold)
        }
    }

    private fun locate(bold: Boolean): String {
        val preferred = if (bold) BOLD_FONTS else REGULAR_FONTS
        val alternative = if (bold) REGULAR_FONTS else BOLD_FONTS

        (preferred + alternative)
            .map { File(fontDirectory, it) }
            .firstOrNull { it.isFile && it.canRead() }
            ?.let { return it.absolutePath }

        val anyFont = fontDirectory
            .listFiles { file -> file.extension.equals("ttf", ignoreCase = true) && file.canRead() }
            ?.minByOrNull { it.name }
        if (anyFont != null) {
            Log.w(TAG, "No known font found (bold=$bold); using ${anyFont.name}")
            return anyFont.absolutePath
        }

        // FFmpeg will report the missing file; returning a path keeps the command well-formed.
        Log.e(TAG, "No readable font in $fontDirectory")
        return File(fontDirectory, REGULAR_FONTS.first()).absolutePath
    }

    private companion object {
        const val TAG = "SystemFontProvider"

        val BOLD_FONTS = listOf(
            "Roboto-Bold.ttf",        // AOSP
            "NotoSans-Bold.ttf",
            "SamsungSans-Bold.ttf",   // One UI
            "SamsungOne-700.ttf",
            "MiSans-Bold.ttf",        // MIUI / HyperOS
            "MiSans-Demibold.ttf",
            "OPPOSans-B.ttf",         // ColorOS
            "DroidSans-Bold.ttf",     // legacy
        )

        /** The files of each family (regular, then bold), as AOSP names them. */
        val FAMILIES: Map<TextFont, Pair<List<String>, List<String>>> = mapOf(
            TextFont.SERIF to (listOf("NotoSerif-Regular.ttf", "NotoSerif.ttf", "DroidSerif-Regular.ttf") to listOf("NotoSerif-Bold.ttf", "DroidSerif-Bold.ttf")),
            TextFont.MONO to (listOf("DroidSansMono.ttf", "RobotoMono-Regular.ttf") to listOf("RobotoMono-Bold.ttf")),
            TextFont.TYPEWRITER to (listOf("CutiveMono.ttf") to emptyList()),
            TextFont.HANDWRITING to (listOf("DancingScript-Regular.ttf") to listOf("DancingScript-Bold.ttf")),
            TextFont.CASUAL to (listOf("ComingSoon.ttf") to emptyList()),
            TextFont.SMALL_CAPS to (listOf("CarroisGothicSC-Regular.ttf") to emptyList()),
        )

        val REGULAR_FONTS = listOf(
            "Roboto-Regular.ttf",
            "NotoSans-Regular.ttf",
            "SamsungSans-Regular.ttf",
            "SamsungOne-400.ttf",
            "MiSans-Regular.ttf",
            "OPPOSans-R.ttf",
            "DroidSans.ttf",
        )
    }
}
