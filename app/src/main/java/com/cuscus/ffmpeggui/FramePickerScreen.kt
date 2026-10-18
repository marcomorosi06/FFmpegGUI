@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.preview.smallFrameAt
import com.cuscus.ffmpeggui.ui.expressive.MorphingPlayButton
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.RepeatingIconButton
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Where a picture is taken out of a video: before the picture editor, the
 * exact frame is chosen. The video plays here; it is moved through coarsely on
 * a strip of its frames, finely on a wheel that ticks at every frame (thrown,
 * it spins on), one or ten frames at a time with the buttons; the frame can be
 * looked at closely with two fingers. Then the frame is edited as a photo, or
 * every frame (at a chosen rate, over a chosen part) is saved into a zip.
 *
 * Frames are counted on the file's clock at its frame rate: frame n starts at
 * n / rate, and the player and FFmpeg both show the first frame at or after a
 * position, so the frame seen is the frame exported.
 */

/**
 * @param rangeStartMs where the clip the picture comes from starts in its file.
 * @param rangeEndMs   where it ends.
 * @param initialMs    the frame chosen before, in the file.
 * @param onEditAsPhoto the frame at that position of the file is edited as a photo.
 * @param onExportFrames every frame at that rate, between those positions of the file, into a zip.
 */
@Composable
fun FramePickerScreen(
    uri: Uri,
    fileName: String,
    info: MediaInfo?,
    format: OutputFormat,
    rangeStartMs: Long,
    rangeEndMs: Long,
    initialMs: Long,
    onEditAsPhoto: (Long) -> Unit,
    onExportFrames: (framesPerSecond: Float, startMs: Long, endMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val fps = info?.frameRate?.takeIf { it >= MIN_SOURCE_FPS } ?: DEFAULT_FPS
    val end = rangeEndMs.coerceAtLeast(rangeStartMs + 1)
    val firstFrame = frameAt(rangeStartMs, fps)
    // The last frame that starts before the clip ends.
    val lastFrame = (frameAt(end - 1, fps)).coerceAtLeast(firstFrame)

    // -- The player ------------------------------------------------------------------
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
            setMediaItem(MediaItem.Builder().setUri(uri).build())
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var frame by remember(uri) { mutableLongStateOf(frameAt(initialMs.coerceIn(rangeStartMs, end - 1), fps).coerceIn(firstFrame, lastFrame)) }
    var playing by remember { mutableStateOf(false) }
    val positionMs = startOf(frame, fps)
    // Shows the frame chosen by hand (not while playing: the player moves on by itself).
    LaunchedEffect(player) { player.seekTo(startOf(frame, fps)) }
    val go: (Long) -> Unit = { target ->
        val clamped = target.coerceIn(firstFrame, lastFrame)
        if (clamped != frame) {
            frame = clamped
            if (playing) {
                player.pause()
                playing = false
            }
            player.seekTo(startOf(clamped, fps))
        }
    }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        if (frame >= lastFrame) {
            frame = firstFrame
            player.seekTo(startOf(firstFrame, fps))
        }
        player.play()
        while (playing) {
            val now = player.currentPosition
            if (now >= end) {
                player.pause()
                playing = false
                frame = lastFrame
                player.seekTo(startOf(lastFrame, fps))
                break
            }
            frame = frameAt(now, fps).coerceIn(firstFrame, lastFrame)
            delay(PLAY_TICK_MS)
        }
    }
    val togglePlay: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        if (playing) {
            player.pause()
            playing = false
            // Settles on the frame it stopped on, exactly.
            player.seekTo(startOf(frame, fps))
        } else {
            playing = true
        }
    }

    var showExport by remember { mutableStateOf(false) }
    val ratio = info?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height } ?: (16f / 9f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val wide = maxWidth > maxHeight * 1.1f && maxWidth >= 600.dp
        val header: @Composable () -> Unit = {
            PickerHeader(fileName = fileName, format = format, onBack = onBack)
        }
        val picture: @Composable (Modifier) -> Unit = { modifier ->
            FrameView(
                player = player,
                ratio = ratio,
                frame = frame,
                modifier = modifier,
            )
        }
        val controls: @Composable () -> Unit = {
            FrameControls(
                frame = frame,
                firstFrame = firstFrame,
                lastFrame = lastFrame,
                positionMs = positionMs,
                fps = fps,
                playing = playing,
                uri = uri,
                rangeStartMs = rangeStartMs,
                rangeEndMs = end,
                onFrame = go,
                onTogglePlay = togglePlay,
                onEditAsPhoto = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    player.pause()
                    playing = false
                    onEditAsPhoto(startOf(frame, fps))
                },
                onExport = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    player.pause()
                    playing = false
                    showExport = true
                },
            )
        }
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header()
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                        picture(Modifier)
                    }
                }
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) {
                    controls()
                }
            }
        } else {
            val pictureMax = maxHeight * PICTURE_SHARE
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                header()
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().heightIn(max = pictureMax)) {
                    picture(Modifier)
                }
                controls()
            }
        }
    }

    if (showExport) {
        FramesExportSheet(
            format = format,
            fps = fps,
            rangeStartMs = rangeStartMs,
            rangeEndMs = end,
            currentMs = positionMs,
            onDismiss = { showExport = false },
            onExport = { rate, from, to ->
                showExport = false
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onExportFrames(rate, from, to)
            },
        )
    }
}

