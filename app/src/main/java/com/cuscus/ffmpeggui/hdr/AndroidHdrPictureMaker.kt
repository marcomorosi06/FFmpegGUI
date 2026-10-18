package com.cuscus.ffmpeggui.hdr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.HdrJob
import com.cuscus.ffmpeggui.core.engine.HdrPictureMaker
import com.cuscus.ffmpeggui.core.hdr.GainMap
import com.cuscus.ffmpeggui.core.hdr.GainMapBuilder
import com.cuscus.ffmpeggui.core.hdr.GainMapMetadata
import com.cuscus.ffmpeggui.core.hdr.GreyJpeg
import com.cuscus.ffmpeggui.core.image.borderPixels
import com.cuscus.ffmpeggui.core.image.editedSize
import com.cuscus.ffmpeggui.core.model.HdrMode
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.media.StillGainMaps
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * [HdrPictureMaker] on the platform's PNG decoder: the two lossless copies of
 * the picture are read a band of rows at a time (a photo of tens of megapixels
 * never has to be held whole), the gain map is worked out by
 * [GainMapBuilder] and written as a grey JPG ([GreyJpeg]).
 */
class AndroidHdrPictureMaker(private val gainMaps: StillGainMaps) : HdrPictureMaker {

    override fun make(job: HdrJob): GainMapMetadata? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(job.finalPng, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        val builder = GainMapBuilder(width, height)
        val picture = regionDecoder(job.finalPng) ?: return null
        val plain = regionDecoder(job.plainPng)
        try {
            // Bands of an even number of rows (the gain map takes two at a time), about a megapixel each.
            val rows = max(GainMapBuilder.SCALE, (BAND_PIXELS / width) / GainMapBuilder.SCALE * GainMapBuilder.SCALE)
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            var top = 0
            while (top < height) {
                val count = min(rows, height - top)
                val rect = Rect(0, top, width, top + count)
                val all = pixels(picture.decodeRegion(rect, options) ?: return null, width, count)
                val bare = plain?.decodeRegion(rect, options)?.let { pixels(it, width, count) }
                builder.add(top, count, all, bare)
                top += count
            }
        } finally {
            picture.recycle()
            plain?.recycle()
        }
        val map = gainMap(job, builder) ?: return null
        File(job.gainMapJpg).writeBytes(GreyJpeg.encode(map.values, map.width, map.height, GAIN_MAP_QUALITY))
        return map.metadata
    }

    private fun gainMap(job: HdrJob, builder: GainMapBuilder): GainMap? {
        val hdr = job.hdr
        // Where the picture lies in the JPG (inside its border), in gain map pixels.
        val (editedW, editedH) = editedSize(job.edit, job.effects, job.sourceWidth.coerceAtLeast(1), job.sourceHeight.coerceAtLeast(1))
        val border = borderPixels(job.edit, editedW, editedH)
        val scaleX = builder.mapWidth.toFloat() / (editedW + 2 * border)
        val scaleY = builder.mapHeight.toFloat() / (editedH + 2 * border)
        val rect = floatArrayOf(border * scaleX, border * scaleY, editedW * scaleX, editedH * scaleY)
        val area = HdrSources.areaOn(hdr, job.edit, job.effects, job.sourceWidth, job.sourceHeight, builder.mapWidth, builder.mapHeight, rect)
        if (hdr.areaMask != null && area == null) {
            Log.w(TAG, "The HDR area's mask cannot be read: no HDR look")
            return null
        }
        return when (hdr.mode) {
            HdrMode.GENERATED -> builder.generated(hdr.intensity, area)
            HdrMode.ORIGINAL -> {
                val stops = HdrSources.originalOn(gainMaps, job.sourceUri, job.edit, job.effects, builder.mapWidth, builder.mapHeight, rect)
                    ?: return null
                builder.original(stops, HdrSources.originalStrength(hdr), area)
            }
        }
    }

    private fun regionDecoder(path: String): BitmapRegionDecoder? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(path)
        } else {
            @Suppress("DEPRECATION")
            FileInputStream(path).use { BitmapRegionDecoder.newInstance(it, false) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read $path: ${e.message}")
        null
    }

    private fun pixels(bitmap: Bitmap, width: Int, rows: Int): IntArray {
        val out = IntArray(width * rows)
        bitmap.getPixels(out, 0, width, 0, 0, min(width, bitmap.width), min(rows, bitmap.height))
        bitmap.recycle()
        return out
    }

    private companion object {
        const val TAG = "AndroidHdrPictureMaker"
        const val BAND_PIXELS = 1_000_000
        const val GAIN_MAP_QUALITY = 90
    }
}
