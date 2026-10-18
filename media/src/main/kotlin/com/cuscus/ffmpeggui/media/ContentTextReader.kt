package com.cuscus.ffmpeggui.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.TextSourceReader
import java.io.ByteArrayOutputStream

/** [TextSourceReader] backed by the ContentResolver. */
class ContentTextReader(private val contentResolver: ContentResolver) : TextSourceReader {

    constructor(context: Context) : this(context.applicationContext.contentResolver)

    override fun readText(sourceUri: String, maxBytes: Int): String? = try {
        contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
            val buffer = ByteArray(8 * 1024)
            val bytes = ByteArrayOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                bytes.write(buffer, 0, read)
                if (bytes.size() > maxBytes) return null
            }
            bytes.toString(Charsets.UTF_8.name())
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read text source: ${e.message}")
        null
    }

    private companion object {
        const val TAG = "ContentTextReader"
    }
}
