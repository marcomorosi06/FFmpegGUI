package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.denoiseFilter
import com.cuscus.ffmpeggui.core.command.toneMapFilters
import com.cuscus.ffmpeggui.core.model.VideoHdr
import java.util.Locale

/*
 * The denoise comparison of the video editor: one small square of a frame,
 * at the source's own pixels (noise only shows up close), before and after
 * the denoise, side by side in one PNG.
 *
 * The preview cannot show the denoise live: hqdn3d also works over time, each
 * frame blended with the ones before it, so a frame alone would be wrong. The
 * comparison is made by FFmpeg itself, with the very filter the export runs
 * (denoiseFilter), so the "after" half is what the export makes of those
 * pixels. Checked against a full run of the same filter on a whole video:
 *
 *  - over time: hqdn3d starts again at the start of every clip and quickly
 *    forgets older frames, so the frames of a short warm-up before the
 *    position ([DenoiseSample.WARM_UP_MS], or from the clip's start if
 *    nearer) are enough for the same result, without decoding the whole clip;
 *  - across the frame: hqdn3d's smoothing runs along each row and column and
 *    fades within a few pixels, so the square is cut out with a margin
 *    ([DenoiseSample.MARGIN_PX]) on every side, denoised, and the margin is
 *    cut away. Where the square touches the frame's edge, that edge is the
 *    frame's own, as in the export.
 *
 * Both halves come from one decode and one split, and are written as one
 * picture: the comparison cannot mix up two different frames.
 */

/**
 * What a comparison shows.
 *
 * @param sourceUri    the clip's file.
 * @param positionMs   the frame, as a position in the file.
 * @param clipStartMs  where the clip starts in the file: the denoise starts there.
 * @param still        the file is a picture: one frame, no seeking.
 * @param strengths    the denoise strengths run one after the other (the clip's,
 *                     then the whole video's), as in the export; zeros are skipped.
 * @param focusX       where the square is across the frame: 0 = left edge, 1 = right edge.
 * @param focusY       where it is down the frame: 0 = top edge, 1 = bottom edge.
 * @param hdr          the file's HDR kind.
 * @param hdrKept      the export keeps HDR footage HDR: the denoise works on the HDR
 *                     frames, and the comparison is tone mapped only to be shown. When
 *                     false, HDR footage is made SDR first, as the export does.
 */
data class DenoiseSampleRequest(
    val sourceUri: String,
    val positionMs: Long,
    val clipStartMs: Long,
    val still: Boolean,
    val strengths: List<Float>,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val hdr: VideoHdr = VideoHdr.NONE,
    val hdrKept: Boolean = false,
) {
    /** True when some denoise is on: otherwise both halves would be the same. */
    val denoises: Boolean get() = strengths.any { it > 0f }
}

object DenoiseSample {
    /** Side of the square shown, in the source's pixels (less on a smaller frame). */
    const val SIDE_PX = 512

    /** Pixels cut out around the square so that its edges are denoised as in the whole frame. */
    const val MARGIN_PX = 32

    /** Frames decoded before the position, so that the denoise's memory is as in the export. */
    const val WARM_UP_MS = 500L

    /**
     * The command that writes the comparison of [request] read from [inputPath]
     * into [outputPath] (a PNG): the square before the denoise on the left, after
     * it on the right, each as wide as the picture is high.
     */
    fun command(request: DenoiseSampleRequest, inputPath: String, outputPath: String): FfmpegCommand {
        val start = if (request.still) 0L else maxOf(request.clipStartMs, request.positionMs - WARM_UP_MS).coerceAtLeast(0L)
        val warmUpMs = if (request.still) 0L else (request.positionMs - start).coerceAtLeast(0L)
        val args = mutableListOf("-nostats", "-y")
        if (start > 0L) args += listOf("-ss", seconds(start))
        args += listOf("-i", inputPath)
        args += listOf("-filter_complex", graph(request, warmUpMs))
        args += listOf("-frames:v", "1", "-an", "-sn", "-dn", outputPath)
        return FfmpegCommand(args)
    }

