package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Echo
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.EncodingSettings
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframe
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.GifDither
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.ImageEffect
import com.cuscus.ffmpeggui.core.model.ImageOutput
import com.cuscus.ffmpeggui.core.model.ImageBorder
import com.cuscus.ffmpeggui.core.model.ImageTone
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.core.model.VideoHdr
import com.cuscus.ffmpeggui.core.model.Limiter
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.MotionKind
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.OverlayVideo
import com.cuscus.ffmpeggui.core.model.PlacedSound
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.Split
import com.cuscus.ffmpeggui.core.model.Stabilization
import com.cuscus.ffmpeggui.core.model.StereoImage
import com.cuscus.ffmpeggui.core.model.StillMotion
import com.cuscus.ffmpeggui.core.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandBuilderTest {

    private val builder = CommandBuilder { bold -> if (bold) "/fonts/Bold.ttf" else "/fonts/Regular.ttf" }

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

    @Test
    fun `plain mp4 conversion`() {
        assertEquals(
            listOf(
                "-y", "-i", "input.mov",
                "-vcodec", "libx264", "-crf", "18", "-preset", "medium",
                "-acodec", "aac", "-b:a", "128k",
                "output.mp4",
            ),
            args(request()),
        )
    }

    @Test
    fun `trim is applied on the input`() {
        val result = args(request { copy(segments = listOf(Segment(0, 1_500, 10_000))) })
        assertEquals(
            listOf("-y", "-ss", "00:00:01.500", "-to", "00:00:10.000", "-i", "input.mov"),
            result.take(7),
        )
    }

    @Test
    fun `picture filters keep a deterministic order`() {
        val result = args(
            request {
                copy(
                    effects = Effects(
                        rotation = Rotation.DEG_90,
                        flipHorizontal = true,
                        crop = CropRect(left = 0.25f, top = 0.25f, width = 0.5f, height = 0.5f),
                        color = ColorSettings(brightness = 0.1f, contrast = 1.2f, saturation = 0.8f),
                        speed = 2f,
                        fadeInSeconds = 1f,
                        fadeOutSeconds = 2f,
                    ),
                    canvas = CanvasRatio.SQUARE,
                    encoding = EncodingSettings(resolution = Resolution.P720, framerate = Framerate.FPS30),
                )
            },
        )
        assertEquals(
            "hflip,transpose=1," +
                "crop=w=trunc(iw*0.5000/2)*2:h=trunc(ih*0.5000/2)*2:x=trunc(iw*0.2500):y=trunc(ih*0.2500)," +
                "pad=width='ceil(max(iw,ih)/2)*2':height='ceil(max(ih,iw)/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black," +
                "scale=-2:720," +
                "eq=brightness=0.10:contrast=1.20:saturation=0.80," +
                "setpts=0.5000*PTS,fps=30," +
                // 20 s at 2x is 10 s of output, so the fade-out starts at 8 s.
                "fade=t=in:st=0:d=1.0,fade=t=out:st=8.000:d=2.0",
            result.valueOf("-vf"),
        )
        assertEquals("atempo=2.0000,afade=t=in:st=0:d=1.0,afade=t=out:st=8.000:d=2.0", result.valueOf("-af"))
    }

    @Test
    fun `identity crop and fit canvas add no filter`() {
        val result = args(
            request {
                copy(effects = Effects(crop = CropRect()))
            },
        )
        assertFalse("-vf" in result)
    }

    @Test
    fun `very slow playback chains atempo`() {
        val result = args(request { copy(effects = Effects(speed = 0.25f)) })
        assertEquals("atempo=0.5,atempo=0.5000", result.valueOf("-af"))
    }

    @Test
    fun `removing audio drops the audio encoder`() {
        val result = args(request { copy(effects = Effects(muted = true)) })
        assertTrue("-an" in result)
        assertFalse("-acodec" in result)
    }

    @Test
    fun `text overlay is escaped for every parser layer`() {
        val result = args(
            request { copy(textOverlays = listOf(TextOverlay(id = "t", text = "it's 5:00 (100%)", color = 0xFF00FF00))) },
        )
        assertEquals(
            "drawtext=fontfile='/fonts/Bold.ttf'" +
                """:text='it\'\''s 5\:00 (100\\%)'""" +
                ":fontcolor=0x00FF00:fontsize=h*0.0500:x=(w-tw)*0.5000:y=(h-th)*0.5000",
            result.valueOf("-vf"),
        )
    }

    @Test
    fun `blank text overlays are skipped`() {
        assertFalse("-vf" in args(request { copy(textOverlays = listOf(TextOverlay(text = "  "))) }))
    }

    @Test
    fun `image overlay keeps the source audio filters`() {
        val result = args(
            request {
                copy(
                    imageOverlays = listOf(ImagePlacement(x = 1f, y = 0f, widthFraction = 0.2f, opacity = 0.5f)),
                    effects = Effects(speed = 2f),
                )
            },
        )
        assertEquals(listOf("-i", "input.mov", "-i", "overlay_0.png"), result.subList(1, 5))
        assertEquals(
            "[0:v]setpts=0.5000*PTS[wmain];" +
                "[1:v]scale=w=384:h='max(2,trunc(ow/dar/2)*2)'[wovs0];" +
                "[wovs0]format=rgba,colorchannelmixer=aa=0.500[wov0];" +
                "[wmain][wov0]overlay=x=(W-w)*1.0000:y=(H-h)*0.0000[vout];" +
                "[0:a]atempo=2.0000[aout]",
            result.valueOf("-filter_complex"),
        )
        assertEquals(listOf("-map", "[vout]", "-map", "[aout]"), result.subList(7, 11))
    }

    @Test
    fun `image overlay without audio filters maps the source audio directly`() {
        val result = args(request { copy(imageOverlays = listOf(ImagePlacement())) })
        assertTrue(result.containsAll(listOf("-map", "0:a?")))
    }

    @Test
    fun `a placed sound is mixed with the source audio`() {
        val result = args(
            request {
                copy(sounds = listOf(PlacedSound(startMs = 250, trimStartMs = 1_000, trimEndMs = 5_000, volume = 0.5f)))
            },
        )
        assertEquals(
            "[0:v]null[vout];" +
                "[0:a]pan=stereo|FL=FL+FC|FR=FR+FC[a0];" +
                "[1:a]asetpts=N/SR/TB,atrim=start=1.000:end=5.000,asetpts=PTS-STARTPTS,volume=0.50,adelay=delays=250:all=1,asetpts=N/SR/TB," +
                "apad=whole_dur=20.000,atrim=end=20.000,pan=stereo|FL=FL+FC|FR=FR+FC[s0];" +
                "[a0][s0]amix=inputs=2:duration=longest:dropout_transition=2:normalize=0[aout]",
            result.valueOf("-filter_complex"),
        )
        assertFalse("-shortest" in result)
    }

    @Test
    fun `a sound alone is fitted to the output length`() {
        val result = args(
            request {
                copy(sources = listOf(sources[0].copy(hasAudio = false)), sounds = listOf(PlacedSound()))
            },
        )
        assertEquals(
            "[0:v]null[vout];[1:a]asetpts=N/SR/TB,asetpts=PTS-STARTPTS,apad=whole_dur=20.000,atrim=end=20.000[aout]",
            result.valueOf("-filter_complex"),
        )
        // An endless apad never terminates on its own stream; it must not be used here.
        assertFalse("-shortest" in result)
        assertTrue("-acodec" in result)
    }

    @Test
    fun `a sound alone is left untouched when the length is unknown`() {
        val result = args(
            request { copy(sources = listOf(SourceInfo(hasAudio = false)), sounds = listOf(PlacedSound())) },
        )
        assertEquals(
            "[0:v]null[vout];[1:a]asetpts=N/SR/TB,asetpts=PTS-STARTPTS[aout]",
            result.valueOf("-filter_complex"),
        )
    }

    @Test
    fun `gif gets default size, rate and a palette`() {
        assertEquals(
            listOf(
                "-y", "-i", "input.mov",
                "-vf", "scale=w='min(iw,480)':h=-1:flags=lanczos,fps=15,split[s0][s1];" +
                    "[s0]palettegen=max_colors=256:stats_mode=diff[p];[s1][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle",
                "-an", "-loop", "0", "output.gif",
            ),
            args(request(OutputFormat.GIF)),
        )
    }

    @Test
    fun `gif settings replace the video ones and set the loops`() {
        val result = args(
            request(OutputFormat.GIF) {
                copy(
                    encoding = EncodingSettings(
                        resolution = Resolution.P1080, framerate = Framerate.FPS30,
                        gif = GifSettings(width = 0, fps = 10, colors = 64, dither = GifDither.BAYER, loops = -1, smaller = false),
                    ),
                )
            },
        )
        assertEquals(
            "fps=10,split[s0][s1];[s0]palettegen=max_colors=64[p];[s1][p]paletteuse=dither=bayer:bayer_scale=3",
            result.valueOf("-vf"),
        )
        assertEquals("-1", result.valueOf("-loop"))
    }

    @Test
    fun `gif with an overlay still gets its palette and no audio`() {
        val result = args(request(OutputFormat.GIF) { copy(imageOverlays = listOf(ImagePlacement())) })
        val graph = result.valueOf("-filter_complex")
        // The GIF's frame rate after the pictures, so that they share it.
        assertTrue(graph, graph.contains("[wmain][wov0]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500[wend];[wend]fps="))
        assertTrue(graph.endsWith("[vpre];[vpre]split[s0][s1];[s0]palettegen=max_colors=256:stats_mode=diff[p];[s1][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle[vout]"))
        assertTrue("-an" in result)
        assertFalse(result.any { it == "0:a?" || it == "[aout]" })
    }

    @Test
    fun `audio formats ignore picture features`() {
        val result = args(
            request(OutputFormat.MP3) {
                copy(
                    imageOverlays = listOf(ImagePlacement()),
                    textOverlays = listOf(TextOverlay(text = "hi")),
                    effects = Effects(volume = 1.5f),
                    normalizeAudio = true,
                )
            },
        )
        assertEquals(
            listOf(
                "-y", "-i", "input.mov", "-af", "volume=1.50,loudnorm,aresample=48000",
                "-vn", "-acodec", "libmp3lame", "-b:a", "192k", "-f", "mp3", "output.mp3",
            ),
            result,
        )
    }

    @Test
    fun `stream copy ignores filters and extra inputs`() {
        val result = args(
            request(OutputFormat.MKV) {
                copy(
                    encoding = EncodingSettings(streamCopy = true),
                    imageOverlays = listOf(ImagePlacement()),
                    effects = Effects(speed = 2f),
                    metadata = MetadataTags(title = "My clip", year = "2026"),
                )
            },
        )
        assertEquals(
            listOf("-y", "-i", "input.mov", "-c", "copy", "-metadata", "title=My clip", "-metadata", "date=2026", "output.mkv"),
            result,
        )
    }

    @Test
    fun `tags read from the input are written back, blanks removing the input's own`() {
        val result = args(request(OutputFormat.MP3) { copy(metadata = MetadataTags(title = "Song", copyright = "2026 Me", fromSource = true)) })
        val tags = result.indices.filter { result[it] == "-metadata" }.map { result[it + 1] }
        assertEquals(listOf("title=Song", "artist=", "album=", "date=", "genre=", "comment=", "copyright=2026 Me"), tags)
    }

    @Test
    fun `reversed audio gets fresh timestamps`() {
        val result = args(request { copy(effects = Effects(reversed = true)) })
        assertEquals("reverse", result.valueOf("-vf"))
        assertEquals("areverse,asetpts=N/SR/TB", result.valueOf("-af"))
    }

    // -- Several segments -----------------------------------------------------------

    private val twoCuts = listOf(Segment(0, 1_000, 3_000), Segment(0, 8_000, 9_500))

    @Test
    fun `cuts of one file are joined frame-accurately without rescaling`() {
        val result = args(request { copy(segments = twoCuts) })
        assertEquals(
            listOf(
                "-y",
                "-ss", "00:00:01.000", "-t", "00:00:02.000", "-i", "input.mov",
                "-ss", "00:00:08.000", "-t", "00:00:01.500", "-i", "input.mov",
                "-filter_complex", "[0:v][0:a][1:v][1:a]concat=n=2:v=1:a=1[vcat][acat];[vcat]null[vout]",
                "-map", "[vout]", "-map", "[acat]",
            ),
            result.take(19),
        )
    }

    @Test
    fun `the rest of the chain treats joined footage as one video`() {
        val result = args(
            request {
                copy(
                    segments = twoCuts,
                    imageOverlays = listOf(ImagePlacement()),
                    sounds = listOf(PlacedSound()),
                    effects = Effects(speed = 2f, fadeOutSeconds = 0.5f),
                )
            },
        )
        // 3.5 s of footage at 2x is 1.75 s of output: the fade-out starts at 1.25 s.
        assertEquals(
            "[0:v][0:a][1:v][1:a]concat=n=2:v=1:a=1[vcat][acat];" +
                "[vcat]setpts=0.5000*PTS[wmain];" +
                "[2:v]scale=w=384:h='max(2,trunc(ow/dar/2)*2)'[wov0];" +
                // The fade covers the pictures too.
                "[wmain][wov0]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500[wend];[wend]fade=t=out:st=1.250:d=0.5[vout];" +
                "[acat]atempo=2.0000,afade=t=out:st=1.250:d=0.5,pan=stereo|FL=FL+FC|FR=FR+FC[a0];" +
                "[3:a]asetpts=N/SR/TB,asetpts=PTS-STARTPTS,afade=t=out:st=1.250:d=0.5,apad=whole_dur=1.750,atrim=end=1.750,pan=stereo|FL=FL+FC|FR=FR+FC[s0];" +
                "[a0][s0]amix=inputs=2:duration=longest:dropout_transition=2:normalize=0[aout]",
            result.valueOf("-filter_complex"),
        )
        assertEquals(listOf("-i", "overlay_0.png", "-i", "sound_1.mp3"), result.subList(13, 17))
    }

    @Test
    fun `different files are fitted to the first frame size and silent ones padded`() {
        val request = request {
            copy(
                sources = listOf(
                    SourceInfo(durationMs = 20_000, width = 1920, height = 1080),
                    SourceInfo(durationMs = 5_000, width = 721, height = 1281, hasAudio = false),
                ),
                segments = listOf(Segment(0, 0, 4_000), Segment(1, 500, 2_500), Segment(0, 10_000, 11_000)),
            )
        }
        val result = args(request)
        assertEquals(listOf("-ss", "00:00:00.500", "-t", "00:00:02.000", "-i", "clip_2.mov"), result.subList(5, 11))
        val fit = "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1"
        assertEquals(
            "[0:v]$fit[sv0];[1:v]$fit[sv1];anullsrc=r=48000:cl=stereo,atrim=duration=2.000[sa1];[2:v]$fit[sv2];" +
                "[sv0][0:a][sv1][sa1][sv2][2:a]concat=n=3:v=1:a=1[vcat][acat];[vcat]null[vout]",
            result.valueOf("-filter_complex"),
        )
    }

    @Test
    fun `joined footage without any audio drops the audio stream`() {
        val result = args(request { copy(segments = twoCuts, effects = Effects(muted = true)) })
        assertEquals("[0:v][1:v]concat=n=2:v=1:a=0[vcat];[vcat]null[vout]", result.valueOf("-filter_complex"))
        assertTrue("-an" in result)
    }

    @Test
    fun `audio formats join the sound only`() {
        val result = args(request(OutputFormat.MP3) { copy(segments = twoCuts, effects = Effects(volume = 0.5f)) })
        assertEquals("[0:a][1:a]concat=n=2:v=0:a=1[acat];[acat]volume=0.50[aout]", result.valueOf("-filter_complex"))
        assertFalse(result.contains("[vout]"))
    }

    @Test
    fun `remuxing several segments reads a concat list`() {
        val result = args(request(OutputFormat.MKV) { copy(segments = twoCuts, encoding = EncodingSettings(streamCopy = true)) })
        assertEquals(listOf("-y", "-f", "concat", "-safe", "0", "-i", "concat_list.txt", "-c", "copy", "output.mkv"), result)
    }

    @Test
    fun `single frame extraction`() {
        val result = args(request { copy(frameExtraction = FrameExtraction.SingleFrame(5_000)) })
        assertEquals(
            listOf("-y", "-ss", "00:00:05.000", "-i", "input.mov", "-frames:v", "1", "-q:v", "2", "output.png"),
            result,
        )
    }

    @Test
    fun `single frame is taken from the segment that is on screen`() {
        val result = args(request { copy(segments = twoCuts, frameExtraction = FrameExtraction.SingleFrame(2_500)) })
        // 2.5 s into the sequence is 0.5 s into the second cut, which starts at 8 s.
        assertEquals(listOf("-y", "-ss", "00:00:08.500", "-i", "input.mov"), result.take(5))
    }

    @Test
    fun `frame sequence over joined footage`() {
        val result = args(request { copy(segments = twoCuts, frameExtraction = FrameExtraction.Sequence(1f)) })
        assertEquals("[0:v][1:v]concat=n=2:v=1:a=0[vcat];[vcat]fps=1.0000[vout]", result.valueOf("-filter_complex"))
        assertEquals(listOf("-map", "[vout]", "-q:v", "2", "output_%05d.png"), result.takeLast(5))
    }

    @Test
    fun `stabilization analyses the joined footage`() {
        val request = request { copy(segments = twoCuts, stabilization = Stabilization()) }
        val analysis = builder.buildStabilizationAnalysis(request, MediaPaths.aliasesFor(request)).arguments
        assertEquals(
            "[0:v][1:v]concat=n=2:v=1:a=0[vcat];[vcat]vidstabdetect=shakiness=5:accuracy=15:result='transforms.trf'[vout]",
            analysis.valueOf("-filter_complex"),
        )
        assertEquals(listOf("-map", "[vout]", "-f", "null", "-"), analysis.takeLast(5))
    }

    @Test
    fun `frame sequence extraction`() {
        val result = args(request { copy(frameExtraction = FrameExtraction.Sequence(0.2f)) })
        assertEquals(listOf("-y", "-i", "input.mov", "-vf", "fps=0.2000", "-q:v", "2", "output_%05d.png"), result)
    }

    @Test
    fun `frame sequence of a part, as JPG pictures of the chosen quality`() {
        val result = args(request {
            copy(format = OutputFormat.JPG, frameExtraction = FrameExtraction.Sequence(24f, 1_500, 4_000), image = ImageEdit(output = ImageOutput(quality = 1f)))
        })
        assertEquals("trim=start=1.500:end=4.000,setpts=PTS-STARTPTS,fps=24.0000", result.valueOf("-vf"))
        assertEquals(listOf("-c:v", "mjpeg", "-q:v", "2", "-pix_fmt", "yuvj444p", "output_%05d.jpg"), result.takeLast(7))
        // A WebP's frames are PNGs (FFmpeg writes no WebP here); from the start, up to an end.
        val webp = args(request { copy(format = OutputFormat.WEBP, frameExtraction = FrameExtraction.Sequence(5f, 0, 2_000)) })
        assertEquals("trim=end=2.000,setpts=PTS-STARTPTS,fps=5.0000", webp.valueOf("-vf"))
        assertEquals("output_%05d.png", webp.last())
    }

    @Test
    fun `split by duration uses the segment muxer`() {
        val result = args(request { copy(split = Split.ByDuration(5)) })
        assertEquals(
            listOf(
                "-f", "segment", "-segment_time", "5", "-reset_timestamps", "1",
                "-segment_time_delta", "0.05", "-force_key_frames", "expr:gte(t,n_forced*5)", "output_%03d.mp4",
            ),
            result.takeLast(11),
        )
    }

    @Test
    fun `split in parts uses explicit cut points and moves the container flag`() {
        val result = args(request(OutputFormat.MP3) { copy(split = Split.ByParts(4)) })
        // 20 s in 4 parts -> cuts at 5, 10 and 15 s.
        assertEquals("5.000,10.000,15.000", result.valueOf("-segment_times"))
        assertEquals("mp3", result.valueOf("-segment_format"))
        assertEquals("segment", result.valueOf("-f"))
        assertFalse("-force_key_frames" in result)
    }

    @Test
    fun `a sound falls back to the source length when the output length is unknown`() {
        val result = args(request { copy(sources = listOf(SourceInfo()), sounds = listOf(PlacedSound())) })
        assertTrue(result.valueOf("-filter_complex").endsWith(
            "[1:a]asetpts=N/SR/TB,asetpts=PTS-STARTPTS,pan=stereo|FL=FL+FC|FR=FR+FC[s0];[a0][s0]amix=inputs=2:duration=first:dropout_transition=2:normalize=0[aout]"))
    }

    @Test
    fun `overlapping sounds are each placed, faded and mixed with the source`() {
        val result = args(
            request {
                copy(
                    sounds = listOf(
                        PlacedSound(startMs = 0, trimStartMs = 0, trimEndMs = 8_000, fadeInSeconds = 1f, fadeOutSeconds = 2f),
                        PlacedSound(startMs = 5_000, trimStartMs = 2_000, trimEndMs = 4_000, volume = 1.5f),
                    ),
                )
            },
        )
        assertEquals(listOf("-i", "sound_1.mp3", "-i", "sound_2.mp3"), result.subList(3, 7))
        assertEquals(
            "[0:v]null[vout];" +
                "[0:a]pan=stereo|FL=FL+FC|FR=FR+FC[a0];" +
                "[1:a]asetpts=N/SR/TB,atrim=start=0.000:end=8.000,asetpts=PTS-STARTPTS," +
                "afade=t=in:st=0:d=1.0,afade=t=out:st=6.000:d=2.0,apad=whole_dur=20.000,atrim=end=20.000,pan=stereo|FL=FL+FC|FR=FR+FC[s0];" +
                "[2:a]asetpts=N/SR/TB,atrim=start=2.000:end=4.000,asetpts=PTS-STARTPTS,volume=1.50," +
                "adelay=delays=5000:all=1,asetpts=N/SR/TB,apad=whole_dur=20.000,atrim=end=20.000,pan=stereo|FL=FL+FC|FR=FR+FC[s1];" +
                "[a0][s0][s1]amix=inputs=3:duration=longest:dropout_transition=2:normalize=0[aout]",
            result.valueOf("-filter_complex"),
        )
    }

    @Test
    fun `sounds replace a muted source and are mixed together`() {
        val result = args(
            request {
                copy(effects = Effects(muted = true), sounds = listOf(PlacedSound(trimEndMs = 3_000), PlacedSound(startMs = 1_000, trimEndMs = 3_000)))
            },
        )
        val graph = result.valueOf("-filter_complex")
        assertFalse(graph, graph.contains("[0:a]"))
        assertTrue(graph, graph.endsWith("[s0][s1]amix=inputs=2:duration=longest:dropout_transition=2:normalize=0[aout]"))
    }

    @Test
    fun `sounds are left out of a picture-only output and of a remux`() {
        val gif = args(request(OutputFormat.GIF) { copy(sounds = listOf(PlacedSound())) })
        assertFalse(gif.contains("sound_1.mp3"))
        val remux = args(request { copy(encoding = EncodingSettings(streamCopy = true), sounds = listOf(PlacedSound())) })
        assertFalse(remux.contains("sound_1.mp3"))
    }

    @Test
    fun `stabilization uses the same input for both passes`() {
        val request = request { copy(segments = listOf(Segment(0, 2_000)), stabilization = Stabilization(shakiness = 8, smoothing = 20)) }
        val paths = MediaPaths.aliasesFor(request)
        assertTrue(builder.needsStabilizationAnalysis(request))
        assertEquals(
            listOf(
                "-y", "-ss", "00:00:02.000", "-i", "input.mov",
                "-vf", "vidstabdetect=shakiness=8:accuracy=15:result='transforms.trf'",
                "-f", "null", "-",
            ),
            builder.buildStabilizationAnalysis(request, paths).arguments,
        )
        assertEquals("vidstabtransform=input='transforms.trf':smoothing=20", builder.build(request, paths).arguments.valueOf("-vf"))
    }

    @Test
    fun `effects and denoise filters`() {
        val result = args(
            request {
                copy(
                    effects = Effects(
                        color = ColorSettings(sharpness = 1f, gaussianBlur = 2.5f, vignette = true, filmGrain = 12f, denoise = 4f),
                        audioDenoise = 0.5f,
                    ),
                )
            },
        )
        assertEquals("hqdn3d=4.0,unsharp=5:5:1.50:5:5:0.00,gblur=sigma=2.5,vignette,noise=alls=12:allf=t+u", result.valueOf("-vf"))
        assertEquals("anlmdn=s=5.00", result.valueOf("-af"))
    }

    @Test
    fun `subtitles are burnt in before the speed change`() {
        val result = args(request { copy(subtitleSource = 0, effects = Effects(speed = 2f)) })
        assertEquals("subtitles='subtitles.srt',setpts=0.5000*PTS", result.valueOf("-vf"))
    }

    @Test
    fun `subtitles are skipped when no line is left to show`() {
        val request = request { copy(subtitleSource = 0) }
        val result = builder.build(request, MediaPaths.aliasesFor(request).copy(subtitles = null)).arguments
        assertFalse("-vf" in result)
    }

    @Test
    fun `pictures are sized from the frame worked out here, never by scale2ref`() {
        // scale2ref never ends in the bundled FFmpeg 6.1.6 once a picture's single frame is
        // through (it closes the video it measured against): no picture may use it.
        val looks = listOf(
            ImagePlacement(),
            ImagePlacement(fadeInSeconds = 1f, endMs = 5_000),
            ImagePlacement(opacity = 0.5f, rotationDegrees = 30f),
            ImagePlacement(video = OverlayVideo(0, 2_000), endMs = 2_000),
            ImagePlacement(
                keyframes = Keyframes(listOf(Keyframe(0L, mapOf(KeyProperty.X to 0f)), Keyframe(1_000L, mapOf(KeyProperty.X to 1f)))),
            ),
        )
        looks.forEach { placement ->
            val graph = args(request { copy(imageOverlays = listOf(placement)) }).valueOf("-filter_complex")
            assertFalse(graph, graph.contains("[wmain]scale2ref"))
            assertFalse(graph, graph.contains("mdar"))
        }
    }

    @Test
    fun `the frame the pictures land on follows the turn, crop, canvas and output height`() {
        // 1920 x 1080 turned: 1080 x 1920; half of its width kept: 540 x 1920; on a 16:9
        // canvas: 3414 x 1920; at 720 lines high: 1280 wide.
        val turned = request {
            copy(
                effects = Effects(rotation = Rotation.DEG_90, crop = CropRect(0f, 0f, 0.5f, 1f)),
                canvas = CanvasRatio.LANDSCAPE_16_9,
                encoding = encoding.copy(resolution = Resolution.P720),
            )
        }
        assertEquals(1280, builder.wholeFrameWidth(turned))
        assertEquals(1920, builder.wholeFrameWidth(request { this }))
        // Not known: a phone's width.
        assertEquals(1080, builder.wholeFrameWidth(request { copy(sources = listOf(SourceInfo(hasAudio = true))) }))
        val graph = args(turned.copy(imageOverlays = listOf(ImagePlacement(widthFraction = 0.25f)))).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("scale=w=320:h='max(2,trunc(ow/dar/2)*2)'"))
    }

    @Test
    fun `several overlays chain through the frame they land on`() {
        val result = args(request { copy(imageOverlays = listOf(ImagePlacement(), ImagePlacement(widthFraction = 0.5f))) })
        assertEquals(
            "[0:v]null[wmain];" +
                "[1:v]scale=w=384:h='max(2,trunc(ow/dar/2)*2)'[wov0];" +
                "[wmain][wov0]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500[wtmp0];" +
                "[2:v]scale=w=960:h='max(2,trunc(ow/dar/2)*2)'[wov1];" +
                "[wtmp0][wov1]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500[vout]",
            result.valueOf("-filter_complex"),
        )
    }

    @Test
    fun `preview is a paste-able command line`() {
        val request = request { copy(encoding = EncodingSettings(resolution = Resolution.P480)) }
        assertEquals(
            "ffmpeg -y -i input.mov -vf scale=-2:480 -vcodec libx264 -crf 18 -preset medium -acodec aac -b:a 128k output.mp4",
            builder.build(request, MediaPaths.aliasesFor(request)).toDisplayString(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom format cannot be generated`() {
        args(request(OutputFormat.CUSTOM))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing overlay paths are rejected`() {
        val request = request { copy(imageOverlays = listOf(ImagePlacement())) }
        builder.build(request, MediaPaths(inputs = listOf("in.mov"), output = "out.mp4"))
    }

    @Test
    fun `a sound's speed keeps its pitch by default and changes its length`() {
        val chain = soundGraph(PlacedSound(trimStartMs = 0, trimEndMs = 4_000, speed = 2f, fadeOutSeconds = 1f))
        // 4 s at 2x last 2 s: the fade-out starts at 1 s.
        assertTrue(chain, chain.contains("asetpts=PTS-STARTPTS,atempo=2.0000,afade=t=out:st=1.000:d=1.0"))
        assertFalse(chain, chain.contains("asetrate"))
    }

    @Test
    fun `a sound's speed can carry its pitch along, like a tape`() {
        val chain = soundGraph(PlacedSound(trimEndMs = 4_000, speed = 1.5f, keepPitch = false))
        assertTrue(chain, chain.contains("aresample=48000,asetrate=72000,aresample=48000,"))
        assertFalse(chain, chain.contains("atempo"))
    }

    @Test
    fun `a pitch shift keeps the speed`() {
        val chain = soundGraph(PlacedSound(trimEndMs = 4_000, pitchSemitones = 12f))
        // An octave up: twice the rate, then stretched back to the same length.
        assertTrue(chain, chain.contains("aresample=48000,asetrate=96000,aresample=48000,atempo=0.5000"))
    }

    @Test
    fun `a repeated sound is looped from its trimmed part`() {
        val chain = soundGraph(PlacedSound(trimStartMs = 1_000, trimEndMs = 3_000, repeats = 3, fadeOutSeconds = 0.5f))
        assertTrue(chain, chain.contains("atrim=start=1.000:end=3.000,asetpts=PTS-STARTPTS,aresample=48000,aloop=loop=2:size=96000,asetpts=N/SR/TB"))
        // Three times 2 s: the fade-out is at the end of the sixth second.
        assertTrue(chain, chain.contains("afade=t=out:st=5.500:d=0.5"))
    }

    @Test
    fun `equaliser, levelling and volume come in that order`() {
        val chain = soundGraph(PlacedSound(trimEndMs = 2_000, bassDb = 6f, midDb = -3f, trebleDb = 2.5f, normalize = true, volume = 0.8f))
        assertTrue(
            chain,
            chain.contains("bass=f=100:t=q:w=0.5:g=6,equalizer=f=1000:t=o:w=1.5:g=-3,treble=f=4000:t=q:w=0.5:g=2.5,loudnorm,aresample=48000,volume=0.80"),
        )
    }

    /** The filter chain of a single placed sound on a silent 20 s video. */
    private fun soundGraph(sound: PlacedSound): String =
        args(request { copy(sources = listOf(sources[0].copy(hasAudio = false)), sounds = listOf(sound)) }).valueOf("-filter_complex")

    @Test
    fun `whole-video captions are timed on the exported clock, after the speed change`() {
        val result = args(
            request {
                copy(
                    effects = Effects(speed = 2f, fadeOutSeconds = 1f),
                    textOverlays = listOf(TextOverlay(text = "hi", startMs = 1_000, endMs = 4_000, opacity = 0.8f, fadeInSeconds = 0.5f, fadeOutSeconds = 1f)),
                )
            },
        )
        val chain = result.valueOf("-vf")
        assertTrue(chain, chain.startsWith("setpts=0.5000*PTS,drawtext="))
        assertTrue(
            chain,
            chain.contains(":alpha='0.800*clip(min((t-1.000)/0.500,(4.000-t)/1.000),0,1)':enable='between(t,1.000,4.000)',fade=t=out"),
        )
    }

    @Test
    fun `a caption without a window shows all along`() {
        val chain = args(request { copy(textOverlays = listOf(TextOverlay(text = "hi", startMs = 0, endMs = null))) }).valueOf("-vf")
        assertFalse(chain, chain.contains("enable"))
        assertFalse(chain, chain.contains("alpha"))
        val later = args(request { copy(textOverlays = listOf(TextOverlay(text = "hi", startMs = 2_500))) }).valueOf("-vf")
        assertTrue(later, later.endsWith(":enable='gte(t,2.500)'"))
    }

    @Test
    fun `a timed picture is enabled for its window and a fading one is looped and faded`() {
        val result = args(
            request {
                copy(
                    imageOverlays = listOf(
                        ImagePlacement(startMs = 2_000, endMs = 5_000),
                        ImagePlacement(startMs = 1_000, endMs = 6_000, fadeInSeconds = 0.5f, fadeOutSeconds = 1f),
                    ),
                )
            },
        )
        // Both read once; the fading one is repeated after it is sized.
        assertEquals(listOf("-i", "overlay_0.png", "-i", "overlay_1.png"), result.subList(3, 7))
        assertFalse(result.contains("-loop"))
        val graph = result.valueOf("-filter_complex")
        assertTrue(graph, graph.contains("[wmain][wov0]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500:enable='between(t,2.000,5.000)'[wtmp0]"))
        assertTrue(graph, graph.contains("[wov1]loop=loop=-1:size=1,format=rgba,fade=t=in:st=1.000:d=0.500:alpha=1,fade=t=out:st=5.000:d=1.000:alpha=1[wovf1]"))
        assertTrue(graph, graph.contains("[wtmp0][wovf1]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500:enable='between(t,1.000,6.000)':shortest=1[vout]"))
    }

    @Test
    fun `a video overlay plays its own part from its start, adjusted, mirrored and turned`() {
        val result = args(
            request {
                copy(
                    imageOverlays = listOf(
                        ImagePlacement(
                            startMs = 2_000, endMs = 4_500, rotationDegrees = 90f, flipHorizontal = true, saturation = 0f,
                            video = OverlayVideo(trimStartMs = 1_000, trimEndMs = 3_500),
                        ),
                    ),
                )
            },
        )
        assertEquals(listOf("-ss", "00:00:01.000", "-t", "00:00:02.500", "-i", "overlay_0.png"), result.subList(3, 9))
        val graph = result.valueOf("-filter_complex")
        assertTrue(graph, graph.contains("[1:v]setpts=PTS-STARTPTS+2.000/TB,eq=brightness=0.00:contrast=1.00:saturation=0.00,hflip[wovp0]"))
        assertTrue(graph, graph.contains("[wovp0]scale=w=384:h='max(2,trunc(ow/dar/2)*2)'[wov0]"))
        assertTrue(graph, graph.contains("[wov0]format=rgba,rotate=1.570796:ow=rotw(1.570796):oh=roth(1.570796):c=none[wovr0]"))
        assertTrue(graph, graph.contains("[wmain][wovr0]overlay=x=(W-w)*0.0500:y=(H-h)*0.0500:enable='between(t,2.000,4.500)':eof_action=pass[vout]"))
    }

    @Test
    fun `a still with motion is zoomed frame by frame before its other effects`() {
        val request = request {
            copy(
                sources = sources + SourceInfo(width = 1080, height = 1920, hasAudio = false, isStill = true),
                segments = listOf(Segment(0, 0, 2_000, clipId = "v"), Segment(1, 0, 2_000, Effects(motion = StillMotion(MotionKind.ZOOM_IN, 1.5f), flipVertical = true), clipId = "s")),
            )
        }
        val graph = args(request).valueOf("-filter_complex")
        assertTrue(
            graph,
            graph.contains("[1:v]zoompan=z='1+(1.5000-1)*min(on/59,1)':x='(iw-iw/zoom)/2':y='(ih-ih/zoom)/2':d=1:s=1080x1920:fps=30,vflip,"),
        )
    }

    @Test
    fun `the final sound's effects follow the footage's own sound`() {
        val result = args(
            request(OutputFormat.WAV) {
                copy(
                    effects = Effects(volume = 0.5f),
                    audioEffects = AudioEffects(lowCutHz = 80, echo = Echo(delayMs = 250, feedback = 0.5f), pitchSemitones = 12f),
                    encoding = EncodingSettings(audio = AudioEncoding(sampleRate = 44100, bitDepth = 24)),
                )
            },
        )
        val chain = result.valueOf("-af")
        assertTrue(chain, chain.startsWith("volume=0.50,aformat=sample_fmts=fltp,highpass=f=80:t=q:w=0.707,aecho=1:0.8:250|500|750|1000|1250:0.5|0.25|0.125|0.0625|0.03125,"))
        assertTrue(chain, chain.contains("aresample=48000,asetrate=96000,aresample=48000,atempo=0.5000,atrim=end=20.000"))
        assertEquals(listOf("-vn", "-acodec", "pcm_s24le", "-ar", "44100", "-f", "wav", "output.wav"), result.takeLast(8))
    }

    @Test
    fun `with placed sounds the final sound's effects run on the mix`() {
        val graph = args(
            request(OutputFormat.MP3) {
                copy(
                    sounds = listOf(PlacedSound(trimEndMs = 3_000)),
                    audioEffects = AudioEffects(stereo = StereoImage(mono = true)),
                )
            },
        ).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("amix=inputs=2:duration=longest:dropout_transition=2:normalize=0,aformat=sample_fmts=fltp,pan=stereo|FL=FL+FC|FR=FR+FC,pan=mono|c0=0.5*c0+0.5*c1[aout]"))
    }

    @Test
    fun `each audio format has its own codec and settings`() {
        fun tail(format: OutputFormat, audio: AudioEncoding = AudioEncoding()) =
            args(request(format) { copy(encoding = EncodingSettings(audio = audio)) }).drop(3)
        assertEquals(listOf("-vn", "-acodec", "aac", "-b:a", "256k", "-f", "ipod", "output.m4a"), tail(OutputFormat.M4A, AudioEncoding(bitrateKbps = 256)))
        assertEquals(listOf("-vn", "-acodec", "libopus", "-b:a", "256k", "-f", "opus", "output.opus"), tail(OutputFormat.OPUS, AudioEncoding(bitrateKbps = 320, sampleRate = 44100)))
        assertEquals(listOf("-vn", "-acodec", "libvorbis", "-b:a", "128k", "-ar", "22050", "-f", "ogg", "output.ogg"), tail(OutputFormat.OGG, AudioEncoding(bitrateKbps = 128, sampleRate = 22050)))
        assertEquals(listOf("-vn", "-acodec", "flac", "-sample_fmt", "s32", "-bits_per_raw_sample", "24", "-f", "flac", "output.flac"), tail(OutputFormat.FLAC, AudioEncoding(bitDepth = 24)))
        assertEquals(listOf("-vn", "-acodec", "pcm_s16le", "-f", "wav", "output.wav"), tail(OutputFormat.WAV))
    }

    @Test
    fun `untouched final sound adds nothing`() {
        assertEquals(listOf("-y", "-i", "input.mov", "-vn", "-acodec", "libmp3lame", "-b:a", "192k", "-f", "mp3", "output.mp3"), args(request(OutputFormat.MP3)))
    }

    @Test
    fun `a placed sound runs its own effects before it is placed, cut at its end`() {
        val graph = args(
            request {
                copy(sounds = listOf(PlacedSound(startMs = 1_000, trimEndMs = 3_000, effects = AudioEffects(echo = Echo(delayMs = 200, feedback = 0.5f), lowCutHz = 100))))
            },
        ).valueOf("-filter_complex")
        assertTrue(
            graph,
            graph.contains("asetpts=PTS-STARTPTS,aformat=sample_fmts=fltp,highpass=f=100:t=q:w=0.707,aecho=1:0.8:200|400|600|800|1000:0.5|0.25|0.125|0.0625|0.03125,atrim=end=3.000,adelay=delays=1000:all=1"),
        )
    }

    @Test
    fun `a video's final sound gets its effects after the mix too`() {
        val result = args(request { copy(audioEffects = AudioEffects(limiter = Limiter(-3f))) })
        assertTrue(result.valueOf("-af"), result.valueOf("-af").startsWith("aformat=sample_fmts=fltp,acompressor="))
        assertEquals("libx264", result.valueOf("-vcodec"))
    }

    @Test
    fun `HDR footage stays HDR in an MP4, or is tone mapped before anything else`() {
        val hdr = listOf(SourceInfo(durationMs = 20_000, width = 1920, height = 1080, hasAudio = true, hdr = VideoHdr.PQ))
        // Kept: no tone mapping, the video says it is PQ.
        val kept = args(request { copy(sources = hdr) })
        assertFalse(kept.any { it.contains("zscale") })
        assertTrue(kept.joinToString(" ").contains("-color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc"))
        // Made SDR.
        val sdr = args(request { copy(sources = hdr, encoding = encoding.copy(keepHdr = false)) })
        val graph = sdr.valueOf("-filter_complex")
        assertTrue(graph, graph.startsWith("[0:v]zscale=tin=smpte2084:min=bt2020nc:pin=bt2020:t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p[vsdr]"))
        assertTrue(sdr.containsAll(listOf("-map", "[vout]", "0:a?")))
        assertFalse(sdr.contains("-color_trc"))
        // An HDR clip joined to an SDR one: the HDR one is made SDR, the SDR one stays as it is.
        val joined = args(
            request {
                copy(
                    sources = hdr + SourceInfo(durationMs = 5_000, width = 1920, height = 1080),
                    segments = listOf(Segment(0, 0, 2_000), Segment(1, 0, 2_000)),
                )
            },
        ).valueOf("-filter_complex")
        assertTrue(joined, joined.contains("[0:v]zscale=tin=smpte2084"))
        assertFalse(joined, joined.contains("[1:v]zscale"))
        // A GIF and a picture made of a frame of HLG footage are SDR.
        assertTrue(args(request(OutputFormat.GIF) { copy(sources = hdr) }).joinToString(" ").contains("zscale=tin=smpte2084"))
        val picture = args(request(OutputFormat.JPG) { copy(sources = listOf(SourceInfo(durationMs = 20_000, width = 400, height = 300, hdr = VideoHdr.HLG))) })
        assertTrue(picture.valueOf("-filter_complex").startsWith("[0:v]zscale=tin=arib-std-b67"))
        // Stream copy keeps the file as it is.
        assertFalse(args(request { copy(sources = hdr, encoding = encoding.copy(streamCopy = true, keepHdr = false)) }).any { it.contains("zscale") })
    }

    @Test
    fun `an Ultra HDR picture is also written losslessly, as it comes out and without what lies on it`() {
        val request = request(OutputFormat.JPG) {
            copy(
                sources = listOf(SourceInfo(width = 400, height = 300, isStill = true)),
                image = ImageEdit(border = ImageBorder(0.1f, 0xFF102030), hdr = ImageHdr()),
                textOverlays = listOf(TextOverlay(text = "hi")),
            )
        }
        val paths = MediaPaths.aliasesFor(request).copy(hdrPictures = HdrPictures("final.png", "plain.png"))
        val result = builder.build(request, paths).arguments
        val graph = result.valueOf("-filter_complex")
        assertTrue(graph, graph.contains(",split[hdrall][hdrp]"))
        assertTrue(graph, graph.contains("[hdrall]drawtext="))
        assertTrue(graph, graph.contains("[pic]pad=w=iw+60:h=ih+60:x=30:y=30:color=0x102030,split[vout][hdrfinal]"))
        assertTrue(graph, graph.contains("[hdrp]pad=w=iw+60:h=ih+60:x=30:y=30:color=black[hdrplain]"))
        assertEquals(
            listOf(
                "output.jpg",
                "-map", "[hdrfinal]", "-frames:v", "1", "-update", "1", "-c:v", "png", "-pix_fmt", "rgb24", "final.png",
                "-map", "[hdrplain]", "-frames:v", "1", "-update", "1", "-c:v", "png", "-pix_fmt", "rgb24", "plain.png",
            ),
            result.takeLast(23),
        )
        // Not a JPG: no gain map, nothing more written.
        val png = builder.build(request.copy(format = OutputFormat.PNG), paths.copy(output = "output.png")).arguments
        assertFalse(png.contains("final.png"))
    }

    @Test
    fun `a picture is edited, framed and written once`() {
        val result = args(
            request(OutputFormat.JPG) {
                copy(
                    sources = listOf(SourceInfo(durationMs = 20_000, width = 400, height = 300)),
                    effects = Effects(crop = CropRect(0f, 0f, 0.5f, 1f)),
                    image = ImageEdit(frameMs = 2_500, tone = ImageTone(contrast = 0.5f), border = ImageBorder(0.1f, 0xFF102030), output = ImageOutput(maxSide = 1000, quality = 1f)),
                    textOverlays = listOf(TextOverlay(text = "hi")),
                    imageOverlays = listOf(ImagePlacement()),
                )
            },
        )
        assertEquals(listOf("-y", "-ss", "00:00:02.500", "-i", "input.mov", "-i", "overlay_0.png"), result.take(7))
        val graph = result.valueOf("-filter_complex")
        assertTrue(graph, graph.startsWith("[0:v]format=rgb24,crop=w='iw*0.5':h='ih*1':x='iw*0':y='ih*0',lutrgb="))
        assertTrue(graph, graph.contains(",drawtext="))
        assertTrue(graph, graph.contains("[pic]pad=w=iw+40:h=ih+40:x=20:y=20:color=0x102030,scale=w='if(gte(iw,ih),min(iw,1000),-1)'"))
        assertEquals(listOf("-map", "[vout]", "-frames:v", "1", "-update", "1", "-c:v", "mjpeg", "-q:v", "2", "-pix_fmt", "yuvj444p", "output.jpg"), result.takeLast(13))
    }

    @Test
    fun `a picture opened as a still is read whole`() {
        val result = args(
            request(OutputFormat.PNG) {
                copy(sources = listOf(SourceInfo(width = 100, height = 100, hasAudio = false, isStill = true)), image = ImageEdit(frameMs = 9_000, effect = ImageEffect.INVERT))
            },
        )
        assertEquals(listOf("-y", "-i", "input.mov", "-filter_complex"), result.take(4))
        assertEquals(listOf("-c:v", "png", "-pix_fmt", "rgb24", "output.png"), result.takeLast(5))
    }

    @Test
    fun `a picture's frame stays inside its clip and its stickers have no clock`() {
        val result = args(
            request(OutputFormat.PNG) {
                copy(
                    sources = listOf(SourceInfo(durationMs = 20_000, width = 400, height = 300)),
                    segments = listOf(Segment(0, 1_000, 3_000)),
                    image = ImageEdit(frameMs = 60_000),
                    imageOverlays = listOf(ImagePlacement(startMs = 4_000, endMs = 5_000, fadeInSeconds = 1f)),
                )
            },
        )
        // 1 s into the file, then up to 100 ms before the clip ends.
        assertEquals(listOf("-y", "-ss", "00:00:02.900", "-i", "input.mov"), result.take(5))
        val graph = result.valueOf("-filter_complex")
        assertTrue(graph, !graph.contains("enable=") && !graph.contains("fade="))
    }

    @Test
    fun `an mp3 gets its cover as an attached picture, named as the front cover`() {
        assertEquals(
            listOf(
                "-y", "-i", "input.mov", "-i", "cover.jpg",
                "-map", "0:a", "-map", "1:v",
                "-acodec", "libmp3lame", "-b:a", "192k", "-f", "mp3",
                "-c:v", "copy", "-disposition:v:0", "attached_pic", "-metadata:s:v:0", "comment=Cover (front)",
                "output.mp3",
            ),
            args(request(OutputFormat.MP3) { copy(cover = true) }),
        )
    }

    @Test
    fun `a cover comes after the placed sounds and is mapped next to the mix`() {
        val result = args(request(OutputFormat.M4A) { copy(cover = true, sounds = listOf(PlacedSound(trimEndMs = 2_000))) })
        assertEquals(listOf("-i", "input.mov", "-i", "sound_1.mp3", "-i", "cover.jpg"), result.filterIndexed { i, _ -> i in 1..6 })
        val maps = result.indices.filter { result[it] == "-map" }.map { result[it + 1] }
        assertEquals(listOf("[aout]", "2:v"), maps)
        assertFalse(result.contains("-vn"))
        assertEquals("attached_pic", result.valueOf("-disposition:v:0"))
        assertFalse(result.contains("-metadata:s:v:0"))
    }

    @Test
    fun `an ogg or opus file gets its cover from a tags file`() {
        for (format in listOf(OutputFormat.OGG, OutputFormat.OPUS)) {
            val result = args(request(format) { copy(cover = true) })
            assertEquals(listOf("-y", "-i", "input.mov", "-f", "ffmetadata", "-i", "cover_tags.txt"), result.take(7))
            assertTrue(result.contains("-vn"))
            assertEquals(listOf("-map_metadata", "0", "-map_metadata", "1"), result.subList(result.indexOf("-map_metadata"), result.indexOf("-map_metadata") + 4))
            assertFalse(result.contains("-map"))
        }
    }

    @Test
    fun `no cover where it cannot be kept`() {
        // WAV holds none; parts of a split MP3 would not all get it; frames are pictures already.
        val wav = args(request(OutputFormat.WAV) { copy(cover = true) })
        val split = args(request(OutputFormat.MP3) { copy(cover = true, split = Split.ByParts(2)) })
        val frame = args(request(OutputFormat.MP3) { copy(cover = true, frameExtraction = FrameExtraction.SingleFrame(0)) })
        for (result in listOf(wav, split, frame)) {
            assertFalse(result.joinToString(" "), result.any { it.startsWith("cover") || it == "attached_pic" })
        }
        assertEquals(null, MediaPaths.aliasesFor(request(OutputFormat.WAV) { copy(cover = true) }).cover)
        // Tags go into every part.
        assertTrue(args(request(OutputFormat.OPUS) { copy(cover = true, split = Split.ByParts(2)) }).contains("cover_tags.txt"))
    }

    @Test
    fun `a remuxed audio file keeps only its sound, or its sound and the new cover`() {
        val plain = args(request(OutputFormat.MP3) { copy(encoding = EncodingSettings(streamCopy = true)) })
        assertEquals(listOf("-y", "-i", "input.mov", "-c", "copy", "-vn", "output.mp3"), plain)
        val covered = args(request(OutputFormat.MP3) { copy(cover = true, encoding = EncodingSettings(streamCopy = true)) })
        assertEquals(
            listOf(
                "-y", "-i", "input.mov", "-i", "cover.jpg", "-c", "copy", "-map", "0:a", "-map", "1:v",
                "-disposition:v:0", "attached_pic", "-metadata:s:v:0", "comment=Cover (front)", "output.mp3",
            ),
            covered,
        )
    }

    @Test
    fun `cover tags hold a front cover picture block in base64`() {
        // A JPEG with an APP0 segment, then a baseline frame of 300 x 200.
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 1, 2,
            0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 0, 200.toByte(), 1, 44, 3, 1, 0x22, 0,
        )
        assertEquals(300 to 200, CoverTags.pictureSize(jpeg))
        val png = ByteArray(24).also {
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47).copyInto(it)
            it[19] = 16
            it[23] = 9
        }
        assertEquals(16 to 9, CoverTags.pictureSize(png))
        assertEquals("image/png", CoverTags.mimeType(png))
        assertEquals(null, CoverTags.pictureSize(byteArrayOf(1, 2, 3)))

        val block = CoverTags.pictureBlock(jpeg)
        val data = java.io.DataInputStream(block.inputStream())
        assertEquals(3, data.readInt())
        assertEquals("image/jpeg", String(ByteArray(data.readInt()).also { data.readFully(it) }))
        assertEquals(0, data.readInt())
        assertEquals(listOf(300, 200, 24, 0, jpeg.size), List(5) { data.readInt() })
        assertTrue(ByteArray(jpeg.size).also { data.readFully(it) }.contentEquals(jpeg))

        val tags = CoverTags.ffmetadata(jpeg)
        assertTrue(tags.startsWith(";FFMETADATA1\nMETADATA_BLOCK_PICTURE="))
        val value = tags.lines()[1].substringAfter('=').replace("\\=", "=")
        assertTrue(java.util.Base64.getDecoder().decode(value).contentEquals(block))
    }
}
