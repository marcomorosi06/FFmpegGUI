@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.vector.PathOp
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import com.cuscus.ffmpeggui.core.vector.VectorImage
import com.cuscus.ffmpeggui.core.vector.VectorTracer
import com.cuscus.ffmpeggui.preview.toRaster
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The shapes tool of the picture editor, for a picture saved as an SVG: how it
 * is traced (colours or black and white, how many colours, how closely the
 * outlines follow the pixels, how small a speck is dropped, curves or straight
 * lines), with the traced picture shown over the stage while the tool is open,
 * made off the main thread from the picture as it looks (texts, stickers and
 * the drawing are traced with it at export).
 */

/** The two ways of tracing. */
private enum class TraceMode(val labelRes: Int) { COLOURS(R.string.trace_colours), MONO(R.string.trace_mono) }

/**
 * [picture] traced as [settings] say, while [active]: a moment after the last
 * change (a slider being dragged traces only where it stops). The value is
 * the last traced picture, and whether a newer one is being made.
 */
@Composable
internal fun rememberTraced(picture: Bitmap?, settings: TraceSettings, active: Boolean): State<Pair<VectorImage?, Boolean>> =
    produceState<Pair<VectorImage?, Boolean>>(initialValue = null to false, picture, settings, active) {
        if (!active || picture == null) {
            value = value.first to false
            return@produceState
        }
        value = value.first to true
        delay(TRACE_SETTLE_MS)
        val traced = withContext(Dispatchers.Default) { VectorTracer.trace(picture.toRaster(), settings) }
        value = traced to false
    }

/** The traced picture, filling the space it is given (its colours, even-odd, as the SVG draws them). */
@Composable
internal fun VectorPreview(image: VectorImage, modifier: Modifier = Modifier) {
    val paths = remember(image) {
        image.layers.map { layer ->
            val path = Path().apply { fillType = PathFillType.EvenOdd }
            for (op in layer.ops) {
                when (op) {
                    is PathOp.MoveTo -> path.moveTo(op.x, op.y)
                    is PathOp.LineTo -> path.lineTo(op.x, op.y)
                    is PathOp.QuadTo -> path.quadraticTo(op.cx, op.cy, op.x, op.y)
                    PathOp.Close -> path.close()
                }
            }
            path to Color(layer.argb)
        }
    }
    Canvas(modifier = modifier) {
        val sx = size.width / image.width.coerceAtLeast(1)
        val sy = size.height / image.height.coerceAtLeast(1)
        withTransform({ scale(sx, sy, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
            for ((path, color) in paths) {
                drawPath(path, color)
                if (image.stroked) drawPath(path, color, style = Stroke(width = 0.5f, join = StrokeJoin.Round))
            }
        }
    }
}

/** How the picture is traced; [traced] the last result (for its figures), [working] while a newer one is made. */
@Composable
internal fun ImageTracePanel(trace: TraceSettings, traced: VectorImage?, working: Boolean, onChange: ImageChange) {
    fun set(key: String?, change: (TraceSettings) -> TraceSettings) = onChange(key?.let { "trace:$it" }) { it.copy(trace = change(it.trace).sane()) }
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // What is made, live.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (working) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = traced?.let { pluralStringResource(R.plurals.trace_shapes, it.outlines, it.outlines, it.layers.size) }
                        ?: stringResource(R.string.trace_working),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        ConnectedChoiceGroup(
            options = TraceMode.entries,
            selected = if (trace.mono) TraceMode.MONO else TraceMode.COLOURS,
            label = { stringResource(it.labelRes) },
            onSelect = { mode -> set(null) { it.copy(mono = mode == TraceMode.MONO) } },
        )
        AnimatedVisibility(
            visible = !trace.mono,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            CompactSlider(
                label = stringResource(R.string.trace_colour_count),
                valueText = trace.colors.toString(),
                value = trace.colors.toFloat(),
                onValueChange = { v -> set("colors") { it.copy(colors = v.roundToInt()) } },
                valueRange = 2f..32f,
                defaultValue = TraceSettings().colors.toFloat(),
            )
        }
        CompactSlider(
            label = stringResource(R.string.trace_detail),
            valueText = stringResource(R.string.value_percent, (trace.detail * 100).roundToInt()),
            value = trace.detail,
            onValueChange = { v -> set("detail") { it.copy(detail = v) } },
            valueRange = 0f..1f,
            defaultValue = TraceSettings().detail,
        )
        CompactSlider(
            label = stringResource(R.string.trace_speckle),
            valueText = stringResource(R.string.trace_speckle_value, trace.speckle),
            value = trace.speckle.toFloat(),
            onValueChange = { v -> set("speckle") { it.copy(speckle = v.roundToInt()) } },
            valueRange = 0f..100f,
            defaultValue = TraceSettings().speckle.toFloat(),
        )
        SwitchCard(
            icon = Icons.Rounded.Gesture,
            title = stringResource(R.string.trace_smooth),
            subtitle = stringResource(R.string.trace_smooth_hint),
            checked = trace.smooth,
            onCheckedChange = { on -> set(null) { it.copy(smooth = on) } },
        )
        PanelHint(stringResource(R.string.trace_hint))
    }
}

/** How long the settings rest before the picture is traced again. */
private const val TRACE_SETTLE_MS = 250L
