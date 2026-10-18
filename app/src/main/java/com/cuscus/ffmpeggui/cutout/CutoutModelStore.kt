package com.cuscus.ffmpeggui.cutout

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
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
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The cut-out model on this phone: imported from the file the user downloaded,
 * checked against its pinned SHA-256, kept in the app's private files, removed
 * on request. It tells where it is with the speech model's states (one file,
 * no archive), so that the settings show both the same way.
 *
 * An import copies the file to a temporary one, hashing it on the way; only a
 * file that matches takes the model's place, so a failed or stopped import
 * leaves what was there before.
 */
class CutoutModelStore(context: Context, val model: CutoutModel) {

    private val appContext = context.applicationContext
    private val folder: File = File(appContext.filesDir, model.folder)

    /** The model's file (it may not be there). */
    val file: File = File(folder, model.fileName)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state.asStateFlow()

    /** True when the model is installed and checked. */
    val ready: Boolean get() = _state.value is State.Ready

    /** Imports the file the user picked. */
    @Synchronized
    fun import(uri: Uri) {
        if (job?.isActive == true) return
        job = scope.launch {
            val wasInstalled = file.isFile
            _state.value = State.Importing(Phase.READING, 0f, model.fileName)
            _state.value = try {
                importNow(uri, wasInstalled)
            } catch (e: CancellationException) {
                _state.value = initialState()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                State.Failed(if (isNoSpace(e)) Problem.NO_SPACE else Problem.UNREADABLE, installed = wasInstalled && file.isFile)
            }
        }
    }

    /** Stops an import; the model as it was stays. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Hashes the installed file again. */
    @Synchronized
    fun verify() {
        if (job?.isActive == true || !file.isFile) return
        job = scope.launch {
            _state.value = State.Importing(Phase.CHECKING, 0f, model.fileName)
            val total = file.length().coerceAtLeast(1L)
            val hash = try {
                file.inputStream().use { hashing(it, total, Phase.CHECKING) }
            } catch (e: IOException) {
                null
            }
            val check = FileCheck(model.fileName, model.sha256, hash)
            _state.value = if (check.matches) {
                val now = System.currentTimeMillis()
                prefs().edit().putString(KEY_HASH, hash).putLong(KEY_VERIFIED, now).apply()
                State.Ready(file.length(), listOf(check), archiveMatches = null, verifiedAt = now)
            } else {
                State.Failed(Problem.HASH_MISMATCH, listOf(check), installed = true)
            }
        }
    }

    /** Removes the model from the phone. */
    @Synchronized
    fun delete() {
        stop()
        folder.deleteRecursively()
        prefs().edit().clear().apply()
        _state.value = State.Missing
    }

    // ---------------------------------------------------------------------

    private fun prefs() = appContext.getSharedPreferences(model.prefs, Context.MODE_PRIVATE)

    private fun initialState(): State {
        if (!file.isFile) return State.Missing
        val prefs = prefs()
        val check = FileCheck(model.fileName, model.sha256, prefs.getString(KEY_HASH, null))
        // The hash found at import time, as long as the file has not changed size since.
        return if (file.length() == model.bytes && check.matches) {
            State.Ready(file.length(), listOf(check), archiveMatches = null, verifiedAt = prefs.getLong(KEY_VERIFIED, 0L))
        } else {
            State.Failed(Problem.HASH_MISMATCH, listOf(check), installed = true)
        }
    }

    private suspend fun importNow(uri: Uri, wasInstalled: Boolean): State {
        folder.mkdirs()
        val temp = File(folder, TEMP_NAME)
        try {
            val size = uriSize(uri)
            // Something of another size is not the model at all: say so before copying it.
            if (size != null && size != model.bytes) {
                return State.Failed(Problem.NOT_THE_MODEL, installed = wasInstalled)
            }
            val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            val hash = input.use { stream ->
                temp.outputStream().use { out -> hashing(stream, size ?: model.bytes, Phase.READING) { bytes, n -> out.write(bytes, 0, n) } }
            }
            val check = FileCheck(model.fileName, model.sha256, hash)
            if (!check.matches) {
                val problem = if (temp.length() != model.bytes) Problem.NOT_THE_MODEL else Problem.HASH_MISMATCH
                return State.Failed(problem, listOf(check), installed = wasInstalled)
            }
            _state.value = State.Importing(Phase.CHECKING, 1f, model.fileName)
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Could not move the model into place")
            val now = System.currentTimeMillis()
            prefs().edit().clear().putString(KEY_HASH, hash).putLong(KEY_VERIFIED, now).apply()
            return State.Ready(file.length(), listOf(check), archiveMatches = null, verifiedAt = now)
        } finally {
            temp.delete()
        }
    }

    /** Reads [input] to its end, hashing it (and handing each piece to [sink]); returns the SHA-256. */
    private suspend fun hashing(input: InputStream, total: Long, phase: Phase, sink: (ByteArray, Int) -> Unit = { _, _ -> }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var done = 0L
        var count = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            sink(buffer, n)
            digest.update(buffer, 0, n)
            done += n
            if (++count % 16 == 0) {
                currentCoroutineContext().ensureActive()
                _state.value = State.Importing(phase, (done.toFloat() / total).coerceIn(0f, 1f), model.fileName)
            }
        }
        return SpeechModelStore.hex(digest.digest())
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

    private companion object {
        const val TAG = "CutoutModelStore"
        const val TEMP_NAME = "importing.part"
        const val KEY_HASH = "sha256"
        const val KEY_VERIFIED = "verified_at"
        const val BUFFER = 256 * 1024
    }
}
