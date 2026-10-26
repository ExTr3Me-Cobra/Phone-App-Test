"""Procedural macro render of a pit viper's eye, for the Viper Vision app icon.

Usage: python3 -I snake_eye.py OUT.png [SIZE]
"""
import sys
import numpy as np
from PIL import Image, ImageFilter

OUT = sys.argv[1]
SIZE = int(sys.argv[2]) if len(sys.argv) > 2 else 1024
SS = 2  # supersampling
N = SIZE * SS
rng = np.random.default_rng(7)


def noise(shape, cells, octaves=5, seed=0, persistence=0.55):
    """Fractal value noise via upscaled random grids. Returns 0..1."""
    r = np.random.default_rng(seed)
    h, w = shape
    total = np.zeros(shape, np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        cy, cx = max(2, int(cells[0] * 2 ** o)), max(2, int(cells[1] * 2 ** o))
        grid = (r.random((cy, cx)) * 255).astype(np.uint8)
        img = Image.fromarray(grid).resize((w, h), Image.BICUBIC)
        total += amp * (np.asarray(img, np.float32) / 255.0)
        norm += amp
        amp *= persistence
    return total / norm


def _box(a, r, axis):
    if r < 1:
        return a
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode='edge'), axis=axis, dtype=np.float64)
    hi = np.take(c, np.arange(2 * r + 1, c.shape[axis]), axis=axis)
    lo = np.take(c, np.arange(0, c.shape[axis] - 2 * r - 1), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def blur(a, radius):
    """Approximate gaussian blur: three box blurs per axis."""
    r = int(round(radius * 0.85))
    if r < 1:
        return a
    for _ in range(3):
        a = _box(_box(a, r, 0), r, 1)
    return a


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = t[..., None] if np.ndim(t) == 2 else t
    return a * (1 - t) + b * t


yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
x = (xx - N / 2) / (N / 2)
y = (yy - N / 2) / (N / 2)
# Eye sits a little up-left of centre, looking slightly to the side.
ex, ey = -0.04, -0.03
dx, dy = x - ex, y - ey
r = np.hypot(dx, dy)
th = np.arctan2(dy, dx)
R = 0.56  # eye radius (macro: the eye fills most of the frame)

# ---------------------------------------------------------------- scales
# Conformal (log-polar) mapping: scales form rings around the eye and grow outwards.
lr = np.log(np.maximum(r, 1e-3))
K = 15.0  # scales around the eye
u = th / (2 * np.pi) * K
v = (lr - np.log(R)) * K / (2 * np.pi) * 1.15
# Offset every other ring (brick pattern).
row = np.floor(v)
u2 = u + 0.5 * (row % 2)
cu, cv = u2 - np.floor(u2) - 0.5, v - row - 0.5
# Tightly packed, overlapping scales. v grows outwards, so each scale's inner edge tucks under
# the ring before it and its tip overlaps the next.
jit = noise((N, N), (10, 10), 3, seed=3) - 0.5
su, sv = cu / 0.5, (cv + 0.08 * (2 * cu) ** 2) / 0.5
d = (np.abs(su) ** 2.4 + np.abs(sv) ** 2.4) ** (1 / 2.4) + 0.06 * jit
scale_mask = smoothstep(1.02, 0.93, d)
dome = np.clip(1 - d ** 2, 0, 1) ** 0.6
tuck = smoothstep(-1.0, -0.15, sv)  # inner edge sits under the previous ring
keel = np.exp(-(cu / 0.035) ** 2) * smoothstep(1.0, 0.2, d) * smoothstep(-0.8, 0.2, sv)
height = dome * (0.35 + 0.65 * tuck) + keel * 0.18
# Leathery micro-bumps so the scales read as skin, not plastic.
leather = noise((N, N), (140, 140), 3, seed=17)
height = height + (leather - 0.5) * 0.06 * scale_mask
# Normal from height for lighting (light from upper left).
gy, gx = np.gradient(blur(height, 1.5 * SS))
nz = 1.0 / np.sqrt(1 + (gx * 60) ** 2 + (gy * 60) ** 2)
nx, ny = -gx * 60 * nz, -gy * 60 * nz
L = np.array([-0.55, -0.6, 0.58])
L = L / np.linalg.norm(L)
diffuse = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1) * (0.45 + 0.55 * tuck)
hvec = L + np.array([0, 0, 1.0])
hvec = hvec / np.linalg.norm(hvec)
spec = np.clip(nx * hvec[0] + ny * hvec[1] + nz * hvec[2], 0, 1) ** 24 * (0.4 + 0.6 * leather)

