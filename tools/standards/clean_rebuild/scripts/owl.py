import csv, glob, json, re
best={}
for f in glob.glob('opl/owl/event_data/*/*.csv'):
    for r in csv.DictReader(open(f, newline='', encoding='utf-8', errors='ignore')):
        cat=(r.get('age_category') or '').lower()
        sex='F' if re.search(r"women|female|ladies|girls|\bw\d", cat) else ('M' if re.search(r"\bmen|male|boys|\bm\d", cat) else None)
        if not sex or re.search(r'youth|u1[0-7]|school|13|14|15|16', cat): continue
        try: bw=float(r['body_weight_(kg)'])
        except: continue
        if bw<35: continue
        for lift,col in (('SN','best_snatch'),('CJ','best_c&j')):
            try: v=float(r[col])
            except: continue
            if v<=0: continue
            k=(r['lifter'].strip().lower(),sex,lift)
            if k not in best or v>best[k][0]: best[k]=(v,bw)
rows=[[k[1],k[2],v[0],v[1]] for k,v in best.items()]
json.dump(rows,open('work/owl_best.json','w'))
import collections; print(collections.Counter((r[0],r[1]) for r in rows))
