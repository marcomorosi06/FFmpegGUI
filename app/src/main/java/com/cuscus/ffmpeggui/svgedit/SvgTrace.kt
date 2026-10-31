@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.svgedit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.TraceBadge
import com.cuscus.ffmpeggui.TraceControls
import com.cuscus.ffmpeggui.TraceResult
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import com.cuscus.ffmpeggui.ui.expressive.PanelHint

/*
 * The card under the stage while a picture is traced into the editor: its
 * badge (the kind of picture's shape, spinning into a cookie while a trace is
 * made) and how many colours came out, then the trace's settings (see
 * TraceControls), the stage showing each trace as it comes, then one big
 * button to start editing the shapes.
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
            TraceBadge(
                settings = t,
                working = tracing.working,
                modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = if (tracing.working) turn else 0f },
            )
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
            AnimatedVisibility(visible = tracing.working, enter = fadeIn(motion.fastEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }
        TraceControls(
            settings = t,
            onChange = { _, change -> onChange(change(t).sane()) },
            working = tracing.working,
            result = TraceResult(tracing.palette, tracing.outlines).takeIf { tracing.palette.isNotEmpty() },
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
