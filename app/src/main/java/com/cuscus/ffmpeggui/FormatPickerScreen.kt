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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress
import com.cuscus.ffmpeggui.onboarding.rememberReducedMotion
import kotlinx.coroutines.delay

/*
 * Where the output format is chosen. The file comes first (its name and what
 * is known of it), then the format that fits it best as a large card, then
 * every format by category: each category as a banner with its own film
 * (FormatShowcase), shape and colours (FormatVisuals), each format as its
 * extension in that shape with what it is good for. Every piece pops in once,
 * one after the other.
 */

/** The fewest and most formats in a row, and the width a tile likes to have. */
private const val MIN_COLUMNS = 3
private const val MAX_COLUMNS = 8
private val TILE_WIDTH = 124.dp

/** The widest the picker gets (a monitor): centred beyond it. */
private val PICKER_MAX_WIDTH = 1120.dp

/** How tall a category's film is. */
private val BANNER_FILM_HEIGHT = 120.dp

/** How long a category's badge rests before breathing into a circle, or back. */
private const val BADGE_BREATH_MS = 2_600L

/** The steps between the pieces popping in, and the most steps any piece waits. */
private const val ENTER_STAGGER_MS = 60L
private const val MAX_ENTER_STEPS = 10

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
    // What has already popped in: scrolled back into view, it is simply there.
    val entrance = remember { Entrance(android.os.SystemClock.uptimeMillis()) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Where each piece stands in the order of popping in.
        var order = 0
        item(span = { GridItemSpan(columns) }, key = "header") {
            Header(fileName = fileName, mediaInfo = mediaInfo, onBack = onBack)
        }
        if (suggestion != null) {
            val at = order++
            item(span = { GridItemSpan(columns) }, key = "suggested") {
                SuggestedCard(
                    format = suggestion,
                    onClick = { onFormatSelected(suggestion) },
                    modifier = Modifier.enterOnce("suggested", at, entrance),
                )
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
            val bannerAt = order++
            item(span = { GridItemSpan(columns) }, key = category) {
                CategoryBanner(
                    category = category,
                    count = formats.size,
                    modifier = Modifier.enterOnce(category, bannerAt, entrance),
                )
            }
            // The tiles follow their banner, one step apart within the row.
            val tilesAt = order++
            formats.forEachIndexed { index, format ->
                item(key = format) {
                    val enter = Modifier.enterOnce(format, tilesAt + index % columns, entrance)
                    if (format.isTranscript) {
                        // Greyed out until the speech model is set up; then it is a tap away.
                        FormatTile(
                            format = format,
                            enabled = transcriptReady,
                            note = if (transcriptReady) null else stringResource(R.string.format_txt_needs_model),
                            onClick = { if (transcriptReady) onFormatSelected(format) else onOpenSmartFeatures() },
                            modifier = enter,
                        )
                    } else {
                        FormatTile(format = format, onClick = { onFormatSelected(format) }, modifier = enter)
                    }
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
private fun SuggestedCard(format: OutputFormat, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
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

/**
 * A category as a banner: its film playing (FormatShowcase), its badge in its
 * own shape breathing into a circle and back, its name, what it holds and how
 * many formats it has. The tiles of its formats follow under it.
 */
@Composable
private fun CategoryBanner(category: FormatCategory, count: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = categoryColors(category)
    val reduced = rememberReducedMotion()
    var breathe by remember { mutableStateOf(false) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(BADGE_BREATH_MS)
                breathe = !breathe
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BANNER_FILM_HEIGHT)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            CategoryShowcase(category, modifier = Modifier.fillMaxSize())
            MorphingContainer(
                from = categoryShape(category),
                to = MaterialShapes.Circle,
                morphed = breathe,
                color = colors.container,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(44.dp),
            ) {
                Icon(categoryIcon(category), contentDescription = null, tint = colors.content, modifier = Modifier.size(22.dp))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.labelRes),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(categoryHintRes(category)),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.format_category_count, count, count),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = colors.content,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.container)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** When the screen opened, and the keys of what has popped in since. */
private class Entrance(val startMs: Long) {
    val shown = mutableSetOf<Any>()
}

/**
 * Pops in (fades, grows from a little smaller and rises, on the theme's slow
 * spatial spring) the first time the item with [key] is shown, [order] steps
 * after the top of the screen. Shown again after a scroll, it is simply there;
 * with the system's animations off, always.
 */
@Composable
private fun Modifier.enterOnce(key: Any, order: Int, entrance: Entrance): Modifier {
    val reduced = rememberReducedMotion()
    val first = remember { !reduced && entrance.shown.add(key) }
    val progress = remember { Animatable(if (first) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    if (first) {
        LaunchedEffect(Unit) {
            // Its turn counts from the screen's opening: what a scroll brings in later comes at once.
            val elapsed = android.os.SystemClock.uptimeMillis() - entrance.startMs
            delay((order.coerceAtMost(MAX_ENTER_STEPS) * ENTER_STAGGER_MS - elapsed).coerceAtLeast(0L))
            progress.animateTo(1f, spec)
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val scale = 0.86f + 0.14f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * 36.dp.toPx()
    }
}

/**
 * One output format: its badge (the extension in its category's shape, which
 * morphs into a circle under the finger) and what it is good for, on a very
 * round tile that squishes.
 */
@Composable
private fun FormatTile(
    format: OutputFormat,
    onClick: () -> Unit,
    enabled: Boolean = true,
    note: String? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
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
