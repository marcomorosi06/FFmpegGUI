package com.cuscus.ffmpeggui.preview

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.cuscus.ffmpeggui.core.image.ImageStage
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.image.imageStages
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageEffect
import com.cuscus.ffmpeggui.core.model.ImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The picture editor's preview: the source picture (a still, or a frame of a
 * video) at a phone-screen size, run through the same steps the export runs
 * (core.image), on the CPU, off the main thread.
 */

/** Longer side of the picture the editor works on. */
const val IMAGE_PREVIEW_PX = 1080

/** Longer side of the filter and effect thumbnails. */
const val IMAGE_THUMB_PX = 160

/** The pixels of [bitmap] as a [Raster]. */
fun Bitmap.toRaster(): Raster {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return Raster(width, height, pixels)
}

/**
 * This raster with every pixel opaque, as the export's `format=rgb24` reads a
 * picture: its colours kept, its transparency dropped.
 */
fun Raster.opaque(): Raster = Raster(width, height, IntArray(pixels.size) { pixels[it] or (0xFF shl 24) })

/** [raster] as a bitmap the screen can show. */
fun Raster.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

/** [bitmap] no larger than [boxPx] on its longer side (a copy only when it must shrink). */
fun fitted(bitmap: Bitmap, boxPx: Int): Bitmap {
    val longer = max(bitmap.width, bitmap.height)
    if (longer <= boxPx) return bitmap
    val scale = boxPx.toFloat() / longer
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}

/** A still of the project (an upright PNG), at most [boxPx] on its longer side; null when unreadable. */
fun stillPicture(context: Context, uri: Uri, boxPx: Int = IMAGE_PREVIEW_PX): Bitmap? =
    smallPicture(context, uri, boxPx)?.let { fitted(it, boxPx) }

/**
 * Frames of one video, the exact frame at each position asked (not the nearest
 * key frame): the picture is made from that frame. One frame at a time: a
 * call waits for the one before, and [close] for the frame being decoded.
 */
class FrameGrabber(context: Context, private val uri: Uri) : Closeable {
    private val appContext = context.applicationContext
    private val retriever = MediaMetadataRetriever()
    private var opened: Boolean? = null
    private var closed = false

    /** The frame at [positionMs] of the file, at most [boxPx] on its longer side. Call it off the main thread. */
    @Synchronized
    fun frameAt(positionMs: Long, boxPx: Int = IMAGE_PREVIEW_PX): Bitmap? {
        if (closed) return null
        // Opened on first use, away from the main thread.
        val ready = opened ?: runCatching { retriever.setDataSource(appContext, uri) }.isSuccess.also { opened = it }
        if (!ready) return null
        val us = positionMs.coerceAtLeast(0L) * 1000
        // HDR (10 bit) footage: some phones give no frame unless asked for an 8 bit one, or
        // give it in a config the editor cannot read (RGBA_1010102, F16, HARDWARE). Each way
        // is tried in turn, and what comes is made plain ARGB_8888.
        val attempts = listOf<() -> Bitmap?>(
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
                    retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, boxPx, boxPx, params)
                } else {
                    null
                }
            },
            { retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, boxPx, boxPx) },
            { retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)?.let { fitted(it, boxPx) } },
            { retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, boxPx, boxPx) },
        )
        for (attempt in attempts) {
            val frame = try {
                attempt()
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            } ?: continue
            plain(frame)?.let { return it }
        }
        return null
    }

    /** [frame] as a readable ARGB_8888 bitmap; null when it cannot be made one. */
    private fun plain(frame: Bitmap): Bitmap? {
        if (frame.config == Bitmap.Config.ARGB_8888) return frame
        return try {
            frame.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** [full] scaled down to fit a [boxPx] square, aspect kept. */
    private fun fitted(full: Bitmap, boxPx: Int): Bitmap {
        val scale = minOf(1f, boxPx.toFloat() / max(full.width, full.height).coerceAtLeast(1))
        if (scale >= 1f) return full
        return Bitmap.createScaledBitmap(full, (full.width * scale).roundToInt().coerceAtLeast(1), (full.height * scale).roundToInt().coerceAtLeast(1), true)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { retriever.release() }
    }
}

/**
 * What the picture looks like: [source] with [edit] and the turn, mirrors and
 * crop of [effects]. The drawing, texts, stickers and border are drawn on top
 * by the editor.
 */
suspend fun renderPicture(source: Raster, edit: ImageEdit, effects: Effects, subjectMask: Raster? = null): Raster {
    var raster = source
    for (stage in imageStages(edit, effects, source.width, source.height, subjectMask)) {
        currentCoroutineContext().ensureActive()
        raster = stage.apply(raster)
    }
    return raster
}

/**
 * [renderPicture] for the editor (with the mask of a subject key, as large as
 * [source], when it has one), which asks for a new look on every move of a
 * slider: the geometry (turn, mirrors, perspective, straighten, crop), the
 * slowest part, is kept from the last time while it stays the same, so a change
 * of light, colour or effect only runs the steps after it. Use it from one
 * coroutine at a time.
 */
class PictureRenderer {
    private var geometryKey: Any? = null
    private var geometryResult: Raster? = null

    suspend fun render(source: Raster, edit: ImageEdit, effects: Effects, subjectMask: Raster? = null): Raster {
        val stages = imageStages(edit, effects, source.width, source.height, subjectMask)
        val split = stages.indexOfFirst { !it.isGeometry }.let { if (it < 0) stages.size else it }
        val geometry = stages.subList(0, split)
        // The source is compared by identity (a new frame is a new raster), the steps by value.
        val key = source to geometry
        var raster = geometryResult?.takeIf { key == geometryKey } ?: run {
            var shaped = source
            for (stage in geometry) {
                currentCoroutineContext().ensureActive()
                shaped = stage.apply(shaped)
            }
            geometryKey = key
            geometryResult = shaped
            shaped
        }
        for (stage in stages.subList(split, stages.size)) {
            currentCoroutineContext().ensureActive()
            raster = stage.apply(raster)
        }
        return raster
    }
}

/** [source] with each ready-made filter at full strength, for the filter tiles. */
suspend fun filterThumbnails(source: Raster): Map<ImageFilter, Raster> =
    ImageFilter.entries.associateWith { renderPicture(source, ImageEdit(filter = it), Effects()) }

/** [source] with each effect at a middle strength, for the effect tiles. */
suspend fun effectThumbnails(source: Raster): Map<ImageEffect, Raster> =
    ImageEffect.entries.associateWith { renderPicture(source, ImageEdit(effect = it, effectAmount = 0.6f), Effects()) }
