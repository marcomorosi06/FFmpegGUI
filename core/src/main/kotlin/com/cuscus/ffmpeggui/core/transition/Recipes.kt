package com.cuscus.ffmpeggui.core.transition

import com.cuscus.ffmpeggui.core.model.PUNCH_BACK_SWITCH
import com.cuscus.ffmpeggui.core.model.PUNCH_SWITCH
import com.cuscus.ffmpeggui.core.model.TransitionKind
import kotlin.math.PI

/*
 * The transitions told as layers (see TransitionRecipe), one recipe each. The
 * kinds exported with xfade's own transition (TransitionKind.xfade other than
 * custom) have a recipe that draws the same, for the preview, from
 * libavfilter/vf_xfade.c; the others are this project's own, and the export
 * draws them from the recipe itself.
 *
 * Throughout, X and Y are 0..1 across and down the frame, T how far the
 * transition has gone (0: the clip going out alone, 1: the one coming in).
 */

/** The layers of this kind, or null for the first ones, drawn by core.preview's TransitionLook. */
val TransitionKind.recipe: TransitionRecipe? get() = Recipes.of(this)

internal object Recipes {
    private val cache = HashMap<TransitionKind, TransitionRecipe?>()

    @Synchronized
    fun of(kind: TransitionKind): TransitionRecipe? = cache.getOrPut(kind) { build(kind) }

