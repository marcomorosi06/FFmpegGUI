package com.cuscus.ffmpeggui.speech

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.cuscus.ffmpeggui.core.archive.Bzip2InputStream
import com.cuscus.ffmpeggui.core.archive.DownloadedName
import com.cuscus.ffmpeggui.core.archive.TarReader
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
 * One build of the speech model ([model]) on this phone: imported from what the user downloaded,
 * checked, kept in the app's private files, removed on request.
 *
 * An import reads the archive (or the loose files) once, as a stream: the
 * archive is decompressed and unpacked on the fly, every file hashed as it is
 * written, and the whole lands in a temporary folder that only takes the
 * model's place once every file matches its pinned SHA-256. A failed or
 * stopped import leaves what was there before untouched.
 */
class SpeechModelStore(context: Context, val model: SpeechModel) {

    /** Where an import is. */
    enum class Phase { READING, CHECKING }

    /** One file of the model and whether it matched. */
    data class FileCheck(val name: String, val expected: String, val actual: String?) {
        val matches: Boolean get() = actual == expected
    }

    sealed interface State {
        data object Missing : State

        /** [fraction] of the archive read (null while its size is unknown); [file] is the one being written. */
        data class Importing(val phase: Phase, val fraction: Float?, val file: String?) : State

        /** Installed and checked; [checks] are the files with their hashes, [archiveMatches] when an archive was read. */
        data class Ready(val bytes: Long, val checks: List<FileCheck>, val archiveMatches: Boolean?, val verifiedAt: Long) : State

        /** The last import or check failed for [problem]; [checks] tells which files, when known. */
        data class Failed(val problem: Problem, val checks: List<FileCheck> = emptyList(), val installed: Boolean) : State
    }

    enum class Problem {
        /** The file chosen is not the model's archive nor one of its files. */
        NOT_THE_MODEL,

        /** Some file is missing from what was chosen. */
        INCOMPLETE,

        /** A file does not hash to what it should: damaged or another model. */
        HASH_MISMATCH,

        /** It could not be read (a broken download, a provider that failed). */
        UNREADABLE,

        /** There is no room for it. */
        NO_SPACE,
    }

    private val appContext = context.applicationContext
    val folder: File = File(appContext.filesDir, model.folder)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state.asStateFlow()

    /** True when every file of the model is in place (checked when it was imported). */
    val isInstalled: Boolean get() = _state.value is State.Ready || ((_state.value as? State.Failed)?.installed == true && filesPresent())

    /** The file of the model with [role], or null when it is not installed. */
    fun file(role: SpeechModel.Role): File? =
        model.files.firstOrNull { it.role == role }?.let { File(folder, it.name) }?.takeIf { it.isFile }

    /** The recordings that came with the model, those that are on the phone. */
    fun samples(): List<Pair<SpeechModel.Sample, File>> =
        model.samples.mapNotNull { sample -> File(folder, sample.file.name).takeIf { it.isFile }?.let { sample to it } }

    /** Imports what the user picked: the downloaded archive, or the model's files. */
    @Synchronized
    fun import(uris: List<Uri>) {
        if (job?.isActive == true || uris.isEmpty()) return
        job = scope.launch {
            val wasInstalled = filesPresent()
            _state.value = State.Importing(Phase.READING, 0f, null)
            val result = try {
                importNow(uris)
            } catch (e: CancellationException) {
                _state.value = initialState()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                State.Failed(if (isNoSpace(e)) Problem.NO_SPACE else Problem.UNREADABLE, installed = wasInstalled)
            }
            _state.value = if (result is State.Failed) result.copy(installed = wasInstalled && filesPresent()) else result
        }
    }

    /** Stops an import; the model as it was stays. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Hashes the installed files again (they are large: it takes a little while). */
    @Synchronized
    fun verify() {
        if (job?.isActive == true || !filesPresent()) return
        job = scope.launch {
            _state.value = State.Importing(Phase.CHECKING, 0f, null)
            val total = model.allFiles.sumOf { File(folder, it.name).length() }.coerceAtLeast(1L)
            var done = 0L
            val checks = model.allFiles.mapNotNull { expected ->
                val file = File(folder, expected.name)
                if (!file.isFile) return@mapNotNull if (expected.role == SpeechModel.Role.SAMPLE) null else FileCheck(expected.name, expected.sha256, null)
                val hash = try {
                    sha256(FileInputStream(file)) { read ->
                        done += read
                        _state.value = State.Importing(Phase.CHECKING, done.toFloat() / total, expected.name)
                    }
                } catch (e: IOException) {
                    null
                }
                FileCheck(expected.name, expected.sha256, hash)
            }
            _state.value = if (checks.all { it.matches }) {
                State.Ready(installedBytes(), checks, archiveMatches = null, verifiedAt = System.currentTimeMillis())
            } else {
                State.Failed(Problem.HASH_MISMATCH, checks, installed = true)
            }
        }
    }

