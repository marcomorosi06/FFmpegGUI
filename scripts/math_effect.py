"""
The "math" effect (see generate-effects.py), drawn here by this code: the
formulas, symbols and little diagrams of a head full of maths (the confused
lady of the meme) floating in front of the picture, written on stroke by
stroke as if with chalk of light, drifting, turning and coming closer, then
fading away. Light on black, screened over the video like the other effects.

The letters are this script's own stroke font: every glyph a few lines and
arcs on a grid one unit tall (0 at the top, 1 on the baseline), so nothing is
taken from a typeface. A formula is a string with a little markup: "^x" or
"^{...}" raises a smaller part, "`sqrt`{...}" puts it under a square root.

It loops seamlessly: every item comes back once a loop, at its own phase,
and is invisible at both ends of the part of the loop it shows in. Everything is deterministic
(fixed seed). Needs: Python 3 and numpy (the drawing is generate-effects.py's
Canvas).
"""

import math

import numpy as np

# ---------------------------------------------------------------------------
# The stroke font
# ---------------------------------------------------------------------------


def arc(cx, cy, rx, ry, a0, a1, n=None):
    """Points of an arc of the ellipse at cx, cy from angle a0 to a1 (degrees, counter-clockwise, y down)."""
    if n is None:
        n = max(6, int(abs(a1 - a0) / 9))
    a = np.radians(np.linspace(a0, a1, n))
    return [(cx + rx * math.cos(t), cy - ry * math.sin(t)) for t in a]


def ring(cx, cy, rx, ry):
    return arc(cx, cy, rx, ry, 0, 360, 40)


def dot(cx, cy, r=0.035):
    return ring(cx, cy, r, r)


