@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui.svgedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.CompactSlider
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.svg.CONNECT_ARROWS
import com.cuscus.ffmpeggui.core.vector.svg.ConnectorStyle
import com.cuscus.ffmpeggui.core.vector.svg.PaintKind
import com.cuscus.ffmpeggui.core.vector.svg.connectorStyle
import com.cuscus.ffmpeggui.core.vector.svg.isLineLike
import com.cuscus.ffmpeggui.core.vector.svg.restyleConnector
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgPaint
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.SvgStyle
import com.cuscus.ffmpeggui.core.vector.svg.SvgText
import com.cuscus.ffmpeggui.core.vector.svg.cssLength
import com.cuscus.ffmpeggui.core.vector.svg.find
import com.cuscus.ffmpeggui.core.vector.svg.ownBounds
import com.cuscus.ffmpeggui.core.vector.svg.resolvedStyle
import com.cuscus.ffmpeggui.core.vector.svg.svgNumber
import com.cuscus.ffmpeggui.core.vector.svg.update
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import kotlin.math.roundToInt

/*
 * How the selected node looks: its fill and its outline (a colour, or none),
 * how wide and how dashed the outline is, how see-through it all is, how
 * round a rectangle's corners are; for a text, its words, size, weight and
 * font. Everything shows on the stage as it changes; each control is one
 * step of undo however long it is dragged.
 */

/** Colours offered first: vivid ones, and black, white and greys. */
private val SWATCHES = listOf(
    0xFF000000L, 0xFFFFFFFFL, 0xFF9E9E9EL, 0xFFE53935L, 0xFFFF7043L, 0xFFFFB300L, 0xFFFDD835L,
    0xFF43A047L, 0xFF00ACC1L, 0xFF1E88E5L, 0xFF3949ABL, 0xFF8E24AAL, 0xFFD81B60L, 0xFF6D4C41L,
)

/** How an outline is drawn. */
private enum class Dash(val label: Int) { SOLID(R.string.svg_dash_solid), DASHED(R.string.svg_dash_dashed), DOTTED(R.string.svg_dash_dotted) }

/** The fonts offered: the families every SVG reader has. */
private enum class Font(val family: String, val label: Int) {
    SANS("sans-serif", R.string.svg_font_sans), SERIF("serif", R.string.svg_font_serif), MONO("monospace", R.string.svg_font_mono)
}

