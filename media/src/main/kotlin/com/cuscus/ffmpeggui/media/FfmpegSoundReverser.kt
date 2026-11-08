package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.engine.SoundReverser
import com.cuscus.ffmpeggui.core.preview.ReversedSound
import com.cuscus.ffmpeggui.core.preview.ReversedSoundRequest
import java.io.File

/**
 * [SoundReverser] that runs [ReversedSound]'s commands with FFmpeg: the sound
 * reversed in one go, the picture in pieces, the pieces joined, the sound
 * laid under them. The files go into a folder of the cache of their own,
 * named after the part they hold ([ReversedSound.fileName]): a part
 * reversed before is found there and played again without FFmpeg. The
 * [CacheJanitor] removes those not used for [KEPT_MS] at start-up, and any
 * work left by a run cut short.
 *
 * When a file cannot be made, the command and the end of FFmpeg's log go to
 * Logcat (tag [TAG]). When only the picture cannot be made, the reversed
 * sound is still given (a sound-only file, [ReversedSound.fileName] of the
 * part without picture): the preview then hears it while it steps the
 * picture back itself. With nothing made it steps the clip back in silence.
 */
class FfmpegSoundReverser(
    context: Context,
    private val executor: FfmpegExecutor,
) : SoundReverser {

    private val appContext = context.applicationContext

    override suspend fun reverse(request: ReversedSoundRequest, onProgress: (Float) -> Unit): String? {
        val directory = File(appContext.cacheDir, DIRECTORY).apply { mkdirs() }
        val output = File(directory, ReversedSound.fileName(request))
        if (output.isFile && output.length() > 0L) {
            // Used again: kept a while longer.
            output.setLastModified(System.currentTimeMillis())
            return output.absolutePath
        }
        val input = ffmpegReadablePath(appContext, Uri.parse(request.sourceUri))
        if (input == null) {
            Log.w(TAG, "FFmpeg cannot open the source of the reversed part")
            return null
        }
        // The work in a folder of its own, then the result renamed: a run cut short never
        // leaves a file that looks done.
        val work = File(directory, output.name + WORK_SUFFIX).apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val pieces = if (request.video) ReversedSound.pieces(request) else emptyList()
            // Each piece of picture, the sound, then joining and laying: one step each.
            val steps = pieces.size + (if (request.audio) 1 else 0) + (if (request.video) 2 else 0)
            var done = 0
            var cancelled = false
            suspend fun step(command: FfmpegCommand, made: File): Boolean {
                val log = StringBuffer() // appended to from FFmpeg's callback thread
                val result = executor.execute(command) { log.append(it) }
                val ok = result == ExecutionResult.Success && made.isFile && made.length() > 0L
                if (result == ExecutionResult.Cancelled) cancelled = true
                if (!ok && !cancelled) report(result, command.toDisplayString(), log.toString())
                if (ok) onProgress(++done / steps.toFloat())
                return ok
            }
            onProgress(0f)
            val sound = File(work, "sound.m4a")
            if (request.audio && !step(ReversedSound.command(request, input, sound.absolutePath), sound)) return null
            if (!request.video) return sound.takeIf { it.renameTo(output) }?.let { output.absolutePath }

            // The picture could not be made: the sound alone, unless the run was stopped.
            fun soundOnly(): String? {
                if (cancelled || !request.audio || !sound.isFile) return null
                val alone = File(directory, ReversedSound.fileName(request.copy(video = false)))
                Log.w(TAG, "The reversed picture could not be made: only the sound is used")
                alone.delete()
                return if (sound.renameTo(alone)) alone.absolutePath else null
            }
            val made = pieces.mapIndexed { index, (start, length) ->
                val piece = File(work, "piece_%04d.mp4".format(java.util.Locale.ROOT, index))
                if (!step(ReversedSound.pieceCommand(request, start, length, input, piece.absolutePath), piece)) return soundOnly()
                piece.absolutePath
            }
            val list = File(work, "pieces.txt").apply { writeText(ReversedSound.joinList(made)) }
            val picture = File(work, "picture.mp4")
            if (!step(ReversedSound.joinCommand(list.absolutePath, picture.absolutePath), picture)) return soundOnly()
            val result = if (request.audio) {
                val both = File(work, "both.mp4")
                if (!step(ReversedSound.layCommand(picture.absolutePath, sound.absolutePath, both.absolutePath), both)) return soundOnly()
                both
            } else {
                onProgress(1f)
                picture
            }
            return if (result.renameTo(output)) output.absolutePath else null
        } finally {
            work.deleteRecursively()
        }
    }

    /** Writes why a step failed to Logcat, in pieces short enough for it. */
    private fun report(result: ExecutionResult, command: String, log: String) {
        Log.w(TAG, "Reversing failed ($result): $command")
        log.takeLast(LOG_TAIL_CHARS).chunked(LOGCAT_PIECE_CHARS).forEach { Log.w(TAG, it) }
    }

    companion object {
        /** The cache folder of the reversed parts. */
        const val DIRECTORY = "reversed-sounds"

        /** The folder a part is made in, beside its file. */
        const val WORK_SUFFIX = ".work"

        /** A reversed part not played for this long is removed at start-up. */
        const val KEPT_MS = 3L * 24 * 60 * 60 * 1000

        /** Logcat tag of the failures. */
        const val TAG = "SoundReverser"

        private const val LOG_TAIL_CHARS = 6000
        private const val LOGCAT_PIECE_CHARS = 3000
    }
}
