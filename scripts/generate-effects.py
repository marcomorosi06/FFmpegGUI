#!/usr/bin/env python3
"""
Generates the overlay effects the video editor ships with (app/src/main/assets/effects/).

Every effect is drawn here, by this code, frame by frame, as light on black:
the editor lays it over the video with the "screen" blend, so black disappears
and the light adds up. No transparency is needed, so every effect is a plain
H.264 MP4 that every phone decodes and FFmpeg reads.

Looping effects loop seamlessly: each particle has a phase within the loop
and its age is taken modulo the loop's length, so the last frame leads into
the first. Transitions (the fairy, an explosion, a flash) play once over the
cut between two clips: index.json says where the cut falls ("cut", in seconds
from the start), under the effect's brightest moment.

Everything is deterministic (fixed seeds): running this again makes the same
frames. The encoder's output is committed with the app, so the build does not
depend on this script; it is here so that anyone can see and remake them.

Usage: python3 scripts/generate-effects.py [--only name,name] [--out DIR] [--crf N] [--index-only]
Needs: Python 3, numpy, scipy, and ffmpeg with libx264 on the PATH.
"""

import argparse
import json
import math
import os
import subprocess
import sys

import numpy as np
from scipy.ndimage import gaussian_filter

W, H = 1280, 720
FPS = 30


# ---------------------------------------------------------------------------
# Drawing
# ---------------------------------------------------------------------------

class Canvas:
    """A float RGB buffer of light, in several layers blurred by different amounts."""

    def __init__(self, sigmas):
        self.layers = {s: np.zeros((H, W, 3), np.float32) for s in sigmas}
        # Sprites (shapes with an area) are blurred without being brightened back.
        self.sprites = {}

    def splat(self, sigma, x, y, rgb, weight):
        """Adds points at x, y (arrays) of colour rgb (N x 3) and weight (N), bilinear."""
        buf = self.layers[sigma]
        x = np.asarray(x, np.float32)
        y = np.asarray(y, np.float32)
        ok = (x >= 0) & (x < W - 1) & (y >= 0) & (y < H - 1) & (weight > 0)
        if not ok.any():
            return
        x, y, rgb, weight = x[ok], y[ok], rgb[ok], weight[ok]
        x0 = np.floor(x).astype(np.int64)
        y0 = np.floor(y).astype(np.int64)
        fx = (x - x0)[:, None]
        fy = (y - y0)[:, None]
        c = rgb * weight[:, None]
        flat = buf.reshape(-1, 3)
        for dx, dy, f in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy), (1, 1, fx * fy)):
            np.add.at(flat, (y0 + dy) * W + (x0 + dx), c * f)

    def stamp(self, sigma, x, y, mask, rgb, weight):
        """Adds the 2D [mask] (centred) at x, y in rgb times weight (one sprite)."""
        buf = self.sprites.setdefault(sigma, np.zeros((H, W, 3), np.float32))
        mh, mw = mask.shape
        x0 = int(round(x)) - mw // 2
        y0 = int(round(y)) - mh // 2
        xa, ya = max(0, x0), max(0, y0)
        xb, yb = min(W, x0 + mw), min(H, y0 + mh)
        if xa >= xb or ya >= yb:
            return
        m = mask[ya - y0:yb - y0, xa - x0:xb - x0]
        buf[ya:yb, xa:xb] += m[:, :, None] * (np.asarray(rgb, np.float32) * weight)[None, None, :]

    def render(self, exposure=1.0):
        out = np.zeros((H, W, 3), np.float32)
        for s, buf in self.layers.items():
            if s > 0:
                # Blurred so that a point keeps its brightness at its centre: weights are peaks.
                out += gaussian_filter(buf, (s, s, 0)) * (2 * math.pi * s * s)
            else:
                out += buf
        for s, buf in self.sprites.items():
            out += gaussian_filter(buf, (s, s, 0)) if s > 0 else buf
        # Filmic: highlights roll off to white instead of clipping in a colour.
        return 1.0 - np.exp(-out * exposure)


def to8(img):
    return (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)


def ramp(u, stops):
    """Colour at u (array 0..1) along [(pos, (r, g, b)), ...]."""
    u = np.clip(u, 0, 1)
    pos = np.array([p for p, _ in stops], np.float32)
    cols = np.array([c for _, c in stops], np.float32)
    out = np.empty((len(u), 3), np.float32)
    for k in range(3):
        out[:, k] = np.interp(u, pos, cols[:, k])
    return out


def disc(radius, soft=1.5):
    r = int(math.ceil(radius + soft * 2))
    yy, xx = np.mgrid[-r:r + 1, -r:r + 1]
    d = np.sqrt(xx * xx + yy * yy)
    return np.clip((radius - d) / soft + 0.5, 0, 1).astype(np.float32)


def heart(size):
    r = int(size)
    yy, xx = np.mgrid[-r:r + 1, -r:r + 1].astype(np.float32)
    x = xx / size * 1.3
    y = -yy / size * 1.3 + 0.25
    f = (x * x + y * y - 1) ** 3 - x * x * y ** 3
    return np.clip(-f * 6 + 0.5, 0, 1).astype(np.float32)


# ---------------------------------------------------------------------------
# Effects: each a function of the frame index giving an RGB float image.
# ---------------------------------------------------------------------------

def looped(rng, n, loop, life_min, life_max):
    """n particles: their phase within the loop and their life."""
    phase = rng.uniform(0, loop, n).astype(np.float32)
    life = rng.uniform(life_min, life_max, n).astype(np.float32)
    return phase, life


def age_at(t, phase, loop):
    return np.mod(t - phase, loop)


