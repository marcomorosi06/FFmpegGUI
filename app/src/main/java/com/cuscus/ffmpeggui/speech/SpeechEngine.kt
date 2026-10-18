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
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.Locale

/**
 * Runs the speech model chosen in the settings (through sherpa-onnx, on the
 * processor): [active] gives its store each time the model is used, [samples]
 * the recordings to measure it on (those of any build on the phone).
 *
 * The model is loaded the first time it is needed and stays in memory while it
 * is used; [IDLE_UNLOAD_MS] after the last use it is unloaded, since it holds
 * over a gigabyte. When another build is chosen, the one in memory is let go
 * of and the new one loaded. One recognition runs at a time.
 */
class SpeechEngine(
    context: Context,
    private val active: () -> SpeechModelStore,
    private val samples: () -> List<Pair<SpeechModel.Sample, File>>,
) {

    /** What the model heard in some audio. Times are in seconds from its start. */
    class Heard(val text: String, val tokens: List<String>, val starts: FloatArray, val durations: FloatArray)

    /** How a recording that comes with the model went: what was said, what was heard, how alike. */
    class SampleResult(val language: String, val expected: String, val heard: String, val accuracy: Float, val audioMs: Long, val workMs: Long)

    /** The speed test: loading, then every recording. */
    class SpeedResult(val loadMs: Long, val samples: List<SampleResult>, val threads: Int) {
        val audioMs: Long get() = samples.sumOf { it.audioMs }
        val workMs: Long get() = samples.sumOf { it.workMs }

        /** How many seconds of speech one second of work gets through. */
        val timesRealTime: Float get() = if (workMs > 0) audioMs.toFloat() / workMs else 0f

        val accuracy: Float get() = if (samples.isEmpty()) 0f else samples.map { it.accuracy }.average().toFloat()
    }

    class NotEnoughMemory(val availableBytes: Long) : IOException("Not enough free memory for the speech model")

    class ModelMissing : IOException("The speech model is not installed")

    /** No recordings to measure the model on: they come with the int8 archive. */
    class NoRecordings : IOException("No recordings to measure the speech model on")

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recognizer: OfflineRecognizer? = null
    private var recognizerStore: SpeechModelStore? = null
    private var unloadJob: Job? = null

    private val _loaded = MutableStateFlow(false)

    /** True while the model is in memory. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Threads the model runs on: two fewer than the cores, between two and four. */
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /**
     * Runs [block] with the model loaded (loading it if needed). [onLoaded] gets
     * how long loading took, 0 when it was already in memory.
     */
    suspend fun <T> use(onLoaded: (Long) -> Unit = {}, block: suspend (Recognizer) -> T): T = withContext(Dispatchers.Default) {
        mutex.withLock {
            unloadJob?.cancel()
            val store = active()
            if (recognizer != null && recognizerStore !== store) release()
            val started = SystemClock.elapsedRealtime()
            val wasLoaded = recognizer != null
            val current = recognizer ?: load(store).also {
                recognizer = it
                recognizerStore = store
            }
            _loaded.value = true
            onLoaded(if (wasLoaded) 0L else SystemClock.elapsedRealtime() - started)
            try {
                block(Recognizer(current))
            } finally {
                scheduleUnload()
            }
        }
    }

    /** Frees the model's memory now (it loads again when next needed). */
    fun unload() {
        scope.launch {
            mutex.withLock { release() }
        }
    }

    /** Measures the model on the recordings that came with it (or with the other build). */
    suspend fun speedTest(): SpeedResult {
        val samples = samples()
        if (samples.isEmpty()) throw NoRecordings()
        var loadMs = 0L
        val results = use(onLoaded = { loadMs = it }) { recognizer ->
            samples.map { (sample, file) ->
                val wave = readWave(file)
                val started = SystemClock.elapsedRealtime()
                val heard = recognizer.recognize(wave.samples, wave.sampleRate)
                val workMs = SystemClock.elapsedRealtime() - started
                SampleResult(
                    language = sample.language,
                    expected = sample.says,
                    heard = heard.text.trim(),
                    accuracy = wordAccuracy(sample.says, heard.text),
                    audioMs = wave.samples.size * 1000L / wave.sampleRate,
                    workMs = workMs,
                )
            }
        }
        return SpeedResult(loadMs, results, threads)
    }

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

    private fun load(store: SpeechModelStore): OfflineRecognizer {
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
                provider = "cpu",
                modelType = "nemo_transducer",
            ),
            decodingMethod = "greedy_search",
        )
        val started = SystemClock.elapsedRealtime()
        val model = OfflineRecognizer(null, config)
        Log.i(TAG, "${store.model.name} loaded in ${SystemClock.elapsedRealtime() - started} ms on $threads threads")
        return model
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { release() }
        }
    }

    private fun release() {
        recognizer?.release()
        recognizer = null
        recognizerStore = null
        _loaded.value = false
    }

    companion object {
        private const val TAG = "SpeechEngine"
        const val SAMPLE_RATE = 16_000
        private const val FEATURE_DIM = 80

        /** The model is let go of after this long unused. */
        const val IDLE_UNLOAD_MS = 2 * 60_000L

        /** Below this much free memory the model is not even tried (it needs about 1.2 GB). */
        private const val MIN_FREE_BYTES = 700L * 1024 * 1024

        /** A WAV file's sound, mono. */
        class Wave(val samples: FloatArray, val sampleRate: Int)

        /** Reads a 16-bit PCM WAV file (the recordings of the model), mixed down to mono. */
        fun readWave(file: File): Wave = RandomAccessFile(file, "r").use { data ->
            val bytes = ByteArray(data.length().toInt())
            data.readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") {
                throw IOException("Not a WAV file")
            }
            var position = 12
            var channels = 1
            var rate = SAMPLE_RATE
            var bitsPerSample = 16
            while (position + 8 <= bytes.size) {
                val id = String(bytes, position, 4, Charsets.US_ASCII)
                val size = buffer.getInt(position + 4)
                val body = position + 8
                when (id) {
                    "fmt " -> {
                        channels = buffer.getShort(body + 2).toInt().coerceAtLeast(1)
                        rate = buffer.getInt(body + 4)
                        bitsPerSample = buffer.getShort(body + 14).toInt()
                    }
                    "data" -> {
                        if (bitsPerSample != 16) throw IOException("Only 16-bit WAV files are read")
                        val frames = minOf(size, bytes.size - body) / (2 * channels)
                        val samples = FloatArray(frames) { frame ->
                            var sum = 0f
                            for (c in 0 until channels) sum += buffer.getShort(body + (frame * channels + c) * 2) / 32768f
                            sum / channels
                        }
                        return@use Wave(samples, rate)
                    }
                }
                position = body + size + (size and 1)
            }
            throw IOException("WAV file without sound")
        }

        /** Share of the words of [expected] found in [heard], in order (1 = all), ignoring case and punctuation. */
        fun wordAccuracy(expected: String, heard: String): Float {
            val a = words(expected)
            val b = words(heard)
            if (a.isEmpty()) return if (b.isEmpty()) 1f else 0f
            var previous = IntArray(b.size + 1) { it }
            for (i in 1..a.size) {
                val current = IntArray(b.size + 1)
                current[0] = i
                for (j in 1..b.size) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                previous = current
            }
            return (1f - previous[b.size].toFloat() / a.size).coerceIn(0f, 1f)
        }

        private fun words(text: String): List<String> =
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFC)
                .split(Regex("[^\\p{L}\\p{N}']+"))
                .filter { it.isNotBlank() }
    }
}
