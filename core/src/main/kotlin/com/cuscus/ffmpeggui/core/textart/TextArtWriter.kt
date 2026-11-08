package com.cuscus.ffmpeggui.core.textart

import java.io.ByteArrayOutputStream

/*
 * Text art laid out (frame, margins, title and signature) and written: a plain
 * text file (ASCII art) or an ANSI file (escape codes for the colours; UTF-8,
 * or code page 437 with a SAUCE record, as the ANSI art scene's files are).
 */
object TextArtLayout {
    /** [art] with the frame, margins, title and signature of [layout]; [cp437]: only characters code page 437 has. */
    fun compose(art: TextArt, layout: ArtLayout, cp437: Boolean = false): TextArt {
        val margin = layout.margin.coerceIn(0, MAX_MARGIN)
        val frame = frameOf(layout.frame, cp437)
        val border = if (frame == null) 0 else 1
        val innerW = art.columns + margin * 2
        val innerH = art.rows + margin * 2
        val title = clean(layout.title, cp437)
        val signature = clean(layout.signature, cp437)
        // Without a frame, the title and the signature take a line of their own.
        val titleRow = if (frame == null && title.isNotEmpty()) 1 else 0
        val signatureRow = if (frame == null && signature.isNotEmpty()) 1 else 0
        val w = innerW + border * 2
        val h = innerH + border * 2 + titleRow + signatureRow
        if (w == art.columns && h == art.rows) return art
        val chars = IntArray(w * h) { Glyphs.SPACE }
        val fg = IntArray(w * h) { -1 }
        val bg = IntArray(w * h) { -1 }
        val top = titleRow + border + margin
        val left = border + margin
        for (y in 0 until art.rows) for (x in 0 until art.columns) {
            val from = y * art.columns + x
            val to = (top + y) * w + left + x
            chars[to] = art.chars[from]
            fg[to] = art.fg[from]
            bg[to] = art.bg[from]
        }
        if (frame != null) {
            val y0 = titleRow
            val y1 = titleRow + innerH + 1
            for (x in 1 until w - 1) {
                chars[y0 * w + x] = frame.horizontal
                chars[y1 * w + x] = frame.horizontal
            }
            for (y in y0 + 1 until y1) {
                chars[y * w] = frame.vertical
                chars[y * w + w - 1] = frame.vertical
            }
            chars[y0 * w] = frame.topLeft
            chars[y0 * w + w - 1] = frame.topRight
            chars[y1 * w] = frame.bottomLeft
            chars[y1 * w + w - 1] = frame.bottomRight
            if (title.isNotEmpty()) write(chars, y0 * w + 2, w - 4, " $title ", alignEnd = false)
            if (signature.isNotEmpty()) write(chars, y1 * w + 2, w - 4, " $signature ", alignEnd = true)
        } else {
            if (title.isNotEmpty()) write(chars, 0, w, title, alignEnd = false)
            if (signature.isNotEmpty()) write(chars, (h - 1) * w, w, signature, alignEnd = true)
        }
        return TextArt(w, h, chars, fg, bg, art.depth, art.palette, art.paper, art.drawnCells)
    }

    /** [text] on one line, each character one code page 437 has when [cp437] (else a ?). */
    private fun clean(text: String, cp437: Boolean): String = buildString {
        for (cp in Glyphs.points(text.replace('\n', ' ').replace('\r', ' ').trim())) {
            val printable = cp >= 0x20 && cp != 0x7F
            appendCodePoint(if (!printable || (cp437 && !Glyphs.inCp437(cp))) '?'.code else cp)
        }
    }

    private fun write(chars: IntArray, start: Int, room: Int, text: String, alignEnd: Boolean) {
        if (room <= 0) return
        val shown = Glyphs.points(text).take(room)
        val offset = if (alignEnd) room - shown.size else 0
        for ((k, c) in shown.withIndex()) chars[start + offset + k] = c
    }

    class Frame(val topLeft: Int, val topRight: Int, val bottomLeft: Int, val bottomRight: Int, val horizontal: Int, val vertical: Int)

