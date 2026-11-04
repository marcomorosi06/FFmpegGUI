package com.cuscus.ffmpeggui.marking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.marking.PcmWav
import com.cuscus.ffmpeggui.core.marking.SpeechMarking
import com.cuscus.ffmpeggui.core.provenance.AiDisclosure
import com.cuscus.ffmpeggui.core.provenance.PngXmp
import com.cuscus.ffmpeggui.core.provenance.RiffXmp
import com.cuscus.ffmpeggui.core.provenance.Xmp
import com.cuscus.ffmpeggui.core.voice.WavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Right before an export: every dub heard and every picture a model changed
 * that does not carry its invisible AI mark yet (made before the marks, or
 * brought in from elsewhere) gets it, in a copy the export uses instead
 * (the project keeps its file). So that nothing a model made leaves the app
 * without its mark, whatever its age (EU AI Act, Article 50(2)).
 *
 * Each file is looked at once per run of the app ([known]). Fails (and the
 * export with it) rather than let an unmarked one through.
 */
class MarkGuard(context: Context, private val marker: MarkEngine) {

    private val app = context.applicationContext
    private val folder = File(app.filesDir, FOLDER)

    /** What each file looked at is to be used as: itself, or its marked copy. */
    private val known = ConcurrentHashMap<String, Uri>()

    /** The dub at [uri] (a WAV), marked: itself when it already is. */
    suspend fun speech(uri: Uri): Uri = withContext(Dispatchers.IO) {
        known[uri.toString()]?.let { return@withContext it }
        if (!marker.ready) throw MarkEngine.Missing()
        val local = local(uri, "wav")
        try {
            val sound = PcmWav.read(local)
            val runs = SpeechMarking.runs(sound.samples, sound.rate)
            val lines = runs.map { sound.samples.copyOfRange(it.first, it.last + 1) }
            val verdict = marker.speech(lines, sound.rate)
            // Marked already: as it is. (Too little speech to tell: it is marked all the same.)
            if (verdict.marked) return@withContext uri.also { known[uri.toString()] = it }
            val marked = marker.markSpeech(lines, sound.rate)
            val out = sound.samples.copyOf()
            for ((i, run) in runs.withIndex()) System.arraycopy(marked[i], 0, out, run.first, marked[i].size)
            folder.mkdirs()
            val target = File(folder, "voice-${UUID.randomUUID().toString().take(8)}.wav")
            WavWriter(target, 1, sound.rate, comment = AiDisclosure.SPEECH_TEXT).use { it.write(out, out.size) }
            if (!RiffXmp.write(target, Xmp.packet(AiDisclosure(speech = true), AiDisclosure.TRAINED))) {
                target.delete()
                throw IOException("Could not mark the voice as made with AI")
            }
            Log.i(TAG, "A voice made before the AI marks got them for this export")
            Uri.fromFile(target).also { known[uri.toString()] = it }
        } finally {
            if (local.parentFile?.name == SCRATCH) local.delete()
        }
    }

    /** The picture at [uri] a model changed, marked: itself when it already is. */
    suspend fun picture(uri: Uri): Uri = withContext(Dispatchers.IO) {
        known[uri.toString()]?.let { return@withContext it }
        if (!marker.ready) throw MarkEngine.Missing()
        val picture = try {
            app.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                })
            }
        } catch (e: OutOfMemoryError) {
            null
        } ?: throw IOException("The picture cannot be read")
        try {
            if (marker.pictureMarked(picture)) return@withContext uri.also { known[uri.toString()] = it }
            marker.markPicture(picture)
            folder.mkdirs()
            val target = File(folder, "picture-${UUID.randomUUID().toString().take(8)}.png")
            FileOutputStream(target).use { if (!picture.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not write the picture") }
            if (!PngXmp.write(target, Xmp.packet(AiDisclosure(pictures = true)))) {
                target.delete()
                throw IOException("Could not mark the picture as changed with AI")
            }
            Log.i(TAG, "A picture changed before the AI marks got them for this export")
            Uri.fromFile(target).also { known[uri.toString()] = it }
        } finally {
            picture.recycle()
        }
    }

    /** [uri] as a file to read: itself when it is one, else a copy in the cache. */
    private fun local(uri: Uri, extension: String): File {
        if (uri.scheme == "file") uri.path?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        val scratch = File(app.cacheDir, SCRATCH).apply { mkdirs() }
        val copy = File(scratch, "${UUID.randomUUID()}.$extension")
        val input = app.contentResolver.openInputStream(uri) ?: throw IOException("The file cannot be read")
        input.use { source -> copy.outputStream().use { source.copyTo(it) } }
        return copy
    }

    private companion object {
        const val TAG = "MarkGuard"
        const val FOLDER = "ai-marked"
        const val SCRATCH = "ai-marking"
    }
}
