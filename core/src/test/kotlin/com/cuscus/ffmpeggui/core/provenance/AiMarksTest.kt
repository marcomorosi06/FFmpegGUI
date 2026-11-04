package com.cuscus.ffmpeggui.core.provenance

import com.cuscus.ffmpeggui.core.hdr.GainMapMetadata
import com.cuscus.ffmpeggui.core.hdr.UltraHdr
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.PlacedSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class AiMarksTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val erased = AiDisclosure(pictures = true)
    private val dubbed = AiDisclosure(speech = true)
    private val both = AiDisclosure(speech = true, pictures = true)

    private fun text(file: File) = String(file.readBytes(), Charsets.ISO_8859_1)

    private fun picture(format: String, name: String): File {
        val image = BufferedImage(8, 6, if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 6) for (x in 0 until 8) image.setRGB(x, y, (0xFF shl 24) or (x * 30 shl 16) or (y * 40 shl 8))
        return temp.newFile(name).also { assertTrue(ImageIO.write(image, format, it)) }
    }

    // --- What is said -----------------------------------------------------

    @Test
    fun `the words say what a model made, and nothing more`() {
        assertEquals("AI-generated voice; AI-generated inpainting", both.text)
        assertEquals("AI-generated voice", dubbed.text)
        assertTrue(AiDisclosure().isEmpty)
        // No model, no voice, no app, no date: the packet holds only the source type and the words.
        val xmp = Xmp.packet(both)
        for (word in listOf("Kokoro", "Piper", "LaMa", "MI-GAN", "FFmpeg", "xmptk", "CreatorTool", "Date")) assertFalse(word, xmp.contains(word))
    }

    @Test
    fun `an export says only what its format carries`() {
        val request = ConversionRequest(format = OutputFormat.MP3, inputFileName = "a.mp4", sounds = listOf(PlacedSound(aiVoice = "Kokoro (af_heart)")), aiEditedPictures = true)
        assertEquals(dubbed, request.aiDisclosure())
        assertEquals(erased, request.copy(format = OutputFormat.PNG).aiDisclosure())
        assertEquals(both, request.copy(format = OutputFormat.MP4).aiDisclosure())
        assertTrue(request.copy(format = OutputFormat.TXT).aiDisclosure().isEmpty)
        // A command typed by hand: everything, then what its file's kind can hold.
        val all = request.copy(format = OutputFormat.CUSTOM).aiSources()
        assertEquals(both, all)
        assertEquals(dubbed, AiSeal.disclosureFor("/out/output.flac", all))
        assertEquals(erased, AiSeal.disclosureFor("/out/output.WEBP", all))
        assertTrue(AiSeal.disclosureFor("/out/output.srt", all).isEmpty)
        assertEquals(all, AiSeal.disclosureFor("/out/output.mkv", all))
    }

    @Test
    fun `the packet holds IPTC's source type and the words`() {
        val xmp = Xmp.packet(erased)
        assertTrue(xmp.contains("Iptc4xmpExt:DigitalSourceType=\"${AiDisclosure.COMPOSITE}\""))
        assertTrue(xmp.contains(">AI-generated inpainting<"))
        assertEquals("A &amp; &lt;B&gt; &quot;", Xmp.escape("A & <B> \""))
        assertTrue(xmp.startsWith("<?xpacket begin=\"\uFEFF\""))
        assertTrue(Xmp.packet(dubbed, AiDisclosure.TRAINED).contains(AiDisclosure.TRAINED))
    }

    @Test
    fun `merged into another packet, the marks take the place of what was there`() {
        val other = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:Iptc4xmpExt="${Xmp.IPTC_EXT}" xmlns:dc="http://purl.org/dc/elements/1.1/"
              Iptc4xmpExt:DigitalSourceType="http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture" xmlns:hdrgm="h" hdrgm:Version="1.0">
              <dc:description><rdf:Alt><rdf:li xml:lang="x-default">A sunset</rdf:li></rdf:Alt></dc:description>
              <hdrgm:Directory/>
            </rdf:Description></rdf:RDF></x:xmpmeta>"""
        val merged = Xmp.mergeInto(other, erased)!!
        assertFalse(merged.contains("digitalCapture"))
        assertFalse(merged.contains("A sunset"))
        assertEquals(1, Regex("xmlns:Iptc4xmpExt=").findAll(merged).count())
        assertEquals(1, Regex("xmlns:dc=").findAll(merged).count())
        assertTrue(merged.contains("hdrgm:Version=\"1.0\""))
        assertTrue(merged.contains("<hdrgm:Directory/>"))
        assertTrue(AiMarksReader.changedWithAi(merged))
        // Parsed as XML: nothing doubled, nothing left open.
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(merged.byteInputStream())
        // A self-closing description gets a body.
        val closed = Xmp.mergeInto("""<rdf:RDF xmlns:rdf="r"><rdf:Description rdf:about=""/></rdf:RDF>""", erased)!!
        assertTrue(closed.contains("</rdf:Description></rdf:RDF>"))
        assertNull(Xmp.mergeInto("<x:xmpmeta/>", erased))
    }

    // --- Where it goes ----------------------------------------------------

    @Test
    fun `every format has its way, or none`() {
        assertEquals(AiSeal.Way.MP4, AiSeal.wayFor("/a/b.M4A"))
        assertEquals(AiSeal.Way.RIFF, AiSeal.wayFor("x.avi"))
        assertEquals(AiSeal.Way.TAGS, AiSeal.wayFor("x.opus"))
        assertEquals(AiSeal.Way.TIFF, AiSeal.wayFor("x.tif"))
        assertEquals(AiSeal.Way.NONE, AiSeal.wayFor("x.bmp"))
        assertEquals(AiSeal.Way.NONE, AiSeal.wayFor("x.ts"))
        assertEquals(AiSeal.Way.NONE, AiSeal.wayFor("noextension"))
        assertEquals(AiSeal.Marked.NO_PLACE, AiSeal.mark(temp.newFile("x.bmp"), erased))
        assertEquals(AiSeal.Marked.TAGS_ONLY, AiSeal.mark(temp.newFile("x.mkv"), erased))
        assertEquals(AiSeal.Marked.FAILED, AiSeal.mark(temp.newFile("x.mp4").apply { writeText("not an mp4") }, erased))
    }

    @Test
    fun `the stream copy keeps everything and writes the tags over`() {
        val args = AiSeal.remux("/in.mp4", "/out.mp4", dubbed).arguments
        assertEquals(listOf("-hide_banner", "-nostdin", "-y", "-i", "/in.mp4", "-map", "0", "-c", "copy", "-map_metadata", "0", "-movflags", "+use_metadata_tags"), args.take(13))
        assertTrue("AI_GENERATED=AI-generated voice" in args)
        assertTrue("DIGITAL_SOURCE_TYPE=${AiDisclosure.COMPOSITE}" in args)
        assertEquals("/out.mp4", args.last())
        assertFalse("-movflags" in AiSeal.remux("/in.mp3", "/out.mp3", dubbed).arguments)
    }

    // --- The writers ------------------------------------------------------

    private fun box(type: String, body: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(bigBytes(8 + body.size))
            write(type.toByteArray(Charsets.US_ASCII))
            write(body)
        }.toByteArray()

    @Test
    fun `an MP4 gets a uuid box last, an old XMP made free space`() {
        val old = box("uuid", Mp4Xmp.XMP_UUID + "<old/>".toByteArray())
        val bytes = box("ftyp", "isom".toByteArray()) + box("moov", ByteArray(8)) + old + box("mdat", ByteArray(5))
        val file = temp.newFile("a.mp4").apply { writeBytes(bytes) }
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, dubbed))
        val after = file.readBytes()
        assertEquals("free", String(after, bytes.size - 13 - old.size + 4, 4, Charsets.US_ASCII))
        assertEquals("uuid", String(after, bytes.size + 4, 4, Charsets.US_ASCII))
        assertEquals(after.size - bytes.size, bigInt(after, bytes.size))
        assertTrue(text(file).contains("AI-generated voice"))
        // No movie, a box past the end, a box that runs to the end: left alone.
        assertFalse(Mp4Xmp.write(temp.newFile("b.mp4").apply { writeBytes(box("ftyp", ByteArray(4))) }, "x"))
        assertFalse(Mp4Xmp.write(temp.newFile("c.mp4").apply { writeBytes(box("ftyp", ByteArray(4)) + bigBytes(0) + "moov".toByteArray()) }, "x"))
    }

    private fun wav(dataBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val fmt = ByteArray(16)
        val size = 4 + (8 + 16) + (8 + dataBytes + (dataBytes and 1))
        out.write("RIFF".toByteArray()); out.write(littleBytes(size)); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); out.write(littleBytes(16)); out.write(fmt)
        out.write("data".toByteArray()); out.write(littleBytes(dataBytes)); out.write(ByteArray(dataBytes + (dataBytes and 1)))
        return out.toByteArray()
    }

    @Test
    fun `a WAV gets a _PMX chunk, its size made right`() {
        val file = temp.newFile("dub.wav").apply { writeBytes(wav(3)) }
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, dubbed, AiDisclosure.TRAINED))
        val b = file.readBytes()
        assertEquals(b.size - 8, littleInt(b, 4))
        assertEquals(0, b.size and 1)
        assertTrue(text(file).contains("_PMX"))
        assertTrue(text(file).contains(AiDisclosure.TRAINED))
        // Again: the first becomes JUNK, the new one is last.
        assertTrue(RiffXmp.write(file, Xmp.packet(erased)))
        val again = text(file)
        assertTrue(again.contains("JUNK"))
        assertEquals(1, Regex("_PMX").findAll(again).count())
        assertEquals(file.length() - 8, littleInt(file.readBytes(), 4).toLong())
        // A RIFF whose size is wrong is left alone.
        val cut = temp.newFile("cut.wav").apply { writeBytes(wav(3).copyOf(30)) }
        assertFalse(RiffXmp.write(cut, "x"))
    }

    @Test
    fun `a PNG keeps its picture and says it once`() {
        val file = picture("png", "erased.png")
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, both))
        assertEquals(8, ImageIO.read(file).width)
        assertEquals(1, Regex("XML:com.adobe.xmp").findAll(text(file)).count())
        val xmp = AiMarksReader.xmp(file.inputStream())!!
        assertTrue(AiMarksReader.changedWithAi(xmp))
        assertTrue(xmp.contains("AI-generated voice; AI-generated inpainting"))
    }

    @Test
    fun `a JPEG gets its XMP after JFIF, an old one replaced`() {
        val file = picture("jpg", "a.jpg")
        assertTrue(JpegXmp.write(file, Xmp.packet(dubbed)))
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        assertEquals(6, ImageIO.read(file).height)
        val all = text(file)
        assertEquals(1, Regex("http://ns.adobe.com/xap/1.0/\u0000").findAll(all).count())
        assertFalse(all.contains("voice"))
        assertTrue(AiMarksReader.changedWithAi(AiMarksReader.xmp(file.inputStream())!!))
    }

    @Test
    fun `an Ultra HDR picture keeps its gain map, its index set right`() {
        val primary = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()
        val gainMap = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY), "jpg", it) }.toByteArray()
        val metadata = GainMapMetadata(minStops = 0f, maxStops = 2f)
        val assembled = UltraHdr.assemble(primary, gainMap, metadata, erased)
        assertTrue(AiMarksReader.changedWithAi(AiMarksReader.xmp(assembled.inputStream())!!))
        assertFalse(String(assembled, Charsets.ISO_8859_1).contains("FFmpeg"))
        assertNotNull(UltraHdr.read(assembled))

        // Marked again afterwards (a command copying it): the marks merged, the gain map still found.
        val file = temp.newFile("hdr.jpg").apply { writeBytes(UltraHdr.assemble(primary, gainMap, metadata)) }
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        val marked = file.readBytes()
        val xmp = AiMarksReader.xmp(marked.inputStream())!!
        assertTrue(xmp.contains("Item:Semantic=\"GainMap\""))
        assertTrue(AiMarksReader.changedWithAi(xmp))
        val read = UltraHdr.read(marked)!!
        assertEquals(2f, read.metadata.maxStops, 1e-5f)
        assertTrue(ImageIO.read(read.gainMapJpeg.inputStream()) != null)
        assertEquals(4, ImageIO.read(file).width)
    }

    @Test
    fun `a simple WebP is made extended for it`() {
        // VP8L: signature, then 14 bits of width less one, 14 of height less one, alpha, version.
        val bits = (9 - 1) or ((5 - 1) shl 14) or (1 shl 28)
        val vp8l = byteArrayOf(0x2F) + littleBytes(bits) + byteArrayOf(1, 2, 3)
        val body = ByteArrayOutputStream().apply {
            write("WEBP".toByteArray()); write("VP8L".toByteArray()); write(littleBytes(vp8l.size)); write(vp8l)
        }.toByteArray()
        val file = temp.newFile("a.webp").apply { writeBytes("RIFF".toByteArray() + littleBytes(body.size) + body) }
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        val b = file.readBytes()
        assertEquals(b.size - 8, littleInt(b, 4))
        assertEquals("VP8X", String(b, 12, 4, Charsets.US_ASCII))
        // Flags: XMP and alpha; the canvas 9 x 5.
        assertEquals(0x14, b[20].toInt() and 0xFF)
        assertEquals(8, b[24].toInt())
        assertEquals(4, b[27].toInt())
        assertEquals("VP8L", String(b, 30, 4, Charsets.US_ASCII))
        assertTrue(text(file).contains("XMP "))
        // Again: still one XMP.
        assertTrue(WebpXmp.write(file, Xmp.packet(erased)))
        assertEquals(1, Regex("XMP ").findAll(text(file)).count())
    }

    @Test
    fun `a GIF still reads, with its XMP before the trailer`() {
        val file = picture("gif", "a.gif")
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        assertEquals(8, ImageIO.read(file).width)
        val all = text(file)
        assertEquals(1, Regex("XMP DataXMP").findAll(all).count())
        assertTrue(all.startsWith("GIF89a"))
        assertEquals(0x3B, file.readBytes().last().toInt())
    }

    @Test
    fun `a TIFF gets tag 700 in a directory written again`() {
        val file = picture("tiff", "a.tiff")
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        assertEquals(8, ImageIO.read(file).width)
        // The first directory, read by hand: tag 700 once, its bytes the packet, the tags in order.
        val b = java.nio.ByteBuffer.wrap(file.readBytes())
        b.order(if (b.get(0) == 0x49.toByte()) java.nio.ByteOrder.LITTLE_ENDIAN else java.nio.ByteOrder.BIG_ENDIAN)
        val ifd = b.getInt(4)
        val tags = (0 until (b.getShort(ifd).toInt() and 0xFFFF)).map { e -> b.getShort(ifd + 2 + 12 * e).toInt() and 0xFFFF }
        assertEquals(tags.sorted(), tags)
        assertEquals(1, tags.count { it == 700 })
        val entry = ifd + 2 + 12 * tags.indexOf(700)
        val xmp = String(file.readBytes(), b.getInt(entry + 8), b.getInt(entry + 4), Charsets.UTF_8)
        assertEquals(Xmp.packet(erased), xmp)
    }

    @Test
    fun `an SVG gets its metadata first in the svg element`() {
        val file = temp.newFile("a.svg").apply { writeText("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" data-x=\"a>b\"><path d=\"M0 0\"/></svg>") }
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        assertEquals(AiSeal.Marked.WRITTEN, AiSeal.mark(file, erased))
        val svg = file.readText()
        assertEquals(1, Regex("<metadata").findAll(svg).count())
        assertTrue(svg.indexOf("<metadata") > svg.indexOf("a>b"))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
        assertFalse(SvgXmp.write(temp.newFile("b.svg").apply { writeText("<svg/>") }, "x"))
    }

    // --- Reading them back --------------------------------------------------

    @Test
    fun `another program's AI mark counts, a camera's does not`() {
        fun packet(type: String) = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF><rdf:Description Iptc4xmpExt:DigitalSourceType="$type"/></rdf:RDF></x:xmpmeta>"""
        assertTrue(AiMarksReader.changedWithAi(packet(AiDisclosure.TRAINED)))
        assertFalse(AiMarksReader.changedWithAi(packet("http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture")))
        assertFalse(AiMarksReader.changedWithAi("<x:xmpmeta/>"))
        val element = """<rdf:Description><Iptc4xmpExt:DigitalSourceType>${AiDisclosure.COMPOSITE}</Iptc4xmpExt:DigitalSourceType></rdf:Description>"""
        assertTrue(AiMarksReader.changedWithAi(element))
        // Not a picture, or none in it.
        assertNull(AiMarksReader.xmp("RIFF....".byteInputStream()))
        assertNull(AiMarksReader.xmp(picture("png", "plain.png").inputStream()))
        assertNull(AiMarksReader.xmp(ByteArray(0).inputStream()))
    }
}
