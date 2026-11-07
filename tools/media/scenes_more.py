"""Kolejne sceny (kilof 3x3, klatka pojedynku, hak, smoczy szpon). Importowane przez make_scenes.py."""
import math
import random

from PIL import Image, ImageDraw

import mc
import render3d as r
import scene as sc
from make_scenes import S, W, H, save, chat_line

DEG = math.radians


def avg_color(tex):
    px = [p for p in tex.convert("RGBA").getdata() if p[3] > 0]
    n = len(px)
    return tuple(sum(p[i] for p in px) // n for i in range(3))


# ============================================================================ Kilof Otchlani 3x3

def scene_pick():
    st = sc.Stage(yaw=-0.5, pitch=0.3, scale=54, center=(0.5, 2.1, 0))
    rnd = random.Random(11)
    stone = mc.vanilla("block/stone")
    ores = [mc.vanilla("block/coal_ore"), mc.vanilla("block/iron_ore"), mc.vanilla("block/diamond_ore"), mc.vanilla("block/andesite")]
    floor = st.floor(-5, 6, -1, 4, mc.vanilla("block/polished_andesite"))
    cells = {}
    for x in range(-3, 4):
        for y in range(0, 5):
            cells[(x, y)] = ores[rnd.randrange(4)] if rnd.random() < 0.25 else stone
    pick = mc.vanilla("item/netherite_pickaxe")
    destroy = [mc.vanilla(f"block/destroy_stage_{i}") for i in range(10)]
    removed = {(x, y) for x in (-1, 0, 1) for y in (1, 2, 3)}
    frames = []
    parts = []
    dt_ticks = 2

    def wall(gone, crack=None):
        out = []
        for (x, y), tex in cells.items():
            if gone and (x, y) in removed:
                continue
            fd = {d: (tex, (0, 0, 16, 16), 0) for d in ("south", "east", "west", "up")}
            out += r.box_faces(x, y, -1, x + 1, y + 1, 0, fd, layer=0)
        if crack is not None:
            out += r.box_faces(0, 2, -1, 1, 3, 0, {"south": (destroy[crack], (0, 0, 16, 16), 0)}, layer=0, offset=(0, 0, 0.01))
        return out

    def draw(wall_faces, parts_now, note=None, swing=-70.0):
        player = sc.steve((-1.3, 0, 1.7), DEG(180), dict(arm_r=swing), item=sc.sprite_item(pick, 1.0))
        img = st.render(floor + wall_faces + player)
        for pos, col, size, alpha in parts_now:
            sc.dot(img, st.project(pos), size, col, alpha)
        sc.caption(img, f"{S}4{S}lKilof Otchłani", 16, 12)
        mc.draw_text(img, 16 / 2, 52 / 2, f"{S}7niszczy {S}c3×3{S}7, a wytrzymałość spada jak za {S}fjeden{S}7 blok", 2)
        if note:
            sc.actionbar(img, note, 2)
        frames.append(img)

    for i in range(10):
        draw(wall(False, crack=i), [], swing=-65 if i % 2 == 0 else -105)
    for (x, y) in removed:
        col = avg_color(cells[(x, y)])
        for _ in range(7):
            parts.append(dict(p=[x + rnd.uniform(.2, .8), y + rnd.uniform(.2, .8), -0.5], v=[rnd.uniform(-.06, .06), rnd.uniform(.0, .12), rnd.uniform(.05, .2)], c=col, life=rnd.randint(14, 22), kind="block"))
    for _ in range(26):
        parts.append(dict(p=[rnd.uniform(-1, 2), rnd.uniform(1, 4), -0.45], v=[rnd.uniform(-.03, .03), rnd.uniform(.02, .08), rnd.uniform(.0, .06)], c=rnd.choice([(80, 190, 255), (255, 150, 40), (200, 40, 50)]), life=rnd.randint(10, 20), kind="fx"))
    for k in range(26):
        now = []
        for q in parts:
            if q["life"] <= 0:
                continue
            for _ in range(dt_ticks):
                q["p"][0] += q["v"][0]
                q["p"][1] += q["v"][1]
                q["p"][2] += q["v"][2]
                q["v"][1] -= 0.03 if q["kind"] == "block" else -0.004
                q["life"] -= 1
            a = 255 if q["life"] > 6 else max(0, q["life"] * 40)
            now.append((tuple(q["p"]), q["c"], 4 if q["kind"] == "block" else 3, a))
        draw(wall(True), now, note=f"{S}7Wytrzymałość: {S}f-1" if k > 2 else None, swing=-75)
    for _ in range(8):
        draw(wall(True), [], note=f"{S}7Wytrzymałość: {S}f-1", swing=-70)
    save("kilof-otchlani", frames, ms=100)


# ============================================================================ Klatka Pojedynku

def scene_snare():
    st = sc.Stage(yaw=-0.55, pitch=0.5, scale=56, center=(0, 0.9, 0.2))
    floor = st.floor(-8, 8, -6, 7, mc.vanilla("block/deepslate_tiles"))
    ff = mc.vanilla("misc/forcefield")
    tear = mc.pack("item/snare")
    frames = []
    a_pos, v_pos = (-2.0, 0, 0.0), (2.0, 0, 0.0)

    def border_faces(alpha):
        walls = [
            ((-5, 0, -5.02, 5, 5, -4.98), ("south", "north")),
            ((-5, 0, 4.98, 5, 5, 5.02), ("south", "north")),
            ((-5.02, 0, -5, -4.98, 5, 5), ("east", "west")),
            ((4.98, 0, -5, 5.02, 5, 5), ("east", "west")),
        ]
        def zc(w):
            (x1, y1, z1, x2, y2, z2), _ = w
            return st.cam.view(((x1 + x2) / 2, 2.5, (z1 + z2) / 2))[2]
        far = sorted(walls, key=zc)[:2]
        out = []
        for (box, dirs) in far:
            out += r.box_faces(*box, {d: (ff, (0, 0, 16, 16), 0) for d in dirs}, layer=3, tint=(120, 170, 255), alpha=alpha, two_sided=True)
        return out

    def frame(t, show_border=0.0, swing=0, title=False, bar=None, msg=None, fx=False, tear_hand=True):
        arm_a = -70 + (-35 if swing % 2 else 0)
        arm_v = -70 + (-35 if (swing + 1) % 2 else 0)
        a = sc.steve(a_pos, DEG(90), dict(arm_r=arm_a), item=sc.sprite_item(tear, 0.9) if tear_hand else None)
        v = sc.steve(v_pos, DEG(-90), dict(arm_r=arm_v))
        faces = floor + a + v
        if show_border > 0:
            faces += border_faces(0.38 * show_border)
        img = st.render(faces)
        if show_border > 0:
            d = ImageDraw.Draw(img, "RGBA")
            pts = [st.project(p) for p in ((-5, 0.02, -5), (5, 0.02, -5), (5, 0.02, 5), (-5, 0.02, 5), (-5, 0.02, -5))]
            d.line(pts, fill=(110, 150, 255, int(255 * show_border)), width=3)
            for cx_, cz_ in ((-5, -5), (5, -5), (5, 5), (-5, 5)):
                d.line([st.project((cx_, 0, cz_)), st.project((cx_, 5, cz_))], fill=(110, 150, 255, int(120 * show_border)), width=2)
        if fx:
            rnd = random.Random(int(t * 100))
            for _ in range(40):
                p = st.project((v_pos[0] + rnd.uniform(-.5, .5), 1 + rnd.uniform(-1, 1), rnd.uniform(-.5, .5)))
                sc.dot(img, p, 3, (170, 90, 255), 220)
        sc.caption(img, f"{S}d{S}l✸ Klatka Pojedynku ✸", 16, 12)
        mc.draw_text(img, 16 / 2, 52 / 2, f"{S}7border {S}d10x10{S}7 na maks. {S}e20s{S}7, bez rzucania i teleportu", 2)
        if title:
            mc.center_text(img, W / 2 / 5, 130 / 5, f"{S}d{S}lBorder!", 5)
            mc.center_text(img, W / 2 / 3, 200 / 3, f"{S}71v1 z {S}fGracz", 3)
        if bar is not None:
            left = max(0, math.ceil(20 * bar))
            sc.bossbar(img, W / 2, 92, f"{S}d{S}lZNAJDUJESZ SIĘ W KLATCE! {S}fPozostały czas: {S}e{left}s", bar, "purple", 2)
            mc.draw_text(img, (W - 150) / 2, (H - 24) / 2, f"{S}8(przyspieszone x3)", 2)
        if msg:
            chat_line(img, msg, H - 120)
        frames.append(img)

    for i in range(6):
        frame(i * 0.1, swing=0)
    for i in range(4):
        frame(0.6 + i * 0.1, swing=1, fx=True, show_border=min(1.0, i / 3 + 0.1), title=True)
    for i in range(10):
        frame(1.0 + i * 0.1, swing=i, show_border=1.0, title=i < 6, bar=1.0 - i * 0.003)
    for i in range(30):
        frame(2.0 + i * 0.1, swing=i, show_border=1.0, bar=max(0.0, 1.0 - (i + 10) / 40))
    for i in range(8):
        frame(5.0 + i * 0.1, swing=0, show_border=max(0.0, 1 - i / 6), msg=f"{S}eCzas klatki minął — wracacie na swoje miejsca." if i > 1 else None)
    save("klatka-pojedynku", frames, ms=100)


# ============================================================================ Smoczy Szpon

def claw_bar(bonus):
    if bonus >= 150:
        color = f"{S}4{S}l"
    elif bonus >= 130:
        color = f"{S}c{S}l"
    elif bonus >= 110:
        color = f"{S}6"
    else:
        color = f"{S}7"
    prog = max(0, min(20, int((bonus - 100) / 50 * 20)))
    bar = f"{S}8["
    for i in range(20):
        bar += (f"{S}c|" if i < prog else f"{S}8|")
    bar += f"{S}8]"
    return f"{color}⚔ Obrażenia: {bonus}% {S}8{bar}"


def scene_claw():
    st = sc.Stage(yaw=-0.55, pitch=0.42, scale=58, center=(0, 1.1, 0))
    floor = st.floor(-6, 6, -3, 3, mc.vanilla("block/obsidian"))
    claw = mc.pack("item/dragon_claw")
    ax, tx = -3.2, 3.2
    frames = []

    def frame(bonus, arm, hurt=False, knock=0.0, msg=None, fx=0, strike_back=False):
        a = sc.steve((ax, 0, 0), DEG(90), dict(arm_r=arm), item=sc.sprite_item(claw, 1.2))
        t = sc.steve((tx + knock, 0, 0), DEG(-90), dict(arm_r=-100 if strike_back else 0), tint=(255, 110, 110) if hurt else None)
        img = st.render(floor + a + t)
        if fx:
            rnd = random.Random(fx * 13)
            n = int(min(40, (bonus - 100) / 50 * 40) + 2)
            for _ in range(n):
                p = st.project((tx + knock + rnd.uniform(-.7, .7), 1.2 + rnd.uniform(-1, 1), rnd.uniform(-.6, .6)))
                sc.dot(img, p, 2 if bonus < 150 else 3, (210, 140, 255) if bonus < 150 else (240, 90, 255), 230)
            if bonus >= 150:
                sc.glow(img, st.project((tx + knock, 1.3, 0)), 80, (200, 60, 255), 0.45)
        sc.actionbar(img, claw_bar(bonus))
        sc.caption(img, f"{S}5{S}lSmoczy Szpon", 16, 12)
        mc.draw_text(img, 16 / 2, 52 / 2, f"{S}7każde trafienie: {S}f+5%{S}7 obrażeń, max {S}f150%{S}7", 2)
        if msg:
            chat_line(img, msg, H - 150)
        frames.append(img)

    bonus = 100
    for hit in range(11):
        frame(bonus, -60)
        frame(bonus, -112, hurt=True, knock=0.15, fx=hit + 1 if bonus > 100 else 0)
        frame(bonus, -112, fx=hit + 1 if bonus > 100 else 0)
        bonus = min(150, bonus + 5)
    for _ in range(5):
        frame(150, -70, fx=99)
    reset = f"{S}c⚠ {S}7Bonus {S}5Smoczego Szponu{S}7 został zresetowany!"
    for i in range(5):
        frame(150, -70, strike_back=True, msg=reset if i > 1 else None)
    for _ in range(10):
        frame(100, -70, msg=reset)
    save("smoczy-szpon", frames, ms=80)
