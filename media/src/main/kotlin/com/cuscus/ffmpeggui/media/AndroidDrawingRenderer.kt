package com.cuscus.ffmpeggui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.DrawingRenderer
import com.cuscus.ffmpeggui.core.model.Stroke

/**
 * [DrawingRenderer] on the platform canvas ([StrokePainter], as the editor
 * draws it). The PNG goes with the stills in the cache ([CacheJanitor.newStill]),
 * so it is cleaned up like them.
 */
class AndroidDrawingRenderer(private val janitor: CacheJanitor) : DrawingRenderer {

    override fun render(strokes: List<Stroke>, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Drawing too large")
            return null
        }
        val (file, uri) = janitor.newStill()
        return try {
            StrokePainter.paint(Canvas(bitmap), strokes, width.toFloat(), height.toFloat())
            val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (written) uri else null.also { file.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the drawing: ${e.message}")
            file.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "AndroidDrawingRenderer"
    }
}
