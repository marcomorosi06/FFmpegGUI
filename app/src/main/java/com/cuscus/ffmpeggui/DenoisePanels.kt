@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.engine.DenoiseSampler
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.VideoHdr
import com.cuscus.ffmpeggui.core.preview.DenoiseSample
import com.cuscus.ffmpeggui.core.preview.DenoiseSampleRequest
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.preview.PreviewSnapshot
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The video editor's denoise (ColorSettings.denoise, FFmpeg's hqdn3d), for a
 * clip or for the whole video: the first setting of its Details tool (see
 * VideoDetailsPanels, which names it in its row of settings). Its signs outside the panel: a badge on the preview
 * (DenoiseBadge, in EditorPreview's status badges) and a mark on the clip in
 * the timeline.
 *
 * The live preview cannot show it: hqdn3d also works over time, each frame
 * blended with the ones before it. So the panel shows it where it matters,
 * close up: a small square of the frame under the playhead, at the footage's
 * own pixels, before and after, made by FFmpeg with the export's own filter
 * (see core's DenoiseSample). In Material 3 Expressive:
 *
 *  - the strength: a connected button group of levels, and a slider for the
 *    value in between;
 *  - the comparison in a cookie (one of MaterialShapes) that swells into a soft
 *    burst while FFmpeg works and settles back on a spring when the new
 *    comparison is in; a divider to drag between before and after, which
 *    sweeps across once to show the difference;
 *  - the whole frame under it, with a frame on the part compared that springs
 *    to wherever it is tapped (or dragged, after a long press, so that a
 *    plain swipe still scrolls the panel).
 */

/** Draws the comparisons; null where there is no FFmpeg (previews of the UI). */
val LocalDenoiseSampler = staticCompositionLocalOf<DenoiseSampler?> { null }

/**
 * The frame a comparison is taken from: [positionMs] of the file at
 * [sourceUri], in clip [clipId], which starts at [clipStartMs] of the file.
 *
 * @param hdrKept the export keeps the HDR footage HDR (see DenoiseSampleRequest).
 * @param width   width of the frame as displayed, 0 while not known.
 * @param height  height of the frame as displayed, 0 while not known.
 */
internal data class DenoiseFrame(
    val clipId: String,
    val sourceUri: Uri,
    val positionMs: Long,
    val clipStartMs: Long,
    val still: Boolean,
    val hdr: VideoHdr,
    val hdrKept: Boolean,
    val width: Int,
    val height: Int,
)

/**
 * The frame to compare at [now]: of clip [clipId] (under the playhead, or its
 * first frame when the playhead is on another clip), or, for the whole video
 * (null), of the clip under the playhead. Null when there is no such clip.
 */
internal fun denoiseFrameAt(
    clips: List<Clip>,
    sources: List<EditorSource>,
    now: PreviewSnapshot,
    clipId: String?,
    hdrKept: Boolean,
): DenoiseFrame? {
    val here = clips.getOrNull(now.segment)
    val clip = (if (clipId == null) here ?: clips.lastOrNull() else clips.firstOrNull { it.id == clipId }) ?: return null
    val source = sources.firstOrNull { it.id == clip.sourceId } ?: return null
    // Where in its file the playhead is: the snapshot follows the clip's speed and direction.
    val position = if (here?.id == clip.id) now.clipFileMs else clip.startMs
    val end = clip.endMs?.let { maxOf(clip.startMs, it - 1L) } ?: Long.MAX_VALUE
    val info = source.info
    return DenoiseFrame(
        clipId = clip.id,
        sourceUri = source.uri,
        positionMs = position.coerceIn(clip.startMs, end),
        clipStartMs = clip.startMs,
        still = source.isStill,
        hdr = if (source.isStill) VideoHdr.NONE else info?.hdr ?: VideoHdr.NONE,
        hdrKept = hdrKept,
        width = info?.width ?: 0,
        height = info?.height ?: 0,
    )
}

/** The levels offered in one tap; the slider gives the values in between. */
private enum class DenoiseLevel(@StringRes val labelRes: Int, val strength: Float) {
    OFF(R.string.denoise_off, 0f),
    LIGHT(R.string.denoise_light, 2f),
    MEDIUM(R.string.denoise_medium, 4f),
    STRONG(R.string.denoise_strong, 7f),
}

/**
 * The denoise panel.
 *
 * @param strength      the denoise being edited: the clip's, or the whole video's.
 * @param clipScope     the panel edits a clip (else the whole video).
 * @param otherStrength the denoise the export also runs on [frame]: the whole
 *                      video's for a clip, the clip's for the whole video.
 * @param frame         the frame compared, null while the playhead moves.
 * @param strengths     every denoise the export runs on [frame], in order.
 */
@Composable
internal fun DenoisePanel(
    strength: Float,
    onStrength: (Float) -> Unit,
    clipScope: Boolean,
    otherStrength: Float,
    frame: DenoiseFrame?,
    strengths: List<Float>,
    enabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val level = DenoiseLevel.entries.firstOrNull { abs(it.strength - strength) < LEVEL_TOLERANCE }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ConnectedChoiceGroup(
            options = DenoiseLevel.entries,
            // A value between the levels shows none of them chosen.
            selected = level,
            label = { option -> option?.let { stringResource(it.labelRes) }.orEmpty() },
            onSelect = { option -> option?.let { onStrength(it.strength) } },
            enabled = enabled,
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.denoise_strength),
                valueText = if (strength > 0f) decimal(strength, 1) else stringResource(R.string.denoise_off),
                value = strength.coerceIn(0f, ColorSettings.DENOISE_MAX),
                // A tenth at a time: what the export writes into the command.
                onValueChange = { value -> onStrength((value * 10f).roundToInt() / 10f) },
                valueRange = 0f..ColorSettings.DENOISE_MAX,
                icon = Icons.Rounded.Grain,
                defaultValue = 0f,
                enabled = enabled,
                input = ValueInput(step = 0.5f, decimals = 1, range = 0f..ColorSettings.DENOISE_MAX),
            )
            AnimatedVisibility(
                visible = strength >= STRONG_FROM,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                PanelHint(stringResource(R.string.denoise_strong_hint))
            }
        }

        DenoiseCompareCard(frame = frame, strengths = strengths)

        // What this denoise is, and what else the comparison runs.
        PanelHint(stringResource(if (clipScope) R.string.denoise_hint_clip else R.string.denoise_hint_whole))
        if (otherStrength > 0f) {
            PanelHint(
                stringResource(
                    if (clipScope) R.string.denoise_with_whole else R.string.denoise_with_clip,
                    decimal(otherStrength, 1),
                ),
            )
        }
        PanelHint(stringResource(R.string.denoise_compare_hint))
    }
}

