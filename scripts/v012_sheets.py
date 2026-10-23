#!/usr/bin/env python3
"""Assemble the v0.12 contact sheets from the real renders in out/shots (V12ScreenshotTest)."""
import os
from PIL import Image, ImageDraw, ImageFont

D = os.path.join(os.path.dirname(__file__), '..', 'out', 'shots')
BG = (34, 34, 40)
try:
    FONT = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 26)
except OSError:
    FONT = ImageFont.load_default()


def load(name):
    p = os.path.join(D, name + '.png')
    return Image.open(p).convert('RGBA') if os.path.exists(p) else None


def sheet(out, rows, scale=0.5, gap=24, label=40):
    """rows: list of lists of (caption, name, scale override or None)."""
    cells = [[(cap, load(n), s or scale) for cap, n, s in r] for r in rows]
    cells = [[(cap, im.resize((int(im.width * s), int(im.height * s)), Image.LANCZOS)) for cap, im, s in r if im] for r in cells]
    cells = [r for r in cells if r]
    W = max(sum(im.width for _, im in r) + gap * (len(r) + 1) for r in cells)
    H = sum(max(im.height for _, im in r) + label + gap for r in cells) + gap
    img = Image.new('RGBA', (W, H), BG)
    dr = ImageDraw.Draw(img)
    y = gap
    for r in cells:
        x = gap
        for cap, im in r:
            dr.text((x, y), cap, fill=(235, 235, 240), font=FONT)
            img.alpha_composite(im, (x, y + label))
            x += im.width + gap
        y += max(im.height for _, im in r) + label + gap
    img.convert('RGB').save(os.path.join(D, out))
    print(out, img.size)


sheet('v012-overview.png', [
    [('Today (opens here)', 'v012-today-light', None), ('Overview · light', 'v012-overview-light', None), ('Overview · dark', 'v012-overview-dark', None)],
    [('Tab switch Today → Overview · light', 'v012-tab-overview-filmstrip-light', 0.6)],
    [('dark', 'v012-tab-overview-filmstrip-dark', 0.6)],
    [('reduced motion (crossfade)', 'v012-tab-overview-filmstrip-reduced', 0.6)],
])
sheet('v012-categories.png', [
    [('Today · chips + accents', 'v012-chips-today-light', None), ('Work chip', 'v012-chips-work-light', None),
     ('Upcoming', 'v012-chips-upcoming-light', None), ('Done', 'v012-chips-done-light', None)],
    [('dark', 'v012-chips-today-dark', None), ('Work chip', 'v012-chips-work-dark', None),
     ('Upcoming', 'v012-chips-upcoming-dark', None), ('Done', 'v012-chips-done-dark', None)],
    [('Chip filter All → Work · light', 'v012-chip-filter-filmstrip-light', 0.6)],
    [('dark', 'v012-chip-filter-filmstrip-dark', 0.6)],
])
W = 1.0
sheet('v012-widgets.png', [
    [('W1 pill 2x1', 'v012-w1-pill-light', W), ('narrow', 'v012-w1-pill-narrow-light', W), ('W2 round 1x1', 'v012-w2-round-light', W),
     ('W3 quick lists 4x1', 'v012-w3-quick-light', W), ('narrow', 'v012-w3-quick-narrow-light', W)],
    [('dark', 'v012-w1-pill-dark', W), ('', 'v012-w1-pill-narrow-dark', W), ('', 'v012-w2-round-dark', W),
     ('', 'v012-w3-quick-dark', W), ('', 'v012-w3-quick-narrow-dark', W)],
    [('W4 hub 2x2', 'v012-w4-hub-light', W), ('empty', 'v012-w4-hub-empty-light', W), ('wide', 'v012-w4-hub-wide-light', W),
     ('dark', 'v012-w4-hub-dark', W), ('empty', 'v012-w4-hub-empty-dark', W), ('wide', 'v012-w4-hub-wide-dark', W)],
    [('Picker previews (previewLayout)', 'v012-preview-pill-light', W), ('', 'v012-preview-round-light', W), ('', 'v012-preview-quick-light', W),
     ('', 'v012-preview-hub-light', W)],
    [('dark', 'v012-preview-pill-dark', W), ('', 'v012-preview-round-dark', W), ('', 'v012-preview-quick-dark', W), ('', 'v012-preview-hub-dark', W)],
    [('Today widget (unchanged)', 'v012-today-widget-light', W), ('dark', 'v012-today-widget-dark', W)],
    [('Capture · Work preset', 'v012-capture-work-light', 0.4), ('Today preset', 'v012-capture-today-light', 0.4),
     ('Voice preset', 'v012-capture-voice-light', 0.4), ('Work · dark', 'v012-capture-work-dark', 0.4),
     ('Today · dark', 'v012-capture-today-dark', 0.4), ('Voice · dark', 'v012-capture-voice-dark', 0.4)],
])
