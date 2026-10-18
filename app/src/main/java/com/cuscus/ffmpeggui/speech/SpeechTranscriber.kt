package com.cuscus.ffmpeggui.speech

import android.content.Context
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.subtitles.RecognizedWords
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.media.FfmpegAudioExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Finds the words said in a media file, with their times on the file's clock.
 *
 * The sound is first taken out with FFmpeg (16 kHz mono floats, in the cache),
 * then fed to the model [CHUNK_SECONDS] at a time, however long it is (an hour
 * is about 120 pieces): the model's memory grows with the square of what it
 * hears at once, and the sound waits on disk, not in memory. Each chunk ends at the quietest
 * moment of its last [CUT_WINDOW_SECONDS], so that no word is cut in two. The
 * words of every chunk are handed over as soon as they are found.
 */
class SpeechTranscriber(
    context: Context,
    private val engine: SpeechEngine,
    private val extractor: FfmpegAudioExtractor,
) {

    /** Where the work on one file is. */
    sealed interface Step {
        /** Taking the sound out of the file: [fraction] done, null while unknown. */
        data class Extracting(val fraction: Float?) : Step

        /** Loading the model into memory. */
        data object Loading : Step

        /**
         * Listening: [fraction] of the sound done, piece [part] of about [parts]
         * (the sound is heard [CHUNK_SECONDS] at a time, however long it is).
         */
        data class Listening(val fraction: Float, val part: Int = 0, val parts: Int = 0) : Step
    }

    /** The file has no sound that could be read. */
    class NoSound : IOException("No sound to listen to")

    private val cache = File(context.applicationContext.cacheDir, "speech")

    /**
     * The words of [sourceUri] ([durationMs] long, 0 when unknown). [onStep]
     * tells how far it is, [onWords] gets the words of every chunk as found.
     */
    suspend fun transcribe(
        sourceUri: String,
        durationMs: Long,
        onStep: (Step) -> Unit,
        onWords: (List<SpokenWord>) -> Unit,
    ): List<SpokenWord> {
        val pcm = File(cache, UUID.randomUUID().toString() + ".pcm")
        try {
            onStep(Step.Extracting(null))
            when (extractor.extract(sourceUri, pcm, durationMs) { onStep(Step.Extracting(it)) }) {
                ExecutionResult.Success -> Unit
                ExecutionResult.Cancelled -> throw CancellationException("Stopped")
                is ExecutionResult.Failed -> throw NoSound()
            }
            val total = (pcm.length() / FfmpegAudioExtractor.BYTES_PER_SAMPLE).toInt()
            if (total <= 0) throw NoSound()
            onStep(Step.Loading)
            val words = mutableListOf<SpokenWord>()
            engine.use { recognizer ->
                val chunk = CHUNK_SECONDS * RATE
                // About how many pieces: each ends a little early, in a quiet spot.
                val parts = (total + chunk - 1) / chunk
                var part = 1
                onStep(Step.Listening(0f, part, parts))
                RandomAccessFile(pcm, "r").use { file ->
                    val bytes = ByteArray(chunk * FfmpegAudioExtractor.BYTES_PER_SAMPLE)
                    var start = 0
                    while (start < total) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(chunk, total - start)
                        file.seek(start.toLong() * FfmpegAudioExtractor.BYTES_PER_SAMPLE)
                        file.readFully(bytes, 0, count * FfmpegAudioExtractor.BYTES_PER_SAMPLE)
                        val samples = FloatArray(count)
                        ByteBuffer.wrap(bytes, 0, count * FfmpegAudioExtractor.BYTES_PER_SAMPLE)
                            .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
                        // The last chunk takes everything; the others end in the quietest spot near their end.
                        val length = if (start + count >= total) {
                            count
                        } else {
                            RecognizedWords.quietestCut(samples, count - CUT_WINDOW_SECONDS * RATE, count, CUT_FRAME).coerceAtLeast(RATE)
                        }
                        val piece = if (length == count) samples else samples.copyOf(length)
                        val heard = recognizer.recognize(piece, RATE)
                        val offsetMs = start * 1000L / RATE
                        val found = RecognizedWords.fromTokens(heard.tokens, heard.starts, heard.durations, offsetMs, (start + length) * 1000L / RATE)
                        words += found
                        if (found.isNotEmpty()) onWords(found)
                        start += length
                        if (start < total) part++
                        onStep(Step.Listening(start.toFloat() / total, part, maxOf(parts, part)))
                    }
                }
            }
            return words
        } finally {
            pcm.delete()
        }
    }

    /** Removes sound files a stopped or killed run left behind. */
    fun sweep() {
        cache.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val RATE = FfmpegAudioExtractor.SAMPLE_RATE

        /** Heard at once: about 1.5 GB of memory with the model. */
        const val CHUNK_SECONDS = 30

        /** The cut moves back at most this far, to the quietest 20 ms. */
        private const val CUT_WINDOW_SECONDS = 2
        private const val CUT_FRAME = RATE / 50
    }
}
