package com.cuscus.ffmpeggui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.ImageRecoder
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.model.OutputFormat
import java.io.File
import kotlin.math.roundToInt

/**
 * [ImageRecoder] on the platform encoder: the bundled FFmpeg has no WebP
 * encoder, so it writes a PNG and this writes the WebP from it. At full
 * quality the WebP is lossless (from Android 11; before, the platform encoder
 * is lossy at quality 100). It also reads the PNG an SVG is traced from.
 */
class AndroidImageRecoder : ImageRecoder {

    override fun recode(pngPath: String, targetPath: String, format: OutputFormat, quality: Float): Boolean {
        if (format != OutputFormat.WEBP) return false
        val bitmap = try {
            BitmapFactory.decodeFile(pngPath)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Picture too large to encode")
            null
        } ?: return false
        val target = File(targetPath)
        return try {
            val percent = (quality.coerceIn(0f, 1f) * 100).roundToInt()
            target.outputStream().use { out -> bitmap.compress(webp(lossless = percent >= 100), percent, out) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write ${target.name}: ${e.message}")
            false
        } finally {
            bitmap.recycle()
        }
    }

    override fun read(pngPath: String): Raster? = try {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        BitmapFactory.decodeFile(pngPath, options)?.let { bitmap ->
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                // Not premultiplied: as the tracer (and FFmpeg) read colours.
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                Raster(bitmap.width, bitmap.height, pixels)
            } finally {
                bitmap.recycle()
            }
        }
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Picture too large to trace")
        null
    }

    private fun webp(lossless: Boolean): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    private companion object {
        const val TAG = "AndroidImageRecoder"
    }
}
