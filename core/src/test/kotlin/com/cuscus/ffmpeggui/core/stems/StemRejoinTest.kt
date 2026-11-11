package com.cuscus.ffmpeggui.core.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StemRejoinTest {

    private val tracks = listOf(
        StemRejoin.Track("s", setOf(StemKind.SINGING)),
        StemRejoin.Track("p", setOf(StemKind.PIANO)),
        StemRejoin.Track("db", setOf(StemKind.DRUMS, StemKind.BASS)),
    )

    @Test
    fun `all of a clip's tracks bring the clip back`() {
        assertEquals(StemRejoin.Plan.Whole(listOf("s", "p", "db")), StemRejoin.plan(tracks, null))
        assertEquals(StemRejoin.Plan.Whole(listOf("s", "p", "db")), StemRejoin.plan(tracks, setOf("s", "p", "db")))
        // A single track left is the whole of it.
        val last = listOf(StemRejoin.Track("x", setOf(StemKind.OTHER)))
        assertEquals(StemRejoin.Plan.Whole(listOf("x")), StemRejoin.plan(last, setOf("x")))
    }

    @Test
    fun `some of them become one track holding their stems`() {
        val plan = StemRejoin.plan(tracks, setOf("p", "db"))
        assertEquals(StemRejoin.Plan.Merge(listOf("p", "db"), setOf(StemKind.PIANO, StemKind.DRUMS, StemKind.BASS)), plan)
    }

    @Test
    fun `nothing chosen, or one track of several, changes nothing`() {
        assertEquals(StemRejoin.Plan.Nothing, StemRejoin.plan(tracks, emptySet()))
        assertEquals(StemRejoin.Plan.Nothing, StemRejoin.plan(tracks, setOf("p")))
        assertEquals(StemRejoin.Plan.Nothing, StemRejoin.plan(tracks, setOf("gone")))
    }

    @Test
    fun `a track's name says what it holds, in StemKind's order`() {
        assertEquals("piano", StemRejoin.kindsPart(setOf(StemKind.PIANO)))
        assertEquals("piano-drums-bass", StemRejoin.kindsPart(setOf(StemKind.BASS, StemKind.PIANO, StemKind.DRUMS)))
        assertEquals(setOf(StemKind.PIANO, StemKind.DRUMS, StemKind.BASS), StemRejoin.kindsOf("piano-drums-bass"))
        assertEquals(setOf(StemKind.DIALOGUE), StemRejoin.kindsOf("dialogue"))
        assertNull(StemRejoin.kindsOf("piano-tuba"))
        assertNull(StemRejoin.kindsOf(""))
    }

    @Test
    fun `the stems are summed at their own levels into FLAC`() {
        val args = StemRejoin.command(listOf("/a.flac", "/b.flac"), "/out.part").arguments
        assertEquals(listOf("/a.flac", "/b.flac"), args.indices.filter { args[it] == "-i" }.map { args[it + 1] })
        assertTrue(args[args.indexOf("-filter_complex") + 1].contains("amix=inputs=2:duration=longest:normalize=0"))
        assertEquals("flac", args[args.indexOf("-f") + 1])
        assertEquals("/out.part", args.last())
    }
}
