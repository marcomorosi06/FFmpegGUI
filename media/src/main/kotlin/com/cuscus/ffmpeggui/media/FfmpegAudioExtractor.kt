package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.engine.ProgressParser
import java.io.File

/**
 * Takes the sound out of a media file the way speech recognition wants it:
 * the first audio track, mixed down to mono, resampled to [SAMPLE_RATE] and
 * written as raw 32-bit floats (little endian), its first sample at the
 * file's time 0. The file is read in place.
 */
class FfmpegAudioExtractor(context: Context, private val executor: FfmpegExecutor) {

    private val appContext = context.applicationContext
    private val progress = ProgressParser()

    /**
     * Writes the sound of [sourceUri] to [target]. [durationMs] (0 when unknown)
     * lets [onProgress] say how far it is. Returns the result of FFmpeg; a file
     * without sound fails.
     */
    suspend fun extract(sourceUri: String, target: File, durationMs: Long, onProgress: (Float) -> Unit = {}): ExecutionResult {
        val input = ffmpegReadablePath(appContext, Uri.parse(sourceUri)) ?: return ExecutionResult.Failed(UNREADABLE)
        target.parentFile?.mkdirs()
        target.delete()
        val command = FfmpegCommand(
            listOf(
                "-y", "-i", input,
                "-map", "0:a:0", "-vn", "-sn", "-dn",
                // Silence in front when the sound starts after the picture: sample 0 is the file's time 0,
                // the clock the editor places the words on.
                "-af", "aresample=$SAMPLE_RATE:first_pts=0",
                "-ac", "1",
                "-c:a", "pcm_f32le", "-f", "f32le",
                target.absolutePath,
            ),
        )
        val expected = durationMs.takeIf { it > 0 }
        return executor.execute(command) { line -> progress.fraction(line, expected)?.let(onProgress) }
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** Bytes of one sample in the written file. */
        const val BYTES_PER_SAMPLE = 4

        private const val UNREADABLE = -2
    }
}
