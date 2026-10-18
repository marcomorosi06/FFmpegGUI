package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.EncodingSettings
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.Stabilization
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.time.expectedOutputDurationMs
import com.cuscus.ffmpeggui.core.time.joinedDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Effects that belong to single clips, applied before the clips are joined. */
class ClipEffectsTest {

    private val builder = CommandBuilder { "/fonts/Regular.ttf" }

    private fun request(
        format: OutputFormat = OutputFormat.MP4,
        configure: ConversionRequest.() -> ConversionRequest = { this },
    ): ConversionRequest = ConversionRequest(
        format = format,
        inputFileName = "clip.mov",
        sources = listOf(SourceInfo(durationMs = 20_000, width = 1920, height = 1080, hasAudio = true)),
    ).configure()

    private fun args(request: ConversionRequest): List<String> =
        builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun List<String>.valueOf(flag: String): String = this[indexOf(flag) + 1]

    private val plain = Segment(0, 1_000, 3_000, clipId = "a")

    @Test
    fun `a clip's own speed and fades run on that clip only`() {
        val fast = Segment(0, 8_000, 9_500, Effects(speed = 2f, fadeInSeconds = 0.5f), clipId = "b")
        val request = request { copy(segments = listOf(plain, fast)) }
        assertEquals(
            "[1:v]setpts=0.5000*PTS,fade=t=in:st=0:d=0.5[sv1];" +
                "[1:a]atempo=2.0000,afade=t=in:st=0:d=0.5[sa1];" +
                "[0:v][0:a][sv1][sa1]concat=n=2:v=1:a=1[vcat][acat];" +
                "[vcat]null[vout]",
            args(request).valueOf("-filter_complex"),
        )
        // 2 s + 1.5 s at 2x.
        assertEquals(2_750L, request.joinedDurationMs())
        assertEquals(2_750L, request.expectedOutputDurationMs())
    }

    @Test
    fun `clip and whole video speeds multiply`() {
        val fast = Segment(0, 0, 4_000, Effects(speed = 2f))
        val request = request { copy(segments = listOf(fast), effects = Effects(speed = 0.5f)) }
        assertEquals(4_000L, request.expectedOutputDurationMs())
        assertEquals(
            "[0:v]setpts=0.5000*PTS[sv0];[0:a]atempo=2.0000[sa0];" +
                "[sv0][sa0]concat=n=1:v=1:a=1[vcat][acat];" +
                "[vcat]setpts=2.0000*PTS[vout];[acat]atempo=0.5000[aout]",
            args(request).valueOf("-filter_complex"),
        )
    }

    @Test
    fun `a clip fade-out is placed at the end of the clip`() {
        val clip = Segment(0, 0, 3_000, Effects(speed = 1.5f, fadeOutSeconds = 1f))
        val graph = args(request { copy(segments = listOf(clip)) }).valueOf("-filter_complex")
        // 3 s at 1.5x lasts 2 s, so the fade starts at 1 s.
        assertTrue(graph, graph.contains("setpts=0.6667*PTS,fade=t=out:st=1.000:d=1.0[sv0]"))
        assertTrue(graph, graph.contains("atempo=1.5000,afade=t=out:st=1.000:d=1.0[sa0]"))
    }

    @Test
    fun `a cropped or turned clip is fitted into the project frame`() {
        val turned = Segment(0, 5_000, 7_000, Effects(rotation = Rotation.DEG_90, crop = CropRect(0f, 0.1f, 1f, 0.5f)))
        val graph = args(request { copy(segments = listOf(plain, turned)) }).valueOf("-filter_complex")
        val fit = "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1"
        assertEquals(
            "[0:v]$fit[sv0];" +
                "[1:v]transpose=1,crop=w=trunc(iw*1.0000/2)*2:h=trunc(ih*0.5000/2)*2:x=trunc(iw*0.0000):y=trunc(ih*0.1000),$fit[sv1];" +
                "[sv0][0:a][sv1][1:a]concat=n=2:v=1:a=1[vcat][acat];" +
                "[vcat]null[vout]",
            graph,
        )
    }

