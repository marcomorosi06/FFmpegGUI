@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import com.cuscus.ffmpeggui.ui.adaptive.scaledToFit
import com.cuscus.ffmpeggui.ui.adaptive.ScrollPane
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import com.cuscus.ffmpeggui.core.engine.VideoEncoderUse
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.graphics.shapes.Morph
import com.cuscus.ffmpeggui.core.engine.ConversionStage
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.MorphShape
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

/*
 * The export from start to finish, on one screen:
 *
 *  - running: a shape morphing and turning, filling up with a wave as the
 *    export goes, the percentage rolling in its middle (a loading indicator
 *    until FFmpeg says how far it is), a halo, small shapes circling it, a ring
 *    and a tick at each quarter; behind the screen a tide rises with it; the
 *    stage, the time so far and the time left; the log, which opens;
 *  - done: the badge bursts into shapes once; the file with its format, size
 *    and how long it took; share or save it, open it, edit again or open
 *    another file;
 *  - failed: what went wrong, the log open, back to the editor (the edit is
 *    kept) or start over.
 */

@Composable
fun ProcessingScreen(
    state: ConversionState,
    onCancel: () -> Unit,
    onNewConversion: () -> Unit,
    /** Back to the editor, the edit as it was. */
    onBackToEditor: () -> Unit = onNewConversion,
    fileName: String = "",
    format: OutputFormat? = null,
) {
    // A tide rising behind the whole screen as the export goes (and ebbing once it is done).
    val running = state as? ConversionState.Processing
    val tide by animateFloatAsState(
        targetValue = if (running != null && running.stage == ConversionStage.ENCODING) running.progress.coerceIn(0f, 1f) else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "tide",
    )
    val tideColor = MaterialTheme.colorScheme.primaryContainer
    val window = LocalWindowLayout.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                if (tide > 0f) {
                    val fade = TIDE_EDGE.toPx()
                    val top = size.height * (1f - tide) - fade
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to tideColor.copy(alpha = 0f),
                            1f to tideColor.copy(alpha = 0.5f),
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top),
                    )
                }
            },
    ) {
        val content: @Composable ColumnScope.(Part) -> Unit = { part ->
            when (state) {
                is ConversionState.Processing -> RunningContent(state, fileName, format, onCancel, part)
                is ConversionState.Success -> DoneContent(state, format, onBackToEditor, onNewConversion, part)
                is ConversionState.Error -> FailedContent(state, onBackToEditor, onNewConversion, part)
                ConversionState.Idle -> Unit
            }
        }
        if (window.twoPanes) {
            // Side by side: the picture of where the export is on one side (made smaller
            // when the screen is short), what can be done on the other.
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = window.sidePadding(PROCESSING_MAX_WIDTH, 24.dp), vertical = 12.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    content(Part.PICTURE)
                }
                ScrollPane(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    content(Part.DETAILS)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = window.sidePadding(PROCESSING_COLUMN_WIDTH, 20.dp), vertical = 16.dp),
            ) {
                content(Part.WHOLE)
            }
        }
    }
}

/** Which part of a state's content to show: all of it in one column, or one side of two. */
private enum class Part { WHOLE, PICTURE, DETAILS }

/** The widest the two sides get together (a monitor), and the single column. */
private val PROCESSING_MAX_WIDTH = 1100.dp
private val PROCESSING_COLUMN_WIDTH = 600.dp

/** [content] alone on its side when [part] is the picture (shrunk to the height there), as it is otherwise. */
@Composable
private fun ColumnScope.Fitted(part: Part, content: @Composable () -> Unit) {
    if (part == Part.PICTURE) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f, fill = false).scaledToFit()) { content() }
    } else {
        content()
    }
}

// -- Running ------------------------------------------------------------------------------

