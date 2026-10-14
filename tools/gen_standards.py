"""Builds app/src/main/assets/standards.json (read by StandardsData.kt) from the Strength Level tables collected in tools/standards.

Sources, merged per exercise and sex, row by row (body weight -> 5 levels):
- tools/standards/batch_*.json  first collection (rows at 150/180/220 lb men, 120/140/170 lb women)
- tools/standards/raw/*.json    second, wider collection ({"v": [...], "c": 1|2}; c=2 = seen twice)

Checks (a row that fails is dropped and reported):
- the 5 levels must rise left to right;
- the same row from two collections must agree within 7%, otherwise the one seen twice wins,
  or both are dropped;
- each level must follow the body-weight trend of the neighbouring rows (within 15% of the
  log-log line between them), which catches mis-read rows.
Exercises left with fewer than 2 rows for a sex get no table for that sex.
Run: python3 tools/gen_standards.py
"""
import json, os, glob, math
HERE = os.path.dirname(__file__)
import sys; sys.path.insert(0, HERE)
ASSET = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'standards.json')
db = {e['n']: e for e in json.load(open(os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'exercises.json'))) if not e.get('x')}

# Strength Level lift -> exercises in the app: (name, note); note says how it's related when it isn't the same lift.
MAP = {
    'Bench Press': [('Barbell Bench Press - Medium Grip', None), ('Bench Press - Powerlifting', None)],
    'Squat': [('Barbell Squat', None), ('Barbell Full Squat', None)],
    'Deadlift': [('Barbell Deadlift', None)],
    'Shoulder Press': [('Standing Military Press', None)],
    'Bent Over Row': [('Bent Over Barbell Row', None)],
    'Front Squat': [('Front Barbell Squat', None), ('Front Squat (Clean Grip)', None)],
    'Incline Bench Press': [('Barbell Incline Bench Press - Medium Grip', None)],
    'Romanian Deadlift': [('Romanian Deadlift', None)],
    'Hip Thrust': [('Barbell Hip Thrust', None)],
    'Power Clean': [('Power Clean', None)],
    'Dumbbell Bench Press': [('Dumbbell Bench Press', None)],
    'Dumbbell Row': [('One-Arm Dumbbell Row', None)],
    'Dumbbell Fly': [('Dumbbell Flyes', None)],
    'Dumbbell Lateral Raise': [('Side Lateral Raise', None)],
    'Dumbbell Curl': [('Dumbbell Bicep Curl', None), ('Dumbbell Alternate Bicep Curl', None)],
    'Hammer Curl': [('Hammer Curls', None), ('Alternate Hammer Curl', None)],
    'Incline Dumbbell Bench Press': [('Incline Dumbbell Press', None)],
    'Dumbbell Shrug': [('Dumbbell Shrug', None)],
    'Goblet Squat': [('Goblet Squat', None)],
    'Dumbbell Lunge': [('Dumbbell Lunges', None)],
    'Dumbbell Bulgarian Split Squat': [('Split Squat with Dumbbells', None)],
    'Barbell Curl': [('Barbell Curl', None)],
    'Preacher Curl': [('Preacher Curl', None)],
    'EZ Bar Curl': [('EZ-Bar Curl', None)],
    'Lying Tricep Extension': [('Lying Triceps Press', None), ('EZ-Bar Skullcrusher', None)],
    'Close Grip Bench Press': [('Close-Grip Barbell Bench Press', None)],
    'Barbell Shrug': [('Barbell Shrug', None)],
    'Calf Raise': [('Standing Calf Raises', None)],
    'Decline Bench Press': [('Decline Barbell Bench Press', None)],
    'Barbell Lunge': [('Barbell Lunge', None)],
    'Pull Ups': [('Pullups', None)],
    'Chin Ups': [('Chin-Up', None)],
    'Push Ups': [('Pushups', None)],
    'Dips': [('Dips - Triceps Version', None), ('Parallel Bar Dip', None), ('Dips - Chest Version', None)],
    'Lat Pulldown': [('Wide-Grip Lat Pulldown', None), ('Medium-Grip Lat Pulldown', None),
                     ('Close-Grip Front Lat Pulldown', 'Close variant: grip width changes pulldown strength little (Andersen 2014).'),
                     ('Medium-Grip Neutral Lat Pulldown', 'Close variant: grip width/orientation changes pulldown strength little (Andersen 2014, Lusk 2010).')],
    'Seated Cable Row': [('Seated Cable Rows', None)],
    'Leg Press': [('Leg Press', None)],
    'Leg Extension': [('Leg Extensions', None)],
    'Lying Leg Curl': [('Lying Leg Curls', None)],
    'Seated Leg Curl': [('Seated Leg Curl', None)],
    'Tricep Pushdown': [('Triceps Pushdown', None), ('Triceps Pushdown - V-Bar Attachment', 'Close variant of the straight-bar pushdown.')],
    'Machine Chest Press': [('Leverage Chest Press', None)],
    'Machine Shoulder Press': [('Machine Shoulder (Military) Press', None), ('Leverage Shoulder Press', None)],
    'Seated Calf Raise': [('Seated Calf Raise', None)],
    'Cable Fly': [('Cable Crossover', None)],
    # Added in the wider collection.
    'Seated Shoulder Press': [('Seated Barbell Military Press', None), ('Barbell Shoulder Press', None)],
    'Seated Dumbbell Shoulder Press': [('Dumbbell Shoulder Press', None)],
    'Smith Machine Bench Press': [('Smith Machine Bench Press', None)],
    'Smith Machine Squat': [('Smith Machine Squat', None)],
    'Smith Machine Incline Bench Press': [('Smith Machine Incline Bench Press', None)],
    'Hip Abduction': [('Hip Abduction (Machine)', None)],
    'Hip Adduction': [('Hip Adduction (Machine)', None)],
    'Face Pull': [('Face Pull', None)],
    'Upright Row': [('Upright Barbell Row', None)],
    'Arnold Press': [('Arnold Dumbbell Press', None)],
    'T-Bar Row': [('T-Bar Row with Handle', None)],
    'Good Morning': [('Good Morning', None)],
    'Sumo Deadlift': [('Sumo Deadlift', None)],
    'Box Squat': [('Box Squat', None)],
    'Rack Pull': [('Rack Pulls', None)],
    'Trap Bar Deadlift': [('Trap Bar Deadlift', None)],
    'Deficit Deadlift': [('Deficit Deadlift', None)],
    'Zercher Squat': [('Zercher Squats', None)],
    'Overhead Squat': [('Overhead Squat', None)],
    'Clean and Jerk': [('Clean and Jerk', None)],
    'Snatch': [('Snatch', None)],
    'Hang Clean': [('Hang Clean', None)],
    'Power Snatch': [('Power Snatch', None)],
    'Push Press': [('Push Press', None)],
    'Hack Squat': [('Hack Squat', None)],
    'Tricep Rope Pushdown': [('Triceps Pushdown - Rope Attachment', None)],
    'Dumbbell Tricep Extension': [('Standing Dumbbell Triceps Extension', None)],
    'Dumbbell Pullover': [('Straight-Arm Dumbbell Pullover', None), ('Bent-Arm Dumbbell Pullover', None)],
    'Dumbbell Front Raise': [('Front Dumbbell Raise', None), ('Front Two-Dumbbell Raise', None)],
    'Reverse Barbell Curl': [('Reverse Barbell Curl', None)],
    'Wrist Curl': [('Palms-Up Barbell Wrist Curl Over A Bench', None), ('Seated Palm-Up Barbell Wrist Curl', None)],
    'Incline Dumbbell Curl': [('Incline Dumbbell Curl', None), ('Alternate Incline Dumbbell Curl', None)],
    'Concentration Curl': [('Concentration Curls', None)],
    'Cable Bicep Curl': [('Standing Biceps Cable Curl', None)],
    'Machine Bicep Curl': [('Machine Bicep Curl', None)],
    'Reverse Dumbbell Fly': [('Reverse Flyes', None)],
    'Machine Reverse Fly': [('Reverse Machine Flyes', None)],
    'Machine Fly': [('Butterfly', None)],
    'Dumbbell Squat': [('Dumbbell Squat', None)],
    'Stiff Leg Deadlift': [('Stiff-Legged Barbell Deadlift', None)],
    'Dumbbell Romanian Deadlift': [('Stiff-Legged Dumbbell Deadlift', 'Close variant: the dumbbell stiff-legged deadlift is ranked with the dumbbell Romanian deadlift standard.')],
    'Bent Over Dumbbell Row': [('Bent Over Two-Dumbbell Row', None), ('Bent Over Two-Dumbbell Row With Palms In', None)],
    'Decline Dumbbell Bench Press': [('Decline Dumbbell Bench Press', None)],
    'Barbell Glute Bridge': [('Barbell Glute Bridge', None)],
    'Cable Crunch': [('Cable Crunch', None)],
    'Muscle Ups': [('Muscle Up', None)],
    'Sit Ups': [('Sit-Up', None)],
    'Inverted Row': [('Inverted Row', None)],
    'Handstand Push Ups': [('Handstand Push-Ups', None)],
}
DUMBBELL = {'Dumbbell Bench Press', 'Dumbbell Row', 'Dumbbell Fly', 'Dumbbell Lateral Raise', 'Dumbbell Curl', 'Hammer Curl',
            'Incline Dumbbell Bench Press', 'Dumbbell Shrug', 'Dumbbell Lunge', 'Dumbbell Bulgarian Split Squat',
            'Seated Dumbbell Shoulder Press', 'Arnold Press', 'Dumbbell Front Raise', 'Incline Dumbbell Curl', 'Concentration Curl',
            'Reverse Dumbbell Fly', 'Dumbbell Squat', 'Dumbbell Romanian Deadlift', 'Bent Over Dumbbell Row', 'Decline Dumbbell Bench Press'}

