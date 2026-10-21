"""Render the Play listing graphics from the launcher mark (S16B-05).

The mark is the one in apps/android/app-*/src/main/res/drawable/ic_lumo.xml:
a rounded screen with a play triangle punched out of it. No channel artwork,
no bouquet logo, nothing else (AGENTS.md section 1).

    python -I docs/releases/0.4.0/play/graphismes/make_graphics.py

Needs Pillow. Text uses Segoe UI Semibold when present (Windows), otherwise
Pillow's default font: rerun on Windows to reproduce the committed files.
"""
import os
from PIL import Image, ImageDraw, ImageFont

BG = (7, 9, 15)          # #07090F, ic_lumo background
ACCENT = (76, 184, 255)  # #4CB8FF, ic_lumo frame
MUTED = (160, 172, 190)
OUT = os.path.dirname(os.path.abspath(__file__))
SS = 4  # supersampling factor for smooth edges


def font(size, bold=True):
    names = ["seguisb.ttf", "segoeuib.ttf"] if bold else ["segoeui.ttf"]
    for name in names:
        path = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts", name)
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def mark(draw, cx, cy, w):
    """The mark: frame of 60x48 units, radius 6, triangle 24 wide, centred."""
    u = w / 60.0
    h = 48 * u
    x0, y0 = cx - w / 2, cy - h / 2
    draw.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=6 * u, fill=ACCENT)
    # Triangle M48,42 l24,12 l-24,12 in the 108 viewport: frame starts at (24,30).
    tx, ty = x0 + 24 * u, y0 + 12 * u
    draw.polygon([(tx, ty), (tx + 24 * u, ty + 12 * u), (tx, ty + 24 * u)], fill=BG)


def canvas(w, h):
    img = Image.new("RGB", (w * SS, h * SS), BG)
    return img, ImageDraw.Draw(img)


def save(img, w, h, name):
    img.resize((w, h), Image.LANCZOS).save(os.path.join(OUT, name), optimize=True)


def icon():
    img, d = canvas(512, 512)
    mark(d, 256 * SS, 256 * SS, 300 * SS)
    save(img, 512, 512, "icone-512.png")


def lockup(w, h, name, mark_w, title_px, tagline, tagline_px, label="Lumo"):
    img, d = canvas(w, h)
    title = font(title_px * SS)
    sub = font(tagline_px * SS, bold=False) if tagline else None
    gap = mark_w * 0.28
    tw = d.textlength(label, font=title)
    total = mark_w + gap + tw
    left = (w * SS - total) / 2
    cy = (h * SS) / 2 - (tagline_px * SS * 0.9 if tagline else 0)
    mark(d, left + mark_w / 2, cy, mark_w)
    d.text((left + mark_w + gap, cy), label, font=title, fill=(255, 255, 255), anchor="lm")
    if tagline:
        d.text((w * SS / 2, cy + mark_w * 0.62), tagline, font=sub, fill=MUTED, anchor="mt")
    save(img, w, h, name)


if __name__ == "__main__":
    icon()
    # Android TV listing banner, 1280 x 720. The TV application is called
    # "Lumo TV" (app-tv strings.xml), so its banner says so.
    lockup(1280, 720, "banniere-tv-1280x720.png", 260 * SS, 170, "", 0, label="Lumo TV")
    # Feature graphics, 1024 x 500, one per listing and language: they carry text.
    taglines = {
        "fr": "Votre lecteur, vos sources. Aucun contenu fourni.",
        "en": "Your player, your sources. No content included.",
    }
    for lang, tagline in taglines.items():
        lockup(1024, 500, f"presentation-telephone-1024x500-{lang}.png",
               190 * SS, 120, tagline, 34)
        lockup(1024, 500, f"presentation-tv-1024x500-{lang}.png",
               160 * SS, 110, tagline, 34, label="Lumo TV")
