import csv, sys, statistics
rows=[]
for f in sys.argv[1:]:
    with open(f) as fh:
        for r in csv.DictReader(fh):
            try:
                rows.append((float(r['age']), r['gender'], float(r['sit-ups counts'])))
            except: pass
print("n rows", len(rows))
def pct(v,p):
    v=sorted(v); k=(len(v)-1)*p/100; f=int(k); c=min(f+1,len(v)-1)
    return v[f]+(v[c]-v[f])*(k-f)
bands=[(20,29),(30,39),(40,49),(50,59),(60,64),(20,64)]
for sex in 'MF':
    for lo,hi in bands:
        v=[s for a,g,s in rows if g==sex and lo<=a<=hi]
        if not v: continue
        print(sex, f"{lo}-{hi}", "n=%d"%len(v), "mean=%.1f sd=%.1f"%(statistics.mean(v), statistics.stdev(v)), "min=%d max=%d"%(min(v),max(v)), " ".join("p%d=%.0f"%(p,pct(v,p)) for p in (5,20,50,80,95)))
ages=[a for a,_,_ in rows]; print("age range", min(ages), max(ages))
