package com.cuscus.ffmpeggui.cutout

import com.cuscus.ffmpeggui.core.cutout.SubjectNet

/** Which cut-out model: quick and light, precise, or the finest edges. */
enum class CutoutVariant { FAST, PRECISE, FINEST }

/**
 * A model that finds the subject of a picture, for the cut-out (no green
 * screen needed):
 *
 *  - [FAST]: U2-Net's small model, U2-NetP (Xuebin Qin and others, Apache 2.0),
 *    as converted to ONNX by the rembg project (MIT). One file of under 5 MB;
 *    it works on a 320 x 320 copy, several times quicker, with rougher edges.
 *  - [PRECISE]: IS-Net, general use, of the DIS project (Xuebin Qin and others,
 *    Apache 2.0), as converted by rembg. One file of about 170 MB; it works on a
 *    1024 x 1024 copy of the picture and needs about a gigabyte of memory.
 *  - [FINEST]: BiRefNet-lite, general use (Zheng Peng and others, MIT), as
 *    converted by this project (scripts/export-birefnet.py: the author's
 *    weights, nothing trained or changed) and served, with its LICENSE, NOTICE
 *    and PROVENANCE, as a release of this repository. About 170 MB too, on a
 *    1024 x 1024 copy; hair, fur and thin parts come out whole, and it keeps
 *    to the main subject. Slower, and it needs about 2.5 GB of memory.
 *
 * As with the speech model the app never downloads them: the user's browser
 * does, and the file is then imported and checked against the SHA-256 pinned
 * here. docs/MODELS.md has the record of each (licences, sources, hashes,
 * what they were trained on). All can be on the phone; the one chosen in the
 * settings is used.
 */
class CutoutModel private constructor(
    val variant: CutoutVariant,
    val name: String,
    val maker: String,
    /** The model's own licence (its weights and code). */
    val licence: String,
    val net: SubjectNet,
    /** Who made the ONNX file: rembg, or this project (see [Host]). */
    val host: Host,
    /** Where the user downloads it (in the browser: the app has no network access). */
    val downloadUrl: String,
    /** The page of the model (its paper, code and licence). */
    val modelPageUrl: String,
    val fileName: String,
    val sha256: String,
    val bytes: Long,
    /** Folder of the installed model, inside the app's private files. */
    val folder: String,
    /** Its settings (hash, when checked), kept apart from the other model's. */
    val prefs: String,
    /** Added to the name of the masks it makes, so that each model keeps its own. */
    val maskSuffix: String,
    /** Below this much free memory the model is not even tried. */
    val minFreeBytes: Long,
    /**
     * Run without ONNX Runtime's memory arena: a little slower to allocate,
     * but the peak stays lower (2.1 GB rather than 2.5 for BiRefNet).
     */
    val lean: Boolean,
) {
    /** Where the ONNX file is served from. */
    enum class Host {
        /** rembg's releases on GitHub: rembg made the file. */
        REMBG,

        /** A release of this repository: this project made the file, from the author's weights. */
        THIS_PROJECT,
    }

    companion object {
        /** The project the rembg ONNX files and the way to run them come from. */
        const val REMBG_URL = "https://github.com/danielgatis/rembg"

        /** The release of this repository that serves BiRefNet-lite (see scripts/birefnet-onnx.lock). */
        const val BIREFNET_RELEASE_TAG = "birefnet-lite-onnx-1"

        /** Its page: the file, its LICENSE, NOTICE and PROVENANCE. */
        const val BIREFNET_RELEASE_URL = "https://github.com/marcomorosi06/FFmpegGUI/releases/tag/$BIREFNET_RELEASE_TAG"

        private const val MB = 1024L * 1024

        val PRECISE = CutoutModel(
            variant = CutoutVariant.PRECISE,
            name = "IS-Net (general use)",
            maker = "Xuebin Qin et al. (DIS)",
            licence = "Apache 2.0",
            net = SubjectNet.ISNET,
            host = Host.REMBG,
            downloadUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx",
            modelPageUrl = "https://github.com/xuebinqin/DIS",
            fileName = "isnet-general-use.onnx",
            sha256 = "60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a",
            bytes = 178_648_008L,
            folder = "cutout",
            prefs = "cutout_model",
            maskSuffix = "",
            minFreeBytes = 600 * MB,
            lean = false,
        )

        val FAST = CutoutModel(
            variant = CutoutVariant.FAST,
            name = "U2-NetP",
            maker = "Xuebin Qin et al. (U2-Net)",
            licence = "Apache 2.0",
            net = SubjectNet.U2NET,
            host = Host.REMBG,
            downloadUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx",
            modelPageUrl = "https://github.com/xuebinqin/U-2-Net",
            fileName = "u2netp.onnx",
            sha256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8",
            bytes = 4_574_861L,
            folder = "cutout-fast",
            prefs = "cutout_model_fast",
            maskSuffix = "-fast",
            minFreeBytes = 300 * MB,
            lean = false,
        )

        val FINEST = CutoutModel(
            variant = CutoutVariant.FINEST,
            name = "BiRefNet-lite (general use)",
            maker = "Zheng Peng et al. (BiRefNet)",
            licence = "MIT",
            net = SubjectNet.BIREFNET,
            host = Host.THIS_PROJECT,
            downloadUrl = "https://github.com/marcomorosi06/FFmpegGUI/releases/download/$BIREFNET_RELEASE_TAG/birefnet-lite-general.onnx",
            modelPageUrl = "https://github.com/ZhengPeng7/BiRefNet",
            fileName = "birefnet-lite-general.onnx",
            sha256 = "8b75d639b11e984fc7d3a6c0818a2dd2f62df0b19997e4e822099e72f8d9b774",
            bytes = 180_240_681L,
            folder = "cutout-finest",
            prefs = "cutout_model_finest",
            maskSuffix = "-finest",
            minFreeBytes = 1500 * MB,
            lean = true,
        )

        fun of(variant: CutoutVariant): CutoutModel = when (variant) {
            CutoutVariant.FAST -> FAST
            CutoutVariant.PRECISE -> PRECISE
            CutoutVariant.FINEST -> FINEST
        }
    }
}
