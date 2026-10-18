@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.onboarding

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.BoxWithConstraints
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.FormatBadge
import com.cuscus.ffmpeggui.HapticSample
import com.cuscus.ffmpeggui.playHapticSample
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ToneColors
import com.cuscus.ffmpeggui.categoryColors
import com.cuscus.ffmpeggui.categoryShape
import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.export.PublicCopies
import com.cuscus.ffmpeggui.formatDescriptionRes
import com.cuscus.ffmpeggui.formatShortName
import com.cuscus.ffmpeggui.media.HardwareCalibration
import com.cuscus.ffmpeggui.projects.ProjectCard
import com.cuscus.ffmpeggui.settings.AppSettings
import com.cuscus.ffmpeggui.settings.CalibrationCard
import com.cuscus.ffmpeggui.settings.ConfirmDialog
import com.cuscus.ffmpeggui.settings.SettingsGroup
import com.cuscus.ffmpeggui.settings.SettingsRow
import com.cuscus.ffmpeggui.settings.SettingsRowTitle
import com.cuscus.ffmpeggui.settings.SettingsSwitchRow
import com.cuscus.ffmpeggui.settings.ThemeMode
import com.cuscus.ffmpeggui.settings.groupShape
import com.cuscus.ffmpeggui.settings.liveUpdatesAvailable
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.springPress
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import com.cuscus.ffmpeggui.ui.theme.dynamicColorAvailable
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The welcome's pages. Each one tells one thing about the app with a moving
 * picture, and the ones about settings set them right there (the same settings,
 * kept the same way, as in the settings pages): what is chosen applies at once,
 * the colours to this very screen.
 */

// -- The frame of a page ----------------------------------------------------------------------

/** The widest a page's column gets (tablets, landscape): centred beyond it. */
private val MAX_PAGE_WIDTH = 560.dp

/**
 * A page: one column, centred and scrolling when the screen is short, with room
 * around it. On a screen that is wider than it is tall (a phone on its side, a
 * tablet) the page's first piece (its heading, or its picture) goes on the
 * left and the rest on the right, so that the page fits the short height.
 */
@Composable
internal fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    val window = LocalWindowLayout.current
    if (window.isLandscape && window.width >= SPLIT_MIN_WIDTH) {
        BoxWithConstraints(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
            val room = maxHeight
            Column(
                modifier = Modifier
                    .widthIn(max = MAX_SPLIT_WIDTH)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
            ) {
                val scope = this
                SplitPage(minHeight = room - 24.dp, spacing = 16.dp, gap = 40.dp) { scope.content() }
            }
        }
        return
    }
    Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = MAX_PAGE_WIDTH)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            content = content,
        )
    }
}

/** From this width a page wider than it is tall is laid out in two columns. */
private val SPLIT_MIN_WIDTH = 560.dp

/** The widest a page in two columns gets. */
private val MAX_SPLIT_WIDTH = 1080.dp

/**
 * The first child on the left (two fifths of the width), the others one under
 * the other on the right, [spacing] apart; each side in the middle of the
 * height, which is at least [minHeight].
 */
@Composable
private fun SplitPage(minHeight: Dp, spacing: Dp, gap: Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val leftWidth = ((width - gapPx) * 0.42f).roundToInt()
        val rightWidth = width - gapPx - leftWidth
        val spacingPx = spacing.roundToPx()
        val left = measurables.firstOrNull()?.measure(Constraints(maxWidth = leftWidth))
        val right = measurables.drop(1).map { it.measure(Constraints(maxWidth = rightWidth)) }
        val rightHeight = right.sumOf { it.height } + spacingPx * (right.size - 1).coerceAtLeast(0)
        val height = maxOf(left?.height ?: 0, rightHeight, minHeight.roundToPx().coerceAtLeast(0))
        layout(width, height) {
            left?.place((leftWidth - left.width) / 2, (height - left.height) / 2)
            var y = (height - rightHeight) / 2
            right.forEach {
                it.place(leftWidth + gapPx + (rightWidth - it.width) / 2, y)
                y += it.height + spacingPx
            }
        }
    }
}

/** A page's title, in the display face, large. */
internal val WelcomeTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = 36.sp, lineHeight = 40.sp)

/**
 * The top of a page: a small word in the primary colour ([kicker]), the
 * [title] large in the display face, and a few lines on it ([text]). The three
 * arrive one after the other, as the page's first layers.
 */
