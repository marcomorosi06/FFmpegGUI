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

/** Splits a command line into arguments. Unterminated quotes run to the end. */
fun tokenizeCommandLine(commandLine: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inToken = false
    var i = 0

    while (i < commandLine.length) {
        val c = commandLine[i]
        when {
            c == '\'' -> {
                inToken = true
                val end = commandLine.indexOf('\'', i + 1).let { if (it < 0) commandLine.length else it }
                current.append(commandLine, i + 1, end)
                i = end
            }
            c == '"' -> {
                inToken = true
                i++
                while (i < commandLine.length && commandLine[i] != '"') {
                    val q = commandLine[i]
                    val next = commandLine.getOrNull(i + 1)
                    if (q == '\\' && next != null && next in DOUBLE_QUOTE_ESCAPABLE) {
                        current.append(next)
                        i += 2
                    } else {
                        current.append(q)
                        i++
                    }
                }
            }
            c == '\\' -> {
                inToken = true
                val next = commandLine.getOrNull(i + 1)
                if (next != null) {
                    current.append(next)
                    i++
                } else {
                    current.append(c)
                }
            }
            c.isWhitespace() -> {
                if (inToken) {
                    tokens += current.toString()
                    current.setLength(0)
                    inToken = false
                }
            }
            else -> {
                inToken = true
                current.append(c)
            }
        }
        i++
    }
    if (inToken) tokens += current.toString()
    return tokens
}

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
