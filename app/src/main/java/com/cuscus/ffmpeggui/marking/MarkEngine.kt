package com.cuscus.ffmpeggui.marking

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.cuscus.ffmpeggui.core.marking.Marks
import com.cuscus.ffmpeggui.core.marking.PictureMarking
import com.cuscus.ffmpeggui.core.marking.Resampler
import com.cuscus.ffmpeggui.core.marking.SpeechMarking
import com.cuscus.ffmpeggui.speech.AiKernels
import com.cuscus.ffmpeggui.speech.ModelSlot
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Writes the invisible AI marks (see MarkerModel, core.marking) and finds
 * them again, with the files of [store]:
 *
 *  - [markSpeech]: the lines of a dub, each with AudioSeal's watermark, then
 *    checked all together; [speech] tells how much of a sound carries it;
 *  - [markPicture]: a picture with TrustMark's mark, laid as Adobe lays it,
 *    then checked; [pictureMarked] tells whether a picture carries it.
 *
 * Marking fails rather than hand out what does not carry the mark: a mark
 * that does not read back right away is [NotHeld]. The models take their
 * turn at the ModelGate like every other, and are let go of right after.
 *
 * Nothing about the phone, the person, the file or the model goes in:
 * AudioSeal's message and TrustMark's payload are zeros (Marks).
 */
class MarkEngine(context: Context, private val store: SpeechModelStore) {

    /** The marks' files are not installed (or did not pass their check). */
    class Missing : IOException("The AI marks are not installed")

    /** The mark did not read back after it was written (too short a sound, too small a picture). */
    class NotHeld(what: String) : IOException("The AI mark does not hold on $what")

    /** What the detector found in a sound: the share of its speech marked (null: too little speech), the message. */
    class SpeechVerdict(val share: Float?, val message: FloatArray) {
        val marked: Boolean get() = share != null && share >= SpeechMarking.MARKED_SHARE
    }

    private val slot = ModelSlot("AI marks") { release() }
    private var sessions: MutableMap<String, OrtSession> = mutableMapOf()
    private var options: OrtSession.SessionOptions? = null
    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** True when the marks' files are installed and checked. */
    val ready: Boolean get() = store.state.value is SpeechModelStore.State.Ready

    // -- Speech ---------------------------------------------------------------------------

    /**
     * [lines] (mono, at [rate]) each with its mark; checked together right
     * after: at least SpeechMarking.FRESH_SHARE of their speech must carry it,
     * with the message of zeros.
     */
    suspend fun markSpeech(lines: List<FloatArray>, rate: Int): List<FloatArray> = withContext(Dispatchers.Default) {
        slot.run {
            val generator = session(MarkerModel.GENERATOR.name)
            val marked = lines.map { line ->
                currentCoroutineContext().ensureActive()
                if (line.isEmpty()) return@map line
                val at16 = Resampler.resample(line, rate, SpeechMarking.RATE)
                val windows = SpeechMarking.markWindows(at16.size)
                val marks = windows.map { window -> watermark(generator, SpeechMarking.padded(at16, window)) }
                val joined = SpeechMarking.join(at16.size, windows, marks)
                val back = Resampler.resample(joined, SpeechMarking.RATE, rate, line.size)
                FloatArray(line.size) { i -> (line[i] + back[i]).coerceIn(-1f, 1f) }
            }
            val verdict = detect(session(MarkerModel.DETECTOR.name), marked, rate)
            // Too little speech to tell (a dub of one short word) is marked all the same.
            if (verdict.share != null && (verdict.share < SpeechMarking.FRESH_SHARE || !Marks.speechMessageIsOurs(verdict.message))) {
                Log.w(TAG, "Speech mark found on ${verdict.share} of the speech only")
                throw NotHeld("this voice")
            }
            Log.i(TAG, "Speech marked: found on ${verdict.share} of the speech")
            marked
        }
    }

    /** How much of [lines] (at [rate]) carries the speech mark. */
    suspend fun speech(lines: List<FloatArray>, rate: Int): SpeechVerdict = withContext(Dispatchers.Default) {
        slot.run { detect(session(MarkerModel.DETECTOR.name), lines, rate) }
    }

