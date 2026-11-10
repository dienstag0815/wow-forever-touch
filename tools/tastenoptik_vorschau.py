#!/usr/bin/env python3
"""Preview board "tastenoptik": three looks for the touch controls (Glas, Azeroth, Minimal+), drawn with PIL.

Not part of the build and not used by the app. It only produces a PNG so that the look can be chosen before it is built
into ControlElement.java. Everything is drawn here (no Blizzard art, the ability icons are simple stand-ins).

Needs: pip install pillow numpy. Run from anywhere:
  python tools/tastenoptik_vorschau.py [--bg SCENE.jpg] [--out DIR] [--only glas|azeroth|minimal]
Output: DIR/tastenoptik_vorschau.png (all three styles side by side) and one PNG per style.
"""
import argparse
import math
import pathlib

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

S = 2                       # supersampling, the board is reduced with LANCZOS at the end
PW, PH = 1200, 1700         # panel size in final pixels
FONTS = pathlib.Path("C:/Windows/Fonts")
DEFAULT_BG = None                   # a game screenshot of your own (--bg); none is shipped in the repo
DEFAULT_OUT = "vorschau"            # relative to the working directory


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), int(size * S))


# --------------------------------------------------------------------------------------------- patch and masks
class Patch:
    """Transparent working area around one element (final px box, padded for shadows)."""

    def __init__(self, x0, y0, x1, y1, pad=40):
        self.ox = int((x0 - pad) * S)
        self.oy = int((y0 - pad) * S)
        self.w = int((x1 - x0 + 2 * pad) * S)
        self.h = int((y1 - y0 + 2 * pad) * S)
        self.img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))

    def lx(self, x):
        return x * S - self.ox

    def ly(self, y):
        return y * S - self.oy

    def blank(self):
        return Image.new("L", (self.w, self.h), 0)

    def put(self, layer):
        self.img = Image.alpha_composite(self.img, layer)

    def fill(self, mask, color):
        a = color[3] if len(color) > 3 else 255
        m = mask if a == 255 else mask.point(lambda v: v * a // 255)
        layer = Image.new("RGBA", (self.w, self.h), tuple(color[:3]) + (0,))
        layer.putalpha(m)
        self.put(layer)

    def fill_rgba(self, rgba, mask):
        """rgba: full-size RGBA image, its alpha is multiplied with the mask."""
        layer = rgba.copy()
        layer.putalpha(ImageChops.multiply(layer.getchannel("A"), mask))
        self.put(layer)

    def vgrad(self, y0, y1, c0, c1):
        """RGBA image, vertical gradient between two final-px heights (clamped)."""
        ys = (np.arange(self.h) + self.oy) / S
        t = np.clip((ys - y0) / max(1e-6, (y1 - y0)), 0, 1)[:, None, None]
        c0 = np.array(c0 if len(c0) == 4 else tuple(c0) + (255,), dtype=float)
        c1 = np.array(c1 if len(c1) == 4 else tuple(c1) + (255,), dtype=float)
        arr = (c0 * (1 - t) + c1 * t)
        arr = np.broadcast_to(arr, (self.h, self.w, 4)).astype(np.uint8)
        return Image.fromarray(arr, "RGBA")

    def shadow(self, mask, dy, blur, alpha, color=(0, 0, 0)):
        m = ImageChops.offset(mask, 0, int(dy * S)).filter(ImageFilter.GaussianBlur(blur * S))
        self.fill(m, tuple(color) + (alpha,))

    def glow(self, mask, blur, color, alpha):
        m = mask.filter(ImageFilter.GaussianBlur(blur * S))
        m = m.point(lambda v: min(255, int(v * 1.6)))
        self.fill(m, tuple(color) + (alpha,))

    def vmask(self, mask, y0, y1, a0, a1):
        """Mask times a vertical alpha ramp (a0 at y0 to a1 at y1, 0..255)."""
        ys = (np.arange(self.h) + self.oy) / S
        t = np.clip((ys - y0) / max(1e-6, (y1 - y0)), 0, 1)
        col = (a0 * (1 - t) + a1 * t) / 255.0
        ramp = np.broadcast_to(col[:, None], (self.h, self.w))
        arr = (np.asarray(mask, dtype=float) * ramp).astype(np.uint8)
        return Image.fromarray(arr, "L")

    def dgrad(self, box, c0, c1):
        """RGBA image, diagonal gradient (top left c0 to bottom right c1) over a final-px box."""
        ys = (np.arange(self.h) + self.oy)[:, None] / S
        xs = (np.arange(self.w) + self.ox)[None, :] / S
        t = np.clip(((xs - box[0]) + (ys - box[1])) / max(1e-6, (box[2] - box[0]) + (box[3] - box[1])), 0, 1)[:, :, None]
        c0 = np.array(tuple(c0) + (255,) if len(c0) == 3 else c0, dtype=float)
        c1 = np.array(tuple(c1) + (255,) if len(c1) == 3 else c1, dtype=float)
        return Image.fromarray((c0 * (1 - t) + c1 * t).astype(np.uint8), "RGBA")

    def text(self, xy, s, fnt, color, shadow=(0, 2, 3, 170), anchor="mm", outline=0):
        m = self.blank()
        ImageDraw.Draw(m).text((self.lx(xy[0]), self.ly(xy[1])), s, font=fnt, fill=255, anchor=anchor)
        if outline:
            om = self.blank()
            ImageDraw.Draw(om).text((self.lx(xy[0]), self.ly(xy[1])), s, font=fnt, fill=255, anchor=anchor,
                                    stroke_width=max(1, int(outline * S)), stroke_fill=255)
            self.fill(om, (0, 0, 0, 235))
        if shadow:
            dx, dy, bl, al = shadow
            sm = ImageChops.offset(m, int(dx * S), int(dy * S))
            if bl:
                sm = sm.filter(ImageFilter.GaussianBlur(bl * S))
            self.fill(sm, (0, 0, 0, al))
        self.fill(m, color)

    def paste_on(self, canvas):
        canvas.alpha_composite(self.img, (self.ox, self.oy))


class Circ:
    def __init__(self, cx, cy, r):
        self.cx, self.cy, self.r = cx, cy, r
        self.box = (cx - r, cy - r, cx + r, cy + r)

    def mask(self, P, inset=0, k=1.0):
        m = P.blank()
        r = self.r * k - inset
        if r > 0:
            ImageDraw.Draw(m).ellipse([P.lx(self.cx - r), P.ly(self.cy - r), P.lx(self.cx + r), P.ly(self.cy + r)], fill=255)
        return m

    def sub(self, dr):
        return Circ(self.cx, self.cy, self.r + dr)


class RRect:
    def __init__(self, x0, y0, x1, y1, rad):
        self.x0, self.y0, self.x1, self.y1, self.rad = x0, y0, x1, y1, rad
        self.box = (x0, y0, x1, y1)
        self.cx, self.cy = (x0 + x1) / 2, (y0 + y1) / 2

    @classmethod
    def pill(cls, cx, cy, w, h):
        return cls(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, min(w, h) / 2)

    def mask(self, P, inset=0, k=1.0):
        m = P.blank()
        hw, hh = (self.x1 - self.x0) / 2 * k - inset, (self.y1 - self.y0) / 2 * k - inset
        if hw <= 0 or hh <= 0:
            return m
        rad = max(0.0, min(self.rad * k - inset, hw, hh))
        ImageDraw.Draw(m).rounded_rectangle(
            [P.lx(self.cx - hw), P.ly(self.cy - hh), P.lx(self.cx + hw), P.ly(self.cy + hh)], radius=rad * S, fill=255)
        return m

    def inner(self, d, rad=None):
        return RRect(self.x0 + d, self.y0 + d, self.x1 - d, self.y1 - d, max(0, self.rad - d) if rad is None else rad)


class Sect:
    """Ring segment (as in ControlElement.buildSectorPath), corners slightly rounded."""

    def __init__(self, cx, cy, ri, ro, a0, a1, rnd=7):
        self.cx, self.cy, self.ri, self.ro, self.a0, self.a1, self.rnd = cx, cy, ri, ro, a0, a1, rnd
        self.box = (cx - ro, cy - ro, cx + ro, cy + ro)
        am = math.radians((a0 + a1) / 2)
        self.mx = cx + math.cos(am) * (ri + ro) / 2
        self.my = cy + math.sin(am) * (ri + ro) / 2
        self.thick = ro - ri

    def mask(self, P, inset=0, k=1.0):
        ri, ro = self.ri + inset, self.ro - inset
        mid = (self.ri + self.ro) / 2
        ri = mid - (mid - ri) * k
        ro = mid + (ro - mid) * k
        da0 = math.degrees(inset / max(1.0, self.ro))
        a0, a1 = self.a0 + da0, self.a1 - da0
        m = P.blank()
        if ro <= ri or a1 <= a0:
            return m
        n = max(8, int((a1 - a0) / 1.5))
        pts = []
        for i in range(n + 1):
            a = math.radians(a0 + (a1 - a0) * i / n)
            pts.append((P.lx(self.cx + math.cos(a) * ro), P.ly(self.cy + math.sin(a) * ro)))
        for i in range(n, -1, -1):
            a = math.radians(a0 + (a1 - a0) * i / n)
            pts.append((P.lx(self.cx + math.cos(a) * ri), P.ly(self.cy + math.sin(a) * ri)))
        ImageDraw.Draw(m).polygon(pts, fill=255)
        r = max(0.5, self.rnd - inset * 0.5) * S
        m = m.filter(ImageFilter.GaussianBlur(r * 0.5))
        return m.point(lambda v: max(0, min(255, int((v - 128) * 6 + 128))))


def ring_of(P, shape, w, k=1.0):
    return ImageChops.subtract(shape.mask(P, 0, k), shape.mask(P, w, k))


# --------------------------------------------------------------------------------------------- ability icons (stand-ins)
def chaikin(pts, n=3):
    for _ in range(n):
        out = []
        for i in range(len(pts)):
            a, b = pts[i], pts[(i + 1) % len(pts)]
            out.append((a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25))
            out.append((a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75))
        pts = out
    return pts


def rot(pts, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [(x * c - y * s, x * s + y * c) for x, y in pts]


def glyph_polys(name):
    """Polygons in the unit square (-1..1) as (points, tone) with tone 0 = light, 1 = mid, 2 = dark."""
    if name == "sword":
        blade = [(0, -1), (0.15, -0.8), (0.15, 0.3), (-0.15, 0.3), (-0.15, -0.8)]
        guard = [(-0.55, 0.3), (0.55, 0.3), (0.55, 0.45), (-0.55, 0.45)]
        grip = [(-0.09, 0.45), (0.09, 0.45), (0.09, 0.85), (-0.09, 0.85)]
        return [(rot(blade, 40), 0), (rot(guard, 40), 1), (rot(grip, 40), 1)]
    if name == "flame":
        o = chaikin([(0, -1), (0.38, -0.4), (0.58, 0.05), (0.5, 0.5), (0.22, 0.9), (0, 1), (-0.22, 0.9), (-0.5, 0.5),
                     (-0.58, 0.05), (-0.32, -0.15), (-0.2, -0.6)], 3)
        i = [(x * 0.5, y * 0.5 + 0.42) for x, y in chaikin([(0, -1), (0.55, 0.0), (0.4, 0.7), (0, 1), (-0.4, 0.7), (-0.55, 0.0)], 3)]
        return [(o, 0), (i, 2)]
    if name == "shield":
        o = chaikin([(-0.75, -0.8), (0.75, -0.8), (0.75, 0.0), (0.45, 0.6), (0, 1), (-0.45, 0.6), (-0.75, 0.0)], 2)
        c = [(-0.1, -0.6), (0.1, -0.6), (0.1, 0.65), (-0.1, 0.65)]
        c2 = [(-0.5, -0.2), (0.5, -0.2), (0.5, -0.02), (-0.5, -0.02)]
        return [(o, 0), (c, 2), (c2, 2)]
    if name == "bolt":
        b = [(0.25, -1), (-0.6, 0.15), (-0.05, 0.15), (-0.28, 1), (0.62, -0.22), (0.08, -0.22)]
        return [(b, 0)]
    if name == "star":
        pts = []
        for i in range(10):
            r = 1.0 if i % 2 == 0 else 0.45
            a = math.radians(-90 + i * 36)
            pts.append((math.cos(a) * r, math.sin(a) * r))
        return [(pts, 0)]
    if name == "heart":
        pts = []
        for i in range(60):
            t = math.radians(i * 6)
            pts.append((16 * math.sin(t) ** 3 / 17, -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)) / 17))
        return [(pts, 0)]
    raise ValueError(name)


ICONS = {  # background gradient top, bottom, glyph light, mid, dark
    "sword": ((120, 142, 172), (34, 44, 64), (238, 243, 250), (178, 140, 80), (110, 120, 140)),
    "flame": ((236, 118, 30), (110, 18, 10), (255, 226, 120), (255, 160, 50), (200, 60, 20)),
    "shield": ((62, 112, 204), (14, 28, 84), (232, 204, 118), (200, 170, 90), (60, 40, 20)),
    "bolt": ((120, 80, 200), (30, 18, 84), (255, 238, 120), (255, 200, 60), (180, 120, 20)),
    "star": ((70, 150, 110), (14, 56, 44), (255, 236, 130), (255, 210, 80), (200, 150, 40)),
    "heart": ((206, 46, 66), (78, 10, 26), (255, 208, 212), (255, 150, 160), (150, 30, 50)),
}


