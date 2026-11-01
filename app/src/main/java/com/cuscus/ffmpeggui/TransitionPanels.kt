@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cuscus.ffmpeggui.core.model.TV_BREAK_CARD_IN_MS
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.effects.EffectInfo
import com.cuscus.ffmpeggui.effects.EffectLibrary
import com.cuscus.ffmpeggui.effects.TV_CARD
import com.cuscus.ffmpeggui.effects.TV_INTRO
import com.cuscus.ffmpeggui.effects.TV_OUTRO
import com.cuscus.ffmpeggui.core.model.TransitionKind
import com.cuscus.ffmpeggui.core.preview.MaskShape
import com.cuscus.ffmpeggui.core.preview.PartLook
import com.cuscus.ffmpeggui.core.preview.maskAlpha
import com.cuscus.ffmpeggui.core.preview.outgoingOnTop
import com.cuscus.ffmpeggui.core.preview.transitionLook
import com.cuscus.ffmpeggui.core.preview.whiteBehind
import com.cuscus.ffmpeggui.core.transition.Rgb
import com.cuscus.ffmpeggui.core.transition.TransitionRecipe
import com.cuscus.ffmpeggui.core.transition.recipe
import com.cuscus.ffmpeggui.preview.TransitionShaders
import com.cuscus.ffmpeggui.preview.gpuTransitions
import com.cuscus.ffmpeggui.preview.layerEffect
import com.cuscus.ffmpeggui.preview.setTransition
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot

/*
 * Transitions between clips: how the preview draws them (the same math as the
 * export's xfade, see core's TransitionLook.kt) and the panel that picks one,
 * each choice a tile playing its transition in a loop.
 */

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

/**
 * Draws this layer as one of the two clips of a transition: the one going out
 * ([incoming] false) or the one coming in, [kind] and [progress] (0..1) read
 * while drawing, so that a running transition never recomposes. Without a
 * transition the clip going out is drawn as it is and the one coming in not
 * at all. Put the layer's background after this modifier: it takes part.
 */
fun Modifier.transitionPart(kind: () -> TransitionKind?, progress: () -> Float, incoming: Boolean): Modifier {
    fun look(): PartLook {
        val current = kind() ?: return if (incoming) PartLook.HIDDEN else PartLook.PLAIN
        return transitionLook(current, progress(), incoming)
    }
    return graphicsLayer {
        val look = look()
        alpha = look.alpha
        translationX = look.dx * size.width
        translationY = look.dy * size.height
        scaleX = look.scale
        scaleY = look.scale
        // A soft mask cuts into what is drawn below it in the layer only.
        compositingStrategy = if (look.mask != MaskShape.NONE) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        val look = look()
        if (look.alpha <= 0f) return@drawWithContent
        val window = look.window
        when {
            window != null -> clipRect(window.left * size.width, window.top * size.height, window.right * size.width, window.bottom * size.height) {
                this@drawWithContent.drawContent()
            }
            look.circle >= 0f -> {
                val radius = look.circle * hypot(size.width / 2f, size.height / 2f)
                val circle = Path().apply { addOval(Rect(center, radius)) }
                clipPath(circle) { this@drawWithContent.drawContent() }
            }
            else -> drawContent()
        }
        val current = kind()
        if (current != null && look.mask != MaskShape.NONE) {
            drawRect(maskBrush(current, progress(), look.mask, size.width, size.height), blendMode = BlendMode.DstIn)
        }
    }
}

/** The soft edge of a [mask] transition as a brush of opacities, over a layer of [width] x [height]. */
private fun maskBrush(kind: TransitionKind, t: Float, mask: MaskShape, width: Float, height: Float): Brush {
    fun stop(at: Float, u: Float) = at to Color.Black.copy(alpha = maskAlpha(kind, t, u).coerceIn(0f, 1f))
    val centre = Offset(width / 2f, height / 2f)
    return when (mask) {
        MaskShape.ACROSS -> Brush.horizontalGradient(*Array(MASK_STOPS + 1) { i -> (i.toFloat() / MASK_STOPS).let { stop(it, it) } }, startX = 0f, endX = width)
        MaskShape.DOWN -> Brush.verticalGradient(*Array(MASK_STOPS + 1) { i -> (i.toFloat() / MASK_STOPS).let { stop(it, it) } }, startY = 0f, endY = height)
        MaskShape.RADIUS -> Brush.radialGradient(
            *Array(MASK_STOPS + 1) { i -> (i.toFloat() / MASK_STOPS).let { stop(it, it) } },
            center = centre,
            radius = hypot(width / 2f, height / 2f).coerceAtLeast(1f),
        )
        MaskShape.ANGLE -> {
            // A sweep starts at 3 o'clock and turns clockwise (down first); xfade's angle is
            // 0 straight down and grows towards the right, jumping from -PI to PI straight up.
            fun angle(f: Float): Float {
                val turned = 2f * PI.toFloat() * f
                return if (f < UP) PI.toFloat() / 2f - turned else 5f * PI.toFloat() / 2f - turned
            }
            val stops = ArrayList<Pair<Float, Color>>(SWEEP_STOPS + 3)
            for (i in 0..SWEEP_STOPS) {
                val f = i.toFloat() / SWEEP_STOPS
                if (f >= UP && stops.none { it.first >= UP }) {
                    stops += stop(UP - JUMP, angle(UP - JUMP))
                    stops += stop(UP + JUMP, angle(UP + JUMP))
                }
                if (kotlin.math.abs(f - UP) > JUMP) stops += stop(f, angle(f))
            }
            Brush.sweepGradient(*stops.toTypedArray(), center = centre)
        }
        MaskShape.NONE -> Brush.horizontalGradient(listOf(Color.Black, Color.Black))
    }
}

