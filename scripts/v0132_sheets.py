#!/usr/bin/env python3
"""v0.13.2 contact sheets: top bar (TopBarTest) and the Settings-entry mocks (SettingsOptionsMockTest)."""
import importlib.util
import os

_spec = importlib.util.spec_from_file_location('v013_sheets', os.path.join(os.path.dirname(__file__), 'v013_sheets.py'))
_m = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_m)
sheet = _m.sheet

TOP = '#0,0,1081,430'      # 412 dp: top bar + greeting / page title
TOP360 = '#0,0,945,430'    # 360 dp

rows = []
for tab in ('home', 'todo', 'calendar'):
    rows.append(f'{tab.capitalize()}: search | pill | theme toggle')
    rows.append([(f'{t} {s}%', f'v0132-topbar-{tab}-{t}-{s}{TOP}', 0.5) for s in ('100', '130') for t in ('light', 'dark')])
rows.append('Narrow 360 dp (Home / Todo / Calendar, 1.3x)')
rows.append([(f'{tab} {t} 130% 360dp', f'v0132-topbar-{tab}-{t}-130-360{TOP360}', 0.38) for tab in ('home', 'todo', 'calendar') for t in ('light',)])
rows.append([(f'{tab} {t} 130% 360dp', f'v0132-topbar-{tab}-{t}-130-360{TOP360}', 0.38) for tab in ('home', 'todo', 'calendar') for t in ('dark',)])
sheet('v0132-topbar.png', rows)

opts = [('now (gear)', '0-current'), ('a) initial circle', 'a-initial'), ('b) row at end', 'b-row'), ('c) top-left slot', 'c-topleft')]
sheet('v0132-settings-options.png', [
    'Settings entry options (mocks only, nothing ships)',
    [(cap, f'v0132-settings-option-{n}-light', 0.3) for cap, n in opts],
    [(cap, f'v0132-settings-option-{n}-dark', 0.3) for cap, n in opts],
])
