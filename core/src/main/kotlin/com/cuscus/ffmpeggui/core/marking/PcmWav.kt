package com.cuscus.ffmpeggui.core.marking

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a 16-bit PCM WAV (as WavWriter writes the dubs) into mono samples:
 * for a dub made before the invisible marks, which gets them before it is
 * exported. Pure, tested.
 */
object PcmWav {
    class Sound(val samples: FloatArray, val rate: Int)

    fun read(file: File): Sound = RandomAccessFile(file, "r").use { f ->
        val head = ByteArray(12)
        f.readFully(head)
        if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") throw IOException("Not a WAV")
        var channels = 0
        var rate = 0
        var bits = 0
        var position = 12L
        val chunk = ByteArray(8)
        while (position + 8 <= f.length()) {
            f.seek(position)
            f.readFully(chunk)
            val id = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(minOf(size, 40L).toInt())
                    f.readFully(fmt)
                    val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    val format = b.getShort(0).toInt() and 0xFFFF
                    if (format != 1 && format != 0xFFFE) throw IOException("Not PCM")
                    channels = b.getShort(2).toInt()
                    rate = b.getInt(4)
                    bits = b.getShort(14).toInt()
                }
                "data" -> {
                    if (channels <= 0 || rate <= 0 || bits != 16) throw IOException("Not 16-bit PCM")
                    val available = minOf(size, f.length() - position - 8)
                    val frames = (available / (2L * channels)).toInt()
                    val bytes = ByteArray(frames * 2 * channels)
                    f.readFully(bytes)
                    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val out = FloatArray(frames)
                    for (i in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += b.getShort((i * channels + c) * 2) / 32768f
                        out[i] = sum / channels
                    }
                    return Sound(out, rate)
                }
            }
            position += 8 + size + (size and 1)
        }
        throw IOException("No sound in the WAV")
    }
}
