package com.cuscus.ffmpeggui.erase

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.cuscus.ffmpeggui.core.erase.InpaintArea
import com.cuscus.ffmpeggui.preview.SubjectMasks
import com.cuscus.ffmpeggui.speech.AiKernels
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID
import kotlin.math.max

/**
 * The magic eraser: what is painted over a picture is filled in with what
 * should be behind it, by the model chosen ([active]: MI-GAN or LaMa, through
 * ONNX Runtime, with the XNNPACK kernels when on). The part around what is
 * erased is cut out and squeezed to the model's square (InpaintArea), the
 * model fills it, and its picture goes back only where the erased part was,
 * faded at the edge; the rest of the picture is untouched, its alpha kept.
 * The result is a new PNG (the old picture stays: projects may still use it).
 * One model in memory, let go of [IDLE_UNLOAD_MS] after the last use.
 */
class EraseEngine(context: Context, private val active: () -> Pair<EraseVariant, SpeechModelStore>) {

    class ModelMissing : IOException("The magic eraser's model is not installed")
    class NotEnoughMemory : IOException("Not enough free memory for the magic eraser's model")
    class NothingToErase : IOException("Nothing is painted to be erased")
    class Unreadable : IOException("The picture cannot be read")

    private class Loaded(val variant: EraseVariant, val options: OrtSession.SessionOptions, val session: OrtSession)

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loaded: Loaded? = null
    private var unloadJob: Job? = null

