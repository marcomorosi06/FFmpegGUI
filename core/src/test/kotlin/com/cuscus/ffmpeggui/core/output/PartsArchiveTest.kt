package com.cuscus.ffmpeggui.core.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class PartsArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the parts are found in the counter's order, and nothing else`() {
        val folder = temp.newFolder("parts")
        listOf("part_010.mp4", "part_002.mp4", "part_1000.mp4", "part_02.mp4", "part_003.mkv", "other_001.mp4", "part_001.mp4")
            .forEach { File(folder, it).writeText(it) }

        val found = PartsArchive.parts(File(folder, "part_%03d.mp4").path).map { it.name }

        assertEquals(listOf("part_001.mp4", "part_002.mp4", "part_010.mp4", "part_1000.mp4"), found)
        assertEquals(listOf("f1.png", "f2.png", "f10.png"), run {
            listOf("f10.png", "f2.png", "f1.png").forEach { File(folder, it).writeText(it) }
            PartsArchive.parts(File(folder, "f%d.png").path).map { it.name }
        })
        assertTrue(PartsArchive.parts(File(folder, "plain.mp4").path).isEmpty())
    }

    @Test
    fun `packing keeps every byte and takes the parts away`() {
        val folder = temp.newFolder("parts")
        val bytes = ByteArray(300_000) { (it * 31 % 251).toByte() }
        val parts = listOf(File(folder, "part_000.wav").apply { writeBytes(bytes) }, File(folder, "part_001.wav").apply { writeText("short") })
        val archive = File(temp.root, "out/parts.zip")

        PartsArchive.pack(parts, archive)

        ZipFile(archive).use { zip ->
            assertEquals(listOf("part_000.wav", "part_001.wav"), zip.entries().toList().map { it.name })
            assertTrue(bytes.contentEquals(zip.getInputStream(zip.getEntry("part_000.wav")).readBytes()))
            assertEquals("short", zip.getInputStream(zip.getEntry("part_001.wav")).readBytes().decodeToString())
        }
        assertTrue(parts.none { it.exists() })
    }

    @Test
    fun `a stopped packing leaves no archive`() {
        val folder = temp.newFolder("parts")
        val parts = (0 until 3).map { File(folder, "part_00$it.mp4").apply { writeText("part $it") } }
        val archive = File(temp.root, "parts.zip")
        var checks = 0
        try {
            PartsArchive.pack(parts, archive) { if (++checks > 2) throw IllegalStateException("stop") }
            fail("Expected the stop")
        } catch (expected: IllegalStateException) {
            assertFalse(archive.exists())
        }
    }
}
