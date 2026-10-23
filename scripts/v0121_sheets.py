#!/usr/bin/env python3
"""v0.12.1 polish contact sheet: before (out/shots/v0121-before, v0.12.0 code) vs after
(out/shots, V121PolishScreenshotTest on v0.12.1). Names may carry a crop: 'name#x0,y0,x1,y1'."""
import os
from PIL import Image, ImageDraw, ImageFont

D = os.path.join(os.path.dirname(__file__), '..', 'out', 'shots')
BG = (34, 34, 40)
try:
    FONT = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 26)
    BIG = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 34)
except OSError:
    FONT = BIG = ImageFont.load_default()


def load(name):
    name, _, crop = name.partition('#')
    p = os.path.join(D, name + '.png')
    if not os.path.exists(p):
        return None
    im = Image.open(p).convert('RGBA')
    if crop:
        im = im.crop(tuple(int(v) for v in crop.split(',')))
    return im


def sheet(out, rows, scale=0.3, gap=24, label=40):
    cells = []
    for r in rows:
        if isinstance(r, str):
            cells.append(r)
            continue
        row = []
        for cap, n, s in r:
            im = load(n)
            if im:
                s = s or scale
                row.append((cap, im.resize((int(im.width * s), int(im.height * s)), Image.LANCZOS)))
        if row:
            cells.append(row)
    W = max(sum(im.width for _, im in r) + gap * (len(r) + 1) for r in cells if not isinstance(r, str))
    H = sum((60 if isinstance(r, str) else max(im.height for _, im in r) + label + gap) for r in cells) + gap
    img = Image.new('RGBA', (W, H), BG)
    dr = ImageDraw.Draw(img)
    y = gap
    for r in cells:
        if isinstance(r, str):
            dr.text((gap, y + 10), r, fill=(255, 210, 120), font=BIG)
            y += 60
            continue
        x = gap
        for cap, im in r:
            dr.text((x, y), cap, fill=(235, 235, 240), font=FONT)
            img.alpha_composite(im, (x, y + label))
            x += im.width + gap
        y += max(im.height for _, im in r) + label + gap
    img.convert('RGB').save(os.path.join(D, out))
    print(out, img.size)


B = 'v0121-before/v0121-'
A = 'v0121-'


def pair(screen, variants=('light-100', 'dark-100', 'light-130', 'dark-130'), crop='', s=None):
    return [(f'{k} {v}', f'{p}{screen}-{v}{crop}', s) for v in variants for k, p in (('before', B), ('after', A))]


Z = 1.0
sheet('v0121-polish.png', [
    '1 · No list-colour stripe on task rows (tinted ring + list dot stay)',
    pair('today', ('light-100', 'dark-100'), '#0,480,1081,1000', 0.5),
    '2 · Selected chip: clean solid fill, no white line inside the top edge',
    pair('today', ('light-100', 'dark-100'), '#40,390,340,530', Z),
    '3 · Nothing of the list shows below the composer pill',
    pair('today', ('light-130', 'dark-100'), '#0,2141,1081,2401', 0.5),
    '4 · Font scale 1.3: tabs, stat tiles and panel actions no longer truncate or clip',
    pair('overview', ('light-130', 'dark-130'), '#0,0,1081,1080', 0.4),
    pair('panel', ('light-100', 'light-130'), '#0,2071,1081,2301', 0.5),
    '5 · Plan your day: the swipe hint no longer peeks out under the stacked cards',
    pair('plan', ('light-130', 'dark-100'), '#0,750,1081,1700', 0.4),
    'Full screens (after) · light / dark × font 1.0 / 1.3',
    [(f'{n} {v}', f'{A}{n}-{v}', 0.22) for n in ('today', 'overview', 'upcoming', 'done') for v in ('light-100', 'dark-130')],
    [(f'{n} {v}', f'{A}{n}-{v}', 0.22) for n in ('search', 'panel', 'lists', 'capture') for v in ('light-100', 'dark-130')],
    [(f'{n} {v}', f'{A}{n}-{v}', 0.22) for n in ('plan', 'settings', 'settingsdata', 'reveal') for v in ('light-100', 'dark-130')],
])
