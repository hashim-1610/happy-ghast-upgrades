#!/usr/bin/env python3
"""Regenerates the README images in docs/images/.

Everything is drawn from scratch with Pillow: no Minecraft textures or screenshots are used. The pictures are
labelled mock-ups (the item tiles are simple shapes with a caption), not in-game footage.

Needs: pip install pillow
Usage: python3 tools/render_images.py
"""
import math, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "images")
os.makedirs(OUT, exist_ok=True)

FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_R = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
def font(size, bold=False):
    try: return ImageFont.truetype(FONT_B if bold else FONT_R, size)
    except Exception: return ImageFont.load_default()

TAU = 2 * math.pi
AQUA = (110, 230, 240)
SPEED = (255, 200, 70)
HEALTH = (255, 105, 130)
INK = (190, 200, 215)


# ------------------------------------------------------------------ shared drawing helpers
def glow(size, draw_fn, blur):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    return layer.filter(ImageFilter.GaussianBlur(blur))

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)

def iso_cube(d, cx, cy, s, base, edge=None, alpha=255):
    """Flat-shaded isometric cube centred on (cx, cy); s is the edge length in pixels."""
    w = s * 0.866
    top = [(cx, cy - s), (cx + w, cy - s / 2), (cx, cy), (cx - w, cy - s / 2)]
    left = [(cx - w, cy - s / 2), (cx, cy), (cx, cy + s), (cx - w, cy + s / 2)]
    right = [(cx + w, cy - s / 2), (cx, cy), (cx, cy + s), (cx + w, cy + s / 2)]
    e = (edge or shade(base, 0.45)) + (alpha,)
    d.polygon(left, fill=shade(base, 0.72) + (alpha,), outline=e)
    d.polygon(right, fill=shade(base, 0.52) + (alpha,), outline=e)
    d.polygon(top, fill=shade(base, 1.0) + (alpha,), outline=e)
    return top, left, right

def heart(d, cx, cy, r, fill):
    d.ellipse([cx - r, cy - r, cx, cy], fill=fill)
    d.ellipse([cx, cy - r, cx + r, cy], fill=fill)
    d.polygon([(cx - r, cy - r * 0.35), (cx + r, cy - r * 0.35), (cx, cy + r * 1.05)], fill=fill)

def chevron(d, cx, cy, r, fill):
    d.polygon([(cx, cy - r), (cx + r, cy + r * 0.2), (cx + r * 0.45, cy + r * 0.2), (cx, cy - r * 0.45),
               (cx - r * 0.45, cy + r * 0.2), (cx - r, cy + r * 0.2)], fill=fill)