@Composable
internal fun SvgStyleSheet(
    doc: SvgDocument,
    key: Int,
    onChange: (String, (SvgDocument) -> SvgDocument) -> Unit,
    onDismiss: () -> Unit,
    /** What else is selected: its fill, outline and opacity change with [key]'s. */
    others: List<Int> = emptyList(),
) {
    val node = doc.find(key) ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scheme = MaterialTheme.colorScheme
    val resolved = doc.resolvedStyle(key)
    val own = node.style
    val kind = kindOf(node)
    val isLine = node is SvgShape && node.isLineLike
    val scale = minOf(doc.viewBox.width, doc.viewBox.height)
    fun restyle(step: String, change: (SvgStyle) -> SvgStyle) = onChange(step) { d ->
        (listOf(key) + others).distinct().fold(d) { acc, k -> acc.update(k) { it.withStyle(change(it.style)) } }
    }

    PeekThroughSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingContainer(
                    from = kind.shape,
                    to = kind.shape,
                    morphed = false,
                    color = resolved.fill.takeIf { it.kind == PaintKind.COLOR }?.let { androidx.compose.ui.graphics.Color((it.argb or 0xFF000000L).toInt()) }
                        ?: scheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {}
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.svg_style), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(
                        if (others.isEmpty()) node.label ?: stringResource(kind.label) else pluralStringResource(R.plurals.svg_selected_count, others.size + 1, others.size + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (node is SvgText) TextControls(node, onChange, key)

            if (node is SvgShape && node.isLineLike) ConnectorControls(node, onChange, key)

            if (!isLine) {
                PaintCard(
                    title = stringResource(R.string.svg_fill),
                    paint = resolved.fill,
                    onPaint = { p -> restyle("fill") { it.copy(fill = p) } },
                )
            }
            PaintCard(
                title = stringResource(R.string.svg_outline),
                paint = resolved.stroke,
                onPaint = { p ->
                    restyle("stroke") {
                        // An outline chosen where there was none comes with a width that shows.
                        val width = if (resolved.stroke.kind == PaintKind.NONE && (it.strokeWidth ?: resolved.strokeWidth) < scale * 0.004) scale * 0.01 else it.strokeWidth
                        it.copy(stroke = p, strokeWidth = width)
                    }
                },
            )
            if (resolved.stroke.kind != PaintKind.NONE) {
                PanelCard {
                    val maxWidth = (scale * 0.1).toFloat().coerceAtLeast(1f)
                    CompactSlider(
                        label = stringResource(R.string.svg_outline_width),
                        valueText = svgNumber(resolved.strokeWidth),
                        value = resolved.strokeWidth.toFloat().coerceIn(0f, maxWidth),
                        onValueChange = { v -> restyle("strokeWidth") { it.copy(strokeWidth = v.toDouble()) } },
                        valueRange = 0f..maxWidth,
                    )
                    val dashNow = when {
                        resolved.other["stroke-dasharray"].isNullOrBlank() || resolved.other["stroke-dasharray"] == "none" -> Dash.SOLID
                        resolved.other["stroke-dasharray"]?.trim()?.startsWith("0 ") == true -> Dash.DOTTED
                        else -> Dash.DASHED
                    }
                    ConnectedChoiceGroup(
                        options = Dash.entries,
                        selected = dashNow,
                        label = { stringResource(it.label) },
                        onSelect = { dash ->
                            restyle("dash") {
                                val w = (it.strokeWidth ?: resolved.strokeWidth).coerceAtLeast(0.5)
                                val other = LinkedHashMap(it.other)
                                when (dash) {
                                    Dash.SOLID -> other.remove("stroke-dasharray")
                                    Dash.DASHED -> other["stroke-dasharray"] = "${svgNumber(w * 3)} ${svgNumber(w * 2)}"
                                    Dash.DOTTED -> {
                                        other["stroke-dasharray"] = "0 ${svgNumber(w * 2)}"
                                        other["stroke-linecap"] = "round"
                                    }
                                }
                                it.copy(other = other)
                            }
                        },
                    )
                }
            }
            PanelCard {
                val opacity = (own.opacity ?: 1.0).toFloat()
                CompactSlider(
                    label = stringResource(R.string.svg_opacity),
                    valueText = stringResource(R.string.value_percent, (opacity * 100).roundToInt()),
                    value = opacity,
                    onValueChange = { v -> restyle("opacity") { it.copy(opacity = v.toDouble().takeIf { o -> o < 0.999 }) } },
                    valueRange = 0f..1f,
                    defaultValue = 1f,
                )
                if (node is SvgShape && node.tag == "rect") {
                    val box = doc.ownBounds(node)
                    val maxR = ((box?.let { minOf(it.width, it.height) } ?: 0.0) / 2).toFloat().coerceAtLeast(0.01f)
                    val r = (node.geometry["rx"]?.let { cssLength(it) } ?: node.geometry["ry"]?.let { cssLength(it) } ?: 0.0).toFloat()
                    CompactSlider(
                        label = stringResource(R.string.svg_corners),
                        valueText = svgNumber(r.toDouble()),
                        value = r.coerceIn(0f, maxR),
                        onValueChange = { v ->
                            onChange("corners") { d ->
                                d.update(key) { n ->
                                    if (n is SvgShape) n.copy(geometry = LinkedHashMap(n.geometry).apply { put("rx", svgNumber(v.toDouble())); remove("ry") }) else n
                                }
                            }
                        },
                        valueRange = 0f..maxR,
                        defaultValue = 0f,
                    )
                }
            }
        }
    }
}

/** Where a line's arrows are. */
private enum class Arrows(val value: String?, val label: Int) {
    NONE(null, R.string.svg_arrows_none), END("end", R.string.svg_arrows_end), START("start", R.string.svg_arrows_start), BOTH("both", R.string.svg_arrows_both)
}

