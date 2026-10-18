package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.OverlayLook
import com.cuscus.ffmpeggui.core.model.OverlayShape
import java.util.Locale

/*
 * The filters of an OverlayLook. The preview does the same on the CPU with
 * core.image.OverlayDecoration (same steps, same pixel sizes), so that what is
 * seen while editing is what the export makes.
 */

/**
 * The part of a picture that shows: `crop` with the sides as fractions of the
 * picture (even sizes, which every later filter takes).
 */
internal fun overlayCropFilter(crop: CropRect): String {
    val w = num(crop.width.coerceIn(0.01f, 1f))
    val h = num(crop.height.coerceIn(0.01f, 1f))
    val x = num(crop.left.coerceIn(0f, 1f))
    val y = num(crop.top.coerceIn(0f, 1f))
    return "crop=w='max(2,trunc(iw*$w/2)*2)':h='max(2,trunc(ih*$h/2)*2)':x='trunc(iw*$x)':y='trunc(ih*$y)'"
}

/**
 * What [look] does to a picture once it is sized (rgba in, rgba out), as one
 * link of a filter chain that may hold a little graph of its own ([label]
 * makes its links unique):
 *
 *  1. its content blurred (`boxblur`, three passes, its alpha left sharp);
 *  2. cut to its shape: `geq` multiplies its alpha by how much of each pixel
 *     lies inside the rounded rectangle or ellipse (half a pixel of soft edge);
 *  3. room made around it (`pad`, transparent) for the outline and the shadow;
 *  4. the outline: its alpha spread by a box blur of the outline's radius and
 *     made full from 1/16 up (`lut`), in the outline's colour (`lutrgb`, then
 *     `alphamerge`), the picture laid on it;
 *  5. the shadow: the alpha of all that blurred (two passes) and scaled by the
 *     shadow's opacity, in its colour, laid where it falls on a transparent
 *     copy of the box, the picture with its outline laid on it.
 *
 * Lengths are fractions of the picture's width: after the padding that width
 * is the box's divided by 1 + 2 x the margin.
 */
internal fun overlayLookFilters(look: OverlayLook, label: String): String {
    val parts = mutableListOf("format=rgba")
    if (look.blurs) {
        val radius = "min(trunc(w*${num(look.blur)}),min(w,h)/2-1)"
        parts += "boxblur=lr='$radius':lp=3:ar=0"
    }
    shapeAlpha(look)?.let { alpha ->
        parts += "geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='alpha(X,Y)*$alpha'"
    }
    if (!look.borders && !look.shadows) return parts.joinToString(",")

    val margin = num(look.margin)
    parts += "pad=w='iw+2*trunc(iw*$margin)':h='ih+2*trunc(iw*$margin)':x='trunc(iw*$margin)':y='trunc(iw*$margin)':color=black@0"
    // The picture's own width, from the padded box's.
    val picture = "(w/(1+2*$margin))"
    val chain = StringBuilder(parts.joinToString(","))
    if (look.borders) {
        val radius = "trunc($picture*${num(look.borderWidth)})"
        val (r, g, b) = rgb(look.borderColor)
        chain.append(",split[${label}b0][${label}b1];")
        chain.append("[${label}b1]alphaextract,boxblur=lr='$radius':lp=1,lut=y='clip(val*${OverlayLook.BORDER_GAIN},0,255)'[${label}ba];")
        chain.append("[${label}b0]split[${label}b2][${label}b3];")
        chain.append("[${label}b3]lutrgb=r=$r:g=$g:b=$b[${label}bc];")
        chain.append("[${label}bc][${label}ba]alphamerge[${label}bl];")
        chain.append("[${label}bl][${label}b2]overlay=format=rgb,format=rgba")
    }
    if (look.shadows) {
        val radius = "trunc($picture*${num(look.shadowBlur)})"
        val opacity = num(look.shadowOpacity.coerceIn(0f, 1f))
        val (r, g, b) = rgb(look.shadowColor)
        // On the canvas the box is W wide: its picture is W / (1 + 2 x margin).
        val canvasPicture = "(W/(1+2*$margin))"
        val dx = "trunc($canvasPicture*${num(look.shadowX)})"
        val dy = "trunc($canvasPicture*${num(look.shadowY)})"
        chain.append(",split[${label}s0][${label}s1];")
        chain.append("[${label}s1]alphaextract,boxblur=lr='$radius':lp=2,lut=y='val*$opacity'[${label}sa];")
        chain.append("[${label}s0]split[${label}s2][${label}s3];")
        chain.append("[${label}s3]split[${label}s4][${label}s5];")
        chain.append("[${label}s4]lutrgb=r=$r:g=$g:b=$b[${label}sc];")
        chain.append("[${label}sc][${label}sa]alphamerge[${label}sl];")
        chain.append("[${label}s5]lutrgb=a=0[${label}cv];")
        chain.append("[${label}cv][${label}sl]overlay=x='$dx':y='$dy':format=rgb[${label}sp];")
        chain.append("[${label}sp][${label}s2]overlay=format=rgb,format=rgba")
    }
    return chain.toString()
}

/**
 * How much of pixel (X, Y) lies inside the look's shape, as a `geq` expression
 * (0..1), or null when the shape cuts nothing. The distance from the outline
 * is worked out at the pixel's centre; half a pixel either side is the soft
 * edge. (OverlayDecoration.shapeCoverage is the same, in Kotlin.)
 */
private fun shapeAlpha(look: OverlayLook): String? {
    if (!look.shaped) return null
    return when (look.shape) {
        OverlayShape.RECTANGLE -> null
        OverlayShape.ROUNDED -> {
            val radius = "(min(W,H)*${num(look.cornerRadius.coerceIn(0f, 1f))}/2)"
            val px = "(abs(X+0.5-W/2)-(W/2-$radius))"
            val py = "(abs(Y+0.5-H/2)-(H/2-$radius))"
            val outside = "(hypot(max($px,0),max($py,0))+min(max($px,$py),0)-$radius)"
            "clip(0.5-$outside,0,1)"
        }
        OverlayShape.ELLIPSE -> {
            val k = "hypot((X+0.5-W/2)/(W/2),(Y+0.5-H/2)/(H/2))"
            "clip(0.5-($k-1)*min(W,H)/2,0,1)"
        }
    }
}

private fun rgb(color: Int): Triple<Int, Int, Int> = Triple((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)

private fun num(value: Float): String = String.format(Locale.US, "%.4f", value)
