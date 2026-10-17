#!/usr/bin/env python3
"""
Pool BASELINE 1RM + body-mass characteristics from open-access exercise-science
studies (Europe PMC) to estimate how strong ordinary gym-goers are.

Stages (run with `python3 -I baselines.py <stage>`):
  search     Europe PMC REST search (OPEN_ACCESS:Y, TABLE:/METHODS: section queries)
             -> research/search/candidates.json
  fetch      download fullTextXML for candidates -> research/xml/PMCxxxx.xml
  extract    parse tables + participant sentences; pull mean +- SD for body mass and
             1RM per participant group; licence, sex, n, age, training-status cues
             -> research/work/auto.json (+ research/work/review.txt for hand checks)
  build      quality filters, dedupe, BM joins, licence flags -> research/baselines.json
  summarise  pooled tables, lift ratios, body-mass scaling -> research/summary.md/.json
  all        extract + build + summarise

Downloaded XML is treated as untrusted data: it is only parsed, never executed.
"""
import json
import math
import os
import re
import subprocess
import sys
import html
import random
import xml.etree.ElementTree as ET
from collections import defaultdict, OrderedDict, Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # research/
SEARCH_DIR = os.path.join(ROOT, "search")
XML_DIR = os.path.join(ROOT, "xml")
WORK_DIR = os.path.join(ROOT, "work")
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
      "Chrome/126.0 Safari/537.36")
API = "https://www.ebi.ac.uk/europepmc/webservices/rest"
LB = 0.45359237

for d in (SEARCH_DIR, XML_DIR, WORK_DIR):
    os.makedirs(d, exist_ok=True)


# =========================================================================== search
RM_Q = ('(TABLE:"1RM" OR TABLE:"1-RM" OR TABLE:"1 RM" OR TABLE:"one-repetition maximum" '
        'OR TABLE:"one repetition maximum" OR TABLE:"RM")')
BM_Q = '(TABLE:"body mass" OR TABLE:"body weight" OR TABLE:"weight" OR TABLE:"mass")'
EX_TABLE = {
    "bench": 'TABLE:"bench"',
    "squat": 'TABLE:"squat"',
    "deadlift": '(TABLE:"deadlift" OR TABLE:"dead lift")',
    "ohp": '(TABLE:"overhead press" OR TABLE:"military press" OR TABLE:"shoulder press")',
    "row": '(TABLE:"bent-over row" OR TABLE:"bent over row" OR TABLE:"barbell row" OR TABLE:"bench pull" OR TABLE:"seated row")',
    "pulldown": '(TABLE:"pulldown" OR TABLE:"pull-down" OR TABLE:"lat pull")',
    "curl": '(TABLE:"curl")',
    "legpress": '(TABLE:"leg press")',
    "legext": '(TABLE:"leg extension" OR TABLE:"knee extension")',
    "hipthrust": '(TABLE:"hip thrust")',
}
METHODS_Q = [
    'METHODS:"1RM" AND METHODS:"bench press" AND (METHODS:"body mass" OR METHODS:"body weight")',
    'METHODS:"1RM" AND METHODS:"squat" AND (METHODS:"body mass" OR METHODS:"body weight")',
    'METHODS:"1RM" AND METHODS:"deadlift" AND (METHODS:"body mass" OR METHODS:"body weight")',
    '"recreationally resistance-trained" AND ("1RM" OR "one-repetition maximum")',
    '"resistance-trained" AND "1RM" AND "bench press" AND "women"',
    '"untrained" AND "1RM" AND ("bench press" OR "squat") AND ("body mass" OR "body weight")',
]


def curl_json(url, params):
    args = ["curl", "-sS", "--max-time", "60", "-A", UA, "-G", url]
    for k, v in params.items():
        args += ["--data-urlencode", f"{k}={v}"]
    out = subprocess.run(args, capture_output=True, text=True, check=True).stdout
    return json.loads(out)


def search_all(query, cap=3000):
    res, cursor = [], "*"
    while True:
        d = curl_json(f"{API}/search", {"query": query, "format": "json", "pageSize": 1000,
                                        "resultType": "lite", "cursorMark": cursor})
        batch = d.get("resultList", {}).get("result", [])
        res += batch
        nxt = d.get("nextCursorMark")
        if not batch or not nxt or nxt == cursor or len(res) >= cap:
            return d.get("hitCount"), res
        cursor = nxt


def stage_search():
    queries = OrderedDict()
    for k, ex in EX_TABLE.items():
        queries[f"table_{k}"] = f"OPEN_ACCESS:Y AND {RM_Q} AND {BM_Q} AND {ex} AND NOT PUB_TYPE:\"review\""
    for i, q in enumerate(METHODS_Q):
        queries[f"methods_{i}"] = f"OPEN_ACCESS:Y AND {q} AND NOT PUB_TYPE:\"review\""
    cands = {}
    for name, q in queries.items():
        hits, res = search_all(q)
        print(f"{name}: hitCount={hits} fetched={len(res)}")
        for r in res:
            pmcid = r.get("pmcid")
            if not pmcid:
                continue
            c = cands.setdefault(pmcid, {"pmcid": pmcid, "title": r.get("title"),
                                         "authors": r.get("authorString"), "journal": r.get("journalTitle"),
                                         "year": r.get("pubYear"), "doi": r.get("doi"),
                                         "pubType": r.get("pubType"), "queries": []})
            c["queries"].append(name)
    out = os.path.join(SEARCH_DIR, "candidates.json")
    json.dump(list(cands.values()), open(out, "w"), indent=1)
    print("unique candidates:", len(cands), "->", out)


# =========================================================================== fetch
def stage_fetch(limit=None):
    cands = json.load(open(os.path.join(SEARCH_DIR, "candidates.json")))
    todo = [c["pmcid"] for c in cands if not os.path.exists(os.path.join(XML_DIR, c["pmcid"] + ".xml"))]
    if limit:
        todo = todo[:int(limit)]
    print("to fetch:", len(todo))
    listing = os.path.join(WORK_DIR, "fetch_list.txt")
    with open(listing, "w") as f:
        f.write("\n".join(todo))
    cmd = ("xargs -P 4 -I{} sh -c 'curl -sS --max-time 90 -A \"$0\" "
           f"\"{API}/{{}}/fullTextXML\" -o \"{XML_DIR}/{{}}.part\" && "
           f"mv \"{XML_DIR}/{{}}.part\" \"{XML_DIR}/{{}}.xml\"' \"{UA}\" < \"{listing}\"")
    subprocess.run(cmd, shell=True)
    bad = 0
    for p in todo:
        fp = os.path.join(XML_DIR, p + ".xml")
        if os.path.exists(fp) and (os.path.getsize(fp) < 2000 or b"<article" not in open(fp, "rb").read(5000)):
            os.remove(fp)
            bad += 1
    print("removed bad payloads:", bad)


# =========================================================================== XML helpers
XML_KEEP = {"amp", "lt", "gt", "quot", "apos"}


def _fix_entities(raw):
    def rep(m):
        if m.group(1) in XML_KEEP:
            return m.group(0)
        u = html.unescape(m.group(0))
        return u if u != m.group(0) else " "
    return re.sub(r"&([A-Za-z][A-Za-z0-9]*);", rep, raw)


def ln(tag):
    return tag.rsplit("}", 1)[-1] if isinstance(tag, str) else ""


def txt(el):
    if el is None:
        return ""
    return re.sub(r"\s+", " ", "".join(el.itertext())).strip()


def cell_text(el):
    # keep sup/sub content but separate it so "9.1<sup>a</sup>" -> "9.1 a"
    parts = []

    def walk(e):
        if e.text:
            parts.append(e.text)
        for c in e:
            if ln(c.tag) in ("sup", "sub", "xref"):
                parts.append(" " + txt(c) + " ")
            elif ln(c.tag) == "break":
                parts.append(" ")
            else:
                walk(c)
            if c.tail:
                parts.append(c.tail)
    walk(el)
    return re.sub(r"\s+", " ", "".join(parts)).strip()


def load_root(path):
    raw = open(path, encoding="utf-8", errors="replace").read()
    raw = re.sub(r"<!DOCTYPE[^>\[]*(\[.*?\])?\s*>", "", raw, count=1, flags=re.S)
    raw = _fix_entities(raw)
    return ET.fromstring(raw)


def find_all(root, name):
    return [e for e in root.iter() if ln(e.tag) == name]


def find_first(root, name):
    for e in root.iter():
        if ln(e.tag) == name:
            return e
    return None


# --------------------------------------------------------------------------- licence
def normalise_licence(root):
    lic_texts, urls = [], []
    for lic in find_all(root, "license"):
        for k, v in lic.attrib.items():
            if ln(k) == "href":
                urls.append(v)
        for e in lic.iter():
            if ln(e.tag) == "license_ref" and e.text:
                urls.append(e.text.strip())
            if ln(e.tag) == "ext-link":
                for k, v in e.attrib.items():
                    if ln(k) == "href":
                        urls.append(v)
        lic_texts.append(txt(lic))
    blob = " ".join(urls + lic_texts)
    lb = blob.lower()
    m = re.search(r"creativecommons\.org/licenses/([a-z\-]+)/(\d\.\d)", lb)
    name = None
    if "publicdomain/zero" in lb or re.search(r"\bcc0\b", lb):
        name = "CC0 1.0"
    elif m:
        name = "CC " + m.group(1).upper() + " " + m.group(2)
    else:
        t = lb.replace("‐", "-")
        if "creative commons" in t:
            nc = "noncommercial" in t.replace("-", "").replace(" ", "")
            nd = "noderiv" in t.replace("-", "").replace(" ", "")
            sa = "sharealike" in t.replace("-", "").replace(" ", "")
            code = "BY" + ("-NC" if nc else "") + ("-ND" if nd else "") + ("-SA" if sa else "")
            v = re.search(r"\b(\d\.\d)\b", t)
            name = "CC " + code + (" " + v.group(1) if v else "")
        elif m is None and re.search(r"cc[ -]by", t):
            mm = re.search(r"cc[ -](by(?:-nc)?(?:-nd)?(?:-sa)?)\s*(\d\.\d)?", t)
            name = "CC " + mm.group(1).upper() + (" " + mm.group(2) if mm.group(2) else "")
    if not name:
        name = "unspecified" + (": " + blob[:120] if blob else "")
    code = name.split(" ")[1] if name.startswith("CC ") else name
    commercial = name.startswith("CC0") or code in ("BY", "BY-SA", "BY-ND")
    return name, commercial