    /** The characters of [style]; code page 437 has no rounded or heavy corners: single and double instead. */
    fun frameOf(style: FrameStyle, cp437: Boolean): Frame? = when (style) {
        FrameStyle.NONE -> null
        FrameStyle.ASCII -> Frame('+'.code, '+'.code, '+'.code, '+'.code, '-'.code, '|'.code)
        FrameStyle.SINGLE -> Frame(0x250C, 0x2510, 0x2514, 0x2518, 0x2500, 0x2502)
        FrameStyle.DOUBLE -> Frame(0x2554, 0x2557, 0x255A, 0x255D, 0x2550, 0x2551)
        FrameStyle.ROUNDED -> if (cp437) frameOf(FrameStyle.SINGLE, true) else Frame(0x256D, 0x256E, 0x2570, 0x256F, 0x2500, 0x2502)
        FrameStyle.HEAVY -> if (cp437) frameOf(FrameStyle.DOUBLE, true) else Frame(0x250F, 0x2513, 0x2517, 0x251B, 0x2501, 0x2503)
    }

    private const val MAX_MARGIN = 20
}

object TextArtWriter {
    private const val ESC = "\u001B["

    /** [art] as plain text: one line a row, trailing spaces cut as [layout] says. */
    fun plain(art: TextArt, layout: ArtLayout): String = buildString {
        for (y in 0 until art.rows) {
            val row = art.row(y)
            append(if (layout.trimTrailing) row.trimEnd(' ') else row)
            append(layout.lineEnding.text)
        }
    }

