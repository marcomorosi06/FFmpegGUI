@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiOff
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ToneColors
import com.cuscus.ffmpeggui.cutout.CutoutEngine
import com.cuscus.ffmpeggui.cutout.CutoutModel
import com.cuscus.ffmpeggui.cutout.CutoutVariant
import com.cuscus.ffmpeggui.cutout.RefineEngine
import com.cuscus.ffmpeggui.cutout.RefineModel
import com.cuscus.ffmpeggui.speech.SmartFeatures
import com.cuscus.ffmpeggui.speech.SpeechEngine
import com.cuscus.ffmpeggui.speech.SpeechModel
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import com.cuscus.ffmpeggui.speech.SpeechVariant
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import java.util.Locale

/*
 * Smart features: what the phone can do with a model of its own. The subtitles
 * made from what is said, with the speech model, and the subject cut out of a
 * picture, with the cut-out model. For each: where to get it (the browser
 * downloads it: the app never goes online), importing it with every file
 * checked against its pinned SHA-256, checking it again, measuring its speed on
 * this phone, removing it.
 */

/** The smart features' state and actions, for their settings page. */
class SmartFeaturesUi(
    /** The build of the model chosen, and where each build is. */
    val variant: SpeechVariant,
    val variants: Map<SpeechVariant, SpeechModelStore.State>,
    val onChoose: (SpeechVariant) -> Unit,
    /** Where the chosen build is. */
    val model: SpeechModelStore.State,
    /** The model is in memory now. */
    val loaded: Boolean,
    val test: SmartFeatures.TestState,
    /** False while subtitles are being made (the model is in use). */
    val idle: Boolean,
    val onImport: (List<Uri>) -> Unit,
    val onStopImport: () -> Unit,
    val onVerify: () -> Unit,
    val onDelete: () -> Unit,
    val onTest: () -> Unit,
    val onStopTest: () -> Unit,
    val onUnload: () -> Unit,
    val cutout: CutoutFeatureUi,
)

/** The cut-out model's state and actions, for the same page. */
class CutoutFeatureUi(
    /** The model chosen, and where each model is. */
    val variant: CutoutVariant,
    val variants: Map<CutoutVariant, SpeechModelStore.State>,
    val onChoose: (CutoutVariant) -> Unit,
    /** Where the chosen model is. */
    val model: SpeechModelStore.State,
    /** The model is in memory now. */
    val loaded: Boolean,
    val test: SmartFeatures.CutoutTestState,
    /** False while a picture is being cut out (the model is in use). */
    val idle: Boolean,
    val onImport: (Uri) -> Unit,
    val onStopImport: () -> Unit,
    val onVerify: () -> Unit,
    val onDelete: () -> Unit,
    val onTest: () -> Unit,
    val onStopTest: () -> Unit,
    val onUnload: () -> Unit,
    /** The model that corrects a cut-out where the finger points (optional). */
    val refine: RefineFeatureUi,
)

/** The correcting model's state and actions (see RefineModel). */
class RefineFeatureUi(
    val model: SpeechModelStore.State,
    /** The model is in memory now. */
    val loaded: Boolean,
    val onImport: (List<Uri>) -> Unit,
    val onStopImport: () -> Unit,
    val onVerify: () -> Unit,
    val onDelete: () -> Unit,
    val onUnload: () -> Unit,
)