    private fun watermark(generator: OrtSession, window: FloatArray): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val audio = OnnxTensor.createTensor(env, FloatBuffer.wrap(window), longArrayOf(1, 1, window.size.toLong()))
        val message = OnnxTensor.createTensor(env, LongBuffer.wrap(Marks.SPEECH_MESSAGE), longArrayOf(1, Marks.SPEECH_BITS.toLong()))
        try {
            generator.run(mapOf(name(generator, 3, "audio") to audio, name(generator, 2, "message") to message)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                return FloatArray(out.remaining()).also { out.get(it) }
            }
        } finally {
            audio.close()
            message.close()
        }
    }

    private suspend fun detect(detector: OrtSession, lines: List<FloatArray>, rate: Int): SpeechVerdict {
        val env = OrtEnvironment.getEnvironment()
        var speech = 0
        var marked = 0
        val messages = ArrayList<FloatArray>()
        val weights = ArrayList<Int>()
        for (line in lines) {
            if (line.isEmpty()) continue
            val at16 = Resampler.resample(line, rate, SpeechMarking.RATE)
            val voiced = SpeechMarking.voiced(at16)
            for (window in SpeechMarking.checkWindows(at16.size)) {
                currentCoroutineContext().ensureActive()
                val audio = OnnxTensor.createTensor(env, FloatBuffer.wrap(SpeechMarking.padded(at16, window)), longArrayOf(1, 1, SpeechMarking.WINDOW.toLong()))
                try {
                    detector.run(mapOf(detector.inputNames.first() to audio)).use { result ->
                        val detection = (result.get(0) as OnnxTensor).floatBuffer
                        val message = (result.get(1) as OnnxTensor).floatBuffer
                        // [1, 2, WINDOW]: the second row is "marked".
                        var here = 0
                        for (j in 0 until window.length) {
                            if (!voiced[window.start + j]) continue
                            here++
                            if (detection.get(SpeechMarking.WINDOW + j) > SpeechMarking.DETECTED) marked++
                        }
                        speech += here
                        messages += FloatArray(Marks.SPEECH_BITS) { message.get(it) }
                        weights += here
                    }
                } finally {
                    audio.close()
                }
            }
        }
        val share = if (speech < SpeechMarking.LEAST_VOICED) null else marked.toFloat() / speech
        return SpeechVerdict(share, SpeechMarking.message(messages, weights))
    }

    // -- Pictures -------------------------------------------------------------------------

    /**
     * Lays TrustMark's mark on [picture] (a mutable ARGB bitmap, changed in
     * place; its transparency kept), then reads it back: it must come back
     * with at most Marks.PICTURE_FRESH_ERRORS wrong bits.
     */
    suspend fun markPicture(picture: Bitmap) = withContext(Dispatchers.Default) {
        slot.run {
            val width = picture.width
            val height = picture.height
            val region = PictureMarking.region(width, height)
            val cover = squeeze(picture, region, PictureMarking.SIZE)
            val encoder = session(MarkerModel.ENCODER.name)
            val stego = encode(encoder, cover)
            val residual = PictureMarking.residual(stego, cover)
            val feather = PictureMarking.feather(region)
            val row = IntArray(region.width)
            val mark = FloatArray(3 * region.width)
            for (y in 0 until region.height) {
                if (y % 64 == 0) currentCoroutineContext().ensureActive()
                picture.getPixels(row, 0, region.width, region.left, region.top + y, region.width, 1)
                PictureMarking.residualRow(residual, y, region.width, region.height, mark)
                for (x in 0 until region.width) {
                    val p = row[x]
                    val w = PictureMarking.weight(x, y, region, feather)
                    val r = PictureMarking.apply((p shr 16) and 0xFF, mark[x * 3], w)
                    val g = PictureMarking.apply((p shr 8) and 0xFF, mark[x * 3 + 1], w)
                    val b = PictureMarking.apply(p and 0xFF, mark[x * 3 + 2], w)
                    row[x] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
                picture.setPixels(row, 0, region.width, region.left, region.top + y, region.width, 1)
            }
            val errors = Marks.pictureErrors(decode(picture))
            if (errors == null || errors > Marks.PICTURE_FRESH_ERRORS) {
                Log.w(TAG, "Picture mark reads back with $errors wrong bits")
                throw NotHeld("this picture")
            }
            Log.i(TAG, "Picture marked: reads back with $errors wrong bits")
        }
    }

    /** True when [picture] carries TrustMark's mark of zeros. */
    suspend fun pictureMarked(picture: Bitmap): Boolean = withContext(Dispatchers.Default) {
        slot.run { Marks.pictureMarked(decode(picture)) }
    }

    private fun squeeze(picture: Bitmap, region: PictureMarking.Region, size: Int): FloatArray {
        val squeezer = PictureMarking.Squeezer(region.width, region.height, size)
        val row = IntArray(region.width)
        for (y in 0 until region.height) {
            picture.getPixels(row, 0, region.width, region.left, region.top + y, region.width, 1)
            squeezer.add(y, row)
        }
        return squeezer.result()
    }

    private fun encode(encoder: OrtSession, cover: FloatArray): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val n = PictureMarking.SIZE.toLong()
        val image = OnnxTensor.createTensor(env, FloatBuffer.wrap(cover), longArrayOf(1, 3, n, n))
        val bits = OnnxTensor.createTensor(env, FloatBuffer.wrap(Marks.PICTURE_PAYLOAD), longArrayOf(1, Marks.PICTURE_BITS.toLong()))
        try {
            encoder.run(mapOf(name(encoder, 4, "onnx::Concat_0") to image, name(encoder, 2, "onnx::Gemm_1") to bits)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                if (out.remaining() != cover.size) throw IOException("Unexpected output of ${out.remaining()} values")
                return FloatArray(out.remaining()).also { out.get(it) }
            }
        } finally {
            image.close()
            bits.close()
        }
    }

    private fun decode(picture: Bitmap): FloatArray {
        val decoder = session(MarkerModel.DECODER.name)
        val shape = (decoder.inputInfo.values.first().info as? TensorInfo)?.shape
        val size = shape?.lastOrNull()?.takeIf { it > 0 }?.toInt() ?: PictureMarking.SIZE
        val region = PictureMarking.region(picture.width, picture.height)
        val squeezed = squeeze(picture, region, size)
        val env = OrtEnvironment.getEnvironment()
        val image = OnnxTensor.createTensor(env, FloatBuffer.wrap(squeezed), longArrayOf(1, 3, size.toLong(), size.toLong()))
        try {
            decoder.run(mapOf(decoder.inputNames.first() to image)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                return FloatArray(out.remaining()).also { out.get(it) }
            }
        } finally {
            image.close()
        }
    }

    // -- The sessions -----------------------------------------------------------------------

    /** The input of [session] named [preferred], else the one of [rank] dimensions. */
    private fun name(session: OrtSession, rank: Int, preferred: String): String {
        if (preferred in session.inputNames) return preferred
        return session.inputInfo.entries.firstOrNull { (it.value.info as? TensorInfo)?.shape?.size == rank }?.key
            ?: throw IOException("No input of rank $rank")
    }

    /** The session of the marks' file [name], loaded in this slot's turn. */
    private fun session(name: String): OrtSession {
        sessions[name]?.let { return it }
        if (!ready) throw Missing()
        val file = File(store.folder, name)
        if (!file.isFile) throw Missing()
        val opts = options ?: AiKernels.options(threads).also { options = it }
        return try {
            OrtEnvironment.getEnvironment().createSession(file.absolutePath, opts).also { sessions[name] = it }
        } catch (e: OutOfMemoryError) {
            throw IOException("Not enough memory for the AI marks", e)
        }
    }

    private fun release() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions = mutableMapOf()
        options?.close()
        options = null
    }

    /** Frees the models' memory now. */
    fun unload() = slot.unload()

    private companion object {
        const val TAG = "MarkEngine"
    }
}
