@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.layout.widthIn
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.WbShade
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * The bottom of the editor: a row of tools that changes with the selection,
 * and the panel a tool opens in place of the timeline. The preview above keeps
 * its size whatever is open down here.
 */

/**
 * What a tool works on. Each family has its own Material tone, used everywhere
 * the family shows: the tool's icon in the tool row, its panel (badge, chosen
 * options, sliders, switches) and its lane on the timeline. So the colour tells
 * what something is: picture and timing in the primary tone, sound in the
 * tertiary tone, things laid over the picture in the secondary tone.
 */
enum class ToolFamily { PICTURE, SOUND, OVERLAY, OUTPUT }

/** Tools that open a panel. */
enum class EditorTool(@StringRes val labelRes: Int, val icon: ImageVector, val family: ToolFamily) {
    TRIM_ALL(R.string.tool_trim, Icons.Rounded.Straighten, ToolFamily.PICTURE),
    CLIP_TRIM(R.string.tool_trim, Icons.Rounded.Straighten, ToolFamily.PICTURE),
    AUDIO(R.string.tool_audio, Icons.Rounded.GraphicEq, ToolFamily.SOUND),
    VOLUME(R.string.tool_volume, Icons.Rounded.VolumeUp, ToolFamily.SOUND),
    // Tools of a selected sound.
    SOUND_TRIM(R.string.tool_trim, Icons.Rounded.Straighten, ToolFamily.SOUND),
    SOUND_VOLUME(R.string.tool_volume, Icons.Rounded.VolumeUp, ToolFamily.SOUND),
    SOUND_SPEED(R.string.tool_speed, Icons.Rounded.Speed, ToolFamily.SOUND),
    SOUND_VOICE(R.string.tool_voice, Icons.Rounded.RecordVoiceOver, ToolFamily.SOUND),
    SOUND_FADE(R.string.tool_fade, Icons.Rounded.Gradient, ToolFamily.SOUND),
    SOUND_EQ(R.string.tool_equalizer, Icons.Rounded.Equalizer, ToolFamily.SOUND),
    SOUND_REPEAT(R.string.tool_repeat, Icons.Rounded.Repeat, ToolFamily.SOUND),
    // Tools of a selected text or picture.
    TEXT_EDIT(R.string.tool_text_edit, Icons.Rounded.Keyboard, ToolFamily.OVERLAY),
    TEXT_STYLE(R.string.tool_style, Icons.Rounded.FormatColorText, ToolFamily.OVERLAY),
    OVERLAY_LAYOUT(R.string.tool_position, Icons.Rounded.OpenWith, ToolFamily.OVERLAY),
    IMAGE_OPACITY(R.string.tool_opacity, Icons.Rounded.Opacity, ToolFamily.OVERLAY),
    OVERLAY_FADE(R.string.tool_fade, Icons.Rounded.Gradient, ToolFamily.OVERLAY),
    OVERLAY_TIMING(R.string.tool_timing, Icons.Rounded.Schedule, ToolFamily.OVERLAY),
    OVERLAY_ROTATE(R.string.tool_rotate, Icons.Rounded.RotateRight, ToolFamily.OVERLAY),
    OVERLAY_ADJUST(R.string.tool_adjust, Icons.Rounded.Tune, ToolFamily.OVERLAY),
    OVERLAY_KEY(R.string.tool_key, Icons.Rounded.Colorize, ToolFamily.OVERLAY),
    OVERLAY_VOLUME(R.string.tool_volume, Icons.Rounded.VolumeUp, ToolFamily.OVERLAY),
    OVERLAY_CROP(R.string.tool_overlay_crop, Icons.Rounded.Crop, ToolFamily.OVERLAY),
    OVERLAY_BORDER(R.string.tool_border, Icons.Rounded.BorderOuter, ToolFamily.OVERLAY),
    OVERLAY_SHADOW(R.string.tool_shadow, Icons.Rounded.WbShade, ToolFamily.OVERLAY),
    OVERLAY_ANIMATE(R.string.tool_animate, Icons.Rounded.Animation, ToolFamily.OVERLAY),
    // How the keyframe at the playhead moves on to the next (opened from the control row).
    EASING(R.string.tool_easing, Icons.Rounded.Gesture, ToolFamily.OVERLAY),
    // Tools of an audio file's final sound.
    AUDIO_SILENCE(R.string.tool_silence, Icons.Rounded.MusicOff, ToolFamily.SOUND),
    AUDIO_PITCH(R.string.tool_pitch, Icons.Rounded.Piano, ToolFamily.SOUND),
    AUDIO_EQ(R.string.tool_equalizer, Icons.Rounded.Equalizer, ToolFamily.SOUND),
    AUDIO_CLEAN(R.string.tool_clean, Icons.Rounded.CleaningServices, ToolFamily.SOUND),
    AUDIO_DYNAMICS(R.string.tool_dynamics, Icons.Rounded.Compress, ToolFamily.SOUND),
    AUDIO_SPACE(R.string.tool_space, Icons.Rounded.SurroundSound, ToolFamily.SOUND),
    AUDIO_FX(R.string.tool_effects, Icons.Rounded.AutoAwesome, ToolFamily.SOUND),
    AUDIO_STEREO(R.string.tool_stereo, Icons.Rounded.Headphones, ToolFamily.SOUND),
    AUDIO_TAGS(R.string.tool_tags, Icons.Rounded.Sell, ToolFamily.OUTPUT),
    AUDIO_COVER(R.string.tool_cover, Icons.Rounded.Album, ToolFamily.OUTPUT),
    AUDIO_PARTS(R.string.tool_parts, Icons.Rounded.ViewWeek, ToolFamily.OUTPUT),
    // Tool of a selected still clip.
    MOTION(R.string.tool_motion, Icons.Rounded.ZoomIn, ToolFamily.PICTURE),
    COLOR(R.string.tool_color, Icons.Rounded.Palette, ToolFamily.PICTURE),
    KEY(R.string.tool_key, Icons.Rounded.Colorize, ToolFamily.PICTURE),
    // The HDR look of the whole video.
    HDR(R.string.tool_hdr, Icons.Rounded.HdrOn, ToolFamily.PICTURE),
    TRANSFORM(R.string.tool_transform, Icons.Rounded.CropRotate, ToolFamily.PICTURE),
    // Tool of a selected clip: zoom and pan, with keyframes a move over time.
    FRAMING(R.string.tool_framing, Icons.Rounded.ZoomOutMap, ToolFamily.PICTURE),
    SPEED(R.string.tool_speed, Icons.Rounded.Speed, ToolFamily.PICTURE),
    FADE(R.string.tool_fade, Icons.Rounded.Gradient, ToolFamily.PICTURE),
    // How a clip gives way to the next one.
    TRANSITION(R.string.tool_transition, Icons.Rounded.AutoAwesomeMotion, ToolFamily.PICTURE),
    CANVAS(R.string.tool_canvas, Icons.Rounded.AspectRatio, ToolFamily.PICTURE),
    SUBTITLE(R.string.tool_subtitle, Icons.Rounded.ClosedCaption, ToolFamily.OVERLAY),
    EXTRACT(R.string.tool_extract, Icons.Rounded.PhotoCamera, ToolFamily.OUTPUT),
    REMUX(R.string.tool_remux, Icons.Rounded.Sync, ToolFamily.OUTPUT),
    COMMAND(R.string.tool_command, Icons.Rounded.Terminal, ToolFamily.OUTPUT),
    GIF(R.string.tool_gif, Icons.Rounded.Gif, ToolFamily.OUTPUT),
}

