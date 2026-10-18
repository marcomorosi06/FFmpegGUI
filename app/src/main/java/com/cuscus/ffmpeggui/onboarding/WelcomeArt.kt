@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.FormatBadge
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import com.cuscus.ffmpeggui.ui.theme.appIconBackground
import com.cuscus.ffmpeggui.ui.theme.appIconGlyph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The welcome's pictures. Each one is drawn by the app itself (shapes from
 * MaterialShapes, the theme's colours, the app's own badges and icon), so they
 * follow the colours chosen on the welcome as they change, and each one moves:
 * slowly by itself, and more when touched.
 */

// -- Hello: the logo, large ----------------------------------------------------------------------

/**
 * One large shape and what it holds, nothing around it: behind, a soft blob
 * in [glow] that breathes (grows and shrinks a little) and turns very slowly;
 * in front, [content] in a shape of [color] that keeps changing, slowly,
 * between [from] and [to]. Held, the front shape squishes and the blob
 * swells; let go, both spring back.
 */
@Composable
private fun BreathingHero(
    from: RoundedPolygon,
    to: RoundedPolygon,
    color: Color,
    glow: Color,
    modifier: Modifier = Modifier,
    maxSize: Dp = 300.dp,
    content: @Composable (side: Dp) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val reduced = rememberReducedMotion()
    var morphed by remember { mutableStateOf(false) }
    LaunchedEffect(reduced) {
        while (!reduced) {
            delay(HERO_BREATH_MS)
            morphed = !morphed
        }
    }
    val breath by rememberLoop(HERO_BREATH_LOOP_MS, "heroBreath", still = 0.25f)
    val turn by rememberTurn(HERO_TURN_MS, "heroTurn")
    var pressed by remember { mutableStateOf(false) }
    val squish by animateFloatAsState(if (pressed) 0.86f else 1f, motion.fastSpatialSpec(), label = "heroSquish")
    val swell by animateFloatAsState(if (pressed) 1.08f else 1f, motion.defaultSpatialSpec(), label = "heroSwell")
    val blob = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.widthIn(max = maxSize).fillMaxWidth().aspectRatio(1f),
    ) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        // The blob behind: breathing in and out, turning very slowly.
        Box(
            modifier = Modifier
                .size(side * 0.96f)
                .graphicsLayer {
                    val b = 0.94f + 0.06f * sin(breath * 2f * PI.toFloat())
                    scaleX = b * swell
                    scaleY = b * swell
                    rotationZ = turn
                }
                .clip(blob)
                .background(glow),
        )
        MorphingContainer(
            from = from,
            to = to,
            morphed = morphed,
            color = color,
            modifier = Modifier
                .size(side * 0.62f)
                .graphicsLayer {
                    scaleX = squish
                    scaleY = squish
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            tryAwaitRelease()
                            pressed = false
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        },
                    )
                },
        ) {
            content(side * 0.62f)
        }
    }
}

/**
 * The app's logo, large: the launcher icon's glyph in a shape that slowly
 * changes from a twelve-sided cookie into a soft burst and back, over a soft
 * blob in the primary container's colour that breathes.
 */
@Composable
internal fun HelloHero(modifier: Modifier = Modifier) {
    BreathingHero(
        from = MaterialShapes.Cookie12Sided,
        to = MaterialShapes.SoftBurst,
        color = appIconBackground(),
        glow = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = modifier,
    ) { side ->
        // The glyph is drawn on the launcher's 108 dp layer, whose visible part is the inner 72 dp.
        Image(painter = appIconGlyph(), contentDescription = null, modifier = Modifier.requiredSize(side * 1.5f))
    }
}

// -- The editor, in small -------------------------------------------------------------------

/**
 * A small editor at work: a picture with a text gliding between its keyframes
 * over it, and under it a timeline (three clips, the text's lane with its
 * diamonds lighting up as the playhead passes, the sound's wave) with the
 * playhead sweeping across.
 */
