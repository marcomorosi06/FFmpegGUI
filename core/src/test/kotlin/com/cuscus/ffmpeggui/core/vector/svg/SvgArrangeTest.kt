package com.cuscus.ffmpeggui.core.vector.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SvgArrangeTest {

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)

    private val text = """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100">
      <rect id="a" x="10" y="10" width="20" height="20"/>
      <g id="g" transform="translate(50 0)">
        <circle id="c" cx="50" cy="50" r="10" fill="red"/>
      </g>
      <rect id="b" x="160" y="60" width="20" height="30"/>
    </svg>"""

    private fun SvgDocument.key(id: String) = findById(id)!!.key

    private fun SvgDocument.box(id: String) = worldBounds(key(id))!!

    @Test
    fun `nodes line up on the box around them, one alone on the page`() {
        val doc = SvgDocument.parse(text)
        val keys = listOf(doc.key("a"), doc.key("c"), doc.key("b"))
        val left = doc.align(keys, SvgAlign.LEFT)
        listOf("a", "c", "b").forEach { near(10.0, left.box(it).left) }
        val bottom = doc.align(keys, SvgAlign.BOTTOM)
        listOf("a", "c", "b").forEach { near(90.0, bottom.box(it).bottom) }
        // One alone: in the middle of the page.
        val centred = doc.align(listOf(doc.key("a")), SvgAlign.CENTER)
        near(100.0, centred.box("a").centerX)
    }

    @Test
    fun `nodes are spaced out evenly, the first and the last staying`() {
        val doc = SvgDocument.parse(text)
        val d = doc.distribute(listOf(doc.key("b"), doc.key("a"), doc.key("c")), horizontal = true)
        near(10.0, d.box("a").left)
        near(180.0, d.box("b").right)
        // 170 across, 60 of boxes: two gaps of 55.
        near(85.0, d.box("c").left)
        near(55.0, d.box("c").left - d.box("a").right)
    }

    @Test
    fun `nodes are grouped where the top one was, looking as they did, and ungrouped again`() {
        val doc = SvgDocument.parse(text)
        val before = listOf("a", "c").associateWith { doc.box(it) }
        val (grouped, g) = doc.group(listOf(doc.key("c"), doc.key("a")))!!
        val group = grouped.find(g) as SvgGroup
        assertEquals(2, group.children.size)
        before.forEach { (id, box) -> assertEquals(box, grouped.box(id)) }
        // Where the top one (c) was: inside g, which now holds only the new group.
        assertEquals(2, grouped.nodes.size)
        assertEquals(grouped.key("g"), grouped.parentOf(g)?.key)
        val (apart, keys) = grouped.ungroup(g)!!
        assertEquals(2, keys.size)
        before.forEach { (id, box) -> assertEquals(box, apart.box(id)) }
        assertNull(apart.find(g))
    }

    @Test
    fun `a flip mirrors about the middle of the selection`() {
        val doc = SvgDocument.parse(text)
        val d = doc.flip(listOf(doc.key("a"), doc.key("b")), horizontal = true)
        // Across 10..180: a goes where b was, and b where a was.
        near(160.0, d.box("a").left)
        near(30.0, d.box("b").right)
    }

    @Test
    fun `a shape becomes a path that draws the same, and outlines take the place of shapes`() {
        val doc = SvgDocument.parse(text)
        val a = doc.key("a")
        val path = doc.toPath(a).find(a) as SvgShape
        assertEquals("path", path.tag)
        assertEquals(doc.box("a"), doc.toPath(a).box("a"))
        val c = doc.key("c")
        val outline = doc.worldOutline(c)
        assertNotNull(outline)
        assertEquals(Box(90.0, 40.0, 110.0, 60.0), SvgPath.bounds(outline!!)!!.let { b -> Box(Math.round(b.left).toDouble(), Math.round(b.top).toDouble(), Math.round(b.right).toDouble(), Math.round(b.bottom).toDouble()) })
        // Its outline put in place of a and c, in c's group and units.
        val (d, made) = doc.replaceWithOutlines(listOf(a, c), c, listOf(outline to SvgStyle(fill = SvgPaint.color(0xFF0000FFL))))
        assertEquals(1, made.size)
        assertNull(d.find(a))
        assertNull(d.find(c))
        assertEquals(doc.findById("g")!!.key, d.parentOf(made[0])?.key)
        val box = d.worldBounds(made[0])!!
        near(90.0, box.left, 0.01)
        near(110.0, box.right, 0.01)
    }

    @Test
    fun `several nodes snap to everything but themselves`() {
        val doc = SvgDocument.parse(text)
        val t = doc.snapTargets(listOf(doc.key("a"), doc.key("b")))
        assertTrue(10.0 !in t.xs.toList())
        assertTrue(160.0 !in t.xs.toList())
        assertTrue(90.0 in t.xs.toList())
    }
}
