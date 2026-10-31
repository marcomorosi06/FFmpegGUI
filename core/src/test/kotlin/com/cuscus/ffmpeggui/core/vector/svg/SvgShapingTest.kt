package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SvgShapingTest {

    private fun near(expected: Double, actual: Double, eps: Double = 0.02) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)

    private val text = """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100">
      <rect id="a" x="10" y="10" width="20" height="20" rx="4" stroke="black" stroke-width="2"/>
      <circle id="c" cx="100" cy="50" r="10"/>
      <rect id="b" x="150" y="40" width="20" height="20"/>
    </svg>"""

    private fun SvgDocument.key(id: String) = findById(id)!!.key

    @Test
    fun `a square stretched one way becomes a rectangle, its corners and outline untouched`() {
        val doc = SvgDocument.parse(text)
        val a = doc.key("a")
        val r = doc.resizeOwn(a, 2.0, 1.0, 10.0, 10.0).find(a) as SvgShape
        assertEquals("40", r.geometry["width"])
        assertEquals("20", r.geometry["height"])
        assertEquals("10", r.geometry["x"])
        assertEquals("4", r.geometry["rx"])
        assertTrue(r.transform.isIdentity)
        near(2.0, r.style.strokeWidth!!)
    }

    @Test
    fun `a circle stretched one way becomes an ellipse`() {
        val doc = SvgDocument.parse(text)
        val c = doc.key("c")
        val e = doc.resizeOwn(c, 1.0, 2.0, 100.0, 40.0).find(c) as SvgShape
        assertEquals("ellipse", e.tag)
        assertEquals("10", e.geometry["rx"])
        assertEquals("20", e.geometry["ry"])
        assertEquals("60", e.geometry["cy"])
    }

    @Test
    fun `a path is sized point by point`() {
        val doc = SvgDocument.parse("""<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><path id="p" d="M0 0 L10 10"/></svg>""")
        val p = doc.key("p")
        val s = doc.resizeOwn(p, 3.0, 1.0, 0.0, 0.0).find(p) as SvgShape
        assertEquals("M0 0L30 10", s.geometry["d"])
    }

    @Test
    fun `a box moved near another lands on its edge, and says on which guide`() {
        val doc = SvgDocument.parse(text)
        val targets = doc.snapTargets(doc.key("a"))
        // The moved box's right edge (148.5) is 1.5 from b's left edge (150).
        val snap = snapBox(Box(128.5, 70.0, 148.5, 90.0), targets, threshold = 3.0)
        near(1.5, snap.dx)
        assertEquals(listOf(150.0), snap.xs)
        // The page's middle line is far: no vertical move.
        near(0.0, snap.dy)
        // Its own old place is no guide.
        assertTrue(targets.xs.none { it == 10.0 })
    }

    @Test
    fun `angles snap near multiples of fifteen degrees`() {
        near(45.0, snapAngle(43.0))
        near(37.0, snapAngle(37.0))
    }

    @Test
    fun `a connector holds two shapes and follows one when it moves`() {
        val doc = SvgDocument.parse(text).let { d ->
            d.insertAbove(null, SvgShape(SvgKeys.next(), "l", Affine.IDENTITY, SvgStyle(stroke = SvgPaint.BLACK), emptyMap(), "line", linkedMapOf("x1" to "0", "y1" to "0", "x2" to "1", "y2" to "1")))
        }
        val line = doc.key("l")
        val joined = doc.connect(line, true, doc.key("c")).connect(line, false, doc.key("b"))
        val l = joined.find(line) as SvgShape
        // From the circle's box edge (x 110) to b's left edge (150), at the middles' height.
        assertEquals("110", l.geometry["x1"])
        assertEquals("150", l.geometry["x2"])
        assertEquals("50", l.geometry["y2"])
        assertTrue(l.isConnector)
        // b moved down by 20: the line's end goes with it.
        val moved = joined.transformInWorld(joined.key("b"), Affine.translate(0.0, 20.0)).reroute()
        val after = moved.find(line) as SvgShape
        assertTrue((cssLength(after.geometry["y2"]!!) ?: 0.0) > 55.0)
        // An id was given to the shapes that had none? c had one: kept.
        assertEquals("c", joined.findById("c")!!.id)
        // Let go: the end stays where it is.
        val free = joined.connect(line, false, null)
        assertTrue(CONNECT_TO !in free.find(line)!!.attributes)
    }

    @Test
    fun `a stroke becomes a few smooth curves through its points`() {
        val pts = DoubleArray(200) { i -> if (i % 2 == 0) (i / 2).toDouble() else 10 * kotlin.math.sin(i / 2 / 10.0) }
        val segs = smoothStroke(pts, tolerance = 0.5)
        assertTrue(segs.first() is Seg.Move)
        assertTrue(segs.size in 4..40)
        val end = segs.last() as Seg.Cubic
        near(99.0, end.x, 1e-9)
        val box = SvgPath.bounds(segs)!!
        near(-10.0, box.top, 0.6)
    }

    private fun withLine(): SvgDocument = SvgDocument.parse(text).let { d ->
        d.insertAbove(null, SvgShape(SvgKeys.next(), "l", Affine.IDENTITY, SvgStyle(stroke = SvgPaint.BLACK, strokeWidth = 2.0), emptyMap(), "line", linkedMapOf("x1" to "0", "y1" to "0", "x2" to "1", "y2" to "1")))
    }

    @Test
    fun `an end put on a corner of a shape stays on that corner`() {
        val doc = withLine()
        val line = doc.key("l")
        // b's top right corner.
        val joined = doc.connect(line, false, doc.key("b"), 1.0 to 0.0)
        val e = joined.lineEnds(line)!!
        near(170.0, e[2]); near(40.0, e[3])
        // Turned about its middle, the corner goes with it.
        val turned = joined.transformInWorld(joined.key("b"), Affine.rotate(90.0, 160.0, 50.0)).reroute()
        val t = turned.lineEnds(line)!!
        near(170.0, t[2]); near(60.0, t[3])
        // Where a finger is on a shape, as fractions of its box.
        val (u, v) = joined.anchorOf(joined.key("b"), 160.0, 45.0)!!
        near(0.5, u); near(0.25, v)
    }

    @Test
    fun `a connector in square turns with an arrow is a path, and a line again when plain`() {
        val doc = withLine()
        val line = doc.key("l")
        val joined = doc.connect(line, true, doc.key("a")).connect(line, false, doc.key("b"))
        val elbow = joined.restyleConnector(line, ConnectorStyle.ELBOW, "end")
        val p = elbow.find(line) as SvgShape
        assertEquals("path", p.tag)
        val segs = SvgPath.parse(p.geometry["d"])
        // Out of a's right side, two square turns into b's left side, then the chevron.
        val turns = segs.takeWhile { it !is Seg.Move || it === segs.first() }
        assertTrue(turns.drop(1).all { it is Seg.Line })
        assertEquals(4, turns.size)
        assertTrue(segs.count { it is Seg.Move } == 2)
        assertTrue(p.isConnector)
        // b moved: the run is drawn again.
        val moved = elbow.transformInWorld(elbow.key("b"), Affine.translate(0.0, 30.0)).reroute()
        assertTrue((moved.find(line) as SvgShape).geometry["d"] != p.geometry["d"])
        // A free end of a path connector is dragged where it goes.
        val free = elbow.connect(line, false, null).moveLineEnd(line, false, 120.0, 90.0)
        val fe = free.lineEnds(line)!!
        near(120.0, fe[2]); near(90.0, fe[3])
        // Plain again: a line.
        assertEquals("line", (free.restyleConnector(line, ConnectorStyle.STRAIGHT, null).find(line) as SvgShape).tag)
        // Curved: one cubic between the shapes.
        val curved = joined.restyleConnector(line, ConnectorStyle.CURVE, null).find(line) as SvgShape
        assertTrue(SvgPath.parse(curved.geometry["d"])[1] is Seg.Cubic)
    }
}