# ------------------------------------------------------------------ banner
def banner_frame(k, N, W=1100, H=320):
    t = k / N
    img = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):                                   # night sky
        f = y / H
        d.line([(0, y), (W, y)], fill=(int(10 + 14 * f), int(18 + 36 * f), int(40 + 36 * f), 255))
    # drifting cloud bands (pattern period 275 px, so the loop is seamless)
    P = 275
    shift = t * P
    for row, (cy_, a) in enumerate([(284, 70), (306, 95)]):
        for n in range(-1, 6):
            x = n * P + shift * (1 if row == 0 else 0.6) % P + row * 90
            img = Image.alpha_composite(img, glow((W, H), lambda g, x=x, cy_=cy_, a=a: g.ellipse([x - 120, cy_ - 16, x + 120, cy_ + 22], fill=(200, 235, 245, a)), 10))
    d = ImageDraw.Draw(img)
    # twinkling stars, kept clear of the text
    import random
    rng = random.Random(7)
    for _ in range(80):
        x, y = rng.randint(0, W), rng.randint(0, H - 60)
        ph, cyc = rng.random(), rng.choice([1, 2])
        if x < 760 and 50 < y < 305: continue
        b = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(TAU * (t * cyc + ph)))
        c = int(255 * b)
        d.rectangle([x, y, x + 1, y + 1], fill=(c, c, int(c * .92), 255))

    # the core: a floating glowing cube, upgrade symbols rising past it
    cx, cy = 925, 150 + 8 * math.sin(TAU * t)
    pulse = 0.5 + 0.5 * math.sin(TAU * t)
    img = Image.alpha_composite(img, glow((W, H), lambda g: g.ellipse([cx - 120, cy - 110, cx + 120, cy + 110], fill=(90, 220, 235, int(55 + 45 * pulse))), 24))
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for n in range(8):                                    # rising speed chevrons and hearts
        ph = (n * 0.137) % 1
        u = (t + ph) % 1
        px = cx + (n - 3.5) * 24 + 6 * math.sin(TAU * (u + ph))
        py = cy + 120 - u * 250
        a = int(235 * math.sin(math.pi * u))
        if n % 2: chevron(ld, px, py, 8, SPEED + (a,))
        else: heart(ld, px, py, 6, HEALTH + (a,))
    img = Image.alpha_composite(img, layer)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    top, left, right = iso_cube(ld, cx, cy, 62, (96, 215, 230), edge=(220, 255, 255))
    # inner glint on the top face
    c2 = (cx, cy - 31)
    ld.polygon([(c2[0], c2[1] - 14), (c2[0] + 24, c2[1]), (c2[0], c2[1] + 14), (c2[0] - 24, c2[1])], fill=(235, 255, 255, int(120 + 110 * pulse)))
    img = Image.alpha_composite(img, layer)
    d = ImageDraw.Draw(img)
    for n in range(3):                                    # orbiting sparks
        ang = TAU * (t + n / 3)
        ox, oy = cx + 98 * math.cos(ang), cy + 18 + 26 * math.sin(ang)
        r = 3
        d.ellipse([ox - r, oy - r, ox + r, oy + r], fill=(235, 255, 255, 255))

    # text (title is fitted to the free width on the left)
    title = "Happy Ghast Upgrades"
    size = 70
    while d.textbbox((0, 0), title, font=font(size, True))[2] > 740: size -= 2
    d.text((52, 56), title, font=font(size, True), fill=(255, 255, 255, 255))
    d.text((56, 160), "Permanent ghast upgrades for Fabric 26.3.", font=font(26), fill=(205, 232, 240, 255))
    d.text((56, 194), "Server-side only. Players install nothing.", font=font(26), fill=(205, 232, 240, 255))
    f = font(22, True)
    x = 56
    active = int(t * 3) % 3
    for i, (label, col) in enumerate([("Speed", SPEED), ("Health", HEALTH), ("Permanent", AQUA)]):
        tw = d.textbbox((0, 0), label, font=f)[2]
        w = tw + 44
        on = i == active
        if on:
            g = glow((W, H), lambda gg, x=x, w=w, col=col: gg.rounded_rectangle([x, 252, x + w, 294], 10, fill=col + (150,)), 8)
            img = Image.alpha_composite(img, g); d = ImageDraw.Draw(img)
        d.rounded_rectangle([x, 252, x + w, 294], 10, fill=shade(col, 0.5 if on else 0.28) + (255,), outline=col + (255,), width=3 if on else 2)
        d.text((x + 22, 259), label, font=f, fill=col + (255,))
        x += w + 16
    return img.convert("RGB")

def banner():
    N = 24
    frames = [banner_frame(k, N).quantize(colors=48, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE) for k in range(N)]
    frames[0].save(os.path.join(OUT, "banner.gif"), save_all=True, append_images=frames[1:], duration=110, loop=0, optimize=True, disposal=1)


# ------------------------------------------------------------------ recipe / upgrade mock-up
CELL = 76

