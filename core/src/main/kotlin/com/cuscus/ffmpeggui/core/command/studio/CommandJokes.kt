package com.cuscus.ffmpeggui.core.command.studio

import java.util.Locale

/**
 * The command studio's small talk: what is typed at terminals, which is not an
 * FFmpeg command, gets a reply instead of a list of errors. A few classics
 * match as the whole command (spaces and case aside, an "ffmpeg" before it
 * allowed); a few programs get their own reply whatever follows them (cat,
 * pwd, rm, python...); any other program a terminal would run gets the
 * general one. A real FFmpeg command is never taken for one, and the studio
 * never runs a command that gets a reply (nothing here ever reaches a shell
 * anyway). The words of each reply are the app's (strings, by [Joke]).
 * Pure, tested.
 */
object CommandJokes {

    /** What the studio answers. */
    enum class Joke {
        /** sudo anything else: there is no root to become. */
        ROOT,

        /** sudo please, please sudo. */
        PLEASE,

        /** make me a sandwich, asked plainly. */
        SANDWICH,

        /** sudo make me a sandwich. */
        SANDWICH_SUDO,

        /** rm -rf of the root, and its cousins. */
        WIPE,

        /** Any other rm. */
        RM,

        /** :q, :wq, :q!, :x, quit. */
        WAY_OUT,

        /** exit. */
        EXIT,

        /** vim, vi, nvim. */
        VIM,

        /** emacs, nano. */
        EDITOR,

        /** ping. */
        PING,

        /** whoami. */
        WHOAMI,

        /** ls, dir. */
        FILES,

        /** cat. */
        CAT,

        /** pwd. */
        PWD,

        /** cd. */
        CD,

        /** python, python3... */
        PYTHON,

        /** apt, apt-get. */
        APT,

        /** dnf. */
        DNF,

        /** pacman. */
        PACMAN,

        /** sl, the steam locomotive for whoever meant ls. */
        SL,

        /** clear, cls. */
        CLEAR,

        /** top, htop. */
        TOP,

        /** git status. */
        GIT_STATUS,

        /** kill, killall, pkill. */
        KILL,

        /** man. */
        MAN,

        /** history. */
        HISTORY,

        /** echo. */
        ECHO,

        /** reboot. */
        REBOOT,

        /** nmap. */
        NMAP,

        /** grep, egrep, fgrep. */
        GREP,

        /** ssh, su: the gate of Dante's hell. */
        HELL,

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

        /** Any other program typed as a terminal would take it: this is not a shell. */
        SHELL,
    }

    /** The reply to [text], or null when it is anything else (a command, or the start of one). */
    fun replyTo(text: String): Joke? {
        val words = text.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val said = if (words.firstOrNull() == "ffmpeg") words.drop(1) else words
        if (said.isEmpty()) return null
        val line = said.joinToString(" ")
        WHOLE[line]?.let { return it }
        if (said.first() == "sudo") {
            val rest = said.drop(1)
            val restLine = rest.joinToString(" ")
            return when {
                rest.firstOrNull() == "please" -> Joke.PLEASE
                restLine == "make me a sandwich" -> Joke.SANDWICH_SUDO
                WIPES.matches(restLine) -> Joke.WIPE
                else -> Joke.ROOT
            }
        }
        if (said.first() == "please" && said.getOrNull(1) == "sudo") return Joke.PLEASE
        if (WIPES.matches(line)) return Joke.WIPE
        // A program's own reply, whatever follows it; not after "ffmpeg", which is a command.
        if (words.first() != "ffmpeg") {
            val first = words.first()
            PROGRAMS[first]?.let { return it }
            if (PYTHONS.matches(first)) return Joke.PYTHON
        }
        if (program(text) != null) return Joke.SHELL
        return null
    }

    /**
     * The program [text] calls when it is some other program than FFmpeg's, as
     * a terminal command starts (a lowercase word: no option, no file, no path),
     * else null. A word on its way to "ffmpeg" (f, ff, ffm...) is not one: the
     * studio does not answer a command still being typed.
     */
    fun program(text: String): String? {
        val words = text.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = (if (words.firstOrNull() == "sudo") words.drop(1) else words).firstOrNull() ?: return null
        if (!PROGRAM.matches(first)) return null
        if (OURS.any { it.startsWith(first) }) return null
        return first
    }

    /** Whole commands with a reply of their own ("ffmpeg" before them allowed). */
    private val WHOLE = mapOf(
        "make me a sandwich" to Joke.SANDWICH,
        ":q" to Joke.WAY_OUT,
        ":q!" to Joke.WAY_OUT,
        ":wq" to Joke.WAY_OUT,
        ":x" to Joke.WAY_OUT,
        "quit" to Joke.WAY_OUT,
        "exit" to Joke.EXIT,
        "vim" to Joke.VIM,
        "vi" to Joke.VIM,
        "emacs" to Joke.EDITOR,
        "nano" to Joke.EDITOR,
        "ping" to Joke.PING,
        "whoami" to Joke.WHOAMI,
        "ls" to Joke.FILES,
        "hello" to Joke.HELLO,
        "hi" to Joke.HELLO,
        "xyzzy" to Joke.XYZZY,
        "coffee" to Joke.TEAPOT,
        "brew coffee" to Joke.TEAPOT,
        "make coffee" to Joke.TEAPOT,
        "42" to Joke.ANSWER,
        "git push" to Joke.PUSH,
        "git status" to Joke.GIT_STATUS,
    )

    /** Programs with a reply of their own, whatever their arguments. */
    private val PROGRAMS = mapOf(
        "cat" to Joke.CAT,
        "pwd" to Joke.PWD,
        "cd" to Joke.CD,
        "apt" to Joke.APT,
        "apt-get" to Joke.APT,
        "dnf" to Joke.DNF,
        "pacman" to Joke.PACMAN,
        "rm" to Joke.RM,
        "ls" to Joke.FILES,
        "dir" to Joke.FILES,
        "sl" to Joke.SL,
        "clear" to Joke.CLEAR,
        "cls" to Joke.CLEAR,
        "top" to Joke.TOP,
        "htop" to Joke.TOP,
        "exit" to Joke.EXIT,
        "vim" to Joke.VIM,
        "vi" to Joke.VIM,
        "nvim" to Joke.VIM,
        "kill" to Joke.KILL,
        "killall" to Joke.KILL,
        "pkill" to Joke.KILL,
        "whoami" to Joke.WHOAMI,
        "man" to Joke.MAN,
        "ping" to Joke.PING,
        "history" to Joke.HISTORY,
        "echo" to Joke.ECHO,
        "reboot" to Joke.REBOOT,
        "nmap" to Joke.NMAP,
        "grep" to Joke.GREP,
        "egrep" to Joke.GREP,
        "fgrep" to Joke.GREP,
        "ssh" to Joke.HELL,
        "su" to Joke.HELL,
    )

    /** python, python3, python3.12... */
    private val PYTHONS = Regex("""python[0-9.]*""")


    /** A program's name as typed at a terminal: letters first, then letters, digits, - and _. */
    private val PROGRAM = Regex("""[a-z][a-z0-9_-]*""")

    /** FFmpeg's own programs, and every start of their names. */
    private val OURS = listOf("ffmpeg", "ffprobe", "ffplay")

    // rm -rf of the root (or of everything in it, with a star), its flags in any order,
    // with or without --no-preserve-root.
    private val WIPES = Regex("""rm( --no-preserve-root)? -(rf|fr|r -f|f -r)( --no-preserve-root)? /\*?""")
}
