@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.asComposePath
import com.cuscus.ffmpeggui.core.model.Split
import com.cuscus.ffmpeggui.preview.LevelMeter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the editor shows instead of a picture when an audio file is being made:
 * the exported sound as a big waveform (played part in the theme's tone, parts
 * that would clip in the error tone, the bars at the playhead swelling with the
 * level that is playing), a badge that morphs with the level (the file's
 * [cover] cut to its shape, when there is one), the tags and format on top
 * and the effects in use below. Touching or dragging the waveform
 * moves the playhead.
 *
 * [envelope] is the outline of the output (see `outputEnvelope`); [partsMs] are
 * where the file will be cut into parts; [positionMs] is read while drawing, so
 * playback does not recompose anything.
 */
@Composable
internal fun AudioPreview(
    title: String,
    subtitle: String,
    formatLabel: String,
    envelope: FloatArray,
    outputDurationMs: Long,
    positionMs: () -> Long,
    isPlaying: Boolean,
    meter: LevelMeter,
    partsMs: List<Long>,
    activeEffects: List<String>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
) {
    val colors = MaterialTheme.colorScheme
    // The level that is playing: quick to rise, slow to fall, read while drawing.
    var level by remember { mutableFloatStateOf(0f) }
    var spin by remember { mutableFloatStateOf(0f) }
    // The badge's shape follows the level more calmly still, so it breathes instead of twitching.
    var shape by remember { mutableFloatStateOf(0f) }
    val playing by rememberUpdatedState(isPlaying)
    LaunchedEffect(meter) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = if (last == 0L) 0f else (now - last) / 1_000_000_000f
            last = now
            val peak = meter.take().coerceAtMost(1.2f)
            // Rises over a frame or two and falls slowly: no flicker from one frame to the next.
            level = if (peak > level) level + (peak - level) * LEVEL_ATTACK else level * LEVEL_RELEASE
            shape += (level.coerceIn(0f, 1f) - shape) * SHAPE_FOLLOW
            if (playing) spin = (spin + elapsed * SPIN_DEGREES_PER_SECOND) % 360f
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LevelBadge(level = { shape }, spin = { spin }, cover = cover)
            Spacer(modifier = Modifier.width(14.dp))
            // The tags the file will carry (the file name and format are in the top bar).
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { stringResource(R.string.audio_untitled) },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = if (title.isBlank()) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle.ifBlank { stringResource(R.string.audio_untitled_hint) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Pill(text = formatLabel, container = colors.secondaryContainer, content = colors.onSecondaryContainer)
        }
        Spacer(modifier = Modifier.height(12.dp))
        WaveformView(
            envelope = envelope,
            durationMs = outputDurationMs,
            positionMs = positionMs,
            level = { level },
            partsMs = partsMs,
            onSeek = onSeek,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (activeEffects.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                activeEffects.forEach { label ->
                    Pill(text = label, container = colors.tertiaryContainer, content = colors.onTertiaryContainer)
                }
            }
        }
    }
}

/**
 * A cookie that turns slowly while playing and bursts out with the level. The
 * shape is drawn at one fixed scale around its own centre and turned there: a
 * shape fitted to its box anew on every frame would grow and shrink as it turns.
 * With a [cover] the shape holds the picture (its middle square) and does not
 * turn, so that the picture stays upright.
 */
