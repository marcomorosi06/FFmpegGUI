package com.cuscus.ffmpeggui.speech

import ai.onnxruntime.OrtSession
import android.util.Log

/**
 * How the AI models run on the processor: ONNX Runtime's plain kernels, or
 * its XNNPACK ones (quicker on many ARM processors for the networks in
 * floating point; a node XNNPACK cannot run, such as a quantised one, stays
 * on the plain kernels). A phone's GPU and NPU are not used: ONNX Runtime's
 * Android build has no GPU provider, and NNAPI takes only fixed shapes,
 * which these networks are not.
 *
 * Read by every engine when it loads a model: after a change the models in
 * memory are let go of (SmartFeatures.setFastKernels).
 */
object AiKernels {

    @Volatile
    var fast: Boolean = true

    /** sherpa-onnx's provider for the setting (it falls back to the CPU by itself where XNNPACK is missing). */
    val provider: String get() = if (fast) "xnnpack" else "cpu"

    /**
     * Session options on [threads] threads, every optimisation on, with the
     * XNNPACK kernels when the setting asks for them and they are there.
     */
    fun options(threads: Int): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(threads)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        } catch (e: Exception) {
            options.close()
            throw e
        }
        if (fast) {
            try {
                // XNNPACK has threads of its own: ONNX Runtime's are not left spinning beside them.
                options.addConfigEntry("session.intra_op.allow_spinning", "0")
                options.addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
            } catch (e: Exception) {
                Log.w(TAG, "XNNPACK is not available: the plain kernels are used", e)
            }
        }
        return options
    }

    private const val TAG = "AiKernels"
}
