#!/usr/bin/env python3
"""Runde 2 der Icon-Entwuerfe fuer "WoW Forever Touch": D Arkan-Siegel, E Fluegelwappen, F Amulett.

Aufruf:  python tools/icons_build_r2.py [--nur svg|png|sheet] [--entwurf DEF]
Ausgabe: docs/design/icons/<D|E|F>/*.svg, *.png und docs/design/icons/entwuerfe2.png

Baut auf tools/icons_build.py auf (Schrift als Pfade, Rendering mit Edge, Kontaktblatt). Neu: plastische Optik mit
SVG-Filtern (gehaemmertes Gold per feTurbulence + feDiffuseLighting, Schlagschatten, Glimmen), facettierter Edelstein,
Runen-Ring, Nieten, Federn, Schuppenrand. Alle Formen sind eigene Geometrie, keine Blizzard-Motive.
Nichts in app/src/main/res wird angefasst.
"""
import argparse
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import icons_build as ib  # noqa: E402
from icons_build import OUT, bg_defs, cap_height, fit_text, text_path  # noqa: E402

GOLD_BRIGHT, GOLD, CRIMSON = ib.GOLD_BRIGHT, ib.GOLD, ib.CRIMSON

GOLD_PAL = ("#6E4F22", "#C9A253", "#FFF0BE")        # dunkel, mittel, hell (fuer berechnete Facetten)
TEAL = dict(dark="#06323A", mid="#17A8B4", light="#C8FFF8", glow="#2FE6D8")
EMER = dict(dark="#06301C", mid="#18A85C", light="#CFFFE0", glow="#35F08C")
ICE = dict(dark="#0B2C57", mid="#4FA8E8", light="#EAF8FF", glow="#7FD4FF")
PAL = {"D": TEAL, "E": EMER, "F": ICE}
BGGLOW = {"D": "#1F6F78", "E": "#7A2A2E", "F": "#2E5E8C"}
NAMES = {"D": "Arkan-Siegel", "E": "Flügelwappen", "F": "Amulett"}


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lerp3(pal, t):
    """t 0..1 durch dunkel -> mittel -> hell."""
    a, b, c = (hexrgb(x) for x in pal)
    t = max(0.0, min(1.0, t))
    if t < .5:
        u, p, q = t * 2, a, b
    else:
        u, p, q = (t - .5) * 2, b, c
    return "#%02x%02x%02x" % tuple(round(p[i] + (q[i] - p[i]) * u) for i in range(3))


def P(r, a, cx=0.0, cy=0.0):
    """Polarpunkt: a in Grad, 0 = oben, im Uhrzeigersinn."""
    t = math.radians(a)
    return cx + r * math.sin(t), cy - r * math.cos(t)


def pts(points):
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in points)


def shade(angle_deg):
    """Helligkeit 0..1 einer Flaeche, deren Normale in angle_deg zeigt; Licht kommt von links oben (-45 Grad)."""
    return (math.cos(math.radians(angle_deg + 45)) + 1) / 2


def arc_at(cx, cy, r, a0, sweep):
    x0, y0 = P(r, a0, cx, cy)
    x1, y1 = P(r, a0 + sweep, cx, cy)
    return f"M{x0:.2f},{y0:.2f} A{r},{r} 0 {1 if sweep > 180 else 0} 1 {x1:.2f},{y1:.2f}"


def circ(r):
    return f"M{-r},0 A{r},{r} 0 1 0 {r},0 A{r},{r} 0 1 0 {-r},0Z"


def annulus(ro, ri):
    return circ(ro) + circ(ri)


def arc2(r, a0, sweep):
    """Kreisbogen um (0,0), Winkel wie P()."""
    x0, y0 = P(r, a0)
    x1, y1 = P(r, a0 + sweep)
    return f"M{x0:.2f},{y0:.2f} A{r},{r} 0 {1 if sweep > 180 else 0} 1 {x1:.2f},{y1:.2f}"


