@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.onboarding.NotificationArt
import com.cuscus.ffmpeggui.onboarding.PhonePreview
import com.cuscus.ffmpeggui.onboarding.ProjectsArt
import com.cuscus.ffmpeggui.onboarding.rememberReducedMotion
import com.cuscus.ffmpeggui.onboarding.rememberTurn
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The settings' pictures: a stage at the top of each category, Material 3
 * Expressive through and through. Behind, big shapes of the theme's own
 * library turn slowly and change shape in turn; in front, what the category
 * changes, made of shapes too and moved by the theme's springs, overshoot and
 * all. Each follows its settings: the appearance is the app in the theme as
 * it is now, the timeline loses its waves when they are off, the phone stops
 * buzzing, the export lands in one folder or two, the projects close up when
 * none are kept. Still when the system's animations are off; nothing for a
 * screen reader (the settings under them say it all).
 */

/** The picture of [category], following [settings]. */
@Composable
internal fun CategoryArt(category: SettingsCategory, settings: AppSettings, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val stage = when (category) {
        SettingsCategory.APPEARANCE -> Stage(scheme.primaryContainer, scheme.tertiaryContainer, listOf(scheme.primary, scheme.tertiary, scheme.secondary))
        SettingsCategory.EDITOR -> Stage(scheme.secondaryContainer, scheme.primaryContainer, listOf(scheme.secondary, scheme.primary, scheme.tertiary))
        SettingsCategory.VIBRATION -> Stage(scheme.tertiaryContainer, scheme.secondaryContainer, listOf(scheme.tertiary, scheme.secondary, scheme.primary))
        SettingsCategory.EXPORTS -> Stage(scheme.secondaryContainer, scheme.tertiaryContainer, listOf(scheme.secondary, scheme.tertiary, scheme.primary))
        SettingsCategory.PROJECTS -> Stage(scheme.tertiaryContainer, scheme.primaryContainer, listOf(scheme.tertiary, scheme.primary, scheme.secondary))
        SettingsCategory.NOTIFICATIONS -> Stage(scheme.primaryContainer, scheme.secondaryContainer, listOf(scheme.primary, scheme.secondary, scheme.tertiary))
        SettingsCategory.SMART -> Stage(scheme.primaryContainer, scheme.tertiaryContainer, listOf(scheme.primary, scheme.tertiary, scheme.secondary))
    }
    ExpressiveStage(stage, minHeight = if (category == SettingsCategory.APPEARANCE) 0.dp else STAGE_HEIGHT, modifier = modifier) {
        when (category) {
            SettingsCategory.APPEARANCE -> PhonePreview(modifier = Modifier.padding(vertical = 24.dp, horizontal = 32.dp))
            SettingsCategory.EDITOR -> EditorArt(settings.clipWaveforms)
            SettingsCategory.VIBRATION -> VibrationArt(settings.haptics)
            SettingsCategory.EXPORTS -> ExportsArt(settings.saveCopies)
            SettingsCategory.PROJECTS -> ProjectsArt(active = settings.saveProjects)
            SettingsCategory.NOTIFICATIONS -> NotificationArt(modifier = Modifier.padding(horizontal = 20.dp))
            SettingsCategory.SMART -> Unit
        }
    }
}

/** A stage's colours: its gradient, and the accents of the big shapes behind. */
internal class Stage(val top: Color, val bottom: Color, val accents: List<Color>)

/**
 * The stage: a very round card in a gradient of two containers, with three big
 * shapes behind what it holds, half out of its edges, turning slowly each its
 * own way and, one after the other, changing into another shape on the
 * theme's spatial spring.
 */
