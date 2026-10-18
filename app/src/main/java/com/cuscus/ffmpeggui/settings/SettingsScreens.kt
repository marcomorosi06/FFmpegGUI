@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import com.cuscus.ffmpeggui.ui.adaptive.WindowLayout
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import java.util.Locale
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import com.cuscus.ffmpeggui.media.HardwareCalibration
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.HapticSample
import com.cuscus.ffmpeggui.playHapticSample
import com.cuscus.ffmpeggui.ToneColors
import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.cuscus.ffmpeggui.categoryColors
import com.cuscus.ffmpeggui.categoryIcon
import com.cuscus.ffmpeggui.categoryShape
import com.cuscus.ffmpeggui.projects.ProjectCard
import com.cuscus.ffmpeggui.relativeTime
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.export.PublicCopies
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.theme.dynamicColorAvailable

/*
 * The settings: a page of categories, each opening a page of its own, then the
 * project (About, the source code, Ko-fi) and a reset. Every category has its
 * MaterialShapes shape and colour pair, the same on the list and on its page.
 */

/** The pages the settings are split into. */
enum class SettingsCategory(val key: String) {
    APPEARANCE("appearance"),
    VIBRATION("vibration"),
    EXPORTS("exports"),
    PROJECTS("projects"),
    NOTIFICATIONS("notifications"),
    SMART("smart"),
}

private class CategoryLook(val icon: ImageVector, val shape: RoundedPolygon, val title: Int)

private fun look(category: SettingsCategory): CategoryLook = when (category) {
    SettingsCategory.APPEARANCE -> CategoryLook(Icons.Rounded.Palette, MaterialShapes.Cookie9Sided, R.string.settings_appearance)
    SettingsCategory.VIBRATION -> CategoryLook(Icons.Rounded.Vibration, MaterialShapes.SoftBurst, R.string.settings_vibration)
    SettingsCategory.EXPORTS -> CategoryLook(Icons.Rounded.SaveAlt, MaterialShapes.Clover4Leaf, R.string.settings_exports)
    SettingsCategory.PROJECTS -> CategoryLook(Icons.Rounded.History, MaterialShapes.Cookie7Sided, R.string.settings_projects)
    SettingsCategory.NOTIFICATIONS -> CategoryLook(Icons.Rounded.Notifications, MaterialShapes.Flower, R.string.settings_notifications)
    SettingsCategory.SMART -> CategoryLook(Icons.Rounded.AutoAwesome, MaterialShapes.Clover8Leaf, R.string.settings_smart)
}

@Composable
private fun tone(category: SettingsCategory): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        SettingsCategory.APPEARANCE -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        SettingsCategory.VIBRATION -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        SettingsCategory.EXPORTS -> ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        SettingsCategory.PROJECTS -> ToneColors(scheme.secondary, scheme.onSecondary)
        SettingsCategory.NOTIFICATIONS -> ToneColors(scheme.primary, scheme.onPrimary)
        SettingsCategory.SMART -> ToneColors(scheme.tertiary, scheme.onTertiary)
    }
}

/**
 * A page of the settings: scrolls, with the room every page has around it; on a
 * wide screen a column of a readable width in the middle (it scrolls from anywhere).
 */