def draw_icon(P, shape, name, k=1.0, bright=1.0, dx=0.0, dy=0.0, dim=0, clip=None):
    """Icon art clipped to the shape (circle or rounded square); dx/dy shift the art, dim darkens it (0..255)."""
    box = shape.box
    cx, cy = (box[0] + box[2]) / 2 + dx, (box[1] + box[3]) / 2 + dy
    size = min(box[2] - box[0], box[3] - box[1]) * k
    clip = shape.mask(P, 0, k) if clip is None else clip
    top, bot, gl, gm, gd = ICONS[name]
    bg = P.vgrad(cy - size / 2, cy + size / 2, top, bot)
    P.fill_rgba(bg, clip)
    # soft vignette
    vg = P.blank()
    ImageDraw.Draw(vg).ellipse([P.lx(cx - size * 0.62), P.ly(cy - size * 0.62), P.lx(cx + size * 0.62), P.ly(cy + size * 0.62)], fill=255)
    vg = ImageChops.invert(vg.filter(ImageFilter.GaussianBlur(size * 0.14 * S)))
    P.fill(ImageChops.multiply(vg, clip), (0, 0, 0, 120))
    # glyph
    gs = size * 0.36
    tones = {0: gl, 1: gm, 2: gd}
    shm = P.blank()
    layers = []
    for pts, tone in glyph_polys(name):
        m = P.blank()
        ImageDraw.Draw(m).polygon([(P.lx(cx + x * gs), P.ly(cy + y * gs)) for x, y in pts], fill=255)
        layers.append((m, tone))
        if tone != 2:
            shm = ImageChops.lighter(shm, m)
    P.fill(ImageChops.multiply(ImageChops.offset(shm, 0, int(size * 0.03 * S)).filter(ImageFilter.GaussianBlur(size * 0.03 * S)), clip), (0, 0, 0, 150))
    for m, tone in layers:
        P.fill(ImageChops.multiply(m, clip), tones[tone] + (255,))
    # gloss on top
    P.fill(ImageChops.multiply(clip, P.vmask(clip, cy - size / 2, cy + size * 0.1, 70, 0)), (255, 255, 255, 255))
    if bright > 1.0:
        P.fill(clip, (255, 255, 255, int(40 * (bright - 1) / 0.3)))
    if dim:
        P.fill(clip, (0, 0, 0, dim))
    return clip


def cooldown_overlay(P, shape, frac_left, label, fnt, k=1.0, clip=None):
    """Dark clockwise sweep over the icon plus the remaining time."""
    box = shape.box
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    size = max(box[2] - box[0], box[3] - box[1]) * k
    clip = shape.mask(P, 0, k) if clip is None else clip
    m = P.blank()
    start = -90 + 360 * (1 - frac_left)
    ImageDraw.Draw(m).pieslice([P.lx(cx - size), P.ly(cy - size), P.lx(cx + size), P.ly(cy + size)], start, 270, fill=255)
    P.fill(clip, (0, 0, 0, 70))
    P.fill(ImageChops.multiply(m, clip), (0, 0, 0, 175))
    # bright sweep edge
    a = math.radians(start)
    edge = P.blank()
    ImageDraw.Draw(edge).line([(P.lx(cx), P.ly(cy)), (P.lx(cx + math.cos(a) * size), P.ly(cy + math.sin(a) * size))], fill=255, width=int(1.6 * S))
    P.fill(ImageChops.multiply(edge, clip), (255, 255, 255, 120))
    P.text((cx, cy), label, fnt, (255, 255, 255, 255), shadow=(0, 0, 5, 255))
    P.text((cx, cy), label, fnt, (255, 255, 255, 255), shadow=(0, 2, 2, 230))


# --------------------------------------------------------------------------------------------- styles
class Style:
    key = ""
    title = ""
    blurb = ""
    sans = "segoeuib.ttf"
    text_color = (255, 255, 255, 255)

    def f(self, size):
        return font(self.sans, size)

    def label(self, P, xy, s, size, pressed=False, color=None):
        P.text(xy, s, self.f(size), color or self.text_color, shadow=self.text_shadow)


