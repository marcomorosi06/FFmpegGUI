package com.cuscus.ffmpeggui.core.vector.svg

import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/*
 * An SVG as the editor holds it: a tree of groups, shapes, texts and pictures,
 * each with its transform and its style, everything else of the file kept as
 * it was (definitions, gradients, styles, metadata, elements the editor does
 * not know). Read with [SvgDocument.parse], written back with
 * [SvgDocument.write]: a file read and written unchanged draws the same.
 *
 * Styles come from presentation attributes, then the file's own simple class,
 * element and id rules, then the inline style, as CSS orders them; they are
 * written back inline, so that whatever the editor changes wins over the
 * file's rules everywhere.
 */

/** What paints a fill or an outline. */
enum class PaintKind { NONE, COLOR, URL, CURRENT_COLOR }

/** A fill or outline: nothing, a colour ([argb]), a gradient or pattern ([url], an id), or the current colour. */
data class SvgPaint(val kind: PaintKind, val argb: Long = 0xFF000000L, val url: String = "") {

    fun toCss(): String = when (kind) {
        PaintKind.NONE -> "none"
        PaintKind.COLOR -> String.format(Locale.ROOT, "#%06x", argb and 0xFFFFFFL)
        PaintKind.URL -> "url(#$url)"
        PaintKind.CURRENT_COLOR -> "currentColor"
    }

    companion object {
        val NONE = SvgPaint(PaintKind.NONE)
        val BLACK = SvgPaint(PaintKind.COLOR, 0xFF000000L)

        fun color(argb: Long) = SvgPaint(PaintKind.COLOR, argb or 0xFF000000L)

        /** [text] read as a paint; null when it cannot be (the property is then left as it was). */
        fun parse(text: String?): SvgPaint? {
            val t = text?.trim() ?: return null
            if (t.isEmpty() || t == "inherit") return null
            if (t == "none" || t == "transparent") return NONE
            if (t.equals("currentColor", ignoreCase = true)) return SvgPaint(PaintKind.CURRENT_COLOR)
            if (t.startsWith("url(")) {
                val id = t.substringAfter("url(").substringBefore(")").trim().trim('\'', '"').removePrefix("#")
                return SvgPaint(PaintKind.URL, url = id)
            }
            return parseColor(t)?.let { SvgPaint(PaintKind.COLOR, it) }
        }

        /** A CSS colour (#rgb, #rrggbb, #rrggbbaa, rgb(), rgba(), a usual name) as opaque ARGB; null when unknown. */
        fun parseColor(text: String): Long? {
            val t = text.trim().lowercase(Locale.ROOT)
            if (t.startsWith("#")) {
                val h = t.substring(1)
                return when (h.length) {
                    3, 4 -> h.substring(0, 3).map { "$it$it" }.joinToString("").toLongOrNull(16)?.let { 0xFF000000L or it }
                    6, 8 -> h.substring(0, 6).toLongOrNull(16)?.let { 0xFF000000L or it }
                    else -> null
                }
            }
            if (t.startsWith("rgb")) {
                val parts = t.substringAfter('(').substringBefore(')').split(',', ' ', '/').map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size < 3) return null
                fun channel(p: String): Long = if (p.endsWith('%')) {
                    ((p.dropLast(1).toDoubleOrNull() ?: 0.0) * 2.55).toLong()
                } else {
                    (p.toDoubleOrNull() ?: 0.0).toLong()
                }.coerceIn(0, 255)
                return 0xFF000000L or (channel(parts[0]) shl 16) or (channel(parts[1]) shl 8) or channel(parts[2])
            }
            return NAMED[t]
        }

        private val NAMED = mapOf(
            "black" to 0xFF000000L, "white" to 0xFFFFFFFFL, "red" to 0xFFFF0000L, "green" to 0xFF008000L,
            "blue" to 0xFF0000FFL, "yellow" to 0xFFFFFF00L, "cyan" to 0xFF00FFFFL, "aqua" to 0xFF00FFFFL,
            "magenta" to 0xFFFF00FFL, "fuchsia" to 0xFFFF00FFL, "gray" to 0xFF808080L, "grey" to 0xFF808080L,
            "silver" to 0xFFC0C0C0L, "maroon" to 0xFF800000L, "olive" to 0xFF808000L, "lime" to 0xFF00FF00L,
            "navy" to 0xFF000080L, "teal" to 0xFF008080L, "purple" to 0xFF800080L, "orange" to 0xFFFFA500L,
            "pink" to 0xFFFFC0CBL, "brown" to 0xFFA52A2AL, "gold" to 0xFFFFD700L, "indigo" to 0xFF4B0082L,
            "violet" to 0xFFEE82EEL, "darkgray" to 0xFFA9A9A9L, "lightgray" to 0xFFD3D3D3L, "darkgreen" to 0xFF006400L,
            "darkblue" to 0xFF00008BL, "darkred" to 0xFF8B0000L, "coral" to 0xFFFF7F50L, "tomato" to 0xFFFF6347L,
            "salmon" to 0xFFFA8072L, "khaki" to 0xFFF0E68CL, "beige" to 0xFFF5F5DCL, "ivory" to 0xFFFFFFF0L,
            "turquoise" to 0xFF40E0D0L, "skyblue" to 0xFF87CEEBL, "steelblue" to 0xFF4682B4L, "crimson" to 0xFFDC143CL,
        )
    }
}

