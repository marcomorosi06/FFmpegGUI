@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlin.math.roundToInt

/*
 * How a picture becomes shapes (an SVG), the same in the SVG editor and in the
 * picture editor. First what the picture is (a logo, a drawing, text, an
 * illustration, a photo, pixel art), each a ready setting; then what the
 * trace made, as figures and as its very colours; then, for whoever wants to
 * go further, its colours and its shapes, every slider saying in words what
 * its two ends do.
 */

/** What a trace made: its colours (ARGB, the largest first) and how many outlines it has. */
internal class TraceResult(val palette: List<Int>, val outlines: Int)

/** A change to the trace; the step names a run of changes that undo as one (null: one change). */
internal typealias TraceChange = (String?, (TraceSettings) -> TraceSettings) -> Unit

/** What a picture is, each with the setting that suits it. */
internal enum class TracePreset(
    val labelRes: Int,
    val hintRes: Int,
    val icon: ImageVector,
    val shape: RoundedPolygon,
    val settle: (TraceSettings) -> TraceSettings,
) {
    LOGO(R.string.trace_kind_logo, R.string.trace_kind_logo_hint, Icons.Rounded.Interests, MaterialShapes.Cookie9Sided,
        { it.copy(mono = false, colors = 8, detail = 0.75f, speckle = 8, smooth = true) }),
    DRAWING(R.string.trace_kind_drawing, R.string.trace_kind_drawing_hint, Icons.Rounded.Draw, MaterialShapes.Flower,
        { it.copy(mono = true, detail = 0.7f, speckle = 12, smooth = true) }),
    TEXT(R.string.trace_kind_text, R.string.trace_kind_text_hint, Icons.Rounded.TextFields, MaterialShapes.Square,
        { it.copy(mono = true, detail = 0.9f, speckle = 4, smooth = true) }),
    ILLUSTRATION(R.string.trace_kind_illustration, R.string.trace_kind_illustration_hint, Icons.Rounded.Palette, MaterialShapes.SoftBurst,
        { it.copy(mono = false, colors = 16, detail = 0.6f, speckle = 8, smooth = true) }),
    PHOTO(R.string.trace_kind_photo, R.string.trace_kind_photo_hint, Icons.Rounded.Photo, MaterialShapes.Clover4Leaf,
        { it.copy(mono = false, colors = 24, detail = 0.45f, speckle = 30, smooth = true) }),
    PIXEL(R.string.trace_kind_pixel, R.string.trace_kind_pixel_hint, Icons.Rounded.GridOn, MaterialShapes.PixelCircle,
        { it.copy(mono = false, colors = 32, detail = 1f, speckle = 0, smooth = false) }),
    ;

    /** Whether [settings] are this kind's, as it sets them (the traced size apart). */
    fun matches(settings: TraceSettings): Boolean = settle(settings).sane() == settings.sane()
}

/** How heavy a trace is: what a viewer has to draw. */
private enum class TraceWeight(val labelRes: Int) { LIGHT(R.string.trace_weight_light), MEDIUM(R.string.trace_weight_medium), HEAVY(R.string.trace_weight_heavy) }

private fun weightOf(outlines: Int): TraceWeight = when {
    outlines < LIGHT_OUTLINES -> TraceWeight.LIGHT
    outlines < HEAVY_OUTLINES -> TraceWeight.MEDIUM
    else -> TraceWeight.HEAVY
}

/**
 * The trace's settings: what the picture is, what came of it, its colours and
 * its shapes. [working] while a newer trace is being made; [result] the last
 * one (null before the first).
 */
