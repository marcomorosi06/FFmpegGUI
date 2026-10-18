package com.cuscus.ffmpeggui.media

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.MediaProbe
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.VideoHdr

/** [MediaProbe] backed by the platform's MediaMetadataRetriever and ContentResolver. */
class AndroidMediaProbe(context: Context) : MediaProbe {

    private val appContext = context.applicationContext

    override fun describe(sourceUri: String): MediaInfo? {
        if (isPicture(sourceUri)) return describePicture(sourceUri) ?: describeMedia(sourceUri)
        // Some apps tell no type, or a vague one ("application/octet-stream"): what has
        // neither a length nor sound may still be a picture.
        val media = describeMedia(sourceUri)
        if (media != null && (media.durationMs > 0L || media.hasAudio)) return media
        return describePicture(sourceUri) ?: media
    }

    /** True when [sourceUri] is a picture rather than audio or video. */
    private fun isPicture(sourceUri: String): Boolean {
        val type = try {
            appContext.contentResolver.getType(Uri.parse(sourceUri))
        } catch (e: Exception) {
            null
        }
        if (type != null && type.startsWith("image/")) return true
        if (type != null && (type.startsWith("video/") || type.startsWith("audio/"))) return false
        return PICTURE_EXTENSIONS.any { sourceUri.lowercase().substringBefore('?').endsWith(it) }
    }

    /**
     * A picture: its size as displayed (the orientation tag applied), no length,
     * no sound; null when it cannot be read as one.
     */
    private fun describePicture(sourceUri: String): MediaInfo? {
        val uri = Uri.parse(sourceUri)
        val size = pictureSize(uri) ?: return null
        val sideways = isSideways(uri)
        return MediaInfo.of(
            durationMs = 0L,
            isVideo = true,
            width = if (sideways) size.second else size.first,
            height = if (sideways) size.first else size.second,
            fileSizeBytes = fileSize(sourceUri),
            hasAudio = false,
            isStill = true,
        )
    }

    /** The stored size of the picture at [uri], read from its header only; null when it is not one. */
    private fun pictureSize(uri: Uri): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read picture: ${e.message}")
        }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) return bounds.outWidth to bounds.outHeight
        // Formats BitmapFactory does not know here (HEIF, AVIF on some phones): the platform decoder's header.
        var found: Pair<Int, Int>? = null
        try {
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            ImageDecoder.decodeDrawable(source) { _, info, _ ->
                found = info.size.width to info.size.height
                // Only the header was wanted: stop before decoding.
                throw HeaderRead()
            }
        } catch (e: HeaderRead) {
            // The size is known.
        } catch (e: Exception) {
            Log.w(TAG, "Could not read picture header: ${e.message}")
        }
        found?.takeIf { it.first > 0 && it.second > 0 }?.let { return it }
        return null
    }

    /** True when the picture's orientation tag turns it a quarter (its width and height swap). */
    private fun isSideways(uri: Uri): Boolean = try {
        // A file descriptor can be sought, which some formats need (HEIF).
        appContext.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            when (ExifInterface(descriptor.fileDescriptor).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> true
                else -> false
            }
        } ?: false
    } catch (e: Exception) {
        // No tag that can be read: shown as stored.
        false
    }

    /** Thrown to stop [ImageDecoder] once it has told the size. */
    private class HeaderRead : RuntimeException()

    private fun describeMedia(sourceUri: String): MediaInfo? = withRetriever(sourceUri) { retriever ->
        // HAS_VIDEO / HAS_AUDIO report the string "yes" when the track exists.
        fun flag(key: Int) = retriever.extractMetadata(key).equals("yes", ignoreCase = true)
        fun int(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0

        // The stored frame may carry a rotation flag; players and FFmpeg apply it, so
        // report the size as it is displayed.
        val storedWidth = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val storedHeight = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val sideways = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0

        val isVideo = flag(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        val track = if (isVideo) videoTrack(sourceUri) else VideoTrack()
        MediaInfo.of(
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            isVideo = isVideo,
            width = if (sideways) storedHeight else storedWidth,
            height = if (sideways) storedWidth else storedHeight,
            fileSizeBytes = fileSize(sourceUri),
            hasAudio = flag(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO),
            hdr = track.hdr,
            frameRate = track.frameRate,
        )
    }

    /**
     * The transfer curve of the first video track, as its format tells it:
     * PQ or HLG (see VideoHdr); SDR when it says neither or cannot be read.
     */
    /** What the video track tells of its picture: its HDR kind and its frame rate (0 when not said). */
    private data class VideoTrack(val hdr: VideoHdr = VideoHdr.NONE, val frameRate: Float = 0f)

    private fun videoTrack(sourceUri: String): VideoTrack {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(appContext, Uri.parse(sourceUri), null)
            val video = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            val transfer = video?.takeIf { it.containsKey(MediaFormat.KEY_COLOR_TRANSFER) }?.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
            val hdr = when (transfer) {
                MediaFormat.COLOR_TRANSFER_ST2084 -> VideoHdr.PQ
                MediaFormat.COLOR_TRANSFER_HLG -> VideoHdr.HLG
                else -> VideoHdr.NONE
            }
            // Stored as an integer by some extractors and as a float by others.
            val rate = video?.takeIf { it.containsKey(MediaFormat.KEY_FRAME_RATE) }?.let { format ->
                runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                    .recoverCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }
                    .getOrNull()
            } ?: 0f
            VideoTrack(hdr, if (rate.isFinite() && rate > 0f) rate else 0f)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the video track: ${e.message}")
            VideoTrack()
        } finally {
            runCatching { extractor.release() }
        }
    }

    override fun durationMs(sourceUri: String): Long = withRetriever(sourceUri) { retriever ->
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } ?: 0L

    override fun displayName(sourceUri: String): String? = try {
        appContext.contentResolver
            .query(Uri.parse(sourceUri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read display name: ${e.message}")
        null
    }

    private fun fileSize(sourceUri: String): Long {
        val size = try {
            appContext.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { it.statSize }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read file size: ${e.message}")
            null
        }
        return (size ?: 0L).coerceAtLeast(0L)
    }

    private fun <T> withRetriever(sourceUri: String, block: (MediaMetadataRetriever) -> T?): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, Uri.parse(sourceUri))
            block(retriever)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read media metadata: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val TAG = "AndroidMediaProbe"

        /** Used when the content resolver does not tell the type. */
        val PICTURE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif", ".heic", ".heif")
    }
}
