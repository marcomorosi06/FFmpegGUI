package com.cuscus.ffmpeggui.core.archive

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Reads data compressed with bzip2 (the ".bz2" of a ".tar.bz2"), on every core.
 *
 * A bzip2 stream is a row of blocks that can be decoded each on its own, each
 * starting with the same 48-bit mark at any bit position. A reader thread finds
 * the marks and hands each block's bytes to a pool of decoders; this stream
 * gives out their results in order. Every block's CRC and every stream's are
 * checked, so a damaged file stops with an [IOException] instead of giving
 * wrong bytes. Concatenated streams (as parallel compressors write them) are
 * read one after the other. Randomised blocks (written by bzip2 0.9.0 only) are
 * refused.
 */
class Bzip2InputStream(
    private val input: InputStream,
    threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS),
) : InputStream() {

    /** What the reader thread finds, in order: a decoded block, or the end of a stream. */
    private sealed interface Item {
        class Block(val bytes: ByteArray, val length: Int, val crc: Int) : Item
        class StreamEnd(val crc: Int) : Item
        data object End : Item
    }

    private val pool: ExecutorService = Executors.newFixedThreadPool(threads) { task ->
        Thread(task, "bzip2").apply { isDaemon = true }
    }
    private val queue = ArrayBlockingQueue<Future<Item>>(threads * 2 + 2)
    private val reader: Thread

    @Volatile private var closed = false

    private var current: Item.Block? = null
    private var offset = 0
    private var streamCrc = 0
    private var finished = false

    init {
        // The first stream's header is read here, so that a file that is not bzip2 fails at once.
        val header = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(header, read, 4 - read)
            if (n < 0) break
            read += n
        }
        if (read < 4 || header[0] != 'B'.code.toByte() || header[1] != 'Z'.code.toByte() || header[2] != 'h'.code.toByte() ||
            header[3] !in '1'.code.toByte()..'9'.code.toByte()
        ) {
            pool.shutdownNow()
            throw IOException("Not a bzip2 stream")
        }
        reader = Thread({ readBlocks() }, "bzip2-reader").apply { isDaemon = true; start() }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            val block = current
            if (block != null && this.offset < block.length) {
                val n = minOf(length, block.length - this.offset)
                System.arraycopy(block.bytes, this.offset, buffer, offset, n)
                this.offset += n
                return n
            }
            if (finished) return -1
            when (val item = next()) {
                is Item.Block -> {
                    streamCrc = ((streamCrc shl 1) or (streamCrc ushr 31)) xor item.crc
                    current = item
                    this.offset = 0
                }
                is Item.StreamEnd -> {
                    if (item.crc != streamCrc) throw IOException("bzip2 stream CRC mismatch")
                    streamCrc = 0
                }
                Item.End -> {
                    finished = true
                    current = null
                    return -1
                }
            }
        }
    }

    override fun close() {
        closed = true
        reader.interrupt()
        pool.shutdownNow()
        queue.clear()
        input.close()
    }

    private fun next(): Item {
        val future = try {
            queue.take()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted")
        }
        return try {
            future.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            val cause = e.cause
            throw if (cause is IOException) cause else IOException("bzip2 data is damaged", cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted")
        }
    }

    // ---------------------------------------------------------------------
    // The reader thread: finds the blocks
    // ---------------------------------------------------------------------

    private fun put(future: Future<Item>) {
        while (!closed) {
            if (queue.offer(future, 100, TimeUnit.MILLISECONDS)) return
        }
        throw InterruptedException()
    }

    private fun fail(e: Throwable) {
        val failed = CompletableFuture<Item>()
        failed.completeExceptionally(e)
        try {
            put(failed)
        } catch (ignored: InterruptedException) {
        }
    }

    /**
     * Reads the compressed bytes, keeping those from the start of the current
     * block on, and looks at every bit position for the block mark and the end
     * mark. The bytes of a block (from its mark to the next one) go to a decoder.
     */
    private fun readBlocks() {
        try {
            var data = ByteArray(INITIAL_WINDOW)
            var size = 0
            // Absolute byte position of data[0], and the bit where the open block starts (-1: none).
            var base = 4L // data[0] is this byte of the file: the header is read already
            var blockStart = -1L
            var window = 0L
            var absolute = 4L // the stream header is read
            var pendingEnd = -1L // an end mark whose CRC is still to come
            val chunk = ByteArray(READ_CHUNK)
            fun keepFrom(bytePosition: Long) {
                val drop = (bytePosition - base).toInt().coerceIn(0, size)
                if (drop > 0) {
                    System.arraycopy(data, drop, data, 0, size - drop)
                    size -= drop
                    base += drop
                }
            }
            fun slice(fromBit: Long, toBit: Long): Pair<ByteArray, Int> {
                val first = (fromBit ushr 3) - base
                val last = ((toBit + 7) ushr 3) - base
                return data.copyOfRange(first.toInt(), minOf(last.toInt(), size)) to (fromBit and 7L).toInt()
            }
            fun handleEndCrc() {
                // The 32 bits right after an end mark are the stream's CRC.
                val from = pendingEnd + 48
                if ((from + 32) <= (base + size) * 8) {
                    var crc = 0L
                    for (bit in 0 until 32) {
                        val position = from + bit
                        val byte = data[((position ushr 3) - base).toInt()].toInt()
                        crc = (crc shl 1) or ((byte ushr (7 - (position and 7L).toInt())) and 1).toLong()
                    }
                    val stored = crc.toInt()
                    put(CompletableFuture.completedFuture(Item.StreamEnd(stored)))
                    pendingEnd = -1L
                }
            }
            while (!closed) {
                val n = input.read(chunk)
                if (n < 0) break
                if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
                System.arraycopy(chunk, 0, data, size, n)
                val firstNew = size
                size += n
                for (i in firstNew until size) {
                    window = (window shl 8) or (data[i].toLong() and 0xFF)
                    absolute++
                    // Byte k-3 lies inside a mark that starts in byte k-6, whatever its bit.
                    if (absolute < 7) continue
                    val probe = ((window ushr 24) and 0xFF).toInt()
                    val candidates = CANDIDATES[probe]
                    if (candidates == 0) continue
                    for (shift in 0 until 8) {
                        if (candidates and (0x101 shl shift) == 0) continue
                        val bits = (window ushr (8 - shift)) and MASK48
                        val isBlock = bits == BLOCK_MAGIC
                        val isEnd = bits == END_MAGIC
                        if (!isBlock && !isEnd) continue
                        val markBit = (absolute - 7) * 8 + shift
                        // A stream ended before this mark: its CRC comes first.
                        if (pendingEnd >= 0) handleEndCrc()
                        if (blockStart >= 0) {
                            val (bytes, bitOffset) = slice(blockStart, markBit)
                            put(pool.submit(Callable<Item> { BlockDecoder.decode(bytes, bitOffset) }))
                        }
                        blockStart = if (isBlock) markBit else -1L
                        if (isEnd) pendingEnd = markBit
                    }
                }
                if (pendingEnd >= 0) handleEndCrc()
                // Only the bytes of the open block (or the last few, where a mark may begin) are kept.
                keepFrom(
                    when {
                        pendingEnd >= 0 -> pendingEnd ushr 3
                        blockStart >= 0 -> blockStart ushr 3
                        else -> base + maxOf(0, size - 8)
                    },
                )
            }
            if (pendingEnd >= 0) handleEndCrc()
            if (closed) return
            if (blockStart >= 0 || pendingEnd >= 0) throw IOException("bzip2 data ends too early")
            put(CompletableFuture.completedFuture(Item.End))
        } catch (e: InterruptedException) {
            // Closed.
        } catch (e: Throwable) {
            if (!closed) fail(e)
        } finally {
            pool.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // One block, on a decoder thread
    // ---------------------------------------------------------------------

    private class BlockDecoder(private val data: ByteArray, startBit: Int) {
        private var position = startBit

        /** The next [count] (at most 24) bits. */
        private fun bits(count: Int): Int {
            val value = peek24() ushr (24 - count)
            position += count
            return value
        }

        /** The next 24 bits without moving; zeros past the end. */
        private fun peek24(): Int {
            val index = position ushr 3
            val d = data
            val b0 = if (index < d.size) d[index].toInt() and 0xFF else 0
            val b1 = if (index + 1 < d.size) d[index + 1].toInt() and 0xFF else 0
            val b2 = if (index + 2 < d.size) d[index + 2].toInt() and 0xFF else 0
            val b3 = if (index + 3 < d.size) d[index + 3].toInt() and 0xFF else 0
            val word = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            return (word shl (position and 7)) ushr 8
        }

        private fun bits32(): Int = (bits(16) shl 16) or bits(16)

        fun decode(): Item.Block {
            if (position.toLong() + 48 > data.size * 8L) throw IOException("bzip2 block too short")
            position += 48 // the block mark
            val expectedCrc = bits32()
            if (bits(1) != 0) throw IOException("Randomised bzip2 blocks are not supported")
            val origin = bits(24)

            // The bytes used in the block.
            val used = IntArray(256)
            var usedCount = 0
            val ranges = bits(16)
            for (i in 0 until 16) {
                if (ranges and (0x8000 ushr i) != 0) {
                    val inRange = bits(16)
                    for (j in 0 until 16) {
                        if (inRange and (0x8000 ushr j) != 0) used[usedCount++] = i * 16 + j
                    }
                }
            }
            if (usedCount == 0) throw IOException("Empty bzip2 block")
            val alphaSize = usedCount + 2

            // Huffman tables and which one each group of 50 symbols uses.
            val groups = bits(3)
            if (groups !in 2..6) throw IOException("Bad bzip2 table count")
            val selectorCount = bits(15)
            if (selectorCount < 1) throw IOException("Bad bzip2 selector count")
            val mtfGroups = IntArray(groups) { it }
            val selectors = ByteArray(selectorCount)
            for (i in 0 until selectorCount) {
                var j = 0
                while (bits(1) == 1) {
                    j++
                    if (j >= groups) throw IOException("Bad bzip2 selector")
                }
                val chosen = mtfGroups[j]
                System.arraycopy(mtfGroups, 0, mtfGroups, 1, j)
                mtfGroups[0] = chosen
                selectors[i] = chosen.toByte()
            }
            val tables = Array(groups) {
                val lengths = IntArray(alphaSize)
                var length = bits(5)
                for (symbol in 0 until alphaSize) {
                    while (true) {
                        if (length !in 1..MAX_CODE) throw IOException("Bad bzip2 code length")
                        if (bits(1) == 0) break
                        if (bits(1) == 0) length++ else length--
                    }
                    lengths[symbol] = length
                }
                Huffman(lengths)
            }

            // Symbols -> move-to-front -> the block's bytes, counted for the inverse transform.
            val mtf = IntArray(256) { it }
            val counts = IntArray(256)
            val buffers = BUFFERS.get()
            val block = buffers.block
            var length = 0
            val endOfBlock = alphaSize - 1
            var groupIndex = 0
            var groupLeft = 0
            var table = tables[0]
            var run = 0
            var runWeight = 1
            while (true) {
                if (groupLeft == 0) {
                    if (groupIndex >= selectorCount) throw IOException("Bad bzip2 selectors")
                    table = tables[selectors[groupIndex++].toInt()]
                    groupLeft = GROUP_SIZE
                }
                groupLeft--
                // Table lookup for the short codes, the canonical walk for the long ones.
                val window = peek24()
                val entry = table.lookup[window ushr (24 - LOOKUP_BITS)]
                val symbol: Int
                if (entry != 0) {
                    position += entry and 0x1F
                    symbol = entry ushr 5
                } else {
                    var len = LOOKUP_BITS + 1
                    var found = -1
                    while (len <= table.maxLength) {
                        val code = window ushr (24 - len)
                        if (code <= table.limit[len]) {
                            val index = code + table.base[len]
                            if (index !in table.permutation.indices) throw IOException("Bad bzip2 code")
                            found = table.permutation[index]
                            break
                        }
                        len++
                    }
                    if (found < 0) throw IOException("Bad bzip2 code")
                    position += len
                    symbol = found
                }
                if (symbol <= RUN_B) {
                    run += if (symbol == RUN_A) runWeight else runWeight * 2
                    runWeight = runWeight shl 1
                    if (run > BLOCK_MAX) throw IOException("bzip2 run too long")
                    continue
                }
                if (run > 0) {
                    val value = used[mtf[0]]
                    if (length + run > BLOCK_MAX) throw IOException("bzip2 block too long")
                    counts[value] += run
                    java.util.Arrays.fill(block, length, length + run, value.toByte())
                    length += run
                    run = 0
                    runWeight = 1
                }
                if (symbol == endOfBlock) break
                // Symbol n (2..alphaSize-2) moves entry n-1 to the front.
                val index = symbol - 1
                val chosen = mtf[index]
                System.arraycopy(mtf, 0, mtf, 1, index)
                mtf[0] = chosen
                val value = used[chosen]
                if (length >= BLOCK_MAX) throw IOException("bzip2 block too long")
                counts[value]++
                block[length++] = value.toByte()
            }
            if (origin >= length) throw IOException("Bad bzip2 origin")

            // Inverse Burrows-Wheeler transform: each entry keeps its byte (low 8 bits)
            // and the position of the entry that follows it in the output (the rest).
            val tt = buffers.links
            val starts = IntArray(256)
            var sum = 0
            for (i in 0 until 256) {
                starts[i] = sum
                sum += counts[i]
            }
            for (i in 0 until length) {
                val value = block[i].toInt() and 0xFF
                tt[starts[value]++] = i shl 8
            }
            for (i in 0 until length) tt[i] = tt[i] or (block[i].toInt() and 0xFF)

            // The output, with the first run-length coding undone, and its CRC.
            var out = ByteArray(length + length / 4 + 16)
            var written = 0
            var crc = -1
            val crcTable = CRC_TABLE
            var at = tt[origin] ushr 8
            var last = -1
            var same = 0
            var produced = 0
            while (produced < length) {
                val link = tt[at]
                val value = link and 0xFF
                at = link ushr 8
                produced++
                if (same == 4) {
                    // After four equal bytes comes how many more of them follow.
                    if (written + value > out.size) out = out.copyOf(maxOf(out.size * 2, written + value + 16))
                    for (r in 0 until value) {
                        out[written++] = last.toByte()
                        crc = (crc shl 8) xor crcTable[((crc ushr 24) xor last) and 0xFF]
                    }
                    same = 0
                    last = -1
                    continue
                }
                if (value == last) same++ else { same = 1; last = value }
                if (written >= out.size) out = out.copyOf(out.size * 2)
                out[written++] = value.toByte()
                crc = (crc shl 8) xor crcTable[((crc ushr 24) xor value) and 0xFF]
            }
            val actual = crc.inv()
            if (actual != expectedCrc) throw IOException("bzip2 block CRC mismatch")
            return Item.Block(out, written, actual)
        }

        companion object {
            fun decode(data: ByteArray, startBit: Int): Item = BlockDecoder(data, startBit).decode()
        }
    }

    /** Canonical Huffman decoding of one table: a lookup for codes up to [LOOKUP_BITS], limits for the rest. */
    private class Huffman(lengths: IntArray) {
        val maxLength = lengths.max()
        private val minLength = lengths.min()
        val limit = IntArray(MAX_CODE + 2) { -1 }
        val base = IntArray(MAX_CODE + 2)
        val permutation = IntArray(lengths.size)

        /** For every [LOOKUP_BITS]-bit prefix: symbol shl 5 or code length, 0 when the code is longer. */
        val lookup = IntArray(1 shl LOOKUP_BITS)

        init {
            var p = 0
            for (len in minLength..maxLength) {
                for (symbol in lengths.indices) if (lengths[symbol] == len) permutation[p++] = symbol
            }
            val countOf = IntArray(MAX_CODE + 2)
            for (len in lengths) countOf[len]++
            var code = 0
            var index = 0
            for (len in minLength..maxLength) {
                base[len] = index - code
                // Codes of this length, in the order of the permutation.
                for (k in 0 until countOf[len]) {
                    val symbol = permutation[index + k]
                    val value = code + k
                    if (len <= LOOKUP_BITS) {
                        val from = value shl (LOOKUP_BITS - len)
                        val to = (value + 1) shl (LOOKUP_BITS - len)
                        val entry = (symbol shl 5) or len
                        for (e in from until to) lookup[e] = entry
                    }
                }
                code += countOf[len]
                index += countOf[len]
                limit[len] = code - 1
                code = code shl 1
            }
        }
    }

    /** The arrays a decoder thread works in, kept from block to block. */
    private class Buffers {
        val block = ByteArray(BLOCK_MAX)
        val links = IntArray(BLOCK_MAX)
    }

    private companion object {
        val BUFFERS: ThreadLocal<Buffers> = ThreadLocal.withInitial { Buffers() }

        const val MAX_THREADS = 8
        const val INITIAL_WINDOW = 4 shl 20
        const val READ_CHUNK = 256 * 1024
        const val BLOCK_MAX = 900_000
        const val GROUP_SIZE = 50
        const val RUN_A = 0
        const val RUN_B = 1
        const val MAX_CODE = 20
        const val LOOKUP_BITS = 12

        const val MASK48 = 0xFFFFFFFFFFFFL
        const val BLOCK_MAGIC = 0x314159265359L
        const val END_MAGIC = 0x177245385090L

        /**
         * For every value of the byte three before the last one read: the bit
         * shifts at which it could belong to a block mark (low byte) or an end
         * mark (high byte) starting six bytes back.
         */
        val CANDIDATES = IntArray(256).also { table ->
            for (shift in 0 until 8) {
                val block = ((BLOCK_MAGIC shl (8 - shift)) ushr 24) and 0xFF
                val end = ((END_MAGIC shl (8 - shift)) ushr 24) and 0xFF
                table[block.toInt()] = table[block.toInt()] or (1 shl shift)
                table[end.toInt()] = table[end.toInt()] or (0x100 shl shift)
            }
        }

        /** The CRC-32 of bzip2 (MSB first, polynomial 0x04C11DB7). */
        val CRC_TABLE = IntArray(256) { n ->
            var c = n shl 24
            repeat(8) { c = if (c and Int.MIN_VALUE != 0) (c shl 1) xor 0x04C11DB7 else c shl 1 }
            c
        }
    }
}