@Composable
private fun SettingsPage(content: @Composable () -> Unit) {
    val side = LocalWindowLayout.current.sidePadding(WindowLayout.READABLE_WIDTH)
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = side, vertical = 16.dp),
    ) {
        content()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OutLink() {
    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
}

/** The first page: the categories with what they are set to now, then the project and a reset. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    /** The speech model of the smart features is on the phone and checked. */
    speechReady: Boolean = false,
    /** The cut-out model of the smart features is on the phone and checked. */
    cutoutReady: Boolean = false,
    versionName: String,
    onOpenCategory: (SettingsCategory) -> Unit,
    onOpenAbout: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    /** Shows the welcome again. */
    onOpenWelcome: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var confirmReset by remember { mutableStateOf(false) }
    SettingsPage {
        SettingsHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            icon = Icons.Rounded.Tune,
            shape = MaterialShapes.Cookie12Sided,
        )
        SettingsGroup(
            title = null,
            rows = SettingsCategory.entries.map { category ->
                val summary = categorySummary(category, settings, speechReady, cutoutReady)
                val categoryLook = look(category)
                val row: @Composable (Shape) -> Unit = { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = categoryLook.icon,
                        title = stringResource(categoryLook.title),
                        summary = summary,
                        badge = categoryLook.shape,
                        colors = tone(category),
                        onClick = { onOpenCategory(category) },
                        trailing = { Chevron() },
                    )
                }
                row
            },
        )
        SettingsGroup(
            title = stringResource(R.string.about_project),
            rows = listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.about_version, versionName),
                        badge = MaterialShapes.Sunny,
                        colors = ToneColors(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.colorScheme.inverseOnSurface),
                        onClick = onOpenAbout,
                        trailing = { Chevron() },
                    )
                },
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.WavingHand,
                        title = stringResource(R.string.settings_welcome),
                        summary = stringResource(R.string.settings_welcome_summary),
                        badge = MaterialShapes.Flower,
                        colors = ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                        onClick = onOpenWelcome,
                        trailing = { Chevron() },
                    )
                },
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.settings_github),
                        summary = stringResource(R.string.settings_github_summary),
                        badge = MaterialShapes.Gem,
                        onClick = { openLink(context, ProjectLinks.GITHUB) },
                        trailing = { OutLink() },
                    )
                },
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.LocalCafe,
                        title = stringResource(R.string.settings_kofi),
                        summary = stringResource(R.string.settings_kofi_summary),
                        badge = MaterialShapes.Cookie6Sided,
                        colors = ToneColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                        onClick = { openLink(context, ProjectLinks.KOFI) },
                        trailing = { OutLink() },
                    )
                },
            ),
        )
        SettingsGroup(
            title = null,
            rows = listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.RestartAlt,
                        title = stringResource(R.string.settings_reset),
                        summary = stringResource(R.string.settings_reset_summary),
                        badge = MaterialShapes.Cookie4Sided,
                        colors = ToneColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer),
                        onClick = { confirmReset = true },
                    )
                },
            ),
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            icon = Icons.Rounded.RestartAlt,
            title = stringResource(R.string.settings_reset_title),
            text = stringResource(R.string.settings_reset_text),
            confirm = stringResource(R.string.action_reset),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                confirmReset = false
                onReset()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** What a category is set to now, in a few words. */
@Composable
private fun categorySummary(category: SettingsCategory, settings: AppSettings, speechReady: Boolean, cutoutReady: Boolean): String = when (category) {
    SettingsCategory.APPEARANCE -> stringResource(
        R.string.settings_appearance_summary,
        stringResource(themeLabel(settings.themeMode)),
        stringResource(if (settings.dynamicColor && dynamicColorAvailable) R.string.settings_colors_wallpaper else R.string.settings_colors_teal),
    )
    SettingsCategory.VIBRATION -> stringResource(if (settings.haptics) R.string.settings_on else R.string.settings_off)
    SettingsCategory.EXPORTS -> stringResource(
        if (settings.saveCopies && PublicCopies.available) R.string.settings_exports_summary_copies else R.string.settings_exports_summary_app,
    )
    SettingsCategory.PROJECTS ->
        if (settings.saveProjects) {
            stringResource(R.string.settings_projects_summary_on, settings.projectsLimit)
        } else {
            stringResource(R.string.settings_projects_summary_off)
        }
    SettingsCategory.NOTIFICATIONS -> stringResource(
        if (settings.liveUpdates && liveUpdatesAvailable) R.string.settings_notifications_summary_live else R.string.settings_notifications_summary_plain,
    )
    SettingsCategory.SMART -> stringResource(
        when {
            speechReady && cutoutReady -> R.string.settings_smart_summary_both
            speechReady -> R.string.settings_smart_summary_ready
            cutoutReady -> R.string.settings_smart_summary_cutout
            else -> R.string.settings_smart_summary_missing
        },
    )
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/** Whether this Android has Live Updates (16 and later). */
internal val liveUpdatesAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

/** One category's page. */
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    exportsUsage: ExportsUsage?,
    canDeleteExports: Boolean,
    onDeleteExports: () -> Unit,
    onBack: () -> Unit,
    /** The recent projects, null while they are read. */
    projects: List<ProjectCard>? = null,
    onDeleteProject: (String) -> Unit = {},
    onDeleteAllProjects: () -> Unit = {},
    /** Deletes all but the newest projects, the number given (the limit was lowered). */
    onKeepProjects: (Int) -> Unit = {},
    /** The video chip's test: where it is, and what it found. */
    calibration: HardwareCalibration.State = HardwareCalibration.State.Untested,
    onCalibrate: () -> Unit = {},
    onStopCalibration: () -> Unit = {},
    /** False while an export runs (it uses FFmpeg and the chip). */
    canCalibrate: Boolean = true,
    /** The smart features (the speech model and its test). */
    smart: SmartFeaturesUi? = null,
) {
    val categoryLook = look(category)
    SettingsPage {
        SettingsHeader(
            title = stringResource(categoryLook.title),
            onBack = onBack,
            icon = categoryLook.icon,
            shape = categoryLook.shape,
            colors = tone(category),
        )
        when (category) {
            SettingsCategory.APPEARANCE -> AppearanceSettings(settings, onChange)
            SettingsCategory.VIBRATION -> VibrationSettings(settings, onChange)
            SettingsCategory.EXPORTS -> ExportSettings(
                settings, onChange, exportsUsage, canDeleteExports, onDeleteExports,
                calibration, onCalibrate, onStopCalibration, canCalibrate,
            )
            SettingsCategory.PROJECTS -> ProjectSettings(settings, onChange, projects, onDeleteProject, onDeleteAllProjects, onKeepProjects)
            SettingsCategory.NOTIFICATIONS -> NotificationSettings(settings, onChange)
            SettingsCategory.SMART -> if (smart != null) SmartSettings(smart)
        }
    }
}