# Each glyph: (advance, [polyline, ...]); a polyline is a list of (x, y).
GLYPHS = {
    "0": (0.62, [ring(0.3, 0.5, 0.24, 0.45)]),
    "1": (0.5, [[(0.1, 0.22), (0.3, 0.05), (0.3, 0.95)], [(0.12, 0.95), (0.46, 0.95)]]),
    "2": (0.62, [arc(0.3, 0.3, 0.23, 0.24, 160, -40) + [(0.06, 0.95), (0.56, 0.95)]]),
    "3": (0.62, [arc(0.29, 0.27, 0.21, 0.22, 150, -90), arc(0.29, 0.71, 0.25, 0.24, 90, -150)]),
    "4": (0.62, [[(0.44, 0.95), (0.44, 0.05), (0.04, 0.66), (0.58, 0.66)]]),
    "5": (0.62, [[(0.52, 0.05), (0.14, 0.05), (0.1, 0.46)] + arc(0.3, 0.67, 0.25, 0.28, 125, -145)]),
    "6": (0.62, [[(0.48, 0.07), (0.15, 0.45)], ring(0.31, 0.68, 0.23, 0.27)]),
    "7": (0.62, [[(0.05, 0.05), (0.56, 0.05), (0.22, 0.95)]]),
    "8": (0.62, [ring(0.3, 0.27, 0.19, 0.22), ring(0.3, 0.72, 0.24, 0.24)]),
    "9": (0.62, [ring(0.3, 0.31, 0.23, 0.26), [(0.53, 0.33), (0.2, 0.95)]]),
    "a": (0.6, [ring(0.25, 0.69, 0.2, 0.26), [(0.46, 0.43), (0.46, 0.95)]]),
    "b": (0.6, [[(0.08, 0.0), (0.08, 0.95)], ring(0.28, 0.69, 0.2, 0.26)]),
    "c": (0.56, [arc(0.28, 0.69, 0.22, 0.26, 50, 310)]),
    "d": (0.6, [ring(0.25, 0.69, 0.2, 0.26), [(0.46, 0.0), (0.46, 0.95)]]),
    "e": (0.58, [[(0.06, 0.68), (0.5, 0.68)] + arc(0.28, 0.68, 0.22, 0.27, 0, 320)]),
    "f": (0.46, [arc(0.38, 0.17, 0.14, 0.14, 20, 180) + [(0.24, 0.95)], [(0.06, 0.43), (0.42, 0.43)]]),
    "g": (0.6, [ring(0.25, 0.66, 0.2, 0.24), [(0.46, 0.43), (0.46, 1.05)] + arc(0.27, 1.05, 0.19, 0.18, 0, -160)]),
    "i": (0.3, [[(0.13, 0.43), (0.13, 0.95)], dot(0.13, 0.22)]),
    "k": (0.56, [[(0.08, 0.0), (0.08, 0.95)], [(0.46, 0.42), (0.09, 0.72), (0.48, 0.95)]]),
    "l": (0.3, [[(0.13, 0.0), (0.13, 0.95)]]),
    "m": (0.86, [[(0.08, 0.43), (0.08, 0.95)], arc(0.25, 0.62, 0.17, 0.19, 180, 0) + [(0.42, 0.95)],
                 arc(0.6, 0.62, 0.18, 0.19, 180, 0) + [(0.78, 0.95)]]),
    "n": (0.6, [[(0.08, 0.43), (0.08, 0.95)], arc(0.27, 0.62, 0.19, 0.2, 180, 0) + [(0.46, 0.95)]]),
    "o": (0.58, [ring(0.27, 0.69, 0.21, 0.26)]),
    "q": (0.6, [ring(0.25, 0.69, 0.2, 0.26), [(0.46, 0.43), (0.46, 1.25)]]),
    "r": (0.46, [[(0.08, 0.43), (0.08, 0.95)], arc(0.28, 0.62, 0.2, 0.19, 180, 50)]),
    "s": (0.54, [arc(0.26, 0.56, 0.18, 0.13, 20, 270) + arc(0.26, 0.82, 0.19, 0.13, 90, -160)]),
    "t": (0.44, [[(0.2, 0.12), (0.2, 0.85)] + arc(0.3, 0.85, 0.1, 0.1, 180, 290), [(0.04, 0.43), (0.4, 0.43)]]),
    "x": (0.56, [[(0.05, 0.43), (0.48, 0.95)], [(0.48, 0.43), (0.05, 0.95)]]),
    "y": (0.56, [[(0.05, 0.43), (0.27, 0.92)], [(0.5, 0.43), (0.14, 1.25)]]),
    "z": (0.54, [[(0.06, 0.43), (0.48, 0.43), (0.06, 0.95), (0.5, 0.95)]]),
    "A": (0.7, [[(0.04, 0.95), (0.34, 0.05), (0.64, 0.95)], [(0.15, 0.62), (0.53, 0.62)]]),
    "B": (0.64, [[(0.08, 0.95), (0.08, 0.05), (0.32, 0.05)] + arc(0.32, 0.27, 0.2, 0.22, 90, -90) + [(0.08, 0.49)],
                 [(0.32, 0.49)] + arc(0.34, 0.72, 0.24, 0.23, 90, -90) + [(0.08, 0.95)]]),
    "E": (0.6, [[(0.52, 0.05), (0.08, 0.05), (0.08, 0.95), (0.52, 0.95)], [(0.08, 0.5), (0.44, 0.5)]]),
    "P": (0.62, [[(0.08, 0.95), (0.08, 0.05), (0.32, 0.05)] + arc(0.32, 0.29, 0.22, 0.24, 90, -90) + [(0.08, 0.53)]]),
    "V": (0.66, [[(0.04, 0.05), (0.33, 0.95), (0.62, 0.05)]]),
    "+": (0.62, [[(0.08, 0.6), (0.52, 0.6)], [(0.3, 0.38), (0.3, 0.82)]]),
    "-": (0.56, [[(0.08, 0.6), (0.48, 0.6)]]),
    "=": (0.62, [[(0.08, 0.5), (0.52, 0.5)], [(0.08, 0.72), (0.52, 0.72)]]),
    "~": (0.62, [arc(0.18, 0.62, 0.12, 0.07, 180, 0) + arc(0.42, 0.62, 0.12, 0.07, 180, 360)]),
    "#": (0.66, [[(0.08, 0.48), (0.56, 0.48)], [(0.08, 0.74), (0.56, 0.74)], [(0.24, 0.3), (0.2, 0.92)], [(0.44, 0.3), (0.4, 0.92)]]),
    "/": (0.5, [[(0.44, 0.0), (0.06, 1.0)]]),
    "*": (0.56, [[(0.1, 0.42), (0.46, 0.78)], [(0.46, 0.42), (0.1, 0.78)]]),
    "(": (0.36, [arc(0.44, 0.5, 0.3, 0.58, 125, 235)]),
    ")": (0.36, [arc(-0.08, 0.5, 0.3, 0.58, 55, -55)]),
    "|": (0.3, [[(0.15, -0.05), (0.15, 1.05)]]),
    ".": (0.28, [dot(0.12, 0.92, 0.045)]),
    ",": (0.28, [[(0.14, 0.88), (0.08, 1.08)]]),
    "!": (0.3, [[(0.14, 0.05), (0.14, 0.68)], dot(0.14, 0.92, 0.045)]),
    "?": (0.6, [arc(0.28, 0.27, 0.22, 0.22, 160, -60) + [(0.28, 0.66)], dot(0.28, 0.92, 0.045)]),
    "'": (0.24, [[(0.14, 0.0), (0.08, 0.22)]]),
    " ": (0.36, []),
    # Greek letters and signs, named by one character each.
    "p": (0.66, [[(0.02, 0.45), (0.6, 0.43)], [(0.18, 0.45), (0.15, 0.95)], [(0.44, 0.44), (0.47, 0.95)]]),  # pi
    "S": (0.66, [[(0.58, 0.12), (0.58, 0.04), (0.06, 0.04), (0.36, 0.5), (0.06, 0.96), (0.58, 0.96), (0.58, 0.88)]]),  # Sigma
    "D": (0.66, [[(0.33, 0.04), (0.62, 0.95), (0.04, 0.95), (0.33, 0.04)]]),  # Delta
    "h": (0.56, [ring(0.27, 0.5, 0.21, 0.45), [(0.06, 0.5), (0.48, 0.5)]]),  # theta
    "w": (0.66, [arc(0.34, 0.68, 0.28, 0.27, 20, 345) + [(0.6, 0.95)]]),  # alpha
    "I": (0.48, [arc(0.36, 0.0, 0.1, 0.1, 10, 190) + [(0.26, 0.1), (0.2, 0.92)] + arc(0.1, 0.92, 0.1, 0.1, -10, -190)]),  # integral
    "8i": (0.9, [[(0.44, 0.62)] + arc(0.24, 0.62, 0.2, 0.2, 0, 360, 40)[1:] + [(0.44, 0.62)] + arc(0.64, 0.62, 0.2, 0.2, 180, -180, 40)[1:]]),  # infinity
    "d'": (0.6, [ring(0.26, 0.7, 0.2, 0.25), arc(0.18, 0.5, 0.29, 0.45, -5, 120)]),  # partial
    ">": (0.84, [[(0.05, 0.62), (0.76, 0.62)], [(0.56, 0.48), (0.78, 0.62), (0.56, 0.76)]]),  # arrow
    "+-": (0.62, [[(0.08, 0.48), (0.52, 0.48)], [(0.3, 0.28), (0.3, 0.68)], [(0.08, 0.86), (0.52, 0.86)]]),
    "~=": (0.62, [arc(0.18, 0.52, 0.12, 0.07, 180, 0) + arc(0.42, 0.52, 0.12, 0.07, 180, 360),
                  arc(0.18, 0.74, 0.12, 0.07, 180, 0) + arc(0.42, 0.74, 0.12, 0.07, 180, 360)]),
    "!=": (0.62, [[(0.08, 0.5), (0.52, 0.5)], [(0.08, 0.72), (0.52, 0.72)], [(0.42, 0.3), (0.18, 0.92)]]),
}

