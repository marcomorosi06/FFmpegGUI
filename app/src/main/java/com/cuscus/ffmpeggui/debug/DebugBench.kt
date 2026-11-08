package com.cuscus.ffmpeggui.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The preview's sound path, to compare (issue #35: a sped-up preview that
 * crackled because Media3's Sonic could not keep up on the player's thread).
 */
enum class SoundTrial {
    /** The usual path: Android changes the speed (AudioTrack playback parameters). */
    NONE,

    /** As before the fix: Media3's Sonic changes the speed. */
    SONIC,
}

/**
 * What the debug dashboard sets and shows (see DebugDashboard). Only a build
 * that can be debugged has it: in a release build [enabled] is false, every
 * trial is [SoundTrial.NONE] and nothing is counted. Main thread only.
 */
object DebugBench {

    /** Whether this build can be debugged (set by [init]). */
    var enabled: Boolean = false
        private set

    private var preferences: android.content.SharedPreferences? = null

    /** The sound path the preview's players are made with; the editor is made again on a change. */
    var soundTrial: SoundTrial by mutableStateOf(SoundTrial.NONE)
        private set

    /** Times the main player's speaker ran dry, dropped frames and seeks asked by the clock, since the last reset. */
    val underruns = mutableIntStateOf(0)
    val droppedFrames = mutableIntStateOf(0)
    val seeks = mutableIntStateOf(0)

    /** The rate the main player was last set to. */
    val rate = mutableFloatStateOf(1f)

    /**
     * How much of the file the main player has read ahead of where it plays, ms:
     * near 0 while the sound runs dry, the file is read too slowly; well above,
     * the time goes in decoding or in the sound path.
     */
    val aheadMs = androidx.compose.runtime.mutableLongStateOf(0L)

    /** The main player's audio decoder, and its input format. */
    var audioDecoder: String by mutableStateOf("-")

    /** The file of the editor's first clip, which the bare tests play. */
    var testUri: android.net.Uri? = null

    /** What the running or last bare test says; null before the first. */
    var testReport: String? by mutableStateOf(null)
        private set

    /** Whether a bare test is playing. */
    var testing: Boolean by mutableStateOf(false)
        private set

