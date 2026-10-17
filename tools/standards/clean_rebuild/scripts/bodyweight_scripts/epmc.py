import json, sys, urllib.parse, subprocess
q=sys.argv[1]; n=sys.argv[2] if len(sys.argv)>2 else "15"
url="https://www.ebi.ac.uk/europepmc/webservices/rest/search?query="+urllib.parse.quote(q)+"&format=json&resultType=core&pageSize="+n
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
out=subprocess.run(["curl","-sS","-L","-A",UA,"-m","60",url],capture_output=True,text=True).stdout
d=json.loads(out)
print("hits",d.get("hitCount"))
for r in d["resultList"]["result"]:
    ab=(r.get("abstractText") or "")
    print("-",r.get("pmcid"),r.get("pmid"),r.get("pubYear"),"|",r.get("title"),"|",r.get("journalInfo",{}).get("journal",{}).get("isoabbreviation"),"| OA:",r.get("isOpenAccess"),"| lic:",r.get("license"))
    if len(sys.argv)>3: print("   ABS:",ab[:int(sys.argv[3])].replace("<h4>"," ").replace("</h4>"," "))
