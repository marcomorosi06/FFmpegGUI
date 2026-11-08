@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.textart

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbShade
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.textart.Alphabet
import com.cuscus.ffmpeggui.core.textart.ArtColor
import com.cuscus.ffmpeggui.core.textart.ArtColors
import com.cuscus.ffmpeggui.core.textart.ArtEncoding
import com.cuscus.ffmpeggui.core.textart.ArtMode
import com.cuscus.ffmpeggui.core.textart.ArtStroke
import com.cuscus.ffmpeggui.core.textart.ArtWarning
import com.cuscus.ffmpeggui.core.textart.BrightMode
import com.cuscus.ffmpeggui.core.textart.ColorLayer
import com.cuscus.ffmpeggui.core.textart.ColorMatch
import com.cuscus.ffmpeggui.core.textart.Dither
import com.cuscus.ffmpeggui.core.textart.DitherTarget
import com.cuscus.ffmpeggui.core.textart.FrameStyle
import com.cuscus.ffmpeggui.core.textart.GlyphFilter
import com.cuscus.ffmpeggui.core.textart.GlyphGroup
import com.cuscus.ffmpeggui.core.textart.GlyphKey
import com.cuscus.ffmpeggui.core.textart.HueBand
import com.cuscus.ffmpeggui.core.textart.LineEnding
import com.cuscus.ffmpeggui.core.textart.Paper
import com.cuscus.ffmpeggui.core.textart.TerminalTheme
import com.cuscus.ffmpeggui.core.textart.TextArtLayout
import com.cuscus.ffmpeggui.core.textart.TextArtPresets
import com.cuscus.ffmpeggui.core.textart.TextArtReport
import com.cuscus.ffmpeggui.core.textart.TextArtRenderer
import com.cuscus.ffmpeggui.core.textart.TextArtSettings
import com.cuscus.ffmpeggui.core.textart.TextTarget
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The panels of the text-art editor, one a tool. Each changes the settings
 * through [ArtChange]: a key groups the changes of one slider drag into one
 * undo step (null: a step of its own).
 */

/** A change to the text-art settings: (undo key, change). */
internal typealias ArtChange = (String?, (TextArtSettings) -> TextArtSettings) -> Unit

/** Panel content spacing, as the other editors' panels. */
@Composable
private fun PanelColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { content() }
}

/** A title over a group of controls. */
@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
}

// -----------------------------------------------------------------------------
// Style: where it is going, the mode, the size, the page
// -----------------------------------------------------------------------------

@Composable
internal fun ArtStylePanel(
    settings: TextArtSettings,
    ansi: Boolean,
    previews: Map<ArtMode, Bitmap>,
    grid: IntArray?,
    cellAspect: Float,
    onChange: ArtChange,
) {
    val cp437 = ansi && settings.layout.encoding == ArtEncoding.CP437
    PanelColumn {
        PanelCard {
            SectionTitle(stringResource(R.string.textart_target_title))
            OptionTileRow(
                options = TextTarget.entries.filter { ansi || it != TextTarget.BBS }.map { t ->
                    TileOption(t, stringResource(t.labelRes), icon = when (t) {
                        TextTarget.TERMINAL -> Icons.Rounded.Terminal
                        TextTarget.EDITOR -> Icons.Rounded.Edit
                        TextTarget.MARKDOWN -> Icons.Rounded.Code
                        TextTarget.CHAT -> Icons.Rounded.Groups
                        TextTarget.BBS -> Icons.Rounded.Tv
                    })
                },
                selected = settings.target,
                onSelect = { t -> onChange(null) { TextArtPresets.apply(t, it, ansi) } },
            )
            PanelHint(stringResource(R.string.textart_target_hint))
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_mode_title))
            OptionTileRow(
                options = ArtMode.entries.filter { !(cp437 && it == ArtMode.BRAILLE) }.map { m ->
                    TileOption(m, stringResource(m.labelRes), icon = m.icon, preview = previews[m]?.let(::modePreview))
                },
                selected = settings.mode,
                onSelect = { m -> onChange(null) { it.copy(mode = m, alphabet = TextArtPresets.alphabetFor(m, it.alphabet)) } },
            )
            PanelHint(stringResource(settings.mode.hintRes))
            if (settings.mode == ArtMode.TEXT_FILL) {
                ArtTextField(
                    value = settings.phrase,
                    label = stringResource(R.string.textart_phrase),
                    onValue = { v -> onChange("phrase") { it.copy(phrase = v) } },
                )
            }
        }
        PanelCard {
            ArtSizeBlock(settings, grid, cellAspect, onChange)
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_paper_title))
            ConnectedChoiceGroup(
                options = Paper.entries,
                selected = settings.paper,
                label = { stringResource(it.labelRes) },
                onSelect = { p -> onChange(null) { it.copy(paper = p) } },
            )
            PanelHint(stringResource(R.string.textart_paper_hint))
            SwitchCard(
                icon = Icons.Rounded.AspectRatio,
                title = stringResource(R.string.textart_aspect_auto),
                subtitle = stringResource(R.string.textart_aspect_auto_hint),
                checked = settings.cellAspect <= 0f,
                onCheckedChange = { on -> onChange(null) { it.copy(cellAspect = if (on) 0f else 0.5f) } },
            )
            AnimatedVisibility(visible = settings.cellAspect > 0f) {
                ValueSlider(
                    label = stringResource(R.string.textart_aspect),
                    valueText = "%.2f".format(settings.cellAspect.coerceAtLeast(0.2f)),
                    value = settings.cellAspect.coerceIn(0.25f, 1.2f),
                    onValueChange = { v -> onChange("aspect") { it.copy(cellAspect = (v * 100).roundToInt() / 100f) } },
                    valueRange = 0.25f..1.2f,
                    defaultValue = 0.5f,
                    input = ValueInput(step = 0.01f, decimals = 2),
                )
            }
        }
    }
}

/** Widths the size block offers a tap away: a chat, a classic terminal, wider screens. */
private val COMMON_WIDTHS = listOf(40, 80, 120, 160, 240)

