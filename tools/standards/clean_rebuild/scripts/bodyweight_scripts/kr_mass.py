import csv, sys, statistics, math
rows=[]
for f in sys.argv[1:]:
    for r in csv.DictReader(open(f)):
        try: rows.append(dict(age=float(r['age']),sex=r['gender'],h=float(r['height_cm']),w=float(r['weight_kg']),bf=float(r['body fat_%']),s=float(r['sit-ups counts'])))
        except: pass
def pct(v,p):
    v=sorted(v); k=(len(v)-1)*p/100; f=int(k); c=min(f+1,len(v)-1); return v[f]+(v[c]-v[f])*(k-f)
try:
    import numpy as np
except ImportError:
    np=None
for sex,bins in (('M',[(0,60),(60,70),(70,80),(80,90),(90,200)]),('F',[(0,50),(50,55),(55,60),(60,70),(70,200)])):
    for alo,ahi in ((20,39),(40,64)):
        print(f"\n{sex} age {alo}-{ahi}: body mass bins")
        for lo,hi in bins:
            v=[x['s'] for x in rows if x['sex']==sex and alo<=x['age']<=ahi and lo<=x['w']<hi]
            if len(v)<30: continue
            print(f"  {lo}-{hi}kg n={len(v)} mean={statistics.mean(v):.1f} p20={pct(v,20):.0f} p50={pct(v,50):.0f} p80={pct(v,80):.0f}")
    # regression: s ~ age + log(w) and with bodyfat
    sub=[x for x in rows if x['sex']==sex and x['s']>0]
    if np is not None:
        X=np.array([[1,x['age'],math.log(x['w'])] for x in sub]); y=np.array([math.log(x['s']) for x in sub])
        b,*_=np.linalg.lstsq(X,y,rcond=None); print(f"{sex} log(situps)= {b[0]:.3f} + {b[1]:.4f}*age + {b[2]:.3f}*ln(mass)  n={len(sub)}")
        X2=np.array([[1,x['age'],math.log(x['w']),x['bf']] for x in sub]); b2,*_=np.linalg.lstsq(X2,y,rcond=None)
        print(f"{sex} with bodyfat: age {b2[1]:.4f}, ln(mass) {b2[2]:.3f}, bf% {b2[3]:.4f}")
        lean=[x for x in sub]; X3=np.array([[1,x['age'],math.log(x['w']*(1-x['bf']/100)),math.log(x['w'])] for x in sub]); b3,*_=np.linalg.lstsq(X3,y,rcond=None)
        print(f"{sex} ln(lean mass) {b3[2]:.3f}, ln(total mass) {b3[3]:.3f}")
    ws=[x['w'] for x in rows if x['sex']==sex]; print(sex,"mass mean %.1f sd %.1f"%(statistics.mean(ws),statistics.stdev(ws)))