    private val _inMemory = MutableStateFlow(false)
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** The picture at [uri] with what [maskPath] marks (its alpha above half) filled in: a new PNG. */
    suspend fun erase(uri: Uri, maskPath: String): File = withContext(Dispatchers.Default) {
        val picture = decode(uri) ?: throw Unreadable()
        val width = picture.width
        val height = picture.height
        val alphas = SubjectMasks.alphas(maskPath, width, height) ?: throw Unreadable()
        val marked = BooleanArray(alphas.size) { alphas[it] > 127 }
        val side = max(width, height)
        // A little more than was painted: an outline or a soft shadow at its edge goes too.
        val erased = InpaintArea.grow(marked, width, height, max(2, side / GROW_PARTS))
        val bounds = InpaintArea.bounds(erased, width, height) ?: throw NothingToErase()
        val crop = InpaintArea.crop(bounds, width, height)
        val pixels = IntArray(width * height)
        picture.getPixels(pixels, 0, width, 0, 0, width, height)
        // The crop, squeezed to the model's square, and its mask the same.
        val cropped = Bitmap.createBitmap(picture, crop.left, crop.top, crop.width, crop.height)
        val square = Bitmap.createScaledBitmap(cropped, InpaintArea.SIDE, InpaintArea.SIDE, true)
        val n = InpaintArea.SIDE
        val squarePixels = IntArray(n * n).also { square.getPixels(it, 0, n, 0, 0, n, n) }
        val squareMask = BooleanArray(n * n) { i ->
            val x = crop.left + (i % n) * crop.width / n
            val y = crop.top + (i / n) * crop.height / n
            erased[y * width + x]
        }
        val filled = mutex.withLock {
            unloadJob?.cancel()
            try {
                run(use(), squarePixels, squareMask)
            } catch (e: OutOfMemoryError) {
                throw NotEnoughMemory()
            } finally {
                scheduleUnload()
            }
        }
        // Back to the crop's size, then laid where the erased part was.
        val made = Bitmap.createBitmap(filled, n, n, Bitmap.Config.ARGB_8888)
        val back = Bitmap.createScaledBitmap(made, crop.width, crop.height, true)
        val backPixels = IntArray(crop.width * crop.height).also { back.getPixels(it, 0, crop.width, 0, 0, crop.width, crop.height) }
        val local = BooleanArray(crop.width * crop.height) { i -> erased[(crop.top + i / crop.width) * width + crop.left + i % crop.width] }
        val weights = InpaintArea.weights(local, crop.width, crop.height, max(1, side / FEATHER_PARTS))
        val inCrop = IntArray(crop.width * crop.height) { i -> pixels[(crop.top + i / crop.width) * width + crop.left + i % crop.width] }
        InpaintArea.blend(inCrop, backPixels, weights)
        for (i in inCrop.indices) pixels[(crop.top + i / crop.width) * width + crop.left + i % crop.width] = inCrop[i]
        val out = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        folder.mkdirs()
        val target = File(folder, "erased-${UUID.randomUUID().toString().take(8)}.png")
        FileOutputStream(target).use { if (!out.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not write the picture") }
        listOf(picture, cropped, square, made, back, out).forEach { if (!it.isRecycled) it.recycle() }
        target
    }

    /** Frees the model's memory now. */
    fun unload() {
        scope.launch { mutex.withLock { release() } }
    }

    /** True when [path] is a picture this engine made. */
    fun owns(path: String?): Boolean = path != null && path.startsWith(folder.absolutePath + File.separator)

    /**
     * The model's picture for [argb] (its square) with [hole] filled in, as ARGB.
     * MI-GAN takes bytes (the mask 255 where the picture is kept), LaMa floats
     * 0..1 (the mask 1 where it is filled); each gives its picture as it does.
     */
    private fun run(model: Loaded, argb: IntArray, hole: BooleanArray): IntArray {
        val env = OrtEnvironment.getEnvironment()
        val n = InpaintArea.SIDE
        val plane = n * n
        val names = model.session.inputNames.toList()
        val maskName = names.firstOrNull { it.contains("mask", ignoreCase = true) } ?: names.last()
        val imageName = names.firstOrNull { it != maskName } ?: names.first()
        val image: OnnxTensor
        val mask: OnnxTensor
        if (model.variant == EraseVariant.FAST) {
            val pixels = ByteBuffer.allocateDirect(3 * plane).order(ByteOrder.nativeOrder())
            for (c in 0 until 3) {
                val shift = 16 - 8 * c
                for (i in 0 until plane) pixels.put(((argb[i] shr shift) and 0xFF).toByte())
            }
            pixels.rewind()
            val known = ByteBuffer.allocateDirect(plane).order(ByteOrder.nativeOrder())
            for (i in 0 until plane) known.put((if (hole[i]) 0 else 255).toByte())
            known.rewind()
            image = OnnxTensor.createTensor(env, pixels, longArrayOf(1, 3, n.toLong(), n.toLong()), OnnxJavaType.UINT8)
            mask = OnnxTensor.createTensor(env, known, longArrayOf(1, 1, n.toLong(), n.toLong()), OnnxJavaType.UINT8)
        } else {
            val pixels = FloatArray(3 * plane)
            for (c in 0 until 3) {
                val shift = 16 - 8 * c
                for (i in 0 until plane) pixels[c * plane + i] = ((argb[i] shr shift) and 0xFF) / 255f
            }
            val filled = FloatArray(plane) { if (hole[it]) 1f else 0f }
            image = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), longArrayOf(1, 3, n.toLong(), n.toLong()))
            mask = OnnxTensor.createTensor(env, FloatBuffer.wrap(filled), longArrayOf(1, 1, n.toLong(), n.toLong()))
        }
        try {
            model.session.run(mapOf(imageName to image, maskName to mask)).use { result ->
                val tensor = result.get(0) as OnnxTensor
                val channels: FloatArray = when (tensor.info.type) {
                    OnnxJavaType.UINT8 -> {
                        val bytes = tensor.byteBuffer
                        FloatArray(bytes.remaining()) { (bytes.get(it).toInt() and 0xFF).toFloat() }
                    }
                    else -> {
                        val floats = tensor.floatBuffer
                        val values = FloatArray(floats.remaining()).also { floats.get(it) }
                        // Some exports give 0..1, others 0..255.
                        if ((values.maxOrNull() ?: 0f) <= 1.5f) for (i in values.indices) values[i] *= 255f
                        values
                    }
                }
                if (channels.size < 3 * plane) throw IOException("Unexpected output of ${channels.size} values")
                return IntArray(plane) { i ->
                    val r = channels[i].toInt().coerceIn(0, 255)
                    val g = channels[plane + i].toInt().coerceIn(0, 255)
                    val b = channels[2 * plane + i].toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        } finally {
            image.close()
            mask.close()
        }
    }

    private fun use(): Loaded {
        val (variant, store) = active()
        loaded?.let { if (it.variant == variant) return it }
        release()
        val file = store.file(SpeechModel.Role.ENCODER)
        if (file == null || store.state.value !is SpeechModelStore.State.Ready) throw ModelMissing()
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        val needs = if (variant == EraseVariant.FAST) MIN_FREE_FAST else MIN_FREE_PRECISE
        if (memory.availMem in 1 until needs) throw NotEnoughMemory()
        val started = SystemClock.elapsedRealtime()
        val options = AiKernels.options(threads)
        try {
            val session = OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
            Log.i(TAG, "${variant.name} loaded in ${SystemClock.elapsedRealtime() - started} ms on $threads threads; ${session.inputNames} -> ${session.outputNames}")
            return Loaded(variant, options, session).also {
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
        loaded?.let {
            it.session.close()
            it.options.close()
        }
        loaded = null
        _inMemory.value = false
    }

    private fun decode(uri: Uri): Bitmap? = try {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = false
                })
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    companion object {
        private const val TAG = "EraseEngine"
        private const val FOLDER = "erased"

        /** The model is let go of after this long unused. */
        const val IDLE_UNLOAD_MS = 2 * 60_000L

        /** Pictures larger than this are worked on at half size (and halved again...). */
        private const val MAX_SIDE = 4_096

        /** The painted part grows by its picture's side over this, and fades over the side over [FEATHER_PARTS]. */
        private const val GROW_PARTS = 150
        private const val FEATHER_PARTS = 220

        private const val MIN_FREE_FAST = 200L * 1024 * 1024
        private const val MIN_FREE_PRECISE = 700L * 1024 * 1024
    }
}
