@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialShapes
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.semantics.Role
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.expressive.springPress
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.core.model.TextOverlay
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.positionChange
import com.cuscus.ffmpeggui.core.preview.OutputClock
import com.cuscus.ffmpeggui.core.timeline.LaneSpan
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import com.cuscus.ffmpeggui.core.timeline.TimeSpan
import com.cuscus.ffmpeggui.preview.Waveform
import com.cuscus.ffmpeggui.preview.decodeWaveform
import kotlinx.coroutines.runInterruptible
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.Timeline
import com.cuscus.ffmpeggui.preview.smallFrameAt
import com.cuscus.ffmpeggui.preview.smallPicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import kotlin.math.ln
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The editor's timeline, laid out like the mobile video editors people know:
 *
 *   ruler          0:00    0:05    0:10
 *   video track    [clip 1 frames][clip 2 frames]          [+]
 *   text rows         [title]      [caption]
 *   picture rows   [logo..........]  [sticker]
 *   sound rows        [music.............]
 *                          [voice-over..]       <- overlapping items take a row each
 *   add row        + Text     + Overlay     + Audio
 *                             |  <- playhead, fixed in the middle
 *
 * The playhead stays in the middle and the footage slides under it: drag (or
 * fling) to move through the video, pinch to zoom. Everything is drawn on one
 * Canvas, reading the moving position only while drawing, so playback redraws
 * the timeline without recomposing it.
 *
 * Positions are on the editor's timeline: the clips end to end, before any
 * speed change, before the whole-video trim (which is shown shaded). Texts,
 * pictures and sounds are placed on the exported video's clock and drawn
 * through [OutputClock]. A selected one is moved by dragging it and trimmed by
 * dragging its ends; its edges snap to the cuts, the playhead and the other items.
 */

/**
 * What the timeline keeps while a tool panel hides it: the zoom, how far the
 * lanes are scrolled, and the decoded frames and waveforms. Created with
 * [rememberTimelineViewState] above the place where the timeline comes and goes.
 */
@Stable
class TimelineViewState internal constructor(context: Context) {
    internal val filmstrip = FilmstripCache(context)
    internal val waveforms = WaveformCache(context)
    /** Pixels per millisecond; 0 until the timeline has been measured once. */
    internal val zoom = mutableFloatStateOf(0f)
    /** How far the lanes under the video track are scrolled down, in pixels. */
    internal val laneScroll = mutableFloatStateOf(0f)

    /**
     * The waveform of the sound file at [uri], or null until it has been decoded
     * (or when it cannot be). Read in composition, it recomposes when it arrives.
     */
    fun waveform(uri: Uri): Waveform? = waveforms.waveform(uri)
}

@Composable
fun rememberTimelineViewState(): TimelineViewState {
    val context = LocalContext.current
    val state = remember { TimelineViewState(context.applicationContext) }
    DisposableEffect(state) {
        onDispose {
            state.filmstrip.release()
            state.waveforms.release()
        }
    }
    return state
}

