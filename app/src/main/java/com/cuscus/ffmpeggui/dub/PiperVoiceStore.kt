package com.cuscus.ffmpeggui.dub

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.cuscus.ffmpeggui.core.archive.Bzip2InputStream
import com.cuscus.ffmpeggui.core.archive.DownloadedName
import com.cuscus.ffmpeggui.core.archive.TarReader
import com.cuscus.ffmpeggui.core.dub.OnnxMetadata
import com.cuscus.ffmpeggui.core.dub.PiperListed
import com.cuscus.ffmpeggui.core.dub.PiperVoiceConfig
import com.cuscus.ffmpeggui.core.dub.VoiceLicence
import com.cuscus.ffmpeggui.speech.SpeechModelStore
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
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The Piper voices on the phone (see PiperVoiceConfig): any number, each in
 * a folder of its own in the app's private files: its network (with the
 * metadata sherpa-onnx reads, appended at import), its token list made from
 * its settings, its settings and, when it came with one, its model card (who
 * recorded it, under which licence).
 *
 * A voice comes as one of sherpa-onnx's archives (vits-piper-*.tar.bz2, from
 * its releases on GitHub) or as Piper's own two files (the .onnx network and
 * its .onnx.json settings, as Piper publishes them and as a voice trained at
 * home comes out). eSpeak NG's data, which every voice reads words with, is
 * kept once for all of them (taken from the first archive that has it, or
 * from the Kokoro model's folder).
 *
 * Unlike the models the app knows, a voice can be any one: nothing is pinned;
 * its network's SHA-256 is taken at import and shown.
 */
class PiperVoiceStore(context: Context, private val kokoro: DubModelStore) {

    /** A voice on the phone. */
    class Voice(val id: String, val folder: File, val config: PiperVoiceConfig, val bytes: Long, val sha256: String, val card: String?) {
        val model: File get() = File(folder, MODEL)
        val tokens: File get() = File(folder, TOKENS)

        /**
         * How good it sounds ("x_low", "low", "medium", "high"): as its name says
         * ("it_IT-miro-high"), which every voice published carries, else as its
         * settings say, in Piper's words; null when neither tells.
         */
        val quality: String? =
            id.substringAfterLast('-').lowercase().takeIf { it in PiperListed.QUALITIES }
                ?: config.quality?.trim()?.lowercase()?.replace('-', '_')?.takeIf { it in PiperListed.QUALITIES }

        /** What its card says may be done with it (see VoiceLicence). */
        val licence: VoiceLicence = VoiceLicence.of(card)

        /** "Paola": the voice's own name, from its file's ("it_IT-paola-medium"). */
        val name: String
            get() = id.split('-').getOrNull(1)?.replace('_', ' ')?.replaceFirstChar { it.uppercaseChar() } ?: id
    }

    sealed interface Importing {
        data object Idle : Importing
        data class Working(val fraction: Float?, val file: String?) : Importing
        data class Failed(val problem: Problem) : Importing
        data class Done(val name: String) : Importing
    }

    enum class Problem {
        /** No Piper network with its settings was picked. */
        NOT_A_VOICE,

        /** Its settings ask for another front end than eSpeak NG (a pinyin voice). */
        UNSUPPORTED,

        /** Piper's own files, but eSpeak NG's data is not on the phone yet. */
        NO_ESPEAK,

        UNREADABLE,
        NO_SPACE,
    }

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, FOLDER)
    private val sharedEspeak = File(appContext.filesDir, ESPEAK_FOLDER)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _voices = MutableStateFlow(scan())
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    private val _importing = MutableStateFlow<Importing>(Importing.Idle)
    val importing: StateFlow<Importing> = _importing.asStateFlow()

    /** Where eSpeak NG's data is: the voices' own copy, else the Kokoro model's; null when neither. */
    fun espeakData(): File? =
        sharedEspeak.takeIf { File(it, PHONTAB).isFile } ?: kokoro.file(DubModel.ESPEAK_DATA)?.takeIf { File(it, PHONTAB).isFile }

    @Synchronized
    fun import(uris: List<Uri>) {
        if (job?.isActive == true || uris.isEmpty()) return
        job = scope.launch {
            _importing.value = Importing.Working(null, null)
            val temp = File(root, TEMP).apply { deleteRecursively(); mkdirs() }
            val espeakTemp = File(root, ESPEAK_TEMP).apply { deleteRecursively() }
            _importing.value = try {
                importNow(uris, temp, espeakTemp)
            } catch (e: CancellationException) {
                _importing.value = Importing.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Voice import failed", e)
                Importing.Failed(if (isNoSpace(e)) Problem.NO_SPACE else Problem.UNREADABLE)
            } finally {
                temp.deleteRecursively()
                espeakTemp.deleteRecursively()
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    fun dismiss() {
        if (_importing.value !is Importing.Working) _importing.value = Importing.Idle
    }

    /** Removes voice [id]; eSpeak NG's data goes with the last one. */
    fun delete(id: String) {
        File(root, id).deleteRecursively()
        _voices.value = scan()
        if (_voices.value.isEmpty()) sharedEspeak.deleteRecursively()
    }

    // ---------------------------------------------------------------------

    private suspend fun importNow(uris: List<Uri>, temp: File, espeakTemp: File): Importing {
        var network: File? = null
        var settings: String? = null
        var card: String? = null
        var name: String? = null
        val single = uris.singleOrNull()
        if (single != null && looksLikeArchive(single)) {
            val size = uriSize(single)
            var read = 0L
            val counted = object : java.io.FilterInputStream(BufferedInputStream(open(single), BUFFER)) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) {
                        read += n
                        if (size != null) _importing.value = Importing.Working((read.toFloat() / size).coerceIn(0f, 1f), (_importing.value as? Importing.Working)?.file)
                    }
                    return n
                }
            }
            val wantEspeak = !File(sharedEspeak, PHONTAB).isFile
            counted.use { raw ->
                Bzip2InputStream(raw).use { decompressed ->
                    val tar = TarReader(decompressed)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = tar.next() ?: break
                        val path = entry.path.removePrefix("./")
                        val inEspeak = path.substringAfter("espeak-ng-data/", "").takeIf { "espeak-ng-data/" in path }
                        when {
                            inEspeak != null -> if (wantEspeak) {
                                val target = File(espeakTemp, inEspeak).canonicalFile
                                if (target.path.startsWith(espeakTemp.canonicalPath + File.separator)) {
                                    target.parentFile?.mkdirs()
                                    target.outputStream().use { tar.content.copyTo(it, BUFFER) }
                                }
                            }
                            entry.name.endsWith(".onnx.json") -> settings = String(tar.content.readBytes(), Charsets.UTF_8)
                            entry.name.endsWith(".onnx") -> {
                                name = entry.name.removeSuffix(".onnx")
                                _importing.value = Importing.Working((_importing.value as? Importing.Working)?.fraction, entry.name)
                                val target = File(temp, MODEL)
                                target.outputStream().use { tar.content.copyTo(it, BUFFER) }
                                network = target
                            }
                            entry.name == "MODEL_CARD" || entry.name.equals("README.md", ignoreCase = true) ->
                                card = String(tar.content.readBytes(), Charsets.UTF_8).take(MAX_CARD)
                        }
                    }
                    val sink = ByteArray(BUFFER)
                    while (decompressed.read(sink) >= 0) currentCoroutineContext().ensureActive()
                }
            }
        } else {
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                val shown = DownloadedName.of(displayName(uri) ?: continue)
                when {
                    // A browser that shows a .json file may save it as text.
                    shown.endsWith(".onnx.json") || shown.endsWith(".json") || shown.endsWith(".txt") && "onnx" in shown ->
                        settings = open(uri).use { String(it.readBytes(), Charsets.UTF_8) }
                    shown.endsWith(".onnx") -> {
                        name = shown.removeSuffix(".onnx")
                        _importing.value = Importing.Working(null, shown)
                        val target = File(temp, MODEL)
                        open(uri).use { input -> target.outputStream().use { input.copyTo(it, BUFFER) } }
                        network = target
                    }
                    shown == "MODEL_CARD" || shown.endsWith(".md") -> card = open(uri).use { String(it.readBytes(), Charsets.UTF_8).take(MAX_CARD) }
                }
            }
        }
        val model = network ?: return Importing.Failed(Problem.NOT_A_VOICE)
        val text = settings ?: return Importing.Failed(Problem.NOT_A_VOICE)
        val config = PiperVoiceConfig.parse(text) ?: return Importing.Failed(if (text.contains("phoneme_id_map")) Problem.UNSUPPORTED else Problem.NOT_A_VOICE)
        // eSpeak NG's data: the archive's, the first time; Piper's own files need it there already.
        // Else a copy of the Kokoro model's: the voices keep working once that model is removed.
        if (!File(sharedEspeak, PHONTAB).isFile && !File(espeakTemp, PHONTAB).isFile) {
            kokoro.file(DubModel.ESPEAK_DATA)?.takeIf { File(it, PHONTAB).isFile }?.copyRecursively(espeakTemp, overwrite = true)
        }
        if (File(espeakTemp, PHONTAB).isFile && !File(sharedEspeak, PHONTAB).isFile) {
            sharedEspeak.deleteRecursively()
            sharedEspeak.parentFile?.mkdirs()
            if (!espeakTemp.renameTo(sharedEspeak)) throw IOException("Could not keep eSpeak NG's data")
        }
        if (espeakData() == null) return Importing.Failed(Problem.NO_ESPEAK)
        // The facts sherpa-onnx reads, appended to the network (see OnnxMetadata).
        FileOutputStream(model, true).use { it.write(OnnxMetadata.append(config.metadata())) }
        File(temp, TOKENS).writeText(config.tokens())
        File(temp, SETTINGS).writeText(text)
        card?.let { File(temp, CARD).writeText(it) }
        val id = safeId(name ?: "voice-${System.currentTimeMillis()}")
        val target = File(root, id)
        target.deleteRecursively()
        root.mkdirs()
        if (!temp.renameTo(target)) throw IOException("Could not keep the voice")
        _voices.value = scan()
        return Importing.Done(_voices.value.firstOrNull { it.id == id }?.name ?: id)
    }

    private fun scan(): List<Voice> =
        root.listFiles()?.filter { it.isDirectory && it.name != TEMP && it.name != ESPEAK_TEMP && File(it, MODEL).isFile && File(it, SETTINGS).isFile }
            ?.mapNotNull { dir ->
                val config = runCatching { PiperVoiceConfig.parse(File(dir, SETTINGS).readText()) }.getOrNull() ?: return@mapNotNull null
                val hash = File(dir, HASH).takeIf { it.isFile }?.readText() ?: sha256(File(dir, MODEL)).also { File(dir, HASH).writeText(it) }
                Voice(dir.name, dir, config, dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }, hash, File(dir, CARD).takeIf { it.isFile }?.readText())
            }
            ?.sortedBy { it.id }
            .orEmpty()

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        SpeechModelStore.hex(digest.digest())
    }

    private fun safeId(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private fun looksLikeArchive(uri: Uri): Boolean {
        val name = displayName(uri)?.lowercase() ?: return true
        return name.endsWith(".bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz")
    }

    private fun open(uri: Uri): InputStream = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    private fun displayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (e: Exception) {
        uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uriSize(uri: Uri): Long? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
        }
    } catch (e: Exception) {
        null
    }

    private fun isNoSpace(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

    companion object {
        private const val TAG = "PiperVoiceStore"
        private const val FOLDER = "dub/piper"
        private const val ESPEAK_FOLDER = "dub/espeak-ng-data"
        private const val TEMP = ".importing"
        private const val ESPEAK_TEMP = ".espeak-importing"
        private const val MODEL = "model.onnx"
        private const val TOKENS = "tokens.txt"
        private const val SETTINGS = "voice.onnx.json"
        private const val CARD = "MODEL_CARD"
        private const val HASH = "model.sha256"
        private const val PHONTAB = "phontab"
        private const val BUFFER = 256 * 1024
        private const val MAX_CARD = 4_000

        /** Where Piper's voices are listed, by language (each with its model card). */
        const val VOICES_PAGE_URL = "https://huggingface.co/rhasspy/piper-voices"

        /** sherpa-onnx's archives of them, ready to import (vits-piper-*.tar.bz2). */
        const val ARCHIVES_PAGE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models"
    }
}
