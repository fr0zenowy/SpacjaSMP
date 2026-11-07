"""Pomocnicze elementy scen: kamera, podloga, gracz z przedmiotem, czasteczki i HUD z gry."""
import math

from PIL import Image, ImageDraw, ImageFilter

import mc
import render3d as r

W, H = 640, 360


def gradient_bg(size=(W, H), top=(30, 34, 46), bottom=(13, 15, 21)):
    w, h = size
    bg = Image.new("RGB", size)
    px = bg.load()
    for y in range(h):
        t = y / (h - 1)
        c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
        for x in range(w):
            px[x, y] = c
    return bg.convert("RGBA")


_BG = {}


def background(size=(W, H)):
    if size not in _BG:
        _BG[size] = gradient_bg(size)
    return _BG[size].copy()


class Stage:
    def __init__(self, yaw=-0.5, pitch=0.5, scale=34.0, center=(0, 0.8, 0), size=(W, H)):
        self.size = size
        self.cam = r.Camera(yaw=yaw, pitch=pitch, scale=scale, cx=size[0] / 2, cy=size[1] / 2, center=center)
        self.static = []   # faces ktore sie nie zmieniaja (podloga)

    def project(self, p):
        x, y, _ = self.cam.project(p)
        return x, y

    def floor(self, x0, x1, z0, z1, tex, tint=None, y=0.0, depth=1):
        """Podloga z kostek (gorna sciana + boki na krawedziach). Kostki zajmuja y-1..y."""
        faces = []
        for x in range(x0, x1):
            for z in range(z0, z1):
                fd = {"up": (tex, (0, 0, tex.width, tex.height), 0)}
                if x == x0:
                    fd["west"] = (tex, (0, 0, tex.width, tex.height), 0)
                if x == x1 - 1:
                    fd["east"] = (tex, (0, 0, tex.width, tex.height), 0)
                if z == z0:
                    fd["north"] = (tex, (0, 0, tex.width, tex.height), 0)
                if z == z1 - 1:
                    fd["south"] = (tex, (0, 0, tex.width, tex.height), 0)
                faces += r.box_faces(x, y - depth, z, x + 1, y, z + 1, fd, layer=0, tint=tint)
        return faces

    def render(self, faces, bg=None):
        base = bg.copy() if bg is not None else background(self.size)
        fr = r.render(faces, self.cam, self.size, bg=(0, 0, 0, 0), ss=2)
        base.alpha_composite(fr)
        return base


# ---------------------------------------------------------------- gracz z przedmiotem

def sprite_item(tex, size=0.9):
    """Plaski przedmiot (jak item/handheld): chwyt w (0,0,0), czubek po przekatnej."""
    faces = r.sprite_faces(tex, size=size, thickness=0.0625, slices=5, center=(size * 0.38, size * 0.38, 0), layer=1)
    return faces


def model_item(faces, grip, scale=0.9):
    """Model 3D (os ostrza = +y): chwyt w (0,0,0), ostrze po przekatnej."""
    out = []
    for f in faces:
        f.v = [((p[0] - grip[0]) * scale, (p[1] - grip[1]) * scale, (p[2] - grip[2]) * scale) for p in f.v]
        out.append(f)
    r.rotate_faces(out, "z", -45)
    return out


def hold(faces, pose_arm=-70.0):
    """Wklada przedmiot (chwyt w 0,0,0, czubek po przekatnej w plaszczyznie XY) do prawej reki."""
    k = 0.9375
    pivot = (-6 / 16 * k, 24 / 16 * k, 0.0)
    hand = r._rot((-6 / 16 * k, 12 / 16 * k, 0.0), pivot, "x", pose_arm)
    r.rotate_faces(faces, "y", -90)
    r.translate_faces(faces, (hand[0], hand[1], hand[2] + 0.08))
    r.rotate_faces(faces, "x", pose_arm + 70, hand)
    return faces


def steve(origin=(0, 0, 0), yaw=0.0, pose=None, tint=None, item=None, skin=None):
    """item: lista Face w ukladzie 'chwyt w (0,0,0)', zbudowana przez sprite_item/model_item."""
    skin = skin or mc.vanilla("entity/player/wide/steve")
    pose = dict(pose or {})
    faces = r.player_faces(skin, pose=pose, origin=(0, 0, 0), yaw=0.0, layer=1, tint=tint)
    if item is not None:
        faces += hold(item, pose_arm=pose.get("arm_r", -70.0))
    if yaw:
        r.rotate_faces(faces, "y", yaw)
    r.translate_faces(faces, origin)
    return faces


# ---------------------------------------------------------------- czasteczki i efekty (nakladka 2D)

_glow_cache = {}


