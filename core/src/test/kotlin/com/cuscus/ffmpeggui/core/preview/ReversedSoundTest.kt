package com.cuscus.ffmpeggui.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversedSoundTest {

    @Test
    fun `a clip asks for its own part, to the end of the file when it has no end`() {
        assertEquals(ReversedSoundRequest("u", 1_000L, 4_000L), ReversedSound.requestFor("u", 1_000L, 4_000L, 9_000L))
        assertEquals(ReversedSoundRequest("u", 1_000L, 9_000L), ReversedSound.requestFor("u", 1_000L, null, 9_000L))
        // Its end not known yet, or nothing left.
        assertNull(ReversedSound.requestFor("u", 1_000L, null, 0L))
        assertNull(ReversedSound.requestFor("u", 4_000L, 4_010L, 9_000L))
    }

    @Test
    fun `backwards, the footage's end is the start of the reversed file`() {
        val request = ReversedSoundRequest("u", 1_000L, 4_000L)
        assertEquals(0L, ReversedSound.reversedMs(request, 4_000L))
        assertEquals(3_000L, ReversedSound.reversedMs(request, 1_000L))
        assertEquals(500L, ReversedSound.reversedMs(request, 3_500L))
        // Outside the part: held at its ends.
        assertEquals(0L, ReversedSound.reversedMs(request, 5_000L))
        assertEquals(3_000L, ReversedSound.reversedMs(request, 0L))
    }

    @Test
    fun `only the part's sound is read, and reversed`() {
        val command = ReversedSound.command(ReversedSoundRequest("u", 1_500L, 4_250L), "/in.mp4", "/out.m4a")
        val args = command.arguments
        // Cut before the input: only that part is decoded.
        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
        assertEquals("1.500", args[args.indexOf("-ss") + 1])
        assertEquals("2.750", args[args.indexOf("-t") + 1])
        assertEquals("/in.mp4", args[args.indexOf("-i") + 1])
        assertEquals("areverse", args[args.indexOf("-af") + 1])
        assertTrue("-vn" in args)
        assertEquals("/out.m4a", args.last())
    }

    @Test
    fun `the same part gets the same file, another part another`() {
        val a = ReversedSoundRequest("content://x/1", 0L, 3_000L)
        assertEquals(ReversedSound.fileName(a), ReversedSound.fileName(a.copy()))
        assertNotEquals(ReversedSound.fileName(a), ReversedSound.fileName(a.copy(endMs = 3_001L)))
        assertNotEquals(ReversedSound.fileName(a), ReversedSound.fileName(a.copy(sourceUri = "content://x/2")))
        assertTrue(ReversedSound.fileName(a).matches(Regex("reversed_[0-9a-f]{16}\\.m4a")))
    }
}

class ReversedPictureTest {

    @Test
    fun `a clip with neither picture nor sound asks for nothing, HDR only with a picture`() {
        assertNull(ReversedSound.requestFor("u", 0L, 3_000L, 0L, video = false, audio = false))
        val picture = ReversedSound.requestFor("u", 0L, 3_000L, 0L, video = true, audio = false, hdr = com.cuscus.ffmpeggui.core.model.VideoHdr.HLG)!!
        assertEquals(com.cuscus.ffmpeggui.core.model.VideoHdr.HLG, picture.hdr)
        val sound = ReversedSound.requestFor("u", 0L, 3_000L, 0L, video = false, audio = true, hdr = com.cuscus.ffmpeggui.core.model.VideoHdr.HLG)!!
        assertEquals(com.cuscus.ffmpeggui.core.model.VideoHdr.NONE, sound.hdr)
    }

    @Test
    fun `the picture is reversed in pieces, the last first, covering the part exactly`() {
        val request = ReversedSoundRequest("u", 1_000L, 8_500L, video = true)
        val pieces = ReversedSound.pieces(request)
        assertEquals(listOf(5_500L to 3_000L, 2_500L to 3_000L, 1_000L to 1_500L), pieces)
        assertEquals(request.lengthMs, pieces.sumOf { it.second })
        // A sliver at the start joins the piece before it.
        val sliver = ReversedSound.pieces(ReversedSoundRequest("u", 0L, 6_020L, video = true))
        assertEquals(listOf(3_020L to 3_000L, 0L to 3_020L), sliver)
    }

    @Test
    fun `a piece is cut, made small, reversed, and has no sound`() {
        val request = ReversedSoundRequest("u", 0L, 9_000L, video = true)
        val args = ReversedSound.pieceCommand(request, 6_000L, 3_000L, "/in.mp4", "/p0.mp4").arguments
        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
        assertEquals("6.000", args[args.indexOf("-ss") + 1])
        assertEquals("3.000", args[args.indexOf("-t") + 1])
        assertTrue("-an" in args)
        val filters = args[args.indexOf("-vf") + 1]
        assertTrue(filters, filters.contains("reverse") && filters.contains("fps=30") && filters.startsWith("scale="))
        // HDR footage is tone mapped first.
        val hdr = ReversedSound.pieceCommand(request.copy(hdr = com.cuscus.ffmpeggui.core.model.VideoHdr.HLG), 0L, 3_000L, "/in", "/out.mp4").arguments
        assertTrue(hdr[hdr.indexOf("-vf") + 1].startsWith("zscale="))
    }

    @Test
    fun `the pieces are joined as they are, and the sound laid under them`() {
        assertEquals("file '/a/p0.mp4'\nfile '/a/it'\\''s.mp4'\n", ReversedSound.joinList(listOf("/a/p0.mp4", "/a/it's.mp4")))
        val join = ReversedSound.joinCommand("/a/list.txt", "/a/joined.mp4").arguments
        assertEquals(listOf("concat", "0", "copy"), listOf(join[join.indexOf("-f") + 1], join[join.indexOf("-safe") + 1], join[join.indexOf("-c") + 1]))
        val lay = ReversedSound.layCommand("/a/joined.mp4", "/a/sound.m4a", "/a/out.mp4").arguments
        assertTrue("0:v:0" in lay && "1:a:0" in lay && "copy" in lay)
    }

    @Test
    fun `a part with a picture is an mp4, and differs from the sound alone`() {
        val sound = ReversedSoundRequest("u", 0L, 3_000L)
        val both = sound.copy(video = true)
        assertTrue(ReversedSound.fileName(both).endsWith(".mp4"))
        assertTrue(ReversedSound.fileName(sound).endsWith(".m4a"))
        assertNotEquals(ReversedSound.fileName(sound).substringBefore('.'), ReversedSound.fileName(both).substringBefore('.'))
    }
}
