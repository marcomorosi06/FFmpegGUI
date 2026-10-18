package com.cuscus.ffmpeggui.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Copies of finished exports in the phone's shared folders, where galleries,
 * music players and file managers find them: videos in Movies, sounds in
 * Music, pictures (and GIFs) in Pictures, anything else (a zip of parts) in
 * Download, each in an FFmpegGUI folder. Through MediaStore: the app adds
 * its own files there, which needs no permission (Android 10 and later, all
 * the app runs on).
 */
object PublicCopies {

    /** Whether copies can be made: always, kept for the settings that ask. */
    const val available: Boolean = true

    /**
     * Copies [file]; the folder it went to ("Movies/FFmpegGUI"), or null when it
     * could not be copied (nothing is left half written). Call it off the main thread.
     */
    fun save(context: Context, file: File): String? {
        if (!file.isFile) return null
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val (collection: Uri, directory: String) = when {
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(volume) to Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(volume) to Environment.DIRECTORY_MUSIC
            mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(volume) to Environment.DIRECTORY_PICTURES
            else -> MediaStore.Downloads.getContentUri(volume) to Environment.DIRECTORY_DOWNLOADS
        }
        val folder = "$directory/$FOLDER"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: RuntimeException) {
            null
        } ?: return null
        return try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("No stream for $uri")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            folder
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** The folder the copies go to, inside each shared folder. */
    private const val FOLDER = "FFmpegGUI"
}