def _radial(size):
    if size not in _glow_cache:
        g = Image.radial_gradient("L").resize((size, size), Image.BILINEAR)
        g = g.point(lambda v: max(0, 255 - v) ** 2 // 255)
        _glow_cache[size] = g
    return _glow_cache[size]


def glow(img, xy, radius, color, strength=1.0):
    """Miekka poswiata (addytywna) wokol punktu."""
    radius = max(2, int(radius))
    mask = _radial(radius * 2).point(lambda v: int(v * strength))
    layer = Image.new("RGBA", (radius * 2, radius * 2), color + (255,))
    layer.putalpha(mask)
    img.alpha_composite(layer, (int(xy[0] - radius), int(xy[1] - radius)), source=(0, 0)) if (
        0 <= xy[0] - radius and 0 <= xy[1] - radius and xy[0] + radius < img.width and xy[1] + radius < img.height
    ) else _paste_clipped(img, layer, (int(xy[0] - radius), int(xy[1] - radius)))


def _paste_clipped(img, layer, pos):
    x, y = pos
    sx, sy = max(0, -x), max(0, -y)
    ex, ey = min(layer.width, img.width - x), min(layer.height, img.height - y)
    if ex <= sx or ey <= sy:
        return
    img.alpha_composite(layer.crop((sx, sy, ex, ey)), (x + sx, y + sy))


def dot(img, xy, r_px, color, alpha=255):
    d = ImageDraw.Draw(img, "RGBA")
    d.ellipse((xy[0] - r_px, xy[1] - r_px, xy[0] + r_px, xy[1] + r_px), fill=color + (alpha,))


def particle(img, name, xy, size_px, frame=None, tint=None, alpha=255):
    """Sprite czasteczki z klienta (billboard)."""
    tex = mc.vanilla("particle/" + name if frame is None else f"particle/{name}_{frame}")
    im = tex.resize((int(size_px), int(size_px)), Image.NEAREST)
    if tint:
        px = im.load()
        for yy in range(im.height):
            for xx in range(im.width):
                pr, pg, pb, pa = px[xx, yy]
                px[xx, yy] = (pr * tint[0] // 255, pg * tint[1] // 255, pb * tint[2] // 255, pa)
    if alpha < 255:
        a = im.getchannel("A").point(lambda v: v * alpha // 255)
        im.putalpha(a)
    _paste_clipped(img, im, (int(xy[0] - size_px / 2), int(xy[1] - size_px / 2)))


def ring_points(center, normal, radius, n=24, phase=0.0):
    """Punkty okregu w 3D prostopadlego do `normal`."""
    nx, ny, nz = normal
    ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
    nx, ny, nz = nx / ln, ny / ln, nz / ln
    ax = (0, 1, 0) if abs(ny) < 0.9 else (1, 0, 0)
    ux = ny * ax[2] - nz * ax[1]
    uy = nz * ax[0] - nx * ax[2]
    uz = nx * ax[1] - ny * ax[0]
    lu = math.sqrt(ux * ux + uy * uy + uz * uz) or 1
    ux, uy, uz = ux / lu, uy / lu, uz / lu
    vx, vy, vz = ny * uz - nz * uy, nz * ux - nx * uz, nx * uy - ny * ux
    pts = []
    for i in range(n):
        a = phase + 2 * math.pi * i / n
        c, s = math.cos(a) * radius, math.sin(a) * radius
        pts.append((center[0] + ux * c + vx * s, center[1] + uy * c + vy * s, center[2] + uz * c + vz * s))
    return pts


# ---------------------------------------------------------------- HUD

def hud_text(img, text, cx, y, scale=3):
    mc.center_text(img, cx / scale, y / scale, text, scale)


def actionbar(img, text, scale=3):
    hud_text(img, text, img.width / 2, img.height - 78, scale)


def hearts_row(img, x, y, full, total=10, scale=3, container=True):
    """Pasek serc z gry; full moze byc ulamkiem (polowki)."""
    c = mc.vanilla("gui/sprites/hud/heart/container")
    f = mc.vanilla("gui/sprites/hud/heart/full")
    h = mc.vanilla("gui/sprites/hud/heart/half")
    for i in range(total):
        px = x + i * 8 * scale
        img.alpha_composite(c.resize((9 * scale, 9 * scale), Image.NEAREST), (px, y))
        if full >= i + 1:
            img.alpha_composite(f.resize((9 * scale, 9 * scale), Image.NEAREST), (px, y))
        elif full > i:
            img.alpha_composite(h.resize((9 * scale, 9 * scale), Image.NEAREST), (px, y))


def bossbar(img, cx, y, title, progress, color="purple", scale=2):
    bg = mc.vanilla(f"gui/sprites/boss_bar/{color}_background")
    pr = mc.vanilla(f"gui/sprites/boss_bar/{color}_progress")
    w, h = bg.size
    x = int(cx - w * scale / 2)
    img.alpha_composite(bg.resize((w * scale, h * scale), Image.NEAREST), (x, y))
    pw = int(w * progress)
    if pw > 0:
        img.alpha_composite(pr.crop((0, 0, pw, h)).resize((pw * scale, h * scale), Image.NEAREST), (x, y))
    mc.center_text(img, cx / 2, (y - 14) / 2, title, 2)


def effect_icon(img, name, x, y, label, scale=2):
    """Ikona efektu jak w HUD (prawy gorny rog); podpis po lewej od ikony."""
    ico = mc.vanilla("mob_effect/" + name)
    img.alpha_composite(ico.resize((18 * scale, 18 * scale), Image.NEAREST), (x, y))
    w = mc.text_width(label) * 2
    mc.draw_text(img, (x - w - 8) / 2, (y + 10) / 2, label, 2)


def caption(img, text, x=18, y=14, scale=3):
    mc.draw_text(img, x / scale, y / scale, text, scale)


def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t
