package com.cuscus.ffmpeggui.media

import android.content.Context
import com.cuscus.ffmpeggui.core.engine.OutputDirectoryProvider
import java.io.File

/**
 * Stores results in the app-specific external files directory, which needs no
 * storage permission. Falls back to internal storage when external storage is
 * unavailable.
 */
class AppOutputDirectory(context: Context) : OutputDirectoryProvider {

    private val appContext = context.applicationContext

    override fun outputDirectory(): String {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(base, DIRECTORY_NAME).absolutePath
    }

    private companion object {
        const val DIRECTORY_NAME = "FFmpegGui"
    }
}
