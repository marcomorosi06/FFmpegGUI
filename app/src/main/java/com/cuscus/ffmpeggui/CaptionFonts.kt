package com.cuscus.ffmpeggui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The fonts the user added for captions (.ttf, .otf): each copied into the
 * app's own storage, in a folder of its own so that two files of one name can
 * live side by side, under the name it had. Captions keep the copy's path
 * (TextOverlay.fontFile); the preview and FFmpeg's drawtext read that file.
 */
internal object CaptionFonts {

    /** What the file picker offers: font types, and what some pickers call fonts. */
    val PICKER_TYPES = arrayOf(
        "font/ttf",
        "font/otf",
        "font/sfnt",
        "application/x-font-ttf",
        "application/x-font-otf",
        "application/font-sfnt",
        "application/vnd.ms-opentype",
        "application/octet-stream",
    )

    private const val FOLDER = "caption-fonts"
    private val EXTENSIONS = setOf("ttf", "otf")

    private fun folder(context: Context): File = File(context.filesDir, FOLDER)

    /** The fonts added so far, the newest first. Reads the disk: not on the main thread. */
    fun added(context: Context): List<File> =
        folder(context).listFiles()
            ?.flatMap { it.listFiles()?.toList().orEmpty() }
            ?.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** What a font file is called on its tile: its name without the extension. */
    fun label(path: String): String = File(path).nameWithoutExtension

    /**
     * Copies the font at [uri] into the app and checks that it can be drawn:
     * the copy, or null when it is not a .ttf or .otf the phone can read (nothing
     * is kept then).
     */
    suspend fun add(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val name = displayName(context, uri)?.let(::safeName) ?: return@withContext null
        if (name.substringAfterLast('.', "").lowercase() !in EXTENSIONS) return@withContext null
        val home = File(folder(context), UUID.randomUUID().toString())
        val target = File(home, name)
        val kept = try {
            home.mkdirs()
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
                true
            } ?: false
            // A file that only looks like a font is turned away here, not when it is drawn.
            if (copied && Typeface.Builder(target).build() != null) target else null
        } catch (e: Exception) {
            null
        }
        if (kept == null) home.deleteRecursively()
        kept
    }

    private fun displayName(context: Context, uri: Uri): String? {
        val shown = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        return shown ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    /** A name safe on disk and in an FFmpeg filter: letters, digits, dot, dash and underscore. */
    private fun safeName(name: String): String? {
        val clean = name.map { if ((it.isLetterOrDigit() && it.code < 128) || it in "._-") it else '_' }.joinToString("").trim('.')
        return clean.takeIf { it.isNotEmpty() }
    }
}