@Composable
internal fun SmartSettings(smart: SmartFeaturesUi) {
    val context = LocalContext.current
    SmartIntro()

    // Subtitles from speech.
    SectionTitle(stringResource(R.string.smart_section_speech), stringResource(R.string.smart_speech_intro))
    val speech = SpeechModel.of(smart.variant)
    VariantChooser(
        options = listOf(
            VariantOption(
                title = stringResource(R.string.speech_variant_int8),
                tag = stringResource(R.string.smart_variant_simple),
                text = stringResource(R.string.speech_variant_int8_text, Formatter.formatShortFileSize(context, SpeechModel.INT8.installedBytes)),
                icon = Icons.Rounded.Verified,
                state = smart.variants[SpeechVariant.INT8],
                selected = smart.variant == SpeechVariant.INT8,
                onSelect = { smart.onChoose(SpeechVariant.INT8) },
            ),
            VariantOption(
                title = stringResource(R.string.speech_variant_smoothquant),
                tag = stringResource(R.string.smart_variant_best),
                text = stringResource(R.string.speech_variant_smoothquant_text, Formatter.formatShortFileSize(context, SpeechModel.SMOOTHQUANT.installedBytes)),
                icon = Icons.Rounded.Tune,
                state = smart.variants[SpeechVariant.SMOOTHQUANT],
                selected = smart.variant == SpeechVariant.SMOOTHQUANT,
                onSelect = { smart.onChoose(SpeechVariant.SMOOTHQUANT) },
            ),
        ),
        enabled = smart.idle,
    )
    val speechArchive = speech.archiveDownload
    val speechFiles = speech.files.filter { it.asset == null }.joinToString(", ") { it.name }
    ModelCard(
        spec = ModelSpec(
            title = stringResource(R.string.smart_model_title_of, stringResource(if (smart.variant == SpeechVariant.INT8) R.string.speech_variant_int8 else R.string.speech_variant_smoothquant)),
            importing = stringResource(R.string.smart_model_importing),
            downloadText = stringResource(R.string.smart_step_download_files_text, speech.downloads.size, Formatter.formatShortFileSize(context, speech.installedBytes)),
            downloads = speech.downloads.map { it.file to it.url },
            otherDownload = speechArchive?.let { it.file to it.url },
            otherDownloadText = speech.archive?.let {
                stringResource(R.string.smart_step_download_archive, Formatter.formatShortFileSize(context, it.bytes))
            },
            importText = if (speechArchive != null) {
                stringResource(R.string.smart_step_import_files_or_archive, speechFiles)
            } else {
                stringResource(R.string.smart_step_import_files_text, speechFiles)
            },
            installedBytes = speech.installedBytes,
            notTheModel = stringResource(R.string.smart_problem_not_model_files),
            busy = stringResource(R.string.smart_busy),
            deleteTitle = stringResource(R.string.smart_delete_title),
            deleteText = R.string.smart_delete_text,
            unloadMinutes = (SpeechEngine.IDLE_UNLOAD_MS / 60_000).toInt(),
        ),
        model = smart.model,
        loaded = smart.loaded,
        idle = smart.idle,
        onImport = smart.onImport,
        onStopImport = smart.onStopImport,
        onVerify = smart.onVerify,
        onDelete = smart.onDelete,
        onUnload = smart.onUnload,
    )
    if (smart.model is SpeechModelStore.State.Ready) SpeedTestCard(smart)

    // The subject cut out of a picture.
    val cutout = smart.cutout
    SectionTitle(stringResource(R.string.smart_section_cutout), stringResource(R.string.cutout_intro))
    val cutter = CutoutModel.of(cutout.variant)
    VariantChooser(
        options = listOf(
            VariantOption(
                title = stringResource(R.string.cutout_variant_fast),
                tag = null,
                text = stringResource(R.string.cutout_variant_fast_text, CutoutModel.FAST.name, Formatter.formatShortFileSize(context, CutoutModel.FAST.bytes)),
                icon = Icons.Rounded.Speed,
                state = cutout.variants[CutoutVariant.FAST],
                selected = cutout.variant == CutoutVariant.FAST,
                onSelect = { cutout.onChoose(CutoutVariant.FAST) },
            ),
            VariantOption(
                title = stringResource(R.string.cutout_variant_precise),
                tag = null,
                text = stringResource(R.string.cutout_variant_precise_text, CutoutModel.PRECISE.name, Formatter.formatShortFileSize(context, CutoutModel.PRECISE.bytes)),
                icon = Icons.Rounded.CenterFocusStrong,
                state = cutout.variants[CutoutVariant.PRECISE],
                selected = cutout.variant == CutoutVariant.PRECISE,
                onSelect = { cutout.onChoose(CutoutVariant.PRECISE) },
            ),
        ),
        enabled = cutout.idle,
    )
    ModelCard(
        spec = ModelSpec(
            title = stringResource(R.string.cutout_model_title_of, cutter.name),
            importing = stringResource(R.string.cutout_model_importing),
            downloadText = stringResource(R.string.cutout_step_download_text, Formatter.formatShortFileSize(context, cutter.bytes)),
            downloads = listOf(cutter.fileName to cutter.downloadUrl),
            importText = stringResource(R.string.cutout_step_import_text, cutter.fileName),
            installedBytes = cutter.bytes,
            notTheModel = stringResource(R.string.cutout_problem_not_model, cutter.fileName),
            busy = stringResource(R.string.cutout_busy),
            deleteTitle = stringResource(R.string.cutout_delete_title),
            deleteText = R.string.cutout_delete_text,
            unloadMinutes = (CutoutEngine.IDLE_UNLOAD_MS / 60_000).toInt(),
            oneFile = true,
        ),
        model = cutout.model,
        loaded = cutout.loaded,
        idle = cutout.idle,
        onImport = { uris -> uris.firstOrNull()?.let(cutout.onImport) },
        onStopImport = cutout.onStopImport,
        onVerify = cutout.onVerify,
        onDelete = cutout.onDelete,
        onUnload = cutout.onUnload,
    )
    if (cutout.model is SpeechModelStore.State.Ready) CutoutTestCard(cutout)

    // Correcting a cut-out with the finger: keep and remove brushes, read by SlimSAM.
    val refine = cutout.refine
    SectionTitle(stringResource(R.string.refine_section), stringResource(R.string.refine_intro))
    val refineFiles = RefineModel.FILES.files.joinToString(", ") { it.name }
    ModelCard(
        spec = ModelSpec(
            title = stringResource(R.string.refine_model_title, RefineModel.NAME),
            importing = stringResource(R.string.cutout_model_importing),
            downloadText = stringResource(R.string.smart_step_download_files_text, RefineModel.FILES.downloads.size, Formatter.formatShortFileSize(context, RefineModel.FILES.installedBytes)),
            downloads = RefineModel.FILES.downloads.map { it.file to it.url },
            importText = stringResource(R.string.smart_step_import_files_text, refineFiles),
            installedBytes = RefineModel.FILES.installedBytes,
            notTheModel = stringResource(R.string.refine_problem_not_model, refineFiles),
            busy = stringResource(R.string.cutout_busy),
            deleteTitle = stringResource(R.string.refine_delete_title),
            deleteText = R.string.refine_delete_text,
            unloadMinutes = (RefineEngine.IDLE_UNLOAD_MS / 60_000).toInt(),
        ),
        model = refine.model,
        loaded = refine.loaded,
        idle = cutout.idle,
        onImport = refine.onImport,
        onStopImport = refine.onStopImport,
        onVerify = refine.onVerify,
        onDelete = refine.onDelete,
        onUnload = refine.onUnload,
    )

    SettingsGroup(
        title = stringResource(R.string.smart_about_model),
        rows = listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>(
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Language,
                    title = SpeechModel.NAME,
                    summary = stringResource(R.string.smart_model_summary, SpeechModel.MAKER, SpeechModel.LANGUAGES.size, SpeechModel.LICENCE),
                    badge = MaterialShapes.Gem,
                    onClick = { openLink(context, SpeechModel.MODEL_PAGE_URL) },
                    trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.speech_variant_smoothquant),
                    summary = stringResource(R.string.smart_smoothquant_summary, SpeechModel.LICENCE),
                    badge = MaterialShapes.Diamond,
                    onClick = { openLink(context, SpeechModel.SMOOTHQUANT_PAGE_URL) },
                    trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.ContentCut,
                    title = CutoutModel.PRECISE.name,
                    summary = stringResource(R.string.cutout_model_summary, CutoutModel.PRECISE.maker, CutoutModel.LICENCE),
                    badge = MaterialShapes.Clover4Leaf,
                    onClick = { openLink(context, CutoutModel.PRECISE.modelPageUrl) },
                    trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Speed,
                    title = CutoutModel.FAST.name,
                    summary = stringResource(R.string.cutout_model_summary, CutoutModel.FAST.maker, CutoutModel.LICENCE),
                    badge = MaterialShapes.Clover8Leaf,
                    onClick = { openLink(context, CutoutModel.FAST.modelPageUrl) },
                    trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Brush,
                    title = RefineModel.NAME,
                    summary = stringResource(R.string.refine_model_summary, RefineModel.MAKER, RefineModel.LICENCE),
                    badge = MaterialShapes.SoftBurst,
                    onClick = { openLink(context, RefineModel.MODEL_PAGE_URL) },
                    trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.smart_credit_title),
                    summary = stringResource(R.string.smart_credit_summary),
                    badge = MaterialShapes.Cookie7Sided,
                    onClick = { openLink(context, ProjectLinks.ANTI_VOCALE) },
                    trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.cutout_credit_title),
                    summary = stringResource(R.string.cutout_credit_summary),
                    badge = MaterialShapes.Cookie6Sided,
                    onClick = { openLink(context, CutoutModel.REMBG_URL) },
                    trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                )
            },
        ),
    )
}

