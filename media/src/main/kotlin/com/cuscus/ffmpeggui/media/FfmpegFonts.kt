package com.cuscus.ffmpeggui.media

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKitConfig

/**
 * Tells FFmpeg's fontconfig where the phone's fonts are, so that libass (the
 * subtitles filter) finds a font for any subtitle file, whatever font it asks
 * for and whatever its alphabet. Styled subtitles name their own font and hand
 * it over directly as well (see the conversion engine).
 */
object FfmpegFonts {

    private const val TAG = "FfmpegFonts"
    private const val SYSTEM_FONTS = "/system/fonts"

    @Volatile private var configured = false

    /** Writes FFmpegKit's font configuration once per process; call it away from the main thread. */
    fun configure(context: Context) {
        if (configured) return
        try {
            FFmpegKitConfig.setFontDirectoryList(context.applicationContext, listOf(SYSTEM_FONTS), emptyMap())
            configured = true
        } catch (e: Exception) {
            Log.w(TAG, "Font configuration failed", e)
        }
    }
}
