package com.cuscus.ffmpeggui.core.model

/**
 * What is done to a picture when the output is an image file (a photo, or one
 * frame of a video at [frameMs]). The turn, mirrors and crop are the whole
 * video's [Effects]; texts and pictures laid on top are the project's
 * [TextOverlay]s and [ImagePlacement]s, as for a video. The rest is here, applied
 * in this order:
 *
 *  1. geometry: [perspectiveH] / [perspectiveV] keystone, then [straightenDegrees]
 *     (the picture zooms in so that no corner shows), then the crop;
 *  2. [tone] (with the ready-made [filter] at [filterAmount] added to it);
 *  3. colour: saturation, hue and the black-and-white / sepia of the filter;
 *  4. [effect] at [effectAmount], then [sharpen], [vignette] and [grain];
 *  5. the colour [key] (a background cut out: transparent, or a colour of its own);
 *  6. texts and pictures on top, then the drawing ([strokes]);
 *  7. [border] around everything, then the output size ([output]);
 *  8. [hdr]: a JPG becomes an Ultra HDR picture, the picture above plus a gain
 *     map that makes its light parts brighter on an HDR screen.
 *
 * The export runs FFmpeg filters for all of it; the editor shows it with ports
 * of the same filters (`core.image`). All defaults leave the picture untouched.
 */
data class ImageEdit(
    val frameMs: Long = 0L,
    val perspectiveH: Float = 0f,
    val perspectiveV: Float = 0f,
    val straightenDegrees: Float = 0f,
    val tone: ImageTone = ImageTone(),
    val filter: ImageFilter = ImageFilter.NONE,
    val filterAmount: Float = 1f,
    val effect: ImageEffect = ImageEffect.NONE,
    val effectAmount: Float = 0.5f,
    /** Colours of the duotone effect: shadows and highlights. */
    val duotoneDark: Long = 0xFF1B1F5AL,
    val duotoneLight: Long = 0xFFFFC857L,
    /** 0..1. */
    val sharpen: Float = 0f,
    /** 0..1: how dark the corners get. */
    val vignette: Float = 0f,
    /** 0..1. */
    val grain: Float = 0f,
    val border: ImageBorder? = null,
    val strokes: List<Stroke> = emptyList(),
    val output: ImageOutput = ImageOutput(),
    /**
     * A colour cut out of the picture. Without a background of its own, what is
     * cut stays transparent (PNG, WEBP, TIFF); see [keyFor] for the formats that
     * cannot hold transparency.
     */
    val key: ColorKey? = null,
    /** An HDR look for the picture (see [ImageHdr]); null for none. */
    val hdr: ImageHdr? = null,
) {
    /**
     * This edit as [format] will hold it: where the format has no transparency
     * (JPG, BMP) a cut-out without a background becomes white, and a see-through
     * background opaque.
     */
    fun keyFor(format: OutputFormat): ImageEdit {
        val cut = key ?: return this
        if (format.holdsTransparency) return this
        return when {
            cut.background == null -> copy(key = cut.over(OPAQUE_KEY_BACKGROUND))
            cut.hasTranslucentBackground -> copy(key = cut.over(cut.background))
            else -> this
        }
    }

    /** True when the picture comes out with transparent (or see-through) parts. */
    val isTransparent: Boolean get() = key != null && (key.background == null || key.hasTranslucentBackground)

    companion object {
        /** What shows through a cut-out in a picture file that holds no transparency. */
        const val OPAQUE_KEY_BACKGROUND = 0xFFFFFF
    }
}

/**
 * Light and colour. Each value is 0 when untouched: [exposure] in stops
 * (-2..2), [hue] in degrees (-180..180), the rest -1..1 ([fade] 0..1).
 */
data class ImageTone(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val hue: Float = 0f,
    val fade: Float = 0f,
) {
    operator fun plus(other: ImageTone): ImageTone = ImageTone(
        exposure + other.exposure, brightness + other.brightness, contrast + other.contrast,
        highlights + other.highlights, shadows + other.shadows, saturation + other.saturation,
        warmth + other.warmth, tint + other.tint, hue + other.hue, fade + other.fade,
    )

    operator fun times(amount: Float): ImageTone = ImageTone(
        exposure * amount, brightness * amount, contrast * amount, highlights * amount, shadows * amount,
        saturation * amount, warmth * amount, tint * amount, hue * amount, fade * amount,
    )
}

/**
 * Ready-made looks: a change of tone, and for some a black-and-white
 * ([mono] 1) or sepia ([sepia] 1) colouring, all scaled by the filter amount.
 */
