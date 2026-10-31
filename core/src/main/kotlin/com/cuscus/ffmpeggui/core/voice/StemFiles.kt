package com.cuscus.ffmpeggui.core.voice

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A sound written by FFmpeg as raw interleaved 16-bit stereo samples (little
 * endian, `-f s16le -ac 2`), read where asked: what [VoiceSeparation] reads.
 */
class PcmStereoFile(file: File) : VoiceSeparation.Source, AutoCloseable {

    private val raf = RandomAccessFile(file, "r")
    override val length: Long = raf.length() / BYTES_PER_FRAME
    private var bytes = ByteArray(0)

    override fun read(start: Long, into: FloatArray, count: Int) {
        java.util.Arrays.fill(into, 0, count * 2, 0f)
        val from = maxOf(start, 0L)
        val to = minOf(start + count, length)
        if (to <= from) return
        val frames = (to - from).toInt()
        val size = frames * BYTES_PER_FRAME
        if (bytes.size < size) bytes = ByteArray(size)
        raf.seek(from * BYTES_PER_FRAME)
        raf.readFully(bytes, 0, size)
        val shorts = ByteBuffer.wrap(bytes, 0, size).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var at = ((from - start) * 2).toInt()
        for (i in 0 until frames * 2) into[at++] = shorts.get(i) / 32768f
    }

    override fun close() = raf.close()

    companion object {
        const val BYTES_PER_FRAME = 4
    }
}

/**
 * A WAV file of 16-bit samples written as they come: [channels] interleaved
 * at [sampleRate]. Its sizes are filled in on [close].
 *
 * The header is WAVE_FORMAT_EXTENSIBLE, with the speakers named: without
 * them FFmpeg guesses four channels as "4.0" (front left, right, centre and
 * back centre), which its FLAC encoder does not take; named "quad" they are
 * what FLAC keeps for four channels.
 *
 * A [comment], when given, goes in a LIST INFO chunk before the samples
 * (ICMT, and ISFT for the software): FFmpeg reads it as the "comment" tag.
 */
class WavWriter(
    private val file: File,
    private val channels: Int,
    private val sampleRate: Int,
    comment: String? = null,
) : AutoCloseable {

    private val info: ByteArray = comment?.let { infoChunk(it) } ?: ByteArray(0)
    private val headerBytes = HEADER_BYTES + info.size

    private val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
    private var dataBytes = 0L
    private var buffer = ByteArray(0)

    init {
        out.write(header(0L))
    }

    /** Writes [frames] frames of [samples] (interleaved, -1..1; clipped beyond). */
    fun write(samples: FloatArray, frames: Int) {
        val size = frames * channels * 2
        if (buffer.size < size) buffer = ByteArray(size)
        var at = 0
        for (i in 0 until frames * channels) {
            val v = Math.round(samples[i] * 32767f).coerceIn(-32768, 32767)
            buffer[at++] = (v and 0xFF).toByte()
            buffer[at++] = ((v shr 8) and 0xFF).toByte()
        }
        out.write(buffer, 0, size)
        dataBytes += size
    }

    override fun close() {
        out.close()
        RandomAccessFile(file, "rw").use { it.write(header(dataBytes)) }
    }

    private fun header(data: Long): ByteArray {
        // Past 4 GB the sizes cannot be told: they are left at their largest (FFmpeg reads on).
        val dataSize = minOf(data, 0xFFFFFFFFL - (headerBytes - 8)).toInt()
        val b = ByteBuffer.allocate(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(headerBytes - 8 + dataSize)
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(40)
        b.putShort(EXTENSIBLE.toShort()).putShort(channels.toShort()).putInt(sampleRate)
        b.putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        // The extension: 22 bytes, the bits really used, the speakers, PCM.
        b.putShort(22).putShort(16).putInt(speakers(channels))
        b.put(PCM_GUID)
        b.put(info)
        b.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataSize)
        return b.array()
    }

    companion object {
        const val HEADER_BYTES = 68

        private const val EXTENSIBLE = 0xFFFE

        /** A LIST INFO chunk with [comment] (ICMT) and the software (ISFT), ASCII, each NUL-ended and even. */
        fun infoChunk(comment: String): ByteArray {
            fun sub(id: String, text: String): ByteArray {
                val bytes = text.filter { it.code in 0x20..0x7E }.toByteArray(Charsets.US_ASCII) + 0
                val padded = if (bytes.size % 2 == 1) bytes + 0 else bytes
                val b = ByteBuffer.allocate(8 + padded.size).order(ByteOrder.LITTLE_ENDIAN)
                b.put(id.toByteArray(Charsets.US_ASCII)).putInt(bytes.size).put(padded)
                return b.array()
            }
            val body = "INFO".toByteArray(Charsets.US_ASCII) + sub("ICMT", comment) + sub("ISFT", "FFmpegGUI")
            val b = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            b.put("LIST".toByteArray(Charsets.US_ASCII)).putInt(body.size).put(body)
            return b.array()
        }

        /** KSDATAFORMAT_SUBTYPE_PCM, 00000001-0000-0010-8000-00AA00389B71, as stored. */
        private val PCM_GUID = byteArrayOf(
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00,
            0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71,
        )

        /** The speakers of [channels] channels: mono, stereo or quad (left, right, back left, back right). */
        fun speakers(channels: Int): Int = when (channels) {
            1 -> 0x4
            2 -> 0x3
            4 -> 0x33
            else -> 0
        }
    }
}

/**
 * The file a separation leaves: four channels, the voice's left and right
 * then the rest's left and right, so that one player or one FFmpeg input
 * gives both and any mix of them is a matter of two gains.
 */
object VoiceStem {
    const val CHANNELS = 4

    /** Interleaves the [count] stereo frames of [voice] and [rest] into [into] (four channels). */
    fun interleave(voice: FloatArray, rest: FloatArray, count: Int, into: FloatArray) {
        var at = 0
        for (i in 0 until count) {
            into[at++] = voice[i * 2]
            into[at++] = voice[i * 2 + 1]
            into[at++] = rest[i * 2]
            into[at++] = rest[i * 2 + 1]
        }
    }

    /**
     * The FFmpeg filter that makes a stem file stereo again, the voice at
     * [voice] and the rest at [rest] (linear gains, 1 = as recorded).
     */
    fun panFilter(voice: Float, rest: Float): String {
        fun gain(v: Float) = String.format(java.util.Locale.ROOT, "%.4f", v.coerceIn(0f, MAX_LEVEL))
        return "pan=stereo|c0=${gain(voice)}*c0+${gain(rest)}*c2|c1=${gain(voice)}*c1+${gain(rest)}*c3"
    }

    /** Mixes one four-channel frame at [at] of [stem] down to stereo, into [out] at [to]. */
    fun mixFrame(stem: FloatArray, at: Int, voice: Float, rest: Float, out: FloatArray, to: Int) {
        out[to] = stem[at] * voice + stem[at + 2] * rest
        out[to + 1] = stem[at + 1] * voice + stem[at + 3] * rest
    }

    /** Loudest a level goes: twice as loud. */
    const val MAX_LEVEL = 2f
}
