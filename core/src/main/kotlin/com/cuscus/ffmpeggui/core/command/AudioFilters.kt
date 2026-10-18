package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.audio.pitchFactor
import com.cuscus.ffmpeggui.core.audio.ringsOn
import com.cuscus.ffmpeggui.core.audio.soundEqualizerStages
import com.cuscus.ffmpeggui.core.audio.stages
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.PlacedSound
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import kotlin.math.abs

/**
 * Builds the sound filter chains: one per clip (before the join), one for the
 * joined sound and one per placed sound. Stateless.
 */
internal class AudioFilters {

    /**
     * Filters of one clip's sound.
     *
     * A muted clip keeps its speed change, so that its sound still lasts exactly
     * as long as its picture, and is then silenced.
     *
     * @param playedSeconds   length of the clip once its speed is applied, or null.
     * @param footageSeconds  length of the clip's footage, or null; a speed curve needs it.
     * @param label           makes the links and filter names of its graph unique.
     */
    fun clipChain(
        effects: Effects,
        playedSeconds: Double?,
        fileTime: String = "t",
        footageSeconds: Double? = null,
        label: String = "ac",
    ): List<String> {
        val curve = effects.curve
        if (effects.muted) {
            if (curve != null && playedSeconds != null) {
                return listOf("asetpts=PTS-STARTPTS", "volume=0") + exactly(playedSeconds)
            }
            return atempo(effects.effectiveSpeed) + "volume=0"
        }
        val filters = mutableListOf<String>()
        filters += shaping(effects, fileTime, footageSeconds, playedSeconds, label)
        filters += fades(effects, playedSeconds)
        return filters
    }

    /** Filters applied to the joined source sound. */
    fun wholeChain(request: ConversionRequest, outputDurationSeconds: Double?): List<String> {
        val filters = mutableListOf<String>()
        filters += shaping(request.effects)
        if (request.normalizeAudio) {
            // Single-pass loudnorm upsamples to 192 kHz internally; bring it back to a
            // rate every player and encoder accepts.
            filters += "loudnorm"
            filters += "aresample=$NORMALIZED_SAMPLE_RATE"
        }
        filters += fades(request.effects, outputDurationSeconds)
        return filters
    }

    /**
     * Filters of one placed sound: its part of the file, its volume and fades,
     * then moved to its place on the output clock, where the fades of the whole
     * video reach it too.
     *
     * When the output length is known the sound is padded / cut to exactly that
     * length. An endless `apad` must be avoided: FFmpeg never finishes a stream
     * that ends in one, and `amix=duration=first` cuts the mix short when the
     * other input goes through a filter with latency such as loudnorm.
     */
    fun soundChain(
        sound: PlacedSound,
        request: ConversionRequest,
        outputDurationSeconds: Double?,
    ): List<String> {
        val filters = mutableListOf("asetpts=N/SR/TB")

        val start = sound.trimStartMs.coerceAtLeast(0L)
        val end = sound.trimEndMs?.takeIf { it > start }
        if (start > 0 || end != null) {
            var trim = fmt("atrim=start=%.3f", start / 1000.0)
            if (end != null) trim += fmt(":end=%.3f", end / 1000.0)
            filters += trim
        }
        filters += "asetpts=PTS-STARTPTS"

        // Repeats and pitch changes count samples, so the rate is made known first.
        val pitchFactor = Math.pow(2.0, sound.pitchSemitones / 12.0) * (if (sound.keepPitch) 1.0 else sound.rate.toDouble())
        val shiftsPitch = abs(pitchFactor - 1.0) > PITCH_EPSILON
        val repeated = sound.loops > 1 && end != null
        if (repeated || shiftsPitch) filters += "aresample=$WORK_SAMPLE_RATE"
        if (repeated) {
            val samples = Math.round((end!! - start) / 1000.0 * WORK_SAMPLE_RATE)
            filters += "aloop=loop=${sound.loops - 1}:size=$samples"
            filters += "asetpts=N/SR/TB"
        }
        // Speed and pitch: the samples are re-labelled at a rate that gives the pitch,
        // brought back to the working rate, then stretched in time (keeping that pitch)
        // until the speed is the one asked for.
        var tempo = sound.rate.toDouble()
        if (shiftsPitch) {
            val shiftedRate = Math.round(WORK_SAMPLE_RATE * pitchFactor)
            filters += "asetrate=$shiftedRate"
            filters += "aresample=$WORK_SAMPLE_RATE"
            tempo = sound.rate * WORK_SAMPLE_RATE.toDouble() / shiftedRate
        }
        if (abs(tempo - 1.0) > PITCH_EPSILON) filters += atempo(tempo.toFloat())
        // Equaliser: low shelf, a bell in the middle, high shelf (the preview runs the same).
        filters += soundEqualizerStages(sound.bassDb, sound.midDb, sound.trebleDb).map { it.filter }
        // Its own effects, on float samples like those of the final sound. An echo would
        // ring on past its end: it is cut there, as the preview's player stops there.
        val ownStages = sound.effects.stages()
        if (ownStages.isNotEmpty()) {
            filters += "aformat=sample_fmts=fltp"
            filters += ownStages.map { it.filter }
            val length = sound.lengthMs
            if (length != null && ownStages.any { it.ringsOn }) filters += fmt("atrim=end=%.3f", length / 1000.0)
        }
        if (sound.normalize) {
            filters += "loudnorm"
            filters += "aresample=$NORMALIZED_SAMPLE_RATE"
        }
        // Its volume, on its own clock (from its start on the video).
        filters += volume(sound.volume, sound.keyframes, "t")
        // Its own fades, on its own clock: before it is moved into place.
        val ownEffects = Effects(fadeInSeconds = sound.fadeInSeconds, fadeOutSeconds = sound.fadeOutSeconds)
        filters += fades(ownEffects, sound.lengthMs?.let { it / 1000.0 })
        val delayMs = sound.startMs.coerceAtLeast(0L)
        if (delayMs > 0) {
            filters += "adelay=delays=$delayMs:all=1"
            // adelay keeps the timestamps it was given; after a rate change (pitch) they no
            // longer match the samples and the padding and cut below would go wrong.
            filters += "asetpts=N/SR/TB"
        }
        filters += fades(request.effects, outputDurationSeconds)
        if (outputDurationSeconds != null) {
            filters += fmt("apad=whole_dur=%.3f", outputDurationSeconds)
            filters += fmt("atrim=end=%.3f", outputDurationSeconds)
        }
        return filters
    }

