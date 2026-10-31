package com.cuscus.ffmpeggui.svgedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.nodeCount
import com.cuscus.ffmpeggui.projects.ProjectCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/*
 * The SVG editor's drawings, kept like the recent projects so that they show
 * under "Continue editing" on the home screen: each in the app's private
 * storage (`files/drawings/<id>/`), never leaving the phone, with
 *
 *  - `drawing.svg`: the drawing as the editor writes it;
 *  - `name.txt`: its name;
 *  - `thumb.png`: a small picture of it, for the home screen.
 *
 * Its time is the SVG file's own. Written one at a time, off the main thread,
 * in the order asked.
 */
class SvgDrawingStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val root: File get() = File(appContext.filesDir, ROOT)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val work = Channel<() -> Unit>(Channel.UNLIMITED)
    private val deleted = HashSet<String>()

    private val _cards = MutableStateFlow<List<ProjectCard>>(emptyList())

    /** The drawings as cards of the recent projects, most recently edited first. */
    val cards: StateFlow<List<ProjectCard>> = _cards.asStateFlow()

    init {
        scope.launch {
            for (job in work) {
                try {
                    job()
                } catch (e: Exception) {
                    Log.w(TAG, "Drawing task failed", e)
                }
            }
        }
        enqueue { publish() }
    }

    private fun enqueue(job: () -> Unit) {
        work.trySend(job)
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun refresh() = enqueue { publish() }

    /** Keeps [doc] as the drawing [id] named [name] (its picture made again). */
    fun save(id: String, name: String, doc: SvgDocument) = enqueue {
        if (id in deleted) return@enqueue
        val dir = File(root, safe(id)).apply { mkdirs() }
        File(dir, NAME).writeText(name)
        // The picture first: the drawing's time is what the home screen shows.
        thumbnail(doc)?.let { bitmap ->
            File(dir, THUMB).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val tmp = File(dir, "$SVG.tmp")
        tmp.writeText(doc.write())
        tmp.renameTo(File(dir, SVG))
        publish()
    }

    /** The drawing [id] and its name; null when it cannot be read. */
    fun load(id: String): Pair<SvgDocument, String>? {
        val dir = File(root, safe(id))
        return runCatching {
            SvgDocument.parse(File(dir, SVG).readText()) to (File(dir, NAME).takeIf { it.isFile }?.readText() ?: "drawing")
        }.onFailure { Log.w(TAG, "Unreadable drawing $id", it) }.getOrNull()
    }

    fun delete(id: String) = enqueue {
        deleted += id
        File(root, safe(id)).deleteRecursively()
        publish()
    }

    /** A copy of the drawing [id], named as [copyName] says from its name. */
    fun duplicate(id: String, copyName: (String) -> String) = enqueue {
        val from = File(root, safe(id))
        if (!from.isDirectory) return@enqueue
        val to = File(root, safe(newId()))
        from.copyRecursively(to)
        File(to, NAME).writeText(copyName(File(from, NAME).takeIf { it.isFile }?.readText() ?: "drawing"))
        File(to, SVG).setLastModified(System.currentTimeMillis())
        publish()
    }

    /** Only the [limit] most recently edited drawings are kept. */
    fun keepAtMost(limit: Int) = enqueue {
        root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { File(it, SVG).lastModified() }
            ?.drop(limit.coerceAtLeast(0))?.forEach { it.deleteRecursively() }
        publish()
    }

    private fun publish() {
        _cards.value = root.listFiles()?.filter { it.isDirectory && File(it, SVG).isFile }.orEmpty().map { dir ->
            val svg = File(dir, SVG)
            val thumb = File(dir, THUMB).takeIf { it.isFile }
            val nodes = runCatching { SvgDocument.parse(svg.readText()).nodeCount }.getOrDefault(0)
            ProjectCard(
                id = dir.name,
                name = File(dir, NAME).takeIf { it.isFile }?.readText()?.ifBlank { null } ?: "drawing",
                format = OutputFormat.SVG,
                editedAtMs = svg.lastModified(),
                thumbnail = thumb?.absolutePath,
                thumbnailStamp = thumb?.lastModified() ?: 0L,
                clips = nodes,
                durationMs = 0L,
                missingFiles = 0,
                copiedFiles = 0,
                bytes = dir.listFiles()?.sumOf { it.length() } ?: 0L,
                lastExport = null,
                drawing = true,
            )
        }.sortedByDescending { it.editedAtMs }
    }

    /** [doc] drawn at most [THUMB_SIDE] px on its longer side, on white where it is transparent. */
    private fun thumbnail(doc: SvgDocument): Bitmap? = runCatching {
        val scale = minOf(1.0, THUMB_SIDE / maxOf(doc.width, doc.height, 1.0))
        val w = (doc.width * scale).toInt().coerceAtLeast(1)
        val h = (doc.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.scale(scale.toFloat(), scale.toFloat())
        SvgRenderer().draw(canvas, doc)
        bitmap
    }.getOrNull()

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        private const val TAG = "SvgDrawingStore"
        private const val ROOT = "drawings"
        private const val SVG = "drawing.svg"
        private const val NAME = "name.txt"
        private const val THUMB = "thumb.png"
        private const val THUMB_SIDE = 480.0

        @Volatile
        private var instance: SvgDrawingStore? = null

        /** The one store of the app. */
        fun get(context: Context): SvgDrawingStore =
            instance ?: synchronized(this) { instance ?: SvgDrawingStore(context).also { instance = it } }
    }
}
