package com.cuscus.ffmpeggui.preview

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.cuscus.ffmpeggui.core.audio.SpectrumBands
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/*
 * What the audio editor's spectrum reads: the sound each player of the
 * preview hands to the speaker, at the very moment it is heard.
 *
 * A player's sound effects run well before the sound is heard: the speaker
 * keeps up to two seconds in hand (see processingPlayer), so a spectrum of
 * the sound as it goes through them would run that far ahead of the music.
 * So each player keeps the last seconds of its sound (mono, after its
 * effects) with the time each sample plays at, as the player's own audio
 * sink counts it (TimedSink: the time of every buffer handed to it, and the
 * position it reports as heard). The spectrum reads, for every player that
 * plays, the samples that end where its speaker is, and adds them up as
 * their powers at the player's volume (core's SpectrumBands). Nothing is
 * kept while no spectrum is shown.
 */

/** The last seconds of one player's sound, timed as its speaker plays them. Written from its audio thread. */
class SpectrumTap internal constructor(private val hub: SpectrumHub) {

    /** The time of the buffer the sink is working on (its first sample), in the sink's microseconds. */
    @Volatile
    internal var inputPtsUs: Long = UNSET

    /** Where the speaker is, on the same clock. */
    @Volatile
    internal var playedUs: Long = UNSET

    private val lock = Any()
    private val ring = FloatArray(RING_FRAMES)

    /** Frames written ever; the first of the run that goes on without a jump, and its time. */
    private var written = 0L
    private var anchorIndex = 0L
    private var anchorUs = UNSET
    private var rate = 0

    /** True while a spectrum is shown: only then is the sound kept. */
    internal val wanted: Boolean get() = hub.watching

    /** The sound goes on from somewhere else (a seek): what was kept is not followed by it. */
    internal fun flush() {
        playedUs = UNSET
        synchronized(lock) { anchorUs = UNSET }
    }

    internal fun writeShorts(samples: ShortArray, frames: Int, channels: Int, sampleRate: Int) =
        write(frames, channels, sampleRate) { samples[it] / 32768f }

    internal fun writeFloats(samples: FloatArray, frames: Int, channels: Int, sampleRate: Int) =
        write(frames, channels, sampleRate) { samples[it] }

    private inline fun write(frames: Int, channels: Int, sampleRate: Int, sample: (Int) -> Float) {
        val time = inputPtsUs
        if (frames <= 0 || channels <= 0 || sampleRate <= 0) return
        synchronized(lock) {
            if (time == UNSET) {
                anchorUs = UNSET
                return
            }
            // Where the run so far says this buffer should be: a jump starts a new run.
            val expected = if (anchorUs == UNSET || sampleRate != rate) UNSET else anchorUs + (written - anchorIndex) * 1_000_000L / rate
            if (expected == UNSET || abs(time - expected) > JUMP_US) {
                anchorIndex = written
                anchorUs = time
                rate = sampleRate
            }
            var at = 0
            for (n in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += sample(at + c)
                ring[(written % RING_FRAMES).toInt()] = sum / channels
                written++
                at += channels
            }
        }
    }

    /** The rate of the samples kept. */
    internal val sampleRate: Int get() = rate

    /**
     * Fills [out] with the samples that end where the speaker is; false when
     * that is not known, or not kept (just after a seek, or a while ago).
     */
    internal fun window(out: FloatArray): Boolean {
        val at = playedUs
        if (at == UNSET) return false
        synchronized(lock) {
            if (anchorUs == UNSET || rate <= 0) return false
            val end = anchorIndex + (at - anchorUs) * rate / 1_000_000L
            val start = end - out.size
            if (start < anchorIndex || end > written || start < written - RING_FRAMES) return false
            for (i in out.indices) out[i] = ring[((start + i) % RING_FRAMES).toInt()]
        }
        return true
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE

        /** About five seconds at 48 kHz: the speaker's two seconds in hand, and the window. */
        const val RING_FRAMES = 1 shl 18

        /** A buffer this far from where the run says is a jump; closer, it is the pipeline lagging a buffer behind (a seek flushes anyway). */
        const val JUMP_US = 250_000L
    }
}

/**
 * The players of one preview and their [SpectrumTap]s, read by the spectrum
 * while it is shown ([watch]). Players are added and read on the main thread.
 */
class SpectrumHub {
    private val entries = ArrayList<Pair<Player, SpectrumTap>>()
    private val watchers = AtomicInteger()
    private val bands = SpectrumBands()
    private val window = FloatArray(bands.windowSize)

    internal val watching: Boolean get() = watchers.get() > 0

    /** The bands the powers are read in. */
    val bandCount: Int get() = bands.bandCount

    /** A tap for a player about to be made (see processingPlayer); [attach] it once made. */
    fun tap(): SpectrumTap = SpectrumTap(this)

    fun attach(player: Player, tap: SpectrumTap) {
        entries += player to tap
    }

    fun detach(player: Player) {
        entries.removeAll { it.first === player }
    }

    /** A spectrum is shown: the players keep their sound until [unwatch]. */
    fun watch() {
        watchers.incrementAndGet()
    }

    fun unwatch() {
        watchers.decrementAndGet()
    }

    /**
     * The power of each band of what is heard now into [powers]
     * ([bandCount] long): every player that plays, at its volume. False when
     * none does.
     */
    fun read(powers: DoubleArray): Boolean {
        powers.fill(0.0)
        var any = false
        for ((player, tap) in entries) {
            if (!player.isPlaying) continue
            val gain = player.volume
            if (gain <= 0f || !tap.window(window)) continue
            bands.add(window, tap.sampleRate, gain, powers)
            any = true
        }
        return any
    }
}

/** An audio sink that tells [tap] the time of every buffer handed to it and where its speaker is. */
@OptIn(UnstableApi::class)
internal class TimedSink(sink: AudioSink, private val tap: SpectrumTap) : ForwardingAudioSink(sink) {

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        tap.inputPtsUs = presentationTimeUs
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        if (position != AudioSink.CURRENT_POSITION_NOT_SET) tap.playedUs = position
        return position
    }

    override fun flush() {
        tap.flush()
        super.flush()
    }
}
