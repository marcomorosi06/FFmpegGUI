package com.cuscus.ffmpeggui.core.vector.svg

import kotlin.math.abs

/*
 * What is done to several nodes at once, and to the structure of the drawing:
 * aligning and spacing them out, mirroring them, grouping and ungrouping,
 * turning a basic shape into a path, and putting an outline worked out
 * elsewhere (a boolean of shapes, a stroke made a shape, a text's letters) in
 * their place. Every change gives a new drawing, as in SvgEdits.kt.
 */

/** Where nodes line up: the left edges, the middles across, the right edges; the tops, the middles down, the bottoms. */
enum class SvgAlign { LEFT, CENTER, RIGHT, TOP, MIDDLE, BOTTOM }

/**
 * Of [keys], those not inside another of them (a group and what it holds:
 * the group), in drawing order, the bottom first. Keys of no node are left out.
 */
fun SvgDocument.outermost(keys: Collection<Int>): List<Int> {
    val wanted = keys.toSet()
    val out = ArrayList<Int>()
    fun walk(nodes: List<SvgNode>) {
        for (n in nodes) {
            if (n.key in wanted) {
                out += n.key
                continue
            }
            if (n is SvgGroup) walk(n.children)
        }
    }
    walk(nodes)
    return out
}

/** The box around every one of [keys] on the drawing, in pixels; null when none has one. */
fun SvgDocument.boundsOf(keys: Collection<Int>, measure: TextMeasure = TextMeasure.ESTIMATE): Box? {
    var box: Box? = null
    for (k in outermost(keys)) {
        val b = worldBounds(k, measure) ?: continue
        box = box?.union(b) ?: b
    }
    return box
}

/** The drawing with each of [keys] (the outermost of them) changed by [m], a transform of the drawing's pixels. */
fun SvgDocument.transformAll(keys: Collection<Int>, m: Affine): SvgDocument =
    outermost(keys).fold(this) { d, k -> d.transformInWorld(k, m) }

/**
 * [keys] lined up as [how] says: on the box around them all, or, for a
 * single node or with [toPage], on the page.
 */
fun SvgDocument.align(keys: Collection<Int>, how: SvgAlign, measure: TextMeasure = TextMeasure.ESTIMATE, toPage: Boolean = false): SvgDocument {
    val top = outermost(keys)
    if (top.isEmpty()) return this
    val frame = if (toPage || top.size == 1) Box(0.0, 0.0, width, height) else boundsOf(top, measure) ?: return this
    var d = this
    for (k in top) {
        val b = worldBounds(k, measure) ?: continue
        val dx = when (how) {
            SvgAlign.LEFT -> frame.left - b.left
            SvgAlign.CENTER -> frame.centerX - b.centerX
            SvgAlign.RIGHT -> frame.right - b.right
            else -> 0.0
        }
        val dy = when (how) {
            SvgAlign.TOP -> frame.top - b.top
            SvgAlign.MIDDLE -> frame.centerY - b.centerY
            SvgAlign.BOTTOM -> frame.bottom - b.bottom
            else -> 0.0
        }
        if (abs(dx) > 1e-9 || abs(dy) > 1e-9) d = d.transformInWorld(k, Affine.translate(dx, dy))
    }
    return d
}

/**
 * [keys] spaced out evenly across ([horizontal]) or down: the first and the
 * last stay, the ones between are moved so that the gaps between their boxes
 * are all the same. Fewer than three: nothing to do.
 */
fun SvgDocument.distribute(keys: Collection<Int>, horizontal: Boolean, measure: TextMeasure = TextMeasure.ESTIMATE): SvgDocument {
    val boxes = outermost(keys).mapNotNull { k -> worldBounds(k, measure)?.let { k to it } }
    if (boxes.size < 3) return this
    val sorted = boxes.sortedBy { (_, b) -> if (horizontal) b.centerX else b.centerY }
    val first = sorted.first().second
    val last = sorted.last().second
    val span = if (horizontal) last.right - first.left else last.bottom - first.top
    val sizes = sorted.sumOf { (_, b) -> if (horizontal) b.width else b.height }
    val gap = (span - sizes) / (sorted.size - 1)
    var at = if (horizontal) first.left else first.top
    var d = this
    for ((k, b) in sorted) {
        val delta = at - (if (horizontal) b.left else b.top)
        if (abs(delta) > 1e-9) d = d.transformInWorld(k, if (horizontal) Affine.translate(delta, 0.0) else Affine.translate(0.0, delta))
        at += (if (horizontal) b.width else b.height) + gap
    }
    return d
}

