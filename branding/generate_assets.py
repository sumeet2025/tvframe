"""Regenerate launcher icons and TV banners from branding/logo-1024.png.
Usage (from repo root): python3 branding/generate_assets.py   (needs Pillow)"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os, glob
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)
logo = Image.open('branding/logo-1024.png').convert('RGB')
BG = (0x0C, 0x13, 0x24)  # dark navy sampled from the logo's edges (matches ic_launcher_background)
R = 'app/src/main/res'

def faded(size, inner):
    """Logo resized to `size`, with edges feathered to transparent so it blends into BG."""
    l = logo.resize((size * 2, size * 2), Image.LANCZOS)
    m = Image.new('L', l.size, 0)
    pad = int(l.size[0] * (1 - inner) / 2)
    ImageDraw.Draw(m).rounded_rectangle([pad, pad, l.size[0] - pad, l.size[0] - pad], radius=int(l.size[0] * 0.18), fill=255)
    m = m.filter(ImageFilter.GaussianBlur(l.size[0] * 0.045))
    t = Image.new('RGBA', l.size, (0, 0, 0, 0)); t.paste(l, (0, 0), m)
    return t.resize((size, size), Image.LANCZOS)

logo.resize((512, 512), Image.LANCZOS).save('branding/icon-512.png', optimize=True)

glyph = logo.crop((100, 100, 924, 924))  # the rounded-square glyph, tighter for small legacy icons
for d, s in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
    os.makedirs(f'{R}/mipmap-{d}', exist_ok=True)
    glyph.resize((s, s), Image.LANCZOS).save(f'{R}/mipmap-{d}/ic_launcher.png', optimize=True)
    big = glyph.resize((s * 4, s * 4), Image.LANCZOS)
    m = Image.new('L', big.size, 0); ImageDraw.Draw(m).ellipse([0, 0, s * 4 - 1, s * 4 - 1], fill=255)
    rnd = Image.new('RGBA', big.size, (0, 0, 0, 0)); rnd.paste(big, (0, 0), m)
    rnd.resize((s, s), Image.LANCZOS).save(f'{R}/mipmap-{d}/ic_launcher_round.png', optimize=True)
    # Adaptive foreground: 108dp canvas, glyph sized to sit inside the ~66-72dp visible zone
    fs = int(s * 108 / 48)
    fg = Image.new('RGBA', (fs, fs), BG + (255,))
    ls = int(fs * 0.86)
    fg.alpha_composite(faded(ls, 0.92), ((fs - ls) // 2, (fs - ls) // 2))
    fg.convert('RGB').save(f'{R}/mipmap-{d}/ic_launcher_foreground.png', optimize=True)

FONT = next(iter(glob.glob('/usr/share/fonts/**/Inter-VariableFont*.ttf', recursive=True)), None)
def font(px):
    if not FONT: return ImageFont.load_default(px)
    f = ImageFont.truetype(FONT, px)
    try: f.set_variation_by_name('Bold')
    except Exception: pass
    return f

def banner(w, h, path):
    b = Image.new('RGBA', (w, h), BG + (255,))
    ls = h
    x0 = int(w * 0.01)
    b.alpha_composite(faded(ls, 0.90), (x0, 0))
    d = ImageDraw.Draw(b); txt = 'TVFrame'
    tx = x0 + ls; avail = w - tx - int(w * 0.05)
    f = font(int(h * 0.34)); bb = d.textbbox((0, 0), txt, font=f)
    if bb[2] - bb[0] > avail:
        f = font(int(h * 0.34 * avail / (bb[2] - bb[0]))); bb = d.textbbox((0, 0), txt, font=f)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    d.text((tx + (avail - tw) // 2 - bb[0], (h - th) // 2 - bb[1]), txt, font=f, fill=(0xF1, 0xF5, 0xF9, 255))
    b.convert('RGB').save(path, optimize=True)

os.makedirs(f'{R}/drawable-xhdpi', exist_ok=True)
banner(320, 180, f'{R}/drawable-xhdpi/tv_banner.png')
banner(1280, 720, 'branding/tv-banner-1280x720.png')
print('done')