/** Stops of a straight or round soft edge (it is smooth over a large part of the frame). */
private const val MASK_STOPS = 24

/** Stops of the radial wipe's edge, which is one radian wide. */
private const val SWEEP_STOPS = 120

/** Where straight up is along a sweep, and how close to it the angle's jump is put. */
private const val UP = 0.75f
private const val JUMP = 0.0005f

/**
 * Two layers (the clip going out, then the one coming in, each a full-frame
 * box) drawn as a [kind] transition, for the preview and the tiles alike. A
 * kind told as layers (core.transition's TransitionRecipe) is drawn by its
 * shaders where the device has them (see [gpuTransitions]).
 */
@Composable
fun BoxScope.TransitionLayers(
    kind: TransitionKind?,
    progress: () -> Float,
    outgoing: @Composable BoxScope.() -> Unit,
    incoming: (@Composable BoxScope.() -> Unit)?,
) {
    val recipe = kind?.recipe
    if (kind != null && recipe != null && gpuTransitions && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RecipeLayers(kind, recipe, progress, outgoing, incoming)
        return
    }
    val current = { kind }
    if (kind?.whiteBehind == true) Box(modifier = Modifier.matchParentSize().background(Color.White))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .matchParentSize()
            .zIndex(if (kind?.outgoingOnTop == true) 1f else 0f)
            .transitionPart(current, progress, incoming = false)
            .background(Color.Black),
        content = outgoing,
    )
    if (incoming != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .zIndex(0.5f)
                .transitionPart(current, progress, incoming = true)
                .background(Color.Black),
            content = incoming,
        )
    }
}

