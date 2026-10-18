package com.cuscus.ffmpeggui.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.cuscus.ffmpeggui.FfmpegGuiApp

/**
 * Keeps the app running while long work runs (see [WorkNotifier]), also in the
 * background, with the notification of one of them as its own. The work runs
 * in the app; this only holds the foreground. Its type is media processing
 * from Android 15, data sync before (as the exports').
 */
class WorkService : Service() {

    private val notifier: WorkNotifier?
        get() = (application as? FfmpegGuiApp)?.container?.workNotifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val kind = intent.getStringExtra(EXTRA_KIND)?.let { name -> WorkNotifier.Kind.entries.firstOrNull { it.name == name } }
            if (kind != null) notifier?.cancelRequested(kind)
            return START_NOT_STICKY
        }
        // Started as a foreground service it must come to the foreground, even when the work
        // was over before it got here (a quick cut-out): then only for a moment.
        val (id, notification) = notifier?.foreground() ?: (IDLE_ID to idleNotification())
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            else -> startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        // The work that held the foreground before may be over: its notification goes now.
        notifier?.sweep()
        if (notifier?.foreground() == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun idleNotification(): android.app.Notification =
        android.app.Notification.Builder(this, WorkNotifier.CHANNEL)
            .setSmallIcon(com.cuscus.ffmpeggui.R.drawable.ic_notification_done)
            .setContentTitle(getString(com.cuscus.ffmpeggui.R.string.app_name))
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifier?.sweep()
        super.onDestroy()
    }

    /** Android 15 ends a media processing service after six hours: the work is stopped. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        notifier?.cancelAll()
        stopSelf()
    }

    /** The app was swiped away from the recent apps: the work goes with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        notifier?.cancelAll()
        stopSelf()
    }

    companion object {
        private const val ACTION_CANCEL = "com.cuscus.ffmpeggui.work.CANCEL"
        private const val EXTRA_KIND = "kind"

        private const val IDLE_ID = 4299

        /**
         * The work running changed: the service puts another one's notification in the
         * foreground, or lets go when none is left (it stops itself, so that it is never
         * stopped before it came to the foreground).
         */
        fun refresh(context: Context) {
            try {
                context.startService(Intent(context, WorkService::class.java))
            } catch (e: RuntimeException) {
                // Not running and not allowed to start: there is nothing to hold.
                context.stopService(Intent(context, WorkService::class.java))
            }
        }

        /** What [kind]'s stop button sends. */
        fun cancelIntent(context: Context, kind: WorkNotifier.Kind): PendingIntent = PendingIntent.getService(
            context,
            kind.id,
            Intent(context, WorkService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
