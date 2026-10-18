@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllOut
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.conversion.CutoutProblem
import com.cuscus.ffmpeggui.conversion.CutoutStatus
import com.cuscus.ffmpeggui.core.model.HdrArea
import com.cuscus.ffmpeggui.core.model.HdrMode
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.cutout.MaskRefiner
import com.cuscus.ffmpeggui.hdr.HdrSources
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** What changes the HDR look; the key groups quick changes (a slider) into one undo step. */
internal typealias HdrChange = (String?, (ImageHdr?) -> ImageHdr?) -> Unit

/**
 * The HDR look of a picture (see ImageHdr): on or off, from the photo's own
 * HDR or worked out of its light, how strong, and where (everywhere, the
 * subject, the background, or where it is painted). The editor shows it as it
 * changes: brighter on a screen that shows HDR, and on any screen the zones it
 * brightens, tinted, while [showZones].
 *
 * @param pictureUri  the still the picture is; null for a frame of a video
 *                    (then only "everywhere": the masks are made on a still).
 * @param hasOriginal the photo came with its own HDR (an Ultra HDR photo).
 * @param subjectMask the mask of the picture's subject key, if it has one: the subject is found already.
 * @param screenHdr   this screen shows HDR.
 */
@Composable
internal fun ImageHdrPanel(
    hdr: ImageHdr?,
    onHdr: HdrChange,
    format: OutputFormat,
    pictureUri: Uri?,
    hasOriginal: Boolean,
    subjectMask: String?,
    screenHdr: Boolean,
    /** How much brighter than white the screen can show now (see rememberHdrHeadroom); null when it does not say. */
    headroom: Float?,
    showZones: Boolean,
    onShowZones: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SwitchCard(
            icon = Icons.Rounded.HdrOn,
            title = stringResource(R.string.hdr_title),
            subtitle = stringResource(R.string.hdr_subtitle),
            checked = hdr != null,
            onCheckedChange = { on ->
                onHdr(null) { if (on) ImageHdr(mode = if (hasOriginal) HdrMode.ORIGINAL else HdrMode.GENERATED) else null }
            },
            enabled = enabled,
        )
        AnimatedVisibility(
            visible = hdr != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            val current = hdr ?: ImageHdr()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Only a JPG holds it.
                if (format != OutputFormat.JPG) {
                    PanelCard {
                        Text(
                            stringResource(R.string.hdr_jpg_only, stringResource(format.labelRes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                PanelHint(stringResource(if (screenHdr) R.string.hdr_screen_shows else R.string.hdr_screen_sdr))
                if (headroom != null) {
                    // What the screen gives now: at 1.00 nothing shows brighter, whatever the picture.
                    val none = headroom < NO_HEADROOM
                    Text(
                        text = stringResource(
                            if (none) R.string.hdr_headroom_none else R.string.hdr_headroom,
                            String.format(java.util.Locale.ROOT, "%.2f", headroom),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (none) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                // Where the gain map comes from, when the photo has its own (or the look says so).
                if (hasOriginal || current.mode == HdrMode.ORIGINAL) {
                    ConnectedChoiceGroup(
                        options = listOf(HdrMode.ORIGINAL, HdrMode.GENERATED),
                        selected = current.mode,
                        label = { stringResource(if (it == HdrMode.ORIGINAL) R.string.hdr_mode_original else R.string.hdr_mode_generated) },
                        onSelect = { mode -> onHdr(null) { it?.copy(mode = mode, intensity = ImageHdr.DEFAULT_INTENSITY) } },
                        enabled = enabled,
                    )
                }
                val original = current.mode == HdrMode.ORIGINAL
                ValueSlider(
                    label = stringResource(R.string.hdr_intensity),
                    // 100% in the middle: the photo as shot, or the generated look's full peak.
                    valueText = stringResource(R.string.value_percent, (current.intensity * 200).roundToInt()),
                    value = current.intensity,
                    onValueChange = { v -> onHdr("intensity") { it?.copy(intensity = v) } },
                    valueRange = 0f..1f,
                    defaultValue = ImageHdr.DEFAULT_INTENSITY,
                    enabled = enabled,
                )
                PanelHint(stringResource(if (original) R.string.hdr_mode_original_hint else R.string.hdr_mode_generated_hint))

                // Where it goes.
                Text(stringResource(R.string.hdr_where), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
                val areas = buildList {
                    add(TileOption(HdrArea.EVERYWHERE, stringResource(R.string.hdr_area_everywhere), Icons.Rounded.AllOut))
                    if (pictureUri != null) {
                        add(TileOption(HdrArea.SUBJECT, stringResource(R.string.hdr_area_subject), Icons.Rounded.Person))
                        add(TileOption(HdrArea.BACKGROUND, stringResource(R.string.hdr_area_background), Icons.Rounded.Landscape))
                        add(TileOption(HdrArea.BRUSH, stringResource(R.string.hdr_area_brush), Icons.Rounded.Brush))
                    }
                }
                OptionTileRow(
                    options = areas,
                    selected = current.area,
                    onSelect = { area ->
                        onHdr(null) { now ->
                            now?.copy(
                                area = area,
                                // The subject found for the key already is the subject here too.
                                mask = now.mask ?: subjectMask?.takeIf { area == HdrArea.SUBJECT || area == HdrArea.BACKGROUND },
                            )
                        }
                    },
                    enabled = enabled,
                )
                if (pictureUri == null) PanelHint(stringResource(R.string.hdr_area_frame_hint))
                when (current.area) {
                    HdrArea.EVERYWHERE -> Unit
                    HdrArea.SUBJECT, HdrArea.BACKGROUND -> if (pictureUri != null) HdrSubjectCard(current, onHdr, pictureUri, enabled)
                    HdrArea.BRUSH -> if (pictureUri != null) HdrBrushCard(current, onHdr, pictureUri, enabled)
                }

                SwitchCard(
                    icon = Icons.Rounded.Visibility,
                    title = stringResource(R.string.hdr_show_zones),
                    subtitle = stringResource(R.string.hdr_show_zones_hint),
                    checked = showZones,
                    onCheckedChange = onShowZones,
                )
            }
        }
    }
}

/** Below this HDR/SDR ratio the screen shows nothing brighter than white. */
private const val NO_HEADROOM = 1.02f

/** The subject the look goes on (or around): found by the cut-out model, corrected with brushes. */
@Composable
private fun HdrSubjectCard(hdr: ImageHdr, onHdr: HdrChange, uri: Uri, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val cut = LocalSubjectCut.current
    val name = uri.toString()
    val working = cut != null && (cut.status as? CutoutStatus.Working)?.uri == name
    val failed = cut?.let { (it.status as? CutoutStatus.Failed)?.takeIf { f -> f.uri == name }?.problem }
    val mask = hdr.mask?.takeIf { File(it).isFile }
    var refining by remember(mask) { mutableStateOf(false) }
    PanelCard {
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
                mask != null -> stringResource(R.string.hdr_subject_done)
                cut == null || !cut.ready -> stringResource(R.string.key_subject_needs_model)
                else -> stringResource(R.string.hdr_subject_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (failed != null) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        when {
            working -> {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { cut?.onStop?.invoke() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.smart_stop)) }
            }
            mask != null -> {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        refining = true
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.refine_open))
                }
                if (refining) {
                    RefineMaskDialog(
                        uri = uri,
                        maskPath = mask,
                        engine = cut?.refine,
                        onDone = { corrected ->
                            refining = false
                            onHdr(null) { it?.copy(mask = corrected) }
                        },
                        onDismiss = { refining = false },
                    )
                }
            }
            cut == null -> Unit
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        cut.onCut(uri) { path ->
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onHdr(null) { it?.copy(mask = path) }
                        }
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.hdr_subject_find))
                }
            }
        }
    }
}

