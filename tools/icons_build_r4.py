#!/usr/bin/env python3
"""Runde 4 der Icon-Entwuerfe (10.11.2026): heller, naeher am Original-Look von WoW Forever
(rundes Metall-Emblem mit Zacken, leuchtende Mitte, Serifen-Schriftzug), mit eigenem Touch (Panzerhandschuh + Tippwellen).

Alles eigene Geometrie, keine Blizzard-Motive oder -Schriftzuege. Schrift: Cinzel (OFL, liegt in der App).
Aufruf:  python tools/icons_build_r4.py [--entwurf JKL] [--nur svg|png|sheet]
"""
import argparse
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import icons_build as ib  # noqa: E402
import icons_build_r3 as r3  # noqa: E402

OUT = ib.OUT
NAMES = {"J": "Emblem mit Schrift", "K": "Emblem ohne Schrift", "L": "Emblem hell"}
INK = "#10161E"

# Hintergrund je Entwurf: (oben links, unten rechts, Lichtfleck)
BG = {"J": ("#22405C", "#0B1522", "#3FA7C9"),
      "K": ("#22405C", "#0B1522", "#3FA7C9"),
      "L": ("#2F6E8C", "#12283C", "#8FE3F2")}


def defs4():
    return """
<linearGradient id="gGold" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#FBE7A6"/><stop offset=".35" stop-color="#E2B866"/><stop offset=".7" stop-color="#B9873E"/><stop offset="1" stop-color="#7E5726"/></linearGradient>
<linearGradient id="gGoldS" x1="-1" y1="0" x2="1" y2="0">
  <stop offset="0" stop-color="#FFF0BE"/><stop offset=".5" stop-color="#E0B463"/><stop offset="1" stop-color="#94672E"/></linearGradient>
<radialGradient id="gCore" cx="-12" cy="-22" r="80" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#D9FBFF"/><stop offset=".3" stop-color="#6FD9EE"/><stop offset=".7" stop-color="#1C8DB5"/><stop offset="1" stop-color="#0C4766"/></radialGradient>
<radialGradient id="gCoreRim" cx="0" cy="0" r="66" gradientUnits="userSpaceOnUse">
  <stop offset=".78" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#03141F" stop-opacity=".55"/></radialGradient>
<linearGradient id="gSteel" x1="-40" y1="-40" x2="44" y2="90" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#F2F4F5"/><stop offset=".45" stop-color="#B7BEC6"/><stop offset="1" stop-color="#6B747E"/></linearGradient>
<linearGradient id="gSilverT" x1="0" y1="-1" x2="0" y2="0" gradientUnits="objectBoundingBox">
  <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#AEB7C0"/></linearGradient>
<linearGradient id="gSilverY" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#FFFFFF"/><stop offset=".55" stop-color="#D5DBE1"/><stop offset="1" stop-color="#8F99A4"/></linearGradient>
<filter id="fGlow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3.2"/></filter>
<filter id="fPaintL" x="-12%" y="-12%" width="124%" height="124%">
  <feTurbulence type="fractalNoise" baseFrequency=".03" numOctaves="2" seed="4" result="w1"/>
  <feDisplacementMap in="SourceGraphic" in2="w1" scale="2.2" xChannelSelector="R" yChannelSelector="G" result="d1"/>
  <feTurbulence type="fractalNoise" baseFrequency=".6" numOctaves="3" seed="13" result="g"/>
  <feColorMatrix in="g" type="matrix" values=".22 0 0 0 .89  .22 0 0 0 .89  .22 0 0 0 .89  0 0 0 0 1" result="gm"/>
  <feComposite in="d1" in2="gm" operator="arithmetic" k1="1" k2="0" k3="0" k4="0"/></filter>
"""


