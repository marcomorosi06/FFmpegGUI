package com.cuscus.ffmpeggui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.StillMaker
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [StillMaker] on the platform decoders. Every still is written as a PNG in the
 * cache ([CacheJanitor.newStill]):
 *
 *  - upright: the orientation tag of a photo is applied here, because FFmpeg
 *    does not apply it to pictures; frames of a video come upright from the
 *    retriever;
 *  - at most [MAX_SIDE_PX] on its longer side, which is plenty for a video
 *    frame and keeps memory in check;
 *  - with even sides, as the H.264 encoder requires;
 *  - in a format every part of the pipeline reads (HEIC or WebP pictures
 *    included, which FFmpeg may not decode; an SVG is drawn at
 *    [SVG_SIDE_PX], its transparency kept).
 */
class AndroidStillMaker(
    context: Context,
    private val janitor: CacheJanitor,
    /** Keeps a photo's own gain map beside its still (Ultra HDR photos). */
    private val gainMaps: StillGainMaps? = null,
) : StillMaker {

    private val appContext = context.applicationContext

    override fun importPicture(sourceUri: String): String? = try {
        decodePicture(Uri.parse(sourceUri))?.let(::save)?.also { (file, _) -> gainMaps?.keep(sourceUri, file) }?.second
    } catch (e: Exception) {
        Log.w(TAG, "Could not import picture: ${e.message}")
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Picture too large to import")
        null
    }

    override fun captureFrame(sourceUri: String, positionMs: Long): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, Uri.parse(sourceUri))
            // The exact frame, not the nearest key frame: a freeze frame shows what was on screen.
            val frame = retriever.getFrameAtTime(positionMs.coerceAtLeast(0L) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            frame?.let { save(fitted(it))?.second }
        } catch (e: Exception) {
            Log.w(TAG, "Could not capture frame: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Frame too large to capture")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** The picture at [uri], upright and no larger than needed; an SVG drawn at [SVG_SIDE_PX]. */
    private fun decodePicture(uri: Uri): Bitmap? {
        if (isSvg(uri)) return drawnSvg(uri)?.let(::fitted)
        return decodeRaster(uri)
    }

    /** True for an SVG: by its type, its name, or (for a file without either) its first bytes. */
    private fun isSvg(uri: Uri): Boolean {
        val type = runCatching { appContext.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (type == "image/svg+xml") return true
        if (uri.lastPathSegment?.lowercase()?.endsWith(".svg") == true) return true
        if (type.startsWith("image/") && type != "image/svg+xml") return false
        val head = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = ByteArray(512)
                val read = stream.read(bytes)
                if (read > 0) String(bytes, 0, read, Charsets.UTF_8) else ""
            }
        }.getOrNull().orEmpty()
        return head.contains("<svg")
    }

    /**
     * The SVG at [uri] drawn (AndroidSVG) with its longer side [SVG_SIDE_PX],
     * its transparent parts kept; its size from its width and height, else its
     * view box, else a square.
     */
    private fun drawnSvg(uri: Uri): Bitmap? {
        val svg = appContext.contentResolver.openInputStream(uri)?.use { SVG.getFromInputStream(it) } ?: return null
        val box = svg.documentViewBox
        var w = svg.documentWidth
        var h = svg.documentHeight
        if (w <= 0f || h <= 0f) {
            w = box?.width() ?: SVG_SIDE_PX.toFloat()
            h = box?.height() ?: SVG_SIDE_PX.toFloat()
        }
        if (w <= 0f || h <= 0f) return null
        // Without a view box the drawing would not scale: its own size becomes one.
        if (box == null) svg.setDocumentViewBox(0f, 0f, w, h)
        val scale = SVG_SIDE_PX / max(w, h)
        val width = (w * scale).roundToInt().coerceAtLeast(1)
        val height = (h * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, width.toFloat(), height.toFloat()))
        return bitmap
    }

    /** A raster picture (JPG, PNG, HEIC, WebP...) at [uri], upright and no larger than needed. */
    private fun decodeRaster(uri: Uri): Bitmap? {
        // ImageDecoder applies the orientation tag itself and reads HEIF and WebP.
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longer = max(info.size.width, info.size.height)
            if (longer > MAX_SIDE_PX) {
                val scale = MAX_SIDE_PX.toFloat() / longer
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        return fitted(bitmap)
    }

    /** [bitmap] no larger than [MAX_SIDE_PX] and with even sides. */
    private fun fitted(bitmap: Bitmap): Bitmap {
        val longer = max(bitmap.width, bitmap.height)
        val scale = if (longer > MAX_SIDE_PX) MAX_SIDE_PX.toFloat() / longer else 1f
        var width = (bitmap.width * scale).roundToInt().coerceAtLeast(2)
        var height = (bitmap.height * scale).roundToInt().coerceAtLeast(2)
        width -= width % 2
        height -= height % 2
        if (width == bitmap.width && height == bitmap.height) return bitmap
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (resized !== bitmap) bitmap.recycle()
        return resized
    }

    /** Writes [bitmap] as a new still; returns its file and its content URI. */
    private fun save(bitmap: Bitmap): Pair<File, String>? {
        val (file, uri) = janitor.newStill()
        return try {
            val written = file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (written) {
                file to uri
            } else {
                deleteQuietly(file)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write still: ${e.message}")
            deleteQuietly(file)
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun deleteQuietly(file: File) {
        if (file.exists() && !file.delete()) Log.w(TAG, "Could not delete ${file.name}")
    }

    private companion object {
        const val TAG = "AndroidStillMaker"

        /** Longest side of a still: a 4K frame. */
        const val MAX_SIDE_PX = 3840

        /** Longer side an SVG is drawn at: sharp for any edit, light enough for the phone. */
        const val SVG_SIDE_PX = 2048f
    }
}
