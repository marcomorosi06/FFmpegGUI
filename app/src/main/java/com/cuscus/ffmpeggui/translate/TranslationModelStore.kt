package com.cuscus.ffmpeggui.translate

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.JsonText
import com.cuscus.ffmpeggui.core.project.jsonObject
import com.cuscus.ffmpeggui.core.translate.Languages
import com.cuscus.ffmpeggui.core.translate.MarianConfig
import com.cuscus.ffmpeggui.core.translate.MarianTokenizer
import com.cuscus.ffmpeggui.core.translate.SentencePieceModel
import com.cuscus.ffmpeggui.speech.ModelSlot
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
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The translation models on this phone (see OpusMt), each in a folder of its
 * own in the app's private files with what it is (meta.json): imported from
 * the five files the user downloaded, checked on the way (its settings are a
 * Marian model's, its vocabulary and SentencePiece model read, its two ONNX
 * files open), removed on request.
 *
 * An import is put together in a folder of its own and takes the model's
 * place only once every file is in and checked: a failed or stopped import
 * leaves what was there.
 */
class TranslationModelStore(context: Context) {

    /** Where an import is. */
    sealed interface Importing {
        data object Idle : Importing
        /** [fraction] of the files copied; [file] the one being copied (then checked). */
        data class Working(val fraction: Float, val file: String, val checking: Boolean = false) : Importing
        /** [missing]: the kinds of file not among those picked; [detail] what else went wrong. */
        data class Failed(val problem: Problem, val missing: List<OpusMt.Kind> = emptyList(), val detail: String? = null) : Importing
        /** [model] was imported (or replaced the one of the same name). */
        data class Done(val model: InstalledModel) : Importing
    }

