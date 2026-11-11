@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.audio.SpectrumBars
import com.cuscus.ffmpeggui.preview.SpectrumHub
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The spectrum of what the preview plays, under the audio editor's waveform:
 * the lows on the left, the highs on the right (see core's SpectrumBands and
 * SpectrumBars, read from [hub] at the moment the sound is heard).
 *
 * Pills as wide as the waveform above, standing straight on the preview (no
 * box of their own), that jump with the sound and fall back softly, coloured from the
 * theme's primary to its tertiary as they rise, with a faint reflection under
 * them; over each, a dot thrown up by it that falls back under its own weight;
 * behind them a glow that follows where the sound is and swells with it.
 * Shown, the pills grow in from the left one after the other; paused, they
 * settle into a slow wave that breathes, and wake as soon as it plays.
 * Only the drawing runs at every frame: nothing is composed again.
 */
@Composable
internal fun AudioSpectrum(hub: SpectrumHub, playing: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val bars = remember(hub) { SpectrumBars(hub.bandCount) }
    val powers = remember(hub) { DoubleArray(hub.bandCount) }
    // Read while drawing: a new frame draws again, and composes nothing.
    var frame by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableFloatStateOf(0f) }
    // The breathing wave while nothing plays, fading out as the music comes in.
    val idle by animateFloatAsState(if (playing) 0f else 1f, motion.slowEffectsSpec(), label = "spectrumIdle")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, motion.slowSpatialSpec()) }

    // The players keep their sound only while it is shown.
    DisposableEffect(hub) {
        hub.watch()
        onDispose { hub.unwatch() }
    }
    LaunchedEffect(hub, bars) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(MAX_STEP_SECONDS)
            last = now
            val heard = hub.read(powers)
            bars.step(if (heard) powers else null, elapsed)
            seconds += elapsed
            frame++
        }
    }

    val track = colors.surfaceContainerHighest
    val low = colors.primary
    val high = colors.tertiary
    val cap = colors.onSurface
    val glow = colors.primaryContainer
    val description = stringResource(R.string.audio_spectrum_description)
    // No box of its own: the pills stand straight on the audio preview, as wide as the waveform.
    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .drawBehind {
                // Both read here only: a frame draws again without composing.
                frame
                val t = seconds
                val count = bars.count
                if (size.width <= 0f || size.height <= 0f || count == 0) return@drawBehind
                val padTop = 8.dp.toPx()
                val reflection = size.height * REFLECTION_SHARE
                val floor = size.height - reflection
                val tallest = (floor - padTop).coerceAtLeast(1f)
                val slot = size.width / count
                val barWidth = (slot * BAR_SHARE).coerceAtLeast(1f)
                val radius = CornerRadius(barWidth / 2f)
                val shown = appear.value
                val resting = idle

                // How tall each pill is now: the sound, or the breathing wave, as it grows in.
                val heights = FloatArray(count)
                var energy = 0f
                var weighted = 0f
                for (b in 0 until count) {
                    val grow = ((shown - b.toFloat() / count * STAGGER) / (1f - STAGGER)).coerceIn(0f, 1f)
                    val eased = 1f - (1f - grow) * (1f - grow)
                    val wave = WAVE_BASE + WAVE_DEPTH * (0.5f + 0.5f * sin(t * WAVE_SPEED + b * WAVE_STEP))
                    val v = max(bars.bars[b], resting * wave) * eased
                    heights[b] = v
                    energy += bars.bars[b]
                    weighted += bars.bars[b] * b
                }
                val mean = energy / count

                // The glow behind, where the sound is and as loud as it is: flattened, so that it
                // fades out within the spectrum's own height.
                if (mean > 0.01f) {
                    val centre = Offset(slot * (if (energy > 0f) weighted / energy + 0.5f else count / 2f), floor)
                    val reach = floor * (0.8f + mean * 0.6f)
                    clipRect {
                        scale(scaleX = 1f, scaleY = GLOW_FLATTEN, pivot = centre) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(glow.copy(alpha = min(0.85f, mean * 1.6f)), Color.Transparent),
                                    center = centre,
                                    radius = reach,
                                ),
                                radius = reach,
                                center = centre,
                            )
                        }
                    }
                }

                for (b in 0 until count) {
                    val x = b * slot + (slot - barWidth) / 2f
                    val v = heights[b]
                    // The empty pill, the bar's room.
                    drawRoundRect(track.copy(alpha = TRACK_ALPHA), Offset(x, padTop), Size(barWidth, tallest), radius)
                    val h = max(barWidth, v * tallest)
                    val colour = lerp(low, high, (v * 0.9f).coerceIn(0f, 1f))
                    drawRoundRect(colour, Offset(x, floor - h), Size(barWidth, h), radius)
                    // Its reflection, fading into the floor.
                    val mirror = min(reflection - 2.dp.toPx(), h * REFLECTION_LENGTH)
                    if (mirror > 1f) {
                        clipRect(top = floor + 2.dp.toPx()) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(colour.copy(alpha = REFLECTION_ALPHA), Color.Transparent),
                                    startY = floor + 2.dp.toPx(),
                                    endY = floor + 2.dp.toPx() + mirror,
                                ),
                                topLeft = Offset(x, floor + 2.dp.toPx()),
                                size = Size(barWidth, mirror),
                                cornerRadius = radius,
                            )
                        }
                    }
                    // The dot thrown up by the bar, falling back after it.
                    val peak = bars.peaks[b] * shown
                    if (peak > v + CAP_GAP && peak > CAP_MIN) {
                        val y = floor - max(barWidth, peak * tallest) - barWidth * 0.9f
                        drawCircle(
                            color = lerp(colour, cap, 0.35f).copy(alpha = (0.55f + peak * 0.45f).coerceAtMost(1f)),
                            radius = barWidth * 0.42f,
                            center = Offset(x + barWidth / 2f, y.coerceAtLeast(padTop / 2f)),
                        )
                    }
                }
            },
    )
}

/** How flat the glow is: its height over its width. */
private const val GLOW_FLATTEN = 0.55f

/** Share of each pill's slot it fills (the rest is the gap). */
private const val BAR_SHARE = 0.62f

/** Share of the height under the floor where the pills are reflected, and how long a reflection is. */
private const val REFLECTION_SHARE = 0.2f
private const val REFLECTION_LENGTH = 0.35f
private const val REFLECTION_ALPHA = 0.28f

private const val TRACK_ALPHA = 0.55f

/** Share of the growing-in during which the pills start one after the other. */
private const val STAGGER = 0.55f

/** The breathing wave: its lowest, its depth, how fast it moves and how far apart its pills are. */
private const val WAVE_BASE = 0.05f
private const val WAVE_DEPTH = 0.09f
private const val WAVE_SPEED = 1.6f
private const val WAVE_STEP = (PI / 9).toFloat()

/** A dot shows a little over its bar, and only once it is up. */
private const val CAP_GAP = 0.015f
private const val CAP_MIN = 0.03f

/** A frame after a long pause counts as this much. */
private const val MAX_STEP_SECONDS = 0.25f
