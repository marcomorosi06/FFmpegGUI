package com.cuscus.ffmpeggui.core.command

/*
 * FFmpeg filter strings are parsed in layers (see "Notes on filtergraph
 * escaping" in the FFmpeg manual):
 *
 *   1. the filtergraph parser splits on , ; [ ] and understands '...' quoting;
 *   2. each filter's option parser splits on : and = and unescapes backslashes;
 *   3. drawtext additionally expands %{...} sequences in its text.
 *
 * The helpers below apply the escaping for one or more of those layers.
 */

/** Layer 2: protects a value from the option parser. */
private fun escapeForOptionParser(value: String): String = buildString {
    for (c in value) {
        if (c == '\\' || c == ':' || c == '\'') append('\\')
        append(c)
    }
}

/** Layer 1: wraps a string in single quotes for the filtergraph parser. */
private fun quoteForFiltergraph(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

/** Escapes an arbitrary value (typically a file path) used as a filter option. */
internal fun filterOptionValue(value: String): String =
    quoteForFiltergraph(escapeForOptionParser(value))

/** Escapes user text for drawtext's `text=` option (all three layers). */
internal fun drawtextValue(text: String): String {
    val expansionSafe = buildString {
        for (c in text) {
            if (c == '\\' || c == '%') append('\\')
            append(c)
        }
    }
    return filterOptionValue(expansionSafe)
}
