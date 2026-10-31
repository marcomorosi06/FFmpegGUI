@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.svgedit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.CompactSlider
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import kotlin.math.roundToInt

/*
 * The card under the stage while a picture is traced into the editor: how it
 * becomes shapes (colours or black and white, how many colours, how closely,
 * how small a speck), the stage showing each trace as it comes, then one big
 * button to start editing the shapes. Its badge spins into a twelve-sided
 * cookie while a trace is being made.
 */

@Composable
internal fun SvgTraceCard(
    tracing: SvgEditorViewModel.Tracing,
    onChange: (TraceSettings) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val t = tracing.settings
    fun set(change: (TraceSettings) -> TraceSettings) = onChange(change(t).sane())
    val spin = rememberInfiniteTransition(label = "traceSpin")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400)), label = "turn")
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.surfaceContainerHigh)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
                .padding(12.dp),
        ) {
            MorphingContainer(
                from = MaterialShapes.SoftBurst,
                to = MaterialShapes.Cookie12Sided,
                morphed = tracing.working,
                color = scheme.primary,
                modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = if (tracing.working) turn else 0f },
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.svg_trace_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onPrimaryContainer)
                AnimatedContent(
                    targetState = tracing.working to tracing.colours,
                    transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "traceCount",
                ) { (working, colours) ->
                    Text(
                        if (working) stringResource(R.string.trace_working) else pluralStringResource(R.plurals.svg_trace_colours, colours, colours),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            AnimatedVisibility(visible = tracing.working, enter = fadeIn(), exit = fadeOut()) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }
        ConnectedChoiceGroup(
            options = listOf(false, true),
            selected = t.mono,
            label = { stringResource(if (it) R.string.trace_mono else R.string.trace_colours) },
            onSelect = { mono -> set { it.copy(mono = mono) } },
        )
        AnimatedVisibility(
            visible = !t.mono,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            CompactSlider(
                label = stringResource(R.string.trace_colour_count),
                valueText = t.colors.toString(),
                value = t.colors.toFloat(),
                onValueChange = { v -> set { it.copy(colors = v.roundToInt()) } },
                valueRange = 2f..32f,
                defaultValue = TraceSettings().colors.toFloat(),
            )
        }
        CompactSlider(
            label = stringResource(R.string.trace_detail),
            valueText = stringResource(R.string.value_percent, (t.detail * 100).roundToInt()),
            value = t.detail,
            onValueChange = { v -> set { it.copy(detail = v) } },
            valueRange = 0f..1f,
            defaultValue = TraceSettings().detail,
        )
        CompactSlider(
            label = stringResource(R.string.trace_speckle),
            valueText = stringResource(R.string.trace_speckle_value, t.speckle),
            value = t.speckle.toFloat(),
            onValueChange = { v -> set { it.copy(speckle = v.roundToInt()) } },
            valueRange = 0f..100f,
            defaultValue = TraceSettings().speckle.toFloat(),
        )
        SwitchCard(
            icon = Icons.Rounded.Gesture,
            title = stringResource(R.string.trace_smooth),
            subtitle = stringResource(R.string.trace_smooth_hint),
            checked = t.smooth,
            onCheckedChange = { on -> set { it.copy(smooth = on) } },
        )
        PanelHint(stringResource(R.string.svg_trace_hint))
        Button(
            onClick = onDone,
            enabled = !tracing.working,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.svg_trace_done), style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}
