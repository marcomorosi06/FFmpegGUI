package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import java.util.Locale

/** A word as it is shown: its text (cased as the style asks) and when it is said. */
data class CueWord(val text: String, val startMs: Long, val endMs: Long)

/**
 * What is on screen from [startMs] to [endMs]: up to a few [lines] of words.
 * [timed] blocks come from speech (every word has its own time and can be
 * highlighted); the others from a subtitle file.
 */
data class SubtitleBlock(
    val startMs: Long,
    val endMs: Long,
    val lines: List<List<CueWord>>,
    val timed: Boolean,
    /**
     * Below 1 when a single word is wider than the frame allows: the whole
     * block is drawn this much smaller (text, outline and box), so it fits.
     */
    val scale: Float = 1f,
) {
    val words: List<CueWord> get() = lines.flatten()

    val text: String get() = lines.joinToString("\n") { line -> line.joinToString(" ") { it.text } }
}

/**
 * Splits speech (or the lines of a subtitle file) into what is on screen at
 * once. The preview and the export both draw exactly these blocks, line by
 * line, so the lines break in the same places in both.
 *
 * Lines are broken where their width, added up from the advances of the font
 * libass will draw with ([FontFacts], the same file in the preview and in the
 * export), would leave the frame. Without a font, widths are estimated.
 */
object SubtitleLayout {

    /**
     * How wide lines may be and how wide they are, for [style] on a frame of
     * [frameRatio] (width / height) drawn with [font]. Widths are in frame heights.
     */
    class Measure(style: SubtitleStyle, frameRatio: Float, private val font: FontFacts?) {
        private val ratio = if (frameRatio.isFinite() && frameRatio > 0f) frameRatio else DEFAULT_RATIO

        /** The widest a line may be. */
        val maxWidth: Float = USABLE_WIDTH * ratio

        // libass makes a bold the font does not have by thickening the letters, a little wider.
        private val synthetic = style.bold && (font == null || font.weight < BOLD_WEIGHT)
        private val scale = style.size * (font?.emPerSize ?: EM_PER_SIZE) * (if (synthetic) SYNTHETIC_BOLD_WIDER else 1f)

        /** Width of [text] in frame heights. */
        fun width(text: String): Float = (font?.widthEm(text) ?: estimatedEm(text)) * scale

        fun width(line: List<CueWord>): Float = width(line.joinToString(" ") { it.text })

        /** 1, or less when the widest of [lines] (a word alone on its line) is wider than allowed. */
        fun scaleToFit(lines: List<List<CueWord>>): Float {
            val widest = lines.maxOfOrNull { width(it) } ?: 0f
            return if (widest > maxWidth && widest > 0f) (maxWidth / widest).coerceAtLeast(MIN_SCALE) else 1f
        }

        fun fits(line: List<CueWord>): Boolean = line.size <= 1 || (width(line) <= maxWidth && chars(line) <= MAX_LINE_CHARS)

        private fun estimatedEm(text: String): Float = text.sumOf { c ->
            when {
                c == ' ' -> SPACE_EM.toDouble()
                c.isUpperCase() -> CAPITAL_EM.toDouble()
                else -> CHAR_EM.toDouble()
            }
        }.toFloat()
    }

    /**
     * Blocks of the words heard (already on the clock they are shown on, in
     * order). A block ends at the end of a sentence, at a pause, when it has
     * been on screen for long, or when its lines are full.
     */
    fun ofWords(words: List<SpokenWord>, style: SubtitleStyle, frameRatio: Float, font: FontFacts? = null): List<SubtitleBlock> {
        val sane = style.sane()
        val measure = Measure(sane, frameRatio, font)
        val groups = mutableListOf<List<CueWord>>()
        var current = mutableListOf<CueWord>()
        var previous: SpokenWord? = null
        for (word in words) {
            val text = cased(word.text, sane).trim()
            if (text.isEmpty()) continue
            val shown = CueWord(text, word.startMs, word.endMs)
            val last = previous
            val breakBefore = last != null && current.isNotEmpty() && (
                word.startMs - last.endMs >= PAUSE_MS ||
                    endsSentence(last.text) ||
                    word.endMs - current.first().startMs > MAX_BLOCK_MS ||
                    wrap(current + shown, measure).size > sane.maxLines
                )
            if (breakBefore) {
                groups += current
                current = mutableListOf()
            }
            current += shown
            previous = word
        }
        if (current.isNotEmpty()) groups += current

        return groups.mapIndexed { index, group ->
            val start = group.first().startMs
            val nextStart = groups.getOrNull(index + 1)?.first()?.startMs
            var end = maxOf(group.last().endMs + TAIL_MS, start + MIN_BLOCK_MS)
            if (nextStart != null) end = minOf(end, nextStart)
            val lines = balanced(group, measure, sane.maxLines)
            SubtitleBlock(start, maxOf(end, start + 1), lines, timed = true, scale = measure.scaleToFit(lines))
        }
    }

    /** Blocks of a subtitle file's lines: its own timing and line breaks, long lines broken again. */
    fun ofCues(cues: List<SubtitleCue>, style: SubtitleStyle, frameRatio: Float, font: FontFacts? = null): List<SubtitleBlock> {
        val sane = style.sane()
        val measure = Measure(sane, frameRatio, font)
        return cues.mapNotNull { cue ->
            val lines = cue.text.split('\n').flatMap { line ->
                val words = cased(line, sane).split(' ').filter { it.isNotBlank() }.map { CueWord(it, cue.startMs, cue.endMs) }
                wrap(words, measure)
            }.filter { it.isNotEmpty() }
            if (lines.isEmpty() || cue.endMs <= cue.startMs) null else SubtitleBlock(cue.startMs, cue.endMs, lines, timed = false, scale = measure.scaleToFit(lines))
        }
    }