# --------------------------------------------------------------------------- metadata
def article_meta(root):
    am = find_first(root, "article-meta")
    jm = find_first(root, "journal-meta")
    title = txt(find_first(am, "article-title")) if am is not None else ""
    authors = []
    if am is not None:
        for c in find_all(am, "contrib"):
            if c.attrib.get("contrib-type") != "author":
                continue
            nm = find_first(c, "name")
            if nm is None:
                coll = find_first(c, "collab")
                if coll is not None:
                    authors.append(txt(coll))
                continue
            sur = txt(find_first(nm, "surname"))
            giv = txt(find_first(nm, "given-names"))
            ini = "".join(w[0] for w in re.split(r"[\s\-\.]+", giv) if w)
            authors.append(f"{sur} {ini}".strip())
    journal = ""
    if jm is not None:
        jt = find_first(jm, "journal-title")
        journal = txt(jt)
        for j in find_all(jm, "journal-id"):
            if j.attrib.get("journal-id-type") in ("nlm-ta", "iso-abbrev"):
                journal = txt(j)
                break
    year = vol = issue = pages = doi = ""
    if am is not None:
        for pd in find_all(am, "pub-date"):
            y = find_first(pd, "year")
            if y is not None and y.text:
                year = y.text.strip()
                break
        for k, nm in (("volume", "volume"), ("issue", "issue")):
            e = next((x for x in am if ln(x.tag) == nm), None)
            if k == "volume":
                vol = txt(e)
            else:
                issue = txt(e)
        fp = next((x for x in am if ln(x.tag) == "fpage"), None)
        lp = next((x for x in am if ln(x.tag) == "lpage"), None)
        el = next((x for x in am if ln(x.tag) == "elocation-id"), None)
        pages = txt(fp) + ("-" + txt(lp) if lp is not None else "") if fp is not None else txt(el)
        for aid in find_all(am, "article-id"):
            if aid.attrib.get("pub-id-type") == "doi":
                doi = txt(aid)
    au = ", ".join(authors[:3]) + (", et al" if len(authors) > 3 else "")
    cit = f"{au}. {title.rstrip('.')}. {journal}. {year}"
    if vol:
        cit += f";{vol}" + (f"({issue})" if issue else "")
    if pages:
        cit += f":{pages}"
    cit += "."
    if doi:
        cit += f" doi:{doi}"
    return {"title": title, "citation": cit, "year": year, "doi": doi, "journal": journal}


def section_texts(root):
    """abstract text, participants-section text, whole methods text"""
    abstract = " ".join(txt(a) for a in find_all(root, "abstract") if a.attrib.get("abstract-type") not in ("graphical",))
    body = find_first(root, "body")
    part, methods = [], []
    if body is not None:
        def walk(sec, path):
            t = sec.find("./title")
            title = txt(t) if t is not None else ""
            p2 = path + [title.lower()]
            paras = " ".join(txt(p) for p in sec if ln(p.tag) == "p")
            joined = " / ".join(p2)
            if re.search(r"method|material|procedure|design|protocol", joined):
                methods.append(paras)
            if re.search(r"participant|subject|sample|volunteer|population|athlete", title.lower()):
                part.append(paras)
            for c in sec:
                if ln(c.tag) == "sec":
                    walk(c, p2)
        for s in body:
            if ln(s.tag) == "sec":
                walk(s, [])
    return abstract, " ".join(part), " ".join(methods)


# =========================================================================== tables
def table_grid(tbl):
    """Return list of (is_header, cells[]) with row/col spans expanded."""
    rows = []
    pending = {}  # col -> [remaining, text]
    trs = []
    for child in tbl.iter():
        if ln(child.tag) == "tr":
            trs.append(child)
    parent_of = {}
    for sect in tbl.iter():
        if ln(sect.tag) in ("thead", "tbody", "tfoot"):
            for tr in sect.iter():
                if ln(tr.tag) == "tr":
                    parent_of[id(tr)] = ln(sect.tag)
    for tr in trs:
        cells = [c for c in tr if ln(c.tag) in ("td", "th")]
        is_head = parent_of.get(id(tr)) == "thead" or (cells and all(ln(c.tag) == "th" for c in cells) and not rows)
        row, col, ci = [], 0, 0
        while True:
            if col in pending and pending[col][0] > 0:
                row.append(pending[col][1])
                pending[col][0] -= 1
                col += 1
                continue
            if ci >= len(cells):
                if any(k >= col and v[0] > 0 for k, v in pending.items()):
                    row.append("")
                    col += 1
                    continue
                break
            c = cells[ci]
            ci += 1
            t = cell_text(c)
            try:
                cs = max(1, min(int(c.attrib.get("colspan", "1") or 1), 50))
            except ValueError:
                cs = 1
            try:
                rs = max(1, min(int(c.attrib.get("rowspan", "1") or 1), 200))
            except ValueError:
                rs = 1
            for k in range(cs):
                row.append(t)
                if rs > 1:
                    pending[col] = [rs - 1, t]
                col += 1
        rows.append((bool(is_head), row))
    return rows


NUM = r"-?\d+(?:\.\d+)?"
PM = r"(?:±|\+/-|\+-|∓|±)"
MS_RE = re.compile(rf"^\s*\(?\s*({NUM})\s*(?:kg|lbs?|lb)?\s*{PM}\s*({NUM})")
PAR_RE = re.compile(rf"^\s*({NUM})\s*(?:kg|lbs?)?\s*[\(\[]\s*({NUM})\s*[\)\]]")
PLAIN_RE = re.compile(rf"^\s*({NUM})\s*(?:kg|lbs?)?\s*(?:[a-z\*†‡§#¶]{{1,2}}(?:\s*,\s*[a-z\*†‡§#¶]{{1,2}})*)?\s*$")
INT_RE = re.compile(r"^\s*(\d{1,4})\s*$")


def norm_num_text(s):
    s = s.replace("−", "-").replace("–", "–").replace(" ", " ").replace(" ", " ")
    s = re.sub(r"(\d)·(\d)", r"\1.\2", s)
    s = re.sub(r"(?<![\d.])(\d+),(\d+)(?![\d,])", r"\1.\2", s)  # decimal comma
    return s


def parse_ms(s):
    s = norm_num_text(s)
    m = MS_RE.match(s)
    if m:
        return float(m.group(1)), float(m.group(2)), "pm"
    m = PAR_RE.match(s)
    if m:
        return float(m.group(1)), float(m.group(2)), "par"
    m = PLAIN_RE.match(s)
    if m:
        return float(m.group(1)), None, "plain"
    return None


def is_valueish(s):
    s = norm_num_text(s).strip()
    if not s:
        return False
    if s in ("-", "–", "—", "NA", "n/a", "N/A", "NR", "ns", "NS"):
        return True
    if parse_ms(s):
        return True
    return bool(re.match(rf"^[<>≤≥]?\s*{NUM}\s*(%|[–—\-]\s*{NUM}|\(.*\)|[\*†‡§#¶a-z]{{0,2}})?\s*$", s))


# --------------------------------------------------------------------------- label classification
BM_RE = re.compile(r"(body[\s\-]*mass|body[\s\-]*weight|^\s*weight\b|^\s*mass\b|^\s*bm\b|^\s*bw\b|^\s*weight\s*\(|^\s*mass\s*\()", re.I)
BM_EXCL = re.compile(r"lean|fat|free|muscle|skeletal|relative|index|bmi|%|change|Δ|delta|bone|/|per\b|loss|gain|target|lift|lifted|press|squat|curl|row|load|fm\b|ffm|lbm|segment|arm|leg|trunk", re.I)
AGE_RE = re.compile(r"^\s*age\b", re.I)
N_RE = re.compile(r"^\s*(n|number|no\.|sample size|participants|subjects)\s*(\(n\))?\s*$", re.I)

RM_RE = re.compile(r"(\b1\s*[\-–]?\s*rm\b|\b1rm|one[\s\-]*rep(etition)?[\s\-]*max|1[\s\-]*repetition[\s\-]*max|repetition[\s\-]*maximum|\brm\s*\(kg\)|maximal (dynamic )?strength|maximum (dynamic )?strength|max(imal)? strength|1-rm|\bmds\b)", re.I)
OTHER_RM = re.compile(r"\b(?:[2-9]|1[0-9]|20|25|30)\s*[\-–]?\s*rm\b|\b(?:[2-9]|1[0-9]|20)rm\b", re.I)
RM_EXCL = re.compile(
    r"%|velocity|m\s*/\s*s|m·s|m\.s|power|watt|\(w\)|\bw\b|newton|\(n\)|torque|n\s*·?\s*m\b|nm\b|reps?\b|repetitions|volume|rpe|emg|"
    r"time|tut|load at|predict|estimat|change|delta|Δ|\bpost\b|icc|\bcv\b|\bsem\b|mdc|correlation|asymmetr|isometric|mvic|mvc|imtp|"
    r"isokinetic|jump|throw|bar speed|mpv|\bmv\b|grip|sets|endurance|fatigue|failure|work|impulse|force|rfd|difference|"
    r"ratio|deficit|imbalance|eccentric|concentric only|bilateral deficit|left|right|dominant|limb|unilateral|single[\s\-]leg|"
    r"one[\s\-]leg|one[\s\-]arm|single[\s\-]arm|dumbbell|kettlebell|band|machine|smith|isoinertial|flywheel|cable|"
    r"incline|decline|close[\s\-]grip|wide[\s\-]grip|split|bulgarian|jump|goblet|overhead squat|box|pin|rack|pause|"
    r"speed|bands|chains|trap|hex|sumo|romanian|rdl|stiff|good morning|deficit|block|snatch|clean|jerk|push press|z press|"
    r"seated|front squat|quarter|1/4|half|1/2|partial|technique|depth", re.I)
