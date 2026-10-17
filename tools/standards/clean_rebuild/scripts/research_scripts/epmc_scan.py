# usage: epmc_scan.py <cache_dir> <out_json> <regex_any> <pmcid...>
import sys, os, re, json, html, time, urllib.request
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
cache, outp, rx = sys.argv[1], sys.argv[2], re.compile(sys.argv[3], re.I)
os.makedirs(cache, exist_ok=True)
def get(p):
    fp = os.path.join(cache, p + ".xml")
    if not os.path.exists(fp):
        try:
            req = urllib.request.Request(f"https://www.ebi.ac.uk/europepmc/webservices/rest/{p}/fullTextXML", headers={"User-Agent": UA})
            data = urllib.request.urlopen(req, timeout=60).read()
        except Exception as e:
            data = b""
        open(fp, "wb").write(data); time.sleep(0.2)
    return open(fp, encoding="utf-8", errors="replace").read()
def txt(s):
    s = re.sub(r"</(td|th)>", " | ", s); s = re.sub(r"</tr>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s); return html.unescape(s)
res = {}
for p in sys.argv[4:]:
    x = get(p)
    if not x: continue
    title = txt((re.search(r"<article-title>(.*?)</article-title>", x, re.S) or [None, ""])[1])[:150]
    lic = txt((re.search(r"<license.*?</license>", x, re.S) or [""])[0])[:200]
    hits = []
    for tw in re.findall(r"<table-wrap.*?</table-wrap>", x, re.S):
        t = txt(tw)
        if rx.search(t) and re.search(r"kg|1RM|RM", t):
            cap = txt((re.search(r"<caption>(.*?)</caption>", tw, re.S) or [None, ""])[1]).strip()[:200]
            rows = [l.strip() for l in t.split("\n") if rx.search(l) or re.search(r"body ?mass|weight \(kg\)|BM", l, re.I)]
            hits.append({"table": cap, "rows": rows[:25]})
    body = txt(re.sub(r"<table-wrap.*?</table-wrap>", "", x, flags=re.S))
    sents = [s.strip() for s in re.split(r"(?<=[.;])\s+", body) if rx.search(s) and re.search(r"\d+(\.\d+)?\s?(±|kg)", s)]
    if hits or sents:
        res[p] = {"title": title, "licence": lic, "tables": hits, "sentences": sents[:12]}
json.dump(res, open(outp, "w"), indent=1, ensure_ascii=False)
print(len(res), "papers with matches")
for p, v in res.items():
    print("\n#####", p, v["title"]); print("  LIC:", v["licence"][:120])
    for h in v["tables"]:
        print("  [T]", h["table"])
        for r in h["rows"][:12]: print("     ", r[:220])
    for s in v["sentences"][:6]: print("  [S]", s[:300])
