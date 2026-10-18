package com.cuscus.ffmpeggui.cutout

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.cuscus.ffmpeggui.core.cutout.SubjectModelIo
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

/**
 * Runs the cut-out model chosen in the settings (through ONNX Runtime, on the
 * processor): [active] gives its store each time a picture is cut out.
 *
 * The model is loaded the first time it is needed and stays in memory while it
 * is used; [IDLE_UNLOAD_MS] after the last use it is let go of. When another
 * model is chosen, the one in memory is let go of and the new one loaded. One
 * picture at a time.
 */
class CutoutEngine(context: Context, private val active: () -> CutoutModelStore) {

    /** What the model found: [values] (0..255, row by row) on a [side] x [side] square, by [model]. */
    class Mask(val side: Int, val values: IntArray, val model: CutoutModel)

    /** The speed test: loading, then one picture. */
    class SpeedResult(val loadMs: Long, val workMs: Long, val threads: Int)

    class NotEnoughMemory(val availableBytes: Long) : IOException("Not enough free memory for the cut-out model")

    class ModelMissing : IOException("The cut-out model is not installed")

    private class Loaded(
        val store: CutoutModelStore,
        val environment: OrtEnvironment,
        val options: OrtSession.SessionOptions,
        val session: OrtSession,
        val input: String,
    )

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loaded: Loaded? = null
    private var unloadJob: Job? = null

    private val _inMemory = MutableStateFlow(false)

    /** True while the model is in memory. */
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    /** Threads the model runs on: two fewer than the cores, between two and four. */
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** The model chosen now. */
    val model: CutoutModel get() = active().model

    /**
     * How much of each pixel of a picture is the subject, by the model chosen
     * now. [picture] gets the side of the square the model sees and gives the
     * picture squeezed to it (ARGB, row by row). Loads the model if needed;
     * [onLoaded] gets how long that took, 0 when it was in memory already.
     */
    suspend fun mask(picture: (side: Int) -> IntArray, onLoaded: (Long) -> Unit = {}): Mask = withContext(Dispatchers.Default) {
        mutex.withLock {
            unloadJob?.cancel()
            val store = active()
            if (loaded != null && loaded?.store !== store) release()
            val started = SystemClock.elapsedRealtime()
            val wasLoaded = loaded != null
            val model = loaded ?: load(store).also { loaded = it }
            _inMemory.value = true
            onLoaded(if (wasLoaded) 0L else SystemClock.elapsedRealtime() - started)
            try {
                val net = store.model.net
                val argb = picture(net.side)
                Mask(net.side, run(model, SubjectModelIo.input(argb, net), net.side), store.model)
            } finally {
                scheduleUnload()
            }
        }
    }

    /** Frees the model's memory now (it loads again when next needed). */
    fun unload() {
        scope.launch { mutex.withLock { release() } }
    }

    /** Measures the model on a made-up picture: how long it loads, how long one picture takes. */
    suspend fun speedTest(): SpeedResult {
        // A light background with a darker disc in the middle: something to find.
        val picture = { side: Int ->
            IntArray(side * side) { i ->
                val x = i % side - side / 2
                val y = i / side - side / 2
                if (x * x + y * y < (side / 4) * (side / 4)) 0xFF8A3B2E.toInt() else 0xFFD8E4EC.toInt()
            }
        }
        var loadMs = 0L
        val started = SystemClock.elapsedRealtime()
        mask(picture) { loadMs = it }
        val workMs = SystemClock.elapsedRealtime() - started - loadMs
        return SpeedResult(loadMs, workMs, threads)
    }

    private fun run(model: Loaded, input: FloatArray, side: Int): IntArray {
        val edge = side.toLong()
        val output = OnnxTensor.createTensor(model.environment, FloatBuffer.wrap(input), longArrayOf(1, 3, edge, edge)).use { tensor ->
            model.session.run(mapOf(model.input to tensor)).use { result ->
                val values = (result.get(0) as OnnxTensor).floatBuffer
                FloatArray(values.remaining()).also { values.get(it) }
            }
        }
        if (output.size != side * side) throw IOException("Unexpected output of ${output.size} values")
        return SubjectModelIo.mask(output)
    }

    private fun load(store: CutoutModelStore): Loaded {
        val file = store.file
        if (!file.isFile || !store.ready) throw ModelMissing()
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        if (memory.availMem in 1 until MIN_FREE_BYTES) throw NotEnoughMemory(memory.availMem)
        val started = SystemClock.elapsedRealtime()
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(threads)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val session = environment.createSession(file.absolutePath, options)
            val input = session.inputNames.firstOrNull() ?: throw IOException("The model has no input")
            Log.i(TAG, "${store.model.name} loaded in ${SystemClock.elapsedRealtime() - started} ms on $threads threads")
            return Loaded(store, environment, options, session, input)
        } catch (e: Exception) {
            options.close()
            throw e
        } catch (e: OutOfMemoryError) {
            options.close()
            throw NotEnoughMemory(memory.availMem)
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
            runCatching { model.session.close() }
            runCatching { model.options.close() }
        }
        loaded = null
        _inMemory.value = false
    }

    companion object {
        private const val TAG = "CutoutEngine"

        /** The model is let go of after this long unused. */
        const val IDLE_UNLOAD_MS = 2 * 60_000L

        /** Below this much free memory the model is not even tried (the precise one needs about a gigabyte). */
        private const val MIN_FREE_BYTES = 600L * 1024 * 1024
    }
}