report = []

# Left out entirely.
EXCLUDE = {'Kettlebell Swing': 'no matching exercise', 'Dumbbell Romanian Deadlift': 'unclear if per dumbbell or both'}
# Usable but less certain data: these rank only as estimates (silver-blue ★, All on mode).
APPROX = {'Hip Abduction': 'Strength Level shows two different versions of this page.',
          'Hack Squat': 'Strength Level shows two different versions of this page.',
          'Inverted Row': 'Only heavier body weights were found (210 lb and up).',
          'Sit Ups': 'Sparse rows; which table is men\'s or women\'s was guessed.',
          'Handstand Push Ups': 'The women\'s table was guessed and some men\'s values were not per row.',
          'Barbell Glute Bridge': 'Rows were pieced together from separate snippets.'}
from derived_standards import DERIVED
# Single rows found to be copies of another exercise's row.
DROP_ROWS = {('Preacher Curl', 'male', 110.0): 'identical to the barbell curl row'}

def rising(v):
    """Levels rise left to right ("< 1 rep" cells are 0, so rep tables may start 0, 0)."""
    k = 0
    while k < 2 and v[k] == 0: k += 1
    return all(v[i] < v[i + 1] for i in range(max(k - 1, 0), 4)) and (k < 2 or v[2] > 0)


