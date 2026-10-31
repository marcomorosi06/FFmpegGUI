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
 * its brightest moment on the cut. The export and the preview both take these
 * overlays from here, so they always agree; the editor never keeps them.
 */

/**
 * How long before the cut this transition starts to show: its length for a
 * cross-fade, for an effect the part of it before the cut.
 */
fun Transition.leadMs(): Long = if (effect != null) EffectLibrary.info(effect)?.cutMs ?: 0L else durationMs

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
    if (clips.size < 2 || clips.dropLast(1).none { it.effects.transition?.isEffect == true }) return emptyList()
    val clock = outputClock(clips, whole, 0L)
    return clips.dropLast(1).mapIndexedNotNull { gap, clip ->
        val name = clip.effects.transition?.effect ?: return@mapIndexedNotNull null
        val effect = EffectLibrary.info(name) ?: return@mapIndexedNotNull null
        val uri = EffectLibrary.uri(name) ?: return@mapIndexedNotNull null
        val cut = clock.cutOutputMs(gap) ?: return@mapIndexedNotNull null
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
            scaleW = 1f,
            layer = TRANSITION_EFFECT_LAYER,
            blend = LayerBlend.SCREEN,
            effect = name,
        ).withVideo(span)
    }
}
