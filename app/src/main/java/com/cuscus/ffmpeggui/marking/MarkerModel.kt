package com.cuscus.ffmpeggui.marking

import com.cuscus.ffmpeggui.speech.SpeechModel

/**
 * The invisible AI marks (EU AI Act, Regulation (EU) 2024/1689, Article
 * 50(2)): the files that write a mark into what the app's models make, and
 * find it again. They are imported before any other model (MarkingGate):
 * without them no model is imported, and none stays on the phone
 * (MarkingSweep).
 *
 *  - AudioSeal (Meta, MIT, the weights included): its generator and its
 *    detector, converted to ONNX by this project for a 5 s window at 16 kHz;
 *    a dub's voice gets its mark as it is made.
 *  - TrustMark Q (Adobe, MIT, the model files included, as Adobe confirmed:
 *    TRUSTMARK_CONFIRMATION_URL): Adobe's own encoder and decoder, unchanged;
 *    a picture the magic eraser changed gets its mark as it is made.
 *
 * The marks carry nothing but "made with AI" (core.marking.Marks: constant
 * payloads, the same for everyone): no identifier of the phone, the person,
 * the file or the model, no date, nothing random.
 *
 * Built twice by .github/workflows/ai-markers.yml from what
 * scripts/ai-markers.lock pins and published, with the licences, the NOTICE
 * and the PROVENANCE, as the release [RELEASE_TAG] of this repository. As
 * with the other models the app never downloads them: the user's browser
 * does, the files are imported and checked against the SHA-256 pinned here
 * (scripts/check-model-pins.py checks them against the lock). A pin of
 * zeros is not pinned yet (see scripts/pin-ai-markers.py): no file matches
 * it, so nothing can be imported until it is.
 */
object MarkerModel {

    const val NAME = "AudioSeal + TrustMark"
    const val AUDIO_NAME = "AudioSeal"
    const val AUDIO_MAKER = "Meta"
    const val PICTURE_NAME = "TrustMark"
    const val PICTURE_MAKER = "Adobe"
    const val LICENCE = "MIT"

    const val AUDIO_PAGE_URL = "https://github.com/facebookresearch/audioseal"
    const val PICTURE_PAGE_URL = "https://github.com/adobe/trustmark"

    /** Adobe's confirmation that TrustMark's model files may be redistributed unchanged. */
    const val TRUSTMARK_CONFIRMATION_URL = "https://github.com/adobe/trustmark/issues/48"

    /** The release of this repository that serves the files (see scripts/ai-markers.lock). */
    const val RELEASE_TAG = "ai-markers-1"

    /** Where the files are, with their licences, NOTICE and PROVENANCE. */
    const val FILES_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/tag/$RELEASE_TAG"

    private const val DOWNLOAD_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/download/$RELEASE_TAG"

    /** AudioSeal's generator: speech (16 kHz, one window) and its message of zeros in, the watermark out. */
    val GENERATOR = SpeechModel.ModelFile(
        name = "audioseal-generator.onnx",
        sha256 = "ecb230f2d38de821f72f04eda9c40b82f102e058263848f892be4f6e189dd0e7",
        bytes = 58_960_229L,
        role = SpeechModel.Role.ENCODER,
    )

    /** AudioSeal's detector: speech in, per sample how likely the mark is there, and its message. */
    val DETECTOR = SpeechModel.ModelFile(
        name = "audioseal-detector.onnx",
        sha256 = "a389f9a5e3fa67a6a3a13480c3799875a5f3251d2d542fada6bd93b5c2bb53fa",
        bytes = 34_754_353L,
        role = SpeechModel.Role.DECODER,
    )

    /** TrustMark Q's encoder (Adobe's encoder_Q.onnx): a 256 square and the payload in, the square marked out. */
    val ENCODER = SpeechModel.ModelFile(
        name = "trustmark-encoder-q.onnx",
        sha256 = "19b3d1b25836130ffd78775a8f61539f993375d1823ef0e59ba5b8dffb4f892d",
        bytes = 17_312_208L,
        role = SpeechModel.Role.JOINER,
    )

    /** TrustMark Q's decoder (Adobe's decoder_Q.onnx): a 256 square in, its 100 bits out. */
    val DECODER = SpeechModel.ModelFile(
        name = "trustmark-decoder-q.onnx",
        sha256 = "ee3268f057c9dabef680e169302f5973d0589feea86189ed229a896cc3aa88df",
        bytes = 47_401_222L,
        role = SpeechModel.Role.TOKENS,
    )

    private val ALL = listOf(GENERATOR, DETECTOR, ENCODER, DECODER)

    /** True until the files are pinned: nothing can be imported, the settings say why. */
    val NOT_PINNED: Boolean get() = ALL.any { it.sha256.all { c -> c == '0' } }

    /** The files, kept the way the speech model's are (one import, every file checked). */
    val FILES: SpeechModel = SpeechModel(
        variant = null,
        name = NAME,
        archive = null,
        downloads = ALL.map { SpeechModel.Download(it.name, "$DOWNLOAD_URL/${it.name}") },
        folder = "markers/$RELEASE_TAG",
        prefs = "ai_markers",
        tempFolder = "importing-markers",
        files = ALL,
        samples = emptyList(),
        installedBytes = ALL.sumOf { it.bytes ?: 0L },
    )
}