/**
 * The art's size in characters. A frame shows the grid as the art will stand
 * (its cells as wide as the font's), with the count in it. Columns and lines
 * have a slider each; a chain between them keeps the picture's shape (lines
 * follow columns, and dragging the lines moves the columns) or, open, lets the
 * lines be set on their own, the picture stretched or squeezed to fill them.
 * [grid] is the last art's columns and lines, [cellAspect] a cell's width over its height.
 */
@Composable
private fun ArtSizeBlock(settings: TextArtSettings, grid: IntArray?, cellAspect: Float, onChange: ArtChange) {
    val haptic = LocalHapticFeedback.current
    val linked = settings.rows <= 0
    // Columns over lines as the picture has them, from the last art (it keeps the shape when linked).
    val shownColumns = grid?.getOrNull(0)?.coerceAtLeast(1) ?: settings.columns
    val shownRows = grid?.getOrNull(1)?.coerceAtLeast(1) ?: (settings.columns / 2)
    val ratio = if (linked) shownColumns.toFloat() / shownRows else settings.columns.toFloat() / settings.rows.coerceAtLeast(1)
    val rows = if (linked) (settings.columns / ratio).roundToInt().coerceIn(TextArtSettings.MIN_ROWS, TextArtSettings.MAX_ROWS) else settings.rows
    val columnRange = TextArtSettings.MIN_COLUMNS.toFloat()..TextArtSettings.MAX_COLUMNS.toFloat()
    val rowRange = TextArtSettings.MIN_ROWS.toFloat()..TextArtSettings.MAX_ROWS.toFloat()

    SectionTitle(stringResource(R.string.textart_size_title))
    GridFrame(settings.columns, rows, cellAspect, linked)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            ValueSlider(
                label = stringResource(R.string.textart_columns),
                valueText = "${settings.columns}",
                value = settings.columns.toFloat(),
                onValueChange = { v -> onChange("columns") { it.copy(columns = v.roundToInt()) } },
                valueRange = columnRange,
                defaultValue = TextArtSettings.DEFAULT_COLUMNS.toFloat(),
                icon = Icons.Rounded.Straighten,
                input = ValueInput(step = 1f, range = columnRange),
            )
            ValueSlider(
                label = stringResource(R.string.textart_rows),
                valueText = if (linked) stringResource(R.string.textart_rows_auto, rows) else "$rows",
                value = rows.toFloat(),
                onValueChange = { v ->
                    onChange("rows") {
                        if (linked) {
                            // The chain is closed: the columns move so the shape stays.
                            it.copy(columns = (v * ratio).roundToInt().coerceIn(TextArtSettings.MIN_COLUMNS, TextArtSettings.MAX_COLUMNS))
                        } else {
                            it.copy(rows = v.roundToInt())
                        }
                    }
                },
                valueRange = rowRange,
                icon = Icons.Rounded.Height,
                input = ValueInput(step = 1f, range = rowRange),
            )
        }
        SizeChain(
            linked = linked,
            onToggle = { on ->
                haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(null) { it.copy(rows = if (on) 0 else rows) }
            },
        )
    }
    SectionTitle(stringResource(R.string.textart_size_presets))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        COMMON_WIDTHS.forEach { width ->
            val on = settings.columns == width
            FilterChip(
                selected = on,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onChange(null) { s ->
                        // Open, the lines keep the shape they had with the new width.
                        if (s.rows > 0) s.copy(columns = width, rows = (width / ratio).roundToInt().coerceIn(TextArtSettings.MIN_ROWS, TextArtSettings.MAX_ROWS)) else s.copy(columns = width)
                    }
                },
                label = { Text("$width", fontFamily = FontFamily.Monospace) },
                leadingIcon = if (width == 80) {
                    { Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
    PanelHint(stringResource(if (linked) R.string.textart_size_hint_linked else R.string.textart_size_hint_free))
}

/**
 * The grid as the art will stand: a frame the shape of [columns] cells across
 * and [rows] down (each [cellAspect] as wide as tall), faint lines every few
 * cells, and the count in its middle. The frame eases into a new shape; open
 * ([linked] false), its border is dashed, as a shape that can be pulled.
 */
@Composable
private fun GridFrame(columns: Int, rows: Int, cellAspect: Float, linked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val target = (columns * cellAspect.coerceIn(0.2f, 2f) / rows.coerceAtLeast(1)).coerceIn(0.25f, 6f)
    val aspect by animateFloatAsState(target, motion.defaultSpatialSpec(), label = "gridAspect")
    val fill = scheme.secondaryContainer
    val line = scheme.onSecondaryContainer.copy(alpha = 0.14f)
    val border = scheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(GRID_FRAME_HEIGHT)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxW = size.width
            val maxH = size.height
            val w = if (maxW / maxH > aspect) maxH * aspect else maxW
            val h = w / aspect
            val left = (maxW - w) / 2f
            val top = (maxH - h) / 2f
            val radius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
            drawRoundRect(fill, Offset(left, top), Size(w, h), radius)
            // A line every few cells, never so many they make a blur.
            val stepX = gridStep(columns)
            val stepY = gridStep(rows)
            var c = stepX
            while (c < columns) {
                val x = left + w * c / columns
                drawLine(line, Offset(x, top), Offset(x, top + h), strokeWidth = 1.dp.toPx())
                c += stepX
            }
            var r = stepY
            while (r < rows) {
                val y = top + h * r / rows
                drawLine(line, Offset(left, y), Offset(left + w, y), strokeWidth = 1.dp.toPx())
                r += stepY
            }
            val dash = if (linked) null else androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
            drawRoundRect(
                border,
                Offset(left, top),
                Size(w, h),
                radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = dash),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$columns \u00D7 $rows",
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.textart_size_cells, columns * rows),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

/** Cells between two lines of the grid frame: about eight lines at most. */
private fun gridStep(count: Int): Int {
    val raw = (count / 8f).coerceAtLeast(1f)
    return listOf(1, 2, 5, 10, 20, 25, 50, 100).firstOrNull { it >= raw } ?: 100
}

/**
 * The chain between the columns and lines sliders: closed, the lines follow
 * the picture's shape; open, they are set on their own. Two short arms reach
 * up and down to the sliders it ties, coloured while they are tied.
 */
@Composable
private fun SizeChain(linked: Boolean, onToggle: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val arm by animateFloatAsState(if (linked) 1f else 0.35f, motion.defaultSpatialSpec(), label = "chainArm")
    val armColor = if (linked) scheme.primary else scheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
        Box(Modifier.width(3.dp).height(CHAIN_ARM * arm).clip(CircleShape).background(armColor))
        FilledTonalIconToggleButton(
            checked = linked,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Icon(
                if (linked) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                contentDescription = stringResource(if (linked) R.string.textart_size_unlink else R.string.textart_size_link),
            )
        }
        Box(Modifier.width(3.dp).height(CHAIN_ARM * arm).clip(CircleShape).background(armColor))
    }
}

private val GRID_FRAME_HEIGHT = 132.dp
private val CHAIN_ARM = 28.dp

/** A mode's tile: the picture drawn in that mode, small. */
private fun modePreview(bitmap: Bitmap): @Composable (Color) -> Unit = { _ ->
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize())
}

