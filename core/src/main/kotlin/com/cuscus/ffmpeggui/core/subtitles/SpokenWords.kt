package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.preview.mapToTimeline
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import kotlin.math.sqrt

/** A word heard in a file, on that file's own clock. */
data class SpokenWord(val startMs: Long, val endMs: Long, val text: String)

/** The words heard in source [source] of a request (an index into its sources). */
data class SourceWords(val source: Int, val words: List<SpokenWord>)

/**
 * Words out of what a speech recognizer returns.
 *
 * Recognizers built on sentence pieces (Parakeet) return pieces of words: a
 * piece that starts with a space (or the "lower one eighth block" U+2581 some
 * vocabularies use) starts a new word; the others ("sk" of "Ask", a comma, the
 * "-" of "Demandez-vous") join the word before. Every piece has its start; a
 * TDT model also gives how long it lasts.
 */
object RecognizedWords {

    /**
     * The words of one chunk of audio.
     *
     * @param tokens     the pieces, in order.
     * @param starts     when each piece starts, in seconds from the start of the chunk.
     * @param durations  how long each piece lasts, in seconds; null or shorter
     *                   when the model does not say (a piece then lasts until the
     *                   next one, the last one [LAST_PIECE_MS]).
     * @param offsetMs   where the chunk starts in the file.
     * @param chunkEndMs where the chunk ends in the file; no word goes past it.
     */
    fun fromTokens(
        tokens: List<String>,
        starts: FloatArray,
        durations: FloatArray?,
        offsetMs: Long,
        chunkEndMs: Long,
    ): List<SpokenWord> {
        val count = minOf(tokens.size, starts.size)
        val words = mutableListOf<SpokenWord>()
        var text = StringBuilder()
        var start = 0L
        var end = 0L
        fun flush() {
            val clean = text.toString().trim()
            if (clean.isNotEmpty() && clean.any { it.isLetterOrDigit() }) {
                words += SpokenWord(start, maxOf(end, start + MIN_WORD_MS).coerceAtMost(maxOf(chunkEndMs, start + 1)), clean)
            } else if (clean.isNotEmpty() && words.isNotEmpty()) {
                // Punctuation on its own (after a pause): it belongs to the word before.
                val last = words.removeAt(words.size - 1)
                words += last.copy(text = last.text + clean)
            }
            text = StringBuilder()
        }
        for (i in 0 until count) {
            val raw = tokens[i].replace(PIECE_SPACE, ' ')
            if (raw.isEmpty()) continue
            val pieceStart = offsetMs + (starts[i] * 1000f).toLong()
            val pieceEnd = when {
                durations != null && i < durations.size && durations[i] > 0f -> pieceStart + (durations[i] * 1000f).toLong()
                i + 1 < count -> offsetMs + (starts[i + 1] * 1000f).toLong()
                else -> pieceStart + LAST_PIECE_MS
            }.coerceAtMost(maxOf(chunkEndMs, pieceStart + 1))
            val startsWord = raw.first().isWhitespace() || text.isEmpty()
            if (startsWord && text.isNotEmpty()) flush()
            if (text.isEmpty()) start = pieceStart
            text.append(raw)
            // A comma or a full stop does not keep the word on screen longer.
            if (raw.any { it.isLetterOrDigit() }) end = pieceEnd
        }
        flush()
        return words
    }

    /** Index of the start of the quietest [frame]-long stretch of [samples] inside from..to (exclusive). */
    fun quietestCut(samples: FloatArray, from: Int, to: Int, frame: Int): Int {
        val first = from.coerceIn(0, samples.size)
        val last = to.coerceIn(first, samples.size)
        if (frame <= 0 || last - first < frame) return last
        var best = last
        var bestEnergy = Double.MAX_VALUE
        var position = first
        while (position + frame <= last) {
            var sum = 0.0
            for (i in position until position + frame) sum += samples[i].toDouble() * samples[i]
            val energy = sqrt(sum / frame)
            // Later cuts win ties: chunks stay as long as allowed.
            if (energy <= bestEnergy) {
                bestEnergy = energy
                best = position
            }
            position += frame
        }
        return best
    }

    /** Spaces of sentence-piece vocabularies. */
    private const val PIECE_SPACE = '\u2581'

    /** How long the last piece of a model without durations lasts. */
    const val LAST_PIECE_MS = 400L

    /** A word is on the clock for at least this long. */
    private const val MIN_WORD_MS = 80L
}

/**
 * The words of every source, moved onto the edited sequence: each word follows
 * its footage through trims, cuts, speed and reversal (as subtitle files do),
 * and disappears with the footage that is cut away. Sorted by start.
 */
fun List<SourceWords>.onTimeline(segments: List<Segment>, reversed: Boolean = false): List<SpokenWord> =
    flatMap { (source, words) ->
        words.flatMap { word ->
            mapToTimeline(word.startMs, word.endMs, segments, source, reversed).map { (from, to) -> SpokenWord(from, to, word.text) }
        }
    }.sortedBy { it.startMs }

/**
 * The words heard in a sound placed on the video, where they are said on the
 * edited sequence (the clock the words of the sources are moved onto): [words]
 * are on the sound file's own clock; [span] plays part of it, maybe faster or
 * slower and several times over, from a moment of the exported video, which
 * runs [wholeSpeed] times as fast as the edited sequence. A word cut by the
 * end of the part ends with it; the words outside it are not said.
 */
fun placedWords(words: List<SpokenWord>, span: SoundSpan, wholeSpeed: Float): List<SpokenWord> {
    val range = span.rangeMs
    if (range <= 0L || words.isEmpty()) return emptyList()
    val rate = span.rate.toDouble()
    val whole = (if (wholeSpeed > 0f) wholeSpeed else 1f).toDouble()
    val inPart = words.filter { it.endMs > span.trimStartMs && it.startMs < span.trimEndMs }
    return (0 until span.loops).flatMap { loop ->
        inPart.map { word ->
            fun sequenceMs(fileMs: Long): Long {
                val played = loop * range + (fileMs.coerceIn(span.trimStartMs, span.trimEndMs) - span.trimStartMs)
                return Math.round((span.startMs + played / rate) * whole)
            }
            SpokenWord(sequenceMs(word.startMs), sequenceMs(word.endMs), word.text)
        }
    }.filter { it.endMs > it.startMs }
}
