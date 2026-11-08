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
import com.cuscus.ffmpeggui.core.stems.BanditMasks
import com.cuscus.ffmpeggui.core.stems.SpleeterMasks
import com.cuscus.ffmpeggui.core.stems.StemKind
import com.cuscus.ffmpeggui.core.stems.StemSeparation
import com.cuscus.ffmpeggui.core.voice.PcmStereoFile
import com.cuscus.ffmpeggui.core.voice.VoiceStem
import com.cuscus.ffmpeggui.core.voice.WavWriter
import com.cuscus.ffmpeggui.media.FfmpegAudioExtractor
import com.cuscus.ffmpeggui.speech.AiKernels
import com.cuscus.ffmpeggui.speech.ModelSlot
import com.cuscus.ffmpeggui.speech.SpeechModel
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Splits the sound of a media file into stems with the stem models
 * ([StemModels], through ONNX Runtime, on the processor), and keeps them:
 *
 *  - [separate]: Bandit v2 finds the dialogue, the music and the effects.
 *    Kept as one stereo FLAC each, and as the four-channel file the voice
 *    levels and the dub read (the dialogue's two channels, then the music's
 *    and the effects' together: see VoiceStem), on the file's own clock.
 *  - [separateInstruments]: Spleeter's five stems split that music in turn
 *    into singing, piano, drums, bass and the rest, one stereo FLAC each.
 *
 * The sound is taken out with FFmpeg (stereo, 16-bit, at the model's rate,
 * in the cache), split about ten seconds at a time (core.stems) into WAV
 * files, which FFmpeg then packs losslessly into the kept FLACs. However long
 * the file, memory stays the same; the cache needs a few times the sound's
 * raw size while it works.
 *
 * Results are kept in the app's private files, by the media file's address,
 * so that a file split once is never split again, in any project. Files left
 * by the voice separation before (Spleeter's two stems: the four-channel file
 * alone) keep working for the voice levels; splitting them into tracks makes
 * them again with Bandit. The models go through the app's ModelGate: loaded
 * only while they work (not while FFmpeg takes the sound out or packs it),
 * one at a time and alone in memory, and let go of as soon as they are done.
 */
class VoiceSeparator(
    context: Context,
    private val sceneStore: SpeechModelStore,
    private val instrumentsStore: SpeechModelStore,
    private val extractor: FfmpegAudioExtractor,
) {

    /** Where the work on one file is. */
    sealed interface Step {
        /** Taking the sound out of the file: [fraction] done, null while unknown. */
        data class Extracting(val fraction: Float?) : Step

        /** Loading a model into memory: Bandit, or Spleeter for the [instruments]. */
        data class Loading(val instruments: Boolean = false) : Step

        /** Splitting the sound: [fraction] done; into its [instruments] when true. */
        data class Separating(val fraction: Float, val instruments: Boolean = false) : Step

        /** Packing the result: [fraction] done, null while unknown. */
        data class Saving(val fraction: Float?) : Step
    }

    class ModelMissing(val instruments: Boolean = false) : IOException("A stem model is not installed")
    class NotEnoughMemory : IOException("Not enough free memory for the stem model")
    class NoSound : IOException("No sound to separate")
    class NoSpace : IOException("Not enough free space to separate the sound")

    private enum class Which { SCENE, INSTRUMENTS }

    private class Loaded(val which: Which, val session: OrtSession, val options: OrtSession.SessionOptions) {
        fun close() {
            runCatching { session.close() }
            runCatching { options.close() }
        }
    }

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)
    private val cache = File(appContext.cacheDir, FOLDER)
    /** One file at a time; the models themselves wait their turn at the gate. */
    private val mutex = Mutex()
    private val slot = ModelSlot("Stem model") { release() }
    private var loaded: Loaded? = null

    private val _inMemory = MutableStateFlow(false)
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    private val _kept = MutableStateFlow(keptNow())

    /** How many files are split, and the space that takes. */
    val kept: StateFlow<Kept> = _kept.asStateFlow()

    data class Kept(val files: Int, val bytes: Long)

    /** Threads a model runs on: two fewer than the cores, between two and four. */
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** True when Bandit is installed and checked: the voice can be set apart. */
    val ready: Boolean get() = sceneStore.state.value is SpeechModelStore.State.Ready

    /** True when Spleeter's five stems are installed and checked: the music can be split. */
    val instrumentsReady: Boolean get() = instrumentsStore.state.value is SpeechModelStore.State.Ready

    /** The four-channel stems of the file at [uri] (voice | rest) when it was separated before, else null. */
    fun existing(uri: Uri): File? = File(folder, name(uri) + EXTENSION).takeIf { it.isFile && it.length() > 0 }

    /** Every stem of the file at [uri] kept as a track of its own, in StemKind's order. */
    fun tracks(uri: Uri): Map<StemKind, File> = StemKind.entries.mapNotNull { kind ->
        stemFile(uri, kind).takeIf { it.isFile && it.length() > 0 }?.let { kind to it }
    }.toMap()

    /** True when [sound] (a placed sound's address) is a stem of [kind] this separator keeps. */
    fun isStemTrack(sound: Uri, kind: StemKind): Boolean {
        val file = sound.path?.let(::File) ?: return false
        return sound.scheme == "file" && file.parentFile == folder && file.name.endsWith("." + kind.id + EXTENSION)
    }

    /** True when the dialogue, the music and the effects of [uri] are kept as tracks. */
    fun hasScene(uri: Uri): Boolean = tracks(uri).keys.containsAll(StemKind.SCENE)

    /** True when the instruments of [uri]'s music are kept as tracks. */
    fun hasInstruments(uri: Uri): Boolean = tracks(uri).keys.containsAll(StemKind.INSTRUMENTS)

    /**
     * The four-channel stems of the file at [uri] ([durationMs] long, 0 when
     * unknown), made now (with every Bandit stem) unless they are there
     * already. [onStep] tells how far it is.
     */
    suspend fun separate(uri: Uri, durationMs: Long, onStep: (Step) -> Unit): File = withContext(Dispatchers.Default) {
        mutex.withLock { scene(uri, durationMs, onStep) }
    }

    /**
     * Splits the music of the file at [uri] into its instruments, splitting
     * the file into dialogue, music and effects first when that is not done.
     * Returns the instruments' tracks.
     */
    suspend fun separateInstruments(uri: Uri, durationMs: Long, onStep: (Step) -> Unit): Map<StemKind, File> = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (!hasInstruments(uri)) {
                if (!instrumentsReady) throw ModelMissing(instruments = true)
                if (!hasScene(uri)) scene(uri, durationMs, onStep)
                val music = stemFile(uri, StemKind.MUSIC)
                split(
                    which = Which.INSTRUMENTS,
                    layout = SpleeterMasks.LAYOUT,
                    durationMs = durationMs,
                    kinds = StemKind.INSTRUMENTS,
                    extract = { pcm, progress -> extractor.extractStereo(music, pcm, durationMs, SpleeterMasks.SAMPLE_RATE, progress) },
                    uri = uri,
                    onStep = onStep,
                    master = false,
                )
            }
            tracks(uri).filterKeys { it.isInstrument }
        }
    }

    private suspend fun scene(uri: Uri, durationMs: Long, onStep: (Step) -> Unit): File {
        val master = existing(uri)
        if (master != null && hasScene(uri)) return master
        if (!ready) throw ModelMissing()
        split(
            which = Which.SCENE,
            layout = BanditMasks.LAYOUT,
            durationMs = durationMs,
            kinds = StemKind.SCENE,
            extract = { pcm, progress -> extractor.extractStereo(uri.toString(), pcm, durationMs, BanditMasks.SAMPLE_RATE, progress) },
            uri = uri,
            onStep = onStep,
            master = true,
        )
        return existing(uri) ?: throw IOException("Could not keep the stems")
    }

    /**
     * Takes the sound out with [extract], splits it into [kinds] with the
     * model [which] and keeps each as a stereo FLAC; with [master], also the
     * four-channel file (the first stem, then the others together).
     */
    private suspend fun split(
        which: Which,
        layout: StemSeparation.Layout,
        durationMs: Long,
        kinds: List<StemKind>,
        extract: suspend (File, (Float) -> Unit) -> ExecutionResult,
        uri: Uri,
        onStep: (Step) -> Unit,
        master: Boolean,
    ) {
        val instruments = which == Which.INSTRUMENTS
        val id = UUID.randomUUID().toString()
        val pcm = File(cache, "$id.pcm")
        val wavs = kinds.map { File(cache, "$id.${it.id}.wav") }
        val masterWav = File(cache, "$id.voice.wav")
        try {
            cache.mkdirs()
            // The raw sound, each stem's two channels, the four of the master and a margin.
            val rawBytes = if (durationMs > 0) durationMs / 1000.0 * layout.sampleRate * PcmStereoFile.BYTES_PER_FRAME else 0.0
            val factor = 1.5 + kinds.size + if (master) 2 else 0
            if (rawBytes > 0 && freeBytes(cache) < (rawBytes * factor).toLong()) throw NoSpace()
            onStep(Step.Extracting(null))
            when (extract(pcm) { onStep(Step.Extracting(it)) }) {
                ExecutionResult.Success -> Unit
                ExecutionResult.Cancelled -> throw CancellationException("Stopped")
                is ExecutionResult.Failed -> throw NoSound()
            }
            if (pcm.length() < PcmStereoFile.BYTES_PER_FRAME) throw NoSound()
            onStep(Step.Loading(instruments))
            val context = currentCoroutineContext()
            slot.run {
                val session = load(which)
                onStep(Step.Separating(0f, instruments))
                val model: StemSeparation.Model = when (which) {
                    Which.SCENE -> BanditMasks { spectrogram, frames -> runBandit(session, spectrogram, frames) }
                    Which.INSTRUMENTS -> SpleeterMasks { magnitudes -> runSpleeter(session, magnitudes) }
                }
                PcmStereoFile(pcm).use { source ->
                    val writers = wavs.map { WavWriter(it, 2, layout.sampleRate) }
                    val masterWriter = if (master) WavWriter(masterWav, VoiceStem.CHANNELS, layout.sampleRate) else null
                    try {
                        val frame = FloatArray(layout.core * layout.hop * VoiceStem.CHANNELS)
                        val rest = FloatArray(layout.core * layout.hop * 2)
                        StemSeparation(layout, model).separate(
                            source = source,
                            sink = { stems, count ->
                                for (s in stems.indices) writers[s].write(stems[s], count)
                                if (masterWriter != null) {
                                    // The voice is the first stem, the rest all the others together.
                                    java.util.Arrays.fill(rest, 0, count * 2, 0f)
                                    for (s in 1 until stems.size) for (i in 0 until count * 2) rest[i] += stems[s][i]
                                    VoiceStem.interleave(stems[0], rest, count, frame)
                                    masterWriter.write(frame, count)
                                }
                            },
                            onProgress = { onStep(Step.Separating(it, instruments)) },
                            checkCancelled = { context.ensureActive() },
                        )
                    } finally {
                        writers.forEach { runCatching { it.close() } }
                        masterWriter?.let { runCatching { it.close() } }
                    }
                }
            }
            pcm.delete()
            folder.mkdirs()
            // Packed one after the other: the master last, so that a file with a master always has its tracks.
            val packs = kinds.indices.map { wavs[it] to stemFile(uri, kinds[it]) } +
                (if (master) listOf(masterWav to File(folder, name(uri) + EXTENSION)) else emptyList())
            packs.forEachIndexed { index, (wav, target) ->
                onStep(Step.Saving(index.toFloat() / packs.size))
                val part = File(folder, target.name + ".part")
                when (extractor.encodeFlac(wav, part, durationMs) { onStep(Step.Saving((index + it) / packs.size)) }) {
                    ExecutionResult.Success -> Unit
                    ExecutionResult.Cancelled -> throw CancellationException("Stopped")
                    is ExecutionResult.Failed -> {
                        part.delete()
                        throw IOException("Could not pack the stems")
                    }
                }
                wav.delete()
                target.delete()
                if (!part.renameTo(target)) throw IOException("Could not keep the stems")
            }
            _kept.value = keptNow()
        } catch (e: OutOfMemoryError) {
            throw NotEnoughMemory()
        } finally {
            pcm.delete()
            wavs.forEach { it.delete() }
            masterWav.delete()
        }
    }

    /** Removes every stem of [uri] (a project that uses them goes back to the file's own sound). */
    fun delete(uri: Uri) {
        val prefix = name(uri)
        folder.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
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

    /** Frees the model's memory now. */
    fun unload() = slot.unload()

    private fun runBandit(session: OrtSession, spectrogram: FloatArray, frames: Int): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(1, (BanditMasks.N / 2 + 1).toLong(), frames.toLong(), 2)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(spectrogram), shape).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                val buffer = (result.get(0) as OnnxTensor).floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
    }

    private fun runSpleeter(session: OrtSession, magnitudes: FloatArray): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(2, 1, SpleeterMasks.FRAMES.toLong(), SpleeterMasks.BINS.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(magnitudes), shape).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                val buffer = (result.get(0) as OnnxTensor).floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
    }

    private fun load(which: Which): OrtSession {
        loaded?.let { if (it.which == which) return it.session else release() }
        val store = if (which == Which.SCENE) sceneStore else instrumentsStore
        val file = store.file(SpeechModel.Role.ENCODER)
        if (store.state.value !is SpeechModelStore.State.Ready || file == null) throw ModelMissing(which == Which.INSTRUMENTS)
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        if (memory.availMem in 1 until MIN_FREE_BYTES) throw NotEnoughMemory()
        val env = OrtEnvironment.getEnvironment()
        val options = AiKernels.options(threads)
        try {
            val session = env.createSession(file.absolutePath, options)
            Log.i(TAG, "${store.model.name} loaded on $threads threads")
            loaded = Loaded(which, session, options)
            _inMemory.value = true
            return session
        } catch (e: Exception) {
            options.close()
            throw e
        } catch (e: OutOfMemoryError) {
            options.close()
            throw NotEnoughMemory()
        }
    }

    private fun release() {
        loaded?.close()
        loaded = null
        _inMemory.value = false
    }

    private fun keptNow(): Kept {
        val files = folder.listFiles()?.filter { it.isFile && it.name.endsWith(EXTENSION) }.orEmpty()
        return Kept(files.count { MASTER.matches(it.name) }, files.sumOf { it.length() })
    }

    private fun freeBytes(dir: File): Long = try {
        StatFs(dir.absolutePath).availableBytes
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    private fun stemFile(uri: Uri, kind: StemKind) = File(folder, name(uri) + "." + kind.id + EXTENSION)

    /** The stems' file name, without its extension: made of the media file's address. */
    private fun name(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }

    companion object {
        private const val TAG = "VoiceSeparator"
        private const val FOLDER = "voice-stems"
        private const val EXTENSION = ".flac"

        /** A media file's four-channel stems: its name alone (its tracks add the stem's). */
        private val MASTER = Regex("[0-9a-f]{32}\\.flac")

        /** Below this much free memory the models are not even tried. */
        private const val MIN_FREE_BYTES = 450L * 1024 * 1024
    }
}