// -----------------------------------------------------------------------------
// Alphabet: the characters, and what keeps them out
// -----------------------------------------------------------------------------

/** The preset's alphabet, keeping the filters and preferences of [current]. */
private fun AlphabetPreset.alphabet(current: Alphabet): Alphabet {
    val base = when (this) {
        AlphabetPreset.CLASSIC -> Alphabet()
        AlphabetPreset.DETAILED -> Alphabet(extra = Alphabet.DETAILED)
        AlphabetPreset.ALL_ASCII -> TextArtPresets.ALL_ASCII
        AlphabetPreset.LETTERS -> Alphabet(groups = setOf(GlyphGroup.UPPER, GlyphGroup.LOWER), extra = "")
        AlphabetPreset.DIGITS -> Alphabet(groups = setOf(GlyphGroup.DIGITS), extra = "")
        AlphabetPreset.BINARY -> Alphabet(extra = "01")
        AlphabetPreset.DOTS -> Alphabet(extra = ".:oO0@")
        AlphabetPreset.CODE -> Alphabet(extra = "{}[]()<>;=+-*/")
    }
    return base.copy(filters = current.filters, bias = current.bias, maxShare = current.maxShare, maxRun = current.maxRun, dedupe = current.dedupe)
}

private fun AlphabetPreset.matches(a: Alphabet): Boolean {
    val p = alphabet(a)
    return p.groups == a.groups && p.extra == a.extra && a.removed.isEmpty()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtAlphabetPanel(settings: TextArtSettings, keys: List<GlyphKey>, onChange: ArtChange) {
    val haptic = LocalHapticFeedback.current
    val a = settings.alphabet
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val blockModes = settings.mode == ArtMode.BLOCKS || settings.mode == ArtMode.HALF_BLOCKS || settings.mode == ArtMode.QUADRANTS || settings.mode == ArtMode.BRAILLE
    PanelColumn {
        if (blockModes) PanelHint(stringResource(R.string.textart_alphabet_blocks_hint))
        PanelCard {
            SectionTitle(stringResource(R.string.textart_presets))
            OptionTileRow(
                options = AlphabetPreset.entries.map { p ->
                    TileOption(p, stringResource(p.labelRes), preview = { color ->
                        Text(p.sample, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = color, maxLines = 1)
                    })
                },
                selected = AlphabetPreset.entries.firstOrNull { it.matches(a) },
                onSelect = { p -> onChange(null) { it.copy(alphabet = p.alphabet(it.alphabet)) } },
            )
            SectionTitle(stringResource(R.string.textart_groups))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GlyphGroup.entries.forEach { g ->
                    val on = g in a.groups
                    FilterChip(
                        selected = on,
                        onClick = {
                            haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            onChange(null) { it.copy(alphabet = it.alphabet.copy(groups = if (on) it.alphabet.groups - g else it.alphabet.groups + g)) }
                        },
                        label = { Text(stringResource(g.labelRes)) },
                        leadingIcon = if (on) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.textart_keyboard))
                Spacer(modifier = Modifier.weight(1f))
                val used = keys.count { it.state == com.cuscus.ffmpeggui.core.textart.GlyphState.ACTIVE }
                Text(stringResource(R.string.textart_keyboard_count, used, keys.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PanelHint(stringResource(R.string.textart_keyboard_hint))
            GlyphKeyboard(
                keys = keys,
                bias = a.bias,
                picked = picked,
                onToggle = { code ->
                    val ch = String(Character.toChars(code))
                    onChange(null) {
                        val removed = it.alphabet.removed
                        it.copy(alphabet = it.alphabet.copy(removed = if (removed.contains(ch)) removed.replace(ch, "") else removed + ch))
                    }
                },
                onPick = { picked = it },
            )
            val p = picked
            AnimatedVisibility(visible = p != null) {
                val ch = p?.let { String(Character.toChars(it)) } ?: ""
                val v = a.bias[ch] ?: 0f
                ValueSlider(
                    label = stringResource(R.string.textart_bias, ch),
                    valueText = when {
                        v > 0.05f -> stringResource(R.string.textart_bias_prefer, (v * 100).roundToInt())
                        v < -0.05f -> stringResource(R.string.textart_bias_avoid, (-v * 100).roundToInt())
                        else -> stringResource(R.string.textart_bias_neutral)
                    },
                    value = v,
                    onValueChange = { nv ->
                        onChange("bias:$ch") {
                            val b = it.alphabet.bias.toMutableMap()
                            if (abs(nv) < 0.02f) b.remove(ch) else b[ch] = (nv * 100).roundToInt() / 100f
                            it.copy(alphabet = it.alphabet.copy(bias = b))
                        }
                    },
                    valueRange = -1f..1f,
                    defaultValue = 0f,
                    startLabel = stringResource(R.string.textart_bias_avoid_short),
                    endLabel = stringResource(R.string.textart_bias_prefer_short),
                )
            }
            ArtTextField(
                value = a.extra,
                label = stringResource(R.string.textart_extra),
                monospace = true,
                onValue = { v -> onChange("extra") { it.copy(alphabet = it.alphabet.copy(extra = v.replace("\n", ""))) } },
            )
            if (a.removed.isNotEmpty() || a.bias.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        picked = null
                        onChange(null) { it.copy(alphabet = it.alphabet.copy(removed = "", bias = emptyMap())) }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.textart_keys_reset))
                }
            }
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_filters))
            GlyphFilter.entries.forEach { f ->
                SwitchCard(
                    icon = when (f) {
                        GlyphFilter.ASCII_ONLY -> Icons.Rounded.Keyboard
                        GlyphFilter.MARKDOWN_SAFE -> Icons.Rounded.Tag
                        GlyphFilter.COMMENT_SAFE -> Icons.Rounded.DataObject
                        GlyphFilter.NO_LOOKALIKES -> Icons.Rounded.Visibility
                        GlyphFilter.SAFE_WIDTH -> Icons.Rounded.Straighten
                    },
                    title = stringResource(f.labelRes),
                    subtitle = stringResource(f.hintRes),
                    checked = f in a.filters,
                    onCheckedChange = { on -> onChange(null) { it.copy(alphabet = it.alphabet.copy(filters = if (on) it.alphabet.filters + f else it.alphabet.filters - f)) } },
                )
            }
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_limits))
            ValueSlider(
                label = stringResource(R.string.textart_dedupe),
                valueText = if (a.dedupe <= 0f) stringResource(R.string.textart_off) else "%.1f %%".format(a.dedupe * 100f),
                value = a.dedupe,
                onValueChange = { v -> onChange("dedupe") { it.copy(alphabet = it.alphabet.copy(dedupe = (v * 1000).roundToInt() / 1000f)) } },
                valueRange = 0f..0.05f,
                defaultValue = Alphabet().dedupe,
            )
            PanelHint(stringResource(R.string.textart_dedupe_hint))
            ValueSlider(
                label = stringResource(R.string.textart_max_share),
                valueText = if (a.maxShare >= 1f) stringResource(R.string.textart_off) else "${(a.maxShare * 100).roundToInt()} %",
                value = a.maxShare,
                onValueChange = { v -> onChange("share") { it.copy(alphabet = it.alphabet.copy(maxShare = (v * 100).roundToInt() / 100f)) } },
                valueRange = 0.05f..1f,
                defaultValue = 1f,
            )
            ValueSlider(
                label = stringResource(R.string.textart_max_run),
                valueText = if (a.maxRun <= 0) stringResource(R.string.textart_off) else "${a.maxRun}",
                value = a.maxRun.toFloat(),
                onValueChange = { v -> onChange("run") { it.copy(alphabet = it.alphabet.copy(maxRun = v.roundToInt())) } },
                valueRange = 0f..12f,
                steps = 11,
                defaultValue = 0f,
            )
            PanelHint(stringResource(R.string.textart_limits_hint))
        }
    }
}

