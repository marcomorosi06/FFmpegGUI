"""
The 3D fist of the punch transitions (see generate-effects.py), drawn here by
this code: a right fist, knuckles first, made of rounded shapes blended into
one surface (a signed distance field), ray marched and lit with a key, a fill
and a rim light, soft shadows, ambient occlusion, a skin that lets light
through near its edges, a glossier thumbnail, and motion blur.

Two punches:
 - "punch": the fist flies at the viewer, twisting as a real punch does,
   fills the whole frame when it lands (the cut between the two clips hides
   under it), and draws back and away;
 - "punch_back": seen from behind the fist, as one's own, it flies into the
   picture and hits it (a ring of light where it lands; the app shakes the
   clip), draws back out of the frame, and a flash hides the cut.

Each frame is colour plus opacity (RGBA, not premultiplied), the fist's soft
shadow on the picture under it included. The moment of each cut (CUT) is the
one the app's recipes switch clips at (TransitionKind.PUNCH, PUNCH_BACK).

Everything is deterministic: running it again draws the same frames.
Needs: Python 3 and numpy.
"""

import math

import numpy as np

# ---------------------------------------------------------------------------
# Timing (seconds) and camera
# ---------------------------------------------------------------------------

FPS = 30
DURATION = 1.0
# Toward the viewer: the fist lands at HIT and stays on the lens until AWAY; the cut falls between them.
HIT = 0.40
AWAY = 0.50
CUT = 0.43
# Into the picture: the fist lands on it at BACK_HIT, leaves it at BACK_AWAY; a flash at BACK_CUT hides the cut.
BACK_HIT = 0.32
BACK_AWAY = 0.42
BACK_CUT = 0.55
# How deep the picture lies, seen from the camera (the fist's knuckles land there).
PLANE = 2.3

# Each punch: where the cut falls, seconds from its start.
CUTS = {"punch": CUT, "punch_back": BACK_CUT}

# Vertical field of view of the camera, in degrees.
FOV = 50.0

# Sub-frames averaged for the motion blur, over this part of a frame.
BLUR_SAMPLES = 6
SHUTTER = 0.6

# ---------------------------------------------------------------------------
# Distance field of the fist
# ---------------------------------------------------------------------------
# Hand space: x across the knuckles (index at -x, little finger at +x), y up
# (the back of the hand), z forward (the way the fist travels). The knuckles'
# front is near z = 0.15; the forearm runs back along -z.


def length(v):
    return np.sqrt(np.sum(v * v, axis=-1))


def capsule(p, a, b, r):
    """Distance to a capsule from a to b of radius r (r may taper: (ra, rb))."""
    a = np.asarray(a, np.float32)
    b = np.asarray(b, np.float32)
    pa = p - a
    ba = b - a
    h = np.clip(np.sum(pa * ba, -1) / float(np.dot(ba, ba)), 0.0, 1.0)
    if isinstance(r, tuple):
        rr = r[0] + (r[1] - r[0]) * h
    else:
        rr = r
    return length(pa - h[..., None] * ba) - rr


def round_box(p, c, half, r):
    q = np.abs(p - np.asarray(c, np.float32)) - (np.asarray(half, np.float32) - r)
    return length(np.maximum(q, 0.0)) + np.minimum(np.max(q, -1), 0.0) - r


def ellipsoid(p, c, radii):
    q = (p - np.asarray(c, np.float32)) / np.asarray(radii, np.float32)
    k0 = length(q)
    k1 = length(q / np.asarray(radii, np.float32))
    return k0 * (k0 - 1.0) / np.maximum(k1, 1e-6)


def smin(a, b, k):
    """Smooth union: the two shapes melt into each other over k."""
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b + (a - b) * h - k * h * (1.0 - h)


# The four fingers: x, knuckle height, how far forward the knuckle and the
# middle joint stand, radii (proximal, middle phalanx).
FINGERS = [
    (-0.335, 0.200, 0.015, 0.124, 0.112),  # index
    (-0.110, 0.215, 0.040, 0.130, 0.116),  # middle
    (0.118, 0.205, 0.020, 0.124, 0.110),   # ring
    (0.330, 0.175, -0.035, 0.106, 0.096),  # little
]

# The thumb: base on the side of the palm, joint, tip across the front.
THUMB = [(-0.54, -0.10, -0.40), (-0.50, -0.20, 0.02), (-0.08, -0.19, 0.165)]


