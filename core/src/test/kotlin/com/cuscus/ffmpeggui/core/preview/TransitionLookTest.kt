package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.model.TransitionKind
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

/**
 * The layers the preview draws for a transition (see [transitionLook]) give
 * the picture xfade makes: both are worked out pixel by pixel here, xfade's
 * way as in libavfilter/vf_xfade.c.
 */
class TransitionLookTest {

    private val w = 64
    private val h = 36

    private fun a(x: Int, y: Int) = 0.1f + 0.5f * x / w + 0.2f * y / h
    private fun b(x: Int, y: Int) = 0.9f - 0.3f * x / w - 0.1f * y / h

    /** vf_xfade.c, with progress = 1 - t. */
    private fun xfade(kind: TransitionKind, t: Float, x: Int, y: Int): Float {
        val p = 1f - t
        fun mix(a: Float, b: Float, m: Float) = a * m + b * (1f - m)
        fun wrapX(zx: Int) = zx % w + w * (if (zx < 0) 1 else 0)
        fun wrapY(zy: Int) = zy % h + h * (if (zy < 0) 1 else 0)
        val diagonal = hypot((w / 2).toFloat(), (h / 2).toFloat())
        val distance = hypot((x - w / 2).toFloat(), (y - h / 2).toFloat())
        return when (kind) {
            TransitionKind.FADE -> mix(a(x, y), b(x, y), p)
            TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE -> {
                val bg = if (kind == TransitionKind.FADE_WHITE) 1f else 0f
                mix(mix(a(x, y), bg, smoothstep(0.8f, 1f, p)), mix(bg, b(x, y), smoothstep(0.2f, 1f, p)), p)
            }
            TransitionKind.WIPE_LEFT -> if (x > (w * p).toInt()) b(x, y) else a(x, y)
            TransitionKind.WIPE_RIGHT -> if (x > (w * (1f - p)).toInt()) a(x, y) else b(x, y)
            TransitionKind.WIPE_UP -> if (y > (h * p).toInt()) b(x, y) else a(x, y)
            TransitionKind.WIPE_DOWN -> if (y > (h * (1f - p)).toInt()) a(x, y) else b(x, y)
            TransitionKind.SLIDE_LEFT, TransitionKind.SLIDE_RIGHT -> {
                val z = ((if (kind == TransitionKind.SLIDE_LEFT) -p else p) * w).toInt()
                val zx = z + x
                if (zx in 0 until w) b(wrapX(zx), y) else a(wrapX(zx), y)
            }
            TransitionKind.SLIDE_UP, TransitionKind.SLIDE_DOWN -> {
                val z = ((if (kind == TransitionKind.SLIDE_UP) -p else p) * h).toInt()
                val zy = z + y
                if (zy in 0 until h) b(x, wrapY(zy)) else a(x, wrapY(zy))
            }
            TransitionKind.COVER_LEFT, TransitionKind.COVER_RIGHT -> {
                val zx = ((if (kind == TransitionKind.COVER_LEFT) -p else p) * w).toInt() + x
                if (zx in 0 until w) b(wrapX(zx), y) else a(x, y)
            }
            TransitionKind.REVEAL_LEFT, TransitionKind.REVEAL_RIGHT -> {
                val zx = ((if (kind == TransitionKind.REVEAL_LEFT) -p else p) * w).toInt() + x
                if (zx in 0 until w) b(x, y) else a(wrapX(zx), y)
            }
            TransitionKind.SMOOTH_LEFT -> mix(b(x, y), a(x, y), smoothstep(0f, 1f, 1f + x.toFloat() / w - p * 2f))
            TransitionKind.SMOOTH_RIGHT -> mix(b(x, y), a(x, y), smoothstep(0f, 1f, 1f + (w - 1 - x).toFloat() / w - p * 2f))
            TransitionKind.CIRCLE_OPEN -> mix(a(x, y), b(x, y), smoothstep(0f, 1f, distance / diagonal + (p - 0.5f) * 3f))
            TransitionKind.CIRCLE_CLOSE -> mix(b(x, y), a(x, y), smoothstep(0f, 1f, distance / diagonal + (1f - p - 0.5f) * 3f))
            TransitionKind.VERT_OPEN -> mix(b(x, y), a(x, y), smoothstep(0f, 1f, 2f - abs((x - w / 2f) / (w / 2f)) - p * 2f))
            TransitionKind.HORZ_OPEN -> mix(b(x, y), a(x, y), smoothstep(0f, 1f, 2f - abs((y - h / 2f) / (h / 2f)) - p * 2f))
            TransitionKind.RADIAL -> mix(
                b(x, y), a(x, y),
                smoothstep(0f, 1f, atan2((x - w / 2).toFloat(), (y - h / 2).toFloat()) - (p - 0.5f) * (Math.PI.toFloat() * 2.5f)),
            )
            TransitionKind.CIRCLE_CROP -> {
                val z = (2f * abs(p - 0.5f)).pow(3) * diagonal
                if (z < distance) 0f else if (p < 0.5f) b(x, y) else a(x, y)
            }
            TransitionKind.RECT_CROP -> {
                val zh = (abs(p - 0.5f) * h).toInt()
                val zw = (abs(p - 0.5f) * w).toInt()
                val inside = abs(x - w / 2) < zw && abs(y - h / 2) < zh
                if (!inside) 0f else if (p < 0.5f) b(x, y) else a(x, y)
            }
            TransitionKind.ZOOM_IN -> {
                val zf = smoothstep(0.5f, 1f, p)
                val u = 0.5f + (x.toFloat() / w - 0.5f) * zf
                val v = 0.5f + (y.toFloat() / h - 0.5f) * zf
                mix(a(ceil(u * (w - 1)).toInt(), ceil(v * (h - 1)).toInt()), b(x, y), smoothstep(0f, 0.5f, p))
            }
        }
    }

