@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.ui.expressive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import kotlin.math.abs
import kotlin.math.min

/*
 * The building blocks of the editor's tool panels, all in one Material 3
 * Expressive language:
 *
 *  - PanelCard / PanelHint: very round tonal surfaces and the text that explains them;
 *  - OptionTile(Row): choices as shape tiles - a rounded square that morphs into a
 *    playful MaterialShapes shape when chosen;
 *  - ValueSlider: a slider with its value in a pill and a reset that pops in once
 *    the value leaves its default; with a ValueInput, - and + buttons and a field
 *    to type the exact value (RepeatingIconButton);
 *  - SwitchCard: a whole card that switches, its badge morphing with the state;
 *  - FilePickCard: a file slot, empty or filled;
 *  - ColorSwatchRow: colours as circles that turn into cookies when picked.
 *
 * Motion always comes from MaterialTheme.motionScheme.
 */

/** Corner radius of panel cards. */
private val CARD_RADIUS = 24.dp

/** A group of related controls on a tonal, very round surface. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        content = content,
    )
}

/** A line explaining what the controls around it do. */
@Composable
fun PanelHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Shapes a chosen tile morphs into, one after the other along a row. */
private val CHOSEN_SHAPES: List<RoundedPolygon>
    get() = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Flower,
        MaterialShapes.Sunny,
    )

/**
 * One choice as a tile: a badge with [icon] (or [preview]) above [label]. Chosen,
 * the badge morphs from a rounded square into [chosenShape] and takes the
 * primary container colour; it squishes under the finger.
 */
@Composable
fun OptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    chosenShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    enabled: Boolean = true,
    badgeSize: Dp = 56.dp,
    role: Role = Role.RadioButton,
    preview: (@Composable (color: Color) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContent",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(badgeSize + 20.dp)
            .springPress(interaction, pressedScale = 0.9f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(vertical = 4.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Square,
            to = chosenShape,
            morphed = selected,
            color = container,
            modifier = Modifier.size(badgeSize),
        ) {
            when {
                preview != null -> preview(content)
                icon != null -> Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One option of an [OptionTileRow]. */
class TileOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val preview: (@Composable (color: Color) -> Unit)? = null,
)

/** Picks one of [options] as a row of [OptionTile]s that scrolls sideways when it must. */
@Composable
fun <T> OptionTileRow(
    options: List<TileOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .horizontalScroll(rememberScrollState()),
    ) {
        val shapes = CHOSEN_SHAPES
        options.forEachIndexed { index, option ->
            OptionTile(
                label = option.label,
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                icon = option.icon,
                preview = option.preview,
                chosenShape = shapes[index % shapes.size],
                enabled = enabled,
                role = role,
            )
        }
    }
}

/**
 * A frame of [ratio] (width / height) drawn inside a tile badge; null draws a
 * dashed "free" frame.
 */
@Composable
fun AspectPreview(ratio: Float?, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(28.dp)) {
        val box = size.minDimension
        val r = ratio ?: 1f
        val width = if (r >= 1f) box else box * r
        val height = if (r >= 1f) box / r else box
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - width) / 2f + stroke / 2f, (size.height - height) / 2f + stroke / 2f),
            size = Size(width - stroke, height - stroke),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(
                width = stroke,
                pathEffect = if (ratio == null) {
                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                } else {
                    null
                },
            ),
        )
    }
}

/**
 * How the value of a [ValueSlider] can be typed and nudged: shown as the value
 * times [scale] with [decimals] digits and [unit] after it (a fraction 0..1 as
 * "35 %" is scale 100, unit "%"). The - and + buttons move it by [step] (in the
 * slider's own units). Typed values may go as far as [range] (the slider's
 * range when null).
 */
