package com.cuscus.ffmpeggui.marking

import com.cuscus.ffmpeggui.core.marking.Resampler
import com.cuscus.ffmpeggui.core.marking.SpeechMarking
import com.cuscus.ffmpeggui.core.provenance.SoundSeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioSeal's mark on a finished file's whole sound (see SoundSeal): the WAV
 * the engine took out (32-bit float, its own rate and channels) read a piece
 * at a time, each piece mixed down to mono at 16 kHz, its watermark made
 * there ([MarkEngine.watermarkOf]), brought back to the file's rate and added
 * to every channel. Pieces of [PIECE_SECONDS]: an hour of sound never sits in
 * memory at once, and the progress moves as each is done.
 */
class SoundSealer(private val marker: MarkEngine) : SoundSeal.SoundSealer {

    override suspend fun seal(input: File, output: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (!marker.ready) throw MarkEngine.Missing()
        RandomAccessFile(input, "r").use { source ->
            val wav = FloatWav.read(source)
            RandomAccessFile(output, "rw").use { target ->
                target.setLength(0)
                FloatWav.writeHeader(target, wav.channels, wav.rate, wav.frames)
                val piece = wav.rate * PIECE_SECONDS
                val bytes = ByteArray(piece * wav.channels * 4)
                var frame = 0L
                while (frame < wav.frames) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(piece.toLong(), wav.frames - frame).toInt()
                    val length = count * wav.channels * 4
                    source.seek(wav.dataOffset + frame * wav.channels * 4)
                    source.readFully(bytes, 0, length)
                    val samples = FloatArray(count * wav.channels)
                    ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
                    val mono = FloatArray(count) { i ->
                        var sum = 0f
                        for (c in 0 until wav.channels) sum += samples[i * wav.channels + c]
                        sum / wav.channels
                    }
                    val at16 = Resampler.resample(mono, wav.rate, SpeechMarking.RATE)
                    val mark16 = marker.watermarkOf(at16)
                    val mark = Resampler.resample(mark16, SpeechMarking.RATE, wav.rate, count)
                    for (i in 0 until count) {
                        for (c in 0 until wav.channels) {
                            val at = i * wav.channels + c
                            samples[at] = (samples[at] + mark[i]).coerceIn(-1f, 1f)
                        }
                    }
                    ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(samples)
                    target.write(bytes, 0, length)
                    frame += count
                    onProgress(frame.toFloat() / wav.frames.coerceAtLeast(1L))
                }
            }
        }
    }

    private companion object {
        /** Short enough for the progress to move every second or so. */
        const val PIECE_SECONDS = 10
    }
}

/** A 32-bit float WAV as FFmpeg writes it (pcm_f32le): read its header, write one. */
internal object FloatWav {

    class Info(val channels: Int, val rate: Int, val dataOffset: Long, val frames: Long)

    /** The header of [file]: its format chunk and where its samples are. */
    fun read(file: RandomAccessFile): Info {
        val head = ByteArray(12)
        file.seek(0)
        file.readFully(head)
        if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") throw IOException("Not a WAV")
        var at = 12L
        var channels = 0
        var rate = 0
        var bits = 0
        val chunk = ByteArray(8)
        while (at + 8 <= file.length()) {
            file.seek(at)
            file.readFully(chunk)
            val id = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(16)
                    file.readFully(fmt)
                    val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    channels = b.getShort(2).toInt()
                    rate = b.getInt(4)
                    bits = b.getShort(14).toInt()
                }
                "data" -> {
                    if (channels <= 0 || rate <= 0 || bits != 32) throw IOException("Not a 32-bit float WAV")
                    // FFmpeg writing to a file it can seek in fills the size in; else the rest of the file.
                    val bytes = if (size == 0L || size == 0xFFFFFFFFL) file.length() - (at + 8) else minOf(size, file.length() - (at + 8))
                    return Info(channels, rate, at + 8, bytes / (channels * 4L))
                }
            }
            at += 8 + size + (size and 1L)
        }
        throw IOException("No samples in the WAV")
    }

    /** A float WAV's header for [frames] frames of [channels] at [rate], at the start of [file]. */
    fun writeHeader(file: RandomAccessFile, channels: Int, rate: Int, frames: Long) {
        val data = frames * channels * 4
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt((36 + data).coerceAtMost(0xFFFFFFFFL).toInt())
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(3)
        b.putShort(channels.toShort())
        b.putInt(rate)
        b.putInt(rate * channels * 4)
        b.putShort((channels * 4).toShort())
        b.putShort(32)
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(data.coerceAtMost(0xFFFFFFFFL).toInt())
        file.seek(0)
        file.write(b.array())
    }
}
