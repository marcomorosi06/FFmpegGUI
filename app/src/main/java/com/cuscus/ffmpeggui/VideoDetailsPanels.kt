@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.preview.FinishingTouches
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * The video editor's Details tool: the fine detail of the picture, for a
 * clip or for the whole video, as the photo editor's Details. Five settings,
 * each a round chip in a row (the chosen one a cookie, a dot on those in use):
 *
 *  - Denoise (hqdn3d), with its close-up comparison (see DenoisePanels);
 *  - Sharpness (unsharp): sharper one way, softer the other;
 *  - Blur (gblur);
 *  - Vignette (vignette): the corners darker;
 *  - Grain (noise): a film's grain, new every frame.
 *
 * Each has levels in a connected button group and a slider for the values in
 * between, over a small scene drawn in the theme's colours that shows what it
 * does (the swatch). The preview shows sharpness, blur, vignette and grain
 * live (see FinishingTouchesPreview), as far as the phone allows: the panel
 * says when it cannot.
 */

/** The settings of the Details tool, in the order the export runs them (denoise first). */
internal enum class DetailsSection(@StringRes val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DENOISE(R.string.details_denoise, Icons.Rounded.CleaningServices),
    SHARPNESS(R.string.details_sharpness, Icons.Rounded.Details),
    BLUR(R.string.details_blur, Icons.Rounded.BlurOn),
    VIGNETTE(R.string.details_vignette, Icons.Rounded.Vignette),
    GRAIN(R.string.details_grain, Icons.Rounded.Grain),
    ;

    /** Whether [color] uses this setting (its chip wears a dot). */
    fun inUse(color: ColorSettings): Boolean = when (this) {
        DENOISE -> color.denoise > 0f
        SHARPNESS -> color.sharpness != 0f
        BLUR -> color.gaussianBlur > 0f
        VIGNETTE -> color.vignette > 0f
        GRAIN -> color.filmGrain > 0f
    }
}

/** A level of a setting, offered in one tap; the slider gives the values in between. */
private class DetailLevel(@StringRes val labelRes: Int, val value: Float)

private val SHARPNESS_LEVELS = listOf(
    DetailLevel(R.string.details_level_softer, -0.5f),
    DetailLevel(R.string.details_level_off, 0f),
    DetailLevel(R.string.details_level_crisp, 0.3f),
    DetailLevel(R.string.details_level_sharp, 0.6f),
    DetailLevel(R.string.details_level_very_sharp, 1f),
)

private val BLUR_LEVELS = listOf(
    DetailLevel(R.string.details_level_off, 0f),
    DetailLevel(R.string.details_level_light, 2f),
    DetailLevel(R.string.details_level_medium, 6f),
    DetailLevel(R.string.details_level_strong, 12f),
)

private val VIGNETTE_LEVELS = listOf(
    DetailLevel(R.string.details_level_off, 0f),
    DetailLevel(R.string.details_level_soft, 0.3f),
    DetailLevel(R.string.details_level_classic, ColorSettings.VIGNETTE_WAS_ON),
    DetailLevel(R.string.details_level_deep, 0.8f),
    DetailLevel(R.string.details_level_full, 1f),
)

private val GRAIN_LEVELS = listOf(
    DetailLevel(R.string.details_level_off, 0f),
    DetailLevel(R.string.details_level_fine, 8f),
    DetailLevel(R.string.details_level_film, 18f),
    DetailLevel(R.string.details_level_heavy, 35f),
)

/**
 * The Details tool.
 *
 * @param color     the settings being edited: the clip's, or the whole video's.
 * @param onColor   changes them.
 * @param clipScope the panel edits a clip (else the whole video).
 * @param other     the settings the export also runs on the picture: the whole
 *                  video's for a clip, the clip's under the playhead for the whole video.
 * @param denoise   the denoise's own panel, with its comparison (see DenoisePanel).
 */