@Composable
private fun ColumnScope.RunningContent(state: ConversionState.Processing, fileName: String, format: OutputFormat?, onCancel: () -> Unit, part: Part) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    // A clock for the times shown, ticking twice a second.
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(500)
            value = SystemClock.elapsedRealtime()
        }
    }
    val elapsedMs = if (state.startedAtMs > 0L) now - state.startedAtMs else 0L
    val leftMs = timeLeftMs(state, now)
    val known = state.progress > 0f && state.stage == ConversionStage.ENCODING

    if (part != Part.PICTURE) {
        FileRow(fileName = fileName, format = format)
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (part != Part.DETAILS) {
        Fitted(part) { ExportHero(progress = state.progress, known = known) }
        Spacer(modifier = Modifier.height(12.dp))
        // The stage, sliding in from below when it changes.
        AnimatedContent(
            targetState = state.stage,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
            },
            label = "stage",
        ) { stage ->
            Text(
                text = stringResource(stageLabelRes(stage)),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.processing_keep_open),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
    if (part == Part.PICTURE) return
    if (part == Part.WHOLE) Spacer(modifier = Modifier.height(20.dp))
    // The times, as two tiles joined in the middle.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        TimeTile(
            icon = Icons.Rounded.Schedule,
            label = stringResource(R.string.processing_tile_elapsed),
            value = clockText(elapsedMs),
            emphasized = false,
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            modifier = Modifier.weight(1f),
        )
        TimeTile(
            icon = Icons.Rounded.Timer,
            label = stringResource(R.string.processing_tile_left),
            value = leftMs?.let(::clockText),
            emphasized = true,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier.weight(1f),
        )
    }
    // Who makes the picture: the phone's video chip, or the processor (after the chip failed).
    AnimatedVisibility(visible = state.hardware != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            StatPill(
                icon = if (state.hardware == true) Icons.Rounded.Memory else Icons.Rounded.DeveloperBoard,
                text = stringResource(if (state.hardware == true) R.string.processing_encoder_hardware else R.string.processing_encoder_fallback),
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    LogCard(logs = state.logs, startOpen = false)
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onCancel()
        },
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.errorContainer,
            contentColor = scheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.processing_cancel), style = MaterialTheme.typography.titleSmallEmphasized)
    }
}

/**
 * The heart of the running export: a shape that keeps morphing from one
 * MaterialShape into the next while it turns, filling up from below with a
 * moving wave as far as the export has gone. The percentage rolls digit by
 * digit in its middle and changes colour where the wave covers it. A soft halo
 * breathes behind it, three small shapes circle it, and each quarter done sends
 * a ring out of it with a tick. Until FFmpeg says how far it is, a loading
 * indicator sits in the middle.
 */
