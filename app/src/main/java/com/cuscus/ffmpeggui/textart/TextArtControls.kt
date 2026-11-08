@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.textart

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.textart.ArtColors
import com.cuscus.ffmpeggui.core.textart.GlyphKey
import com.cuscus.ffmpeggui.core.textart.GlyphState
import com.cuscus.ffmpeggui.core.textart.HueBand
import com.cuscus.ffmpeggui.core.textart.Lab
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The text-art editor's own controls: nothing like them elsewhere in the app,
 * because nothing else is made of characters.
 *
 *  - GlyphKeyboard: the alphabet as keys, each with its ink measured on the
 *    font, lit when used; a tap switches a key, a long press picks it to be
 *    preferred or avoided.
 *  - HueRing: the colour wheel with the hues left out as arcs, dragged round.
 *  - ToneCurve: five points drawn through, the light of the picture to ink.
 *  - PaletteGrid: the 16 or 256 colours of the file, each banned or remapped.
 *  - SwatchCookies: the picture's own colours as cookies that deflate when hidden.
 */

/** A colour of the art (0xRRGGBB) as a Compose colour. */
internal fun artColor(rgb: Int): Color = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

/**
 * The alphabet as a keyboard: every key offered, its ink as a bar under it,
 * lit when the art uses it, dimmed and crossed out when it is off, faint when
 * a filter, the font or code page 437 keeps it out. [picked] is the key whose
 * bias is shown; [bias] colours the keys preferred (primary) or avoided (error).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun GlyphKeyboard(
    keys: List<GlyphKey>,
    bias: Map<String, Float>,
    picked: Int?,
    onToggle: (Int) -> Unit,
    onPick: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val maxInk = keys.maxOfOrNull { it.density }?.coerceAtLeast(0.01f) ?: 1f
    val stateNames = mapOf(
        GlyphState.ACTIVE to stringResource(R.string.textart_key_active),
        GlyphState.OFF to stringResource(R.string.textart_key_off),
        GlyphState.FILTERED to stringResource(R.string.textart_key_filtered),
        GlyphState.MISSING to stringResource(R.string.textart_key_missing),
        GlyphState.DUPLICATE to stringResource(R.string.textart_key_duplicate),
        GlyphState.UNENCODABLE to stringResource(R.string.textart_key_unencodable),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        keys.forEach { key ->
            val interaction = remember { MutableInteractionSource() }
            val b = bias[key.char] ?: 0f
            val active = key.state == GlyphState.ACTIVE
            val switchable = key.state == GlyphState.ACTIVE || key.state == GlyphState.OFF || key.state == GlyphState.DUPLICATE
            val container by animateColorAsState(
                targetValue = when {
                    key.code == picked -> scheme.tertiaryContainer
                    active && b > 0.05f -> scheme.primaryContainer
                    active && b < -0.05f -> scheme.errorContainer
                    active -> scheme.secondaryContainer
                    else -> scheme.surfaceContainerHighest
                },
                animationSpec = motion.fastEffectsSpec(),
                label = "keyContainer",
            )
            val content = when {
                key.code == picked -> scheme.onTertiaryContainer
                active && b > 0.05f -> scheme.onPrimaryContainer
                active && b < -0.05f -> scheme.onErrorContainer
                active -> scheme.onSecondaryContainer
                else -> scheme.onSurfaceVariant
            }
            val lift by animateFloatAsState(if (active) 1f else 0.82f, motion.fastSpatialSpec(), label = "keyLift")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .size(width = 40.dp, height = 52.dp)
                    .graphicsLayer {
                        scaleX = lift
                        scaleY = lift
                        alpha = if (switchable) 1f else 0.45f
                    }
                    .springPress(interaction, pressedScale = 0.86f)
                    .clip(RoundedCornerShape(if (key.code == picked) 20.dp else 12.dp))
                    .background(container)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = switchable,
                        role = Role.Checkbox,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPick(if (picked == key.code) null else key.code)
                        },
                        onClick = {
                            haptic.performHapticFeedback(if (active) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            onToggle(key.code)
                        },
                    )
                    .semantics {
                        contentDescription = key.char
                        stateDescription = stateNames[key.state] ?: ""
                    }
                    .padding(top = 4.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
            ) {
                val struck = key.state == GlyphState.OFF
                Text(
                    text = key.char,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = content,
                    textAlign = TextAlign.Center,
                    // Switched off: crossed out.
                    modifier = Modifier.weight(1f).drawWithContent {
                        drawContent()
                        if (struck) drawLine(content, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    },
                )
                // The ink, measured on the font.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((key.density / maxInk).coerceIn(0.04f, 1f))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(content),
                    )
                }
            }
        }
    }
}

/**
 * The colour wheel: the hues of [bands] are drawn as arcs over it (left out of
 * the art). Dragging on the ring moves the nearest end of the picked band;
 * a tap on a free part of the ring starts a band there.
 */
