package com.cuscus.ffmpeggui.command

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.cuscus.ffmpeggui.core.command.CommandFile
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.studio.CatalogTopic
import com.cuscus.ffmpeggui.core.command.studio.FfmpegCatalog
import com.cuscus.ffmpeggui.core.command.studio.FfmpegHelpParser
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * What the command studio keeps: what the bundled FFmpeg knows (asked once
 * per app version, kept on the phone; nothing goes online), the commands run
 * and starred, and the files added to the tray.
 */
class CommandStudioStore(context: Context, private val executor: FfmpegExecutor) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = app.getSharedPreferences("command_studio", Context.MODE_PRIVATE)

    private val _catalog = MutableStateFlow(FfmpegCatalog.BUILT_IN)
    /** Starts as the built-in everyday options; FFmpeg's own list replaces it once read. */
    val catalog: StateFlow<FfmpegCatalog> = _catalog.asStateFlow()

    private val _reading = MutableStateFlow(false)
    val reading: StateFlow<Boolean> = _reading.asStateFlow()

    private val _topics = MutableStateFlow<Map<String, CatalogTopic>>(emptyMap())
    /** FFmpeg's help about one encoder or format ("encoder=libx264", "muxer=mp4"). */
    val topics: StateFlow<Map<String, CatalogTopic>> = _topics.asStateFlow()

    private val _history = MutableStateFlow(readList(KEY_HISTORY))
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _favourites = MutableStateFlow(readList(KEY_FAVOURITES))
    val favourites: StateFlow<List<String>> = _favourites.asStateFlow()

    @Volatile private var started = false
    private val asked = mutableSetOf<String>()

    /** Where FFmpeg's answers are kept, one folder per installed version of the app. */
    private val helpDir: File by lazy {
        val stamp = try {
            app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
        } catch (e: Exception) {
            0L
        }
        val root = File(app.filesDir, "ffmpeg_help")
        root.listFiles()?.filter { it.name != stamp.toString() }?.forEach { it.deleteRecursively() }
        File(root, stamp.toString()).apply { mkdirs() }
    }

    private val filesDir: File get() = File(app.filesDir, "command_files").apply { mkdirs() }

    /** Reads what FFmpeg knows, once (from the phone after the first time). */
    fun ensureCatalog() {
        if (started) return
        started = true
        scope.launch {
            _reading.value = true
            try {
                val full = ask("full", "-h", "full")
                val filters = ask("filters", "-filters")
                val encoders = ask("encoders", "-encoders")
                val muxers = ask("muxers", "-muxers")
                val pixFmts = ask("pix_fmts", "-pix_fmts")
                val read = FfmpegHelpParser.catalog(full, filters, encoders, muxers, pixFmts)
                // Filters asked about meanwhile are kept.
                _catalog.update { old -> old.filterHelp.entries.fold(read) { c, (k, v) -> c.withFilterHelp(k, v) } }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read FFmpeg's help: ${e.message}")
                started = false
            } finally {
                _reading.value = false
            }
        }
    }

    /** Asks FFmpeg about a filter, an encoder or a format ("filter=scale"), once. */
    fun requestHelp(key: String) {
        synchronized(asked) { if (!asked.add(key)) return }
        scope.launch {
            try {
                val topic = FfmpegHelpParser.parseTopic(ask(key, "-h", key))
                if (key.startsWith("filter=")) {
                    _catalog.update { it.withFilterHelp(key.removePrefix("filter="), topic) }
                } else {
                    _topics.update { it + (key to topic) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No help for $key: ${e.message}")
                synchronized(asked) { asked.remove(key) }
            }
        }
    }

    /** FFmpeg's answer to [args], from the phone when it was asked before. */
    private suspend fun ask(name: String, vararg args: String): String {
        val file = File(helpDir, name.replace(Regex("[^A-Za-z0-9_=.-]"), "_") + ".txt")
        if (file.isFile && file.length() > 0) return file.readText()
        val out = StringBuilder()
        executor.execute(FfmpegCommand(listOf("-hide_banner") + args)) { line -> synchronized(out) { out.append(line) } }
        val text = synchronized(out) { out.toString() }
        if (text.isNotBlank()) file.writeText(text)
        return text
    }

    // -- History and favourites --------------------------------------------------------------

    /** A command the user used: first in the history, once. */
    fun remember(command: String) {
        val c = command.trim()
        if (c.isEmpty()) return
        _history.update { (listOf(c) + it.filter { h -> h != c }).take(MAX_HISTORY) }
        writeList(KEY_HISTORY, _history.value)
    }

    fun forget(command: String) {
        _history.update { it.filter { h -> h != command } }
        writeList(KEY_HISTORY, _history.value)
    }

    fun setFavourite(command: String, on: Boolean) {
        val c = command.trim()
        if (c.isEmpty()) return
        _favourites.update { if (on) (listOf(c) + it.filter { f -> f != c }).take(MAX_FAVOURITES) else it.filter { f -> f != c } }
        writeList(KEY_FAVOURITES, _favourites.value)
    }

    private fun readList(key: String): List<String> = try {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeList(key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }

    // -- Files ---------------------------------------------------------------------------------

    /**
     * Keeps a copy of [uri] in the app (so it stays readable) and names it for
     * the command; null when it cannot be read.
     */
    suspend fun importFile(uri: Uri, taken: Set<String>): CommandFile? = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "file"
        val alias = CommandFile.aliasFor(name, taken)
        val target = File(filesDir, "${UUID.randomUUID()}_$alias")
        try {
            val input = app.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            CommandFile(alias, Uri.fromFile(target).toString(), name)
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep $name: ${e.message}")
            target.delete()
            null
        }
    }

    private fun displayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment
    } catch (e: Exception) {
        uri.lastPathSegment
    }

    private companion object {
        const val TAG = "CommandStudio"
        const val KEY_HISTORY = "history"
        const val KEY_FAVOURITES = "favourites"
        const val MAX_HISTORY = 40
        const val MAX_FAVOURITES = 60
    }
}
