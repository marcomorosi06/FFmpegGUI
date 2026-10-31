package com.cuscus.ffmpeggui.media

import android.graphics.Bitmap
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.BlankMaker
import com.cuscus.ffmpeggui.core.model.BlankFillType
import com.cuscus.ffmpeggui.core.model.BlankSpec

/**
 * [BlankMaker] on a platform bitmap: the fill drawn row by row as the core
 * works it out ([com.cuscus.ffmpeggui.core.model.BlankFill.row]), a single
 * colour or nothing at once, written as a PNG next to the stills (a project
 * that keeps it copies it, as any still).
 */
class AndroidBlankMaker(private val janitor: CacheJanitor) : BlankMaker {

    override fun make(spec: BlankSpec): String? {
        val blank = spec.sane()
        val bitmap = try {
            Bitmap.createBitmap(blank.width, blank.height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Blank too large")
            return null
        }
        val (file, uri) = janitor.newStill()
        return try {
            when (blank.fill.type) {
                BlankFillType.TRANSPARENT -> bitmap.eraseColor(0)
                BlankFillType.SOLID -> bitmap.eraseColor(blank.fill.from.toInt())
                else -> {
                    val row = IntArray(blank.width)
                    for (y in 0 until blank.height) {
                        blank.fill.row(y, blank.width, blank.height, row)
                        bitmap.setPixels(row, 0, blank.width, 0, y, blank.width, 1)
                    }
                }
            }
            val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (written) uri else null.also { file.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write blank: ${e.message}")
            file.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "AndroidBlankMaker"
    }
}
