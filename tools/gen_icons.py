"""Generates OneNet's quick-settings icons.

Usage: python3 tools/gen_icons.py app/src/main/res/drawable [svg-preview-dir]

Geometry fitted to One UI 9's own Wi-Fi tile glyph, side by side in the same quick
panel: three round-capped arcs about the dot, half-angle ~47.5deg, stroke 2.2/24,
dot r 1.4/24. States follow
Pixel's Internet tile (level icons, "!" for no internet, globe ?/x when offline).
"""
import math, os, sys

OUT_RES = sys.argv[1]
OUT_SVG = sys.argv[2] if len(sys.argv) > 2 else None
os.makedirs(OUT_RES, exist_ok=True)
if OUT_SVG: os.makedirs(OUT_SVG, exist_ok=True)
DIM = 0.3
f = lambda v: f"{v:.2f}".rstrip('0').rstrip('.')

# Wi-Fi
CX, CY, DOT_R, WIFI_STROKE, HALF = 12.0, 18.8, 1.4, 2.2, math.radians(47.5)
STROKE, DATA_STROKE = 2.0, 1.9
WIDTHS = {'stroke': STROKE, 'stroke-wifi': WIFI_STROKE, 'stroke-data': DATA_STROKE}
RADII = (4.9, 9.35, 13.7)

def arc(r):
    dx, dy = r * math.sin(HALF), r * math.cos(HALF)
    return f"M{f(CX-dx)},{f(CY-dy)}A{f(r)},{f(r)} 0 0 1 {f(CX+dx)},{f(CY-dy)}"

def circle(cx, cy, r):
    return f"M{f(cx-r)},{f(cy)}a{f(r)},{f(r)} 0 1 1 {f(2*r)},0a{f(r)},{f(r)} 0 1 1 {f(-2*r)},0"

def rrect(x, y, w, h, r):
    return (f"M{f(x+r)},{f(y)}H{f(x+w-r)}A{f(r)},{f(r)} 0 0 1 {f(x+w)},{f(y+r)}V{f(y+h-r)}"
            f"A{f(r)},{f(r)} 0 0 1 {f(x+w-r)},{f(y+h)}H{f(x+r)}A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y+h-r)}"
            f"V{f(y+r)}A{f(r)},{f(r)} 0 0 1 {f(x+r)},{f(y)}Z")

def bang(x, top, bottom, dot_y):
    """Exclamation mark: (stroke path, dot path)."""
    return f"M{f(x)},{f(top)}V{f(bottom)}", circle(x, dot_y, 1.1)

# items: (kind, d, alpha) kind: 'fill' | 'stroke'
def wifi(level, no_internet=False):
    items = []
    for i, r in enumerate(RADII):
        items.append(('stroke-wifi', arc(r), 1.0 if level >= i + 2 else DIM))
    items.append(('fill', circle(CX, CY, DOT_R), 1.0 if level >= 1 else DIM))
    if no_internet:
        s, d = bang(21.4, 13.2, 16.2, 19.1)
        items += [('stroke', s, 1.0), ('fill', d, 1.0)]
    return items

# Mobile data: One UI 9's down/up arrows, traced from its own quick panel tile.
DATA = ("M8.18,5.5V18.6M4.48,14.9L8.18,18.6L11.88,14.9"
        "M16.23,18.6V5.5M12.53,9.2L16.23,5.5L19.93,9.2")

def data(no_internet=False, dim=False):
    items = [('stroke-data', DATA, 0.4 if dim else 1.0)]
    if no_internet:
        s_, d_ = bang(21.3, 12.4, 15.6, 18.6)
        items += [('stroke', s_, 1.0), ('fill', d_, 1.0)]
    return items

# Searching: arcs light up outwards and fade, on a loop (Pixel's ic_wifi_connecting, in steps).
SEARCH_WAVE = [(1, DIM, DIM), (1, 1, DIM), (1, 1, 1), (DIM, 1, 1), (DIM, DIM, 1), (DIM, DIM, DIM)]

def wifi_search(frame):
    alphas = SEARCH_WAVE[frame]
    return [('stroke-wifi', arc(r), a) for r, a in zip(RADII, alphas)] + [('fill', circle(CX, CY, DOT_R), 1.0)]

ETHERNET = [('fill', "M7.77,6.76L6.23,5.48 0.82,12l5.41,6.52 1.54,-1.28L3.42,12l4.35,-5.24zM7,13h2v-2H7v2zm10,-2h-2v2h2v-2zm-6,2h2v-2h-2v2zm6.77,-7.52l-1.54,1.28L20.58,12l-4.35,5.24 1.54,1.28L23.18,12l-5.41,-6.52z", 1.0)]

def globe(alpha=1.0):
    # One path, so overlapping strokes don't stack alpha at the crossings.
    return [('stroke', circle(12, 12, 9)
             + "M12,3C9.6,5.4 8.2,8.6 8.2,12S9.6,18.6 12,21C14.4,18.6 15.8,15.4 15.8,12S14.4,5.4 12,3Z"
             + "M3.4,9H20.6M3.4,15H20.6", alpha)]