@Composable
internal fun HueRing(
    bands: List<HueBand>,
    picked: Int,
    onBands: (step: String, bands: List<HueBand>) -> Unit,
    onPicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val current by rememberUpdatedState(bands)
    val currentPicked by rememberUpdatedState(picked)
    val hues = remember { (0..360 step 30).map { hueColor(it.toFloat()) } }
    val description = stringResource(R.string.textart_hue_ring)
    var dragEnd by remember { mutableIntStateOf(-1) }
    Canvas(
        modifier = modifier
            .size(220.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val hue = angleOf(p, size.width / 2f, size.height / 2f)
                    val inside = current.indexOfFirst { com.cuscus.ffmpeggui.core.textart.TextArtRenderer.inBand(hue, it) }
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    if (inside >= 0) {
                        onPicked(inside)
                    } else {
                        onBands("hue:add", current + HueBand(hue - 15f, hue + 15f))
                        onPicked(current.size)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p ->
                        val band = current.getOrNull(currentPicked) ?: return@detectDragGestures
                        val hue = angleOf(p, size.width / 2f, size.height / 2f)
                        dragEnd = if (hueDistance(hue, band.start) <= hueDistance(hue, band.end)) 0 else 1
                    },
                    onDragEnd = { dragEnd = -1 },
                ) { change, _ ->
                    val index = currentPicked
                    val band = current.getOrNull(index) ?: return@detectDragGestures
                    val hue = angleOf(change.position, size.width / 2f, size.height / 2f).roundToInt().toFloat()
                    val next = if (dragEnd == 0) band.copy(start = hue) else band.copy(end = hue)
                    if (next != band) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        onBands("hue:$index", current.toMutableList().also { it[index] = next })
                    }
                }
            },
    ) {
        val stroke = size.minDimension * 0.14f
        val radius = size.minDimension / 2f - stroke
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(brush = Brush.sweepGradient(hues, centre), radius = radius, center = centre, style = Stroke(width = stroke))
        // The bands left out: a dark veil over their hues, with a bright edge, the picked one thicker.
        current.forEachIndexed { i, band ->
            val start = band.start
            var sweep = (band.end - band.start + 360f) % 360f
            if (sweep == 0f) sweep = 360f
            val chosen = i == currentPicked
            drawArc(
                color = Color.Black.copy(alpha = 0.62f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = stroke * (if (chosen) 1.18f else 1f), cap = StrokeCap.Round),
            )
            for (end in listOf(band.start, band.end)) {
                val a = Math.toRadians(end.toDouble())
                val p = Offset(centre.x + radius * cos(a).toFloat(), centre.y + radius * sin(a).toFloat())
                drawCircle(color = Color.White, radius = stroke * (if (chosen) 0.42f else 0.3f), center = p)
                drawCircle(color = scheme.primary, radius = stroke * (if (chosen) 0.42f else 0.3f), center = p, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

/** The colour of [hue] (degrees, OKLab's hue) at a mid light, for the ring. */
internal fun hueColor(hue: Float): Color {
    val a = Math.toRadians(hue.toDouble())
    return artColor(ArtColors.rgb(Lab(0.72f, (0.13 * cos(a)).toFloat(), (0.13 * sin(a)).toFloat())))
}

private fun angleOf(p: Offset, cx: Float, cy: Float): Float =
    ((Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())) + 360.0) % 360.0).toFloat()

private fun hueDistance(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % 360f
    return min(d, 360f - d)
}

/**
 * The tone curve: the lightness at 0, 1/4, 1/2, 3/4 and 1, each point dragged
 * up and down; the straight line is the picture as it is.
 */
@Composable
internal fun ToneCurve(
    points: List<Float>,
    onPoints: (step: String, points: List<Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val current by rememberUpdatedState(points)
    var dragging by remember { mutableIntStateOf(-1) }
    val description = stringResource(R.string.textart_curve)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHighest)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p ->
                        val n = current.size - 1
                        dragging = ((p.x / size.width) * n).roundToInt().coerceIn(0, n)
                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragEnd = {
                        dragging = -1
                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    },
                ) { change, _ ->
                    val i = dragging
                    if (i < 0) return@detectDragGestures
                    val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    val snapped = (v * 100f).roundToInt() / 100f
                    if (snapped != current[i]) onPoints("curve:$i", current.toMutableList().also { it[i] = snapped })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPoints("curve:reset", listOf(0f, 0.25f, 0.5f, 0.75f, 1f))
                })
            },
    ) {
        val pad = 14.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        // The grid, and the straight line of the picture as it is.
        for (k in 1..3) {
            val x = pad + w * k / 4f
            val y = pad + h * k / 4f
            drawLine(scheme.outlineVariant, Offset(x, pad), Offset(x, pad + h), strokeWidth = 1.dp.toPx())
            drawLine(scheme.outlineVariant, Offset(pad, y), Offset(pad + w, y), strokeWidth = 1.dp.toPx())
        }
        drawLine(scheme.outline, Offset(pad, pad + h), Offset(pad + w, pad), strokeWidth = 1.5.dp.toPx())
        val n = current.size - 1
        val path = Path()
        current.forEachIndexed { i, v ->
            val p = Offset(pad + w * i / n, pad + h * (1f - v))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, scheme.primary, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        current.forEachIndexed { i, v ->
            val p = Offset(pad + w * i / n, pad + h * (1f - v))
            val big = i == dragging
            drawCircle(scheme.primary, radius = if (big) 13.dp.toPx() else 9.dp.toPx(), center = p)
            drawCircle(scheme.onPrimary, radius = if (big) 5.dp.toPx() else 3.dp.toPx(), center = p)
        }
    }
}

