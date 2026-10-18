package com.cuscus.ffmpeggui.export

import com.cuscus.ffmpeggui.core.engine.ConversionStage

/**
 * What an export tells the system while it runs: a notification with its
 * progress (a Live Update where Android offers them, see
 * [AndroidExportNotifier]), and its outcome once it is over. The calls may come
 * from any thread.
 */
interface ExportNotifier {
    /** An export of [name] starts; the notification's cancel button calls [onCancel]. */
    fun started(name: String, onCancel: () -> Unit)

    /** It moved on to [stage]. */
    fun stage(stage: ConversionStage)

    /** It is [fraction] (0..1) of the way through the encoding. */
    fun progress(fraction: Float)

    /** It is over. */
    fun finished(result: ExportResult)

    /** The app is on screen again: a result notification has done its job. */
    fun clearResult()

    companion object {
        /** Tells nothing to anyone. */
        val NONE: ExportNotifier = object : ExportNotifier {
            override fun started(name: String, onCancel: () -> Unit) = Unit
            override fun stage(stage: ConversionStage) = Unit
            override fun progress(fraction: Float) = Unit
            override fun finished(result: ExportResult) = Unit
            override fun clearResult() = Unit
        }
    }
}

/** How an export ended. */
sealed interface ExportResult {
    /** The file at [path] was made (a zip, for an output split into parts or frames). */
    data class Saved(val path: String) : ExportResult

    data object Failed : ExportResult

    data object Cancelled : ExportResult
}
