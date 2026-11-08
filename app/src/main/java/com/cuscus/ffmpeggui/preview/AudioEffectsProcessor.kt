package com.cuscus.ffmpeggui.preview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.cuscus.ffmpeggui.core.audio.AudioStage
import com.cuscus.ffmpeggui.core.audio.EffectsChain
import com.cuscus.ffmpeggui.core.voice.VoiceStem
import com.cuscus.ffmpeggui.debug.DebugBench
import com.cuscus.ffmpeggui.debug.SoundTrial
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * The loudest sample the preview's players have handed to the speaker since it
 * was last read: what the audio preview's level follows. Written from the audio
 * threads, read from the UI.
 */
class LevelMeter {
    private val bits = AtomicInteger(0)

    fun report(peak: Float) {
        while (true) {
            val current = bits.get()
            if (Float.fromBits(current) >= peak) return
            if (bits.compareAndSet(current, peak.toRawBits())) return
        }
    }

    /** The peak since the last call (0..1, or more when the sound clips), then starts again. */
    fun take(): Float = Float.fromBits(bits.getAndSet(0))
}

/**
 * Runs the editor's sound effects (see `core.audio`) on what a player is about
 * to play, so that every change is heard at once. [stages] is read for every
 * buffer: a change reaches the speaker after the few hundred milliseconds the
 * player keeps buffered.
 *
 * Mono sound comes out on two channels, so that the stereo tools can act on
 * it as the export does (FFmpeg makes it stereo for them).
 */
@OptIn(UnstableApi::class)
internal class AudioEffectsProcessor(
    private val stages: () -> List<AudioStage>,
    private val meter: LevelMeter?,
) : BaseAudioProcessor() {

    private var chain: EffectsChain? = null
    private var work = FloatArray(0)
    private var shorts = ShortArray(0)
    private var floats = FloatArray(0)

    /** The stages the chain was last given: the same list is not compared again for every buffer. */
    private var lastStages: List<AudioStage>? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val channels = if (inputAudioFormat.channelCount == 1) 2 else inputAudioFormat.channelCount
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, channels, encoding)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        chain = EffectsChain(outputAudioFormat.sampleRate, outputAudioFormat.channelCount)
        lastStages = null
    }

    override fun onReset() {
        chain = null
        lastStages = null
        work = FloatArray(0)
        shorts = ShortArray(0)
        floats = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val inChannels = inputAudioFormat.channelCount
        val outChannels = outputAudioFormat.channelCount
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (float) 4 else 2
        val frames = input.remaining() / (bytesPerSample * inChannels)
        if (frames <= 0) {
            input.position(input.limit())
            return
        }
        val running = chain ?: EffectsChain(outputAudioFormat.sampleRate, outChannels).also { chain = it }
        val wanted = stages()
        if (wanted !== lastStages) {
            running.update(wanted)
            lastStages = wanted
        }
        val inSamples = frames * inChannels
        val outSamples = frames * outChannels
        // Read in bulk: one sample at a time through the buffer cost the most of the sound's
        // time, and sped up four times it is four times as much sound (issue #35).
        if (float) {
            if (floats.size < inSamples) floats = FloatArray(inSamples)
            input.asFloatBuffer().get(floats, 0, inSamples)
        } else {
            if (shorts.size < inSamples) shorts = ShortArray(inSamples)
            input.asShortBuffer().get(shorts, 0, inSamples)
        }
        input.position(input.limit())

        // Nothing to do to the sound: it goes through as it came, measured on the way.
        if (running.isEmpty && inChannels == outChannels) {
            val output = replaceOutputBuffer(outSamples * bytesPerSample)
            var peak = 0f
            if (float) {
                for (i in 0 until outSamples) peak = maxOf(peak, abs(floats[i]))
                output.asFloatBuffer().put(floats, 0, outSamples)
            } else {
                var loudest = 0
                for (i in 0 until outSamples) loudest = maxOf(loudest, abs(shorts[i].toInt()))
                peak = loudest / 32768f
                output.asShortBuffer().put(shorts, 0, outSamples)
            }
            output.position(outSamples * bytesPerSample)
            output.flip()
            meter?.report(peak)
            return
        }

        if (work.size < outSamples) work = FloatArray(outSamples)
        val samples = work
        // As FFmpeg reads 16-bit samples (divided by 32768); mono goes on both channels.
        var at = 0
        var from = 0
        for (n in 0 until frames) {
            for (c in 0 until inChannels) {
                samples[at + c] = if (float) floats[from + c] else shorts[from + c] / 32768f
            }
            if (inChannels == 1) samples[at + 1] = samples[at]
            at += outChannels
            from += inChannels
        }
        if (!running.isEmpty) running.process(samples, frames)

        var peak = 0f
        val output = replaceOutputBuffer(outSamples * bytesPerSample)
        if (float) {
            for (i in 0 until outSamples) peak = maxOf(peak, abs(samples[i]))
            output.asFloatBuffer().put(samples, 0, outSamples)
        } else {
            if (shorts.size < outSamples) shorts = ShortArray(outSamples)
            for (i in 0 until outSamples) {
                val value = samples[i]
                peak = maxOf(peak, abs(value))
                shorts[i] = (value * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            output.asShortBuffer().put(shorts, 0, outSamples)
        }
        output.position(outSamples * bytesPerSample)
        output.flip()
        meter?.report(peak)
    }
}

/**
 * Mixes the four channels of a voice's stems (see VoiceStem: the voice's left
 * and right, then the rest's) down to stereo, the voice at [levels]' first
 * gain and the rest at its second, read for every buffer. Any other sound
 * passes untouched.
 */
@OptIn(UnstableApi::class)
internal class StemMixProcessor(private val levels: () -> Pair<Float, Float>) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != VoiceStem.CHANNELS) return AudioProcessor.AudioFormat.NOT_SET
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (float) 4 else 2
        val frames = input.remaining() / (bytesPerSample * VoiceStem.CHANNELS)
        val (voice, rest) = levels()
        val output = replaceOutputBuffer(frames * 2 * bytesPerSample)
        val frame = FloatArray(VoiceStem.CHANNELS)
        val mixed = FloatArray(2)
        for (n in 0 until frames) {
            for (c in 0 until VoiceStem.CHANNELS) frame[c] = if (float) input.float else input.short / 32768f
            VoiceStem.mixFrame(frame, 0, voice, rest, mixed, 0)
            for (c in 0 until 2) {
                if (float) {
                    output.putFloat(mixed[c])
                } else {
                    output.putShort((mixed[c] * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                }
            }
        }
        input.position(input.limit())
        output.flip()
    }
}

