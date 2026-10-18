package com.cuscus.ffmpeggui.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps the editor's preview playing while the app is in the background, with
 * the system's media notification (title, picture, play / pause, seek bar),
 * the lock screen and headphone buttons. Media3 runs it in the foreground
 * while the preview plays and lets it go when it pauses.
 *
 * The session wraps the open editor's [PreviewSessionPlayer], handed over by
 * [PreviewSessions]; the service stops as soon as the editor closes.
 */
@OptIn(UnstableApi::class)
class PreviewPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var closing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        // Started by an editor (or again, while running): it takes over. Restarted by the
        // system after the app was gone, it finds no editor and stops.
        if (!closing) PreviewSessions.register(this)
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Gives the session to [player], the open editor's. */
    internal fun open(player: PreviewSessionPlayer) {
        if (closing || session?.player === player) return
        session?.release()
        session = MediaSession.Builder(this, player)
            .setId("preview-${++sessionCount}")
            .setCallback(TrustedControllersOnly)
            .apply { appIntent()?.let(::setSessionActivity) }
            .build()
            .also(::addSession)
    }

    /** The editor closed: the notification goes and the service stops. */
    internal fun close() {
        if (closing) return
        closing = true
        PreviewSessions.unregister(this)
        session?.release()
        session = null
        pauseAllPlayersAndStopSelf()
    }

    /** The app was swiped away from the recent apps: nothing is left to play. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        close()
    }

    override fun onDestroy() {
        PreviewSessions.unregister(this)
        session?.release()
        session = null
        super.onDestroy()
        // An editor that opened while this one was closing gets a service of its own.
        PreviewSessions.restartIfWaiting(applicationContext)
    }

    /** Tapping the notification brings the app back as it was, like its launcher icon. */
    private fun appIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        /** Each session gets its own id: a closing one may still be registered for a moment. */
        var sessionCount = 0
    }

    /**
     * The service is exported (the system binds to it), so any installed app could
     * connect a controller and read what the editor plays: its title, artist and a
     * frame of the video. Only trusted controllers are let in: this app (its own
     * media notification included), the system (System UI, the lock screen,
     * headphone and Bluetooth buttons), apps holding MEDIA_CONTENT_CONTROL and apps
     * the user made notification listeners (see ControllerInfo.isTrusted).
     */
    private object TrustedControllersOnly : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            if (controller.isTrusted || session.isMediaNotificationController(controller)) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            } else {
                MediaSession.ConnectionResult.reject()
            }
    }
}

/**
 * Hands the open editor's [PreviewSessionPlayer] to [PreviewPlaybackService]:
 * starts the service when an editor opens, stops it when the editor closes.
 * Main thread only.
 */
object PreviewSessions {
    private var player: PreviewSessionPlayer? = null
    private var service: PreviewPlaybackService? = null

    /** An editor opened with [player]. */
    fun attach(context: Context, player: PreviewSessionPlayer) {
        this.player = player
        val running = service
        if (running != null) running.open(player) else start(context)
    }

    /** The editor of [player] closed. */
    fun detach(player: PreviewSessionPlayer) {
        if (this.player !== player) return
        this.player = null
        service?.close()
    }

    internal fun register(service: PreviewPlaybackService) {
        this.service = service
        val current = player
        if (current != null) service.open(current) else service.close()
    }

    internal fun unregister(service: PreviewPlaybackService) {
        if (this.service === service) this.service = null
    }

    internal fun restartIfWaiting(context: Context) {
        if (player != null && service == null) start(context)
    }

    /**
     * Starts the service. The editor is on screen when this is called, which the system
     * allows; Media3 brings the service to the foreground as soon as the preview plays.
     */
    private fun start(context: Context) {
        runCatching { context.startService(Intent(context, PreviewPlaybackService::class.java)) }
    }
}
