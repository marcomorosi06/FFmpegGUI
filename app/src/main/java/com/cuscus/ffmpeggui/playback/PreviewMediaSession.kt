package com.cuscus.ffmpeggui.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.preview.FrameGrabber
import com.cuscus.ffmpeggui.preview.PreviewSnapshot
import com.cuscus.ffmpeggui.preview.smallPicture
import com.cuscus.ffmpeggui.ui.theme.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * What the media notification and the lock screen show for the preview:
 * [title], [artist] and the picture of [artwork].
 */
data class NowPlaying(
    val title: String,
    val artist: String,
    val artwork: Artwork,
)

/** Where the picture of the media notification comes from. */
sealed interface Artwork {
    /** A picture file: an audio file's cover. */
    data class Picture(val uri: Uri) : Artwork

    /** The frame at [positionMs] of a video ([still]: a picture shown as a clip). */
    data class Frame(val uri: Uri, val positionMs: Long, val still: Boolean) : Artwork

    /** Nothing of the project's own: the app's icon. */
    data object AppIcon : Artwork
}

/**
 * The preview as a media session (see [PreviewSessionPlayer]), for as long as it
 * is on screen: the play state of [isPlaying] and the clock of [snapshot] go to
 * the notification, its play, pause and seeks come back ([onSeek] gets a
 * position of the exported file).
 */
@Composable
internal fun PreviewMediaSession(
    isPlaying: MutableState<Boolean>,
    snapshot: () -> PreviewSnapshot,
    nowPlaying: NowPlaying,
    onSeek: (Long) -> Unit,
) {
    val context = LocalContext.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentSnapshot by rememberUpdatedState(snapshot)
    val player = remember {
        PreviewSessionPlayer(
            onPlay = { isPlaying.value = it },
            onSeek = { currentOnSeek(it) },
            position = { currentSnapshot().outputMs },
        )
    }
    DisposableEffect(player) {
        PreviewSessions.attach(context, player)
        onDispose {
            player.disconnect()
            PreviewSessions.detach(player)
            player.release()
        }
    }
    val playing = isPlaying.value
    LaunchedEffect(player, playing) { player.setPlaying(playing) }
    // The clock, followed on every tick without recomposing anything.
    LaunchedEffect(player) {
        snapshotFlow { currentSnapshot() }.collect { player.follow(it.outputMs, it.outputDurationMs) }
    }
    val artwork by produceState<ByteArray?>(initialValue = null, nowPlaying.artwork) {
        value = withContext(Dispatchers.IO) { artworkBytes(context, nowPlaying.artwork) }
    }
    LaunchedEffect(player, nowPlaying.title, nowPlaying.artist, artwork) {
        player.setMetadata(nowPlaying.title, nowPlaying.artist, artwork)
    }
}

/** Longest side of the notification's picture. */
private const val ARTWORK_PX = 512

private const val ARTWORK_QUALITY = 88

/** The picture of [artwork] as JPEG bytes (the app icon as PNG), or null when there is none at all. */
private fun artworkBytes(context: Context, artwork: Artwork): ByteArray? {
    val picture = when (artwork) {
        is Artwork.Picture -> smallPicture(context, artwork.uri, ARTWORK_PX)
        is Artwork.Frame ->
            if (artwork.still) {
                smallPicture(context, artwork.uri, ARTWORK_PX)
            } else {
                FrameGrabber(context, artwork.uri).use { it.frameAt(artwork.positionMs, ARTWORK_PX) }
            }
        Artwork.AppIcon -> null
    }
    val bitmap = picture ?: appIcon(context) ?: return null
    val format = if (picture != null) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(format, ARTWORK_QUALITY, out)
        out.toByteArray()
    }
}

/** The app's icon, when there is nothing of the project's own to show. */
private fun appIcon(context: Context): Bitmap? = runCatching {
    ContextCompat.getDrawable(context, AppIcon.launcher)?.toBitmap(ARTWORK_PX, ARTWORK_PX)
}.getOrNull()