@Composable
internal fun VideoDetailsPanel(
    color: ColorSettings,
    onColor: ((ColorSettings) -> ColorSettings) -> Unit,
    clipScope: Boolean,
    other: ColorSettings,
    enabled: Boolean,
    denoise: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var section by rememberSaveable { mutableStateOf(DetailsSection.DENOISE) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            DetailsSection.entries.forEach { entry ->
                ControlChip(
                    label = stringResource(entry.labelRes),
                    icon = entry.icon,
                    selected = section == entry,
                    changed = entry.inUse(color),
                    onClick = { section = entry },
                )
            }
        }
        AnimatedContent(
            targetState = section,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "detailsSection",
        ) { shown ->
            when (shown) {
                DetailsSection.DENOISE -> denoise()
                DetailsSection.SHARPNESS -> DetailSetting(
                    section = shown,
                    color = color,
                    levels = SHARPNESS_LEVELS,
                    value = color.sharpness,
                    range = -1f..1f,
                    valueText = signedPercent(color.sharpness),
                    input = ValueInput(step = 0.05f, unit = "%", scale = 100f, range = -1f..1f),
                    onValue = { v -> onColor { it.copy(sharpness = (v * 100f).roundToInt() / 100f) } },
                    hint = R.string.details_sharpness_hint,
                    clipScope = clipScope,
                    otherValue = other.sharpness.takeIf { it != 0f }?.let { signedPercent(it) },
                    enabled = enabled,
                    previewShown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                )
                DetailsSection.BLUR -> DetailSetting(
                    section = shown,
                    color = color,
                    levels = BLUR_LEVELS,
                    value = color.gaussianBlur,
                    range = 0f..ColorSettings.BLUR_MAX,
                    valueText = if (color.gaussianBlur > 0f) stringResource(R.string.details_blur_value, decimal(color.gaussianBlur, 1)) else stringResource(R.string.details_level_off),
                    input = ValueInput(step = 0.5f, unit = "px", decimals = 1, range = 0f..ColorSettings.BLUR_MAX),
                    onValue = { v -> onColor { it.copy(gaussianBlur = (v * 10f).roundToInt() / 10f) } },
                    hint = R.string.details_blur_hint,
                    clipScope = clipScope,
                    otherValue = other.gaussianBlur.takeIf { it > 0f }?.let { decimal(it, 1) + " px" },
                    enabled = enabled,
                    previewShown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
                DetailsSection.VIGNETTE -> DetailSetting(
                    section = shown,
                    color = color,
                    levels = VIGNETTE_LEVELS,
                    value = color.vignette,
                    range = 0f..1f,
                    valueText = if (color.vignette > 0f) stringResource(R.string.value_percent, (color.vignette * 100).roundToInt()) else stringResource(R.string.details_level_off),
                    input = ValueInput(step = 0.05f, unit = "%", scale = 100f, range = 0f..1f),
                    onValue = { v -> onColor { it.copy(vignette = (v * 100f).roundToInt() / 100f) } },
                    hint = R.string.details_vignette_hint,
                    clipScope = clipScope,
                    otherValue = other.vignette.takeIf { it > 0f }?.let { "${(it * 100).roundToInt()}%" },
                    enabled = enabled,
                    previewShown = true,
                )
                DetailsSection.GRAIN -> DetailSetting(
                    section = shown,
                    color = color,
                    levels = GRAIN_LEVELS,
                    value = color.filmGrain,
                    range = 0f..ColorSettings.GRAIN_MAX,
                    valueText = if (color.filmGrain > 0f) color.filmGrain.roundToInt().toString() else stringResource(R.string.details_level_off),
                    input = ValueInput(step = 1f, range = 0f..ColorSettings.GRAIN_MAX),
                    onValue = { v -> onColor { it.copy(filmGrain = v.roundToInt().toFloat()) } },
                    hint = R.string.details_grain_hint,
                    clipScope = clipScope,
                    otherValue = other.filmGrain.takeIf { it > 0f }?.roundToInt()?.toString(),
                    enabled = enabled,
                    previewShown = true,
                )
            }
        }
    }
}

/**
 * One finishing touch: its swatch, its levels, its slider, what it does, and
 * where else the export runs one.
 */
