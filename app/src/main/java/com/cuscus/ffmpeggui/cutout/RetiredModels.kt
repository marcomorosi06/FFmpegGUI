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
     * [replacement]'s name.
     */
    class Retired(val id: String, val name: String, val folder: String, val prefs: String, val replacement: String, val reason: Reason)

    /** Why it was retired, for the words the dialog says. */
    enum class Reason {
        /** A newer model does the same work better. */
        SUPERSEDED,
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
    )

    private const val PREFS = "retired_models"
    private const val KEY_PENDING = "pending"

    /** Deletes every retired model still on the phone; returns what it deleted now. */
    fun sweep(context: Context): List<Removed> {
        val app = context.applicationContext
        val removed = ArrayList<Removed>()
        for (retired in ALL) {
            val folder = File(app.filesDir, retired.folder)
            if (!folder.exists()) continue
            val bytes = folder.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            if (folder.deleteRecursively()) {
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