@Composable
private fun LevelBadge(level: () -> Float, spin: () -> Float, cover: ImageBitmap? = null) {
    val colors = MaterialTheme.colorScheme
    val start = MaterialShapes.Cookie9Sided
    val end = MaterialShapes.SoftBurst
    val morph = remember { Morph(start, end) }
    // The shapes' common centre, and how far they reach from it.
    val centre = remember { Offset((start.centerX + end.centerX) / 2f, (start.centerY + end.centerY) / 2f) }
    val reach = remember {
        listOf(start.calculateBounds(), end.calculateBounds()).maxOf { b ->
            maxOf(centre.x - b[0], b[2] - centre.x, centre.y - b[1], b[3] - centre.y)
        }.coerceAtLeast(0.01f)
    }
    val path = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (cover != null) 64.dp else 56.dp)
            .graphicsLayer {
                val l = level().coerceIn(0f, 1f)
                scaleX = 1f + l * 0.14f
                scaleY = 1f + l * 0.14f
            }
            .drawBehind {
                path.rewind()
                morph.toPath(level().coerceIn(0f, 1f), path)
                val scale = size.minDimension / 2f / reach
                matrix.reset()
                matrix.setTranslate(-centre.x, -centre.y)
                matrix.postScale(scale, scale)
                if (cover == null) matrix.postRotate(spin())
                matrix.postTranslate(size.width / 2f, size.height / 2f)
                path.transform(matrix)
                if (cover == null) {
                    drawPath(path.asComposePath(), colors.primary)
                } else {
                    val side = minOf(cover.width, cover.height)
                    clipPath(path.asComposePath()) {
                        drawImage(
                            image = cover,
                            srcOffset = IntOffset((cover.width - side) / 2, (cover.height - side) / 2),
                            srcSize = IntSize(side, side),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )
                    }
                }
            },
    ) {
        if (cover == null) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun Pill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The bars of the output's sound, the playhead and the cuts between parts. */
@Composable
private fun WaveformView(
    envelope: FloatArray,
    durationMs: Long,
    positionMs: () -> Long,
    level: () -> Float,
    partsMs: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(durationMs)
    // While the finger is on it the playhead follows the finger at once.
    var dragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableLongStateOf(0L) }
    // Where the finger left it: shown until the player reports that position, so the
    // playhead does not jump back to where the player was a moment before.
    var heldMs by remember { mutableStateOf<Long?>(null) }
    val currentPosition by rememberUpdatedState(positionMs)
    LaunchedEffect(heldMs) {
        val target = heldMs ?: return@LaunchedEffect
        withTimeoutOrNull(HANDOVER_TIMEOUT_MS) {
            snapshotFlow { currentPosition() }.first { abs(it - target) < HANDOVER_TOLERANCE_MS }
        }
        heldMs = null
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val ms = msAt(offset.x, size.width.toFloat(), currentDuration)
                    heldMs = ms
                    currentOnSeek(ms)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragMs = msAt(offset.x, size.width.toFloat(), currentDuration)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnSeek(dragMs)
                    },
                    onDragEnd = {
                        heldMs = dragMs
                        dragging = false
                    },
                    onDragCancel = {
                        heldMs = dragMs
                        dragging = false
                    },
                ) { change, _ ->
                    change.consume()
                    dragMs = msAt(change.position.x, size.width.toFloat(), currentDuration)
                    currentOnSeek(dragMs)
                }
            }
            .drawBehind {
                if (size.width <= 0f || size.height <= 0f) return@drawBehind
                val barWidth = BAR_WIDTH_DP.dp.toPx()
                val gap = BAR_GAP_DP.dp.toPx()
                val step = barWidth + gap
                val count = max(1, ((size.width + gap) / step).toInt())
                val left = (size.width - (count * step - gap)) / 2f
                val middle = size.height / 2f
                val tallest = middle - 8.dp.toPx()
                val position = if (dragging) dragMs else heldMs ?: positionMs()
                val fraction = if (durationMs > 0L) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                val playX = fraction * size.width
                val live = level().coerceIn(0f, 1.2f)

                // Each bar's height, swelling smoothly with the level as the playhead passes.
                val values = FloatArray(count)
                val halves = FloatArray(count)
                for (i in 0 until count) {
                    var value = 0f
                    if (envelope.isNotEmpty()) {
                        val from = i * envelope.size / count
                        val to = max(from + 1, (i + 1) * envelope.size / count).coerceAtMost(envelope.size)
                        for (k in from until to) if (envelope[k] > value) value = envelope[k]
                    }
                    val distance = abs(left + i * step + barWidth / 2f - playX) / step
                    val swell = if (distance < SWELL_BARS) {
                        1f + live * 0.6f * (0.5f + 0.5f * cos(PI.toFloat() * distance / SWELL_BARS))
                    } else {
                        1f
                    }
                    values[i] = value
                    // At least a round dot, unless the view is lower than one (the keyboard
                    // open over a short window squeezes it to a few pixels).
                    halves[i] = (value.coerceAtMost(1f) * tallest * swell).coerceIn(minOf(barWidth / 2f, middle), middle)
                }
                fun bars(played: Boolean) {
                    for (i in 0 until count) {
                        val color = when {
                            values[i] > 1.001f -> colors.error
                            played -> colors.primary
                            else -> colors.onSurfaceVariant.copy(alpha = 0.32f)
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left + i * step, middle - halves[i]),
                            size = Size(barWidth, halves[i] * 2f),
                            cornerRadius = CornerRadius(barWidth / 2f),
                        )
                    }
                }
                // The played part fills in exactly up to the playhead, through the bar under
                // it, rather than one whole bar at a time.
                clipRect(left = playX) { bars(played = false) }
                clipRect(right = playX) { bars(played = true) }

                // Where the file will be cut into parts: dashed lines.
                val dash = 6.dp.toPx()
                partsMs.forEach { ms ->
                    if (durationMs <= 0L) return@forEach
                    val x = ms.toFloat() / durationMs * size.width
                    var y = 0f
                    while (y < size.height) {
                        drawRoundRect(
                            color = colors.tertiary,
                            topLeft = Offset(x - 1.dp.toPx(), y),
                            size = Size(2.dp.toPx(), dash),
                            cornerRadius = CornerRadius(1.dp.toPx()),
                        )
                        y += dash * 2f
                    }
                }

                // The playhead: a rounded line with a knob.
                val x = playX
                val lineWidth = 3.dp.toPx()
                drawRoundRect(
                    color = colors.primary,
                    topLeft = Offset(x - lineWidth / 2f, 4.dp.toPx()),
                    size = Size(lineWidth, size.height - 4.dp.toPx()),
                    cornerRadius = CornerRadius(lineWidth / 2f),
                )
                drawCircle(color = colors.primary, radius = 6.dp.toPx(), center = Offset(x, 6.dp.toPx()))
            },
    )
}

