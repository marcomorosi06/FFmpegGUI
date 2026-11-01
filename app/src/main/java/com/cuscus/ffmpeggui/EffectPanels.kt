@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalDensity
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Speed
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.PictureLook
import com.cuscus.ffmpeggui.core.preview.hueColorMatrix
import com.cuscus.ffmpeggui.effects.EffectInfo
import com.cuscus.ffmpeggui.effects.EffectLibrary
import com.cuscus.ffmpeggui.effects.EffectSheet
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The app's own effects in the editor: the gallery they are picked from (each
 * tile flicking through its own frames, on black as the effect is drawn), and
 * the panels of a chosen one: which effect it is, how strong and in which
 * colours.
 */

/** The name of effect [name], as the editor shows it; null for one this version does not know. */
@StringRes
fun effectLabelRes(name: String): Int? = when (name) {
    "sparks" -> R.string.effect_sparks
    "fireworks" -> R.string.effect_fireworks
    "flames" -> R.string.effect_flames
    "smoke" -> R.string.effect_smoke
    "snow" -> R.string.effect_snow
    "rain" -> R.string.effect_rain
    "confetti" -> R.string.effect_confetti
    "dust" -> R.string.effect_dust
    "bokeh" -> R.string.effect_bokeh
    "hearts" -> R.string.effect_hearts
    "math" -> R.string.effect_math
    "bw" -> R.string.look_bw
    "old_film" -> R.string.look_old_film
    "vhs" -> R.string.look_vhs
    "glitch" -> R.string.look_glitch
    "tv_static" -> R.string.look_tv_static
    "prism" -> R.string.look_prism
    "shake" -> R.string.look_shake
    "pulse" -> R.string.look_pulse
    "rainbow" -> R.string.look_rainbow
    "strobe" -> R.string.look_strobe
    "pixelate" -> R.string.look_pixelate
    "invert" -> R.string.look_invert
    "mirror" -> R.string.look_mirror
    "glow" -> R.string.look_glow
    "fairy" -> R.string.effect_fairy
    "explosion" -> R.string.effect_explosion
    "flash" -> R.string.effect_flash
    else -> null
}

/** Every effect's name as the editor shows it, by its library name. */
@Composable
fun effectLabels(): Map<String, String> {
    val names = (EffectLibrary.loops + EffectLibrary.transitions).map { it.name }
    return names.associateWith { name -> effectLabelRes(name)?.let { stringResource(it) } ?: name }
}

/** The small frames of effect [name] (null until they are read, which is quick: one JPEG). */
@Composable
internal fun rememberSheet(name: String): EffectSheet? {
    val sheet by produceState(initialValue = EffectLibrary.sheetIfRead(name), name) {
        if (value == null) value = withContext(Dispatchers.IO) { EffectLibrary.sheet(name) }
    }
    return sheet
}

/**
 * Frame [index] of [sheet] drawn to fill this scope (its shape kept, the
 * overflow cut evenly), through [filter]; [fraction] of that size, in the
 * middle (as the export lays a sized effect, see ImagePlacement.coverSize).
 */
internal fun DrawScope.drawSheetFrame(sheet: EffectSheet, image: ImageBitmap, index: Int, filter: ColorFilter?, fraction: Float = 1f) {
    val about = sheet.about
    val i = index.coerceIn(0, about.frames - 1)
    val column = i % about.columns
    val row = i / about.columns
    val scale = maxOf(size.width / about.width, size.height / about.height) * fraction
    val width = (about.width * scale).roundToInt()
    val height = (about.height * scale).roundToInt()
    drawImage(
        image = image,
        srcOffset = IntOffset(column * about.width, row * about.height),
        srcSize = IntSize(about.width, about.height),
        dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
        dstSize = IntSize(width, height),
        colorFilter = filter,
        filterQuality = if (sheet.about.width >= SHARP_SHEET_WIDTH) FilterQuality.Medium else FilterQuality.Low,
    )
}

/** Sheets this wide are drawn smoothly (a fist's are large enough to show it). */
private const val SHARP_SHEET_WIDTH = 400