/**
 * A transition told as layers: what shows behind, the two clips each through
 * its own shader (in the recipe's order), the light over them through one
 * more. A layer whose shader the device refuses is cross-faded instead.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun BoxScope.RecipeLayers(
    kind: TransitionKind,
    recipe: TransitionRecipe,
    progress: () -> Float,
    outgoing: @Composable BoxScope.() -> Unit,
    incoming: (@Composable BoxScope.() -> Unit)?,
) {
    Box(modifier = Modifier.matchParentSize().background(if (recipe.backdrop == Rgb.WHITE) Color.White else Color.Black))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .matchParentSize()
            .zIndex(if (recipe.outgoingOnTop) 1f else 0f)
            .recipePart(kind, incoming = false, progress)
            .background(Color.Black),
        content = outgoing,
    )
    if (incoming != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .zIndex(0.5f)
                .recipePart(kind, incoming = true, progress)
                .background(Color.Black),
            content = incoming,
        )
    }
    if (recipe.overlays.isNotEmpty()) {
        val light = remember(kind) { TransitionShaders.light(kind) }
        if (light != null) {
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(2f)
                    .drawBehind {
                        if (size.width <= 0f || size.height <= 0f) return@drawBehind
                        light.setTransition(size.width, size.height, progress())
                        drawRect(ShaderBrush(light))
                    },
            )
        }
    }
}

/**
 * Draws this layer as the clip going out ([incoming] false) or coming in of a
 * [kind] transition told as layers, through its shader, [progress] read while
 * drawing (a running transition never recomposes). A clip shown as it is
 * needs none; one whose shader the device refuses is cross-faded.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.recipePart(kind: TransitionKind, incoming: Boolean, progress: () -> Float): Modifier = composed {
    val recipe = remember(kind) { kind.recipe }
    val part = if (incoming) recipe?.incoming else recipe?.outgoing
    if (part == null || part.plain) return@composed Modifier
    val shader = remember(kind, incoming) { TransitionShaders.part(kind, incoming) }
    if (shader == null) {
        Modifier.transitionPart({ kind }, progress, incoming)
    } else {
        Modifier.graphicsLayer {
            renderEffect = if (size.width > 0f && size.height > 0f) {
                shader.layerEffect(size.width, size.height, progress()).asComposeRenderEffect()
            } else {
                null
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Names
// ---------------------------------------------------------------------------

/** Transitions grouped as the panel shows them, as CapCut groups its own. */
enum class TransitionFamily(@StringRes val labelRes: Int, val kinds: List<TransitionKind>) {
    BASIC(
        R.string.transition_family_basic,
        listOf(
            TransitionKind.FADE, TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE,
            TransitionKind.DISSOLVE, TransitionKind.FADE_GRAYS, TransitionKind.OVERLAP_CUT,
        ),
    ),
    CAMERA(
        R.string.transition_family_camera,
        listOf(
            TransitionKind.ZOOM_IN, TransitionKind.ZOOM_OUT, TransitionKind.PULL_IN, TransitionKind.PULL_OUT,
            TransitionKind.WHIP_LEFT, TransitionKind.WHIP_RIGHT, TransitionKind.WHIP_UP, TransitionKind.WHIP_DOWN,
            TransitionKind.SPIN_CW, TransitionKind.SPIN_CCW, TransitionKind.ROLL, TransitionKind.SHAKE, TransitionKind.IMPACT_SHAKE,
            TransitionKind.SWIRL,
        ),
    ),
    MOTION(
        R.string.transition_family_motion,
        listOf(
            TransitionKind.PENDULUM_1, TransitionKind.PENDULUM_2, TransitionKind.BOUNCE,
            TransitionKind.REBOUND_1, TransitionKind.REBOUND_2, TransitionKind.SPRING,
        ),
    ),
    SLIDE(
        R.string.transition_family_slide,
        listOf(
            TransitionKind.SLIDE_LEFT, TransitionKind.SLIDE_RIGHT, TransitionKind.SLIDE_UP, TransitionKind.SLIDE_DOWN,
            TransitionKind.COVER_LEFT, TransitionKind.COVER_RIGHT, TransitionKind.COVER_UP, TransitionKind.COVER_DOWN,
            TransitionKind.REVEAL_LEFT, TransitionKind.REVEAL_RIGHT, TransitionKind.REVEAL_UP, TransitionKind.REVEAL_DOWN,
            TransitionKind.SPLIT_H, TransitionKind.SPLIT_V, TransitionKind.SQUEEZE_H, TransitionKind.SQUEEZE_V,
        ),
    ),
    WIPE(
        R.string.transition_family_wipe,
        listOf(
            TransitionKind.WIPE_LEFT, TransitionKind.WIPE_RIGHT, TransitionKind.WIPE_UP, TransitionKind.WIPE_DOWN,
            TransitionKind.SMOOTH_LEFT, TransitionKind.SMOOTH_RIGHT, TransitionKind.SMOOTH_UP, TransitionKind.SMOOTH_DOWN,
            TransitionKind.WIPE_TL, TransitionKind.WIPE_TR, TransitionKind.WIPE_BL, TransitionKind.WIPE_BR,
            TransitionKind.DIAG_TL, TransitionKind.DIAG_TR, TransitionKind.DIAG_BL, TransitionKind.DIAG_BR,
        ),
    ),
    LIGHT(
        R.string.transition_family_light,
        listOf(TransitionKind.FLASH, TransitionKind.LIGHT_LEAK, TransitionKind.FILM_BURN, TransitionKind.GLOW, TransitionKind.LENS_FLARE),
    ),
    GLITCH(
        R.string.transition_family_glitch,
        listOf(
            TransitionKind.TECHNICAL_DIFFICULTIES,
            TransitionKind.GLITCH, TransitionKind.RGB_SPLIT, TransitionKind.WAVE, TransitionKind.TV_NOISE, TransitionKind.PIXELATE,
        ),
    ),
    BLUR(
        R.string.transition_family_blur,
        listOf(TransitionKind.ZOOM_BLUR, TransitionKind.MOTION_BLUR, TransitionKind.SPIN_BLUR, TransitionKind.DEFOCUS),
    ),
    SHAPE(
        R.string.transition_family_shape,
        listOf(
            TransitionKind.CIRCLE_OPEN, TransitionKind.RECT_CROP, TransitionKind.RADIAL, TransitionKind.DIAMOND,
            TransitionKind.HEXAGON, TransitionKind.STAR, TransitionKind.STRIPES, TransitionKind.CHECKER,
            TransitionKind.PAPER_TEAR, TransitionKind.BRUSH,
        ),
    ),
    MASK(
        R.string.transition_family_mask,
        listOf(
            TransitionKind.VERT_OPEN, TransitionKind.VERT_CLOSE, TransitionKind.HORZ_OPEN, TransitionKind.HORZ_CLOSE,
            TransitionKind.CIRCLE_CLOSE, TransitionKind.CIRCLE_CROP, TransitionKind.DIAGONAL_SPLIT, TransitionKind.LUMA_FADE,
        ),
    ),
    THREE_D(
        R.string.transition_family_3d,
        listOf(
            TransitionKind.PUNCH, TransitionKind.PUNCH_BACK,
            TransitionKind.CUBE_LEFT, TransitionKind.CUBE_RIGHT, TransitionKind.FLIP_H, TransitionKind.FLIP_V, TransitionKind.PAGE_TURN,
        ),
    ),
    SOCIAL(
        R.string.transition_family_social,
        listOf(TransitionKind.SWIPE_UP, TransitionKind.SWIPE_DOWN, TransitionKind.HEARTS),
    ),

    /** The app's own transition effects, laid over a cut (see Transition.effect). */
    MAGIC(R.string.transition_family_magic, emptyList()),
    ;

    companion object {
        fun of(transition: Transition?): TransitionFamily =
            if (transition?.isEffect == true) MAGIC else entries.firstOrNull { transition?.kind in it.kinds } ?: BASIC
    }
}