    @Test
    fun `flips and colour do not need the frame fitted`() {
        val graded = Segment(0, 5_000, 7_000, Effects(flipHorizontal = true, color = ColorSettings(saturation = 0f)))
        val graph = args(request { copy(segments = listOf(plain, graded)) }).valueOf("-filter_complex")
        assertTrue(graph, graph.startsWith("[1:v]hflip,eq=brightness=0.00:contrast=1.00:saturation=0.00[sv1];"))
        assertFalse(graph, graph.contains("scale="))
    }

    @Test
    fun `a muted clip keeps its length and is silenced`() {
        val muted = Segment(0, 5_000, 7_000, Effects(speed = 2f, muted = true, volume = 3f, fadeInSeconds = 1f))
        val graph = args(request { copy(segments = listOf(plain, muted)) }).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("[1:a]atempo=2.0000,volume=0[sa1]"))
    }

    @Test
    fun `a reversed clip plays its picture and sound backwards`() {
        val backwards = Segment(0, 5_000, 7_000, Effects(reversed = true, volume = 0.5f))
        val graph = args(request { copy(segments = listOf(plain, backwards)) }).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("[1:v]reverse[sv1]"))
        assertTrue(graph, graph.contains("[1:a]areverse,asetpts=N/SR/TB,volume=0.50[sa1]"))
    }

    @Test
    fun `a silent file with its own speed gets silence of the played length`() {
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "a.mp4",
            sources = listOf(
                SourceInfo(durationMs = 20_000, width = 1280, height = 720),
                SourceInfo(durationMs = 20_000, width = 1280, height = 720, hasAudio = false),
            ),
            segments = listOf(Segment(0, 0, 2_000), Segment(1, 0, 3_000, Effects(speed = 2f))),
        )
        val graph = args(request).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("anullsrc=r=48000:cl=stereo,atrim=duration=1.500[sa1]"))
    }

    @Test
    fun `captions and pictures can belong to one clip`() {
        val request = request {
            copy(
                segments = listOf(plain, Segment(0, 5_000, 7_000, clipId = "b")),
                textOverlays = listOf(
                    TextOverlay(id = "t1", text = "Clip", clipId = "b"),
                    TextOverlay(id = "t2", text = "All"),
                ),
                imageOverlays = listOf(
                    ImagePlacement(widthFraction = 0.5f, clipId = "b"),
                    ImagePlacement(),
                ),
            )
        }
        val result = args(request)
        // Two clips, then the two pictures.
        assertEquals(
            listOf(
                "-ss", "00:00:01.000", "-t", "00:00:02.000", "-i", "input.mov",
                "-ss", "00:00:05.000", "-t", "00:00:02.000", "-i", "input.mov",
                "-i", "overlay_0.png", "-i", "overlay_1.png",
            ),
            result.subList(1, 17),
        )
        val clipText = "drawtext=fontfile='/fonts/Regular.ttf':text='Clip':fontcolor=0xFFFFFF" +
            ":fontsize=h*0.0500:x=(w-tw)*0.5000:y=(h-th)*0.5000"
        val wholeText = clipText.replace("'Clip'", "'All'")
        assertEquals(
            "[1:v]$clipText[c1main];" +
                "[2:v]scale=w=960:h='max(2,trunc(ow/dar/2)*2)'[c1ov0];" +
                "[c1main][c1ov0]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500[sv1];" +
                "[0:v][0:a][sv1][1:a]concat=n=2:v=1:a=1[vcat][acat];" +
                "[vcat]$wholeText[wmain];" +
                "[3:v]scale=w=384:h='max(2,trunc(ow/dar/2)*2)'[wov0];" +
                "[wmain][wov0]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500[vout]",
            result.valueOf("-filter_complex"),
        )
    }

    @Test
    fun `a single clip with effects of its own goes through the join`() {
        val graded = Segment(0, 0, 5_000, Effects(color = ColorSettings(brightness = 0.2f)))
        val result = args(request { copy(segments = listOf(graded), effects = Effects(volume = 2f)) })
        assertEquals(
            "[0:v]eq=brightness=0.20:contrast=1.00:saturation=1.00[sv0];" +
                "[sv0][0:a]concat=n=1:v=1:a=1[vcat][acat];" +
                "[vcat]null[vout];[acat]volume=2.00[aout]",
            result.valueOf("-filter_complex"),
        )
    }

    @Test
    fun `clip effects without a picture keep the sound part`() {
        val fast = Segment(0, 0, 4_000, Effects(speed = 2f, rotation = Rotation.DEG_90))
        val graph = args(request(OutputFormat.MP3) { copy(segments = listOf(plain, fast)) }).valueOf("-filter_complex")
        assertEquals("[1:a]atempo=2.0000[sa1];[0:a][sa1]concat=n=2:v=0:a=1[acat]", graph)
    }

    @Test
    fun `stream copy and frame extraction ignore clip effects`() {
        val fast = Segment(0, 0, 4_000, Effects(speed = 2f, color = ColorSettings(contrast = 2f)))
        val copy = args(request(OutputFormat.MKV) { copy(segments = listOf(fast), encoding = EncodingSettings(streamCopy = true)) })
        assertEquals(listOf("-y", "-to", "00:00:04.000", "-i", "input.mov", "-c", "copy", "output.mkv"), copy)

        val frames = args(
            request {
                copy(segments = listOf(fast), frameExtraction = FrameExtraction.Sequence(1f))
            },
        )
        assertEquals("fps=1.0000", frames.valueOf("-vf"))
    }

    @Test
    fun `stabilization analyses the clips with their own effects`() {
        val request = request {
            copy(
                segments = listOf(plain, Segment(0, 5_000, 7_000, Effects(flipVertical = true))),
                stabilization = Stabilization(),
            )
        }
        val paths = MediaPaths.aliasesFor(request)
        val analysis = builder.buildStabilizationAnalysis(request, paths).arguments
        assertTrue(analysis.valueOf("-filter_complex").startsWith("[1:v]vflip[sv1];[0:v][sv1]concat=n=2:v=1:a=0[vcat];"))
    }

    @Test
    fun `a still is a looped input as long as its clip, with silence for its sound`() {
        val request = request {
            copy(
                sources = sources + SourceInfo(width = 1080, height = 1920, hasAudio = false, isStill = true),
                segments = listOf(plain, Segment(1, 0, 3_000, clipId = "s")),
            )
        }
        val args = args(request)
        val still = args.indexOf("-loop")
        assertTrue(still >= 0)
        assertEquals(listOf("-loop", "1", "-framerate", "30", "-t", "00:00:03.000", "-i"), args.subList(still, still + 7))
        val graph = args.valueOf("-filter_complex")
        assertTrue(graph, graph.contains("anullsrc=r=48000:cl=stereo,atrim=duration=3.000[sa1]"))
        // The picture is fitted into the frame of the first clip.
        assertTrue(graph, graph.contains("[1:v]scale=1920:1080:force_original_aspect_ratio=decrease"))
        assertEquals(5_000L, request.expectedOutputDurationMs())
    }

    @Test
    fun `a sequence with a still is encoded even when remux is asked for`() {
        val request = request {
            copy(
                sources = sources + SourceInfo(width = 1920, height = 1080, hasAudio = false, isStill = true),
                segments = listOf(plain, Segment(1, 0, 2_000, clipId = "s")),
                encoding = EncodingSettings(streamCopy = true),
            )
        }
        val args = args(request)
        assertFalse(args.contains("copy"))
        assertTrue(args.contains("-filter_complex"))
    }

    @Test
    fun `a single frame taken from a still is not seeked`() {
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "frame.png",
            sources = listOf(SourceInfo(width = 640, height = 480, hasAudio = false, isStill = true)),
            segments = listOf(Segment(0, 0, 3_000, clipId = "s")),
            frameExtraction = FrameExtraction.SingleFrame(1_500),
        )
        assertFalse(args(request).contains("-ss"))
    }

    @Test
    fun `a reversed still is played forwards, as it looks the same`() {
        val request = request {
            copy(
                sources = sources + SourceInfo(width = 1920, height = 1080, hasAudio = false, isStill = true),
                segments = listOf(plain.copy(effects = Effects(reversed = true)), Segment(1, 0, 2_000, Effects(reversed = true), clipId = "s")),
            )
        }
        val graph = args(request).valueOf("-filter_complex")
        // The video clip is still reversed; the still is not.
        assertTrue(graph, graph.contains("[0:v]reverse"))
        assertFalse(graph, graph.contains("[1:v]reverse"))
    }
}
