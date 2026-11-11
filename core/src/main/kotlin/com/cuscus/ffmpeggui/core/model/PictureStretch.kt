package com.cuscus.ffmpeggui.core.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A picture laid over the video (or a photo on its canvas), or a caption,
 * pulled out of its shape by hand, by the handles on the sides of its box in the editors: the
 * side handle makes it wider or narrower and keeps its height, the bottom one
 * makes it taller or shorter and keeps its width. The corner handle and two
 * fingers keep its shape; [ImagePlacement.stretch] is what changes here.
 *
 * The box is the picture as it shows ([OverlayLook.shownAspect]) with the
 * room its look takes around it ([room], twice [OverlayLook.margin], a
 * fraction of the picture's width on every side): the picture is
 * scaleW x canvasWidth wide and (that / shown) high, its box (1 + room) times
 * as wide and (1 / shown + room) times the picture's width high. The shown
 * shape grows with the stretch (shown = its own shown shape x stretch).
 *
 * The box grows on both sides: a handle dragged [drag] pixels outwards makes
 * it 2 x [drag] larger, so that the edge follows the finger when the picture
 * is in the middle. Near its own shape ([SNAP] either way) it takes its own
 * shape back exactly ([Pulled.ownShape]), so that the way back is easy to
 * find; a stretch stays within [ImagePlacement.MIN_STRETCH] and
 * [ImagePlacement.MAX_STRETCH], a width within [MIN_WIDTH] and
 * [ImagePlacement.MAX_WIDTH], as the export lays them.
 */
object PictureStretch {

    /** How far from its own shape (as a share of the stretch) a pull snaps back to it. */
    const val SNAP = 0.04f

    /** Narrowest a picture is laid, a fraction of the frame (as the editors' other sizes). */
    const val MIN_WIDTH = 0.02f

    /** The picture's width ([scaleW]) and [stretch] after a pull; [ownShape] when it snapped back to its shape. */
    data class Pulled(val scaleW: Float, val stretch: Float, val ownShape: Boolean)

    /**
     * [drag] pixels (screen) made [dx], [dy] along the box turned by
     * [degrees] (clockwise, as the editors turn it): along its width when
     * [sideways], else along its height.
     */
    fun along(dx: Float, dy: Float, degrees: Float, sideways: Boolean): Float {
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return if (sideways) dx * c + dy * s else -dx * s + dy * c
    }

    /**
     * Wider or narrower by the side handle, its height kept: the picture's
     * width and its stretch grow by the same factor. [scaleW] and [stretch]
     * are those at the start of the drag, [drag] the pixels pulled outwards
     * since, on a canvas [canvasWidth] pixels wide.
     */
    fun sideways(scaleW: Float, stretch: Float, room: Float, canvasWidth: Float, drag: Float): Pulled {
        val startStretch = ImagePlacement.saneStretch(stretch)
        if (canvasWidth <= 0f || scaleW <= 0f) return Pulled(scaleW, startStretch, startStretch == 1f)
        val box = scaleW * canvasWidth * (1f + room.coerceAtLeast(0f))
        var factor = ((box + 2f * drag) / box).coerceAtLeast(1e-3f)
        // Within the stretch and the width the export lays.
        factor = factor.coerceIn(ImagePlacement.MIN_STRETCH / startStretch, ImagePlacement.MAX_STRETCH / startStretch)
        factor = factor.coerceIn(MIN_WIDTH / scaleW, ImagePlacement.MAX_WIDTH / scaleW)
        val own = 1f / startStretch
        val snapped = abs(startStretch * factor - 1f) < SNAP && own * scaleW in MIN_WIDTH..ImagePlacement.MAX_WIDTH
        if (snapped) factor = own
        val pulled = if (snapped) 1f else ImagePlacement.saneStretch(startStretch * factor)
        return Pulled(scaleW * factor, pulled, pulled == 1f)
    }

    /** A caption's font [size] (a fraction of the frame's height) and [stretch] after a pull; [ownShape] when it snapped back. */
    data class PulledText(val size: Float, val stretch: Float, val ownShape: Boolean)

    /**
     * A caption made wider or narrower by its side handle, its height (its
     * size) kept: [width] is how wide it is drawn at the start of the drag,
     * in pixels, with its [stretch] then; [drag] the pixels pulled outwards.
     */
    fun textSideways(size: Float, stretch: Float, width: Float, drag: Float): PulledText {
        val startStretch = ImagePlacement.saneStretch(stretch)
        if (width <= 0f) return PulledText(size, startStretch, startStretch == 1f)
        val wanted = startStretch * ((width + 2f * drag) / width).coerceAtLeast(1e-3f)
        val pulled = if (abs(wanted - 1f) < SNAP) 1f else ImagePlacement.saneStretch(wanted)
        return PulledText(size, pulled, pulled == 1f)
    }

    /**
     * A caption made taller or shorter by its bottom handle, its width kept:
     * its size grows with its height (within [minSize]..[maxSize]) and its
     * stretch shrinks as much. [height] is how high it is drawn at the start
     * of the drag, in pixels. Back at its own shape it takes it exactly, at
     * the size that keeps its width.
     */
    fun textUpDown(size: Float, stretch: Float, height: Float, drag: Float, minSize: Float, maxSize: Float): PulledText {
        val startStretch = ImagePlacement.saneStretch(stretch)
        if (height <= 0f || size <= 0f) return PulledText(size, startStretch, startStretch == 1f)
        var grown = (size * (height + 2f * drag) / height).coerceIn(minSize, maxSize)
        // Within the stretch it can be drawn with, its width kept.
        grown = grown.coerceIn(size * startStretch / ImagePlacement.MAX_STRETCH, size * startStretch / ImagePlacement.MIN_STRETCH)
        val wanted = startStretch * size / grown
        val own = size * startStretch
        if (abs(wanted - 1f) < SNAP && own in minSize..maxSize) return PulledText(own, 1f, true)
        val pulled = ImagePlacement.saneStretch(wanted)
        return PulledText(grown, pulled, pulled == 1f)
    }

    /**
     * Taller or shorter by the bottom handle, its width kept: only the
     * stretch changes. [shown] is the picture's shown shape (width / height)
     * at the start of the drag, with its [stretch] then; [drag] the pixels
     * pulled outwards since.
     */
    fun upDown(scaleW: Float, stretch: Float, shown: Float, room: Float, canvasWidth: Float, drag: Float): Pulled {
        val startStretch = ImagePlacement.saneStretch(stretch)
        if (canvasWidth <= 0f || scaleW <= 0f || shown <= 0f) return Pulled(scaleW, startStretch, startStretch == 1f)
        val margins = room.coerceAtLeast(0f)
        val width = scaleW * canvasWidth
        val box = width * (1f / shown + margins)
        // The picture's new height, as a share of its width: never less than a sliver.
        val inverse = ((box + 2f * drag) / width - margins).coerceAtLeast(1e-3f)
        val wanted = startStretch * (1f / inverse) / shown
        val pulled = if (abs(wanted - 1f) < SNAP) 1f else ImagePlacement.saneStretch(wanted)
        return Pulled(scaleW, pulled, pulled == 1f)
    }
}