/** The accent of [family]: its main tone, with the tones that go on it. */
private class FamilyTones(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)

@Composable
private fun tonesOf(family: ToolFamily): FamilyTones {
    val c = MaterialTheme.colorScheme
    return when (family) {
        ToolFamily.PICTURE -> FamilyTones(c.primary, c.onPrimary, c.primaryContainer, c.onPrimaryContainer)
        ToolFamily.SOUND -> FamilyTones(c.tertiary, c.onTertiary, c.tertiaryContainer, c.onTertiaryContainer)
        ToolFamily.OVERLAY -> FamilyTones(c.secondary, c.onSecondary, c.secondaryContainer, c.onSecondaryContainer)
        // Output settings are neutral: they change the file, not what is seen or heard.
        ToolFamily.OUTPUT -> FamilyTones(c.onSurface, c.surface, c.surfaceContainerHighest, c.onSurface)
    }
}

/** The colour that stands for [family], e.g. for its icon. */
@Composable
fun familyColor(family: ToolFamily): Color = tonesOf(family).accent

/**
 * [content] in the tones of [family]: the theme's primary roles take the
 * family's tone, so every component inside (sliders, switches, chosen tiles,
 * buttons) speaks the family's colour without being told.
 */
@Composable
fun ToolFamilyTheme(family: ToolFamily, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val tones = tonesOf(family)
    val scheme = remember(base, family) {
        base.copy(
            primary = tones.accent,
            onPrimary = tones.onAccent,
            primaryContainer = tones.container,
            onPrimaryContainer = tones.onContainer,
        )
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** One button of the tool row: opens a panel or runs an action straight away. */
class ToolbarItem(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val family: ToolFamily = ToolFamily.PICTURE,
    val onClick: () -> Unit,
)

/** Height of the tool row. */
val TOOLBAR_HEIGHT = 72.dp

/** Height of the header of a tool panel. */
val PANEL_HEADER_HEIGHT = 56.dp

/** The widest a panel's content gets (a panel across a tablet or a monitor). */
val PANEL_CONTENT_MAX_WIDTH = 720.dp

/**
 * The tool row. With [onBack] (a clip is selected) a back button leads, which
 * returns to the tools of the whole video.
 */
@Composable
fun EditorToolbar(
    items: List<ToolbarItem>,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                ToolButton(
                    labelRes = R.string.tool_back,
                    icon = Icons.Rounded.KeyboardDoubleArrowLeft,
                    enabled = true,
                    highlighted = true,
                    onClick = onBack,
                )
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items) { item ->
                    ToolButton(
                        labelRes = item.labelRes,
                        icon = item.icon,
                        enabled = item.enabled,
                        highlighted = false,
                        family = item.family,
                        onClick = item.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    @StringRes labelRes: Int,
    icon: ImageVector,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    family: ToolFamily = ToolFamily.PICTURE,
) {
    val haptic = LocalHapticFeedback.current
    val tones = tonesOf(family)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The indicator pill behind the icon grows out of it under the finger, with
    // the theme's springy motion (the expressive navigation-bar indicator).
    val indicator by animateFloatAsState(
        targetValue = if (pressed || highlighted) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toolIndicator",
    )
    // The pill is the tool's family container: pressing a sound tool lights up in
    // the sound tone, and so on.
    val indicatorColor = tones.container
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 56.dp, height = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = indicator
                        alpha = indicator.coerceIn(0f, 1f)
                    }
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                // At rest the icon wears its family's tone, so the row reads by colour.
                tint = if (highlighted || pressed) tones.onContainer else tones.accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(labelRes),
            style = if (highlighted) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The panel of an open tool, in place of the timeline and the tool row: a
 * sheet with large rounded top corners. [scope] says what it changes ("Whole
 * video", "Clip 2"); [onDone] closes it.
 */
@Composable
fun ToolPanel(
    title: String,
    scope: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** Told the full height of the content, in pixels, so the editor can make room for it. */
    onContentHeight: ((Int) -> Unit)? = null,
    /** Whose tone the panel speaks (see [ToolFamily]). */
    family: ToolFamily = ToolFamily.PICTURE,
    /** Every corner rounded: a panel beside the preview rather than rising from the bottom. */
    rounded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) = ToolFamilyTheme(family) {
    // The tool's badge morphs from a circle into a cookie as the panel opens.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(if (rounded) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(PANEL_HEADER_HEIGHT)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            if (icon != null) {
                MorphingContainer(
                    from = MaterialShapes.Circle,
                    to = MaterialShapes.Cookie9Sided,
                    morphed = opened,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            // Title and scope chip share the free space; the title gives way first.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = scope,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.panel_done),
                )
            }
        }
        // On a wide screen the content keeps a readable width, in the middle.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Measured in a column of its own: a child of a column gets no minimum
            // height, so this is the content's own height. (Measured right under the
            // scroll it would also be at least the panel's height, and the panel,
            // growing to fit it, would grow without end.)
            Column(
                modifier = Modifier
                    .widthIn(max = PANEL_CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .onSizeChanged { onContentHeight?.invoke(it.height) }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                content = content,
            )
        }
    }
}

/** One action of the floating clip toolbar. */
class ClipAction(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * The actions on the selected clip as such (split, duplicate, move, delete), in a
 * floating toolbar over the bottom of the preview: Material 3 Expressive's
 * contextual toolbar. The tools that edit the clip stay in the tool row.
 */
@Composable
fun ClipActionsToolbar(
    actions: List<ClipAction>,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    HorizontalFloatingToolbar(
        expanded = true,
        // The clip's own actions: the picture family's vibrant tone.
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier,
    ) {
        actions.forEach { action ->
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    action.onClick()
                },
                enabled = action.enabled,
            ) {
                Icon(imageVector = action.icon, contentDescription = stringResource(action.labelRes))
            }
        }
    }
}