# Viper green with yellow-lime tips and per-scale colour variation.
scale_id_noise = noise((N, N), (6, 6), 4, seed=11)
base_dark = np.array([0.02, 0.16, 0.06])
base_mid = np.array([0.10, 0.48, 0.13])
base_tip = np.array([0.55, 0.78, 0.18])
tone = np.clip(dome * 0.7 * (0.5 + 0.5 * tuck) + (scale_id_noise - 0.5) * 0.6 + 0.15, 0, 1)
scale_col = mix(mix(np.broadcast_to(base_dark, (N, N, 3)), base_mid, smoothstep(0.0, 0.6, tone)), base_tip,
                smoothstep(0.75, 1.0, tone) * 0.55)
# Fine skin texture.
micro = noise((N, N), (80, 80), 3, seed=21)
scale_col = scale_col * (0.85 + 0.3 * micro[..., None])
gap_col = np.array([0.01, 0.04, 0.02])
scales = mix(np.broadcast_to(gap_col, (N, N, 3)), scale_col * (0.25 + 0.95 * diffuse[..., None]), scale_mask)
scales += (spec * scale_mask * 0.55)[..., None] * np.array([0.85, 1.0, 0.8])
# Dark, sunken ring of skin right around the eye.
socket = smoothstep(R * 1.02, R * 1.22, r)
scales *= (0.25 + 0.75 * socket)[..., None]

# Shallow depth of field: scales further from the eye are blurred more.
blurred1 = blur(scales, 3 * SS)
blurred2 = blur(scales, 9 * SS)
dof = smoothstep(R * 1.25, R * 2.1, r)
scales = mix(mix(scales, blurred1, smoothstep(0, 0.5, dof)), blurred2, smoothstep(0.5, 1, dof))

# ---------------------------------------------------------------- iris
rr = r / R
# Radial fibres: high frequency around, low along the radius.
fib_a = noise((N, N), (1, 1), 1, seed=1)  # placeholder shape
polar_h, polar_w = 512, 2048
fib = noise((polar_h, polar_w), (5, 70), 5, seed=5, persistence=0.6)
fib2 = noise((polar_h, polar_w), (14, 40), 4, seed=6)
warp = noise((polar_h, polar_w), (8, 24), 3, seed=8)
pi_r = np.clip(rr * (polar_h - 1), 0, polar_h - 1).astype(np.int32)
pi_t = (((th + np.pi) / (2 * np.pi)) * (polar_w - 1)).astype(np.int32)
pi_t2 = ((pi_t + ((warp[pi_r, pi_t] - 0.5) * 120).astype(np.int32)) % polar_w)
fibres = fib[pi_r, pi_t2]
blotch = fib2[pi_r, pi_t]
# Thin dark reticulated lines where the noise crosses its middle value.
net = noise((N, N), (14, 14), 4, seed=12)
reticle = smoothstep(0.035, 0.0, np.abs(net - 0.5))
# Bumpy surface shading for the iris.
ibump = blur(fibres * 0.6 + blotch * 0.4, 1.0 * SS)
igy, igx = np.gradient(ibump)
ilight = np.clip(1 + (-igx * L[0] - igy * L[1]) * 90, 0.6, 1.5)

gold_in = np.array([1.00, 0.86, 0.34])
gold = np.array([0.93, 0.62, 0.12])
amber = np.array([0.62, 0.30, 0.04])
dark = np.array([0.18, 0.07, 0.01])
iris = mix(mix(np.broadcast_to(gold_in, (N, N, 3)), gold, smoothstep(0.1, 0.55, rr)), amber, smoothstep(0.55, 0.95, rr))
iris = mix(iris, dark, np.clip((fibres - 0.56) * 2.2, 0, 1) * 0.75)  # dark crypts
iris = mix(iris, dark * 0.8, reticle * 0.55 * smoothstep(0.2, 0.5, rr))
iris = mix(iris, gold_in * 1.08, np.clip((0.42 - fibres) * 3.0, 0, 1) * 0.6)  # bright strands
iris = mix(iris, amber * 0.6, np.clip((blotch - 0.6) * 2.5, 0, 1) * 0.5)
# Dark limbal ring and speckles.
iris = mix(iris, dark * 0.5, smoothstep(0.86, 1.0, rr))
speck = noise((N, N), (90, 90), 2, seed=9)
iris = iris * ilight[..., None]
iris = mix(iris, dark, np.clip((speck - 0.78) * 6, 0, 1) * 0.6)