// -- Appearance ---------------------------------------------------------------------------

@Composable
private fun AppearanceSettings(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // The theme: three tiles that morph when chosen.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = SettingsRowTitle,
            color = scheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        OptionTileRow(
            options = listOf(
                TileOption(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system), Icons.Rounded.BrightnessAuto),
                TileOption(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode),
                TileOption(ThemeMode.DARK, stringResource(R.string.settings_theme_dark), Icons.Rounded.DarkMode),
            ),
            selected = settings.themeMode,
            onSelect = { mode -> onChange { it.copy(themeMode = mode) } },
        )
    }
    // The colours in use, live: they change as the switches below do.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_palette_preview),
            style = SettingsRowTitle,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ColorDot(scheme.primary, 28.dp)
        ColorDot(scheme.secondary, 28.dp)
        ColorDot(scheme.tertiary, 28.dp)
        ColorDot(scheme.primaryContainer, 28.dp)
        ColorDot(scheme.surfaceContainerHighest, 28.dp)
    }
    SettingsGroup(
        title = null,
        rows = listOf<@Composable (Shape) -> Unit>(
            { shape ->
                SettingsSwitchRow(
                    shape = shape,
                    icon = Icons.Rounded.Wallpaper,
                    title = stringResource(R.string.settings_dynamic),
                    summary = stringResource(
                        when {
                            !dynamicColorAvailable -> R.string.settings_dynamic_unavailable
                            settings.dynamicColor -> R.string.settings_dynamic_on
                            else -> R.string.settings_dynamic_off
                        },
                    ),
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

// -- Vibration ----------------------------------------------------------------------------

@Composable
private fun VibrationSettings(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
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
                        if (on) {
                            view.performHapticFeedback(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    android.view.HapticFeedbackConstants.CONFIRM
                                } else {
                                    android.view.HapticFeedbackConstants.VIRTUAL_KEY
                                },
                            )
                        }
                        onChange { it.copy(haptics = on) }
                    },
                    badge = MaterialShapes.SoftBurst,
                    colors = ToneColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                )
            },
        ),
    )
    // Try them: each button gives one of the taps the app uses.
    AnimatedVisibility(
        visible = settings.haptics,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_haptics_try), style = SettingsRowTitle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    R.string.settings_haptics_tick to HapticSample.TICK,
                    R.string.settings_haptics_tap to HapticSample.TAP,
                    R.string.settings_haptics_confirm to HapticSample.CONFIRM,
                ).forEach { (label, sample) ->
                    Button(
                        onClick = { view.playHapticSample(sample) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                    }
                }
            }
        }
    }
}

// -- Exports and storage ------------------------------------------------------------------

