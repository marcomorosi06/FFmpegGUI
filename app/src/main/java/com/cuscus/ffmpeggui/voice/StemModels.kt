package com.cuscus.ffmpeggui.voice

import com.cuscus.ffmpeggui.speech.SpeechModel

/**
 * The two models that split a sound into stems (see StemSeparator):
 *
 *  - [BANDIT]: Bandit v2 (Karn N. Watcharasupat, Chih-Wei Wu, Iroro Orife;
 *    code Apache 2.0, weights CC BY-SA 4.0), the sound of a video split into
 *    dialogue, music and effects. It is what sets the voice apart for the
 *    voice levels and the dub, and the first step of every split.
 *  - [INSTRUMENTS]: Spleeter's five stems (Deezer, MIT), the music Bandit
 *    found split in turn into singing, piano, drums, bass and the rest.
 *
 * Both are published for other frameworks only (PyTorch, TensorFlow): these
 * ONNX files are this project's conversions (scripts/export-bandit.py and
 * scripts/export-spleeter.py, from what scripts/bandit-onnx.lock and
 * scripts/spleeter-onnx.lock pin, nothing trained or changed), built twice by
 * .github/workflows/models-onnx.yml and published, with their LICENSE, the
 * NOTICE of the changes and their PROVENANCE, as releases of this
 * repository. docs/MODELS.md has the whole record.
 *
 * As with the other models the app never downloads them: the user's browser
 * does, the files are imported and checked against the SHA-256 pinned here
 * (the same as the locks': scripts/check-model-pins.py checks it). They are
 * kept like the speech model's files (see SpeechModelStore).
 *
 * They replace Spleeter's two stems (sherpa-onnx's int8 files), which only
 * told a voice from the rest: see RetiredModels.
 */
object StemModels {

    // -- Bandit v2 ---------------------------------------------------------------------

    const val BANDIT_NAME = "Bandit v2"
    const val BANDIT_MAKER = "Watcharasupat, Wu, Orife"
    const val BANDIT_LICENCE = "CC BY-SA 4.0"

    /** The page of the model (its paper, code and licences). */
    const val BANDIT_PAGE_URL = "https://github.com/kwatcharasupat/bandit-v2"

    /** Where the authors publish the weights (Zenodo), with their licence. */
    const val BANDIT_WEIGHTS_URL = "https://zenodo.org/records/12701995"

    /** The release of this repository that serves the ONNX file (see scripts/bandit-onnx.lock). */
    const val BANDIT_RELEASE_TAG = "bandit-onnx-2"

    /** Where the ONNX file is, with its LICENSE, NOTICE and PROVENANCE. */
    const val BANDIT_FILES_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/tag/$BANDIT_RELEASE_TAG"

    private const val BANDIT_DOWNLOAD_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/download/$BANDIT_RELEASE_TAG"

    /**
     * The network: from the spectrogram, a complex mask for each of speech,
     * music and effects (core.stems.BanditMasks). Zeros until its first build
     * is pinned (scripts/pin-stems.py): until then nothing can be imported.
     */
    val BANDIT = SpeechModel.ModelFile(
        name = "bandit-v2-multi.onnx",
        sha256 = "7836f01622021adc2fe2570f263ec365ef7677d598b51d27b015b94d869f2ff2",
        bytes = 149_293_980L,
        role = SpeechModel.Role.ENCODER,
    )

    /** False while the file waits for its first build to be pinned: the settings say it is on its way. */
    val BANDIT_PINNED: Boolean get() = BANDIT.sha256.any { it != '0' }

    /** Bandit's file, kept the way the speech model's are. */
    val SCENE: SpeechModel = SpeechModel(
        name = BANDIT_NAME,
        downloads = listOf(SpeechModel.Download(BANDIT.name, "$BANDIT_DOWNLOAD_URL/${BANDIT.name}")),
        // bandit-onnx-2's: the first file (bandit-onnx-1) was in "stems/bandit-v2" (see RetiredModels).
        folder = "stems/bandit-v2-2",
        prefs = "stems_bandit_2",
        tempFolder = "importing-bandit",
        files = listOf(BANDIT),
        installedBytes = BANDIT.bytes ?: 0L,
    )

    // -- Spleeter, five stems --------------------------------------------------------

    const val SPLEETER_NAME = "Spleeter 5 stems"
    const val SPLEETER_MAKER = "Deezer"
    const val SPLEETER_LICENCE = "MIT"

    /** The page of the model (its code, paper and licence). */
    const val SPLEETER_PAGE_URL = "https://github.com/deezer/spleeter"

    /** The release of this repository that serves the ONNX file (see scripts/spleeter-onnx.lock). */
    const val INSTRUMENTS_RELEASE_TAG = "spleeter-onnx-1"

    /** Where the ONNX file is, with its LICENSE, NOTICE and PROVENANCE. */
    const val SPLEETER_FILES_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/tag/$INSTRUMENTS_RELEASE_TAG"

    private const val SPLEETER_DOWNLOAD_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/download/$INSTRUMENTS_RELEASE_TAG"

    /** The five U-Nets in one file: from the magnitudes, each instrument's (core.stems.SpleeterMasks). */
    val INSTRUMENTS_FILE = SpeechModel.ModelFile(
        name = "spleeter-5stems.onnx",
        sha256 = "533f0f260e6bfd14f55027594ba443e9d5c1cd8a1507a67000fa312d5b2374cf",
        bytes = 196_647_290L,
        role = SpeechModel.Role.ENCODER,
    )

    /** Spleeter's file, kept the way the speech model's are. */
    val INSTRUMENTS: SpeechModel = SpeechModel(
        name = SPLEETER_NAME,
        downloads = listOf(SpeechModel.Download(INSTRUMENTS_FILE.name, "$SPLEETER_DOWNLOAD_URL/${INSTRUMENTS_FILE.name}")),
        folder = "stems/spleeter-5stems",
        prefs = "stems_spleeter",
        tempFolder = "importing-spleeter",
        files = listOf(INSTRUMENTS_FILE),
        installedBytes = INSTRUMENTS_FILE.bytes ?: 0L,
    )
}
