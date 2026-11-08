package com.cuscus.ffmpeggui.core.project

import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectIntakeTest {

    private fun info(video: Boolean, audio: Boolean, still: Boolean = false, ms: Long = 5_000L) =
        MediaInfo(ms, "0:05", video, 1920, 1080, 1L, "1 B", hasAudio = audio, isStill = still)

    @Test
    fun `the kind of a shared file`() {
        assertEquals(IncomingKind.PICTURE, ProjectIntake.kindOf(info(video = true, audio = false, still = true, ms = 0L)))
        assertEquals(IncomingKind.VIDEO, ProjectIntake.kindOf(info(video = true, audio = true)))
        assertEquals(IncomingKind.SILENT_VIDEO, ProjectIntake.kindOf(info(video = true, audio = false)))
        // A track of silence (a recording made muted) is no sound.
        assertEquals(IncomingKind.SILENT_VIDEO, ProjectIntake.kindOf(info(video = true, audio = true), audible = false))
        assertEquals(IncomingKind.SOUND, ProjectIntake.kindOf(info(video = false, audio = true)))
        assertNull(ProjectIntake.kindOf(null))
        assertNull(ProjectIntake.kindOf(info(video = false, audio = false)))
        assertNull(ProjectIntake.kindOf(info(video = true, audio = true, ms = 0L)))
    }

    @Test
    fun `a video project takes everything, asking when there is a choice`() {
        assertEquals(listOf(IntoProject.SOUND), ProjectIntake.ways(IncomingKind.SOUND, OutputFormat.MP4))
        assertEquals(listOf(IntoProject.OVERLAY, IntoProject.CLIP), ProjectIntake.ways(IncomingKind.PICTURE, OutputFormat.MKV))
        assertEquals(listOf(IntoProject.CLIP, IntoProject.OVERLAY, IntoProject.SOUND), ProjectIntake.ways(IncomingKind.VIDEO, OutputFormat.MP4))
        assertEquals(listOf(IntoProject.CLIP, IntoProject.OVERLAY), ProjectIntake.ways(IncomingKind.SILENT_VIDEO, OutputFormat.AVI))
    }

    @Test
    fun `a GIF has no sound to take`() {
        assertEquals(emptyList<IntoProject>(), ProjectIntake.ways(IncomingKind.SOUND, OutputFormat.GIF))
        assertEquals(listOf(IntoProject.CLIP, IntoProject.OVERLAY), ProjectIntake.ways(IncomingKind.VIDEO, OutputFormat.GIF))
    }

    @Test
    fun `an audio project takes sound, and a picture as its cover when it can hold one`() {
        assertEquals(listOf(IntoProject.CLIP, IntoProject.SOUND), ProjectIntake.ways(IncomingKind.SOUND, OutputFormat.MP3))
        assertEquals(listOf(IntoProject.CLIP, IntoProject.SOUND), ProjectIntake.ways(IncomingKind.VIDEO, OutputFormat.FLAC))
        assertEquals(emptyList<IntoProject>(), ProjectIntake.ways(IncomingKind.SILENT_VIDEO, OutputFormat.MP3))
        assertEquals(listOf(IntoProject.COVER), ProjectIntake.ways(IncomingKind.PICTURE, OutputFormat.M4A))
        assertEquals(emptyList<IntoProject>(), ProjectIntake.ways(IncomingKind.PICTURE, OutputFormat.WAV))
    }

    @Test
    fun `a picture project takes pictures, and a frame of a video`() {
        assertEquals(listOf(IntoProject.OVERLAY), ProjectIntake.ways(IncomingKind.PICTURE, OutputFormat.PNG))
        assertEquals(listOf(IntoProject.FRAME_OVERLAY), ProjectIntake.ways(IncomingKind.VIDEO, OutputFormat.JPG))
        assertEquals(listOf(IntoProject.FRAME_OVERLAY), ProjectIntake.ways(IncomingKind.SILENT_VIDEO, OutputFormat.WEBP))
        assertEquals(emptyList<IntoProject>(), ProjectIntake.ways(IncomingKind.SOUND, OutputFormat.PNG))
    }

    @Test
    fun `projects with an editor of their own take nothing`() {
        for (format in listOf(OutputFormat.SVG, OutputFormat.ASCII, OutputFormat.ANSI, OutputFormat.TXT, OutputFormat.SRT, OutputFormat.CUSTOM)) {
            for (kind in IncomingKind.entries) {
                assertEquals("$kind into $format", emptyList<IntoProject>(), ProjectIntake.ways(kind, format))
            }
        }
    }
}
