@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.debug

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlin.math.roundToInt

/*
 * The debug dashboard: only in a build that can be debugged (DebugBench.enabled),
 * floating over every screen. Folded it is a bubble (with the count of speaker
 * underruns when there are some), opened a small card: the preview's sound path
 * for issue #35 and what the main player went through. Both are dragged
 * anywhere on the screen; the bubble opens with a tap, the card folds with its
 * button. Its words are not translated: it is a tool for whoever builds the app.
 */

/** Over the whole screen; only the bubble or the card take touches. */
@Composable
fun DebugDashboard(modifier: Modifier = Modifier) {
    if (!DebugBench.enabled) return
    var open by rememberSaveable { mutableStateOf(false) }
    var x by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var y by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxX = with(density) { maxWidth.toPx() }
        val maxY = with(density) { maxHeight.toPx() }
        // What is shown now (the bubble or the card), measured: it stays whole on the screen.
        var shownWidth by remember { mutableIntStateOf(0) }
        var shownHeight by remember { mutableIntStateOf(0) }
        // At first: on the right, a third of the way down.
        if (x.isNaN()) x = maxX - with(density) { (BUBBLE + 12.dp).toPx() }
        if (y.isNaN()) y = maxY / 3f
        val shownX = x.coerceIn(0f, (maxX - shownWidth).coerceAtLeast(0f))
        val shownY = y.coerceIn(0f, (maxY - shownHeight).coerceAtLeast(0f))
        val drag = Modifier.pointerInput(maxX, maxY) {
            detectDragGestures { change, amount ->
                change.consume()
                x = (x.coerceIn(0f, maxX) + amount.x).coerceIn(0f, (maxX - shownWidth).coerceAtLeast(0f))
                y = (y.coerceIn(0f, maxY) + amount.y).coerceIn(0f, (maxY - shownHeight).coerceAtLeast(0f))
            }
        }
        AnimatedContent(
            targetState = open,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
            },
            contentAlignment = Alignment.TopStart,
            label = "debugDashboard",
            modifier = Modifier
                .offset { IntOffset(shownX.roundToInt(), shownY.roundToInt()) }
                .onSizeChanged {
                    shownWidth = it.width
                    shownHeight = it.height
                },
        ) { opened ->
            if (opened) {
                DashboardCard(drag = drag, onFold = { open = false })
            } else {
                DashboardBubble(drag = drag, onOpen = { open = true })
            }
        }
    }
}

@Composable
private fun DashboardBubble(drag: Modifier, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val underruns = DebugBench.underruns.intValue
    Box(modifier = Modifier.size(BUBBLE)) {
        MorphingContainer(
            from = MaterialShapes.Cookie9Sided,
            to = MaterialShapes.Cookie12Sided,
            morphed = underruns > 0,
            color = if (underruns > 0) scheme.errorContainer else scheme.tertiaryContainer,
            modifier = Modifier
                .size(BUBBLE)
                .shadow(6.dp, CircleShape)
                .then(drag)
                .clickable(onClick = onOpen),
        ) {
            Icon(
                Icons.Rounded.BugReport,
                contentDescription = "Debug dashboard",
                tint = if (underruns > 0) scheme.onErrorContainer else scheme.onTertiaryContainer,
                modifier = Modifier.size(26.dp),
            )
        }
        if (underruns > 0) {
            Text(
                text = if (underruns > 99) "99+" else underruns.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(scheme.error)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DashboardCard(drag: Modifier, onFold: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val trial = DebugBench.soundTrial
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(bottom = 14.dp),
    ) {
        // The header is the handle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(drag)
                .background(scheme.tertiaryContainer)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(Icons.Rounded.BugReport, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Debug", style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onTertiaryContainer)
                Text("Drag to move", style = MaterialTheme.typography.labelSmall, color = scheme.onTertiaryContainer.copy(alpha = 0.7f))
            }
            FilledTonalIconButton(onClick = onFold) {
                Icon(Icons.Rounded.Remove, contentDescription = "Fold")
            }
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("Preview sound path (#35)", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            ConnectedChoiceGroup(
                options = SoundTrial.entries,
                selected = trial,
                label = { it.label },
                onSelect = DebugBench::choose,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(trial.summary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(
                "Changing it makes the editor again.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text("Main player", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Counter("Underruns", DebugBench.underruns.intValue.toString(), alarm = DebugBench.underruns.intValue > 0, modifier = Modifier.weight(1f))
                Counter("Dropped", DebugBench.droppedFrames.intValue.toString(), alarm = false, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Counter("Seeks", DebugBench.seeks.intValue.toString(), alarm = false, modifier = Modifier.weight(1f))
                Counter("Rate", "%.2fx".format(java.util.Locale.ROOT, DebugBench.rate.floatValue), alarm = false, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ahead = DebugBench.aheadMs.longValue
                Counter("Read ahead", "%.1f s".format(java.util.Locale.ROOT, ahead / 1000f), alarm = ahead < LOW_AHEAD_MS, modifier = Modifier.weight(1f))
                Counter("Decoder", DebugBench.audioDecoder.removePrefix("c2.").removePrefix("OMX."), alarm = false, modifier = Modifier.weight(1f), small = true)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Test the file alone (pause the preview)", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for ((label, ours) in listOf("Bare 4x" to false, "Preview's 4x" to true)) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { DebugBench.runTest(context, 4f, ours) },
                        enabled = !DebugBench.testing,
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { DebugBench.runTest(context, 4f, ours = false, platformSpeed = true) },
                    enabled = !DebugBench.testing,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Android 4x", maxLines = 1)
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = { DebugBench.runDecodeTest(context) },
                    enabled = !DebugBench.testing,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Decode speed", maxLines = 1)
                }
            }
            if (DebugBench.testing) {
                TextButton(onClick = DebugBench::stopTest) { Text("Stop the test") }
            }
            DebugBench.testReport?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = DebugBench::resetCounts, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset counts")
            }
        }
    }
}

/** One number the main player went through. */
@Composable
private fun Counter(label: String, value: String, alarm: Boolean, modifier: Modifier, small: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (alarm) scheme.errorContainer else scheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (alarm) scheme.onErrorContainer else scheme.onSurfaceVariant)
        Text(
            value,
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleLargeEmphasized,
            color = if (alarm) scheme.onErrorContainer else scheme.onSurface,
            maxLines = 2,
        )
    }
}

private val SoundTrial.label: String
    get() = when (this) {
        SoundTrial.NONE -> "Usual"
        SoundTrial.SONIC -> "Sonic"
    }

private val SoundTrial.summary: String
    get() = when (this) {
        SoundTrial.NONE -> "The path release builds take: Android changes the speed."
        SoundTrial.SONIC -> "As before the fix: Media3's Sonic changes the speed, on the player's thread."
    }

/** Less read ahead than this while playing: the file is read too slowly. */
private const val LOW_AHEAD_MS = 1_000L

private val BUBBLE = 56.dp
private val CARD_WIDTH = 320.dp