def bg_defs4(k, w, h):
    lt, dk, spot = BG[k]
    return (f'<linearGradient id="bgBase" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{lt}"/><stop offset="1" stop-color="{dk}"/></linearGradient>'
            f'<radialGradient id="bgSpot" cx=".5" cy=".45" r=".6"><stop offset="0" stop-color="{spot}" stop-opacity=".45"/><stop offset="1" stop-color="{spot}" stop-opacity="0"/></radialGradient>'
            '<radialGradient id="bgVig" cx=".5" cy=".5" r=".78"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".45"/></radialGradient>'
            + r3.noise_filter("fBgM", f"{5.0 / w:.4f}", 21, 3, (0, 0, 0), -3.0, 1.55))


def bg4(w, h):
    return (f'<rect width="{w}" height="{h}" fill="url(#bgBase)"/>'
            f'<rect width="{w}" height="{h}" fill="url(#bgSpot)"/>'
            f'<rect width="{w}" height="{h}" filter="url(#fBgM)" opacity=".35"/>'
            f'<rect width="{w}" height="{h}" fill="url(#bgVig)"/>')


def spike(a, r0, r1, half):
    """Zacke als Raute von r0 bis r1 in Richtung a (Grad, 0 = oben)."""
    t = math.radians(a)
    ux, uy = math.sin(t), -math.cos(t)
    px, py = -uy, ux
    rm = r0 + (r1 - r0) * .32
    pts = [(ux * r0, uy * r0), (ux * rm + px * half, uy * rm + py * half), (ux * r1, uy * r1), (ux * rm - px * half, uy * rm - py * half)]
    return "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + " Z"


def gauntlet():
    """Silberner Panzerhandschuh (Formen aus Runde 3), Zeigefinger oben bei (-13,-40)."""
    iron = f'fill="url(#gSteel)" stroke="{INK}" stroke-width="2.6" stroke-linejoin="round"'
    g = f'<path d="{r3.CUFF}" {iron}/>'
    g += f'<path d="M-36,64 L36,64 L37.5,71 L-37.5,71 Z" fill="url(#gGold)" stroke="{INK}" stroke-width="1.8"/>'
    g += f'<path d="M-39,85 L39,85 L40,92 L-40,92 Z" fill="url(#gGold)" stroke="{INK}" stroke-width="1.8"/>'
    g += f'<path d="{r3.HAND_BODY}" {iron}/>'
    g += f'<path d="M-33,50 L41,50" stroke="{INK}" stroke-width="1.5" opacity=".55"/>'
    for x, top, w in ((1, 6, 13.5), (15, 3, 13.5), (29, 8, 14.5)):
        d = f"M{x},26 L{x},{top + 5} Q{x},{top} {x + 5},{top} L{x + w - 5},{top} Q{x + w},{top} {x + w},{top + 5} L{x + w},26 Z"
        g += f'<path d="{d}" {iron}/><path d="M{x + 1},{top + 11} L{x + w - 1},{top + 11}" stroke="{INK}" stroke-width="1.2" opacity=".5"/>'
    g += f'<path d="{r3.THUMB}" {iron}/>'
    g += f'<path d="{r3.INDEX}" {iron}/>'
    for y in (-18, 0, 15):
        g += f'<path d="M-23,{y} L-3,{y}" stroke="{INK}" stroke-width="1.8"/><path d="M-22,{y + 1.8} L-4,{y + 1.8}" stroke="#FFFFFF" stroke-width=".8" opacity=".6"/>'
    g += '<path d="M-19,-34 Q-13,-37 -7,-34" fill="none" stroke="#FFFFFF" stroke-width="1.6" opacity=".8"/>'
    return g


