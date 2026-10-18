package com.cuscus.ffmpeggui.core.model

import java.util.UUID

/**
 * A caption drawn on top of the picture.
 *
 * [x] and [y] are fractions (0..1) of the free space left around the text, so
 * 0.5 centres it. [size] is the font height as a fraction of the frame height.
 * [color] is ARGB; only the RGB part is used.
 *
 * [opacity] goes from 0 to 1.
 *
 * A caption of the whole video shows from [startMs] to [endMs] (null = to the
 * end) of the exported video, fading in and out over its own fade lengths.
 *
 * With [keyframes], [x], [y], [size] and [opacity] change over time.
 *
 * [clipId] binds the caption to one clip instead (it is then drawn on that
 * clip only, sized against its frame, for the whole clip; its times and fades
 * are not used); null puts it on the whole video.
 */
data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.05f,
    val color: Long = 0xFFFFFFFF,
    val isBold: Boolean = true,
    val clipId: String? = null,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val opacity: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    /** The typeface; the bold one when [isBold] and the phone has it. */
    val font: TextFont = TextFont.SANS,
    /**
     * How its place, size and opacity change while it shows (see [Keyframes];
     * times from its own start, or from its clip's start for a caption of a clip).
     */
    val keyframes: Keyframes = Keyframes.NONE,
    /**
     * Where it lies among the captions and pictures of its clip (or of the
     * whole video): higher is on top (see [stackOrder]).
     */
    val layer: Int = 0,
)
