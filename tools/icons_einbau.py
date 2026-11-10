#!/usr/bin/env python3
"""Baut den gewaehlten Icon-Entwurf (Standard: H Panzerhandschuh) in app/src/main ein.

Aufruf:  python tools/icons_einbau.py [--entwurf H] [--blatt]
Quelle:  docs/design/icons/<X>/  (PNG/SVG aus tools/icons_build_r3.py; Edge headless fuer die Monochrom-Ebene)
Ziel:    app/src/main/res/{mipmap-*,drawable*}, app/src/main/ic_*-playstore.png, docs/design/icons/eingebaut.png
Dichten: mdpi 1, hdpi 1.5, xhdpi 2, xxhdpi 3, xxxhdpi 4 (Launcher 48 dp, Adaptiv 108 dp, Benachrichtigung 24 dp, Banner 320x180 dp).
"""
import argparse
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import icons_build as ib  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
MAIN = ROOT / "app/src/main"
DENS = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def rs(im, px_w, px_h=None):
    return im.resize((px_w, px_h or px_w), Image.LANCZOS)


def mono_layer(src, k):
    """Einfarbige Adaptiv-Ebene (Themen-Icon Android 13): notification.svg, in die Sicherheitszone (66 von 108 dp) gesetzt."""
    with tempfile.TemporaryDirectory() as t:
        glyph_png = Path(t) / "g.png"
        ib.render_svg(src / "notification.svg", 528, 528, glyph_png)
        glyph = rs(Image.open(glyph_png).convert("RGBA"), 264)
    layer = Image.new("RGBA", (432, 432), (255, 255, 255, 0))
    layer.paste(glyph, (84, 84), glyph)
    return layer


def build(k):
    src = ib.OUT / k
    icon512 = Image.open(src / "icon_512.png").convert("RGB")
    big = icon512  # 512 reicht: groesste Ablage 192 px
    fg = Image.open(src / "foreground_432.png").convert("RGBA")
    bg = Image.open(src / "background_432.png").convert("RGBA")
    mono = mono_layer(src, k)
    banner = Image.open(src / "banner_1280x720.png").convert("RGBA")
    notif = Image.open(src / "notification_96.png").convert("RGBA")
    wall = Image.open(src / "wallpaper_512.png").convert("RGBA")

    for d, f in DENS.items():
        mip = RES / f"mipmap-{d}"
        px = round(48 * f)
        sq = rs(big, px).convert("RGBA")
        sq.putalpha(ib.rounded_mask(px, 0.16))
        rd = rs(big, px).convert("RGBA")
        rd.putalpha(ib.round_mask(px))
        for name, im in (("ic_launcher", sq), ("ic_launcher_round", rd), ("ic_launcher_alt", sq), ("ic_launcher_alt_round", rd)):
            im.save(mip / f"{name}.webp", lossless=True)
        ad = round(108 * f)
        rs(fg, ad).save(mip / "ic_launcher_foreground.png")
        rs(bg, ad).save(mip / "ic_launcher_background.png")
        rs(mono, ad).save(mip / "ic_launcher_monochrome.png")
        rs(fg, ad).save(mip / "ic_launcher_alt_foreground.webp", lossless=True)
        rs(bg, ad).convert("RGB").save(mip / "ic_launcher_alt_background.webp", lossless=True)
        dr = RES / f"drawable-{d}"
        npx = round(24 * f)
        for name in ("ic_notification", "ic_notification_alt"):
            rs(notif, npx).save(dr / f"{name}.png")
        rs(banner, round(320 * f), round(180 * f)).save(dr / "ic_banner.png")
    rs(banner, 320, 180).save(RES / "drawable/ic_banner.png")
    wall.save(RES / "drawable-hdpi/wallpaper.png")
    # Play-Store-Dateien (nicht im Build verwendet): drei farbige = App-Icon, Mono = Benachrichtigungsmotiv
    for name in ("ic_launcher-playstore.png", "ic_launcher_alt-playstore.png", "ic_launcher_gold-playstore.png"):
        icon512.convert("RGBA").save(MAIN / name)
    rs(mono, 512).save(MAIN / "ic_mono-playstore.png")