GLOBE = globe()
BADGE = (18.6, 18.6)
# Clip a hole around the badge so the dim globe doesn't run into it.
BADGE_CLIP = ("M0,0H24V24H0Z"
              f"M{f(BADGE[0])},{f(BADGE[1] - 5.6)}a5.6,5.6 0 1 0 0.01,0Z")

def no_internet(available):
    if available:
        mark = [('stroke', f"M16.9,16.6a1.8,1.8 0 1 1 2.6,1.6c-0.6,0.35 -0.9,0.75 -0.9,1.45", 1.0),
                ('fill', circle(18.6, 22.3, 1.0), 1.0)]
    else:
        mark = [('stroke', "M16.4,16.4L20.8,20.8M20.8,16.4L16.4,20.8", 1.0)]
    return [('clip', BADGE_CLIP, 1.0)] + globe(DIM) + [('endclip', '', 1.0)] + mark

def vector_xml(items, size=24, viewport=24, group=None):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{size}dp"', f'    android:height="{size}dp"',
             f'    android:viewportWidth="{viewport}"', f'    android:viewportHeight="{viewport}">']
    indent = '    '
    if group:
        lines.append(f'    <group {group}>'); indent = '        '
    for kind, d, a in items:
        if kind == 'clip':
            lines.append(f'{indent}<group>')
            indent += '    '
            lines.append(f'{indent}<clip-path android:pathData="{d}" />')
            continue
        if kind == 'endclip':
            indent = indent[:-4]
            lines.append(f'{indent}</group>')
            continue
        attrs = [f'android:pathData="{d}"']
        if kind == 'fill':
            attrs.append('android:fillColor="#FFFFFFFF"')
            if a < 1: attrs.append(f'android:fillAlpha="{a}"')
        else:
            width = WIDTHS[kind]
            attrs += ['android:fillColor="#00000000"', 'android:strokeColor="#FFFFFFFF"',
                      f'android:strokeWidth="{f(width)}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
            if a < 1: attrs.append(f'android:strokeAlpha="{a}"')
        lines.append(f'{indent}<path\n' + '\n'.join(f'{indent}    {x}' for x in attrs) + ' />')
    if group: lines.append('    </group>')
    lines.append('</vector>')
    return '\n'.join(lines) + '\n'

def svg(items, color):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">']
    for kind, d, a in items:
        if kind == 'clip':
            out.append(f'<clipPath id="c"><path d="{d}" clip-rule="evenodd" fill-rule="evenodd"/></clipPath><g clip-path="url(#c)">')
            continue
        if kind == 'endclip':
            out.append('</g>')
            continue
        if kind == 'fill':
            out.append(f'<path d="{d}" fill="{color}" fill-opacity="{a}"/>')
        else:
            width = WIDTHS[kind]
            out.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-opacity="{a}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>')
    out.append('</svg>')
    return ''.join(out)

icons = {}
for l in range(5):
    icons[f'ic_qs_wifi_{l}'] = wifi(l)
    icons[f'ic_qs_wifi_no_internet_{l}'] = wifi(l, True)
for frame in range(len(SEARCH_WAVE)):
    icons[f'ic_qs_wifi_search_{frame}'] = wifi_search(frame)
icons['ic_qs_data'] = data()
icons['ic_qs_data_no_internet'] = data(no_internet=True)
icons['ic_qs_data_off'] = data(dim=True)
icons['ic_qs_no_internet_available'] = no_internet(True)
icons['ic_qs_no_internet_unavailable'] = no_internet(False)
icons['ic_qs_ethernet'] = ETHERNET
icons['ic_qs_globe'] = GLOBE
# Panel glyphs.
icons['ic_lock'] = [('stroke', "M8.5,10.5V8a3.5,3.5 0 0 1 7,0v2.5", 1.0), ('fill', rrect(5.5, 10.5, 13, 10, 2.4), 1.0)]
icons['ic_chevron_right'] = [('stroke', "M9.5,5.5L16,12L9.5,18.5", 1.0)]
icons['ic_settings'] = [('fill', "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z", 1.0)]

for name, items in icons.items():
    open(f'{OUT_RES}/{name}.xml', 'w').write(vector_xml(items))
    if OUT_SVG: open(f'{OUT_SVG}/{name}.svg', 'w').write(svg(items, '#2b2b2b'))

# Launcher foreground: the full Wi-Fi glyph, scaled into the adaptive-icon safe zone.
scale = 2.3
open(f'{OUT_RES}/ic_launcher_foreground.xml', 'w').write(vector_xml(
    wifi(4), size=108, viewport=108,
    group=f'android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{f(54 - 12 * scale)}" android:translateY="{f(54 - 12.1 * scale)}"'))

# Launcher background: One UI blue, lit from the top left.
open(f'{OUT_RES}/ic_launcher_background.xml', 'w').write('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="18"
                android:startY="10"
                android:endX="90"
                android:endY="98"
                android:startColor="#FF5B9BFF"
                android:endColor="#FF2557E8" />
        </aapt:attr>
    </path>
</vector>
''')
