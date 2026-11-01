package com.cuscus.ffmpeggui.effects

import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.LayerBlend
import com.cuscus.ffmpeggui.core.model.TV_BREAK_CARD_IN_MS
import com.cuscus.ffmpeggui.core.model.TV_BREAK_MIN_MS
import com.cuscus.ffmpeggui.core.model.TV_BREAK_PART_MS
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
 * the recipe swaps the clips, as large as Transition.size says; a TV break
 * (TransitionKind.tvBreak) brings three, one after the other, each with its
 * own sound. The export
 * and the preview both take these overlays from here, so they always agree;
 * the editor never keeps them.
 */

/**
 * How long before the cut this transition starts to show: its length for a
 * cross-fade, for an effect the part of it before the cut.
 */
fun Transition.leadMs(): Long = if (effect != null) EffectLibrary.info(effect)?.cutMs ?: 0L else requestedMs

/** True when an effect is laid over this transition: its own, or that of its kind (a fist, a TV break). */
val Transition.drawsEffect: Boolean get() = isEffect || kind.companionEffect != null || kind.tvBreak

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
    val tvBreaks = clips.dropLast(1).flatMapIndexed { gap, clip ->
        val breaks = clip.effects.transition?.let { !it.isEffect && it.kind.tvBreak } == true
        if (breaks) tvBreak(clip.id, clock.transitionOutputRange(gap)) else emptyList()
    }
    return tvBreaks + clips.dropLast(1).mapIndexedNotNull { gap, clip ->
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

/**
 * The TV break over the stretch [range] of the output (ms) the clips overlap:
 * the card with its tune all along but for its ends, the channel changing into
 * it and out of it laid over its ends (snow, the channel's number, the screen
 * coming on). Each carries its own sound. On a stretch shorter than the
 * break's own shortest (a video sped up), the channel changes faster.
 */
private fun tvBreak(clipId: String, range: LongRange): List<ImageOverlay> {
    if (range.isEmpty()) return emptyList()
    val card = EffectLibrary.info(TV_CARD) ?: return emptyList()
    val intro = EffectLibrary.info(TV_INTRO) ?: return emptyList()
    val outro = EffectLibrary.info(TV_OUTRO) ?: return emptyList()
    val length = range.last - range.first
    val shrink = (length.toDouble() / TV_BREAK_MIN_MS).coerceAtMost(1.0)
    val part = Math.round(TV_BREAK_PART_MS * shrink)
    val cardIn = Math.round(TV_BREAK_CARD_IN_MS * shrink)
    val cardMs = (length - 2 * cardIn).coerceIn(1L, card.lengthMs)
    fun overlay(info: EffectInfo, part: String, span: SoundSpan, fadeIn: Float = 0f, fadeOut: Float = 0f): ImageOverlay? {
        val uri = EffectLibrary.uri(info.name) ?: return null
        return ImageOverlay(
            id = ID_PREFIX + clipId + ":" + part,
            uri = uri,
            fileName = info.file,
            x = 0f,
            y = 0f,
            scaleW = 1f,
            layer = TRANSITION_EFFECT_LAYER,
            blend = LayerBlend.NORMAL,
            effect = info.name,
            hasAudio = info.audio,
            fadeInSeconds = fadeIn,
            fadeOutSeconds = fadeOut,
        ).withVideo(span)
    }
    val introMs = part.coerceAtMost(intro.lengthMs)
    val outroMs = part.coerceAtMost(outro.lengthMs)
    // The card first (under), its tune fading in under the snow and out under the next snow.
    return listOfNotNull(
        overlay(card, "card", SoundSpan(range.first + cardIn, 0L, cardMs, card.lengthMs), fadeIn = TV_SOUND_FADE_S, fadeOut = TV_SOUND_FADE_S),
        overlay(intro, "intro", SoundSpan(range.first, 0L, introMs, intro.lengthMs)),
        // The outro keeps its end (the screen coming on), whatever is cut from its start.
        overlay(outro, "outro", SoundSpan(range.last - outroMs, outro.lengthMs - outroMs, outro.lengthMs, outro.lengthMs)),
    )
}

/** The TV break's three parts in the effect library (see scripts/tv_break.py). */
const val TV_INTRO = "tv_intro"
const val TV_CARD = "tv_card"
const val TV_OUTRO = "tv_outro"

/** How quickly the card's tune comes in and goes, under the snow, seconds. */
private const val TV_SOUND_FADE_S = 0.1f
