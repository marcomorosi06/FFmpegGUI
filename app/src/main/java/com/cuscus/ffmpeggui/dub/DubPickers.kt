@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.dub

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.settings.groupShape
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.max

/*
 * The pieces both dub screens are made of (DubCard, in the subtitles panel,
 * and AutoDubSectionContent, the editor's "Dub" tool): blocks joined as one
 * group, the languages as a strip of tokens and the voices as a carousel of
 * cards, each card with its own button to hear it. One line of each, however
 * many voices there are: they scroll sideways, the chosen one kept in sight.
 */

/**
 * Plays a voice's few words, one voice at a time: [playing] and [loading]
 * say which (its key), so the card that asked shows it.
 */
@Stable
internal class DubSampler(private val scope: CoroutineScope, private val make: suspend (DubSpeaker, Float) -> File?) {
    var playing by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null
    private var job: Job? = null

    /** Plays [speaker] at [speed]; stops it if it is the one playing (or being made). */
    fun toggle(speaker: DubSpeaker, speed: Float) {
        val asked = speaker.key
        val same = playing == asked || loading == asked
        stop()
        if (same) return
        loading = asked
        job = scope.launch {
            val file = try {
                make(speaker, speed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            loading = null
            if (file == null) return@launch
            try {
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { done ->
                        done.release()
                        if (player === done) {
                            player = null
                            playing = null
                        }
                    }
                    prepare()
                    start()
                }
                playing = asked
            } catch (e: Exception) {
                player?.release()
                player = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        loading = null
        player?.release()
        player = null
        playing = null
    }
}

/** A sampler for this screen, let go of when it goes. */
@Composable
internal fun rememberDubSampler(onSample: suspend (DubSpeaker, Float) -> File?): DubSampler {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onSample)
    val sampler = remember(scope) { DubSampler(scope) { speaker, speed -> latest(speaker, speed) } }
    DisposableEffect(sampler) { onDispose { sampler.stop() } }
    return sampler
}

/**
 * One block of a dub screen's group ([index] of [count]: the group's corners
 * round off at its ends only): an icon and a title, something at the end of
 * the title's line if given, then [content].
 */
@Composable
internal fun DubBlock(
    index: Int,
    count: Int,
    title: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(color)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.primary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

/** The blocks of a group, spaced as connected rows are. */
@Composable
internal fun DubGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth(), content = content)
}

/**
 * The languages as one line of tokens: its code in a shape that blooms when
 * chosen, its name under it. [found] (the subtitles' own, found by itself)
 * wears a little star. The chosen one is scrolled into sight.
 */
@Composable
internal fun LanguageStrip(languages: List<String>, selected: String?, found: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected, languages) {
        val at = languages.indexOf(selected)
        if (at >= 0) state.animateScrollToItem(max(0, at - 1))
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(languages, key = { it }) { lang ->
            LanguageToken(lang, selected = lang == selected, found = lang == found, enabled = enabled) { onSelect(lang) }
        }
    }
}

@Composable
private fun LanguageToken(code: String, selected: Boolean, found: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val name = languageName(code)
    val text by animateColorAsState(if (selected) scheme.onTertiary else scheme.onSurface, motion.defaultEffectsSpec(), label = "langText")
    val grow by animateFloatAsState(if (selected) 1f else 0.88f, motion.fastSpatialSpec(), label = "langGrow")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TOKEN_WIDTH)
            .springPress(interaction, pressedScale = 0.88f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .semantics {
                this.selected = selected
                contentDescription = name
            }
            .padding(vertical = 4.dp),
    ) {
        Box {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = MaterialShapes.Cookie7Sided,
                morphed = selected,
                color = if (selected) scheme.tertiary else scheme.surfaceContainerHighest,
                modifier = Modifier.size(52.dp).scale(grow),
            ) {
                Text(code.uppercase(Locale.ROOT), style = MaterialTheme.typography.titleMediumEmphasized, color = text)
            }
            if (found) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(scheme.primary),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.dub_language_found), tint = scheme.onPrimary, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            name,
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The voices as a carousel of cards: who it is (a woman, a man, or a quick
 * Piper voice), its name, its kind, a gavel when its licence has conditions,
 * and its own button to hear it at [speed]. A tap on a card chooses it; the
 * button chooses it too and plays it. The chosen card is filled and kept in sight.
 */
@Composable
internal fun VoiceCarousel(voices: List<DubSpeaker>, selected: DubSpeaker?, sampler: DubSampler, speed: Float, enabled: Boolean, onSelect: (DubSpeaker) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected?.key, voices) {
        val at = voices.indexOfFirst { it.key == selected?.key }
        if (at >= 0) state.animateScrollToItem(max(0, at - 1))
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(voices, key = { it.key }) { voice ->
            VoiceCard(
                voice = voice,
                selected = voice.key == selected?.key,
                playing = sampler.playing == voice.key,
                loading = sampler.loading == voice.key,
                enabled = enabled,
                onSelect = { onSelect(voice) },
                onPlay = {
                    onSelect(voice)
                    sampler.toggle(voice, speed)
                },
            )
        }
    }
}

@Composable
private fun VoiceCard(voice: DubSpeaker, selected: Boolean, playing: Boolean, loading: Boolean, enabled: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(if (selected) scheme.tertiaryContainer else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "voiceCard")
    val content by animateColorAsState(if (selected) scheme.onTertiaryContainer else scheme.onSurface, motion.defaultEffectsSpec(), label = "voiceText")
    val corner by animateDpAsState(if (selected) 28.dp else 18.dp, motion.defaultSpatialSpec(), label = "voiceCorner")
    val outline by animateColorAsState(if (selected) scheme.tertiary else Color.Transparent, motion.defaultEffectsSpec(), label = "voiceOutline")
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val kind = stringResource(if (voice is DubSpeaker.Piper) R.string.dub_kind_piper else R.string.dub_kind_kokoro)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(CARD_WIDTH)
            .springPress(interaction, pressedScale = 0.94f)
            .clip(shape)
            .background(container)
            .border(2.dp, outline, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect()
            }
            .semantics { this.selected = selected }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = if (playing) MaterialShapes.SoftBurst else MaterialShapes.Sunny,
                morphed = selected || playing,
                color = if (selected) scheme.tertiary else scheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    when {
                        playing -> Icons.Rounded.GraphicEq
                        voice.female == true -> Icons.Rounded.Female
                        voice.female == false -> Icons.Rounded.Male
                        else -> Icons.Rounded.Bolt
                    },
                    contentDescription = null,
                    tint = if (selected) scheme.onTertiary else scheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onPlay()
                },
                enabled = enabled,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(40.dp),
            ) {
                when {
                    loading -> LoadingIndicator(modifier = Modifier.size(24.dp))
                    playing -> Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.dub_sample_stop))
                    else -> Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.dub_sample_of, voice.label))
                }
            }
        }
        Column {
            Text(voice.label, style = MaterialTheme.typography.titleSmallEmphasized, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (voice.licence.needsCare) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Rounded.Gavel, contentDescription = stringResource(R.string.dub_licence_caution), tint = content, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** A line drawn behind a row of nodes, from the first one's middle to the last one's, at [y]. */
@Composable
internal fun NodeRail(color: Color, nodes: Int, y: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (nodes < 2) return@Canvas
        val slot = size.width / nodes
        drawLine(
            color,
            Offset(slot / 2f, y.toPx()),
            Offset(size.width - slot / 2f, y.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private val TOKEN_WIDTH = 72.dp
private val CARD_WIDTH = 148.dp