REL_RE = re.compile(r"relative|kg\s*/\s*kg|kg\s*[·\.∙⋅]\s*kg|kg\s*kg\s*[\-−–]?\s*1|/\s*bm\b|/\s*bw\b|[×x]\s*bm\b|[×x]\s*bw\b|/\s*body\s*(mass|weight)|per kg|normali[sz]ed|·\s*bm|· ?bw|\bbm\s*[\-−–]1|kg\s*·\s*bm", re.I)

EXERCISES = [
    ("hipthrust", re.compile(r"hip[\s\-]*thrust", re.I)),
    ("legpress", re.compile(r"leg[\s\-]*press|\blp\b", re.I)),
    ("legcurl", re.compile(r"(leg|hamstring|knee|lying|prone|seated)[\s\-]*curl|knee flexion|knee flexor", re.I)),
    ("legext", re.compile(r"leg[\s\-]*extension|knee[\s\-]*extension|knee extensor|\ble\b", re.I)),
    ("deadlift", re.compile(r"dead[\s\-]*lift|\bdl\b", re.I)),
    ("ohp", re.compile(r"overhead[\s\-]*press|military[\s\-]*press|shoulder[\s\-]*press|\bohp\b|strict press|standing press", re.I)),
    ("pulldown", re.compile(r"pull[\s\-]*down|lat[\s\-]*pull|\blpd\b", re.I)),
    ("row", re.compile(r"bent[\s\-]*over[\s\-]*row|barbell row|\brow\b|bench pull|prone row|pendlay|\bbor\b", re.I)),
    ("curl", re.compile(r"(arm|bicep|biceps|elbow|barbell|ez[\s\-]*bar|standing|preacher|dumbbell)?[\s\-]*curl|elbow flex", re.I)),
    ("bench", re.compile(r"bench[\s\-]*press|\bbp\b|\bbench\b(?![\s\-]*pull)", re.I)),
    ("squat", re.compile(r"squat|\bsq\b|\bbs\b", re.I)),
]
# variant words allowed for the core lifts (these are dropped from RM_EXCL for that lift)
VARIANT_OK = {
    "legpress": re.compile(r"seated|machine|45|horizontal|incline", re.I),
    "legext": re.compile(r"machine|seated|bilateral|unilateral|single|left|right|dominant|one[\s\-]leg", re.I),
    "legcurl": re.compile(r"machine|seated|lying|prone|bilateral|unilateral|single|left|right|dominant|one[\s\-]leg", re.I),
    "pulldown": re.compile(r"machine|cable|seated", re.I),
    "row": re.compile(r"seated|cable|machine|dumbbell|one[\s\-]arm|single[\s\-]arm", re.I),
    "curl": re.compile(r"dumbbell|cable|machine|seated|preacher|one[\s\-]arm|single[\s\-]arm|unilateral|left|right|dominant", re.I),
    "ohp": re.compile(r"seated|dumbbell|machine|smith", re.I),
    "hipthrust": re.compile(r"machine|smith", re.I),
}


def variant_of(ex, label):
    l = label.lower()
    if ex == "bench":
        if re.search(r"smith", l):
            return "smith"
        return "barbell"
    if ex == "squat":
        if re.search(r"smith", l):
            return "smith"
        if re.search(r"half|1/2|quarter|partial", l):
            return "half"
        return "back"
    if ex == "deadlift":
        if re.search(r"trap|hex", l):
            return "trap_bar"
        return "conventional/unspecified"
    if ex in ("legext", "legcurl", "curl", "row", "ohp"):
        if re.search(r"unilateral|single|one[\s\-](arm|leg)|left|right|dominant", l):
            return "unilateral"
        if ex == "curl" and re.search(r"dumbbell", l):
            return "dumbbell"
        if ex == "ohp" and re.search(r"seated", l):
            return "seated"
        if ex == "ohp" and re.search(r"dumbbell", l):
            return "dumbbell"
        if ex == "ohp" and re.search(r"machine|smith", l):
            return "machine/smith"
        if ex == "row" and re.search(r"seated|cable|machine", l):
            return "seated/cable"
        if ex == "row" and re.search(r"bench pull|prone", l):
            return "prone_bench_pull"
        return "bilateral/barbell"
    if ex == "legpress":
        if re.search(r"horizontal|seated", l):
            return "horizontal/seated"
        if re.search(r"45|incline|sled", l):
            return "45deg_sled"
        return "unspecified"
    return ""


def match_exercise(text):
    for ex, rx in EXERCISES:
        m = rx.search(text)
        if m:
            return ex, m
    return None, None


def classify_var(label, ctx):
    """label = row (or column) label; ctx = section + caption + group header text.
    Returns (kind, exercise, variant, unit) with kind in bm/rm/rel/age/n or None."""
    L = label.strip()
    l = L.lower()
    if not L:
        return None
    if BM_RE.search(l) and not BM_EXCL.search(re.sub(r"body[\s\-]*mass|body[\s\-]*weight", "", l)):
        unit = "lb" if re.search(r"\blbs?\b|pound", l) else ("kg" if "kg" in l else None)
        return ("bm", None, None, unit)
    if AGE_RE.search(l):
        return ("age", None, None, None)
    if N_RE.search(l):
        return ("n", None, None, None)
    ex, m = match_exercise(l)
    use_ctx = False
    if ex is None:
        # label like "1RM (kg)" under a section/caption naming a single exercise
        if RM_RE.search(l) or re.fullmatch(r"\s*(kg|absolute|absolute \(kg\)|1rm|load \(kg\))\s*", l):
            ex, m = match_exercise(ctx)
            use_ctx = True
        if ex is None:
            return None
    full = l if not use_ctx else (l + " " + ctx.lower())
    has_rm = bool(RM_RE.search(l) or RM_RE.search(ctx))
    if not has_rm:
        return None
    if OTHER_RM.search(l) or (use_ctx and OTHER_RM.search(ctx) and not RM_RE.search(l)):
        return None
    if REL_RE.search(l):
        kind = "rel"
    else:
        kind = "rm"
    # exclusion words, but allow variant words that are harmless for that lift
    lab_wo = l
    for exn, rx in EXERCISES:
        lab_wo = rx.sub(" ", lab_wo)
    lab_wo = RM_RE.sub(" ", lab_wo)
    lab_wo = REL_RE.sub(" ", lab_wo)
    if ex in VARIANT_OK:
        lab_wo = VARIANT_OK[ex].sub(" ", lab_wo)
    if ex in ("bench", "squat") and re.search(r"smith", lab_wo):
        lab_wo = re.sub(r"smith( machine)?", " ", lab_wo)
    if ex == "squat":
        lab_wo = re.sub(r"half|1/2|parallel|full|deep|back|free[\s\-]weight|barbell", " ", lab_wo)
    if ex == "deadlift":
        lab_wo = re.sub(r"trap|hex|conventional|barbell", " ", lab_wo)
    if ex == "bench":
        lab_wo = re.sub(r"barbell|flat|free[\s\-]weight", " ", lab_wo)
    lab_wo = re.sub(r"\b(lbs?|kg|absolute|abs|pre|baseline|test|kg\s*/\s*kg|values?|mean|sd|maximal|maximum|strength|load|lift(ed)?|exercise|press|machine)\b", " ", lab_wo)
    if RM_EXCL.search(lab_wo):
        return None
    if ex == "squat" and re.search(r"jump|split|front|overhead|goblet|bulgarian|box|pistol|sumo|lunge", l):
        return None
    if ex == "bench" and re.search(r"throw|pull|incline|decline|close|dumbbell|ballistic", l):
        return None
    if ex == "deadlift" and re.search(r"romanian|rdl|stiff|sumo|single", l):
        return None
    unit = None
    if kind == "rel":
        unit = "kg/kg"
    elif re.search(r"\blbs?\b|pound", l):
        unit = "lb"
    elif re.search(r"\bkg\b", l):
        unit = "kg"
    variant = variant_of(ex, full)
    return (kind, ex, variant, unit)


TIME_SKIP = re.compile(
    r"\bpost|after|follow[\s\-]?up|\bweek\s*[1-9]|\bwk\s*[1-9]|\bw[1-9]\d?\b|\bmid\b|retention|detrain|\bt[2-9]\b|change|Δ|delta|diff|%|"
    r"p[\s\-]?value|^\s*p\s*$|\bp\b|effect size|\bes\b|cohen|hedges|^\s*d\s*$|\bg\s*$|95\s*%|\bci\b|range|^\s*min|^\s*max|icc|\bcv\b|"
    r"\bsem\b|\bcorr|^\s*r\s*$|statistic|^\s*[ft]\s*$|η|eta|retest|test\s*2|session\s*2|day\s*2|trial\s*2|re-?test|"
    r"posttest|post-test|final|end|month\s*[1-9]|month[s]?\s*\d|interaction|group\s*x|time\s*x|ancova|anova|sig|lower|upper|iqr|median|"
    r"cv%|swc|mdc|bias|loa|limits|predicted|estimated|day\s*[2-9]|session\s*[2-9]|visit\s*[2-9]|testing\s*2|t2", re.I)
