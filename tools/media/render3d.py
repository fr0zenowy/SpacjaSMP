"""Maly renderer 3D (rzut ortogonalny, algorytm malarza) do obrazkow z Minecrafta.

Obsluguje: modele JSON przedmiotow (elementy z obrotem i UV), kostki blokow, wytloczone sprite'y
(jak item/generated) i gracza ze skinem. Bez numpy, tylko Pillow.
"""
import json
import math

from PIL import Image, ImageChops, ImageDraw

SHADE = {"up": 1.0, "down": 0.55, "north": 0.8, "south": 0.8, "east": 0.68, "west": 0.68}


class Face:
    __slots__ = ("v", "tex", "uv", "rot", "shade", "normal", "two_sided", "layer", "tint", "alpha")

    def __init__(self, v, tex, uv, rot=0, shade=1.0, normal=(0, 0, 1), two_sided=False, layer=0, tint=None, alpha=1.0):
        self.v, self.tex, self.uv, self.rot = v, tex, uv, rot
        self.shade, self.normal, self.two_sided, self.layer = shade, normal, two_sided, layer
        self.tint, self.alpha = tint, alpha


class Camera:
    def __init__(self, yaw=0.0, pitch=0.5, scale=10.0, cx=0.0, cy=0.0, center=(0, 0, 0)):
        self.yaw, self.pitch, self.scale, self.cx, self.cy, self.center = yaw, pitch, scale, cx, cy, center

    def view(self, p):
        x, y, z = p[0] - self.center[0], p[1] - self.center[1], p[2] - self.center[2]
        cy_, sy_ = math.cos(self.yaw), math.sin(self.yaw)
        x1, z1 = x * cy_ + z * sy_, -x * sy_ + z * cy_
        cp, sp = math.cos(self.pitch), math.sin(self.pitch)
        return x1, y * cp - z1 * sp, y * sp + z1 * cp

    def project(self, p):
        x, y, z = self.view(p)
        return self.cx + x * self.scale, self.cy - y * self.scale, z

    def view_normal(self, n):
        x, y, z = n
        cy_, sy_ = math.cos(self.yaw), math.sin(self.yaw)
        x1, z1 = x * cy_ + z * sy_, -x * sy_ + z * cy_
        cp, sp = math.cos(self.pitch), math.sin(self.pitch)
        return x1, y * cp - z1 * sp, y * sp + z1 * cp


def _solve_affine(P, S):
    """P: 3 punkty docelowe (TL,TR,BL), S: 3 punkty zrodlowe -> (M, brak) lub None."""
    e1 = (P[1][0] - P[0][0], P[1][1] - P[0][1])
    e2 = (P[2][0] - P[0][0], P[2][1] - P[0][1])
    det = e1[0] * e2[1] - e1[1] * e2[0]
    if abs(det) < 1e-6:
        return None
    s1 = (S[1][0] - S[0][0], S[1][1] - S[0][1])
    s2 = (S[2][0] - S[0][0], S[2][1] - S[0][1])
    inv = ((e2[1] / det, -e2[0] / det), (-e1[1] / det, e1[0] / det))
    return (
        (s1[0] * inv[0][0] + s2[0] * inv[1][0], s1[0] * inv[0][1] + s2[0] * inv[1][1]),
        (s1[1] * inv[0][0] + s2[1] * inv[1][0], s1[1] * inv[0][1] + s2[1] * inv[1][1]),
    )