# Vertical slit pupil with a soft collarette around it.
py = (y - ey + 0.0) / (R * 0.9)
half_w = 0.085 * R * np.sqrt(np.clip(1 - py ** 2, 0, 1)) + 0.004
slit = np.abs(dx) / np.maximum(half_w, 1e-4)
pupil = smoothstep(1.08, 0.92, slit) * smoothstep(1.0, 0.96, np.abs(py))
collar = smoothstep(2.6, 1.0, slit) * smoothstep(1.02, 0.85, np.abs(py))
iris = mix(iris, iris * np.array([1.1, 0.95, 0.7]) * 1.15, collar * 0.35)
pupil_col = np.array([0.005, 0.007, 0.012])
eye = mix(iris, pupil_col, pupil)

# Spherical shading: the eyeball darkens toward its edge and from the light side.
sphere_z = np.sqrt(np.clip(1 - rr ** 2, 0, 1))
shade = 0.35 + 0.65 * np.clip((-dx / R * L[0] - dy / R * L[1]) * 0.5 + sphere_z * 0.75, 0, 1.2)
eye *= shade[..., None]

# Brille (clear eye cap): glossy reflections of a window and the sky, plus a rim light.
win_x, win_y = ex - 0.36 * R, ey - 0.30 * R
# Window reflection, bent around the curved eye cap.
wx = (x - win_x) / (0.15 * R)
wy = (y - win_y + 0.35 * (x - win_x) ** 2 / R) / (0.11 * R)
window = smoothstep(1.0, 0.6, np.maximum(np.abs(wx), np.abs(wy)))
window *= 1 - 0.5 * smoothstep(0.10, 0.0, np.abs(wx)) * smoothstep(1.0, 0.7, np.abs(wy))
window *= 1 - 0.5 * smoothstep(0.10, 0.0, np.abs(wy)) * smoothstep(1.0, 0.7, np.abs(wx))
window = blur(window, 1.5 * SS) * 0.8
small = np.exp(-(((x - (ex + 0.22 * R)) / (0.05 * R)) ** 2 + ((y - (ey + 0.30 * R)) / (0.035 * R)) ** 2))
sky = smoothstep(0.2, -0.9, dy / R) * smoothstep(1.0, 0.3, rr) * 0.18
rim = smoothstep(0.80, 0.99, rr) * smoothstep(1.0, 0.97, rr) * np.clip(-dy / R + 0.3, 0, 1) * 0.5
eye = eye + (window * 0.95 + small * 0.9 + sky + rim)[..., None] * np.array([1.0, 0.99, 0.95])
eye = np.clip(eye, 0, 1)

# ---------------------------------------------------------------- composite
eye_mask = smoothstep(R * 1.005, R * 0.985, r)
img = mix(scales, eye, eye_mask)
# Viper brow: a large scale overhangs the top of the eye and shades it.
brow_edge = ey - R * 0.62 + 0.55 * (x - ex) ** 2 / R + 0.05 * (x - ex)
brow = smoothstep(0.012, -0.012, y - brow_edge) * smoothstep(R * 1.35, R * 0.9, r)
brow_shadow = smoothstep(0.16, 0.0, y - brow_edge) * (1 - brow) * smoothstep(R * 1.2, R * 0.8, r)
img *= (1 - 0.65 * brow_shadow)[..., None]
bh = smoothstep(-0.22, 0.0, y - brow_edge)  # lit rounded lip of the brow scale
brow_col = mix(np.broadcast_to(base_dark, (N, N, 3)), base_mid * 1.1, bh * 0.9) * (0.75 + 0.35 * micro[..., None])
ridges = 0.5 + 0.5 * np.sin((y - brow_edge) * 160 + (noise((N, N), (6, 6), 2, seed=41) - 0.5) * 6)
brow_col = brow_col * (0.7 + 0.3 * ridges[..., None]) * (0.8 + 0.4 * leather[..., None])
brow_col += (smoothstep(-0.05, -0.01, y - brow_edge) * smoothstep(0.0, -0.01, y - brow_edge) * 0.25)[..., None]
img = mix(img, brow_col, brow)
# Thin shadow where the brille meets the scales.
img *= (1 - 0.6 * np.exp(-((r - R * 1.01) / (0.012)) ** 2))[..., None]
# Vignette and a touch of grain.
vig = 1 - 0.45 * smoothstep(0.6, 1.5, np.hypot(x, y))
img *= vig[..., None]
grain = (noise((N, N), (N // 4, N // 4), 1, seed=33) - 0.5) * 0.03
img = np.clip(img + grain[..., None], 0, 1)
# Gentle tone curve for contrast.
img = img ** 0.92
img = smoothstep(0.0, 1.0, img) * 0.35 + img * 0.65

out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGB')
out = out.resize((SIZE, SIZE), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=1.2, percent=60, threshold=2))
out.save(OUT)
print('saved', OUT)