@Composable
internal fun TraceControls(
    settings: TraceSettings,
    onChange: TraceChange,
    working: Boolean,
    result: TraceResult?,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val preset = TracePreset.entries.firstOrNull { it.matches(settings) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        // -- What it is --------------------------------------------------------------------
        SectionTitle(Icons.Rounded.Interests, stringResource(R.string.trace_kind_title))
        OptionTileRow(
            options = TracePreset.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = preset,
            onSelect = { kind -> onChange(null) { kind.settle(it) } },
        )
        AnimatedContent(
            targetState = preset,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "traceKindHint",
        ) { kind ->
            Text(
                text = stringResource(kind?.hintRes ?: R.string.trace_kind_custom_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // -- What came of it ----------------------------------------------------------------
        TraceOutcome(working = working, result = result, mono = settings.mono)

        // -- Its colours ----------------------------------------------------------------------
        SectionTitle(Icons.Rounded.Palette, stringResource(R.string.trace_section_colours))
        PanelCard {
            ConnectedChoiceGroup(
                options = listOf(false, true),
                selected = settings.mono,
                label = { stringResource(if (it) R.string.trace_mono else R.string.trace_colours) },
                onSelect = { mono -> onChange(null) { it.copy(mono = mono) } },
            )
            AnimatedVisibility(
                visible = !settings.mono,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                ValueSlider(
                    label = stringResource(R.string.trace_colour_count),
                    valueText = settings.colors.toString(),
                    value = settings.colors.toFloat().coerceIn(MIN_COLOURS.toFloat(), MAX_COLOURS.toFloat()),
                    onValueChange = { v -> onChange("colors") { it.copy(colors = v.roundToInt()) } },
                    valueRange = MIN_COLOURS.toFloat()..MAX_COLOURS.toFloat(),
                    steps = MAX_COLOURS - MIN_COLOURS - 1,
                    icon = Icons.Rounded.Palette,
                    defaultValue = TraceSettings().colors.toFloat(),
                    startLabel = stringResource(R.string.trace_colours_few),
                    endLabel = stringResource(R.string.trace_colours_many),
                )
            }
        }

        // -- Its shapes -----------------------------------------------------------------------
        SectionTitle(Icons.Rounded.Polyline, stringResource(R.string.trace_section_shapes))
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.trace_accuracy),
                valueText = stringResource(
                    when {
                        settings.detail < 0.34f -> R.string.trace_accuracy_loose
                        settings.detail < 0.8f -> R.string.trace_accuracy_balanced
                        else -> R.string.trace_accuracy_exact
                    },
                ),
                value = settings.detail,
                onValueChange = { v -> onChange("detail") { it.copy(detail = v) } },
                valueRange = 0f..1f,
                icon = Icons.Rounded.Polyline,
                defaultValue = TraceSettings().detail,
                startLabel = stringResource(R.string.trace_accuracy_start),
                endLabel = stringResource(R.string.trace_accuracy_end),
            )
            ValueSlider(
                label = stringResource(R.string.trace_spots),
                valueText = if (settings.speckle == 0) stringResource(R.string.trace_spots_none) else stringResource(R.string.trace_spots_value, settings.speckle),
                value = settings.speckle.toFloat().coerceIn(0f, MAX_SPECKLE),
                onValueChange = { v -> onChange("speckle") { it.copy(speckle = v.roundToInt()) } },
                valueRange = 0f..MAX_SPECKLE,
                icon = Icons.Rounded.BubbleChart,
                defaultValue = TraceSettings().speckle.toFloat(),
                startLabel = stringResource(R.string.trace_spots_start),
                endLabel = stringResource(R.string.trace_spots_end),
            )
        }
        SwitchCard(
            icon = Icons.Rounded.Gesture,
            title = stringResource(R.string.trace_smooth),
            subtitle = stringResource(R.string.trace_smooth_hint),
            checked = settings.smooth,
            onCheckedChange = { on -> onChange(null) { it.copy(smooth = on) } },
        )
    }
}

/** A section's title, in the theme's emphasized type. */
@Composable
private fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * What the last trace made: its colours as a row of swatches (one ink for black
 * and white), how many shapes, and how heavy the file is, the meter filling
 * a segment per weight. Dimmed while a newer trace is made.
 */
