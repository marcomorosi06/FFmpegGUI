package com.cuscus.ffmpeggui.speech

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Runs the speech model kept in [store] (through sherpa-onnx, on the
 * processor).
 *
 * The model goes through the app's ModelGate: loaded for one [use] (a whole
 * file's words, say), alone in memory, and let go of as soon as it is done,
 * since it holds over a gigabyte.
 */
class SpeechEngine(
    context: Context,
    private val store: SpeechModelStore,
) {

    /** What the model heard in some audio. Times are in seconds from its start. */
    class Heard(val text: String, val tokens: List<String>, val starts: FloatArray, val durations: FloatArray)

    class NotEnoughMemory(val availableBytes: Long) : IOException("Not enough free memory for the speech model")

    class ModelMissing : IOException("The speech model is not installed")

    private val appContext = context.applicationContext
    private val slot = ModelSlot("Speech model") { release() }
    private var recognizer: OfflineRecognizer? = null

    private val _loaded = MutableStateFlow(false)

    /** True while the model is in memory. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Threads the model runs on: two fewer than the cores, between two and four. */
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** Runs [block] with the model loaded (loading it if needed), then lets it go. */
    suspend fun <T> use(block: suspend (Recognizer) -> T): T = withContext(Dispatchers.Default) {
        slot.run {
            val current = recognizer ?: load().also { recognizer = it }
            _loaded.value = true
            block(Recognizer(current))
        }
    }

    /** Frees the model's memory now (it loads again when next needed). */
    fun unload() = slot.unload()

    /** The loaded model, for one [use]. */
    inner class Recognizer internal constructor(private val model: OfflineRecognizer) {
        /** What is said in [samples] (mono, -1..1) at [sampleRate]. */
        fun recognize(samples: FloatArray, sampleRate: Int): Heard {
            val stream = model.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                model.decode(stream)
                val result = model.getResult(stream)
                return Heard(result.text, result.tokens.toList(), result.timestamps, result.durations)
            } finally {
                stream.release()
            }
        }
    }

    // ---------------------------------------------------------------------

    private fun load(): OfflineRecognizer {
        val encoder = store.file(SpeechModel.Role.ENCODER)
        val decoder = store.file(SpeechModel.Role.DECODER)
        val joiner = store.file(SpeechModel.Role.JOINER)
        val tokens = store.file(SpeechModel.Role.TOKENS)
        // Only files that hashed right at import are ever handed to the native runtime:
        // damaged ones would bring the whole app down instead of failing here.
        if (encoder == null || decoder == null || joiner == null || tokens == null || store.state.value !is SpeechModelStore.State.Ready) {
            throw ModelMissing()
        }
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        if (memory.availMem in 1 until MIN_FREE_BYTES) throw NotEnoughMemory(memory.availMem)

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = threads,
                debug = false,
                provider = AiKernels.provider,
                modelType = "nemo_transducer",
            ),
            decodingMethod = "greedy_search",
        )
        val started = SystemClock.elapsedRealtime()
        val model = OfflineRecognizer(null, config)
        Log.i(TAG, "${store.model.name} loaded in ${SystemClock.elapsedRealtime() - started} ms on $threads threads")
        return model
    }

    private fun release() {
        recognizer?.release()
        recognizer = null
        _loaded.value = false
    }

    companion object {
        private const val TAG = "SpeechEngine"
        const val SAMPLE_RATE = 16_000
        private const val FEATURE_DIM = 80

        /** Below this much free memory the model is not even tried (it needs about 1.2 GB). */
        private const val MIN_FREE_BYTES = 700L * 1024 * 1024
    }
}
