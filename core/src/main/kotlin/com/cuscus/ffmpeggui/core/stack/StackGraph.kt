package com.cuscus.ffmpeggui.core.stack

/**
 * One step from an editor to the one opened on top of it: [kind] of part
 * [partId] of the editor's project (a clip, a picture laid over, a sound;
 * [NestedKind.part] says which).
 */
data class StackHop(val partId: String, val kind: NestedKind)

/** What a node of the map is. */
enum class GraphNodeKind {
    /** The project: the editor at the bottom of every stack. */
    PROJECT,

    /** A part of a project edited in an editor of its own ([GraphNode.nested] says which). */
    NESTED,
}

/**
 * A node of the map of the editors: the project, or a part of a project (a
 * clip, a picture laid over, a sound) edited in an editor of its own, theirs
 * in turn. Only editors are nodes: the part is told on the link to it
 * ([number], with the kind of part).
 *
 * [parent] is the index of the editor it was opened from (-1: the project).
 * [path] leads from the project to it. [level] is its place on the stack
 * open now (0 the project's), null when it is not on it; [current] marks the
 * editor open now. [applied]: the part's edits are in the clip below (false
 * for a part open now and never applied yet). [number] counts, from 1, the
 * part among those of its kind in the editor below (its clips, its pictures
 * laid over, its sounds; 0 for the project).
 */
data class GraphNode(
    val id: String,
    val title: String,
    val kind: GraphNodeKind,
    val nested: NestedKind?,
    val parent: Int,
    val path: List<StackHop>,
    val level: Int?,
    val current: Boolean,
    val applied: Boolean = true,
    val number: Int = 0,
)

/** The map of the editors: [nodes], each linked to its parent. */
data class StackGraph(val nodes: List<GraphNode>) {

    /** The links, as pairs of indexes (parent, child). */
    val links: List<Pair<Int, Int>> get() = nodes.mapIndexedNotNull { i, n -> if (n.parent in nodes.indices) n.parent to i else null }

    /** Index of the editor open now; -1 when there is none. */
    val currentIndex: Int get() = nodes.indexOfFirst { it.current }

    /** True when [index] is joined to [other] by a link. */
    fun linked(index: Int, other: Int): Boolean =
        nodes.getOrNull(index)?.parent == other || nodes.getOrNull(other)?.parent == index

    companion object {
        val EMPTY = StackGraph(emptyList())

        /**
         * How to go from the stack open now ([open], its hops from the
         * project) to the editor at [target]: the level to go back down to
         * (the editors above it are left), then the hops to open from there.
         */
        fun route(open: List<StackHop>, target: List<StackHop>): Pair<Int, List<StackHop>> {
            var common = 0
            while (common < open.size && common < target.size && open[common] == target[common]) common++
            return common to target.drop(common)
        }
    }
}

/**
 * The map of the editors laid out as a tidy tree, top down: the project at
 * the top, each editor a row under the one it was opened from, the leaves
 * side by side one slot apart in their order, each parent centred over its
 * children. Calm and the same every time: nothing moves unless the editors
 * change.
 */
object TreeLayout {

    /**
     * Where each node of a tree goes: its column ([Placed.x], in slots, the
     * project at 0) and its row ([Placed.depth]). [parent] gives each node's
     * parent (-1: a root), parents listed before their children.
     */
    fun place(parent: IntArray): Placed {
        val n = parent.size
        val children = Array(n) { ArrayList<Int>() }
        val roots = ArrayList<Int>()
        for (i in 0 until n) {
            val p = parent[i]
            if (p in 0 until i) children[p] += i else roots += i
        }
        val x = FloatArray(n)
        val depth = IntArray(n)
        var next = 0f
        fun visit(i: Int, d: Int) {
            depth[i] = d
            val kids = children[i]
            if (kids.isEmpty()) {
                x[i] = next
                next += 1f
            } else {
                kids.forEach { visit(it, d + 1) }
                x[i] = (x[kids.first()] + x[kids.last()]) / 2f
            }
        }
        roots.forEach { visit(it, 0) }
        val shift = roots.firstOrNull()?.let { x[it] } ?: 0f
        for (i in 0 until n) x[i] -= shift
        return Placed(x, depth)
    }

    /** Columns and rows of the nodes, by index. */
    class Placed(val x: FloatArray, val depth: IntArray) {
        val minX: Float get() = x.minOrNull() ?: 0f
        val maxX: Float get() = x.maxOrNull() ?: 0f
        val rows: Int get() = (depth.maxOrNull() ?: -1) + 1
    }
}
