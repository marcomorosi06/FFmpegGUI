package com.cuscus.ffmpeggui.cutout

import com.cuscus.ffmpeggui.speech.SpeechModel

/**
 * The model that corrects a subject's cut-out where the finger points: SlimSAM
 * (Zigeng Chen et al., Apache 2.0), a pruned Segment Anything, as converted to
 * ONNX for transformers.js (by Xenova, on Hugging Face). Two files: the image
 * encoder, run once per picture, and the prompt encoder with the mask decoder,
 * run for every stroke. Given points on the picture marked "this is part of
 * it" or "this is not", it finds the whole thing they point at.
 *
 * As with the other models the app never downloads it: the user's browser
 * does, the files are imported and checked against the SHA-256 pinned here.
 * It is kept like the speech model's files (see SpeechModelStore).
 */
object RefineModel {

    const val NAME = "SlimSAM"
    const val MAKER = "Zigeng Chen et al."
    const val LICENCE = "Apache 2.0"

    /** The page of the model (its paper, code and licence). */
    const val MODEL_PAGE_URL = "https://github.com/czg1225/SlimSAM"

    /** Where the ONNX files are, on Hugging Face. */
    const val FILES_URL = "https://huggingface.co/Xenova/slimsam-77-uniform"

    val ENCODER = SpeechModel.ModelFile(
        name = "vision_encoder.onnx",
        sha256 = "9f8433273a6750b587779baa0cf5508111001bf7e7acfcf585d370139fd366d0",
        bytes = null,
        role = SpeechModel.Role.ENCODER,
    )

    val DECODER = SpeechModel.ModelFile(
        name = "prompt_encoder_mask_decoder.onnx",
        sha256 = "f4514391764fbd56e08e119060d874ecd7d52994bfb1968af159e12d4943b5bb",
        bytes = null,
        role = SpeechModel.Role.DECODER,
    )

    /** The model's files, kept the way the speech model's are. */
    val FILES: SpeechModel = SpeechModel(
        variant = null,
        name = NAME,
        archive = null,
        // "?download=true" makes Hugging Face send the file as an attachment.
        downloads = listOf(ENCODER, DECODER).map { SpeechModel.Download(it.name, "$FILES_URL/resolve/main/onnx/${it.name}?download=true") },
        folder = "refine/slimsam-77",
        prefs = "refine_model",
        tempFolder = "importing-refine",
        files = listOf(ENCODER, DECODER),
        samples = emptyList(),
        // 23.3 MB and 16.6 MB.
        installedBytes = 39_900_000L,
    )
}
