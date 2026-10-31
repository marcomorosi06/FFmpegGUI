@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.ui.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import java.util.Locale

/*
 * Any colour by its hex code, beside the swatches: a swatch drawn as a colour
 * wheel with a "#", opening a dialog where the code is typed (RGB, "#" or
 * not, three or six digits) and the colour shows live in a big cookie that
 * morphs once the code is valid.
 */

/** The colour (opaque ARGB) of [text], a hex code of 3 or 6 digits, "#" or not; null when it is not one. */
fun parseHexColor(text: String): Long? {
    val digits = text.trim().removePrefix("#")
    if (!digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val six = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("")
        6 -> digits
        else -> return null
    }
    return 0xFF000000L or six.toLong(16)
}

/** [argb] as "RRGGBB". */
fun hexOf(argb: Long): String = String.format(Locale.US, "%06X", argb and 0xFFFFFFL)

/** The swatch that opens the hex dialog: a small colour wheel with a "#". */
@Composable
internal fun HexSwatch(current: Long, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.color_hex)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .springPress(interaction, pressedScale = 0.85f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { open = true }
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            ) {
                Icon(Icons.Rounded.Tag, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    if (open) {
        HexColorDialog(
            initial = current,
            onDismiss = { open = false },
            onPick = {
                open = false
                onPick(it)
            },
        )
    }
}

/** Types a colour by its hex code, shown live; [onPick] with the opaque colour. */
@Composable
fun HexColorDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf(hexOf(initial)) }
    val parsed = parseHexColor(text)
    val shown by animateColorAsState(Color(parsed ?: initial), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hexColour")
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        icon = {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = MaterialShapes.Cookie9Sided,
                morphed = parsed != null,
                color = shown,
                modifier = Modifier.size(88.dp),
            ) {}
        },
        title = { Text(stringResource(R.string.color_hex_title), style = MaterialTheme.typography.headlineSmallEmphasized) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { typed -> text = typed.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase(Locale.US) },
                    prefix = { Text("#") },
                    singleLine = true,
                    isError = text.isNotEmpty() && parsed == null,
                    supportingText = { Text(stringResource(if (text.isNotEmpty() && parsed == null) R.string.color_hex_invalid else R.string.color_hex_hint)) },
                    textStyle = MaterialTheme.typography.titleLargeEmphasized.copy(fontFeatureSettings = "tnum"),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { parsed?.let(onPick) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    parsed?.let(onPick)
                },
                enabled = parsed != null,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.color_hex_use)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
