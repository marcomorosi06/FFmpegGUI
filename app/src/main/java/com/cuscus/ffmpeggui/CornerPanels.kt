@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The corners tool of the picture editor: from square to a circle, the whole
 * picture (border included) rounded at export. Ready-made roundnesses as
 * tiles, each a little square rounded as much, and a slider for anything in
 * between; then what fills the cut-off corners: transparent (preselected
 * where the format holds it) or a colour (black by default elsewhere).
 */

/** Ready-made roundnesses: square, soft, round, very round, circle. */
private val CORNER_PRESETS = listOf(
    0f to R.string.corners_square,
    0.12f to R.string.corners_soft,
    0.3f to R.string.corners_round,
    0.6f to R.string.corners_very_round,
    1f to R.string.corners_circle,
)

/** The corners of the picture: [corners] 0..1, as [format] will hold what they cut off. */
@Composable
internal fun ImageCornersPanel(corners: Float, cornerColor: Long?, format: OutputFormat, onChange: ImageChange) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    // What fills them now: null is transparent (only where the format holds it, else black).
    val fill = ImageEdit(cornerColor = cornerColor).cornerFillFor(format)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = CORNER_PRESETS.map { (value, label) ->
                TileOption(
                    value = value,
                    label = stringResource(label),
                    preview = { color -> CornerSample(corners = value, color = color) },
                )
            },
            selected = CORNER_PRESETS.firstOrNull { abs(it.first - corners) < 0.005f }?.first,
            onSelect = { value -> onChange(null) { it.copy(corners = value) } },
        )
        CompactSlider(
            label = stringResource(R.string.corners_amount),
            valueText = stringResource(R.string.value_percent, (corners * 100).roundToInt()),
            value = corners,
            onValueChange = { v -> onChange("corners") { it.copy(corners = v) } },
            valueRange = 0f..1f,
            defaultValue = 0f,
        )
        // What fills the cut-off corners: transparent (where the format holds it) or a colour.
        Text(stringResource(R.string.corners_fill), style = MaterialTheme.typography.titleSmallEmphasized)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (format.holdsTransparency) {
                TransparentSwatch(
                    chosen = fill == null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(null) { it.copy(cornerColor = ImageEdit.TRANSPARENT_CORNERS) }
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            ColorSwatchRow(
                colors = CORNER_COLORS,
                // None of the colours while transparent.
                selected = fill?.let { 0xFF000000L or it.toLong() } ?: 0xFF000000L,
                noneSelected = fill == null,
                onSelect = { c -> onChange(null) { it.copy(cornerColor = c or 0xFF000000L) } },
                modifier = Modifier.weight(1f),
            )
        }
        // What the cut-off corners become, which changes with the format and the fill.
        AnimatedContent(
            targetState = fill == null,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "cornersNote",
        ) { transparent ->
            NoteCard(
                icon = if (transparent) Icons.Outlined.Layers else Icons.Outlined.FormatColorFill,
                text = stringResource(
                    if (transparent) R.string.corners_transparent_hint else if (format.holdsTransparency) R.string.corners_colour_hint else R.string.corners_white_hint,
                    stringResource(format.labelRes),
                ),
            )
        }
    }
}

/** Colours offered for the cut-off corners. */
private val CORNER_COLORS = listOf(
    0xFF000000L, 0xFFFFFFFFL, 0xFFF5EBDCL, 0xFF9E9E9EL, 0xFF1B1F5AL,
    0xFFFF5252L, 0xFFFFC857L, 0xFF69F0AEL, 0xFF40C4FFL, 0xFFFF80ABL,
)

/** The transparent choice beside the colours: a checkerboard that morphs when chosen. */
@Composable
private fun TransparentSwatch(chosen: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.corners_fill_transparent)
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .springPress(interaction, pressedScale = 0.85f)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics {
                selected = chosen
                contentDescription = label
            },
    ) {
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie7Sided,
            morphed = chosen,
            color = if (chosen) scheme.primary else scheme.outlineVariant,
            modifier = Modifier.size(36.dp),
        ) {
            Checkerboard(modifier = Modifier.size(if (chosen) 22.dp else 30.dp).clip(CircleShape), cell = 5.dp)
        }
    }
}

/** A little square rounded as [corners] rounds the picture, its radius springing to it. */
@Composable
private fun CornerSample(corners: Float, color: Color) {
    val shown by animateFloatAsState(corners, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "cornerSample")
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(percent = (shown * 50).roundToInt().coerceIn(0, 50)))
            .background(color),
    )
}
