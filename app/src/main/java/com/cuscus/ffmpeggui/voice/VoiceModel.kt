package com.cuscus.ffmpeggui.voice

import com.cuscus.ffmpeggui.speech.SpeechModel

/**
 * The model that sets a voice apart from the rest of a sound: Spleeter's
 * two-stem model (Deezer, MIT), as converted to ONNX and quantised to int8 by
 * sherpa-onnx (Apache 2.0). Two networks: one estimates the voice, the other
 * the accompaniment, from the same spectrogram (see VoiceSeparation).
 *
 * As with the other models the app never downloads it: the user's browser
 * does (one archive from sherpa-onnx's releases on GitHub), the archive is
 * unpacked on the phone and both files are checked against the SHA-256
 * pinned here. It is kept like the speech model's files (see SpeechModelStore).
 */
object VoiceModel {

    const val NAME = "Spleeter 2stems"
    const val MAKER = "Deezer"
    const val LICENCE = "MIT"

    /** The page of the model (its code, paper and licence). */
    const val MODEL_PAGE_URL = "https://github.com/deezer/spleeter"

    /** The page of the ONNX conversion. */
    const val CONVERSION_PAGE_URL = "https://k2-fsa.github.io/sherpa/onnx/source-separation/models.html"

    private const val ARCHIVE_NAME = "sherpa-onnx-spleeter-2stems-int8.tar.bz2"
    private const val ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/source-separation-models/$ARCHIVE_NAME"

    /** The voice network (as SpeechModel roles go: the first of the two). */
    val VOCALS = SpeechModel.ModelFile(
        name = "vocals.int8.onnx",
        sha256 = "6e9721864c0c1a6d86eaae92de71637b2daeb3df769c9c536053b46be8dd10aa",
        bytes = 26_238_864L,
        role = SpeechModel.Role.ENCODER,
    )

    /** The accompaniment network. */
    val ACCOMPANIMENT = SpeechModel.ModelFile(
        name = "accompaniment.int8.onnx",
        sha256 = "53a96a0d1520b45b8f3cc53262e3725defb686fc3a17490932f050721f4583f1",
        bytes = 26_238_869L,
        role = SpeechModel.Role.DECODER,
    )

    /** The model's files, kept the way the speech model's are. */
    val FILES: SpeechModel = SpeechModel(
        variant = null,
        name = NAME,
        archive = SpeechModel.Archive(ARCHIVE_NAME, "c5dc8edfa08cc9a6d82670fcca15cfe671e446385e1a28c9da8c1816d238ea17", 48_623_481L, ARCHIVE_URL),
        // One download: the archive (the loose files, when the user has them, are taken too).
        downloads = listOf(SpeechModel.Download(ARCHIVE_NAME, ARCHIVE_URL)),
        folder = "voice/spleeter-2stems-int8",
        prefs = "voice_model",
        tempFolder = "importing-voice",
        files = listOf(VOCALS, ACCOMPANIMENT),
        samples = emptyList(),
        installedBytes = (VOCALS.bytes ?: 0L) + (ACCOMPANIMENT.bytes ?: 0L),
    )
}