/** How a line or a connector runs (straight, in square turns, curved) and where its arrows are. */
@Composable
private fun ConnectorControls(node: SvgShape, onChange: (String, (SvgDocument) -> SvgDocument) -> Unit, key: Int) {
    val style = node.connectorStyle
    val arrows = Arrows.entries.firstOrNull { it.value == node.attributes[CONNECT_ARROWS] } ?: Arrows.NONE
    PanelCard {
        Text(stringResource(R.string.svg_connector_run), style = MaterialTheme.typography.titleSmallEmphasized)
        ConnectedChoiceGroup(
            options = ConnectorStyle.entries,
            selected = style,
            label = {
                stringResource(
                    when (it) {
                        ConnectorStyle.STRAIGHT -> R.string.svg_connector_straight
                        ConnectorStyle.ELBOW -> R.string.svg_connector_elbow
                        ConnectorStyle.CURVE -> R.string.svg_connector_curve
                    },
                )
            },
            onSelect = { s -> onChange("connector") { d -> d.restyleConnector(key, s, arrows.value) } },
        )
        Text(stringResource(R.string.svg_arrows), style = MaterialTheme.typography.titleSmallEmphasized)
        ConnectedChoiceGroup(
            options = Arrows.entries,
            selected = arrows,
            label = { stringResource(it.label) },
            onSelect = { a -> onChange("arrows") { d -> d.restyleConnector(key, style, a.value) } },
        )
        PanelHint(stringResource(R.string.svg_connector_hint))
    }
}

/** A fill or an outline: none, or a colour. */
@Composable
private fun PaintCard(title: String, paint: SvgPaint, onPaint: (SvgPaint) -> Unit) {
    PanelCard {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = paint.kind == PaintKind.NONE,
                onClick = { onPaint(SvgPaint.NONE) },
                label = { Text(stringResource(R.string.svg_paint_none)) },
                leadingIcon = { Icon(Icons.Rounded.FormatColorReset, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(16.dp),
            )
            if (paint.kind == PaintKind.URL) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.svg_paint_gradient), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val primary = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xFFFFFFFFL
        val tertiary = MaterialTheme.colorScheme.tertiary.toArgb().toLong() and 0xFFFFFFFFL
        ColorSwatchRow(
            colors = listOf(primary, tertiary) + SWATCHES,
            selected = if (paint.kind == PaintKind.COLOR) paint.argb else 0L,
            onSelect = { onPaint(SvgPaint.color(it)) },
            noneSelected = paint.kind != PaintKind.COLOR,
        )
    }
}

/** A text's words, size, weight and font. */
@Composable
private fun TextControls(node: SvgText, onChange: (String, (SvgDocument) -> SvgDocument) -> Unit, key: Int) {
    var words by remember(node.key) { mutableStateOf(node.text) }
    PanelCard {
        OutlinedTextField(
            value = words,
            onValueChange = { t ->
                words = t
                // Typed words replace a text placed piece by piece with a plain one.
                onChange("words") { d -> d.update(key) { n -> if (n is SvgText) n.copy(text = t, raw = null) else n } }
            },
            label = { Text(stringResource(R.string.svg_text_words)) },
            shape = RoundedCornerShape(20.dp),
            minLines = 1,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        val size = (node.style.fontSize ?: 16.0).toFloat()
        CompactSlider(
            label = stringResource(R.string.svg_text_size),
            valueText = svgNumber(size.toDouble()),
            value = size.coerceIn(2f, 600f),
            onValueChange = { v -> onChange("fontSize") { d -> d.update(key) { it.withStyle(it.style.copy(fontSize = v.toDouble())) } } },
            valueRange = 2f..600f,
        )
        val bold = node.style.fontWeight?.let { it == "bold" || (it.toIntOrNull() ?: 400) >= 600 } == true
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = bold,
                onClick = { onChange("bold") { d -> d.update(key) { it.withStyle(it.style.copy(fontWeight = if (bold) "normal" else "bold")) } } },
                label = { Text(stringResource(R.string.svg_text_bold)) },
                leadingIcon = { Icon(Icons.Rounded.FormatBold, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(16.dp),
            )
        }
        val family = node.style.fontFamily.orEmpty().lowercase()
        ConnectedChoiceGroup(
            options = Font.entries,
            selected = Font.entries.firstOrNull { family.startsWith(it.family) } ?: Font.SANS,
            label = { stringResource(it.label) },
            onSelect = { f -> onChange("font") { d -> d.update(key) { it.withStyle(it.style.copy(fontFamily = f.family)) } } },
        )
    }
}
