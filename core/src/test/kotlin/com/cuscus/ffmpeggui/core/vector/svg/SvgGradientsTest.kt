package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgGradientsTest {

    private val text = """
        <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">
          <defs>
            <linearGradient id="sky" gradientUnits="userSpaceOnUse" x1="0" y1="10" x2="0" y2="90">
              <stop offset="0" stop-color="#0000ff"/><stop offset="1" stop-color="#ffffff"/>
            </linearGradient>
            <radialGradient id="swatch"><stop offset="0" stop-color="#00ff00"/></radialGradient>
          </defs>
          <rect id="a" x="0" y="0" width="50" height="50" style="fill:#ff0000"/>
          <rect id="b" x="50" y="0" width="50" height="50" style="fill:url(#sky)"/>
          <rect id="c" x="0" y="50" width="50" height="50" style="fill:url(#sky)"/>
        </svg>
    """.trimIndent()

    private fun SvgDocument.key(id: String): Int = allNodes().first { it.id == id }.key

    private fun SvgDocument.fillOf(id: String): SvgPaint = allNodes().first { it.id == id }.style.fill!!

    @Test
    fun `a colour becomes a gradient of the shape's own, laid along its angle`() {
        val doc = SvgDocument.parse(text)
        val spec = GradientSpec(angle = 90.0, stops = listOf(GradientStop(0.0, 0xFFFF0000L), GradientStop(1.0, 0x80000000L)))
        val painted = doc.paintWithGradient(doc.key("a"), stroke = false, spec)
        val fill = painted.fillOf("a")
        assertEquals(PaintKind.URL, fill.kind)
        assertTrue(fill.url.startsWith(APP_GRADIENT_PREFIX))
        val g = painted.gradients[fill.url]!!
        assertTrue(g.linear)
        assertFalse(g.userSpace)
        // Top to bottom across the box.
        assertEquals(listOf(0.5, 0.0, 0.5, 1.0), g.coords)
        // The second stop keeps its opacity.
        assertEquals(0x80000000L, g.stops[1].second and 0xFF000000L)
        // Written and read back, it is the same gradient.
        val again = SvgDocument.parse(painted.write())
        assertEquals(painted.gradientSpec(fill), again.gradientSpec(again.fillOf("a")))
    }

    @Test
    fun `changing the stops keeps where the gradient lies`() {
        val doc = SvgDocument.parse(text)
        // b and c share the file's gradient: b gets a copy of its own, with its place.
        val spec = doc.gradientSpec(doc.fillOf("b"))!!
        assertEquals(90.0, spec.angle, 1e-9)
        val recoloured = spec.copy(stops = spec.stops.map { GradientStop(it.offset, 0xFFFFA500L) })
        val painted = doc.paintWithGradient(doc.key("b"), stroke = false, recoloured)
        val id = painted.fillOf("b").url
        assertTrue(id != "sky")
        val g = painted.gradients[id]!!
        assertTrue(g.userSpace)
        assertEquals(listOf(0.0, 10.0, 0.0, 90.0), g.coords)
        // c still has the file's gradient, unchanged.
        assertEquals("sky", painted.fillOf("c").url)
        assertEquals(0xFF0000FFL, painted.gradients["sky"]!!.stops[0].second)
    }

    @Test
    fun `a gradient that is the shape's alone is changed in place`() {
        val doc = SvgDocument.parse(text)
        val first = doc.paintWithGradient(doc.key("a"), stroke = false, GradientSpec.between(0xFFFF0000L, 0xFF0000FFL))
        val id = first.fillOf("a").url
        val second = first.paintWithGradient(first.key("a"), stroke = false, GradientSpec(radial = true, stops = first.gradientSpec(first.fillOf("a"))!!.stops))
        assertEquals(id, second.fillOf("a").url)
        assertFalse(second.gradients[id]!!.linear)
        // One gradient of the app's: none was left behind.
        assertEquals(1, Regex("""id="ffg-""").findAll(second.write()).count())
    }

    @Test
    fun `the app's gradients nothing uses are left out, the file's own are kept`() {
        val doc = SvgDocument.parse(text)
        val painted = doc.paintWithGradient(doc.key("a"), stroke = false, GradientSpec.between(0xFFFF0000L, 0xFF0000FFL))
        val plain = painted.update(painted.key("a")) { it.withStyle(it.style.copy(fill = SvgPaint.color(0xFF00FF00L))) }
        val written = plain.write()
        assertFalse(written.contains("ffg-gradient"))
        // The file's gradients stay, even one nothing paints with (a swatch).
        assertTrue(written.contains("id=\"sky\""))
        assertTrue(written.contains("id=\"swatch\""))
    }

    @Test
    fun `an outline takes a gradient too, and a drawing without definitions gets them`() {
        val doc = SvgDocument.blank(200.0, 100.0, background = 0xFFFFFFFFL)
        val key = doc.nodes.first().key
        val painted = doc.paintWithGradient(key, stroke = true, GradientSpec.between(0xFF000000L, 0xFFFFFFFFL))
        val stroke = painted.nodes.first().style.stroke!!
        assertEquals(PaintKind.URL, stroke.kind)
        assertNotNull(painted.gradients[stroke.url])
        assertEquals("defs", painted.head.first().localName)
    }

    @Test
    fun `a duplicate shares a gradient until one of them is changed`() {
        val doc = SvgDocument.parse(text)
        val painted = doc.paintWithGradient(doc.key("a"), stroke = false, GradientSpec.between(0xFFFF0000L, 0xFF0000FFL))
        val (withCopy, copyKey) = painted.duplicate(painted.key("a"))!!
        val id = painted.fillOf("a").url
        val changed = withCopy.paintWithGradient(copyKey, stroke = false, GradientSpec.between(0xFF00FF00L, 0xFF0000FFL))
        val copyFill = changed.find(copyKey)!!.style.fill!!
        assertTrue(copyFill.url != id)
        // The original keeps its red.
        assertEquals(0xFFFF0000L, changed.gradients[id]!!.stops[0].second)
    }

    @Test
    fun `a paint that is no gradient has no spec, and reversing swaps the ends`() {
        val doc = SvgDocument.parse(text)
        assertNull(doc.gradientSpec(doc.fillOf("a")))
        assertNull(doc.gradientSpec(SvgPaint(PaintKind.URL, url = "missing")))
        val spec = GradientSpec(stops = listOf(GradientStop(0.0, 1L), GradientStop(0.25, 2L), GradientStop(1.0, 3L)))
        assertEquals(listOf(0.0 to 3L, 0.75 to 2L, 1.0 to 1L), spec.reversed().stops.map { it.offset to it.argb })
    }
}