def tile(img, x, y, kind, label, lit=None):
    """A 76x76 inventory-style slot with a simple icon and a caption underneath the icon."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + CELL - 4, y + CELL - 4], fill=(40, 42, 52), outline=lit or (110, 116, 130), width=3 if lit else 1)
    cx, cy = x + (CELL - 4) // 2, y + 28
    if kind == "dried":
        iso_cube(d, cx, cy, 20, (150, 140, 175))
        for dx in (-7, 7): d.rectangle([cx + dx - 2, cy - 14, cx + dx + 1, cy - 11], fill=(70, 62, 90))
    elif kind == "core":
        iso_cube(d, cx, cy, 20, (96, 215, 230), edge=(220, 255, 255))
        d.rectangle([cx - 2, cy - 14, cx + 2, cy - 10], fill=(235, 255, 255))
    elif kind == "apple":
        d.ellipse([cx - 14, cy - 12, cx + 14, cy + 16], fill=(235, 190, 50), outline=(150, 100, 20), width=2)
        d.ellipse([cx - 8, cy - 7, cx - 2, cy - 1], fill=(255, 240, 160))
        d.polygon([(cx, cy - 12), (cx + 10, cy - 20), (cx + 6, cy - 10)], fill=(90, 170, 70))
    elif kind == "totem":
        d.polygon([(cx - 13, cy - 14), (cx + 13, cy - 14), (cx + 13, cy + 4), (cx, cy + 18), (cx - 13, cy + 4)], fill=(70, 160, 90), outline=(235, 195, 60), width=2)
        d.rectangle([cx - 2, cy - 8, cx + 1, cy + 8], fill=(235, 195, 60))
        d.rectangle([cx - 8, cy - 3, cx + 7, cy], fill=(235, 195, 60))
    f = font(11)
    for i, line in enumerate(label.split("|")[:2]):
        d.text((x + 4, y + CELL - 31 + i * 13), line, font=f, fill=(230, 232, 238))

def arrow(img, x, y, w=60, col=(180, 184, 196)):
    d = ImageDraw.Draw(img)
    d.polygon([(x, y - 8), (x + w - 24, y - 8), (x + w - 24, y - 20), (x + w, y), (x + w - 24, y + 20), (x + w - 24, y + 8), (x, y + 8)], fill=col)

def recipe_image():
    W, H = 1100, 600
    img = Image.new("RGB", (W, H), (16, 20, 30))
    d = ImageDraw.Draw(img)
    d.text((28, 20), "Ghast Core and upgrades", font=font(32, True), fill=(255, 255, 255))
    d.text((28, 62), "Mock-up drawn for this README, not an in-game screenshot. The core recipe is shapeless.",
           font=font(15), fill=INK)

    # 1. craft the core
    x0 = 28
    d.text((x0, 112), "1. Craft the core", font=font(24, True), fill=AQUA)
    d.rectangle([x0, 152, x0 + CELL * 3 + 8, 152 + CELL * 3 + 8], fill=(74, 78, 90))
    for k in range(9):
        cx_, cy_ = x0 + 4 + (k % 3) * CELL, 156 + (k // 3) * CELL
        if k < 3: tile(img, cx_, cy_, "dried", "dried|ghast")
        else: d.rectangle([cx_, cy_, cx_ + CELL - 4, cy_ + CELL - 4], fill=(40, 42, 52), outline=(110, 116, 130))
    arrow(img, x0 + 262, 266, 56)
    tile(img, x0 + 340, 228, "core", "Ghast|Core", lit=AQUA)
    d = ImageDraw.Draw(img)
    d.text((x0, 410), "Ghast Core", font=font(20, True), fill=AQUA)
    d.text((x0, 440), "3 dried ghasts, in any crafting table.", font=font(14), fill=INK)
    d.text((x0, 462), "Dried ghasts come from ghast tears", font=font(14), fill=INK)
    d.text((x0, 482), "or from Soul Sand Valley fossils.", font=font(14), fill=INK)

    # 2. spend it
    xr = 520
    d.text((xr, 112), "2. Spend it on a happy ghast", font=font(24, True), fill=(255, 255, 255))
    rows = [("apple", "golden|apple", "Speed", SPEED, "+15% flying speed per level", "up to 4 levels"),
            ("totem", "totem of|undying", "Health", HEALTH, "+4 HP (2 hearts) per level", "up to 5 levels")]
    for i, (kind, lab, name, col, line1, line2) in enumerate(rows):
        y = 168 + i * 150
        d.text((xr, y - 2), "main hand", font=font(12), fill=INK)
        tile(img, xr, y + 14, "core", "Ghast|Core")
        d = ImageDraw.Draw(img)
        d.text((xr + 88, y + 38), "+", font=font(30, True), fill=INK)
        d.text((xr + 118, y - 2), "off-hand", font=font(12), fill=INK)
        tile(img, xr + 118, y + 14, kind, lab)
        arrow(img, xr + 214, y + 52, 52)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([xr + 290, y + 8, xr + 560, y + 94], 12, fill=shade(col, 0.22), outline=col, width=3)
        d.text((xr + 308, y + 16), name, font=font(24, True), fill=col)
        d.text((xr + 308, y + 52), line1, font=font(14), fill=(235, 238, 245))
        d.text((xr + 308, y + 72), line2, font=font(14), fill=INK)
    d.text((xr, 468), "Right-click the ghast. Both items are used up (creative mode keeps them).", font=font(14), fill=INK)
    d.text((xr, 490), "The upgrade stays on that ghast for good.", font=font(14), fill=INK)
    d.text((xr, 512), "If the ghast dies, its upgrades are gone with it.", font=font(14), fill=INK)

    d.line([(28, 540), (W - 28, 540)], fill=(60, 66, 82), width=1)
    d.text((28, 556), "Payment items, amounts and level caps are set in config/happyghastupgrades/settings.json.", font=font(15), fill=INK)
    img.save(os.path.join(OUT, "recipes.png"), optimize=True)


if __name__ == "__main__":
    banner(); recipe_image()
    print("done ->", OUT)