enum class ImageFilter(val tone: ImageTone = ImageTone(), val mono: Float = 0f, val sepia: Float = 0f) {
    NONE,
    VIVID(ImageTone(contrast = 0.15f, saturation = 0.4f)),
    WARM(ImageTone(warmth = 0.45f, saturation = 0.1f)),
    COOL(ImageTone(warmth = -0.45f, tint = 0.05f)),
    VINTAGE(ImageTone(fade = 0.35f, warmth = 0.3f, saturation = -0.25f, contrast = -0.05f), sepia = 0.25f),
    FADED(ImageTone(fade = 0.55f, saturation = -0.3f, contrast = -0.1f)),
    DRAMA(ImageTone(contrast = 0.4f, shadows = -0.25f, highlights = -0.2f, saturation = -0.15f)),
    FILM(ImageTone(fade = 0.2f, contrast = 0.1f, warmth = 0.1f, saturation = -0.05f)),
    MONO(mono = 1f),
    NOIR(ImageTone(contrast = 0.45f, shadows = -0.2f), mono = 1f),
    SEPIA(ImageTone(contrast = 0.05f), sepia = 1f),
    MATTE(ImageTone(fade = 0.3f, contrast = -0.15f, highlights = -0.2f)),
}

/** Stylised effects, one at a time. */
enum class ImageEffect { NONE, BLUR, PIXELATE, GLITCH, POSTERIZE, INVERT, EMBOSS, SKETCH, DUOTONE }

/** A frame around the picture, [widthFraction] of its shorter side wide. */
data class ImageBorder(val widthFraction: Float = 0.04f, val color: Long = 0xFFFFFFFFL)

/** How a stroke of the drawing is made. */
enum class StrokeShape { FREE, LINE, ARROW, RECTANGLE, OVAL }

/**
 * One stroke of the drawing. [points] are x, y pairs as fractions of the
 * picture (before the border); [width] is a fraction of its width. An [eraser]
 * stroke rubs out what was drawn before it (never the picture).
 */
data class Stroke(
    val points: List<Float>,
    val color: Long = 0xFFFFFFFFL,
    val width: Float = 0.01f,
    val opacity: Float = 1f,
    val shape: StrokeShape = StrokeShape.FREE,
    val eraser: Boolean = false,
)

/** Output size and quality: [maxSide] 0 keeps the size; [quality] 0..1 for JPG and WEBP. */
data class ImageOutput(val maxSide: Int = 0, val quality: Float = 0.9f)

/**
 * The HDR look of a picture, saved as an Ultra HDR JPG: the picture as it is
 * (every screen shows it so) and a gain map that tells an HDR screen how much
 * brighter each part may get.
 *
 * [mode] says where the gain map comes from: worked out from the picture's own
 * light ([HdrMode.GENERATED]) or the photo's own, from the camera
 * ([HdrMode.ORIGINAL], for an Ultra HDR photo). [intensity] 0..1, 0.5 in the
 * middle: generated, the light parts' peak grows up to the middle, then more of
 * the picture comes near it; the photo's own, from none through as shot (the
 * middle) to its lighter parts nearer its brightest (see HdrIntensity). [area] says where:
 * the subject and the background take [mask] (the subject's), the brush
 * [brush] (the painted one); both PNGs whose alpha says how much, in the
 * picture's own pixels (before the edit's turn, straighten and crop, as a
 * subject key's).
 */
data class ImageHdr(
    val mode: HdrMode = HdrMode.GENERATED,
    val intensity: Float = DEFAULT_INTENSITY,
    val area: HdrArea = HdrArea.EVERYWHERE,
    val mask: String? = null,
    val brush: String? = null,
) {
    /** The area's mask, when the area needs one and has it. */
    val areaMask: String?
        get() = when (area) {
            HdrArea.EVERYWHERE -> null
            HdrArea.SUBJECT, HdrArea.BACKGROUND -> mask
            HdrArea.BRUSH -> brush
        }

    /** True when the area has what it needs (the subject, the background and the brush need a mask). */
    val hasArea: Boolean get() = area == HdrArea.EVERYWHERE || areaMask != null

    companion object {
        const val DEFAULT_INTENSITY = 0.5f
    }
}

/** Where the gain map of [ImageHdr] comes from. */
enum class HdrMode { GENERATED, ORIGINAL }

/** Where the HDR look of [ImageHdr] goes. */
enum class HdrArea { EVERYWHERE, SUBJECT, BACKGROUND, BRUSH }
