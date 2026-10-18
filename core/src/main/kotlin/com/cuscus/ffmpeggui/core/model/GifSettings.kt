package com.cuscus.ffmpeggui.core.model

/**
 * How a GIF is made. A GIF has no sound and at most 256 colours, so its size
 * comes from these: [width] in pixels (0 = as the video; never made larger),
 * [fps] frames a second, [colors] in the palette (2..256), how the missing
 * colours are imitated ([dither]), how many times it plays ([loops]: 0 =
 * forever, -1 = once, n = n more times after the first) and, with [smaller],
 * a palette weighted to what moves and frames that only store the part that
 * changed (a smaller file, the same look where the picture is still).
 */
data class GifSettings(
    val width: Int = 480,
    val fps: Int = 15,
    val colors: Int = 256,
    val dither: GifDither = GifDither.SIERRA,
    val loops: Int = 0,
    val smaller: Boolean = true,
)

/** How paletteuse imitates the colours a GIF palette lacks. */
enum class GifDither(val filter: String) {
    /** FFmpeg's default: error diffusion, light and fine. */
    SIERRA("sierra2_4a"),
    FLOYD_STEINBERG("floyd_steinberg"),
    /** An ordered pattern: steady from frame to frame, so it compresses well. */
    BAYER("bayer:bayer_scale=3"),
    NONE("none"),
}
