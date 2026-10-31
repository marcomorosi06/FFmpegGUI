@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.onboarding.rememberLoop
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ToneColors
import com.cuscus.ffmpeggui.onboarding.popIn
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import com.cuscus.ffmpeggui.translate.TranslationSettingsSection
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import com.cuscus.ffmpeggui.ui.adaptive.WindowLayout
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress

/*
 * The smart features' settings, made again as a showcase. The first page is a
 * hub: a hero (the features' badges going round a turning shape, a ring of
 * how many are ready, the privacy promise and the speed switch), then one tile
 * per feature, each with its own small film playing, its name, what it does
 * in a line and whether it is ready. A tile opens the feature's own page:
 * its film large, a line about it, and only its own cards (models, tests,
 * voices). Back goes back to the hub. Other screens can open a feature's page
 * directly (see SmartPageRequest).
 */

/** The features' pages. */
internal enum class SmartPage(
    val icon: ImageVector,
    val shape: RoundedPolygon,
    @StringRes val title: Int,
    @StringRes val tagline: Int,
    @StringRes val intro: Int,
) {
    SPEECH(Icons.Rounded.ClosedCaption, MaterialShapes.Cookie9Sided, R.string.smart_section_speech, R.string.smart_tag_speech, R.string.smart_speech_intro),
    TRANSLATE(Icons.Rounded.Translate, MaterialShapes.Clover4Leaf, R.string.translate_section, R.string.smart_tag_translate, R.string.translate_intro),
    CUTOUT(Icons.Rounded.ContentCut, MaterialShapes.Flower, R.string.smart_section_cutout, R.string.smart_tag_cutout, R.string.cutout_intro),
    ERASE(Icons.Rounded.AutoFixHigh, MaterialShapes.Sunny, R.string.erase_section, R.string.smart_tag_erase, R.string.erase_intro_settings),
    VOICE(Icons.Rounded.GraphicEq, MaterialShapes.SoftBurst, R.string.voice_section, R.string.smart_tag_voice, R.string.voice_intro),
    DUB(Icons.Rounded.RecordVoiceOver, MaterialShapes.Cookie12Sided, R.string.dub_section, R.string.smart_tag_dub, R.string.dub_intro),
    ABOUT(Icons.Rounded.Info, MaterialShapes.Pill, R.string.smart_about_models, R.string.smart_tag_about, R.string.smart_about_intro),
}

/** Each feature's own pair of colours, used on its tile, its badge and its page. */
@Composable
internal fun SmartPage.tone(): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        SmartPage.SPEECH -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        SmartPage.TRANSLATE -> ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        SmartPage.CUTOUT -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        SmartPage.ERASE -> ToneColors(scheme.primary, scheme.onPrimary)
        SmartPage.VOICE -> ToneColors(scheme.secondary, scheme.onSecondary)
        SmartPage.DUB -> ToneColors(scheme.tertiary, scheme.onTertiary)
        SmartPage.ABOUT -> ToneColors(scheme.surfaceContainerHighest, scheme.onSurface)
    }
}

/**
 * A feature's page to open the next time the smart features open, set by the
 * screen that sends the user there ("Get the model" in the dub, the eraser...).
 */
internal object SmartPageRequest {
    var next: SmartPage? = null
}

/** What a tile says of its feature. */
private enum class StatusKind { READY, MISSING, WORKING, PROBLEM, INFO }

private class FeatureStatus(val text: String, val kind: StatusKind)

@Composable
private fun modelStatus(state: SpeechModelStore.State?): FeatureStatus {
    val context = LocalContext.current
    return when (state) {
        is SpeechModelStore.State.Ready -> FeatureStatus(stringResource(R.string.smart_status_ready, Formatter.formatShortFileSize(context, state.bytes)), StatusKind.READY)
        is SpeechModelStore.State.Importing -> FeatureStatus(stringResource(R.string.smart_status_importing), StatusKind.WORKING)
        is SpeechModelStore.State.Failed -> FeatureStatus(stringResource(R.string.smart_status_problem), StatusKind.PROBLEM)
        else -> FeatureStatus(stringResource(R.string.smart_status_missing), StatusKind.MISSING)
    }
}

