package com.cuscus.ffmpeggui.core.textart

/*
 * How a picture becomes text: ASCII art (OutputFormat.ASCII, a .txt) or ANSI
 * art (OutputFormat.ANSI, a .ans with colours). Every choice of the text-art
 * editor lives here, kept in projects (core.project.ModelJson) and carried to
 * the export in ImageEdit.textArt. The engine is TextArtRenderer, the files are
 * written by TextArtWriter.
 *
 * These enums carry no display text: the app names them (app/.../textart/).
 */

/** How the cells are drawn. */
enum class ArtMode {
    /** One character a cell, picked by how much ink it has (a density ramp). */
    RAMP,

    /** The character whose shape is closest to the cell's (its ink measured in sub-cells). */
    SHAPE,

    /** Lines drawn along the edges (- / | \), the rest from the ramp or empty. */
    EDGES,

    /** Shades: space and the four shade blocks. */
    BLOCKS,

    /** Two pixels a cell, one above the other (half blocks). */
    HALF_BLOCKS,

    /** Four pixels a cell, two by two (quadrant blocks). */
    QUADRANTS,

    /** Eight dots a cell, two by four (Braille patterns). */
    BRAILLE,

    /** The picture written with the letters of a phrase, over and over. */
    TEXT_FILL,
}

/** What the text is read on: light ink on a dark page (terminals) or dark ink on a light one (editors). */
enum class Paper { DARK, LIGHT }

/** Colours an ANSI file can hold. NONE: plain characters (what an ASCII file always is). */
enum class ArtColor { NONE, ANSI_16, ANSI_256, TRUECOLOR }

/** What carries the colour of a cell. */
enum class ColorLayer {
    /** The characters only, on the terminal's own background. */
    FOREGROUND,

    /** The characters and the cell behind them, both chosen for the cell. */
    FOREGROUND_AND_BACKGROUND,
}

/** How a colour is matched to the nearest one the palette has. */
enum class ColorMatch { OKLAB, CIEDE2000, RGB }

/** How the error of a too-coarse choice is spread, so that a smooth tone stays smooth. */
enum class Dither { NONE, BAYER_4, BAYER_8, FLOYD_STEINBERG, ATKINSON }

/** What the dithering works on. */
enum class DitherTarget { GLYPHS, COLORS, BOTH }

/**
 * The sixteen colours of a terminal theme: a 16-colour (or the first sixteen
 * of a 256-colour) file looks as the reader's theme paints it, so the editor
 * matches the picture to the theme chosen here and shows it so.
 */
enum class TerminalTheme { VGA, XTERM, WINDOWS, TANGO }

/** How the bright colours (8 to 15) are asked for. */
enum class BrightMode {
    /** Bold with the normal colour (SGR 1;30-37), as every old terminal and BBS viewer reads it. */
    BOLD,

    /** The bright codes (SGR 90-97 and 100-107), as modern terminals read them. */
    AIXTERM,
}

/** How the bytes of an ANSI file are written. */
enum class ArtEncoding {
    /** UTF-8, for today's terminals (`cat file.ans`): every character, 256 colours and true colour. */
    UTF8,

    /** IBM code page 437, as the ANSI art scene's files are, with a SAUCE record: 16 colours. */
    CP437,
}

enum class LineEnding(val text: String) { LF("\n"), CRLF("\r\n") }

/** A frame drawn around the art. */
enum class FrameStyle { NONE, ASCII, SINGLE, DOUBLE, ROUNDED, HEAVY }

/** Groups of characters the alphabet can take whole. */
enum class GlyphGroup { UPPER, LOWER, DIGITS, PUNCTUATION, SYMBOLS, BOX, SHADES, GEOMETRIC }

/** Characters taken out for where the text is going (see Glyphs.passes). */
enum class GlyphFilter {
    /** Printable ASCII only (32 to 126). */
    ASCII_ONLY,

    /** Nothing Markdown or a chat would read as formatting: ` * _ ~ | # > < [ ] \ */
    MARKDOWN_SAFE,

    /** Never a star nor a backslash, so the art can sit in a code comment (it can neither close it nor run on to the next line). */
    COMMENT_SAFE,

    /** None of l 1 I | O 0, which fonts draw alike. */
    NO_LOOKALIKES,

    /** Nothing that an East Asian terminal may draw two columns wide (boxes, blocks, shapes, Greek). */
    SAFE_WIDTH,
}

/** Where the art is going: each sets the size, paper, alphabet and file layout that suit it (TextArtPresets). */
enum class TextTarget { TERMINAL, EDITOR, MARKDOWN, CHAT, BBS }

/** The characters the art may use. */
data class Alphabet(
    val groups: Set<GlyphGroup> = emptySet(),
    /** Characters added one by one (also any not in a group). The default: the classic ten-step ramp. */
    val extra: String = CLASSIC,
    /** Characters switched off one by one. */
    val removed: String = "",
    val filters: Set<GlyphFilter> = emptySet(),
    /**
     * Ramp only: characters whose ink differs by less than this from one kept
     * (0..0.05 of the whole range) are dropped, so every step of the ramp is a
     * step of tone.
     */
    val dedupe: Float = 0.004f,
    /** How much each character is preferred (above 0) or avoided (below 0), -1..1, by the character. */
    val bias: Map<String, Float> = emptyMap(),
    /** The largest share of the drawn cells one character may take (1: no limit). */
    val maxShare: Float = 1f,
    /** The longest run of one character in a row (0: no limit); spaces are never limited. */
    val maxRun: Int = 0,
) {
    companion object {
        /** Ten steps of ink, the best known ramp. */
        const val CLASSIC = ".:-=+*#%@"

        /** Paul Bourke's seventy steps (the space, always there, is the first). */
        const val DETAILED = ".'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@\$"
    }
}

