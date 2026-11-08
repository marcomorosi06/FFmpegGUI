package com.cuscus.ffmpeggui.textart

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.ScatterPlot
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.textart.ArtColor
import com.cuscus.ffmpeggui.core.textart.ArtEncoding
import com.cuscus.ffmpeggui.core.textart.ArtMode
import com.cuscus.ffmpeggui.core.textart.ArtWarning
import com.cuscus.ffmpeggui.core.textart.BrightMode
import com.cuscus.ffmpeggui.core.textart.ColorLayer
import com.cuscus.ffmpeggui.core.textart.ColorMatch
import com.cuscus.ffmpeggui.core.textart.Dither
import com.cuscus.ffmpeggui.core.textart.DitherTarget
import com.cuscus.ffmpeggui.core.textart.FrameStyle
import com.cuscus.ffmpeggui.core.textart.GlyphFilter
import com.cuscus.ffmpeggui.core.textart.GlyphGroup
import com.cuscus.ffmpeggui.core.textart.LineEnding
import com.cuscus.ffmpeggui.core.textart.Paper
import com.cuscus.ffmpeggui.core.textart.TerminalTheme
import com.cuscus.ffmpeggui.core.textart.TextTarget

/*
 * Names, icons and shapes of the text-art editor's choices: the :core enums
 * carry no text, this ties them to string resources.
 */

/** The tools of the text-art editor, each with its panel. */
internal enum class TextArtTool(@StringRes val labelRes: Int, val icon: ImageVector) {
    PICTURE(R.string.textart_tool_picture, Icons.Rounded.Crop),
    STYLE(R.string.textart_tool_style, Icons.Rounded.AutoAwesome),
    ALPHABET(R.string.textart_tool_alphabet, Icons.Rounded.TextFields),
    LIGHT(R.string.textart_tool_light, Icons.Rounded.Contrast),
    COLORS(R.string.textart_tool_colors, Icons.Rounded.Palette),
    HIDE(R.string.textart_tool_hide, Icons.Rounded.VisibilityOff),
    LAYOUT(R.string.textart_tool_layout, Icons.Rounded.BorderOuter),
    CHECK(R.string.textart_tool_check, Icons.Rounded.Verified),
}

/** The shape a tool's badge turns into when it is open: each tool its own. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val TextArtTool.shape: RoundedPolygon
    get() = when (this) {
        TextArtTool.PICTURE -> MaterialShapes.Square
        TextArtTool.STYLE -> MaterialShapes.Sunny
        TextArtTool.ALPHABET -> MaterialShapes.Cookie9Sided
        TextArtTool.LIGHT -> MaterialShapes.SoftBurst
        TextArtTool.COLORS -> MaterialShapes.Flower
        TextArtTool.HIDE -> MaterialShapes.Ghostish
        TextArtTool.LAYOUT -> MaterialShapes.Slanted
        TextArtTool.CHECK -> MaterialShapes.Clover4Leaf
    }

/** How the stage shows the art. */
internal enum class ArtView(@StringRes val labelRes: Int) {
    ART(R.string.textart_view_art),
    OVER(R.string.textart_view_over),
    COMPARE(R.string.textart_view_compare),
}

@get:StringRes
internal val ArtMode.labelRes: Int
    get() = when (this) {
        ArtMode.RAMP -> R.string.textart_mode_ramp
        ArtMode.SHAPE -> R.string.textart_mode_shape
        ArtMode.EDGES -> R.string.textart_mode_edges
        ArtMode.BLOCKS -> R.string.textart_mode_blocks
        ArtMode.HALF_BLOCKS -> R.string.textart_mode_half
        ArtMode.QUADRANTS -> R.string.textart_mode_quadrants
        ArtMode.BRAILLE -> R.string.textart_mode_braille
        ArtMode.TEXT_FILL -> R.string.textart_mode_text
    }

