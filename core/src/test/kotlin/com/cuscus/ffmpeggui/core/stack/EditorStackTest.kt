package com.cuscus.ffmpeggui.core.stack

import com.cuscus.ffmpeggui.core.model.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStackTest {

    @Test
    fun `a clip's sound is laid back under its picture where the clip starts`() {
        val args = SoundSwap.command("/v.mp4", "/s.flac", offsetMs = 12_500L, durationMs = 60_000L, output = "/o.mkv").arguments
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertEquals("[1:a:0]adelay=delays=12500:all=1,apad[s]", graph)
        // The picture copied, the first one only; the sound encoded losslessly.
        assertEquals("0:v:0", args[args.indexOf("-map") + 1])
        assertTrue(args.containsAll(listOf("-c:v", "copy", "-c:a", "flac")))
        // As long as the file was: the clip's times in it still hold.
        assertEquals("60.000", args[args.indexOf("-t") + 1])
        assertFalse(args.contains("-shortest"))
        assertEquals("/o.mkv", args.last())
    }

    @Test
    fun `a file of unknown length ends with its picture`() {
        val args = SoundSwap.command("/v.mp4", "/s.flac", offsetMs = -5L, durationMs = null, output = "/o.mkv").arguments
        assertTrue(args.contains("-shortest"))
        assertFalse(args.contains("-t"))
        // Never before the start.
        assertTrue(args[args.indexOf("-filter_complex") + 1].contains("delays=0:"))
    }

    @Test
    fun `each kind edits in its own format and is kept by its name`() {
        assertEquals(OutputFormat.MP4, NestedKind.CLIP_VIDEO.format)
        assertEquals(OutputFormat.FLAC, NestedKind.CLIP_SOUND.format)
        // A picture keeps its see-through edges; a GIF stays a GIF; every sound is lossless.
        assertEquals(OutputFormat.PNG, NestedKind.OVERLAY_PICTURE.format)
        assertEquals(OutputFormat.PNG, NestedKind.CLIP_PHOTO.format)
        assertEquals(OutputFormat.GIF, NestedKind.OVERLAY_GIF.format)
        assertEquals(OutputFormat.MP4, NestedKind.OVERLAY_VIDEO.format)
        assertEquals(OutputFormat.FLAC, NestedKind.OVERLAY_SOUND.format)
        assertEquals(OutputFormat.FLAC, NestedKind.SOUND.format)
        NestedKind.entries.forEach { assertEquals(it, NestedKind.named(it.name)) }
        assertNull(NestedKind.named("SOMETHING_NEWER"))
        assertNull(NestedKind.named(null))
    }

    @Test
    fun `only the sounds of clips and of videos laid over are laid back under their picture`() {
        val swapping = NestedKind.entries.filter { it.swapsSound }.toSet()
        assertEquals(setOf(NestedKind.CLIP_SOUND, NestedKind.OVERLAY_SOUND), swapping)
        swapping.forEach { assertEquals(NestedEditor.AUDIO, it.editor) }
    }

    @Test
    fun `a part opens in the editors made for what it is`() {
        assertEquals(listOf(NestedKind.OVERLAY_PICTURE), NestedKind.forOverlay(isVideo = false, isGif = false, hasAudio = false))
        assertEquals(listOf(NestedKind.OVERLAY_GIF), NestedKind.forOverlay(isVideo = true, isGif = true, hasAudio = true))
        assertEquals(listOf(NestedKind.OVERLAY_VIDEO, NestedKind.OVERLAY_SOUND), NestedKind.forOverlay(isVideo = true, isGif = false, hasAudio = true))
        assertEquals(listOf(NestedKind.OVERLAY_VIDEO), NestedKind.forOverlay(isVideo = true, isGif = false, hasAudio = false))
        assertEquals(listOf(NestedKind.CLIP_PHOTO), NestedKind.forClip(NestedEditor.VIDEO, still = true))
        assertEquals(listOf(NestedKind.CLIP_SOUND, NestedKind.CLIP_VIDEO), NestedKind.forClip(NestedEditor.VIDEO, still = false))
        assertEquals(listOf(NestedKind.CLIP_SOUND), NestedKind.forClip(NestedEditor.AUDIO, still = false))
        assertEquals(listOf(NestedKind.CLIP_VIDEO), NestedKind.forClip(NestedEditor.GIF, still = false))
        assertEquals(listOf(NestedKind.CLIP_PHOTO), NestedKind.forClip(NestedEditor.PHOTO, still = false))
        // Each kind's part is where it is looked for.
        assertEquals(NestedPart.SOUND, NestedKind.SOUND.part)
        NestedKind.entries.filter { it.name.startsWith("OVERLAY_") }.forEach { assertEquals(NestedPart.OVERLAY, it.part) }
        NestedKind.entries.filter { it.name.startsWith("CLIP_") }.forEach { assertEquals(NestedPart.CLIP, it.part) }
    }
}
