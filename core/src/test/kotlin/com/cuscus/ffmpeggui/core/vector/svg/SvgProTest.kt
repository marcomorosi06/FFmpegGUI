package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SvgProTest {

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)

    private fun rect(x: Double, y: Double, w: Double, h: Double) = SvgShape(
        SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(fill = SvgPaint.color(0xFF0000)), emptyMap(), "rect",
        linkedMapOf("x" to svgNumber(x), "y" to svgNumber(y), "width" to svgNumber(w), "height" to svgNumber(h)),
    )

    private val page = SvgDocument.blank(100.0, 100.0)

    @Test
    fun `a symmetry makes its copies from what is drawn, and they follow it`() {
        val (withSym, source) = page.withSymmetry(Symmetry(SymmetryKind.QUAD, cx = 50.0, cy = 50.0))
        val drawn = withSym.insertInto(source, rect(10.0, 10.0, 20.0, 20.0)).mirrored()
        val group = drawn.symmetryGroup()!!
        assertEquals(4, group.children.size)
        assertEquals(3, group.children.count { it.isSymmetryCopy })
        // The copy mirrored left to right lies at the right.
        val copy = group.children[1]
        val box = drawn.worldBounds(copy.key)!!
        near(70.0, box.left)
        near(90.0, box.right)
        // The copies cannot be picked: the source is under the finger on its side only.
        assertEquals(drawn.symmetrySource()!!.children.first().key, drawn.hit(20.0, 20.0))
        assertNull(drawn.hit(80.0, 20.0))
        // Moved, the source takes its copies with it; their keys stay.
        val key = drawn.symmetrySource()!!.children.first().key
        val moved = drawn.transformInWorld(key, Affine.translate(5.0, 0.0)).mirrored()
        val again = moved.symmetryGroup()!!.children[1]
        assertEquals(copy.key, again.key)
        near(65.0, moved.worldBounds(again.key)!!.left)
        // Written and read again, it is still a symmetry.
        val read = SvgDocument.parse(moved.write())
        assertEquals(SymmetryKind.QUAD, read.symmetry()!!.kind)
        // Stopped: kept as shapes, or gone.
        assertEquals(4, moved.withoutSymmetry(keepCopies = true).nodes.last().let { (it as SvgGroup).children.size })
        assertEquals(1, moved.withoutSymmetry(keepCopies = false).nodes.last().let { (it as SvgGroup).children.size })
        assertNull(moved.withoutSymmetry(true).symmetry())
    }

    @Test
    fun `turns and kaleidoscopes make as many parts as asked`() {
        val s = Symmetry(SymmetryKind.RADIAL, 6, 50.0, 50.0)
        assertEquals(5, s.transforms().size)
        assertEquals(6, s.axes().size)
        assertEquals(11, Symmetry(SymmetryKind.KALEIDO, 6, 0.0, 0.0).transforms().size)
        assertEquals(s, Symmetry.parse(s.write()))
    }

    @Test
    fun `repeated in a row, a grid and round a centre`() {
        val r = rect(0.0, 0.0, 10.0, 10.0)
        val doc = page.copy(nodes = listOf(r))
        val (row, rowKeys) = doc.repeatInRow(listOf(r.key), 4, 20.0, 0.0)
        assertEquals(4, rowKeys.size)
        near(60.0, row.worldBounds(rowKeys.last())!!.left)
        val (grid, gridKeys) = doc.repeatInGrid(listOf(r.key), 3, 2, 15.0, 15.0)
        assertEquals(6, gridKeys.size)
        near(30.0, grid.worldBounds(gridKeys.last())!!.left)
        near(15.0, grid.worldBounds(gridKeys.last())!!.top)
        val (round, roundKeys) = doc.repeatAround(listOf(r.key), 4, 50.0, 50.0)
        assertEquals(4, roundKeys.size)
        // A half turn about the centre brings 0..10 to 90..100.
        near(90.0, round.worldBounds(roundKeys[2])!!.left)
        val (mirrored, made) = doc.mirrorCopy(listOf(r.key), horizontal = true)
        assertEquals(1, made.size)
        assertEquals(2, mirrored.nodes.size)
    }

    @Test
    fun `a locked node cannot be picked`() {
        val r = rect(0.0, 0.0, 10.0, 10.0)
        val doc = page.copy(nodes = listOf(r))
        assertEquals(r.key, doc.hit(5.0, 5.0))
        val locked = doc.setLocked(r.key, true)
        assertTrue(locked.find(r.key)!!.isLocked)
        assertNull(locked.hit(5.0, 5.0))
        assertEquals(r.key, locked.setLocked(r.key, false).hit(5.0, 5.0))
    }

    @Test
    fun `paths reversed, smoothed, simplified, broken apart and joined`() {
        val tri = SvgPath.parse("M0 0 L10 0 L10 10 Z")
        val back = reversePath(tri)
        assertEquals(Seg.Move(10.0, 10.0), back.first())
        assertEquals(Seg.Close, back.last())
        assertEquals(Seg.Line(0.0, 0.0), back[back.size - 2])
        val smooth = smoothPath(tri)
        assertEquals(3, smooth.count { it is Seg.Cubic })
        // A line of many points on a straight run comes down to a few.
        val zigzag = SvgPath.parse("M0 0 " + (1..50).joinToString(" ") { "L${it * 2} ${if (it % 2 == 0) 0.0 else 0.1}" })
        assertTrue(simplifyPath(zigzag, 1.0).size < 10)
        val two = SvgShape(SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(), emptyMap(), "path", linkedMapOf("d" to "M0 0 L10 0 L10 10 Z M20 20 L30 20 L30 30 Z"))
        val doc = page.copy(nodes = listOf(two))
        val (apart, pieces) = doc.breakApart(two.key)!!
        assertEquals(2, pieces.size)
        assertNull(apart.find(two.key))
        val (joined, key) = apart.joinPaths(pieces)!!
        assertEquals(1, joined.nodes.size)
        assertEquals(2, subpaths((joined.find(key) as SvgShape).segments()).size)
        assertNull(doc.breakApart(rect(0.0, 0.0, 1.0, 1.0).key))
    }

    @Test
    fun `a grid to snap to, and the page to line up on`() {
        val t = SnapTargets(doubleArrayOf(3.0), doubleArrayOf()).withGrid(25.0, 100.0, 100.0)
        assertEquals(6, t.xs.size)
        assertEquals(5, t.ys.size)
        near(50.0, onGrid(47.0, 25.0))
        near(20.0, gridStep(5, 100.0, 200.0))
        val a = rect(10.0, 10.0, 10.0, 10.0)
        val b = rect(30.0, 30.0, 10.0, 10.0)
        val doc = page.copy(nodes = listOf(a, b))
        val right = doc.align(listOf(a.key, b.key), SvgAlign.RIGHT, toPage = true)
        near(100.0, right.worldBounds(a.key)!!.right)
        near(100.0, right.worldBounds(b.key)!!.right)
        assertNotNull(right)
    }
}
