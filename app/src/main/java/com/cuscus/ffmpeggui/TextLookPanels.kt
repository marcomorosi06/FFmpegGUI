@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.SettingsOverscan
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.core.model.TextFont
import com.cuscus.ffmpeggui.core.model.TextLook
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * A caption's outline, shadow and background box (issue #22), each in a panel
 * of its own as a picture's border and shadow are, and its font, which can be
 * one the user added. Every look is drawn by drawtext in the export and by
 * the preview the same way (see TextLook).
 */

/** A change to a caption's look; the step names a run of changes that undo as one (null: one change). */
internal typealias TextLookChange = (String?, (TextLook) -> TextLook) -> Unit

/** Colours offered for outlines, shadows and boxes (ARGB, as the swatches take them). */
private val TEXT_LOOK_COLORS = listOf(
    0xFF000000L, 0xFFFFFFFFL, 0xFF263238L, 0xFFFF5252L, 0xFFFFAB40L, 0xFFFFEB3BL,
    0xFF69F0AEL, 0xFF40C4FFL, 0xFF536DFEL, 0xFFE040FBL, 0xFFFF80ABL,
)

/** [picked]'s colour with [current]'s alpha: a swatch changes the colour, not how see-through it is. */
private fun keepAlpha(picked: Long, current: Long): Long = (current and 0xFF000000L) or (picked and 0xFFFFFFL)

/** [color] with its alpha set to [alpha] (0..1). */
private fun withAlpha(color: Long, alpha: Float): Long =
    ((alpha.coerceIn(0f, 1f) * 255f).roundToInt().toLong() shl 24) or (color and 0xFFFFFFL)

/** The alpha of [color], 0..1. */
private fun alphaOf(color: Long): Float = ((color ushr 24) and 0xFF) / 255f

@Composable
private fun percent(value: Float, of: Float): String = stringResource(R.string.value_percent, (value / of * 100).roundToInt())

// -- Outline ----------------------------------------------------------------------------------------

/** The outline round the letters: how thick, and its colour (a colour turns it on). */
@Composable
internal fun TextOutlineContent(look: TextLook, onLook: TextLookChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.text_outline_width),
                valueText = if (look.outline > 0f) percent(look.outline, TextLook.MAX_OUTLINE) else stringResource(R.string.overlay_off),
                value = look.outline,
                onValueChange = { value -> onLook("outline") { it.copy(outline = value) } },
                valueRange = 0f..TextLook.MAX_OUTLINE,
                icon = Icons.Rounded.BorderOuter,
                defaultValue = 0f,
            )
            ColorSwatchRow(
                colors = TEXT_LOOK_COLORS,
                selected = look.outlineColor,
                onSelect = { argb ->
                    onLook(null) { it.copy(outlineColor = keepAlpha(argb, it.outlineColor), outline = if (it.outline > 0f) it.outline else TextLook.DEFAULT_OUTLINE) }
                },
            )
        }
        PanelHint(stringResource(R.string.text_outline_hint))
    }
}

// -- Shadow -----------------------------------------------------------------------------------------

/** The shadow under the letters: how far it falls, how soft, how dark, its colour (a colour turns it on). */
@Composable
internal fun TextShadowContent(look: TextLook, onLook: TextLookChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.text_shadow_distance),
                valueText = when {
                    look.shadow > 0f -> percent(look.shadow, TextLook.MAX_SHADOW)
                    look.shadowBlur > 0f -> stringResource(R.string.text_shadow_glow)
                    else -> stringResource(R.string.overlay_off)
                },
                value = look.shadow,
                onValueChange = { value -> onLook("shadow") { it.copy(shadow = value) } },
                valueRange = 0f..TextLook.MAX_SHADOW,
                icon = Icons.Rounded.SouthEast,
                defaultValue = 0f,
            )
            ValueSlider(
                label = stringResource(R.string.text_shadow_blur),
                valueText = if (look.shadowBlur > 0f) percent(look.shadowBlur, TextLook.MAX_SHADOW_BLUR) else stringResource(R.string.text_shadow_sharp),
                value = look.shadowBlur,
                onValueChange = { value -> onLook("shadowBlur") { it.copy(shadowBlur = value) } },
                valueRange = 0f..TextLook.MAX_SHADOW_BLUR,
                icon = Icons.Rounded.BlurOn,
                defaultValue = 0f,
                startLabel = stringResource(R.string.text_shadow_sharp),
                endLabel = stringResource(R.string.text_shadow_soft),
            )
            ValueSlider(
                label = stringResource(R.string.image_opacity),
                valueText = stringResource(R.string.value_percent, (alphaOf(look.shadowColor) * 100).roundToInt()),
                value = alphaOf(look.shadowColor),
                onValueChange = { value -> onLook("shadowOpacity") { it.copy(shadowColor = withAlpha(it.shadowColor, value)) } },
                valueRange = 0f..1f,
                icon = Icons.Rounded.Opacity,
                defaultValue = alphaOf(TextLook.SHADOW_COLOR),
                enabled = look.hasShadow,
            )
            ColorSwatchRow(
                colors = TEXT_LOOK_COLORS,
                selected = look.shadowColor,
                onSelect = { argb ->
                    onLook(null) { it.copy(shadowColor = keepAlpha(argb, it.shadowColor), shadow = if (it.hasShadow) it.shadow else TextLook.DEFAULT_SHADOW) }
                },
            )
        }
        PanelHint(stringResource(R.string.text_shadow_hint))
    }
}