def fingers_field(p):
    d = None
    for x, top, fwd, r1, r2 in FINGERS:
        knuckle = (x, top, -0.05 + fwd)
        middle = (x * 1.02, -0.06, 0.10 + fwd)
        tucked = (x * 1.03, -0.24, -0.13 + fwd)
        f = smin(
            capsule(p, knuckle, middle, (r1, r1 * 0.95)),
            capsule(p, middle, tucked, (r2, r2 * 0.88)),
            0.04,
        )
        # The knuckle's head, a little proud of the back of the hand.
        f = smin(f, ellipsoid(p, (x, top + 0.035, -0.08 + fwd), (r1 * 1.05, r1 * 0.85, r1 * 1.05)), 0.04)
        d = f if d is None else smin(d, f, 0.022)
    return d


def thumb_field(p):
    a, b, c = THUMB
    return smin(capsule(p, a, b, (0.14, 0.125)), capsule(p, b, c, (0.122, 0.102)), 0.06)


def hand_field(p):
    palm = round_box(p, (0.0, 0.0, -0.42), (0.47, 0.25, 0.36), 0.17)
    # The tendons on the back of the hand, from each knuckle toward the wrist.
    for x, top, fwd, r1, _ in FINGERS:
        palm = smin(palm, capsule(p, (x, top + 0.01, -0.12 + fwd), (x * 0.45, 0.205, -0.68), (0.034, 0.024)), 0.05)
    arm = capsule(p, (0.0, -0.03, -0.75), (0.02, -0.08, -3.2), (0.30, 0.38))
    hand = smin(palm, arm, 0.18)
    hand = smin(hand, fingers_field(p), 0.07)
    return smin(hand, thumb_field(p), 0.06)


def nail_weight(p, n):
    """Where the thumbnail is: near the thumb's tip, on its upper face (0..1)."""
    tip = np.asarray(THUMB[2], np.float32)
    toward = np.asarray([0.93, -0.18, 0.32], np.float32)
    toward /= np.linalg.norm(toward)
    q = p - tip
    along = np.sum(q * toward, -1)
    side = length(q - along[..., None] * toward)
    up = np.clip(n[..., 1] * 1.4 + n[..., 2] * 0.5, 0.0, 1.0)
    shape = np.clip(1.0 - np.abs(along + 0.01) / 0.075, 0.0, 1.0) * np.clip(1.0 - side / 0.12, 0.0, 1.0)
    return np.clip(shape * 3.0, 0.0, 1.0) * up


# ---------------------------------------------------------------------------
# The punch: where the fist is at each moment
# ---------------------------------------------------------------------------

def smoothstep(e0, e1, x):
    k = min(max((x - e0) / (e1 - e0), 0.0), 1.0)
    return k * k * (3 - 2 * k)


def rotation(roll, pitch, yaw):
    """Turns about the fist's own forward axis (roll), then across (pitch), then up (yaw)."""
    cr, sr = math.cos(roll), math.sin(roll)
    cp, sp = math.cos(pitch), math.sin(pitch)
    cy, sy = math.cos(yaw), math.sin(yaw)
    rz = np.array([[cr, -sr, 0], [sr, cr, 0], [0, 0, 1]], np.float32)
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], np.float32)
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], np.float32)
    return ry @ rx @ rz


def pose(t, variant="punch"):
    """Where the fist is at time t: its knuckles' place in camera space, its turn, and its opacity."""
    if variant == "punch_back":
        return pose_back(t)
    # The camera looks along +z; the fist points at it (its forward is -z of the camera).
    if t < HIT:
        s = t / HIT
        # Faster and faster, from far away low on the right, twisting palm-down as it comes.
        k = s ** 2.4
        z = 9.0 + (0.32 - 9.0) * k
        x = 1.6 * (1 - k) + 0.04 * k
        y = -1.0 * (1 - k) - 0.02 * k
        roll = math.radians(-75.0) * (1 - smoothstep(0.0, 1.0, s) ** 0.8)
        opacity = smoothstep(0.0, 0.08, t)
    elif t < AWAY:
        # On the lens: a small push and back, the hand still.
        s = (t - HIT) / (AWAY - HIT)
        z = 0.32 - 0.05 * math.sin(math.pi * s)
        x, y, roll = 0.04, -0.02, 0.0
        opacity = 1.0
    else:
        s = (t - AWAY) / (DURATION - AWAY)
        # Drawing back fast, then drifting away down to the right as it fades.
        k = 1 - (1 - s) ** 2.6
        z = 0.32 + (7.5 - 0.32) * k
        x = 0.04 + 1.9 * k * k
        y = -0.02 - 1.1 * k * k
        roll = math.radians(-35.0) * k
        opacity = 1.0 - smoothstep(0.65, 0.97, s)
    rot = rotation(roll, math.radians(-12.0), math.radians(-6.0))
    # Hand space to camera space: forward (z of the hand) is toward the camera (-z).
    flip = np.array([[1, 0, 0], [0, 1, 0], [0, 0, -1]], np.float32)
    return np.array([x, y, z], np.float32), flip @ rot, opacity