@Composable
fun EditorTimeline(
    timeline: Timeline,
    sources: List<EditorSource>,
    trimStartMs: Long,
    trimEndMs: Long?,
    playback: PreviewPlayback,
    viewState: TimelineViewState,
    selectedClipId: String?,
    /** Captions and pictures, sounds: all placed on the exported video's clock, which [clock] maps. */
    textOverlays: List<TextOverlay>,
    imageOverlays: List<ImageOverlay>,
    sounds: List<AudioClip>,
    clock: OutputClock,
    /** The text, picture or sound that has the focus (at most one), or null. */
    selectedItemId: String?,
    /** A clip was tapped (null = empty space: nothing is selected any more). */
    onSelectClip: (String?) -> Unit,
    /** An edge of the selected clip was dragged: the part of its file it now shows. */
    onTrimClip: (String, Long, Long) -> Unit = { _, _, _ -> },
    /**
     * The join after a clip (its id) was tapped: its transition into the next one.
     * Null draws no joins.
     */
    onJunction: ((String) -> Unit)? = null,
    /** A text, picture or sound was tapped. */
    onSelectItem: (LaneKind, String) -> Unit,
    /** A sound was moved or one of its edges dragged: its new span, as one change. */
    onEditSound: (String, SoundSpan) -> Unit,
    /**
     * A text or overlay was moved or trimmed: its new span, as one change (a
     * video overlay's is a [SoundSpan], the part of its file it plays).
     */
    onEditOverlay: (String, LaneSpan<*>) -> Unit,
    /** Show the frame at this timeline position. */
    onScrub: (Long) -> Unit,
    onAddClip: () -> Unit,
    /** Adds a text, a picture or a sound at the playhead. */
    onAdd: (LaneKind) -> Unit,
    /** The kinds of lane shown, in order (an audio file has only sounds). */
    kinds: List<LaneKind> = LaneKind.entries,
    /** The clips are drawn as their sound's waveform rather than as frames. */
    soundClips: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    // Every label on screen at once, measured once: the drawing runs at every frame.
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val colors = MaterialTheme.colorScheme
    val cache = viewState.filmstrip

    val totalMs = timeline.durationMs
    val starts = remember(timeline) { timeline.clips.indices.map { timeline.startOf(it) } }
    val sourcesById = remember(sources) { sources.associateBy { it.id } }

    // Sizes in pixels.
    val rulerHeight = with(density) { RULER_HEIGHT.toPx() }
    val trackTop = rulerHeight + with(density) { ROW_GAP.toPx() }
    val trackHeight = with(density) { TRACK_HEIGHT.toPx() }
    val lanesTop = trackTop + trackHeight + with(density) { ROW_GAP.toPx() }
    val lanesViewport = with(density) { (TIMELINE_HEIGHT - RULER_HEIGHT - ROW_GAP - TRACK_HEIGHT - ROW_GAP).toPx() }
    val minPxPerMs = with(density) { MIN_DP_PER_SECOND.toPx() } / 1000f
    val maxPxPerMs = with(density) { MAX_DP_PER_SECOND.toPx() } / 1000f
    val handleGrabPx = with(density) { SOUND_HANDLE_GRAB.toPx() }
    val snapPx = with(density) { SNAP_DISTANCE.toPx() }

    val untitledText = stringResource(R.string.tool_text)
    val untitledImage = stringResource(R.string.tool_image)

    // Everything placed on the video, each kind on as many rows as its overlaps need.
    val items = remember(textOverlays, imageOverlays, sounds, untitledText, untitledImage) {
        laneItems(textOverlays, imageOverlays, sounds, untitledText, untitledImage)
    }
    val lanes = remember(items, density, kinds) {
        LaneLayout(
            kinds = kinds,
            rowCounts = kinds.associateWith { kind -> items.filter { it.kind == kind }.maxOfOrNull { it.row + 1 } ?: 0 },
            laneHeight = with(density) { LANE_HEIGHT.toPx() },
            soundHeight = with(density) { SOUND_ROW_HEIGHT.toPx() },
            gap = with(density) { LANE_GAP.toPx() },
            hasItems = items.isNotEmpty(),
        )
    }

    // Zoom (pixels per millisecond); 0 until the width is known.
    var pxPerMs by viewState.zoom
    var laneScroll by viewState.laneScroll
    var widthPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(widthPx, totalMs > 0) {
        if (pxPerMs == 0f && widthPx > 0f && totalMs > 0) {
            // At first the whole project spans about one and a half screens.
            pxPerMs = (widthPx * 1.5f / totalMs).coerceIn(minPxPerMs, maxPxPerMs)
        }
    }
    val maxLaneScroll = (lanes.contentHeight - lanesViewport).coerceAtLeast(0f)
    LaunchedEffect(maxLaneScroll) { if (laneScroll > maxLaneScroll) laneScroll = maxLaneScroll }
    // An item just selected (or added) is scrolled into view.
    LaunchedEffect(selectedItemId, lanes) {
        val item = items.firstOrNull { it.id == selectedItemId } ?: return@LaunchedEffect
        val top = lanes.top(item.kind, item.row)
        val bottom = top + lanes.height(item.kind)
        if (top < laneScroll) laneScroll = top
        if (bottom > laneScroll + lanesViewport) laneScroll = (bottom - lanesViewport).coerceIn(0f, maxLaneScroll)
    }

    // While the user drags, the timeline is moved by hand; otherwise it follows the playhead.
    val drag = remember { DragState() }
    val currentTrimStart by rememberUpdatedState(trimStartMs)
    val currentTotal by rememberUpdatedState(totalMs)
    val currentOnScrub by rememberUpdatedState(onScrub)
    fun position(): Float =
        if (drag.active) drag.positionMs.floatValue
        else (currentTrimStart + playback.playheadMs.longValue).toFloat().coerceIn(0f, currentTotal.toFloat())

    val scrollState = rememberScrollableState { delta ->
        // Nothing to move: the lanes' vertical scroll hands what it does not use on to this
        // one as a zero shift, outside any gesture of its own. Taking the view then would
        // keep it forever (no gesture ends to hand it back to the playhead).
        if (delta == 0f || pxPerMs <= 0f || currentTotal <= 0) return@rememberScrollableState 0f
        if (!drag.active) {
            drag.positionMs.floatValue = position()
            drag.active = true
            playback.isPlaying.value = false
        }
        val before = drag.positionMs.floatValue
        val after = (before - delta / pxPerMs).coerceIn(0f, currentTotal.toFloat())
        drag.positionMs.floatValue = after
        val now = System.nanoTime()
        if (now - drag.lastScrubNanos >= SCRUB_INTERVAL_NANOS) {
            drag.lastScrubNanos = now
            currentOnScrub(after.toLong())
        }
        (before - after) * pxPerMs
    }
    // When the finger (and the fling) stops: show the exact frame, then hand back to the
    // playhead once the preview has reached it, so the timeline does not jump.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress && drag.active) {
            val target = drag.positionMs.floatValue
            currentOnScrub(target.toLong())
            withTimeoutOrNull(HANDOVER_TIMEOUT_MS) {
                snapshotFlow { currentTrimStart + playback.playheadMs.longValue }
                    .first { abs(it - target) < HANDOVER_TOLERANCE_MS }
            }
            drag.active = false
        }
    }
    // The lanes scroll up and down when there are more of them than fit.
    val currentMaxLaneScroll by rememberUpdatedState(maxLaneScroll)
    val laneScrollState = rememberScrollableState { delta ->
        val before = laneScroll
        laneScroll = (before - delta).coerceIn(0f, currentMaxLaneScroll)
        before - laneScroll
    }

    val currentTimeline by rememberUpdatedState(timeline)
    val currentStarts by rememberUpdatedState(starts)
    val currentItems by rememberUpdatedState(items)
    val currentLanes by rememberUpdatedState(lanes)
    val currentClock by rememberUpdatedState(clock)
    val currentSelectedItem by rememberUpdatedState(selectedItemId)
    val currentOnSelect by rememberUpdatedState(onSelectClip)
    val currentOnSelectItem by rememberUpdatedState(onSelectItem)
    val currentOnEditSound by rememberUpdatedState(onEditSound)
    val currentOnEditOverlay by rememberUpdatedState(onEditOverlay)
    val currentOnAdd by rememberUpdatedState(onAdd)
    val currentSelectedClip by rememberUpdatedState(selectedClipId)
    val currentOnTrimClip by rememberUpdatedState(onTrimClip)
    val currentSources by rememberUpdatedState(sourcesById)
    val currentOnJunction by rememberUpdatedState(onJunction)

    // While an edge or an item is dragged the view holds still (the preview shows the
    // edge, not the playhead), and it moves by itself while the finger rests near a side.
    val scope = rememberCoroutineScope()
    val edgeZonePx = with(density) { EDGE_SCROLL_ZONE.toPx() }
    val edgeSpeedPx = with(density) { EDGE_SCROLL_SPEED.toPx() }
    var releaseTarget by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(releaseTarget) {
        val target = releaseTarget ?: return@LaunchedEffect
        currentOnScrub(target.toLong())
        withTimeoutOrNull(HANDOVER_TIMEOUT_MS) {
            snapshotFlow { currentTrimStart + playback.playheadMs.longValue }
                .first { abs(it - target) < HANDOVER_TOLERANCE_MS }
        }
        drag.active = false
        releaseTarget = null
    }

    /** Holds the view where it is: from now on it moves only by hand, or near a side. */
    fun holdView() {
        releaseTarget = null
        if (!drag.active) {
            drag.positionMs.floatValue = position()
            drag.active = true
        }
        playback.isPlaying.value = false
    }

    /** Hands the view back to the playhead, which is first brought to [targetMs]. */
    fun releaseView(targetMs: Float) {
        drag.positionMs.floatValue = targetMs
        releaseTarget = targetMs
    }

    /**
     * Moves the view by itself, frame by frame, while [fingerX] rests within
     * [EDGE_SCROLL_ZONE] of a side: faster the closer it is (up to [EDGE_SCROLL_SPEED]),
     * never before the start nor past [maxMs]. [onShift] gets each move in pixels, so
     * that what is dragged goes with the view and stays under the finger. A tick says
     * when it starts.
     */
    fun edgeScroll(fingerX: () -> Float, maxMs: () -> Float, onShift: (Float) -> Unit): Job = scope.launch {
        var last = withFrameNanos { it }
        var pushing = false
        while (isActive) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val x = fingerX()
            val push = when {
                x < edgeZonePx -> -(1f - x / edgeZonePx)
                x > widthPx - edgeZonePx -> 1f - (widthPx - x) / edgeZonePx
                else -> 0f
            }.coerceIn(-1f, 1f)
            if ((push != 0f) != pushing) {
                pushing = push != 0f
                if (pushing) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            if (push == 0f || pxPerMs <= 0f) continue
            val before = drag.positionMs.floatValue
            val after = (before + push * abs(push) * edgeSpeedPx * seconds / pxPerMs).coerceIn(0f, max(maxMs(), before))
            drag.positionMs.floatValue = after
            val moved = (after - before) * pxPerMs
            if (moved != 0f) onShift(moved)
        }
    }

    // An edge of the selected clip being dragged; the clip is drawn as it will be, saved on release.
    var clipDrag by remember { mutableStateOf<ClipDrag?>(null) }

    /**
     * The part of its file the clip of [state] shows as the finger has it now,
     * and whether its edge snapped (to the playhead, a cut, the end or the edge
     * of a text, picture or sound).
     */
    fun trimmed(state: ClipDrag): Triple<Long, Long, Boolean> {
        var dtMs = (state.dxPx.floatValue / pxPerMs).toDouble()
        val edgeAt = state.timelineStart + (if (state.edge == ItemEdge.START) 0L else state.endMs - state.startMs) + dtMs
        val target = SoundSpan.snap(edgeAt, state.targets, (snapPx / pxPerMs).toDouble())
        val snapped = target != null
        if (target != null) dtMs += target - edgeAt
        val dt = Math.round(dtMs)
        return if (state.edge == ItemEdge.START) {
            Triple((state.startMs + dt).coerceIn(0L, state.endMs - Timeline.MIN_CLIP_MS), state.endMs, snapped)
        } else {
            Triple(state.startMs, (state.endMs + dt).coerceIn(state.startMs + Timeline.MIN_CLIP_MS, state.limitMs), snapped)
        }
    }

    // An item being moved or trimmed by hand; drawn where the finger takes it, saved on release.
    var itemDrag by remember { mutableStateOf<ItemDrag?>(null) }

    /** Where [ms] of the timeline is drawn, for the current position and zoom. */
    fun xOf(ms: Double, width: Float): Float = width / 2f + ((ms - position()) * pxPerMs).toFloat()

    /** Drawn edges (left, right) of [span] on the timeline, in milliseconds. */
    fun edges(span: LaneSpan<*>): Pair<Double, Double> {
        val a = currentClock.timelineMs(span.startMs)
        val b = currentClock.timelineMs(span.endMs)
        return min(a, b) to max(a, b)
    }

    /** [original] as the drag [state] has it now, and the target it snapped to (if any). */
    fun <T : LaneSpan<T>> dragged(original: T, state: ItemDrag): Pair<T, Double?> {
        val dxMs = (state.dxPx.floatValue / pxPerMs).toDouble()
        val tolerance = (snapPx / pxPerMs).toDouble()
        fun snap(ms: Double) = SoundSpan.snap(ms, state.targets, tolerance)
        return when (state.edge) {
            ItemEdge.BODY -> {
                val (left, right) = edges(original)
                val snapLeft = snap(left + dxMs)
                val snapRight = snap(right + dxMs)
                val shift = when {
                    snapLeft != null && (snapRight == null || abs(snapLeft - left - dxMs) <= abs(snapRight - right - dxMs)) -> snapLeft - left - dxMs
                    snapRight != null -> snapRight - right - dxMs
                    else -> 0.0
                }
                val start = currentClock.timelineMs(original.startMs) + dxMs + shift
                original.movedTo(currentClock.outputMs(start)) to (snapLeft ?: snapRight)
            }
            ItemEdge.START -> {
                val at = currentClock.timelineMs(original.startMs) + dxMs
                val snapped = snap(at)
                original.withStartEdgeMovedBy(currentClock.outputMs(snapped ?: at) - original.startMs) to snapped
            }
            ItemEdge.END -> {
                val at = currentClock.timelineMs(original.endMs) + dxMs
                val snapped = snap(at)
                original.withEndEdgeMovedBy(currentClock.outputMs(snapped ?: at) - original.endMs) to snapped
            }
        }
    }

    /** The item of [state] as dragged now, whatever its kind. */
    fun draggedSpan(state: ItemDrag): Pair<LaneSpan<*>, Double?> = when (val original = state.original) {
        is SoundSpan -> dragged(original, state)
        is TimeSpan -> dragged(original, state)
        else -> original to null
    }

    /** What an edge being dragged snaps to: the playhead, the cuts, the ends and the other items. */
    fun snapTargets(exceptId: String): List<Double> {
        val targets = mutableListOf(position().toDouble(), currentTrimStart.toDouble())
        targets += currentClock.timelineMs(currentClock.outputDurationMs)
        currentStarts.forEachIndexed { index, start ->
            targets += start.toDouble()
            targets += (start + currentTimeline.clips[index].lengthMs).toDouble()
        }
        currentItems.filter { it.id != exceptId }.forEach { item ->
            val (left, right) = edges(item.span)
            targets += left
            targets += right
        }
        return targets
    }

    val addLabels = mapOf(
        LaneKind.TEXT to stringResource(R.string.lane_text),
        LaneKind.IMAGE to stringResource(R.string.lane_image),
        LaneKind.SOUND to stringResource(R.string.lane_audio),
    )
    val surfaceColor = colors.surfaceContainerHigh
    // What does not make it into the video: washed out in light mode, dimmed in dark mode.
    val veil = if (colors.surface.luminance() > 0.5f) colors.surface.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.55f)
    val primary = colors.primary
    val onSurface = colors.onSurface
    val muted = colors.onSurfaceVariant
    // Each kind wears its tool family's tone (see ToolFamily): captions and pictures the
    // overlay tone, sounds the sound tone. A selected item takes the full tone.
    val itemColors = mapOf(
        LaneKind.TEXT to colors.secondaryContainer,
        LaneKind.IMAGE to colors.secondary.copy(alpha = 0.45f),
        LaneKind.SOUND to colors.tertiaryContainer,
    )
    val selectedColors = mapOf(
        LaneKind.TEXT to colors.secondary,
        LaneKind.IMAGE to colors.secondary,
        LaneKind.SOUND to colors.tertiary,
    )
    val soundWave = colors.onTertiaryContainer.copy(alpha = 0.45f)
    val selectedSoundWave = colors.onTertiary.copy(alpha = 0.55f)
    val mutedStyle = remember(muted) { TextStyle(fontSize = 10.sp, color = muted) }
    val itemStyle = remember(onSurface) { TextStyle(fontSize = 10.sp, color = onSurface) }
    val onSecondary = colors.onSecondary
    val onTertiary = colors.onTertiary
    val selectedStyles = remember(onSecondary, onTertiary) {
        mapOf(
            LaneKind.TEXT to TextStyle(fontSize = 10.sp, color = onSecondary),
            LaneKind.IMAGE to TextStyle(fontSize = 10.sp, color = onSecondary),
            LaneKind.SOUND to TextStyle(fontSize = 10.sp, color = onTertiary),
        )
    }
    val badgeStyle = remember { TextStyle(fontSize = 10.sp, color = Color.White) }
    val onPrimary = colors.onPrimary
    val trimBadgeStyle = remember(onPrimary) { TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onPrimary) }
    val clipShape = remember { Path() }
    val junctionGlyph = remember { Path() }

    // Read in composition, so that the timeline is drawn again when a waveform arrives.
    val waveforms = sounds.associate { it.uri to viewState.waveform(it.uri) }
    val soundsById = remember(sounds) { sounds.associateBy { it.id } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TIMELINE_HEIGHT)
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Two fingers zoom; seen before the scroll handler so that it does not pan.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2 && pxPerMs > 0f) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    pxPerMs = (pxPerMs * zoom).coerceIn(minPxPerMs, maxPxPerMs)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .scrollable(state = scrollState, orientation = Orientation.Horizontal)
                .scrollable(state = laneScrollState, orientation = Orientation.Vertical)
                // The selected clip: its edges trim it (CapCut style), next to the Trim tool.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (pxPerMs <= 0f) return@awaitEachGesture
                        val id = currentSelectedClip ?: return@awaitEachGesture
                        val index = currentTimeline.indexOf(id).takeIf { it >= 0 } ?: return@awaitEachGesture
                        val clip = currentTimeline.clips[index]
                        val end = clip.endMs ?: return@awaitEachGesture
                        val slack = handleGrabPx / 3f
                        if (down.position.y < trackTop - slack || down.position.y > trackTop + trackHeight + slack) return@awaitEachGesture
                        val start = currentStarts.getOrNull(index) ?: return@awaitEachGesture
                        val left = xOf(start.toDouble(), size.width.toFloat())
                        val right = xOf((start + clip.lengthMs).toDouble(), size.width.toFloat())
                        val x = down.position.x
                        val edge = when {
                            abs(x - left) <= handleGrabPx && abs(x - left) <= abs(x - right) -> ItemEdge.START
                            abs(x - right) <= handleGrabPx -> ItemEdge.END
                            else -> return@awaitEachGesture
                        }
                        var overSlop = 0f
                        val moved = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            overSlop = over
                        } ?: return@awaitEachGesture
                        val duration = currentSources[clip.sourceId]?.durationMs ?: 0L
                        // What the edge snaps to: all but where it starts from (its clip's own edge,
                        // which the next clip's start shares).
                        val edgeFrom = (start + if (edge == ItemEdge.START) 0L else clip.lengthMs).toDouble()
                        val state = ClipDrag(
                            clipId = id,
                            edge = edge,
                            startMs = clip.startMs,
                            endMs = end,
                            limitMs = if (duration > 0L) duration else Long.MAX_VALUE,
                            timelineStart = start,
                            targets = snapTargets("").filter { abs(it - edgeFrom) > 1.0 },
                        )
                        state.dxPx.floatValue = overSlop
                        clipDrag = state
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        holdView()
                        var lastSnapped = false
                        var lastScrub = 0L
                        var fingerX = moved.position.x
                        // The frame at the edge, where the clip still shows it.
                        fun scrubEdge() {
                            val (from, to, snapped) = trimmed(state)
                            if (snapped && !lastSnapped) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lastSnapped = snapped
                            val at = start + (if (edge == ItemEdge.START) from - clip.startMs else to - clip.startMs)
                            val now = System.nanoTime()
                            if (at in start..(start + clip.lengthMs) && now - lastScrub >= SCRUB_INTERVAL_NANOS) {
                                lastScrub = now
                                currentOnScrub(at)
                            }
                        }
                        // Near a side the timeline moves on by itself, the edge with it (an end may
                        // go past the end of the timeline as far as the file allows).
                        val autoScroll = edgeScroll(
                            fingerX = { fingerX },
                            maxMs = { (currentTotal + max(0L, trimmed(state).second - end)).toFloat() },
                        ) { shift ->
                            state.dxPx.floatValue += shift
                            scrubEdge()
                        }
                        val finished = horizontalDrag(moved.id) { change ->
                            state.dxPx.floatValue += change.positionChange().x
                            fingerX = change.position.x
                            change.consume()
                            scrubEdge()
                        }
                        autoScroll.cancel()
                        val (from, to) = trimmed(state)
                        clipDrag = null
                        val committed = finished && (from != clip.startMs || to != end)
                        // A start trimmed was drawn with the clips before it moved along; once saved,
                        // those stay and what follows moves instead: the view goes with it, so that
                        // nothing seems to jump.
                        val view = drag.positionMs.floatValue
                        val shift = if (committed && edge == ItemEdge.START) (from - clip.startMs).toFloat() else 0f
                        releaseView((view - shift).coerceAtLeast(0f))
                        if (committed) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnTrimClip(id, from, to)
                        }
                    }
                }
                // The selected item: its body moves it, its edges trim it. Seen before the
                // scroll handlers, which then leave the gesture alone.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (pxPerMs <= 0f) return@awaitEachGesture
                        val id = currentSelectedItem ?: return@awaitEachGesture
                        val item = currentItems.firstOrNull { it.id == id } ?: return@awaitEachGesture
                        val top = lanesTop - laneScroll + currentLanes.top(item.kind, item.row)
                        val slack = handleGrabPx / 3f
                        if (down.position.y < max(top - slack, lanesTop) || down.position.y > top + currentLanes.height(item.kind) + slack) {
                            return@awaitEachGesture
                        }
                        val (leftMs, rightMs) = edges(item.span)
                        val left = xOf(leftMs, size.width.toFloat())
                        val right = xOf(rightMs, size.width.toFloat())
                        val x = down.position.x
                        if (x < left - handleGrabPx / 2f || x > right + handleGrabPx / 2f) return@awaitEachGesture
                        // The drawn left edge is the start, unless the whole video plays backwards.
                        val startOnLeft = currentClock.timelineMs(item.span.startMs) <= currentClock.timelineMs(item.span.endMs)
                        val nearLeft = abs(x - left) <= handleGrabPx && (right - left > handleGrabPx * 2f || x < (left + right) / 2f)
                        val nearRight = abs(x - right) <= handleGrabPx
                        val edge = when {
                            nearLeft -> if (startOnLeft) ItemEdge.START else ItemEdge.END
                            nearRight -> if (startOnLeft) ItemEdge.END else ItemEdge.START
                            else -> ItemEdge.BODY
                        }
                        var overSlop = 0f
                        val moved = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            overSlop = over
                        } ?: return@awaitEachGesture
                        holdView()
                        val state = ItemDrag(id, edge, item.span, snapTargets(id))
                        state.dxPx.floatValue = overSlop
                        itemDrag = state
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var lastSnap: Double? = null
                        var fingerX = moved.position.x
                        fun followSnap() {
                            val snapped = draggedSpan(state).second
                            if (snapped != null && snapped != lastSnap) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lastSnap = snapped
                        }
                        // Near a side the timeline moves on by itself, the item with it.
                        val autoScroll = edgeScroll(
                            fingerX = { fingerX },
                            maxMs = { max(currentTotal.toDouble(), edges(draggedSpan(state).first).second).toFloat() },
                        ) { shift ->
                            state.dxPx.floatValue += shift
                            followSnap()
                        }
                        val finished = horizontalDrag(moved.id) { change ->
                            state.dxPx.floatValue += change.positionChange().x
                            fingerX = change.position.x
                            change.consume()
                            followSnap()
                        }
                        autoScroll.cancel()
                        val result = draggedSpan(state).first
                        itemDrag = null
                        releaseView(drag.positionMs.floatValue)
                        if (finished && result != state.original) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (item.kind == LaneKind.SOUND && result is SoundSpan) {
                                currentOnEditSound(id, result)
                            } else {
                                currentOnEditOverlay(id, result)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        if (pxPerMs <= 0f) return@detectTapGestures
                        // The join between two clips: the transition from one into the other.
                        val onJoin = currentOnJunction
                        if (onJoin != null && tap.y >= trackTop && tap.y <= trackTop + trackHeight) {
                            val grab = JUNCTION_GRAB.toPx()
                            val join = (0 until currentTimeline.clips.size - 1).firstOrNull { index ->
                                // The selected clip's edges are its trim handles.
                                val clipsHere = currentTimeline.clips
                                if (clipsHere[index].id == currentSelectedClip || clipsHere[index + 1].id == currentSelectedClip) return@firstOrNull false
                                val start = currentStarts.getOrNull(index + 1) ?: return@firstOrNull false
                                abs(xOf(start.toDouble(), size.width.toFloat()) - tap.x) <= grab
                            }
                            if (join != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onJoin(currentTimeline.clips[join].id)
                                return@detectTapGestures
                            }
                        }
                        val timeMs = (position() + (tap.x - size.width / 2f) / pxPerMs).toLong()
                        // A tap on something the playhead is not on brings the playhead to it, as
                        // CapCut does: to its first frame when the playhead was before it, to its
                        // last when it was after (so that its ends are one tap away).
                        fun bringPlayheadInto(left: Double, right: Double) {
                            val now = position().toDouble()
                            val target = when {
                                now < left -> left
                                now >= right -> (right - LAST_FRAME_MS).coerceAtLeast(left)
                                else -> return
                            }
                            currentOnScrub(Math.round(target).coerceAtLeast(0L))
                        }
                        // A keyframe of what is selected: the playhead goes onto it.
                        val keyframe = selectedKeyframes(currentTimeline, currentStarts, currentSelectedClip, currentItems, currentSelectedItem, currentClock)
                            .minByOrNull { abs(it - timeMs) }
                            ?.takeIf { abs(it - timeMs) * pxPerMs <= KEYFRAME_GRAB.toPx() }
                        if (keyframe != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            currentOnScrub(keyframe)
                            return@detectTapGestures
                        }
                        when {
                            tap.y >= trackTop && tap.y <= trackTop + trackHeight -> {
                                val index = clipAt(currentTimeline, currentStarts, timeMs)
                                val clip = index?.let { currentTimeline.clips[it] }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(clip?.id)
                                if (index != null && clip != null) {
                                    val clipStart = currentStarts[index].toDouble()
                                    bringPlayheadInto(clipStart, clipStart + clip.lengthMs)
                                }
                            }
                            tap.y >= lanesTop -> when (val row = currentLanes.rowAt(tap.y - lanesTop + laneScroll, tap.x, size.width.toFloat())) {
                                is LaneRow.Item -> {
                                    val hit = currentItems.firstOrNull { item ->
                                        val (left, right) = edges(item.span)
                                        item.kind == row.kind && item.row == row.index && timeMs >= left && timeMs <= right
                                    }
                                    when {
                                        hit != null -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentOnSelectItem(hit.kind, hit.id)
                                            val (left, right) = edges(hit.span)
                                            bringPlayheadInto(left, right)
                                        }
                                        // An empty kind offers to add one, as its label says.
                                        currentItems.none { it.kind == row.kind } -> currentOnAdd(row.kind)
                                        else -> currentOnSelect(null)
                                    }
                                }
                                is LaneRow.Add -> currentOnAdd(row.kind)
                                null -> currentOnSelect(null)
                            }
                            // The ruler: nothing selected.
                            else -> currentOnSelect(null)
                        }
                    }
                },
        ) {
            if (pxPerMs <= 0f) return@Canvas
            val centre = size.width / 2f
            val positionMs = position()
            fun x(ms: Float): Float = centre + (ms - positionMs) * pxPerMs

            drawRuler(textMeasurer, positionMs, pxPerMs, rulerHeight, muted, mutedStyle, currentTotal)

            // -- Video track ------------------------------------------------------------
            val clips = currentTimeline.clips
            val gap = 2.dp.toPx()
            val corner = CornerRadius(12.dp.toPx())
            val trimming = clipDrag
            // The clips next to an edge being dragged go with it, like everything beyond
            // them: an end pushes (or pulls) the clips after it, a start the clips before
            // it, so that clips never overlap nor leave a gap while the finger moves.
            val trimIndex = trimming?.let { currentTimeline.indexOf(it.clipId) } ?: -1
            val trimmedNow = trimming?.let { trimmed(it) }
            val pushAfter = if (trimming?.edge == ItemEdge.END && trimmedNow != null) trimmedNow.second - trimming.endMs else 0L
            val pushBefore = if (trimming?.edge == ItemEdge.START && trimmedNow != null) trimmedNow.first - trimming.startMs else 0L
            clips.forEachIndexed { index, original ->
                val start = currentStarts.getOrNull(index) ?: return@forEachIndexed
                // A clip whose edge is being dragged is drawn as it will be: its kept start
                // where its start was moved to, or its end where its end was moved to.
                var clip = original
                var shift = when {
                    trimIndex < 0 -> 0L
                    index > trimIndex -> pushAfter
                    index < trimIndex -> pushBefore
                    else -> 0L
                }
                if (trimming != null && trimmedNow != null && index == trimIndex) {
                    val (from, to) = trimmedNow
                    clip = original.copy(startMs = from, endMs = to)
                    if (trimming.edge == ItemEdge.START) shift = from - original.startMs
                }
                val left = x((start + shift).toFloat())
                val right = x((start + shift + clip.lengthMs).toFloat()).coerceAtLeast(left + MIN_CLIP_WIDTH_DP.dp.toPx())
                if (right < 0f || left > size.width) return@forEachIndexed
                val rect = RoundRect(left + gap / 2, trackTop, right - gap / 2, trackTop + trackHeight, corner)
                val source = sourcesById[clip.sourceId]
                clipShape.reset()
                clipShape.addRoundRect(rect)
                clipPath(clipShape) {
                    drawRect(surfaceColor, topLeft = Offset(rect.left, rect.top), size = Size(rect.width, rect.height))
                    if (source != null && soundClips) {
                        drawClipWaveform(viewState.waveform(source.uri), clip, rect.left, rect.right, trackTop, trackHeight, pxPerMs, primary)
                    } else if (source != null) {
                        drawFilmstrip(cache, source.uri, source.isStill, clip, rect.left, rect.right, trackTop, trackHeight, pxPerMs)
                    }
                }
                // A clip with effects of its own carries a dot; a clip at another speed says so.
                if (!clip.effects.isNeutral) {
                    drawCircle(primary, radius = 4.dp.toPx(), center = Offset(rect.right - 8.dp.toPx(), rect.top + 8.dp.toPx()))
                }
                // A speed curve is drawn along the clip (fast up, slow down; in playing order).
                clip.effects.curve?.let { curve ->
                    drawSpeedCurve(curve, clip.effects.reversed, rect.left, rect.right, trackTop, trackHeight, primary)
                }
                if (clip.effects.changesSpeed && rect.width > 40.dp.toPx()) {
                    drawLabel(
                        textMeasurer,
                        (if (clip.effects.curve != null) "~" else "") + String.format(Locale.US, "%.2gx", clip.effects.effectiveSpeed),
                        Offset(rect.left + 4.dp.toPx(), rect.bottom - 18.dp.toPx()),
                        maxWidth = rect.width - 8.dp.toPx(),
                        background = Color.Black.copy(alpha = 0.6f),
                        style = badgeStyle,
                    )
                }
                val selected = clip.id == selectedClipId
                if (selected) {
                    // The selected clip: a frame, and its edges as tabs to drag (an open-ended
                    // clip, still waiting for its length, has only the frame).
                    drawTrimHandles(
                        left = rect.left,
                        right = rect.right,
                        top = rect.top,
                        height = rect.height,
                        corner = corner.x,
                        color = primary,
                        chevron = onPrimary,
                        active = trimming?.takeIf { it.clipId == clip.id }?.edge,
                        handles = clip.endMs != null,
                    )
                    // Its keyframes, where they are in its footage.
                    clip.effects.keyframes.frames.forEach { frame ->
                        val inClip = frame.atMs - clip.startMs
                        if (inClip in 0..clip.lengthMs) {
                            val at = x((start + shift + inClip).toFloat())
                            drawKeyframe(Offset(at, rect.bottom - 9.dp.toPx()), 6.dp.toPx(), abs(start + shift + inClip - positionMs) * pxPerMs < 6.dp.toPx(), primary)
                        }
                    }
                    // While an edge moves, how long the clip will be.
                    if (trimming != null && trimming.clipId == clip.id) {
                        val text = String.format(Locale.US, "%.1f s", clip.lengthMs / 1000f)
                        val measured = textMeasurer.measure(text, trimBadgeStyle)
                        val padH = 8.dp.toPx()
                        val padV = 3.dp.toPx()
                        val pill = Size(measured.size.width + padH * 2f, measured.size.height + padV * 2f)
                        val pillLeft = ((rect.left + rect.right - pill.width) / 2f).coerceIn(0f, max(0f, size.width - pill.width))
                        val pillTop = rect.top + (rect.height - pill.height) / 2f
                        drawRoundRect(primary, Offset(pillLeft, pillTop), pill, CornerRadius(pill.height / 2f))
                        drawText(measured, topLeft = Offset(pillLeft + padH, pillTop + padV))
                    }
                } else {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.4f),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }

            // The joins between clips, each a button: a line for a plain cut, a bow tie for a
            // transition (filled in the accent).
            if (currentOnJunction != null && trimming == null && clips.size > 1) {
                val radius = JUNCTION_RADIUS.toPx()
                val cy = trackTop + trackHeight / 2f
                for (index in 0 until clips.size - 1) {
                    // The selected clip's edges are its trim handles.
                    if (clips[index].id == selectedClipId || clips[index + 1].id == selectedClipId) continue
                    val cx = x((currentStarts.getOrNull(index + 1) ?: continue).toFloat())
                    if (cx < -radius || cx > size.width + radius) continue
                    val on = clips[index].effects.transition != null
                    val centre = Offset(cx, cy)
                    drawCircle(if (on) primary else surfaceColor, radius, centre)
                    drawCircle(Color.Black.copy(alpha = 0.35f), radius, centre, style = Stroke(width = 1.dp.toPx()))
                    val glyph = if (on) onPrimary else onSurface
                    val g = radius * 0.42f
                    if (on) {
                        junctionGlyph.reset()
                        junctionGlyph.moveTo(cx - g, cy - g * 0.85f)
                        junctionGlyph.lineTo(cx, cy)
                        junctionGlyph.lineTo(cx - g, cy + g * 0.85f)
                        junctionGlyph.close()
                        junctionGlyph.moveTo(cx + g, cy - g * 0.85f)
                        junctionGlyph.lineTo(cx, cy)
                        junctionGlyph.lineTo(cx + g, cy + g * 0.85f)
                        junctionGlyph.close()
                        drawPath(junctionGlyph, glyph)
                    } else {
                        drawLine(glyph, Offset(cx, cy - g), Offset(cx, cy + g), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }

            // The parts the whole-video trim cuts away: veiled, inside the clips' own rounded
            // shapes (the empty space around the clips stays as it is).
            val keptEndMs = (trimEndMs ?: currentTotal).coerceAtMost(currentTotal)
            if (currentTotal > 0 && (currentTrimStart > 0L || keptEndMs < currentTotal)) {
                val keptFrom = x(currentTrimStart.toFloat())
                val keptTo = x(keptEndMs.toFloat())
                clips.forEachIndexed { index, clip ->
                    val start = currentStarts.getOrNull(index) ?: return@forEachIndexed
                    val left = x(start.toFloat()) + gap / 2
                    val right = x((start + clip.lengthMs).toFloat()).coerceAtLeast(left + MIN_CLIP_WIDTH_DP.dp.toPx()) - gap / 2
                    if (right < 0f || left > size.width || (left >= keptFrom && right <= keptTo)) return@forEachIndexed
                    clipShape.reset()
                    clipShape.addRoundRect(RoundRect(left, trackTop, right, trackTop + trackHeight, corner))
                    clipPath(clipShape) {
                        if (keptFrom > left) drawRect(veil, Offset(left, trackTop), Size(min(keptFrom, right) - left, trackHeight))
                        if (keptTo < right) {
                            val from = max(keptTo, left)
                            drawRect(veil, Offset(from, trackTop), Size(right - from, trackHeight))
                        }
                    }
                }
            }

            // -- Lanes: captions, pictures, sounds --------------------------------------
            val scroll = laneScroll.coerceIn(0f, maxLaneScroll)
            val layout = lanes
            clipRect(top = lanesTop) {
                val origin = lanesTop - scroll
                layout.kinds.forEach { kind ->
                    for (row in 0 until layout.rows(kind)) {
                        drawRect(surfaceColor.copy(alpha = 0.35f), Offset(0f, origin + layout.top(kind, row)), Size(size.width, layout.height(kind)))
                    }
                    // An empty kind shows what tapping its row does.
                    if (items.none { it.kind == kind }) {
                        val top = origin + layout.top(kind, 0)
                        drawLabel(
                            textMeasurer, addLabels.getValue(kind),
                            Offset(8.dp.toPx(), top + (layout.height(kind) - 14.dp.toPx()) / 2f),
                            size.width, null, mutedStyle,
                        )
                    }
                }
                // Once there is something placed, one row adds any kind: three tap zones.
                if (layout.hasAddRow) {
                    val top = origin + layout.addTop
                    layout.kinds.forEachIndexed { index, kind ->
                        drawLabel(
                            textMeasurer, addLabels.getValue(kind),
                            Offset(size.width * index / layout.kinds.size + 8.dp.toPx(), top + 2.dp.toPx()),
                            size.width / layout.kinds.size - 12.dp.toPx(), null, mutedStyle,
                        )
                    }
                }

                val videoEndX = x(currentClock.timelineMs(currentClock.outputDurationMs).toFloat())
                val dragging = itemDrag
                items.forEach { item ->
                    val selected = item.id == selectedItemId
                    val span = if (dragging != null && dragging.id == item.id) draggedSpan(dragging).first else item.span
                    val (leftMs, rightMs) = edges(span)
                    val left = x(leftMs.toFloat())
                    val right = max(x(rightMs.toFloat()), left + MIN_CLIP_WIDTH_DP.dp.toPx())
                    if (right < 0f || left > size.width) return@forEach
                    val top = origin + layout.top(item.kind, item.row)
                    val height = layout.height(item.kind)
                    val sound = if (item.kind == LaneKind.SOUND) soundsById[item.id] else null
                    if (sound != null && span is SoundSpan) {
                        drawSound(
                            sound = sound,
                            span = span,
                            clock = currentClock,
                            waveform = waveforms[sound.uri],
                            left = left,
                            right = right,
                            top = top,
                            height = height,
                            positionMs = positionMs,
                            pxPerMs = pxPerMs,
                            fill = if (selected) selectedColors.getValue(item.kind) else itemColors.getValue(item.kind),
                            wave = if (selected) selectedSoundWave else soundWave,
                            selected = selected,
                            handleColor = primary,
                            chevronColor = onPrimary,
                            activeEdge = dragging?.takeIf { it.id == item.id }?.edge,
                            startOnLeft = currentClock.timelineMs(span.startMs) <= currentClock.timelineMs(span.endMs),
                        )
                    } else {
                        drawOverlayItem(
                            left = left,
                            right = right,
                            top = top,
                            height = height,
                            fill = if (selected) selectedColors.getValue(item.kind) else itemColors.getValue(item.kind),
                            lengthMs = span.endMs - span.startMs,
                            fadeInSeconds = item.fadeInSeconds,
                            fadeOutSeconds = item.fadeOutSeconds,
                            selected = selected,
                            handleColor = primary,
                            chevronColor = onPrimary,
                            activeEdge = dragging?.takeIf { it.id == item.id }?.edge,
                            startOnLeft = currentClock.timelineMs(span.startMs) <= currentClock.timelineMs(span.endMs),
                        )
                    }
                    val label = if (sound != null && span is SoundSpan) soundLabel(sound, span) else item.label
                    drawLabel(
                        textMeasurer, label,
                        Offset(max(left, 0f) + (if (selected) 14.dp else 6.dp).toPx(), top + (height - 14.dp.toPx()) / 2f),
                        maxWidth = right - max(left, 0f) - 28.dp.toPx(),
                        background = null,
                        style = if (selected) selectedStyles.getValue(item.kind) else itemStyle,
                    )
                    // The selected one's keyframes, where they are on the video.
                    if (selected) {
                        item.keyframes.frames.forEach { frame ->
                            val atMs = currentClock.timelineMs(span.startMs + frame.atMs)
                            val at = x(atMs.toFloat())
                            if (at in left..right) {
                                drawKeyframe(Offset(at, top + height / 2f), 5.dp.toPx(), abs(atMs - positionMs) * pxPerMs < 6.dp.toPx(), primary)
                            }
                        }
                    }
                    // What runs past the end of the video is cut by the export.
                    if (right > videoEndX) {
                        val from = max(left, videoEndX)
                        drawRoundRect(
                            veil,
                            Offset(from, top),
                            Size(right - from, height),
                            CornerRadius(8.dp.toPx()),
                        )
                    }
                }
            }

            // -- Playhead ---------------------------------------------------------------
            // A thin line with a pill-shaped grip on the ruler, in the text's colour (light or dark).
            drawLine(onSurface, Offset(centre, 0f), Offset(centre, size.height), strokeWidth = 2.dp.toPx())
            val grip = Size(8.dp.toPx(), 16.dp.toPx())
            drawRoundRect(
                onSurface,
                topLeft = Offset(centre - grip.width / 2f, 2.dp.toPx()),
                size = grip,
                cornerRadius = CornerRadius(grip.width / 2f),
            )
        }

        // Add a file at the end, always at hand like in other editors: a four-sided
        // cookie (MaterialShapes) that squishes under the finger.
        val addInteraction = remember { MutableInteractionSource() }
        val addShape = remember { PolygonShape(MaterialShapes.Cookie4Sided) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, trackTop.toInt()) }
                .padding(end = 8.dp)
                .springPress(addInteraction, pressedScale = 0.85f)
                .size(TRACK_HEIGHT)
                .clip(addShape)
                .background(colors.primaryContainer)
                .clickable(interactionSource = addInteraction, indication = LocalIndication.current, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddClip()
                },
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.editor_add_clip),
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** What can be placed freely on the video, each kind on rows of its own. */
enum class LaneKind { TEXT, IMAGE, SOUND }

/** One text, picture or sound on its lane: when it plays, on which row, how it is labelled. */
private class LaneEntry(
    val kind: LaneKind,
    val id: String,
    val span: LaneSpan<*>,
    val row: Int,
    val label: String,
    val fadeInSeconds: Float,
    val fadeOutSeconds: Float,
    /** Its keyframes (times from its start), drawn as diamonds while it is selected. */
    val keyframes: Keyframes = Keyframes.NONE,
)

/** Every item placed on the video, with the rows their overlaps need (per kind). */
private fun laneItems(
    texts: List<TextOverlay>,
    pictures: List<ImageOverlay>,
    sounds: List<AudioClip>,
    untitledText: String,
    untitledImage: String,
): List<LaneEntry> {
    // Captions and pictures bound to a clip (from older projects) have no place of their own.
    val freeTexts = texts.filter { it.clipId == null }
    val freePictures = pictures.filter { it.clipId == null }
    val textRows = TimeSpan.rows(freeTexts.map { it.span })
    val pictureRows = TimeSpan.rows(freePictures.map { it.laneSpan })
    val soundRows = TimeSpan.rows(sounds.map { it.span })
    return freeTexts.mapIndexed { i, t ->
        LaneEntry(LaneKind.TEXT, t.id, t.span, textRows[i], t.text.ifBlank { untitledText }, t.fadeInSeconds, t.fadeOutSeconds, t.keyframes)
    } + freePictures.mapIndexed { i, p ->
        // A video is marked with a play sign; trimming it trims the part of its file it plays.
        val name = p.fileName.ifBlank { untitledImage }
        LaneEntry(LaneKind.IMAGE, p.id, p.laneSpan, pictureRows[i], if (p.isVideo) "\u25B6 $name" else name, p.fadeInSeconds, p.fadeOutSeconds, p.keyframes)
    } + sounds.mapIndexed { i, s ->
        LaneEntry(LaneKind.SOUND, s.id, s.span, soundRows[i], s.name, s.fadeInSeconds, s.fadeOutSeconds, s.keyframes)
    }
}

/**
 * A text or picture on its row: a rounded block with its fades shaded; when
 * [selected], a white outline and a grip at each end, the handles that trim it.
 */
private fun DrawScope.drawOverlayItem(
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    fill: Color,
    lengthMs: Long,
    fadeInSeconds: Float,
    fadeOutSeconds: Float,
    selected: Boolean,
    handleColor: Color,
    chevronColor: Color,
    activeEdge: ItemEdge?,
    startOnLeft: Boolean,
) {
    val corner = CornerRadius(height / 2f)
    drawRoundRect(fill, Offset(left, top), Size(right - left, height), corner)
    drawFadeRamps(left, right, top, height, lengthMs, fadeInSeconds, fadeOutSeconds)
    if (selected) {
        drawTrimHandles(left, right, top, height, corner.x, handleColor, chevronColor, activeEdge, startOnLeft = startOnLeft)
    }
}

/** How close to a keyframe's diamond a tap moves the playhead onto it. */
private val KEYFRAME_GRAB = 14.dp

/** Where the keyframes of the selected clip, or of the selected text, picture or sound, are on the timeline. */
private fun selectedKeyframes(
    timeline: Timeline,
    starts: List<Long>,
    clipId: String?,
    items: List<LaneEntry>,
    itemId: String?,
    clock: OutputClock,
): List<Long> {
    val index = clipId?.let { timeline.indexOf(it) } ?: -1
    if (index >= 0) {
        val clip = timeline.clips[index]
        val start = starts.getOrNull(index) ?: return emptyList()
        return clip.effects.keyframes.frames.map { it.atMs - clip.startMs }.filter { it in 0..clip.lengthMs }.map { start + it }
    }
    val item = items.firstOrNull { it.id == itemId } ?: return emptyList()
    return item.keyframes.frames.map { Math.round(clock.timelineMs(item.span.startMs + it.atMs)) }
}

/**
 * A keyframe: a small white diamond outlined in [accent], filled with it when
 * the playhead is on it ([current]).
 */
private fun DrawScope.drawKeyframe(center: Offset, radius: Float, current: Boolean, accent: Color) {
    val diamond = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    drawPath(diamond, if (current) accent else Color.White)
    drawPath(diamond, if (current) Color.White else accent, style = Stroke(width = 1.5.dp.toPx()))
}

/**
 * A clip's speed curve, drawn along it: normal speed across the middle of the
 * track, faster above, slower below (on a log scale, 10x at the edges). In
 * playing order, so mirrored for a clip played backwards.
 */
private fun DrawScope.drawSpeedCurve(curve: SpeedCurve, reversed: Boolean, left: Float, right: Float, top: Float, height: Float, color: Color) {
    if (right - left < 4f) return
    val middle = top + height / 2f
    val reach = height * 0.42f
    val limit = ln(SpeedCurve.MAX_SPEED.toDouble())
    val path = Path()
    val steps = ((right - left) / 3f).toInt().coerceIn(8, 160)
    for (k in 0..steps) {
        val f = k.toDouble() / steps
        val speed = curve.speedAt(if (reversed) 1.0 - f else f)
        val x = left + (right - left) * f.toFloat()
        val y = middle - (ln(speed.toDouble()) / limit).toFloat() * reach
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawLine(Color.White.copy(alpha = 0.35f), Offset(left, middle), Offset(right, middle), strokeWidth = 1f)
    drawPath(path, Color.Black.copy(alpha = 0.5f), style = Stroke(width = 4.5f))
    drawPath(path, color, style = Stroke(width = 2.5f))
}


/** Fades: the ends they soften, as darker ramps over the block. */
private fun DrawScope.drawFadeRamps(left: Float, right: Float, top: Float, height: Float, lengthMs: Long, fadeInSeconds: Float, fadeOutSeconds: Float) {
    if (lengthMs <= 0) return
    val shade = Color.Black.copy(alpha = 0.25f)
    val lengthPx = right - left
    if (fadeInSeconds > 0f) {
        val w = (lengthPx * fadeInSeconds * 1000f / lengthMs).coerceAtMost(lengthPx)
        drawPath(Path().apply { moveTo(left, top); lineTo(left + w, top); lineTo(left, top + height); close() }, shade)
    }
    if (fadeOutSeconds > 0f) {
        val w = (lengthPx * fadeOutSeconds * 1000f / lengthMs).coerceAtMost(lengthPx)
        drawPath(Path().apply { moveTo(right, top); lineTo(right - w, top); lineTo(right, top + height); close() }, shade)
    }
}

/**
 * What a selected block (a clip, a text, a picture, a sound) wears: a frame in
 * [color] and, with [handles], a tab at each end to drag, rounded outside like
 * the block ([corner]) and straight inside, with a chevron pointing out. The tab
 * being dragged ([active]; which side is the start depends on [startOnLeft]) is
 * wider and glows. On a block too short for both, the tabs share its width.
 */
private fun DrawScope.drawTrimHandles(
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    corner: Float,
    color: Color,
    chevron: Color,
    active: ItemEdge?,
    handles: Boolean = true,
    startOnLeft: Boolean = true,
) {
    val frame = 2.5.dp.toPx()
    val width = right - left
    drawRoundRect(
        color = color,
        topLeft = Offset(left + frame / 2f, top + frame / 2f),
        size = Size((width - frame).coerceAtLeast(0f), height - frame),
        cornerRadius = CornerRadius((corner - frame / 2f).coerceAtLeast(0f)),
        style = Stroke(width = frame),
    )
    if (!handles) return
    val round = CornerRadius(min(corner, height / 2f))
    listOf(true, false).forEach { onLeft ->
        val edge = if (onLeft == startOnLeft) ItemEdge.START else ItemEdge.END
        val dragged = active == edge || active == ItemEdge.BODY
        val tab = min((if (active == edge) HANDLE_ACTIVE_WIDTH else HANDLE_WIDTH).toPx(), width / 2f)
        val tabLeft = if (onLeft) left else right - tab
        val shape = Path().apply {
            addRoundRect(
                if (onLeft) {
                    RoundRect(tabLeft, top, tabLeft + tab, top + height, topLeftCornerRadius = round, bottomLeftCornerRadius = round)
                } else {
                    RoundRect(tabLeft, top, tabLeft + tab, top + height, topRightCornerRadius = round, bottomRightCornerRadius = round)
                },
            )
        }
        if (dragged && active != ItemEdge.BODY) drawPath(shape, color.copy(alpha = 0.35f), style = Stroke(width = 6.dp.toPx()))
        drawPath(shape, color)
        // The chevron, pointing out: the way the edge lengthens the block.
        val cx = tabLeft + tab / 2f
        val cy = top + height / 2f
        val arm = min(4.dp.toPx(), height / 5f)
        val out = if (onLeft) -1f else 1f
        val mark = Path().apply {
            moveTo(cx - out * arm / 2f, cy - arm)
            lineTo(cx + out * arm / 2f, cy)
            lineTo(cx - out * arm / 2f, cy + arm)
        }
        if (tab >= arm * 2.5f) drawPath(mark, chevron, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * One sound on its row: a rounded block with its waveform; when [selected], a
 * white outline and a grip at each end, the handles that trim it.
 */
private fun DrawScope.drawSound(
    sound: AudioClip,
    span: SoundSpan,
    clock: OutputClock,
    waveform: Waveform?,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    positionMs: Float,
    pxPerMs: Float,
    fill: Color,
    wave: Color,
    selected: Boolean,
    handleColor: Color,
    chevronColor: Color,
    activeEdge: ItemEdge?,
    startOnLeft: Boolean,
) {
    val corner = CornerRadius(8.dp.toPx())
    drawRoundRect(fill, Offset(left, top), Size(right - left, height), corner)

    // The waveform, one bar every few pixels, sampled where each pixel falls in the file
    // (speed and repeats included). A muted sound keeps only a faint outline.
    if (waveform != null) {
        val centre = size.width / 2f
        fun fileMsAt(px: Float): Long {
            val timelineMs = positionMs + (px - centre) / pxPerMs
            return span.fileMsAt(clock.outputMs(timelineMs.toDouble()))
        }
        val step = 3.dp.toPx()
        val barWidth = 1.5.dp.toPx()
        val middle = top + height / 2f
        var px = max(left, 0f) + step / 2f
        val end = min(right, size.width)
        while (px < end) {
            val a = fileMsAt(px - step / 2f)
            val b = fileMsAt(px + step / 2f)
            val gain = if (sound.muted) 0.15f else sound.volume.coerceAtMost(1f)
            // Across the seam of a repetition the two ends are measured on their own.
            val peak = if (abs(b - a) > span.rangeMs / 2) waveform.peakBetween(a, a) else waveform.peakBetween(min(a, b), max(a, b))
            val level = kotlin.math.sqrt(peak * gain)
            val barHeight = (height - 6.dp.toPx()) * level.coerceIn(0.04f, 1f)
            drawLine(wave, Offset(px, middle - barHeight / 2f), Offset(px, middle + barHeight / 2f), strokeWidth = barWidth)
            px += step
        }
    }

    // Fades: the corners they soften, as ramps of the block's own tone.
    val fadeShade = Color.Black.copy(alpha = 0.25f)
    val lengthPx = right - left
    if (sound.fadeInSeconds > 0f && span.lengthMs > 0) {
        val w = (lengthPx * sound.fadeInSeconds * 1000f / span.lengthMs).coerceAtMost(lengthPx)
        val ramp = Path().apply { moveTo(left, top); lineTo(left + w, top); lineTo(left, top + height); close() }
        drawPath(ramp, fadeShade)
    }
    if (sound.fadeOutSeconds > 0f && span.lengthMs > 0) {
        val w = (lengthPx * sound.fadeOutSeconds * 1000f / span.lengthMs).coerceAtMost(lengthPx)
        val ramp = Path().apply { moveTo(right, top); lineTo(right - w, top); lineTo(right, top + height); close() }
        drawPath(ramp, fadeShade)
    }

    // Where each repetition starts again.
    if (span.loops > 1 && span.lengthMs > 0) {
        for (repeat in 1 until span.loops) {
            val seam = left + lengthPx * repeat / span.loops
            drawLine(Color.White.copy(alpha = 0.5f), Offset(seam, top + 3.dp.toPx()), Offset(seam, top + height - 3.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
    }

    if (selected) drawTrimHandles(left, right, top, height, corner.x, handleColor, chevronColor, activeEdge, startOnLeft = startOnLeft)
}

/** A sound's name with what changes how it plays: "music  1.5x  3 times  muted". */
private fun soundLabel(sound: AudioClip, span: SoundSpan): String = buildString {
    append(sound.name)
    if (span.rate != 1f) append("  ").append(String.format(Locale.US, "%.2gx", span.rate))
    if (span.loops > 1) append("  ").append(span.loops).append("\u00d7")
    if (sound.pitchSemitones != 0f) append("  ").append(if (sound.pitchSemitones > 0f) "+" else "").append(Math.round(sound.pitchSemitones)).append(" st")
    if (sound.muted) append("  ").append("\u2205")
}

/**
 * An edge of the selected clip being dragged: the part of its file it showed
 * when the finger came down ([startMs] to [endMs], at most [limitMs]), where it
 * starts on the timeline, and how far the finger has gone since (state, so the
 * drawing follows).
 */
private class ClipDrag(
    val clipId: String,
    val edge: ItemEdge,
    val startMs: Long,
    val endMs: Long,
    val limitMs: Long,
    val timelineStart: Long,
    /** Where the edge snaps, on the timeline (milliseconds). */
    val targets: List<Double>,
) {
    val dxPx = mutableFloatStateOf(0f)
}

/** Which part of the selected item a drag holds. */
private enum class ItemEdge { START, END, BODY }

/**
 * An item being moved or trimmed: its span when the finger came down, how far
 * the finger has gone since (state, so the drawing follows), and what its edges
 * snap to.
 */
private class ItemDrag(val id: String, val edge: ItemEdge, val original: LaneSpan<*>, val targets: List<Double>) {
    val dxPx = mutableFloatStateOf(0f)
}

/** A row of the lanes area: one of the rows of a kind, or the row that adds any kind. */
private sealed interface LaneRow {
    data class Item(val kind: LaneKind, val index: Int) : LaneRow
    data class Add(val kind: LaneKind) : LaneRow
}

/**
 * How the lanes under the video track are stacked, in pixels from the top of
 * the lanes area (before scrolling): the rows of captions, of pictures and of
 * sounds (as many as their overlaps need, at least one each), then, once
 * anything is placed, a row to add any kind.
 */
private class LaneLayout(
    val kinds: List<LaneKind>,
    rowCounts: Map<LaneKind, Int>,
    val laneHeight: Float,
    val soundHeight: Float,
    val gap: Float,
    hasItems: Boolean,
) {
    private val counts = kinds.associateWith { max(1, rowCounts[it] ?: 0) }
    private val firstTop: Map<LaneKind, Float>
    val hasAddRow = hasItems
    val addTop: Float
    val contentHeight: Float

    init {
        var top = 0f
        val tops = HashMap<LaneKind, Float>()
        kinds.forEach { kind ->
            tops[kind] = top
            top += counts.getValue(kind) * (height(kind) + gap)
        }
        firstTop = tops
        addTop = top
        contentHeight = if (hasAddRow) top + laneHeight else top - gap
    }

    fun rows(kind: LaneKind): Int = counts[kind] ?: 0

    fun height(kind: LaneKind): Float = if (kind == LaneKind.SOUND) soundHeight else laneHeight

    fun top(kind: LaneKind, row: Int): Float = (firstTop[kind] ?: 0f) + row * (height(kind) + gap)

    /** The row at [y]; on the add row, the kind whose third of the [width] holds [x]. */
    fun rowAt(y: Float, x: Float, width: Float): LaneRow? {
        if (y < 0f) return null
        kinds.forEach { kind ->
            for (row in 0 until rows(kind)) {
                val top = top(kind, row)
                if (y in top..top + height(kind)) return LaneRow.Item(kind, row)
            }
        }
        if (hasAddRow && y in addTop..addTop + laneHeight) {
            val zone = (x / width * kinds.size).toInt().coerceIn(0, kinds.lastIndex)
            return LaneRow.Add(kinds[zone])
        }
        return null
    }
}

/**
 * Drag bookkeeping. [active] and [positionMs] are state, so that the drawing,
 * which reads them, runs again when they change (and stops or starts following
 * the playhead); none of it recomposes anything.
 */
private class DragState {
    var active by mutableStateOf(false)
    val positionMs = mutableFloatStateOf(0f)
    var lastScrubNanos = 0L
}

/** Index of the clip under [timeMs], or null past the end. */
private fun clipAt(timeline: Timeline, starts: List<Long>, timeMs: Long): Int? {
    if (timeMs < 0) return null
    timeline.clips.forEachIndexed { index, clip ->
        val start = starts.getOrNull(index) ?: return null
        if (timeMs >= start && timeMs < start + max(clip.lengthMs, 1L)) return index
    }
    return null
}

/** Tick marks and times, spaced so that labels never crowd. */
private fun DrawScope.drawRuler(
    measurer: androidx.compose.ui.text.TextMeasurer,
    positionMs: Float,
    pxPerMs: Float,
    height: Float,
    color: Color,
    style: TextStyle,
    totalMs: Long,
) {
    val centre = size.width / 2f
    val minLabelGap = 64.dp.toPx()
    val stepMs = RULER_STEPS_MS.firstOrNull { it * pxPerMs >= minLabelGap } ?: RULER_STEPS_MS.last()
    val visibleFrom = max(0f, positionMs - centre / pxPerMs)
    val visibleTo = min(totalMs.toFloat(), positionMs + centre / pxPerMs)
    var tick = floor(visibleFrom / stepMs).toLong() * stepMs
    while (tick <= visibleTo) {
        val x = centre + (tick - positionMs) * pxPerMs
        drawLine(color, Offset(x, height - 6.dp.toPx()), Offset(x, height), strokeWidth = 1.dp.toPx())
        val half = x + stepMs / 2f * pxPerMs
        if (tick + stepMs / 2 <= visibleTo) {
            drawLine(color.copy(alpha = 0.5f), Offset(half, height - 3.dp.toPx()), Offset(half, height), strokeWidth = 1.dp.toPx())
        }
        drawLabel(measurer, formatRulerTime(tick, withTenths = stepMs < 1000), Offset(x + 3.dp.toPx(), 0f), 60.dp.toPx(), null, style)
        tick += stepMs
    }
}

/**
 * One line of small text, cut to [maxWidth], optionally on a dark pill.
 *
 * The text is measured at its own width, which does not change from frame to
 * frame, so that the measurer's cache keeps it while the timeline scrolls; a
 * text too wide is measured again with an ellipsis at a width rounded down to
 * a step (a few cache entries per label, not one per frame).
 */
private fun DrawScope.drawLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    topLeft: Offset,
    maxWidth: Float,
    background: Color?,
    style: TextStyle,
) {
    if (maxWidth < 12.dp.toPx()) return
    val whole = measurer.measure(text = text, style = style, softWrap = false, maxLines = 1)
    val layout = if (whole.size.width <= maxWidth) {
        whole
    } else {
        val step = LABEL_WIDTH_STEP.dp.toPx()
        val width = (floor(maxWidth / step) * step).toInt()
        if (width < step) return
        measurer.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = width),
        )
    }
    if (background != null) {
        drawRoundRect(
            background,
            topLeft = Offset(topLeft.x - 2.dp.toPx(), topLeft.y),
            size = Size(layout.size.width + 4.dp.toPx(), layout.size.height.toFloat()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
    drawText(layout, topLeft = topLeft)
}

/** Widths a label cut short is measured at are multiples of this (dp). */
private const val LABEL_WIDTH_STEP = 16

/**
 * Frames of [clip] side by side between [left] and [right]. Each tile shows the
 * frame at its own time, rounded so that zooming does not decode a new frame per
 * pixel. A [still] shows its one picture on every tile.
 */
private fun DrawScope.drawFilmstrip(
    cache: FilmstripCache,
    uri: Uri,
    still: Boolean,
    clip: Clip,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    pxPerMs: Float,
) {
    val tileWidth = height * TILE_ASPECT
    val tileMs = tileWidth / pxPerMs
    val stepMs = FRAME_STEPS_MS.firstOrNull { it >= tileMs } ?: FRAME_STEPS_MS.last()
    val firstTile = max(0, floor(-left / tileWidth).toInt())
    val lastTile = ceil((min(right, size.width) - left) / tileWidth).toInt()
    for (tile in firstTile until lastTile) {
        val tileLeft = left + tile * tileWidth
        if (tileLeft > right) break
        val offsetMs = (tile * tileWidth / pxPerMs).toLong()
        // Rounded down to the step, but never to footage the clip does not show.
        val lastMs = (clip.endMs ?: Long.MAX_VALUE).coerceAtLeast(clip.startMs)
        val frameMs = if (still) FilmstripCache.PICTURE else ((clip.startMs + offsetMs) / stepMs * stepMs).coerceIn(clip.startMs, lastMs)
        val frame = cache.frame(uri, frameMs) ?: continue
        // Centre-crop the frame into the tile.
        val frameAspect = frame.width.toFloat() / frame.height.coerceAtLeast(1)
        val srcWidth = if (frameAspect > TILE_ASPECT) (frame.height * TILE_ASPECT).toInt() else frame.width
        val srcHeight = if (frameAspect > TILE_ASPECT) frame.height else (frame.width / TILE_ASPECT).toInt()
        drawImage(
            image = frame,
            srcOffset = IntOffset((frame.width - srcWidth) / 2, (frame.height - srcHeight) / 2),
            srcSize = IntSize(srcWidth.coerceAtLeast(1), srcHeight.coerceAtLeast(1)),
            dstOffset = IntOffset(tileLeft.toInt(), top.toInt()),
            dstSize = IntSize(ceil(tileWidth).toInt() + 1, height.toInt()),
        )
    }
}

/**
 * A clip of an audio project: its sound's outline, mirrored about the middle of
 * the track, one bar every few pixels; flat bars while the file is being read.
 */
private fun DrawScope.drawClipWaveform(
    waveform: Waveform?,
    clip: Clip,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    pxPerMs: Float,
    color: Color,
) {
    val step = 3.dp.toPx()
    val bar = 2.dp.toPx()
    val middle = top + height / 2f
    val tallest = height / 2f - 4.dp.toPx()
    var x = max(left, 0f)
    val end = min(right, size.width)
    while (x < end) {
        val fromMs = clip.startMs + ((x - left) / pxPerMs).toLong()
        val toMs = clip.startMs + ((x + step - left) / pxPerMs).toLong()
        val peak = waveform?.peakBetween(fromMs, toMs) ?: 0.05f
        val half = (peak.coerceIn(0f, 1f) * tallest).coerceAtLeast(bar / 2f)
        drawRoundRect(color.copy(alpha = 0.85f), Offset(x, middle - half), Size(bar, half * 2f), CornerRadius(bar / 2f))
        x += step
    }
}

/** "0:05", "1:30"; "0:05.5" [withTenths]. */
private fun formatRulerTime(ms: Long, withTenths: Boolean): String {
    val seconds = ms / 1000
    return if (withTenths) {
        String.format(Locale.US, "%d:%02d.%d", seconds / 60, seconds % 60, (ms % 1000) / 100)
    } else {
        String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }
}

/**
 * Small frames of the project's files, decoded in the background. Asking for a
 * frame that is not ready returns null and queues it; drawings that asked are
 * run again when a frame arrives.
 *
 * The frames asked for last are decoded first, so that after a fling the tiles
 * on screen do not wait behind the ones flown past; old requests are dropped.
 * The decoded frames are kept up to a memory budget, least recently drawn first
 * out.
 */
internal class FilmstripCache(private val context: Context) {

    private class FrameKey(val uri: Uri, val ms: Long) {
        override fun equals(other: Any?) = other is FrameKey && other.uri == uri && other.ms == ms
        override fun hashCode() = uri.hashCode() * 31 + ms.hashCode()
    }

    /** Guards everything below that the worker and the drawing share. */
    private val lock = Any()
    private val frames = LinkedHashMap<FrameKey, ImageBitmap>(64, 0.75f, true)
    private var frameBytes = 0L
    /** Waiting to be decoded, newest last. */
    private val pending = ArrayDeque<FrameKey>()
    /** Waiting or being decoded. */
    private val queued = HashSet<FrameKey>()
    /** Frames that could not be decoded: not asked for again. */
    private val failed = HashSet<FrameKey>()

    /** Bumped when a frame arrives; read while drawing, so the drawing runs again. */
    private val arrivals = mutableIntStateOf(0)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Touched on the worker only. */
    private val retrievers = HashMap<Uri, MediaMetadataRetriever?>()

    init {
        scope.launch {
            try {
                for (signal in wake) {
                    while (true) {
                        val key = synchronized(lock) { pending.removeLastOrNull() } ?: break
                        val frame = decode(key)
                        synchronized(lock) {
                            queued.remove(key)
                            if (frame != null) store(key, frame) else markFailed(key)
                        }
                        if (frame != null) withContext(Dispatchers.Main) { arrivals.intValue++ }
                    }
                }
            } finally {
                retrievers.values.forEach { retriever ->
                    try {
                        retriever?.release()
                    } catch (e: Exception) {
                        // Already released: nothing to do.
                    }
                }
            }
        }
    }

    fun frame(uri: Uri, ms: Long): ImageBitmap? {
        arrivals.intValue
        val key = FrameKey(uri, ms)
        synchronized(lock) {
            frames[key]?.let { return it }
            if (key in failed || !queued.add(key)) return null
            pending.addLast(key)
            while (pending.size > MAX_PENDING) queued.remove(pending.removeFirst())
        }
        wake.trySend(Unit)
        return null
    }

    fun release() {
        wake.close()
        scope.cancel()
    }

    /** Called with [lock] held. */
    private fun store(key: FrameKey, frame: ImageBitmap) {
        frames.put(key, frame)?.let { frameBytes -= it.byteCount() }
        frameBytes += frame.byteCount()
        val oldest = frames.entries.iterator()
        while (frameBytes > MAX_BYTES && frames.size > 1 && oldest.hasNext()) {
            frameBytes -= oldest.next().value.byteCount()
            oldest.remove()
        }
    }

    /** Called with [lock] held. */
    private fun markFailed(key: FrameKey) {
        if (failed.size >= MAX_FAILED) failed.clear()
        failed.add(key)
    }

    private fun ImageBitmap.byteCount(): Long = width.toLong() * height * 4

    private fun decode(key: FrameKey): ImageBitmap? {
        if (key.ms == PICTURE) return smallPicture(context, key.uri, FRAME_BOX_PX)?.asImageBitmap()
        val retriever = retrievers.getOrPut(key.uri) {
            try {
                MediaMetadataRetriever().apply { setDataSource(context, key.uri) }
            } catch (e: Exception) {
                null // not a video, or unreadable: no frames for it
            }
        } ?: return null
        return try {
            smallFrameAt(retriever, key.ms * 1000, FRAME_BOX_PX)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Asked for instead of a time: the file is a still, a single picture. */
        const val PICTURE = -1L

        /** Longest side of a decoded frame; the shorter one still about fills a tile. */
        private const val FRAME_BOX_PX = 240
        /** About 24 MB of frames. */
        private const val MAX_BYTES = 24L * 1024 * 1024
        private const val MAX_PENDING = 48
        private const val MAX_FAILED = 2048
    }
}

/**
 * Waveforms of the project's sound files, decoded in the background one file at
 * a time. Asking for one that is not ready returns null and queues it; the
 * composition that asked is run again when it arrives. A file that cannot be
 * decoded is not asked for again.
 */
internal class WaveformCache(private val context: Context) {

    private val lock = Any()
    /** Decoded waveforms; a null value marks a file that could not be decoded. */
    private val waveforms = HashMap<Uri, Waveform?>()
    private val pending = ArrayDeque<Uri>()
    private val queued = HashSet<Uri>()

    /** Bumped when a waveform arrives; read by [waveform], so its readers recompose. */
    private val arrivals = mutableIntStateOf(0)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The outline read so far of the files being read, drawn until they are done. */
    private val partial = HashMap<Uri, Waveform>()

    init {
        // A few files at once, so that a long one does not hold back the others.
        repeat(WAVEFORM_WORKERS) {
            scope.launch {
                for (signal in wake) {
                    while (true) {
                        val uri = synchronized(lock) { pending.removeFirstOrNull() } ?: break
                        // More may be waiting: wake another worker for them.
                        wake.trySend(Unit)
                        // Interruptible: leaving the editor stops a long decode at once.
                        val waveform = runInterruptible {
                            decodeWaveform(context, uri) { soFar ->
                                synchronized(lock) { partial[uri] = soFar }
                                scope.launch(Dispatchers.Main) { arrivals.intValue++ }
                            }
                        }
                        synchronized(lock) {
                            waveforms[uri] = waveform
                            partial.remove(uri)
                            queued.remove(uri)
                        }
                        withContext(Dispatchers.Main) { arrivals.intValue++ }
                    }
                }
            }
        }
    }

    /**
     * The outline of [uri]: complete, or what has been read of it so far (see
     * [Waveform.complete]), or null until the first part is ready.
     */
    fun waveform(uri: Uri): Waveform? {
        arrivals.intValue
        synchronized(lock) {
            if (uri in waveforms) return waveforms[uri]
            partial[uri]?.let { return it }
            if (!queued.add(uri)) return null
            pending.addLast(uri)
        }
        wake.trySend(Unit)
        return null
    }

    fun release() {
        wake.close()
        scope.cancel()
    }
}

/** Files whose outline is read at the same time. */
private const val WAVEFORM_WORKERS = 2

private val RULER_HEIGHT = 20.dp
private val TRACK_HEIGHT = 56.dp

/** Size of the button on the join between two clips, and how far from it a tap still takes it. */
private val JUNCTION_RADIUS = 11.dp
private val JUNCTION_GRAB = 16.dp
private val LANE_HEIGHT = 20.dp
private val SOUND_ROW_HEIGHT = 28.dp
private val LANE_GAP = 4.dp
private val ROW_GAP = 6.dp

/** How far from an edge of the selected sound a finger still grabs that edge. */
private val SOUND_HANDLE_GRAB = 24.dp

/** Width of the tabs at the ends of a selected block, and of the one being dragged. */
private val HANDLE_WIDTH = 14.dp
private val HANDLE_ACTIVE_WIDTH = 20.dp

/** How close to a side the finger must be for the timeline to move by itself, and its top speed there. */
private val EDGE_SCROLL_ZONE = 56.dp
private val EDGE_SCROLL_SPEED = 900.dp

/** How close an edge being dragged must come to a cut, the playhead or another item to snap to it. */
private val SNAP_DISTANCE = 12.dp

/**
 * How far before an item's end its last frame is shown (the end itself is
 * where it is gone): a frame at 30 fps.
 */
private const val LAST_FRAME_MS = 33.0

/**
 * Total height: ruler, track, and room for the caption and picture lanes and a
 * row of sounds; more rows of sounds scroll.
 */
val TIMELINE_HEIGHT = 20.dp + 6.dp + 56.dp + 6.dp + 20.dp * 2 + 28.dp + 4.dp * 2 + 4.dp

private val MIN_DP_PER_SECOND = 4.dp
private val MAX_DP_PER_SECOND = 400.dp
private const val MIN_CLIP_WIDTH_DP = 6
/** Width / height of one filmstrip frame. */
private const val TILE_ASPECT = 1f

private const val SCRUB_INTERVAL_NANOS = 50_000_000L
private const val HANDOVER_TIMEOUT_MS = 600L
private const val HANDOVER_TOLERANCE_MS = 80L

private val RULER_STEPS_MS = listOf(500L, 1_000L, 2_000L, 5_000L, 10_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L)
private val FRAME_STEPS_MS = listOf(250L, 500L, 1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 60_000L)