// -- Box --------------------------------------------------------------------------------------------

/** A box behind the text: how see-through (none at 0), how far it goes past the letters, its colour. */
@Composable
internal fun TextBoxContent(look: TextLook, onLook: TextLookChange) {
    val opacity = if (look.box) alphaOf(look.boxColor) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.image_opacity),
                valueText = if (look.box) stringResource(R.string.value_percent, (opacity * 100).roundToInt()) else stringResource(R.string.overlay_off),
                value = opacity,
                onValueChange = { value ->
                    // At 0 the box goes; its colour keeps the last opacity it had, for when it comes back.
                    onLook("boxOpacity") { if (value <= 0f) it.copy(box = false) else it.copy(box = true, boxColor = withAlpha(it.boxColor, value)) }
                },
                valueRange = 0f..1f,
                icon = Icons.Rounded.Opacity,
                defaultValue = 0f,
            )
            ValueSlider(
                label = stringResource(R.string.text_box_padding),
                valueText = percent(look.boxPadding, TextLook.MAX_BOX_PADDING),
                value = look.boxPadding,
                onValueChange = { value -> onLook("boxPadding") { it.copy(boxPadding = value) } },
                valueRange = 0f..TextLook.MAX_BOX_PADDING,
                icon = Icons.Rounded.SettingsOverscan,
                defaultValue = TextLook.DEFAULT_BOX_PADDING,
                enabled = look.box,
            )
            ColorSwatchRow(
                colors = TEXT_LOOK_COLORS,
                selected = look.boxColor,
                onSelect = { argb -> onLook(null) { it.copy(boxColor = keepAlpha(argb, it.boxColor), box = true) } },
            )
        }
        PanelHint(stringResource(R.string.text_box_hint))
    }
}

// -- Font -------------------------------------------------------------------------------------------

/** One tile of the caption's font picker: a family of the phone, a font the user added, or the way to add one. */
private sealed interface FontChoice {
    data class Family(val font: TextFont) : FontChoice
    data class Added(val path: String) : FontChoice
    data object Add : FontChoice
}

/**
 * The caption's typeface: the phone's families, each shown in itself, then the
 * fonts the user added (each in itself too), then a tile to add a .ttf or .otf.
 */
@Composable
internal fun CaptionFontPicker(
    text: TextOverlay,
    onFont: (TextFont) -> Unit,
    onFontFile: (String?) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Bumped after a font is added, so that the list is read again.
    var added by remember { mutableIntStateOf(0) }
    val fonts by produceState(initialValue = emptyList<String>(), added) {
        value = withContext(Dispatchers.IO) { CaptionFonts.added(context).map { it.absolutePath } }
    }
    val failed = stringResource(R.string.text_font_add_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = CaptionFonts.add(context, uri)
            if (file == null) {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                added++
                onFontFile(file.absolutePath)
            }
        }
    }
    // The caption's own file first among the added ones, also when it was added elsewhere.
    val own = (listOfNotNull(text.fontFile) + fonts).distinct()
    val options = TextFont.entries.map { font ->
        TileOption<FontChoice>(
            value = FontChoice.Family(font),
            label = stringResource(captionFontLabel(font)),
            preview = { color ->
                Text(
                    text = "Aa",
                    color = color,
                    fontSize = 22.sp,
                    fontWeight = if (text.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = rememberExportFont(font, text.isBold),
                )
            },
        )
    } + own.map { path ->
        TileOption<FontChoice>(
            value = FontChoice.Added(path),
            label = CaptionFonts.label(path),
            preview = { color ->
                Text(
                    text = "Aa",
                    color = color,
                    fontSize = 22.sp,
                    // As the export draws it: the file as it is, no bold made up.
                    fontFamily = rememberCaptionFont(TextFont.SANS, false, path),
                    style = TextStyle(fontSynthesis = FontSynthesis.None),
                )
            },
        )
    } + TileOption<FontChoice>(FontChoice.Add, stringResource(R.string.text_font_add), icon = Icons.Rounded.Add)
    OptionTileRow(
        options = options,
        selected = text.fontFile?.let { FontChoice.Added(it) } ?: FontChoice.Family(text.font),
        onSelect = { choice ->
            when (choice) {
                is FontChoice.Family -> {
                    onFont(choice.font)
                    if (text.fontFile != null) onFontFile(null)
                }
                is FontChoice.Added -> onFontFile(choice.path)
                FontChoice.Add -> picker.launch(CaptionFonts.PICKER_TYPES)
            }
        },
    )
}

private fun captionFontLabel(font: TextFont): Int = when (font) {
    TextFont.SANS -> R.string.font_sans
    TextFont.SERIF -> R.string.font_serif
    TextFont.MONO -> R.string.font_mono
    TextFont.TYPEWRITER -> R.string.font_typewriter
    TextFont.HANDWRITING -> R.string.font_handwriting
    TextFont.CASUAL -> R.string.font_casual
    TextFont.SMALL_CAPS -> R.string.font_small_caps
}
