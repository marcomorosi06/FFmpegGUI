package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SvgPointSnapTest {

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)

    private val none = DoubleArray(0)

    @Test
    fun `a point near another lands on it, both ways at once`() {
        val t = PointTargets(doubleArrayOf(50.0, 50.0, 90.0, 10.0), doubleArrayOf(48.0), none)
        val s = snapPoint(53.0, 47.0, t, 6.0)
        assertTrue(s.onPoint)
        near(50.0, s.x)
        near(50.0, s.y)
    }

    @Test
    fun `a point nearer a line than any point lands on the line, each way on its own`() {
        val t = PointTargets(none, doubleArrayOf(0.0, 100.0), doubleArrayOf(50.0))
        val across = snapPoint(97.0, 20.0, t, 5.0)
        near(100.0, across.x)
        near(20.0, across.y)
        assertEquals(listOf(100.0), across.xs)
        assertTrue(across.ys.isEmpty())
        val crossing = snapPoint(97.0, 52.0, t, 5.0)
        near(100.0, crossing.x)
        near(50.0, crossing.y)
    }

    @Test
    fun `nothing near leaves the point where it is`() {
        val t = PointTargets(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0), doubleArrayOf(0.0))
        val s = snapPoint(40.0, 40.0, t, 5.0)
        assertFalse(s.snapped)
        near(40.0, s.x)
        near(40.0, s.y)
    }

    @Test
    fun `a grid gives its lines and the middles of its squares`() {
        val (xs, ys) = gridSnapLines(25.0, 100.0, 50.0)
        assertEquals(listOf(0.0, 12.5, 25.0, 37.5, 50.0, 62.5, 75.0, 87.5, 100.0), xs.toList())
        assertEquals(listOf(0.0, 12.5, 25.0, 37.5, 50.0), ys.toList())
    }

    @Test
    fun `a point near a spoke lands on it, not behind its centre`() {
        // A spoke from (50, 50) to the right.
        val spoke = SnapRay(50.0, 50.0, 1.0, 0.0, whole = false)
        val t = PointTargets(none, none, none, listOf(spoke))
        val on = snapPoint(80.0, 53.0, t, 5.0)
        near(80.0, on.x)
        near(50.0, on.y)
        assertEquals(listOf(spoke), on.rays)
        val behind = snapPoint(20.0, 53.0, t, 5.0)
        assertFalse(behind.snapped)
    }

    @Test
    fun `a point on a line and near a spoke lands on their crossing`() {
        val d = 1 / sqrt(2.0)
        val diagonal = SnapRay(0.0, 0.0, d, d, whole = true)
        val t = PointTargets(none, doubleArrayOf(40.0), none, listOf(diagonal))
        val s = snapPoint(41.0, 43.0, t, 5.0)
        near(40.0, s.x)
        near(40.0, s.y)
        assertEquals(listOf(40.0), s.xs)
        assertEquals(listOf(diagonal), s.rays)
    }

    @Test
    fun `a turn of four parts snaps to its spokes and to the lines half way between them`() {
        val sym = Symmetry(SymmetryKind.RADIAL, count = 4, cx = 50.0, cy = 50.0)
        val rays = sym.snapRays(Affine.IDENTITY)
        assertEquals(8, rays.size)
        assertTrue(rays.none { it.whole })
        // The first spoke points straight up, as the editor draws it.
        near(0.0, rays[0].dx)
        near(-1.0, rays[0].dy)
        // The second is half way to the next spoke: a diagonal.
        near(1 / sqrt(2.0), rays[1].dx)
        near(-1 / sqrt(2.0), rays[1].dy)
    }

    @Test
    fun `a mirror's axis runs both ways`() {
        val sym = Symmetry(SymmetryKind.MIRROR_X, cx = 10.0, cy = 10.0)
        val rays = sym.snapRays(Affine.IDENTITY)
        assertEquals(1, rays.size)
        assertTrue(rays[0].whole)
    }

    @Test
    fun `the points of a drawing come in its pixels, the one being moved left out`() {
        val doc = SvgDocument.parse(
            """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
              <path id="p" d="M10 10L20 10L20 20" transform="translate(5 0)"/>
            </svg>""",
        )
        val key = doc.findById("p")!!.key
        val all = doc.pathPoints()
        assertEquals(listOf(15.0, 10.0, 25.0, 10.0, 25.0, 20.0), all.toList())
        val others = doc.pathPoints(key, 0, 1)
        assertEquals(listOf(15.0, 10.0, 25.0, 20.0), others.toList())
    }
}