/**
 * Effect [name] playing on black at its own speed from its small frames, its
 * colours turned by [hue]; [running] false holds one frame from its middle.
 * Only the drawing follows the frames: nothing recomposes while it plays.
 */
@Composable
fun EffectFrames(name: String, hue: Float, modifier: Modifier = Modifier, running: Boolean = true) {
    // A picture look shows on a little picture of its own.
    val look = PictureLook.of(name)
    if (look != null) {
        LookFrames(look, hue, modifier, running)
        return
    }
    val sheet = rememberSheet(name)
    val filter = remember(hue) { hueColorMatrix(hue)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    val image = remember(sheet) { sheet?.bitmap?.asImageBitmap() }
    val count = sheet?.about?.frames ?: 1
    val phase = if (running && sheet != null) {
        val loop = rememberInfiniteTransition(label = "effectFrames")
        loop.animateFloat(
            initialValue = 0f,
            targetValue = count.toFloat(),
            animationSpec = infiniteRepeatable(tween(sheet.lengthMs.toInt().coerceAtLeast(1), easing = LinearEasing)),
            label = "effectFrame",
        )
    } else {
        null
    }
    Canvas(modifier = modifier.background(Color.Black).clipToBounds()) {
        if (sheet == null || image == null) return@Canvas
        val index = phase?.value?.toInt() ?: (count / 2)
        drawSheetFrame(sheet, image, index, filter)
    }
}

/**
 * One effect as a tile: its frames playing in a frame that rounds off when it is
 * chosen, a ring of the primary and tertiary tones turning round it and a
 * badge (a still Material burst) popping in; it squishes under the finger.
 */
@Composable
fun EffectTile(
    effect: EffectInfo,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hue: Float = 0f,
    width: Dp = 112.dp,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    // Springs overshoot: a padding or a corner can never go below zero.
    val springRadius by animateDpAsState(if (selected) 30.dp else 16.dp, motion.defaultSpatialSpec(), label = "effectRadius")
    val springRing by animateDpAsState(if (selected) 4.dp else 0.dp, motion.fastSpatialSpec(), label = "effectRing")
    val radius = springRadius.coerceAtLeast(0.dp)
    val ringWidth = springRing.coerceAtLeast(0.dp)
    val badge by animateFloatAsState(if (selected) 1f else 0f, motion.fastSpatialSpec(), label = "effectBadge")
    val spin = rememberInfiniteTransition(label = "effectRing")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "ringTurn")
    val ringColors = listOf(scheme.primary, scheme.tertiary, scheme.primaryContainer, scheme.primary)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(width)
            .springPress(interaction, pressedScale = 0.88f)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(vertical = 4.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(radius + ringWidth))
                    .background(scheme.surfaceContainerHighest)
                    .drawBehind {
                        // A sweep of colours turning round the chosen tile.
                        if (ringWidth > 0.dp) rotate(turn) { drawCircle(Brush.sweepGradient(ringColors), radius = size.maxDimension) }
                    }
                    .padding(ringWidth)
                    .clip(RoundedCornerShape(radius)),
            ) {
                EffectFrames(effect.name, hue, modifier = Modifier.fillMaxSize())
            }
            if (badge > 0f) {
                MorphingContainer(
                    from = MaterialShapes.Circle,
                    to = MaterialShapes.SoftBurst,
                    morphed = selected,
                    color = scheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = badge
                            scaleY = badge
                            alpha = badge.coerceIn(0f, 1f)
                        },
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The gallery of looping effects in a sheet, under a big heading; [onPick]
 * gets the chosen one's name (the sheet closes itself).
 */
@Composable
internal fun EffectPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val labels = effectLabels()
    var family by rememberSaveable { mutableStateOf(EffectFamily.LIGHTS) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(horizontal = 16.dp).selectableGroup(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
                        .padding(14.dp),
                ) {
                    MorphingContainer(
                        from = MaterialShapes.Cookie12Sided,
                        to = MaterialShapes.SoftBurst,
                        morphed = true,
                        color = scheme.primary,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.effect_picker_title),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = scheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.effect_picker_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            // Light laid over the video, or looks that change the picture itself.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    ConnectedChoiceGroup(
                        options = EffectFamily.entries,
                        selected = family,
                        label = { stringResource(it.labelRes) },
                        onSelect = { family = it },
                    )
                    Text(
                        stringResource(if (family == EffectFamily.LOOKS) R.string.effect_looks_hint else R.string.effect_lights_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            items(family.effects, key = { it.name }) { effect ->
                EffectTile(
                    effect = effect,
                    label = labels[effect.name] ?: effect.name,
                    selected = false,
                    width = 200.dp,
                    onClick = { onPick(effect.name) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/** The two kinds of effects: light laid over the video, and looks that change the picture itself. */
internal enum class EffectFamily(val labelRes: Int) {
    LIGHTS(R.string.effect_family_lights),
    LOOKS(R.string.effect_looks_title),
    ;

    val effects: List<EffectInfo>
        get() = if (this == LOOKS) EffectLibrary.looks else EffectLibrary.loops.filter { it.look == null }

    companion object {
        fun of(name: String?): EffectFamily = if (PictureLook.of(name) != null) LOOKS else LIGHTS
    }
}

/**
 * Which effect the selected one is: its family (light or look) in a connected
 * button group, then that family's tiles in two rows that scroll sideways, the
 * current one chosen and in sight.
 */
@Composable
internal fun EffectChoiceContent(current: String?, hue: Float, onPick: (String) -> Unit) {
    val labels = effectLabels()
    val motion = MaterialTheme.motionScheme
    var family by rememberSaveable { mutableStateOf(EffectFamily.of(current)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelHint(stringResource(R.string.effect_choice_hint))
        ConnectedChoiceGroup(
            options = EffectFamily.entries,
            selected = family,
            label = { stringResource(it.labelRes) },
            onSelect = { family = it },
        )
        Crossfade(targetState = family, animationSpec = motion.defaultEffectsSpec(), label = "effectFamily") { shown ->
            val effects = shown.effects
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // The chosen one in sight when the family opens.
            LaunchedEffect(shown) {
                val index = effects.indexOfFirst { it.name == current }
                val column = if (index < 0) 0 else index / CHOICE_ROWS
                scroll.animateScrollTo(with(density) { ((CHOICE_TILE + CHOICE_GAP) * column).roundToPx() }, motion.defaultSpatialSpec())
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(CHOICE_GAP),
                modifier = Modifier.fillMaxWidth().selectableGroup().horizontalScroll(scroll),
            ) {
                effects.chunked(CHOICE_ROWS).forEach { column ->
                    Column(verticalArrangement = Arrangement.spacedBy(CHOICE_GAP)) {
                        column.forEach { effect ->
                            EffectTile(
                                effect = effect,
                                label = labels[effect.name] ?: effect.name,
                                selected = effect.name == current,
                                hue = hue,
                                onClick = { onPick(effect.name) },
                                width = CHOICE_TILE,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The choice's rows of tiles, their width and the space between them. */
private const val CHOICE_ROWS = 2
private val CHOICE_TILE = 112.dp
private val CHOICE_GAP = 8.dp

/** Hue turns offered as ready-made colours, in degrees. */
private val HUE_PRESETS = listOf(0f, 60f, 120f, 180f, 240f, 300f)

/**
 * How the selected effect shows: its strength ([opacity]) and its colours
 * turned round the wheel ([hue], degrees), the effect playing large above the
 * sliders as it will look, ready-made colours as its own frame tinted.
 */
@Composable
internal fun EffectLookContent(
    name: String,
    opacity: Float,
    hue: Float,
    onOpacity: (Float) -> Unit,
    onHue: (Float) -> Unit,
    amount: Float = 1f,
    speed: Float = 1f,
    onAmount: (Float) -> Unit = {},
    onSpeed: (Float) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val look = PictureLook.of(name)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(21f / 9f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black),
        ) {
            if (look != null) {
                // A look as it is set, over its picture as it is.
                LookFrames(look, hue, modifier = Modifier.fillMaxSize(), strength = opacity.coerceIn(0f, 1f), speed = speed, amount = amount)
            } else {
                EffectFrames(name, hue, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacity.coerceIn(0.05f, 1f) })
            }
            // Its name on a pill, its family's shape beside it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.92f))
                    .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            ) {
                MorphingContainer(
                    from = if (look != null) MaterialShapes.Cookie9Sided else MaterialShapes.SoftBurst,
                    to = MaterialShapes.Circle,
                    morphed = false,
                    color = if (look != null) scheme.tertiary else scheme.primary,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        if (look != null) Icons.Rounded.Tune else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (look != null) scheme.onTertiary else scheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    effectLabelRes(name)?.let { stringResource(it) } ?: name,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = scheme.onSurface,
                )
            }
        }
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.effect_strength),
                valueText = stringResource(R.string.value_percent, (opacity * 100).roundToInt()),
                value = opacity,
                onValueChange = onOpacity,
                valueRange = 0f..1f,
                icon = Icons.Rounded.Opacity,
                defaultValue = 1f,
            )
            // A look's own settings: how much it does, how fast it moves.
            if (look != null && look.hasAmount) {
                ValueSlider(
                    label = stringResource(R.string.look_amount),
                    valueText = stringResource(R.string.value_percent, (amount * 100).roundToInt()),
                    value = amount.coerceIn(PictureLook.MIN_AMOUNT, PictureLook.MAX_AMOUNT),
                    onValueChange = { onAmount((Math.round(it * 20f) / 20f).coerceIn(PictureLook.MIN_AMOUNT, PictureLook.MAX_AMOUNT)) },
                    valueRange = PictureLook.MIN_AMOUNT..PictureLook.MAX_AMOUNT,
                    icon = Icons.Rounded.Tune,
                    defaultValue = 1f,
                    startLabel = stringResource(R.string.value_percent, (PictureLook.MIN_AMOUNT * 100).roundToInt()),
                    endLabel = stringResource(R.string.value_percent, (PictureLook.MAX_AMOUNT * 100).roundToInt()),
                )
            }
            if (look != null && look.moves) {
                ValueSlider(
                    label = stringResource(R.string.look_speed),
                    valueText = stringResource(R.string.look_speed_value, decimal(speed)),
                    value = speed.coerceIn(PictureLook.MIN_SPEED, PictureLook.MAX_SPEED),
                    onValueChange = { onSpeed((Math.round(it * 20f) / 20f).coerceIn(PictureLook.MIN_SPEED, PictureLook.MAX_SPEED)) },
                    valueRange = PictureLook.MIN_SPEED..PictureLook.MAX_SPEED,
                    icon = Icons.Rounded.Speed,
                    defaultValue = 1f,
                    startLabel = stringResource(R.string.look_speed_value, decimal(PictureLook.MIN_SPEED)),
                    endLabel = stringResource(R.string.look_speed_value, decimal(PictureLook.MAX_SPEED)),
                )
            }
        }
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.effect_hue),
                valueText = stringResource(R.string.value_degrees, hue.roundToInt()),
                value = hue,
                onValueChange = { onHue(Math.round(it).toFloat()) },
                valueRange = -180f..180f,
                icon = Icons.Rounded.Palette,
                defaultValue = 0f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HUE_PRESETS.forEach { preset ->
                    val shown = if (preset > 180f) preset - 360f else preset
                    val chosen = Math.abs(hue - shown) < 1f
                    val chipSize by animateDpAsState(if (chosen) 48.dp else 40.dp, motion.fastSpatialSpec(), label = "hueChip")
                    val ring by animateColorAsState(if (chosen) scheme.primary else Color.Transparent, motion.defaultEffectsSpec(), label = "hueRing")
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        MorphingContainer(
                            from = MaterialShapes.Circle,
                            to = MaterialShapes.Cookie9Sided,
                            morphed = chosen,
                            color = ring,
                            modifier = Modifier
                                .size(chipSize)
                                .springPress(interaction, pressedScale = 0.85f)
                                .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onHue(shown)
                                }
                                .semantics { selected = chosen },
                        ) {
                            EffectFrames(
                                name,
                                shown,
                                running = false,
                                modifier = Modifier.padding(3.dp).fillMaxSize().clip(CircleShape).border(1.dp, scheme.outlineVariant, CircleShape),
                            )
                        }
                    }
                }
            }
        }
        PanelHint(stringResource(if (look != null) R.string.look_hint else R.string.effect_look_hint))
    }
}
