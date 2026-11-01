"""
The "technical difficulties" TV break (TransitionKind.TECHNICAL_DIFFICULTIES,
see generate-effects.py), drawn and composed here by this code:

 - tv_intro: the channel changes: a click, the picture tears, snow and the
   channel number on screen, then the snow clears off the card;
 - tv_card: the test card of an old TV station ("TECHNICAL DIFFICULTIES /
   PLEASE STAND BY"), on a curved, flickering screen, with a little lounge
   tune that loops (vibraphone, electric piano, upright bass, brushes);
 - tv_outro: snow again, the next channel's number, then the screen comes on
   from a bright line on the next clip.

Everything is this project's own: the letters are a stroke font written here,
the card and its little TV set are drawn here, and the tune (melody, chords,
bass line) is composed here and played by small synthesizers below. It loops
seamlessly: every note that rings past the end of the loop is wrapped round
to its beginning, and so is the room's echo.

Everything is deterministic (fixed seeds). Needs: Python 3, numpy, scipy.
"""

import math

import numpy as np
from scipy.ndimage import gaussian_filter, map_coordinates
from scipy.signal import butter, sosfilt

FPS = 30
SR = 44100

# The parts' lengths (frames): the intro and outro are fixed, the card loops.
INTRO_FRAMES = 21
OUTRO_FRAMES = 21
CARD_LOOP_FRAMES = 60
# The card's file: as long as the longest break the app allows (TECHNICAL_DIFFICULTIES' maxMs).
CARD_SECONDS = 16.0

# ---------------------------------------------------------------------------
# The stroke font (capitals and the digits the screens need)
# ---------------------------------------------------------------------------


def arc(cx, cy, rx, ry, a0, a1, n=None):
    """Points of an arc of the ellipse at cx, cy from a0 to a1 (degrees, counter-clockwise, y down)."""
    if n is None:
        n = max(6, int(abs(a1 - a0) / 8))
    return [(cx + rx * math.cos(math.radians(a)), cy - ry * math.sin(math.radians(a))) for a in np.linspace(a0, a1, n)]


GLYPHS = {
    "A": (0.70, [[(0.0, 1.0), (0.35, 0.0), (0.70, 1.0)], [(0.13, 0.64), (0.57, 0.64)]]),
    "B": (0.62, [[(0.0, 1.0), (0.0, 0.0), (0.3, 0.0)] + arc(0.3, 0.24, 0.24, 0.24, 90, -90) + [(0.0, 0.48)],
                 [(0.3, 0.48)] + arc(0.32, 0.74, 0.27, 0.26, 90, -90) + [(0.0, 1.0)]]),
    "C": (0.64, [arc(0.36, 0.5, 0.34, 0.5, 50, 310)]),
    "D": (0.66, [[(0.0, 0.0), (0.24, 0.0)] + arc(0.24, 0.5, 0.40, 0.5, 90, -90) + [(0.0, 1.0), (0.0, 0.0)]]),
    "E": (0.56, [[(0.54, 0.0), (0.0, 0.0), (0.0, 1.0), (0.54, 1.0)], [(0.0, 0.5), (0.42, 0.5)]]),
    "F": (0.54, [[(0.54, 0.0), (0.0, 0.0), (0.0, 1.0)], [(0.0, 0.5), (0.42, 0.5)]]),
    "H": (0.64, [[(0.0, 0.0), (0.0, 1.0)], [(0.62, 0.0), (0.62, 1.0)], [(0.0, 0.5), (0.62, 0.5)]]),
    "I": (0.20, [[(0.10, 0.0), (0.10, 1.0)]]),
    "L": (0.52, [[(0.0, 0.0), (0.0, 1.0), (0.5, 1.0)]]),
    "N": (0.66, [[(0.0, 1.0), (0.0, 0.0), (0.64, 1.0), (0.64, 0.0)]]),
    "O": (0.74, [arc(0.37, 0.5, 0.37, 0.5, 0, 360, 48)]),
    "P": (0.60, [[(0.0, 1.0), (0.0, 0.0), (0.3, 0.0)] + arc(0.3, 0.26, 0.27, 0.26, 90, -90) + [(0.0, 0.52)]]),
    "S": (0.60, [arc(0.3, 0.26, 0.27, 0.24, 25, 270) + arc(0.3, 0.74, 0.29, 0.26, 90, -155)]),
    "T": (0.62, [[(0.0, 0.0), (0.62, 0.0)], [(0.31, 0.0), (0.31, 1.0)]]),
    "U": (0.64, [[(0.0, 0.0), (0.0, 0.64)] + arc(0.32, 0.64, 0.32, 0.36, 180, 360) + [(0.64, 0.0)]]),
    "Y": (0.66, [[(0.0, 0.0), (0.33, 0.52), (0.66, 0.0)], [(0.33, 0.52), (0.33, 1.0)]]),
    "0": (0.62, [arc(0.3, 0.5, 0.3, 0.5, 0, 360, 44)]),
    "3": (0.60, [arc(0.28, 0.25, 0.26, 0.25, 150, -90), arc(0.28, 0.74, 0.3, 0.26, 90, -150)]),
    "4": (0.62, [[(0.46, 1.0), (0.46, 0.0), (0.0, 0.68), (0.62, 0.68)]]),
    " ": (0.34, []),
}


