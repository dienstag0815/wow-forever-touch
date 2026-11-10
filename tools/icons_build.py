#!/usr/bin/env python3
"""Erzeugt die Icon-Entwuerfe A/B/C (SVG-Quellen und PNGs) fuer "WoW Forever Touch".

Aufruf:  python tools/icons_build.py [--nur svg|png|sheet]
Ausgabe: docs/design/icons/<A|B|C>/*.svg, *.png und docs/design/icons/entwuerfe.png

Alle Formen sind eigene Geometrie. Text (Cinzel, OFL) wird mit fontTools in Pfade umgewandelt, damit die SVGs ohne Schrift
auskommen. PNGs entstehen mit Edge/Chrome ohne Kopf (Screenshot einer Seite mit dem SVG), auf Zielgroesse verkleinert mit Pillow.
Nichts in app/src/main/res wird angefasst.
"""
import argparse
import math
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "design" / "icons"
FONT = ROOT / "app" / "src" / "main" / "res" / "font" / "cinzel_variable.ttf"

GOLD_BRIGHT = "#E2C995"
GOLD = "#B59A72"
BRONZE = "#85673C"
CRIMSON = "#842F31"
CRIMSON_DARK = "#5F2226"
STONE_DARK = "#14100C"
STONE = "#1E1A16"
TEXT = "#F0E6D2"

BROWSERS = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
]

_fonts = {}


def font(weight):
    if weight not in _fonts:
        _fonts[weight] = instantiateVariableFont(TTFont(str(FONT)), {"wght": weight}, inplace=False)
    return _fonts[weight]


def text_path(text, size, weight=700, tracking=0.0):
    """Pfad (d, Breite) mit Grundlinie bei y=0, Start bei x=0; tracking in Einheiten der Schriftgroesse (em)."""
    f = font(weight)
    gs, cmap, hmtx = f.getGlyphSet(), f.getBestCmap(), f["hmtx"]
    s = size / f["head"].unitsPerEm
    x = 0.0
    parts = []
    for ch in text:
        name = cmap[ord(ch)]
        pen = SVGPathPen(gs)
        gs[name].draw(TransformPen(pen, (s, 0, 0, -s, x, 0)))
        parts.append(pen.getCommands())
        x += hmtx[name][0] * s + tracking * size
    return " ".join(p for p in parts if p), x - tracking * size


def fit_text(text, max_w, weight=700, tracking=0.0):
    """Schriftgroesse, bei der der Text max_w breit ist."""
    _, w = text_path(text, 100, weight, tracking)
    return 100 * max_w / w


def cap_height(size, weight=700):
    f = font(weight)
    return f["OS/2"].sCapHeight * size / f["head"].unitsPerEm


def arc(r, a0, sweep):
    """Kreisbogen um (0,0) als Pfad; Winkel in Grad, 0 = oben, im Uhrzeigersinn."""
    def pt(a):
        t = math.radians(a - 90)
        return r * math.cos(t), r * math.sin(t)
    x0, y0 = pt(a0)
    x1, y1 = pt(a0 + sweep)
    large = 1 if sweep > 180 else 0
    return f"M{x0:.2f},{y0:.2f} A{r},{r} 0 {large} 1 {x1:.2f},{y1:.2f}"


# ---------------------------------------------------------------- gemeinsame Farbverlaeufe
DEFS = f"""
<linearGradient id="gGold" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#F0DDAE"/><stop offset=".5" stop-color="{GOLD_BRIGHT}"/><stop offset="1" stop-color="#A68757"/>
</linearGradient>
<linearGradient id="gGoldD" x1="-100" y1="-100" x2="100" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="{GOLD_BRIGHT}"/><stop offset="1" stop-color="{BRONZE}"/>
</linearGradient>
<linearGradient id="gCrim" x1="0" y1="-100" x2="0" y2="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#A33C3F"/><stop offset="1" stop-color="{CRIMSON_DARK}"/>
</linearGradient>
<radialGradient id="gDisc" cx="0" cy="-20" r="100" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#302418"/><stop offset="1" stop-color="{STONE_DARK}"/>
</radialGradient>
<radialGradient id="gDot" cx="-4" cy="-6" r="20" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#B84447"/><stop offset="1" stop-color="{CRIMSON_DARK}"/>
</radialGradient>
"""