/**
 * The denoise under the playhead, for the preview's badge: the clip's own
 * ([clipStrength], of clip [clipId]) and the whole video's ([wholeStrength]),
 * either of them 0 for none.
 */
internal data class DenoiseShown(val clipStrength: Float, val wholeStrength: Float, val clipId: String?) {
    /** The clip's denoise when it has one, else the whole video's: what the badge opens. */
    val openClipId: String? get() = clipId?.takeIf { clipStrength > 0f }
}

/**
 * The denoise under the playhead of [clips] at [segment], with [whole] the
 * whole video's effects; null when none runs there or when filters do not run
 * at all ([filtersApply] false: stream copy, frame extraction).
 */
internal fun denoiseShownAt(clips: List<Clip>, segment: Int, whole: Float, filtersApply: Boolean): DenoiseShown? {
    if (!filtersApply) return null
    val clip = clips.getOrNull(segment)
    val own = clip?.effects?.color?.denoise ?: 0f
    if (own <= 0f && whole <= 0f) return null
    return DenoiseShown(clipStrength = own, wholeStrength = whole, clipId = clip?.id)
}

/**
 * The preview's sign that a denoise runs on the frame shown, although the
 * preview cannot show it: a small cookie of dark glass with the grain in it,
 * among the preview's status badges. It springs in from a circle; for a moment
 * it says what it is ("Denoise 4.0, in the export") and then folds back to the
 * cookie alone. It fades while the video plays, and a tap opens the Details
 * tool ([onOpen], with the clip whose denoise it is; null for the whole video).
 */
