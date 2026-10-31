package com.cuscus.ffmpeggui.core.translate

import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.JsonText
import com.cuscus.ffmpeggui.core.project.asLong

/*
 * The text side of an Opus-MT model (Marian, as Hugging Face ships it): the
 * pieces of the source language's SentencePiece model looked up in the
 * model's vocabulary, the end-of-sentence token after them; on the way back,
 * the pieces of the ids the model gave, joined, their spaces put back.
 */

/** What the model's config.json says of its tokens and its length. */
data class MarianConfig(
    /** "Helsinki-NLP/opus-mt-en-it": where it comes from (its languages are in it). */
    val name: String?,
    val eosId: Int,
    val padId: Int,
    val decoderStartId: Int,
    val vocabSize: Int,
    /** Ids never to be written (the padding, mostly). */
    val badIds: Set<Int>,
    val maxLength: Int,
) {
    companion object {
        /**
         * What config.json would say, from the vocabulary alone (as Opus-MT's
         * models all have it): the end of a sentence is "</s>", the padding
         * "<pad>", which also starts the decoder and is never written.
         */
        fun fromVocab(vocab: Map<String, Int>): MarianConfig {
            val pad = vocab["<pad>"] ?: (vocab.values.maxOrNull() ?: 0)
            return MarianConfig(
                name = null,
                eosId = vocab["</s>"] ?: 0,
                padId = pad,
                decoderStartId = pad,
                vocabSize = (vocab.values.maxOrNull() ?: -1) + 1,
                badIds = setOf(pad),
                maxLength = 512,
            )
        }

        /** Reads config.json; an error when it is not a Marian model's. */
        fun parse(text: String): MarianConfig {
            val o = JsonText.parse(text) as? Json.Obj ?: throw IllegalArgumentException("config.json is not an object")
            val type = o.stringOrNull("model_type")
            require(type == null || type == "marian") { "Not a Marian model: $type" }
            val pad = o.int("pad_token_id", 0)
            val bad = HashSet<Int>()
            (o["bad_words_ids"] as? Json.Arr)?.items?.forEach { group ->
                (group as? Json.Arr)?.items?.singleOrNull()?.asLong()?.let { bad += it.toInt() }
            }
            bad += pad
            return MarianConfig(
                name = o.stringOrNull("_name_or_path"),
                eosId = o.int("eos_token_id", 0),
                padId = pad,
                decoderStartId = o.int("decoder_start_token_id", pad),
                vocabSize = o.int("vocab_size", o.int("decoder_vocab_size", 0)),
                badIds = bad,
                maxLength = o.int("max_length", 512).coerceIn(16, 1024),
            )
        }
    }
}

class MarianTokenizer(
    private val source: SentencePieceModel,
    /** Every token's id (vocab.json). */
    private val vocab: Map<String, Int>,
    val config: MarianConfig,
) {
    private val pieces: Array<String?> = arrayOfNulls<String>((vocab.values.maxOrNull() ?: -1) + 1).also { array ->
        for ((piece, id) in vocab) if (id >= 0) array[id] = piece
    }

    private val unknownId: Int = vocab["<unk>"] ?: 1

    /**
     * The target languages the model writes, when it writes several: the
     * tokens like ">>ita<<" its vocabulary has (one goes before the text), by
     * the language each names (as a two-letter code where there is one).
     */
    val targetTokens: Map<String, String> = vocab.keys.mapNotNull { token ->
        TARGET_TOKEN.matchEntire(token)?.let { Languages.normalize(it.groupValues[1]) to token }
    }.toMap()

    /**
     * The ids of [text], for the encoder: [target]'s token first when the model
     * writes several languages, then the text's pieces, then the end of the
     * sentence. At most [maxTokens] in all.
     */
    fun encode(text: String, target: String? = null, maxTokens: Int = 256): LongArray {
        val out = ArrayList<Long>()
        target?.let { lang -> targetTokens[Languages.normalize(lang)]?.let { token -> vocab[token]?.let { out += it.toLong() } } }
        for (piece in source.encode(text)) {
            if (out.size >= maxTokens - 1) break
            out += (vocab[piece] ?: unknownId).toLong()
        }
        out += config.eosId.toLong()
        return out.toLongArray()
    }

    /** The text the model wrote as [ids]: special tokens left out, its spaces put back. */
    fun decode(ids: IntArray): String {
        val text = StringBuilder()
        for (id in ids) {
            if (id == config.eosId || id == config.padId || id == unknownId) continue
            val piece = pieces.getOrNull(id) ?: continue
            if (TARGET_TOKEN.matches(piece) || (piece.startsWith("<") && piece.endsWith(">"))) continue
            text.append(piece)
        }
        return text.toString().replace(SentencePieceModel.SPACE, " ").replace(Regex(" +"), " ").trim()
    }

    companion object {
        private val TARGET_TOKEN = Regex(">>([A-Za-z_]+)<<")

        /** Reads vocab.json: every token and its id. */
        fun vocab(text: String): Map<String, Int> {
            val o = JsonText.parse(text) as? Json.Obj ?: throw IllegalArgumentException("vocab.json is not an object")
            val out = HashMap<String, Int>(o.fields.size * 2)
            for ((token, value) in o.fields) value.asLong()?.let { out[token] = it.toInt() }
            require(out.isNotEmpty()) { "Empty vocabulary" }
            return out
        }
    }
}

/**
 * The model's words one at a time, each the likeliest (greedy search): [next]
 * gives, for the ids written so far (the decoder's start first), the scores
 * of every id for the next one. Ends at the end-of-sentence id, after
 * [maxNew] ids, or when the same id comes [MAX_REPEAT] times in a row (a
 * model stuck on a word). The ids written, without the start.
 */
object GreedyDecoder {

    const val MAX_REPEAT = 4

    fun decode(config: MarianConfig, maxNew: Int, next: (IntArray) -> FloatArray): IntArray {
        val out = ArrayList<Int>()
        out += config.decoderStartId
        var run = 0
        repeat(maxNew) {
            val scores = next(out.toIntArray())
            var best = -1
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in scores.indices) {
                if (i in config.badIds) continue
                val s = scores[i]
                if (s > bestScore) {
                    bestScore = s
                    best = i
                }
            }
            if (best < 0 || best == config.eosId) return out.drop(1).toIntArray()
            run = if (best == out.last()) run + 1 else 1
            if (run >= MAX_REPEAT) return out.drop(1).toIntArray()
            out += best
        }
        return out.drop(1).toIntArray()
    }
}
