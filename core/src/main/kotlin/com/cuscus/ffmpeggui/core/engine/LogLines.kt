package com.cuscus.ffmpeggui.core.engine

/**
 * Joins FFmpeg's log into whole lines. FFmpeg hands its log over in pieces
 * (a key, its value, the line's end, each on its own), so that shown one per
 * line it reads as a word per line. Every piece is [feed], and each line is
 * passed to [onLine] once it ends ("\n", or "\r" for the progress line FFmpeg
 * rewrites in place); [flush] passes on what is left. Blank lines are dropped.
 * Pieces may come from another thread than the flush: both hold one lock.
 */
class LogLineJoiner(private val onLine: (String) -> Unit) {

    private val pending = StringBuilder()

    fun feed(piece: String) {
        val ended = ArrayList<String>(1)
        synchronized(pending) {
            for (c in piece) {
                if (c == '\n' || c == '\r') take()?.let(ended::add) else pending.append(c)
            }
        }
        // Outside the lock: what is done with a line may take its time.
        ended.forEach(onLine)
    }

    fun flush() {
        val last = synchronized(pending) { take() }
        last?.let(onLine)
    }

    private fun take(): String? {
        val line = pending.toString().trimEnd()
        pending.setLength(0)
        return line.takeIf { it.isNotBlank() }
    }
}

/**
 * Whether FFmpeg said, in any pass of a conversion, that the phone's storage
 * is full ("No space left on device", the system's ENOSPC): the failure is
 * then told as such, not as an encoding error.
 */
class StorageFull {
    @Volatile
    var seen: Boolean = false

    /** The failure to report: [FailureReason.STORAGE_FULL] when it was seen, else [reason]. */
    fun or(reason: FailureReason, detail: String = ""): ConversionOutcome.Failure =
        if (seen) ConversionOutcome.Failure(FailureReason.STORAGE_FULL) else ConversionOutcome.Failure(reason, detail)

    companion object {
        /** True when [line] is FFmpeg saying the storage is full. */
        fun saysSo(line: String): Boolean = line.contains("No space left on device", ignoreCase = true) || line.contains("ENOSPC")
    }
}
