package com.cuscus.ffmpeggui.core.model

import com.cuscus.ffmpeggui.core.project.ModelJson
import com.cuscus.ffmpeggui.core.transition.ExEnv
import com.cuscus.ffmpeggui.core.transition.eval
import com.cuscus.ffmpeggui.core.transition.recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The punches: a 3D fist laid over a transition told as layers, at the length of its blow. */
class PunchTransitionTest {

    @Test
    fun `a punch keeps the length of its blow`() {
        assertEquals(PUNCH_MS, Transition(TransitionKind.PUNCH, durationMs = 300).requestedMs)
        assertEquals(PUNCH_MS, Transition(TransitionKind.PUNCH_BACK, durationMs = 2_500).requestedMs)
        assertEquals(700L, Transition(TransitionKind.FADE, durationMs = 700).requestedMs)
        // Two long clips overlap for the whole blow, whatever was asked.
        assertEquals(listOf(PUNCH_MS), transitionLengths(listOf(Transition(TransitionKind.PUNCH, 300)), listOf(10_000L, 10_000L)))
        // Two short ones, for as long as they allow.
        assertEquals(listOf(600L), transitionLengths(listOf(Transition(TransitionKind.PUNCH, 300)), listOf(1_200L, 5_000L)))
    }

    @Test
    fun `each punch brings its fist and swaps the clips under it`() {
        assertEquals("punch", TransitionKind.PUNCH.companionEffect)
        assertEquals("punch_back", TransitionKind.PUNCH_BACK.companionEffect)
        assertNull(TransitionKind.FADE.companionEffect)
        assertNull(TransitionKind.IMPACT_SHAKE.fixedMs)
        for (kind in listOf(TransitionKind.PUNCH, TransitionKind.PUNCH_BACK)) {
            val recipe = kind.recipe
            assertNotNull(recipe)
            recipe!!
            // The new clip shows from the switch on, not before.
            val before = kind.companionSwitch - 0.02
            val after = kind.companionSwitch + 0.02
            assertEquals(0.0, recipe.incoming.alpha.eval(ExEnv(32.0, 18.0, before, 64.0, 36.0)), 1e-9)
            assertEquals(1.0, recipe.incoming.alpha.eval(ExEnv(32.0, 18.0, after, 64.0, 36.0)), 1e-9)
        }
    }

    @Test
    fun `the size of the fist is kept with the project`() {
        val effects = Effects(transition = Transition(TransitionKind.PUNCH_BACK, PUNCH_MS, size = 0.55f))
        val back = ModelJson.effects(ModelJson.write(effects))
        assertEquals(0.55f, back.transition!!.size, 1e-6f)
        assertEquals(TransitionKind.PUNCH_BACK, back.transition!!.kind)
    }
}
