@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ColumnScope
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.cuscus.ffmpeggui.conversion.SpeechProblem
import com.cuscus.ffmpeggui.conversion.SpeechStatus
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.SubtitleBlock
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLayout
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle
import com.cuscus.ffmpeggui.core.subtitles.WordHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * The transcript before it is saved (.txt or .srt). The lines are what this
 * screen is for, so they get most of it: the picture is kept to under a third
 * of the height (and can be hidden; it steps aside while a line is corrected),
 * the player's controls sit in one card under it, and each line is a large card
 * with its time on top, the word being said lit and the line followed as it
 * plays. A tap on a word plays from there; the pencil opens the line for
 * correcting (its words take the line's time). Everything stays on the phone.
 *
 * An hour and more of speech makes thousands of lines, so nothing here grows
 * with them on the main thread: the lines are made in the background (the old
 * ones stay shown meanwhile), the line being said is found by halving, and the
 * clock, read 25 times a second, is read only by the card of the controls and
 * by the line being said, so that nothing else is drawn again as it plays.
 */

@Composable
fun TranscriptScreen(
    fileName: String,
    uri: Uri,
    isVideo: Boolean,
    /** Width / height of the picture, 0 when unknown. */
    videoRatio: Float,
    format: OutputFormat,
    /** The words heard in the file (kept and being found), on its own clock. */
    words: List<SpokenWord>,
    speech: SpeechStatus,
    /** The file has been listened to (it may have said nothing). */
    heard: Boolean,
    style: SubtitleStyle,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onEditLine: (Long, Long, String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    // -- The player ----------------------------------------------------------------
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.Builder().setUri(uri).build())
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // Not read while composing this screen (see the note at the top): only through [position].
    var positionMs by remember { mutableLongStateOf(0L) }
    val position: () -> Long = remember { { positionMs } }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.takeIf { it > 0 } ?: durationMs
            playing = player.isPlaying
            delay(TICK_MS)
        }
    }
    val seek: (Long) -> Unit = { ms ->
        val target = if (durationMs > 0) ms.coerceIn(0L, durationMs) else ms.coerceAtLeast(0L)
        player.seekTo(target)
        positionMs = target
    }

    // -- The lines -----------------------------------------------------------------
    // Lines as the subtitles make them, at most two rows each (the frame does not matter
    // here), made in the background; until they are, the ones made before stay.
    var layingOut by remember { mutableStateOf(true) }
    val lines by produceState(TranscriptLines.NONE, words, style) {
        layingOut = true
        value = withContext(Dispatchers.Default) { TranscriptLines.of(words, style) }
        layingOut = false
    }
    val blocks = lines.blocks
    val current by remember(blocks) {
        derivedStateOf(structuralEqualityPolicy()) { TranscriptLines.lineAt(blocks, positionMs) }
    }
    val listState = rememberLazyListState()
    var editing by remember { mutableStateOf(-1) }
    LaunchedEffect(current, playing) {
        if (playing && current >= 0 && editing < 0) listState.animateScrollToItem(current)
    }
    // The line being corrected is brought into view (above the keyboard), the file paused.
    LaunchedEffect(editing) {
        if (editing >= 0) {
            player.pause()
            listState.animateScrollToItem(editing)
        }
    }
    // The picture can be hidden for more room; it steps aside by itself while a line is corrected.
    var showPicture by remember { mutableStateOf(true) }
    val pictureShown = isVideo && showPicture && editing < 0

    // The file is listened to as soon as the screen opens, when it was not yet.
    LaunchedEffect(Unit) { if (!heard && speech !is SpeechStatus.Working) onListen() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .imePadding(),
    ) {
        // At most this much of the height for the picture, so that the lines always have the most.
        val ratio = if (videoRatio > 0f) videoRatio.coerceIn(0.5f, 2.4f) else 16f / 9f
        val pictureHeight = minOf(maxHeight * PICTURE_SHARE, (maxWidth - 32.dp) / ratio)

        val working = speech as? SpeechStatus.Working
        val titleRow: @Composable ColumnScope.() -> Unit = {
            // -- Title ---------------------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onBack()
                }) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = stringResource(R.string.action_back), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.transcript_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = scheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(fileName, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isVideo) {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        showPicture = !showPicture
                    }) {
                        Icon(
                            if (showPicture) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                            contentDescription = stringResource(if (showPicture) R.string.transcript_hide_picture else R.string.transcript_show_picture),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                FormatBadge(format = format, size = 44.dp)
            }
        }
        val picturePart: @Composable ColumnScope.(Dp) -> Unit = { pictureHeight ->
            // -- The picture, never more than a third of the screen --------------------
            AnimatedVisibility(
                visible = pictureShown,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(pictureHeight + 8.dp).padding(bottom = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black),
                    ) {
                        MediaPreview(exoPlayer = player, colorMatrix = null, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        val transport: @Composable ColumnScope.() -> Unit = {
            // -- The player's controls in one card ----------------------------------------
            TransportCard(
                isVideo = isVideo,
                fileName = fileName,
                position = position,
                durationMs = durationMs,
                playing = playing,
                onPlayPause = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (player.isPlaying) player.pause() else player.play()
                },
                onStep = { deltaMs ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    seek(positionMs + deltaMs)
                },
                onSeek = seek,
            )
        }
        val statusPart: @Composable ColumnScope.() -> Unit = {
            // -- Listening, or why it stopped ----------------------------------------------
            if (working != null) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.tertiaryContainer)
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 2.dp),
                ) {
                    val fraction = working.fraction
                    if (fraction != null) {
                        LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.transcript_listening),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        IconButton(onClick = onStop) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.speech_stop), tint = scheme.onTertiaryContainer)
                        }
                    }
                }
            } else if (speech is SpeechStatus.Failed || !heard) {
                // Stopped or failed before the end: say why, and offer to listen again.
                val failed = speech as? SpeechStatus.Failed
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (failed != null) scheme.errorContainer else scheme.surfaceContainerHigh)
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        stringResource(
                            when (failed?.problem) {
                                SpeechProblem.NO_MODEL -> R.string.speech_problem_model
                                SpeechProblem.NO_MEMORY -> R.string.speech_problem_memory
                                SpeechProblem.FAILED -> R.string.speech_problem_failed
                                null -> R.string.transcript_stopped
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed != null) scheme.onErrorContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    TextButton(onClick = onListen) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.transcript_listen))
                    }
                }
            } else if (blocks.isNotEmpty() && editing < 0) {
                Text(
                    stringResource(R.string.transcript_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
        val linesPart: @Composable ColumnScope.() -> Unit = {
            // -- The lines: the most room ------------------------------------------------------
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(
                    items = blocks,
                    key = { index, _ -> lines.keys[index] },
                    contentType = { _, _ -> LINE_ITEM },
                ) { index, block ->
                    TranscriptLine(
                        block = block,
                        current = index == current,
                        position = position,
                        editing = index == editing,
                        onSeek = { ms ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            seek(ms)
                        },
                        onEdit = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            editing = index
                        },
                        onSave = { text ->
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onEditLine(block.startMs, block.endMs, text)
                            editing = -1
                        },
                        onCancel = { editing = -1 },
                    )
                }
                if (blocks.isEmpty() && layingOut && words.isNotEmpty()) {
                    // The first lines of a long file take a moment to make.
                    item(contentType = LOADING_ITEM) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                            LoadingIndicator()
                        }
                    }
                } else if (blocks.isEmpty() && working == null && heard && !layingOut) {
                    item {
                        Text(
                            stringResource(R.string.transcript_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
        val savePart: @Composable ColumnScope.() -> Unit = {
            // Saving, out of the way while a line is corrected.
            AnimatedVisibility(
                visible = editing < 0,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        player.pause()
                        onSave()
                    },
                    enabled = blocks.isNotEmpty() && working == null,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(60.dp),
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(if (format == OutputFormat.SRT) R.string.transcript_save_srt else R.string.transcript_save_txt),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }

        val window = LocalWindowLayout.current
        if (window.twoPanes) {
            // Side by side: the file, its picture and its controls on one side, the lines
            // (all the height) on the other.
            val side = window.sidePadding(TRANSCRIPT_MAX_WIDTH)
            // Read here: inside the row, the box's own size cannot be reached.
            val paneWidth = (maxWidth - side * 2 - 20.dp) * 0.45f
            val widePictureHeight = minOf(maxHeight * WIDE_PICTURE_SHARE, paneWidth / ratio)
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = side, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    titleRow()
                    Spacer(modifier = Modifier.height(12.dp))
                    picturePart(widePictureHeight)
                    transport()
                    statusPart()
                }
                Column(modifier = Modifier.weight(0.55f).fillMaxHeight()) {
                    linesPart()
                    savePart()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                titleRow()
                Spacer(modifier = Modifier.height(12.dp))
                picturePart(pictureHeight)
                transport()
                statusPart()
                linesPart()
                savePart()
            }
        }
    }
}

/**
 * Play and pause (a large button that squares off while playing), five seconds
 * back and forward, the position and the length, in one rounded card. For a
 * sound file the card also names it (there is no picture above).
 */
@Composable
private fun TransportCard(
    isVideo: Boolean,
    fileName: String,
    /** The clock, read here only (each read draws this card again, and nothing else). */
    position: () -> Long,
    durationMs: Long,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onStep: (Long) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (playing) 18.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "playCorner")
    val clock = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    val positionMs = position()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (!isVideo) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(scheme.secondaryContainer),
                ) {
                    Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(fileName, style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Slider(
            value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction -> if (durationMs > 0) onSeek((fraction * durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(clockOf(positionMs), style = clock, color = scheme.onSurface, modifier = Modifier.weight(1f).padding(start = 4.dp))
            IconButton(onClick = { onStep(-STEP_MS) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Replay5, contentDescription = stringResource(R.string.transcript_back_5), tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(onClick = onPlayPause, shape = RoundedCornerShape(corner), modifier = Modifier.size(width = 72.dp, height = 56.dp)) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.transcript_pause else R.string.transcript_play),
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { onStep(STEP_MS) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Forward5, contentDescription = stringResource(R.string.transcript_forward_5), tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            }
            Text(
                if (durationMs > 0) clockOf(durationMs) else "",
                style = clock,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(end = 4.dp),
            )
        }
    }
}

/**
 * One line as a card: its time on top (a tap plays from there) with the pencil,
 * then its words, large, the one being said lit (a tap on a word plays from it).
 * The line being said takes the primary container and rounder corners; the one
 * being corrected opens into a large field with Cancel and Save.
 */
@Composable
private fun TranscriptLine(
    block: SubtitleBlock,
    current: Boolean,
    /** The clock; read only while this is the line being said. */
    position: () -> Long,
    editing: Boolean,
    onSeek: (Long) -> Unit,
    onEdit: () -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val words = block.words
    val container by animateColorAsState(
        when {
            editing -> scheme.surfaceContainerHighest
            current -> scheme.primaryContainer
            else -> scheme.surfaceContainerLow
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "lineContainer",
    )
    val content = if (current && !editing) scheme.onPrimaryContainer else scheme.onSurface
    val corner by animateDpAsState(if (current || editing) 28.dp else 20.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "lineCorner")
    val shape = RoundedCornerShape(corner)
    val time = clockOf(block.startMs)
    val lineStyle = lineTextStyle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (editing) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (current) scheme.primary else scheme.surfaceContainerHighest)
                    .clickable(onClickLabel = stringResource(R.string.transcript_play_from, time)) { onSeek(block.startMs) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (current) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!editing) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.subtitle_edit_line),
                        tint = if (current) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (editing) {
            var text by remember(block) { mutableStateOf(block.words.joinToString(" ") { it.text }) }
            Column(modifier = Modifier.padding(end = 12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace('\n', ' ') },
                    textStyle = lineStyle.copy(color = scheme.onSurface),
                    shape = RoundedCornerShape(20.dp),
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.subtitle_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.subtitle_edit_cancel), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(text) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.subtitle_edit_save), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            // Where each word starts in the text, to find the word under a tap.
            val starts = remember(block) {
                var at = 0
                words.map { word -> at.also { at += word.text.length + 1 } }
            }
            // Drawn again when the word lit changes, not at every tick of the clock.
            val lit by remember(block, current) {
                derivedStateOf(structuralEqualityPolicy()) {
                    if (current) SubtitleLayout.highlighted(block, position(), WordHighlight.WORD) else IntRange.EMPTY
                }
            }
            val litText = scheme.onPrimary
            val litBack = scheme.primary
            val shown = buildAnnotatedString {
                words.forEachIndexed { index, word ->
                    if (index > 0) append(' ')
                    if (index in lit) {
                        pushStyle(SpanStyle(color = litText, background = litBack, fontWeight = FontWeight.Bold))
                        append(word.text)
                        pop()
                    } else {
                        append(word.text)
                    }
                }
            }
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = shown,
                style = lineStyle,
                color = content,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .pointerInput(block) {
                        detectTapGestures { offset ->
                            val at = layout?.getOffsetForPosition(offset) ?: return@detectTapGestures
                            val index = starts.indexOfLast { it <= at }.coerceAtLeast(0)
                            words.getOrNull(index)?.let { onSeek(it.startMs) }
                        }
                    },
            )
        }
    }
}

