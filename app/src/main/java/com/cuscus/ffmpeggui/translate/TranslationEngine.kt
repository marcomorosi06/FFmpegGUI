package com.cuscus.ffmpeggui.translate

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.cuscus.ffmpeggui.core.translate.GreedyDecoder
import com.cuscus.ffmpeggui.core.translate.MarianConfig
import com.cuscus.ffmpeggui.core.translate.MarianTokenizer
import com.cuscus.ffmpeggui.core.translate.SentencePieceModel
import com.cuscus.ffmpeggui.core.translate.TranslationStep
import com.cuscus.ffmpeggui.speech.AiKernels
import com.cuscus.ffmpeggui.speech.ModelSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Translates lines of text with the models on the phone (through ONNX
 * Runtime, on the processor), one model after the other when the way goes
 * through English. Each line is encoded once, then the decoder writes the
 * translation a token at a time, the likeliest each time (GreedyDecoder).
 *
 * The models go through the app's ModelGate: one in memory at a time (the
 * way through English lets the first go before the second loads), and let go
 * of as soon as the lines are translated.
 */
class TranslationEngine(context: Context, private val store: TranslationModelStore) {

    class ModelMissing(id: String) : IOException("The translation model $id is not installed")
    class NotEnoughMemory : IOException("Not enough free memory for the translation model")

    private class Loaded(
        val id: String,
        val tokenizer: MarianTokenizer,
        val encoder: OrtSession,
        val decoder: OrtSession,
        val options: OrtSession.SessionOptions,
    ) {
        fun close() {
            runCatching { encoder.close() }
            runCatching { decoder.close() }
            runCatching { options.close() }
        }
    }

    private val appContext = context.applicationContext
    private val slot = ModelSlot("Translation model") { releaseAll() }
    private val loaded = LinkedHashMap<String, Loaded>()

    private val _inMemory = MutableStateFlow(false)
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    /** Threads a model runs on: two fewer than the cores, between two and four. */
    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /**
     * [texts] translated along [steps]; [onProgress] gets how many lines are
     * done of how many (all steps counted). An empty line stays empty.
     */
    suspend fun translate(texts: List<String>, steps: List<TranslationStep>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<String> =
        withContext(Dispatchers.Default) {
            slot.run {
                var current = texts
                val total = texts.size * steps.size.coerceAtLeast(1)
                var done = 0
                for (step in steps) {
                    val model = load(step.model)
                    // A model that writes several languages is told which by a token before the text.
                    val target = if (model.tokenizer.targetTokens.isNotEmpty()) step.to else null
                    current = current.map { line ->
                        currentCoroutineContext().ensureActive()
                        val out = if (line.isBlank()) line else translateOne(model, line, target)
                        done++
                        onProgress(done, total)
                        out
                    }
                }
                current
            }
        }

    /** Frees the models' memory now. */
    fun unload() = slot.unload()

    private fun translateOne(model: Loaded, text: String, target: String?): String {
        val env = OrtEnvironment.getEnvironment()
        val ids = model.tokenizer.encode(text, target)
        val length = ids.size.toLong()
        val mask = LongArray(ids.size) { 1L }
        // The encoder, once: what the decoder looks at for every token it writes.
        val (hidden, hiddenShape) = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, length)).use { input ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, length)).use { attention ->
                val feeds = HashMap<String, OnnxTensor>()
                for (name in model.encoder.inputNames) {
                    when {
                        name.contains("mask") -> feeds[name] = attention
                        name.contains("input_ids") -> feeds[name] = input
                    }
                }
                model.encoder.run(feeds).use { result ->
                    val out = result.get(0) as OnnxTensor
                    val buffer = out.floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) } to out.info.shape
                }
            }
        }
        val maxNew = minOf(model.tokenizer.config.maxLength, ids.size * 3 + 16)
        val written = OnnxTensor.createTensor(env, FloatBuffer.wrap(hidden), hiddenShape).use { states ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, length)).use { attention ->
                GreedyDecoder.decode(model.tokenizer.config, maxNew) { sofar ->
                    val tokens = LongArray(sofar.size) { sofar[it].toLong() }
                    OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())).use { decoderInput ->
                        val feeds = HashMap<String, OnnxTensor>()
                        for (name in model.decoder.inputNames) {
                            when {
                                name.contains("encoder_hidden_states") -> feeds[name] = states
                                name.contains("encoder_attention_mask") -> feeds[name] = attention
                                name.contains("input_ids") -> feeds[name] = decoderInput
                            }
                        }
                        model.decoder.run(feeds).use { result ->
                            val logits = (result.get(0) as OnnxTensor)
                            val shape = logits.info.shape
                            val vocab = shape.last().toInt()
                            val buffer = logits.floatBuffer
                            // The scores of the token after the last one: the last row.
                            val last = FloatArray(vocab)
                            buffer.position(buffer.limit() - vocab)
                            buffer.get(last)
                            last
                        }
                    }
                }
            }
        }
        return model.tokenizer.decode(written)
    }

    private fun load(id: String): Loaded {
        loaded[id]?.let { return it }
        val dir = store.folder(id)
        if (!dir.isDirectory) throw ModelMissing(id)
        // One model in memory: the way through English needs two, one after the other.
        releaseAll()
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        if (memory.availMem in 1 until MIN_FREE_BYTES) throw NotEnoughMemory()
        val vocab = MarianTokenizer.vocab(store.file(id, OpusMt.Kind.VOCAB).readText())
        val configFile = store.file(id, OpusMt.Kind.CONFIG)
        val config = if (configFile.isFile) MarianConfig.parse(configFile.readText()) else MarianConfig.fromVocab(vocab)
        val tokenizer = MarianTokenizer(SentencePieceModel.parse(store.file(id, OpusMt.Kind.SOURCE).readBytes()), vocab, config)
        val env = OrtEnvironment.getEnvironment()
        // The files are quantised (MatMulInteger and the like), which XNNPACK does not run: every
        // heavy node stays on the plain kernels anyway, and the decoder runs once per token, many
        // small runs, where the threads XNNPACK's setup puts to sleep would only be woken again.
        val options = AiKernels.options(threads, xnnpack = false)
        try {
            val encoder = env.createSession(store.file(id, OpusMt.Kind.ENCODER).absolutePath, options)
            val decoder = try {
                env.createSession(store.file(id, OpusMt.Kind.DECODER).absolutePath, options)
            } catch (e: Exception) {
                encoder.close()
                throw e
            }
            Log.i(TAG, "$id loaded on $threads threads")
            return Loaded(id, tokenizer, encoder, decoder, options).also {
                loaded[id] = it
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

    private fun releaseAll() {
        loaded.values.forEach { it.close() }
        loaded.clear()
        _inMemory.value = false
    }

    companion object {
        private const val TAG = "TranslationEngine"

        /** Below this much free memory a model is not even tried. */
        private const val MIN_FREE_BYTES = 250L * 1024 * 1024
    }
}
