package com.cuscus.ffmpeggui.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaInfoTest {

    @Test
    fun `display strings are derived`() {
        val info = MediaInfo.of(durationMs = 3_723_000, isVideo = true, width = 1920, height = 1080, fileSizeBytes = 5_242_880, hasAudio = false)
        assertEquals("01:02:03", info.durationFormatted)
        assertEquals("5.0 MB", info.fileSizeFormatted)
    }

    @Test
    fun `file sizes pick a sensible unit`() {
        assertEquals("2 KB", MediaInfo.formatFileSize(2_048))
        assertEquals("1.5 GB", MediaInfo.formatFileSize(1_610_612_736))
    }

    @Test
    fun `metadata keeps only filled tags`() {
        assertEquals(
            mapOf("title" to "T", "date" to "2026"),
            MetadataTags(title = "T", year = "2026", genre = " ").asMap(),
        )
    }

    @Test
    fun `tags read from the file are the whole truth, so blanks remove tags`() {
        val tags = MetadataTags(title = "T", copyright = "(c) me", fromSource = true)
        assertEquals(
            listOf("title" to "T", "artist" to "", "album" to "", "date" to "", "genre" to "", "comment" to "", "copyright" to "(c) me"),
            tags.asMap().toList(),
        )
        assertEquals(false, tags.isEmpty)
        assertEquals(true, MetadataTags().isEmpty)
    }
}
