@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.conversion.CutoutProblem
import com.cuscus.ffmpeggui.conversion.CutoutStatus
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.preview.ColorKeying
import com.cuscus.ffmpeggui.preview.FrameGrabber
import com.cuscus.ffmpeggui.preview.applyColorMatrix
import com.cuscus.ffmpeggui.preview.fitted
import com.cuscus.ffmpeggui.preview.smallPicture
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The colour key panel (green screen), the same in every editor: on a clip or
 * the whole video, on a picture or video laid over it, on a photo and on its
 * stickers.
 *
 *  - Switched on, the key guesses the colour by itself from the sample (the
 *    commonest colour of its border, see ColorKeying.suggestedKey).
 *  - The sample shows the result (on a checkerboard where it is transparent);
 *    touching or dragging on it picks the colour under the finger, with a loupe
 *    that magnifies the pixels there and rings them in the colour taken; the
 *    compare button shows the original while it is held.
 *  - The colour to cut is always in sight, with its code and a Detect button;
 *    quick colours and Detect set the mode that suits them (by hue for a
 *    screen, exact for white and black); a picked colour keeps the mode.
 *  - What is cut shows a fill: transparent, or a colour, with an opacity where
 *    the result can hold transparency.
 */

/** What a key changes; the key groups quick changes (a slider) into one undo step. */
internal typealias KeyChange = (String?, (ColorKey?) -> ColorKey?) -> Unit

/** A colour offered at once, with its name. */
private class KeyPreset(val color: Int, val label: Int)

private val KEY_PRESETS = listOf(
    KeyPreset(ColorKey.GREEN_SCREEN, R.string.key_preset_green),
    KeyPreset(ColorKey.BLUE_SCREEN, R.string.key_preset_blue),
    KeyPreset(0xFFFFFF, R.string.key_preset_white),
    KeyPreset(0x000000, R.string.key_preset_black),
)

/** Colours a cut can show instead (the first ones of a video). */
internal val KEY_BACKGROUNDS = listOf(0x000000, 0xFFFFFF, 0x00B140, 0x0047BB, 0x202124, 0xF2C94C, 0xE57373)

/** The ways to cut by colour (the subject has a card of its own). */
private val COLOR_MODES = listOf(KeyMode.CHROMA, KeyMode.COLOR)

/**
 * The subject key's part of the key panels: whether the cut-out model is set
 * up, where cutting a picture out is, and the actions. Provided once for the
 * whole app; null where there is no model support at all.
 */
internal class SubjectCut(
    val ready: Boolean,
    /** SlimSAM, to correct a cut-out with brushes; null while it is not set up. */
    val refine: com.cuscus.ffmpeggui.cutout.RefineEngine? = null,
    val status: CutoutStatus,
    /** Cuts the picture at the uri out; the callback gets the mask's path. */
    val onCut: (Uri, (String) -> Unit) -> Unit,
    val onStop: () -> Unit,
    val onOpenSmartFeatures: () -> Unit,
)

internal val LocalSubjectCut = staticCompositionLocalOf<SubjectCut?> { null }

/** Longest side of the sample the result is shown on in the panel. */
private const val SAMPLE_PX = 360

/** Undo step of the colour picked by a touch or a drag. */
private const val PICK_STEP = "pick"

/** Side, in pixels of the sample, of what the loupe magnifies. */
private const val LOUPE_PIXELS = 11

/** This key cutting [color], in the mode that suits it: by hue for a green or blue screen, else exact. */
private fun ColorKey.cutting(color: Int): ColorKey {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    val screen = maxOf(r, g, b) - minOf(r, g, b) >= 60 && (g > r && g >= b || b > r && b > g)
    val mode = if (screen) KeyMode.CHROMA else KeyMode.COLOR
    return copy(color = color, mode = mode, similarity = if (mode == this.mode) similarity else ColorKey.defaultSimilarity(mode))
}

/** The key [sample] most likely wants, from [base] (see ColorKeying.suggestedKey); [base] without a sample. */
private fun suggested(sample: Bitmap?, base: ColorKey): ColorKey {
    val picture = sample ?: return base
    val pixels = IntArray(picture.width * picture.height)
    picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
    return ColorKeying.suggestedKey(pixels, picture.width, picture.height, base) ?: base
}

