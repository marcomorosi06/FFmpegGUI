package com.cuscus.ffmpeggui.core.engine

/** Extracts progress from FFmpeg's periodic "time=HH:MM:SS.ss" status output. */
class ProgressParser {

    private val timePattern = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")

    /** Position in the output, in milliseconds, or null when [logLine] carries none. */
    fun positionMs(logLine: String): Long? {
        val match = timePattern.find(logLine) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toDoubleOrNull() ?: return null
        return ((hours * 3600 + minutes * 60 + seconds) * 1000.0).toLong()
    }

    /** Completed fraction (0..1), or null when unknown. */
    fun fraction(logLine: String, expectedDurationMs: Long?): Float? {
        if (expectedDurationMs == null || expectedDurationMs <= 0) return null
        val position = positionMs(logLine) ?: return null
        return (position.toDouble() / expectedDurationMs).toFloat().coerceIn(0f, 1f)
    }
}
