package com.cuscus.ffmpeggui.core.model

/*
 * The order captions and pictures are laid on the picture, bottom first: by
 * layer, then (on the same layer) captions under pictures, then in the order
 * they were added. The export draws them in this order and the editor shows
 * them so.
 */

/** One caption ([isText]) or picture, by its index in its own list. */
data class StackSlot(val isText: Boolean, val index: Int)

/** The captions and pictures (their layers, each list in its own order) from the bottom up. */
fun stackOrder(textLayers: List<Int>, pictureLayers: List<Int>): List<StackSlot> =
    (textLayers.mapIndexed { i, layer -> Triple(StackSlot(true, i), layer, 0) } +
        pictureLayers.mapIndexed { i, layer -> Triple(StackSlot(false, i), layer, 1) })
        .sortedWith(compareBy({ it.second }, { it.third }, { it.first.index }))
        .map { it.first }

/** How one caption or picture moves among the others. */
enum class LayerMove { TO_FRONT, FORWARD, BACKWARD, TO_BACK }

/**
 * The layers that put the item at [position] of [order] (bottom first, as
 * [stackOrder] gives it) one step or all the way up or down: one per item,
 * in the same order, numbered from 0.
 */
fun movedLayers(order: List<StackSlot>, position: Int, move: LayerMove): List<StackSlot> {
    if (position !in order.indices) return order
    val target = when (move) {
        LayerMove.TO_FRONT -> order.lastIndex
        LayerMove.FORWARD -> (position + 1).coerceAtMost(order.lastIndex)
        LayerMove.BACKWARD -> (position - 1).coerceAtLeast(0)
        LayerMove.TO_BACK -> 0
    }
    val list = order.toMutableList()
    val item = list.removeAt(position)
    list.add(target, item)
    return list
}
