package com.cuscus.ffmpeggui.cutout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.max

/**
 * Finds the subject of a still picture with the cut-out model and keeps its
 * mask: a PNG as large as the picture (as FFmpeg and the preview read it, its
 * EXIF turn ignored by both), white, its alpha how much of each pixel is the
 * subject. The export merges that alpha in (see the subject key); the preview
 * reads it too.
 *
 * Masks are kept in the app's private files, one per picture, so that cutting
 * the same picture out again (another sticker, another project) is instant.
 */
class SubjectCutter(context: Context, private val engine: CutoutEngine) {

    /** The picture could not be read. */
    class Unreadable : IOException("The picture could not be read")

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)

    /** The mask of the picture at [uri], made now unless it is there already. Returns its file. */
    suspend fun cut(uri: Uri): File = withContext(Dispatchers.Default) {
        existing(uri)?.let { return@withContext it }
        val picture = decode(uri) ?: throw Unreadable()
        try {
            val found = engine.mask({ side ->
                val square = Bitmap.createScaledBitmap(picture, side, side, true)
                val argb = IntArray(side * side)
                square.getPixels(argb, 0, side, 0, 0, side, side)
                if (square !== picture) square.recycle()
                argb
            })
            currentCoroutineContext().ensureActive()
            val side = found.side
            val values = found.values
            val pixels = IntArray(side * side)
            val target = File(folder, name(uri, found.model))
            // White, with the subject in the alpha; then back to the picture's own size.
            for (i in values.indices) pixels[i] = (values[i] shl 24) or 0xFFFFFF
            val small = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
            val mask = Bitmap.createScaledBitmap(small, picture.width, picture.height, true)
            folder.mkdirs()
            val temp = File(folder, target.name + ".part")
            temp.outputStream().use { out -> if (!mask.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Could not write the mask") }
            if (!temp.renameTo(target)) throw IOException("Could not keep the mask")
            if (mask !== small) mask.recycle()
            small.recycle()
            target
        } finally {
            picture.recycle()
        }
    }

    /**
     * The mask of the picture at [uri] when the model chosen now cut it out
     * before, else null (another model's mask is not taken: the choice of a
     * more precise model is a wish for a better mask).
     */
    fun existing(uri: Uri): File? = File(folder, name(uri, engine.model)).takeIf { it.isFile && it.length() > 0 }

    /** The picture at its own size, or halved until its longer side is at most [MAX_SIDE]. */
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
                })
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** The mask's file name: the picture's, then the model's ([CutoutModel.maskSuffix]). */
    private fun name(uri: Uri, model: CutoutModel): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) } + model.maskSuffix + ".png"
    }

    private companion object {
        const val FOLDER = "cutouts"

        /**
         * Longest side the mask is made at: a larger picture's mask is smaller and
         * stretched to it by the export (the model sees at most 1024 pixels anyway).
         */
        const val MAX_SIDE = 4096
    }
}