class Glas(Style):
    key = "glas"
    title = "A  Glas"
    blurb = ["Halbtransparente dunkle Füllung (35 %), helle Lichtkante oben, weicher Schatten.",
             "Gedrückt: heller und auf 94 % verkleinert."]
    text_shadow = (0, 2, 4, 170)
    FILL = (10, 18, 32, 90)
    PRESS_K = 0.94

    def surface(self, P, sh, pressed=False, soft=False):
        k = self.PRESS_K if pressed else 1.0
        m = sh.mask(P, 0, k)
        P.shadow(m, 6 if not pressed else 3, 7, 110 if not soft else 70)
        P.fill(m, self.FILL)
        if pressed:
            P.fill(m, (170, 205, 245, 86))
        y0, y1 = sh.box[1], sh.box[3]
        h = y1 - y0
        P.fill(P.vmask(m, y0, y0 + h * 0.55, 62 if not pressed else 84, 0), (255, 255, 255, 255))
        rim = ring_of(P, sh, 2.4, k)
        P.fill(P.vmask(rim, y0, y1, 235 if not pressed else 255, 70), (236, 246, 255, 255))
        inner = ImageChops.subtract(sh.mask(P, 2.4, k), sh.mask(P, 4.4, k))
        P.fill(P.vmask(inner, y0, y1, 26, 0), (255, 255, 255, 255))
        return m

    def base(self, P, sh, pressed=False):
        """Large quiet disc behind a stick: weaker fill, thin rim."""
        m = sh.mask(P)
        P.shadow(m, 5, 8, 80)
        P.fill(m, (10, 18, 32, 62 if not pressed else 84))
        y0, y1 = sh.box[1], sh.box[3]
        rim = ring_of(P, sh, 2.0)
        P.fill(P.vmask(rim, y0, y1, 200 if not pressed else 255, 60), (236, 246, 255, 255))
        P.fill(P.vmask(m, y0, y0 + (y1 - y0) * 0.5, 30, 0), (255, 255, 255, 255))

    def thumb(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.shadow(m, 5, 5, 120)
        P.fill(m, (150, 180, 220, 120 if not pressed else 190))
        y0, y1 = sh.box[1], sh.box[3]
        P.fill(P.vmask(m, y0, y1, 120, 0), (255, 255, 255, 255))
        P.fill(P.vmask(ring_of(P, sh, 2.2), y0, y1, 240, 90), (240, 248, 255, 255))

    def segment(self, P, sh, pressed=False):
        k = 0.96 if pressed else 1.0
        m = sh.mask(P, 0, k)
        P.shadow(m, 4, 5, 90)
        P.fill(m, self.FILL if not pressed else (10, 18, 32, 90))
        if pressed:
            P.fill(m, (170, 205, 245, 100))
        y0, y1 = sh.box[1], sh.box[3]
        rim = ring_of(P, sh, 2.0, k)
        P.fill(P.vmask(rim, sh.my - sh.thick, sh.my + sh.thick, 230 if not pressed else 255, 70), (236, 246, 255, 255))
        P.fill(P.vmask(m, sh.my - sh.thick, sh.my, 40, 0), (255, 255, 255, 255))

    def wheel(self, P, sh, vertical, phase, pressed=False):
        m = self.surface(P, sh, pressed)
        k = self.PRESS_K if pressed else 1.0
        self._grooves(P, sh, m, vertical, phase, (236, 246, 255), 70 if not pressed else 110)
        # cylinder shading
        x0, y0, x1, y1 = sh.box
        if vertical:
            sx = P.vgrad(0, 1, (0, 0, 0, 0), (0, 0, 0, 0))
            ramp = np.abs(((np.arange(P.w) + P.ox) / S - sh.cx) / ((x1 - x0) / 2))
            arr = np.broadcast_to((np.clip(ramp, 0, 1) ** 2 * 110)[None, :], (P.h, P.w)).astype(np.uint8)
        else:
            ramp = np.abs(((np.arange(P.h) + P.oy) / S - sh.cy) / ((y1 - y0) / 2))
            arr = np.broadcast_to((np.clip(ramp, 0, 1) ** 2 * 110)[:, None], (P.h, P.w)).astype(np.uint8)
        P.fill(ImageChops.multiply(Image.fromarray(arr, "L"), sh.mask(P, 0, k)), (0, 0, 0, 255))
        P.fill(ring_of(P, sh, 2.0, k).point(lambda v: v * 0), (0, 0, 0, 0))

    def _grooves(self, P, sh, clip, vertical, phase, color, alpha, spacing=18):
        x0, y0, x1, y1 = sh.box
        g = P.blank()
        d = ImageDraw.Draw(g)
        if vertical:
            y = y0 + phase
            while y < y1:
                d.line([(P.lx(x0), P.ly(y)), (P.lx(x1), P.ly(y))], fill=255, width=max(1, int(2 * S)))
                y += spacing
        else:
            x = x0 + phase
            while x < x1:
                d.line([(P.lx(x), P.ly(y0)), (P.lx(x), P.ly(y1))], fill=255, width=max(1, int(2 * S)))
                x += spacing
        P.fill(ImageChops.multiply(g, sh.mask(P, 5)), tuple(color) + (alpha,))

    def chevron(self, P, pts, pressed=False):
        d = P.blank()
        ImageDraw.Draw(d).line([(P.lx(x), P.ly(y)) for x, y in pts], fill=255, width=int(3 * S), joint="curve")
        P.fill(d, (240, 248, 255, 230))

    def frame(self, P, sh):
        self.surface(P, sh)

    def cell(self, P, sh, pressed=False):
        k = 0.94 if pressed else 1.0
        m = sh.mask(P, 0, k)
        if pressed:
            P.fill(m, (190, 220, 255, 100))
            P.fill(ring_of(P, sh, 2.0, k), (240, 248, 255, 210))
        else:
            P.fill(m, (255, 255, 255, 16))
            P.fill(P.vmask(ring_of(P, sh, 1.4), sh.box[1], sh.box[3], 90, 10), (240, 248, 255, 255))

    def icon_frame(self, P, sh, pressed=False, k=1.0):
        rim = ring_of(P, sh, 2.0, k)
        P.fill(P.vmask(rim, sh.box[1], sh.box[3], 255 if pressed else 220, 80), (240, 248, 255, 255))

    def icon_ring(self, P, sh, pressed=False):
        k = self.PRESS_K if pressed else 1.0
        m = sh.mask(P, 0, k)
        P.shadow(m, 5 if not pressed else 3, 6, 115)
        P.fill(m, (10, 18, 32, 120))
        return k


class Azeroth(Style):
    key = "azeroth"
    title = "B  Azeroth"
    blurb = ["Dunkle Füllung, dünner Bronze-Gold-Rand in Anlehnung an die WoW-Oberfläche,",
             "Symbole mit Rundecke. Gedrückt: Goldglühen am Rand (kurz)."]
    sans = "georgiab.ttf"
    text_color = (250, 226, 160, 255)
    text_shadow = (0, 2, 0, 230)
    BRONZE0, BRONZE1 = (196, 146, 70), (92, 60, 26)
    GOLD = (244, 206, 118)

    def surface(self, P, sh, pressed=False, soft=False):
        m = sh.mask(P)
        y0, y1 = sh.box[1], sh.box[3]
        P.shadow(m, 5, 6, 170)
        if pressed:
            P.glow(ring_of(P, sh, 5.0), 7, (255, 196, 70), 230)
        P.fill_rgba(P.vgrad(y0, y1, (40, 30, 22, 226) if not pressed else (66, 48, 24, 236), (14, 10, 8, 232) if not pressed else (34, 22, 10, 236)), m)
        rim = ring_of(P, sh, 3.6)
        P.fill_rgba(P.vgrad(y0, y1, self.BRONZE0 if not pressed else (255, 220, 130), self.BRONZE1 if not pressed else (170, 110, 40)), rim)
        dark = ImageChops.subtract(sh.mask(P, 3.6), sh.mask(P, 5.2))
        P.fill(dark, (10, 6, 3, 220))
        hair = ImageChops.subtract(sh.mask(P, 5.2), sh.mask(P, 6.4))
        P.fill(hair, self.GOLD + ((150 if not pressed else 255),))
        P.fill(P.vmask(m, y0, y0 + (y1 - y0) * 0.5, 18, 0), (255, 235, 190, 255))
        return m

    def base(self, P, sh, pressed=False):
        m = sh.mask(P)
        y0, y1 = sh.box[1], sh.box[3]
        P.shadow(m, 5, 7, 120)
        P.fill(m, (12, 9, 7, 96 if not pressed else 130))
        rim = ring_of(P, sh, 2.6)
        P.fill_rgba(P.vgrad(y0, y1, self.BRONZE0 if not pressed else (255, 220, 130), self.BRONZE1), rim)
        hair = ImageChops.subtract(sh.mask(P, 2.6), sh.mask(P, 3.6))
        P.fill(hair, self.GOLD + (110,))

    def thumb(self, P, sh, pressed=False):
        m = sh.mask(P)
        y0, y1 = sh.box[1], sh.box[3]
        P.shadow(m, 5, 5, 150)
        if pressed:
            P.glow(ring_of(P, sh, 4.0), 7, (255, 196, 70), 220)
        P.fill_rgba(P.vgrad(y0, y1, (92, 70, 44, 235) if not pressed else (140, 104, 50, 240), (30, 22, 14, 235)), m)
        P.fill_rgba(P.vgrad(y0, y1, self.BRONZE0, self.BRONZE1), ring_of(P, sh, 3.0))
        P.fill(ImageChops.subtract(sh.mask(P, 3.0), sh.mask(P, 4.2)), (10, 6, 3, 220))
        P.fill(ImageChops.subtract(sh.mask(P, 4.2), sh.mask(P, 5.2)), self.GOLD + (140,))

    def segment(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.shadow(m, 4, 5, 150)
        if pressed:
            P.glow(ring_of(P, sh, 4.0), 7, (255, 196, 70), 230)
        P.fill(m, (22, 16, 11, 222) if not pressed else (62, 44, 22, 236))
        rim = ring_of(P, sh, 2.8)
        P.fill_rgba(P.vgrad(sh.my - sh.thick, sh.my + sh.thick, self.BRONZE0 if not pressed else (255, 220, 130), self.BRONZE1), rim)
        hair = ImageChops.subtract(sh.mask(P, 2.8), sh.mask(P, 3.8))
        P.fill(hair, self.GOLD + ((100 if not pressed else 230),))

    def wheel(self, P, sh, vertical, phase, pressed=False):
        m = self.surface(P, sh, pressed)
        x0, y0, x1, y1 = sh.box
        g = P.blank()
        d = ImageDraw.Draw(g)
        spacing = 18
        if vertical:
            y = y0 + phase
            while y < y1:
                d.line([(P.lx(x0), P.ly(y)), (P.lx(x1), P.ly(y))], fill=255, width=int(2 * S))
                y += spacing
        else:
            x = x0 + phase
            while x < x1:
                d.line([(P.lx(x), P.ly(y0)), (P.lx(x), P.ly(y1))], fill=255, width=int(2 * S))
                x += spacing
        clip = sh.mask(P, 7)
        P.fill(ImageChops.multiply(g, clip), (196, 146, 70, 130 if not pressed else 200))
        if vertical:
            ramp = np.abs(((np.arange(P.w) + P.ox) / S - sh.cx) / ((x1 - x0) / 2))
            arr = np.broadcast_to((np.clip(ramp, 0, 1) ** 2 * 120)[None, :], (P.h, P.w)).astype(np.uint8)
        else:
            ramp = np.abs(((np.arange(P.h) + P.oy) / S - sh.cy) / ((y1 - y0) / 2))
            arr = np.broadcast_to((np.clip(ramp, 0, 1) ** 2 * 120)[:, None], (P.h, P.w)).astype(np.uint8)
        P.fill(ImageChops.multiply(Image.fromarray(arr, "L"), clip), (0, 0, 0, 255))

    def chevron(self, P, pts, pressed=False):
        d = P.blank()
        ImageDraw.Draw(d).line([(P.lx(x), P.ly(y)) for x, y in pts], fill=255, width=int(3 * S), joint="curve")
        P.fill(d, self.GOLD + (240,))

    def frame(self, P, sh):
        self.surface(P, sh)

    def cell(self, P, sh, pressed=False):
        m = sh.mask(P)
        y0, y1 = sh.box[1], sh.box[3]
        if pressed:
            P.glow(ring_of(P, sh, 3.0), 6, (255, 196, 70), 220)
        P.fill(m, (6, 4, 3, 190) if not pressed else (44, 30, 14, 220))
        P.fill(P.vmask(ring_of(P, sh, 3.0), y0, y1, 90, 200) if not pressed else ring_of(P, sh, 3.0), (0, 0, 0, 255) if not pressed else (255, 214, 120, 255))
        P.fill(ImageChops.subtract(sh.mask(P, 3.0), sh.mask(P, 4.0)), self.GOLD + ((80 if not pressed else 230),))

    def icon_frame(self, P, sh, pressed=False, k=1.0):
        y0, y1 = sh.box[1], sh.box[3]
        P.fill_rgba(P.vgrad(y0, y1, (230, 184, 96) if pressed else self.BRONZE0, self.BRONZE1), ring_of(P, sh, 3.0, k))
        P.fill(ImageChops.subtract(sh.mask(P, 3.0, k), sh.mask(P, 4.0, k)), (10, 6, 3, 220))

    def icon_ring(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.shadow(m, 5, 6, 170)
        if pressed:
            P.glow(ring_of(P, sh, 4.0), 7, (255, 196, 70), 230)
        P.fill(m, (20, 14, 10, 230))
        return 1.0


class Minimal(Style):
    key = "minimal"
    title = "C  Minimal+"
    blurb = ["Der heutige Stil, aber eine einheitliche Strichstärke (3 px) überall, fette Schrift mit Schatten,",
             "gefüllte Mitte bei Aktivität. Kein Schatten, kein Verlauf: am günstigsten zu zeichnen."]
    text_shadow = (0, 2, 3, 200)
    W = 3.0
    LINE = (255, 255, 255, 215)

    def surface(self, P, sh, pressed=False, soft=False):
        m = sh.mask(P)
        P.fill(m, (0, 0, 0, 34) if not pressed else (255, 255, 255, 92))
        P.fill(ring_of(P, sh, self.W), (255, 255, 255, 215 if not pressed else 255))
        return m

    def base(self, P, sh, pressed=False):
        self.surface(P, sh, False)

    def thumb(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.fill(m, (255, 255, 255, 70 if not pressed else 130))
        P.fill(ring_of(P, sh, self.W), (255, 255, 255, 215 if not pressed else 255))

    def segment(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.fill(m, (0, 0, 0, 34) if not pressed else (255, 255, 255, 92))
        P.fill(ring_of(P, sh, self.W), (255, 255, 255, 215 if not pressed else 255))

    def wheel(self, P, sh, vertical, phase, pressed=False):
        self.surface(P, sh, pressed)
        x0, y0, x1, y1 = sh.box
        g = P.blank()
        d = ImageDraw.Draw(g)
        spacing = 18
        if vertical:
            y = y0 + phase
            while y < y1:
                d.line([(P.lx(x0), P.ly(y)), (P.lx(x1), P.ly(y))], fill=255, width=int(2 * S))
                y += spacing
        else:
            x = x0 + phase
            while x < x1:
                d.line([(P.lx(x), P.ly(y0)), (P.lx(x), P.ly(y1))], fill=255, width=int(2 * S))
                x += spacing
        P.fill(ImageChops.multiply(g, sh.mask(P, self.W)), (255, 255, 255, 100 if not pressed else 150))

    def chevron(self, P, pts, pressed=False):
        d = P.blank()
        ImageDraw.Draw(d).line([(P.lx(x), P.ly(y)) for x, y in pts], fill=255, width=int(self.W * S), joint="curve")
        P.fill(d, (255, 255, 255, 230))

    def frame(self, P, sh):
        self.surface(P, sh)

    def cell(self, P, sh, pressed=False):
        if pressed:
            P.fill(sh.mask(P), (255, 255, 255, 96))
            P.fill(ring_of(P, sh, self.W), (255, 255, 255, 255))
        else:
            P.fill(ring_of(P, sh, 1.6), (255, 255, 255, 90))

    def icon_frame(self, P, sh, pressed=False, k=1.0):
        P.fill(ring_of(P, sh, self.W, k), (255, 255, 255, 255 if pressed else 215))

    def icon_ring(self, P, sh, pressed=False):
        return 1.0


# --------------------------------------------------------------------------------------------- elements
def round_button_text(st, canvas, cx, cy, r, text, pressed=False, size=30):
    P = Patch(cx - r, cy - r, cx + r, cy + r, pad=34)
    st.surface(P, Circ(cx, cy, r), pressed)
    k = getattr(st, "PRESS_K", 1.0) if pressed else 1.0
    st.label(P, (cx, cy), text, size * k, pressed)
    P.paste_on(canvas)


def round_button_icon(st, canvas, cx, cy, r, icon, pressed=False, cd=None):
    P = Patch(cx - r, cy - r, cx + r, cy + r, pad=34)
    sh = Circ(cx, cy, r)
    if isinstance(st, Minimal):
        st.surface(P, sh, pressed)
        k = 0.8
        draw_icon(P, Circ(cx, cy, r), icon, k=k, bright=1.3 if pressed else 1.0)
        if cd:
            cooldown_overlay(P, Circ(cx, cy, r), cd[0], cd[1], st.f(34), k=k)
    else:
        kk = st.icon_ring(P, sh, pressed)
        k = (0.9 * kk) if isinstance(st, Glas) else 0.86
        if isinstance(st, Glas):
            P.fill(sh.mask(P, 0, kk), (0, 0, 0, 0))
            # glass ring around the icon tile
            rim = ring_of(P, sh, 3.0, kk)
            P.fill(P.vmask(rim, cy - r, cy + r, 240 if pressed else 220, 70), (240, 248, 255, 255))
            P.fill(P.vmask(sh.mask(P, 0, kk), cy - r, cy, 40 if not pressed else 70, 0), (255, 255, 255, 255))
        else:
            st.icon_frame(P, sh, pressed)
        draw_icon(P, Circ(cx, cy, r * (0.9 if isinstance(st, Glas) else 0.86) * kk), icon, bright=1.3 if pressed else 1.0)
        if cd:
            cooldown_overlay(P, Circ(cx, cy, r * (0.9 if isinstance(st, Glas) else 0.86) * kk), cd[0], cd[1], st.f(34))
    P.paste_on(canvas)


def pill_button(st, canvas, cx, cy, w, h, text, pressed=False):
    P = Patch(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, pad=34)
    st.surface(P, RRect.pill(cx, cy, w, h), pressed)
    st.label(P, (cx, cy), text, 26 * (getattr(st, "PRESS_K", 1.0) if pressed else 1.0), pressed)
    P.paste_on(canvas)


def segment_content(st, P, sec, item, pressed):
    mx, my = sec.mx, sec.my
    if item is None:
        return
    kind, val = item
    if kind == "icon":
        r = sec.thick * 0.36
        sh = Circ(mx, my, r)
        draw_icon(P, sh, val, bright=1.3 if pressed else 1.0)
        if isinstance(st, Minimal):
            P.fill(ring_of(P, sh, 2.0), (255, 255, 255, 230))
        elif isinstance(st, Glas):
            P.fill(P.vmask(ring_of(P, sh, 1.8), my - r, my + r, 230, 80), (240, 248, 255, 255))
        else:
            st.icon_frame(P, sh, pressed)
    else:
        st.label(P, (mx, my), val, 21, pressed)


def radial(st, canvas, cx, cy, R, pressed_seg=1):
    ro, ri = R, R * 0.745
    stick_r = R * 0.62
    thumb_r = R * 0.32
    P = Patch(cx - R, cy - R, cx + R, cy + R, pad=34)
    st.base(P, Circ(cx, cy, stick_r), False)
    items = [("icon", "sword"), ("icon", "flame"), ("icon", "shield"), ("text", "Tab"), ("icon", "bolt"), None, ("icon", "heart"), ("text", "F5")]
    step = 360 / 8
    for i in range(8):
        c = -90 + i * step
        sec = Sect(cx, cy, ri, ro, c - (step - 4) / 2, c + (step - 4) / 2, rnd=7)
        pr = (i == pressed_seg)
        st.segment(P, sec, pr)
        segment_content(st, P, sec, items[i], pr)
    th = Circ(cx, cy, thumb_r)
    st.thumb(P, th, False)
    st.label(P, (cx, cy), "Space", 34)
    P.paste_on(canvas)


def joystick(st, canvas, cx, cy, R, pressed=False):
    P = Patch(cx - R, cy - R, cx + R, cy + R, pad=34)
    st.base(P, Circ(cx, cy, R), pressed)
    tx, ty = (cx + R * 0.34, cy - R * 0.28) if pressed else (cx, cy)
    st.thumb(P, Circ(tx, ty, R * 0.42), pressed)
    P.paste_on(canvas)


def mouse_wheel(st, canvas, cx, cy, vertical, pressed=False, phase=0.0):
    w, h = (84, 236) if vertical else (236, 84)
    P = Patch(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, pad=34)
    sh = RRect.pill(cx, cy, w, h)
    st.wheel(P, sh, vertical, phase, pressed)
    s = 15
    if vertical:
        top, bot = cy - h / 2 + w / 2, cy + h / 2 - w / 2
        st.chevron(P, [(cx - s, top + s * 0.5), (cx, top - s * 0.5), (cx + s, top + s * 0.5)], pressed)
        st.chevron(P, [(cx - s, bot - s * 0.5), (cx, bot + s * 0.5), (cx + s, bot - s * 0.5)], pressed)
    else:
        l, r = cx - w / 2 + h / 2, cx + w / 2 - h / 2
        st.chevron(P, [(l + s * 0.5, cy - s), (l - s * 0.5, cy), (l + s * 0.5, cy + s)], pressed)
        st.chevron(P, [(r - s * 0.5, cy - s), (r + s * 0.5, cy), (r - s * 0.5, cy + s)], pressed)
    P.paste_on(canvas)


def grid(st, canvas, x0, y0, rows, cols, cell, cells, pad=11, gap=8, pressed=None, cd=None):
    """Knopffeld: one frame, free cells. cells[r][c] = ('text', s) | ('icon', name)."""
    w = cols * cell + (cols - 1) * gap + 2 * pad
    h = rows * cell + (rows - 1) * gap + 2 * pad
    P = Patch(x0, y0, x0 + w, y0 + h, pad=34)
    frame = RRect(x0, y0, x0 + w, y0 + h, 26)
    st.frame(P, frame)
    crad = 16
    for r in range(rows):
        for c in range(cols):
            cx0 = x0 + pad + c * (cell + gap)
            cy0 = y0 + pad + r * (cell + gap)
            sh = RRect(cx0, cy0, cx0 + cell, cy0 + cell, crad)
            pr = pressed == (r, c)
            if isinstance(st, Minimal) and gap > 0:
                pass
            st.cell(P, sh, pr)
            kind, val = cells[r][c]
            ccx, ccy = sh.cx, sh.cy
            if kind == "text":
                st.label(P, (ccx, ccy), val, 28 * (0.94 if pr and isinstance(st, Glas) else 1.0), pr)
            else:
                k = 0.94 if pr and isinstance(st, Glas) else 1.0
                ish = RRect(cx0 + 7, cy0 + 7, cx0 + cell - 7, cy0 + cell - 7, 11)
                draw_icon(P, ish, val, k=k, bright=1.3 if pr else 1.0)
                st.icon_frame(P, ish, pr, k)
                if cd and cd[0] == (r, c):
                    cooldown_overlay(P, ish, cd[1], cd[2], st.f(30), k=k)
    P.paste_on(canvas)
    return w, h


# --------------------------------------------------------------------------------------------- Classic (Phase 1b)
# Form language taken from the game UI: chamfered (45 degree cut) square slots with a thin bevelled frame and inner
# shadow, dark half-transparent panels with a bronze rim, red buttons with a metal rim and yellow serif text,
# thin bronze rings. Everything is drawn here from shapes and colours, no game graphics are used.
GOLD = (255, 209, 0)
BRONZE_L, BRONZE_M, BRONZE_D = (178, 136, 86), (126, 92, 54), (56, 38, 22)
STONE_L, STONE_M, STONE_D = (166, 148, 128), (112, 96, 80), (62, 50, 40)
HILITE = (255, 212, 30)


class Cham:
    """Rectangle with cut (chamfered) corners, like the slots and panels of the game UI."""

    def __init__(self, x0, y0, x1, y1, c):
        self.x0, self.y0, self.x1, self.y1, self.c = x0, y0, x1, y1, c
        self.box = (x0, y0, x1, y1)
        self.cx, self.cy = (x0 + x1) / 2, (y0 + y1) / 2

    def mask(self, P, inset=0, k=1.0):
        m = P.blank()
        hw, hh = (self.x1 - self.x0) / 2 * k - inset, (self.y1 - self.y0) / 2 * k - inset
        if hw <= 0 or hh <= 0:
            return m
        c = max(0.0, min(self.c * k - 0.586 * inset, hw, hh))
        l, t, r, b = self.cx - hw, self.cy - hh, self.cx + hw, self.cy + hh
        pts = [(l + c, t), (r - c, t), (r, t + c), (r, b - c), (r - c, b), (l + c, b), (l, b - c), (l, t + c)]
        ImageDraw.Draw(m).polygon([(P.lx(x), P.ly(y)) for x, y in pts], fill=255)
        return m

    def inner(self, d):
        return Cham(self.x0 + d, self.y0 + d, self.x1 - d, self.y1 - d, max(0.0, self.c - 0.586 * d))


class Classic:
    sans = "palab.ttf"          # stand-in for the serif of the game UI (the app would use "serif" bold)

    def __init__(self, dark):
        self.dark = dark
        self.key = "classic_dunkel" if dark else "classic_hell"
        self.title = "E  Classic dunkel" if dark else "D  Classic hell"
        self.blurb = (["Dunkle Panels mit dünnem Goldrand, Slots mit schmalem Goldrahmen,",
                       "Schrift goldgelb mit dunkler Kontur. Gedrückt: Symbol dunkler und 2 px versetzt, gelber Rand."] if dark else
                      ["Wie die Aktionsleiste des Spiels: abgeschrägter grauer Steinrahmen mit Innenschatten, Textknöpfe rot mit",
                       "goldgelber Serifenschrift, Ringe bronze. Gedrückt: Symbol dunkler und 2 px versetzt, gelber Rand."])

    def f(self, size):
        return font(self.sans, size)

    # ---- helpers
    def text(self, P, xy, s, size, color=GOLD, pressed=False, outline=1.4):
        off = 1.6 if pressed else 0.0
        col = tuple(int(v * (0.82 if pressed else 1.0)) for v in color) + (255,)
        P.text((xy[0] + off, xy[1] + off), s, self.f(size), col, shadow=(0, 1.2, 0, 150), outline=outline)

    def rim_color(self):
        return (BRONZE_L, BRONZE_D) if not self.dark else ((222, 176, 78), (122, 88, 30))

    def bevel(self, P, sh, w, pressed=False):
        """Frame of width w: stone bevel (hell) or thin gold line (dunkel), dark hairlines outside and inside."""
        b = sh.box
        P.fill(ring_of(P, sh, 1.0), (12, 9, 6, 235))
        sub = ImageChops.subtract(sh.mask(P, 1.0), sh.mask(P, 1.0 + w))
        if self.dark:
            P.fill_rgba(P.dgrad(b, (232, 188, 90), (118, 84, 28)), sub)
        else:
            P.fill_rgba(P.dgrad(b, STONE_L, STONE_D), sub)
        P.fill(ImageChops.subtract(sh.mask(P, 1.0 + w), sh.mask(P, 2.0 + w)), (8, 8, 8, 230))
        if pressed:
            P.fill(ring_of(P, sh, 2.4), HILITE + (255,))

    def inner_shadow(self, P, sh, inset, depth=9, alpha=150):
        m = sh.mask(P, inset)
        b = sh.box
        P.fill(ImageChops.multiply(m, P.vmask(m, b[1] + inset, b[1] + inset + depth, alpha, 0)), (0, 0, 0, 255))
        xs = (np.arange(P.w) + P.ox) / S
        t = np.clip((xs - (b[0] + inset)) / max(1e-6, depth), 0, 1)
        arr = np.broadcast_to(((1 - t) * alpha * 0.6)[None, :], (P.h, P.w)).astype(np.uint8)
        P.fill(ImageChops.multiply(m, Image.fromarray(arr, "L")), (0, 0, 0, 255))

    # ---- action slot (square, chamfered)
    def slot(self, P, sh, icon=None, pressed=False, cd=None, text=None, text_size=26, shadow=True):
        w = 3.4 if not self.dark else 2.0
        m = sh.mask(P)
        if shadow:
            P.shadow(m, 3, 3, 90)
        P.fill(m, (20, 20, 22, 235) if not self.dark else (8, 10, 14, 235))
        inner = sh.mask(P, 2.0 + w)
        ish = sh.inner(1.0 + w)
        if icon:
            off = 1.8 if pressed else 0.0
            draw_icon(P, ish, icon, dx=off * 0.5, dy=off * 0.5, dim=85 if pressed else 0, clip=inner)
            if cd:
                cooldown_overlay(P, ish, cd[0], cd[1], self.f(36 if sh.x1 - sh.x0 > 90 else 28), clip=inner)
        else:
            P.fill(inner, (30, 30, 32, 160) if not self.dark else (12, 14, 18, 120))
            if text:
                self.text(P, (sh.cx, sh.cy), text, text_size, pressed=pressed)
        self.inner_shadow(P, sh, 1.0 + w, depth=8)
        self.bevel(P, sh, w, pressed)

    # ---- dark translucent panel with rim (frame of Knopfreihe/Tastenfeld, wheel)
    def panel(self, P, sh, pressed=False, shadow=True):
        m = sh.mask(P)
        if shadow:
            P.shadow(m, 4, 5, 110)
        b = sh.box
        P.fill_rgba(P.vgrad(b[1], b[3], (24, 24, 26, 212), (12, 12, 14, 220)) if not self.dark
                    else P.vgrad(b[1], b[3], (14, 16, 22, 220), (6, 8, 12, 228)), m)
        self.bevel(P, sh, 3.0 if not self.dark else 2.0, pressed)

    # ---- round text button, ring base, thumb
    def round_text(self, P, cx, cy, r, text, pressed=False, size=34):
        sh = Circ(cx, cy, r)
        m = sh.mask(P)
        P.shadow(m, 3, 4, 100)
        P.fill(m, (14, 14, 16, 200) if not pressed else (26, 24, 18, 215))
        self.ring(P, sh, 3.2 if not self.dark else 2.4, pressed)
        self.text(P, (cx, cy), text, size, pressed=pressed)

    def ring(self, P, sh, w, pressed=False):
        b = sh.box
        P.fill(ring_of(P, sh, 1.0), (12, 9, 6, 235))
        rim = ImageChops.subtract(sh.mask(P, 1.0), sh.mask(P, 1.0 + w))
        c0, c1 = self.rim_color()
        P.fill_rgba(P.dgrad(b, c0, c1), rim)
        P.fill(ImageChops.subtract(sh.mask(P, 1.0 + w), sh.mask(P, 2.0 + w)), (8, 8, 8, 215))
        if pressed:
            P.fill(ring_of(P, sh, 2.4), HILITE + (255,))

    def base(self, P, sh, pressed=False):
        P.fill(sh.mask(P), (8, 8, 10, 92))
        self.ring(P, sh, 2.6 if not self.dark else 2.0, False)

    def thumb(self, P, sh, pressed=False):
        m = sh.mask(P)
        b = sh.box
        P.shadow(m, 3, 4, 110)
        if self.dark:
            P.fill_rgba(P.vgrad(b[1], b[3], (30, 32, 38, 235), (10, 12, 16, 235)), m)
        else:
            P.fill_rgba(P.dgrad(b, (142, 138, 130, 235), (66, 64, 60, 235)), m)
        self.inner_shadow(P, sh, 3.0, depth=10, alpha=90)
        self.ring(P, sh, 3.0 if not self.dark else 2.0, pressed)

    # ---- red (hell) or dark (dunkel) text button
    def text_button(self, P, sh, text, pressed=False, size=28):
        b = sh.box
        m = sh.mask(P)
        P.shadow(m, 3, 3, 100)
        if self.dark:
            P.fill_rgba(P.vgrad(b[1], b[3], (20, 22, 28, 225) if not pressed else (34, 30, 20, 232),
                                (8, 10, 14, 228) if not pressed else (20, 16, 8, 232)), m)
            self.bevel(P, sh, 2.0, pressed)
        else:
            top, bot = ((178, 20, 16), (96, 0, 0)) if not pressed else ((128, 12, 8), (58, 0, 0))
            P.fill_rgba(P.vgrad(b[1], b[3], top, bot), m)
            P.fill(ImageChops.multiply(m, P.vmask(m, b[1], b[1] + 8, 0 if pressed else 60, 0)), (255, 120, 90, 255))
            self.inner_shadow(P, sh, 3.0, depth=7, alpha=110 if pressed else 60)
            sub = ImageChops.subtract(sh.mask(P, 1.0), sh.mask(P, 3.4))
            P.fill(ring_of(P, sh, 1.0), (12, 9, 6, 235))
            P.fill_rgba(P.dgrad(b, (172, 150, 120), (70, 56, 40)) if not pressed else P.dgrad(b, (110, 92, 70), (60, 48, 34)), sub)
            if pressed:
                P.fill(ring_of(P, sh, 2.0), HILITE + (255,))
        self.text(P, (sh.cx, sh.cy), text, size, pressed=pressed)

    # ---- radial segment
    def segment(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.shadow(m, 2, 3, 100)
        P.fill(m, (10, 10, 12, 200) if not pressed else (36, 32, 18, 215))
        P.fill(ring_of(P, sh, 1.0), (12, 9, 6, 230))
        rim = ImageChops.subtract(sh.mask(P, 1.0), sh.mask(P, 3.2 if not self.dark else 2.4))
        c0, c1 = self.rim_color()
        P.fill_rgba(P.dgrad((sh.mx - sh.thick, sh.my - sh.thick, sh.mx + sh.thick, sh.my + sh.thick), c0, c1), rim)
        if pressed:
            P.fill(ring_of(P, sh, 2.4), HILITE + (255,))

    # ---- mouse wheel
    def wheel(self, P, sh, vertical, phase, pressed=False):
        self.panel(P, sh, pressed)
        x0, y0, x1, y1 = sh.box
        g = P.blank()
        d = ImageDraw.Draw(g)
        sp = 16
        inner = sh.mask(P, 5.5 if not self.dark else 4.0)
        light = P.blank()
        dl = ImageDraw.Draw(light)
        if vertical:
            y = y0 + phase
            while y < y1:
                d.line([(P.lx(x0), P.ly(y)), (P.lx(x1), P.ly(y))], fill=255, width=int(2 * S))
                dl.line([(P.lx(x0), P.ly(y + 2)), (P.lx(x1), P.ly(y + 2))], fill=255, width=int(1 * S))
                y += sp
        else:
            x = x0 + phase
            while x < x1:
                d.line([(P.lx(x), P.ly(y0)), (P.lx(x), P.ly(y1))], fill=255, width=int(2 * S))
                dl.line([(P.lx(x + 2), P.ly(y0)), (P.lx(x + 2), P.ly(y1))], fill=255, width=int(1 * S))
                x += sp
        P.fill(ImageChops.multiply(g, inner), (0, 0, 0, 170))
        P.fill(ImageChops.multiply(light, inner), (255, 255, 255, 38))

    def arrow(self, P, cx, cy, direction, size=11):
        """Filled yellow triangle like the dropdown arrows of the UI. direction: u d l r."""
        s = size
        pts = {"u": [(cx - s, cy + s * 0.6), (cx + s, cy + s * 0.6), (cx, cy - s * 0.7)],
               "d": [(cx - s, cy - s * 0.6), (cx + s, cy - s * 0.6), (cx, cy + s * 0.7)],
               "l": [(cx + s * 0.6, cy - s), (cx + s * 0.6, cy + s), (cx - s * 0.7, cy)],
               "r": [(cx - s * 0.6, cy - s), (cx - s * 0.6, cy + s), (cx + s * 0.7, cy)]}[direction]
        m = P.blank()
        ImageDraw.Draw(m).polygon([(P.lx(x), P.ly(y)) for x, y in pts], fill=255)
        P.fill(ImageChops.offset(m, 0, int(1.4 * S)), (0, 0, 0, 200))
        P.fill(m, GOLD + (255,))


# --------------------------------------------------------------------------------------------- Classic ruhig (Phase 1c)
# Same form language as Classic (chamfered slots, rings, serif type) but calm: thin (1-2 px) grey-brown frames, gold only
# for the text and the pressed state, no red areas, dark half-transparent fills so the game scene shows through,
# few and soft lines (fine wheel ribs, faint segment edges, joystick almost only an outline).
TEXT_GOLD = (232, 198, 70)
EDGE_L, EDGE_D = (146, 134, 118), (84, 75, 64)


class Ruhig(Classic):
    def __init__(self):
        super().__init__(True)
        self.key = "classic_ruhig"
        self.title = "F  Classic ruhig"
        self.blurb = ["Gleiche Formsprache, aber leise: 1-2 px Rahmen in gedämpftem Graubraun, Fläche dunkel und halbtransparent,",
                      "Gold nur für Schrift und den gedrückten Zustand (Symbol dunkler, 2 px versetzt, dünner gelber Rand)."]

    # ---- helpers
    def text(self, P, xy, s, size, color=TEXT_GOLD, pressed=False, outline=0.9):
        off = 1.5 if pressed else 0.0
        col = tuple(int(v * (0.80 if pressed else 1.0)) for v in color) + (255,)
        P.text((xy[0] + off, xy[1] + off), s, self.f(size * 0.94), col, shadow=(0, 1.0, 0, 120), outline=outline)

    def frame(self, P, sh, w=1.6, pressed=False, alpha=205):
        """Thin muted frame (light edge top left, dark bottom right) and one dark hairline inside; pressed: thin yellow line."""
        b = sh.box
        if pressed:
            P.fill(ring_of(P, sh, 1.7), HILITE + (240,))
        else:
            rim = ImageChops.subtract(sh.mask(P), sh.mask(P, w))
            P.fill_rgba(P.dgrad(b, EDGE_L + (alpha,), EDGE_D + (alpha,)), rim)
        P.fill(ImageChops.subtract(sh.mask(P, w), sh.mask(P, w + 0.8)), (0, 0, 0, 105))

    def bevel(self, P, sh, w, pressed=False):
        self.frame(P, sh, 1.6, pressed)

    def ring(self, P, sh, w, pressed=False):
        self.frame(P, sh, 1.5, pressed)

    # ---- action slot
    def slot(self, P, sh, icon=None, pressed=False, cd=None, text=None, text_size=26, shadow=True):
        w = 1.6
        m = sh.mask(P)
        if shadow:
            P.shadow(m, 2, 3, 55)
        P.fill(m, (8, 9, 12, 150) if not pressed else (14, 13, 10, 165))
        inner = sh.mask(P, w + 0.4)
        ish = sh.inner(w + 0.4)
        if icon:
            off = 1.5 if pressed else 0.0
            draw_icon(P, ish, icon, dx=off * 0.5, dy=off * 0.5, dim=92 if pressed else 44, clip=inner)
            P.fill(inner, (30, 27, 24, 84))      # mutes the colours
            if cd:
                cooldown_overlay(P, ish, cd[0], cd[1], self.f(36 if sh.x1 - sh.x0 > 90 else 28), clip=inner)
            self.inner_shadow(P, sh, w + 0.4, depth=5, alpha=70)
        elif text:
            self.text(P, (sh.cx, sh.cy), text, text_size, pressed=pressed)
        self.frame(P, sh, w, pressed)

    # ---- panel (frame of Knopfreihe / Tastenfeld, wheel)
    def panel(self, P, sh, pressed=False, shadow=True):
        m = sh.mask(P)
        if shadow:
            P.shadow(m, 3, 5, 60)
        b = sh.box
        P.fill_rgba(P.vgrad(b[1], b[3], (14, 14, 17, 142), (8, 8, 10, 152)), m)
        self.frame(P, sh, 1.6, pressed)

    def round_text(self, P, cx, cy, r, text, pressed=False, size=34):
        sh = Circ(cx, cy, r)
        m = sh.mask(P)
        P.shadow(m, 2, 3, 55)
        P.fill(m, (10, 10, 12, 140) if not pressed else (26, 23, 16, 168))
        self.ring(P, sh, 1.5, pressed)
        self.text(P, (cx, cy), text, size, pressed=pressed)

    def base(self, P, sh, pressed=False):
        P.fill(sh.mask(P), (8, 8, 10, 58))
        self.frame(P, sh, 1.3, False, alpha=172)

    def thumb(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.shadow(m, 2, 3, 55)
        P.fill(m, (20, 20, 22, 150) if not pressed else (36, 32, 22, 175))
        self.frame(P, sh, 1.5, pressed, alpha=190)

    def text_button(self, P, sh, text, pressed=False, size=28):
        m = sh.mask(P)
        P.shadow(m, 2, 3, 55)
        P.fill(m, (12, 13, 17, 165) if not pressed else (32, 28, 19, 190))
        self.frame(P, sh, 1.5, pressed)
        self.text(P, (sh.cx, sh.cy), text, size, pressed=pressed)

    def segment(self, P, sh, pressed=False):
        m = sh.mask(P)
        P.shadow(m, 1.5, 2.5, 50)
        P.fill(m, (10, 10, 12, 118) if not pressed else (36, 32, 20, 150))
        if pressed:
            P.fill(ring_of(P, sh, 1.7), HILITE + (240,))
        else:
            rim = ImageChops.subtract(sh.mask(P), sh.mask(P, 1.2))
            P.fill_rgba(P.dgrad((sh.mx - sh.thick, sh.my - sh.thick, sh.mx + sh.thick, sh.my + sh.thick),
                                EDGE_L + (172,), EDGE_D + (172,)), rim)

    def wheel(self, P, sh, vertical, phase, pressed=False):
        self.panel(P, sh, pressed)
        x0, y0, x1, y1 = sh.box
        g = P.blank()
        d = ImageDraw.Draw(g)
        sp = 22
        inner = sh.mask(P, 5.0)
        if vertical:
            y = y0 + phase + 6
            while y < y1:
                d.line([(P.lx(x0), P.ly(y)), (P.lx(x1), P.ly(y))], fill=255, width=int(1 * S))
                y += sp
        else:
            x = x0 + phase + 6
            while x < x1:
                d.line([(P.lx(x), P.ly(y0)), (P.lx(x), P.ly(y1))], fill=255, width=int(1 * S))
                x += sp
        P.fill(ImageChops.multiply(g, inner), (170, 156, 134, 36))

    def arrow(self, P, cx, cy, direction, size=9):
        s = size
        pts = {"u": [(cx - s, cy + s * 0.6), (cx + s, cy + s * 0.6), (cx, cy - s * 0.7)],
               "d": [(cx - s, cy - s * 0.6), (cx + s, cy - s * 0.6), (cx, cy + s * 0.7)],
               "l": [(cx + s * 0.6, cy - s), (cx + s * 0.6, cy + s), (cx - s * 0.7, cy)],
               "r": [(cx - s * 0.6, cy - s), (cx - s * 0.6, cy + s), (cx + s * 0.7, cy)]}[direction]
        m = P.blank()
        ImageDraw.Draw(m).polygon([(P.lx(x), P.ly(y)) for x, y in pts], fill=255)
        P.fill(m, TEXT_GOLD + (205,))


# --------------------------------------------------------------------------------------------- Aktuell (heutige App)
# Follows ControlElement.draw() and InputControlsView: only outlines, no fills (except the stick thumb, alpha 50, and a
# pressed ring segment, alpha 80); colour white with the overlay opacity, stroke = 0.25 * snappingSize (u = width / 100),
# system font, thumb radius 3.5 u, ring gap 0.75 u, ring width 2.5 u, outer gap 0.5 u, scroll wheel = capsule with
# grooves (spacing u, half alpha) and chevrons. The opacity 0.63 and the white were measured in a device screenshot of the app owner
# (line pixels about (170,172,171) over a (23,29,29) background); the app default would be 0.4.
APP_ALPHA = 160                       # 0.63 * 255
APP_WHITE = (255, 255, 255, APP_ALPHA)
APP_BLUE = (2, 119, 189, APP_ALPHA)   # InputControlsView.getSecondaryColor(): shown for a button that is held > 0.3 s


class Aktuell(Classic):
    sans = "segoeui.ttf"              # stand-in for the Android system font (Roboto)

    def __init__(self):
        super().__init__(True)
        self.key = "aktuell"
        self.title = "A  Aktuell (heutige App)"
        self.pressed_word = "gehalten (blau)"
        self.u = 19.0                 # snappingSize on the board
        self.stroke = 0.25 * self.u
        self.blurb = ["Nach ControlElement.draw() und Geräte-Screenshot: nur Umrisse (weiß, Deckkraft 0,63, Strich 0,25 Raster), Systemschrift, keine Füllung",
                      "außer Joystick-Daumen und gedrücktem Radialsegment. Kurzer Druck ändert nichts; nach 0,3 s Halten wird der Umriss blau."]

    # ---- helpers
    def outline(self, P, sh, color=APP_WHITE, w=None):
        """Stroke centred on the path, like Canvas.drawCircle/drawRoundRect/drawPath with Paint.Style.STROKE."""
        w = self.stroke if w is None else w
        P.fill(ImageChops.subtract(sh.mask(P, -w / 2), sh.mask(P, w / 2)), color)

    def atext(self, P, xy, s, maxw, cap, color=APP_WHITE):
        """Text sized like getTextSizeForWidth() capped at cap, centred, no shadow."""
        w48 = font(self.sans, 48).getlength(s) / S
        size = min(48 * maxw / max(1.0, w48), cap)
        P.text(xy, s, font(self.sans, size), color, shadow=None)

    def rrect(self, sh):
        return RRect(sh.x0, sh.y0, sh.x1, sh.y1, 0.75 * self.u)       # Shape.SQUARE

    # ---- buttons
    def round_text(self, P, cx, cy, r, text, pressed=False, size=34):
        self.outline(P, Circ(cx, cy, r), APP_BLUE if pressed else APP_WHITE)
        self.atext(P, (cx, cy), text, 2 * r - 2 * self.stroke, min(2 * self.u, r))

    def slot(self, P, sh, icon=None, pressed=False, cd=None, text=None, text_size=26, shadow=True):
        w = sh.x1 - sh.x0
        shape = Circ(sh.cx, sh.cy, w / 2) if shadow else self.rrect(sh)   # standalone action button = circle, grid cell = square
        clip = shape.mask(P, self.stroke * 0.5)
        if icon:
            draw_icon(P, shape, icon, dim=22, clip=clip)
            if cd:
                cooldown_overlay(P, shape, cd[0], cd[1], self.f(36 if w > 90 else 28), clip=clip)
        elif text:
            self.atext(P, (sh.cx, sh.cy), text, w - 2 * self.stroke, min(2 * self.u, w * 0.5))
        self.outline(P, shape, APP_BLUE if pressed else APP_WHITE)

    def text_button(self, P, sh, text, pressed=False, size=28):
        w, h = sh.x1 - sh.x0, sh.y1 - sh.y0
        self.outline(P, self.rrect(sh), APP_BLUE if pressed else APP_WHITE)
        self.atext(P, (sh.cx, sh.cy), text, w - 2 * self.stroke, min(2 * self.u, h * 0.5))

    def panel(self, P, sh, pressed=False, shadow=True):
        pass                          # the app has no common frame: buttons next to each other

    def arrow(self, P, cx, cy, direction, size=11):
        pass                          # chevrons are part of wheel()

    # ---- scroll wheel (capsule, grooves, chevrons)
    def wheel(self, P, sh, vertical, phase, pressed=False):
        x0, y0, x1, y1 = sh.box
        w, h = x1 - x0, y1 - y0
        cap = RRect(x0, y0, x1, y1, min(w, h) / 2)
        col = APP_BLUE if pressed else APP_WHITE
        self.outline(P, cap, col)
        sp = max(4.0, self.u)
        ph = phase % sp
        g = P.blank()
        d = ImageDraw.Draw(g)
        gw = int(max(1.0, self.stroke * 0.6) * S)
        if vertical:
            y = y0 + ph
            while y < y1:
                d.line([(P.lx(x0 + self.stroke), P.ly(y)), (P.lx(x1 - self.stroke), P.ly(y))], fill=255, width=gw)
                y += sp
        else:
            x = x0 + ph
            while x < x1:
                d.line([(P.lx(x), P.ly(y0 + self.stroke)), (P.lx(x), P.ly(y1 - self.stroke))], fill=255, width=gw)
                x += sp
        P.fill(ImageChops.multiply(g, cap.mask(P)), col[:3] + (col[3] // 2,))
        s = min(w, h) * 0.18
        inset = min(w, h) * 0.5
        c = P.blank()
        dc = ImageDraw.Draw(c)
        sw = int(self.stroke * S)
        if vertical:
            for top, sgn in ((y0 + inset, 1), (y1 - inset, -1)):
                pts = [(sh.cx - s, top + sgn * s * 0.5), (sh.cx, top - sgn * s * 0.5), (sh.cx + s, top + sgn * s * 0.5)]
                dc.line([(P.lx(x), P.ly(y)) for x, y in pts], fill=255, width=sw, joint="curve")
        else:
            for lft, sgn in ((x0 + inset, 1), (x1 - inset, -1)):
                pts = [(lft + sgn * s * 0.5, sh.cy - s), (lft - sgn * s * 0.5, sh.cy), (lft + sgn * s * 0.5, sh.cy + s)]
                dc.line([(P.lx(x), P.ly(y)) for x, y in pts], fill=255, width=sw, joint="curve")
        P.fill(c, col)

    # ---- joystick
    def joystick(self, canvas, cx, cy, R, pressed=False):
        P = Patch(cx - R, cy - R, cx + R, cy + R, pad=30)
        self.outline(P, Circ(cx, cy, R))
        tx, ty = (cx + R * 0.34, cy - R * 0.28) if pressed else (cx, cy)
        tr = 3.5 * self.u
        P.fill(Circ(tx, ty, tr).mask(P), (255, 255, 255, 50))
        self.outline(P, Circ(tx, ty, tr + self.stroke * 0.5))
        P.paste_on(canvas)

    # ---- radial: stick with centre text, ring of 8 segments, optional outer field (Tab)
    def radial(self, canvas, cx, cy, R, pressed_seg=1):
        u = self.u
        stick = 6 * u
        ri, ro = stick + 0.75 * u, stick + 0.75 * u + 2.5 * u
        oi, oo = ro + 0.5 * u, ro + 0.5 * u + 2.5 * u
        P = Patch(cx - oo, cy - oo, cx + oo, cy + oo, pad=30)
        self.outline(P, Circ(cx, cy, stick))
        tr = 3.5 * u
        P.fill(Circ(cx, cy, tr).mask(P), (255, 255, 255, 50))
        self.outline(P, Circ(cx, cy, tr + self.stroke * 0.5))
        th = tr * 1.6
        self.atext(P, (cx, cy), "Space", th * 1.3, th * 0.6)
        items = [("icon", "sword"), ("icon", "flame"), ("icon", "shield"), ("text", "F5"), ("icon", "bolt"), ("box", None), ("icon", "heart"), ("box", None)]
        step, sweep = 45.0, 41.0
        thick = ro - ri
        mid = (ri + ro) / 2
        for i in range(8):
            c = -90 + i * step
            sec = Sect(cx, cy, ri, ro, c - sweep / 2, c + sweep / 2, rnd=1)
            if i == pressed_seg:
                P.fill(sec.mask(P), (255, 255, 255, 80))
            self.outline(P, sec)
            kind, val = items[i]
            if kind == "icon":
                half = thick * 0.38
                draw_icon(P, Circ(sec.mx, sec.my, half), val, dim=22)
            elif kind == "text":
                self.atext(P, (sec.mx, sec.my), val, thick * 1.3, min(thick * 0.6, 2 * u))
            else:                       # unbound slot: the app draws the missing-glyph box
                side = thick * 0.55
                self.outline(P, RRect(sec.mx - side / 2, sec.my - side / 2, sec.mx + side / 2, sec.my + side / 2, side * 0.2), APP_WHITE, w=2.6)
        c = -90.0
        sec = Sect(cx, cy, oi, oo, c - sweep / 2, c + sweep / 2, rnd=1)
        self.outline(P, sec)
        self.atext(P, (sec.mx, sec.my), "Tab", (oo - oi) * 1.3, min((oo - oi) * 0.6, 2 * u))
        P.paste_on(canvas)


# --------------------------------------------------------------------------------------------- G: ruhig + Touch-Muster
# Built on F. Patterns taken from touch-control research (Xbox Touch Adaptation Kit, PPSSPP, CoD Mobile, Fortnite):
#  - rest: only symbol and edge visible (fill 28 %, edge 60 %); active / last used: fill 53 %
#  - pressed: symbol 25 % smaller, fill 80 %, edge briefly warm gold
#  - cooldown: dark clockwise sector, number in serif
#  - colours: stone fill #1C1A17, bronze edge #8C6B3E with light edge on top #C9A15A, text / symbol #E8DCC0,
#    edge 1.5 dp, 1 dp dark joint inside, soft drop shadow outside (contrast on bright ground), slots chamfered 9 %,
#    round only for the main button / centre button
#  - joystick as a soft surface: no ring, no fixed disc; rest = faint placeholder, touch = warm radial glow on a soft
#    dark underlay with a brighter core under the thumb, deflection = light sector at the rim
FILL_G, BRONZE_G, LIGHT_G = (28, 26, 23), (140, 107, 62), (201, 161, 90)
TXT_G, WARM_G = (232, 220, 192), (230, 184, 79)
DP = 1.3                          # board pixels per dp (guess: the board is a 1200 px wide drawing, a phone is about 900 dp wide)


def layer_like(P):
    Q = Patch.__new__(Patch)
    Q.ox, Q.oy, Q.w, Q.h = P.ox, P.oy, P.w, P.h
    Q.img = Image.new("RGBA", (P.w, P.h), (0, 0, 0, 0))
    return Q


def put_faded(P, Q, alpha):
    Q.img.putalpha(Q.img.getchannel("A").point(lambda v: int(v * alpha)))
    P.put(Q.img)


class Touch(Ruhig):
    def __init__(self):
        super().__init__()
        self.key = "g"
        self.title = "G  Classic ruhig + Touch-Muster"
        self.state = "ruhe"       # ruhe | aktiv (last used); the pressed state is passed per call
        self.blurb = ["Wie F plus Touch-Muster: Ruhe 28 % Füllung, 60 % Kante; aktiv 53 %; gedrückt 80 %, Symbol 25 % kleiner, Kante warmgold.",
                      "Farben Stein #1C1A17, Bronze #8C6B3E, Lichtkante #C9A15A, Schrift #E8DCC0. Joystick als weiche Fläche ohne Ring."]

    # ---- state to alpha
    def fa(self, pressed):
        return 204 if pressed else (136 if self.state == "aktiv" else 72)

    def ea(self, pressed):
        return 255 if pressed else (225 if self.state == "aktiv" else 153)

    # ---- helpers
    def drop(self, P, m, dy=2.0, blur=4.0, alpha=115):
        """Soft drop shadow outside the shape only (so the translucent fill stays clean)."""
        sm = ImageChops.offset(m, 0, int(dy * S)).filter(ImageFilter.GaussianBlur(blur * S))
        P.fill(ImageChops.multiply(sm, ImageChops.invert(m)), (0, 0, 0, alpha))

    def text(self, P, xy, s, size, color=TXT_G, pressed=False, outline=1.0):
        sz = size * (0.75 if pressed else 1.0) * 0.94
        P.text(xy, s, self.f(sz), tuple(color) + (255,), shadow=(0, 1.5, 3, 210), outline=outline)

    def frame(self, P, sh, w=None, pressed=False, alpha=None):
        ew = 1.5 * DP
        a = self.ea(pressed) if alpha is None else alpha
        b = sh.box
        if pressed:
            P.fill(ring_of(P, sh, ew + 0.4), WARM_G + (255,))
        else:
            rim = ImageChops.subtract(sh.mask(P), sh.mask(P, ew))
            P.fill_rgba(P.vgrad(b[1], b[1] + (b[3] - b[1]) * 0.45, LIGHT_G + (a,), BRONZE_G + (a,)), rim)
        P.fill(ImageChops.subtract(sh.mask(P, ew), sh.mask(P, ew + 1.0 * DP)), (0, 0, 0, 120))

    # ---- action slot (chamfered 9 %)
    def slot(self, P, sh, icon=None, pressed=False, cd=None, text=None, text_size=26, shadow=True):
        w = sh.x1 - sh.x0
        sh = Cham(sh.x0, sh.y0, sh.x1, sh.y1, w * 0.09)
        m = sh.mask(P)
        if shadow:
            self.drop(P, m)
        P.fill(m, FILL_G + (self.fa(pressed),))
        ew = 1.5 * DP + 0.4
        inner = sh.mask(P, ew)
        ish = sh.inner(ew)
        if icon:
            Q = layer_like(P)
            draw_icon(Q, ish, icon, k=0.75 if pressed else 1.0, dim=40)
            Q.fill(ish.mask(Q, 0, 0.75 if pressed else 1.0), (30, 27, 22, 50))
            put_faded(P, Q, 1.0 if (pressed or self.state == "aktiv") else 0.82)
            if cd:
                cooldown_overlay(P, ish, cd[0], cd[1], self.f(34 if w > 90 else 26), clip=inner)
        elif text:
            self.text(P, (sh.cx, sh.cy), text, text_size, pressed=pressed)
        self.frame(P, sh, pressed=pressed)

    def panel(self, P, sh, pressed=False, shadow=True):
        sh = Cham(sh.x0, sh.y0, sh.x1, sh.y1, min(sh.c, 0.1 * min(sh.x1 - sh.x0, sh.y1 - sh.y0)))
        m = sh.mask(P)
        if shadow:
            self.drop(P, m, 3, 5, 120)
        P.fill(m, FILL_G + (self.fa(False),))
        self.frame(P, sh, pressed=pressed)

    def round_text(self, P, cx, cy, r, text, pressed=False, size=34):
        sh = Circ(cx, cy, r)
        m = sh.mask(P)
        self.drop(P, m)
        P.fill(m, FILL_G + (self.fa(pressed),))
        self.frame(P, sh, pressed=pressed)
        self.text(P, (cx, cy), text, size, pressed=pressed)

    def text_button(self, P, sh, text, pressed=False, size=28):
        sh = Cham(sh.x0, sh.y0, sh.x1, sh.y1, 0.1 * min(sh.x1 - sh.x0, sh.y1 - sh.y0))
        m = sh.mask(P)
        self.drop(P, m)
        P.fill(m, FILL_G + (self.fa(pressed),))
        self.frame(P, sh, pressed=pressed)
        self.text(P, (sh.cx, sh.cy), text, size, pressed=pressed)

    # ---- radial parts (used by the generic c_radial)
    def base(self, P, sh, pressed=False):
        P.fill(sh.mask(P), FILL_G + (36,))
        self.frame(P, sh, alpha=120 if self.state == "ruhe" else 170)

    def thumb(self, P, sh, pressed=False):
        m = sh.mask(P)
        self.drop(P, m)
        P.fill(m, FILL_G + (self.fa(False),))
        self.frame(P, sh)

    def segment(self, P, sh, pressed=False):
        m = sh.mask(P)
        self.drop(P, m, 1.5, 3, 100)
        P.fill(m, FILL_G + (self.fa(pressed),))
        if pressed:
            P.fill(ring_of(P, sh, 1.5 * DP + 0.4), WARM_G + (255,))
        else:
            a = self.ea(False)
            rim = ImageChops.subtract(sh.mask(P), sh.mask(P, 1.5 * DP))
            b = sh.box
            P.fill_rgba(P.vgrad(b[1], b[1] + (b[3] - b[1]) * 0.45, LIGHT_G + (a,), BRONZE_G + (a,)), rim)

    # ---- wheel
    def wheel(self, P, sh, vertical, phase, pressed=False):
        self.panel(P, sh, pressed)
        x0, y0, x1, y1 = sh.box
        g = P.blank()
        d = ImageDraw.Draw(g)
        sp = 22
        inner = sh.mask(P, 5.0)
        if vertical:
            y = y0 + phase + 6
            while y < y1:
                d.line([(P.lx(x0), P.ly(y)), (P.lx(x1), P.ly(y))], fill=255, width=int(1 * S))
                y += sp
        else:
            x = x0 + phase + 6
            while x < x1:
                d.line([(P.lx(x), P.ly(y0)), (P.lx(x), P.ly(y1))], fill=255, width=int(1 * S))
                x += sp
        P.fill(ImageChops.multiply(g, inner), TXT_G + (38,))

    def arrow(self, P, cx, cy, direction, size=9):
        s = size
        pts = {"u": [(cx - s, cy + s * 0.6), (cx + s, cy + s * 0.6), (cx, cy - s * 0.7)],
               "d": [(cx - s, cy - s * 0.6), (cx + s, cy - s * 0.6), (cx, cy + s * 0.7)],
               "l": [(cx + s * 0.6, cy - s), (cx + s * 0.6, cy + s), (cx - s * 0.7, cy)],
               "r": [(cx - s * 0.6, cy - s), (cx - s * 0.6, cy + s), (cx + s * 0.7, cy)]}[direction]
        m = P.blank()
        ImageDraw.Draw(m).polygon([(P.lx(x), P.ly(y)) for x, y in pts], fill=255)
        P.fill(ImageChops.offset(m, 0, int(1.0 * S)), (0, 0, 0, 120))
        P.fill(m, TXT_G + (215,))

    # ---- joystick as a soft surface: mode "ruhe" | "mitte" | "aus"
    def soft_joystick(self, canvas, cx, cy, mode):
        Rf, Ru = 80 * DP, 150.0            # glow radius about 80 dp, dark underlay larger
        P = Patch(cx - Ru - 6, cy - Ru - 6, cx + Ru + 6, cy + Ru + 6, pad=0)
        xs = ((np.arange(P.w) + P.ox) / S - cx)[None, :]
        ys = ((np.arange(P.h) + P.oy) / S - cy)[:, None]
        r = np.hypot(xs, ys)

        def sm(a, b, x):
            t = np.clip((x - a) / (b - a), 0, 1)
            return t * t * (3 - 2 * t)

        def lay(col, alpha):
            arr = np.zeros((P.h, P.w, 4), np.uint8)
            arr[..., :3] = col
            arr[..., 3] = np.clip(alpha * 255, 0, 255).astype(np.uint8)
            P.put(Image.fromarray(arr, "RGBA"))

        if mode == "ruhe":                 # faint placeholder symbol, about 20 % opacity
            m = P.blank()
            d = ImageDraw.Draw(m)
            d.ellipse([P.lx(cx - 5), P.ly(cy - 5), P.lx(cx + 5), P.ly(cy + 5)], fill=255)
            for deg in (0, 90, 180, 270):
                a = math.radians(deg)
                ux, uy, px, py = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
                tip = (cx + ux * 44, cy + uy * 44)
                b1 = (cx + ux * 28 + px * 11, cy + uy * 28 + py * 11)
                b2 = (cx + ux * 28 - px * 11, cy + uy * 28 - py * 11)
                d.polygon([(P.lx(x), P.ly(y)) for x, y in (tip, b1, b2)], fill=255)
            P.shadow(m, 1.5, 4, 70)                                         # soft shadow so it stays findable on bright ground
            P.fill(m, TXT_G + (51,))
            P.paste_on(canvas)
            return
        ang = math.radians(-38)
        defl = 0.0 if mode == "mitte" else 0.5
        tx, ty = defl * Rf * math.cos(ang), defl * Rf * math.sin(ang)
        lay((0, 0, 0), 0.23 * (1 - sm(0.15, 1.0, r / Ru)))                 # very soft dark underlay
        lay(WARM_G, 0.35 * (1 - sm(0.0, 1.0, r / Rf)) ** 0.75)                     # warm glow, soft to 0
        if defl > 0:                                                       # light sector, about 50 degrees wide
            phi = np.arctan2(ys, xs)
            delta = np.abs((phi - ang + math.pi) % (2 * math.pi) - math.pi)
            a_ang = 1 - sm(0, math.radians(25), delta)
            a_rad = sm(0.62, 0.95, r / Rf) * (1 - sm(1.0, 1.3, r / Rf))
            lay((255, 230, 160), (0.35 + 0.5 * defl) * a_ang * a_rad)
        rr = np.hypot(xs - tx, ys - ty)
        lay((255, 238, 200), 0.55 * (1 - sm(0.45, 1.0, rr / 26.0)))        # brighter core under the thumb, radius 20 dp
        P.paste_on(canvas)


def c_round_text(st, canvas, cx, cy, r, text, pressed=False):
    P = Patch(cx - r, cy - r, cx + r, cy + r, pad=30)
    st.round_text(P, cx, cy, r, text, pressed)
    P.paste_on(canvas)


def c_slot(st, canvas, cx, cy, size, icon, pressed=False, cd=None):
    h = size / 2
    P = Patch(cx - h, cy - h, cx + h, cy + h, pad=30)
    st.slot(P, Cham(cx - h, cy - h, cx + h, cy + h, size * 0.14), icon, pressed, cd)
    P.paste_on(canvas)


def c_text_button(st, canvas, cx, cy, w, h, text, pressed=False):
    P = Patch(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, pad=30)
    st.text_button(P, Cham(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 9), text, pressed)
    P.paste_on(canvas)


def c_radial(st, canvas, cx, cy, R, pressed_seg=1):
    if hasattr(st, "radial"):
        return st.radial(canvas, cx, cy, R, pressed_seg)
    ro, ri = R, R * 0.745
    stick_r, thumb_r = R * 0.62, R * 0.32
    P = Patch(cx - R, cy - R, cx + R, cy + R, pad=30)
    st.base(P, Circ(cx, cy, stick_r))
    items = [("icon", "sword"), ("icon", "flame"), ("icon", "shield"), ("text", "Tab"), ("icon", "bolt"), None, ("icon", "heart"), ("text", "F5")]
    step = 360 / 8
    for i in range(8):
        c = -90 + i * step
        sec = Sect(cx, cy, ri, ro, c - (step - 4) / 2, c + (step - 4) / 2, rnd=5)
        pr = i == pressed_seg
        st.segment(P, sec, pr)
        it = items[i]
        if it is None:
            continue
        sz = sec.thick * 0.66
        sh = Cham(sec.mx - sz / 2, sec.my - sz / 2, sec.mx + sz / 2, sec.my + sz / 2, sz * 0.14)
        if it[0] == "icon":
            st.slot(P, sh, it[1], pr, shadow=False)
        else:
            st.text(P, (sec.mx, sec.my), it[1], 22, pressed=pr)
    st.thumb(P, Circ(cx, cy, thumb_r))
    st.text(P, (cx, cy), "Space", 34)
    P.paste_on(canvas)


def c_joystick(st, canvas, cx, cy, R, pressed=False):
    if hasattr(st, "joystick"):
        return st.joystick(canvas, cx, cy, R, pressed)
    P = Patch(cx - R, cy - R, cx + R, cy + R, pad=30)
    st.base(P, Circ(cx, cy, R))
    tx, ty = (cx + R * 0.34, cy - R * 0.28) if pressed else (cx, cy)
    st.thumb(P, Circ(tx, ty, R * 0.42), pressed)
    P.paste_on(canvas)


def c_wheel(st, canvas, cx, cy, vertical, pressed=False, phase=0.0):
    w, h = (84, 236) if vertical else (236, 84)
    P = Patch(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, pad=30)
    sh = Cham(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 16)
    st.wheel(P, sh, vertical, phase, pressed)
    if vertical:
        st.arrow(P, cx, cy - h / 2 + 24, "u")
        st.arrow(P, cx, cy + h / 2 - 24, "d")
    else:
        st.arrow(P, cx - w / 2 + 24, cy, "l")
        st.arrow(P, cx + w / 2 - 24, cy, "r")
    P.paste_on(canvas)


def c_grid(st, canvas, x0, y0, rows, cols, cell, cells, pad=10, gap=6, pressed=None, cd=None):
    w = cols * cell + (cols - 1) * gap + 2 * pad
    h = rows * cell + (rows - 1) * gap + 2 * pad
    P = Patch(x0, y0, x0 + w, y0 + h, pad=30)
    st.panel(P, Cham(x0, y0, x0 + w, y0 + h, 14))
    for r in range(rows):
        for c in range(cols):
            cx0 = x0 + pad + c * (cell + gap)
            cy0 = y0 + pad + r * (cell + gap)
            sh = Cham(cx0, cy0, cx0 + cell, cy0 + cell, cell * 0.14)
            kind, val = cells[r][c]
            pr = pressed == (r, c)
            cdv = (cd[1], cd[2]) if cd and cd[0] == (r, c) else None
            if kind == "icon":
                st.slot(P, sh, val, pr, cdv, shadow=False)
            else:
                st.slot(P, sh, None, pr, None, text=val, text_size=28, shadow=False)
    P.paste_on(canvas)
    return w, h


def draw_classic_panel(st, bg):
    global PRESSED_WORD
    PRESSED_WORD = getattr(st, "pressed_word", "gedrückt")
    canvas = bg.copy()
    header_scrim(canvas)
    P = Patch(0, 0, PW, 140, pad=0)
    P.text((38, 44), st.title, font("palab.ttf", 46), GOLD + (255,), shadow=(0, 2, 0, 200), anchor="lm", outline=1.6)
    for i, line in enumerate(st.blurb):
        P.text((40, 92 + i * 26), line, font("segoeui.ttf", 19), (226, 232, 240, 255), shadow=(0, 1, 2, 220), anchor="lm")
    P.paste_on(canvas)

    caption(canvas, (38, 158), "RUNDKNOPF, AKTIONSKNOPF, TEXTKNOPF", 15, "lm")
    y = 252
    c_round_text(st, canvas, 90, y, 56, "Space", False)
    c_round_text(st, canvas, 232, y, 56, "Space", True)
    c_slot(st, canvas, 410, y, 104, "sword", False)
    c_slot(st, canvas, 540, y, 104, "flame", True)
    c_slot(st, canvas, 670, y, 104, "shield", False, cd=(0.62, "14"))
    c_text_button(st, canvas, 960, y - 38, 200, 64, "TAB", False)
    c_text_button(st, canvas, 960, y + 44, 200, 64, "TAB", True)
    for x, s in [(90, "Rund, Text"), (232, "gedrückt"), (410, "Aktionsknopf"), (540, "gedrückt"), (670, "Abklingzeit"), (960, "Textknopf")]:
        caption(canvas, (x, 354), s)

    caption(canvas, (38, 396), "RADIAL UND JOYSTICK", 15, "lm")
    c_radial(st, canvas, 262, 650, 222, pressed_seg=1)
    c_joystick(st, canvas, 700, 650, 130, False)
    c_joystick(st, canvas, 990, 650, 130, True)
    caption(canvas, (262, 900), "Radial, Segment 2 gedrückt")
    caption(canvas, (700, 820), "Joystick")
    caption(canvas, (990, 820), "Joystick gedrückt")

    caption(canvas, (38, 946), "MAUSRAD UND KNOPFREIHE", 15, "lm")
    c_wheel(st, canvas, 90, 1090, True, False, 4)
    c_wheel(st, canvas, 210, 1090, True, True, 11)
    c_wheel(st, canvas, 420, 1040, False, False, 6)
    c_wheel(st, canvas, 420, 1145, False, True, 13)
    caption(canvas, (150, 1232), "Mausrad normal / gedrückt")
    row = [[("text", "Q"), ("text", "W"), ("text", "E"), ("text", "R"), ("text", "F")]]
    w, h = c_grid(st, canvas, 600, 1020, 1, 5, 96, row, pressed=(0, 2))
    caption(canvas, (600 + w / 2, 1020 + h + 28), "Knopfreihe 1x5, frei belegbar (E gedrückt)")
    row2 = [[("icon", "sword"), ("icon", "flame"), ("icon", "shield"), ("icon", "bolt"), ("icon", "heart")]]
    c_grid(st, canvas, 600, 1190, 1, 5, 96, row2, pressed=None, cd=((0, 3), 0.55, "9"))

    caption(canvas, (38, 1306), "TASTENFELD (NEU): ZEILEN x SPALTEN, JEDE ZELLE FREI BELEGBAR", 15, "lm")
    cells = [[("icon", "sword"), ("icon", "flame"), ("text", "Tab"), ("icon", "shield")],
             [("icon", "bolt"), ("text", "F5"), ("icon", "heart"), ("icon", "star")]]
    w, h = c_grid(st, canvas, 60, 1350, 2, 4, 118, cells, pressed=(1, 0), cd=((0, 1), 0.7, "21"))
    caption(canvas, (60 + w / 2, 1350 + h + 30), "Tastenfeld 2x4: gemischt, Zelle (2,1) gedrückt")
    cells3 = [[("text", "1"), ("text", "2"), ("text", "3")],
              [("text", "4"), ("text", "5"), ("text", "6")],
              [("text", "7"), ("text", "8"), ("text", "9")]]
    w2, h2 = c_grid(st, canvas, 760, 1350, 3, 3, 90, cells3, pressed=(1, 1))
    caption(canvas, (760 + w2 / 2, 1350 + h2 + 30), "Tastenfeld 3x3, Zelle 5 gedrückt")
    return canvas


# --------------------------------------------------------------------------------------------- board
LIGHT = False               # drawing on the bright ground: dark captions, solid header band


def header_scrim(canvas):
    P = Patch(0, 0, PW, 150, pad=0)
    if LIGHT:
        P.fill_rgba(P.vgrad(124, 150, (20, 24, 30, 238), (20, 24, 30, 0)), Image.new("L", (P.w, P.h), 255))
    else:
        P.fill_rgba(P.vgrad(0, 150, (0, 0, 0, 150), (0, 0, 0, 0)), Image.new("L", (P.w, P.h), 255))
    P.paste_on(canvas)


PRESSED_WORD = "gedrückt"   # wording of the pressed state in the captions (the current app shows a held button in blue)


def caption(canvas, xy, s, size=17, anchor="mm"):
    if not s.startswith(("Joystick", "Radial")):
        s = s.replace("gedrückt", PRESSED_WORD)
    half = 400 if anchor == "mm" else 0
    P = Patch(xy[0] - half - 10, xy[1] - 20, xy[0] + (half if anchor == "mm" else 900), xy[1] + 20, pad=4)
    if LIGHT:
        P.text(xy, s, font("segoeui.ttf", size), (22, 26, 34, 255), shadow=(0, 1, 2, 120), anchor=anchor)
    else:
        P.text(xy, s, font("segoeui.ttf", size), (214, 220, 230, 255), shadow=(0, 1, 2, 220), anchor=anchor)
    P.paste_on(canvas)


def load_bg(path):
    im = Image.open(path).convert("RGB")
    # dark ground and trees between joystick and radial of the screenshot, free of HUD
    w = int(PW / PH * 1095)
    box = (900, 120, 900 + w, 1215)
    im = im.crop(box).resize((PW * S, PH * S), Image.LANCZOS).filter(ImageFilter.GaussianBlur(1.4 * S))
    im = Image.eval(im, lambda v: int(v * 0.92))
    return im.convert("RGBA")


def load_bg_light(path):
    """Bright ground (snow): blurred crop of the minimap of the scene over a light blue-white gradient."""
    im = Image.open(path).convert("RGB")
    snow = im.crop((2010, 250, 2370, 470)).resize((PW * S, PH * S), Image.LANCZOS).filter(ImageFilter.GaussianBlur(14 * S))
    grad = np.linspace(0, 1, PH * S)[:, None, None]
    top, bot = np.array([238, 243, 250], float), np.array([206, 218, 232], float)
    base = Image.fromarray(np.broadcast_to(top * (1 - grad) + bot * grad, (PH * S, PW * S, 3)).astype(np.uint8), "RGB")
    return Image.blend(base, snow, 0.35).convert("RGBA")


def draw_g_panel(st, bg):
    global PRESSED_WORD
    PRESSED_WORD = "gedrückt"
    canvas = bg.copy()
    header_scrim(canvas)
    P = Patch(0, 0, PW, 140, pad=0)
    P.text((38, 44), st.title, font("palab.ttf", 46), GOLD + (255,), shadow=(0, 2, 0, 200), anchor="lm", outline=1.6)
    for i, line in enumerate(st.blurb):
        P.text((40, 92 + i * 26), line, font("segoeui.ttf", 19), (226, 232, 240, 255), shadow=(0, 1, 2, 220), anchor="lm")
    P.paste_on(canvas)

    caption(canvas, (38, 158), "HAUPTKNOPF (RUND) UND TEXTKNOPF: RUHE, AKTIV, GEDRÜCKT", 15, "lm")
    for x, state, pr, lab in [(80, "ruhe", False, "Ruhe"), (200, "aktiv", False, "aktiv"), (320, "ruhe", True, "gedrückt")]:
        st.state = state
        c_round_text(st, canvas, x, 250, 52, "Space", pr)
        caption(canvas, (x, 322), lab)
    for x, state, pr, lab in [(560, "ruhe", False, "Ruhe"), (730, "aktiv", False, "aktiv"), (900, "ruhe", True, "gedrückt")]:
        st.state = state
        c_text_button(st, canvas, x, 250, 150, 58, "TAB", pr)
        caption(canvas, (x, 322), lab)

    caption(canvas, (38, 372), "AKTIONSKNOPF MIT SYMBOL UND ABKLINGZEIT, MAUSRAD", 15, "lm")
    for x, state, pr, cd, lab in [(70, "ruhe", False, None, "Ruhe"), (180, "aktiv", False, None, "aktiv"),
                                  (290, "ruhe", True, None, "gedrückt"), (400, "ruhe", False, (0.62, "14"), "Abklingzeit")]:
        st.state = state
        c_slot(st, canvas, x, 470, 92, "sword" if x < 400 else "shield", pr, cd)
        caption(canvas, (x, 540), lab)
    for x, state, pr in [(600, "ruhe", False), (700, "aktiv", False), (800, "ruhe", True)]:
        st.state = state
        c_wheel(st, canvas, x, 500, True, pr, 4 + x % 9)
    for y, state, pr in [(445, "ruhe", False), (545, "aktiv", True)]:
        st.state = state
        c_wheel(st, canvas, 1000, y, False, pr, 6)
    caption(canvas, (700, 640), "Mausrad: Ruhe / aktiv / gedrückt")
    caption(canvas, (1000, 640), "Ruhe (oben), gedrückt (unten)")

    caption(canvas, (38, 676), "JOYSTICK ALS WEICHE FLÄCHE: KEIN RING, KEINE FESTE SCHEIBE", 15, "lm")
    for x, mode, lab in [(200, "ruhe", "Ruhe: Platzhalter (20 %)"), (600, "mitte", "Daumen mittig"), (1000, "aus", "Daumen ausgelenkt")]:
        st.soft_joystick(canvas, x, 850, mode)
        caption(canvas, (x, 1010), lab)

    caption(canvas, (38, 1036), "RADIAL: RUHE UND AKTIV (SEGMENT 2 GEDRÜCKT)", 15, "lm")
    st.state = "ruhe"
    c_radial(st, canvas, 290, 1255, 190, pressed_seg=-1)
    st.state = "aktiv"
    c_radial(st, canvas, 880, 1255, 190, pressed_seg=1)
    caption(canvas, (290, 1470), "Radial Ruhe")
    caption(canvas, (880, 1470), "Radial aktiv, Segment 2 gedrückt")

    caption(canvas, (38, 1508), "KNOPFREIHE, FREI BELEGBAR: RUHE UND AKTIV (E GEDRÜCKT)", 15, "lm")
    row = [[("icon", "sword"), ("text", "W"), ("text", "E"), ("icon", "bolt"), ("text", "F")]]
    st.state = "ruhe"
    w, h = c_grid(st, canvas, 40, 1540, 1, 5, 76, row)
    caption(canvas, (40 + w / 2, 1540 + h + 26), "Ruhe")
    st.state = "aktiv"
    c_grid(st, canvas, 600, 1540, 1, 5, 76, row, pressed=(0, 2))
    caption(canvas, (600 + w / 2, 1540 + h + 26), "aktiv, E gedrückt")
    st.state = "ruhe"
    return canvas


def draw_panel(st, bg):
    canvas = bg.copy()
    P = Patch(0, 0, PW, 150, pad=0)
    P.fill_rgba(P.vgrad(0, 150, (0, 0, 0, 150), (0, 0, 0, 0)), Image.new("L", (P.w, P.h), 255))
    P.paste_on(canvas)
    P = Patch(0, 0, PW, 140, pad=0)
    P.text((38, 44), st.title, font("segoeuib.ttf", 46), (255, 255, 255, 255), shadow=(0, 2, 4, 220), anchor="lm")
    for i, line in enumerate(st.blurb):
        P.text((40, 92 + i * 26), line, font("segoeui.ttf", 20), (226, 232, 240, 255), shadow=(0, 1, 2, 220), anchor="lm")
    P.paste_on(canvas)

    # A: round buttons
    caption(canvas, (38, 158), "RUNDKNOPF", 15, "lm")
    y = 252
    round_button_text(st, canvas, 110, y, 68, "Space", False)
    round_button_text(st, canvas, 270, y, 68, "Space", True)
    round_button_icon(st, canvas, 450, y, 68, "sword", False)
    round_button_icon(st, canvas, 610, y, 68, "flame", True)
    round_button_icon(st, canvas, 770, y, 68, "shield", False, cd=(0.62, "14"))
    pill_button(st, canvas, 1010, y - 38, 200, 70, "TAB", False)
    pill_button(st, canvas, 1010, y + 48, 200, 70, "TAB", True)
    for x, s in [(110, "Text"), (270, "Text gedrückt"), (450, "Symbol"), (610, "Symbol gedrückt"), (770, "Symbol, Abklingzeit"), (1010, "Pille / Leiste")]:
        caption(canvas, (x, 354), s)

    # B: radial and joysticks
    caption(canvas, (38, 396), "RADIAL UND JOYSTICK", 15, "lm")
    radial(st, canvas, 262, 650, 222, pressed_seg=1)
    joystick(st, canvas, 700, 650, 130, False)
    joystick(st, canvas, 990, 650, 130, True)
    caption(canvas, (262, 900), "Radial, Segment 2 gedrückt")
    caption(canvas, (700, 820), "Joystick")
    caption(canvas, (990, 820), "Joystick gedrückt")

    # C: wheels + button row
    caption(canvas, (38, 946), "MAUSRAD UND KNOPFREIHE", 15, "lm")
    mouse_wheel(st, canvas, 90, 1090, True, False, 4)
    mouse_wheel(st, canvas, 210, 1090, True, True, 11)
    mouse_wheel(st, canvas, 420, 1040, False, False, 6)
    mouse_wheel(st, canvas, 420, 1145, False, True, 13)
    caption(canvas, (150, 1232), "Mausrad normal / gedrückt")
    row = [[("text", "Q"), ("text", "W"), ("text", "E"), ("text", "R"), ("text", "F")]]
    w, h = grid(st, canvas, 600, 1020, 1, 5, 96, row, pressed=(0, 2))
    caption(canvas, (600 + w / 2, 1020 + h + 28), "Knopfreihe 1x5, frei belegbar (E gedrückt)")
    row2 = [[("icon", "sword"), ("icon", "flame"), ("icon", "shield"), ("icon", "bolt"), ("icon", "heart")]]
    grid(st, canvas, 600, 1190, 1, 5, 96, row2, pressed=None, cd=((0, 3), 0.55, "9"))

    # D: grid
    caption(canvas, (38, 1306), "TASTENFELD (NEU): ZEILEN x SPALTEN, JEDE ZELLE FREI BELEGBAR", 15, "lm")
    cells = [[("icon", "sword"), ("icon", "flame"), ("text", "Tab"), ("icon", "shield")],
             [("icon", "bolt"), ("text", "F5"), ("icon", "heart"), ("icon", "star")]]
    w, h = grid(st, canvas, 60, 1350, 2, 4, 118, cells, pressed=(1, 0), cd=((0, 1), 0.7, "21"))
    caption(canvas, (60 + w / 2, 1350 + h + 30), "Tastenfeld 2x4: gemischt, Zelle (2,1) gedrückt")
    cells3 = [[("text", "1"), ("text", "2"), ("text", "3")],
              [("text", "4"), ("text", "5"), ("text", "6")],
              [("text", "7"), ("text", "8"), ("text", "9")]]
    w2, h2 = grid(st, canvas, 760, 1350, 3, 3, 96, cells3, pressed=(1, 1))
    caption(canvas, (760 + w2 / 2, 1350 + h2 + 30), "Tastenfeld 3x3, Zelle 5 gedrückt")
    return canvas


STYLES = {"glas": Glas, "azeroth": Azeroth, "minimal": Minimal}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bg", default=DEFAULT_BG, required=DEFAULT_BG is None, help="background screenshot (a game picture of your own)")
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--only", choices=list(STYLES), default=None)
    ap.add_argument("--tafel", choices=["erste", "classic", "ruhig", "aktuell", "g", "vergleich"], default="erste",
                    help="erste = Glas/Azeroth/Minimal+ (Phase 1), classic = Classic hell/dunkel (D, E), "
                         "ruhig = Classic ruhig (F) einzeln, aktuell = heutiges Aussehen der App, g = Entwurf G (ruhig + Touch-Muster), vergleich = Aktuell | F | G dunkel, darunter hell")
    ap.add_argument("--scale", type=float, default=1.0, help="Factor for the final PNG (1.0 = 1200 px per panel)")
    args = ap.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    bg = load_bg(args.bg)
    if args.tafel in ("g", "vergleich"):
        global LIGHT
        bg_light = load_bg_light(args.bg)
        styles = [Touch()] if args.tafel == "g" else [Aktuell(), Ruhig(), Touch()]
        rows = []
        for light in (False, True):
            LIGHT = light
            row = []
            for st in styles:
                draw = draw_g_panel if isinstance(st, Touch) else draw_classic_panel
                pic = draw(st, bg_light if light else bg).convert("RGB").resize((int(PW * args.scale), int(PH * args.scale)), Image.LANCZOS)
                pic.save(out / f"tastenoptik_{st.key}{'_hell' if light else ''}.png")
                row.append(pic)
                print("ok", st.key, "hell" if light else "dunkel")
            rows.append(row)
        LIGHT = False
        if args.tafel == "vergleich":
            gap = 24
            pw, ph = rows[0][0].size
            board = Image.new("RGB", (len(styles) * pw + gap * (len(styles) + 1), 2 * ph + 3 * gap), (22, 24, 28))
            for ri, row in enumerate(rows):
                for ci, pic in enumerate(row):
                    board.paste(pic, (gap + ci * (pw + gap), gap + ri * (ph + gap)))
            board.save(out / "tastenoptik_vergleich.png")
            print("ok", out / "tastenoptik_vergleich.png", board.size)
        return
    if args.tafel in ("classic", "ruhig", "aktuell", "vergleich"):
        styles = {"classic": [Classic(False), Classic(True)], "ruhig": [Ruhig()], "aktuell": [Aktuell()],
                  "vergleich": [Aktuell(), Classic(False), Classic(True), Ruhig()]}[args.tafel]
        panels = []
        for st in styles:
            pic = draw_classic_panel(st, bg).convert("RGB").resize((int(PW * args.scale), int(PH * args.scale)), Image.LANCZOS)
            pic.save(out / f"tastenoptik_{st.key}.png")
            panels.append(pic)
            print("ok", st.key)
        if len(panels) > 1:
            gap = 24
            board = Image.new("RGB", (sum(p.width for p in panels) + gap * (len(panels) + 1), panels[0].height + 2 * gap), (22, 24, 28))
            x = gap
            for pic in panels:
                board.paste(pic, (x, gap))
                x += pic.width + gap
            name = "tastenoptik_classic.png" if args.tafel == "classic" else "tastenoptik_vergleich.png"
            board.save(out / name)
            print("ok", out / name, board.size)
        return
    panels = []
    for key, cls in STYLES.items():
        if args.only and key != args.only:
            continue
        pic = draw_panel(cls(), bg).convert("RGB").resize((int(PW * args.scale), int(PH * args.scale)), Image.LANCZOS)
        pic.save(out / f"tastenoptik_{key}.png")
        panels.append(pic)
        print("ok", key)
    if len(panels) > 1:
        gap = 24
        W = sum(p.width for p in panels) + gap * (len(panels) + 1)
        board = Image.new("RGB", (W, panels[0].height + 2 * gap), (22, 24, 28))
        x = gap
        for p in panels:
            board.paste(p, (x, gap))
            x += p.width + gap
        board.save(out / "tastenoptik_vorschau.png")
        print("ok", out / "tastenoptik_vorschau.png", board.size)


if __name__ == "__main__":
    main()
