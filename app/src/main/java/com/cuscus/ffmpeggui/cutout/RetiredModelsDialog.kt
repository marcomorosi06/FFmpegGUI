@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.cutout

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.settings.SettingsGroupTitle
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import kotlinx.coroutines.delay

/** The parts the stem models split a sound into, as the dialog shows them: dialogue, music, effects, instruments. */
private val NEW_PARTS = listOf(
    Icons.Rounded.RecordVoiceOver to MaterialShapes.Cookie9Sided,
    Icons.Rounded.MusicNote to MaterialShapes.Flower,
    Icons.Rounded.Bolt to MaterialShapes.SoftBurst,
    Icons.Rounded.Piano to MaterialShapes.Clover8Leaf,
    Icons.Rounded.GraphicEq to MaterialShapes.Sunny,
)

/**
 * Said once after old models were deleted from the phone (see RetiredModels):
 * which ones, what took their place and how much space came back, with a way
 * straight to setting up the new ones.
 *
 * The badge sweeps in (a nine-sided cookie that blooms into a sun while a
 * broom swings), each old model then slides in struck through with its
 * replacement beside it, and the space freed counts up to its total.
 */
@Composable
fun RetiredModelsDialog(
    removed: List<RetiredModels.Removed>,
    onSetUp: (RetiredModels.Area) -> Unit,
    onDismiss: () -> Unit,
) {
    if (removed.isEmpty()) return
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    // How many rows are in: one after the other, after the badge.
    var rows by remember { mutableStateOf(0) }
    val freed = remember { Animatable(0f) }
    val total = removed.sumOf { it.bytes }
    LaunchedEffect(Unit) {
        shown = true
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        delay(260)
        for (i in removed.indices) {
            rows = i + 1
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            delay(140)
        }
        freed.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val scale by animateFloatAsState(if (shown) 1f else 0.82f, motion.defaultSpatialSpec(), label = "retiredScale")
    val fade by animateFloatAsState(if (shown) 1f else 0f, motion.defaultEffectsSpec(), label = "retiredFade")
    val turn by animateFloatAsState(if (shown) 0f else -60f, motion.slowSpatialSpec(), label = "retiredTurn")
    // The broom sweeps to and fro, gently, for as long as the dialog is open.
    val sweep = rememberInfiniteTransition(label = "retiredSweep")
    val swing by sweep.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "retiredSwing",
    )
    val replacements = removed.map { it.retired.replacement }.distinct()
    val spark = remember { PolygonShape(MaterialShapes.SoftBurst) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(scheme.surfaceContainerHigh)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                MorphingContainer(
                    from = MaterialShapes.Cookie9Sided,
                    to = MaterialShapes.Sunny,
                    morphed = shown,
                    color = scheme.tertiaryContainer,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = turn }
                        .size(88.dp),
                ) {
                    Icon(
                        Icons.Rounded.CleaningServices,
                        contentDescription = null,
                        tint = scheme.onTertiaryContainer,
                        modifier = Modifier
                            .graphicsLayer { rotationZ = -turn + swing }
                            .size(40.dp),
                    )
                }
                // A spark at the edge, where the dust went.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            val s = freed.value
                            scaleX = 0.4f + 0.6f * s
                            scaleY = 0.4f + 0.6f * s
                            alpha = s
                            rotationZ = 90f * s
                        }
                        .size(30.dp)
                        .clip(spark)
                        .background(scheme.primary),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.retired_title),
                style = SettingsGroupTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(R.string.retired_text),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                removed.forEachIndexed { index, item ->
                    RetiredRow(
                        item = item,
                        visible = index < rows,
                        first = index == 0,
                        last = index == removed.lastIndex,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // The space given back, counting up.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.retired_freed, Formatter.formatShortFileSize(context, (total * freed.value).toLong())),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = scheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.retired_next, replacements.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSetUp(removed.first().retired.area)
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        stringResource(R.string.retired_set_up, replacements.joinToString(", ")),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.retired_ok), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
                }
            }
        }
    }
}

/**
 * One old model: its name struck through, an arrow, the new one's name; it
 * slides in from the left and settles with the theme's spring. The group's
 * outer corners are large, the inner ones small, as connected rows are.
 */
@Composable
private fun RetiredRow(item: RetiredModels.Removed, visible: Boolean, first: Boolean, last: Boolean) {
    val context = LocalContext.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val enter by animateFloatAsState(if (visible) 1f else 0f, motion.defaultSpatialSpec(), label = "retiredRow")
    val big = 24.dp
    val small = 8.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer {
                translationX = (1f - enter) * -48.dp.toPx()
                alpha = enter.coerceIn(0f, 1f)
            }
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = if (first) big else small,
                    topEnd = if (first) big else small,
                    bottomStart = if (last) big else small,
                    bottomEnd = if (last) big else small,
                ),
            )
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Old name, then arrow and new name as one piece: when both do not fit on a line, the
            // new one goes whole to the next line instead of being squeezed a few letters at a time.
            FlowRow(verticalArrangement = Arrangement.Center) {
                Text(
                    item.retired.name,
                    style = MaterialTheme.typography.titleSmall.copy(textDecoration = TextDecoration.LineThrough),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
                    )
                    Text(
                        item.retired.replacement,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Text(
                stringResource(
                    when (item.retired.reason) {
                        RetiredModels.Reason.SUPERSEDED -> R.string.retired_reason_superseded
                        RetiredModels.Reason.EXTENDED -> R.string.retired_reason_extended
                        RetiredModels.Reason.QUICKER -> R.string.retired_reason_quicker
                        RetiredModels.Reason.WITHDRAWN -> R.string.retired_reason_withdrawn
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (item.retired.reason == RetiredModels.Reason.EXTENDED) {
                // What the new models give where the old one gave a voice: each part, popping in.
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NEW_PARTS.forEachIndexed { i, (icon, shape) ->
                        val part by animateFloatAsState(
                            if (visible) 1f else 0f,
                            motion.defaultSpatialSpec(),
                            label = "retiredPart$i",
                        )
                        var popped by remember { mutableStateOf(false) }
                        LaunchedEffect(visible) {
                            if (visible) {
                                delay(120L + 90L * i)
                                popped = true
                            }
                        }
                        val pop by animateFloatAsState(if (popped) 1f else 0f, motion.fastSpatialSpec(), label = "retiredPop$i")
                        MorphingContainer(
                            from = shape,
                            to = MaterialShapes.Circle,
                            morphed = !popped,
                            color = if (i == 0) scheme.primary else scheme.tertiaryContainer,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pop * part
                                    scaleY = pop * part
                                    alpha = pop
                                }
                                .size(28.dp),
                        ) {
                            Icon(icon, contentDescription = null, tint = if (i == 0) scheme.onPrimary else scheme.onTertiaryContainer, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            Formatter.formatShortFileSize(context, item.bytes),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            maxLines = 1,
            softWrap = false,
        )
    }
}