def trail(canvas, sigma, pos_fn, age, life, alive, rgb_fn, weight_fn, steps, span):
    """Draws particles at their position and a few earlier ones (a streak), fading back."""
    for k in range(steps):
        a = age - span * k / max(1, steps - 1)
        ok = alive & (a >= 0)
        if not ok.any():
            continue
        x, y = pos_fn(a)
        fade = (1 - k / steps) ** 1.5
        canvas.splat(sigma, x[ok], y[ok], rgb_fn(a)[ok], weight_fn(a)[ok] * fade / steps * 2)


def sparks(loop=4.0, seed=1):
    """Hot sparks thrown up from the bottom, arcing and falling, with streaks."""
    rng = np.random.default_rng(seed)
    n = 700
    phase, life = looped(rng, n, loop, 1.0, 2.4)
    # From a few hot spots along the bottom, as off a grinder or a fire.
    spots = np.array([0.22, 0.5, 0.78], np.float32) * W
    x0 = (spots[rng.integers(0, 3, n)] + rng.normal(0, 40, n)).astype(np.float32)
    y0 = np.full(n, H + 10, np.float32)
    vx = rng.normal(0, 300, n).astype(np.float32)
    vy = -rng.uniform(700, 1350, n).astype(np.float32)
    g = 1100.0
    drag = rng.uniform(0.3, 0.9, n).astype(np.float32)
    hot = rng.uniform(0, 1, n).astype(np.float32)
    flick = rng.uniform(12, 35, n).astype(np.float32)

    def pos(a):
        k = (1 - np.exp(-drag * a)) / drag
        return x0 + vx * k, y0 + vy * k + 0.5 * g * a * a

    def rgb(a):
        u = a / life
        return ramp(u * 0.8 + (1 - hot) * 0.3, [(0, (1, 0.97, 0.85)), (0.3, (1, 0.78, 0.3)), (0.7, (1, 0.42, 0.08)), (1, (0.6, 0.1, 0.02))])

    def weight(a):
        u = a / life
        return np.clip(1 - u, 0, 1) ** 1.1 * (0.75 + 0.25 * np.sin(a * flick))

    def frame(i):
        t = i / FPS
        c = Canvas([0, 0.8, 5, 16])
        age = age_at(t, phase, loop)
        alive = age < life
        # A continuous streak: many points along the last tenth of a second.
        steps = 44
        for k in range(steps):
            a = age - 0.09 * k / (steps - 1)
            ok = alive & (a >= 0)
            x, y = pos(a)
            fade = (1 - k / steps) ** 1.2
            w = weight(a)[ok] * fade
            col = rgb(a)[ok]
            c.splat(0, x[ok], y[ok], col, w * 0.4)
            c.splat(0.8, x[ok], y[ok], col, w * 0.25)
        x, y = pos(age)
        c.splat(5, x[alive], y[alive], rgb(age)[alive], weight(age)[alive] * 0.1)
        c.splat(16, x[alive], y[alive], rgb(age)[alive], weight(age)[alive] * 0.015)
        # The hot spots glow where the sparks come from, flickering.
        glow = 0.5 + 0.12 * np.sin(t * 2 * math.pi * np.array([3, 4, 5]) / loop * 4)
        c.splat(16, spots, np.full(3, H - 2, np.float32), np.tile(np.array([1, 0.55, 0.15], np.float32), (3, 1)), glow * 0.6)
        return c.render(2.0)

    return frame, int(loop * FPS), True


def periodic_noise(rng, shape, scale):
    """Smooth random values on a grid that wraps round on every axis (white noise
    filtered in the frequency domain), scaled to about -1..1. [scale] is the
    size of its features in cells."""
    white = rng.standard_normal(shape)
    freqs = np.meshgrid(*[np.fft.fftfreq(n) for n in shape], indexing="ij")
    scales = scale if isinstance(scale, (tuple, list)) else [scale] * len(shape)
    f2 = sum((f * math.pi * sc) ** 2 for f, sc in zip(freqs, scales))
    spectrum = np.fft.fftn(white) * np.exp(-f2 / 2)
    out = np.real(np.fft.ifftn(spectrum))
    return (out / (np.abs(out).max() + 1e-9)).astype(np.float32)


def flames(loop=6.0, seed=2):
    """A wall of flames along the bottom, licking upwards: rising turbulence
    (noise that wraps round in time, so it loops) shaped into tongues of fire."""
    from scipy.ndimage import map_coordinates, zoom
    rng = np.random.default_rng(seed)
    frames = int(loop * FPS)
    lw, lh = 320, 180
    # Noise volumes: y (wraps as it scrolls up), x, time (wraps over the loop).
    # Features taller than wide: tongues, not blobs.
    big = periodic_noise(rng, (64, 128, frames), (6, 3.2, 16))
    mid = periodic_noise(rng, (96, 192, frames), (3.5, 1.8, 9))
    fine = periodic_noise(rng, (128, 256, frames), (1.8, 1.2, 5))
    yy, xx = np.mgrid[0:lh, 0:lw].astype(np.float32)
    v = yy / lh  # 1 at the bottom, 0 at the top

    def sample(vol, t_index, ys, xs, scroll):
        Y, X, T = vol.shape
        # One whole scroll of the volume per loop: back where it started.
        y = (ys / lh * Y + scroll * Y) % Y
        x = xs / lw * X
        tt = np.full_like(y, t_index % T)
        return map_coordinates(vol, [y, x, tt], order=1, mode="grid-wrap")

    # How tall the fire is along the bottom (slowly changing, looping).
    height = periodic_noise(rng, (96, frames), (8, 30))

    def frame(i):
        u = i / frames
        # Whole scrolls per loop (so it loops), each layer rising faster than the last.
        n1 = sample(big, i, yy, xx, u * 2)
        n2 = sample(mid, i, yy, xx, u * 3)
        n3 = sample(fine, i, yy, xx, u * 5)
        # The tongues: the turbulence bends the picture sideways and upwards.
        warp = n1 * 0.6 + n2 * 0.3
        hx = map_coordinates(height, [xx[0] / lw * height.shape[0], np.full(lw, i % frames, np.float32)], order=1, mode="grid-wrap")
        tall = 0.55 + 0.25 * hx[None, :]
        heat = (1 - (1 - v + warp * 0.22) / tall) + n2 * 0.3 + n3 * 0.12
        # Steeper at the edges: tongues with an outline, not a haze.
        heat = np.clip(heat, 0, 1.4)
        heat = np.where(heat > 0, heat ** 1.6, 0) * 1.25
        col = ramp(np.clip(heat / 1.25, 0, 1).ravel(), [
            (0.0, (0, 0, 0)), (0.18, (0.25, 0.02, 0.0)), (0.38, (0.85, 0.18, 0.02)),
            (0.6, (1.0, 0.48, 0.06)), (0.8, (1.0, 0.78, 0.3)), (1.0, (1.0, 0.97, 0.85)),
        ]).reshape(lh, lw, 3)
        img = zoom(col, (H / lh, W / lw, 1), order=1)
        img = gaussian_filter(img, (1.2, 1.2, 0)) * 1.15
        return np.clip(img, 0, 1)

    return frame, frames, True


