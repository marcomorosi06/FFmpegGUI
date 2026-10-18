@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.GifDither
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption

/*
 * How a GIF is made: its width, frame rate, colours, dithering, loops and the
 * smaller-file option. The editor's GIF tool and the export sheet show the
 * same controls. The editor's preview plays the plain video, smooth and silent:
 * what these change (size, colours, frame rate) shows in the exported file.
 */

/** Widths offered (0 = as the video); those wider than the video are left out. */
private val GIF_WIDTHS = listOf(240, 320, 480, 640, 0)
private val GIF_RATES = listOf(8, 10, 12, 15, 20, 25)
private val GIF_COLORS = listOf(32, 64, 128, 256)

/** Times a GIF plays: forever, once, twice, three times (see [GifSettings.loops]). */
private val GIF_LOOPS = listOf(0, -1, 1, 2)

@get:StringRes
private val GifDither.labelRes: Int
    get() = when (this) {
        GifDither.SIERRA -> R.string.gif_dither_soft
        GifDither.FLOYD_STEINBERG -> R.string.gif_dither_fine
        GifDither.BAYER -> R.string.gif_dither_pattern
        GifDither.NONE -> R.string.gif_dither_none
    }

private val GifDither.icon
    get() = when (this) {
        GifDither.SIERRA -> Icons.Rounded.BlurOn
        GifDither.FLOYD_STEINBERG -> Icons.Rounded.Grain
        GifDither.BAYER -> Icons.Rounded.GridOn
        GifDither.NONE -> Icons.Rounded.HideImage
    }

/**
 * The GIF's settings. [videoWidth] is the width of the picture before the GIF
 * makes it smaller (0 when unknown); [onGif] gets a key that groups quick
 * changes into one undo step.
 */
@Composable
internal fun GifSettingsContent(
    gif: GifSettings,
    videoWidth: Int,
    onGif: (String?, (GifSettings) -> GifSettings) -> Unit,
    enabled: Boolean = true,
) {
    val widths = GIF_WIDTHS.filter { it == 0 || videoWidth <= 0 || it < videoWidth }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            SectionLabel(stringResource(R.string.gif_width))
            ConnectedChoiceGroup(
                options = widths,
                selected = gif.width.takeIf { it in widths } ?: 0,
                label = { if (it == 0) stringResource(R.string.gif_width_original) else it.toString() },
                onSelect = { width -> onGif(null) { it.copy(width = width) } },
                enabled = enabled,
            )
            SectionLabel(stringResource(R.string.gif_fps))
            ConnectedChoiceGroup(
                options = GIF_RATES,
                selected = GIF_RATES.minBy { kotlin.math.abs(it - gif.fps) },
                label = { it.toString() },
                onSelect = { fps -> onGif(null) { it.copy(fps = fps) } },
                enabled = enabled,
            )
            PanelHint(stringResource(R.string.gif_size_hint))
        }
        PanelCard {
            SectionLabel(stringResource(R.string.gif_colors))
            ConnectedChoiceGroup(
                options = GIF_COLORS,
                selected = GIF_COLORS.minBy { kotlin.math.abs(it - gif.colors) },
                label = { it.toString() },
                onSelect = { colors -> onGif(null) { it.copy(colors = colors) } },
                enabled = enabled,
            )
            SectionLabel(stringResource(R.string.gif_dither))
            OptionTileRow(
                options = GifDither.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
                selected = gif.dither,
                onSelect = { dither -> onGif(null) { it.copy(dither = dither) } },
                enabled = enabled,
            )
            PanelHint(stringResource(R.string.gif_dither_hint))
        }
        PanelCard {
            SectionLabel(stringResource(R.string.gif_loops))
            ConnectedChoiceGroup(
                options = GIF_LOOPS,
                selected = gif.loops.takeIf { it in GIF_LOOPS } ?: 0,
                label = { loops ->
                    when (loops) {
                        0 -> stringResource(R.string.gif_loop_forever)
                        -1 -> stringResource(R.string.gif_loop_once)
                        else -> stringResource(R.string.value_times, loops + 1)
                    }
                },
                onSelect = { loops -> onGif(null) { it.copy(loops = loops) } },
                enabled = enabled,
            )
        }
        SwitchCard(
            icon = Icons.Rounded.Compress,
            title = stringResource(R.string.gif_smaller),
            subtitle = stringResource(R.string.gif_smaller_hint),
            checked = gif.smaller,
            onCheckedChange = { on -> onGif(null) { it.copy(smaller = on) } },
            enabled = enabled,
        )
        PanelHint(stringResource(R.string.gif_no_sound))
    }
}
