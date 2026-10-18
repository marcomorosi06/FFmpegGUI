@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup

/** Heading of a group of controls, in the emphasized label style. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

class FFmpegVisualTransformation(
    private val colors: ColorScheme
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)

        val optionRegex = Regex("-[a-zA-Z0-9_:]+")
        val stringRegex = Regex("['\"].*?['\"]")
        val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
        val ffmpegRegex = Regex("^ffmpeg")

        optionRegex.findAll(text.text).forEach {
            builder.addStyle(SpanStyle(color = colors.secondary, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
        }
        stringRegex.findAll(text.text).forEach {
            builder.addStyle(SpanStyle(color = colors.tertiary), it.range.first, it.range.last + 1)
        }
        numberRegex.findAll(text.text).forEach {
            builder.addStyle(SpanStyle(color = colors.primary), it.range.first, it.range.last + 1)
        }
        ffmpegRegex.findAll(text.text).forEach {
            builder.addStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.ExtraBold), it.range.first, it.range.last + 1)
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun TerminalBox(
    text: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val visualTransformation = remember(colors) { FFmpegVisualTransformation(colors) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
        color = colors.surfaceContainerLowest
    ) {
        if (editable && onTextChange != null) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                visualTransformation = visualTransformation,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth().padding(4.dp)
            )
        } else {
            Text(
                text = visualTransformation.filter(AnnotatedString(text)).text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/** Picks one of [options] (their texts); a connected button group. */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectedChoiceGroup(
        options = options,
        selected = selected,
        label = { it },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * [SegmentedSelector] for typed options: [label] supplies the text shown for
 * each one, so callers never have to map display strings back to values.
 */
@Composable
fun <T> ChoiceSelector(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectedChoiceGroup(
        options = options,
        selected = selected,
        label = label,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    startLabel: String = "",
    endLabel: String = "",
    valueLabel: String = "",
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionLabel(label)
            if (valueLabel.isNotBlank()) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            steps = steps,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (startLabel.isNotBlank() || endLabel.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = startLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = endLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}