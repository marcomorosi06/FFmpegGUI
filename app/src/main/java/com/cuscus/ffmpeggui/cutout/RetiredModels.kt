package com.cuscus.ffmpeggui.cutout

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Models the app no longer uses, and what to do when one is still on the
 * phone: delete it (it is never loaded again, so it only takes space) and
 * say so once, with what took its place (see RetiredModelsDialog).
 *
 * [sweep] runs at every start, off the main thread, and is quick when there
 * is nothing to delete (one check per retired model). What it deleted waits
 * in [PREFS] until the user has seen it ([acknowledge]), so the notice is not
 * lost if the app is closed before it shows.
 */
object RetiredModels {

    /**
     * A model the app used before: its [name], the [folder] (inside the app's
     * private files) and the settings file ([prefs]) it was kept in, and the
     * [replacement]'s name, and the [area] of Smart features where that is set up.
     */
    class Retired(
        val id: String,
        val name: String,
        val folder: String,
        val prefs: String,
        val replacement: String,
        val reason: Reason,
        val area: Area = Area.CUTOUT,
        val leftovers: List<String> = emptyList(),
    )

    /** Where in Smart features the replacement is set up: what "Set up" in the dialog opens. */
    enum class Area { CUTOUT, VOICE }

    /** Why it was retired, for the words the dialog says. */
    enum class Reason {
        /** A newer model does the same work better. */
        SUPERSEDED,

        /** Newer models do the same work better, and more of it (a voice from the rest: every stem). */
        EXTENDED,

        /** The same model, in a file that loads and runs quicker. */
        QUICKER,
    }

    /** What [sweep] deleted: which model, and how much space it gave back. */
    data class Removed(val retired: Retired, val bytes: Long)

    /** Every model retired so far. Add, never take away: an old phone may still hold any of them. */
    val ALL: List<Retired> = listOf(
        // SlimSAM (the correction tool's model until EdgeTAM took its place).
        Retired(
            id = "slimsam-77",
            name = "SlimSAM",
            folder = "refine/slimsam-77",
            prefs = "refine_model",
            replacement = RefineModel.NAME,
            reason = Reason.SUPERSEDED,
        ),
        // Spleeter's two stems as sherpa-onnx's int8 files (the voice separation until Bandit v2,
        // with Spleeter's five stems for the instruments, took its place). What it made stays: the
        // voices already set apart are results, kept in "voice-stems", not the model.
        Retired(
            id = "spleeter-2stems-int8",
            name = "Spleeter 2stems",
            folder = "voice/spleeter-2stems-int8",
            prefs = "voice_model",
            replacement = com.cuscus.ffmpeggui.voice.StemModels.BANDIT_NAME,
            reason = Reason.EXTENDED,
            area = Area.VOICE,
            leftovers = listOf("voice/importing-voice"),
        ),
        // Bandit v2's first file (bandit-onnx-1): the same model, built band by band, that took
        // three times as long to load and gave its masks as doubles. bandit-onnx-2 is the same
        // weights, put together in one go: imported again once, from its own release.
        Retired(
            id = "bandit-onnx-1",
            name = "Bandit v2 (first file)",
            folder = "stems/bandit-v2",
            prefs = "stems_bandit",
            replacement = com.cuscus.ffmpeggui.voice.StemModels.BANDIT_NAME,
            reason = Reason.QUICKER,
            area = Area.VOICE,
            leftovers = listOf("stems/importing-bandit"),
        ),
    )

    private const val PREFS = "retired_models"
    private const val KEY_PENDING = "pending"

    /** Deletes every retired model still on the phone; returns what it deleted now. */
    fun sweep(context: Context): List<Removed> {
        val app = context.applicationContext
        val removed = ArrayList<Removed>()
        for (retired in ALL) {
            val folder = File(app.filesDir, retired.folder)
            // A half-finished import of it, next to it, goes without a word.
            retired.leftovers.forEach { File(app.filesDir, it).deleteRecursively() }
            if (!folder.exists()) continue
            val bytes = folder.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            if (folder.deleteRecursively()) {
                // Its parent too, when nothing else is left in it (a delete of a folder that is not empty fails).
                folder.parentFile?.takeIf { it != app.filesDir }?.delete()
                app.deleteSharedPreferences(retired.prefs)
                Log.i(TAG, "Deleted ${retired.name}, retired: ${bytes} bytes freed")
                removed += Removed(retired, bytes)
            } else {
                Log.w(TAG, "Could not delete all of ${retired.name} in ${folder.path}: tried again at the next start")
            }
        }
        if (removed.isNotEmpty()) {
            val pending = pending(app).associateBy { it.retired.id }.toMutableMap()
            for (r in removed) pending[r.retired.id] = Removed(r.retired, (pending[r.retired.id]?.bytes ?: 0L) + r.bytes)
            save(app, pending.values.toList())
        }
        return removed
    }

    /** What was deleted and not yet shown to the user. */
    fun pending(context: Context): List<Removed> {
        val text = prefs(context).getString(KEY_PENDING, null) ?: return emptyList()
        return text.lineSequence().mapNotNull { line ->
            val id = line.substringBefore('\t')
            val bytes = line.substringAfter('\t', "0").toLongOrNull() ?: 0L
            ALL.firstOrNull { it.id == id }?.let { Removed(it, bytes) }
        }.toList()
    }

    /** The user has seen the notice: it is not shown again. */
    fun acknowledge(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).apply()
    }

    private fun save(context: Context, removed: List<Removed>) {
        prefs(context).edit().putString(KEY_PENDING, removed.joinToString("\n") { "${it.retired.id}\t${it.bytes}" }).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val TAG = "RetiredModels"
}