TOTAL_RE = re.compile(r"\b(all|total|whole|overall|combined|pooled|entire|sample|participants|subjects)\b", re.I)


def group_sex(text):
    t = text.lower()
    m = bool(re.search(r"\b(men|males?|man|boys|male participants|♂)\b", t))
    f = bool(re.search(r"\b(women|females?|woman|girls|female participants|♀)\b", t))
    if re.fullmatch(r"\s*m\s*(\(n\s*=\s*\d+\))?\s*", t):
        m = True
    if re.fullmatch(r"\s*f\s*(\(n\s*=\s*\d+\))?\s*", t):
        f = True
    if m and not f:
        return "male"
    if f and not m:
        return "female"
    if m and f:
        return "mixed"
    return None


WORDNUM = {w: i for i, w in enumerate(
    "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen "
    "seventeen eighteen nineteen twenty".split())}
for tens, v in (("twenty", 20), ("thirty", 30), ("forty", 40), ("fifty", 50), ("sixty", 60), ("seventy", 70), ("eighty", 80), ("ninety", 90)):
    WORDNUM[tens] = v
    for u, uv in list(WORDNUM.items())[1:10]:
        WORDNUM[f"{tens}-{u}"] = v + uv
        WORDNUM[f"{tens} {u}"] = v + uv


def group_n(text):
    m = re.search(r"\bn\s*[=:]\s*(\d{1,4})", text, re.I)
    if m:
        return int(m.group(1))
    m = re.search(r"\((\d{1,3})\)\s*$", text)
    if m and not re.search(r"\d\s*[\.±]", text):
        return None
    return None


# --------------------------------------------------------------------------- training status
def norm_t(s):
    return re.sub(r"[‐‑‒–\-]", " ", s.lower())


ELITE_STRENGTH = re.compile(r"powerlift|weightlifter|olympic weightlift|weightlifting athlete|strongm[ae]n|bodybuilder|bodybuilding athlete|competitive (power|weight)|"
                            r"(national|international|elite|world)[ ]?(level|class)? (power|weight)lift|strength athletes|ipf|\busapl\b", re.I)
ELITE_ATH = re.compile(r"\b(elite|professional|international level|national team|olympic|world class|first division|premier league|national level|top level|tier [45])\b", re.I)
ATHLETE = re.compile(r"\b(athletes?|players?|rugby|soccer|footballers?|american football|basketball|handball|volleyball|baseball|softball|hockey|wrestlers?|judokas?|"
                     r"sprinters?|throwers?|rowers?|swimmers?|cyclists?|runners?|ncaa|division i|collegiate (athletes|football)|crossfit|military|soldiers?|"
                     r"cadets?|firefighters?|police|combat|fighters|boxers?|karate|taekwondo|jiu|mma|team sport|tennis|sprint|rowing|canoe|kayak|ski|skiers?|cricket|lacrosse|gymnasts?)\b", re.I)
UNTRAINED = re.compile(r"\b(untrained|un trained|sedentary|non trained|nontrained|resistance training na[iï]ve|training na[iï]ve|na[iï]ve to resistance|"
                       r"novices?|inexperienced|no (previous |prior |regular |formal |systematic )?(resistance|strength|weight) (training|exercise)|"
                       r"not (been )?(engaged|participat\w*|involved|performed) in (any )?(regular |systematic |structured |formal )?(resistance|strength|weight)|"
                       r"had not (performed|participated|engaged)|without (any )?(previous |prior )?(resistance|strength) training|beginners?|no experience (in|with) (resistance|strength|weight))\b", re.I)
RECACT = re.compile(r"\b(recreationally active|physically active|moderately active|healthy active|active (young )?(men|women|males|females|adults|students|individuals)|sport science students|physical education students|kinesiology students)\b", re.I)
RECRT = re.compile(r"\b(recreationally (resistance |strength |weight )?trained|recreational(ly)? (weight ?lifters|lifters|resistance|strength|bodybuild\w*|gym)|"
                   r"recreationally active[^.]{0,100}(resistance|strength|weight) (train(ed|ing)|exercise)|gym goers?|fitness (center|centre|club) (members|users)|"
                   r"recreational(ly)? (trained|training))\b", re.I)
RT = re.compile(r"\b(resistance trained|strength trained|weight trained|well trained|highly trained|trained (men|women|males|females|lifters|individuals|participants|subjects|adults|young)|"
                r"experienced (lifters|in resistance|resistance)|resistance training experience|strength training experience|years? of (resistance|strength|weight) training|"
                r"(resistance|strength|weight) training experience)\b", re.I)
CLINICAL = re.compile(r"patients?|disease|cancer|diabet|obes|sarcopen|osteo|frail|elderly|older (adults|men|women|people|individuals|persons|subjects)|aged (men|women)|"
                      r"postmenopausal|rehabilitat|injur|arthrit|hiv|copd|heart failure|stroke|dialysis|kidney|renal|children|adolescen|youth|boys|girls|pregnan|"
                      r"spinal cord|parkinson|multiple sclerosis|hypertens|metabolic syndrome|schizophren|depress|transplant|surgery|reconstruction|acl\b|"
                      r"older\b|seniors?|middle[\s\-]aged|nursing|intellectual|down syndrome|cerebral|wheelchair|amput|menopaus|polycystic|pcos|fibromyalg|"
                      r"veterans|prostate|breast|lymph|chemother|hemophil|asthma|cystic|sickle|chronic|athero|cardiac|coronary|thyroid|haemodial|hemodial", re.I)


def exp_years(text):
    t = norm_t(text)
    m = re.search(r"(at least|minimum of|minimum|more than|over|≥|>|>=)\s*(\d+(\.\d+)?|one|two|three|four|five|six|twelve|eighteen)\s*(years?|yrs?|months?|mo)\b[^.]{0,60}(resistance|strength|weight|training|lifting)", t)
    if not m:
        m = re.search(r"(\d+(\.\d+)?)\s*±\s*\d+(\.\d+)?\s*(years?|yrs?)\s*(of\s*)?(resistance|strength|weight|training)( training)? (experience|history)", t)
        if m:
            v = float(m.group(1))
            return v, m.group(0)
        m = re.search(r"(resistance|strength|weight) training experience[^.]{0,30}?(\d+(\.\d+)?)\s*±\s*\d+(\.\d+)?\s*(years?|yrs?|months?)", t)
        if m:
            v = float(m.group(2))
            if m.group(5).startswith("mo"):
                v /= 12
            return v, m.group(0)
        return None, None
    num = m.group(2)
    v = float(WORDNUM.get(num, num)) if not re.match(r"\d", num) else float(num)
    if m.group(4).startswith("mo"):
        v /= 12
    return v, m.group(0)


def status_from_text(text):
    """Return (status, evidence) from title+abstract+participants text."""
    t = norm_t(text)
    ev = {}
    for name, rx in (("elite_strength", ELITE_STRENGTH), ("athlete", ATHLETE), ("untrained", UNTRAINED),
                     ("recreationally active", RECACT), ("recreationally resistance-trained", RECRT), ("resistance-trained", RT)):
        m = rx.search(t)
        if m:
            s = max(0, m.start() - 60)
            ev[name] = t[s:m.end() + 60]
    elite = bool(ELITE_ATH.search(t))
    return ev, elite


def classify_status(ev, elite, group_label, yrs):
    g = norm_t(group_label)
    # group-level override
    if re.search(r"powerlift|weightlift", g):
        return "elite_strength", "group label"
    if re.search(r"\buntrained|sedentary|novice|non trained|beginner", g):
        return "untrained", "group label"
    if re.search(r"recreational", g) and re.search(r"train|lift|resist|strength", g):
        return "recreationally resistance-trained", "group label"
    if re.search(r"\b(trained|resistance trained|strength trained|experienced|advanced)\b", g):
        return "resistance-trained", "group label"
    if re.search(r"\b(athletes?|players?|rugby|soccer|football)\b", g):
        return "athletes", "group label"
    if "elite_strength" in ev:
        return "elite_strength", ev["elite_strength"]
    if "athlete" in ev:
        return ("elite_athletes" if elite else "athletes"), ev["athlete"]
    has_u = "untrained" in ev
    has_rrt = "recreationally resistance-trained" in ev
    has_rt = "resistance-trained" in ev
    has_ra = "recreationally active" in ev
    if has_u and (has_rt or has_rrt):
        # e.g. "trained and untrained men" -> needs group labels; or exclusion text
        return "ambiguous", ev.get("untrained", "") + " || " + ev.get("resistance-trained", ev.get("recreationally resistance-trained", ""))
    if has_u:
        return "untrained", ev["untrained"]
    if has_rrt:
        return "recreationally resistance-trained", ev["recreationally resistance-trained"]
    if has_rt:
        if has_ra:
            return "recreationally resistance-trained", ev["recreationally active"] + " || " + ev["resistance-trained"]
        return "resistance-trained", ev["resistance-trained"]
    if has_ra:
        if yrs is not None and yrs >= 0.5:
            return "recreationally resistance-trained", ev["recreationally active"]
        return "recreationally active", ev["recreationally active"]
    if yrs is not None:
        return ("resistance-trained" if yrs >= 2 else "recreationally resistance-trained"), "experience criterion only"
    return "unknown", ""