    /** The preview's way: a backdrop, then the two layers in order, each as [transitionLook] says. */
    private fun layers(kind: TransitionKind, t: Float, x: Int, y: Int): Float {
        var color = if (kind.whiteBehind) 1f else 0f
        val order = if (kind.outgoingOnTop) listOf(true, false) else listOf(false, true)
        for (incoming in order) {
            val look = transitionLook(kind, t, incoming)
            // Where this pixel of the screen falls in the layer.
            val lx = (x - w / 2f - look.dx * w) / look.scale + w / 2f
            val ly = (y - h / 2f - look.dy * h) / look.scale + h / 2f
            val sx = floor(lx + 1e-3f).toInt()
            val sy = floor(ly + 1e-3f).toInt()
            if (sx !in 0 until w || sy !in 0 until h) continue
            if (look.window != null && (lx < look.window.left * w || lx >= look.window.right * w || ly < look.window.top * h || ly >= look.window.bottom * h)) continue
            val diagonal = hypot(w / 2f, h / 2f)
            if (look.circle >= 0f && hypot(lx - w / 2f, ly - h / 2f) > look.circle * diagonal) continue
            val u = when (look.mask) {
                MaskShape.NONE -> 0f
                MaskShape.ACROSS -> lx / w
                MaskShape.DOWN -> ly / h
                MaskShape.RADIUS -> hypot(lx - w / 2f, ly - h / 2f) / diagonal
                MaskShape.ANGLE -> atan2(lx - w / 2f, ly - h / 2f)
            }
            val alpha = look.alpha * (if (look.mask == MaskShape.NONE) 1f else maskAlpha(kind, t, u))
            val value = if (incoming) b(sx, sy) else a(sx, sy)
            color = value * alpha + color * (1f - alpha)
        }
        return color
    }

    @Test
    fun `the layers give xfade's picture`() {
        for (kind in TransitionKind.entries) {
            for (step in 0..20) {
                val t = step / 20f
                var wrong = 0
                for (y in 0 until h) for (x in 0 until w) {
                    if (abs(xfade(kind, t, x, y) - layers(kind, t, x, y)) > 0.03f) wrong++
                }
                // Edges may fall a pixel apart (xfade rounds its lines to whole pixels).
                val allowed = when (kind) {
                    TransitionKind.ZOOM_IN -> w * h / 10
                    // Four edges.
                    TransitionKind.RECT_CROP -> 2 * (w + h)
                    else -> w + h
                }
                assertTrue("$kind at $t: $wrong pixels differ", wrong <= allowed)
            }
        }
    }
}