@Composable
internal fun PageHeading(motion: PageMotion, kicker: String, title: String, text: String, centered: Boolean = false, firstOrder: Int = 0) {
    val scheme = MaterialTheme.colorScheme
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = kicker.uppercase(),
            style = MaterialTheme.typography.labelLargeEmphasized.copy(letterSpacing = 2.sp),
            color = scheme.primary,
            textAlign = align,
            modifier = Modifier.enter(motion, firstOrder),
        )
        Text(
            text = title,
            style = WelcomeTitle,
            color = scheme.onBackground,
            textAlign = align,
            modifier = Modifier.enter(motion, firstOrder + 1).semantics { heading() },
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.enter(motion, firstOrder + 2),
        )
    }
}

// -- 1. Hello ---------------------------------------------------------------------------------

@Composable
internal fun HelloPage(motion: PageMotion) {
    val scheme = MaterialTheme.colorScheme
    PageColumn {
        HelloHero(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).enter(motion, 0))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().enter(motion, 1)) {
            Text(
                text = stringResource(R.string.home_title_ffmpeg),
                style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = 30.sp, lineHeight = 34.sp),
                color = scheme.primary,
            )
            Text(
                text = stringResource(R.string.home_title_gui),
                style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(1000), fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-1).sp),
                color = scheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.welcome_hello_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = scheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().enter(motion, 2),
        )
        Text(
            text = stringResource(R.string.welcome_hello_text),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().enter(motion, 3),
        )
    }
}

// -- 2. Formats --------------------------------------------------------------------------------

/** The categories shown on the formats page, in order. */
private val CATEGORIES = listOf(
    FormatCategory.VIDEO, FormatCategory.AUDIO, FormatCategory.IMAGE,
    FormatCategory.ANIMATION, FormatCategory.TEXT, FormatCategory.CUSTOM,
)

private fun categoryLabel(category: FormatCategory): Int = when (category) {
    FormatCategory.VIDEO -> R.string.format_category_video
    FormatCategory.ANIMATION -> R.string.format_category_animation
    FormatCategory.AUDIO -> R.string.format_category_audio
    FormatCategory.IMAGE -> R.string.format_category_image
    FormatCategory.TEXT -> R.string.format_category_text
    FormatCategory.CUSTOM -> R.string.format_category_custom
}

/**
 * Every format the app makes, by category, as the badges of the format picker;
 * a tap on one morphs it into a circle and says, below, what it is good for.
 */
@Composable
internal fun FormatsPage(motion: PageMotion) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var chosen by rememberSaveable { mutableStateOf(OutputFormat.MP4) }
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_formats_kicker),
            title = stringResource(R.string.welcome_formats_title),
            text = stringResource(R.string.welcome_formats_text),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.selectableGroup()) {
            CATEGORIES.forEachIndexed { index, category ->
                val formats = OutputFormat.entries.filter { it.category == category }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .enter(motion, 3 + index)
                        .fillMaxWidth()
                        .clip(groupShape(index, CATEGORIES.size))
                        .background(scheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(categoryLabel(category)),
                        style = SettingsRowTitle,
                        color = scheme.onSurface,
                        modifier = Modifier.width(92.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        formats.forEach { format ->
                            FormatChoice(
                                format = format,
                                selected = format == chosen,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    chosen = format
                                },
                            )
                        }
                    }
                }
            }
        }
        // What the chosen format is good for.
        val springs = MaterialTheme.motionScheme
        val fadeSpec = springs.defaultEffectsSpec<Float>()
        val growSpec = springs.defaultSpatialSpec<Float>()
        val leaveSpec = springs.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = chosen,
            transitionSpec = {
                (fadeIn(fadeSpec) + scaleIn(growSpec, initialScale = 0.92f)) togetherWith fadeOut(leaveSpec)
            },
            label = "formatCaption",
            modifier = Modifier.enter(motion, 9),
        ) { format ->
            val tone = categoryColors(format.category)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(tone.container)
                    .padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                FormatBadge(format = format, size = 56.dp, morphed = true)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatShortName(format), style = MaterialTheme.typography.titleLargeEmphasized, color = tone.content)
                    Text(stringResource(formatDescriptionRes(format)), style = MaterialTheme.typography.bodyMedium, color = tone.content)
                }
            }
        }
    }
}

/** One format's badge to tap: chosen, or under the finger, it is a circle. */
@Composable
private fun FormatChoice(format: OutputFormat, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val name = formatShortName(format)
    FormatBadge(
        format = format,
        size = 46.dp,
        morphed = selected || pressed,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.86f)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
    )
}

