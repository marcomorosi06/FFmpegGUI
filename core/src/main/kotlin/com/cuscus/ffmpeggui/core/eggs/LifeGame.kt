package com.cuscus.ffmpeggui.core.eggs

import com.cuscus.ffmpeggui.core.command.FfmpegCommand

/**
 * Conway's Game of Life as FFmpeg plays it: its own `life` source (libavfilter's
 * vsrc_life) computes the generations, this only asks for them and reads them
 * back. The About page's hidden game (seven taps on FFmpeg's version) is drawn
 * from these frames: nothing here computes a generation itself.
 *
 * A run asks for [generations] frames of a [columns] x [rows] grid, either
 * filled at random ([seed], [ratio] of it alive) or carried on from a grid
 * written as text ([gridText], read back by FFmpeg with the source's `filename`
 * option), so that the game goes on from where it was, or from a cell the
 * finger woke. The grid wraps at its edges (`stitch`). Frames come back as raw
 * grey bytes, one per cell, 255 alive and 0 dead ([frames]). Pure, tested.
 */
object LifeGame {

    /**
     * The command for one run: [generations] frames into [output] (raw grey
     * bytes). [startFile] null fills the grid at random; otherwise the run
     * starts from that file's grid, its first frame being that grid itself.
     */
    fun command(
        columns: Int,
        rows: Int,
        generations: Int,
        output: String,
        seed: Int = 0,
        ratio: Float = DEFAULT_RATIO,
        startFile: String? = null,
    ): FfmpegCommand {
        require(columns > 0 && rows > 0 && generations > 0) { "A grid and at least one generation" }
        val source = buildString {
            append("life=size=").append(columns).append('x').append(rows)
            append(":rate=").append(RATE)
            append(":stitch=1")
            if (startFile != null) {
                append(":filename=").append(escape(startFile))
            } else {
                append(":random_seed=").append(seed.toLong() and 0xFFFFFFFFL)
                append(":ratio=").append(formatRatio(ratio))
            }
        }
        return FfmpegCommand(
            listOf(
                "-hide_banner", "-nostdin", "-y",
                "-f", "lavfi", "-i", source,
                "-frames:v", generations.toString(),
                "-vf", "format=gray",
                "-f", "rawvideo",
                output,
            ),
        )
    }

    /** [cells] (row by row, true alive) as the text the `life` source reads: 'O' alive, ' ' dead, a line per row. */
    fun gridText(cells: BooleanArray, columns: Int, rows: Int): String {
        require(cells.size == columns * rows) { "One cell per place" }
        return buildString(rows * (columns + 1)) {
            for (y in 0 until rows) {
                for (x in 0 until columns) append(if (cells[y * columns + x]) 'O' else ' ')
                append('\n')
            }
        }
    }

    /** The generations in [bytes] (raw grey, one byte per cell): a cell is alive above half. */
    fun frames(bytes: ByteArray, columns: Int, rows: Int): List<BooleanArray> {
        val size = columns * rows
        if (size <= 0) return emptyList()
        return (0 until bytes.size / size).map { f ->
            BooleanArray(size) { i -> (bytes[f * size + i].toInt() and 0xFF) >= 128 }
        }
    }

    /**
     * True when [frames] ends settled: everything dead, or the last generations
     * repeating with a period of one or two (still lifes and blinkers), for at
     * least [SETTLED_RUN] frames. A settled game is shuffled again.
     */
    fun isSettled(frames: List<BooleanArray>): Boolean {
        val last = frames.lastOrNull() ?: return false
        if (last.none { it }) return true
        if (frames.size < SETTLED_RUN + 2) return false
        val tail = frames.takeLast(SETTLED_RUN + 2)
        return (2 until tail.size).all { tail[it].contentEquals(tail[it - 2]) }
    }

    /** How many cells of [cells] are alive. */
    fun alive(cells: BooleanArray): Int = cells.count { it }

    /** A filter argument written so that ':' and '\' in a path do not end it. */
    private fun escape(path: String): String = path.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")

    private fun formatRatio(ratio: Float): String {
        val r = ratio.coerceIn(0.01f, 0.99f)
        return ((r * 1000).toInt() / 1000.0).toString()
    }

    /** Generations per second the source is told to make (only a label in raw frames: they are read as fast as they come). */
    const val RATE = 8

    /** About a third of the grid alive at first: a lively start that does not choke. */
    const val DEFAULT_RATIO = 0.32f

    /** Frames that must repeat before the game counts as settled. */
    const val SETTLED_RUN = 6
}
