#!/usr/bin/env python3
"""Runde 3 der Icon-Entwuerfe fuer "WoW Forever Touch": G Holzschild, H Panzerhandschuh, I Runenstein.

Aufruf:  python tools/icons_build_r3.py [--nur svg|png|sheet] [--entwurf GHI]
Ausgabe: docs/design/icons/<G|H|I>/*.svg, *.png und docs/design/icons/entwuerfe3.png

Handgemalt statt Hochglanz: unregelmaessige Formen (Verzerrung per feTurbulence + feDisplacementMap), matte Flaechen mit Koernung,
Holzmaserung, Gesteinsflecken, Kratzer, abgeplatzte Farbe, kein Glanz, kein Gluehen. Baut auf tools/icons_build.py auf
(Schrift als Pfade, Rendering mit Edge, Kontaktblatt). Alle Formen sind eigene Geometrie, keine Blizzard-Motive.
Nichts in app/src/main/res wird angefasst.
"""
import argparse
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import icons_build as ib  # noqa: E402
from icons_build import OUT, cap_height, fit_text, text_path  # noqa: E402

NAMES = {"G": "Holzschild", "H": "Panzerhandschuh", "I": "Runenstein"}
# Hintergrund je Entwurf: (hell, dunkel, Streiflicht)
BG = {"G": ("#3A2A1B", "#140E09", "#8A5E32"),
      "H": ("#3C2220", "#140D0C", "#8A4A40"),
      "I": ("#2B2C28", "#10110F", "#6A6E62")}
INK = "#1A120B"
OCHRE = "#D3B27A"
OCHRE_D = "#B59A72"
IRON_EDGE = "#15100B"


# ---------------------------------------------------------------- Hilfen
def f2(v):
    return f"{v:.1f}"


