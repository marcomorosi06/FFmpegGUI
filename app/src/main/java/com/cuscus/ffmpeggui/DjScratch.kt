package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.eggs.ScratchDetector
import com.cuscus.ffmpeggui.ui.expressive.MorphingPlayButton
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

/*
 * The video editor's hidden scratch: drag the timeline back and forth quickly
 * and widely, as a DJ scratches a record, and the play button turns into a
 * spinning vinyl for a moment, ticking at every turn of the hand, spinning on
 * with the finger and slowing down when it stops. It plays nothing and
 * changes nothing (the playhead moves as any scrub moves it); with the
 * system's animations off it never shows. See ScratchDetector for what counts.
 */

/** What the timeline tells the play button about a scratch (public: the timeline, which takes it, is). */
@Stable
class DjScratch(minSwingPx: Float) {
    private val detector = ScratchDetector(minSwingPx)

    /** A scratch is on: the play button is a record. */
    var active by mutableStateOf(false)
        private set

    /** Turns of the hand while scratching: each one ticks. */
    var turns by mutableIntStateOf(0)
        private set

    /** The record's angle, degrees: it follows the finger, and spins on by itself. */
    val angle = mutableFloatStateOf(0f)

    /** When the finger last moved, for the record to stop once it rests. */
    @Volatile
    var lastMoveNanos = 0L
        private set

    /** One step of a drag of the timeline: [deltaPx] either way, at [nanos]. */
    fun drag(deltaPx: Float, nanos: Long) {
        when (detector.feed(deltaPx, nanos)) {
            ScratchDetector.Step.SCRATCH -> {
                active = true
                turns++
            }
            ScratchDetector.Step.TURN -> if (active) turns++
            ScratchDetector.Step.NONE -> Unit
        }
        if (active) {
            angle.floatValue += deltaPx * DEGREES_PER_PX
            lastMoveNanos = nanos
        }
    }

    /** The scratch is over. */
    fun stop() {
        active = false
        detector.reset()
    }

    companion object {
        /** How far the record turns for a pixel of the finger. */
        const val DEGREES_PER_PX = 0.9f

        /** The record stops this long after the finger last moved. */
        const val QUIET_NANOS = 900_000_000L

        /** A turning record, left to itself: about 33 turns a minute. */
        const val IDLE_DEGREES_PER_SECOND = 200f

        /** The widest swing that counts, so that slow scrubbing never sets it off. */
        val MIN_SWING: Dp = 56.dp
    }
}

/**
 * The play button, or the record while [scratch] is on: it springs out of the
 * button and back into it. Tapping either plays or pauses.
 */
@Composable
internal fun PlayOrRecord(
    isPlaying: Boolean,
    scratch: DjScratch?,
    onClick: () -> Unit,
    playDescription: String,
    pauseDescription: String,
) {
    val haptic = LocalHapticFeedback.current
    val spinning = scratch?.active == true
    if (scratch != null) {
        // A tick at every turn of the hand, the first one firmer.
        LaunchedEffect(scratch) {
            snapshotFlow { scratch.turns }.drop(1).collect { turn ->
                haptic.performHapticFeedback(if (turn == 1) HapticFeedbackType.LongPress else HapticFeedbackType.SegmentFrequentTick)
            }
        }
        // Spinning on by itself while it lasts; over once the finger has rested a moment.
        LaunchedEffect(spinning) {
            if (!spinning) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                scratch.angle.floatValue += DjScratch.IDLE_DEGREES_PER_SECOND * (now - last) / 1_000_000_000f
                last = now
                if (System.nanoTime() - scratch.lastMoveNanos > DjScratch.QUIET_NANOS) {
                    delay(120)
                    scratch.stop()
                    break
                }
            }
        }
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = spinning,
        transitionSpec = {
            val pop = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            (scaleIn(pop, initialScale = 0.4f) + fadeIn(motion.fastEffectsSpec())) togetherWith
                (scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f) + fadeOut(motion.fastEffectsSpec())) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.Center,
        label = "playOrRecord",
    ) { record ->
        if (record && scratch != null) {
            VinylRecord(scratch, onClick, if (isPlaying) pauseDescription else playDescription)
        } else {
            MorphingPlayButton(isPlaying = isPlaying, onClick = onClick, playDescription = playDescription, pauseDescription = pauseDescription)
        }
    }
}

/** A record on the turntable, as big as the play button: grooves, a shine that stays put, the label turning. */
@Composable
private fun VinylRecord(scratch: DjScratch, onClick: () -> Unit, description: String, size: Dp = 48.dp) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.85f)
            .size(size)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = center
            drawCircle(scheme.inverseSurface, r, c)
            // The grooves.
            for (k in 1..GROOVES) {
                drawCircle(scheme.inverseOnSurface.copy(alpha = 0.14f), r * (0.52f + 0.11f * k), c, style = Stroke(width = 1.dp.toPx()))
            }
            // The shine, where the light is: it does not turn with the record.
            drawArc(
                color = scheme.inverseOnSurface.copy(alpha = 0.22f),
                startAngle = -150f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.8f),
                size = Size(r * 1.6f, r * 1.6f),
                style = Stroke(width = r * 0.16f),
            )
            rotate(scratch.angle.floatValue, c) {
                drawCircle(scheme.primary, r * 0.4f, c)
                // A mark on the label, to see it turn.
                drawCircle(scheme.onPrimary, r * 0.08f, Offset(c.x, c.y - r * 0.24f))
                drawArc(
                    color = scheme.tertiary,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = true,
                    topLeft = Offset(c.x - r * 0.4f, c.y - r * 0.4f),
                    size = Size(r * 0.8f, r * 0.8f),
                    alpha = 0.55f,
                )
            }
            drawCircle(scheme.inverseSurface, r * 0.07f, c)
        }
    }
}

private const val GROOVES = 4
