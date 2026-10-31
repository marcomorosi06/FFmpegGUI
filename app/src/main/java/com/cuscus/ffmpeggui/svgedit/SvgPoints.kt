@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.svgedit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.CompactSlider
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.svg.Bend
import com.cuscus.ffmpeggui.core.vector.svg.EditPath
import com.cuscus.ffmpeggui.core.vector.svg.svgNumber
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * The cards that take the toolbar's place while a finger does something else
 * on the stage: editing a path point by point (what joins the picked piece:
 * straight, a curve and how far it bends, a zig-zag or a wave and how many
 * and how tall; a point deleted; the shape closed or opened), placing the
 * points of a new line, and the paint bucket (its colour, how wide a gap it
 * closes).
 */

/** How a piece is joined, as the card offers it. */
private enum class PieceKind(val label: Int) {
    STRAIGHT(R.string.svg_piece_straight), CURVE(R.string.svg_piece_curve), ZIGZAG(R.string.svg_piece_zigzag), WAVE(R.string.svg_piece_wave)
}

private fun kindOf(bend: Bend): PieceKind = when (bend) {
    Bend.Straight -> PieceKind.STRAIGHT
    is Bend.Curve -> PieceKind.CURVE
    is Bend.ZigZag -> PieceKind.ZIGZAG
    is Bend.Wave -> PieceKind.WAVE
}

/** The card's frame: a badge, a title, what it holds, and its Done button. */
@Composable
private fun ModeCard(icon: ImageVector, title: String, onDone: () -> Unit, modifier: Modifier, busy: Boolean = false, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.surfaceContainerHigh)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(from = MaterialShapes.Cookie9Sided, to = MaterialShapes.Cookie12Sided, morphed = busy, color = scheme.tertiary, modifier = Modifier.size(44.dp)) {
                Icon(icon, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = busy, enter = fadeIn(), exit = fadeOut()) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
            Button(onClick = onDone, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.svg_done), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
        content()
    }
}

/**
 * Editing [path] point by point, [sel] picked: what joins the piece picked
 * (or the one after the point picked), deleting the point, closing the shape.
 */