# ---------------------------------------------------------------- Defs
def defs2():
    s = f"""
<linearGradient id="rG" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#FFF3CC"/><stop offset=".2" stop-color="#EBD49A"/><stop offset=".47" stop-color="#B8923F"/>
  <stop offset=".63" stop-color="#E6C57C"/><stop offset="1" stop-color="#6E4F22"/></linearGradient>
<linearGradient id="rGd" x1="-100" y1="-100" x2="100" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#FFF0BE"/><stop offset=".3" stop-color="#D4B36B"/><stop offset=".55" stop-color="#9B7632"/>
  <stop offset=".8" stop-color="#E3C47E"/><stop offset="1" stop-color="#6A4B20"/></linearGradient>
<linearGradient id="rEdgeO" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#FFFBE6"/><stop offset=".5" stop-color="#C9A253"/><stop offset="1" stop-color="#4B3517"/></linearGradient>
<linearGradient id="rEdgeI" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#4B3517"/><stop offset=".5" stop-color="#C9A253"/><stop offset="1" stop-color="#FFF3CC"/></linearGradient>
<linearGradient id="rShade" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#000" stop-opacity=".85"/><stop offset=".55" stop-color="#000" stop-opacity="0"/></linearGradient>
<linearGradient id="rText" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#FFF3CC"/><stop offset=".45" stop-color="#E2C995"/><stop offset="1" stop-color="#B08A45"/></linearGradient>
<linearGradient id="rWine" x1="0" y1="-80" x2="0" y2="90" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#C9585B"/><stop offset=".5" stop-color="#93343A"/><stop offset="1" stop-color="#4A1A1E"/></linearGradient>
<radialGradient id="rRivet" cx=".35" cy=".3" r=".8">
  <stop offset="0" stop-color="#FFF8DC"/><stop offset=".45" stop-color="#C9A253"/><stop offset="1" stop-color="#4B3517"/></radialGradient>
<radialGradient id="rCore" cx="0" cy="-10" r="62" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#14414A"/><stop offset="1" stop-color="#030E12"/></radialGradient>
<radialGradient id="rCoreE" cx="0" cy="-10" r="70" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#0E3B3F"/><stop offset="1" stop-color="#030E12"/></radialGradient>
<radialGradient id="rCoreF" cx="0" cy="-10" r="58" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#163A63"/><stop offset="1" stop-color="#050C18"/></radialGradient>
<radialGradient id="rOrb" cx=".5" cy=".5" r=".6">
  <stop offset="0" stop-color="#FFFFFF"/><stop offset=".3" stop-color="#BFEBFF"/><stop offset=".65" stop-color="#46A6F2"/>
  <stop offset="1" stop-color="#1558B0"/></radialGradient>
<radialGradient id="rScale" cx=".35" cy=".3" r=".85">
  <stop offset="0" stop-color="#FFF3CC"/><stop offset=".5" stop-color="#D2AE62"/><stop offset="1" stop-color="#7A5A26"/></radialGradient>
<radialGradient id="rScaleD" cx=".35" cy=".3" r=".85">
  <stop offset="0" stop-color="#E3C47E"/><stop offset=".5" stop-color="#A47F3A"/><stop offset="1" stop-color="#4B3517"/></radialGradient>
<filter id="fHam" x="-5%" y="-5%" width="110%" height="110%">
  <feTurbulence type="fractalNoise" baseFrequency=".24" numOctaves="2" seed="7" result="n"/>
  <feDiffuseLighting in="n" lighting-color="#fff" surfaceScale="1.7" result="l"><feDistantLight azimuth="225" elevation="60"/></feDiffuseLighting>
  <feComponentTransfer in="l" result="l2"><feFuncR type="linear" slope="1.2"/><feFuncG type="linear" slope="1.2"/><feFuncB type="linear" slope="1.2"/></feComponentTransfer>
  <feComposite in="l2" in2="SourceGraphic" operator="in" result="lc"/>
  <feBlend in="SourceGraphic" in2="lc" mode="multiply"/>
</filter>
<filter id="fBlur" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="2"/></filter>
<filter id="fBlur4" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="4.5"/></filter>
<filter id="fDrop" x="-15%" y="-15%" width="130%" height="135%">
  <feGaussianBlur in="SourceAlpha" stdDeviation="2.4"/><feOffset dy="3.2" result="o"/>
  <feComponentTransfer in="o" result="s"><feFuncA type="linear" slope=".65"/></feComponentTransfer>
  <feMerge><feMergeNode in="s"/><feMergeNode in="SourceGraphic"/></feMerge>
</filter>
<clipPath id="cDiscD"><circle r="57"/></clipPath>
<clipPath id="cDiscF"><circle r="56"/></clipPath>
<clipPath id="cOrb"><circle r="34"/></clipPath>
"""
    for k, p in (("T", TEAL), ("E", EMER), ("I", ICE)):
        s += (f'<radialGradient id="gl{k}"><stop offset="0" stop-color="{p["glow"]}" stop-opacity=".95"/>'
              f'<stop offset=".35" stop-color="{p["glow"]}" stop-opacity=".45"/><stop offset="1" stop-color="{p["glow"]}" stop-opacity="0"/></radialGradient>')
    return s