/**
 * The colours of the file ([colors], 0xRRGGBB, 16 or 256 of them): a tap bans
 * one (it shrinks to a cross), a long press picks it, and with one picked a
 * tap on another paints the first as the second ([onRemap]).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun PaletteGrid(
    colors: IntArray,
    banned: Set<Int>,
    remap: Map<Int, Int>,
    onToggle: (Int) -> Unit,
    onRemap: (from: Int, to: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var picked by remember { mutableStateOf<Int?>(null) }
    val swatch = if (colors.size > 16) 22.dp else 40.dp
    val bannedLabel = stringResource(R.string.textart_palette_banned)
    val usedLabel = stringResource(R.string.textart_palette_used)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(if (colors.size > 16) 3.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (colors.size > 16) 3.dp else 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            colors.forEachIndexed { index, rgb ->
                val off = index in banned
                val target = remap[index]
                val scale by animateFloatAsState(if (off) 0.55f else 1f, motion.fastSpatialSpec(), label = "swatchScale")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(swatch)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(if (picked == index) RoundedCornerShape(30) else if (colors.size > 16) RoundedCornerShape(4.dp) else CircleShape)
                        .background(artColor(rgb))
                        .then(if (picked == index) Modifier.border(3.dp, scheme.primary, RoundedCornerShape(30)) else Modifier)
                        .combinedClickable(
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                picked = if (picked == index) null else index
                            },
                            onClick = {
                                val from = picked
                                if (from != null && from != index) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onRemap(from, index)
                                    picked = null
                                } else {
                                    haptic.performHapticFeedback(if (off) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                    onToggle(index)
                                }
                            },
                        )
                        .semantics {
                            contentDescription = "#%06X".format(rgb and 0xFFFFFF)
                            stateDescription = if (off) bannedLabel else usedLabel
                        },
                ) {
                    if (off) Text("\u00D7", color = if (ArtColors.oklab(rgb).l > 0.6f) Color.Black else Color.White, fontSize = 14.sp)
                    if (target != null && colors.size <= 16) {
                        Box(modifier = Modifier.size(swatch * 0.42f).clip(CircleShape).background(artColor(colors[target])).border(2.dp, Color.White, CircleShape))
                    }
                }
            }
        }
        val from = picked
        if (from != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(artColor(colors[from])))
                Text(stringResource(R.string.textart_palette_remap_hint), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (remap.containsKey(from)) {
                    Text(
                        stringResource(R.string.textart_palette_unmap),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onRemap(from, null)
                                picked = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The picture's own colours ([swatches], 0xRRGGBB) as cookies: tapped, a colour
 * is left out of the art and its cookie deflates into a small, slanted pill.
 */
