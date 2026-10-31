package com.cuscus.ffmpeggui.svgedit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Base64
import android.util.Log
import com.caverock.androidsvg.SVG
import com.cuscus.ffmpeggui.core.vector.svg.Affine
import com.cuscus.ffmpeggui.core.vector.svg.Box
import com.cuscus.ffmpeggui.core.vector.svg.PaintKind
import com.cuscus.ffmpeggui.core.vector.svg.ResolvedStyle
import com.cuscus.ffmpeggui.core.vector.svg.SVG_LINE_HEIGHT
import com.cuscus.ffmpeggui.core.vector.svg.Seg
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgGradient
import com.cuscus.ffmpeggui.core.vector.svg.SvgGroup
import com.cuscus.ffmpeggui.core.vector.svg.SvgImage
import com.cuscus.ffmpeggui.core.vector.svg.SvgNode
import com.cuscus.ffmpeggui.core.vector.svg.SvgOther
import com.cuscus.ffmpeggui.core.vector.svg.SvgPaint
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.SvgText
import com.cuscus.ffmpeggui.core.vector.svg.TextMeasure
import com.cuscus.ffmpeggui.core.vector.svg.Xml
import com.cuscus.ffmpeggui.core.vector.svg.XmlElement
import com.cuscus.ffmpeggui.core.vector.svg.svgElement

/*
 * Draws an SVG document of the editor onto an Android canvas, node by node,
 * from the editor's own model: what the user changes shows at once, and a
 * node dragged around is the only thing that moves. Shapes, paths, texts,
 * pictures, solid colours and gradients, opacity and dashes are drawn here;
 * the rest (a use, a text placed piece by piece, anything the editor keeps as
 * it was) is drawn by AndroidSVG, inside the same transforms. Clip paths,
 * masks and filters are not drawn while editing (they stay in the file).
 *
 * The outlines, decoded pictures and AndroidSVG pieces are kept between
 * frames, by node: one renderer per open editor, main thread only.
 */
class SvgRenderer {

    private class CachedPath(val geometry: Map<String, String>, val path: Path)
    private class CachedPiece(val element: XmlElement, val svg: SVG?)

    private val paths = HashMap<Int, CachedPath>()
    private val pieces = HashMap<Int, CachedPiece>()
    private val pictures = HashMap<String, Bitmap?>()
    private var head: List<XmlElement> = emptyList()
    private var headText = ""

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val rect = RectF()

    /** Texts measured with the fonts they are drawn with. */
    val measure = TextMeasure { line, style ->
        setFont(text, style)
        text.measureText(line).toDouble()
    }

    /** Draws [doc] onto [canvas], its pixels as the canvas's units. */
    fun draw(canvas: Canvas, doc: SvgDocument) {
        if (doc.head !== head) {
            head = doc.head
            headText = doc.head.joinToString("") { Xml.write(it, declaration = false) }
            // Pieces drawn by AndroidSVG see the definitions: made again with the new ones.
            pieces.clear()
        }
        canvas.save()
        concat(canvas, doc.viewTransform)
        val gradients = gradientsOf(doc)
        for (n in doc.nodes) node(canvas, n, ResolvedStyle(), gradients)
        canvas.restore()
    }

    private var gradientSources: List<XmlElement> = emptyList()
    private var gradients: Map<String, SvgGradient> = emptyMap()

    /**
     * The drawing's gradients, worked out again only when the elements they can
     * be in change (not at every frame of a drag).
     */
    private fun gradientsOf(doc: SvgDocument): Map<String, SvgGradient> {
        val sources = ArrayList<XmlElement>(doc.head)
        fun walk(nodes: List<SvgNode>) {
            for (n in nodes) {
                if (n is SvgOther) sources += n.element
                if (n is SvgGroup) walk(n.children)
            }
        }
        walk(doc.nodes)
        val same = sources.size == gradientSources.size && sources.indices.all { sources[it] === gradientSources[it] }
        if (!same) {
            gradientSources = sources
            gradients = doc.gradients
        }
        return gradients
    }

    /** Forgets what is kept of nodes that are gone (after a drawing is opened). */
    fun forget() {
        paths.clear()
        pieces.clear()
        pictures.clear()
        gradientSources = emptyList()
    }