@Composable
internal fun ExpressiveStage(
    stage: Stage,
    modifier: Modifier = Modifier,
    minHeight: Dp = STAGE_HEIGHT,
    content: @Composable BoxScope.() -> Unit,
) {
    val turn by rememberTurn(BACKDROP_TURN_MS, "stageTurn")
    val beat = rememberBeat(BACKDROP_BEAT_MS, steps = 3)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(40.dp))
            .background(Brush.linearGradient(listOf(stage.top, stage.bottom)))
            .clearAndSetSemantics {},
    ) {
        // The big shapes, each going between two of the library's.
        BackdropShape(
            pair = MaterialShapes.Cookie12Sided to MaterialShapes.Flower,
            morphed = beat == 0,
            color = stage.accents[0].copy(alpha = 0.22f),
            size = 190.dp,
            rotation = turn,
            modifier = Modifier.align(Alignment.TopStart).offset { IntOffset(-70.dp.roundToPx(), -60.dp.roundToPx()) },
        )
        BackdropShape(
            pair = MaterialShapes.SoftBurst to MaterialShapes.Cookie9Sided,
            morphed = beat == 1,
            color = stage.accents[1].copy(alpha = 0.26f),
            size = 150.dp,
            rotation = -turn * 1.4f,
            modifier = Modifier.align(Alignment.BottomEnd).offset { IntOffset(46.dp.roundToPx(), 50.dp.roundToPx()) },
        )
        BackdropShape(
            pair = MaterialShapes.Clover4Leaf to MaterialShapes.Sunny,
            morphed = beat == 2,
            color = stage.accents[2].copy(alpha = 0.2f),
            size = 84.dp,
            rotation = turn * 2f,
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-28.dp.roundToPx(), 18.dp.roundToPx()) },
        )
        content()
    }
}

@Composable
private fun BackdropShape(pair: Pair<RoundedPolygon, RoundedPolygon>, morphed: Boolean, color: Color, size: Dp, rotation: Float, modifier: Modifier) {
    MorphingContainer(
        from = pair.first,
        to = pair.second,
        morphed = morphed,
        color = color,
        rotationDegrees = rotation,
        modifier = modifier.size(size),
    ) {}
}

// -- Editor -----------------------------------------------------------------------------------------

/**
 * Three clips, round as pills, the playhead a gem hopping from one to the next
 * on the spatial spring (it overshoots and settles); the clip it lands on
 * swells and its badge blooms from a circle into a cookie. With the clips'
 * waves on, each shows its sound as round bars that dance while it plays.
 */
