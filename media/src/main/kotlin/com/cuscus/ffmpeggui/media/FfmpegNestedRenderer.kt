package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.ConversionEngine
import com.cuscus.ffmpeggui.core.engine.ConversionEvent
import com.cuscus.ffmpeggui.core.engine.ConversionJob
import com.cuscus.ffmpeggui.core.engine.ConversionOutcome
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.engine.OutputDirectoryProvider
import com.cuscus.ffmpeggui.core.stack.NestedRenderer
import com.cuscus.ffmpeggui.core.stack.SoundSwap
import java.io.File

/**
 * [NestedRenderer] on the app's own engine and FFmpeg: [engine] renders into
 * [workDirectory] (see [workDirectoryOf]), never among the exports; the file
 * made is then moved into the cache folder the [janitor] owns for what
 * editors of the stack apply (`nested/`), and handed back as a content URI
 * of the app's. A saved project copies it into its own folder, as it does
 * recordings and stills; the janitor deletes it when the project closes
 * without it and at start-up.
 */
class FfmpegNestedRenderer(
    context: Context,
    private val engine: ConversionEngine,
    private val executor: FfmpegExecutor,
    private val janitor: CacheJanitor,
) : NestedRenderer {

    private val appContext = context.applicationContext

    override suspend fun render(job: ConversionJob, onProgress: (Float) -> Unit): String? {
        onProgress(0f)
        val outcome = engine.run(job) { event -> if (event is ConversionEvent.Progress) onProgress(event.fraction) }
        return when (outcome) {
            is ConversionOutcome.Success -> adopt(File(outcome.outputPath))
            is ConversionOutcome.Failure -> {
                Log.w(TAG, "The nested project could not be rendered: ${outcome.reason} ${outcome.detail.takeLast(LOG_TAIL_CHARS)}")
                null
            }
            ConversionOutcome.Cancelled -> null
        }
    }

    override suspend fun swapSound(video: String, sound: String, offsetMs: Long, durationMs: Long?, onProgress: (Float) -> Unit): String? {
        val videoPath = ffmpegReadablePath(appContext, Uri.parse(video)) ?: return null.also { Log.w(TAG, "FFmpeg cannot open the clip's file") }
        val soundPath = ffmpegReadablePath(appContext, Uri.parse(sound)) ?: return null.also { Log.w(TAG, "FFmpeg cannot open the edited sound") }
        val (file, uri) = janitor.newNested(SoundSwap.EXTENSION)
        // Written beside its place, then renamed: a run cut short never leaves a file that looks done.
        val partial = File(file.parentFile, ".${file.name}.part.${SoundSwap.EXTENSION}")
        onProgress(0f)
        val command = SoundSwap.command(videoPath, soundPath, offsetMs, durationMs, partial.absolutePath)
        val log = StringBuffer() // appended to from FFmpeg's callback thread
        val result = try {
            executor.execute(command) { log.append(it) }
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
        if (result != ExecutionResult.Success || !partial.isFile || partial.length() == 0L || !partial.renameTo(file)) {
            if (result != ExecutionResult.Cancelled) Log.w(TAG, "The sound could not be laid back ($result): ${command.toDisplayString()} ${log.toString().takeLast(LOG_TAIL_CHARS)}")
            partial.delete()
            return null
        }
        onProgress(1f)
        return uri
    }

    /** [made] moved into the janitor's folder; its content URI, or null when it cannot be moved. */
    private fun adopt(made: File): String? {
        if (!made.isFile) return null
        val (file, uri) = janitor.newNested(made.extension.ifBlank { "bin" })
        val moved = made.renameTo(file) || runCatching { made.copyTo(file, overwrite = true); made.delete(); true }.getOrDefault(false)
        return if (moved) uri else null.also { Log.w(TAG, "The rendered file could not be kept") }
    }

    companion object {
        private const val TAG = "NestedRenderer"
        private const val LOG_TAIL_CHARS = 3000

        /** Where [ConversionEngine] renders for the stack: a folder of the internal cache, emptied by the moves. */
        fun workDirectoryOf(context: Context): OutputDirectoryProvider =
            OutputDirectoryProvider { File(context.applicationContext.cacheDir, CacheJanitor.NESTED_WORK).absolutePath }
    }
}
