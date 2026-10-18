package com.cuscus.ffmpeggui.cutout

import com.cuscus.ffmpeggui.core.cutout.SubjectNet

/** Which cut-out model: quick and light, or slower and more precise. */
enum class CutoutVariant { FAST, PRECISE }

/**
 * A model that finds the subject of a picture, for the subject key (a cut-out
 * without a green screen), as converted to ONNX by the rembg project (MIT):
 *
 *  - [PRECISE]: IS-Net, general use, of the DIS project (Xuebin Qin and others,
 *    Apache 2.0). One file of about 170 MB; it works on a 1024 x 1024 copy of
 *    the picture and needs about a gigabyte of memory while it runs.
 *  - [FAST]: U2-Net's small model, U2-NetP (Xuebin Qin and others, Apache 2.0).
 *    One file of under 5 MB; it works on a 320 x 320 copy, several times
 *    quicker, with rougher edges.
 *
 * As with the speech model the app never downloads them: the user's browser
 * does, from rembg's releases on GitHub, and the file is then imported and
 * checked against the SHA-256 pinned here. Both can be on the phone; the one
 * chosen in the settings is used.
 */
class CutoutModel private constructor(
    val variant: CutoutVariant,
    val name: String,
    val maker: String,
    val net: SubjectNet,
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
) {
    companion object {
        const val LICENCE = "Apache 2.0"

        /** The project the ONNX files and the way to run them come from. */
        const val REMBG_URL = "https://github.com/danielgatis/rembg"

        val PRECISE = CutoutModel(
            variant = CutoutVariant.PRECISE,
            name = "IS-Net (general use)",
            maker = "Xuebin Qin et al. (DIS)",
            net = SubjectNet.ISNET,
            downloadUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx",
            modelPageUrl = "https://github.com/xuebinqin/DIS",
            fileName = "isnet-general-use.onnx",
            sha256 = "60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a",
            bytes = 178_648_008L,
            folder = "cutout",
            prefs = "cutout_model",
            maskSuffix = "",
        )

        val FAST = CutoutModel(
            variant = CutoutVariant.FAST,
            name = "U2-NetP",
            maker = "Xuebin Qin et al. (U2-Net)",
            net = SubjectNet.U2NET,
            downloadUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx",
            modelPageUrl = "https://github.com/xuebinqin/U-2-Net",
            fileName = "u2netp.onnx",
            sha256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8",
            bytes = 4_574_861L,
            folder = "cutout-fast",
            prefs = "cutout_model_fast",
            maskSuffix = "-fast",
        )

        fun of(variant: CutoutVariant): CutoutModel = when (variant) {
            CutoutVariant.FAST -> FAST
            CutoutVariant.PRECISE -> PRECISE
        }
    }
}
