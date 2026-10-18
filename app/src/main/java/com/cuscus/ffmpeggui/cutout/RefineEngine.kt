package com.cuscus.ffmpeggui.cutout

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.cuscus.ffmpeggui.core.cutout.PromptSegmentation
import com.cuscus.ffmpeggui.speech.SpeechModel
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Runs SlimSAM ([RefineModel]) through ONNX Runtime, on the processor: [study]
 * reads a picture once (the image encoder, a second or two), [find] then gives
 * the masks of what some points show (the mask decoder, a fraction of a
 * second), once per stroke.
 *
 * The model is loaded the first time it is needed and let go of
 * [IDLE_UNLOAD_MS] after its last use. One call at a time.
 *
 * The tensors are found by name, as transformers.js names them: the encoder
 * gives "image_embeddings" and "image_positional_embeddings", the decoder takes
 * those with "input_points" (float, 1 x 1 x points x 2) and "input_labels"
 * (int64, 1 x 1 x points) and gives "iou_scores" and "pred_masks" (1 x 1 x 3 x
 * 256 x 256 logits).
 */
class RefineEngine(context: Context, private val store: SpeechModelStore) {

    class ModelMissing : IOException("The refining model is not installed")

    class NotEnoughMemory : IOException("Not enough free memory for the refining model")

    /** A picture the encoder has read: what the decoder needs to find things in it. */
    class Studied internal constructor(
        val width: Int,
        val height: Int,
        internal val outputs: Map<String, Pair<FloatArray, LongArray>>,
    )

    private class Loaded(
        val environment: OrtEnvironment,
        val options: OrtSession.SessionOptions,
        val encoder: OrtSession,
        val decoder: OrtSession,
    )

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loaded: Loaded? = null
    private var unloadJob: Job? = null

    private val _inMemory = MutableStateFlow(false)

    /** True while the model is in memory. */
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    /** True when the model is installed and checked. */
    val ready: Boolean get() = store.state.value is SpeechModelStore.State.Ready

    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** Reads [picture] (any size) with the image encoder. */
    suspend fun study(picture: Bitmap): Studied = withContext(Dispatchers.Default) {
        mutex.withLock {
            val model = use()
            try {
                val (rw, rh) = PromptSegmentation.resizedSize(picture.width, picture.height)
                val resized = Bitmap.createScaledBitmap(picture, rw, rh, true)
                val argb = IntArray(rw * rh)
                resized.getPixels(argb, 0, rw, 0, 0, rw, rh)
                if (resized !== picture) resized.recycle()
                val input = PromptSegmentation.input(argb, rw, rh)
                val side = PromptSegmentation.SIDE.toLong()
                val inputName = model.encoder.inputNames.firstOrNull() ?: throw IOException("The encoder has no input")
                val outputs = OnnxTensor.createTensor(model.environment, FloatBuffer.wrap(input), longArrayOf(1, 3, side, side)).use { tensor ->
                    model.encoder.run(mapOf(inputName to tensor)).use { result ->
                        val kept = mutableMapOf<String, Pair<FloatArray, LongArray>>()
                        for ((name, value) in result) {
                            val t = value as? OnnxTensor ?: continue
                            val buffer = t.floatBuffer ?: continue
                            kept[name] = FloatArray(buffer.remaining()).also { buffer.get(it) } to t.info.shape
                        }
                        kept
                    }
                }
                Studied(picture.width, picture.height, outputs)
            } finally {
                scheduleUnload()
            }
        }
    }

    /** What the decoder gives: [count] masks of 256 x 256 logits, one after the other, and a score for each. */
    class Prediction(val logits: FloatArray, val scores: FloatArray, val count: Int)

