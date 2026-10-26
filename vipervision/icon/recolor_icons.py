"""Makes the launcher icon's colour variants (Gallery's icon colour setting).

The eye's blue (and blue-tinted) parts are shifted to each colour; dark pixels (the pupil and
shadows) are left untouched so the pupil stays black. The default icon (Gallery's "green"
alias, shown as the first swatch's colour) keeps the original blue.

Usage: python3 -I recolor_icons.py RES_DIR COLORS_XML
"""
import re
import sys
import numpy as np
from PIL import Image

RES, COLORS = sys.argv[1], sys.argv[2]
# Only the two largest: Android scales them down for other screens (saves ~0.8 MB).
DENSITIES = ["xxhdpi", "xxxhdpi"]
VARIANTS = {
    "red": "md_red_700", "pink": "md_pink_700", "purple": "md_purple_700",
    "deep_purple": "md_deep_purple_700", "indigo": "md_indigo_700", "blue": "md_blue_700",
    "light_blue": "md_light_blue_700", "cyan": "md_cyan_700", "teal": "md_teal_700",
    "light_green": "md_light_green_700", "lime": "md_lime_700", "yellow": "md_yellow_700",
    "amber": "md_amber_700", "orange": "md_orange_700", "deep_orange": "md_deep_orange_700",
    "brown": "md_brown_700", "blue_grey": "md_blue_grey_700", "grey_black": "md_grey_black",
}
# The original blue: typical hue, saturation and brightness of its bright parts.
REF_H, REF_S, REF_V = 221.6, 0.75, 0.85

xml = open(COLORS).read()
def color(name):
    value = re.search(rf'<color name="{name}">([^<]+)</color>', xml).group(1)
    return color(value[7:]) if value.startswith("@color/") else value

def rgb_to_hsv(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    d = np.maximum(mx - mn, 1e-6)
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    return h, s, mx

def hsv_to_rgb(h, s, v):
    h = (h % 360) / 60
    i = np.floor(h).astype(int) % 6
    f = h - np.floor(h)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    out = np.zeros(h.shape + (3,), np.float32)
    for k, (rr, gg, bb) in enumerate([(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)]):
        m = i == k
        out[m, 0], out[m, 1], out[m, 2] = rr[m], gg[m], bb[m]
    return out

for name, res in VARIANTS.items():
    hexv = color(res).lstrip("#")[-6:]
    target = np.array([int(hexv[i:i + 2], 16) for i in (0, 2, 4)], np.float32)[None, None] / 255
    th, ts, tv = (x.item() for x in rgb_to_hsv(target))
    if name == "grey_black":
        ts, tv = 0.0, 0.8  # silver eye rather than an all-black one
    for d in DENSITIES:
        src = Image.open(f"{RES}/drawable-{d}/vv_launcher_photo.webp").convert("RGB")
        a = np.asarray(src, np.float32) / 255
        h, s, v = rgb_to_hsv(a)
        # Only coloured, not-dark pixels change; the black pupil and shadows stay as they are.
        weight = np.clip((v - 0.06) / 0.08, 0, 1) * np.clip((s - 0.04) / 0.08, 0, 1)
        nh = th + (h - REF_H) * 0.5
        ns = np.clip(s * (ts / REF_S), 0, 1)
        nv = np.clip(v * (tv / REF_V), 0, 1)
        recolored = hsv_to_rgb(nh, ns, nv)
        out = a * (1 - weight[..., None]) + recolored * weight[..., None]
        # WebP keeps the 36 variants small (PNG would add several MB to the app).
        Image.fromarray((out * 255 + 0.5).astype(np.uint8)).save(
            f"{RES}/drawable-{d}/vv_launcher_photo_{name}.webp", quality=88, method=6)
    print(name, color(res))
