package com.cuscus.ffmpeggui.core.model

import kotlin.math.min

/**
 * How one clip gives way to the next: FFmpeg's xfade transition [xfade] (the
 * export) and its family, which tells the live preview how to draw it.
 *
 * The two clips overlap for the transition's length: the joined video is that
 * much shorter, as in every video editor.
 *
 * The kinds after [ZOOM_IN] are told as layers (see core.transition, their
 * TransitionRecipe): those whose [xfade] is [XFADE_CUSTOM] are exported with that
 * recipe as xfade's custom expression, the others with xfade's own transition
 * of that name, which the recipe draws alike for the preview.
 */
enum class TransitionKind(val xfade: String) {
    FADE("fade"),
    FADE_BLACK("fadeblack"),
    FADE_WHITE("fadewhite"),
    WIPE_LEFT("wipeleft"),
    WIPE_RIGHT("wiperight"),
    WIPE_UP("wipeup"),
    WIPE_DOWN("wipedown"),
    SLIDE_LEFT("slideleft"),
    SLIDE_RIGHT("slideright"),
    SLIDE_UP("slideup"),
    SLIDE_DOWN("slidedown"),
    COVER_LEFT("coverleft"),
    COVER_RIGHT("coverright"),
    REVEAL_LEFT("revealleft"),
    REVEAL_RIGHT("revealright"),
    SMOOTH_LEFT("smoothleft"),
    SMOOTH_RIGHT("smoothright"),
    CIRCLE_OPEN("circleopen"),
    CIRCLE_CLOSE("circleclose"),
    CIRCLE_CROP("circlecrop"),
    RECT_CROP("rectcrop"),
    VERT_OPEN("vertopen"),
    HORZ_OPEN("horzopen"),
    RADIAL("radial"),
    ZOOM_IN("zoomin"),

    // Basic
    DISSOLVE("dissolve"),
    FADE_GRAYS(XFADE_CUSTOM),
    OVERLAP_CUT(XFADE_CUSTOM),

    // Camera
    ZOOM_OUT(XFADE_CUSTOM),
    PULL_IN(XFADE_CUSTOM),
    PULL_OUT(XFADE_CUSTOM),
    WHIP_LEFT(XFADE_CUSTOM),
    WHIP_RIGHT(XFADE_CUSTOM),
    WHIP_UP(XFADE_CUSTOM),
    WHIP_DOWN(XFADE_CUSTOM),
    SPIN_CW(XFADE_CUSTOM),
    SPIN_CCW(XFADE_CUSTOM),
    SHAKE(XFADE_CUSTOM),
    SWIRL(XFADE_CUSTOM),
    ROLL(XFADE_CUSTOM),

    // Slide
    COVER_UP("coverup"),
    COVER_DOWN("coverdown"),
    REVEAL_UP("revealup"),
    REVEAL_DOWN("revealdown"),
    SPLIT_H(XFADE_CUSTOM),
    SPLIT_V(XFADE_CUSTOM),
    SQUEEZE_H("squeezeh"),
    SQUEEZE_V("squeezev"),

    // Wipe
    SMOOTH_UP("smoothup"),
    SMOOTH_DOWN("smoothdown"),
    WIPE_TL("wipetl"),
    WIPE_TR("wipetr"),
    WIPE_BL("wipebl"),
    WIPE_BR("wipebr"),
    DIAG_TL("diagtl"),
    DIAG_TR("diagtr"),
    DIAG_BL("diagbl"),
    DIAG_BR("diagbr"),

    // Light
    FLASH(XFADE_CUSTOM),
    LIGHT_LEAK(XFADE_CUSTOM),
    FILM_BURN(XFADE_CUSTOM),
    GLOW(XFADE_CUSTOM),
    LENS_FLARE(XFADE_CUSTOM),

    // Glitch
    GLITCH(XFADE_CUSTOM),
    RGB_SPLIT(XFADE_CUSTOM),
    WAVE(XFADE_CUSTOM),
    TV_NOISE(XFADE_CUSTOM),
    PIXELATE("pixelize"),

    // Blur
    ZOOM_BLUR(XFADE_CUSTOM),
    MOTION_BLUR(XFADE_CUSTOM),
    SPIN_BLUR(XFADE_CUSTOM),
    DEFOCUS(XFADE_CUSTOM),

    // Shapes (motion graphics)
    DIAMOND(XFADE_CUSTOM),
    HEXAGON(XFADE_CUSTOM),
    STAR(XFADE_CUSTOM),
    STRIPES(XFADE_CUSTOM),
    CHECKER(XFADE_CUSTOM),
    PAPER_TEAR(XFADE_CUSTOM),
    BRUSH(XFADE_CUSTOM),