def bg_defs(glow):
    return f"""
<linearGradient id="bgBase" x1="0" y1="0" x2="1" y2="1">
  <stop offset="0" stop-color="#2A221A"/><stop offset="1" stop-color="{STONE_DARK}"/>
</linearGradient>
<radialGradient id="bgGlow" cx=".5" cy=".46" r=".62">
  <stop offset="0" stop-color="{glow}" stop-opacity=".55"/><stop offset="1" stop-color="{glow}" stop-opacity="0"/>
</radialGradient>"""


def bg_rect(x, y, w, h, glow):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#bgBase)"/>'
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#bgGlow)"/>')


# ---------------------------------------------------------------- Embleme (Einheitsraum, Radius 100 um 0,0)
def emblem_A(mono=False):
    """Siegel: Doppelring mit Zacken, Monogramm WFT."""
    g, g2, crim = ("#fff",) * 3 if mono else ("url(#gGold)", "url(#gGoldD)", "url(#gCrim)")
    out = []
    if mono:
        out.append('<circle r="92" fill="none" stroke="#fff" stroke-width="13"/>')
        d, w = text_path("W", 100, 800)
        s = 150 / 100
        out.append(f'<path transform="translate({-w * s / 2:.2f},{cap_height(100 * s, 800) / 2:.2f}) scale({s})" d="{d}" fill="#fff"/>')
        return "".join(out)
    out.append(f'<circle r="96" fill="url(#gDisc)" stroke="{g}" stroke-width="7"/>')
    out.append(f'<circle r="82" fill="none" stroke="{g2}" stroke-width="2.2"/>')
    # Zacken (Strahlen) zwischen den Ringen, die vier Hauptrichtungen als Weinrot-Rauten
    for i in range(24):
        a = i * 15
        if i % 6 == 0:
            out.append(f'<path transform="rotate({a})" d="M0,-92 L5,-87 L0,-82 L-5,-87 Z" fill="{CRIMSON}" stroke="{GOLD_BRIGHT}" stroke-width="1.2"/>')
        else:
            w = 2.6 if i % 2 == 0 else 1.6
            out.append(f'<path transform="rotate({a})" d="M0,-90 L0,-84.5" stroke="{GOLD}" stroke-width="{w}" stroke-linecap="round"/>')
    out.append(f'<circle r="72" fill="none" stroke="{CRIMSON}" stroke-width="3"/>')
    size = fit_text("WFT", 104, 800, 0.04)
    d, w = text_path("WFT", size, 800, 0.04)
    ch = cap_height(size, 800)
    out.append(f'<path transform="translate({-w / 2:.2f},{ch / 2 - 6:.2f})" d="{d}" fill="{g}"/>')
    # Zierlinie mit Raute unter dem Monogramm
    out.append(f'<path d="M-34,{ch / 2 + 12:.1f} L-7,{ch / 2 + 12:.1f} M7,{ch / 2 + 12:.1f} L34,{ch / 2 + 12:.1f}" stroke="{GOLD}" stroke-width="2" stroke-linecap="round"/>')
    out.append(f'<path d="M0,{ch / 2 + 7:.1f} L5,{ch / 2 + 12:.1f} L0,{ch / 2 + 17:.1f} L-5,{ch / 2 + 12:.1f} Z" fill="{CRIMSON}" stroke="{GOLD_BRIGHT}" stroke-width="1.2"/>')
    return "".join(out)


