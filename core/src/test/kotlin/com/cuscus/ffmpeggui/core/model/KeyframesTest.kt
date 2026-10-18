package com.cuscus.ffmpeggui.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.cuscus.ffmpeggui.core.testing.FfExpr

class KeyframesTest {

    private val path = Keyframes(
        listOf(
            Keyframe(1_000, mapOf(KeyProperty.X to 0f, KeyProperty.OPACITY to 1f), KeyEasing.LINEAR),
            Keyframe(3_000, mapOf(KeyProperty.X to 1f), KeyEasing.SMOOTH),
            Keyframe(5_000, mapOf(KeyProperty.X to 0.5f, KeyProperty.OPACITY to 0f)),
        ),
    )

    @Test
    fun `values hold before the first and after the last keyframe, and move between them`() {
        assertEquals(0f, path.valueAt(KeyProperty.X, 0L, 9f), 1e-6f)
        assertEquals(0.5f, path.valueAt(KeyProperty.X, 2_000L, 9f), 1e-6f)
        // Smooth from 1 to 0.5: halfway is halfway, a quarter in is less than a quarter of the way.
        assertEquals(0.75f, path.valueAt(KeyProperty.X, 4_000L, 9f), 1e-6f)
        assertEquals(1f - 0.5f * 0.15625f, path.valueAt(KeyProperty.X, 3_500L, 9f), 1e-6f)
        assertEquals(0.5f, path.valueAt(KeyProperty.X, 9_000L, 9f), 1e-6f)
        // Only the keyframes that hold a property count for it.
        assertEquals(0.5f, path.valueAt(KeyProperty.OPACITY, 3_000L, 9f), 1e-6f)
        // None holds it: the thing's own value.
        assertEquals(9f, path.valueAt(KeyProperty.SIZE, 3_000L, 9f), 1e-6f)
        assertTrue(path.animates(KeyProperty.X))
        assertFalse(path.animates(KeyProperty.SIZE))
    }

    @Test
    fun `the FFmpeg expression follows the same curve`() {
        for (property in listOf(KeyProperty.X, KeyProperty.OPACITY, KeyProperty.SIZE)) {
            val expression = path.expression(property, 0.25f, "(t-2)", decimals = 6)
            for (ms in 0..9_000 step 125) {
                val t = ms / 1000.0 + 2.0
                assertEquals("$property at $ms: $expression", path.valueAt(property, ms.toLong(), 0.25f).toDouble(), FfExpr(expression, t).value(), 1e-4)
            }
        }
        assertEquals("0.2500", path.expression(KeyProperty.SIZE, 0.25f, "t"))
    }

    @Test
    fun `keyframes are set, merged, moved and removed`() {
        val one = Keyframes().set(2_000, mapOf(KeyProperty.X to 0.2f))
        val merged = one.set(2_020, mapOf(KeyProperty.Y to 0.7f))
        assertEquals(1, merged.frames.size)
        assertEquals(mapOf(KeyProperty.X to 0.2f, KeyProperty.Y to 0.7f), merged.frames[0].values)
        val two = merged.set(500, mapOf(KeyProperty.X to 0.9f))
        assertEquals(listOf(500L, 2_000L), two.frames.map { it.atMs })
        assertEquals(500L, two.previous(2_000)?.atMs)
        assertEquals(2_000L, two.next(500)?.atMs)
        assertNull(two.next(2_000))
        assertEquals(listOf(2_000L), two.remove(510).frames.map { it.atMs })
        assertEquals(listOf(-500L, 1_000L), two.shifted(-1_000).frames.map { it.atMs })
        assertEquals(KeyEasing.LINEAR, two.withEasing(2_000, KeyEasing.LINEAR).at(2_000)?.easing)
    }

    @Test
    fun `every easing's FFmpeg expression follows its curve`() {
        val drawn = EaseCurve(listOf(EasePoint(0.3f, 0.8f), EasePoint(0.6f, 1.2f)))
        for (easing in KeyEasing.entries) {
            val keys = Keyframes(
                listOf(
                    Keyframe(500, mapOf(KeyProperty.X to 0.2f), easing, curve = drawn),
                    Keyframe(2_500, mapOf(KeyProperty.X to 0.9f)),
                ),
            )
            // A long clock (as a speed curve writes it) is kept in a variable.
            for (time in listOf("t", "(0.0000+(t*1.000000)+0.0000*1.000000)")) {
                val expression = keys.expression(KeyProperty.X, 0f, time, decimals = 6)
                for (ms in 0..3_000 step 50) {
                    val expected = keys.valueAt(KeyProperty.X, ms.toLong(), 0f).toDouble()
                    assertEquals("$easing at $ms: $expression", expected, FfExpr(expression, ms / 1000.0).value(), 1e-4)
                }
            }
        }
    }

    @Test
    fun `easings start at the keyframe's value and end at the next one's`() {
        for (easing in KeyEasing.entries) {
            assertEquals("$easing", 0.0, Keyframes.ease(easing, 0.0, EaseCurve()), 1e-3)
            assertEquals("$easing", 1.0, Keyframes.ease(easing, 1.0, EaseCurve()), 1e-3)
        }
        assertEquals(0.0, Keyframes.ease(KeyEasing.HOLD, 0.99), 1e-9)
        assertTrue(Keyframes.ease(KeyEasing.EASE_IN, 0.5) < 0.5)
        assertTrue(Keyframes.ease(KeyEasing.EASE_OUT, 0.5) > 0.5)
        assertTrue((0..100).any { Keyframes.ease(KeyEasing.BACK, it / 100.0) > 1.0 })
    }

    @Test
    fun `a drawn curve goes through its nodes and keeps its ends`() {
        val curve = EaseCurve(listOf(EasePoint(0.5f, 0.2f)))
        assertEquals(listOf(0f, 0.5f, 1f), curve.nodes.map { it.x })
        assertEquals(0.2, curve.at(0.5), 1e-6)
        val added = curve.withNode(0.25f)
        assertEquals(4, added.nodes.size)
        assertEquals(curve.at(0.25), added.at(0.25), 1e-6)
        val moved = added.movedNode(2, 0.9f, 2f)
        // Not past its neighbour, not past the highest value a node takes.
        assertTrue(moved.nodes[2].x < moved.nodes[3].x)
        assertEquals(EaseCurve.MAX_Y, moved.nodes[2].y, 1e-6f)
        assertEquals(3, moved.withoutNode(1).nodes.size)
        assertEquals(moved, moved.withoutNode(0))
        // Never past its nodes between two of them (monotone).
        for (i in 0..100) assertTrue(curve.at(i / 100.0) in -1e-9..1.0 + 1e-9)
    }
}