@Composable
internal fun DenoiseBadge(shown: DenoiseShown?, playing: Boolean, onOpen: ((String?) -> Unit)?) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    // While it leaves, the badge keeps what it showed last.
    val last = remember { arrayOfNulls<DenoiseShown>(1) }
    if (shown != null) last[0] = shown
    val showing = shown ?: last[0] ?: return
    val strengths = listOf(showing.clipStrength, showing.wholeStrength).filter { it > 0f }.joinToString(" + ") { decimal(it, 1) }
    val label = stringResource(R.string.denoise_badge, strengths)
    val action = stringResource(R.string.denoise_badge_open)

    // In from a circle to the cookie, on the spatial spring.
    var arrived by remember { mutableStateOf(false) }
    // The words show for a moment when it comes in or its strength changes, then fold away.
    var spelled by remember { mutableStateOf(false) }
    LaunchedEffect(shown != null) { arrived = shown != null }
    LaunchedEffect(strengths, shown != null) {
        if (shown == null) return@LaunchedEffect
        spelled = true
        delay(BADGE_SPELLED_MS)
        spelled = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (playing) BADGE_PLAYING_ALPHA else 1f,
        animationSpec = motion.slowEffectsSpec(),
        label = "denoiseBadgeAlpha",
    )
    val glass = Color.Black.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .then(
                if (onOpen == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = action) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onOpen(showing.openClipId)
                    }
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie9Sided,
            morphed = arrived,
            color = glass,
            modifier = Modifier.size(STATUS_BADGE_SIZE),
        ) {
            Icon(Icons.Rounded.Grain, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        AnimatedVisibility(
            visible = spelled,
            enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkHorizontally(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .height(STATUS_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(glass)
                    .padding(horizontal = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
            }
        }
    }
}

/** Two halves of a comparison: the square before the denoise and after it. */
private class DenoiseComparison(val before: ImageBitmap, val after: ImageBitmap)

/**
 * The comparison of [frame] with [strengths]: a cookie with before and after
 * either side of a divider, and the whole frame under it, where the part
 * compared is picked.
 */
@Composable
private fun DenoiseCompareCard(frame: DenoiseFrame?, strengths: List<Float>) {
    val sampler = LocalDenoiseSampler.current
    val context = LocalContext.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    // Where to look, kept while the panel is open (a new clip starts in the middle).
    var focusX by rememberSaveable(frame?.clipId) { mutableFloatStateOf(0.5f) }
    var focusY by rememberSaveable(frame?.clipId) { mutableFloatStateOf(0.5f) }

    val request = frame?.let {
        DenoiseSampleRequest(
            sourceUri = it.sourceUri.toString(),
            positionMs = it.positionMs,
            clipStartMs = it.clipStartMs,
            still = it.still,
            strengths = strengths,
            focusX = focusX,
            focusY = focusY,
            hdr = it.hdr,
            hdrKept = it.hdrKept,
        )
    }
    var shown by remember { mutableStateOf<DenoiseComparison?>(null) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(sampler, request) {
        if (sampler == null || request == null) {
            working = false
            return@LaunchedEffect
        }
        working = true
        // A slider being dragged or a finger moving on the frame asks again and again:
        // FFmpeg only runs once they rest.
        delay(COMPARE_SETTLE_MS)
        val made = withContext(Dispatchers.IO) { sampler.sample(request)?.let(::readComparison) }
        if (made != null) {
            shown = made
            failed = false
        } else {
            // An older comparison would pass for this one: say it failed instead.
            shown = null
            failed = true
        }
        working = false
    }

    // The whole frame, to pick the part compared.
    val whole by produceState<Bitmap?>(initialValue = null, frame?.sourceUri, frame?.positionMs, frame?.still) {
        val at = frame ?: run {
            value = null
            return@produceState
        }
        keySample(context, at.sourceUri, at.positionMs, at.still, matrix = null)?.let { value = it }
    }

    // The divider sweeps from the right to the middle the first time a comparison shows.
    val split = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val firstShown = shown != null
    LaunchedEffect(firstShown) {
        if (firstShown) split.animateTo(0.5f, motion.slowSpatialSpec())
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                text = stringResource(R.string.denoise_compare_title),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ComparePill(stringResource(R.string.denoise_before), scheme.surfaceContainerHighest, scheme.onSurface)
            Spacer(modifier = Modifier.width(6.dp))
            ComparePill(stringResource(R.string.denoise_after), scheme.primary, scheme.onPrimary)
        }

        BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            val side = minOf(maxWidth, LOUPE_MAX)
            val sidePx = with(LocalDensity.current) { side.toPx() }
            val description = stringResource(R.string.denoise_compare_description)
            val dividerDescription = stringResource(R.string.denoise_split_description)
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.SoftBurst,
                // Swollen while FFmpeg works; back on a spring once the comparison is in.
                morphed = working,
                color = scheme.surfaceContainerHighest,
                modifier = Modifier
                    .size(side)
                    .semantics { contentDescription = description }
                    .pointerInput(sidePx) {
                        detectTapGestures { at -> scope.launch { split.animateTo((at.x / sidePx).coerceIn(0f, 1f), motion.fastSpatialSpec()) } }
                    }
                    .pointerInput(sidePx) {
                        detectHorizontalDragGestures(
                            onDragEnd = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                        ) { change, _ ->
                            change.consume()
                            scope.launch { split.snapTo((change.position.x / sidePx).coerceIn(0f, 1f)) }
                        }
                    },
            ) {
                val dim by animateFloatAsState(
                    targetValue = if (working) WORKING_ALPHA else 1f,
                    animationSpec = motion.defaultEffectsSpec(),
                    label = "compareDim",
                )
                AnimatedContent(
                    targetState = shown,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "comparison",
                ) { comparison ->
                    if (comparison == null) {
                        CompareMessage(failed = failed && !working, waiting = frame == null)
                    } else {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = dim }) {
                            Image(
                                bitmap = comparison.after,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Image(
                                bitmap = comparison.before,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        clipRect(right = size.width * split.value) { this@drawWithContent.drawContent() }
                                    },
                            )
                        }
                    }
                }
                if (shown != null) {
                    // The divider and its handle, read while drawing: dragging does not recompose.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(DIVIDER_WIDTH)
                            .offset { IntOffset((split.value * sidePx - DIVIDER_WIDTH.toPx() / 2f).roundToInt(), 0) }
                            .background(scheme.inverseSurface.copy(alpha = 0.85f)),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((split.value * sidePx - HANDLE_SIZE.toPx() / 2f).roundToInt(), 0) }
                            .size(HANDLE_SIZE)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .semantics {
                                contentDescription = dividerDescription
                                progressBarRangeInfo = ProgressBarRangeInfo(split.value, 0f..1f)
                                setProgress { target ->
                                    scope.launch { split.animateTo(target.coerceIn(0f, 1f), motion.fastSpatialSpec()) }
                                    true
                                }
                            },
                    ) {
                        Icon(Icons.Rounded.Compare, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                // Faded in and out by hand: inside the card's Column, AnimatedVisibility would
                // resolve to the ColumnScope one, which a Box inside it cannot call.
                val loading by animateFloatAsState(
                    targetValue = if (working) 1f else 0f,
                    animationSpec = motion.fastEffectsSpec(),
                    label = "compareLoading",
                )
                if (loading > 0f) {
                    LoadingIndicator(modifier = Modifier.size(56.dp).graphicsLayer { alpha = loading })
                }
            }
        }

        WholeFrame(
            picture = whole,
            frame = frame,
            focusX = focusX,
            focusY = focusY,
            onFocus = { x, y ->
                focusX = x
                focusY = y
            },
        )
    }
}

