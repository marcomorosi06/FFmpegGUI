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
        assertEquals(Joke.EDITOR, CommandJokes.replyTo("ffmpeg vim"))
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
        assertNull(CommandJokes.replyTo("rm -rf ./old"))
        assertNull(CommandJokes.replyTo("make me a"))
        assertNull(CommandJokes.replyTo("pingpong"))
    }
}
