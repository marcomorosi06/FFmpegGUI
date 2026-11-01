package com.cuscus.ffmpeggui.core.transition

import com.cuscus.ffmpeggui.core.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The transitions told as layers (see TransitionRecipe): each starts on the
 * clip going out and ends on the one coming in, its corners agree with where
 * it reads its picture, and its shaders are written whole.
 */
class TransitionRecipeTest {

    private val w = 48
    private val h = 27

    /** Two pictures, YUV, 8 bits: a gradient each way, different on every plane. */
    private fun picture(input: Int, plane: Int, x: Int, y: Int): Double = when (plane) {
        0 -> if (input == 0) 30.0 + 150.0 * x / w + 40.0 * y / h else 220.0 - 120.0 * x / w - 60.0 * y / h
        1 -> if (input == 0) 90.0 + 40.0 * y / h else 170.0 - 30.0 * x / w
        else -> if (input == 0) 160.0 - 50.0 * x / w else 80.0 + 50.0 * y / h
    }

    private val drawn = TransitionKind.entries.filter { it.recipe != null }

    @Test
    fun `every kind after the first ones is told as layers`() {
        val first = TransitionKind.entries.takeWhile { it != TransitionKind.DISSOLVE }
        assertEquals(25, first.size)
        assertTrue(first.all { it.recipe == null })
        assertEquals(TransitionKind.entries.size - 25, drawn.size)
    }

    @Test
    fun `each starts on the clip going out and ends on the one coming in`() {
        for (kind in drawn) {
            val recipe = kind.recipe!!
            for ((t, shown) in listOf(0.0 to 0, 1.0 to 1)) {
                var wrong = 0
                for (plane in 0..2) {
                    val e = planeExpression(recipe, plane)
                    for (y in 0 until h) for (x in 0 until w) {
                        val env = ExEnv(x.toDouble() / w, y.toDouble() / h, t, w.toDouble(), h.toDouble(), plane = plane, pixel = ::picture)
                        if (abs(e.eval(env) - picture(shown, plane, x, y)) > 2.0) wrong++
                    }
                }
                assertEquals("$kind at $t", 0, wrong)
            }
        }
    }

    @Test
    fun `corners go where the picture is read`() {
        for (kind in drawn) {
            val recipe = kind.recipe!!
            for (part in listOf(recipe.outgoing, recipe.incoming)) {
                val corners = part.corners ?: continue
                for (step in 0..10) {
                    val t = step / 10.0
                    corners.forEachIndexed { i, (cx, cy) ->
                        val env0 = ExEnv(0.0, 0.0, t, 1920.0, 1080.0)
                        val x = cx.eval(env0)
                        val y = cy.eval(env0)
                        val at = ExEnv(x, y, t, 1920.0, 1080.0)
                        // Seen edge-on, a corner of a card says nothing of where it is read.
                        if (part.alpha.eval(at) == 0.0) return@forEachIndexed
                        val u = part.u.eval(at)
                        val v = part.v.eval(at)
                        assertEquals("$kind corner $i at $t (u)", if (i % 2 == 0) 0.0 else 1.0, u, 0.01)
                        assertEquals("$kind corner $i at $t (v)", if (i < 2) 0.0 else 1.0, v, 0.01)
                    }
                }
            }
        }
    }

    @Test
    fun `the shaders are written whole and read every uniform`() {
        for (kind in drawn) {
            val recipe = kind.recipe!!
            val shaders = listOf(partShader(recipe.outgoing), partShader(recipe.incoming)) +
                if (recipe.overlays.isEmpty()) emptyList() else listOf(overlayShader(recipe.overlays))
            for (shader in shaders) {
                assertEquals("$kind: parentheses", shader.count { it == '(' }, shader.count { it == ')' })
                assertEquals("$kind: braces", shader.count { it == '{' }, shader.count { it == '}' })
                assertTrue("$kind: ASCII", shader.all { it.code < 128 })
                val body = shader.substringAfter("main(")
                assertTrue("$kind reads its size", body.contains(TransitionShaderNames.SIZE))
                assertTrue("$kind reads its progress", body.contains(TransitionShaderNames.PROGRESS))
            }
            assertTrue(partShader(recipe.incoming).contains("${TransitionShaderNames.CONTENT}.eval("))
        }
    }
}
