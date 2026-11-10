#!/usr/bin/env python3
"""Vorher/Nachher-Vorschau der Launcher-Palette (Gold und Rot), links alt, rechts neu.

Liest die Farben aus LauncherStyle.kt: "alt" aus dem Git-Stand <ref> (Standard e0afc20 = vor fold8.29), "neu" aus dem Arbeitsordner.
Nachbau der Compose-Muster (Titel mit Verlauf und Schatten, Knopf SPIELEN, Karte, Schalter, Abzeichen Neu, Fließtext), kein Layout-Test.

  python tools/palette_preview.py [--old-ref e0afc20] [--out docs/reports/farben_vorher_nachher.png]
"""
import argparse
import re
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STYLE = "app/src/main/java/app/gamenative/ui/screen/wow/launcher/LauncherStyle.kt"
FONT = ROOT / "app/src/main/res/font/cinzel_variable.ttf"
BODY_FONT = ROOT / "app/src/main/res/font/bricolage_grotesque_regular.ttf"

# Schatten: alt = LauncherColors.Shadow (80 %), neu = TextShadowSoft (50 %); Versatz und Unschärfe wie im Code.
OLD_SHADOW = dict(alpha=0xCC, dy=3, blur=6, btn_dy=2, btn_blur=4, glow=0.38, rim_top="GOLD_BRIGHT", line_alpha=0.75)
NEW_SHADOW = dict(alpha=0x80, dy=2, blur=4, btn_dy=1, btn_blur=3, glow=0.22, rim_top="GOLD", line_alpha=0.45)


def palette(text):
    return {m.group(1): int(m.group(2), 16) & 0xFFFFFF for m in re.finditer(r"const val (\w+) = 0x([0-9A-Fa-f]{8})L", text)}


def rgb(v):
    return (v >> 16 & 255, v >> 8 & 255, v & 255)


