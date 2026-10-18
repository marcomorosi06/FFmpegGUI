package com.cuscus.ffmpeggui.core.hdr

import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.HdrArea
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.Rotation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class HdrTest {

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun `a gain map's values stand for stops between its ends, and its metadata is written back as it was`() {
        val m = GainMapMetadata(minStops = -1f, maxStops = 3f, gamma = 2f)
        assertEquals(-1f, m.stopsOf(0), 1e-5f)
        assertEquals(3f, m.stopsOf(255), 1e-5f)
        // v^(1/2): a quarter is half way.
        assertEquals(1f, m.stopsOf(0.25f), 1e-5f)
        assertEquals(m, GainMapMetadata.parse(m.encode()))
        assertNull(GainMapMetadata.parse("1 2 3"))
    }

    @Test
    fun `light parts get brighter, dark ones stay, and nothing laid over the picture does`() {
        val w = 40
        val h = 20
        // Left half dark grey, right half near white.
        val picture = IntArray(w * h) { i -> if (i % w < 20) grey(40) else grey(245) }
        // A "text" over the bottom right corner.
        val plain = picture.copyOf()
        for (y in 16 until 20) for (x in 30 until 40) picture[y * w + x] = grey(250)
        val builder = GainMapBuilder(w, h)
        builder.add(0, h, picture, plain)
        val map = builder.generated(0.5f)!!
        assertEquals(20, map.width)
        assertEquals(10, map.height)
        assertEquals(0f, map.metadata.minStops, 0f)
        assertTrue(map.metadata.maxStops > 2f && map.metadata.maxStops <= HdrIntensity.PEAK_STOPS)
        assertEquals(0, map.valueAt(2, 5))
        assertTrue(map.valueAt(15, 2) > 200)
        // Under the text: nothing.
        assertEquals(0, map.valueAt(17, 9))
    }

    @Test
    fun `past the middle the peak stays and more of the picture comes near it`() {
        // A ramp from black to white.
        val w = 64
        val picture = IntArray(w * 8) { i -> grey((i % w) * 255 / (w - 1)) }
        fun map(intensity: Float) = GainMapBuilder(w, 8).apply { add(0, 8, picture) }.generated(intensity)!!
        val middle = map(0.5f)
        val strong = map(1f)
        val weak = map(0.25f)
        assertEquals(HdrIntensity.PEAK_STOPS / 2, weak.metadata.maxStops, 0.3f)
        assertTrue(strong.metadata.maxStops >= middle.metadata.maxStops && strong.metadata.maxStops <= HdrIntensity.PEAK_STOPS)
        // A light grey: brighter at full intensity than at the middle.
        val x = 22
        assertTrue(strong.metadata.stopsOf(strong.valueAt(x, 2)) > middle.metadata.stopsOf(middle.valueAt(x, 2)) + 0.3f)
        // The photo's own, past as shot: its brightest the same, the rest nearer to it.
        val photo = GainMapBuilder(4, 2).apply { add(0, 2, IntArray(8) { grey(128) }) }
        val asShot = photo.original(floatArrayOf(2f, 0.5f), 1f)!!
        val more = photo.original(floatArrayOf(2f, 0.5f), 2f)!!
        assertEquals(asShot.metadata.maxStops, more.metadata.maxStops, 1e-4f)
        assertTrue(more.metadata.stopsOf(more.valueAt(1, 0)) > 0.9f)
    }

    @Test
    fun `the area says where, and a dark picture gives no gain map`() {
        val w = 8
        val h = 8
        val builder = GainMapBuilder(w, h)
        builder.add(0, h, IntArray(w * h) { grey(250) })
        val area = FloatArray(builder.mapWidth * builder.mapHeight) { i -> if (i % builder.mapWidth < 2) 1f else 0f }
        val map = builder.generated(1f, area)!!
        assertTrue(map.valueAt(0, 1) > 240)
        assertEquals(0, map.valueAt(3, 1))
        val dark = GainMapBuilder(w, h).apply { add(0, h, IntArray(w * h) { grey(20) }) }
        assertNull(dark.generated(3f))
    }

    @Test
    fun `a photo's own gain map is scaled, kept where the photo is and may darken`() {
        val builder = GainMapBuilder(4, 2)
        builder.add(0, 2, IntArray(8) { grey(128) })
        val stops = floatArrayOf(2f, -1f)
        val map = builder.original(stops, 0.5f)!!
        assertEquals(-0.5f, map.metadata.minStops, 1e-5f)
        assertEquals(1f, map.metadata.maxStops, 1e-5f)
        assertEquals(255, map.valueAt(0, 0))
        assertEquals(0, map.valueAt(1, 0))
        assertNull(builder.original(floatArrayOf(Float.NaN, Float.NaN), 1f))
    }

    @Test
    fun `a mask follows the picture's crop and turn, and its place in the frame`() {
        // Left half in, on a 10 x 10 picture.
        val mask = Raster(10, 10, IntArray(100) { i -> if (i % 10 < 5) grey(255) else grey(0) })
        val cropped = HdrAreas.aligned(mask, ImageEdit(), Effects(crop = CropRect(0f, 0f, 0.5f, 1f)), 5, 10)
        assertTrue(cropped.all { it > 0.99f })
        // Turned a quarter clockwise, the left half is the top half.
        val turned = HdrAreas.aligned(mask, ImageEdit(), Effects(rotation = Rotation.DEG_90), 10, 10)
        assertTrue(turned[1 * 10 + 5] > 0.99f)
        assertTrue(turned[8 * 10 + 5] < 0.01f)
        // In a 20 x 20 frame with a border of 5: nothing outside the picture.
        val framed = HdrAreas.aligned(mask, ImageEdit(), Effects(), 20, 20, floatArrayOf(5f, 5f, 10f, 10f), outside = Float.NaN)
        assertTrue(framed[0].isNaN())
        assertTrue(framed[10 * 20 + 6] > 0.99f)
        assertTrue(framed[10 * 20 + 13] < 0.01f)
        // The background is the rest.
        assertEquals(0f, HdrAreas.share(HdrArea.BACKGROUND, floatArrayOf(1f))!![0], 0f)
        assertNull(HdrAreas.share(HdrArea.EVERYWHERE, floatArrayOf(1f)))
    }

    /** A tiny JPG-shaped file: the segments the container looks at, then "picture data". */
    private fun jpeg(vararg extra: ByteArray, body: String = "data"): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        out.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0, 7) + "JFIF\u0000".toByteArray(Charsets.US_ASCII))
        extra.forEach { out.write(it) }
        out.write(byteArrayOf(0xFF.toByte(), 0xDB.toByte(), 0, 4, 1, 2))
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 3, 0))
        out.write(body.toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    @Test
    fun `an Ultra HDR file holds the picture, then the gain map its index and directory point to`() {
        val exif = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 8) + "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val primary = jpeg(exif, body = "the picture")
        val gainMap = jpeg(body = "the gain map")
        val metadata = GainMapMetadata(minStops = 0f, maxStops = 2.5f)
        val file = UltraHdr.assemble(primary, gainMap, metadata)

        // The picture still starts as it did: SOI, JFIF, EXIF.
        assertArrayEquals(primary.copyOfRange(0, 2 + 9 + 10), file.copyOfRange(0, 2 + 9 + 10))
        val text = String(file, Charsets.ISO_8859_1)
        assertTrue(text.contains("hdrgm:Version=\"1.0\""))
        assertTrue(text.contains("Item:Semantic=\"GainMap\""))
        // The second picture starts right after the first one's end.
        val end = text.indexOf("the picture") + "the picture".length + 2
        assertEquals(0xD8, file[end + 1].toInt() and 0xFF)

        val read = UltraHdr.read(file)!!
        assertEquals(metadata.maxStops, read.metadata.maxStops, 1e-5f)
        assertEquals(metadata.offsetSdr, read.metadata.offsetSdr, 1e-6f)
        assertTrue(String(read.gainMapJpeg, Charsets.ISO_8859_1).contains("the gain map"))
        assertEquals(file.size - end, read.gainMapJpeg.size)
        // A plain JPG has none.
        assertNull(UltraHdr.read(primary))
    }

    @Test
    fun `a gain map's XMP is read in its attribute and its element forms`() {
        val attributes = """<rdf:Description xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0" hdrgm:GainMapMax="3.0" hdrgm:HDRCapacityMax="3.0"/>"""
        assertEquals(3f, UltraHdr.parseGainMapXmp(attributes)!!.maxStops, 1e-5f)
        val elements = """<rdf:Description xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0">
            <hdrgm:GainMapMax><rdf:Seq><rdf:li>1.0</rdf:li><rdf:li>2.0</rdf:li><rdf:li>3.0</rdf:li></rdf:Seq></hdrgm:GainMapMax>
            <hdrgm:HDRCapacityMax>2.0</hdrgm:HDRCapacityMax></rdf:Description>"""
        val m = UltraHdr.parseGainMapXmp(elements)!!
        assertEquals(2f, m.maxStops, 1e-5f)
        assertEquals(0f, m.minStops, 0f)
        // An HDR base picture is not one this reads.
        assertNull(UltraHdr.parseGainMapXmp(attributes.replace("/>", " hdrgm:BaseRenditionIsHDR=\"True\"/>")))
    }
}