/** The title of a feature on the page, with a line about it. */
@Composable
private fun SectionTitle(title: String, text: String? = null) {
    Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)) {
        Text(title, style = SettingsGroupTitle, color = MaterialTheme.colorScheme.onSurface)
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** What differs between the models' cards. */
private class ModelSpec(
    val title: String,
    /** Said while it is imported. */
    val importing: String,
    val downloadText: String,
    /** What to download: file name and link, one button each (one button, unnamed, when alone). */
    val downloads: List<Pair<String, String>>,
    /** Something else to download instead (file name and link), and what it is. */
    val otherDownload: Pair<String, String>? = null,
    val otherDownloadText: String? = null,
    val importText: String,
    val installedBytes: Long,
    val notTheModel: String,
    val busy: String,
    val deleteTitle: String,
    /** Takes the space freed. */
    @androidx.annotation.StringRes val deleteText: Int,
    val unloadMinutes: Int,
    /** A single file is picked (not an archive or several files). */
    val oneFile: Boolean = false,
)

/**
 * What the smart subtitles are, in one line, and why the model comes through
 * the browser: plainly, as the app's privacy by design asks.
 */
@Composable
private fun SmartIntro() {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(R.string.smart_intro_text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.smart_privacy_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * The model on this phone: the two steps to get it, an import as it goes, the
 * files and their checksums once it is here, or what went wrong.
 */
@Composable
private fun ModelCard(
    spec: ModelSpec,
    model: SpeechModelStore.State,
    loaded: Boolean,
    idle: Boolean,
    onImport: (List<Uri>) -> Unit,
    onStopImport: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    onUnload: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val state = model
    val importing = state as? SpeechModelStore.State.Importing
    val ready = state as? SpeechModelStore.State.Ready
    val failed = state as? SpeechModelStore.State.Failed
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onImport(uris)
        }
    }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onImport(listOf(uri))
        }
    }
    val tone = when {
        failed != null -> ToneColors(scheme.errorContainer, scheme.onErrorContainer)
        ready != null -> ToneColors(scheme.primary, scheme.onPrimary)
        else -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    val spin by animateFloatAsState(if (importing != null) 360f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "modelTurn")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = if (ready != null) MaterialShapes.Cookie12Sided else MaterialShapes.Cookie4Sided,
                morphed = ready != null || failed != null,
                color = tone.container,
                modifier = Modifier.graphicsLayer { rotationZ = spin }.size(52.dp),
            ) {
                if (importing != null) {
                    LoadingIndicator(color = tone.content, modifier = Modifier.size(36.dp))
                } else {
                    Icon(
                        imageVector = when {
                            ready != null -> Icons.Rounded.VerifiedUser
                            failed != null -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.Download
                        },
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.graphicsLayer { rotationZ = -spin }.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(spec.title, style = SettingsRowTitle, color = scheme.onSurface)
                Text(
                    text = when {
                        importing != null ->
                            if (importing.phase == SpeechModelStore.Phase.CHECKING) stringResource(R.string.smart_model_checking) else spec.importing
                        ready != null -> stringResource(R.string.smart_model_ready, Formatter.formatShortFileSize(context, ready.bytes))
                        failed != null -> when (failed.problem) {
                            SpeechModelStore.Problem.NOT_THE_MODEL -> spec.notTheModel
                            SpeechModelStore.Problem.INCOMPLETE -> stringResource(R.string.smart_problem_incomplete)
                            SpeechModelStore.Problem.HASH_MISMATCH -> stringResource(R.string.smart_problem_hash)
                            SpeechModelStore.Problem.UNREADABLE -> stringResource(R.string.smart_problem_unreadable)
                            SpeechModelStore.Problem.NO_SPACE -> stringResource(R.string.smart_problem_space)
                        }
                        else -> stringResource(R.string.smart_model_missing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        if (importing != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val fraction = importing.fraction
            if (fraction != null) {
                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    text = importing.file ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (fraction != null) {
                    Text(String.format(Locale.ROOT, "%d %%", Math.round(fraction * 100)), style = MaterialTheme.typography.labelMediumEmphasized, color = scheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    onStopImport()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.smart_stop))
            }
            Text(stringResource(R.string.smart_import_keep_open), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            return@Column
        }

        val checks = ready?.checks ?: failed?.checks.orEmpty()
        if (checks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Checksums(checks, ready?.archiveMatches)
        }

        if (ready == null) {
            // The two steps: the browser downloads, the app imports.
            Spacer(modifier = Modifier.height(14.dp))
            Step(
                number = 1,
                title = stringResource(R.string.smart_step_download),
                text = spec.downloadText,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    spec.downloads.forEach { (file, url) ->
                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                openLink(context, url)
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (spec.downloads.size == 1) stringResource(R.string.smart_download) else file,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val other = spec.otherDownload
                    if (other != null) {
                        if (spec.otherDownloadText != null) {
                            Text(
                                spec.otherDownloadText,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                openLink(context, other.second)
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.smart_download_archive), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Step(
                number = 2,
                title = stringResource(R.string.smart_step_import),
                text = spec.importText,
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (spec.oneFile) singlePicker.launch(arrayOf("*/*")) else picker.launch(arrayOf("*/*"))
                    },
                    enabled = idle,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.smart_import))
                }
            }
            Text(
                text = stringResource(R.string.smart_space_needed, Formatter.formatShortFileSize(context, spec.installedBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }

        if (ready != null || failed?.installed == true) {
            Spacer(modifier = Modifier.height(14.dp))
            if (loaded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.secondaryContainer).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.smart_in_memory, spec.unloadMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onUnload, enabled = idle) { Text(stringResource(R.string.smart_unload)) }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onVerify()
                    },
                    enabled = idle,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.smart_verify), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirmDelete = true
                    },
                    enabled = idle,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.smart_delete), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!idle) {
                Text(spec.busy, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            icon = Icons.Rounded.Delete,
            title = spec.deleteTitle,
            text = stringResource(spec.deleteText, Formatter.formatShortFileSize(context, (model as? SpeechModelStore.State.Ready)?.bytes ?: spec.installedBytes)),
            confirm = stringResource(R.string.smart_delete),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** One model to choose: what it is, and where it is on this phone ([state], null when unknown). */
private class VariantOption(
    val title: String,
    val tag: String?,
    val text: String,
    val icon: ImageVector,
    val state: SpeechModelStore.State?,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

/**
 * The models to choose from, as tiles: the chosen one filled, its shape
 * blooming; each says whether it is on this phone. Both can be installed: the
 * chosen one is used, and the card below is about it.
 */
@Composable
private fun VariantChooser(options: List<VariantOption>, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
        options.forEachIndexed { index, option ->
            val first = index == 0
            val last = index == options.lastIndex
            val big = 24.dp
            val small = 8.dp
            val shape = RoundedCornerShape(
                topStart = if (first) big else small,
                topEnd = if (first) big else small,
                bottomStart = if (last) big else small,
                bottomEnd = if (last) big else small,
            )
            val container = if (option.selected) scheme.secondaryContainer else scheme.surfaceContainerHigh
            val content = if (option.selected) scheme.onSecondaryContainer else scheme.onSurface
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(container)
                    .selectable(selected = option.selected, enabled = enabled, role = Role.RadioButton) {
                        if (!option.selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            option.onSelect()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                MorphingContainer(
                    from = MaterialShapes.Circle,
                    to = MaterialShapes.Cookie7Sided,
                    morphed = option.selected,
                    color = if (option.selected) scheme.primary else scheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(option.icon, contentDescription = null, tint = if (option.selected) scheme.onPrimary else scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(option.title, style = MaterialTheme.typography.titleSmallEmphasized, color = content, modifier = Modifier.weight(1f, fill = false))
                        if (option.tag != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                option.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onTertiaryContainer,
                                maxLines = 1,
                                modifier = Modifier.clip(CircleShape).background(scheme.tertiaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(option.text, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
                    Text(
                        text = when (option.state) {
                            is SpeechModelStore.State.Ready -> stringResource(R.string.smart_variant_installed)
                            is SpeechModelStore.State.Importing -> stringResource(R.string.smart_variant_importing)
                            is SpeechModelStore.State.Failed -> stringResource(R.string.smart_variant_problem)
                            else -> stringResource(R.string.smart_variant_missing)
                        },
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = when (option.state) {
                            is SpeechModelStore.State.Ready -> if (option.selected) scheme.onSecondaryContainer else scheme.primary
                            is SpeechModelStore.State.Failed -> scheme.error
                            else -> content.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    if (option.selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (option.selected) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    Text(
        stringResource(R.string.smart_variant_hint),
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
    )
}

/** A numbered step: its number in a little shape, what to do, and the button that does it. */
@Composable
private fun Step(number: Int, title: String, text: String, action: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHighest).padding(12.dp),
    ) {
        MorphingContainer(from = MaterialShapes.Cookie6Sided, to = MaterialShapes.Cookie6Sided, morphed = false, color = scheme.primaryContainer, modifier = Modifier.size(32.dp)) {
            Text(number.toString(), style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.onPrimaryContainer)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.onSurface)
            Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            action()
        }
    }
}

/** The files and whether each matched its SHA-256: a line that opens to the hashes themselves. */
@Composable
private fun Checksums(checks: List<SpeechModelStore.FileCheck>, archiveMatches: Boolean?) {
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val matching = checks.count { it.matches }
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "checksumsTurn")
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHighest)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                if (matching == checks.size) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (matching == checks.size) scheme.primary else scheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                pluralStringResource(R.plurals.smart_checksums, checks.size, matching, checks.size),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                if (archiveMatches != null) {
                    Text(
                        stringResource(if (archiveMatches) R.string.smart_archive_matches else R.string.smart_archive_differs),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                checks.forEach { check ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (check.matches) scheme.primary else scheme.error))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(check.name, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                        }
                        Text(
                            text = "SHA-256 " + (check.actual ?: stringResource(R.string.smart_file_missing)),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (check.matches) scheme.onSurfaceVariant else scheme.error,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                        if (!check.matches) {
                            Text(
                                text = stringResource(R.string.smart_expected, check.expected),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The speed test: loads the model and listens to the recordings that came with
 * it, then says how fast this phone is (times real time, what a minute of video
 * takes) and how many words it got right.
 */
@Composable
private fun SpeedTestCard(smart: SmartFeaturesUi) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val test = smart.test
    val running = test as? SmartFeatures.TestState.Running
    val result = (test as? SmartFeatures.TestState.Done)?.result
    val failed = (test as? SmartFeatures.TestState.Failed)?.problem
    Column(
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
                morphed = result != null,
                color = if (failed != null) scheme.errorContainer else scheme.secondaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                if (running != null) {
                    LoadingIndicator(color = scheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
                } else {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = if (failed != null) scheme.onErrorContainer else scheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.smart_test_title), style = SettingsRowTitle, color = scheme.onSurface)
                Text(
                    text = when {
                        running?.loading == true -> stringResource(R.string.smart_test_loading)
                        running != null -> stringResource(R.string.smart_test_listening, SpeechModel.INT8.samples.size)
                        failed == SmartFeatures.Problem.NO_MEMORY -> stringResource(R.string.smart_test_no_memory, freeMemory(context))
                        failed == SmartFeatures.Problem.NO_MODEL -> stringResource(R.string.smart_test_no_samples)
                        failed == SmartFeatures.Problem.NO_RECORDINGS -> stringResource(R.string.smart_test_no_recordings)
                        failed != null -> stringResource(R.string.smart_test_failed)
                        result != null -> stringResource(R.string.smart_test_done, result.threads)
                        else -> stringResource(R.string.smart_test_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (result != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Figure(
                    value = String.format(Locale.ROOT, "%.1f\u00D7", result.timesRealTime),
                    label = stringResource(R.string.smart_figure_speed),
                    modifier = Modifier.weight(1f),
                )
                Figure(
                    value = String.format(Locale.ROOT, "%d %%", Math.round(result.accuracy * 100)),
                    label = stringResource(R.string.smart_figure_accuracy),
                    modifier = Modifier.weight(1f),
                )
                Figure(
                    value = seconds(result.loadMs),
                    label = stringResource(R.string.smart_figure_load),
                    modifier = Modifier.weight(1f),
                )
            }
            val minute = if (result.timesRealTime > 0f) (60_000f / result.timesRealTime).toLong() else 0L
            Text(
                stringResource(R.string.smart_test_minute, seconds(minute)),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                result.samples.forEach { sample ->
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHighest).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Locale.forLanguageTag(sample.language).getDisplayLanguage(Locale.getDefault()),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                String.format(Locale.ROOT, "%d %% \u00B7 %s", Math.round(sample.accuracy * 100), seconds(sample.workMs)),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text("\u201C" + sample.heard + "\u201D", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (running != null) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    smart.onStopTest()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.smart_stop)) }
        } else {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    smart.onTest()
                },
                enabled = smart.idle,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    stringResource(if (result == null) R.string.smart_test_start else R.string.smart_test_again),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
        }
    }
}

/**
 * The cut-out model's speed test: loads the model and cuts a made-up picture
 * out, then says how long loading and one picture take on this phone.
 */
@Composable
private fun CutoutTestCard(cutout: CutoutFeatureUi) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val test = cutout.test
    val running = test is SmartFeatures.CutoutTestState.Running
    val result = (test as? SmartFeatures.CutoutTestState.Done)?.result
    val failed = (test as? SmartFeatures.CutoutTestState.Failed)?.problem
    Column(
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
                morphed = result != null,
                color = if (failed != null) scheme.errorContainer else scheme.secondaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                if (running) {
                    LoadingIndicator(color = scheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
                } else {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = if (failed != null) scheme.onErrorContainer else scheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.smart_test_title), style = SettingsRowTitle, color = scheme.onSurface)
                Text(
                    text = when {
                        running -> stringResource(R.string.cutout_test_running)
                        failed == SmartFeatures.Problem.NO_MEMORY -> stringResource(R.string.smart_test_no_memory, freeMemory(context))
                        failed != null -> stringResource(R.string.smart_test_failed)
                        result != null -> stringResource(R.string.smart_test_done, result.threads)
                        else -> stringResource(R.string.cutout_test_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (result != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Figure(value = seconds(result.workMs), label = stringResource(R.string.cutout_figure_picture), modifier = Modifier.weight(1f))
                Figure(value = seconds(result.loadMs), label = stringResource(R.string.smart_figure_load), modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (running) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    cutout.onStopTest()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.smart_stop)) }
        } else {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    cutout.onTest()
                },
                enabled = cutout.idle,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    stringResource(if (result == null) R.string.smart_test_start else R.string.smart_test_again),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
        }
    }
}

/** A number and what it is, in a tonal tile. */
@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(scheme.primaryContainer).padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        Text(value, style = SettingsGroupTitle, color = scheme.onPrimaryContainer, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "3.2 s". */
private fun seconds(ms: Long): String = String.format(Locale.getDefault(), "%.1f s", ms / 1000f)

/** Free memory of the phone now, for the message that there is too little. */
private fun freeMemory(context: Context): String {
    val info = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(info)
    return Formatter.formatShortFileSize(context, info.availMem)
}