// -----------------------------------------------------------------------------
// Light: tone, edges and dithering
// -----------------------------------------------------------------------------

@Composable
internal fun ArtLightPanel(settings: TextArtSettings, ansi: Boolean, onChange: ArtChange) {
    val t = settings.tone
    PanelColumn {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.textart_brightness),
                valueText = signedPercent(t.brightness),
                value = t.brightness,
                onValueChange = { v -> onChange("brightness") { it.copy(tone = it.tone.copy(brightness = round2(v))) } },
                valueRange = -1f..1f,
                defaultValue = 0f,
                input = ValueInput(step = 0.01f, unit = "%", scale = 100f),
            )
            ValueSlider(
                label = stringResource(R.string.textart_contrast),
                valueText = signedPercent(t.contrast),
                value = t.contrast,
                onValueChange = { v -> onChange("contrast") { it.copy(tone = it.tone.copy(contrast = round2(v))) } },
                valueRange = -1f..1f,
                defaultValue = 0f,
                input = ValueInput(step = 0.01f, unit = "%", scale = 100f),
            )
            ValueSlider(
                label = stringResource(R.string.textart_gamma),
                valueText = "%.2f".format(t.gamma),
                value = t.gamma,
                onValueChange = { v -> onChange("gamma") { it.copy(tone = it.tone.copy(gamma = round2(v))) } },
                valueRange = 0.3f..3f,
                defaultValue = 1f,
                input = ValueInput(step = 0.01f, decimals = 2),
            )
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_curve))
            ToneCurve(points = t.curve, onPoints = { step, pts -> onChange(step) { it.copy(tone = it.tone.copy(curve = pts)) } })
            PanelHint(stringResource(R.string.textart_curve_hint))
            SwitchCard(
                icon = Icons.Rounded.Equalizer,
                title = stringResource(R.string.textart_equalize),
                subtitle = stringResource(R.string.textart_equalize_hint),
                checked = t.equalize,
                onCheckedChange = { on -> onChange(null) { it.copy(tone = it.tone.copy(equalize = on)) } },
            )
            SwitchCard(
                icon = Icons.Rounded.InvertColors,
                title = stringResource(R.string.textart_invert),
                subtitle = stringResource(R.string.textart_invert_hint),
                checked = t.invert,
                onCheckedChange = { on -> onChange(null) { it.copy(tone = it.tone.copy(invert = on)) } },
            )
        }
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.textart_edge_boost),
                valueText = if (t.edgeBoost <= 0f) stringResource(R.string.textart_off) else "%.2f".format(t.edgeBoost),
                value = t.edgeBoost,
                onValueChange = { v -> onChange("edgeBoost") { it.copy(tone = it.tone.copy(edgeBoost = round2(v))) } },
                valueRange = 0f..2f,
                defaultValue = 0f,
            )
            if (settings.mode == ArtMode.EDGES) {
                ValueSlider(
                    label = stringResource(R.string.textart_edge_threshold),
                    valueText = "${(t.edgeThreshold * 100).roundToInt()} %",
                    value = t.edgeThreshold,
                    onValueChange = { v -> onChange("edgeThreshold") { it.copy(tone = it.tone.copy(edgeThreshold = round2(v))) } },
                    valueRange = 0.02f..1f,
                    defaultValue = 0.22f,
                )
                SwitchCard(
                    icon = Icons.Rounded.SelectAll,
                    title = stringResource(R.string.textart_edge_fill),
                    subtitle = stringResource(R.string.textart_edge_fill_hint),
                    checked = t.edgeFill,
                    onCheckedChange = { on -> onChange(null) { it.copy(tone = it.tone.copy(edgeFill = on)) } },
                )
            }
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_dither))
            OptionTileRow(
                options = Dither.entries.map { d -> TileOption(d, stringResource(d.labelRes), preview = { color -> DitherSample(d, color) }) },
                selected = settings.dither,
                onSelect = { d -> onChange(null) { it.copy(dither = d) } },
            )
            AnimatedVisibility(visible = settings.dither != Dither.NONE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValueSlider(
                        label = stringResource(R.string.textart_dither_strength),
                        valueText = "${(settings.ditherStrength * 100).roundToInt()} %",
                        value = settings.ditherStrength,
                        onValueChange = { v -> onChange("ditherStrength") { it.copy(ditherStrength = round2(v)) } },
                        valueRange = 0f..1f,
                        defaultValue = 1f,
                    )
                    if (ansi && settings.palette.depth != ArtColor.TRUECOLOR) {
                        ConnectedChoiceGroup(
                            options = DitherTarget.entries,
                            selected = settings.ditherTarget,
                            label = { stringResource(it.labelRes) },
                            onSelect = { d -> onChange(null) { it.copy(ditherTarget = d) } },
                        )
                    }
                }
            }
            PanelHint(stringResource(R.string.textart_dither_hint))
        }
    }
}

