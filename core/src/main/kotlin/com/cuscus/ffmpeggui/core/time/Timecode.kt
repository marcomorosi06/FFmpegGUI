package com.cuscus.ffmpeggui.core.time

import java.util.Locale

/**
 * Parses "HH:MM:SS" or "HH:MM:SS.mmm" into milliseconds.
 * Anything that does not look like a timecode yields 0.
 */
fun parseTimecodeMs(timecode: String): Long {
    val parts = timecode.trim().split(":")
    if (parts.size != 3) return 0L
    val hours = parts[0].toLongOrNull() ?: 0L
    val minutes = parts[1].toLongOrNull() ?: 0L
    val secondParts = parts[2].split(".")
    val seconds = secondParts[0].toLongOrNull() ?: 0L
    val millis = if (secondParts.size > 1) {
        secondParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    } else {
        0L
    }
    return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis
}

/** Formats milliseconds as "HH:MM:SS.mmm", the form FFmpeg accepts for -ss / -to. */
fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000
    return String.format(
        Locale.US,
        "%02d:%02d:%02d.%03d",
        totalSeconds / 3_600,
        (totalSeconds % 3_600) / 60,
        totalSeconds % 60,
        safe % 1_000,
    )
}
