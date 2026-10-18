package com.cuscus.ffmpeggui.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.engine.ConversionStage
import com.cuscus.ffmpeggui.work.LiveProgress
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * [ExportNotifier] on the system's notifications, with [ExportService] keeping
 * the app running (and its notification shown) while an export runs:
 *
 *  - Android 16 and later: a Live Update, unless turned off in the settings
 *    (then the same notification, not promoted), as all the app's long work
 *    looks (see work.LiveProgress): the teal bar with the film riding it, the
 *    percentage in the status bar chip, the time left counting down;
 *  - before that: an ordinary ongoing notification with a progress bar.
 *
 * Both show the stage, the percentage and the time left, a cancel button, and
 * open the app when touched. Once over, a short notification says how it went,
 * only when the app is not on screen ([isAppVisible]).
 */
class AndroidExportNotifier(
    context: Context,
    private val isAppVisible: () -> Boolean,
    /** Whether the user wants Live Updates (the settings); off, the notification is not promoted. */
    private val liveUpdates: () -> Boolean = { true },
) : ExportNotifier {

    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    /** What the notification shows; null when no export runs. */
    private class Progress(
        val name: String,
        val stage: ConversionStage,
        val fraction: Float?,
        /** When the encoding started (elapsed realtime), for the time left. */
        val encodingSince: Long,
    )

    @Volatile private var progress: Progress? = null
    @Volatile private var onCancel: (() -> Unit)? = null
    private var lastPostMs = 0L
    private var lastPercent = -1
    @Volatile private var shownEndsAt: Long? = null

    override fun started(name: String, onCancel: () -> Unit) {
        ensureChannels()
        manager.cancel(RESULT_ID)
        this.onCancel = onCancel
        progress = Progress(name, ConversionStage.PREPARING, null, 0L)
        lastPercent = -1
        shownEndsAt = null
        // The service shows the notification as its own and keeps the app alive meanwhile.
        try {
            app.startForegroundService(Intent(app, ExportService::class.java))
        } catch (e: RuntimeException) {
            // Not allowed to start now (the app went to the background): a plain notification then.
            Log.w(TAG, "Export service not started: ${e.message}")
            post(force = true)
        }
    }

    override fun stage(stage: ConversionStage) {
        val current = progress ?: return
        progress = Progress(
            current.name,
            stage,
            null,
            if (stage == ConversionStage.ENCODING) SystemClock.elapsedRealtime() else current.encodingSince,
        )
        post(force = true)
    }

    override fun progress(fraction: Float) {
        val current = progress ?: return
        progress = Progress(current.name, current.stage, fraction.coerceIn(0f, 1f), current.encodingSince)
        post(force = false)
    }

    override fun finished(result: ExportResult) {
        val name = progress?.name ?: return
        progress = null
        onCancel = null
        ExportService.stop(app)
        manager.cancel(PROGRESS_ID)
        if (isAppVisible()) return
        when (result) {
            is ExportResult.Saved -> postResult(
                R.drawable.ic_notification_done,
                app.getString(R.string.export_done_title),
                app.getString(R.string.export_done_text, File(result.path).name.ifBlank { name }),
            )
            ExportResult.Failed -> postResult(
                R.drawable.ic_notification_export,
                app.getString(R.string.export_failed_title),
                app.getString(R.string.export_failed_text, name),
            )
            ExportResult.Cancelled -> Unit
        }
    }

    override fun clearResult() {
        manager.cancel(RESULT_ID)
    }

    /** The cancel button was touched. */
    internal fun cancelRequested() {
        onCancel?.invoke()
    }

    /** The notification of the export running now, or null when none runs. */
    internal fun progressNotification(): Notification? = progress?.let(::build)

    /** Shows the progress again: at once with [force], else at most a few times a second. */
    private fun post(force: Boolean) {
        val current = progress ?: return
        val now = SystemClock.elapsedRealtime()
        val percent = current.fraction?.let { (it * 100).roundToInt() } ?: -1
        if (!force && (percent == lastPercent || now - lastPostMs < MIN_UPDATE_MS)) return
        lastPostMs = now
        lastPercent = percent
        try {
            manager.notify(PROGRESS_ID, build(current))
        } catch (e: SecurityException) {
            // Notifications refused: the export goes on regardless.
        }
    }

    private fun build(current: Progress): Notification {
        val stage = app.getString(
            when (current.stage) {
                ConversionStage.PREPARING -> R.string.export_stage_preparing
                ConversionStage.ANALYZING -> R.string.export_stage_analyzing
                ConversionStage.ENCODING -> R.string.export_stage_encoding
                ConversionStage.PACKING -> R.string.export_stage_packing
            },
        )
        return LiveProgress.build(
            app,
            LiveProgress.Spec(
                channel = PROGRESS_CHANNEL,
                icon = R.drawable.ic_notification_export,
                title = app.getString(R.string.export_notification_title, current.name),
                text = stage,
                fraction = current.fraction,
                endsAtMs = endsAt(current),
                cancel = ExportService.cancelIntent(app),
                cancelLabel = app.getString(R.string.export_action_cancel),
                open = LiveProgress.openApp(app),
                promote = liveUpdates(),
            ),
        )
    }

    /**
     * When the export should be over (wall clock), once the encoding has run long
     * enough to tell; steadied, so that the countdown does not jump at every update.
     */
    private fun endsAt(current: Progress): Long? {
        val fraction = current.fraction
        if (current.stage != ConversionStage.ENCODING || fraction == null || fraction < MIN_FRACTION_FOR_ETA || fraction >= 1f) {
            shownEndsAt = null
            return null
        }
        val elapsed = SystemClock.elapsedRealtime() - current.encodingSince
        if (elapsed < MIN_ELAPSED_FOR_ETA_MS) return null
        val now = System.currentTimeMillis()
        val estimate = now + (elapsed * (1f - fraction) / fraction).toLong()
        val shown = shownEndsAt
        if (shown == null || abs(estimate - shown) > maxOf(ETA_SLACK_MS, (shown - now) / 10)) shownEndsAt = estimate
        return shownEndsAt
    }

    private fun postResult(icon: Int, title: String, text: String) {
        val notification = Notification.Builder(app, RESULT_CHANNEL)
            .setSmallIcon(icon)
            .setColor(LiveProgress.ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(LiveProgress.openApp(app))
            .build()
        try {
            manager.notify(RESULT_ID, notification)
        } catch (e: SecurityException) {
            // Notifications refused.
        }
    }

    private fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(PROGRESS_CHANNEL, app.getString(R.string.export_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = app.getString(R.string.export_channel_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL, app.getString(R.string.export_result_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = app.getString(R.string.export_result_channel_description)
            },
        )
    }

    internal companion object {
        const val TAG = "ExportNotifier"
        const val PROGRESS_ID = 4101
        const val RESULT_ID = 4102
        const val PROGRESS_CHANNEL = "export_progress"
        const val RESULT_CHANNEL = "export_result"

        /** The system drops updates that come too often; a few a second are plenty. */
        const val MIN_UPDATE_MS = 400L
        const val MIN_FRACTION_FOR_ETA = 0.03f
        const val MIN_ELAPSED_FOR_ETA_MS = 3_000L
        const val ETA_SLACK_MS = 5_000L
    }
}