@Composable
private fun ExportSettings(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    usage: ExportsUsage?,
    canDelete: Boolean,
    onDelete: () -> Unit,
    calibration: HardwareCalibration.State,
    onCalibrate: () -> Unit,
    onStopCalibration: () -> Unit,
    canCalibrate: Boolean,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val result = (calibration as? HardwareCalibration.State.Done)?.result
    val works = result is HardwareCalibration.Result.Works
    // A test that has just passed turns the chip on; one that has just failed, off.
    var wasRunning by remember { mutableStateOf(calibration is HardwareCalibration.State.Running) }
    LaunchedEffect(calibration) {
        if (wasRunning && calibration is HardwareCalibration.State.Done) {
            haptics.performHapticFeedback(if (works) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            onChange { it.copy(hardwareVideo = works) }
        }
        wasRunning = calibration is HardwareCalibration.State.Running
    }
    SettingsGroup(
        title = null,
        rows = listOf<@Composable (Shape) -> Unit>(
            { shape ->
                SettingsSwitchRow(
                    shape = shape,
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.settings_hardware_video),
                    summary = stringResource(
                        when {
                            result is HardwareCalibration.Result.Unusable -> R.string.settings_hardware_video_unusable
                            !works -> R.string.settings_hardware_video_untested
                            settings.hardwareVideo -> R.string.settings_hardware_video_on
                            else -> R.string.settings_hardware_video_off
                        },
                    ),
                    // Only once the test below has shown that the chip makes correct videos.
                    checked = settings.hardwareVideo && works,
                    onCheckedChange = { on -> onChange { it.copy(hardwareVideo = on) } },
                    badge = MaterialShapes.Cookie12Sided,
                    colors = ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer),
                    enabled = works,
                )
            },
        ),
    )
    CalibrationCard(calibration, canCalibrate, onCalibrate, onStopCalibration)
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
    // How hard the processor works, when it encodes a video.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_cpu_speed),
            style = SettingsRowTitle,
            color = scheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = stringResource(if (settings.hardwareVideo) R.string.settings_cpu_speed_fallback else R.string.settings_cpu_speed_always),
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
            text = stringResource(
                when (settings.cpuSpeed) {
                    CpuEncoderSpeed.FASTEST -> R.string.cpu_speed_fastest_hint
                    CpuEncoderSpeed.FAST -> R.string.cpu_speed_fast_hint
                    CpuEncoderSpeed.BALANCED -> R.string.cpu_speed_balanced_hint
                    CpuEncoderSpeed.SMALLEST -> R.string.cpu_speed_smallest_hint
                },
            ),
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
    val sizeText = usage?.let { Formatter.formatShortFileSize(context, it.bytes) }
    SettingsGroup(
        title = stringResource(R.string.settings_storage),
        rows = listOf<@Composable (Shape) -> Unit>(
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Storage,
                    title = when {
                        usage == null -> "\u2026"
                        usage.files == 0 -> stringResource(R.string.settings_storage_empty)
                        else -> pluralStringResource(R.plurals.settings_storage_files, usage.files, usage.files, sizeText ?: "")
                    },
                    summary = stringResource(R.string.settings_storage_hint),
                    badge = MaterialShapes.Cookie6Sided,
                )
            },
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.DeleteSweep,
                    title = stringResource(R.string.settings_storage_delete),
                    summary = stringResource(if (canDelete) R.string.settings_storage_delete_summary else R.string.settings_storage_busy),
                    badge = MaterialShapes.Cookie4Sided,
                    colors = ToneColors(scheme.errorContainer, scheme.onErrorContainer),
                    enabled = canDelete && (usage?.files ?: 0) > 0,
                    onClick = { confirmDelete = true },
                )
            },
        ),
    )
    if (confirmDelete) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteSweep,
            title = stringResource(R.string.settings_storage_delete_title),
            text = stringResource(R.string.settings_storage_delete_text, sizeText ?: ""),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * The video chip's test: a badge that turns while it runs (a loading indicator in
 * it) and settles on a tick or a stop sign, what it is doing or found in words, a
 * wavy bar of its steps, and the button that starts or stops it.
 */
