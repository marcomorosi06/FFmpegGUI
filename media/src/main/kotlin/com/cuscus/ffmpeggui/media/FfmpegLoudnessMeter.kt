package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.engine.LoudnessMeter
import com.cuscus.ffmpeggui.core.preview.loudnessAnalysisCommand
import com.cuscus.ffmpeggui.core.preview.parseIntegratedLoudness

/**
 * [LoudnessMeter] that runs FFmpeg's ebur128 analysis over the source audio.
 * Only audio is decoded, so it takes a small fraction of the clip's length.
 */
class FfmpegLoudnessMeter(
    context: Context,
    private val executor: FfmpegExecutor,
) : LoudnessMeter {

    private val appContext = context.applicationContext

    override suspend fun integratedLufs(sourceUri: String): Double? {
        val input = ffmpegReadablePath(appContext, Uri.parse(sourceUri)) ?: return null
        val log = StringBuffer() // appended to from FFmpeg's callback thread
        val result = executor.execute(loudnessAnalysisCommand(input)) { log.append(it) }
        return if (result == ExecutionResult.Success) parseIntegratedLoudness(log.toString()) else null
    }
}
