@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.vector.PathOp
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import com.cuscus.ffmpeggui.core.vector.VectorImage
import com.cuscus.ffmpeggui.core.vector.VectorTracer
import com.cuscus.ffmpeggui.preview.toRaster
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The shapes tool of the picture editor, for a picture saved as an SVG: how it
 * is traced (colours or black and white, how many colours, how closely the
 * outlines follow the pixels, how small a speck is dropped, curves or straight
 * lines), with the traced picture shown over the stage while the tool is open,
 * made off the main thread from the picture as it looks (texts, stickers and
 * the drawing are traced with it at export).
 */

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

/** How the picture is traced (see TraceControls); [traced] the last result, [working] while a newer one is made. */
@Composable
internal fun ImageTracePanel(trace: TraceSettings, traced: VectorImage?, working: Boolean, onChange: ImageChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        TraceControls(
            settings = trace,
            onChange = { key, change -> onChange(key?.let { "trace:$it" }) { it.copy(trace = change(it.trace).sane()) } },
            working = working,
            result = traced?.let { TraceResult(it.palette, it.outlines) },
        )
        PanelHint(stringResource(R.string.trace_hint))
    }
}

/** How long the settings rest before the picture is traced again. */
private const val TRACE_SETTLE_MS = 250L
