package com.cuscus.ffmpeggui.preview

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.audio.stages
import com.cuscus.ffmpeggui.core.preview.fadeLevel
import com.cuscus.ffmpeggui.core.audio.AudioStage
import com.cuscus.ffmpeggui.core.audio.soundEqualizerStages
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.KeyProperty
import kotlin.math.abs

/**
 * One placed sound as the preview plays it: the part of [uri] from
 * [trimStartMs] to [trimEndMs], [repeats] times, from [startMs] of the output
 * clock, at [speed] and with the pitch multiplied by [pitch]. [volume] is the
 * final linear gain (0 for a muted sound, levelling included); the equaliser
 * gains are in dB.
 */
data class PreviewSound(
    val id: String,
    val uri: Uri,
    val startMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val volume: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val repeats: Int = 1,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    /** Its own effects (the pitch apart: that is [pitch]). */
    val effects: AudioEffects = AudioEffects.NONE,
    /** Its keyframes: its volume may change while it plays (times from its start). */
    val keyframes: Keyframes = Keyframes.NONE,
    /** What the keyframes' volume is multiplied by (its levelling; 0 when muted). */
    val keyScale: Float = 1f,
) {
    /** Its volume at [localMs] from its start. */
    fun volumeAt(localMs: Long): Float =
        if (keyframes.animates(KeyProperty.VOLUME)) keyframes.valueAt(KeyProperty.VOLUME, localMs, 1f) * keyScale else volume

    /** The steps its player runs: its equaliser, then its own effects. */
    val stages: List<AudioStage> get() = soundEqualizerStages(bassDb, midDb, trebleDb) + effects.stages()

    /** Length of the part of the file, played once. */
    val rangeMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    /** How long it plays on the output clock. */
    val lengthMs: Long get() = Math.round(rangeMs.toDouble() * repeats.coerceAtLeast(1) / speed.coerceAtLeast(MIN_RATE))
}

private const val MIN_RATE = 0.1f

/**
 * Plays the placed sounds alongside the footage, each on a player of its own,
 * kept where the output clock says it should be.
 *
 * A player exists only while its sound plays or is about to: a few seconds
 * before its start it is created and loaded, and soon after its end it is
 * released. However many sounds the project has, only those that overlap
 * hold a decoder.
 *
 * All methods must be called from the main thread.
 */
@OptIn(UnstableApi::class)
internal class SoundVoices(private val context: Context, private val live: LiveAudio) {

    /** A sound's player; its own equaliser and the final sound's effects run in it. */
    private class Voice(var sound: PreviewSound) {
        val booster = GainBooster()
        var parameters: PlaybackParameters? = null

        @Volatile
        var ownStages: List<AudioStage> = sound.stages
        lateinit var player: ExoPlayer
    }

    private var sounds: List<PreviewSound> = emptyList()
    private val voices = HashMap<String, Voice>()

    /** Sets the sounds; a sound whose file or trim changed is loaded again. */
    fun setSounds(newSounds: List<PreviewSound>) {
        if (newSounds == sounds) return
        sounds = newSounds
        val byId = newSounds.associateBy { it.id }
        voices.entries.removeAll { (id, voice) ->
            val sound = byId[id]
            val reload = sound == null || sound.uri != voice.sound.uri ||
                sound.trimStartMs != voice.sound.trimStartMs || sound.trimEndMs != voice.sound.trimEndMs
            if (reload) {
                release(voice)
            } else {
                voice.sound = sound!!
                voice.ownStages = sound.stages
            }
            reload
        }
    }

