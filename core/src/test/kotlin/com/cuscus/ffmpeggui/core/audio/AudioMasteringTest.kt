package com.cuscus.ffmpeggui.core.audio

import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.Compressor
import com.cuscus.ffmpeggui.core.model.Echo
import com.cuscus.ffmpeggui.core.model.Limiter
import com.cuscus.ffmpeggui.core.model.Modulation
import com.cuscus.ffmpeggui.core.model.ModulationKind
import com.cuscus.ffmpeggui.core.model.NoiseGate
import com.cuscus.ffmpeggui.core.model.Reverb
import com.cuscus.ffmpeggui.core.model.RoomSize
import com.cuscus.ffmpeggui.core.model.StereoImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioMasteringTest {

    private val rate = 48_000

    private fun sine(hz: Double, seconds: Double, level: Double = 0.5, channels: Int = 2): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * channels) { i -> (level * sin(2 * PI * hz * (i / channels) / rate)).toFloat() }
    }

    private fun rms(buffer: FloatArray, from: Int = 0): Double {
        var sum = 0.0
        for (i in from until buffer.size) sum += buffer[i].toDouble() * buffer[i]
        return sqrt(sum / (buffer.size - from))
    }

    private fun run(stage: AudioStage, buffer: FloatArray, channels: Int = 2): FloatArray {
        stage.processor(rate, channels).process(buffer, buffer.size / channels)
        return buffer
    }

    @Test
    fun `nothing set means no stages`() {
        assertTrue(AudioEffects().stages().isEmpty())
        assertTrue(AudioEffects().isNeutral)
        assertTrue(!AudioEffects(lowCutHz = 80).isNeutral)
    }

    @Test
    fun `stages come in the documented order`() {
        val effects = AudioEffects(
            equalizer = listOf(0f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f),
            lowCutHz = 80,
            gate = NoiseGate(),
            compressor = Compressor(),
            lofiBits = 8,
            modulation = Modulation(ModulationKind.TREMOLO, 4f, 0.5f),
            echo = Echo(),
            reverb = Reverb(RoomSize.HALL),
            stereo = StereoImage(width = 1.5f),
            limiter = Limiter(),
        )
        val names = effects.stages().map { it.filter.substringBefore('=') }
        assertEquals(
            listOf("highpass", "equalizer", "agate", "acompressor", "acrusher", "tremolo", "aecho", "aecho", "pan", "extrastereo", "acompressor"),
            names,
        )
    }

    @Test
    fun `filters carry the numbers the preview uses`() {
        val stages = AudioEffects(
            equalizer = listOf(-2.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 6f),
            compressor = Compressor(thresholdDb = -20f, ratio = 4f, attackMs = 10f, releaseMs = 100f, makeupDb = 6f),
            stereo = StereoImage(balance = 0.5f, swapSides = true),
        ).stages()
        assertEquals("equalizer=f=31:t=o:w=1:g=-2.5", stages[0].filter)
        assertEquals("equalizer=f=16000:t=o:w=1:g=6", stages[1].filter)
        assertEquals(
            "acompressor=threshold=0.1:ratio=4:attack=10:release=100:makeup=1.995262:knee=2.828427:detection=rms:link=average",
            stages[2].filter,
        )
        assertEquals("pan=stereo|c0=0.5*c1|c1=1*c0", stages.last().filter)
    }

    @Test
    fun `a peaking band gives its gain at its centre and the cuts take away their side`() {
        val peak = AudioStage.Biquad(BiquadType.EQUALIZER, 1000.0, 1.0, WidthType.OCTAVE, 6.0).coefficients(rate)!!
        assertEquals(6.0, biquadResponseDb(peak, rate, 1000.0), 0.01)
        assertEquals(0.0, biquadResponseDb(peak, rate, 20.0), 0.2)
        val lowCut = AudioStage.Biquad(BiquadType.HIGHPASS, 200.0, 0.707, WidthType.Q).coefficients(rate)!!
        assertTrue(biquadResponseDb(lowCut, rate, 50.0) < -20)
        assertEquals(0.0, biquadResponseDb(lowCut, rate, 5000.0), 0.1)
        // Above half the sample rate FFmpeg leaves the sound alone.
        assertNull(AudioStage.Biquad(BiquadType.EQUALIZER, 16000.0, 1.0, WidthType.OCTAVE, 6.0).coefficients(22_050))
    }

    @Test
    fun `the filter lowers a tone as its response says`() {
        val stage = AudioStage.Biquad(BiquadType.LOWPASS, 1000.0, 0.707, WidthType.Q)
        val out = run(stage, sine(4000.0, 0.5))
        val expected = 0.5 / sqrt(2.0) * Math.pow(10.0, biquadResponseDb(stage.coefficients(rate)!!, rate, 4000.0) / 20)
        assertEquals(expected, rms(out, out.size / 2), expected * 0.02)
    }

    @Test
    fun `an echo repeats an impulse after its delay`() {
        val stage = AudioEffects(echo = Echo(delayMs = 20, feedback = 0.5f)).stages().single()
        val buffer = FloatArray(2 * 2000)
        buffer[0] = 1f
        buffer[1] = 1f
        run(stage, buffer)
        val outGain = (stage as AudioStage.Echo).outGain
        assertEquals(outGain.toDouble(), buffer[0].toDouble(), 1e-6)
        assertEquals(0.5 * outGain, buffer[2 * 960].toDouble(), 1e-6)
        assertEquals(0.25 * outGain, buffer[2 * 1920 + 1].toDouble(), 1e-6)
        assertEquals(0.0, buffer[2 * 100].toDouble(), 1e-9)
    }

    @Test
    fun `the compressor turns a loud tone down and the gate a quiet one`() {
        val loud = run(AudioEffects(compressor = Compressor(thresholdDb = -30f, ratio = 8f)).stages().single(), sine(440.0, 1.0, 0.8))
        assertTrue(rms(loud, loud.size / 2) < 0.8 / sqrt(2.0) / 4)
        val quiet = run(AudioEffects(gate = NoiseGate(thresholdDb = -20f, reductionDb = -40f)).stages().single(), sine(440.0, 1.0, 0.01))
        assertTrue(rms(quiet, quiet.size / 2) < 0.01 / sqrt(2.0) / 10)
        val kept = run(AudioEffects(gate = NoiseGate(thresholdDb = -40f)).stages().single(), sine(440.0, 1.0, 0.5))
        assertEquals(0.5 / sqrt(2.0), rms(kept, kept.size / 2), 0.01)
    }

    @Test
    fun `the limiter keeps peaks near its ceiling`() {
        val out = run(AudioEffects(limiter = Limiter(ceilingDb = -6f)).stages().single(), sine(200.0, 1.0, 1.0))
        val peak = out.drop(out.size / 2).maxOf { abs(it) }
        assertTrue("peak $peak", peak < 0.6)
    }

    @Test
    fun `bit crushing rounds to the steps of its bits`() {
        val buffer = floatArrayOf(0.3f, -0.3f, 0.01f, -0.01f)
        run(AudioStage.Crusher(2), buffer)
        assertEquals(listOf(0.333333, -0.333333, 0.0, 0.0), buffer.map { Math.round(it * 1e6) / 1e6 + 0.0 })
    }

    @Test
    fun `width zero, balance and mono shape the two sides`() {
        val frame = floatArrayOf(1f, 0f)
        run(AudioStage.Width(0f), frame)
        assertEquals(listOf(0.5f, 0.5f), frame.toList())
        val panned = floatArrayOf(0.2f, 0.8f)
        run(AudioEffects(stereo = StereoImage(balance = -0.5f, swapSides = true)).stages().last(), panned)
        assertEquals(0.8f, panned[0], 1e-6f)
        assertEquals(0.1f, panned[1], 1e-6f)
        val mono = floatArrayOf(1f, 0f)
        run(AudioStage.Mono, mono)
        assertEquals(listOf(0.5f, 0.5f), mono.toList())
    }

    @Test
    fun `tremolo swings the level between full and the depth`() {
        val buffer = FloatArray(2 * rate) { 0.5f }
        run(AudioStage.Tremolo(2.0, 1.0), buffer)
        val levels = buffer.toList()
        assertEquals(0.5, levels.maxOf { it }.toDouble(), 1e-3)
        assertEquals(0.0, levels.minOf { it }.toDouble(), 1e-3)
    }

    @Test
    fun `modulation and reverb keep the level sensible`() {
        for (kind in ModulationKind.entries) {
            val out = run(AudioEffects(modulation = Modulation(kind, 1f, 0.7f)).stages().single(), sine(330.0, 1.0, 0.3))
            val level = rms(out, out.size / 2)
            assertTrue("$kind $level", level > 0.02 && level < 0.6)
        }
        for (size in RoomSize.entries) {
            val out = run(AudioEffects(reverb = Reverb(size, 0.8f)).stages().single(), sine(330.0, 1.0, 0.3))
            val level = rms(out, out.size / 2)
            assertTrue("$size $level", level in 0.05..0.6)
        }
    }

    @Test
    fun `the chain keeps the state of stages that stay`() {
        val chain = EffectsChain(rate, 2)
        val echo = AudioEffects(echo = Echo(delayMs = 20, feedback = 0.5f)).stages()
        chain.update(echo)
        val first = FloatArray(2 * 100)
        first[0] = 1f
        chain.process(first, 100)
        // The echo of the impulse comes in the next buffer, even after another stage is added.
        chain.update(echo + AudioStage.Width(1.5f))
        val second = FloatArray(2 * 1000)
        chain.process(second, 1000)
        assertTrue(abs(second[2 * 860]) > 0.3f)
    }
}
