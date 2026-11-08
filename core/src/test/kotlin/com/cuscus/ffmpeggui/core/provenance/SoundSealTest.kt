package com.cuscus.ffmpeggui.core.provenance

import com.cuscus.ffmpeggui.core.model.AudioEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundSealTest {

    @Test
    fun `the sound is taken out as float, at its own rate and channels`() {
        assertEquals(
            listOf("-y", "-i", "a.mp4", "-map", "0:a:0", "-vn", "-sn", "-dn", "-c:a", "pcm_f32le", "-f", "wav", "s.wav"),
            SoundSeal.decode("a.mp4", "s.wav").arguments,
        )
    }

    @Test
    fun `the marked sound takes the first sound's place, the rest copied`() {
        val args = SoundSeal.remux("a.mp4", "m.wav", "b.mp4", SoundSeal.codecFor("mp4", AudioEncoding())!!).arguments
        assertEquals(
            listOf(
                "-y", "-i", "a.mp4", "-i", "m.wav",
                "-map", "0", "-map", "-0:a:0", "-map", "1:a:0",
                "-map_metadata", "0", "-map_chapters", "0",
                "-c", "copy", "-c:a", "aac", "-b:a", "192k",
                "-movflags", "+faststart", "b.mp4",
            ),
            args,
        )
        // An MKV has no fast start.
        assertTrue(!SoundSeal.remux("a.mkv", "m.wav", "b.mkv", listOf("-c:a", "aac")).arguments.contains("-movflags"))
    }

    @Test
    fun `each file gets back the codec it had, at the bitrate chosen`() {
        val audio = AudioEncoding(bitrateKbps = 256, bitDepth = 24)
        assertEquals(listOf("-c:a", "libmp3lame", "-b:a", "256k"), SoundSeal.codecFor("mp3", audio))
        assertEquals(listOf("-c:a", "aac", "-b:a", "256k"), SoundSeal.codecFor("m4a", audio))
        assertEquals(listOf("-c:a", "libopus", "-b:a", "256k"), SoundSeal.codecFor("opus", audio))
        assertEquals(listOf("-c:a", "flac", "-sample_fmt", "s32", "-bits_per_raw_sample", "24"), SoundSeal.codecFor("flac", audio))
        assertEquals(listOf("-c:a", "pcm_s24le"), SoundSeal.codecFor("WAV", audio))
        assertEquals(listOf("-c:a", "aac", "-b:a", "192k"), SoundSeal.codecFor("mkv", audio))
        // No sound to mark.
        assertNull(SoundSeal.codecFor("gif", audio))
        assertNull(SoundSeal.codecFor("png", audio))
    }
}
