@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import java.util.Locale

/*
 * How formats look wherever they appear (the format picker, the export sheet,
 * the processing screen): every category has a MaterialShapes shape and a
 * colour pair of its own, and a format shows as its extension set in that
 * shape, so that "MP4" looks the same from the moment it is chosen to the
 * moment the file is made.
 */

/** The shape of a category's badges. */
internal fun categoryShape(category: FormatCategory): RoundedPolygon = when (category) {
    FormatCategory.VIDEO -> MaterialShapes.Cookie9Sided
    FormatCategory.ANIMATION -> MaterialShapes.Flower
    FormatCategory.AUDIO -> MaterialShapes.SoftBurst
    FormatCategory.IMAGE -> MaterialShapes.Clover4Leaf
    FormatCategory.TEXT -> MaterialShapes.Cookie12Sided
    FormatCategory.CUSTOM -> MaterialShapes.Gem
}

/** A category's icon. */
internal fun categoryIcon(category: FormatCategory): ImageVector = when (category) {
    FormatCategory.VIDEO -> Icons.Rounded.Movie
    FormatCategory.ANIMATION -> Icons.Rounded.Gif
    FormatCategory.AUDIO -> Icons.Rounded.Audiotrack
    FormatCategory.IMAGE -> Icons.Rounded.Photo
    FormatCategory.TEXT -> Icons.Rounded.Notes
    FormatCategory.CUSTOM -> Icons.Rounded.Terminal
}

/** A container colour and the colour on it. */
internal class ToneColors(val container: Color, val content: Color)

/** The colours of a category's badges, from the theme's roles. */
@Composable
internal fun categoryColors(category: FormatCategory): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        FormatCategory.VIDEO -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        FormatCategory.ANIMATION -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        FormatCategory.AUDIO -> ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        FormatCategory.IMAGE -> ToneColors(scheme.tertiary, scheme.onTertiary)
        FormatCategory.TEXT -> ToneColors(scheme.secondary, scheme.onSecondary)
        FormatCategory.CUSTOM -> ToneColors(scheme.inverseSurface, scheme.inverseOnSurface)
    }
}

/** What a category holds, in a few words. */
@StringRes
internal fun categoryHintRes(category: FormatCategory): Int = when (category) {
    FormatCategory.VIDEO -> R.string.format_category_video_hint
    FormatCategory.ANIMATION -> R.string.format_category_animation_hint
    FormatCategory.AUDIO -> R.string.format_category_audio_hint
    FormatCategory.IMAGE -> R.string.format_category_image_hint
    FormatCategory.TEXT -> R.string.format_category_text_hint
    FormatCategory.CUSTOM -> R.string.format_category_custom_hint
}

/** What a format is good for, in a few words. */
@StringRes
internal fun formatDescriptionRes(format: OutputFormat): Int = when (format) {
    OutputFormat.MP4 -> R.string.format_desc_mp4
    OutputFormat.MKV -> R.string.format_desc_mkv
    OutputFormat.AVI -> R.string.format_desc_avi
    OutputFormat.GIF -> R.string.format_desc_gif
    OutputFormat.MP3 -> R.string.format_desc_mp3
    OutputFormat.M4A -> R.string.format_desc_m4a
    OutputFormat.OGG -> R.string.format_desc_ogg
    OutputFormat.OPUS -> R.string.format_desc_opus
    OutputFormat.FLAC -> R.string.format_desc_flac
    OutputFormat.WAV -> R.string.format_desc_wav
    OutputFormat.JPG -> R.string.format_desc_jpg
    OutputFormat.PNG -> R.string.format_desc_png
    OutputFormat.WEBP -> R.string.format_desc_webp
    OutputFormat.BMP -> R.string.format_desc_bmp
    OutputFormat.TIFF -> R.string.format_desc_tiff
    OutputFormat.TXT -> R.string.format_desc_txt
    OutputFormat.SRT -> R.string.format_desc_srt
    OutputFormat.CUSTOM -> R.string.format_desc_custom
}

/** The short name a format goes by: its extension in capitals ("MP4"), or the custom format's name. */
@Composable
internal fun formatShortName(format: OutputFormat): String =
    if (format.extension.isEmpty()) stringResource(format.labelRes) else format.extension.uppercase(Locale.ROOT)

/**
 * A format as a badge: its extension (an icon for the custom format) set in its
 * category's shape and colours. [morphed] eases the shape into a circle (under
 * the finger, say), with the theme's spatial spring.
 */
@Composable
internal fun FormatBadge(
    format: OutputFormat,
    size: Dp,
    modifier: Modifier = Modifier,
    morphed: Boolean = false,
) {
    val colors = categoryColors(format.category)
    MorphingContainer(
        from = categoryShape(format.category),
        to = MaterialShapes.Circle,
        morphed = morphed,
        color = colors.container,
        modifier = modifier.size(size),
    ) {
        if (format.extension.isEmpty()) {
            Icon(categoryIcon(format.category), contentDescription = null, tint = colors.content, modifier = Modifier.size(size * 0.42f))
        } else {
            Text(
                text = format.extension.uppercase(Locale.ROOT),
                style = badgeTextStyle(size),
                color = colors.content,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** The type of a badge's extension, by the badge's size: four letters always fit. */
@Composable
private fun badgeTextStyle(size: Dp): TextStyle {
    val type = MaterialTheme.typography
    return when {
        size >= 88.dp -> type.headlineSmallEmphasized
        size >= 72.dp -> type.titleLargeEmphasized
        size >= 56.dp -> type.titleMediumEmphasized
        size >= 44.dp -> type.labelLargeEmphasized
        else -> type.labelMediumEmphasized
    }
}