    private var testPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private val testHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Plays the editor's first file at [speed] for [TEST_MS] on a player of its
     * own, and counts how often its speaker runs dry. [ours] makes it the way
     * the preview makes its players (its sound path, effects, scheduling);
     * otherwise a player exactly as Media3 builds it. Fine on the bare player
     * and not in the preview: the time goes in the preview's own work.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun runTest(context: Context, speed: Float, ours: Boolean, platformSpeed: Boolean = false) {
        if (!enabled) return
        stopTest()
        val uri = testUri ?: run {
            testReport = "Open a file in the editor first."
            return
        }
        val player = when {
            ours -> com.cuscus.ffmpeggui.preview.testPlayer(context)
            // The speed changed by Android (AudioTrack) instead of Media3's Sonic.
            platformSpeed -> androidx.media3.exoplayer.ExoPlayer.Builder(
                context,
                androidx.media3.exoplayer.DefaultRenderersFactory(context).setEnableAudioOutputPlaybackParameters(true),
            ).build()
            else -> androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        }
        testPlayer = player
        testing = true
        val kind = when {
            ours -> "Preview's player"
            platformSpeed -> "Bare, Android's speed"
            else -> "Bare Media3"
        }
        var underruns = 0
        val started = android.os.SystemClock.elapsedRealtime()
        fun report(done: Boolean) {
            val playedS = player.currentPosition / 1000f
            val realS = (android.os.SystemClock.elapsedRealtime() - started) / 1000f
            testReport = "%s at %.1fx: %d underruns, %.1f s of file in %.1f s (%.2fx)%s".format(
                java.util.Locale.ROOT,
                kind,
                speed,
                underruns,
                playedS,
                realS,
                if (realS > 0f) playedS / realS else 0f,
                if (done) "" else "...",
            )
        }
        player.addAnalyticsListener(
            object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onAudioUnderrun(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    bufferSize: Int,
                    bufferSizeMs: Long,
                    elapsedSinceLastFeedMs: Long,
                ) {
                    underruns++
                }
            },
        )
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        player.prepare()
        player.play()
        val tick = object : Runnable {
            override fun run() {
                if (testPlayer !== player) return
                if (android.os.SystemClock.elapsedRealtime() - started >= TEST_MS) {
                    report(done = true)
                    stopTest()
                } else {
                    report(done = false)
                    testHandler.postDelayed(this, TEST_REPORT_MS)
                }
            }
        }
        testHandler.postDelayed(tick, TEST_REPORT_MS)
    }

    /**
     * Decodes the sound of the editor's first file as fast as the decoder goes,
     * nothing played, for [DECODE_MS] at most, on a thread of its own: how many
     * times the real time the decoder alone manages. Under the speed asked, the
     * decoder is what cannot keep up.
     */
    fun runDecodeTest(context: Context) {
        if (!enabled || testing) return
        val uri = testUri ?: run {
            testReport = "Open a file in the editor first."
            return
        }
        testing = true
        testReport = "Decoding..."
        val appContext = context.applicationContext
        Thread {
            val report = try {
                decodeSpeed(appContext, uri)
            } catch (e: Exception) {
                "Decode test failed: ${e.javaClass.simpleName}: ${e.message}"
            }
            testHandler.post {
                testReport = report
                testing = false
            }
        }.start()
    }

    private fun decodeSpeed(context: Context, uri: android.net.Uri): String {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            return "No sound in the file."
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(android.media.MediaFormat.KEY_MIME)!!
        val rate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
        val codec = android.media.MediaCodec.createDecoderByType(mime)
        val name = codec.name
        codec.configure(format, null, null, 0)
        codec.start()
        val info = android.media.MediaCodec.BufferInfo()
        var frames = 0L
        var inputDone = false
        var outputDone = false
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            while (!outputDone && android.os.SystemClock.elapsedRealtime() - started < DECODE_MS) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(1_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 1_000)
                if (out >= 0) {
                    frames += info.size / (2 * channels)
                    if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    codec.releaseOutputBuffer(out, false)
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        val wallS = (android.os.SystemClock.elapsedRealtime() - started) / 1000f
        val audioS = frames.toFloat() / rate
        return "Decoder %s: %.1f s of sound in %.2f s (%.1fx the real time)".format(
            java.util.Locale.ROOT,
            name,
            audioS,
            wallS,
            if (wallS > 0f) audioS / wallS else 0f,
        )
    }

    fun stopTest() {
        testHandler.removeCallbacksAndMessages(null)
        testPlayer?.release()
        testPlayer = null
        testing = false
    }

    /** Reads whether the build can be debugged, and the trial chosen before. */
    fun init(context: Context) {
        enabled = isDebuggable(context)
        if (!enabled) return
        val stored = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences = stored
        soundTrial = SoundTrial.entries.firstOrNull { it.name == stored.getString(KEY_TRIAL, null) } ?: SoundTrial.NONE
    }

    fun choose(trial: SoundTrial) {
        if (!enabled || trial == soundTrial) return
        soundTrial = trial
        preferences?.edit()?.putString(KEY_TRIAL, trial.name)?.apply()
        resetCounts()
    }

    fun resetCounts() {
        underruns.intValue = 0
        droppedFrames.intValue = 0
        seeks.intValue = 0
    }

    /** The trial a player made now takes: always the usual path in a release build. */
    fun trialFor(context: Context): SoundTrial = if (isDebuggable(context)) soundTrial else SoundTrial.NONE

    fun isDebuggable(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private const val TEST_MS = 10_000L
    private const val DECODE_MS = 10_000L
    private const val TEST_REPORT_MS = 500L
    private const val PREFERENCES = "debug_bench"
    private const val KEY_TRIAL = "sound_trial"
}
