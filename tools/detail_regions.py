"""Splits the traced muscle-group shapes (body_regions.py) into individual muscles/heads.
Shapes are for the image-left side and are mirrored in the app (except the neck)."""
import sys, os, json
sys.path.insert(0, os.path.dirname(__file__))
from body_regions import FRONT, BACK, FC, BC


def cr(P, n=6):
    """Catmull-Rom smoothing of a closed polygon into dense points."""
    out = []; m = len(P)
    for i in range(m):
        p0, p1, p2, p3 = P[(i - 1) % m], P[i], P[(i + 1) % m], P[(i + 2) % m]
        for k in range(n):
            t = k / n; t2 = t * t; t3 = t2 * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                   + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in (0, 1)))
    return out


def clip(poly, a, b, keep_left=True):
    """Sutherland-Hodgman: keep the part of poly on one side of the line a->b."""
    def side(p):
        v = (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        return v >= 0 if keep_left else v <= 0
    def inter(p, q):
        x1, y1 = p; x2, y2 = q; x3, y3 = a; x4, y4 = b
        d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if d == 0: return p
        t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d
        return (x1 + t * (x2 - x1), y1 + t * (y2 - y1))
    out = []
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        if side(q):
            if not side(p): out.append(inter(p, q))
            out.append(q)
        elif side(p):
            out.append(inter(p, q))
    return out

# Half-plane helpers (image y grows downward).
def above(y): return ((0, y), (2000, y), False)     # keep y < value
def below(y): return ((0, y), (2000, y), True)      # keep y > value
def leftof(a, b): return (a, b, True)               # keep the side toward smaller x for a downward line
def rightof(a, b): return (a, b, False)


def cut(poly, *planes):
    for a, b, k in planes:
        poly = clip(poly, a, b, k)
    return poly


def build():
    # Outlines hand-drawn in the Body Map Editor win over the generated ones below.
    edits = os.path.join(os.path.dirname(__file__), 'body_map_edits.json')
    if os.path.exists(edits):
        F, B = {}, {}
        for name, sides in json.load(open(edits))['muscles'].items():
            for side, polys in sides.items():
                for poly in polys:
                    if len(poly) >= 3:
                        (F if side == 'front' else B).setdefault(name, []).append([(round(x, 1), round(y, 1)) for x, y in cr([tuple(q) for q in poly])])
        return F, B
    F, B = {}, {}
    def add(d, name, poly):
        if len(poly) >= 3: d.setdefault(name, []).append([(round(x, 1), round(y, 1)) for x, y in poly])
    f = {k: [cr(s) for s in v] for k, v in FRONT.items()}
    b = {k: [cr(s) for s in v] for k, v in BACK.items()}
    # ---- front ----
    ch = f['chest'][0]
    add(F, 'chest (upper)', cut(ch, above(246)))
    add(F, 'chest (middle)', cut(ch, below(246), above(282)))
    add(F, 'chest (lower)', cut(ch, below(282)))
    sh = f['shoulders'][0]
    add(F, 'shoulders (front delt)', cut(sh, rightof((200, 205), (176, 300))))
    add(F, 'shoulders (side delt)', cut(sh, leftof((200, 205), (176, 300))))
    bi = f['biceps'][0]
    add(F, 'biceps (long head)', cut(bi, leftof((184, 268), (170, 362)), above(338)))
    add(F, 'brachialis', cut(bi, leftof((184, 268), (170, 362)), below(338)))
    add(F, 'biceps (short head)', cut(bi, rightof((184, 268), (170, 362))))
    add(F, 'triceps (lateral head)', f['triceps'][0])
    fo = f['forearms'][0]
    add(F, 'forearms (brachioradialis)', cut(fo, leftof((152, 352), (130, 455)), above(425)))
    add(F, 'forearms (wrist flexors)', cut(fo, rightof((152, 352), (130, 455))))
    add(F, 'forearms (wrist flexors)', cut(fo, leftof((152, 352), (130, 455)), below(425)))
    add(F, 'forearms (finger flexors)', f['grip'][0])
    ab = f['abdominals'][0]
    add(F, 'abs (upper)', cut(ab, above(397)))  # navel / lowest tendinous line
    add(F, 'abs (lower)', cut(ab, below(397)))
    add(F, 'obliques', f['abdominals'][1])
    add(F, 'serratus (serratus anterior)', f['serratus'][0])
    add(F, 'lats', f['lats'][0])
    q = f['quadriceps'][0]
    L1 = ((205, 455), (213, 645))
    add(F, 'quadriceps (vastus lateralis)', cut(q, leftof(*L1)))
    add(F, 'quadriceps (vastus medialis)', cut(q, rightof(*L1), below(565), rightof((232, 0), (232, 1000))))
    add(F, 'quadriceps (rectus femoris)', cut(q, rightof(*L1), above(565)))
    add(F, 'quadriceps (rectus femoris)', cut(q, rightof(*L1), below(565), leftof((232, 0), (232, 1000))))
    add(F, 'adductors (longus & brevis)', f['adductors'][0])
    add(F, 'abductors (TFL)', f['abductors'][0])
    add(F, 'calves (gastrocnemius)', f['calves'][0])
    add(F, 'calves (soleus)', f['calves'][1])
    add(F, 'shins (tibialis anterior)', cr([(204, 672), (218, 668), (222, 700), (218, 760), (210, 790), (204, 760), (202, 700)]))
    add(F, 'hip flexors (iliopsoas)', cr([(240, 452), (256, 458), (262, 474), (252, 486), (240, 474)]))
    add(F, 'traps (upper)', f['traps'][0])
    add(F, 'rotator cuff (subscapularis)', f['rotator cuff'][0])
    add(F, 'neck', f['neck'][0])
    # ---- back ----
    t = b['traps'][0]
    add(B, 'traps (upper)', cut(t, above(232)))
    add(B, 'traps (middle)', cut(t, below(232), above(275)))
    add(B, 'traps (lower)', cut(t, below(275)))
    s = b['shoulders'][0]
    add(B, 'shoulders (rear delt)', cut(s, rightof((692, 213), (672, 302))))
    add(B, 'shoulders (side delt)', cut(s, leftof((692, 213), (672, 302))))
    rc = b['rotator cuff']
    add(B, 'rotator cuff (supraspinatus)', rc[0])
    add(B, 'rotator cuff (infraspinatus)', rc[1])
    add(B, 'rotator cuff (teres minor)', rc[2])
    add(B, 'teres major', cr([(700, 300), (721, 300), (725, 316), (708, 322), (698, 312)]))
    add(B, 'middle back (rhomboids)', b['middle back'][0])
    add(B, 'lats', b['lats'][0])
    add(B, 'lower back (erector spinae)', b['lower back'][0])
    tr = b['triceps'][0]
    L2 = ((692, 280), (668, 345))
    add(B, 'triceps (long head)', cut(tr, rightof(*L2), above(342)))
    add(B, 'triceps (lateral head)', cut(tr, leftof(*L2), above(342)))
    add(B, 'triceps (medial head)', cut(tr, below(342)))
    add(B, 'forearms (wrist extensors)', b['forearms'][0])
    add(B, 'forearms (finger flexors)', b['grip'][0])
    add(B, 'glutes (maximus)', b['glutes'][0])
    add(B, 'abductors (glute medius)', b['abductors'][0])
    h = b['hamstrings'][0]
    add(B, 'hamstrings (biceps femoris)', cut(h, leftof((752, 520), (752, 670))))
    add(B, 'hamstrings (semitendinosus & semimembranosus)', cut(h, rightof((752, 520), (752, 670))))
    add(B, 'adductors (magnus)', b['adductors'][0])
    c = b['calves'][0]
    add(B, 'calves (gastrocnemius)', cut(c, above(745)))
    add(B, 'calves (soleus)', cut(c, below(745)))
    add(B, 'neck', b['neck'][0])
    return F, B

if __name__ == '__main__':
    F, B = build()
    print(len(F), len(B), sorted(set(F) | set(B)))