@Composable
private fun ExportHero(progress: Float, known: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val shown by animateFloatAsState(
        targetValue = if (known) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "progress",
    )
    val motion = rememberInfiniteTransition(label = "hero")
    val turn by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(HERO_TURN_MS, easing = LinearEasing)),
        label = "turn",
    )
    val phase by motion.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(WAVE_MS, easing = LinearEasing)),
        label = "wave",
    )
    val orbit by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(ORBIT_MS, easing = LinearEasing)),
        label = "orbit",
    )
    val breath by motion.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(animation = tween(BREATH_MS, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "breath",
    )

    // One shape into the next, on and on, with the theme's spring.
    val morphs = remember {
        val shapes = listOf(
            MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny,
            MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst, MaterialShapes.Flower,
        )
        shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) }
    }
    var index by remember { mutableIntStateOf(0) }
    val morphing = remember { Animatable(0f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        while (true) {
            delay(MORPH_EVERY_MS)
            morphing.animateTo(1f, spring)
            index = (index + 1) % morphs.size
            morphing.snapTo(0f)
        }
    }
    // Everything that moves at every frame is read while drawing: the hero recomposes
    // only when the percentage or the shape it morphs from changes, so that it takes
    // nothing from the export it shows.
    val shapeOf: () -> Shape = { MorphShape(morphs[index], morphing.value, rotationDegrees = turn) }
    val halo = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
    val pieces = remember { listOf(MaterialShapes.Gem, MaterialShapes.Clover4Leaf, MaterialShapes.Pill).map { PolygonShape(it) } }
    val pieceColors = listOf(scheme.tertiary, scheme.primary, scheme.secondary)

    // A ring and a tick at each quarter done.
    val quarter = if (known) (progress * 4f).toInt().coerceIn(0, 4) else 0
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(quarter) {
        if (quarter in 1..3) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(PULSE_MS, easing = FastOutSlowInEasing))
        }
    }

    val waveHeight = with(density) { WAVE_HEIGHT.toPx() }
    val amplitudeOf: () -> Float = { waveHeight * (sin(PI * shown).toFloat()).coerceAtLeast(0.3f) }
    val pulsing by remember { derivedStateOf { pulse.value < 1f } }
    val percent = (progress * 100f).toInt().coerceIn(0, 100)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(HERO_SIZE)
            .semantics { if (known) progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
    ) {
        // The halo, turning the other way and breathing.
        Box(
            modifier = Modifier
                .size(HALO_SIZE)
                .graphicsLayer {
                    rotationZ = -turn * 0.6f
                    scaleX = breath
                    scaleY = breath
                }
                .clip(halo)
                .background(scheme.secondaryContainer.copy(alpha = 0.6f)),
        )
        // The ring of the last quarter, flying out.
        if (pulsing) {
            val ring = scheme.primary
            Box(
                modifier = Modifier
                    .size(SHAPE_SIZE)
                    .graphicsLayer {
                        val grow = 1f + 0.32f * pulse.value
                        scaleX = grow
                        scaleY = grow
                        alpha = 1f - pulse.value
                    }
                    .drawBehind {
                        val width = 4.dp.toPx()
                        // Inside the shape's edge, as a border lies.
                        inset(width / 2f) {
                            drawOutline(shapeOf().createOutline(size, layoutDirection, this), ring, style = Stroke(width))
                        }
                    },
            )
        }
        // The small shapes going round, each at its own pace.
        pieces.forEachIndexed { i, piece ->
            Box(
                modifier = Modifier
                    .size(if (i == 1) 18.dp else 14.dp)
                    .graphicsLayer {
                        val angle = Math.toRadians((orbit * (1f + 0.35f * i) + i * 120f).toDouble())
                        val reach = ORBIT_RADIUS.toPx() * (if (i == 2) 0.93f else 1f)
                        translationX = (cos(angle) * reach).toFloat()
                        translationY = (sin(angle) * reach).toFloat()
                        rotationZ = orbit * 3f
                    }
                    .clip(piece)
                    .background(pieceColors[i]),
            )
        }
        // The shape, filled from below as the export goes.
        Box(
            modifier = Modifier
                .size(SHAPE_SIZE)
                .graphicsLayer {
                    shape = shapeOf()
                    clip = true
                }
                .background(scheme.primaryContainer)
                .drawBehind {
                    if (known || shown > 0f) {
                        val amplitude = amplitudeOf()
                        drawPath(wavePath(size, shown, phase + WAVE_BACK_SHIFT, amplitude * 0.8f), scheme.primary.copy(alpha = 0.35f))
                        drawPath(wavePath(size, shown, phase, amplitude), scheme.primary)
                    }
                },
        )
        Crossfade(targetState = known, label = "heroMiddle") { counting ->
            if (counting) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SHAPE_SIZE)) {
                    // Over the empty part, then again, clipped to the wave, over the filled part.
                    PercentText(percent = percent, color = scheme.onPrimaryContainer, modifier = Modifier.matchParentSize())
                    PercentText(
                        percent = percent,
                        color = scheme.onPrimary,
                        modifier = Modifier
                            .matchParentSize()
                            .drawWithContent {
                                clipPath(wavePath(size, shown, phase, amplitudeOf())) { this@drawWithContent.drawContent() }
                            },
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SHAPE_SIZE)) {
                    LoadingIndicator(modifier = Modifier.size(120.dp), color = scheme.onPrimaryContainer)
                }
            }
        }
    }
}

