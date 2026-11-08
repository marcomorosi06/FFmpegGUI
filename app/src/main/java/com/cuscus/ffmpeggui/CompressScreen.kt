@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.Compression
import com.cuscus.ffmpeggui.core.model.CompressionLevel
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * The compressor: a file made smaller in one go, without the editor. A video
 * becomes HEVC (x265) at the quality chosen, or exactly fills a size in two
 * passes; a JPG keeps the best quality that fits; a PNG is deflated hardest,
 * or brought to 256 colours; a sound keeps its format at a lower bitrate.
 * Smaller still: a lower resolution, and without where and with what it was
 * made. Then the usual export runs (progress, notification, result).
 */

/** What kind of file the compressor works on. */
internal enum class CompressKind { VIDEO, PICTURE, SOUND }

/** How the file is squeezed: a level, or a size to fit. */
private enum class CompressMode { SAME, BALANCED, SMALLEST, SIZE }

/** What the compressor makes of [info] ([fileName]): its kind and the format it is written in. */
internal fun compressTarget(fileName: String, info: MediaInfo): Pair<CompressKind, OutputFormat> {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        info.isStill -> CompressKind.PICTURE to when (ext) {
            // An SVG opens drawn as a picture: it stays a PNG, transparency and all.
            "png", "svg" -> OutputFormat.PNG
            "webp" -> OutputFormat.WEBP
            else -> OutputFormat.JPG
        }
        info.isVideo -> CompressKind.VIDEO to OutputFormat.MP4
        else -> CompressKind.SOUND to when (ext) {
            "mp3" -> OutputFormat.MP3
            "ogg", "oga" -> OutputFormat.OGG
            "opus" -> OutputFormat.OPUS
            // Lossless or anything else: AAC, which every player plays.
            else -> OutputFormat.M4A
        }
    }
}

/** Sizes offered to fit (bytes): what messengers and mail take, kept when smaller than the file. */
private val FIXED_SIZES = listOf(8L, 10L, 16L, 25L, 50L).map { it * 1_000_000L }

/**
 * The compressor's screen. [mediaInfo] is the picked file's ([fileName]), null
 * until one is picked and looked at; [onStart] gets the format to write and
 * what to change of the settings for this export.
 */
