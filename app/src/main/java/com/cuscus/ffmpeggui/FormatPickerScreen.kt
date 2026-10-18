@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress

/*
 * Where the output format is chosen. The file comes first (its name and what
 * is known of it), then the format that fits it best as a large card, then
 * every format by category: each category with its own shape and colours (see
 * FormatVisuals), each format as its extension in that shape with what it is
 * good for.
 */

/** The fewest and most formats in a row, and the width a tile likes to have. */
private const val MIN_COLUMNS = 3
private const val MAX_COLUMNS = 8
private val TILE_WIDTH = 124.dp

/** The widest the picker gets (a monitor): centred beyond it. */
private val PICKER_MAX_WIDTH = 1120.dp

@Composable
fun FormatPickerScreen(
    fileName: String,
    mediaInfo: MediaInfo?,
    onFormatSelected: (OutputFormat) -> Unit,
    onBack: () -> Unit,
    /** The speech model is on the phone: a transcript (.txt) can be made. */
    transcriptReady: Boolean = false,
    /** Where the speech model is set up (Smart features). */
    onOpenSmartFeatures: () -> Unit = {},
) {
    val groupedFormats = OutputFormat.entries.groupBy { it.category }
    val suggestion = suggestedFormat(mediaInfo)
    // More formats in a row as the window widens (a phone on its side, a tablet), kept
    // together in the middle of a monitor.
    val window = LocalWindowLayout.current
    val side = window.sidePadding(PICKER_MAX_WIDTH)
    val columns = ((window.width - side * 2 + 12.dp) / (TILE_WIDTH + 12.dp)).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        item(span = { GridItemSpan(columns) }) {
            Header(fileName = fileName, mediaInfo = mediaInfo, onBack = onBack)
        }
        if (suggestion != null) {
            item(span = { GridItemSpan(columns) }) {
                SuggestedCard(format = suggestion, onClick = { onFormatSelected(suggestion) })
            }
        }
        FormatCategory.entries.forEach { category ->
            // A picture needs a picture and a sound file needs sound: a file with only
            // sound cannot become a picture, a photo (or a silent video) cannot become sound.
            if (category == FormatCategory.IMAGE && mediaInfo != null && !mediaInfo.isVideo) return@forEach
            if (category == FormatCategory.AUDIO && mediaInfo != null && !mediaInfo.hasAudio) return@forEach
            // A transcript needs something said: a file with sound.
            if (category == FormatCategory.TEXT && mediaInfo != null && !mediaInfo.hasAudio) return@forEach
            val formats = groupedFormats[category] ?: return@forEach
            item(span = { GridItemSpan(columns) }) {
                CategoryHeader(category)
            }
            items(formats) { format ->
                if (format.isTranscript) {
                    // Greyed out until the speech model is set up; then it is a tap away.
                    FormatTile(
                        format = format,
                        enabled = transcriptReady,
                        note = if (transcriptReady) null else stringResource(R.string.format_txt_needs_model),
                        onClick = { if (transcriptReady) onFormatSelected(format) else onOpenSmartFeatures() },
                    )
                } else {
                    FormatTile(format = format, onClick = { onFormatSelected(format) })
                }
            }
        }
    }
}

/** The format most files of this kind want: MP4 for a video, MP3 for sound alone. */
private fun suggestedFormat(info: MediaInfo?): OutputFormat? = when {
    info == null -> null
    info.isVideo && info.durationMs > 0 -> OutputFormat.MP4
    info.hasAudio && !info.isVideo -> OutputFormat.MP3
    else -> null
}

/** Back, the title set large, the file and what is known of it. */
@Composable
private fun Header(fileName: String, mediaInfo: MediaInfo?, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        FilledTonalIconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = stringResource(R.string.action_back), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.format_picker_title),
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            FactPill(icon = Icons.Rounded.VideoFile, text = fileName, emphasized = true)
            if (mediaInfo != null) {
                if (mediaInfo.durationMs > 0) {
                    FactPill(Icons.Rounded.Schedule, mediaInfo.durationFormatted, label = stringResource(R.string.media_info_duration))
                }
                if (mediaInfo.isVideo && mediaInfo.width > 0) {
                    FactPill(
                        Icons.Rounded.AspectRatio,
                        stringResource(R.string.media_info_resolution_value, mediaInfo.width, mediaInfo.height),
                        label = stringResource(R.string.media_info_resolution),
                    )
                }
                if (mediaInfo.fileSizeBytes > 0) {
                    FactPill(Icons.Rounded.Description, mediaInfo.fileSizeFormatted, label = stringResource(R.string.media_info_size))
                }
            }
        }
    }
}

/** One fact about the file in a capsule (its icon read out as [label]); the file's name stands out. */
@Composable
private fun FactPill(icon: ImageVector, text: String, emphasized: Boolean = false, label: String? = null) {
    val container = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(container)
            .padding(start = 12.dp, end = 16.dp),
    ) {
        Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = if (emphasized) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (emphasized) Modifier.widthIn(max = 220.dp) else Modifier,
        )
    }
}

/** The best fit for the file, large: its badge, its name and what it is good for. */
@Composable
private fun SuggestedCard(format: OutputFormat, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(20.dp),
    ) {
        FormatBadge(format = format, size = 88.dp, morphed = pressed)
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.format_picker_suggested),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(format.labelRes),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(formatDescriptionRes(format)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A category: its icon in its shape, its name and what it holds. */
@Composable
private fun CategoryHeader(category: FormatCategory) {
    val colors = categoryColors(category)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp, start = 4.dp),
    ) {
        MorphingContainer(
            from = categoryShape(category),
            to = categoryShape(category),
            morphed = false,
            color = colors.container,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(categoryIcon(category), contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(categoryHintRes(category)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One output format: its badge (the extension in its category's shape, which
 * morphs into a circle under the finger) and what it is good for, on a very
 * round tile that squishes.
 */
@Composable
private fun FormatTile(format: OutputFormat, onClick: () -> Unit, enabled: Boolean = true, note: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.92f)
            .fillMaxWidth()
            // One height for every tile, so that a row of them lines up.
            .height(148.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
    ) {
        FormatBadge(format = format, size = 60.dp, morphed = pressed)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = note ?: stringResource(formatDescriptionRes(format)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
