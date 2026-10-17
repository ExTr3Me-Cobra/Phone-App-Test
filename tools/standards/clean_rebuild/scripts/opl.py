import csv, glob, json, sys, collections
root = sys.argv[1]; out = sys.argv[2]
best = {}  # (name, sex, lift) -> (kg, bwkg, age)
for f in glob.glob(root + '/meet-data/*/*/entries.csv'):
    with open(f, newline='', encoding='utf-8') as fh:
        for r in csv.DictReader(fh):
            if r.get('Equipment') != 'Raw': continue
            sex = r.get('Sex');
            if sex not in ('M', 'F'): continue
            try: bw = float(r.get('BodyweightKg') or 0)
            except: continue
            if bw <= 30: continue
            age = r.get('Age') or ''
            for lift, col in (('S', 'Best3SquatKg'), ('B', 'Best3BenchKg'), ('D', 'Best3DeadliftKg')):
                v = r.get(col)
                try: v = float(v)
                except: continue
                if v <= 0: continue
                k = (r['Name'], sex, lift)
                if k not in best or v > best[k][0]: best[k] = (v, bw, age)
rows = [[k[1], k[2], v[0], v[1], v[2]] for k, v in best.items()]
json.dump(rows, open(out, 'w'))
c = collections.Counter((r[0], r[1]) for r in rows); print(c)
