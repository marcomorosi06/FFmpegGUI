package com.cuscus.ffmpeggui.core.eggs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeGameTest {

    @Test
    fun `a random run asks FFmpeg's life source for raw grey generations`() {
        val args = LifeGame.command(columns = 40, rows = 24, generations = 48, output = "/cache/life.gray", seed = 7, ratio = 0.3f).arguments
        val source = args[args.indexOf("-i") + 1]
        assertEquals("lavfi", args[args.indexOf("-f") + 1])
        assertTrue(source.startsWith("life=size=40x24:"))
        assertTrue(source.contains(":stitch=1"))
        assertTrue(source.contains(":random_seed=7"))
        assertTrue(source.contains(":ratio=0.3"))
        assertFalse(source.contains("filename"))
        assertEquals("48", args[args.indexOf("-frames:v") + 1])
        assertEquals("format=gray", args[args.indexOf("-vf") + 1])
        assertEquals("rawvideo", args[args.lastIndexOf("-f") + 1])
        assertEquals("/cache/life.gray", args.last())
    }

    @Test
    fun `a run that goes on reads its grid from a file, its path escaped`() {
        val source = LifeGame.command(8, 4, 2, "out", startFile = "/data/a:b/grid.txt").arguments.let { it[it.indexOf("-i") + 1] }
        assertTrue(source.contains(":filename=/data/a\\:b/grid.txt"))
        assertFalse(source.contains("random_seed"))
    }

    @Test
    fun `a negative seed is handed over as FFmpeg's unsigned one`() {
        val source = LifeGame.command(4, 4, 1, "out", seed = -1).arguments.let { it[it.indexOf("-i") + 1] }
        assertTrue(source.contains(":random_seed=4294967295"))
    }

    @Test
    fun `the grid is written a row per line, and frames are read back a byte per cell`() {
        val cells = booleanArrayOf(true, false, false, true, true, false)
        assertEquals("O  \nOO \n", LifeGame.gridText(cells, 3, 2))
        val bytes = byteArrayOf(-1, 0, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0)
        val frames = LifeGame.frames(bytes, 3, 2)
        assertEquals(2, frames.size)
        assertTrue(frames[0].contentEquals(booleanArrayOf(true, false, false, false, true, true)))
        assertEquals(0, LifeGame.alive(frames[1]))
    }

    @Test
    fun `a game that died or only blinks is settled, a lively one is not`() {
        val dead = BooleanArray(9)
        assertTrue(LifeGame.isSettled(listOf(dead)))
        val a = BooleanArray(9) { it in 3..5 }
        val b = BooleanArray(9) { it in setOf(1, 4, 7) }
        val blinking = List(LifeGame.SETTLED_RUN + 4) { if (it % 2 == 0) a else b }
        assertTrue(LifeGame.isSettled(blinking))
        val lively = List(LifeGame.SETTLED_RUN + 4) { n -> BooleanArray(9) { it == n % 9 } }
        assertFalse(LifeGame.isSettled(lively))
        assertFalse(LifeGame.isSettled(listOf(a, b)))
    }
}