@Composable
private fun EditorArt(waves: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val active = rememberBeat(EDITOR_BEAT_MS, steps = 3)
    val clips = listOf(
        Triple(scheme.primary, scheme.onPrimary, Icons.Rounded.Movie),
        Triple(scheme.tertiary, scheme.onTertiary, Icons.Rounded.Photo),
        Triple(scheme.secondary, scheme.onSecondary, Icons.Rounded.Audiotrack),
    )
    val weights = listOf(1.2f, 0.9f, 1.3f)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
        val gap = 10.dp
        val total = maxWidth - gap * 2
        val widths = weights.map { total * (it / weights.sum()) }
        val centres = widths.indices.map { i -> widths.take(i).fold(0.dp) { a, w -> a + w } + gap * i + widths[i] / 2 }
        // The playhead: a gem riding above the clips, hopping on the spatial spring.
        val head by animateDpAsState(centres[active], motion.defaultSpatialSpec(), label = "playhead")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(34.dp)) {
                MorphingContainer(
                    from = MaterialShapes.Gem,
                    to = MaterialShapes.Gem,
                    morphed = false,
                    color = scheme.inverseSurface,
                    rotationDegrees = 180f,
                    modifier = Modifier.offset(x = head - 15.dp).size(30.dp),
                ) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                clips.forEachIndexed { i, (color, onColor, icon) ->
                    val on = i == active
                    val swell by animateFloatAsState(if (on) 1.08f else 0.94f, motion.fastSpatialSpec(), label = "clipSwell")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .width(widths[i])
                            .height(78.dp)
                            .graphicsLayer {
                                scaleX = swell
                                scaleY = swell
                            }
                            .clip(RoundedCornerShape(50))
                            .background(color)
                            .padding(horizontal = 10.dp),
                    ) {
                        MorphingContainer(
                            from = MaterialShapes.Circle,
                            to = MaterialShapes.Cookie9Sided,
                            morphed = on,
                            color = onColor,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        }
                        if (waves) {
                            WaveBars(color = onColor, playing = on, seed = i, modifier = Modifier.padding(start = 8.dp).weight(1f).height(36.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Round bars of a clip's sound, each as wide as the others: as many as the
 * clip has room for (none in a clip too narrow), never squeezed into lines.
 * While [playing] they jump to new heights on the fast spatial spring.
 */
@Composable
private fun WaveBars(color: Color, playing: Boolean, seed: Int, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    val tick = rememberBeat(WAVE_BEAT_MS, steps = 16)
    BoxWithConstraints(contentAlignment = Alignment.CenterStart, modifier = modifier) {
        val count = ((maxWidth + WAVE_GAP) / (WAVE_BAR + WAVE_GAP)).toInt().coerceIn(0, WAVE_BARS)
        // Read here: inside the row below the box's own scope is not the receiver.
        val tallest = maxHeight
        Row(horizontalArrangement = Arrangement.spacedBy(WAVE_GAP), verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { b ->
                val rest = 0.35f + 0.5f * ((b * 37 + seed * 11) % 7) / 6f
                val target = if (playing) 0.25f + 0.75f * ((b * 13 + tick * 7 + seed * 5) % 9) / 8f else rest
                val h by animateFloatAsState(target, motion.fastSpatialSpec(), label = "bar")
                Box(
                    modifier = Modifier
                        .width(WAVE_BAR)
                        .height(tallest * h.coerceIn(0.15f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.75f)),
                )
            }
        }
    }
}

// -- Vibration --------------------------------------------------------------------------------------

/**
 * A phone in a blooming badge: it buzzes, and with each buzz it kicks to one
 * side on the fast spatial spring (overshooting back and forth as it
 * settles), while bursts of shape fly out on both sides. With the taps off it
 * sleeps: its badge closes into a plain circle, nothing flies.
 */
@Composable
private fun VibrationArt(on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val reduced = rememberReducedMotion()
    val kick = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    val kickSpec = motion.fastSpatialSpec<Float>()
    val burstSpec = motion.slowEffectsSpec<Float>()
    LaunchedEffect(on, reduced) {
        if (!on || reduced) {
            kick.snapTo(0f)
            burst.snapTo(1f)
            return@LaunchedEffect
        }
        var side = 1f
        while (true) {
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, burstSpec)
            }
            kick.snapTo(14f * side)
            kick.animateTo(0f, kickSpec)
            side = -side
            delay(VIBRATION_PAUSE_MS)
        }
    }
    val bloom by animateFloatAsState(if (on) 1f else 0.82f, motion.defaultSpatialSpec(), label = "bloom")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(STAGE_HEIGHT)) {
        // The bursts: shapes flying out to both sides, turning and fading.
        if (on) {
            listOf(MaterialShapes.SoftBurst, MaterialShapes.Cookie4Sided, MaterialShapes.Sunny).forEachIndexed { k, shape ->
                listOf(-1f, 1f).forEach { side ->
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val p = burst.value
                                translationX = side * (70.dp.toPx() + p * (40 + k * 26).dp.toPx())
                                translationY = (k - 1) * 34.dp.toPx() * (0.4f + p)
                                val s = (1f - p) * (1f - k * 0.2f)
                                scaleX = s
                                scaleY = s
                                rotationZ = p * 120f * side
                                alpha = 1f - p
                            }
                            .size(28.dp)
                            .clip(PolygonShape(shape))
                            .background(if (k == 1) scheme.primary else scheme.tertiary),
                    )
                }
            }
        }
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie12Sided,
            morphed = on,
            color = if (on) scheme.tertiary else scheme.surfaceContainerHighest,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = bloom
                    scaleY = bloom
                }
                .size(132.dp),
        ) {
            // The phone, kicking.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = kick.value
                        translationX = kick.value * 0.6f * density
                    }
                    .width(54.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) scheme.onTertiary else scheme.outline)
                    .padding(5.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) scheme.tertiaryContainer else scheme.surfaceContainerHigh),
                ) {
                    Icon(Icons.Rounded.Vibration, contentDescription = null, tint = if (on) scheme.onTertiaryContainer else scheme.outline, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// -- Exports ----------------------------------------------------------------------------------------

/**
 * A finished file, a gem with the film in it, flying in an arc on the slow
 * spatial spring into the app's folder (a cookie); with the copies on, a twin
 * parts from it and lands in the phone's folder (a flower). The folders swell
 * as they catch it, overshoot and settle.
 */
@Composable
private fun ExportsArt(copies: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val reduced = rememberReducedMotion()
    val flight = remember { Animatable(0f) }
    val catch = remember { Animatable(1f) }
    val flightSpec = motion.slowSpatialSpec<Float>()
    val catchSpec = motion.fastSpatialSpec<Float>()
    LaunchedEffect(reduced) {
        if (reduced) {
            flight.snapTo(0.5f)
            return@LaunchedEffect
        }
        while (true) {
            flight.snapTo(0f)
            delay(EXPORTS_REST_MS)
            flight.animateTo(1f, flightSpec)
            catch.snapTo(1.25f)
            catch.animateTo(1f, catchSpec)
            delay(EXPORTS_REST_MS)
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(STAGE_HEIGHT)) {
        val w = maxWidth
        val h = maxHeight
        val start = w * 0.2f to h * 0.5f
        val own = w * 0.78f to if (copies) h * 0.3f else h * 0.5f
        val phone = w * 0.78f to h * 0.72f
        Folder(own, scheme.primary, scheme.onPrimary, MaterialShapes.Cookie9Sided, Icons.Rounded.Folder, catch.value)
        if (copies) Folder(phone, scheme.tertiary, scheme.onTertiary, MaterialShapes.Flower, Icons.Rounded.PhoneAndroid, catch.value)
        val targets = if (copies) listOf(own, phone) else listOf(own)
        val p = flight.value
        targets.forEach { target ->
            val x = start.first + (target.first - start.first) * p
            // The arc: up and over.
            val y = start.second + (target.second - start.second) * p - h * 0.22f * (4 * p * (1 - p))
            val s = 1f - 0.55f * p.coerceIn(0f, 1f)
            MorphingContainer(
                from = MaterialShapes.Gem,
                to = MaterialShapes.Cookie4Sided,
                morphed = p > 0.5f,
                color = scheme.surfaceContainerLowest,
                rotationDegrees = p * 200f,
                modifier = Modifier
                    .offset(x = x - 26.dp, y = y - 26.dp)
                    .graphicsLayer {
                        scaleX = s
                        scaleY = s
                        alpha = if (p > 0.97f) 0f else 1f
                    }
                    .size(52.dp),
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** A folder at [at] (its centre): a big shape in [color] with [icon], swelling by [catch]. */
@Composable
private fun Folder(at: Pair<Dp, Dp>, color: Color, onColor: Color, shape: RoundedPolygon, icon: ImageVector, catch: Float) {
    MorphingContainer(
        from = shape,
        to = shape,
        morphed = false,
        color = color,
        modifier = Modifier
            .offset(x = at.first - 36.dp, y = at.second - 36.dp)
            .graphicsLayer {
                scaleX = catch
                scaleY = catch
            }
            .size(72.dp),
    ) {
        Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.size(32.dp))
    }
}

// -- Helpers ----------------------------------------------------------------------------------------

/** A step that goes round 0 until [steps], one every [periodMs]; 0 and still when the system's animations are off. */
@Composable
internal fun rememberBeat(periodMs: Long, steps: Int): Int {
    val reduced = rememberReducedMotion()
    var beat by remember { mutableIntStateOf(0) }
    if (!reduced) {
        LaunchedEffect(periodMs, steps) {
            while (true) {
                delay(periodMs)
                beat = (beat + 1) % steps
            }
        }
    }
    return beat
}

/** How tall a stage is (the appearance's grows to the phone it shows). */
private val STAGE_HEIGHT = 220.dp

private const val BACKDROP_TURN_MS = 48_000
private const val BACKDROP_BEAT_MS = 2_400L
private const val EDITOR_BEAT_MS = 1_500L
private const val WAVE_BEAT_MS = 180L
private const val WAVE_BARS = 6
private val WAVE_BAR = 5.dp
private val WAVE_GAP = 4.dp
private const val VIBRATION_PAUSE_MS = 900L
private const val EXPORTS_REST_MS = 700L