class ValueInput(
    val step: Float,
    val unit: String = "",
    val scale: Float = 1f,
    val decimals: Int = 0,
    val range: ClosedFloatingPointRange<Float>? = null,
) {
    /** [value] as the field shows it. */
    fun text(value: Float): String {
        val shown = value * scale
        val text = if (decimals <= 0) Math.round(shown).toString() else String.format(Locale.ROOT, "%.${decimals}f", shown)
        return if (text.startsWith("-") && text.trim('-', '0', '.').isEmpty()) text.removePrefix("-") else text
    }

    /** The value typed in [text] (a comma works as a decimal point), or null. */
    fun parse(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.let { it / scale }
}

/**
 * A slider with its [label], an optional [icon], and the value ([valueText]) in a
 * pill. When [defaultValue] is given, a reset button pops in as soon as the value
 * leaves it.
 *
 * Felt: a light tick as the shown value changes under the finger (no more than
 * one every few milliseconds), a firmer one when it passes the default, and one
 * when the finger lifts. Heard: TalkBack reads the label and [valueText].
 *
 * With [input], the value is also exact: - and + buttons on the sides (held,
 * they repeat) and a tap on the pill opens a field to type it, with its unit,
 * the range it accepts and a sign button where it can be negative.
 */
@Composable
fun ValueSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    defaultValue: Float? = null,
    steps: Int = 0,
    startLabel: String = "",
    endLabel: String = "",
    enabled: Boolean = true,
    input: ValueInput? = null,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val span = valueRange.endInclusive - valueRange.start
    val atDefault = defaultValue != null && abs(value - defaultValue) <= span / 1000f
    val moved = defaultValue != null && !atDefault
    val inputRange = input?.range ?: valueRange
    var editing by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableLongStateOf(0L) }
    var wasAtDefault by remember { mutableStateOf(atDefault) }
    // The ticks under the finger.
    LaunchedEffect(valueText) {
        if (dragging) {
            val now = System.nanoTime()
            if (atDefault && !wasAtDefault) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                lastTick = now
            } else if (now - lastTick > TICK_GAP_NANOS) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                lastTick = now
            }
        }
        wasAtDefault = atDefault
    }
    // The value the buttons last asked for: the caller may round it, so a press
    // goes on from it rather than from the rounded value.
    var pending by remember { mutableStateOf<Float?>(null) }
    val nudge: (Int) -> Boolean = { direction ->
        val step = input?.step
        if (step == null || !enabled) {
            false
        } else {
            val base = pending?.takeIf { abs(it - value) < step } ?: value
            val next = (base + direction * step).coerceIn(inputRange.start, inputRange.endInclusive)
            if (abs(next - base) < step / 1000f) {
                false
            } else {
                pending = next
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onValueChange(next)
                true
            }
        }
    }
    val typeLabel = stringResource(R.string.value_type)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = moved,
                enter = expandHorizontally(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkHorizontally(motion.fastEffectsSpec()) + scaleOut(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        defaultValue?.let(onValueChange)
                    },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = stringResource(R.string.value_reset, label),
                        tint = scheme.primary,
                    )
                }
            }
            val pill by animateColorAsState(
                targetValue = if (editing) scheme.primaryContainer else scheme.surfaceContainerHighest,
                animationSpec = motion.defaultEffectsSpec(),
                label = "valuePill",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(pill)
                    .then(
                        if (input != null) {
                            Modifier.clickable(enabled = enabled, onClickLabel = typeLabel, role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                editing = !editing
                            }
                        } else {
                            Modifier
                        },
                    )
                    .semantics { contentDescription = "$label: $valueText" }
                    .padding(start = 12.dp, end = if (input != null) 10.dp else 12.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleSmallEmphasized.copy(fontFeatureSettings = "tnum"),
                    color = if (editing) scheme.onPrimaryContainer else scheme.onSurface,
                )
                if (input != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (editing) Icons.Rounded.Close else Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = if (editing) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (input != null) {
            AnimatedVisibility(
                visible = editing,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                ValueField(
                    label = label,
                    value = value,
                    input = input,
                    range = inputRange,
                    onApply = { typed ->
                        pending = null
                        onValueChange(typed)
                        editing = false
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (input != null) {
                RepeatingIconButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.value_less, label),
                    enabled = enabled && value > inputRange.start + input.step / 1000f,
                    onStep = { nudge(-1) },
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { v ->
                    dragging = true
                    pending = null
                    onValueChange(v)
                },
                onValueChangeFinished = {
                    dragging = false
                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = label
                        stateDescription = valueText
                    },
            )
            if (input != null) {
                Spacer(modifier = Modifier.width(4.dp))
                RepeatingIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.value_more, label),
                    enabled = enabled && value < inputRange.endInclusive - input.step / 1000f,
                    onStep = { nudge(1) },
                )
            }
        }
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (input != null) 44.dp else 0.dp),
            ) {
                Text(startLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(endLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** At most one tick every this long while a slider is dragged. */
private const val TICK_GAP_NANOS = 28_000_000L

/**
 * The field that opens under a [ValueSlider]'s pill: the value as a number with
 * its unit, the range it accepts below (in red when the number is outside it),
 * a sign button where the value can be negative, and a button that applies it
 * (as does the keyboard's done key).
 */
@Composable
private fun ValueField(label: String, value: Float, input: ValueInput, range: ClosedFloatingPointRange<Float>, onApply: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var text by remember { mutableStateOf(input.text(value)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val typed = input.parse(text)
    val valid = typed != null && typed >= range.start - input.step / 1000f && typed <= range.endInclusive + input.step / 1000f
    val unit = input.unit
    val from = input.text(range.start) + if (unit.isNotEmpty()) " $unit" else ""
    val to = input.text(range.endInclusive) + if (unit.isNotEmpty()) " $unit" else ""
    val apply = {
        if (valid && typed != null) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onApply(typed.coerceIn(range.start, range.endInclusive))
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }.take(MAX_TYPED) },
            label = { Text(label) },
            singleLine = true,
            isError = text.isNotEmpty() && !valid,
            leadingIcon = if (range.start < 0f) {
                {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        text = if (text.startsWith("-")) text.removePrefix("-") else "-$text"
                    }) {
                        Icon(Icons.Rounded.Exposure, contentDescription = stringResource(R.string.value_sign))
                    }
                }
            } else {
                null
            },
            suffix = if (unit.isNotEmpty()) {
                { Text(unit) }
            } else {
                null
            },
            supportingText = { Text(stringResource(R.string.value_range, from, to)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (input.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { apply() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = { apply() },
            enabled = valid,
            modifier = Modifier.padding(top = 8.dp).size(52.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.value_apply))
        }
    }
}

/** Longest text the value field takes. */
private const val MAX_TYPED = 12

/**
 * A round button that does [onStep] once when tapped and again and again while
 * held (it stops when [onStep] returns false, at a limit), squishing under the
 * finger. Accessibility services see a plain button.
 */
@Composable
fun RepeatingIconButton(
    icon: ImageVector,
    contentDescription: String,
    onStep: () -> Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentStep by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeated = false
        delay(REPEAT_START_MS)
        var gap = REPEAT_FIRST_GAP_MS
        while (true) {
            repeated = true
            if (!currentStep()) break
            delay(gap)
            // Held longer, it goes faster.
            gap = (gap * 0.85f).toLong().coerceAtLeast(REPEAT_FASTEST_MS)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .springPress(interaction, pressedScale = 0.86f)
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.38f))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button) {
                // A hold already stepped: lifting the finger adds nothing.
                if (!repeated) currentStep()
                repeated = false
            }
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) content else content.copy(alpha = 0.38f), modifier = Modifier.size(size * 0.5f))
    }
}

