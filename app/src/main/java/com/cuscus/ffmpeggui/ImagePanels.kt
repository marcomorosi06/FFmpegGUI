@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.core.model.ImageBorder
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageEffect
import com.cuscus.ffmpeggui.core.model.ImageFilter
import com.cuscus.ffmpeggui.core.model.ImageOutput
import com.cuscus.ffmpeggui.core.model.ImageTone
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Stroke
import com.cuscus.ffmpeggui.core.model.StrokeShape
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTile
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The panels of the picture editor, built to stay low so that the picture
 * keeps most of the screen (the photo-app way): a row of round controls, and
 * above it one slider (or one row of colours) for the control that is chosen.
 * Each panel changes the ImageEdit (or the brush) through a callback; quick
 * changes of one setting (a slider) carry a key, so they make one undo step.
 */

/** Changes the picture's edit; the key groups quick changes into one undo step. */
internal typealias ImageChange = (String?, (ImageEdit) -> ImageEdit) -> Unit

/** Shapes a chosen thumbnail morphs into, along the row. */
private val THUMB_SHAPES: List<RoundedPolygon>
    get() = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Flower,
        MaterialShapes.Sunny,
    )

// -- Building blocks ---------------------------------------------------------------------

/**
 * A slider in one row: the slider, its value in a pill and, once it has left
 * [defaultValue], a reset that pops in. [label] names it for accessibility.
 */
@Composable
internal fun CompactSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    defaultValue: Float? = null,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val moved = defaultValue != null && abs(value - defaultValue) > (valueRange.endInclusive - valueRange.start) / 1000f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().height(COMPACT_ROW_HEIGHT)) {
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            valueRange = valueRange,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        Spacer(modifier = Modifier.width(8.dp))
        AnimatedVisibility(
            visible = moved,
            enter = expandHorizontally(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = shrinkHorizontally(motion.fastEffectsSpec()) + scaleOut(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                defaultValue?.let(onValueChange)
            }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.action_reset), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** One control of a [ControlRow]: its round chip, and what shows above the row when it is chosen. */
internal class PanelControl(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    /** Away from its default: the chip wears a dot. */
    val changed: Boolean,
    val content: @Composable () -> Unit,
)

/**
 * Controls as a row of round chips (the chosen one morphs into a cookie) with,
 * above it, the chosen one's slider or colours: two short rows for any number
 * of settings. [trailing] goes at the end of the row (a reset, say).
 */
@Composable
internal fun ControlRow(
    controls: List<PanelControl>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val motion = MaterialTheme.motionScheme
    val shown = controls.firstOrNull { it.key == selectedKey } ?: controls.first()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        AnimatedContent(
            targetState = shown.key,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            contentAlignment = Alignment.Center,
            label = "controlRow",
        ) { key ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(COMPACT_ROW_HEIGHT)) {
                (controls.firstOrNull { it.key == key } ?: shown).content()
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            controls.forEach { control ->
                ControlChip(
                    label = stringResource(control.labelRes),
                    icon = control.icon,
                    selected = control.key == shown.key,
                    changed = control.changed,
                    onClick = { onSelect(control.key) },
                )
            }
            trailing?.invoke()
        }
    }
}

/** A round chip with [icon] above its [label]; [changed] adds a dot. */
@Composable
internal fun ControlChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    changed: Boolean,
    onClick: () -> Unit,
    badgeSize: Dp = 44.dp,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "chip")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, motion.defaultEffectsSpec(), label = "chipContent")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(badgeSize + 22.dp)
            .springPress(interaction, pressedScale = 0.88f)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(vertical = 2.dp),
    ) {
        Box {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = MaterialShapes.Cookie9Sided,
                morphed = selected,
                color = container,
                modifier = Modifier.size(badgeSize),
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            }
            if (changed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.tertiary),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Height of one compact row: a slider, a row of colours. */
private val COMPACT_ROW_HEIGHT = 48.dp

// -- Frame -------------------------------------------------------------------------------

/** Which frame of the video the picture is: a frame back, a slider over the clip, a frame forward. */
@Composable
internal fun ImageFramePanel(frameMs: Long, lastMs: Long, frameStepMs: Long, onFrame: (String?, Long) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val last = lastMs.coerceAtLeast(0L)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFrame(null, (frameMs - frameStepMs).coerceIn(0L, last))
            },
            enabled = frameMs > 0L,
        ) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.image_frame_previous))
        }
        CompactSlider(
            label = stringResource(R.string.image_frame_position),
            valueText = formatTimeMs(frameMs),
            value = frameMs.toFloat(),
            onValueChange = { onFrame("frame", Math.round(it).toLong().coerceIn(0L, last)) },
            valueRange = 0f..last.toFloat().coerceAtLeast(1f),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFrame(null, (frameMs + frameStepMs).coerceIn(0L, last))
            },
            enabled = frameMs < last,
        ) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.image_frame_next))
        }
    }
}

