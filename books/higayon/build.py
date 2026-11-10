# בונה את קובץ ספר ההגיון: python3 build.py <יעד>
import sys, os
here = os.path.dirname(os.path.abspath(__file__))
for f in ('gen1.py', 'gen2.py', 'gen3.py'):
    exec(open(os.path.join(here, f), encoding='utf-8').read())

aspects = {}

def esc(s, attr):
    s = s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    if attr:
        s = s.replace('"', '&quot;').replace('\n', '&#10;')
    return s

def info(out, ind, name, text):
    out.append('%s<kvInfo kind="user" name="%s">%s</kvInfo>' % (ind, esc(name, True), esc(text, False)))

count = [0]

def item(n, depth, out):
    count[0] += 1
    ind = '  ' * depth
    kids = []
    for x in L(n['d']): kids.append(('הגדרה', x))
    for x in L(n['p']): kids.append(('פירוש', x))
    for x in L(n['ex']): kids.append(('דוגמה', x))
    for x in L(n['r']): kids.append(('כלל', x))
    for k, x in n['h']: kids.append((k, x))
    if n['src']: kids.append(('מקור', n['src']))
    if n['note']: kids.append(('הערה', n['note']))
    divs = n['divs']
    if not kids and not divs:
        out.append('%s<outline text="%s"/>' % (ind, esc(n['name'], True)))
        return
    out.append('%s<outline text="%s">' % (ind, esc(n['name'], True)))
    for k, x in kids: info(out, ind + '  ', k, x)
    single = len(divs) == 1 and divs[0][0] == ''
    for aspect, children in divs:
        if single:
            for c in children: item(c, depth + 1, out)
        else:
            if aspect and aspect not in aspects: aspects[aspect] = 'b%d' % (len(aspects) + 1)
            a = (' kvAspect="%s"' % aspects[aspect]) if aspect else ''
            out.append('%s  <outline kvDivision="yes" text="%s"%s>' % (ind, esc(aspect, True), a))
            for c in children: item(c, depth + 2, out)
            out.append('%s  </outline>' % ind)
    out.append('%s</outline>' % ind)

body = []
item(root, 2, body)
head = ['קבוצות ותגיות · קובץ מפה', '<?xml version="1.0" encoding="UTF-8"?>', '<opml version="2.0">', '  <head>',
        '    <title>ספר ההגיון</title>', '    <kv version="1" absolute="no"/>', '    <kvAspects>']
for name, id in aspects.items():
    head.append('      <kvAspect id="%s" name="%s"/>' % (id, esc(name, True)))
head += ['    </kvAspects>', '  </head>', '  <body>']
text = '\n'.join(head + body + ['  </body>', '</opml>', ''])
open(sys.argv[1], 'w', encoding='utf-8').write(text)
print('items', count[0], 'aspects', len(aspects), 'chars', len(text))
