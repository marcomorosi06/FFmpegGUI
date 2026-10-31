package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SvgEditsTest {

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)

    // A 200 x 100 drawing of a 100 x 50 view box: everything drawn twice as big.
    private val text = """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100" viewBox="0 0 100 50">
      <rect id="bg" width="100" height="50" fill="white"/>
      <g id="g" transform="translate(10 10)">
        <rect id="a" x="0" y="0" width="20" height="10" fill="red"/>
        <circle id="ring" cx="50" cy="15" r="10" fill="none" stroke="black" stroke-width="2"/>
      </g>
      <path id="donut" d="M70 5 h20 v20 h-20 z M75 10 h10 v10 h-10 z" fill-rule="evenodd"/>
    </svg>"""

    private fun SvgDocument.key(id: String): Int = allNodes().first { it.id == id }.key

    @Test
    fun `world transforms include the view box and the groups`() {
        val doc = SvgDocument.parse(text)
        val a = doc.key("a")
        val t = doc.worldTransform(a)
        near(20.0, t.mapX(0.0, 0.0)); near(20.0, t.mapY(0.0, 0.0))
        val box = doc.worldBounds(a)!!
        near(20.0, box.left); near(60.0, box.right); near(40.0, box.bottom)
        assertEquals("g", doc.parentOf(a)!!.id)
        assertNull(doc.parentOf(doc.key("g")))
    }

    @Test
    fun `the top node under a point is found, holes and empty insides passed over`() {
        val doc = SvgDocument.parse(text)
        assertEquals(doc.key("a"), doc.hit(30.0, 30.0))
        // The middle of the ring has no fill: the background is there.
        assertEquals(doc.key("bg"), doc.hit(120.0, 50.0))
        // Its outline is.
        assertEquals(doc.key("ring"), doc.hit(100.0, 50.0, tolerance = 2.0))
        // The donut's hole shows the background, its body is the donut.
        assertEquals(doc.key("bg"), doc.hit(160.0, 30.0))
        assertEquals(doc.key("donut"), doc.hit(145.0, 15.0))
        // Outside the drawing.
        assertNull(doc.hit(500.0, 500.0))
        // A hidden node is passed over.
        assertEquals(doc.key("bg"), doc.setHidden(doc.key("a"), true).hit(30.0, 30.0))
    }

    @Test
    fun `a move in pixels moves the node by as much on screen, inside a group under a view box`() {
        val doc = SvgDocument.parse(text)
        val a = doc.key("a")
        val moved = doc.transformInWorld(a, Affine.translate(10.0, 4.0))
        val box = moved.worldBounds(a)!!
        near(30.0, box.left); near(24.0, box.top)
        // In its own units: half as far (the view box doubles it).
        near(5.0, moved.find(a)!!.transform.e); near(2.0, moved.find(a)!!.transform.f)
        // Sized twice about its centre: it stays centred.
        val c = doc.worldBounds(a)!!
        val sized = doc.transformInWorld(a, scaleAbout(2.0, 2.0, c.centerX, c.centerY)).worldBounds(a)!!
        near(c.centerX, sized.centerX); near(c.width * 2, sized.width)
    }

    @Test
    fun `turned, its corners turn with it`() {
        val doc = SvgDocument.parse(text)
        val a = doc.key("a")
        val c = doc.worldBounds(a)!!
        val turned = doc.transformInWorld(a, Affine.rotate(90.0, c.centerX, c.centerY))
        val k = turned.corners(a)!!
        // The top left corner went to the top right.
        near(c.centerX + c.height / 2, k[0], 1e-6); near(c.centerY - c.width / 2, k[1], 1e-6)
    }

    @Test
    fun `duplicate, remove and reorder`() {
        val doc = SvgDocument.parse(text)
        val a = doc.key("a")
        val (dup, copy) = doc.duplicate(a, 10.0, 10.0)!!
        val group = dup.find(doc.key("g")) as SvgGroup
        assertEquals(listOf(a, copy), group.children.take(2).map { it.key })
        near(doc.worldBounds(a)!!.left + 10, dup.worldBounds(copy)!!.left)
        val gone = dup.remove(a)
        assertNull(gone.find(a))
        assertNotNull(gone.find(copy))
        val top = doc.reorder(doc.key("bg"), SvgOrder.TOP)
        assertEquals("bg", top.nodes.last().id)
        assertFalse(top.canReorder(doc.key("bg"), SvgOrder.UP))
        assertTrue(top.canReorder(doc.key("bg"), SvgOrder.DOWN))
        val down = doc.reorder(doc.key("ring"), SvgOrder.DOWN)
        assertEquals(listOf("ring", "a"), (down.find(doc.key("g")) as SvgGroup).children.map { it.id })
        // Nothing changed: the same drawing.
        assertTrue(doc.update(-1) { it } === doc)
    }

    @Test
    fun `styles are inherited down the groups`() {
        val doc = SvgDocument.parse(
            """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><g fill="blue" stroke-width="3"><g opacity=".5"><rect id="r" width="5" height="5" stroke="red"/></g></g></svg>""",
        )
        val s = doc.resolvedStyle(doc.key("r"))
        assertEquals(0xFF0000FFL, s.fill.argb)
        near(3.0, s.strokeWidth)
        assertEquals(0xFFFF0000L, s.stroke.argb)
        // Opacity is the node's own.
        near(1.0, s.opacity)
    }

    @Test
    fun `a text's box follows its anchor and its lines`() {
        val t = SvgText(SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(), emptyMap(), 100.0, 50.0, "ab\ncd")
        val style = ResolvedStyle(fontSize = 10.0, textAnchor = "middle")
        val box = textBox(t, style, TextMeasure { line, s -> line.length * s.fontSize })!!
        near(90.0, box.left); near(110.0, box.right)
        near(42.0, box.top); near(64.0, box.bottom)
    }

    @Test
    fun `every node under a point, the top one first`() {
        val doc = SvgDocument.parse(text)
        assertEquals(listOf(doc.key("a"), doc.key("bg")), doc.hits(30.0, 30.0))
    }

    @Test
    fun `an element kept as it was is moved and hidden by the editor`() {
        val doc = SvgDocument.parse(
            """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="10" height="10"><use id="u" xlink:href="#x" transform="scale(2)" style="fill:red"/></svg>""",
        )
        val u = doc.key("u")
        val moved = doc.transformInWorld(u, Affine.translate(3.0, 0.0)).setHidden(u, true)
        val back = SvgDocument.parse(moved.write())
        val e = (back.nodes.single() as SvgOther).element
        assertEquals("matrix(2 0 0 2 3 0)", e.attributes["transform"])
        assertTrue(back.nodes.single().style.hidden)
        assertTrue(e.attributes["style"]!!.contains("fill:red"))
        val shown = SvgDocument.parse(back.setHidden(back.nodes.single().key, false).write())
        assertFalse(shown.nodes.single().style.hidden)
        // Untouched, written as it was.
        assertEquals("scale(2)", (SvgDocument.parse(doc.write()).nodes.single() as SvgOther).element.attributes["transform"])
    }

    @Test
    fun `line ends and the current colour pass down the groups`() {
        val doc = SvgDocument.parse(
            """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><g stroke-linecap="round" color="#00ff00"><path id="p" d="M0 0L5 5"/></g></svg>""",
        )
        val s = doc.resolvedStyle(doc.key("p"))
        assertEquals("round", s.other["stroke-linecap"])
        assertEquals(0xFF00FF00L, s.color)
    }

    @Test
    fun `a traced picture opens in the editor as one coloured shape per colour`() {
        val w = 40
        val h = 30
        val px = IntArray(w * h) { i -> if (i % w in 10..25 && i / w in 5..20) 0xFFFF0000.toInt() else 0xFFFFFFFF.toInt() }
        val image = com.cuscus.ffmpeggui.core.vector.VectorTracer.trace(
            com.cuscus.ffmpeggui.core.image.Raster(w, h, px),
            com.cuscus.ffmpeggui.core.vector.TraceSettings(colors = 2, vectorLayers = false),
        )
        val doc = SvgDocument.parse(image.toSvg(400, 300))
        assertEquals(400.0, doc.width, 0.0)
        assertEquals(image.layers.size, doc.nodes.size)
        assertTrue(doc.nodes.all { it is SvgShape && it.style.fill!!.kind == PaintKind.COLOR })
        // The red square is found where it is drawn, ten times larger.
        val red = doc.nodes.first { (it as SvgShape).style.fill!!.argb == 0xFFFF0000L }
        assertEquals(red.key, doc.hit(175.0, 125.0))
    }

    @Test
    fun `a layer dropped over another goes just above it, into its group if need be`() {
        val doc = SvgDocument.parse(text)
        // The donut, on top of the drawing, dropped just above a, inside the group.
        val moved = doc.moveAbove(doc.key("donut"), doc.key("a"))
        assertEquals(listOf("a", "donut", "ring"), (moved.find(doc.key("g")) as SvgGroup).children.map { it.id })
        assertEquals(listOf("bg", "g"), moved.nodes.map { it.id })
        // And below the background: the very bottom.
        val bottom = doc.moveBelow(doc.key("donut"), doc.key("bg"))
        assertEquals(listOf("donut", "bg", "g"), bottom.nodes.map { it.id })
        // A group never goes inside itself.
        assertTrue(doc.moveAbove(doc.key("g"), doc.key("a")) === doc)
    }
}
