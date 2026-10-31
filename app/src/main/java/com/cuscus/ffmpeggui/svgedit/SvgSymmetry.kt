@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.svgedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.svg.Symmetry
import com.cuscus.ffmpeggui.core.vector.svg.SymmetryKind
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlin.math.roundToInt

/*
 * The SVG editor's symmetry and grid, in one sheet. The symmetry's kinds are
 * big tiles, each drawing a little leaf as the kind repeats it (two halves,
 * four quarters, a turn, a kaleidoscope); chosen, the tile fills and its leaf
 * blooms. A turn says how many parts it has. Turned off, the copies are kept
 * as shapes or taken away. Below, the grid: how fine, and whether moves snap
 * to it.
 */

/** One kind of symmetry the sheet offers (OFF: none). */
private enum class SymChoice(val kind: SymmetryKind?, val label: Int) {
    OFF(null, R.string.svg_sym_off),
    MIRROR_X(SymmetryKind.MIRROR_X, R.string.svg_sym_mirror_x),
    MIRROR_Y(SymmetryKind.MIRROR_Y, R.string.svg_sym_mirror_y),
    QUAD(SymmetryKind.QUAD, R.string.svg_sym_quad),
    RADIAL(SymmetryKind.RADIAL, R.string.svg_sym_radial),
    KALEIDO(SymmetryKind.KALEIDO, R.string.svg_sym_kaleido),
}

@Composable
internal fun SvgSymmetrySheet(
    current: Symmetry?,
    /** The page's centre in the drawing's units: where a new symmetry turns about. */
    centreX: Double,
    centreY: Double,
    grid: Int,
    snapToGrid: Boolean,
    onSymmetry: (Symmetry?, Boolean) -> Unit,
    onGrid: (Int) -> Unit,
    onSnapToGrid: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var count by rememberSaveable { mutableStateOf(current?.count ?: 6) }
    var askingKeep by remember { mutableStateOf(false) }
    val chosen = SymChoice.entries.firstOrNull { it.kind == current?.kind } ?: SymChoice.OFF
    fun choose(choice: SymChoice) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        val kind = choice.kind
        if (kind == null) {
            if (current != null) askingKeep = true
            return
        }
        askingKeep = false
        onSymmetry(Symmetry(kind, count, current?.cx ?: centreX, current?.cy ?: centreY), true)
    }
    PeekThroughSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingContainer(
                    from = MaterialShapes.Cookie9Sided,
                    to = MaterialShapes.Flower,
                    morphed = current != null,
                    color = if (current != null) scheme.tertiary else scheme.tertiaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.Flare, contentDescription = null, tint = if (current != null) scheme.onTertiary else scheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.svg_symmetry), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(
                        if (current == null) stringResource(R.string.svg_sym_intro) else stringResource(R.string.svg_sym_on, current.parts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            PanelCard {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SymChoice.entries.forEach { choice ->
                        SymTile(choice, count, selected = choice == chosen, modifier = Modifier.weight(1f)) { choose(choice) }
                    }
                }
                AnimatedVisibility(
                    visible = current?.kind == SymmetryKind.RADIAL || current?.kind == SymmetryKind.KALEIDO,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    ValueSlider(
                        label = stringResource(R.string.svg_sym_parts),
                        valueText = count.toString(),
                        value = count.toFloat(),
                        onValueChange = { v ->
                            val n = v.roundToInt().coerceIn(2, 24)
                            if (n != count) {
                                count = n
                                current?.let { onSymmetry(it.copy(count = n), true) }
                            }
                        },
                        valueRange = 2f..24f,
                        steps = 21,
                        defaultValue = 6f,
                    )
                }
                AnimatedVisibility(visible = askingKeep) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelHint(stringResource(R.string.svg_sym_stop_hint))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    askingKeep = false
                                    onSymmetry(null, true)
                                },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) { Text(stringResource(R.string.svg_sym_keep), textAlign = TextAlign.Center) }
                            OutlinedButton(
                                onClick = {
                                    askingKeep = false
                                    onSymmetry(null, false)
                                },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) { Text(stringResource(R.string.svg_sym_remove), textAlign = TextAlign.Center) }
                        }
                    }
                }
                PanelHint(stringResource(R.string.svg_sym_hint))
            }

            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GridOn, contentDescription = null, tint = scheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.svg_grid), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.primary)
                }
                ValueSlider(
                    label = stringResource(R.string.svg_grid_squares),
                    valueText = if (grid == 0) stringResource(R.string.svg_grid_off) else grid.toString(),
                    value = grid.toFloat(),
                    onValueChange = { onGrid(it.roundToInt()) },
                    valueRange = 0f..64f,
                    defaultValue = 0f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.svg_grid_snap), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = snapToGrid, onCheckedChange = {
                        haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onSnapToGrid(it)
                    }, enabled = grid > 0)
                }
            }
        }
    }
}

