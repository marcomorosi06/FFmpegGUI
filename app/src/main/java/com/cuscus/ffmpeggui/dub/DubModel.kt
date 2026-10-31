package com.cuscus.ffmpeggui.dub

/**
 * The voice that dubs the video: Kokoro-82M v1.0 (hexgrad, Apache 2.0), 54
 * natural voices in eight languages, in sherpa-onnx's multi-language build
 * (Apache 2.0) at full precision: on a phone's processor float32 is more
 * than twice as fast as the int8 build (whose quantised convolutions ONNX
 * Runtime runs slowly) and sounds less metallic. The build reads words with
 * its pronunciation lists, jieba's dictionaries for Chinese and eSpeak NG's
 * data (GPL-3.0) for the rest.
 *
 * As with the other models the app never downloads it: the user's browser
 * does (one archive from sherpa-onnx's releases on GitHub), and the archive is
 * unpacked on the phone whole, its folders kept (eSpeak NG's data is a few
 * hundred files). The archive and its main files are checked against the
 * SHA-256 pinned here.
 */
object DubModel {

    const val NAME = "Kokoro-82M"
    const val MAKER = "hexgrad"
    const val LICENCE = "Apache 2.0"

    /** The page of the model (its card, voices and licence). */
    const val MODEL_PAGE_URL = "https://huggingface.co/hexgrad/Kokoro-82M"

    /** The page of the build the app runs. */
    const val CONVERSION_PAGE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models"

    const val ARCHIVE_NAME = "kokoro-multi-lang-v1_0.tar.bz2"
    const val ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$ARCHIVE_NAME"
    const val ARCHIVE_SHA256 = "c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298"
    const val ARCHIVE_BYTES = 349_906_910L

    /** About the space it takes once unpacked. */
    const val INSTALLED_BYTES = 401_239_297L

    /** The folder the archive holds everything in (left out when unpacking). */
    const val ARCHIVE_FOLDER = "kokoro-multi-lang-v1_0"

    const val MODEL = "model.onnx"
    const val VOICES = "voices.bin"
    const val TOKENS = "tokens.txt"
    const val ESPEAK_DATA = "espeak-ng-data"
    const val CHINESE_DICT = "dict"

    /** Chinese dates, phone numbers and numbers read out as words. */
    val CHINESE_RULES = listOf("date-zh.fst", "phone-zh.fst", "number-zh.fst")

    /** The files checked, with what they must hash to (the rest comes with the archive's hash). */
    val PINNED: Map<String, String> = linkedMapOf(
        MODEL to "b40f62b166ac8164b0627ef48a0b358eda0985e272fb03ef5252e7206305da11",
        VOICES to "1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29",
        TOKENS to "6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425",
        "lexicon-us-en.txt" to "7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14",
        "lexicon-gb-en.txt" to "c4cbb37316f62210dff52718a7afcaae24f50c032cc75ab47ae67b831d1049e7",
        "lexicon-zh.txt" to "902bc2d20ac7c449c5ecbbbb23c89d12119c03ed5b8ed27f114f0260c9e35229",
        "$ESPEAK_DATA/phontab" to "886f3fa402cb0ba73d483aa8ad000af47a6b7cc06293c75a97913fba68a530f6",
    )

    /** The rate the model speaks at. */
    const val SAMPLE_RATE = 24_000
}