@get:StringRes
internal val ArtMode.hintRes: Int
    get() = when (this) {
        ArtMode.RAMP -> R.string.textart_mode_ramp_hint
        ArtMode.SHAPE -> R.string.textart_mode_shape_hint
        ArtMode.EDGES -> R.string.textart_mode_edges_hint
        ArtMode.BLOCKS -> R.string.textart_mode_blocks_hint
        ArtMode.HALF_BLOCKS -> R.string.textart_mode_half_hint
        ArtMode.QUADRANTS -> R.string.textart_mode_quadrants_hint
        ArtMode.BRAILLE -> R.string.textart_mode_braille_hint
        ArtMode.TEXT_FILL -> R.string.textart_mode_text_hint
    }

internal val ArtMode.icon: ImageVector
    get() = when (this) {
        ArtMode.RAMP -> Icons.Rounded.LinearScale
        ArtMode.SHAPE -> Icons.Rounded.Interests
        ArtMode.EDGES -> Icons.Rounded.Polyline
        ArtMode.BLOCKS -> Icons.Rounded.GridView
        ArtMode.HALF_BLOCKS -> Icons.Rounded.ViewWeek
        ArtMode.QUADRANTS -> Icons.Rounded.GridOn
        ArtMode.BRAILLE -> Icons.Rounded.ScatterPlot
        ArtMode.TEXT_FILL -> Icons.Rounded.Notes
    }

@get:StringRes
internal val Paper.labelRes: Int
    get() = when (this) {
        Paper.DARK -> R.string.textart_paper_dark
        Paper.LIGHT -> R.string.textart_paper_light
    }

@get:StringRes
internal val ArtColor.labelRes: Int
    get() = when (this) {
        ArtColor.NONE -> R.string.textart_depth_none
        ArtColor.ANSI_16 -> R.string.textart_depth_16
        ArtColor.ANSI_256 -> R.string.textart_depth_256
        ArtColor.TRUECOLOR -> R.string.textart_depth_true
    }

@get:StringRes
internal val ColorLayer.labelRes: Int
    get() = when (this) {
        ColorLayer.FOREGROUND -> R.string.textart_layer_fg
        ColorLayer.FOREGROUND_AND_BACKGROUND -> R.string.textart_layer_both
    }

@get:StringRes
internal val ColorMatch.labelRes: Int
    get() = when (this) {
        ColorMatch.OKLAB -> R.string.textart_match_oklab
        ColorMatch.CIEDE2000 -> R.string.textart_match_ciede
        ColorMatch.RGB -> R.string.textart_match_rgb
    }

@get:StringRes
internal val Dither.labelRes: Int
    get() = when (this) {
        Dither.NONE -> R.string.textart_dither_none
        Dither.BAYER_4 -> R.string.textart_dither_bayer4
        Dither.BAYER_8 -> R.string.textart_dither_bayer8
        Dither.FLOYD_STEINBERG -> R.string.textart_dither_fs
        Dither.ATKINSON -> R.string.textart_dither_atkinson
    }

@get:StringRes
internal val DitherTarget.labelRes: Int
    get() = when (this) {
        DitherTarget.GLYPHS -> R.string.textart_dither_glyphs
        DitherTarget.COLORS -> R.string.textart_dither_colors
        DitherTarget.BOTH -> R.string.textart_dither_both
    }

@get:StringRes
internal val TerminalTheme.labelRes: Int
    get() = when (this) {
        TerminalTheme.VGA -> R.string.textart_theme_vga
        TerminalTheme.XTERM -> R.string.textart_theme_xterm
        TerminalTheme.WINDOWS -> R.string.textart_theme_windows
        TerminalTheme.TANGO -> R.string.textart_theme_tango
    }

@get:StringRes
internal val BrightMode.labelRes: Int
    get() = when (this) {
        BrightMode.BOLD -> R.string.textart_bright_bold
        BrightMode.AIXTERM -> R.string.textart_bright_aixterm
    }

@get:StringRes
internal val ArtEncoding.labelRes: Int
    get() = when (this) {
        ArtEncoding.UTF8 -> R.string.textart_encoding_utf8
        ArtEncoding.CP437 -> R.string.textart_encoding_cp437
    }

@get:StringRes
internal val LineEnding.labelRes: Int
    get() = when (this) {
        LineEnding.LF -> R.string.textart_line_lf
        LineEnding.CRLF -> R.string.textart_line_crlf
    }