def text_strokes(text, x, y, size, tracking=0.16, italic=0.0, bounce=0.0, turn=0.0, seed=0):
    """The strokes of [text] (capitals), its top-left at x, y, letters [size] px tall; and its width."""
    rng = np.random.default_rng(seed)
    lines = []
    pen = 0.0
    for k, ch in enumerate(text):
        adv, glyph = GLYPHS[ch]
        a = math.radians(turn * rng.uniform(-1, 1))
        dy = bounce * math.sin(k * 1.9 + seed)
        cx, cy = adv / 2, 0.5
        for pl in glyph:
            pts = []
            for px, py in pl:
                # Each letter turned a little about its middle, slanted, bounced on the line.
                rx = (px - cx) * math.cos(a) - (py - cy) * math.sin(a) + cx
                ry = (px - cx) * math.sin(a) + (py - cy) * math.cos(a) + cy
                rx += italic * (1.0 - ry)
                pts.append((x + (pen + rx) * size, y + (ry + dy) * size))
            lines.append(pts)
        pen += adv + tracking
    return lines, (pen - tracking) * size


def distance_to(lines, height, width):
    """Distance (px) from every pixel to the nearest of [lines] (polylines in px), within reach of them."""
    dist = np.full((height, width), 1e9, np.float32)
    for pl in lines:
        p = np.asarray(pl, np.float32)
        for (x0, y0), (x1, y1) in zip(p[:-1], p[1:]):
            pad = 40
            xa, xb = int(max(0, min(x0, x1) - pad)), int(min(width, max(x0, x1) + pad + 1))
            ya, yb = int(max(0, min(y0, y1) - pad)), int(min(height, max(y0, y1) + pad + 1))
            if xa >= xb or ya >= yb:
                continue
            yy, xx = np.mgrid[ya:yb, xa:xb].astype(np.float32)
            dx, dy = x1 - x0, y1 - y0
            l2 = dx * dx + dy * dy
            t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / l2, 0, 1) if l2 > 0 else 0.0
            d = np.hypot(xx - (x0 + t * dx), yy - (y0 + t * dy))
            dist[ya:yb, xa:xb] = np.minimum(dist[ya:yb, xa:xb], d)
    return dist


def cover(dist, radius):
    """Coverage (0..1, smoothed) of a stroke [radius] px wide on each side."""
    return np.clip(radius - dist + 0.5, 0, 1)


def paint(img, mask, rgb):
    m = mask[..., None]
    img *= 1 - m
    img += m * np.asarray(rgb, np.float32)


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (1, 3, 5))


# ---------------------------------------------------------------------------
# The test card
# ---------------------------------------------------------------------------

TEAL = hexrgb("#1E5E66")
TEAL_DARK = hexrgb("#123C42")
CREAM = hexrgb("#F4EAD2")
INK = hexrgb("#1B1A1F")
RED = hexrgb("#E8492F")
YELLOW = hexrgb("#F6C445")
BARS = [hexrgb(c) for c in ("#D9D9D9", "#E5D33A", "#3FC2C9", "#4BB64B", "#C246B8", "#D9433A", "#3A47C2")]


def ring(h, w, cx, cy, r, width):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.abs(np.hypot(xx - cx, yy - cy) - r)
    return np.clip(width - d + 0.5, 0, 1)


def disc_mask(h, w, cx, cy, r):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    return np.clip(r - np.hypot(xx - cx, yy - cy) + 0.5, 0, 1)


