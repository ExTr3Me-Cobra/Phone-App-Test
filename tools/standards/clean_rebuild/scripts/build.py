import json, math, re, csv
S='work/'
LB=2.20462
opl=[r[:4] for r in json.load(open(S+'opl_full_best.json'))]; owl=json.load(open(S+'owl_best.json'))
cur=json.load(open('/home/user/iron-log/app/src/main/assets/standards.json'))
def median(xs):
    xs=sorted(xs); n=len(xs); return xs[n//2] if n%2 else (xs[n//2-1]+xs[n//2])/2
def fit(rows, sex, lift):
    # median lift (lb) in 10-lb bodyweight bins, then a power-law fit: median = a * bw^b
    pts=[]
    for g in range(90,330,10):
        w=[r[2]*LB for r in rows if r[0]==sex and r[1]==lift and abs(r[3]*LB-g)<=5]
        if len(w)>=200: pts.append((g,median(w),len(w)))
    # Quadratic in log-log space (weighted by lifters per bin): follows the flattening at heavy bodyweights.
    import numpy as np
    X=np.log([g for g,_,_ in pts]); Y=np.log([m for _,m,_ in pts]); W=np.sqrt([n for *_,n in pts])
    c=np.polyfit(X,Y,2,w=W); lo,hi=min(g for g,_,_ in pts),max(g for g,_,_ in pts)
    return lambda bw: math.exp(np.polyval(c,math.log(min(max(bw,lo),hi))))*((bw/hi)**0.15 if bw>hi else 1)*((bw/lo)**0.9 if bw<lo else 1)
F={}
for s in 'MF':
    for l in 'SBD': F[s+l]=fit(opl,s,l)
    for l in ('SN','CJ'): F[s+l]=fit(owl,s,l)
# Anchors from open research: gym-goer median bench = 1.00 x BW for men at 180 lb (Tiggemann 2011, JSSM 2011,
# Rodriguez-Ridao 2020, Pareja-Blanco), 0.63 x BW for women at 132 lb (JSSM 2011).
K={'M': 180*1.00/F['MB'](180), 'F': 132*0.63/F['FB'](132)}
SIG={'M':0.30,'F':0.35}
Z=[-1.645,-0.842,0,0.842,1.645]
def table(sex, med):
    grid=range(110,320,10) if sex=='M' else range(90,270,10)
    return [[g]+[round(med(g)*math.exp(z*SIG[sex]),1) for z in Z] for g in grid]
def base(lift):
    return lambda sex: (lambda bw: K[sex]*F[sex+lift](bw))
B=base('B'); Sq=base('S'); D=base('D'); SN=base('SN'); CJ=base('CJ')
def scaled(f, r): return lambda sex: (lambda bw: f(sex)(bw)*(r if not isinstance(r,dict) else r[sex]))
NEW={
 'Barbell Bench Press': (B, 'OpenPowerlifting shape; level from open research'),
 'Barbell Back Squat': (Sq, 'OpenPowerlifting shape & squat:bench ratio; level from bench anchor'),
 'Barbell Deadlift': (D, 'OpenPowerlifting shape & deadlift:bench ratio'),
 'Barbell Sumo Deadlift': (D, 'OpenPowerlifting deadlift (includes sumo)'),
 'Trap Bar Deadlift': (scaled(D,1.04), 'deadlift x 1.04 (Swinton 2011; Camara)'),
 'Barbell Front Squat': (scaled(Sq,0.785), 'squat x 0.785 (Gullett 2009)'),
 'Barbell Incline Bench Press': (scaled(B,0.78), 'bench x 0.78 at 30 deg (Rodriguez-Ridao 2020, CC BY)'),
 'Dumbbell Bench Press': (scaled(B,0.83/2), 'bench x 0.83, per dumbbell (Jackson 2012; Saeterbakken 2011)'),
 'Incline Dumbbell Bench Press': (scaled(B,0.78*0.83/2), 'incline ratio x dumbbell ratio'),
 'Smith Machine Bench Press': (scaled(B,0.96), 'bench x 0.96 (Cotterman 2005; Saeterbakken 2011)'),
 'Sled Leg Press': (scaled(B,{'M':2.65,'F':4.5}), 'bench x 2.65 men / 4.5 women (Piper 2021/2022, n=1,466; Tiggemann 2011)'),
 'Cable Lat Pulldown': (scaled(B,{'M':0.93,'F':1.34}), 'bench x 0.93 men / 1.34 women (Piper et al. 2021/2022 college norms, n=1,466, CC BY)'),
 'Barbell Seated Overhead Press': (scaled(B,{'M':0.665,'F':0.79}), 'bench x 0.665 men / 0.79 women (Piper 2021/2022; Garcia-Ramos)'),
 'Barbell Standing Overhead Press': (scaled(B,{'M':0.665*0.92,'F':0.79*0.92}), 'seated press x 0.92 (Saeterbakken 2013 standing vs seated)'),
 'Seated Dumbbell Shoulder Press': (scaled(B,{'M':0.30,'F':0.35}), 'per dumbbell ~0.30 men / 0.35 women x bench (Saeterbakken 2013 chain; HK 6RM study) - low confidence'),
 'Barbell Preacher Curl': (scaled(B,{'M':0.47,'F':0.57}), 'bench x 0.47 men / 0.57 women (Piper 2021/2022)'),
 'Barbell Power Clean': (scaled(CJ,0.91), 'clean & jerk x 0.91 (power clean ~0.89 x clean; Czaplicki 2021)'),
 'Barbell Hang Clean': (scaled(CJ,0.96), 'clean & jerk x 0.96 (hang clean ~0.96 x clean; Czaplicki 2021)'),
 'Barbell Power Snatch': (scaled(SN,0.88), 'snatch x 0.88 (Czaplicki 2019, PLOS ONE, CC BY)'),
 'Barbell Push Press': (scaled(CJ,0.87), 'clean & jerk x 0.87 (push press vs split jerk, Soriano) - low confidence'),
 'Barbell Decline Bench Press': (scaled(B,1.0), 'bench x 1.00 (Saeterbakken 2017; Brazilian studies) - low confidence'),
 'Decline Dumbbell Bench Press': (scaled(B,0.83/2), 'dumbbell bench x 1.00 - low confidence'),
 'Barbell Close-Grip Bench Press': (scaled(B,0.93), 'bench x 0.93 (Larsen 2021, Frontiers, CC BY; Saeterbakken 2017)'),

 'Barbell Snatch': (SN, 'OpenWeightlifting shape; same gap below competitors as the big lifts'),
 'Barbell Clean and Jerk': (CJ, 'OpenWeightlifting shape; same gap below competitors as the big lifts'),
}
KEEP={'Hand Grip Dynamometer'}
REF={'M':180,'F':140}
def at(tab,bw):
    for r in tab:
        if r[0]==bw: return r[1:]
    lo=max([r for r in tab if r[0]<bw], key=lambda r:r[0]); hi=min([r for r in tab if r[0]>bw], key=lambda r:r[0])
    t=(bw-lo[0])/(hi[0]-lo[0]); return [a+(b-a)*t for a,b in zip(lo[1:],hi[1:])]
def factor(note):
    m=re.search(r'standard \((the same|[\d.]+)',note or '')
    return 1.0 if not m or m.group(1)=='the same' else float(m.group(1))
out=[]; tiers=['Wood 1','Wood 2','Wood 3','Bronze 1','Bronze 2','Bronze 3','Silver 1','Silver 2','Silver 3','Gold 1','Gold 2','Gold 3','Platinum 1','Platinum 2','Platinum 3','Diamond 1','Diamond 2','Diamond 3','Olympian','Legend']
def tier(p): return tiers[min(19,int(p//5))]
def Phi(z): return 0.5*(1+math.erf(z/math.sqrt(2)))
for x in cur:
    fam=x['ln'] or x['lift']; row={'exercise':x['lift'],'id':x['i'],'family':fam}
    if fam in KEEP: row['status']='kept'; out.append(row); continue
    if fam not in NEW or x.get('r'): row['status']='lost'; out.append(row); continue
    f,src=NEW[fam]; k=1.0  # tables are the base lift; the multiplier is applied in the app
    row['status']='rebuilt'; row['source']=src
    for s in 'MF':
        ct=x['m' if s=='M' else 'f']
        if not ct: continue
        c=at(ct,REF[s]); med=f(s)(REF[s])*k
        n=[med*math.exp(z*SIG[s]) for z in Z]
        row[s]={'cur':[round(v) for v in c],'new':[round(v) for v in n],
                'chg':[round(100*(b/a-1)) for a,b in zip(c,n)],
                'gold2_now':tier(100*Phi(math.log(c[2]/med)/SIG[s])), 'beginner_now':tier(100*Phi(math.log(c[0]/med)/SIG[s])), 'elite_now':tier(100*Phi(math.log(c[4]/med)/SIG[s]))}
    out.append(row)
json.dump(out,open(S+'compare.json','w'),indent=1)
import collections
print(collections.Counter(r['status'] for r in out))
seen=set()
for r in out:
    if r['status']!='rebuilt' or r['family'] in seen or r['family']!=r['exercise'] and r['exercise']!=r['family']: continue
for fam in NEW:
    r=next((r for r in out if r['family']==fam and r['status']=='rebuilt' and factor(next(x for x in cur if x['i']==r['id']).get('note'))==1.0),None)
    if not r: continue
    for s in 'MF':
        if s in r: print(f"{fam[:28]:28} {s} cur {r[s]['cur']} new {r[s]['new']} chg% {r[s]['chg']} | median lifter now -> {r[s]['gold2_now']}")
