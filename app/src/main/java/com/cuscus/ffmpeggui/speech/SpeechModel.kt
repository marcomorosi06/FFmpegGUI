package com.cuscus.ffmpeggui.speech

/** Which build of the speech model: the int8 one of sherpa-onnx, or the SmoothQuant one. */
enum class SpeechVariant { INT8, SMOOTHQUANT }

/**
 * A speech model the smart subtitles run on: NVIDIA's Parakeet TDT 0.6B v3
 * (CC BY 4.0), converted to ONNX and quantised to int8. 25 European languages,
 * found by itself; every word comes with its time. Two builds of it can be
 * installed, the one chosen in the settings is used:
 *
 *  - [INT8]: sherpa-onnx's own, the lighter of the two; it understands a
 *    little less. Three loose files from Hugging Face (quick to import: they
 *    are only copied and checked), or the whole archive from sherpa-onnx's
 *    releases on GitHub, with recordings to measure it on (slow to import:
 *    bzip2 takes minutes to unpack its 670 MB on a phone).
 *  - [SMOOTHQUANT]: quantised with SmoothQuant (by pantinor, on Hugging Face):
 *    three loose files (the token list is in the app), a little larger. It
 *    understands speech better.
 *
 * The app never downloads them: the user's browser does, and the files are
 * then imported. Every file is checked against the SHA-256 pinned here before
 * it is used (a damaged model can bring the whole app down inside the native
 * runtime), and again on request.
 *
 * The idea, the models and the way they are run come from the anti-vocale app
 * (github.com/RisorseArtificiali/anti-vocale, Apache 2.0); the code is this
 * app's own.
 */
