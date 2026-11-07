"""Karty systemow (lifesteal, zrzut, smok, boss, sklep, klany, anticheat) i galeria okien GUI.
Wszystkie liczby pochodza z kodu i config.yml pluginu. Wyniki: docs/media/systems/*.png
"""
import os

from PIL import Image, ImageDraw

import mc

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "docs", "media", "systems"))
S = "§"
SCALE = 2
WIDTH = 980


def card(name, title, rows, icon, accent):
    row_h = 12 * SCALE + 6
    head_h = 104
    h = head_h + len(rows) * row_h + 26
    img = Image.new("RGBA", (WIDTH, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, WIDTH - 1, h - 1), radius=18, fill=(24, 27, 35, 255), outline=accent + (255,), width=3)
    d.rectangle((3, 3, 14, h - 4), fill=accent + (255,))
    ico = icon.resize((64, 64), Image.NEAREST)
    img.alpha_composite(ico, (36, 22))
    mc.draw_text(img, 118 / 4, 30 / 4, title, 4)
    d.line((36, head_h - 6, WIDTH - 30, head_h - 6), fill=(60, 66, 80, 255), width=2)
    for i, row in enumerate(rows):
        w = mc.text_width(row) * SCALE
        if w > WIDTH - 80:
            print(f"UWAGA {name}: wiersz {i} za szeroki ({w}px): {mc.strip_codes(row)[:50]}")
        mc.draw_text(img, 36 / SCALE, (head_h + i * row_h) / SCALE, row, SCALE)
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + ".png"), optimize=True)
    print("OK", name, img.size)


def main():
    card("lifesteal", f"{S}c{S}lLifesteal", [
        f"{S}7Start: {S}f10 serc{S}7, maksimum {S}f20{S}7. Serce to przedmiot (craft i {S}f/wyplacserce{S}7).",
        f"{S}7Śmierć zabiera {S}f1 serce{S}7 (także nie z PvP), zabójca dostaje {S}f1{S}7.",
        f"{S}7Ten sam gracz albo to samo IP daje serce raz na {S}f30 min{S}7 (anty-multikonto).",
        f"{S}7Po śmierci {S}f180 s{S}7 ochrony: nikt nie traci ani nie zyskuje serc.",
        f"{S}7Wylog w walce (tag {S}f30 s{S}7) = śmierć: drop, XP i utrata serca.",
        f"{S}70 serc = ban lifestealowy. {S}6Księga Odrodzenia{S}7 przywraca gracza z {S}f5 sercami{S}7.",
        f"{S}7Craft serca: {S}f3× blok złota{S}7 na górze, {S}fgwiazda Netheru{S}7 w środku i {S}f5× blok diamentu{S}7.",
    ], mc.pack("item/lifesteal_heart"), (235, 70, 80))

    card("zrzut", f"{S}e{S}lZrzut (airdrop)", [
        f"{S}7Codziennie o godzinie ustawionej przez admina ({S}f/adminzrzut{S}7), czas {S}fEurope/Warsaw{S}7.",
        f"{S}7Skrzynia spada w losowym miejscu w kwadracie {S}f±2000{S}7 bloków.",
        f"{S}7Animacja spadania: wszyscy oglądają ją w trybie {S}fspectator{S}7.",
        f"{S}7Potem {S}f15 min{S}7 blokady z bossbarem (współrzędne i odliczanie).",
        f"{S}7Skrzynię otwiera ten, kto pierwszy rozwiąże {S}f3 zagadki{S}7 w GUI.",
        f"{S}7Loot to dokładnie pula z {S}f/drop » Zrzut{S}7, bez ruletki i bez eventówek.",
        f"{S}7W trakcie zrzutu nie da się rzucać włóczniami (spear).",
    ], mc.vanilla("item/nether_star"), (240, 200, 70))

    card("smok-kresu", f"{S}5{S}lEvent Smoka Kresu", [
        f"{S}f/aktywujsmoka{S}7 (admin) przywołuje smoka w Endzie, {S}f/smok{S}7 otwiera GUI z informacjami.",
        f"{S}7Wszyscy trafiają do spectatora, animacja trwa {S}f12 s{S}7:",
        f"{S}7   {S}f5 s{S}7 ładowanie portalu, {S}f4 s{S}7 materializacja, {S}f3 s{S}7 wybuch i ujawnienie.",
        f"{S}7Plugin liczy obrażenia graczy do {S}f1500 HP{S}7 (prawdziwe HP smoka jest ogromne).",
        f"{S}7W trakcie walki scoreboard pokazuje ranking obrażeń, po walce top 10 na czacie.",
        f"{S}7Zwycięzca (najwięcej obrażeń): {S}f4 losowe itemy{S}7 z puli {S}f+ 1 eventówka{S}7.",
        f"{S}7Eventówka 50/50: {S}5Smoczy Szpon{S}7 albo {S}5Brzuszek Smoka{S}7.",
    ], mc.pack("item/dragon_claw"), (140, 70, 200))

    card("wladca-lochow", f"{S}7{S}lWładca Lochów (boss)", [
        f"{S}7MythicMobs + ModelEngine: {S}fVaelthar, the Hollow King{S}7, wither skeleton.",
        f"{S}f3000 HP{S}7, pancerz 12, odporny na wither, truciznę i upadek.",
        f"{S}7Ataki: cleave, sweep, soul harvest (5 czaszek), skok, dekret i przyzwanie strażników.",
        f"{S}7Faza 2 (enrage) po utracie części HP, {S}fStrażnik Lochów{S}7 ma 28 HP.",
        f"{S}7Event w pluginie: bossbar, scoreboard z {S}fTOP 3{S}7 obrażeń, arena (szkło i kraty).",
        f"{S}7Drop: {S}f5 itemów{S}7 z puli (edycja {S}f/adrop{S}7) i eventówka dla {S}6TOP 1{S}7 obrażeń.",
        f"{S}dKataklizm Pustki{S}7 to eventówka tego bossa (wg opisu itemu). Zresp: {S}f/zrespbossa{S}7.",
    ], mc.pack("custom/hollowking/greatsword"), (110, 130, 120))

    card("sklep-i-rynek", f"{S}a{S}lSklep i rynek", [
        f"{S}f/shop{S}7 skup i sprzedaż serwerowa, {S}f/asklep{S}7 (admin) edytuje ceny w GUI.",
        f"{S}7Cena sprzedaży spada o {S}f0,01${S}7 za każde {S}f250${S}7 sprzedaży danego itemu.",
        f"{S}7Po {S}f45 min{S}7 bez handlu cena wraca do bazy: co minutę o {S}f3%{S}7 różnicy.",
        f"{S}7Limit osobisty: {S}f1000${S}7 z jednego itemu = {S}f-40%{S}7 ceny na {S}f2–4 h{S}7 (tylko dla niego).",
        f"{S}7Kupno nie rusza ceny. Dolny próg {S}f0,01${S}7, a przy {S}f5{S}7 sprzedawcach w godzinę {S}f0,10${S}7.",
        f"{S}f/rynek{S}7 oferty graczy, {S}f/wystaw{S}7 wystawia item z ręki, tabliczki rynku.",
        f"{S}7Saldo jako placeholder {S}f%zamrozenie_money%{S}7. Pieniądze nie giną przy śmierci.",
    ], mc.vanilla("item/emerald"), (70, 200, 120))

    card("klany", f"{S}6{S}lKlany", [
        f"{S}7Maks. {S}f4 osoby{S}7 w klanie. Rangi: {S}fLIDER{S}7, {S}fADMIN{S}7, {S}fCZŁONEK{S}7. Brak friendly fire.",
        f"{S}7Podkomendy {S}f/klan{S}7: utworz, rozwiaz, zapros, akceptuj, odrzuc, wyrzuc, opusc,",
        f"{S}7   awansuj, zdegraduj, info, lista, sethome, home, chat, kolor, sojusz.",
        f"{S}7Tag klanu widać na czacie, nad głową i na tabie. Kolor ustawia {S}f/klan kolor{S}7.",
        f"{S}7Dom klanu, czat klanowy i sojusze między klanami.",
    ], mc.vanilla("item/name_tag"), (230, 150, 60))

    card("anticheat", f"{S}c{S}lAntiCheat i walka", [
        f"{S}7{S}f16{S}7 checków: Reach, WallHit, Aim, MultiAura, MaceKiller, AutoClicker, Fly, Speed,",
        f"{S}7   Blink, BadPacket, Timer, FastPlace, VehicleFly, Nuker, Freecam, KillAura.",
        f"{S}7Domyślnie {S}ftylko alerty{S}7 (config {S}falerts-only: true{S}7), VL maleje o {S}f2{S}7 co 10 s.",
        f"{S}7Alerty widzą gracze z {S}fzamrozenie.ac.alerts{S}7, komenda {S}f/ac{S}7 (alias {S}f/zac{S}7).",
        f"{S}7Anti-xray: plugin sam włącza w Paper {S}fengine-mode 2{S}7 przy starcie.",
        f"{S}7Antylogout: tag walki {S}f30 s{S}7, perły wyłączone, max {S}f3 totemy{S}7, kryształy zablokowane.",
    ], mc.vanilla("item/iron_sword"), (210, 80, 80))

    gallery()


