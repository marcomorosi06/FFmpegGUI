package com.cuscus.ffmpeggui.core.vector.svg

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Gradients as the editor makes and changes them. A fill or an outline is
 * painted with a gradient of the definitions ([SvgPaint] with a url). Each
 * node gets one of its own the first time it is changed: a gradient shared
 * with another node (a duplicate, a file's own) is copied first, so that
 * changing one never changes the other. Changing only the stops keeps where
 * the gradient lies as the file had it (its units, its coordinates, its
 * transform); changing its kind or its angle lays it across the node's box.
 * The app's own gradients ("ffg-" ids) that nothing uses any more are left
 * out when the drawing is written.
 */

/** One stop of a gradient: where it sits (0..1) and its colour (ARGB, its alpha the stop's opacity). */
data class GradientStop(val offset: Double, val argb: Long)

/** A gradient as the editor shows and changes it. */
data class GradientSpec(
    /** Out from the middle rather than along a line. */
    val radial: Boolean = false,
    /** A linear one's direction in degrees: 0 left to right, 90 top to bottom. */
    val angle: Double = 0.0,
    /** Its stops, by offset. */
    val stops: List<GradientStop>,
) {
    /** The same gradient run the other way. */
    fun reversed(): GradientSpec = copy(stops = stops.map { GradientStop(1.0 - it.offset, it.argb) }.sortedBy { it.offset })

    /** Its stops sorted, each offset within 0..1. */
    fun sane(): GradientSpec = copy(stops = stops.map { GradientStop(it.offset.coerceIn(0.0, 1.0), it.argb) }.sortedBy { it.offset })

    companion object {
        /** From [from] to [to] (ARGB), left to right. */
        fun between(from: Long, to: Long): GradientSpec = GradientSpec(stops = listOf(GradientStop(0.0, from), GradientStop(1.0, to)))
    }
}

/** The prefix of the ids of the gradients the app makes (the tracer's and the editor's). */
const val APP_GRADIENT_PREFIX = "ffg-"

/** The gradient [paint] names, as the editor shows it; null when it names none the drawing has. */
fun SvgDocument.gradientSpec(paint: SvgPaint): GradientSpec? {
    if (paint.kind != PaintKind.URL) return null
    val g = gradients[paint.url] ?: return null
    if (g.stops.isEmpty()) return null
    return GradientSpec(
        radial = !g.linear,
        angle = if (g.linear) angleOf(g) else 0.0,
        stops = g.stops.map { (offset, argb) -> GradientStop(offset, argb) },
    ).sane()
}

/**
 * The drawing with the fill (or the outline, for [stroke]) of node [key]
 * painted with [spec]: in the gradient it already has when no other node
 * uses it, else in a new one of its own.
 */
fun SvgDocument.paintWithGradient(key: Int, stroke: Boolean, spec: GradientSpec): SvgDocument {
    val node = find(key) ?: return this
    if (node is SvgOther) return this
    val wanted = spec.sane()
    if (wanted.stops.isEmpty()) return this
    val own = if (stroke) node.style.stroke else node.style.fill
    val resolved = resolvedStyle(key).let { if (stroke) it.stroke else it.fill }
    val current = resolved.takeIf { it.kind == PaintKind.URL }?.url
    val existing = current?.let { gradients[it] }
    val element = current?.let { headGradient(it) }
    // Changed in place only when it is this node's alone, written plainly in the definitions.
    val inPlace = element != null && own?.kind == PaintKind.URL && own.url == current &&
        element.attributes["href"] == null && element.attributes["xlink:href"] == null &&
        referenceCounts()[current] == 1
    // Only the stops changed: the gradient stays where it lies.
    val keep = existing?.takeIf { g ->
        g.linear != wanted.radial && (wanted.radial || abs(angleDifference(angleOf(g), wanted.angle)) < 0.5)
    }
    val id = if (inPlace) current!! else freshGradientId()
    val made = gradientElement(id, wanted, keep)
    val head = if (inPlace) replaceHeadGradient(id, made) else withDefinition(made)
    val paint = SvgPaint(PaintKind.URL, url = id)
    return copy(head = head).update(key) { n ->
        n.withStyle(if (stroke) n.style.copy(stroke = paint) else n.style.copy(fill = paint))
    }
}

/**
 * The drawing without the app's own gradients ("ffg-" ids) in its
 * definitions that nothing uses (a shape given a plain colour again, or
 * deleted). Gradients of the file's own are always kept: a program may keep
 * them as swatches.
 */
fun SvgDocument.withoutUnusedGradients(): SvgDocument {
    val used = referenceCounts().keys
    fun unused(e: XmlElement) = (e.localName == "linearGradient" || e.localName == "radialGradient") &&
        e.attributes["id"]?.let { it.startsWith(APP_GRADIENT_PREFIX) && it !in used } == true
    if (head.none { unused(it) || (it.localName == "defs" && it.elements.any(::unused)) }) return this
    val kept = head.mapNotNull { e ->
        when {
            unused(e) -> null
            e.localName == "defs" && e.elements.any(::unused) -> {
                val children = e.children.filter { it !is XmlElement || !unused(it) }
                // A definitions block left with nothing but space goes too.
                if (children.none { it is XmlElement }) null else e.copy(children = children)
            }
            else -> e
        }
    }
    return copy(head = kept)
}

/** How many times each id is named as "#id": by the nodes' fills and outlines, and anywhere in the elements kept as they were. */
private fun SvgDocument.referenceCounts(): Map<String, Int> {
    val counts = HashMap<String, Int>()
    fun add(id: String) { counts[id] = (counts[id] ?: 0) + 1 }
    for (n in allNodes()) {
        n.style.fill?.takeIf { it.kind == PaintKind.URL }?.let { add(it.url) }
        n.style.stroke?.takeIf { it.kind == PaintKind.URL }?.let { add(it.url) }
        for (v in n.attributes.values) REFERENCE.findAll(v).forEach { add(it.groupValues[1]) }
    }
    fun walk(e: XmlElement) {
        for (v in e.attributes.values) REFERENCE.findAll(v).forEach { add(it.groupValues[1]) }
        e.elements.forEach(::walk)
    }
    head.forEach(::walk)
    allNodes().filterIsInstance<SvgOther>().forEach { walk(it.element) }
    return counts
}

/** "#id" in an attribute: a url(#id), an href. */
private val REFERENCE = Regex("""#([A-Za-z_][\w.:-]*)""")

/** The gradient [id] when it lies in the definitions at the top of the drawing (alone, or in a defs). */
private fun SvgDocument.headGradient(id: String): XmlElement? {
    fun isIt(e: XmlElement) = (e.localName == "linearGradient" || e.localName == "radialGradient") && e.attributes["id"] == id
    for (e in head) {
        if (isIt(e)) return e
        if (e.localName == "defs") e.elements.firstOrNull(::isIt)?.let { return it }
    }
    return null
}

/** The definitions with the gradient [id] at the top replaced by [made]. */
private fun SvgDocument.replaceHeadGradient(id: String, made: XmlElement): List<XmlElement> {
    fun isIt(e: XmlElement) = (e.localName == "linearGradient" || e.localName == "radialGradient") && e.attributes["id"] == id
    return head.map { e ->
        when {
            isIt(e) -> made
            e.localName == "defs" && e.elements.any(::isIt) -> e.copy(children = e.children.map { c -> if (c is XmlElement && isIt(c)) made else c })
            else -> e
        }
    }
}

/** The definitions with [made] added: into the first defs at the top, or in a new one. */
private fun SvgDocument.withDefinition(made: XmlElement): List<XmlElement> {
    val at = head.indexOfFirst { it.localName == "defs" }
    if (at < 0) return listOf(XmlElement("defs", LinkedHashMap(), listOf(made))) + head
    return head.mapIndexed { i, e -> if (i == at) e.copy(children = e.children + made) else e }
}

/** An id for a new gradient that nothing in the drawing has yet. */
private fun SvgDocument.freshGradientId(): String {
    val taken = HashSet<String>()
    allNodes().forEach { n -> n.id?.let(taken::add) }
    fun walk(e: XmlElement) {
        e.attributes["id"]?.let(taken::add)
        e.elements.forEach(::walk)
    }
    head.forEach(::walk)
    allNodes().filterIsInstance<SvgOther>().forEach { walk(it.element) }
    var i = 1
    while ("${APP_GRADIENT_PREFIX}gradient-$i" in taken) i++
    return "${APP_GRADIENT_PREFIX}gradient-$i"
}

/**
 * The element of gradient [id] for [spec]: where [keep] lies (its units,
 * coordinates and transform) when given, else across the box of what it
 * paints: a linear one from side to side along its angle, a radial one from
 * the middle to the sides.
 */
private fun gradientElement(id: String, spec: GradientSpec, keep: SvgGradient?): XmlElement {
    val attrs = LinkedHashMap<String, String>()
    attrs["id"] = id
    if (keep != null) {
        if (keep.userSpace) attrs["gradientUnits"] = "userSpaceOnUse"
        val c = keep.coords
        if (keep.linear) {
            attrs["x1"] = n(c[0]); attrs["y1"] = n(c[1]); attrs["x2"] = n(c[2]); attrs["y2"] = n(c[3])
        } else {
            attrs["cx"] = n(c[0]); attrs["cy"] = n(c[1]); attrs["r"] = n(c[2])
            if (c[3] != c[0]) attrs["fx"] = n(c[3])
            if (c[4] != c[1]) attrs["fy"] = n(c[4])
        }
        keep.transform.toSvg().takeIf { it.isNotEmpty() }?.let { attrs["gradientTransform"] = it }
    } else if (spec.radial) {
        attrs["cx"] = "0.5"; attrs["cy"] = "0.5"; attrs["r"] = "0.5"
    } else {
        val radians = Math.toRadians(spec.angle)
        val dx = cos(radians)
        val dy = sin(radians)
        // Long enough to reach the box's far corners whatever the angle.
        val half = (abs(dx) + abs(dy)) / 2
        attrs["x1"] = n4(0.5 - dx * half); attrs["y1"] = n4(0.5 - dy * half)
        attrs["x2"] = n4(0.5 + dx * half); attrs["y2"] = n4(0.5 + dy * half)
    }
    val stops = spec.stops.map { s ->
        val stop = LinkedHashMap<String, String>()
        stop["offset"] = n4(s.offset)
        stop["stop-color"] = String.format(Locale.ROOT, "#%06x", s.argb and 0xFFFFFFL)
        val alpha = ((s.argb ushr 24) and 0xFF) / 255.0
        if (alpha < 1.0) stop["stop-opacity"] = n4(alpha)
        XmlElement("stop", stop)
    }
    return XmlElement(if (spec.radial) "radialGradient" else "linearGradient", attrs, stops)
}

/** The direction a linear gradient runs, in degrees (0..360, 0 left to right), as written. */
private fun angleOf(g: SvgGradient): Double {
    val c = g.coords
    val dx = c[2] - c[0]
    val dy = c[3] - c[1]
    if (dx == 0.0 && dy == 0.0) return 0.0
    val degrees = Math.toDegrees(atan2(dy, dx))
    return if (degrees < 0) degrees + 360.0 else degrees
}

/** [a] - [b] in degrees, within -180..180. */
private fun angleDifference(a: Double, b: Double): Double {
    var d = (a - b) % 360.0
    if (d > 180) d -= 360.0
    if (d < -180) d += 360.0
    return d
}
