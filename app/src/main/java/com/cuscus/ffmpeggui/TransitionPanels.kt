@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.model.TransitionKind
import com.cuscus.ffmpeggui.core.preview.MaskShape
import com.cuscus.ffmpeggui.core.preview.PartLook
import com.cuscus.ffmpeggui.core.preview.maskAlpha
import com.cuscus.ffmpeggui.core.preview.outgoingOnTop
import com.cuscus.ffmpeggui.core.preview.transitionLook
import com.cuscus.ffmpeggui.core.preview.whiteBehind
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.springPress
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
 * box) drawn as a [kind] transition, for the preview and the tiles alike.
 */
@Composable
fun BoxScope.TransitionLayers(
    kind: TransitionKind?,
    progress: () -> Float,
    outgoing: @Composable BoxScope.() -> Unit,
    incoming: (@Composable BoxScope.() -> Unit)?,
) {
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

// ---------------------------------------------------------------------------
// Names
// ---------------------------------------------------------------------------

/** Transitions grouped as the panel shows them. */
enum class TransitionFamily(@StringRes val labelRes: Int, val kinds: List<TransitionKind>) {
    DISSOLVE(R.string.transition_family_dissolve, listOf(TransitionKind.FADE, TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE)),
    SLIDE(
        R.string.transition_family_slide,
        listOf(
            TransitionKind.SLIDE_LEFT, TransitionKind.SLIDE_RIGHT, TransitionKind.SLIDE_UP, TransitionKind.SLIDE_DOWN,
            TransitionKind.COVER_LEFT, TransitionKind.COVER_RIGHT, TransitionKind.REVEAL_LEFT, TransitionKind.REVEAL_RIGHT,
        ),
    ),
    WIPE(
        R.string.transition_family_wipe,
        listOf(
            TransitionKind.WIPE_LEFT, TransitionKind.WIPE_RIGHT, TransitionKind.WIPE_UP, TransitionKind.WIPE_DOWN,
            TransitionKind.SMOOTH_LEFT, TransitionKind.SMOOTH_RIGHT,
        ),
    ),
    SHAPE(
        R.string.transition_family_shape,
        listOf(
            TransitionKind.CIRCLE_OPEN, TransitionKind.CIRCLE_CLOSE, TransitionKind.CIRCLE_CROP, TransitionKind.RECT_CROP,
            TransitionKind.VERT_OPEN, TransitionKind.HORZ_OPEN, TransitionKind.RADIAL, TransitionKind.ZOOM_IN,
        ),
    ),
    ;

    companion object {
        fun of(kind: TransitionKind?): TransitionFamily = entries.firstOrNull { kind in it.kinds } ?: DISSOLVE
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
}

// ---------------------------------------------------------------------------
// Panel
// ---------------------------------------------------------------------------

/**
 * Picks how the selected clip gives way to the next one ([transition], null
 * for a plain cut) and how long that takes. [hasNext] is false for the last
 * clip, which has nothing to go into; [effectiveMs] is how long the transition
 * really lasts (shorter when a clip is too short for it).
 */
@Composable
internal fun TransitionContent(
    transition: Transition?,
    hasNext: Boolean,
    effectiveMs: Long,
    onTransition: (Transition?) -> Unit,
    onApplyToAll: () -> Unit,
) {
    var family by rememberSaveable { mutableStateOf(TransitionFamily.of(transition?.kind)) }
    val durationMs = transition?.durationMs ?: Transition.DEFAULT_MS
    // Every tile plays its transition at the length chosen, so that the slider is seen at once.
    val loop = rememberTransitionLoop(durationMs)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelHint(stringResource(if (hasNext) R.string.transition_hint else R.string.transition_last_clip))
        ConnectedChoiceGroup(
            options = TransitionFamily.entries,
            selected = family,
            label = { stringResource(it.labelRes) },
            onSelect = { family = it },
            enabled = hasNext,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .horizontalScroll(rememberScrollState()),
        ) {
            TransitionTile(
                kind = null,
                label = stringResource(R.string.transition_none),
                selected = transition == null,
                enabled = hasNext,
                loop = loop,
                onClick = { onTransition(null) },
            )
            family.kinds.forEach { kind ->
                TransitionTile(
                    kind = kind,
                    label = stringResource(transitionLabel(kind)),
                    selected = transition?.kind == kind,
                    enabled = hasNext,
                    loop = loop,
                    onClick = { onTransition(Transition(kind, durationMs)) },
                )
            }
        }
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.transition_duration),
                valueText = stringResource(R.string.value_seconds_decimal, decimal(durationMs / 1000f)),
                value = durationMs / 1000f,
                onValueChange = { seconds ->
                    val ms = (Math.round(seconds * 10f) * 100L).coerceIn(Transition.MIN_MS, Transition.MAX_MS)
                    transition?.let { onTransition(it.copy(durationMs = ms)) }
                },
                valueRange = Transition.MIN_MS / 1000f..Transition.MAX_MS / 1000f,
                icon = Icons.Rounded.Timer,
                defaultValue = Transition.DEFAULT_MS / 1000f,
                enabled = transition != null && hasNext,
            )
            AnimatedVisibility(visible = transition != null && hasNext && effectiveMs < durationMs) {
                PanelHint(
                    if (effectiveMs <= 0L) {
                        stringResource(R.string.transition_too_short)
                    } else {
                        stringResource(R.string.transition_shortened, decimal(effectiveMs / 1000f))
                    },
                )
            }
        }
        FilledTonalButton(
            onClick = onApplyToAll,
            enabled = hasNext,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(if (transition == null) R.string.transition_none_to_all else R.string.transition_apply_all))
        }
    }
}

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
                    TransitionLayers(
                        kind = kind,
                        progress = loop,
                        outgoing = { TileScene(first = true) },
                        incoming = { TileScene(first = false) },
                    )
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