    /**
     * The masks the model makes of what some points show: [points] as x, y
     * pairs in the studied picture's pixels, [inside] for each whether it is on
     * what is wanted. Which one to take is the caller's choice (see
     * PromptSegmentation.choose).
     */
    suspend fun find(studied: Studied, points: FloatArray, inside: BooleanArray): Prediction = withContext(Dispatchers.Default) {
        require(points.size == inside.size * 2 && inside.isNotEmpty()) { "One label per point" }
        mutex.withLock {
            val model = use()
            try {
                val count = inside.size.toLong()
                val xs = FloatArray(inside.size) { points[2 * it] }
                val ys = FloatArray(inside.size) { points[2 * it + 1] }
                val scaled = PromptSegmentation.points(xs, ys, studied.width, studied.height)
                val labels = LongArray(inside.size) { if (inside[it]) PromptSegmentation.INSIDE else PromptSegmentation.OUTSIDE }
                val env = model.environment
                val tensors = mutableMapOf<String, OnnxTensor>()
                try {
                    for (name in model.decoder.inputNames) {
                        val lower = name.lowercase()
                        tensors[name] = when {
                            "point" in lower -> OnnxTensor.createTensor(env, FloatBuffer.wrap(scaled), longArrayOf(1, 1, count, 2))
                            "label" in lower -> OnnxTensor.createTensor(env, LongBuffer.wrap(labels), longArrayOf(1, 1, count))
                            else -> {
                                val (values, shape) = studied.outputs[name] ?: throw IOException("The decoder wants $name, which the encoder does not give")
                                OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)
                            }
                        }
                    }
                    model.decoder.run(tensors).use { result ->
                        var logits: FloatArray? = null
                        var scores: FloatArray? = null
                        for ((name, value) in result) {
                            val buffer = (value as? OnnxTensor)?.floatBuffer ?: continue
                            val values = FloatArray(buffer.remaining()).also { buffer.get(it) }
                            when {
                                "mask" in name.lowercase() -> logits = values
                                "iou" in name.lowercase() || "score" in name.lowercase() -> scores = values
                            }
                        }
                        val maskLogits = logits ?: throw IOException("The decoder gave no mask")
                        val maskCount = maskLogits.size / (PromptSegmentation.MASK_SIDE * PromptSegmentation.MASK_SIDE)
                        if (maskCount < 1) throw IOException("Unexpected mask of ${maskLogits.size} values")
                        val maskScores = scores?.takeIf { it.size >= maskCount } ?: FloatArray(maskCount) { 1f }
                        Prediction(maskLogits, maskScores, maskCount)
                    }
                } finally {
                    tensors.values.forEach { it.close() }
                }
            } finally {
                scheduleUnload()
            }
        }
    }

    /** Frees the model's memory now (it loads again when next needed). */
    fun unload() {
        scope.launch { mutex.withLock { release() } }
    }

    private fun use(): Loaded {
        unloadJob?.cancel()
        val model = loaded ?: load().also { loaded = it }
        _inMemory.value = true
        return model
    }

    private fun load(): Loaded {
        if (!ready) throw ModelMissing()
        val encoderFile = store.file(SpeechModel.Role.ENCODER) ?: throw ModelMissing()
        val decoderFile = store.file(SpeechModel.Role.DECODER) ?: throw ModelMissing()
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        if (memory.availMem in 1 until MIN_FREE_BYTES) throw NotEnoughMemory()
        val started = SystemClock.elapsedRealtime()
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(threads)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val encoder = environment.createSession(encoderFile.absolutePath, options)
            val decoder = try {
                environment.createSession(decoderFile.absolutePath, options)
            } catch (e: Exception) {
                encoder.close()
                throw e
            }
            Log.i(TAG, "${RefineModel.NAME} loaded in ${SystemClock.elapsedRealtime() - started} ms on $threads threads; " +
                "encoder ${encoder.inputNames} -> ${encoder.outputNames}, decoder ${decoder.inputNames} -> ${decoder.outputNames}")
            return Loaded(environment, options, encoder, decoder)
        } catch (e: Exception) {
            options.close()
            throw e
        } catch (e: OutOfMemoryError) {
            options.close()
            throw NotEnoughMemory()
        }
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { release() }
        }
    }

    private fun release() {
        loaded?.let { model ->
            runCatching { model.encoder.close() }
            runCatching { model.decoder.close() }
            runCatching { model.options.close() }
        }
        loaded = null
        _inMemory.value = false
    }

    companion object {
        private const val TAG = "RefineEngine"

        /** The model is let go of after this long unused. */
        const val IDLE_UNLOAD_MS = 2 * 60_000L

        /** Below this much free memory the model is not even tried. */
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024
    }
}