@Composable
internal fun EditorMock(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t by rememberLoop(EDITOR_LOOP_MS, "editorPlayhead", still = 0.42f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(22.dp))
                .drawBehind { drawScene(t, scheme.primaryContainer, scheme.tertiaryContainer, scheme.tertiary, scheme.secondary) },
        ) {
            val width = maxWidth
            val height = maxHeight
            Box(
                modifier = Modifier
                    .offset {
                        val x = stickerX(t)
                        IntOffset(((width - STICKER_WIDTH) * x).roundToPx(), (height * 0.18f).roundToPx())
                    }
                    .graphicsLayer {
                        val s = 0.9f + 0.25f * stickerX(t)
                        scaleX = s
                        scaleY = s
                        rotationZ = -6f + 12f * stickerX(t)
                    }
                    .width(STICKER_WIDTH)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.welcome_editor_sticker),
                    style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = 16.sp),
                    color = scheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        val clipColors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
        val lane = scheme.surfaceContainerHighest
        val lit = scheme.primary
        val dim = scheme.outline
        val wave = scheme.secondaryContainer
        val wavePast = scheme.secondary
        val head = scheme.onSurface
        Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
            drawTimeline(t, clipColors, lane, lit, dim, wave, wavePast, head)
        }
    }
}

/** Where the text is across the picture (0..1) at [t]: three keyframes, smooth between them. */
private fun stickerX(t: Float): Float {
    val keys = STICKER_KEYS
    if (t <= keys.first().first) return keys.first().second
    if (t >= keys.last().first) return keys.last().second
    for (i in 0 until keys.lastIndex) {
        val (ta, xa) = keys[i]
        val (tb, xb) = keys[i + 1]
        if (t < tb) {
            val p = (t - ta) / (tb - ta)
            return xa + (xb - xa) * p * p * (3 - 2 * p)
        }
    }
    return keys.last().second
}

private val STICKER_KEYS = listOf(0.1f to 0.08f, 0.5f to 0.85f, 0.9f to 0.3f)
private val STICKER_WIDTH = 86.dp

