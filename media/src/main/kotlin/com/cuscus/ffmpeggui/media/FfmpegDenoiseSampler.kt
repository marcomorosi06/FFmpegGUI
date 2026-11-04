package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.DenoiseSampler
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.preview.DenoiseSample
import com.cuscus.ffmpeggui.core.preview.DenoiseSampleRequest
import java.io.File

/**
 * [DenoiseSampler] that runs [DenoiseSample.command] with FFmpeg: the frames
 * of a short warm-up before the position decoded, a small square of the last
 * one written as a PNG. Quick next to an export, though a 4K HEVC file still
 * takes a moment to decode.
 *
 * The pictures go into a folder of the cache of their own, which the
 * [CacheJanitor] empties at start-up (a comparison left by a killed process).
 *
 * When a comparison cannot be made, the command and the end of FFmpeg's log
 * go to Logcat (tag [TAG]): the panel only says it failed.
 */
class FfmpegDenoiseSampler(
    context: Context,
    private val executor: FfmpegExecutor,
) : DenoiseSampler {

    private val appContext = context.applicationContext

    override suspend fun sample(request: DenoiseSampleRequest): String? {
        val input = ffmpegReadablePath(appContext, Uri.parse(request.sourceUri))
        if (input == null) {
            Log.w(TAG, "FFmpeg cannot open the source of the comparison")
            return null
        }
        val directory = File(appContext.cacheDir, DIRECTORY).apply { mkdirs() }
        val output = File(directory, "compare_${System.nanoTime()}.png")
        val command = DenoiseSample.command(request, input, output.absolutePath)
        val log = StringBuffer() // appended to from FFmpeg's callback thread
        var kept = false
        try {
            val result = executor.execute(command) { log.append(it) }
            kept = result == ExecutionResult.Success && output.isFile && output.length() > 0L
            if (!kept && result != ExecutionResult.Cancelled) report(result, command.toDisplayString(), log.toString())
            return if (kept) output.absolutePath else null
        } finally {
            // A failed or cancelled run leaves nothing behind.
            if (!kept) output.delete()
        }
    }

    /** Writes why a comparison failed to Logcat, in pieces short enough for it. */
    private fun report(result: ExecutionResult, command: String, log: String) {
        Log.w(TAG, "The comparison failed ($result): $command")
        log.takeLast(LOG_TAIL_CHARS).chunked(LOGCAT_PIECE_CHARS).forEach { Log.w(TAG, it) }
    }

    companion object {
        /** The cache folder of the comparisons. */
        const val DIRECTORY = "denoise-samples"

        /** Logcat tag of the failures. */
        const val TAG = "DenoiseSampler"

        /** How much of FFmpeg's log a failure reports (its end tells what went wrong). */
        private const val LOG_TAIL_CHARS = 6000

        /** Logcat cuts longer lines. */
        private const val LOGCAT_PIECE_CHARS = 3000
    }
}
