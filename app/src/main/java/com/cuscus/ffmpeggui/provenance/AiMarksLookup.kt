package com.cuscus.ffmpeggui.provenance

import android.content.Context
import android.net.Uri
import com.cuscus.ffmpeggui.core.provenance.AiMarksReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a picture opened in the app says a model changed it (its XMP, see
 * AiMarksReader): the magic eraser's results, wherever a project copied
 * them, and pictures other programs marked. Each file is read once (only its
 * first chunks: the marks come before the picture's data in a JPEG, and a
 * PNG's chunks are skipped over). What is exported from them says so too.
 */
class AiMarksLookup(context: Context) {
    private val appContext = context.applicationContext
    private val known = ConcurrentHashMap<String, Boolean>()

    fun of(uri: Uri): Boolean = known.getOrPut(uri.toString()) { read(uri) }

    private fun read(uri: Uri): Boolean {
        val marked = try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                AiMarksReader.xmp(input.buffered())?.let(AiMarksReader::changedWithAi)
            }
        } catch (e: Exception) {
            null
        }
        if (marked == true) return true
        // One of the magic eraser's own pictures that cannot be read now is still one.
        val name = uri.lastPathSegment.orEmpty()
        val ours = uri.scheme == "file" && uri.path.orEmpty().startsWith(appContext.filesDir.absolutePath) &&
            (name.startsWith(ERASED) || name.contains("_$ERASED"))
        return ours
    }

    private companion object {
        const val ERASED = "erased-"
    }
}
