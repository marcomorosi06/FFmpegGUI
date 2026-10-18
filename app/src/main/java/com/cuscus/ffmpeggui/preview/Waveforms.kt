package com.cuscus.ffmpeggui.preview

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

/**
 * The loudness outline of a sound file, as the timeline draws it: the peak level
 * (0..1) of every [bucketMs] of the file, from its start. While the file is
 * still being read it is not [complete]: the buckets not reached yet are 0.
 */
class Waveform(val peaks: FloatArray, val bucketMs: Int, val complete: Boolean = true) {

    /** Highest peak between [fromMs] and [toMs] of the file. */
    fun peakBetween(fromMs: Long, toMs: Long): Float {
        if (peaks.isEmpty()) return 0f
        val first = (fromMs / bucketMs).toInt().coerceIn(0, peaks.lastIndex)
        val last = (toMs / bucketMs).toInt().coerceIn(first, peaks.lastIndex)
        var peak = 0f
        for (i in first..last) if (peaks[i] > peak) peak = peaks[i]
        return peak
    }
}

/**
 * Decodes the sound of [uri] with the platform decoders and measures its
 * [Waveform]. Only the first [maxDurationMs] are read, so that a very long file
 * cannot keep the decoder busy for minutes. Null when the file has no sound or
 * cannot be decoded. Call it off the main thread; it stops early when the
 * thread is interrupted.
 *
 * The decoder is kept as busy as it can be: every free input buffer is filled
 * and every ready output drained before waiting, so the file is read many
 * times faster than it plays. [onProgress] gets the outline read so far a few
 * times a second, so that it can be drawn while the rest is being read.
 */
fun decodeWaveform(
    context: Context,
    uri: Uri,
    bucketMs: Int = WAVEFORM_BUCKET_MS,
    maxDurationMs: Long = WAVEFORM_MAX_MS,
    onProgress: ((Waveform) -> Unit)? = null,
): Waveform? {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs <= 0L) return null
        val readMs = min(durationUs / 1000L, maxDurationMs)
        val peaks = FloatArray((readMs / bucketMs + 1).toInt())

        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var layout = PcmLayout(
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                isFloat = false,
            )
            val scratch = SampleScratch()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastReport = System.nanoTime()

            /** Handles what the decoder answered; true when it handed something over. */
            fun handleOutput(outIndex: Int): Boolean = when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    layout = layoutOf(codec.outputFormat, layout)
                    true
                }
                outIndex >= 0 -> {
                    val output = codec.getOutputBuffer(outIndex)
                    if (output != null && info.size > 0) measure(output, info, layout, peaks, bucketMs, scratch)
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    true
                }
                else -> false
            }

            while (!outputDone) {
                if (Thread.currentThread().isInterrupted) return null
                var progressed = false
                // Every free input buffer, without waiting.
                while (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(0L)
                    if (inIndex < 0) break
                    val buffer = codec.getInputBuffer(inIndex)
                    val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                    if (size < 0 || extractor.sampleTime > readMs * 1000L) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                    progressed = true
                }
                // Every ready output, without waiting.
                while (!outputDone) {
                    if (!handleOutput(codec.dequeueOutputBuffer(info, 0L))) break
                    progressed = true
                }
                // Nothing to do until the decoder has worked: wait for it a moment.
                if (!progressed && !outputDone) handleOutput(codec.dequeueOutputBuffer(info, TIMEOUT_US))

                val now = System.nanoTime()
                if (onProgress != null && now - lastReport >= PROGRESS_NANOS) {
                    lastReport = now
                    onProgress(Waveform(peaks.copyOf(), bucketMs, complete = false))
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return Waveform(peaks, bucketMs)
    } catch (e: Exception) {
        Log.w(TAG, "No waveform: ${e.message}")
        return null
    } finally {
        extractor.release()
    }
}

/** How the decoder hands its samples over. */
private class PcmLayout(val sampleRate: Int, val channels: Int, val isFloat: Boolean)

/** Arrays the samples of a buffer are copied into, kept from one buffer to the next. */
private class SampleScratch {
    var shorts = ShortArray(0)
    var floats = FloatArray(0)
}

private fun layoutOf(format: MediaFormat, previous: PcmLayout): PcmLayout = PcmLayout(
    sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else previous.sampleRate,
    channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else previous.channels,
    isFloat = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
        format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT,
)

/** Raises the peaks of the buckets that the samples of [buffer] fall in (copied in bulk). */
private fun measure(
    buffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    layout: PcmLayout,
    peaks: FloatArray,
    bucketMs: Int,
    scratch: SampleScratch,
) {
    if (layout.sampleRate <= 0 || layout.channels <= 0) return
    val data = buffer.duplicate().order(ByteOrder.nativeOrder())
    data.position(info.offset)
    data.limit(info.offset + info.size)
    val bytesPerSample = if (layout.isFloat) 4 else 2
    val frames = info.size / (bytesPerSample * layout.channels)
    val count = frames * layout.channels
    if (layout.isFloat) {
        if (scratch.floats.size < count) scratch.floats = FloatArray(count)
        data.asFloatBuffer().get(scratch.floats, 0, count)
    } else {
        if (scratch.shorts.size < count) scratch.shorts = ShortArray(count)
        data.asShortBuffer().get(scratch.shorts, 0, count)
    }
    val startMs = info.presentationTimeUs / 1000.0
    val framesPerBucket = layout.sampleRate.toDouble() * bucketMs / 1000.0
    val firstFrameInBuckets = startMs / bucketMs
    var at = 0
    var bucket = firstFrameInBuckets.toInt()
    var peak = 0f
    for (frame in 0 until frames) {
        val nowBucket = (firstFrameInBuckets + frame / framesPerBucket).toInt()
        if (nowBucket != bucket) {
            if (bucket in peaks.indices && peak > peaks[bucket]) peaks[bucket] = peak.coerceAtMost(1f)
            bucket = nowBucket
            peak = 0f
        }
        for (channel in 0 until layout.channels) {
            val sample = if (layout.isFloat) abs(scratch.floats[at]) else abs(scratch.shorts[at].toInt()) / 32768f
            if (sample > peak) peak = sample
            at++
        }
    }
    if (bucket in peaks.indices && peak > peaks[bucket]) peaks[bucket] = peak.coerceAtMost(1f)
}

/** One peak per this many milliseconds of the file. */
const val WAVEFORM_BUCKET_MS = 20

/** Longest part of a file that is measured: half an hour. */
const val WAVEFORM_MAX_MS = 30L * 60L * 1000L

private const val TIMEOUT_US = 5_000L

/** How often the outline read so far is handed over while a file is being read. */
private const val PROGRESS_NANOS = 250_000_000L
private const val TAG = "Waveforms"
