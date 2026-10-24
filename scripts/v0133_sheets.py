#!/usr/bin/env python3
"""v0.13.3 contact sheet: the profile-circle Settings entry on Home (ProfileEntryTest)."""
import importlib.util
import os

_spec = importlib.util.spec_from_file_location('v013_sheets', os.path.join(os.path.dirname(__file__), 'v013_sheets.py'))
_m = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_m)
sheet = _m.sheet

TOP = '#0,0,1081,470'      # 412 dp: top bar + greeting row
TOP360 = '#0,0,945,520'    # 360 dp
ZOOM = '#890,212,1050,372'  # the circle at 412 dp, 1.0x
P = 'v0133-settings-entry-'

sheet('v0133-settings-entry.png', [
    'Name set ("Sai"), 1.0x: light | dark',
    [(f'{t} 100%', f'{P}{t}-100{TOP}', 0.5) for t in ('light', 'dark')],
    'Empty name: person glyph',
    [(f'{t} 100% empty', f'{P}{t}-100-empty{TOP}', 0.5) for t in ('light', 'dark')],
    'Font scale 1.3 ("Sai Krishna" wraps; circle stays put)',
    [('light 130%', f'{P}light-130{TOP}', 0.5), ('dark 130%', f'{P}dark-130{TOP}', 0.5)],
    [('light 130% empty', f'{P}light-130-empty{TOP}', 0.5), ('emoji name', f'{P}light-100-emoji{TOP}', 0.5)],
    '360 dp',
    [('light 130% 360dp', f'{P}light-130-360{TOP360}', 0.38), ('dark 130% 360dp', f'{P}dark-130-360{TOP360}', 0.38),
     ('empty 130% 360dp', f'{P}light-130-empty-360{TOP360}', 0.38), ('dark 100% 360dp', f'{P}dark-100-360{TOP360}', 0.38)],
    'Close-ups: letter, glyph, emoji, Devanagari',
    [(cap, f'{P}{n}{ZOOM}', 1.3) for cap, n in (('light', 'light-100'), ('dark', 'dark-100'), ('empty', 'light-100-empty'),
                                                ('empty dark', 'dark-100-empty'), ('emoji', 'light-100-emoji'), ('Devanagari', 'light-100-devanagari'))],
])
