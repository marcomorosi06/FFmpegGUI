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
        // Started in the foreground, it must take the foreground at once, whatever happened
        // meanwhile: else Android closes the app. An export over already (a command that fails
        // at once, "pwd" for one) gets a quiet notification, held only to let go of it.
        val running = notifier?.progressNotification()
        val notification = running ?: notifier?.closingNotification()
        if (notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                startForeground(AndroidExportNotifier.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            else -> startForeground(AndroidExportNotifier.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        holding = true
        if (running == null || stopWanted) {
            stopWanted = false
            letGo()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        holding = false
        super.onDestroy()
    }

    /** Out of the foreground, its notification gone, and stopped. */
    private fun letGo() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

        /** The service has taken the foreground (and has not ended since). */
        @Volatile private var holding = false

        /** The export ended before the service took the foreground: it lets go as soon as it has. */
        @Volatile private var stopWanted = false

        /** A new export is about to start the service: no stop is pending any more. */
        fun expectStart() {
            stopWanted = false
        }

        /**
         * Lets the service go: the export is over. Not before it has taken the
         * foreground, which Android would answer by closing the app: then it
         * lets go by itself as soon as it has.
         */
        fun stop(context: Context) {
            if (holding) {
                context.stopService(Intent(context, ExportService::class.java))
            } else {
                stopWanted = true
            }
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