/** A tile's picture of a dithering: a ramp from paper to ink, dotted as [dither] dots it. */
@Composable
private fun DitherSample(dither: Dither, color: Color) {
    Canvas(modifier = Modifier.size(32.dp)) {
        val n = 8
        val cell = size.width / n
        val err = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val t = (x + 0.5f) / n
            val v = when (dither) {
                Dither.NONE -> t
                Dither.BAYER_4 -> t + TextArtRenderer.bayer4(x, y) - 0.5f
                Dither.BAYER_8 -> t + TextArtRenderer.bayer8(x, y) - 0.5f
                else -> t + err[y * n + x]
            }
            val on = v >= 0.5f
            if (dither == Dither.FLOYD_STEINBERG || dither == Dither.ATKINSON) {
                val e = v - if (on) 1f else 0f
                fun add(xx: Int, yy: Int, w: Float) { if (xx in 0 until n && yy in 0 until n) err[yy * n + xx] += e * w }
                if (dither == Dither.ATKINSON) {
                    add(x + 1, y, 0.125f); add(x + 2, y, 0.125f); add(x - 1, y + 1, 0.125f); add(x, y + 1, 0.125f); add(x + 1, y + 1, 0.125f); add(x, y + 2, 0.125f)
                } else {
                    add(x + 1, y, 7f / 16f); add(x - 1, y + 1, 3f / 16f); add(x, y + 1, 5f / 16f); add(x + 1, y + 1, 1f / 16f)
                }
            }
            if (on) drawRect(color, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// -----------------------------------------------------------------------------
// Colours (ANSI): depth, theme, layers, matching, the palette itself
// -----------------------------------------------------------------------------

@Composable
internal fun ArtColorsPanel(settings: TextArtSettings, onChange: ArtChange) {
    val haptic = LocalHapticFeedback.current
    val p = settings.palette
    val cp437 = settings.layout.encoding == ArtEncoding.CP437
    val depth = if (cp437) ArtColor.ANSI_16 else p.depth
    PanelColumn {
        PanelCard {
            SectionTitle(stringResource(R.string.textart_depth))
            ConnectedChoiceGroup(
                options = listOf(ArtColor.ANSI_16, ArtColor.ANSI_256, ArtColor.TRUECOLOR),
                selected = depth,
                label = { stringResource(it.labelRes) },
                onSelect = { d -> onChange(null) { it.copy(palette = it.palette.copy(depth = d, banned = emptySet(), remap = emptyMap())) } },
                enabled = !cp437,
            )
            PanelHint(stringResource(if (cp437) R.string.textart_depth_cp437_hint else R.string.textart_depth_hint))
            if (depth != ArtColor.TRUECOLOR) {
                SectionTitle(stringResource(R.string.textart_theme))
                OptionTileRow(
                    options = TerminalTheme.entries.map { th -> TileOption(th, stringResource(th.labelRes), preview = { _ -> ThemeSwatches(ArtColors.theme(th)) }) },
                    selected = p.theme,
                    onSelect = { th -> onChange(null) { it.copy(palette = it.palette.copy(theme = th)) } },
                )
                PanelHint(stringResource(R.string.textart_theme_hint))
            }
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_layer))
            ConnectedChoiceGroup(
                options = ColorLayer.entries,
                selected = p.layer,
                label = { stringResource(it.labelRes) },
                onSelect = { l -> onChange(null) { it.copy(palette = it.palette.copy(layer = l)) } },
            )
            PanelHint(stringResource(R.string.textart_layer_hint))
            SectionTitle(stringResource(R.string.textart_match))
            ConnectedChoiceGroup(
                options = ColorMatch.entries,
                selected = p.match,
                label = { stringResource(it.labelRes) },
                onSelect = { m -> onChange(null) { it.copy(palette = it.palette.copy(match = m)) } },
                enabled = depth != ArtColor.TRUECOLOR,
            )
            PanelHint(stringResource(R.string.textart_match_hint))
        }
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.textart_saturation),
                valueText = signedPercent(p.saturation),
                value = p.saturation,
                onValueChange = { v -> onChange("saturation") { it.copy(palette = it.palette.copy(saturation = round2(v))) } },
                valueRange = -1f..1f,
                defaultValue = 0f,
                input = ValueInput(step = 0.01f, unit = "%", scale = 100f),
            )
            ValueSlider(
                label = stringResource(R.string.textart_reduce),
                valueText = if (p.reduceTo <= 0) stringResource(R.string.textart_off) else "${p.reduceTo}",
                value = p.reduceTo.toFloat(),
                onValueChange = { v -> onChange("reduce") { it.copy(palette = it.palette.copy(reduceTo = v.roundToInt())) } },
                valueRange = 0f..64f,
                defaultValue = 0f,
                input = ValueInput(step = 1f),
            )
            PanelHint(stringResource(R.string.textart_reduce_hint))
            SwitchCard(
                icon = Icons.Rounded.WbShade,
                title = stringResource(R.string.textart_light_from_glyph),
                subtitle = stringResource(R.string.textart_light_from_glyph_hint),
                checked = p.lightFromGlyph,
                onCheckedChange = { on -> onChange(null) { it.copy(palette = it.palette.copy(lightFromGlyph = on)) } },
            )
            if (depth == ArtColor.ANSI_16 && !cp437) {
                SectionTitle(stringResource(R.string.textart_bright))
                ConnectedChoiceGroup(
                    options = BrightMode.entries,
                    selected = p.bright,
                    label = { stringResource(it.labelRes) },
                    onSelect = { b -> onChange(null) { it.copy(palette = it.palette.copy(bright = b)) } },
                )
                PanelHint(stringResource(R.string.textart_bright_hint))
            }
            if (cp437) {
                SwitchCard(
                    icon = Icons.Rounded.AcUnit,
                    title = stringResource(R.string.textart_ice),
                    subtitle = stringResource(R.string.textart_ice_hint),
                    checked = p.iceColors,
                    onCheckedChange = { on -> onChange(null) { it.copy(palette = it.palette.copy(iceColors = on)) } },
                )
            }
        }
        if (depth != ArtColor.TRUECOLOR) {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.textart_palette))
                    Spacer(modifier = Modifier.weight(1f))
                    if (p.banned.isNotEmpty() || p.remap.isNotEmpty()) {
                        Text(
                            stringResource(R.string.textart_palette_reset),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onChange(null) { it.copy(palette = it.palette.copy(banned = emptySet(), remap = emptyMap())) }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                PanelHint(stringResource(R.string.textart_palette_hint))
                val colors = if (depth == ArtColor.ANSI_16) ArtColors.theme(p.theme) else ArtColors.xterm256(p.theme)
                PaletteGrid(
                    colors = colors,
                    banned = p.banned,
                    remap = p.remap,
                    onToggle = { i -> onChange(null) { it.copy(palette = it.palette.copy(banned = if (i in it.palette.banned) it.palette.banned - i else it.palette.banned + i)) } },
                    onRemap = { from, to ->
                        onChange(null) {
                            val m = it.palette.remap.toMutableMap()
                            if (to == null) m.remove(from) else m[from] = to
                            it.copy(palette = it.palette.copy(remap = m))
                        }
                    },
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Hide: what is left out of the art
// -----------------------------------------------------------------------------

@Composable
internal fun ArtHidePanel(
    settings: TextArtSettings,
    swatches: IntArray,
    touch: StageTouch,
    onTouch: (StageTouch) -> Unit,
    brushRadius: Float,
    onBrushRadius: (Float) -> Unit,
    onChange: ArtChange,
    cutout: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val h = settings.hide
    var band by rememberSaveable { mutableIntStateOf(0) }
    fun isHidden(rgb: Int): Boolean {
        val lab = ArtColors.oklab(rgb)
        return h.colors.any { ArtColors.oklab((it and 0xFFFFFF).toInt()).distance(lab) < 0.02f }
    }
    PanelColumn {
        PanelCard {
            SectionTitle(stringResource(R.string.textart_hide_cutout))
            PanelHint(stringResource(R.string.textart_hide_cutout_hint))
            cutout()
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_hide_colors))
            if (swatches.isNotEmpty()) {
                SwatchCookies(
                    swatches = swatches,
                    hidden = ::isHidden,
                    onToggle = { rgb ->
                        onChange(null) {
                            val lab = ArtColors.oklab(rgb)
                            val list = it.hide.colors
                            val near = list.indexOfFirst { c -> ArtColors.oklab((c and 0xFFFFFF).toInt()).distance(lab) < 0.02f }
                            it.copy(hide = it.hide.copy(colors = if (near >= 0) list.filterIndexed { i, _ -> i != near } else list + (rgb.toLong() and 0xFFFFFF)))
                        }
                    },
                )
            }
            SwitchCard(
                icon = Icons.Rounded.Colorize,
                title = stringResource(R.string.textart_hide_pick),
                subtitle = stringResource(R.string.textart_hide_pick_hint),
                checked = touch == StageTouch.PICK,
                onCheckedChange = { on -> onTouch(if (on) StageTouch.PICK else StageTouch.NONE) },
            )
            if (h.colors.any { c -> swatches.none { s -> ArtColors.oklab(s).distance(ArtColors.oklab((c and 0xFFFFFF).toInt())) < 0.02f } }) {
                // Colours picked from the picture itself, not among the swatches.
                SwatchCookies(
                    swatches = h.colors.map { (it and 0xFFFFFF).toInt() }.filter { c -> swatches.none { s -> ArtColors.oklab(s).distance(ArtColors.oklab(c)) < 0.02f } }.toIntArray(),
                    hidden = { true },
                    onToggle = { rgb -> onChange(null) { it.copy(hide = it.hide.copy(colors = it.hide.colors.filter { c -> (c and 0xFFFFFF).toInt() != rgb })) } },
                )
            }
            ValueSlider(
                label = stringResource(R.string.textart_tolerance),
                valueText = "${(h.tolerance * 250).roundToInt()} %",
                value = h.tolerance,
                onValueChange = { v -> onChange("tolerance") { it.copy(hide = it.hide.copy(tolerance = (v * 1000).roundToInt() / 1000f)) } },
                valueRange = 0.01f..0.4f,
                defaultValue = 0.08f,
            )
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_hide_hues))
            PanelHint(stringResource(R.string.textart_hide_hues_hint))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                HueRing(
                    bands = h.hues,
                    picked = band,
                    onBands = { step, bands -> onChange(step) { it.copy(hide = it.hide.copy(hues = bands)) } },
                    onPicked = { band = it },
                )
            }
            if (h.hues.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    h.hues.forEachIndexed { i, b ->
                        FilterChip(
                            selected = i == band,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                band = i
                            },
                            label = { Text("${degrees(b.start)} - ${degrees(b.end)}") },
                            leadingIcon = { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(hueColor((b.start + ((b.end - b.start + 360f) % 360f) / 2f) % 360f))) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                val picked = h.hues.getOrNull(band)
                if (picked != null) {
                    ValueSlider(
                        label = stringResource(R.string.textart_hue_min_chroma),
                        valueText = "${(picked.minChroma * 500).roundToInt()} %",
                        value = picked.minChroma,
                        onValueChange = { v -> onChange("chroma:$band") { it.copy(hide = it.hide.copy(hues = it.hide.hues.mapIndexed { i, x -> if (i == band) x.copy(minChroma = (v * 1000).roundToInt() / 1000f) else x })) } },
                        valueRange = 0f..0.2f,
                        defaultValue = HueBand().minChroma,
                    )
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onChange(null) { it.copy(hide = it.hide.copy(hues = it.hide.hues.filterIndexed { i, _ -> i != band })) }
                            band = 0
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.textart_hue_remove))
                    }
                }
            }
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_hide_light))
            RangeSlider(
                value = h.lightFrom..h.lightTo,
                onValueChange = { r ->
                    if ((r.start * 50).roundToInt() != (h.lightFrom * 50).roundToInt() || (r.endInclusive * 50).roundToInt() != (h.lightTo * 50).roundToInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    onChange("light") { it.copy(hide = it.hide.copy(lightFrom = round2(r.start), lightTo = round2(r.endInclusive))) }
                },
                valueRange = 0f..1f,
            )
            Text(
                stringResource(R.string.textart_hide_light_range, (h.lightFrom * 100).roundToInt(), (h.lightTo * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchCard(
                icon = Icons.Rounded.SelectAll,
                title = stringResource(R.string.textart_hide_invert),
                subtitle = stringResource(R.string.textart_hide_invert_hint),
                checked = h.invert,
                onCheckedChange = { on -> onChange(null) { it.copy(hide = it.hide.copy(invert = on)) } },
            )
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_brush))
            ConnectedChoiceGroup(
                options = listOf(StageTouch.NONE, StageTouch.BRUSH_HIDE, StageTouch.BRUSH_SHOW),
                selected = if (touch == StageTouch.PICK) StageTouch.NONE else touch,
                label = {
                    stringResource(
                        when (it) {
                            StageTouch.BRUSH_HIDE -> R.string.textart_brush_hide
                            StageTouch.BRUSH_SHOW -> R.string.textart_brush_show
                            else -> R.string.textart_brush_off
                        },
                    )
                },
                onSelect = onTouch,
            )
            PanelHint(stringResource(R.string.textart_brush_hint))
            ValueSlider(
                label = stringResource(R.string.textart_brush_size),
                valueText = "${(brushRadius * 100).roundToInt()} %",
                value = brushRadius,
                onValueChange = onBrushRadius,
                valueRange = 0.005f..0.2f,
                defaultValue = ArtStroke().radius,
                icon = Icons.Rounded.Brush,
            )
            if (h.strokes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onChange(null) { it.copy(hide = it.hide.copy(strokes = it.hide.strokes.dropLast(1))) }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.textart_brush_undo))
                    }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onChange(null) { it.copy(hide = it.hide.copy(strokes = emptyList())) }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.textart_brush_clear))
                    }
                }
            }
            ValueSlider(
                label = stringResource(R.string.textart_alpha_cut),
                valueText = "${(h.alphaCut * 100).roundToInt()} %",
                value = h.alphaCut,
                onValueChange = { v -> onChange("alphaCut") { it.copy(hide = it.hide.copy(alphaCut = round2(v))) } },
                valueRange = 0.05f..0.95f,
                defaultValue = 0.5f,
            )
            PanelHint(stringResource(R.string.textart_alpha_cut_hint))
        }
    }
}

