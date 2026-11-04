package com.cuscus.ffmpeggui.dub

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.cuscus.ffmpeggui.core.archive.Bzip2InputStream
import com.cuscus.ffmpeggui.core.archive.TarReader
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import com.cuscus.ffmpeggui.speech.SpeechModelStore.FileCheck
import com.cuscus.ffmpeggui.speech.SpeechModelStore.Phase
import com.cuscus.ffmpeggui.speech.SpeechModelStore.Problem
import com.cuscus.ffmpeggui.speech.SpeechModelStore.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The dubbing model on the phone (see DubModel): its archive unpacked whole
 * into the app's private files, folders kept, while it is read once as a
 * stream (hashed as it comes, decompressed on every core). The import is put
 * together apart and takes the place of the old one only when the archive
 * and the pinned files hash right. Its state reads like the other models'
 * (SpeechModelStore.State), so that the settings show it the same way.
 */
class DubModelStore(context: Context) {

    private val appContext = context.applicationContext
    val folder: File = File(appContext.filesDir, FOLDER)
    private val temp: File = File(appContext.filesDir, TEMP_FOLDER)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    init {
        // The int8 build of the first version: slower and harsher, given up for the float32 one.
        val old = File(appContext.filesDir, OLD_FOLDER)
        if (old.exists()) {
            old.deleteRecursively()
            prefs.edit().clear().apply()
        }
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state.asStateFlow()

    val ready: Boolean get() = _state.value is State.Ready

    /** A file of the installed model ([path] inside its folder), or null when it is not installed. */
    fun file(path: String): File? = File(folder, path).takeIf { ready && it.exists() }

    /** Imports the archive the user picked (a single file). */
    @Synchronized
    fun import(uri: Uri) {
        if (job?.isActive == true) return
        // The AI marks come first (MarkingGate): without them nothing is read.
        if (!com.cuscus.ffmpeggui.marking.MarkingGate.allowsImport()) return
        job = scope.launch {
            val wasInstalled = filesPresent()
            _state.value = State.Importing(Phase.READING, 0f, null)
            val result = try {
                importNow(uri)
            } catch (e: CancellationException) {
                temp.deleteRecursively()
                _state.value = initialState()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                temp.deleteRecursively()
                State.Failed(if (isNoSpace(e)) Problem.NO_SPACE else Problem.UNREADABLE, installed = wasInstalled)
            }
            _state.value = if (result is State.Failed) result.copy(installed = wasInstalled && filesPresent()) else result
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Hashes the pinned files again. */
    @Synchronized
    fun verify() {
        if (job?.isActive == true || !filesPresent()) return
        job = scope.launch {
            _state.value = State.Importing(Phase.CHECKING, 0f, null)
            val total = DubModel.PINNED.keys.sumOf { File(folder, it).length() }.coerceAtLeast(1L)
            var done = 0L
            val checks = DubModel.PINNED.map { (name, expected) ->
                val file = File(folder, name)
                val hash = if (!file.isFile) null else try {
                    sha256(FileInputStream(file)) { read ->
                        done += read
                        _state.value = State.Importing(Phase.CHECKING, done.toFloat() / total, name)
                    }
                } catch (e: IOException) {
                    null
                }
                FileCheck(name, expected, hash)
            }
            _state.value = if (checks.all { it.matches }) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_VERIFIED, now).apply()
                State.Ready(installedBytes(), checks, prefs.getString(KEY_ARCHIVE, null)?.let { it == "1" }, now)
            } else {
                State.Failed(Problem.HASH_MISMATCH, checks, installed = true)
            }
        }
    }

    @Synchronized
    fun delete() {
        stop()
        folder.deleteRecursively()
        temp.deleteRecursively()
        prefs.edit().clear().apply()
        _state.value = State.Missing
    }

    // ---------------------------------------------------------------------

    private fun initialState(): State {
        if (!filesPresent()) return State.Missing
        val checks = DubModel.PINNED.map { (name, expected) -> FileCheck(name, expected, prefs.getString(name, null)) }
        return if (checks.all { it.matches }) {
            State.Ready(prefs.getLong(KEY_BYTES, 0L), checks, prefs.getString(KEY_ARCHIVE, null)?.let { it == "1" }, prefs.getLong(KEY_VERIFIED, 0L))
        } else {
            State.Failed(Problem.HASH_MISMATCH, checks, installed = true)
        }
    }

    private fun filesPresent(): Boolean = DubModel.PINNED.keys.all { File(folder, it).isFile }

    private fun installedBytes(): Long = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private suspend fun importNow(uri: Uri): State {
        temp.deleteRecursively()
        temp.mkdirs()
        val size = uriSize(uri) ?: DubModel.ARCHIVE_BYTES
        val archiveDigest = MessageDigest.getInstance("SHA-256")
        val found = HashMap<String, String>()
        var shown = -1
        var any = false
        val counted = CountingStream(BufferedInputStream(open(uri), BUFFER)) { total, bytes, offset, length ->
            archiveDigest.update(bytes, offset, length)
            val step = (total * PROGRESS_STEPS / size).toInt()
            if (step != shown) {
                shown = step
                _state.value = State.Importing(Phase.READING, (total.toFloat() / size).coerceIn(0f, 1f), (_state.value as? State.Importing)?.file)
            }
        }
        val root = temp.canonicalFile
        counted.use { raw ->
            Bzip2InputStream(raw).use { decompressed ->
                val tar = TarReader(decompressed)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = tar.next() ?: break
                    any = true
                    // Inside the archive's own folder; anything else, or a way out of it, is left.
                    val path = entry.path.removePrefix("./").removePrefix(DubModel.ARCHIVE_FOLDER + "/")
                    if (path == entry.path.removePrefix("./") || path.isEmpty()) continue
                    val target = File(temp, path).canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) continue
                    target.parentFile?.mkdirs()
                    val pinned = path in DubModel.PINNED
                    if (pinned) _state.value = State.Importing(Phase.READING, (_state.value as? State.Importing)?.fraction, entry.name)
                    val digest = if (pinned) MessageDigest.getInstance("SHA-256") else null
                    target.outputStream().use { out ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            val n = tar.content.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest?.update(buffer, 0, n)
                        }
                    }
                    if (digest != null) found[path] = SpeechModelStore.hex(digest.digest())
                }
                // The rest: the bzip2 checks and the archive's own hash.
                val sink = ByteArray(BUFFER)
                while (decompressed.read(sink) >= 0) currentCoroutineContext().ensureActive()
                while (raw.read(sink) >= 0) currentCoroutineContext().ensureActive()
            }
        }
        if (!any || found.isEmpty()) {
            temp.deleteRecursively()
            return State.Failed(Problem.NOT_THE_MODEL, installed = false)
        }
        val archiveMatches = SpeechModelStore.hex(archiveDigest.digest()) == DubModel.ARCHIVE_SHA256
        val checks = DubModel.PINNED.map { (name, expected) -> FileCheck(name, expected, found[name]) }
        if (checks.any { it.actual == null }) {
            temp.deleteRecursively()
            return State.Failed(Problem.INCOMPLETE, checks, installed = false)
        }
        if (checks.any { !it.matches }) {
            temp.deleteRecursively()
            return State.Failed(Problem.HASH_MISMATCH, checks, installed = false)
        }
        _state.value = State.Importing(Phase.CHECKING, 1f, null)
        folder.deleteRecursively()
        folder.parentFile?.mkdirs()
        if (!temp.renameTo(folder)) throw IOException("Could not move the model into place")
        val now = System.currentTimeMillis()
        val bytes = installedBytes()
        prefs.edit().apply {
            clear()
            checks.forEach { putString(it.name, it.actual) }
            putString(KEY_ARCHIVE, if (archiveMatches) "1" else "0")
            putLong(KEY_VERIFIED, now)
            putLong(KEY_BYTES, bytes)
        }.apply()
        return State.Ready(bytes, checks, archiveMatches, now)
    }

    private suspend fun sha256(input: InputStream, onRead: (Int) -> Unit): String = input.use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var count = 0
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            onRead(n)
            if (++count % 64 == 0) currentCoroutineContext().ensureActive()
        }
        SpeechModelStore.hex(digest.digest())
    }

    private fun open(uri: Uri): InputStream =
        appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    private fun uriSize(uri: Uri): Long? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
        }
    } catch (e: Exception) {
        null
    }

    private fun isNoSpace(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

    /** Counts what is read through it, handing over the bytes as they pass (skips are read, for the hash). */
    private class CountingStream(
        input: InputStream,
        private val onRead: (total: Long, bytes: ByteArray, offset: Int, length: Int) -> Unit,
    ) : FilterInputStream(input) {
        private var total = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) {
                total++
                onRead(total, byteArrayOf(b.toByte()), 0, 1)
            }
            return b
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = super.read(buffer, offset, length)
            if (n > 0) {
                total += n
                onRead(total, buffer, offset, n)
            }
            return n
        }

        override fun skip(n: Long): Long {
            val buffer = ByteArray(minOf(n, 64L * 1024).toInt().coerceAtLeast(1))
            var left = n
            while (left > 0) {
                val read = read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                if (read < 0) break
                left -= read
            }
            return n - left
        }
    }

    private companion object {
        const val TAG = "DubModelStore"
        const val FOLDER = "dub/kokoro-multi-lang-v1_0"

        /** Where the int8 build was kept, by the first version of the dubbing: removed. */
        const val OLD_FOLDER = "dub/kokoro-int8-multi-lang-v1_0"
        const val TEMP_FOLDER = "dub/importing"
        const val PREFS = "dub_model"
        const val KEY_ARCHIVE = "archive_matches"
        const val KEY_VERIFIED = "verified_at"
        const val KEY_BYTES = "installed_bytes"
        const val BUFFER = 256 * 1024
        const val PROGRESS_STEPS = 1000L
    }
}
