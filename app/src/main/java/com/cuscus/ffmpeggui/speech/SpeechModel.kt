package com.cuscus.ffmpeggui.speech

/**
 * A model the app keeps in its own files, imported and checked the same way
 * (see SpeechModelStore): first of all the speech model the smart subtitles
 * run on, NVIDIA's Parakeet TDT 0.6B v3 (CC BY 4.0) as sherpa-onnx converted
 * it to ONNX and quantised it to int8. 25 European languages, found by
 * itself; every word comes with its time.
 *
 * Its three ONNX files are served by this repository's release
 * [PARAKEET_RELEASE_TAG], unchanged from sherpa-onnx's, with the CC BY 4.0
 * legal code, the NOTICE of where they come from and their SHA-256: the
 * places upstream serve whatever was uploaded last, so the files the app was
 * pinned to are kept where they cannot change under it. The token list is in
 * the app (see [TOKENS]). docs/MODELS.md has the whole record.
 *
 * The app never downloads them: the user's browser does, and the files are
 * then imported. Every file is checked against the SHA-256 pinned here before
 * it is used (a damaged model can bring the whole app down inside the native
 * runtime), and again on request.
 *
 * The idea, the model and the way it is run come from the anti-vocale app
 * (github.com/RisorseArtificiali/anti-vocale, Apache 2.0); the code is this
 * app's own.
 */
class SpeechModel internal constructor(
    val name: String,
    /** What the user downloads (in the browser: the app has no network access), in order. */
    val downloads: List<Download>,
    /** Folder of the installed model, inside the app's private files. */
    val folder: String,
    /** Its settings (hashes, when checked), kept apart from every other model's. */
    val prefs: String,
    /** Where an import is put together, next to [folder], until every file matches. */
    val tempFolder: String,
    /** The files the model needs. */
    val files: List<ModelFile>,
    /** About the space it takes once installed (what it really takes is shown once it is). */
    val installedBytes: Long,
    /**
     * True for a model that makes what passes for real and must carry the
     * invisible AI marks (the magic eraser's): it is imported only once the
     * marks are (MarkingGate) and goes without them (MarkingSweep).
     */
    val generative: Boolean = false,
) {

    /** What a file of the model is for. */
    enum class Role { ENCODER, DECODER, JOINER, TOKENS }

    /**
     * A file of the model, with what it must hash to; [bytes] when its size is
     * known. [asset] is where the app carries it itself (in its assets), when
     * it does: it is then not downloaded, and an import that lacks it takes
     * the app's copy (checked against the same SHA-256).
     */
    class ModelFile(val name: String, val sha256: String, val bytes: Long?, val role: Role, val asset: String? = null)

    /** One thing to download: [file] is its name. */
    class Download(val file: String, val url: String)

    fun fileNamed(name: String): ModelFile? = files.firstOrNull { it.name == name }

    companion object {
        const val NAME = "Parakeet TDT 0.6B v3"
        const val MAKER = "NVIDIA"
        const val LICENCE = "CC BY 4.0"

        /** The page of the model (its card and licence). */
        const val MODEL_PAGE_URL = "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3"

        /** The release of this repository that serves the model's files (see docs/MODELS.md). */
        const val PARAKEET_RELEASE_TAG = "parakeet-onnx-1"

        /** Where the files are, with their licence, NOTICE and SHA-256. */
        const val FILES_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/tag/$PARAKEET_RELEASE_TAG"

        private const val DOWNLOAD_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/download/$PARAKEET_RELEASE_TAG"

        /** The languages it understands, as ISO 639-1 codes. */
        val LANGUAGES: List<String> = listOf(
            "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hr", "hu", "it",
            "lv", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk",
        )

        /**
         * The model's token list: a small text file the app carries (browsers
         * tend to show it rather than save it). Kept byte for byte by
         * .gitattributes, so no checkout can change its line ends.
         */
        private val TOKENS = ModelFile(
            "tokens.txt", "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d", 93_939L, Role.TOKENS,
            asset = "speech/parakeet-tdt-0.6b-v3-tokens.txt",
        )

        private val FILES = listOf(
            ModelFile("encoder.int8.onnx", "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247", 652_184_281L, Role.ENCODER),
            ModelFile("decoder.int8.onnx", "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e", 11_845_275L, Role.DECODER),
            ModelFile("joiner.int8.onnx", "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3", 6_355_277L, Role.JOINER),
            TOKENS,
        )

        /** The speech model. */
        val PARAKEET = SpeechModel(
            name = "$NAME int8",
            // The token list is in the app: not downloaded.
            downloads = FILES.filter { it.asset == null }.map { Download(it.name, "$DOWNLOAD_URL/${it.name}") },
            folder = "speech/parakeet-tdt-0.6b-v3-int8",
            prefs = "speech_model",
            tempFolder = "importing",
            files = FILES,
            installedBytes = FILES.sumOf { it.bytes ?: 0L },
        )
    }
}