// -- Straighten and perspective ------------------------------------------------------

@Composable
internal fun ImageStraightenPanel(edit: ImageEdit, onChange: ImageChange) {
    var key by rememberSaveable { mutableStateOf("angle") }
    ControlRow(
        controls = listOf(
            PanelControl("angle", R.string.image_straighten_angle, Icons.Rounded.RotateRight, edit.straightenDegrees != 0f) {
                CompactSlider(
                    label = stringResource(R.string.image_straighten_angle),
                    valueText = stringResource(R.string.value_degrees_decimal, signedDecimal(edit.straightenDegrees, 1)),
                    value = edit.straightenDegrees,
                    onValueChange = { v -> onChange("straighten") { it.copy(straightenDegrees = Math.round(v * 10f) / 10f) } },
                    valueRange = -45f..45f,
                    defaultValue = 0f,
                )
            },
            PanelControl("vertical", R.string.image_keystone_vertical, Icons.Rounded.SwapVert, edit.perspectiveV != 0f) {
                CompactSlider(
                    label = stringResource(R.string.image_keystone_vertical),
                    valueText = signedWhole(edit.perspectiveV * 100),
                    value = edit.perspectiveV,
                    onValueChange = { v -> onChange("keystoneV") { it.copy(perspectiveV = v) } },
                    valueRange = -1f..1f,
                    defaultValue = 0f,
                )
            },
            PanelControl("horizontal", R.string.image_keystone_horizontal, Icons.Rounded.SwapHoriz, edit.perspectiveH != 0f) {
                CompactSlider(
                    label = stringResource(R.string.image_keystone_horizontal),
                    valueText = signedWhole(edit.perspectiveH * 100),
                    value = edit.perspectiveH,
                    onValueChange = { v -> onChange("keystoneH") { it.copy(perspectiveH = v) } },
                    valueRange = -1f..1f,
                    defaultValue = 0f,
                )
            },
        ),
        selectedKey = key,
        onSelect = { key = it },
    )
}

// -- Adjust ------------------------------------------------------------------------------

/** One setting of the Adjust panel: what it shows and how it changes the tone. */
private class ToneSetting(
    @StringRes val labelRes: Int,
    val key: String,
    val icon: ImageVector,
    val range: ClosedFloatingPointRange<Float>,
    val read: (ImageTone) -> Float,
    val write: (ImageTone, Float) -> ImageTone,
)

