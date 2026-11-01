package com.cuscus.ffmpeggui.effects

import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.LayerBlend
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import com.cuscus.ffmpeggui.preview.outputClock

/*
 * A transition drawn by an effect (see Transition.effect): the two clips meet
 * with a plain cut, and the effect is laid over the whole video around it,
 * its brightest moment on the cut. A transition told as layers may bring an
 * effect of its own too (see TransitionKind.companionEffect: the 3D fist of a
 * punch), laid over the stretch the two clips overlap, its blow on the moment
 * the recipe swaps the clips, as large as Transition.size says. The export
 * and the preview both take these overlays from here, so they always agree;
 * the editor never keeps them.
 */

/**
 * How long before the cut this transition starts to show: its length for a
 * cross-fade, for an effect the part of it before the cut.
 */
fun Transition.leadMs(): Long = if (effect != null) EffectLibrary.info(effect)?.cutMs ?: 0L else requestedMs

/** True when an effect is laid over this transition: its own, or that of its kind. */
val Transition.drawsEffect: Boolean get() = isEffect || kind.companionEffect != null

/** Above every caption, picture and effect the editor places. */
const val TRANSITION_EFFECT_LAYER = Int.MAX_VALUE / 2

/** Id prefix of these overlays (they are made again each time, never edited). */
private const val ID_PREFIX = "transition:"

/** True for an overlay made here. */
val ImageOverlay.isTransitionEffect: Boolean get() = id.startsWith(ID_PREFIX)

/**
 * The effects laid over the cuts between [clips] (those that play, in order;
 * [whole] the effects of the whole video), on the exported video's clock. An
 * effect that would start before the video loses its beginning; one whose
 * file is unknown is left out.
 */
fun transitionEffects(clips: List<Clip>, whole: Effects): List<ImageOverlay> {
    if (clips.size < 2 || clips.dropLast(1).none { it.effects.transition?.drawsEffect == true }) return emptyList()
    val clock = outputClock(clips, whole, 0L)
    return clips.dropLast(1).mapIndexedNotNull { gap, clip ->
        val transition = clip.effects.transition ?: return@mapIndexedNotNull null
        val companion = if (transition.isEffect) null else transition.kind.companionEffect
        val name = transition.effect ?: companion ?: return@mapIndexedNotNull null
        val effect = EffectLibrary.info(name) ?: return@mapIndexedNotNull null
        val uri = EffectLibrary.uri(name) ?: return@mapIndexedNotNull null
        // Where its blow (or its brightest moment) falls on the output.
        val cut = if (companion == null) {
            clock.cutOutputMs(gap) ?: return@mapIndexedNotNull null
        } else {
            val range = clock.transitionOutputRange(gap)
            if (range.isEmpty()) return@mapIndexedNotNull null
            val length = range.last - range.first
            val through = if (whole.reversed) 1.0 - transition.kind.companionSwitch else transition.kind.companionSwitch
            range.first + Math.round(length * through)
        }
        val start = cut - effect.cutMs
        val skipped = (-start).coerceAtLeast(0L)
        if (skipped >= effect.lengthMs) return@mapIndexedNotNull null
        val span = SoundSpan(
            startMs = start + skipped,
            trimStartMs = skipped,
            trimEndMs = effect.lengthMs,
            fileDurationMs = effect.lengthMs,
        )
        ImageOverlay(
            id = ID_PREFIX + clip.id,
            uri = uri,
            fileName = effect.file,
            x = 0f,
            y = 0f,
            // A fist as large as asked; light fills the frame.
            scaleW = if (effect.alpha) transition.size.coerceIn(Transition.MIN_SIZE, Transition.MAX_SIZE) else 1f,
            layer = TRANSITION_EFFECT_LAYER,
            // Light on black is screened; what carries its own opacity is laid as it is.
            blend = if (effect.alpha) LayerBlend.NORMAL else LayerBlend.SCREEN,
            effect = name,
        ).withVideo(span)
    }
}
