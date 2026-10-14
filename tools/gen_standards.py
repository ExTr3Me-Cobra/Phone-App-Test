"""Builds Standards data (StandardsData.kt) from the Strength Level tables in tools/standards/*.json.
Levels: Beginner/Novice/Intermediate/Advanced/Elite = stronger than 5/20/50/80/95% of lifters.
Only medium/high-confidence tables are used. Run: python3 tools/gen_standards.py"""
import json, os, glob
HERE = os.path.dirname(__file__)
data = {}
for f in sorted(glob.glob(os.path.join(HERE, 'standards', '*.json'))):
    data.update(json.load(open(f)))
db = {e['n']: e for e in json.load(open(os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'exercises.json'))) if not e.get('x')}

# Standard -> exercises in the app. (name, note) note = None for the same lift, or how it's related.
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
}
def kt(v): return '%.1f' % v
rows, used, skipped = [], 0, []
for std, exs in MAP.items():
    s = data[std]
    if s['confidence'] == 'low': skipped.append(std); continue
    def rowset(d):
        if not d: return 'null'
        return 'listOf(' + ', '.join(f'{kt(float(bw))} to doubleArrayOf({", ".join(kt(float(x if x is not None else 0)) for x in lv)})' for bw, lv in sorted(d.items(), key=lambda kv: float(kv[0]))) + ')'
    for name, note in exs:
        assert name in db, name
        e = db[name]
        dumb = bool(s.get('per_dumbbell'))
        rows.append(f'        Std({json.dumps(e["i"])}, {json.dumps(std)}, {rowset(s["male"])}, {rowset(s.get("female"))}, '
                    f'reps = {"true" if s.get("reps") else "false"}, perDumbbell = {"true" if dumb else "false"}, '
                    f'source = {json.dumps("https://" + s["source"])}, note = {json.dumps(note)}),')
        used += 1
out = os.path.join(HERE, '..', 'app', 'src', 'main', 'java', 'com', 'ironlog', 'app', 'StandardsData.kt')
open(out, 'w').write('''package com.ironlog.app

/**
 * Strength standards from Strength Level (strengthlevel.com, built from lifts logged by its users),
 * one-rep max in lb (or reps for bodyweight moves) for Beginner / Novice / Intermediate / Advanced / Elite
 * = stronger than 5 / 20 / 50 / 80 / 95% of lifters, at several body weights.
 * Made by tools/gen_standards.py from the JSON files in tools/standards – edit those and re-run.
 */
object StandardsData {
    class Std(
        val id: String, val lift: String,
        val male: List<Pair<Double, DoubleArray>>?, val female: List<Pair<Double, DoubleArray>>?,
        val reps: Boolean, val perDumbbell: Boolean, val source: String, val note: String?,
    )

    val all: List<Std> = listOf(
''' + '\n'.join(rows) + '''
    )
}
''')
print(used, 'exercises ranked from', len(MAP) - len(skipped), 'standards; skipped low confidence:', skipped)