@Composable
internal fun SwatchCookies(
    swatches: IntArray,
    hidden: (Int) -> Boolean,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val hiddenLabel = stringResource(R.string.textart_swatch_hidden)
    val shownLabel = stringResource(R.string.textart_swatch_shown)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
    ) {
        swatches.forEach { rgb ->
            val off = hidden(rgb)
            val interaction = remember { MutableInteractionSource() }
            val scale by animateFloatAsState(if (off) 0.7f else 1f, motion.defaultSpatialSpec(), label = "cookieScale")
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .springPress(interaction, pressedScale = 0.85f)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                        haptic.performHapticFeedback(if (off) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onToggle(rgb)
                    }
                    .semantics {
                        contentDescription = "#%06X".format(rgb and 0xFFFFFF)
                        stateDescription = if (off) hiddenLabel else shownLabel
                    },
            ) {
                MorphingContainer(
                    from = MaterialShapes.Cookie7Sided,
                    to = MaterialShapes.Slanted,
                    morphed = off,
                    color = artColor(rgb).copy(alpha = if (off) 0.4f else 1f),
                    modifier = Modifier.size(52.dp),
                ) {
                    if (off) Text("\u00D7", color = if (ArtColors.oklab(rgb).l > 0.6f) Color.Black else Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

/** A big number with what it counts under it, for the check panel. */
@Composable
internal fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMediumEmphasized, color = scheme.onSecondaryContainer, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer.copy(alpha = 0.8f), maxLines = 1)
    }
}

/** The sixteen colours of a theme, as a little 8 x 2 tile for its choice. */
@Composable
internal fun ThemeSwatches(colors: IntArray, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(36.dp)) {
        val cw = size.width / 4f
        val ch = size.height / 4f
        for (i in 0 until 16) {
            drawRect(artColor(colors[i]), topLeft = Offset((i % 4) * cw, (i / 4) * ch), size = androidx.compose.ui.geometry.Size(cw + 0.5f, ch + 0.5f))
        }
    }
}

/** A frame style drawn small: its corner and edges, for its tile. */
@Composable
internal fun FrameSample(corners: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = corners,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Degrees as the hue ring writes them. */
internal fun degrees(value: Float): String = "${(((value % 360f) + 360f) % 360f).roundToInt()}\u00B0"

/** The share [part] of [whole] as a percentage. */
internal fun percent(part: Int, whole: Int): String = if (whole <= 0) "0 %" else "${(100.0 * part / whole).roundToInt()} %"