    // Masks
    VERT_CLOSE("vertclose"),
    HORZ_CLOSE("horzclose"),
    DIAGONAL_SPLIT(XFADE_CUSTOM),
    LUMA_FADE(XFADE_CUSTOM),

    // 3D
    CUBE_LEFT(XFADE_CUSTOM),
    CUBE_RIGHT(XFADE_CUSTOM),
    FLIP_H(XFADE_CUSTOM),
    FLIP_V(XFADE_CUSTOM),
    PAGE_TURN(XFADE_CUSTOM),

    // Social
    SWIPE_UP(XFADE_CUSTOM),
    SWIPE_DOWN(XFADE_CUSTOM),
    HEARTS(XFADE_CUSTOM),

    // Motion
    PENDULUM_1(XFADE_CUSTOM),
    PENDULUM_2(XFADE_CUSTOM),
    BOUNCE(XFADE_CUSTOM),
    REBOUND_1(XFADE_CUSTOM),
    REBOUND_2(XFADE_CUSTOM),
    SPRING(XFADE_CUSTOM),
    IMPACT_SHAKE(XFADE_CUSTOM),

    // Punches: a 3D fist laid over the clips (see companionEffect)
    PUNCH(XFADE_CUSTOM),
    PUNCH_BACK(XFADE_CUSTOM),

    // The TV break: the channel changes, a test card with its tune, the channel changes again
    TECHNICAL_DIFFICULTIES(XFADE_CUSTOM),
    ;

    /** True when the export draws this one with xfade's custom expression (see core.transition). */
    val isCustom: Boolean get() = xfade == XFADE_CUSTOM

    /**
     * The app's own effect laid over the clips while this one runs (a 3D fist),
     * by its name in the effect library; null for most. Its cut falls where the
     * recipe switches clips ([companionSwitch] of the way through).
     */
    val companionEffect: String?
        get() = when (this) {
            PUNCH -> "punch"
            PUNCH_BACK -> "punch_back"
            else -> null
        }

    /** How far through a kind with a [companionEffect] its recipe switches clips, under the effect. */
    val companionSwitch: Double
        get() = when (this) {
            PUNCH -> PUNCH_SWITCH
            PUNCH_BACK -> PUNCH_BACK_SWITCH
            else -> 0.5
        }

    /** The length of a kind that lasts as long as its [companionEffect], whatever is asked; null for the others. */
    val fixedMs: Long?
        get() = if (companionEffect != null) PUNCH_MS else null

    /**
     * True for the TV break: the app lays its three parts over the clips (the
     * channel changing, the test card with its tune, the channel changing
     * again: the effects tv_intro, tv_card and tv_outro), and the clips' own
     * sound gives way to theirs (see [mutesClips]).
     */
    val tvBreak: Boolean get() = this == TECHNICAL_DIFFICULTIES

    /**
     * True when the clips' sound does not cross-fade over this one: the clip
     * going out falls silent at its start, the one coming in is heard at its
     * end, each over [CLIP_SOUND_FADE_MS].
     */
    val mutesClips: Boolean get() = tvBreak

    /** The shortest this kind can last (shorter, the clips meet with a cut). */
    val minMs: Long get() = if (tvBreak) TV_BREAK_MIN_MS else Transition.MIN_MS / 2

    /** The longest this kind can be asked to last. */
    val maxMs: Long get() = if (tvBreak) TV_BREAK_MAX_MS else Transition.MAX_MS

    /** The length this kind starts with when it is chosen. */
    val defaultMs: Long get() = if (tvBreak) TV_BREAK_DEFAULT_MS else Transition.DEFAULT_MS
}

/** The TV break's lengths: its shortest, longest and first. The longest is its card's file's (scripts/tv_break.py, CARD_SECONDS). */
const val TV_BREAK_MIN_MS = 1_600L
const val TV_BREAK_MAX_MS = 15_000L
const val TV_BREAK_DEFAULT_MS = 3_000L

/** How long the channel takes to change, into the card and out of it (scripts/tv_break.py, INTRO_FRAMES, OUTRO_FRAMES). */
const val TV_BREAK_PART_MS = 700L

/** How far into the break the card starts under the snow (and how far before its end it stops). */
const val TV_BREAK_CARD_IN_MS = 450L

/** How quickly the clips' sound falls silent and comes back around a kind that [TransitionKind.mutesClips]. */
const val CLIP_SOUND_FADE_MS = 250L

/**
 * How much of each clip's sound is heard [playedMs] into a transition of
 * [kind] lasting [overlapMs]: the clip going out's, the one coming in's.
 * They cross-fade, as the export's acrossfade does, unless the kind mutes them.
 */
