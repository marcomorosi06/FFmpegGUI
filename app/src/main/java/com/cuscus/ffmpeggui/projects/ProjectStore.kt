package com.cuscus.ffmpeggui.projects

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.cuscus.ffmpeggui.ConversionSettings
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.JsonText
import com.cuscus.ffmpeggui.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

/** What the home screen and the settings show about a recent project. */
data class ProjectCard(
    val id: String,
    val name: String,
    val format: com.cuscus.ffmpeggui.core.model.OutputFormat,
    val editedAtMs: Long,
    /** Absolute path of its small preview picture, or null when it has none. */
    val thumbnail: String?,
    /** Changes whenever the preview picture is made again, so that it is reloaded. */
    val thumbnailStamp: Long,
    val clips: Int,
    val durationMs: Long,
    /** Files it uses that can no longer be read (moved, deleted, access withdrawn). */
    val missingFiles: Int,
    /** Files copied into it (recordings, stills, files other apps only lent). */
    val copiedFiles: Int,
    /** Everything it takes on the phone. */
    val bytes: Long,
    /** The last file exported from it, when that file still exists. */
    val lastExport: String?,
)

/**
 * The recent projects, in the app's private storage (`files/projects/<id>/`):
 * nothing else on the phone can read them, they are not backed up (the app
 * allows no backup) and they never leave the device (the app has no network
 * access). Each project is a folder with:
 *
 *  - `project.json`: the editor's state in readable JSON (see [ProjectJson]);
 *  - `thumb.jpg`: a small picture of its first frame, for the home screen;
 *  - `media/`: copies of the files the project could not otherwise open again
 *    later: camera recordings and stills (made in the cache, which is emptied),
 *    and files another app only lent for a moment (a file opened from a share).
 *
 * Files picked with the system picker are not copied: the project keeps the
 * permission to read them (a persistable URI permission), and gives it back
 * when no project uses them any more.
 *
 * Every change runs on one background worker, in order, so a save, a deletion
 * and the clean-up after a project is closed never overlap.
 */
