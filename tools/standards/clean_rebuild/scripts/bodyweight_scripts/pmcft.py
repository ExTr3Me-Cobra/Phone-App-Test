# fetch Europe PMC full text XML (or PMC html fallback) and print licence + tables + sentences matching keywords
import sys, re, html, subprocess, os
pmc=sys.argv[1]; kws=sys.argv[2].split("|") if len(sys.argv)>2 else []
maxtab=int(sys.argv[3]) if len(sys.argv)>3 else 6
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
d="/tmp/claude-0/-home-user-Phone-App-Test/38839be0-c3cc-5779-aa49-91384a3b0295/scratchpad/research/dl_pmc"
fn=f"{d}/{pmc}.xml"
if not os.path.exists(fn) or os.path.getsize(fn)<1000:
    subprocess.run(["curl","-sS","-L","-A",UA,"-m","90","-o",fn,f"https://www.ebi.ac.uk/europepmc/webservices/rest/{pmc}/fullTextXML"])
t=open(fn,errors="ignore").read()
if len(t)<1000:
    fn=f"{d}/{pmc}.html"
    subprocess.run(["curl","-sS","-L","-A",UA,"-m","90","-o",fn,f"https://pmc.ncbi.nlm.nih.gov/articles/{pmc}/"])
    t=open(fn,errors="ignore").read(); print("[used PMC html]")
def clean(s): return re.sub(r'\s+',' ',html.unescape(re.sub(r'<[^>]+>',' ',s))).strip()
title=re.search(r'<article-title>(.*?)</article-title>',t,re.S)
print("TITLE:",clean(title.group(1)) if title else None)
j=re.search(r'<journal-title>(.*?)</journal-title>',t,re.S); print("JOURNAL:",clean(j.group(1)) if j else None)
p=re.search(r'<permissions>(.*?)</permissions>',t,re.S); print("PERMISSIONS:",clean(p.group(1))[:500] if p else None)
lic=re.findall(r'xlink:href="(https?://creativecommons[^"]+)"',t); print("LICURL:",set(lic))
for i,tw in enumerate(re.findall(r'<table-wrap.*?</table-wrap>',t,re.S)[:maxtab]):
    tw=re.sub(r'</t[dh]>',' | ',tw); tw=re.sub(r'</tr>','\n',tw)
    print(f"\n=== TABLE {i+1}\n"+html.unescape(re.sub(r'<[^>]+>','',tw))[:4000])
body=clean(t)
for kw in kws:
    for m in list(re.finditer(kw,body,re.I))[:6]:
        print(f"\n[{kw}] ...{body[max(0,m.start()-300):m.start()+400]}...")
