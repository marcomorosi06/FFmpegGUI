package com.cuscus.ffmpeggui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log

object MediaInfoExtractor {

    private const val TAG = "MediaInfoExtractor"

    fun extract(context: Context, uri: Uri): MediaInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            // METADATA_KEY_HAS_VIDEO returns the string "yes" when a video track
            // is present, not a boolean — check explicitly.
            val hasVideo = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                .equals("yes", ignoreCase = true)

            val width  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

            val s = (durationMs / 1000) % 60
            val m = (durationMs / 60_000) % 60
            val h = durationMs / 3_600_000
            val durationFormatted = String.format("%02d:%02d:%02d", h, m, s)

            var fileSizeBytes = 0L
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    fileSizeBytes = fd.statSize
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read file size: ${e.message}")
            }

            val fileSizeFormatted = when {
                fileSizeBytes >= 1_073_741_824L -> String.format("%.1f GB", fileSizeBytes / 1_073_741_824.0)
                fileSizeBytes >= 1_048_576L     -> String.format("%.1f MB", fileSizeBytes / 1_048_576.0)
                else                            -> String.format("%.0f KB", fileSizeBytes / 1_024.0)
            }

            MediaInfo(
                durationMs = durationMs,
                durationFormatted = durationFormatted,
                isVideo = hasVideo,
                width = width,
                height = height,
                fileSizeBytes = fileSizeBytes,
                fileSizeFormatted = fileSizeFormatted,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract media info: ${e.message}", e)
            null
        } finally {
            retriever.release()
        }
    }
}