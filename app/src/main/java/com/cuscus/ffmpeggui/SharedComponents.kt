package com.cuscus.ffmpeggui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
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
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
        color = colors.surfaceContainerLowest // Effetto terminale pulito Material 3
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

@Composable
fun SegmentedSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(150),
                label = "seg_bg",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(150),
                label = "seg_text",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(
                        when (index) {
                            0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                            options.lastIndex -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    )
                    .background(bgColor)
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
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
                    style = MaterialTheme.typography.labelLarge,
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