def star_mask(radius, turn, points=5, inner=0.45):
    """A star of [points] points, [radius] px, turned by [turn] radians, soft-edged."""
    r = int(math.ceil(radius)) + 2
    yy, xx = np.mgrid[-r:r + 1, -r:r + 1].astype(np.float32)
    ang = np.arctan2(yy, xx) - turn
    d = np.sqrt(xx * xx + yy * yy)
    # The star's edge at each angle: between the tips and the hollows.
    seg = 2 * math.pi / points
    f = np.abs(np.mod(ang + math.pi / 2, seg) - seg / 2) / (seg / 2)
    edge = radius * (inner + (1 - inner) * (1 - f) ** 1.6)
    return np.clip((edge - d) / 1.2 + 0.5, 0, 1).astype(np.float32)


def fairy(seed=3):
    """A fairy's magic wand: it comes in, waves for a second, strikes the middle,
    and a burst of twinkling sparkles bursts out of it and rains down."""
    rng = np.random.default_rng(seed)
    duration = 3.2
    # When the wand strikes: where a transition cuts from one clip to the next, under the flash.
    hit = 1.15
    # The hand holds the wand off the bottom right corner; the wand turns about it.
    pivot = np.array([W * 0.86, H * 1.18], np.float32)
    length = math.hypot(W * 0.5 - pivot[0], H * 0.5 - pivot[1])
    strike = math.atan2(H * 0.5 - pivot[1], W * 0.5 - pivot[0])

    def wand_angle(t):
        if t < hit - 0.25:
            # Waving: swings either side of a raised position, a little faster each time.
            raised = strike + 0.38
            return raised + 0.22 * math.sin(t * 2 * math.pi * (1.6 + t * 0.9))
        if t < hit:
            # The strike: a quick swing down onto the middle, easing in.
            u = (t - (hit - 0.25)) / 0.25
            start = strike + 0.38 + 0.22 * math.sin((hit - 0.25) * 2 * math.pi * (1.6 + (hit - 0.25) * 0.9))
            return start + (strike - start) * (u * u)
        # A little bounce back after the hit.
        u = t - hit
        return strike + 0.06 * math.sin(u * 18) * math.exp(-u * 6)

    def wand_reach(t):
        # In from below at the start, out again at the end.
        come = min(1.0, t / 0.35)
        go = min(1.0, max(0.0, (duration - 1.3 - t) / 0.45))
        ease = lambda v: 1 - (1 - v) ** 3
        return 0.25 + 0.75 * ease(come) * ease(go)

    def tip_at(t):
        ang = wand_angle(t)
        reach = wand_reach(t)
        return pivot[0] + math.cos(ang) * length * reach, pivot[1] + math.sin(ang) * length * reach

    # The burst: out of the middle at the hit, slowing, then falling, twinkling.
    n = 2400
    speed = (rng.uniform(0.15, 1.0, n) ** 0.6 * 900).astype(np.float32)
    ang = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    vx = np.cos(ang) * speed
    vy = np.sin(ang) * speed * 0.85 - 120
    drag = rng.uniform(2.4, 3.8, n).astype(np.float32)
    g = rng.uniform(320, 560, n).astype(np.float32)
    life = rng.uniform(1.3, 2.05, n).astype(np.float32)
    delay = rng.uniform(0, 0.12, n).astype(np.float32)
    tw_f = rng.uniform(7, 18, n).astype(np.float32)
    tw_p = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    hue = rng.uniform(0, 1, n).astype(np.float32)
    palette = [(0, (1.0, 0.86, 0.45)), (0.35, (1.0, 0.68, 0.88)), (0.65, (0.68, 0.86, 1.0)), (1, (1.0, 0.93, 0.62))]
    colours = ramp(hue, palette)

    # The glitter the tip leaves while it waves.
    m = 900
    born = rng.uniform(0, hit, m).astype(np.float32)
    m_life = rng.uniform(0.5, 1.1, m).astype(np.float32)
    m_v = rng.normal(0, 40, (m, 2)).astype(np.float32)
    m_hue = ramp(rng.uniform(0, 1, m).astype(np.float32), palette)
    m_from = np.array([tip_at(float(b)) for b in born], np.float32)

    def frame(i):
        t = i / FPS
        c = Canvas([0, 0.9, 3, 10, 30, 80])
        # Glitter off the waving tip, falling gently.
        a = t - born
        ok = (a >= 0) & (a < m_life)
        if ok.any():
            u = a[ok] / m_life[ok]
            x = m_from[ok, 0] + m_v[ok, 0] * a[ok]
            y = m_from[ok, 1] + m_v[ok, 1] * a[ok] + 0.5 * 160 * a[ok] ** 2
            w = (1 - u) ** 1.5 * (0.4 + 0.6 * np.maximum(0, np.sin(a[ok] * 14 + born[ok] * 50)) ** 2)
            c.splat(0, x, y, m_hue[ok], w * 0.8)
            c.splat(3, x, y, m_hue[ok], w * 0.06)
        # The burst.
        a = t - hit - delay
        ok = (a >= 0) & (a < life)
        if ok.any():
            aa = a[ok]
            u = aa / life[ok]
            k = (1 - np.exp(-drag[ok] * aa)) / drag[ok]
            x = W * 0.5 + vx[ok] * k
            y = H * 0.5 + vy[ok] * k + 0.5 * g[ok] * aa * aa
            twinkle = np.maximum(0, np.sin(aa * tw_f[ok] + tw_p[ok])) ** 3
            w = (1 - u) ** 1.2 * (0.35 + 1.4 * twinkle) * (1 + 2.5 * np.exp(-aa * 6))
            col = colours[ok]
            c.splat(0, x, y, col, w * 0.7)
            c.splat(0.9, x, y, col, w * 0.4)
            c.splat(3, x, y, col, w * 0.05)
            # The brightest glints as little four-pointed stars.
            big = twinkle > 0.9
            for xi, yi, ci, wi in zip(x[big], y[big], col[big], w[big]):
                for d in range(2, 8):
                    f = (1 - d / 8) * 0.4 * wi
                    c.splat(0, np.array([xi + d, xi - d, xi, xi]), np.array([yi, yi, yi + d, yi - d]), np.tile(ci, (4, 1)), np.full(4, f))
        # The flash and a ring spreading where the wand struck.
        a = t - hit
        if 0 <= a < 0.9:
            centre = (np.array([W * 0.5]), np.array([H * 0.5]))
            c.splat(30, *centre, np.array([[1.0, 0.9, 0.95]], np.float32), np.array([2.6 * math.exp(-a * 7)]))
            c.splat(80, *centre, np.array([[0.95, 0.85, 1.0]], np.float32), np.array([2.0 * math.exp(-a * 4)]))
            # The whole frame lit for an instant: the cut is not seen.
            c.layers[0] += np.float32(1.6 * math.exp(-a * 9))
            ring_r = 30 + a * 520
            th = np.linspace(0, 2 * math.pi, 360, endpoint=False).astype(np.float32)
            c.splat(0.9, W * 0.5 + np.cos(th) * ring_r, H * 0.5 + np.sin(th) * ring_r * 0.92,
                    np.tile(np.array([1.0, 0.9, 0.75], np.float32), (360, 1)), np.full(360, 0.5 * (1 - a / 0.9) ** 2, np.float32))
        # The wand: a slender stick of light, a star at its tip, turning as it waves.
        reach = wand_reach(t)
        if reach > 0.26:
            tx, ty = tip_at(t)
            ang = wand_angle(t)
            # From where the hand holds it to the star, in many points.
            seg = np.linspace(0.12, 0.93, 260, dtype=np.float32)
            hx = pivot[0] + (tx - pivot[0]) * seg
            hy = pivot[1] + (ty - pivot[1]) * seg
            stick = np.tile(np.array([0.95, 0.88, 0.8], np.float32), (len(seg), 1))
            c.splat(0.9, hx, hy, stick, np.full(len(seg), 0.55, np.float32))
            c.splat(3, hx, hy, np.tile(np.array([1.0, 0.75, 0.9], np.float32), (len(seg), 1)), np.full(len(seg), 0.03, np.float32))
            glow = 1.0 + (2.0 * math.exp(-max(0.0, t - hit) * 5) if t >= hit - 0.05 else 0.25 * math.sin(t * 20))
            c.stamp(0, tx, ty, star_mask(17, ang + t * 2.5), (1.0, 0.92, 0.6), 0.95 * glow)
            c.splat(3, np.array([tx]), np.array([ty]), np.array([[1.0, 0.9, 0.6]], np.float32), np.array([0.5 * glow]))
            c.splat(10, np.array([tx]), np.array([ty]), np.array([[1.0, 0.8, 0.95]], np.float32), np.array([0.35 * glow]))
            c.splat(30, np.array([tx]), np.array([ty]), np.array([[0.9, 0.8, 1.0]], np.float32), np.array([0.18 * glow]))
        return c.render(1.5)

    return frame, int(duration * FPS), False


