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
    }

    override fun onReset() {
        chain = null
        work = FloatArray(0)
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
        if (work.size < frames * outChannels) work = FloatArray(frames * outChannels)
        val samples = work
        // Read, as FFmpeg reads 16-bit samples (divided by 32768).
        var at = 0
        for (n in 0 until frames) {
            for (c in 0 until inChannels) {
                samples[at + c] = if (float) input.float else input.short / 32768f
            }
            if (inChannels == 1) samples[at + 1] = samples[at]
            at += outChannels
        }
        input.position(input.limit())
        val running = chain ?: EffectsChain(outputAudioFormat.sampleRate, outChannels).also { chain = it }
        running.update(stages())
        if (!running.isEmpty) running.process(samples, frames)

        var peak = 0f
        val output = replaceOutputBuffer(frames * outChannels * bytesPerSample)
        for (i in 0 until frames * outChannels) {
            val value = samples[i]
            val level = abs(value)
            if (level > peak) peak = level
            if (float) {
                output.putFloat(value)
            } else {
                output.putShort((value * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        output.flip()
        meter?.report(peak)
    }
}

/** A player whose sound goes through [processor] on its way to the speaker (as 16-bit samples). */
@OptIn(UnstableApi::class)
internal fun processingPlayer(context: Context, processor: AudioProcessor): ExoPlayer {
    val renderers = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            // Processors only run on 16-bit output; float output would skip them.
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf(processor))
            .build()
    }
    return ExoPlayer.Builder(context, renderers).build()
}

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