private fun DrawScope.drawScene(t: Float, top: Color, bottom: Color, sun: Color, hills: Color) {
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val sunY = size.height * (0.62f - 0.22f * sin(t * PI).toFloat())
    drawCircle(sun, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.72f, sunY))
    val back = Path().apply {
        moveTo(0f, size.height * 0.78f)
        cubicTo(size.width * 0.25f, size.height * 0.55f, size.width * 0.45f, size.height * 0.9f, size.width * 0.7f, size.height * 0.7f)
        cubicTo(size.width * 0.85f, size.height * 0.6f, size.width * 0.95f, size.height * 0.68f, size.width, size.height * 0.66f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(back, hills.copy(alpha = 0.55f))
    val front = Path().apply {
        moveTo(0f, size.height * 0.9f)
        cubicTo(size.width * 0.3f, size.height * 0.75f, size.width * 0.6f, size.height * 1.02f, size.width, size.height * 0.82f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(front, hills)
}

private fun DrawScope.drawTimeline(
    t: Float,
    clipColors: List<Color>,
    lane: Color,
    lit: Color,
    dim: Color,
    wave: Color,
    wavePast: Color,
    head: Color,
) {
    val w = size.width
    val gap = 4.dp.toPx()
    val clipH = 28.dp.toPx()
    val radius = CornerRadius(10.dp.toPx())
    // The clips.
    val clips = listOf(0f to 0.36f, 0.36f to 0.7f, 0.7f to 1f)
    clips.forEachIndexed { i, (a, b) ->
        drawRoundRect(
            color = clipColors[i],
            topLeft = Offset(w * a + if (i > 0) gap / 2 else 0f, 0f),
            size = Size(w * (b - a) - if (i in 1..1) gap else gap / 2, clipH),
            cornerRadius = radius,
        )
    }
    // The text's lane and its keyframes.
    val laneY = clipH + 8.dp.toPx()
    val laneH = 18.dp.toPx()
    drawRoundRect(lane, topLeft = Offset(0f, laneY), size = Size(w, laneH), cornerRadius = CornerRadius(laneH / 2))
    STICKER_KEYS.forEach { (k, _) ->
        val near = kotlin.math.abs(t - k) < 0.05f
        val half = (if (near) 8.dp else 5.dp).toPx()
        val c = Offset(w * k, laneY + laneH / 2)
        val diamond = Path().apply {
            moveTo(c.x, c.y - half)
            lineTo(c.x + half, c.y)
            lineTo(c.x, c.y + half)
            lineTo(c.x - half, c.y)
            close()
        }
        drawPath(diamond, if (near || t > k) lit else dim)
    }
    // The sound.
    val waveY = laneY + laneH + 8.dp.toPx()
    val waveH = size.height - waveY
    val bars = 46
    val step = w / bars
    for (i in 0 until bars) {
        val x = i * step + step / 2
        val amp = 0.25f + 0.75f * kotlin.math.abs(sin(i * 0.55f) * cos(i * 0.21f))
        val h = waveH * amp
        drawLine(
            color = if (x < w * t) wavePast else wave,
            start = Offset(x, waveY + (waveH - h) / 2),
            end = Offset(x, waveY + (waveH + h) / 2),
            strokeWidth = step * 0.55f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
    // The playhead.
    val x = w * t
    drawLine(head, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5f.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(head, radius = 5.dp.toPx(), center = Offset(x, 0f))
}

// -- Smart features ----------------------------------------------------------------------------

/**
 * Words said and written in time: the words of a sentence as pills, lit one
 * after the other as if spoken.
 */
@Composable
internal fun SpokenWordsArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val words = stringResource(R.string.welcome_smart_words).split(' ')
    val loop = rememberLoop(WORDS_LOOP_MS, "spokenWords", still = 0.3f)
    // Recomposed only when the word changes, not on every frame.
    val current by remember(words.size) { derivedStateOf { (loop.value * (words.size + 2)).toInt() } }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        words.forEachIndexed { index, word ->
            val on = index == current
            val said = index < current
            val back by animateColorAsState(
                when {
                    on -> scheme.primary
                    said -> scheme.primaryContainer
                    else -> scheme.surfaceContainerHighest
                },
                MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "word",
            )
            val scale by animateFloatAsState(if (on) 1.12f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "wordScale")
            Text(
                text = word,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    on -> scheme.onPrimary
                    said -> scheme.onPrimaryContainer
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(back)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * A subject cut out of its picture: a figure in a flower shape over a scene that
 * fades away to the checkerboard of transparency, and back.
 */
@Composable
internal fun CutoutArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(CUTOUT_LOOP_MS, "cutout", still = 0.75f)
    val light = scheme.surfaceContainerHighest
    val dark = scheme.surfaceContainerLow
    val sceneTop = scheme.tertiaryContainer
    val sceneBottom = scheme.secondaryContainer
    val flower = remember { PolygonShape(MaterialShapes.Flower) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            // The checkerboard is one path made once for the size; only the scene's fade is
            // drawn again at every frame.
            .drawWithCache {
                val cell = 10.dp.toPx()
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toInt() + 1
                val darkCells = Path().apply {
                    for (r in 0 until rows) for (c in 0 until cols) {
                        if ((r + c) % 2 != 0) addRect(Rect(Offset(c * cell, r * cell), Size(cell, cell)))
                    }
                }
                val scene = Brush.verticalGradient(listOf(sceneTop, sceneBottom))
                onDrawBehind {
                    drawRect(light)
                    drawPath(darkCells, dark)
                    drawRect(scene, alpha = sceneAlpha(loop.value))
                }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(0.62f)
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = 1f + 0.06f * (1f - sceneAlpha(loop.value))
                    scaleX = s
                    scaleY = s
                }
                .clip(flower)
                .background(scheme.primary),
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.fillMaxSize(0.55f))
        }
    }
}

/** How much of the scene shows at [t]: there, fading out, gone, fading back. */
private fun sceneAlpha(t: Float): Float = when {
    t < 0.35f -> 1f
    t < 0.5f -> 1f - (t - 0.35f) / 0.15f
    t < 0.85f -> 0f
    else -> (t - 0.85f) / 0.15f
}

// -- Privacy -------------------------------------------------------------------------------------

/** A lock, large, in a shape that slowly changes, over a breathing blob; nothing around it. */
@Composable
internal fun PrivacyArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    BreathingHero(
        from = MaterialShapes.Cookie9Sided,
        to = MaterialShapes.Cookie4Sided,
        color = scheme.primary,
        glow = scheme.secondaryContainer.copy(alpha = 0.8f),
        modifier = modifier,
        maxSize = 240.dp,
    ) { side ->
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(side * 0.42f))
    }
}

// -- Projects --------------------------------------------------------------------------------------

/** Three projects fanned like cards, opening wider when the page comes into view. */
@Composable
internal fun ProjectsArt(active: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fan by animateFloatAsState(if (active) 1f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "fan")
    val tones = listOf(
        scheme.tertiaryContainer to scheme.tertiary,
        scheme.secondaryContainer to scheme.secondary,
        scheme.primaryContainer to scheme.primary,
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().height(190.dp)) {
        tones.forEachIndexed { index, (container, accent) ->
            val side = index - 1
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = side * 11f * fan
                        translationX = side * 64.dp.toPx() * fan
                        translationY = kotlin.math.abs(side) * 14.dp.toPx() * fan
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.2f)
                    }
                    .width(118.dp)
                    .height(156.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(scheme.surfaceContainerHighest)
                    .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(container),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(PolygonShape(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst, MaterialShapes.Clover4Leaf)[index]))
                            .background(accent),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(8.dp).clip(CircleShape).background(scheme.onSurfaceVariant.copy(alpha = 0.5f)))
                Spacer(modifier = Modifier.height(5.dp))
                Box(modifier = Modifier.fillMaxWidth(0.5f).height(8.dp).clip(CircleShape).background(scheme.onSurfaceVariant.copy(alpha = 0.3f)))
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// -- Notifications --------------------------------------------------------------------------------

/** An export's notification as it will look: the app's badge, the file, a wavy bar filling. */
@Composable
internal fun NotificationArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(NOTIFICATION_LOOP_MS, "notification", still = 0.64f)
    // The bar reads the loop while drawing; only the percentage (a hundred steps) and the
    // moment it is done recompose, not every frame.
    val progressOf = remember(loop) { { (loop.value * 1.25f).coerceAtMost(1f) } }
    val percent by remember(loop) { derivedStateOf { (progressOf() * 100).roundToInt() } }
    val done by remember(loop) { derivedStateOf { progressOf() >= 1f } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(16.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie12Sided,
            to = MaterialShapes.Circle,
            morphed = done,
            color = if (done) scheme.primary else appIconBackground(),
            modifier = Modifier.size(48.dp),
        ) {
            if (done) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
            } else {
                Image(painter = appIconGlyph(), contentDescription = null, modifier = Modifier.requiredSize(72.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (done) R.string.welcome_notify_mock_done else R.string.welcome_notify_mock_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = scheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearWavyProgressIndicator(progress = progressOf, modifier = Modifier.fillMaxWidth())
        }
    }
}

// -- All set ---------------------------------------------------------------------------------------------

/** The shapes of the burst on the last page. */
private val BURST_SHAPES: List<RoundedPolygon>
    get() = listOf(
        MaterialShapes.Cookie4Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Clover4Leaf,
        MaterialShapes.Gem, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided, MaterialShapes.Pill,
        MaterialShapes.Clover8Leaf, MaterialShapes.Cookie9Sided, MaterialShapes.Diamond, MaterialShapes.Cookie7Sided,
    )

/**
 * A big tick in a cookie with shapes bursting out of it in every colour of the
 * theme when the page comes into view, and again at every tap (the cookie
 * turning into a sun for a moment).
 */
@Composable
internal fun CelebrationArt(active: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val reduced = rememberReducedMotion()
    val burst = remember { Animatable(0f) }
    var pokes by remember { mutableIntStateOf(0) }
    var sunny by remember { mutableStateOf(false) }
    val drift by rememberTurn(BURST_DRIFT_MS, "burstDrift")
    val spec = motion.slowSpatialSpec<Float>()
    LaunchedEffect(active, pokes) {
        if (!active) return@LaunchedEffect
        if (reduced) {
            burst.snapTo(1f)
            return@LaunchedEffect
        }
        sunny = true
        burst.snapTo(0f)
        launch {
            delay(SUNNY_MS)
            sunny = false
        }
        burst.animateTo(1f, spec)
    }
    val tones = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)
    val shapes = remember { BURST_SHAPES.map { PolygonShape(it) } }
    val pop by animateFloatAsState(if (active) 1f else 0.4f, motion.slowSpatialSpec(), label = "tickPop")
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1.15f),
    ) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        shapes.forEachIndexed { index, shape ->
            val angle = index * 360f / shapes.size + (index % 3) * 9f
            val reach = 0.33f + (index % 4) * 0.045f
            val size = side * (0.07f + (index % 3) * 0.025f)
            Box(
                modifier = Modifier
                    .offset {
                        val a = Math.toRadians((angle + drift * 0.15f).toDouble())
                        val r = side.toPx() * reach * burst.value
                        IntOffset((cos(a) * r).roundToInt(), (sin(a) * r).roundToInt())
                    }
                    .graphicsLayer {
                        rotationZ = drift * (if (index % 2 == 0) 1f else -1f) + index * 30f
                        val s = burst.value.coerceIn(0f, 1.2f)
                        scaleX = s
                        scaleY = s
                        alpha = burst.value.coerceIn(0f, 1f)
                    }
                    .size(size)
                    .clip(shape)
                    .background(tones[index % tones.size]),
            )
        }
        MorphingContainer(
            from = MaterialShapes.Cookie12Sided,
            to = MaterialShapes.Sunny,
            morphed = sunny,
            color = scheme.primary,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                    rotationZ = drift * 0.25f
                }
                .size(side * 0.4f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        pokes++
                    }
                },
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.graphicsLayer { rotationZ = -drift * 0.25f }.size(side * 0.19f),
            )
        }
    }
}

