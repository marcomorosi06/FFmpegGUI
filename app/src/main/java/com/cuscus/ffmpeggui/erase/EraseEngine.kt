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
import com.cuscus.ffmpeggui.core.provenance.AiDisclosure
import com.cuscus.ffmpeggui.core.provenance.PngXmp
import com.cuscus.ffmpeggui.core.provenance.Xmp
import com.cuscus.ffmpeggui.marking.MarkEngine
import com.cuscus.ffmpeggui.speech.AiKernels
import com.cuscus.ffmpeggui.speech.ModelSlot
import com.cuscus.ffmpeggui.speech.SpeechModel
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * The magic eraser: what is painted over a picture is filled in with what
 * should be behind it, by the model chosen ([active]: MI-GAN or LaMa, through
 * ONNX Runtime, with the XNNPACK kernels when on). The part around what is
 * erased is cut out and squeezed to the model's square (InpaintArea), the
 * model fills it, and its picture goes back only where the erased part was,
 * faded at the edge; the rest of the picture is untouched, its alpha kept.
 * The result is a new PNG (the old picture stays: projects may still use it).
 *
 * Memory: the picture and its mask stay bitmaps (outside the Java heap, which
 * is a few hundred megabytes whatever the phone's memory); only the crop is
 * held as arrays, and the fade only where it can reach, so that a photo of
 * tens of megapixels does not run the heap out. The model goes through the
 * app's ModelGate: alone in memory, and let go of as soon as the picture is
 * filled in.
 */
class EraseEngine(
    context: Context,
    /** Writes the invisible AI mark into every picture erased (MarkEngine): none without it. */
    private val marker: MarkEngine,
    private val active: () -> Pair<EraseVariant, SpeechModelStore>,
) {

    class ModelMissing : IOException("The magic eraser's model is not installed")
    class NotEnoughMemory : IOException("Not enough free memory for the magic eraser's model")
    class NothingToErase : IOException("Nothing is painted to be erased")
    class Unreadable : IOException("The picture cannot be read")

    private class Loaded(val variant: EraseVariant, val options: OrtSession.SessionOptions, val session: OrtSession)

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)
    private val slot = ModelSlot("Magic eraser model") { release() }
    private var loaded: Loaded? = null

    private val _inMemory = MutableStateFlow(false)
    val inMemory: StateFlow<Boolean> = _inMemory.asStateFlow()

    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

    /** The picture at [uri] with what [maskPath] marks (its alpha above half) filled in: a new PNG. */
    suspend fun erase(uri: Uri, maskPath: String): File = withContext(Dispatchers.Default) {
        // The AI marks first: without them nothing is erased (and the model is not even loaded).
        if (!marker.ready) throw MarkEngine.Missing()
        // Written in place, row by row, where the erased part was: no copy of the whole picture.
        val picture = decode(uri) ?: throw Unreadable()
        var mask: Bitmap? = null
        try {
            val width = picture.width
            val height = picture.height
            mask = maskBitmap(maskPath, width, height) ?: throw Unreadable()
            val marked = markedBox(mask) ?: throw NothingToErase()
            val side = max(width, height)
            // A little more than was painted: an outline or a soft shadow at its edge goes too.
            val growBy = max(2, side / GROW_PARTS)
            val erasedBox = InpaintArea.Box(
                max(0, marked.left - growBy),
                max(0, marked.top - growBy),
                min(width, marked.right + growBy),
                min(height, marked.bottom + growBy),
            )
            val crop = InpaintArea.crop(erasedBox, width, height)
            val cw = crop.width
            val ch = crop.height
            // What is marked, in the crop (all of it is inside), then grown.
            val local = BooleanArray(cw * ch)
            val row = IntArray(cw)
            for (y in 0 until ch) {
                mask.getPixels(row, 0, cw, crop.left, crop.top + y, cw, 1)
                for (x in 0 until cw) local[y * cw + x] = (row[x] ushr 24) > 127
            }
            mask.recycle()
            mask = null
            val erased = InpaintArea.grow(local, cw, ch, growBy)
            // The crop, squeezed to the model's square, and its mask the same.
            val n = InpaintArea.SIDE
            val cropped = Bitmap.createBitmap(picture, crop.left, crop.top, cw, ch)
            val square = Bitmap.createScaledBitmap(cropped, n, n, true)
            if (cropped !== picture && cropped !== square) cropped.recycle()
            val squarePixels = IntArray(n * n).also { square.getPixels(it, 0, n, 0, 0, n, n) }
            if (square !== picture) square.recycle()
            val squareMask = BooleanArray(n * n) { i -> erased[(i / n) * ch / n * cw + (i % n) * cw / n] }
            val filled = slot.run {
                try {
                    run(use(), squarePixels, squareMask)
                } catch (e: OutOfMemoryError) {
                    throw NotEnoughMemory()
                }
            }
            // Back to the crop's size, then laid where the erased part was.
            val made = Bitmap.createBitmap(filled, n, n, Bitmap.Config.ARGB_8888)
            val back = Bitmap.createScaledBitmap(made, cw, ch, true)
            if (back !== made) made.recycle()
            // Only where the fade can reach is weighed (its blur sees nothing past it): elsewhere
            // the picture stays as it is.
            val feather = max(1, side / FEATHER_PARTS)
            val inside = InpaintArea.bounds(erased, cw, ch) ?: throw NothingToErase()
            val reach = FADE_REACH * feather
            val bx = max(0, inside.left - reach)
            val by = max(0, inside.top - reach)
            val bw = min(cw, inside.right + reach) - bx
            val bh = min(ch, inside.bottom + reach) - by
            val part = BooleanArray(bw * bh) { i -> erased[(by + i / bw) * cw + bx + i % bw] }
            val weights = InpaintArea.weights(part, bw, bh, feather)
            val pictureRow = IntArray(bw)
            val madeRow = IntArray(bw)
            for (y in 0 until bh) {
                picture.getPixels(pictureRow, 0, bw, crop.left + bx, crop.top + by + y, bw, 1)
                back.getPixels(madeRow, 0, bw, bx, by + y, bw, 1)
                InpaintArea.blend(pictureRow, madeRow, weights, y * bw)
                picture.setPixels(pictureRow, 0, bw, crop.left + bx, crop.top + by + y, bw, 1)
            }
            back.recycle()
            // The invisible AI mark (TrustMark), over the whole picture, read back before it is kept.
            marker.markPicture(picture)
            folder.mkdirs()
            val target = File(folder, "erased-${UUID.randomUUID().toString().take(8)}.png")
            FileOutputStream(target).use { if (!picture.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not write the picture") }
            // The picture says it was changed by a model (EU AI Act, Article 50(2)), never which
            // one, wherever it goes: what is exported from it says so too (see AiMarksReader).
            // Never without it.
            if (!PngXmp.write(target, Xmp.packet(AiDisclosure(pictures = true)))) {
                target.delete()
                throw IOException("Could not mark the picture as changed with AI")
            }
            target
        } finally {
            mask?.recycle()
            picture.recycle()
        }
    }

    /** Frees the model's memory now. */
    fun unload() = slot.unload()

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

    private fun release() {
        loaded?.let {
            it.session.close()
            it.options.close()
        }
        loaded = null
        _inMemory.value = false
    }

    /**
     * The mask at [path] at the picture's [width] x [height], as a bitmap (read at
     * the smallest power-of-two step that still covers that size); null when it
     * cannot be read.
     */
    private fun maskBitmap(path: String, width: Int, height: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { decoded ->
                if (decoded.width == width && decoded.height == height) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, width, height, true).also { if (it !== decoded) decoded.recycle() }
                }
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** The box of what [mask] marks (alpha above half), read a row at a time; null when nothing is. */
    private fun markedBox(mask: Bitmap): InpaintArea.Box? {
        val width = mask.width
        val row = IntArray(width)
        var left = width
        var top = -1
        var right = -1
        var bottom = -1
        for (y in 0 until mask.height) {
            mask.getPixels(row, 0, width, 0, y, width, 1)
            var any = false
            for (x in 0 until width) {
                if ((row[x] ushr 24) <= 127) continue
                any = true
                if (x < left) left = x
                if (x > right) right = x
            }
            if (any) {
                if (top < 0) top = y
                bottom = y
            }
        }
        return if (right < 0) null else InpaintArea.Box(left, top, right + 1, bottom + 1)
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
                    inMutable = true
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

        /** Pictures larger than this are worked on at half size (and halved again...). */
        private const val MAX_SIDE = 4_096

        /** The painted part grows by its picture's side over this, and fades over the side over [FEATHER_PARTS]. */
        private const val GROW_PARTS = 150
        private const val FEATHER_PARTS = 220

        /**
         * The fade is weighed this many feathers past the erased part: its grow and
         * blur reach two, so past that nothing changes (the result is the same as
         * weighing the whole crop).
         */
        private const val FADE_REACH = 3

        private const val MIN_FREE_FAST = 200L * 1024 * 1024
        private const val MIN_FREE_PRECISE = 700L * 1024 * 1024
    }
}