# Names longer than one character, written in a formula as `name` (between backticks).
NAMED = {"inf": "8i", "pi": "p", "Sigma": "S", "Delta": "D", "theta": "h", "alpha": "w", "int": "I",
         "partial": "d'", "to": ">", "pm": "+-", "approx": "~=", "neq": "!=", "sqrt": "R"}


def tokens(text):
    """The glyph names and markup of a formula."""
    out = []
    i = 0
    while i < len(text):
        c = text[i]
        if c == "`":
            j = text.index("`", i + 1)
            out.append(NAMED[text[i + 1:j]])
            i = j + 1
        else:
            out.append(c)
            i += 1
    return out


def layout(toks, i=0, stop=None):
    """Lines of the formula (unit high) from token i up to stop; returns (polylines, advance, next index)."""
    lines = []
    x = 0.0
    while i < len(toks):
        t = toks[i]
        if t == stop:
            return lines, x, i + 1
        if t in ("^", "R", "_") and i + 1 < len(toks):
            nxt = toks[i + 1]
            if nxt == "{":
                inner, adv, i = layout(toks, i + 2, "}")
            else:
                inner, adv, _ = layout([nxt], 0)
                i += 2
            if t == "R":
                # A square root: its sign, then the line over what it holds.
                sign = [(x, 0.62), (x + 0.1, 0.56), (x + 0.24, 0.98), (x + 0.42, -0.08), (x + 0.5 + adv, -0.08)]
                lines.append(sign)
                lines += [[(x + 0.48 + px, py) for px, py in pl] for pl in inner]
                x += 0.56 + adv
            else:
                s = 0.6
                dy = -0.34 if t == "^" else 0.5
                lines += [[(x + 0.04 + px * s, dy + py * s) for px, py in pl] for pl in inner]
                x += 0.08 + adv * s
            continue
        if t == "{":
            inner, adv, i = layout(toks, i + 1, "}")
            lines += [[(x + px, py) for px, py in pl] for pl in inner]
            x += adv
            continue
        adv, glyph = GLYPHS[t]
        lines += [[(x + px, py) for px, py in pl] for pl in glyph]
        x += adv
        i += 1
    return lines, x, i


