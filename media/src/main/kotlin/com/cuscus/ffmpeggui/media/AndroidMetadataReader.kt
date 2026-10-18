package com.cuscus.ffmpeggui.media

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFprobeKit
import com.cuscus.ffmpeggui.core.engine.MetadataReader
import com.cuscus.ffmpeggui.core.model.MetadataTags
import org.json.JSONObject

/**
 * [MetadataReader] for the files the user opens:
 *
 *  - pictures: their EXIF description (the title), artist and copyright;
 *  - sound and video: the tags FFprobe finds on the file and on its first sound
 *    stream (Ogg and Opus keep them there), whatever their case (FLAC writes
 *    "TITLE"); if FFprobe cannot open the file, the platform retriever's.
 */
class AndroidMetadataReader(context: Context) : MetadataReader {

    private val appContext = context.applicationContext

    override fun read(sourceUri: String): MetadataTags? {
        val uri = Uri.parse(sourceUri)
        val type = runCatching { appContext.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return if (type.startsWith("image/")) pictureTags(uri) else probedTags(uri) ?: retrieverTags(uri)
    }

    private fun pictureTags(uri: Uri): MetadataTags? = try {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            fun tag(name: String) = exif.getAttribute(name)?.trim().orEmpty()
            MetadataTags(
                title = tag(ExifInterface.TAG_IMAGE_DESCRIPTION),
                artist = tag(ExifInterface.TAG_ARTIST),
                copyright = tag(ExifInterface.TAG_COPYRIGHT),
                fromSource = true,
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read picture tags: ${e.message}")
        null
    }

    private fun probedTags(uri: Uri): MetadataTags? {
        val input = ffmpegReadablePath(appContext, uri) ?: return null
        return try {
            // FFmpegKitNext is written in Kotlin: its results have getters, not properties.
            val info = FFprobeKit.getMediaInformation(input).getMediaInformation() ?: return null
            // Tags of the file first, then those of its first sound stream, by lower-case name.
            val found = HashMap<String, String>()
            fun take(tags: JSONObject?) {
                tags ?: return
                for (key in tags.keys()) {
                    val value = tags.optString(key).trim()
                    if (value.isNotEmpty()) found.putIfAbsent(key.lowercase(), value)
                }
            }
            take(info.getTags())
            take(info.getStreams().firstOrNull { it.getType() == "audio" }?.getTags())
            fun first(vararg keys: String) = keys.firstNotNullOfOrNull { found[it] }.orEmpty()
            MetadataTags(
                title = first("title"),
                artist = first("artist", "album_artist", "author", "performer"),
                album = first("album"),
                year = yearOf(first("date", "year", "creation_date")),
                comment = first("comment", "description"),
                genre = first("genre"),
                copyright = first("copyright"),
                fromSource = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not probe tags: ${e.message}")
            null
        }
    }

    private fun retrieverTags(uri: Uri): MetadataTags? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            fun tag(key: Int) = retriever.extractMetadata(key)?.trim().orEmpty()
            MetadataTags(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifEmpty { tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) },
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                year = yearOf(tag(MediaMetadataRetriever.METADATA_KEY_YEAR).ifEmpty { tag(MediaMetadataRetriever.METADATA_KEY_DATE) }),
                genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                // The retriever does not report comments or copyright: those the file keeps.
                fromSource = false,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read tags: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** The year of a date tag ("2024", "2024-05-01", "20240501T..."), or "" when there is none. */
    private fun yearOf(date: String): String = Regex("""(1[0-9]{3}|2[0-9]{3})""").find(date)?.value.orEmpty()

    private companion object {
        const val TAG = "AndroidMetadataReader"
    }
}