    /**
     * What is done to the final sound ([AudioEffects]), after everything is mixed:
     * its steps as filters working on float samples, then the pitch shift (which
     * keeps the tempo). Echoes and choruses ring on past the end of the input;
     * when the output length is known the sound is cut there, as the preview hears it.
     */
    fun masterChain(effects: AudioEffects, outputDurationSeconds: Double?): List<String> {
        val stages = effects.stages()
        val factor = pitchFactor(effects.pitchSemitones.coerceIn(-12f, 12f))
        val shifts = abs(factor - 1.0) > PITCH_EPSILON
        if (stages.isEmpty() && !shifts) return emptyList()
        val filters = mutableListOf<String>()
        if (stages.isNotEmpty()) {
            filters += "aformat=sample_fmts=fltp"
            filters += stages.map { it.filter }
        }
        if (shifts) {
            val shiftedRate = Math.round(WORK_SAMPLE_RATE * factor)
            filters += "aresample=$WORK_SAMPLE_RATE"
            filters += "asetrate=$shiftedRate"
            filters += "aresample=$WORK_SAMPLE_RATE"
            filters += atempo((WORK_SAMPLE_RATE.toDouble() / shiftedRate).toFloat())
        }
        if (outputDurationSeconds != null && stages.any { it.ringsOn }) {
            filters += fmt("atrim=end=%.3f", outputDurationSeconds)
        }
        return filters
    }

    /**
     * Mixes [inputs] streams at their own levels; see [soundChain] for the
     * duration rule. Without a known output length the mix lasts as long as the
     * source sound when [sourceFirst] (it is the first input), else as the longest.
     */
    fun mixer(inputs: Int, outputDurationSeconds: Double?, sourceFirst: Boolean): String {
        val duration = if (outputDurationSeconds != null || !sourceFirst) "longest" else "first"
        return "amix=inputs=$inputs:duration=$duration:dropout_transition=2:normalize=0"
    }

    /**
     * Denoise, direction, speed and volume (its keyframes following [time],
     * seconds in the clip's file). A speed curve is followed when the lengths
     * of the footage and of what it plays are known, else its average speed.
     */
    private fun shaping(
        effects: Effects,
        time: String = "t",
        footageSeconds: Double? = null,
        playedSeconds: Double? = null,
        label: String = "aw",
    ): List<String> {
        val filters = mutableListOf<String>()
        if (effects.audioDenoise > 0f) {
            filters += fmt("anlmdn=s=%.2f", effects.audioDenoise.coerceIn(0f, 1f) * ANLMDN_MAX_STRENGTH)
        }
        if (effects.reversed) {
            // areverse hands the samples back with their old timestamps, which are out of
            // order once the footage has been joined from several segments: renumber them.
            filters += "areverse"
            filters += "asetpts=N/SR/TB"
        }
        val curve = effects.curve
        filters += when {
            curve != null && footageSeconds != null && playedSeconds != null && footageSeconds > 0.0 ->
                if (effects.keepPitch) tempoCurve(curve, footageSeconds, playedSeconds, label)
                else tapeCurve(curve, footageSeconds, playedSeconds, label)
            effects.keepPitch -> atempo(effects.effectiveSpeed)
            else -> tape(effects.effectiveSpeed.toDouble())
        }
        filters += volume(effects.volume, effects.keyframes, time)
        return filters
    }

    /**
     * Faster or slower with the pitch rising and falling with it, like a tape:
     * the same samples played at another rate.
     */
    private fun tape(speed: Double): List<String> {
        if (abs(speed - 1.0) < 1e-4 || speed <= 0.0) return emptyList()
        val rate = Math.round(WORK_SAMPLE_RATE * speed)
        return listOf("aresample=$WORK_SAMPLE_RATE", "asetrate=$rate", "aresample=$WORK_SAMPLE_RATE")
    }