def formula(text):
    lines, width, _ = layout(tokens(text))
    return lines, width


FORMULAS = [
    "a^2+b^2=c^2",
    "e^{i`pi`}+1=0",
    "`int`f(x)dx",
    "x=-b`pm``sqrt`{b^2-4ac}",
    "`Sigma`1/n^2=`pi`^2/6",
    "dy/dx",
    "sin^2`theta`+cos^2`theta`=1",
    "E=mc^2",
    "`pi``approx`3.14159",
    "lim sinx/x=1",
    "f'(x)=2x",
    "`partial`f/`partial`y",
    "2+2=?",
    "y=mx+q",
    "n!",
    "`sqrt`2",
    "x^2",
    "`inf`",
    "`Delta`",
    "`alpha`+`theta`",
    "`int`e^{-x^2}dx=`sqrt`{`pi`}",
    "a`neq`b",
    "V=4/3`pi`r^3",
    "log2=0.301",
    "x`to``inf`",
    "P(A|B)",
    "`Delta`=b^2-4ac",
    "cos0=1",
]

# Question marks: the confusion of the meme.
QUESTIONS = ["?", "??", "?!", "???"]


def shapes(rng):
    """The little diagrams of the meme: each a list of polylines (about one unit wide) and its width."""
    out = []
    # A right triangle with its right angle and its sides named.
    tri = [[(0.0, 1.0), (1.6, 1.0), (0.0, 0.0), (0.0, 1.0)], [(0.0, 0.84), (0.16, 0.84), (0.16, 1.0)]]
    for text, dx, dy in (("a", -0.42, 0.38), ("b", 0.7, 1.08), ("c", 0.86, 0.22)):
        lines, _ = formula(text)
        tri += [[(dx + px * 0.45, dy + py * 0.45) for px, py in pl] for pl in lines]
    out.append((tri, 1.6))
    # A sine wave on its axes.
    xs = np.linspace(0, 2.4, 60)
    out.append(([[(0.0, 0.5), (2.5, 0.5)], [(0.05, 0.0), (0.05, 1.0)],
                 [(float(x) + 0.05, 0.5 - 0.4 * math.sin(x * 2 * math.pi / 1.2)) for x in xs]], 2.5))
    # A circle with its radius.
    circ = [ring(0.6, 0.6, 0.6, 0.6), [(0.6, 0.6), (1.02, 0.18)], dot(0.6, 0.6, 0.03)]
    lines, _ = formula("r")
    circ += [[(0.86 + px * 0.4, 0.28 + py * 0.4) for px, py in pl] for pl in lines]
    out.append((circ, 1.2))
    # A parabola on its axes.
    xs = np.linspace(-1.0, 1.0, 40)
    out.append(([[(0.0, 1.0), (2.4, 1.0)], [(1.2, 1.15), (1.2, -0.2)],
                 [(float(x) + 1.2, 1.0 - 1.05 * x * x) for x in xs]], 2.4))
    # A cube in perspective (its corners turned by the item's own turn when drawn: see cube_lines).
    out.append(("cube", 1.4))
    # A triangle split by its height, angles marked.
    tri2 = [[(0.0, 1.0), (1.8, 1.0), (0.7, 0.0), (0.0, 1.0)], [(0.7, 0.0), (0.7, 1.0)],
            arc(0.0, 1.0, 0.22, 0.22, 0, 55), arc(1.8, 1.0, 0.2, 0.2, 180, 138)]
    out.append((tri2, 1.8))
    return out


