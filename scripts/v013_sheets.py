#!/usr/bin/env python3
"""v0.13.0 contact sheets from V13ScreenshotTest (out/shots/v013-*). Names may carry a crop: 'name#x0,y0,x1,y1'."""
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



M = ('light', 'dark')


def screens(names, s=0.22):
    short = lambda n: n.replace('composer-', '').replace('calendar-', '').replace('capture-', 'cap ')
    return [(f'{short(n)} {m[0].upper()}', f'v013-{n}-{m}', s) for n in names for m in M]


def strip(name, s=0.6):
    return [(f'{name} filmstrip {m}', f'v013-{name}-filmstrip-{m}', s) for m in M]


sheet('v013-switcher.png', [
    'View switcher: glass pills for Today / Upcoming / Recurring / Done',
    screens(('today', 'switcher-open', 'recurring')),
    screens(('upcoming', 'done', 'home')),
    'Switcher opening · 24 ms per frame',
    strip('switcher')[:1], strip('switcher')[1:],
])
sheet('v013-calendar.png', [
    'Calendar page: week strip, pull to month, day list with projected repeats',
    screens(('calendar-week', 'calendar-month', 'calendar-day'), 0.25),
    'Week -> month pull · 32 ms per frame',
    strip('calendar-pull')[:1], strip('calendar-pull')[1:],
])
sheet('v013-add-toolbar.png', [
    'Composer toolbar: Date · Time · Repeat · List · Priority · Reminder · Notes',
    screens(('composer-tools', 'composer-date', 'composer-repeat')),
    screens(('composer-list', 'composer-chips')),
    'Capture card (quick add) with the same toolbar',
    screens(('capture', 'capture-reminder', 'capture-chips')),
    'Composer focus -> toolbar rises · 24 ms per frame',
    strip('toolbar')[:1], strip('toolbar')[1:],
])
