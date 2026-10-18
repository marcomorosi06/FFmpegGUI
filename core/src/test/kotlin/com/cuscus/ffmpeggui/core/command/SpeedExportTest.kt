package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import com.cuscus.ffmpeggui.core.model.SpeedPoint
import com.cuscus.ffmpeggui.core.model.SpeedPreset
import com.cuscus.ffmpeggui.core.time.expectedOutputDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Speed curves and the pitch following the speed, in the command. */
class SpeedExportTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun graph(request: ConversionRequest): String =
        builder.build(request, MediaPaths.aliasesFor(request)).arguments.let { it[it.indexOf("-filter_complex") + 1] }

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true)),
    )

    private val ramp = SpeedCurve(listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 2f)))

    @Test
    fun `a clip along a curve has its frames re-timed, its sound's tempo stepped and cut to length`() {
        val request = video.copy(segments = listOf(Segment(0, 1_000, 5_000, Effects(speedCurve = ramp))))
        val g = graph(request)
        assertTrue(g, g.contains("setpts='(if(gte(st(1,clip(T,0,4.000000)),-1),"))
        assertTrue(g, g.contains("asendcmd=c='0.000 atempo@ac00 tempo 0.5"))
        assertTrue(g, g.contains(",atempo@ac00=tempo="))
        val played = Math.round(ramp.playedLengthMs(4_000.0)) / 1000.0
        assertTrue(g, g.contains(String.format(java.util.Locale.US, "apad=whole_dur=%.4f,atrim=duration=%.4f", played, played)))
        assertEquals(Math.round(ramp.playedLengthMs(4_000.0)), request.expectedOutputDurationMs())
    }

    @Test
    fun `slow curves chain several atempo`() {
        val slow = SpeedCurve(listOf(SpeedPoint(0f, 0.1f), SpeedPoint(1f, 1f)))
        val g = graph(video.copy(segments = listOf(Segment(0, 0, 3_000, Effects(speedCurve = slow)))))
        assertTrue(g, g.contains("atempo@ac03=tempo="))
        assertFalse(g, g.contains("atempo@ac04"))
    }

    @Test
    fun `a curve with the pitch following it plays the footage as a tape, step by step`() {
        val effects = Effects(speedCurve = SpeedPreset.MONTAGE.curve, keepPitch = false)
        val g = graph(video.copy(segments = listOf(Segment(0, 0, 3_000, effects))))
        assertTrue(g, g.contains("aresample=48000,asplit=30[ac0i0][ac0i1]"))
        assertTrue(g, g.contains(";[ac0i0]atrim=start=0.0000:end=0.1000,asetpts=PTS-STARTPTS,asetrate="))
        assertTrue(g, g.contains("[ac0o29]concat=n=30:v=0:a=1,apad=whole_dur="))
        assertFalse(g, g.contains("atempo"))
    }

    @Test
    fun `a constant speed with the pitch following it`() {
        val clip = graph(video.copy(segments = listOf(Segment(0, 0, 3_000, Effects(speed = 1.5f, keepPitch = false)))))
        assertTrue(clip, clip.contains("aresample=48000,asetrate=72000,aresample=48000"))
        assertFalse(clip, clip.contains("atempo"))
        val whole = builder.build(video.copy(effects = Effects(speed = 0.5f, keepPitch = false)), MediaPaths.aliasesFor(video)).arguments
        assertTrue(whole.joinToString(" "), whole.joinToString(" ").contains("asetrate=24000"))
    }

    @Test
    fun `a clip's captions and volume follow the curve's clocks`() {
        val effects = Effects(
            speedCurve = ramp,
            keyframes = com.cuscus.ffmpeggui.core.model.Keyframes(
                listOf(
                    com.cuscus.ffmpeggui.core.model.Keyframe(1_000, mapOf(com.cuscus.ffmpeggui.core.model.KeyProperty.VOLUME to 1f)),
                    com.cuscus.ffmpeggui.core.model.Keyframe(5_000, mapOf(com.cuscus.ffmpeggui.core.model.KeyProperty.VOLUME to 0f)),
                ),
            ),
        )
        val g = graph(video.copy(segments = listOf(Segment(0, 1_000, 5_000, effects))))
        // The sound's file time: the footage played after t, from the curve's inverse, kept in a variable.
        assertTrue(g, g.contains("volume=eval=frame:volume='max(0,if(gte(st(2,(1.0000+(if(gte(st(1,clip(t,0,"))
    }
}