/**
 * The colour key of something.
 *
 * @param sample          the picture as it looks before the key (null while it is read).
 * @param backgrounds     what the cut can show instead, null meaning transparency; empty
 *                        when there is no choice (an overlay always lets what is below show).
 * @param newBackground   the fill of a key switched on.
 * @param onRefreshSample takes the sample again (the frame under the playhead), when there is one.
 * @param fillOpacity     the fill can be see-through (the result holds transparency).
 * @param fillHint        says what the output does with the fill.
 */
@Composable
internal fun ColorKeyContent(
    key: ColorKey?,
    onKey: KeyChange,
    sample: Bitmap?,
    backgrounds: List<Int?>,
    newBackground: Int?,
    onRefreshSample: (() -> Unit)? = null,
    enabled: Boolean = true,
    fillOpacity: Boolean = false,
    fillHint: String? = null,
    subjectUri: Uri? = null,
) {
    val motion = MaterialTheme.motionScheme
    val small = remember(sample) { sample?.let { fitted(it, SAMPLE_PX) } }
    val currentSmall by rememberUpdatedState(small)
    // The cut-out model, where there is one and a still picture to look at.
    val subjectCut = LocalSubjectCut.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SwitchCard(
            icon = Icons.Rounded.Colorize,
            title = stringResource(R.string.key_title),
            subtitle = stringResource(R.string.key_subtitle),
            checked = key != null,
            onCheckedChange = { on ->
                // Switched on, it starts from the colour the sample's border suggests.
                onKey(null) { if (on) suggested(currentSmall, ColorKey(background = newBackground)) else null }
            },
            enabled = enabled,
        )
        if (subjectCut != null && subjectUri != null) {
            SubjectCard(
                key = key,
                onKey = onKey,
                uri = subjectUri,
                cut = subjectCut,
                newBackground = newBackground,
                onColorInstead = { onKey(null) { it?.let { now -> suggested(currentSmall, now.copy(mode = KeyMode.CHROMA, mask = null, similarity = ColorKey.defaultSimilarity(KeyMode.CHROMA))) } } },
                enabled = enabled,
            )
        }
        AnimatedVisibility(
            visible = key != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            // Kept while it closes, so that the panel does not empty before it is gone.
            var shownKey by remember { mutableStateOf(key ?: ColorKey()) }
            if (key != null) shownKey = key
            val current = shownKey
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // A subject key has no colour to pick or tune: only its fill.
                if (current.mode != KeyMode.SUBJECT) {
                PanelCard {
                    KeySample(
                        small = small,
                        key = current,
                        // A picked colour keeps the mode chosen; quick colours and Detect choose it.
                        onPick = { color -> onKey(PICK_STEP) { it?.copy(color = color) } },
                        onRefresh = onRefreshSample,
                        enabled = enabled,
                    )
                    KeyColorRow(
                        key = current,
                        onDetect = { onKey(null) { it?.let { now -> suggested(small, now) } } },
                        enabled = enabled && small != null,
                    )
                    PanelHint(stringResource(R.string.key_pick_hint))
                }
                PanelCard {
                    Text(
                        text = stringResource(R.string.key_presets),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OptionTileRow(
                        options = KEY_PRESETS.map { preset ->
                            TileOption(
                                value = preset.color,
                                label = stringResource(preset.label),
                                preview = { _ -> ColorDot(preset.color, size = 30.dp) },
                            )
                        },
                        selected = current.color,
                        onSelect = { color -> onKey(null) { it?.cutting(color) } },
                        enabled = enabled,
                    )
                    Text(
                        text = stringResource(R.string.key_mode),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ConnectedChoiceGroup(
                        options = COLOR_MODES,
                        selected = current.mode,
                        label = { stringResource(if (it == KeyMode.CHROMA) R.string.key_mode_chroma else R.string.key_mode_color) },
                        onSelect = { mode -> onKey(null) { it?.copy(mode = mode, similarity = ColorKey.defaultSimilarity(mode)) } },
                        enabled = enabled,
                    )
                    PanelHint(stringResource(if (current.mode == KeyMode.CHROMA) R.string.key_mode_chroma_hint else R.string.key_mode_color_hint))
                }
                PanelCard {
                    Text(
                        text = stringResource(R.string.key_precision),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ValueSlider(
                        label = stringResource(R.string.key_similarity),
                        valueText = "${(current.similarity * 100).roundToInt()}%",
                        value = current.similarity,
                        onValueChange = { value -> onKey("similarity") { it?.copy(similarity = value) } },
                        valueRange = 0.01f..if (current.mode == KeyMode.CHROMA) 0.6f else 1f,
                        defaultValue = ColorKey.defaultSimilarity(current.mode),
                        enabled = enabled,
                    )
                    ValueSlider(
                        label = stringResource(R.string.key_blend),
                        valueText = "${(current.blend * 100).roundToInt()}%",
                        value = current.blend,
                        onValueChange = { value -> onKey("blend") { it?.copy(blend = value) } },
                        valueRange = 0f..0.5f,
                        defaultValue = ColorKey.DEFAULT_BLEND,
                        enabled = enabled,
                    )
                    // Spill only means something on a green or blue screen cut by hue.
                    val spillAvailable = current.mode == KeyMode.CHROMA && current.copy(spill = 1f).spillScreen != null
                    AnimatedVisibility(
                        visible = spillAvailable,
                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        Column {
                            ValueSlider(
                                label = stringResource(R.string.key_spill),
                                valueText = "${(current.spill * 100).roundToInt()}%",
                                value = current.spill,
                                onValueChange = { value -> onKey("spill") { it?.copy(spill = value) } },
                                valueRange = 0f..1f,
                                defaultValue = 0f,
                                enabled = enabled,
                            )
                            PanelHint(stringResource(R.string.key_spill_hint))
                        }
                    }
                    if (!spillAvailable) PanelHint(stringResource(R.string.key_spill_unavailable))
                }
                }
                if (backgrounds.isNotEmpty()) {
                    PanelCard {
                        Text(
                            text = stringResource(R.string.key_background),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        KeyFillRow(
                            choices = backgrounds,
                            selected = current.background,
                            opacity = if (fillOpacity) current.backgroundOpacity else 1f,
                            onSelect = { color -> onKey(null) { it?.copy(background = color) } },
                            enabled = enabled,
                        )
                        AnimatedVisibility(
                            visible = fillOpacity && current.background != null,
                            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                        ) {
                            ValueSlider(
                                label = stringResource(R.string.key_fill_opacity),
                                valueText = "${(current.backgroundOpacity * 100).roundToInt()}%",
                                value = current.backgroundOpacity,
                                onValueChange = { value -> onKey("fillOpacity") { it?.copy(backgroundOpacity = value) } },
                                valueRange = 0f..1f,
                                defaultValue = 1f,
                                enabled = enabled,
                            )
                        }
                        if (fillHint != null) PanelHint(fillHint)
                    }
                } else if (fillHint != null) {
                    PanelHint(fillHint, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

/**
 * Cutting the subject out with the model rather than a colour: what it does,
 * the model to set up first, the picture being looked at, what went wrong; once
 * cut, the way back to a colour key. The key then keeps only its fill.
 */
@Composable
private fun SubjectCard(
    key: ColorKey?,
    onKey: KeyChange,
    uri: Uri,
    cut: SubjectCut,
    newBackground: Int?,
    onColorInstead: () -> Unit,
    enabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val name = uri.toString()
    val working = (cut.status as? CutoutStatus.Working)?.uri == name
    val failed = (cut.status as? CutoutStatus.Failed)?.takeIf { it.uri == name }?.problem
    val active = key?.mode == KeyMode.SUBJECT
    val maskPath = key?.mask
    val maskGone = remember(maskPath) { maskPath != null && !File(maskPath).isFile }
    val maskMissing = active && (maskPath == null || maskGone)
    val done = active && !maskMissing
    var refining by remember(maskPath) { mutableStateOf(false) }
    val tone = when {
        failed != null || maskMissing -> ToneColors(scheme.errorContainer, scheme.onErrorContainer)
        done -> ToneColors(scheme.primary, scheme.onPrimary)
        else -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    val cutNow = {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        cut.onCut(uri) { path ->
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onKey(null) { (it ?: ColorKey(background = newBackground)).copy(mode = KeyMode.SUBJECT, mask = path) }
        }
    }
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Sunny,
                morphed = working || done,
                color = tone.container,
                modifier = Modifier.size(48.dp),
            ) {
                if (working) {
                    LoadingIndicator(color = tone.content, modifier = Modifier.size(34.dp))
                } else {
                    Icon(
                        imageVector = when {
                            failed != null || maskMissing -> Icons.Rounded.ErrorOutline
                            done -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.AutoAwesome
                        },
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.key_subject_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(
                    text = when {
                        working -> stringResource(R.string.key_subject_working)
                        failed != null -> stringResource(
                            when (failed) {
                                CutoutProblem.NO_MODEL -> R.string.cutout_problem_model
                                CutoutProblem.NO_MEMORY -> R.string.cutout_problem_memory
                                CutoutProblem.UNREADABLE -> R.string.cutout_problem_unreadable
                                CutoutProblem.FAILED -> R.string.cutout_problem_failed
                            },
                        )
                        maskMissing -> stringResource(R.string.key_subject_missing)
                        done -> stringResource(R.string.key_subject_done)
                        !cut.ready -> stringResource(R.string.key_subject_needs_model)
                        else -> stringResource(R.string.key_subject_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        when {
            working -> {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        cut.onStop()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.smart_stop)) }
            }
            done -> {
                // Corrected with brushes: green on what should stay, red on what should go.
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (cut.refine != null) refining = true else cut.onOpenSmartFeatures()
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(if (cut.refine != null) R.string.refine_open else R.string.refine_setup))
                }
                val engine = cut.refine
                val path = maskPath
                if (refining && engine != null && path != null) {
                    RefineMaskDialog(
                        uri = uri,
                        maskPath = path,
                        engine = engine,
                        onDone = { corrected ->
                            refining = false
                            onKey(null) { it?.copy(mask = corrected) }
                        },
                        onDismiss = { refining = false },
                    )
                }
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onColorInstead()
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Colorize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.key_subject_use_color))
                }
            }
            !cut.ready -> {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        cut.onOpenSmartFeatures()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.key_subject_setup)) }
            }
            else -> {
                Button(
                    onClick = cutNow,
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(if (failed != null || maskMissing) R.string.speech_try_again else R.string.key_subject_cut))
                }
            }
        }
        if (cut.ready || done) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.key_subject_private), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The sample with the key's result (a checkerboard where it is transparent).
 * A touch or a drag picks the colour under the finger (of the picture as it
 * is): a loupe above the finger magnifies the pixels there, ringed in the
 * colour taken, and the place stays marked. The compare button shows the
 * original while it is held.
 */
@Composable
private fun KeySample(small: Bitmap?, key: ColorKey, onPick: (Int) -> Unit, onRefresh: (() -> Unit)?, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    var showOriginal by remember { mutableStateOf(false) }
    var picked by remember(small) { mutableStateOf<Offset?>(null) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    // The result is made again (off the main thread) for every change of the key.
    val result by produceState<Bitmap?>(initialValue = null, small, key) {
        val source = small ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val width = source.width
            val height = source.height
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            ColorKeying.apply(pixels, width, height, key, pixels)
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }
    val currentOnPick by rememberUpdatedState(onPick)
    val original = remember(small) { small?.asImageBitmap() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (small == null || original == null) {
            LoadingIndicator(modifier = Modifier.padding(32.dp).size(48.dp))
        } else {
            val ratio = small.width.toFloat() / small.height.coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .aspectRatio(ratio)
                    .pointerInput(enabled, small) {
                        if (!enabled) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var lastColor = -1
                            fun pickAt(at: Offset) {
                                val x = (at.x / size.width * small.width).toInt().coerceIn(0, small.width - 1)
                                val y = (at.y / size.height * small.height).toInt().coerceIn(0, small.height - 1)
                                finger = Offset(at.x.coerceIn(0f, size.width.toFloat()), at.y.coerceIn(0f, size.height.toFloat()))
                                picked = Offset((x + 0.5f) / small.width, (y + 0.5f) / small.height)
                                val color = small.getPixel(x, y) and 0xFFFFFF
                                if (color != lastColor) {
                                    lastColor = color
                                    currentOnPick(color)
                                }
                            }
                            down.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pickAt(down.position)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.positionChange() != Offset.Zero) {
                                    change.consume()
                                    pickAt(change.position)
                                }
                            }
                            finger = null
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
            ) {
                Checkerboard(modifier = Modifier.fillMaxSize())
                val shown = if (showOriginal) small else result ?: small
                val image = remember(shown) { shown.asImageBitmap() }
                Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                val ring = MaterialTheme.colorScheme.primary
                val keyColor = Color(0xFF000000L or key.color.toLong())
                val at = finger
                val mark = picked
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (at != null) {
                        drawLoupe(original, at, keyColor)
                    } else if (mark != null) {
                        val center = Offset(mark.x * size.width, mark.y * size.height)
                        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(width = 4.dp.toPx()))
                        drawCircle(ring, radius = 11.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
            // Over the picture's corner: take the frame again, and compare with the original.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                if (onRefresh != null) {
                    SampleButton(
                        icon = Icons.Rounded.Refresh,
                        description = stringResource(R.string.key_refresh_sample),
                        enabled = enabled,
                        modifier = Modifier.clickable(enabled = enabled, role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRefresh()
                        },
                    )
                }
                SampleButton(
                    icon = Icons.Rounded.Compare,
                    description = stringResource(R.string.key_compare),
                    enabled = true,
                    highlighted = showOriginal,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                showOriginal = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                tryAwaitRelease()
                                showOriginal = false
                            },
                        )
                    },
                )
            }
        }
    }
}