@get:StringRes
internal val FrameStyle.labelRes: Int
    get() = when (this) {
        FrameStyle.NONE -> R.string.textart_frame_none
        FrameStyle.ASCII -> R.string.textart_frame_ascii
        FrameStyle.SINGLE -> R.string.textart_frame_single
        FrameStyle.DOUBLE -> R.string.textart_frame_double
        FrameStyle.ROUNDED -> R.string.textart_frame_rounded
        FrameStyle.HEAVY -> R.string.textart_frame_heavy
    }

@get:StringRes
internal val GlyphGroup.labelRes: Int
    get() = when (this) {
        GlyphGroup.UPPER -> R.string.textart_group_upper
        GlyphGroup.LOWER -> R.string.textart_group_lower
        GlyphGroup.DIGITS -> R.string.textart_group_digits
        GlyphGroup.PUNCTUATION -> R.string.textart_group_punctuation
        GlyphGroup.SYMBOLS -> R.string.textart_group_symbols
        GlyphGroup.BOX -> R.string.textart_group_box
        GlyphGroup.SHADES -> R.string.textart_group_shades
        GlyphGroup.GEOMETRIC -> R.string.textart_group_geometric
    }

@get:StringRes
internal val GlyphFilter.labelRes: Int
    get() = when (this) {
        GlyphFilter.ASCII_ONLY -> R.string.textart_filter_ascii
        GlyphFilter.MARKDOWN_SAFE -> R.string.textart_filter_markdown
        GlyphFilter.COMMENT_SAFE -> R.string.textart_filter_comment
        GlyphFilter.NO_LOOKALIKES -> R.string.textart_filter_lookalikes
        GlyphFilter.SAFE_WIDTH -> R.string.textart_filter_width
    }

@get:StringRes
internal val GlyphFilter.hintRes: Int
    get() = when (this) {
        GlyphFilter.ASCII_ONLY -> R.string.textart_filter_ascii_hint
        GlyphFilter.MARKDOWN_SAFE -> R.string.textart_filter_markdown_hint
        GlyphFilter.COMMENT_SAFE -> R.string.textart_filter_comment_hint
        GlyphFilter.NO_LOOKALIKES -> R.string.textart_filter_lookalikes_hint
        GlyphFilter.SAFE_WIDTH -> R.string.textart_filter_width_hint
    }

@get:StringRes
internal val TextTarget.labelRes: Int
    get() = when (this) {
        TextTarget.TERMINAL -> R.string.textart_target_terminal
        TextTarget.EDITOR -> R.string.textart_target_editor
        TextTarget.MARKDOWN -> R.string.textart_target_markdown
        TextTarget.CHAT -> R.string.textart_target_chat
        TextTarget.BBS -> R.string.textart_target_bbs
    }

@get:StringRes
internal val ArtWarning.labelRes: Int
    get() = when (this) {
        ArtWarning.NON_ASCII -> R.string.textart_warning_non_ascii
        ArtWarning.MARKDOWN_CHARS -> R.string.textart_warning_markdown
        ArtWarning.AMBIGUOUS_WIDTH -> R.string.textart_warning_width
        ArtWarning.MISSING_GLYPHS -> R.string.textart_warning_missing
        ArtWarning.UNENCODABLE -> R.string.textart_warning_unencodable
        ArtWarning.TOO_WIDE -> R.string.textart_warning_too_wide
        ArtWarning.EMPTY -> R.string.textart_warning_empty
    }

/** The ready-made alphabets of the alphabet panel. */
internal enum class AlphabetPreset(@StringRes val labelRes: Int, val sample: String) {
    CLASSIC(R.string.textart_preset_classic, "@%#*"),
    DETAILED(R.string.textart_preset_detailed, "$@B8"),
    ALL_ASCII(R.string.textart_preset_all, "Aa1#"),
    LETTERS(R.string.textart_preset_letters, "MWab"),
    DIGITS(R.string.textart_preset_digits, "8096"),
    BINARY(R.string.textart_preset_binary, "0101"),
    DOTS(R.string.textart_preset_dots, ".:oO"),
    CODE(R.string.textart_preset_code, "{}<>"),
}