// -- 3. Editor -----------------------------------------------------------------------------------

private class Feature(val icon: ImageVector, val label: Int, val shape: RoundedPolygon)

private val FEATURES: List<Feature>
    get() = listOf(
        Feature(Icons.Rounded.ContentCut, R.string.welcome_feature_trim, MaterialShapes.Cookie9Sided),
        Feature(Icons.Rounded.TextFields, R.string.welcome_feature_texts, MaterialShapes.Clover4Leaf),
        Feature(Icons.Rounded.Diamond, R.string.welcome_feature_keyframes, MaterialShapes.Diamond),
        Feature(Icons.Rounded.Palette, R.string.welcome_feature_colours, MaterialShapes.Flower),
        Feature(Icons.Rounded.Speed, R.string.welcome_feature_speed, MaterialShapes.SoftBurst),
        Feature(Icons.Rounded.GraphicEq, R.string.welcome_feature_sound, MaterialShapes.Cookie6Sided),
        Feature(Icons.Rounded.ClosedCaption, R.string.welcome_feature_subtitles, MaterialShapes.Cookie12Sided),
        Feature(Icons.Rounded.Undo, R.string.welcome_feature_undo, MaterialShapes.Sunny),
    )

@Composable
internal fun EditorPage(motion: PageMotion) {
    val scheme = MaterialTheme.colorScheme
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_editor_kicker),
            title = stringResource(R.string.welcome_editor_title),
            text = stringResource(R.string.welcome_editor_text),
        )
        EditorMock(modifier = Modifier.enter(motion, 3))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().enter(motion, 4),
        ) {
            val tones = listOf(
                ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer),
                ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
            )
            FEATURES.forEachIndexed { index, feature ->
                FeatureChip(feature, tones[index % tones.size])
            }
        }
    }
}

/** One thing the editor does: its icon in a shape that morphs under the finger, and its name. */
@Composable
private fun FeatureChip(feature: Feature, tone: ToneColors) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        MorphingContainer(from = feature.shape, to = MaterialShapes.Circle, morphed = pressed, color = tone.container, modifier = Modifier.size(34.dp)) {
            Icon(feature.icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(feature.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

// -- 4. Smart features ----------------------------------------------------------------------------

@Composable
internal fun SmartPage(
    motion: PageMotion,
    speechReady: Boolean,
    cutoutReady: Boolean,
    openSmart: Boolean,
    onOpenSmart: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_smart_kicker),
            title = stringResource(R.string.welcome_smart_title),
            text = stringResource(R.string.welcome_smart_text),
        )
        SmartCard(
            title = stringResource(R.string.welcome_smart_speech_title),
            text = stringResource(R.string.welcome_smart_speech_text),
            ready = speechReady,
            modifier = Modifier.enter(motion, 3),
        ) {
            SpokenWordsArt(modifier = Modifier.fillMaxWidth())
        }
        SmartCard(
            title = stringResource(R.string.welcome_smart_cutout_title),
            text = stringResource(R.string.welcome_smart_cutout_text),
            ready = cutoutReady,
            modifier = Modifier.enter(motion, 4),
        ) {
            CutoutArt(modifier = Modifier.fillMaxWidth().height(132.dp))
        }
        AnimatedVisibility(
            visible = !(speechReady && cutoutReady),
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            modifier = Modifier.enter(motion, 5),
        ) {
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsSwitchRow(
                            shape = shape,
                            icon = Icons.Rounded.AutoAwesome,
                            title = stringResource(R.string.welcome_smart_later_title),
                            summary = stringResource(R.string.welcome_smart_later_text),
                            checked = openSmart,
                            onCheckedChange = onOpenSmart,
                            badge = MaterialShapes.Clover8Leaf,
                            colors = ToneColors(scheme.tertiary, scheme.onTertiary),
                        )
                    },
                ),
            )
        }
    }
}

/** One smart feature: its moving picture, what it does, and whether its model is here. */
@Composable
private fun SmartCard(title: String, text: String, ready: Boolean, modifier: Modifier = Modifier, art: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        art()
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SettingsRowTitle, color = scheme.onSurface, modifier = Modifier.weight(1f))
            StatusPill(ready = ready)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}

