import sys, re, html
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
s = re.sub(r"</(p|title|tr|caption|license-p|td|th)>", lambda m: "\n" if m.group(1) in ("p","title","tr","caption","license-p") else " | ", s)
s = re.sub(r"<[^>]+>", "", s)
s = html.unescape(s)
s = re.sub(r"\n\s*\n+", "\n", s)
open(sys.argv[2], "w").write(s)
