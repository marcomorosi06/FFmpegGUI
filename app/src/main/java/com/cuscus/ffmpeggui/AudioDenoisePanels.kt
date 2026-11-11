@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cuscus.ffmpeggui.core.engine.AudioDenoiseSampler
import com.cuscus.ffmpeggui.core.preview.AudioDenoiseSampleRequest
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The sound's denoise (Effects.audioDenoise, FFmpeg's anlmdn), for a clip or
 * for the whole video, in both editors: the Noise tool.
 *
 * The preview's live sound cannot run anlmdn (see core's AudioDenoiseSample),
 * so the panel lets you hear it where you ask: a few seconds of the sound
 * under the playhead, made by FFmpeg as it is and through the export's own
 * filter, played together in a loop, one heard and the other silent, a
 * connected button group switching between them at the same instant. In
 * Material 3 Expressive: the levels as a connected button group and a slider;
 * the listening card a cookie that swells into a soft burst (with the
 * expressive loading indicator) while FFmpeg works and settles when it is in,
 * a wavy progress line under it as the loop plays.
 */

/** Makes the listening comparisons; null where there is no FFmpeg (previews of the UI). */
val LocalAudioDenoiseSampler = compositionLocalOf<AudioDenoiseSampler?> { null }

/**
 * Where the sound to listen to is, as the playhead stands: [positionMs] of the
 * file [sourceUri], in clip [clipId] (from [clipStartMs] to [clipEndMs] of the
 * file); [hasSound] false for a clip with nothing to hear (a picture).
 */
internal data class SoundSpot(
    val clipId: String,
    val sourceUri: String,
    val positionMs: Long,
    val clipStartMs: Long,
    val clipEndMs: Long?,
    val hasSound: Boolean,
)

/** The levels offered in one tap; the slider gives the values in between. */
private enum class NoiseLevel(@StringRes val labelRes: Int, val strength: Float) {
    OFF(R.string.details_level_off, 0f),
    LIGHT(R.string.details_level_light, 0.25f),
    MEDIUM(R.string.details_level_medium, 0.5f),
    STRONG(R.string.details_level_strong, 0.8f),
}

/** Which of the two the listening card lets you hear. */
private enum class Heard(@StringRes val labelRes: Int) {
    BEFORE(R.string.denoise_before),
    AFTER(R.string.denoise_after),
}

/**
 * The Noise tool.
 *
 * @param strength   the denoise being edited (0..1): the clip's, or the whole video's.
 * @param clipScope  the panel edits a clip (else the whole video).
 * @param other      the denoise the export also runs there: the whole video's for a
 *                   clip, the clip's under the playhead for the whole video.
 * @param spotNow    where the sound to listen to is, read when asked.
 * @param strengthsAt every denoise the export runs on the sound of a clip (its id),
 *                   in order: the clip's, then the whole video's.
 * @param onPausePreview stops the editor's own playback while the comparison plays.
 */
@Composable
internal fun AudioDenoisePanel(
    strength: Float,
    onStrength: (Float) -> Unit,
    clipScope: Boolean,
    other: Float,
    spotNow: () -> SoundSpot?,
    strengthsAt: (String) -> List<Float>,
    onPausePreview: () -> Unit,
    enabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val level = NoiseLevel.entries.firstOrNull { abs(it.strength - strength) < 0.005f }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ConnectedChoiceGroup(
            options = NoiseLevel.entries,
            selected = level,
            label = { option -> option?.let { stringResource(it.labelRes) }.orEmpty() },
            onSelect = { option -> option?.let { onStrength(it.strength) } },
            enabled = enabled,
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.audio_denoise_strength),
                valueText = if (strength > 0f) stringResource(R.string.value_percent, (strength * 100).roundToInt()) else stringResource(R.string.details_level_off),
                value = strength.coerceIn(0f, 1f),
                onValueChange = { v -> onStrength((v * 100f).roundToInt() / 100f) },
                valueRange = 0f..1f,
                icon = Icons.Rounded.Waves,
                defaultValue = 0f,
                enabled = enabled,
                input = ValueInput(step = 0.05f, unit = "%", scale = 100f, range = 0f..1f),
            )
            AnimatedVisibility(
                visible = strength >= STRONG_FROM,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                PanelHint(stringResource(R.string.audio_denoise_strong_hint))
            }
        }
        ListenCard(spotNow = spotNow, strengthsAt = strengthsAt, onPausePreview = onPausePreview)
        PanelHint(stringResource(if (clipScope) R.string.audio_denoise_hint_clip else R.string.audio_denoise_hint_whole))
        if (other > 0f) {
            PanelHint(
                stringResource(
                    if (clipScope) R.string.audio_denoise_with_whole else R.string.audio_denoise_with_clip,
                    (other * 100).roundToInt(),
                ),
            )
        }
    }
}

