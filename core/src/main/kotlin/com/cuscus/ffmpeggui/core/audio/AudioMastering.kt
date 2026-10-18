package com.cuscus.ffmpeggui.core.audio

import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.ModulationKind
import com.cuscus.ffmpeggui.core.model.RoomSize
import kotlin.math.exp
import kotlin.math.pow

/** Linear level of [db] decibels. */
internal fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

/**
 * The steps of [AudioEffects] in the order they are applied (pitch apart: the
 * export shifts it at the end, the preview player does it). Empty when the
 * sound is left as it is.
 */
fun AudioEffects.stages(): List<AudioStage> {
    val stages = mutableListOf<AudioStage>()

    // Tone.
    if (lowCutHz > 0) stages += AudioStage.Biquad(BiquadType.HIGHPASS, lowCutHz.toDouble(), BUTTERWORTH_Q, WidthType.Q)
    if (highCutHz > 0) stages += AudioStage.Biquad(BiquadType.LOWPASS, highCutHz.toDouble(), BUTTERWORTH_Q, WidthType.Q)
    equalizerGains.forEachIndexed { band, gain ->
        if (gain != 0f) {
            stages += AudioStage.Biquad(
                BiquadType.EQUALIZER, AudioEffects.EQ_BANDS_HZ[band].toDouble(), 1.0, WidthType.OCTAVE, written(gain.toDouble()),
            )
        }
    }

    // Dynamics.
    gate?.let { gate ->
        stages += AudioStage.Gate(
            threshold = written(dbToLinear(gate.thresholdDb.toDouble().coerceIn(-80.0, 0.0))),
            range = written(dbToLinear(gate.reductionDb.toDouble().coerceIn(-80.0, 0.0))),
            ratio = GATE_RATIO,
            attackMs = GATE_ATTACK_MS,
            releaseMs = GATE_RELEASE_MS,
            knee = DEFAULT_KNEE,
        )
    }
    compressor?.let { c ->
        stages += AudioStage.Compressor(
            threshold = written(dbToLinear(c.thresholdDb.toDouble().coerceIn(-60.0, 0.0)).coerceAtLeast(MIN_COMPRESSOR_THRESHOLD)),
            ratio = written(c.ratio.toDouble().coerceIn(1.0, 20.0)),
            attackMs = written(c.attackMs.toDouble().coerceIn(0.1, 2000.0)),
            releaseMs = written(c.releaseMs.toDouble().coerceIn(1.0, 9000.0)),
            makeup = written(dbToLinear(c.makeupDb.toDouble().coerceIn(0.0, 24.0))),
            knee = DEFAULT_KNEE,
        )
    }

    // Character.
    if (lofiBits > 0) stages += AudioStage.Crusher(lofiBits.coerceIn(2, 16))
    modulation?.let { m ->
        val depth = m.depth.toDouble().coerceIn(0.0, 1.0)
        val rate = m.rateHz.toDouble()
        stages += when (m.kind) {
            ModulationKind.CHORUS -> {
                val r = rate.coerceIn(0.1, 5.0)
                AudioStage.Chorus(
                    inGain = 0.7f,
                    outGain = 0.9f,
                    voices = CHORUS_VOICES.map { (delay, decay, speedFactor, depthBase, depthSpan) ->
                        ChorusVoice(
                            delayMs = delay,
                            decay = decay,
                            speedHz = written((r * speedFactor).toFloat()),
                            depthMs = written((depthBase + depthSpan * depth).toFloat()),
                        )
                    },
                )
            }
            ModulationKind.FLANGER -> AudioStage.Flanger(
                delayMs = 0.0,
                depthMs = written(1.0 + 9.0 * depth),
                regenPercent = written(40.0 * depth),
                widthPercent = 71.0,
                speedHz = written(rate.coerceIn(0.1, 10.0)),
                phasePercent = 25.0,
            )
            ModulationKind.PHASER -> AudioStage.Phaser(
                inGain = 0.4,
                outGain = 0.74,
                delayMs = 3.0,
                decay = written(0.2 + 0.6 * depth),
                speedHz = written(rate.coerceIn(0.1, 2.0)),
            )
            ModulationKind.TREMOLO -> AudioStage.Tremolo(written(rate.coerceIn(0.1, 20.0)), written(depth))
            ModulationKind.VIBRATO -> AudioStage.Vibrato(written(rate.coerceIn(0.1, 20.0)), written(depth))
        }
    }

    // Space.
    echo?.let { e ->
        val feedback = e.feedback.coerceIn(0.05f, 0.9f)
        val delay = e.delayMs.coerceIn(20, 2000)
        val taps = (1..ECHO_MAX_TAPS).map { k -> k to written(feedback.toDouble().pow(k).toFloat()) }
            .filter { (_, decay) -> decay >= ECHO_MIN_DECAY }
        stages += AudioStage.Echo(
            inGain = 1f,
            outGain = written((1.0 / (1.0 + 0.5 * feedback)).toFloat()),
            delaysMs = taps.map { (k, _) -> (delay * k).toFloat() },
            decays = taps.map { it.second },
        )
    }
    reverb?.let { r ->
        val mix = r.mix.coerceIn(0.05f, 1f).toDouble()
        val (delays, tau) = when (r.size) {
            RoomSize.ROOM -> ROOM_TAPS_MS to 0.25
            RoomSize.HALL -> HALL_TAPS_MS to 0.6
            RoomSize.CATHEDRAL -> CATHEDRAL_TAPS_MS to 1.2
        }
        val weights = delays.map { exp(-it / 1000.0 / tau) }
        val scale = REVERB_LEVEL * mix / weights.sum()
        val taps = delays.zip(weights).map { (delay, weight) -> delay to written((weight * scale).toFloat()) }
            .filter { it.second > 0f }
        stages += AudioStage.Echo(
            inGain = 1f,
            outGain = written((1.0 / (1.0 + 0.5 * mix)).toFloat()),
            delaysMs = taps.map { it.first.toFloat() },
            decays = taps.map { it.second },
        )
    }

    // Stereo image.
    if (!stereo.isNeutral) {
        stages += AudioStage.StereoLayout
        val width = stereo.width.coerceIn(0f, 2f)
        if (width != 1f) stages += AudioStage.Width(written(width))
        val balance = stereo.balance.coerceIn(-1f, 1f)
        if (balance != 0f || stereo.swapSides) {
            stages += AudioStage.Balance(
                leftGain = written((1f - balance).coerceAtMost(1f)),
                rightGain = written((1f + balance).coerceAtMost(1f)),
                swap = stereo.swapSides,
            )
        }
        if (stereo.mono) stages += AudioStage.Mono
    }

    limiter?.let { l ->
        stages += AudioStage.Compressor(
            threshold = written(dbToLinear(l.ceilingDb.toDouble().coerceIn(-24.0, 0.0))),
            ratio = 20.0,
            attackMs = LIMITER_ATTACK_MS,
            releaseMs = LIMITER_RELEASE_MS,
            makeup = 1.0,
            knee = 1.0,
            rms = false,
            linkMaximum = true,
        )
    }
    return stages
}