/** A kind of symmetry as a tile: a leaf drawn as many times, and as mirrored, as the kind makes it. */
@Composable
private fun SymTile(choice: SymChoice, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(if (selected) scheme.tertiaryContainer else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "symTile")
    val bloom by animateFloatAsState(if (selected) 1f else 0.82f, motion.defaultSpatialSpec(), label = "symBloom")
    val turn by animateFloatAsState(if (selected) 0f else -20f, motion.slowSpatialSpec(), label = "symTurn")
    val leaf = if (selected) scheme.tertiary else scheme.onSurfaceVariant
    val axis = scheme.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .springPress(interaction, pressedScale = 0.9f)
            .clip(RoundedCornerShape(if (selected) 32.dp else 20.dp))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Square,
            to = MaterialShapes.Cookie12Sided,
            morphed = selected || pressed,
            color = scheme.surfaceContainerLowest,
            modifier = Modifier.size(64.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp).graphicsLayer { rotationZ = turn }) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2
                // A leaf in the top right quarter, off the centre.
                val shape = Path().apply {
                    moveTo(c.x + r * 0.12f, c.y - r * 0.18f)
                    cubicTo(c.x + r * 0.2f, c.y - r * 0.95f, c.x + r * 0.85f, c.y - r * 0.8f, c.x + r * 0.62f, c.y - r * 0.28f)
                    cubicTo(c.x + r * 0.5f, c.y - r * 0.1f, c.x + r * 0.25f, c.y - r * 0.08f, c.x + r * 0.12f, c.y - r * 0.18f)
                    close()
                }
                fun leafAt(alpha: Float = 1f) = drawPath(shape, leaf.copy(alpha = alpha))
                val n = count.coerceIn(2, 24)
                scale(bloom, pivot = c) {
                    when (choice.kind) {
                        null -> leafAt()
                        SymmetryKind.MIRROR_X -> {
                            leafAt()
                            scale(-1f, 1f, pivot = c) { leafAt(0.6f) }
                        }
                        SymmetryKind.MIRROR_Y -> {
                            leafAt()
                            scale(1f, -1f, pivot = c) { leafAt(0.6f) }
                        }
                        SymmetryKind.QUAD -> {
                            leafAt()
                            scale(-1f, 1f, pivot = c) { leafAt(0.6f) }
                            scale(1f, -1f, pivot = c) { leafAt(0.6f) }
                            scale(-1f, -1f, pivot = c) { leafAt(0.6f) }
                        }
                        SymmetryKind.RADIAL -> for (i in 0 until n) rotate(360f * i / n, pivot = c) { leafAt(if (i == 0) 1f else 0.6f) }
                        SymmetryKind.KALEIDO -> for (i in 0 until n) rotate(360f * i / n, pivot = c) {
                            leafAt(if (i == 0) 1f else 0.6f)
                            withTransform({ scale(-1f, 1f, pivot = c) }) { leafAt(0.45f) }
                        }
                    }
                }
                if (choice.kind != null) {
                    drawCircle(axis, radius = 2.dp.toPx(), center = c)
                } else {
                    drawCircle(axis, radius = r * 0.9f, center = c, style = Stroke(width = 1.dp.toPx()))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(choice.label),
            style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onTertiaryContainer else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
