"""Definicje przedmiotow pluginu (teksty skopiowane 1:1 z LegendaryItems.java / LifestealManager.java)
oraz funkcje renderujace ich model 3D i tooltip.
"""
import json
import math
import os

from PIL import Image, ImageDraw

import mc
import render3d as r

S = "§"
ARROW = "»"

RARITY = f"{S}6✦ {S}e{S}lLEGENDARNY{S}6 ✦"

# Tam, gdzie w kodzie nie ma polskich znakow, ich tu tez nie ma.
ITEMS = [
    dict(
        id="warden_sword", cmd="/wardensword", name="Warden Sword", material="NETHERITE_SWORD", cmd_data=7100002,
        source="Eventówka (lista w /drop), komenda dla OP",
        lines=[
            f"{S}3Warden Sword",
            f"{S}7{ARROW} Każde trafienie ładuje miecz o {S}b5%",
            f"{S}7{ARROW} Przy {S}b100%{S}7 odblokowuje {S}bRage",
            "",
            f"{S}f{ARROW} {S}7Shift + PPM: {S}bSonic Boom",
            f"{S}7   ▪ {S}c5 serc{S}7 true damage",
            f"{S}7   ▪ {S}bSzybkość II{S}7 na {S}e10s",
            f"{S}7   ▪ Ładunek wraca do {S}f0%",
        ],
        render="model",
    ),
    dict(
        id="void_bow", cmd="/kataklizm", name="Kataklizm Pustki", material="BOW", cmd_data=7100003,
        source="Boss Władca Lochów (eventówka w /drop)",
        lines=[
            f"{S}d{S}lKataklizm Pustki",
            f"{S}7{ARROW} Jest to przedmiot z:",
            f"{S}7{ARROW} Bossa władcy lochów (Lochy)",
            "",
            f"{S}7{ARROW} Łuk posiada 2 Customowe moce",
            f"{S}7{ARROW} 1. Wystrzel strzałę aby stworzyć czarną dziurę",
            f"{S}7{ARROW} Shift + PPM aby zmienić {S}cmoc",
            f"{S}7{ARROW} 2. Traf strzałą w gracza aby zadać mu 3.5 serca!",
        ],
        render="sprite", tex=("vanilla", "item/bow"),
    ),
    dict(
        id="nether_pick", cmd="/kilof3x3", name="Kilof Otchłani", material="NETHERITE_PICKAXE", cmd_data=None,
        source="Craft na stole 3x3 (przepis w docs/ITEMY.md)",
        lines=[
            f"{S}4{S}lKilof Otchłani",
            RARITY,
            "",
            f"{S}7Wykuty z netheritu i zaklęty ogniem piekieł.",
            f"{S}7Skała pęka wokół niego jak szkło.",
            "",
            f"{S}f{ARROW} {S}7Niszczy obszar {S}c3×3",
            f"{S}f{ARROW} {S}cW walce{S}7 działa jak zwykły kilof",
            f"{S}f{ARROW} {S}6Efficiency V{S}8  |  {S}bUnbreaking III{S}8  |  {S}aMending",
            "",
            f"{S}8Pasywnie, bez cooldownu",
        ],
        render="sprite", tex=("vanilla", "item/netherite_pickaxe"),
    ),
    dict(
        id="pick_shard", cmd="/odlamekkilofa", name="Odłamek Kilofa", material="ECHO_SHARD", cmd_data=None,
        source="Składnik craftingu Kilofa Otchłani",
        lines=[
            f"{S}4{S}lOdłamek Kilofa",
            f"{S}c✦ {S}7Odłamek z piekielnego rdzenia.",
            "",
            f"{S}f{ARROW} {S}7Składnik craftingu {S}4{S}lKilofa Otchłani",
            f"{S}8Zwykły Echo Shard nie zadziała.",
        ],
        render="sprite", tex=("vanilla", "item/echo_shard"),
    ),
    dict(
        id="snare", cmd="(zrzut / OP)", name="Klatka Pojedynku", material="GHAST_TEAR", cmd_data=5003,
        source="Pula zrzutu (losowa legenda)",
        lines=[
            f"{S}d{S}l✸ Klatka Pojedynku ✸",
            RARITY,
            "",
            f"{S}7Nici pustki splataja wymiar pojedynku.",
            f"{S}7Reszta swiata was nie widzi.",
            "",
            f"{S}f{ARROW} {S}7Traf gracza tym przedmiotem.",
            f"{S}f{ARROW} {S}7Wokół was tworzy sie border {S}d10x10",
            f"{S}7   na maks. {S}e20s{S}7.",
            f"{S}7   Bez rzucania i bez teleportu.",
            "",
            f"{S}8Cooldown: 1.5 min",
        ],
        render="sprite", tex=("pack", "item/snare"),
    ),
    dict(
        id="rift_thorn", cmd="(zrzut / OP)", name="Kolce Rozdarcia", material="PRISMARINE_SHARD", cmd_data=5005,
        source="Pula zrzutu (losowa legenda)",
        lines=[
            f"{S}b{S}l✸ Kolce Rozdarcia ✸",
            RARITY,
            "",
            f"{S}7Odłamek kryształu z rozpękniętej areny.",
            f"{S}7Kazde trafienie wysysa sile przeciwnika.",
            "",
            f"{S}f{ARROW} {S}7Traf gracza w walce wręcz:",
            f"{S}7   ▪ {S}cSlabosc I{S}7 na {S}e10s",
            f"{S}7   ▪ {S}bSpowolnienie II{S}7 na {S}e3s",
            "",
            f"{S}8Cooldown: 30s",
        ],
        render="sprite", tex=("pack", "item/rift_thorn"),
    ),
    dict(
        id="boss_pumpkin", cmd="(zrzut / OP)", name="Dynia Bossa", material="CARVED_PUMPKIN", cmd_data=5004,
        source="Pula zrzutu (losowa legenda)",
        lines=[
            f"{S}6{S}l✸ Dynia Bossa ✸",
            RARITY,
            "",
            f"{S}7Wypalona pieczecia upadlego wladcy dyni.",
            f"{S}7Nosi ja ten, kto przetrwal najgorsze.",
            "",
            f"{S}f{ARROW} {S}7Zaloz na glowe:",
            f"{S}9   ▪ Odpornosc I",
            f"{S}8   ▪ Ukryta ochrona VII",
            "",
            f"{S}8Pasywnie, bez cooldownu",
        ],
        render="pumpkin",
    ),
    dict(
        id="grapple", cmd="(OP / eventy)", name="Hak Pustkowi", material="FISHING_ROD", cmd_data=5001,
        source="Legenda w kodzie (dla OP / eventów)",
        lines=[
            f"{S}6{S}l✸ Hak Pustkowi ✸",
            RARITY,
            "",
            f"{S}7Wykuty przez zaginionych lowcow,",
            f"{S}7lina nigdy nie chybia celu.",
            "",
            f"{S}f{ARROW} {S}7Zarzuc hak i zwin, a zostaniesz",
            f"{S}7   blyskawicznie przyciagniety w jego strone.",
            f"{S}f{ARROW} {S}aBrak obrazen z upadku po uzyciu.",
            "",
            f"{S}8Cooldown: 4s",
        ],
        render="sprite", tex=("pack", "item/grapple"),
    ),
    dict(
        id="fury", cmd="(OP / eventy)", name="Berlo Furii", material="BLAZE_ROD", cmd_data=5002,
        source="Legenda w kodzie (dla OP / eventów)",
        lines=[
            f"{S}c{S}l✸ Berlo Furii ✸",
            RARITY,
            "",
            f"{S}7Plonie gniewem dawnych wojownikow.",
            f"{S}7Kto je dzierzy, nie zna strachu.",
            "",
            f"{S}f{ARROW} {S}7PPM: wpadasz w bojowy szal na {S}c5s",
            f"{S}c   ▪ Sila III",
            f"{S}b   ▪ Szybkosc III",
            "",
            f"{S}8Cooldown: 40s",
        ],
        render="sprite", tex=("vanilla", "item/blaze_rod"),
    ),
    dict(
        id="dragon_claw", cmd="/dragongive", name="Smoczy Szpon", material="NETHERITE_SWORD", cmd_data=5007,
        source="Event Smoka Kresu",
        lines=[
            f"{S}5{S}lSmoczy Szpon",
            RARITY,
            "",
            f"{S}7Szpon wykuty z pazura smoka kresu,",
            f"{S}7każde uderzenie wzmacnia jego moc.",
            "",
            f"{S}f{ARROW} {S}7Każde {S}cuderzenie{S}7 zwiększa",
            f"{S}7   obrażenia o {S}f5%{S}7 (max {S}f150%{S}7)",
            f"{S}f{ARROW} {S}7Otrzymanie obrażeń {S}cresetuje{S}7 bonus",
            "",
            f"{S}8Pasywnie, bez cooldownu",
        ],
        render="sprite", tex=("pack", "item/dragon_claw"),
    ),
    dict(
        id="dragon_belly", cmd="/dragongive", name="Brzuszek Smoka", material="SHULKER_SHELL", cmd_data=5006,
        source="Event Smoka Kresu",
        lines=[
            f"{S}5{S}lBrzuszek {S}d{S}lSmoka",
            RARITY,
            "",
            f"{S}7Brzuszek pradawnego smoka kresu,",
            f"{S}7pożera dusze pokonanych wrogów.",
            "",
            f"{S}f{ARROW} {S}7Posiadając w ekwipunku po {S}czabiciu",
            f"{S}7   {S}fgracza{S}7 jego przedmioty trafiają",
            f"{S}7   do {S}5Brzuszka {S}dSmoka",
            "",
            f"{S}6{ARROW} PPM: otwórz brzuszek!",
            "",
            f"{S}8Pasywnie, bez cooldownu",
        ],
        render="sprite", tex=("pack", "item/dragon_belly"),
    ),
    dict(
        id="heart", cmd="/lifesteal, /wyplacserce", name="Serce", material="RED_DYE", cmd_data=1001,
        source="Zabójstwo gracza, craft, /wyplacserce",
        lines=[
            f"{S}c{S}lSerce",
            f"{S}7Prawym przyciskiem aby użyć.",
            f"{S}4+{S}c1 serce do maksymalnego HP.",
        ],
        render="sprite", tex=("pack", "item/lifesteal_heart"),
    ),
]
BY_ID = {i["id"]: i for i in ITEMS}