@Composable
private fun statusOf(page: SmartPage, smart: SmartFeaturesUi): FeatureStatus = when (page) {
    SmartPage.SPEECH -> modelStatus(smart.model)
    SmartPage.CUTOUT -> modelStatus(smart.cutout.model)
    SmartPage.ERASE -> {
        val states = smart.erase?.variants.orEmpty()
        val chosen = smart.erase?.let { states[it.variant] }
        modelStatus(chosen.takeIf { it !is SpeechModelStore.State.Missing } ?: states.values.firstOrNull { it is SpeechModelStore.State.Ready } ?: chosen)
    }
    SmartPage.VOICE -> modelStatus(smart.voice?.model)
    SmartPage.TRANSLATE -> {
        val n = smart.translation?.models?.size ?: 0
        if (n > 0) FeatureStatus(pluralStringResource(R.plurals.smart_status_pairs, n, n), StatusKind.READY) else FeatureStatus(stringResource(R.string.smart_status_missing), StatusKind.MISSING)
    }
    SmartPage.DUB -> {
        val voices = smart.dub?.piper?.voices?.size ?: 0
        val kokoro = smart.dub?.model is SpeechModelStore.State.Ready
        when {
            kokoro && voices > 0 -> FeatureStatus(pluralStringResource(R.plurals.smart_status_kokoro_and_voices, voices, voices), StatusKind.READY)
            kokoro -> FeatureStatus(stringResource(R.string.smart_status_kokoro), StatusKind.READY)
            voices > 0 -> FeatureStatus(pluralStringResource(R.plurals.smart_status_voices, voices, voices), StatusKind.READY)
            else -> modelStatus(smart.dub?.model)
        }
    }
    SmartPage.ABOUT -> FeatureStatus(stringResource(R.string.smart_status_licences), StatusKind.INFO)
}

/**
 * The smart features' settings: the hub, or a feature's page over it. Back
 * from a page comes back to the hub; from the hub, [onBack].
 */
