package com.cuscus.ffmpeggui.translate

import com.cuscus.ffmpeggui.core.translate.Languages
import com.cuscus.ffmpeggui.core.translate.TranslationAbility

/**
 * The models that translate subtitles: Opus-MT, of the University of Helsinki
 * (Jorg Tiedemann and others, CC BY 4.0), each from one language to another
 * (or from many, or to many), as converted to ONNX for transformers.js (by
 * Xenova, on Hugging Face). Four files each: its vocabulary, the source
 * language's SentencePiece model, and the encoder and the decoder, quantised
 * (its config.json is taken when given, else its vocabulary says enough).
 *
 * As with the other models the app never downloads them: the user's browser
 * does, and the files are imported. A model of any pair can be added (its
 * name on Hugging Face is enough to find its files); its files' SHA-256 are
 * worked out when it is imported and checked again on request.
 */
object OpusMt {

    const val NAME = "Opus-MT"
    const val MAKER = "University of Helsinki"
    const val LICENCE = "CC BY 4.0"

    /** The project's page (its models, papers and licence). */
    const val PROJECT_URL = "https://github.com/Helsinki-NLP/Opus-MT"

    /** Where the ONNX conversions are. */
    const val HUB = "https://huggingface.co"

    /** What a file of a model is for; [optional] when the model works without it (see MarianConfig.fromVocab). */
    enum class Kind(val fileName: String, val path: String, val optional: Boolean = false) {
        CONFIG("config.json", "config.json", optional = true),
        VOCAB("vocab.json", "vocab.json"),
        SOURCE("source.spm", "source.spm"),
        ENCODER("encoder_model_quantized.onnx", "onnx/encoder_model_quantized.onnx"),
        DECODER("decoder_model_quantized.onnx", "onnx/decoder_model_quantized.onnx"),
        ;

        companion object {
            /**
             * The kind of a file by its name as the browser saved it (a second
             * download of a name becomes "config (1).json"): null for none.
             */
            fun of(name: String): Kind? {
                val n = name.lowercase()
                return when {
                    // A browser that shows a .json file may save it as text.
                    n.startsWith("config") && (n.endsWith(".json") || n.endsWith(".txt")) -> CONFIG
                    n.startsWith("vocab") && (n.endsWith(".json") || n.endsWith(".txt")) -> VOCAB
                    n.startsWith("source") && n.endsWith(".spm") -> SOURCE
                    n.startsWith("encoder_model") && n.endsWith(".onnx") -> ENCODER
                    n.startsWith("decoder_model") && n.endsWith(".onnx") && "past" !in n && "merged" !in n -> DECODER
                    else -> null
                }
            }
        }
    }

    /** One file to download: its name and its link. */
    class Download(val kind: Kind, val url: String)

    /** The files of the model [repo] ("Xenova/opus-mt-en-it") to download. */
    fun downloads(repo: String): List<Download> =
        Kind.entries.filter { !it.optional }.map { kind ->
            val base = "$HUB/${repo.trim().trim('/')}"
            // A browser on a phone shows a .json file rather than saving it, whatever the
            // server says: its page on Hugging Face has a download button that saves it.
            val url = if (kind.fileName.endsWith(".json")) "$base/blob/main/${kind.path}" else "$base/resolve/main/${kind.path}?download=true"
            Download(kind, url)
        }

    /** The page of the model [repo]. */
    fun page(repo: String): String = "$HUB/${repo.trim().trim('/')}"

    /** Whether [text] looks like a model's name on Hugging Face ("owner/opus-mt-xx-yy"). */
    fun isRepo(text: String): Boolean = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(text.trim()) && Languages.pairOf(text) != null

    /**
     * Models offered in the settings, each a pair converted for transformers.js:
     * from and to English for the commonest languages, a few between others,
     * and two that read or write many.
     */
    val SUGGESTED: List<String> = listOf(
        "en-it", "it-en", "en-es", "es-en", "en-fr", "fr-en", "en-de", "de-en",
        "en-nl", "nl-en", "en-ro", "en-ru", "ru-en", "en-sv", "sv-en", "en-fi", "fi-en",
        "en-zh", "zh-en", "en-ar", "ar-en", "en-hi", "hi-en", "en-af", "af-en",
        "it-es", "es-it", "es-fr", "fr-es", "de-fr", "fr-de", "de-es", "es-de", "ru-uk", "uk-ru",
        "mul-en", "en-mul",
    ).map { "Xenova/opus-mt-$it" }
}

/** A model on the phone. */
data class InstalledModel(
    /** Its folder's name, and its id: made of its name on Hugging Face. */
    val id: String,
    /** "Xenova/opus-mt-en-it". */
    val repo: String,
    /** The languages it reads (empty: any) and writes, as two-letter codes where there are some. */
    val sources: Set<String>,
    val targets: Set<String>,
    /** It writes several languages and must be told which. */
    val needsToken: Boolean,
    val bytes: Long,
    /** Each file's SHA-256 when it was imported. */
    val hashes: Map<String, String>,
    val importedAt: Long,
) {
    val ability: TranslationAbility get() = TranslationAbility(id, sources, targets, needsToken)
}