    /** The block on screen at [timeMs], if any. */
    fun blockAt(blocks: List<SubtitleBlock>, timeMs: Long): SubtitleBlock? {
        // Blocks are sorted and do not overlap when they come from speech; files may overlap.
        var low = 0
        var high = blocks.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (blocks[middle].startMs <= timeMs) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        if (found < 0) return null
        for (i in found downTo maxOf(0, found - OVERLAP_LOOKBACK)) {
            val block = blocks[i]
            if (timeMs >= block.startMs && timeMs < block.endMs) return block
        }
        return null
    }

    /**
     * Which words of [block] are highlighted at [timeMs] with [highlight]: the
     * indexes (in [SubtitleBlock.words]) from the first to the last one, or an
     * empty range. The word being said stays lit until the next one starts.
     */
    fun highlighted(block: SubtitleBlock, timeMs: Long, highlight: WordHighlight): IntRange {
        if (!block.timed || highlight == WordHighlight.NONE) return IntRange.EMPTY
        val words = block.words
        val current = words.indexOfLast { it.startMs <= timeMs }
        if (current < 0) return IntRange.EMPTY
        return if (highlight == WordHighlight.SPOKEN) 0..current else current..current
    }

    /**
     * The moments inside [block] where the highlight changes, [SubtitleBlock.startMs]
     * and [SubtitleBlock.endMs] included, in order. Between two of them the
     * highlight stays the same.
     */
    fun highlightSteps(block: SubtitleBlock, highlight: WordHighlight): List<Long> {
        if (!block.timed || highlight == WordHighlight.NONE) return listOf(block.startMs, block.endMs)
        val inner = block.words.map { it.startMs }.filter { it > block.startMs && it < block.endMs }
        return (listOf(block.startMs) + inner + block.endMs).distinct()
    }

    // ---------------------------------------------------------------------

    private fun cased(text: String, style: SubtitleStyle): String =
        if (style.uppercase) text.uppercase(Locale.ROOT) else text

    private fun endsSentence(text: String): Boolean = text.trimEnd().lastOrNull()?.let { it in SENTENCE_ENDS } == true

    private fun chars(line: List<CueWord>): Int = line.sumOf { it.text.length } + (line.size - 1).coerceAtLeast(0)

    /** Greedy word wrap; a word wider than a line gets a line of its own. */
    private fun wrap(words: List<CueWord>, measure: Measure): List<List<CueWord>> {
        val lines = mutableListOf<List<CueWord>>()
        var line = mutableListOf<CueWord>()
        for (word in words) {
            if (line.isNotEmpty() && !measure.fits(line + word)) {
                lines += line
                line = mutableListOf()
            }
            line += word
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    /**
     * The words of a block on its lines: one line when they fit, else two of
     * about the same width (the first one rather the longer), which reads better
     * than a full line over a single word.
     */
    private fun balanced(words: List<CueWord>, measure: Measure, maxLines: Int): List<List<CueWord>> {
        val wrapped = wrap(words, measure)
        if (wrapped.size != 2 || maxLines < 2) return wrapped
        var best = wrapped
        var bestScore = Float.MAX_VALUE
        for (split in 1 until words.size) {
            val first = words.subList(0, split)
            val second = words.subList(split, words.size)
            if (!measure.fits(first) || !measure.fits(second)) continue
            val a = measure.width(first)
            val b = measure.width(second)
            // The wider line decides; a first line narrower than the second costs a little more.
            val score = maxOf(a, b) + (if (a < b) (b - a) * 0.25f else 0f)
            if (score < bestScore) {
                bestScore = score
                best = listOf(first, second)
            }
        }
        return best
    }

    /**
     * Font size (the height of a line) to the size of the em of a typical
     * sans-serif font, as libass sizes it (ascent plus descent of the OS/2
     * table): 2048 / (2146 + 555) for Roboto.
     */
    const val EM_PER_SIZE = 0.758f

    /** Estimated widths in em, without a font: a space, a capital, any other character. */
    private const val SPACE_EM = 0.25f
    private const val CAPITAL_EM = 0.66f
    private const val CHAR_EM = 0.52f

    private const val BOLD_WEIGHT = 600
    private const val SYNTHETIC_BOLD_WIDER = 1.05f

    /** Lines take up to this part of the frame width. */
    private const val USABLE_WIDTH = 0.88f
    private const val DEFAULT_RATIO = 16f / 9f
    /** A word too wide for the frame is never drawn smaller than this. */
    private const val MIN_SCALE = 0.4f

    /** Longer lines are hard to read, however wide the frame. */
    private const val MAX_LINE_CHARS = 42

    /** A silence this long ends a block. */
    private const val PAUSE_MS = 700L

    /** A block is never on screen longer than this. */
    private const val MAX_BLOCK_MS = 6_000L

    /** A block stays a little after its last word, and on screen for at least a moment. */
    private const val TAIL_MS = 300L
    private const val MIN_BLOCK_MS = 600L

    /** Lines of a subtitle file that overlap are looked for this far back. */
    private const val OVERLAP_LOOKBACK = 8

    private val SENTENCE_ENDS = setOf('.', '?', '!', '\u2026', '\u3002', '\uFF1F', '\uFF01')
}