@Composable
private fun TraceOutcome(working: Boolean, result: TraceResult?, mono: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val weight = result?.let { weightOf(it.outlines) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(16.dp),
    ) {
        // The palette, overlapping a little, as many as fit; the rest counted.
        val palette = result?.palette.orEmpty()
        val shown = palette.take(MAX_SWATCHES)
        val described = stringResource(R.string.trace_palette_description, palette.size)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = described },
        ) {
            shown.forEachIndexed { i, argb ->
                Box(
                    modifier = Modifier
                        .offset(x = (-8 * i).dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(argb or (0xFF shl 24)))
                        .border(2.dp, scheme.surfaceContainerHighest, CircleShape),
                )
            }
            if (palette.size > shown.size) {
                Text(
                    text = "+" + (palette.size - shown.size),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = (-8 * shown.size + 12).dp),
                )
            }
        }
        // The figures, and the weight as three segments.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = if (working || result == null) null else result.outlines to result.palette.size,
                    transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "traceFigures",
                ) { figures ->
                    Text(
                        text = figures?.let { (outlines, colours) ->
                            if (mono) {
                                pluralStringResource(R.plurals.trace_shapes_mono, outlines, outlines)
                            } else {
                                pluralStringResource(R.plurals.trace_shapes, outlines, outlines, colours)
                            }
                        } ?: stringResource(R.string.trace_working),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = weight?.let { stringResource(it.labelRes) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            WeightMeter(weight)
        }
    }
}

/** Three segments that fill up to [weight], each growing and colouring on the theme's springs. */
@Composable
private fun WeightMeter(weight: TraceWeight?) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val filled = (weight?.ordinal ?: -1) + 1
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(TraceWeight.entries.size) { i ->
            val on = i < filled
            val tone = when (weight) {
                TraceWeight.HEAVY -> scheme.error
                TraceWeight.MEDIUM -> scheme.tertiary
                else -> scheme.primary
            }
            val color by animateColorAsState(if (on) tone else scheme.outlineVariant, motion.defaultEffectsSpec(), label = "weightColor")
            val height by animateDpAsState(if (on) (12 + 8 * i).dp else 10.dp, motion.fastSpatialSpec(), label = "weightHeight")
            Box(modifier = Modifier.width(10.dp).height(height).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

/** The badge of the trace: its kind's shape, turning into a cookie while a trace is made. */
@Composable
internal fun TraceBadge(settings: TraceSettings, working: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val kind = TracePreset.entries.firstOrNull { it.matches(settings) }
    MorphingContainer(
        from = kind?.shape ?: MaterialShapes.SoftBurst,
        to = MaterialShapes.Cookie12Sided,
        morphed = working,
        color = scheme.primary,
        modifier = modifier,
    ) {
        Icon(kind?.icon ?: Icons.Rounded.Palette, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(24.dp))
    }
}

private const val MIN_COLOURS = 2
private const val MAX_COLOURS = 32
private const val MAX_SPECKLE = 100f
private const val MAX_SWATCHES = 12

/** Below this many outlines a trace is light; from the second it is heavy. */
private const val LIGHT_OUTLINES = 400
private const val HEAVY_OUTLINES = 2_500

/**
 * Over a preview while a newer trace is made: the shapes dim under a light
 * scrim and a contained loading indicator (its shapes morphing one into the
 * next) pops in on the spatial spring with what is happening under it. It
 * waits a moment before showing, so that a quick trace does not flash it.
 * Touches go through to the preview.
 */
@Composable
internal fun TracingOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(OVERLAY_DELAY_MS)
            shown = true
        } else {
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.28f))) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(motion.defaultSpatialSpec(), initialScale = 0.5f),
                        exit = scaleOut(motion.fastSpatialSpec(), targetScale = 0.7f),
                    )
                    .clip(RoundedCornerShape(36.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.trace_working), style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.onSurface)
            }
        }
    }
}

/** How long a trace runs before the overlay shows. */
private const val OVERLAY_DELAY_MS = 150L
