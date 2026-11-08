package com.cuscus.ffmpeggui.core.command.studio

import com.cuscus.ffmpeggui.core.command.studio.CommandJokes.Joke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandJokesTest {

    @Test
    fun `the classics get their reply, spaces, case and ffmpeg aside`() {
        assertEquals(Joke.SANDWICH, CommandJokes.replyTo("make me a sandwich"))
        assertEquals(Joke.SANDWICH_SUDO, CommandJokes.replyTo("  SUDO   make me a sandwich "))
        assertEquals(Joke.ROOT, CommandJokes.replyTo("sudo ffmpeg -i in.mp4 out.mp4"))
        assertEquals(Joke.WIPE, CommandJokes.replyTo("rm -rf /"))
        assertEquals(Joke.WIPE, CommandJokes.replyTo("sudo rm -rf /*"))
        assertEquals(Joke.WIPE, CommandJokes.replyTo("rm -r -f --no-preserve-root /"))
        assertEquals(Joke.WAY_OUT, CommandJokes.replyTo(":wq"))
        assertEquals(Joke.VIM, CommandJokes.replyTo("ffmpeg vim"))
        assertEquals(Joke.EDITOR, CommandJokes.replyTo("nano"))
        assertEquals(Joke.TEAPOT, CommandJokes.replyTo("brew coffee"))
        assertEquals(Joke.XYZZY, CommandJokes.replyTo("xyzzy"))
        assertEquals(Joke.ANSWER, CommandJokes.replyTo("42"))
        assertEquals(Joke.PUSH, CommandJokes.replyTo("git push"))
    }

    @Test
    fun `real commands and half-typed ones are left alone`() {
        assertNull(CommandJokes.replyTo(""))
        assertNull(CommandJokes.replyTo("ffmpeg"))
        assertNull(CommandJokes.replyTo("ffmpeg -i in.mp4 -c:v libx264 out.mp4"))
        assertNull(CommandJokes.replyTo("-i ping.mp4 out.mp4"))
        // Not the classics they start like: only the general reply to a terminal command.
        assertEquals(Joke.RM, CommandJokes.replyTo("rm -rf ./old"))
        assertEquals(Joke.SHELL, CommandJokes.replyTo("make me a"))
        assertEquals(Joke.SHELL, CommandJokes.replyTo("pingpong"))
    }

    @Test
    fun `any other program a terminal would run gets the general reply`() {
        for (typed in listOf("make", "git log", "ffprobes", "termux-setup-storage", "sudo-not")) {
            assertEquals(typed, Joke.SHELL, CommandJokes.replyTo(typed))
        }
        assertEquals("pwd", CommandJokes.program("pwd"))
        assertEquals("cd", CommandJokes.program("cd /sdcard"))
        assertEquals("apt", CommandJokes.program("sudo apt install ffmpeg").let { CommandJokes.program("apt install ffmpeg") })
        // The ones with a reply of their own keep it.
        assertEquals(Joke.FILES, CommandJokes.replyTo("ls"))
        assertEquals(Joke.ROOT, CommandJokes.replyTo("sudo apt install ffmpeg"))
        // FFmpeg's own, on the way to being typed, or a real command: no reply.
        for (typed in listOf("f", "ff", "ffm", "ffmpe", "ffmpeg", "ffpro", "ffplay", "ffmpeg -i input.mp4 out.mp4", "-i input.mp4", "input.mp4", "/bin/ls", "", "   ")) {
            assertNull(typed, CommandJokes.replyTo(typed))
            assertNull(typed, CommandJokes.program(typed))
        }
    }

    @Test
    fun `some programs get their own reply, whatever follows them`() {
        val expected = mapOf(
            "cat input.mp4" to Joke.CAT,
            "pwd" to Joke.PWD,
            "cd /sdcard" to Joke.CD,
            "python3 -m http.server" to Joke.PYTHON,
            "python" to Joke.PYTHON,
            "apt install ffmpeg" to Joke.APT,
            "apt-get update" to Joke.APT,
            "dnf upgrade" to Joke.DNF,
            "pacman -Syu" to Joke.PACMAN,
            "rm out.mp4" to Joke.RM,
            "ls -la" to Joke.FILES,
            "sl" to Joke.SL,
            "clear" to Joke.CLEAR,
            "top" to Joke.TOP,
            "exit" to Joke.EXIT,
            "exit 0" to Joke.EXIT,
            "vim notes.txt" to Joke.VIM,
            "git status" to Joke.GIT_STATUS,
            "kill -9 1234" to Joke.KILL,
            "killall ffmpeg" to Joke.KILL,
            "whoami" to Joke.WHOAMI,
            "man ffmpeg" to Joke.MAN,
            "ping 8.8.8.8" to Joke.PING,
            "history" to Joke.HISTORY,
            "  Echo hi  " to Joke.ECHO,
            "reboot" to Joke.REBOOT,
            "nmap -sV 10.0.0.1" to Joke.NMAP,
            "grep -r meaning ." to Joke.GREP,
            "ssh me@host" to Joke.HELL,
            "su" to Joke.HELL,
            "sudo apt install ffmpeg" to Joke.ROOT,
            "sudo please" to Joke.PLEASE,
            "SUDO Please make it work" to Joke.PLEASE,
            "please sudo" to Joke.PLEASE,
            "please" to Joke.SHELL,
            "git log" to Joke.SHELL,
        )
        for ((typed, joke) in expected) assertEquals(typed, joke, CommandJokes.replyTo(typed))
        // After "ffmpeg" a word is part of a command, not a program.
        assertNull(CommandJokes.replyTo("ffmpeg -i cat.mp4 cat.gif"))
        assertEquals(Joke.TEAPOT, CommandJokes.replyTo("ffmpeg coffee"))
    }
}