/** "Ready" with a tick, or "Not set up yet". */
@Composable
private fun StatusPill(ready: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (ready) scheme.primary else scheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (ready) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            stringResource(if (ready) R.string.welcome_status_ready else R.string.welcome_status_later),
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) scheme.onPrimary else scheme.onSurfaceVariant,
        )
    }
}

// -- 5. Privacy ---------------------------------------------------------------------------------------

@Composable
internal fun PrivatePage(motion: PageMotion) {
    val scheme = MaterialTheme.colorScheme
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_private_kicker),
            title = stringResource(R.string.welcome_private_title),
            text = stringResource(R.string.welcome_private_text),
        )
        PrivacyArt(modifier = Modifier.align(Alignment.CenterHorizontally).enter(motion, 3))
        Box(modifier = Modifier.enter(motion, 4)) {
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.PhoneAndroid,
                            title = stringResource(R.string.welcome_private_here),
                            summary = stringResource(R.string.welcome_private_here_text),
                            badge = MaterialShapes.Cookie9Sided,
                            colors = ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer),
                        )
                    },
                    { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.Lock,
                            title = stringResource(R.string.welcome_private_storage),
                            summary = stringResource(R.string.welcome_private_storage_text),
                            badge = MaterialShapes.Cookie4Sided,
                            colors = ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
                        )
                    },
                    { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.VisibilityOff,
                            title = stringResource(R.string.welcome_private_nothing),
                            summary = stringResource(R.string.welcome_private_nothing_text),
                            badge = MaterialShapes.SoftBurst,
                            colors = ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                        )
                    },
                    { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.welcome_private_open),
                            summary = stringResource(R.string.welcome_private_open_text),
                            badge = MaterialShapes.Gem,
                            colors = ToneColors(scheme.inverseSurface, scheme.inverseOnSurface),
                        )
                    },
                ),
            )
        }
    }
}

// -- 6. Look --------------------------------------------------------------------------------------------

@Composable
internal fun LookPage(motion: PageMotion, settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_look_kicker),
            title = stringResource(R.string.welcome_look_title),
            text = stringResource(R.string.welcome_look_text),
        )
        PhonePreview(modifier = Modifier.align(Alignment.CenterHorizontally).enter(motion, 3))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup().enter(motion, 4),
        ) {
            listOf(
                Triple(ThemeMode.SYSTEM, Icons.Rounded.BrightnessAuto, null),
                Triple(ThemeMode.LIGHT, Icons.Rounded.LightMode, true),
                Triple(ThemeMode.DARK, Icons.Rounded.DarkMode, false),
            ).forEach { (mode, icon, light) ->
                ThemeTile(
                    label = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        },
                    ),
                    icon = icon,
                    light = light,
                    selected = settings.themeMode == mode,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onChange { it.copy(themeMode = mode) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(modifier = Modifier.enter(motion, 5)) {
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsSwitchRow(
                            shape = shape,
                            icon = Icons.Rounded.Wallpaper,
                            title = stringResource(R.string.settings_dynamic),
                            summary = stringResource(if (dynamicColorAvailable) R.string.welcome_look_dynamic else R.string.settings_dynamic_unavailable),
                            checked = settings.dynamicColor && dynamicColorAvailable,
                            onCheckedChange = { on -> onChange { it.copy(dynamicColor = on) } },
                            badge = MaterialShapes.Cookie9Sided,
                            colors = ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer),
                            enabled = dynamicColorAvailable,
                        )
                    },
                    { shape ->
                        SettingsSwitchRow(
                            shape = shape,
                            icon = Icons.Rounded.InvertColors,
                            title = stringResource(R.string.settings_pure_black),
                            summary = stringResource(
                                if (settings.themeMode == ThemeMode.LIGHT) R.string.settings_pure_black_light else R.string.settings_pure_black_summary,
                            ),
                            checked = settings.pureBlack,
                            onCheckedChange = { on -> onChange { it.copy(pureBlack = on) } },
                            badge = MaterialShapes.Pill,
                            colors = ToneColors(scheme.inverseSurface, scheme.inverseOnSurface),
                            enabled = settings.themeMode != ThemeMode.LIGHT,
                        )
                    },
                ),
            )
        }
    }
}

/**
 * A theme to choose, as a tile with a tiny screen of it: chosen, the tile
 * fills with the primary container, its corners round off more and a tick
 * pops onto the screen.
 */
