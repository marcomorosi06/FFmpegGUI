package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.AudioDenoiseSampler
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.preview.AudioDenoiseSample
import com.cuscus.ffmpeggui.core.preview.AudioDenoiseSampleRequest
import java.io.File

/**
 * [AudioDenoiseSampler] that runs [AudioDenoiseSample.command] with FFmpeg:
 * a few seconds of the clip's sound, as it is and denoised, as two WAV files.
 *
 * They go into the folder of the denoise comparisons, which the
 * [CacheJanitor] empties at start-up (a comparison left by a killed process).
 * When one cannot be made, the command and the end of FFmpeg's log go to
 * Logcat (tag [TAG]): the panel only says it failed.
 */
class FfmpegAudioDenoiseSampler(
    context: Context,
    private val executor: FfmpegExecutor,
) : AudioDenoiseSampler {

    private val appContext = context.applicationContext

    override suspend fun sample(request: AudioDenoiseSampleRequest): Pair<String, String>? {
        val input = ffmpegReadablePath(appContext, Uri.parse(request.sourceUri))
        if (input == null) {
            Log.w(TAG, "FFmpeg cannot open the source of the comparison")
            return null
        }
        val directory = File(appContext.cacheDir, FfmpegDenoiseSampler.DIRECTORY).apply { mkdirs() }
        val stamp = System.nanoTime()
        val before = File(directory, "sound_before_$stamp.wav")
        val after = File(directory, "sound_after_$stamp.wav")
        val command = AudioDenoiseSample.command(request, input, before.absolutePath, after.absolutePath)
        val log = StringBuffer() // appended to from FFmpeg's callback thread
        var kept = false
        try {
            val result = executor.execute(command) { log.append(it) }
            kept = result == ExecutionResult.Success && before.length() > WAV_HEADER_BYTES && after.length() > WAV_HEADER_BYTES
            if (!kept && result != ExecutionResult.Cancelled) {
                Log.w(TAG, "The comparison failed ($result): ${command.toDisplayString()}")
                log.takeLast(LOG_TAIL_CHARS).chunked(LOGCAT_PIECE_CHARS).forEach { Log.w(TAG, it) }
            }
            return if (kept) before.absolutePath to after.absolutePath else null
        } finally {
            // A failed or cancelled run leaves nothing behind.
            if (!kept) {
                before.delete()
                after.delete()
            }
        }
    }

    companion object {
        /** Logcat tag of the failures. */
        const val TAG = "AudioDenoiseSampler"

        /** A WAV with no sound in it is its header alone. */
        private const val WAV_HEADER_BYTES = 44L

        /** How much of FFmpeg's log a failure reports (its end tells what went wrong). */
        private const val LOG_TAIL_CHARS = 6000

        /** Logcat cuts longer lines. */
        private const val LOGCAT_PIECE_CHARS = 3000
    }
}