/** [keys] mirrored left to right ([horizontal]) or upside down, about the middle of the box around them. */
fun SvgDocument.flip(keys: Collection<Int>, horizontal: Boolean, measure: TextMeasure = TextMeasure.ESTIMATE): SvgDocument {
    val box = boundsOf(keys, measure) ?: return this
    val m = scaleAbout(if (horizontal) -1.0 else 1.0, if (horizontal) 1.0 else -1.0, box.centerX, box.centerY)
    return transformAll(keys, m)
}

/**
 * [keys] put into a new group, where the top one of them was, looking as they
 * did; with the group's key. A node from another group is moved into the
 * group with its place on the drawing kept (what its old group's style gave
 * it is not carried). Null when fewer than one node is found.
 */
fun SvgDocument.group(keys: Collection<Int>): Pair<SvgDocument, Int>? {
    val top = outermost(keys)
    if (top.isEmpty()) return null
    val above = top.last()
    val parent = parentTransform(above)
    val inverse = parent.inverse()
    // Each one in the units the group is placed in: its whole transform on the drawing, the group's parent undone.
    val children = top.mapNotNull { k ->
        val node = find(k) ?: return@mapNotNull null
        node.withTransform(inverse * worldTransform(k))
    }
    val group = SvgGroup(SvgKeys.next(), null, Affine.IDENTITY, SvgStyle(), emptyMap(), children)
    var d = insertAbove(above, group)
    for (k in top) d = d.removeOne(k, keep = group.key)
    return d to group.key
}

/** The drawing without the node [key], unless it is inside the node [keep]. */
private fun SvgDocument.removeOne(key: Int, keep: Int): SvgDocument {
    fun walk(nodes: List<SvgNode>): List<SvgNode> =
        nodes.filter { it.key != key }.map { if (it is SvgGroup && it.key != keep) it.copy(children = walk(it.children)) else it }
    return copy(nodes = walk(nodes))
}

/**
 * The group [key] taken apart: what it holds put where it was, in its
 * parent, each looking as it did (the group's transform taken into each,
 * its style under each one's own); with their keys. Null for no group.
 */
fun SvgDocument.ungroup(key: Int): Pair<SvgDocument, List<Int>>? {
    val group = find(key) as? SvgGroup ?: return null
    val children = group.children.map { c -> c.withTransform(group.transform * c.transform).withStyle(c.style.over(group.style)) }
    fun walk(nodes: List<SvgNode>): List<SvgNode> {
        val at = nodes.indexOfFirst { it.key == key }
        if (at >= 0) return nodes.subList(0, at) + children + nodes.subList(at + 1, nodes.size)
        return nodes.map { if (it is SvgGroup) it.copy(children = walk(it.children)) else it }
    }
    return copy(nodes = walk(nodes)) to children.map { it.key }
}

/** The outline of the shape [key] on the drawing, in pixels (its transforms applied); null for anything but a shape. */
fun SvgDocument.worldOutline(key: Int): List<Seg>? {
    val shape = find(key) as? SvgShape ?: return null
    val segs = shape.segments()
    if (segs.isEmpty()) return null
    return SvgPath.transform(segs, worldTransform(key))
}

/** A basic shape (a rectangle, a circle...) as a path that draws the same; anything else as it is. */
fun SvgDocument.toPath(key: Int): SvgDocument = update(key) { n ->
    if (n is SvgShape && n.tag != "path") {
        val segs = n.segments()
        if (segs.isEmpty()) n else n.copy(tag = "path", geometry = linkedMapOf("d" to SvgPath.write(segs)))
    } else {
        n
    }
}

/**
 * [outlines] (in pixels of the drawing, each with its style) put in place of
 * [replaced]: laid where [at] is, in its group and in its units (its
 * transform kept, so that an outline's width means what it meant), in the
 * order given (the first at the bottom), then everything in [replaced]
 * removed. With their keys.
 */
fun SvgDocument.replaceWithOutlines(replaced: Collection<Int>, at: Int, outlines: List<Pair<List<Seg>, SvgStyle>>): Pair<SvgDocument, List<Int>> {
    val reference = find(at) ?: return this to emptyList()
    // In [at]'s own units, with its transform: its outline's width stays as it was.
    val toLocal = worldTransform(at).inverse()
    var d = this
    var below = at
    val made = ArrayList<Int>()
    for ((segs, style) in outlines) {
        if (segs.isEmpty()) continue
        val shape = SvgShape(
            SvgKeys.next(), null, reference.transform, style, emptyMap(), "path",
            linkedMapOf("d" to SvgPath.write(SvgPath.transform(segs, toLocal))),
        )
        d = d.insertAbove(below, shape)
        below = shape.key
        made += shape.key
    }
    for (k in outermost(replaced)) d = d.remove(k)
    return d to made
}