# --------------------------------------------------------------------------- per-table extraction
def header_rows(grid):
    """number of leading header rows"""
    nh = 0
    for is_head, row in grid:
        if is_head:
            nh += 1
        else:
            break
    if nh == 0:
        for is_head, row in grid[:4]:
            if any(is_valueish(c) and parse_ms(c) and parse_ms(c)[2] != "plain" for c in row[1:]):
                break
            if sum(1 for c in row[1:] if is_valueish(c)) >= max(1, (len(row) - 1) // 2):
                break
            nh += 1
    return nh


def col_labels(grid, nh, width):
    labs = []
    for j in range(width):
        seen = []
        for is_head, row in grid[:nh]:
            if j < len(row) and row[j] and row[j] not in seen:
                seen.append(row[j])
        labs.append(" | ".join(seen))
    return labs


def extract_table(tw, tidx):
    label = txt(tw.find("./label")) if tw.find("./label") is not None else f"T{tidx}"
    cap_el = find_first(tw, "caption")
    caption = txt(cap_el)
    foot = " ".join(txt(f) for f in tw.iter() if ln(f.tag) == "table-wrap-foot")
    out = []
    for tbl in [e for e in tw.iter() if ln(e.tag) == "table"]:
        grid = table_grid(tbl)
        if len(grid) < 2:
            continue
        width = max(len(r) for _, r in grid)
        grid = [(h, r + [""] * (width - len(r))) for h, r in grid]
        nh = header_rows(grid)
        clabs = col_labels(grid, nh, width)
        # Mean | SD split columns
        msplit = {}
        for j in range(width - 1):
            a, b = clabs[j].lower(), clabs[j + 1].lower()
            if re.search(r"(\bmean\b|\bm\b|x̄|average)\s*$", a) and re.search(r"(\bsd\b|s\.d\.|±|\bstd\b|standard deviation)\s*$", b):
                msplit[j] = j + 1
        body = grid[nh:]
        # orientation (a): variables in rows; (b): variables in columns
        rowvars = sum(1 for _, r in body if classify_var(r[0], caption) and classify_var(r[0], caption)[0] in ("bm", "rm", "rel"))
        colvars = sum(1 for j in range(1, width) if classify_var(clabs[j].split(" | ")[-1], caption) and classify_var(clabs[j].split(" | ")[-1], caption)[0] in ("bm", "rm", "rel"))
        colvars_full = sum(1 for j in range(1, width) if classify_var(clabs[j], caption) and classify_var(clabs[j], caption)[0] in ("bm", "rm", "rel"))
        colvars = max(colvars, colvars_full)
        cells = []  # dicts: group, var, kind, ex, variant, unit, mean, sd
        if rowvars >= colvars and rowvars > 0:
            section = ""
            for _, r in body:
                nonempty = [c for c in r if c]
                if nonempty and len(set(nonempty)) == 1 and not is_valueish(nonempty[0]):
                    section = nonempty[0]
                    continue
                # leading label cells
                k = 0
                while k < width and not is_valueish(r[k]):
                    k += 1
                if k == 0:
                    continue
                var = r[0]
                sub = " ".join(x for x in r[1:k] if x and x != var)
                cv = classify_var(var, section + " " + caption)
                if cv is None and sub:
                    cv = classify_var(sub, section + " " + caption)
                    if cv:
                        var, sub = sub, r[0]
                if cv is None:
                    continue
                kind, ex, variant, unit = cv
                seen_cols = set()
                for j in range(k, width):
                    if j in seen_cols:
                        continue
                    v = r[j]
                    if not v:
                        continue
                    if j in msplit:
                        pm = parse_ms(v)
                        sdv = parse_ms(r[msplit[j]])
                        if pm and sdv:
                            pm = (pm[0], sdv[0], "split")
                        seen_cols.add(msplit[j])
                        glab = re.sub(r"\|?\s*(mean|m|x̄|average)\s*$", "", clabs[j], flags=re.I).strip(" |")
                    else:
                        pm = parse_ms(v)
                        glab = clabs[j]
                    if not pm:
                        continue
                    if j > 0 and r[j] == r[j - 1] and j - 1 not in msplit:
                        continue  # colspan duplicate
                    grp = " || ".join(x for x in (section, sub, glab) if x)
                    unit2 = unit
                    if unit2 is None and kind in ("rm", "bm"):
                        ctxu = (section + " " + glab + " " + caption + " " + foot).lower()
                        if re.search(r"\blbs?\b|pounds", ctxu) and not re.search(r"\bkg\b", ctxu):
                            unit2 = "lb"
                        elif re.search(r"\bkg\b", ctxu + " " + v.lower()):
                            unit2 = "kg"
                    if kind == "n":
                        mm = INT_RE.match(v)
                        if not mm:
                            continue
                        pm = (float(mm.group(1)), None, "int")
                    cells.append(dict(group=grp, glab=glab, section=section, var=var, kind=kind, ex=ex, variant=variant,
                                      unit=unit2, mean=pm[0], sd=pm[1], fmt=pm[2], raw=v, col=j))
        elif colvars > 0:
            section = ""
            for _, r in body:
                nonempty = [c for c in r if c]
                if nonempty and len(set(nonempty)) == 1 and not is_valueish(nonempty[0]):
                    section = nonempty[0]
                    continue
                k = 0
                while k < width and not is_valueish(r[k]):
                    k += 1
                if k == 0:
                    continue
                rowlab = " ".join(dict.fromkeys(x for x in r[:k] if x))
                for j in range(k, width):
                    cv = classify_var(clabs[j].split(" | ")[-1], caption) or classify_var(clabs[j], caption)
                    v = r[j]
                    if cv is None or not v:
                        continue
                    kind, ex, variant, unit = cv
                    if kind == "n":
                        mm = INT_RE.match(v)
                        if not mm:
                            continue
                        pm = (float(mm.group(1)), None, "int")
                    else:
                        pm = parse_ms(v)
                    if not pm:
                        continue
                    if unit is None and kind in ("rm", "bm"):
                        ctxu = (clabs[j] + " " + caption + " " + foot).lower()
                        if re.search(r"\blbs?\b|pounds", ctxu) and not re.search(r"\bkg\b", ctxu):
                            unit = "lb"
                        elif re.search(r"\bkg\b", ctxu):
                            unit = "kg"
                    grp = " || ".join(x for x in (section, rowlab) if x)
                    cells.append(dict(group=grp, glab=rowlab, section=section, var=clabs[j], kind=kind, ex=ex, variant=variant,
                                      unit=unit, mean=pm[0], sd=pm[1], fmt=pm[2], raw=v, col=j))
        if cells:
            out.append(dict(table=label, caption=caption, foot=foot[:400], cells=cells,
                            preview=[" | ".join(r) for _, r in grid[:40]]))
    return out


# --------------------------------------------------------------------------- text extraction
SENT_SPLIT = re.compile(r"(?<=[.;])\s+(?=[A-Z(])")


def extract_text_values(text):
    """Find 'variable ... mean ± sd unit' fragments in participant sentences."""
    res = []
    t = norm_num_text(text)
    for sent in SENT_SPLIT.split(t):
        if "±" not in sent:
            continue
        if not (re.search(r"body\s*mass|body\s*weight|weight", sent, re.I) or RM_RE.search(sent)):
            continue
        for m in re.finditer(rf"({NUM})\s*(?:kg|lbs?)?\s*{PM}\s*({NUM})\s*(kg\s*/\s*kg|kg[·\.]kg[\-−]1|kg|lbs?|lb|years?|yrs?|y\b|cm|m\b|%|kg/m)?", sent):
            pre = sent[max(0, m.start() - 70):m.start()]
            pre = re.split(r"[;,(\[]|\band\b", pre)[-1] if re.search(r"[a-zA-Z]", re.split(r"[;,(\[]|\band\b", pre)[-1]) else pre
            unit = (m.group(3) or "").lower()
            res.append(dict(sentence=sent[:400], var=pre.strip(" :=-"), mean=float(m.group(1)), sd=float(m.group(2)), unit=unit))
    return res


# --------------------------------------------------------------------------- article-level
def article_sex(text):
    t = text.lower()
    nm = len(re.findall(r"\b(men|males?|man)\b", t))
    nf = len(re.findall(r"\b(women|females?|woman)\b", t))
    if nm and not nf:
        return "male"
    if nf and not nm:
        return "female"
    if nm and nf:
        return "mixed"
    return None


def article_n(text):
    t = norm_t(text)
    m = re.search(r"\b(\d{1,3}|" + "|".join(sorted(map(re.escape, WORDNUM), key=len, reverse=True)) +
                  r")\s+(?:[a-z\-]+\s+){0,5}?(men|women|males|females|participants|subjects|volunteers|individuals|adults|athletes|students|lifters)\b", t)
    if m:
        v = m.group(1)
        return int(v) if v.isdigit() else WORDNUM.get(v)
    m = re.search(r"\bn\s*=\s*(\d{1,3})\b", t)
    return int(m.group(1)) if m else None


def stage_extract(only=None):
    files = sorted(f for f in os.listdir(XML_DIR) if f.endswith(".xml"))
    if only:
        files = [f for f in files if f[:-4] in only]
    arts = []
    errs = 0
    for i, f in enumerate(files):
        pmcid = f[:-4]
        try:
            root = load_root(os.path.join(XML_DIR, f))
        except ET.ParseError as e:
            errs += 1
            continue
        tabs = []
        for k, tw in enumerate(find_all(root, "table-wrap")):
            try:
                tabs += extract_table(tw, k)
            except Exception as e:  # noqa
                print("table error", pmcid, k, e, file=sys.stderr)
        if not any(c["kind"] in ("rm", "rel") for t in tabs for c in t["cells"]):
            # text-only fallback: participants paragraphs
            abstract, part, methods = section_texts(root)
            tv = extract_text_values(part or methods[:4000])
            if not any(RM_RE.search(x["var"]) or match_exercise(x["var"])[0] for x in tv):
                continue
        meta = article_meta(root)
        lic, comm = normalise_licence(root)
        abstract, part, methods = section_texts(root)
        a = dict(pmcid=pmcid, **meta, licence=lic, commercial_ok=comm, abstract=abstract[:3000],
                 participants=part[:4000], methods_head=methods[:3000], tables=tabs,
                 text_values=extract_text_values(part or methods[:4000]))
        arts.append(a)
        if (i + 1) % 300 == 0:
            print(f"  {i+1}/{len(files)} scanned, {len(arts)} with candidate values", file=sys.stderr)
    json.dump(arts, open(os.path.join(WORK_DIR, "auto.json"), "w"), indent=0)
    print(f"scanned {len(files)} xml, parse errors {errs}, articles with 1RM candidates {len(arts)}")


# =========================================================================== build
PLAUS = {  # absolute 1RM kg range, relative range (1RM/BM)
    "bench": ((15, 260), (0.25, 2.3)), "squat": ((20, 380), (0.35, 3.2)), "deadlift": ((30, 380), (0.5, 3.4)),
    "ohp": ((10, 160), (0.15, 1.5)), "row": ((15, 220), (0.2, 2.0)), "pulldown": ((15, 200), (0.2, 1.8)),
    "curl": ((5, 110), (0.05, 1.0)), "legpress": ((40, 800), (0.6, 9.0)), "legext": ((8, 260), (0.1, 3.0)),
    "legcurl": ((8, 200), (0.1, 2.0)), "hipthrust": ((30, 450), (0.5, 4.5)),
}


def to_kg(v, unit):
    return None if v is None else (v * LB if unit == "lb" else v)


def norm_group(g):
    g = g.lower()
    g = re.sub(r"\(?\s*n\s*[=:]\s*\d+\s*\)?", " ", g)
    g = re.sub(r"\b(pre|baseline|before|pre[\s\-]?test|pre[\s\-]?training|pre[\s\-]?intervention|t1|t0|week\s*0|wk\s*0|test\s*1|session\s*1|day\s*1|"
               r"mean\s*±\s*sd|mean|sd|±|kg|values?)\b", " ", g)
    g = re.sub(r"[^a-z0-9 ]", " ", g)
    return re.sub(r"\s+", " ", g).strip()


def combine(rows_ms):
    """combine (n, mean, sd) groups into one: n-weighted mean, pooled SD incl. between-group variance"""
    N = sum(n for n, m, s in rows_ms)
    M = sum(n * m for n, m, s in rows_ms) / N
    if all(s is not None for n, m, s in rows_ms) and N > 1:
        ss = sum((n - 1) * s * s + n * (m - M) ** 2 for n, m, s in rows_ms)
        S = math.sqrt(ss / (N - 1))
    else:
        S = None
    return N, M, S


def stage_build():
    arts = json.load(open(os.path.join(WORK_DIR, "auto.json")))
    rows, rejects = [], Counter()
    review = []
    for a in arts:
        status_text = " ".join([a["title"], a["abstract"], a["participants"] or a["methods_head"][:2500]])
        ev, elite = status_from_text(status_text)
        yrs, yrs_ev = exp_years(a["participants"] + " " + a["abstract"] + " " + a["methods_head"][:2500])
        clinical = bool(CLINICAL.search(a["title"])) or bool(re.search(r"\b(patients|older adults|elderly|children|adolescents)\b", a["abstract"][:600], re.I))
        asex = article_sex(" ".join([a["title"], a["abstract"], a["participants"]]))
        an = article_n(a["participants"] or a["abstract"])
        # ---- gather cells per (table, group)
        groups = OrderedDict()
        for t in a["tables"]:
            for c in t["cells"]:
                if c["kind"] in ("rm", "rel") and TIME_SKIP.search(c["glab"] or ""):
                    continue
                if c["kind"] in ("bm", "age", "n") and TIME_SKIP.search(c["glab"] or "") and not re.search(r"\bpre\b|baseline", c["glab"] or "", re.I):
                    continue
                if c["kind"] in ("rm", "rel") and re.search(r"\b(post|after)\b", c["var"], re.I):
                    continue
                key = (t["table"], c["group"])
                g = groups.setdefault(key, dict(table=t["table"], caption=t["caption"], group=c["group"], glab=c["glab"],
                                                bm=None, age=None, n=None, rm={}, rel={}, preview=t["preview"]))
                if c["kind"] == "bm" and g["bm"] is None:
                    g["bm"] = c
                elif c["kind"] == "age" and g["age"] is None:
                    g["age"] = c
                elif c["kind"] == "n" and g["n"] is None:
                    g["n"] = int(c["mean"])
                elif c["kind"] == "rm":
                    kk = (c["ex"], c["variant"])
                    if kk not in g["rm"]:
                        g["rm"][kk] = c
                elif c["kind"] == "rel":
                    kk = (c["ex"], c["variant"])
                    if kk not in g["rel"]:
                        g["rel"][kk] = c
        # BM sources across tables
        bm_by_norm = {}
        bm_all = []
        for (tb, gr), g in groups.items():
            if g["bm"] is not None:
                bm_by_norm.setdefault(norm_group(g["glab"] or ""), g)
                bm_all.append(g)
        text_bm = [x for x in a["text_values"] if re.search(r"body\s*mass|body\s*weight|^weight|\bweight$|\bmass$", x["var"], re.I)
                   and not re.search(r"lean|fat|free|index", x["var"], re.I) and x["unit"] in ("kg", "lb", "lbs", "")]
        # text-derived 1RM when no table rm
        if not any(g["rm"] for g in groups.values()) and text_bm and len(text_bm) == 1:
            g = dict(table="text", caption="participants text", group="text", glab="", bm=None, age=None, n=None, rm={}, rel={}, preview=[])
            tb = text_bm[0]
            g["bm"] = dict(mean=tb["mean"], sd=tb["sd"], unit="lb" if tb["unit"].startswith("lb") else "kg", raw=tb["sentence"][:200], var=tb["var"])
            for x in a["text_values"]:
                cv = classify_var(x["var"], x["sentence"][:0])
                if cv and cv[0] in ("rm", "rel") and x["unit"] in ("kg", "lb", "lbs", "kg/kg", ""):
                    unit = "kg/kg" if cv[0] == "rel" else ("lb" if x["unit"].startswith("lb") else "kg")
                    c = dict(mean=x["mean"], sd=x["sd"], unit=unit, raw=x["sentence"][:200], var=x["var"], ex=cv[1], variant=cv[2], kind=cv[0])
                    (g["rm"] if cv[0] == "rm" else g["rel"]).setdefault((cv[1], cv[2]), c)
            if g["rm"]:
                groups[("text", "text")] = g
        # ---- decide which table to use per exercise (avoid double counting same participants)
        best_table = {}
        for (tb, gr), g in groups.items():
            for kk in list(g["rm"].keys()):
                score = (1 if g["bm"] is not None else 0, -list(groups.keys()).index((tb, gr)))
                cur = best_table.get(kk[0])
                tscore = (sum(1 for (tb2, _), g2 in groups.items() if tb2 == tb and kk in g2["rm"] and g2["bm"] is not None), -int(re.sub(r"\D", "", tb) or 99) if tb != "text" else -100)
                if cur is None or tscore > cur[1]:
                    best_table[kk[0]] = (tb, tscore)
        art_rows = []
        for (tb, gr), g in groups.items():
            glab = g["glab"] or ""
            for (ex, variant), c in g["rm"].items():
                if best_table.get(ex, (None,))[0] != tb:
                    continue
                flags = []
                # --- body mass
                bm = g["bm"]
                bm_src = "same table group"
                if bm is None:
                    cand = bm_by_norm.get(norm_group(glab))
                    if cand is not None and norm_group(glab):
                        bm, bm_src = cand["bm"], f"matched group in {cand['table']}"
                    elif len(bm_all) == 1:
                        bm, bm_src = bm_all[0]["bm"], f"single sample BM in {bm_all[0]['table']}"
                        flags.append("bm_from_whole_sample")
                    elif len(text_bm) == 1:
                        tbv = text_bm[0]
                        bm = dict(mean=tbv["mean"], sd=tbv["sd"], unit="lb" if tbv["unit"].startswith("lb") else "kg", raw=tbv["sentence"][:160], var=tbv["var"])
                        bm_src = "participants text"
                        flags.append("bm_from_whole_sample")
                if bm is None:
                    rejects["no_body_mass"] += 1
                    continue
                # --- units
                unit = c.get("unit")
                if unit is None:
                    unit = "kg"
                    flags.append("unit_assumed_kg")
                bunit = bm.get("unit") or ("lb" if bm["mean"] > 150 else "kg")
                if bm.get("unit") is None:
                    flags.append("bm_unit_assumed")
                rm_kg, rm_sd = to_kg(c["mean"], unit), to_kg(c["sd"], unit)
                bm_kg, bm_sd = to_kg(bm["mean"], bunit), to_kg(bm["sd"], bunit)
                # --- plausibility
                (lo, hi), (rlo, rhi) = PLAUS[ex]
                if not (35 <= bm_kg <= 160) or (bm_sd is not None and not (0.5 <= bm_sd <= 40)):
                    rejects["implausible_bm"] += 1
                    continue
                if not (lo <= rm_kg <= hi):
                    rejects["implausible_1rm"] += 1
                    continue
                rel = rm_kg / bm_kg
                if not (rlo <= rel <= rhi):
                    rejects["implausible_relative"] += 1
                    continue
                if rm_sd is not None and (rm_sd <= 0 or rm_sd > 0.8 * rm_kg):
                    rejects["implausible_sd"] += 1
                    continue
                if rm_sd is None:
                    flags.append("no_sd")
                # --- sex
                sex = group_sex(glab) or group_sex(g["group"]) or group_sex(g["caption"]) or asex
                if sex is None:
                    rejects["sex_unknown"] += 1
                    continue
                if group_sex(glab) is None and group_sex(g["group"]) is None and group_sex(g["caption"]) is None:
                    flags.append("sex_from_article_text")
                # --- n
                n = g["n"] or group_n(glab) or group_n(g["group"])
                n_src = "table"
                if n is None:
                    # sibling groups with n? caption?
                    nn = group_n(g["caption"])
                    ngroups = len({gg["group"] for (tb2, _), gg in groups.items() if tb2 == tb and (ex, variant) in gg["rm"]})
                    if nn and ngroups == 1:
                        n, n_src = nn, "caption"
                    elif an and ngroups == 1:
                        n, n_src = an, "text"
                        flags.append("n_from_text")
                if n is None or n < 3 or n > 2000:
                    rejects["n_unknown"] += 1
                    continue
                # --- age
                age = None
                ag = g["age"] or (bm_by_norm.get(norm_group(glab)) or {}).get("age") or (bm_all[0]["age"] if len(bm_all) == 1 else None)
                if ag:
                    age = ag["mean"]
                if age is None:
                    m = re.search(r"(?:aged?|age)[^.;]{0,25}?(\d{2}(?:\.\d+)?)\s*±\s*\d+(?:\.\d+)?\s*(?:years|yrs|y)\b", norm_num_text(a["participants"] + " " + a["abstract"]), re.I)
                    if m:
                        age = float(m.group(1))
                # --- relative reported?
                relc = g["rel"].get((ex, variant)) or next((v for (e2, _), v in g["rel"].items() if e2 == ex), None)
                rel_rep = None
                if relc and relc["mean"] and 0.05 < relc["mean"] < 10:
                    rel_rep = (relc["mean"], relc["sd"])
                status, sev = classify_status(ev, elite, glab + " " + g["group"], yrs)
                pool = "gym_goer"
                if status in ("elite_strength", "elite_athletes"):
                    pool = "excluded_elite"
                elif clinical:
                    pool = "excluded_clinical_or_age"
                elif age is not None and (age < 18 or age >= 50):
                    pool = "excluded_clinical_or_age"
                elif status in ("ambiguous", "unknown"):
                    pool = "excluded_status_unclear"
                conf = "high"
                if any(f in flags for f in ("unit_assumed_kg", "bm_unit_assumed", "n_from_text", "sex_from_article_text", "bm_from_whole_sample", "no_sd")):
                    conf = "medium"
                if sex == "mixed":
                    conf = "low"
                if tb == "text":
                    conf = "medium" if conf == "high" else conf
                    flags.append("from_text")
                row = OrderedDict(
                    pmcid=a["pmcid"], citation=a["citation"], licence=a["licence"], commercial_ok=a["commercial_ok"],
                    sex=sex, training_status=status, pool=pool, n=n, age_mean=age,
                    body_mass_kg=dict(mean=round(bm_kg, 2), sd=None if bm_sd is None else round(bm_sd, 2)),
                    exercise=ex, variant=variant,
                    one_rm_kg=dict(mean=round(rm_kg, 2), sd=None if rm_sd is None else round(rm_sd, 2)),
                    relative=round(rel, 3),
                    relative_reported=None if rel_rep is None else dict(mean=rel_rep[0], sd=rel_rep[1]),
                    group=glab or g["group"], source_table=tb, confidence=conf,
                    notes="; ".join(x for x in [
                        f"1RM label '{c['var'][:60]}' raw '{c['raw'][:40]}' unit {unit}",
                        f"BM '{bm['raw'][:30]}' ({bm_src})",
                        f"n from {n_src}",
                        f"status evidence: \"{sev[:160]}\"" if sev else "",
                        f"RT experience criterion ~{yrs:g} y" if yrs is not None else "",
                        ("flags: " + ",".join(flags)) if flags else ""] if x))
                art_rows.append(row)
        # dedupe identical rows (same exercise, same numbers)
        seen = set()
        for r in art_rows:
            k = (r["exercise"], r["sex"], r["one_rm_kg"]["mean"], r["body_mass_kg"]["mean"], r["n"])
            if k in seen:
                continue
            seen.add(k)
            rows.append(r)
        if art_rows:
            review.append((a, art_rows))
    # drop total/all groups when subgroups for same exercise+sex exist in the same table
    out = []
    by = defaultdict(list)
    for r in rows:
        by[(r["pmcid"], r["source_table"], r["exercise"], r["variant"])].append(r)
    for k, rs in by.items():
        if len(rs) > 1:
            tot = [r for r in rs if TOTAL_RE.search(r["group"]) and not group_sex(r["group"])]
            rest = [r for r in rs if r not in tot]
            if tot and rest:
                rs = rest
            # mixed-sex total next to male/female subgroups
            if any(r["sex"] in ("male", "female") and group_sex(r["group"]) for r in rs):
                rs = [r for r in rs if not (r["sex"] == "mixed")]
        out += rs
    json.dump(out, open(os.path.join(ROOT, "baselines.json"), "w"), indent=1, ensure_ascii=False)
    print("rows:", len(out), "articles:", len({r["pmcid"] for r in out}))
    print("rejects:", dict(rejects))
    print("pools:", Counter(r["pool"] for r in out))
    print("status:", Counter(r["training_status"] for r in out))
    print("exercise:", Counter(r["exercise"] for r in out))
    print("confidence:", Counter(r["confidence"] for r in out))
    # review dump
    with open(os.path.join(WORK_DIR, "review.txt"), "w") as f:
        for a, ars in review:
            f.write(f"\n######## {a['pmcid']} | {a['licence']} | {a['title'][:120]}\n")
            for r in ars:
                f.write(f"  -> {r['exercise']}/{r['variant']} {r['sex']} {r['training_status']} pool={r['pool']} n={r['n']} age={r['age_mean']} "
                        f"BM={r['body_mass_kg']} 1RM={r['one_rm_kg']} rel={r['relative']} grp='{r['group'][:60]}' [{r['confidence']}]\n     {r['notes'][:400]}\n")
            tabs = {r["source_table"] for r in ars}
            for t in a["tables"]:
                if t["table"] in tabs:
                    f.write(f"  == {t['table']}: {t['caption'][:150]}\n")
                    for line in t["preview"][:30]:
                        f.write("     " + line[:220] + "\n")


# =========================================================================== summarise
EX_ORDER = ["bench", "squat", "deadlift", "ohp", "row", "pulldown", "curl", "legpress", "legext", "legcurl", "hipthrust"]
STATUS_ORDER = ["untrained", "recreationally active", "recreationally resistance-trained", "resistance-trained", "athletes"]
CORE_VARIANT = {"bench": {"barbell"}, "squat": {"back"}, "deadlift": {"conventional/unspecified"}}


def est_rho(rows):
    """empirical corr(1RM, BM) implied by groups that report abs SD, BM SD and relative SD"""
    out = []
    for r in rows:
        rr = r.get("relative_reported")
        if not rr or rr.get("sd") is None or r["one_rm_kg"]["sd"] is None or r["body_mass_kg"]["sd"] is None:
            continue
        a = r["one_rm_kg"]["sd"] / r["one_rm_kg"]["mean"]
        b = r["body_mass_kg"]["sd"] / r["body_mass_kg"]["mean"]
        c = rr["sd"] / rr["mean"]
        if abs(rr["mean"] - r["relative"]) / r["relative"] > 0.12:
            continue  # reported relative doesn't match ratio of means (different unit or mismatch)
        rho = (a * a + b * b - c * c) / (2 * a * b)
        if -1.2 < rho < 1.2:
            out.append(max(-1, min(1, rho)))
    return out


def rel_sd(r, rho):
    rr = r.get("relative_reported")
    if rr and rr.get("sd") is not None and abs(rr["mean"] - r["relative"]) / r["relative"] < 0.12:
        return rr["sd"], "reported"
    if r["one_rm_kg"]["sd"] is None or r["body_mass_kg"]["sd"] is None:
        return None, None
    a = r["one_rm_kg"]["sd"] / r["one_rm_kg"]["mean"]
    b = r["body_mass_kg"]["sd"] / r["body_mass_kg"]["mean"]
    v = a * a + b * b - 2 * rho * a * b
    return r["relative"] * math.sqrt(max(v, 0.25 * a * a)), "delta"


def pool_stats(items):
    """items: list of (n, mean, sd). Returns N, mean, within-SD, total SD (incl. between-group), between-group SD"""
    N = sum(n for n, m, s in items)
    M = sum(n * m for n, m, s in items) / N
    wi = [(n, s) for n, m, s in items if s is not None]
    within = math.sqrt(sum((n - 1) * s * s for n, s in wi) / max(1, sum(n - 1 for n, s in wi))) if wi else None
    if wi:
        tot_ss = sum((n - 1) * (s if s is not None else within) ** 2 + n * (m - M) ** 2 for n, m, s in items)
        total = math.sqrt(tot_ss / (N - 1))
    else:
        total = None
    between = math.sqrt(sum(n * (m - M) ** 2 for n, m, s in items) / N)
    return N, M, within, total, between


def wls(X, y, w):
    import numpy as np
    X, y, w = np.asarray(X, float), np.asarray(y, float), np.asarray(w, float)
    W = np.sqrt(w)
    beta, *_ = np.linalg.lstsq(X * W[:, None], y * W, rcond=None)
    resid = y - X @ beta
    dof = max(1, len(y) - X.shape[1])
    s2 = (w * resid ** 2).sum() / dof
    cov = s2 * np.linalg.pinv((X * w[:, None]).T @ X)
    return beta, np.sqrt(np.diag(cov))


def stage_summarise():
    rows = json.load(open(os.path.join(ROOT, "baselines.json")))
    lines = []
    summ = {}

    def P(s=""):
        lines.append(s)

    usable = [r for r in rows if r["confidence"] in ("high", "medium") and r["sex"] in ("male", "female")]
    gym = [r for r in usable if r["pool"] == "gym_goer"]
    rhos = est_rho(usable)
    rho = sorted(rhos)[len(rhos) // 2] if len(rhos) >= 5 else 0.5
    P(f"# Baseline strength of study participants (pooled)\n")
    P(f"Rows in baselines.json: {len(rows)} from {len({r['pmcid'] for r in rows})} articles; usable (high/medium confidence, single-sex): {len(usable)}; "
      f"gym-goer pool: {len(gym)} rows / {len({r['pmcid'] for r in gym})} articles / {sum(r['n'] for r in gym)} participant-lift entries.")
    P(f"Commercial-OK licences (CC BY / CC BY-SA / CC0) in gym pool: {len({r['pmcid'] for r in gym if r['commercial_ok']})} articles.")
    P(f"Median implied corr(1RM, BM) from groups reporting relative SD: {rho:.2f} (k={len(rhos)}) -> used for delta-method SD of 1RM/BM where not reported.\n")

    for subset_name, subset in (("commercial_ok", [r for r in gym if r["commercial_ok"]]), ("all_licences", gym)):
        P(f"\n## Relative strength (1RM / body mass) by lift x sex x training status — {subset_name}\n")
        P("| lift | sex | status | studies | groups | N | mean BM kg | mean 1RM kg | rel mean | within-SD | total SD | CV | between-study SD | rel range of group means |")
        P("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        tab = []
        for ex in EX_ORDER:
            for sex in ("male", "female"):
                for st in STATUS_ORDER + ["all gym-goer statuses"]:
                    rs = [r for r in subset if r["exercise"] == ex and r["sex"] == sex and (st == "all gym-goer statuses" or r["training_status"] == st)]
                    if ex in CORE_VARIANT:
                        rs = [r for r in rs if r["variant"] in CORE_VARIANT[ex]]
                    if not rs:
                        continue
                    items = []
                    for r in rs:
                        sd, how = rel_sd(r, rho)
                        items.append((r["n"], r["relative"], sd))
                    N, M, within, total, between = pool_stats(items)
                    _, BMm, _, _, _ = pool_stats([(r["n"], r["body_mass_kg"]["mean"], r["body_mass_kg"]["sd"]) for r in rs])
                    _, RMm, _, _, _ = pool_stats([(r["n"], r["one_rm_kg"]["mean"], r["one_rm_kg"]["sd"]) for r in rs])
                    k = len({r["pmcid"] for r in rs})
                    rec = dict(lift=ex, sex=sex, status=st, studies=k, groups=len(rs), N=N, bm=BMm, rm=RMm, rel=M, within_sd=within,
                               total_sd=total, cv=(total / M if total else None), between_sd=between,
                               rel_min=min(r["relative"] for r in rs), rel_max=max(r["relative"] for r in rs))
                    tab.append(rec)
                    f = lambda x, d=2: "–" if x is None else f"{x:.{d}f}"
                    P(f"| {ex} | {sex} | {st} | {k} | {len(rs)} | {N} | {BMm:.1f} | {RMm:.1f} | **{M:.2f}** | {f(within)} | {f(total)} | {f(rec['cv'])} | {between:.2f} | {rec['rel_min']:.2f}–{rec['rel_max']:.2f} |")
        summ[f"relative_{subset_name}"] = tab

    # ---- ratios between lifts in the same participants
    P("\n## Ratios between lifts measured in the same participant group (gym-goer pool, all licences; n-weighted)\n")
    pairs = [("squat", "bench"), ("deadlift", "bench"), ("deadlift", "squat"), ("ohp", "bench"), ("row", "bench"), ("pulldown", "bench"),
             ("curl", "bench"), ("legpress", "squat"), ("legext", "squat"), ("legcurl", "legext"), ("hipthrust", "squat"), ("legpress", "bench"), ("legext", "legpress"), ("legcurl", "legpress")]
    bygroup = defaultdict(dict)
    for r in usable:
        if r["pool"] != "gym_goer":
            continue
        if r["exercise"] in CORE_VARIANT and r["variant"] not in CORE_VARIANT[r["exercise"]]:
            continue
        bygroup[(r["pmcid"], r["source_table"], r["group"], r["sex"])].setdefault(r["exercise"], r)
    P("| ratio | sex | groups | studies | N | weighted mean | min | max | commercial-OK studies |")
    P("|---|---|---|---|---|---|---|---|---|")
    rat = []
    for a, b in pairs:
        for sex in ("male", "female"):
            vals = [(g[a]["n"], g[a]["one_rm_kg"]["mean"] / g[b]["one_rm_kg"]["mean"], k[0], g[a]["commercial_ok"]) for k, g in bygroup.items() if k[3] == sex and a in g and b in g]
            if not vals:
                continue
            N = sum(v[0] for v in vals)
            M = sum(v[0] * v[1] for v in vals) / N
            rat.append(dict(ratio=f"{a}:{b}", sex=sex, groups=len(vals), studies=len({v[2] for v in vals}), N=N, mean=M,
                            min=min(v[1] for v in vals), max=max(v[1] for v in vals)))
            P(f"| {a}:{b} | {sex} | {len(vals)} | {len({v[2] for v in vals})} | {N} | **{M:.2f}** | {min(v[1] for v in vals):.2f} | {max(v[1] for v in vals):.2f} | {len({v[2] for v in vals if v[3]})} |")
    summ["ratios"] = rat

    # ---- body-mass scaling
    P("\n## Body-mass scaling across study groups (gym-goer pool, all licences)\n")
    P("Allometric fit across group means: log(1RM) = a + b·log(BM) + training-status offsets (WLS, weights = n). "
      "b is an ecological (between-group) exponent; groups of heavier people also differ in other ways.\n")
    P("| lift | sex | groups | b (± SE) | b without status terms | rel. strength by group-mean BM bin (N-weighted) |")
    P("|---|---|---|---|---|---|")
    scal = []
    for ex in ["bench", "squat", "deadlift", "ohp", "row", "curl", "legpress", "legext", "legcurl", "hipthrust", "pulldown"]:
        for sex in ("male", "female"):
            rs = [r for r in gym if r["exercise"] == ex and r["sex"] == sex and r["training_status"] in STATUS_ORDER]
            if ex in CORE_VARIANT:
                rs = [r for r in rs if r["variant"] in CORE_VARIANT[ex]]
            if len(rs) < 6:
                continue
            sts = [s for s in STATUS_ORDER if any(r["training_status"] == s for r in rs)]
            X, y, w, X0 = [], [], [], []
            for r in rs:
                X.append([1.0, math.log(r["body_mass_kg"]["mean"])] + [1.0 if r["training_status"] == s else 0.0 for s in sts[1:]])
                X0.append([1.0, math.log(r["body_mass_kg"]["mean"])])
                y.append(math.log(r["one_rm_kg"]["mean"]))
                w.append(r["n"])
            beta, se = wls(X, y, w)
            beta0, se0 = wls(X0, y, w)
            bins = ([0, 65, 75, 85, 95, 999] if sex == "male" else [0, 55, 62, 70, 999])
            bt = []
            for lo, hi in zip(bins[:-1], bins[1:]):
                bb = [r for r in rs if lo <= r["body_mass_kg"]["mean"] < hi]
                if bb:
                    N = sum(r["n"] for r in bb)
                    bt.append(f"{'<' + str(hi) if lo == 0 else (str(lo) + '+' if hi == 999 else f'{lo}-{hi}')}kg: {sum(r['n'] * r['relative'] for r in bb) / N:.2f} (k={len(bb)})")
            scal.append(dict(lift=ex, sex=sex, groups=len(rs), b=float(beta[1]), se=float(se[1]), b_raw=float(beta0[1]), se_raw=float(se0[1]), bins=bt))
            P(f"| {ex} | {sex} | {len(rs)} | {beta[1]:.2f} ± {se[1]:.2f} | {beta0[1]:.2f} ± {se0[1]:.2f} | {'; '.join(bt)} |")
    summ["scaling"] = scal

    # ---- elite/excluded list
    P("\n## Excluded from gym-goer pool (kept separately)\n")
    P("| pool | lift | sex | status | studies | N | rel mean |")
    P("|---|---|---|---|---|---|---|")
    for pool in ("excluded_elite",):
        for ex in EX_ORDER:
            for sex in ("male", "female"):
                rs = [r for r in usable if r["pool"] == pool and r["exercise"] == ex and r["sex"] == sex]
                if ex in CORE_VARIANT:
                    rs = [r for r in rs if r["variant"] in CORE_VARIANT[ex]]
                if not rs:
                    continue
                for st in sorted({r["training_status"] for r in rs}):
                    r2 = [r for r in rs if r["training_status"] == st]
                    N = sum(r["n"] for r in r2)
                    P(f"| {pool} | {ex} | {sex} | {st} | {len({r['pmcid'] for r in r2})} | {N} | {sum(r['n'] * r['relative'] for r in r2) / N:.2f} |")
    open(os.path.join(ROOT, "summary.md"), "w").write("\n".join(lines) + "\n")
    json.dump(dict(rho=rho, k_rho=len(rhos), **summ), open(os.path.join(ROOT, "summary.json"), "w"), indent=1)
    print("\n".join(lines))


if __name__ == "__main__":
    stage = sys.argv[1] if len(sys.argv) > 1 else "help"
    if stage == "search":
        stage_search()
    elif stage == "fetch":
        stage_fetch(*(sys.argv[2:3]))
    elif stage == "extract":
        stage_extract(set(sys.argv[2:]) or None)
    elif stage == "build":
        stage_build()
    elif stage == "summarise":
        stage_summarise()
    elif stage == "all":
        stage_extract()
        stage_build()
        stage_summarise()
    else:
        print(__doc__)