    private fun node(canvas: Canvas, n: SvgNode, inherited: ResolvedStyle, gradients: Map<String, SvgGradient>) {
        if (n.style.hidden) return
        val style = inherited.inherit(n.style)
        val saved = canvas.save()
        concat(canvas, n.transform)
        if (style.opacity < 0.999) {
            canvas.saveLayerAlpha(null, (style.opacity.coerceIn(0.0, 1.0) * 255).toInt())
        }
        try {
            when (n) {
                is SvgGroup -> for (child in n.children) node(canvas, child, style, gradients)
                is SvgShape -> shape(canvas, n, style, gradients)
                is SvgText -> if (n.raw != null) piece(canvas, n, style) else text(canvas, n, style, gradients)
                is SvgImage -> image(canvas, n)
                is SvgOther -> piece(canvas, n, style)
            }
        } catch (e: RuntimeException) {
            // One node that cannot be drawn does not take the drawing with it.
            Log.w(TAG, "Cannot draw node ${n.key}", e)
        }
        canvas.restoreToCount(saved)
    }

    private fun shape(canvas: Canvas, n: SvgShape, style: ResolvedStyle, gradients: Map<String, SvgGradient>) {
        val cached = paths[n.key]?.takeIf { it.geometry === n.geometry || it.geometry == n.geometry }
        val path = cached?.path ?: pathOf(n.segments()).also { paths[n.key] = CachedPath(n.geometry, it) }
        path.fillType = if (style.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        if (style.fill.kind != PaintKind.NONE && n.tag != "line") {
            if (paint(fill, style.fill, style.fillOpacity, path, gradients, style)) canvas.drawPath(path, fill)
        }
        if (style.stroke.kind != PaintKind.NONE && style.strokeWidth > 0) {
            stroke.strokeWidth = style.strokeWidth.toFloat()
            stroke.strokeCap = when (style.other["stroke-linecap"]) {
                "round" -> Paint.Cap.ROUND
                "square" -> Paint.Cap.SQUARE
                else -> Paint.Cap.BUTT
            }
            stroke.strokeJoin = when (style.other["stroke-linejoin"]) {
                "round" -> Paint.Join.ROUND
                "bevel" -> Paint.Join.BEVEL
                else -> Paint.Join.MITER
            }
            stroke.strokeMiter = style.other["stroke-miterlimit"]?.toFloatOrNull() ?: 4f
            stroke.pathEffect = dashes(style.other["stroke-dasharray"], style.other["stroke-dashoffset"])
            if (paint(stroke, style.stroke, style.strokeOpacity, path, gradients, style)) canvas.drawPath(path, stroke)
        }
    }

    /** Sets [p] to draw [paint]; false when it draws nothing (a gradient that is not there, all transparent). */
    private fun paint(p: Paint, paint: SvgPaint, opacity: Double, path: Path?, gradients: Map<String, SvgGradient>, style: ResolvedStyle, box: Box? = null): Boolean {
        p.shader = null
        val alpha = (opacity.coerceIn(0.0, 1.0) * 255).toInt()
        when (paint.kind) {
            PaintKind.NONE -> return false
            PaintKind.COLOR -> {
                p.color = (paint.argb and 0xFFFFFFL).toInt() or (alpha shl 24)
            }
            PaintKind.CURRENT_COLOR -> {
                p.color = (style.color and 0xFFFFFFL).toInt() or (alpha shl 24)
            }
            PaintKind.URL -> {
                val g = gradients[paint.url] ?: return false
                if (g.stops.isEmpty()) return false
                if (g.stops.size == 1) {
                    val c = g.stops[0].second
                    p.color = (c and 0xFFFFFFL).toInt() or ((((c ushr 24) and 0xFF) * alpha / 255).toInt() shl 24)
                    return true
                }
                val bounds = box ?: path?.let {
                    it.computeBounds(rect, true)
                    Box(rect.left.toDouble(), rect.top.toDouble(), rect.right.toDouble(), rect.bottom.toDouble())
                } ?: return false
                p.color = (alpha shl 24) or 0xFFFFFF
                p.shader = shaderOf(g, bounds) ?: return false
            }
        }
        return true
    }

    private fun shaderOf(g: SvgGradient, bounds: Box): Shader? {
        var last = 0f
        val positions = FloatArray(g.stops.size) { i ->
            // Offsets never go back, as SVG asks.
            last = maxOf(last, g.stops[i].first.toFloat())
            last
        }
        val colors = IntArray(g.stops.size) { g.stops[it].second.toInt() }
        val c = g.coords.map { it.toFloat() }
        val shader = if (g.linear) {
            LinearGradient(c[0], c[1], c[2], c[3], colors, positions, Shader.TileMode.CLAMP)
        } else {
            if (c[2] <= 0f) return null
            RadialGradient(c[0], c[1], c[2], colors, positions, Shader.TileMode.CLAMP)
        }
        val local = Matrix()
        if (!g.userSpace) {
            if (bounds.width <= 0 || bounds.height <= 0) return null
            local.setScale(bounds.width.toFloat(), bounds.height.toFloat())
            local.postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
        }
        if (!g.transform.isIdentity) {
            val t = Matrix()
            t.setValues(values(g.transform))
            local.preConcat(t)
        }
        shader.setLocalMatrix(local)
        return shader
    }

    private fun dashes(array: String?, offset: String?): DashPathEffect? {
        if (array.isNullOrBlank() || array == "none") return null
        val v = array.split(',', ' ').mapNotNull { it.trim().removeSuffix("px").toFloatOrNull() }.filter { it >= 0f }
        if (v.isEmpty() || v.all { it == 0f }) return null
        val even = if (v.size % 2 == 1) v + v else v
        return DashPathEffect(even.toFloatArray(), offset?.removeSuffix("px")?.toFloatOrNull() ?: 0f)
    }

    private fun text(canvas: Canvas, n: SvgText, style: ResolvedStyle, gradients: Map<String, SvgGradient>) {
        setFont(text, style)
        text.textAlign = when (style.textAnchor) {
            "middle" -> Paint.Align.CENTER
            "end" -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        val lines = n.lines
        val size = style.fontSize.toFloat()
        lines.forEachIndexed { i, line ->
            val y = (n.y + i * style.fontSize * SVG_LINE_HEIGHT).toFloat()
            if (style.fill.kind != PaintKind.NONE) {
                text.style = Paint.Style.FILL
                val width = text.measureText(line)
                val left = when (text.textAlign) {
                    Paint.Align.CENTER -> n.x.toFloat() - width / 2
                    Paint.Align.RIGHT -> n.x.toFloat() - width
                    else -> n.x.toFloat()
                }
                if (paint(text, style.fill, style.fillOpacity, null, gradients, style, Box(left.toDouble(), (y - size).toDouble(), (left + width).toDouble(), y.toDouble()))) {
                    canvas.drawText(line, n.x.toFloat(), y, text)
                }
            }
            if (style.stroke.kind != PaintKind.NONE && style.strokeWidth > 0) {
                text.style = Paint.Style.STROKE
                text.strokeWidth = style.strokeWidth.toFloat()
                if (paint(text, style.stroke, style.strokeOpacity, null, emptyMap(), style)) canvas.drawText(line, n.x.toFloat(), y, text)
                text.style = Paint.Style.FILL
            }
        }
        text.shader = null
    }

    /** The letters of [n] as one outline, in its own units, placed as they are drawn. */
    fun textOutline(n: SvgText, style: ResolvedStyle): Path {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        setFont(p, style)
        p.textAlign = when (style.textAnchor) {
            "middle" -> Paint.Align.CENTER
            "end" -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        val out = Path()
        n.lines.forEachIndexed { i, line ->
            if (line.isEmpty()) return@forEachIndexed
            val y = (n.y + i * style.fontSize * SVG_LINE_HEIGHT).toFloat()
            val piece = Path()
            p.getTextPath(line, 0, line.length, n.x.toFloat(), y, piece)
            out.addPath(piece)
        }
        return out
    }

    private fun setFont(p: Paint, style: ResolvedStyle) {
        p.textSize = style.fontSize.toFloat().coerceIn(0.5f, 4096f)
        p.typeface = typeface(style.fontFamily, style.bold)
    }

    private val typefaces = HashMap<Pair<String, Boolean>, Typeface>()

    private fun typeface(family: String, bold: Boolean): Typeface = typefaces.getOrPut(family to bold) {
        val first = family.split(',').firstOrNull()?.trim()?.trim('\'', '"')?.lowercase().orEmpty()
        val styleBits = if (bold) Typeface.BOLD else Typeface.NORMAL
        when (first) {
            "serif", "times", "times new roman", "georgia" -> Typeface.create(Typeface.SERIF, styleBits)
            "monospace", "courier", "courier new", "consolas" -> Typeface.create(Typeface.MONOSPACE, styleBits)
            "", "sans-serif", "sans", "arial", "helvetica" -> Typeface.create(Typeface.SANS_SERIF, styleBits)
            else -> Typeface.create(first, styleBits)
        }
    }

    private fun image(canvas: Canvas, n: SvgImage) {
        if (n.width <= 0 || n.height <= 0) return
        val bitmap = pictures.getOrPut(n.href) { decode(n.href) } ?: return
        rect.set(n.x.toFloat(), n.y.toFloat(), (n.x + n.width).toFloat(), (n.y + n.height).toFloat())
        // Kept whole and centred, as SVG's default preserveAspectRatio does.
        val keep = n.attributes["preserveAspectRatio"]?.trim() != "none"
        if (keep) {
            val s = minOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
            val w = bitmap.width * s
            val h = bitmap.height * s
            rect.set(rect.centerX() - w / 2, rect.centerY() - h / 2, rect.centerX() + w / 2, rect.centerY() + h / 2)
        }
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
    }

    /** A picture written into the file (a data URI); a link to another file is not followed. */
    private fun decode(href: String): Bitmap? {
        if (!href.startsWith("data:")) return null
        return runCatching {
            val comma = href.indexOf(',')
            val meta = href.substring(5, comma)
            val data = href.substring(comma + 1)
            val bytes = if (meta.endsWith(";base64")) Base64.decode(data, Base64.DEFAULT) else java.net.URLDecoder.decode(data, "UTF-8").toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PICTURE_SIDE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /**
     * A node drawn by AndroidSVG, in the canvas's units as they are (the
     * transforms above it already applied), with what its groups give it.
     */
    private fun piece(canvas: Canvas, n: SvgNode, style: ResolvedStyle) {
        // Its transform and opacity are already on the canvas.
        val element = svgElement(n.withTransform(Affine.IDENTITY).withStyle(n.style.copy(opacity = null)))
        val cached = pieces[n.key]?.takeIf { it.element == element }
        val svg = cached?.svg ?: run {
            val inherited = inheritedCss(style)
            val markup = buildString {
                append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
                append("width=\"$PIECE_SPAN\" height=\"$PIECE_SPAN\" viewBox=\"${-PIECE_SPAN / 2} ${-PIECE_SPAN / 2} $PIECE_SPAN $PIECE_SPAN\">")
                append(headText)
                append("<g style=\"").append(Xml.escape(inherited, attribute = true)).append("\">")
                append(Xml.write(element, declaration = false))
                append("</g></svg>")
            }
            runCatching { SVG.getFromString(markup) }.onFailure { Log.w(TAG, "AndroidSVG cannot read node ${n.key}", it) }.getOrNull()
                .also { pieces[n.key] = CachedPiece(element, it) }
        } ?: return
        canvas.save()
        canvas.translate(-PIECE_SPAN / 2f, -PIECE_SPAN / 2f)
        svg.renderToCanvas(canvas)
        canvas.restore()
    }

    /** What a node's groups give it, as CSS for AndroidSVG (its opacity is drawn here). */
    private fun inheritedCss(s: ResolvedStyle): String = buildString {
        append("fill:").append(s.fill.toCss()).append(';')
        append("stroke:").append(s.stroke.toCss()).append(';')
        append("stroke-width:").append(s.strokeWidth).append(';')
        append("fill-opacity:").append(s.fillOpacity).append(';')
        append("stroke-opacity:").append(s.strokeOpacity).append(';')
        append("fill-rule:").append(if (s.evenOdd) "evenodd" else "nonzero").append(';')
        append("font-family:").append(s.fontFamily).append(';')
        append("font-size:").append(s.fontSize).append("px;")
        append("font-weight:").append(if (s.bold) "bold" else "normal").append(';')
        append("text-anchor:").append(s.textAnchor)
    }

    private fun concat(canvas: Canvas, t: Affine) {
        if (t.isIdentity) return
        matrix.setValues(values(t))
        canvas.concat(matrix)
    }

    private fun values(t: Affine): FloatArray = floatArrayOf(
        t.a.toFloat(), t.c.toFloat(), t.e.toFloat(),
        t.b.toFloat(), t.d.toFloat(), t.f.toFloat(),
        0f, 0f, 1f,
    )

    private companion object {
        const val TAG = "SvgRenderer"
        /** Pictures inside a drawing are decoded no larger than this. */
        const val MAX_PICTURE_SIDE = 2048
        /** How far around the origin a piece drawn by AndroidSVG may reach. */
        const val PIECE_SPAN = 200_000
    }
}

/** [segments] as an Android path. */
internal fun pathOf(segments: List<Seg>): Path {
    val path = Path()
    for (s in segments) {
        when (s) {
            is Seg.Move -> path.moveTo(s.x.toFloat(), s.y.toFloat())
            is Seg.Line -> path.lineTo(s.x.toFloat(), s.y.toFloat())
            is Seg.Quad -> path.quadTo(s.cx.toFloat(), s.cy.toFloat(), s.x.toFloat(), s.y.toFloat())
            is Seg.Cubic -> path.cubicTo(s.c1x.toFloat(), s.c1y.toFloat(), s.c2x.toFloat(), s.c2y.toFloat(), s.x.toFloat(), s.y.toFloat())
            Seg.Close -> path.close()
        }
    }
    return path
}
