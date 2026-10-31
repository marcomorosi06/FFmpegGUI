@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.PictureCanvas
import com.cuscus.ffmpeggui.ui.expressive.AspectPreview
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The canvas tool of the picture editor: the picture gets a canvas of its own
 * (its shape, its size, its colour or none) and becomes a layer on it, moved,
 * pinched, turned and stacked among the texts and stickers like them. The
 * canvas's shape as tiles drawn in that shape, its size as a connected group,
 * its fill as a colour or a checkerboard, and the picture put back in the
 * middle, whole or covering everything.
 */

/** Shapes offered for the canvas (width / height); null: the picture's own. */
private val CANVAS_SHAPES: List<Pair<Float?, String>> = listOf(
    null to "",
    1f to "1:1",
    4f / 5f to "4:5",
    3f / 4f to "3:4",
    9f / 16f to "9:16",
    16f / 9f to "16:9",
    4f / 3f to "4:3",
    3f / 2f to "3:2",
)

/** Longer sides offered for the canvas, in pixels; 0: the picture's own. */
private val CANVAS_SIDES = listOf(0, 1080, 2048, 4096)

/** Fills offered for the canvas, besides none. */
private val CANVAS_COLORS = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFF5EBDCL, 0xFF1B1F5AL, 0xFFFFC857L,
    0xFFFF80ABL, 0xFF69F0AEL, 0xFF40C4FFL, 0xFF9E9E9EL, 0xFFFF5252L,
)

/** A canvas of shape [ratio] (width / height) whose longer side is [side]. */
private fun canvasOf(ratio: Float, side: Int, background: Long?): PictureCanvas {
    val w = if (ratio >= 1f) side else (side * ratio).roundToInt()
    val h = if (ratio >= 1f) (side / ratio).roundToInt() else side
    return PictureCanvas(w, h, background).sane()
}

/**
 * The picture's canvas: [canvas] (null: none, the picture is the frame) for a
 * picture of [pictureWidth] x [pictureHeight] saved as [format].
 */
@Composable
internal fun ImageCanvasPanel(
    canvas: PictureCanvas?,
    pictureWidth: Int,
    pictureHeight: Int,
    format: OutputFormat,
    enabled: Boolean,
    onCanvas: (PictureCanvas?) -> Unit,
    onFit: (fill: Boolean) -> Unit,
    onSelectPicture: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val pictureRatio = pictureWidth.coerceAtLeast(1).toFloat() / pictureHeight.coerceAtLeast(1)
    val pictureSide = maxOf(pictureWidth, pictureHeight).coerceAtLeast(PictureCanvas.MIN_SIDE)
    // Remembered while it is off, so that switching it back on restores it.
    val remembered = remember { arrayOf(canvas ?: canvasOf(pictureRatio, pictureSide, 0xFFFFFFFFL)) }
    if (canvas != null) remembered[0] = canvas
    val last = remembered[0]
    val ratio = last.width.toFloat() / last.height
    val side = maxOf(last.width, last.height)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SwitchCard(
            icon = Icons.Rounded.AspectRatio,
            title = stringResource(R.string.canvas_on),
            subtitle = stringResource(R.string.canvas_on_hint),
            checked = canvas != null,
            onCheckedChange = { on -> onCanvas(if (on) last else null) },
            enabled = enabled,
        )
        AnimatedVisibility(
            visible = canvas != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Its shape, each tile drawn in it.
                val pictureLabel = stringResource(R.string.canvas_shape_picture)
                OptionTileRow(
                    options = CANVAS_SHAPES.map { (r, label) ->
                        TileOption(
                            value = r ?: -1f,
                            label = if (r == null) pictureLabel else label,
                            preview = { color -> AspectPreview(ratio = r ?: pictureRatio, color = color) },
                        )
                    },
                    selected = if (abs(pictureRatio - ratio) < 0.01f) -1f else CANVAS_SHAPES.firstOrNull { (r, _) -> r != null && abs(r - ratio) < 0.01f }?.first,
                    onSelect = { r -> onCanvas(canvasOf(if (r < 0f) pictureRatio else r, side, last.background)) },
                    enabled = enabled,
                )
                // Its size: the longer side.
                val pictureSizeLabel = stringResource(R.string.canvas_size_picture)
                ConnectedChoiceGroup(
                    options = CANVAS_SIDES,
                    selected = CANVAS_SIDES.firstOrNull { it == side } ?: if (side == pictureSide) 0 else -1,
                    label = { s -> if (s == 0) pictureSizeLabel else "$s" },
                    onSelect = { s -> onCanvas(canvasOf(ratio, if (s == 0) pictureSide else s, last.background)) },
                    enabled = enabled,
                )
                Text(
                    text = stringResource(R.string.canvas_size_now, last.width, last.height),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Its fill: a colour, or none (transparent where the format keeps it).
                val transparentLabel = stringResource(R.string.canvas_fill_none)
                val colourLabel = stringResource(R.string.canvas_fill_colour)
                OptionTileRow(
                    options = listOf(
                        TileOption(value = false, label = colourLabel, preview = { _ ->
                            Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color(last.background ?: CANVAS_COLORS.first())))
                        }),
                        TileOption(value = true, label = transparentLabel, preview = { _ ->
                            Checkerboard(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)), cell = 6.dp)
                        }),
                    ),
                    selected = last.background == null,
                    onSelect = { none -> onCanvas(last.copy(background = if (none) null else CANVAS_COLORS.first())) },
                    enabled = enabled,
                )
                val fill = last.background
                if (fill != null) {
                    ColorSwatchRow(
                        colors = CANVAS_COLORS,
                        selected = fill,
                        onSelect = { c -> onCanvas(last.copy(background = c)) },
                    )
                } else if (!format.holdsTransparency) {
                    PanelHint(stringResource(R.string.canvas_fill_none_white, stringResource(format.labelRes)))
                }
                // The picture: put back in the middle, whole or covering the canvas; or picked to move it.
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(false, true).forEach { cover ->
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onFit(cover)
                            },
                            enabled = enabled,
                            shapes = ButtonDefaults.shapes(
                                shape = if (cover) {
                                    RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                                } else {
                                    RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                                },
                                pressedShape = RoundedCornerShape(12.dp),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(if (cover) Icons.Rounded.Fullscreen else Icons.Rounded.FitScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(if (cover) R.string.canvas_picture_fill else R.string.canvas_picture_fit), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onSelectPicture()
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.canvas_picture_select))
                }
                PanelHint(stringResource(R.string.canvas_picture_hint))
            }
        }
    }
}
