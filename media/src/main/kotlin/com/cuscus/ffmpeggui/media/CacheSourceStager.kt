package com.cuscus.ffmpeggui.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.SourceStager
import com.cuscus.ffmpeggui.core.engine.StagingSession
import com.cuscus.ffmpeggui.core.output.sanitizeFileName
import java.io.File
import java.util.UUID

/**
 * Stages sources by copying them into the app cache, so FFmpeg can open them as
 * ordinary files whatever the content provider behind the URI is.
 *
 * Every session gets its own directory under [root]. Directories left behind by
 * a killed process are removed the next time a session is opened; this assumes
 * a single conversion at a time, which is what the app does.
 */
class CacheSourceStager(
    private val contentResolver: ContentResolver,
    private val root: File,
) : SourceStager {

    constructor(context: Context) : this(
        contentResolver = context.applicationContext.contentResolver,
        root = File(context.applicationContext.cacheDir, "staging"),
    )

    override fun open(): StagingSession {
        root.listFiles()?.forEach { it.deleteRecursively() }
        val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        return CacheStagingSession(contentResolver, directory)
    }
}

private class CacheStagingSession(
    private val contentResolver: ContentResolver,
    private val directory: File,
) : StagingSession {

    private var counter = 0

    override fun stage(sourceUri: String, preferredName: String): String? {
        val target = File(directory, "${counter++}_${safeName(preferredName)}")
        return try {
            val input = contentResolver.openInputStream(Uri.parse(sourceUri)) ?: return null
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            target.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Could not stage $preferredName: ${e.message}")
            target.delete()
            null
        }
    }

    override fun scratchFile(name: String): String = File(directory, safeName(name)).absolutePath

    override fun close() {
        directory.deleteRecursively()
    }

    /** Keeps the extension (FFmpeg uses it to guess formats) and neutralises the rest. */
    private fun safeName(name: String): String {
        val extension = sanitizeFileName(name.substringAfterLast('.', ""), fallback = "tmp", maxLength = 10)
        val base = sanitizeFileName(name.substringBeforeLast('.'), fallback = "file", maxLength = 40)
        return "$base.$extension"
    }

    private companion object {
        const val TAG = "CacheSourceStager"
    }
}
