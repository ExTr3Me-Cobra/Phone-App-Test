import re, html, sys
t = open(sys.argv[1], encoding='utf-8').read()
def clean(s):
    s = re.sub(r'<[^>]+>', ' ', s)
    return re.sub(r'\s+', ' ', html.unescape(s)).strip()
for k, tw in enumerate(re.findall(r'<table-wrap.*?</table-wrap>', t, flags=re.S)):
    cap = re.search(r'<caption>(.*?)</caption>', tw, flags=re.S)
    lab = re.search(r'<label>(.*?)</label>', tw, flags=re.S)
    print('=== TABLE', k, clean(lab.group(1)) if lab else '', '|', clean(cap.group(1)) if cap else '')
    for tr in re.findall(r'<tr.*?</tr>', tw, flags=re.S):
        cells = [clean(c) for c in re.findall(r'<t[hd][^>]*>(.*?)</t[hd]>', tr, flags=re.S)]
        print(' | '.join(cells))