/**
 * The style of an element as the editor changes it; null: not set here
 * (inherited, or the default). [other] keeps every other property as written.
 */
data class SvgStyle(
    val fill: SvgPaint? = null,
    val stroke: SvgPaint? = null,
    val strokeWidth: Double? = null,
    val opacity: Double? = null,
    val fillOpacity: Double? = null,
    val strokeOpacity: Double? = null,
    val fillRule: String? = null,
    val fontFamily: String? = null,
    val fontSize: Double? = null,
    val fontWeight: String? = null,
    val textAnchor: String? = null,
    val other: Map<String, String> = emptyMap(),
) {
    /** Hidden by its own style (display: none, visibility: hidden). */
    val hidden: Boolean get() = other["display"] == "none" || other["visibility"] == "hidden"

    fun withHidden(hide: Boolean): SvgStyle = copy(other = if (hide) other + ("display" to "none") else other - "display" - "visibility")

    /** As an inline style ("fill:#ff0000;stroke-width:2"). */
    fun toCss(): String {
        val parts = ArrayList<String>()
        fill?.let { parts += "fill:${it.toCss()}" }
        fillOpacity?.let { parts += "fill-opacity:${n4(it)}" }
        fillRule?.let { parts += "fill-rule:$it" }
        stroke?.let { parts += "stroke:${it.toCss()}" }
        strokeWidth?.let { parts += "stroke-width:${n4(it)}" }
        strokeOpacity?.let { parts += "stroke-opacity:${n4(it)}" }
        opacity?.let { parts += "opacity:${n4(it)}" }
        fontFamily?.let { parts += "font-family:$it" }
        fontSize?.let { parts += "font-size:${n4(it)}px" }
        fontWeight?.let { parts += "font-weight:$it" }
        textAnchor?.let { parts += "text-anchor:$it" }
        for ((k, v) in other) parts += "$k:$v"
        return parts.joinToString(";")
    }

    /** This style over [base]: what this one sets wins. */
    fun over(base: SvgStyle): SvgStyle = SvgStyle(
        fill = fill ?: base.fill,
        stroke = stroke ?: base.stroke,
        strokeWidth = strokeWidth ?: base.strokeWidth,
        opacity = opacity ?: base.opacity,
        fillOpacity = fillOpacity ?: base.fillOpacity,
        strokeOpacity = strokeOpacity ?: base.strokeOpacity,
        fillRule = fillRule ?: base.fillRule,
        fontFamily = fontFamily ?: base.fontFamily,
        fontSize = fontSize ?: base.fontSize,
        fontWeight = fontWeight ?: base.fontWeight,
        textAnchor = textAnchor ?: base.textAnchor,
        other = base.other + other,
    )

    companion object {
        /** The properties a style holds, as CSS names, and what reads them. */
        val PROPERTIES = setOf(
            "fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity", "fill-rule",
            "font-family", "font-size", "font-weight", "text-anchor",
        )

        /** Other presentation attributes, kept in [other]. */
        val PRESENTATION = setOf(
            "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset",
            "display", "visibility", "font-style", "letter-spacing", "clip-rule", "color", "mix-blend-mode",
        )

        /** [declarations] ("fill:red; stroke-width:2") as a style. */
        fun fromCss(declarations: Map<String, String>): SvgStyle {
            var style = SvgStyle()
            val other = LinkedHashMap<String, String>()
            for ((key, raw) in declarations) {
                val v = raw.trim()
                when (key) {
                    "fill" -> SvgPaint.parse(v)?.let { style = style.copy(fill = it) }
                    "stroke" -> SvgPaint.parse(v)?.let { style = style.copy(stroke = it) }
                    "stroke-width" -> cssLength(v)?.let { style = style.copy(strokeWidth = it) }
                    "opacity" -> fraction(v)?.let { style = style.copy(opacity = it) }
                    "fill-opacity" -> fraction(v)?.let { style = style.copy(fillOpacity = it) }
                    "stroke-opacity" -> fraction(v)?.let { style = style.copy(strokeOpacity = it) }
                    "fill-rule" -> style = style.copy(fillRule = v)
                    "font-family" -> style = style.copy(fontFamily = v)
                    "font-size" -> cssLength(v)?.let { style = style.copy(fontSize = it) }
                    "font-weight" -> style = style.copy(fontWeight = v)
                    "text-anchor" -> style = style.copy(textAnchor = v)
                    else -> other[key] = v
                }
            }
            return style.copy(other = other)
        }

        /** "fill:red; stroke : blue" as declarations. */
        fun declarations(css: String?): Map<String, String> {
            if (css.isNullOrBlank()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (part in css.split(';')) {
                val k = part.substringBefore(':', "").trim().lowercase(Locale.ROOT)
                val v = part.substringAfter(':', "").trim().removeSuffix("!important").trim()
                if (k.isNotEmpty() && v.isNotEmpty()) out[k] = v
            }
            return out
        }

        private fun fraction(v: String): Double? =
            if (v.endsWith('%')) v.dropLast(1).toDoubleOrNull()?.div(100) else v.toDoubleOrNull()
    }
}

/** What a node draws with once inherited: every property known. */
data class ResolvedStyle(
    val fill: SvgPaint = SvgPaint.BLACK,
    val stroke: SvgPaint = SvgPaint.NONE,
    val strokeWidth: Double = 1.0,
    val fillOpacity: Double = 1.0,
    val strokeOpacity: Double = 1.0,
    val evenOdd: Boolean = false,
    val fontFamily: String = "sans-serif",
    val fontSize: Double = 16.0,
    val bold: Boolean = false,
    val textAnchor: String = "start",
    /** The node's own opacity (not inherited: groups multiply as they are drawn). */
    val opacity: Double = 1.0,
    /** The other inherited properties as written (stroke-linecap, stroke-dasharray, color...). */
    val other: Map<String, String> = emptyMap(),
) {
    /** The colour "currentColor" stands for (the color property; black when none). */
    val color: Long get() = other["color"]?.let { SvgPaint.parseColor(it) } ?: 0xFF000000L

    /** [style] over this, inherited as SVG inherits. */
    fun inherit(style: SvgStyle): ResolvedStyle = ResolvedStyle(
        fill = style.fill ?: fill,
        stroke = style.stroke ?: stroke,
        strokeWidth = style.strokeWidth ?: strokeWidth,
        fillOpacity = style.fillOpacity ?: fillOpacity,
        strokeOpacity = style.strokeOpacity ?: strokeOpacity,
        evenOdd = style.fillRule?.let { it == "evenodd" } ?: evenOdd,
        fontFamily = style.fontFamily ?: fontFamily,
        fontSize = style.fontSize ?: fontSize,
        bold = style.fontWeight?.let { it == "bold" || it == "bolder" || (it.toIntOrNull() ?: 400) >= 600 } ?: bold,
        textAnchor = style.textAnchor ?: textAnchor,
        opacity = style.opacity ?: 1.0,
        other = if (style.other.keys.none { it in INHERITED }) other else other + style.other.filterKeys { it in INHERITED },
    )

    companion object {
        /** The properties of [SvgStyle.other] that pass from a group to what it holds. */
        val INHERITED = setOf(
            "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset",
            "color", "font-style", "letter-spacing", "visibility", "clip-rule",
        )
    }
}

/** Hands out the editor's own keys for nodes (never written into the file). */
object SvgKeys {
    private val counter = AtomicInteger(1)
    fun next(): Int = counter.getAndIncrement()
}

/** One node of the drawing. [attributes] keeps what the editor does not change (class, data-*...). */
sealed class SvgNode {
    abstract val key: Int
    abstract val id: String?
    abstract val transform: Affine
    abstract val style: SvgStyle
    abstract val attributes: Map<String, String>

    /** A name for the layers list: its id, or what it is. */
    val label: String? get() = attributes["inkscape:label"] ?: id

    abstract fun withTransform(t: Affine): SvgNode
    abstract fun withStyle(s: SvgStyle): SvgNode

    /** The same node with fresh keys (a copy), its id dropped (ids must stay unique). */
    abstract fun duplicated(): SvgNode
}

/** A group of nodes. */
data class SvgGroup(
    override val key: Int,
    override val id: String?,
    override val transform: Affine,
    override val style: SvgStyle,
    override val attributes: Map<String, String>,
    val children: List<SvgNode>,
) : SvgNode() {
    override fun withTransform(t: Affine) = copy(transform = t)
    override fun withStyle(s: SvgStyle) = copy(style = s)
    override fun duplicated() = copy(key = SvgKeys.next(), id = null, children = children.map { it.duplicated() })
}

/**
 * A basic shape or a path: [tag] (rect, circle, ellipse, line, polyline,
 * polygon, path) and its geometry attributes, as written.
 */
data class SvgShape(
    override val key: Int,
    override val id: String?,
    override val transform: Affine,
    override val style: SvgStyle,
    override val attributes: Map<String, String>,
    val tag: String,
    val geometry: Map<String, String>,
) : SvgNode() {
    override fun withTransform(t: Affine) = copy(transform = t)
    override fun withStyle(s: SvgStyle) = copy(style = s)
    override fun duplicated() = copy(key = SvgKeys.next(), id = null)

    /** Its outline in its own coordinates. */
    fun segments(): List<Seg> {
        fun g(k: String) = geometry[k]?.let { cssLength(it) } ?: 0.0
        return when (tag) {
            "path" -> SvgPath.parse(geometry["d"])
            "rect" -> {
                val x = g("x"); val y = g("y"); val w = g("width"); val h = g("height")
                if (w <= 0 || h <= 0) return emptyList()
                var rx = geometry["rx"]?.let { cssLength(it) }
                var ry = geometry["ry"]?.let { cssLength(it) }
                if (rx == null) rx = ry
                if (ry == null) ry = rx
                val r1 = (rx ?: 0.0).coerceIn(0.0, w / 2)
                val r2 = (ry ?: 0.0).coerceIn(0.0, h / 2)
                if (r1 <= 0 || r2 <= 0) {
                    listOf(Seg.Move(x, y), Seg.Line(x + w, y), Seg.Line(x + w, y + h), Seg.Line(x, y + h), Seg.Close)
                } else {
                    val out = ArrayList<Seg>()
                    out += Seg.Move(x + r1, y)
                    out += Seg.Line(x + w - r1, y)
                    out += SvgPath.arc(x + w - r1, y, r1, r2, 0.0, false, true, x + w, y + r2)
                    out += Seg.Line(x + w, y + h - r2)
                    out += SvgPath.arc(x + w, y + h - r2, r1, r2, 0.0, false, true, x + w - r1, y + h)
                    out += Seg.Line(x + r1, y + h)
                    out += SvgPath.arc(x + r1, y + h, r1, r2, 0.0, false, true, x, y + h - r2)
                    out += Seg.Line(x, y + r2)
                    out += SvgPath.arc(x, y + r2, r1, r2, 0.0, false, true, x + r1, y)
                    out += Seg.Close
                    out
                }
            }
            "circle", "ellipse" -> {
                val cx = g("cx"); val cy = g("cy")
                val rx = if (tag == "circle") g("r") else g("rx")
                val ry = if (tag == "circle") g("r") else g("ry")
                if (rx <= 0 || ry <= 0) return emptyList()
                listOf(Seg.Move(cx + rx, cy)) +
                    SvgPath.arc(cx + rx, cy, rx, ry, 0.0, false, true, cx - rx, cy) +
                    SvgPath.arc(cx - rx, cy, rx, ry, 0.0, false, true, cx + rx, cy) + Seg.Close
            }
            "line" -> listOf(Seg.Move(g("x1"), g("y1")), Seg.Line(g("x2"), g("y2")))
            "polyline", "polygon" -> {
                val v = numbers(geometry["points"].orEmpty())
                if (v.size < 4) return emptyList()
                val out = ArrayList<Seg>()
                out += Seg.Move(v[0], v[1])
                var i = 2
                while (i + 1 < v.size) {
                    out += Seg.Line(v[i], v[i + 1])
                    i += 2
                }
                if (tag == "polygon") out += Seg.Close
                out
            }
            else -> emptyList()
        }
    }

    companion object {
        val TAGS = setOf("path", "rect", "circle", "ellipse", "line", "polyline", "polygon")

        /** The geometry attributes of each kind. */
        val GEOMETRY = setOf("d", "x", "y", "width", "height", "rx", "ry", "cx", "cy", "r", "x1", "y1", "x2", "y2", "points", "pathLength")
    }
}

/**
 * A text at [x], [y] (its baseline's start); [raw] keeps its inner markup
 * (tspans placed one by one) until its words are changed.
 */
data class SvgText(
    override val key: Int,
    override val id: String?,
    override val transform: Affine,
    override val style: SvgStyle,
    override val attributes: Map<String, String>,
    val x: Double,
    val y: Double,
    val text: String,
    val raw: XmlElement? = null,
) : SvgNode() {
    override fun withTransform(t: Affine) = copy(transform = t)
    override fun withStyle(s: SvgStyle) = copy(style = s)
    override fun duplicated() = copy(key = SvgKeys.next(), id = null)

    /** Its lines (one per tspan, or as typed). */
    val lines: List<String> get() = text.split('\n')
}

/** A picture laid in the drawing: [href] (a data URI, or a file next to it). */
data class SvgImage(
    override val key: Int,
    override val id: String?,
    override val transform: Affine,
    override val style: SvgStyle,
    override val attributes: Map<String, String>,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val href: String,
) : SvgNode() {
    override fun withTransform(t: Affine) = copy(transform = t)
    override fun withStyle(s: SvgStyle) = copy(style = s)
    override fun duplicated() = copy(key = SvgKeys.next(), id = null)
}

/** Anything else (a use, a foreign object, a switch...): kept and written as it was. */
data class SvgOther(
    override val key: Int,
    override val id: String?,
    override val transform: Affine,
    override val style: SvgStyle,
    override val attributes: Map<String, String>,
    val element: XmlElement,
) : SvgNode() {
    override fun withTransform(t: Affine) = copy(transform = t)
    override fun withStyle(s: SvgStyle) = copy(style = s)
    override fun duplicated() = copy(key = SvgKeys.next(), id = null)
}

/** A gradient of the definitions, for drawing: its stops (offset, ARGB with its own opacity). */
data class SvgGradient(
    val linear: Boolean,
    /** x1 y1 x2 y2 for a linear one; cx cy r fx fy for a radial one. */
    val coords: List<Double>,
    val userSpace: Boolean,
    val transform: Affine,
    val stops: List<Pair<Double, Long>>,
)

/**
 * A whole drawing: [width] x [height] (its size in pixels), drawn from its
 * [viewBox]; [head] the elements written before the drawing as they were
 * (definitions, styles, title, metadata); [nodes] the drawing, bottom first.
 */
data class SvgDocument(
    val width: Double,
    val height: Double,
    val viewBox: Box,
    val head: List<XmlElement>,
    val nodes: List<SvgNode>,
    val rootAttributes: Map<String, String> = emptyMap(),
) {
    /** The view box onto the size: the transform from drawing units to pixels (aspect kept, centred). */
    val viewTransform: Affine
        get() {
            if (viewBox.width <= 0 || viewBox.height <= 0) return Affine.IDENTITY
            val s = minOf(width / viewBox.width, height / viewBox.height)
            val tx = (width - viewBox.width * s) / 2 - viewBox.left * s
            val ty = (height - viewBox.height * s) / 2 - viewBox.top * s
            return Affine(s, 0.0, 0.0, s, tx, ty)
        }

    /** The gradients of the definitions by id, for drawing (a gradient's stops may come from the one it refers to). */
    val gradients: Map<String, SvgGradient> by lazy { SvgReader.gradients(head + nodes.flatMap { SvgReader.otherElements(it) }) }

    /** The SVG text of the drawing (the app's own gradients that nothing uses left out). */
    fun write(): String = Xml.write(SvgWriter.root(withoutUnusedGradients()))

    companion object {
        /** The drawing in [text]; throws [XmlException] when it is not XML, [IllegalArgumentException] when not an SVG. */
        fun parse(text: String): SvgDocument = SvgReader.document(Xml.parse(text))

        /** An empty drawing [width] x [height], with a [background] rectangle when one is given (ARGB). */
        fun blank(width: Double, height: Double, background: Long? = null): SvgDocument {
            val nodes = if (background == null || (background ushr 24) == 0L) {
                emptyList()
            } else {
                val alpha = ((background ushr 24) and 0xFF) / 255.0
                listOf(
                    SvgShape(
                        SvgKeys.next(), "background", Affine.IDENTITY,
                        SvgStyle(fill = SvgPaint.color(background), fillOpacity = alpha.takeIf { it < 1.0 }),
                        emptyMap(), "rect",
                        linkedMapOf("x" to "0", "y" to "0", "width" to n(width), "height" to n(height)),
                    ),
                )
            }
            return SvgDocument(width, height, Box(0.0, 0.0, width, height), emptyList(), nodes)
        }
    }
}

/** [node] as the element it is written as (its style inline, its transform as an attribute). */
fun svgElement(node: SvgNode): XmlElement = SvgWriter.element(node)

/** A length as SVG writes it ("12", "12px", "3mm", "1.5in", "2em"); null when it is not one. */
fun cssLength(text: String, reference: Double = 0.0): Double? {
    val t = text.trim()
    val number = Regex("""^[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""").find(t)?.value ?: return null
    val v = number.toDoubleOrNull() ?: return null
    return when (t.substring(number.length).trim().lowercase(Locale.ROOT)) {
        "", "px" -> v
        "pt" -> v * 4.0 / 3.0
        "pc" -> v * 16.0
        "mm" -> v * 96.0 / 25.4
        "cm" -> v * 96.0 / 2.54
        "in" -> v * 96.0
        "em" -> v * 16.0
        "ex" -> v * 8.0
        "%" -> v / 100.0 * reference
        else -> v
    }
}

/** Reads the tree of an SVG. */
internal object SvgReader {

    fun document(root: XmlElement): SvgDocument {
        require(root.localName == "svg") { "Not an SVG: <${root.name}>" }
        val vbNumbers = numbers(root.attributes["viewBox"].orEmpty())
        val vb = if (vbNumbers.size == 4 && vbNumbers[2] > 0 && vbNumbers[3] > 0) Box(vbNumbers[0], vbNumbers[1], vbNumbers[0] + vbNumbers[2], vbNumbers[1] + vbNumbers[3]) else null
        val w = root.attributes["width"]?.let { cssLength(it, vb?.width ?: 0.0) }?.takeIf { it > 0 && !(root.attributes["width"]!!.endsWith('%')) }
        val h = root.attributes["height"]?.let { cssLength(it, vb?.height ?: 0.0) }?.takeIf { it > 0 && !(root.attributes["height"]!!.endsWith('%')) }
        // One side given: the other from the view box's shape.
        val ratio = if (vb != null) vb.width / vb.height else 2.0
        val width = w ?: h?.let { it * ratio } ?: vb?.width ?: 300.0
        val height = h ?: w?.let { it / ratio } ?: vb?.height ?: 150.0
        val viewBox = vb ?: Box(0.0, 0.0, width, height)
        val rules = rules(root)
        val head = ArrayList<XmlElement>()
        val nodes = ArrayList<SvgNode>()
        for (child in root.elements) {
            when (child.localName) {
                "defs", "style", "title", "desc", "metadata", "sodipodi:namedview", "namedview", "script" -> head += child
                else -> node(child, rules)?.let { nodes += it }
            }
        }
        val kept = root.attributes.filterKeys { it !in setOf("width", "height", "viewBox") }
        return SvgDocument(width, height, viewBox, head, nodes, kept)
    }

    /** A simple CSS rule: what it selects ("rect", ".a", "#b", "rect.a") and its declarations. */
    class Rule(val tag: String?, val classes: Set<String>, val id: String?, val declarations: Map<String, String>) {
        fun matches(e: XmlElement): Boolean {
            if (tag != null && tag != "*" && tag != e.localName) return false
            if (id != null && e.attributes["id"] != id) return false
            if (classes.isNotEmpty()) {
                val own = e.attributes["class"].orEmpty().split(' ').filter { it.isNotEmpty() }.toSet()
                if (!own.containsAll(classes)) return false
            }
            return true
        }

        /** How specific it is (ids, classes, tags), for their order. */
        val specificity: Int get() = (if (id != null) 100 else 0) + classes.size * 10 + (if (tag != null && tag != "*") 1 else 0)
    }

    /** The simple rules of the drawing's styles (selectors of one compound part; others ignored). */
    fun rules(root: XmlElement): List<Rule> {
        val css = buildString {
            fun walk(e: XmlElement) {
                if (e.localName == "style") append(e.textContent).append('\n')
                e.elements.forEach(::walk)
            }
            walk(root)
        }
        if (css.isBlank()) return emptyList()
        val clean = css.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        val out = ArrayList<Rule>()
        for (m in Regex("""([^{}]+)\{([^{}]*)}""").findAll(clean)) {
            val declarations = SvgStyle.declarations(m.groupValues[2])
            for (selector in m.groupValues[1].split(',').map { it.trim() }) {
                if (selector.isEmpty() || selector.any { it == ' ' || it == '>' || it == ':' || it == '[' || it == '+' || it == '~' }) continue
                val parts = Regex("""([.#]?)([\w-]+|\*)""").findAll(selector).toList()
                var tag: String? = null
                var id: String? = null
                val classes = HashSet<String>()
                for (p in parts) {
                    when (p.groupValues[1]) {
                        "." -> classes += p.groupValues[2]
                        "#" -> id = p.groupValues[2]
                        else -> tag = p.groupValues[2]
                    }
                }
                out += Rule(tag, classes, id, declarations)
            }
        }
        return out.sortedBy { it.specificity }
    }

    /** The style of [e]: attributes, then the rules that select it, then its inline style. */
    fun style(e: XmlElement, rules: List<Rule>): SvgStyle {
        val declarations = LinkedHashMap<String, String>()
        for ((k, v) in e.attributes) if (k in SvgStyle.PROPERTIES || k in SvgStyle.PRESENTATION) declarations[k] = v
        for (r in rules) if (r.matches(e)) declarations.putAll(r.declarations)
        declarations.putAll(SvgStyle.declarations(e.attributes["style"]))
        return SvgStyle.fromCss(declarations)
    }

    /** What the node keeps of [e]'s attributes besides those the editor reads. */
    private fun kept(e: XmlElement, also: Set<String> = emptySet()): Map<String, String> =
        e.attributes.filterKeys { it !in also && it != "id" && it != "transform" && it != "style" && it !in SvgStyle.PROPERTIES && it !in SvgStyle.PRESENTATION }

    fun node(e: XmlElement, rules: List<Rule>): SvgNode? {
        val id = e.attributes["id"]
        val t = Affine.parse(e.attributes["transform"])
        val style = style(e, rules)
        val key = SvgKeys.next()
        return when (val tag = e.localName) {
            "g", "a" -> SvgGroup(key, id, t, style, kept(e), e.elements.mapNotNull { node(it, rules) })
            in SvgShape.TAGS -> SvgShape(key, id, t, style, kept(e, SvgShape.GEOMETRY), tag, e.attributes.filterKeys { it in SvgShape.GEOMETRY })
            "text" -> {
                val x = e.attributes["x"]?.let { numbers(it).firstOrNull() } ?: 0.0
                val y = e.attributes["y"]?.let { numbers(it).firstOrNull() } ?: 0.0
                val spans = e.elements.filter { it.localName == "tspan" }
                val text = if (spans.isNotEmpty() && e.children.none { it is XmlText && it.text.isNotBlank() }) {
                    spans.joinToString("\n") { it.textContent }
                } else {
                    e.textContent.trim()
                }
                // Placed piece by piece: its markup kept until its words change.
                val raw = if (e.elements.isNotEmpty()) e else null
                SvgText(key, id, t, style, kept(e, setOf("x", "y")), x, y, text, raw)
            }
            "image" -> SvgImage(
                key, id, t, style, kept(e, setOf("x", "y", "width", "height", "href", "xlink:href")),
                e.attributes["x"]?.let { cssLength(it) } ?: 0.0,
                e.attributes["y"]?.let { cssLength(it) } ?: 0.0,
                e.attributes["width"]?.let { cssLength(it) } ?: 0.0,
                e.attributes["height"]?.let { cssLength(it) } ?: 0.0,
                e.attributes["href"] ?: e.attributes["xlink:href"].orEmpty(),
            )
            // Kept as it is; only whether it is hidden is read (the editor can show it again).
            else -> SvgOther(key, id, Affine.IDENTITY, if (style.hidden) SvgStyle(other = mapOf("display" to "none")) else SvgStyle(), emptyMap(), e)
        }
    }

    /** The elements a node keeps as they were (definitions inside groups among them). */
    fun otherElements(node: SvgNode): List<XmlElement> = when (node) {
        is SvgOther -> listOf(node.element)
        is SvgGroup -> node.children.flatMap { otherElements(it) }
        else -> emptyList()
    }

    /** Every gradient in [elements] (at any depth), with its stops resolved. */
    fun gradients(elements: List<XmlElement>): Map<String, SvgGradient> {
        val found = LinkedHashMap<String, XmlElement>()
        fun walk(e: XmlElement) {
            if (e.localName == "linearGradient" || e.localName == "radialGradient") e.attributes["id"]?.let { found[it] = e }
            e.elements.forEach(::walk)
        }
        elements.forEach(::walk)
        fun stops(e: XmlElement, depth: Int = 0): List<Pair<Double, Long>> {
            val own = e.elements.filter { it.localName == "stop" }.map { s ->
                val d = SvgStyle.declarations(s.attributes["style"])
                val color = SvgPaint.parseColor(d["stop-color"] ?: s.attributes["stop-color"] ?: "black") ?: 0xFF000000L
                val opacity = (d["stop-opacity"] ?: s.attributes["stop-opacity"])?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
                val offsetText = s.attributes["offset"] ?: "0"
                val offset = (if (offsetText.endsWith('%')) offsetText.dropLast(1).toDoubleOrNull()?.div(100) else offsetText.toDoubleOrNull()) ?: 0.0
                offset.coerceIn(0.0, 1.0) to ((color and 0xFFFFFFL) or ((opacity * 255).toLong() shl 24))
            }
            if (own.isNotEmpty() || depth > 8) return own
            val ref = (e.attributes["href"] ?: e.attributes["xlink:href"])?.removePrefix("#") ?: return own
            return found[ref]?.let { stops(it, depth + 1) } ?: own
        }
        return found.mapValues { (_, e) ->
            val user = e.attributes["gradientUnits"] == "userSpaceOnUse"
            fun c(k: String, default: Double): Double {
                val v = e.attributes[k] ?: return default
                return if (v.endsWith('%')) (v.dropLast(1).toDoubleOrNull() ?: 0.0) / 100 else cssLength(v) ?: default
            }
            val linear = e.localName == "linearGradient"
            SvgGradient(
                linear = linear,
                coords = if (linear) {
                    listOf(c("x1", 0.0), c("y1", 0.0), c("x2", 1.0), c("y2", 0.0))
                } else {
                    val cx = c("cx", 0.5)
                    val cy = c("cy", 0.5)
                    listOf(cx, cy, c("r", 0.5), c("fx", cx), c("fy", cy))
                },
                userSpace = user,
                transform = Affine.parse(e.attributes["gradientTransform"]),
                stops = stops(e),
            )
        }
    }
}

/** Writes the tree of an SVG back. */
internal object SvgWriter {

    /** A node kept as it was, with what the editor did to it (moved, hidden) laid over its own. */
    private fun other(node: SvgOther): XmlElement {
        val wasHidden = SvgStyle.declarations(node.element.attributes["style"])["display"] == "none" || node.element.attributes["display"] == "none"
        if (node.transform.isIdentity && node.style.hidden == wasHidden && node.style.other.keys.all { it == "display" }) return node.element
        val attrs = LinkedHashMap(node.element.attributes)
        if (!node.transform.isIdentity) {
            val t = node.transform * Affine.parse(attrs["transform"])
            t.toSvg().let { if (it.isEmpty()) attrs.remove("transform") else attrs["transform"] = it }
        }
        // Its own display and visibility give way to the editor's.
        val own = SvgStyle.declarations(attrs["style"]).filterKeys { it != "display" && it != "visibility" }
        attrs.remove("display")
        attrs.remove("visibility")
        val css = (own.map { "${it.key}:${it.value}" } + listOfNotNull(node.style.toCss().takeIf { it.isNotEmpty() })).joinToString(";")
        if (css.isEmpty()) attrs.remove("style") else attrs["style"] = css
        return node.element.copy(attributes = attrs)
    }

    fun root(doc: SvgDocument): XmlElement {
        val attrs = LinkedHashMap<String, String>()
        attrs["xmlns"] = "http://www.w3.org/2000/svg"
        for ((k, v) in doc.rootAttributes) if (k != "xmlns") attrs[k] = v
        attrs["width"] = n(doc.width)
        attrs["height"] = n(doc.height)
        attrs["viewBox"] = "${n(doc.viewBox.left)} ${n(doc.viewBox.top)} ${n(doc.viewBox.width)} ${n(doc.viewBox.height)}"
        val children: List<XmlNode> = doc.head + doc.nodes.map(::element)
        // Links written as xlink:href by older programs keep their namespace.
        if ("xmlns:xlink" !in attrs && children.any { it is XmlElement && Xml.write(it, declaration = false).contains("xlink:") }) {
            attrs["xmlns:xlink"] = "http://www.w3.org/1999/xlink"
        }
        return XmlElement("svg", attrs, children)
    }

    fun element(node: SvgNode): XmlElement {
        if (node is SvgOther) return other(node)
        val attrs = LinkedHashMap<String, String>()
        node.id?.let { attrs["id"] = it }
        attrs.putAll(node.attributes)
        val children: List<XmlNode>
        val name: String
        when (node) {
            is SvgGroup -> {
                name = "g"
                children = node.children.map(::element)
            }
            is SvgShape -> {
                name = node.tag
                attrs.putAll(node.geometry)
                children = emptyList()
            }
            is SvgText -> {
                name = "text"
                attrs["x"] = n(node.x)
                attrs["y"] = n(node.y)
                children = node.raw?.children ?: if (node.lines.size <= 1) {
                    listOf(XmlText(node.text))
                } else {
                    // One line under the other, a font size apart.
                    node.lines.mapIndexed { i, line ->
                        XmlElement("tspan", linkedMapOf("x" to n(node.x), "dy" to if (i == 0) "0" else "1.2em"), listOf(XmlText(line)))
                    }
                }
            }
            is SvgImage -> {
                name = "image"
                attrs["x"] = n(node.x)
                attrs["y"] = n(node.y)
                attrs["width"] = n(node.width)
                attrs["height"] = n(node.height)
                attrs["href"] = node.href
                children = emptyList()
            }
            is SvgOther -> error("handled above")
        }
        node.transform.toSvg().takeIf { it.isNotEmpty() }?.let { attrs["transform"] = it }
        node.style.toCss().takeIf { it.isNotEmpty() }?.let { attrs["style"] = it }
        return XmlElement(name, attrs, children)
    }
}