class SpeechModel internal constructor(
    /** Which build of the speech model; null for another model kept the same way (see RefineModel). */
    val variant: SpeechVariant?,
    val name: String,
    /** The archive holding the model, when it comes as one. */
    val archive: Archive?,
    /** What the user downloads (in the browser: the app has no network access), in order. */
    val downloads: List<Download>,
    /** Folder of the installed model, inside the app's private files. */
    val folder: String,
    /** Its settings (hashes, when checked), kept apart from the other model's. */
    val prefs: String,
    /** Where an import is put together, next to [folder], until every file matches. */
    val tempFolder: String,
    /** The files the model needs. */
    val files: List<ModelFile>,
    /** The recordings to measure it on (optional files). */
    val samples: List<Sample>,
    /** About the space it takes once installed (what it really takes is shown once it is). */
    val installedBytes: Long,
) {

    /** What a file of the model is for. */
    enum class Role { ENCODER, DECODER, JOINER, TOKENS, SAMPLE }

    /**
     * A file of the model, with what it must hash to; [bytes] when its size is
     * known. [asset] is where the app carries it itself (in its assets), when
     * it does: it is then not downloaded, and an import that lacks it takes
     * the app's copy (checked against the same SHA-256).
     */
    class ModelFile(val name: String, val sha256: String, val bytes: Long?, val role: Role, val asset: String? = null)

    /**
     * A recording that comes with the model, used to measure its speed on this
     * phone; [says] is what is said, to check the result against.
     */
    class Sample(val file: ModelFile, val language: String, val says: String)

    class Archive(val name: String, val sha256: String, val bytes: Long)

    /** One thing to download: [file] is its name. */
    class Download(val file: String, val url: String)

    /** The archive to download instead of the loose files, when there is one. */
    val archiveDownload: Download? get() = archive?.let { Download(it.name, "$ARCHIVE_RELEASES/${it.name}") }

    /** Every file an import looks for: the model, then its recordings. */
    val allFiles: List<ModelFile> = files + samples.map { it.file }

    fun fileNamed(name: String): ModelFile? = allFiles.firstOrNull { it.name == name }

    companion object {
        const val NAME = "Parakeet TDT 0.6B v3"
        const val MAKER = "NVIDIA"
        const val LICENCE = "CC BY 4.0"

        /** The page of the model (its card and licence). */
        const val MODEL_PAGE_URL = "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3"

        /** Where sherpa-onnx's archives are, on GitHub. */
        private const val ARCHIVE_RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

        /** The int8 build's loose files, on Hugging Face (the same files as in the archive). */
        const val INT8_FILES_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"

        /** The page of the SmoothQuant build. */
        const val SMOOTHQUANT_PAGE_URL = "https://huggingface.co/pantinor/parakeet-tdt-0.6b-v3-smoothquant"

        /** The languages it understands, as ISO 639-1 codes. */
        val LANGUAGES: List<String> = listOf(
            "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hr", "hu", "it",
            "lv", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk",
        )

        private val JOINER = ModelFile("joiner.int8.onnx", "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3", 6_355_277L, Role.JOINER)
        /**
         * The model's token list, the same for both builds: a small text file the
         * app carries (browsers tend to show it rather than save it).
         */
        private val TOKENS = ModelFile(
            "tokens.txt", "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d", 93_939L, Role.TOKENS,
            asset = "speech/parakeet-tdt-0.6b-v3-tokens.txt",
        )

        private val SAMPLES: List<Sample> = listOf(
            Sample(
                ModelFile("en.wav", "148b936b43ce7c546a866e64da059f0458aee2d65e617f16e9d94f06e8d99ed6", 184_608L, Role.SAMPLE),
                "en", "Ask not what your country can do for you, ask what you can do for your country.",
            ),
            Sample(
                ModelFile("de.wav", "36d3c4845b9808a1656a2a2e92d884590e2db94389e6fe559643291ae0cd3710", 121_388L, Role.SAMPLE),
                "de", "Alles hat ein Ende, nur die Wurst hat zwei.",
            ),
            Sample(
                ModelFile("fr.wav", "b59be4349b92d344fb903677165eaf4694025d1ab119c608726ecbcb3164b528", 219_180L, Role.SAMPLE),
                "fr", "Ne vous demandez pas ce que votre pays peut faire pour vous. Demandez-vous plut\u00F4t ce que vous pouvez faire pour lui.",
            ),
            Sample(
                ModelFile("es.wav", "49fd2cfa4b62db7068143c582b35de9d31ec2733495ece3611105131d21de06c", 235_052L, Role.SAMPLE),
                "es", "No preguntes qu\u00E9 puede hacer tu pa\u00EDs por ti, pregunta qu\u00E9 puedes hacer t\u00FA por tu pa\u00EDs.",
            ),
        )

        private const val INT8_ARCHIVE = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2"

        private val INT8_FILES = listOf(
            ModelFile("encoder.int8.onnx", "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247", 652_184_281L, Role.ENCODER),
            ModelFile("decoder.int8.onnx", "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e", 11_845_275L, Role.DECODER),
            JOINER,
            TOKENS,
        )

        val INT8 = SpeechModel(
            variant = SpeechVariant.INT8,
            name = "int8 (sherpa-onnx)",
            archive = Archive(INT8_ARCHIVE, "5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf", 487_170_055L),
            // "?download=true" makes Hugging Face send the file as an attachment.
            // The token list is in the app: not downloaded.
            downloads = INT8_FILES.filter { it.asset == null }
                .map { Download(it.name, "$INT8_FILES_URL/resolve/main/${it.name}?download=true") },
            folder = "speech/parakeet-tdt-0.6b-v3-int8",
            prefs = "speech_model",
            tempFolder = "importing",
            files = INT8_FILES,
            samples = SAMPLES,
            installedBytes = (INT8_FILES + SAMPLES.map { it.file }).sumOf { it.bytes ?: 0L },
        )

        private val SMOOTHQUANT_FILES = listOf(
            ModelFile("encoder.int8.onnx", "1db13b37849829eecce166bf2e1c7d4d334ceed9e3b9b95c515188dd870301ae", null, Role.ENCODER),
            ModelFile("decoder.int8.onnx", "0018457edb28af7715cfd86ae47a6d67775492d7d32de58a41e7aa09305121e1", null, Role.DECODER),
            JOINER,
            TOKENS,
        )

        val SMOOTHQUANT = SpeechModel(
            variant = SpeechVariant.SMOOTHQUANT,
            name = "SmoothQuant",
            archive = null,
            // "?download=true" makes Hugging Face send the file as an attachment.
            // The token list is in the app: not downloaded.
            downloads = SMOOTHQUANT_FILES.filter { it.asset == null }
                .map { Download(it.name, "$SMOOTHQUANT_PAGE_URL/resolve/main/${it.name}?download=true") },
            folder = "speech/parakeet-tdt-0.6b-v3-smoothquant",
            prefs = "speech_model_smoothquant",
            tempFolder = "importing-smoothquant",
            files = SMOOTHQUANT_FILES,
            // The int8 archive's recordings, when the user picks them too.
            samples = SAMPLES,
            installedBytes = 862L * 1024 * 1024,
        )

        fun of(variant: SpeechVariant): SpeechModel = when (variant) {
            SpeechVariant.INT8 -> INT8
            SpeechVariant.SMOOTHQUANT -> SMOOTHQUANT
        }
    }
}
