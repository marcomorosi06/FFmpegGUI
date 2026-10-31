package com.cuscus.ffmpeggui.core.image

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Rounded corners for a picture file: the last step of the export, on the
 * whole picture (border included, at its output size). [ImageEdit.corners] 0
 * keeps the corners square; 1 rounds them by half the shorter side, so a square
 * picture becomes a circle and a long one a pill. What is cut off is of the
 * colour chosen ([com.cuscus.ffmpeggui.core.model.ImageEdit.cornerColor]):
 * by default transparent in a format that holds transparency (PNG, WEBP,
 * TIFF), black in the others (JPG, BMP).
 *
 * One `geq` does it, its edge smoothed over a pixel: each pixel is covered by
 * the rounded rectangle as far as its centre lies inside it (half a pixel of
 * slope), and its alpha is multiplied by that (or its colour mixed with the fill).
 * Pixels are read where they are (`i=nearest`: geq's bilinear reading takes
 * the last row and column from the ones before them).
 * [roundCorners] is the port of the very same expression, computed in the same
 * order with doubles and truncated as geq stores an 8-bit value.
 */

/** Below this, the corners are square: nothing is done. */
const val MIN_CORNERS = 0.001f

/** True when [corners] (see [com.cuscus.ffmpeggui.core.model.ImageEdit.corners]) rounds anything. */
fun roundsCorners(corners: Float): Boolean = corners >= MIN_CORNERS

/**
 * The filters that round the corners by [corners] (0..1); none for square
 * corners. The cut-off parts are transparent when [fill] is null, otherwise
 * of the colour [fill] (0xRRGGBB): opaque there, and with [keepAlpha] (a
 * format that holds transparency) the rest of the picture keeps its own.
 */
fun cornerFilters(corners: Float, fill: Int?, keepAlpha: Boolean = fill == null): List<String> {
    if (!roundsCorners(corners)) return emptyList()
    val c = num(written(corners.coerceIn(0f, 1f).toDouble()))
    // The radius, at least half a pixel (a smaller one would fade the whole picture).
    val r = "max($c*min(W,H)/2,0.5)"
    val dx = "max(max($r-X-0.5,X+0.5-W+$r),0)"
    val dy = "max(max($r-Y-0.5,Y+0.5-H+$r),0)"
    val cover = "clip($r-hypot($dx,$dy)+0.5,0,1)"
    if (fill == null) {
        return listOf("format=rgba", "geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='alpha(X,Y)*$cover':i=nearest")
    }
    fun mix(channel: String, value: Int) = "$channel(X,Y)*$cover+$value*(1-$cover)"
    val red = (fill shr 16) and 0xFF
    val green = (fill shr 8) and 0xFF
    val blue = fill and 0xFF
    val colours = "r='${mix("r", red)}':g='${mix("g", green)}':b='${mix("b", blue)}'"
    return if (keepAlpha) {
        listOf("format=rgba", "geq=$colours:a='${mix("alpha", 255)}':i=nearest")
    } else {
        listOf("format=gbrp", "geq=$colours:i=nearest")
    }
}

/**
 * How much of the pixel at [x], [y] of a [w] x [h] picture the rounded
 * rectangle of [corners] covers, 0..1, as [cornerFilters]'s expression says.
 */
fun cornerCover(x: Int, y: Int, w: Int, h: Int, corners: Float): Double {
    val c = written(corners.coerceIn(0f, 1f).toDouble())
    val r = max(c * min(w, h).toDouble() / 2, 0.5)
    val dx = max(max(r - x - 0.5, x + 0.5 - w + r), 0.0)
    val dy = max(max(r - y - 0.5, y + 0.5 - h + r), 0.0)
    return (r - hypot(dx, dy) + 0.5).coerceIn(0.0, 1.0)
}

/**
 * [raster] with its corners rounded by [corners], as the export does it (see
 * [cornerFilters]): the cut-off parts transparent when [fill] is null,
 * otherwise of the colour [fill]; with [keepAlpha] the rest keeps its own
 * transparency, without it the picture is opaque.
 */
fun roundCorners(raster: Raster, corners: Float, fill: Int?, keepAlpha: Boolean = fill == null): Raster {
    if (!roundsCorners(corners)) return raster
    val w = raster.width
    val h = raster.height
    val out = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val p = raster.pixels[y * w + x]
            val cover = cornerCover(x, y, w, h, corners)
            out[y * w + x] = if (fill == null) {
                val a = ((p ushr 24) * cover).toInt()
                (p and 0xFFFFFF) or (a shl 24)
            } else {
                fun mix(v: Int, to: Int): Int = (v * cover + to * (1 - cover)).toInt().coerceIn(0, 255)
                val a = if (keepAlpha) mix(p ushr 24, 255) else 0xFF
                (a shl 24) or (mix((p shr 16) and 0xFF, (fill shr 16) and 0xFF) shl 16) or
                    (mix((p shr 8) and 0xFF, (fill shr 8) and 0xFF) shl 8) or mix(p and 0xFF, fill and 0xFF)
            }
        }
    }
    return Raster(w, h, out)
}