private val TONE_SETTINGS = listOf(
    ToneSetting(R.string.image_exposure, "exposure", Icons.Rounded.Exposure, -2f..2f, { it.exposure }, { t, v -> t.copy(exposure = v) }),
    ToneSetting(R.string.image_brightness, "brightness", Icons.Rounded.BrightnessMedium, -1f..1f, { it.brightness }, { t, v -> t.copy(brightness = v) }),
    ToneSetting(R.string.image_contrast, "contrast", Icons.Rounded.Contrast, -1f..1f, { it.contrast }, { t, v -> t.copy(contrast = v) }),
    ToneSetting(R.string.image_highlights, "highlights", Icons.Rounded.WbSunny, -1f..1f, { it.highlights }, { t, v -> t.copy(highlights = v) }),
    ToneSetting(R.string.image_shadows, "shadows", Icons.Rounded.DarkMode, -1f..1f, { it.shadows }, { t, v -> t.copy(shadows = v) }),
    ToneSetting(R.string.image_saturation, "saturation", Icons.Rounded.WaterDrop, -1f..1f, { it.saturation }, { t, v -> t.copy(saturation = v) }),
    ToneSetting(R.string.image_warmth, "warmth", Icons.Rounded.Thermostat, -1f..1f, { it.warmth }, { t, v -> t.copy(warmth = v) }),
    ToneSetting(R.string.image_tint, "tint", Icons.Rounded.ColorLens, -1f..1f, { it.tint }, { t, v -> t.copy(tint = v) }),
    ToneSetting(R.string.image_hue, "hue", Icons.Rounded.Palette, -180f..180f, { it.hue }, { t, v -> t.copy(hue = v) }),
    ToneSetting(R.string.image_fade, "fade", Icons.Rounded.Gradient, 0f..1f, { it.fade }, { t, v -> t.copy(fade = v) }),
)

/** Light and colour: one slider at a time over a row of chips, and a reset for all of them. */
@Composable
internal fun ImageAdjustPanel(tone: ImageTone, onChange: ImageChange) {
    var key by rememberSaveable { mutableStateOf(TONE_SETTINGS.first().key) }
    ControlRow(
        controls = TONE_SETTINGS.map { setting ->
            val value = setting.read(tone)
            PanelControl(setting.key, setting.labelRes, setting.icon, value != 0f) {
                CompactSlider(
                    label = stringResource(setting.labelRes),
                    valueText = when (setting.key) {
                        "exposure" -> stringResource(R.string.value_ev, signedDecimal(value, 1))
                        "hue" -> stringResource(R.string.value_degrees, Math.round(value))
                        else -> signedWhole(value * 100)
                    },
                    value = value,
                    onValueChange = { v -> onChange("tone.${setting.key}") { it.copy(tone = setting.write(it.tone, v)) } },
                    valueRange = setting.range,
                    defaultValue = 0f,
                )
            }
        },
        selectedKey = key,
        onSelect = { key = it },
        trailing = {
            ResetChip(visible = tone != ImageTone()) { onChange(null) { it.copy(tone = ImageTone()) } }
        },
    )
}

/** A chip that puts every setting of a panel back, shown only when there is something to put back. */
@Composable
private fun ResetChip(visible: Boolean, onReset: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = shrinkHorizontally(motion.fastEffectsSpec()) + scaleOut(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        ControlChip(
            label = stringResource(R.string.action_reset),
            icon = Icons.Rounded.RestartAlt,
            selected = false,
            changed = false,
            onClick = onReset,
        )
    }
}

// -- Filters and effects -------------------------------------------------------------

@get:StringRes
private val ImageFilter.labelRes: Int
    get() = when (this) {
        ImageFilter.NONE -> R.string.filter_none
        ImageFilter.VIVID -> R.string.filter_vivid
        ImageFilter.WARM -> R.string.filter_warm
        ImageFilter.COOL -> R.string.filter_cool
        ImageFilter.VINTAGE -> R.string.filter_vintage
        ImageFilter.FADED -> R.string.filter_faded
        ImageFilter.DRAMA -> R.string.filter_drama
        ImageFilter.FILM -> R.string.filter_film
        ImageFilter.MONO -> R.string.filter_mono
        ImageFilter.NOIR -> R.string.filter_noir
        ImageFilter.SEPIA -> R.string.filter_sepia
        ImageFilter.MATTE -> R.string.filter_matte
    }

@get:StringRes
private val ImageEffect.labelRes: Int
    get() = when (this) {
        ImageEffect.NONE -> R.string.effect_none
        ImageEffect.BLUR -> R.string.effect_blur
        ImageEffect.PIXELATE -> R.string.effect_pixelate
        ImageEffect.GLITCH -> R.string.effect_glitch
        ImageEffect.POSTERIZE -> R.string.effect_posterize
        ImageEffect.INVERT -> R.string.effect_invert
        ImageEffect.EMBOSS -> R.string.effect_emboss
        ImageEffect.SKETCH -> R.string.effect_sketch
        ImageEffect.DUOTONE -> R.string.effect_duotone
    }