/** How the light of the picture is read before it becomes ink. */
data class ArtTone(
    /** -1..1, added to the lightness. */
    val brightness: Float = 0f,
    /** -1..1. */
    val contrast: Float = 0f,
    /** The exponent of the lightness, 0.3..3 (1: as it is). */
    val gamma: Float = 1f,
    /** The lightness at 0, 1/4, 1/2, 3/4 and 1, a curve drawn through them (a straight line: as it is). */
    val curve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    /** Spreads the picture's lightness over the whole ramp (histogram equalisation). */
    val equalize: Boolean = false,
    /** Ink for light and paper for dark: a negative. */
    val invert: Boolean = false,
    /** How much the edges are sharpened before choosing, 0..2. */
    val edgeBoost: Float = 0f,
    /** Edges mode: how strong an edge must be to be drawn, 0..1. */
    val edgeThreshold: Float = 0.22f,
    /** Edges mode: the cells off the edges are drawn from the ramp (or left empty). */
    val edgeFill: Boolean = true,
)

/** The colours of an ANSI file. */
data class ArtPalette(
    val depth: ArtColor = ArtColor.ANSI_256,
    val theme: TerminalTheme = TerminalTheme.XTERM,
    val layer: ColorLayer = ColorLayer.FOREGROUND,
    val match: ColorMatch = ColorMatch.OKLAB,
    /** Palette entries never used (indices of the 16 or the 256 colours). */
    val banned: Set<Int> = emptySet(),
    /** Palette entries painted as another one (index to index). */
    val remap: Map<Int, Int> = emptyMap(),
    /** The picture's colours first brought down to this many (0: all of them), then matched. */
    val reduceTo: Int = 0,
    /** -1..1: the colours made duller or more vivid. */
    val saturation: Float = 0f,
    val bright: BrightMode = BrightMode.AIXTERM,
    /** CP437 files: bright backgrounds through the blink bit (iCE colours, said in SAUCE). */
    val iceColors: Boolean = false,
    /** The character carries the light, the colour only the hue: the colours are all as light as one another. */
    val lightFromGlyph: Boolean = false,
)

/** A stretch of hues (degrees, 0..360, going round from [start] to [end]) left out. */
data class HueBand(val start: Float = 0f, val end: Float = 30f, val minChroma: Float = 0.04f)

/** A stroke of the brush (points x, y, x, y... as fractions of the picture), hiding or showing. */
data class ArtStroke(val points: List<Float> = emptyList(), val radius: Float = 0.03f, val show: Boolean = false)

/** What is left out of the art: the cells it covers stay empty. */
data class ArtHide(
    /** Below this opacity a pixel is not there (the cut-out's background). */
    val alphaCut: Float = 0.5f,
    /** Only the lightness between these two (0..1) is drawn. */
    val lightFrom: Float = 0f,
    val lightTo: Float = 1f,
    val hues: List<HueBand> = emptyList(),
    /** Colours left out (0xRRGGBB), each with everything nearer than [tolerance] to it. */
    val colors: List<Long> = emptyList(),
    /** In OKLab units (0..0.4). */
    val tolerance: Float = 0.08f,
    /** Shows only what the light, hue and colour choices pick, instead of leaving it out. */
    val invert: Boolean = false,
    val strokes: List<ArtStroke> = emptyList(),
)

/** How the file is laid out. */
data class ArtLayout(
    val frame: FrameStyle = FrameStyle.NONE,
    /** Empty cells around the art, inside the frame. */
    val margin: Int = 0,
    /** Set in the top of the frame (or above the art), and the SAUCE title. */
    val title: String = "",
    /** Set in the bottom of the frame (or below the art), and the SAUCE author. */
    val signature: String = "",
    /** The SAUCE group. */
    val group: String = "",
    val trimTrailing: Boolean = true,
    val lineEnding: LineEnding = LineEnding.LF,
    /** A byte order mark at the start of a UTF-8 file. */
    val bom: Boolean = false,
    val encoding: ArtEncoding = ArtEncoding.UTF8,
    /** Every line ends with a reset, so a line pasted alone keeps its colours (larger file). */
    val resetEachLine: Boolean = true,
    /** Warn when a line is wider than this (0: no limit). */
    val maxWidth: Int = 0,
)

/** Everything the text-art editor sets. */
data class TextArtSettings(
    val mode: ArtMode = ArtMode.RAMP,
    /** How many characters across. */
    val columns: Int = DEFAULT_COLUMNS,
    /**
     * How many lines down; 0: as many as keep the picture's shape. Set, the
     * picture is stretched or squeezed to fill the columns and lines asked for.
     */
    val rows: Int = 0,
    /** A cell's width over its height; 0: as the system's monospaced font has it. */
    val cellAspect: Float = 0f,
    val paper: Paper = Paper.DARK,
    val alphabet: Alphabet = Alphabet(),
    val tone: ArtTone = ArtTone(),
    val dither: Dither = Dither.NONE,
    /** 0..1. */
    val ditherStrength: Float = 1f,
    val ditherTarget: DitherTarget = DitherTarget.BOTH,
    val palette: ArtPalette = ArtPalette(),
    val hide: ArtHide = ArtHide(),
    val layout: ArtLayout = ArtLayout(),
    /** Text fill mode: the phrase the picture is written with. */
    val phrase: String = "",
    /** The target last picked (the editor's profile chips). */
    val target: TextTarget = TextTarget.TERMINAL,
) {
    companion object {
        const val DEFAULT_COLUMNS = 100
        const val MIN_COLUMNS = 8
        const val MAX_COLUMNS = 400
        const val MIN_ROWS = 2
        const val MAX_ROWS = 600
    }
}