// -- Colours ----------------------------------------------------------------------------------------------

/**
 * A little phone with the app as it will look: its icon (as the launcher will
 * show it), its name, the colours in use and a few controls in them.
 */
@Composable
internal fun PhonePreview(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .widthIn(max = 260.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.surfaceContainerLowest)
            .border(3.dp, scheme.outlineVariant, RoundedCornerShape(36.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie12Sided,
                to = MaterialShapes.Circle,
                morphed = false,
                color = appIconBackground(),
                modifier = Modifier.size(52.dp),
            ) {
                Image(painter = appIconGlyph(), contentDescription = null, modifier = Modifier.requiredSize(78.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.home_title_ffmpeg),
                    style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = 15.sp),
                    color = scheme.primary,
                )
                Text(
                    stringResource(R.string.home_title_gui),
                    style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(1000), fontSize = 24.sp, lineHeight = 24.sp),
                    color = scheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.tertiaryContainer).forEach {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(it))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(scheme.primaryContainer),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clip(PolygonShape(MaterialShapes.Cookie9Sided))
                    .background(scheme.primary),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f).height(36.dp).clip(CircleShape).background(scheme.primary))
            Spacer(modifier = Modifier.width(8.dp))
            // A switch, on.
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.width(52.dp).height(32.dp).clip(CircleShape).background(scheme.primary).padding(4.dp),
            ) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(scheme.onPrimary))
            }
        }
    }
}

