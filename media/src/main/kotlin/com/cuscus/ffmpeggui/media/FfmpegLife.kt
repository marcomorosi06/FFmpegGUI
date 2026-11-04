package com.cuscus.ffmpeggui.media

import android.content.Context
import android.util.Log
import com.cuscus.ffmpeggui.core.eggs.LifeGame
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The generations of Conway's Game of Life, computed by FFmpeg's own `life`
 * source (see LifeGame): the About page's hidden game. A run takes a fraction
 * of a second; its files go into a folder of the cache of their own, deleted
 * as soon as they are read (and by the CacheJanitor, should the app be killed
 * meanwhile).
 */
class FfmpegLife(context: Context, private val executor: FfmpegExecutor) {

    private val folder = File(context.applicationContext.cacheDir, DIRECTORY)

    /**
     * [generations] generations of a [columns] x [rows] grid: from [start] (its
     * first frame being [start] itself) or, null, from a grid filled at random by
     * [seed]. Null when FFmpeg could not make them.
     */
    suspend fun generations(columns: Int, rows: Int, generations: Int, seed: Int, start: BooleanArray? = null): List<BooleanArray>? =
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            val id = UUID.randomUUID().toString().take(8)
            val output = File(folder, "life-$id.gray")
            val grid = start?.let { cells -> File(folder, "life-$id.txt").also { it.writeText(LifeGame.gridText(cells, columns, rows)) } }
            try {
                val command = LifeGame.command(columns, rows, generations, output.absolutePath, seed = seed, startFile = grid?.absolutePath)
                val log = StringBuffer()
                when (val result = executor.execute(command) { log.append(it) }) {
                    ExecutionResult.Success -> LifeGame.frames(output.readBytes(), columns, rows).takeIf { it.isNotEmpty() }
                    else -> {
                        if (result != ExecutionResult.Cancelled) Log.w(TAG, "Life could not be played ($result): ${log.takeLast(LOG_TAIL)}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Life could not be played", e)
                null
            } finally {
                output.delete()
                grid?.delete()
            }
        }

    companion object {
        private const val TAG = "FfmpegLife"

        /** Inside the cache. */
        const val DIRECTORY = "life"

        private const val LOG_TAIL = 600
    }
}