def smooth_closed(points):
    """Geschlossener glatter Pfad (Catmull-Rom nach Bezier) durch die Punkte."""
    n = len(points)
    d = f"M{points[0][0]:.1f},{points[0][1]:.1f}"
    for i in range(n):
        p0, p1, p2, p3 = points[i - 1], points[i], points[(i + 1) % n], points[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f" C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}"
    return d + " Z"


def blob(cx, cy, r, n, jit, seed, sx=1.0, sy=1.0):
    rng = random.Random(seed)
    pts = []
    for i in range(n):
        a = 2 * math.pi * (i + rng.uniform(-.18, .18)) / n
        rr = r + rng.uniform(-jit, jit)
        pts.append((cx + sx * rr * math.sin(a), cy - sy * rr * math.cos(a)))
    return smooth_closed(pts)


def polar(r, a, cx=0.0, cy=0.0):
    t = math.radians(a)
    return cx + r * math.sin(t), cy - r * math.cos(t)


def scratches(seed, n, box, lmin, lmax, color, op, wmin=.5, wmax=1.0, angle=None):
    """Kratzer: leicht gekruemmte duenne Linien in einem Rechteck."""
    rng = random.Random(seed)
    x0, y0, x1, y1 = box
    out = []
    for _ in range(n):
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
        ln = rng.uniform(lmin, lmax)
        a = math.radians(angle if angle is not None else rng.uniform(0, 180)) + rng.uniform(-.25, .25)
        dx, dy = ln * math.cos(a), ln * math.sin(a)
        j = rng.uniform(-1.2, 1.2)
        out.append(f'<path d="M{x:.1f},{y:.1f} Q{x + dx / 2 + j:.1f},{y + dy / 2 - j:.1f} {x + dx:.1f},{y + dy:.1f}" '
                   f'stroke="{color}" stroke-width="{rng.uniform(wmin, wmax):.2f}" opacity="{op * rng.uniform(.6, 1):.2f}" fill="none" stroke-linecap="round"/>')
    return "".join(out)


def chips(seed, n, box, rmin, rmax, color):
    """Abgeplatzte Farbe: kleine unregelmaessige Flecken."""
    rng = random.Random(seed)
    x0, y0, x1, y1 = box
    out = []
    for i in range(n):
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
        r = rng.uniform(rmin, rmax)
        out.append(f'<path d="{blob(x, y, r, 7, r * .45, seed * 100 + i, rng.uniform(.7, 1.5), rng.uniform(.6, 1.2))}" fill="{color}"/>')
    return "".join(out)


def noise_filter(fid, freq, seed, octaves, rgb, mul, off):
    r, g, b = (c / 255 for c in rgb)
    return (f'<filter id="{fid}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="{freq}" '
            f'numOctaves="{octaves}" seed="{seed}"/><feColorMatrix type="matrix" values="0 0 0 0 {r:.2f}  0 0 0 0 {g:.2f}  0 0 0 0 {b:.2f}  {mul} 0 0 0 {off}"/></filter>')


def paint_filter(fid, f_low, s_low, f_hi, s_hi, grain, seed, grain_f=".55"):
    """Handgemalt: zweistufige Verzerrung (grosse Welle + feines Wackeln) und Koernung (Multiplikation)."""
    return (f'<filter id="{fid}" x="-12%" y="-12%" width="124%" height="124%">'
            f'<feTurbulence type="fractalNoise" baseFrequency="{f_low}" numOctaves="2" seed="{seed}" result="w1"/>'
            f'<feDisplacementMap in="SourceGraphic" in2="w1" scale="{s_low}" xChannelSelector="R" yChannelSelector="G" result="d1"/>'
            f'<feTurbulence type="fractalNoise" baseFrequency="{f_hi}" numOctaves="2" seed="{seed + 5}" result="w2"/>'
            f'<feDisplacementMap in="d1" in2="w2" scale="{s_hi}" xChannelSelector="G" yChannelSelector="R" result="d2"/>'
            f'<feTurbulence type="fractalNoise" baseFrequency="{grain_f}" numOctaves="3" seed="{seed + 9}" result="g"/>'
            f'<feColorMatrix in="g" type="matrix" values="{grain} 0 0 0 {1 - grain * .5:.2f}  {grain} 0 0 0 {1 - grain * .5:.2f}  {grain} 0 0 0 {1 - grain * .5:.2f}  0 0 0 0 1" result="gm"/>'
            f'<feComposite in="d2" in2="gm" operator="arithmetic" k1="1" k2="0" k3="0" k4="0"/></filter>')


def defs3():
    s = f"""
<linearGradient id="gIron" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#625F58"/><stop offset=".5" stop-color="#403D39"/><stop offset="1" stop-color="#232120"/></linearGradient>
<linearGradient id="gIronH" x1="-40" y1="0" x2="44" y2="0" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#6F6B63"/><stop offset=".55" stop-color="#4A4741"/><stop offset="1" stop-color="#2A2826"/></linearGradient>
<linearGradient id="gCrim" x1="-60" y1="-70" x2="60" y2="90" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#862F32"/><stop offset=".6" stop-color="#6C2528"/><stop offset="1" stop-color="#481619"/></linearGradient>
<linearGradient id="gBrass" x1="0" y1="-1" x2="0" y2="1">
  <stop offset="0" stop-color="#CDAE76"/><stop offset="1" stop-color="#8E7347"/></linearGradient>
<linearGradient id="gStone" x1="-60" y1="0" x2="60" y2="0" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#9A978B"/><stop offset=".5" stop-color="#77746A"/><stop offset="1" stop-color="#4E4C45"/></linearGradient>
<radialGradient id="gLightG" cx="-34" cy="-40" r="140" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#FFF1D0" stop-opacity=".16"/><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".34"/></radialGradient>
<linearGradient id="rTextM" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#E6D0A0"/><stop offset="1" stop-color="#BFA070"/></linearGradient>
<filter id="fDrop" x="-15%" y="-15%" width="130%" height="140%">
  <feGaussianBlur in="SourceAlpha" stdDeviation="3"/><feOffset dx="1.5" dy="4.5" result="o"/>
  <feComponentTransfer in="o" result="s"><feFuncA type="linear" slope=".6"/></feComponentTransfer>
  <feMerge><feMergeNode in="s"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<filter id="fSoft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation=".9"/></filter>
"""
    s += paint_filter("fPaint", ".022", 5.5, ".075", 2.0, .55, 4)
    s += paint_filter("fPaintT", ".05", 1.6, ".22", .7, .4, 12, ".9")          # Schrift und Zierlinien (Banner/Wallpaper)
    s += paint_filter("fPaintM", ".022", 4.5, ".075", 1.6, 0.0001, 6)           # ohne Koernung (Benachrichtigung)
    s += ('<filter id="fWood" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".085 .006" numOctaves="3" seed="2"/>'
          '<feColorMatrix type="matrix" values="0 0 0 0 .20  0 0 0 0 .10  0 0 0 0 .04  -2.4 0 0 0 1.35"/></filter>')
    s += ('<filter id="fWoodL" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".11 .008" numOctaves="2" seed="8"/>'
          '<feColorMatrix type="matrix" values="0 0 0 0 .78  0 0 0 0 .55  0 0 0 0 .30  2.6 0 0 0 -1.25"/></filter>')
    s += noise_filter("fRockD", ".045", 3, 4, (40, 38, 30), -2.6, 1.45)
    s += noise_filter("fRockL", ".06", 5, 3, (205, 200, 180), 2.8, -1.25)
    s += noise_filter("fRockS", ".5", 7, 2, (20, 18, 14), -3.2, 1.6)
    s += noise_filter("fMoss", ".07", 13, 3, (96, 108, 56), 2.2, -.7)
    return s


# ---------------------------------------------------------------- Hintergrund (dunkel, erdig, gemottelt)
def bg3(k, w, h):
    return (f'<rect width="{w}" height="{h}" fill="url(#bgBase)"/>'
            f'<rect width="{w}" height="{h}" fill="url(#bgSpot)"/>'
            f'<rect width="{w}" height="{h}" filter="url(#fBgM)" opacity=".5"/>'
            f'<rect width="{w}" height="{h}" filter="url(#fBgS)" opacity=".30"/>'
            f'<rect width="{w}" height="{h}" fill="url(#bgVig)"/>')


def bg_defs3(k, w, h):
    lt, dk, spot = BG[k]
    return (f'<linearGradient id="bgBase" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{lt}"/><stop offset="1" stop-color="{dk}"/></linearGradient>'
            f'<radialGradient id="bgSpot" cx=".3" cy=".25" r=".75"><stop offset="0" stop-color="{spot}" stop-opacity=".30"/><stop offset="1" stop-color="{spot}" stop-opacity="0"/></radialGradient>'
            '<radialGradient id="bgVig" cx=".5" cy=".5" r=".78"><stop offset=".5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".55"/></radialGradient>'
            + noise_filter("fBgM", f"{5.0 / w:.4f}", 21, 3, (0, 0, 0), -3.0, 1.55)
            + noise_filter("fBgS", f"{170.0 / w:.4f}", 17, 2, (0, 0, 0), -3.4, 1.7))


# ---------------------------------------------------------------- Embleme (Einheitsraum, Radius ~100 um 0,0)
def fingerprint(cx, cy, n, step, tall, stroke, color, seed, rot=-10, core=3.6):
    """Fingerabdruck aus unterbrochenen, leicht schiefen Rillen (Wirbel)."""
    rng = random.Random(seed)
    g = f'<g transform="translate({cx},{cy}) rotate({rot})" fill="none" stroke="{color}" stroke-width="{stroke}" stroke-linecap="round">'
    g += f'<ellipse rx="{core}" ry="{core * tall:.1f}" fill="{color}" stroke="none"/>'
    for i in range(1, n + 1):
        rx = core + 2.4 + i * step
        ry = rx * tall
        a0 = rng.uniform(0, 360)
        sweep = rng.uniform(235, 310) if i < n else rng.uniform(150, 220)
        x0, y0 = rx * math.sin(math.radians(a0)), -ry * math.cos(math.radians(a0))
        x1, y1 = rx * math.sin(math.radians(a0 + sweep)), -ry * math.cos(math.radians(a0 + sweep))
        g += f'<path d="M{x0:.1f},{y0:.1f} A{rx:.1f},{ry:.1f} 0 {1 if sweep > 180 else 0} 1 {x1:.1f},{y1:.1f}"/>'
    return g + "</g>"


def emblem_G(mono=False):
    if mono:
        return emblem_G_mono()
    rim_o, rim_i = blob(0, 0, 96, 30, 1.4, 1), blob(0, 0, 84, 28, 1.2, 2)
    disc = blob(0, 0, 42, 18, 1.6, 3)
    g = f'<clipPath id="cWood"><path d="{rim_i}"/></clipPath><clipPath id="cCrim"><path d="{disc}"/></clipPath><clipPath id="cShield"><path d="{rim_o}"/></clipPath>'
    # Eisenrand
    g += f'<path d="{rim_o} {rim_i}" fill-rule="evenodd" fill="url(#gIron)" stroke="{IRON_EDGE}" stroke-width="2.4" stroke-linejoin="round"/>'
    rng = random.Random(5)
    for i in range(46):                      # Hammerschlaege auf dem Rand
        a = rng.uniform(0, 360)
        x0, y0 = polar(rng.uniform(86.5, 93.5), a)
        x1, y1 = polar(rng.uniform(86.5, 93.5), a + rng.uniform(2, 5))
        col, op = ("#15110D", .45) if i % 2 else ("#A39E92", .3)
        g += f'<path d="M{x0:.1f},{y0:.1f} L{x1:.1f},{y1:.1f}" stroke="{col}" stroke-width="1.1" opacity="{op}" stroke-linecap="round"/>'
    for i in range(16):                      # Nieten im Rand
        x, y = polar(90, i * 22.5 + rng.uniform(-1.5, 1.5))
        g += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="3.2" fill="#8C6F40" stroke="{INK}" stroke-width="1"/><circle cx="{x + .7:.1f}" cy="{y + .9:.1f}" r="1.5" fill="#4C3A1E" opacity=".6"/>'
    # Holzbretter
    g += '<g clip-path="url(#cWood)">'
    cols = ["#80582F", "#6F4A28", "#876035", "#765230", "#7D5630"]
    xs = [-90, -51.6, -17.2, 17.2, 51.6, 90]
    for i, c in enumerate(cols):
        g += f'<rect x="{xs[i]}" y="-90" width="{xs[i + 1] - xs[i]:.1f}" height="180" fill="{c}"/>'
    g += '<rect x="-90" y="-90" width="180" height="180" filter="url(#fWood)" opacity=".9"/>'
    g += '<rect x="-90" y="-90" width="180" height="180" filter="url(#fWoodL)" opacity=".6"/>'
    for i, x in enumerate(xs[1:-1]):         # Fugen
        w = [1.5, -1.8, 1.2, -1.4]
        g += f'<path d="M{x},-90 Q{x + w[i]:.1f},-30 {x - w[i] / 2:.1f},0 T{x + w[i] / 2:.1f},90" fill="none" stroke="#24150A" stroke-width="2.1"/>'
        g += f'<path d="M{x + 1.5},-90 Q{x + 1.5 + w[i]:.1f},-30 {x + 1.5 - w[i] / 2:.1f},0 T{x + 1.5 + w[i] / 2:.1f},90" fill="none" stroke="#C99A62" stroke-width=".8" opacity=".35"/>'
    g += '<ellipse cx="-34" cy="-30" rx="3.6" ry="6" fill="none" stroke="#3A2210" stroke-width="1.4" opacity=".55"/>'
    g += '<ellipse cx="68" cy="24" rx="3" ry="5" fill="none" stroke="#3A2210" stroke-width="1.3" opacity=".5"/>'
    # Eisenbaender
    for y in (-66, 54):
        g += f'<rect x="-90" y="{y}" width="180" height="12" fill="url(#gIron)" stroke="{IRON_EDGE}" stroke-width="2"/>'
        g += f'<path d="M-90,{y + 3} L90,{y + 3}" stroke="#A39E92" stroke-width=".8" opacity=".3"/>'
        for x in (-51.6, -17.2, 17.2, 51.6):
            g += f'<circle cx="{x}" cy="{y + 6}" r="2.7" fill="#8C6F40" stroke="{INK}" stroke-width=".9"/>'
    # Kratzer und Dellen im Holz
    g += scratches(11, 22, (-80, -80, 80, 80), 6, 22, "#E6C48F", .28, angle=80)
    g += scratches(12, 14, (-80, -80, 80, 80), 5, 16, "#1E1108", .4, angle=75)
    g += '<rect x="-90" y="-90" width="180" height="180" fill="url(#gLightG)"/></g>'
    # rote bemalte Scheibe mit abgeplatzter Farbe und Fingerabdruck
    g += f'<path d="{disc}" fill="url(#gCrim)" stroke="#2B0F10" stroke-width="2.2"/>'
    g += (f'<g clip-path="url(#cCrim)">{chips(21, 7, (-42, -42, 42, 42), 2.2, 5.5, "#8A5A2C")}'
          f'<rect x="-44" y="-44" width="88" height="88" filter="url(#fRockD)" opacity=".5"/>'
          f'{scratches(23, 9, (-38, -38, 38, 38), 5, 14, "#D9A58C", .25)}</g>')
    g += fingerprint(0, 2, 5, 5.3, 1.12, 2.7, OCHRE, 41)
    g += f'<path d="{blob(0, 0, 42, 18, 1.6, 3)}" fill="none" stroke="#2B0F10" stroke-width=".8" opacity=".5" transform="scale(.93)"/>'
    # Gebrauchsspuren am Rand: Dellen
    g += f'<g clip-path="url(#cShield)"><path d="{blob(-78, -48, 7, 7, 3, 31)}" fill="#1A120B" opacity=".25"/><path d="{blob(70, 60, 8, 7, 3, 32)}" fill="#1A120B" opacity=".25"/>'
    g += scratches(15, 12, (-90, -90, 90, 90), 8, 20, "#B8B2A4", .22) + "</g>"
    return g


def emblem_G_mono():
    rim_o, rim_i = blob(0, 0, 96, 24, 1.2, 1), blob(0, 0, 80, 22, 1.2, 2)
    g = '<mask id="mG" maskUnits="userSpaceOnUse" x="-110" y="-110" width="220" height="220"><rect x="-110" y="-110" width="220" height="220" fill="#000"/>'
    g += f'<path d="{rim_o}" fill="#fff"/><path d="{rim_i}" fill="none" stroke="#000" stroke-width="5"/>'
    g += '<rect x="-90" y="-69" width="180" height="5" fill="#000"/><rect x="-90" y="64" width="180" height="5" fill="#000"/>'
    g += fingerprint(0, 2, 3, 8.6, 1.1, 5.2, "#000", 41, core=5)
    g += '</mask>'
    return g + '<rect x="-110" y="-110" width="220" height="220" fill="#fff" mask="url(#mG)"/>'


# ---- H: Panzerhandschuh vor Wappenschild
SHIELD_H = "M-60,-72 L60,-72 L60,4 Q60,52 0,92 Q-60,52 -60,4 Z"
HAND_BODY = "M-31,66 L-37,38 Q-39,24 -26,22 L38,22 Q47,24 45,40 L39,66 Z"
THUMB = "M-34,44 Q-47,36 -46,22 Q-44,10 -36,15 L-24,28 L-24,46 Z"
INDEX = "M-23,28 L-23,-27 Q-23,-40 -13,-40 Q-3,-40 -3,-27 L-3,28 Z"
CUFF = "M-35,64 L35,64 L40,92 L-40,92 Z"


def emblem_H(mono=False):
    if mono:
        return emblem_H_mono()
    g = f'<clipPath id="cSh"><path d="{SHIELD_H}"/></clipPath>'
    # Schild: weinrot, Holz scheint durch abgeplatzte Farbe, Altgold-Rand
    g += f'<path d="{SHIELD_H}" fill="#7C4F27" stroke="{INK}" stroke-width="2.6" stroke-linejoin="round"/>'
    g += (f'<g clip-path="url(#cSh)"><path d="{SHIELD_H}" fill="url(#gCrim)"/>'
          f'<rect x="-64" y="-76" width="128" height="172" filter="url(#fRockD)" opacity=".42"/>'
          f'<rect x="-64" y="-76" width="128" height="172" filter="url(#fWood)" opacity=".35"/>'
          f'{chips(31, 11, (-56, -68, 56, 80), 2.2, 6, "#8A5A2C")}'
          f'{scratches(32, 18, (-58, -68, 58, 70), 6, 18, "#E3A58E", .22, angle=60)}'
          f'{scratches(33, 8, (-58, -68, 58, 70), 6, 16, "#220C0D", .35, angle=60)}'
          f'<path d="{SHIELD_H}" fill="none" stroke="{OCHRE_D}" stroke-width="9"/>'
          f'<path d="{SHIELD_H}" fill="none" stroke="#6E5836" stroke-width="1.3" transform="scale(.915) translate(0,-1.5)"/>'
          f'{scratches(34, 14, (-58, -68, 58, 80), 4, 10, "#3A2C16", .45)}'
          f'<rect x="-64" y="-76" width="128" height="172" fill="url(#gLightG)" opacity=".8"/></g>')
    g += f'<path d="{SHIELD_H}" fill="none" stroke="{INK}" stroke-width="2.2" stroke-linejoin="round"/>'
    # Tipp-Wellen (aufgemalt, ocker)
    cx, cy = -13, -40
    for r, w in ((13, 3.0), (23, 2.6)):
        x0, y0 = polar(r, -68, cx, cy)
        x1, y1 = polar(r, 68, cx, cy)
        g += f'<path d="M{x0:.1f},{y0:.1f} A{r},{r} 0 0 1 {x1:.1f},{y1:.1f}" fill="none" stroke="{INK}" stroke-width="{w + 2.2}" stroke-linecap="round" opacity=".55"/>'
        g += f'<path d="M{x0:.1f},{y0:.1f} A{r},{r} 0 0 1 {x1:.1f},{y1:.1f}" fill="none" stroke="{OCHRE}" stroke-width="{w}" stroke-linecap="round" stroke-dasharray="{r * 1.9:.0f} 2 {r * .5:.0f} 2 200"/>'
    # Panzerhandschuh
    g += '<g transform="translate(-13,-40) scale(.9) translate(13,40)">'
    iron = f'fill="url(#gIronH)" stroke="{IRON_EDGE}" stroke-width="2.4" stroke-linejoin="round"'
    g += f'<path d="{CUFF}" {iron}/>'
    g += f'<path d="M-36,64 L36,64 L37.5,71 L-37.5,71 Z" fill="url(#gBrass)" stroke="{IRON_EDGE}" stroke-width="1.8"/>'
    g += f'<path d="M-39,85 L39,85 L40,92 L-40,92 Z" fill="url(#gBrass)" stroke="{IRON_EDGE}" stroke-width="1.8"/>'
    for x in (-22, -7, 8, 23):
        g += f'<path d="M{x},73 L{x + .5},84" stroke="{IRON_EDGE}" stroke-width="1.6" opacity=".7"/>'
    g += f'<path d="{HAND_BODY}" {iron}/>'
    g += f'<path d="M-33,50 L41,50" stroke="{IRON_EDGE}" stroke-width="1.5" opacity=".7"/><path d="M-34,40 L43,40" stroke="{IRON_EDGE}" stroke-width="1.3" opacity=".55"/>'
    # gekrümmte Finger (Knöchel)
    for x, top, w in ((1, 6, 13.5), (15, 3, 13.5), (29, 8, 14.5)):
        d = f"M{x},26 L{x},{top + 5} Q{x},{top} {x + 5},{top} L{x + w - 5},{top} Q{x + w},{top} {x + w},{top + 5} L{x + w},26 Z"
        g += f'<path d="{d}" {iron}/><path d="M{x + 1},{top + 11} L{x + w - 1},{top + 11}" stroke="{IRON_EDGE}" stroke-width="1.2" opacity=".6"/>'
    g += f'<path d="{THUMB}" {iron}/><path d="M-41,30 L-27,35" stroke="{IRON_EDGE}" stroke-width="1.3" opacity=".6"/>'
    g += f'<path d="{INDEX}" {iron}/>'
    for y in (-18, 0, 15):
        g += f'<path d="M-23,{y} L-3,{y}" stroke="{IRON_EDGE}" stroke-width="1.8"/><path d="M-22,{y + 1.8} L-4,{y + 1.8}" stroke="#A8A398" stroke-width=".7" opacity=".35"/>'
    g += f'<path d="M-21,-30 Q-13,-35 -5,-30" fill="none" stroke="{OCHRE_D}" stroke-width="1.6" opacity=".8"/>'
    g += scratches(36, 16, (-40, -35, 44, 90), 3, 9, "#B8B2A4", .3)
    g += scratches(37, 10, (-40, -35, 44, 90), 3, 8, "#0E0A06", .5)
    g += f'<path d="{blob(26, 56, 9, 7, 3, 77)}" fill="#7B4A22" opacity=".28"/>'     # Rost
    g += f'<path d="{blob(-14, -4, 5, 7, 2, 78, .8, 1.6)}" fill="#7B4A22" opacity=".25"/>'
    return g + '</g>'


def emblem_H_mono():
    g = '<mask id="mH" maskUnits="userSpaceOnUse" x="-110" y="-110" width="220" height="220"><rect x="-110" y="-110" width="220" height="220" fill="#000"/>'
    g += f'<path d="{SHIELD_H}" fill="#fff"/>'
    for r in (13, 23):                       # Tipp-Wellen frei schneiden
        x0, y0 = polar(r, -66, -13, -40)
        x1, y1 = polar(r, 66, -13, -40)
        g += f'<path d="M{x0:.1f},{y0:.1f} A{r},{r} 0 0 1 {x1:.1f},{y1:.1f}" fill="none" stroke="#000" stroke-width="4.8" stroke-linecap="round"/>'
    g += '<g transform="translate(-13,-40) scale(.9) translate(13,40)">'
    for d in (CUFF, HAND_BODY, THUMB, INDEX):   # Gap um den Handschuh
        g += f'<path d="{d}" fill="#000" stroke="#000" stroke-width="7" stroke-linejoin="round"/>'
    g += f'<path d="M-35,64 L35,64 L40,92 L-40,92 Z" fill="#fff"/><path d="{HAND_BODY}" fill="#fff"/><path d="{THUMB}" fill="#fff"/><path d="{INDEX}" fill="#fff"/>'
    for x, top, w in ((1, 6, 13.5), (15, 3, 13.5), (29, 8, 14.5)):
        g += f'<path d="M{x},26 L{x},{top + 5} Q{x},{top} {x + 5},{top} L{x + w - 5},{top} Q{x + w},{top} {x + w},{top + 5} L{x + w},26 Z" fill="#fff" stroke="#000" stroke-width="2.6"/>'
    for y in (-14, 6):
        g += f'<path d="M-24,{y} L-2,{y}" stroke="#000" stroke-width="3"/>'
    g += '<path d="M-34,64 L36,64" stroke="#000" stroke-width="3"/></g>'
    g += '</mask>'
    return g + '<rect x="-110" y="-110" width="220" height="220" fill="#fff" mask="url(#mH)"/>'


# ---- I: Runenstein mit Handabdruck
STONE_PTS = [(-54, 92), (-60, 52), (-58, 0), (-52, -38), (-40, -64), (-16, -76), (2, -93), (22, -76), (44, -60), (57, -32), (61, 10), (57, 52), (54, 92)]
RUNES = ["M-3,-9 L-3,9 M-3,-3 L6,-10",
         "M0,-10 L7,0 L0,10 L-7,0 Z M0,10 L0,15",
         "M-5,-9 L5,0 L-5,9 M6,-9 L6,9",
         "M-5,-9 L-5,9 M-5,-9 L6,-1 L-5,6"]


def hand_shapes(sc=1.0):
    """Handabdruck (rechte Hand, Finger nach oben) als Liste (Pfad/Linie, Breite)."""
    return [("M-17,8 L-19,-23", 9.4), ("M-6,5 L-6,-30", 9.4), ("M5,6 L8,-26", 9.0), ("M15,12 L22,-10", 7.6), ("M-19,24 L-38,-1", 10)]


HAND_PALM = "M-21,8 Q-25,28 -15,44 L-10,57 Q0,60 12,57 L17,42 Q26,28 21,8 Q0,2 -21,8 Z"


def emblem_I(mono=False):
    if mono:
        return emblem_I_mono()
    st = smooth_closed(STONE_PTS)
    g = f'<clipPath id="cSt"><path d="{st}"/></clipPath><mask id="cHd" maskUnits="userSpaceOnUse" x="-60" y="-50" width="120" height="120"><path d="{HAND_PALM}" fill="#fff"/>'
    g += "".join(f'<path d="{d}" fill="none" stroke="#fff" stroke-width="{w}" stroke-linecap="round"/>' for d, w in hand_shapes()) + '</mask>'
    # Erdhaufen
    g += f'<path d="{blob(0, 93, 55, 14, 3, 91, 1, .13)}" fill="#2A2216" stroke="{INK}" stroke-width="1.5"/>'
    g += f'<path d="{st}" fill="url(#gStone)" stroke="{INK}" stroke-width="2.6" stroke-linejoin="round"/>'
    g += '<g clip-path="url(#cSt)">'
    g += '<rect x="-64" y="-95" width="128" height="190" filter="url(#fRockD)" opacity=".55"/>'
    g += '<rect x="-64" y="-95" width="128" height="190" filter="url(#fRockL)" opacity=".38"/>'
    g += '<rect x="-64" y="-95" width="128" height="190" filter="url(#fRockS)" opacity=".4"/>'
    # behauene Flaechen: linkes Lichtband, rechte Schattenflaeche
    g += '<path d="M-64,-70 L-40,-72 L-36,-30 L-42,20 L-40,95 L-64,95 Z" fill="#E8E2CE" opacity=".13"/>'
    g += '<path d="M36,-80 L64,-60 L64,95 L40,95 L44,30 L40,-30 Z" fill="#0D0C09" opacity=".2"/>'
    g += '<path d="M-36,-30 L-42,20 L-40,95" fill="none" stroke="#2A2822" stroke-width="1.4" opacity=".5"/>'
    g += '<path d="M40,-30 L44,30 L40,95" fill="none" stroke="#B8B3A1" stroke-width="1" opacity=".25"/>'
    # Meisselspuren
    g += scratches(41, 26, (-56, -80, 56, 90), 3, 7, "#2A2822", .4, 1.0, 1.6, angle=35)
    g += scratches(42, 20, (-56, -80, 56, 90), 3, 7, "#CFC9B6", .28, .8, 1.3, angle=35)
    # Risse
    g += f'<path d="M-6,-90 L-9,-74 L-3,-66 L-7,-58" fill="none" stroke="{INK}" stroke-width="1.7" opacity=".85" stroke-linejoin="round"/>'
    g += f'<path d="M50,-40 L40,-28 L44,-18 L36,-8" fill="none" stroke="{INK}" stroke-width="1.5" opacity=".75"/>'
    g += f'<path d="M-52,40 L-40,46 L-36,58" fill="none" stroke="{INK}" stroke-width="1.5" opacity=".7"/>'
    # Moos
    g += f'<g filter="url(#fSoft)"><path d="{blob(-50, 84, 13, 9, 5, 51, 1.4, .7)}" fill="#4A5632" opacity=".8"/><path d="{blob(40, 88, 16, 9, 5, 52, 1.5, .6)}" fill="#4A5632" opacity=".8"/><path d="{blob(-44, -44, 7, 7, 3, 53, 1.2, .8)}" fill="#4A5632" opacity=".6"/></g>'
    g += '<rect x="-64" y="60" width="128" height="40" filter="url(#fMoss)" opacity=".35"/>'
    # Runen (eingemeisselt, matt)
    for i, x in enumerate((-30, -6, 18)):
        y = -46 + (-4 if i == 1 else 0) + (2 if i == 2 else 0)
        g += f'<g transform="translate({x},{y}) rotate({(-4, 3, -2)[i]})"><path d="{RUNES[i]}" fill="none" stroke="#CFC9B6" stroke-width="3" opacity=".3" transform="translate(.9,1.1)" stroke-linecap="round"/><path d="{RUNES[i]}" fill="none" stroke="#25231E" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/></g>'
    # Handabdruck (Weinrot, Pigment)
    g += '<g transform="translate(0,10) scale(1.1)">'
    g += f'<g mask="url(#cHd)"><rect x="-50" y="-45" width="100" height="110" fill="#842F31"/>'
    g += '<rect x="-50" y="-45" width="100" height="110" filter="url(#fRockD)" opacity=".55"/><rect x="-50" y="-45" width="100" height="110" filter="url(#fRockL)" opacity=".28"/>'
    g += scratches(61, 12, (-40, -35, 30, 58), 3, 8, "#C9A58C", .35, .8, 1.2) + '</g>'
    g += '</g>'
    g += '</g>'
    g += f'<path d="{st}" fill="none" stroke="{INK}" stroke-width="2.4" stroke-linejoin="round"/>'
    # Gras am Fuss
    rng = random.Random(8)
    for i in range(26):
        x = rng.uniform(-54, 54)
        y = 92 + rng.uniform(-3, 4)
        g += f'<path d="M{x:.1f},{y:.1f} Q{x + rng.uniform(-3, 3):.1f},{y - 7:.1f} {x + rng.uniform(-6, 6):.1f},{y - rng.uniform(9, 15):.1f}" stroke="#4E5A2E" stroke-width="1.5" fill="none" stroke-linecap="round"/>'
    return g


def emblem_I_mono():
    st = smooth_closed(STONE_PTS)
    g = '<mask id="mI" maskUnits="userSpaceOnUse" x="-110" y="-110" width="220" height="220"><rect x="-110" y="-110" width="220" height="220" fill="#000"/>'
    g += f'<path d="{st}" fill="#fff"/><rect x="-66" y="88" width="132" height="10" rx="4" fill="#fff"/>'
    g += '<g transform="translate(0,10) scale(1.1)" stroke="#000" fill="#000" stroke-linecap="round">'
    for d, w in hand_shapes():
        g += f'<path d="{d}" stroke-width="{w + 2.6}" fill="none"/>'
    g += f'<path d="{HAND_PALM}" stroke-width="3"/></g>'
    for i, x in enumerate((-30, -6, 18)):
        g += f'<g transform="translate({x},{-46 + (-4 if i == 1 else 0) + (2 if i == 2 else 0)})"><path d="{RUNES[i]}" fill="none" stroke="#000" stroke-width="4.2" stroke-linecap="round"/></g>'
    g += '</mask>'
    return g + '<rect x="-110" y="-110" width="220" height="220" fill="#fff" mask="url(#mI)"/>'


EMBLEMS = {"G": emblem_G, "H": emblem_H, "I": emblem_I}
R_ICON = {"G": 214, "H": 226, "I": 226}      # Radius im 512er Icon
R_FG = {"G": 31.0, "H": 32.0, "I": 29.7}     # Radius im adaptiven Vordergrund (sicherer Kreis 33)
SHIFT_Y = {"G": 0, "H": 9, "I": 2}           # optische Mitte (Einheitsraum)


def svg_doc(w, h, vb, body, defs=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}">'
            f'<defs>{defs3()}{defs}</defs>{body}</svg>\n')