@Composable
private fun ThemeTile(
    label: String,
    icon: ImageVector,
    light: Boolean?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (selected) 32.dp else 22.dp, motion.defaultSpatialSpec(), label = "themeCorner")
    val back by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "themeBack")
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    val shape = RoundedCornerShape(corner)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .springPress(interaction, pressedScale = 0.93f)
            .clip(shape)
            .background(back)
            .then(if (selected) Modifier.border(3.dp, scheme.primary, shape) else Modifier)
            .selectable(selected = selected, interactionSource = interaction, indication = LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.82f)) {
            ThemeSwatch(light = light, accent = scheme.primary, modifier = Modifier.fillMaxSize())
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(),
                exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp).clip(CircleShape).background(scheme.primary)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized, color = content, maxLines = 1)
        }
    }
}

// -- 7. Feel -----------------------------------------------------------------------------------------------

@Composable
internal fun FeelPage(motion: PageMotion, settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_feel_kicker),
            title = stringResource(R.string.welcome_feel_title),
            text = stringResource(R.string.welcome_feel_text),
        )
        Box(modifier = Modifier.enter(motion, 3)) {
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsSwitchRow(
                            shape = shape,
                            icon = Icons.Rounded.Vibration,
                            title = stringResource(R.string.settings_haptics),
                            summary = stringResource(R.string.settings_haptics_summary),
                            checked = settings.haptics,
                            onCheckedChange = { on ->
                                // Turned on, it says so at once (the switch's own tick is off until now).
                                if (on) view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                onChange { it.copy(haptics = on) }
                            },
                            badge = MaterialShapes.SoftBurst,
                            colors = ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                        )
                    },
                ),
            )
        }
        // The pad: three taps to feel, each a shape that rings out when touched.
        val alpha by animateFloatAsState(if (settings.haptics) 1f else 0.4f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "padAlpha")
        Column(
            modifier = Modifier
                .enter(motion, 4)
                .graphicsLayer { this.alpha = alpha }
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.welcome_feel_pad), style = SettingsRowTitle, color = scheme.onSurface, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                PadButton(
                    label = stringResource(R.string.settings_haptics_tick),
                    icon = Icons.Rounded.LinearScale,
                    shape = MaterialShapes.Cookie9Sided,
                    tone = ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
                    sample = HapticSample.TICK,
                    enabled = settings.haptics,
                )
                PadButton(
                    label = stringResource(R.string.settings_haptics_tap),
                    icon = Icons.Rounded.TouchApp,
                    shape = MaterialShapes.SoftBurst,
                    tone = ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                    sample = HapticSample.TAP,
                    enabled = settings.haptics,
                )
                PadButton(
                    label = stringResource(R.string.settings_haptics_confirm),
                    icon = Icons.Rounded.Done,
                    shape = MaterialShapes.Clover4Leaf,
                    tone = ToneColors(scheme.primary, scheme.onPrimary),
                    sample = HapticSample.CONFIRM,
                    enabled = settings.haptics,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.welcome_feel_slide), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            var steps by remember { mutableFloatStateOf(4f) }
            Slider(
                value = steps,
                onValueChange = { value ->
                    if (value.roundToInt() != steps.roundToInt()) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    steps = value
                },
                valueRange = 0f..10f,
                steps = 9,
                enabled = settings.haptics,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A shape to touch: it morphs into a circle, squishes, rings out, and the phone gives [type]. */
@Composable
private fun PadButton(label: String, icon: ImageVector, shape: RoundedPolygon, tone: ToneColors, sample: HapticSample, enabled: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ring = remember { Animatable(1f) }
    val ringSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer {
                        val p = ring.value
                        val s = 0.7f + 0.45f * p
                        scaleX = s
                        scaleY = s
                        alpha = (1f - p) * 0.8f
                    }
                    .border(3.dp, tone.container, CircleShape),
            )
            MorphingContainer(
                from = shape,
                to = MaterialShapes.Circle,
                morphed = pressed,
                color = tone.container,
                modifier = Modifier
                    .springPress(interaction, pressedScale = 0.84f)
                    .size(72.dp)
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label) {
                        view.playHapticSample(sample)
                        scope.launch {
                            ring.snapTo(0f)
                            ring.animateTo(1f, ringSpec)
                        }
                    },
            ) {
                Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(30.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
    }
}

// -- 8. Exports -----------------------------------------------------------------------------------------------