    /**
     * A speed curve keeping the pitch: atempo told a new tempo every step of the
     * footage (asendcmd, on the footage's clock). atempo loses a little time at
     * every change, so the sound is then padded or cut to exactly its length.
     * Slow speeds need several atempo in a row (one goes down to 0.5).
     */
    private fun tempoCurve(curve: SpeedCurve, footageSeconds: Double, playedSeconds: Double, label: String): List<String> {
        val steps = curve.steps(footageSeconds)
        var count = 1
        while (Math.pow(curve.slowest.toDouble(), 1.0 / count) < ATEMPO_MIN && count < 8) count++
        val names = (0 until count).map { "atempo@$label$it" }
        fun tempo(speed: Double) = fmt("%.4f", Math.pow(speed, 1.0 / count))
        val commands = steps.joinToString(";") { step ->
            fmt("%.3f ", step.from) + names.joinToString(", ") { "$it tempo ${tempo(step.speed)}" }
        }
        val first = tempo(steps.first().speed)
        return listOf("asetpts=PTS-STARTPTS", "asendcmd=c='$commands'") +
            names.map { "$it=tempo=$first" } +
            exactly(playedSeconds)
    }

    /**
     * A speed curve with the pitch following it: the footage cut into steps,
     * each played as a tape at its own speed (the samples are the same, so the
     * joins are seamless), joined again.
     */
    private fun tapeCurve(curve: SpeedCurve, footageSeconds: Double, playedSeconds: Double, label: String): List<String> {
        val steps = curve.steps(footageSeconds, maxSteps = MAX_TAPE_STEPS)
        val inputs = steps.indices.joinToString("") { "[${label}i$it]" }
        val graph = StringBuilder("asplit=${steps.size}$inputs")
        steps.forEachIndexed { k, step ->
            val rate = Math.round(WORK_SAMPLE_RATE * step.speed).coerceAtLeast(1L)
            graph.append(";[${label}i$k]")
            graph.append(fmt("atrim=start=%.4f:end=%.4f", step.from, step.to))
            graph.append(",asetpts=PTS-STARTPTS,asetrate=$rate,aresample=$WORK_SAMPLE_RATE[${label}o$k]")
        }
        graph.append(";")
        steps.indices.forEach { graph.append("[${label}o$it]") }
        graph.append("concat=n=${steps.size}:v=0:a=1")
        return listOf("asetpts=PTS-STARTPTS", "aresample=$WORK_SAMPLE_RATE", graph.toString()) + exactly(playedSeconds)
    }

    /** The sound padded with silence or cut to exactly [seconds]. */
    private fun exactly(seconds: Double): List<String> =
        listOf(fmt("apad=whole_dur=%.4f", seconds), fmt("atrim=duration=%.4f", seconds))

    /** [volume], or its keyframes over [time] (worked out again on every frame of samples). */
    private fun volume(volume: Float, keyframes: Keyframes, time: String): List<String> = when {
        keyframes.animates(KeyProperty.VOLUME) ->
            listOf("volume=eval=frame:volume='max(0,${keyframes.expression(KeyProperty.VOLUME, volume, time)})'")
        else -> {
            val level = keyframes.valueAt(KeyProperty.VOLUME, 0L, volume)
            if (level != 1f) listOf(fmt("volume=%.2f", level)) else emptyList()
        }
    }

    private fun fades(effects: Effects, durationSeconds: Double?): List<String> {
        val filters = mutableListOf<String>()
        if (effects.fadeInSeconds > 0f) {
            filters += fmt("afade=t=in:st=0:d=%.1f", effects.fadeInSeconds)
        }
        fadeOutStart(effects.fadeOutSeconds, durationSeconds)?.let { start ->
            filters += fmt("afade=t=out:st=%.3f:d=%.1f", start, effects.fadeOutSeconds)
        }
        return filters
    }

    /**
     * One atempo instance only accepts 0.5..100, so slower speeds are reached by
     * chaining several of them.
     */
    private fun atempo(speed: Float): List<String> {
        if (speed == 1f || speed <= 0f) return emptyList()
        val filters = mutableListOf<String>()
        var remaining = speed
        while (remaining < 0.5f) { filters += "atempo=0.5"; remaining *= 2f }
        while (remaining > 100f) { filters += "atempo=100.0"; remaining /= 100f }
        if (remaining != 1f) filters += fmt("atempo=%.4f", remaining)
        return filters
    }

    companion object {
        /** Rate sounds are brought to before they are repeated or shifted in pitch. */
        const val WORK_SAMPLE_RATE = 48000

        /** Pitch factors closer to 1 than this are no shift. */
        private const val PITCH_EPSILON = 0.0005

        /** Slowest tempo one atempo takes. */
        private const val ATEMPO_MIN = 0.5

        /** Most steps a speed curve with the pitch following it is cut into. */
        private const val MAX_TAPE_STEPS = 120

        /** anlmdn strength reached when the request asks for full denoising. */
        const val ANLMDN_MAX_STRENGTH = 10f
        const val NORMALIZED_SAMPLE_RATE = 48000
    }
}
