package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.VideoHdr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The denoise comparison's command. The graphs below were run with FFmpeg 6.1
 * on a noisy test video: the "after" half matched, pixel for pixel, the same
 * square of the frame from a run of the filter over the whole video.
 */
class DenoiseSampleTest {

    private val video = DenoiseSampleRequest(
        sourceUri = "content://clip",
        positionMs = 1_600L,
        clipStartMs = 0L,
        still = false,
        strengths = listOf(4f, 6f),
        focusX = 0.3f,
        focusY = 0.7f,
    )

    private val inner = "crop=w='iw-2*min(32,trunc(iw/4))':h='ih-2*min(32,trunc(ih/4))'" +
        ":x='min(32,trunc(iw/4))':y='min(32,trunc(ih/4))'"

    @Test
    fun `warms the denoise up on the frames before the one shown`() {
        val args = DenoiseSample.command(video, "in.mp4", "out.png").arguments
        assertEquals(
            listOf(
                "-nostats", "-y", "-ss", "1.100", "-i", "in.mp4",
                "-filter_complex",
                "[0:v]crop=w='min(iw,576)':h='min(ih,576)':x='(iw-ow)*0.3000':y='(ih-oh)*0.7000',split=2[raw][dirty];" +
                    "[raw]$inner[before];" +
                    "[dirty]hqdn3d=4.0,hqdn3d=6.0,$inner[after];" +
                    "[before][after]hstack=inputs=2,trim=start=0.500,setpts=PTS-STARTPTS,format=rgb24",
                "-frames:v", "1", "-an", "-sn", "-dn", "out.png",
            ),
            args,
        )
    }

    @Test
    fun `the warm-up starts no earlier than the clip`() {
        val args = DenoiseSample.command(video.copy(positionMs = 5_200L, clipStartMs = 5_000L), "in.mp4", "out.png").arguments
        assertEquals("5.000", args[args.indexOf("-ss") + 1])
        assertTrue(args[args.indexOf("-filter_complex") + 1].contains("trim=start=0.200,"))
    }

    @Test
    fun `a frame at the very start needs no seek and no warm-up`() {
        val args = DenoiseSample.command(video.copy(positionMs = 0L), "in.mp4", "out.png").arguments
        assertFalse(args.contains("-ss"))
        assertFalse(args[args.indexOf("-filter_complex") + 1].contains("trim"))
    }

    @Test
    fun `a picture is one frame`() {
        val args = DenoiseSample.command(video.copy(still = true, positionMs = 3_000L), "in.png", "out.png").arguments
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertFalse(args.contains("-ss"))
        assertFalse(graph.contains("trim"))
    }

    @Test
    fun `zero strengths are skipped, and none at all still makes a comparison`() {
        val one = DenoiseSample.graph(video.copy(strengths = listOf(0f, 3f)), warmUpMs = 0L)
        assertTrue(one.contains("[dirty]hqdn3d=3.0,crop"))
        assertFalse(one.contains("hqdn3d=0"))
        val none = DenoiseSample.graph(video.copy(strengths = listOf(0f, 0f)), warmUpMs = 0L)
        assertTrue(none.contains("[dirty]null,crop"))
        assertFalse(video.copy(strengths = listOf(0f, 0f)).denoises)
        assertTrue(video.denoises)
    }

    @Test
    fun `a strength past the top is the top`() {
        assertTrue(DenoiseSample.graph(video.copy(strengths = listOf(25f)), warmUpMs = 0L).contains("hqdn3d=10.0,"))
    }

    @Test
    fun `hdr footage made sdr is denoised sdr, as in the export`() {
        val graph = DenoiseSample.graph(video.copy(hdr = VideoHdr.PQ, hdrKept = false), warmUpMs = 0L)
        assertTrue(graph.startsWith("[0:v]zscale=tin=smpte2084:"))
        // Tone mapped once, before the square is cut out.
        assertEquals(1, Regex("tonemap=hable").findAll(graph).count())
        assertTrue(graph.indexOf("tonemap=hable") < graph.indexOf("split=2"))
    }

    @Test
    fun `hdr footage kept hdr is denoised as it is and tone mapped half by half`() {
        val graph = DenoiseSample.graph(video.copy(hdr = VideoHdr.HLG, hdrKept = true), warmUpMs = 0L)
        assertTrue(graph.startsWith("[0:v]crop="))
        // hstack drops the colour tags zscale needs: each half is tone mapped before it.
        assertEquals(2, Regex("tonemap=hable").findAll(graph).count())
        assertTrue(graph.lastIndexOf("tonemap=hable") < graph.indexOf("hstack"))
        assertTrue(graph.contains("zscale=tin=arib-std-b67:"))
    }

    @Test
    fun `the focus is kept inside the frame`() {
        val graph = DenoiseSample.graph(video.copy(focusX = -1f, focusY = 3f), warmUpMs = 0L)
        assertTrue(graph.contains(":x='(iw-ow)*0.0000':y='(ih-oh)*1.0000'"))
    }

    @Test
    fun `the area shown is the square without its margin`() {
        // 1920 wide: the 576 square cut out starts at 1344 * 0.5 = 672, the 512 shown at 704.
        val area = DenoiseSample.shownArea(1920, 1080, 0.5f, 0f)
        assertEquals(704f / 1920, area.left, 1e-5f)
        assertEquals(1216f / 1920, area.right, 1e-5f)
        assertEquals(32f / 1080, area.top, 1e-5f)
        assertEquals(544f / 1080, area.bottom, 1e-5f)
    }

    @Test
    fun `a small frame is shown nearly whole`() {
        // 300 x 200: the whole frame is cut out, a quarter of it at most taken away on each side.
        val area = DenoiseSample.shownArea(300, 200, 0.2f, 0.9f)
        assertEquals(32f / 300, area.left, 1e-5f)
        assertEquals(268f / 300, area.right, 1e-5f)
        assertEquals(32f / 200, area.top, 1e-5f)
        assertEquals(168f / 200, area.bottom, 1e-5f)
    }

    @Test
    fun `a tap centres the square there, as far as the edges allow`() {
        val (x, y) = DenoiseSample.focusFor(1920, 1080, 0.5f, 0.5f)
        assertEquals(0.5f, x, 1e-5f)
        assertEquals(0.5f, y, 1e-5f)
        val area = DenoiseSample.shownArea(1920, 1080, x, y)
        assertEquals(0.5f, (area.left + area.right) / 2, 1e-5f)
        // Near a corner it stops at the edge.
        assertEquals(0f to 1f, DenoiseSample.focusFor(1920, 1080, 0.01f, 0.99f))
    }

    @Test
    fun `a tap on a frame no bigger than the square changes nothing`() {
        assertEquals(0.5f to 0.5f, DenoiseSample.focusFor(400, 300, 0.1f, 0.9f))
        assertEquals(0.5f to 0.5f, DenoiseSample.focusFor(0, 0, 0.1f, 0.9f))
    }
}
