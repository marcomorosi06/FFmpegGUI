package com.cuscus.ffmpeggui.provenance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.marking.SpeechMarking
import com.cuscus.ffmpeggui.core.provenance.AiMarksReader
import com.cuscus.ffmpeggui.marking.MarkEngine
import com.cuscus.ffmpeggui.media.FfmpegAudioExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * The home's AI check: whether a picture, a sound or a video says it was
 * made or changed with AI, by the marks the app itself writes (EU AI Act,
 * Article 50(2)):
 *
 *  - its metadata: IPTC's Digital Source Type, in its XMP or its container's
 *    own tags, wherever the file keeps them (AiMarksReader.scan);
 *  - its invisible mark, when the AI marks are installed: TrustMark's in a
 *    picture's pixels, AudioSeal's in the speech of a sound or of a video's
 *    sound (a dub), found by [marker].
 *
 * Only these: a file that carries neither (most do not, and sharing often
 * strips the metadata) may still have been made with AI, which the screen
 * says. Everything is read on the phone; nothing is sent anywhere.
 */
class AiCheck(
    context: Context,
    private val marker: MarkEngine,
    private val extractor: FfmpegAudioExtractor,
) {
    private val appContext = context.applicationContext
    private val cache = File(appContext.cacheDir, SCRATCH)

    /** What kind of file was checked: which invisible mark it can hold. */
    enum class Kind { PICTURE, SOUND, VIDEO }

    /** What the invisible mark check gave. */
    enum class Mark {
        /** The mark is there. */
        FOUND,

        /** It was looked for and is not there. */
        NOT_FOUND,

        /** Too little speech to tell (a sound's mark is in its speech). */
        TOO_LITTLE_SPEECH,

        /** The file has no sound to listen to. */
        NO_SOUND,

        /** The AI marks are not installed: nothing to look for it with. */
        NOT_INSTALLED,

        /** The file could not be decoded (or the check failed). */
        UNREADABLE,
    }

    /** What the check found in a file, and a small picture of it to show (null for a sound). */
    class Result(
        val kind: Kind,
        val metadata: AiMarksReader.Found,
        val mark: Mark,
        val preview: Bitmap?,
    ) {
        /** One of the marks says AI. */
        val ai: Boolean get() = metadata.ai || mark == Mark.FOUND

        /** It says a voice in it was made with AI: a dub. */
        val voice: Boolean get() = kind != Kind.PICTURE && (mark == Mark.FOUND || metadata.voice)
    }

    /** Checks the file at [uri]. Throws IOException when it cannot be read at all. */
    suspend fun check(uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = read(uri) ?: throw IOException("The file cannot be read")
        val metadata = AiMarksReader.scan(bytes)
        val type = appContext.contentResolver.getType(uri).orEmpty()
        when {
            type.startsWith("audio/") -> Result(Kind.SOUND, metadata, soundMark(uri), null)
            type.startsWith("video/") -> Result(Kind.VIDEO, metadata, soundMark(uri), frame(uri))
            else -> {
                // A picture (or a file whose type the phone does not say: a picture if it decodes).
                val picture = decode(uri)
                if (picture == null && !type.startsWith("image/")) {
                    val video = frame(uri)
                    return@withContext Result(if (video != null) Kind.VIDEO else Kind.SOUND, metadata, soundMark(uri), video)
                }
                val mark = when {
                    picture == null -> Mark.UNREADABLE
                    !marker.ready -> Mark.NOT_INSTALLED
                    else -> guarded { if (marker.pictureMarked(picture)) Mark.FOUND else Mark.NOT_FOUND }
                }
                val preview = picture?.let(::small)
                if (picture != null && picture !== preview) picture.recycle()
                Result(Kind.PICTURE, metadata, mark, preview)
            }
        }
    }

    /**
     * AudioSeal's mark in the speech of [uri]'s first sound track (its first
     * LISTEN_MS): found when enough of the speech carries it.
     */
    private suspend fun soundMark(uri: Uri): Mark {
        if (!marker.ready) return Mark.NOT_INSTALLED
        cache.mkdirs()
        val pcm = File(cache, UUID.randomUUID().toString() + ".pcm")
        try {
            when (extractor.extract(uri.toString(), pcm, 0L, limitMs = LISTEN_MS)) {
                ExecutionResult.Success -> Unit
                ExecutionResult.Cancelled -> throw CancellationException("Stopped")
                is ExecutionResult.Failed -> return Mark.NO_SOUND
            }
            val count = (pcm.length() / FfmpegAudioExtractor.BYTES_PER_SAMPLE).toInt()
            if (count <= 0) return Mark.NO_SOUND
            val samples = FloatArray(count)
            RandomAccessFile(pcm, "r").use { file ->
                val bytes = ByteArray(count * FfmpegAudioExtractor.BYTES_PER_SAMPLE)
                file.readFully(bytes)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
            }
            val rate = FfmpegAudioExtractor.SAMPLE_RATE
            val lines = SpeechMarking.runs(samples, rate).map { samples.copyOfRange(it.first, it.last + 1) }
            if (lines.isEmpty()) return Mark.TOO_LITTLE_SPEECH
            return guarded {
                val verdict = marker.speech(lines, rate)
                Log.i(
                    TAG,
                    "AudioSeal: share ${verdict.share}, marked ${verdict.markedSamples / rate.toFloat()} s, " +
                        "message ours ${com.cuscus.ffmpeggui.core.marking.Marks.speechMessageIsOurs(verdict.message)}, " +
                        "where marked ${com.cuscus.ffmpeggui.core.marking.Marks.speechMessageIsOurs(verdict.markedMessage)}",
                )
                when {
                    // A dub alone: most of its speech is marked. A dub in a mix (music, effects, the
                    // video's other sounds): enough of it is, with the app's message.
                    verdict.marked || verdict.markedInMix -> Mark.FOUND
                    verdict.share == null -> Mark.TOO_LITTLE_SPEECH
                    else -> Mark.NOT_FOUND
                }
            }
        } catch (e: OutOfMemoryError) {
            return Mark.UNREADABLE
        } finally {
            pcm.delete()
        }
    }

    /** [look], with what keeps it from looking said as such. */
    private suspend fun guarded(look: suspend () -> Mark): Mark = try {
        look()
    } catch (e: MarkEngine.Missing) {
        Mark.NOT_INSTALLED
    } catch (e: IOException) {
        Log.w(TAG, "Could not look for the invisible mark", e)
        Mark.UNREADABLE
    } catch (e: OutOfMemoryError) {
        Mark.UNREADABLE
    }

    /**
     * The file's bytes where its marks are: all of it up to HEAD_BYTES +
     * TAIL_BYTES, else its head and its tail (a video's metadata may be at
     * either end).
     */
    private fun read(uri: Uri): ByteArray? = try {
        val size = appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        if (size > HEAD_BYTES + TAIL_BYTES) {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    val head = readUpTo(input, HEAD_BYTES)
                    // A file that cannot be read from the end gives its head alone.
                    val tail = try {
                        input.channel.position(size - TAIL_BYTES)
                        readUpTo(input, TAIL_BYTES)
                    } catch (e: IOException) {
                        ByteArray(0)
                    }
                    head + tail
                }
            }
        } else {
            appContext.contentResolver.openInputStream(uri)?.use { readUpTo(it, HEAD_BYTES + TAIL_BYTES) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the file", e)
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun readUpTo(input: InputStream, most: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < most) {
            val n = input.read(buffer, 0, minOf(buffer.size, most - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * The picture at [uri], at most MAX_PIXELS (the mark is read from a 256
     * square, so a smaller copy loses nothing of it), or null when it is not
     * a picture the phone can decode.
     */
    private fun decode(uri: Uri): Bitmap? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > MAX_PIXELS) sample *= 2
        repeat(3) {
            try {
                return resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                }
            } catch (e: OutOfMemoryError) {
                sample *= 2
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    /** A frame of the video at [uri] to show (a second in, or its first), or null. */
    private fun frame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val picture = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            picture?.let(::small)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** A copy of [picture] to show, its longer side at most PREVIEW_SIDE (itself when it already is). */
    private fun small(picture: Bitmap): Bitmap? {
        val longer = maxOf(picture.width, picture.height)
        if (longer <= PREVIEW_SIDE) return picture
        val scale = PREVIEW_SIDE.toFloat() / longer
        return try {
            Bitmap.createScaledBitmap(picture, (picture.width * scale).toInt().coerceAtLeast(1), (picture.height * scale).toInt().coerceAtLeast(1), true)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Removes sound files a stopped check left behind. */
    fun sweep() {
        cache.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val TAG = "AiCheck"
        const val SCRATCH = "ai-check"
        const val HEAD_BYTES = 64 * 1024 * 1024
        const val TAIL_BYTES = 32 * 1024 * 1024
        const val MAX_PIXELS = 24_000_000L
        const val PREVIEW_SIDE = 1280

        /** How much of a sound is listened to: a dub's voice is in its speech from the start. */
        const val LISTEN_MS = 10 * 60 * 1000L
    }
}