@Composable
internal fun ExportsPage(
    motion: PageMotion,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    calibration: HardwareCalibration.State,
    canCalibrate: Boolean,
    onCalibrate: () -> Unit,
    onStopCalibration: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_exports_kicker),
            title = stringResource(R.string.welcome_exports_title),
            text = stringResource(R.string.welcome_exports_text),
        )
        Box(modifier = Modifier.enter(motion, 3)) {
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsSwitchRow(
                            shape = shape,
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.settings_save_copies),
                            summary = stringResource(if (PublicCopies.available) R.string.settings_save_copies_summary else R.string.settings_save_copies_unavailable),
                            checked = settings.saveCopies && PublicCopies.available,
                            onCheckedChange = { on -> onChange { it.copy(saveCopies = on) } },
                            badge = MaterialShapes.Clover4Leaf,
                            colors = ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
                            enabled = PublicCopies.available,
                        )
                    },
                ),
            )
        }
        Column(
            modifier = Modifier
                .enter(motion, 4)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_cpu_speed), style = SettingsRowTitle, color = scheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            Text(
                stringResource(R.string.welcome_exports_speed),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 10.dp),
            )
            OptionTileRow(
                options = listOf(
                    TileOption(CpuEncoderSpeed.FASTEST, stringResource(R.string.cpu_speed_fastest), Icons.Rounded.Bolt),
                    TileOption(CpuEncoderSpeed.FAST, stringResource(R.string.cpu_speed_fast), Icons.Rounded.Speed),
                    TileOption(CpuEncoderSpeed.BALANCED, stringResource(R.string.cpu_speed_balanced), Icons.Rounded.Balance),
                    TileOption(CpuEncoderSpeed.SMALLEST, stringResource(R.string.cpu_speed_smallest), Icons.Rounded.Compress),
                ),
                selected = settings.cpuSpeed,
                onSelect = { speed -> onChange { it.copy(cpuSpeed = speed) } },
            )
            Text(
                text = stringResource(speedHint(settings.cpuSpeed)),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceContainerHighest)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(modifier = Modifier.enter(motion, 5), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalibrationCard(calibration, canCalibrate, onCalibrate, onStopCalibration)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Rounded.Memory, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.welcome_exports_chip), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private fun speedHint(speed: CpuEncoderSpeed): Int = when (speed) {
    CpuEncoderSpeed.FASTEST -> R.string.cpu_speed_fastest_hint
    CpuEncoderSpeed.FAST -> R.string.cpu_speed_fast_hint
    CpuEncoderSpeed.BALANCED -> R.string.cpu_speed_balanced_hint
    CpuEncoderSpeed.SMALLEST -> R.string.cpu_speed_smallest_hint
}

private fun speedLabel(speed: CpuEncoderSpeed): Int = when (speed) {
    CpuEncoderSpeed.FASTEST -> R.string.cpu_speed_fastest
    CpuEncoderSpeed.FAST -> R.string.cpu_speed_fast
    CpuEncoderSpeed.BALANCED -> R.string.cpu_speed_balanced
    CpuEncoderSpeed.SMALLEST -> R.string.cpu_speed_smallest
}

// -- 9. Projects --------------------------------------------------------------------------------------------------

