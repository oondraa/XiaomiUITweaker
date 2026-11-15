"""
Build "Graphite" MIUI 14 theme (.mtz) for Redmi Note 10 (1080x2400).

  python build.py            -> dist/Graphite.mtz + dist/preview_lock.png

Struktura .mtz (je to obycejny zip):
  description.xml
  wallpaper/default_wallpaper.jpg
  wallpaper/default_lock_wallpaper.jpg
  lockscreen                 <- dalsi zip (bez pripony) s advance/manifest.xml
  preview/preview_lockscreen_0.jpg
  preview/preview_wallpaper_0.jpg
"""
import datetime as dt
import io
import random
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).parent
SRC = ROOT / "theme_src"
DIST = ROOT / "dist"
W, H = 1080, 2400

THEME_NAME = "Graphite"
AUTHOR = "MIUITWEAK"
VERSION = "1.0"
UI_VERSION = "14"  # kdyby Themes appka hlasila nekompatibilitu, zkus "13" nebo "12"

# ---------- paleta ----------
BG_TOP = (22, 22, 24)
BG_BOTTOM = (10, 10, 11)
GLOW_LOCK = (120, 104, 78)   # teply piskovy odlesk
GLOW_HOME = (70, 84, 92)     # chladny sedomodry odlesk
TEXT = (242, 239, 234)
ACCENT = (201, 180, 138)


def wallpaper(glow, glow_pos, seed):
    """Tmava grafitova tapeta: jemny gradient + rozmazany barevny 'odlesk' + zrno."""
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(BG_TOP[i] * (1 - t) + BG_BOTTOM[i] * t) for i in range(3))
        for x in range(W):
            px[x, y] = c

    layer = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(layer)
    gx, gy = glow_pos
    r = 900
    d.ellipse((gx - r, gy - r * 0.7, gx + r, gy + r * 0.7), fill=glow)
    layer = layer.filter(ImageFilter.GaussianBlur(320))
    img = Image.blend(img, Image.composite(layer, img, layer.convert("L")), 0.8)

    # filmove zrno (decentni, ne "AI glossy")
    rnd = random.Random(seed)
    noise = Image.new("L", (W, H))
    noise.putdata([128 + int(rnd.gauss(0, 9)) for _ in range(W * H)])
    img = Image.blend(img, Image.merge("RGB", (noise, noise, noise)), 0.045)
    return img


def font(name, size):
    for cand in (name, "segoeuil.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(cand, size)
        except OSError:
            continue
    return ImageFont.load_default()


DNY = ["pondělí", "úterý", "středa", "čtvrtek", "pátek", "sobota", "neděle"]
MESICE = ["ledna", "února", "března", "dubna", "května", "června", "července",
          "srpna", "září", "října", "listopadu", "prosince"]


def render_lock_preview(bg):
    """Napodobenina manifest.xml pro nahled na PC (rozmisteni 1:1)."""
    img = bg.copy().convert("RGBA")
    ov = Image.new("RGBA", img.size, (0, 0, 0, 70))
    img = Image.alpha_composite(img, ov)
    d = ImageDraw.Draw(img)
    now = dt.datetime.now()
    pad, top = 96, 300

    d.text((pad, top), f"{DNY[now.weekday()]} {now.day}. {MESICE[now.month - 1]}",
           font=font("segoeui.ttf", 40), fill=TEXT + (204,))
    d.text((pad - 10, top + 50), now.strftime("%H:%M"),
           font=font("segoeuil.ttf", 250), fill=TEXT + (255,))
    d.rectangle((pad, top + 360, pad + 72, top + 363), fill=TEXT + (102,))
    small = font("segoeui.ttf", 34)
    d.text((pad, top + 392), "78 %", font=small, fill=TEXT + (153,))
    d.text((W - pad, top + 392), "budík 6:45", font=small, fill=TEXT + (153,), anchor="ra")
    # status bar (kresli MIUI, jen pro predstavu)
    sb = font("segoeui.ttf", 32)
    d.text((60, 40), now.strftime("%H:%M"), font=sb, fill=TEXT + (230,))
    d.text((W - 60, 40), "5G  ▮ 78", font=sb, fill=TEXT + (230,), anchor="ra")
    # swipe linka
    d.rounded_rectangle(((W - 120) // 2, H - 90, (W + 120) // 2, H - 84), 3, fill=TEXT + (136,))
    return img.convert("RGB")


def jpg_bytes(img, q=93):
    b = io.BytesIO()
    img.save(b, "JPEG", quality=q, optimize=True)
    return b.getvalue()


def description_xml():
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<theme>
    <title>{THEME_NAME}</title>
    <designer>{AUTHOR}</designer>
    <author>{AUTHOR}</author>
    <version>{VERSION}</version>
    <uiVersion>{UI_VERSION}</uiVersion>
    <description>Minimalist graphite lock screen. Big thin clock, nothing else.</description>
</theme>
"""


def lockscreen_zip():
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w", zipfile.ZIP_DEFLATED) as z:
        base = SRC / "lockscreen"
        for f in base.rglob("*"):
            if f.is_file():
                z.write(f, f.relative_to(base).as_posix())
    return b.getvalue()


def main():
    DIST.mkdir(exist_ok=True)
    lock_bg = wallpaper(GLOW_LOCK, (W * 0.85, H * 0.78), 1)
    home_bg = wallpaper(GLOW_HOME, (W * 0.15, H * 0.30), 2)
    preview = render_lock_preview(lock_bg)

    lock_bg.save(DIST / "wallpaper_lock.jpg", quality=95)
    home_bg.save(DIST / "wallpaper_home.jpg", quality=95)
    preview.save(DIST / "preview_lock.png")

    mtz = DIST / f"{THEME_NAME}.mtz"
    with zipfile.ZipFile(mtz, "w", zipfile.ZIP_STORED) as z:
        z.writestr("description.xml", description_xml())
        z.writestr("wallpaper/default_wallpaper.jpg", jpg_bytes(home_bg))
        z.writestr("wallpaper/default_lock_wallpaper.jpg", jpg_bytes(lock_bg))
        z.writestr("lockscreen", lockscreen_zip())
        z.writestr("preview/preview_lockscreen_0.jpg", jpg_bytes(preview, 85))
        z.writestr("preview/preview_wallpaper_0.jpg", jpg_bytes(home_bg, 85))
    print(f"OK -> {mtz} ({mtz.stat().st_size // 1024} kB)")


if __name__ == "__main__":
    main()
