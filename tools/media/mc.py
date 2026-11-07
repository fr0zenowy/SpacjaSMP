"""Zasoby Minecrafta 1.21.11 + czcionka + tooltipy.

Skrypty w tym folderze robia obrazki do README. Nie sa potrzebne do dzialania pluginu.
Do pracy potrzebuja klienta Minecrafta 1.21.11 (jar + indeks assetow, np. z Modrinth App).
Sciezki mozna nadpisac zmiennymi srodowiskowymi:
  MC_CLIENT_JAR, MC_ASSETS_DIR, MC_ASSET_INDEX, ZAM_PACK_DIR
"""
import io
import json
import os
import re
import zipfile
from functools import lru_cache

from PIL import Image, ImageDraw

JAR = os.environ.get(
    "MC_CLIENT_JAR",
    r"C:\Users\piotr\AppData\Roaming\ModrinthApp\meta\versions\1.21.11-0.19.5\1.21.11-0.19.5.jar",
)
ASSETS = os.environ.get("MC_ASSETS_DIR", r"C:\Users\piotr\AppData\Roaming\ModrinthApp\meta\assets")
ASSET_INDEX = os.environ.get("MC_ASSET_INDEX", "29")
PACK = os.environ.get("ZAM_PACK_DIR", r"C:\Users\piotr\Downloads\Nowy folder (13)\resource pack\assets")

_jar = zipfile.ZipFile(JAR)


def jar_bytes(path):
    return _jar.read(path)


@lru_cache(None)
def vanilla(name):
    """Tekstura z klienta, np. 'item/bow' albo 'block/stone'."""
    return Image.open(io.BytesIO(_jar.read(f"assets/minecraft/textures/{name}.png"))).convert("RGBA")


@lru_cache(None)
def pack(path):
    """Tekstura z paczki zasobow serwera, np. 'item/snare'."""
    return Image.open(os.path.join(PACK, "minecraft", "textures", *path.split("/")) + ".png").convert("RGBA")


def pack_gui(namespace, name):
    return Image.open(os.path.join(PACK, namespace, "textures", "gui", name + ".png")).convert("RGBA")


@lru_cache(None)
def _asset_index():
    with open(os.path.join(ASSETS, "indexes", ASSET_INDEX + ".json"), encoding="utf-8") as f:
        return json.load(f)["objects"]


def asset_object(key):
    h = _asset_index()[key]["hash"]
    with open(os.path.join(ASSETS, "objects", h[:2], h), "rb") as f:
        return f.read()


# ---------------------------------------------------------------- czcionka

COLORS = {
    "0": (0, 0, 0), "1": (0, 0, 170), "2": (0, 170, 0), "3": (0, 170, 170),
    "4": (170, 0, 0), "5": (170, 0, 170), "6": (255, 170, 0), "7": (170, 170, 170),
    "8": (85, 85, 85), "9": (85, 85, 255), "a": (85, 255, 85), "b": (85, 255, 255),
    "c": (255, 85, 85), "d": (255, 85, 255), "e": (255, 255, 85), "f": (255, 255, 255),
}


class _Glyph:
    __slots__ = ("img", "adv", "top", "h", "unit")

    def __init__(self, img, adv, top, h, unit):
        self.img, self.adv, self.top, self.h, self.unit = img, adv, top, h, unit


@lru_cache(None)
def _glyphs():
    """Zwraca slownik znak -> _Glyph (pikselowe maski)."""
    table = {}
    prov = json.loads(_jar.read("assets/minecraft/font/include/default.json"))["providers"]
    for p in prov:
        if p["type"] != "bitmap":
            continue
        sheet = Image.open(io.BytesIO(_jar.read("assets/minecraft/textures/" + p["file"].split(":")[1]))).convert("RGBA")
        rows = p["chars"]
        cols = len(rows[0])
        cw, ch = sheet.width // cols, sheet.height // len(rows)
        height = p.get("height", 8)
        ascent = p["ascent"]
        scale = height / ch
        for r, row in enumerate(rows):
            for c, ch_ in enumerate(row):
                if ch_ == "\x00" or ch_ in table:
                    continue
                cell = sheet.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch))
                bbox = cell.getchannel("A").getbbox()
                if not bbox:
                    width = 0
                else:
                    width = bbox[2]
                adv = round(width * scale) + 1 if width else 4
                table[ch_] = _Glyph(cell, adv, 7 - ascent, height, scale)
    return table


@lru_cache(None)
def _unihex():
    data = {}
    z = zipfile.ZipFile(io.BytesIO(asset_object("minecraft/font/unifont.zip")))
    for name in z.namelist():
        if name.endswith(".hex"):
            for line in z.read(name).decode("ascii", "ignore").splitlines():
                cp, _, hx = line.partition(":")
                if hx:
                    data[int(cp, 16)] = hx
    return data