def _tex(spec):
    kind, name = spec
    return mc.vanilla(name) if kind == "vanilla" else mc.pack(name)


def item_faces(item):
    kind = item["render"]
    if kind == "model":
        model = json.load(open(os.path.join(mc.PACK, "minecraft", "models", "custom", "warden_sword.json"), encoding="utf-8"))
        t = mc.pack("custom/warden_sword")
        faces = r.model_faces(model, {"0": t, "particle": t}, scale=1 / 16, center=(8, 8.4, 8))
        return faces, dict(scale=232, cy=4.0)
    if kind == "pumpkin":
        front, side, top = mc.vanilla("block/carved_pumpkin"), mc.vanilla("block/pumpkin_side"), mc.vanilla("block/pumpkin_top")
        faces = r.cube(-0.5, -0.5, -0.5, dict(south=front, up=top, down=top, side=side))
        return faces, dict(scale=230, cy=0.0)
    tex = _tex(item["tex"])
    faces = r.sprite_faces(tex, size=1.0, thickness=0.0625, slices=7)
    return faces, dict(scale=300, cy=0.0)


def turntable(item, size=320, frames=40, bg=(26, 29, 37, 255)):
    faces, opt = item_faces(item)
    out = []
    for i in range(frames):
        yaw = 2 * math.pi * i / frames + 0.35
        pitch = 0.25 if item["render"] != "pumpkin" else 0.45
        cam = r.Camera(yaw=yaw, pitch=pitch, scale=opt["scale"] * size / 360, cx=size / 2, cy=size / 2 + opt["cy"])
        fr = r.render(faces, cam, (size, size), bg=(0, 0, 0, 0), ss=2)
        base = Image.new("RGBA", (size, size), bg)
        glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse((size * 0.2, size * 0.78, size * 0.8, size * 0.92), fill=(0, 0, 0, 90))
        base.alpha_composite(glow)
        base.alpha_composite(fr)
        out.append(base)
    return out


def tooltip_image(item, scale=3):
    return mc.tooltip(item["lines"], scale=scale)
