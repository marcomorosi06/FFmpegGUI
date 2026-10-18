package com.cuscus.ffmpeggui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.command.CoverTags
import com.cuscus.ffmpeggui.core.engine.CoverArtist
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [CoverArtist] on the platform decoders. Covers are JPEGs of at most
 * [MAX_SIDE_PX] a side, written next to the stills ([CacheJanitor.newCover]):
 *
 *  - a picture the user chooses is turned upright (ImageDecoder applies the
 *    orientation tag), cut square around its centre, as covers are shown;
 *  - a cover the file already holds is kept as it is when it is a JPEG of a
 *    fitting size, and made into one otherwise.
 */
class AndroidCoverArtist(
    context: Context,
    private val janitor: CacheJanitor,
) : CoverArtist {

    private val appContext = context.applicationContext

    override fun fromPicture(pictureUri: String): String? = try {
        decode(Uri.parse(pictureUri))?.let { save(square(it)) }
    } catch (e: Exception) {
        Log.w(TAG, "Could not make a cover: ${e.message}")
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Picture too large for a cover")
        null
    }

    override fun embedded(sourceUri: String): String? {
        val retriever = MediaMetadataRetriever()
        val bytes = try {
            retriever.setDataSource(appContext, Uri.parse(sourceUri))
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        } ?: return null
        return try {
            val size = CoverTags.pictureSize(bytes)
            val fitting = size != null && CoverTags.mimeType(bytes) == "image/jpeg" && max(size.first, size.second) <= MAX_SIDE_PX
            if (fitting) {
                write { it.writeBytes(bytes) }
            } else {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { save(fitted(it)) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep the file's cover: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** The picture at [uri], upright, at least about [MAX_SIDE_PX] on its shorter side when it is that large. */
    private fun decode(uri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val shorter = min(info.size.width, info.size.height)
            if (shorter > MAX_SIDE_PX) {
                val scale = MAX_SIDE_PX.toFloat() / shorter
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    /** The middle square of [bitmap], at most [MAX_SIDE_PX] a side. */
    private fun square(bitmap: Bitmap): Bitmap {
        val side = min(bitmap.width, bitmap.height)
        val cut = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        if (cut !== bitmap) bitmap.recycle()
        return fitted(cut)
    }

    /** [bitmap] no larger than [MAX_SIDE_PX] on its longer side. */
    private fun fitted(bitmap: Bitmap): Bitmap {
        val longer = max(bitmap.width, bitmap.height)
        if (longer <= MAX_SIDE_PX) return bitmap
        val scale = MAX_SIDE_PX.toFloat() / longer
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (resized !== bitmap) bitmap.recycle()
        return resized
    }

    /** Writes [bitmap] as a new cover; returns its content URI. */
    private fun save(bitmap: Bitmap): String? = try {
        write { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } }
    } finally {
        bitmap.recycle()
    }

    /** A new cover file filled by [fill] (false or an error = nothing kept); its content URI. */
    private fun write(fill: (File) -> Any): String? {
        val (file, uri) = janitor.newCover()
        return try {
            val result = fill(file)
            if (result != false && file.length() > 0) {
                uri
            } else {
                file.delete()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write a cover: ${e.message}")
            file.delete()
            null
        }
    }

    private companion object {
        const val TAG = "AndroidCoverArtist"

        /** Longest side of a cover: plenty for any player, small enough for the tags. */
        const val MAX_SIDE_PX = 1000

        const val JPEG_QUALITY = 90
    }
}
