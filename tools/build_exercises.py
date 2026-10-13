"""Builds app/src/main/assets/exercises.json from the public-domain Free Exercise DB
(github.com/yuhonas/free-exercise-db, saved as free-exercise-db.json) plus extra_exercises.py.

- Adds three muscle groups: rotator cuff, serratus and grip, and moves exercises to them.
- Fills in secondary muscles the source leaves out, by movement pattern.
Run: python3 tools/build_exercises.py
"""
import json, re, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from extra_exercises import EXTRA
from cleanup import REMOVE, RENAME
from weights import RULES, DEFAULT, FALLBACK

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'exercises.json')
MUSCLES = {'abdominals', 'abductors', 'adductors', 'biceps', 'calves', 'chest', 'forearms', 'glutes', 'hamstrings',
           'lats', 'lower back', 'middle back', 'neck', 'quadriceps', 'shoulders', 'traps', 'triceps',
           'rotator cuff', 'serratus', 'grip'}
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
    if ex['c'] != 'stretching':
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
def weight(e, m):
    name = e['n'].lower()
    for rx, need, ws in RULES:
        if m in ws and re.search(rx, name) and (need is None or need in e['p']):
            return ws[m]
    return DEFAULT.get(m, FALLBACK)

for e in out:
    e['w'] = [weight(e, m) for m in e['s']]

names_now = {e['n'] for e in out}
missing = (REMOVE | set(RENAME)) - names_now
assert not missing, missing
for e in out:
    if e['n'] in REMOVE: e['x'] = 1
    if e['n'] in RENAME: e['n'] = RENAME[e['n']]
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
for n in ['Barbell Bench Press - Medium Grip', 'Barbell Deadlift', 'Pullups', 'Barbell Squat', 'Dumbbell Bicep Curl', 'Standing Military Press', 'Bent Over Barbell Row', 'Pushups']:
    e = next(x for x in out if x['n'] == n)
    print(n, 'P:', e['p'], 'S:', dict(zip(e['s'], e['w'])))