def cube_lines(turn, tilt):
    """A wire cube (one unit), turned by [turn] about the vertical and tilted by [tilt], in perspective."""
    pts = np.array([(x, y, z) for x in (-0.5, 0.5) for y in (-0.5, 0.5) for z in (-0.5, 0.5)], np.float64)
    c, s = math.cos(turn), math.sin(turn)
    x = pts[:, 0] * c + pts[:, 2] * s
    z = -pts[:, 0] * s + pts[:, 2] * c
    y = pts[:, 1]
    ct, st = math.cos(tilt), math.sin(tilt)
    y, z = y * ct - z * st, y * st + z * ct
    f = 2.6 / (2.6 + z)
    px = 0.7 + x * f
    py = 0.7 + y * f
    edges = [(a, b) for a in range(8) for b in range(a + 1, 8) if bin(a ^ b).count("1") == 1]
    return [[(px[a], py[a]), (px[b], py[b])] for a, b in edges]


# ---------------------------------------------------------------------------
# The effect
# ---------------------------------------------------------------------------

PALETTE = np.array([
    (1.0, 1.0, 1.0),
    (0.72, 0.92, 1.0),
    (1.0, 0.93, 0.66),
    (0.8, 0.85, 1.0),
    (0.75, 1.0, 0.88),
], np.float32)


def resample(polyline, step):
    """Points along a polyline every [step] (its own units), with their distance from its start."""
    p = np.asarray(polyline, np.float64)
    if len(p) < 2:
        return p, np.zeros(len(p))
    seg = np.diff(p, axis=0)
    lengths = np.hypot(seg[:, 0], seg[:, 1])
    total = lengths.sum()
    n = max(2, int(total / step) + 1)
    at = np.linspace(0, total, n)
    cum = np.concatenate([[0], np.cumsum(lengths)])
    x = np.interp(at, cum, p[:, 0])
    y = np.interp(at, cum, p[:, 1])
    return np.stack([x, y], 1), at


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# The part of the loop each item shows in (the rest of it, it is away): about
# half of them are on screen at once.
SHOWN = 0.55