/** Effects whose strength can be set (the others are on or off). */
private val ImageEffect.hasStrength: Boolean
    get() = this != ImageEffect.NONE && this != ImageEffect.EMBOSS && this != ImageEffect.DUOTONE

/**
 * Choices shown as small thumbnails of the picture itself: [thumbnails] is the
 * picture with each choice applied (null while they are being made).
 */
@Composable
private fun <T> ThumbnailRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    thumbnails: Map<T, Bitmap>?,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        val shapes = THUMB_SHAPES
        options.forEachIndexed { index, option ->
            val thumb = thumbnails?.get(option)
            val image = remember(thumb) { thumb?.asImageBitmap() }
            OptionTile(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                chosenShape = shapes[index % shapes.size],
                badgeSize = 52.dp,
                preview = { color ->
                    if (image != null) {
                        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.Photo, contentDescription = null, tint = color)
                    }
                },
            )
        }
    }
}

/** Ready-made looks, each shown on the picture, and how strongly the chosen one applies. */
@Composable
internal fun ImageFiltersPanel(edit: ImageEdit, thumbnails: Map<ImageFilter, Bitmap>?, onChange: ImageChange) {
    val motion = MaterialTheme.motionScheme
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        AnimatedVisibility(
            visible = edit.filter != ImageFilter.NONE,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            CompactSlider(
                label = stringResource(R.string.image_filter_amount),
                valueText = stringResource(R.string.value_percent, (edit.filterAmount * 100).roundToInt()),
                value = edit.filterAmount,
                onValueChange = { v -> onChange("filter.amount") { it.copy(filterAmount = v) } },
                valueRange = 0f..1f,
                defaultValue = 1f,
            )
        }
        ThumbnailRow(
            options = ImageFilter.entries,
            selected = edit.filter,
            label = { stringResource(it.labelRes) },
            thumbnails = thumbnails,
            onSelect = { filter -> onChange(null) { it.copy(filter = filter, filterAmount = 1f) } },
        )
    }
}

/** Duotone colours: shadows and highlights. */
private val DUOTONE_DARKS = listOf(0xFF1B1F5AL, 0xFF000000L, 0xFF3A0CA3L, 0xFF7B1E3AL, 0xFF0B4F3CL, 0xFF263238L)
private val DUOTONE_LIGHTS = listOf(0xFFFFC857L, 0xFFFFFFFFL, 0xFFFF8FABL, 0xFF80FFDBL, 0xFFB5E48CL, 0xFFFFD6A5L)

/** One stylised effect at a time, with its strength (or, for duotone, its two colours). */
@Composable
internal fun ImageEffectsPanel(edit: ImageEdit, thumbnails: Map<ImageEffect, Bitmap>?, onChange: ImageChange) {
    val motion = MaterialTheme.motionScheme
    // Duotone: which of its two colours is being picked.
    var lightColour by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        AnimatedVisibility(
            visible = edit.effect.hasStrength,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            CompactSlider(
                label = stringResource(R.string.image_effect_amount),
                valueText = stringResource(R.string.value_percent, (edit.effectAmount * 100).roundToInt()),
                value = edit.effectAmount,
                onValueChange = { v -> onChange("effect.amount") { it.copy(effectAmount = v) } },
                valueRange = 0f..1f,
                defaultValue = 0.5f,
            )
        }
        AnimatedVisibility(
            visible = edit.effect == ImageEffect.DUOTONE,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // The two colours as one switchable dot: shadows, then highlights.
                ColourTarget(color = edit.duotoneDark, selected = !lightColour, label = stringResource(R.string.image_duotone_dark)) { lightColour = false }
                ColourTarget(color = edit.duotoneLight, selected = lightColour, label = stringResource(R.string.image_duotone_light)) { lightColour = true }
                Spacer(modifier = Modifier.width(4.dp))
                ColorSwatchRow(
                    colors = if (lightColour) DUOTONE_LIGHTS else DUOTONE_DARKS,
                    selected = if (lightColour) edit.duotoneLight else edit.duotoneDark,
                    onSelect = { c -> onChange(null) { if (lightColour) it.copy(duotoneLight = c) else it.copy(duotoneDark = c) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ThumbnailRow(
            options = ImageEffect.entries,
            selected = edit.effect,
            label = { stringResource(it.labelRes) },
            thumbnails = thumbnails,
            onSelect = { effect -> onChange(null) { it.copy(effect = effect) } },
        )
    }
}

/** A colour being set, as a dot with a ring while it is the one the swatches change. */
@Composable
private fun ColourTarget(color: Long, selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = label
            },
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            drawCircle(color = if (selected) colors.primary else colors.outlineVariant, radius = size.minDimension / 2f)
            drawCircle(color = Color(color), radius = size.minDimension / 2f - 4.dp.toPx())
        }
    }
}

