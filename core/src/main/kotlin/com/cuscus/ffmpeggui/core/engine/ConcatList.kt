package com.cuscus.ffmpeggui.core.engine

import java.util.Locale

/** One file of an ffconcat list with its optional trim window. */
data class ConcatEntry(
    val path: String,
    val inpointMs: Long? = null,
    val outpointMs: Long? = null,
)

/** Renders the text of an ffconcat (concat demuxer) list. */
fun renderConcatList(entries: List<ConcatEntry>): String = buildString {
    append("ffconcat version 1.0\n")
    for (entry in entries) {
        append("file '").append(entry.path.replace("'", "'\\''")).append("'\n")
        entry.inpointMs?.takeIf { it > 0 }?.let { append("inpoint ").append(seconds(it)).append('\n') }
        entry.outpointMs?.let { append("outpoint ").append(seconds(it)).append('\n') }
    }
}

private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)
