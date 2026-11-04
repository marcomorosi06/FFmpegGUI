@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.cuscus.ffmpeggui.ui.expressive.MorphShape
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The photo editor's long press, which lifts the subject under the finger off
 * the picture as a sticker (see SubjectLifter), as the picture stage shows it:
 * [onPress] gets where the finger pressed (shares of the picture's width and
 * height), null when a press does nothing now (a canvas, the crop, drawing);
 * [onRelease] that the finger left (a lift not finished by then is called
 * off); [working] is where the model is looking while it does; [aborted]
 * where a lift was called off, shown in red ([onAbortShown] when it has);
 * [shown] the subject just lifted, which rises off the picture and settles as
 * its sticker ([onShown] when it has); [note] a word on what happened.
 */
internal class PictureLift(
    val onPress: ((Float, Float) -> Unit)?,
    val onRelease: () -> Unit,
    val working: Offset?,
    val aborted: Offset?,
    val onAbortShown: () -> Unit,
    val shown: LiftedShow?,
    val onShown: () -> Unit,
    val note: LiftNote?,
    val onNoteAction: () -> Unit,
    val onNoteGone: () -> Unit,
)

/** A subject just lifted: its picture, and its box as shares of the picture. */
internal class LiftedShow(val image: ImageBitmap, val left: Float, val top: Float, val width: Float, val height: Float)

/** What the stage says after a long press, with its icon and, for some, an action. */
internal enum class LiftNote(val text: Int, val icon: ImageVector, val action: Int?, val showMs: Long) {
    /** Once: what a long press does. */
    TIP(R.string.lift_tip, Icons.Rounded.TouchApp, null, 6_000L),

    /** The model (EdgeTAM) is not set up: the action goes to the settings. */
    NEEDS_MODEL(R.string.lift_needs_model, Icons.Rounded.AutoAwesome, R.string.lift_set_up, 6_000L),

    /** Nothing clear under the finger. */
    NOTHING(R.string.lift_nothing, Icons.Rounded.SearchOff, null, 4_000L),

    MEMORY(R.string.lift_memory, Icons.Rounded.Memory, null, 5_000L),

    FAILED(R.string.lift_failed, Icons.Rounded.ErrorOutline, null, 4_000L),
}

/** Longest side of the picture a subject is lifted from: sharper than the preview, light enough for the phone. */
internal const val LIFT_PX = 2048

/** Where the photo editor remembers that the long press was explained. */
internal const val LIFT_PREFS = "photo_editor"
internal const val KEY_LIFT_TIP = "lift_tip_shown"

/** The tip waits this long after the editor opens, so that it is not lost among the rest. */
internal const val LIFT_TIP_DELAY_MS = 1_200L

/**
 * Over the picture: while the model looks, a badge where the finger pressed (a
 * turning cookie with a loading indicator, ripples spreading from it as on a
 * pond); called off, the same badge springs into a red clover with a cross,
 * the ripples drawn back in, and goes;
 * done, it pops away while the subject lifted rises off the picture (bigger, a
 * halo of the theme's colour round its outline, a burst of sparks) and settles
 * back into the sticker laid in its place.
 */
@Composable
internal fun LiftEffects(lift: PictureLift, modifier: Modifier = Modifier) {
    // The badge's point, kept while it goes away.
    var badge by remember { mutableStateOf<Offset?>(null) }
    (lift.working ?: lift.aborted)?.let { badge = it }
    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        badge?.let { at ->
            val state = when (at) {
                lift.aborted -> BadgeState.ABORTED
                lift.working -> BadgeState.WORKING
                else -> BadgeState.DONE
            }
            key(at) { LiftBadge(Offset(at.x * w, at.y * h), state, lift.onAbortShown) { badge = null } }
        }
        lift.shown?.let { shown -> key(shown) { LiftRise(shown, w, h, lift.onShown) } }
    }
}

/** What the badge where the finger pressed shows. */
private enum class BadgeState { WORKING, ABORTED, DONE }

/**
 * The badge where the finger pressed. Working: a nine-sided cookie that turns
 * slowly, a loading indicator in it, and ripples spreading from it, each born
 * as the cookie and relaxing into a circle as it goes. Called off: the turn
 * springs to rest, the cookie morphs into a four-leaf clover and turns red, the
 * indicator gives way to a cross, the ripples turn red and are drawn back in, a
 * squash, and it shrinks away ([onAbortShown]). Done: it pops away ([onGone]).
 */