    /** Removes the model from the phone. */
    @Synchronized
    fun delete() {
        stop()
        folder.deleteRecursively()
        File(folder.parentFile, model.tempFolder).deleteRecursively()
        appContext.getSharedPreferences(model.prefs, Context.MODE_PRIVATE).edit().clear().apply()
        _state.value = State.Missing
    }

    // ---------------------------------------------------------------------

    private fun initialState(): State {
        if (!filesPresent()) return State.Missing
        val prefs = appContext.getSharedPreferences(model.prefs, Context.MODE_PRIVATE)
        val checks = model.allFiles.mapNotNull { expected ->
            val file = File(folder, expected.name)
            if (!file.isFile) null else FileCheck(expected.name, expected.sha256, prefs.getString(expected.name, null))
        }
        // The hashes found at import time, as long as the files have not changed size since.
        val sizesHold = model.allFiles.all { expected ->
            val file = File(folder, expected.name)
            !file.isFile || expected.bytes == null || file.length() == expected.bytes
        }
        return if (sizesHold && checks.all { it.matches }) {
            State.Ready(installedBytes(), checks, prefs.getString(KEY_ARCHIVE, null)?.let { it == "1" }, prefs.getLong(KEY_VERIFIED, 0L))
        } else {
            State.Failed(Problem.HASH_MISMATCH, checks, installed = true)
        }
    }

    private fun filesPresent(): Boolean = model.files.all { File(folder, it.name).isFile }

    private fun installedBytes(): Long = model.allFiles.sumOf { File(folder, it.name).takeIf { f -> f.isFile }?.length() ?: 0L }

    private suspend fun importNow(uris: List<Uri>): State {
        val temp = File(folder.parentFile, model.tempFolder).apply { deleteRecursively(); mkdirs() }
        try {
            val found = linkedMapOf<String, String>()
            var archiveMatches: Boolean? = null
            val single = uris.singleOrNull()
            val archive = model.archive
            if (single != null && archive != null && looksLikeArchive(single)) {
                archiveMatches = readArchive(single, archive, temp, found)
            } else {
                for ((index, uri) in uris.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val shown = displayName(uri) ?: "#${index + 1}"
                    // Browsers number a file downloaded twice ("tokens (1).txt"): the name is
                    // only a guess, the content says which file it is.
                    val guess = model.fileNamed(DownloadedName.of(shown))
                    if (guess == null && DownloadedName.of(shown).substringAfterLast('.') !in modelExtensions) continue
                    val part = File(temp, "picked-$index.part")
                    val hash = copyHashing(open(uri), part, uriSize(uri), shown)
                    val expected = model.allFiles.firstOrNull { it.sha256 == hash } ?: guess
                    // Not the model's, or a damaged copy of a file already found whole.
                    if (expected == null || (found[expected.name] == expected.sha256 && hash != expected.sha256)) {
                        part.delete()
                        continue
                    }
                    val target = File(temp, expected.name)
                    target.delete()
                    if (!part.renameTo(target)) throw IOException("Could not name ${expected.name}")
                    found[expected.name] = hash
                }
            }
            if (found.isEmpty()) return State.Failed(Problem.NOT_THE_MODEL, installed = false)
            // What the app carries itself, when it was not picked (or not in the archive).
            for (expected in model.files) {
                val asset = expected.asset ?: continue
                if (found.containsKey(expected.name)) continue
                found[expected.name] = copyHashing(appContext.assets.open(asset), File(temp, expected.name), expected.bytes, expected.name)
            }
            val checks = model.allFiles.mapNotNull { expected ->
                val hash = found[expected.name]
                if (hash == null && expected.role == SpeechModel.Role.SAMPLE) null else FileCheck(expected.name, expected.sha256, hash)
            }
            if (checks.any { it.actual == null }) return State.Failed(Problem.INCOMPLETE, checks, installed = false)
            if (checks.any { !it.matches }) return State.Failed(Problem.HASH_MISMATCH, checks, installed = false)

            // Every file matches: the new model takes the place of the old one.
            _state.value = State.Importing(Phase.CHECKING, 1f, null)
            folder.deleteRecursively()
            folder.parentFile?.mkdirs()
            if (!temp.renameTo(folder)) throw IOException("Could not move the model into place")
            val now = System.currentTimeMillis()
            appContext.getSharedPreferences(model.prefs, Context.MODE_PRIVATE).edit().apply {
                clear()
                checks.forEach { putString(it.name, it.actual) }
                archiveMatches?.let { putString(KEY_ARCHIVE, if (it) "1" else "0") }
                putLong(KEY_VERIFIED, now)
            }.apply()
            return State.Ready(installedBytes(), checks, archiveMatches, now)
        } finally {
            temp.deleteRecursively()
        }
    }

