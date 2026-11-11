@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialShapes
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.Shadow
import com.cuscus.ffmpeggui.core.stems.StemKind
import com.cuscus.ffmpeggui.voice.VoiceSeparator
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material.icons.rounded.Grain
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
import androidx.compose.ui.text.TextMeasurer
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
import com.cuscus.ffmpeggui.core.model.transitionLengths
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
    /** A clip held and dragged to another place among the clips: moved by this many places. Null: clips stay put. */
    onMoveClip: ((String, Int) -> Unit)? = null,
    /**
     * The join after a clip (its id) was tapped: its transition into the next one.
     * Null draws no joins.
     */
    onJunction: ((String) -> Unit)? = null,
    /** A text, picture or sound was tapped. */
    onSelectItem: (LaneKind, String) -> Unit,
    /**
     * A keyframe of the selected clip, text, picture or sound was dragged from
     * its time to another (both on its owner's own clock, see Keyframes), as one
     * change. Null: keyframes cannot be dragged.
     */
    onMoveKeyframe: ((KeyframeOwner, Long, Long) -> Unit)? = null,
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
    /**
     * The sound of each video clip's own file drawn along the bottom of its frames
     * (see drawClipSoundBand), to cut and line sounds up by ear and eye.
     */
    clipWaveforms: Boolean = false,
    /**
     * Clips (ids) heard silent for now: played backwards, their sound not reversed
     * yet (see ReversedSound). Their sound is drawn faint, as a muted clip's.
     */
    quietClips: Set<String> = emptySet(),
    /**
     * Clips (ids) heard from their stem tracks (see AudioClip.stemOf): with
     * [soundClips], each is drawn as an empty slot (it still sets where the
     * rest of the sound is), and the row is made thin when every clip is one.
     */
    splitClips: Set<String> = emptySet(),
    /**
     * The most it may grow to, to show more rows of lanes before they scroll (the
     * tools beside the player have the whole height); it is never lower than
     * [TIMELINE_HEIGHT], nor taller than its rows need.
     */
    maxHeight: Dp = TIMELINE_HEIGHT,
    /** Told of every step of a drag, to spot a DJ's scratch (see DjScratch); null: not looked for. */
    scratch: DjScratch? = null,
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
    // How long each transition between two clips really lasts (0 where there is none), for the
    // slanted edges that show it (see transitionSlants).
    val transitionMs = remember(timeline) {
        transitionLengths(timeline.clips.map { it.effects.transition }, timeline.clips.map { it.effects.playedMs(it.lengthMs) })
    }
    val sourcesById = remember(sources) { sources.associateBy { it.id } }

    // Sizes in pixels.
    val rulerHeight = with(density) { RULER_HEIGHT.toPx() }
    val trackTop = rulerHeight + with(density) { ROW_GAP.toPx() }
    // An audio file whose every clip is heard from its stems: the clips' row is only their slots, thin.
    val allSplit = soundClips && timeline.clips.isNotEmpty() && timeline.clips.all { it.id in splitClips }
    val trackDp by animateDpAsState(if (allSplit) SPLIT_TRACK_HEIGHT else TRACK_HEIGHT, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "trackHeight")
    val trackHeight = with(density) { trackDp.toPx() }
    val lanesTop = trackTop + trackHeight + with(density) { ROW_GAP.toPx() }
    val minPxPerMs = with(density) { MIN_DP_PER_SECOND.toPx() } / 1000f
    val maxPxPerMs = with(density) { MAX_DP_PER_SECOND.toPx() } / 1000f
    val handleGrabPx = with(density) { SOUND_HANDLE_GRAB.toPx() }
    val snapPx = with(density) { SNAP_DISTANCE.toPx() }

    val untitledText = stringResource(R.string.tool_text)
    val splitSlotText = stringResource(R.string.timeline_split_slot)
    val untitledImage = stringResource(R.string.tool_image)

    // Everything placed on the video, each kind on as many rows as its overlaps need.
    val shapeNames = ShapeKind.entries.associateWith { stringResource(it.labelRes) }
    val effectNames = effectLabels()
    val items = remember(textOverlays, imageOverlays, sounds, untitledText, untitledImage, shapeNames, effectNames) {
        laneItems(textOverlays, imageOverlays, sounds, untitledText, untitledImage, shapeNames) { effectNames[it] ?: it }
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
    // As tall as its rows need, between its usual height and [maxHeight]; the rows past it scroll.
    val neededHeight = with(density) { (lanesTop + lanes.contentHeight).toDp() }
    val targetHeight = neededHeight.coerceIn(TIMELINE_HEIGHT, maxOf(TIMELINE_HEIGHT, maxHeight))
    val timelineHeight by animateDpAsState(targetHeight, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "timelineHeight")
    val lanesViewport = with(density) { (timelineHeight - RULER_HEIGHT - ROW_GAP - trackDp - ROW_GAP).toPx() }

    // Zoom (pixels per millisecond); 0 until the width is known.
    var pxPerMs by viewState.zoom
    var laneScroll by viewState.laneScroll
    var widthPx by remember { mutableFloatStateOf(0f) }
    // Pinched on past the widest zoom: the ruler, squeezed, names spans no video has, and
    // springs back when the fingers leave (see drawRuler). 1: not pinched past.
    var overPinch by remember { mutableFloatStateOf(1f) }
    val pinchBack = remember { Animatable(1f) }
    val reducedMotion = com.cuscus.ffmpeggui.onboarding.rememberReducedMotion()
    val absurdUnits = rememberAbsurdUnits()
    val absurdDepth by remember { derivedStateOf { depthPastWidest(min(overPinch, pinchBack.value)) } }
    val absurdLevel by remember { derivedStateOf { spanAt(absurdDepth, absurdUnits.size) } }
    // Each new span a tick.
    LaunchedEffect(Unit) {
        snapshotFlow { absurdLevel }.collect { level -> if (level >= 0 && overPinch < 1f) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
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
    val currentScratch by rememberUpdatedState(scratch)
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
        // A fling never turns back: only the finger scratches.
        currentScratch?.drag(delta, now)
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
    val currentTransitionMs by rememberUpdatedState(transitionMs)
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
    val currentOnMoveClip by rememberUpdatedState(onMoveClip)
    val currentSources by rememberUpdatedState(sourcesById)
    val currentOnJunction by rememberUpdatedState(onJunction)
    val currentOnMoveKeyframe by rememberUpdatedState(onMoveKeyframe)

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

    // A clip held and dragged among the others; it follows the finger, the others make room.
    var clipMove by remember { mutableStateOf<ClipMove?>(null) }

    /**
     * Where the clip of [state] would go if let go now (its new place among the
     * clips), and where every clip is drawn meanwhile: the held one under the
     * finger, the others one after another with a gap where it would land.
     */
    fun moveLayout(state: ClipMove): Pair<Int, Map<String, Long>> {
        val others = currentTimeline.clips.filter { it.id != state.clipId }
        val heldCentre = state.startMs + state.lengthMs / 2.0 + state.dxPx.floatValue / pxPerMs
        var target = 0
        var at = 0L
        for (clip in others) {
            if (at + clip.lengthMs / 2.0 < heldCentre) target++
            at += clip.lengthMs
        }
        val starts = HashMap<String, Long>(others.size + 1)
        var next = 0L
        others.forEachIndexed { index, clip ->
            if (index == target) next += state.lengthMs
            starts[clip.id] = next
            next += clip.lengthMs
        }
        starts[state.clipId] = Math.round(state.startMs + state.dxPx.floatValue / pxPerMs.toDouble())
        return target to starts
    }

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

    // A keyframe of what is selected, dragged along it; drawn where the finger takes it, saved on release.
    var keyframeDrag by remember { mutableStateOf<KeyframeDrag?>(null) }
    val keyframeGrabPx = with(density) { KEYFRAME_DRAG_GRAB.toPx() }
    val keyframeBandPx = with(density) { KEYFRAME_BAND.toPx() }
    val keyframeOnPlayheadPx = with(density) { KEYFRAME_ON_PLAYHEAD.toPx() }

    /**
     * Where the keyframe of [state] is as the finger has it now, on its owner's
     * clock; whether it snapped (to the playhead, a cut, an edge), and whether it
     * is held at the edge of where it may go (see Keyframes.moveBounds).
     */
    fun draggedKeyframe(state: KeyframeDrag): Triple<Long, Boolean, Boolean> {
        val at = state.timelineFrom + state.dxPx.floatValue / pxPerMs
        val target = SoundSpan.snap(at, state.targets, (snapPx / pxPerMs).toDouble())
        val wanted = state.toOwnClock(target ?: at)
        val ms = wanted.coerceIn(state.bounds.first, state.bounds.last)
        return Triple(ms, target != null && ms == wanted, ms != wanted)
    }

    /** True when the playhead is on the keyframe at [timelineMs]: it is drawn filled. */
    fun onPlayhead(timelineMs: Double): Boolean = abs(timelineMs - position()) * pxPerMs < keyframeOnPlayheadPx

    /**
     * The keyframe of the selected clip, text, picture or sound under [at] (a
     * canvas [width] wide), ready to be dragged; null when there is none. On the
     * clip, only along the bottom of the track, where the diamonds are (higher up
     * its edges always trim it); on a text, picture or sound, on its row.
     *
     * Where an edge can be grabbed too (a keyframe at the start or the end), the
     * edge wins, unless the playhead is on the keyframe: a tap on it first (which
     * brings the playhead there and fills it) picks the keyframe instead. So both
     * stay at hand: drag at once to trim, tap then drag to move the keyframe.
     */
    fun keyframeAt(at: Offset, width: Float): KeyframeDrag? {
        val index = currentSelectedClip?.let { currentTimeline.indexOf(it) } ?: -1
        if (index >= 0) {
            val bottom = trackTop + trackHeight
            if (at.y < bottom - keyframeBandPx || at.y > bottom + handleGrabPx / 3f) return null
            val clip = currentTimeline.clips[index]
            // A clip still waiting for its length has no room for them to move in.
            if (clip.endMs == null) return null
            val start = currentStarts.getOrNull(index) ?: return null
            fun timelineOf(ms: Long): Double = (start + ms - clip.startMs).toDouble()
            // Those left outside the clip by a trim too: dragged back in, they count again.
            val frame = clip.effects.keyframes.frames
                .minByOrNull { abs(xOf(timelineOf(it.atMs), width) - at.x) }
                ?: return null
            if (abs(xOf(timelineOf(frame.atMs), width) - at.x) > keyframeGrabPx) return null
            // Where the trim handler would take it (see the selected clip's edges above).
            val onEdge = abs(at.x - xOf(start.toDouble(), width)) <= handleGrabPx ||
                abs(at.x - xOf((start + clip.lengthMs).toDouble(), width)) <= handleGrabPx
            if (onEdge && !onPlayhead(timelineOf(frame.atMs))) return null
            val bounds = clip.effects.keyframes.moveBounds(frame.atMs, clip.startMs..(clip.startMs + clip.lengthMs), toleranceMs = 0L)
                ?: return null
            return KeyframeDrag(
                owner = KeyframeOwner.OfClip(clip.id),
                fromMs = frame.atMs,
                bounds = bounds,
                timelineFrom = timelineOf(frame.atMs),
                targets = snapTargets(""),
                toOwnClock = { timelineMs -> clip.startMs + Math.round(timelineMs - start) },
                toTimeline = ::timelineOf,
            )
        }
        val item = currentItems.firstOrNull { it.id == currentSelectedItem } ?: return null
        val top = lanesTop - laneScroll + currentLanes.top(item.kind, item.row)
        if (at.y < max(top, lanesTop) || at.y > top + currentLanes.height(item.kind)) return null
        val (left, right) = edges(item.span)
        val span = item.span
        val clock = currentClock
        fun timelineOf(ms: Long): Double = clock.timelineMs(span.startMs + ms)
        val frame = item.keyframes.frames.minByOrNull { abs(xOf(timelineOf(it.atMs), width) - at.x) } ?: return null
        if (abs(xOf(timelineOf(frame.atMs), width) - at.x) > keyframeGrabPx) return null
        // Where the item handler would trim it (its edges, as wide as there).
        val onEdge = abs(at.x - xOf(left, width)) <= handleGrabPx || abs(at.x - xOf(right, width)) <= handleGrabPx
        if (onEdge && !onPlayhead(timelineOf(frame.atMs))) return null
        val bounds = item.keyframes.moveBounds(frame.atMs, 0L..(span.endMs - span.startMs), toleranceMs = 0L) ?: return null
        return KeyframeDrag(
            owner = KeyframeOwner.OfItem(item.kind, item.id),
            fromMs = frame.atMs,
            bounds = bounds,
            timelineFrom = timelineOf(frame.atMs),
            targets = snapTargets(""),
            toOwnClock = { timelineMs -> clock.outputMs(timelineMs) - span.startMs },
            toTimeline = ::timelineOf,
        )
    }

    val addLabels = mapOf(
        LaneKind.EFFECT to stringResource(R.string.lane_effect),
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
        LaneKind.EFFECT to colors.primaryContainer,
        LaneKind.TEXT to colors.secondaryContainer,
        LaneKind.IMAGE to colors.secondary.copy(alpha = 0.45f),
        LaneKind.SOUND to colors.tertiaryContainer,
    )
    val selectedColors = mapOf(
        LaneKind.EFFECT to colors.primary,
        LaneKind.TEXT to colors.secondary,
        LaneKind.IMAGE to colors.secondary,
        LaneKind.SOUND to colors.tertiary,
    )
    val soundWave = colors.onTertiaryContainer.copy(alpha = 0.45f)
    // A clip's own sound, over its frames: the sound tone, lightened to read on the dark band.
    val clipWave = androidx.compose.ui.graphics.lerp(colors.tertiary, Color.White, 0.55f)
    val selectedSoundWave = colors.onTertiary.copy(alpha = 0.55f)
    val mutedStyle = remember(muted) { TextStyle(fontSize = 10.sp, color = muted) }
    // An item's name in the colour made for its fill (the container's "on" colour),
    // light or dark alike.
    val onPrimaryContainer = colors.onPrimaryContainer
    val onSecondaryContainer = colors.onSecondaryContainer
    val onTertiaryContainer = colors.onTertiaryContainer
    val itemStyles = remember(onPrimaryContainer, onSecondaryContainer, onTertiaryContainer, onSurface) {
        mapOf(
            LaneKind.EFFECT to TextStyle(fontSize = 10.sp, color = onPrimaryContainer),
            LaneKind.TEXT to TextStyle(fontSize = 10.sp, color = onSecondaryContainer),
            LaneKind.IMAGE to TextStyle(fontSize = 10.sp, color = onSurface),
            LaneKind.SOUND to TextStyle(fontSize = 10.sp, color = onTertiaryContainer),
        )
    }
    // A stem made by the Stems tool shows what it is: its kind's icon before its name.
    // Known by its file (kept by the separator), or by its name ("Dialogue (film)").
    val stemPainters = StemKind.entries.associateWith { rememberVectorPainter(stemIcon(it)) }
    val stemNames = StemKind.entries.associateWith { stemLabel(it) }
    val stemKinds = remember(sounds, stemNames) {
        sounds.associate { sound ->
            sound.id to (VoiceSeparator.stemKindOf(sound.uri) ?: stemKindNamed(sound.name, stemNames))
        }
    }
    val onSecondary = colors.onSecondary
    val onTertiary = colors.onTertiary
    val onPrimaryItem = colors.onPrimary
    val selectedStyles = remember(onSecondary, onTertiary, onPrimaryItem) {
        mapOf(
            LaneKind.EFFECT to TextStyle(fontSize = 10.sp, color = onPrimaryItem),
            LaneKind.TEXT to TextStyle(fontSize = 10.sp, color = onSecondary),
            LaneKind.IMAGE to TextStyle(fontSize = 10.sp, color = onSecondary),
            LaneKind.SOUND to TextStyle(fontSize = 10.sp, color = onTertiary),
        )
    }
    val badgeStyle = remember { TextStyle(fontSize = 10.sp, color = Color.White) }
    // The mark of a clip with a denoise of its own: the preview's badge, small (see DenoiseBadge).
    val denoiseMark = remember { unitCookie() }
    val grainPainter = rememberVectorPainter(Icons.Rounded.Grain)
    val onPrimary = colors.onPrimary
    val trimBadgeStyle = remember(onPrimary) { TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onPrimary) }
    val clipShape = remember { Path() }
    val junctionGlyph = remember { Path() }
    // Kept from frame to frame: the timeline is drawn again on every frame while it plays.
    val bars = remember { BarBatch() }
    val bandShade = remember { BandShade() }

    // Read in composition, so that the timeline is drawn again when a waveform arrives.
    val waveforms = sounds.associate { it.uri to viewState.waveform(it.uri) }
    val soundsById = remember(sounds) { sounds.associateBy { it.id } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(timelineHeight)
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
                                    // At the widest already, pinching on goes past it (and back first).
                                    if (overPinch < 1f || (zoom < 1f && pxPerMs <= minPxPerMs * WIDEST_SLACK)) {
                                        overPinch = (overPinch * zoom).coerceIn(MIN_OVER_PINCH, 1f)
                                    } else {
                                        pxPerMs = (pxPerMs * zoom).coerceIn(minPxPerMs, maxPxPerMs)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        // The fingers left: the ruler springs back to the video's own time.
                        if (overPinch < 1f) {
                            val from = overPinch
                            overPinch = 1f
                            scope.launch {
                                pinchBack.snapTo(from)
                                if (reducedMotion) {
                                    pinchBack.snapTo(1f)
                                } else {
                                    pinchBack.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                }
                            }
                        }
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
                }
                // A clip held, then dragged: it goes elsewhere among the clips, as a placed sound
                // goes elsewhere on the video. Seen first of all, so that once the hold is
                // recognised the scroll, the trim and the tap leave the gesture alone.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (pxPerMs <= 0f || currentOnMoveClip == null || currentTimeline.clips.size < 2) return@awaitEachGesture
                        if (down.position.y < trackTop || down.position.y > trackTop + trackHeight) return@awaitEachGesture
                        val timeMs = position() + (down.position.x - size.width / 2f) / pxPerMs
                        val index = clipAt(currentTimeline, currentStarts, timeMs.toLong()) ?: return@awaitEachGesture
                        val clip = currentTimeline.clips[index]
                        val start = currentStarts.getOrNull(index) ?: return@awaitEachGesture
                        // The selected clip's edges are its trim handles.
                        if (clip.id == currentSelectedClip) {
                            val left = xOf(start.toDouble(), size.width.toFloat())
                            val right = xOf((start + clip.lengthMs).toDouble(), size.width.toFloat())
                            if (abs(down.position.x - left) <= handleGrabPx || abs(down.position.x - right) <= handleGrabPx) return@awaitEachGesture
                        }
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        held.consume()
                        holdView()
                        currentOnSelect(clip.id)
                        val state = ClipMove(clip.id, index, start, clip.lengthMs)
                        clipMove = state
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var lastTarget = index
                        var fingerX = held.position.x
                        fun followTarget() {
                            val target = moveLayout(state).first
                            if (target != lastTarget) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lastTarget = target
                        }
                        // Near a side the timeline moves on by itself, the clip with it.
                        val autoScroll = edgeScroll(fingerX = { fingerX }, maxMs = { currentTotal.toFloat() }) { shift ->
                            state.dxPx.floatValue += shift
                            followTarget()
                        }
                        val finished = horizontalDrag(held.id) { change ->
                            state.dxPx.floatValue += change.positionChange().x
                            fingerX = change.position.x
                            change.consume()
                            followTarget()
                        }
                        autoScroll.cancel()
                        // Let go without moving: the up is the hold's, not a tap.
                        currentEvent.changes.forEach { it.consume() }
                        val target = moveLayout(state).first
                        clipMove = null
                        releaseView(drag.positionMs.floatValue)
                        if (finished && target != index) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnMoveClip?.invoke(clip.id, target - index)
                        }
                    }
                }
                // A keyframe of what is selected, dragged along it (issue #31). Seen first of
                // all: on a diamond, a drag moves the keyframe before the item's own drag, a trim
                // or the scroll take it. A tap still brings the playhead onto it (the tap handler,
                // as nothing is consumed until the finger moves), a hold still lifts the clip.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (pxPerMs <= 0f || currentOnMoveKeyframe == null) return@awaitEachGesture
                        val state = keyframeAt(down.position, size.width.toFloat()) ?: return@awaitEachGesture
                        var overSlop = 0f
                        val moved = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            overSlop = over
                        } ?: return@awaitEachGesture
                        holdView()
                        state.dxPx.floatValue = overSlop
                        keyframeDrag = state
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var lastSnapped = false
                        var lastHeld = false
                        var lastScrub = 0L
                        var fingerX = moved.position.x
                        // Felt: a tick as it snaps, another as it meets a neighbour or an end. Seen:
                        // the preview shows the frame where it is.
                        fun follow() {
                            val (ms, snapped, held) = draggedKeyframe(state)
                            if ((snapped && !lastSnapped) || (held && !lastHeld)) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lastSnapped = snapped
                            lastHeld = held
                            val now = System.nanoTime()
                            if (now - lastScrub >= SCRUB_INTERVAL_NANOS) {
                                lastScrub = now
                                currentOnScrub(Math.round(state.toTimeline(ms)).coerceAtLeast(0L))
                            }
                        }
                        // Near a side the timeline moves on by itself, the keyframe with it.
                        val autoScroll = edgeScroll(fingerX = { fingerX }, maxMs = { currentTotal.toFloat() }) { shift ->
                            state.dxPx.floatValue += shift
                            follow()
                        }
                        val finished = horizontalDrag(moved.id) { change ->
                            state.dxPx.floatValue += change.positionChange().x
                            fingerX = change.position.x
                            change.consume()
                            follow()
                        }
                        autoScroll.cancel()
                        val (ms, _, _) = draggedKeyframe(state)
                        keyframeDrag = null
                        val committed = finished && ms != state.fromMs
                        // The playhead lands on the keyframe, where it now is.
                        releaseView(state.toTimeline(if (committed) ms else state.fromMs).toFloat().coerceAtLeast(0f))
                        if (committed) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnMoveKeyframe?.invoke(state.owner, state.fromMs, ms)
                        }
                    }
                },
        ) {
            if (pxPerMs <= 0f) return@Canvas
            val centre = size.width / 2f
            val positionMs = position()
            fun x(ms: Float): Float = centre + (ms - positionMs) * pxPerMs

            drawRuler(
                textMeasurer, positionMs, pxPerMs, rulerHeight, muted, mutedStyle, currentTotal,
                absurd = absurdLevel.takeIf { it >= 0 }?.let { absurdUnits[it] },
                squeeze = 1f - RULER_SQUEEZE * absurdDepth,
            )

            // -- Video track ------------------------------------------------------------
            val clips = currentTimeline.clips
            val gap = 2.dp.toPx()
            val corner = CornerRadius(12.dp.toPx())
            val trimming = clipDrag
            // The selected clip's keyframes, drawn over all the clips (see below).
            var clipKeyframes: (() -> Unit)? = null
            // The clips next to an edge being dragged go with it, like everything beyond
            // them: an end pushes (or pulls) the clips after it, a start the clips before
            // it, so that clips never overlap nor leave a gap while the finger moves.
            val trimIndex = trimming?.let { currentTimeline.indexOf(it.clipId) } ?: -1
            val trimmedNow = trimming?.let { trimmed(it) }
            val pushAfter = if (trimming?.edge == ItemEdge.END && trimmedNow != null) trimmedNow.second - trimming.endMs else 0L
            val pushBefore = if (trimming?.edge == ItemEdge.START && trimmedNow != null) trimmedNow.first - trimming.startMs else 0L
            // A clip being moved is drawn last, over the others, which make room for it.
            val moving = clipMove
            val moveStarts = moving?.let { moveLayout(it).second }
            val drawOrder = if (moving == null) clips.indices.toList() else clips.indices.sortedBy { clips[it].id == moving.clipId }
            // The joins with a transition: the two clips' edges slanted alike, as wide as it is long (CapCut's
            // look), unless one of the clips is selected (its edges are trim handles) or being moved.
            val slants = if (moving == null) {
                transitionSlants(clips, currentStarts, currentTransitionMs, selectedClipId, pxPerMs, MIN_SLANT.toPx(), corner.x)
            } else {
                emptyList()
            }
            drawOrder.forEach { index ->
                val original = clips[index]
                val held = moving != null && original.id == moving.clipId
                val start = (if (moveStarts != null) moveStarts[original.id] else currentStarts.getOrNull(index)) ?: return@forEach
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
                // Off screen it is not drawn, unless it is the selected clip with keyframes:
                // those left outside it may still be on screen (with the line to its edge).
                val keepsKeyframes = clip.id == selectedClipId && !clip.effects.keyframes.isEmpty
                if ((right < 0f || left > size.width) && !keepsKeyframes) return@forEach
                // The clip held is lifted a little off the track, on its shadow.
                val lift = if (held) 3.dp.toPx() else 0f
                val rect = RoundRect(left + gap / 2, trackTop - lift, right - gap / 2, trackTop + trackHeight - lift, corner)
                if (held) drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(rect.left, rect.top + lift * 2f), Size(rect.width, rect.height), corner)
                val source = sourcesById[clip.sourceId]
                val before = slants.getOrNull(index - 1)
                val after = slants.getOrNull(index)
                clipShape.reset()
                if (before == null && after == null) {
                    clipShape.addRoundRect(rect)
                } else {
                    clipShape.addSlantedClip(
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom,
                        leftTop = before?.top ?: 0f,
                        leftBottom = -(before?.bottom ?: 0f),
                        rightTop = after?.top ?: 0f,
                        rightBottom = -(after?.bottom ?: 0f),
                        radius = corner.x,
                    )
                }
                // The picture reaches into the slanted tips too.
                val stripLeft = rect.left - (before?.bottom ?: 0f)
                val stripRight = rect.right + (after?.top ?: 0f)
                // Heard from its stem tracks: an empty slot, its outline dashed.
                val slot = soundClips && clip.id in splitClips
                if (slot) {
                    drawPath(clipShape, surfaceColor.copy(alpha = SPLIT_SLOT_ALPHA))
                    val dash = 5.dp.toPx()
                    drawPath(clipShape, muted, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
                    if (rect.width > SPLIT_LABEL_MIN_WIDTH.toPx() && rect.height > 18.dp.toPx()) {
                        drawLabel(
                            textMeasurer,
                            splitSlotText,
                            Offset(rect.left + 8.dp.toPx(), rect.top + (rect.height - 14.dp.toPx()) / 2f),
                            maxWidth = rect.width - 16.dp.toPx(),
                            background = null,
                            style = mutedStyle,
                        )
                    }
                } else clipPath(clipShape) {
                    drawRect(surfaceColor, topLeft = Offset(stripLeft, rect.top), size = Size(stripRight - stripLeft, rect.height))
                    if (source != null && soundClips) {
                        drawClipWaveform(bars, viewState.waveform(source.uri), clip, rect.left, rect.right, trackTop, trackHeight, pxPerMs, primary, faint = clip.id in quietClips)
                    } else if (source != null) {
                        drawFilmstrip(cache, source.uri, source.isStill, clip, stripLeft, stripRight, trackTop, trackHeight, pxPerMs)
                        // Its own sound along the bottom (a picture, or a file without sound, has none).
                        if (clipWaveforms && !source.isStill && source.info?.hasAudio != false) {
                            drawClipSoundBand(
                                bars = bars,
                                shade = bandShade,
                                waveform = viewState.waveform(source.uri),
                                clip = clip,
                                left = rect.left,
                                right = rect.right,
                                bottom = trackTop + trackHeight,
                                height = trackHeight * SOUND_BAND_SHARE,
                                pxPerMs = pxPerMs,
                                color = clipWave,
                                silent = clip.effects.muted || clip.effects.volume <= 0f || clip.id in quietClips,
                                bright = clip.id == selectedClipId,
                            )
                        }
                    }
                }
                // A clip with effects of its own carries a dot; a clip at another speed says so.
                // A denoise, which the preview cannot show, has a mark of its own in that corner
                // (where the clip is wide enough), and the dot moves beside it for the rest.
                val denoiseMarked = clip.effects.color.denoise > 0f && rect.width > DENOISE_MARK_MIN_CLIP.toPx()
                if (denoiseMarked) {
                    val side = DENOISE_MARK_SIZE.toPx()
                    val centre = Offset(rect.right - 6.dp.toPx() - side / 2f, rect.top + 6.dp.toPx() + side / 2f)
                    translate(centre.x - side / 2f, centre.y - side / 2f) {
                        scale(side, side, pivot = Offset.Zero) { drawPath(denoiseMark, primary) }
                    }
                    val icon = side * DENOISE_MARK_ICON_SHARE
                    translate(centre.x - icon / 2f, centre.y - icon / 2f) {
                        with(grainPainter) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(onPrimary)) }
                    }
                    val rest = clip.effects.copy(color = clip.effects.color.copy(denoise = 0f))
                    if (!rest.isNeutral) {
                        drawCircle(primary, radius = 4.dp.toPx(), center = Offset(centre.x - side / 2f - 8.dp.toPx(), centre.y))
                    }
                } else if (!clip.effects.isNeutral) {
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
                if (held) {
                    drawRoundRect(primary, Offset(rect.left, rect.top), Size(rect.width, rect.height), corner, style = Stroke(width = 3.dp.toPx()))
                } else if (selected) {
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
                    // Its keyframes, where they are in its footage (the one being dragged where
                    // the finger has it, lifted, with its time in the clip above it). One left
                    // outside the clip by a trim still counts (the values head for it), so it
                    // stays in view where it is: hollow, tied to the clip's edge by a dashed line.
                    // Drawn once every clip is: those outside lie over the clips beside it.
                    clipKeyframes = {
                        val draggedHere = keyframeDrag?.takeIf { it.owner == KeyframeOwner.OfClip(clip.id) }
                        val draggedMs = draggedHere?.let { draggedKeyframe(it).first }
                        clip.effects.keyframes.frames.forEach { frame ->
                            val lifted = draggedHere != null && frame.atMs == draggedHere.fromMs
                            val inClip = (if (lifted && draggedMs != null) draggedMs else frame.atMs) - clip.startMs
                            val outside = inClip !in 0..clip.lengthMs
                            val at = x((start + shift + inClip).toFloat())
                            val centre = Offset(at, rect.bottom - 9.dp.toPx())
                            if (outside) drawGhostLink(if (inClip < 0) rect.left else rect.right, centre, primary)
                            if (lifted) {
                                drawKeyframe(centre, 8.dp.toPx(), current = true, accent = primary)
                                drawKeyframeTime(textMeasurer, inClip, centre.x, rect.top + 4.dp.toPx(), trimBadgeStyle, primary)
                            } else {
                                drawKeyframe(centre, 6.dp.toPx(), abs(start + shift + inClip - positionMs) * pxPerMs < keyframeOnPlayheadPx, primary, ghost = outside)
                            }
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
                    drawPath(clipShape, Color.Black.copy(alpha = 0.4f), style = Stroke(width = 1.dp.toPx()))
                }
            }
            clipKeyframes?.invoke()

            // The joins between clips, each a button: a line for a plain cut, a bow tie for a
            // transition (filled in the accent).
            if (currentOnJunction != null && trimming == null && moving == null && clips.size > 1) {
                val radius = JUNCTION_RADIUS.toPx()
                val cy = trackTop + trackHeight / 2f
                for (index in 0 until clips.size - 1) {
                    // The selected clip's edges are its trim handles.
                    if (clips[index].id == selectedClipId || clips[index + 1].id == selectedClipId) continue
                    // On the middle of the slanted edges, where there are some.
                    val slant = slants.getOrNull(index)
                    val cx = x((currentStarts.getOrNull(index + 1) ?: continue).toFloat()) + (slant?.let { (it.top - it.bottom) / 2f } ?: 0f)
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
            if (moving == null && currentTotal > 0 && (currentTrimStart > 0L || keptEndMs < currentTotal)) {
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
                // The selected item's keyframes, drawn over all the items (those outside it may
                // lie over the next one on its row).
                var itemKeyframes: (() -> Unit)? = null
                items.forEach { item ->
                    val selected = item.id == selectedItemId
                    val span = if (dragging != null && dragging.id == item.id) draggedSpan(dragging).first else item.span
                    val (leftMs, rightMs) = edges(span)
                    val left = x(leftMs.toFloat())
                    val right = max(x(rightMs.toFloat()), left + MIN_CLIP_WIDTH_DP.dp.toPx())
                    // Off screen it is not drawn, unless it is selected with keyframes: those left
                    // outside it may still be on screen (with the line to its edge).
                    val keepsKeyframes = selected && !item.keyframes.isEmpty
                    if ((right < 0f || left > size.width) && !keepsKeyframes) return@forEach
                    val top = origin + layout.top(item.kind, item.row)
                    val height = layout.height(item.kind)
                    val sound = if (item.kind == LaneKind.SOUND) soundsById[item.id] else null
                    if (sound != null && span is SoundSpan) {
                        drawSound(
                            bars = bars,
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
                            fill = (if (selected) selectedColors.getValue(item.kind) else itemColors.getValue(item.kind)).faded(item.hidden),
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
                            fill = (if (selected) selectedColors.getValue(item.kind) else itemColors.getValue(item.kind)).faded(item.hidden),
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
                    val fill = (if (selected) selectedColors.getValue(item.kind) else itemColors.getValue(item.kind)).faded(item.hidden)
                    drawLabel(
                        textMeasurer, label,
                        Offset(max(left, 0f) + (if (selected) 14.dp else 6.dp).toPx(), top + (height - 14.dp.toPx()) / 2f),
                        maxWidth = right - max(left, 0f) - 28.dp.toPx(),
                        background = null,
                        // A sound's name has a soft halo of its own fill, so it reads over the
                        // waveform without covering it.
                        halo = if (sound != null) fill.copy(alpha = 1f) else null,
                        style = if (selected) selectedStyles.getValue(item.kind) else itemStyles.getValue(item.kind),
                        icon = if (sound != null) stemKinds[sound.id]?.let { stemPainters.getValue(it) } else null,
                    )
                    // The selected one's keyframes, where they are on the video.
                    if (selected) itemKeyframes = {
                        val draggedHere = keyframeDrag?.takeIf { it.owner == KeyframeOwner.OfItem(item.kind, item.id) }
                        val draggedMs = draggedHere?.let { draggedKeyframe(it).first }
                        item.keyframes.frames.forEach { frame ->
                            val lifted = draggedHere != null && frame.atMs == draggedHere.fromMs
                            val ownMs = if (lifted && draggedMs != null) draggedMs else frame.atMs
                            val atMs = currentClock.timelineMs(span.startMs + ownMs)
                            val at = x(atMs.toFloat())
                            // Left outside by a trim, it stays in view where it is, as on a clip.
                            val outside = at < left || at > right
                            val centre = Offset(at, top + height / 2f)
                            if (outside) drawGhostLink(if (at < left) left else right, centre, primary)
                            if (lifted) {
                                drawKeyframe(centre, 7.dp.toPx(), current = true, accent = primary)
                                // Its time from the start of what it belongs to, above the block.
                                drawKeyframeTime(textMeasurer, ownMs, centre.x, top - 2.dp.toPx(), trimBadgeStyle, primary, above = true)
                            } else {
                                drawKeyframe(centre, 5.dp.toPx(), abs(atMs - positionMs) * pxPerMs < keyframeOnPlayheadPx, primary, ghost = outside)
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
                itemKeyframes?.invoke()
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
                .size(trackDp)
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
                modifier = Modifier.size(trackDp / 2),
            )
        }
    }
}

/** What can be placed freely on the video, each kind on rows of its own: effects first, as they lie on top. */
enum class LaneKind { EFFECT, TEXT, IMAGE, SOUND }

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
    /** Hidden from the layers list (its eye): drawn faded, as it is not in the video. */
    val hidden: Boolean = false,
)

/** Every item placed on the video, with the rows their overlaps need (per kind). */
private fun laneItems(
    texts: List<TextOverlay>,
    pictures: List<ImageOverlay>,
    sounds: List<AudioClip>,
    untitledText: String,
    untitledImage: String,
    /** The name of each kind of shape, for shape layers. */
    shapeNames: Map<ShapeKind, String>,
    /** The name of each of the app's effects, by its library name. */
    effectName: (String) -> String,
): List<LaneEntry> {
    // Captions and pictures bound to a clip (from older projects) have no place of their own.
    val freeTexts = texts.filter { it.clipId == null }
    val freePictures = pictures.filter { it.clipId == null && !it.isEffect }
    val effects = pictures.filter { it.clipId == null && it.isEffect }
    val textRows = TimeSpan.rows(freeTexts.map { it.span })
    val pictureRows = TimeSpan.rows(freePictures.map { it.laneSpan })
    val effectRows = TimeSpan.rows(effects.map { it.laneSpan })
    val soundRows = TimeSpan.rows(sounds.map { it.span })
    return effects.mapIndexed { i, e ->
        // An effect is marked with a sparkle.
        LaneEntry(LaneKind.EFFECT, e.id, e.laneSpan, effectRows[i], "\u2726 " + effectName(e.effect.orEmpty()), e.fadeInSeconds, e.fadeOutSeconds, e.keyframes, e.hidden)
    } + freeTexts.mapIndexed { i, t ->
        LaneEntry(LaneKind.TEXT, t.id, t.span, textRows[i], t.text.ifBlank { untitledText }, t.fadeInSeconds, t.fadeOutSeconds, t.keyframes, t.hidden)
    } + freePictures.mapIndexed { i, p ->
        // A video is marked with a play sign; trimming it trims the part of its file it plays.
        val name = p.shape?.let { shapeNames[it.kind] } ?: p.fileName.ifBlank { untitledImage }
        LaneEntry(LaneKind.IMAGE, p.id, p.laneSpan, pictureRows[i], if (p.isVideo) "\u25B6 $name" else name, p.fadeInSeconds, p.fadeOutSeconds, p.keyframes, p.hidden)
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

/** How close to a keyframe's diamond a drag picks it up (a little less: an item's own edges are near). */
private val KEYFRAME_DRAG_GRAB = 12.dp

/** How near the playhead a keyframe is on it: drawn filled, and picked up before an edge. */
private val KEYFRAME_ON_PLAYHEAD = 6.dp

/** The band along the bottom of the clip track where its keyframes are picked up. */
private val KEYFRAME_BAND = 24.dp

/** What owns a keyframe dragged on the timeline: a clip, or a text, picture or sound. */
sealed interface KeyframeOwner {
    data class OfClip(val clipId: String) : KeyframeOwner
    data class OfItem(val kind: LaneKind, val id: String) : KeyframeOwner
}

/**
 * A keyframe being dragged: its owner and time when the finger came down (on the
 * owner's own clock), where it may go, where it was drawn (timeline milliseconds),
 * what it snaps to, how the two clocks map, and how far the finger has gone since
 * (state, so the drawing follows).
 */
private class KeyframeDrag(
    val owner: KeyframeOwner,
    val fromMs: Long,
    val bounds: LongRange,
    val timelineFrom: Double,
    val targets: List<Double>,
    val toOwnClock: (Double) -> Long,
    val toTimeline: (Long) -> Double,
) {
    val dxPx = mutableFloatStateOf(0f)
}

/**
 * The time of a keyframe being dragged ([ms], from the start of what it belongs
 * to) in a pill centred on [centerX]: its top at [y], or its bottom when [above].
 */
private fun DrawScope.drawKeyframeTime(
    textMeasurer: TextMeasurer,
    ms: Long,
    centerX: Float,
    y: Float,
    style: TextStyle,
    color: Color,
    above: Boolean = false,
) {
    val measured = textMeasurer.measure(String.format(Locale.US, "%.2f s", ms / 1000f), style)
    val padH = 8.dp.toPx()
    val padV = 3.dp.toPx()
    val pill = Size(measured.size.width + padH * 2f, measured.size.height + padV * 2f)
    val pillLeft = (centerX - pill.width / 2f).coerceIn(0f, max(0f, size.width - pill.width))
    val pillTop = (if (above) y - pill.height else y).coerceAtLeast(0f)
    drawRoundRect(color, Offset(pillLeft, pillTop), pill, CornerRadius(pill.height / 2f))
    drawText(measured, topLeft = Offset(pillLeft + padH, pillTop + padV))
}

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
        // Those left outside it by a trim too: a tap brings the playhead there, to pick it up.
        return clip.effects.keyframes.frames.map { start + it.atMs - clip.startMs }
    }
    val item = items.firstOrNull { it.id == itemId } ?: return emptyList()
    return item.keyframes.frames.map { Math.round(clock.timelineMs(item.span.startMs + it.atMs)) }
}

/**
 * A keyframe: a small white diamond outlined in [accent], filled with it when
 * the playhead is on it ([current]). A [ghost] (left outside what it belongs
 * to by a trim) is hollow, its outline dashed and paler, unless it is current.
 */
private fun DrawScope.drawKeyframe(center: Offset, radius: Float, current: Boolean, accent: Color, ghost: Boolean = false) {
    val diamond = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    if (ghost && !current) {
        drawPath(diamond, Color.White.copy(alpha = GHOST_FILL_ALPHA))
        val dash = 2.dp.toPx()
        drawPath(
            diamond,
            accent.copy(alpha = GHOST_ALPHA),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
        )
        return
    }
    drawPath(diamond, if (current) accent else Color.White)
    drawPath(diamond, if (current) Color.White else accent, style = Stroke(width = 1.5.dp.toPx()))
}

/** The dashed line that ties a keyframe left outside (at [to]) to the edge it was cut off at ([edgeX]). */
private fun DrawScope.drawGhostLink(edgeX: Float, to: Offset, accent: Color) {
    val dash = 3.dp.toPx()
    drawLine(
        color = accent.copy(alpha = GHOST_ALPHA),
        start = Offset(edgeX, to.y),
        end = to,
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
    )
}

/** How strong a keyframe left outside is drawn: there, but plainly not inside. */
private const val GHOST_ALPHA = 0.6f
private const val GHOST_FILL_ALPHA = 0.35f

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
    bars: BarBatch,
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
        val gain = if (sound.muted) 0.15f else sound.volume.coerceAtMost(1f)
        // The bars stay on the block as it scrolls (one every step from its left end),
        // instead of being laid out again from the screen's edge at every frame.
        var px = left + firstBar(left, step) * step + step / 2f
        val end = min(right, size.width)
        while (px < end) {
            val a = fileMsAt(px - step / 2f)
            val b = fileMsAt(px + step / 2f)
            // Across the seam of a repetition the two ends are measured on their own.
            val peak = if (abs(b - a) > span.rangeMs / 2) waveform.peakBetween(a, a) else waveform.peakBetween(min(a, b), max(a, b))
            val level = kotlin.math.sqrt(peak * gain)
            val barHeight = (height - 6.dp.toPx()) * level.coerceIn(0.04f, 1f)
            bars.add(px, middle - barHeight / 2f, middle + barHeight / 2f)
            px += step
        }
        bars.draw(this, wave, barWidth, rounded = false)
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

/** A clip held and dragged among the others (see moveLayout): where it was, and how far the finger took it. */
private class ClipMove(val clipId: String, val from: Int, val startMs: Long, val lengthMs: Long) {
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
    /** Pinched past the widest zoom: what each tick is called instead of its time (its count of some vast span). */
    absurd: ((Int) -> String)? = null,
    /** How squeezed the ruler is (1: not), around the playhead. */
    squeeze: Float = 1f,
) = scale(scaleX = squeeze, scaleY = 1f, pivot = Offset(size.width / 2f, 0f)) {
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
        if (absurd == null) {
            drawLabel(measurer, formatRulerTime(tick, withTenths = stepMs < 1000), Offset(x + 3.dp.toPx(), 0f), 60.dp.toPx(), null, style)
        } else {
            // Longer words: on every other tick, with the room of two.
            val index = (tick / stepMs).toInt()
            if (index % 2 == 0) drawLabel(measurer, absurd(index), Offset(x + 3.dp.toPx(), 0f), 2 * stepMs * pxPerMs - 6.dp.toPx(), null, style)
        }
        tick += stepMs
    }
}

/** How far past the widest zoom [overPinch] is, 0 (not) to 1 (as far as it goes). */
private fun depthPastWidest(overPinch: Float): Float =
    if (overPinch >= 1f) 0f else (ln(overPinch) / ln(MIN_OVER_PINCH)).coerceIn(0f, 1f)

/** Which of [units] spans the ruler names at [depth]; -1: its own time still. */
private fun spanAt(depth: Float, units: Int): Int =
    if (depth < ABSURD_FROM || units == 0) -1 else (((depth - ABSURD_FROM) / (1f - ABSURD_FROM)) * units).toInt().coerceAtMost(units - 1)

/** The vast spans, from a day to an eon, each counting its ticks. */
@Composable
private fun rememberAbsurdUnits(): List<(Int) -> String> {
    val resources = LocalContext.current.resources
    return remember(resources) { ABSURD_UNITS.map { id -> { n: Int -> resources.getQuantityString(id, n, n) } } }
}

private val ABSURD_UNITS = listOf(
    R.plurals.ruler_absurd_day,
    R.plurals.ruler_absurd_year,
    R.plurals.ruler_absurd_century,
    R.plurals.ruler_absurd_ice_age,
    R.plurals.ruler_absurd_era,
    R.plurals.ruler_absurd_eon,
)

/** The furthest a pinch goes past the widest zoom, as a share of it. */
private const val MIN_OVER_PINCH = 0.02f

/** Just past the widest zoom the ruler still tells the time: a pinch that only grazes it changes nothing. */
private const val ABSURD_FROM = 0.1f

/** How much the ruler squeezes at the furthest. */
private const val RULER_SQUEEZE = 0.22f

/** The zoom counts as the widest this close to it. */
private const val WIDEST_SLACK = 1.0005f

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
    /** Drawn before the text, as tall as it, in its colour. */
    icon: VectorPainter? = null,
    /** A soft glow of this colour around the text (and a dot behind the icon), to read over busy drawing. */
    halo: Color? = null,
) {
    val shown = if (halo != null) style.copy(shadow = Shadow(halo, Offset.Zero, 3.dp.toPx())) else style
    if (maxWidth < 12.dp.toPx()) return
    val iconSide = if (icon != null) 12.dp.toPx() else 0f
    val iconRoom = if (icon != null) iconSide + 3.dp.toPx() else 0f
    val textMax = maxWidth - iconRoom
    if (icon != null && textMax < 12.dp.toPx()) return
    val whole = measurer.measure(text = text, style = shown, softWrap = false, maxLines = 1)
    val layout = if (whole.size.width <= textMax) {
        whole
    } else {
        val step = LABEL_WIDTH_STEP.dp.toPx()
        val width = (floor(textMax / step) * step).toInt()
        if (width < step) return
        measurer.measure(
            text = text,
            style = shown,
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
            size = Size(iconRoom + layout.size.width + 4.dp.toPx(), layout.size.height.toFloat()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
    if (icon != null) {
        if (halo != null) {
            drawCircle(halo, radius = iconSide / 2f + 1.dp.toPx(), center = Offset(topLeft.x + iconSide / 2f, topLeft.y + layout.size.height / 2f))
        }
        translate(topLeft.x, topLeft.y + (layout.size.height - iconSide) / 2f) {
            with(icon) { draw(Size(iconSide, iconSide), colorFilter = ColorFilter.tint(style.color)) }
        }
    }
    // The halo is drawn twice, to be strong enough over light bars.
    if (halo != null) drawText(layout, topLeft = Offset(topLeft.x + iconRoom, topLeft.y))
    drawText(layout, topLeft = Offset(topLeft.x + iconRoom, topLeft.y))
}

/** The stem a sound's [name] says it is: a stem's name alone, or with its file's after it. */
private fun stemKindNamed(name: String, stemNames: Map<StemKind, String>): StemKind? =
    stemNames.entries.firstOrNull { (_, stem) -> name == stem || name.startsWith("$stem (") }?.key

/** Widths a label cut short is measured at are multiples of this (dp). */
private const val LABEL_WIDTH_STEP = 16

/** How far the edges at a join lean: out over the next clip at the top, back over this one at the bottom (px). */
private class Slant(val top: Float, val bottom: Float)

/** The narrowest slant drawn, so that a short transition still shows. */
private val MIN_SLANT = 4.dp

/**
 * The slant of each join between [clips] (null where it has no transition,
 * or touches [selectedId]): half of the transition's footage on each side,
 * the clip going out reaching over the next at the top and the next reaching
 * back under it at the bottom, so that the slant is as wide as the
 * transition is long (an effect laid over a cut, which has no length, gets
 * the narrowest one). Never more than half of either clip, less its corner.
 */
private fun transitionSlants(
    clips: List<Clip>,
    starts: List<Long>,
    lengthsMs: List<Long>,
    selectedId: String?,
    pxPerMs: Float,
    least: Float,
    corner: Float,
): List<Slant?> = (0 until (clips.size - 1).coerceAtLeast(0)).map { gap ->
    val out = clips[gap]
    val into = clips[gap + 1]
    val transition = out.effects.transition ?: return@map null
    if (out.id == selectedId || into.id == selectedId || starts.getOrNull(gap + 1) == null) return@map null
    val ms = lengthsMs.getOrNull(gap) ?: 0L
    if (!transition.isEffect && ms <= 0L) return@map null
    // The footage each clip plays in that time.
    val outPx = if (transition.isEffect) least else max(least, ms * out.effects.effectiveSpeed * pxPerMs / 2f)
    val intoPx = if (transition.isEffect) least else max(least, ms * into.effects.effectiveSpeed * pxPerMs / 2f)
    val room = { clip: Clip -> max(0f, clip.lengthMs * pxPerMs / 2f - corner) }
    Slant(top = min(intoPx, room(into)), bottom = min(outPx, room(out)))
}

/**
 * A clip's outline with its ends leaning: its corners at ([left] +
 * [leftTop], [top]), ([right] + [rightTop], [top]), ([right] + [rightBottom],
 * [bottom]) and ([left] + [leftBottom], [bottom]), each rounded by [radius]
 * (less where an edge is short).
 */
private fun Path.addSlantedClip(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    leftTop: Float,
    leftBottom: Float,
    rightTop: Float,
    rightBottom: Float,
    radius: Float,
) {
    val corners = listOf(
        Offset(left + leftTop, top),
        Offset(right + rightTop, top),
        Offset(right + rightBottom, bottom),
        Offset(left + leftBottom, bottom),
    )
    corners.forEachIndexed { i, corner ->
        val before = corners[(i + 3) % 4]
        val after = corners[(i + 1) % 4]
        val toBefore = before - corner
        val toAfter = after - corner
        val r = minOf(radius, toBefore.getDistance() / 2f, toAfter.getDistance() / 2f)
        val start = corner + toBefore / toBefore.getDistance().coerceAtLeast(1e-3f) * r
        val end = corner + toAfter / toAfter.getDistance().coerceAtLeast(1e-3f) * r
        if (i == 0) moveTo(start.x, start.y) else lineTo(start.x, start.y)
        quadraticTo(corner.x, corner.y, end.x, end.y)
    }
    close()
}

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
    bars: BarBatch,
    waveform: Waveform?,
    clip: Clip,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    pxPerMs: Float,
    color: Color,
    faint: Boolean = false,
) {
    val step = 3.dp.toPx()
    val bar = 2.dp.toPx()
    val middle = top + height / 2f
    val tallest = height / 2f - 4.dp.toPx()
    // Bars anchored to the clip (see drawSound), all drawn in one go.
    var x = left + firstBar(left, step) * step
    val end = min(right, size.width)
    while (x < end) {
        val fromMs = clip.startMs + ((x - left) / pxPerMs).toLong()
        val toMs = clip.startMs + ((x + step - left) / pxPerMs).toLong()
        val peak = waveform?.peakBetween(fromMs, toMs) ?: 0.05f
        val half = (peak.coerceIn(0f, 1f) * tallest).coerceAtLeast(bar / 2f)
        bars.add(x + bar / 2f, middle - half, middle + half)
        x += step
    }
    bars.draw(this, color.copy(alpha = if (faint) 0.3f else 0.85f), bar, rounded = true)
}

/**
 * A video clip's own sound, along the bottom [height] of its frames: a dark
 * band fading up into the picture, and on it the peaks of the clip's part of
 * its file, one bar every few pixels, growing up from the bottom (loud is
 * tall), so that a cut or a clap can be found by eye. A [silent] clip (muted,
 * or at no volume) shows its sound faintly; the [bright] one (selected) at full
 * strength. Flat bars while the file is still being read.
 */
private fun DrawScope.drawClipSoundBand(
    bars: BarBatch,
    shade: BandShade,
    waveform: Waveform?,
    clip: Clip,
    left: Float,
    right: Float,
    bottom: Float,
    height: Float,
    pxPerMs: Float,
    color: Color,
    silent: Boolean,
    bright: Boolean,
) {
    val top = bottom - height
    val from = max(left, 0f)
    val to = min(right, size.width)
    if (to <= from) return
    drawRect(
        brush = shade.between(top - height * 0.35f, bottom),
        topLeft = Offset(from, top - height * 0.35f),
        size = Size(to - from, height * 1.35f),
    )
    val step = 3.dp.toPx()
    val bar = 2.dp.toPx()
    val floor = bottom - 3.dp.toPx()
    val tallest = height - 6.dp.toPx()
    val alpha = when {
        silent -> 0.3f
        bright -> 1f
        else -> 0.85f
    }
    // Bars anchored to the clip (see drawSound), all drawn in one go.
    var x = left + firstBar(left, step) * step
    while (x < to) {
        val fromMs = clip.startMs + ((x - left) / pxPerMs).toLong()
        val toMs = clip.startMs + ((x + step - left) / pxPerMs).toLong()
        val peak = waveform?.peakBetween(fromMs, toMs) ?: 0.04f
        val tall = (peak.coerceIn(0f, 1f) * tallest).coerceAtLeast(bar)
        bars.add(x + bar / 2f, floor - tall, floor)
        x += step
    }
    bars.draw(this, color.copy(alpha = alpha), bar, rounded = true)
}

/** The first bar, one every [step] from [left], that is not left of the screen. */
private fun firstBar(left: Float, step: Float): Int = if (left >= 0f) 0 else floor(-left / step).toInt()

/**
 * Vertical bars collected and drawn as one batch of lines: a waveform is a few
 * hundred bars, drawn again on every frame while the timeline moves, and one
 * call for all of them costs far less than one rounded rectangle each.
 */
private class BarBatch {
    private var points = FloatArray(512)
    private var count = 0
    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
    }

    /** A bar centred on [x], from [top] to [bottom]. */
    fun add(x: Float, top: Float, bottom: Float) {
        if (count + 4 > points.size) points = points.copyOf(points.size * 2)
        points[count++] = x
        points[count++] = top
        points[count++] = x
        points[count++] = bottom
    }

    /** Draws the bars added since the last time, [width] wide; [rounded] ends stay inside top and bottom. */
    fun draw(scope: DrawScope, color: Color, width: Float, rounded: Boolean) {
        if (count == 0) return
        if (rounded) {
            // A round cap adds half the width at each end: the line is shortened by as much.
            val inset = width / 2f
            var i = 0
            while (i < count) {
                val top = points[i + 1] + inset
                val bottom = points[i + 3] - inset
                if (bottom < top) {
                    val middle = (points[i + 1] + points[i + 3]) / 2f
                    points[i + 1] = middle
                    points[i + 3] = middle
                } else {
                    points[i + 1] = top
                    points[i + 3] = bottom
                }
                i += 4
            }
        }
        paint.color = color.toArgb()
        paint.strokeWidth = width
        paint.strokeCap = if (rounded) android.graphics.Paint.Cap.ROUND else android.graphics.Paint.Cap.BUTT
        scope.drawContext.canvas.nativeCanvas.drawLines(points, 0, count, paint)
        count = 0
    }
}

/** The dark band under a clip's sound, made again only when the track moves or changes size. */
private class BandShade {
    private var from = Float.NaN
    private var to = Float.NaN
    private var brush: Brush? = null

    fun between(startY: Float, endY: Float): Brush {
        val kept = brush
        if (kept != null && startY == from && endY == to) return kept
        from = startY
        to = endY
        return Brush.verticalGradient(
            0f to Color.Transparent,
            0.35f to Color.Black.copy(alpha = 0.45f),
            1f to Color.Black.copy(alpha = 0.7f),
            startY = startY,
            endY = endY,
        ).also { brush = it }
    }
}

/**
 * "0:05", "1:30"; "0:05.5" [withTenths]. Built by hand: the ruler writes its
 * times on every frame, and String.format costs far more than a few appends.
 */
private fun formatRulerTime(ms: Long, withTenths: Boolean): String {
    val seconds = ms / 1000
    val rest = seconds % 60
    return buildString(10) {
        append(seconds / 60).append(':')
        if (rest < 10) append('0')
        append(rest)
        if (withTenths) append('.').append((ms % 1000) / 100)
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

/** The clips' row of an audio file whose every clip is heard from its stems: only their slots. */
private val SPLIT_TRACK_HEIGHT = 28.dp

/** A clip heard from its stems: its slot's fill, faint, and the width from which it is named. */
private const val SPLIT_SLOT_ALPHA = 0.35f
private val SPLIT_LABEL_MIN_WIDTH = 72.dp

/** Share of a clip's height its own sound takes, along its bottom. */
private const val SOUND_BAND_SHARE = 0.42f

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

/** Side of a clip's denoise mark on the timeline. */
private val DENOISE_MARK_SIZE = 16.dp

/** A clip narrower than this has no room for the mark: it keeps the dot. */
private val DENOISE_MARK_MIN_CLIP = 40.dp

/** The grain in the mark, as a share of its side. */
private const val DENOISE_MARK_ICON_SHARE = 0.68f

/** Material's nine-sided cookie as a path filling the unit square, to scale into a mark. */
private fun unitCookie(): Path {
    val polygon = MaterialShapes.Cookie9Sided
    val bounds = polygon.calculateBounds()
    val path = polygon.toPath()
    val matrix = android.graphics.Matrix()
    matrix.setTranslate(-bounds[0], -bounds[1])
    matrix.postScale(1f / (bounds[2] - bounds[0]).coerceAtLeast(MIN_COOKIE_EXTENT), 1f / (bounds[3] - bounds[1]).coerceAtLeast(MIN_COOKIE_EXTENT))
    path.transform(matrix)
    return path.asComposePath()
}

private const val MIN_COOKIE_EXTENT = 0.0001f

/** [this] colour faded for an item hidden from the layers list (it is not in the video). */
private fun Color.faded(hidden: Boolean): Color = if (hidden) copy(alpha = alpha * HIDDEN_LANE_ALPHA) else this

/** How strong a hidden item is drawn on its lane. */
private const val HIDDEN_LANE_ALPHA = 0.35f
