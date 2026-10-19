"""Builds app/src/main/assets/exercises.json from the public-domain Free Exercise DB
(github.com/yuhonas/free-exercise-db, saved as free-exercise-db.json) plus extra_exercises.py.

- Adds three muscle groups: rotator cuff, serratus and grip, and moves exercises to them.
- Fills in secondary muscles the source leaves out, by movement pattern.
Run: python3 tools/build_exercises.py
"""
import json, re, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from extra_exercises import EXTRA
from cleanup import REMOVE, RENAME, REPS_ONLY, TIMED_ONLY, WEIGHTED, WEIGHT_TIME
from weights import RULES, DEFAULT, DEFAULT_REFS, FALLBACK, OVERRIDES, OVERRIDE_REFS
from detail_weights import GROUPS, HEADS, DEFAULT_HEADS, MIDDLE_BACK_EXTRA, ADD, REFS, ABS_SPLIT, RA

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'exercises.json')
MUSCLES = {'abdominals', 'abductors', 'adductors', 'biceps', 'calves', 'chest', 'forearms', 'glutes', 'hamstrings',
           'lats', 'lower back', 'middle back', 'neck', 'quadriceps', 'shoulders', 'traps', 'triceps',
           'rotator cuff', 'serratus', 'grip', 'hip flexors', 'shins'}
assert MUSCLES - {'grip'} == set(GROUPS), (MUSCLES - {'grip'}) ^ set(GROUPS)  # grip = the forearms' finger flexors
EQ = {'barbell': 'Barbell', 'dumbbell': 'Dumbbell', 'cable': 'Cable', 'machine': 'Machine', 'kettlebells': 'Kettlebell',
      'body only': 'Bodyweight', None: 'Other', 'other': 'Other', 'bands': 'Bands', 'medicine ball': 'Medicine Ball',
      'exercise ball': 'Exercise Ball', 'foam roll': 'Foam Roll', 'e-z curl bar': 'EZ Bar'}
TIMED = re.compile(r'plank|hold|wall sit|isometric|dead hang|l-sit|hollow|flexed-arm hang|static|carry|farmer', re.I)


def kind(cat, eq, name):
    if cat == 'cardio': return 'CARDIO'
    if cat == 'stretching' or eq == 'Foam Roll' or TIMED.search(name): return 'TIMED'
    if eq == 'Bodyweight' or (eq == 'Other' and cat == 'plyometrics'): return 'BODYWEIGHT'
    return 'WEIGHT'


# Movement patterns -> secondary muscles to add (name regex, required primary or None, muscles).
PATTERNS = [
    (r'bench press|chest press|floor press|push-?ups?\b|push up|dips?\b', None, ['triceps', 'shoulders', 'chest']),
    (r'push-?ups?\b|push up|pullover|incline.*press|military press|shoulder press|overhead press|push press|landmine|plank|rollout|ab wheel|bear crawl|scaption', None, ['serratus']),
    (r'fl(y|ye|yes)\b|crossover|pec deck|butterfly', 'chest', ['shoulders']),
    (r'shoulder press|military press|overhead press|push press|arnold|jerk', None, ['triceps', 'traps']),
    (r'lateral raise|front raise|upright row', None, ['traps']),
    (r'rear delt|reverse fl|face pull|band pull apart', None, ['middle back', 'traps', 'rotator cuff']),
    (r'(?<!upright )row\b|rows\b', None, ['biceps', 'middle back', 'forearms', 'grip', 'shoulders']),
    (r'pull-?ups?|chin-?ups?|pulldown|muscle up', None, ['biceps', 'middle back', 'forearms', 'grip']),
    (r'curl', 'biceps', ['forearms']),
    (r'leg curl|hamstring curl', 'hamstrings', ['calves']),
    (r'squat', None, ['glutes', 'hamstrings', 'adductors', 'lower back', 'calves']),
    (r'lunge|split squat|step-?up|step down', None, ['glutes', 'hamstrings', 'adductors', 'calves']),
    (r'leg press|hack squat', None, ['glutes', 'hamstrings', 'adductors']),
    (r'deadlift|rack pull', None, ['glutes', 'hamstrings', 'lower back', 'traps', 'forearms', 'grip']),
    (r'good morning|romanian|stiff-?legged', None, ['glutes', 'hamstrings', 'lower back']),
    (r'hip thrust|bridge', 'glutes', ['hamstrings', 'abdominals']),
    (r'hyperextension|back extension', None, ['glutes', 'hamstrings']),
    (r'shrug', None, ['forearms', 'grip']),
    (r'swing', None, ['hamstrings', 'lower back', 'shoulders', 'grip']),
    (r'\bclean\b|snatch', None, ['shoulders', 'traps', 'glutes', 'hamstrings', 'quadriceps', 'grip']),
    (r'farmer|carry|walk with|hang\b|hanging', None, ['grip', 'forearms']),
    (r'wrist curl|wrist roller|reverse curl|zottman|hammer', None, ['forearms', 'grip']),
    (r'kickback|skull|triceps extension|tricep extension|pushdown', 'triceps', ['forearms']),
    (r'crunch|sit-?up|situp|v-up|jackknife', 'abdominals', ['lats'] if False else []),
    (r'leg raise|knee raise|knee tuck', 'abdominals', ['quadriceps']),
    (r'calf raise|calf press', 'calves', []),
    (r'leg extension', 'quadriceps', []),
    (r'clamshell|abduction|monster walk', None, ['glutes']),
    (r'sit-?up|pull-in|leg tuck|knee tuck|tuck crunch|reverse crunch|butt-ups|hip raise|jackknife|v-up|cocoons|knee/hip raise|air bike|elbow to knee', 'abdominals', ['quadriceps']),
    (r'twist|side bend|oblique|judo flip|heel touch', 'abdominals', ['lower back']),
    (r'front .*raise|delt raise above head|raise over head', 'shoulders', ['chest', 'traps', 'serratus']),
    (r'deltoid raise|side laterals|power partials', 'shoulders', ['traps']),
    (r'reverse machine fl', None, ['middle back', 'traps', 'rotator cuff']),
    (r'triceps press|tricep press|press to chin', 'triceps', ['chest', 'shoulders']),
    (r'overhead triceps|triceps overhead|overhead extension|chain handle extension|body tricep', 'triceps', ['shoulders', 'forearms']),
    (r'neck', 'neck', ['traps']),
    (r'hip flexion', None, ['abdominals']),
    (r'slam', None, ['shoulders', 'abdominals', 'triceps']),
    (r'iron cross', None, ['shoulders']),
    (r'incline pushdown', None, ['triceps']),
    (r'pronation|supination|wrist rotation', None, ['grip']),
    (r'dead bug', None, ['lower back', 'quadriceps']),
    (r'calf raise|calf press|donkey calf', 'calves', []),
]


