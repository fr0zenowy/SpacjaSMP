"""Animacje mechanik przedmiotow (docs/media/scenes/*.gif).

To NIE sa nagrania z serwera. To sceny zlozone z prawdziwych tekstur Minecrafta i liczb z kodu pluginu
(zasiegi, czasy, obrazenia), zeby pokazac, jak dany przedmiot dziala.

Uzycie:  python -X utf8 make_scenes.py [warden] [hole] ...    (bez argumentow: wszystkie)
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

import mc
import render3d as r
import scene as sc

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "docs", "media", "scenes"))
S = "§"
W, H = sc.W, sc.H
FRAME_MS = 80


def sword_faces():
    model = json.load(open(os.path.join(mc.PACK, "minecraft", "models", "custom", "warden_sword.json"), encoding="utf-8"))
    t = mc.pack("custom/warden_sword")
    return sc.model_item(r.model_faces(model, {"0": t, "particle": t}, scale=1 / 16, center=(8, 3, 8)), (0, 0, 0), scale=1.35)


def save(name, frames, colors=128, max_mb=3.2, ms=FRAME_MS):
    os.makedirs(OUT, exist_ok=True)
    mb = mc.save_gif(frames, os.path.join(OUT, name + ".gif"), ms=ms, colors=colors, max_mb=max_mb)
    print(f"{name:24s} {len(frames):3d} klatek  {mb:.2f} MB")


def chat_line(img, text, y):
    d = ImageDraw.Draw(img, "RGBA")
    w = mc.text_width(text) * 2 + 12
    d.rectangle((8, y - 4, 8 + w, y + 22), fill=(0, 0, 0, 120))
    mc.draw_text(img, 14 / 2, (y + 3) / 2, text, 2)


def charge_bar(charge):
    filled = round(charge * 10 / 100)
    bar = ""
    for i in range(10):
        bar += (f"{S}b" if charge >= 100 else f"{S}3") + "|" if i < filled else f"{S}8|"
    pct = f"{S}b{S}l" if charge >= 100 else (f"{S}b" if charge >= 75 else f"{S}3")
    return f"{pct}⚡ {charge}% {S}8[{bar}{S}8]"


# ============================================================================ Warden Sword

def scene_warden():
    st = sc.Stage(yaw=-0.5, pitch=0.4, scale=64, center=(0, 1.2, 0))
    floor = st.floor(-6, 6, -3, 3, mc.vanilla("block/deepslate_tiles"))
    ax, tx = -3.8, 3.8
    boom_frames = [mc.vanilla(f"particle/sonic_boom_{i}") for i in range(16) if _has(f"particle/sonic_boom_{i}")]
    frames = []

    def frame(charge, arm, hurt=0.0, knock=0.0, boom_age=None, hearts=10.0, flash=False, msg=None, speed=False, plus=None, sneak=False):
        a = sc.steve((ax, -0.12 if sneak else 0, 0), math.radians(90), dict(arm_r=arm), item=sword_faces())
        t = sc.steve((tx + knock, 0, 0), math.radians(-90), dict(), tint=(255, 110, 110) if hurt > 0 else None)
        img = st.render(floor + a + t)
        # sonic boom
        if boom_age is not None:
            for k in range(0, 7):
                d = 1.2 + k * 1.1
                pos = (ax + d, 1.55, 0)
                fi = min(len(boom_frames) - 1, int(boom_age * FRAME_MS / 50))
                if fi >= 0 and boom_age * FRAME_MS / 50 < len(boom_frames) + 1:
                    p = st.project(pos)
                    sz = 84
                    im = boom_frames[fi].resize((sz, sz), Image.NEAREST)
                    sc._paste_clipped(img, im, (int(p[0] - sz / 2), int(p[1] - sz / 2)))
            rnd = random.Random(7)
            for k in range(46):
                d = 1.0 + k * 0.2
                jx, jy, jz = rnd.uniform(-.35, .35), rnd.uniform(-.3, .3), rnd.uniform(-.2, .2)
                drift = boom_age * 0.04
                p = st.project((ax + d + jx, 1.5 + jy + drift, jz))
                fade = max(0, 255 - boom_age * 22)
                if fade:
                    sc.dot(img, p, 3, (92, 210, 232), int(fade * 0.9))
        # +5% przy trafieniu
        if plus is not None:
            p = st.project((tx, 3.1, 0))
            mc.center_text(img, p[0] / 3, p[1] / 3 - plus * 0.6, f"{S}b+5%", 3)
        # serca przeciwnika
        hp = st.project((tx + knock, 2.35, 0))
        sc.hearts_row(img, int(hp[0] - 80), int(hp[1] - 14), hearts, 10, scale=2)
        if flash:
            sc.glow(img, st.project((tx, 1.2, 0)), 60, (255, 255, 255), 0.5)
        sc.actionbar(img, charge_bar(charge))
        if msg:
            chat_line(img, msg, H - 150)
        if speed:
            sc.effect_icon(img, "speed", W - 52, 12, f"{S}bSzybkość II  {S}f0:10")
        sc.caption(img, f"{S}3Warden Sword", 16, 12)
        mc.draw_text(img, 16 / 2, 52 / 2, f"{S}7true damage: {S}c5 serc{S}7, zasięg {S}f24{S}7 bloków", 2)
        frames.append(img)

    # 1) ladowanie: kazde trafienie = +5%
    charge = 0
    for hit in range(20):
        charge += 5
        frame(charge - 5, -58)
        frame(charge, -112, hurt=1, knock=0.18, plus=hit % 3)
    # 2) RAGE
    rage = f"{S}b{S}lRAGE {S}3— Shift + PPM, aby wystrzelić sonic boom!"
    for _ in range(9):
        frame(100, -70, msg=rage)
    # 3) Sonic Boom
    for i in range(16):
        hit = i >= 1
        frame(0, -75, hurt=1 if 1 <= i <= 4 else 0, knock=min(0.7, i * 0.25) if hit else 0, boom_age=i,
              hearts=5.0 if hit else 10.0, flash=1 <= i <= 2, msg=rage if i == 0 else None, speed=True, sneak=True)
    for i in range(10):
        frame(0, -70, knock=0.7, hearts=5.0, speed=True, boom_age=16 + i if i < 2 else None)
    save("warden-sword", frames)



# ============================================================================ Kataklizm Pustki: czarna dziura

def hole_faces(c, tick):
    """Bryla czarnej dziury zlozona tak, jak w VoidBowListener.spawnHoleVisuals (rozmiary i predkosci z kodu)."""
    bc, gc, sg = mc.vanilla("block/black_concrete"), mc.vanilla("block/gray_concrete"), mc.vanilla("block/black_stained_glass")

    def box(tex, cx, cy, cz, sx, sy, sz, ang_deg, alpha=1.0, layer=1):
        fd = {d: (tex, (0, 0, 16, 16), 0) for d in ("up", "down", "north", "south", "east", "west")}
        fs = r.box_faces(cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2, fd,
                         rotation=((cx, cy, cz), "y", ang_deg), layer=layer, alpha=alpha)
        return fs

    out = []
    out += box(bc, c[0], c[1], c[2], 1.7, 1.7, 1.7, math.degrees(tick * 0.7 * 0.15))
    out += box(sg, c[0], c[1], c[2], 2.2, 2.2, 2.2, math.degrees(-tick * 0.45 * 0.15), alpha=0.75)
    n = 16
    for i in range(n):
        a = i * math.pi * 2 / n + tick * 0.055
        out += box(bc, c[0] + math.cos(a) * 2.15, c[1], c[2] + math.sin(a) * 2.15, 0.52, 0.09, 0.52, -math.degrees(a))
        b = i * math.pi * 2 / n + 0.1 + tick * 0.038
        out += box(gc, c[0] + math.cos(b) * 3.1, c[1], c[2] + math.sin(b) * 3.1, 0.46, 0.07, 0.46, -math.degrees(b))
    return out


def scene_hole():
    st = sc.Stage(yaw=-0.6, pitch=0.5, scale=50, center=(-0.8, 1.0, 0.6))
    floor = st.floor(-7, 6, -4, 5, mc.vanilla("block/smooth_stone"))
    c = (0.0, 1.05, 0.0)
    OPEN, END, FREEZE = 1.1, 1.1 + 8.0, 5.0
    bow = mc.vanilla("item/bow")
    # symulacja ofiary (wzory z pullPlayers: v = v*0.65 + kierunek * (0.18 + 0.32*(1 - d/5.5)); zamrozenie <= 1.4)
    pos, vel = [3.5, 3.1], [0.0, 0.0]
    frozen_at = None
    traj = []
    for k in range(260):
        t = k * 0.05
        if OPEN <= t < END and (frozen_at is None or t < frozen_at + FREEZE):
            d = math.hypot(pos[0], pos[1])
            if frozen_at is None:
                if d <= 1.4:
                    frozen_at = t
                    vel = [0.0, 0.0]
                elif d <= 5.5:
                    s = 0.18 + 0.32 * (1 - d / 5.5)
                    vel = [vel[0] * 0.65 - pos[0] / d * s, vel[1] * 0.65 - pos[1] / d * s]
                    pos = [pos[0] + vel[0] * 0.55, pos[1] + vel[1] * 0.55]
        traj.append((pos[0], pos[1], frozen_at is not None and t < frozen_at + FREEZE))
    frames = []
    dt = 0.1
    for i in range(int(10.6 / dt)):
        t = i * dt
        k = min(len(traj) - 1, int(t / 0.05))
        vx, vz, frozen = traj[k]
        yaw = math.atan2(-vx, -vz)
        shooter = sc.steve((-5.9, 0, 0), math.radians(90), dict(arm_r=-88), item=sc.sprite_item(bow, 1.0))
        victim = sc.steve((vx, 0, vz), yaw, dict(), tint=(150, 205, 255) if frozen else None)
        faces = floor + shooter + victim
        tick = (t - OPEN) / 0.05
        is_open = OPEN <= t < END
        if is_open:
            faces += hole_faces(c, tick)
        img = st.render(faces)
        cp = st.project(c)
        if is_open:
            d = ImageDraw.Draw(img, "RGBA")
            for rad, col in ((5.5, (255, 255, 255, 70)), (1.4, (120, 200, 255, 120))):
                pts = [st.project((math.cos(a * math.pi / 36) * rad, 0.02, math.sin(a * math.pi / 36) * rad)) for a in range(73)]
                d.line(pts, fill=col, width=2)
            core_z = st.cam.view(c)[2]
            tt = tick * 0.12

            def dust(radius, y, n, phase, color, size, alpha=255):
                for q in range(n):
                    a = phase + q * 2 * math.pi / n
                    p3 = (c[0] + math.cos(a) * radius, c[1] + y, c[2] + math.sin(a) * radius)
                    px, py, pz = st.cam.project(p3)
                    if pz < core_z - 0.1 and math.hypot(px - cp[0], py - cp[1]) < 1.0 * st.cam.scale:
                        continue
                    sc.dot(img, (px, py), size, color, alpha)

            dust(4.05, 0, 12, tt * 0.5, (40, 40, 46), 3)
            dust(3.1, 0, 16, tt * 0.7, (50, 50, 58), 3)
            dust(2.15, 0, 14, tt * 0.95, (22, 22, 26), 3)
            sc.glow(img, cp, 70, (255, 120, 20), 0.28)
            dust(1.26, 0, 26, tt * 1.7, (255, 132, 28), 3)
            dust(1.42, 0, 18, tt * 1.45, (255, 214, 120), 2)
            dust(1.34, 0.7, 12, tt * 1.25, (255, 132, 28), 2)
            dust(1.34, -0.7, 12, tt * 1.25, (255, 132, 28), 2)
        # pocisk (ShulkerBullet) zanim dziura sie otworzy
        if 0.2 <= t < OPEN:
            u = (t - 0.2) / (OPEN - 0.2)
            st3 = (-5.6, 1.7, 0.0)
            p3 = (st3[0] + (c[0] - st3[0]) * u, st3[1] + (c[1] - st3[1]) * u + 0.7 * math.sin(math.pi * u), 0.0)
            for q in range(8):
                uu = max(0, u - q * 0.025)
                pq = (st3[0] + (c[0] - st3[0]) * uu, st3[1] + (c[1] - st3[1]) * uu + 0.7 * math.sin(math.pi * uu), 0.0)
                sc.dot(img, st.project(pq), max(2, 5 - q // 2), (15, 15, 18), max(40, 255 - q * 30))
        # napisy
        sc.caption(img, f"{S}d{S}lKataklizm Pustki", 16, 12)
        mc.draw_text(img, 16 / 2, 52 / 2, f"{S}7moc 1: czarna dziura, ssanie {S}f5,5{S}7 bloku, zamrożenie {S}f5s", 2)
        if is_open:
            sc.hud_text(img, f"{S}dCzarna dziura: {S}f{max(0, math.ceil(END - t))}s", W - 130, 14, 3)
        if frozen and frozen_at is not None:
            left = max(0, math.ceil(frozen_at + FREEZE - t))
            p = st.project((vx, 3.0, vz))
            mc.center_text(img, p[0] / 3, p[1] / 3, f"{S}b❄ {left}s", 3)
            if t - frozen_at < 2.0:
                chat_line(img, f"{S}5Czarna dziura Cię zamroziła na 5s!", H - 120)
        if t >= END:
            sc.actionbar(img, f"{S}7Cooldown {S}f2m 30s {S}7(jeden na strzał i na obie moce)", 2)
        frames.append(img)
    save("kataklizm-czarna-dziura", frames, ms=100)


def _has(name):
    try:
        mc.vanilla(name)
        return True
    except KeyError:
        return False


SCENES = {
    "warden": scene_warden,
    "hole": scene_hole,
}


def _load_more():
    import scenes_more as m
    SCENES.update({"pick": m.scene_pick, "snare": m.scene_snare, "claw": m.scene_claw})


if __name__ == "__main__":
    _load_more()
    wanted = sys.argv[1:] or list(SCENES)
    for w in wanted:
        SCENES[w]()