/** A comparison made: the two files, as it is and denoised. */
private class Comparison(val before: File, val after: File)

/**
 * The listening card: "Listen" makes the comparison of the sound under the
 * playhead; then both halves loop together, the one chosen heard. A change
 * of strength while it is open makes it again.
 */
@Composable
private fun ListenCard(spotNow: () -> SoundSpot?, strengthsAt: (String) -> List<Float>, onPausePreview: () -> Unit) {
    val sampler = LocalAudioDenoiseSampler.current
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    var spot by remember { mutableStateOf<SoundSpot?>(null) }
    var asked by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf<Comparison?>(null) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf(Heard.AFTER) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val strengths = spot?.let { strengthsAt(it.clipId) }
    val request = spot?.takeIf { asked && it.hasSound }?.let {
        AudioDenoiseSampleRequest(it.sourceUri, it.positionMs, it.clipStartMs, it.clipEndMs, strengths.orEmpty())
    }
    // The comparison, made again when what it asks for changes (a slider rests first).
    LaunchedEffect(sampler, request) {
        if (sampler == null || request == null) return@LaunchedEffect
        working = true
        delay(LISTEN_SETTLE_MS)
        val pair = withContext(Dispatchers.IO) { sampler.sample(request) }
        working = false
        if (pair == null) {
            failed = true
            made = null
        } else {
            failed = false
            made = Comparison(File(pair.first), File(pair.second))
        }
    }
    // The two halves in two players, started together and looping; only the one chosen is heard.
    val players = remember(made) {
        made?.let { comparison ->
            listOf(comparison.before, comparison.after).map { file ->
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                }
            }
        }
    }
    val latestHeard by rememberUpdatedState(heard)
    DisposableEffect(players) {
        val shown = players
        if (shown != null) {
            shown.forEachIndexed { i, player -> player.volume = if (i == latestHeard.ordinal) 1f else 0f }
            shown.forEach { it.playWhenReady = playing }
        }
        onDispose {
            shown?.forEach { it.release() }
        }
    }
    // The files go with the comparison they were made for.
    DisposableEffect(made) {
        val files = made
        onDispose {
            files?.before?.delete()
            files?.after?.delete()
        }
    }
    LaunchedEffect(players, heard) {
        players?.forEachIndexed { i, player -> player.volume = if (i == heard.ordinal) 1f else 0f }
    }
    LaunchedEffect(players, playing) {
        val shown = players ?: return@LaunchedEffect
        if (playing) {
            onPausePreview()
            // Both from the same instant, so that switching keeps the place.
            val at = shown[0].currentPosition
            shown.forEach { it.seekTo(at) }
            shown.forEach { it.play() }
            while (true) {
                withFrameMillis { }
                val first = shown[0]
                val length = first.duration.takeIf { it > 0L } ?: continue
                progress = (first.currentPosition.toFloat() / length).coerceIn(0f, 1f)
                // Drifted apart (a buffer late): back in step.
                if (abs(shown[1].currentPosition - first.currentPosition) > RESYNC_MS) shown[1].seekTo(first.currentPosition)
            }
        } else {
            shown.forEach { it.pause() }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.SoftBurst,
                // Swollen while FFmpeg works; back on a spring once the comparison is in.
                morphed = working,
                color = if (made != null) scheme.primary else scheme.surfaceContainerHighest,
                modifier = Modifier.size(56.dp),
            ) {
                if (working) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Icon(
                        if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = if (made != null) scheme.onPrimary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.audio_denoise_listen_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(
                    stringResource(
                        when {
                            working -> R.string.audio_denoise_listen_working
                            failed -> R.string.audio_denoise_listen_failed
                            spot?.hasSound == false -> R.string.audio_denoise_listen_silent
                            made != null -> R.string.audio_denoise_listen_ready
                            else -> R.string.audio_denoise_listen_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        AnimatedContent(
            targetState = made != null,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "listen",
        ) { ready ->
            if (!ready) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        spot = spotNow()
                        asked = true
                        playing = true
                    },
                    enabled = sampler != null && !working,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.audio_denoise_listen))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                playing = !playing
                            },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(if (playing) R.string.lyrics_sync_pause_song else R.string.lyrics_sync_play),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        ConnectedChoiceGroup(
                            options = Heard.entries,
                            selected = heard,
                            label = { stringResource(it.labelRes) },
                            onSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                heard = it
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
                        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            // Another place: where the playhead is now.
                            spot = spotNow()
                        },
                        enabled = !working,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.audio_denoise_listen_here))
                    }
                }
            }
        }
    }
}

/** From this strength anlmdn starts to take the voice's own breath with the noise. */
private const val STRONG_FROM = 0.75f

/** How long a slider rests before the comparison is made again. */
private const val LISTEN_SETTLE_MS = 400L

/** How far the two halves may drift apart before they are put back in step. */
private const val RESYNC_MS = 40L
