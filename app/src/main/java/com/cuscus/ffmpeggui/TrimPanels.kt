package com.cuscus.ffmpeggui

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.preview.Waveform
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.Timeline
import com.cuscus.ffmpeggui.preview.smallFrameAt
import com.cuscus.ffmpeggui.preview.smallPicture
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * The two trim panels: one clip (which part of its file it shows) and all the
 * clips together (the whole-video trim, which leaves the clips untouched).
 */

/** Roughly one frame: keeps a scrub to the end of a clip from landing on the next one. */
private const val FRAME_MS = 40L

/**
 * Chooses which part of its file [clip] shows. The frame under the handle being
 * dragged is shown in the preview.
 *
 * @param clipStartPlayingMs where the clip starts in the playing sequence, or
 *                           null when the whole-video trim leaves it out.
 * @param cutIntoMs          how much of the clip's start the whole-video trim
 *                           cuts away (0 when it does not reach the clip).
 * @param revision           bump it when the clip may have changed from outside
 *                           (undo / redo): the handles are then placed again.
 */
@Composable
fun ClipTrimPanel(
    clip: Clip,
    source: EditorSource,
    clipStartPlayingMs: Long?,
    cutIntoMs: Long,
    revision: Int,
    onTrimClip: (Long, Long) -> Unit,
    onSeekPlaying: (Long) -> Unit,
    /** For an audio file: the file's outline, drawn instead of its frames (null while it is read). */
    soundOnly: Boolean = false,
    waveform: Waveform? = null,
) {
    if (source.isStill) {
        // A picture has nothing to trim: only how long it stays on screen.
        StillDurationPanel(clip = clip, onLength = { lengthMs -> onTrimClip(0L, lengthMs) })
        return
    }
    val clipEndMs = clip.endMs
    if (source.durationMs <= 0 || clipEndMs == null) {
        // The length of the file is not known yet.
        Text(
            text = stringResource(R.string.clip_trim_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val currentClip by rememberUpdatedState(clip)
    val currentStart by rememberUpdatedState(clipStartPlayingMs)
    val currentCut by rememberUpdatedState(cutIntoMs)

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.trim_start, formatSeconds(clip.startMs)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.clip_trim_length, formatSeconds(clip.lengthMs)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.trim_end, formatSeconds(clipEndMs)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    // The trimmer keeps its own handle positions while dragging, so it is rebuilt only
    // for another clip or after an undo.
    if (soundOnly) {
        key(clip.id, revision) {
            AudioTimelineTrimmer(
                durationMs = source.durationMs,
                startTimeMs = clip.startMs,
                endTimeMs = clipEndMs,
                onRangeChange = { startMs, endMs -> onTrimClip(startMs, endMs) },
                waveform = waveform,
            )
        }
    } else key(clip.id, revision) {
        VideoTimelineTrimmer(
            inputUri = source.uri,
            durationMs = source.durationMs,
            startTimeMs = clip.startMs,
            endTimeMs = clipEndMs,
            onRangeChange = { startMs, endMs -> onTrimClip(startMs, endMs) },
            onScrub = { sourceMs ->
                val start = currentStart ?: return@VideoTimelineTrimmer
                val trimmed = currentClip
                val end = trimmed.endMs ?: sourceMs
                val draggingStart = sourceMs - trimmed.startMs < end - sourceMs
                // The start handle shows the clip's first frame that plays; the end handle
                // the frame just before the cut. The playing clip may begin later than the
                // clip itself, where the whole-video trim cuts into it.
                val offsetInClip = if (draggingStart) {
                    0L
                } else {
                    (sourceMs - trimmed.startMs - FRAME_MS - currentCut).coerceAtLeast(0L)
                }
                onSeekPlaying(start + offsetInClip)
            },
        )
    }
    Text(
        text = stringResource(R.string.clip_trim_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Shortest and longest a still can stay on screen, in seconds. */
private const val STILL_MIN_SECONDS = 0.5f
private const val STILL_MAX_SECONDS = 30f

/** How long the still [clip] stays on screen, by tenths of a second. */
@Composable
private fun StillDurationPanel(clip: Clip, onLength: (Long) -> Unit) {
    val seconds = clip.lengthMs / 1000f
    ValueSlider(
        label = stringResource(R.string.still_duration),
        valueText = formatSeconds(clip.lengthMs),
        value = seconds.coerceIn(STILL_MIN_SECONDS, STILL_MAX_SECONDS),
        onValueChange = { picked ->
            val tenths = Math.round(picked.coerceIn(STILL_MIN_SECONDS, STILL_MAX_SECONDS) * 10f)
            val lengthMs = tenths * 100L
            if (lengthMs != clip.lengthMs) onLength(lengthMs)
        },
        valueRange = STILL_MIN_SECONDS..STILL_MAX_SECONDS,
        icon = Icons.Rounded.Timer,
        defaultValue = DEFAULT_STILL_SECONDS,
        startLabel = formatSeconds((STILL_MIN_SECONDS * 1000).toLong()),
        endLabel = formatSeconds((STILL_MAX_SECONDS * 1000).toLong()),
    )
    Text(
        text = stringResource(R.string.still_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Length of a new still, as the editor makes it. */
private const val DEFAULT_STILL_SECONDS = 3f

/**
 * Trims all the clips together, as one video, without changing them.
 * Positions are on the timeline (the clips end to end).
 */
@Composable
fun WholeTrimPanel(
    timeline: Timeline,
    sources: List<EditorSource>,
    trimStartMs: Long,
    trimEndMs: Long?,
    /** Bump it when the trim may have changed from outside (undo / redo). */
    revision: Int,
    /** Show the frame at this position of the playing sequence. */
    onSeekPlaying: (Long) -> Unit,
    onTrimWindow: (Long, Long?) -> Unit,
    /** For an audio file: each clip shows its sound's outline instead of a frame. */
    waveformOf: ((Uri) -> Waveform?)? = null,
) {
    val totalMs = timeline.durationMs
    val windowEndMs = (trimEndMs ?: totalMs).coerceAtMost(totalMs)

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.trim_start, formatSeconds(trimStartMs)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.trim_end, formatSeconds(windowEndMs)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (totalMs > 0) {
        // Rebuilt when the sequence changes length, since the handles are positioned in
        // pixels, and after an undo or redo, which may move them.
        key(totalMs, revision) {
            SequenceTrimmer(
                timeline = timeline,
                sources = sources,
                startMs = trimStartMs,
                endMs = windowEndMs,
                onRangeChange = { startMs, endMs ->
                    // An end handle parked on the far right means "to the end".
                    onTrimWindow(startMs, endMs.takeIf { it < totalMs })
                },
                // The start handle always shows the first frame that is kept.
                onScrubStart = { onSeekPlaying(0L) },
                onScrubEnd = { startMs, endMs -> onSeekPlaying((endMs - startMs - FRAME_MS).coerceAtLeast(0L)) },
                waveformOf = waveformOf,
            )
        }
    }

    Text(
        text = stringResource(R.string.timeline_trim_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Two handles over the whole sequence. Positions are on the timeline. */
@Composable
private fun SequenceTrimmer(
    timeline: Timeline,
    sources: List<EditorSource>,
    startMs: Long,
    endMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubEnd: (startMs: Long, endMs: Long) -> Unit,
    waveformOf: ((Uri) -> Waveform?)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val totalMs = timeline.durationMs
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)

    var startPx by remember { mutableFloatStateOf(0f) }
    var endPx by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var activeHandle by remember { mutableIntStateOf(NO_HANDLE) }
    var lastReportMs by remember { mutableLongStateOf(0L) }

    val handleWidthPx = with(density) { 14.dp.toPx() }
    val minGapPx = with(density) { 28.dp.toPx() }
    val grabDistancePx = with(density) { 36.dp.toPx() }

    fun positionMs(px: Float): Long = if (widthPx > 0f) (px / widthPx * totalMs).toLong() else 0L

    // Every report rebuilds the preview playlist, so they are spaced out while dragging.
    fun report(handle: Int, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastReportMs < REPORT_INTERVAL_MS) return
        lastReportMs = now
        val startMs = positionMs(startPx)
        val endMs = positionMs(endPx)
        currentOnRangeChange(startMs, endMs)
        if (handle == START_HANDLE) currentOnScrubStart() else currentOnScrubEnd(startMs, endMs)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onSizeChanged { size ->
                // Place the handles once the width is known.
                if (widthPx == 0f && size.width > 0 && totalMs > 0) {
                    widthPx = size.width.toFloat()
                    startPx = startMs.toFloat() / totalMs * widthPx
                    endPx = endMs.toFloat() / totalMs * widthPx
                }
            },
    ) {
        ClipBlocks(timeline = timeline, sources = sources, selectedClipId = null, waveformOf = waveformOf, modifier = Modifier.fillMaxSize())

        Canvas(modifier = Modifier.fillMaxSize()) {
            val shade = Color.Black.copy(alpha = 0.55f)
            drawRect(color = shade, size = Size(startPx, size.height))
            drawRect(color = shade, topLeft = Offset(endPx, 0f), size = Size((size.width - endPx).coerceAtLeast(0f), size.height))
            drawRoundRect(
                color = primary,
                topLeft = Offset(startPx, 0f),
                size = Size((endPx - startPx).coerceAtLeast(0f), size.height),
                cornerRadius = CornerRadius(20.dp.toPx()),
                style = Stroke(width = 3.dp.toPx()),
            )
            // Pill handles with a grip line.
            listOf(startPx, endPx - handleWidthPx).forEach { left ->
                drawRoundRect(color = primary, topLeft = Offset(left, 0f), size = Size(handleWidthPx, size.height), cornerRadius = CornerRadius(handleWidthPx / 2f))
                drawLine(
                    color = onPrimary,
                    start = Offset(left + handleWidthPx / 2f, size.height * 0.35f),
                    end = Offset(left + handleWidthPx / 2f, size.height * 0.65f),
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(totalMs) {
                    // Horizontal only, so that a vertical swipe still scrolls the panel.
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val toStart = kotlin.math.abs(offset.x - startPx)
                            val toEnd = kotlin.math.abs(offset.x - endPx)
                            activeHandle = when {
                                toStart < grabDistancePx && toStart <= toEnd -> START_HANDLE
                                toEnd < grabDistancePx -> END_HANDLE
                                else -> NO_HANDLE
                            }
                            if (activeHandle != NO_HANDLE) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (activeHandle != NO_HANDLE) change.consume()
                            when (activeHandle) {
                                START_HANDLE -> {
                                    startPx = (startPx + dragAmount).coerceIn(0f, (endPx - minGapPx).coerceAtLeast(0f))
                                    report(START_HANDLE, force = false)
                                }
                                END_HANDLE -> {
                                    endPx = (endPx + dragAmount).coerceIn((startPx + minGapPx).coerceAtMost(widthPx), widthPx)
                                    report(END_HANDLE, force = false)
                                }
                            }
                        },
                        onDragEnd = {
                            if (activeHandle != NO_HANDLE) {
                                // The last position always gets through.
                                report(activeHandle, force = true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            activeHandle = NO_HANDLE
                        },
                        onDragCancel = { activeHandle = NO_HANDLE },
                    )
                },
        )
    }
}

private const val NO_HANDLE = -1
private const val START_HANDLE = 0
private const val END_HANDLE = 1
private const val REPORT_INTERVAL_MS = 40L

/** One block per clip, as wide as the clip is long. */
@Composable
private fun ClipBlocks(
    timeline: Timeline,
    sources: List<EditorSource>,
    selectedClipId: String?,
    modifier: Modifier = Modifier,
    waveformOf: ((Uri) -> Waveform?)? = null,
) {
    Row(modifier = modifier) {
        timeline.clips.forEachIndexed { index, clip ->
            val selected = clip.id == selectedClipId
            val source = sources.firstOrNull { it.id == clip.sourceId }
            // Width in proportion to the length, so that positions map straight to pixels.
            val weight = clip.lengthMs.coerceAtLeast(1L).toFloat()

            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                if (source != null && waveformOf != null) {
                    // The clip's part of its sound, mirrored about the middle.
                    val waveform = waveformOf(source.uri)
                    val color = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 6.dp)) {
                        val step = 3.dp.toPx()
                        val bar = 2.dp.toPx()
                        val bars = (size.width / step).toInt().coerceAtLeast(1)
                        val msPerBar = clip.lengthMs.toDouble() / bars
                        for (i in 0 until bars) {
                            val from = clip.startMs + (i * msPerBar).toLong()
                            val peak = waveform?.peakBetween(from, from + msPerBar.toLong()) ?: 0.05f
                            val half = (peak.coerceIn(0f, 1f) * size.height / 2f).coerceAtLeast(bar / 2f)
                            drawRoundRect(
                                color = color.copy(alpha = if (selected) 0.95f else 0.7f),
                                topLeft = Offset(i * step, size.height / 2f - half),
                                size = Size(bar, half * 2f),
                                cornerRadius = CornerRadius(bar / 2f),
                            )
                        }
                    }
                } else if (source != null) {
                    val thumbnail = rememberClipThumbnail(source.uri, clip.startMs, source.isStill)
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = if (selected) 1f else 0.65f,
                            modifier = Modifier.fillMaxSize().padding(if (selected) 2.dp else 1.dp),
                        )
                    }
                }
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp),
                )
                if (!clip.effects.isNeutral) {
                    // The clip has effects of its own.
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = stringResource(R.string.timeline_clip_effects),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

/**
 * First frame of a clip (the picture, for a [still]), decoded off the main thread.
 * Null until ready or when there is no picture.
 */
@Composable
private fun rememberClipThumbnail(uri: Uri, positionMs: Long, still: Boolean): ImageBitmap? {
    val context = LocalContext.current
    // Rounded to the second: dragging a trim handle should not decode a frame per pixel.
    val second = positionMs / 1000
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, uri, second, still) {
        value = withContext(Dispatchers.IO) {
            if (still) return@withContext smallPicture(context, uri, THUMBNAIL_PX)?.asImageBitmap()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                smallFrameAt(retriever, second * 1_000_000L, THUMBNAIL_PX)?.asImageBitmap()
            } catch (e: Exception) {
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Already released or never initialised: nothing to clean up.
                }
            }
        }
    }
    return thumbnail
}

private const val THUMBNAIL_PX = 160

/** "12.3s", or "1:02.3" from one minute up. */
private fun formatSeconds(ms: Long): String {
    val tenths = ms.coerceAtLeast(0L) / 100
    val minutes = tenths / 600
    val seconds = (tenths % 600) / 10
    val tenth = tenths % 10
    return if (minutes > 0) {
        String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenth)
    } else {
        String.format(Locale.US, "%d.%ds", seconds, tenth)
    }
}
