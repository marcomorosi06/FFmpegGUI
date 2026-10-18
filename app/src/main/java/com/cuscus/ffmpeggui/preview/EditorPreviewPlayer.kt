package com.cuscus.ffmpeggui.preview

import android.content.Context
import android.graphics.Bitmap
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.image.ImageOutput
import com.cuscus.ffmpeggui.core.audio.AudioStage
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.MediaPoint
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming
import com.cuscus.ffmpeggui.core.preview.fadeLevel
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.preview.volumeToDecibels
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.model.TransitionKind
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** One clip of the preview sequence, with its trim window on the source clock. */
data class PreviewClip(
    val uri: Uri,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    /** Full length of the source, 0 when not known yet. */
    val sourceDurationMs: Long = 0L,
    /**
     * A still picture: played as an image item lasting the clip's length. The
     * player keeps time through it; the editor draws the picture itself.
     */
    val isStill: Boolean = false,
) {
    /** Playable length as far as it can be told before the player has loaded the clip. */
    val expectedLengthMs: Long
        get() = ((endMs ?: sourceDurationMs) - startMs).coerceAtLeast(0L)
}

/**
 * How one clip plays: its own speed and direction, its gain (0 = muted) and its
 * fades. Kept apart from [PreviewClip] so that changing them never reloads the
 * footage.
 */
data class ClipLevels(
    val speed: Float = 1f,
    val reversed: Boolean = false,
    /** The speed changing along the clip; it takes the place of [speed]. */
    val curve: SpeedCurve? = null,
    /** False when the pitch rises and falls with the speed. */
    val keepPitch: Boolean = true,
    val gain: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    /** The clip's keyframes: its volume may change over its file (0 gain stays silent). */
    val keyframes: Keyframes = Keyframes.NONE,
    /** How the clip gives way to the next one (they overlap for its length). */
    val transition: Transition? = null,
) {
    /** The gain at [fileMs] of the clip's file. */
    fun gainAt(fileMs: Long): Float =
        if (gain == 0f || !keyframes.animates(KeyProperty.VOLUME)) gain else keyframes.valueAt(KeyProperty.VOLUME, fileMs, gain)
}

/** Where the preview is, on the clocks the editor UI cares about. */
data class PreviewSnapshot(
    /** Position in the file the export would produce. */
    val outputMs: Long = 0L,
    val outputDurationMs: Long = 0L,
    /** Position in the joined video, before the speed of the whole video (the subtitle clock). */
    val timelineMs: Long = 0L,
    /** Position in the footage as laid out on the timeline, whatever the playback direction. */
    val mediaMs: Long = 0L,
    /** Fade of the whole video: 0 = fully faded to black / silence, 1 = untouched. */
    val fadeLevel: Float = 1f,
    /** Index of the clip on screen. */
    val segment: Int = 0,
    /** Fade of the clip on screen, on the same scale as [fadeLevel]. */
    val clipFadeLevel: Float = 1f,
    /** How far through the clip on screen playback is, 0..1 (what a still's motion follows). */
    val clipProgress: Float = 0f,
    /** Where in its file the clip on screen is (what the clip's keyframes follow). */
    val clipFileMs: Long = 0L,
    /** How long the clip on screen has played, on its own clock (what its captions' and pictures' keyframes follow). */
    val clipPlayedMs: Long = 0L,
    /** The transition on screen, from clip [segment] into [incoming]; null when one clip alone is. */
    val transition: TransitionKind? = null,
    /** How far through it, 0..1. */
    val transitionProgress: Float = 0f,
    /** The clip coming in, shown by [EditorPreviewPlayer.incomingPlayer] (or drawn, a still). */
    val incoming: Int = -1,
    /** Its [clipFadeLevel], [clipProgress], [clipFileMs] and [clipPlayedMs]. */
    val incomingFadeLevel: Float = 1f,
    val incomingProgress: Float = 0f,
    val incomingFileMs: Long = 0L,
    val incomingPlayedMs: Long = 0L,
    /** The clip [EditorPreviewPlayer.incomingPlayer] holds (also just before its transition), -1 for none. */
    val incomingLoaded: Int = -1,
)

