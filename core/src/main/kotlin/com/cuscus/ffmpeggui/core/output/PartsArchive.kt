package com.cuscus.ffmpeggui.core.output

import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The files of a numbered output ("part_%03d.mp4", "frame_%03d.png": a video
 * split into parts, a sequence of frames) packed into one zip, so that the
 * result is a single file that can be shared and saved like any other.
 */
object PartsArchive {

    /**
     * The files FFmpeg wrote for [pattern] (a path whose name holds a "%03d" or
     * "%d" counter), in the counter's order. Other files next to them are left
     * out.
     */
    fun parts(pattern: String): List<File> {
        val file = File(pattern)
        val matcher = nameMatcher(file.name) ?: return emptyList()
        val directory = file.parentFile ?: File(".")
        return directory.listFiles().orEmpty()
            .filter { it.isFile }
            .mapNotNull { part -> matcher.matchEntire(part.name)?.let { part to it.groupValues[1].toBigInteger() } }
            .sortedBy { it.second }
            .map { it.first }
    }

    /**
     * Packs [parts] into [archive] under their own names, deleting each part
     * once it is in (so the storage needed stays about the size of the parts).
     * [checkCancelled] runs between blocks and may throw to stop; then, and on
     * any failure, the archive is deleted and what is left of the parts stays
     * for the caller to clean up.
     *
     * Media files are already compressed: the fastest level is used, which still
     * shrinks the few that are not (WAV, BMP, TIFF) and stores the others at
     * nearly their size.
     */
    @Throws(IOException::class)
    fun pack(parts: List<File>, archive: File, checkCancelled: () -> Unit = {}) {
        archive.parentFile?.mkdirs()
        var done = false
        try {
            ZipOutputStream(archive.outputStream().buffered(BUFFER_BYTES)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                val buffer = ByteArray(BUFFER_BYTES)
                for (part in parts) {
                    checkCancelled()
                    zip.putNextEntry(ZipEntry(part.name).apply { time = part.lastModified() })
                    part.inputStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            zip.write(buffer, 0, read)
                            checkCancelled()
                        }
                    }
                    zip.closeEntry()
                    part.delete()
                }
            }
            done = true
        } finally {
            if (!done) archive.delete()
        }
    }

    /** "part_%03d.mp4" -> part_(\d{3,})\.mp4; null without a counter. */
    private fun nameMatcher(name: String): Regex? {
        val counter = COUNTER.find(name) ?: return null
        val width = counter.groupValues[1].toIntOrNull() ?: 1
        val digits = if (width > 1) "\\d{$width,}" else "\\d+"
        val before = Regex.escape(name.substring(0, counter.range.first))
        val after = Regex.escape(name.substring(counter.range.last + 1))
        return Regex("$before($digits)$after")
    }

    /** printf's integer counter as FFmpeg reads it in output names: %d, %3d, %03d. */
    private val COUNTER = Regex("""%0?(\d*)d""")

    private const val BUFFER_BYTES = 64 * 1024
}
