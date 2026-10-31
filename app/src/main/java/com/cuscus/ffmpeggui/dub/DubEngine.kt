package com.cuscus.ffmpeggui.dub

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.cuscus.ffmpeggui.core.dub.DubAudio
import com.cuscus.ffmpeggui.core.dub.DubFit
import com.cuscus.ffmpeggui.core.dub.DubLanguage
import com.cuscus.ffmpeggui.core.dub.DubPace
import com.cuscus.ffmpeggui.core.dub.DubTiming
import com.cuscus.ffmpeggui.core.voice.WavWriter
import com.cuscus.ffmpeggui.speech.AiKernels
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
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
import java.util.UUID

/**
 * Says the lines of the subtitles with a dubbing voice (Kokoro's, or a Piper
 * voice the user imported: both through sherpa-onnx, on the processor) and
 * lays them, each where its subtitle is
 * (see DubTiming), into one sound file: what the editor then places on the
 * video as a sound of its own.
 *
 * A line that would last longer than the time it has is said again, faster
 * (as fast as it should be is learnt line after line, see DubPace; up to
 * DubTiming.MAX_SPEED_UP). Each line is said in one go (DubTiming.oneBreath):
 * sherpa-onnx would otherwise say each of its sentences on its own and glue
 * them, which is heard. One model is loaded at a time (Kokoro for one
 * language, or one Piper voice) and let go of [IDLE_UNLOAD_MS] after the
 * last use.
 */
class DubEngine(context: Context, private val store: DubModelStore, private val piper: PiperVoiceStore) {

    class ModelMissing : IOException("The dubbing model is not installed")
    class NotEnoughMemory : IOException("Not enough free memory for the dubbing model")
    class NothingToSay : IOException("No line to say")

    /**
     * What a dub came out as: its file, where it starts on the output clock and
     * how long it is, and how long it took to make.
     */
    class Made(val file: File, val startMs: Long, val lengthMs: Long, val lines: Int, val tookMs: Long)

    /** One loaded model: which one, and sherpa-onnx's engine for it. */
    private class Loaded(val key: String, val tts: OfflineTts)

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loaded: Loaded? = null
    private var unloadJob: Job? = null

    private val _inMemory = MutableStateFlow(false)
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    /** Threads the model runs on: two fewer than the cores, between two and four. */
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /**
     * The dub of [lines] (on the output clock, sorted) said by [speaker], at
     * [speed] (1 = as the voice speaks), none past [endMs] (the end of the
     * video, when known). Each line starts with its subtitle: one too long is
     * made to fit (DubFit). [onProgress] gets how many lines are done of how
     * many.
     */
    suspend fun dub(
        lines: List<DubTiming.Line>,
        speaker: DubSpeaker,
        speed: Float,
        endMs: Long?,
        onProgress: (Int, Int) -> Unit,
    ): Made = withContext(Dispatchers.Default) {
        val began = System.currentTimeMillis()
        val said = lines.mapNotNull { line ->
            DubTiming.oneBreath(DubTiming.spoken(line.text)).takeIf { it.isNotEmpty() }?.let { line.copy(text = it) }
        }
        if (said.isEmpty()) throw NothingToSay()
        mutex.withLock {
            unloadJob?.cancel()
            try {
                val tts = load(speaker)
                val sid = sid(speaker)
                val rate = tts.sampleRate()
                val slots = DubTiming.slots(said, endMs)
                val clips = ArrayList<FloatArray>(said.size)
                onProgress(0, said.size)
                // How fast the voice speaks, learnt line after line: a long line is said fast
                // enough the first time, and again only when that still was not enough.
                val pace = DubPace()
                said.forEachIndexed { index, line ->
                    currentCoroutineContext().ensureActive()
                    val used = pace.speedFor(line.text, slots[index], speed)
                    var samples = say(tts, line.text, sid, used)
                    pace.learn(line.text, samples.size * 1000L / rate, used)
                    val again = DubTiming.retrySpeed(samples.size * 1000L / rate, slots[index], used, speed)
                    if (again != null) {
                        currentCoroutineContext().ensureActive()
                        val faster = say(tts, line.text, sid, again)
                        if (faster.isNotEmpty()) samples = faster
                    }
                    // What the voice could not make fit is fitted here: the next line starts on time.
                    clips += DubFit.fit(samples, rate, slots[index])
                    onProgress(index + 1, said.size)
                }
                val durations = clips.map { it.size * 1000L / rate }
                val starts = DubTiming.place(said, durations)
                val first = starts.first()
                folder.mkdirs()
                sweepOld()
                val target = File(folder, "dub-${UUID.randomUUID().toString().take(8)}.wav")
                var written = 0L
                WavWriter(target, 1, rate, comment = aiComment(speaker)).use { writer ->
                    val silence = FloatArray(rate / 10)
                    clips.forEachIndexed { index, clip ->
                        // Silence up to where the line starts, then the line.
                        val at = (starts[index] - first) * rate / 1000L
                        var gap = at - written
                        while (gap > 0) {
                            val n = minOf(gap, silence.size.toLong()).toInt()
                            writer.write(silence, n)
                            gap -= n
                            written += n
                        }
                        writer.write(clip, clip.size)
                        written += clip.size
                    }
                }
                val took = System.currentTimeMillis() - began
                Log.i(TAG, "Dub of ${said.size} lines (${written * 1000L / rate} ms) made in $took ms by ${speaker.key}")
                Made(target, first, written * 1000L / rate, said.size, took)
            } catch (e: OutOfMemoryError) {
                throw NotEnoughMemory()
            } finally {
                scheduleUnload()
            }
        }
    }

