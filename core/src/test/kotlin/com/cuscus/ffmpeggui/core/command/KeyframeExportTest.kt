package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframe
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.PlacedSound
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.core.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keyframes in the command: expressions of the time, worked out on every frame. */
class KeyframeExportTest {

    private val builder = CommandBuilder { "/fonts/font.ttf" }

    private fun args(request: ConversionRequest): List<String> = builder.build(request, MediaPaths.aliasesFor(request)).arguments

    private fun List<String>.valueOf(flag: String): String = this[indexOf(flag) + 1]

    private val video = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = "clip.mp4",
        sources = listOf(SourceInfo(durationMs = 10_000, width = 640, height = 360, hasAudio = true)),
    )

    private fun keys(vararg frames: Pair<Long, Map<KeyProperty, Float>>) = Keyframes(frames.map { Keyframe(it.first, it.second) })

    @Test
    fun `a clip's framing, colours and volume follow the position in its file`() {
        val effects = Effects(
            keyframes = keys(
                2_000L to mapOf(KeyProperty.ZOOM to 1f, KeyProperty.BRIGHTNESS to 0f, KeyProperty.VOLUME to 1f),
                6_000L to mapOf(KeyProperty.ZOOM to 2f, KeyProperty.BRIGHTNESS to 0.2f, KeyProperty.VOLUME to 0f),
            ),
        )
        val request = video.copy(segments = listOf(Segment(0, 2_000, 6_000, effects)))
        val graph = args(request).valueOf("-filter_complex")
        // The file's clock: the segment starts 2 s into it.
        assertTrue(graph, graph.contains("split[kc0za][kc0zb];[kc0zb]scale=w='max(2,trunc(iw*max(1,if(lt((t+2.0000),6.0000)"))
        assertTrue(graph, graph.contains(":eval=frame[kc0zc];[kc0za][kc0zc]overlay=x='(W-w)*clip(0.5000,0,1)'"))
        assertTrue(graph, graph.contains("eq=eval=frame:brightness='if(lt((t+2.0000),6.0000)"))
        assertTrue(graph, graph.contains("volume=eval=frame:volume='max(0,if(lt((2.0000+(t*1.000000)),6.0000)"))
    }

    @Test
    fun `a reversed clip's keyframes count back from its end`() {
        val effects = Effects(reversed = true, speed = 2f, keyframes = keys(0L to mapOf(KeyProperty.ZOOM to 1f), 4_000L to mapOf(KeyProperty.ZOOM to 3f)))
        val graph = args(video.copy(segments = listOf(Segment(0, 1_000, 4_000, effects)))).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("if(lt((4.0000-t),4.0000)"))
    }

    @Test
    fun `framing without change and keyframes that do not move keep the plain filters`() {
        val still = Effects(zoom = 1.5f, panX = 0f)
        val graph = args(video.copy(segments = listOf(Segment(0, 0, 3_000, still)))).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("scale=w='max(2,trunc(iw*max(1,1.5000)/2)*2)'"))
        assertTrue(graph, graph.contains("overlay=x='(W-w)*clip(0.0000,0,1)'"))
        val flat = Effects(keyframes = keys(0L to mapOf(KeyProperty.BRIGHTNESS to 0.1f)))
        val plain = args(video.copy(segments = listOf(Segment(0, 0, 3_000, flat)))).valueOf("-filter_complex")
        assertFalse(plain, plain.contains("eval=frame"))
    }

    @Test
    fun `a text moves, grows and fades on its own clock`() {
        val text = TextOverlay(
            text = "Hi", startMs = 1_500, endMs = 4_000,
            keyframes = keys(0L to mapOf(KeyProperty.X to 0f, KeyProperty.OPACITY to 1f), 2_000L to mapOf(KeyProperty.X to 1f, KeyProperty.OPACITY to 0f)),
        )
        val filter = args(video.copy(textOverlays = listOf(text))).valueOf("-vf")
        assertTrue(filter, filter.contains(":x='(w-tw)*if(lt((t-1.5000),2.0000)"))
        assertTrue(filter, filter.contains(":fontsize='h*0.0500'"))
        assertTrue(filter, filter.contains(":alpha='clip(if(lt((t-1.5000),2.0000)"))
        assertTrue(filter, filter.contains(":enable='between(t,1.500,4.000)'"))
    }

    @Test
    fun `a moving picture is a stream of frames, sized once, turned, shrunk and placed on every frame`() {
        val picture = ImagePlacement(
            widthFraction = 0.2f, aspect = 2f, startMs = 1_000,
            keyframes = keys(
                0L to mapOf(KeyProperty.SIZE to 0.1f, KeyProperty.ROTATION to 0f, KeyProperty.OPACITY to 1f),
                2_000L to mapOf(KeyProperty.SIZE to 0.4f, KeyProperty.ROTATION to 90f, KeyProperty.OPACITY to 0.5f),
            ),
        )
        val all = args(video.copy(imageOverlays = listOf(picture)))
        // Read once, sized once at its largest (0.4 of 640), then repeated as a stream.
        assertFalse(all.contains("-loop"))
        val graph = all.valueOf("-filter_complex")
        assertTrue(graph, graph.contains("[1:v]scale=w=256:h='max(2,trunc(ow/dar/2)*2)'[wk0s];[wk0s]loop=loop=-1:size=1[wk0st]"))
        assertTrue(graph, graph.contains("color=c=black:s=2x2:r=25,format=gray,geq=lum='255*clip(if(lt((T-1.0000),2.0000)"))
        assertTrue(graph, graph.contains("blend=all_mode=multiply"))
        assertTrue(graph, graph.contains("rotate=a='(if(lt((t-1.0000),2.0000)"))
        assertTrue(graph, graph.contains(":ow='hypot(iw,ih)':oh=ow:c=black@0"))
        assertTrue(graph, graph.contains("/0.4000/2)*2)':h="))
        // Placed by the tight box of the turned picture (2:1).
        assertTrue(graph, graph.contains("abs(cos("))
        assertTrue(graph, graph.contains(":shortest=1"))
    }

    @Test
    fun `a sound's volume follows its keyframes from its start`() {
        val sound = PlacedSound(startMs = 500, trimEndMs = 3_000, keyframes = keys(0L to mapOf(KeyProperty.VOLUME to 0f), 1_000L to mapOf(KeyProperty.VOLUME to 1f)))
        val graph = args(video.copy(sounds = listOf(sound))).valueOf("-filter_complex")
        assertTrue(graph, graph.contains("volume=eval=frame:volume='max(0,if(lt(t,1.0000)"))
    }
}
