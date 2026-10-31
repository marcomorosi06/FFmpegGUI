package com.cuscus.ffmpeggui.core.translate

import java.text.Normalizer

/*
 * SentencePiece, the way Opus-MT cuts text into pieces before it is
 * translated: the model's file (a protocol buffer: every piece with its score
 * and kind) read by hand, and the unigram model's cut, the one whose pieces'
 * scores add up highest (Viterbi). Only what Opus-MT's models use is here.
 */

/** A SentencePiece model: its pieces, each with its log-probability [Piece.score] and [Piece.type]. */
class SentencePieceModel(val pieces: List<Piece>) {

    /** One piece; [type] as SentencePiece numbers them (1 normal, 2 unknown, 3 control, 4 user defined, 5 unused, 6 byte). */
    data class Piece(val text: String, val score: Float, val type: Int)

    /** The pieces a text may be cut into (normal and user-defined ones), by their text. */
    private val usable: Map<String, Float> = HashMap<String, Float>().apply {
        for (p in pieces) if ((p.type == NORMAL || p.type == USER_DEFINED) && p.text.isNotEmpty()) putIfAbsent(p.text, p.score)
    }

    private val longest: Int = usable.keys.maxOfOrNull { it.length } ?: 1

    /** What a piece the model does not know costs: well below the least likely one it knows. */
    private val unknownScore: Float = (pieces.minOfOrNull { it.score } ?: 0f) - UNKNOWN_PENALTY

    /**
     * [text] cut into pieces as the model cuts it: normalised (NFKC, spaces
     * made one), each space a "\u2581" with one before the first word, then
     * the cut whose pieces' scores add up highest. A character no piece
     * covers is a piece of its own (it maps to the unknown token).
     */
    fun encode(text: String): List<String> {
        val s = normalize(text)
        if (s.isEmpty()) return emptyList()
        val n = s.length
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val from = IntArray(n + 1) { -1 }
        best[0] = 0.0
        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue
            var matched = false
            val max = minOf(longest, n - i)
            for (len in 1..max) {
                val score = usable[s.substring(i, i + len)] ?: continue
                matched = true
                val total = best[i] + score
                if (total > best[i + len]) {
                    best[i + len] = total
                    from[i + len] = i
                }
            }
            // No piece starts with this character: it stands alone (a whole code point).
            val step = if (Character.isHighSurrogate(s[i]) && i + 1 < n) 2 else 1
            if (!matched || usable[s.substring(i, i + step)] == null) {
                val total = best[i] + unknownScore
                if (total > best[i + step]) {
                    best[i + step] = total
                    from[i + step] = i
                }
            }
        }
        val out = ArrayList<String>()
        var at = n
        while (at > 0) {
            val start = from[at]
            if (start < 0) break
            out += s.substring(start, at)
            at = start
        }
        out.reverse()
        return out
    }

    /** Whether [piece] is one of the model's own (not a character it does not know). */
    fun knows(piece: String): Boolean = piece in usable

    companion object {
        const val NORMAL = 1
        const val UNKNOWN = 2
        const val CONTROL = 3
        const val USER_DEFINED = 4

        /** The space as SentencePiece writes it. */
        const val SPACE = "\u2581"

        private const val UNKNOWN_PENALTY = 10f

        /** As SentencePiece's "nmt_nfkc" rule does it, nearly: NFKC, control characters out, spaces made one. */
        fun normalize(text: String): String {
            val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
            val clean = StringBuilder(nfkc.length)
            for (c in nfkc) {
                when {
                    c == '\t' || c == '\n' || c == '\r' || c.isWhitespace() -> clean.append(' ')
                    Character.isISOControl(c) -> Unit
                    else -> clean.append(c)
                }
            }
            val words = clean.toString().trim().split(Regex(" +")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return ""
            return SPACE + words.joinToString(SPACE)
        }

        /** Reads a SentencePiece model file (.spm / .model). */
        fun parse(bytes: ByteArray): SentencePieceModel {
            val pieces = ArrayList<Piece>()
            val top = ProtoReader(bytes, 0, bytes.size)
            while (top.more()) {
                val (field, wire) = top.tag()
                if (field == 1 && wire == 2) {
                    val (start, end) = top.delimited()
                    pieces += piece(ProtoReader(bytes, start, end))
                } else {
                    top.skip(wire)
                }
            }
            require(pieces.isNotEmpty()) { "No pieces in the SentencePiece model" }
            return SentencePieceModel(pieces)
        }

        private fun piece(r: ProtoReader): Piece {
            var text = ""
            var score = 0f
            var type = NORMAL
            while (r.more()) {
                val (field, wire) = r.tag()
                when {
                    field == 1 && wire == 2 -> text = r.string()
                    field == 2 && wire == 5 -> score = Float.fromBits(r.fixed32())
                    field == 3 && wire == 0 -> type = r.varint().toInt()
                    else -> r.skip(wire)
                }
            }
            return Piece(text, score, type)
        }
    }
}

/** Just enough of the protocol buffer wire format to read a SentencePiece model. */
internal class ProtoReader(private val bytes: ByteArray, private var at: Int, private val end: Int) {

    fun more(): Boolean = at < end

    fun varint(): Long {
        var shift = 0
        var value = 0L
        while (true) {
            require(at < end) { "Truncated varint" }
            val b = bytes[at++].toInt() and 0xFF
            value = value or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
            require(shift < 64) { "Varint too long" }
        }
    }

    /** The next field's number and wire type. */
    fun tag(): Pair<Int, Int> {
        val t = varint()
        return (t ushr 3).toInt() to (t and 7).toInt()
    }

    fun fixed32(): Int {
        require(at + 4 <= end) { "Truncated fixed32" }
        val v = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)
        at += 4
        return v
    }

    /** Where a length-delimited field lies: its start and end; it is passed over. */
    fun delimited(): Pair<Int, Int> {
        val length = varint().toInt()
        require(length >= 0 && at + length <= end) { "Truncated field" }
        val start = at
        at += length
        return start to at
    }

    fun string(): String {
        val (start, stop) = delimited()
        return String(bytes, start, stop - start, Charsets.UTF_8)
    }

    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> at += 8
            2 -> delimited()
            5 -> at += 4
            else -> throw IllegalArgumentException("Unknown wire type $wire")
        }
        require(at <= end) { "Truncated field" }
    }
}
