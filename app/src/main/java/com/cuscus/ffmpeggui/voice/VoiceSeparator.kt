package com.cuscus.ffmpeggui.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.voice.PcmStereoFile
import com.cuscus.ffmpeggui.core.voice.VoiceSeparation
import com.cuscus.ffmpeggui.core.voice.VoiceStem
import com.cuscus.ffmpeggui.core.voice.WavWriter
import com.cuscus.ffmpeggui.media.FfmpegAudioExtractor
import com.cuscus.ffmpeggui.speech.AiKernels
import com.cuscus.ffmpeggui.speech.SpeechModel
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Sets the voice of a media file apart from the rest of its sound, with the
 * voice model ([VoiceModel], through ONNX Runtime, on the processor), and
 * keeps the result: one FLAC file per media file, four channels (the voice's
 * two, then the rest's two, see VoiceStem), on the file's own clock.
 *
 * The sound is first taken out with FFmpeg (44.1 kHz stereo, 16-bit, in the
 * cache), separated about ten seconds at a time (VoiceSeparation) into a WAV
 * file, which FFmpeg then packs losslessly into the kept FLAC. However long
 * the file, memory stays the same; the cache needs about three times the
 * sound's raw size while it works.
 *
 * Results are kept in the app's private files, one per media file (by its
 * address), so that a file separated once is never separated again, in any
 * project. The models stay in memory [IDLE_UNLOAD_MS] after the last use.
 */
