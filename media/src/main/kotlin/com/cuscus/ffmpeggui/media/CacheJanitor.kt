package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.cuscus.ffmpeggui.core.engine.ScratchStorage
import java.io.File

/**
 * Owns everything the app writes to its cache directories and makes sure it
 * goes away once it is no longer needed:
 *
 *  - camera recordings live in `recordings/` and are deleted as soon as the
 *    project that uses them is closed ([discard]);
 *  - stills (pictures added as clips, freeze frames, see [AndroidStillMaker]) and
 *    cover pictures of audio files (see [AndroidCoverArtist])
 *    live in `stills/` and go the same way;
 *  - working copies made for a conversion live in `staging/` and are deleted by
 *    [CacheSourceStager] at the end of every run;
 *  - [sweep] removes whatever a killed process or an older version of the app
 *    left behind.
 *
 * Exported files are not cache and are never touched here.
 */
class CacheJanitor(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScratchStorage {

    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.provider"

    /** External cache when available (the camera app can write there), internal otherwise. */
    private val cacheRoot: File
        get() = appContext.externalCacheDir ?: appContext.cacheDir

    private val recordingsDirectory: File
        get() = File(cacheRoot, RECORDINGS)

    override fun newRecording(): String {
        val directory = recordingsDirectory.apply { mkdirs() }
        val file = File(directory, "camera_${clock()}.mp4")
        return FileProvider.getUriForFile(appContext, authority, file).toString()
    }

    /** A new file for a still, in the internal cache, and its content URI. */
    fun newStill(): Pair<File, String> {
        val directory = File(appContext.cacheDir, STILLS).apply { mkdirs() }
        val file = File(directory, "still_${clock()}_${System.nanoTime() % 100_000}.png")
        return file to FileProvider.getUriForFile(appContext, authority, file).toString()
    }

    /** A new file for a cover picture (see [AndroidCoverArtist]), next to the stills, and its content URI. */
    fun newCover(): Pair<File, String> {
        val directory = File(appContext.cacheDir, STILLS).apply { mkdirs() }
        val file = File(directory, "cover_${clock()}_${System.nanoTime() % 100_000}.jpg")
        return file to FileProvider.getUriForFile(appContext, authority, file).toString()
    }

    override fun discard(sourceUri: String) {
        val (directory, name) = ownedFile(sourceUri) ?: return
        listOfNotNull(appContext.externalCacheDir, appContext.cacheDir)
            .map { File(File(it, directory), name) }
            .filter { it.isFile }
            .forEach { if (!it.delete()) Log.w(TAG, "Could not delete $name") }
    }

    override fun sweep() {
        try {
            val now = clock()
            // No conversion runs at start-up, so every working directory is an orphan.
            File(appContext.cacheDir, STAGING).listFiles()?.forEach { it.deleteRecursively() }

            // Recordings of projects that were never closed. A very recent one is kept: the
            // process may have been restarted while the camera app was still writing it.
            listOfNotNull(appContext.externalCacheDir, appContext.cacheDir).forEach { root ->
                File(root, RECORDINGS).listFiles()
                    ?.filter { now - it.lastModified() > RECENT_RECORDING_MS }
                    ?.forEach { it.deleteRecursively() }
            }

            // Stills of projects that were never closed: no project survives a restart.
            File(appContext.cacheDir, STILLS).listFiles()?.forEach { it.deleteRecursively() }

            // Older versions copied every source straight into the cache root under a
            // time-stamped name ("video_1712345678901.mp4", "concat_...txt", "camera_...mp4")
            // and did not always remove them.
            listOfNotNull(appContext.externalCacheDir, appContext.cacheDir).forEach { root ->
                root.listFiles()
                    ?.filter { it.isFile && LEGACY_NAME.matches(it.name) }
                    ?.forEach { it.delete() }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cache sweep skipped: ${e.message}")
        }
    }

    /**
     * Directory and file name of the app-made file behind [sourceUri] (a recording or
     * a still), or null when the URI is not one of ours.
     */
    private fun ownedFile(sourceUri: String): Pair<String, String>? {
        val uri = Uri.parse(sourceUri)
        if (uri.scheme != "content" || uri.authority != authority) return null
        val segments = uri.pathSegments
        val name = segments.lastOrNull() ?: return null
        // Only a plain file name directly inside one of our directories qualifies.
        if (name.contains('/') || name.startsWith(".")) return null
        val directory = segments.getOrNull(segments.size - 2)
        if (directory != RECORDINGS && directory != STILLS) return null
        return directory to name
    }

    private companion object {
        const val TAG = "CacheJanitor"
        const val STAGING = "staging"
        const val RECORDINGS = "recordings"
        const val STILLS = "stills"
        const val RECENT_RECORDING_MS = 10 * 60 * 1000L

        /** "<name>_<13-digit epoch millis>.<ext>", the naming used by earlier versions. */
        val LEGACY_NAME = Regex("""^.+_\d{12,14}\.[A-Za-z0-9]{1,10}$""")
    }
}
