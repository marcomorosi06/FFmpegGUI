package com.cuscus.ffmpeggui.core.model

import com.cuscus.ffmpeggui.core.preview.PreviewTimeline
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming
import com.cuscus.ffmpeggui.core.testing.FfExpr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class SpeedCurveTest {

    private val ramp = SpeedCurve(listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 2f)))

    @Test
    fun `nodes are put in order and in range, with ends at 0 and 1`() {
        val curve = SpeedCurve(listOf(SpeedPoint(0.7f, 50f), SpeedPoint(0.2f, 0.01f)))
        assertEquals(listOf(0f, 0.2f, 0.7f, 1f), curve.nodes.map { it.at })
        assertEquals(SpeedCurve.MIN_SPEED, curve.nodes[0].speed, 1e-6f)
        assertEquals(SpeedCurve.MAX_SPEED, curve.nodes.last().speed, 1e-6f)
        assertTrue(SpeedCurve.flat(2f).isFlat)
        assertFalse(ramp.isFlat)
    }

    @Test
    fun `the curve never leaves the range of its nodes`() {
        for (preset in SpeedPreset.entries) {
            val curve = preset.curve
            for (i in 0..400) {
                val s = curve.speedAt(i / 400.0)
                assertTrue("$preset at $i: $s", s >= curve.slowest - 1e-3f && s <= curve.fastest + 1e-3f)
            }
            // Through its nodes.
            for (node in curve.nodes) assertEquals("$preset", node.speed, curve.speedAt(node.at.toDouble()), node.speed * 0.01f)
        }
    }

    @Test
    fun `a flat curve plays like a constant speed`() {
        val curve = SpeedCurve.flat(2f)
        assertEquals(5_000.0, curve.playedLengthMs(10_000.0), 1e-6)
        assertEquals(2.0, curve.averageSpeed, 1e-9)
        val effects = Effects(speedCurve = curve)
        assertEquals(5_000L, effects.playedMs(10_000L))
        assertEquals(2f, effects.effectiveSpeed, 1e-6f)
        assertEquals(null, effects.curve)
    }

    @Test
    fun `time played and footage consumed are each other's inverse`() {
        for (curve in SpeedPreset.entries.map { it.curve } + ramp) {
            val length = 7_000.0
            var previous = -1.0
            for (i in 0..140) {
                val consumed = length * i / 140
                val played = curve.playedMs(length, consumed)
                assertTrue(played >= previous)
                previous = played
                assertEquals(consumed, curve.consumedMs(length, played), 0.05)
            }
            assertEquals(curve.playedLengthMs(length), curve.playedMs(length, length), 1e-6)
        }
    }

    @Test
    fun `a linear ramp matches its closed form`() {
        // The smooth curve through two nodes is a straight line in log speed: s = 0.5 * 4^x.
        val length = 4.0
        val expected = length / 0.5 * (1 - 0.25) / ln(4.0)
        assertEquals(expected * 1000, ramp.playedLengthMs(length * 1000), 1e-6)
        assertEquals(1.0, ramp.speedAt(0.5).toDouble(), 1e-6)
    }

    @Test
    fun `the FFmpeg expressions follow the same curve`() {
        val length = 6.0
        for (curve in SpeedPreset.entries.map { it.curve } + ramp) {
            val played = curve.playedExpression(length, "T")
            val consumed = curve.consumedExpression(length, "t")
            for (i in 0..120) {
                val u = length * i / 120
                val p = curve.playedMs(length, u)
                assertEquals(p, FfExpr(played, u).value(), 1e-4)
                assertEquals(u, FfExpr(consumed, p).value(), 1e-3)
            }
        }
    }

    @Test
    fun `steps play each stretch in exactly its time`() {
        val length = 5.0
        for (curve in SpeedPreset.entries.map { it.curve }) {
            val steps = curve.steps(length)
            assertEquals(50, steps.size)
            assertEquals(0.0, steps.first().from, 1e-9)
            assertEquals(length, steps.last().to, 1e-9)
            val played = steps.sumOf { (it.to - it.from) / it.speed }
            assertEquals(curve.playedLengthMs(length), played, 1e-6)
        }
        assertEquals(SpeedCurve.MAX_STEPS, ramp.steps(600.0).size)
    }

    @Test
    fun `nodes are added, moved without passing each other, and taken away`() {
        val three = ramp.withNode(0.5f)
        assertEquals(3, three.nodes.size)
        assertEquals(ramp.speedAt(0.5), three.nodes[1].speed, 1e-4f)
        val moved = three.movedNode(1, 2f, 0.3f)
        assertEquals(1f - SpeedCurve.MIN_GAP, moved.nodes[1].at, 1e-6f)
        assertEquals(0f, three.movedNode(0, 0.4f, 1f).nodes[0].at, 0f)
        assertEquals(2, three.withoutNode(1).nodes.size)
        assertEquals(three, three.withoutNode(0))
        assertEquals(listOf(2f, 0.5f), ramp.mirrored().nodes.map { it.speed })
    }

    @Test
    fun `the preview timeline follows a clip's curve`() {
        val timeline = PreviewTimeline(
            listOf(SegmentTiming(2_000), SegmentTiming(4_000, curve = ramp, keepPitch = false), SegmentTiming(1_000, speed = 2f)),
            speed = 1f,
        )
        val curveLength = ramp.playedLengthMs(4_000.0)
        assertEquals(Math.round(2_000 + curveLength + 500), timeline.outputDurationMs)
        // Halfway through the curved clip's footage.
        val media = 4_000L
        val out = timeline.outputMs(media)
        assertEquals(2_000 + ramp.playedMs(4_000.0, 2_000.0), out.toDouble(), 1.0)
        assertTrue(abs(timeline.mediaMsForOutput(out) - media) <= 2)
        assertEquals(1f, timeline.rateAt(media), 1e-3f)
        assertEquals(0.5f, timeline.rateAt(2_000L), 1e-3f)
        // Its pitch follows its speed; the first clip keeps its own.
        assertEquals(1f, timeline.pitchAt(media), 1e-3f)
        assertEquals(1f, timeline.pitchAt(500L), 1e-6f)
        assertEquals(0.5f, timeline.pitchAt(2_000L), 1e-3f)
        for (o in 0L..timeline.outputDurationMs step 37) {
            val m = timeline.mediaMsForOutput(o)
            assertTrue("$o -> $m -> ${timeline.outputMs(m)}", abs(timeline.outputMs(m) - o) <= 2)
        }
    }

    @Test
    fun `a whole video whose pitch follows its speed`() {
        val timeline = PreviewTimeline(listOf(SegmentTiming(1_000, speed = 2f)), speed = 1.5f, keepPitch = false)
        assertEquals(1.5f, timeline.pitchAt(500L), 1e-6f)
        assertEquals(3f, timeline.rateAt(500L), 1e-6f)
    }

    @Test
    fun `a clip cut in two keeps its part of the curve on each half`() {
        val clip = com.cuscus.ffmpeggui.core.timeline.Clip(id = "a", sourceId = "s", startMs = 0, endMs = 4_000, effects = Effects(speedCurve = ramp))
        val timeline = com.cuscus.ffmpeggui.core.timeline.Timeline(listOf(clip)).splitAt(1_000, "b")
        val (first, second) = timeline.clips
        assertEquals(ramp.speedAt(0.25), first.effects.speedCurve!!.nodes.last().speed, 1e-4f)
        assertEquals(ramp.speedAt(0.25), second.effects.speedCurve!!.nodes.first().speed, 1e-4f)
        assertEquals(2f, second.effects.speedCurve!!.nodes.last().speed, 1e-4f)
        // Played the other way, the first half to play is the end of the file.
        val back = com.cuscus.ffmpeggui.core.timeline.Timeline(listOf(clip.copy(effects = Effects(speedCurve = ramp, reversed = true)))).splitAt(1_000, "b")
        assertEquals("b", back.clips[0].id)
        assertEquals(0.5f, back.clips[0].effects.speedCurve!!.nodes.first().speed, 1e-4f)
        assertEquals(ramp.speedAt(0.75), back.clips[0].effects.speedCurve!!.nodes.last().speed, 1e-4f)
        // The whole sequence trimmed: the part that still plays keeps its speeds.
        val window = com.cuscus.ffmpeggui.core.timeline.Timeline(listOf(clip)).window(2_000, null).single()
        assertEquals(1f, window.effects.speedCurve!!.nodes.first().speed, 1e-4f)
    }
}