/**
 * What the audio preview shows around the waveform: [title] and [subtitle]
 * (the tags the file will carry; blank when there are none), how it is encoded
 * ([formatLabel]), the effects in use and how the file is cut into parts. The
 * file name and format are not repeated here: the top bar shows them.
 */
data class AudioCard(
    val title: String,
    val subtitle: String,
    val formatLabel: String,
    val activeEffects: List<String>,
    val split: Split,
    /** The file's cover picture, shown in the badge; null for none. */
    val cover: Uri? = null,
)

/** Where a file of [durationMs] is cut by [split]. */
internal fun splitPoints(split: Split, durationMs: Long): List<Long> = when (split) {
    Split.Disabled -> emptyList()
    is Split.ByDuration -> {
        val every = split.seconds.coerceAtLeast(1) * 1000L
        generateSequence(every) { it + every }.takeWhile { it < durationMs }.take(MAX_PARTS).toList()
    }
    is Split.ByParts -> {
        val count = split.count.coerceIn(1, MAX_PARTS)
        (1 until count).map { durationMs * it / count }
    }
}

private const val MAX_PARTS = 200

private fun msAt(x: Float, width: Float, durationMs: Long): Long =
    if (width <= 0f) 0L else ((x / width).coerceIn(0f, 1f) * durationMs).toLong()

private const val BAR_WIDTH_DP = 4
private const val BAR_GAP_DP = 3

/** How many bars either side of the playhead swell with the level. */
private const val SWELL_BARS = 8f

/** Share of the way to the level the badge's shape covers in one frame. */
private const val SHAPE_FOLLOW = 0.18f

/** Share of the way to a higher level covered in one frame. */
private const val LEVEL_ATTACK = 0.5f

/** How long a seek may take to show in the player's position before the playhead follows it anyway. */
private const val HANDOVER_TIMEOUT_MS = 600L

/** How close the player's position must come to a seek for the playhead to follow it again. */
private const val HANDOVER_TOLERANCE_MS = 250L

/** Share of the level kept from one frame to the next once the sound drops. */
private const val LEVEL_RELEASE = 0.88f

private const val SPIN_DEGREES_PER_SECOND = 24f
