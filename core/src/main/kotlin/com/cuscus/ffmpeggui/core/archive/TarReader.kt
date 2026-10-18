package com.cuscus.ffmpeggui.core.archive

import java.io.IOException
import java.io.InputStream

/**
 * Reads the files of a tar archive, one after the other, without seeking: the
 * archive can come straight out of a decompressor.
 *
 * Plain files only (folders, links and the rest are skipped); long names of
 * the GNU and PAX kinds are understood.
 */
class TarReader(private val input: InputStream) {

    /** A file of the archive: its [path] inside it and its [size] in bytes. */
    class Entry(val path: String, val size: Long) {
        /** The last part of [path]. */
        val name: String get() = path.trimEnd('/').substringAfterLast('/')
    }

    private val header = ByteArray(BLOCK)
    private var left = 0L
    private var padding = 0L
    private var finished = false

    /** The next file, or null at the end. What was left of the previous one is skipped. */
    fun next(): Entry? {
        skipRest()
        var longName: String? = null
        while (!finished) {
            if (!readBlock(header)) {
                finished = true
                return null
            }
            if (header.all { it.toInt() == 0 }) {
                finished = true
                return null
            }
            val size = octal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val name = longName ?: run {
                val prefix = text(header, 345, 155)
                val plain = text(header, 0, 100)
                if (prefix.isNotEmpty() && magic().startsWith("ustar")) "$prefix/$plain" else plain
            }
            longName = null
            when (type) {
                '0', '\u0000', '7' -> {
                    left = size
                    padding = (BLOCK - size % BLOCK) % BLOCK
                    return Entry(name, size)
                }
                'L' -> longName = String(readAll(size), Charsets.UTF_8).trimEnd('\u0000')
                'x' -> longName = paxPath(String(readAll(size), Charsets.UTF_8))
                else -> {
                    left = size
                    padding = (BLOCK - size % BLOCK) % BLOCK
                    skipRest()
                }
            }
        }
        return null
    }

    /** The content of the current file. Reading past its end gives -1. */
    val content: InputStream = object : InputStream() {
        override fun read(): Int {
            if (left <= 0) return -1
            val b = input.read()
            if (b < 0) throw IOException("tar archive ends inside a file")
            left--
            return b
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (left <= 0) return -1
            val n = input.read(buffer, offset, minOf(length.toLong(), left).toInt())
            if (n < 0) throw IOException("tar archive ends inside a file")
            left -= n
            return n
        }
    }

    private fun skipRest() {
        val buffer = ByteArray(BLOCK * 16)
        var toSkip = left + padding
        while (toSkip > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), toSkip).toInt())
            if (n < 0) throw IOException("tar archive ends inside a file")
            toSkip -= n
        }
        left = 0
        padding = 0
    }

    private fun readAll(size: Long): ByteArray {
        if (size > MAX_HEADER_DATA) throw IOException("tar header data too large")
        val data = ByteArray(size.toInt())
        var read = 0
        while (read < data.size) {
            val n = input.read(data, read, data.size - read)
            if (n < 0) throw IOException("tar archive ends inside a header")
            read += n
        }
        left = 0
        padding = (BLOCK - size % BLOCK) % BLOCK
        skipRest()
        return data
    }

    private fun readBlock(target: ByteArray): Boolean {
        var read = 0
        while (read < target.size) {
            val n = input.read(target, read, target.size - read)
            if (n < 0) {
                if (read == 0) return false
                throw IOException("tar archive ends inside a header")
            }
            read += n
        }
        return true
    }

    private fun magic(): String = text(header, 257, 6)

    private fun paxPath(records: String): String? =
        records.lineSequence().mapNotNull { line ->
            val record = line.substringAfter(' ', "")
            if (record.startsWith("path=")) record.removePrefix("path=") else null
        }.firstOrNull()

    private fun text(bytes: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && bytes[end].toInt() != 0) end++
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    private fun octal(bytes: ByteArray, offset: Int, length: Int): Long {
        // Base-256 for large sizes (GNU).
        if (bytes[offset].toInt() and 0x80 != 0) {
            var value = 0L
            for (i in offset + 1 until offset + length) value = (value shl 8) or (bytes[i].toLong() and 0xFF)
            return value
        }
        var value = 0L
        var digits = false
        for (i in offset until offset + length) {
            val c = bytes[i].toInt().toChar()
            when {
                c in '0'..'7' -> {
                    value = value * 8 + (c - '0')
                    digits = true
                }
                c == ' ' && !digits -> Unit // leading spaces
                else -> break
            }
        }
        return value
    }

    private companion object {
        const val BLOCK = 512
        const val MAX_HEADER_DATA = 1L shl 20
    }
}