def fix(ex):
    name = ex['n'].lower()
    p, s = list(ex['p']), list(ex['s'])
    # Rotator cuff: rotation work is for the cuff, not the shoulders in general.
    if re.search(r'external rotation|internal rotation|cuban press', name) and 'with external' not in name:
        p = ['rotator cuff' if m == 'shoulders' else m for m in p]
        if 'shoulders' not in s: s.append('shoulders')
    if re.search(r'external rotation|face pull|cuban|scaption|band pull apart', name) and 'rotator cuff' not in p:
        s.append('rotator cuff')
    # Hip adduction works the adductors.
    if 'adduction' in name: p = ['adductors']
    # Grip: carries, pinches, finger work.
    if re.search(r"farmer|plate pinch|finger curl|gripper", name):
        p = ['grip' if m == 'forearms' else m for m in p]
        if 'forearms' not in p: s.append('forearms')
    if ex['c'] != 'stretching' and not ex['i'].startswith('x_'):
        for rx, need, add in PATTERNS:
            if re.search(rx, name) and (need is None or need in p):
                s += add
    # Clean up: no duplicates, nothing both primary and secondary, only known muscles.
    p = [m for i, m in enumerate(p) if m in MUSCLES and m not in p[:i]]
    s = [m for i, m in enumerate(s) if m in MUSCLES and m not in p and m not in s[:i]]
    ex['p'], ex['s'] = p, s
    return ex


src = json.load(open(os.path.join(HERE, 'free-exercise-db.json')))
out = []
for x in src:
    eq = EQ[x['equipment']]
    out.append(fix({'i': x['id'], 'n': x['name'], 'e': eq, 't': kind(x['category'], eq, x['name']),
                    'p': x['primaryMuscles'], 's': x['secondaryMuscles'], 'c': x['category'], 'h': x['instructions']}))
names = {e['n'].lower() for e in out}
added = 0
for (name, eq, t, p, s, cat, h) in EXTRA:
    if name.lower() in names: continue
    bad = [m for m in p + s if m not in MUSCLES]
    assert not bad, (name, bad)
    eid = 'x_' + re.sub(r'[^A-Za-z0-9]+', '_', name).strip('_')
    out.append(fix({'i': eid, 'n': name, 'e': eq, 't': t, 'p': p, 's': s, 'c': cat, 'h': h}))
    added += 1
def first_rule(e):
    """The first group-weight rule that fits this exercise: its muscles all count as helpers."""
    name = e['n'].lower()
    for rx, need, ws, refs in RULES:
        if re.search(rx, name) and (need is None or need in e['p']):
            return ws
    return {}


def weight(e, m):
    """Group weight for a secondary muscle, and the studies it comes from."""
    if m in OVERRIDES.get(e['n'], {}): return OVERRIDES[e['n']][m], OVERRIDE_REFS[e['n']]
    name = e['n'].lower()
    for rx, need, ws, refs in RULES:
        if m in ws and re.search(rx, name) and (need is None or need in e['p']):
            return ws[m], refs
    return DEFAULT.get(m, FALLBACK), DEFAULT_REFS


def heads(e, g):
    name = e['n'].lower()
    for rx, grp, dist, refs in HEADS:
        if grp == g and re.search(rx, name):
            return dist, refs
    return DEFAULT_HEADS[g]