/** Sharpen, vignette and grain: the finishing touches, over everything else. */
@Composable
internal fun ImageDetailsPanel(edit: ImageEdit, onChange: ImageChange) {
    var key by rememberSaveable { mutableStateOf("sharpen") }
    @Composable
    fun percentSlider(@StringRes labelRes: Int, stepKey: String, value: Float, write: (ImageEdit, Float) -> ImageEdit) = CompactSlider(
        label = stringResource(labelRes),
        valueText = stringResource(R.string.value_percent, (value * 100).roundToInt()),
        value = value,
        onValueChange = { v -> onChange(stepKey) { write(it, v) } },
        valueRange = 0f..1f,
        defaultValue = 0f,
    )
    ControlRow(
        controls = listOf(
            PanelControl("sharpen", R.string.image_sharpen, Icons.Rounded.Details, edit.sharpen > 0f) {
                percentSlider(R.string.image_sharpen, "sharpen", edit.sharpen) { e, v -> e.copy(sharpen = v) }
            },
            PanelControl("vignette", R.string.image_vignette, Icons.Rounded.Vignette, edit.vignette > 0f) {
                percentSlider(R.string.image_vignette, "vignette", edit.vignette) { e, v -> e.copy(vignette = v) }
            },
            PanelControl("grain", R.string.image_grain, Icons.Rounded.Grain, edit.grain > 0f) {
                percentSlider(R.string.image_grain, "grain", edit.grain) { e, v -> e.copy(grain = v) }
            },
        ),
        selectedKey = key,
        onSelect = { key = it },
    )
}

// -- Drawing -----------------------------------------------------------------------------

/** What the finger draws with: the kind of stroke, its colour, size and opacity. */
internal data class BrushSettings(
    val mode: DrawMode = DrawMode.BRUSH,
    val color: Long = 0xFFFFFFFFL,
    /** Fraction of the picture width. */
    val width: Float = 0.012f,
    val opacity: Float = 1f,
) {
    /** A stroke through [points] (x, y fractions of the picture). */
    fun stroke(points: List<Float>): Stroke = Stroke(
        points = points,
        color = color,
        width = width,
        opacity = opacity,
        shape = mode.shape,
        eraser = mode == DrawMode.ERASER,
    )
}

internal enum class DrawMode(@StringRes val labelRes: Int, val icon: ImageVector, val shape: StrokeShape) {
    BRUSH(R.string.draw_brush, Icons.Rounded.Gesture, StrokeShape.FREE),
    LINE(R.string.draw_line, Icons.Rounded.HorizontalRule, StrokeShape.LINE),
    ARROW(R.string.draw_arrow, Icons.Rounded.NorthEast, StrokeShape.ARROW),
    BOX(R.string.draw_box, Icons.Rounded.CropSquare, StrokeShape.RECTANGLE),
    OVAL(R.string.draw_oval, Icons.Rounded.RadioButtonUnchecked, StrokeShape.OVAL),
    ERASER(R.string.draw_eraser, Icons.Rounded.AutoFixNormal, StrokeShape.FREE),
}

private val DRAW_COLORS = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFFF5252L, 0xFFFFAB40L, 0xFFFFEB3BL,
    0xFF69F0AEL, 0xFF40C4FFL, 0xFF536DFEL, 0xFFE040FBL, 0xFFFF80ABL,
)

