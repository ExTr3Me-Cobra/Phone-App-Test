import zipfile, re, sys
z = zipfile.ZipFile(sys.argv[1])
ss = []
if 'xl/sharedStrings.xml' in z.namelist():
    x = z.read('xl/sharedStrings.xml').decode('utf-8')
    for si in re.findall(r'<si>(.*?)</si>', x, flags=re.S):
        ss.append(''.join(re.findall(r'<t[^>]*>(.*?)</t>', si, flags=re.S)))
wb = z.read('xl/workbook.xml').decode('utf-8')
names = re.findall(r'<sheet [^>]*name="([^"]+)"', wb)
for i, nm in enumerate(names, 1):
    p = f'xl/worksheets/sheet{i}.xml'
    if p not in z.namelist(): continue
    x = z.read(p).decode('utf-8')
    print('### SHEET', i, nm)
    maxrows = int(sys.argv[2]) if len(sys.argv) > 2 else 60
    for r, row in enumerate(re.findall(r'<row[^>]*>(.*?)</row>', x, flags=re.S)):
        if r >= maxrows: break
        out = []
        for c in re.finditer(r'<c r="([A-Z]+)\d+"([^>]*?)(?:/>|>(.*?)</c>)', row, flags=re.S):
            ref, attrs, inner = c.group(1), c.group(2), c.group(3) or ''
            v = re.search(r'<v>(.*?)</v>', inner)
            val = v.group(1) if v else ''
            if 't="s"' in attrs and val != '':
                val = ss[int(val)]
            if val != '':
                try: val = str(round(float(val), 2))
                except: pass
                out.append(f'{ref}={val}')
        if out: print(' ; '.join(out))
