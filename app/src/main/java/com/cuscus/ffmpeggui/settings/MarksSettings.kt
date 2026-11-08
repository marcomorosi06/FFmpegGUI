@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ToneColors
import com.cuscus.ffmpeggui.marking.MarkerModel
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress

/*
 * The AI marks' page: why the app marks what its models make (the law, said
 * plainly), what it marks and how, what the marks never say (nothing about
 * anyone), where they stop working, and the marks' own files, imported
 * before any model. A banner on the smart features' hub says whether they
 * are there; every feature's page says so while they are not.
 */

/** The marks' state and actions. */
class MarksFeatureUi(
    val model: SpeechModelStore.State,
    /** In memory now. */
    val loaded: Boolean,
    val onImport: (List<Uri>) -> Unit,
    val onStopImport: () -> Unit,
    val onVerify: () -> Unit,
    /** Removes the marks, and every model with them. */
    val onDeleteAll: () -> Unit,
    val onUnload: () -> Unit,
) {
    val ready: Boolean get() = !MarkerModel.NOT_PINNED && model is SpeechModelStore.State.Ready
}

/** The marks' page: the law, what is marked, what is never said, the limits; then their files. */
@Composable
internal fun MarksSection(marks: MarksFeatureUi) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    SettingsGroup(
        title = null,
        rows = listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>(
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Gavel,
                    title = stringResource(R.string.marks_law_title),
                    summary = stringResource(R.string.marks_law_text),
                    badge = MaterialShapes.Cookie9Sided,
                    colors = ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer),
                    link = AI_ACT_URL,
                    onClick = { openLink(context, AI_ACT_URL) },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.marks_what_title),
                    summary = stringResource(R.string.marks_what_text),
                    badge = MaterialShapes.Sunny,
                    colors = ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.marks_privacy_title),
                    summary = stringResource(R.string.marks_privacy_text),
                    badge = MaterialShapes.Clover8Leaf,
                    colors = ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.marks_limits_title),
                    summary = stringResource(R.string.marks_limits_text),
                    badge = MaterialShapes.Pill,
                )
            },
        ),
    )
    if (MarkerModel.NOT_PINNED) {
        // A build made before the marks' files were published: nothing can be imported yet.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.errorContainer)
                .padding(16.dp),
        ) {
            Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = scheme.onErrorContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.marks_not_published), style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
        }
    }
    val files = MarkerModel.FILES
    ModelCard(
        spec = ModelSpec(
            title = stringResource(R.string.marks_files_title),
            importing = stringResource(R.string.marks_importing),
            downloadText = stringResource(R.string.marks_download_text, files.downloads.size, Formatter.formatShortFileSize(context, files.installedBytes)),
            downloads = files.downloads.map { it.file to it.url },
            importText = stringResource(R.string.marks_import_text, files.files.joinToString(", ") { it.name }),
            installedBytes = files.installedBytes,
            notTheModel = stringResource(R.string.marks_not_the_files),
            busy = stringResource(R.string.smart_busy),
            deleteTitle = stringResource(R.string.marks_delete_title),
            deleteText = R.string.marks_delete_text,
        ),
        model = marks.model,
        loaded = marks.loaded,
        idle = true,
        onImport = marks.onImport,
        onStopImport = marks.onStopImport,
        onVerify = marks.onVerify,
        onDelete = marks.onDeleteAll,
        onUnload = marks.onUnload,
    )
    SettingsGroup(
        title = stringResource(R.string.marks_sources),
        rows = listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>(
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.marks_release_title),
                    summary = stringResource(R.string.marks_release_text, MarkerModel.RELEASE_TAG),
                    link = MarkerModel.FILES_URL,
                    onClick = { openLink(context, MarkerModel.FILES_URL) },
                    trailing = { OutLinkIcon() },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.GraphicEq,
                    title = "${MarkerModel.AUDIO_NAME}, ${MarkerModel.AUDIO_MAKER}",
                    summary = stringResource(R.string.marks_audioseal_text),
                    link = MarkerModel.AUDIO_PAGE_URL,
                    onClick = { openLink(context, MarkerModel.AUDIO_PAGE_URL) },
                    trailing = { OutLinkIcon() },
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Shield,
                    title = "${MarkerModel.PICTURE_NAME}, ${MarkerModel.PICTURE_MAKER}",
                    summary = stringResource(R.string.marks_trustmark_text),
                    link = MarkerModel.TRUSTMARK_CONFIRMATION_URL,
                    onClick = { openLink(context, MarkerModel.TRUSTMARK_CONFIRMATION_URL) },
                    trailing = { OutLinkIcon() },
                )
            },
        ),
    )
}

/**
 * The hub's banner: while the marks are missing, a large card that says they
 * come first and why, its badge breathing between two shapes; once they are
 * here, a quiet line that they are on. Either opens the marks' page.
 */
@Composable
internal fun MarksBanner(ready: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var bloom by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bloom = true }
    val spin by animateFloatAsState(if (bloom) 0f else -45f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "marksSpin")
    val interaction = remember { MutableInteractionSource() }
    val container = if (ready) scheme.surfaceContainerHigh else scheme.primaryContainer
    val content = if (ready) scheme.onSurface else scheme.onPrimaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(if (ready) 28.dp else 36.dp))
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpen()
            }
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = if (ready) 12.dp else 18.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie12Sided,
            to = if (ready) MaterialShapes.Clover8Leaf else MaterialShapes.SoftBurst,
            morphed = bloom,
            color = if (ready) scheme.tertiaryContainer else scheme.primary,
            rotationDegrees = spin,
            modifier = Modifier.size(if (ready) 40.dp else 52.dp),
        ) {
            Icon(
                if (ready) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (ready) scheme.onTertiaryContainer else scheme.onPrimary,
                modifier = Modifier.size(if (ready) 20.dp else 26.dp).graphicsLayer { rotationZ = -spin },
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(if (ready) R.string.marks_banner_on else R.string.marks_banner_needed),
                style = if (ready) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.titleMediumEmphasized,
                color = content,
            )
            Text(
                stringResource(if (ready) R.string.marks_banner_on_text else R.string.marks_banner_needed_text),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.8f),
            )
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

/** On a feature's page while the marks are missing: its models cannot be imported yet, and why. */
@Composable
internal fun MarksFirstNotice(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.secondaryContainer)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(scheme.secondary)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.marks_locked), style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onOpen()
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.marks_set_up), style = MaterialTheme.typography.titleSmallEmphasized)
        }
    }
}

/** The AI Act's text, on EUR-Lex. */
internal const val AI_ACT_URL = "https://eur-lex.europa.eu/eli/reg/2024/1689/oj"
