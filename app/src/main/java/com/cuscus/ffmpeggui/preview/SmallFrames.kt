package com.cuscus.ffmpeggui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlin.math.max
import kotlin.math.min

/**
 * The frame of [retriever] nearest (on a key frame) to [timeUs], scaled down to
 * fit a [boxPx] square, aspect kept; null when there is none.
 *
 * Decoding a full frame only to shrink it costs tens of megabytes for 4K
 * footage, so the decoder scales it itself.
 */
fun smallFrameAt(retriever: MediaMetadataRetriever, timeUs: Long, boxPx: Int): Bitmap? =
    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, boxPx, boxPx)

/** [full] scaled down to fit a [boxPx] square; [full] is recycled when a copy is made. */
private fun fitInBox(full: Bitmap, boxPx: Int): Bitmap {
    val scale = min(1f, boxPx.toFloat() / max(full.width, full.height).coerceAtLeast(1))
    if (scale >= 1f) return full
    val scaled = Bitmap.createScaledBitmap(
        full,
        (full.width * scale).toInt().coerceAtLeast(1),
        (full.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== full) full.recycle()
    return scaled
}

/**
 * The picture at [uri] (a still of the project: a PNG already upright), decoded
 * at a fraction of its size so that it still covers a [boxPx] square; null when
 * it cannot be read. Call it off the main thread.
 */
fun smallPicture(context: Context, uri: Uri, boxPx: Int): Bitmap? = try {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        // The largest power of two that keeps the longer side at least boxPx.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= boxPx) sample *= 2
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/**
 * Stills decoded for the preview, kept up to a memory budget so that going back
 * and forth between clips does not decode them again. Safe to use from any thread.
 */
class PictureCache(context: Context) {

    private val appContext = context.applicationContext

    private val pictures = object : LruCache<Uri, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Uri, value: Bitmap): Int = value.byteCount
    }

    /** The picture if it has been decoded already. */
    fun peek(uri: Uri): Bitmap? = pictures.get(uri)

    /** The picture, decoded now if needed. Call it off the main thread. */
    fun load(uri: Uri): Bitmap? = pictures.get(uri) ?: smallPicture(appContext, uri, BOX_PX)?.also { pictures.put(uri, it) }

    private companion object {
        /** The longer side of a decoded still is at least this: sharp on a phone-sized preview. */
        const val BOX_PX = 1280
        /** About 32 MB of pictures. */
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}