/** True when a stage keeps sounding after the input ends (FFmpeg then adds a tail). */
internal val AudioStage.ringsOn: Boolean
    get() = this is AudioStage.Echo || this is AudioStage.Chorus

/**
 * The three-band equaliser of a placed sound, as the same filters the export
 * uses (low shelf, a bell in the middle, high shelf).
 */
fun soundEqualizerStages(bassDb: Float, midDb: Float, trebleDb: Float): List<AudioStage> = buildList {
    if (bassDb != 0f) add(AudioStage.Biquad(BiquadType.BASS, SOUND_EQ_BASS_HZ, 0.5, WidthType.Q, written(bassDb.toDouble())))
    if (midDb != 0f) add(AudioStage.Biquad(BiquadType.EQUALIZER, SOUND_EQ_MID_HZ, SOUND_EQ_MID_WIDTH, WidthType.OCTAVE, written(midDb.toDouble())))
    if (trebleDb != 0f) add(AudioStage.Biquad(BiquadType.TREBLE, SOUND_EQ_TREBLE_HZ, 0.5, WidthType.Q, written(trebleDb.toDouble())))
}

/** Pitch factor of a shift of [semitones]. */
fun pitchFactor(semitones: Float): Double = 2.0.pow(semitones / 12.0)

/**
 * Runs a list of stages on interleaved float frames, for the preview. When the
 * stages change ([update]), the ones that stay the same keep their state, so
 * moving one slider does not cut the echo of another effect.
 */
class EffectsChain(private val sampleRate: Int, private val channels: Int) {
    private var running: List<Pair<AudioStage, StageProcessor>> = emptyList()

    /** Sets the stages to run from now on. */
    fun update(stages: List<AudioStage>) {
        if (stages == running.map { it.first }) return
        val spare = running.toMutableList()
        running = stages.map { stage ->
            val index = spare.indexOfFirst { it.first == stage }
            if (index >= 0) spare.removeAt(index) else stage to stage.processor(sampleRate, channels)
        }
    }

    val isEmpty: Boolean get() = running.isEmpty()

    fun process(buffer: FloatArray, frames: Int) {
        for ((_, processor) in running) processor.process(buffer, frames)
    }
}

private const val BUTTERWORTH_Q = 0.707
private const val DEFAULT_KNEE = 2.828427
private const val MIN_COMPRESSOR_THRESHOLD = 0.000977
private const val GATE_RATIO = 4.0
private const val GATE_ATTACK_MS = 5.0
private const val GATE_RELEASE_MS = 200.0
private const val LIMITER_ATTACK_MS = 0.1
private const val LIMITER_RELEASE_MS = 50.0
private const val ECHO_MAX_TAPS = 8
private const val ECHO_MIN_DECAY = 0.02f
private const val REVERB_LEVEL = 1.5

/** Turning points of a placed sound's equaliser (the export uses the same). */
const val SOUND_EQ_BASS_HZ = 100.0
const val SOUND_EQ_MID_HZ = 1000.0
const val SOUND_EQ_MID_WIDTH = 1.5
const val SOUND_EQ_TREBLE_HZ = 4000.0

/** The two voices of the chorus: delay, level, speed (times the rate), depth (base + span * depth). */
private data class VoiceShape(val delayMs: Float, val decay: Float, val speedFactor: Double, val depthBase: Double, val depthSpan: Double)

private val CHORUS_VOICES = listOf(VoiceShape(45f, 0.45f, 1.0, 1.0, 3.0), VoiceShape(60f, 0.35f, 1.25, 1.2, 3.5))

/** Early reflections of each space, in ms: spread so that they do not line up. */
private val ROOM_TAPS_MS = listOf(7, 11, 17, 23, 29, 37, 43, 53, 61, 71, 83, 97)
private val HALL_TAPS_MS = listOf(13, 23, 37, 47, 61, 79, 97, 113, 131, 157, 181, 211)
private val CATHEDRAL_TAPS_MS = listOf(19, 37, 53, 79, 107, 137, 173, 211, 257, 307, 367, 431)