/** Where the look goes, painted: the precise brush, and the magic one where the correcting model is set up. */
@Composable
private fun HdrBrushCard(hdr: ImageHdr, onHdr: HdrChange, uri: Uri, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cut = LocalSubjectCut.current
    val brush = hdr.brush?.takeIf { File(it).isFile }
    var painting by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    PanelCard {
        PanelHint(
            stringResource(
                when {
                    failed -> R.string.hdr_brush_failed
                    brush != null -> R.string.hdr_brush_done
                    else -> R.string.hdr_brush_hint
                },
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (brush != null) {
                        painting = brush
                    } else {
                        scope.launch {
                            val blank = runCatching { withContext(Dispatchers.IO) { HdrSources.blankMask(context, uri) } }.getOrNull()
                            failed = blank == null
                            painting = blank
                        }
                    }
                },
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(if (brush != null) R.string.hdr_brush_more else R.string.hdr_brush_paint))
            }
            if (brush != null) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onHdr(null) { it?.copy(brush = null) }
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.refine_reset)) }
            }
        }
    }
    painting?.let { path ->
        RefineMaskDialog(
            uri = uri,
            maskPath = path,
            engine = cut?.refine,
            title = stringResource(R.string.hdr_brush_title),
            startTool = MaskRefiner.Tool.PRECISE,
            kind = MaskKind.LIGHT,
            onDone = { painted ->
                painting = null
                onHdr(null) { it?.copy(brush = painted) }
            },
            onDismiss = { painting = null },
        )
    }
}