@Composable
private fun LiftBadge(at: Offset, state: BadgeState, onAbortShown: () -> Unit, onGone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    // Once called off it stays red while it goes.
    var aborted by remember { mutableStateOf(false) }
    if (state == BadgeState.ABORTED) aborted = true
    LaunchedEffect(state) {
        when (state) {
            BadgeState.WORKING -> {
                launch { appear.animateTo(1f, motion.defaultSpatialSpec()) }
                launch { burst.animateTo(1f, motion.slowSpatialSpec()) }
                while (true) spin.animateTo(spin.value + 360f, tween(durationMillis = SPIN_MS, easing = LinearEasing))
            }
            BadgeState.ABORTED -> {
                // The turn comes to rest with a spring, on the clover's own angle.
                val rest = ceil(spin.value / 90f) * 90f
                launch { spin.animateTo(rest, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
                launch { burst.animateTo(0f, motion.fastSpatialSpec()) }
                appear.animateTo(1.18f, motion.fastSpatialSpec())
                appear.animateTo(1f, motion.defaultSpatialSpec())
                delay(ABORT_HOLD_MS)
                appear.animateTo(0f, motion.fastSpatialSpec())
                onAbortShown()
            }
            BadgeState.DONE -> {
                launch { burst.animateTo(0f, motion.fastSpatialSpec()) }
                appear.animateTo(0f, motion.fastSpatialSpec())
                onGone()
            }
        }
    }
    // Ripples, as on a pond: each starts as the badge's cookie and relaxes into a
    // circle as it spreads, turning a little and fading.
    val pond = rememberInfiniteTransition(label = "liftPond")
    val wave by pond.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = RIPPLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "liftWave",
    )
    val container by animateColorAsState(if (aborted) scheme.error else scheme.primaryContainer, motion.defaultEffectsSpec(), label = "liftBadgeColor")
    val content by animateColorAsState(if (aborted) scheme.onError else scheme.onPrimaryContainer, motion.defaultEffectsSpec(), label = "liftBadgeContent")
    val ripple by animateColorAsState(if (aborted) scheme.error else scheme.primary, motion.fastEffectsSpec(), label = "liftRippleColor")
    val relax = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val start = 64.dp.toPx()
        val reach = 96.dp.toPx()
        val line = 3.dp.toPx()
        val spread = burst.value.coerceAtLeast(0f)
        for (k in 0 until RIPPLES) {
            val p = (wave + k.toFloat() / RIPPLES) % 1f
            // Called off, the ripples are drawn back in towards the badge.
            val side = start + p * reach * 2f * spread
            val outline = MorphShape(relax, p, spin.value + p * RIPPLE_TURN).createOutline(Size(side, side), layoutDirection, this)
            translate(at.x - side / 2f, at.y - side / 2f) {
                drawOutline(
                    outline = outline,
                    color = ripple.copy(alpha = (1f - p) * 0.75f * burst.value.coerceIn(0f, 1f)),
                    style = Stroke(width = line * (1f - p * 0.6f)),
                )
            }
        }
    }
    val side = 64.dp
    val half = with(density) { (side / 2).toPx() }
    MorphingContainer(
        from = MaterialShapes.Cookie9Sided,
        to = MaterialShapes.Clover4Leaf,
        morphed = aborted,
        color = container,
        rotationDegrees = spin.value,
        modifier = Modifier
            .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
            .graphicsLayer {
                val s = appear.value.coerceAtLeast(0f)
                scaleX = s
                scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
            }
            .size(side),
    ) {
        Crossfade(targetState = aborted, animationSpec = motion.fastEffectsSpec(), label = "liftBadgeIcon") { off ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(side)) {
                if (off) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = content, modifier = Modifier.size(30.dp))
                } else {
                    LoadingIndicator(color = content, modifier = Modifier.size(42.dp))
                }
            }
        }
    }
}

/**
 * The subject lifted, over the sticker just laid in its place: it rises (a
 * spring with a little bounce), a halo of its own outline shines round it and
 * sparks fly from it; it then settles and fades, leaving the sticker.
 */
