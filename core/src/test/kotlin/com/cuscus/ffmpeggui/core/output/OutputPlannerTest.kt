package com.cuscus.ffmpeggui.core.output

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.Split
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputPlannerTest {

    private val planner = OutputPlanner()
    private val base = ConversionRequest(
        format = OutputFormat.MKV,
        inputFileName = "My holiday 100%.final.mov",
        sources = listOf(SourceInfo(durationMs = 60_000)),
    )

    @Test
    fun `single file output`() {
        assertEquals(
            OutputPlan("/out/My_holiday_100_.final_7.mkv", "/out/My_holiday_100_.final_7.mkv", packed = false),
            planner.plan(base, "/out", 7),
        )
    }

    @Test
    fun `frame sequence goes into its own directory, then into a zip named like it`() {
        val plan = planner.plan(base.copy(frameExtraction = FrameExtraction.Sequence(1f)), "/out", 7)
        assertEquals(OutputPlan("/out/My_holiday_100_.final_frames_7/frame_%05d.png", "/out/My_holiday_100_.final_frames_7.zip", packed = true), plan)
        // Frames of a picture format are in it.
        val jpg = planner.plan(base.copy(format = OutputFormat.JPG, frameExtraction = FrameExtraction.Sequence(1f)), "/out", 7)
        assertEquals("/out/My_holiday_100_.final_frames_7/frame_%05d.jpg", jpg.commandPath)
    }

    @Test
    fun `single frame is a png next to the other outputs`() {
        val plan = planner.plan(base.copy(frameExtraction = FrameExtraction.SingleFrame(0)), "/out", 7)
        assertEquals("/out/My_holiday_100_.final_frame_7.png", plan.commandPath)
    }

    @Test
    fun `segments go into their own directory, then into a zip`() {
        val plan = planner.plan(base.copy(split = Split.ByDuration(10)), "/out", 7)
        assertEquals("/out/My_holiday_100_.final_segments_7/part_%03d.mkv", plan.commandPath)
        assertEquals("/out/My_holiday_100_.final_segments_7.zip", plan.resultPath)
        assertEquals(true, plan.packed)
    }

    @Test
    fun `split is ignored where it cannot work`() {
        val plan = planner.plan(base.copy(format = OutputFormat.GIF, split = Split.ByDuration(10)), "/out", 7)
        assertEquals(false, plan.packed)
    }

    @Test
    fun `unusable names fall back`() {
        assertEquals("file", sanitizeFileName("///"))
        assertEquals("a_b", sanitizeFileName("a b"))
        assertEquals("/out/output_1", planner.manualStem(base.copy(inputFileName = "?.mp4"), "/out", 1))
    }
}