def ripples(cx, cy, col="#E9FDFF"):
    g = ""
    for r, w in ((12, 3.4), (21, 2.8), (30, 2.0)):
        x0, y0 = r3.polar(r, -62, cx, cy)
        x1, y1 = r3.polar(r, 62, cx, cy)
        arc = f"M{x0:.1f},{y0:.1f} A{r},{r} 0 0 1 {x1:.1f},{y1:.1f}"
        g += f'<path d="{arc}" fill="none" stroke="#7FF0FF" stroke-width="{w + 4}" stroke-linecap="round" opacity=".55" filter="url(#fGlow)"/>'
        g += f'<path d="{arc}" fill="none" stroke="{col}" stroke-width="{w}" stroke-linecap="round" opacity="{1 - (r - 12) / 40:.2f}"/>'
    return g


def emblem(mono=False):
    """Rundes Emblem, Einheitsraum Radius 100: Zacken (Gold), Ring (Gold), leuchtende Scheibe, Handschuh mit Tippwellen."""
    if mono:
        g = ""
        for a in range(0, 360, 90):
            g += f'<path d="{spike(a, 60, 99, 15)}" fill="#fff"/>'
        g += '<circle r="76" fill="#fff"/><circle r="64" fill="#000"/>'
        g += '<g transform="translate(0,6) scale(.72) translate(13,-6)">'
        for d in (r3.CUFF, r3.HAND_BODY, r3.THUMB, r3.INDEX):
            g += f'<path d="{d}" fill="#fff"/>'
        for x, top, w in ((1, 6, 13.5), (15, 3, 13.5), (29, 8, 14.5)):
            g += f'<rect x="{x}" y="{top}" width="{w}" height="{26 - top}" rx="5" fill="#fff"/>'
        g += '</g>'
        return f'<mask id="mM"><rect x="-110" y="-110" width="220" height="220" fill="#000"/>{g}</mask><rect x="-110" y="-110" width="220" height="220" fill="#fff" mask="url(#mM)"/>'
    g = ""
    # Zacken: vier grosse, vier kleine
    for a in range(45, 360, 90):
        g += f'<path d="{spike(a, 58, 88, 11)}" fill="url(#gGold)" stroke="{INK}" stroke-width="2.4" stroke-linejoin="round"/>'
    for a in range(0, 360, 90):
        g += f'<path d="{spike(a, 56, 100, 16)}" fill="url(#gGold)" stroke="{INK}" stroke-width="2.6" stroke-linejoin="round"/>'
        t = math.radians(a)
        g += (f'<path d="M{math.sin(t) * 62:.1f},{-math.cos(t) * 62:.1f} L{math.sin(t) * 96:.1f},{-math.cos(t) * 96:.1f}" '
              f'stroke="#FFF4CF" stroke-width="1.6" opacity=".7"/>')
    # Ring
    g += f'<circle r="75" fill="url(#gGold)" stroke="{INK}" stroke-width="2.8"/>'
    g += '<circle r="70.5" fill="none" stroke="#FFF1C4" stroke-width="1.4" opacity=".85"/>'
    for i in range(12):
        x, y = r3.polar(70.5, i * 30 + 15)
        g += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2.1" fill="#8A5F28" stroke="#FFF1C4" stroke-width=".7"/>'
    # Scheibe
    g += f'<circle r="65" fill="url(#gCore)" stroke="{INK}" stroke-width="2.6"/>'
    g += '<circle r="65" fill="url(#gCoreRim)"/>'
    g += '<ellipse cx="-20" cy="-34" rx="30" ry="16" fill="#FFFFFF" opacity=".16" transform="rotate(-25 -20 -34)"/>'
    # Handschuh mit Tippwellen
    g += '<clipPath id="cCore"><circle r="64"/></clipPath><g clip-path="url(#cCore)">'
    g += '<g transform="translate(0,8) scale(.72) translate(13,-6)">' + ripples(-13, -42) + gauntlet() + '</g></g>'
    return g


R_ICON = {"J": 172, "K": 214, "L": 172}
CY_ICON = {"J": 214, "K": 256, "L": 214}
R_FG = 31.5