def gallery():
    shots = [
        ("drop", "drop", "/drop"), ("rynek", "rynek", "/rynek"), ("rynek", "rynek_moje", "/rynek (moje)"), ("rynek", "rynek_zakup", "/rynek (zakup)"),
        ("shop", "shop", "/shop"), ("shop", "shop_list", "/shop (lista)"), ("shop", "shop_confirm", "/shop (potwierdzenie)"),
    ]
    k = 2
    cell_w, cell_h = 176 * k + 40, 236 * k + 70
    cols = 4
    rows = 2
    img = Image.new("RGBA", (cols * cell_w, rows * cell_h), (24, 27, 35, 255))
    for n, (ns, name, label) in enumerate(shots):
        tex = mc.pack_gui(ns, name)
        big = tex.resize((tex.width * k, tex.height * k), Image.NEAREST)
        cx, cy = (n % cols) * cell_w, (n // cols) * cell_h
        img.alpha_composite(big, (cx + 20, cy + 20))
        mc.center_text(img, (cx + cell_w / 2) / 3, (cy + 236 * k + 28) / 3, f"{S}f{label}", 3)
    # puste miejsce: opis
    n = len(shots)
    cx, cy = (n % cols) * cell_w, (n // cols) * cell_h
    mc.draw_text(img, (cx + 20) / 2, (cy + 60) / 2, f"{S}7Tła okien GUI z paczki zasobów", 2)
    mc.draw_text(img, (cx + 20) / 2, (cy + 90) / 2, f"{S}7(drop, rynek, sklep). Same tekstury,", 2)
    mc.draw_text(img, (cx + 20) / 2, (cy + 120) / 2, f"{S}7przedmioty w slotach dokłada plugin.", 2)
    img.convert("RGB").save(os.path.join(OUT, "okna-gui.png"), optimize=True)
    print("OK okna-gui", img.size)


if __name__ == "__main__":
    main()