@Composable
private fun DetailSetting(
    section: DetailsSection,
    color: ColorSettings,
    levels: List<DetailLevel>,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    input: ValueInput,
    onValue: (Float) -> Unit,
    @StringRes hint: Int,
    clipScope: Boolean,
    otherValue: String?,
    enabled: Boolean,
    previewShown: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val tolerance = (range.endInclusive - range.start) / 200f
    val level = levels.firstOrNull { abs(it.value - value) <= tolerance }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        DetailSwatch(section = section, color = color)
        ConnectedChoiceGroup(
            options = levels,
            // A value between the levels shows none of them chosen.
            selected = level,
            label = { option -> option?.let { stringResource(it.labelRes) }.orEmpty() },
            onSelect = { option -> option?.let { onValue(it.value) } },
            enabled = enabled,
        )
        PanelCard {
            ValueSlider(
                label = stringResource(section.labelRes),
                valueText = valueText,
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValue,
                valueRange = range,
                icon = section.icon,
                defaultValue = 0f,
                enabled = enabled,
                input = input,
                startLabel = if (section == DetailsSection.SHARPNESS) stringResource(R.string.details_softer) else "",
                endLabel = if (section == DetailsSection.SHARPNESS) stringResource(R.string.details_sharper) else "",
            )
            AnimatedVisibility(
                visible = value != 0f,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                PreviewNote(previewShown)
            }
        }
        PanelHint(stringResource(hint))
        PanelHint(stringResource(if (clipScope) R.string.details_scope_clip else R.string.details_scope_whole))
        if (otherValue != null) {
            PanelHint(stringResource(if (clipScope) R.string.details_with_whole else R.string.details_with_clip, otherValue))
        }
    }
}

/** Whether the preview shows the setting (it always is in the export). */
@Composable
private fun PreviewNote(shown: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (shown) scheme.secondaryContainer else scheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            if (shown) Icons.Rounded.Visibility else Icons.Rounded.Info,
            contentDescription = null,
            tint = if (shown) scheme.onSecondaryContainer else scheme.onTertiaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(if (shown) R.string.details_preview_live else R.string.details_preview_export),
            style = MaterialTheme.typography.labelMedium,
            color = if (shown) scheme.onSecondaryContainer else scheme.onTertiaryContainer,
        )
    }
}

/**
 * A small scene in the theme's colours (a sky, a sun, two hills) with the
 * setting of [section] laid on it as [color] has it, beside the setting's
 * badge: what it does, at a glance. The badge's shape says it too: soft or
 * spiky for the sharpness, a soft burst for the blur, a cookie otherwise.
 */
