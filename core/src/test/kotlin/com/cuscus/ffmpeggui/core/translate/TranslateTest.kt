package com.cuscus.ffmpeggui.core.translate

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class TranslateTest {

    private val sp = SentencePieceModel.SPACE

    /** A SentencePiece model file, written as the real ones are (pieces as field 1). */
    private fun spm(vararg pieces: Triple<String, Float, Int>): ByteArray {
        val out = ByteArrayOutputStream()
        fun varint(v: Long, to: ByteArrayOutputStream) {
            var x = v
            while (true) {
                if (x and 0x7FL.inv() == 0L) {
                    to.write(x.toInt())
                    return
                }
                to.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
        }
        for ((text, score, type) in pieces) {
            val piece = ByteArrayOutputStream()
            val bytes = text.toByteArray(Charsets.UTF_8)
            varint((1 shl 3 or 2).toLong(), piece); varint(bytes.size.toLong(), piece); piece.write(bytes)
            varint((2 shl 3 or 5).toLong(), piece)
            val bits = score.toRawBits()
            for (k in 0 until 4) piece.write((bits ushr (8 * k)) and 0xFF)
            varint((3 shl 3 or 0).toLong(), piece); varint(type.toLong(), piece)
            val body = piece.toByteArray()
            varint((1 shl 3 or 2).toLong(), out); varint(body.size.toLong(), out); out.write(body)
        }
        // A trainer spec after the pieces (field 2), passed over.
        varint((2 shl 3 or 2).toLong(), out); varint(2, out); out.write(byteArrayOf(8, 1))
        return out.toByteArray()
    }

    private val model = SentencePieceModel.parse(
        spm(
            Triple("<unk>", 0f, 2), Triple("<s>", 0f, 3),
            Triple("${sp}hello", -1f, 1), Triple("${sp}he", -2f, 1), Triple("llo", -2f, 1),
            Triple(sp, -5f, 1), Triple("h", -6f, 1), Triple("e", -6f, 1), Triple("l", -6f, 1), Triple("o", -6f, 1),
            Triple("${sp}world", -1.5f, 1), Triple("${sp}mondo", -1.5f, 1), Triple("${sp}ciao", -1f, 1),
        ),
    )

    private val vocab = mapOf(
        "</s>" to 0, "<unk>" to 1, "${sp}hello" to 2, "${sp}world" to 3, "${sp}ciao" to 4, "${sp}mondo" to 5,
        ">>ita<<" to 6, ">>fra<<" to 7, "<pad>" to 8, sp to 9, "h" to 10,
    )

    private val config = MarianConfig.parse(
        """{"_name_or_path":"Helsinki-NLP/opus-mt-en-it","model_type":"marian","eos_token_id":0,"pad_token_id":8,
           "decoder_start_token_id":8,"vocab_size":11,"bad_words_ids":[[8]],"max_length":512}""",
    )

    @Test
    fun `a sentence is cut into the pieces whose scores add up highest`() {
        assertEquals(listOf("${sp}hello", "${sp}world"), model.encode("  hello \n world "))
        // A letter no piece holds stands alone.
        assertEquals(listOf("${sp}hello", sp, "\u00E9"), model.encode("hello \u00E9"))
        assertTrue(model.encode("   ").isEmpty())
    }

    @Test
    fun `ids go in with the end of the sentence and the target's token, and come back as words`() {
        val tok = MarianTokenizer(model, vocab, config)
        assertArrayEquals(longArrayOf(2, 3, 0), tok.encode("hello world"))
        assertEquals(mapOf("it" to ">>ita<<", "fr" to ">>fra<<"), tok.targetTokens)
        assertArrayEquals(longArrayOf(6, 2, 0), tok.encode("hello", target = "it"))
        assertEquals("ciao mondo", tok.decode(intArrayOf(8, 4, 5, 0)))
        assertEquals("Helsinki-NLP/opus-mt-en-it", config.name)
        // Without config.json, the vocabulary says the same of the tokens.
        val guessed = MarianConfig.fromVocab(vocab)
        assertEquals(config.eosId, guessed.eosId)
        assertEquals(config.padId, guessed.padId)
        assertEquals(config.decoderStartId, guessed.decoderStartId)
        assertEquals(config.badIds, guessed.badIds)
        assertEquals(8, config.decoderStartId)
        assertTrue(8 in config.badIds)
    }

    @Test
    fun `the greedy search stops at the end, never writes padding and gives up on a stuck word`() {
        val wanted = intArrayOf(4, 5)
        val ids = GreedyDecoder.decode(config, 20) { sofar ->
            FloatArray(11) { i ->
                val step = sofar.size - 1
                when {
                    i == 8 -> 100f
                    step < wanted.size && i == wanted[step] -> 10f
                    step >= wanted.size && i == 0 -> 10f
                    else -> 0f
                }
            }
        }
        assertArrayEquals(wanted, ids)
        val stuck = GreedyDecoder.decode(config, 50) { FloatArray(11) { i -> if (i == 4) 5f else 0f } }
        assertEquals(GreedyDecoder.MAX_REPEAT - 1, stuck.size)
    }

    @Test
    fun `models are found by their names, and a way through English when none goes straight`() {
        assertEquals("en" to "it", Languages.pairOf("Helsinki-NLP/opus-mt-en-it"))
        assertEquals("en" to "ROMANCE", Languages.pairOf("Xenova/opus-mt-tc-big-en-ROMANCE"))
        assertNull(Languages.pairOf("t5-small"))
        assertEquals("it", Languages.normalize("ita"))
        assertEquals("fr", Languages.normalize("fra"))
        assertTrue(Languages.isGroup("mul"))
        assertTrue(Languages.isGroup("ROMANCE"))
        val models = listOf(
            TranslationAbility("en-it", setOf("en"), setOf("it"), false),
            TranslationAbility("it-en", setOf("it"), setOf("en"), false),
            TranslationAbility("en-de", setOf("en"), setOf("de"), false),
            TranslationAbility("mul-en", emptySet(), setOf("en"), false),
        )
        assertEquals(listOf(TranslationStep("en-it", "en", "it")), TranslationRoute.plan(models, "en", "it"))
        assertEquals(
            listOf(TranslationStep("it-en", "it", "en"), TranslationStep("en-de", "en", "de")),
            TranslationRoute.plan(models, "it", "de"),
        )
        // Not knowing the language, only a model that reads any will do.
        assertEquals(listOf(TranslationStep("mul-en", null, "en")), TranslationRoute.plan(models, null, "en"))
        assertNull(TranslationRoute.plan(models, "de", "fr"))
        assertEquals(setOf("en", "de"), TranslationRoute.reachable(models, "it"))
    }

    @Test
    fun `the language of subtitles is guessed from their words or their alphabet`() {
        assertEquals("en", LanguageGuess.guess(listOf("What are you doing here?", "I don't know, but it is not what you think."))?.language)
        assertEquals("it", LanguageGuess.guess(listOf("Che cosa fai qui?", "Non lo so, ma non \u00E8 come pensi tu."))?.language)
        assertEquals("de", LanguageGuess.guess(listOf("Was machst du hier?", "Ich wei\u00DF es nicht, aber es ist nicht so."))?.language)
        assertEquals("ru", LanguageGuess.guess(listOf("\u0427\u0442\u043E \u0442\u044B \u0437\u0434\u0435\u0441\u044C \u0434\u0435\u043B\u0430\u0435\u0448\u044C?"))?.language)
        assertNull(LanguageGuess.guess(listOf("12345 !!!")))
    }

    @Test
    fun `a SubRip file keeps its numbers and times, its words translated`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,500\n<i>Hello</i>\nworld\n\n2\n00:00:03,000 --> 00:00:04,000\nBye\n"
        assertEquals(listOf("Hello world", "Bye"), SubtitleTranslation.texts(srt))
        val out = SubtitleTranslation.rewrite(srt, listOf("Ciao mondo", "Addio"))
        assertEquals("1\n00:00:01,000 --> 00:00:02,500\nCiao mondo\n\n2\n00:00:03,000 --> 00:00:04,000\nAddio\n", out)
        // A long line is broken near its middle.
        val long = SubtitleTranslation.rewrite(srt, listOf("Questa \u00E8 una frase molto lunga che va spezzata in due", "Addio"))
        assertTrue(long.contains("Questa \u00E8 una frase molto lunga\nche va spezzata in due") || long.contains("Questa \u00E8 una frase molto\nlunga che va spezzata in due"))
    }

    @Test
    fun `a SubStation Alpha file keeps every field and the tags that place a line`() {
        val ass = """
            [Script Info]
            Title: x

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\an8}Hello, {\i1}world{\i0}
            Comment: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,not this
        """.trimIndent()
        assertEquals(listOf("Hello, world"), SubtitleTranslation.texts(ass))
        val out = SubtitleTranslation.rewrite(ass, listOf("Ciao, mondo"))
        assertTrue(out, out.contains("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}Ciao, mondo"))
        assertTrue(out.contains("Comment: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,not this"))
        assertTrue(out.startsWith("[Script Info]\nTitle: x"))
    }
}
