import json, sys, urllib.request, urllib.parse
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
def search(q, n=50):
    url = "https://www.ebi.ac.uk/europepmc/webservices/rest/search?" + urllib.parse.urlencode(
        {"query": q, "format": "json", "pageSize": n, "resultType": "lite"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    d = json.load(urllib.request.urlopen(req, timeout=60))
    return d.get("hitCount"), d["resultList"]["result"]
out = {}
for q in sys.argv[2:]:
    hc, res = search(q)
    print(f"## {q}  hits={hc}")
    rows = []
    for r in res:
        rows.append({k: r.get(k) for k in ("pmid", "pmcid", "title", "journalTitle", "pubYear", "isOpenAccess", "authorString")})
        print(f"  {r.get('pmcid')} {r.get('pubYear')} OA={r.get('isOpenAccess')} | {r.get('journalTitle')} | {(r.get('title') or '')[:110]}")
    out[q] = rows
json.dump(out, open(sys.argv[1], "w"), indent=1)
