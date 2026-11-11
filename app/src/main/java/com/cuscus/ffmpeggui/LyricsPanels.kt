@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.conversion.SpeechProblem
import com.cuscus.ffmpeggui.conversion.SpeechStatus
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.subtitles.LyricsText
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard

/*
 * The lyrics of the song, among its tags: typed or pasted, or made of what is
 * sung with the speech model the smart subtitles use (the same words, heard
 * once). Plain lines, or synced lines the LRC way that players which know it
 * light up as the song plays. The badge on top morphs to say which kind the
 * text is, and how many lines it has. A page of their own (LyricsSyncDialog)
 * times the lines by hand while the song plays.
 */

/** The lyrics field and what fills it; [speech] null leaves only the field. */
@Composable
internal fun LyricsCard(
    lyrics: String,
    onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit,
    speech: SubtitleEditing?,
    /** The song as the editor plays it: a button opens the page that times the lines by hand. Null: no such page. */
    playback: LyricsPlayback? = null,
) {
    var syncing by rememberSaveable { mutableStateOf(false) }
    if (syncing && playback != null) {
        LyricsSyncDialog(
            lyrics = lyrics,
            playback = playback,
            onDone = { text ->
                syncing = false
                onTags(null) { it.copy(lyrics = text) }
            },
            onDismiss = { syncing = false },
        )
    }
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val synced = LyricsText.isSynced(lyrics)
    val lines = lyrics.lines().count { it.isNotBlank() }
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie7Sided,
                to = MaterialShapes.Flower,
                morphed = synced,
                color = if (synced) scheme.tertiaryContainer else scheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    if (synced) Icons.Rounded.AvTimer else Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = if (synced) scheme.onTertiaryContainer else scheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tag_lyrics), style = MaterialTheme.typography.titleMediumEmphasized)
                AnimatedContent(
                    targetState = Triple(lyrics.isBlank(), synced, lines),
                    transitionSpec = {
                        (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.9f)) togetherWith
                            fadeOut(motion.fastEffectsSpec())
                    },
                    label = "lyricsKind",
                ) { (empty, isSynced, count) ->
                    Text(
                        text = when {
                            empty -> stringResource(R.string.lyrics_none)
                            isSynced -> pluralStringResource(R.plurals.lyrics_synced_lines, count, count)
                            else -> pluralStringResource(R.plurals.lyrics_plain_lines, count, count)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedTextField(
            value = lyrics,
            onValueChange = { text -> onTags("lyrics") { it.copy(lyrics = text) } },
            label = { Text(stringResource(R.string.lyrics_field)) },
            placeholder = { Text(stringResource(R.string.lyrics_placeholder)) },
            trailingIcon = if (lyrics.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onTags(null) { it.copy(lyrics = "") }
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tag_clear, stringResource(R.string.tag_lyrics)))
                    }
                }
            } else {
                null
            },
            // Synced lines keep their times aligned.
            textStyle = if (synced) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            minLines = 4,
            maxLines = 12,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (playback != null) {
            // Each line stamped with when it is sung, while the song plays (issue #16).
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    syncing = true
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.AvTimer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.lyrics_sync_open), maxLines = 1)
            }
        }
        if (speech != null) FromSinging(speech, hasLyrics = lyrics.isNotBlank())
    }
}

/** Fills the lyrics with what is sung: plain or synced, the listening's progress, or setting up the model. */
@Composable
private fun FromSinging(speech: SubtitleEditing, hasLyrics: Boolean) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val working = speech.speech as? SpeechStatus.Working
    val failed = (speech.speech as? SpeechStatus.Failed)?.problem
    AnimatedContent(
        targetState = when {
            working != null -> 0
            !speech.modelReady -> 1
            else -> 2
        },
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        label = "lyricsFromSinging",
    ) { state ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            when (state) {
                0 -> {
                    val shown = working ?: return@Column
                    val fraction by animateFloatAsState(shown.fraction ?: 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "lyricsProgress")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stepText(shown),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            Text(
                                text = shown.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            speech.onStop()
                        }) {
                            Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.smart_stop))
                        }
                    }
                    if (shown.fraction != null) {
                        LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                1 -> {
                    Text(stringResource(R.string.lyrics_needs_model), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            speech.onOpenSmartFeatures()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.key_subject_setup)) }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (hasLyrics) R.string.lyrics_from_singing_replace else R.string.lyrics_from_singing),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    // Two halves of one connected pair: plain lines, or timed ones.
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(false, true).forEach { timed ->
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    speech.onLyrics(timed)
                                },
                                shapes = ButtonDefaults.shapes(
                                    shape = if (timed) {
                                        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                                    } else {
                                        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                                    },
                                    pressedShape = RoundedCornerShape(12.dp),
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(if (timed) Icons.Rounded.AvTimer else Icons.AutoMirrored.Rounded.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(if (timed) R.string.lyrics_synced else R.string.lyrics_plain), maxLines = 1)
                            }
                        }
                    }
                    if (failed != null) {
                        Text(
                            text = stringResource(
                                when (failed) {
                                    SpeechProblem.NO_MODEL -> R.string.speech_problem_model
                                    SpeechProblem.NO_MEMORY -> R.string.speech_problem_memory
                                    SpeechProblem.FAILED -> R.string.speech_problem_failed
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.errorContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
