@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.marking

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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.settings.SettingsGroupTitle
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlinx.coroutines.delay

/**
 * Said when a dub or eraser model is to be imported before the AI marks
 * (MarkingGate): why the marks come first for those, with the law that asks
 * for them, and what they never say. Its badge, a lock, breathes between a
 * cookie and a burst.
 */
@Composable
fun MarkersFirstDialog(onSetUp: () -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    MarksDialog(
        icon = Icons.Rounded.Lock,
        title = stringResource(R.string.marks_first_title),
        text = stringResource(R.string.marks_first_text),
        facts = listOf(
            Icons.Rounded.Gavel to stringResource(R.string.marks_first_law),
            Icons.Rounded.VisibilityOff to stringResource(R.string.marks_first_privacy),
        ),
        primary = stringResource(R.string.marks_set_up),
        secondary = stringResource(R.string.marks_first_later),
        footer = null,
        onPrimary = onSetUp,
        onDismiss = onDismiss,
    )
}

/**
 * Said once after the dub's or the eraser's models were deleted for want of
 * the AI marks (MarkingSweep): which kinds went, the space they gave back,
 * why, and the way to the marks, then to the models again.
 */
@Composable
fun ModelsClearedDialog(removed: List<MarkingSweep.Removed>, onSetUp: () -> Unit, onDismiss: () -> Unit) {
    if (removed.isEmpty()) return
    val context = LocalContext.current
    val freed = remember { Animatable(0f) }
    LaunchedEffect(Unit) { freed.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing)) }
    val total = removed.sumOf { it.bytes }
    MarksDialog(
        icon = Icons.Rounded.DeleteSweep,
        title = stringResource(R.string.marks_cleared_title),
        text = stringResource(R.string.marks_cleared_text, removed.joinToString(", ") { context.getString(it.label) }),
        facts = listOf(
            Icons.Rounded.Gavel to stringResource(R.string.marks_first_law),
            Icons.Rounded.VisibilityOff to stringResource(R.string.marks_first_privacy),
        ),
        primary = stringResource(R.string.marks_set_up),
        secondary = stringResource(R.string.marks_cleared_ok),
        footer = stringResource(R.string.marks_cleared_freed, Formatter.formatShortFileSize(context, (total * freed.value).toLong())),
        onPrimary = onSetUp,
        onDismiss = onDismiss,
    )
}

@Composable
private fun MarksDialog(
    icon: ImageVector,
    title: String,
    text: String,
    facts: List<Pair<ImageVector, String>>,
    primary: String,
    secondary: String,
    footer: String?,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        shown = true
        delay(240)
        for (i in facts.indices) {
            rows = i + 1
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            delay(150)
        }
    }
    val scale by animateFloatAsState(if (shown) 1f else 0.82f, motion.defaultSpatialSpec(), label = "marksScale")
    val fade by animateFloatAsState(if (shown) 1f else 0f, motion.defaultEffectsSpec(), label = "marksFade")
    val breath = rememberInfiniteTransition(label = "marksBreath")
    val morph by breath.animateFloat(0f, 1f, infiniteRepeatable(tween(durationMillis = 1_800), RepeatMode.Reverse), label = "marksMorph")
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
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.SoftBurst,
                morphed = morph > 0.5f,
                color = scheme.primaryContainer,
                rotationDegrees = morph * 30f,
                modifier = Modifier.size(88.dp),
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = -morph * 30f })
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(title, style = SettingsGroupTitle.copy(fontSize = 26.sp, lineHeight = 32.sp), color = scheme.onSurface, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                facts.forEachIndexed { index, (factIcon, fact) ->
                    val visible = index < rows
                    val slide by animateFloatAsState(if (visible) 0f else -40f, motion.defaultSpatialSpec(), label = "marksFactSlide")
                    val alpha by animateFloatAsState(if (visible) 1f else 0f, motion.defaultEffectsSpec(), label = "marksFactFade")
                    val big = 24.dp
                    val small = 6.dp
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) big else small,
                        topEnd = if (index == 0) big else small,
                        bottomStart = if (index == facts.lastIndex) big else small,
                        bottomEnd = if (index == facts.lastIndex) big else small,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = slide
                                this.alpha = alpha
                            }
                            .fillMaxWidth()
                            .clip(shape)
                            .background(scheme.surfaceContainerHighest)
                            .padding(14.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).clip(CircleShape).background(scheme.secondaryContainer)) {
                            Icon(factIcon, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(fact, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                    }
                }
            }
            if (footer != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    footer,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.clip(CircleShape).background(scheme.primaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onPrimary()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(primary, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
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
                    Text(secondary, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
                }
            }
        }
    }
}