/**
 * Plays the edit live, before anything is rendered.
 *
 * It owns a player for the picture and source audio (a playlist of the clips)
 * and the players of the placed sounds ([SoundVoices]). While playing, the output clock is
 * the master: on every [tick] it advances in real time and each clip is driven
 * to where that clock says it should be. That is how what the players cannot do
 * on their own is reproduced:
 *
 *  - every clip at its own speed (the player rate changes from clip to clip);
 *  - backwards playback (of a clip or of the whole video), emulated by stepping
 *    the paused player backwards;
 *  - fades of clips and of the whole video, and gain above 100 %, applied to the
 *    audio volume;
 *  - placement, trim and fades of the placed sounds;
 *  - transitions: while two clips overlap the one coming in plays on a second
 *    player ([incomingPlayer]), loaded a little ahead, and the two sounds are
 *    cross-faded. The main player meanwhile plays the end of the clip going
 *    out; it then goes on into the next clip where the transition left it (its
 *    item starts there, see [heads]), without a seek.
 *
 * While the preview can be heard it holds the audio focus like any media
 * player: another app playing, a call or unplugged headphones pause it, and
 * [onInterrupted] tells the editor so.
 *
 * All methods must be called from the main thread.
 */
@OptIn(UnstableApi::class)
class EditorPreviewPlayer(context: Context) {

    /** The effects on the final sound, heard on every player, and its level. */
    val liveAudio = LiveAudio()

    /** Bind this to the surface that shows the picture. */
    val videoPlayer: ExoPlayer = processingPlayer(context, AudioEffectsProcessor({ liveAudio.stages }, liveAudio.meter)).apply {
        repeatMode = Player.REPEAT_MODE_ALL
    }

    /** Plays the clip coming in through a transition; bind it to a second surface. */
    val incomingPlayer: ExoPlayer = processingPlayer(context, AudioEffectsProcessor({ liveAudio.stages }, liveAudio.meter))

    private val voices = SoundVoices(context, liveAudio)

    /** Invoked with the decoded frame size whenever it changes. */
    var onVideoSize: ((width: Int, height: Int) -> Unit)? = null

    /** Invoked when the system paused the preview (see the class notes); it is paused already. */
    var onInterrupted: (() -> Unit)? = null