/** The percentage, each digit rolling up when it changes. */
@Composable
private fun PercentText(percent: Int, color: Color, modifier: Modifier) {
    val digits = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = 76.sp, lineHeight = 80.sp, fontFeatureSettings = "tnum")
    Box(contentAlignment = Alignment.Center, modifier = modifier.clearAndSetSemantics { }) {
        Row(verticalAlignment = Alignment.Bottom) {
            val text = percent.toString()
            text.forEachIndexed { i, digit ->
                // Keyed from the right, so that the units stay the units when a digit is added.
                key(text.length - i) {
                    AnimatedContent(
                        targetState = digit,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) using SizeTransform(clip = false)
                        },
                        label = "digit",
                    ) { shown ->
                        Text(text = shown.toString(), style = digits, color = color)
                    }
                }
            }
            Text(
                text = "%",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = color,
                modifier = Modifier.padding(bottom = 14.dp, start = 2.dp),
            )
        }
    }
}

/** A filled wave rising to [level] (0..1) of [size], shifted by [phase], [amplitude] high. */
private fun wavePath(size: Size, level: Float, phase: Float, amplitude: Float): Path = Path().apply {
    val base = size.height * (1f - level)
    val length = size.width / WAVES_ACROSS
    val step = size.width / WAVE_STEPS
    moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + step) {
        lineTo(x, base + amplitude * sin(2f * PI.toFloat() * x / length + phase))
        x += step
    }
    lineTo(size.width + step, size.height)
    close()
}