# ---------------------------------------------------------------- Runde 4b: Schrift im Emblem, TOUCH als Graffiti-Tag
NAMES.update({"M": "Emblem + Tag hell", "N": "Emblem + Tag rot"})
BG.update({"M": BG["J"], "N": BG["J"]})
TAG_COL = {"M": ("#E9FDFF", "#5FE3F5"), "N": ("#FF6A5C", "#FF3B4E")}

# Tag-Buchstaben als freie Striche (eigene Handschrift, keine Schriftdatei), Hoehe ~40
TAG_STROKES = [
    "M-2,3 Q12,-3 30,-1",                                   # T Balken
    "M14,0 Q13,20 11,39",                                   # T Stamm
    "M44,9 Q33,8 32,24 Q32,39 43,38 Q54,37 53,22 Q52,9 42,11",   # O
    "M60,8 Q58,30 66,37 Q74,40 78,26 L79,7 Q79,24 81,38",  # U
    "M106,12 Q97,6 91,15 Q85,27 92,36 Q99,41 107,34",      # C
    "M115,0 Q114,20 113,39",                                # H links
    "M134,-3 Q133,18 132,38",                               # H rechts
    "M108,22 Q122,17 141,15",                               # H Querstrich (ueberstehend)
]
TAG_SWOOSH = "M-6,52 Q60,44 128,50 Q146,52 156,42"
TAG_DRIPS = [(11, 39, 9), (113, 39, 13), (132, 38, 6)]


