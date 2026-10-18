package com.cuscus.ffmpeggui.export

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.cuscus.ffmpeggui.FfmpegGuiApp

/**
 * Keeps the app running while an export runs, also in the background, with the
 * export's notification as its own (see [AndroidExportNotifier]). The export
 * itself runs in the app; this only holds the foreground. Its type is media
 * processing (transcoding) from Android 15, data sync before.
 */
class ExportService : Service() {

    private val notifier: AndroidExportNotifier?
        get() = (application as? FfmpegGuiApp)?.container?.exportNotifier as? AndroidExportNotifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            notifier?.cancelRequested()
            return START_NOT_STICKY
        }
        val notification = notifier?.progressNotification()
        if (notification == null) {
            // Nothing runs (any more): nothing to hold.
            stopSelf()
            return START_NOT_STICKY
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                startForeground(AndroidExportNotifier.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            else -> startForeground(AndroidExportNotifier.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        return START_NOT_STICKY
    }

    /** Android 15 ends a media processing service after six hours: the export is stopped. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        notifier?.cancelRequested()
        stopSelf()
    }

    /** The app was swiped away from the recent apps: the export goes with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        notifier?.cancelRequested()
        stopSelf()
    }

    companion object {
        private const val ACTION_CANCEL = "com.cuscus.ffmpeggui.export.CANCEL"

        /** Lets the service go: the export is over. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ExportService::class.java))
        }

        /** What the notification's cancel button sends. */
        fun cancelIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, ExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
