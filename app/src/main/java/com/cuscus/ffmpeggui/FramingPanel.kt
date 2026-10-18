@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The framing of a clip: how much it is enlarged and which part of it shows.
 * With keyframes (the diamond by undo) the values at the playhead are shown and
 * changed, and the export and the preview move from one keyframe to the next:
 * a zoom in, a pan across, a push towards a face.
 */

/** Largest enlargement offered. */
private const val MAX_ZOOM = 4f

@Composable
internal fun FramingSectionContent(
    zoom: Float,
    panX: Float,
    panY: Float,
    onZoom: (Float) -> Unit,
    onPan: (Float, Float) -> Unit,
    /** The clip has keyframes: the values are those at the playhead. */
    keyed: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            Text(
                text = stringResource(R.string.framing_title),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ValueSlider(
                label = stringResource(R.string.framing_zoom),
                valueText = String.format(Locale.US, "%.2f\u00D7", zoom),
                value = zoom.coerceIn(1f, MAX_ZOOM),
                onValueChange = onZoom,
                valueRange = 1f..MAX_ZOOM,
                icon = Icons.Rounded.ZoomIn,
                defaultValue = 1f,
            )
            ValueSlider(
                label = stringResource(R.string.framing_across),
                valueText = "${(panX * 100).roundToInt()}%",
                value = panX.coerceIn(0f, 1f),
                onValueChange = { onPan(it, panY) },
                valueRange = 0f..1f,
                icon = Icons.Rounded.SwapHoriz,
                defaultValue = 0.5f,
                enabled = zoom > 1.001f,
            )
            ValueSlider(
                label = stringResource(R.string.framing_down),
                valueText = "${(panY * 100).roundToInt()}%",
                value = panY.coerceIn(0f, 1f),
                onValueChange = { onPan(panX, it) },
                valueRange = 0f..1f,
                icon = Icons.Rounded.SwapVert,
                defaultValue = 0.5f,
                enabled = zoom > 1.001f,
            )
            PanelHint(stringResource(if (keyed) R.string.framing_keyed_hint else R.string.framing_hint))
        }
    }
}