@lru_cache(None)
def _uni_glyph(ch):
    hx = _unihex().get(ord(ch))
    if not hx:
        return None
    width = len(hx) // 4  # 8 albo 16 kolumn
    rows = []
    step = width // 4
    for i in range(16):
        rows.append(int(hx[i * step:(i + 1) * step], 16))
    im = Image.new("RGBA", (width, 16), (0, 0, 0, 0))
    px = im.load()
    for y, bits in enumerate(rows):
        for x in range(width):
            if bits >> (width - 1 - x) & 1:
                px[x, y] = (255, 255, 255, 255)
    bbox = im.getchannel("A").getbbox()
    if not bbox:
        return None
    im = im.crop((bbox[0], 0, bbox[2], 16))
    adv = (bbox[2] - bbox[0]) / 2 + 1
    return _Glyph(im, adv, 0, 8, 0.5)


def _glyph(ch):
    g = _glyphs().get(ch)
    if g is None:
        g = _uni_glyph(ch)
    return g


def _parse(text, default="f"):
    """Dzieli napis z kodami § na (znak, kolor, bold)."""
    out = []
    color, bold = default, False
    i = 0
    while i < len(text):
        c = text[i]
        if c == "\u00a7" and i + 1 < len(text):
            code = text[i + 1].lower()
            i += 2
            if code in COLORS:
                color, bold = code, False
            elif code == "l":
                bold = True
            elif code == "r":
                color, bold = default, False
            continue
        out.append((c, color, bold))
        i += 1
    return out


def text_width(text):
    w = 0.0
    for ch, _, bold in _parse(text):
        if ch == " ":
            w += 4
            continue
        g = _glyph(ch)
        w += (g.adv if g else 6) + (1 if bold else 0)
    return int(round(w))


def draw_text(img, x, y, text, scale=3, shadow=True, default="f"):
    """Rysuje tekst jak w grze. x,y w pikselach GUI (mnozone przez scale). Zwraca szerokosc w px GUI."""
    cx = float(x)
    for ch, color, bold in _parse(text, default):
        if ch == " ":
            cx += 4
            continue
        g = _glyph(ch)
        if g is None:
            cx += 6
            continue
        rgb = COLORS[color]
        mask = g.img.getchannel("A")
        gw = max(1, round(g.img.width * g.unit * scale))
        gh = max(1, round(g.img.height * g.unit * scale))
        mask = mask.resize((gw, gh), Image.NEAREST)
        offs = [(0, 0), (1, 0)] if bold else [(0, 0)]
        passes = []
        if shadow:
            passes.append(((1, 1), tuple(c // 4 for c in rgb)))
        passes.append(((0, 0), rgb))
        for (sx, sy), col in passes:
            for ox, oy in offs:
                px = round((cx + sx + ox) * scale)
                py = round((y + g.top + sy) * scale)
                img.paste(Image.new("RGBA", mask.size, col + (255,)), (px, py), mask)
        cx += g.adv + (1 if bold else 0)
    return cx - x


def center_text(img, cx, y, text, scale=3, **kw):
    w = text_width(text)
    return draw_text(img, cx - w / 2, y, text, scale, **kw)


def strip_codes(s):
    return re.sub("\u00a7.", "", s)


# ---------------------------------------------------------------- tooltip

def tooltip(lines, scale=3, pad=0):
    """Obrazek tooltipu jak w grze. Pierwsza linia to nazwa przedmiotu."""
    widths = [text_width(l) for l in lines]
    w = max(widths) + 8
    h = len(lines) * 10 + 2 + 6 if len(lines) > 1 else 10 + 6
    img = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = scale
    # tlo + ramka (jak vanilla: ciemny fiolet i fioletowa obwodka)
    d.rectangle((s, 0, w * s - s - 1, h * s - 1), fill=(16, 0, 16, 245))
    d.rectangle((0, s, w * s - 1, h * s - s - 1), fill=(16, 0, 16, 245))
    d.rectangle((s, s, w * s - s - 1, h * s - s - 1), outline=(72, 12, 150, 255), width=s)
    y = 4
    for i, line in enumerate(lines):
        draw_text(img, 4, y, line, s)
        y += 10 + (2 if i == 0 and len(lines) > 1 else 0)
    return img


# ---------------------------------------------------------------- GIF

def save_gif(frames, path, ms=50, colors=160, loop=0, max_mb=None):
    """Zapisuje liste RGB(A) klatek jako GIF z jedna wspolna paleta."""
    rgb = [f.convert("RGB") for f in frames]
    step = max(1, len(rgb) // 12)
    sample = Image.new("RGB", (rgb[0].width, rgb[0].height * len(rgb[::step])))
    for i, fr in enumerate(rgb[::step]):
        sample.paste(fr, (0, i * rgb[0].height))
    pal = sample.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = [fr.quantize(palette=pal, dither=Image.Dither.NONE) for fr in rgb]
    out[0].save(path, save_all=True, append_images=out[1:], duration=ms, loop=loop, optimize=False, disposal=1)
    mb = os.path.getsize(path) / 1048576
    if max_mb and mb > max_mb and colors > 48:
        return save_gif(frames, path, ms, colors - 32, loop, max_mb)
    return mb
