package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.model.CropRect
import kotlin.math.max
import kotlin.math.min

/**
 * Where the editor's view goes while a picture is cropped: zoomed in on the
 * crop frame and centred on it, so that a small frame fills the stage and its
 * handles are easy to take, away from the thumb (issue #33). Applied when the
 * frame is let go, never under the finger.
 *
 * The content (the picture, or the canvas it lies on) is laid out centred in
 * the stage at scale 1; the view zooms it by [View.scale] around its centre
 * and moves it by [View.x], [View.y] (pixels of the stage).
 */
object CropFocus {

    /** How the content is shown: zoomed by [scale] (1 = as it fits) and moved by [x], [y]. */
    data class View(val scale: Float, val x: Float, val y: Float)

    /** The content as it fits the stage. */
    val FIT = View(1f, 0f, 0f)

    /** Less zoom than this is not worth moving the view for. */
    const val MIN_GAIN = 0.15f

    /**
     * The view that frames [crop] (fractions of the picture), the picture lying
     * at [pictureLeft], [pictureTop] in content [contentWidth] x [contentHeight]
     * (pixels at scale 1) and being [pictureWidth] x [pictureHeight]; the
     * stage is [stageWidth] x [stageHeight], with [margin] pixels kept around
     * the frame for its handles. At most [maxScale]; a frame that already
     * (nearly) fills the stage leaves the content as it fits. The content
     * is kept over the stage where it can be, and never moved farther than
     * [margin] past its edges.
     */
    fun frame(
        crop: CropRect,
        pictureLeft: Float,
        pictureTop: Float,
        pictureWidth: Float,
        pictureHeight: Float,
        contentWidth: Float,
        contentHeight: Float,
        stageWidth: Float,
        stageHeight: Float,
        margin: Float,
        maxScale: Float,
    ): View {
        if (pictureWidth <= 0f || pictureHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f || stageWidth <= 0f || stageHeight <= 0f) return FIT
        val width = crop.width.coerceIn(0f, 1f) * pictureWidth
        val height = crop.height.coerceIn(0f, 1f) * pictureHeight
        if (width <= 0f || height <= 0f) return FIT
        // Room for the frame: the stage less the margin on each side (never less than half of it).
        val roomW = max(stageWidth - 2f * margin, stageWidth / 2f)
        val roomH = max(stageHeight - 2f * margin, stageHeight / 2f)
        val scale = min(min(roomW / width, roomH / height), max(1f, maxScale))
        if (scale < 1f + MIN_GAIN) return FIT
        // The frame's middle, from the content's middle.
        val dx = pictureLeft + (crop.left + crop.width / 2f) * pictureWidth - contentWidth / 2f
        val dy = pictureTop + (crop.top + crop.height / 2f) * pictureHeight - contentHeight / 2f
        val limitX = max(0f, (contentWidth * scale - stageWidth) / 2f) + margin
        val limitY = max(0f, (contentHeight * scale - stageHeight) / 2f) + margin
        return View(scale, (-dx * scale).coerceIn(-limitX, limitX), (-dy * scale).coerceIn(-limitY, limitY))
    }
}
