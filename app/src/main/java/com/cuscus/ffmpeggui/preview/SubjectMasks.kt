package com.cuscus.ffmpeggui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.cuscus.ffmpeggui.core.image.Raster

/**
 * The masks of subject keys (see SubjectCutter) as the preview needs them: at
 * the size of the picture it shows, read once and kept a little while. A mask
 * that cannot be read (deleted meanwhile) gives null: nothing is cut.
 */
object SubjectMasks {

    // Up to about 16 MB of masks, one int per pixel.
    private val cache = object : LruCache<Triple<String, Int, Int>, IntArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: Triple<String, Int, Int>, value: IntArray): Int = value.size
    }

    /** How much of each pixel is the subject, 0..255, for a picture of [width] x [height]. Off the main thread. */
    fun alphas(path: String, width: Int, height: Int): IntArray? {
        if (width <= 0 || height <= 0) return null
        val key = Triple(path, width, height)
        cache.get(key)?.let { return it }
        // The mask is as large as the picture (a photo: tens of megabytes decoded), the preview
        // needs it far smaller: read at the smallest power-of-two step that still covers the
        // size asked, so that a picture laid over the video (shown small, beside players and
        // frames in memory) does not run out of memory and silently stay uncut.
        val pixels = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return unreadable(path)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return unreadable(path)
            val sized = if (decoded.width == width && decoded.height == height) decoded else Bitmap.createScaledBitmap(decoded, width, height, true)
            val out = IntArray(width * height)
            sized.getPixels(out, 0, width, 0, 0, width, height)
            if (sized !== decoded) sized.recycle()
            decoded.recycle()
            out
        } catch (e: OutOfMemoryError) {
            return unreadable(path)
        }
        for (i in pixels.indices) pixels[i] = pixels[i] ushr 24
        cache.put(key, pixels)
        return pixels
    }

    private fun unreadable(path: String): IntArray? {
        Log.w(TAG, "Subject mask could not be read: $path")
        return null
    }

    private const val TAG = "SubjectMasks"

    /** The mask as a raster for the picture editor's steps: its value in every channel, opaque. */
    fun raster(path: String, width: Int, height: Int): Raster? {
        val alphas = alphas(path, width, height) ?: return null
        return Raster(width, height, IntArray(alphas.size) { i -> val a = alphas[i]; (0xFF shl 24) or (a shl 16) or (a shl 8) or a })
    }
}