    /**
     * Brings every sound to [outputMs]. [playing] is whether the output clock
     * runs; [gain] is the level of all the sounds together (0 when the output has
     * no sound) and [fade] the fade of the whole video at that instant.
     */
    fun sync(outputMs: Long, playing: Boolean, gain: Float, fade: Float) {
        for (sound in sounds) {
            val localMs = outputMs - sound.startMs
            val near = localMs >= -PRELOAD_MS && localMs < sound.lengthMs + LINGER_MS
            var voice = voices[sound.id]
            if (!near) {
                if (voice != null) {
                    release(voice)
                    voices.remove(sound.id)
                }
                continue
            }
            if (voice == null) {
                voice = Voice(sound).also { it.player = load(it) }
                voices[sound.id] = voice
            }
            val inside = localMs >= 0 && localMs < sound.lengthMs
            val ownFade = fadeLevel(localMs.coerceAtLeast(0L), sound.lengthMs, sound.fadeInSeconds, sound.fadeOutSeconds)
            voice.booster.apply(voice.player, if (inside) gain * sound.volumeAt(localMs) * fade * ownFade else 0f)
            // Speed and pitch apart: the player keeps the pitch when only the speed changes. The
            // pitch of the final sound comes on top of the sound's own.
            val parameters = PlaybackParameters(sound.speed.coerceAtLeast(MIN_RATE), (sound.pitch * live.pitch).coerceAtLeast(MIN_RATE))
            if (parameters != voice.parameters) {
                voice.parameters = parameters
                voice.player.playbackParameters = parameters
            }
            val wantedRepeat = if (sound.repeats > 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (voice.player.repeatMode != wantedRepeat) voice.player.repeatMode = wantedRepeat
            drive(voice.player, filePosition(sound, localMs), sound.rangeMs, playing && inside)
        }
    }

    /** Pauses every sound where it is. */
    fun pauseAll() {
        voices.values.forEach { it.player.pause() }
    }

    fun release() {
        voices.values.forEach(::release)
        voices.clear()
    }

    private fun load(voice: Voice): ExoPlayer = processingPlayer(
        context,
        AudioEffectsProcessor({ voice.ownStages + live.stages }, live.meter),
    ).apply {
        val sound = voice.sound
        repeatMode = Player.REPEAT_MODE_OFF
        // The sound of a video file: its picture is not needed.
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        setMediaItem(
            MediaItem.Builder()
                .setUri(sound.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(sound.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(sound.trimEndMs.coerceAtLeast(sound.trimStartMs + 1))
                        .build(),
                )
                .build(),
        )
        prepare()
        seekTo(0L)
    }

    /**
     * Where in its part of the file [sound] is, [localMs] after it started: the
     * position the player reports, wrapping round with each repetition.
     */
    private fun filePosition(sound: PreviewSound, localMs: Long): Long {
        val range = sound.rangeMs
        if (range <= 0L) return 0L
        val played = Math.round(localMs.coerceAtLeast(0L) * sound.speed.toDouble()).coerceAtMost(range * sound.repeats.coerceAtLeast(1))
        return if (played >= range * sound.repeats.coerceAtLeast(1)) range else played % range
    }

    /**
     * Keeps [player] at [positionMs] of its part (which lasts [rangeMs] and may
     * wrap round), running while [run].
     */
    private fun drive(player: ExoPlayer, positionMs: Long, rangeMs: Long, run: Boolean) {
        // A player still loading after a seek is left alone, or every tick would seek again.
        val loading = player.playbackState == Player.STATE_BUFFERING
        // Distance round the loop: the end of one repetition is next to the start of the next.
        val straight = abs(player.currentPosition - positionMs)
        val drift = if (rangeMs > 0L) minOf(straight, (rangeMs - straight).coerceAtLeast(0L)) else straight
        if (!run) {
            if (player.isPlaying) player.pause()
            // Waiting at the right place, so that it starts on time.
            if (!loading && drift > PARKED_DRIFT_MS) player.seekTo(positionMs)
            return
        }
        if (!loading && drift > DRIFT_MS) player.seekTo(positionMs)
        if (!player.isPlaying && player.playbackState == Player.STATE_READY) player.play()
    }

    private fun release(voice: Voice) {
        voice.booster.release()
        voice.player.release()
    }

    private companion object {
        /** A player is made ready this long before its sound starts. */
        const val PRELOAD_MS = 3_000L

        /** And kept this long after it ends, for a quick look back. */
        const val LINGER_MS = 2_000L

        /** How far a playing sound may drift before it is re-aligned. */
        const val DRIFT_MS = 400L

        /** How far a waiting sound may be from its place before it is moved there. */
        const val PARKED_DRIFT_MS = 150L
    }
}