def stone_noise(freq, w, h):
    return (f'<filter id="fStone" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="{freq}" numOctaves="2" seed="11"/>'
            '<feColorMatrix type="matrix" values="0 0 0 0 .95  0 0 0 0 .85  0 0 0 0 .7  1.1 0 0 0 -.42"/></filter>'
            f'<filter id="fVig" x="0" y="0" width="100%" height="100%"><feGaussianBlur stdDeviation="1"/></filter>')


def bg2(w, h, glow, freq):
    """Dunkler Stein mit farbigem Glimmen, feiner Maserung und leichter Randabdunklung."""
    return (ib.bg_rect(0, 0, w, h, glow)
            + f'<rect width="{w}" height="{h}" filter="url(#fStone)" opacity=".22"/>'
            + f'<rect width="{w}" height="{h}" fill="url(#bgVig)"/>')


def bg_defs2(glow, freq, w, h):
    return (bg_defs(glow) + stone_noise(freq, w, h)
            + '<radialGradient id="bgVig" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/>'
              '<stop offset="1" stop-color="#000" stop-opacity=".55"/></radialGradient>')


# ---------------------------------------------------------------- Bausteine
def rivet(x, y, r=2.2):
    return (f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{r + .5}" fill="#1A1208" opacity=".55"/>'
            f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{r}" fill="url(#rRivet)"/>')


def bevel_ring(ro, ri, hammer=True):
    """Goldener Ring mit Fase: Flaeche mit Verlauf (gehaemmert), helle obere Aussenkante, dunkle obere Innenkante."""
    f = ' filter="url(#fHam)"' if hammer else ""
    return (f'<path d="{annulus(ro, ri)}" fill="url(#rG)" fill-rule="evenodd"{f}/>'
            f'<circle r="{ro - .9:.2f}" fill="none" stroke="url(#rEdgeO)" stroke-width="1.5"/>'
            f'<circle r="{ri + .9:.2f}" fill="none" stroke="url(#rEdgeI)" stroke-width="1.5"/>'
            f'<circle r="{ro + .35:.2f}" fill="none" stroke="#1A1208" stroke-width=".8" opacity=".7"/>')


def gem(cx, cy, r, pal, glow_id, mono=False, glow_r=2.6, sx=1.0, sy=1.0, clip=None):
    """Facettierter Edelstein (Achteck-Schliff), Licht von links oben, mit Glimmen und Glanzpunkt."""
    o = []
    if glow_id:
        c = f' clip-path="url(#{clip})"' if clip else ""
        o.append(f'<g{c}><circle cx="{cx}" cy="{cy}" r="{r * glow_r:.1f}" fill="url(#{glow_id})"/></g>')
    o.append(f'<g transform="translate({cx},{cy}) scale({sx},{sy})">')
    out = [P(r, 45 * k) for k in range(8)]
    inn = [P(r * .58, 45 * k + 22.5) for k in range(8)]
    pol = pts(out)
    o.append(f'<polygon points="{pol}" fill="{pal["dark"]}" stroke="#031014" stroke-width="1.2" stroke-linejoin="round"/>')
    gp = (pal["dark"], pal["mid"], pal["light"])
    for k in range(8):
        a, b = out[k], out[(k + 1) % 8]
        i0, im = inn[k], inn[k - 1]
        # Kronenfacette ueber der Kante O_k-O_k+1
        ang = 45 * k + 22.5
        o.append(f'<polygon points="{pts([a, b, i0])}" fill="{lerp3(gp, shade(ang) * .85 + .1)}" stroke="{pal["light"]}" stroke-opacity=".35" stroke-width=".6"/>')
        # Facette an der Ecke O_k
        ang2 = 45 * k
        o.append(f'<polygon points="{pts([a, im, i0])}" fill="{lerp3(gp, shade(ang2) * .8 + .05)}" stroke="{pal["light"]}" stroke-opacity=".3" stroke-width=".6"/>')
    # Tafel (Mitte)
    o.append(f'<polygon points="{pts(inn)}" fill="{lerp3(gp, .72)}" stroke="{pal["light"]}" stroke-opacity=".6" stroke-width=".7"/>')
    tl = [P(r * .3, 45 * k + 22.5) for k in range(8)]
    o.append(f'<polygon points="{pts(tl)}" fill="{pal["light"]}" opacity=".35"/>')
    # Glanzpunkt und Funkeln links oben
    hx, hy = -r * .3, -r * .34
    o.append(f'<ellipse cx="{hx:.1f}" cy="{hy:.1f}" rx="{r * .2:.1f}" ry="{r * .13:.1f}" fill="#fff" opacity=".7" transform="rotate(-35 {hx:.1f} {hy:.1f})"/>')
    s = r * .42
    o.append(f'<path d="M{hx:.1f},{hy - s:.1f} L{hx + s * .18:.1f},{hy - s * .18:.1f} L{hx + s:.1f},{hy:.1f} L{hx + s * .18:.1f},{hy + s * .18:.1f} '
             f'L{hx:.1f},{hy + s:.1f} L{hx - s * .18:.1f},{hy + s * .18:.1f} L{hx - s:.1f},{hy:.1f} L{hx - s * .18:.1f},{hy - s * .18:.1f}Z" fill="#fff" opacity=".85"/>')
    o.append('</g>')
    return "".join(o)


RUNES = [
    "M0,-1.5 L0,1.5 M0,-1.5 L1,-0.6 M0,-0.3 L1,0.6",
    "M-0.9,-1.5 L-0.9,1.5 M-0.9,-1.5 L0.9,-0.2 L-0.9,1.0",
    "M0,-1.5 L0,1.5 M-0.9,-0.6 L0.9,0.6",
    "M-0.9,-1.5 L0.9,1.5 M0.9,-1.5 L-0.9,1.5",
    "M0,-1.5 L0,1.5 M0,-1.5 L-0.9,-0.5 M0,1.5 L0.9,0.5",
    "M0,-1.5 L0.9,0 L0,1.5 L-0.9,0 Z",
    "M-0.8,-1.5 L-0.8,1.5 M0.8,-1.5 L0.8,1.5 M-0.8,-0.4 L0.8,0.4",
    "M0,-1.5 L0,1.5 M-0.8,-1.0 L0,-0.2 L0.8,-1.0",
]


def rune_ring(rr, n, size, color, order=(0, 3, 5, 1, 6, 2, 7, 4)):
    d = []
    for i in range(n):
        g = RUNES[order[i % len(order)]]
        d.append(f'<path transform="rotate({i * 360 / n:.2f}) translate(0,{-rr}) scale({size})" d="{g}"/>')
    body = "".join(d)
    glow = f'<g fill="none" stroke="{color}" stroke-width=".55" stroke-linecap="round" stroke-linejoin="round" filter="url(#fBlur)" opacity=".95">{body}</g>'
    crisp = f'<g fill="none" stroke="{color}" stroke-width=".26" stroke-linecap="round" stroke-linejoin="round">{body}</g>'
    core = f'<g fill="none" stroke="#fff" stroke-width=".09" stroke-linecap="round" stroke-linejoin="round" opacity=".8">{body}</g>'
    return glow + crisp + core


def ripple(pal, specs):
    """Tippwelle: Bogenpaare mit Glimmen. specs = [(r, a0, sweep, w)]"""
    d = "".join(arc2(r, a0, sw) for r, a0, sw, w in specs)
    o = f'<path d="{d}" fill="none" stroke="{pal["glow"]}" stroke-width="5" stroke-linecap="round" opacity=".55" filter="url(#fBlur)"/>'
    for r, a0, sw, w in specs:
        o += f'<path d="{arc2(r, a0, sw)}" fill="none" stroke="{pal["light"]}" stroke-width="{w}" stroke-linecap="round"/>'
        o += f'<path d="{arc2(r, a0, sw)}" fill="none" stroke="{pal["glow"]}" stroke-width="{w + 1.2}" stroke-linecap="round" opacity=".55"/>'
    return o


def gold_facet_poly(points, ang, extra=""):
    return f'<polygon points="{pts(points)}" fill="{lerp3(GOLD_PAL, shade(ang) * .9 + .08)}" {extra}/>'


# ---------------------------------------------------------------- D: Arkan-Siegel
def emblem_D(mono=False):
    p = TEAL
    if mono:
        o = []
        for i in range(12):
            a = i * 30
            tip = 100 if i % 2 == 0 else 93
            o.append(f'<polygon points="{pts([P(80, a - 11), P(tip, a), P(80, a + 11)])}" fill="#fff"/>')
        o.append(f'<path d="{annulus(88, 72)}" fill="#fff" fill-rule="evenodd"/>')
        o.append(f'<path d="{arc2(53, 30, 130)}{arc2(53, 200, 130)}" fill="none" stroke="#fff" stroke-width="7" stroke-linecap="round"/>')
        o.append(f'<polygon points="{pts([P(34, 45 * k) for k in range(8)])}" fill="#fff"/>')
        return "".join(o)
    o = []
    # Zacken: je zwei Facetten (hell/dunkel) -> plastisch
    for i in range(12):
        a = i * 30
        tip = 100 if i % 2 == 0 else 93
        base_l, base_r, tp, mid = P(80, a - 11), P(80, a + 11), P(tip, a), P(78, a)
        o.append(gold_facet_poly([base_l, tp, mid], a - 90, 'stroke="#3A2810" stroke-width=".5" stroke-linejoin="round"'))
        o.append(gold_facet_poly([mid, tp, base_r], a + 90, 'stroke="#3A2810" stroke-width=".5" stroke-linejoin="round"'))
    o.append('<g>')
    o.append(f'<circle r="88" fill="url(#rG)" filter="url(#fHam)"/>')
    o.append(f'<circle r="87.2" fill="none" stroke="url(#rEdgeO)" stroke-width="1.6"/>')
    o.append(f'<circle r="88.4" fill="none" stroke="#1A1208" stroke-width=".8" opacity=".7"/>')
    # Runenkanal
    o.append('<circle r="77.5" fill="#071014"/>')
    o.append(f'<circle r="77.5" fill="none" stroke="url(#rEdgeI)" stroke-width="1.6"/>')
    o.append(f'<circle r="76" fill="none" stroke="url(#rShade)" stroke-width="4.5"/>')
    o.append(rune_ring(67.5, 16, 5.0, p["glow"]))
    # innerer Goldring
    o.append(bevel_ring(59, 49))
    o.append('</g>')
    # Kern
    o.append('<circle r="49" fill="url(#rCore)"/>')
    o.append(f'<circle r="48" fill="none" stroke="url(#rShade)" stroke-width="5"/>')
    o.append(f'<circle r="49" fill="none" stroke="{p["glow"]}" stroke-width="1" opacity=".45"/>')
    o.append('<g clip-path="url(#cDiscD)">')
    o.append(ripple(p, [(41, 25, 150, 2.4), (41, 205, 150, 2.4), (46.5, 80, 90, 1.7), (46.5, 260, 90, 1.7)]))
    o.append('</g>')
    # Kristall mit Fassung
    o.append(gem(0, 0, 27, p, "glT", clip="cDiscD", glow_r=2.4))
    o.append(f'<circle r="31" fill="none" stroke="url(#rEdgeO)" stroke-width="3.2"/>')
    for a in range(0, 360, 45):
        x, y = P(31, a + 22.5)
        o.append(rivet(x, y, 1.9))
    # Nieten im Aussenring
    for i in range(12):
        x, y = P(83.2, i * 30 + 15)
        o.append(rivet(x, y, 1.9))
    return "".join(o)


# ---------------------------------------------------------------- E: Fluegelwappen
SH = "M0,-70 C16,-62 38,-60 54,-62 L54,12 Q54,54 0,88 Q-54,54 -54,12 L-54,-62 C-38,-60 -16,-62 0,-70 Z"


def feather(bx, by, tx, ty, w):
    dx, dy = tx - bx, ty - by
    L = math.hypot(dx, dy)
    nx, ny = -dy / L, dx / L
    mx, my = bx + dx * .55, by + dy * .55
    return (f"M{bx},{by} Q{mx + nx * w:.1f},{my + ny * w:.1f} {tx},{ty} "
            f"Q{bx + dx * .45 - nx * w * .9:.1f},{by + dy * .45 - ny * w * .9:.1f} {bx},{by}Z")


WING = [  # Basis, Spitze, Breite  (rechter Fluegel, hinten -> vorn)
    ((34, -34), (46, -82), 17), ((36, -28), (68, -74), 19), ((38, -20), (86, -58), 20),
    ((38, -12), (95, -38), 20), ((38, -3), (96, -16), 18), ((36, 6), (88, 6), 16),
]


def wing_svg(m=1):
    """Fluegel; m=-1 spiegelt die Koordinaten (kein Transform, damit der Hammer-Filter gleich beleuchtet)."""
    o = []
    for (b, t, w) in WING:
        bx, tx = b[0] * m, t[0] * m
        d = feather(bx, b[1], tx, t[1], w * m)
        o.append(f'<path d="{d}" fill="url(#rG)" stroke="#3A2810" stroke-width=".9" stroke-linejoin="round" filter="url(#fHam)"/>')
        o.append(f'<path d="{d}" fill="url(#rEdgeO)" opacity=".18"/>')
        sx, sy = bx + (tx - bx) * .08, b[1] + (t[1] - b[1]) * .08
        ex, ey = bx + (tx - bx) * .86, b[1] + (t[1] - b[1]) * .86
        o.append(f'<path d="M{sx:.1f},{sy:.1f} L{ex:.1f},{ey:.1f}" stroke="#5A3F1B" stroke-width="1" stroke-linecap="round" opacity=".8"/>')
        o.append(f'<path d="M{sx - .6:.1f},{sy - .8:.1f} L{ex - .6:.1f},{ey - .8:.1f}" stroke="#FFF3CC" stroke-width=".6" stroke-linecap="round" opacity=".7"/>')
    return "".join(o)


def emblem_E(mono=False):
    p = EMER
    if mono:
        return (
            '<defs><mask id="mEw" maskUnits="userSpaceOnUse" x="-110" y="-110" width="220" height="220">'
            '<rect x="-110" y="-110" width="220" height="220" fill="#fff"/>'
            f'<path d="{SH}" fill="#000" stroke="#000" stroke-width="9" stroke-linejoin="round"/></mask>'
            '<mask id="mEs" maskUnits="userSpaceOnUse" x="-110" y="-110" width="220" height="220">'
            '<rect x="-110" y="-110" width="220" height="220" fill="#fff"/>'
            f'<polygon points="{pts([P(21, 45 * k) for k in range(8)])}" fill="#000" transform="translate(0,-6)"/></mask></defs>'
            f'<g mask="url(#mEw)"><g>{wing_mono()}</g><g transform="scale(-1,1)">{wing_mono()}</g></g>'
            f'<path d="{SH}" fill="#fff" mask="url(#mEs)"/>'
            f'<polygon points="{pts([P(11, 45 * k) for k in range(8)])}" fill="#fff" transform="translate(0,-6)"/>')
    o = []
    o.append('<g>' + wing_svg() + '</g>')
    o.append('<g>' + wing_svg(-1) + '</g>')
    # Schild: Goldrahmen, Weinrot-Feld, Fase
    o.append(f'<path d="{SH}" fill="url(#rG)" filter="url(#fHam)"/>')
    o.append(f'<path d="{SH}" fill="none" stroke="url(#rEdgeO)" stroke-width="1.8" stroke-linejoin="round"/>')
    o.append(f'<path d="{SH}" fill="none" stroke="#1A1208" stroke-width=".8" opacity=".75" transform="scale(1.008)"/>')
    inner = '<g transform="translate(0,1.2) scale(.87)">'
    o.append(inner + f'<path d="{SH}" fill="url(#rWine)"/>')
    o.append(f'<path d="{SH}" fill="url(#rShade)" opacity=".4"/>')
    o.append(f'<path d="M0,-70 C-16,-62 -38,-60 -54,-62 L-54,12 Q-54,54 0,88Z" fill="#fff" opacity=".08"/>')
    o.append(f'<path d="M0,-70 C16,-62 38,-60 54,-62 L54,12 Q54,54 0,88Z" fill="#000" opacity=".14"/>')
    o.append(f'<path d="{SH}" fill="none" stroke="url(#rEdgeI)" stroke-width="2.4" stroke-linejoin="round"/></g>')
    # Ornamentband (Zierlinie + Spitzen) unter dem Stein
    o.append('<g transform="translate(0,-6)">')
    o.append(ripple(p, [(35, 232, 76, 2.4), (35, 52, 76, 2.4), (43.5, 242, 56, 1.8), (43.5, 62, 56, 1.8)]))
    o.append(f'<path d="M-36,30 L0,44 L36,30" fill="none" stroke="url(#rEdgeO)" stroke-width="2.2" stroke-linejoin="round" opacity=".9"/>')
    o.append(gem(0, 0, 21, p, "glE", sx=.88, sy=1.12, glow_r=2.4))
    o.append('<ellipse rx="19.6" ry="25.2" fill="none" stroke="url(#rEdgeO)" stroke-width="3.2"/>')
    for (x, y) in ((0, -25.2), (0, 25.2), (-17.6, 0), (17.6, 0)):
        o.append(rivet(x, y, 2.0))
    o.append('</g>')
    # Nieten an den Rahmenecken
    for (x, y) in ((-46, -56), (46, -56), (-46, 18), (46, 18), (0, 76)):
        o.append(rivet(x, y, 2.1))
    # Spitze oben
    o.append(f'<polygon points="0,-90 7,-74 0,-66 -7,-74" fill="url(#rGd)" stroke="#3A2810" stroke-width=".8" stroke-linejoin="round"/>')
    o.append(f'<polygon points="0,-90 7,-74 0,-76" fill="#fff" opacity=".18"/>')
    return "".join(o)


def wing_mono():
    return "".join(f'<path d="{feather(b[0], b[1], t[0], t[1], w)}" fill="#fff"/>' for (b, t, w) in WING)


# ---------------------------------------------------------------- F: Amulett
def emblem_F(mono=False):
    p = ICE
    if mono:
        n = 24
        o = []
        for i in range(n):
            x, y = P(78, i * 360 / n)
            o.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="12.5" fill="#fff"/>')
        o.append(f'<path d="{annulus(72, 58)}" fill="#fff" fill-rule="evenodd"/>')
        o.append('<circle r="36" fill="#fff"/>')
        return "".join(o)
    o = []
    # Aufhaengering oben
    o.append('<circle cy="-92" r="7.2" fill="none" stroke="url(#rEdgeO)" stroke-width="3.6"/>')
    # Schuppenrand: zwei versetzte Reihen, ueberlappend, jede Schuppe gewoelbt (Radialverlauf) mit Glanzkante
    n = 24
    for rr, sr, off, fill in ((70, 14, 7.5, "rScaleD"), (77, 14, 0, "rScale")):
        for i in range(n):
            a = i * 360 / n + off
            x, y = P(rr, a)
            o.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{sr}" fill="url(#{fill})" stroke="#2E1F0C" stroke-width="1"/>')
            o.append(f'<path d="{arc_at(x, y, sr - 2.6, a - 62, 124)}" fill="none" stroke="#FFF6D6" stroke-width="1.3" stroke-linecap="round" opacity=".75"/>')
    # Fassungsring
    o.append(bevel_ring(66, 57))
    for i in range(32):
        x, y = P(61.5, i * 360 / 32)
        o.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="1.35" fill="url(#rRivet)"/>')
    # Kern-Scheibe mit starkem Glimmen
    o.append('<circle r="57" fill="url(#rCoreF)"/>')
    o.append(f'<g clip-path="url(#cDiscF)"><circle r="62" fill="url(#glI)"/></g>')
    o.append('<circle r="56" fill="none" stroke="url(#rShade)" stroke-width="5"/>')
    # Leuchtende Kugel, Fingerabdruck-Rillen als helle Linien
    o.append('<circle r="40" fill="none" stroke="url(#rEdgeO)" stroke-width="3" opacity=".95"/>')
    for a in range(0, 360, 45):
        x, y = P(40, a + 22.5)
        o.append(rivet(x, y, 1.9))
    o.append('<circle r="35" fill="url(#rOrb)"/>')
    o.append('<g clip-path="url(#cOrb)" fill="none" stroke-linecap="round">')
    for r, a0, sw in ((6, 190, 250), (12, 20, 300), (18, 140, 270), (24, 260, 280), (30, 40, 260)):
        o.append(f'<path d="{arc2(r, a0, sw)}" stroke="#0B3A7A" stroke-width="2.6" opacity=".35" transform="translate(.8,1)"/>')
        o.append(f'<path d="{arc2(r, a0, sw)}" stroke="#F4FCFF" stroke-width="1.7" opacity=".85"/>')
    o.append('</g>')
    o.append('<circle r="35" fill="none" stroke="#7FD4FF" stroke-width="1" opacity=".7"/>')
    o.append('<ellipse cx="-12" cy="-15" rx="10" ry="5.6" fill="#fff" opacity=".6" transform="rotate(-38 -12 -15)"/>')
    return "".join(o)


EMBLEMS = {"D": emblem_D, "E": emblem_E, "F": emblem_F}
R_ICON = {"D": 208, "E": 216, "F": 206}      # Radius im 512er Icon
R_FG = {"D": 31.0, "E": 31.0, "F": 30.5}     # Radius im adaptiven Vordergrund (sicherer Kreis 33)
SHIFT_Y = {"D": 0, "E": 3, "F": 3}           # optische Mitte (Einheitsraum)


def svg_doc(w, h, vb, body, defs=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}">'
            f'<defs>{defs2()}{defs}</defs>{body}</svg>\n')


def place(k, emblem_svg, cx, cy, r, shadow=True):
    f = ' filter="url(#fDrop)"' if shadow else ""
    return (f'<g transform="translate({cx},{cy}) scale({r / 100:.5f}) translate(0,{-SHIFT_Y[k]})"><g{f}>{emblem_svg}</g></g>')


def build_svgs(k):
    d = OUT / k
    d.mkdir(parents=True, exist_ok=True)
    em = EMBLEMS[k]
    glow = BGGLOW[k]
    emb_c, emb_m = em(False), em(True)

    def wr(name, w, h, vb, body, defs=""):
        (d / name).write_text(svg_doc(w, h, vb, body, defs), encoding="utf-8")

    wr("icon.svg", 512, 512, "0 0 512 512", bg2(512, 512, glow, .5) + place(k, emb_c, 256, 256, R_ICON[k]), bg_defs2(glow, .5, 512, 512))
    wr("background.svg", 108, 108, "0 0 108 108", bg2(108, 108, glow, 2.0), bg_defs2(glow, 2.0, 108, 108))
    wr("foreground.svg", 108, 108, "0 0 108 108", place(k, emb_c, 54, 54, R_FG[k]))
    wr("notification.svg", 96, 96, "0 0 96 96", place(k, emb_m, 48, 48, 46, shadow=False))
    # Banner 320x180
    b = bg2(320, 180, glow, 2.0)
    b += f'<rect x="5" y="5" width="310" height="170" fill="none" stroke="url(#rEdgeO)" stroke-width="1.6" opacity=".9"/>'
    b += f'<rect x="8.5" y="8.5" width="303" height="163" fill="none" stroke="{GOLD}" stroke-width=".5" opacity=".6"/>'
    for (x, y) in ((5, 5), (315, 5), (5, 175), (315, 175)):
        b += rivet(x, y, 2.4)
    b += place(k, emb_c, 80, 90, 62 if k != "E" else 66)
    tsz = fit_text("WoW Forever", 150, 700, 0.03)
    td, tw = text_path("WoW Forever", tsz, 700, 0.03)
    tx, ty = 150, 82
    b += f'<g transform="translate({tx + .9},{ty + 1.2})"><path d="{td}" fill="#000" opacity=".6"/></g>'
    b += f'<g transform="translate({tx},{ty})"><path d="{td}" fill="url(#rText)"/></g>'
    sz2 = fit_text("TOUCH", 96, 600, 0.35)
    d2, w2 = text_path("TOUCH", sz2, 600, 0.35)
    y2 = ty + 16 + cap_height(sz2, 600)
    b += f'<g transform="translate({tx + 1.6},{y2 + 1:.1f})"><path d="{d2}" fill="#000" opacity=".5"/></g>'
    b += f'<g transform="translate({tx + 1},{y2:.1f})"><path d="{d2}" fill="{GOLD}"/></g>'
    ly = ty + 8
    pal = PAL[k]
    b += f'<path d="M{tx},{ly} L{tx + 56},{ly}" stroke="{GOLD}" stroke-width=".9"/>'
    b += (f'<path transform="translate({tx + 64},{ly})" d="M0,-3.4 L3.4,0 L0,3.4 L-3.4,0 Z" fill="{pal["mid"]}" stroke="{GOLD_BRIGHT}" stroke-width=".7"/>'
          f'<path transform="translate({tx + 64},{ly})" d="M0,-3.4 L3.4,0 L0,0 Z" fill="{pal["light"]}" opacity=".8"/>')
    b += f'<path d="M{tx + 72},{ly} L{tx + 148},{ly}" stroke="{GOLD}" stroke-width=".9" opacity=".6"/>'
    wr("banner.svg", 1280, 720, "0 0 320 180", b, bg_defs2(glow, 2.0, 320, 180))
    # Wallpaper 512x512
    w = bg2(512, 512, glow, 1.0)
    w += f'<circle cx="256" cy="212" r="190" fill="url(#gl{"TEI"["DEF".index(k)]})" opacity=".35"/>'
    w += f'<g opacity=".95">{place(k, emb_c, 256, 212, 150)}</g>'
    wsz = fit_text("WoW Forever Touch", 250, 700, 0.12)
    wd, ww = text_path("WoW Forever Touch", wsz, 700, 0.12)
    w += f'<g transform="translate({256 - ww / 2 + .8:.1f},{431})"><path d="{wd}" fill="#000" opacity=".6"/></g>'
    w += f'<g transform="translate({256 - ww / 2:.1f},{430})"><path d="{wd}" fill="url(#rText)"/></g>'
    wr("wallpaper.svg", 1024, 1024, "0 0 512 512", w, bg_defs2(glow, 1.0, 512, 512))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--nur", choices=["svg", "png", "sheet"])
    ap.add_argument("--entwurf", default="DEF")
    a = ap.parse_args()
    ib.NAMES.update(NAMES)
    for k in a.entwurf:
        if a.nur in (None, "svg"):
            build_svgs(k)
        if a.nur in (None, "png"):
            ib.build_pngs(k)
    if a.nur in (None, "sheet"):
        ib.build_sheet("DEF", "entwuerfe2.png")


if __name__ == "__main__":
    main()