// -----------------------------------------------------------------------------
// Layout: frame, words around the art, the file's own form
// -----------------------------------------------------------------------------

@Composable
internal fun ArtLayoutPanel(settings: TextArtSettings, ansi: Boolean, onChange: ArtChange) {
    val l = settings.layout
    val cp437 = ansi && l.encoding == ArtEncoding.CP437
    PanelColumn {
        PanelCard {
            SectionTitle(stringResource(R.string.textart_frame))
            OptionTileRow(
                options = FrameStyle.entries.map { f ->
                    TileOption(f, stringResource(f.labelRes), preview = { color ->
                        val fr = TextArtLayout.frameOf(f, cp437)
                        val sample = if (fr == null) "   \n   \n   " else {
                            fun c(code: Int) = String(Character.toChars(code))
                            c(fr.topLeft) + c(fr.horizontal) + c(fr.topRight) + "\n" + c(fr.vertical) + " " + c(fr.vertical) + "\n" + c(fr.bottomLeft) + c(fr.horizontal) + c(fr.bottomRight)
                        }
                        FrameSample(sample, color)
                    })
                },
                selected = l.frame,
                onSelect = { f -> onChange(null) { it.copy(layout = it.layout.copy(frame = f)) } },
            )
            ValueSlider(
                label = stringResource(R.string.textart_margin),
                valueText = "${l.margin}",
                value = l.margin.toFloat(),
                onValueChange = { v -> onChange("margin") { it.copy(layout = it.layout.copy(margin = v.roundToInt())) } },
                valueRange = 0f..10f,
                steps = 9,
                defaultValue = 0f,
            )
        }
        PanelCard {
            ArtTextField(value = l.title, label = stringResource(R.string.textart_title), onValue = { v -> onChange("title") { it.copy(layout = it.layout.copy(title = v)) } })
            ArtTextField(value = l.signature, label = stringResource(R.string.textart_signature), onValue = { v -> onChange("signature") { it.copy(layout = it.layout.copy(signature = v)) } })
            if (cp437) {
                ArtTextField(value = l.group, label = stringResource(R.string.textart_group), onValue = { v -> onChange("group") { it.copy(layout = it.layout.copy(group = v)) } })
                PanelHint(stringResource(R.string.textart_sauce_hint))
            }
        }
        PanelCard {
            if (ansi) {
                SectionTitle(stringResource(R.string.textart_encoding))
                ConnectedChoiceGroup(
                    options = ArtEncoding.entries,
                    selected = l.encoding,
                    label = { stringResource(it.labelRes) },
                    onSelect = { e ->
                        onChange(null) {
                            val next = it.copy(layout = it.layout.copy(encoding = e))
                            if (e == ArtEncoding.CP437) next.copy(palette = next.palette.copy(depth = ArtColor.ANSI_16, bright = BrightMode.BOLD), mode = if (next.mode == ArtMode.BRAILLE) ArtMode.HALF_BLOCKS else next.mode) else next
                        }
                    },
                )
                PanelHint(stringResource(R.string.textart_encoding_hint))
            }
            SectionTitle(stringResource(R.string.textart_line_ending))
            ConnectedChoiceGroup(
                options = LineEnding.entries,
                selected = l.lineEnding,
                label = { stringResource(it.labelRes) },
                onSelect = { e -> onChange(null) { it.copy(layout = it.layout.copy(lineEnding = e)) } },
            )
            SwitchCard(
                icon = Icons.Rounded.ContentCut,
                title = stringResource(R.string.textart_trim),
                subtitle = stringResource(R.string.textart_trim_hint),
                checked = l.trimTrailing,
                onCheckedChange = { on -> onChange(null) { it.copy(layout = it.layout.copy(trimTrailing = on)) } },
            )
            if (!cp437) {
                SwitchCard(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.textart_bom),
                    subtitle = stringResource(R.string.textart_bom_hint),
                    checked = l.bom,
                    onCheckedChange = { on -> onChange(null) { it.copy(layout = it.layout.copy(bom = on)) } },
                )
            }
            if (ansi && !cp437) {
                SwitchCard(
                    icon = Icons.Rounded.RestartAlt,
                    title = stringResource(R.string.textart_reset_lines),
                    subtitle = stringResource(R.string.textart_reset_lines_hint),
                    checked = l.resetEachLine,
                    onCheckedChange = { on -> onChange(null) { it.copy(layout = it.layout.copy(resetEachLine = on)) } },
                )
            }
            ValueSlider(
                label = stringResource(R.string.textart_max_width),
                valueText = if (l.maxWidth <= 0) stringResource(R.string.textart_off) else "${l.maxWidth}",
                value = l.maxWidth.toFloat(),
                onValueChange = { v -> onChange("maxWidth") { it.copy(layout = it.layout.copy(maxWidth = v.roundToInt())) } },
                valueRange = 0f..240f,
                defaultValue = 0f,
                input = ValueInput(step = 1f),
            )
            PanelHint(stringResource(R.string.textart_max_width_hint))
        }
    }
}