fun clipSoundShares(kind: TransitionKind?, playedMs: Double, overlapMs: Long): Pair<Float, Float> {
    if (overlapMs <= 0L) return 1f to 0f
    if (kind?.mutesClips == true) {
        val fade = CLIP_SOUND_FADE_MS.toDouble()
        val outgoing = 1.0 - (playedMs / fade).coerceIn(0.0, 1.0)
        val incoming = ((playedMs - (overlapMs - CLIP_SOUND_FADE_MS)) / fade).coerceIn(0.0, 1.0)
        return outgoing.toFloat() to incoming.toFloat()
    }
    val progress = (playedMs / overlapMs).coerceIn(0.0, 1.0).toFloat()
    return (1f - progress) to progress
}

/** How long a punch lasts: as long as its fist's effect (scripts/punch3d.py, DURATION). */
const val PUNCH_MS = 1_000L

/** Where the fist covers the frame (scripts/punch3d.py, CUT over DURATION). */
const val PUNCH_SWITCH = 0.43

/** Where the flash after the fist hit the picture is brightest (scripts/punch3d.py, BACK_CUT over DURATION). */
const val PUNCH_BACK_SWITCH = 0.55

/** xfade's name for a transition given by an expression. */
const val XFADE_CUSTOM = "custom"

/**
 * The transition from a clip into the one after it: its [kind], [durationMs]
 * long (made shorter when a clip is too short for it, see [effectiveMs]).
 *
 * With an [effect] (the name of one of the app's own transition effects, a
 * fairy's wand, an explosion), the clips meet with a cut instead, and the
 * effect is laid over the cut by whoever builds the request: [kind] and
 * [durationMs] are then not used.
 *
 * A kind with a companion effect (a punch's fist) lasts that effect's length
 * ([requestedMs]); [size] is how large the effect is drawn, 1 its full size
 * (a fist that fills the frame when it lands).
 */
data class Transition(
    val kind: TransitionKind = TransitionKind.FADE,
    val durationMs: Long = DEFAULT_MS,
    val effect: String? = null,
    val size: Float = 1f,
    /** The sound of its companion effect (a punch's blow, a TV break's channel changing) is heard. */
    val sound: Boolean = true,
    /** How loud that sound is (linear, 1 = as made). */
    val soundVolume: Float = 1f,
    /** A TV break's tune is heard. */
    val music: Boolean = true,
    /** How loud the tune is (linear, 1 = as made). */
    val musicVolume: Float = 1f,
) {
    /** True when the clips meet with a cut under an effect (see [effect]). */
    val isEffect: Boolean get() = effect != null

    /** How long it is asked to last: its kind's own length when it has one (see TransitionKind.fixedMs). */
    val requestedMs: Long get() = kind.fixedMs ?: durationMs

    companion object {
        const val DEFAULT_MS = 500L
        const val MIN_MS = 100L
        const val MAX_MS = 3_000L

        /** The smallest and largest [size] of a companion effect. */
        const val MIN_SIZE = 0.3f
        const val MAX_SIZE = 1f

        /** The loudest the sounds of a transition can be set (linear). */
        const val MAX_VOLUME = 2f

        /**
         * How long a transition of [requestedMs] really lasts between clips that
         * play [outgoingMs] and [incomingMs]: never more than half of either, so
         * that the two transitions of a clip never meet, and 0 when that is too
         * short to be seen.
         */
        fun effectiveMs(requestedMs: Long, outgoingMs: Long, incomingMs: Long, kind: TransitionKind? = null): Long {
            val ms = min(requestedMs.coerceIn(0L, kind?.maxMs ?: MAX_MS), min(outgoingMs, incomingMs) / 2)
            return if (ms < (kind?.minMs ?: (MIN_MS / 2))) 0L else ms
        }
    }
}

/**
 * How long each transition between [playedMs] (the clips' lengths once their
 * speed is applied, in order) really lasts: one value per gap, 0 where there
 * is none. A null length (open-ended clip) has none around it.
 */
fun transitionLengths(transitions: List<Transition?>, playedMs: List<Long?>): List<Long> =
    (0 until (playedMs.size - 1).coerceAtLeast(0)).map { i ->
        val transition = transitions.getOrNull(i) ?: return@map 0L
        // An effect is laid over a cut: the clips do not overlap.
        if (transition.isEffect) return@map 0L
        val a = playedMs[i] ?: return@map 0L
        val b = playedMs[i + 1] ?: return@map 0L
        Transition.effectiveMs(transition.requestedMs, a, b, transition.kind)
    }