/** While the file is still being looked at. */
@Composable
fun FramePickerLoading() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadingIndicator(modifier = Modifier.size(64.dp))
    }
}

/** Back, the file, and the format the picture becomes. */
@Composable
private fun PickerHeader(fileName: String, format: OutputFormat, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onBack()
        }) {
            Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = stringResource(R.string.action_back), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.frame_picker_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = scheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(fileName, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FormatBadge(format = format, size = 44.dp)
    }
}

/**
 * The video at the chosen frame, in a large rounded frame. Two fingers bring
 * it closer to check the detail (a double tap puts it back); the frame's
 * number sits on it.
 */
@Composable
private fun FrameView(player: ExoPlayer, ratio: Float, frame: Long, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(Size.Zero) }
    val shownZoom by animateFloatAsState(zoom, MaterialTheme.motionScheme.fastSpatialSpec(), label = "frameZoom")
    Box(
        modifier = modifier
            .aspectRatio(ratio.coerceIn(0.3f, 3f))
            .clip(RoundedCornerShape(32.dp))
            .background(Color.Black)
            .onSizeChanged { boxSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    zoom = if (zoom > 1.01f) 1f else DOUBLE_TAP_ZOOM
                    pan = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    val next = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                    zoom = next
                    // Kept within the picture: its edges never come inside the frame.
                    val limitX = boxSize.width * (next - 1f) / 2f
                    val limitY = boxSize.height * (next - 1f) / 2f
                    pan = Offset((pan.x + panChange.x).coerceIn(-limitX, limitX), (pan.y + panChange.y).coerceIn(-limitY, limitY))
                }
            },
    ) {
        MediaPreview(
            exoPlayer = player,
            colorMatrix = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = shownZoom
                    scaleY = shownZoom
                    translationX = pan.x
                    translationY = pan.y
                },
        )
        // The frame's number.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                stringResource(R.string.frame_picker_frame_badge, frame),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = scheme.onPrimaryContainer,
            )
        }
        AnimatedVisibility(
            visible = zoom > 1.01f,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.tertiary)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    stringResource(R.string.frame_picker_zoom, String.format(java.util.Locale.ROOT, "%.1f", zoom)),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = scheme.onTertiary,
                )
            }
        }
    }
}