def pose_back(t):
    """The fist seen from behind, thrown into the picture: its knuckles' place, its turn, its opacity."""
    start = np.array([0.50, -0.50, 0.75], np.float32)
    land = np.array([0.0, -0.04, PLANE], np.float32)
    if t < BACK_HIT:
        s = t / BACK_HIT
        k = s ** 2.2
        at = start + (land - start) * k
        roll = math.radians(-70.0) * (1 - smoothstep(0.0, 1.0, s))
        opacity = smoothstep(0.0, 0.06, t)
    elif t < BACK_AWAY:
        # Against the picture: pressed in a little, then back.
        s = (t - BACK_HIT) / (BACK_AWAY - BACK_HIT)
        at = land + np.array([0.0, 0.0, 0.06 * math.sin(math.pi * s)], np.float32)
        roll = 0.0
        opacity = 1.0
    else:
        # Drawn back out of the frame, down to the right.
        s = (t - BACK_AWAY) / (DURATION - BACK_AWAY)
        k = 1 - (1 - s) ** 2.2
        out = np.array([0.9, -0.95, 0.55], np.float32)
        at = land + (out - land) * k
        roll = math.radians(-30.0) * k
        opacity = 1.0 - smoothstep(0.55, 0.85, s)
    # Pointing along its way, from the right and below toward the middle.
    rot = rotation(roll, math.radians(-14.0), math.radians(-16.0))
    return at.astype(np.float32), rot, opacity


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

def normalize(v):
    return v / np.maximum(length(v)[..., None], 1e-8)


KEY = normalize(np.array([-0.55, 0.75, -0.65], np.float32))
FILL = normalize(np.array([0.85, 0.10, -0.45], np.float32))
RIM = normalize(np.array([0.30, 0.55, 0.85], np.float32))

SKIN = np.array([0.62, 0.30, 0.18], np.float32)
SKIN_DEEP = np.array([0.75, 0.12, 0.06], np.float32)
KEY_COLOUR = np.array([1.0, 0.93, 0.84], np.float32)
NAIL = np.array([0.93, 0.70, 0.64], np.float32)