/** A round button laid over the sample, readable on any picture. */
@Composable
private fun SampleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val container = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container)
            .then(modifier)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) content else content.copy(alpha = 0.38f), modifier = Modifier.size(20.dp))
    }
}

/**
 * The loupe of the picker: a circle above [at] (below it near the top) showing
 * the pixels of [picture] around that point magnified, sharp, with the one
 * taken framed in the middle, and ringed in [color], the colour taken.
 */
private fun DrawScope.drawLoupe(picture: ImageBitmap, at: Offset, color: Color) {
    val radius = 46.dp.toPx()
    val gap = 34.dp.toPx()
    var center = Offset(at.x, at.y - radius - gap)
    if (center.y - radius < 0f) center = Offset(at.x, at.y + radius + gap)
    center = Offset(center.x.coerceIn(radius, (size.width - radius).coerceAtLeast(radius)), center.y)
    // The pixels around the one under the finger, kept inside the picture.
    val px = (at.x / size.width * picture.width).toInt().coerceIn(0, picture.width - 1)
    val py = (at.y / size.height * picture.height).toInt().coerceIn(0, picture.height - 1)
    val span = minOf(LOUPE_PIXELS, picture.width, picture.height)
    val left = (px - span / 2).coerceIn(0, picture.width - span)
    val top = (py - span / 2).coerceIn(0, picture.height - span)
    val diameter = (radius * 2f).roundToInt()
    val origin = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt())
    val circle = Path().apply { addOval(Rect(center, radius)) }
    drawCircle(Color.Black.copy(alpha = 0.25f), radius = radius + 4.dp.toPx(), center = center + Offset(0f, 2.dp.toPx()))
    clipPath(circle) {
        drawRect(Color(0xFFF2F2F2), topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2f, radius * 2f))
        drawImage(
            image = picture,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(span, span),
            dstOffset = origin,
            dstSize = IntSize(diameter, diameter),
            filterQuality = FilterQuality.None,
        )
        // The pixel taken.
        val cell = radius * 2f / span
        val cellTopLeft = Offset(center.x - radius + (px - left) * cell, center.y - radius + (py - top) * cell)
        drawRect(Color.White, topLeft = cellTopLeft, size = Size(cell, cell), style = Stroke(width = 2.5.dp.toPx()))
        drawRect(Color.Black, topLeft = cellTopLeft - Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(cell + 2.dp.toPx(), cell + 2.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
    }
    drawCircle(color, radius = radius, center = center, style = Stroke(width = 7.dp.toPx()))
    drawCircle(Color.White, radius = radius + 4.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
}

/** The colour being cut, its code and how it is cut, with the button that finds it by itself. */
@Composable
private fun KeyColorRow(key: ColorKey, onDetect: () -> Unit, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val code = "#" + String.format(Locale.US, "%06X", key.color and 0xFFFFFF)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ColorDot(key.color, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.key_color),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = code + "  \u00B7  " + stringResource(if (key.mode == KeyMode.CHROMA) R.string.key_mode_chroma else R.string.key_mode_color),
                style = MaterialTheme.typography.titleSmallEmphasized.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val detect = stringResource(R.string.key_auto_description)
        FilledTonalButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDetect()
            },
            shapes = ButtonDefaults.shapes(),
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = detect },
        ) {
            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.key_auto), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** A round swatch of [color] (0xRRGGBB), outlined so that white shows on a light card. */