/** A time with its icon and label above; [value] null shows that it is being worked out. */
@Composable
private fun TimeTile(icon: ImageVector, label: String, value: String?, emphasized: Boolean, shape: Shape, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = if (emphasized) scheme.tertiaryContainer else scheme.surfaceContainerHigh
    val content = if (emphasized) scheme.onTertiaryContainer else scheme.onSurface
    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.8f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        AnimatedContent(
            targetState = value != null,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            label = "tileValue",
        ) { ready ->
            if (ready) {
                Text(
                    text = value.orEmpty(),
                    style = MaterialTheme.typography.headlineMediumEmphasized.copy(fontFeatureSettings = "tnum"),
                    color = content,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = stringResource(R.string.processing_left_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

/** The time left, once the encoding has run long enough to tell; null before. */
private fun timeLeftMs(state: ConversionState.Processing, now: Long): Long? {
    if (state.stage != ConversionStage.ENCODING || state.encodingSinceMs <= 0L) return null
    val fraction = state.progress
    val encoding = now - state.encodingSinceMs
    if (fraction < MIN_FRACTION_FOR_ETA || encoding < MIN_ELAPSED_FOR_ETA_MS) return null
    return (encoding * (1f - fraction) / fraction).toLong()
}

private fun stageLabelRes(stage: ConversionStage): Int = when (stage) {
    ConversionStage.PREPARING -> R.string.export_stage_preparing
    ConversionStage.ANALYZING -> R.string.export_stage_analyzing
    ConversionStage.ENCODING -> R.string.export_stage_encoding
    ConversionStage.PACKING -> R.string.export_stage_packing
}

/** 0:42, 12:05, 1:02:03. */
private fun clockText(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

/**
 * What is being made, as the head of the page rather than a capsule on top: the
 * format's badge (easing into a circle and back while it works), "Exporting to
 * MP4" and the name of the file it comes from, centred over the progress.
 */
@Composable
private fun FileRow(fileName: String, format: OutputFormat?) {
    if (fileName.isEmpty() && format == null) return
    val scheme = MaterialTheme.colorScheme
    var morphed by remember { mutableStateOf(false) }
    if (format != null) {
        LaunchedEffect(format) {
            while (true) {
                delay(BADGE_BREATH_MS)
                morphed = !morphed
            }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics(mergeDescendants = true) {},
    ) {
        if (format != null) {
            FormatBadge(format = format, size = 52.dp, morphed = morphed)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.processing_exporting_to, formatShortName(format)),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = scheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        if (fileName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** How long the badge holds each shape while the export runs. */
private const val BADGE_BREATH_MS = 1_600L

/** A time in a capsule; [emphasized] in the secondary container. */
@Composable
private fun StatPill(icon: ImageVector, text: String, emphasized: Boolean = false) {
    val container = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(container)
            .padding(start = 12.dp, end = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = content)
    }
}

// -- Done ---------------------------------------------------------------------------------

@Composable
private fun ColumnScope.DoneContent(state: ConversionState.Success, format: OutputFormat?, onEditAgain: () -> Unit, onNewConversion: () -> Unit, part: Part) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val file = remember(state.outputPath) { File(state.outputPath) }
    val size = remember(state.outputPath) { file.length() }
    val isZip = file.extension.equals("zip", ignoreCase = true)
    val shareTitle = stringResource(R.string.share_chooser_title)
    val noApp = stringResource(R.string.done_no_app)
    if (part != Part.DETAILS) {
        // Once, by the side that shows the badge (or the whole column).
        LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

        if (part == Part.WHOLE) Spacer(modifier = Modifier.height(24.dp))
        Fitted(part) { CelebrationBadge() }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.done_title),
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (state.tookMs > 0L) {
            Text(
                text = stringResource(R.string.done_took, clockText(state.tookMs)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state.encoder) {
            VideoEncoderUse.HARDWARE -> Text(
                text = stringResource(R.string.done_encoder_hardware),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            VideoEncoderUse.FALLBACK -> Text(
                text = stringResource(R.string.done_encoder_fallback),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            VideoEncoderUse.NONE -> Unit
        }
    }
    if (part == Part.PICTURE) return
    if (part == Part.WHOLE) Spacer(modifier = Modifier.height(28.dp))

    // The file made.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        if (format != null && !isZip) {
            FormatBadge(format = format, size = 64.dp)
        } else {
            MorphingContainer(
                from = MaterialShapes.Cookie6Sided,
                to = MaterialShapes.Cookie6Sided,
                morphed = false,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isZip) {
                    stringResource(R.string.done_zip) + ", " + Formatter.formatShortFileSize(context, size)
                } else {
                    Formatter.formatShortFileSize(context, size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // Where the copy in the phone's shared folders went (see the settings).
    val copiedTo = state.copiedTo
    if (copiedTo != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.done_copied, copiedTo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shareFile(context, file, shareTitle)
        },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(stringResource(R.string.done_share), style = MaterialTheme.typography.titleMediumEmphasized)
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { if (!openFile(context, file)) Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show() },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.done_open), style = MaterialTheme.typography.titleSmallEmphasized)
        }
        Button(
            onClick = onEditAgain,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.done_edit_again), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onNewConversion,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(stringResource(R.string.done_new_conversion), style = MaterialTheme.typography.titleSmallEmphasized)
    }
}

/**
 * The badge of a finished export: it grows in and morphs from a circle into a
 * soft burst while a ring of small shapes flies out of it and fades, once.
 */
@Composable
private fun CelebrationBadge() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.3f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "badgeScale",
    )
    val burst by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(BURST_MS, easing = FastOutSlowInEasing),
        label = "burst",
    )
    val scheme = MaterialTheme.colorScheme
    val pieces = remember {
        listOf(
            MaterialShapes.Cookie4Sided, MaterialShapes.Flower, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf,
            MaterialShapes.Pill, MaterialShapes.Gem, MaterialShapes.Cookie6Sided, MaterialShapes.SoftBurst,
        ).map { PolygonShape(it) }
    }
    val colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        pieces.forEachIndexed { index, shape ->
            val angle = Math.toRadians(index * 45.0 - 90.0 + 22.5)
            val reach = 52f + 72f * burst
            Box(
                modifier = Modifier
                    .size(if (index % 2 == 0) 22.dp else 16.dp)
                    .graphicsLayer {
                        translationX = (cos(angle) * reach).toFloat().dp.toPx()
                        translationY = (sin(angle) * reach).toFloat().dp.toPx()
                        rotationZ = burst * 200f * (if (index % 2 == 0) 1f else -1f)
                        alpha = (1f - burst * burst).coerceIn(0f, 1f)
                        scaleX = 0.4f + burst
                        scaleY = 0.4f + burst
                    }
                    .clip(shape)
                    .background(colors[index % colors.size]),
            )
        }
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.SoftBurst,
            morphed = shown,
            color = scheme.primaryContainer,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(132.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(60.dp))
        }
    }
}

/** Hands [file] to the share sheet, with its real type so that the apps that save files offer themselves. */
internal fun shareFile(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(file)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/** Opens [file] in an app that shows it; false when none can. */
private fun openFile(context: Context, file: File): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun mimeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "*/*"

// -- Failed -------------------------------------------------------------------------------

@Composable
private fun ColumnScope.FailedContent(state: ConversionState.Error, onBackToEditor: () -> Unit, onStartOver: () -> Unit, part: Part) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    if (part != Part.DETAILS) {
        if (part == Part.WHOLE) Spacer(modifier = Modifier.height(32.dp))
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie4Sided,
            morphed = shown,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(120.dp),
        ) {
            Icon(Icons.Rounded.PriorityHigh, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(52.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.error_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    if (part == Part.PICTURE) return
    if (part == Part.WHOLE) Spacer(modifier = Modifier.height(24.dp))
    if (state.logs.isNotEmpty()) {
        LogCard(logs = state.logs, startOpen = true)
        Spacer(modifier = Modifier.height(16.dp))
    }
    Button(
        onClick = onBackToEditor,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(stringResource(R.string.error_back_to_editor), style = MaterialTheme.typography.titleMediumEmphasized)
    }
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onStartOver,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.filledTonalButtonColors(),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(stringResource(R.string.error_back_to_start), style = MaterialTheme.typography.titleSmallEmphasized)
    }
}

// -- Log ----------------------------------------------------------------------------------

/**
 * FFmpeg's log: closed, its last line ticking by; open, the whole of it,
 * following the end. It can be copied either way.
 */
@Composable
private fun LogCard(logs: List<String>, startOpen: Boolean) {
    var open by remember { mutableStateOf(startOpen) }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.log_copied)
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size, open) {
        if (open && logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    open = !open
                }
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.processing_log), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
                if (!open) {
                    Text(
                        text = logs.lastOrNull()?.trim().orEmpty(),
                        style = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(logs.joinToString("\n") { it.trimEnd() }))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                },
                enabled = logs.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.log_copy), modifier = Modifier.size(20.dp))
            }
            Icon(
                imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (open) R.string.processing_hide_log else R.string.processing_show_log),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                items(logs) { line ->
                    Text(
                        text = line.trimEnd(),
                        style = mono,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** One turn of the shape of the running export. */
private const val HERO_TURN_MS = 24_000

/** The running export's hero: its whole box, the halo, the shape, the round of the small shapes. */
private val HERO_SIZE = 300.dp
private val HALO_SIZE = 284.dp
private val SHAPE_SIZE = 232.dp
private val ORBIT_RADIUS = 140.dp

/** How often the shape morphs into the next one. */
private const val MORPH_EVERY_MS = 1_800L

/** The wave in the shape: one slide across, how high it goes, how many crests fit, how finely it is drawn. */
private const val WAVE_MS = 2_400
private val WAVE_HEIGHT = 9.dp
private const val WAVES_ACROSS = 1.25f
private const val WAVE_STEPS = 48f

/** The wave behind, a little ahead of the front one. */
private const val WAVE_BACK_SHIFT = 1.9f

/** One round of the small shapes, one breath of the halo, one ring of a quarter done. */
private const val ORBIT_MS = 9_000
private const val BREATH_MS = 2_600
private const val PULSE_MS = 900

/** How soft the top of the tide behind the screen is. */
private val TIDE_EDGE = 160.dp

/** How long the shapes of a finished export take to fly out. */
private const val BURST_MS = 1_100

private const val MIN_FRACTION_FOR_ETA = 0.03f
private const val MIN_ELAPSED_FOR_ETA_MS = 3_000L