/** What the cookie says when there is no comparison to show. */
@Composable
private fun CompareMessage(failed: Boolean, waiting: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        // Read out as it changes: the comparison could not be made, or waits for the playhead.
        modifier = Modifier.fillMaxSize().padding(36.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when {
            failed -> {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.denoise_failed), style = MaterialTheme.typography.bodyMedium, color = scheme.error, textAlign = TextAlign.Center)
            }
            waiting -> Text(
                stringResource(R.string.denoise_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            else -> Unit
        }
    }
}

/**
 * The whole frame compared, with a frame on the part shown: a tap moves it
 * there, on a spring; a long press picks it up to drag it (a plain swipe
 * scrolls the panel).
 */
@Composable
private fun WholeFrame(
    picture: Bitmap?,
    frame: DenoiseFrame?,
    focusX: Float,
    focusY: Float,
    onFocus: (Float, Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    // The frame's real size decides how big the square is on it; the picture read is smaller.
    val width = frame?.width?.takeIf { it > 0 } ?: picture?.width ?: 0
    val height = frame?.height?.takeIf { it > 0 } ?: picture?.height ?: 0
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
    val area = DenoiseSample.shownArea(width, height, focusX, focusY)
    val left by animateFloatAsState(area.left, motion.fastSpatialSpec(), label = "focusLeft")
    val top by animateFloatAsState(area.top, motion.fastSpatialSpec(), label = "focusTop")
    val right by animateFloatAsState(area.right, motion.fastSpatialSpec(), label = "focusRight")
    val bottom by animateFloatAsState(area.bottom, motion.fastSpatialSpec(), label = "focusBottom")
    val image = remember(picture) { picture?.asImageBitmap() }
    val description = stringResource(R.string.denoise_focus_description)
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.denoise_focus_title), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio.coerceIn(MIN_RATIO, MAX_RATIO))
                .clip(RoundedCornerShape(20.dp))
                .clipToBounds()
                .background(scheme.surfaceContainerHighest)
                .semantics { contentDescription = description }
                .pointerInput(width, height) {
                    detectTapGestures { at ->
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        val (x, y) = DenoiseSample.focusFor(width, height, at.x / size.width, at.y / size.height)
                        onFocus(x, y)
                    }
                }
                // Dragged after a long press only: a plain swipe up or down scrolls the panel.
                .pointerInput(width, height) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { at ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val (x, y) = DenoiseSample.focusFor(width, height, at.x / size.width, at.y / size.height)
                            onFocus(x, y)
                        },
                        onDragEnd = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                    ) { change, _ ->
                        change.consume()
                        val (x, y) = DenoiseSample.focusFor(width, height, change.position.x / size.width, change.position.y / size.height)
                        onFocus(x, y)
                    }
                },
        ) {
            if (image == null) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center).size(40.dp))
            } else {
                Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val topLeft = Offset(left * size.width, top * size.height)
                    val box = Size((right - left) * size.width, (bottom - top) * size.height)
                    val corner = CornerRadius(8.dp.toPx())
                    // The rest of the frame a little darker, the part compared in its own colour.
                    drawRect(scheme.scrim.copy(alpha = SCRIM_ALPHA))
                    clipRect(topLeft.x, topLeft.y, topLeft.x + box.width, topLeft.y + box.height) {
                        drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    }
                    drawRoundRect(color = scheme.primary, topLeft = topLeft, size = box, cornerRadius = corner, style = Stroke(width = 3.dp.toPx()))
                }
            }
        }
    }
}

