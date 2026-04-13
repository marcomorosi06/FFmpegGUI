package com.cuscus.ffmpeggui

import android.util.Log
import java.io.File

/**
 * Resolves a usable system font path for FFmpeg's drawtext filter.
 *
 * Different OEMs ship fonts at different locations and with different names.
 * This resolver tries a broad set of known paths and falls back gracefully.
 * The result is cached per (isBold) key to avoid repeated filesystem hits.
 */
object FontResolver {

    private const val TAG = "FontResolver"

    /**
     * Ordered list of (bold, regular) font path pairs to attempt.
     * Bold paths come first so that [resolve](isBold = true) gets the best match.
     */
    private val BOLD_CANDIDATES = listOf(
        // Stock Android / AOSP
        "/system/fonts/Roboto-Bold.ttf",
        "/system/fonts/NotoSans-Bold.ttf",
        // Samsung One UI
        "/system/fonts/SamsungSans-Bold.ttf",
        "/system/fonts/SamsungOne-700.ttf",
        // Xiaomi / MIUI
        "/system/fonts/MiSans-Bold.ttf",
        "/system/fonts/MiSans-Demibold.ttf",
        // OPPO / ColorOS
        "/system/fonts/OPPOSans-B.ttf",
        // Older fallbacks
        "/system/fonts/DroidSans-Bold.ttf",
        "/system/fonts/AndroidClock-Regular.ttf",
    )

    private val REGULAR_CANDIDATES = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/SamsungSans-Regular.ttf",
        "/system/fonts/SamsungOne-400.ttf",
        "/system/fonts/MiSans-Regular.ttf",
        "/system/fonts/OPPOSans-R.ttf",
        "/system/fonts/DroidSans.ttf",
        // Last-resort: any .ttf in /system/fonts
        "/system/fonts/",
    )

    /** Cache: key = isBold, value = resolved absolute path */
    private val cache = mutableMapOf<Boolean, String>()

    /**
     * Returns an absolute font path suitable for FFmpeg's `fontfile=` option.
     * Never returns null or a non-existent path — falls back to a best-effort
     * scan of /system/fonts/ if no known candidate is found.
     */
    fun resolve(isBold: Boolean): String {
        cache[isBold]?.let { return it }

        val primary = if (isBold) BOLD_CANDIDATES else REGULAR_CANDIDATES
        val secondary = if (isBold) REGULAR_CANDIDATES else BOLD_CANDIDATES

        for (path in primary + secondary) {
            if (path.endsWith("/")) continue          // skip the wildcard entry for now
            val f = File(path)
            if (f.exists() && f.canRead()) {
                Log.d(TAG, "Resolved font (isBold=$isBold): $path")
                cache[isBold] = path
                return path
            }
        }

        // Last resort: pick the first .ttf found in /system/fonts/
        val fallback = File("/system/fonts/")
            .takeIf { it.isDirectory }
            ?.listFiles { f -> f.extension.equals("ttf", ignoreCase = true) && f.canRead() }
            ?.firstOrNull()
            ?.absolutePath

        if (fallback != null) {
            Log.w(TAG, "No known font found for isBold=$isBold; using fallback: $fallback")
            cache[isBold] = fallback
            return fallback
        }

        // Absolute last resort – this will likely cause FFmpeg to log an error,
        // but returning something is safer than throwing.
        val hardcoded = "/system/fonts/Roboto-Regular.ttf"
        Log.e(TAG, "Could not find any readable system font. Returning hardcoded path: $hardcoded")
        cache[isBold] = hardcoded
        return hardcoded
    }

    /** Clears the in-memory cache (useful for tests). */
    internal fun clearCache() = cache.clear()
}