@Composable
private fun DetailSwatch(section: DetailsSection, color: ColorSettings) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val description = stringResource(R.string.details_swatch_description, stringResource(section.labelRes))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(12.dp)
            .semantics { contentDescription = description },
    ) {
        val spiky = section == DetailsSection.SHARPNESS && color.sharpness > 0f
        MorphingContainer(
            from = when (section) {
                DetailsSection.SHARPNESS -> MaterialShapes.Puffy
                DetailsSection.BLUR -> MaterialShapes.Circle
                else -> MaterialShapes.Cookie6Sided
            },
            to = when (section) {
                DetailsSection.SHARPNESS -> MaterialShapes.Burst
                DetailsSection.BLUR -> MaterialShapes.SoftBurst
                else -> MaterialShapes.Cookie9Sided
            },
            morphed = if (section == DetailsSection.SHARPNESS) spiky else section.inUse(color),
            color = if (section.inUse(color)) scheme.primary else scheme.surfaceContainerHighest,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = if (section.inUse(color)) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Box(modifier = Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp))) {
            when (section) {
                DetailsSection.BLUR, DetailsSection.SHARPNESS -> {
                    // Blurred as much as asked (Android 12 and later); a negative sharpness softens.
                    val sigma = if (section == DetailsSection.BLUR) color.gaussianBlur else (-color.sharpness).coerceAtLeast(0f) * 3f
                    val shown by animateFloatAsState(sigma, motion.defaultEffectsSpec(), label = "swatchBlur")
                    Scene(
                        crisp = if (section == DetailsSection.SHARPNESS) color.sharpness.coerceAtLeast(0f) else 0f,
                        modifier = Modifier.fillMaxSize().blur((shown * SWATCH_BLUR_DP_PER_SIGMA).dp),
                    )
                }
                DetailsSection.VIGNETTE -> {
                    val shown by animateFloatAsState(color.vignette, motion.defaultEffectsSpec(), label = "swatchVignette")
                    Scene(crisp = 0f, modifier = Modifier.fillMaxSize())
                    Canvas(modifier = Modifier.fillMaxSize()) { drawVignette(FinishingTouches.vignetteAngle(ColorSettings(vignette = shown))) }
                }
                DetailsSection.GRAIN -> {
                    Scene(crisp = 0f, modifier = Modifier.fillMaxSize())
                    GrainLayer(amplitude = FinishingTouches.grainAmplitude(color), modifier = Modifier.fillMaxSize())
                }
                DetailsSection.DENOISE -> Scene(crisp = 0f, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** The swatch's scene: a sky, a sun and two hills, outlined as much as [crisp] (0..1) to show sharper edges. */
@Composable
private fun Scene(crisp: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        drawRect(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
        val sun = Offset(size.width * 0.72f, size.height * 0.34f)
        drawCircle(scheme.secondary, radius = size.height * 0.16f, center = sun)
        val far = Path().apply {
            moveTo(0f, size.height * 0.7f)
            cubicTo(size.width * 0.25f, size.height * 0.42f, size.width * 0.45f, size.height * 0.5f, size.width * 0.62f, size.height * 0.66f)
            cubicTo(size.width * 0.78f, size.height * 0.8f, size.width * 0.9f, size.height * 0.58f, size.width, size.height * 0.6f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(far, scheme.tertiary)
        val near = Path().apply {
            moveTo(0f, size.height * 0.88f)
            cubicTo(size.width * 0.3f, size.height * 0.66f, size.width * 0.6f, size.height * 0.95f, size.width, size.height * 0.78f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(near, scheme.primary)
        if (crisp > 0f) {
            // Sharper: the edges stand out, as unsharp makes them.
            val edge = Stroke(width = 1.5.dp.toPx() * (0.5f + crisp))
            drawCircle(scheme.onSecondary.copy(alpha = crisp), radius = size.height * 0.16f, center = sun, style = edge)
            drawPath(far, scheme.onTertiary.copy(alpha = crisp * 0.8f), style = edge)
            drawPath(near, scheme.onPrimary.copy(alpha = crisp * 0.8f), style = edge)
        }
    }
}

/** The vignette's own falloff (see FinishingTouches.vignetteStops), over the whole of the scope, for [angle]. */
internal fun DrawScope.drawVignette(angle: Float) {
    if (angle <= 0f) return
    val reach = kotlin.math.hypot(size.width / 2f, size.height / 2f)
    val stops = FinishingTouches.vignetteStops(angle).map { (at, dark) -> at to Color.Black.copy(alpha = dark) }.toTypedArray()
    drawRect(Brush.radialGradient(colorStops = stops, center = Offset(size.width / 2f, size.height / 2f), radius = reach))
}

/**
 * Grain over what is under it: specks lighter and darker by up to
 * [amplitude] of full scale, drawn anew every frame (as noise draws them),
 * while it shows.
 */
@Composable
internal fun GrainLayer(amplitude: Float, modifier: Modifier = Modifier, specks: Int = SWATCH_SPECKS) {
    if (amplitude <= 0f) return
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it / GRAIN_FRAME_MS }
    }
    Canvas(modifier = modifier) {
        val random = Random(frame)
        val dot = 1.5.dp.toPx()
        // The specks' strength: the noise's own, made visible at the swatch's size.
        val alpha = (amplitude * GRAIN_SHOWN_GAIN).coerceIn(0f, 0.9f)
        repeat(specks) {
            val light = random.nextBoolean()
            drawRect(
                color = (if (light) Color.White else Color.Black).copy(alpha = alpha * random.nextFloat()),
                topLeft = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
                size = androidx.compose.ui.geometry.Size(dot, dot),
            )
        }
    }
}

/** "+30%", "-50%", "Off". */
@Composable
private fun signedPercent(value: Float): String =
    if (value == 0f) stringResource(R.string.details_level_off) else (if (value > 0f) "+" else "") + "${(value * 100).roundToInt()}%"

/** How much the swatch's scene blurs for a sigma of one footage pixel. */
private const val SWATCH_BLUR_DP_PER_SIGMA = 0.6f

/** How many specks of grain the swatch draws. */
private const val SWATCH_SPECKS = 900

/** How the grain's strength shows at the swatch's size. */
private const val GRAIN_SHOWN_GAIN = 4f

/** How long a draw of grain lasts (noise draws anew every frame of a 30 fps video). */
private const val GRAIN_FRAME_MS = 33L