/** The timecode, the strip, the wheel, the buttons and what to do with the frame. */
@Composable
private fun FrameControls(
    frame: Long,
    firstFrame: Long,
    lastFrame: Long,
    positionMs: Long,
    fps: Float,
    playing: Boolean,
    uri: Uri,
    rangeStartMs: Long,
    rangeEndMs: Long,
    onFrame: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onEditAsPhoto: () -> Unit,
    onExport: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val currentFrame by rememberUpdatedState(frame)
    val step: (Long) -> Boolean = { by ->
        val target = (currentFrame + by).coerceIn(firstFrame, lastFrame)
        if (target != currentFrame) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onFrame(target)
            true
        } else {
            false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        // -- Where it is --------------------------------------------------------------------
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = frameTimecode(positionMs),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = scheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(stringResource(R.string.frame_picker_frame_of, frame - firstFrame + 1, lastFrame - firstFrame + 1), scheme.secondaryContainer, scheme.onSecondaryContainer)
                InfoPill(stringResource(R.string.frame_picker_fps, fpsLabel(fps)), scheme.surfaceContainerHighest, scheme.onSurface)
            }
        }

        // -- Coarse: the strip of frames ----------------------------------------------------
        FilmStrip(
            uri = uri,
            rangeStartMs = rangeStartMs,
            rangeEndMs = rangeEndMs,
            fraction = if (lastFrame > firstFrame) (frame - firstFrame).toFloat() / (lastFrame - firstFrame) else 0f,
            onFraction = { f -> onFrame(firstFrame + ((lastFrame - firstFrame) * f.coerceIn(0f, 1f)).roundToLong()) },
        )

        // -- Fine: the wheel ---------------------------------------------------------------
        JogWheel(onFrames = { by -> step(by.toLong()) })

        // -- One or ten at a time, and play -------------------------------------------------
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RepeatingIconButton(
                icon = Icons.Rounded.KeyboardDoubleArrowLeft,
                contentDescription = stringResource(R.string.frame_picker_back_ten),
                onStep = { step(-BIG_STEP) },
                enabled = frame > firstFrame,
                size = 48.dp,
            )
            RepeatingIconButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.frame_picker_back_one),
                onStep = { step(-1) },
                enabled = frame > firstFrame,
                size = 56.dp,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
            )
            MorphingPlayButton(
                isPlaying = playing,
                onClick = onTogglePlay,
                playDescription = stringResource(R.string.action_play),
                pauseDescription = stringResource(R.string.action_pause),
                size = 72.dp,
            )
            RepeatingIconButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.frame_picker_next_one),
                onStep = { step(1) },
                enabled = frame < lastFrame,
                size = 56.dp,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
            )
            RepeatingIconButton(
                icon = Icons.Rounded.KeyboardDoubleArrowRight,
                contentDescription = stringResource(R.string.frame_picker_next_ten),
                onStep = { step(BIG_STEP) },
                enabled = frame < lastFrame,
                size = 48.dp,
            )
        }

        // -- What to do with it -------------------------------------------------------------
        Button(
            onClick = onEditAsPhoto,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.frame_picker_edit), style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.frame_picker_all), style = MaterialTheme.typography.titleSmall)
        }
        PanelHint(stringResource(R.string.frame_picker_hint))
    }
}