def sheet(k):
    """Kontaktblatt der eingebauten Dateien, je Dichte eine Zeile, auf Rasterhintergrund."""
    fnt = ImageFont.truetype("arial.ttf", 14)
    cols = ["ic_launcher", "ic_launcher_round", "adaptiv (bg+fg)", "adaptiv rund", "monochrom", "ic_notification", "ic_banner"]
    cw, ch, lab = 200, 170, 90
    W, H = lab + len(cols) * cw, 30 + (len(DENS) + 2) * ch
    out = Image.new("RGB", (W, H), (96, 96, 96))
    dr = ImageDraw.Draw(out)
    for i, c in enumerate(cols):
        dr.text((lab + i * cw + 6, 6), c, fill="white", font=fnt)

    def put(im, c, r, h=None):
        h = h or ch - 24
        w = round(im.width * min(h / im.height, (cw - 12) / im.width))
        hh = round(im.height * w / im.width)
        im = im.convert("RGBA").resize((w, hh), Image.NEAREST)
        out.paste(im, (lab + c * cw + 6, 30 + r * ch + 4), im)

    for r, d in enumerate(DENS):
        mip, drw = RES / f"mipmap-{d}", RES / f"drawable-{d}"
        dr.text((6, 30 + r * ch + 4), d + "\n" + str(Image.open(mip / "ic_launcher.webp").width) + " px", fill="white", font=fnt)
        put(Image.open(mip / "ic_launcher.webp"), 0, r)
        put(Image.open(mip / "ic_launcher_round.webp"), 1, r)
        comp = Image.alpha_composite(Image.open(mip / "ic_launcher_background.png").convert("RGBA"), Image.open(mip / "ic_launcher_foreground.png").convert("RGBA"))
        put(comp, 2, r)
        c = comp.crop((comp.width // 6, comp.width // 6, comp.width * 5 // 6, comp.width * 5 // 6))
        c.putalpha(ib.round_mask(c.width))
        put(c, 3, r)
        mono = Image.open(mip / "ic_launcher_monochrome.png").convert("RGBA")
        tile = Image.new("RGBA", mono.size, (200, 190, 160, 255))
        m = mono.copy()
        px = m.load()
        for y in range(m.height):
            for x in range(m.width):
                a = px[x, y][3]
                px[x, y] = (40, 40, 60, a)
        tile.alpha_composite(m)
        put(tile, 4, r)
        n = Image.open(drw / "ic_notification.png").convert("RGBA")
        nt = Image.new("RGBA", n.size, (70, 70, 70, 255))
        nt.alpha_composite(n)
        put(nt, 5, r, h=min(ch - 24, n.height * 3))
        put(Image.open(drw / "ic_banner.png"), 6, r, h=90)
    r = len(DENS)
    dr.text((6, 30 + r * ch + 4), "Wallpaper\nPlay Store", fill="white", font=fnt)
    put(Image.open(RES / "drawable-hdpi/wallpaper.png"), 0, r)
    put(Image.open(MAIN / "ic_launcher_gold-playstore.png"), 1, r)
    put(Image.open(MAIN / "ic_mono-playstore.png"), 2, r)
    put(Image.open(RES / "drawable/ic_banner.png"), 6, r, h=90)
    dr.text((6, 30 + (r + 1) * ch + 4), "drawable/\nic_banner", fill="white", font=fnt)
    out.save(ib.OUT / "eingebaut.png")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--entwurf", default="H")
    ap.add_argument("--blatt", action="store_true", help="nur Kontaktblatt neu")
    a = ap.parse_args()
    if not a.blatt:
        build(a.entwurf)
    sheet(a.entwurf)
