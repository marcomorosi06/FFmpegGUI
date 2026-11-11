package com.cuscus.ffmpeggui.core.stack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackGraphTest {

    private val a = StackHop("c1", NestedKind.CLIP_SOUND)
    private val b = StackHop("c2", NestedKind.CLIP_VIDEO)
    private val c = StackHop("c3", NestedKind.CLIP_SOUND)

    @Test
    fun `a route goes back to what both stacks share, then opens the rest`() {
        // Open: project > a > b. To project > a > c: back to a's editor (level 1), then c.
        assertEquals(1 to listOf(c), StackGraph.route(listOf(a, b), listOf(a, c)))
        // To the project itself: back to level 0, nothing to open.
        assertEquals(0 to emptyList<StackHop>(), StackGraph.route(listOf(a, b), emptyList()))
        // Deeper on the same way: nothing left, one opened.
        assertEquals(2 to listOf(c), StackGraph.route(listOf(a, b), listOf(a, b, c)))
        // Elsewhere from the project.
        assertEquals(0 to listOf(b, c), StackGraph.route(listOf(a), listOf(b, c)))
    }

    @Test
    fun `nodes are linked to the editors they were opened from`() {
        val graph = StackGraph(
            listOf(
                GraphNode("p", "Project", GraphNodeKind.PROJECT, null, -1, emptyList(), level = 0, current = false),
                GraphNode("p/c1/s", "a.mp4", GraphNodeKind.NESTED, NestedKind.CLIP_SOUND, 0, listOf(a), level = 1, current = true, number = 1),
                GraphNode("p/c2/v", "b.mp4", GraphNodeKind.NESTED, NestedKind.CLIP_VIDEO, 0, listOf(b), level = null, current = false, number = 2),
            ),
        )
        assertEquals(listOf(0 to 1, 0 to 2), graph.links)
        assertEquals(1, graph.currentIndex)
        assertTrue(graph.linked(0, 2))
        assertFalse(graph.linked(1, 2))
    }

    @Test
    fun `a tree is laid out top down, each parent over its children`() {
        // 0 > (1 > (3, 4), 2)
        val placed = TreeLayout.place(intArrayOf(-1, 0, 0, 1, 1))
        assertEquals(listOf(0, 1, 1, 2, 2), placed.depth.toList())
        // The leaves one slot apart, in their order: 3, 4, then 2.
        assertEquals(1f, placed.x[4] - placed.x[3], 0f)
        assertEquals(1f, placed.x[2] - placed.x[4], 0f)
        // Each parent centred over its children; the project at 0.
        assertEquals((placed.x[3] + placed.x[4]) / 2f, placed.x[1], 0f)
        assertEquals(0f, placed.x[0], 0f)
        assertEquals(3, placed.rows)
        // A chain stands straight.
        assertEquals(listOf(0f, 0f, 0f), TreeLayout.place(intArrayOf(-1, 0, 1)).x.toList())
        assertEquals(0, TreeLayout.place(IntArray(0)).rows)
    }
}