@Composable
internal fun CompressScreen(
    fileName: String,
    mediaInfo: MediaInfo?,
    /** The file was picked before the screen opened (from the home): it counts from the start. */
    pickedAlready: Boolean = false,
    onPick: (Uri) -> Unit,
    onStart: (OutputFormat, (ConversionSettings) -> ConversionSettings) -> Unit,
    onBack: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    // Only a file picked here counts (an editor's project may still be open behind).
    var picked by rememberSaveable { mutableStateOf(pickedAlready) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            picked = true
            onPick(uri)
        }
    }
    BackHandler(onBack = onBack)
    val info = mediaInfo?.takeIf { picked }
    val target = info?.let { compressTarget(fileName, it) }
    val kind = target?.first
    val format = target?.second

    var mode by rememberSaveable { mutableStateOf(CompressMode.SAME) }
    var sizeFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var fixedSize by rememberSaveable { mutableStateOf<Long?>(null) }
    var resolution by rememberSaveable { mutableStateOf(Resolution.ORIGINAL) }
    var maxSide by rememberSaveable { mutableStateOf(0) }
    var palette by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(true) }

    val original = info?.fileSizeBytes?.takeIf { it > 0L } ?: 0L
    // A size can be fitted where the bitrate or the quality decides it.
    val length = info?.durationMs ?: 0L
    val canFit = original > 0L && ((kind == CompressKind.VIDEO || kind == CompressKind.SOUND) && length > 0L || format == OutputFormat.JPG)
    val targetBytes = when {
        mode != CompressMode.SIZE || !canFit -> 0L
        fixedSize != null -> fixedSize!!
        else -> (original * sizeFraction).roundToLong()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.compress_title), style = MaterialTheme.typography.headlineMediumEmphasized)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            FileCard(
                picked = picked,
                fileName = fileName,
                info = info,
                kind = kind,
                onPick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) },
            )
            AnimatedVisibility(
                visible = info != null && format != null,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                if (info == null || format == null || kind == null) return@AnimatedVisibility
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // How hard: the same look, balanced, the smallest; or a size to fit.
                    PanelCard {
                        Text(stringResource(R.string.compress_how), style = MaterialTheme.typography.titleMediumEmphasized)
                        if (format == OutputFormat.PNG) {
                            OptionTileRow(
                                options = listOf(
                                    TileOption(false, stringResource(R.string.compress_png_lossless), Icons.Rounded.HighQuality),
                                    TileOption(true, stringResource(R.string.compress_png_palette), Icons.Rounded.Palette),
                                ),
                                selected = palette,
                                onSelect = { palette = it },
                            )
                            PanelHint(stringResource(if (palette) R.string.compress_png_palette_hint else R.string.compress_png_lossless_hint))
                        } else {
                            OptionTileRow(
                                options = listOfNotNull(
                                    TileOption(CompressMode.SAME, stringResource(R.string.compress_same), Icons.Rounded.HighQuality),
                                    TileOption(CompressMode.BALANCED, stringResource(R.string.compress_balanced), Icons.Rounded.Tune),
                                    TileOption(CompressMode.SMALLEST, stringResource(R.string.compress_smallest), Icons.Rounded.Compress),
                                    if (canFit) TileOption(CompressMode.SIZE, stringResource(R.string.compress_size), Icons.Rounded.Straighten) else null,
                                ),
                                selected = mode,
                                onSelect = { mode = it },
                            )
                            AnimatedContent(
                                targetState = mode,
                                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                                label = "compressMode",
                            ) { shown ->
                                if (shown == CompressMode.SIZE && canFit) {
                                    SizeChooser(
                                        original = original,
                                        fraction = sizeFraction,
                                        fixed = fixedSize,
                                        targetBytes = targetBytes,
                                        onFraction = {
                                            fixedSize = null
                                            sizeFraction = it
                                        },
                                        onFixed = { fixedSize = it },
                                    )
                                } else {
                                    PanelHint(
                                        stringResource(
                                            when (kind) {
                                                CompressKind.VIDEO -> when (shown) {
                                                    CompressMode.SAME -> R.string.compress_video_same_hint
                                                    CompressMode.BALANCED -> R.string.compress_video_balanced_hint
                                                    else -> R.string.compress_video_smallest_hint
                                                }
                                                CompressKind.PICTURE -> R.string.compress_picture_hint
                                                CompressKind.SOUND -> R.string.compress_sound_hint
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    // Smaller still: fewer pixels.
                    if (kind == CompressKind.VIDEO) {
                        val heights = Resolution.entries.filter { it == Resolution.ORIGINAL || (it.height in 480 until info.height) }
                        if (heights.size > 1) {
                            PanelCard {
                                Text(stringResource(R.string.compress_resolution), style = MaterialTheme.typography.titleMediumEmphasized)
                                val originalLabel = stringResource(R.string.compress_original)
                                ConnectedChoiceGroup(
                                    options = heights.sortedByDescending { if (it == Resolution.ORIGINAL) Int.MAX_VALUE else it.height },
                                    selected = resolution,
                                    label = { if (it == Resolution.ORIGINAL) originalLabel else "${it.height}p" },
                                    onSelect = { resolution = it },
                                )
                            }
                        }
                    }
                    if (kind == CompressKind.PICTURE) {
                        val longer = maxOf(info.width, info.height)
                        val sides = listOf(0) + listOf(4096, 2048, 1080).filter { it < longer }
                        if (sides.size > 1) {
                            PanelCard {
                                Text(stringResource(R.string.compress_picture_size), style = MaterialTheme.typography.titleMediumEmphasized)
                                val originalLabel = stringResource(R.string.compress_original)
                                ConnectedChoiceGroup(
                                    options = sides,
                                    selected = maxSide,
                                    label = { if (it == 0) originalLabel else "$it px" },
                                    onSelect = { maxSide = it },
                                )
                            }
                        }
                    }
                    SwitchCard(
                        icon = Icons.Rounded.LocationOff,
                        title = stringResource(R.string.compress_private),
                        subtitle = stringResource(R.string.compress_private_hint),
                        checked = privacy,
                        onCheckedChange = { privacy = it },
                    )
                    val formatLabel = stringResource(format.labelRes)
                    PanelHint(
                        stringResource(
                            if (kind == CompressKind.VIDEO) R.string.compress_video_format_hint else R.string.compress_format_hint,
                            formatLabel,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        // The one action, always in reach.
        AnimatedVisibility(
            visible = info != null && format != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.9f) + fadeIn(),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Button(
                onClick = {
                    val chosen = format ?: return@Button
                    val shown = info ?: return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val level = when (mode) {
                        CompressMode.BALANCED -> CompressionLevel.BALANCED
                        CompressMode.SMALLEST -> CompressionLevel.SMALLEST
                        else -> CompressionLevel.SAME_QUALITY
                    }
                    val compression = Compression(level = level, targetBytes = targetBytes, pngPalette = palette && chosen == OutputFormat.PNG)
                    onStart(chosen) { settings ->
                        var s = settings.copy(compression = compression, useStreamCopy = false)
                        s = when (kind) {
                            CompressKind.VIDEO -> s.copy(resolution = resolution)
                            CompressKind.PICTURE -> s.copy(
                                imageEdit = s.imageEdit.copy(
                                    output = s.imageEdit.output.copy(
                                        // Fitting a size starts from the best and comes down.
                                        quality = if (compression.hasTarget) 0.95f else level.jpegQuality,
                                        maxSide = maxSide,
                                    ),
                                ),
                            )
                            CompressKind.SOUND, null -> s.copy(
                                audioEncoding = s.audioEncoding.copy(bitrateKbps = Compression.audioKbps(chosen, compression, shown.durationMs)),
                            )
                        }
                        if (privacy) s = s.copy(tags = s.tags.withoutSensitive())
                        s
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.compress_start), style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
    }
}

/** The picked file (its kind, size and shape), or the big button that picks one. */
@Composable
private fun FileCard(picked: Boolean, fileName: String, info: MediaInfo?, kind: CompressKind?, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interaction, pressedScale = 0.96f)
            .clip(RoundedCornerShape(32.dp))
            .background(if (info != null) scheme.secondaryContainer else scheme.primaryContainer)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onPick)
            .padding(18.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie9Sided,
            to = MaterialShapes.Circle,
            morphed = pressed || info == null,
            color = if (info != null) scheme.secondary else scheme.primary,
            modifier = Modifier.size(64.dp),
        ) {
            if (picked && info == null) {
                LoadingIndicator(color = scheme.onPrimary, modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    imageVector = when (kind) {
                        CompressKind.VIDEO -> Icons.Rounded.Movie
                        CompressKind.PICTURE -> Icons.Rounded.Photo
                        CompressKind.SOUND -> Icons.Rounded.Audiotrack
                        null -> Icons.Rounded.FileOpen
                    },
                    contentDescription = null,
                    tint = if (info != null) scheme.onSecondary else scheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (info == null) {
                Text(stringResource(R.string.compress_pick), style = MaterialTheme.typography.titleLargeEmphasized, color = scheme.onPrimaryContainer)
                Text(stringResource(R.string.compress_pick_hint), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
            } else {
                Text(fileName, style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    info.fileSizeFormatted.takeIf { it.isNotBlank() },
                    "${info.width} \u00D7 ${info.height}".takeIf { info.width > 0 && info.height > 0 },
                    info.durationFormatted.takeIf { !info.isStill && info.durationMs > 0L },
                ).joinToString(" \u00B7 ")
                Text(details, style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer.copy(alpha = 0.8f))
                Text(stringResource(R.string.compress_pick_other), style = MaterialTheme.typography.labelMedium, color = scheme.secondary)
            }
        }
    }
}

/** The size to fit: a share of the file on a slider, or a size messengers and mail take. */
@Composable
private fun SizeChooser(
    original: Long,
    fraction: Float,
    fixed: Long?,
    targetBytes: Long,
    onFraction: (Float) -> Unit,
    onFixed: (Long) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedContent(
            targetState = MediaInfo.formatFileSize(targetBytes),
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "compressTarget",
        ) { text ->
            Text(
                text = stringResource(R.string.compress_at_most, text),
                style = MaterialTheme.typography.headlineSmallEmphasized.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        CompactSlider(
            label = stringResource(R.string.compress_size),
            valueText = stringResource(R.string.value_percent, (fraction * 100).roundToInt()),
            value = fraction,
            onValueChange = { onFraction((it * 20).roundToInt() / 20f) },
            valueRange = 0.05f..0.95f,
            defaultValue = 0.5f,
        )
        val sizes = FIXED_SIZES.filter { it < original }
        if (sizes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                sizes.forEach { size ->
                    val chosen = fixed == size
                    FilterChip(
                        selected = chosen,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onFixed(size)
                        },
                        label = { Text(MediaInfo.formatFileSize(size)) },
                        leadingIcon = if (chosen) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
        PanelHint(stringResource(R.string.compress_size_hint))
    }
}