class VoiceSeparator(
    context: Context,
    private val store: SpeechModelStore,
    private val extractor: FfmpegAudioExtractor,
) {

    /** Where the work on one file is. */
    sealed interface Step {
        /** Taking the sound out of the file: [fraction] done, null while unknown. */
        data class Extracting(val fraction: Float?) : Step

        /** Loading the models into memory. */
        data object Loading : Step

        /** Setting the voice apart: [fraction] of the sound done. */
        data class Separating(val fraction: Float) : Step

        /** Packing the result: [fraction] done, null while unknown. */
        data class Saving(val fraction: Float?) : Step
    }

    class ModelMissing : IOException("The voice model is not installed")
    class NotEnoughMemory : IOException("Not enough free memory for the voice model")
    class NoSound : IOException("No sound to separate")
    class NoSpace : IOException("Not enough free space to separate the voice")

    private class Loaded(val vocals: OrtSession, val accompaniment: OrtSession, val options: OrtSession.SessionOptions) {
        fun close() {
            runCatching { vocals.close() }
            runCatching { accompaniment.close() }
            runCatching { options.close() }
        }
    }

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)
    private val cache = File(appContext.cacheDir, FOLDER)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loaded: Loaded? = null
    private var unloadJob: Job? = null

    private val _inMemory = MutableStateFlow(false)
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    private val _kept = MutableStateFlow(keptNow())

    /** How many files have their voice set apart, and the space that takes. */
    val kept: StateFlow<Kept> = _kept.asStateFlow()

    data class Kept(val files: Int, val bytes: Long)

    /** Threads a model runs on: two fewer than the cores, between two and four. */
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** True when the model is installed and checked. */
    val ready: Boolean get() = store.state.value is SpeechModelStore.State.Ready

    /** The stems of the file at [uri] when it was separated before, else null. */
    fun existing(uri: Uri): File? = File(folder, name(uri)).takeIf { it.isFile && it.length() > 0 }

    /**
     * The stems of the file at [uri] ([durationMs] long, 0 when unknown), made
     * now unless they are there already. [onStep] tells how far it is.
     */
    suspend fun separate(uri: Uri, durationMs: Long, onStep: (Step) -> Unit): File = withContext(Dispatchers.Default) {
        existing(uri)?.let { return@withContext it }
        mutex.withLock {
            unloadJob?.cancel()
            val id = UUID.randomUUID().toString()
            val pcm = File(cache, "$id.pcm")
            val wav = File(cache, "$id.wav")
            try {
                cache.mkdirs()
                // The raw sound, the four channels of the result and its packed copy.
                val rawBytes = if (durationMs > 0) durationMs / 1000.0 * VoiceSeparation.SAMPLE_RATE * PcmStereoFile.BYTES_PER_FRAME else 0.0
                if (rawBytes > 0 && freeBytes(cache) < (rawBytes * SPACE_FACTOR).toLong()) throw NoSpace()
                onStep(Step.Extracting(null))
                when (extractor.extractStereo(uri.toString(), pcm, durationMs, VoiceSeparation.SAMPLE_RATE) { onStep(Step.Extracting(it)) }) {
                    ExecutionResult.Success -> Unit
                    ExecutionResult.Cancelled -> throw CancellationException("Stopped")
                    is ExecutionResult.Failed -> throw NoSound()
                }
                if (pcm.length() < PcmStereoFile.BYTES_PER_FRAME) throw NoSound()
                onStep(Step.Loading)
                val model = load()
                onStep(Step.Separating(0f))
                val context = currentCoroutineContext()
                PcmStereoFile(pcm).use { source ->
                    WavWriter(wav, VoiceStem.CHANNELS, VoiceSeparation.SAMPLE_RATE).use { writer ->
                        val frame = FloatArray(VoiceSeparation.CORE_FRAMES * VoiceSeparation.HOP * VoiceStem.CHANNELS)
                        VoiceSeparation { magnitudes -> estimate(model, magnitudes) }.separate(
                            source = source,
                            sink = { voice, rest, count ->
                                VoiceStem.interleave(voice, rest, count, frame)
                                writer.write(frame, count)
                            },
                            onProgress = { onStep(Step.Separating(it)) },
                            checkCancelled = { context.ensureActive() },
                        )
                    }
                }
                pcm.delete()
                onStep(Step.Saving(null))
                folder.mkdirs()
                val target = File(folder, name(uri))
                val part = File(folder, target.name + ".part")
                when (extractor.encodeFlac(wav, part, durationMs) { onStep(Step.Saving(it)) }) {
                    ExecutionResult.Success -> Unit
                    ExecutionResult.Cancelled -> throw CancellationException("Stopped")
                    is ExecutionResult.Failed -> {
                        part.delete()
                        throw IOException("Could not pack the stems")
                    }
                }
                if (!part.renameTo(target)) throw IOException("Could not keep the stems")
                _kept.value = keptNow()
                target
            } catch (e: OutOfMemoryError) {
                throw NotEnoughMemory()
            } finally {
                pcm.delete()
                wav.delete()
                scheduleUnload()
            }
        }
    }

    /** Removes the stems of [uri] (a project that uses them goes back to the file's own sound). */
    fun delete(uri: Uri) {
        File(folder, name(uri)).delete()
        _kept.value = keptNow()
    }

    /** Removes every file's stems. */
    fun deleteAll() {
        folder.listFiles()?.forEach { it.delete() }
        _kept.value = keptNow()
    }

    /** Removes what a stopped or killed run left behind. */
    fun sweep() {
        cache.listFiles()?.forEach { it.delete() }
        folder.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /** Frees the models' memory now. */
    fun unload() {
        scope.launch { mutex.withLock { release() } }
    }

    private fun estimate(model: Loaded, magnitudes: FloatArray): Pair<FloatArray, FloatArray> {
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(2, 1, VoiceSeparation.FRAMES.toLong(), VoiceSeparation.BINS.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(magnitudes), shape).use { input ->
            fun run(session: OrtSession): FloatArray =
                session.run(mapOf(session.inputNames.first() to input)).use { result ->
                    val buffer = (result.get(0) as OnnxTensor).floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            run(model.vocals) to run(model.accompaniment)
        }
    }

    private fun load(): Loaded {
        loaded?.let { return it }
        val vocals = store.file(SpeechModel.Role.ENCODER)
        val accompaniment = store.file(SpeechModel.Role.DECODER)
        if (!ready || vocals == null || accompaniment == null) throw ModelMissing()
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        if (memory.availMem in 1 until MIN_FREE_BYTES) throw NotEnoughMemory()
        val env = OrtEnvironment.getEnvironment()
        val options = AiKernels.options(threads)
        try {
            val first = env.createSession(vocals.absolutePath, options)
            val second = try {
                env.createSession(accompaniment.absolutePath, options)
            } catch (e: Exception) {
                first.close()
                throw e
            }
            Log.i(TAG, "Voice model loaded on $threads threads")
            return Loaded(first, second, options).also {
                loaded = it
                _inMemory.value = true
            }
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
        loaded?.close()
        loaded = null
        _inMemory.value = false
    }

    private fun keptNow(): Kept {
        val files = folder.listFiles()?.filter { it.isFile && it.name.endsWith(".flac") }.orEmpty()
        return Kept(files.size, files.sumOf { it.length() })
    }

    private fun freeBytes(dir: File): Long = try {
        StatFs(dir.absolutePath).availableBytes
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    /** The stems' file name: made of the media file's address. */
    private fun name(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) } + ".flac"
    }

    companion object {
        private const val TAG = "VoiceSeparator"
        private const val FOLDER = "voice-stems"

        /** The models are let go of after this long unused. */
        const val IDLE_UNLOAD_MS = 2 * 60_000L

        /** Below this much free memory the models are not even tried. */
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024

        /** Cache needed, in raw sounds: the sound itself, its four channels and a margin. */
        private const val SPACE_FACTOR = 3.5
    }
}
