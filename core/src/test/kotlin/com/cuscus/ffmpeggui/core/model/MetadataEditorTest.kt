package com.cuscus.ffmpeggui.core.model

import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every tag of a file, edited, removed or added. */
class MetadataEditorTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private val phoneVideo = MetadataTags(
        title = "Holiday",
        fromSource = true,
        custom = listOf(
            CustomTag("location", "+45.0000+009.0000/"),
            CustomTag("com.android.model", "Pixel 9"),
            CustomTag("creation_time", "2026-07-01T10:00:00Z"),
        ),
        sourceKeys = setOf("location", "com.android.model", "creation_time"),
    )

    private fun args(format: OutputFormat, tags: MetadataTags, streamCopy: Boolean = false): List<String> {
        val request = ConversionRequest(
            format = format,
            inputFileName = "clip.mp4",
            sources = listOf(SourceInfo(width = 640, height = 360, hasAudio = true)),
            metadata = tags,
            encoding = EncodingSettings(streamCopy = streamCopy),
        )
        return builder.build(request, MediaPaths.aliasesFor(request)).arguments
    }

    private fun List<String>.tags(): List<String> = indices.filter { this[it] == "-metadata" }.map { this[it + 1] }

    @Test
    fun `every tag read is written back as it is edited`() {
        val edited = phoneVideo.copy(custom = phoneVideo.custom.map { if (it.key == "com.android.model") it.copy(value = "Mine") else it })
        val tags = args(OutputFormat.MP4, edited).tags()
        assertTrue(tags.toString(), "com.android.model=Mine" in tags)
        assertTrue(tags.toString(), "location=+45.0000+009.0000/" in tags)
    }

    @Test
    fun `a tag taken away is removed from the file`() {
        val tags = args(OutputFormat.MP4, phoneVideo.copy(custom = phoneVideo.custom.filter { it.key != "creation_time" })).tags()
        assertTrue(tags.toString(), "creation_time=" in tags)
    }

    @Test
    fun `removing what is sensitive takes away the place and the device, even unread`() {
        val clean = phoneVideo.withoutSensitive()
        assertEquals(listOf("creation_time"), clean.custom.map { it.key })
        val tags = args(OutputFormat.MP4, clean).tags()
        for (key in listOf("location=", "location-eng=", "com.android.model=", "com.android.manufacturer=", "com.apple.quicktime.location.ISO6709=")) {
            assertTrue("$key in $tags", key in tags)
        }
        assertTrue(tags.toString(), "creation_time=2026-07-01T10:00:00Z" in tags)
    }

    @Test
    fun `removing everything drops the source's tags and writes only new ones`() {
        val cleared = phoneVideo.cleared().copy(title = "New")
        val a = args(OutputFormat.MP4, cleared, streamCopy = true)
        val i = a.indexOf("-map_metadata")
        assertEquals("-1", a[i + 1])
        assertTrue(a.tags().toString(), "title=New" in a.tags())
        assertFalse(cleared.isEmpty)
    }

    @Test
    fun `an mp4 is told to keep tags of its own`() {
        val a = args(OutputFormat.MP4, MetadataTags(custom = listOf(CustomTag("project", "Sea"))))
        assertEquals("+use_metadata_tags", a[a.indexOf("-movflags") + 1])
        assertTrue("project=Sea" in a.tags())
        // Only the usual tags: nothing to tell.
        assertFalse(args(OutputFormat.MP4, MetadataTags(title = "T")).contains("-movflags"))
        // Other containers keep any tag.
        assertFalse(args(OutputFormat.MKV, MetadataTags(custom = listOf(CustomTag("project", "Sea")))).contains("-movflags"))
    }

    @Test
    fun `a custom tag cannot take the place of a field`() {
        val tags = MetadataTags(title = "Real", custom = listOf(CustomTag("title", "Fake"))).asMap()
        assertEquals("Real", tags["title"])
    }

    @Test
    fun `tags are grouped by what they say`() {
        assertEquals(TagKind.LOCATION, tagKind("GPSLatitude"))
        assertEquals(TagKind.LOCATION, tagKind("location-eng"))
        assertEquals(TagKind.LOCATION, tagKind("com.apple.quicktime.location.ISO6709"))
        assertEquals(TagKind.DEVICE, tagKind("Model"))
        assertEquals(TagKind.DEVICE, tagKind("com.android.manufacturer"))
        assertEquals(TagKind.DEVICE, tagKind("BodySerialNumber"))
        assertEquals(TagKind.DEVICE, tagKind("LensModel"))
        assertEquals(TagKind.DATE, tagKind("DateTimeOriginal"))
        assertEquals(TagKind.DATE, tagKind("creation_time"))
        assertEquals(TagKind.CAMERA, tagKind("ExposureTime"))
        assertEquals(TagKind.CAMERA, tagKind("FNumber"))
        assertEquals(TagKind.OTHER, tagKind("encoder"))
        assertTrue(EXIF_TAGS.none { it == "Orientation" || it == "ImageWidth" })
    }

    @Test
    fun `every part of the tags is saved with the project`() {
        val tags = phoneVideo.withoutSensitive().copy(stripAll = true)
        assertEquals(tags, ModelJson.metadataTags(ModelJson.write(tags)))
    }

    @Test
    fun `a dub made by a machine is said so in the file's tags`() {
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "clip.mp4",
            sources = listOf(SourceInfo(width = 640, height = 360, hasAudio = true)),
            sounds = listOf(
                PlacedSound(aiVoice = "Piper (it_IT-paola-medium)"),
                PlacedSound(aiVoice = "Piper (it_IT-paola-medium)"),
                PlacedSound(),
            ),
        )
        val a = builder.build(request, MediaPaths.aliasesFor(request)).arguments
        val tags = a.tags()
        assertTrue(tags.toString(), "AI_GENERATED=Contains AI-generated speech: Piper (it_IT-paola-medium)" in tags)
        assertTrue(tags.toString(), "DIGITAL_SOURCE_TYPE=http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia" in tags)
        assertEquals("+use_metadata_tags", a[a.indexOf("-movflags") + 1])
        // Without a dub, nothing is said.
        val plain = request.copy(sounds = listOf(PlacedSound()))
        assertFalse(builder.build(plain, MediaPaths.aliasesFor(plain)).arguments.tags().any { it.startsWith("AI_GENERATED=") })
    }

    @Test
    fun `the mp4 flags are given once, all of them`() {
        val merged = builder.oneMovFlags(listOf("-movflags", "+faststart", "-c:a", "aac", "-movflags", "+use_metadata_tags", "out.mp4"))
        assertEquals(listOf("-c:a", "aac", "-movflags", "+faststart+use_metadata_tags", "out.mp4"), merged)
        val one = listOf("-movflags", "+faststart", "out.mp4")
        assertEquals(one, builder.oneMovFlags(one))
    }
}