def emblem_B(mono=False):
    """Tippwelle: Punkt mit drei offenen Wellenringen (zugleich Fingerabdruck-Linien) in einem Ring."""
    out = []
    if mono:
        out.append('<circle r="93" fill="none" stroke="#fff" stroke-width="9"/>')
        out.append('<circle r="19" fill="#fff"/>')
        for r, a0, sw, w in ((42, 20, 300, 12), (65, 150, 290, 11)):
            out.append(f'<path d="{arc(r, a0, sw)}" fill="none" stroke="#fff" stroke-width="{w}" stroke-linecap="round"/>')
        return "".join(out)
    out.append(f'<circle r="95" fill="url(#gDisc)" stroke="url(#gGold)" stroke-width="5"/>')
    out.append(f'<circle r="86" fill="none" stroke="{GOLD}" stroke-width="1.4" opacity=".7"/>')
    rings = ((38, 25, 300, 10, "url(#gGold)"), (59, 150, 290, 9, "url(#gGold)"), (79, 275, 285, 7.5, "url(#gGoldD)"))
    for r, a0, sw, w, col in rings:
        out.append(f'<path d="{arc(r, a0, sw)}" fill="none" stroke="{col}" stroke-width="{w}" stroke-linecap="round"/>')
    out.append(f'<circle r="18" fill="url(#gDot)" stroke="url(#gGold)" stroke-width="3.5"/>')
    out.append('<circle cx="-5" cy="-6" r="4.5" fill="#fff" opacity=".22"/>')
    return "".join(out)


SHIELD = "M0,-96 L66,-74 L66,6 Q66,58 0,98 Q-66,58 -66,6 L-66,-74 Z"
SHIELD_IN = "M0,-84 L55,-66 L55,4 Q55,50 0,85 Q-55,50 -55,4 L-55,-66 Z"


def emblem_C(mono=False):
    """Kristallschild mit Fingertipp (Punkt und zwei Wellenbogen)."""
    out = []
    if mono:
        out.append('<defs><mask id="mC" maskUnits="userSpaceOnUse" x="-100" y="-100" width="200" height="200">'
                   '<rect x="-100" y="-100" width="200" height="200" fill="#fff"/>'
                   '<g transform="translate(0,4)"><circle r="15" fill="#000"/>'
                   f'<path d="{arc(36, -65, 130)}{arc(36, 115, 130)}" fill="none" stroke="#000" stroke-width="10" stroke-linecap="round"/></g>'
                   '</mask></defs>')
        out.append(f'<path d="{SHIELD}" fill="#fff" mask="url(#mC)"/>')
        return "".join(out)
    out.append(f'<path d="{SHIELD}" fill="url(#gGold)" stroke="{BRONZE}" stroke-width="2" stroke-linejoin="round"/>')
    out.append(f'<path d="{SHIELD_IN}" fill="url(#gCrim)"/>')
    # Kristallfacetten: linke Haelfte heller, rechte dunkler, Querfacette oben
    out.append(f'<path d="M0,-84 L-55,-66 L-55,4 Q-55,50 0,85 Z" fill="#fff" opacity=".09"/>')
    out.append(f'<path d="M0,-84 L55,-66 L55,4 Q55,50 0,85 Z" fill="#000" opacity=".16"/>')
    out.append(f'<path d="M-55,-66 L0,-40 L55,-66 L0,-84 Z" fill="#fff" opacity=".12"/>')
    out.append(f'<path d="M0,-84 L0,85 M-55,-66 L0,-40 L55,-66 M-55,4 L0,-40 L55,4" fill="none" stroke="{GOLD_BRIGHT}" stroke-width="1.3" opacity=".55" stroke-linejoin="round"/>')
    out.append(f'<path d="{SHIELD_IN}" fill="none" stroke="{GOLD_BRIGHT}" stroke-width="2.4" stroke-linejoin="round"/>')
    # Fingertipp
    out.append('<g transform="translate(0,6)">')
    out.append(f'<path d="{arc(47, -52, 104)}{arc(47, 128, 104)}" fill="none" stroke="{GOLD}" stroke-width="5.5" stroke-linecap="round" opacity=".85"/>')
    out.append(f'<path d="{arc(31, -68, 136)}{arc(31, 112, 136)}" fill="none" stroke="{GOLD_BRIGHT}" stroke-width="6.5" stroke-linecap="round"/>')
    out.append(f'<circle r="14.5" fill="url(#gGold)" stroke="{BRONZE}" stroke-width="1.5"/>')
    out.append('</g>')
    return "".join(out)


