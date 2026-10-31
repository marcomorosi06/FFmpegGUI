package com.cuscus.ffmpeggui.erase

import com.cuscus.ffmpeggui.speech.SpeechModel

/** The two models of the magic eraser: quick and small, or slower and better on large parts. */
enum class EraseVariant { FAST, PRECISE }

/**
 * The magic eraser's models: what is painted over is filled in with what
 * should be behind it.
 *
 *  - MI-GAN (Picsart AI Research, ICCV 2023; code and weights MIT), made for
 *    phones: its ONNX pipeline (by its author, on Hugging Face) takes the
 *    picture and the mask as they are and does its own crop and blend.
 *  - LaMa (Samsung AI Center, "big-lama"; Apache 2.0), as converted to ONNX
 *    by Carve (a fixed 512 x 512 square, float). Better on large parts and
 *    repeating patterns, slower.
 *
 * Both learnt from Places2, a set of photos for research. As with the other
 * models the app never downloads them: the user's browser does and they are
 * imported. MI-GAN's file is pinned by its SHA-256; LaMa's is not published
 * with one, so its hash is taken at import and shown (an empty pin).
 */
object EraseModel {

    const val MIGAN_NAME = "MI-GAN"
    const val MIGAN_MAKER = "Picsart AI Research"
    const val MIGAN_LICENCE = "MIT"
    const val MIGAN_PAGE_URL = "https://github.com/Picsart-AI-Research/MI-GAN"
    private const val MIGAN_FILES_URL = "https://huggingface.co/andraniksargsyan/migan"

    const val LAMA_NAME = "LaMa"
    const val LAMA_MAKER = "Samsung AI Center"
    const val LAMA_LICENCE = "Apache 2.0"
    const val LAMA_PAGE_URL = "https://github.com/advimman/lama"
    private const val LAMA_FILES_URL = "https://huggingface.co/Carve/LaMa-ONNX"

    val MIGAN_FILE = SpeechModel.ModelFile(
        name = "migan_pipeline_v2.onnx",
        sha256 = "6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b",
        bytes = null,
        role = SpeechModel.Role.ENCODER,
    )

    val LAMA_FILE = SpeechModel.ModelFile(
        name = "lama_fp32.onnx",
        // Not published with a hash: taken at import (see SpeechModelStore.FileCheck).
        sha256 = "",
        bytes = null,
        role = SpeechModel.Role.ENCODER,
    )

    val MIGAN: SpeechModel = SpeechModel(
        variant = null,
        name = MIGAN_NAME,
        archive = null,
        downloads = listOf(SpeechModel.Download(MIGAN_FILE.name, "$MIGAN_FILES_URL/resolve/main/${MIGAN_FILE.name}?download=true")),
        folder = "erase/migan",
        prefs = "erase_model_migan",
        tempFolder = "importing-erase-migan",
        files = listOf(MIGAN_FILE),
        samples = emptyList(),
        installedBytes = 28_100_000L,
    )

    val LAMA: SpeechModel = SpeechModel(
        variant = null,
        name = LAMA_NAME,
        archive = null,
        downloads = listOf(SpeechModel.Download(LAMA_FILE.name, "$LAMA_FILES_URL/resolve/main/${LAMA_FILE.name}?download=true")),
        folder = "erase/lama",
        prefs = "erase_model_lama",
        tempFolder = "importing-erase-lama",
        files = listOf(LAMA_FILE),
        samples = emptyList(),
        installedBytes = 208_000_000L,
    )

    fun of(variant: EraseVariant): SpeechModel = if (variant == EraseVariant.FAST) MIGAN else LAMA
}
