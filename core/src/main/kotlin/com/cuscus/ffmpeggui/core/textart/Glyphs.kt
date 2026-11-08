package com.cuscus.ffmpeggui.core.textart

import kotlin.math.abs

/*
 * The characters text art is made of: the groups the alphabet takes, the
 * filters that keep a text safe where it is going, and the ink of each
 * character as the font draws it (GlyphAtlas, measured by the app on the
 * system's monospaced font, the font the editor shows the art with).
 */

/**
 * How each character of a font looks, measured: its ink in [subW] x [subH]
 * sub-cells (0 paper, 1 ink; row by row), for the characters the font has.
 * [cellAspect] is a cell's width over its height.
 */
class GlyphAtlas(
    val cellAspect: Float,
    val subW: Int,
    val subH: Int,
    private val coverage: Map<Int, FloatArray>,
    /** Characters the font has no glyph for (they would show as a box). */
    val missing: Set<Int> = emptySet(),
    /** Characters the font draws wider or narrower than a cell. */
    val wide: Set<Int> = emptySet(),
) {
    init {
        require(subW > 0 && subH > 0) { "Sub-cells must be at least 1 x 1" }
        require(coverage.values.all { it.size == subW * subH }) { "Every glyph needs $subW x $subH values" }
    }

    /** The ink of [code] in its sub-cells; null when the font cannot draw it. */
    fun coverage(code: Int): FloatArray? = if (code == SPACE) blank else coverage[code]

    /** How much of the cell [code] inks, 0..1. */
    fun density(code: Int): Float = densities.getOrPut(code) { coverage(code)?.average()?.toFloat() ?: -1f }

    fun has(code: Int): Boolean = code == SPACE || (code in coverage && code !in missing)

    private val blank = FloatArray(subW * subH)
    private val densities = HashMap<Int, Float>()

    companion object {
        const val SPACE = 0x20
    }
}

/** What the alphabet does with one character, for the editor's keys. */
enum class GlyphState { ACTIVE, OFF, FILTERED, MISSING, DUPLICATE, UNENCODABLE }

/** One character of the alphabet, its ink and whether it is used. */
data class GlyphKey(val code: Int, val density: Float, val state: GlyphState) {
    val char: String get() = String(Character.toChars(code))
}

/** The characters the art may use, darkest last, and why the others are left out. */
class ResolvedAlphabet(
    /** The characters used, by ink (lightest first; the space first of all). */
    val ramp: IntArray,
    /** Their ink, spread over 0..1 (the lightest 0, the darkest 1). */
    val levels: FloatArray,
    /** Every character offered, with its state, in the editor's order. */
    val keys: List<GlyphKey>,
)

object Glyphs {
    const val SPACE = 0x20

    /** Shade blocks, from empty to full. */
    const val LIGHT_SHADE = 0x2591
    const val MEDIUM_SHADE = 0x2592
    const val DARK_SHADE = 0x2593
    const val FULL_BLOCK = 0x2588
    const val UPPER_HALF = 0x2580
    const val LOWER_HALF = 0x2584
    const val LEFT_HALF = 0x258C
    const val RIGHT_HALF = 0x2590
    const val BRAILLE_BASE = 0x2800

    /** The characters of [group], in the order the editor shows them. */
    fun of(group: GlyphGroup): IntArray = when (group) {
        GlyphGroup.UPPER -> ('A'..'Z').map { it.code }.toIntArray()
        GlyphGroup.LOWER -> ('a'..'z').map { it.code }.toIntArray()
        GlyphGroup.DIGITS -> ('0'..'9').map { it.code }.toIntArray()
        GlyphGroup.PUNCTUATION -> codes(".,:;!?'\"`-_()[]{}")
        GlyphGroup.SYMBOLS -> codes("#$%&*+/\\|<>=@^~")
        GlyphGroup.BOX -> intArrayOf(
            0x2500, 0x2502, 0x250C, 0x2510, 0x2514, 0x2518, 0x251C, 0x2524, 0x252C, 0x2534, 0x253C,
            0x2550, 0x2551, 0x2554, 0x2557, 0x255A, 0x255D, 0x2560, 0x2563, 0x2566, 0x2569, 0x256C,
        )
        GlyphGroup.SHADES -> intArrayOf(LIGHT_SHADE, MEDIUM_SHADE, DARK_SHADE, FULL_BLOCK, UPPER_HALF, LOWER_HALF, LEFT_HALF, RIGHT_HALF)
        GlyphGroup.GEOMETRIC -> intArrayOf(0x25A0, 0x25A1, 0x25AA, 0x25AB, 0x25CF, 0x25CB, 0x25C6, 0x25C7, 0x25B2, 0x25BC, 0x25E2, 0x25E3, 0x25E4, 0x25E5, 0x2219, 0x00B7)
    }

