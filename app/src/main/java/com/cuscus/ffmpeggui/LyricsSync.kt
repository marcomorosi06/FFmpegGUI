@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cuscus.ffmpeggui.core.subtitles.LrcLines
import com.cuscus.ffmpeggui.core.subtitles.LyricsText
import com.cuscus.ffmpeggui.ui.expressive.springPress
import java.util.Locale

/*
 * The lyrics timed by hand (issue #16): a page over the editor with two ways
 * of seeing them. "Plain": the words as text, to type or paste. "Synced": a
 * line each, with a stopwatch that stamps the line with where the song is
 * playing now (held, it takes the time away), the words, and a button that
 * plays the song from that line. Under them, the song's own player: where it
 * is, play and pause, five seconds back and on. The line sung now is lit, the
 * next one to stamp is marked; stamping one goes on to the next. The times
 * are those of the file being made (its trim, speed and all), as the preview
 * plays it. Done writes them into the lyrics tag, the LRC way when timed.
 */

/** The song as the editor plays it, for the lyrics page: where it is, how long, playing, and how to move it. */
class LyricsPlayback(
    /** Where the song is now, on the clock of the file being made. */
    val positionMs: () -> Long,
    /** How long the file being made is. */
    val durationMs: () -> Long,
    val playing: () -> Boolean,
    val onPlaying: (Boolean) -> Unit,
    /** Goes to this instant of the file being made. */
    val onSeek: (Long) -> Unit,
)

/** The two ways the page shows the lyrics. */
private enum class LyricsView { PLAIN, SYNCED }

/**
 * The lyrics page: [lyrics] as they are in the tag, played by [playback];
 * [onDone] gets the lyrics to write (plain lines, or timed LRC), [onDismiss]
 * leaves them as they were.
 */
@Composable
internal fun LyricsSyncDialog(
    lyrics: String,
    playback: LyricsPlayback,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val first = remember { LrcLines.parse(lyrics) }
    // The header of an LRC file (artist, title, offset...) is kept as it came.
    val header = first.header
    var lines by remember { mutableStateOf(first.lines) }
    var plain by remember { mutableStateOf(LrcLines.plain(first.lines)) }
    var view by remember { mutableStateOf(if (LyricsText.isSynced(lyrics) || lyrics.isBlank()) LyricsView.SYNCED else LyricsView.PLAIN) }
    // The line the next stamp goes to.
    var next by remember { mutableIntStateOf(first.lines.indexOfFirst { it.timeMs == null }.coerceAtLeast(0)) }
    // Where the song is, read once a frame while the page is open.
    var position by remember { mutableStateOf(playback.positionMs()) }
    var duration by remember { mutableStateOf(playback.durationMs()) }
    var playing by remember { mutableStateOf(playback.playing()) }
    LaunchedEffect(playback) {
        while (true) {
            withFrameMillis { }
            position = playback.positionMs()
            duration = playback.durationMs()
            playing = playback.playing()
        }
    }
    // Leaving the page pauses the song where it is.
    androidx.compose.runtime.DisposableEffect(playback) { onDispose { playback.onPlaying(false) } }
    val listState = rememberLazyListState()
    LaunchedEffect(next, view) {
        if (view == LyricsView.SYNCED && lines.isNotEmpty()) listState.animateScrollToItem((next - 2).coerceAtLeast(0))
    }
    fun switchTo(target: LyricsView) {
        if (target == view) return
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (target == LyricsView.SYNCED) {
            // The words as typed, each line keeping the time it had.
            lines = LrcLines.retimed(lines, plain)
            next = lines.indexOfFirst { it.timeMs == null }.coerceAtLeast(0)
        } else {
            plain = LrcLines.plain(lines)
        }
        view = target
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = scheme.surfaceContainerLowest, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                // Close, the two views, done.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    FilledTonalIconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lyrics_sync_close))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        LyricsView.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = view == option,
                                onClick = { switchTo(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, LyricsView.entries.size),
                            ) {
                                Text(stringResource(if (option == LyricsView.PLAIN) R.string.lyrics_plain else R.string.lyrics_synced), maxLines = 1)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            val text = if (view == LyricsView.PLAIN) {
                                plain.trimEnd()
                            } else if (lines.any { it.timeMs != null }) {
                                LrcLines.format(LrcLines.Lyrics(header, lines))
                            } else {
                                LrcLines.plain(lines)
                            }
                            onDone(text)
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lyrics_sync_done))
                    }
                }
                AnimatedContent(
                    targetState = view,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "lyricsView",
                    modifier = Modifier.weight(1f),
                ) { shown ->
                    if (shown == LyricsView.PLAIN) {
                        OutlinedTextField(
                            value = plain,
                            onValueChange = { plain = it },
                            label = { Text(stringResource(R.string.lyrics_field)) },
                            placeholder = { Text(stringResource(R.string.lyrics_sync_plain_hint)) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else if (lines.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.lyrics_sync_empty), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                                FilledTonalButton(onClick = { switchTo(LyricsView.PLAIN) }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.lyrics_sync_type))
                                }
                            }
                        }
                    } else {
                        val sung = LrcLines.lineAt(lines, position)
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(lines) { index, line ->
                                SyncedLine(
                                    line = line,
                                    sung = index == sung,
                                    next = index == next,
                                    onStamp = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        lines = lines.toMutableList().also { it[index] = line.copy(timeMs = position.coerceAtLeast(0L)) }
                                        next = (index + 1).coerceAtMost(lines.lastIndex)
                                    },
                                    onClear = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                        lines = lines.toMutableList().also { it[index] = line.copy(timeMs = null) }
                                        next = index
                                    },
                                    onPlay = {
                                        val time = line.timeMs ?: return@SyncedLine
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        playback.onSeek(time)
                                        playback.onPlaying(true)
                                        next = (index + 1).coerceAtMost(lines.lastIndex)
                                    },
                                    onPick = { next = index },
                                )
                            }
                        }
                    }
                }
                SongBar(
                    position = position,
                    duration = duration,
                    playing = playing,
                    onPlaying = { wanted ->
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        playback.onPlaying(wanted)
                    },
                    onSeek = { ms -> playback.onSeek(ms.coerceIn(0L, duration.coerceAtLeast(0L))) },
                )
            }
        }
    }
}

