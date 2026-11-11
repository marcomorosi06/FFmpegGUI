package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.stems.StemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenedSoundTest {

    @Test
    fun `the voice is heard alone only when the dialogue and the singing are both kept`() {
        assertEquals(listOf(StemKind.DIALOGUE, StemKind.SINGING), ListenedSound.voiceOf(StemKind.entries.toSet()))
        assertEquals(listOf(StemKind.DIALOGUE, StemKind.SINGING), ListenedSound.voiceOf(setOf(StemKind.SINGING, StemKind.DIALOGUE)))
        // A song's singing goes to Bandit's music: the dialogue alone could be silent.
        assertTrue(ListenedSound.voiceOf(StemKind.SCENE.toSet()).isEmpty())
        assertTrue(ListenedSound.voiceOf(setOf(StemKind.SINGING)).isEmpty())
        assertTrue(ListenedSound.voiceOf(emptySet()).isEmpty())
    }

    @Test
    fun `one file is read as before`() {
        assertEquals(
            listOf(
                "-y", "-i", "in.mp4",
                "-map", "0:a:0", "-vn", "-sn", "-dn",
                "-af", "aresample=16000:first_pts=0",
                "-ac", "1", "-c:a", "pcm_f32le", "-f", "f32le",
                "out.pcm",
            ),
            ListenedSound.command(listOf("in.mp4"), "out.pcm").arguments,
        )
    }

    @Test
    fun `a limit keeps only the start`() {
        val args = ListenedSound.command(listOf("in.mp4"), "out.pcm", limitMs = 90_500L).arguments
        assertEquals(listOf("-t", "90.500", "out.pcm"), args.takeLast(3))
    }

    @Test
    fun `the voice stems are mixed at their own levels, on the file's clock`() {
        val args = ListenedSound.command(listOf("a.dialogue.flac", "a.singing.flac"), "out.pcm").arguments
        assertEquals(listOf("-y", "-i", "a.dialogue.flac", "-i", "a.singing.flac"), args.take(5))
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertEquals("[0:a:0][1:a:0]amix=inputs=2:duration=longest:normalize=0,aresample=16000:first_pts=0[voice]", graph)
        assertEquals("[voice]", args[args.indexOf("-map") + 1])
        assertEquals(listOf("-ac", "1", "-c:a", "pcm_f32le", "-f", "f32le", "out.pcm"), args.takeLast(7))
    }
}
