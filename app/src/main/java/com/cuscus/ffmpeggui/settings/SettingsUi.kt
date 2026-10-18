@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ToneColors
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily

/*
 * The building blocks of the settings and About pages, in the grouped list
 * style of Material 3 Expressive: the rows of a group touch, very round at
 * the group's ends and barely round between, each with a badge in its own
 * MaterialShapes shape that morphs into a circle under the finger, a spring
 * squish and a haptic tick.
 */

/*
 * The type of the settings: every title in the display face of the app's name
 * (Google Sans Flex, fully rounded, wide), heavier and larger the higher it
 * stands; the explanations under them stay in the body face, for reading.
 */

/** A page's title. */
internal val SettingsPageTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = 42.sp, lineHeight = 46.sp)

/** A group's title, over its rows. */
internal val SettingsGroupTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = 20.sp, lineHeight = 24.sp)

/** A row's or a card's title. */
internal val SettingsRowTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(640), fontSize = 17.sp, lineHeight = 22.sp)

/** Gap between the rows of a group. */
private val ROW_GAP = 3.dp

private val OUTER_RADIUS = 28.dp
private val INNER_RADIUS = 6.dp

/** The shape of row [index] of a group of [count]: round at the group's ends. */
internal fun groupShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(OUTER_RADIUS)
    index == 0 -> RoundedCornerShape(topStart = OUTER_RADIUS, topEnd = OUTER_RADIUS, bottomEnd = INNER_RADIUS, bottomStart = INNER_RADIUS)
    index == count - 1 -> RoundedCornerShape(topStart = INNER_RADIUS, topEnd = INNER_RADIUS, bottomEnd = OUTER_RADIUS, bottomStart = OUTER_RADIUS)
    else -> RoundedCornerShape(INNER_RADIUS)
}

/** Rows that belong together, with an optional [title] above them. */
@Composable
internal fun SettingsGroup(title: String?, rows: List<@Composable (Shape) -> Unit>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = SettingsGroupTitle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp, top = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            rows.forEachIndexed { index, row -> row(groupShape(index, rows.size)) }
        }
    }
}

/**
 * One row: a badge ([icon] in [badge], in [colors]), a [title] and a
 * [summary], something at the end ([trailing]). Pressed it squishes, its badge
 * morphs into a circle and the phone ticks; [onClick] null makes it a plain row.
 */
@Composable
internal fun SettingsRow(
    shape: Shape,
    icon: ImageVector,
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    badge: RoundedPolygon = MaterialShapes.Cookie9Sided,
    colors: ToneColors? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    haptic: HapticFeedbackType = HapticFeedbackType.ContextClick,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tone = colors ?: ToneColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    val alpha = if (enabled) 1f else 0.45f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .springPress(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = role) {
                        haptics.performHapticFeedback(haptic)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        MorphingContainer(
            from = badge,
            to = MaterialShapes.Circle,
            morphed = pressed,
            color = tone.container,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SettingsRowTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                // Never cut: a summary wraps to as many lines as it needs.
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A row that switches [checked]: the whole row toggles, with the toggle's own haptic. */
@Composable
internal fun SettingsSwitchRow(
    shape: Shape,
    icon: ImageVector,
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: RoundedPolygon = MaterialShapes.Cookie9Sided,
    colors: ToneColors? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        shape = shape,
        icon = icon,
        title = title,
        summary = summary,
        badge = badge,
        colors = colors,
        enabled = enabled,
        role = Role.Switch,
        haptic = if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

/**
 * The top of a settings page: back, then the page's badge ([icon] in [shape]
 * and [colors]) and its [title] set large in the display face (Google Sans
 * Flex, rounded and wide, as the app's name), and what the page holds. Without
 * a title only back shows (the About page has its own hero).
 */
@Composable
internal fun SettingsHeader(
    title: String?,
    onBack: () -> Unit,
    icon: ImageVector? = null,
    shape: RoundedPolygon = MaterialShapes.Cookie12Sided,
    colors: ToneColors? = null,
    subtitle: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        FilledTonalIconButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onBack()
        }) {
            Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = stringResource(R.string.action_back), modifier = Modifier.size(20.dp))
        }
        if (title == null) return@Column
        Spacer(modifier = Modifier.height(28.dp))
        if (icon != null) {
            val tone = colors ?: ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            MorphingContainer(from = shape, to = shape, morphed = false, color = tone.container, modifier = Modifier.padding(start = 4.dp).size(56.dp)) {
                Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = SettingsPageTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** A round dot of [color], for palette previews. */
@Composable
internal fun ColorDot(color: Color, size: Dp) {
    val shown by animateColorAsState(color, MaterialTheme.motionScheme.slowEffectsSpec(), label = "dot")
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(shown),
    )
}

/** Opens [url] in the browser; a toast when nothing can. The app itself never goes online. */
internal fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.settings_link_failed), Toast.LENGTH_SHORT).show()
    }
}

/** Where the project lives. */
internal object ProjectLinks {
    const val GITHUB = "https://github.com/marcomorosi06/FFmpegGUI"
    const val KOFI = "https://ko-fi.com/marcomorosi"
    const val FFMPEG_LEGAL = "https://ffmpeg.org/legal.html"
    const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"
    const val MIT = "https://opensource.org/license/mit"
    const val CC_BY = "https://creativecommons.org/licenses/by/4.0/"
    const val ANTI_VOCALE = "https://github.com/RisorseArtificiali/anti-vocale"
    const val SHERPA_ONNX = "https://github.com/k2-fsa/sherpa-onnx"
}
