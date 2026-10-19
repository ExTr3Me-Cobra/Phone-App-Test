"""Bodyweight curves and age curve from OpenPowerlifting (public domain, https://openpowerlifting.gitlab.io/opl-csv/).

Input: the OpenPowerlifting bulk CSV (raw division only), reduced to each lifter's best squat, bench and deadlift.
Output: inputs/openpowerlifting_curves.json
  bodyweight: per sex and lift, the median 1RM (lb) vs bodyweight as a quadratic in log-log space: ln(median) = a*x^2 + b*x + c,
              x = ln(bodyweight lb) clamped to [lo, hi]; beyond the range x (bw/hi)^0.15 above and (bw/lo)^0.9 below.
              Only the shape is used by the standards.
  age:        per sex, strength relative to ages 25-34 (bodyweight controlled), averaged over the three lifts.
Also: snatch and clean & jerk curves from OpenWeightlifting (public domain, https://github.com/euanwm/OpenWeightlifting),
each lifter's best (adult categories), same fit.
Usage: python3 derive_openpowerlifting.py openpowerlifting.csv owl_best.json
  (or a pre-reduced best-lifts JSON: [[sex, lift, kg, bw_kg, age, tested], ...]; OpenWeightlifting: [[sex, lift, kg, bw_kg], ...])
"""
import csv, json, math, os, sys, statistics as st, collections
import numpy as np
LB = 2.20462

def best_lifts(path):
    if path.endswith('.json'): return json.load(open(path))
    best = {}
    with open(path, newline='', encoding='utf-8') as fh:
        for r in csv.DictReader(fh):
            if r['Equipment'] != 'Raw' or r['Sex'] not in ('M', 'F'): continue
            try: bw = float(r['BodyweightKg'])
            except ValueError: continue
            if bw < 30: continue
            for lift, col in (('S', 'Best3SquatKg'), ('B', 'Best3BenchKg'), ('D', 'Best3DeadliftKg')):
                try: v = float(r[col])
                except ValueError: continue
                if v <= 0: continue
                k = (r['Name'], r['Sex'], lift)
                if k not in best or v > best[k][0]: best[k] = (v, bw, r['Age'], r['Tested'] == 'Yes')
    return [[k[1], k[2], v[0], v[1], v[2], v[3]] for k, v in best.items()]

def median(xs):
    xs = sorted(xs); n = len(xs); return xs[n // 2] if n % 2 else (xs[n // 2 - 1] + xs[n // 2]) / 2

def curve(rows, sex, lift):
    pts = []
    for g in range(90, 330, 10):
        w = [r[2] * LB for r in rows if r[0] == sex and r[1] == lift and abs(r[3] * LB - g) <= 5]
        if len(w) >= 200: pts.append((g, median(w), len(w)))
    X = np.log([g for g, _, _ in pts]); Y = np.log([m for _, m, _ in pts]); W = np.sqrt([n for *_, n in pts])
    a, b, c = np.polyfit(X, Y, 2, w=W)
    return [round(float(a), 6), round(float(b), 6), round(float(c), 6), min(g for g, _, _ in pts), max(g for g, _, _ in pts)]

def age_curve(rows, sex):
    bins = collections.defaultdict(list)
    for s, l, kg, bw, age, _ in rows:
        if s == sex and age: bins[(l, round(bw / 5))].append(kg)
    med = {k: median(v) for k, v in bins.items() if len(v) >= 50}
    by = collections.defaultdict(list)
    for s, l, kg, bw, age, _ in rows:
        if s != sex or not age: continue
        m = med.get((l, round(bw / 5))); a = float(age)
        if not m or a < 13 or a > 90: continue
        b = int(a // 2 * 2) if a < 20 else int(a // 5 * 5)
        by[(l, b)].append(kg / m)
    peak = {l: st.mean([median(by[(l, b)]) for b in (25, 30)]) for l in 'SBD'}
    out = []
    for b in sorted({k[1] for k in by}):
        v = [median(by[(l, b)]) / peak[l] for l in 'SBD' if len(by[(l, b)]) >= 40]
        if len(v) >= 2: out.append([b + (1 if b < 20 else 2), round(min(1.0, st.mean(v)), 3)])   # bin midpoint, capped at the peak
    return out

if __name__ == '__main__':
    rows = best_lifts(sys.argv[1]); owl = json.load(open(sys.argv[2]))
    bw = {s + l: curve(rows, s, l) for s in 'MF' for l in 'SBD'}
    bw.update({s + l: curve(owl, s, l) for s in 'MF' for l in ('SN', 'CJ')})
    out = {'source': 'OpenPowerlifting bulk data, raw division, public domain (https://openpowerlifting.gitlab.io/opl-csv/); '
                     'OpenWeightlifting event data, public domain (https://github.com/euanwm/OpenWeightlifting)',
           'bodyweight': bw,
           'age': {s: age_curve(rows, s) for s in 'MF'}}
    json.dump(out, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'inputs', 'openpowerlifting_curves.json'), 'w'), indent=1)
    print(json.dumps(out['bodyweight'])); print(out['age'])