/** The name of [kind], as the panel shows it. */
@StringRes
fun transitionLabel(kind: TransitionKind): Int = when (kind) {
    TransitionKind.FADE -> R.string.transition_fade
    TransitionKind.FADE_BLACK -> R.string.transition_fade_black
    TransitionKind.FADE_WHITE -> R.string.transition_fade_white
    TransitionKind.WIPE_LEFT -> R.string.transition_wipe_left
    TransitionKind.WIPE_RIGHT -> R.string.transition_wipe_right
    TransitionKind.WIPE_UP -> R.string.transition_wipe_up
    TransitionKind.WIPE_DOWN -> R.string.transition_wipe_down
    TransitionKind.SLIDE_LEFT -> R.string.transition_slide_left
    TransitionKind.SLIDE_RIGHT -> R.string.transition_slide_right
    TransitionKind.SLIDE_UP -> R.string.transition_slide_up
    TransitionKind.SLIDE_DOWN -> R.string.transition_slide_down
    TransitionKind.COVER_LEFT -> R.string.transition_cover_left
    TransitionKind.COVER_RIGHT -> R.string.transition_cover_right
    TransitionKind.REVEAL_LEFT -> R.string.transition_reveal_left
    TransitionKind.REVEAL_RIGHT -> R.string.transition_reveal_right
    TransitionKind.SMOOTH_LEFT -> R.string.transition_smooth_left
    TransitionKind.SMOOTH_RIGHT -> R.string.transition_smooth_right
    TransitionKind.CIRCLE_OPEN -> R.string.transition_circle_open
    TransitionKind.CIRCLE_CLOSE -> R.string.transition_circle_close
    TransitionKind.CIRCLE_CROP -> R.string.transition_circle_crop
    TransitionKind.RECT_CROP -> R.string.transition_rect_crop
    TransitionKind.VERT_OPEN -> R.string.transition_vert_open
    TransitionKind.HORZ_OPEN -> R.string.transition_horz_open
    TransitionKind.RADIAL -> R.string.transition_radial
    TransitionKind.ZOOM_IN -> R.string.transition_zoom_in
    TransitionKind.DISSOLVE -> R.string.transition_dissolve
    TransitionKind.FADE_GRAYS -> R.string.transition_fade_grays
    TransitionKind.OVERLAP_CUT -> R.string.transition_overlap_cut
    TransitionKind.ZOOM_OUT -> R.string.transition_zoom_out
    TransitionKind.PULL_IN -> R.string.transition_pull_in
    TransitionKind.PULL_OUT -> R.string.transition_pull_out
    TransitionKind.WHIP_LEFT -> R.string.transition_whip_left
    TransitionKind.WHIP_RIGHT -> R.string.transition_whip_right
    TransitionKind.WHIP_UP -> R.string.transition_whip_up
    TransitionKind.WHIP_DOWN -> R.string.transition_whip_down
    TransitionKind.SPIN_CW -> R.string.transition_spin_cw
    TransitionKind.SPIN_CCW -> R.string.transition_spin_ccw
    TransitionKind.SHAKE -> R.string.transition_shake
    TransitionKind.SWIRL -> R.string.transition_swirl
    TransitionKind.ROLL -> R.string.transition_roll
    TransitionKind.COVER_UP -> R.string.transition_cover_up
    TransitionKind.COVER_DOWN -> R.string.transition_cover_down
    TransitionKind.REVEAL_UP -> R.string.transition_reveal_up
    TransitionKind.REVEAL_DOWN -> R.string.transition_reveal_down
    TransitionKind.SPLIT_H -> R.string.transition_split_h
    TransitionKind.SPLIT_V -> R.string.transition_split_v
    TransitionKind.SQUEEZE_H -> R.string.transition_squeeze_h
    TransitionKind.SQUEEZE_V -> R.string.transition_squeeze_v
    TransitionKind.SMOOTH_UP -> R.string.transition_smooth_up
    TransitionKind.SMOOTH_DOWN -> R.string.transition_smooth_down
    TransitionKind.WIPE_TL -> R.string.transition_wipe_tl
    TransitionKind.WIPE_TR -> R.string.transition_wipe_tr
    TransitionKind.WIPE_BL -> R.string.transition_wipe_bl
    TransitionKind.WIPE_BR -> R.string.transition_wipe_br
    TransitionKind.DIAG_TL -> R.string.transition_diag_tl
    TransitionKind.DIAG_TR -> R.string.transition_diag_tr
    TransitionKind.DIAG_BL -> R.string.transition_diag_bl
    TransitionKind.DIAG_BR -> R.string.transition_diag_br
    TransitionKind.FLASH -> R.string.transition_flash
    TransitionKind.LIGHT_LEAK -> R.string.transition_light_leak
    TransitionKind.FILM_BURN -> R.string.transition_film_burn
    TransitionKind.GLOW -> R.string.transition_glow
    TransitionKind.LENS_FLARE -> R.string.transition_lens_flare
    TransitionKind.GLITCH -> R.string.transition_glitch
    TransitionKind.RGB_SPLIT -> R.string.transition_rgb_split
    TransitionKind.WAVE -> R.string.transition_wave
    TransitionKind.TV_NOISE -> R.string.transition_tv_noise
    TransitionKind.PIXELATE -> R.string.transition_pixelate
    TransitionKind.ZOOM_BLUR -> R.string.transition_zoom_blur
    TransitionKind.MOTION_BLUR -> R.string.transition_motion_blur
    TransitionKind.SPIN_BLUR -> R.string.transition_spin_blur
    TransitionKind.DEFOCUS -> R.string.transition_defocus
    TransitionKind.DIAMOND -> R.string.transition_diamond
    TransitionKind.HEXAGON -> R.string.transition_hexagon
    TransitionKind.STAR -> R.string.transition_star
    TransitionKind.STRIPES -> R.string.transition_stripes
    TransitionKind.CHECKER -> R.string.transition_checker
    TransitionKind.PAPER_TEAR -> R.string.transition_paper_tear
    TransitionKind.BRUSH -> R.string.transition_brush
    TransitionKind.VERT_CLOSE -> R.string.transition_vert_close
    TransitionKind.HORZ_CLOSE -> R.string.transition_horz_close
    TransitionKind.DIAGONAL_SPLIT -> R.string.transition_diagonal_split
    TransitionKind.LUMA_FADE -> R.string.transition_luma_fade
    TransitionKind.CUBE_LEFT -> R.string.transition_cube_left
    TransitionKind.CUBE_RIGHT -> R.string.transition_cube_right
    TransitionKind.FLIP_H -> R.string.transition_flip_h
    TransitionKind.FLIP_V -> R.string.transition_flip_v
    TransitionKind.PAGE_TURN -> R.string.transition_page_turn
    TransitionKind.SWIPE_UP -> R.string.transition_swipe_up
    TransitionKind.SWIPE_DOWN -> R.string.transition_swipe_down
    TransitionKind.HEARTS -> R.string.transition_hearts
    TransitionKind.PENDULUM_1 -> R.string.transition_pendulum_1
    TransitionKind.PENDULUM_2 -> R.string.transition_pendulum_2
    TransitionKind.BOUNCE -> R.string.transition_bounce
    TransitionKind.REBOUND_1 -> R.string.transition_rebound_1
    TransitionKind.REBOUND_2 -> R.string.transition_rebound_2
    TransitionKind.SPRING -> R.string.transition_spring
    TransitionKind.IMPACT_SHAKE -> R.string.transition_impact_shake
    TransitionKind.PUNCH -> R.string.transition_punch
    TransitionKind.PUNCH_BACK -> R.string.transition_punch_back
    TransitionKind.TECHNICAL_DIFFICULTIES -> R.string.transition_technical_difficulties
}