def wrap_fall(rng, n, loop, margin, k_choices):
    """Start heights and speeds of things falling through the frame that are back
    where they started after [loop] seconds: each falls a whole number of
    frame heights (plus margin) per loop."""
    k = rng.choice(k_choices, n).astype(np.float32)
    speed = k * (H + 2 * margin) / loop
    y0 = rng.uniform(0, H + 2 * margin, n).astype(np.float32)
    return y0, speed


def fall_y(y0, speed, t, margin):
    return np.mod(y0 + speed * t, H + 2 * margin) - margin


def harmonic(rng, n, loop, max_k):
    """Frequencies (radians per second) that fit a whole number of times in the loop."""
    return (rng.integers(1, max_k + 1, n) * 2 * math.pi / loop).astype(np.float32)


def fireworks(loop=6.0, seed=4):
    """Rockets rising from below and bursting into coloured stars that droop and crackle."""
    rng = np.random.default_rng(seed)
    bursts = 7
    b_time = (np.arange(bursts) / bursts * loop + rng.uniform(0, loop / bursts * 0.6, bursts)).astype(np.float32)
    b_x = rng.uniform(W * 0.15, W * 0.85, bursts).astype(np.float32)
    b_y = rng.uniform(H * 0.18, H * 0.48, bursts).astype(np.float32)
    colours = [(1.0, 0.35, 0.3), (1.0, 0.8, 0.3), (0.4, 0.8, 1.0), (0.6, 1.0, 0.5), (1.0, 0.5, 0.9), (0.85, 0.7, 1.0), (1.0, 0.95, 0.85)]
    b_col = np.array([colours[i % len(colours)] for i in rng.permutation(bursts)], np.float32)
    per = 420
    rise = 0.7
    # A sphere of stars seen from the side: its projection fills the disc, densest at the rim.
    sp = (np.sqrt(1 - rng.uniform(-1, 1, (bursts, per)) ** 2) * rng.uniform(0.85, 1.0, (bursts, per)) * 430).astype(np.float32)
    an = rng.uniform(0, 2 * math.pi, (bursts, per)).astype(np.float32)
    life = rng.uniform(1.5, 2.3, (bursts, per)).astype(np.float32)
    crackle = rng.uniform(0, 1, (bursts, per)).astype(np.float32) > 0.7
    tw = rng.uniform(15, 30, (bursts, per)).astype(np.float32)

    def frame(i):
        t = i / FPS
        c = Canvas([0, 1.0, 4, 14, 50])
        for b in range(bursts):
            # This burst's time: its rocket rises [rise] s before it, all within the loop.
            a = np.mod(t - b_time[b], loop) - rise
            col = b_col[b]
            if -rise <= a < 0:
                u = (a + rise) / rise
                ry = H + 20 + (b_y[b] - H - 20) * (1 - (1 - u) ** 2)
                for k in range(16):
                    uu = max(0.0, u - k * 0.012)
                    yy = H + 20 + (b_y[b] - H - 20) * (1 - (1 - uu) ** 2)
                    c.splat(0, np.array([b_x[b] + math.sin(uu * 9) * 3]), np.array([yy]), np.array([[1, 0.85, 0.6]], np.float32), np.array([0.5 * (1 - k / 16)]))
            if a >= 0:
                aa = a
                ok = aa < life[b]
                if not ok.any():
                    continue
                u = aa / life[b][ok]
                steps = 10
                for k in range(steps):
                    ak = max(0.0, aa - 0.12 * k / steps)
                    kk = (1 - math.exp(-2.2 * ak)) / 2.2
                    x = b_x[b] + np.cos(an[b][ok]) * sp[b][ok] * kk
                    y = b_y[b] + np.sin(an[b][ok]) * sp[b][ok] * kk + 0.5 * 90 * ak * ak
                    w = (1 - u) ** 1.1 * (1 - k / steps) * 1.1
                    sparkle = np.where(crackle[b][ok] & (u > 0.55), np.maximum(0, np.sin(aa * tw[b][ok])) ** 4 * 2, 1.0)
                    cc = np.tile(col, (ok.sum(), 1)) * 0.75 + 0.25 * np.where(u[:, None] < 0.15, 1.0, 0.0)
                    c.splat(0, x, y, cc, w * sparkle)
                    if k == 0:
                        c.splat(4, x, y, cc, w * 0.08)
                        c.splat(1.0, x, y, cc, w * 0.6)
                # The burst's flash.
                if aa < 0.35:
                    c.splat(50, np.array([b_x[b]]), np.array([b_y[b]]), col[None], np.array([0.5 * (1 - aa / 0.35)]))
                    c.splat(14, np.array([b_x[b]]), np.array([b_y[b]]), np.array([[1, 1, 0.95]], np.float32), np.array([0.8 * (1 - aa / 0.35)]))
        return c.render(1.7)

    return frame, int(loop * FPS), True