@Composable
internal fun ProjectsPage(
    motion: PageMotion,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    projects: List<ProjectCard>,
    onDeleteAllProjects: () -> Unit,
    onKeepProjects: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // Shown again from the settings, there may be projects already: nothing goes without asking.
    var confirmOff by remember { mutableStateOf(false) }
    var confirmLimit by remember { mutableStateOf<Int?>(null) }
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_projects_kicker),
            title = stringResource(R.string.welcome_projects_title),
            text = stringResource(R.string.welcome_projects_text),
        )
        ProjectsArt(active = motion.active, modifier = Modifier.enter(motion, 3))
        Box(modifier = Modifier.enter(motion, 4)) {
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsSwitchRow(
                            shape = shape,
                            icon = Icons.Rounded.History,
                            title = stringResource(R.string.settings_projects_keep),
                            summary = stringResource(if (settings.saveProjects) R.string.settings_projects_keep_on else R.string.settings_projects_keep_off),
                            checked = settings.saveProjects,
                            onCheckedChange = { on ->
                                when {
                                    on -> onChange { it.copy(saveProjects = true) }
                                    projects.isEmpty() -> onChange { it.copy(saveProjects = false) }
                                    else -> confirmOff = true
                                }
                            },
                            badge = MaterialShapes.Cookie7Sided,
                            colors = ToneColors(scheme.secondary, scheme.onSecondary),
                        )
                    },
                ),
            )
        }
        AnimatedVisibility(
            visible = settings.saveProjects,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            modifier = Modifier.enter(motion, 5),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.settings_projects_limit), style = SettingsRowTitle, color = scheme.onSurface, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                ConnectedChoiceGroup(
                    options = AppSettings.PROJECTS_LIMITS,
                    selected = settings.projectsLimit,
                    label = { it.toString() },
                    onSelect = { limit ->
                        if (limit < projects.size) confirmLimit = limit else onChange { it.copy(projectsLimit = limit) }
                    },
                )
                Text(
                    stringResource(R.string.settings_projects_limit_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 8.dp).enter(motion, 6)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.welcome_projects_private), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
    if (confirmOff) {
        ConfirmDialog(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.settings_projects_off_title),
            text = stringResource(R.string.settings_projects_off_text, Formatter.formatShortFileSize(context, projects.sumOf { it.bytes })),
            confirm = stringResource(R.string.action_turn_off),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirmOff = false
                onChange { it.copy(saveProjects = false) }
                onDeleteAllProjects()
            },
            onDismiss = { confirmOff = false },
        )
    }
    confirmLimit?.let { limit ->
        val dropped = projects.drop(limit)
        ConfirmDialog(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.settings_projects_limit_title, limit),
            text = pluralStringResource(
                R.plurals.settings_projects_limit_text,
                dropped.size,
                dropped.size,
                Formatter.formatShortFileSize(context, dropped.sumOf { it.bytes }),
            ),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirmLimit = null
                onChange { it.copy(projectsLimit = limit) }
                onKeepProjects(limit)
            },
            onDismiss = { confirmLimit = null },
        )
    }
}

// -- 10. Notifications -------------------------------------------------------------------------------------------------

@Composable
internal fun NotifyPage(
    motion: PageMotion,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    notifications: NotificationAccess,
) {
    val scheme = MaterialTheme.colorScheme
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_notify_kicker),
            title = stringResource(R.string.welcome_notify_title),
            text = stringResource(R.string.welcome_notify_text),
        )
        NotificationArt(modifier = Modifier.enter(motion, 3))
        PermissionCard(notifications, modifier = Modifier.enter(motion, 4))
        Box(modifier = Modifier.enter(motion, 5)) {
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsSwitchRow(
                            shape = shape,
                            icon = Icons.Rounded.NotificationsActive,
                            title = stringResource(R.string.settings_live_updates),
                            summary = stringResource(if (liveUpdatesAvailable) R.string.settings_live_updates_summary else R.string.settings_live_updates_unavailable),
                            checked = settings.liveUpdates && liveUpdatesAvailable,
                            onCheckedChange = { on -> onChange { it.copy(liveUpdates = on) } },
                            badge = MaterialShapes.Flower,
                            colors = ToneColors(scheme.primary, scheme.onPrimary),
                            enabled = liveUpdatesAvailable,
                        )
                    },
                ),
            )
        }
    }
}

