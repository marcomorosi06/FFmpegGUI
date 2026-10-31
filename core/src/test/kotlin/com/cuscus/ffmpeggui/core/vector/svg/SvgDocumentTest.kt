package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SvgDocumentTest {

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)

    @Test
    fun `xml reads attributes, entities, cdata and skips comments and doctype`() {
        val root = Xml.parse(
            """<?xml version="1.0"?>
            <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "x.dtd" [ <!ENTITY a "b"> ]>
            <!-- before -->
            <svg a='1 &amp; 2'><!-- in --><t>x &lt; y &#65;&#x42;</t><s><![CDATA[a<b]]></s></svg>""",
        )
        assertEquals("svg", root.name)
        assertEquals("1 & 2", root.attributes["a"])
        assertEquals("x < y AB", root.elements[0].textContent)
        assertEquals("a<b", root.elements[1].textContent)
    }

    @Test
    fun `xml written and read again is the same`() {
        val e = XmlElement(
            "svg", linkedMapOf("q" to "a\"b<c\nd"),
            listOf(XmlElement("g", linkedMapOf(), listOf(XmlElement("text", linkedMapOf(), listOf(XmlText("  a & b  ")))))),
        )
        assertEquals(e, Xml.parse(Xml.write(e)))
    }

    @Test(expected = XmlException::class)
    fun `broken xml is refused`() {
        Xml.parse("<svg><g></svg>")
    }

    @Test
    fun `relative paths, shorthand curves and close are absolute`() {
        val segs = SvgPath.parse("m10 10 h10 v10 l-10 0 z m5 5 c1 1 2 2 3 3 s4 4 5 5 q1 1 2 2 t2 2")
        assertEquals(Seg.Move(10.0, 10.0), segs[0])
        assertEquals(Seg.Line(20.0, 10.0), segs[1])
        assertEquals(Seg.Line(20.0, 20.0), segs[2])
        assertEquals(Seg.Line(10.0, 20.0), segs[3])
        assertEquals(Seg.Close, segs[4])
        // After a close the pen is back at the start: m5 5 is from 10,10.
        assertEquals(Seg.Move(15.0, 15.0), segs[5])
        val s = segs[7] as Seg.Cubic
        // The first control point mirrors the previous one (17,17) around 18,18.
        near(19.0, s.c1x); near(19.0, s.c1y)
        near(23.0, s.x); near(23.0, s.y)
        val t = segs[9] as Seg.Quad
        near(26.0, t.cx); near(26.0, t.cy)
        near(27.0, t.x); near(27.0, t.y)
    }

    @Test
    fun `an arc becomes curves that end where it ends and stay on the circle`() {
        val segs = SvgPath.parse("M0 0 A10 10 0 0 1 20 0")
        val last = segs.last() as Seg.Cubic
        near(20.0, last.x, 1e-6); near(0.0, last.y, 1e-6)
        val box = SvgPath.bounds(segs)!!
        near(0.0, box.left, 0.05); near(20.0, box.right, 0.05)
        // Clockwise from 0,0 to 20,0 in screen space goes up (negative y).
        near(-10.0, box.top, 0.05)
    }

    @Test
    fun `numbers glued together are read as SVG reads them`() {
        val segs = SvgPath.parse("M1.5.5L-2-3")
        assertEquals(Seg.Move(1.5, 0.5), segs[0])
        assertEquals(Seg.Line(-2.0, -3.0), segs[1])
    }

    @Test
    fun `transforms parse and compose in order`() {
        val t = Affine.parse("translate(10,20) scale(2)")
        near(12.0, t.mapX(1.0, 1.0)); near(22.0, t.mapY(1.0, 1.0))
        val r = Affine.parse("rotate(90 10 10)")
        near(10.0, r.mapX(20.0, 10.0)); near(20.0, r.mapY(20.0, 10.0))
        val back = t.inverse()!!.times(t)
        near(1.0, back.a); near(0.0, back.e)
        assertEquals(t, Affine.parse(t.toSvg()))
    }

    @Test
    fun `colours in every usual spelling`() {
        assertEquals(0xFFFF0000L, SvgPaint.parseColor("#f00"))
        assertEquals(0xFF112233L, SvgPaint.parseColor("#112233"))
        assertEquals(0xFF0A141EL, SvgPaint.parseColor("rgb(10, 20, 30)"))
        assertEquals(0xFFFFA500L, SvgPaint.parseColor("Orange"))
        assertNull(SvgPaint.parseColor("nonsense"))
        assertEquals(PaintKind.URL, SvgPaint.parse("url(#g1)")!!.kind)
        assertEquals("g1", SvgPaint.parse("url('#g1')")!!.url)
    }

    @Test
    fun `lengths in units`() {
        near(96.0, cssLength("1in")!!)
        near(96.0 / 25.4 * 10, cssLength("10mm")!!)
        near(50.0, cssLength("50%", 100.0)!!)
        assertNull(cssLength("px"))
    }

    private val sample = """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="200mm" viewBox="0 0 400 200">
      <title>t</title>
      <defs><linearGradient id="g"><stop offset="0" stop-color="red"/><stop offset="100%" style="stop-color:blue;stop-opacity:.5"/></linearGradient></defs>
      <style>.a { fill: #00ff00 } rect { stroke: black } #c { stroke-width: 3 }</style>
      <g id="layer" transform="translate(5 5)" opacity="0.5">
        <rect id="c" class="a" x="1" y="2" width="30" height="40" rx="4" fill="red" style="fill-opacity:.25"/>
        <circle cx="50" cy="50" r="10" fill="url(#g)"/>
        <text x="10" y="90" font-size="12pt">Hello &amp; bye</text>
      </g>
      <image x="0" y="0" width="10" height="10" xlink:href="data:image/png;base64,AAAA"/>
      <use xlink:href="#c" x="100"/>
    </svg>"""

    @Test
    fun `a document reads its size, groups, shapes, texts and styles`() {
        val doc = SvgDocument.parse(sample)
        near(200 * 96 / 25.4, doc.width, 1e-6)
        near(doc.width / 2, doc.height, 1e-6)
        assertEquals(400.0, doc.viewBox.width, 0.0)
        assertEquals(listOf("title", "defs", "style"), doc.head.map { it.localName })
        val group = doc.nodes[0] as SvgGroup
        assertEquals("layer", group.id)
        near(5.0, group.transform.e)
        near(0.5, group.style.opacity!!)
        val rect = group.children[0] as SvgShape
        // The class rule beats the attribute, the inline style is on top.
        assertEquals(0xFF00FF00L, rect.style.fill!!.argb)
        near(0.25, rect.style.fillOpacity!!)
        near(3.0, rect.style.strokeWidth!!)
        assertEquals(PaintKind.COLOR, rect.style.stroke!!.kind)
        assertEquals("a", rect.attributes["class"])
        assertTrue(rect.segments().size > 5)
        val circle = group.children[1] as SvgShape
        assertEquals("g", circle.style.fill!!.url)
        val text = group.children[2] as SvgText
        assertEquals("Hello & bye", text.text)
        near(16.0, text.style.fontSize!!)
        assertTrue(doc.nodes[1] is SvgImage)
        assertTrue(doc.nodes[2] is SvgOther)
        val g = doc.gradients["g"]!!
        assertEquals(2, g.stops.size)
        assertEquals(0x0000FFL, g.stops[1].second and 0xFFFFFFL)
        assertEquals(127L, g.stops[1].second ushr 24)
    }

    /** Every node's kind, id, transform, style and geometry, without the editor's keys. */
    private fun shape(nodes: List<SvgNode>): List<String> = nodes.map { n ->
        val inner = when (n) {
            is SvgGroup -> shape(n.children).toString()
            is SvgShape -> n.tag + n.geometry
            is SvgText -> "${n.x},${n.y}:${n.text}"
            is SvgImage -> "${n.x},${n.y},${n.width},${n.height}:${n.href}"
            is SvgOther -> n.element.toString()
        }
        "${n::class.simpleName}|${n.id}|${n.transform}|${n.style}|${n.attributes}|$inner"
    }

    @Test
    fun `a document written and read again is the same drawing`() {
        val doc = SvgDocument.parse(sample)
        val again = SvgDocument.parse(doc.write())
        near(doc.width, again.width, 0.01)
        assertEquals(doc.viewBox, again.viewBox)
        assertEquals(shape(doc.nodes), shape(again.nodes))
        assertTrue(doc.write().contains("xmlns:xlink"))
        // And written a second time, word for word.
        assertEquals(again.write(), SvgDocument.parse(again.write()).write())
    }

    @Test
    fun `an edited style wins over the file's rules once written`() {
        val doc = SvgDocument.parse(sample)
        val group = doc.nodes[0] as SvgGroup
        val rect = group.children[0] as SvgShape
        val edited = doc.copy(nodes = listOf(group.copy(children = listOf(rect.withStyle(rect.style.copy(fill = SvgPaint.color(0xFF123456L))))) ) + doc.nodes.drop(1))
        val back = (SvgDocument.parse(edited.write()).nodes[0] as SvgGroup).children[0] as SvgShape
        assertEquals(0xFF123456L, back.style.fill!!.argb)
    }

    @Test
    fun `multi line texts are tspans and come back as lines`() {
        val doc = SvgDocument.blank(100.0, 100.0).copy(
            nodes = listOf(SvgText(SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(), emptyMap(), 5.0, 20.0, "one\ntwo")),
        )
        val back = SvgDocument.parse(doc.write()).nodes[0] as SvgText
        assertEquals(listOf("one", "two"), back.lines)
    }

    @Test
    fun `a blank has its background rectangle, or nothing when transparent`() {
        assertTrue(SvgDocument.blank(10.0, 10.0, 0x00FFFFFFL).nodes.isEmpty())
        val b = SvgDocument.blank(10.0, 20.0, 0x80FF0000L)
        val r = b.nodes.single() as SvgShape
        assertEquals("20", r.geometry["height"])
        near(128 / 255.0, r.style.fillOpacity!!)
        assertNotNull(SvgDocument.parse(b.write()).nodes.single() as? SvgShape)
    }

    @Test
    fun `a duplicate gets new keys and no id`() {
        val doc = SvgDocument.parse(sample)
        val g = doc.nodes[0] as SvgGroup
        val d = g.duplicated() as SvgGroup
        assertNull(d.id)
        assertTrue(d.key != g.key && d.children[0].key != g.children[0].key)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `not an svg is refused`() {
        SvgDocument.parse("<html/>")
    }
}