def tag(k, sw=7.0):
    """TOUCH wie gesprueht: Overspray-Halo, dunkle Kontur, Farbe, Glanzlinie, Laufnasen. Mitte bei (75,22)."""
    fill, glow = TAG_COL[k]
    allp = TAG_STROKES + [TAG_SWOOSH]
    def strokes(col, w, op=1.0, extra=""):
        return "".join(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{w:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="{op}"{extra}/>' for d in allp)
    drips = ""
    for x, y, ln in TAG_DRIPS:
        drips += (f'<path d="M{x},{y} L{x - .4},{y + ln}" stroke="{INK}" stroke-width="{sw * .55 + 2.4:.1f}" stroke-linecap="round"/>'
                  f'<path d="M{x},{y} L{x - .4},{y + ln}" stroke="{fill}" stroke-width="{sw * .55:.1f}" stroke-linecap="round"/>')
    g = strokes(glow, sw * 3.2, .35, ' filter="url(#fSpray)"')
    g += strokes(INK, sw + 4.2)
    g += drips
    g += strokes(fill, sw)
    g += "".join(f'<path d="{d}" fill="none" stroke="#FFFFFF" stroke-width="{sw * .22:.1f}" stroke-linecap="round" opacity=".55" transform="translate(-1.2,-1.4)"/>' for d in allp)
    return f'<g transform="translate(-75,-22)">{g}</g>'


def composite(k):
    """Emblem mit FOREVER quer ueber dem unteren Drittel und schraeg darunter gesprayt TOUCH. Einheitsraum ~ +-125."""
    em = emblem(False)
    g = f'<g transform="translate(0,-14)">{em}</g>'
    t, _ = title("FOREVER", 0, 52, 236, 800, .05, "url(#gSilverY)", 2.6)
    g += t
    g += f'<g transform="translate(18,86) rotate(-11) skewX(-14) scale(.86)">{tag(k)}</g>'
    return g


def build_svgs_tag(k):
    d = OUT / k
    d.mkdir(parents=True, exist_ok=True)
    comp = composite(k)

    def wr(name, w, h, vb, body, defs=""):
        (d / name).write_text(r3.svg_doc(w, h, vb, body, defs4() + SPRAY + defs), encoding="utf-8")

    wr("icon.svg", 512, 512, "0 0 512 512", bg4(512, 512) + place(comp, 256, 262, 196), bg_defs4(k, 512, 512))
    wr("background.svg", 108, 108, "0 0 108 108", bg4(108, 108), bg_defs4(k, 108, 108))
    wr("foreground.svg", 108, 108, "0 0 108 108", place(comp, 54, 55, 27.5))
    wr("notification.svg", 96, 96, "0 0 96 96", place(emblem(True), 48, 48, 46, mono=True))
    b = bg4(320, 180) + place(comp, 160, 92, 70)
    wr("banner.svg", 1280, 720, "0 0 320 180", b, bg_defs4(k, 320, 180))
    w = bg4(512, 512) + place(comp, 256, 262, 196)
    wr("wallpaper.svg", 1024, 1024, "0 0 512 512", w, bg_defs4(k, 512, 512))


NAMES.update({"O": "Original + Touch"})
BG.update({"O": BG["J"]})
LOGO = OUT / "O" / "quelle_logo.png"   # Original-Logo WoW Forever (10.11.2026: bewusst auch oeffentlich, bei Aerger tauschen)


def orig_body():
    """Variante Q: Original-Logo, Handschuh von oben tippt auf die Scheibe, TOUCH als Tag darunter. 512er Raum, ohne Hintergrund."""
    import base64
    from PIL import Image
    w0, h0 = Image.open(LOGO).size
    uri = "data:image/png;base64," + base64.b64encode(LOGO.read_bytes()).decode()
    W = 470
    s = W / w0
    lx, ly = 21, 34
    fx, fy = lx + w0 * .499 * s, ly + h0 * .418 * s
    b = f'<image href="{uri}" x="{lx}" y="{ly}" width="{W}" height="{h0 * s:.1f}"/>'
    b += (f'<g filter="url(#fDrop)"><g transform="translate({fx:.1f},{fy - 6:.1f}) scale(1.05) rotate(172) translate(13,40)">'
          + ripples(-13, -42) + gauntlet() + '</g></g>')
    b += f'<g transform="translate(300,392) rotate(-11) skewX(-14) scale(1.25)">{tag("M")}</g>'
    return b


def build_svgs_orig(k="O"):
    d = OUT / k
    body = orig_body()

    def wr(name, w, h, vb, inner, defs=""):
        (d / name).write_text(r3.svg_doc(w, h, vb, inner, defs4() + SPRAY + defs), encoding="utf-8")

    wr("icon.svg", 512, 512, "0 0 512 512", bg4(512, 512) + body, bg_defs4(k, 512, 512))
    wr("background.svg", 108, 108, "0 0 108 108", bg4(108, 108), bg_defs4(k, 108, 108))
    fg = 76.0   # 512er Komposition auf 76 dp: Logo ~70 dp breit, passt in die sichtbaren 72 dp
    wr("foreground.svg", 108, 108, "0 0 108 108", f'<g transform="translate({54 - fg / 2},{54 - fg / 2}) scale({fg / 512:.5f})">{body}</g>')
    wr("notification.svg", 96, 96, "0 0 96 96", place(emblem(True), 48, 48, 46, mono=True))
    bh = 176.0
    wr("banner.svg", 1280, 720, "0 0 320 180", bg4(320, 180) + f'<g transform="translate({160 - bh / 2},{90 - bh / 2 + 2}) scale({bh / 512:.5f})">{body}</g>', bg_defs4(k, 320, 180))
    wr("wallpaper.svg", 1024, 1024, "0 0 512 512", bg4(512, 512) + body, bg_defs4(k, 512, 512))


SPRAY = ('<filter id="fSpray" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="3.5" result="b"/>'
         '<feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="1" seed="3" result="n"/>'
         '<feComposite in="b" in2="n" operator="arithmetic" k1="1.6" k2=".35" k3="0" k4="0"/></filter>')


def place(emb, cx, cy, r, mono=False):
    f = ' filter="url(#fPaintM)"' if mono else ' filter="url(#fPaintL)"'
    sh = '' if mono else ' filter="url(#fDrop)"'
    return f'<g transform="translate({cx},{cy}) scale({r / 100:.5f})"><g{sh}><g{f}>{emb}</g></g></g>'


def title(text, cx, base, max_w, weight=800, track=.06, fill="url(#gSilverY)", sw=3.0):
    sz = ib.fit_text(text, max_w, weight, track)
    d, w = ib.text_path(text, sz, weight, track)
    x = cx - w / 2
    return (f'<g transform="translate({x:.1f},{base:.1f})">'
            f'<path d="{d}" fill="{INK}" stroke="{INK}" stroke-width="{sw * 1.9:.1f}" stroke-linejoin="round" transform="translate(0,{sw * .7:.1f})" opacity=".55"/>'
            f'<path d="{d}" fill="none" stroke="#5B3D17" stroke-width="{sw * 1.5:.1f}" stroke-linejoin="round"/>'
            f'<path d="{d}" fill="none" stroke="url(#gGoldS)" stroke-width="{sw:.1f}" stroke-linejoin="round"/>'
            f'<path d="{d}" fill="{fill}"/></g>'), sz


def icon_body(k, emb):
    b = bg4(512, 512) + place(emb, 256, CY_ICON[k], R_ICON[k])
    if k in "JL":
        t, sz = title("FOREVER", 256, 405, 372)
        b += t
        t2, _ = title("TOUCH", 256, 450, 150, 700, .32, "url(#gGold)", 2.2)
        b += t2
    return b


def build_svgs(k):
    d = OUT / k
    d.mkdir(parents=True, exist_ok=True)
    emb_c, emb_m = emblem(False), emblem(True)

    def wr(name, w, h, vb, body, defs=""):
        (d / name).write_text(r3.svg_doc(w, h, vb, body, defs4() + defs), encoding="utf-8")

    wr("icon.svg", 512, 512, "0 0 512 512", icon_body(k, emb_c), bg_defs4(k, 512, 512))
    wr("background.svg", 108, 108, "0 0 108 108", bg4(108, 108), bg_defs4(k, 108, 108))
    wr("foreground.svg", 108, 108, "0 0 108 108", place(emb_c, 54, 54, R_FG))
    wr("notification.svg", 96, 96, "0 0 96 96", place(emb_m, 48, 48, 46, mono=True))
    # Banner 320x180
    b = bg4(320, 180) + place(emb_c, 82, 90, 66)
    t, _ = title("FOREVER", 225, 92, 140, 800, .06, "url(#gSilverY)", 1.4)
    t2, _ = title("TOUCH", 225, 120, 70, 700, .32, "url(#gGold)", 1.0)
    b += t + t2
    wr("banner.svg", 1280, 720, "0 0 320 180", b, bg_defs4(k, 320, 180))
    # Wallpaper 512x512
    w = bg4(512, 512) + place(emb_c, 256, 210, 150)
    t, _ = title("FOREVER", 256, 432, 300, 800, .06, "url(#gSilverY)", 2.4)
    t2, _ = title("TOUCH", 256, 472, 120, 700, .32, "url(#gGold)", 1.8)
    w += t + t2
    wr("wallpaper.svg", 1024, 1024, "0 0 512 512", w, bg_defs4(k, 512, 512))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--nur", choices=["svg", "png", "sheet"])
    ap.add_argument("--entwurf", default="JKL")
    a = ap.parse_args()
    ib.NAMES.update(NAMES)
    for k in a.entwurf:
        if a.nur in (None, "svg"):
            (build_svgs_orig if k == "O" else build_svgs_tag if k in "MN" else build_svgs)(k)
        if a.nur in (None, "png"):
            ib.build_pngs(k)
    if a.nur in (None, "sheet"):
        ib.build_sheet(a.entwurf, "entwuerfe4c.png" if a.entwurf == "O" else "entwuerfe4b.png" if set(a.entwurf) <= set("MN") else "entwuerfe4.png")


if __name__ == "__main__":
    main()
