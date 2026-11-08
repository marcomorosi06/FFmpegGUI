package com.cuscus.ffmpeggui.marking

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.cutout.CutoutModel
import com.cuscus.ffmpeggui.cutout.CutoutVariant
import com.cuscus.ffmpeggui.cutout.RefineModel
import com.cuscus.ffmpeggui.erase.EraseModel
import com.cuscus.ffmpeggui.erase.EraseVariant
import com.cuscus.ffmpeggui.speech.SpeechModel
import com.cuscus.ffmpeggui.speech.SpeechVariant
import com.cuscus.ffmpeggui.voice.StemModels
import java.io.File

/**
 * At every start, before any model store reads its files: when there are AI
 * models on the phone and the marks are not installed before them, every
 * model goes, whatever the app's version (EU AI Act, Article 50(2): no model
 * may make anything that cannot be marked). "Before them" is kept as the
 * time the marks were first installed ([since]); a model file older than
 * that came in without them. The user is told once what went and why
 * (ModelsClearedDialog), and what they made with the models stays.
 *
 * The same deletion runs when the user removes the marks ([clearAll]).
 */
object MarkingSweep {

    /** A kind of model that went, and the space it took. */
    data class Removed(@StringRes val label: Int, val bytes: Long)

    private class Kind(@StringRes val label: Int, val folders: List<String>, val prefs: List<String>)

    /** Every model the app keeps, by kind: their folders (in the app's files) and settings. */
    private val KINDS: List<Kind> by lazy {
        fun of(vararg models: SpeechModel) = models.flatMap { listOf(it.folder, File(File(it.folder).parent ?: "", it.tempFolder).path) }
        listOf(
            Kind(R.string.smart_section_speech, of(SpeechModel.of(SpeechVariant.INT8), SpeechModel.of(SpeechVariant.SMOOTHQUANT)) + "speech",
                listOf(SpeechModel.of(SpeechVariant.INT8).prefs, SpeechModel.of(SpeechVariant.SMOOTHQUANT).prefs)),
            // TranslationModelStore.ROOT: every pair, its temporary import included.
            Kind(R.string.translate_section, listOf("translate"), emptyList()),
            Kind(R.string.smart_section_cutout,
                CutoutVariant.entries.map { CutoutModel.of(it).folder } + of(RefineModel.FILES) + "refine",
                CutoutVariant.entries.map { CutoutModel.of(it).prefs } + RefineModel.FILES.prefs),
            Kind(R.string.erase_section, EraseVariant.entries.flatMap { of(EraseModel.of(it)) } + "erase", EraseVariant.entries.map { EraseModel.of(it).prefs }),
            // Bandit and Spleeter's five stems under "stems"; "voice" held Spleeter's two stems (retired).
            Kind(R.string.voice_section, of(StemModels.SCENE, StemModels.INSTRUMENTS) + "stems" + "voice",
                listOf(StemModels.SCENE.prefs, StemModels.INSTRUMENTS.prefs, "voice_model")),
            // DubModelStore and PiperVoiceStore: Kokoro, the Piper voices, eSpeak NG's data, under "dub"
            // (the dubs made are in "dubs": they stay). DubModelStore.PREFS.
            Kind(R.string.dub_section, listOf("dub"), listOf("dub_model")),
        )
    }

    private const val PREFS = "ai_markers_sweep"
    private const val KEY_SINCE = "since"
    private const val KEY_PENDING = "pending"

    /**
     * Deletes every model when [markersReady] is false or a model predates
     * the marks; returns what went now. Cheap when there is nothing to do.
     */
    fun sweep(context: Context, markersReady: Boolean): List<Removed> {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var since = prefs.getLong(KEY_SINCE, 0L)
        if (markersReady && since == 0L) {
            // The marks are here and no time was kept (just installed): from now on.
            since = System.currentTimeMillis()
            prefs.edit().putLong(KEY_SINCE, since).apply()
        }
        if (!markersReady) prefs.edit().remove(KEY_SINCE).apply()
        val models = present(app)
        if (models.isEmpty()) return emptyList()
        val before = models.any { (_, files) -> files.any { it.lastModified() < since - GRACE_MS } }
        if (markersReady && !before) return emptyList()
        Log.w(TAG, if (markersReady) "Models older than the AI marks: all of them deleted" else "AI models without the AI marks: all of them deleted")
        return delete(app)
    }

    /** The marks were just installed: models imported from now on come after them. */
    fun markersInstalled(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_SINCE, 0L) == 0L) prefs.edit().putLong(KEY_SINCE, System.currentTimeMillis()).apply()
    }

    /** The marks were removed: when they come back, they count from then. */
    fun markersRemoved(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SINCE).apply()
    }

    /** Every model goes (the user removes the marks); returns what went. */
    fun clearAll(context: Context): List<Removed> = delete(context.applicationContext)

    /** What went and was not yet shown to the user. */
    fun pending(context: Context): List<Removed> {
        val text = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING, null) ?: return emptyList()
        return text.lineSequence().mapNotNull { line ->
            val index = line.substringBefore('\t').toIntOrNull() ?: return@mapNotNull null
            val bytes = line.substringAfter('\t', "0").toLongOrNull() ?: 0L
            KINDS.getOrNull(index)?.let { Removed(it.label, bytes) }
        }.toList()
    }

    /** The user has seen it. */
    fun acknowledge(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PENDING).apply()
    }

    private fun present(app: Context): List<Pair<Kind, List<File>>> = KINDS.mapNotNull { kind ->
        val files = kind.folders.map { File(app.filesDir, it) }.filter { it.exists() }
            .flatMap { folder -> folder.walkBottomUp().filter { it.isFile }.toList() }
        if (files.isEmpty()) null else kind to files
    }

    private fun delete(app: Context): List<Removed> {
        val removed = ArrayList<Pair<Int, Long>>()
        for ((index, kind) in KINDS.withIndex()) {
            var bytes = 0L
            for (name in kind.folders) {
                val folder = File(app.filesDir, name)
                if (!folder.exists()) continue
                bytes += folder.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                if (!folder.deleteRecursively()) Log.w(TAG, "Could not delete all of $name: tried again at the next start")
            }
            kind.prefs.forEach { app.deleteSharedPreferences(it) }
            if (bytes > 0L) removed += index to bytes
        }
        if (removed.isNotEmpty()) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val pending = pendingIndexed(prefs.getString(KEY_PENDING, null)).toMutableMap()
            for ((i, b) in removed) pending[i] = (pending[i] ?: 0L) + b
            prefs.edit().putString(KEY_PENDING, pending.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
        }
        return removed.map { (i, b) -> Removed(KINDS[i].label, b) }
    }

    private fun pendingIndexed(text: String?): Map<Int, Long> = text?.lineSequence()?.mapNotNull { line ->
        val i = line.substringBefore('\t').toIntOrNull() ?: return@mapNotNull null
        i to (line.substringAfter('\t', "0").toLongOrNull() ?: 0L)
    }?.toMap().orEmpty()

    /** A file written in the same second the marks were kept is not "before" them. */
    private const val GRACE_MS = 2_000L
    private const val TAG = "MarkingSweep"
}
