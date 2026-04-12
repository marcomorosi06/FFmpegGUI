package com.cuscus.ffmpeggui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

object MediaInfoExtractor {
    fun extract(context: Context, uri: Uri): MediaInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0

            val s = (durationMs / 1000) % 60
            val m = (durationMs / (1000 * 60)) % 60
            val h = (durationMs / (1000 * 60 * 60)) % 24
            val durationFormatted = String.format("%02d:%02d:%02d", h, m, s)

            var fileSizeBytes = 0L
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fileSizeBytes = fd.statSize
            }
            val fileSizeFormatted = "${(fileSizeBytes / (1024 * 1024))} MB"

            MediaInfo(
                durationMs = durationMs,
                durationFormatted = durationFormatted,
                isVideo = hasVideo,
                width = width,
                height = height,
                fileSizeBytes = fileSizeBytes,
                fileSizeFormatted = fileSizeFormatted
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}