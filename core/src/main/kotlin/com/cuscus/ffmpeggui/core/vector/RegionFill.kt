package com.cuscus.ffmpeggui.core.vector

import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.vector.svg.Seg
import kotlin.math.abs

/*
 * The SVG editor's paint bucket: the drawing drawn into a picture, the space
 * under a finger found as a flood fill finds it (every pixel reached without
 * crossing one of another colour), and that space traced into an outline the
 * drawing can hold as a shape of its own.
 *
 * Lines rarely meet exactly: [gap] closes holes up to about twice that many
 * pixels (the borders grown by it before the fill, the space grown back by it
 * after), so that the fill does not run out through them.
 */
object RegionFill {

    /**
     * The space at [x], [y] in [picture]: true where it is. Pixels count as
     * the same when their channels differ by at most [tolerance] (0..255);
     * null when [x], [y] is outside.
     */
    fun region(picture: Raster, x: Int, y: Int, gap: Int = 0, tolerance: Int = 24): BooleanArray? {
        val w = picture.width
        val h = picture.height
        if (x !in 0 until w || y !in 0 until h) return null
        val px = picture.pixels
        val seed = px[y * w + x]
        // The borders: whatever is not the colour under the finger.
        val wall = BooleanArray(w * h) { !same(px[it], seed, tolerance) }
        val walls = if (gap > 0) grow(wall, w, h, gap) else wall
        // Under the finger a border grew over it: the space is the finger's own pixel's.
        if (walls[y * w + x] && !wall[y * w + x]) walls[y * w + x] = false
        if (walls[y * w + x]) return null
        val inside = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        stack[top++] = y * w + x
        inside[y * w + x] = true
        while (top > 0) {
            val i = stack[--top]
            val cx = i % w
            val cy = i / w
            if (cx > 0) visit(i - 1, walls, inside, stack, top)?.let { top = it }
            if (cx < w - 1) visit(i + 1, walls, inside, stack, top)?.let { top = it }
            if (cy > 0) visit(i - w, walls, inside, stack, top)?.let { top = it }
            if (cy < h - 1) visit(i + w, walls, inside, stack, top)?.let { top = it }
        }
        // Back over the ground the grown borders took, but never past a real border.
        if (gap > 0) {
            val back = grow(inside, w, h, gap)
            for (i in back.indices) if (back[i] && !wall[i]) inside[i] = true
        }
        return inside
    }

    private fun visit(j: Int, walls: BooleanArray, inside: BooleanArray, stack: IntArray, top: Int): Int? {
        if (walls[j] || inside[j]) return null
        inside[j] = true
        stack[top] = j
        return top + 1
    }

    /** [mask] grown by [r] pixels every way (a square), in two passes. */
    private fun grow(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val across = BooleanArray(w * h)
        for (y in 0 until h) {
            var last = -1_000_000
            for (x in 0 until w) {
                if (mask[y * w + x]) last = x
                if (x - last <= r) across[y * w + x] = true
            }
            last = 1_000_000
            for (x in w - 1 downTo 0) {
                if (mask[y * w + x]) last = x
                if (last - x <= r) across[y * w + x] = true
            }
        }
        val out = BooleanArray(w * h)
        for (x in 0 until w) {
            var last = -1_000_000
            for (y in 0 until h) {
                if (across[y * w + x]) last = y
                if (y - last <= r) out[y * w + x] = true
            }
            last = 1_000_000
            for (y in h - 1 downTo 0) {
                if (across[y * w + x]) last = y
                if (last - y <= r) out[y * w + x] = true
            }
        }
        return out
    }

    private fun same(a: Int, b: Int, tolerance: Int): Boolean {
        val aa = (a ushr 24) and 0xFF
        val ba = (b ushr 24) and 0xFF
        if (abs(aa - ba) > tolerance) return false
        // Both nearly transparent: the same nothing, whatever colour they hold.
        if (aa < 16 && ba < 16) return true
        return abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tolerance &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tolerance &&
            abs((a and 0xFF) - (b and 0xFF)) <= tolerance
    }

    /**
     * The outline of [mask] ([width] x [height]) as segments, in its pixels:
     * smooth curves, holes kept (fill it even-odd). Empty when there is nothing.
     */
    fun outline(mask: BooleanArray, width: Int, height: Int, detail: Float = 0.75f): List<Seg> {
        val picture = Raster(width, height, IntArray(width * height) { if (mask[it]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() })
        val traced = VectorTracer.trace(
            picture,
            TraceSettings(mono = true, detail = detail, smooth = true, speckle = 2, maxSide = maxOf(width, height).coerceAtLeast(64)),
        )
        // The dark layer: the space itself.
        val layer = traced.layers.maxByOrNull { layer -> 255 - (((layer.argb shr 16) and 0xFF) + ((layer.argb shr 8) and 0xFF) + (layer.argb and 0xFF)) / 3 }
            ?: return emptyList()
        // Traced at the picture's own size: its pixels as they are.
        return layer.ops.map { op ->
            when (op) {
                is PathOp.MoveTo -> Seg.Move(op.x.toDouble(), op.y.toDouble())
                is PathOp.LineTo -> Seg.Line(op.x.toDouble(), op.y.toDouble())
                is PathOp.QuadTo -> Seg.Quad(op.cx.toDouble(), op.cy.toDouble(), op.x.toDouble(), op.y.toDouble())
                PathOp.Close -> Seg.Close
            }
        }
    }
}