    private val videoListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                onVideoSize?.invoke(videoSize.width, videoSize.height)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady &&
                (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
            ) {
                interrupt()
            }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            // A call or another app for a moment: paused rather than left running silently.
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) interrupt()
        }
    }
    private var audible: Boolean? = null

    private var clips: List<PreviewClip> = emptyList()
    private var lengths: MutableList<Long> = mutableListOf()
    private var levels: List<ClipLevels> = emptyList()
    private var speed = 1f
    private var reversed = false
    private var keepPitch = true
    private var timeline = PreviewTimeline(emptyList())
    /**
     * Footage at the start of each clip the main player skips (the item starts
     * after it): the part shown only by [incomingPlayer], as the clip comes in.
     */
    private var heads: List<Long> = emptyList()

    /** The clip [incomingPlayer] holds, -1 for none. */
    private var incomingIndex = -1
    private var lastIncomingSeekNanos = 0L
    private var incomingRate = 1f
    private var incomingPitch = 1f
    private val incomingBooster = GainBooster()

    private var playing = false
    /** The master clock while playing: position in the output, in milliseconds. */
    private var outputClockMs = 0.0
    private var lastTickNanos = 0L
    private var lastStepNanos = 0L
    private var lastSeekNanos = 0L
    /** True while the clip on screen is being stepped frame by frame rather than played. */
    private var stepping = false
    /** Playback rate and pitch last handed to the player. */
    private var appliedRate = 1f
    private var appliedPitch = 1f

    private var sourceGain = 1f
    private var soundGain = 1f
    private var fadeInSeconds = 0f
    private var fadeOutSeconds = 0f
    private val sourceBooster = GainBooster()

    init {
        incomingPlayer.repeatMode = Player.REPEAT_MODE_OFF
        incomingPlayer.setImageOutput(object : ImageOutput {
            override fun onImageAvailable(presentationTimeUs: Long, bitmap: Bitmap) = Unit
            override fun onDisabled() = Unit
        })
        videoPlayer.addListener(videoListener)
        // Unplugged headphones pause it, as any media player; the CPU stays awake while it plays.
        videoPlayer.setHandleAudioBecomingNoisy(true)
        videoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)
        // Stills are image items. The player only has to keep time through them (the
        // editor draws the picture), but it needs somewhere to send their frames.
        videoPlayer.setImageOutput(object : ImageOutput {
            override fun onImageAvailable(presentationTimeUs: Long, bitmap: Bitmap) = Unit
            override fun onDisabled() = Unit
        })
    }

    // ---------------------------------------------------------------------
    // What to play
    // ---------------------------------------------------------------------

    /**
     * Sets the clip sequence and how each clip plays ([newLevels], same order).
     *
     * When only the levels change the frame on screen stays the same and only
     * the clocks around it move. When the clips change, playback resumes from
     * the same output instant, clamped to the new length.
     */
    fun setSequence(newClips: List<PreviewClip>, newLevels: List<ClipLevels>) {
        if (newClips == clips && newLevels == levels) return
        if (newClips == clips) {
            val mediaMs = currentMediaMs()
            levels = newLevels
            rebuildTimeline()
            outputClockMs = timeline.outputMs(mediaMs).toDouble()
            // Transitions changed: the items start elsewhere.
            if (replaceShiftedItems()) seekToMedia(mediaMs)
            return
        }
        // Measured on the old sequence, while clips, lengths and levels still agree.
        val previousOutputMs = currentOutputMs()

        clips = newClips
        lengths = newClips.map { it.expectedLengthMs }.toMutableList()
        levels = newLevels
        rebuildTimeline()

        releaseIncoming()
        if (newClips.isEmpty()) {
            videoPlayer.clearMediaItems()
            return
        }
        appliedHeads = heads
        videoPlayer.setMediaItems(newClips.mapIndexed { index, clip -> mediaItem(clip, heads.getOrElse(index) { 0L }) })
        videoPlayer.prepare()
        seekToMedia(timeline.mediaMsForOutput(previousOutputMs))
        applyPlayState()
    }

    /** Sets the placed sounds; their places are on the output clock. */
    fun setSounds(sounds: List<PreviewSound>) = voices.setSounds(sounds)

    /**
     * Sets what is done to the final sound: its steps (heard on every player)
     * and its pitch factor (1 = unchanged; the tempo is kept).
     */
    fun setAudioEffects(stages: List<AudioStage>, pitch: Float) {
        liveAudio.stages = stages
        liveAudio.pitch = if (pitch > 0f) pitch else 1f
    }

    /**
     * An audio file is being made: the picture is not decoded at all (the
     * editor shows the sound instead).
     */
    fun setPictureEnabled(enabled: Boolean) {
        val parameters = videoPlayer.trackSelectionParameters
        val disabled = !enabled
        videoPlayer.trackSelectionParameters = parameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
            .build()
        incomingPlayer.trackSelectionParameters = incomingPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
            .build()
    }

    /** Speed and direction of the whole video, and whether its pitch is kept. */
    fun setPlayback(speed: Float, reversed: Boolean, keepPitch: Boolean = true) {
        val mediaMs = currentMediaMs() // keep showing the same frame
        this.speed = if (speed > 0f) speed else 1f
        this.reversed = reversed
        this.keepPitch = keepPitch
        rebuildTimeline()
        outputClockMs = timeline.outputMs(mediaMs).toDouble()
        applyPlayState()
    }

    /**
     * Linear gains of the source sound and of the placed sounds together (1 =
     * unchanged, above 1 = boost) and the fade lengths of the whole video in
     * seconds. A gain of 0 silences, for example when the output has no audio.
     */
    fun setLevels(sourceGain: Float, soundGain: Float, fadeInSeconds: Float, fadeOutSeconds: Float) {
        this.sourceGain = sourceGain.coerceAtLeast(0f)
        this.soundGain = soundGain.coerceAtLeast(0f)
        this.fadeInSeconds = fadeInSeconds
        this.fadeOutSeconds = fadeOutSeconds
    }

    /**
     * Whether the preview can be heard at all. Only then does it ask for the audio
     * focus: a silent preview (a GIF, a muted video) leaves other apps playing.
     */
    fun setAudible(audible: Boolean) {
        if (this.audible == audible) return
        this.audible = audible
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        videoPlayer.setAudioAttributes(attributes, audible)
        // One focus for the preview: the second player never asks for it (it would pause the first).
        incomingPlayer.setAudioAttributes(attributes, false)
    }

    // ---------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------

    fun play() {
        outputClockMs = currentOutputMs().toDouble()
        playing = true
        lastTickNanos = 0L
        applyPlayState()
    }

    fun pause() {
        if (playing) {
            // Leave the player on the frame the clock had reached.
            playing = false
            seekToMedia(timeline.mediaMsForOutput(outputClockMs.toLong()))
        }
        applyPlayState()
    }

    /** Jumps to a position of the exported file. */
    fun seekToOutput(outputMs: Long) {
        seekToMedia(timeline.mediaMsForOutput(outputMs))
    }

    /** Pauses on the frame found at [mediaMs] of the footage (see [PreviewSnapshot.mediaMs]). */
    fun showMediaFrame(mediaMs: Long) {
        playing = false
        applyPlayState()
        seekToMedia(mediaMs)
    }

    /**
     * Advances the preview. Call it often (on every frame, at least about 30 times a second)
     * while the editor is visible.
     */
    fun tick(): PreviewSnapshot {
        val now = System.nanoTime()
        // In fractions of a millisecond: at 60 or 120 ticks a second whole milliseconds
        // would lose a few percent of the time.
        val elapsedMs = if (lastTickNanos == 0L) 0.0 else ((now - lastTickNanos) / 1_000_000.0).coerceAtMost(MAX_TICK_MS.toDouble())
        lastTickNanos = now

        adoptReportedLength()

        val mediaMs = if (playing && clips.isNotEmpty() && timeline.outputDurationMs > 0) {
            drive(elapsedMs, now)
        } else {
            currentMediaMs()
        }

        val outputMs = timeline.outputMs(mediaMs)
        val point = timeline.locate(mediaMs)
        val clip = timeline.clipPosition(mediaMs)
        val clipLevels = levels.getOrNull(point.segment) ?: ClipLevels()
        val fade = fadeLevel(outputMs, timeline.outputDurationMs, fadeInSeconds, fadeOutSeconds)
        val clipFade = fadeLevel(clip.playedOffsetMs, clip.playedLengthMs, clipLevels.fadeInSeconds, clipLevels.fadeOutSeconds)

        val transition = if (clips.size > 1) timeline.transitionAt(outputMs) else null
        val incoming = driveIncoming(outputMs, transition, now)
        // The sounds cross-fade, as the export's acrossfade does.
        val outgoingShare = if (transition != null) 1f - transition.progress else 1f

        // Sound played backwards cannot be produced on the fly, so it is muted meanwhile.
        val backwards = timeline.playsBackwards(point.segment)
        val fileMs = (clips.getOrNull(point.segment)?.startMs ?: 0L) + point.offsetMs
        sourceBooster.apply(videoPlayer, if (backwards) 0f else sourceGain * clipLevels.gainAt(fileMs) * fade * clipFade * outgoingShare)
        incomingBooster.apply(
            incomingPlayer,
            if (incoming == null || transition == null || timeline.playsBackwards(transition.incoming)) {
                0f
            } else {
                sourceGain * (levels.getOrNull(transition.incoming) ?: ClipLevels()).gainAt(incoming.fileMs) * fade * incoming.fadeLevel * transition.progress
            },
        )
        voices.sync(outputMs, playing, soundGain, fade)

        return PreviewSnapshot(
            outputMs = outputMs,
            outputDurationMs = timeline.outputDurationMs,
            timelineMs = timeline.timelineMs(mediaMs),
            mediaMs = mediaMs,
            fadeLevel = fade,
            segment = point.segment,
            clipFadeLevel = clipFade,
            clipProgress = timeline.footageProgress(mediaMs),
            clipFileMs = fileMs,
            clipPlayedMs = clip.playedOffsetMs,
            transition = transition?.let { levels.getOrNull(it.outgoing)?.transition?.kind },
            transitionProgress = transition?.progress ?: 0f,
            incoming = transition?.incoming ?: -1,
            incomingFadeLevel = incoming?.fadeLevel ?: 1f,
            incomingProgress = incoming?.progress ?: 0f,
            incomingFileMs = incoming?.fileMs ?: 0L,
            incomingPlayedMs = transition?.incomingPlayedMs ?: 0L,
            incomingLoaded = incomingIndex,
        )
    }

    fun release() {
        onVideoSize = null
        onInterrupted = null
        videoPlayer.removeListener(videoListener)
        sourceBooster.release()
        incomingBooster.release()
        voices.release()
        videoPlayer.release()
        incomingPlayer.release()
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Moves the output clock on by [elapsedMs] and brings the player there.
     * Returns the media position now on screen.
     */
    private fun drive(elapsedMs: Double, now: Long): Long {
        // A player still loading holds the clock, so that it does not run ahead of the picture.
        val loading = !stepping && videoPlayer.playbackState == Player.STATE_BUFFERING
        if (!loading) outputClockMs += elapsedMs
        if (outputClockMs >= timeline.outputDurationMs) outputClockMs = 0.0 // loop, like a player
        val targetMs = timeline.mediaMsForOutput(outputClockMs.toLong())
        val target = timeline.locate(targetMs)
        // Along a speed curve the rate changes all the time: it is handed over in small steps.
        val curved = timeline.curved(target.segment)
        val rate = timeline.rateAt(targetMs).let { if (curved) quantized(it) else it }

        // Backwards, or faster / slower than the player can go: show it frame by frame.
        stepping = timeline.playsBackwards(target.segment) || rate < MIN_RATE || rate > MAX_RATE
        if (stepping) {
            videoPlayer.playWhenReady = false
            // Wait for the previous frame to land: a new exact seek cancels a pending one.
            val landed = videoPlayer.playbackState == Player.STATE_READY
            if ((landed && now - lastStepNanos >= REVERSE_STEP_NANOS) || now - lastStepNanos >= STEP_TIMEOUT_NANOS) {
                lastStepNanos = now
                seekPlayer(target)
            }
            return targetMs
        }

        val pitch = liveAudio.pitch * timeline.pitchAt(targetMs).let { if (curved) quantized(it) else it }
        if (appliedRate != rate || appliedPitch != pitch) {
            appliedRate = rate
            appliedPitch = pitch
            // Speed and pitch apart: the player keeps the pitch when only the speed changes.
            videoPlayer.playbackParameters = PlaybackParameters(rate, pitch)
        }
        videoPlayer.playWhenReady = true

        // Follow the player while it is where the clock expects it (also across the
        // boundary into the next clip, which it plays on its own); otherwise seek.
        val playerMs = playerMediaMs()
        val playerOutputMs = timeline.outputMs(playerMs).toDouble()
        return if (abs(playerOutputMs - outputClockMs) <= DRIFT_MS) {
            outputClockMs = playerOutputMs
            playerMs
        } else {
            // Give a seek time to land before asking for another one.
            if (now - lastSeekNanos >= RESEEK_NANOS) {
                lastSeekNanos = now
                seekPlayer(target)
            }
            targetMs
        }
    }

    /** Where the clip coming in is, for its sound and its picture. */
    private class IncomingState(val fileMs: Long, val fadeLevel: Float, val progress: Float)

    /**
     * Keeps [incomingPlayer] on the clip coming in at [outputMs] ([transition],
     * null outside one): loaded [PRELOAD_MS] ahead, playing along with the clock
     * (or stepped, like the main player), parked otherwise.
     */
    private fun driveIncoming(outputMs: Long, transition: PreviewTimeline.TransitionPoint?, now: Long): IncomingState? {
        val wanted = transition?.incoming ?: upcomingIncoming(outputMs)
        if (wanted < 0) {
            if (incomingIndex >= 0) releaseIncoming()
            return null
        }
        val clip = clips.getOrNull(wanted) ?: return null
        if (wanted != incomingIndex) {
            incomingIndex = wanted
            incomingPlayer.setMediaItem(mediaItem(clip, 0L))
            incomingPlayer.prepare()
            // Ready on the frame the transition opens with.
            val range = timeline.transitionOutputRange(wanted - 1)
            val start = if (range.isEmpty()) null else timeline.transitionAt((range.first + 1).coerceAtMost(range.last))
            incomingPlayer.seekTo(start?.incomingOffsetMs ?: 0L)
            lastIncomingSeekNanos = now
        }
        if (transition == null) {
            incomingPlayer.playWhenReady = false
            return null
        }
        val offset = transition.incomingOffsetMs
        val media = timeline.mediaMs(MediaPoint(wanted, offset))
        val rate = timeline.rateAt(media).let { if (timeline.curved(wanted)) quantized(it) else it }
        val stepped = !playing || timeline.playsBackwards(wanted) || rate < MIN_RATE || rate > MAX_RATE || clip.isStill
        if (stepped) {
            incomingPlayer.playWhenReady = false
            val off = abs(incomingPlayer.currentPosition - offset) > STEP_TOLERANCE_MS
            val landed = incomingPlayer.playbackState == Player.STATE_READY
            if (off && ((landed && now - lastIncomingSeekNanos >= REVERSE_STEP_NANOS) || now - lastIncomingSeekNanos >= STEP_TIMEOUT_NANOS)) {
                lastIncomingSeekNanos = now
                incomingPlayer.seekTo(offset)
            }
        } else {
            val pitch = liveAudio.pitch * timeline.pitchAt(media).let { if (timeline.curved(wanted)) quantized(it) else it }
            if (incomingRate != rate || incomingPitch != pitch) {
                incomingRate = rate
                incomingPitch = pitch
                incomingPlayer.playbackParameters = PlaybackParameters(rate, pitch)
            }
            incomingPlayer.playWhenReady = true
            // Output time between where it is and where the clock wants it.
            val driftMs = abs(incomingPlayer.currentPosition - offset) / rate.coerceAtLeast(MIN_RATE)
            if (driftMs > DRIFT_MS && now - lastIncomingSeekNanos >= RESEEK_NANOS) {
                lastIncomingSeekNanos = now
                incomingPlayer.seekTo(offset)
            }
        }
        val levelsIn = levels.getOrNull(wanted) ?: ClipLevels()
        val position = timeline.clipPosition(media)
        return IncomingState(
            fileMs = clip.startMs + offset,
            fadeLevel = fadeLevel(transition.incomingPlayedMs, position.playedLengthMs, levelsIn.fadeInSeconds, levelsIn.fadeOutSeconds),
            progress = timeline.footageProgress(media),
        )
    }

    /** The clip whose transition is about to start (playing) or is near (paused), -1 for none. */
    private fun upcomingIncoming(outputMs: Long): Int {
        var best = -1
        var bestDistance = Long.MAX_VALUE
        for (gap in 0 until (clips.size - 1)) {
            val range = timeline.transitionOutputRange(gap)
            if (range.isEmpty()) continue
            // Ahead of the clock (the range is on the output's clock, whatever the direction).
            val distance = range.first - outputMs
            if (distance in 0..PRELOAD_MS && distance < bestDistance) {
                best = gap + 1
                bestDistance = distance
            }
        }
        return best
    }

    private fun releaseIncoming() {
        if (incomingIndex < 0) return
        incomingIndex = -1
        incomingPlayer.playWhenReady = false
        incomingPlayer.clearMediaItems()
    }

    /**
     * Replaces the items of the main player whose start moved (a transition
     * before them changed). True when the one it is on was among them.
     */
    private fun replaceShiftedItems(): Boolean {
        if (clips.isEmpty() || heads == appliedHeads) return false
        val current = videoPlayer.currentMediaItemIndex
        var replacedCurrent = false
        for (index in clips.indices) {
            val head = heads.getOrElse(index) { 0L }
            if (head == appliedHeads.getOrElse(index) { 0L }) continue
            videoPlayer.replaceMediaItem(index, mediaItem(clips[index], head))
            if (index == current) replacedCurrent = true
        }
        appliedHeads = heads
        return replacedCurrent
    }

    /** [value] rounded to steps of about 1 %, so that a curve does not change the player at every frame. */
    private fun quantized(value: Float): Float {
        if (value <= 0f) return value
        val step = Math.round(ln(value.toDouble()) / RATE_STEP)
        return exp(step * RATE_STEP).toFloat().let { if (abs(it - 1f) < 1e-4f) 1f else it }
    }

    /** The item that plays [clip], starting [headMs] into it. */
    private fun mediaItem(clip: PreviewClip, headMs: Long): MediaItem {
        if (clip.isStill) {
            return MediaItem.Builder()
                .setUri(clip.uri)
                .setMimeType(MimeTypes.IMAGE_PNG)
                .setImageDurationMs((clip.expectedLengthMs - headMs).coerceAtLeast(MIN_CLIP_MS))
                .build()
        }
        val end = clip.endMs
        val endPosition = when {
            end == null -> C.TIME_END_OF_SOURCE
            end <= clip.startMs + headMs -> clip.startMs + headMs + MIN_CLIP_MS
            else -> end
        }
        return MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs.coerceAtLeast(0L) + headMs)
                    .setEndPositionMs(endPosition)
                    .build(),
            )
            .build()
    }

    private fun rebuildTimeline() {
        val timings = lengths.mapIndexed { index, length ->
            val clipLevels = levels.getOrNull(index) ?: ClipLevels()
            SegmentTiming(length, clipLevels.speed, clipLevels.reversed, clipLevels.curve, clipLevels.keepPitch, clipLevels.transition)
        }
        timeline = PreviewTimeline(timings, speed, reversed, keepPitch)
        heads = timings.indices.map { timeline.incomingFootageMs(it) }
    }

    /** The [heads] the main player's items were made with. */
    private var appliedHeads: List<Long> = emptyList()

    /**
     * A clip whose length was not known yet ("to the end of the file") takes the
     * length the player reports once it has loaded it. Clips with a known end
     * keep it, so that positions stay those of the editor's timeline.
     */
    private fun adoptReportedLength() {
        val index = videoPlayer.currentMediaItemIndex
        // The item starts after its head.
        val reported = videoPlayer.duration.let { if (it == C.TIME_UNSET || it <= 0) it else it + appliedHeads.getOrElse(index) { 0L } }
        val openEnded = clips.getOrNull(index)?.endMs == null
        if (openEnded && index in lengths.indices && reported != C.TIME_UNSET && reported > 0 && lengths[index] != reported) {
            val mediaMs = currentMediaMs()
            lengths[index] = reported
            rebuildTimeline()
            outputClockMs = timeline.outputMs(mediaMs).toDouble()
            if (replaceShiftedItems()) seekToMedia(mediaMs)
        }
    }

    /** Where the player itself is, on the media clock. */
    private fun playerMediaMs(): Long {
        val index = videoPlayer.currentMediaItemIndex
        return timeline.mediaMs(MediaPoint(index, videoPlayer.currentPosition.coerceAtLeast(0L) + appliedHeads.getOrElse(index) { 0L }))
    }

    /** Puts the main player on [point] (its item starts after the clip's head: the head shows its first frame). */
    private fun seekPlayer(point: MediaPoint) {
        videoPlayer.seekTo(point.segment, (point.offsetMs - appliedHeads.getOrElse(point.segment) { 0L }).coerceAtLeast(0L))
    }

    private fun currentMediaMs(): Long {
        if (clips.isEmpty()) return 0L
        if (playing) return timeline.mediaMsForOutput(outputClockMs.toLong())
        return playerMediaMs()
    }

    private fun currentOutputMs(): Long = if (playing) outputClockMs.toLong() else timeline.outputMs(currentMediaMs())

    private fun seekToMedia(mediaMs: Long) {
        if (clips.isEmpty()) return
        val point = timeline.locate(mediaMs)
        seekPlayer(point)
        val head = appliedHeads.getOrElse(point.segment) { 0L }
        outputClockMs = timeline.outputMs(timeline.mediaMs(if (point.offsetMs < head) point.copy(offsetMs = head) else point)).toDouble()
    }

    /** The system paused the player: the preview pauses where it is and the editor is told. */
    private fun interrupt() {
        if (!playing) return
        pause()
        onInterrupted?.invoke()
    }

    private fun applyPlayState() {
        // While playing, tick() decides whether the player runs or is stepped.
        if (!playing) {
            videoPlayer.playWhenReady = false
            incomingPlayer.playWhenReady = false
            voices.pauseAll()
        }
    }

    private companion object {
        /** Shortest clip the player is asked to show when the trim handles cross. */
        const val MIN_CLIP_MS = 500L

        /** A pause longer than this (app in background) is not played back as a jump. */
        const val MAX_TICK_MS = 250L

        /** Minimum time between backward steps; each one is an exact seek. */
        const val REVERSE_STEP_NANOS = 80_000_000L

        /** A step that has not landed after this long is replaced by the next one. */
        const val STEP_TIMEOUT_NANOS = 600_000_000L

        /** Rates the player's audio path supports; outside them clips are stepped. */
        const val MIN_RATE = 0.1f
        const val MAX_RATE = 8f

        /** How far (output time) the player may be from the clock before it is moved there. */
        const val DRIFT_MS = 300.0

        /** Steps the rate and pitch along a curve are handed over in (log scale, about 1 %). */
        const val RATE_STEP = 0.01

        /** Minimum time between two corrective seeks while playing forwards. */
        const val RESEEK_NANOS = 500_000_000L

        /** How far ahead of a transition (output time) the clip coming in is loaded. */
        const val PRELOAD_MS = 1_500L

        /** How far a paused or stepped incoming clip may be from its place before it is moved. */
        const val STEP_TOLERANCE_MS = 20L
    }
}

