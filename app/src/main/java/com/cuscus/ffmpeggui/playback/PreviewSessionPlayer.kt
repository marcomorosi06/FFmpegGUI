package com.cuscus.ffmpeggui.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs

/**
 * What the system sees of the editor's preview: one item, as long as the file
 * the export will make, that plays, pauses and seeks. The media session, its
 * notification, the lock screen, headphones and cars talk to this player; it
 * passes their requests on to the editor ([onPlay], [onSeek]) and reports the
 * editor's state back ([setPlaying], [follow], [setMetadata]).
 *
 * The preview itself is not a single player (clips at their own speed,
 * backwards, placed sounds: see EditorPreviewPlayer), so this stands in front
 * of it rather than being it. Use it on the main thread.
 */
@OptIn(UnstableApi::class)
class PreviewSessionPlayer(
    private var onPlay: (Boolean) -> Unit,
    private var onSeek: (Long) -> Unit,
    /** Where the preview is, on the clock of the exported file. */
    private val position: () -> Long,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private var playing = false
    private var durationMs = 0L
    private var metadata = MediaMetadata.EMPTY
    /** A jump of the preview (a seek in the editor, a loop) still to be reported, and where to. */
    private var jumpedToMs: Long? = null
    private var lastPositionMs = 0L
    private var lastNanos = 0L

    /** The editor plays or pauses. */
    fun setPlaying(playing: Boolean) {
        if (this.playing == playing) return
        this.playing = playing
        invalidateState()
    }

    /**
     * The preview's position and length, on every tick. A length that changes, or a
     * position that is not where playing (or standing still) would have taken it, is
     * reported; the position itself is read whenever it is asked for.
     */
    fun follow(positionMs: Long, durationMs: Long) {
        val now = System.nanoTime()
        val expected = lastPositionMs + if (playing && lastNanos != 0L) (now - lastNanos) / 1_000_000L else 0L
        val jumped = lastNanos != 0L && abs(positionMs - expected) > JUMP_MS
        lastPositionMs = positionMs
        lastNanos = now
        if (jumped) jumpedToMs = positionMs
        if (jumped || durationMs != this.durationMs) {
            this.durationMs = durationMs
            invalidateState()
        }
    }

    /** The title, artist and picture shown by the notification and the lock screen. */
    fun setMetadata(title: String, artist: String, artwork: ByteArray?) {
        metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        invalidateState()
    }

    /** The editor is gone: requests that still arrive change nothing. */
    fun disconnect() {
        onPlay = {}
        onSeek = {}
    }

    override fun getState(): State {
        val item = MediaItemData.Builder(ITEM_UID)
            .setMediaItem(MediaItem.Builder().setMediaId(ITEM_UID).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setDurationUs(if (durationMs > 0L) durationMs * 1000L else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
        val state = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(listOf(item))
            .setContentPositionMs { position().coerceAtLeast(0L) }
        jumpedToMs?.let {
            state.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, it)
            jumpedToMs = null
        }
        return state.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playing = playWhenReady
        onPlay(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
        onSeek(target)
        lastPositionMs = target
        lastNanos = System.nanoTime()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playing = false
        onPlay(false)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val ITEM_UID = "editor-preview"

        /** How far (ms) the preview may be from where it should be before it counts as a jump. */
        const val JUMP_MS = 700L

        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
