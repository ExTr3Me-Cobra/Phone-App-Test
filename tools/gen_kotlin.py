"""Writes BodyMapData.kt (detailed muscle shapes) and Muscles.kt (groups, default head shares, references).
Run: python3 tools/gen_kotlin.py"""
import os, sys, json
sys.path.insert(0, os.path.dirname(__file__))
from detail_regions import build, FC, BC
from detail_weights import GROUPS, DEFAULT_HEADS, REFS

SRC = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'java', 'com', 'ironlog', 'app')
F, B = build()
names = set(F) | set(B)
heads = {h for hs in GROUPS.values() for h in hs}
assert names == heads, (names ^ heads)

def q(s): return json.dumps(s)
def num(v): return ('%.1f' % v).rstrip('0').rstrip('.') + 'f'

lines = []
for regs, cx, front in ((F, FC, 'true'), (B, BC, 'false')):
    for m in sorted(regs):
        for p in regs[m]:
            # Drop near-duplicate points to keep the file small.
            pts = [p[0]]
            for x, y in p[1:]:
                if abs(x - pts[-1][0]) + abs(y - pts[-1][1]) >= 1.5: pts.append((x, y))
            flat = ', '.join(num(v) for xy in pts for v in xy)
            mirror = 'null' if m == 'neck' else num(cx)
            lines.append(f'        Region({q(m)}, floatArrayOf({flat}), {mirror}, {front}),')
open(os.path.join(SRC, 'BodyMapData.kt'), 'w').write(f'''package com.ironlog.app

/**
 * Individual muscle shapes on the body picture (assets/body.jpg, 1082 x 976), made by
 * tools/gen_kotlin.py from the outlines in tools/body_regions.py split by tools/detail_regions.py.
 * Points are dense, so they're joined with straight lines. Each shape is mirrored to the other
 * side unless it sits on the centre line (neck).
 */
object BodyMapData {{
    const val W = 1082f
    const val H = 976f
    /** Crop boxes: left, top, right, bottom. */
    val frontBox = floatArrayOf(100f, 80f, 470f, 900f)
    val backBox = floatArrayOf(615f, 80f, 985f, 900f)
    val regions: List<Region> = listOf(
{chr(10).join(lines)}
    )
    class Region(val muscle: String, val pts: FloatArray, val mirrorX: Float?, val front: Boolean)
}}
''')

groups = '\n'.join(f'        {q(g)} to listOf({", ".join(q(h) for h in hs)}),' for g, hs in GROUPS.items())
defaults = '\n'.join(f'        {q(g)} to mapOf({", ".join(f"{q(h)} to {num(v)}" for h, v in d.items())}),'
                     for g, (d, _) in DEFAULT_HEADS.items())
refs = '\n'.join(f'        {q(k)} to {q(v)},' for k, v in sorted(REFS.items()))
open(os.path.join(SRC, 'Muscles.kt'), 'w').write(f'''package com.ironlog.app

/** Made by tools/gen_kotlin.py from tools/detail_weights.py – edit that and re-run. */
object Muscles {{
    /** Each muscle group (used for the list filters and the line under an exercise's name) and its individual muscles. */
    val groups: Map<String, List<String>> = linkedMapOf(
{groups}
    )

    /** Usual share of a group's work for each of its muscles, when nothing more specific is known. */
    val defaultShares: Map<String, Map<String, Float>> = mapOf(
{defaults}
    )

    /** The studies the percentages come from. */
    val refs: Map<String, String> = mapOf(
{refs}
    )

    private val groupOf: Map<String, String> = groups.flatMap {{ (g, hs) -> hs.map {{ it to g }} }}.toMap()

    /** Every individual muscle, grouped. */
    val all: List<String> = groups.values.flatten()

    fun group(muscle: String): String = groupOf[muscle] ?: muscle

    /** "biceps (long head)" -> "Biceps (Long Head)"; "middle back (rhomboids)" -> "Middle Back (Rhomboids)". */
    fun label(m: String): String = m.split(" ").joinToString(" ") {{ w ->
        val i = w.indexOfFirst {{ it.isLetter() }}
        if (i < 0 || w == "&" || w == "TFL") w else w.substring(0, i) + w[i].uppercase() + w.substring(i + 1)
    }}

    /** Group weights (old style: primary groups 1, secondary groups by weight) -> individual muscles. */
    fun expand(groupWeights: Map<String, Float>): Map<String, Float> {{
        val out = HashMap<String, Float>()
        for ((g, w) in groupWeights) {{
            val shares = defaultShares[g] ?: if (g in groupOf) mapOf(g to 1f) else continue
            for ((h, s) in shares) out[h] = maxOf(out[h] ?: 0f, w * s)
        }}
        return out
    }}
}}
''')
print(len(lines), 'regions')