    /** Every character the editor offers: the font is measured for these. */
    val candidates: IntArray by lazy {
        (listOf(SPACE) + GlyphGroup.entries.flatMap { of(it).toList() } + FRAMES.toList() + BLOCK_ELEMENTS.toList()).distinct().toIntArray()
    }

    /** The frames' own characters (TextArtLayout.frameOf) that no group holds. */
    private val FRAMES = intArrayOf(0x256D, 0x256E, 0x256F, 0x2570, 0x250F, 0x2513, 0x2517, 0x251B, 0x2501, 0x2503)

    /** Every block element (U+2580..U+259F): drawn by the app itself when the font lacks them. */
    val BLOCK_ELEMENTS: Set<Int> = (0x2580..0x259F).toSet()

    /** The code points of [text], in order, repeats kept. */
    fun points(text: String): List<Int> {
        val out = ArrayList<Int>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            out += cp
            i += Character.charCount(cp)
        }
        return out
    }

    /** The code points of [text], each once, in order. */
    fun codes(text: String): IntArray {
        val out = LinkedHashSet<Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            out += cp
            i += Character.charCount(cp)
        }
        return out.toIntArray()
    }

    private val MARKDOWN = codes("`*_~|#><[]\\").toSet()
    private val COMMENT = codes("*\\").toSet()
    private val LOOKALIKES = codes("l1I|O0").toSet()

    /** Whether [code] may be used under [filters]. */
    fun passes(code: Int, filters: Set<GlyphFilter>): Boolean {
        if (code == SPACE) return true
        for (filter in filters) {
            val ok = when (filter) {
                GlyphFilter.ASCII_ONLY -> code in 0x21..0x7E
                GlyphFilter.MARKDOWN_SAFE -> code !in MARKDOWN
                GlyphFilter.COMMENT_SAFE -> code !in COMMENT
                GlyphFilter.NO_LOOKALIKES -> code !in LOOKALIKES
                GlyphFilter.SAFE_WIDTH -> !ambiguousWidth(code)
            }
            if (!ok) return false
        }
        return true
    }

    /**
     * Characters of East Asian ambiguous width that the alphabet can hold:
     * Latin-1 and Greek letters and signs, general punctuation, box drawing,
     * blocks and geometric shapes. (Braille is neutral: always one column.)
     */
    fun ambiguousWidth(code: Int): Boolean =
        (code in 0x00A1..0x00FF && code != 0x00A0) || code in 0x0391..0x03C9 || code in 0x2010..0x27BF || code in 0x2190..0x21FF

    /** Whether [code] has a byte in code page 437. */
    fun inCp437(code: Int): Boolean = code in 0x20..0x7E || Cp437.byteOf(code) != null

    /**
     * The alphabet of [alphabet] as [atlas] draws it: the groups, plus the
     * extra characters, without the removed ones, the filtered ones, those the
     * font lacks (or draws off the cell), and (for a CP437 file, [cp437]) those
     * code page 437 has no byte for. With [dedupe], characters whose ink is
     * within the tolerance of one already kept are dropped (a ramp needs steps).
     * The space is always there: it is the paper.
     */
    fun resolve(alphabet: Alphabet, atlas: GlyphAtlas, cp437: Boolean = false, dedupe: Boolean = true): ResolvedAlphabet {
        val offered = LinkedHashSet<Int>()
        for (group in GlyphGroup.entries) if (group in alphabet.groups) offered += of(group).toList()
        offered += codes(alphabet.extra).toList()
        offered.remove(SPACE)
        val removed = codes(alphabet.removed).toSet()
        val keys = ArrayList<GlyphKey>(offered.size)
        val usable = ArrayList<Int>()
        for (code in offered) {
            val density = atlas.density(code)
            val state = when {
                code in removed -> GlyphState.OFF
                !passes(code, alphabet.filters) -> GlyphState.FILTERED
                !atlas.has(code) || code in atlas.wide || density < 0f -> GlyphState.MISSING
                cp437 && !inCp437(code) -> GlyphState.UNENCODABLE
                else -> GlyphState.ACTIVE
            }
            keys += GlyphKey(code, density.coerceAtLeast(0f), state)
            if (state == GlyphState.ACTIVE) usable += code
        }
        // Lightest first; equal ink keeps the order they were offered in.
        val sorted = usable.sortedBy { atlas.density(it) }
        val kept = ArrayList<Int>()
        kept += SPACE
        val top = sorted.lastOrNull()?.let { atlas.density(it) } ?: 1f
        val tolerance = alphabet.dedupe.coerceIn(0f, 0.05f) * top.coerceAtLeast(1e-6f)
        val dropped = HashSet<Int>()
        for (code in sorted) {
            val d = atlas.density(code)
            if (dedupe && tolerance > 0f && abs(d - atlas.density(kept.last())) < tolerance) {
                // The one preferred more stays.
                val last = kept.last()
                if (last != SPACE && (alphabet.bias[String(Character.toChars(code))] ?: 0f) > (alphabet.bias[String(Character.toChars(last))] ?: 0f)) {
                    kept[kept.size - 1] = code
                    dropped += last
                } else {
                    dropped += code
                }
                continue
            }
            kept += code
        }
        val maxInk = kept.maxOf { atlas.density(it) }.coerceAtLeast(1e-6f)
        val levels = FloatArray(kept.size) { (atlas.density(kept[it]) / maxInk).coerceIn(0f, 1f) }
        val withDuplicates = keys.map { if (it.code in dropped && it.state == GlyphState.ACTIVE) it.copy(state = GlyphState.DUPLICATE) else it }
        return ResolvedAlphabet(kept.toIntArray(), levels, withDuplicates)
    }
}

