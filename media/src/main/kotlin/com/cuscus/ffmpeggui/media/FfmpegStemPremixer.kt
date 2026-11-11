package com.cuscus.ffmpeggui.media

import android.content.Context
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.engine.StemPremixer
import com.cuscus.ffmpeggui.core.preview.StemBundle
import com.cuscus.ffmpeggui.core.preview.StemPremix
import java.io.File

/**
 * [StemPremixer] that runs [StemPremix]'s command with FFmpeg. The premixes
 * go into a folder of the cache of their own, named after what is mixed and
 * the stems' files ([StemPremix.fileName]): a mix heard before is found
 * there and played again without FFmpeg. The [CacheJanitor] removes those
 * not used for [KEPT_MS] at start-up, and any work left by a run cut short.
 *
 * When a premix cannot be made, the command and the end of FFmpeg's log go to
 * Logcat (tag [TAG]); the preview then plays the stems one by one.
 */
class FfmpegStemPremixer(
    context: Context,
    private val executor: FfmpegExecutor,
) : StemPremixer {

    private val appContext = context.applicationContext

    override suspend fun premix(bundle: StemBundle): String? {
        val inputs = bundle.heard.map { File(it.path) }
        if (inputs.any { !it.isFile }) {
            Log.w(TAG, "A stem of the premix is missing")
            return null
        }
        val directory = File(appContext.cacheDir, DIRECTORY).apply { mkdirs() }
        // Stems split again are written over the old ones: their size and time are in the name.
        val stamps = inputs.map { "${it.length()}:${it.lastModified()}" }
        val output = File(directory, StemPremix.fileName(bundle, stamps))
        if (output.isFile && output.length() > 0L) {
            // Heard again: kept a while longer.
            output.setLastModified(System.currentTimeMillis())
            return output.absolutePath
        }
        // Written beside, then renamed: a run cut short never leaves a file that looks done.
        val work = File(directory, output.name + WORK_SUFFIX)
        try {
            work.delete()
            val command = StemPremix.command(bundle, work.absolutePath)
            val log = StringBuffer() // appended to from FFmpeg's callback thread
            val result = executor.execute(command) { log.append(it) }
            if (result != ExecutionResult.Success || !work.isFile || work.length() == 0L) {
                if (result != ExecutionResult.Cancelled) report(result, command.toDisplayString(), log.toString())
                return null
            }
            return if (work.renameTo(output)) output.absolutePath else null
        } finally {
            work.delete()
        }
    }

    /** Writes why a premix failed to Logcat, in pieces short enough for it. */
    private fun report(result: ExecutionResult, command: String, log: String) {
        Log.w(TAG, "Premix failed ($result): $command")
        log.takeLast(LOG_TAIL_CHARS).chunked(LOGCAT_PIECE_CHARS).forEach { Log.w(TAG, it) }
    }

    companion object {
        /** The cache folder of the premixes. */
        const val DIRECTORY = "stem-premixes"

        /** The file a premix is written to, beside its name. */
        const val WORK_SUFFIX = ".work"

        /** A premix not heard for this long is removed at start-up. */
        const val KEPT_MS = 3L * 24 * 60 * 60 * 1000

        /** Logcat tag of the failures. */
        const val TAG = "StemPremixer"

        private const val LOG_TAIL_CHARS = 6000
        private const val LOGCAT_PIECE_CHARS = 3000
    }
}
