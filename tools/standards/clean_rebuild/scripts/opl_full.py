import csv, glob, json, collections
f=glob.glob('opl/bulk/*/*.csv')[0]
best={}
n=0
with open(f,newline='',encoding='utf-8') as fh:
    for r in csv.DictReader(fh):
        n+=1
        if r['Equipment']!='Raw' or r['Sex'] not in ('M','F'): continue
        try: bw=float(r['BodyweightKg'])
        except: continue
        if bw<30: continue
        age=r['Age']
        for lift,col in (('S','Best3SquatKg'),('B','Best3BenchKg'),('D','Best3DeadliftKg')):
            try: v=float(r[col])
            except: continue
            if v<=0: continue
            k=(r['Name'],r['Sex'],lift)
            if k not in best or v>best[k][0]: best[k]=(v,bw,age,r['Tested']=='Yes')
rows=[[k[1],k[2],v[0],v[1],v[2],v[3]] for k,v in best.items()]
json.dump(rows,open('work/opl_full_best.json','w'))
print('entries read',n); print(collections.Counter((r[0],r[1]) for r in rows))