/**
 * One line of the synced view: its stopwatch (tapped, the line is sung from
 * now; held, it is not timed any more), its words, the button that plays the
 * song from it. Lit while it is sung; marked as the next to stamp.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SyncedLine(
    line: LrcLines.Line,
    sung: Boolean,
    next: Boolean,
    onStamp: () -> Unit,
    onClear: () -> Unit,
    onPlay: () -> Unit,
    onPick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val background by animateColorAsState(
        when {
            sung -> scheme.primaryContainer
            next -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainer
        },
        motion.defaultEffectsSpec(),
        label = "lyricsLine",
    )
    val stampLabel = stringResource(R.string.lyrics_sync_stamp)
    val clearLabel = stringResource(R.string.lyrics_sync_clear)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (next) 28.dp else 20.dp))
            .background(background)
            .combinedClickable(onClick = onPick)
            .padding(6.dp),
    ) {
        // The stopwatch: the time under it, a tap stamps, a long press clears.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(84.dp)
                .springPress(interaction, pressedScale = 0.9f)
                .clip(RoundedCornerShape(if (pressed) 28.dp else 16.dp))
                .background(if (line.timeMs != null) scheme.tertiaryContainer else scheme.surfaceContainerHighest)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = stampLabel,
                    onLongClickLabel = clearLabel,
                    onLongClick = { if (line.timeMs != null) onClear() },
                    onClick = onStamp,
                )
                .padding(vertical = 8.dp)
                .semantics { contentDescription = stampLabel },
        ) {
            Icon(
                Icons.Rounded.AvTimer,
                contentDescription = null,
                tint = if (line.timeMs != null) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = line.timeMs?.let(::lyricsClock) ?: "--:--.--",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (line.timeMs != null) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = line.text.ifEmpty { stringResource(R.string.lyrics_sync_pause) },
            style = if (sung) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            color = when {
                line.text.isEmpty() -> scheme.onSurfaceVariant
                sung -> scheme.onPrimaryContainer
                else -> scheme.onSurface
            },
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPlay, enabled = line.timeMs != null) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.lyrics_sync_play_from))
        }
    }
}

/** The song's player under the lines: where it is (dragged to move it), back, play or pause, on, and the time. */
@Composable
private fun SongBar(
    position: Long,
    duration: Long,
    playing: Boolean,
    onPlaying: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var dragged by remember { mutableStateOf<Float?>(null) }
    val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Slider(
                value = dragged ?: fraction,
                onValueChange = { dragged = it },
                onValueChangeFinished = {
                    dragged?.let { onSeek((it * duration).toLong()) }
                    dragged = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { onSeek(position - SKIP_MS) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Replay5, contentDescription = stringResource(R.string.lyrics_sync_back))
                }
                FilledIconButton(onClick = { onPlaying(!playing) }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.lyrics_sync_pause_song else R.string.lyrics_sync_play),
                    )
                }
                FilledTonalIconButton(onClick = { onSeek(position + SKIP_MS) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Forward5, contentDescription = stringResource(R.string.lyrics_sync_forward))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = lyricsClock(dragged?.let { (it * duration).toLong() } ?: position) + " / " + lyricsClock(duration),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "m:ss.xx", as the lines are timed. */
private fun lyricsClock(ms: Long): String {
    val hundredths = ms.coerceAtLeast(0L) / 10
    return String.format(Locale.ROOT, "%d:%02d.%02d", hundredths / 6000, (hundredths / 100) % 60, hundredths % 100)
}

/** How far back and on the song's buttons go. */
private const val SKIP_MS = 5_000L