/**
 * Applies a linear gain to a player: the player volume covers 0..1 and a
 * LoudnessEnhancer on its audio session provides anything above.
 * Audio effects are optional on Android, so failures fall back to plain volume.
 */
@OptIn(UnstableApi::class)
internal class GainBooster {

    private var enhancer: LoudnessEnhancer? = null
    private var sessionId = C.AUDIO_SESSION_ID_UNSET
    private var appliedGain = -1f

    fun apply(player: ExoPlayer, gain: Float) {
        val currentSession = player.audioSessionId
        if (gain == appliedGain && currentSession == sessionId) return
        appliedGain = gain
        player.volume = gain.coerceIn(0f, 1f)

        val boostMillibels = if (gain > 1f) (volumeToDecibels(gain) * 100f).toInt() else 0
        if (boostMillibels == 0 && enhancer == null) {
            sessionId = currentSession
            return
        }
        try {
            if (enhancer == null || currentSession != sessionId) {
                enhancer?.release()
                enhancer = if (currentSession != C.AUDIO_SESSION_ID_UNSET) LoudnessEnhancer(currentSession) else null
            }
            sessionId = currentSession
            enhancer?.setTargetGain(boostMillibels)
            enhancer?.setEnabled(boostMillibels > 0)
        } catch (e: RuntimeException) {
            release()
        }
    }

    fun release() {
        try {
            enhancer?.release()
        } catch (e: RuntimeException) {
            // Nothing to do: the effect is already gone.
        }
        enhancer = null
    }
}