/**
 * The brush in three short rows: what it draws (with undo and clear at the
 * end), its colour, and its size or opacity on one slider.
 */
@Composable
internal fun ImageDrawPanel(
    brush: BrushSettings,
    onBrush: (BrushSettings) -> Unit,
    strokeCount: Int,
    onUndoStroke: () -> Unit,
    onClear: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    // Which of the two the slider sets.
    var settingOpacity by rememberSaveable { mutableStateOf(false) }
    val eraser = brush.mode == DrawMode.ERASER
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                DrawMode.entries.forEach { mode ->
                    SmallChoice(
                        icon = mode.icon,
                        label = stringResource(mode.labelRes),
                        selected = brush.mode == mode,
                        onClick = { onBrush(brush.copy(mode = mode)) },
                    )
                }
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUndoStroke()
                },
                enabled = strokeCount > 0,
            ) {
                Icon(Icons.Rounded.Undo, contentDescription = stringResource(R.string.draw_undo))
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClear()
                },
                enabled = strokeCount > 0,
            ) {
                Icon(Icons.Rounded.LayersClear, contentDescription = stringResource(R.string.draw_clear))
            }
        }
        if (!eraser) {
            ColorSwatchRow(colors = DRAW_COLORS, selected = brush.color, onSelect = { onBrush(brush.copy(color = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SmallChoice(
                icon = Icons.Rounded.LineWeight,
                label = stringResource(R.string.draw_size),
                selected = !settingOpacity || eraser,
                onClick = { settingOpacity = false },
            )
            if (!eraser) {
                SmallChoice(
                    icon = Icons.Rounded.Opacity,
                    label = stringResource(R.string.draw_opacity),
                    selected = settingOpacity,
                    onClick = { settingOpacity = true },
                )
            }
            // The brush as it will draw: its size, colour and opacity.
            Canvas(modifier = Modifier.size(40.dp)) {
                val radius = (brush.width / 0.08f).coerceIn(0.08f, 1f) * size.minDimension / 2f
                drawCircle(
                    color = if (eraser) colors.outline else Color(brush.color).copy(alpha = brush.opacity),
                    radius = radius,
                )
            }
            if (settingOpacity && !eraser) {
                CompactSlider(
                    label = stringResource(R.string.draw_opacity),
                    valueText = stringResource(R.string.value_percent, (brush.opacity * 100).roundToInt()),
                    value = brush.opacity,
                    onValueChange = { onBrush(brush.copy(opacity = it)) },
                    valueRange = 0.1f..1f,
                    defaultValue = 1f,
                    modifier = Modifier.weight(1f),
                )
            } else {
                CompactSlider(
                    label = stringResource(R.string.draw_size),
                    valueText = stringResource(R.string.value_percent, (brush.width * 1000).roundToInt()),
                    value = brush.width,
                    onValueChange = { onBrush(brush.copy(width = it)) },
                    valueRange = 0.002f..0.08f,
                    defaultValue = 0.012f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A small round icon choice (40 dp) that fills in when chosen. */
@Composable
private fun SmallChoice(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else Color.Transparent, motion.defaultEffectsSpec(), label = "smallChoice")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, motion.defaultEffectsSpec(), label = "smallChoiceContent")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .springPress(interaction, pressedScale = 0.85f)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                this.selected = selected
                contentDescription = label
            },
    ) {
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie6Sided,
            morphed = selected,
            color = container,
            modifier = Modifier.size(38.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

// -- Border ------------------------------------------------------------------------------

private val BORDER_COLORS = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFF5EBDCL, 0xFF9E9E9EL, 0xFFFF5252L,
    0xFFFFC857L, 0xFF69F0AEL, 0xFF40C4FFL, 0xFF536DFEL, 0xFFFF80ABL,
)

/** A frame around the picture: on or off and its width in one row, its colour in another. */
@Composable
internal fun ImageBorderPanel(border: ImageBorder?, onChange: ImageChange) {
    val haptic = LocalHapticFeedback.current
    // Remembered while it is off, so that switching it back on restores it. (A plain
    // holder: it follows the border and never needs to recompose anything itself.)
    val remembered = remember { arrayOf(border ?: ImageBorder()) }
    if (border != null) remembered[0] = border
    val last = remembered[0]
    val on = border != null
    val label = stringResource(R.string.border_on)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(COMPACT_ROW_HEIGHT)) {
            Switch(
                checked = on,
                onCheckedChange = { checked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChange(null) { it.copy(border = if (checked) last else null) }
                },
                modifier = Modifier.semantics { contentDescription = label },
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (on) {
                CompactSlider(
                    label = stringResource(R.string.border_width),
                    valueText = stringResource(R.string.value_percent, (last.widthFraction * 100).roundToInt()),
                    value = last.widthFraction,
                    onValueChange = { v -> onChange("border.width") { it.copy(border = (it.border ?: last).copy(widthFraction = v)) } },
                    valueRange = 0.005f..0.25f,
                    defaultValue = 0.04f,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(label, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (on) {
            ColorSwatchRow(
                colors = BORDER_COLORS,
                selected = last.color,
                onSelect = { c -> onChange(null) { it.copy(border = (it.border ?: last).copy(color = c)) } },
            )
        }
    }
}

// -- Export ------------------------------------------------------------------------------

/** Longer sides offered for the output, 0 = as it is. */
private val OUTPUT_SIDES = listOf(0, 4096, 2048, 1080, 720)

/**
 * Size and quality of the picture file: the longer side ([OUTPUT_SIDES] that
 * make it smaller than [fullWidth] x [fullHeight]), the quality of a lossy
 * format, and the size that comes out.
 */
@Composable
internal fun ImageOutputSettings(
    format: OutputFormat,
    output: ImageOutput,
    fullWidth: Int,
    fullHeight: Int,
    onChange: ImageChange,
    enabled: Boolean,
) {
    val longer = maxOf(fullWidth, fullHeight)
    val sides = OUTPUT_SIDES.filter { it == 0 || it < longer }
    val (outW, outH) = outputSize(fullWidth, fullHeight, output.maxSide)
    PanelCard {
        SectionLabel(stringResource(R.string.image_export_size))
        ConnectedChoiceGroup(
            options = sides,
            selected = output.maxSide.takeIf { it in sides } ?: 0,
            label = { side -> if (side == 0) stringResource(R.string.image_size_original) else stringResource(R.string.image_size_side, side) },
            onSelect = { side -> onChange(null) { it.copy(output = it.output.copy(maxSide = side)) } },
            enabled = enabled,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelHint(stringResource(R.string.image_size_hint), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.image_size_value, outW, outH),
                    style = MaterialTheme.typography.titleSmallEmphasized.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (format.hasImageQuality) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.image_export_quality),
                valueText = stringResource(R.string.value_percent, (output.quality * 100).roundToInt()),
                value = output.quality,
                onValueChange = { v -> onChange("quality") { it.copy(output = it.output.copy(quality = v)) } },
                valueRange = 0.1f..1f,
                defaultValue = 0.9f,
                enabled = enabled,
            )
            PanelHint(stringResource(R.string.image_quality_hint))
        }
    } else {
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                PanelHint(stringResource(R.string.image_lossless))
            }
        }
    }
    if (format.needsPlatformEncoder) {
        PanelCard { PanelHint(stringResource(R.string.image_webp_note)) }
    }
}

/** Size of a [width] x [height] picture once its longer side is at most [maxSide] (0 = as it is). */
internal fun outputSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    if (maxSide <= 0 || maxOf(width, height) <= maxSide) return width to height
    return if (width >= height) {
        maxSide to (height.toLong() * maxSide / width).toInt().coerceAtLeast(1)
    } else {
        (width.toLong() * maxSide / height).toInt().coerceAtLeast(1) to maxSide
    }
}

// -- Helpers -------------------------------------------------------------------------

/** "+1.5", "-0.3", "0.0". */
internal fun signedDecimal(value: Float, digits: Int): String {
    val text = decimal(value, digits)
    return when {
        text.toDouble() == 0.0 -> decimal(0f, digits)
        value > 0f -> "+$text"
        else -> text
    }
}
