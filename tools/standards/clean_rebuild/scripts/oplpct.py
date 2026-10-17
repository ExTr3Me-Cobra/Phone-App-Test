import json, sys
S='/tmp/claude-0/-home-user-Phone-App-Test/38839be0-c3cc-5779-aa49-91384a3b0295/scratchpad/work/'
rows=json.load(open(S+'opl_best.json'))
LB=2.20462
def pct(xs,p):
    xs=sorted(xs); i=(len(xs)-1)*p/100; lo=int(i); hi=min(lo+1,len(xs)-1); return xs[lo]+(xs[hi]-xs[lo])*(i-lo)
by={}
for sex,lift,kg,bw,age in rows: by.setdefault((sex,lift),[]).append((bw*LB,kg*LB))
res={}
for (sex,lift),L in by.items():
    grid=range(110,320,10) if sex=='M' else range(90,270,10)
    out=[]
    for g in grid:
        win=[v for b,v in L if abs(b-g)<=6]
        if len(win)<150: win=[v for b,v in L if abs(b-g)<=15]
        out.append([g,len(win)]+[round(pct(win,p),1) for p in (5,20,50,80,95)])
    res[f'{sex}{lift}']=out
json.dump(res,open(S+'opl_pct.json','w'))
cur=json.load(open('/home/user/iron-log/app/src/main/assets/standards.json'))
ids={'S':'Barbell Back Squat','B':'Barbell Bench Press','D':'Barbell Deadlift'}
for k,v in res.items():
    sex,lift=k[0],k[1]
    st=next(x for x in cur if (x['ln'] or x['lift'])==ids[lift])
    tab=st['m' if sex=='M' else 'f']
    for bwt in ((150,180,220) if sex=='M' else (130,160)):
        o=next(r for r in v if r[0]==bwt); c=next((r for r in tab if r[0]==bwt),None)
        print(k,bwt,'n',o[1],'OPL',o[2:],'CUR',c[1:] if c else None)