@Composable
private fun LiftRise(shown: LiftedShow, w: Float, h: Float, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val rise = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { burst.animateTo(1f, tween(durationMillis = 800)) }
        rise.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
        delay(260)
        rise.animateTo(0f, motion.defaultSpatialSpec())
        fade.animateTo(0f, motion.defaultEffectsSpec())
        onDone()
    }
    val left = shown.left * w
    val top = shown.top * h
    val bw = shown.width * w
    val bh = shown.height * h
    val lift = with(density) { 12.dp.toPx() }
    val boxDp = with(density) { Pair(bw.toDp(), bh.toDp()) }
    val halo = ColorFilter.tint(scheme.primary, BlendMode.SrcIn)
    // The halo: the outline in the theme's colour, a little bigger, behind.
    Image(
        bitmap = shown.image,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = halo,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(boxDp.first, boxDp.second)
            .graphicsLayer {
                val s = 1f + 0.12f * rise.value
                scaleX = s
                scaleY = s
                translationY = -lift * rise.value
                alpha = 0.55f * rise.value.coerceIn(0f, 1f) * fade.value
            },
    )
    Image(
        bitmap = shown.image,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(boxDp.first, boxDp.second)
            .graphicsLayer {
                val s = 1f + 0.07f * rise.value
                scaleX = s
                scaleY = s
                translationY = -lift * rise.value
                alpha = fade.value
            },
    )
    // Sparks: the theme's shapes, flying out from the subject's middle.
    val spark = remember { PolygonShape(MaterialShapes.SoftBurst) }
    val sparkSide = 14.dp
    val sparkHalf = with(density) { (sparkSide / 2).toPx() }
    val reach = with(density) { 36.dp.toPx() } + maxOf(bw, bh) / 2f
    for (k in 0 until SPARKS) {
        val angle = 2.0 * PI * k / SPARKS + PI / SPARKS
        val cx = left + bw / 2f
        val cy = top + bh / 2f
        Box(
            modifier = Modifier
                .offset {
                    val p = burst.value
                    IntOffset(
                        (cx + cos(angle).toFloat() * reach * p - sparkHalf).roundToInt(),
                        (cy + sin(angle).toFloat() * reach * p - sparkHalf).roundToInt(),
                    )
                }
                .graphicsLayer {
                    val p = burst.value
                    val s = if (p < 0.3f) p / 0.3f else 1f - (p - 0.3f) / 0.7f
                    scaleX = s
                    scaleY = s
                    rotationZ = 180f * p
                    alpha = s
                }
                .size(sparkSide)
                .clip(spark)
                .background(if (k % 2 == 0) scheme.tertiary else scheme.primary),
        )
    }
}

/**
 * What a long press says, at the foot of the stage: a pill that springs up,
 * with its icon, its words and, for some, an action; it goes by itself.
 */
@Composable
internal fun LiftNotePill(note: LiftNote?, onAction: () -> Unit, onGone: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    // The last note, kept while the pill goes away.
    var last by remember { mutableStateOf(note) }
    if (note != null) last = note
    LaunchedEffect(note) {
        if (note != null) {
            delay(note.showMs)
            onGone()
        }
    }
    AnimatedVisibility(
        visible = note != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.8f) + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it / 2 } + scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f) + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val shown = last ?: return@AnimatedVisibility
        val tip = shown == LiftNote.TIP
        val container = if (tip) scheme.tertiaryContainer else scheme.inverseSurface
        val content = if (tip) scheme.onTertiaryContainer else scheme.inverseOnSurface
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(container)
                .padding(start = 16.dp, end = if (shown.action != null) 6.dp else 18.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(shown.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(shown.text), style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f, fill = false))
            shown.action?.let { action ->
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onAction) {
                    Text(stringResource(action), style = MaterialTheme.typography.labelLarge, color = if (tip) scheme.tertiary else scheme.inversePrimary)
                }
            }
        }
    }
}

private const val SPARKS = 8

/** One turn of the working badge. */
private const val SPIN_MS = 4_000

/** Ripples on the pond at once, how long each takes to spread and how far it turns as it does. */
private const val RIPPLES = 3
private const val RIPPLE_MS = 1_800
private const val RIPPLE_TURN = 40f

/** How long the red clover stays before it goes. */
private const val ABORT_HOLD_MS = 280L