def load():
    data = {}  # lift -> {'unit','male':{bw: [(v, c, src)]}, 'female':...}
    def add(lift, sex, bw, v, c, src):
        if v is None or len(v) != 5 or any(x is None for x in v): return
        v = [float(x) for x in v]
        if not rising(v):
            report.append(f'{lift} {sex} {bw}: levels not rising {v} ({src}) – dropped'); return
        data.setdefault(lift, {'unit': 'lb', 'male': {}, 'female': {}})[sex].setdefault(float(bw), []).append((v, c, src))
    for f in sorted(glob.glob(os.path.join(HERE, 'standards', 'batch_*.json'))):
        for lift, s in json.load(open(f)).items():
            if lift.startswith('_') or s.get('confidence') == 'low': continue
            if s.get('reps'): data.setdefault(lift, {'unit': 'reps', 'male': {}, 'female': {}})['unit'] = 'reps'
            for sex in ('male', 'female'):
                for bw, v in (s.get(sex) or {}).items(): add(lift, sex, bw, v, 1, 'first')
    for f in sorted(glob.glob(os.path.join(HERE, 'standards', 'raw', '*.json'))):
        try: raw = json.load(open(f))
        except Exception as ex: report.append(f'{f}: unreadable ({ex})'); continue
        for lift, s in raw.items():
            if lift.startswith('_') or not isinstance(s, dict): continue
            lift = lift.split(' (')[0]  # "Hip Abduction (machine)" -> "Hip Abduction"
            if s.get('unit') == 'reps': data.setdefault(lift, {'unit': 'reps', 'male': {}, 'female': {}})['unit'] = 'reps'
            for sex in ('male', 'female'):
                for bw, row in (s.get(sex) or {}).items():
                    if isinstance(row, dict): add(lift, sex, bw, row.get('v'), int(row.get('c', 1)), os.path.basename(f))
                    else: add(lift, sex, bw, row, 1, os.path.basename(f))
    return data


def merge(lift, sex, rows):
    out = {}
    for bw, cands in rows.items():
        if len(cands) == 1: out[bw] = cands[0][0]; continue
        base = cands[0][0]
        if all(max(abs(a - b) / max(b, 1) for a, b in zip(v, base)) <= 0.07 for v, _, _ in cands):
            out[bw] = max(cands, key=lambda t: t[1])[0]
        else:
            twice = [cd for cd in cands if cd[1] >= 2]
            if len(twice) == 1: out[bw] = twice[0][0]; report.append(f'{lift} {sex} {bw:g}: sources disagree, kept the one seen twice')
            else: report.append(f'{lift} {sex} {bw:g}: sources disagree {[cd[0] for cd in cands]} – dropped')
    return out