/**
 * A theme as a tiny screen: the light one, the dark one, or the two halves of
 * the system's (light one way, dark the other).
 */
@Composable
internal fun ThemeSwatch(light: Boolean?, accent: Color, modifier: Modifier = Modifier) {
    val day = Color(0xFFF4F7F6)
    val night = Color(0xFF14191A)
    Canvas(modifier = modifier) {
        val r = CornerRadius(14.dp.toPx())
        when (light) {
            true -> drawRoundRect(day, cornerRadius = r)
            false -> drawRoundRect(night, cornerRadius = r)
            null -> {
                drawRoundRect(day, cornerRadius = r)
                val half = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                val card = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
                clipPath(card) { drawPath(half, night) }
            }
        }
        val pad = 8.dp.toPx()
        drawRoundRect(accent, topLeft = Offset(pad, pad), size = Size(size.width * 0.45f, 8.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
        drawCircle(accent, radius = 7.dp.toPx(), center = Offset(size.width - pad - 7.dp.toPx(), size.height - pad - 7.dp.toPx()))
    }
}

private const val HERO_BREATH_MS = 2_400L
private const val HERO_BREATH_LOOP_MS = 6_000
private const val HERO_TURN_MS = 60_000
private const val EDITOR_LOOP_MS = 5_200
private const val WORDS_LOOP_MS = 4_200
private const val CUTOUT_LOOP_MS = 4_800
private const val BREATH_MS = 1_800L
private const val NOTIFICATION_LOOP_MS = 4_400
private const val BURST_DRIFT_MS = 30_000
private const val SUNNY_MS = 650L