def smoke(loop=6.0, seed=5):
    """Soft light smoke drifting up from the bottom, curling."""
    from scipy.ndimage import map_coordinates, zoom
    rng = np.random.default_rng(seed)
    frames = int(loop * FPS)
    lw, lh = 256, 144
    big = periodic_noise(rng, (48, 80, frames), (7, 6, 24))
    mid = periodic_noise(rng, (80, 128, frames), (4, 3.5, 14))
    yy, xx = np.mgrid[0:lh, 0:lw].astype(np.float32)
    v = yy / lh

    def sample(vol, i, scroll):
        Y, X, T = vol.shape
        y = (yy / lh * Y + scroll * Y) % Y
        return map_coordinates(vol, [y, xx / lw * X, np.full_like(y, i % T)], order=1, mode="grid-wrap")

    def frame(i):
        u = i / frames
        n1 = sample(big, i, u)
        n2 = sample(mid, i, u * 2)
        dens = np.clip((v - 0.15) * 1.1 + n1 * 0.55 + n2 * 0.3 - 0.1, 0, 1) ** 1.4
        img = np.repeat(dens[:, :, None], 3, 2) * np.array([0.45, 0.45, 0.48], np.float32)
        img = zoom(img, (H / lh, W / lw, 1), order=1)
        return np.clip(gaussian_filter(img, (6, 6, 0)), 0, 1)

    return frame, frames, True


def snow(loop=6.0, seed=6):
    """Snow falling in three depths: small and slow far away, big and soft near."""
    rng = np.random.default_rng(seed)
    layers = []
    for n, k, size, sigma, bright in ((900, (1,), 1.0, 0, 0.55), (350, (1, 2), 2.0, 1.2, 0.8), (70, (2, 3), 5.0, 3.5, 0.9)):
        y0, speed = wrap_fall(rng, n, loop, 30, k)
        x0 = rng.uniform(0, W, n).astype(np.float32)
        sw_f = harmonic(rng, n, loop, 3)
        sw_p = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
        sw_a = rng.uniform(8, 30, n).astype(np.float32) * size / 2
        layers.append((x0, y0, speed, sw_f, sw_p, sw_a, sigma, bright))

    def frame(i):
        t = i / FPS
        c = Canvas([0, 1.2, 3.5])
        for x0, y0, speed, sw_f, sw_p, sw_a, sigma, bright in layers:
            x = x0 + np.sin(t * sw_f + sw_p) * sw_a
            y = fall_y(y0, speed, t, 30)
            col = np.tile(np.array([0.95, 0.97, 1.0], np.float32), (len(x), 1))
            c.splat(sigma, x, y, col, np.full(len(x), bright, np.float32))
        return c.render(1.3)

    return frame, int(loop * FPS), True