/** A label in a pill. */
@Composable
private fun ComparePill(text: String, container: Color, content: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * The comparison FFmpeg wrote at [path] (before and after side by side), read
 * and deleted. Null when it cannot be read.
 */
private fun readComparison(path: String): DenoiseComparison? {
    val file = File(path)
    return try {
        val picture = BitmapFactory.decodeFile(path) ?: return null
        val half = picture.width / 2
        if (half <= 0 || picture.height <= 0) return null
        DenoiseComparison(
            before = Bitmap.createBitmap(picture, 0, 0, half, picture.height).asImageBitmap(),
            after = Bitmap.createBitmap(picture, half, 0, half, picture.height).asImageBitmap(),
        )
    } finally {
        file.delete()
    }
}

/** Side of the preview's badge. */
internal val STATUS_BADGE_SIZE = 24.dp

/** How long the badge says what it is before it folds back to the cookie. */
internal const val BADGE_SPELLED_MS = 2_500L

/** The badge while the video plays: there, but not in the way. */
internal const val BADGE_PLAYING_ALPHA = 0.55f

/** A strength this near a level is that level. */
private const val LEVEL_TOLERANCE = 0.05f

/** From this strength on, the panel warns that detail goes too. */
private const val STRONG_FROM = 6f

/** How long the settings rest before FFmpeg is asked for a comparison. */
private const val COMPARE_SETTLE_MS = 350L

/** The comparison shown while the next one is made. */
private const val WORKING_ALPHA = 0.55f

/** The darkening of the frame around the part compared. */
private const val SCRIM_ALPHA = 0.45f

/** The widest the comparison gets. */
private val LOUPE_MAX = 320.dp

private val DIVIDER_WIDTH = 3.dp
private val HANDLE_SIZE = 36.dp

/** The whole frame keeps its proportions within these. */
private const val MIN_RATIO = 0.5f
private const val MAX_RATIO = 2.4f