/** IBM code page 437: the bytes of the ANSI art scene's files. */
object Cp437 {
    /** The characters of bytes 0x80 to 0xFF. */
    private const val HIGH =
        "\u00C7\u00FC\u00E9\u00E2\u00E4\u00E0\u00E5\u00E7\u00EA\u00EB\u00E8\u00EF\u00EE\u00EC\u00C4\u00C5" +
        "\u00C9\u00E6\u00C6\u00F4\u00F6\u00F2\u00FB\u00F9\u00FF\u00D6\u00DC\u00A2\u00A3\u00A5\u20A7\u0192" +
        "\u00E1\u00ED\u00F3\u00FA\u00F1\u00D1\u00AA\u00BA\u00BF\u2310\u00AC\u00BD\u00BC\u00A1\u00AB\u00BB" +
        "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255D\u255C\u255B\u2510" +
        "\u2514\u2534\u252C\u251C\u2500\u253C\u255E\u255F\u255A\u2554\u2569\u2566\u2560\u2550\u256C\u2567" +
        "\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256B\u256A\u2518\u250C\u2588\u2584\u258C\u2590\u2580" +
        "\u03B1\u00DF\u0393\u03C0\u03A3\u03C3\u00B5\u03C4\u03A6\u0398\u03A9\u03B4\u221E\u03C6\u03B5\u2229" +
        "\u2261\u00B1\u2265\u2264\u2320\u2321\u00F7\u2248\u00B0\u2219\u00B7\u221A\u207F\u00B2\u25A0\u00A0"

    private val BYTES: Map<Int, Int> by lazy { HIGH.mapIndexed { i, c -> c.code to (0x80 + i) }.toMap() }

    /** The byte of [code], or null when code page 437 has none (printable ASCII is itself). */
    fun byteOf(code: Int): Int? = when (code) {
        in 0x20..0x7E -> code
        else -> BYTES[code]
    }

    /** The character of [byte] (0x20..0xFF). */
    fun charOf(byte: Int): Int = if (byte < 0x80) byte else HIGH[byte - 0x80].code
}
