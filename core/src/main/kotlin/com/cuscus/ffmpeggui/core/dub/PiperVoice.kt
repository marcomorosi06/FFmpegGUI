package com.cuscus.ffmpeggui.core.dub

import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.JsonText
import java.io.ByteArrayOutputStream

/**
 * A Piper voice (VITS, rhasspy/piper and its successors, MIT), as Piper
 * writes it: an ONNX network and its ".onnx.json" settings. sherpa-onnx
 * runs it once the network carries a few facts about itself (its metadata:
 * that it is Piper's, its eSpeak NG voice, its rate, its speakers) and the
 * phoneme table is a token list: both are made here from the settings, so
 * that any Piper voice, one of the many published or one trained at home,
 * can be imported as it is.
 */
data class PiperVoiceConfig(
    /** eSpeak NG's voice for its words ("it", "en-us"). */
    val espeak: String,
    /** Its language: two letters ("it") and the full code ("it_IT"). */
    val languageCode: String,
    val languageTag: String,
    /** The language's name in English ("Italian"), which sherpa-onnx wants too. */
    val languageName: String,
    val sampleRate: Int,
    /** Its speakers, by number: their names when it has several ("0" for one). */
    val speakers: List<String>,
    val noiseScale: Float,
    val lengthScale: Float,
    val noiseW: Float,
    /** Its phoneme table, symbol to number, in order. */
    val phonemes: List<Pair<String, Int>>,
    /** What it says of where its recordings come from (a name, a link), when it does. */
    val dataset: String?,
    /** "low", "medium", "high"... */
    val quality: String?,
) {
    /** sherpa-onnx's token list: "symbol number" a line (the line break left out, as its script does). */
    fun tokens(): String = buildString {
        for ((symbol, id) in phonemes) {
            if (symbol == "\n") continue
            append(symbol).append(' ').append(id).append('\n')
        }
    }

    /** The facts sherpa-onnx reads from the network (see its scripts/piper/add_meta_data.py). */
    fun metadata(): Map<String, String> = linkedMapOf(
        "model_type" to "vits",
        "comment" to "piper",
        "language" to languageName,
        "voice" to espeak,
        "version" to "1",
        "has_espeak" to "1",
        "has_g2pw" to "0",
        "n_speakers" to speakers.size.toString(),
        "sample_rate" to sampleRate.toString(),
    )

    companion object {
        /** The settings in [text] (an ".onnx.json" file); null when they are not a Piper voice's. */
        fun parse(text: String): PiperVoiceConfig? {
            val root = runCatching { JsonText.parse(text) }.getOrNull() as? Json.Obj ?: return null
            val ids = root.obj("phoneme_id_map") ?: return null
            val phonemes = ids.fields.mapNotNull { (symbol, value) ->
                val id = when (value) {
                    is Json.Arr -> (value.items.firstOrNull() as? Json.Num)?.text?.toIntOrNull()
                    is Json.Num -> value.text.toIntOrNull()
                    else -> null
                } ?: return@mapNotNull null
                symbol to id
            }
            if (phonemes.isEmpty()) return null
            // Piper writes "phoneme_type": "pinyin" for voices that need another front end: not taken.
            if (root.string("phoneme_type", "espeak") != "espeak") return null
            val language = root.obj("language")
            var espeak = root.stringOrNull("lang_code") ?: root.obj("espeak")?.stringOrNull("voice") ?: return null
            if (espeak == "pt-PT") espeak = "pt"
            val tag = language?.stringOrNull("code") ?: espeak.replace('-', '_')
            var rate = root.obj("audio")?.int("sample_rate", 22_050) ?: 22_050
            if (rate == 22_500) rate = 22_050
            val count = root.int("num_speakers", 1).coerceAtLeast(1)
            val named = root.obj("speaker_id_map")?.fields?.mapNotNull { (name, value) ->
                (value as? Json.Num)?.text?.toIntOrNull()?.let { it to name }
            }?.toMap().orEmpty()
            val inference = root.obj("inference")
            val dataset = root.stringOrNull("dataset")
            return PiperVoiceConfig(
                espeak = espeak,
                languageCode = (language?.stringOrNull("family") ?: tag.substringBefore('_')).lowercase().take(2),
                languageTag = tag,
                languageName = language?.stringOrNull("name_english") ?: tag,
                sampleRate = rate,
                speakers = (0 until count).map { named[it] ?: it.toString() },
                noiseScale = inference?.double("noise_scale", 0.667)?.toFloat() ?: 0.667f,
                lengthScale = inference?.double("length_scale", 1.0)?.toFloat() ?: 1f,
                noiseW = inference?.double("noise_w", 0.8)?.toFloat() ?: 0.8f,
                phonemes = phonemes,
                dataset = dataset?.takeIf { it.isNotBlank() },
                quality = root.obj("audio")?.stringOrNull("quality"),
            )
        }
    }
}

/**
 * Metadata for an ONNX file, written as more of the file: an ONNX model is a
 * protocol buffer (ModelProto), whose repeated field 14, metadata_props, can
 * be appended to the end of it; a reader merges what it finds. So a network
 * gets its metadata without being read into memory and written again.
 */
object OnnxMetadata {

    /** The bytes to put after an ONNX file for it to carry [props] too. */
    fun append(props: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((key, value) in props) {
            val entry = ByteArrayOutputStream()
            field(entry, 1, key.toByteArray(Charsets.UTF_8))
            field(entry, 2, value.toByteArray(Charsets.UTF_8))
            field(out, METADATA_PROPS, entry.toByteArray())
        }
        return out.toByteArray()
    }

    /**
     * The metadata of an ONNX file's bytes [model] (the fields 14 at its top
     * level): what [append] wrote, or what the file had.
     */
    fun read(model: ByteArray): Map<String, String> {
        val found = linkedMapOf<String, String>()
        var at = 0
        while (at < model.size) {
            val (tag, afterTag) = varint(model, at)
            at = afterTag
            val number = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> at = varint(model, at).second
                1 -> at += 8
                5 -> at += 4
                2 -> {
                    val (length, afterLength) = varint(model, at)
                    val end = afterLength + length.toInt()
                    if (number == METADATA_PROPS) {
                        var key = ""
                        var value = ""
                        var p = afterLength
                        while (p < end) {
                            val (t, a) = varint(model, p)
                            val (l, b) = varint(model, a)
                            val text = String(model, b, l.toInt(), Charsets.UTF_8)
                            if ((t ushr 3).toInt() == 1) key = text else if ((t ushr 3).toInt() == 2) value = text
                            p = b + l.toInt()
                        }
                        found[key] = value
                    }
                    at = end
                }
                else -> return found
            }
        }
        return found
    }

    private const val METADATA_PROPS = 14

    private fun field(out: ByteArrayOutputStream, number: Int, bytes: ByteArray) {
        writeVarint(out, (number.toLong() shl 3) or 2L)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    private fun varint(bytes: ByteArray, from: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var at = from
        while (true) {
            val b = bytes[at++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result to at
            shift += 7
        }
    }
}
