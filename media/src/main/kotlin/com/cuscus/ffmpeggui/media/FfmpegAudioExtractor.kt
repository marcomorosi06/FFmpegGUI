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
     * lets [onProgress] say how far it is; [limitMs] (0: none) keeps only that
     * much from the start. Returns the result of FFmpeg; a file without sound fails.
     */
    suspend fun extract(sourceUri: String, target: File, durationMs: Long, limitMs: Long = 0L, onProgress: (Float) -> Unit = {}): ExecutionResult {
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
            ) + (if (limitMs > 0L) listOf("-t", String.format(java.util.Locale.ROOT, "%.3f", limitMs / 1000.0)) else emptyList()) +
                target.absolutePath,
        )
        val expected = (if (limitMs > 0L && (durationMs <= 0L || durationMs > limitMs)) limitMs else durationMs).takeIf { it > 0 }
        return executor.execute(command) { line -> progress.fraction(line, expected)?.let(onProgress) }
    }

    /**
     * Writes the sound of [sourceUri] to [target] as voice separation wants it:
     * the first audio track on two channels (a mono one on both), at [rate],
     * as raw 16-bit samples (little endian), its first sample at the file's time 0.
     */
    suspend fun extractStereo(sourceUri: String, target: File, durationMs: Long, rate: Int, onProgress: (Float) -> Unit = {}): ExecutionResult {
        val input = ffmpegReadablePath(appContext, Uri.parse(sourceUri)) ?: return ExecutionResult.Failed(UNREADABLE)
        return extractStereoFrom(input, target, durationMs, rate, onProgress)
    }

    /**
     * As [extractStereo], from a file of the app's own ([source]: a stem it
     * made, read again at another rate for the next model).
     */
    suspend fun extractStereo(source: File, target: File, durationMs: Long, rate: Int, onProgress: (Float) -> Unit = {}): ExecutionResult =
        extractStereoFrom(source.absolutePath, target, durationMs, rate, onProgress)

    private suspend fun extractStereoFrom(input: String, target: File, durationMs: Long, rate: Int, onProgress: (Float) -> Unit): ExecutionResult {
        target.parentFile?.mkdirs()
        target.delete()
        val command = FfmpegCommand(
            listOf(
                "-y", "-i", input,
                "-map", "0:a:0", "-vn", "-sn", "-dn",
                // As above: sample 0 is the file's time 0, the clock the picture and the edit are on.
                "-af", "aresample=$rate:first_pts=0",
                "-ac", "2",
                "-c:a", "pcm_s16le", "-f", "s16le",
                target.absolutePath,
            ),
        )
        val expected = durationMs.takeIf { it > 0 }
        return executor.execute(command) { line -> progress.fraction(line, expected)?.let(onProgress) }
    }

    /** Encodes the WAV file [wav] as FLAC into [target], losslessly (every channel kept). */
    suspend fun encodeFlac(wav: File, target: File, durationMs: Long, onProgress: (Float) -> Unit = {}): ExecutionResult {
        target.parentFile?.mkdirs()
        target.delete()
        val command = FfmpegCommand(
            // The format is named: the target may end in something else than .flac (a part being written).
            listOf("-y", "-i", wav.absolutePath, "-map", "0:a:0", "-c:a", "flac", "-compression_level", "5", "-f", "flac", target.absolutePath),
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