def render(width, height, t, variant="punch"):
    """The fist at time t, one sample: RGB (linear, premultiplied) and coverage, H x W."""
    centre, rot, opacity = pose(t, variant)
    inv = rot.T
    aspect = width / height
    tan = math.tan(math.radians(FOV) / 2)
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float32)
    sx = ((xx + 0.5) / width * 2 - 1) * tan * aspect
    sy = (1 - (yy + 0.5) / height * 2) * tan
    dirs = normalize(np.stack([sx, sy, np.ones_like(sx)], -1)).reshape(-1, 3)

    def field(points):
        # Points in camera space to hand space.
        return hand_field((points - centre) @ inv.T)

    # Only the rays that can meet the fist: inside a sphere around it (arm and all).
    oc = -centre
    bound = 3.6
    b = np.sum(dirs * oc, -1)
    c = float(np.dot(oc, oc)) - bound * bound
    disc = b * b - c
    candidates = np.nonzero(disc > 0)[0]
    rgb = np.zeros((dirs.shape[0], 3), np.float32)
    cover = np.zeros(dirs.shape[0], np.float32)
    if len(candidates) == 0:
        return rgb.reshape(height, width, 3), cover.reshape(height, width)
    d = dirs[candidates]
    tt = np.maximum(-b[candidates] - np.sqrt(disc[candidates]), 0.02)
    far = -b[candidates] + np.sqrt(disc[candidates])
    hit = np.zeros(len(candidates), bool)
    alive = np.ones(len(candidates), bool)
    for _ in range(140):
        idx = np.nonzero(alive)[0]
        if len(idx) == 0:
            break
        dist = field(d[idx] * tt[idx, None])
        close = dist < 0.0006 * tt[idx] + 0.0004
        hit[idx[close]] = True
        tt[idx] += dist * 0.85
        gone = tt[idx] > far[idx]
        alive[idx[close | gone]] = False
    ok = np.nonzero(hit)[0]
    if len(ok) == 0:
        return rgb.reshape(height, width, 3), cover.reshape(height, width)
    p = d[ok] * tt[ok, None]
    view = -d[ok]

    # Normal, from the field around the point (tetrahedron).
    e = 0.0015
    ks = np.array([[1, -1, -1], [-1, -1, 1], [-1, 1, -1], [1, 1, 1]], np.float32)
    n = np.zeros_like(p)
    for k in ks:
        n += k * field(p + k * e)[:, None]
    n = normalize(n)

    # Ambient occlusion: how much of the field closes in along the normal.
    occ = np.zeros(len(p), np.float32)
    w = 1.0
    for i in range(1, 6):
        h = 0.012 + 0.045 * i
        occ += (h - field(p + n * h)) * w
        w *= 0.62
    ao = np.clip(1.0 - 3.0 * occ, 0.0, 1.0)

    # Soft shadow toward the key light.
    shadow = np.ones(len(p), np.float32)
    st = np.full(len(p), 0.02, np.float32)
    origin = p + n * 0.004
    for _ in range(40):
        h = field(origin + KEY * st[:, None])
        shadow = np.minimum(shadow, 10.0 * h / st)
        st += np.clip(h, 0.01, 0.12)
    shadow = np.clip(shadow, 0.0, 1.0)
    shadow = shadow * shadow * (3 - 2 * shadow)

    # Skin: light wrapping round its edges and coming through reddened.
    local_p = (p - centre) @ inv.T
    local_n = normalize(n @ inv.T)
    nail = nail_weight(local_p, local_n)
    albedo = SKIN[None, :] * (1 - nail[:, None]) + NAIL[None, :] * nail[:, None]
    nk = np.sum(n * KEY, -1)
    wrap = np.clip((nk + 0.45) / 1.45, 0.0, 1.0)
    direct = np.clip(nk, 0.0, 1.0) * shadow
    through = np.clip(wrap - direct, 0.0, 1.0)
    diffuse = albedo * KEY_COLOUR * (direct[:, None] * 1.5) + SKIN_DEEP[None, :] * (through[:, None] * 0.5)
    nf = np.clip(np.sum(n * FILL, -1), 0.0, 1.0)
    diffuse += albedo * np.array([0.55, 0.62, 0.78], np.float32) * (nf[:, None] * 0.4)
    sky = 0.5 + 0.5 * n[:, 1]
    ambient = albedo * (np.array([0.24, 0.27, 0.34], np.float32) * sky[:, None] +
                        np.array([0.20, 0.13, 0.09], np.float32) * (1 - sky[:, None]))
    colour = (diffuse + ambient) * ao[:, None]
    # Shine: soft on the skin, sharper on the nail, stronger at grazing angles.
    half = normalize(KEY[None, :] + view)
    nh = np.clip(np.sum(n * half, -1), 0.0, 1.0)
    nv = np.clip(np.sum(n * view, -1), 0.0, 1.0)
    fresnel = 0.03 + 0.97 * (1 - nv) ** 5
    power = 28 + 140 * nail
    spec = nh ** power * (0.35 + 1.6 * nail) * (0.4 + fresnel) * shadow
    colour += spec[:, None] * np.array([1.0, 0.96, 0.92], np.float32)
    rim = np.clip(np.sum(n * RIM, -1), 0.0, 1.0) * (1 - nv) ** 2.5
    colour += rim[:, None] * np.array([0.95, 0.85, 0.80], np.float32) * 0.9 * ao[:, None]

    idx = candidates[ok]
    rgb[idx] = colour * opacity
    cover[idx] = opacity
    return rgb.reshape(height, width, 3), cover.reshape(height, width)


def on_screen(point, width, height):
    """Where a point in camera space falls on the frame, in pixels, and how many pixels one unit there spans."""
    z = max(float(point[2]), 0.05)
    tan = math.tan(math.radians(FOV) / 2)
    px = (point[0] / (z * tan * width / height) + 1) / 2 * width
    py = (1 - point[1] / (z * tan)) / 2 * height
    return px, py, height / 2 / (z * tan)