@Composable
internal fun SmartSettingsScreen(smart: SmartFeaturesUi, onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SmartPageRequest.next.also { SmartPageRequest.next = null }) }
    BackHandler(enabled = page != null) { page = null }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            if (forward) {
                (slideInHorizontally(motion.defaultSpatialSpec()) { it / 3 } + fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.04f))
            } else {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 1.04f)) togetherWith
                    (slideOutHorizontally(motion.fastSpatialSpec()) { it / 3 } + fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.92f))
            }
        },
        label = "smartPage",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { shown ->
        val side = LocalWindowLayout.current.sidePadding(WindowLayout.READABLE_WIDTH)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = side, vertical = 16.dp),
        ) {
            if (shown == null) {
                Hub(smart, onBack = onBack, onOpen = { page = it })
            } else {
                FeaturePage(shown, smart, onBack = { page = null })
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -- The hub -------------------------------------------------------------------------------------

@Composable
private fun Hub(smart: SmartFeaturesUi, onBack: () -> Unit, onOpen: (SmartPage) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    BackButton(onBack)
    Text(stringResource(R.string.settings_smart), style = SettingsPageTitle, color = scheme.onBackground, modifier = Modifier.padding(horizontal = 4.dp).popIn(true, 0))

    val m = motion()
    // The hero: how many features are ready, each one's badge lit or not, the promise, the speed.
    val features = SmartPage.entries.filter { it != SmartPage.ABOUT }
    val readyPages = features.filter { statusOf(it, smart).kind == StatusKind.READY }.toSet()
    val ready = readyPages.size
    val total = features.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .popIn(true, 1)
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = ready,
                        transitionSpec = {
                            (slideInVertically(m.defaultSpatialSpec()) { it } + fadeIn(m.defaultEffectsSpec())) togetherWith
                                (slideOutVertically(m.fastSpatialSpec()) { -it } + fadeOut(m.fastEffectsSpec()))
                        },
                        label = "readyCount",
                    ) { n ->
                        Text(
                            "$n",
                            style = MaterialTheme.typography.displayLargeEmphasized,
                            color = if (n > 0) scheme.primary else scheme.onSurface,
                        )
                    }
                    Text(
                        "/$total",
                        style = MaterialTheme.typography.headlineMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                Text(
                    stringResource(R.string.smart_hub_ready_label),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            ReadyGrid(features, readyPages, onOpen = onOpen)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(28.dp).clip(remember { PolygonShape(MaterialShapes.Cookie4Sided) }).background(scheme.secondaryContainer),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(15.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.smart_hub_promise), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // The speed: one switch for every model.
        val fast = smart.fastKernels
        val pill by animateColorAsState(if (fast) scheme.primaryContainer else scheme.surfaceContainerHighest, motion().defaultEffectsSpec(), label = "speedPill")
        val speedInteraction = remember { MutableInteractionSource() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .springPress(speedInteraction, pressedScale = 0.97f)
                .clip(RoundedCornerShape(24.dp))
                .background(pill)
                .clickable(interactionSource = speedInteraction, indication = LocalIndication.current, role = Role.Switch) {
                    haptic.performHapticFeedback(if (fast) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    smart.onFastKernels(!fast)
                }
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            SpeedGauge(fast, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.smart_fast_kernels),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = if (fast) scheme.onPrimaryContainer else scheme.onSurface,
                )
                Text(
                    stringResource(if (fast) R.string.smart_fast_kernels_on else R.string.smart_fast_kernels_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fast) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                )
            }
            Switch(checked = fast, onCheckedChange = null)
        }
    }

    // The features, two by two.
    features.chunked(2).forEachIndexed { row, pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            pair.forEachIndexed { i, feature ->
                FeatureTile(feature, statusOf(feature, smart), order = 2 + row * 2 + i, modifier = Modifier.weight(1f)) { onOpen(feature) }
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
    AboutTile(order = 2 + features.size) { onOpen(SmartPage.ABOUT) }
}

/**
 * The features as a small grid of their badges: lit in their own colours when
 * ready, quiet when not. One after another they round off and back, a slow
 * ripple across the grid; a tap opens the feature.
 */
@Composable
private fun ReadyGrid(features: List<SmartPage>, ready: Set<SmartPage>, onOpen: (SmartPage) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val wave by rememberLoop(1_300 * features.size, "readyWave", still = -1f)
    val active = if (wave < 0f) -1 else (wave * features.size).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        features.chunked(3).forEachIndexed { row, three ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                three.forEachIndexed { col, page ->
                    val index = row * 3 + col
                    val lit = page in ready
                    val tone = page.tone()
                    val interaction = remember { MutableInteractionSource() }
                    MorphingContainer(
                        from = page.shape,
                        to = MaterialShapes.Circle,
                        morphed = index == active,
                        color = if (lit) tone.container else scheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(44.dp)
                            .springPress(interaction, pressedScale = 0.85f)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onOpen(page)
                            },
                    ) {
                        Icon(
                            page.icon,
                            contentDescription = stringResource(page.title),
                            tint = if (lit) tone.content else scheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A feature as a tile: its film playing, its badge and name, a line, and whether it is ready. */
@Composable
private fun FeatureTile(page: SmartPage, status: FeatureStatus, order: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tone = page.tone()
    Column(
        modifier = modifier
            .popIn(true, order)
            .springPress(interaction, pressedScale = 0.94f)
            .clip(RoundedCornerShape(if (pressed) 36.dp else 28.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            SmartShowcase(page, modifier = Modifier.fillMaxSize())
            MorphingContainer(
                from = page.shape,
                to = MaterialShapes.Circle,
                morphed = pressed,
                color = tone.container,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(36.dp),
            ) {
                Icon(page.icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Text(
            stringResource(page.tagline),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusChip(status, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
}

/** The models' licences, as a wide tile under the features. */
@Composable
private fun AboutTile(order: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .popIn(true, order)
            .springPress(interaction, pressedScale = 0.96f)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(12.dp),
    ) {
        Box(modifier = Modifier.size(width = 104.dp, height = 84.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHighest)) {
            SmartShowcase(SmartPage.ABOUT, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 0.7f; scaleY = 0.7f })
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(SmartPage.ABOUT.title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
            Text(stringResource(SmartPage.ABOUT.tagline), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

/** Whether a feature is ready, as a small pill: its colour and icon say it before the words. */
@Composable
private fun StatusChip(status: FeatureStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status.kind) {
        StatusKind.READY -> scheme.primary to scheme.onPrimary
        StatusKind.WORKING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StatusKind.PROBLEM -> scheme.errorContainer to scheme.onErrorContainer
        StatusKind.MISSING -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        StatusKind.INFO -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(CircleShape).background(container).padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
    ) {
        when (status.kind) {
            StatusKind.WORKING -> LoadingIndicator(color = content, modifier = Modifier.size(16.dp))
            else -> Icon(
                when (status.kind) {
                    StatusKind.READY -> Icons.Rounded.CheckCircle
                    StatusKind.PROBLEM -> Icons.Rounded.ErrorOutline
                    StatusKind.INFO -> Icons.Rounded.Info
                    else -> Icons.Rounded.Download
                },
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(status.text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// -- A feature's page ----------------------------------------------------------------------------

@Composable
private fun FeaturePage(page: SmartPage, smart: SmartFeaturesUi, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tone = page.tone()
    BackButton(onBack)
    // The film, large and uncovered; the badge blooms in beside the title.
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val bloom by animateFloatAsState(if (shown) 1f else 0.4f, motion().slowSpatialSpec(), label = "pageBadge")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .popIn(true, 0)
            .clip(RoundedCornerShape(36.dp)),
    ) {
        SmartShowcase(page, modifier = Modifier.fillMaxSize())
    }
    Column(modifier = Modifier.padding(horizontal = 4.dp).popIn(true, 1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = page.shape,
                morphed = shown,
                color = tone.container,
                rotationDegrees = (1f - bloom) * -90f,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = bloom
                        scaleY = bloom
                    },
            ) {
                Icon(page.icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(stringResource(page.title), style = SettingsPageTitle, color = scheme.onBackground, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(page.intro), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))
        StatusChip(statusOf(page, smart))
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.popIn(true, 2)) {
        when (page) {
            SmartPage.SPEECH -> SpeechSection(smart)
            SmartPage.TRANSLATE -> smart.translation?.let { TranslationSettingsSection(it, showHeading = false) }
            SmartPage.CUTOUT -> CutoutSection(smart)
            SmartPage.ERASE -> smart.erase?.let { EraseSettingsSection(it) }
            SmartPage.VOICE -> smart.voice?.let { VoiceSettingsSection(it) }
            SmartPage.DUB -> smart.dub?.let { DubSection(it) }
            SmartPage.ABOUT -> AboutModelsSection()
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    FilledTonalIconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onBack()
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = stringResource(R.string.action_back), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun motion() = MaterialTheme.motionScheme