/**
 * The lines of a transcript, as [TranscriptScreen] shows them, with what the
 * list needs: [keys], one per line, steady while more words come in (a
 * line's start, made unique).
 */
private class TranscriptLines(
    val blocks: List<SubtitleBlock>,
    val keys: LongArray,
) {
    companion object {
        val NONE = TranscriptLines(emptyList(), LongArray(0))

        fun of(words: List<SpokenWord>, style: SubtitleStyle): TranscriptLines {
            val blocks = SubtitleLayout.ofWords(words, style.copy(uppercase = false, maxLines = 2, highlight = WordHighlight.WORD), 16f / 9f)
            val keys = LongArray(blocks.size)
            var last = Long.MIN_VALUE
            for (i in blocks.indices) {
                val start = blocks[i].startMs
                last = if (start > last) start else last + 1
                keys[i] = last
            }
            return TranscriptLines(blocks, keys)
        }

        /** The line being said at [ms] in [blocks] (in order, apart), -1 when none. */
        fun lineAt(blocks: List<SubtitleBlock>, ms: Long): Int {
            var low = 0
            var high = blocks.size - 1
            var found = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (blocks[mid].startMs <= ms) {
                    found = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return if (found >= 0 && ms < blocks[found].endMs) found else -1
        }
    }
}

/** The type of a line: large and airy, to be read and corrected at ease (it follows the font size setting). */
@Composable
private fun lineTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 29.sp)

/** "m:ss" (or "h:mm:ss"). */
private fun clockOf(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}

private const val LINE_ITEM = "line"
private const val LOADING_ITEM = "loading"

/** How often the clock is read while playing. */
private const val TICK_MS = 40L

/** How far the back and forward buttons go. */
private const val STEP_MS = 5_000L

/** The most of the screen's height the picture takes. */
private const val PICTURE_SHARE = 0.3f

/** Side by side, the picture may take more of the height (it has its own side). */
private const val WIDE_PICTURE_SHARE = 0.5f

/** The widest the two sides get together (a monitor). */
private val TRANSCRIPT_MAX_WIDTH = 1280.dp