@Composable
internal fun CalibrationCard(
    state: HardwareCalibration.State,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val running = state as? HardwareCalibration.State.Running
    val result = (state as? HardwareCalibration.State.Done)?.result
    val tone = when (result) {
        is HardwareCalibration.Result.Works -> ToneColors(scheme.primary, scheme.onPrimary)
        is HardwareCalibration.Result.Unusable -> ToneColors(scheme.errorContainer, scheme.onErrorContainer)
        null -> ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
    val spin by animateFloatAsState(if (running != null) 360f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "calibrationTurn")
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
                to = if (result is HardwareCalibration.Result.Works) MaterialShapes.Cookie12Sided else MaterialShapes.Cookie4Sided,
                morphed = result != null,
                color = tone.container,
                modifier = Modifier.graphicsLayer { rotationZ = spin }.size(52.dp),
            ) {
                if (running != null) {
                    LoadingIndicator(color = tone.content, modifier = Modifier.size(36.dp))
                } else {
                    Icon(
                        when (result) {
                            is HardwareCalibration.Result.Works -> Icons.Rounded.CheckCircle
                            is HardwareCalibration.Result.Unusable -> Icons.Rounded.Block
                            null -> Icons.Rounded.Science
                        },
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.graphicsLayer { rotationZ = -spin }.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.calibration_title), style = SettingsRowTitle, color = scheme.onSurface)
                Text(
                    text = when {
                        running != null -> stringResource(
                            R.string.calibration_trying,
                            running.trying.codecName,
                            running.trying.pixelFormat.uppercase(Locale.ROOT),
                            running.trying.align,
                        )
                        result is HardwareCalibration.Result.Works -> stringResource(
                            R.string.calibration_works,
                            result.encoder.codecName,
                            result.encoder.pixelFormat.uppercase(Locale.ROOT),
                            result.encoder.widthAlign,
                            String.format(Locale.ROOT, "%.0f", result.psnrDb),
                        )
                        result is HardwareCalibration.Result.Unusable -> if (result.tried > 0) {
                            pluralStringResource(R.plurals.calibration_unusable, result.tried, result.tried)
                        } else {
                            stringResource(R.string.calibration_no_chip)
                        }
                        else -> stringResource(R.string.calibration_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (running != null) {
            Spacer(modifier = Modifier.height(14.dp))
            LinearWavyProgressIndicator(
                progress = { running.step / running.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.calibration_step, running.step, running.total),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (running != null) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    onStop()
                },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.calibration_stop), style = MaterialTheme.typography.titleSmallEmphasized)
            }
        } else {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onStart()
                },
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    stringResource(if (result == null) R.string.calibration_start else R.string.calibration_again),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            if (!enabled) {
                Text(
                    text = stringResource(R.string.calibration_busy),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        }
    }
}

// -- Recent projects ----------------------------------------------------------------------

/**
 * The recent projects: the switch (turning it off deletes what is kept, after
 * asking); how many to keep; every project with its size, each deletable, and
 * all of them at once; then a quiet line on where they are kept, which opens
 * to say exactly what is kept.
 */
@Composable
private fun ProjectSettings(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    projects: List<ProjectCard>?,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onKeep: (Int) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    var confirmOff by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProjectCard?>(null) }
    var confirmLimit by remember { mutableStateOf<Int?>(null) }
    val all = projects.orEmpty()
    val totalBytes = all.sumOf { it.bytes }
    val totalText = Formatter.formatShortFileSize(context, totalBytes)

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
                            all.isEmpty() -> onChange { it.copy(saveProjects = false) }
                            else -> confirmOff = true
                        }
                    },
                    badge = MaterialShapes.Cookie7Sided,
                    colors = ToneColors(scheme.secondary, scheme.onSecondary),
                )
            },
        ),
    )

    AnimatedVisibility(
        visible = settings.saveProjects,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_projects_limit),
                style = SettingsRowTitle,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            ConnectedChoiceGroup(
                options = AppSettings.PROJECTS_LIMITS,
                selected = settings.projectsLimit,
                label = { it.toString() },
                onSelect = { limit ->
                    // Fewer than are kept now: the older ones would go, so it asks first.
                    if (limit < all.size) confirmLimit = limit else onChange { it.copy(projectsLimit = limit) }
                },
            )
            Text(
                text = stringResource(R.string.settings_projects_limit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }

    SettingsGroup(
        title = stringResource(R.string.settings_projects_saved),
        rows = buildList<@Composable (Shape) -> Unit> {
            add { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Storage,
                    title = when {
                        projects == null -> "\u2026"
                        all.isEmpty() -> stringResource(R.string.settings_projects_none)
                        else -> pluralStringResource(R.plurals.settings_projects_count, all.size, all.size, totalText)
                    },
                    summary = all.sumOf { it.copiedFiles }.takeIf { it > 0 }?.let {
                        pluralStringResource(R.plurals.projects_copied, it, it)
                    },
                    badge = MaterialShapes.Cookie6Sided,
                )
            }
            all.forEach { project ->
                add { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = categoryIcon(project.format.category),
                        title = project.name,
                        summary = stringResource(
                            R.string.settings_projects_row,
                            Formatter.formatShortFileSize(context, project.bytes),
                            relativeTime(project.editedAtMs),
                        ) + if (project.missingFiles > 0) {
                            "\n" + pluralStringResource(R.plurals.projects_missing, project.missingFiles, project.missingFiles)
                        } else {
                            ""
                        },
                        badge = categoryShape(project.format.category),
                        colors = categoryColors(project.format.category),
                        trailing = {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    confirmDelete = project
                                },
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.projects_delete), tint = scheme.error)
                            }
                        },
                    )
                }
            }
            add { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.DeleteSweep,
                    title = stringResource(R.string.settings_projects_delete_all),
                    summary = stringResource(R.string.settings_projects_delete_all_summary),
                    badge = MaterialShapes.Cookie4Sided,
                    colors = ToneColors(scheme.errorContainer, scheme.onErrorContainer),
                    enabled = all.isNotEmpty(),
                    onClick = { confirmDeleteAll = true },
                )
            }
        },
    )

    ProjectsNote()

    if (confirmOff) {
        ConfirmDialog(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.settings_projects_off_title),
            text = stringResource(R.string.settings_projects_off_text, totalText),
            confirm = stringResource(R.string.action_turn_off),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirmOff = false
                onChange { it.copy(saveProjects = false) }
                onDeleteAll()
            },
            onDismiss = { confirmOff = false },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteSweep,
            title = stringResource(R.string.settings_projects_delete_all_title),
            text = stringResource(R.string.settings_projects_delete_all_text, totalText),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirmDeleteAll = false
                onDeleteAll()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
    confirmLimit?.let { limit ->
        val dropped = all.drop(limit)
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
                onKeep(limit)
            },
            onDismiss = { confirmLimit = null },
        )
    }
    confirmDelete?.let { project ->
        ConfirmDialog(
            icon = Icons.Rounded.Delete,
            title = stringResource(R.string.projects_delete_title),
            text = stringResource(R.string.projects_delete_text, Formatter.formatShortFileSize(context, project.bytes)),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                confirmDelete = null
                onDelete(project.id)
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

/**
 * Where the projects are kept, in one line under the list, and what exactly is
 * kept when it is tapped open.
 */
@Composable
private fun ProjectsNote() {
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "noteChevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = stringResource(if (open) R.string.action_collapse else R.string.action_expand), role = Role.Button) { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.settings_projects_note),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 40.dp, end = 12.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.settings_projects_privacy_what), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_projects_privacy_files), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

// -- Notifications ------------------------------------------------------------------------

@Composable
private fun NotificationSettings(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
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
            { shape ->
                SettingsRow(
                    shape = shape,
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.settings_notification_system),
                    summary = stringResource(R.string.settings_notification_system_summary),
                    badge = MaterialShapes.Cookie9Sided,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    trailing = { OutLink() },
                )
            },
        ),
    )
}

// -- Dialogs ------------------------------------------------------------------------------

/**
 * Asks before something that cannot be undone, in the Material 3 Expressive
 * way: a very round sheet that springs in, a big badge (a cookie that morphs
 * into a four-sided one and turns a little as the dialog opens, in the error
 * colours), the question set in the display face, what happens, and two wide
 * buttons that squish under the finger: keep things as they are, or go ahead.
 */
@Composable
internal fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val scale by animateFloatAsState(if (shown) 1f else 0.82f, motion.defaultSpatialSpec(), label = "dialogScale")
    val fade by animateFloatAsState(if (shown) 1f else 0f, motion.defaultEffectsSpec(), label = "dialogFade")
    val turn by animateFloatAsState(if (shown) 0f else -45f, motion.slowSpatialSpec(), label = "dialogBadgeTurn")
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
        ) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Cookie4Sided,
                morphed = shown,
                color = scheme.errorContainer,
                modifier = Modifier
                    .graphicsLayer { rotationZ = turn }
                    .size(80.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.onErrorContainer,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = -turn }
                        .size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = SettingsGroupTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
                }
                Button(
                    onClick = onConfirm,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(confirm, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
                }
            }
        }
    }
}