// -----------------------------------------------------------------------------
// Check: what the file holds, what to look out for, copying it
// -----------------------------------------------------------------------------

@Composable
internal fun ArtCheckPanel(
    report: TextArtReport?,
    ansi: Boolean,
    cp437: Boolean,
    onCopy: (CopyKind) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    PanelColumn {
        if (report == null) {
            PanelHint(stringResource(R.string.textart_check_working))
            return@PanelColumn
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatBlock("${report.columns}\u00D7${report.rows}", stringResource(R.string.textart_stat_grid), modifier = Modifier.weight(1f))
            StatBlock(sizeText(report.bytes), stringResource(R.string.textart_stat_size), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatBlock("${report.characters.size}", stringResource(R.string.textart_stat_chars), modifier = Modifier.weight(1f))
            StatBlock(if (ansi) "${report.colors.size}" else "-", stringResource(R.string.textart_stat_colors), modifier = Modifier.weight(1f))
        }
        if (report.warnings.isEmpty()) {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.textart_check_clean), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                }
            }
        } else {
            report.warnings.forEach { w ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (w == ArtWarning.EMPTY || w == ArtWarning.UNENCODABLE) scheme.errorContainer else scheme.tertiaryContainer)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = if (w == ArtWarning.EMPTY || w == ArtWarning.UNENCODABLE) scheme.onErrorContainer else scheme.onTertiaryContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(w.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (w == ArtWarning.EMPTY || w == ArtWarning.UNENCODABLE) scheme.onErrorContainer else scheme.onTertiaryContainer,
                    )
                }
            }
        }
        if (report.characters.isNotEmpty()) {
            PanelCard {
                SectionTitle(stringResource(R.string.textart_check_chars))
                val total = report.characters.sumOf { it.second }
                val top = report.characters.take(TOP_SHOWN)
                val most = top.first().second.coerceAtLeast(1)
                top.forEach { (code, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            String(Character.toChars(code)),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            color = scheme.onSurface,
                            modifier = Modifier.width(32.dp),
                        )
                        Box(modifier = Modifier.weight(1f).height(12.dp).clip(CircleShape).background(scheme.surfaceContainerHighest)) {
                            Box(modifier = Modifier.fillMaxWidth(count.toFloat() / most).height(12.dp).clip(CircleShape).background(scheme.primary))
                        }
                        Text(percent(count, total), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                    }
                }
            }
        }
        if (ansi && report.colors.isNotEmpty()) {
            PanelCard {
                SectionTitle(stringResource(R.string.textart_check_colors))
                val total = report.colors.sumOf { it.second }.coerceAtLeast(1)
                // The colours as one bar, each as wide as the cells it takes.
                Row(modifier = Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))) {
                    report.colors.take(COLORS_SHOWN).forEach { (rgb, count) ->
                        Box(modifier = Modifier.weight(count.toFloat() / total).fillMaxSize().background(artColor(rgb)))
                    }
                }
            }
        }
        PanelCard {
            SectionTitle(stringResource(R.string.textart_copy))
            PanelHint(stringResource(if (ansi) R.string.textart_copy_ansi_hint else R.string.textart_copy_hint))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onCopy(CopyKind.PLAIN)
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.textart_copy_plain), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onCopy(CopyKind.MARKDOWN)
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.textart_copy_markdown), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (ansi && !cp437) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onCopy(CopyKind.ANSI)
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.textart_copy_escapes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** What "copy" puts on the clipboard. */
internal enum class CopyKind { PLAIN, MARKDOWN, ANSI }

// -----------------------------------------------------------------------------
// Small parts
// -----------------------------------------------------------------------------

/** A text field for one line of the settings. */
@Composable
internal fun ArtTextField(value: String, label: String, onValue: (String) -> Unit, monospace: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        textStyle = if (monospace) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun round2(v: Float): Float = (v * 100).roundToInt() / 100f

private fun signedPercent(v: Float): String {
    val p = (v * 100).roundToInt()
    return if (p > 0) "+$p %" else "$p %"
}

/** [bytes] as a short size. */
internal fun sizeText(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}

private const val TOP_SHOWN = 12
private const val COLORS_SHOWN = 48
