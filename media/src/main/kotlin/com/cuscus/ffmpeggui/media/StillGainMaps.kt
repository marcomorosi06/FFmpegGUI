package com.cuscus.ffmpeggui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.hdr.GainMapMetadata
import com.cuscus.ffmpeggui.core.hdr.UltraHdr
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import android.util.LruCache
import kotlin.math.max

/**
 * The gain maps of Ultra HDR photos made into stills (see AndroidStillMaker).
 * A still is a PNG, which holds no gain map: when a photo has one, it is kept,
 * upright as the still is, as a grey PNG and a line of text saying how its
 * values are read ([GainMapMetadata.encode]). The picture editor can then keep
 * the photo's own HDR look (HdrMode.ORIGINAL).
 *
 * They are found by the still's content, not by where it is: a saved project
 * copies its stills into its own folder (the cache is emptied), and the copy
 * must still find its gain map, in this session and after a restart. They live
 * in the app's private files; one unused for [UNUSED_DAYS] days goes.
 */
class StillGainMaps(context: Context) {

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)

    /** The content key of each still already read, by its URI. */
    private val keys = LruCache<String, String>(64)

    /** A photo's gain map: its values (grey) and how they are read. */
    class Stored(val values: Bitmap, val metadata: GainMapMetadata)

    /** Keeps the gain map of the photo at [sourceUri], if it has one, for the still [still] made from it. */
    fun keep(sourceUri: String, still: File) {
        try {
            val bytes = appContext.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                // An Ultra HDR file is a JPG: a few megabytes, never hundreds.
                val read = input.readBytes()
                read.takeIf { it.size in 4..MAX_FILE_BYTES }
            } ?: return
            if ((bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) return
            val embedded = UltraHdr.read(bytes) ?: return
            val map = BitmapFactory.decodeByteArray(embedded.gainMapJpeg, 0, embedded.gainMapJpeg.size) ?: return
            val upright = upright(map, orientation(bytes))
            val key = still.inputStream().use(::digest)
            folder.mkdirs()
            val (picture, text) = files(key)
            picture.outputStream().use { out -> upright.compress(Bitmap.CompressFormat.PNG, 100, out) }
            text.writeText(embedded.metadata.encode())
            if (upright !== map) upright.recycle()
            map.recycle()
            prune()
            Log.i(TAG, "Kept the photo's gain map (${embedded.metadata.maxStops} stops)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep the photo's gain map: ${e.message}")
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Gain map too large to keep")
        }
    }

    /** True when the still at [stillUri] (or a copy of it) came with a photo's gain map. Off the main thread. */
    fun has(stillUri: String): Boolean {
        val key = keyOf(stillUri) ?: return false
        return files(key).let { (picture, text) -> picture.isFile && text.isFile }
    }

    /** The gain map of the still at [stillUri], at most [maxSide] on its longer side; null when there is none. Off the main thread. */
    fun read(stillUri: String, maxSide: Int): Stored? {
        val key = keyOf(stillUri) ?: return null
        val (picture, text) = files(key)
        if (!picture.isFile || !text.isFile) return null
        val metadata = GainMapMetadata.parse(text.readText()) ?: return null
        val now = System.currentTimeMillis()
        picture.setLastModified(now)
        text.setLastModified(now)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(picture.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val values = BitmapFactory.decodeFile(picture.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            Stored(values, metadata)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** The content key of the still at [uri]; null when it cannot be read. */
    private fun keyOf(uri: String): String? {
        keys.get(uri)?.let { return it }
        val key = try {
            appContext.contentResolver.openInputStream(Uri.parse(uri))?.use(::digest)
        } catch (e: Exception) {
            null
        } ?: return null
        keys.put(uri, key)
        return key
    }

    private fun files(key: String): Pair<File, File> = File(folder, "$key.png") to File(folder, "$key.txt")

    /** The gain maps no still has asked for in [UNUSED_DAYS] days. */
    private fun prune() {
        val oldest = System.currentTimeMillis() - UNUSED_DAYS * 24L * 60 * 60 * 1000
        folder.listFiles()?.filter { it.lastModified() < oldest }?.forEach { it.delete() }
    }

    /** The EXIF orientation of a JPG's bytes. */
    private fun orientation(bytes: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** [bitmap] turned and mirrored as [orientation] says, as the platform decoder does to the photo. */
    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val TAG = "StillGainMaps"
        const val FOLDER = "gainmaps"

        /** The largest photo looked into for a gain map. */
        const val MAX_FILE_BYTES = 80 * 1024 * 1024

        /** A gain map no still asked for in this many days goes. */
        const val UNUSED_DAYS = 180

        /** SHA-256 of what [input] holds, as hex. */
        fun digest(input: InputStream): String {
            val sha = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                sha.update(buffer, 0, n)
            }
            return sha.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
        }
    }
}
