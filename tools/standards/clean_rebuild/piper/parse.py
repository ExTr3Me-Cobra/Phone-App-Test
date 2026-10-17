import re, json
def parse(fn, sex):
    L=open(fn).read().split('\n'); out=[]
    i=0
    while i<len(L):
        m=re.match(r'\s*Table\s+(\d+)\.\s+Percentile ranks and descriptive statistics for (.+?) (Pre|Post)-Training',L[i])
        if m:
            ex=m.group(2).strip(); phase=m.group(3)
            # header row with classes
            j=i+1
            while 'Percentile' not in L[j]: j+=1
            classes=re.findall(r'[<>]?\d+(?:<-\d+)?',L[j].replace('Percentile',''))
            rows={}
            k=j+1
            while k<len(L) and not re.match(r'\s*(Table|Copyright)',L[k]) and k<j+25:
                mm=re.match(r'\s*(\d+%|n|Mean|SD)\s+(.*)',L[k])
                if mm:
                    vals=[float(v) for v in re.findall(r'-?\d+(?:\.\d+)?',mm.group(2))]
                    rows[mm.group(1)]=vals
                k+=1
            out.append({'sex':sex,'exercise':ex,'phase':phase,'classes':classes,'rows':rows})
            i=k
        else: i+=1
    return out
d=parse('male.txt','M')+parse('female.txt','F')
for t in d:
    bad=[r for r,v in t['rows'].items() if len(v)!=len(t['classes'])]
    print(t['sex'],t['exercise'],t['phase'],len(t['classes']),t['classes'],'rows',len(t['rows']),'BAD' if bad else '',bad)
json.dump(d,open('piper_tables.json','w'),indent=1)
