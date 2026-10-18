package com.cuscus.ffmpeggui.core.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualCommandResolverTest {

    private val resolver = ManualCommandResolver()
    private val aliases = mapOf(
        "input.mp4" to "/cache/0_input.mp4",
        "overlay_0.png" to "/cache/1_logo.png",
        "subtitles.srt" to "/cache/2_subs.srt",
    )

    private fun resolve(line: String) = resolver.resolve(line, aliases, "/out/clip_42")

    @Test
    fun `drops the program name and swaps aliases`() {
        val result = resolve("ffmpeg -y -i input.mp4 -i overlay_0.png -c copy output.mkv") as ManualCommandResolver.Result.Resolved
        assertEquals(
            listOf("-y", "-i", "/cache/0_input.mp4", "-i", "/cache/1_logo.png", "-c", "copy", "/out/clip_42.mkv"),
            result.command.arguments,
        )
        assertEquals("/out/clip_42.mkv", result.outputPath)
        assertFalse(result.isPattern)
    }

    @Test
    fun `aliases inside filter strings are replaced`() {
        val result = resolve("""-i input.mp4 -vf "subtitles='subtitles.srt',scale=-2:720" output.mp4""") as ManualCommandResolver.Result.Resolved
        assertEquals("subtitles='/cache/2_subs.srt',scale=-2:720", result.command.arguments[3])
    }

    @Test
    fun `look-alike file names are left alone`() {
        val result = resolve("-i input.mp4 -i my_input.mp4 output.mp4") as ManualCommandResolver.Result.Resolved
        assertEquals("my_input.mp4", result.command.arguments[3])
    }

    @Test
    fun `numbered outputs are reported as patterns`() {
        val result = resolve("-i input.mp4 output_%03d.png") as ManualCommandResolver.Result.Resolved
        assertEquals("/out/clip_42_%03d.png", result.outputPath)
        assertTrue(result.isPattern)
    }

    @Test
    fun `empty and output-less commands are rejected`() {
        assertEquals(ManualCommandResolver.Result.Empty, resolve("   "))
        assertEquals(ManualCommandResolver.Result.Empty, resolve("ffmpeg"))
        assertEquals(ManualCommandResolver.Result.MissingOutput, resolve("ffmpeg -i input.mp4 -f null -"))
    }
}
