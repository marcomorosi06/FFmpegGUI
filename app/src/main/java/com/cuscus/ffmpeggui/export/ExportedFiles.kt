package com.cuscus.ffmpeggui.export

import com.cuscus.ffmpeggui.settings.ExportsUsage
import java.io.File

/**
 * The exports kept in the app's own storage (every export is written there
 * first; see AppOutputDirectory): how much they take, and removing them.
 * Copies in the phone's shared folders (PublicCopies) are not touched.
 */
class ExportedFiles(private val directory: () -> String) {

    /** How many files there are and how much they take. Call it off the main thread. */
    fun usage(): ExportsUsage {
        val files = File(directory()).walkTopDown().filter { it.isFile }.toList()
        return ExportsUsage(files = files.size, bytes = files.sumOf { it.length() })
    }

    /** Removes every exported file (and the folders of parts left); how many files went. */
    fun deleteAll(): Int {
        val root = File(directory())
        var deleted = 0
        root.listFiles().orEmpty().forEach { entry ->
            val count = entry.walkTopDown().count { it.isFile }
            if (entry.deleteRecursively()) deleted += count
        }
        return deleted
    }
}