def make(canvas_cls, width, height, fps, loop=6.0, seed=14):
    """The effect: (frame function, frame count, loops). [canvas_cls] is generate-effects.py's Canvas."""
    rng = np.random.default_rng(seed)
    items = []
    diagrams = shapes(rng)
    texts = [(t, False) for t in FORMULAS] + [(q, False) for q in QUESTIONS] + [(None, True)] * len(diagrams)
    order = rng.permutation(len(texts))
    count = len(texts)
    # Spread over the frame without clumps: two per cell of a grid (the cells shuffled), the
    # second showing while the first is away.
    cols, rows = 6, 4
    cells = np.concatenate([rng.permutation(cols * rows), rng.permutation(cols * rows)])[:count]
    starts = rng.uniform(0, 1, cols * rows)
    for n, k in enumerate(order):
        text, is_shape = texts[k]
        if is_shape:
            shape, wide = diagrams[k - len(FORMULAS) - len(QUESTIONS)]
            lines = shape
        else:
            lines, wide = formula(text)
            if text in QUESTIONS:
                wide = max(wide, 0.6)
        cell = cells[n]
        second = n >= cols * rows
        cx = (cell % cols + rng.uniform(0.2, 0.8)) / cols * width
        cy = (cell // cols + rng.uniform(0.2, 0.8)) / rows * height
        depth = rng.uniform(0, 1)
        unit = (26 + 34 * depth) * (width / 1280)
        if text in QUESTIONS:
            unit *= 1.7
        if is_shape:
            unit *= 1.25
        # A long formula is drawn smaller, so that it keeps to its corner of the frame.
        unit = min(unit, 0.24 * width / max(wide, 1e-6))
        # Whole inside the frame, as large as it grows.
        half_w = wide * unit * 1.25 / 2 + 12
        half_h = unit * 1.25 * 0.75 + 12
        cx = min(max(cx, half_w), width - half_w)
        cy = min(max(cy, half_h), height - half_h)
        items.append({
            "lines": lines,
            "wide": wide,
            "cx": cx, "cy": cy,
            "unit": unit,
            "depth": depth,
            "phase": (starts[cell] + (0.5 if second else 0.0)) % 1.0,
            "drift": rng.normal(0, 1, 2) * (14 + 22 * depth) * (width / 1280),
            "turn0": rng.uniform(-0.35, 0.35),
            "spin": rng.uniform(-0.25, 0.25),
            "cube_spin": rng.choice((-1, 1)) * rng.uniform(1.2, 2.2),
            "colour": PALETTE[rng.integers(0, len(PALETTE))],
            "wobble": rng.uniform(0, 2 * math.pi),
        })

    frames = int(round(loop * fps))

    def frame(i):
        t = i / fps
        c = canvas_cls([1.0, 2.2, 9.0])
        for it in items:
            # Its age through the part of the loop it shows in: written on, held, faded out.
            u = ((t / loop + it["phase"]) % 1.0) / SHOWN
            if u >= 1.0:
                continue
            written = smooth(0.0, 0.32, u)
            shown = smooth(0.0, 0.06, u) * (1 - smooth(0.74, 0.99, u))
            if shown <= 0.002:
                continue
            # Coming closer as it lives, drifting and turning a little.
            scale = it["unit"] * (0.8 + 0.45 * u)
            ang = it["turn0"] + it["spin"] * u + 0.04 * math.sin(2 * math.pi * u * 2 + it["wobble"])
            ox = it["cx"] + it["drift"][0] * (u - 0.5) * 2
            oy = it["cy"] + it["drift"][1] * (u - 0.5) * 2 + 6 * math.sin(2 * math.pi * u + it["wobble"])
            lines = it["lines"]
            if isinstance(lines, str):
                lines = cube_lines(it["wobble"] + it["cube_spin"] * u * math.pi, 0.45 + 0.2 * math.sin(2 * math.pi * u))
            step = 0.55 / scale
            pts, dist = [], []
            run = 0.0
            for pl in lines:
                p, d = resample(pl, step)
                pts.append(p)
                dist.append(d + run)
                run += (d[-1] if len(d) else 0.0) + 0.3
            p = np.concatenate(pts)
            d = np.concatenate(dist)
            keep = d <= written * run
            if not keep.any():
                continue
            p = p[keep]
            # Centred on its middle, turned, scaled and placed.
            lx = (p[:, 0] - it["wide"] / 2) * scale
            ly = (p[:, 1] - 0.5) * scale
            ca, sa = math.cos(ang), math.sin(ang)
            x = ox + lx * ca - ly * sa
            y = oy + lx * sa + ly * ca
            near = it["depth"]
            col = np.repeat(it["colour"][None, :], len(x), 0)
            # The chalk's tip, brightest where it is writing now.
            tip = np.exp(-np.maximum(0.0, written * run - d[keep]) / 0.6) * (written < 1)
            w = (0.16 + 0.1 * near) * shown * (1 + 2.5 * tip)
            sharp = 1.0 if near > 0.45 else 2.2
            c.splat(sharp, x, y, col, w * (1.0 if sharp == 1.0 else 0.5))
            c.splat(9.0, x, y, col, w * 0.035)
        return c.render(1.15)

    return frame, frames, True