    /** The filtergraph of [command]; its frames start [warmUpMs] before the one shown. */
    internal fun graph(request: DenoiseSampleRequest, warmUpMs: Long): String {
        val focusX = request.focusX.coerceIn(0f, 1f)
        val focusY = request.focusY.coerceIn(0f, 1f)
        val outer = SIDE_PX + 2 * MARGIN_PX
        // HDR footage the export makes SDR is denoised SDR, as there.
        val sdrFirst = if (request.hdrKept) emptyList() else toneMapFilters(request.hdr)
        // HDR footage kept HDR is denoised as it is, then tone mapped to be seen: each half
        // on its own, as hstack does not carry the frames' colour tags zscale reads.
        val shownSdr = if (request.hdrKept) toneMapFilters(request.hdr, "gbrp") else emptyList()
        val square = "crop=w='min(iw,$outer)':h='min(ih,$outer)'" +
            ":x='(iw-ow)*${decimal(focusX)}':y='(ih-oh)*${decimal(focusY)}'"
        // The margin, or a quarter of a tiny frame, cut away again.
        val inner = "crop=w='iw-2*min($MARGIN_PX,trunc(iw/4))':h='ih-2*min($MARGIN_PX,trunc(ih/4))'" +
            ":x='min($MARGIN_PX,trunc(iw/4))':y='min($MARGIN_PX,trunc(ih/4))'"
        val denoise = request.strengths.mapNotNull { denoiseFilter(it) }.ifEmpty { listOf("null") }
        val head = (sdrFirst + square + "split=2").joinToString(",")
        val tail = buildList {
            add("hstack=inputs=2")
            if (warmUpMs > 0L) {
                add("trim=start=${seconds(warmUpMs)}")
                add("setpts=PTS-STARTPTS")
            }
            add("format=rgb24")
        }.joinToString(",")
        return "[0:v]$head[raw][dirty];" +
            "[raw]${(listOf(inner) + shownSdr).joinToString(",")}[before];" +
            "[dirty]${(denoise + inner + shownSdr).joinToString(",")}[after];" +
            "[before][after]$tail"
    }

    /**
     * The part of a [width] x [height] frame (as displayed) the comparison shows
     * for [focusX] / [focusY], as fractions of the frame: what the graph cuts out
     * (the margin taken away). For the editor to mark it on the whole frame.
     */
    fun shownArea(width: Int, height: Int, focusX: Float, focusY: Float): Area {
        if (width <= 0 || height <= 0) return Area(0f, 0f, 1f, 1f)
        val (left, right) = shownSpan(width, focusX.coerceIn(0f, 1f))
        val (top, bottom) = shownSpan(height, focusY.coerceIn(0f, 1f))
        return Area(left / width, top / height, right / width, bottom / height)
    }

    /**
     * The focus (x, y) that centres the square on the point [x] / [y] (fractions
     * of a [width] x [height] frame), or as near as the frame's edges allow.
     */
    fun focusFor(width: Int, height: Int, x: Float, y: Float): Pair<Float, Float> {
        if (width <= 0 || height <= 0) return 0.5f to 0.5f
        return focusAlong(width, x.coerceIn(0f, 1f)) to focusAlong(height, y.coerceIn(0f, 1f))
    }

    /** A part of a frame, as fractions of its width and height. */
    data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Where the shown square starts and ends along a side of [size] pixels, in pixels. */
    private fun shownSpan(size: Int, focus: Float): Pair<Float, Float> {
        val outer = minOf(size, SIDE_PX + 2 * MARGIN_PX)
        val margin = minOf(MARGIN_PX, outer / 4)
        val start = (size - outer) * focus + margin
        return start to start + (outer - 2 * margin)
    }

    /** The focus along a side of [size] pixels that centres the square on [point] (0..1 of it). */
    private fun focusAlong(size: Int, point: Float): Float {
        val outer = minOf(size, SIDE_PX + 2 * MARGIN_PX)
        if (size <= outer) return 0.5f
        // The square's start is (size - outer) * focus + margin; its centre half the outer side further.
        val start = point * size - outer / 2f
        return (start / (size - outer)).coerceIn(0f, 1f)
    }

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

    private fun decimal(value: Float): String = String.format(Locale.US, "%.4f", value)
}
