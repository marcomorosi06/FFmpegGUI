package com.cuscus.ffmpeggui.core.command

/*
 * Minimal POSIX-shell-compatible quoting, so that a rendered command can be
 * pasted into a terminal and a hand-edited one is split the way a shell would.
 *
 *  - whitespace separates arguments;
 *  - '...' is taken literally;
 *  - "..." is taken literally except that a backslash escapes " \ $ and `;
 *  - outside quotes a backslash escapes the next character.
 */

private const val DOUBLE_QUOTE_ESCAPABLE = "\"\\$`"

/** Characters that never need quoting. Everything else triggers double quotes. */
private fun isPlain(c: Char): Boolean =
    c.isLetterOrDigit() || c in "-_./:=+,%@"

/**
 * One argument of a command line and where it sits in the text: [start] is its
 * first character (an opening quote included), [end] one past its last.
 * [openQuote] is the quote left open at the end of the line, if any.
 */
data class CommandSpan(
    val value: String,
    val start: Int,
    val end: Int,
    val openQuote: Char? = null,
)

/**
 * Splits a command line into arguments, keeping where each one is. Unterminated
 * quotes run to the end; a backslash before a line break joins the two lines,
 * as in a shell (so a command laid out on several lines still runs).
 */
fun spanCommandLine(commandLine: String): List<CommandSpan> {
    val spans = mutableListOf<CommandSpan>()
    val current = StringBuilder()
    var start = -1
    var open: Char? = null
    var i = 0
    fun begin(at: Int) {
        if (start < 0) start = at
    }
    fun finish(end: Int) {
        if (start >= 0) spans += CommandSpan(current.toString(), start, end, open)
        current.setLength(0)
        start = -1
        open = null
    }

    while (i < commandLine.length) {
        val c = commandLine[i]
        val next = commandLine.getOrNull(i + 1)
        when {
            c == '\\' && next == '\n' -> i += 2
            c == '\'' -> {
                begin(i)
                val close = commandLine.indexOf('\'', i + 1)
                if (close < 0) {
                    current.append(commandLine, i + 1, commandLine.length)
                    open = '\''
                    i = commandLine.length
                } else {
                    current.append(commandLine, i + 1, close)
                    i = close + 1
                }
            }
            c == '"' -> {
                begin(i)
                i++
                var closed = false
                while (i < commandLine.length) {
                    val q = commandLine[i]
                    val after = commandLine.getOrNull(i + 1)
                    if (q == '"') {
                        closed = true
                        i++
                        break
                    }
                    if (q == '\\' && after == '\n') {
                        i += 2
                    } else if (q == '\\' && after != null && after in DOUBLE_QUOTE_ESCAPABLE) {
                        current.append(after)
                        i += 2
                    } else {
                        current.append(q)
                        i++
                    }
                }
                if (!closed) open = '"'
            }
            c == '\\' -> {
                begin(i)
                if (next != null) {
                    current.append(next)
                    i += 2
                } else {
                    current.append(c)
                    i++
                }
            }
            c.isWhitespace() -> {
                finish(i)
                i++
            }
            else -> {
                begin(i)
                current.append(c)
                i++
            }
        }
    }
    finish(commandLine.length)
    return spans
}

/** Splits a command line into arguments. Unterminated quotes run to the end. */
fun tokenizeCommandLine(commandLine: String): List<String> = spanCommandLine(commandLine).map { it.value }

/** Quotes a single argument if (and only if) it needs it. */
fun quoteArgument(argument: String): String {
    if (argument.isNotEmpty() && argument.all(::isPlain)) return argument
    val quoted = StringBuilder("\"")
    for (c in argument) {
        if (c in DOUBLE_QUOTE_ESCAPABLE) quoted.append('\\')
        quoted.append(c)
    }
    return quoted.append('"').toString()
}

/** Inverse of [tokenizeCommandLine]. */
fun renderCommandLine(arguments: List<String>): String =
    arguments.joinToString(" ", transform = ::quoteArgument)