    /**
     * Unpacks the files of the model out of the archive into [target], their
     * hashes into [found]. Returns whether the archive itself hashed right.
     */
    private suspend fun readArchive(uri: Uri, archive: SpeechModel.Archive, target: File, found: MutableMap<String, String>): Boolean {
        val size = uriSize(uri) ?: archive.bytes
        val archiveDigest = MessageDigest.getInstance("SHA-256")
        var shown = -1
        val counted = CountingStream(BufferedInputStream(open(uri), BUFFER)) { read, bytes, offset, length ->
            archiveDigest.update(bytes, offset, length)
            // A step every thousandth of the archive is plenty for the progress bar.
            val step = (read * PROGRESS_STEPS / size).toInt()
            if (step != shown) {
                shown = step
                _state.value = State.Importing(Phase.READING, (read.toFloat() / size).coerceIn(0f, 1f), (_state.value as? State.Importing)?.file)
            }
        }
        counted.use { raw ->
            // Decompressed on every core; closing it stops its threads.
            Bzip2InputStream(raw).use { decompressed ->
            val tar = TarReader(decompressed)
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.next() ?: break
                val expected = model.fileNamed(entry.name) ?: continue
                if (expected.bytes != null && entry.size != expected.bytes) continue
                _state.value = State.Importing(Phase.READING, (_state.value as? State.Importing)?.fraction, entry.name)
                found[entry.name] = copyHashing(tar.content, File(target, entry.name), null, entry.name, close = false)
            }
            // The rest of the archive: its checks (bzip2 CRCs) and the archive's own hash.
            val sink = ByteArray(BUFFER)
            while (decompressed.read(sink) >= 0) currentCoroutineContext().ensureActive()
            while (raw.read(sink) >= 0) currentCoroutineContext().ensureActive()
            }
        }
        return hex(archiveDigest.digest()) == archive.sha256
    }

    /** Copies [input] to [target], hashing it; returns the SHA-256. */
    private suspend fun copyHashing(input: InputStream, target: File, size: Long?, name: String, close: Boolean = true): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var done = 0L
        try {
            target.outputStream().use { out ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    done += n
                    if (done % (BUFFER * 32L) < BUFFER) {
                        currentCoroutineContext().ensureActive()
                        if (size != null && size > 0) _state.value = State.Importing(Phase.READING, (done.toFloat() / size).coerceIn(0f, 1f), name)
                    }
                }
            }
        } finally {
            if (close) input.close()
        }
        return hex(digest.digest())
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
        hex(digest.digest())
    }

    private fun open(uri: Uri): InputStream =
        appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    private fun looksLikeArchive(uri: Uri): Boolean {
        val name = displayName(uri)?.lowercase() ?: return true
        if (model.fileNamed(DownloadedName.of(name)) != null) return false
        return name.endsWith(".bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz") || !name.contains('.')
    }

    /** The extensions of the model's files ("onnx", "txt", "wav"). */
    private val modelExtensions: Set<String> by lazy { model.allFiles.map { it.name.substringAfterLast('.') }.toSet() }

    private fun displayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (e: Exception) {
        uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uriSize(uri: Uri): Long? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
        }
    } catch (e: Exception) {
        null
    }

    private fun isNoSpace(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

    /** Counts what is read through it, handing over the bytes as they pass. */
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

        // Skipped bytes would be missing from the hash: read them instead.
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

    companion object {
        private const val TAG = "SpeechModelStore"
        private const val KEY_ARCHIVE = "archive_matches"
        private const val KEY_VERIFIED = "verified_at"
        private const val BUFFER = 256 * 1024
        private const val PROGRESS_STEPS = 1000L

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }
}