    /** [text] said by speaker [sid] at [speed]: its silence at both ends cut, its ends faded (no clicks). */
    private fun say(tts: OfflineTts, text: String, sid: Int, speed: Float): FloatArray {
        val audio = tts.generate(text, sid, speed)
        return DubAudio.trimmed(audio.samples, audio.sampleRate).also { DubAudio.fade(it, audio.sampleRate) }
    }

    /** A few words said by [speaker], to hear it: a WAV file in the cache. */
    suspend fun sample(speaker: DubSpeaker, text: String, speed: Float): File = withContext(Dispatchers.Default) {
        mutex.withLock {
            unloadJob?.cancel()
            try {
                val tts = load(speaker)
                val audio = tts.generate(DubTiming.oneBreath(text), sid(speaker), speed)
                val file = File(appContext.cacheDir, "dub-sample.wav")
                WavWriter(file, 1, audio.sampleRate, comment = aiComment(speaker)).use { it.write(audio.samples, audio.samples.size) }
                file
            } finally {
                scheduleUnload()
            }
        }
    }

    /** Frees the model's memory now. */
    fun unload() {
        scope.launch { mutex.withLock { release() } }
    }

    private fun sid(speaker: DubSpeaker): Int = when (speaker) {
        is DubSpeaker.Kokoro -> speaker.voice.id
        is DubSpeaker.Piper -> speaker.speaker
    }

    /** What the files it is heard in say of a dub: that a machine made the voice, and which (AI Act, Article 50). */
    private fun aiComment(speaker: DubSpeaker): String = "AI-generated speech: ${speaker.aiName}, made with FFmpegGUI"

    /** Which model [speaker] needs: one per language for Kokoro (its lexicons), one per voice for Piper. */
    private fun modelKey(speaker: DubSpeaker): String = when (speaker) {
        is DubSpeaker.Kokoro -> "kokoro:${speaker.language.name}"
        is DubSpeaker.Piper -> "piper:${speaker.voice.id}"
    } + if (AiKernels.fast) "+xnnpack" else ""

    private fun load(speaker: DubSpeaker): OfflineTts {
        val key = modelKey(speaker)
        loaded?.let { if (it.key == key) return it.tts }
        release()
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        val needs = if (speaker is DubSpeaker.Kokoro) MIN_FREE_BYTES else MIN_FREE_BYTES_PIPER
        if (memory.availMem in 1 until needs) throw NotEnoughMemory()
        val provider = AiKernels.provider
        val config = when (speaker) {
            is DubSpeaker.Kokoro -> kokoroConfig(speaker.language, provider)
            is DubSpeaker.Piper -> piperConfig(speaker.voice, provider)
        }
        val started = System.currentTimeMillis()
        val tts = try {
            OfflineTts(null, config)
        } catch (e: Exception) {
            // XNNPACK may not load on this device: the plain kernels then.
            if (!AiKernels.fast) throw e
            Log.w(TAG, "Could not load with XNNPACK, falling back to the CPU", e)
            OfflineTts(null, config.copy(model = config.model.copy(provider = "cpu")))
        }
        Log.i(TAG, "Dubbing model $key loaded in ${System.currentTimeMillis() - started} ms on $threads threads")
        loaded = Loaded(key, tts)
        _inMemory.value = true
        return tts
    }

    private fun kokoroConfig(language: DubLanguage, provider: String): OfflineTtsConfig {
        val model = store.file(DubModel.MODEL)
        val voices = store.file(DubModel.VOICES)
        val tokens = store.file(DubModel.TOKENS)
        val data = store.file(DubModel.ESPEAK_DATA)
        if (model == null || voices == null || tokens == null || data == null) throw ModelMissing()
        val folder = store.folder
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = model.absolutePath,
                    voices = voices.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = data.absolutePath,
                    lexicon = language.lexicons.joinToString(",") { File(folder, it).absolutePath },
                    lang = language.espeak,
                    dictDir = if (language.chinese) File(folder, DubModel.CHINESE_DICT).absolutePath else "",
                ),
                numThreads = threads,
                debug = false,
                provider = provider,
            ),
            ruleFsts = if (language.chinese) DubModel.CHINESE_RULES.joinToString(",") { File(folder, it).absolutePath } else "",
            maxNumSentences = 1,
            silenceScale = 0.15f,
        )
    }

    private fun piperConfig(voice: PiperVoiceStore.Voice, provider: String): OfflineTtsConfig {
        val data = piper.espeakData()
        if (!voice.model.isFile || !voice.tokens.isFile || data == null) throw ModelMissing()
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = voice.model.absolutePath,
                    tokens = voice.tokens.absolutePath,
                    dataDir = data.absolutePath,
                    noiseScale = voice.config.noiseScale,
                    noiseScaleW = voice.config.noiseW,
                    lengthScale = voice.config.lengthScale,
                ),
                numThreads = threads,
                debug = false,
                provider = provider,
            ),
            maxNumSentences = 1,
            silenceScale = 0.15f,
        )
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { release() }
        }
    }

    private fun release() {
        loaded?.tts?.release()
        loaded = null
        _inMemory.value = false
    }

    /** Dubs older than [KEEP_MS] go: a project that used one keeps its own copy. */
    private fun sweepOld() {
        val old = System.currentTimeMillis() - KEEP_MS
        folder.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
    }

    /** True when [path] is a dub this engine made. */
    fun owns(path: String?): Boolean = path != null && path.startsWith(folder.absolutePath + File.separator)

    companion object {
        private const val TAG = "DubEngine"
        private const val FOLDER = "dubs"

        /** The model is let go of after this long unused. */
        const val IDLE_UNLOAD_MS = 2 * 60_000L

        private const val MIN_FREE_BYTES = 350L * 1024 * 1024
        private const val MIN_FREE_BYTES_PIPER = 150L * 1024 * 1024

        /** How long a dub stays in the app's files, unused. */
        private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    }
}