private const val REPEAT_START_MS = 420L
private const val REPEAT_FIRST_GAP_MS = 110L
private const val REPEAT_FASTEST_MS = 35L

/**
 * A whole card that switches [checked]: the badge with [icon] morphs from a circle
 * into a cookie and lights up when on. Tapping anywhere on the card toggles it.
 */
@Composable
fun SwitchCard(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val badge by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "switchBadge",
    )
    val badgeContent by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "switchBadgeContent",
    )
    val toggle: (Boolean) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onCheckedChange(it)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .springPress(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = toggle,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie9Sided,
            morphed = checked,
            color = badge,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = badgeContent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        // The whole card is the switch: this one only shows the state.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

/**
 * A slot for one file: empty, it offers [pickLabel]; filled, it shows the file
 * ([title], [subtitle]) and a button to remove it. The badge morphs when a file
 * arrives.
 */
@Composable
fun FilePickCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    picked: Boolean,
    pickLabel: String,
    removeDescription: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val badge by animateColorAsState(
        targetValue = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "fileBadge",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Square,
            to = MaterialShapes.Cookie12Sided,
            morphed = picked,
            color = badge,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (picked) {
            FilledTonalIconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove()
            }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = removeDescription)
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPick()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(pickLabel, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/**
 * Colours as round swatches; the chosen one morphs into a cookie with a ring
 * and a check. [colors] are ARGB values.
 */
@Composable
fun ColorSwatchRow(
    colors: List<Long>,
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colorLabel = stringResource(R.string.text_color)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        colors.forEach { argb ->
            val chosen = (argb and RGB_MASK) == (selected and RGB_MASK)
            val interaction = remember { MutableInteractionSource() }
            val swatch = Color(argb)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .springPress(interaction, pressedScale = 0.85f)
                    .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(argb)
                    }
                    .semantics {
                        this.selected = chosen
                        contentDescription = colorLabel + " #" + String.format(java.util.Locale.US, "%06X", argb and RGB_MASK)
                    },
            ) {
                MorphingContainer(
                    from = MaterialShapes.Circle,
                    to = MaterialShapes.Cookie7Sided,
                    morphed = chosen,
                    color = swatch,
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (chosen) {
                                Modifier
                            } else {
                                Modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = CircleShape)
                            },
                        ),
                ) {
                    if (chosen) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (luminance(argb) > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val RGB_MASK = 0xFFFFFFL

/** Rough perceived brightness (0..1) of an ARGB colour. */
private fun luminance(argb: Long): Float {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return min(1f, 0.299f * r + 0.587f * g + 0.114f * b)
}