EMBLEMS = {"A": emblem_A, "B": emblem_B, "C": emblem_C}
GLOW = {"A": "#4A3722", "B": CRIMSON, "C": "#4A3722"}
NAMES = {"A": "Siegel", "B": "Tippwelle", "C": "Kristallschild"}


def svg_doc(w, h, vb, body, defs=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}">'
            f'<defs>{DEFS}{defs}</defs>{body}</svg>\n')


def place(emblem_svg, cx, cy, r):
    return f'<g transform="translate({cx},{cy}) scale({r / 100:.5f})">{emblem_svg}</g>'


# ---------------------------------------------------------------- Dateien je Entwurf
def build_svgs(k):
    d = OUT / k
    d.mkdir(parents=True, exist_ok=True)
    em = EMBLEMS[k]
    glow = GLOW[k]
    emb_c, emb_m = em(False), em(True)

    # Play-Store/Legacy-Icon, randlos 512x512
    r_icon = 214 if k != "C" else 222
    (d / "icon.svg").write_text(svg_doc(512, 512, "0 0 512 512", bg_rect(0, 0, 512, 512, glow) + place(emb_c, 256, 256, r_icon), bg_defs(glow)), encoding="utf-8")
    # adaptiv: Hintergrund und Vordergrund (108 dp, sichtbar 72 dp, sicher 66 dp -> Radius <= 33)
    (d / "background.svg").write_text(svg_doc(108, 108, "0 0 108 108", bg_rect(0, 0, 108, 108, glow), bg_defs(glow)), encoding="utf-8")
    r_fg = 32 if k != "C" else 31.5
    (d / "foreground.svg").write_text(svg_doc(108, 108, "0 0 108 108", place(emb_c, 54, 54, r_fg)), encoding="utf-8")
    # Benachrichtigung, einfarbig weiss; Android nutzt nur den Alphakanal
    (d / "notification.svg").write_text(svg_doc(96, 96, "0 0 96 96", place(emb_m, 48, 48, 46)), encoding="utf-8")
    # Banner 320x180
    banner = bg_rect(0, 0, 320, 180, glow)
    banner += f'<rect x="5" y="5" width="310" height="170" fill="none" stroke="{GOLD}" stroke-width=".8" opacity=".55"/>'
    banner += f'<rect x="8" y="8" width="304" height="164" fill="none" stroke="{GOLD}" stroke-width=".4" opacity=".35"/>'
    banner += place(emb_c, 78, 90, 58)
    tsz = fit_text("WoW Forever", 148, 700, 0.03)
    td, tw = text_path("WoW Forever", tsz, 700, 0.03)
    ch = cap_height(tsz, 700)
    tx, ty = 148, 90 - 8
    banner += (f'<g transform="translate({tx},{ty:.1f})"><path d="{td}" fill="{GOLD_BRIGHT}"/></g>')
    sz2 = fit_text("TOUCH", 96, 600, 0.35)
    d2, w2 = text_path("TOUCH", sz2, 600, 0.35)
    ch2 = cap_height(sz2, 600)
    y2 = ty + 16 + ch2
    banner += f'<g transform="translate({tx + 1},{y2:.1f})"><path d="{d2}" fill="{GOLD}"/></g>'
    ly = ty + 8
    banner += f'<path d="M{tx},{ly:.1f} L{tx + 56},{ly:.1f}" stroke="{GOLD}" stroke-width=".9"/>'
    banner += f'<path transform="translate({tx + 64},{ly:.1f})" d="M0,-2.6 L2.6,0 L0,2.6 L-2.6,0 Z" fill="{CRIMSON}" stroke="{GOLD_BRIGHT}" stroke-width=".6"/>'
    banner += f'<path d="M{tx + 72},{ly:.1f} L{tx + 148},{ly:.1f}" stroke="{GOLD}" stroke-width=".9" opacity=".6"/>'
    (d / "banner.svg").write_text(svg_doc(1280, 720, "0 0 320 180", banner, bg_defs(glow)), encoding="utf-8")
    # Wallpaper (Wine-Desktop), quadratisch wie das vorhandene; Emblem gedaempft, Schriftzug klein darunter
    wp = bg_rect(0, 0, 512, 512, glow)
    wp += f'<g opacity=".9">{place(emb_c, 256, 218, 150)}</g>'
    wsz = fit_text("WoW Forever Touch", 250, 700, 0.12)
    wd, ww = text_path("WoW Forever Touch", wsz, 700, 0.12)
    wp += f'<g transform="translate({256 - ww / 2:.1f},{430})"><path d="{wd}" fill="{GOLD}" opacity=".9"/></g>'
    (d / "wallpaper.svg").write_text(svg_doc(1024, 1024, "0 0 512 512", wp, bg_defs(glow)), encoding="utf-8")