// ---------------------------------------------------------------------------
// Panel
// ---------------------------------------------------------------------------

/**
 * Picks how the selected clip gives way to the next one ([transition], null
 * for a plain cut) and how long that takes. [hasNext] is false for the last
 * clip, which has nothing to go into; [effectiveMs] is how long the transition
 * really lasts (shorter when a clip is too short for it), [limitMs] the
 * longest these two clips allow (half of the shorter, as CapCut has it).
 * [onPlay] plays the transition in the preview.
 */
@Composable
internal fun TransitionContent(
    transition: Transition?,
    hasNext: Boolean,
    effectiveMs: Long,
    limitMs: Long,
    onTransition: (Transition?) -> Unit,
    onApplyToAll: () -> Unit,
    onPlay: () -> Unit,
) {
    var family by rememberSaveable { mutableStateOf(TransitionFamily.of(transition)) }
    val effectScope = rememberCoroutineScope()
    val effectNames = effectLabels()
    val durationMs = transition?.durationMs ?: Transition.DEFAULT_MS
    val motion = MaterialTheme.motionScheme
    // Every tile plays its transition at the length chosen, so that the slider is seen at once.
    val loop = rememberTransitionLoop(durationMs.coerceAtMost(Transition.MAX_MS))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelHint(stringResource(if (hasNext) R.string.transition_hint else R.string.transition_last_clip))
        ConnectedChoiceGroup(
            options = TransitionFamily.entries,
            selected = family,
            label = { stringResource(it.labelRes) },
            onSelect = { family = it },
            enabled = hasNext,
        )
        // The tiles of one family, two rows that scroll sideways; another family fades in.
        Crossfade(targetState = family, animationSpec = motion.defaultEffectsSpec(), label = "transitionFamily") { shown ->
            val tiles = buildList<TransitionChoice> {
                add(TransitionChoice.None)
                if (shown == TransitionFamily.MAGIC) {
                    EffectLibrary.transitions.forEach { add(TransitionChoice.Effect(it)) }
                } else {
                    shown.kinds.forEach { add(TransitionChoice.Kind(it)) }
                }
            }
            fun TransitionChoice.chosen(): Boolean = when (this) {
                TransitionChoice.None -> transition == null
                is TransitionChoice.Kind -> transition?.isEffect != true && transition?.kind == kind
                is TransitionChoice.Effect -> transition?.effect == effect.name
            }
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // The chosen tile in sight when the family opens.
            LaunchedEffect(shown) {
                val index = tiles.indexOfFirst { it.chosen() }
                val column = if (index < 0) 0 else index / TILE_ROWS
                scroll.animateScrollTo(with(density) { ((TILE_WIDTH + 12.dp + TILE_GAP) * column).roundToPx() }, motion.defaultSpatialSpec())
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .horizontalScroll(scroll),
            ) {
                tiles.chunked(TILE_ROWS).forEach { column ->
                    Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                        column.forEach { choice ->
                            when (choice) {
                                TransitionChoice.None -> TransitionTile(
                                    kind = null,
                                    label = stringResource(R.string.transition_none),
                                    selected = choice.chosen(),
                                    enabled = hasNext,
                                    loop = loop,
                                    onClick = { onTransition(null) },
                                )
                                is TransitionChoice.Kind -> TransitionTile(
                                    kind = choice.kind,
                                    label = stringResource(transitionLabel(choice.kind)),
                                    selected = choice.chosen(),
                                    enabled = hasNext,
                                    loop = loop,
                                    onClick = {
                                        val kind = choice.kind
                                        // A fist, a TV break's parts are made ready for the preview and the export.
                                        val parts = listOfNotNull(kind.companionEffect) + (if (kind.tvBreak) listOf(TV_INTRO, TV_CARD, TV_OUTRO) else emptyList())
                                        if (parts.isNotEmpty()) effectScope.launch(Dispatchers.IO) { parts.forEach { EffectLibrary.ensure(it) } }
                                        // A TV break starts at its own length; going from one to another kind, the length
                                        // keeps within the new kind's bounds.
                                        val sameBounds = transition?.isEffect == false && transition.kind.tvBreak == kind.tvBreak
                                        val ms = if (sameBounds) durationMs else kind.defaultMs
                                        // The size and the sounds chosen stay as they were.
                                        val kept = transition ?: Transition()
                                        onTransition(
                                            Transition(
                                                kind, ms.coerceIn(kind.minMs, kind.maxMs), size = transition?.size ?: Transition.MAX_SIZE,
                                                sound = kept.sound, soundVolume = kept.soundVolume, music = kept.music, musicVolume = kept.musicVolume,
                                            ),
                                        )
                                    },
                                )
                                is TransitionChoice.Effect -> EffectTransitionTile(
                                    effect = choice.effect,
                                    label = effectNames[choice.effect.name] ?: choice.effect.name,
                                    selected = choice.chosen(),
                                    enabled = hasNext,
                                    onClick = {
                                        // Its file is made ready for the preview and the export.
                                        effectScope.launch(Dispatchers.IO) { EffectLibrary.ensure(choice.effect.name) }
                                        onTransition(Transition(durationMs = durationMs, effect = choice.effect.name))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        // A punch's blow, a TV break's channel changing and its tune: each on or off, as loud as wanted.
        if (transition != null && !transition.isEffect && hasNext && (transition.kind.companionEffect != null || transition.kind.tvBreak)) {
            TransitionSounds(transition, onTransition)
        }
        val effect = EffectLibrary.info(transition?.effect)
        val fixedMs = transition?.takeIf { !it.isEffect }?.kind?.fixedMs
        if (effect != null && hasNext) {
            // An effect has its own length: nothing to set, the video keeps its length.
            PanelCard {
                PanelHint(stringResource(R.string.transition_effect_hint, decimal(effect.lengthMs / 1000f)))
            }
        } else if (fixedMs != null && transition != null && hasNext) {
            // A punch keeps the length of its blow; what can be set is how large the fist is.
            PanelCard {
                PanelHint(stringResource(R.string.transition_punch_hint, decimal(fixedMs / 1000f)))
                ValueSlider(
                    label = stringResource(R.string.transition_fist_size),
                    valueText = stringResource(R.string.value_percent, Math.round(transition.size * 100f)),
                    value = transition.size.coerceIn(Transition.MIN_SIZE, Transition.MAX_SIZE),
                    onValueChange = { size ->
                        val stepped = (Math.round(size * 20f) / 20f).coerceIn(Transition.MIN_SIZE, Transition.MAX_SIZE)
                        if (stepped != transition.size) onTransition(transition.copy(size = stepped))
                    },
                    valueRange = Transition.MIN_SIZE..Transition.MAX_SIZE,
                    icon = Icons.Rounded.OpenInFull,
                    defaultValue = Transition.MAX_SIZE,
                    startLabel = stringResource(R.string.value_percent, Math.round(Transition.MIN_SIZE * 100f)),
                    endLabel = stringResource(R.string.value_percent, Math.round(Transition.MAX_SIZE * 100f)),
                    enabled = true,
                )
                AnimatedVisibility(visible = effectiveMs in 1 until fixedMs) {
                    PanelHint(stringResource(R.string.transition_shortened, decimal(effectiveMs / 1000f)))
                }
                AnimatedVisibility(visible = effectiveMs <= 0L) {
                    PanelHint(stringResource(R.string.transition_too_short))
                }
                AnimatedVisibility(visible = gpuMissing) {
                    PanelHint(stringResource(R.string.transition_preview_fade))
                }
            }
        } else PanelCard {
            // A TV break has bounds of its own (long enough for its card, and as long as its tune).
            val kind = transition?.takeIf { !it.isEffect }?.kind
            val leastMs = if (kind?.tvBreak == true) kind.minMs else Transition.MIN_MS
            val mostMs = kind?.maxMs ?: Transition.MAX_MS
            if (kind?.tvBreak == true && hasNext) PanelHint(stringResource(R.string.transition_tv_hint))
            // The slider reaches as far as these two clips allow (half of the shorter one).
            val roomMs = limitMs.coerceAtMost(mostMs)
            val room = roomMs >= leastMs
            val endMs = if (room) roomMs.coerceAtLeast(leastMs + 100L) else mostMs
            ValueSlider(
                label = stringResource(R.string.transition_duration),
                valueText = stringResource(R.string.value_seconds_decimal, decimal((if (transition != null && room) effectiveMs.coerceAtLeast(leastMs) else durationMs) / 1000f)),
                value = (durationMs.coerceIn(leastMs, endMs)) / 1000f,
                onValueChange = { seconds ->
                    val ms = (Math.round(seconds * 10f) * 100L).coerceIn(leastMs, endMs)
                    transition?.let { onTransition(it.copy(durationMs = ms)) }
                },
                valueRange = leastMs / 1000f..endMs / 1000f,
                icon = Icons.Rounded.Timer,
                defaultValue = (kind?.defaultMs ?: Transition.DEFAULT_MS).coerceIn(leastMs, endMs) / 1000f,
                startLabel = stringResource(R.string.value_seconds_decimal, decimal(leastMs / 1000f)),
                endLabel = stringResource(R.string.value_seconds_decimal, decimal(endMs / 1000f)),
                enabled = transition != null && hasNext && room,
            )
            AnimatedVisibility(visible = transition != null && !transition.isEffect && hasNext && (!room || effectiveMs < durationMs)) {
                PanelHint(
                    if (effectiveMs <= 0L || !room) {
                        stringResource(R.string.transition_too_short)
                    } else {
                        stringResource(R.string.transition_shortened, decimal(effectiveMs / 1000f))
                    },
                )
            }
            AnimatedVisibility(visible = gpuMissing && transition != null && !transition.isEffect && transition.kind.recipe != null && hasNext) {
                PanelHint(stringResource(R.string.transition_preview_fade))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onPlay,
                enabled = hasNext && transition != null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.transition_play), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = onApplyToAll,
                enabled = hasNext,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(if (transition == null) R.string.transition_none_to_all else R.string.transition_apply_all),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** True on a device that draws the transitions told as layers as cross-fades in the preview. */
private val gpuMissing: Boolean get() = !gpuTransitions

/** One tile of the panel. */
private sealed interface TransitionChoice {
    data object None : TransitionChoice
    data class Kind(val kind: TransitionKind) : TransitionChoice
    data class Effect(val effect: EffectInfo) : TransitionChoice
}

/** Rows of tiles, and the space between tiles. */
private const val TILE_ROWS = 2
private val TILE_GAP = 4.dp

/** A loop of 0..1 that a tile's transition follows: a pause on each clip around a transition of [transitionMs]. */
@Composable
private fun rememberTransitionLoop(transitionMs: Long): () -> Float {
    val total = TILE_HOLD_MS * 2 + transitionMs.toInt()
    val infinite = rememberInfiniteTransition(label = "transitionTiles")
    val phase: State<Float> = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = total, easing = LinearEasing), RepeatMode.Restart),
        label = "transitionPhase",
    )
    return remember(phase, total, transitionMs) {
        { ((phase.value * total - TILE_HOLD_MS) / transitionMs.coerceAtLeast(1L)).coerceIn(0f, 1f) }
    }
}

/** How long a tile rests on each of its two pictures. */
private const val TILE_HOLD_MS = 450

/**
 * One transition as a tile: two little pictures playing it in a loop, its name
 * below. Chosen, its frame rounds off and a ring and a check pop in; it
 * squishes under the finger.
 */
@Composable
private fun TransitionTile(
    kind: TransitionKind?,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    loop: () -> Float,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (selected) 22.dp else 12.dp, motion.defaultSpatialSpec(), label = "tileRadius")
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, motion.defaultEffectsSpec(), label = "tileRing")
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, motion.fastSpatialSpec(), label = "tileCheck")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TILE_WIDTH + 12.dp)
            .springPress(interaction, pressedScale = 0.9f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(vertical = 4.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(TILE_WIDTH, TILE_HEIGHT)
                    .border(3.dp, ring, RoundedCornerShape(radius + 4.dp))
                    .padding(5.dp)
                    .clip(RoundedCornerShape(radius)),
            ) {
                if (kind == null) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(scheme.surfaceContainerHighest)) {
                        Icon(Icons.Rounded.Block, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                } else {
                    // A punch plays at the length of its blow, a TV break at its own, whatever the slider says.
                    val fixed = kind.fixedMs ?: if (kind.tvBreak) kind.defaultMs else null
                    val progress = if (fixed != null) rememberTransitionLoop(fixed) else loop
                    TransitionLayers(
                        kind = kind,
                        progress = progress,
                        outgoing = { TileScene(first = true) },
                        incoming = { TileScene(first = false) },
                    )
                    kind.companionEffect?.let { name -> TileCompanion(kind, name, progress, fixed ?: Transition.DEFAULT_MS) }
                    if (kind.tvBreak) TileTvBreak(progress, fixed ?: kind.defaultMs)
                }
            }
            // The check pops in and out with a spring.
            if (checkScale > 0f) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkScale.coerceIn(0f, 1f)
                        }
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A transition effect as a tile: the two little pictures cut from one to the
 * other under the effect, screened over them as on the video, in a loop. Chosen,
 * it rounds off and a ring and a check pop in, as the other tiles.
 */
@Composable
private fun EffectTransitionTile(
    effect: EffectInfo,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (selected) 22.dp else 12.dp, motion.defaultSpatialSpec(), label = "tileRadius")
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, motion.defaultEffectsSpec(), label = "tileRing")
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, motion.fastSpatialSpec(), label = "tileCheck")
    val sheet = rememberSheet(effect.name)
    val image = remember(sheet) { sheet?.bitmap?.asImageBitmap() }
    val count = sheet?.about?.frames ?: 1
    // At the effect's own speed, then a short rest on the second picture.
    val playMs = (sheet?.lengthMs ?: effect.lengthMs).toInt().coerceAtLeast(1)
    val loop = rememberInfiniteTransition(label = "effectTile")
    val phase = loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(playMs + EFFECT_TILE_REST_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "effectTilePhase",
    )
    fun index(): Int = (phase.value * (playMs + EFFECT_TILE_REST_MS) / playMs * count).toInt().coerceIn(0, count - 1)
    // The cut falls under the effect's brightest frame.
    val cutIndex = if (effect.lengthMs > 0) (effect.cutMs * count / effect.lengthMs).toInt() else 0
    val before by remember(count, cutIndex) { derivedStateOf { index() < cutIndex } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TILE_WIDTH + 12.dp)
            .springPress(interaction, pressedScale = 0.9f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(vertical = 4.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(TILE_WIDTH, TILE_HEIGHT)
                    .border(3.dp, ring, RoundedCornerShape(radius + 4.dp))
                    .padding(5.dp)
                    .clip(RoundedCornerShape(radius)),
            ) {
                TileScene(first = before)
                if (sheet != null && image != null) {
                    Canvas(modifier = Modifier.matchParentSize().clipToBounds().graphicsLayer { blendMode = BlendMode.Screen }) {
                        drawSheetFrame(sheet, image, index(), null)
                    }
                }
            }
            if (checkScale > 0f) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkScale.coerceIn(0f, 1f)
                        }
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The effect a [kind] lays over its clips (a punch's fist, [name]) on a tile,
 * from its small frames, in step with the tile's [progress] through
 * [lengthMs]: its blow on the moment the clips swap, as on the video.
 */
@Composable
private fun BoxScope.TileCompanion(kind: TransitionKind, name: String, progress: () -> Float, lengthMs: Long) {
    val sheet = rememberSheet(name)
    val image = remember(sheet) { sheet?.bitmap?.asImageBitmap() }
    val cutMs = EffectLibrary.info(name)?.cutMs ?: 0L
    Canvas(modifier = Modifier.matchParentSize().zIndex(3f).clipToBounds()) {
        val loaded = sheet ?: return@Canvas
        val bitmap = image ?: return@Canvas
        val p = progress()
        // Nothing before the transition starts or once it is over: the tile rests on a picture.
        if (p <= 0f || p >= 1f) return@Canvas
        val fileMs = cutMs + ((p - kind.companionSwitch) * lengthMs).toLong()
        if (fileMs < 0L || fileMs >= loaded.lengthMs) return@Canvas
        drawSheetFrame(loaded, bitmap, (fileMs * loaded.about.fps / 1000L).toInt(), null)
    }
}

/**
 * A TV break on a tile, from its parts' small frames, in step with the tile's
 * [progress] through [lengthMs]: the channel changing, the card, the channel
 * changing again, laid as the video lays them (see transitionEffects).
 */
@Composable
private fun BoxScope.TileTvBreak(progress: () -> Float, lengthMs: Long) {
    val intro = rememberSheet(TV_INTRO)
    val card = rememberSheet(TV_CARD)
    val outro = rememberSheet(TV_OUTRO)
    val introImage = remember(intro) { intro?.bitmap?.asImageBitmap() }
    val cardImage = remember(card) { card?.bitmap?.asImageBitmap() }
    val outroImage = remember(outro) { outro?.bitmap?.asImageBitmap() }
    Canvas(modifier = Modifier.matchParentSize().zIndex(3f).clipToBounds()) {
        val p = progress()
        if (p <= 0f || p >= 1f) return@Canvas
        val ms = (p * lengthMs).toLong()
        if (card != null && cardImage != null && ms >= TV_BREAK_CARD_IN_MS && ms < lengthMs - TV_BREAK_CARD_IN_MS) {
            val frame = ((ms - TV_BREAK_CARD_IN_MS) * card.about.fps / 1000L % card.about.frames).toInt()
            drawSheetFrame(card, cardImage, frame, null)
        }
        if (intro != null && introImage != null && ms < intro.lengthMs) {
            drawSheetFrame(intro, introImage, (ms * intro.about.fps / 1000L).toInt(), null)
        }
        val outroFrom = lengthMs - (outro?.lengthMs ?: 0L)
        if (outro != null && outroImage != null && ms >= outroFrom) {
            drawSheetFrame(outro, outroImage, ((ms - outroFrom) * outro.about.fps / 1000L).toInt(), null)
        }
    }
}

/** How long a transition effect's tile rests on its second picture before it plays again. */
private const val EFFECT_TILE_REST_MS = 700

/** One of a tile's two pictures: a landscape in the primary tones, then a flower in the tertiary ones. */
@Composable
private fun BoxScope.TileScene(first: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val colors = if (first) listOf(scheme.primary, scheme.primaryContainer) else listOf(scheme.tertiaryContainer, scheme.tertiary)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.matchParentSize().background(Brush.linearGradient(colors)),
    ) {
        Icon(
            imageVector = if (first) Icons.Rounded.Landscape else Icons.Rounded.LocalFlorist,
            contentDescription = null,
            tint = if (first) scheme.onPrimary else scheme.onTertiary,
            modifier = Modifier.size(24.dp),
        )
    }
}

private val TILE_WIDTH = 80.dp
private val TILE_HEIGHT = 58.dp

/**
 * The sounds a transition brings: a punch's blow, or a TV break's channel
 * changing and its tune apart. Each can be switched off, and its volume set
 * (heard in the preview as in the export).
 */
@Composable
private fun TransitionSounds(transition: Transition, onTransition: (Transition) -> Unit) {
    val tv = transition.kind.tvBreak
    TransitionSound(
        icon = if (tv) Icons.Rounded.Tv else Icons.Rounded.SportsMma,
        title = stringResource(if (tv) R.string.transition_sound_channel else R.string.transition_sound_blow),
        on = transition.sound,
        volume = transition.soundVolume,
        onOn = { onTransition(transition.copy(sound = it)) },
        onVolume = { onTransition(transition.copy(soundVolume = it)) },
    )
    if (tv) {
        TransitionSound(
            icon = Icons.Rounded.MusicNote,
            title = stringResource(R.string.transition_sound_tune),
            on = transition.music,
            volume = transition.musicVolume,
            onOn = { onTransition(transition.copy(music = it)) },
            onVolume = { onTransition(transition.copy(musicVolume = it)) },
        )
    }
}

@Composable
private fun TransitionSound(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    on: Boolean,
    volume: Float,
    onOn: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
) {
    com.cuscus.ffmpeggui.ui.expressive.SwitchCard(icon = icon, title = title, checked = on, onCheckedChange = onOn)
    AnimatedVisibility(visible = on) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.transition_sound_volume),
                valueText = stringResource(R.string.value_percent, Math.round(volume * 100f)),
                value = volume.coerceIn(0f, Transition.MAX_VOLUME),
                onValueChange = { v ->
                    val stepped = (Math.round(v * 20f) / 20f).coerceIn(0f, Transition.MAX_VOLUME)
                    if (stepped != volume) onVolume(stepped)
                },
                valueRange = 0f..Transition.MAX_VOLUME,
                icon = icon,
                defaultValue = 1f,
                startLabel = stringResource(R.string.value_percent, 0),
                endLabel = stringResource(R.string.value_percent, Math.round(Transition.MAX_VOLUME * 100f)),
                enabled = true,
            )
        }
    }
}