def r05(v): return round(round(v * 20) / 20, 2)


for e in out:
    if e['c'] != 'stretching' and e['n'] not in OVERRIDES:
        e['s'] += [m for m in first_rule(e) if m not in e['p'] and m not in e['s']]
    refs = set()
    groups = [(g, 1.0) for g in e['p']]
    for m in e['s']:
        w, r = weight(e, m)
        groups.append((m, w)); refs.update(r)
    detail = {}
    def put(h, v):
        if v > detail.get(h, 0): detail[h] = v
    for g, gw in groups:
        dist, r = heads(e, g); refs.update(r)
        if RA in dist:
            name = e['n'].lower()
            split, r2 = next((sp, rr) for rx, sp, rr in ABS_SPLIT if re.search(rx, name))
            dist = {k: v for k, v in dist.items() if k != RA} | {h: dist[RA] * v for h, v in split.items()}
            refs.update(r2)
        for h, share in dist.items(): put(h, gw * share)
        if g == 'middle back':
            for h, share in MIDDLE_BACK_EXTRA[0].items(): put(h, gw * share)
            refs.update(MIDDLE_BACK_EXTRA[1])
    if e['c'] != 'stretching':
        name = e['n'].lower()
        for rx, need, add, r in ADD:
            if re.search(rx, name) and (need is None or need in e['p']):
                for h, v in add.items(): put(h, v)
                refs.update(r)
    detail = {h: r05(v) for h, v in detail.items() if r05(v) >= .05}
    e['m'] = detail
    # Grip is part of the forearms group.
    e['p'] = list(dict.fromkeys('forearms' if g == 'grip' else g for g in e['p']))
    # Secondary groups and their weights follow from the detailed muscles (busiest head of each group).
    gw = {}
    for h, v in detail.items():
        g = next(g for g, hs in GROUPS.items() if h in hs)
        if g not in e['p']: gw[g] = max(gw.get(g, 0), v)
    e['s'] = sorted(gw, key=lambda g: -gw[g])
    e['w'] = [gw[g] for g in e['s']]
    e['r'] = sorted(refs)
    assert all(k in REFS for k in refs), set(refs) - set(REFS)

names_now = {e['n'] for e in out}
missing = (REMOVE | set(RENAME)) - names_now
assert not missing, missing
for e in out:
    if e['n'] in RENAME: e['n'] = RENAME[e['n']]
    if e['n'] in REPS_ONLY: e['t'] = 'BODYWEIGHT'
    if e['n'] in TIMED_ONLY: e['t'] = 'TIMED'
    if e['n'] in WEIGHTED: e['t'] = 'WEIGHT'
    if e['n'] in WEIGHT_TIME: e['t'] = 'WEIGHT_TIME'
# Exercises that don't fit sets of weight and reps are left out of the app entirely.
out = [e for e in out if e['n'] not in REMOVE]
names_after = {e['n'] for e in out}
ALL_SET = REPS_ONLY | TIMED_ONLY | WEIGHTED | WEIGHT_TIME
assert not ALL_SET - names_after, ALL_SET - names_after
# No em dashes anywhere in the app: "x—y—z" becomes "x, y, z" and " — " becomes ", ".
def no_em(v):
    if isinstance(v, str): return v.replace(' — ', ', ').replace('—', ', ')
    if isinstance(v, list): return [no_em(x) for x in v]
    if isinstance(v, dict): return {k: no_em(x) for k, x in v.items()}
    return v
out = no_em(out)
json.dump(out, open(OUT, 'w'), separators=(',', ':'), ensure_ascii=False)
print('hidden:', sum(1 for e in out if e.get('x')), ' renamed:', len(RENAME), ' shown:', sum(1 for e in out if not e.get('x')))
from collections import Counter
print(f'{len(out)} exercises ({added} added)')
print('no secondary:', sum(1 for e in out if not e['s']), ' non-stretch no secondary:', sum(1 for e in out if not e['s'] and e['c'] != 'stretching'))
print('primary counts:', Counter(m for e in out for m in e['p']).most_common())
print('secondary new groups:', {g: sum(1 for e in out if g in e['s']) for g in ('serratus', 'rotator cuff', 'grip')})

import statistics
ws = [w for e in out for w in e['w']]
print('secondary weights: mean %.2f, min %.2f, max %.2f, count %d' % (statistics.mean(ws), min(ws), max(ws), len(ws)))
for n in ['Barbell Bench Press - Medium Grip', 'Incline Dumbbell Curl', 'Hanging Leg Raise', 'Crunches', 'Toe Raise', 'Seated Calf Raise', 'Lying Leg Curls', 'Side Lateral Raise', 'Push-Up Plus', 'Barbell Deadlift', 'Pullups', 'Barbell Squat', 'Dumbbell Bicep Curl', 'Standing Military Press', 'Bent Over Barbell Row', 'Pushups']:
    e = next(x for x in out if x['n'] == n)
    print(n, 'P:', e['p'], 'S:', dict(zip(e['s'], e['w'])))
    print('   ', sorted(e['m'].items(), key=lambda kv: -kv[1]), e['r'])