@Composable
private fun ColorDot(color: Int, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF000000L or (color and 0xFFFFFF).toLong()))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/**
 * What a cut shows: transparency (a checkerboard) or a colour, the chosen
 * colour drawn at [opacity] over a checkerboard.
 */
@Composable
private fun KeyFillRow(choices: List<Int?>, selected: Int?, opacity: Float, onSelect: (Int?) -> Unit, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val transparentLabel = stringResource(R.string.key_background_transparent)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
    ) {
        choices.forEach { choice ->
            val chosen = choice == selected
            val scale by animateFloatAsState(if (chosen) 1f else 0.86f, motion.fastSpatialSpec(), label = "fillScale")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .border(
                            width = if (chosen) 3.dp else 1.dp,
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable(enabled = enabled, role = Role.RadioButton, onClickLabel = if (choice == null) transparentLabel else null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(choice)
                        },
                ) {
                    Checkerboard(modifier = Modifier.fillMaxSize(), cell = 6.dp)
                    if (choice != null) {
                        val alpha = if (chosen) opacity.coerceIn(0f, 1f) else 1f
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000L or choice.toLong()).copy(alpha = alpha)))
                    }
                    if (chosen) {
                        val light = choice == null || opacity < 0.5f ||
                            (((choice shr 16) and 0xFF) * 299 + ((choice shr 8) and 0xFF) * 587 + (choice and 0xFF) * 114) / 1000 > 140
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = if (light) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (choice == null) {
                    Text(
                        text = transparentLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The usual light and dark squares that stand for transparency. */
@Composable
internal fun Checkerboard(modifier: Modifier = Modifier, cell: Dp = 10.dp) {
    Canvas(modifier = modifier) {
        val side = cell.toPx()
        drawRect(Color(0xFFF2F2F2))
        val dark = Color(0xFFCFCFCF)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else side
            while (x < size.width) {
                // The last squares are cut at the edge, never drawn past it.
                drawRect(dark, topLeft = Offset(x, y), size = Size(minOf(side, size.width - x), minOf(side, size.height - y)))
                x += side * 2
            }
            y += side
            row++
        }
    }
}

// -- Samples ------------------------------------------------------------------------------

/** Longest side of a sample picture taken for the key panel. */
private const val SAMPLE_READ_PX = 720

/**
 * A sample for the key panel: the frame at [positionMs] of the file at [uri] (a
 * still: the picture), with [matrix] (the colours the key comes after). Null
 * when it cannot be read.
 */
internal suspend fun keySample(context: Context, uri: Uri, positionMs: Long, still: Boolean, matrix: FloatArray?): Bitmap? =
    withContext(Dispatchers.IO) {
        val picture = if (still) {
            smallPicture(context, uri, SAMPLE_READ_PX)
        } else {
            FrameGrabber(context, uri).use { it.frameAt(positionMs, SAMPLE_READ_PX) }
        } ?: return@withContext null
        val bitmap = if (picture.isMutable && picture.config == Bitmap.Config.ARGB_8888) picture else picture.copy(Bitmap.Config.ARGB_8888, true)
        if (matrix != null) {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            applyColorMatrix(pixels, width * height, matrix)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        bitmap
    }