def drop_shadow(width, height, t, variant="punch"):
    """The fist's soft shadow on the picture under it (opacity)."""
    centre, _, opacity = pose(t, variant)
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float32)
    if variant == "punch_back":
        # On the picture, down to the right of the fist (the light comes from the upper left),
        # nearer and sharper as the fist comes to it.
        gap = max(PLANE - float(centre[2]), 0.0)
        under = np.array([centre[0] + 0.55 * gap, centre[1] - 0.6 * gap, PLANE], np.float32)
        px, py, unit = on_screen(under, width, height)
        size = unit * (0.42 + 0.35 * gap)
        strength = 0.6 * opacity / (1 + 0.9 * gap)
    else:
        # As the fist comes near, a larger and darker shadow on the picture behind it.
        px, py, unit = on_screen(centre, width, height)
        size = unit * 0.375
        px += size * 0.35
        py += size * 0.45
        strength = 0.55 * min(1.0, 2.2 / max(float(centre[2]), 0.05)) * opacity
    dx = (xx - px) / (size * 1.25)
    dy = (yy - py) / (size * 1.05)
    return np.clip(np.exp(-(dx * dx + dy * dy) * 1.6) * strength, 0.0, 1.0).astype(np.float32)


def impact_light(width, height, t):
    """Into the picture: a ring of light spreading from where the fist lands, and the flash over the cut (opacity, white)."""
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float32)
    light = np.zeros((height, width), np.float32)
    s = (t - BACK_HIT) / 0.28
    if 0.0 <= s <= 1.0:
        px, py, unit = on_screen(np.array([0.0, -0.04, PLANE], np.float32), width, height)
        r = np.sqrt((xx - px) ** 2 + (yy - py) ** 2)
        radius = height * (0.04 + 0.55 * (1 - (1 - s) ** 2))
        ring = np.exp(-((r - radius) / (height * (0.012 + 0.03 * s))) ** 2)
        core = np.exp(-(r / (height * 0.10)) ** 2) * (1 - s) ** 3
        light = np.maximum(light, (0.85 * ring + 0.9 * core) * (1 - s) ** 1.5)
    flash = math.exp(-((t - BACK_CUT) / (0.045 if t < BACK_CUT else 0.08)) ** 2)
    return np.clip(np.maximum(light, flash * 0.97), 0.0, 1.0)


def tonemap(c):
    """A filmic curve (ACES, fitted), then sRGB."""
    a, b, cc, d, e = 2.51, 0.03, 2.43, 0.59, 0.14
    c = np.clip((c * (a * c + b)) / (c * (cc * c + d) + e), 0.0, 1.0)
    return np.where(c <= 0.0031308, 12.92 * c, 1.055 * np.power(c, 1 / 2.4) - 0.055)


def frame(width, height, i, variant="punch"):
    """Frame i: RGBA in 0..1, colour not premultiplied: the shadow, the light, the fist over them."""
    rgb = np.zeros((height, width, 3), np.float32)
    cover = np.zeros((height, width), np.float32)
    shade = np.zeros((height, width), np.float32)
    for s in range(BLUR_SAMPLES):
        t = (i + (s + 0.5) / BLUR_SAMPLES * SHUTTER - SHUTTER / 2) / FPS
        t = min(max(t, 0.0), DURATION)
        c, a = render(width, height, t, variant)
        rgb += c
        cover += a
        shade += drop_shadow(width, height, t, variant)
    rgb /= BLUR_SAMPLES
    cover /= BLUR_SAMPLES
    shade /= BLUR_SAMPLES
    fist = tonemap(rgb / np.maximum(cover, 1e-4)[..., None])
    # Under the fist: the shadow (black), then the light (white) over it.
    light = impact_light(width, height, i / FPS) if variant == "punch_back" else np.zeros_like(cover)
    under_alpha = light + shade * (1 - light)
    under = np.repeat((light / np.maximum(under_alpha, 1e-4))[..., None], 3, -1)
    alpha = cover + under_alpha * (1 - cover)
    colour = (fist * cover[..., None] + under * (under_alpha * (1 - cover))[..., None]) / np.maximum(alpha, 1e-4)[..., None]
    return np.concatenate([np.clip(colour, 0.0, 1.0), alpha[..., None]], -1)


def frame_count():
    return int(round(DURATION * FPS))
