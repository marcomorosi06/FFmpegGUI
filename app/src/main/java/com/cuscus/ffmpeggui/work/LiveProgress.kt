package com.cuscus.ffmpeggui.work

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.cuscus.ffmpeggui.R
import kotlin.math.roundToInt

/**
 * How a long piece of work looks in the notifications, the same for all of
 * them (an export, subtitles being made, a model being imported, a subject
 * being cut out):
 *
 *  - Android 16 and later: a Live Update (`Notification.ProgressStyle`) in the
 *    app's teal: the bar as one segment with marks where its parts meet (the
 *    files listened to, say), the work's own icon riding the bar and a tick
 *    waiting at its end; the percentage in the status bar chip; the time left
 *    counting down in the header once it can be told. It asks to be promoted
 *    (`android.requestPromotedOngoing`) unless Live Updates are off in the
 *    settings, and meets the other conditions: ongoing, a title, no custom
 *    views, not a group summary, not colorized;
 *  - before: an ordinary ongoing notification with a progress bar, tinted teal,
 *    the part being worked on in its sub-text.
 *
 * Both show a cancel button (when the work can be stopped) and open the app
 * when touched.
 */
internal object LiveProgress {

    /** The app's teal: the notification's accent and the bar. */
    const val ACCENT = 0xFF00897B.toInt()

    /** The bar's track before it is reached, a pale teal. */
    private const val POINT_COLOR = 0xFF4DB6AC.toInt()

    /** Steps of the bar: fine enough to move smoothly. */
    private const val STEPS = 1000

    class Spec(
        val channel: String,
        /** The small icon, which also rides the bar. */
        val icon: Int,
        val title: String,
        /** What is being done now ("Listening", "Encoding", "Checking every file"...). */
        val text: String,
        /** 0..1, or null while it cannot be told. */
        val fraction: Float?,
        /** Which part of the work this is ("Part 3 of 12"), or null. */
        val part: String? = null,
        /** Where the parts of the work meet on the bar, 0..1 (the files of a run, say). */
        val marks: List<Float> = emptyList(),
        /** Wall-clock time it should be over at, or null while it cannot be told. */
        val endsAtMs: Long? = null,
        val cancel: PendingIntent?,
        val cancelLabel: String,
        val open: PendingIntent?,
        /** Asks for a Live Update (Android 16 and later; the settings can turn them off). */
        val promote: Boolean,
    )

    fun build(context: Context, spec: Spec): Notification {
        val percent = spec.fraction?.let { (it.coerceIn(0f, 1f) * 100).roundToInt() }
        val builder = Notification.Builder(context, spec.channel)
            .setSmallIcon(spec.icon)
            .setColor(ACCENT)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(spec.open)
        // The time left, counting down by itself between updates.
        val endsAt = spec.endsAtMs
        if (endsAt != null && endsAt > System.currentTimeMillis()) {
            builder.setShowWhen(true).setWhen(endsAt).setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        if (spec.cancel != null) {
            builder.addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification_cancel), spec.cancelLabel, spec.cancel).build(),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressIndeterminate(spec.fraction == null)
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(STEPS).setColor(ACCENT)))
                .setProgress(((spec.fraction ?: 0f).coerceIn(0f, 1f) * STEPS).roundToInt())
                .setProgressTrackerIcon(Icon.createWithResource(context, spec.icon).setTint(ACCENT))
                .setProgressEndIcon(Icon.createWithResource(context, R.drawable.ic_notification_done).setTint(ACCENT))
            val marks = spec.marks.filter { it > 0f && it < 1f }
            if (marks.isNotEmpty()) {
                style.setProgressPoints(marks.map { Notification.ProgressStyle.Point((it * STEPS).roundToInt()).setColor(POINT_COLOR) })
            }
            builder.setStyle(style)
            spec.part?.let { builder.setSubText(it) }
            if (spec.promote) {
                builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                builder.setShortCriticalText(percent?.let { "$it%" })
            }
        } else {
            builder.setProgress(STEPS, ((spec.fraction ?: 0f).coerceIn(0f, 1f) * STEPS).roundToInt(), spec.fraction == null)
            val sub = listOfNotNull(spec.part, percent?.let { "$it%" }).joinToString(" \u00B7 ")
            if (sub.isNotEmpty()) builder.setSubText(sub)
        }
        return builder.build()
    }

    /** Touching a notification brings the app back as it was, like its launcher icon. */
    fun openApp(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * When work begun [startedAtMs] (wall clock) and now [fraction] done should
     * be over, once it has run long enough to tell; else null.
     */
    fun endsAt(startedAtMs: Long, fraction: Float?): Long? {
        if (fraction == null || fraction < MIN_FRACTION_FOR_ETA || fraction >= 1f) return null
        val now = System.currentTimeMillis()
        val elapsed = now - startedAtMs
        if (elapsed < MIN_ELAPSED_FOR_ETA_MS) return null
        return now + (elapsed * (1f - fraction) / fraction).toLong()
    }

    /** Notification.EXTRA_REQUEST_PROMOTED_ONGOING: asks for a Live Update. */
    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    private const val MIN_FRACTION_FOR_ETA = 0.03f
    private const val MIN_ELAPSED_FOR_ETA_MS = 3_000L
}
