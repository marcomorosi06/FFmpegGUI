package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SvgPathEditTest {

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)

    private fun shape(d: String) = SvgShape(SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(), emptyMap(), "path", linkedMapOf("d" to d))

    @Test
    fun `a closed path reads as its corners, the closing side included`() {
        val p = shape("M0 0L10 0L10 10L0 10Z").editPath()
        val sub = p.subs.single()
        assertEquals(4, sub.points.size)
        assertTrue(sub.closed)
        assertEquals(4, sub.pieces)
        assertEquals("M0 0L10 0L10 10L0 10L0 0Z", SvgPath.write(p.toSegments()))
    }

    @Test
    fun `a rectangle is edited as a path of four corners`() {
        val rect = SvgShape(SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(), emptyMap(), "rect", linkedMapOf("width" to "10", "height" to "5"))
        val edited = rect.withEditPath(rect.editPath().movePoint(0, 2, 20.0, 5.0))
        assertEquals("path", edited.tag)
        assertTrue(edited.geometry["d"]!!.contains("L20 5"))
    }

    @Test
    fun `a point added on a curve keeps its shape`() {
        val p = shape("M0 0C0 10 10 10 10 0").editPath()
        val before = flatten(p.toSegments(), 50).single().points
        val (split, at) = p.insertPoint(0, 0, 0.5)
        assertEquals(1, at)
        assertEquals(3, split.subs[0].points.size)
        // The middle of the curve: (5, 7.5).
        near(5.0, split.subs[0].points[1].x)
        near(7.5, split.subs[0].points[1].y)
        val box1 = SvgPath.bounds(p.toSegments())!!
        val box2 = SvgPath.bounds(split.toSegments())!!
        near(box1.bottom, box2.bottom, 1e-3)
        assertTrue(before.isNotEmpty())
    }

    @Test
    fun `a zig-zag and a wave are written as their teeth and kept beside the path`() {
        val base = shape("M0 0L100 0")
        val zig = base.editPath().setBend(0, 0, Bend.ZigZag(4, 10.0))
        val segs = zig.toSegments()
        // A move, four teeth, the end.
        assertEquals(6, segs.size)
        near(-10.0, (segs[1] as Seg.Line).y)
        near(10.0, (segs[2] as Seg.Line).y)
        val written = base.withEditPath(zig)
        assertNotNull(written.attributes[EDIT_PATH])
        // Read back as it was made.
        assertEquals(zig, written.editPath())
        // Changed elsewhere: read from its d instead.
        val elsewhere = written.copy(geometry = linkedMapOf("d" to "M0 0L50 50"))
        assertEquals(1, elsewhere.editPath().subs.single().pieces)
        val wave = base.editPath().setBend(0, 0, Bend.Wave(2, 10.0))
        val box = SvgPath.bounds(wave.toSegments())!!
        // Its tops reach the height asked.
        near(-10.0, box.top, 0.3)
        near(10.0, box.bottom, 0.3)
    }

    @Test
    fun `points are removed, strokes closed and opened, and found under a finger`() {
        val p = shape("M0 0L10 0L10 10").editPath()
        val removed = p.removePoint(0, 1)!!
        assertEquals(2, removed.subs[0].points.size)
        assertNull(removed.removePoint(0, 0))
        val closed = p.setClosed(0, true)
        assertEquals(3, closed.subs[0].pieces)
        assertEquals(2, closed.setClosed(0, false).subs[0].pieces)
        val onPoint = p.hit(10.5, 0.5, 2.0)
        assertTrue(onPoint is PathHit.OnPoint && onPoint.point == 1)
        val onPiece = p.hit(5.0, 0.5, 2.0)
        assertTrue(onPiece is PathHit.OnPiece && onPiece.piece == 0)
        near(0.5, (onPiece as PathHit.OnPiece).t, 0.05)
        assertNull(p.hit(50.0, 50.0, 2.0))
    }

    @Test
    fun `encoding round-trips every kind of piece`() {
        val p = EditPath(
            listOf(
                EditSub(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0)), listOf(Bend.Curve(1.0, 2.0, 3.0, 4.0), Bend.Wave(3, 2.5), Bend.ZigZag(5, 1.0)), true),
                EditSub(listOf(Pt(20.0, 20.0), Pt(30.0, 20.0)), listOf(Bend.Straight), false),
            ),
        )
        assertEquals(p, EditPath.decode(p.encode()))
        assertNull(EditPath.decode("nonsense"))
    }

    @Test
    fun `a bend amount comes back as it was set`() {
        val c = EditPath.bentCurve(Pt(0.0, 0.0), Pt(10.0, 0.0), 0.4)
        near(0.4, EditPath.bendOf(Pt(0.0, 0.0), Pt(10.0, 0.0), c))
    }

    @Test
    fun `points are appended to the last stroke`() {
        val p = EditPath(emptyList()).append(0.0, 0.0).append(5.0, 5.0).append(10.0, 0.0)
        assertEquals(3, p.subs.single().points.size)
        assertEquals(2, p.subs.single().pieces)
    }
}
