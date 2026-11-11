package com.cuscus.ffmpeggui.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumTest {

    @Test
    fun `a full-scale sine reads about 1 in its own band, far below elsewhere`() {
        val bands = SpectrumBands()
        val powers = DoubleArray(bands.bandCount)
        bands.add(sineSamples(1_000.0, 48_000, bands.windowSize), 48_000, 1f, powers)
        val band = bands.bandOf(1_000.0, 48_000)
        val amplitude = bandAmplitude(powers[band])
        // Between bins a Hann window loses up to 1.4 dB.
        assertTrue("amplitude $amplitude", amplitude in 0.84..1.01)
        assertTrue(bandAmplitude(powers[0]) < 0.001)
        assertTrue(bandAmplitude(powers[bands.bandCount - 1]) < 0.001)
    }

    @Test
    fun `sounds heard together add up as powers, each at its own level and rate`() {
        val bands = SpectrumBands()
        val alone = DoubleArray(bands.bandCount)
        bands.add(sineSamples(440.0, 44_100, bands.windowSize), 44_100, 0.5f, alone)
        val band = bands.bandOf(440.0, 44_100)
        val full = DoubleArray(bands.bandCount)
        bands.add(sineSamples(440.0, 44_100, bands.windowSize), 44_100, 1f, full)
        assertEquals(full[band] * 0.25, alone[band], full[band] * 1e-9)
        // A second sound, at another rate, lands in its own band.
        bands.add(sineSamples(5_000.0, 48_000, bands.windowSize), 48_000, 1f, alone)
        val high = bands.bandOf(5_000.0, 48_000)
        assertTrue(high > band)
        assertTrue(bandAmplitude(alone[high]) > 0.8)
        // The longer window: only its last samples are read.
        val longer = DoubleArray(bands.bandCount)
        bands.add(FloatArray(100) + sineSamples(440.0, 44_100, bands.windowSize), 44_100, 1f, longer)
        assertEquals(full[band], longer[band], full[band] * 1e-9)
    }

    @Test
    fun `bars jump up, fall slowly, and their caps fall after them`() {
        val bars = SpectrumBars(4)
        val loud = DoubleArray(4) { 0.1 }
        repeat(30) { bars.step(loud, 1f / 30f) }
        val top = bars.bars[0]
        assertTrue(top > 0.8f)
        bars.step(null, 1f / 30f)
        assertTrue(bars.bars[0] < top)
        assertTrue(bars.peaks[0] > bars.bars[0])
        repeat(300) { bars.step(null, 1f / 30f) }
        assertTrue(bars.settled)
    }

    @Test
    fun `the rise is the same at any refresh rate`() {
        val slow = SpectrumBars(1)
        val fast = SpectrumBars(1)
        val loud = DoubleArray(1) { 0.01 }
        slow.step(loud, 1f / 30f)
        fast.step(loud, 1f / 60f)
        fast.step(loud, 1f / 60f)
        assertEquals(slow.bars[0], fast.bars[0], 1e-5f)
    }

    @Test
    fun `the highs are lifted, as the ear hears them`() {
        val bars = SpectrumBars(2)
        val out = FloatArray(2)
        bars.levels(DoubleArray(2) { 1e-4 }, out)
        val tilt = (SpectrumBars.TILT_DB / (SpectrumBars.CEIL_DB - SpectrumBars.FLOOR_DB)).toFloat()
        assertEquals(tilt, out[1] - out[0], 1e-5f)
    }
}
