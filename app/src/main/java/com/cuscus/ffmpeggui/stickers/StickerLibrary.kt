package com.cuscus.ffmpeggui.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.stickers.StickerSignature
import com.cuscus.ffmpeggui.export.PublicCopies
import com.cuscus.ffmpeggui.preview.keyedBitmap
import com.cuscus.ffmpeggui.preview.smallPicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * The app's own gallery of stickers: subjects cut out of a picture (the Cut out
 * tool, corrected with the brushes or not) or lifted off a photo with a long
 * press, saved to be laid on any picture or video again. Each is a PNG with
 * its transparency, cut to the subject's box, in the app's private files
 * ([FOLDER]); with [copiesToPhone] on (Settings, off by default) a copy also
 * goes to Pictures/FFmpegGUI/Stickers, where the phone's gallery finds it
 * (through MediaStore: no permission needed).
 *
 * Each sticker has a record next to it (<id>.sig): the signature of what it
 * was made from (StickerSignature) and the SHA-256 of its own file, so that a
 * picture cut the same way is known to be there already ([savedAs]: the
 * editors' bookmark is filled) and is not saved twice.
 *
 * Deleting a sticker here takes it out of the gallery only: a project that
 * uses it keeps its own copy (ProjectStore copies the app's files into the
 * project), and a copy in Pictures stays the user's.
 */
class StickerLibrary(context: Context, private val copiesToPhone: () -> Boolean) {

    /** A saved sticker: its PNG, its size in pixels and when it was saved. */
    data class Sticker(val id: String, val file: File, val width: Int, val height: Int, val savedAt: Long) {
        val uri: Uri get() = Uri.fromFile(file)
    }

    /**
     * What a save gave: the sticker, the phone's folder its copy went to (null:
     * none), and whether it was there already (nothing new was written).
     */
    class Saved(val sticker: Sticker, val phoneFolder: String?, val alreadyThere: Boolean = false)

    /** What a sticker was made from (null: not known, saved before records) and its own file's SHA-256. */
    private class Record(val signature: String?, val fileSha: String)

    /** Nothing was left once the picture was cut: no sticker to save. */
    class NothingToSave : IOException("Nothing is left of the picture once it is cut")

    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _stickers = MutableStateFlow<List<Sticker>>(emptyList())
    private val records = HashMap<String, Record>()

    /** SHA-256 of the pictures already read, by uri and size: a picture is hashed once. */
    private val pictureShas = HashMap<String, String>()

    /** Every saved sticker, the newest first. */
    val stickers: StateFlow<List<Sticker>> = _stickers.asStateFlow()

    init {
        scope.launch { mutex.withLock { _stickers.value = read() } }
    }

    /**
     * Saves the picture at [source] as a sticker: decoded as the editors read it
     * (at most [MAX_SIDE] on its longer side), cut by [key] when there is one (a
     * subject's mask or a colour, its fill made transparent) after [colorMatrix]
     * (its own colours), then cut to what is left of it. Throws NothingToSave
     * when nothing is, IOException when it cannot be read or written.
     */
    suspend fun save(source: Uri, key: ColorKey? = null, colorMatrix: FloatArray? = null): Saved = withContext(Dispatchers.Default) {
        val signature = withContext(Dispatchers.IO) { signatureOf(source, key, colorMatrix) }
        mutex.withLock { find(source, signature, key, colorMatrix) }?.let { id ->
            _stickers.value.firstOrNull { it.id == id }?.let { return@withContext Saved(it, null, alreadyThere = true) }
        }
        val picture = withContext(Dispatchers.IO) { smallPicture(appContext, source, MAX_SIDE) } ?: throw IOException("Cannot read $source")
        val cut = if (key != null) keyedBitmap(picture, key.copy(background = null, backgroundOpacity = 1f), colorMatrix, keepAlpha = true) else picture
        val trimmed = trimmed(cut) ?: throw NothingToSave()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                folder.mkdirs()
                val id = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
                val file = File(folder, "$id.png")
                FileOutputStream(file).use { if (!trimmed.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not write the sticker") }
                val sticker = Sticker(id, file, trimmed.width, trimmed.height, file.lastModified())
                val fileSha = file.inputStream().use { StickerSignature.sha256(it) }
                File(folder, "$id.$RECORD").writeText(StickerSignature.record(signature, fileSha))
                records[id] = Record(signature, fileSha)
                _stickers.value = listOf(sticker) + _stickers.value
                val copied = if (copiesToPhone()) runCatching { PublicCopies.save(appContext, file, PHONE_SUBFOLDER) }.getOrNull() else null
                listOf(picture, cut, trimmed).distinct().forEach { if (!it.isRecycled) it.recycle() }
                Saved(sticker, copied)
            }
        }
    }

    /**
     * The id of the sticker the picture at [source], cut by [key] after
     * [colorMatrix], already is in the gallery; null when it is not. Reads the
     * picture (once: its SHA-256 is kept) and the key's mask.
     */
    suspend fun savedAs(source: Uri, key: ColorKey? = null, colorMatrix: FloatArray? = null): String? = withContext(Dispatchers.IO) {
        val signature = runCatching { signatureOf(source, key, colorMatrix) }.getOrNull() ?: return@withContext null
        mutex.withLock { find(source, signature, key, colorMatrix) }
    }

    /** Takes [id] out of the gallery (its copy in Pictures, if any, stays). */
    fun delete(id: String) {
        scope.launch {
            mutex.withLock {
                _stickers.value.firstOrNull { it.id == id }?.file?.delete()
                File(folder, "$id.$RECORD").delete()
                records.remove(id)
                _stickers.value = _stickers.value.filterNot { it.id == id }
            }
        }
    }

    /** Which sticker [signature] (of [source] cut by [key] after [colorMatrix]) already is; call with the lock held. */
    private fun find(source: Uri, signature: String, key: ColorKey?, colorMatrix: FloatArray?): String? {
        records.entries.firstOrNull { it.value.signature == signature }?.let { return it.key }
        // A sticker laid as it is (from the gallery, or the same lift twice): known by its own bytes.
        if (key == null && colorMatrix == null) {
            val pictureSha = synchronized(pictureShas) { pictureShas[shaKey(source)] } ?: return null
            records.entries.firstOrNull { it.value.fileSha == pictureSha }?.let { return it.key }
        }
        return null
    }

    /** The signature of [source] cut by [key] after [colorMatrix] (see StickerSignature). */
    private fun signatureOf(source: Uri, key: ColorKey?, colorMatrix: FloatArray?): String {
        val pictureSha = pictureSha(source)
        val cut = key?.copy(background = null, backgroundOpacity = 1f)
        val maskSha = cut?.takeIf { it.mode == KeyMode.SUBJECT }?.mask?.let { path ->
            File(path).takeIf { it.isFile }?.inputStream()?.use { StickerSignature.sha256(it) }
        }
        // The mask counts by its bytes, not its path.
        val keyText = cut?.copy(mask = null)?.toString()
        return StickerSignature.of(pictureSha, keyText, maskSha, colorMatrix)
    }

    /** SHA-256 of the picture at [uri], read once and kept (by uri and size). */
    private fun pictureSha(uri: Uri): String {
        val key = shaKey(uri)
        synchronized(pictureShas) { pictureShas[key]?.let { return it } }
        val sha = appContext.contentResolver.openInputStream(uri)?.use { StickerSignature.sha256(it) } ?: throw IOException("Cannot read $uri")
        synchronized(pictureShas) { pictureShas[key] = sha }
        return sha
    }

    /** What a picture's SHA-256 is kept by: its uri and, for a file, its size and time (a file may be written again). */
    private fun shaKey(uri: Uri): String {
        val file = uri.takeIf { it.scheme == "file" }?.path?.let(::File)
        return if (file != null) "$uri|${file.length()}|${file.lastModified()}" else uri.toString()
    }

    /** True when [uri] is one of the gallery's own files. */
    fun owns(uri: Uri?): Boolean = uri?.scheme == "file" && uri.path?.startsWith(folder.absolutePath + File.separator) == true

    private fun read(): List<Sticker> {
        val files = folder.listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) }.orEmpty()
        records.clear()
        return files.mapNotNull { file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@mapNotNull null
            val id = file.nameWithoutExtension
            // Its record; a sticker saved without one gets one (its signature not known).
            val recordFile = File(folder, "$id.$RECORD")
            val record = runCatching { StickerSignature.parseRecord(recordFile.readText()) }.getOrNull()
                ?.let { (signature, fileSha) -> Record(signature, fileSha) }
                ?: runCatching {
                    val fileSha = file.inputStream().use { StickerSignature.sha256(it) }
                    recordFile.writeText(StickerSignature.record(null, fileSha))
                    Record(null, fileSha)
                }.getOrNull()
            if (record != null) records[id] = record
            Sticker(id, file, bounds.outWidth, bounds.outHeight, file.lastModified())
        }.sortedByDescending { it.savedAt }
    }

    /** [bitmap] cut to the box of what is at least faintly there; null when nothing is. */
    private fun trimmed(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((pixels[row + x] ushr 24) >= MIN_ALPHA) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) return bitmap
        return Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    companion object {
        /** Inside the app's private files. */
        const val FOLDER = "sticker-library"

        /** Extension of a sticker's record file, next to its PNG. */
        private const val RECORD = "sig"

        /** Inside Pictures/FFmpegGUI, where the copies go. */
        const val PHONE_SUBFOLDER = "Stickers"

        /** Longest side a sticker is saved at. */
        private const val MAX_SIDE = 2048

        /** Below this alpha (of 255) a pixel is not part of the sticker's box. */
        private const val MIN_ALPHA = 8
    }
}
