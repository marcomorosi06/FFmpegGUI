package com.cuscus.ffmpeggui.core.output

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.sequenceFormat
import com.cuscus.ffmpeggui.core.time.segmenting
import java.io.File

/**
 * Where a conversion writes its result.
 *
 * @param commandPath  path handed to FFmpeg; may contain a "%03d" counter.
 * @param resultPath   what to show / share afterwards: the file itself, or the
 *                     zip the numbered files are packed into.
 * @param packed       true when [commandPath] is numbered and its files go
 *                     into the zip at [resultPath] (see [PartsArchive]).
 */
data class OutputPlan(
    val commandPath: String,
    val resultPath: String,
    val packed: Boolean,
)

/** Decides output file names. Pure: it never touches the file system. */
class OutputPlanner {

    fun plan(request: ConversionRequest, outputDirectory: String, timestamp: Long): OutputPlan {
        val base = baseName(request)
        val extension = request.format.extension

        fun single(name: String): OutputPlan {
            val file = File(outputDirectory, name).path
            return OutputPlan(commandPath = file, resultPath = file, packed = false)
        }

        // The numbered files are written to a folder of their own, then packed
        // into a zip named like it: a folder cannot be shared or saved.
        fun numbered(directoryName: String, pattern: String): OutputPlan {
            val directory = File(outputDirectory, directoryName)
            return OutputPlan(
                commandPath = File(directory, pattern).path,
                resultPath = "${directory.path}.zip",
                packed = true,
            )
        }

        return when {
            request.frameExtraction is FrameExtraction.SingleFrame -> single("${base}_frame_$timestamp.png")
            request.frameExtraction is FrameExtraction.Sequence ->
                numbered("${base}_frames_$timestamp", "frame_%05d.${request.sequenceFormat().extension}")
            request.segmenting() != null ->
                numbered("${base}_segments_$timestamp", "part_%03d.$extension")
            else -> single("${base}_$timestamp.$extension")
        }
    }

    /** Output path without extension, for hand-written commands that choose their own. */
    fun manualStem(request: ConversionRequest, outputDirectory: String, timestamp: Long): String =
        File(outputDirectory, "${baseName(request)}_$timestamp").path

    private fun baseName(request: ConversionRequest): String =
        sanitizeFileName(request.inputFileName.substringBeforeLast('.'), fallback = "output")
}

/**
 * Reduces a display name to characters that are safe in a path and inside an
 * FFmpeg argument (no separators, quotes, percent signs or whitespace).
 */
fun sanitizeFileName(name: String, fallback: String = "file", maxLength: Int = 60): String =
    name.map { if (it.isLetterOrDigit() || it == '_' || it == '-' || it == '.') it else '_' }
        .joinToString("")
        .trim('.', '_')
        .take(maxLength)
        .ifEmpty { fallback }