@Composable
private fun InfoPill(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * The clip as a strip of small frames, the playhead on it: a touch or a drag
 * anywhere goes there ([onFraction], 0..1 of the clip), ticking as it moves.
 */
@Composable
private fun FilmStrip(uri: Uri, rangeStartMs: Long, rangeEndMs: Long, fraction: Float, onFraction: (Float) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    val count = if (widthPx <= 0f) 0 else (widthPx / with(density) { STRIP_FRAME_WIDTH.toPx() }).toInt().coerceIn(MIN_STRIP_FRAMES, MAX_STRIP_FRAMES)
    val frames = remember(uri, count, rangeStartMs, rangeEndMs) { mutableStateListOf<Bitmap?>().apply { repeat(count) { add(null) } } }
    LaunchedEffect(uri, count, rangeStartMs, rangeEndMs) {
        if (count == 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val boxPx = with(density) { STRIP_HEIGHT.roundToPx() }
                for (i in 0 until count) {
                    val at = rangeStartMs + (rangeEndMs - rangeStartMs) * (i + 0.5f) / count
                    val bitmap = runCatching { smallFrameAt(retriever, (at * 1000).toLong(), boxPx) }.getOrNull()
                    withContext(Dispatchers.Main) { if (i < frames.size) frames[i] = bitmap }
                }
            } catch (e: Exception) {
                // No strip: the wheel and the buttons still work.
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
    val currentOnFraction by rememberUpdatedState(onFraction)
    var lastTick by remember { mutableLongStateOf(0L) }
    val tickAndGo: (Float) -> Unit = { x ->
        if (widthPx > 0f) {
            val now = System.nanoTime()
            if (now - lastTick > STRIP_TICK_NANOS) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                lastTick = now
            }
            currentOnFraction((x / widthPx).coerceIn(0f, 1f))
        }
    }
    val handle = scheme.primary
    val ring = scheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT + 16.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    tickAndGo(down.position.x)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            tickAndGo(change.position.x)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(STRIP_HEIGHT)
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainerHighest),
        ) {
            frames.forEach { bitmap ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (bitmap != null) {
                        val image = remember(bitmap) { bitmap.asImageBitmap() }
                        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        // The playhead: a pill across the strip, a ring at its middle.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = size.width * fraction.coerceIn(0f, 1f)
            val w = 6.dp.toPx()
            drawRoundRect(
                color = handle,
                topLeft = Offset(x - w / 2f, 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
            drawCircle(color = handle, radius = 11.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(color = ring, radius = 5.dp.toPx(), center = Offset(x, size.height / 2f))
        }
    }
}

/**
 * A wheel to turn sideways: every [WHEEL_FRAME_STEP] of travel is one frame
 * ([onFrames], with its tick, false at an end), and thrown it spins on and
 * slows down. Its marks move under the finger.
 */
@Composable
private fun JogWheel(onFrames: (Int) -> Boolean) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stepPx = with(density) { WHEEL_FRAME_STEP.toPx() }
    var travel by remember { mutableFloatStateOf(0f) }
    val currentOnFrames by rememberUpdatedState(onFrames)
    val spin = remember { Animatable(0f) }
    // The travel not yet turned into frames.
    var pending by remember { mutableFloatStateOf(0f) }
    fun move(delta: Float): Boolean {
        travel += delta
        pending += delta
        var moved = true
        while (abs(pending) >= stepPx) {
            val by = if (pending > 0f) -1 else 1
            pending += if (pending > 0f) -stepPx else stepPx
            if (!currentOnFrames(by)) {
                pending = 0f
                moved = false
                break
            }
        }
        return moved
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        scope.launch { spin.stop() }
                        tracker.resetTracking()
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        if (abs(velocity) > MIN_SPIN_VELOCITY) {
                            scope.launch {
                                spin.snapTo(0f)
                                var last = 0f
                                spin.animateDecay(velocity, exponentialDecay(frictionMultiplier = SPIN_FRICTION)) {
                                    val delta = value - last
                                    last = value
                                    if (!move(delta)) scope.launch { spin.stop() }
                                }
                            }
                        }
                    },
                    onDragCancel = { tracker.resetTracking() },
                ) { change, dragAmount ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    move(dragAmount)
                }
            },
    ) {
        val mark = scheme.onSurfaceVariant
        val strong = scheme.primary
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            val spacing = stepPx
            val offset = ((travel % spacing) + spacing) % spacing
            val count = (size.width / spacing).toInt() + 2
            val middle = size.width / 2f
            for (i in -1..count) {
                val x = i * spacing + offset
                if (x < 0f || x > size.width) continue
                // Marks fade and shorten towards the sides: a wheel seen from the front.
                val away = abs(x - middle) / middle
                val alpha = (1f - away).coerceIn(0.15f, 1f)
                val h = size.height * (0.35f + 0.45f * (1f - away))
                drawLine(
                    color = mark.copy(alpha = alpha),
                    start = Offset(x, (size.height - h) / 2f),
                    end = Offset(x, (size.height + h) / 2f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            // The fixed mark in the middle.
            drawRoundRect(
                color = strong,
                topLeft = Offset(middle - 2.dp.toPx(), 0f),
                size = Size(4.dp.toPx(), size.height),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}

/**
 * Every frame into a zip: how many a second (every frame of the file, or
 * fewer), over which part of the clip, and how many pictures that makes.
 */
@Composable
private fun FramesExportSheet(
    format: OutputFormat,
    fps: Float,
    rangeStartMs: Long,
    rangeEndMs: Long,
    currentMs: Long,
    onDismiss: () -> Unit,
    onExport: (Float, Long, Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rates = remember(fps) { (listOf(fps) + EXPORT_RATES.filter { it < fps - 0.5f }).distinct() }
    var rate by remember(fps) { mutableFloatStateOf(fps) }
    var from by remember { mutableFloatStateOf(rangeStartMs.toFloat()) }
    var to by remember { mutableFloatStateOf(rangeEndMs.toFloat()) }
    val lengthMs = (to - from).coerceAtLeast(0f)
    val pictures = floor(lengthMs / 1000f * rate).toLong().coerceAtLeast(if (lengthMs > 0f) 1L else 0L)
    // Frames are saved in a format FFmpeg writes: a WebP's are PNGs (see sequenceFormat).
    val saved = if (format.isImage && !format.needsPlatformEncoder) format else OutputFormat.PNG
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatBadge(format = saved, size = 56.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.frames_export_title), style = MaterialTheme.typography.headlineSmallEmphasized, color = scheme.onSurface)
                    Text(
                        stringResource(R.string.frames_export_subtitle, stringResource(saved.labelRes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            // How many a second.
            Text(stringResource(R.string.frames_export_rate), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurface)
            OptionTileRow(
                options = rates.mapIndexed { index, r ->
                    TileOption(
                        value = r,
                        label = if (index == 0) stringResource(R.string.frames_export_every, fpsLabel(r)) else stringResource(R.string.frame_picker_fps, fpsLabel(r)),
                        icon = when {
                            index == 0 -> Icons.Rounded.Movie
                            r >= 10f -> Icons.Rounded.Speed
                            r >= 2f -> Icons.Rounded.SlowMotionVideo
                            else -> Icons.Rounded.Timer
                        },
                    )
                },
                selected = rate,
                onSelect = { rate = it },
            )

            // Over which part.
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.frames_export_part), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurface, modifier = Modifier.weight(1f))
                    Text(
                        "${frameTimecode(from.roundToLong())} - ${frameTimecode(to.roundToLong())}",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (rangeEndMs > rangeStartMs) {
                    RangeSlider(
                        value = from..to,
                        onValueChange = { range ->
                            if (range.start.roundToLong() != from.roundToLong() || range.endInclusive.roundToLong() != to.roundToLong()) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            from = range.start
                            to = range.endInclusive
                        },
                        valueRange = rangeStartMs.toFloat()..rangeEndMs.toFloat(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            from = currentMs.toFloat().coerceIn(rangeStartMs.toFloat(), to)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.frames_export_from_here), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            to = currentMs.toFloat().coerceIn(from, rangeEndMs.toFloat())
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.frames_export_to_here), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            from = rangeStartMs.toFloat()
                            to = rangeEndMs.toFloat()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.frames_export_whole), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            // How many pictures that makes.
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = String.format(java.util.Locale.getDefault(), "%,d", pictures),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    color = scheme.primary,
                )
                Text(stringResource(R.string.frames_export_count), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                if (pictures > MANY_PICTURES) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.frames_export_many), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                }
            }
            PanelHint(stringResource(R.string.frames_export_hint))
            Button(
                onClick = {
                    // Within the clip, on the sequence's clock.
                    onExport(rate, from.roundToLong(), to.roundToLong())
                },
                enabled = pictures > 0,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.frames_export_go, pictures.toInt()), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Frames and their clock
// ---------------------------------------------------------------------------

/**
 * The frame shown at [ms] of the file at [fps]: the last one starting before
 * the end of that millisecond (so that [startOf] a frame is that frame again).
 */
internal fun frameAt(ms: Long, fps: Float): Long = floor((ms.coerceAtLeast(0L) + 1) * fps / 1000.0).toLong()

/** Where frame [frame] starts, in whole milliseconds at or before its time (the player and FFmpeg show it from there). */
internal fun startOf(frame: Long, fps: Float): Long = floor(frame.coerceAtLeast(0L) * 1000.0 / fps + FRAME_EPSILON).toLong()

/** "1:23.456", or "1:02:03.456" past an hour. */
private fun frameTimecode(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val h = total / 3_600_000
    val m = (total / 60_000) % 60
    val s = (total / 1000) % 60
    val millis = total % 1000
    return if (h > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, millis)
    } else {
        String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", m, s, millis)
    }
}

/** 30, 29.97, 23.976: whole rates without decimals. */
private fun fpsLabel(fps: Float): String =
    if (abs(fps - fps.roundToInt()) < 0.01f) fps.roundToInt().toString() else String.format(java.util.Locale.ROOT, "%.2f", fps).trimEnd('0').trimEnd('.')

/** A rate below this is not believed: the file's is taken as unknown. */
private const val MIN_SOURCE_FPS = 1f

/** The rate frames are counted at when the file does not say. */
private const val DEFAULT_FPS = 30f

/** Rounding slack of the frame clock (a frame at 33.3333 ms is still frame 1 at 33.3333). */
private const val FRAME_EPSILON = 1e-6

/** How often the frame follows the player while it plays. */
private const val PLAY_TICK_MS = 16L

/** The big step of the buttons, in frames. */
private const val BIG_STEP = 10L

/** At most this much of the height for the picture, on a phone upright. */
private const val PICTURE_SHARE = 0.45f

/** How close two fingers bring the frame, and a double tap. */
private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

private val STRIP_HEIGHT = 56.dp
private val STRIP_FRAME_WIDTH = 44.dp
private const val MIN_STRIP_FRAMES = 4
private const val MAX_STRIP_FRAMES = 24
private const val STRIP_TICK_NANOS = 40_000_000L

/** Travel of the wheel for one frame. */
private val WHEEL_FRAME_STEP = 14.dp

/** A throw faster than this (pixels a second) spins the wheel on. */
private const val MIN_SPIN_VELOCITY = 400f
private const val SPIN_FRICTION = 2.5f

/** The rates offered below the file's own, frames a second. */
private val EXPORT_RATES = listOf(30f, 24f, 15f, 10f, 5f, 2f, 1f, 0.5f)

/** More pictures than this make a large zip: said so. */
private const val MANY_PICTURES = 3_000L