    private fun build(kind: TransitionKind): TransitionRecipe? = when (kind) {
        TransitionKind.FADE, TransitionKind.FADE_BLACK, TransitionKind.FADE_WHITE,
        TransitionKind.WIPE_LEFT, TransitionKind.WIPE_RIGHT, TransitionKind.WIPE_UP, TransitionKind.WIPE_DOWN,
        TransitionKind.SLIDE_LEFT, TransitionKind.SLIDE_RIGHT, TransitionKind.SLIDE_UP, TransitionKind.SLIDE_DOWN,
        TransitionKind.COVER_LEFT, TransitionKind.COVER_RIGHT, TransitionKind.REVEAL_LEFT, TransitionKind.REVEAL_RIGHT,
        TransitionKind.SMOOTH_LEFT, TransitionKind.SMOOTH_RIGHT, TransitionKind.CIRCLE_OPEN, TransitionKind.CIRCLE_CLOSE,
        TransitionKind.CIRCLE_CROP, TransitionKind.RECT_CROP, TransitionKind.VERT_OPEN, TransitionKind.HORZ_OPEN,
        TransitionKind.RADIAL, TransitionKind.ZOOM_IN,
        -> null

        // -- Basic ------------------------------------------------------------
        TransitionKind.DISSOLVE -> {
            // vf_xfade.c: each pixel goes over at its own moment, from its noise.
            val smooth = noise(floor(X * W), floor(Y * H)) * 2.0 + P * 2.0 - 1.5
            TransitionRecipe(outgoing = Part(), incoming = Part(alpha = lt(smooth, num(0.5))))
        }
        TransitionKind.FADE_GRAYS -> TransitionRecipe(
            outgoing = Part(saturation = 1.0 - clip(T * 2.5, 0.0, 1.0)),
            incoming = Part(alpha = smoothstep(0.3, 0.7, T), saturation = clip((T - 0.6) * 2.5, 0.0, 1.0)),
        )
        TransitionKind.OVERLAP_CUT -> TransitionRecipe(
            // A short cross, the brightest parts of the clip coming in first.
            outgoing = Part(),
            incoming = Part(alpha = clip(((T - 0.5) * 3.0 + (LUMA - 0.5)) * 4.0 + 0.5, 0.0, 1.0)),
        )

        // -- Camera -----------------------------------------------------------
        TransitionKind.ZOOM_OUT -> {
            val e = ease(T)
            TransitionRecipe(
                outgoing = placed(scale = 1.0 - 0.75 * e).part(alpha = 1.0 - smoothstep(0.55, 1.0, T)),
                incoming = placed(scale = 1.3 - 0.3 * e).part(),
                outgoingOnTop = true,
            )
        }
        TransitionKind.PULL_IN -> {
            val e = ease(T)
            TransitionRecipe(
                outgoing = placed(scale = 1.0 + 0.6 * e).part(),
                incoming = placed(scale = 0.7 + 0.3 * e).part(alpha = smoothstep(0.2, 0.7, T)),
            )
        }
        TransitionKind.PULL_OUT -> {
            val e = ease(T)
            TransitionRecipe(
                outgoing = placed(scale = 1.0 - 0.3 * e).part(alpha = 1.0 - smoothstep(0.3, 0.8, T)),
                incoming = placed(scale = 1.6 - 0.6 * e).part(),
                outgoingOnTop = true,
            )
        }
        TransitionKind.WHIP_LEFT -> whip(-1.0, 0.0)
        TransitionKind.WHIP_RIGHT -> whip(1.0, 0.0)
        TransitionKind.WHIP_UP -> whip(0.0, -1.0)
        TransitionKind.WHIP_DOWN -> whip(0.0, 1.0)
        TransitionKind.SPIN_CW -> spin(2.0 * PI)
        TransitionKind.SPIN_CCW -> spin(-2.0 * PI)
        TransitionKind.ROLL -> {
            // Half a turn: the clip going out turns away, the one coming in turns in from the other side.
            val turn = ease(T) * PI
            val scale = 1.0 + 0.8 * bump()
            val a = placed(scale = scale, angle = turn)
            val b = placed(scale = scale, angle = turn + PI)
            TransitionRecipe(
                outgoing = Part(a.u, a.v, wrap = Wrap.MIRROR),
                incoming = Part(b.u, b.v, wrap = Wrap.MIRROR, alpha = smoothstep(0.46, 0.54, T)),
            )
        }
        TransitionKind.SHAKE -> {
            val env = bump()
            val at = placed(
                scale = 1.0 + 0.08 * env,
                angle = 0.03 * env * sin(T * (2.0 * PI * 5.0)),
                dx = 0.04 * env * sin(T * (2.0 * PI * 7.0)),
                dy = 0.03 * env * sin(T * (2.0 * PI * 9.0) + 1.0),
            )
            TransitionRecipe(
                outgoing = Part(at.u, at.v, wrap = Wrap.MIRROR),
                incoming = Part(at.u, at.v, wrap = Wrap.MIRROR, alpha = smoothstep(0.47, 0.53, T)),
            )
        }
        TransitionKind.IMPACT_SHAKE -> {
            // A punch on the cut: the clip going out pushes in, then the one coming in lands hard and shakes off.
            val after = clip((T - 0.5) / 0.5, 0.0, 1.0)
            val before = clip(T / 0.5, 0.0, 1.0)
            val hit = exp(-after * 6.0) * (1.0 - after) * gte(T, num(0.5))
            val a = placed(scale = 1.0 + 0.12 * before * before)
            val b = placed(
                scale = 1.0 + 0.18 * hit,
                angle = 0.04 * hit * sin(after * 41.0),
                dx = 0.05 * hit * sin(after * 47.0),
                dy = 0.04 * hit * sin(after * 59.0 + 1.0),
            )
            TransitionRecipe(
                outgoing = Part(a.u, a.v, wrap = Wrap.MIRROR),
                incoming = Part(b.u, b.v, wrap = Wrap.MIRROR, alpha = smoothstep(0.49, 0.51, T)),
                overlays = listOf(Overlay(Rgb.WHITE, 0.6 * exp(-square((T - 0.5) / 0.04)))),
            )
        }
        TransitionKind.SWIRL -> {
            // Each point turned about the middle, more the nearer it is (none at the corners).
            val cx = (X - 0.5) * W
            val cy = (Y - 0.5) * H
            val r = hypot(cx, cy) / (hypot(W, H) * 0.5)
            val near = 1.0 - r
            val turn = (3.0 * PI) * bump() * near * near
            val px = cx * cos(turn) + cy * sin(turn)
            val py = cy * cos(turn) - cx * sin(turn)
            val u = px / W + 0.5
            val v = py / H + 0.5
            TransitionRecipe(
                outgoing = Part(u, v, wrap = Wrap.MIRROR),
                incoming = Part(u, v, wrap = Wrap.MIRROR, alpha = smoothstep(0.4, 0.6, T)),
            )
        }

        // -- Slide ------------------------------------------------------------
        // vf_xfade.c: the clip coming in slides over (cover), or the one going out slides off it (reveal).
        TransitionKind.COVER_UP -> TransitionRecipe(outgoing = Part(), incoming = Part(v = Y - P))
        TransitionKind.COVER_DOWN -> TransitionRecipe(outgoing = Part(), incoming = Part(v = Y + P))
        TransitionKind.REVEAL_UP -> TransitionRecipe(outgoing = Part(v = Y + T), incoming = Part(), outgoingOnTop = true)
        TransitionKind.REVEAL_DOWN -> TransitionRecipe(outgoing = Part(v = Y - T), incoming = Part(), outgoingOnTop = true)
        TransitionKind.SPLIT_H -> split(X, horizontal = true)
        TransitionKind.SPLIT_V -> split(Y, horizontal = false)
        // vf_xfade.c: the clip going out squeezed into a line through the middle.
        TransitionKind.SQUEEZE_H -> TransitionRecipe(
            outgoing = Part(v = 0.5 + (Y - 0.5) / max(P, num(1e-4)), alpha = gte(P, num(1e-3))),
            incoming = Part(),
            outgoingOnTop = true,
        )
        TransitionKind.SQUEEZE_V -> TransitionRecipe(
            outgoing = Part(u = 0.5 + (X - 0.5) / max(P, num(1e-4)), alpha = gte(P, num(1e-3))),
            incoming = Part(),
            outgoingOnTop = true,
        )

        // -- Wipe -------------------------------------------------------------
        // vf_xfade.c: a soft edge (smooth*, diag*) or a corner (wipe*) going across.
        TransitionKind.SMOOTH_UP -> softIn(1.0 + Y - P * 2.0)
        TransitionKind.SMOOTH_DOWN -> softIn(1.0 + (1.0 - Y) - P * 2.0)
        TransitionKind.DIAG_TL -> softIn(1.0 + X * Y - P * 2.0)
        TransitionKind.DIAG_TR -> softIn(1.0 + (1.0 - X) * Y - P * 2.0)
        TransitionKind.DIAG_BL -> softIn(1.0 + X * (1.0 - Y) - P * 2.0)
        TransitionKind.DIAG_BR -> softIn(1.0 + (1.0 - X) * (1.0 - Y) - P * 2.0)
        // (Its edges a pixel off xfade's, so that the ends show one clip only.)
        TransitionKind.WIPE_TL -> cornerOver(lt(X, P) * lt(Y, P))
        TransitionKind.WIPE_TR -> cornerOver(gte(X, T) * lt(Y, P))
        TransitionKind.WIPE_BL -> cornerOver(lt(X, P) * gte(Y, T))
        TransitionKind.WIPE_BR -> cornerOver(gte(X, T) * gte(Y, T))

        // -- Light ------------------------------------------------------------
        TransitionKind.FLASH -> TransitionRecipe(
            outgoing = Part(),
            incoming = Part(alpha = smoothstep(0.48, 0.52, T)),
            overlays = listOf(Overlay(Rgb.WHITE, exp(-square((T - 0.5) / 0.17)))),
        )
        TransitionKind.LIGHT_LEAK -> {
            val glow = bump()
            TransitionRecipe(
                outgoing = Part(tint = Rgb.WHITE, tintAmount = 0.15 * glow),
                incoming = Part(alpha = smoothstep(0.35, 0.65, T), tint = Rgb.WHITE, tintAmount = 0.15 * glow),
                overlays = listOf(
                    Overlay(Rgb(1.0, 0.5, 0.15), glow * blob(-0.3 + 1.5 * T, num(0.3), 0.12)),
                    Overlay(Rgb(1.0, 0.85, 0.5), 0.8 * glow * blob(1.2 - 1.3 * T, num(0.75), 0.08)),
                    Overlay(Rgb.WHITE, 0.3 * glow),
                ),
            )
        }
        TransitionKind.FILM_BURN -> {
            // A burn grows from the bottom right corner, its edge wavering; the clip coming in shows behind it.
            val aspect = W / H
            val front = -0.2 + 1.6 * T
            val d = hypot((1.0 - X) * aspect, 1.0 - Y) / hypot(aspect, Ex.ONE) +
                0.05 * sin(X * 17.0 + T * 5.0) * sin(Y * 13.0 + 2.0) + 0.03 * sin((X + Y) * 41.0)
            val env = clip(bump() * 3.0, 0.0, 1.0)
            TransitionRecipe(
                outgoing = Part(),
                incoming = Part(alpha = clip((front - d - 0.06) / 0.04, 0.0, 1.0)),
                overlays = listOf(
                    Overlay(Rgb(1.0, 0.5, 0.1), 0.25 * bump()),
                    Overlay(Rgb(1.0, 0.35, 0.05), 0.95 * env * exp(-square((d - front) / 0.07))),
                    Overlay(Rgb(1.0, 0.85, 0.4), 0.9 * env * exp(-square((d - front + 0.03) / 0.03))),
                ),
            )
        }
        TransitionKind.GLOW -> {
            // The picture flares up toward white, then the clip coming in comes down from it.
            val glow = 0.65 * clip(bump() * 1.4, 0.0, 1.0)
            TransitionRecipe(
                outgoing = Part(tint = Rgb.WHITE, tintAmount = glow),
                incoming = Part(alpha = smoothstep(0.4, 0.6, T), tint = Rgb.WHITE, tintAmount = glow),
                overlays = listOf(Overlay(Rgb.WHITE, 0.25 * square(bump()))),
            )
        }
        TransitionKind.LENS_FLARE -> {
            val aspect = W / H
            val fx = -0.2 + 1.4 * T
            val fy = 0.38
            val dx = (X - fx) * aspect
            val dy = Y - fy
            val r = hypot(dx, dy)
            val env = bump()
            val ghost = hypot((X - (1.0 - fx)) * aspect, Y - (1.0 - fy))
            TransitionRecipe(
                outgoing = Part(),
                incoming = Part(alpha = smoothstep(0.4, 0.6, T)),
                overlays = listOf(
                    Overlay(Rgb(1.0, 0.8, 0.5), 0.35 * env * exp(-square(r / 0.3))),
                    Overlay(Rgb(0.7, 0.85, 1.0), 0.9 * env * exp(-square(dy / 0.012)) * exp(-square(dx / 0.9))),
                    Overlay(Rgb(0.6, 0.9, 1.0), 0.35 * env * exp(-square((r - 0.22) / 0.015))),
                    Overlay(Rgb(0.8, 0.9, 1.0), 0.25 * env * exp(-square(ghost / 0.05))),
                    Overlay(Rgb.WHITE, env * exp(-square(r / 0.07))),
                ),
            )
        }

        // -- Glitch -----------------------------------------------------------
        TransitionKind.GLITCH -> {
            // Bands of rows jump sideways at random, colours apart; the clip coming in shows band by band.
            val env = clip(bump() * 1.6, 0.0, 1.0)
            val step = floor(T * 14.0)
            val band = floor(Y * 18.0)
            val n1 = noise(band, step)
            val n2 = noise(band + 31.0, step + 7.0)
            val shift = (n1 - 0.5) * 0.3 * env * gte(abs(n1 - 0.5), num(0.2))
            val split = Sampling.Split(0.015 * env, Ex.ZERO, -0.015 * env, Ex.ZERO)
            TransitionRecipe(
                outgoing = Part(u = X + shift, wrap = Wrap.MIRROR, sampling = split),
                incoming = Part(u = X + shift, wrap = Wrap.MIRROR, sampling = split, alpha = lt(n2, clip((T - 0.3) / 0.4, 0.0, 1.0))),
            )
        }
        TransitionKind.RGB_SPLIT -> {
            val env = bump()
            val at = placed(scale = 1.0 + 0.08 * env)
            val split = Sampling.Split(0.035 * env, Ex.ZERO, -0.035 * env, 0.01 * env)
            TransitionRecipe(
                outgoing = at.part(wrap = Wrap.MIRROR, sampling = split),
                incoming = at.part(wrap = Wrap.MIRROR, sampling = split, alpha = smoothstep(0.4, 0.6, T)),
            )
        }
        TransitionKind.WAVE -> {
            val env = bump()
            val u = X + 0.035 * env * sin(Y * 22.0 + T * 14.0)
            val v = Y + 0.02 * env * sin(X * 16.0 + T * 10.0)
            TransitionRecipe(
                outgoing = Part(u, v, wrap = Wrap.MIRROR),
                incoming = Part(u, v, wrap = Wrap.MIRROR, alpha = smoothstep(0.35, 0.65, T)),
            )
        }
        TransitionKind.TV_NOISE -> {
            val env = bump()
            val frame = floor(T * 30.0)
            val snow = noise(floor(X * 160.0) + frame * 1.37, floor(Y * 90.0) + frame * 2.11)
            val u = X + 0.01 * env * sin(Y * 120.0 + T * 50.0)
            TransitionRecipe(
                outgoing = Part(u = u, wrap = Wrap.MIRROR),
                incoming = Part(u = u, wrap = Wrap.MIRROR, alpha = smoothstep(0.48, 0.52, T)),
                overlays = listOf(Overlay(Rgb.WHITE, 0.85 * clip(env * 2.5 - 0.5, 0.0, 1.0), level = snow)),
            )
        }
        TransitionKind.PIXELATE -> {
            // vf_xfade.c: both clips in blocks, largest halfway, while one fades into the other.
            val dist = ceil(min(P, T) * 50.0) / 50.0
            val cell = max(2.0 * dist * min(W, H) / 20.0, Ex.ONE)
            val sx = min((floor(X * W / cell) + 0.5) * cell, W - 1.0)
            val sy = min((floor(Y * H / cell) + 0.5) * cell, H - 1.0)
            val u = iff(gte(dist, num(1e-6)), sx / W, X)
            val v = iff(gte(dist, num(1e-6)), sy / H, Y)
            TransitionRecipe(outgoing = Part(u, v), incoming = Part(u, v, alpha = T))
        }

        // -- Blur -------------------------------------------------------------
        TransitionKind.ZOOM_BLUR -> {
            // Three copies of each picture, each a little larger than the last, averaged.
            val env = bump()
            val e = ease(T)
            TransitionRecipe(
                outgoing = echo(List(3) { k -> placed(scale = (1.0 + 0.25 * e) * (1.0 + 0.06 * k * env)) }),
                incoming = echo(List(3) { k -> placed(scale = (1.25 - 0.25 * e) * (1.0 + 0.06 * k * env)) }, alpha = smoothstep(0.4, 0.6, T)),
            )
        }
        TransitionKind.MOTION_BLUR -> {
            val env = bump()
            val e = ease(T)
            val length = 0.08 * env
            val taps = (0 until 5).map { k -> (length * (k / 4.0 - 0.5)) to Ex.ZERO }
            val blur = Sampling.Blur(taps, NativeBlur.Streak(0.0, 0.25 * length))
            val a = placed(dx = -0.15 * e)
            val b = placed(dx = 0.15 * (1.0 - e))
            TransitionRecipe(
                outgoing = Part(a.u, a.v, wrap = Wrap.MIRROR, sampling = blur),
                incoming = Part(b.u, b.v, wrap = Wrap.MIRROR, sampling = blur, alpha = smoothstep(0.35, 0.65, T)),
            )
        }
        TransitionKind.SPIN_BLUR -> {
            // Three copies of each picture, turned a little apart, averaged; enlarged so that no corner shows.
            val env = bump()
            fun copies(turn: Ex) = List(3) { k -> placed(scale = 1.0 + 0.45 * env, angle = turn + (k - 1) * 0.07 * env) }
            TransitionRecipe(
                outgoing = echo(copies(0.2 * env)),
                incoming = echo(copies(-0.2 * env), alpha = smoothstep(0.4, 0.6, T)),
            )
        }
        TransitionKind.DEFOCUS -> {
            val radius = 0.025 * bump()
            val taps = listOf(Ex.ZERO to Ex.ZERO) + (0 until 8).map { k ->
                val a = k * PI / 4.0
                (radius * kotlin.math.cos(a) * H / W) to (radius * kotlin.math.sin(a))
            }
            val blur = Sampling.Blur(taps, NativeBlur.Gauss(0.6 * radius))
            TransitionRecipe(
                outgoing = Part(sampling = blur),
                incoming = Part(sampling = blur, alpha = smoothstep(0.35, 0.65, T)),
            )
        }

        // -- Shapes -----------------------------------------------------------
        TransitionKind.DIAMOND -> {
            val aspect = W / H
            val d = abs(X - 0.5) * aspect + abs(Y - 0.5)
            val size = T * (0.5 * aspect + 0.55)
            shapeIn(clip((size - d) / 0.01, 0.0, 1.0))
        }
        TransitionKind.HEXAGON -> {
            val aspect = W / H
            val qx = abs(X - 0.5) * aspect
            val qy = abs(Y - 0.5)
            val d = max(qx * 0.866 + qy * 0.5, qy)
            val size = T * (max(0.433 * aspect + 0.25, num(0.5)) + 0.05)
            shapeIn(clip((size - d) / 0.01, 0.0, 1.0))
        }
        TransitionKind.STAR -> {
            val aspect = W / H
            val qx = (X - 0.5) * aspect
            val qy = Y - 0.5
            val r = hypot(qx, qy)
            val k = abs(cos((atan2(qy, qx) + T * 1.2) * 2.5))
            val size = T * (0.5 * hypot(aspect, Ex.ONE) / 0.45 + 0.1)
            val radius = size * (0.45 + 0.55 * k * k * k)
            shapeIn(clip((radius - r) / 0.01, 0.0, 1.0))
        }
        TransitionKind.STRIPES -> {
            // Eight bands, every other one sliding in from the top or the bottom, one after the other.
            val i = floor(X * 8.0)
            val odd = i - 2.0 * floor(i * 0.5)
            val e = ease(clip((T - i * 0.05) * 1.6, 0.0, 1.0))
            TransitionRecipe(
                outgoing = Part(),
                incoming = Part(v = iff(odd, Y - (1.0 - e), Y + (1.0 - e))),
            )
        }
        TransitionKind.CHECKER -> {
            val rows = H * 8.0 / W
            val i = floor(X * 8.0)
            val j = floor(Y * rows)
            val parity = (i + j) - 2.0 * floor((i + j) * 0.5)
            val fx = fract(X * 8.0) - 0.5
            val fy = fract(Y * rows) - 0.5
            val e = clip((T - 0.45 * parity) * 1.9, 0.0, 1.0)
            TransitionRecipe(outgoing = Part(), incoming = Part(alpha = lt(max(abs(fx), abs(fy)), 0.51 * e)))
        }
        TransitionKind.PAPER_TEAR -> {
            // The clip going out is torn away from the left along a ragged edge, its torn paper white.
            val jag = 0.025 * sin(Y * 37.0) + 0.015 * sin(Y * 91.0 + 1.3) + 0.01 * sin(Y * 173.0 + 2.1)
            val edge = -0.15 + 1.3 * ease(T) - jag
            val env = clip(bump() * 5.0, 0.0, 1.0)
            TransitionRecipe(
                outgoing = Part(alpha = gte(X, edge)),
                incoming = Part(),
                outgoingOnTop = true,
                overlays = listOf(
                    // The torn edge's shadow on the clip coming in.
                    Overlay(Rgb.BLACK, 0.5 * exp(-square((edge - X) / 0.04)) * lt(X, edge)),
                    Overlay(Rgb(0.97, 0.96, 0.92), 0.95 * env * between(X - edge, -(0.012 + 0.006 * sin(Y * 240.0)), Ex.ZERO)),
                ),
            )
        }
        TransitionKind.BRUSH -> {
            // Four strokes of paint, one after the other, alternately from the left and the right.
            val row = clip(floor((Y + 0.03 * sin(X * 9.0 + 1.7)) * 4.0), 0.0, 3.0)
            val odd = row - 2.0 * floor(row * 0.5)
            val reach = clip(T * 4.6 - row * 1.1, 0.0, 1.0) * 1.12
            val bristles = 0.025 * sin(Y * 130.0 + row * 2.0) + 0.015 * sin(Y * 57.0)
            val along = iff(odd, 1.0 - X, X)
            TransitionRecipe(outgoing = Part(), incoming = Part(alpha = lt(along + 0.05, reach - bristles)))
        }

        // -- Masks ------------------------------------------------------------
        TransitionKind.VERT_CLOSE -> softIn(1.0 + abs((X - 0.5) * 2.0) - P * 2.0)
        TransitionKind.HORZ_CLOSE -> softIn(1.0 + abs((Y - 0.5) * 2.0) - P * 2.0)
        TransitionKind.DIAGONAL_SPLIT -> {
            // The clip going out cut along its diagonal, the two halves sliding apart along it.
            val e = 0.75 * ease(T)
            val n = (X - 0.5) + (Y - 0.5)
            val upper = lt(n, Ex.ZERO)
            TransitionRecipe(
                outgoing = Part(
                    u = iff(upper, X + e, X - e),
                    v = iff(upper, Y + e, Y - e),
                    alpha = iff(upper, lt(n + 2.0 * e, Ex.ZERO), gte(n - 2.0 * e, Ex.ZERO)),
                ),
                incoming = placed(scale = 1.1 - 0.1 * ease(T)).part(),
                outgoingOnTop = true,
            )
        }
        TransitionKind.LUMA_FADE -> TransitionRecipe(
            // The clip coming in shows first where it is brightest.
            outgoing = Part(),
            incoming = Part(alpha = clip((T * 1.25 - (1.0 - LUMA)) / 0.25, 0.0, 1.0)),
        )

        // -- 3D ---------------------------------------------------------------
        TransitionKind.CUBE_LEFT -> cube(-1.0)
        TransitionKind.CUBE_RIGHT -> cube(1.0)
        TransitionKind.FLIP_H -> flip(horizontal = true)
        TransitionKind.FLIP_V -> flip(horizontal = false)
        TransitionKind.PAGE_TURN -> {
            // The clip going out is a page hinged at its left edge, turned toward the viewer until edge-on.
            val turn = ease(T) * (PI / 2.0 * 0.995)
            val page = card(-0.5 + 0.5 * cos(turn), -0.5 * sin(turn), cos(turn), -sin(turn), 2.2, horizontal = true)
            TransitionRecipe(
                outgoing = page.part(tintAmount = 0.3 * sin(turn)),
                incoming = Part(tintAmount = 0.4 * (1.0 - ease(T))),
                outgoingOnTop = true,
            )
        }

        // -- Motion -----------------------------------------------------------
        TransitionKind.PENDULUM_1 -> pendulum(0.5, 0.0, away = 1.6, from = -1.1)
        TransitionKind.PENDULUM_2 -> pendulum(0.0, 0.0, away = 1.6, from = -1.1)
        TransitionKind.BOUNCE -> TransitionRecipe(
            // The clip coming in drops from above and bounces on the bottom of the frame.
            outgoing = Part(),
            incoming = placed(dy = -(1.0 - bounceOut(T))).part(),
        )
        TransitionKind.REBOUND_1 -> rebound(1.0, 0.0)
        TransitionKind.REBOUND_2 -> rebound(0.0, 1.0)
        TransitionKind.SPRING -> {
            // The clip coming in springs out of the middle, past its size and back, and settles.
            val k = 1.0 - T
            val grown = 1.0 - k * k * k * cos(T * (3.5 * PI))
            val at = placed(scale = max(grown, num(0.02)))
            TransitionRecipe(outgoing = Part(), incoming = at.part(alpha = smoothstep(0.0, 0.08, T)))
        }

        // -- Punches (their fist is an effect laid over them, see TransitionKind.companionEffect) --
        TransitionKind.PUNCH -> {
            // The clip going out leans in as the fist comes; the clips change under the fist, which
            // fills the frame; the clip coming in recoils and shakes off the blow as the fist draws back.
            val switch = PUNCH_SWITCH
            val a = placed(scale = 1.0 + 0.05 * smoothstep(0.0, switch, T))
            val after = clip((T - 0.5) / 0.5, 0.0, 1.0)
            val hit = exp(-after * 6.0) * (1.0 - after) * gte(T, num(0.5))
            val b = placed(
                scale = 1.0 + 0.08 * (1.0 - smoothstep(switch, 0.85, T)),
                angle = 0.02 * hit * sin(after * 37.0),
                dx = 0.02 * hit * sin(after * 43.0),
                dy = 0.015 * hit * sin(after * 53.0 + 1.0),
            )
            TransitionRecipe(
                outgoing = a.part(),
                incoming = Part(b.u, b.v, wrap = Wrap.MIRROR, alpha = gte(T, num(switch))),
            )
        }
        TransitionKind.PUNCH_BACK -> {
            // The fist lands on the clip going out, which shakes as if struck; a flash hides the
            // change, and the clip coming in still trembles a little before it settles.
            val switch = PUNCH_BACK_SWITCH
            val landed = 0.32
            val since = clip((T - landed) / (1.0 - landed), 0.0, 1.0)
            val blow = exp(-since * 5.0) * gte(T, num(landed))
            val a = placed(
                scale = 1.0 + 0.05 * blow,
                angle = 0.025 * blow * sin(since * 43.0),
                dx = 0.035 * blow * sin(since * 57.0),
                dy = 0.03 * blow * sin(since * 67.0 + 1.0),
            )
            val after = clip((T - switch) / (1.0 - switch), 0.0, 1.0)
            val tremble = exp(-after * 6.0) * (1.0 - after) * gte(T, num(switch))
            val b = placed(
                scale = 1.0 + 0.03 * tremble,
                angle = 0.012 * tremble * sin(after * 41.0),
                dx = 0.015 * tremble * sin(after * 47.0),
                dy = 0.012 * tremble * sin(after * 59.0 + 1.0),
            )
            TransitionRecipe(
                outgoing = Part(a.u, a.v, wrap = Wrap.MIRROR),
                incoming = Part(b.u, b.v, wrap = Wrap.MIRROR, alpha = gte(T, num(switch))),
            )
        }

        // -- Social -----------------------------------------------------------
        TransitionKind.SWIPE_UP -> swipe(up = true)
        TransitionKind.SWIPE_DOWN -> swipe(up = false)
        TransitionKind.HEARTS -> {
            val aspect = W / H
            val size = max(3.6 * ease(T), num(0.001))
            val big = heart((X - 0.5) * aspect / size, (0.55 - Y) / size)
            val spots = listOf(Triple(0.2, 0.3, 0.0), Triple(0.8, 0.25, 0.12), Triple(0.5, 0.82, 0.24))
            val pops = spots.map { (hx, hy, delay) ->
                val k = clip((T - delay) / 0.45, 0.0, 1.0)
                val s = max(0.12 * sin(k * PI), num(0.0005))
                Overlay(Rgb(1.0, 0.35, 0.55), clip(-heart((X - hx) * aspect / s, (hy - Y) / s) * 6.0, 0.0, 1.0))
            }
            TransitionRecipe(
                outgoing = Part(),
                incoming = Part(alpha = clip(-big * 20.0, 0.0, 1.0)),
                overlays = pops,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Pieces
    // -----------------------------------------------------------------------

    /** Just short of a quarter turn: a card drawn edge-on has no picture to draw. */
    private const val EDGE_ON = PI / 2.0 * 0.995

    /** xfade's progress, 1 to 0. */
    private val P: Ex get() = 1.0 - T

    /** 0 at both ends, 1 halfway. */
    private fun bump(): Ex = sin(T * PI)

    private fun ease(e: Ex): Ex = smoothstep(0.0, 1.0, e)

    private fun square(e: Ex): Ex = e * e

    private fun ceil(e: Ex): Ex = -floor(-e)

    /** A soft round spot of light at ([cx], [cy]), [spread] wide (in heights), 1 in its middle. */
    private fun blob(cx: Ex, cy: Ex, spread: Double): Ex {
        val aspect = W / H
        return exp(-(square((X - cx) * aspect) + square(Y - cy)) / spread)
    }

    /**
     * The clip going out falls away swinging about ([pivotX], [pivotY]) (a turn
     * of [away] radians, faster and faster), and the one coming in swings in
     * from [from] radians about the same point, like a pendulum coming to rest.
     */
    private fun pendulum(pivotX: Double, pivotY: Double, away: Double, from: Double): TransitionRecipe {
        val fall = clip(T / 0.5, 0.0, 1.0)
        val swing = clip((T - 0.4) / 0.6, 0.0, 1.0)
        // Two and a half swings, smaller and smaller, still at the end.
        val angle = from * (1.0 - swing) * cos(swing * (2.5 * PI))
        return TransitionRecipe(
            outgoing = placed(angle = away * fall * fall, pivotX = pivotX, pivotY = pivotY).part(alpha = 1.0 - smoothstep(0.35, 0.5, T)),
            incoming = placed(angle = angle, pivotX = pivotX, pivotY = pivotY).part(alpha = smoothstep(0.38, 0.45, T)),
        )
    }

    /** The clip coming in pushes the one going out off along ([dx], [dy]), going past its place and back. */
    private fun rebound(dx: Double, dy: Double): TransitionRecipe {
        val e = backOut(T)
        val into = placed(dx = dx * (1.0 - e), dy = dy * (1.0 - e))
        // Coming in, only the picture shows; once past its place, its far edge is mirrored rather than leaving a gap.
        val along = (into.u - 0.5) * dx + (into.v - 0.5) * dy
        val shown = iff(gte(e, Ex.ONE), Ex.ONE, between(along, num(-0.5 - 1e-6), num(0.5 - 1e-6)))
        return TransitionRecipe(
            outgoing = placed(dx = -dx * e, dy = -dy * e).part(),
            incoming = Part(into.u, into.v, alpha = shown, wrap = Wrap.MIRROR),
        )
    }

    /** 0 to 1, going past 1 and back on the way (an easing "back out"). */
    private fun backOut(x: Ex): Ex {
        val c1 = 1.70158
        val m = x - 1.0
        return 1.0 + (c1 + 1.0) * m * m * m + c1 * m * m
    }

    /** 0 to 1, landing like a ball: three bounces, smaller and smaller (an easing "bounce out"). */
    private fun bounceOut(x: Ex): Ex {
        val n = 7.5625
        val d = 2.75
        fun arc(at: Double, top: Double) = n * square(x - at / d) + top
        return iff(
            lt(x, num(1.0 / d)),
            n * x * x,
            iff(lt(x, num(2.0 / d)), arc(1.5, 0.75), iff(lt(x, num(2.5 / d)), arc(2.25, 0.9375), arc(2.625, 0.984375))),
        )
    }

    /**
     * A picture moved as a whole: where it shows at each point ([u], [v]) and
     * where its corners go ([corners], see [Part.corners]), worked out alike.
     */
    private class Placed(val u: Ex, val v: Ex, val corners: List<Pair<Ex, Ex>>, val seen: Ex = Ex.ONE) {
        /** The picture so placed, as a part; [alpha] on top of where the picture is seen. */
        fun part(
            alpha: Ex = Ex.ONE,
            wrap: Wrap = Wrap.NONE,
            sampling: Sampling = Sampling.Single,
            tintAmount: Ex = Ex.ZERO,
        ): Part = Part(
            u = u,
            v = v,
            alpha = seen * alpha,
            wrap = wrap,
            sampling = sampling,
            tintAmount = tintAmount,
            corners = corners,
            cornersAlpha = alpha,
        )
    }

    /**
     * The picture enlarged [scale] times about the middle, turned [angle]
     * radians clockwise and moved by ([dx], [dy]) frames.
     */
    private fun placed(
        scale: Ex = Ex.ONE,
        angle: Ex = Ex.ZERO,
        dx: Ex = Ex.ZERO,
        dy: Ex = Ex.ZERO,
        pivotX: Double = 0.5,
        pivotY: Double = 0.5,
    ): Placed {
        val c = cos(angle)
        val s = sin(angle)
        // Where each point of the screen falls in the picture (enlarged and turned about the pivot).
        val cx = (X - pivotX) * W - dx * W
        val cy = (Y - pivotY) * H - dy * H
        val u = (cx * c + cy * s) / scale / W + pivotX
        val v = (cy * c - cx * s) / scale / H + pivotY
        // Where each corner of the picture goes on the screen.
        val corners = listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0).map { (fx, fy) ->
            val px = (fx - pivotX) * W
            val py = (fy - pivotY) * H
            (pivotX + dx + scale * (px * c - py * s) / W) to (pivotY + dy + scale * (px * s + py * c) / H)
        }
        return Placed(u, v, corners)
    }

    /** The clip coming in over the one going out by vf_xfade.c's soft edge: smoothstep(0, 1, [smooth]) opaque. */
    private fun softIn(smooth: Ex): TransitionRecipe =
        TransitionRecipe(outgoing = Part(), incoming = Part(alpha = smoothstep(0.0, 1.0, smooth)))

    /** The clip going out on top where [shown] is 1. */
    private fun cornerOver(shown: Ex): TransitionRecipe = TransitionRecipe(outgoing = Part(alpha = shown), incoming = Part(), outgoingOnTop = true)

    /** The clip coming in inside a shape that grows ([alpha] its inside), settling from a little larger. */
    private fun shapeIn(alpha: Ex): TransitionRecipe =
        TransitionRecipe(outgoing = Part(), incoming = placed(scale = 1.15 - 0.15 * ease(T)).part(alpha = alpha))

    /** The average of the picture placed at each of [copies] (the first where the part is). */
    private fun echo(copies: List<Placed>, alpha: Ex = Ex.ONE): Part {
        val first = copies.first()
        val taps = copies.map { (it.u - first.u) to (it.v - first.v) }
        return Part(
            u = first.u,
            v = first.v,
            alpha = alpha,
            wrap = Wrap.MIRROR,
            sampling = Sampling.Blur(taps, NativeBlur.Echo(copies.map { it.corners })),
        )
    }

    /** Both clips pushed along ([dx], [dy]) fast in the middle, blurred along the way. */
    private fun whip(dx: Double, dy: Double): TransitionRecipe {
        val e = ease(ease(T))
        val length = 0.12 * square(bump())
        val taps = (0 until 5).map { k -> (length * (k / 4.0 - 0.5) * dx) to (length * (k / 4.0 - 0.5) * dy) }
        val blur = Sampling.Blur(taps, NativeBlur.Streak(if (dy != 0.0) 90.0 else 0.0, 0.25 * length))
        return TransitionRecipe(
            outgoing = placed(dx = dx * e, dy = dy * e).part(sampling = blur),
            incoming = placed(dx = -dx * (1.0 - e), dy = -dy * (1.0 - e)).part(sampling = blur),
        )
    }

    /** A full turn of [turn] radians (clockwise when above 0), zooming in on the way; the clips cross halfway. */
    private fun spin(turn: Double): TransitionRecipe {
        val at = placed(scale = 1.0 + 0.6 * bump(), angle = turn * ease(T))
        return TransitionRecipe(
            outgoing = Part(at.u, at.v, wrap = Wrap.MIRROR),
            incoming = Part(at.u, at.v, wrap = Wrap.MIRROR, alpha = smoothstep(0.45, 0.55, T)),
        )
    }

    /** The clip going out cut in two along the middle of [along] (X or Y), the halves sliding apart. */
    private fun split(along: Ex, horizontal: Boolean): TransitionRecipe {
        val e = 0.5 * ease(T)
        val first = lt(along, num(0.5))
        val moved = iff(first, along + e, along - e)
        val alpha = iff(first, lt(along + e, num(0.5)), gte(along - e, num(0.5)))
        return TransitionRecipe(
            outgoing = if (horizontal) Part(u = moved, alpha = alpha) else Part(v = moved, alpha = alpha),
            incoming = placed(scale = 1.15 - 0.15 * ease(T)).part(),
            outgoingOnTop = true,
        )
    }

    /**
     * A picture as a card in 3D, seen from [distance] widths (heights when not
     * [horizontal]) away: its point at `l` across it (-0.5..0.5) is at
     * x0 + l kx across the screen and z0 + l kz deep, about the axis down the
     * screen when [horizontal] (across it otherwise). Seen where its front
     * faces the viewer.
     */
    private fun card(x0: Ex, z0: Ex, kx: Ex, kz: Ex, distance: Double, horizontal: Boolean): Placed {
        // The point on the screen, in widths (or heights) from the middle, along and across the turn.
        val along = if (horizontal) X - 0.5 else Y - 0.5
        val across = if (horizontal) (Y - 0.5) * H / W else (X - 0.5) * W / H
        val den = distance * kx - along * kz
        val l = (along * (distance + z0) - distance * x0) / max(den, num(1e-4))
        val z = z0 + l * kz
        val m = across * (distance + z) / distance
        val seen = gte(den, num(1e-4)) * gte(distance + z, num(1e-3))
        // The corners: half the other side of the frame away from the turn's axis.
        val half = if (horizontal) H / W * 0.5 else W / H * 0.5
        val corners = listOf(-1.0 to -1.0, 1.0 to -1.0, -1.0 to 1.0, 1.0 to 1.0).map { (fx, fy) ->
            val lc = if (horizontal) fx * 0.5 else fy * 0.5
            val mc = if (horizontal) fy * half else fx * half
            val zc = z0 + lc * kz
            val k = distance / (distance + zc)
            val a = (x0 + lc * kx) * k + 0.5
            val b = mc * k
            if (horizontal) a to (b * W / H + 0.5) else (b * H / W + 0.5) to a
        }
        return if (horizontal) {
            Placed(l + 0.5, m * W / H + 0.5, corners, seen)
        } else {
            Placed(m * H / W + 0.5, l + 0.5, corners, seen)
        }
    }

    /** Two faces of a cube turning a quarter turn: [side] -1 toward the left (the picture going left), 1 toward the right. */
    private fun cube(side: Double): TransitionRecipe {
        val e = ease(T)
        val back = 0.3 * bump()
        fun face(angle: Ex): Part {
            // The face, half a width in front of the cube's axis, turned about it (never quite edge-on).
            val a = clip(angle, -EDGE_ON, EDGE_ON)
            return card(0.5 * sin(a), 0.5 - 0.5 * cos(a) + back, cos(a), sin(a), 2.0, horizontal = true)
                .part(tintAmount = 0.5 * (1.0 - cos(a)))
        }
        return TransitionRecipe(
            outgoing = face(side * (PI / 2.0) * e),
            incoming = face(-side * (PI / 2.0) * (1.0 - e)),
        )
    }

    /** The picture turned over about its middle line, edge-on halfway where the clips change. */
    private fun flip(horizontal: Boolean): TransitionRecipe {
        val turn = ease(T) * PI
        val back = 0.25 * bump()
        fun side(angle: Ex, shown: Ex): Part {
            val a = clip(angle, -EDGE_ON, EDGE_ON)
            return card(Ex.ZERO, back, cos(a), sin(a), 2.5, horizontal).part(alpha = shown, tintAmount = 0.4 * abs(sin(a)))
        }
        return TransitionRecipe(
            outgoing = side(turn, lt(T, num(0.5))),
            incoming = side(turn - PI, gte(T, num(0.5))),
        )
    }

    /** A finger swiping the clip going out off (up or down), the clip coming in pushing in after it. */
    private fun swipe(up: Boolean): TransitionRecipe {
        val dir = if (up) -1.0 else 1.0
        val e = ease(clip((T - 0.25) / 0.75, 0.0, 1.0))
        val aspect = W / H
        val travel = ease(clip((T - 0.1) / 0.6, 0.0, 1.0))
        val fy = if (up) 0.85 - 0.6 * travel else 0.15 + 0.6 * travel
        val shown = clip(T / 0.1, 0.0, 1.0) * (1.0 - clip((T - 0.75) / 0.15, 0.0, 1.0))
        val r = hypot((X - 0.5) * aspect, Y - fy)
        // The trail behind the finger, longer as it moves faster.
        val trail = 0.2 * bump()
        val behind = if (up) Y - fy else fy - Y
        val trailAlpha = 0.4 * shown * between(behind, Ex.ZERO, trail) * (1.0 - behind / max(trail, num(0.001))) *
            clip((0.025 - abs((X - 0.5) * aspect)) / 0.008, 0.0, 1.0)
        return TransitionRecipe(
            outgoing = placed(dy = dir * e).part(),
            incoming = placed(dy = -dir * (1.0 - e)).part(),
            overlays = listOf(
                Overlay(Rgb.WHITE, trailAlpha),
                Overlay(Rgb.BLACK, 0.3 * shown * clip((0.065 - r) / 0.01, 0.0, 1.0)),
                Overlay(Rgb.WHITE, 0.92 * shown * clip((0.05 - r) / 0.006, 0.0, 1.0)),
            ),
        )
    }

    /**
     * How far outside a heart (two round lobes over a point) the point ([x],
     * [y]) is, y up and the heart about 2 across: below 0 inside.
     */
    private fun heart(x: Ex, y: Ex): Ex {
        val left = hypot(x + 0.42, y - 0.3) - 0.5
        val right = hypot(x - 0.42, y - 0.3) - 0.5
        val point = (abs(x) / 0.85 + abs(y + 0.35) / 0.65 - 1.0) * 0.6
        return min(min(left, right), point)
    }
}