# ---------------------------------------------------------------- PNG-Rendering
def browser():
    for b in BROWSERS:
        if Path(b).exists():
            return b
    sys.exit("Weder Edge noch Chrome gefunden")


def render_svg(svg, px_w, px_h, dest, transparent=True):
    """SVG mit Edge/Chrome ohne Kopf als PNG rendern (px_w x px_h)."""
    tmp = Path(tempfile.mkdtemp(prefix="icons_"))
    try:
        html = tmp / "p.html"
        html.write_text(
            f'<!doctype html><meta charset="utf-8"><style>html,body{{margin:0;background:transparent;overflow:hidden}}'
            f'img{{display:block;width:{px_w}px;height:{px_h}px}}</style><img src="{Path(svg).resolve().as_uri()}">',
            encoding="utf-8")
        shot = tmp / "s.png"
        subprocess.run([browser(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        "--default-background-color=00000000", f"--window-size={px_w},{px_h + 200}",
                        f"--user-data-dir={tmp / 'prof'}", f"--screenshot={shot}", html.as_uri()],
                       check=True, capture_output=True, timeout=90)
        im = Image.open(shot).convert("RGBA").crop((0, 0, px_w, px_h))
        dest = Path(dest)
        im.save(dest)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def round_mask(size):
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def rounded_mask(size, radius_frac):
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=int(size * 4 * radius_frac), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def build_pngs(k):
    d = OUT / k
    render_svg(d / "icon.svg", 1024, 1024, d / "_icon1024.png")
    big = Image.open(d / "_icon1024.png")
    big.resize((512, 512), Image.LANCZOS).convert("RGB").save(d / "icon_512.png")          # Play Store, ohne Alpha
    sq = big.resize((192, 192), Image.LANCZOS)
    sq.putalpha(rounded_mask(192, 0.16))
    sq.save(d / "icon_square_192.png")                                                       # eckiges Legacy-Icon (abgerundet)
    rd = big.resize((192, 192), Image.LANCZOS)
    rd.putalpha(round_mask(192))
    rd.save(d / "icon_round_192.png")                                                        # rundes Legacy-Icon
    render_svg(d / "foreground.svg", 432, 432, d / "foreground_432.png")
    render_svg(d / "background.svg", 432, 432, d / "background_432.png")
    render_svg(d / "notification.svg", 384, 384, d / "_notif384.png")
    Image.open(d / "_notif384.png").resize((96, 96), Image.LANCZOS).save(d / "notification_96.png")
    render_svg(d / "banner.svg", 1280, 720, d / "banner_1280x720.png")
    render_svg(d / "wallpaper.svg", 1024, 1024, d / "_wp1024.png")
    Image.open(d / "_wp1024.png").resize((512, 512), Image.LANCZOS).save(d / "wallpaper_512.png")
    for t in ("_icon1024.png", "_notif384.png", "_wp1024.png"):
        (d / t).unlink()


# ---------------------------------------------------------------- Kontaktblatt
def adaptive_preview(k, size, shape="round"):
    """Hintergrund + Vordergrund, wie Android maskiert (sichtbar sind die mittleren 72 von 108 dp)."""
    d = OUT / k
    bg = Image.open(d / "background_432.png").convert("RGBA")
    fg = Image.open(d / "foreground_432.png").convert("RGBA")
    comp = Image.alpha_composite(bg, fg)
    m = 432 * 18 // 108
    comp = comp.crop((m, m, 432 - m, 432 - m)).resize((size, size), Image.LANCZOS)
    comp.putalpha(round_mask(size) if shape == "round" else rounded_mask(size, 0.22))
    return comp


def build_sheet(keys="ABC", fname="entwuerfe.png"):
    fnt = ImageFont.truetype("arial.ttf", 15)
    fb = ImageFont.truetype("arialbd.ttf", 20)
    light, dark, gray = (236, 232, 224), (24, 22, 20), (110, 112, 116)
    colw = [512, 8, 120, 120, 8, 64, 64, 8, 80, 8, 320, 8, 160]
    W = 1800
    rowh = 590
    sheet = Image.new("RGB", (W, rowh * len(keys) + 10), (58, 56, 54))
    dr = ImageDraw.Draw(sheet)
    for i, k in enumerate(keys):
        d = OUT / k
        y0 = i * rowh + 10
        x = 20
        dr.text((x, y0), f"{k} · {NAMES[k]}", fill=(240, 230, 210), font=fb)
        y0 += 34
        icon = Image.open(d / "icon_512.png").convert("RGB")
        sheet.paste(icon, (x, y0))
        dr.text((x, y0 + 518), "Play-Store 512 (randlos)", fill=(200, 200, 200), font=fnt)
        x += 512 + 20
        # rund maskiert, 96 und 48, hell und dunkel
        for j, (bgc, lab) in enumerate(((light, "hell"), (dark, "dunkel"))):
            bx = x + j * 132
            dr.rectangle((bx, y0, bx + 124, y0 + 330), fill=bgc)
            sheet.paste(adaptive_preview(k, 96), (bx + 14, y0 + 12), adaptive_preview(k, 96))
            sheet.paste(adaptive_preview(k, 48), (bx + 38, y0 + 126), adaptive_preview(k, 48))
            sq = adaptive_preview(k, 96, "square")
            sheet.paste(sq, (bx + 14, y0 + 190), sq)
            dr.text((bx + 8, y0 + 306), lab, fill=(120, 120, 120) if bgc == light else (170, 170, 170), font=fnt)
        dr.text((x, y0 + 336), "rund 96 / 48, eckig 96", fill=(200, 200, 200), font=fnt)
        x += 2 * 132 + 14
        # Benachrichtigung, weiss auf grau, 24 px und 2 groessere Ansichten
        dr.rectangle((x, y0, x + 150, y0 + 330), fill=gray)
        n = Image.open(d / "notification_96.png").convert("RGBA")
        for (sz, ox, oy) in ((24, 20, 20), (48, 20, 70), (96, 20, 140)):
            nn = n.resize((sz, sz), Image.LANCZOS)
            sheet.paste(nn, (x + ox, y0 + oy), nn)
        dr.text((x, y0 + 336), "Benachr. 24/48/96", fill=(200, 200, 200), font=fnt)
        x += 150 + 20
        # Banner und Wallpaper
        ban = Image.open(d / "banner_1280x720.png").convert("RGB").resize((420, 236), Image.LANCZOS)
        sheet.paste(ban, (x, y0))
        dr.text((x, y0 + 242), "Banner 320x180 (hier 420 px)", fill=(200, 200, 200), font=fnt)
        ban_small = Image.open(d / "banner_1280x720.png").convert("RGB").resize((320, 180), Image.LANCZOS)
        sheet.paste(ban_small, (x, y0 + 272))
        dr.text((x, y0 + 456), "Banner in 320 px", fill=(200, 200, 200), font=fnt)
        x += 440
        wp = Image.open(d / "wallpaper_512.png").convert("RGB").resize((280, 280), Image.LANCZOS)
        sheet.paste(wp, (x, y0))
        dr.text((x, y0 + 286), "Wallpaper 512 (hier 280 px)", fill=(200, 200, 200), font=fnt)
    sheet.save(OUT / fname)
    print("Kontaktblatt:", sheet.size)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--nur", choices=["svg", "png", "sheet"])
    ap.add_argument("--entwurf", default="ABC")
    a = ap.parse_args()
    for k in a.entwurf:
        if a.nur in (None, "svg"):
            build_svgs(k)
        if a.nur in (None, "png"):
            build_pngs(k)
    if a.nur in (None, "sheet"):
        build_sheet()


if __name__ == "__main__":
    main()
