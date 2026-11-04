package com.cuscus.ffmpeggui.core.command.studio

import java.util.Locale

/**
 * The command studio's small talk: a few classics typed at terminals for
 * decades, which are not FFmpeg commands, get a reply instead of a list of
 * errors. Only the whole command matches (spaces and case aside, an
 * "ffmpeg" before it allowed), so a real command is never taken for one; and
 * the studio never runs a command that matches (nothing here ever reaches a
 * shell anyway). The words of each reply are the app's (strings, by [Joke]).
 * Pure, tested.
 */
object CommandJokes {

    /** What the studio answers. */
    enum class Joke {
        /** sudo anything: there is no root to become. */
        ROOT,

        /** make me a sandwich, asked plainly. */
        SANDWICH,

        /** sudo make me a sandwich. */
        SANDWICH_SUDO,

        /** rm -rf of the root, and its cousins. */
        WIPE,

        /** :q, :wq, :q!, exit, quit. */
        WAY_OUT,

        /** vim, emacs, nano. */
        EDITOR,

        /** ping. */
        PING,

        /** whoami. */
        WHOAMI,

        /** ls, dir. */
        FILES,

        /** hello, hi. */
        HELLO,

        /** xyzzy, the magic word of the first adventure game. */
        XYZZY,

        /** coffee, brew coffee, make coffee: HTCPCP's answer (RFC 2324). */
        TEAPOT,

        /** 42. */
        ANSWER,

        /** git push. */
        PUSH,
    }

    /** The reply to [text], or null when it is anything else (a command, or the start of one). */
    fun replyTo(text: String): Joke? {
        val words = text.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val said = if (words.firstOrNull() == "ffmpeg") words.drop(1) else words
        if (said.isEmpty()) return null
        val line = said.joinToString(" ")
        WHOLE[line]?.let { return it }
        if (said.first() == "sudo") {
            val rest = said.drop(1).joinToString(" ")
            return when {
                rest == "make me a sandwich" -> Joke.SANDWICH_SUDO
                WIPES.matches(rest) -> Joke.WIPE
                else -> Joke.ROOT
            }
        }
        if (WIPES.matches(line)) return Joke.WIPE
        return null
    }

    private val WHOLE = mapOf(
        "make me a sandwich" to Joke.SANDWICH,
        ":q" to Joke.WAY_OUT,
        ":q!" to Joke.WAY_OUT,
        ":wq" to Joke.WAY_OUT,
        ":x" to Joke.WAY_OUT,
        "exit" to Joke.WAY_OUT,
        "quit" to Joke.WAY_OUT,
        "vim" to Joke.EDITOR,
        "vi" to Joke.EDITOR,
        "emacs" to Joke.EDITOR,
        "nano" to Joke.EDITOR,
        "ping" to Joke.PING,
        "whoami" to Joke.WHOAMI,
        "ls" to Joke.FILES,
        "ls -la" to Joke.FILES,
        "dir" to Joke.FILES,
        "hello" to Joke.HELLO,
        "hi" to Joke.HELLO,
        "xyzzy" to Joke.XYZZY,
        "coffee" to Joke.TEAPOT,
        "brew coffee" to Joke.TEAPOT,
        "make coffee" to Joke.TEAPOT,
        "42" to Joke.ANSWER,
        "git push" to Joke.PUSH,
    )

    // rm -rf of the root (or of everything in it, with a star), its flags in any order,
    // with or without --no-preserve-root.
    private val WIPES = Regex("""rm( --no-preserve-root)? -(rf|fr|r -f|f -r)( --no-preserve-root)? /\*?""")
}