/** Whether the app may show notifications, and the ways to let it. */
internal class NotificationAccess(
    val allowed: Boolean,
    /** Asked, and the answer was no: only Android's settings can change it now. */
    val refused: Boolean,
    val onAllow: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Asking for notifications: a badge that blooms into a twelve-sided cookie
 * with a tick once they are allowed, what they are for, and the one button
 * that gets them (asking, or Android's settings after a no).
 */
@Composable
private fun PermissionCard(access: NotificationAccess, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tone = if (access.allowed) ToneColors(scheme.primary, scheme.onPrimary) else ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Cookie12Sided,
                morphed = access.allowed,
                color = tone.container,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    when {
                        access.allowed -> Icons.Rounded.CheckCircle
                        access.refused -> Icons.Rounded.NotificationsOff
                        else -> Icons.Rounded.Notifications
                    },
                    contentDescription = null,
                    tint = tone.content,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    stringResource(if (access.allowed) R.string.welcome_notify_allowed else R.string.welcome_notify_ask),
                    style = SettingsRowTitle,
                    color = scheme.onSurface,
                )
                Text(
                    stringResource(
                        when {
                            access.allowed -> R.string.welcome_notify_allowed_text
                            access.refused -> R.string.welcome_notify_refused_text
                            else -> R.string.welcome_notify_ask_text
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            visible = !access.allowed,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Button(
                onClick = if (access.refused) access.onOpenSettings else access.onAllow,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(if (access.refused) R.string.welcome_notify_open_settings else R.string.welcome_notify_allow),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
        }
    }
}

// -- 11. All set ---------------------------------------------------------------------------------------------------------

/** One choice made on the welcome, as a chip on the last page; a tap goes back to its page. */
private class Summary(val icon: ImageVector, val text: String, val page: WelcomePage, val on: Boolean)

@Composable
internal fun ReadyPage(
    motion: PageMotion,
    settings: AppSettings,
    hardwareWorks: Boolean,
    notificationsAllowed: Boolean,
    smartReady: Boolean,
    openSmart: Boolean,
    onJump: (WelcomePage) -> Unit,
) {
    val summary = listOf(
        Summary(
            when (settings.themeMode) {
                ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                ThemeMode.LIGHT -> Icons.Rounded.LightMode
                ThemeMode.DARK -> Icons.Rounded.DarkMode
            },
            stringResource(
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> R.string.welcome_ready_theme_system
                    ThemeMode.LIGHT -> R.string.welcome_ready_theme_light
                    ThemeMode.DARK -> R.string.welcome_ready_theme_dark
                },
            ),
            WelcomePage.LOOK,
            true,
        ),
        Summary(
            Icons.Rounded.Palette,
            stringResource(if (settings.dynamicColor && dynamicColorAvailable) R.string.welcome_ready_colors_wallpaper else R.string.welcome_ready_colors_teal),
            WelcomePage.LOOK,
            true,
        ),
        Summary(
            Icons.Rounded.Vibration,
            stringResource(if (settings.haptics) R.string.welcome_ready_haptics_on else R.string.welcome_ready_haptics_off),
            WelcomePage.FEEL,
            settings.haptics,
        ),
        Summary(
            Icons.Rounded.Download,
            stringResource(if (settings.saveCopies && PublicCopies.available) R.string.welcome_ready_copies_on else R.string.welcome_ready_copies_off),
            WelcomePage.EXPORTS,
            settings.saveCopies && PublicCopies.available,
        ),
        Summary(
            Icons.Rounded.Speed,
            stringResource(R.string.welcome_ready_speed, stringResource(speedLabel(settings.cpuSpeed))),
            WelcomePage.EXPORTS,
            true,
        ),
        Summary(
            Icons.Rounded.Memory,
            stringResource(if (settings.hardwareVideo && hardwareWorks) R.string.welcome_ready_chip_on else R.string.welcome_ready_chip_off),
            WelcomePage.EXPORTS,
            settings.hardwareVideo && hardwareWorks,
        ),
        Summary(
            Icons.Rounded.Schedule,
            if (settings.saveProjects) stringResource(R.string.welcome_ready_projects, settings.projectsLimit) else stringResource(R.string.welcome_ready_projects_off),
            WelcomePage.PROJECTS,
            settings.saveProjects,
        ),
        Summary(
            Icons.Rounded.Notifications,
            stringResource(if (notificationsAllowed) R.string.welcome_ready_notify_on else R.string.welcome_ready_notify_off),
            WelcomePage.NOTIFY,
            notificationsAllowed,
        ),
        Summary(
            Icons.Rounded.AutoAwesome,
            stringResource(
                when {
                    smartReady -> R.string.welcome_ready_smart_ready
                    openSmart -> R.string.welcome_ready_smart_next
                    else -> R.string.welcome_ready_smart_later
                },
            ),
            WelcomePage.SMART,
            smartReady || openSmart,
        ),
    )
    PageColumn {
        CelebrationArt(active = motion.active, modifier = Modifier.align(Alignment.CenterHorizontally).enter(motion, 0))
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_ready_kicker),
            title = stringResource(R.string.welcome_ready_title),
            text = stringResource(R.string.welcome_ready_text),
            centered = true,
            firstOrder = 1,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().enter(motion, 4),
        ) {
            summary.forEach { item -> SummaryChip(item, onClick = { onJump(item.page) }) }
        }
        Text(
            stringResource(R.string.welcome_ready_tap),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().enter(motion, 5),
        )
    }
}

/** A choice as a chip: on in the secondary container, off quieter; it squishes and goes to its page. */
@Composable
private fun SummaryChip(item: Summary, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val container = if (item.on) scheme.secondaryContainer else scheme.surfaceContainerHigh
    val content = if (item.on) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.92f)
            .height(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(start = 12.dp, end = 14.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(item.text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}