def rain(loop=2.0, seed=7):
    """Rain: thin slanted streaks falling fast, near ones longer and brighter."""
    rng = np.random.default_rng(seed)
    n = 900
    y0, speed = wrap_fall(rng, n, loop, 80, (2, 3, 4))
    x0 = rng.uniform(-100, W + 100, n).astype(np.float32)
    near = rng.uniform(0, 1, n).astype(np.float32) ** 2
    slant = 0.16

    def frame(i):
        t = i / FPS
        c = Canvas([0, 0.7])
        y = fall_y(y0, speed, t, 80)
        x = x0 + (y + 80) * slant
        length = 25 + near * 60
        for k in range(14):
            f = k / 13
            yk = y - length * f
            xk = x - length * f * slant
            w = (0.35 + near * 0.8) * (1 - f) ** 0.5
            c.splat(0.7 if k % 2 else 0, xk, yk, np.tile(np.array([0.8, 0.88, 1.0], np.float32), (n, 1)), w)
        return c.render(1.0)

    return frame, int(loop * FPS), True


def confetti(loop=5.0, seed=8):
    """Confetti of many colours fluttering down, turning as they fall."""
    rng = np.random.default_rng(seed)
    n = 420
    y0, speed = wrap_fall(rng, n, loop, 40, (1, 2))
    x0 = rng.uniform(0, W, n).astype(np.float32)
    sw_f = harmonic(rng, n, loop, 4)
    sw_p = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    sw_a = rng.uniform(15, 45, n).astype(np.float32)
    spin = harmonic(rng, n, loop, 9)
    tilt = rng.uniform(0, math.pi, n).astype(np.float32)
    palette = np.array([(1, 0.25, 0.35), (1, 0.75, 0.15), (0.2, 0.75, 1), (0.4, 0.95, 0.45), (0.85, 0.4, 1), (1, 0.5, 0.75), (1, 1, 1)], np.float32)
    col = palette[rng.integers(0, len(palette), n)]
    size = rng.uniform(10, 18, n).astype(np.float32)

    def frame(i):
        t = i / FPS
        c = Canvas([0, 0.8])
        x = x0 + np.sin(t * sw_f + sw_p) * sw_a
        y = fall_y(y0, speed, t, 40)
        turn = np.cos(t * spin + sw_p)
        ang = tilt + t * spin * 0.3
        face = 0.35 + 0.65 * np.abs(turn)
        # Each piece as a short bar, as wide as it shows its face.
        for k in range(-3, 4):
            f = k / 3
            dx = np.cos(ang) * size * 0.5 * f
            dy = np.sin(ang) * size * 0.5 * f * np.abs(turn)
            c.splat(0, x + dx, y + dy, col, face * 0.9)
            c.splat(0.8, x + dx, y + dy, col, face * 0.6)
        return c.render(1.6)

    return frame, int(loop * FPS), True


def dust(loop=6.0, seed=9):
    """Specks of dust floating in a slanted beam of light, catching it as they turn."""
    rng = np.random.default_rng(seed)
    n = 380
    x0 = rng.uniform(0, W, n).astype(np.float32)
    y0 = rng.uniform(0, H, n).astype(np.float32)
    fx = harmonic(rng, n, loop, 2)
    fy = harmonic(rng, n, loop, 2)
    px = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    py = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    ax = rng.uniform(10, 40, n).astype(np.float32)
    ay = rng.uniform(10, 40, n).astype(np.float32)
    tw = harmonic(rng, n, loop, 7)
    depth = rng.uniform(0, 1, n).astype(np.float32)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    # A beam from the top left, soft-edged.
    across = (xx - yy * 0.55 - W * 0.18) / (W * 0.22)
    beam = (np.exp(-across * across * 2) * (1 - yy / H * 0.6)).astype(np.float32)

    def frame(i):
        t = i / FPS
        c = Canvas([0, 1.0, 3.0])
        x = x0 + np.sin(t * fx + px) * ax
        y = y0 + np.sin(t * fy + py) * ay
        inb = beam[np.clip(y.astype(int), 0, H - 1), np.clip(x.astype(int), 0, W - 1)]
        glint = 0.25 + 0.75 * np.maximum(0, np.sin(t * tw + px)) ** 2
        w = (0.3 + inb * 2.2) * glint
        col = np.tile(np.array([1.0, 0.94, 0.8], np.float32), (n, 1))
        sig = np.where(depth > 0.85, 3.0, np.where(depth > 0.5, 1.0, 0))
        for s_ in (0, 1.0, 3.0):
            m = sig == s_
            c.splat(s_, x[m], y[m], col[m], w[m] * (0.7 if s_ else 0.8))
        img = c.render(1.4)
        flicker = 0.92 + 0.08 * math.sin(t * 2 * math.pi / loop * 2)
        light = beam[:, :, None] * np.array([0.22, 0.2, 0.15], np.float32) * flicker
        return np.clip(1 - (1 - img) * (1 - light), 0, 1)

    return frame, int(loop * FPS), True