def place(k, emblem_svg, cx, cy, r, shadow=True, mono=False):
    f = ' filter="url(#fDrop)"' if shadow else ""
    p = ' filter="url(#fPaintM)"' if mono else ' filter="url(#fPaint)"'
    return (f'<g transform="translate({cx},{cy}) scale({r / 100:.5f}) translate(0,{-SHIFT_Y[k]})"><g{f}><g{p}>{emblem_svg}</g></g></g>')


def ink_text(d, x, y, fill, stroke_w, op=1.0):
    """Schrift wie gemalt: dunkle Kontur, matte Fuellung, leichte Verzerrung."""
    return (f'<g filter="url(#fPaintT)" transform="translate({x},{y})" opacity="{op}">'
            f'<path d="{d}" fill="{INK}" stroke="{INK}" stroke-width="{stroke_w}" stroke-linejoin="round" transform="translate(.7,1)" opacity=".8"/>'
            f'<path d="{d}" fill="{fill}" stroke="{INK}" stroke-width="{stroke_w * .55:.2f}" stroke-linejoin="round" paint-order="stroke"/></g>')


def build_svgs(k):
    d = OUT / k
    d.mkdir(parents=True, exist_ok=True)
    em = EMBLEMS[k]
    emb_c, emb_m = em(False), em(True)

    def wr(name, w, h, vb, body, defs=""):
        (d / name).write_text(svg_doc(w, h, vb, body, defs), encoding="utf-8")

    wr("icon.svg", 512, 512, "0 0 512 512", bg3(k, 512, 512) + place(k, emb_c, 256, 256, R_ICON[k]), bg_defs3(k, 512, 512))
    wr("background.svg", 108, 108, "0 0 108 108", bg3(k, 108, 108), bg_defs3(k, 108, 108))
    wr("foreground.svg", 108, 108, "0 0 108 108", place(k, emb_c, 54, 54, R_FG[k]))
    wr("notification.svg", 96, 96, "0 0 96 96", place(k, emb_m, 48, 48, 46, shadow=False, mono=True))
    # Banner 320x180
    b = bg3(k, 320, 180)
    b += (f'<g filter="url(#fPaintT)"><rect x="5.5" y="5.5" width="309" height="169" fill="none" stroke="#3A3631" stroke-width="4"/>'
          f'<rect x="5.5" y="5.5" width="309" height="169" fill="none" stroke="#17120D" stroke-width="4.8" opacity=".35" transform="translate(.4,.6)"/>'
          f'<rect x="9" y="9" width="302" height="162" fill="none" stroke="{OCHRE_D}" stroke-width=".9" opacity=".7"/></g>')
    for (x, y) in ((5.5, 5.5), (314.5, 5.5), (5.5, 174.5), (314.5, 174.5)):
        b += f'<circle cx="{x}" cy="{y}" r="2.5" fill="#8C6F40" stroke="{INK}" stroke-width=".7"/>'
    b += place(k, emb_c, 80, 90, 62 if k == "G" else 66)
    tsz = fit_text("WoW Forever", 150, 700, 0.03)
    td, tw = text_path("WoW Forever", tsz, 700, 0.03)
    tx, ty = 150, 82
    b += ink_text(td, tx, ty, "url(#rTextM)", 1.6)
    sz2 = fit_text("TOUCH", 96, 600, 0.35)
    d2, w2 = text_path("TOUCH", sz2, 600, 0.35)
    y2 = ty + 16 + cap_height(sz2, 600)
    b += ink_text(d2, tx + 1, y2, OCHRE_D, 1.1)
    ly = ty + 8
    b += (f'<g filter="url(#fPaintT)"><path d="M{tx},{ly} L{tx + 56},{ly}" stroke="{OCHRE_D}" stroke-width="1.1" fill="none"/>'
          f'<path transform="translate({tx + 64},{ly})" d="M0,-3.2 L3.2,0 L0,3.2 L-3.2,0 Z" fill="#842F31" stroke="{OCHRE}" stroke-width=".8"/>'
          f'<path d="M{tx + 72},{ly} L{tx + 148},{ly}" stroke="{OCHRE_D}" stroke-width="1.1" opacity=".7" fill="none"/></g>')
    wr("banner.svg", 1280, 720, "0 0 320 180", b, bg_defs3(k, 320, 180))
    # Wallpaper 512x512
    w = bg3(k, 512, 512)
    w += f'<g opacity=".92">{place(k, emb_c, 256, 214, 150)}</g>'
    wsz = fit_text("WoW Forever Touch", 250, 700, 0.12)
    wd, ww = text_path("WoW Forever Touch", wsz, 700, 0.12)
    w += ink_text(wd, 256 - ww / 2, 430, OCHRE_D, 1.6, .9)
    wr("wallpaper.svg", 1024, 1024, "0 0 512 512", w, bg_defs3(k, 512, 512))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--nur", choices=["svg", "png", "sheet"])
    ap.add_argument("--entwurf", default="GHI")
    a = ap.parse_args()
    ib.NAMES.update(NAMES)
    for k in a.entwurf:
        if a.nur in (None, "svg"):
            build_svgs(k)
        if a.nur in (None, "png"):
            ib.build_pngs(k)
    if a.nur in (None, "sheet"):
        ib.build_sheet("GHI", "entwuerfe3.png")


if __name__ == "__main__":
    main()