def rounded_rect(h, w, x0, y0, x1, y1, r, angle=0.0):
    """Coverage of a rounded rectangle turned by [angle] degrees about its middle."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    a = math.radians(angle)
    u = (xx - cx) * math.cos(a) + (yy - cy) * math.sin(a)
    v = -(xx - cx) * math.sin(a) + (yy - cy) * math.cos(a)
    hx, hy = (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
    qx, qy = np.maximum(np.abs(u) - hx, 0), np.maximum(np.abs(v) - hy, 0)
    d = np.hypot(qx, qy) - r
    return np.clip(-d + 0.5, 0, 1)


def turned(lines, cx, cy, angle):
    a = math.radians(angle)
    return [[(cx + (x - cx) * math.cos(a) - (y - cy) * math.sin(a), cy + (x - cx) * math.sin(a) + (y - cy) * math.cos(a)) for x, y in pl] for pl in lines]


def card_base(w, h):
    """The still part of the card (everything but what blinks and wobbles)."""
    s = w / 1280.0
    img = np.zeros((h, w, 3), np.float32)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    # A teal field, lighter in the middle, with the grid of a test card.
    glow = np.exp(-(((xx - w / 2) / (w * 0.55)) ** 2 + ((yy - h / 2) / (h * 0.6)) ** 2))
    img[:] = np.asarray(TEAL_DARK) + (np.asarray(TEAL) - np.asarray(TEAL_DARK)) * glow[..., None]
    step = 80 * s
    grid = np.minimum(np.abs((xx + step / 2) % step - step / 2), np.abs((yy + step / 2) % step - step / 2))
    paint(img, np.clip(1.4 * s - grid + 0.5, 0, 1) * 0.35, CREAM)
    # Colour bars along the top, grey steps along the bottom.
    bar_h = int(58 * s)
    for k, c in enumerate(BARS):
        img[:bar_h, int(k * w / 7):int((k + 1) * w / 7)] = c
    steps = 8
    for k in range(steps):
        v = k / (steps - 1)
        img[h - bar_h:, int(k * w / steps):int((k + 1) * w / steps)] = (v, v, v)
    paint(img, np.clip(1.0 - np.abs(yy - bar_h) / (2.5 * s), 0, 1), INK)
    paint(img, np.clip(1.0 - np.abs(yy - (h - bar_h)) / (2.5 * s), 0, 1), INK)
    # The big circle: a dark disc with a fan of wedges, a cream ring, the cross.
    cx, cy, r = w / 2, h / 2, 0.39 * h
    inside = disc_mask(h, w, cx, cy, r)
    paint(img, inside * 0.55, TEAL_DARK)
    ang = np.arctan2(yy - cy, xx - cx)
    fan = (np.sin(ang * 24) > 0.0).astype(np.float32) * np.clip((np.hypot(xx - cx, yy - cy) - 0.55 * r) / (0.1 * r), 0, 1)
    paint(img, gaussian_filter(fan * inside, 0.7) * 0.18, CREAM)
    paint(img, ring(h, w, cx, cy, r, 4 * s), CREAM)
    paint(img, ring(h, w, cx, cy, r * 0.56, 2 * s), CREAM)
    cross = np.minimum(np.abs(xx - cx), np.abs(yy - cy))
    paint(img, np.clip(1.6 * s - cross + 0.5, 0, 1) * inside * 0.6, CREAM)
    # Four small rings in the corners, as test cards have.
    for fx, fy in ((0.12, 0.27), (0.88, 0.27), (0.12, 0.73), (0.88, 0.73)):
        px, py = fx * w, fy * h
        paint(img, disc_mask(h, w, px, py, 62 * s) * 0.5, TEAL_DARK)
        paint(img, ring(h, w, px, py, 62 * s, 3 * s), CREAM)
        paint(img, ring(h, w, px, py, 30 * s, 2 * s), CREAM)
        corner_cross = np.minimum(np.abs(xx - px), np.abs(yy - py))
        paint(img, np.clip(1.5 * s - corner_cross + 0.5, 0, 1) * disc_mask(h, w, px, py, 62 * s), CREAM)
    # The plate with the title: cream, dark rim, a shadow, a little turned.
    angle = -3.0
    px0, py0, px1, py1 = w * 0.25, h * 0.33, w * 0.80, h * 0.63
    shadow = gaussian_filter(rounded_rect(h, w, px0 + 10 * s, py0 + 14 * s, px1 + 10 * s, py1 + 14 * s, 26 * s, angle), 6 * s)
    paint(img, shadow * 0.55, INK)
    paint(img, rounded_rect(h, w, px0 - 6 * s, py0 - 6 * s, px1 + 6 * s, py1 + 6 * s, 30 * s, angle), INK)
    paint(img, rounded_rect(h, w, px0, py0, px1, py1, 26 * s, angle), CREAM)
    # The title, in bouncing comic capitals, red with a dark outline and a drop shadow.
    size = 58 * s
    lines = []
    for row, (word, seed) in enumerate((("TECHNICAL", 3), ("DIFFICULTIES", 7))):
        probe, width = text_strokes(word, 0, 0, size, italic=0.18, bounce=0.05, turn=5, seed=seed)
        x = (px0 + px1) / 2 - width / 2 + 16 * s
        y = py0 + 26 * s + row * (size + 24 * s)
        word_lines, _ = text_strokes(word, x, y, size, italic=0.18, bounce=0.05, turn=5, seed=seed)
        lines += word_lines
    lines = turned(lines, (px0 + px1) / 2, (py0 + py1) / 2, angle)
    dist = distance_to(lines, h, w)
    shifted = distance_to([[(x + 5 * s, y + 6 * s) for x, y in pl] for pl in lines], h, w)
    paint(img, cover(shifted, 9.5 * s) * 0.9, INK)
    paint(img, cover(dist, 9.5 * s), INK)
    paint(img, cover(dist, 5.5 * s), RED)
    # A highlight along the letters' tops.
    hl = distance_to([[(x - 1.5 * s, y - 2 * s) for x, y in pl] for pl in lines], h, w)
    paint(img, cover(hl, 1.6 * s) * cover(dist, 4.5 * s) * 0.5, hexrgb("#FF9A7A"))
    return img


def tv_icon(w, h, angle, blink):
    """The little TV set beside the plate (an object, no face): its picture and coverage."""
    s = w / 1280.0
    layer = np.zeros((h, w, 3), np.float32)
    alpha = np.zeros((h, w), np.float32)
    cx, cy = w * 0.165, h * 0.50
    body = rounded_rect(h, w, cx - 74 * s, cy - 54 * s, cx + 74 * s, cy + 58 * s, 22 * s, angle)
    rim = rounded_rect(h, w, cx - 80 * s, cy - 60 * s, cx + 80 * s, cy + 64 * s, 26 * s, angle)
    screen = rounded_rect(h, w, cx - 58 * s, cy - 40 * s, cx + 30 * s, cy + 44 * s, 16 * s, angle)
    a = math.radians(angle)

    def at(dx, dy):
        return cx + dx * math.cos(a) - dy * math.sin(a), cy + dx * math.sin(a) + dy * math.cos(a)

    # Antennas, feet and knobs as strokes.
    sticks = [[at(-10, -60), at(-44, -112)], [at(6, -60), at(46, -116)], [at(-52, 64), at(-62, 80)], [at(52, 64), at(62, 80)]]
    d_sticks = distance_to(sticks, h, w)
    tips = [at(-44, -112), at(46, -116)]
    for mask, rgb in ((cover(d_sticks, 4 * s), INK), (rim, INK), (body, YELLOW)):
        paint(layer, mask, rgb)
        alpha = np.maximum(alpha, mask)
    for tx, ty in tips:
        m = disc_mask(h, w, tx, ty, 9 * s)
        paint(layer, m, INK)
        alpha = np.maximum(alpha, m)
        paint(layer, disc_mask(h, w, tx, ty, 5 * s), RED)
    paint(layer, screen, INK)
    # Its screen: snow lines and a blinking spark.
    inner = rounded_rect(h, w, cx - 52 * s, cy - 34 * s, cx + 24 * s, cy + 38 * s, 12 * s, angle)
    paint(layer, inner, hexrgb("#7FA8AD"))
    zig = [[at(-48 + k * 9, -20 + 14 * row + (5 if k % 2 else -5)) for k in range(9)] for row in range(4)]
    paint(layer, cover(distance_to(zig, h, w), 1.6 * s) * inner, CREAM)
    for k, dy in enumerate((-18, 14)):
        kx, ky = at(52, dy)
        paint(layer, disc_mask(h, w, kx, ky, 11 * s), INK)
        paint(layer, disc_mask(h, w, kx, ky, 7 * s), CREAM if (k == 0 or blink) else RED)
    return layer, alpha


def crt(img, t, rng, w, h):
    """An old screen over [img]: a hum bar rolling, a flicker, grain, lines, a curve and dark corners."""
    s = w / 1280.0
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    # Everything here repeats every 2 s (the card's loop).
    phase = (t / 2.0) % 1.0
    bar = np.exp(-(((yy / h) - phase * 1.4 + 0.2) / 0.08) ** 2)
    out = img * (1.0 + 0.10 * bar[..., None]) * (1.0 + 0.025 * math.sin(2 * math.pi * 7 * phase))
    out = out + (rng.random((h, w, 1), dtype=np.float32) - 0.5) * 0.06
    out *= (1.0 - 0.16 * ((yy.astype(np.int32) % max(2, int(round(3 * s)))) == 0))[..., None]
    # A slightly curved glass: the picture bulges, a little fringe of colour at the edges.
    nx, ny = xx / w * 2 - 1, yy / h * 2 - 1
    r2 = nx * nx + ny * ny
    k = 0.045
    res = np.empty_like(out)
    for c, shift in enumerate((1.0015, 1.0, 0.9985)):
        f = (1 - k * r2) * shift
        sx = (nx * f + 1) * w / 2
        sy = (ny * f + 1) * h / 2
        res[..., c] = map_coordinates(out[..., c], [sy, sx], order=1, mode="nearest")
    corner = rounded_rect(h, w, 0, 0, w, h, 60 * s)
    vign = 1.0 - 0.35 * r2 / 2
    return np.clip(res * (corner * vign)[..., None], 0, 1)


_card_cache = {}


def card_frame(w, h, i):
    """Frame [i] of the card, RGB 0..1 (its loop is CARD_LOOP_FRAMES long)."""
    i = i % CARD_LOOP_FRAMES
    if (w, h, i) in _card_cache:
        return _card_cache[(w, h, i)]
    _card_cache[(w, h, i)] = frame = _card_loop_frame(w, h, i)
    return frame


def _card_loop_frame(w, h, i):
    key = (w, h)
    if key not in _card_cache:
        _card_cache[key] = card_base(w, h)
    base = _card_cache[key]
    t = i / FPS
    img = base.copy()
    s = w / 1280.0
    # The TV set rocks, its knob light blinks; PLEASE STAND BY blinks, once a second.
    angle = 6.0 * math.sin(2 * math.pi * t / 2.0)
    layer, alpha = tv_icon(w, h, angle, blink=(t % 1.0) < 0.5)
    shadow = gaussian_filter(np.roll(np.roll(alpha, int(8 * s), 0), int(8 * s), 1), 4 * s)
    paint(img, shadow * 0.45, INK)
    m = alpha[..., None]
    img = img * (1 - m) + layer * m
    if (t % 1.0) < 0.62:
        size = 26 * s
        probe, width = text_strokes("PLEASE STAND BY", 0, 0, size, tracking=0.28)
        lines, _ = text_strokes("PLEASE STAND BY", w / 2 - width / 2 + 26 * s, h * 0.70, size, tracking=0.28)
        d = distance_to(lines, h, w)
        paint(img, cover(d, 5 * s), INK)
        paint(img, cover(d, 2.6 * s), YELLOW)
    rng = np.random.default_rng(1000 + i)
    return crt(img, t, rng, w, h)


def card_frame_count():
    return int(round(CARD_SECONDS * FPS))


# ---------------------------------------------------------------------------
# Snow, the channel number, the screen coming on
# ---------------------------------------------------------------------------

def snow(w, h, rng, roll):
    """TV snow (grey, a little coloured), with brighter bands rolling at [roll] (0..1)."""
    lo_h, lo_w = h // 2, w // 2
    n = rng.random((lo_h, lo_w), dtype=np.float32)
    n = np.repeat(np.repeat(n, 2, 0), 2, 1)[:h, :w]
    yy = np.arange(h, dtype=np.float32)[:, None] / h
    bands = 0.75 + 0.25 * np.sin((yy - roll) * 2 * math.pi * 3) + 0.15 * np.exp(-((yy - roll) % 1.0 - 0.5) ** 2 / 0.004)
    tint = 1.0 + (rng.random((1, 1, 3), dtype=np.float32) - 0.5) * 0.08
    return np.clip((n * bands)[..., None] * tint, 0, 1)


def osd(w, h, text):
    """The channel number on screen, green with a dark edge: its picture and coverage."""
    s = w / 1280.0
    size = 54 * s
    probe, width = text_strokes(text, 0, 0, size, tracking=0.22)
    lines, _ = text_strokes(text, w - width - 70 * s, 64 * s, size, tracking=0.22)
    d = distance_to(lines, h, w)
    edge = cover(d, 8 * s)
    core = cover(d, 4.5 * s)
    glow = gaussian_filter(core, 6 * s)
    rgb = np.zeros((h, w, 3), np.float32)
    rgb[:] = hexrgb("#7CFF6B")
    rgb = rgb * (core + glow * 0.6)[..., None] + np.asarray(INK) * (edge - core)[..., None].clip(0, 1)
    alpha = np.clip(edge + glow * 0.5, 0, 1)
    return rgb, alpha


def tears(w, h, rng, amount):
    """Torn bright lines across the screen (the signal lost for a moment), coverage 0..1."""
    a = np.zeros((h, w), np.float32)
    for _ in range(int(6 + amount * 18)):
        y = int(rng.integers(0, h))
        th = int(rng.integers(1, max(2, int(6 * w / 1280))))
        x0 = int(rng.integers(-w // 2, w // 2))
        a[y:y + th, max(0, x0):max(0, x0) + int(rng.integers(w // 4, w))] = rng.uniform(0.5, 1.0)
    return a * amount


def smooth(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3 - 2 * t)


def intro_frame(w, h, i):
    """Frame [i] of the channel change into the card: RGBA (straight), over the clip going out."""
    t = i / FPS
    rng = np.random.default_rng(2000 + i)
    rgb = snow(w, h, rng, roll=t * 2.2)
    # Snow comes in over the picture with tears, holds (the channel number), then clears off the card.
    level = smooth(0.0, 0.16, t) * (1.0 - smooth(0.52, 0.68, t))
    tear = tears(w, h, rng, 1.0 - smooth(0.05, 0.2, t) + smooth(0.5, 0.62, t) * 0.6)
    alpha = np.clip(level + tear, 0, 1)
    rgb = rgb * (1 - tear[..., None]) + tear[..., None] * 1.0
    if 0.14 <= t <= 0.6:
        o_rgb, o_a = osd(w, h, "CH 03")
        rgb = rgb * (1 - o_a[..., None]) + o_rgb * o_a[..., None]
        alpha = np.maximum(alpha, o_a * level)
    # A flash of white as the knob clicks.
    if i < 2:
        rgb = rgb * 0.4 + 0.6
        alpha = np.maximum(alpha, 0.55 - 0.25 * i)
    return np.concatenate([np.clip(rgb, 0, 1), alpha[..., None]], -1)


def outro_frame(w, h, i):
    """Frame [i] of the channel change out of the card: RGBA (straight), over the clip coming in."""
    t = i / FPS
    rng = np.random.default_rng(3000 + i)
    rgb = snow(w, h, rng, roll=t * 2.6)
    yy = np.arange(h, dtype=np.float32)[:, None] / h
    level = smooth(0.0, 0.14, t)
    alpha = np.full((h, w), level, np.float32)
    tear = tears(w, h, rng, 1.0 - smooth(0.0, 0.14, t))
    rgb = rgb * (1 - tear[..., None]) + tear[..., None]
    alpha = np.clip(alpha + tear, 0, 1)
    if 0.12 <= t < 0.43:
        o_rgb, o_a = osd(w, h, "CH 04")
        rgb = rgb * (1 - o_a[..., None]) + o_rgb * o_a[..., None]
    if t >= 0.43:
        # The screen comes on: black, a bright line that opens into the picture.
        u = smooth(0.43, 0.68, t)
        half = 0.004 + 0.5 * u ** 1.6
        dist = np.abs(yy - 0.5) / half
        open_ = np.clip((1.0 - dist) * 6.0, 0, 1)
        line = np.exp(-dist ** 2 * 4) * (1.0 - u) * 1.2
        rgb = np.zeros((h, w, 3), np.float32) + np.clip(line, 0, 1)[..., None] * np.asarray((0.85, 0.95, 1.0), np.float32)
        alpha = np.clip(1.0 - open_ + np.clip(line, 0, 1) * 0.9, 0, 1) * np.ones((1, w), np.float32)
        alpha *= 1.0 - smooth(0.58, 0.66, t)
    return np.concatenate([np.clip(rgb, 0, 1), alpha[..., None]], -1)


# ---------------------------------------------------------------------------
# The tune: an original lounge loop, eight bars at 120 beats a minute
# ---------------------------------------------------------------------------

BPM = 120
BEAT = 60.0 / BPM
BARS_IN_LOOP = 8
LOOP_SECONDS = BARS_IN_LOOP * 4 * BEAT


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def swing(beat):
    """A beat position with its off-beat eighths pushed late (a triplet's two thirds)."""
    whole = math.floor(beat)
    frac = beat - whole
    if abs(frac - 0.5) < 1e-6:
        return whole + 2.0 / 3.0
    return beat


# Melody (vibraphone): (beat, length in beats, midi note).
MELODY = [
    (0, 0.5, 72), (0.5, 0.5, 69), (1, 0.5, 65), (1.5, 0.5, 69), (2, 1, 72), (3, 0.75, 76), (3.75, 0.25, 74),
    (4, 0.5, 72), (4.5, 1, 69), (5.5, 0.5, 65), (6, 1.5, 74),
    (8, 0.5, 70), (8.5, 0.5, 74), (9, 0.5, 77), (9.5, 0.5, 76), (10, 1, 74), (11, 0.5, 70), (11.5, 0.5, 69),
    (12, 0.5, 67), (12.5, 0.5, 70), (13, 1, 72), (14, 0.5, 64), (14.5, 0.5, 67), (15, 0.5, 70), (15.5, 0.5, 69),
    (16, 0.5, 76), (16.5, 0.5, 72), (17, 1, 69), (18, 0.5, 67), (18.5, 0.5, 69), (19, 1, 72),
    (20, 0.5, 72), (20.5, 0.5, 69), (21, 0.5, 66), (21.5, 0.5, 69), (22, 1.5, 74), (23.5, 0.5, 72),
    (24, 0.5, 70), (24.5, 0.5, 69), (25, 1, 67), (26, 0.5, 76), (26.5, 0.5, 74), (27, 0.5, 72), (27.5, 0.5, 70),
    (28, 1.5, 69), (29.5, 0.5, 65), (30, 0.5, 67), (30.5, 0.5, 69), (31, 1, 72),
]

# Chords (electric piano): (beat, length in beats, voicing).
CHORDS = [
    (0, 4, (53, 57, 60, 64)), (4, 4, (50, 53, 57, 60)), (8, 4, (55, 58, 62, 65)), (12, 4, (52, 55, 58, 60)),
    (16, 4, (57, 60, 64, 67)), (20, 4, (54, 57, 60, 62)), (24, 2, (55, 58, 62, 65)), (26, 2, (52, 55, 58, 60)),
    (28, 2, (53, 57, 60, 64)), (30, 2, (52, 55, 58, 60)),
]

# Walking bass, one note a beat.
BASS = [41, 45, 48, 52, 38, 41, 45, 48, 43, 46, 50, 53, 36, 40, 43, 46,
        45, 48, 52, 55, 38, 42, 45, 48, 43, 46, 36, 40, 41, 45, 48, 40]


def envelope(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(t / max(attack, 1e-4), 1.0) * np.exp(-t / decay)


def vibraphone(freq, seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * freq * t) * envelope(n, 0.004, 1.3)
    tone += 0.35 * np.sin(2 * np.pi * freq * 4.0 * t) * envelope(n, 0.002, 0.25)
    tone += 0.12 * np.sin(2 * np.pi * freq * 9.9 * t) * envelope(n, 0.001, 0.08)
    return tone * (1.0 + 0.18 * np.sin(2 * np.pi * 5.5 * t))


def electric_piano(freq, seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    index = 1.6 * np.exp(-t / 0.35) + 0.25
    mod = np.sin(2 * np.pi * freq * t)
    tone = np.sin(2 * np.pi * freq * t + index * mod) * envelope(n, 0.003, 1.6)
    tone += 0.05 * np.sin(2 * np.pi * freq * 14 * t) * envelope(n, 0.001, 0.05)
    return tone


def upright_bass(freq, seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    f = freq * (1.0 + 0.012 * np.exp(-t / 0.03))
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = (np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)) * envelope(n, 0.006, 0.55)
    return tone


def noise_hit(rng, seconds, lo, hi, decay):
    n = int(seconds * SR)
    sos = butter(2, [lo, hi], btype="band", fs=SR, output="sos")
    return sosfilt(sos, rng.standard_normal(n)) * envelope(n, 0.001, decay)


def kick(seconds=0.3):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    f = 50 + 70 * np.exp(-t / 0.03)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * envelope(n, 0.002, 0.12)


def place(buf, sound, start_seconds, gain):
    """Adds [sound] at [start_seconds] into the loop [buf], what rings past its end wrapped round."""
    n = len(buf)
    i0 = int(round(start_seconds * SR)) % n
    idx = (np.arange(len(sound)) + i0) % n
    np.add.at(buf, idx, sound * gain)


def room(buf, seconds=1.4, mix=0.18, seed=5):
    """A small room's echo, wrapped round the loop (a circular convolution)."""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / 0.35)
    ir[0] = 0
    ir /= np.sqrt((ir ** 2).sum())
    wet = np.real(np.fft.ifft(np.fft.fft(buf) * np.fft.fft(ir, len(buf))))
    return buf + wet * mix


def tv_speaker(x):
    """An old TV's small speaker: no deep bass, no air, a little warmth."""
    sos = butter(2, [110, 6500], btype="band", fs=SR, output="sos")
    # Twice round the loop, so that the filter has settled where the kept part begins.
    doubled = sosfilt(sos, np.concatenate([x, x]))[len(x):]
    return np.tanh(doubled * 1.4) / 1.4


def tune_loop():
    """One turn of the tune (LOOP_SECONDS), mono, -1..1; it loops seamlessly."""
    n = int(round(LOOP_SECONDS * SR))
    buf = np.zeros(n)
    rng = np.random.default_rng(11)
    for beat, length, note in MELODY:
        place(buf, vibraphone(midi_hz(note), max(1.6, length * BEAT + 1.0)), swing(beat) * BEAT, 0.32)
    for beat, length, voicing in CHORDS:
        hits = [(0.0, 0.18), (1.5, 0.12)] if length >= 4 else [(0.0, 0.16)]
        if length >= 4:
            hits.append((3.5, 0.08))
        for offset, gain in hits:
            for m in voicing:
                place(buf, electric_piano(midi_hz(m), 1.8), swing(beat + offset) * BEAT, gain)
    for k, note in enumerate(BASS):
        place(buf, upright_bass(midi_hz(note), 0.9), k * BEAT, 0.42)
    for beat in range(32):
        if beat % 2 == 0:
            place(buf, kick(), beat * BEAT, 0.35)
        # Brushes: every beat, and the swung off-beat; a rim click on two and four.
        place(buf, noise_hit(rng, 0.25, 2500, 9000, 0.09), beat * BEAT, 0.10 if beat % 2 else 0.07)
        place(buf, noise_hit(rng, 0.15, 3000, 9000, 0.05), swing(beat + 0.5) * BEAT, 0.05)
        if beat % 2 == 1:
            place(buf, noise_hit(rng, 0.08, 1500, 4000, 0.02), beat * BEAT, 0.12)
    buf = room(buf)
    # Into the little speaker at a sane level: it only warms the loudest notes.
    buf = tv_speaker(buf / (np.abs(buf).max() + 1e-9) * 0.7)
    return buf / (np.abs(buf).max() + 1e-9) * 0.62


def card_audio():
    """The card's sound: the tune, as long as its file (it loops within)."""
    loop = tune_loop()
    n = int(round(CARD_SECONDS * SR))
    reps = int(math.ceil(n / len(loop)))
    return np.tile(loop, reps)[:n]


def static_hiss(rng, n):
    sos = butter(2, [400, 7000], btype="band", fs=SR, output="sos")
    return sosfilt(sos, rng.standard_normal(n)) * 0.5


def click(rng):
    n = int(0.04 * SR)
    t = np.arange(n) / SR
    return (rng.standard_normal(n) * np.exp(-t / 0.004) + np.sin(2 * np.pi * 1800 * t) * np.exp(-t / 0.01)) * 0.8


def intro_audio():
    """The knob's click and the snow's hiss, rising then giving way to the tune."""
    rng = np.random.default_rng(21)
    secs = INTRO_FRAMES / FPS
    n = int(round(secs * SR))
    t = np.arange(n) / SR
    out = static_hiss(rng, n) * np.clip(t / 0.08, 0, 1) * (1 - np.clip((t - 0.45) / 0.25, 0, 1)) * 0.55
    c = click(rng)
    out[:len(c)] += c
    return np.clip(out, -1, 1)


def outro_audio():
    """The hiss over the tune's end, a click, then the screen coming on (a thump and a short whine)."""
    rng = np.random.default_rng(31)
    secs = OUTRO_FRAMES / FPS
    n = int(round(secs * SR))
    t = np.arange(n) / SR
    out = static_hiss(rng, n) * np.clip(t / 0.12, 0, 1) * (1 - np.clip((t - 0.43) / 0.12, 0, 1)) * 0.55
    on = int(0.43 * SR)
    c = click(rng)
    out[on:on + len(c)] += c[:max(0, n - on)]
    tt = t[on:] - 0.43
    thump = np.sin(2 * np.pi * (70 * tt + 40 * np.exp(-tt / 0.05))) * np.exp(-tt / 0.09) * 0.6
    whine = np.sin(2 * np.pi * (5200 + 900 * tt) * tt) * np.exp(-tt / 0.12) * 0.05
    out[on:] += thump + whine
    return np.clip(out, -1, 1)