/**
 * A player whose sound goes through [processors], in order, on its way to the
 * speaker (as 16-bit samples).
 *
 * With [prewarm] a second video decoder gets the next item of the playlist
 * ready while the current one plays: from one clip to the next the picture
 * goes on at once, instead of waiting for the new clip's first frames (decoded
 * from the key frame before the clip's start) on the same decoder.
 */
@OptIn(UnstableApi::class)
internal fun processingPlayer(context: Context, vararg processors: AudioProcessor, prewarm: Boolean = false): ExoPlayer {
    // A debug build may take Media3's own speed change instead, to compare (see DebugBench).
    val trial = DebugBench.trialFor(context)
    val renderers = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            // Processors only run on 16-bit output; float output would skip them.
            .setEnableFloatOutput(false)
            // Speed and pitch are changed by Android (AudioTrack), not by Media3's Sonic on the
            // player's thread: sped up, Sonic could not keep up (a 4x MP3 came out at 0.6x and
            // crackled, issue #35), Android's runs in the audio server and keeps up.
            // Media3 then makes the speaker's buffer long enough for its fastest speed (8x: two
            // seconds at normal speed). Made shorter, a 4x preview still ran dry: a change of the
            // sound effects is heard that much later instead.
            .setEnableAudioOutputPlaybackParameters(trial != SoundTrial.SONIC)
            .setAudioProcessors(arrayOf(*processors))
            .build()
    }
    renderers.experimentalSetEnableMediaCodecVideoRendererPrewarming(prewarm)
    return ExoPlayer.Builder(context, renderers).build()
}

/**
 * [pitch] within what Android shifts (AUDIO_TIMESTRETCH_PITCH_MIN and _MAX):
 * asked for more, it would refuse the whole change and keep the old speed. A
 * sound sped past 4x without its pitch kept is heard at 4x the pitch.
 */
internal fun androidPitch(pitch: Float): Float = pitch.coerceIn(MIN_ANDROID_PITCH, MAX_ANDROID_PITCH)

private const val MIN_ANDROID_PITCH = 0.25f
private const val MAX_ANDROID_PITCH = 4f

/**
 * What the preview's players apply to the final sound: the steps of the
 * editor's [com.cuscus.ffmpeggui.core.model.AudioEffects] and its pitch factor.
 * Shared by the footage's player and every placed sound's, read from their
 * audio threads.
 */
class LiveAudio {
    @Volatile
    var stages: List<AudioStage> = emptyList()

    @Volatile
    var pitch: Float = 1f

    val meter = LevelMeter()
}


/** A player made as the preview makes its own, sound path and all (for the debug dashboard's test). */
@OptIn(UnstableApi::class)
fun testPlayer(context: Context): ExoPlayer = processingPlayer(context, AudioEffectsProcessor({ emptyList() }, null))