def trend_check(lift, sex, rows, reps):
    """Drop rows that break the body-weight trend of their neighbours (worst first)."""
    while len(rows) >= 3:
        bws = sorted(rows)
        worst, worst_err = None, 1.0
        for i in range(1, len(bws) - 1):
            a, b, c = bws[i - 1], bws[i], bws[i + 1]
            t = (math.log(b) - math.log(a)) / (math.log(c) - math.log(a))
            err = 0
            for k in range(5):
                va, vb, vc = (max(rows[x][k], 0.5) for x in (a, b, c))
                pred = math.exp(math.log(va) + t * (math.log(vc) - math.log(va)))
                tol = 0.15 if not reps or pred >= 15 else 2.5 / pred  # small rep counts: allow ±2.5 reps
                err = max(err, abs(vb - pred) / pred / tol)
            if err > worst_err: worst, worst_err = b, err
        if worst is None: break
        report.append(f'{lift} {sex} {worst:g}: off-trend {rows[worst]} – dropped')
        del rows[worst]
    return rows


data = load()
entries, used, lifts_used, coverage = [], 0, 0, {}
tables_by_lift = {}
for lift, exs in MAP.items():
    if lift not in data: report.append(f'{lift}: no data – not ranked'); continue
    if lift in EXCLUDE: report.append(f'{lift}: left out ({EXCLUDE[lift]})'); continue
    if lift in APPROX: report.append(f'{lift}: estimate only ({APPROX[lift]})')
    d = data[lift]; reps = d['unit'] == 'reps'
    tables = {}
    for sex in ('male', 'female'):
        merged = merge(lift, sex, d[sex])
        for (l, sx, bw), why in DROP_ROWS.items():
            if l == lift and sx == sex and bw in merged: del merged[bw]; report.append(f'{lift} {sex} {bw:g}: {why} – dropped')
        rows = trend_check(lift, sex, merged, reps)
        if len(rows) >= 2: tables[sex] = dict(sorted(rows.items()))
        elif rows: report.append(f'{lift} {sex}: only one row – not enough to rank')
    if not tables: report.append(f'{lift}: no usable rows'); continue
    lifts_used += 1
    coverage[lift] = {s: [min(t), max(t), len(t)] for s, t in tables.items()}
    for name, note in exs:
        if name not in db: report.append(f'{lift}: exercise "{name}" not in the database'); continue
        def rows_of(sex):
            t = tables.get(sex)
            return [[bw] + v for bw, v in t.items()] if t else None
        entries.append({'i': db[name]['i'], 'lift': lift, 'm': rows_of('male'), 'f': rows_of('female'),
                        'r': reps, 'd': lift in DUMBBELL, 'note': note, 'k': 1.0, 'a': APPROX.get(lift)})
        used += 1
    tables_by_lift[lift] = (tables, reps)

# Similar exercises ranked from a lift's standard × a factor.
have = {e['i'] for e in entries}
derived = 0
for name, lift, k, same in DERIVED:
    e = db.get(name)
    if e is None or e['i'] in have: continue
    if lift not in tables_by_lift: report.append(f'{name}: base lift {lift} has no data'); continue
    tables, reps = tables_by_lift[lift]
    if reps != (e['t'] == 'BODYWEIGHT'): report.append(f'{name}: logged differently from {lift} – skipped'); continue
    rows = lambda sex: [[bw] + v for bw, v in tables[sex].items()] if sex in tables else None
    how = 'the same' if abs(k - 1) < 0.04 else f'{k:g}×'
    note = f'Ranked with the Strength Level "{lift}" standard ({how}' + (' per dumbbell' if e['e'] == 'Dumbbell' and k < 0.6 else '') + ').'
    entries.append({'i': e['i'], 'lift': lift, 'm': rows('male'), 'f': rows('female'), 'r': reps,
                    'd': e['e'] == 'Dumbbell' and lift not in ('Goblet Squat', 'Dumbbell Tricep Extension'), 'note': note, 'k': k,
                    'a': None if same and not APPROX.get(lift) else (APPROX.get(lift) or 'A similar exercise\'s standard, scaled – an estimate.')})
    have.add(e['i']); derived += 1
print('derived:', derived)
json.dump(entries, open(ASSET, 'w'), separators=(',', ':'))
json.dump(coverage, open(os.path.join(HERE, 'standards', 'coverage.json'), 'w'), indent=1)
open(os.path.join(HERE, 'standards', 'report.txt'), 'w').write('\n'.join(report) + '\n')
print(f'{used} exercises ranked from {lifts_used} lifts; {len(report)} report lines (tools/standards/report.txt)')