    /** The bytes of a plain text file of [art] (UTF-8, a byte order mark first when asked). */
    fun plainBytes(art: TextArt, layout: ArtLayout): ByteArray {
        val text = plain(art, layout).toByteArray(Charsets.UTF_8)
        return if (layout.bom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text else text
    }

    /**
     * [art] as an ANSI file. UTF-8: SGR codes changed only where they must (a
     * full reset only to turn something off). Code page 437: every change a
     * full reset and the attributes again (what every viewer reads), bold for
     * the bright foregrounds and blink for the bright backgrounds, then the
     * end-of-file mark and the SAUCE record.
     */
    fun ansiBytes(art: TextArt, settings: TextArtSettings): ByteArray {
        val layout = settings.layout
        val cp437 = layout.encoding == ArtEncoding.CP437
        val bright = if (cp437) BrightMode.BOLD else settings.palette.bright
        val out = ByteArrayOutputStream()
        if (!cp437 && layout.bom) out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        var state = Sgr.DEFAULT
        fun emit(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
        fun put(code: Int) {
            if (cp437) {
                out.write(Cp437.byteOf(code) ?: '?'.code)
            } else {
                out.write(String(Character.toChars(code)).toByteArray(Charsets.UTF_8))
            }
        }
        for (y in 0 until art.rows) {
            var end = art.columns
            if (layout.trimTrailing) {
                while (end > 0) {
                    val i = y * art.columns + end - 1
                    if (art.chars[i] == Glyphs.SPACE && art.bg[i] < 0) end-- else break
                }
            }
            for (x in 0 until end) {
                val i = y * art.columns + x
                val fg = if (art.chars[i] == Glyphs.SPACE) state.fg else art.fg[i]
                val want = Sgr.of(art.depth, fg, art.bg[i], bright)
                if (want != state) {
                    emit(transition(state, want, art.depth, full = cp437))
                    state = want
                }
                put(art.chars[i])
            }
            // A background never runs on past the line; with resetEachLine nothing does.
            if (state != Sgr.DEFAULT && (layout.resetEachLine || state.bg >= 0 || state.blink)) {
                emit("${ESC}0m")
                state = Sgr.DEFAULT
            }
            emit(layout.lineEnding.text)
        }
        if (state != Sgr.DEFAULT) emit("${ESC}0m")
        if (cp437) {
            val size = out.size()
            out.write(0x1A)
            out.write(sauce(art, settings, size))
        }
        return out.toByteArray()
    }

    /** The bytes of [art] as the file [ansi] (ANSI) or plain text makes them. */
    fun bytes(art: TextArt, settings: TextArtSettings, ansi: Boolean): ByteArray =
        if (ansi) ansiBytes(art, settings) else plainBytes(art, settings.layout)

    /** What the terminal is set to: a foreground and a background (-1: its own), bold and blink. */
    private data class Sgr(val fg: Int, val bg: Int, val bold: Boolean, val blink: Boolean, val fgCode: String?, val bgCode: String?) {
        companion object {
            val DEFAULT = Sgr(-1, -1, false, false, null, null)

            fun of(depth: ArtColor, fg: Int, bg: Int, bright: BrightMode): Sgr {
                if (fg < 0 && bg < 0) return DEFAULT
                return when (depth) {
                    ArtColor.NONE -> DEFAULT
                    ArtColor.ANSI_16 -> {
                        val bold = bright == BrightMode.BOLD && fg >= 8
                        val blink = bright == BrightMode.BOLD && bg >= 8
                        val fgCode = if (fg < 0) null else if (bright == BrightMode.AIXTERM && fg >= 8) "${90 + fg - 8}" else "${30 + fg % 8}"
                        val bgCode = if (bg < 0) null else if (bright == BrightMode.AIXTERM && bg >= 8) "${100 + bg - 8}" else "${40 + bg % 8}"
                        Sgr(fg, bg, bold, blink, fgCode, bgCode)
                    }
                    ArtColor.ANSI_256 -> Sgr(fg, bg, false, false, if (fg < 0) null else "38;5;$fg", if (bg < 0) null else "48;5;$bg")
                    ArtColor.TRUECOLOR -> Sgr(
                        fg, bg, false, false,
                        if (fg < 0) null else "38;2;${(fg shr 16) and 0xFF};${(fg shr 8) and 0xFF};${fg and 0xFF}",
                        if (bg < 0) null else "48;2;${(bg shr 16) and 0xFF};${(bg shr 8) and 0xFF};${bg and 0xFF}",
                    )
                }
            }
        }
    }

    /** The escape that turns [from] into [to]: changes only, unless [full] or something must be turned off. */
    private fun transition(from: Sgr, to: Sgr, depth: ArtColor, full: Boolean): String {
        if (to == Sgr.DEFAULT) return "${ESC}0m"
        val turnsOff = (from.bold && !to.bold) || (from.blink && !to.blink)
        val params = ArrayList<String>()
        if (full || turnsOff) {
            params += "0"
            if (to.bold) params += "1"
            if (to.blink) params += "5"
            to.fgCode?.let { params += it }
            to.bgCode?.let { params += it }
        } else {
            if (to.bold && !from.bold) params += "1"
            if (to.blink && !from.blink) params += "5"
            if (to.fgCode != from.fgCode) params += to.fgCode ?: "39"
            if (to.bgCode != from.bgCode) params += to.bgCode ?: "49"
        }
        if (depth == ArtColor.NONE) return ""
        return ESC + params.joinToString(";") + "m"
    }

    /** The SAUCE record (128 bytes) of a CP437 file whose art is [size] bytes. No date: nothing that tells one file from another. */
    internal fun sauce(art: TextArt, settings: TextArtSettings, size: Int): ByteArray {
        val out = ByteArray(SAUCE_SIZE) { 0x20 }
        fun text(at: Int, length: Int, value: String) {
            for ((k, c) in Glyphs.points(value).take(length).withIndex()) out[at + k] = (Cp437.byteOf(c) ?: '?'.code).toByte()
        }
        fun le16(at: Int, value: Int) {
            out[at] = (value and 0xFF).toByte()
            out[at + 1] = ((value shr 8) and 0xFF).toByte()
        }
        text(0, 5, "SAUCE")
        text(5, 2, "00")
        text(7, 35, settings.layout.title.trim())
        text(42, 20, settings.layout.signature.trim())
        text(62, 20, settings.layout.group.trim())
        // 82..89: the date, left blank.
        out[90] = (size and 0xFF).toByte()
        out[91] = ((size shr 8) and 0xFF).toByte()
        out[92] = ((size shr 16) and 0xFF).toByte()
        out[93] = ((size shr 24) and 0xFF).toByte()
        out[94] = 1 // DataType: character
        out[95] = 1 // FileType: ANSi
        le16(96, art.columns)
        le16(98, art.rows)
        le16(100, 0)
        le16(102, 0)
        out[104] = 0 // no comment block
        // TFlags: iCE colours (bit 0), 8 pixel letter spacing (bits 1-2 = 01).
        out[105] = ((if (settings.palette.iceColors) 1 else 0) or (1 shl 1)).toByte()
        val font = "IBM VGA".toByteArray(Charsets.US_ASCII)
        for (k in 0 until 22) out[106 + k] = if (k < font.size) font[k] else 0
        return out
    }

    const val SAUCE_SIZE = 128
}

/** What may go wrong where the text is going. */
enum class ArtWarning {
    /** Characters beyond ASCII: the file is UTF-8, some places show them badly. */
    NON_ASCII,

    /** Characters Markdown or a chat reads as formatting. */
    MARKDOWN_CHARS,

    /** Characters East Asian terminals may draw two columns wide. */
    AMBIGUOUS_WIDTH,

    /** Characters the system's font cannot draw. */
    MISSING_GLYPHS,

    /** Characters code page 437 has no byte for: written as ?. */
    UNENCODABLE,

    /** Lines wider than the target allows. */
    TOO_WIDE,

    /** Nothing is drawn: everything is left out. */
    EMPTY,
}

/** What the file holds, for the editor's check: its size, characters and colours, and what to look out for. */
data class TextArtReport(
    val columns: Int,
    val rows: Int,
    val bytes: Int,
    /** The characters used (not the space), most used first, with how many cells each takes. */
    val characters: List<Pair<Int, Int>>,
    /** The colours used (0xRRGGBB), most used first, with how many cells each takes. */
    val colors: List<Pair<Int, Int>>,
    val warnings: Set<ArtWarning>,
) {
    companion object {
        private val MARKDOWN = Glyphs.codes("`*_~|#>").toSet()

        fun of(art: TextArt, bytes: Int, settings: TextArtSettings, atlas: GlyphAtlas?, ansi: Boolean): TextArtReport {
            val chars = HashMap<Int, Int>()
            val colors = HashMap<Int, Int>()
            for (i in art.chars.indices) {
                val c = art.chars[i]
                if (c != Glyphs.SPACE) chars[c] = (chars[c] ?: 0) + 1
                val f = art.rgb(art.fg[i])
                val b = art.rgb(art.bg[i])
                if (f >= 0) colors[f] = (colors[f] ?: 0) + 1
                if (b >= 0) colors[b] = (colors[b] ?: 0) + 1
            }
            val warnings = LinkedHashSet<ArtWarning>()
            val cp437 = ansi && settings.layout.encoding == ArtEncoding.CP437
            for (c in chars.keys) {
                if (c > 0x7E) warnings += ArtWarning.NON_ASCII
                if (c in MARKDOWN) warnings += ArtWarning.MARKDOWN_CHARS
                if (Glyphs.ambiguousWidth(c)) warnings += ArtWarning.AMBIGUOUS_WIDTH
                if (atlas != null && !atlas.has(c) && c !in Glyphs.BLOCK_ELEMENTS && (c < Glyphs.BRAILLE_BASE || c > Glyphs.BRAILLE_BASE + 0xFF)) warnings += ArtWarning.MISSING_GLYPHS
                if (cp437 && Cp437.byteOf(c) == null) warnings += ArtWarning.UNENCODABLE
            }
            // A code page 437 file is read a byte a column by the viewers it is for.
            if (cp437) {
                warnings -= ArtWarning.NON_ASCII
                warnings -= ArtWarning.AMBIGUOUS_WIDTH
            }
            val max = settings.layout.maxWidth
            if (max > 0 && art.columns > max) warnings += ArtWarning.TOO_WIDE
            if (art.drawnCells == 0 || chars.isEmpty() && colors.isEmpty()) warnings += ArtWarning.EMPTY
            return TextArtReport(
                art.columns, art.rows, bytes,
                chars.entries.sortedByDescending { it.value }.map { it.key to it.value },
                colors.entries.sortedByDescending { it.value }.map { it.key to it.value },
                warnings,
            )
        }
    }
}

/** Where the art is going: the settings each place asks for (the editor's target chips). */
object TextArtPresets {
    fun apply(target: TextTarget, settings: TextArtSettings, ansi: Boolean): TextArtSettings {
        val a = settings.alphabet
        val l = settings.layout
        val p = settings.palette
        return when (target) {
            TextTarget.TERMINAL -> settings.copy(
                target = target,
                paper = Paper.DARK,
                columns = 100,
                alphabet = a.copy(filters = a.filters - GlyphFilter.ASCII_ONLY - GlyphFilter.MARKDOWN_SAFE),
                layout = l.copy(lineEnding = LineEnding.LF, encoding = ArtEncoding.UTF8, maxWidth = 0, resetEachLine = true),
                palette = p.copy(bright = BrightMode.AIXTERM, depth = if (p.depth == ArtColor.NONE) ArtColor.ANSI_256 else p.depth),
            )
            TextTarget.EDITOR -> settings.copy(
                target = target,
                paper = Paper.LIGHT,
                columns = 100,
                alphabet = a.copy(filters = a.filters + GlyphFilter.SAFE_WIDTH),
                layout = l.copy(lineEnding = LineEnding.LF, encoding = ArtEncoding.UTF8, maxWidth = 0, trimTrailing = true),
            )
            TextTarget.MARKDOWN -> settings.copy(
                target = target,
                paper = Paper.LIGHT,
                columns = 80,
                mode = if (settings.mode in ASCII_MODES) settings.mode else ArtMode.RAMP,
                alphabet = a.copy(filters = a.filters + GlyphFilter.ASCII_ONLY),
                layout = l.copy(lineEnding = LineEnding.LF, encoding = ArtEncoding.UTF8, maxWidth = 80, trimTrailing = true, bom = false),
            )
            TextTarget.CHAT -> settings.copy(
                target = target,
                paper = Paper.DARK,
                columns = 48,
                mode = if (settings.mode in ASCII_MODES) settings.mode else ArtMode.RAMP,
                alphabet = a.copy(filters = a.filters + GlyphFilter.ASCII_ONLY + GlyphFilter.MARKDOWN_SAFE + GlyphFilter.SAFE_WIDTH),
                layout = l.copy(lineEnding = LineEnding.LF, encoding = ArtEncoding.UTF8, maxWidth = 48, trimTrailing = true, bom = false, frame = FrameStyle.NONE),
            )
            TextTarget.BBS -> if (!ansi) settings else settings.copy(
                target = target,
                paper = Paper.DARK,
                columns = 80,
                mode = if (settings.mode == ArtMode.BRAILLE) ArtMode.HALF_BLOCKS else settings.mode,
                palette = p.copy(depth = ArtColor.ANSI_16, theme = TerminalTheme.VGA, bright = BrightMode.BOLD),
                layout = l.copy(encoding = ArtEncoding.CP437, lineEnding = LineEnding.CRLF, maxWidth = 80, resetEachLine = false, bom = false),
            )
        }
    }

    /** Every printable ASCII character: what the shapes mode needs to find a shape. */
    val ALL_ASCII = Alphabet(groups = setOf(GlyphGroup.UPPER, GlyphGroup.LOWER, GlyphGroup.DIGITS, GlyphGroup.PUNCTUATION, GlyphGroup.SYMBOLS), extra = "")

    /**
     * The alphabet [mode] starts from: the shapes mode needs many shapes, so the
     * untouched classic ramp becomes every ASCII character (and back); an
     * alphabet the user changed is kept as it is.
     */
    fun alphabetFor(mode: ArtMode, current: Alphabet): Alphabet = when {
        mode == ArtMode.SHAPE && current == Alphabet() -> ALL_ASCII
        mode != ArtMode.SHAPE && current == ALL_ASCII -> Alphabet()
        else -> current
    }

    /** The modes that need only ASCII characters (the alphabet's). */
    val ASCII_MODES = setOf(ArtMode.RAMP, ArtMode.SHAPE, ArtMode.EDGES, ArtMode.TEXT_FILL)
}