def bokeh(loop=6.0, seed=10):
    """Big soft discs of light drifting and breathing, as out-of-focus lights do."""
    rng = np.random.default_rng(seed)
    n = 46
    x0 = rng.uniform(0, W, n).astype(np.float32)
    y0 = rng.uniform(0, H, n).astype(np.float32)
    fx = harmonic(rng, n, loop, 1)
    fy = harmonic(rng, n, loop, 1)
    ph = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    ax = rng.uniform(20, 70, n).astype(np.float32)
    ay = rng.uniform(15, 50, n).astype(np.float32)
    br = harmonic(rng, n, loop, 2)
    radius = rng.uniform(18, 70, n).astype(np.float32)
    palette = np.array([(1.0, 0.75, 0.45), (1.0, 0.55, 0.65), (0.6, 0.75, 1.0), (0.85, 0.65, 1.0), (1.0, 0.9, 0.7)], np.float32)
    col = palette[rng.integers(0, len(palette), n)]
    discs = {int(r): disc(r, 2.5) for r in radius}

    def rim(r):
        # The edge of a disc of radius r: brighter, as a lens draws out-of-focus lights.
        size = int(math.ceil(r + 5))
        yy, xx = np.mgrid[-size:size + 1, -size:size + 1]
        d = np.sqrt(xx * xx + yy * yy)
        return (np.clip((r - d) / 2.5 + 0.5, 0, 1) - np.clip((r * 0.86 - d) / 2.5 + 0.5, 0, 1)).astype(np.float32)

    rims = {int(r): rim(int(r)) for r in radius}

    def frame(i):
        t = i / FPS
        c = Canvas([0, 2.0])
        for k in range(n):
            x = x0[k] + math.sin(t * fx[k] + ph[k]) * ax[k]
            y = y0[k] + math.cos(t * fy[k] + ph[k]) * ay[k]
            w = 0.22 + 0.12 * math.sin(t * br[k] + ph[k] * 2)
            m = discs[int(radius[k])]
            # A brighter rim, as a lens draws them.
            c.stamp(2.0, x, y, m, col[k], w)
            c.stamp(0, x, y, rims[int(radius[k])], col[k], w * 0.5)
        return c.render(1.0)

    return frame, int(loop * FPS), True


def hearts(loop=5.0, seed=11):
    """Hearts of light floating up, swaying, softly glowing."""
    rng = np.random.default_rng(seed)
    n = 60
    margin = 60
    k = rng.choice((1, 2), n).astype(np.float32)
    speed = k * (H + 2 * margin) / loop
    y0 = rng.uniform(0, H + 2 * margin, n).astype(np.float32)
    x0 = rng.uniform(0, W, n).astype(np.float32)
    sw_f = harmonic(rng, n, loop, 3)
    sw_p = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    size = rng.uniform(10, 34, n).astype(np.float32)
    palette = np.array([(1.0, 0.3, 0.45), (1.0, 0.55, 0.7), (1.0, 0.85, 0.9), (0.95, 0.25, 0.35)], np.float32)
    col = palette[rng.integers(0, len(palette), n)]
    masks = {int(s): heart(s) for s in size}

    def frame(i):
        t = i / FPS
        c = Canvas([0, 1.0, 8])
        y = H + margin - np.mod(y0 + speed * t, H + 2 * margin)
        x = x0 + np.sin(t * sw_f + sw_p) * 25
        for j in range(n):
            m = masks[int(size[j])]
            c.stamp(1.0, x[j], y[j], m, col[j], 0.6)
            c.stamp(8, x[j], y[j], m, col[j], 0.35)
        return c.render(1.3)

    return frame, int(loop * FPS), True


def explosion(seed=12):
    """A fireball bursting out of the middle with a shock ring and flying debris,
    rolling into smoke: a transition, cutting under its first flash."""
    rng = np.random.default_rng(seed)
    duration = 2.6
    cut = 0.3
    n = 2200
    sp = (rng.uniform(0.0, 1.0, n) ** 1.4 * 620).astype(np.float32)
    an = rng.uniform(0, 2 * math.pi, n).astype(np.float32)
    life = rng.uniform(0.9, 2.2, n).astype(np.float32)
    lift = rng.uniform(40, 140, n).astype(np.float32)
    d = 200
    d_sp = rng.uniform(500, 1300, d).astype(np.float32)
    d_an = rng.uniform(0, 2 * math.pi, d).astype(np.float32)
    d_life = rng.uniform(0.5, 1.3, d).astype(np.float32)
    cx, cy = W * 0.5, H * 0.55

    def frame(i):
        t = i / FPS
        c = Canvas([0, 1.0, 6, 16, 34, 70])
        a = t - 0.12
        if a >= 0:
            ok = a < life
            u = a / life[ok]
            k = (1 - np.exp(-3.2 * a)) / 3.2
            x = cx + np.cos(an[ok]) * sp[ok] * k
            y = cy + np.sin(an[ok]) * sp[ok] * k * 0.8 - lift[ok] * a * a
            col = ramp(u, [(0, (1, 0.97, 0.85)), (0.18, (1, 0.75, 0.3)), (0.4, (1, 0.4, 0.07)), (0.7, (0.45, 0.15, 0.06)), (1, (0.12, 0.1, 0.1))])
            w = (1 - u) ** 0.8 * 0.3
            c.splat(6, x, y, col, w * 0.5)
            c.splat(16, x, y, col, w * 0.25)
            c.splat(34, x, y, col, w * 0.1)
            # Debris: hot specks flung far, falling.
            okd = a < d_life
            for s in range(8):
                ad = max(0.0, a - 0.03 * s)
                xd = cx + np.cos(d_an[okd]) * d_sp[okd] * ad
                yd = cy + np.sin(d_an[okd]) * d_sp[okd] * ad * 0.85 + 0.5 * 900 * ad * ad
                wd = (1 - a / d_life[okd]) * (1 - s / 8) * 0.5
                c.splat(0, xd, yd, np.tile(np.array([1, 0.7, 0.3], np.float32), (okd.sum(), 1)), wd)
            # The shock ring.
            if a < 0.6:
                r = 40 + a * 1500
                th = np.linspace(0, 2 * math.pi, 600, endpoint=False).astype(np.float32)
                c.splat(1.0, cx + np.cos(th) * r, cy + np.sin(th) * r * 0.55, np.tile(np.array([1, 0.9, 0.8], np.float32), (600, 1)), np.full(600, 0.35 * (1 - a / 0.6) ** 2, np.float32))
        # The flash: the whole frame white for an instant, when the clips change.
        f = math.exp(-((t - cut) / 0.11) ** 2)
        if f > 0.15:
            c.layers[0] += np.float32(2.4 * f)
        c.splat(70, np.array([cx]), np.array([cy]), np.array([[1, 0.85, 0.6]], np.float32), np.array([1.6 * math.exp(-max(0.0, t - 0.1) * 3)]))
        return c.render(1.5)

    return frame, int(duration * FPS), False


