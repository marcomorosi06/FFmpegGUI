package com.cuscus.ffmpeggui.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.cuscus.ffmpeggui.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Long work other than exports, shown in the notifications while it runs (a
 * Live Update on Android 16, see [LiveProgress]) and kept running in the
 * background by [WorkService]: subtitles or a transcript being heard, a
 * subject being cut out, a model being imported or checked again. Each kind of
 * work has a notification of its own; several can run at once. Once over, a
 * short notification says how it went, only when the app is not on screen.
 *
 * The calls may come from any thread.
 */
class WorkNotifier(
    context: Context,
    private val isAppVisible: () -> Boolean,
    /** Whether the user wants Live Updates (the settings). */
    private val liveUpdates: () -> Boolean = { true },
) {

    /** The kinds of work, each with its notification. */
    enum class Kind(internal val id: Int, internal val icon: Int) {
        SPEECH(4201, R.drawable.ic_notification_subtitles),
        CUTOUT(4202, R.drawable.ic_notification_cutout),
        SPEECH_MODEL(4203, R.drawable.ic_notification_unpack),
        CUTOUT_MODEL(4204, R.drawable.ic_notification_unpack),
    }

    /** What a notification shows now. */
    class Status(
        val title: String,
        val text: String,
        /** 0..1, or null while it cannot be told. */
        val fraction: Float?,
        /** Which part this is ("Part 3 of 12"), or null. */
        val part: String? = null,
        /** Where the parts of the work meet on the bar, 0..1. */
        val marks: List<Float> = emptyList(),
    )

    private class Running(
        val status: Status,
        val onCancel: (() -> Unit)?,
        val startedAtMs: Long,
        val endsAtMs: Long?,
    )

    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val running = LinkedHashMap<Kind, Running>()
    private val lastPost = HashMap<Kind, Pair<Long, Int>>()

    /**
     * Shows [kind] of work as [status]: a new notification the first time
     * (its cancel button calls [onCancel], none without), the same one after.
     */
    @Synchronized
    fun show(kind: Kind, status: Status, onCancel: (() -> Unit)? = null) {
        val before = running[kind]
        val now = System.currentTimeMillis()
        val startedAt = before?.startedAtMs ?: now
        // The time left, steadied: it only moves when the estimate moved by enough to matter.
        val estimate = LiveProgress.endsAt(startedAt, status.fraction)
        val endsAt = when {
            estimate == null -> null
            before?.endsAtMs == null -> estimate
            abs(estimate - before.endsAtMs) > maxOf(ETA_SLACK_MS, (before.endsAtMs - now) / 10) -> estimate
            else -> before.endsAtMs
        }
        running[kind] = Running(status, onCancel ?: before?.onCancel, startedAt, endsAt)
        if (before == null) {
            ensureChannels()
            manager.cancel(resultId(kind))
            lastPost.remove(kind)
            startService()
        }
        post(kind, force = before == null || before.status.text != status.text)
    }

    /**
     * [kind] of work is over. With [result] (a title and a text), it is said in
     * a notification of its own, when the app is not on screen.
     */
    @Synchronized
    fun done(kind: Kind, result: Pair<String, String>? = null) {
        if (running.remove(kind) == null) return
        lastPost.remove(kind)
        // The service looks again: another work holds the foreground from now on, or it goes.
        WorkService.refresh(app)
        manager.cancel(kind.id)
        if (result != null && !isAppVisible()) postResult(kind, result.first, result.second)
    }

    /** The app is on screen again: the result notifications have done their job. */
    fun clearResults() {
        Kind.entries.forEach { manager.cancel(resultId(it)) }
    }

    /** A cancel button was touched. */
    internal fun cancelRequested(kind: Kind) {
        val cancel = synchronized(this) { running[kind]?.onCancel }
        cancel?.invoke()
    }

    /** Everything stops: the app was swiped away, or the system ended the service. */
    internal fun cancelAll() {
        val cancels = synchronized(this) { running.values.mapNotNull { it.onCancel } }
        cancels.forEach { it() }
    }

    /** Takes away the notifications of work that is over (one that held the foreground stays until let go). */
    @Synchronized
    internal fun sweep() {
        Kind.entries.filter { it !in running }.forEach { manager.cancel(it.id) }
    }

    /** The work that holds the service's foreground, and its notification; null when none runs. */
    @Synchronized
    internal fun foreground(): Pair<Int, Notification>? {
        val (kind, work) = running.entries.firstOrNull()?.toPair() ?: return null
        return kind.id to build(kind, work)
    }

    private fun startService() {
        try {
            app.startForegroundService(Intent(app, WorkService::class.java))
        } catch (e: RuntimeException) {
            // Not allowed now (the app is in the background): plain notifications then.
            Log.w(TAG, "Work service not started: ${e.message}")
        }
    }

    /** Shows [kind] again: at once with [force], else at most a few times a second. */
    private fun post(kind: Kind, force: Boolean) {
        val work = running[kind] ?: return
        val now = SystemClock.elapsedRealtime()
        val permille = work.status.fraction?.let { (it * 1000).roundToInt() } ?: -1
        val last = lastPost[kind]
        if (!force && last != null && (last.second == permille || now - last.first < MIN_UPDATE_MS)) return
        lastPost[kind] = now to permille
        try {
            manager.notify(kind.id, build(kind, work))
        } catch (e: SecurityException) {
            // Notifications refused: the work goes on regardless.
        }
    }

    private fun build(kind: Kind, work: Running): Notification = LiveProgress.build(
        app,
        LiveProgress.Spec(
            channel = CHANNEL,
            icon = kind.icon,
            title = work.status.title,
            text = work.status.text,
            fraction = work.status.fraction,
            part = work.status.part,
            marks = work.status.marks,
            endsAtMs = work.endsAtMs,
            cancel = if (work.onCancel != null) WorkService.cancelIntent(app, kind) else null,
            cancelLabel = app.getString(R.string.work_action_stop),
            open = LiveProgress.openApp(app),
            promote = liveUpdates(),
        ),
    )

    private fun postResult(kind: Kind, title: String, text: String) {
        val notification = Notification.Builder(app, RESULT_CHANNEL)
            .setSmallIcon(kind.icon)
            .setColor(LiveProgress.ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(LiveProgress.openApp(app))
            .build()
        try {
            manager.notify(resultId(kind), notification)
        } catch (e: SecurityException) {
            // Notifications refused.
        }
    }

    private fun resultId(kind: Kind) = kind.id + RESULT_OFFSET

    private fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.work_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = app.getString(R.string.work_channel_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL, app.getString(R.string.work_result_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = app.getString(R.string.work_result_channel_description)
            },
        )
    }

    internal companion object {
        const val TAG = "WorkNotifier"
        const val CHANNEL = "work_progress"
        const val RESULT_CHANNEL = "work_result"
        const val RESULT_OFFSET = 50

        /** The system drops updates that come too often; a few a second are plenty. */
        const val MIN_UPDATE_MS = 400L

        /** The time left moves by at least this much, or a tenth of it, before it is shown moved. */
        const val ETA_SLACK_MS = 5_000L
    }
}