@Composable
internal fun SvgPointsCard(
    path: EditPath,
    sel: PointSel?,
    onBend: (String, Int, Int, Bend) -> Unit,
    onRemove: (Int, Int) -> Unit,
    onClose: (Int, Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    ModeCard(Icons.Rounded.Timeline, stringResource(R.string.svg_points), onDone, modifier) {
        val sub = sel?.let { path.subs.getOrNull(it.sub) }
        // What was picked may be gone (undone): then nothing is.
        val valid = sub != null && when (sel) {
            is PointSel.Point -> sel.index < sub.points.size
            is PointSel.Piece -> sel.index < sub.pieces
            null -> false
        }
        if (sel == null || sub == null || !valid) {
            PanelHint(stringResource(R.string.svg_points_hint))
            return@ModeCard
        }
        val piece = when (sel) {
            is PointSel.Piece -> sel.index
            is PointSel.Point -> sel.index.takeIf { it < sub.pieces }
        }?.takeIf { it < sub.bends.size }
        AnimatedContent(
            targetState = piece != null,
            transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "pieceTitle",
        ) { has ->
            Text(
                stringResource(
                    when {
                        !has -> R.string.svg_points_last
                        sel is PointSel.Piece -> R.string.svg_points_piece
                        else -> R.string.svg_points_after
                    },
                ),
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
        }
        if (piece != null) {
            val bend = sub.bends[piece]
            val a = sub.points[piece]
            val b = sub.end(piece)
            val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-6)
            ConnectedChoiceGroup(
                options = PieceKind.entries,
                selected = kindOf(bend),
                label = { stringResource(it.label) },
                onSelect = { kind ->
                    if (kind == kindOf(bend)) return@ConnectedChoiceGroup
                    val next = when (kind) {
                        PieceKind.STRAIGHT -> Bend.Straight
                        PieceKind.CURVE -> EditPath.bentCurve(a, b, 0.35)
                        PieceKind.ZIGZAG -> Bend.ZigZag(6, len * 0.08)
                        PieceKind.WAVE -> Bend.Wave(3, len * 0.08)
                    }
                    onBend("kind", sel.sub, piece, next)
                },
            )
            when (bend) {
                is Bend.Curve -> {
                    val amount = EditPath.bendOf(a, b, bend).toFloat()
                    CompactSlider(
                        label = stringResource(R.string.svg_piece_bend),
                        valueText = stringResource(R.string.value_percent, (amount * 100).roundToInt()),
                        value = amount,
                        onValueChange = { v -> onBend("bend", sel.sub, piece, EditPath.bentCurve(a, b, v.toDouble())) },
                        valueRange = -1f..1f,
                        defaultValue = 0f,
                    )
                    PanelHint(stringResource(R.string.svg_piece_bend_hint))
                }
                is Bend.ZigZag -> {
                    CompactSlider(
                        label = stringResource(R.string.svg_piece_teeth),
                        valueText = bend.teeth.toString(),
                        value = bend.teeth.toFloat(),
                        onValueChange = { v -> onBend("teeth", sel.sub, piece, bend.copy(teeth = v.roundToInt().coerceAtLeast(1))) },
                        valueRange = 1f..60f,
                    )
                    CompactSlider(
                        label = stringResource(R.string.svg_piece_height),
                        valueText = svgNumber(bend.height),
                        value = bend.height.toFloat().coerceIn(0f, (len * 0.5).toFloat()),
                        onValueChange = { v -> onBend("height", sel.sub, piece, bend.copy(height = v.toDouble())) },
                        valueRange = 0f..(len * 0.5).toFloat(),
                    )
                }
                is Bend.Wave -> {
                    CompactSlider(
                        label = stringResource(R.string.svg_piece_waves),
                        valueText = bend.waves.toString(),
                        value = bend.waves.toFloat(),
                        onValueChange = { v -> onBend("waves", sel.sub, piece, bend.copy(waves = v.roundToInt().coerceAtLeast(1))) },
                        valueRange = 1f..40f,
                    )
                    CompactSlider(
                        label = stringResource(R.string.svg_piece_height),
                        valueText = svgNumber(bend.height),
                        value = bend.height.toFloat().coerceIn(0f, (len * 0.5).toFloat()),
                        onValueChange = { v -> onBend("height", sel.sub, piece, bend.copy(height = v.toDouble())) },
                        valueRange = 0f..(len * 0.5).toFloat(),
                    )
                }
                Bend.Straight -> Unit
            }
        }
        AnimatedVisibility(
            visible = sel is PointSel.Point,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Button(
                onClick = { onRemove(sel.sub, sel.index) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.svg_points_delete))
            }
        }
        SwitchCard(
            icon = Icons.Rounded.Timeline,
            title = stringResource(R.string.svg_points_closed),
            subtitle = stringResource(R.string.svg_points_closed_hint),
            checked = sub.closed,
            onCheckedChange = { onClose(sel.sub, it) },
        )
    }
}

/** Placing the points of a new line. */
@Composable
internal fun SvgBuildCard(onDone: () -> Unit, modifier: Modifier = Modifier) {
    ModeCard(Icons.Rounded.TouchApp, stringResource(R.string.svg_build), onDone, modifier) {
        PanelHint(stringResource(R.string.svg_build_hint))
    }
}

/** The paint bucket: its colour, how wide a gap it closes. */
@Composable
internal fun SvgFillCard(
    colours: List<Long>,
    colour: Long,
    onColour: (Long) -> Unit,
    gap: Float,
    onGap: (Float) -> Unit,
    busy: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModeCard(Icons.Rounded.FormatColorFill, stringResource(R.string.svg_bucket), onDone, modifier, busy = busy) {
        ColorSwatchRow(colors = colours, selected = colour, onSelect = onColour)
        CompactSlider(
            label = stringResource(R.string.svg_bucket_gap),
            valueText = gap.roundToInt().toString(),
            value = gap,
            onValueChange = onGap,
            valueRange = 0f..12f,
            defaultValue = 2f,
        )
        PanelHint(stringResource(R.string.svg_bucket_hint))
    }
}