def flash(seed=13):
    """A burst of white light from the middle with a lens streak: a transition,
    cutting at its brightest."""
    duration = 1.0
    cut = 0.3
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    r2 = ((xx - W / 2) / W) ** 2 + ((yy - H / 2) / H) ** 2
    bloom = np.exp(-r2 * 6).astype(np.float32)
    streak = np.exp(-((yy - H / 2) / 6) ** 2) * np.exp(-((xx - W / 2) / (W * 0.45)) ** 2)

    def frame(i):
        t = i / FPS
        f = math.exp(-((t - cut) / (0.09 if t < cut else 0.22)) ** 2)
        light = bloom * 2.5 * f + streak * 1.5 * f ** 0.6 + 2.2 * f ** 1.5
        img = np.repeat(light[:, :, None], 3, 2) * np.array([1.0, 0.97, 0.92], np.float32)
        return 1 - np.exp(-img)

    return frame, int(duration * FPS), False


EFFECTS = {
    "sparks": sparks,
    "fireworks": fireworks,
    "flames": flames,
    "smoke": smoke,
    "snow": snow,
    "rain": rain,
    "confetti": confetti,
    "dust": dust,
    "bokeh": bokeh,
    "hearts": hearts,
    "fairy": fairy,
    "explosion": explosion,
    "flash": flash,
}

# What each is: a looping effect, or a transition (and the second at which it cuts).
# The cut falls just inside the first frame of the flash (the fairy's wand lands
# at 1.15 s, its first bright frame is the 35th, at 1.167 s), so that the first
# frame of the next clip is under the flash wherever the frames fall.
KINDS = {
    "fairy": ("transition", 1.18),
    "explosion": ("transition", 0.3),
    "flash": ("transition", 0.3),
}


# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

def encode(name, frame_fn, frames, out_dir, crf):
    path = os.path.join(out_dir, name + ".mp4")
    cmd = [
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-c:v", "libx264", "-preset", "veryslow", "-crf", str(crf), "-pix_fmt", "yuv420p",
        "-g", str(FPS), "-threads", "1", "-movflags", "+faststart", "-an",
        path,
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for i in range(frames):
        proc.stdin.write(to8(frame_fn(i)).tobytes())
        if i % 30 == 0:
            print(f"  {name}: frame {i}/{frames}", file=sys.stderr)
    proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"ffmpeg failed on {name}")
    return path


# The picker's tiles: each effect as a sheet of small frames (a JPEG of
# SHEET_COLUMNS frames a row), at SHEET_FPS, which the app flicks through at
# the effect's own speed without decoding any video.
SHEET_FPS = 15
SHEET_W, SHEET_H = 160, 90
SHEET_COLUMNS = 8


def frame_count(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
         "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", path],
        capture_output=True, text=True, check=True).stdout
    return int(out.strip())


def make_sheet(name, out_dir):
    """The effect's sheet of small frames, from its video; returns what the index says of it."""
    video = os.path.join(out_dir, name + ".mp4")
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", video, "-vf",
         f"fps={SHEET_FPS},scale={SHEET_W}:{SHEET_H}:flags=area",
         "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
        capture_output=True, check=True).stdout
    frames = np.frombuffer(raw, np.uint8).reshape(-1, SHEET_H, SHEET_W, 3)
    count = len(frames)
    rows = (count + SHEET_COLUMNS - 1) // SHEET_COLUMNS
    sheet = np.zeros((rows * SHEET_H, SHEET_COLUMNS * SHEET_W, 3), np.uint8)
    for i, f in enumerate(frames):
        r, c = divmod(i, SHEET_COLUMNS)
        sheet[r * SHEET_H:(r + 1) * SHEET_H, c * SHEET_W:(c + 1) * SHEET_W] = f
    path = os.path.join(out_dir, name + ".jpg")
    proc = subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
         "-s", f"{sheet.shape[1]}x{sheet.shape[0]}", "-i", "-", "-q:v", "4", path],
        input=sheet.tobytes(), check=True)
    return {"file": name + ".jpg", "frames": count, "columns": SHEET_COLUMNS,
            "fps": SHEET_FPS, "width": SHEET_W, "height": SHEET_H}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "effects"))
    ap.add_argument("--crf", type=int, default=26)
    ap.add_argument("--index-only", action="store_true", help="only write the sheets and index.json for the videos already there")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    names = [] if args.index_only else ([n for n in args.only.split(",") if n] or list(EFFECTS))
    for name in names:
        frame_fn, frames, loops = EFFECTS[name]()
        print(f"{name}: {frames} frames", file=sys.stderr)
        path = encode(name, frame_fn, frames, args.out, args.crf)
        print(f"  {path}: {os.path.getsize(path) // 1024} KB", file=sys.stderr)
    # The index lists every effect whose file is there, in the order above.
    index = []
    for name in EFFECTS:
        if not os.path.isfile(os.path.join(args.out, name + ".mp4")):
            continue
        kind, cut = KINDS.get(name, ("loop", None))
        frames = frame_count(os.path.join(args.out, name + ".mp4"))
        entry = {"name": name, "file": name + ".mp4", "kind": kind, "frames": frames, "fps": FPS}
        if cut is not None:
            entry["cut"] = cut
        entry["sheet"] = make_sheet(name, args.out)
        index.append(entry)
    with open(os.path.join(args.out, "index.json"), "w") as f:
        json.dump(index, f, indent=1)
        f.write("\n")


if __name__ == "__main__":
    main()