def load_palettes(old_ref):
    old = subprocess.run(["git", "show", f"{old_ref}:{STYLE}"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    return palette(old), palette((ROOT / STYLE).read_text(encoding="utf-8"))


def cinzel(size, weight=800):
    f = ImageFont.truetype(str(FONT), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def vgrad(w, h, top, bottom):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img


def shadow_text(img, xy, text, font, dy, blur, alpha, spacing=0):
    layer = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(layer)
    draw_spaced(d, (xy[0], xy[1] + dy), text, font, 255, spacing)
    layer = layer.filter(ImageFilter.GaussianBlur(blur / 2)).point(lambda v: v * alpha // 255)
    img.paste((0, 0, 0), (0, 0), layer)


def draw_spaced(d, xy, text, font, fill, spacing):
    x, y = xy
    for ch in text:
        d.text((x, y), ch, font=font, fill=fill)
        x += d.textlength(ch, font=font) + spacing
    return x


def spaced_width(text, font, spacing):
    d = ImageDraw.Draw(Image.new("L", (1, 1)))
    return sum(d.textlength(c, font=font) + spacing for c in text)


def gradient_text(img, xy, text, font, top, bottom, spacing, sh):
    shadow_text(img, xy, text, font, sh["dy"], sh["blur"], sh["alpha"], spacing)
    mask = Image.new("L", img.size, 0)
    draw_spaced(ImageDraw.Draw(mask), xy, text, font, 255, spacing)
    h = font.size
    grad = vgrad(img.width, h + 8, top, bottom)
    full = Image.new("RGB", img.size, top)
    full.paste(grad, (0, xy[1]))
    img.paste(full, (0, 0), mask)


def panel(p, sh, w, h):
    P = {k: rgb(v) for k, v in p.items()}
    img = vgrad(w, h, P["STONE_DARK"], P["STONE_LIGHT"])
    img = Image.blend(img, vgrad(w, h, P["STONE_LIGHT"], P["STONE_DARK"]), 0.5)
    d = ImageDraw.Draw(img)
    pad = 36

    # Titel
    gradient_text(img, (pad, 28), "WOW FOREVER", cinzel(44), P["GOLD_BRIGHT"], P["GOLD"], 5, sh)
    d.text((pad, 92), "Titel / Wortmarke", font=ImageFont.truetype(str(BODY_FONT), 13), fill=P["MUTED"])

    # Knopf SPIELEN (Glühen, Verlauf, Rand, Glanzlinie, Text)
    bx, by, bw, bh = pad, 128, 300, 62
    glow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(glow).rounded_rectangle((bx - 20, by - 14, bx + bw + 20, by + bh + 14), 24, fill=int(255 * sh["glow"]))
    glow = glow.filter(ImageFilter.GaussianBlur(18))
    img.paste(P["CRIMSON"], (0, 0), glow)
    d = ImageDraw.Draw(img)
    btn = vgrad(bw, bh, P["CRIMSON"], P["CRIMSON_DARK"])
    m = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, bw - 1, bh - 1), 8, fill=255)
    img.paste(btn, (bx, by), m)
    rim = vgrad(bw, bh, P[sh["rim_top"]], P["BRONZE"])
    rm = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(rm).rounded_rectangle((0, 0, bw - 1, bh - 1), 8, outline=255, width=2)
    img.paste(rim, (bx, by), rm)
    line = Image.new("L", (w, h), 0)
    lx0, lx1 = bx + 10, bx + bw - 10
    for x in range(lx0, lx1):
        t = (x - lx0) / (lx1 - lx0)
        line.putpixel((x, by + 4), int(255 * sh["line_alpha"] * (1 - abs(2 * t - 1))))
    img.paste(P["GOLD_BRIGHT"] if sh is OLD_SHADOW else P["GOLD"], (0, 0), line)
    f = cinzel(24, 800)
    tw = spaced_width("SPIELEN", f, 2.4)
    tx, ty = bx + (bw - tw) / 2, by + (bh - 24) / 2 - 5
    shadow_text(img, (tx, ty), "SPIELEN", f, sh["btn_dy"], sh["btn_blur"], sh["alpha"], 2.4)
    draw_spaced(ImageDraw.Draw(img), (tx, ty), "SPIELEN", f, P["GOLD_BRIGHT"], 2.4)
    d = ImageDraw.Draw(img)
    d.text((pad, by + bh + 12), "Hauptknopf", font=ImageFont.truetype(str(BODY_FONT), 13), fill=P["MUTED"])

    # Karte mit Rahmen, Titel, Text, Abzeichen Neu
    cx, cy, cw, ch = pad, 250, w - 2 * pad, 150
    d.rounded_rectangle((cx, cy, cx + cw, cy + ch), 6, fill=P["SURFACE"], outline=P["BRONZE"], width=2)
    d.rounded_rectangle((cx + 5, cy + 5, cx + cw - 5, cy + ch - 5), 3, outline=tuple(int(P["BRONZE"][i] * 0.55 + P["SURFACE"][i] * 0.45) for i in range(3)), width=1)
    d.line((cx + 16, cy + 16, cx + cw - 16, cy + 16), fill=P["GOLD"], width=1)
    d.text((cx + 20, cy + 26), "Neuigkeiten", font=cinzel(20, 700), fill=P["GOLD"])
    bf = ImageFont.truetype(str(BODY_FONT), 16)
    d.text((cx + 20, cy + 62), "Fließtext auf der Karte: Version 281, neue Einstellungen,", font=bf, fill=P["BODY"])
    d.text((cx + 20, cy + 86), "ruhigere Farben im ganzen Launcher.", font=bf, fill=P["BODY"])
    d.text((cx + 20, cy + 116), "Gedämpfter Hinweistext", font=ImageFont.truetype(str(BODY_FONT), 14), fill=P["MUTED"])
    nf = ImageFont.truetype(str(BODY_FONT), 14)
    nw = int(d.textlength("Neu", font=nf)) + 20
    d.rounded_rectangle((cx + cw - nw - 16, cy + 24, cx + cw - 16, cy + 24 + 24), 12, fill=P["GOLD_BRIGHT"])
    d.text((cx + cw - nw - 6, cy + 27), "Neu", font=nf, fill=P["INK"])

    # Schalter
    sy = 430
    for i, on in enumerate((True, False)):
        x0 = pad + i * 200
        track = P["BRONZE"] if on else P["DIVIDER"] if "DIVIDER" in P else (0x3A, 0x2D, 0x1F)
        d.rounded_rectangle((x0, sy, x0 + 52, sy + 30), 15, fill=track, outline=P["BRONZE"], width=2)
        kx = x0 + 37 if on else x0 + 15
        d.ellipse((kx - 10, sy + 5, kx + 10, sy + 25), fill=P["TEXT_HEAD"] if on else P["MUTED"])
        d.text((x0 + 64, sy + 5), "Schalter an" if on else "Schalter aus", font=bf, fill=P["BODY"])
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--old-ref", default="e0afc20")
    ap.add_argument("--out", default="docs/reports/farben_vorher_nachher.png")
    a = ap.parse_args()
    old, new = load_palettes(a.old_ref)
    pw, ph = 780, 490
    out = Image.new("RGB", (pw * 2 + 6, ph + 44), (10, 8, 6))
    left, right = panel(old, OLD_SHADOW, pw, ph), panel(new, NEW_SHADOW, pw, ph)
    out.paste(left, (0, 44))
    out.paste(right, (pw + 6, 44))
    d = ImageDraw.Draw(out)
    f = ImageFont.truetype(str(BODY_FONT), 22)
    d.text((16, 10), "ALT (fold8.27)", font=f, fill=(230, 220, 200))
    d.text((pw + 22, 10), "NEU (fold8.29)", font=f, fill=(230, 220, 200))
    dest = ROOT / a.out
    dest.parent.mkdir(parents=True, exist_ok=True)
    out.save(dest, optimize=True)
    print(dest, out.size)


if __name__ == "__main__":
    main()