    enum class Problem { MISSING_FILES, NOT_MARIAN, NO_LANGUAGES, BROKEN, NO_SPACE, UNREADABLE, HASH_MISMATCH }

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, ROOT)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The check of an imported model: it opens and closes it, so there is nothing to let go of. */
    private val check = ModelSlot("Translation model check") {}
    private var job: Job? = null

    private val _models = MutableStateFlow(readAll())

    /** The models on the phone, by name. */
    val models: StateFlow<List<InstalledModel>> = _models.asStateFlow()

    private val _importing = MutableStateFlow<Importing>(Importing.Idle)
    val importing: StateFlow<Importing> = _importing.asStateFlow()

    /** The folder of the model [id]. */
    fun folder(id: String): File = File(root, id)

    /** The file of [kind] of the model [id]. */
    fun file(id: String, kind: OpusMt.Kind): File = File(folder(id), kind.fileName)

    /**
     * Imports a model from the files the user picked ([uris]); [repo] is its
     * name on Hugging Face when the user said it (else its settings say).
     */
    @Synchronized
    fun import(uris: List<Uri>, repo: String?) {
        if (job?.isActive == true) return
        job = scope.launch {
            _importing.value = Importing.Working(0f, "")
            _importing.value = try {
                importNow(uris, repo)
            } catch (e: CancellationException) {
                _importing.value = Importing.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                Importing.Failed(if (isNoSpace(e)) Problem.NO_SPACE else Problem.UNREADABLE, detail = e.message)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** The import's outcome seen: back to idle. */
    fun dismiss() {
        if (_importing.value !is Importing.Working) _importing.value = Importing.Idle
    }

    /** Removes the model [id]. */
    fun delete(id: String) {
        folder(id).deleteRecursively()
        _models.value = readAll()
    }

    /** Hashes the files of the model [id] again: true when they are as imported. */
    suspend fun verify(id: String): Boolean {
        val model = _models.value.firstOrNull { it.id == id } ?: return false
        return OpusMt.Kind.entries.all { kind ->
            val f = file(id, kind)
            val recorded = model.hashes[kind.fileName]
            if (recorded == null) kind.optional && !f.isFile else f.isFile && recorded == f.inputStream().use { hash(it) { _, _ -> } }
        }
    }

    /**
     * Keeps translated subtitles [text] as a file named [name] in the app's
     * files (a project copies it into its own when it is kept); its URI.
     * Files made more than a month ago are removed meanwhile.
     */
    fun saveSubtitles(name: String, text: String): Uri {
        val dir = File(appContext.filesDir, OUTPUT).apply { mkdirs() }
        val old = System.currentTimeMillis() - OUTPUT_KEEP_MS
        dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
        val file = File(dir, "${System.currentTimeMillis()}-$safe")
        file.writeText(text)
        return Uri.fromFile(file)
    }

    // ---------------------------------------------------------------------

    private suspend fun importNow(uris: List<Uri>, repoGiven: String?): Importing {
        val picked = uris.mapNotNull { uri -> displayName(uri)?.let { name -> OpusMt.Kind.of(name)?.let { it to uri } } }.toMap()
        val missing = OpusMt.Kind.entries.filter { !it.optional && it !in picked }
        if (missing.isNotEmpty()) return Importing.Failed(Problem.MISSING_FILES, missing)
        root.mkdirs()
        val temp = File(root, TEMP)
        temp.deleteRecursively()
        temp.mkdirs()
        try {
            val sizes = picked.mapValues { (_, uri) -> size(uri) ?: 0L }
            val total = sizes.values.sum().coerceAtLeast(1L)
            var done = 0L
            val hashes = HashMap<String, String>()
            for (kind in OpusMt.Kind.entries) {
                val uri = picked[kind] ?: continue
                val target = File(temp, kind.fileName)
                val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                hashes[kind.fileName] = input.use { stream ->
                    target.outputStream().use { out ->
                        hash(stream) { bytes, n ->
                            out.write(bytes, 0, n)
                            done += n
                            _importing.value = Importing.Working((done.toFloat() / total).coerceIn(0f, 1f), kind.fileName)
                        }
                    }
                }
            }
            // What the files are: a Marian model, its languages.
            _importing.value = Importing.Working(1f, OpusMt.Kind.CONFIG.fileName, checking = true)
            val vocab = runCatching { MarianTokenizer.vocab(File(temp, OpusMt.Kind.VOCAB.fileName).readText()) }
                .getOrElse { return Importing.Failed(Problem.BROKEN, detail = it.message) }
            val configFile = File(temp, OpusMt.Kind.CONFIG.fileName)
            val config = if (configFile.isFile) {
                runCatching { MarianConfig.parse(configFile.readText()) }
                    .getOrElse { return Importing.Failed(Problem.NOT_MARIAN, detail = it.message) }
            } else {
                MarianConfig.fromVocab(vocab)
            }
            val tokenizer = runCatching {
                MarianTokenizer(SentencePieceModel.parse(File(temp, OpusMt.Kind.SOURCE.fileName).readBytes()), vocab, config)
            }.getOrElse { return Importing.Failed(Problem.BROKEN, detail = it.message) }
            val repo = repoGiven?.trim()?.takeIf { Languages.pairOf(it) != null }
                ?: config.name?.takeIf { Languages.pairOf(it) != null }?.let { "Xenova/" + it.substringAfterLast('/') }
                ?: return Importing.Failed(Problem.NO_LANGUAGES)
            val (from, to) = Languages.pairOf(repo) ?: return Importing.Failed(Problem.NO_LANGUAGES)
            val tokens = tokenizer.targetTokens
            val targets = when {
                tokens.isNotEmpty() -> tokens.keys
                !Languages.isGroup(to) -> setOf(Languages.normalize(to))
                else -> return Importing.Failed(Problem.NO_LANGUAGES)
            }
            val sources = if (Languages.isGroup(from)) emptySet() else setOf(Languages.normalize(from))
            // Both networks must open (a broken file is found now, not while translating).
            _importing.value = Importing.Working(1f, OpusMt.Kind.ENCODER.fileName, checking = true)
            opens(File(temp, OpusMt.Kind.ENCODER.fileName))?.let { return Importing.Failed(Problem.BROKEN, detail = it) }
            _importing.value = Importing.Working(1f, OpusMt.Kind.DECODER.fileName, checking = true)
            opens(File(temp, OpusMt.Kind.DECODER.fileName))?.let { return Importing.Failed(Problem.BROKEN, detail = it) }
            currentCoroutineContext().ensureActive()
            val id = repo.replace('/', '_').replace(Regex("[^A-Za-z0-9_.-]"), "")
            val model = InstalledModel(
                id = id,
                repo = repo,
                sources = sources,
                targets = targets.toSet(),
                needsToken = tokens.isNotEmpty(),
                bytes = temp.listFiles()?.sumOf { it.length() } ?: 0L,
                hashes = hashes,
                importedAt = System.currentTimeMillis(),
            )
            File(temp, META).writeText(JsonText.write(write(model)))
            val final = folder(id)
            final.deleteRecursively()
            if (!temp.renameTo(final)) throw IOException("Could not move the model into place")
            _models.value = readAll()
            return Importing.Done(model)
        } finally {
            temp.deleteRecursively()
        }
    }

    /**
     * Null when [file] opens as an ONNX model; what went wrong otherwise. Opened
     * through the ModelGate like any model, so that it never shares the memory
     * with one at work.
     */
    private suspend fun opens(file: File): String? = check.run {
        try {
            val environment = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                environment.createSession(file.absolutePath, options).use { session ->
                    if (session.inputNames.isEmpty()) "no inputs" else null
                }
            }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        } catch (e: OutOfMemoryError) {
            "out of memory"
        }
    }

    private suspend fun hash(input: java.io.InputStream, sink: (ByteArray, Int) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var count = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            sink(buffer, n)
            digest.update(buffer, 0, n)
            if (++count % 16 == 0) currentCoroutineContext().ensureActive()
        }
        return SpeechModelStore.hex(digest.digest())
    }

    private fun readAll(): List<InstalledModel> =
        root.listFiles()?.filter { it.isDirectory && it.name != TEMP }?.mapNotNull { dir ->
            val meta = File(dir, META)
            if (!meta.isFile || OpusMt.Kind.entries.any { !it.optional && !File(dir, it.fileName).isFile }) return@mapNotNull null
            runCatching { read(JsonText.parse(meta.readText()) as Json.Obj, dir.name) }.getOrNull()
        }?.sortedBy { it.repo }.orEmpty()

    private fun write(m: InstalledModel): Json.Obj = jsonObject {
        put("repo", m.repo)
        putList("sources", m.sources.sorted()) { Json.of(it) }
        putList("targets", m.targets.sorted()) { Json.of(it) }
        put("needsToken", m.needsToken)
        put("bytes", m.bytes)
        put("hashes", Json.Obj(m.hashes.mapValues { Json.of(it.value) }))
        put("importedAt", m.importedAt)
    }

    private fun read(o: Json.Obj, id: String): InstalledModel = InstalledModel(
        id = id,
        repo = o.string("repo", id),
        sources = o.list("sources", emptyList()) { (it as? Json.Str)?.value }.toSet(),
        targets = o.list("targets", emptyList()) { (it as? Json.Str)?.value }.toSet(),
        needsToken = o.bool("needsToken", false),
        bytes = o.long("bytes", 0L),
        hashes = o.obj("hashes")?.fields?.mapNotNull { (k, v) -> (v as? Json.Str)?.value?.let { k to it } }?.toMap().orEmpty(),
        importedAt = o.long("importedAt", 0L),
    )

    private fun displayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun size(uri: Uri): Long? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
        }
    } catch (e: Exception) {
        null
    }

    private fun isNoSpace(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { (it.message ?: "").contains("ENOSPC") || (it.message ?: "").contains("No space left") }

    private companion object {
        const val TAG = "TranslationModels"
        const val ROOT = "translate"
        const val TEMP = ".importing"
        const val META = "meta.json"
        const val OUTPUT = "translated-subtitles"
        const val OUTPUT_KEEP_MS = 30L * 24 * 60 * 60 * 1000
        const val BUFFER = 256 * 1024
    }
}
