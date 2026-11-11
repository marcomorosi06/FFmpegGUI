package com.cuscus.ffmpeggui.cutout

import com.cuscus.ffmpeggui.speech.SpeechModel

/**
 * The model that corrects a subject's cut-out where the finger points, and
 * lifts a subject off a photo with a long press: EdgeTAM (Meta, Apache 2.0),
 * a Segment Anything 2 made for phones. Two files: the image encoder, run
 * once per picture, and the prompt encoder with the mask decoder, run for
 * every stroke or tap. Given points on the picture marked "this is part of
 * it" or "this is not", it finds the whole thing they point at.
 *
 * Meta publishes it for PyTorch only: these ONNX files are this project's
 * conversion (scripts/export-edgetam.py, from what scripts/edgetam-onnx.lock
 * pins, nothing trained or changed), built twice by
 * .github/workflows/models-onnx.yml and published, with EdgeTAM's LICENSE, the
 * NOTICE of the changes and their PROVENANCE, as the release [RELEASE_TAG] of
 * this repository. docs/MODELS.md has the whole record.
 *
 * As with the other models the app never downloads it: the user's browser
 * does, the files are imported and checked against the SHA-256 pinned here
 * (the same as the lock's: scripts/check-model-pins.py checks it). It is kept
 * like the speech model's files (see SpeechModelStore).
 */
object RefineModel {

    const val NAME = "EdgeTAM"
    const val MAKER = "Meta"
    const val LICENCE = "Apache 2.0"

    /** The page of the model (its paper, code and licence). */
    const val MODEL_PAGE_URL = "https://github.com/facebookresearch/EdgeTAM"

    /** The release of this repository that serves the ONNX files (see scripts/edgetam-onnx.lock). */
    const val RELEASE_TAG = "edgetam-onnx-1"

    /** Where the ONNX files are, with their LICENSE, NOTICE and PROVENANCE. */
    const val FILES_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/tag/$RELEASE_TAG"

    private const val DOWNLOAD_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/download/$RELEASE_TAG"

    val ENCODER = SpeechModel.ModelFile(
        name = "edgetam-image-encoder.onnx",
        sha256 = "692c9365d13fbbd8fd5b32bdd0c7bd0377696bb0b20fa7d97f8a3a3c1e776f0d",
        bytes = 20_164_359L,
        role = SpeechModel.Role.ENCODER,
    )

    val DECODER = SpeechModel.ModelFile(
        name = "edgetam-mask-decoder.onnx",
        sha256 = "df71726f03f1eb11ab0fda4648ad024569fbbe26ddd69e2a16354432158035a2",
        bytes = 16_574_577L,
        role = SpeechModel.Role.DECODER,
    )

    /** The model's files, kept the way the speech model's are. */
    val FILES: SpeechModel = SpeechModel(
        name = NAME,
        downloads = listOf(ENCODER, DECODER).map { SpeechModel.Download(it.name, "$DOWNLOAD_URL/${it.name}") },
        folder = "refine/edgetam",
        prefs = "refine_model_edgetam",
        tempFolder = "importing-refine",
        files = listOf(ENCODER, DECODER),
        installedBytes = listOf(ENCODER, DECODER).sumOf { it.bytes ?: 0L },
    )
}