class ProjectStore(
    context: Context,
    private val settings: () -> AppSettings,
) {
    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver
    private val authority = "${appContext.packageName}.provider"
    private val root: File get() = File(appContext.filesDir, ROOT)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val _cards = MutableStateFlow<List<ProjectCard>?>(null)

    /** The projects, most recently edited first; null until read once. */
    val cards: StateFlow<List<ProjectCard>?> = _cards.asStateFlow()

    /** Copies made during this run: project id to (original URI to the copy). Each file is copied once. */
    private val copies = HashMap<String, HashMap<Uri, Uri>>()

    /** What the preview picture of each project was made from, so it is only made again when that changes. */
    private val thumbnailKeys = HashMap<String, String>()

    /** Projects deleted by the user during this run: a late save must not bring them back. */
    private val deleted = HashSet<String>()

    /** The files the open editor uses: their read permission is kept even when no saved project needs it. */
    @Volatile
    var inUse: Set<Uri> = emptySet()

    init {
        scope.launch {
            for (job in work) {
                try {
                    job()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Project task failed", e)
                }
            }
        }
        enqueue { publish() }
    }

    private fun enqueue(job: suspend () -> Unit) {
        work.trySend(job)
    }

    /** A new project id. */
    fun newId(): String = UUID.randomUUID().toString()

    /** Reads the projects again (a file may have been deleted meanwhile). */
    fun refresh() = enqueue { publish() }

    /** Runs [block] once everything asked before it is done. */
    fun afterPending(block: () -> Unit) = enqueue { block() }

    /**
     * Saves [project] (unless projects are turned off, or the user deleted it):
     * copies what has to be copied, writes `project.json`, makes the preview
     * picture again when the first frame changed, then keeps no more than the
     * number of projects the settings allow.
     */
    fun save(project: SavedProject) = enqueue {
        if (!settings().saveProjects || project.id in deleted) return@enqueue
        write(project)
        keepWithinLimit(current = project.id)
        publish()
    }

    /** Remembers [path] as the last export of project [id]. */
    fun recordExport(id: String, path: String) = enqueue {
        if (!settings().saveProjects || id in deleted) return@enqueue
        val project = read(id) ?: return@enqueue
        writeJson(id, project.copy(lastExport = path))
        publish()
    }

    /** Keeps only the [limit] most recently edited projects (the limit was just lowered). */
    fun keepAtMost(limit: Int) = enqueue {
        keepWithinLimit(current = null, limit = limit)
        publish()
    }

    /** The project [id] as saved, or null. Call away from the main thread. */
    fun load(id: String): SavedProject? = read(id)

    /**
     * Removes the files of project [id] that it no longer uses (a clip deleted,
     * a sound replaced). Only done once the project is closed: until then an
     * undo may bring them back.
     */
    fun close(id: String) = enqueue {
        val project = read(id) ?: return@enqueue
        val used = ProjectJson.uris(project.settings).toSet()
        File(projectDir(id), MEDIA).listFiles()?.forEach { file ->
            if (fileUri(file) !in used) file.delete()
        }
        copies.remove(id)
        publish()
    }

    /** Deletes project [id] with its copies, and gives back the read permissions nothing else needs. */
    fun delete(id: String) = enqueue {
        deleted += id
        val project = read(id)
        projectDir(id).deleteRecursively()
        copies.remove(id)
        thumbnailKeys.remove(id)
        project?.let { releaseUnused(ProjectJson.uris(it.settings)) }
        publish()
    }

    /** Deletes every project (projects turned off, or "delete all"). */
    fun deleteAll() = enqueue {
        val ids = root.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        deleted += ids
        root.deleteRecursively()
        copies.clear()
        thumbnailKeys.clear()
        // Every permission the app kept was kept for a project.
        resolver.persistedUriPermissions.map { it.uri }.filter { it !in inUse }.forEach(::release)
        publish()
    }

    /** A copy of project [id], with copies of its files, edited now. */
    fun duplicate(id: String, copyName: (String) -> String) = enqueue {
        val project = read(id) ?: return@enqueue
        val newId = newId()
        val from = projectDir(id)
        val to = projectDir(newId)
        from.copyRecursively(to, overwrite = true)
        File(to, JSON).delete()
        val prefix = "/$ROOT/$id/"
        val moved = ProjectJson.mapUris(project.settings) { uri ->
            val path = uri.path
            if (uri.authority == authority && path != null && path.startsWith(prefix)) {
                fileUri(File(to, path.removePrefix(prefix)))
            } else {
                uri
            }
        }
        val now = System.currentTimeMillis()
        writeJson(newId, project.copy(id = newId, name = copyName(project.name), createdAtMs = now, editedAtMs = now, lastExport = null, settings = moved))
        keepWithinLimit(current = newId)
        publish()
    }

    // -----------------------------------------------------------------
    // Writing
    // -----------------------------------------------------------------

    private fun write(project: SavedProject) {
        val dir = projectDir(project.id).apply { mkdirs() }
        val media = File(dir, MEDIA)
        val known = copies.getOrPut(project.id) { HashMap() }
        val kept = ProjectJson.mapUris(project.settings) { uri -> keep(project.id, uri, media, known) }
        val previous = read(project.id)
        writeJson(project.id, project.copy(createdAtMs = previous?.createdAtMs ?: project.createdAtMs, lastExport = project.lastExport ?: previous?.lastExport, settings = kept))
        updateThumbnail(project.id, kept)
    }

    /**
     * Where the project finds [uri] again next time: the URI itself when it can
     * be opened later (one of the project's own files, a file picked whose
     * permission is kept), or a copy in [media].
     */
    private fun keep(id: String, uri: Uri, media: File, known: HashMap<Uri, Uri>): Uri {
        known[uri]?.let { return it }
        val path = uri.path.orEmpty()
        val kept = when {
            // Already one of this project's files.
            uri.authority == authority && path.startsWith("/$ROOT/$id/") -> uri
            // The app's own files (camera recordings, stills, covers: the cache is emptied).
            uri.authority == authority || uri.scheme == "file" -> copyInto(uri, media) ?: uri
            uri.scheme == "content" -> if (holdPermission(uri)) uri else copyInto(uri, media) ?: uri
            else -> uri
        }
        known[uri] = kept
        return kept
    }

    /** True when the app may read [uri] after a restart: it already holds the permission, or could take it. */
    private fun holdPermission(uri: Uri): Boolean {
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return true
        return try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (e: SecurityException) {
            // Lent for a moment only (a share, another app's picker): it has to be copied.
            false
        }
    }

    /** A copy of [uri] in [media], as a URI of the app's own; null when it cannot be read or written. */
    private fun copyInto(uri: Uri, media: File): Uri? {
        media.mkdirs()
        val name = safeName(displayName(uri) ?: uri.lastPathSegment ?: "file")
        val target = File(media, "${UUID.randomUUID().toString().take(8)}_$name")
        val partial = File(media, ".${target.name}.part")
        return try {
            val input = resolver.openInputStream(uri) ?: return null
            input.use { source -> partial.outputStream().use { source.copyTo(it, BUFFER) } }
            if (!partial.renameTo(target)) throw IOException("Could not name ${target.name}")
            fileUri(target)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not copy a file into the project", e)
            partial.delete()
            null
        }
    }

    private fun writeJson(id: String, project: SavedProject) {
        val dir = projectDir(id).apply { mkdirs() }
        val partial = File(dir, ".$JSON.part")
        partial.writeText(JsonText.write(ProjectJson.write(project)) + "\n")
        val target = File(dir, JSON)
        if (!partial.renameTo(target)) {
            target.delete()
            partial.renameTo(target)
        }
    }

    private fun keepWithinLimit(current: String?, limit: Int = settings().projectsLimit) {
        val all = root.listFiles()?.filter { it.isDirectory }.orEmpty()
            .mapNotNull { dir -> read(dir.name)?.let { dir.name to it } }
            .sortedByDescending { it.second.editedAtMs }
        all.drop(limit.coerceAtLeast(1)).filter { it.first != current }.forEach { (id, project) ->
            projectDir(id).deleteRecursively()
            copies.remove(id)
            thumbnailKeys.remove(id)
            releaseUnused(ProjectJson.uris(project.settings))
        }
    }

    /** Gives back the read permission of each of [uris] that no saved project and not the editor uses. */
    private fun releaseUnused(uris: Collection<Uri>) {
        val stillUsed = root.listFiles()?.filter { it.isDirectory }.orEmpty()
            .mapNotNull { read(it.name) }
            .flatMap { ProjectJson.uris(it.settings) }
            .toSet() + inUse
        val held = resolver.persistedUriPermissions.map { it.uri }.toSet()
        uris.filter { it in held && it !in stillUsed }.forEach(::release)
    }

    private fun release(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission already gone")
        }
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    private fun read(id: String): SavedProject? {
        val file = File(projectDir(id), JSON)
        if (!file.isFile) return null
        return try {
            (JsonText.parse(file.readText()) as? Json.Obj)?.let(ProjectJson::project)?.takeIf { it.id == id }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Unreadable project $id")
            null
        }
    }

    private fun publish() {
        _cards.value = root.listFiles()?.filter { it.isDirectory }.orEmpty()
            .mapNotNull { dir -> read(dir.name)?.let { card(dir, it) } }
            .sortedByDescending { it.editedAtMs }
    }

    private fun card(dir: File, project: SavedProject): ProjectCard {
        val s = project.settings
        val thumb = File(dir, THUMB).takeIf { it.isFile }
        val playing = s.playingClips
        val durationMs = playing.sumOf { it.effects.playedMs(it.lengthMs) } / s.wholeEffects.effectiveSpeed
        return ProjectCard(
            id = project.id,
            name = project.name,
            format = project.format,
            editedAtMs = project.editedAtMs,
            thumbnail = thumb?.absolutePath,
            thumbnailStamp = thumb?.lastModified() ?: 0L,
            clips = s.timeline.clips.size,
            durationMs = durationMs.toLong(),
            missingFiles = ProjectJson.uris(s).count { !readable(it) },
            copiedFiles = File(dir, MEDIA).listFiles()?.count { !it.name.startsWith(".") } ?: 0,
            bytes = sizeOf(dir),
            lastExport = project.lastExport?.takeIf { File(it).isFile },
        )
    }

    private fun readable(uri: Uri): Boolean = try {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    // -----------------------------------------------------------------
    // Preview picture
    // -----------------------------------------------------------------

    /** Makes `thumb.jpg` again when what the first frame shows has changed. */
    private fun updateThumbnail(id: String, s: ConversionSettings) {
        val clip = s.playingClips.firstOrNull()
        val source = clip?.let { s.source(it.sourceId) }
        val key = listOf(source?.uri, clip?.startMs, clip?.effects?.look(), s.wholeEffects.look(), s.coverUri).joinToString("|")
        val target = File(projectDir(id), THUMB)
        if (thumbnailKeys[id] == key && target.isFile) return
        thumbnailKeys[id] = key
        val frame = (source?.let { frameOf(it, clip.startMs) } ?: s.coverUri?.let(::pictureOf))
        if (frame == null) {
            target.delete()
            return
        }
        val shown = frame.shaped(clip?.effects ?: Effects.NONE).shaped(s.wholeEffects)
        val partial = File(target.parentFile, ".$THUMB.part")
        try {
            partial.outputStream().use { shown.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it) }
            if (!partial.renameTo(target)) {
                target.delete()
                partial.renameTo(target)
            }
        } finally {
            shown.recycle()
        }
    }

    /** What changes the look of a frame, as a key. */
    private fun Effects.look(): String = "$rotation,$flipHorizontal,$flipVertical,$effectiveCrop"

    private fun frameOf(source: EditorSource, atMs: Long): Bitmap? {
        if (source.isStill) return pictureOf(source.uri)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, source.uri)
            val frame = retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.embeddedPicture?.let { decodeSampled(it) }
            frame?.scaledDown()
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Retriever release failed")
            }
        }
    }

    private fun pictureOf(uri: Uri): Bitmap? = try {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        bytes?.let { decodeSampled(it) }?.scaledDown()
    } catch (e: Exception) {
        null
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun Bitmap.scaledDown(): Bitmap {
        val side = maxOf(width, height)
        if (side <= THUMB_SIDE) return this
        val scale = THUMB_SIDE.toFloat() / side
        val scaled = Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
        if (scaled !== this) recycle()
        return scaled
    }

    /** The frame as [effects] shows it: mirrored, turned, then cropped, in the order of the export. */
    private fun Bitmap.shaped(effects: Effects): Bitmap {
        val matrix = Matrix()
        if (effects.flipHorizontal) matrix.postScale(-1f, 1f)
        if (effects.flipVertical) matrix.postScale(1f, -1f)
        val degrees = when (effects.rotation) {
            Rotation.NONE -> 0f
            Rotation.DEG_90 -> 90f
            Rotation.DEG_180 -> 180f
            Rotation.DEG_270 -> 270f
        }
        matrix.postRotate(degrees)
        var out = if (matrix.isIdentity) this else Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (out !== this) recycle()
        effects.effectiveCrop?.let { crop ->
            val left = (crop.left.coerceIn(0f, 1f) * out.width).toInt().coerceIn(0, out.width - 1)
            val top = (crop.top.coerceIn(0f, 1f) * out.height).toInt().coerceIn(0, out.height - 1)
            val w = (crop.width * out.width).toInt().coerceIn(1, out.width - left)
            val h = (crop.height * out.height).toInt().coerceIn(1, out.height - top)
            val cropped = Bitmap.createBitmap(out, left, top, w, h)
            if (cropped !== out) out.recycle()
            out = cropped
        }
        return out
    }

    // -----------------------------------------------------------------
    // Files
    // -----------------------------------------------------------------

    private fun projectDir(id: String): File = File(root, safeName(id))

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(appContext, authority, file)

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun sizeOf(file: File): Long = if (file.isDirectory) file.listFiles()?.sumOf { sizeOf(it) } ?: 0L else file.length()

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(MAX_NAME).ifEmpty { "file" }

    private companion object {
        const val TAG = "ProjectStore"
        const val ROOT = "projects"
        const val MEDIA = "media"
        const val JSON = "project.json"
        const val THUMB = "thumb.jpg"
        const val THUMB_SIDE = 512
        const val THUMB_QUALITY = 82
        const val BUFFER = 256 * 1024
        const val MAX_NAME = 80
    }
}