def draw_face(canvas, cam, f):
    pts = [cam.project(p) for p in f.v]
    vn = cam.view_normal(f.normal)
    if not f.two_sided and vn[2] <= 0.0001:
        return
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    bx0, by0 = max(0, int(math.floor(min(xs)))), max(0, int(math.floor(min(ys))))
    bx1, by1 = min(canvas.width, int(math.ceil(max(xs)))), min(canvas.height, int(math.ceil(max(ys))))
    w, h = bx1 - bx0, by1 - by0
    if w <= 0 or h <= 0:
        return
    u0, v0, u1, v1 = f.uv
    corners = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    k = (f.rot // 90) % 4
    corners = corners[k:] + corners[:k] if k else corners
    cu = [c[0] for c in corners]
    cv = [c[1] for c in corners]
    ox, oy = int(math.floor(min(cu))), int(math.floor(min(cv)))
    ex, ey = int(math.ceil(max(cu))), int(math.ceil(max(cv)))
    ex, ey = max(ex, ox + 1), max(ey, oy + 1)
    tex = f.tex.crop((ox, oy, ex, ey))
    S = [(corners[0][0] - ox, corners[0][1] - oy), (corners[1][0] - ox, corners[1][1] - oy), (corners[3][0] - ox, corners[3][1] - oy)]
    P = [(pts[0][0], pts[0][1]), (pts[1][0], pts[1][1]), (pts[3][0], pts[3][1])]
    M = _solve_affine(P, S)
    if M is None:
        return
    dx, dy = bx0 - P[0][0], by0 - P[0][1]
    c = M[0][0] * dx + M[0][1] * dy + S[0][0]
    fcoef = M[1][0] * dx + M[1][1] * dy + S[0][1]
    coeffs = (M[0][0], M[0][1], c, M[1][0], M[1][1], fcoef)
    tile = tex.transform((w, h), Image.AFFINE, coeffs, resample=Image.NEAREST, fillcolor=(0, 0, 0, 0))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon([(p[0] - bx0, p[1] - by0) for p in pts], fill=255)
    alpha = ImageChops.multiply(tile.getchannel("A"), mask)
    if f.alpha < 1.0:
        alpha = alpha.point(lambda a: int(a * f.alpha))
    rgb = tile.convert("RGB")
    s = f.shade
    if f.tint:
        rgb = ImageChops.multiply(rgb, Image.new("RGB", (w, h), f.tint))
    rgb = rgb.point(lambda a: min(255, int(a * s)))
    canvas.paste(rgb, (bx0, by0), alpha)


def face_depth(cam, f):
    zs = [cam.view(p)[2] for p in f.v]
    return sum(zs) / len(zs)


def render(faces, cam, size, bg=(0, 0, 0, 0), ss=2):
    """Rysuje liste Face. ss = supersampling."""
    W, H = size
    big = Image.new("RGBA", (W * ss, H * ss), bg)
    c2 = Camera(cam.yaw, cam.pitch, cam.scale * ss, cam.cx * ss, cam.cy * ss, cam.center)
    order = sorted(faces, key=lambda f: (f.layer, face_depth(c2, f)))
    for f in order:
        draw_face(big, c2, f)
    return big.resize((W, H), Image.LANCZOS) if ss > 1 else big


# ---------------------------------------------------------------- geometria

def _rot(p, origin, axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def _rotn(n, axis, deg):
    return _rot(n, (0, 0, 0), axis, deg)


def box_faces(x1, y1, z1, x2, y2, z2, face_defs, rotation=None, offset=(0, 0, 0), layer=0, tint=None, alpha=1.0, shade_override=None, two_sided=False):
    """face_defs: dict kierunek -> (tex, (u0,v0,u1,v1) w pikselach tekstury, rot). Wspolrzedne w blokach."""
    corners = {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    out = []
    for d, spec in face_defs.items():
        tex, uv, rot = spec
        vs = corners[d]
        n = normals[d]
        if rotation:
            org, axis, ang = rotation
            vs = [_rot(p, org, axis, ang) for p in vs]
            n = _rotn(n, axis, ang)
        vs = [(p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]) for p in vs]
        out.append(Face(vs, tex, uv, rot, shade_override if shade_override is not None else SHADE[d], n, two_sided, layer, tint, alpha))
    return out


def cube(x, y, z, tex_by_face, size=1.0, layer=0, tint=None, alpha=1.0, rotation=None):
    """Kostka o boku `size` z rogiem min w (x,y,z). tex_by_face: 'all' albo up/down/side/north/..."""
    fd = {}
    for d in ("up", "down", "north", "south", "east", "west"):
        t = tex_by_face.get(d) or (tex_by_face.get("side") if d not in ("up", "down") else None) or tex_by_face.get("all")
        fd[d] = (t, (0, 0, t.width, t.width), 0)
    return box_faces(x, y, z, x + size, y + size, z + size, fd, layer=layer, tint=tint, alpha=alpha, rotation=rotation)


# ---------------------------------------------------------------- modele JSON z paczki

def model_faces(model, textures, scale=1 / 16.0, center=(8, 8, 8), layer=0):
    """Zamienia model JSON (elements) na liste Face. textures: dict nazwa -> Image (np. {'0': img}).
    Wspolrzedne przesuniete tak, by `center` (w jednostkach modelu) lezal w (0,0,0)."""
    out = []
    names = ("north", "east", "south", "west", "up", "down")
    for el in model["elements"]:
        x1, y1, z1 = el["from"]
        x2, y2, z2 = el["to"]
        fdefs = {}
        for d in names:
            fc = el["faces"].get(d)
            if not fc:
                continue
            tex = textures[fc["texture"].lstrip("#")]
            u0, v0, u1, v1 = fc.get("uv", [0, 0, 16, 16])
            k = tex.width / 16.0
            fdefs[d] = (tex, (u0 * k, v0 * k, u1 * k, v1 * k), fc.get("rotation", 0))
        rot = el.get("rotation")
        rotation = None
        if rot and rot.get("angle"):
            rotation = (tuple(rot["origin"]), rot["axis"], rot["angle"])
        faces = box_faces(x1, y1, z1, x2, y2, z2, fdefs, rotation=rotation, layer=layer)
        for f in faces:
            f.v = [((p[0] - center[0]) * scale, (p[1] - center[1]) * scale, (p[2] - center[2]) * scale) for p in f.v]
        out.extend(faces)
    return out


def sprite_faces(tex, size=1.0, thickness=0.0625, slices=7, center=(0, 0, 0), layer=0, tint=None):
    """Wytloczony sprite w plaszczyznie XY (jak item/generated). Zwraca liste Face."""
    out = []
    half = size / 2
    for i in range(slices):
        t = i / (slices - 1) if slices > 1 else 0.5
        z = center[2] + (t - 0.5) * thickness
        edge = 0 < i < slices - 1
        shade = 0.62 if edge else (1.0 if i == slices - 1 else 0.88)
        v = [
            (center[0] - half, center[1] + half, z),
            (center[0] + half, center[1] + half, z),
            (center[0] + half, center[1] - half, z),
            (center[0] - half, center[1] - half, z),
        ]
        out.append(Face(v, tex, (0, 0, tex.width, tex.height), 0, shade, (0, 0, 1), True, layer, tint))
    return out


def transform_faces(faces, fn):
    for f in faces:
        f.v = [fn(p) for p in f.v]
        f.normal = fn(f.normal) if False else f.normal
    return faces


def rotate_faces(faces, axis, deg, origin=(0, 0, 0)):
    for f in faces:
        f.v = [_rot(p, origin, axis, deg) for p in f.v]
        f.normal = _rotn(f.normal, axis, deg)
    return faces


def translate_faces(faces, d):
    for f in faces:
        f.v = [(p[0] + d[0], p[1] + d[1], p[2] + d[2]) for p in f.v]
    return faces


# ---------------------------------------------------------------- gracz (skin 64x64)

def player_faces(skin, pose=None, origin=(0, 0, 0), yaw=0.0, layer=1, tint=None):
    """Gracz 1.8 bloku wysokosci. pose: slownik kat obrotu (deg) wokol osi X: arm_r, arm_l, leg_r, leg_l, head_pitch.
    Postac patrzy w kierunku +z (south) przy yaw=0."""
    pose = pose or {}
    k = 1 / 16.0

    def part(w, h, d, u, v, px, py, pz, pivot, ang_x=0.0, ang_z=0.0):
        # (px,py,pz) = rog min czesci w pikselach, pivot = punkt obrotu
        uvs = {
            "up": (u + d, v, u + d + w, v + d),
            "down": (u + d + w, v, u + d + w + w, v + d),
            "east": (u, v + d, u + d, v + d + h),
            "south": (u + d, v + d, u + d + w, v + d + h),
            "west": (u + d + w, v + d, u + d + w + d, v + d + h),
            "north": (u + d + w + d, v + d, u + d + w + d + w, v + d + h),
        }
        # skin: przod postaci to kierunek north w modelu skina -> mapujemy 'south' (nasz przod) na front skina
        fd = {}
        mapping = {"south": "south", "north": "north", "east": "west", "west": "east", "up": "up", "down": "down"}
        for dname, src in mapping.items():
            fd[dname] = (skin, uvs[src], 0)
        faces = box_faces(px * k, py * k, pz * k, (px + w) * k, (py + h) * k, (pz + d) * k, fd, layer=layer, tint=tint)
        pv = (pivot[0] * k, pivot[1] * k, pivot[2] * k)
        if ang_x:
            rotate_faces(faces, "x", ang_x, pv)
        if ang_z:
            rotate_faces(faces, "z", ang_z, pv)
        return faces

    out = []
    out += part(4, 12, 4, 0, 16, -4, 0, -2, (-2, 12, 0), pose.get("leg_r", 0))      # prawa noga
    out += part(4, 12, 4, 16, 48, 0, 0, -2, (2, 12, 0), pose.get("leg_l", 0))       # lewa noga
    out += part(8, 12, 4, 16, 16, -4, 12, -2, (0, 12, 0))                          # tulow
    out += part(4, 12, 4, 40, 16, -8, 12, -2, (-6, 24, 0), pose.get("arm_r", 0), pose.get("arm_r_z", 0))  # prawa reka
    out += part(4, 12, 4, 32, 48, 4, 12, -2, (6, 24, 0), pose.get("arm_l", 0), pose.get("arm_l_z", 0))    # lewa reka
    out += part(8, 8, 8, 0, 0, -4, 24, -4, (0, 24, 0), pose.get("head_pitch", 0))  # glowa
    for f in out:  # model gracza jest rysowany w skali 15/16
        f.v = [(p[0] * 0.9375, p[1] * 0.9375, p[2] * 0.9375) for p in f.v]
    if yaw:
        rotate_faces(out, "y", yaw)
    translate_faces(out, origin)
    return out


def to_gif_frames(frames, bg):
    out = []
    for fr in frames:
        base = Image.new("RGBA", fr.size, bg)
        base.alpha_composite(fr)
        out.append(base)
    return out
