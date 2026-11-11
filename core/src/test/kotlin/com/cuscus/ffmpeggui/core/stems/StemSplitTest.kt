package com.cuscus.ffmpeggui.core.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StemSplitTest {

    @Test
    fun `a song is split by Spleeter alone, into the instruments`() {
        assertEquals(StemKind.INSTRUMENTS, StemSplit.SONG.kinds)
        assertFalse(StemSplit.SONG.usesBandit)
        assertTrue(StemSplit.SONG.usesSpleeter)
    }

    @Test
    fun `the scene is Bandit alone, the instruments of a video both`() {
        assertEquals(StemKind.SCENE, StemSplit.SCENE.kinds)
        assertTrue(StemSplit.SCENE.usesBandit)
        assertFalse(StemSplit.SCENE.usesSpleeter)
        assertEquals(listOf(StemKind.DIALOGUE, StemKind.EFFECTS) + StemKind.INSTRUMENTS, StemSplit.INSTRUMENTS.kinds)
        assertTrue(StemSplit.INSTRUMENTS.usesBandit && StemSplit.INSTRUMENTS.usesSpleeter)
    }

    @Test
    fun `a sound alone starts as a song, a video as its scene`() {
        assertEquals(StemSplit.SONG, StemSplit.firstFor(soundOnly = true))
        assertEquals(StemSplit.SCENE, StemSplit.firstFor(soundOnly = false))
    }
}
